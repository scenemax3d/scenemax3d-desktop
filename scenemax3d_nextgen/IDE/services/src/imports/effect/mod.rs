//! Immutable dependency-audited native effect import staging.
use super::*;
use scenemax_ide_core::effect_import as schema;
mod binary;
mod legacy;
/// A referenced runtime resource and its resolution status.
#[derive(Clone, Debug)]
pub struct Dependency {
    /// Runtime category.
    pub kind: &'static str,
    /// Relative source path.
    pub path: String,
    /// Byte count, or missing.
    pub size: Option<u64>,
}
/// Immutable package snapshot, held for the preview lifetime.
pub struct Prepared {
    /// Original selected source.
    pub source: PathBuf,
    /// Staged runtime binary.
    pub runtime: PathBuf,
    /// Container version.
    pub version: u32,
    /// Direct runtime references.
    pub dependencies: Vec<Dependency>,
    /// Total staged bytes.
    pub bytes: u64,
    _stage: tempfile::TempDir,
}
impl Prepared {
    /// Whether every referenced resource exists.
    pub fn complete(&self) -> bool {
        self.dependencies.iter().all(|d| d.size.is_some())
    }
}
/// Inspect a source and stage its resources without modifying it.
pub fn prepare(source: &Path) -> io::Result<Prepared> {
    use std::io::Read;
    let original = source.canonicalize()?;
    let conversion = tempfile::Builder::new()
        .prefix("scenemax-effect-export-")
        .tempdir()?;
    let source = if original
        .extension()
        .is_some_and(|s| s.eq_ignore_ascii_case("efkproj"))
    {
        legacy::export(&original, conversion.path())?
    } else {
        original.clone()
    };
    let root = original
        .parent()
        .ok_or_else(|| error("Missing effect folder"))?;
    let mut bytes = Vec::new();
    fs::File::open(&source)?
        .take(32 * 1024 * 1024 + 1)
        .read_to_end(&mut bytes)?;
    if bytes.len() > 32 * 1024 * 1024 {
        return Err(error("Effect exceeds 32 MiB"));
    }
    let (version, refs) = binary::references(&bytes)?;
    let stage = tempfile::Builder::new()
        .prefix("scenemax-effect-")
        .tempdir()?;
    let runtime = stage.path().join(
        source
            .file_name()
            .ok_or_else(|| error("Effect has no file name"))?,
    );
    fs::write(&runtime, &bytes)?;
    let mut dependencies = Vec::new();
    let mut total = bytes.len() as u64;
    for (kind, path) in refs {
        let relative = Path::new(&path);
        if relative.is_absolute()
            || relative
                .components()
                .any(|c| !matches!(c, std::path::Component::Normal(_)))
        {
            return Err(error(format!(
                "Dependency '{path}' escapes the effect folder. Export a self-contained package first."
            )));
        }
        let found = root
            .join(relative)
            .canonicalize()
            .ok()
            .filter(|p| p.starts_with(root) && p.is_file());
        let size = if let Some(p) = found {
            let n = fs::metadata(&p)?.len();
            total += n;
            if total > 512 * 1024 * 1024 {
                return Err(error("Effect package exceeds 512 MiB"));
            }
            let dest = stage.path().join(relative);
            fs::create_dir_all(
                dest.parent()
                    .ok_or_else(|| error("Dependency has no parent"))?,
            )?;
            fs::copy(p, dest)?;
            Some(n)
        } else {
            None
        };
        dependencies.push(Dependency { kind, path, size });
    }
    Ok(Prepared {
        source: original,
        runtime,
        version,
        dependencies,
        bytes: total,
        _stage: stage,
    })
}
/// Atomically register the reviewed package and preview settings.
pub fn commit(root: &Path, p: &Prepared, draft: &Value) -> io::Result<Outcome> {
    schema::validate(draft).map_err(error)?;
    if !p.complete() {
        return Err(error("Resolve missing dependencies before importing"));
    }
    import(
        root,
        &Request {
            kind: Kind::Effect,
            source: p.runtime.clone(),
            name: draft["name"].as_str().unwrap_or_default().into(),
            rows: 1,
            cols: 1,
            frame_width: 1.,
            frame_height: 0.,
            clip: String::new(),
            scale: 1.,
            model: None,
            effect: Some(json!({"originalImportPath":p.source,"settings":draft})),
        },
    )
}
/// Open or create the persisted import document.
pub fn open_draft(project: &Path) -> io::Result<scenemax_ide_core::Document> {
    use std::io::Read;
    let root = project.canonicalize()?;
    let parent = root.join(".scenemax-studio");
    if parent.exists() && !parent.canonicalize()?.starts_with(&root) {
        return Err(error("Draft folder escapes project"));
    }
    let folder = parent.join("imports");
    fs::create_dir_all(&folder)?;
    let folder = folder.canonicalize()?;
    if !folder.starts_with(&root) {
        return Err(error("Draft folder escapes project"));
    }
    let path = folder.join("Import Effect.smeffectimport");
    match fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&path)
    {
        Ok(mut f) => f.write_all(&serde_json::to_vec_pretty(&schema::draft())?)?,
        Err(e) if e.kind() == io::ErrorKind::AlreadyExists => {}
        Err(e) => return Err(e),
    }
    let path = path.canonicalize()?;
    if !path.starts_with(&root) {
        return Err(error("Draft escapes project"));
    }
    let mut bytes = Vec::new();
    fs::File::open(&path)?
        .take(1024 * 1024 + 1)
        .read_to_end(&mut bytes)?;
    if bytes.len() > 1024 * 1024 {
        return Err(error("Draft is too large"));
    }
    let draft: Value = serde_json::from_slice(&bytes)?;
    schema::validate(&draft).map_err(error)?;
    scenemax_ide_core::Document::from_bytes(path, bytes).map_err(|e| error(e.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    #[ignore = "requires an installed official Effekseer tool and SCENEMAX_TEST_EFKPROJ"]
    fn legacy_project_converts_and_imports_without_touching_source() {
        let path = PathBuf::from(std::env::var_os("SCENEMAX_TEST_EFKPROJ").expect("test source"));
        let before = fs::read(&path).unwrap();
        let p = prepare(&path).unwrap();
        assert!(p.complete(), "missing dependencies: {:?}", p.dependencies);
        assert!(p.runtime.is_file());
        assert_ne!(p.runtime.parent(), path.parent());
        let project = tempfile::tempdir().unwrap();
        let out = commit(project.path(), &p, &schema::draft()).unwrap();
        assert!(out.asset.is_file());
        assert_eq!(fs::read(&path).unwrap(), before);
        eprintln!(
            "Exported version {}, {} dependencies, {} bytes",
            p.version,
            p.dependencies.len(),
            p.bytes
        );
    }
    fn binary(path: &str) -> Vec<u8> {
        let mut b = b"SKFE".to_vec();
        b.extend(0u32.to_le_bytes());
        b.extend(1u32.to_le_bytes());
        let u: Vec<_> = path.encode_utf16().chain(Some(0)).collect();
        b.extend((u.len() as u32).to_le_bytes());
        for c in u {
            b.extend(c.to_le_bytes());
        }
        b
    }
    #[test]
    fn missing_resource_is_reported_and_blocks_atomic_import() {
        let source = tempfile::tempdir().unwrap();
        let project = tempfile::tempdir().unwrap();
        let path = source.path().join("test.efk");
        fs::write(&path, binary("images/missing.png")).unwrap();
        let p = prepare(&path).unwrap();
        assert!(!p.complete());
        assert_eq!(p.dependencies[0].path, "images/missing.png");
        assert!(commit(project.path(), &p, &schema::draft()).is_err());
        assert!(!project.path().join("resources").exists());
    }
    #[test]
    fn stage_is_immutable_and_import_keeps_reviewed_settings() {
        let source = tempfile::tempdir().unwrap();
        let project = tempfile::tempdir().unwrap();
        fs::create_dir(source.path().join("custom")).unwrap();
        let resource = source.path().join("custom/particle.png");
        fs::write(&resource, b"original").unwrap();
        let path = source.path().join("effect.efk");
        fs::write(&path, binary("custom/particle.png")).unwrap();
        let p = prepare(&path).unwrap();
        fs::write(&resource, b"changed").unwrap();
        let mut v = schema::draft();
        v["scale"] = json!(2.5);
        v["input0"] = json!(3.);
        let out = commit(project.path(), &p, &v).unwrap();
        assert_eq!(
            fs::read(out.asset.parent().unwrap().join("custom/particle.png")).unwrap(),
            b"original"
        );
        let doc: Value = serde_json::from_slice(&fs::read(out.document).unwrap()).unwrap();
        assert_eq!(doc["preview"]["scale"], 2.5);
        assert_eq!(doc["preview"]["input0"], 3.);
        assert_eq!(
            doc["source"]["originalImportPath"],
            json!(path.canonicalize().unwrap())
        );
        assert!(commit(project.path(), &p, &v).is_err());
    }
    #[test]
    fn traversal_is_rejected_before_copying() {
        let source = tempfile::tempdir().unwrap();
        let path = source.path().join("effect.efk");
        fs::write(&path, binary("../outside.png")).unwrap();
        assert!(prepare(&path).is_err());
    }
}
