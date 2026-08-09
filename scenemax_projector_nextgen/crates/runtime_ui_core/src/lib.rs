use std::collections::HashMap;

use serde::Deserialize;

#[derive(Debug, Clone, PartialEq)]
pub struct MessageFrame {
    pub visible_text: String,
    pub alpha: f32,
    pub scale: f32,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SceneMaxUiDocument {
    pub name: String,
    #[serde(default = "default_ui_canvas_width")]
    pub canvas_width: f32,
    #[serde(default = "default_ui_canvas_height")]
    pub canvas_height: f32,
    #[serde(default)]
    pub layers: Vec<SceneMaxUiLayerDef>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SceneMaxUiLayerDef {
    pub name: String,
    #[serde(default = "default_true")]
    pub visible: bool,
    #[serde(default)]
    pub z_order: i32,
    #[serde(default)]
    pub widgets: Vec<SceneMaxUiWidgetDef>,
}

#[allow(dead_code)]
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SceneMaxUiWidgetDef {
    pub name: String,
    #[serde(rename = "type")]
    pub widget_type: String,
    #[serde(default = "default_ui_size_mode")]
    pub width_mode: String,
    #[serde(default = "default_ui_size_mode")]
    pub height_mode: String,
    #[serde(default = "default_ui_width")]
    pub width: f32,
    #[serde(default = "default_ui_height")]
    pub height: f32,
    #[serde(default)]
    pub constraints: Vec<SceneMaxUiConstraint>,
    #[serde(default = "default_half")]
    pub horizontal_bias: f32,
    #[serde(default = "default_half")]
    pub vertical_bias: f32,
    #[serde(default)]
    pub padding_left: f32,
    #[serde(default)]
    pub padding_right: f32,
    #[serde(default)]
    pub padding_top: f32,
    #[serde(default)]
    pub padding_bottom: f32,
    #[serde(default)]
    pub margin_left: f32,
    #[serde(default)]
    pub margin_right: f32,
    #[serde(default)]
    pub margin_top: f32,
    #[serde(default)]
    pub margin_bottom: f32,
    #[serde(default = "default_true")]
    pub visible: bool,
    #[serde(default)]
    pub center_horizontal: bool,
    #[serde(default)]
    pub center_vertical: bool,
    #[serde(default)]
    pub z_order: i32,
    #[serde(default = "default_panel_color")]
    pub background_color: String,
    #[serde(default)]
    pub text: String,
    #[serde(default = "default_text_color")]
    pub text_color: String,
    #[serde(default)]
    pub font_name: Option<String>,
    #[serde(default = "default_font_size")]
    pub font_size: f32,
    #[serde(default = "default_text_alignment")]
    pub text_alignment: String,
    #[serde(default)]
    pub button_text: String,
    #[serde(default = "default_button_color")]
    pub button_color: String,
    #[serde(default = "default_text_color")]
    pub button_text_color: String,
    #[serde(default)]
    pub image_path: Option<String>,
    #[serde(default)]
    pub sprite_name: Option<String>,
    #[serde(default)]
    pub sprite_frame: usize,
    #[serde(default = "default_image_scale_mode")]
    pub image_scale_mode: String,
    #[serde(default)]
    pub list_headers: Vec<String>,
    #[serde(default)]
    pub list_rows: Vec<Vec<String>>,
    #[serde(default)]
    pub list_header_font_name: Option<String>,
    #[serde(default)]
    pub list_row_font_name: Option<String>,
    #[serde(default = "default_font_size")]
    pub list_header_font_size: f32,
    #[serde(default = "default_list_row_font_size")]
    pub list_row_font_size: f32,
    #[serde(default)]
    pub children: Vec<SceneMaxUiWidgetDef>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SceneMaxUiConstraint {
    pub side: String,
    pub target_name: String,
    pub target_side: String,
    #[serde(default)]
    pub margin: f32,
}

#[derive(Debug, Clone)]
pub struct SceneMaxSpriteAsset {
    pub path: String,
    pub rows: usize,
    pub cols: usize,
}

#[derive(Debug, Clone, Copy, Default, PartialEq)]
pub struct UiLayoutRect {
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
}

pub fn document_scale(doc: &SceneMaxUiDocument, window_width: u32, window_height: u32) -> f32 {
    let width_scale = window_width as f32 / doc.canvas_width.max(1.0);
    let height_scale = window_height as f32 / doc.canvas_height.max(1.0);
    width_scale.min(height_scale).max(0.1)
}

pub fn scaled_font_size(font_size: f32, ui_scale: f32) -> f32 {
    (font_size * ui_scale).max(1.0)
}

pub fn sorted_widgets(widgets: &[SceneMaxUiWidgetDef]) -> Vec<&SceneMaxUiWidgetDef> {
    let mut sorted = widgets.iter().collect::<Vec<_>>();
    sorted.sort_by_key(|widget| widget.z_order);
    sorted
}

pub fn percent(value: f32, total: f32) -> f32 {
    if total.abs() <= f32::EPSILON {
        0.0
    } else {
        value / total * 100.0
    }
}

pub fn solve_widget_layout(
    widgets: &[SceneMaxUiWidgetDef],
    parent_width: f32,
    parent_height: f32,
) -> HashMap<String, UiLayoutRect> {
    let mut results = widgets
        .iter()
        .map(|widget| {
            (
                widget.name.clone(),
                UiLayoutRect {
                    width: preferred_widget_size(widget, true),
                    height: preferred_widget_size(widget, false),
                    ..Default::default()
                },
            )
        })
        .collect::<HashMap<_, _>>();
    for _ in 0..widgets.len().max(1) {
        for widget in widgets {
            let rect = resolve_widget_rect(widget, &results, parent_width, parent_height);
            results.insert(widget.name.clone(), rect);
        }
    }
    results
}

pub fn target_key(ui_name: &str, layer: &str, widget_path: &[String]) -> String {
    if widget_path.is_empty() {
        format!("{ui_name}.{layer}")
    } else {
        format!("{ui_name}.{layer}.{}", widget_path.join("."))
    }
}

pub fn list_view_text(widget: &SceneMaxUiWidgetDef) -> String {
    let mut lines = Vec::new();
    if !widget.list_headers.is_empty() {
        lines.push(widget.list_headers.join("  "));
    }
    for row in &widget.list_rows {
        lines.push(row.join("  "));
    }
    lines.join("\n")
}

fn default_ui_canvas_width() -> f32 {
    1920.0
}

fn default_ui_canvas_height() -> f32 {
    1080.0
}

fn default_ui_width() -> f32 {
    100.0
}

fn default_ui_height() -> f32 {
    50.0
}

fn default_ui_size_mode() -> String {
    "WRAP_CONTENT".to_owned()
}

fn default_panel_color() -> String {
    "#33333300".to_owned()
}

fn default_text_color() -> String {
    "#FFFFFFFF".to_owned()
}

fn default_button_color() -> String {
    "#4488FFFF".to_owned()
}

fn default_text_alignment() -> String {
    "left".to_owned()
}

fn default_image_scale_mode() -> String {
    "fit".to_owned()
}

fn default_font_size() -> f32 {
    16.0
}

fn default_list_row_font_size() -> f32 {
    14.0
}

fn default_half() -> f32 {
    0.5
}

fn default_true() -> bool {
    true
}

fn resolve_widget_rect(
    widget: &SceneMaxUiWidgetDef,
    results: &HashMap<String, UiLayoutRect>,
    parent_width: f32,
    parent_height: f32,
) -> UiLayoutRect {
    let (x, width) = resolve_axis(widget, results, parent_width, true);
    let (y, height) = resolve_axis(widget, results, parent_height, false);
    UiLayoutRect {
        x,
        y,
        width,
        height,
    }
}

fn resolve_axis(
    widget: &SceneMaxUiWidgetDef,
    results: &HashMap<String, UiLayoutRect>,
    parent_size: f32,
    horizontal: bool,
) -> (f32, f32) {
    let start_side = if horizontal { "LEFT" } else { "TOP" };
    let end_side = if horizontal { "RIGHT" } else { "BOTTOM" };
    let mut start = widget
        .constraints
        .iter()
        .find(|constraint| constraint.side.eq_ignore_ascii_case(start_side));
    let mut end = widget
        .constraints
        .iter()
        .find(|constraint| constraint.side.eq_ignore_ascii_case(end_side));

    let centered = if horizontal {
        widget.center_horizontal
    } else {
        widget.center_vertical
    };
    let synthetic_start;
    let synthetic_end;
    if centered && start.is_none() && end.is_none() {
        synthetic_start = SceneMaxUiConstraint {
            side: start_side.to_owned(),
            target_name: "parent".to_owned(),
            target_side: start_side.to_owned(),
            margin: 0.0,
        };
        synthetic_end = SceneMaxUiConstraint {
            side: end_side.to_owned(),
            target_name: "parent".to_owned(),
            target_side: end_side.to_owned(),
            margin: 0.0,
        };
        start = Some(&synthetic_start);
        end = Some(&synthetic_end);
    }

    let size_mode = if horizontal {
        &widget.width_mode
    } else {
        &widget.height_mode
    };
    let fixed_size = if horizontal {
        widget.width
    } else {
        widget.height
    };
    let bias = if horizontal {
        widget.horizontal_bias
    } else {
        widget.vertical_bias
    };
    let widget_start_margin = if horizontal {
        widget.margin_left
    } else {
        widget.margin_top
    };
    let widget_end_margin = if horizontal {
        widget.margin_right
    } else {
        widget.margin_bottom
    };
    let start_anchor = start
        .map(|constraint| resolve_anchor(constraint, results, parent_size, horizontal))
        .unwrap_or(0.0);
    let end_anchor = end
        .map(|constraint| resolve_anchor(constraint, results, parent_size, horizontal))
        .unwrap_or(0.0);
    let start_margin =
        start.map(|constraint| constraint.margin).unwrap_or(0.0) + widget_start_margin;
    let end_margin = end.map(|constraint| constraint.margin).unwrap_or(0.0) + widget_end_margin;

    if let (Some(_), Some(_)) = (start, end) {
        let available = end_anchor - start_anchor - start_margin - end_margin;
        let size = if size_mode.eq_ignore_ascii_case("MATCH_CONSTRAINT") {
            available.max(0.0)
        } else if size_mode.eq_ignore_ascii_case("FIXED") {
            fixed_size
        } else {
            preferred_widget_size(widget, horizontal)
        };
        return (
            start_anchor + start_margin + (available - size) * bias,
            size,
        );
    }
    if start.is_some() {
        let size = if size_mode.eq_ignore_ascii_case("FIXED") {
            fixed_size
        } else {
            preferred_widget_size(widget, horizontal)
        };
        return (start_anchor + start_margin, size);
    }
    if end.is_some() {
        let size = if size_mode.eq_ignore_ascii_case("FIXED") {
            fixed_size
        } else {
            preferred_widget_size(widget, horizontal)
        };
        return (end_anchor - end_margin - size, size);
    }

    let size = if size_mode.eq_ignore_ascii_case("FIXED") {
        fixed_size
    } else {
        preferred_widget_size(widget, horizontal)
    };
    (widget_start_margin, size)
}

fn resolve_anchor(
    constraint: &SceneMaxUiConstraint,
    results: &HashMap<String, UiLayoutRect>,
    parent_size: f32,
    horizontal: bool,
) -> f32 {
    if constraint.target_name.eq_ignore_ascii_case("parent") {
        return match constraint.target_side.to_ascii_uppercase().as_str() {
            "RIGHT" | "BOTTOM" => parent_size,
            _ => 0.0,
        };
    }
    let Some(rect) = results.get(&constraint.target_name) else {
        return 0.0;
    };
    match constraint.target_side.to_ascii_uppercase().as_str() {
        "RIGHT" => rect.x + rect.width,
        "BOTTOM" => rect.y + rect.height,
        "LEFT" if horizontal => rect.x,
        "TOP" if !horizontal => rect.y,
        "LEFT" | "TOP" => {
            if horizontal {
                rect.x
            } else {
                rect.y
            }
        }
        _ => 0.0,
    }
}

fn preferred_widget_size(widget: &SceneMaxUiWidgetDef, horizontal: bool) -> f32 {
    let mode = if horizontal {
        &widget.width_mode
    } else {
        &widget.height_mode
    };
    if mode.eq_ignore_ascii_case("FIXED") || mode.eq_ignore_ascii_case("MATCH_CONSTRAINT") {
        return if horizontal {
            widget.width
        } else {
            widget.height
        };
    }
    match widget.widget_type.as_str() {
        "TEXT_VIEW" | "EDIT_TEXT" => {
            if horizontal {
                (widget.text.len() as f32 * widget.font_size * 0.6).max(50.0)
            } else {
                widget.font_size * 1.4
            }
        }
        "BUTTON" => {
            if horizontal {
                (widget.button_text.len() as f32 * widget.font_size * 0.6 + 24.0).max(80.0)
            } else {
                widget.font_size * 1.4 + 16.0
            }
        }
        _ => {
            if horizontal {
                widget.width
            } else {
                widget.height
            }
        }
    }
}

pub fn parse_effects(effects: &str) -> Vec<String> {
    effects
        .split(['|', ','])
        .map(normalize_effect)
        .filter(|effect| !effect.is_empty())
        .collect()
}

pub fn has_effect(effects: &str, effect_name: &str) -> bool {
    let effect_name = normalize_effect(effect_name);
    parse_effects(effects)
        .iter()
        .any(|effect| effect == &effect_name)
}

pub fn should_animate(effect_names: &[String]) -> bool {
    effect_names
        .iter()
        .any(|effect| is_supported_effect(effect))
        && !effect_names_contain(effect_names, "none")
}

pub fn evaluate_message_frame(
    full_text: &str,
    effect_names: &[String],
    progress: f32,
) -> MessageFrame {
    let progress = progress.clamp(0.0, 1.0);
    if effect_names.is_empty() || effect_names_contain(effect_names, "none") {
        return MessageFrame {
            visible_text: full_text.to_owned(),
            alpha: 1.0,
            scale: 1.0,
        };
    }

    let visible_text = if effect_names_contain(effect_names, "typewriter")
        || effect_names_contain(effect_names, "typewriter_zoom_in")
    {
        typewriter_visible_text(full_text, progress)
    } else if effect_names_contain(effect_names, "word_reveal")
        || effect_names_contain(effect_names, "chunk_reveal")
    {
        word_reveal_visible_text(full_text, progress)
    } else {
        full_text.to_owned()
    };

    MessageFrame {
        visible_text,
        alpha: resolve_alpha(effect_names, progress),
        scale: resolve_scale(effect_names, progress),
    }
}

pub fn typewriter_visible_text(full_text: &str, progress: f32) -> String {
    let total_weight = full_text.chars().map(char_weight).sum::<usize>();
    if full_text.is_empty() || total_weight == 0 {
        return String::new();
    }
    if progress >= 1.0 {
        return full_text.to_owned();
    }
    let threshold = ((total_weight as f32) * progress.clamp(0.0, 1.0)).round() as usize;
    let threshold = threshold.max(1);
    let mut accumulated = 0usize;
    let mut visible_chars = 0usize;
    for ch in full_text.chars() {
        accumulated += char_weight(ch);
        visible_chars += 1;
        if accumulated >= threshold {
            break;
        }
    }
    full_text.chars().take(visible_chars).collect()
}

pub fn word_reveal_visible_text(full_text: &str, progress: f32) -> String {
    if full_text.is_empty() {
        return String::new();
    }
    let word_ends = word_ends(full_text);
    if word_ends.is_empty() {
        return if progress >= 1.0 {
            full_text.to_owned()
        } else {
            String::new()
        };
    }
    if progress >= 1.0 {
        return full_text.to_owned();
    }
    let word_count = ((word_ends.len() as f32) * progress.clamp(0.0, 1.0))
        .round()
        .max(1.0) as usize;
    let end = word_ends[word_count.min(word_ends.len()) - 1];
    full_text.chars().take(end).collect()
}

pub fn resolve_scale(effect_names: &[String], progress: f32) -> f32 {
    let eased = ease_out(progress);
    if effect_names_contain(effect_names, "typewriter_zoom_in") {
        lerp(0.82, 1.0, eased)
    } else if effect_names_contain(effect_names, "zoom_in") {
        lerp(0.75, 1.0, eased)
    } else if effect_names_contain(effect_names, "zoom_out") {
        lerp(1.35, 1.0, eased)
    } else {
        1.0
    }
}

pub fn resolve_alpha(effect_names: &[String], progress: f32) -> f32 {
    let mut alpha = 1.0;
    if effect_names_contain(effect_names, "fade_in") {
        alpha *= progress;
    }
    if effect_names_contain(effect_names, "fade_out") {
        alpha *= 1.0 - progress;
    }
    let reveal_only = effect_names_contain(effect_names, "typewriter")
        || effect_names_contain(effect_names, "typewriter_zoom_in")
        || effect_names_contain(effect_names, "word_reveal")
        || effect_names_contain(effect_names, "chunk_reveal");
    if alpha == 1.0 && reveal_only {
        return if progress <= 0.0 { 0.0 } else { 1.0 };
    }
    alpha.clamp(0.0, 1.0)
}

pub fn is_supported_effect(effect_name: &str) -> bool {
    matches!(
        effect_name,
        "typewriter"
            | "typewriter_zoom_in"
            | "word_reveal"
            | "chunk_reveal"
            | "zoom_in"
            | "zoom_out"
            | "fade_in"
            | "fade_out"
            | "none"
    )
}

fn normalize_effect(effect: &str) -> String {
    effect
        .trim()
        .rsplit_once('.')
        .map(|(_, name)| name)
        .unwrap_or_else(|| effect.trim())
        .trim()
        .to_ascii_lowercase()
        .replace('-', "_")
}

fn effect_names_contain(effect_names: &[String], effect_name: &str) -> bool {
    effect_names.iter().any(|effect| effect == effect_name)
}

fn char_weight(ch: char) -> usize {
    if ch.is_whitespace() {
        1
    } else {
        match ch {
            '.' | '!' | '?' => 4,
            ',' | ';' | ':' => 3,
            _ => 1,
        }
    }
}

fn word_ends(full_text: &str) -> Vec<usize> {
    let mut word_ends = Vec::new();
    let mut in_word = false;
    let chars = full_text.chars().collect::<Vec<_>>();
    for (index, ch) in chars.iter().enumerate() {
        let whitespace = ch.is_whitespace();
        if !whitespace {
            in_word = true;
        }
        if in_word && (whitespace || index == chars.len() - 1) {
            word_ends.push(if whitespace { index } else { index + 1 });
            in_word = false;
        }
    }
    word_ends
}

fn ease_out(progress: f32) -> f32 {
    let inv = 1.0 - progress.clamp(0.0, 1.0);
    1.0 - inv * inv * inv
}

fn lerp(start: f32, end: f32, progress: f32) -> f32 {
    start + (end - start) * progress
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_ui_document_defaults_without_bevy_runtime() {
        let doc: SceneMaxUiDocument = serde_json::from_str(
            r##"{
                "name": "intro",
                "layers": [{
                    "name": "hud",
                    "widgets": [{
                        "name": "title",
                        "type": "TEXT_VIEW",
                        "text": "Ready"
                    }]
                }]
            }"##,
        )
        .unwrap();

        assert_eq!(doc.name, "intro");
        assert_eq!(doc.canvas_width, 1920.0);
        assert_eq!(doc.layers[0].widgets[0].text_color, "#FFFFFFFF");
    }

    #[test]
    fn solves_centered_fixed_widget_layout_without_bevy_runtime() {
        let doc: SceneMaxUiDocument = serde_json::from_str(
            r##"{
                "name": "intro",
                "layers": [{
                    "name": "hud",
                    "widgets": [{
                        "name": "footer",
                        "type": "TEXT_VIEW",
                        "widthMode": "FIXED",
                        "heightMode": "FIXED",
                        "width": 400,
                        "height": 80,
                        "centerHorizontal": true,
                        "constraints": [
                            {"side": "BOTTOM", "targetName": "parent", "targetSide": "BOTTOM", "margin": 40}
                        ]
                    }]
                }]
            }"##,
        )
        .unwrap();

        let rects = solve_widget_layout(&doc.layers[0].widgets, 1920.0, 1080.0);
        let footer = rects.get("footer").unwrap();

        assert_eq!(footer.x, 760.0);
        assert_eq!(footer.y, 960.0);
        assert_eq!(footer.width, 400.0);
        assert_eq!(footer.height, 80.0);
    }

    #[test]
    fn preserves_text_font_names_from_ui_documents() {
        let doc: SceneMaxUiDocument = serde_json::from_str(
            r##"{
                "name": "intro",
                "layers": [{
                    "name": "hud",
                    "widgets": [{
                        "name": "title",
                        "type": "TEXT_VIEW",
                        "fontName": "message_bold1",
                        "fontSize": 96
                    }, {
                        "name": "scores",
                        "type": "LIST_VIEW",
                        "listHeaderFontName": "message_bold1",
                        "listRowFontName": "arial_64"
                    }]
                }]
            }"##,
        )
        .unwrap();

        assert_eq!(
            doc.layers[0].widgets[0].font_name.as_deref(),
            Some("message_bold1")
        );
        assert_eq!(
            doc.layers[0].widgets[1].list_header_font_name.as_deref(),
            Some("message_bold1")
        );
        assert_eq!(
            doc.layers[0].widgets[1].list_row_font_name.as_deref(),
            Some("arial_64")
        );
    }

    #[test]
    fn detects_effect_expressions() {
        assert!(has_effect(
            "TextEffect.fade_in | TextEffect.typewriter",
            "typewriter"
        ));
        assert!(!has_effect(
            "TextEffect.fade_in | TextEffect.zoom_in",
            "typewriter"
        ));
    }

    #[test]
    fn reveals_typewriter_text_by_classic_weighted_progress() {
        assert_eq!(typewriter_visible_text("ABCD", 0.0), "A");
        assert_eq!(typewriter_visible_text("ABCD", 0.25), "A");
        assert_eq!(typewriter_visible_text("ABCD", 0.5), "AB");
        assert_eq!(typewriter_visible_text("ABCD", 1.0), "ABCD");
        assert_eq!(typewriter_visible_text("A,B", 0.5), "A,");
    }

    #[test]
    fn reveals_words_by_progress() {
        assert_eq!(
            word_reveal_visible_text("Memorize the keys, then launch", 0.0),
            "Memorize"
        );
        assert_eq!(
            word_reveal_visible_text("Memorize the keys, then launch", 1.0),
            "Memorize the keys, then launch"
        );
    }

    #[test]
    fn resolves_fade_and_zoom_like_classic_projector() {
        let effects = parse_effects("TextEffect.word_reveal | TextEffect.fade_in");
        assert_eq!(effects, vec!["word_reveal", "fade_in"]);
        assert!((resolve_alpha(&effects, 0.25) - 0.25).abs() < f32::EPSILON);

        let effects = parse_effects("TextEffect.zoom_in");
        assert!((resolve_scale(&effects, 0.0) - 0.75).abs() < f32::EPSILON);
        assert!((resolve_scale(&effects, 1.0) - 1.0).abs() < f32::EPSILON);
    }

    #[test]
    fn evaluates_combined_message_frame() {
        let effects = parse_effects("TextEffect.word_reveal | TextEffect.fade_in");
        let frame = evaluate_message_frame("Memorize the keys", &effects, 0.5);
        assert!(frame.visible_text.starts_with("Memorize"));
        assert!((frame.alpha - 0.5).abs() < f32::EPSILON);
        assert_eq!(frame.scale, 1.0);
    }
}
