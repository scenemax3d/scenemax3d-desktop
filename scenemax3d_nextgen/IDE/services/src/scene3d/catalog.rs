//! Project-local choices loaded on the existing scene storage worker.
use serde_json::Value;
use std::{collections::BTreeMap, path::Path};

pub(super) fn load(root: &Path) -> BTreeMap<String, Vec<String>> {
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
    if let Some(project) = root.parent()
        && let Ok(materials) = crate::material::documents(project)
    {
        catalog
            .entry("material".into())
            .or_default()
            .extend(materials.into_keys());
    }
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
    fn native_surfaces_appear_only_in_material_catalog() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        std::fs::write(
            dir.path().join("scripts/finish.smmat"),
            scenemax_assets::material::preset("Gold").to_string(),
        )
        .unwrap();
        let catalog = super::load(&dir.path().join("resources"));
        assert!(catalog["material"].contains(&"finish".to_owned()));
        assert!(!catalog["shader"].contains(&"finish".to_owned()));
    }
}
