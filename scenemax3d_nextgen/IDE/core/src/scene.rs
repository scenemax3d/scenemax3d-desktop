//! UI document mutations preserve fields that this designer does not yet expose.
use serde_json::{Value, json};

/// Parse a bounded UI document without discarding unknown properties.
pub fn parse(source: &str) -> Result<Value, String> {
    if source.len() > 1024 * 1024 {
        return Err("UI documents are limited to 1 MiB in this designer".into());
    }
    let value: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    if !value.is_object() || !value["layers"].is_array() {
        return Err("Expected a UI document with layers".into());
    }
    let mut pending = vec![(&value, 0)];
    let mut count = 0;
    while let Some((v, depth)) = pending.pop() {
        count += 1;
        if depth > 24 || count > 16000 {
            return Err("UI document exceeds designer structure limits".into());
        }
        match v {
            Value::Array(a) => pending.extend(a.iter().map(|v| (v, depth + 1))),
            Value::Object(o) => pending.extend(o.values().map(|v| (v, depth + 1))),
            _ => {}
        }
    }
    Ok(value)
}

/// Inspector values, applied together as one document undo transaction.
pub struct Properties {
    /// Text.
    pub text: String,
    /// Width.
    pub width: f64,
    /// Height.
    pub height: f64,
    /// Font size.
    pub font_size: f64,
    /// Color.
    pub color: String,
}

/// Add a supported widget to the selected panel or selected widget's layer.
pub fn add(source: &str, selected: Option<&str>, kind: &str) -> Result<(String, String), String> {
    if ![
        "PANEL",
        "TEXT_VIEW",
        "BUTTON",
        "IMAGE",
        "EDIT_TEXT",
        "LIST_VIEW",
    ]
    .contains(&kind)
    {
        return Err("Unsupported widget type".into());
    }
    let mut value = parse(source)?;
    let parent = selected.filter(|p| value.pointer(p).is_some_and(|w| w["type"] == "PANEL"));
    let target = if let Some(parent) = parent {
        let widget = value.pointer_mut(parent).ok_or("Missing panel")?;
        if widget.get("children").is_none() {
            widget["children"] = json!([]);
        }
        format!("{parent}/children")
    } else {
        let layer = selected.and_then(|p| p.split('/').nth(2)).unwrap_or("0");
        let layer_pointer = format!("/layers/{layer}");
        let layer = value
            .pointer_mut(&layer_pointer)
            .ok_or("Create a layer in the source document first")?;
        if layer.get("widgets").is_none() {
            layer["widgets"] = json!([]);
        }
        format!("{layer_pointer}/widgets")
    };
    let stem = match kind {
        "PANEL" => "panel",
        "BUTTON" => "button",
        _ => "text",
    };
    let mut names = std::collections::BTreeSet::new();
    let mut pending = vec![&value];
    while let Some(v) = pending.pop() {
        match v {
            Value::Object(o) => {
                if let Some(name) = v["name"].as_str() {
                    names.insert(name.to_owned());
                }
                pending.extend(o.values());
            }
            Value::Array(a) => pending.extend(a),
            _ => {}
        }
    }
    let name = (1..=1000)
        .map(|n| format!("{stem}{n}"))
        .find(|n| !names.contains(n))
        .ok_or("Widget name limit reached")?;
    let widgets = value
        .pointer_mut(&target)
        .and_then(Value::as_array_mut)
        .ok_or("Invalid widget container")?;
    let pointer = format!("{target}/{}", widgets.len());
    widgets.push(json!({"name":name,"type":kind,"widthMode":"FIXED","heightMode":"FIXED","width":240,"height":if kind=="PANEL"{160}else{56},"fontSize":24,"text":if kind=="TEXT_VIEW"{"New text"}else{""},"buttonText":"Button","backgroundColor":"#293447","buttonColor":"#416AC9","textColor":"#FFFFFF","buttonTextColor":"#FFFFFF","constraints":[{"side":"LEFT","targetName":"parent","targetSide":"LEFT","margin":24},{"side":"TOP","targetName":"parent","targetSide":"TOP","margin":24}],"children":[]}));
    Ok((
        serde_json::to_string_pretty(&value).map_err(|e| e.to_string())?,
        pointer,
    ))
}

/// Remove a selected widget and its descendants as one source change.
pub fn remove(source: &str, pointer: &str) -> Result<String, String> {
    let mut value = parse(source)?;
    if !value
        .pointer(pointer)
        .is_some_and(|w| w["type"].is_string())
    {
        return Err("Select a widget".into());
    }
    let (parent, index) = pointer.rsplit_once('/').ok_or("Invalid widget path")?;
    let index: usize = index.parse().map_err(|_| "Invalid widget index")?;
    let widgets = value
        .pointer_mut(parent)
        .and_then(Value::as_array_mut)
        .ok_or("Invalid widget container")?;
    if index >= widgets.len() {
        return Err("Widget no longer exists".into());
    }
    widgets.remove(index);
    serde_json::to_string_pretty(&value).map_err(|e| e.to_string())
}
/// Change supported properties on a widget identified by its JSON pointer.
pub fn apply(source: &str, pointer: &str, properties: Properties) -> Result<String, String> {
    let mut value = parse(source)?;
    for n in [properties.width, properties.height, properties.font_size] {
        if !n.is_finite() || !(1.0..=16384.0).contains(&n) {
            return Err("Dimensions and font size must be between 1 and 16384".into());
        }
    }
    let color = properties.color.trim();
    if !color.starts_with('#')
        || ![7, 9].contains(&color.len())
        || !color[1..].bytes().all(|c| c.is_ascii_hexdigit())
    {
        return Err("Color must be #RRGGBB or #RRGGBBAA".into());
    }
    let widget = value
        .pointer_mut(pointer)
        .filter(|v| v["type"].is_string())
        .ok_or("Select a widget")?;
    let button = widget["type"] == "BUTTON";
    widget[if button { "buttonText" } else { "text" }] = json!(properties.text);
    widget["width"] = json!(properties.width);
    widget["height"] = json!(properties.height);
    widget["fontSize"] = json!(properties.font_size);
    widget[if button {
        "buttonColor"
    } else {
        "backgroundColor"
    }] = json!(color);
    serde_json::to_string_pretty(&value).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn adding_and_removing_nested_widgets_preserves_other_content() {
        let source = r#"{"name":"example","layers":[{"name":"overlay","widgets":[]}]}"#;
        let (panel, pointer) = add(source, None, "PANEL").unwrap();
        let (button, child) = add(&panel, Some(&pointer), "BUTTON").unwrap();
        assert!(child.ends_with("/children/0"));
        let value = parse(&button).unwrap();
        assert_eq!(value.pointer(&child).unwrap()["name"], "button1");
        let removed = remove(&button, &child).unwrap();
        assert_eq!(parse(&removed).unwrap(), parse(&panel).unwrap());
        assert!(add(source, None, "UNSUPPORTED").is_err());
    }
    #[test]
    fn property_edits_preserve_constraints_children_and_future_fields() {
        let source = r##"{"name":"example","future":42,"layers":[{"widgets":[{"name":"caption","type":"BUTTON","constraints":[{"side":"LEFT"}],"children":[],"futureStyle":true}]}]}"##;
        let result = apply(
            source,
            "/layers/0/widgets/0",
            Properties {
                text: "שלום".into(),
                width: 240.,
                height: 60.,
                font_size: 24.,
                color: "#335577".into(),
            },
        )
        .unwrap();
        let v = parse(&result).unwrap();
        assert_eq!(v["future"], 42);
        assert_eq!(v["layers"][0]["widgets"][0]["futureStyle"], true);
        assert_eq!(
            v["layers"][0]["widgets"][0]["constraints"][0]["side"],
            "LEFT"
        );
        assert_eq!(v["layers"][0]["widgets"][0]["buttonText"], "שלום");
        assert!(
            apply(
                source,
                "/missing",
                Properties {
                    text: String::new(),
                    width: f64::NAN,
                    height: 1.,
                    font_size: 1.,
                    color: "red".into()
                }
            )
            .is_err()
        );
    }
}

/// Apply typed inspector values without discarding imported extensions.
pub fn patch(source: &str, pointer: &str, fields: Vec<(String, Value)>) -> Result<String, String> {
    let mut root = parse(source)?;
    let old = root
        .pointer(pointer)
        .and_then(|w| w["name"].as_str())
        .ok_or("Select a widget")?
        .to_owned();
    let name = fields
        .iter()
        .find(|(k, _)| k == "name")
        .and_then(|(_, v)| v.as_str())
        .unwrap_or(&old)
        .trim()
        .to_owned();
    if name.is_empty() {
        return Err("Name cannot be empty".into());
    }
    if name != old {
        let mut pending = vec![&root];
        while let Some(v) = pending.pop() {
            if v.get("type").is_some() && v["name"] == name {
                return Err("A widget with this name already exists".into());
            }
            match v {
                Value::Array(a) => pending.extend(a),
                Value::Object(o) => pending.extend(o.values()),
                _ => {}
            }
        }
    }
    let widget = root.pointer_mut(pointer).ok_or("Missing widget")?;
    for (key, value) in fields {
        if let Some(n) = value.as_f64() {
            if !n.is_finite() || n.abs() > 100000. {
                return Err(format!("Invalid {key}"));
            }
            if [
                "width",
                "height",
                "fontSize",
                "listHeaderFontSize",
                "listRowFontSize",
            ]
            .contains(&key.as_str())
                && n <= 0.
            {
                return Err(format!("{key} must be positive"));
            }
            if ["horizontalBias", "verticalBias"].contains(&key.as_str())
                && !(0. ..=1.).contains(&n)
            {
                return Err(format!("{key} must be between 0 and 1"));
            }
        }
        if [
            "spriteFrame",
            "paddingLeft",
            "paddingRight",
            "paddingTop",
            "paddingBottom",
        ]
        .contains(&key.as_str())
            && value.as_f64().is_some_and(|n| n < 0.)
        {
            return Err(format!("{key} cannot be negative"));
        }
        if key == "listColumnWidths"
            && !value.as_array().is_some_and(|a| {
                a.iter()
                    .all(|v| v.as_f64().is_some_and(|n| n.is_finite() && n > 0.))
            })
        {
            return Err("Column widths must be positive numbers".into());
        }
        if key.to_ascii_lowercase().ends_with("color") {
            let s = value
                .as_str()
                .ok_or("Invalid color")?
                .trim_start_matches('#');
            if ![6, 8].contains(&s.len()) || !s.bytes().all(|b| b.is_ascii_hexdigit()) {
                return Err(format!("{key}: use #RRGGBB or #RRGGBBAA"));
            }
        }
        if let Some(rest) = key.strip_prefix("constraints/") {
            let (side, field) = rest.split_once('/').ok_or("Invalid constraint")?;
            if !["LEFT", "RIGHT", "TOP", "BOTTOM"].contains(&side)
                || !["targetName", "targetSide", "margin"].contains(&field)
            {
                return Err("Invalid constraint field".into());
            }
            if widget.get("constraints").is_none() {
                widget["constraints"] = json!([]);
            }
            let constraints = widget["constraints"]
                .as_array_mut()
                .ok_or("Invalid constraints")?;
            let index = constraints
                .iter()
                .position(|c| c["side"] == side)
                .unwrap_or_else(|| {
                    constraints
                        .push(json!({"side":side,"targetName":"","targetSide":side,"margin":0}));
                    constraints.len() - 1
                });
            constraints[index][field] = value;
        } else {
            if ["children", "type", "id"].contains(&key.as_str()) {
                return Err("Protected structural property".into());
            }
            widget[&key] = value;
        }
    }
    widget["name"] = json!(name);
    if let Some(a) = widget["constraints"].as_array_mut() {
        a.retain(|c| c["targetName"].as_str().is_some_and(|s| !s.is_empty()));
    }
    fn rename(v: &mut Value, old: &str, new: &str) {
        match v {
            Value::Object(o) => {
                if o.get("targetName").and_then(Value::as_str) == Some(old) {
                    o.insert("targetName".into(), json!(new));
                }
                for v in o.values_mut() {
                    rename(v, old, new);
                }
            }
            Value::Array(a) => {
                for v in a {
                    rename(v, old, new);
                }
            }
            _ => {}
        }
    }
    if name != old {
        rename(&mut root, &old, &name);
    }
    let output = serde_json::to_string_pretty(&root).map_err(|e| e.to_string())?;
    parse(&output)?;
    Ok(output)
}

#[cfg(test)]
mod patch_tests {
    use super::*;
    #[test]
    fn edits_constraints_and_rename_preserve_imported_data() {
        let source = r#"{"layers":[{"widgets":[{"name":"a","type":"IMAGE","custom":42,"spriteFrame":0},{"name":"b","type":"TEXT_VIEW","constraints":[{"side":"LEFT","targetName":"a","targetSide":"RIGHT","extra":true}]}]}]}"#;
        let edited = patch(
            source,
            "/layers/0/widgets/0",
            vec![
                ("name".into(), json!("portrait")),
                ("spriteFrame".into(), json!(3)),
                ("constraints/LEFT/targetName".into(), json!("parent")),
                ("constraints/LEFT/targetSide".into(), json!("LEFT")),
                ("constraints/LEFT/margin".into(), json!(12.)),
            ],
        )
        .unwrap();
        let v = parse(&edited).unwrap();
        assert_eq!(v["layers"][0]["widgets"][0]["custom"], 42);
        assert_eq!(
            v["layers"][0]["widgets"][0]["constraints"][0]["margin"],
            12.
        );
        assert_eq!(
            v["layers"][0]["widgets"][1]["constraints"][0]["targetName"],
            "portrait"
        );
        assert_eq!(
            v["layers"][0]["widgets"][1]["constraints"][0]["extra"],
            true
        );
        assert!(
            patch(
                source,
                "/layers/0/widgets/0",
                vec![("name".into(), json!("b"))]
            )
            .is_err()
        );
        assert!(
            patch(
                source,
                "/layers/0/widgets/0",
                vec![("horizontalBias".into(), json!(2.))]
            )
            .is_err()
        );
    }
}
