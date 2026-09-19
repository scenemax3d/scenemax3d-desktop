//! Independent, Java-free single-file game bootstrap. The packager appends a ZIP.
#![forbid(unsafe_code)]
#![cfg_attr(windows, windows_subsystem = "windows")]
use std::{
    fs::{self, File},
    io::{self, Read},
    path::{Component, Path},
    process::Command,
};

fn unpack(source: File, root: &Path) -> io::Result<()> {
    let mut archive = zip::ZipArchive::new(source)?;
    if archive.len() > 100_000 {
        return Err(io::Error::other("Too many package entries"));
    }
    let mut total = 0u64;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i)?;
        let relative = entry
            .enclosed_name()
            .ok_or_else(|| io::Error::other("Unsafe package path"))?;
        if entry.is_symlink()
            || entry.name().contains([':', '\\'])
            || relative
                .components()
                .any(|p| !matches!(p, Component::Normal(_)))
        {
            return Err(io::Error::other("Unsafe package entry"));
        }
        total = total
            .checked_add(entry.size())
            .ok_or_else(|| io::Error::other("Package too large"))?;
        if total > 16 * 1024 * 1024 * 1024 {
            return Err(io::Error::other("Package exceeds 16 GiB"));
        }
        let path = root.join(relative);
        if entry.is_dir() {
            fs::create_dir_all(path)?;
            continue;
        }
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut output = File::options().write(true).create_new(true).open(&path)?;
        let expected = entry.size();
        // Read through EOF to verify CRC, but never trust an unbounded decompressor.
        let copied = io::copy(&mut (&mut entry).take(expected + 1), &mut output)?;
        if copied != expected {
            return Err(io::Error::other("Invalid package entry length"));
        }
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            if entry.unix_mode().is_some_and(|m| m & 0o111 != 0) {
                fs::set_permissions(&path, fs::Permissions::from_mode(0o700))?;
            }
        }
    }
    restore_aliases(root, total)
}
fn restore_aliases(root: &Path, mut total: u64) -> io::Result<()> {
    let manifest = root.join(".scenemax-package-aliases.json");
    if !manifest.is_file() {
        return Ok(());
    }
    if fs::metadata(&manifest)?.len() > 16 * 1024 * 1024 {
        return Err(io::Error::other("Alias manifest too large"));
    }
    let aliases: std::collections::BTreeMap<String, String> =
        serde_json::from_slice(&fs::read(manifest)?)?;
    if aliases.len() > 100_000 {
        return Err(io::Error::other("Too many aliases"));
    }
    for (destination, source) in aliases {
        for name in [&destination, &source] {
            if name.is_empty()
                || name.contains([':', '\\'])
                || !name.contains("resources/")
                || Path::new(name)
                    .components()
                    .any(|p| !matches!(p, Component::Normal(_)))
            {
                return Err(io::Error::other("Unsafe resource alias"));
            }
        }
        let source = root.join(source);
        let destination = root.join(destination);
        let metadata = fs::symlink_metadata(&source)?;
        if !metadata.is_file() || metadata.is_symlink() {
            return Err(io::Error::other("Invalid alias source"));
        }
        total = total
            .checked_add(metadata.len())
            .ok_or_else(|| io::Error::other("Package too large"))?;
        if total > 16 * 1024 * 1024 * 1024 {
            return Err(io::Error::other("Package exceeds 16 GiB"));
        }
        fs::create_dir_all(
            destination
                .parent()
                .ok_or_else(|| io::Error::other("Missing alias parent"))?,
        )?;
        // Distinct writable files preserve ordinary resource semantics.
        let mut output = File::options()
            .write(true)
            .create_new(true)
            .open(destination)?;
        io::copy(&mut File::open(source)?, &mut output)?;
    }
    Ok(())
}
fn run() -> io::Result<i32> {
    let temporary = tempfile::Builder::new()
        .prefix("scenemax-game-")
        .tempdir()?;
    unpack(File::open(std::env::current_exe()?)?, temporary.path())?;
    let manifest: serde_json::Value =
        serde_json::from_slice(&fs::read(temporary.path().join("launch.json"))?)?;
    let script = manifest["script"]
        .as_str()
        .ok_or_else(|| io::Error::other("Missing entry point"))?;
    if Path::new(script)
        .components()
        .any(|p| !matches!(p, Component::Normal(_)))
    {
        return Err(io::Error::other("Unsafe entry point"));
    }
    let mut command = Command::new(temporary.path().join(if cfg!(windows) {
        "projector.exe"
    } else {
        "projector"
    }));
    command
        .current_dir(temporary.path())
        .arg("run")
        .arg("--project-root")
        .arg(temporary.path())
        .arg("--script")
        .arg(temporary.path().join(script));
    // User data must survive disposable extraction; runtime extensions may consume this location.
    command.env("SCENEMAX_DISTRIBUTION_EXE", std::env::current_exe()?);
    command.env_remove("SCENEMAX_BUILTIN_RESOURCES");
    let builtin = temporary.path().join("builtin/resources");
    if builtin.is_dir() {
        command.env("SCENEMAX_BUILTIN_RESOURCES", builtin);
    }
    #[cfg(unix)]
    {
        let key = if cfg!(target_os = "macos") {
            "DYLD_LIBRARY_PATH"
        } else {
            "LD_LIBRARY_PATH"
        };
        let mut paths = vec![temporary.path().to_owned()];
        if let Some(previous) = std::env::var_os(key) {
            paths.extend(std::env::split_paths(&previous));
        }
        command.env(key, std::env::join_paths(paths).map_err(io::Error::other)?);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    let status = command.status()?;
    // The private extraction directory is owned until the game exits, then removed.
    Ok(status.code().unwrap_or(1))
}
fn main() {
    match run() {
        Ok(code) => std::process::exit(code),
        Err(error) => {
            let path = std::env::temp_dir().join("scenemax-launch-error.txt");
            let _ = fs::write(
                &path,
                format!("SceneMax could not launch this package: {error}\n"),
            );
            eprintln!("{error}; details: {}", path.display());
            std::process::exit(1);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    #[test]
    fn restores_duplicate_resources_and_rejects_alias_escape_or_overwrite() {
        for (target, succeeds) in [
            ("builtin/resources/shared.bin", true),
            ("../resources/escape", false),
            ("resources/original.bin", false),
        ] {
            let dir = tempfile::tempdir().unwrap();
            fs::create_dir_all(dir.path().join("resources")).unwrap();
            fs::write(dir.path().join("resources/original.bin"), b"shared content").unwrap();
            fs::write(
                dir.path().join(".scenemax-package-aliases.json"),
                serde_json::to_vec(&std::collections::BTreeMap::from([(
                    target,
                    "resources/original.bin",
                )]))
                .unwrap(),
            )
            .unwrap();
            let result = restore_aliases(dir.path(), 14);
            assert_eq!(result.is_ok(), succeeds);
            if succeeds {
                assert_eq!(
                    fs::read(dir.path().join(target)).unwrap(),
                    b"shared content"
                );
            }
        }
    }
    #[test]
    fn appended_zip_extracts_and_rejects_traversal() {
        for name in ["scripts/main", "../escape"] {
            let dir = tempfile::tempdir().unwrap();
            let file = dir.path().join("package");
            let mut f = File::create(&file).unwrap();
            f.write_all(b"executable stub").unwrap();
            let mut z = zip::ZipWriter::new(f);
            z.start_file(name, zip::write::SimpleFileOptions::default())
                .unwrap();
            z.write_all(b"hello").unwrap();
            z.finish().unwrap();
            let out = dir.path().join("out");
            fs::create_dir(&out).unwrap();
            let result = unpack(File::open(file).unwrap(), &out);
            if name.starts_with("..") {
                assert!(result.is_err());
            } else {
                result.unwrap();
                assert_eq!(fs::read(out.join(name)).unwrap(), b"hello");
            }
        }
    }
}
