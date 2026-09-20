//! Project-local choices loaded on the existing scene storage worker.
use serde_json::Value;
use std::{collections::BTreeMap, path::Path};

pub(super) fn load(
    root: &Path,
    materials: &BTreeMap<String, Value>,
) -> BTreeMap<String, Vec<String>> {
    let mut catalog = BTreeMap::new();
    for (key, folder, list) in [
        ("resourcePath", "Models", "models"),
        ("shader", "shaders", "shaders"),
        ("material", "material", "materials"),
    ] {
        let mut names = vec![String::new()];
        for suffix in [".json", "-ext.json"] {
            let path = root.join(folder).join(format!("{list}{suffix}"));
            if let Ok(source) = std::fs::read_to_string(path)
                && source.len() < 8 * 1024 * 1024
                && let Ok(value) = serde_json::from_str::<Value>(&source)
                && let Some(items) = value[list].as_array()
            {
                names.extend(
                    items
                        .iter()
                        .filter_map(|v| v["name"].as_str().map(str::to_owned)),
                );
            }
        }
        names.sort();
        names.dedup();
        catalog.insert(key.into(), names);
    }
    let legacy = catalog.remove("material").unwrap_or_default();
    let mut names = vec![String::new()];
    names.extend(materials.keys().cloned());
    names.extend(
        legacy
            .into_iter()
            .filter(|name| !name.is_empty() && !materials.contains_key(name)),
    );
    catalog.insert("material".into(), names);
    let mut ik = vec![String::new()];
    if let Ok(files) = std::fs::read_dir(root.join("ik")) {
        for file in files.flatten() {
            if let Some(name) = file.file_name().to_str()
                && let Some(name) = name
                    .strip_suffix(".smik")
                    .or_else(|| name.strip_suffix(".ik.json"))
            {
                ik.push(name.into());
            }
        }
    }
    ik.sort();
    ik.dedup();
    catalog.insert("ikAsset".into(), ik);
    catalog
}

#[cfg(test)]
mod material_tests {
    #[test]
    fn nested_material_is_first_and_uses_filename_not_display_name() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir_all(dir.path().join("scripts/Scene assets/materials")).unwrap();
        std::fs::create_dir_all(dir.path().join("resources/material")).unwrap();
        let mut material = scenemax_assets::material::preset("Gold");
        material["name"] = serde_json::json!("Display name");
        std::fs::write(
            dir.path()
                .join("scripts/Scene assets/materials/custom_surface.smmat"),
            material.to_string(),
        )
        .unwrap();
        std::fs::write(
            dir.path().join("resources/material/materials-ext.json"),
            r#"{"materials":[{"name":"wall"},{"name":"custom_surface"}]}"#,
        )
        .unwrap();
        let scene = crate::scene3d::load(
            dir.path(),
            r#"{"entities":[{"type":"BOX","name":"surface"}]}"#,
        )
        .unwrap();
        assert_eq!(scene.catalog["material"], ["", "custom_surface", "wall"]);
        assert!(scene.materials.contains_key("custom_surface"));
        assert!(!scene.catalog["shader"].contains(&"custom_surface".into()));
    }
    #[test]
    fn native_surfaces_appear_only_in_material_catalog() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        std::fs::write(
            dir.path().join("scripts/finish.smmat"),
            scenemax_assets::material::preset("Gold").to_string(),
        )
        .unwrap();
        let catalog = super::load(
            &dir.path().join("resources"),
            &crate::material::documents(dir.path()).unwrap(),
        );
        assert!(catalog["material"].contains(&"finish".to_owned()));
        assert!(!catalog["shader"].contains(&"finish".to_owned()));
    }
}
