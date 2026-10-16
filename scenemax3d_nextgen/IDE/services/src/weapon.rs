//! Weapon creation and runtime publication on the existing filesystem worker.
use crate::{Filesystem, ServiceError, TreeOutcome, inventory, io_result};
use scenemax_ide_core::{Document, Project, weapon};
use std::{fs, io::Write, path::Path};

fn invalid(message: String) -> ServiceError {
    ServiceError::Io {
        operation: "Weapon",
        path: "resources/weapons".into(),
        source: std::io::Error::other(message),
    }
}
fn folder(root: &Path) -> Result<std::path::PathBuf, ServiceError> {
    let path = io_result(
        root,
        "Resolve weapon folder",
        inventory::confined(root, Path::new("resources/weapons")),
    )?;
    io_result(&path, "Create weapon folder", fs::create_dir_all(&path))?;
    Ok(path)
}
fn unique(root: &Path, id: &str, except: &Path) -> Result<(), ServiceError> {
    for directory in ["resources/weapons", "resources/Weapons"] {
        let dir = io_result(
            root,
            "Resolve weapon folder",
            inventory::confined(root, Path::new(directory)),
        )?;
        if !dir.exists() {
            continue;
        }
        for path in io_result(&dir, "Read weapon definitions", inventory::walk(&dir))? {
            if path == except
                || (except.exists() && path.canonicalize().ok() == except.canonicalize().ok())
                || !path
                    .extension()
                    .is_some_and(|e| e.eq_ignore_ascii_case("smweapon"))
            {
                continue;
            }
            let v = io_result(&path, "Read weapon definition", inventory::json(&path))?;
            if v["id"].as_str().is_some_and(|s| s.eq_ignore_ascii_case(id)) {
                return Err(invalid(format!(
                    "Weapon ID already exists: {id} ({})",
                    path.display()
                )));
            }
        }
    }
    Ok(())
}
/// Create a new asset without overwriting existing definitions.
pub(crate) fn create(root: &Path, name: &str) -> Result<TreeOutcome, ServiceError> {
    let value = weapon::template(name).map_err(invalid)?;
    let id = value["id"]
        .as_str()
        .ok_or(ServiceError::Limit("Missing weapon ID"))?;
    let path = folder(root)?.join(format!("{id}.smweapon"));
    unique(root, id, Path::new(""))?;
    let mut pending = io_result(
        &path,
        "Prepare weapon",
        tempfile::NamedTempFile::new_in(
            path.parent()
                .ok_or(ServiceError::Limit("No weapon folder"))?,
        ),
    )?;
    let text = serde_json::to_string_pretty(&value).map_err(|e| invalid(e.to_string()))?;
    io_result(&path, "Write weapon", pending.write_all(text.as_bytes()))?;
    io_result(&path, "Flush weapon", pending.as_file().sync_all())?;
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
    let metadata = io_result(doc.path(), "Read weapon metadata", fs::metadata(doc.path()))?;
    if metadata.len() > crate::MAX_DOCUMENT_BYTES {
        return Err(ServiceError::Limit("Weapon document exceeds 8 MiB"));
    }
    if io_result(doc.path(), "Check weapon source", fs::read(doc.path()))? != doc.saved_bytes() {
        return Err(ServiceError::ExternalChange(doc.path().into()));
    }
    let value = weapon::parse(doc.text()).map_err(invalid)?;
    weapon::validate(&value).map_err(invalid)?;
    let root = doc
        .path()
        .ancestors()
        .find(|p| p.join("scripts").is_dir() && p.join("resources").is_dir())
        .ok_or(ServiceError::Limit(
            "Weapon document must belong to a project with scripts and resources",
        ))?
        .to_owned();
    io_result(
        doc.path(),
        "Resolve weapon source",
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
        .ok_or(ServiceError::Limit("Missing weapon ID"))?;
    let direct = doc
        .path()
        .parent()
        .is_some_and(|p| p == dir || p == root.join("resources/Weapons"));
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
        dir.join(format!("{filename}.smweapon"))
    };
    unique(&root, id, &target)?;
    if direct {
        return Filesystem::save_plain(doc);
    }
    io_result(
        &root,
        "Resolve runtime weapon",
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
            "Read runtime weapon",
            fs::read(&target),
        )?)
    } else {
        None
    };
    if let Some(bytes) = &old {
        let baseline = weapon::parse(
            std::str::from_utf8(doc.saved_bytes()).map_err(|e| invalid(e.to_string()))?,
        )
        .map_err(invalid)?;
        let existing =
            weapon::parse(std::str::from_utf8(bytes).map_err(|e| invalid(e.to_string()))?)
                .map_err(invalid)?;
        if existing != baseline {
            return Err(ServiceError::ExternalChange(target));
        }
    }
    let mut pending = io_result(
        &target,
        "Prepare runtime weapon",
        tempfile::NamedTempFile::new_in(&dir),
    )?;
    io_result(
        &target,
        "Write runtime weapon",
        pending.write_all(doc.text().as_bytes()),
    )?;
    io_result(
        &target,
        "Flush runtime weapon",
        pending.as_file().sync_all(),
    )?;
    if let Some(bytes) = &old {
        if io_result(&target, "Recheck runtime weapon", fs::read(&target))? != *bytes {
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
            io_result(&target, "Restore runtime weapon", fs::write(&target, bytes))?;
        } else {
            io_result(
                &target,
                "Roll back runtime weapon",
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
    #[test]
    fn create_edit_publish_without_overwriting_or_losing_unknown_data() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        fs::create_dir(root.join("scripts")).unwrap();
        let mut doc = create(&root, "Test blade").unwrap().document.unwrap();
        assert!(
            doc.path()
                .ends_with("resources/weapons/weapon_test_blade.smweapon")
        );
        assert!(create(&root, "Test blade").is_err());
        let source =
            weapon::edit(doc.text(), "collider.sphere", serde_json::Value::Null, 0).unwrap();
        doc.replace_text(source);
        save(&mut doc).unwrap();
        assert!(!doc.is_dirty());
        let v: serde_json::Value = serde_json::from_slice(&fs::read(doc.path()).unwrap()).unwrap();
        assert_eq!(v["colliders"][0]["shape"], "sphere");
        doc.replace_text(weapon::edit(doc.text(), "/id", serde_json::json!("renamed"), 0).unwrap());
        save(&mut doc).unwrap();
        assert_eq!(
            fs::read_dir(root.join("resources/weapons"))
                .unwrap()
                .count(),
            1
        );
    }
    #[test]
    fn legacy_export_conflicts_and_source_failure_roll_back() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        fs::create_dir(root.join("scripts")).unwrap();
        fs::create_dir(root.join("resources")).unwrap();
        let path = root.join("scripts/legacy.smweapon");
        let original = weapon::template("legacy").unwrap().to_string();
        fs::write(&path, &original).unwrap();
        let mut doc = Document::from_bytes(path.clone(), original.into_bytes()).unwrap();
        doc.replace_text(
            weapon::edit(doc.text(), "collider.box", serde_json::Value::Null, 0).unwrap(),
        );
        fs::write(&path, "external edit").unwrap();
        assert!(save(&mut doc).is_err());
        assert!(
            !root
                .join("resources/weapons/weapon_legacy.smweapon")
                .exists()
        );
        fs::write(&path, doc.saved_bytes()).unwrap();
        save(&mut doc).unwrap();
        assert!(
            root.join("resources/weapons/weapon_legacy.smweapon")
                .exists()
        );
    }
}
