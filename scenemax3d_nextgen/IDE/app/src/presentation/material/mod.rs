//! Native PBR material workbench hosted as an ordinary IDE document.
use scenemax_ui_input::Interaction;
pub(crate) mod preview;
mod view;
use super::components::EditorHost;
use crate::application::{
    Command, CommandQueue, Session,
    material::{Edit, MaterialLibrary},
};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::{DocumentId, DocumentRevision};
use scenemax_ide_ui::property::{Checked, Choice};
use serde_json::{Value, json};
#[derive(Component)]
pub(crate) struct MaterialHost;
#[derive(Component)]
pub(crate) struct Field(String, String);
#[derive(Component)]
struct TextureThumb(String);
#[derive(Component)]
struct PreviewModel(String);
#[derive(Component)]
enum Action {
    Refresh,
    Save,
    Undo,
    Redo,
    Preset(String),
    Shape(u8),
    Frame,
    Light,
    Rotate,
    Slot(String),
}
#[derive(Resource, Default)]
pub(crate) struct State {
    current: Option<(DocumentId, DocumentRevision)>,
    host: Option<Entity>,
    parts: Option<view::Parts>,
    data: Value,
    world: Option<Entity>,
    camera: Option<Entity>,
    object: Option<Entity>,
    material: Handle<StandardMaterial>,
    shape: u8,
    model: String,
    model_dirty: bool,
    rotating: bool,
    warm: bool,
    frame: bool,
    slots: Vec<String>,
    error: String,
    last_edit: Option<(String, Option<Entity>)>,
}
type Fields<'w, 's> = Query<
    'w,
    's,
    (
        &'static mut Field,
        Option<&'static mut EditableText>,
        Option<&'static mut Checked>,
        Option<&'static mut Choice>,
        Option<&'static Children>,
    ),
>;
type Buttons<'w, 's> = Query<'w, 's, (&'static Interaction, &'static Action), Changed<Interaction>>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Controls<'w, 's> {
    fields: Fields<'w, 's>,
    buttons: Buttons<'w, 's>,
    models: Query<
        'w,
        's,
        (
            &'static mut PreviewModel,
            &'static mut Choice,
            &'static Children,
        ),
        Without<Field>,
    >,
    focus: Option<Res<'w, bevy::input_focus::InputFocus>>,
    texts: Query<'w, 's, &'static mut Text>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
}
pub(crate) fn update(
    mut commands: Commands,
    session: Res<Session>,
    mut library: ResMut<MaterialLibrary>,
    mut state: ResMut<State>,
    hosts: Query<(Entity, &EditorHost), With<MaterialHost>>,
    mut queue: ResMut<CommandQueue>,
    mut controls: Controls,
) {
    let active = session
        .workspace
        .active_id()
        .and_then(|id| session.workspace.document(id).ok().map(|d| (id, d)));
    let target = active.and_then(|(id, doc)| {
        hosts
            .iter()
            .find(|(_, h)| h.0 == id)
            .map(|(host, _)| (host, id, doc))
    });
    if target.map(|t| t.1) != state.current.map(|s| s.0) {
        if let Some(world) = state.world.take() {
            commands.entity(world).try_despawn();
        }
        if let Some(host) = state.host.take()
            && hosts.contains(host)
        {
            commands.entity(host).despawn_children();
        }
        *state = State::default();
    }
    let Some((host, id, doc)) = target else {
        return;
    };
    let stamp = (id, doc.revision());
    state.host = Some(host);
    let parsed = serde_json::from_str::<Value>(doc.text())
        .map_err(|e| e.to_string())
        .and_then(|v| scenemax_assets::material::validate(&v).map(|_| v));
    let data = match parsed {
        Ok(value) => value,
        Err(error) => {
            state.parts = None;
            if let Some(world) = state.world.take() {
                commands.entity(world).try_despawn();
            }
            if state.current != Some(stamp) {
                commands.entity(host).despawn_children();
                commands.spawn((
                    scenemax_ide_ui::label(format!("Cannot open material: {error}"), 14.),
                    ChildOf(host),
                ));
            }
            state.current = Some(stamp);
            return;
        }
    };
    state.host = Some(host);
    let Some(assets) = library
        .assets
        .as_ref()
        .filter(|_| library.project == session.workspace.project().root())
    else {
        if state.error != library.error || state.current.is_none() {
            commands.entity(host).despawn_children();
            commands.spawn((scenemax_ide_ui::label(&library.error, 13.), ChildOf(host)));
            state.error = library.error.clone();
        }
        state.current = Some(stamp);
        return;
    };
    if state.parts.is_none() {
        commands.entity(host).despawn_children();
        let name = doc.path().file_stem().unwrap_or_default().to_string_lossy();
        state.parts = Some(view::build(&mut commands, host, &data, assets, &name));
        state.data = data.clone();
        state.error.clear();
        state.model_dirty = true;
    }
    if !controls.mouse.pressed(MouseButton::Left) || controls.mouse.just_pressed(MouseButton::Left)
    {
        state.last_edit = None;
    }
    let focus = controls.focus.as_ref().and_then(|f| f.get());
    if state.current != Some(stamp) {
        state.data = data.clone();
        for (mut field, text, checked, choice, children) in &mut controls.fields {
            let value = data.pointer(&field.0).cloned().unwrap_or(json!(""));
            let value = view::display(&value);
            // Restore retained controls for undo/redo and preset changes.
            if field.1 != value {
                if let Some(mut text) = text {
                    text.editor_mut().set_text(&value);
                }
                if let Some(mut checked) = checked {
                    checked.0 = value == "true";
                }
                if let Some(mut choice) = choice {
                    choice.0 = value.clone();
                    if let Some(children) = children {
                        for child in children.iter() {
                            if let Ok(mut text) = controls.texts.get_mut(child) {
                                text.0 =
                                    format!("{} ↓", if value.is_empty() { "None" } else { &value });
                            }
                        }
                    }
                }
                field.1 = value;
            }
        }
        state.current = Some(stamp);
    }
    let mut edit = None;
    for (interaction, action) in &controls.buttons {
        if *interaction != Interaction::Pressed {
            continue;
        }
        match action {
            Action::Save => queue.0.push_back(Command::Save),
            Action::Undo => queue
                .0
                .push_back(Command::Edit(crate::application::EditCommand::Undo)),
            Action::Redo => queue
                .0
                .push_back(Command::Edit(crate::application::EditCommand::Redo)),
            Action::Preset(p) => edit = Some(("preset".into(), json!(p))),
            Action::Slot(slot) => edit = Some(("/targetSlot".into(), json!(slot))),
            Action::Shape(shape) => {
                state.shape = *shape;
                state.model.clear();
                for (mut model, mut choice, children) in &mut controls.models {
                    model.0.clear();
                    choice.0.clear();
                    for child in children.iter() {
                        if let Ok(mut text) = controls.texts.get_mut(child) {
                            text.0 = "Preview primitive ↓".into();
                        }
                    }
                }
                state.model_dirty = true;
            }
            Action::Refresh => {
                library.project.clear();
                if let Some(world) = state.world.take() {
                    commands.entity(world).try_despawn();
                }
                state.parts = None;
                state.model_dirty = true;
                state.error.clear();
            }
            Action::Frame => state.frame = true,
            Action::Rotate => state.rotating = !state.rotating,
            Action::Light => state.warm = !state.warm,
        }
    }
    for (mut model, choice, _) in &mut controls.models {
        if model.0 != choice.0 {
            model.0 = choice.0.clone();
            state.model = choice.0.clone();
            state.model_dirty = true;
        }
    }
    if edit.is_none() {
        for (mut field, text, checked, choice, _) in &mut controls.fields {
            let value = text
                .map(|t| t.value().to_string())
                .or_else(|| checked.map(|c| c.0.to_string()))
                .or_else(|| choice.map(|c| c.0.clone()))
                .unwrap_or_default();
            if value == field.1 {
                continue;
            }
            field.1 = value.clone();
            let old = data.pointer(&field.0);
            let parsed = if old.is_some_and(Value::is_number) {
                value
                    .parse::<f64>()
                    .ok()
                    .filter(|n| n.is_finite())
                    .map(|n| json!(n))
            } else if old.is_some_and(Value::is_boolean) {
                Some(json!(value == "true"))
            } else {
                Some(json!(value))
            };
            if let Some(value) = parsed {
                edit = Some((field.0.clone(), value));
            } else {
                state.error = "Enter a valid number".into();
            }
            break;
        }
    }
    if let Some((pointer, value)) = edit {
        let valid = pointer == "preset"
            || scenemax_assets::material::patch(doc.text(), &pointer, value.clone())
                .map_err(|e| state.error = e)
                .is_ok();
        if valid {
            let continuing =
                pointer != "preset" && state.last_edit.as_ref() == Some(&(pointer.clone(), focus));
            state.last_edit = Some((pointer.clone(), focus));
            state.error.clear();
            queue.0.push_back(Command::Material(Edit {
                id,
                revision: doc.revision(),
                pointer,
                value,
                continuing,
            }));
        }
    }
    if let Some(parts) = &state.parts
        && let Ok(mut status) = controls.texts.get_mut(parts.status)
    {
        let text = if !state.error.is_empty() {
            state.error.clone()
        } else {
            format!(
                "{} · PBR surface · {} texture channels · {}",
                if doc.is_dirty() {
                    "Unsaved changes"
                } else {
                    "Saved"
                },
                data["textures"]
                    .as_object()
                    .map(|v| v
                        .values()
                        .filter(|p| p.as_str().is_some_and(|s| !s.is_empty()))
                        .count())
                    .unwrap_or(0),
                if state.data["transmission"].as_f64().unwrap_or(0.) > 0. {
                    "Transmission depends on scene lighting and background"
                } else {
                    "Live preview"
                }
            )
        };
        if status.0 != text {
            status.0 = text;
        }
    }
}

impl State {
    pub(crate) fn animating(&self) -> bool {
        self.world.is_some() && self.rotating
    }
}
