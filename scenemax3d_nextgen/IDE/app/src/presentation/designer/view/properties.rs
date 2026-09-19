//! Typed native controls for the Java-compatible widget property schema.
use super::*;
use scenemax_ide_ui::{number, property};
use serde_json::{Value, json};
#[derive(Clone)]
pub(crate) enum FieldKind {
    Text,
    Number,
    Integer,
    Bool,
    Headers,
    Rows,
    Widths,
}
pub(crate) fn parse_field(kind: &FieldKind, value: &str) -> Result<Value, String> {
    Ok(match kind {
        FieldKind::Text => json!(value),
        FieldKind::Number => {
            let n = value.parse::<f64>().map_err(|_| "Enter a number")?;
            if !n.is_finite() {
                return Err("Enter a finite number".into());
            }
            json!(n)
        }
        FieldKind::Integer => json!(value.parse::<i64>().map_err(|_| "Enter a whole number")?),
        FieldKind::Bool => json!(value == "true"),
        FieldKind::Headers => json!(if value.is_empty() {
            vec![]
        } else {
            value.split('|').map(str::trim).collect::<Vec<_>>()
        }),
        FieldKind::Rows => json!(
            value
                .lines()
                .map(|line| line.split('|').map(str::trim).collect::<Vec<_>>())
                .collect::<Vec<_>>()
        ),
        FieldKind::Widths => json!(if value.is_empty() {
            vec![]
        } else {
            value
                .split('|')
                .map(|s| {
                    s.trim()
                        .parse::<f64>()
                        .map_err(|_| "Enter numeric column widths")
                })
                .collect::<Result<Vec<_>, _>>()?
        }),
    })
}
pub(super) fn inspector_fields(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    scene: &ScenePreview,
    w: &PreviewWidget,
) {
    let raw = &w.properties;
    let mut field = |key: &str, caption: &str, default_value: Value, choices: &[&str]| {
        let value = raw
            .get(key)
            .filter(|v| !v.is_null())
            .cloned()
            .unwrap_or(default_value);
        let kind = if value.is_boolean() {
            FieldKind::Bool
        } else if [
            "spriteFrame",
            "zOrder",
            "listColumnCount",
            "listSelectedRowIndex",
        ]
        .contains(&key)
        {
            FieldKind::Integer
        } else if value.is_number() {
            FieldKind::Number
        } else if key == "listHeaders" {
            FieldKind::Headers
        } else if key == "listRows" {
            FieldKind::Rows
        } else if key == "listColumnWidths" {
            FieldKind::Widths
        } else {
            FieldKind::Text
        };
        let options = if key.ends_with("FontName") || key == "fontName" {
            std::iter::once(String::new())
                .chain(scene.fonts.iter().cloned())
                .collect()
        } else if key == "spriteName" {
            std::iter::once(String::new())
                .chain(scene.sprites.iter().cloned())
                .collect()
        } else {
            choices.iter().map(|s| s.to_string()).collect()
        };
        control(commands, parent, host, key, caption, &value, kind, options);
    };
    field("name", "Name", json!(w.name), &[]);
    field("visible", "Visible", json!(true), &[]);
    field(
        "multiplayer",
        "Sync text (runtime scripts)",
        json!(false),
        &[],
    );
    field("zOrder", "Z order", json!(0), &[]);
    field(
        "widthMode",
        "Width mode",
        json!("WRAP_CONTENT"),
        &["FIXED", "WRAP_CONTENT", "MATCH_CONSTRAINT"],
    );
    field("width", "Width", json!(100.), &[]);
    field(
        "heightMode",
        "Height mode",
        json!("WRAP_CONTENT"),
        &["FIXED", "WRAP_CONTENT", "MATCH_CONSTRAINT"],
    );
    field("height", "Height", json!(50.), &[]);
    field("centerHorizontal", "Center horizontally", json!(false), &[]);
    field("centerVertical", "Center vertically", json!(false), &[]);
    for (key, title) in [
        ("horizontalBias", "Horizontal bias"),
        ("verticalBias", "Vertical bias"),
    ] {
        field(key, title, json!(0.5), &[]);
    }
    for (key, title) in [
        ("marginLeft", "Left margin"),
        ("marginRight", "Right margin"),
        ("marginTop", "Top margin"),
        ("marginBottom", "Bottom margin"),
        ("paddingLeft", "Left padding"),
        ("paddingRight", "Right padding"),
        ("paddingTop", "Top padding"),
        ("paddingBottom", "Bottom padding"),
    ] {
        field(key, title, json!(0.), &[]);
    }
    field("backgroundColor", "Background color", json!(w.color), &[]);
    if ["TEXT_VIEW", "EDIT_TEXT", "BUTTON", "LIST_VIEW"].contains(&w.kind.as_str()) {
        field("fontName", "Font asset", json!(""), &[]);
        field("fontSize", "Font size", json!(24.), &[]);
        field(
            "textAlignment",
            "Alignment",
            json!("left"),
            &["left", "center", "right"],
        );
    }
    if ["TEXT_VIEW", "EDIT_TEXT"].contains(&w.kind.as_str()) {
        field("text", "Text", json!(""), &[]);
        field("textColor", "Text color", json!("#FFFFFF"), &[]);
    }
    if w.kind == "EDIT_TEXT" {
        field("editTextPlaceholder", "Placeholder", json!(""), &[]);
        field("editTextMultiline", "Multiline", json!(false), &[]);
    }
    if w.kind == "BUTTON" {
        field("buttonText", "Button text", json!(""), &[]);
        field("buttonColor", "Button color", json!("#404040"), &[]);
        field(
            "buttonTextColor",
            "Button text color",
            json!("#FFFFFF"),
            &[],
        );
    }
    if w.kind == "IMAGE" {
        field("spriteName", "Sprite asset", json!(""), &[]);
        field("imagePath", "Image path", json!(""), &[]);
        field("spriteFrame", "Sprite frame", json!(0), &[]);
        field(
            "imageScaleMode",
            "Image scale mode",
            json!("FIT_CENTER"),
            &["FIT_CENTER", "CENTER_CROP", "STRETCH"],
        );
    }
    if w.kind == "LIST_VIEW" {
        field("listColumnCount", "Columns", json!(1), &[]);
        field(
            "listViewStyle",
            "Style",
            json!("classic"),
            &["classic", "modern"],
        );
        field("listViewTransparency", "Transparency", json!(0.), &[]);
        field("listHeaderFontName", "Header font", json!(""), &[]);
        field("listRowFontName", "Row font", json!(""), &[]);
        field("listHeaderFontSize", "Header font size", json!(24.), &[]);
        field("listRowFontSize", "Row font size", json!(18.), &[]);
        field("listSelectedRowIndex", "Selected row", json!(-1), &[]);
        field(
            "listColumnWidths",
            "Column widths (separate with |)",
            json!([]),
            &[],
        );
        field("listHeaders", "Headers (separate with |)", json!([]), &[]);
        field(
            "listRows",
            "Rows (one per line; cells separated by |)",
            json!([]),
            &[],
        );
        field("textColor", "Text color", json!("#FFFFFF"), &[]);
    }
    commands.spawn((label("Constraints", 13.), ChildOf(parent)));
    let siblings = w.pointer.rsplit_once('/').map(|(p, _)| p).unwrap_or("");
    let mut targets = vec![String::new(), "parent".into()];
    targets.extend(
        scene
            .widgets
            .iter()
            .filter(|p| {
                p.pointer != w.pointer
                    && p.pointer.rsplit_once('/').map(|(p, _)| p) == Some(siblings)
            })
            .map(|p| p.name.clone()),
    );
    for side in ["LEFT", "RIGHT", "TOP", "BOTTOM"] {
        let c = w
            .definition
            .constraints
            .iter()
            .find(|c| c.side.eq_ignore_ascii_case(side));
        control(
            commands,
            parent,
            host,
            &format!("constraints/{side}/targetName"),
            &format!("{side} target"),
            &json!(c.map(|c| c.target_name.as_str()).unwrap_or("")),
            FieldKind::Text,
            targets.clone(),
        );
        control(
            commands,
            parent,
            host,
            &format!("constraints/{side}/targetSide"),
            "Target edge",
            &json!(c.map(|c| c.target_side.as_str()).unwrap_or(side)),
            FieldKind::Text,
            ["LEFT", "RIGHT", "TOP", "BOTTOM"]
                .iter()
                .map(|s| s.to_string())
                .collect(),
        );
        control(
            commands,
            parent,
            host,
            &format!("constraints/{side}/margin"),
            "Constraint margin",
            &json!(c.map(|c| c.margin).unwrap_or(0.)),
            FieldKind::Number,
            vec![],
        );
    }
}
#[allow(clippy::too_many_arguments)]
fn control(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    key: &str,
    title: &str,
    value: &Value,
    kind: FieldKind,
    choices: Vec<String>,
) {
    let cells = |v: &Value| {
        v.as_array()
            .map(|a| {
                a.iter()
                    .map(|v| {
                        v.as_str()
                            .map(str::to_owned)
                            .unwrap_or_else(|| v.to_string())
                    })
                    .collect::<Vec<_>>()
                    .join(" | ")
            })
            .unwrap_or_default()
    };
    let text = match kind {
        FieldKind::Headers | FieldKind::Widths => cells(value),
        FieldKind::Rows => value
            .as_array()
            .map(|a| a.iter().map(cells).collect::<Vec<_>>().join("\n"))
            .unwrap_or_default(),
        _ => value
            .as_str()
            .map(str::to_owned)
            .unwrap_or_else(|| value.to_string()),
    };
    let marker = Property {
        host,
        key: key.into(),
        kind: kind.clone(),
        observed: text.clone(),
    };
    if matches!(kind, FieldKind::Bool) {
        property::checkbox(
            commands,
            parent,
            marker,
            title,
            value.as_bool().unwrap_or(false),
        );
        return;
    }
    commands.spawn((label(title, 12.), ChildOf(parent)));
    if !choices.is_empty() {
        property::dropdown(commands, parent, marker, &text, &choices);
    } else if matches!(kind, FieldKind::Number | FieldKind::Integer) {
        let bias = key.ends_with("Bias");
        number::input(
            commands,
            parent,
            marker,
            &text,
            if key.contains("margin") { -100. } else { 0. },
            if bias { 1. } else { 1000. },
            if bias { 0.01 } else { 1. },
        );
    } else {
        let entity = property::input(commands, parent, marker, &text);
        if matches!(
            kind,
            FieldKind::Headers | FieldKind::Rows | FieldKind::Widths
        ) || key == "text"
            || key == "buttonText"
        {
            commands.entity(entity).insert((
                EditableText {
                    allow_newlines: true,
                    max_characters: Some(10000),
                    ..EditableText::new(text)
                },
                Node {
                    width: percent(100.),
                    height: px(68.),
                    min_height: px(68.),
                    flex_shrink: 0.,
                    overflow: Overflow::scroll_y(),
                    ..default()
                },
            ));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn list_controls_use_java_cells_and_reject_invalid_numbers() {
        assert_eq!(
            parse_field(&FieldKind::Headers, "Name | Score").unwrap(),
            json!(["Name", "Score"])
        );
        assert_eq!(
            parse_field(&FieldKind::Rows, "A | 10\nB | 20").unwrap(),
            json!([["A", "10"], ["B", "20"]])
        );
        assert_eq!(
            parse_field(&FieldKind::Widths, "120 | 80").unwrap(),
            json!([120., 80.])
        );
        assert!(parse_field(&FieldKind::Number, "NaN").is_err());
        assert!(parse_field(&FieldKind::Integer, "1.5").is_err());
    }
}
