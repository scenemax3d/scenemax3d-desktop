//! Background catalog discovery and animation timing validation.
use scenemax_assets::animation_ranges::{self, Timeline};
use serde_json::Value;
use std::path::{Path, PathBuf};
/// Locate a writable project model catalog, never a shared installation catalog.
pub fn catalog(project: &Path) -> Result<PathBuf, String> {
    for directory in ["Models", "models"] {
        let path = project
            .join("resources")
            .join(directory)
            .join("models-ext.json");
        if path.is_file() {
            return path.canonicalize().map_err(|e| e.to_string());
        }
    }
    let folder = project.join("resources/Models");
    std::fs::create_dir_all(&folder).map_err(|e| e.to_string())?;
    let folder = folder.canonicalize().map_err(|e| e.to_string())?;
    if !folder.starts_with(project.canonicalize().map_err(|e| e.to_string())?) {
        return Err("Model catalog directory escapes the project".into());
    }
    let path = folder.join("models-ext.json");
    match std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&path)
    {
        Ok(mut file) => {
            use std::io::Write;
            file.write_all(b"{\"models\": []}\n")
                .map_err(|e| e.to_string())?;
        }
        Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => {}
        Err(e) => return Err(e.to_string()),
    }
    Ok(path)
}
/// One inventory model with its original catalog, needed for paths and aliases.
#[derive(Clone, Debug)]
pub struct InventoryModel {
    /// Owning catalog.
    pub catalog: PathBuf,
    /// Full catalog for sourceModel alias resolution.
    pub data: Value,
    /// Model row in that catalog.
    pub index: usize,
}
/// Find the installed inventory independently of the project's own catalog.
pub fn inventory(project: &Path) -> Result<Vec<InventoryModel>, String> {
    let mut roots = Vec::new();
    if let Some(root) = std::env::var_os("SCENEMAX_BUILTIN_RESOURCES") {
        roots.push(PathBuf::from(root));
    }
    if let Some(root) = crate::workspace_state::installation_root() {
        roots.push(root.join("resources"));
    }
    roots.extend(project.ancestors().skip(1).map(|p| p.join("resources")));
    roots.push(crate::deployment::workspace().join("../resources"));
    for root in roots {
        if root == project.join("resources") {
            continue;
        }
        if root.join("Models/models.json").is_file() || root.join("models/models.json").is_file() {
            return inventory_at(&root);
        }
    }
    Ok(Vec::new())
}
/// Read a shared inventory with the same extension-first precedence as the runtime.
pub fn inventory_at(root: &Path) -> Result<Vec<InventoryModel>, String> {
    let mut result = Vec::new();
    let mut names = std::collections::HashSet::new();
    for file in [
        "Models/models-ext.json",
        "Models/models.json",
        "models/models-ext.json",
        "models/models.json",
    ] {
        let catalog = root.join(file);
        if !catalog.is_file() {
            continue;
        }
        let data =
            crate::inventory::json(&catalog).map_err(|e| format!("{}: {e}", catalog.display()))?;
        for (index, model) in data["models"]
            .as_array()
            .ok_or("Missing inventory models array")?
            .iter()
            .enumerate()
        {
            if let Some(name) = model["name"].as_str() {
                if names.insert(name.to_lowercase()) {
                    result.push(InventoryModel {
                        catalog: catalog.clone(),
                        data: data.clone(),
                        index,
                    });
                }
            }
        }
    }
    Ok(result)
}
/// Add inventory choices without modifying the project catalog. Project names win.
pub fn with_inventory(
    project: &Value,
    inventory: &[InventoryModel],
) -> (Value, Vec<InventoryModel>) {
    let mut data = project.clone();
    let Some(models) = data["models"].as_array_mut() else {
        return (data, Vec::new());
    };
    let mut names: std::collections::HashSet<String> = models
        .iter()
        .filter_map(|m| m["name"].as_str().map(str::to_lowercase))
        .collect();
    let mut visible = Vec::new();
    for entry in inventory {
        let model = &entry.data["models"][entry.index];
        if model["name"]
            .as_str()
            .is_some_and(|n| names.insert(n.to_lowercase()))
        {
            models.push(model.clone());
            visible.push(entry.clone());
        }
    }
    (data, visible)
}
/// Prepared immutable preview source.
#[derive(Clone, Debug)]
pub struct Source {
    /// Canonical resource root.
    pub root: PathBuf,
    /// Canonical glTF/GLB file.
    pub file: PathBuf,
    /// Source animation keyframe timing.
    pub timelines: Vec<Timeline>,
}
/// Resolve from the current unsaved catalog, retaining alias-model behavior.
pub fn source(catalog: &Path, data: &Value, model: usize) -> Result<Source, String> {
    let root = catalog
        .parent()
        .and_then(Path::parent)
        .ok_or("Invalid model catalog path")?
        .canonicalize()
        .map_err(|e| e.to_string())?;
    let mut value = &data["models"][model];
    let supported = |v: &Value| {
        v["path"].as_str().is_some_and(|p| {
            p.to_lowercase().ends_with(".gltf") || p.to_lowercase().ends_with(".glb")
        })
    };
    if !supported(value) {
        let alias = value["sourceModel"]
            .as_str()
            .ok_or("This model needs a glTF/GLB source; J3O is unsupported")?;
        value = data["models"]
            .as_array()
            .and_then(|models| {
                models.iter().find(|m| {
                    m["name"]
                        .as_str()
                        .is_some_and(|n| n.eq_ignore_ascii_case(alias))
                        && supported(m)
                })
            })
            .ok_or("Source model is unavailable")?;
    }
    let file = root
        .join(
            value["path"]
                .as_str()
                .ok_or("Missing model path")?
                .replace('\\', "/"),
        )
        .canonicalize()
        .map_err(|e| e.to_string())?;
    let timelines = animation_ranges::timelines(&root, &file)?;
    Ok(Source {
        root,
        file,
        timelines,
    })
}
/// Validate records on the storage worker before any catalog is replaced.
pub fn validate(catalog: &Path, text: &str) -> Result<(), String> {
    validate_changed(catalog, "", text)
}
/// Validate modified record sets; unrelated legacy entries are left untouched.
pub fn validate_changed(catalog: &Path, previous: &str, text: &str) -> Result<(), String> {
    let data: Value = serde_json::from_str(text).map_err(|e| e.to_string())?;
    let previous: Value =
        serde_json::from_str(previous.trim_start_matches('\u{feff}')).unwrap_or(Value::Null);
    for (index, model) in data["models"]
        .as_array()
        .ok_or("Missing models array")?
        .iter()
        .enumerate()
    {
        if previous["models"][index] == *model {
            continue;
        }
        let records = animation_ranges::records(model)?;
        if records.is_empty() {
            continue;
        }
        let source = source(catalog, &data, index)?;
        for record in records {
            let timeline = source
                .timelines
                .iter()
                .find(|t| t.name == record.source)
                .or_else(|| {
                    (record.source.is_empty() && source.timelines.len() == 1)
                        .then(|| &source.timelines[0])
                })
                .ok_or_else(|| format!("{}: source animation was not found", record.name))?;
            timeline.interval(&record)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn inventory_models_keep_their_own_roots_and_project_precedence() {
        let dir = tempfile::tempdir().unwrap();
        let shared = dir.path().join("shared");
        std::fs::create_dir_all(shared.join("Models")).unwrap();
        std::fs::write(
            shared.join("Models/fixture.gltf"),
            include_bytes!(
                "../../../Tests/fixtures/animation_analyzer/project/resources/Models/fixture.gltf"
            ),
        )
        .unwrap();
        let data = json!({"models":[{"name":"shared","path":"Models/fixture.gltf"},{"name":"duplicate","path":"Models/fixture.gltf"}]});
        let catalog = shared.join("Models/models.json");
        std::fs::write(&catalog, data.to_string()).unwrap();
        let before = std::fs::read(&catalog).unwrap();
        let entries = inventory_at(&shared).unwrap();
        let project = json!({"models":[{"name":"DUPLICATE","path":"Models/local.glb"}]});
        let (merged, visible) = with_inventory(&project, &entries);
        assert_eq!(merged["models"].as_array().unwrap().len(), 2);
        assert_eq!(merged["models"][0]["path"], "Models/local.glb");
        assert_eq!(visible.len(), 1);
        let loaded = source(&visible[0].catalog, &visible[0].data, visible[0].index).unwrap();
        assert_eq!(loaded.root, shared.canonicalize().unwrap());
        assert!(!loaded.timelines.is_empty());
        assert_eq!(std::fs::read(catalog).unwrap(), before);
        assert_eq!(project["models"].as_array().unwrap().len(), 1);
    }
    #[test]
    fn analyzer_can_open_without_an_imported_model() {
        let dir = tempfile::tempdir().unwrap();
        let path = catalog(dir.path()).unwrap();
        assert_eq!(
            std::fs::read_to_string(&path).unwrap(),
            "{\"models\": []}\n"
        );
        std::fs::write(&path, "preserve existing catalog").unwrap();
        assert_eq!(catalog(dir.path()).unwrap(), path);
        assert_eq!(
            std::fs::read_to_string(path).unwrap(),
            "preserve existing catalog"
        );
    }
    #[test]
    fn nonuniform_frames_aliases_validation_conflicts_and_preservation() {
        let dir = tempfile::tempdir().unwrap();
        let models = dir.path().join("resources/Models");
        std::fs::create_dir_all(&models).unwrap();
        let gltf = include_bytes!(
            "../../../Tests/fixtures/animation_analyzer/project/resources/Models/fixture.gltf"
        );
        std::fs::write(models.join("fixture.gltf"), gltf).unwrap();
        let path = models.join("models-ext.json");
        let mut data: Value = serde_json::from_str(include_str!(
            "../../../Tests/fixtures/animation_analyzer/project/resources/Models/models-ext.json"
        ))
        .unwrap();
        std::fs::write(&path, data.to_string()).unwrap();
        let loaded = source(&path, &data, 0).unwrap();
        assert_eq!(
            loaded.timelines[0].times,
            vec![0., 0.1, 0.35, 0.8, 1.2, 1.6, 2.]
        );
        let rows =
            scenemax_assets::model_animation_records(models.parent().unwrap(), None, "fixture")
                .unwrap();
        assert_eq!(rows[0].1.interval(&rows[0].0).unwrap(), (0., 0.8));
        data["models"].as_array_mut().unwrap().push(json!({"name":"alias","path":"legacy.j3o","sourceModel":"fixture","animationFrameRangesSourceAnimation":"Complete take","animationFrameRanges":[{"name":"middle","start":1,"end":4}]}));
        std::fs::write(&path, data.to_string()).unwrap();
        let alias =
            scenemax_assets::model_animation_records(models.parent().unwrap(), None, "alias")
                .unwrap();
        assert_eq!(alias[0].1.interval(&alias[0].0).unwrap(), (0.1, 1.2));
        let project = crate::Filesystem::open_project(dir.path()).unwrap();
        let mut doc = crate::Filesystem::open_document(&project, &path).unwrap();
        data["models"][0]["animationFrameRanges"][0]["end"] = json!(99);
        doc.replace_text(data.to_string());
        assert!(crate::Filesystem::save_document(&mut doc).is_err());
        assert!(doc.is_dirty());
        data["models"][0]["animationFrameRanges"][0]["end"] = json!(4);
        doc.replace_text(data.to_string());
        std::fs::write(&path, "external edit").unwrap();
        assert!(crate::Filesystem::save_document(&mut doc).is_err());
        assert_eq!(std::fs::read(&path).unwrap(), b"external edit");
        assert_eq!(std::fs::read(models.join("fixture.gltf")).unwrap(), gltf);
        data["models"][0]["animationFrameRanges"][1]["name"] = json!("OPENING");
        assert!(validate(&path, &data.to_string()).is_err());
    }
}
