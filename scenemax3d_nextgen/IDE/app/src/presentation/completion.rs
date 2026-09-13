//! Completion input lifecycle, with stale-source guards and native UI presentation.
use super::components::Editor;
use bevy::{
    input_focus::{FocusCause, InputFocus},
    prelude::*,
    text::{EditableText, FontCx, LayoutCx, TextEdit, TextLayoutInfo},
    ui::widget::TextScroll,
};
use scenemax_ide_core::completion::{Completions, complete_with_symbols};

#[derive(Default, Resource)]
pub(crate) struct CompletionState {
    active: Option<Active>,
    trigger: Option<(Entity, bool)>,
    pub(crate) accept: bool,
    popup: Option<Entity>,
    version: u64,
    drawn: u64,
}
struct Active {
    entity: Entity,
    source: String,
    caret: usize,
    list: Completions,
    selected: usize,
    index_revision: u64,
}
impl CompletionState {
    pub(crate) fn preview(&mut self, entity: Entity) {
        self.trigger = Some((entity, true));
    }
    pub(crate) fn is_open(&self, entity: Entity) -> bool {
        self.active.as_ref().is_some_and(|a| a.entity == entity)
    }
    fn close(&mut self) {
        if self.active.take().is_some() {
            self.version += 1;
        }
        self.accept = false;
    }
}

pub(crate) fn prepare(
    mut state: ResMut<CompletionState>,
    keys: Res<ButtonInput<KeyCode>>,
    buttons: Res<ButtonInput<MouseButton>>,
    focus: Option<Res<InputFocus>>,
    mut inputs: Query<(Entity, &mut EditableText), With<Editor>>,
    fonts: Option<ResMut<FontCx>>,
    layouts: Option<ResMut<LayoutCx>>,
) {
    if buttons.just_pressed(MouseButton::Left) && !state.accept {
        state.close();
        state.trigger = None;
    }
    let focused = focus.as_ref().and_then(|f| f.get());
    let Some((entity, mut input)) = focused.and_then(|e| inputs.get_mut(e).ok()) else {
        state.close();
        state.trigger = None;
        return;
    };
    if input.is_composing()
        || input.pending_paste.is_some()
        || input.pending_edits.iter().any(|e| {
            matches!(
                e,
                TextEdit::Paste | TextEdit::ImeCommit { .. } | TextEdit::ImeSetCompose { .. }
            )
        })
    {
        state.close();
        state.trigger = None;
        return;
    }
    let ctrl = keys.any_pressed([KeyCode::ControlLeft, KeyCode::ControlRight]);
    let plain = !ctrl
        && !keys.any_pressed([
            KeyCode::AltLeft,
            KeyCode::AltRight,
            KeyCode::ShiftLeft,
            KeyCode::ShiftRight,
        ]);
    if ctrl && keys.just_pressed(KeyCode::Space) {
        state.trigger = Some((entity, true));
    }
    if state.is_open(entity) {
        // Native queued motions include OS key repeats; just_pressed does not.
        let mut motions: Vec<_> = input
            .pending_edits
            .iter()
            .filter_map(|edit| match edit {
                TextEdit::Down(false) => Some(1),
                TextEdit::Up(false) => Some(-1),
                _ => None,
            })
            .collect();
        if motions.is_empty() {
            if keys.just_pressed(KeyCode::ArrowDown) {
                motions.push(1);
            } else if keys.just_pressed(KeyCode::ArrowUp) {
                motions.push(-1);
            }
        }
        let navigation = plain && !motions.is_empty();
        let accept = state.accept || (plain && keys.just_pressed(KeyCode::Enter));
        let dismiss = keys.just_pressed(KeyCode::Escape);
        let compatible = input.pending_edits.iter().all(|e| {
            matches!(
                e,
                TextEdit::Up(false) | TextEdit::Down(false) | TextEdit::CollapseSelection
            ) || matches!(e,TextEdit::Insert(s) if s == "\n")
        });
        if compatible && (navigation || accept || dismiss) {
            input.pending_edits.retain(|e| {
                !matches!(
                    e,
                    TextEdit::Up(false) | TextEdit::Down(false) | TextEdit::CollapseSelection
                ) && !matches!(e,TextEdit::Insert(s) if s == "\n")
            });
            if dismiss {
                state.close();
                state.trigger = None;
                return;
            }
            if navigation && let Some(active) = state.active.as_mut() {
                for direction in motions {
                    active.selected = if direction > 0 {
                        (active.selected + 1).min(active.list.items.len().saturating_sub(1))
                    } else {
                        active.selected.saturating_sub(1)
                    };
                }
                state.version += 1;
            }
            if accept {
                if let (Some(active), Some(mut fonts), Some(mut layouts)) =
                    (state.active.as_ref(), fonts, layouts)
                {
                    let selection = input.editor().raw_selection();
                    if input.value() == active.source.as_str()
                        && selection.anchor().index() == active.caret
                        && selection.focus().index() == active.caret
                        && let Some(item) = active.list.items.get(active.selected)
                    {
                        input
                            .editor_mut()
                            .driver(&mut fonts.context, &mut layouts.0)
                            .select_byte_range(active.list.range.start, active.list.range.end);
                        input.queue_edit(TextEdit::Insert(item.insert.clone().into()));
                    }
                }
                state.close();
                state.trigger = None;
                return;
            }
        } else if navigation || accept {
            state.close();
        }
    }
    let typing = input
        .pending_edits
        .iter()
        .any(|e| matches!(e,TextEdit::Insert(s) if !s.contains(['\n','\r'])));
    let backspace = state.is_open(entity)
        && input
            .pending_edits
            .iter()
            .any(|e| matches!(e, TextEdit::Backspace));
    if !ctrl && (typing || backspace) && state.trigger.is_none() {
        state.trigger = Some((entity, false));
    }
}

type CompletionInput<'a> = (
    Entity,
    &'a EditableText,
    Option<&'a TextLayoutInfo>,
    Option<&'a ComputedNode>,
    Option<&'a TextScroll>,
);

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct ProjectContext<'w> {
    session: Res<'w, crate::application::Session>,
    index: Res<'w, crate::application::symbols::ProjectSymbols>,
}
pub(crate) fn refresh(
    mut commands: Commands,
    mut state: ResMut<CompletionState>,
    focus: Option<Res<InputFocus>>,
    inputs: Query<CompletionInput<'static>, With<Editor>>,
    mut popups: Query<&mut Node, Without<Editor>>,
    windows: Query<&Window>,
    project: ProjectContext,
) {
    let ProjectContext { session, index } = project;
    let focused = focus.as_ref().and_then(|f| f.get());
    if !windows.is_empty() && !windows.iter().any(|w| w.focused) {
        state.close();
        state.trigger = None;
    }
    // A completed scan refreshes an open popup only while its source and caret
    // still match. Never reopen a dismissed popup or move the editing caret.
    if state.trigger.is_none()
        && let Some(active) = &state.active
        && active.index_revision != index.revision
        && focused == Some(active.entity)
        && inputs.get(active.entity).is_ok_and(|(_, input, _, _, _)| {
            input.value() == active.source.as_str()
                && input.editor().raw_selection().focus().index() == active.caret
                && input.editor().raw_selection().anchor().index() == active.caret
        })
    {
        state.trigger = Some((active.entity, true));
    }
    if let Some((entity, explicit)) = state.trigger.take() {
        if let Ok((_, input, _, _, _)) = inputs.get(entity) {
            let selection = input.editor().raw_selection();
            let caret = selection.focus().index();
            let source = input.value().to_string();
            let external = session
                .workspace
                .active_id()
                .and_then(|id| session.workspace.document(id).ok())
                .map_or_else(Vec::new, |d| index.suggestions(d.path()));
            let list = complete_with_symbols(&source, caret, &external);
            if focused == Some(entity)
                && !input.is_composing()
                && selection.anchor().index() == caret
                && let Some(list) = list
                && !list.items.is_empty()
                && (explicit || source[list.range.clone()].chars().count() >= 2)
            {
                state.active = Some(Active {
                    entity,
                    source,
                    caret,
                    list,
                    selected: 0,
                    index_revision: index.revision,
                });
                state.version += 1;
            } else {
                state.close();
            }
        } else {
            state.close();
        }
    }
    if let Some(active) = &state.active {
        let valid = inputs.get(active.entity).is_ok_and(|(_, input, _, _, _)| {
            active.index_revision == index.revision
                && focused == Some(active.entity)
                && !input.is_composing()
                && input.value() == active.source.as_str()
                && input.editor().raw_selection().focus().index() == active.caret
                && input.editor().raw_selection().anchor().index() == active.caret
        });
        if !valid {
            state.close();
        }
    }
    if state.version != state.drawn {
        if let Some(popup) = state.popup.take()
            && let Ok(mut popup) = commands.get_entity(popup)
        {
            popup.despawn();
        }
        state.drawn = state.version;
        if let Some(active) = &state.active {
            let (position, size) = inputs
                .get(active.entity)
                .ok()
                .and_then(|(_, _, layout, node, scroll)| {
                    Some(popup_bounds(
                        layout?,
                        node?,
                        scroll,
                        active.list.items.len(),
                    ))
                })
                .unwrap_or((Vec2::ZERO, Vec2::new(360., 240.)));
            let popup = scenemax_ide_ui::completion::suggestion_popup(
                &mut commands,
                active.entity,
                position,
                size,
            );
            let start = active.selected.saturating_sub(7);
            for (index, item) in active.list.items.iter().enumerate().skip(start).take(8) {
                let editor = active.entity;
                let version = state.version;
                let row = scenemax_ide_ui::completion::suggestion_row(
                    &mut commands,
                    popup,
                    &item.label,
                    item.category,
                    index == active.selected,
                    CompletionRow,
                );
                commands.entity(row).observe(
                    move |mut event: On<Pointer<Press>>,
                          mut state: ResMut<CompletionState>,
                          mut focus: ResMut<InputFocus>| {
                        event.propagate(false);
                        if state.version == version
                            && let Some(active) = state.active.as_mut()
                        {
                            active.selected = index;
                            state.accept = true;
                            focus.set(editor, FocusCause::Navigated);
                        }
                    },
                );
            }
            commands.spawn((
                scenemax_ide_ui::label(
                    format!(
                        "↑↓ Select   Enter / Tab Insert   Esc Close\n{}",
                        index.coverage()
                    ),
                    11.,
                ),
                Node {
                    padding: px(6.).all(),
                    ..default()
                },
                ChildOf(popup),
            ));
            state.popup = Some(popup);
        }
    }
    if let (Some(active), Some(popup)) = (&state.active, state.popup)
        && let Ok((_, _, Some(layout), Some(node), scroll)) = inputs.get(active.entity)
        && let Some((_, cursor)) = layout.cursor
        && let Ok(mut popup_node) = popups.get_mut(popup)
    {
        let (position, size) = popup_bounds(layout, node, scroll, active.list.items.len());
        let left = px(position.x);
        let top = px(position.y);
        let width = size.x;
        let height = size.y;
        let point = (cursor.max - scroll.map_or(Vec2::ZERO, |s| s.0)) * node.inverse_scale_factor();
        let viewport = node.content_box().size() * node.inverse_scale_factor();
        let display =
            if point.y < 0. || point.y > viewport.y || point.x < 0. || point.x > viewport.x {
                Display::None
            } else {
                Display::Flex
            };
        if popup_node.left != left
            || popup_node.top != top
            || popup_node.width != px(width)
            || popup_node.max_height != px(height)
            || popup_node.display != display
        {
            popup_node.left = left;
            popup_node.top = top;
            popup_node.width = px(width);
            popup_node.max_height = px(height);
            popup_node.display = display;
        }
    }
}
#[derive(Component)]
struct CompletionRow;

fn popup_bounds(
    layout: &TextLayoutInfo,
    node: &ComputedNode,
    scroll: Option<&TextScroll>,
    count: usize,
) -> (Vec2, Vec2) {
    let scale = node.inverse_scale_factor();
    let cursor = layout.cursor.map_or(Vec2::ZERO, |(_, r)| r.max);
    let point = (cursor - scroll.map_or(Vec2::ZERO, |s| s.0)) * scale + Vec2::splat(16.);
    let viewport = node.size() * scale;
    let size = Vec2::new(
        360_f32.min(viewport.x.max(0.)),
        ((count.min(8) * 26 + 44) as f32).min(viewport.y.max(0.)),
    );
    (
        Vec2::new(
            point.x.clamp(0., (viewport.x - size.x).max(0.)),
            if point.y + size.y <= viewport.y {
                point.y
            } else {
                (point.y - size.y - 22.).max(0.)
            },
        ),
        size,
    )
}
