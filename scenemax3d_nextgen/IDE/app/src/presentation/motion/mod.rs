//! Retained throw-motion document workbench.
pub(crate) mod preview;
#[cfg(test)]
mod tests;
mod view;
use super::components::EditorHost;
use crate::application::{Command, CommandQueue, Session, material::Edit};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::{DocumentId, DocumentRevision, motion};
use scenemax_ide_ui::property::Choice;
use serde_json::{Value, json};
#[derive(Component)]
pub(crate) struct MotionHost;
#[derive(Component)]
struct Field(String, String);
#[derive(Component)]
struct Control(&'static str);
#[derive(Component)]
enum Action {
    Save,
    Undo,
    Redo,
    Play,
    Step,
    Reset,
    Frame,
    Trajectory,
    Samples,
}
#[derive(Resource)]
pub(crate) struct State {
    current: Option<(DocumentId, DocumentRevision)>,
    host: Option<Entity>,
    parts: Option<view::Parts>,
    data: Value,
    error: String,
    last_edit: Option<(String, Option<Entity>)>,
    playing: bool,
    time: f32,
    distance: f32,
    height: f32,
    trajectory: bool,
    samples: bool,
    frame: bool,
    render: preview::Preview,
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
            playing: false,
            time: 0.,
            distance: 12.,
            height: 0.,
            trajectory: true,
            samples: true,
            frame: true,
            render: default(),
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
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Controls<'w, 's> {
    fields: Fields<'w, 's>,
    buttons: Query<'w, 's, (&'static Interaction, &'static Action), Changed<Interaction>>,
    labels: Query<'w, 's, (&'static Action, &'static Children)>,
    controls: Query<'w, 's, (&'static Control, &'static EditableText), Without<Field>>,
    texts: Query<'w, 's, &'static mut Text>,
    focus: Option<Res<'w, bevy::input_focus::InputFocus>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
}
fn display(v: &Value) -> String {
    v.as_str()
        .map(str::to_owned)
        .unwrap_or_else(|| v.to_string())
}
pub(crate) fn update(
    mut c: Commands,
    session: Res<Session>,
    mut s: ResMut<State>,
    hosts: Query<(Entity, &EditorHost), With<MotionHost>>,
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
    let data = match motion::parse(doc.text()) {
        Ok(v) => v,
        Err(e) => {
            if s.current != Some(stamp) {
                c.entity(host).despawn_children();
                c.spawn((
                    scenemax_ide_ui::label(format!("Cannot open motion: {e}"), 14.),
                    ChildOf(host),
                ));
            }
            s.current = Some(stamp);
            return;
        }
    };
    let rebuild = s.parts.is_none() || s.data["motionType"] != data["motionType"];
    if s.parts.is_none() {
        s.parts = Some(view::build(&mut c, host));
    }
    s.data = data.clone();
    if rebuild && let Some(parts) = &s.parts {
        c.entity(parts.inspector).despawn_children();
        view::inspector(&mut c, parts.inspector, &data);
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
                                t.0 = format!("{} ↓", view::caption(&f.0, &value));
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
    for (i, a) in &ui.buttons {
        if *i == Interaction::Pressed {
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
                Action::Play => s.playing = !s.playing,
                Action::Step => {
                    s.playing = false;
                    s.time += 1. / 30.;
                }
                Action::Reset => {
                    s.playing = false;
                    s.time = 0.;
                }
                Action::Frame => s.frame = true,
                Action::Trajectory => s.trajectory = !s.trajectory,
                Action::Samples => s.samples = !s.samples,
            }
        }
    }
    for (control, text) in &ui.controls {
        if let Ok(n) = text.value().to_string().parse::<f32>()
            && n.is_finite()
        {
            match control.0 {
                "distance" => s.distance = n.clamp(2., 40.),
                "height" => s.height = n.clamp(-5., 12.),
                _ => {}
            }
        }
    }
    if !rebuild {
        for (mut f, text, choice, _) in &mut ui.fields {
            let value = text
                .map(|t| t.value().to_string())
                .or_else(|| choice.map(|c| c.0.clone()))
                .unwrap_or_default();
            if value == f.1 {
                continue;
            }
            let old = data.pointer(&f.0).unwrap_or(&Value::Null);
            let parsed = if old.is_number() {
                value
                    .parse::<f64>()
                    .ok()
                    .filter(|n| n.is_finite() && n.abs() <= 1.0e9)
                    .map(|n| json!(n))
            } else if old.is_boolean() {
                value.parse::<bool>().ok().map(|b| json!(b))
            } else {
                Some(json!(value))
            };
            if let Some(v) = parsed {
                let focus = ui.focus.as_ref().and_then(|f| f.get());
                let continuing = s.last_edit.as_ref() == Some(&(f.0.clone(), focus));
                s.last_edit = Some((f.0.clone(), focus));
                s.error.clear();
                f.1 = value;
                queue.0.push_front(Command::Motion(Edit {
                    id,
                    revision: doc.revision(),
                    pointer: f.0.clone(),
                    value: v,
                    continuing,
                }));
            } else {
                s.error = "Enter a finite number (at most one billion in magnitude).".into();
                queue.0.retain(|cmd| !matches!(cmd, Command::Save));
            }
            break;
        }
    }
    for (action, children) in &ui.labels {
        let caption = match action {
            Action::Play => {
                if s.playing {
                    "Pause"
                } else {
                    "Play"
                }
            }
            Action::Trajectory => {
                if s.trajectory {
                    "Trajectory: on"
                } else {
                    "Trajectory: off"
                }
            }
            Action::Samples => {
                if s.samples {
                    "Samples: on"
                } else {
                    "Samples: off"
                }
            }
            _ => continue,
        };
        for child in children.iter() {
            if let Ok(mut t) = ui.texts.get_mut(child) {
                t.0 = caption.into();
            }
        }
    }
    if let Some(parts) = &s.parts
        && let Ok(mut t) = ui.texts.get_mut(parts.status)
    {
        let validation = motion::validate(&data).err().unwrap_or_default();
        let duration = s.render.duration();
        t.0 = format!(
            "{}  ·  {:.2} / {:.2} s  ·  {} samples\n{}{}{}",
            data["id"].as_str().unwrap_or("Preview"),
            s.time,
            duration,
            s.render.count(),
            s.error,
            validation,
            motion::warnings(&data).join(" ")
        );
    }
}

pub(crate) fn smoke(mut s: ResMut<State>, mut done: Local<bool>) {
    if !*done && s.render.count() > 0 {
        s.playing = true;
        *done = true;
    }
}
