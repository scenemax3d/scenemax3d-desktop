//! UI designer adapter for the runtime's pure schema and constraint solver.
use scenemax_runtime_ui_core::{
    SceneMaxUiDocument, SceneMaxUiWidgetDef, UiLayoutRect, solve_widget_layout,
};

/// Bounded preview derived from the shared runtime UI schema.
pub struct ScenePreview {
    /// Name.
    pub name: String,
    /// Width.
    pub width: f32,
    /// Height.
    pub height: f32,
    /// Widgets.
    pub widgets: Vec<PreviewWidget>,
}
/// A widget layout in virtual canvas coordinates.
pub struct PreviewWidget {
    /// Pointer.
    pub pointer: String,
    /// Name.
    pub name: String,
    /// Kind.
    pub kind: String,
    /// Layer.
    pub layer: String,
    /// Depth.
    pub depth: usize,
    /// Rect.
    pub rect: [f32; 4],
    /// Visible.
    pub visible: bool,
    /// Text.
    pub text: String,
    /// Color.
    pub color: String,
    /// Text color.
    pub text_color: String,
    /// Horizontal text alignment from the runtime schema.
    pub alignment: String,
    /// Font size.
    pub font_size: f32,
    /// Width.
    pub width: f32,
    /// Height.
    pub height: f32,
}
/// Parse and solve a UI scene without loading assets or creating a window.
pub fn preview(source: &str) -> Result<ScenePreview, String> {
    let value = scenemax_ide_core::scene::parse(source)?;
    let doc: SceneMaxUiDocument = serde_json::from_value(value).map_err(|e| e.to_string())?;
    if ![doc.canvas_width, doc.canvas_height]
        .iter()
        .all(|n| n.is_finite() && (1.0..=16384.0).contains(n))
    {
        return Err("Canvas dimensions must be between 1 and 16384".into());
    }
    let mut preview = ScenePreview {
        name: doc.name,
        width: doc.canvas_width,
        height: doc.canvas_height,
        widgets: vec![],
    };
    let mut layers: Vec<_> = doc.layers.iter().enumerate().collect();
    layers.sort_by_key(|(_, l)| l.z_order);
    for (i, layer) in layers {
        let parent = UiLayoutRect {
            x: 0.,
            y: 0.,
            width: doc.canvas_width,
            height: doc.canvas_height,
        };
        collect(
            &layer.widgets,
            &format!("/layers/{i}/widgets"),
            &layer.name,
            parent,
            layer.visible,
            &mut preview.widgets,
        )?;
    }
    Ok(preview)
}
fn collect(
    widgets: &[SceneMaxUiWidgetDef],
    pointer: &str,
    layer: &str,
    parent: UiLayoutRect,
    visible: bool,
    output: &mut Vec<PreviewWidget>,
) -> Result<(), String> {
    if output.len() + widgets.len() > 256 {
        return Err("The first designer supports up to 256 widgets".into());
    }
    let rects = solve_widget_layout(widgets, parent.width, parent.height);
    let mut ordered: Vec<_> = widgets.iter().enumerate().collect();
    ordered.sort_by_key(|(_, w)| w.z_order);
    for (i, widget) in ordered {
        let rect = rects.get(&widget.name).ok_or("Missing widget layout")?;
        if ![rect.x, rect.y, rect.width, rect.height, widget.font_size]
            .iter()
            .all(|n| n.is_finite())
        {
            return Err("Non-finite widget layout".into());
        }
        let id = format!("{pointer}/{i}");
        let button = widget.widget_type == "BUTTON";
        output.push(PreviewWidget {
            pointer: id.clone(),
            name: widget.name.clone(),
            kind: widget.widget_type.clone(),
            layer: layer.into(),
            depth: pointer.matches("children").count(),
            rect: [
                parent.x + rect.x,
                parent.y + rect.y,
                rect.width,
                rect.height,
            ],
            visible: visible && widget.visible,
            text: if button {
                widget.button_text.clone()
            } else {
                widget.text.clone()
            },
            color: if button {
                widget.button_color.clone()
            } else {
                widget.background_color.clone()
            },
            text_color: if button {
                widget.button_text_color.clone()
            } else {
                widget.text_color.clone()
            },
            alignment: widget.text_alignment.clone(),
            font_size: widget.font_size,
            width: widget.width,
            height: widget.height,
        });
        let child = UiLayoutRect {
            x: parent.x + rect.x + widget.padding_left,
            y: parent.y + rect.y + widget.padding_top,
            width: (rect.width - widget.padding_left - widget.padding_right).max(1.),
            height: (rect.height - widget.padding_top - widget.padding_bottom).max(1.),
        };
        collect(
            &widget.children,
            &format!("{id}/children"),
            layer,
            child,
            visible && widget.visible,
            output,
        )?;
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn preview_uses_runtime_constraints_and_preserves_widget_identity() {
        let s = r##"{"name":"test","canvasWidth":800,"canvasHeight":600,"layers":[{"name":"overlay","widgets":[{"name":"button","type":"BUTTON","widthMode":"FIXED","heightMode":"FIXED","width":200,"height":50,"centerHorizontal":true,"centerVertical":true}]}]}"##;
        let p = preview(s).unwrap();
        assert_eq!(p.widgets[0].rect, [300., 275., 200., 50.]);
        assert_eq!(p.widgets[0].pointer, "/layers/0/widgets/0");
    }
}
