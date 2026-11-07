use super::*;
use std::{fs, io::Write};

/// Recover uncatalogued project folders and optionally register a successfully
/// opened project. Unknown settings and existing display names are preserved.
pub fn refresh_catalog(
    root: &Path,
    explicit: Option<&Path>,
    opened: Option<&Path>,
) -> Result<ProjectCatalog, ServiceError> {
    let installation = crate::workspace_state::installation_root()
        .or_else(|| crate::workspace_state::is_installation_root(root).then(|| root.to_owned()));
    let path = explicit
        .map(Path::to_owned)
        .or_else(|| installation.map(|p| p.join("projects/projects.json")))
        .or_else(|| {
            root.ancestors()
                .map(|p| p.join("projects/projects.json"))
                .find(|p| p.is_file())
        });
    let Some(path) = path else {
        return load_catalog(root, explicit);
    };
    let directory = path
        .parent()
        .ok_or(ServiceError::Limit("Catalog has no directory"))?;
    io_result(
        directory,
        "Create catalog directory",
        fs::create_dir_all(directory),
    )?;
    let lock_path = directory.join(".projects.lock");
    let lock = io_result(
        &lock_path,
        "Open catalog lock",
        fs::OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .open(&lock_path),
    )?;
    io_result(&lock_path, "Lock project catalog", lock.lock())?;
    // Validate before making any change; malformed metadata must never be replaced.
    if path.exists() {
        load_catalog(root, Some(&path))?;
    }
    let mut value: serde_json::Value = if path.exists() {
        let bytes = io_result(&path, "Read project catalog", fs::read(&path))?;
        serde_json::from_slice(bytes.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&bytes))
            .map_err(|e| ServiceError::Catalog(e.to_string()))?
    } else {
        serde_json::json!({"projects":[]})
    };
    let previous = value.clone();
    let base = directory
        .parent()
        .ok_or(ServiceError::Limit("Catalog has no base directory"))?;
    let base = io_result(base, "Resolve catalog base", base.canonicalize())?;
    let previous_selection = value["selectedProject"].clone();
    let rows = value["projects"]
        .as_array_mut()
        .ok_or_else(|| ServiceError::Catalog("Missing projects list".into()))?;
    let mut candidates = Vec::new();
    for entry in io_result(directory, "Find projects", fs::read_dir(directory))? {
        let entry = io_result(directory, "Read project directory", entry)?;
        if !entry.file_name().to_string_lossy().starts_with('.')
            && entry.path().join("scripts").is_dir()
        {
            candidates.push(entry.path());
        }
    }
    candidates.sort();
    if let Some(opened) = opened {
        candidates.push(opened.to_owned());
    }
    let opened = opened.and_then(|p| p.canonicalize().ok());
    let mut selected = None;
    for candidate in candidates {
        let project = io_result(
            &candidate,
            "Resolve catalog project",
            candidate.canonicalize(),
        )?;
        if !project.is_dir() || crate::workspace_state::is_installation_root(&project) {
            continue;
        }
        let index = rows.iter().position(|row| {
            row["path"].as_str().is_some_and(|p| {
                let p = base.join(p);
                p.canonicalize().unwrap_or(p) == project
            })
        });
        let index = match index {
            Some(index) => index,
            None => {
                if rows.len() >= 2048 {
                    return Err(ServiceError::Limit("Project catalog exceeds 2048 projects"));
                }
                let stem = project
                    .file_name()
                    .unwrap_or_default()
                    .to_string_lossy()
                    .into_owned();
                let mut name = stem.clone();
                let mut suffix = 2;
                while rows.iter().any(|r| r["name"].as_str() == Some(&name)) {
                    name = format!("{stem} ({suffix})");
                    suffix += 1;
                }
                let stored = project
                    .strip_prefix(&base)
                    .map(|p| p.to_string_lossy().replace('\\', "/"))
                    .unwrap_or_else(|_| project.to_string_lossy().into_owned());
                rows.push(serde_json::json!({"name":name,"path":stored}));
                rows.len() - 1
            }
        };
        if opened.as_ref() == Some(&project) {
            rows[index]["lastActiveAt"] = serde_json::json!(
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as u64
            );
            selected = Some(rows[index]["name"].clone());
        }
    }
    if selected.is_none()
        && !rows.iter().any(|row| {
            row["name"] == previous_selection
                && row["path"].as_str().is_some_and(|p| base.join(p).is_dir())
        })
    {
        selected = rows
            .iter()
            .find(|row| row["path"].as_str().is_some_and(|p| base.join(p).is_dir()))
            .map(|row| row["name"].clone());
    }
    if let Some(selected) = selected {
        value["selectedProject"] = selected;
    }
    if value != previous {
        let bytes =
            serde_json::to_vec_pretty(&value).map_err(|e| ServiceError::Catalog(e.to_string()))?;
        if bytes.len() > 1024 * 1024 {
            return Err(ServiceError::Limit("Project catalog exceeds 1 MiB"));
        }
        let mut pending = io_result(
            directory,
            "Prepare project catalog",
            tempfile::NamedTempFile::new_in(directory),
        )?;
        io_result(&path, "Write project catalog", pending.write_all(&bytes))?;
        io_result(&path, "Sync project catalog", pending.as_file().sync_all())?;
        io_result(
            &path,
            "Publish project catalog",
            pending.persist(&path).map_err(|e| e.error),
        )?;
    }
    if path.is_file() {
        load_catalog(root, Some(&path))
    } else {
        Ok(ProjectCatalog::default())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn explicit_startup_recovers_and_selects_existing_project() {
        let dir = tempfile::tempdir().unwrap();
        let project = dir.path().join("projects/existing");
        fs::create_dir_all(project.join("scripts")).unwrap();
        let path = dir.path().join("projects/projects.json");
        let catalog = load_startup_catalog(dir.path(), Some(&path)).unwrap();
        assert_eq!(catalog.projects.len(), 1);
        assert_eq!(catalog.selected, Some(project.canonicalize().unwrap()));
        let bytes = fs::read(&path).unwrap();
        load_startup_catalog(dir.path(), Some(&path)).unwrap();
        assert_eq!(fs::read(path).unwrap(), bytes);
    }
    #[test]
    fn recovers_missing_folders_and_registers_external_projects_without_losing_metadata() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        fs::create_dir_all(root.join("projects/one/scripts")).unwrap();
        fs::create_dir_all(root.join("projects/two/scripts")).unwrap();
        fs::create_dir_all(root.join("projects/.partial/scripts")).unwrap();
        let path = root.join("projects/projects.json");
        fs::write(&path, r#"{"extra":7,"selectedProject":"Custom","projects":[{"name":"Custom","path":"projects/one","settings":{"keep":true}}]}"#).unwrap();
        let catalog = refresh_catalog(root, Some(&path), None).unwrap();
        assert_eq!(catalog.projects.len(), 2);
        assert!(catalog.selected.unwrap().ends_with("one"));
        let external = tempfile::tempdir().unwrap();
        refresh_catalog(root, Some(&path), Some(external.path())).unwrap();
        let catalog = refresh_catalog(root, Some(&path), Some(external.path())).unwrap();
        assert_eq!(catalog.projects.len(), 3);
        assert_eq!(
            catalog.selected,
            Some(external.path().canonicalize().unwrap())
        );
        let value: serde_json::Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
        assert_eq!(value["extra"], 7);
        assert_eq!(value["projects"][0]["settings"]["keep"], true);
        fs::write(&path, "broken").unwrap();
        assert!(refresh_catalog(root, Some(&path), None).is_err());
        assert_eq!(fs::read_to_string(path).unwrap(), "broken");
    }
}
