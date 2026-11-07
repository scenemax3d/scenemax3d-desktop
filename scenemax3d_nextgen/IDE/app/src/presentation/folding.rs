//! Gutter controls and native-editor projections for lossless code folding.
use super::components::Editor;
use bevy::{
    prelude::*,
    text::{EditableText, FontCx, LayoutCx, TextEdit},
};
use scenemax_ide_core::folding::{Projection, regions};
use scenemax_ui_input::{Button, Interaction};

#[derive(Component)]
pub(crate) struct View(pub Projection);
#[derive(Component)]
pub(crate) struct Gutter(pub Entity);
#[derive(Component, Default)]
pub(crate) struct Drawn(u64);
#[derive(Component)]
pub(crate) struct Toggle {
    pub(crate) editor: Entity,
    pub(crate) body: std::ops::Range<usize>,
}
#[derive(Component)]
pub(crate) struct FoldButton;

pub(crate) fn toggle(
    buttons: Query<(&Interaction, &Toggle), Changed<Interaction>>,
    mut inputs: Query<(&mut View, &mut EditableText)>,
    fonts: Option<ResMut<FontCx>>,
    layouts: Option<ResMut<LayoutCx>>,
) {
    let (Some(mut fonts), Some(mut layouts)) = (fonts, layouts) else {
        return;
    };
    for (interaction, action) in &buttons {
        if *interaction != Interaction::Pressed {
            continue;
        }
        let Ok((mut view, mut input)) = inputs.get_mut(action.editor) else {
            continue;
        };
        if input.is_composing() || input.value() != view.0.visible.as_str() {
            continue;
        }
        if !regions(&view.0.source)
            .iter()
            .any(|r| r.body == action.body)
        {
            continue;
        }
        let selection = input.editor().raw_selection();
        let anchor = view.0.to_source(selection.anchor().index());
        let focus = view.0.to_source(selection.focus().index());
        view.0.toggle(action.body.clone());
        let offset = input.viewport.offset;
        input.editor_mut().set_text(&view.0.visible);
        input
            .editor_mut()
            .driver(&mut fonts.context, &mut layouts.0)
            .select_byte_range(view.0.to_visible(anchor), view.0.to_visible(focus));
        input.viewport.offset = offset;
    }
}

// Clipboard/select-all must see complete source, including folded bodies.
// Reveal before native processing; ordinary typing elsewhere keeps folds intact.
pub(crate) fn prepare(
    mut inputs: Query<(&mut View, &mut EditableText)>,
    fonts: Option<ResMut<FontCx>>,
    layouts: Option<ResMut<LayoutCx>>,
) {
    let (Some(mut fonts), Some(mut layouts)) = (fonts, layouts) else {
        return;
    };
    for (mut view, mut input) in &mut inputs {
        if view.0.collapsed.is_empty()
            || !input.pending_edits.iter().any(|e| {
                matches!(
                    e,
                    TextEdit::Copy
                        | TextEdit::Cut
                        | TextEdit::SelectAll
                        | TextEdit::SelectAllIfCollapsed
                )
            })
        {
            continue;
        }
        let selection = input.editor().raw_selection();
        let a = view.0.to_source(selection.anchor().index());
        let b = view.0.to_source(selection.focus().index());
        view.0 = Projection::new(view.0.source.clone());
        input.editor_mut().set_text(&view.0.visible);
        input
            .editor_mut()
            .driver(&mut fonts.context, &mut layouts.0)
            .select_byte_range(a, b);
    }
}

pub(crate) fn draw(
    mut commands: Commands,
    session: Res<crate::application::Session>,
    inputs: Query<(
        &Editor,
        &EditableText,
        &View,
        &bevy::text::LineHeight,
        &ComputedNode,
    )>,
    mut gutters: Query<(Entity, &Gutter, &mut Drawn, Option<&Children>)>,
    buttons: Query<(), With<FoldButton>>,
) {
    use std::hash::{Hash, Hasher};
    for (gutter, owner, mut drawn, children) in &mut gutters {
        let Ok((editor, input, view, height, node)) = inputs.get(owner.0) else {
            continue;
        };
        if session.workspace.active_id() != Some(editor.0) {
            continue;
        }
        let bevy::text::LineHeight::Px(height) = *height else {
            continue;
        };
        let scroll = input.viewport.offset.y * node.inverse_scale_factor();
        let mut hash = std::collections::hash_map::DefaultHasher::new();
        view.0.source.hash(&mut hash);
        view.0.collapsed.hash(&mut hash);
        height.to_bits().hash(&mut hash);
        scroll.to_bits().hash(&mut hash);
        node.size().y.to_bits().hash(&mut hash);
        let key = hash.finish();
        if drawn.0 == key {
            continue;
        }
        drawn.0 = key;
        if let Some(children) = children {
            for child in children.iter() {
                if buttons.contains(child) {
                    commands.entity(child).despawn();
                }
            }
        }
        let hidden = view.0.hidden();
        for region in regions(&view.0.source) {
            if hidden.iter().any(|r| r.contains(&region.start)) {
                continue;
            }
            let at = view.0.to_visible(region.start);
            let line = view.0.visible[..at].bytes().filter(|b| *b == b'\n').count();
            let top = 16. + line as f32 * height - scroll;
            if top + height < 0. || top > node.size().y * node.inverse_scale_factor() {
                continue;
            }
            let collapsed = view.0.collapsed.contains(&region.body);
            let button = commands
                .spawn((
                    Button,
                    FoldButton,
                    Toggle {
                        editor: owner.0,
                        body: region.body,
                    },
                    Name::new(if collapsed {
                        "Expand code block"
                    } else {
                        "Collapse code block"
                    }),
                    Node {
                        position_type: PositionType::Absolute,
                        left: px(1.),
                        top: px(top),
                        width: px(17.),
                        height: px(height),
                        align_items: AlignItems::Center,
                        justify_content: JustifyContent::Center,
                        ..default()
                    },
                    ChildOf(gutter),
                ))
                .id();
            commands.spawn((
                scenemax_ide_ui::label(if collapsed { "▶" } else { "▼" }, 10.),
                Pickable::IGNORE,
                ChildOf(button),
            ));
        }
    }
}
