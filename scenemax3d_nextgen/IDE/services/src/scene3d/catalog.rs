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

/// Read definitions off the UI thread. Resolve IDs independently of filenames.
pub(super) fn ik_assets(root: &Path) -> BTreeMap<String, Value> {
    let mut result = BTreeMap::new();
    let mut seen = std::collections::HashSet::new();
    let mut duplicates = std::collections::HashSet::new();
    let Ok(root) = root.canonicalize() else {
        return result;
    };
    for folder in ["ik", "IK"] {
        let Ok(files) = std::fs::read_dir(root.join(folder)) else {
            continue;
        };
        for entry in files.flatten() {
            let Ok(path) = entry.path().canonicalize() else {
                continue;
            };
            if !path.starts_with(&root) || !seen.insert(path.clone()) {
                continue;
            }
            let name = path
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .to_string();
            let Some(stem) = name
                .strip_suffix(".smik")
                .or_else(|| name.strip_suffix(".ik.json"))
            else {
                continue;
            };
            if !std::fs::metadata(&path).is_ok_and(|m| m.len() <= 8 * 1024 * 1024) {
                continue;
            }
            let Ok(source) = std::fs::read_to_string(path) else {
                continue;
            };
            let Ok(value) = scenemax_ide_core::ik::parse(&source) else {
                continue;
            };
            let keys = [
                value["id"].as_str().unwrap_or(stem).to_ascii_lowercase(),
                stem.to_ascii_lowercase(),
            ];
            for key in keys.into_iter().collect::<std::collections::HashSet<_>>() {
                if result.insert(key.clone(), value.clone()).is_some() {
                    duplicates.insert(key);
                }
            }
        }
    }
    for key in duplicates {
        result.remove(&key);
    }
    result
}

#[cfg(test)]
mod ik_tests {
    #[test]
    fn reads_ik_by_id_and_filename_and_rejects_duplicate_ids() {
        let dir = tempfile::tempdir().unwrap();
        let folder = dir.path().join("ik");
        std::fs::create_dir(&folder).unwrap();
        let source = scenemax_ide_core::ik::template("Contact", "TwoBoneIK")
            .unwrap()
            .to_string();
        std::fs::write(folder.join("different_filename.smik"), &source).unwrap();
        let assets = super::ik_assets(dir.path());
        assert!(assets.contains_key("ik_contact"));
        assert!(assets.contains_key("different_filename"));
        std::fs::write(folder.join("duplicate.smik"), &source).unwrap();
        assert!(!super::ik_assets(dir.path()).contains_key("ik_contact"));
    }
}
