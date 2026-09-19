//! Project-local asset imports and resource registration, independent of UI/runtime.
use serde_json::{Value, json};
use std::{
    fs,
    io::{self, Write},
    path::{Path, PathBuf},
};
mod files;
mod optimization;
pub mod model;
pub mod sprite;
pub mod effect;
mod picker;
struct ImportLock(PathBuf);
impl Drop for ImportLock {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.0);
    }
}
pub use picker::pick_file;
/// Supported Assets menu operations.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Kind {
    /// glTF or FBX model.
    Model,
    /// glTF or FBX animation.
    Animation,
    /// Sprite image or sheet.
    Sprite,
    /// Audio clip.
    Audio,
    /// Video file.
    Video,
    /// Effekseer effect and companion assets.
    Effect,
}
impl Kind {
    /// User-facing operation name.
    pub fn title(self) -> &'static str {
        match self {
            Self::Model => "Import 3D Model",
            Self::Animation => "Import Animation",
            Self::Sprite => "Import Sprite",
            Self::Audio => "Import Audio",
            Self::Video => "Import Video",
            Self::Effect => "Import Effekseer Effect",
        }
    }
    /// Accepted source formats.
    pub fn extensions(self) -> &'static [&'static str] {
        match self {
            Self::Model => &["glb", "gltf", "fbx", "obj", "zip"],
            Self::Animation => &["glb", "gltf", "fbx"],
            Self::Sprite => &["png", "jpg", "jpeg"],
            Self::Audio => &["wav", "ogg"],
            Self::Video => &["mp4", "webm", "mov", "avi", "mkv", "ogv"],
            Self::Effect => &["efkefc", "efkproj", "efk"],
        }
    }
    fn location(self) -> (&'static str, &'static str, &'static str) {
        match self {
            Self::Model => ("Models", "models", "models"),
            Self::Animation => ("animations", "animations", "animations"),
            Self::Sprite => ("sprites", "sprites", "sprites"),
            Self::Audio => ("audio", "audio", "sounds"),
            Self::Video => ("videos", "videos", "videos"),
            Self::Effect => ("effects", "effects", "effects"),
        }
    }
}
/// User-reviewed import settings.
#[derive(Clone, Debug)]
pub struct Request {
    /// Import category.
    pub kind: Kind,
    /// Source file selected by the user.
    pub source: PathBuf,
    /// Resource identifier.
    pub name: String,
    /// Sprite sheet rows.
    pub rows: u32,
    /// Sprite sheet columns.
    pub cols: u32,
    /// Sprite display frame width.
    pub frame_width: f32,
    /// Sprite display frame height.
    pub frame_height: f32,
    /// Animation clip name (empty selects the first named clip).
    pub clip: String,
    /// Uniform imported model scale.
    pub scale: f32,
    /// Full model import metadata, when supplied by the model designer.
    pub model: Option<Value>,
    /// Effect settings supplied by the native effect designer.
    pub effect: Option<Value>,
}
/// Committed import paths.
#[derive(Debug)]
pub struct Outcome {
    /// Resource index or effect designer document.
    pub document: PathBuf,
    /// Copied asset file.
    pub asset: PathBuf,
}
fn error(message: impl Into<String>) -> io::Error {
    io::Error::other(message.into())
}
/// Import into a private staging directory, then atomically publish the resource index.
/// Existing names and files are never overwritten; failed imports remove only their own staging data.
pub fn import(root: &Path, request: &Request) -> io::Result<Outcome> {
    let root = root.canonicalize()?;
    let name = request.name.trim();
    if name.is_empty()
        || !name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-')
    {
        return Err(error(
            "Use letters, numbers, underscores or hyphens for the asset name",
        ));
    }
    let source = request.source.canonicalize()?;
    if !source.is_file() {
        return Err(error("Select a source file"));
    }
    let ext = source
        .extension()
        .unwrap_or_default()
        .to_string_lossy()
        .to_ascii_lowercase();
    if !request.kind.extensions().contains(&ext.as_str()) {
        return Err(error("Unsupported source format for this import tool"));
    }
    if request.rows == 0
        || request.cols == 0
        || request.rows > 4096
        || request.cols > 4096
        || !request.frame_width.is_finite()
        || request.frame_width <= 0.
        || !request.frame_height.is_finite()
        || request.frame_height < 0.
        || !request.scale.is_finite()
        || request.scale <= 0.
    {
        return Err(error(
            "Sprite dimensions and model scale must be valid positive numbers",
        ));
    }
    let resources = root.join("resources");
    fs::create_dir_all(&resources)?;
    if !resources.canonicalize()?.starts_with(&root) {
        return Err(error("Resources folder is outside the project"));
    }
    let (folder, stem, key) = request.kind.location();
    let category = resources.join(folder);
    fs::create_dir_all(&category)?;
    if !category.canonicalize()?.starts_with(&root) {
        return Err(error("Asset folder is outside the project"));
    }
    // Serialize import commits across IDE processes, without touching existing locks.
    let lock_path = category.join(".asset-import.lock");
    let _file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&lock_path)
        .map_err(|_| error("Another import is active in this asset folder"))?;
    drop(_file);
    let _lock = ImportLock(lock_path);
    if index_is_external(&category, stem, &root)? {
        return Err(error("Resource index is outside the project"));
    }
    let lock = tempfile::Builder::new()
        .prefix(".import-")
        .tempdir_in(&category)?;
    let index_path = category.join(format!("{stem}-ext.json"));
    let old = if index_path.exists() {
        Some(fs::read(&index_path)?)
    } else {
        None
    };
    let mut index: Value = match &old {
        Some(bytes) => serde_json::from_slice(bytes)?,
        None => json!({key: []}),
    };
    let entries = index
        .get_mut(key)
        .and_then(Value::as_array_mut)
        .ok_or_else(|| error("Invalid resource index"))?;
    if entries.iter().any(|v| {
        v["name"]
            .as_str()
            .is_some_and(|s| s.eq_ignore_ascii_case(name))
    }) {
        return Err(error("An asset with this name already exists"));
    }
    let builtin = category.join(format!("{stem}.json"));
    if builtin.is_file() {
        let data: Value = serde_json::from_slice(&fs::read(builtin)?)?;
        if data[key].as_array().is_some_and(|a| {
            a.iter().any(|v| {
                v["name"]
                    .as_str()
                    .is_some_and(|s| s.eq_ignore_ascii_case(name))
            })
        }) {
            return Err(error("An asset with this name already exists"));
        }
    }
    let destination = category.join(name);
    if destination.exists() {
        return Err(error("The destination asset folder already exists"));
    }
    let staged = lock.path().join("asset");
    fs::create_dir(&staged)?;
    let copied = files::copy_asset(&source, &staged, request.kind)?;
    let file_name = copied
        .file_name()
        .ok_or_else(|| error("Missing file name"))?;
    let asset = destination.join(file_name);
    let path = format!("{folder}/{name}/{}", file_name.to_string_lossy());
    let mut entry = json!({"name": name, "path": path});
    match request.kind {
        Kind::Sprite => {
            entry["rows"] = json!(request.rows);
            entry["cols"] = json!(request.cols);
            entry["frameWidth"] = json!(request.frame_width);
            entry["frameHeight"] = json!(request.frame_height);
        }
        Kind::Model => {
            for key in ["scaleX", "scaleY", "scaleZ"] {
                entry[key] = json!(request.scale);
            }
        }
        Kind::Animation => {
            let clips = files::clips(&copied)?;
            if clips.is_empty() {
                return Err(error("The file contains no animations"));
            }
            let clip = if request.clip.trim().is_empty() {
                clips[0].clone()
            } else {
                request.clip.trim().to_owned()
            };
            if !clips.contains(&clip) {
                return Err(error(format!(
                    "Clip not found. Available clips: {}",
                    clips.join(", ")
                )));
            }
            entry["clipName"] = json!(clip);
        }
        Kind::Video => {
            entry["previewShape"] = json!("PANE");
        }
        _ => {}
    }
    if let Some(model) = &request.model && request.kind == Kind::Model {
        let metadata=scenemax_ide_core::model_import::metadata(model).map_err(error)?;
        if let Some(fields)=metadata.as_object() {for (k,v) in fields {entry[k]=v.clone();}}
    }
    entries.push(entry);
    let document = if request.kind == Kind::Effect {
        root.join("scripts").join(format!("{name}.smeffectdesign"))
    } else {
        index_path.clone()
    };
    if request.kind == Kind::Effect && document.exists() {
        return Err(error("Effect document already exists"));
    }
    let mut pending = tempfile::NamedTempFile::new_in(&category)?;
    pending.write_all(&serde_json::to_vec_pretty(&index)?)?;
    pending.as_file().sync_all()?;
    // Detect changes made during conversion before publishing anything.
    let current = if index_path.exists() {
        Some(fs::read(&index_path)?)
    } else {
        None
    };
    if old != current {
        return Err(error("Asset index changed during import; retry"));
    }
    fs::rename(&staged, &destination)?;
    let commit = (|| {
        if request.kind == Kind::Effect {
            fs::create_dir_all(root.join("scripts"))?;
            if !root.join("scripts").canonicalize()?.starts_with(&root) {
                return Err(error("Scripts folder is outside project"));
            }
            let mut effect = json!({"version":1,"name":name,"assetId":name,"source":{"importedEffectFile":path,"originalFormat":ext,"originalImportPath":source},"preview":{"loop":true,"playbackSpeed":1.0}});
            if let Some(settings) = &request.effect {
                effect["source"]["originalImportPath"] = settings["originalImportPath"].clone();
                effect["preview"] = settings["settings"].clone();
                if let Some(fields)=effect["preview"].as_object_mut() {fields.remove("source");}
                effect["preview"]["playbackSpeed"] = settings["settings"]["speed"].clone();
            }
            let mut f = tempfile::NamedTempFile::new_in(root.join("scripts"))?;
            f.write_all(&serde_json::to_vec_pretty(&effect)?)?;
            f.persist_noclobber(&document).map_err(|e| e.error)?;
        } else {
            pending.persist(&index_path).map_err(|e| e.error)?;
        }
        Ok(Outcome { document, asset })
    })();
    if commit.is_err() {
        fs::remove_dir_all(&destination)?;
    }
    commit
}

fn index_is_external(category: &Path, stem: &str, root: &Path) -> io::Result<bool> {
    for suffix in [".json", "-ext.json"] {
        let path = category.join(format!("{stem}{suffix}"));
        if path.exists() && !path.canonicalize()?.starts_with(root) {
            return Ok(true);
        }
    }
    Ok(false)
}

#[cfg(test)]
mod tests;
