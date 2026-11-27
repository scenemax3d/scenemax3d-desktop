//! Native browser controls provide touch focus, virtual keyboards and IME.
use super::*;
use wasm_bindgen::prelude::*;

#[wasm_bindgen(module = "/src/ui/edits/browser_input.mjs")]
extern "C" {
    #[wasm_bindgen(catch, js_name = sync)]
    fn sync_browser(state: &str) -> Result<(), JsValue>;
    #[wasm_bindgen(catch, js_name = takeEvents)]
    fn take_browser_events() -> Result<String, JsValue>;
}

#[derive(Deserialize)]
pub(super) struct Input {
    // JS preserves the full Bevy generation in a decimal string.
    #[serde(deserialize_with = "entity_id")]
    pub entity: u64,
    pub text: String,
    pub focus: bool,
    pub caret: usize,
    pub anchor: usize,
}
fn entity_id<'de, D: serde::Deserializer<'de>>(d: D) -> Result<u64, D::Error> {
    String::deserialize(d)?
        .parse()
        .map_err(serde::de::Error::custom)
}
pub(super) fn take_events() -> Vec<Input> {
    take_browser_events()
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

fn clipped_polygon(
    size: Vec2,
    transform: &bevy::ui::UiGlobalTransform,
    clip: Option<&bevy::ui::CalculatedClip>,
) -> Vec<Vec2> {
    if clip.is_some_and(|c| c.is_fully_clipped()) {
        return Vec::new();
    }
    let mut polygon = vec![
        Vec2::ZERO,
        Vec2::new(size.x, 0.),
        size,
        Vec2::new(0., size.y),
    ];
    for rect in clip.and_then(|c| c.rects()).unwrap_or(&[]) {
        for (axis, boundary, sign) in [
            (0, rect.rect.min.x, 1.),
            (0, rect.rect.max.x, -1.),
            (1, rect.rect.min.y, 1.),
            (1, rect.rect.max.y, -1.),
        ] {
            let distance = |point: Vec2| {
                let world = transform.transform_point2(point - size * 0.5);
                (rect.world_to_clip_local.transform_point2(world)[axis] - boundary) * sign
            };
            let mut next = Vec::new();
            for i in 0..polygon.len() {
                let a = polygon[i];
                let b = polygon[(i + 1) % polygon.len()];
                let da = distance(a);
                let db = distance(b);
                if da >= 0. {
                    next.push(a);
                }
                if (da >= 0.) != (db >= 0.) {
                    next.push(a + (b - a) * (da / (da - db)));
                }
            }
            polygon = next;
        }
    }
    polygon
}

pub(crate) fn sync(
    edits: Query<(
        Entity,
        &EditControl,
        &ComputedNode,
        &bevy::ui::UiGlobalTransform,
        &InheritedVisibility,
        Option<&bevy::ui::CalculatedClip>,
        &bevy::ui::ComputedStackIndex,
        &Name,
    )>,
) {
    let mut fields = Vec::new();
    for (entity, edit, node, transform, visibility, clip, stack, name) in &edits {
        if !visibility.get() || node.size.x <= 0. || node.size.y <= 0. {
            continue;
        }
        let polygon = clipped_polygon(node.size, transform, clip);
        if polygon.len() < 3 {
            continue;
        }
        let origin = transform.transform_point2(-node.size * 0.5);
        let x = transform.transform_vector2(Vec2::X);
        let y = transform.transform_vector2(Vec2::Y);
        fields.push(serde_json::json!({
            "id":entity.to_bits().to_string(), "name":name.as_str(),
            "value":edit.state.text, "placeholder":edit.state.placeholder,
            "multiline":edit.state.multiline, "color":edit.state.color,
            "background":edit.state.focused, "cursor":edit.state.cursor,
            "fontSize":edit.state.size * edit.scale / node.inverse_scale_factor.max(0.001),
            "width":node.size.x, "height":node.size.y,
            "matrix":[x.x,x.y,y.x,y.y,origin.x,origin.y], "z":stack.0,
            "clip":polygon.iter().map(|p| [p.x,p.y]).collect::<Vec<_>>()
        }));
    }
    if let Err(error) = sync_browser(&serde_json::to_string(&fields).unwrap_or_default()) {
        tracing::warn!(?error, "browser edit controls could not synchronize");
    }
}
