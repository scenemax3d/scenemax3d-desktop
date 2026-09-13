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
