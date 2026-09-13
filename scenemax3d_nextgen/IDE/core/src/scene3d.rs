//! Lossless scene property transactions. Rendering and disk writes belong to adapters.
use serde_json::Value;

/// Apply a validated property transaction while retaining unknown Java metadata.
/// The caller checks the document revision and records the result in undo history.
pub fn patch(source: &str, pointer: &str, changes: &[(String, Value)]) -> Result<String, String> {
    let mut document: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    let camera = pointer == "/gameCamera";
    if camera && document.get("gameCamera").is_none() {
        document["gameCamera"] = serde_json::json!({"position":[0,2,10],"rotation":[0,1,0,0]});
    }
    let entry = document
        .pointer_mut(pointer)
        .ok_or("Scene entry no longer exists")?;
    if !entry.is_object() || (!camera && !entry["type"].is_string()) {
        return Err("Expected a scene entity".into());
    }
    let old_name = entry["name"].as_str().unwrap_or("").to_owned();
    let id = entry["id"].as_str().unwrap_or("").to_owned();
    for (key, value) in changes {
        if camera && key == "scale" {
            continue;
        }
        if camera && !matches!(key.as_str(), "position" | "rotation") {
            return Err("Game camera supports position and rotation only".into());
        }
        if matches!(key.as_str(), "id" | "type" | "children") {
            return Err("Identity and hierarchy cannot be changed through properties".into());
        }
        if matches!(
            key.as_str(),
            "position" | "rotation" | "scale" | "cinematicTargetOffset"
        ) {
            let count = if key == "rotation" { 4 } else { 3 };
            let values = value
                .as_array()
                .filter(|v| v.len() == count)
                .ok_or("Invalid transform")?;
            if values
                .iter()
                .any(|n| n.as_f64().is_none_or(|n| !n.is_finite() || n.abs() > 1e10))
            {
                return Err("Transform must contain finite numbers".into());
            }
            if key == "rotation" && values.iter().all(|n| n.as_f64() == Some(0.)) {
                return Err("Rotation cannot be a zero quaternion".into());
            }
        }
        if key == "name" && value.as_str().is_none_or(|s| s.trim().is_empty()) {
            return Err("Name cannot be empty".into());
        }
        entry[key] = value.clone();
    }
    if let Some((_, name)) = changes.iter().find(|(key, _)| key == "name") {
        update_references(&mut document["entities"], &id, &old_name, name);
    }
    serde_json::to_string_pretty(&document)
        .map(|s| s + "\n")
        .map_err(|e| e.to_string())
}

fn update_references(entries: &mut Value, id: &str, old_name: &str, new_name: &Value) {
    let Some(entries) = entries.as_array_mut() else {
        return;
    };
    for entry in entries {
        if !old_name.is_empty() && entry["attachTo"].as_str() == Some(old_name) {
            entry["attachTo"] = new_name.clone();
        }
        if !id.is_empty() && entry["cinematicTargetEntityId"].as_str() == Some(id) {
            entry["cinematicTargetEntityName"] = new_name.clone();
        }
        if let Some(segments) = entry
            .get_mut("cinematicSegments")
            .and_then(Value::as_array_mut)
        {
            for segment in segments {
                if !id.is_empty() && segment["trackId"].as_str() == Some(id) {
                    segment["trackName"] = new_name.clone();
                }
            }
        }
        if let Some(children) = entry.get_mut("children") {
            update_references(children, id, old_name, new_name);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn game_camera_round_trips_without_becoming_a_scene_entity() {
        let original = r#"{"entities":[],"future":42,"gameCamera":{"position":[1,2,3],"rotation":[0,1,0,0],"lensMetadata":"keep"}}"#;
        let edited = patch(
            original,
            "/gameCamera",
            &[("position".into(), serde_json::json!([7, 8, 9]))],
        )
        .unwrap();
        let doc: Value = serde_json::from_str(&edited).unwrap();
        assert_eq!(doc["gameCamera"]["position"], serde_json::json!([7, 8, 9]));
        assert_eq!(doc["gameCamera"]["lensMetadata"], "keep");
        assert_eq!(doc["entities"], serde_json::json!([]));
        assert_eq!(doc["future"], 42);
        assert!(doc["gameCamera"].get("type").is_none());
        assert!(
            patch(
                original,
                "/gameCamera",
                &[("rotation".into(), serde_json::json!([0, 0, 0, 0]))]
            )
            .is_err()
        );
        let created = patch(
            r#"{"entities":[]}"#,
            "/gameCamera",
            &[("position".into(), serde_json::json!([1, 2, 3]))],
        )
        .unwrap();
        assert!(created.contains("gameCamera"));
    }
    #[test]
    fn nested_transaction_preserves_rigs_code_and_unknown_metadata() {
        let source = r#"{"custom":42,"entities":[{"type":"CINEMATIC_RIG","cinematicSegments":[{"future":true}],"children":[{"type":"MODEL","id":"m","sceneMaxCode":"keep","ikLayers":[{"weight":0.5}],"position":[0,0,0]}]}]}"#;
        let edited = patch(
            source,
            "/entities/0/children/0",
            &[("position".into(), serde_json::json!([1, 2, 3]))],
        )
        .unwrap();
        let mut expected: Value = serde_json::from_str(source).unwrap();
        expected["entities"][0]["children"][0]["position"] = serde_json::json!([1, 2, 3]);
        assert_eq!(serde_json::from_str::<Value>(&edited).unwrap(), expected);
        assert!(
            patch(
                source,
                "/entities/0",
                &[("rotation".into(), serde_json::json!([0, 0, 0, 0]))]
            )
            .is_err()
        );
        assert!(patch(source, "/entities/0", &[("children".into(), Value::Null)]).is_err());
    }
}

/// Scene structure and clipboard operations.
pub mod structure;
