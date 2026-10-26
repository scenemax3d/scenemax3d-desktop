//! Native weapon documents with retained controls and normal document history.
use scenemax_ui_input::Interaction;
pub(crate) mod attachment;
pub(crate) mod playback;
pub(crate) mod preview;
#[cfg(test)]
mod tests;
mod view;
use super::components::EditorHost;
use crate::application::{Command, CommandQueue, Session, material::Edit};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::{DocumentId, DocumentRevision, weapon};
use scenemax_ide_services::inventory::{Asset, Inventory};
use scenemax_ide_ui::property::Choice;
use serde_json::{Value, json};
#[derive(Component)]
pub(crate) struct WeaponHost;
#[derive(Component)]
struct Field(String, String);
#[derive(Component)]
enum Action {
    Save,
    Undo,
    Redo,
    Refresh,
    Select(bool, usize),
    Section(bool),
    Edit(&'static str),
    Frame,
    Mode(bool),
    Pause,
    ClearAttachment,
}
#[derive(Component)]
pub(crate) struct Control(&'static str);
#[derive(Resource, Default)]
pub(crate) struct State {
    current: Option<(DocumentId, DocumentRevision)>,
    host: Option<Entity>,
    parts: Option<view::Parts>,
    data: Value,
    models: Vec<Asset>,
    task: Option<bevy::tasks::Task<Result<Inventory, std::io::Error>>>,
    posture: usize,
    collider: usize,
    colliders: bool,
    redraw: bool,
    error: String,
    preview_status: String,
    sockets: Vec<String>,
    render: preview::Preview,
    viewport_bound: bool,
    playing: bool,
    animation: String,
    speed: f32,
    rotate: bool,
    last_edit: Option<(String, Option<Entity>)>,
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
type PlaybackControls<'w, 's> = Query<
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
    labels: Query<'w, 's, (&'static Action, &'static Children)>,
    controls: PlaybackControls<'w, 's>,
    texts: Query<'w, 's, &'static mut Text>,
    focus: Option<Res<'w, bevy::input_focus::InputFocus>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
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
    hosts: Query<(Entity, &EditorHost), With<WeaponHost>>,
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
        *s = State {
            speed: 100.,
            ..default()
        };
    }
    let Some((host, id, doc)) = target else {
        return;
    };
    let stamp = (id, doc.revision());
    s.host = Some(host);
    let data = match weapon::parse(doc.text()) {
        Ok(v) => v,
        Err(e) => {
            if s.current != Some(stamp) {
                c.entity(host).despawn_children();
                c.spawn((
                    scenemax_ide_ui::label(format!("Cannot open weapon: {e}"), 14.),
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
                s.error = inventory.warnings.join("; ");
                s.redraw = true;
                s.render.reload = true;
            }
            Err(e) => s.error = e.to_string(),
        }
    }
    let counts = |v: &Value| {
        (
            v["postures"].as_array().map(Vec::len),
            v["colliders"].as_array().map(Vec::len),
        )
    };
    if counts(&s.data) != counts(&data) {
        s.redraw = true;
    }
    s.posture = s
        .posture
        .min(counts(&data).0.unwrap_or(1).saturating_sub(1));
    s.collider = s
        .collider
        .min(counts(&data).1.unwrap_or(0).saturating_sub(1));
    s.data = data.clone();
    if s.parts.is_none() {
        s.parts = Some(view::build(&mut c, host));
        s.redraw = true;
    }
    let rebuilt = s.redraw;
    if rebuilt {
        if let Some(parts) = s.parts.as_ref() {
            c.entity(parts.inspector).despawn_children();
            view::inspector(&mut c, parts.inspector, &s);
        }
        s.redraw = false;
    }
    if s.current != Some(stamp) && !rebuilt {
        for (mut f, text, choice, children) in &mut ui.fields {
            let value = display(data.pointer(&f.0).unwrap_or(&Value::Null));
            if value != f.1 {
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
            Action::Undo => queue
                .0
                .push_back(Command::Edit(crate::application::EditCommand::Undo)),
            Action::Redo => queue
                .0
                .push_back(Command::Edit(crate::application::EditCommand::Redo)),
            Action::Refresh => {
                load(&mut s, &session);
                s.render.reload = true;
            }
            Action::Select(collider, index) => {
                if *collider {
                    s.collider = *index;
                } else {
                    s.posture = *index;
                }
                s.redraw = true;
                s.last_edit = None;
            }
            Action::Section(collider) => {
                s.colliders = *collider;
                s.redraw = true;
            }
            Action::Edit(key) => {
                edit = Some((
                    key.to_string(),
                    json!(if key.starts_with("collider") {
                        s.collider
                    } else {
                        s.posture
                    }),
                ))
            }
            Action::Frame => s.render.frame = true,
            Action::Mode(rotate) => s.rotate = *rotate,
            Action::Pause => s.playing = !s.playing,
            Action::ClearAttachment => {
                edit = Some((
                    format!("/postures/{}/attachmentPoint", s.posture),
                    json!(""),
                ))
            }
        }
    }
    for (control, text, choice) in &ui.controls {
        match control.0 {
            "animation" => {
                if let Some(ch) = choice
                    && s.animation != ch.0
                {
                    s.animation = ch.0.clone();
                    s.render.restart = true;
                    s.playing = !ch.0.is_empty();
                }
            }
            "speed" => {
                if let Some(t) = text
                    && let Ok(n) = t.value().to_string().parse::<f32>()
                {
                    s.speed = n.clamp(1., 100.);
                }
            }
            _ => {}
        }
    }
    if edit.is_none() && !rebuilt && !s.redraw {
        for (mut f, text, choice, _) in &mut ui.fields {
            let value = text
                .map(|t| t.value().to_string())
                .or_else(|| choice.map(|ch| ch.0.clone()))
                .unwrap_or_default();
            if value == f.1 {
                continue;
            }
            f.1 = value.clone();
            let parsed = if data.pointer(&f.0).is_some_and(Value::is_number) {
                value
                    .parse::<f64>()
                    .ok()
                    .filter(|n| n.is_finite())
                    .map(|n| json!(n))
            } else {
                Some(json!(value))
            };
            if let Some(v) = parsed {
                edit = Some((f.0.clone(), v));
            } else {
                s.error = "Enter a finite number".into();
            }
            break;
        }
    }
    if let Some((key, value)) = edit {
        match weapon::edit(
            doc.text(),
            &key,
            value.clone(),
            value.as_u64().unwrap_or(0) as usize,
        ) {
            Ok(source) => {
                let focus = ui.focus.as_ref().and_then(|f| f.get());
                let continuing =
                    key.starts_with('/') && s.last_edit.as_ref() == Some(&(key.clone(), focus));
                s.last_edit = Some((key.clone(), focus));
                s.error.clear();
                if key.ends_with("add")
                    || key.ends_with("duplicate")
                    || key == "collider.box"
                    || key == "collider.sphere"
                {
                    let v: Value = serde_json::from_str(&source).unwrap_or_default();
                    if key.starts_with("posture") {
                        s.posture = if key.ends_with("duplicate") {
                            value.as_u64().unwrap_or(0) as usize + 1
                        } else {
                            v["postures"]
                                .as_array()
                                .map_or(0, |a| a.len().saturating_sub(1))
                        };
                    } else {
                        s.collider = if key.ends_with("duplicate") {
                            value.as_u64().unwrap_or(0) as usize + 1
                        } else {
                            v["colliders"]
                                .as_array()
                                .map_or(0, |a| a.len().saturating_sub(1))
                        };
                    }
                }
                queue.0.push_front(Command::Weapon(Edit {
                    id,
                    revision: doc.revision(),
                    pointer: key,
                    value,
                    continuing,
                }));
            }
            Err(e) => s.error = e,
        }
    }
    for (action, children) in &ui.labels {
        let Action::Select(collider, index) = action else {
            continue;
        };
        let selected = if *collider { s.collider } else { s.posture };
        let v = &s.data[if *collider { "colliders" } else { "postures" }][*index];
        let caption = if *collider {
            format!(
                "{}{} · {}",
                if *index == selected { "● " } else { "" },
                v["name"].as_str().unwrap_or(""),
                v["shape"].as_str().unwrap_or("")
            )
        } else {
            format!(
                "{}{} · {}{}",
                if *index == selected { "● " } else { "" },
                v["id"].as_str().unwrap_or(""),
                v["name"].as_str().unwrap_or(""),
                if v["id"] == s.data["defaultPostureId"] {
                    " (default)"
                } else {
                    ""
                }
            )
        };
        for child in children.iter() {
            if let Ok(mut text) = ui.texts.get_mut(child)
                && text.0 != caption
            {
                text.0 = caption.clone();
            }
        }
    }
    if let Some(parts) = &s.parts
        && let Ok(mut text) = ui.texts.get_mut(parts.status)
    {
        text.0 = format!(
            "{} · {}{}",
            if doc.is_dirty() {
                "Unsaved changes"
            } else {
                "Saved"
            },
            if s.error.is_empty() {
                &s.preview_status
            } else {
                &s.error
            },
            if s.task.is_some() {
                " · Loading registered models…"
            } else {
                ""
            }
        );
    }
}
fn display(v: &Value) -> String {
    v.as_str()
        .map(str::to_owned)
        .unwrap_or_else(|| v.to_string())
}

/// Controlled GPU audit only; never installed for normal IDE launches.
pub(crate) fn smoke(
    mut s: ResMut<State>,
    mut controls: Query<(&Control, &mut Choice, &Children)>,
    mut texts: Query<&mut Text>,
    mut done: Local<bool>,
) {
    if *done || !s.render.ready() {
        return;
    }
    *done = true;
    if std::env::var("SCENEMAX_SMOKE_WEAPON").as_deref() == Ok("colliders") {
        s.colliders = true;
        s.rotate = true;
        s.redraw = true;
    }
    for (control, mut choice, children) in &mut controls {
        if control.0 == "animation" {
            choice.0 = std::env::var("SCENEMAX_SMOKE_WEAPON")
                .ok()
                .and_then(|v| v.strip_prefix("clip:").map(str::to_owned))
                .unwrap_or_else(|| s.render.first_clip().unwrap_or_default());
            for child in children.iter() {
                if let Ok(mut text) = texts.get_mut(child) {
                    text.0 = format!("{} ↓", choice.0);
                }
            }
        }
    }
}
