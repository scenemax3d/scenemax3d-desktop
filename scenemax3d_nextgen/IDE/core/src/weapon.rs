//! Lossless weapon authoring, compatible with SceneMax weapon definitions.
use serde_json::{Value, json};

/// Identity transform; rotations are degrees and collider base size is one unit.
pub fn transform() -> Value {
    json!({"offsetX":0.,"offsetY":0.,"offsetZ":0.,"rotationX":0.,"rotationY":0.,"rotationZ":0.,"scaleX":1.,"scaleY":1.,"scaleZ":1.})
}
/// The Java templates intentionally share the same unbound initial definition.
pub fn template(name: &str) -> Result<Value, String> {
    if name.trim().is_empty() {
        return Err("Enter a weapon name".into());
    }
    let slug = name
        .to_ascii_lowercase()
        .split(|c: char| !c.is_ascii_alphanumeric())
        .filter(|s| !s.is_empty())
        .collect::<Vec<_>>()
        .join("_");
    let id = format!("weapon_{}", if slug.is_empty() { "weapon" } else { &slug });
    Ok(
        json!({"type":"SceneMaxWeaponDefinition","schemaVersion":"1.0","id":id,"modelAssetId":"","defaultPostureId":"default","postures":[{"id":"default","name":"Default","attachmentPoint":"RightHandSocket","transform":transform()}],"colliders":[],"designerMetadata":{}}),
    )
}
/// Parse and normalize legacy definitions while retaining extension metadata.
pub fn parse(source: &str) -> Result<Value, String> {
    let mut v: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    if !v.is_object() {
        return Err("Weapon must be a JSON object".into());
    }
    if v.get("postures")
        .is_some_and(|p| !p.is_null() && !p.is_array())
    {
        return Err("Postures must be an array".into());
    }
    if !v["postures"].as_array().is_some_and(|p| !p.is_empty()) {
        v["postures"] = json!([{"id":"default","name":"Default","attachmentPoint":v["defaultAttachmentPoint"].as_str().unwrap_or("RightHandSocket"),"transform":v.get("attachmentTransform").cloned().unwrap_or_else(transform)}]);
        v["defaultPostureId"] = json!("default");
    }
    for key in ["modelAssetId", "defaultPostureId"] {
        if v.get(key).is_none() {
            v[key] = json!(if key == "defaultPostureId" {
                "default"
            } else {
                ""
            });
        }
    }
    if v.get("colliders").is_none() {
        v["colliders"] = json!([]);
    }
    if !v["designerMetadata"].is_object() {
        v["designerMetadata"] = json!({});
    }
    if v["designerMetadata"]
        .get("previewPlayerModelAssetId")
        .is_none()
    {
        v["designerMetadata"]["previewPlayerModelAssetId"] = json!("");
    }
    for key in ["postures", "colliders"] {
        let items = v[key]
            .as_array_mut()
            .ok_or_else(|| format!("{key} must be an array"))?;
        for item in items {
            if !item.is_object() {
                return Err(format!("Invalid {key} entry"));
            }
            if key == "postures" && item.get("attachmentPoint").is_none() {
                item["attachmentPoint"] = item.get("attachTo").cloned().unwrap_or(json!(""));
            }
            if item.get("transform").is_none() {
                item["transform"] = transform();
            }
            if !item["transform"].is_object() {
                return Err("Invalid transform".into());
            }
            for (k, value) in transform().as_object().into_iter().flatten() {
                if item["transform"].get(k).is_none() {
                    item["transform"][k] = value.clone();
                }
            }
        }
    }
    Ok(v)
}
/// Validate values before publishing a runtime resource.
pub fn validate(v: &Value) -> Result<(), String> {
    if v["id"]
        .as_str()
        .is_none_or(|s| s.trim().is_empty() || s.contains(['/', '\\', ':']))
    {
        return Err("Weapon ID is required and cannot contain path separators".into());
    }
    if !v["modelAssetId"].is_string() {
        return Err("Model asset ID must be text".into());
    }
    let mut ids = std::collections::BTreeSet::new();
    let postures = v["postures"]
        .as_array()
        .ok_or("Postures must be an array")?;
    if postures.is_empty() {
        return Err("Keep at least one posture".into());
    }
    for p in postures {
        let id = p["id"].as_str().unwrap_or("").trim();
        if id.is_empty()
            || p["name"].as_str().unwrap_or("").trim().is_empty()
            || !ids.insert(id.to_ascii_lowercase())
        {
            return Err("Postures require unique IDs and nonempty names".into());
        }
    }
    if !postures.iter().any(|p| {
        ["id", "name"].iter().any(|k| {
            p[k].as_str().is_some_and(|s| {
                s.eq_ignore_ascii_case(v["defaultPostureId"].as_str().unwrap_or(""))
            })
        })
    }) {
        return Err("Choose an existing default posture".into());
    }
    for key in ["postures", "colliders"] {
        for p in v[key].as_array().ok_or("Invalid entries")? {
            if key == "colliders" && !matches!(p["shape"].as_str(), Some("box" | "sphere")) {
                return Err("Collider shape must be box or sphere".into());
            }
            for k in transform()
                .as_object()
                .into_iter()
                .flatten()
                .map(|(k, _)| k)
            {
                let n = p["transform"][k]
                    .as_f64()
                    .ok_or("Transforms require finite numbers")?;
                if !n.is_finite() || !(n as f32).is_finite() || (k.starts_with("scale") && n <= 0.)
                {
                    return Err("Scale must be positive; transforms must be finite".into());
                }
            }
        }
    }
    Ok(())
}
/// Apply a field or collection command without dropping unrecognized fields.
pub fn edit(source: &str, key: &str, value: Value, index: usize) -> Result<String, String> {
    let mut v = parse(source)?;
    match key {
        "posture.add" | "posture.duplicate" | "collider.box" | "collider.sphere"
        | "collider.duplicate" => {
            let posture = key.starts_with("posture");
            let collection = if posture { "postures" } else { "colliders" };
            let default_posture = v["postures"]
                .as_array()
                .and_then(|ps| {
                    ps.iter()
                        .find(|p| {
                            ["id", "name"].iter().any(|k| {
                                p[k].as_str().is_some_and(|id| {
                                    id.eq_ignore_ascii_case(
                                        v["defaultPostureId"].as_str().unwrap_or(""),
                                    )
                                })
                            })
                        })
                        .or_else(|| ps.first())
                })
                .cloned();
            let list = v[collection].as_array_mut().ok_or("Missing entries")?;
            let duplicate = key.ends_with("duplicate");
            let mut item = if duplicate {
                list.get(index).cloned().ok_or("Select an entry")?
            } else if posture {
                default_posture.ok_or("No default posture")?
            } else {
                json!({"name":"weapon_collider","shape":if key.ends_with("sphere"){"sphere"}else{"box"},"transform":transform()})
            };
            let identity = if posture { "id" } else { "name" };
            let base = if duplicate {
                format!("{}_copy", item[identity].as_str().unwrap_or("entry"))
            } else if posture {
                format!("posture_{}", list.len() + 1)
            } else {
                format!("weapon_collider_{}", list.len() + 1)
            };
            let mut candidate = base.clone();
            let mut suffix = 2;
            while list.iter().any(|p| {
                p[identity]
                    .as_str()
                    .is_some_and(|v| v.eq_ignore_ascii_case(&candidate))
            }) {
                candidate = format!("{base}_{suffix}");
                suffix += 1;
            }
            if posture {
                item["id"] = json!(candidate);
                item["name"] = json!(if duplicate {
                    format!("{} Copy", item["name"].as_str().unwrap_or("Posture"))
                } else {
                    format!("Posture {}", list.len() + 1)
                });
            } else {
                item["name"] = json!(candidate);
            }
            if duplicate {
                list.insert(index + 1, item);
            } else {
                list.push(item);
            }
        }
        "posture.delete" | "collider.delete" => {
            let posture = key.starts_with("posture");
            let list = v[if posture { "postures" } else { "colliders" }]
                .as_array_mut()
                .ok_or("Missing entries")?;
            if index >= list.len() {
                return Err("Select an entry".into());
            }
            if posture && list.len() == 1 {
                return Err("Keep at least one posture".into());
            }
            let removed = list.remove(index);
            if posture
                && ["id", "name"].iter().any(|k| {
                    removed[k].as_str().is_some_and(|id| {
                        id.eq_ignore_ascii_case(v["defaultPostureId"].as_str().unwrap_or(""))
                    })
                })
            {
                v["defaultPostureId"] = v["postures"][0]["id"].clone();
            }
        }
        "posture.default" => {
            v["defaultPostureId"] =
                v["postures"].get(index).ok_or("Select a posture")?["id"].clone();
        }
        pointer => {
            let old = v.pointer(pointer).cloned().ok_or("Unknown weapon field")?;
            if pointer.starts_with("/postures/")
                && (pointer.ends_with("/id") || pointer.ends_with("/name"))
                && old.as_str().is_some_and(|id| {
                    id.eq_ignore_ascii_case(v["defaultPostureId"].as_str().unwrap_or(""))
                })
            {
                v["defaultPostureId"] = value.clone();
            }
            let target = v.pointer_mut(pointer).ok_or("Unknown weapon field")?;
            if pointer.ends_with("/transform") && target.is_object() && value.is_object() {
                if let (Some(target), Some(fields)) = (target.as_object_mut(), value.as_object()) {
                    target.extend(fields.clone());
                }
            } else {
                *target = value;
            }
            if pointer == "/modelAssetId" && old != v["modelAssetId"] {
                for p in v["postures"].as_array_mut().into_iter().flatten() {
                    if let (Some(target), Some(identity)) =
                        (p["transform"].as_object_mut(), transform().as_object())
                    {
                        target.extend(identity.clone());
                    }
                }
            }
        }
    }
    validate(&v)?;
    serde_json::to_string_pretty(&v).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn edits_preserve_extensions_and_default_identity() {
        let mut v = template("Test blade").unwrap();
        v["extension"] = json!({"opaque":42});
        let s = edit(&v.to_string(), "/postures/0/id", json!("grip"), 0).unwrap();
        let s = edit(&s, "posture.duplicate", Value::Null, 0).unwrap();
        let s = edit(&s, "posture.delete", Value::Null, 0).unwrap();
        let v = parse(&s).unwrap();
        assert_eq!(v["extension"]["opaque"], 42);
        assert_eq!(v["defaultPostureId"], v["postures"][0]["id"]);
        assert!(edit(&s, "posture.delete", Value::Null, 0).is_err());
        assert!(edit(&s, "/postures/0/transform/scaleX", json!(0), 0).is_err());
    }
    #[test]
    fn legacy_normalizes_and_model_change_resets_postures() {
        let s = r#"{"id":"weapon_test","defaultAttachmentPoint":"socket","attachmentTransform":{"offsetX":4}}"#;
        let v = parse(s).unwrap();
        validate(&v).unwrap();
        assert_eq!(v["postures"][0]["transform"]["offsetX"], 4);
        let s = edit(s, "/modelAssetId", json!("registered_model"), 0).unwrap();
        assert_eq!(parse(&s).unwrap()["postures"][0]["transform"], transform());
    }
    #[test]
    fn new_posture_clones_selected_default_and_transform_extensions_survive() {
        let mut v = template("sample").unwrap();
        v["postures"][0]["transform"]["future"] = json!({"x":1});
        let s = edit(&v.to_string(), "posture.add", Value::Null, 0).unwrap();
        let s = edit(
            &s,
            "/postures/1/attachmentPoint",
            json!("another_socket"),
            0,
        )
        .unwrap();
        let s = edit(&s, "posture.default", Value::Null, 1).unwrap();
        let s = edit(&s, "posture.add", Value::Null, 0).unwrap();
        let v = parse(&s).unwrap();
        assert_eq!(v["postures"][2]["attachmentPoint"], "another_socket");
        let s = edit(&s, "/postures/0/transform", transform(), 0).unwrap();
        assert_eq!(
            parse(&s).unwrap()["postures"][0]["transform"]["future"]["x"],
            1
        );
    }
}
