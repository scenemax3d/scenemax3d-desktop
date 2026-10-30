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
    if !ADD_TYPES.contains(&kind) && !matches!(kind, "CINEMATIC_TRACK" | "SECTION" | "CODE") {
        return Err("Unsupported scene entity type".into());
    }
    let mut entry = json!({"type":kind,"name":kind.to_lowercase(),"position":[0,0.5,0],"rotation":[0,0,0,1],"scale":[1,1,1],"hidden":false});
    // An omitted model scale inherits its catalog default in both the designer
    // and generated script. Explicit scales are reserved for authored overrides.
    if kind == "MODEL" {
        entry.as_object_mut().unwrap().remove("scale");
    }
    let extra = match kind {
        "SECTION" => json!({"children":[]}),
        "CODE" => json!({"codeText":""}),
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
/// Destination relative to the hierarchy before removing the moving subtree.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Placement {
    /// Insert before the object at this JSON pointer.
    Before(String),
    /// Insert after the object at this JSON pointer.
    After(String),
    /// Append to the folder or rig at this JSON pointer.
    Inside(String),
    /// Append at the scene root.
    Root,
}
/// Move without remapping names/IDs or changing transforms. Folders are organizational.
pub fn relocate(
    source: &str,
    pointer: &str,
    placement: &Placement,
) -> Result<(String, String), String> {
    let mut doc: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    let moving = doc.pointer(pointer).ok_or("Missing source object")?;
    if !pointer.starts_with("/entities/") || moving["type"] == "CAMERA" {
        return Err("The game camera cannot be moved".into());
    }
    let (source_list, source_index) = pointer.rsplit_once('/').ok_or("Invalid source pointer")?;
    let source_index: usize = source_index.parse().map_err(|_| "Invalid source index")?;
    let target = match placement {
        Placement::Before(p) | Placement::After(p) | Placement::Inside(p) => Some(p),
        Placement::Root => None,
    };
    if let Some(target) = target {
        if target == pointer || target.starts_with(&format!("{pointer}/")) {
            return Err("Cannot move an object into itself or its descendants".into());
        }
        if !target.starts_with("/entities/")
            || doc.pointer(target).is_none_or(|e| e["type"] == "CAMERA")
        {
            return Err("Invalid destination".into());
        }
    }
    let (mut destination, mut index) = match placement {
        Placement::Root => (
            "/entities".to_owned(),
            doc["entities"].as_array().ok_or("Missing hierarchy")?.len(),
        ),
        Placement::Inside(p) => {
            let parent = doc.pointer(p).ok_or("Missing folder")?;
            if !matches!(parent["type"].as_str(), Some("SECTION" | "CINEMATIC_RIG")) {
                return Err("Drop inside a folder or cinematic rig".into());
            }
            (
                format!("{p}/children"),
                parent["children"].as_array().map_or(0, Vec::len),
            )
        }
        Placement::Before(p) | Placement::After(p) => {
            let (list, i) = p.rsplit_once('/').ok_or("Invalid destination")?;
            let i: usize = i.parse().map_err(|_| "Invalid destination index")?;
            (
                list.to_owned(),
                i + usize::from(matches!(placement, Placement::After(_))),
            )
        }
    };
    let parent_kind = destination
        .strip_suffix("/children")
        .and_then(|p| doc.pointer(p))
        .and_then(|e| e["type"].as_str());
    if (moving["type"] == "CINEMATIC_TRACK") != (parent_kind == Some("CINEMATIC_RIG")) {
        return Err("Cinematic tracks must stay inside a cinematic rig".into());
    }
    let moving = doc
        .pointer_mut(source_list)
        .and_then(Value::as_array_mut)
        .ok_or("Invalid source list")?
        .remove(source_index);
    if destination == source_list && index > source_index {
        index -= 1;
    }
    // Removing an earlier sibling also shifts paths to folders below that sibling list.
    let prefix = format!("{source_list}/");
    if let Some(rest) = destination.strip_prefix(&prefix) {
        let (head, tail) = rest.split_once('/').unwrap_or((rest, ""));
        let i: usize = head.parse().map_err(|_| "Invalid destination path")?;
        if i > source_index {
            destination = format!(
                "{prefix}{}{}",
                i - 1,
                if tail.is_empty() {
                    String::new()
                } else {
                    format!("/{tail}")
                }
            );
        }
    }
    if let Some(parent) = destination.strip_suffix("/children") {
        let parent = doc
            .pointer_mut(parent)
            .ok_or("Missing destination folder")?;
        if parent.get("children").is_none() {
            parent["children"] = json!([]);
        }
    }
    let list = doc
        .pointer_mut(&destination)
        .and_then(Value::as_array_mut)
        .ok_or("Invalid destination list")?;
    if index > list.len() {
        return Err("Invalid destination index".into());
    }
    list.insert(index, moving);
    fn depth(items: &Value, level: usize) -> Result<(), String> {
        if level > 32 {
            return Err("Scene hierarchy exceeds 32 levels".into());
        }
        if let Some(items) = items.as_array() {
            for item in items {
                if item.get("children").is_some() {
                    depth(&item["children"], level + 1)?;
                }
            }
        }
        Ok(())
    }
    depth(&doc["entities"], 0)?;
    Ok((
        serde_json::to_string_pretty(&doc).map_err(|e| e.to_string())? + "\n",
        format!("{destination}/{index}"),
    ))
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
    fn moves_preserve_identity_metadata_and_codegen_order() {
        let source = r#"{"custom":42,"entities":[{"type":"BOX","name":"first","id":"a","scale":[2,3,4]},{"type":"SECTION","name":"folder","children":[{"type":"CODE","name":"logic","codeText":"Logger.info \"inside\""}]},{"type":"BOX","name":"last","id":"z"}]}"#;
        let (moved, pointer) = relocate(
            source,
            "/entities/0",
            &Placement::Inside("/entities/1".into()),
        )
        .unwrap();
        assert_eq!(pointer, "/entities/0/children/1");
        let value: Value = serde_json::from_str(&moved).unwrap();
        assert_eq!(value["custom"], 42);
        assert_eq!(value.pointer(&pointer).unwrap()["id"], "a");
        assert_eq!(value.pointer(&pointer).unwrap()["scale"], json!([2, 3, 4]));
        let code = crate::scene_codegen::generate(&moved, "scene.smdesign", "", "", &[]).unwrap();
        assert!(code.find("inside").unwrap() < code.find("first =>").unwrap());
        assert!(code.find("first =>").unwrap() < code.find("last =>").unwrap());
        let (root, p) = relocate(&moved, &pointer, &Placement::Root).unwrap();
        assert_eq!(p, "/entities/2");
        let (before, p) = relocate(&root, &p, &Placement::Before("/entities/1".into())).unwrap();
        assert_eq!(p, "/entities/1");
        let (_, p) = relocate(&before, &p, &Placement::After("/entities/2".into())).unwrap();
        assert_eq!(p, "/entities/2");
    }
    #[test]
    fn hierarchy_rejects_cycles_camera_and_invalid_rig_moves() {
        let source = r#"{"camera":{},"entities":[{"type":"SECTION","children":[{"type":"SECTION","children":[]}]},{"type":"CINEMATIC_RIG","children":[{"type":"CINEMATIC_TRACK"}]},{"type":"BOX"}]}"#;
        for (p, target) in [
            (
                "/entities/0",
                Placement::Inside("/entities/0/children/0".into()),
            ),
            ("/camera", Placement::Root),
            ("/entities/1/children/0", Placement::Root),
            ("/entities/2", Placement::Inside("/entities/1".into())),
            ("/entities/0", Placement::Inside("/entities/2".into())),
        ] {
            assert!(relocate(source, p, &target).is_err());
        }
        let (moved, p) = relocate(
            source,
            "/entities/0/children/0",
            &Placement::After("/entities/0".into()),
        )
        .unwrap();
        assert_eq!(p, "/entities/1");
        assert_eq!(
            serde_json::from_str::<Value>(&moved).unwrap()["entities"][0]["children"],
            json!([])
        );
    }
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
