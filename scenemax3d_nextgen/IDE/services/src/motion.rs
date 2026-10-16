//! Throw motion creation and runtime publication on the existing filesystem worker.
use crate::{Filesystem, ServiceError, TreeOutcome, inventory, io_result};
use scenemax_ide_core::{Document, Project, motion};
use std::{fs, io::Write, path::Path};

fn invalid(message: String) -> ServiceError {
    ServiceError::Io {
        operation: "Throw motion",
        path: "resources/throw_motions".into(),
        source: std::io::Error::other(message),
    }
}
fn folder(root: &Path) -> Result<std::path::PathBuf, ServiceError> {
    let path = io_result(
        root,
        "Resolve motion folder",
        inventory::confined(root, Path::new("resources/throw_motions")),
    )?;
    io_result(&path, "Create motion folder", fs::create_dir_all(&path))?;
    Ok(path)
}
fn unique(root: &Path, id: &str, except: &Path) -> Result<(), ServiceError> {
    for directory in [
        "resources/throw_motions",
        "resources/Throw_Motions",
        "resources/motions",
        "resources/Motions",
        "resources/ThrowMotions",
    ] {
        let dir = io_result(
            root,
            "Resolve motion folder",
            inventory::confined(root, Path::new(directory)),
        )?;
        if !dir.exists() {
            continue;
        }
        for path in io_result(&dir, "Read motion definitions", inventory::walk(&dir))? {
            if path == except
                || (except.exists() && path.canonicalize().ok() == except.canonicalize().ok())
                || !path
                    .extension()
                    .is_some_and(|e| e.eq_ignore_ascii_case("smmotion"))
            {
                continue;
            }
            let v = io_result(&path, "Read motion definition", inventory::json(&path))?;
            if v["id"].as_str().is_some_and(|s| s.eq_ignore_ascii_case(id)) {
                return Err(invalid(format!(
                    "Throw motion ID already exists: {id} ({})",
                    path.display()
                )));
            }
        }
    }
    Ok(())
}
/// Create a new asset without overwriting existing definitions.
pub(crate) fn create(root: &Path, name: &str, kind: &str) -> Result<TreeOutcome, ServiceError> {
    let value = motion::template(name, kind).map_err(invalid)?;
    let id = value["id"]
        .as_str()
        .ok_or(ServiceError::Limit("Missing motion ID"))?;
    let path = folder(root)?.join(format!("{id}.smmotion"));
    unique(root, id, Path::new(""))?;
    let mut pending = io_result(
        &path,
        "Prepare motion",
        tempfile::NamedTempFile::new_in(
            path.parent()
                .ok_or(ServiceError::Limit("No motion folder"))?,
        ),
    )?;
    let text = serde_json::to_string_pretty(&value).map_err(|e| invalid(e.to_string()))?;
    io_result(&path, "Write motion", pending.write_all(text.as_bytes()))?;
    io_result(&path, "Flush motion", pending.as_file().sync_all())?;
    pending
        .persist_noclobber(&path)
        .map_err(|e| invalid(e.to_string()))?;
    Ok(TreeOutcome {
        paths: vec![],
        document: Some(Filesystem::open_document(
            &Project::new(root.into(), vec![]),
            &path,
        )?),
    })
}
/// Validate and publish source. Legacy script documents also get a runtime copy.
pub(crate) fn save(doc: &mut Document) -> Result<(), ServiceError> {
    let metadata = io_result(doc.path(), "Read motion metadata", fs::metadata(doc.path()))?;
    if metadata.len() > crate::MAX_DOCUMENT_BYTES {
        return Err(ServiceError::Limit("Throw motion document exceeds 8 MiB"));
    }
    if io_result(doc.path(), "Check motion source", fs::read(doc.path()))? != doc.saved_bytes() {
        return Err(ServiceError::ExternalChange(doc.path().into()));
    }
    let value = motion::parse(doc.text()).map_err(invalid)?;
    motion::validate(&value).map_err(invalid)?;
    let root = doc
        .path()
        .ancestors()
        .find(|p| p.join("scripts").is_dir() && p.join("resources").is_dir())
        .ok_or(ServiceError::Limit(
            "Throw motion document must belong to a project with scripts and resources",
        ))?
        .to_owned();
    io_result(
        doc.path(),
        "Resolve motion source",
        inventory::confined(
            &root,
            doc.path()
                .strip_prefix(&root)
                .map_err(|_| ServiceError::OutsideProject(doc.path().into()))?,
        ),
    )?;
    let dir = folder(&root)?;
    let id = value["id"]
        .as_str()
        .ok_or(ServiceError::Limit("Missing motion ID"))?;
    let direct = doc.path().parent().is_some_and(|p| {
        [
            "resources/throw_motions",
            "resources/Throw_Motions",
            "resources/motions",
            "resources/Motions",
        ]
        .iter()
        .any(|d| p == root.join(d))
    });
    let filename: String = id
        .chars()
        .map(|c| {
            if c.is_whitespace() || "<>:\"/\\|?*".contains(c) {
                '_'
            } else {
                c.to_ascii_lowercase()
            }
        })
        .collect();
    let target = if direct {
        doc.path().to_owned()
    } else {
        dir.join(format!("{filename}.smmotion"))
    };
    unique(&root, id, &target)?;
    if direct {
        return Filesystem::save_plain(doc);
    }
    io_result(
        &root,
        "Resolve runtime motion",
        inventory::confined(
            &root,
            target
                .strip_prefix(&root)
                .map_err(|_| ServiceError::OutsideProject(target.clone()))?,
        ),
    )?;
    let old = if target.exists() {
        Some(io_result(
            &target,
            "Read runtime motion",
            fs::read(&target),
        )?)
    } else {
        None
    };
    if let Some(bytes) = &old {
        let baseline = motion::parse(
            std::str::from_utf8(doc.saved_bytes()).map_err(|e| invalid(e.to_string()))?,
        )
        .map_err(invalid)?;
        let existing =
            motion::parse(std::str::from_utf8(bytes).map_err(|e| invalid(e.to_string()))?)
                .map_err(invalid)?;
        if existing != baseline {
            return Err(ServiceError::ExternalChange(target));
        }
    }
    let mut pending = io_result(
        &target,
        "Prepare runtime motion",
        tempfile::NamedTempFile::new_in(&dir),
    )?;
    io_result(
        &target,
        "Write runtime motion",
        pending.write_all(doc.text().as_bytes()),
    )?;
    io_result(
        &target,
        "Flush runtime motion",
        pending.as_file().sync_all(),
    )?;
    if let Some(bytes) = &old {
        if io_result(&target, "Recheck runtime motion", fs::read(&target))? != *bytes {
            return Err(ServiceError::ExternalChange(target));
        }
        if fs::metadata(&target).is_ok_and(|m| m.permissions().readonly()) {
            return Err(ServiceError::ReadOnly(target));
        }
        pending
            .persist(&target)
            .map_err(|e| invalid(e.to_string()))?;
    } else {
        pending
            .persist_noclobber(&target)
            .map_err(|e| invalid(e.to_string()))?;
    }
    if let Err(error) = Filesystem::save_plain(doc) {
        if let Some(bytes) = old {
            io_result(&target, "Restore runtime motion", fs::write(&target, bytes))?;
        } else {
            io_result(
                &target,
                "Roll back runtime motion",
                fs::remove_file(&target),
            )?;
        }
        return Err(error);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn create_save_legacy_export_and_collision_protection() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        fs::create_dir(root.join("scripts")).unwrap();
        let mut doc = create(&root, "Test Throw", "homing")
            .unwrap()
            .document
            .unwrap();
        assert!(
            doc.path()
                .ends_with("resources/throw_motions/motion_test_throw.smmotion")
        );
        assert_eq!(motion::parse(doc.text()).unwrap()["motionType"], "homing");
        assert!(create(&root, "Test Throw", "ballistic").is_err());
        doc.replace_text(motion::edit(doc.text(), "/parameters/turnRate", json!(270.)).unwrap());
        save(&mut doc).unwrap();
        assert!(!doc.is_dirty());
        let source = root.join("scripts/source.smmotion");
        let value = motion::template("Legacy", "returning").unwrap();
        fs::write(&source, value.to_string()).unwrap();
        let mut legacy =
            Document::from_bytes(source.clone(), value.to_string().into_bytes()).unwrap();
        legacy.replace_text(
            motion::edit(legacy.text(), "/parameters/returnSpeed", json!(30.)).unwrap(),
        );
        save(&mut legacy).unwrap();
        let export = root.join("resources/throw_motions/motion_legacy.smmotion");
        assert_eq!(fs::read(&export).unwrap(), fs::read(&source).unwrap());
        fs::write(&export, "external edit").unwrap();
        let before = fs::read(&source).unwrap();
        assert!(save(&mut legacy).is_err());
        assert_eq!(before, fs::read(source).unwrap());
    }
    #[test]
    fn invalid_motion_and_changed_source_do_not_publish() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        fs::create_dir(root.join("scripts")).unwrap();
        let mut doc = create(&root, "Fixture", "straight")
            .unwrap()
            .document
            .unwrap();
        let original = fs::read(doc.path()).unwrap();
        doc.replace_text(motion::edit(doc.text(), "/parameters/speed", json!(0.)).unwrap());
        assert!(save(&mut doc).is_err());
        assert_eq!(fs::read(doc.path()).unwrap(), original);
        fs::write(doc.path(), "external").unwrap();
        assert!(matches!(
            save(&mut doc),
            Err(ServiceError::ExternalChange(_))
        ));
    }
}
