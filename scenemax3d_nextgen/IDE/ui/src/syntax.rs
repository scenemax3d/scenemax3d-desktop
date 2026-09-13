//! Color-only extension of Bevy's native editable text rendering.
//! Uses Parley's visual clusters, so ligatures and bidirectional text retain
//! their original layout, hit testing, selection and composition behavior.
use bevy::{
    prelude::*,
    render::{Extract, ExtractSchedule, RenderApp},
    text::{EditableText, TextCursorStyle, TextLayoutInfo},
    ui_render::{ExtractedUiItem, ExtractedUiNode, ExtractedUiNodes, NodeType, RenderUiSystems},
};
use std::{collections::HashMap, ops::Range};

/// Non-overlapping, sorted UTF-8 source ranges with foreground colors.
#[derive(Component, Default)]
pub struct TextHighlights {
    /// Exact source these ranges describe. Stale results are never rendered.
    pub source: String,
    /// Byte ranges and their colors; unlisted text keeps its normal color.
    pub spans: Vec<(Range<usize>, Color)>,
}

/// Source ranges outlined without changing text layout or cursor geometry.
#[derive(Component, Default)]
pub struct TextEmphasis {
    /// UTF-8 byte ranges in the associated `TextHighlights` source.
    pub ranges: Vec<Range<usize>>,
}

#[derive(Resource, Default)]
struct TextPassStart(usize);

#[derive(Default)]
struct GlyphCache {
    ticks: Option<(
        bevy::ecs::change_detection::Tick,
        bevy::ecs::change_detection::Tick,
    )>,
    colors: Vec<Option<LinearRgba>>,
    bytes: Vec<usize>,
}

pub(crate) fn install(app: &mut App) {
    if let Some(render) = app.get_sub_app_mut(RenderApp) {
        render.init_resource::<TextPassStart>().add_systems(
            ExtractSchedule,
            (
                mark_text_start
                    .after(RenderUiSystems::ExtractTextShadows)
                    .before(RenderUiSystems::ExtractText),
                color_text
                    .after(RenderUiSystems::ExtractText)
                    .before(RenderUiSystems::ExtractCursor),
            ),
        );
    }
}
fn mark_text_start(nodes: Res<ExtractedUiNodes>, mut start: ResMut<TextPassStart>) {
    start.0 = nodes.uinodes.len();
}
type HighlightInput<'a> = (
    Entity,
    &'a EditableText,
    Ref<'a, TextHighlights>,
    Ref<'a, TextLayoutInfo>,
    Option<&'a TextCursorStyle>,
    Option<&'a TextEmphasis>,
);
fn color_text(
    mut commands: Commands,
    query: Extract<Query<HighlightInput<'static>>>,
    mut cache: Local<HashMap<Entity, GlyphCache>>,
    mut nodes: ResMut<ExtractedUiNodes>,
    start: Res<TextPassStart>,
) {
    // Only rendered document entities are visited. This does not create another
    // text surface or modify the input buffer, undo history, or selection.
    let mut outlines = Vec::new();
    let mut offsets = HashMap::<Entity, usize>::new();
    cache.retain(|entity, _| query.contains(*entity));
    let ExtractedUiNodes { uinodes, glyphs } = &mut *nodes;
    for node in &uinodes[start.0..] {
        let ExtractedUiItem::Glyphs { range } = &node.item else {
            continue;
        };
        let Ok((entity, input, highlights, info, cursor_style, emphasis)) =
            query.get(node.main_entity.id())
        else {
            continue;
        };
        if input.is_composing() || input.value() != highlights.source.as_str() {
            continue;
        }
        let Some(layout) = input.editor().try_layout() else {
            continue;
        };
        let cached = cache.entry(entity).or_default();
        let ticks = (highlights.last_changed(), info.last_changed());
        if cached.ticks != Some(ticks) {
            cached.bytes.clear();
            let mut result = Vec::with_capacity(info.glyphs.len());
            for line in layout.lines() {
                for run in line.runs() {
                    for cluster in run.visual_clusters() {
                        let byte = cluster.text_range().start;
                        let index = highlights
                            .spans
                            .partition_point(|(range, _)| range.end <= byte);
                        let color = highlights
                            .spans
                            .get(index)
                            .filter(|(range, _)| range.contains(&byte))
                            .map(|(_, color)| color.to_linear());
                        for glyph in cluster.glyphs() {
                            if u16::try_from(glyph.id).is_ok() {
                                result.push(color);
                                cached.bytes.push(byte);
                            }
                        }
                    }
                }
            }
            cached.colors = result;
            cached.ticks = Some(ticks);
        }
        let palette = &cached.colors;
        // Fail closed if a future Bevy shaping change alters glyph correspondence.
        if palette.len() != info.glyphs.len() {
            continue;
        }
        let offset = offsets.entry(entity).or_default();
        if *offset + range.len() > palette.len() {
            continue;
        }
        for (index, glyph) in glyphs[range.clone()].iter_mut().enumerate() {
            let i = *offset + index;
            if emphasis.is_some_and(|emphasis| {
                emphasis.ranges.iter().any(|r| r.contains(&cached.bytes[i]))
            }) {
                let bounds = Rect::from_center_size(
                    info.glyphs[i].position,
                    info.glyphs[i].atlas_info.rect.size() + Vec2::splat(4.0 * info.scale_factor),
                );
                for rect in outline_rects(bounds, info.scale_factor.max(1.0)) {
                    outlines.push(ExtractedUiNode {
                        z_order: node.z_order + 0.01,
                        image: Default::default(),
                        clip: node.clip,
                        extracted_camera_entity: node.extracted_camera_entity,
                        main_entity: node.main_entity,
                        render_entity: commands
                            .spawn(bevy::render::sync_world::TemporaryRenderEntity)
                            .id(),
                        transform: node.transform
                            * bevy::math::Affine2::from_translation(rect.center()),
                        item: ExtractedUiItem::Node {
                            color: Color::srgba_u8(166, 187, 194, 135).to_linear(),
                            rect: Rect::from_corners(Vec2::ZERO, rect.size()),
                            atlas_scaling: None,
                            flip_x: false,
                            flip_y: false,
                            border: Default::default(),
                            border_radius: Default::default(),
                            node_type: NodeType::Rect,
                        },
                    });
                }
            }
            let selected_override = cursor_style
                .is_some_and(|style| style.selected_text_color.is_some())
                && info.selection_rects.iter().any(|rect| {
                    let glyph_rect = Rect::from_center_size(
                        info.glyphs[i].position,
                        info.glyphs[i].atlas_info.rect.size(),
                    );
                    rect.contains(glyph_rect.min) && rect.contains(glyph_rect.max)
                });
            if !selected_override
                && info.glyphs[i].atlas_info.is_alpha_mask
                && let Some(color) = palette[i]
            {
                glyph.color = color;
            }
        }
        *offset += range.len();
    }
    uinodes.extend(outlines);
}

fn outline_rects(bounds: Rect, width: f32) -> [Rect; 4] {
    let lo = bounds.min;
    let hi = bounds.max;
    [
        Rect::from_corners(lo, Vec2::new(hi.x, lo.y + width)),
        Rect::from_corners(Vec2::new(lo.x, hi.y - width), hi),
        Rect::from_corners(lo, Vec2::new(lo.x + width, hi.y)),
        Rect::from_corners(Vec2::new(hi.x - width, lo.y), hi),
    ]
}
