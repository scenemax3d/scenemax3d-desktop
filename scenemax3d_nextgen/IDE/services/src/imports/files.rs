use super::*;
use std::process::{Command, Stdio};
/// Copy only local dependencies; source symlinks cannot escape the source folder.
fn copy_local(root: &Path, relative: &Path, target: &Path) -> io::Result<()> {
    if relative.is_absolute()
        || relative
            .components()
            .any(|c| !matches!(c, std::path::Component::Normal(_)))
    {
        return Err(error(
            "Asset dependency must be a relative path inside its source folder",
        ));
    }
    let unresolved = root.join(relative);
    if fs::symlink_metadata(&unresolved)?.file_type().is_symlink() {
        return Err(error(
            "Linked asset dependencies must be copied into the source folder first",
        ));
    }
    let source = unresolved.canonicalize()?;
    if !source.starts_with(root) {
        return Err(error("Asset dependency escapes its source folder"));
    }
    if source.is_dir() {
        for item in fs::read_dir(&source)? {
            copy_local(root, &relative.join(item?.file_name()), target)?;
        }
    } else {
        let dest = target.join(relative);
        if let Some(parent) = dest.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::copy(source, dest)?;
    }
    Ok(())
}
pub(super) fn gltf(path: &Path) -> io::Result<Value> {
    let bytes = fs::read(path)?;
    let data = if bytes.starts_with(b"glTF") {
        if bytes.len() < 20 || bytes[4..8] != 2u32.to_le_bytes() || &bytes[16..20] != b"JSON" {
            return Err(error("Invalid GLB header"));
        }
        let length = u32::from_le_bytes(bytes[12..16].try_into().map_err(|_| error("Invalid GLB"))?)
            as usize;
        bytes
            .get(20..20 + length)
            .ok_or_else(|| error("Truncated GLB"))?
    } else {
        &bytes
    };
    let json: Value = serde_json::from_slice(data)?;
    if json["asset"]["version"].as_str() != Some("2.0") {
        return Err(error("Only glTF 2.0 is supported"));
    }
    Ok(json)
}
pub(super) fn clips(path: &Path) -> io::Result<Vec<String>> {
    Ok(gltf(path)?["animations"]
        .as_array()
        .map(|a| {
            a.iter()
                .enumerate()
                .map(|(i, v)| {
                    v["name"]
                        .as_str()
                        .map(str::to_owned)
                        .unwrap_or_else(|| format!("Animation{i}"))
                })
                .collect()
        })
        .unwrap_or_default())
}
pub(super) fn copy_asset(source: &Path, stage: &Path, kind: Kind) -> io::Result<PathBuf> {
    let root = source
        .parent()
        .ok_or_else(|| error("Missing source directory"))?;
    let filename = source
        .file_name()
        .ok_or_else(|| error("Missing source name"))?;
    let ext = source
        .extension()
        .unwrap_or_default()
        .to_string_lossy()
        .to_ascii_lowercase();
    if ext == "fbx" {
        return convert_fbx(source, stage);
    }
    copy_local(root, Path::new(filename), stage)?;
    let copied = stage.join(filename);
    if matches!(kind, Kind::Model | Kind::Animation) {
        let data = gltf(source)?;
        for key in ["buffers", "images"] {
            for entry in data[key].as_array().into_iter().flatten() {
                if let Some(uri) = entry["uri"].as_str() {
                    if uri.starts_with("data:") {
                        continue;
                    }
                    let decoded = decode_uri(uri)?;
                    copy_local(root, Path::new(&decoded), stage)?;
                }
            }
        }
    }
    if kind == Kind::Effect {
        let prepared = effect::prepare(source)?;
        if !prepared.complete() { return Err(error("Missing effect dependencies")); }
        for dep in &prepared.dependencies { copy_local(root, Path::new(&dep.path), stage)?; }
        for sibling in fs::read_dir(root)? {
            let sibling = sibling?;
            let lower = sibling.file_name().to_string_lossy().to_ascii_lowercase();
            if ["texture", "textures", "model", "sound", "material", "curve"]
                .contains(&lower.as_str())
                || sibling.path().extension().is_some_and(|e| {
                    [
                        "efkmat", "efkmodel", "efkcurve", "png", "jpg", "jpeg", "bmp", "tga",
                        "dds", "wav", "ogg", "mp3", "txt", "json",
                    ]
                    .contains(&e.to_string_lossy().to_ascii_lowercase().as_str())
                })
            {
                copy_local(root, Path::new(&sibling.file_name()), stage)?;
            }
        }
    }
    Ok(copied)
}
fn decode_uri(uri: &str) -> io::Result<String> {
    let mut bytes = Vec::new();
    let mut iter = uri.bytes();
    while let Some(c) = iter.next() {
        if c == b'%' {
            let a = iter.next().and_then(|c| (c as char).to_digit(16));
            let b = iter.next().and_then(|c| (c as char).to_digit(16));
            match (a, b) {
                (Some(a), Some(b)) => bytes.push((a * 16 + b) as u8),
                _ => return Err(error("Invalid dependency URI")),
            }
        } else {
            bytes.push(c);
        }
    }
    let result = String::from_utf8(bytes).map_err(|e| error(e.to_string()))?;
    if result.contains(':') || result.contains('\0') {
        return Err(error("Remote asset dependencies are not supported"));
    }
    Ok(result)
}
pub(super) fn hidden(command: &mut Command) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    #[cfg(not(windows))]
    let _ = command;
}
fn convert_fbx(source: &Path, stage: &Path) -> io::Result<PathBuf> {
    let executable = std::env::var_os("SCENEMAX_FBX2GLTF")
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            Path::new(env!("CARGO_MANIFEST_DIR"))
                .join("../../Tools/vendor/fbx2gltf/FBX2glTF-windows-x86_64.exe")
        });
    if !executable.is_file() {
        return Err(error(
            "FBX2glTF converter is not installed. Set SCENEMAX_FBX2GLTF to its executable, or import GLB/glTF.",
        ));
    }
    let log = fs::File::create(stage.join("conversion.log"))?;
    let mut command = Command::new(executable);
    command
        .args([
            "--binary",
            "--skinning-weights",
            "4",
            "--anim-framerate",
            "bake30",
            "--input",
        ])
        .arg(source)
        .arg("--output")
        .arg(stage.join("model"))
        .stdout(Stdio::from(log.try_clone()?))
        .stderr(Stdio::from(log));
    hidden(&mut command);
    let mut child = command.spawn()?;
    let start = std::time::Instant::now();
    loop {
        if let Some(status) = child.try_wait()? {
            if !status.success() {
                return Err(error(format!(
                    "FBX conversion failed: {}",
                    fs::read_to_string(stage.join("conversion.log"))?
                )));
            }
            break;
        }
        if start.elapsed().as_secs() >= 300 {
            child.kill()?;
            child.wait()?;
            return Err(error("FBX conversion timed out"));
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
    let output = stage.join("model.glb");
    gltf(&output)?;
    Ok(output)
}
