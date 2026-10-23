//! Project file access: native disk on desktop, mounted immutable files in browsers.
#[cfg(target_arch = "wasm32")]
pub use browser::*;
#[cfg(not(target_arch = "wasm32"))]
pub use std::fs::*;
use std::path::Path;

/// Whether a project file exists.
pub fn is_file(path: impl AsRef<Path>) -> bool {
    metadata(path).is_ok_and(|m| m.is_file())
}
/// Whether a project directory exists.
pub fn is_dir(path: impl AsRef<Path>) -> bool {
    metadata(path).is_ok_and(|m| m.is_dir())
}
/// Whether a project path exists.
pub fn exists(path: impl AsRef<Path>) -> bool {
    metadata(path).is_ok()
}

#[cfg(any(target_arch = "wasm32", test))]
#[cfg_attr(test, allow(dead_code, unused_imports))]
mod browser {
    use std::{
        collections::{BTreeMap, BTreeSet},
        io,
        path::{Path, PathBuf},
        sync::{Arc, Mutex, OnceLock},
    };
    // Writes used by desktop authoring tools are deliberately unsupported in a browser.
    pub use std::fs::{
        OpenOptions, create_dir, create_dir_all, remove_dir, remove_dir_all, remove_file, write,
    };
    type Files = BTreeMap<PathBuf, Arc<[u8]>>;
    static FILES: OnceLock<Mutex<Files>> = OnceLock::new();
    fn files() -> &'static Mutex<Files> {
        FILES.get_or_init(Mutex::default)
    }
    fn normalized(path: impl AsRef<Path>) -> io::Result<PathBuf> {
        let text = path.as_ref().to_string_lossy().replace('\\', "/");
        let mut result = PathBuf::new();
        for part in text.split('/') {
            match part {
                "" | "." => {}
                ".." => {
                    if !result.pop() {
                        return Err(io::Error::other("Project path escapes its root"));
                    }
                }
                _ if part.contains(':') => {
                    return Err(io::Error::other("Absolute project paths are unsupported"));
                }
                _ => result.push(part),
            }
        }
        Ok(result)
    }
    /// Mount a downloaded project file before starting the runtime.
    pub fn mount(path: &str, bytes: Vec<u8>) -> io::Result<()> {
        let key = normalized(path)?;
        if key.as_os_str().is_empty() {
            return Err(io::Error::other("Empty project filename"));
        }
        files()
            .lock()
            .map_err(|_| io::Error::other("File store unavailable"))?
            .insert(key, bytes.into());
        Ok(())
    }
    /// Mount another logical path sharing immutable bytes with an existing file.
    pub fn mount_alias(path: &str, source: &str) -> io::Result<()> {
        let key = normalized(path)?;
        let source = normalized(source)?;
        if key.as_os_str().is_empty() {
            return Err(io::Error::other("Empty project filename"));
        }
        let mut store = files()
            .lock()
            .map_err(|_| io::Error::other("File store unavailable"))?;
        let bytes = store.get(&source).cloned().ok_or_else(|| {
            io::Error::other(format!("Alias source not mounted: {}", source.display()))
        })?;
        store.insert(key, bytes);
        Ok(())
    }
    /// Read mounted bytes.
    pub fn read(path: impl AsRef<Path>) -> io::Result<Vec<u8>> {
        Ok(File::open(path)?.into_inner().to_vec())
    }
    /// Read UTF-8 project text.
    pub fn read_to_string(path: impl AsRef<Path>) -> io::Result<String> {
        String::from_utf8(read(path)?).map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))
    }
    /// Read-only project file with standard `Read`/`Seek` support.
    pub struct File(io::Cursor<Arc<[u8]>>);
    impl File {
        /// Open a mounted project file.
        pub fn open(path: impl AsRef<Path>) -> io::Result<Self> {
            let key = normalized(path)?;
            let bytes = files()
                .lock()
                .map_err(|_| io::Error::other("File store unavailable"))?
                .get(&key)
                .cloned()
                .ok_or_else(|| {
                    io::Error::new(
                        io::ErrorKind::NotFound,
                        format!("Project file not mounted: {}", key.display()),
                    )
                })?;
            Ok(Self(io::Cursor::new(bytes)))
        }
        fn into_inner(self) -> Arc<[u8]> {
            self.0.into_inner()
        }
    }
    impl io::Read for File {
        fn read(&mut self, bytes: &mut [u8]) -> io::Result<usize> {
            io::Read::read(&mut self.0, bytes)
        }
    }
    impl io::Seek for File {
        fn seek(&mut self, position: io::SeekFrom) -> io::Result<u64> {
            io::Seek::seek(&mut self.0, position)
        }
    }
    /// Metadata for mounted files and inferred directories.
    pub struct Metadata {
        directory: bool,
        size: u64,
    }
    impl Metadata {
        pub fn is_file(&self) -> bool {
            !self.directory
        }
        pub fn is_dir(&self) -> bool {
            self.directory
        }
        pub fn len(&self) -> u64 {
            self.size
        }
        pub fn is_empty(&self) -> bool {
            self.size == 0
        }
        pub fn modified(&self) -> io::Result<std::time::SystemTime> {
            Ok(std::time::UNIX_EPOCH)
        }
    }
    pub fn metadata(path: impl AsRef<Path>) -> io::Result<Metadata> {
        let key = normalized(path)?;
        let files = files()
            .lock()
            .map_err(|_| io::Error::other("File store unavailable"))?;
        if let Some(bytes) = files.get(&key) {
            return Ok(Metadata {
                directory: false,
                size: bytes.len() as u64,
            });
        }
        if files.keys().any(|p| p.starts_with(&key)) {
            return Ok(Metadata {
                directory: true,
                size: 0,
            });
        }
        Err(io::Error::new(
            io::ErrorKind::NotFound,
            key.display().to_string(),
        ))
    }
    pub fn canonicalize(path: impl AsRef<Path>) -> io::Result<PathBuf> {
        let key = normalized(path)?;
        metadata(&key)?;
        Ok(key)
    }
    pub struct DirEntry(PathBuf);
    impl DirEntry {
        pub fn path(&self) -> PathBuf {
            self.0.clone()
        }
        pub fn file_name(&self) -> std::ffi::OsString {
            self.0.file_name().unwrap_or_default().to_owned()
        }
        pub fn metadata(&self) -> io::Result<Metadata> {
            metadata(&self.0)
        }
        pub fn file_type(&self) -> io::Result<Metadata> {
            self.metadata()
        }
    }
    pub fn read_dir(
        path: impl AsRef<Path>,
    ) -> io::Result<std::vec::IntoIter<io::Result<DirEntry>>> {
        let key = normalized(path)?;
        if !metadata(&key)?.is_dir() {
            return Err(io::Error::other("Not a directory"));
        }
        let files = files()
            .lock()
            .map_err(|_| io::Error::other("File store unavailable"))?;
        let entries: BTreeSet<_> = files
            .keys()
            .filter_map(|p| {
                let relative = p.strip_prefix(&key).ok()?;
                relative
                    .components()
                    .next()
                    .map(|c| key.join(c.as_os_str()))
            })
            .collect();
        Ok(entries
            .into_iter()
            .map(|p| Ok(DirEntry(p)))
            .collect::<Vec<_>>()
            .into_iter())
    }
}

#[cfg(test)]
mod tests {
    use super::browser::*;
    #[test]
    fn mounted_aliases_are_readable_and_enumerated_at_their_original_paths() {
        mount("game/alias-test/source.bin", vec![1, 2, 3]).unwrap();
        mount_alias(
            "game/alias-test/nested/copy.bin",
            "game/alias-test/source.bin",
        )
        .unwrap();
        assert_eq!(read("game/alias-test/nested/copy.bin").unwrap(), [1, 2, 3]);
        assert!(metadata("game/alias-test/nested").unwrap().is_dir());
        assert_eq!(read_dir("game/alias-test/nested").unwrap().count(), 1);
        assert!(mount_alias("../escape", "game/alias-test/source.bin").is_err());
        assert!(mount_alias("game/alias-test/bad", "game/missing-source").is_err());
    }
    #[test]
    fn mounted_scripts_support_normalization_directory_walks_and_binary_reads() {
        mount("game/scripts/fixture/main", b"include fixture".to_vec()).unwrap();
        mount("game/resources/fixture.bin", vec![0, 1, 255]).unwrap();
        assert_eq!(
            read_to_string("game/scripts/fixture/../fixture/main").unwrap(),
            "include fixture"
        );
        assert!(metadata("game/resources").unwrap().is_dir());
        assert_eq!(read("game/resources/fixture.bin").unwrap(), [0, 1, 255]);
        assert_eq!(
            read_dir("game/scripts")
                .unwrap()
                .next()
                .unwrap()
                .unwrap()
                .file_name(),
            "fixture"
        );
        assert!(mount("../outside", Vec::new()).is_err());
        assert!(canonicalize("game/missing").is_err());
    }
}
