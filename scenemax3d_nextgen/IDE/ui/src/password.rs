//! Retained masked input; plaintext never enters a renderable text component.
use crate::theme::*;
use bevy::{
    input::{ButtonState, keyboard::KeyboardInput},
    input_focus::{FocusedInput, InputFocus, tab_navigation::TabIndex},
    prelude::*,
};

/// Session-only secret input, deliberately neither Debug nor Reflect nor serializable.
#[derive(Component, Default)]
pub struct Password {
    value: String,
    cursor: usize,
    anchor: usize,
    touched: bool,
}
impl Password {
    /// Current secret, for the owning application's transient credential path only.
    pub fn value(&self) -> &str {
        &self.value
    }
    /// Whether the user explicitly edited the initial value, including clearing it.
    pub fn touched(&self) -> bool {
        self.touched
    }
    fn replace(&mut self, value: &str) {
        let mut chars: Vec<char> = self.value.chars().collect();
        let range = self.anchor.min(self.cursor)..self.anchor.max(self.cursor);
        let inserted: Vec<char> = value.chars().filter(|c| !c.is_control()).collect();
        let mut candidate = chars.clone();
        candidate.splice(range.clone(), inserted.iter().copied());
        if candidate.iter().map(|c| c.len_utf8()).sum::<usize>() > 1024 {
            return;
        }
        chars.splice(range.clone(), inserted.iter().copied());
        self.value = chars.into_iter().collect();
        self.cursor = range.start + inserted.len();
        self.anchor = self.cursor;
        self.touched = true;
    }
    fn mask(&self, focused: bool) -> String {
        let mut result = String::new();
        for index in 0..=self.value.chars().count() {
            if focused && index == self.cursor {
                result.push('|');
            }
            if index < self.value.chars().count() {
                result.push('*');
            }
        }
        result
    }
}
/// Spawn a keyboard-editable masked field with native click/tab focus.
/// Supports Unicode text, paste, selection, arrows, Home/End and deletion.
/// Copy/cut and undo are intentionally unavailable for secrets.
pub fn input<T: Component>(c: &mut Commands, parent: Entity, marker: T, value: &str) -> Entity {
    let count = value.chars().count();
    c.spawn((
        marker,
        Password {
            value: value.into(),
            cursor: count,
            anchor: count,
            touched: false,
        },
        TabIndex(0),
        Text::new("*".repeat(count)),
        TextLayout::no_wrap(),
        TextFont {
            font_size: FontSize::Px(12.),
            ..default()
        },
        TextColor(INK),
        Node {
            width: percent(100.),
            height: px(26.),
            padding: UiRect::axes(px(6.), px(3.)),
            overflow: Overflow::clip(),
            ..default()
        },
        BackgroundColor(BG),
        ChildOf(parent),
    ))
    .observe(keyboard)
    .id()
}
fn keyboard(
    mut event: On<FocusedInput<KeyboardInput>>,
    keys: Res<ButtonInput<KeyCode>>,
    mut fields: Query<&mut Password>,
    mut clipboard: ResMut<bevy::clipboard::Clipboard>,
) {
    if event.input.state != ButtonState::Pressed || event.input.key_code == KeyCode::Tab {
        return;
    }
    let Ok(mut field) = fields.get_mut(event.focused_entity) else {
        return;
    };
    let control = keys.any_pressed([
        KeyCode::ControlLeft,
        KeyCode::ControlRight,
        KeyCode::SuperLeft,
        KeyCode::SuperRight,
    ]);
    let shift = keys.any_pressed([KeyCode::ShiftLeft, KeyCode::ShiftRight]);
    let length = field.value.chars().count();
    match event.input.key_code {
        KeyCode::KeyA if control => {
            field.anchor = 0;
            field.cursor = length;
        }
        KeyCode::KeyV if control => {
            if let Some(Ok(value)) = clipboard.fetch_text().poll_result() {
                field.replace(&value);
            }
        }
        KeyCode::Backspace | KeyCode::Delete => {
            if field.cursor == field.anchor {
                if event.input.key_code == KeyCode::Backspace {
                    field.anchor = field.cursor.saturating_sub(1);
                } else {
                    field.anchor = (field.cursor + 1).min(length);
                }
            }
            field.replace("");
        }
        KeyCode::ArrowLeft | KeyCode::ArrowRight | KeyCode::Home | KeyCode::End => {
            field.cursor = match event.input.key_code {
                KeyCode::Home => 0,
                KeyCode::End => length,
                KeyCode::ArrowLeft if !shift && field.cursor != field.anchor => field.cursor.min(field.anchor),
                KeyCode::ArrowRight if !shift && field.cursor != field.anchor => field.cursor.max(field.anchor),
                KeyCode::ArrowLeft => field.cursor.saturating_sub(1),
                _ => (field.cursor + 1).min(length),
            };
            if !shift {
                field.anchor = field.cursor;
            }
        }
        _ if !control => {
            if let Some(value) = &event.input.text {
                if value.chars().any(|c| !c.is_control()) { field.replace(value); }
            }
        }
        _ => {}
    }
    event.propagate(false);
}
pub(crate) fn render(
    mut fields: Query<(Entity, &Password, &mut Text, &mut BackgroundColor)>,
    focus: Res<InputFocus>,
) {
    for (entity, field, mut text, mut background) in &mut fields {
        let focused = focus.get() == Some(entity);
        let mask = field.mask(focused);
        if text.0 != mask {
            text.0 = mask;
        }
        background.0 = if focused {
            Color::srgb(0.24, 0.27, 0.29)
        } else {
            BG
        };
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn focused_keyboard_input_is_masked_and_enter_keeps_fallback() {
        use bevy::input::keyboard::Key;
        use bevy::input_focus::{FocusCause, InputFocusPlugin};
        let mut app = App::new();
        app.add_plugins((bevy::input::InputPlugin, InputFocusPlugin, bevy::input_focus::InputDispatchPlugin, bevy::clipboard::ClipboardPlugin));
        app.add_systems(Update, render);
        let window = app.world_mut().spawn((Window::default(), bevy::window::PrimaryWindow)).id();
        let field = app.world_mut().spawn((Password::default(), Text::default(), BackgroundColor(BG))).observe(keyboard).id();
        app.update();
        app.world_mut().resource_mut::<InputFocus>().set(field, FocusCause::Navigated);
        let send = |app: &mut App, key_code, logical_key, text: &str| {
            app.world_mut().write_message(KeyboardInput { key_code, logical_key, text: Some(text.into()), state: ButtonState::Pressed, repeat: false, window });
            app.update();
        };
        send(&mut app, KeyCode::Enter, Key::Enter, "\r");
        assert!(!app.world().get::<Password>(field).unwrap().touched());
        send(&mut app, KeyCode::KeyA, Key::Character("a".into()), "a");
        assert_eq!(app.world().get::<Password>(field).unwrap().value(), "a");
        assert_eq!(app.world().get::<Text>(field).unwrap().0, "*|");
        send(&mut app, KeyCode::Backspace, Key::Backspace, "");
        assert_eq!(app.world().get::<Password>(field).unwrap().value(), "");
        assert!(app.world().get::<Password>(field).unwrap().touched());
    }

    #[test]
    fn unicode_selection_clear_and_limits_never_render_plaintext() {
        let mut field = Password::default();
        field.replace("aλ😀");
        assert_eq!(field.value(), "aλ😀");
        assert_eq!(field.mask(true), "***|");
        field.anchor = 1;
        field.cursor = 3;
        field.replace("z");
        assert_eq!(field.value(), "az");
        field.replace(&"x".repeat(1024));
        assert_eq!(field.value(), "az");
        field.anchor = 0;
        field.cursor = 2;
        field.replace("");
        assert!(field.touched());
        assert_eq!(field.value(), "");
        assert_eq!(field.mask(false), "");
    }
}
