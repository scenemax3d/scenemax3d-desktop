//! Lossless scene insertion, removal and clipboard transactions.
use serde_json::{Value, json};
/// Java toolbar entity types, in toolbar order.
pub const ADD_TYPES: &[&str] = &[
    "SPHERE",
    "BOX",
    "WEDGE",
    "CYLINDER",
    "CONE",
    "HOLLOW_CYLINDER",
    "QUAD",
    "STAIRS",
    "ARCH",
    "MODEL",
    "LIGHT",
    "PATH",
    "CINEMATIC_RIG",
];
/// New Java-compatible authoring object; callers choose its placement and model resource.
pub fn template(kind: &str) -> Result<Value, String> {
    if !ADD_TYPES.contains(&kind) && kind != "CINEMATIC_TRACK" {
        return Err("Unsupported scene entity type".into());
    }
    let mut entry = json!({"type":kind,"name":kind.to_lowercase(),"position":[0,0.5,0],"rotation":[0,0,0,1],"scale":[1,1,1],"hidden":false});
    let extra = match kind {
        "SPHERE" => json!({"radius":0.5}),
        "BOX" => json!({"sizeX":0.5,"sizeY":0.5,"sizeZ":0.5}),
        "WEDGE" => json!({"wedgeWidth":1,"wedgeHeight":1,"wedgeDepth":1}),
        "CYLINDER" | "CONE" | "HOLLOW_CYLINDER" => {
            json!({"radiusTop":if kind=="CONE" {0}else{1},"radiusBottom":1,"height":2,"innerRadiusTop":0.5,"innerRadiusBottom":0.5})
        }
        "QUAD" => json!({"quadWidth":1,"quadHeight":1}),
        "STAIRS" => {
            json!({"stairsWidth":2,"stairsStepHeight":0.25,"stairsStepDepth":0.4,"stairsStepCount":6})
        }
        "ARCH" => {
            json!({"archWidth":2,"archHeight":2.5,"archDepth":0.5,"archThickness":0.35,"archSegments":12})
        }
        "LIGHT" => {
            json!({"lightType":"point","lightColor":"warm","lightIntensity":900,"lightIntensityUnit":"lumens","lightRange":12,"lightShadowMode":"medium"})
        }
        "CINEMATIC_RIG" => {
            json!({"cinematicPreviewDuration":5,"cinematicEaseIn":"linear","cinematicEaseOut":"linear","cinematicSegments":[],"children":[template("CINEMATIC_TRACK")?]})
        }
        "CINEMATIC_TRACK" => {
            json!({"cinematicTrackData":{"radiusX":2.5,"radiusZ":2.5,"anchorCount":360,"selectedStartAnchor":0,"selectedEndAnchor":90}})
        }
        _ => json!({}),
    };
    for (key, value) in extra.as_object().ok_or("Invalid template")? {
        entry[key] = value.clone();
    }
    Ok(entry)
}
/// Insert a copied/new subtree, remapping its identities and internal references.
/// Returns serialized source and the inserted root's JSON pointer.
pub fn insert(
    source: &str,
    parent: Option<&str>,
    mut entry: Value,
) -> Result<(String, String), String> {
    let mut doc: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    let mut reserved = source.to_owned();
    let mut mappings = Vec::new();
    fn identity(
        e: &mut Value,
        reserved: &mut String,
        mappings: &mut Vec<(String, String)>,
    ) -> Result<(), String> {
        if !e["type"].is_string() {
            return Err("Clipboard does not contain a scene object".into());
        }
        for key in ["id", "name"] {
            let old = e[key].as_str().unwrap_or("").to_owned();
            let base = if key == "id" {
                "studio".to_owned()
            } else {
                e["type"].as_str().unwrap_or("object").to_lowercase()
            };
            let mut n = 1;
            let value = loop {
                let candidate = format!("{base}_{n}");
                if !reserved.contains(&format!("\"{candidate}\"")) {
                    break candidate;
                }
                n += 1;
            };
            reserved.push_str(&format!("\"{value}\""));
            if !old.is_empty() {
                mappings.push((old, value.clone()));
            }
            e[key] = json!(value);
        }
        if let Some(children) = e.get_mut("children").and_then(Value::as_array_mut) {
            for child in children {
                identity(child, reserved, mappings)?;
            }
        }
        Ok(())
    }
    identity(&mut entry, &mut reserved, &mut mappings)?;
    fn references(v: &mut Value, mappings: &[(String, String)]) {
        match v {
            Value::Object(obj) => {
                for (key, value) in obj {
                    if [
                        "attachTo",
                        "trackId",
                        "trackName",
                        "cinematicTargetEntityId",
                        "cinematicTargetEntityName",
                    ]
                    .contains(&key.as_str())
                    {
                        if let Some((_, new)) =
                            mappings.iter().find(|(old, _)| value.as_str() == Some(old))
                        {
                            *value = json!(new);
                        }
                    } else {
                        references(value, mappings);
                    }
                }
            }
            Value::Array(a) => {
                for v in a {
                    references(v, mappings);
                }
            }
            _ => {}
        }
    }
    references(&mut entry, &mappings);
    let list = if let Some(parent) = parent {
        let p = doc.pointer_mut(parent).ok_or("Missing parent")?;
        if !matches!(p["type"].as_str(), Some("SECTION" | "CINEMATIC_RIG")) {
            return Err("Select a section or rig as parent".into());
        }
        if p.get("children").is_none() {
            p["children"] = json!([]);
        }
        format!("{parent}/children")
    } else {
        "/entities".into()
    };
    let items = doc
        .pointer_mut(&list)
        .and_then(Value::as_array_mut)
        .ok_or("Invalid scene hierarchy")?;
    let pointer = format!("{list}/{}", items.len());
    items.push(entry);
    Ok((
        serde_json::to_string_pretty(&doc).map_err(|e| e.to_string())? + "\n",
        pointer,
    ))
}
/// Remove one entity subtree without disturbing unrelated metadata.
pub fn remove(source: &str, pointer: &str) -> Result<String, String> {
    let mut doc: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    if doc.pointer(pointer).is_none_or(|e| e["type"] == "CAMERA") {
        return Err("Select a removable object".into());
    }
    let (parent, index) = pointer.rsplit_once('/').ok_or("Invalid entity pointer")?;
    let index: usize = index.parse().map_err(|_| "Invalid entity index")?;
    let list = doc
        .pointer_mut(parent)
        .and_then(Value::as_array_mut)
        .ok_or("Invalid hierarchy")?;
    if index >= list.len() {
        return Err("Missing entity".into());
    }
    list.remove(index);
    serde_json::to_string_pretty(&doc)
        .map(|s| s + "\n")
        .map_err(|e| e.to_string())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn duplicate_remaps_nested_rig_references_and_preserves_metadata() {
        let source = r#"{"custom":42,"entities":[]}"#;
        let entry = json!({"id":"rig","name":"Rig","type":"CINEMATIC_RIG","cinematicSegments":[{"trackId":"track","trackName":"Track"}],"children":[{"id":"track","name":"Track","type":"CINEMATIC_TRACK","future":7}]});
        let (s, p) = insert(source, None, entry).unwrap();
        let d: Value = serde_json::from_str(&s).unwrap();
        let e = d.pointer(&p).unwrap();
        assert_eq!(e["cinematicSegments"][0]["trackId"], e["children"][0]["id"]);
        assert_eq!(e["children"][0]["future"], 7);
        let d: Value = serde_json::from_str(&remove(&s, &p).unwrap()).unwrap();
        assert_eq!(d["custom"], 42);
        assert!(d["entities"].as_array().unwrap().is_empty());
    }
}
