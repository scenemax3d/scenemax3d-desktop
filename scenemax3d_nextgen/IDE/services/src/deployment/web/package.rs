use super::{Context, files};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs,
    io::{self, Read},
    path::{Path, PathBuf},
};

/// Deduplicate only the disposable Web staging tree. Logical paths remain in
/// the manifest and are mounted as aliases before any game code runs.
pub(super) fn deduplicate(
    site: &Path,
    listing: &[PathBuf],
    context: &Context,
) -> io::Result<BTreeMap<String, String>> {
    let mut seen = BTreeMap::<(u64, Vec<u8>), PathBuf>::new();
    let mut aliases = BTreeMap::new();
    let mut saved = 0;
    let name = |p: &Path| -> io::Result<String> {
        Ok(p.strip_prefix(site)
            .map_err(io::Error::other)?
            .to_string_lossy()
            .replace('\\', "/"))
    };
    for path in listing {
        context.check()?;
        let mut file = fs::File::open(path)?;
        let size = file.metadata()?.len();
        let mut hash = Sha256::new();
        let mut buffer = [0u8; 65536];
        loop {
            context.check()?;
            let n = file.read(&mut buffer)?;
            if n == 0 {
                break;
            }
            hash.update(&buffer[..n]);
        }
        drop(file);
        let key = (size, hash.finalize().to_vec());
        if let Some(previous) = seen.get(&key) {
            if files::same_content(previous, path, context)? {
                aliases.insert(name(path)?, name(previous)?);
                fs::remove_file(path)?;
                saved += size;
                continue;
            }
        }
        seen.insert(key, path.clone());
    }
    context.log(format!(
        "Web downloads: {} unique files, {} aliases; {:.1} MiB of duplicate downloads removed.\n",
        listing.len() - aliases.len(),
        aliases.len(),
        saved as f64 / 1048576.
    ));
    Ok(aliases)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Arc, Mutex, atomic::AtomicBool};
    #[test]
    fn aliases_preserve_distinct_paths_and_never_chain() {
        let root = tempfile::tempdir().unwrap();
        let ctx = Context {
            report: Arc::new(Mutex::new(crate::deployment::Report::default())),
            cancel: Arc::new(AtomicBool::new(false)),
        };
        fs::create_dir(root.path().join("game")).unwrap();
        for (path, data) in [
            ("a", b"same"),
            ("b", b"same"),
            ("c", b"same"),
            ("d", b"diff"),
        ] {
            fs::write(root.path().join("game").join(path), data).unwrap();
        }
        let listing = files::list(&root.path().join("game"), &ctx).unwrap();
        let aliases = deduplicate(root.path(), &listing, &ctx).unwrap();
        assert_eq!(aliases.len(), 2);
        for (alias, source) in &aliases {
            assert!(!root.path().join(alias).exists());
            assert!(!aliases.contains_key(source));
            assert_eq!(fs::read(root.path().join(source)).unwrap(), b"same");
        }
        assert_eq!(fs::read(root.path().join("game/d")).unwrap(), b"diff");
    }
}
