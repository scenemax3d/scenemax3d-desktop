//! Pure automation schemas and undoable JSON authoring operations.
mod documents;
pub use documents::{edit, inspect, validate};
use serde_json::{Value, json};
/// Discoverable tools; the checked-in catalog also drives documentation.
pub fn tools() -> Vec<Value> {
    serde_json::from_str(include_str!("tools.json")).unwrap_or_default()
}
/// Validate advertised argument types before dispatch; reject unknown/misspelled fields.
pub fn validate_arguments(name: &str, args: &Value) -> Result<(), String> {
    let tool = tools()
        .into_iter()
        .find(|v| v["name"] == name)
        .ok_or("Unknown tool")?;
    let object = args.as_object().ok_or("arguments must be an object")?;
    for required in tool["inputSchema"]["required"]
        .as_array()
        .unwrap_or(&vec![])
    {
        if !object.contains_key(required.as_str().unwrap_or("")) {
            return Err(format!("Missing {required}"));
        }
    }
    for (key, v) in object {
        let schema = &tool["inputSchema"]["properties"][key];
        let valid = match schema["type"].as_str() {
            Some("string") => v.is_string(),
            Some("boolean") => v.is_boolean(),
            Some("integer") => v.is_i64() || v.is_u64(),
            Some("number") => v.is_number(),
            Some("array") => v.is_array(),
            Some("object") => v.is_object(),
            _ => false,
        };
        if !valid {
            return Err(format!("Invalid argument: {key}"));
        }
        if let Some(allowed) = schema["enum"].as_array()
            && !allowed.contains(v)
        {
            return Err(format!("Invalid value for {key}"));
        }
        if let Some(array) = v.as_array() {
            if array.len() > schema["maxItems"].as_u64().unwrap_or(2000) as usize
                || array.len() < schema["minItems"].as_u64().unwrap_or(0) as usize
            {
                return Err(format!("Invalid array length: {key}"));
            }
            for item in array {
                let valid = match schema["items"]["type"].as_str() {
                    Some("number") => item.is_number(),
                    Some("string") => item.is_string(),
                    Some("object") => item.is_object(),
                    _ => true,
                };
                if !valid {
                    return Err(format!("Invalid array item: {key}"));
                }
            }
        }
    }
    Ok(())
}
/// New UI document compatible with the native designer/runtime.
pub fn ui_template() -> Value {
    json!({"name":"UI","canvasWidth":1280,"canvasHeight":720,"layers":[{"name":"main","visible":true,"zOrder":0,"widgets":[]}]})
}
/// New scene document compatible with the scene generator.
pub fn scene_template() -> Value {
    json!({"version":1,"entities":[],"gameCamera":{"position":[0,2,10],"rotation":[0,0,0,1]}})
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn scene_transactions_preserve_unknowns_and_identity() {
        let mut doc = scene_template();
        doc["future"] = json!({"keep":true});
        let source = edit(
            "designer.add_primitive",
            &doc.to_string(),
            &json!({"primitive":"BOX","name":"cube"}),
        )
        .unwrap();
        let entity = inspect(
            "designer.get_entity",
            &source,
            &json!({"entity_name":"cube"}),
        )
        .unwrap();
        let id = entity["entity"]["id"].clone();
        let source = edit(
            "designer.update_entity",
            &source,
            &json!({"entity_id":id,"updates":{"position":[1,2,3]}}),
        )
        .unwrap();
        let v: Value = serde_json::from_str(&source).unwrap();
        assert_eq!(v["future"], doc["future"]);
        assert_eq!(v["entities"][0]["id"], id);
        assert!(
            edit(
                "designer.update_entity",
                &source,
                &json!({"entity_id":id,"updates":{"rotation":[0,0,0,0]}})
            )
            .is_err()
        );
        let source = edit(
            "designer.duplicate_entity",
            &source,
            &json!({"entity_id":id,"count":2,"step":[2,0,0]}),
        )
        .unwrap();
        let v: Value = serde_json::from_str(&source).unwrap();
        assert_eq!(v["entities"].as_array().unwrap().len(), 3);
        assert_ne!(v["entities"][0]["id"], v["entities"][1]["id"]);
        assert_eq!(v["entities"][2]["position"][0], 5.);
        let ids = v["entities"]
            .as_array()
            .unwrap()
            .iter()
            .map(|e| e["id"].clone())
            .collect::<Vec<_>>();
        let grouped = edit(
            "designer.group_entities",
            &source,
            &json!({"action":"group","entity_ids":ids}),
        )
        .unwrap();
        let ungrouped = edit(
            "designer.group_entities",
            &grouped,
            &json!({"action":"ungroup","entity_name":"group"}),
        )
        .unwrap();
        assert_eq!(
            serde_json::from_str::<Value>(&ungrouped).unwrap()["entities"],
            v["entities"]
        );
    }
    #[test]
    fn ui_transactions_and_exact_text_conflicts() {
        let source = edit(
            "ui.add_widget",
            &ui_template().to_string(),
            &json!({"type":"BUTTON","name":"start"}),
        )
        .unwrap();
        let source = edit(
            "ui.update_widget",
            &source,
            &json!({"widgetPath":"/layers/0/widgets/0","updates":{"buttonText":"Begin"}}),
        )
        .unwrap();
        assert_eq!(
            inspect("ui.get_widget", &source, &json!({"widgetPath":"start"})).unwrap()["entity"]["buttonText"],
            "Begin"
        );
        let source = edit("ui.delete_widget", &source, &json!({"widgetPath":"start"})).unwrap();
        assert!(
            inspect("ui.list_widgets", &source, &json!({})).unwrap()["widgets"]
                .as_array()
                .unwrap()
                .is_empty()
        );
        assert!(
            edit(
                "file.modify",
                "same same",
                &json!({"expectedText":"same","replacementText":"new"})
            )
            .is_err()
        );
        assert_eq!(
            edit(
                "file.modify",
                "same same",
                &json!({"expectedText":"same","replacementText":"new","replaceAll":true})
            )
            .unwrap(),
            "new new"
        );
    }
    #[test]
    fn every_tool_has_unique_valid_schema() {
        let tools = tools();
        assert!(tools.len() > 60);
        let mut names = std::collections::HashSet::new();
        for tool in tools {
            assert!(names.insert(tool["name"].as_str().unwrap().to_owned()));
            assert_eq!(tool["inputSchema"]["additionalProperties"], false);
        }
        assert!(
            validate_arguments("designer.adjust_view", &json!({"cameraTarget":[1,2]})).is_err()
        );
        assert!(validate_arguments("file.create", &json!({})).is_err());
    }
}
