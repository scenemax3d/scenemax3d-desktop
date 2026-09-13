//! Read-only compatibility adapter for the existing project catalog.
use crate::{ServiceError, io_result};
use scenemax_ide_core::{ProjectCatalog, ProjectSummary};
use serde::Deserialize;
use std::{
    fs::File,
    io::Read,
    path::{Path, PathBuf},
};
#[derive(Deserialize)]
struct Catalog {
    projects: Vec<Row>,
    #[serde(default, rename = "selectedProject")]
    selected: String,
}
#[derive(Deserialize)]
struct Row {
    name: String,
    path: PathBuf,
    #[serde(default, rename = "lastActiveAt")]
    last_active: u64,
}
/// Discover or read a catalog without rewriting Java settings or project metadata.
pub fn load_catalog(
    search_root: &Path,
    explicit: Option<&Path>,
) -> Result<ProjectCatalog, ServiceError> {
    let search_root = io_result(
        search_root,
        "Locate project catalog",
        search_root.canonicalize(),
    )?;
    let path = match explicit {
        Some(path) => path.to_owned(),
        None => {
            let Some(path) = search_root
                .ancestors()
                .map(|p| p.join("projects/projects.json"))
                .find(|p| p.is_file())
            else {
                return Ok(ProjectCatalog::default());
            };
            path
        }
    };
    let path = io_result(&path, "Resolve project catalog", path.canonicalize())?;
    let mut bytes = Vec::new();
    io_result(&path, "Read project catalog", File::open(&path))?
        .take(1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .map_err(|source| ServiceError::Io {
            operation: "Read project catalog",
            path: path.clone(),
            source,
        })?;
    if bytes.len() > 1024 * 1024 {
        return Err(ServiceError::Limit("Project catalog exceeds 1 MiB"));
    }
    let catalog: Catalog =
        serde_json::from_slice(bytes.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&bytes))
            .map_err(|e| ServiceError::Catalog(e.to_string()))?;
    if catalog.projects.len() > 2048 {
        return Err(ServiceError::Limit("Project catalog exceeds 2048 projects"));
    }
    let base = path
        .parent()
        .and_then(Path::parent)
        .ok_or(ServiceError::Limit(
            "Catalog must be inside a projects directory",
        ))?;
    let mut result = ProjectCatalog::default();
    for row in catalog.projects {
        if row.name.trim().is_empty() || row.path.as_os_str().is_empty() {
            return Err(ServiceError::Catalog(
                "Project name and path must not be empty".into(),
            ));
        }
        let root = if row.path.is_absolute() {
            row.path
        } else {
            base.join(row.path)
        };
        let root = root.canonicalize().unwrap_or(root);
        if row.name == catalog.selected {
            result.selected = Some(root.clone());
        }
        result.projects.push(ProjectSummary {
            name: row.name,
            root,
            last_active: row.last_active,
        });
    }
    result.projects.sort_by(|a, b| {
        b.last_active
            .cmp(&a.last_active)
            .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
    });
    Ok(result)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn imports_catalog_relative_to_installation_without_modifying_it() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("projects")).unwrap();
        let path = dir.path().join("projects/projects.json");
        let bytes = br#"{"selectedProject":"Second","projects":[{"name":"First","path":"projects/one","lastActiveAt":1},{"name":"Second","path":"projects/two","lastActiveAt":2,"unknownSetting":true}]}"#;
        std::fs::write(&path, bytes).unwrap();
        let catalog = load_catalog(dir.path(), None).unwrap();
        assert_eq!(catalog.projects[0].name, "Second");
        assert!(catalog.selected.unwrap().ends_with("projects/two"));
        assert!(catalog.projects[0].root.is_absolute());
        assert_eq!(std::fs::read(path).unwrap(), bytes);
    }
    #[test]
    fn missing_catalog_is_empty_but_malformed_catalog_is_an_error() {
        let dir = tempfile::tempdir().unwrap();
        assert!(load_catalog(dir.path(), None).unwrap().projects.is_empty());
        let file = dir.path().join("broken.json");
        std::fs::write(&file, b"not json").unwrap();
        assert!(load_catalog(dir.path(), Some(&file)).is_err());
    }
}
