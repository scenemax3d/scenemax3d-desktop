//! Retained IK document workbench.
use scenemax_ui_input::Interaction;
pub(crate) mod preview;
#[cfg(test)]
mod tests;
mod view;
use super::components::EditorHost;
use crate::application::{Command, CommandQueue, Session, material::Edit};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::{DocumentId, DocumentRevision, ik};
use scenemax_ide_ui::property::Choice;
use serde_json::{Value, json};
#[derive(Component)]
pub(crate) struct IkHost;
#[derive(Component)]
struct Field(String, String);
#[derive(Component)]
pub(crate) struct Control(&'static str);
#[derive(Component)]
enum Action {
    Save,
    Undo,
    Redo,
    Refresh,
    Add,
    Duplicate,
    Delete,
    Preset,
    Humanoid,
    Run,
    Play,
    PlayAll,
    Stop,
    Reset,
    Frame,
    Mode(&'static str),
    EditTarget(bool),
    PlayAnim,
    StopAnim,
}
#[derive(Resource)]
pub(crate) struct State {
    current: Option<(DocumentId, DocumentRevision)>,
    host: Option<Entity>,
    parts: Option<view::Parts>,
    data: Value,
    error: String,
    last_edit: Option<(String, Option<Entity>)>,
    models: Vec<scenemax_ide_services::inventory::Asset>,
    task: Option<
        bevy::tasks::Task<Result<scenemax_ide_services::inventory::Inventory, std::io::Error>>,
    >,
    target_bone_selection: String,
    layer: usize,
    preset: usize,
    bones: Vec<String>,
    target_bones: Vec<String>,
    redraw: bool,
    running: u8,
    request: u64,
    playing: bool,
    animation: String,
    mode: &'static str,
    edit_target: bool,
    frame: bool,
    render: preview::Preview,
    preview_status: String,
}
impl Default for State {
    fn default() -> Self {
        Self {
            current: None,
            host: None,
            parts: None,
            data: Value::Null,
            error: String::new(),
            last_edit: None,
            models: vec![],
            task: None,
            target_bone_selection: String::new(),
            layer: 0,
            preset: 0,
            bones: vec![],
            target_bones: vec![],
            redraw: false,
            running: 0,
            request: 0,
            playing: false,
            animation: String::new(),
            mode: "Move",
            edit_target: true,
            frame: true,
            render: default(),
            preview_status: String::new(),
        }
    }
}
type Fields<'w, 's> = Query<
    'w,
    's,
    (
        &'static mut Field,
        Option<&'static mut EditableText>,
        Option<&'static mut Choice>,
        Option<&'static Children>,
    ),
>;
type ControlFields<'w, 's> = Query<
    'w,
    's,
    (
        &'static Control,
        Option<&'static EditableText>,
        Option<&'static Choice>,
    ),
    Without<Field>,
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Controls<'w, 's> {
    fields: Fields<'w, 's>,
    buttons: Query<'w, 's, (&'static Interaction, &'static Action), Changed<Interaction>>,

    controls: ControlFields<'w, 's>,
    layer_selectors: Query<'w, 's, (Entity, &'static ChildOf), With<view::LayerSelector>>,
    texts: Query<'w, 's, &'static mut Text>,
    focus: Option<Res<'w, bevy::input_focus::InputFocus>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
}
fn display(v: &Value) -> String {
    if let Some(a) = v.as_array() {
        return a
            .iter()
            .filter_map(Value::as_str)
            .collect::<Vec<_>>()
            .join(", ");
    }
    v.as_str()
        .map(str::to_owned)
        .unwrap_or_else(|| v.to_string())
}
fn load(s: &mut State, session: &Session) {
    let root = session.workspace.project().root().to_owned();
    s.task = Some(
        bevy::tasks::IoTaskPool::get()
            .spawn(async move { scenemax_ide_services::inventory::scan(&root) }),
    );
}
pub(crate) fn update(
    mut c: Commands,
    session: Res<Session>,
    mut s: ResMut<State>,
    hosts: Query<(Entity, &EditorHost), With<IkHost>>,
    mut queue: ResMut<CommandQueue>,
    mut ui: Controls,
) {
    let target = session.workspace.active_id().and_then(|id| {
        session.workspace.document(id).ok().and_then(|d| {
            hosts
                .iter()
                .find(|(_, h)| h.0 == id)
                .map(|(h, _)| (h, id, d))
        })
    });
    if target.map(|t| t.1) != s.current.map(|v| v.0) {
        s.render.clear(&mut c);
        if let Some(h) = s.host
            && hosts.contains(h)
        {
            c.entity(h).despawn_children();
        }
        *s = default();
    }
    let Some((host, id, doc)) = target else {
        return;
    };
    s.host = Some(host);
    let stamp = (id, doc.revision());
    let data = match ik::parse(doc.text()) {
        Ok(v) => v,
        Err(e) => {
            if s.current != Some(stamp) {
                c.entity(host).despawn_children();
                c.spawn((
                    scenemax_ide_ui::label(format!("Cannot open IK: {e}"), 14.),
                    ChildOf(host),
                ));
            }
            s.current = Some(stamp);
            return;
        }
    };
    if s.current.is_none() {
        load(&mut s, &session);
    }
    if let Some(task) = s.task.as_mut()
        && let Some(result) = bevy::tasks::block_on(bevy::tasks::poll_once(task))
    {
        s.task = None;
        match result {
            Ok(inventory) => {
                s.models = inventory
                    .assets
                    .into_iter()
                    .filter(|a| a.category == "Models" && !a.status.contains("Shadowed"))
                    .collect();
                s.models.sort_by(|a, b| a.name.cmp(&b.name));
                s.redraw = true;
            }
            Err(e) => s.error = e.to_string(),
        }
    }
    let count = data["layers"].as_array().map_or(0, Vec::len);
    s.layer = s.layer.min(count.saturating_sub(1));
    let rebuild = s.parts.is_none()
        || s.redraw
        || s.data["layers"].as_array().map_or(0, Vec::len) != count
        || s.data["layers"][s.layer]["solverType"] != data["layers"][s.layer]["solverType"];
    let selector_changed = s.data["layers"]
        .as_array()
        .into_iter()
        .flatten()
        .map(|l| (&l["id"], &l["solverType"]))
        .ne(data["layers"]
            .as_array()
            .into_iter()
            .flatten()
            .map(|l| (&l["id"], &l["solverType"])));
    s.data = data.clone();
    if s.parts.is_none() {
        s.parts = Some(view::build(&mut c, host));
    }
    if rebuild && let Some(parts) = &s.parts {
        c.entity(parts.inspector).despawn_children();
        view::inspector(&mut c, parts.inspector, &s);
        s.redraw = false;
    }
    if !rebuild
        && selector_changed
        && let Some(parts) = &s.parts
    {
        // Refresh only the selector so typing a layer ID keeps input focus.
        for (selector, parent) in &ui.layer_selectors {
            if parent.parent() == parts.inspector {
                c.entity(selector).despawn_children();
                view::layer_selector(&mut c, selector, &s);
            }
        }
    }
    if s.current != Some(stamp) && !rebuild {
        for (mut f, text, choice, children) in &mut ui.fields {
            let value = display(data.pointer(&f.0).unwrap_or(&Value::Null));
            if f.1 != value {
                if let Some(mut t) = text {
                    t.editor_mut().set_text(&value);
                }
                if let Some(mut ch) = choice {
                    ch.0 = value.clone();
                    if let Some(children) = children {
                        for child in children.iter() {
                            if let Ok(mut t) = ui.texts.get_mut(child) {
                                t.0 =
                                    format!("{} ↓", if value.is_empty() { "None" } else { &value });
                            }
                        }
                    }
                }
                f.1 = value;
            }
        }
    }
    s.current = Some(stamp);
    if ui.mouse.just_pressed(MouseButton::Left) {
        s.last_edit = None;
    }
    let mut edit = None;
    for (i, a) in &ui.buttons {
        if *i != Interaction::Pressed {
            continue;
        }
        match a {
            Action::Save => queue.0.push_back(Command::Save),
            Action::Undo => {
                s.last_edit = None;
                queue
                    .0
                    .push_back(Command::Edit(crate::application::EditCommand::Undo));
            }
            Action::Redo => {
                s.last_edit = None;
                queue
                    .0
                    .push_back(Command::Edit(crate::application::EditCommand::Redo));
            }
            Action::Refresh => load(&mut s, &session),
            Action::Add => {
                edit = Some(("add".into(), json!(s.layer)));
                s.layer = count;
            }
            Action::Duplicate => {
                edit = Some(("duplicate".into(), json!(s.layer)));
                s.layer += 1;
            }
            Action::Delete => edit = Some(("delete".into(), json!(s.layer))),
            Action::Preset => {
                let preset = ik::preset(s.preset, &s.bones);
                let mut layer = data["layers"][s.layer].clone();
                for key in ["solverType", "chain", "affectedJoints"] {
                    layer[key] = preset[key].clone();
                }
                edit = Some((format!("/layers/{}", s.layer), layer));
                s.redraw = true;
            }
            Action::Humanoid => {
                let presets = [0, 1, 2, 3, 4, 5, 6].map(|i| ik::preset(i, &s.bones));
                let mut layers = data["layers"].as_array().cloned().unwrap_or_default();
                for mut layer in presets {
                    let base = layer["id"].as_str().unwrap_or("layer").to_owned();
                    let mut id = base.clone();
                    let mut i = 2;
                    while layers.iter().any(|l| l["id"] == id) {
                        id = format!("{base}_{i}");
                        i += 1;
                    }
                    layer["id"] = json!(id);
                    layers.push(layer);
                }
                edit = Some(("/layers".into(), json!(layers)));
                s.redraw = true;
            }
            Action::Run => {
                s.playing = false;
                s.running = 1;
                s.request += 1;
            }
            Action::Play => {
                s.running = 2;
                s.request += 1;
            }
            Action::PlayAll => {
                s.running = 3;
                s.request += 1;
            }
            Action::Stop => {
                s.running = 0;
                s.request += 1;
            }
            Action::Reset => {
                s.running = 0;
                s.playing = false;
                s.animation.clear();
                s.request += 1;
                s.render.reset = true;
            }
            Action::Frame => s.frame = true,
            Action::Mode(mode) => s.mode = mode,
            Action::EditTarget(target) => s.edit_target = *target,
            Action::PlayAnim => s.playing = true,
            Action::StopAnim => s.playing = false,
        }
    }
    for (control, text, choice) in &ui.controls {
        let value = text
            .map(|t| t.value().to_string())
            .or_else(|| choice.map(|ch| ch.0.clone()))
            .unwrap_or_default();
        match control.0 {
            "layer" => {
                if !rebuild
                    && edit.is_none()
                    && let Ok(index) = value.parse::<usize>()
                    && index != s.layer
                {
                    s.layer = index.min(count.saturating_sub(1));
                    s.redraw = true;
                }
            }
            "preset" => s.preset = ik::PRESETS.iter().position(|p| *p == value).unwrap_or(0),
            "animation" => {
                if s.animation != value {
                    s.animation = value;
                    s.playing = !s.animation.is_empty();
                    s.render.restart = true;
                }
            }
            "target_bone" if !rebuild && !s.redraw && value != s.target_bone_selection => {
                s.target_bone_selection = value.clone();
                edit = Some(("target_bone".into(), json!({"index":s.layer,"bone":value})));
            }
            _ => {}
        }
    }
    if edit.is_none() && !rebuild && !s.redraw {
        for (mut f, text, choice, _) in &mut ui.fields {
            let value = text
                .map(|t| t.value().to_string())
                .or_else(|| choice.map(|ch| ch.0.clone()))
                .unwrap_or_default();
            if value == f.1 {
                continue;
            }
            let old = data.pointer(&f.0).unwrap_or(&Value::Null);
            let parsed = if old.is_number() {
                value
                    .parse::<f64>()
                    .ok()
                    .filter(|v| v.is_finite() && v.abs() < 1.0e9)
                    .map(|n| json!(n))
            } else if old.is_boolean() {
                value.parse::<bool>().ok().map(|b| json!(b))
            } else if old.is_array() {
                Some(json!(
                    value
                        .split(',')
                        .map(str::trim)
                        .filter(|s| !s.is_empty())
                        .collect::<Vec<_>>()
                ))
            } else {
                Some(json!(value))
            };
            if let Some(value) = parsed {
                edit = Some((f.0.clone(), value));
                f.1 = value_to_display(edit.as_ref().map(|(_, v)| v));
            } else {
                s.error = "Enter a finite number".into();
                queue.0.retain(|cmd| !matches!(cmd, Command::Save));
            }
            break;
        }
    }
    if let Some((key, value)) = edit {
        let focus = ui.focus.as_ref().and_then(|f| f.get());
        let continuing =
            key.starts_with('/') && s.last_edit.as_ref() == Some(&(key.clone(), focus));
        s.last_edit = Some((key.clone(), focus));
        s.error.clear();
        queue.0.push_front(Command::Ik(Edit {
            id,
            revision: doc.revision(),
            pointer: key,
            value,
            continuing,
        }));
    }
    if let Some(parts) = &s.parts
        && let Ok(mut t) = ui.texts.get_mut(parts.status)
    {
        t.0 = format!(
            "{} · Editing {} · {}\n{}\n{}",
            s.preview_status,
            if s.edit_target { "target" } else { "model" },
            s.mode,
            s.error,
            ik::diagnostics(&data).join(" · ")
        );
    }
}
fn value_to_display(v: Option<&Value>) -> String {
    v.map(display).unwrap_or_default()
}

pub(crate) fn smoke(
    mut s: ResMut<State>,
    mut choices: Query<(&Control, &mut Choice, &Children)>,
    mut texts: Query<&mut Text>,
    mut scrolls: Query<&mut ScrollPosition>,
    mut done: Local<bool>,
) {
    if let Ok(offset) = std::env::var("SCENEMAX_SMOKE_IK_SCROLL")
        .unwrap_or_default()
        .parse::<f32>()
        && offset.is_finite()
        && let Some(parts) = &s.parts
        && let Ok(mut scroll) = scrolls.get_mut(parts.inspector)
    {
        scroll.y = offset.max(0.);
    }
    if !*done && !s.bones.is_empty() {
        s.running = 3;
        s.request += 1;
        let animation = s.render.first_clip().unwrap_or_default();
        for (control, mut choice, children) in &mut choices {
            if control.0 == "animation" {
                choice.0 = animation.clone();
                for child in children.iter() {
                    if let Ok(mut t) = texts.get_mut(child) {
                        t.0 = format!("{} ↓", choice.0);
                    }
                }
            }
        }
        s.animation = animation;
        s.playing = true;
        s.render.restart = true;
        *done = true;
    }
}
