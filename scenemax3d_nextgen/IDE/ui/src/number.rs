//! Bevy numeric controls with SceneMax's existing editable-field bindings.
use bevy::{feathers::controls::*, prelude::*, text::EditableText, ui_widgets::ValueChange};

#[derive(Component)]
pub(crate) struct NumberEditor(Entity);

/// Draggable numeric input. Dragging uses the soft range; typed values remain unrestricted.
/// Returns the control container; the caller's marker is attached to its editable text child.
pub fn input<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    value: &str,
    min: f64,
    max: f64,
    step: f64,
) -> Entity {
    let initial = value.parse::<f64>().unwrap_or(0.);
    let precision = step
        .to_string()
        .split_once('.')
        .map_or(0, |(_, fraction)| fraction.len() as i32);
    let control = commands
        .spawn_scene(bsn! {
            @FeathersNumberInput
            NumberInputValue::F64(initial)
            SoftLimit::f64(min..=max)
            NumberInputStep(step)
            NumberInputPrecision(precision)
            Node { width: percent(100.), min_width: px(0.), height: px(26.), flex_shrink: 0. }
            ChildOf(parent)
        })
        .observe(|event: On<ValueChange<f64>>, mut commands: Commands| {
            if event.value.is_finite() {
                commands
                    .entity(event.source)
                    .insert(NumberInputValue::F64(event.value));
            }
        })
        .id();
    let value = value.to_owned();
    commands.queue(move |world: &mut World| {
        let editor = world.get::<Children>(control).and_then(|children| {
            children
                .iter()
                .find(|child| world.get::<EditableText>(*child).is_some())
        });
        if let Some(editor) = editor {
            world.entity_mut(editor).insert((
                marker,
                NumberEditor(control),
                crate::theme::TEXT_CURSOR_STYLE,
            ));
            if let Some(mut text) = world.get_mut::<EditableText>(editor) {
                text.pending_edits.clear();
                text.editor_mut().set_text(&value);
            }
        }
    });
    control
}

// Designers also update text after undo, preview scrubbing and model changes.
// Keep numeric state in sync so the next drag starts at the visible value.
pub(crate) fn update(
    mut commands: Commands,
    editors: Query<(&NumberEditor, &EditableText), Changed<EditableText>>,
    values: Query<&NumberInputValue>,
) {
    for (editor, text) in &editors {
        if let Ok(value) = text.value().to_string().parse::<f64>()
            && value.is_finite()
            && let Ok(NumberInputValue::F64(old)) = values.get(editor.0)
            && *old != value
        {
            commands
                .entity(editor.0)
                .insert(NumberInputValue::F64(value));
        }
    }
}
