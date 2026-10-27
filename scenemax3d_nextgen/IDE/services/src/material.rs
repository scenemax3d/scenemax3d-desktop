//! Bounded resource inventory for the material editor, executed on the storage worker.
use std::{
    fs,
    path::{Path, PathBuf},
};
/// Supported texture and model files relative to a canonical resource root.
#[derive(Default)]
pub struct Library {
    /// Canonical project resource directory.
    pub root: PathBuf,
    /// Image paths suitable for PBR textures.
    pub textures: Vec<String>,
    /// glTF/GLB preview paths.
    pub models: Vec<String>,
    /// Shared root for builtin model preview paths.
    pub shared_root: Option<PathBuf>,
}
impl Library {
    /// Resolve a preview selection to its owning resource directory and file.
    pub fn model_path(&self, name: &str) -> Option<(&Path, PathBuf)> {
        if !self.models.iter().any(|model| model == name) {
            return None;
        }
        let (root, relative) = match name.strip_prefix("builtin://") {
            Some(relative) => (self.shared_root.as_deref()?, relative),
            None => (self.root.as_path(), name),
        };
        Some((root, root.join(relative)))
    }
}
/// Enumerate files without following links or junctions.
pub fn load(project: &Path) -> Result<Library, String> {
    let mut result = load_root(&project.join("resources"))?;
    if let Some(shared) = crate::resource_roots::shared(project) {
        let library = load_root(&shared)?;
        result.models.extend(
            library
                .models
                .into_iter()
                .map(|path| format!("builtin://{path}")),
        );
        result.shared_root = Some(shared);
    }
    Ok(result)
}
fn load_root(path: &Path) -> Result<Library, String> {
    if !path.exists() {
        return Ok(Library {
            root: path.to_path_buf(),
            ..Default::default()
        });
    }
    let root = path.canonicalize().map_err(|e| e.to_string())?;
    let mut result = Library {
        root: root.clone(),
        ..Default::default()
    };
    let mut pending = vec![(root.clone(), 0)];
    let mut count = 0;
    while let Some((dir, depth)) = pending.pop() {
        if depth > 32 {
            return Err("Resource folder nesting exceeds 32 levels".into());
        }
        for entry in fs::read_dir(dir).map_err(|e| e.to_string())? {
            count += 1;
            if count > 100_000 {
                return Err("Resource inventory exceeds 100,000 entries".into());
            }
            let entry = entry.map_err(|e| e.to_string())?;
            let meta = fs::symlink_metadata(entry.path()).map_err(|e| e.to_string())?;
            if meta.file_type().is_symlink() {
                continue;
            }
            #[cfg(windows)]
            {
                use std::os::windows::fs::MetadataExt;
                if meta.file_attributes() & 0x400 != 0 {
                    continue;
                }
            }
            if meta.is_dir() {
                pending.push((entry.path(), depth + 1));
                continue;
            }
            if !meta.is_file() {
                continue;
            }
            let path = entry.path();
            let ext = path
                .extension()
                .and_then(|v| v.to_str())
                .unwrap_or("")
                .to_lowercase();
            let relative = path
                .strip_prefix(&root)
                .map_err(|e| e.to_string())?
                .to_string_lossy()
                .replace('\\', "/");
            match ext.as_str() {
                "png" | "jpg" | "jpeg" => result.textures.push(relative),
                "glb" | "gltf" => result.models.push(relative),
                _ => {}
            }
        }
    }
    result.textures.sort();
    result.models.sort();
    Ok(result)
}

/// Read valid native materials from the scripts tree, rejecting links and ambiguous names.
pub fn documents(
    project: &Path,
) -> Result<std::collections::BTreeMap<String, serde_json::Value>, String> {
    let root = project.join("scripts");
    let mut result = std::collections::BTreeMap::new();
    let mut pending = vec![(root, 0)];
    let mut count = 0;
    while let Some((dir, depth)) = pending.pop() {
        if depth > 32 {
            return Err("Material folder nesting exceeds 32 levels".into());
        }
        if !dir.exists() {
            continue;
        }
        for entry in fs::read_dir(dir).map_err(|e| e.to_string())? {
            count += 1;
            if count > 100_000 {
                return Err("Material inventory exceeds limit".into());
            }
            let entry = entry.map_err(|e| e.to_string())?;
            let path = entry.path();
            let meta = fs::symlink_metadata(&path).map_err(|e| e.to_string())?;
            if meta.file_type().is_symlink() {
                continue;
            }
            #[cfg(windows)]
            {
                use std::os::windows::fs::MetadataExt;
                if meta.file_attributes() & 0x400 != 0 {
                    continue;
                }
            }
            if meta.is_dir() {
                pending.push((path, depth + 1));
                continue;
            }
            if path.extension().is_some_and(|e| e == "smmat") && meta.len() < 1024 * 1024 {
                let name = path
                    .file_stem()
                    .unwrap_or_default()
                    .to_string_lossy()
                    .into_owned();
                let bytes = fs::read(path).map_err(|e| e.to_string())?;
                let Ok(value) = serde_json::from_slice(&bytes) else {
                    continue;
                };
                if scenemax_assets::material::validate(&value).is_err() {
                    continue;
                }
                if result.insert(name.clone(), value).is_some() {
                    return Err(format!("Duplicate material name: {name}"));
                }
            }
        }
    }
    Ok(result)
}
