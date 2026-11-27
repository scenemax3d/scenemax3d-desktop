//! Lossless HUD subtree copies with fresh identities and internal constraint references.
use super::{Value, parse};
use std::collections::{BTreeSet, HashMap};

fn widget_path(pointer: &str) -> bool {
    let parts: Vec<_> = pointer.split('/').collect();
    parts.len() >= 5
        && parts[0].is_empty()
        && parts[1] == "layers"
        && parts[2].parse::<usize>().is_ok()
        && parts[3] == "widgets"
        && parts[4].parse::<usize>().is_ok()
        && parts[5..]
            .chunks(2)
            .all(|p| p.len() == 2 && p[0] == "children" && p[1].parse::<usize>().is_ok())
}
/// Copy an entire widget subtree without changing the document.
pub fn copy(source: &str, pointer: &str) -> Result<Value, String> {
    let root = parse(source)?;
    if !widget_path(pointer) {
        return Err("Select a UI element to copy".into());
    }
    root.pointer(pointer)
        .filter(|v| v["type"].is_string() && v["name"].is_string())
        .cloned()
        .ok_or("Select a UI element to copy".into())
}
/// A layer or panel is a valid paste destination, including an empty container.
pub fn can_paste(root: &Value, pointer: &str) -> bool {
    let p: Vec<_> = pointer.split('/').collect();
    let layer =
        p.len() == 3 && p[0].is_empty() && p[1] == "layers" && p[2].parse::<usize>().is_ok();
    root.pointer(pointer)
        .is_some_and(|v| v.is_object() && (layer || (widget_path(pointer) && v["type"] == "PANEL")))
}
fn reserve(value: &Value, names: &mut BTreeSet<String>) {
    match value {
        Value::Object(o) => {
            for key in ["name", "id"] {
                if let Some(s) = o.get(key).and_then(Value::as_str) {
                    names.insert(s.into());
                }
            }
            for child in o.values() {
                reserve(child, names);
            }
        }
        Value::Array(a) => {
            for child in a {
                reserve(child, names);
            }
        }
        _ => {}
    }
}
fn identities(
    value: &mut Value,
    names: &mut BTreeSet<String>,
    map: &mut HashMap<String, String>,
) -> Result<(), String> {
    for key in ["name", "id"] {
        if let Some(old) = value.get(key).and_then(Value::as_str).map(str::to_owned) {
            let fresh = if !names.contains(&old) {
                old.clone()
            } else {
                (1..=16000)
                    .map(|i| format!("{old}_copy{i}"))
                    .find(|n| !names.contains(n))
                    .ok_or("UI element name limit reached")?
            };
            names.insert(fresh.clone());
            if key == "name" {
                map.insert(old, fresh.clone());
            }
            value[key] = Value::String(fresh);
        }
    }
    if let Some(children) = value.get_mut("children").and_then(Value::as_array_mut) {
        for child in children {
            identities(child, names, map)?;
        }
    }
    Ok(())
}
fn constraints(value: &mut Value, map: &HashMap<String, String>) {
    if let Some(edges) = value.get_mut("constraints").and_then(Value::as_array_mut) {
        for edge in edges {
            if let Some(name) = edge["targetName"]
                .as_str()
                .filter(|s| *s != "parent")
                .and_then(|s| map.get(s))
                .cloned()
            {
                edge["targetName"] = Value::String(name);
            }
        }
    }
    if let Some(children) = value.get_mut("children").and_then(Value::as_array_mut) {
        for child in children {
            constraints(child, map);
        }
    }
}
/// Paste into a layer or panel. Returns the new document and the pasted root's pointer.
pub fn paste(source: &str, parent: &str, clipboard: &Value) -> Result<(String, String), String> {
    let mut root = parse(source)?;
    if !can_paste(&root, parent) {
        return Err("Paste into a layer or panel".into());
    }
    // Validate the clipboard with the same depth/size limits before recursive traversal.
    let wrapper = serde_json::json!({"layers":[{"widgets":[clipboard]}]});
    let wrapper = parse(&serde_json::to_string(&wrapper).map_err(|e| e.to_string())?)?;
    let mut copied = copy(&wrapper.to_string(), "/layers/0/widgets/0")?;
    let mut names = BTreeSet::new();
    reserve(&root, &mut names);
    let mut map = HashMap::new();
    identities(&mut copied, &mut names, &mut map)?;
    constraints(&mut copied, &map);
    let container = if widget_path(parent) {
        "children"
    } else {
        "widgets"
    };
    let target = root
        .pointer_mut(parent)
        .ok_or("Missing paste destination")?;
    if target.get(container).is_none() {
        target[container] = serde_json::json!([]);
    }
    let children = target[container]
        .as_array_mut()
        .ok_or("Invalid UI container")?;
    let pointer = format!("{parent}/{container}/{}", children.len());
    children.push(copied);
    let result = serde_json::to_string_pretty(&root).map_err(|e| e.to_string())?;
    parse(&result)?;
    Ok((result, pointer))
}

#[cfg(test)]
mod tests {
    use super::*;
    const SOURCE: &str = r#"{"name":"hud","extra":17,"layers":[{"name":"main","widgets":[{"name":"panel","id":"p","type":"PANEL","children":[{"name":"caption","type":"TEXT_VIEW","text":"Hello","future":{"keep":true}},{"name":"go","type":"BUTTON","onClickProcedure":"launch","constraints":[{"targetName":"caption","side":"TOP","targetSide":"BOTTOM"},{"targetName":"parent","side":"LEFT"}]}]}]},{"name":"empty","widgets":[]}]}"#;
    #[test]
    fn nested_copy_rekeys_names_ids_and_internal_constraints_without_changing_payload() {
        let original = parse(SOURCE).unwrap();
        let clip = copy(SOURCE, "/layers/0/widgets/0").unwrap();
        let (source, pointer) = paste(SOURCE, "/layers/0", &clip).unwrap();
        let root = parse(&source).unwrap();
        let node = root.pointer(&pointer).unwrap();
        assert_eq!(
            root["layers"][0]["widgets"][0],
            original["layers"][0]["widgets"][0]
        );
        assert_eq!(node["name"], "panel_copy1");
        assert_eq!(node["id"], "p_copy1");
        assert_eq!(
            node["children"][1]["constraints"][0]["targetName"],
            "caption_copy1"
        );
        assert_eq!(
            node["children"][1]["constraints"][1]["targetName"],
            "parent"
        );
        assert_eq!(node["children"][1]["onClickProcedure"], "launch");
        assert_eq!(node["children"][0]["future"]["keep"], true);
        let (again, p) = paste(&source, "/layers/0/widgets/0", &clip).unwrap();
        assert_eq!(
            parse(&again).unwrap().pointer(&p).unwrap()["name"],
            "panel_copy2"
        );
        assert_eq!(clip, original["layers"][0]["widgets"][0]);
    }
    #[test]
    fn any_widget_copies_to_empty_layers_and_other_documents_but_leaves_are_not_containers() {
        for kind in [
            "BUTTON",
            "TEXT_VIEW",
            "EDIT_TEXT",
            "IMAGE",
            "LIST_VIEW",
            "PANEL",
        ] {
            let clip = serde_json::json!({"name":"item","type":kind});
            let (s, p) = paste(SOURCE, "/layers/1", &clip).unwrap();
            assert_eq!(parse(&s).unwrap().pointer(&p).unwrap()["type"], kind);
        }
        assert!(copy(SOURCE, "/layers/0").is_err());
        assert!(
            paste(
                SOURCE,
                "/layers/0/widgets/0/children/0",
                &copy(SOURCE, "/layers/0/widgets/0").unwrap()
            )
            .is_err()
        );
        assert!(paste(SOURCE, "/layers/999", &serde_json::json!({})).is_err());
    }
}
