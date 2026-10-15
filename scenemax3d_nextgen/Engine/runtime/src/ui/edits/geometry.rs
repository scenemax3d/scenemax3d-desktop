//! Editing positions come from the same shaped/bitmap advances as the text.
use super::*;
#[derive(Default, Clone, PartialEq)]
pub(super) struct Geometry {
    pub stops: Vec<Vec2>,
    pub heights: Vec<f32>,
}
impl Geometry {
    pub fn nearest(&self, point: Vec2) -> usize {
        self.stops
            .iter()
            .enumerate()
            .min_by(|(ia, a), (ib, b)| {
                let score = |i: usize, p: &Vec2| {
                    (
                        (p.y + self.heights[i] * 0.5 - point.y).abs(),
                        (p.x - point.x).abs(),
                    )
                };
                let a = score(*ia, a);
                let b = score(*ib, b);
                a.0.total_cmp(&b.0).then(a.1.total_cmp(&b.1))
            })
            .map_or(0, |(i, _)| i)
    }
    pub fn vertical(&self, index: usize, direction: i32) -> usize {
        let Some(p) = self.stops.get(index) else {
            return index;
        };
        let next = self
            .stops
            .iter()
            .filter(|q| {
                if direction < 0 {
                    q.y < p.y - 0.5
                } else {
                    q.y > p.y + 0.5
                }
            })
            .min_by(|a, b| (a.y - p.y).abs().total_cmp(&(b.y - p.y).abs()));
        let Some(next) = next else {
            return index;
        };
        self.nearest(Vec2::new(p.x, next.y + self.heights[index] * 0.5))
    }
    fn bitmap(
        text: &str,
        font: &scenemax_runtime_ui_core::bitmap::Font,
        size: f32,
        height: f32,
    ) -> Self {
        let scale = size / font.size.max(1.);
        let h = font.line_height.max(font.size) * scale;
        let mut y = (height - h * text.split('\n').count() as f32) * 0.5;
        let mut x = 0.;
        let mut previous = None;
        let mut result = Self::default();
        for ch in text.chars() {
            if ch != '\n' {
                x += previous
                    .and_then(|p| font.kernings.get(&(p, ch)))
                    .copied()
                    .unwrap_or(0.)
                    * scale;
            }
            result.stops.push(Vec2::new(x, y));
            result.heights.push(h);
            if ch == '\n' {
                x = 0.;
                y += h;
                previous = None;
            } else {
                x += font
                    .glyphs
                    .get(&ch)
                    .map_or(h * 0.35, |g| g.x_advance * scale);
                previous = Some(ch);
            }
        }
        result.stops.push(Vec2::new(x, y));
        result.heights.push(h);
        result
    }
    fn shaped(text: &str, block: &bevy::text::ComputedTextBlock, scale: f32) -> Self {
        let count = text.chars().count();
        let mut result = Self {
            stops: vec![Vec2::ZERO; count + 1],
            heights: vec![16.; count + 1],
        };
        for line in block.buffer().lines() {
            let metrics = line.metrics();
            let y = metrics.block_min_coord * scale;
            let h = metrics.line_height * scale;
            let mut x = metrics.offset * scale;
            for run in line.runs() {
                for cluster in run.visual_clusters() {
                    let range = cluster.text_range();
                    if range.end > text.len()
                        || !text.is_char_boundary(range.start)
                        || !text.is_char_boundary(range.end)
                    {
                        continue;
                    }
                    let start = text[..range.start].chars().count();
                    let n = text[range.clone()].chars().count().max(1);
                    let width = cluster.advance() * scale;
                    for j in 0..=n {
                        let i = start + j;
                        if i > count {
                            break;
                        }
                        let t = j as f32 / n as f32;
                        result.stops[i] =
                            Vec2::new(x + width * if cluster.is_rtl() { 1. - t } else { t }, y);
                        result.heights[i] = h;
                    }
                    x += width;
                }
            }
            // Empty lines and hard breaks also need an addressable caret.
            let start = line.text_range().start.min(text.len());
            if text.is_char_boundary(start) {
                let i = text[..start].chars().count();
                if line.text_range().is_empty() {
                    result.stops[i] = Vec2::new(x, y);
                    result.heights[i] = h;
                }
            }
        }
        result
    }
}
pub(crate) fn refresh(world: &mut World) {
    let entities: Vec<_> = world
        .query_filtered::<Entity, With<EditControl>>()
        .iter(world)
        .collect();
    for owner in entities {
        let Some(edit) = world.get::<EditControl>(owner) else {
            continue;
        };
        let Some(text_entity) = edit.text_entity else {
            continue;
        };
        let text = edit.state.text.clone();
        let geometry = if let Some(bitmap) = world.get::<SceneMaxUiBitmapText>(text_entity) {
            let Some(font) = world
                .resource::<SceneMaxUiRuntime>()
                .bitmap_fonts
                .get(&bitmap.font_name)
            else {
                continue;
            };
            Geometry::bitmap(&text, &font.metrics, bitmap.font_size, bitmap.widget_height)
        } else if let Some(block) = world.get::<bevy::text::ComputedTextBlock>(text_entity) {
            let scale = world
                .get::<ComputedNode>(text_entity)
                .map_or(1., |n| n.inverse_scale_factor());
            Geometry::shaped(&text, block, scale)
        } else {
            continue;
        };
        if !edit.overlay_dirty && edit.geometry == geometry {
            continue;
        }
        let (focus, caret, anchor, cursor, selection) = (
            edit.focus,
            edit.caret,
            edit.anchor,
            edit.state.cursor.clone(),
            edit.state.selection.clone(),
        );
        let mut edit = world.get_mut::<EditControl>(owner).unwrap();
        let old = std::mem::take(&mut edit.adornments);
        edit.geometry = geometry.clone();
        edit.overlay_dirty = false;
        for entity in old {
            let _ = world.despawn(entity);
        }
        if !focus {
            continue;
        }
        let mut visuals = Vec::new();
        for index in 0..geometry.stops.len() {
            let selected = index >= caret.min(anchor) && index < caret.max(anchor);
            if !selected && index != caret {
                continue;
            }
            let p = geometry.stops[index];
            let h = geometry.heights[index];
            let next = geometry.stops.get(index + 1).copied().unwrap_or(p);
            let width = if selected {
                if (next.y - p.y).abs() < 0.5 {
                    (next.x - p.x).abs().max(2.)
                } else {
                    2.
                }
            } else {
                2.
            };
            let x = if selected && (next.y - p.y).abs() < 0.5 {
                p.x.min(next.x)
            } else {
                p.x
            };
            let entity = world
                .spawn((
                    Node {
                        position_type: PositionType::Absolute,
                        left: Val::Px(5. + x),
                        top: Val::Px(4. + p.y),
                        width: Val::Px(width),
                        height: Val::Px(h),
                        ..default()
                    },
                    BackgroundColor(parse_ui_color(if selected { &selection } else { &cursor })),
                    ZIndex(if selected { 0 } else { 2 }),
                    bevy::ui::FocusPolicy::Pass,
                    ChildOf(owner),
                ))
                .id();
            visuals.push(entity);
        }
        world.get_mut::<EditControl>(owner).unwrap().adornments = visuals;
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn pointer_vertical_and_kerning_use_measured_advances() {
        let font=scenemax_runtime_ui_core::bitmap::parse("info size=20\ncommon lineHeight=20\npage id=0 file=font.png\nchar id=87 width=15 height=20 xadvance=17\nchar id=105 width=3 height=20 xadvance=4\nkerning first=87 second=105 amount=-2").unwrap();
        let g = Geometry::bitmap("Wi\niW", &font, 20., 40.);
        assert_eq!(
            g.stops,
            vec![
                Vec2::new(0., 0.),
                Vec2::new(15., 0.),
                Vec2::new(19., 0.),
                Vec2::new(0., 20.),
                Vec2::new(4., 20.),
                Vec2::new(21., 20.)
            ]
        );
        assert_eq!(g.nearest(Vec2::new(14., 5.)), 1);
        assert_eq!(g.nearest(Vec2::new(3., 24.)), 4);
        assert_eq!(g.vertical(2, 1), 5);
        assert_eq!(g.vertical(4, -1), 0);
    }
}
