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
    if !["PANEL", "TEXT_VIEW", "BUTTON"].contains(&kind) {
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
