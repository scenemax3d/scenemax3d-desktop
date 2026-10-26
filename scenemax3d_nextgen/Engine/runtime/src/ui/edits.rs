//! Editable widget state; focus, caret and clipboard remain local to each client.
use super::*;
use scenemax_ui_input::Interaction;
pub(crate) mod geometry;
use bevy::input::{
    ButtonState,
    keyboard::{Key, KeyboardInput},
};
use serde::{Deserialize, Serialize};
#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct Snapshot {
    text: String,
    placeholder: String,
    multiline: bool,
    background: String,
    focused: String,
    cursor: String,
    selection: String,
    color: String,
    font: Option<String>,
    size: f32,
}
#[derive(Component)]
pub(crate) struct EditControl {
    state: Snapshot,
    rendered_font: Option<String>,
    caret: usize,
    anchor: usize,
    focus: bool,
    dirty: bool,
    width: f32,
    height: f32,
    scale: f32,
    visuals: Vec<Entity>,
    geometry: geometry::Geometry,
    adornments: Vec<Entity>,
    overlay_dirty: bool,
    text_entity: Option<Entity>,
}
pub(crate) fn canonical(property: &str) -> Option<&'static str> {
    Some(match property.to_ascii_lowercase().as_str() {
        "text" | "value" => "text",
        "placeholder" | "edittextplaceholder" => "placeholder",
        "multiline" | "edittextmultiline" => "multiline",
        "backgroundcolor" | "edittextbackgroundcolor" => "backgroundcolor",
        "focusedcolor" | "edittextfocusedcolor" => "focusedcolor",
        "cursorcolor" | "edittextcursorcolor" => "cursorcolor",
        "selectioncolor" | "edittextselectioncolor" => "selectioncolor",
        "color" | "textcolor" => "textcolor",
        "font" | "fontname" => "font",
        "fontsize" => "fontsize",
        _ => return None,
    })
}
impl Snapshot {
    pub(crate) fn valid(&self) -> bool {
        self.text.len() <= 65536
            && self.placeholder.len() <= 65536
            && self.size.is_finite()
            && (1. ..=4096.).contains(&self.size)
            && [
                &self.background,
                &self.focused,
                &self.cursor,
                &self.selection,
                &self.color,
            ]
            .into_iter()
            .all(|s| properties::color(s).is_some())
    }
    fn change(&mut self, property: &str, value: &str) -> bool {
        match property {
            "text" => self.text = value.into(),
            "placeholder" => self.placeholder = value.into(),
            "multiline" => self.multiline = value.eq_ignore_ascii_case("true") || value == "1",
            "backgroundcolor" => self.background = value.into(),
            "focusedcolor" => self.focused = value.into(),
            "cursorcolor" => self.cursor = value.into(),
            "selectioncolor" => self.selection = value.into(),
            "textcolor" => self.color = value.into(),
            "font" => self.font = Some(value.into()),
            "fontsize" => {
                let Ok(n) = value.trim().parse::<f32>() else {
                    return false;
                };
                self.size = n;
            }
            _ => return false,
        }
        if !self.multiline {
            self.text = self.text.replace(['\n', '\r'], " ");
        }
        self.valid()
    }
}
impl EditControl {
    fn replace_selection(&mut self, text: &str) {
        let mut chars: Vec<_> = self.state.text.chars().collect();
        let start = self.caret.min(self.anchor).min(chars.len());
        let end = self.caret.max(self.anchor).min(chars.len());
        let text: Vec<_> = text
            .chars()
            .filter(|c| !c.is_control() || (*c == '\n' && self.state.multiline))
            .collect();
        if chars.len() - end + start + text.len() > 16384 {
            return;
        }
        self.caret = start + text.len();
        self.anchor = self.caret;
        chars.splice(start..end, text);
        self.state.text = chars.into_iter().collect();
        self.dirty = true;
    }
    fn selected_text(&self) -> String {
        self.state
            .text
            .chars()
            .skip(self.caret.min(self.anchor))
            .take(self.caret.abs_diff(self.anchor))
            .collect()
    }
    fn word(&self, forward: bool) -> usize {
        let chars: Vec<_> = self.state.text.chars().collect();
        let mut i = self.caret.min(chars.len());
        if forward {
            while i < chars.len() && !chars[i].is_whitespace() {
                i += 1;
            }
            while i < chars.len() && chars[i].is_whitespace() {
                i += 1;
            }
        } else {
            i = i.saturating_sub(1);
            while i > 0 && chars[i].is_whitespace() {
                i -= 1;
            }
            while i > 0 && !chars[i - 1].is_whitespace() {
                i -= 1;
            }
        }
        i
    }
    fn key(&mut self, key: &Key, text: Option<&str>, shift: bool, control: bool) {
        let len = self.state.text.chars().count();
        let move_to = match key {
            Key::ArrowLeft if control => Some(self.word(false)),
            Key::ArrowRight if control => Some(self.word(true)),
            Key::ArrowUp => Some(self.geometry.vertical(self.caret, -1)),
            Key::ArrowDown => Some(self.geometry.vertical(self.caret, 1)),
            Key::ArrowLeft => Some(if !shift && self.caret != self.anchor {
                self.caret.min(self.anchor)
            } else {
                self.caret.saturating_sub(1)
            }),
            Key::ArrowRight => Some(if !shift && self.caret != self.anchor {
                self.caret.max(self.anchor)
            } else {
                (self.caret + 1).min(len)
            }),
            Key::Home => Some(
                self.state
                    .text
                    .chars()
                    .take(self.caret)
                    .enumerate()
                    .filter(|(_, c)| *c == '\n')
                    .map(|(i, _)| i + 1)
                    .last()
                    .unwrap_or(0),
            ),
            Key::End => Some(
                self.caret
                    + self
                        .state
                        .text
                        .chars()
                        .skip(self.caret)
                        .take_while(|c| *c != '\n')
                        .count(),
            ),
            _ => None,
        };
        if let Some(index) = move_to {
            self.caret = index;
            if !shift {
                self.anchor = index;
            }
            self.dirty = true;
            return;
        }
        match key {
            Key::Character(s) if control && s.eq_ignore_ascii_case("a") => {
                self.anchor = 0;
                self.caret = len;
                self.dirty = true;
            }
            Key::Backspace => {
                if self.caret == self.anchor {
                    self.anchor = if control {
                        self.word(false)
                    } else {
                        self.caret.saturating_sub(1)
                    };
                }
                self.replace_selection("");
            }
            Key::Delete => {
                if self.caret == self.anchor {
                    self.anchor = if control {
                        self.word(true)
                    } else {
                        (self.caret + 1).min(len)
                    };
                }
                self.replace_selection("");
            }
            Key::Enter if self.state.multiline => self.replace_selection("\n"),
            _ if !control => {
                if let Some(text) = text {
                    self.replace_selection(text);
                }
            }
            _ => {}
        }
    }
}
pub(super) fn insert(
    commands: &mut Commands,
    entity: Entity,
    widget: &SceneMaxUiWidgetDef,
    width: f32,
    height: f32,
    scale: f32,
) {
    let state = Snapshot {
        text: widget.text.clone(),
        placeholder: widget.edit_text_placeholder.clone(),
        multiline: widget.edit_text_multiline,
        background: widget.edit_text_background_color.clone(),
        focused: widget.edit_text_focused_color.clone(),
        cursor: widget.edit_text_cursor_color.clone(),
        selection: widget.edit_text_selection_color.clone(),
        color: widget.text_color.clone(),
        font: widget.font_name.clone(),
        size: widget.font_size,
    };
    let caret = state.text.chars().count();
    commands.entity(entity).insert((
        Interaction::default(),
        bevy::ui::RelativeCursorPosition::default(),
        EditControl {
            state,
            rendered_font: None,
            caret,
            anchor: caret,
            focus: false,
            dirty: true,
            width,
            height,
            scale,
            visuals: vec![],
            geometry: geometry::Geometry::default(),
            adornments: vec![],
            overlay_dirty: true,
            text_entity: None,
        },
    ));
}
pub(crate) fn snapshot(world: &World, entity: Entity) -> Option<Snapshot> {
    Some(world.get::<EditControl>(entity)?.state.clone())
}
pub(super) fn restore(world: &mut World, entity: Entity, state: Snapshot) {
    if !state.valid() {
        return;
    }
    if let Some(mut edit) = world.get_mut::<EditControl>(entity) {
        edit.state = state;
        edit.caret = edit.caret.min(edit.state.text.chars().count());
        edit.anchor = edit.caret;
        edit.dirty = true;
    }
}
pub(super) fn apply(world: &mut World, entity: Entity, property: &str, value: &str) -> bool {
    let Some(mut state) = snapshot(world, entity) else {
        return false;
    };
    if !state.change(property, value) {
        return false;
    }
    restore(world, entity, state);
    true
}
pub(crate) fn input(
    mut events: MessageReader<KeyboardInput>,
    keys: Res<ButtonInput<KeyCode>>,
    mouse: Res<ButtonInput<MouseButton>>,
    mut edits: Query<(
        Entity,
        &Interaction,
        &mut EditControl,
        Option<&bevy::ui::RelativeCursorPosition>,
    )>,
    mut clipboard: Option<ResMut<bevy::clipboard::Clipboard>>,
    runtime: Res<SceneMaxUiRuntime>,
    mut queue: ResMut<SceneMaxUiActionQueue>,
    mut commands: Commands,
) {
    let shift = keys.pressed(KeyCode::ShiftLeft) || keys.pressed(KeyCode::ShiftRight);
    let control = keys.pressed(KeyCode::ControlLeft)
        || keys.pressed(KeyCode::ControlRight)
        || keys.pressed(KeyCode::SuperLeft)
        || keys.pressed(KeyCode::SuperRight);
    let pressed = edits
        .iter()
        .find(|(_, i, _, _)| **i == Interaction::Pressed)
        .map(|(e, _, _, _)| e);
    if mouse.just_pressed(MouseButton::Left) {
        if std::env::var_os("SCENEMAX_ATTACHMENT_AUDIT").is_some() {
            write_runtime_diagnostic_line(format!("UI_EDIT_CLICK target={pressed:?}"));
        }
        for (entity, _, mut edit, cursor) in &mut edits {
            let focus = Some(entity) == pressed;
            if focus {
                if let Some(pos) = cursor.and_then(|c| c.normalized) {
                    let index = edit.geometry.nearest(
                        (pos + Vec2::splat(0.5)) * Vec2::new(edit.width, edit.height)
                            - Vec2::new(5., 4.),
                    );
                    edit.caret = index;
                    if !shift {
                        edit.anchor = index;
                    }
                    edit.dirty = true;
                }
            }
            if focus != edit.focus {
                edit.focus = focus;
                edit.dirty = true;
            }
        }
    }
    queue.input_focused = edits.iter().any(|(_, _, e, _)| e.focus);
    for event in events.read() {
        if event.state != ButtonState::Pressed {
            continue;
        }
        for (entity, _, mut edit, _) in &mut edits {
            if !edit.focus {
                continue;
            }
            let old = edit.state.text.clone();
            match &event.logical_key {
                Key::Character(key)
                    if control
                        && (key.eq_ignore_ascii_case("c") || key.eq_ignore_ascii_case("x")) =>
                {
                    if edit.caret != edit.anchor {
                        if let Some(clipboard) = clipboard.as_mut() {
                            if clipboard.set_text(edit.selected_text()).is_ok()
                                && key.eq_ignore_ascii_case("x")
                            {
                                edit.replace_selection("");
                            }
                        }
                    }
                }
                Key::Character(key) if control && key.eq_ignore_ascii_case("v") => {
                    if let Some(clipboard) = clipboard.as_mut() {
                        if let Some(Ok(text)) = clipboard.fetch_text().poll_result() {
                            edit.replace_selection(&text);
                        }
                    }
                }
                _ => edit.key(&event.logical_key, event.text.as_deref(), shift, control),
            }
            if old != edit.state.text {
                for loaded in runtime.loaded.values() {
                    for (path, target) in &loaded.targets {
                        if target.entity == entity && target.multiplayer {
                            let path = path.clone();
                            commands.queue(move |world: &mut World| {
                                multiplayer::widgets::capture_edit(world, entity, &path)
                            });
                        }
                    }
                }
            }
        }
    }
}
// Publish actual content, never placeholder text, before script key handlers read it.
// Both explicit UI names and the active UI shorthand use the same typed value.
pub(crate) fn publish_values(
    runtime: Res<SceneMaxUiRuntime>,
    edits: Query<&EditControl>,
    mut vars: ResMut<SceneMaxVars>,
    mut previous: Local<std::collections::HashSet<String>>,
) {
    let mut current = std::collections::HashSet::new();
    for (name, loaded) in &runtime.loaded {
        for (path, target) in &loaded.targets {
            let Ok(edit) = edits.get(target.entity) else {
                continue;
            };
            let mut paths = vec![format!("UI.{path}")];
            if runtime.active_ui_name.as_ref() == Some(name) {
                if let Some(relative) = path.strip_prefix(&format!("{name}.")) {
                    paths.push(format!("UI.{relative}"));
                }
            }
            for path in paths {
                for property in ["text", "value"] {
                    let key = format!("{path}.{property}");
                    scenemax_runtime_vm_core::typed::store(
                        &mut vars,
                        &key,
                        serde_json::json!(edit.state.text),
                    );
                    current.insert(key);
                }
            }
        }
    }
    for key in previous.difference(&current) {
        vars.0.remove(key);
        vars.1.remove(key);
        vars.2.remove(key);
    }
    *previous = current;
}

pub(crate) fn render(
    mut commands: Commands,
    server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    mut runtime: ResMut<SceneMaxUiRuntime>,
    mut edits: Query<(Entity, &mut EditControl, &SceneMaxUiWidget)>,
) {
    for (owner, mut edit, marker) in &mut edits {
        if !edit.dirty {
            continue;
        }
        edit.dirty = false;
        edit.overlay_dirty = true;
        for visual in edit.visuals.drain(..) {
            commands.entity(visual).try_despawn();
        }
        let s = edit.state.clone();
        if edit.rendered_font != s.font {
            write_runtime_diagnostic_line(format!(
                "UI:FONT name={} target={owner:?}",
                s.font.as_deref().unwrap_or("")
            ));
            edit.rendered_font = s.font.clone();
        }
        commands
            .entity(owner)
            .insert(BackgroundColor(parse_ui_color(if edit.focus {
                &s.focused
            } else {
                &s.background
            })));
        let placeholder = s.text.is_empty();
        let visible = if placeholder {
            s.placeholder.clone()
        } else {
            s.text.clone()
        };
        let color = parse_ui_color(&s.color).with_alpha(if placeholder { 0.5 } else { 1. });
        let text = spawn_scenemax_ui_text_entity(
            &mut commands,
            &server,
            &context,
            &mut runtime,
            "SceneMax edit text".into(),
            Node {
                position_type: PositionType::Absolute,
                left: Val::Px(5.),
                top: Val::Px(4.),
                width: Val::Px((edit.width - 10.).max(1.)),
                height: Val::Px((edit.height - 8.).max(1.)),
                overflow: Overflow::clip(),
                ..default()
            },
            Visibility::Inherited,
            ZIndex(1),
            visible,
            s.font.as_deref(),
            s.size,
            color,
            Justify::Left,
            edit.scale,
            edit.width - 10.,
            edit.height - 8.,
            None,
        );
        commands.entity(text).insert(bevy::ui::FocusPolicy::Pass);
        add_child_if_alive(&mut commands, owner, text);
        edit.visuals.push(text);
        edit.text_entity = Some(text);
        for loaded in runtime.loaded.values_mut() {
            for target in loaded.targets.values_mut() {
                if target.entity == owner {
                    target.text_entity = Some(text);
                }
            }
        }
        if std::env::var_os("SCENEMAX_ATTACHMENT_AUDIT").is_some() {
            write_runtime_diagnostic_line(format!(
                "UI_EDIT_STATE {}.{}.{} {}",
                marker.ui_name,
                marker.layer,
                marker.widget_path.join("."),
                serde_json::to_string(&s).unwrap_or_default()
            ));
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn fixture(world: &mut World) -> Entity {
        let widget: SceneMaxUiWidgetDef =
            serde_json::from_str(r#"{"name":"entry","type":"EDIT_TEXT"}"#).unwrap();
        let entity = world.spawn_empty().id();
        insert(&mut world.commands(), entity, &widget, 300., 100., 1.);
        world.flush();
        entity
    }
    #[test]
    fn edited_text_is_readable_by_scripts_and_unloaded_ui_does_not_leak_values() {
        let mut app = App::new();
        app.init_resource::<SceneMaxUiRuntime>()
            .init_resource::<SceneMaxVars>()
            .add_systems(Update, publish_values);
        let entity = fixture(app.world_mut());
        let mut loaded = LoadedSceneMaxUi::default();
        loaded.targets.insert(
            "form.layer1.entry".into(),
            SceneMaxUiTarget {
                entity,
                text_entity: None,
                multiplayer: false,
                widget_type: "EDIT_TEXT".into(),
                ui_scale: 1.,
            },
        );
        {
            let mut runtime = app.world_mut().resource_mut::<SceneMaxUiRuntime>();
            runtime.active_ui_name = Some("form".into());
            runtime.loaded.insert("form".into(), loaded);
        }
        app.world_mut()
            .get_mut::<EditControl>(entity)
            .unwrap()
            .replace_selection("Ada λ🙂");
        app.update();
        let program =
            scenemax_parser::parse_program("shared var chosen = UI.layer1.entry.text").unwrap();
        {
            let mut vars = app.world_mut().resource_mut::<SceneMaxVars>();
            scenemax_runtime_vm_core::apply_initial_assignments(&program, &mut vars);
            assert_eq!(
                scenemax_runtime_vm_core::typed::lookup("chosen", &vars, None),
                Some(serde_json::json!("Ada λ🙂"))
            );
            assert_eq!(
                scenemax_runtime_vm_core::typed::lookup("UI.form.layer1.entry.value", &vars, None),
                Some(serde_json::json!("Ada λ🙂"))
            );
        }
        assert!(apply(app.world_mut(), entity, "text", ""));
        app.update();
        assert_eq!(
            scenemax_runtime_vm_core::typed::lookup(
                "UI.layer1.entry.text",
                app.world().resource::<SceneMaxVars>(),
                None
            ),
            Some(serde_json::json!(""))
        );
        app.world_mut()
            .resource_mut::<SceneMaxUiRuntime>()
            .loaded
            .clear();
        app.update();
        let vars = app.world().resource::<SceneMaxVars>();
        assert!(
            scenemax_runtime_vm_core::typed::lookup("UI.layer1.entry.text", vars, None).is_none()
        );
        assert_eq!(
            scenemax_runtime_vm_core::typed::lookup("chosen", vars, None),
            Some(serde_json::json!("Ada λ🙂"))
        );
        let mut vars = app.world_mut().resource_mut::<SceneMaxVars>();
        crate::actions::retain_scene_switch_shared_vars(
            &mut vars,
            Some(&program),
            &mut std::collections::HashSet::new(),
        );
        assert_eq!(
            scenemax_runtime_vm_core::typed::lookup("chosen", &vars, None),
            Some(serde_json::json!("Ada λ🙂"))
        );
    }
    #[test]
    fn focused_field_consumes_keyboard_input_and_clicking_outside_releases_it() {
        let mut app = App::new();
        app.init_resource::<ButtonInput<KeyCode>>()
            .init_resource::<ButtonInput<MouseButton>>()
            .init_resource::<SceneMaxUiRuntime>()
            .init_resource::<SceneMaxUiActionQueue>()
            .add_message::<KeyboardInput>()
            .add_systems(Update, input);
        let entity = fixture(app.world_mut());
        app.world_mut()
            .entity_mut(entity)
            .insert(Interaction::Pressed);
        app.world_mut()
            .resource_mut::<ButtonInput<MouseButton>>()
            .press(MouseButton::Left);
        app.update();
        assert!(
            app.world()
                .resource::<SceneMaxUiActionQueue>()
                .input_focused
        );
        app.world_mut()
            .resource_mut::<ButtonInput<MouseButton>>()
            .reset_all();
        let event = KeyboardInput {
            key_code: KeyCode::KeyA,
            logical_key: Key::Character("a".into()),
            state: ButtonState::Pressed,
            text: Some("a".into()),
            repeat: false,
            window: entity,
        };
        app.world_mut().write_message(event.clone());
        app.update();
        assert_eq!(snapshot(app.world(), entity).unwrap().text, "a");
        app.world_mut().entity_mut(entity).insert(Interaction::None);
        app.world_mut()
            .resource_mut::<ButtonInput<MouseButton>>()
            .press(MouseButton::Left);
        app.update();
        assert!(
            !app.world()
                .resource::<SceneMaxUiActionQueue>()
                .input_focused
        );
        app.world_mut().write_message(event);
        app.update();
        assert_eq!(snapshot(app.world(), entity).unwrap().text, "a");
    }
    #[test]
    fn unicode_editing_selection_deletion_and_multiline_obey_character_boundaries() {
        let mut world = World::new();
        let entity = fixture(&mut world);
        let mut edit = world.get_mut::<EditControl>(entity).unwrap();
        edit.replace_selection("Aλ🙂");
        edit.key(&Key::ArrowLeft, None, false, false);
        edit.key(&Key::Backspace, None, false, false);
        assert_eq!(edit.state.text, "A🙂");
        edit.key(&Key::Character("a".into()), None, false, true);
        edit.replace_selection("replaced");
        assert_eq!(edit.state.text, "replaced");
        edit.key(&Key::Enter, None, false, false);
        assert_eq!(edit.state.text, "replaced");
        edit.state.multiline = true;
        edit.key(&Key::Enter, None, false, false);
        edit.replace_selection("second");
        assert_eq!(edit.state.text, "replaced\nsecond");
        edit.key(&Key::Home, None, false, false);
        edit.key(&Key::Delete, None, false, false);
        assert_eq!(edit.state.text, "replaced\necond");
    }
    #[test]
    fn word_navigation_deletion_and_selection_preserve_unicode() {
        let mut world = World::new();
        let entity = fixture(&mut world);
        let mut edit = world.get_mut::<EditControl>(entity).unwrap();
        edit.replace_selection("one λambda  third");
        edit.key(&Key::ArrowLeft, None, false, true);
        assert_eq!(edit.caret, 12);
        edit.key(&Key::ArrowLeft, None, true, true);
        assert_eq!(edit.selected_text(), "λambda  ");
        edit.key(&Key::Delete, None, false, true);
        assert_eq!(edit.state.text, "one third");
        edit.key(&Key::ArrowRight, None, false, true);
        assert_eq!(edit.caret, 9);
        edit.key(&Key::Backspace, None, false, true);
        assert_eq!(edit.state.text, "one ");
        edit.caret = 0;
        edit.anchor = 0;
        edit.key(&Key::Delete, None, false, true);
        assert_eq!(edit.state.text, "");
    }
}
