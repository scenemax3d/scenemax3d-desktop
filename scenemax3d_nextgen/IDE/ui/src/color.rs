//! Feathers color pickers paired with editable hexadecimal SceneMax values.
use bevy::{feathers::controls::*, prelude::*, text::EditableText, ui_widgets::ValueChange};

#[derive(Component)]
pub(crate) struct ColorEditor(Entity);

/// Color picker with alpha support and an editable hexadecimal value.
/// Returns the text field carrying the caller's marker.
pub fn input<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    value: &str,
) -> Entity {
    let row = commands
        .spawn((
            Node {
                width: percent(100.),
                min_width: px(0.),
                column_gap: px(6.),
                align_items: AlignItems::Center,
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    let initial = Srgba::hex(value).map(Color::from).unwrap_or(Color::WHITE);
    let picker = commands
        .spawn_scene(bsn! {
            @FeathersColorInput
            ColorInputValue(initial)
            ChildOf(row)
        })
        .id();
    let editor = crate::property::input(commands, row, marker, value);
    commands.entity(editor).insert(ColorEditor(picker));
    commands.entity(picker).observe(
        move |event: On<ValueChange<Color>>,
              mut commands: Commands,
              mut inputs: Query<&mut EditableText>| {
            if event.source != picker {
                return;
            }
            commands.entity(picker).insert(ColorInputValue(event.value));
            if let Ok(mut text) = inputs.get_mut(editor) {
                let rgba = event.value.to_srgba().to_u8_array();
                let value = if rgba[3] == 255 {
                    format!("#{:02X}{:02X}{:02X}", rgba[0], rgba[1], rgba[2])
                } else {
                    format!(
                        "#{:02X}{:02X}{:02X}{:02X}",
                        rgba[0], rgba[1], rgba[2], rgba[3]
                    )
                };
                text.editor_mut().set_text(&value);
            }
        },
    );
    editor
}

pub(crate) fn update(
    mut commands: Commands,
    inputs: Query<(&ColorEditor, &EditableText), Changed<EditableText>>,
    values: Query<&ColorInputValue>,
) {
    for (editor, text) in &inputs {
        if let Ok(color) = Srgba::hex(text.value().to_string())
            && let Ok(old) = values.get(editor.0)
            && old.0.to_srgba() != color
        {
            commands
                .entity(editor.0)
                .insert(ColorInputValue(color.into()));
        }
    }
}
