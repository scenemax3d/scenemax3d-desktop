//! Retained multiline input. Knows nothing about files, parsers or project state.
use crate::theme::*;
use bevy::{
    prelude::*,
    text::{EditableText, FontSource},
};

/// Commit native queued edits before application commands and UI construction.
/// Filtered inputs remain on Bevy's normal schedule. Bevy still owns the
/// clipboard/IME behavior and the PostUpdate layout
/// and change notifications. This leaves no structural UI work after layout.
pub fn commit_pending_input(
    mut inputs: Query<&mut EditableText, Without<bevy::text::EditableTextFilter>>,
    fonts: Option<ResMut<bevy::text::FontCx>>,
    layouts: Option<ResMut<bevy::text::LayoutCx>>,
    clipboard: Option<ResMut<bevy::clipboard::Clipboard>>,
) {
    let (Some(mut fonts), Some(mut layouts), Some(mut clipboard)) = (fonts, layouts, clipboard)
    else {
        return;
    };
    for mut input in &mut inputs {
        if input.pending_edits.is_empty() && input.pending_paste.is_none() {
            continue;
        }
        input.apply_pending_edits(&mut fonts, &mut layouts.0, &mut clipboard, |_| true);
    }
}
/// Spawn an input tagged with a caller-owned marker.
pub fn spawn_editor<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    value: &str,
    active: bool,
) -> Entity {
    commands
        .spawn((
            marker,
            // Required by TabNavigationPlugin click-to-focus, as well as native text input.
            bevy::input_focus::tab_navigation::TabIndex(0),
            Node {
                width: percent(100.),
                height: percent(100.),
                min_width: px(0.),
                padding: px(16.).all(),
                display: if active { Display::Flex } else { Display::None },
                ..default()
            },
            EditableText {
                allow_newlines: true,
                max_characters: Some(2 * 1024 * 1024),
                visible_lines: None,
                ..EditableText::new(value)
            },
            TextLayout::no_wrap(),
            bevy::text::LineHeight::Px(22.),
            TextFont {
                font: FontSource::Monospace,
                font_size: FontSize::Px(16.),
                ..default()
            },
            TextColor(INK),
            TEXT_CURSOR_STYLE,
            BackgroundColor(BG),
            ChildOf(parent),
        ))
        .observe(
            |event: On<Pointer<Scroll>>,
             keys: Res<ButtonInput<KeyCode>>,
             mut query: Query<(
                &mut bevy::ui::widget::TextScroll,
                &ComputedNode,
                &bevy::text::TextLayoutInfo,
            )>| {
                if let Ok((mut scroll, node, layout)) = query.get_mut(event.entity) {
                    let scale = if event.unit == bevy::input::mouse::MouseScrollUnit::Line {
                        40.
                    } else {
                        1.
                    };
                    let mut delta = Vec2::new(event.x, event.y) * scale;
                    if keys.any_pressed([KeyCode::ShiftLeft, KeyCode::ShiftRight]) {
                        std::mem::swap(&mut delta.x, &mut delta.y);
                    }
                    let max = (layout.size - node.content_box().size()).max(Vec2::ZERO);
                    scroll.0 = (scroll.0 - delta).clamp(Vec2::ZERO, max);
                }
            },
        )
        .id()
}

// Bevy prunes the local font source cache every frame. Keep a shared weak backing
// cache so reloaded system fonts reuse blobs still held by existing text layouts,
// and therefore reuse their GPU glyph atlases instead of creating fresh IDs.
pub(crate) fn share_font_cache(fonts: Option<ResMut<bevy::text::FontCx>>) {
    if let Some(mut fonts) = fonts {
        fonts.source_cache.make_shared();
    }
}

#[cfg(all(test, target_os = "windows"))]
mod font_cache_tests {
    use super::*;
    use bevy::{ecs::system::RunSystemOnce, text::FontCx};

    #[test]
    fn system_font_id_survives_pruning_while_existing_layout_retains_blob() {
        let mut world = World::new();
        world.init_resource::<FontCx>();
        let source = {
            let mut fonts = world.resource_mut::<FontCx>();
            let family = fonts
                .collection
                .family_by_name("Arial")
                .expect("Windows system font");
            family.fonts()[0].source().clone()
        };
        // Reproduce the original behavior: pruning loses the identity even while
        // an existing text layout still references the same font bytes.
        {
            let mut fonts = world.resource_mut::<FontCx>();
            let retained = fonts.source_cache.get(&source).unwrap();
            fonts.source_cache.prune(0, false);
            assert_ne!(retained.id(), fonts.source_cache.get(&source).unwrap().id());
        }
        world.run_system_once(share_font_cache).unwrap();
        let mut fonts = world.resource_mut::<FontCx>();
        let retained = fonts.source_cache.get(&source).unwrap();
        for _ in 0..500 {
            fonts.source_cache.prune(0, false);
            assert_eq!(retained.id(), fonts.source_cache.get(&source).unwrap().id());
        }
    }
}
