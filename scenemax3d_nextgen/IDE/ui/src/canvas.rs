//! Reusable retained canvas scaling; independent of scene formats and IDE documents.
use bevy::prelude::*;
/// Fits a virtual canvas inside its measured viewport while preserving aspect ratio.
#[derive(Component)]
pub struct Canvas {
    viewport: Entity,
    width: f32,
    height: f32,
    owner: Option<Entity>,
}
impl Canvas {
    /// Virtual dimensions must be positive finite values.
    pub fn new(viewport: Entity, width: f32, height: f32) -> Self {
        Self {
            viewport,
            width,
            height,
            owner: None,
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
    views: Query<&navigation::View>,
    mut labels: Query<(&navigation::ZoomLabel, &mut Text)>,
) {
    for (entity, canvas, mut node) in &mut canvases {
        let Ok(viewport) = viewports.get(canvas.viewport) else {
            continue;
        };
        let size = viewport.size() * viewport.inverse_scale_factor();
        let fit = fit_scale(size, Vec2::new(canvas.width, canvas.height));
        let view = canvas.owner.and_then(|owner| views.get(owner).ok());
        let scale = view.and_then(|v| v.zoom).unwrap_or(fit);
        let pan = view.map_or(Vec2::ZERO, |v| v.pan);
        let width = px(canvas.width * scale);
        let height = px(canvas.height * scale);
        if node.width != width || node.height != height {
            node.width = width;
            node.height = height;
        }
        if canvas.owner.is_some() {
            let left = px((size.x - canvas.width * scale) * 0.5 + pan.x);
            let top = px((size.y - canvas.height * scale) * 0.5 + pan.y);
            if node.left != left || node.top != top || node.position_type != PositionType::Absolute
            {
                node.position_type = PositionType::Absolute;
                node.left = left;
                node.top = top;
            }
        }
        for (label, mut text) in &mut labels {
            if label.0 == entity {
                let value = format!("{:.0}%", scale * 100.);
                if text.0 != value {
                    text.0 = value;
                }
            }
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

fn fit_scale(viewport: Vec2, canvas: Vec2) -> f32 {
    ((viewport.x - 16.) / canvas.x)
        .min((viewport.y - 16.) / canvas.y)
        .clamp(0.01, 2.)
}
mod navigation;
pub use navigation::install_navigation;
