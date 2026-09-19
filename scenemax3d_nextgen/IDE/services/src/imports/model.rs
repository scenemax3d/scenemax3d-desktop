//! Private staging for model preview. Preparing a model never registers it in a project.
use super::*;
/// Open or create the small project-local import draft used by ordinary Save/Undo commands.
pub fn open_draft(project: &Path) -> io::Result<scenemax_ide_core::Document> {
    use std::io::{Read, Write};
    let root = project.canonicalize()?;
    let folder = root.join(".scenemax-studio/imports");
    fs::create_dir_all(&folder)?;
    let folder = folder.canonicalize()?;
    if !folder.starts_with(&root) {
        return Err(error("Import draft folder escapes the project"));
    }
    let path = folder.join("Import 3D Model.smmodelimport");
    match fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&path)
    {
        Ok(mut file) => file.write_all(&serde_json::to_vec_pretty(
            &scenemax_ide_core::model_import::draft(),
        )?)?,
        Err(e) if e.kind() == io::ErrorKind::AlreadyExists => {}
        Err(e) => return Err(e),
    }
    let canonical = path.canonicalize()?;
    if !canonical.starts_with(&root) {
        return Err(error("Import draft escapes the project"));
    }
    let mut bytes = Vec::new();
    fs::File::open(&canonical)?
        .take(8 * 1024 * 1024 + 1)
        .read_to_end(&mut bytes)?;
    if bytes.len() > 8 * 1024 * 1024 {
        return Err(error("Import draft is too large"));
    }
    let value: Value = serde_json::from_slice(&bytes)?;
    scenemax_ide_core::model_import::validate(&value).map_err(error)?;
    scenemax_ide_core::Document::from_bytes(canonical, bytes).map_err(|e| error(e.to_string()))
}
/// An owned, temporary glTF model ready for the embedded viewport.
pub struct Prepared {
    /// Private asset root, removed when the import document releases it.
    pub directory: tempfile::TempDir,
    /// Prepared glTF/GLB path.
    pub path: PathBuf,
    /// Canonical root used by the scoped Bevy asset reader.
    pub root: PathBuf,
    /// Original user-selected source.
    pub source: PathBuf,
    /// Bundled animation names in source order.
    pub clips: Vec<String>,
    /// Source mesh count.
    pub meshes: usize,
    /// Source material count.
    pub materials: usize,
    /// Source node count.
    pub nodes: usize,
    /// Model entries discovered in an archive.
    pub entries: Vec<String>,
}
/// Copy dependencies and convert FBX on a worker; no project writes occur.
pub fn prepare(source: &Path) -> io::Result<Prepared> {
    prepare_with_options(source, &scenemax_ide_core::model_import::draft())
}
/// Prepare and optionally optimize the exact artifact that will be previewed and imported.
pub fn prepare_with_options(source: &Path, draft: &Value) -> io::Result<Prepared> {
    scenemax_ide_core::model_import::validate(draft).map_err(error)?;
    let source = source.canonicalize()?;
    let directory = tempfile::Builder::new()
        .prefix("scenemax-model-preview-")
        .tempdir()?;
    let root = directory.path().canonicalize()?;
    let mut entries = Vec::new();
    let input = if source
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("zip"))
    {
        let unpacked = root.join("package");
        fs::create_dir(&unpacked)?;
        let mut archive =
            zip::ZipArchive::new(fs::File::open(&source)?).map_err(|e| error(e.to_string()))?;
        if archive.len() > 4096 {
            return Err(error("Model package contains too many files"));
        }
        let mut total = 0u64;
        for i in 0..archive.len() {
            let mut item = archive.by_index(i).map_err(|e| error(e.to_string()))?;
            let relative = item
                .enclosed_name()
                .ok_or_else(|| error("Unsafe path in model package"))?;
            if item.unix_mode().is_some_and(|m| m & 0o170000 == 0o120000) {
                return Err(error("Linked files are not allowed in model packages"));
            }
            total = total
                .checked_add(item.size())
                .ok_or_else(|| error("Package is too large"))?;
            if total > 2_000_000_000 {
                return Err(error("Unpacked package exceeds 2 GB"));
            }
            let path = unpacked.join(relative);
            if item.is_dir() {
                fs::create_dir_all(&path)?;
            } else {
                if let Some(parent) = path.parent() {
                    fs::create_dir_all(parent)?;
                }
                let mut file = fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .open(&path)?;
                std::io::copy(&mut item, &mut file)?;
                if path.extension().is_some_and(|e| {
                    ["glb", "gltf", "fbx", "obj"]
                        .contains(&e.to_string_lossy().to_ascii_lowercase().as_str())
                }) {
                    entries.push(
                        path.strip_prefix(&unpacked)
                            .map_err(|e| error(e.to_string()))?
                            .to_string_lossy()
                            .replace('\\', "/"),
                    );
                }
            }
        }
        entries.sort_by_key(|p| {
            (
                if p.to_lowercase().ends_with(".glb") {
                    0
                } else if p.to_lowercase().ends_with(".gltf") {
                    1
                } else {
                    2
                },
                p.clone(),
            )
        });
        let choice = draft["archiveEntry"]
            .as_str()
            .filter(|s| !s.is_empty())
            .map(str::to_owned)
            .or_else(|| entries.first().cloned())
            .ok_or_else(|| error("No supported models in this package"))?;
        if !entries.contains(&choice) {
            return Err(error("The selected model is not in this package"));
        }
        unpacked.join(choice)
    } else {
        source.clone()
    };
    let working = root.join("source");
    fs::create_dir(&working)?;
    let mut path = if input
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("obj"))
    {
        let out = working.join("model.glb");
        optimization::pack(&input, &out, None)?;
        out
    } else {
        files::copy_asset(&input, &working, Kind::Model)?
    };
    if draft["optimization"]["enabled"].as_bool() == Some(true) {
        path = optimization::optimize(
            &path,
            &root,
            &draft["optimization"],
            draft["isStatic"].as_bool().unwrap_or(false),
        )?;
    }
    let path = path.canonicalize()?;
    let data = files::gltf(&path)?;
    let count = |key: &str| data[key].as_array().map_or(0, Vec::len);
    Ok(Prepared {
        root,
        directory,
        path: path.clone(),
        source,
        clips: files::clips(&path)?,
        meshes: count("meshes"),
        materials: count("materials"),
        nodes: count("nodes"),
        entries,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    #[test]
    fn import_draft_uses_normal_save_and_reopens_settings() {
        let root = tempfile::tempdir().unwrap();
        let mut doc = open_draft(root.path()).unwrap();
        let mut draft: Value = serde_json::from_str(doc.text()).unwrap();
        draft["name"] = json!("saved_asset");
        doc.replace_text(serde_json::to_string_pretty(&draft).unwrap());
        crate::Filesystem::save_document(&mut doc).unwrap();
        let reopened = open_draft(root.path()).unwrap();
        assert_eq!(reopened.text(),doc.text());
        assert!(!root.path().join("resources").exists());
    }
    fn package(root: &Path, names: &[&str]) -> PathBuf {
        let path = root.join("models.zip");
        let mut archive = zip::ZipWriter::new(fs::File::create(&path).unwrap());
        for name in names {
            archive
                .start_file(*name, zip::write::SimpleFileOptions::default())
                .unwrap();
            archive
                .write_all(br#"{"asset":{"version":"2.0"},"scenes":[{}],"nodes":[]}"#)
                .unwrap();
        }
        archive.finish().unwrap();
        path
    }
    #[test]
    fn zip_selection_is_private_and_cleanup_preserves_source() {
        let root = tempfile::tempdir().unwrap();
        let path = package(root.path(), &["one.gltf", "nested/two.gltf"]);
        let mut draft = scenemax_ide_core::model_import::draft();
        draft["archiveEntry"] = json!("nested/two.gltf");
        let staged = prepare_with_options(&path, &draft).unwrap();
        assert_eq!(staged.entries.len(), 2);
        assert!(staged.path.ends_with("two.gltf"));
        let temporary = staged.root.clone();
        drop(staged);
        assert!(!temporary.exists());
        assert!(path.exists());
        assert!(!root.path().join("resources").exists());
        draft["archiveEntry"] = json!("absent.gltf");
        assert!(prepare_with_options(&path, &draft).is_err());
    }
    #[test]
    fn zip_rejects_traversal() {
        let root = tempfile::tempdir().unwrap();
        let path = package(root.path(), &["../outside.gltf"]);
        assert!(prepare(&path).is_err());
        assert!(!root.path().join("outside.gltf").exists());
    }
    #[test]
    fn commit_preserves_all_runtime_metadata_without_preview_pose() {
        let root = tempfile::tempdir().unwrap();
        let source = root.path().join("source.gltf");
        fs::write(&source, br#"{"asset":{"version":"2.0"},"scenes":[{}]}"#).unwrap();
        let staged = prepare(&source).unwrap();
        let mut draft = scenemax_ide_core::model_import::draft();
        draft["scaleX"] = json!(3.);
        draft["rotateY"] = json!(90.);
        draft["transY"] = json!(2.);
        draft["character"]["capsuleRadius"] = json!(0.7);
        draft["preview"]["position"] = json!([10., 20., 30.]);
        let req = Request {
            kind: Kind::Model,
            source: staged.path.clone(),
            name: "calibrated".into(),
            rows: 1,
            cols: 1,
            frame_width: 1.,
            frame_height: 0.,
            clip: String::new(),
            scale: 1.,
            model: Some(draft),
            effect: None,
        };
        let result = import(root.path(), &req).unwrap();
        let index: Value = serde_json::from_slice(&fs::read(result.document).unwrap()).unwrap();
        let saved = &index["models"][0];
        assert_eq!(saved["scaleX"], 3.);
        assert_eq!(saved["rotateY"], 90.);
        assert_eq!(saved["transY"], 2.);
        assert_eq!(saved["character"]["capsuleRadius"], 0.7);
        assert!(saved.get("preview").is_none());
    }
    #[test]
    fn native_obj_optimization_produces_loadable_gltf() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("triangle.obj");
        fs::write(&path, "o Triangle\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n").unwrap();
        let mut draft = scenemax_ide_core::model_import::draft();
        draft["optimization"]["enabled"] = json!(true);
        let prepared = prepare_with_options(&path, &draft).unwrap();
        assert!(prepared.meshes > 0);
        assert!(prepared.path.ends_with("model.glb"));
    }
}
