//! Reusable retained canvas scaling; independent of scene formats and IDE documents.
use bevy::prelude::*;
/// Fits a virtual canvas inside its measured viewport while preserving aspect ratio.
#[derive(Component)]
pub struct Canvas {
    viewport: Entity,
    width: f32,
    height: f32,
}
impl Canvas {
    /// Virtual dimensions must be positive finite values.
    pub fn new(viewport: Entity, width: f32, height: f32) -> Self {
        Self {
            viewport,
            width,
            height,
        }
    }
}
/// Text whose logical font size follows its canvas scale.
#[derive(Component)]
pub struct CanvasText {
    canvas: Entity,
    size: f32,
}
impl CanvasText {
    /// Attach text to a canvas using a font size in virtual canvas units.
    pub fn new(canvas: Entity, size: f32) -> Self {
        Self { canvas, size }
    }
}
pub(crate) fn fit(
    viewports: Query<&ComputedNode>,
    mut canvases: Query<(Entity, &Canvas, &mut Node)>,
    mut fonts: Query<(&CanvasText, &mut TextFont)>,
) {
    for (entity, canvas, mut node) in &mut canvases {
        let Ok(viewport) = viewports.get(canvas.viewport) else {
            continue;
        };
        let size = viewport.size() * viewport.inverse_scale_factor();
        let scale = ((size.x - 16.) / canvas.width)
            .min((size.y - 16.) / canvas.height)
            .clamp(0.01, 2.);
        let width = px(canvas.width * scale);
        let height = px(canvas.height * scale);
        if node.width != width || node.height != height {
            node.width = width;
            node.height = height;
        }
        for (text, mut font) in &mut fonts {
            if text.canvas == entity {
                let size = FontSize::Px(text.size * scale);
                if font.font_size != size {
                    font.font_size = size;
                }
            }
        }
    }
}
