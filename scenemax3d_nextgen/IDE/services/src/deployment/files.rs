use super::Context;
use scenemax_ide_core::deployment::{Settings, Target};
use std::{
    collections::{BTreeMap, HashMap},
    fs::{self, File},
    hash::{Hash, Hasher},
    io::{self, Read, Write},
    path::{Path, PathBuf},
};
use zip::write::SimpleFileOptions;

pub(super) fn load_settings(root: &Path) -> io::Result<Settings> {
    let path = root.join(".scenemax-studio/deployment.json");
    let mut settings = if path.is_file() {
        serde_json::from_slice(&read_small(&path)?)?
    } else {
        Settings::default()
    };
    if settings.output.is_empty() {
        settings.output = root.join("build_games").to_string_lossy().into_owned();
    }
    if settings.platforms.len() != Target::ALL.len()
        || Target::ALL.iter().any(|target| {
            settings
                .platforms
                .iter()
                .filter(|p| p.target == *target)
                .count()
                != 1
        })
    {
        return Err(io::Error::other(
            "Deployment settings must contain each of the six platforms exactly once.",
        ));
    }
    if !path.is_file() {
        let shared = super::workspace().join("../resources");
        if shared.is_dir() {
            settings.builtin_resources = shared.canonicalize()?.to_string_lossy().into_owned();
        }
        import_java_defaults(root, &mut settings);
    }
    Ok(settings)
}

fn import_java_defaults(root: &Path, settings: &mut Settings) {
    let installation = super::workspace().join("..");
    let catalog = installation.join("projects/projects.json");
    if let Ok(bytes) = read_small(&catalog)
        && let Ok(value) = serde_json::from_slice::<serde_json::Value>(
            bytes.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&bytes),
        )
        && let Some(rows) = value["projects"].as_array()
    {
        for row in rows {
            let Some(path) = row["path"].as_str() else {
                continue;
            };
            let path = installation.join(path);
            if path.canonicalize().ok() != root.canonicalize().ok() {
                continue;
            }
            settings.itch_page = row["itch_game_page"].as_str().unwrap_or_default().into();
            for p in &mut settings.platforms {
                let key = match p.target {
                    Target::Windows => "itch_windows_channel",
                    Target::Linux => "itch_linux_channel",
                    Target::Macos => "itch_mac_channel",
                    _ => continue,
                };
                if let Some(channel) = row[key].as_str().filter(|s| !s.is_empty()) {
                    p.channel = channel.into();
                }
            }
            break;
        }
    }
    let executable = if cfg!(windows) {
        "butler.exe"
    } else {
        "butler"
    };
    let shipped = installation.join("tools/butler");
    let mut candidates = vec![shipped.join(executable)];
    if let Ok(entries) = fs::read_dir(shipped) {
        for entry in entries.take(32).flatten() {
            candidates.push(entry.path().join(executable));
        }
    }
    if let Some(appdata) = std::env::var_os("APPDATA") {
        let base = PathBuf::from(appdata).join("itch/broth/butler");
        if let Ok(version) = fs::read_to_string(base.join(".chosen-version")) {
            let version = version.trim();
            if !version.is_empty()
                && version
                    .chars()
                    .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-')
            {
                candidates.push(base.join("versions").join(version).join(executable));
            }
        }
    }
    if let Some(path) = candidates.into_iter().find(|p| p.is_file()) {
        settings.butler = path.to_string_lossy().into_owned();
    }
}
pub(super) fn save_settings(root: &Path, settings: &Settings) -> io::Result<()> {
    let folder = root.join(".scenemax-studio");
    fs::create_dir_all(&folder)?;
    let canonical = folder.canonicalize()?;
    if !canonical.starts_with(root.canonicalize()?) {
        return Err(io::Error::other(
            "Deployment settings directory escapes the project.",
        ));
    }
    let path = folder.join("deployment.json");
    if path.exists() {
        regular(&path)?;
    }
    let mut json = if path.exists() {
        serde_json::from_slice(&read_small(&path)?)?
    } else {
        serde_json::json!({})
    };
    let object = json
        .as_object_mut()
        .ok_or_else(|| io::Error::other("Deployment settings must be a JSON object."))?;
    if let Some(new) = serde_json::to_value(settings)?.as_object() {
        object.extend(new.clone());
    }
    let mut temp = tempfile::NamedTempFile::new_in(folder)?;
    temp.write_all(&serde_json::to_vec_pretty(&json)?)?;
    temp.persist(path).map_err(|e| e.error)?;
    Ok(())
}
pub(super) fn read_small(path: &Path) -> io::Result<Vec<u8>> {
    let mut data = Vec::new();
    File::open(path)?
        .take(1024 * 1024 + 1)
        .read_to_end(&mut data)?;
    if data.len() > 1024 * 1024 {
        return Err(io::Error::other("Configuration exceeds 1 MiB."));
    }
    Ok(data)
}
pub(super) fn regular(path: &Path) -> io::Result<fs::Metadata> {
    let metadata = fs::symlink_metadata(path)?;
    let linked = metadata.file_type().is_symlink();
    #[cfg(windows)]
    let linked = {
        use std::os::windows::fs::MetadataExt;
        linked || metadata.file_attributes() & 0x400 != 0
    };
    if linked || (!metadata.is_file() && !metadata.is_dir()) {
        return Err(io::Error::other(format!(
            "Linked or special files are not packaged: {}",
            path.display()
        )));
    }
    Ok(metadata)
}
pub(super) fn list(root: &Path, context: &Context) -> io::Result<Vec<PathBuf>> {
    let mut result = Vec::new();
    let mut queue = vec![root.to_owned()];
    let mut bytes = 0u64;
    while let Some(folder) = queue.pop() {
        context.check()?;
        regular(&folder)?;
        for entry in fs::read_dir(&folder)? {
            let path = entry?.path();
            let meta = regular(&path)?;
            if path
                .strip_prefix(root)
                .map_or(true, |p| p.components().count() > 64)
            {
                return Err(io::Error::other("Package paths exceed 64 levels."));
            }
            if meta.is_dir() {
                queue.push(path);
            } else {
                bytes = bytes.saturating_add(meta.len());
                result.push(path);
            }
            if result.len() + queue.len() > 100_000 || bytes > 16 * 1024 * 1024 * 1024 {
                return Err(io::Error::other("Package exceeds 100,000 files or 16 GiB."));
            }
        }
    }
    result.sort();
    Ok(result)
}
pub(super) fn copy(source: &Path, target: &Path, context: &Context) -> io::Result<()> {
    context.check()?;
    let before = regular(source)?;
    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut input = File::open(source)?;
    let mut output = File::options().write(true).create_new(true).open(target)?;
    let mut buffer = [0u8; 256 * 1024];
    let mut total = 0;
    loop {
        context.check()?;
        let n = input.read(&mut buffer)?;
        if n == 0 {
            break;
        }
        total += n as u64;
        if total > before.len() {
            return Err(io::Error::other(
                "Source grew during packaging. Retry with a stable project.",
            ));
        }
        output.write_all(&buffer[..n])?;
    }
    let after = regular(source)?;
    if total != before.len() || before.modified()? != after.modified()? {
        return Err(io::Error::other(format!(
            "Source changed during packaging: {}",
            source.display()
        )));
    }
    Ok(())
}
pub(super) fn tree(source: &Path, destination: &Path, context: &Context) -> io::Result<()> {
    let paths = list(source, context)?;
    let stamps = paths
        .iter()
        .map(|path| {
            let m = regular(path)?;
            Ok((m.len(), m.modified()?))
        })
        .collect::<io::Result<Vec<_>>>()?;
    for path in &paths {
        let relative = path.strip_prefix(source).map_err(io::Error::other)?;
        copy(path, &destination.join(relative), context)?;
    }
    if list(source, context)? != paths {
        return Err(io::Error::other(
            "Source file inventory changed during packaging. Retry with a stable project.",
        ));
    }
    for (path, stamp) in paths.iter().zip(stamps) {
        context.check()?;
        let m = regular(path)?;
        if stamp != (m.len(), m.modified()?) {
            return Err(io::Error::other(format!(
                "Source changed during packaging: {}",
                path.display()
            )));
        }
    }
    Ok(())
}
pub(super) fn zip(
    root: &Path,
    destination: &Path,
    stub: Option<&Path>,
    context: &Context,
) -> io::Result<()> {
    let mut file = File::options()
        .create_new(true)
        .write(true)
        .read(true)
        .open(destination)?;
    if let Some(stub) = stub {
        io::copy(&mut File::open(stub)?, &mut file)?;
    }
    let mut archive = zip::ZipWriter::new(file);
    let paths = list(root, context)?;
    let mut sizes = HashMap::<u64, usize>::new();
    for path in &paths {
        *sizes.entry(regular(path)?.len()).or_default() += 1;
    }
    let mut seen = HashMap::<(u64, u64), PathBuf>::new();
    let mut aliases = BTreeMap::new();
    let mut saved = 0u64;
    for path in paths {
        context.check()?;
        let relative = path
            .strip_prefix(root)
            .map_err(io::Error::other)?
            .to_str()
            .ok_or_else(|| io::Error::other("Package paths must be Unicode."))?
            .replace('\\', "/");
        if stub.is_some() && relative == ".scenemax-package-aliases.json" {
            return Err(io::Error::other("Reserved package metadata filename."));
        }
        let size = regular(&path)?.len();
        // Hash only duplicate-sized resources; verify bytes before deduplicating.
        // Do not alias executables, whose permissions and identity must be retained.
        if stub.is_some() && relative.contains("resources/") && size >= 65536 && sizes[&size] > 1 {
            let digest = content_hash(&path, context)?;
            if let Some(previous) = seen.get(&(size, digest)) {
                if same_content(previous, &path, context)? {
                    aliases.insert(
                        relative,
                        previous
                            .strip_prefix(root)
                            .map_err(io::Error::other)?
                            .to_string_lossy()
                            .replace('\\', "/"),
                    );
                    saved += size;
                    continue;
                }
            } else {
                seen.insert((size, digest), path.clone());
            }
        }
        let executable = relative == "projector"
            || (stub.is_none() && !relative.contains('/') && !relative.contains('.'));
        let compressed_media = path.extension().and_then(|e| e.to_str()).is_some_and(|e| {
            matches!(
                e.to_ascii_lowercase().as_str(),
                "png" | "jpg" | "jpeg" | "mp4" | "ogg" | "zip"
            )
        });
        archive.start_file(
            relative,
            SimpleFileOptions::default()
                .compression_method(if compressed_media {
                    zip::CompressionMethod::Stored
                } else {
                    zip::CompressionMethod::Deflated
                })
                .compression_level(if compressed_media { None } else { Some(3) })
                .unix_permissions(if executable { 0o755 } else { 0o644 }),
        )?;
        let mut input = File::open(path)?;
        let mut buffer = [0; 256 * 1024];
        loop {
            context.check()?;
            let n = input.read(&mut buffer)?;
            if n == 0 {
                break;
            }
            archive.write_all(&buffer[..n])?;
        }
    }
    if !aliases.is_empty() {
        archive.start_file(
            ".scenemax-package-aliases.json",
            SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated),
        )?;
        archive.write_all(&serde_json::to_vec(&aliases)?)?;
        context.log(format!(
            "Deduplicated {} resource files ({:.1} MiB).\n",
            aliases.len(),
            saved as f64 / 1048576.
        ));
    }
    archive.finish()?.sync_all()?;
    #[cfg(unix)]
    if stub.is_some() {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(destination, fs::Permissions::from_mode(0o755))?;
    }
    Ok(())
}
fn content_hash(path: &Path, context: &Context) -> io::Result<u64> {
    let mut input = File::open(path)?;
    let mut hash = std::collections::hash_map::DefaultHasher::new();
    let mut buffer = [0; 256 * 1024];
    loop {
        context.check()?;
        let n = input.read(&mut buffer)?;
        if n == 0 {
            break;
        }
        buffer[..n].hash(&mut hash);
    }
    Ok(hash.finish())
}
fn same_content(a: &Path, b: &Path, context: &Context) -> io::Result<bool> {
    let (mut a, mut b) = (File::open(a)?, File::open(b)?);
    let (mut left, mut right) = ([0; 256 * 1024], [0; 256 * 1024]);
    loop {
        context.check()?;
        let n = a.read(&mut left)?;
        if n == 0 {
            return Ok(b.read(&mut right)? == 0);
        }
        b.read_exact(&mut right[..n])?;
        if left[..n] != right[..n] {
            return Ok(false);
        }
    }
}
pub(super) fn native_binary(path: &Path, target: Target) -> io::Result<()> {
    regular(path)?;
    let mut magic = [0u8; 4];
    File::open(path)?.read_exact(&mut magic)?;
    let valid = match target {
        Target::Windows => &magic[..2] == b"MZ",
        Target::Linux => magic == *b"\x7fELF",
        Target::Macos => matches!(
            magic,
            [0xcf, 0xfa, 0xed, 0xfe] | [0xfe, 0xed, 0xfa, 0xcf] | [0xca, 0xfe, 0xba, 0xbe]
        ),
        _ => false,
    };
    if valid {
        Ok(())
    } else {
        Err(io::Error::other(format!(
            "{} is not a {} executable.",
            path.display(),
            target.id()
        )))
    }
}
