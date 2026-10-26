//! Retained physics laboratory: isolated live solver and exact generated commands.
pub(crate) mod preview;
mod simulation;
mod view;
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::physics::Settings;
use scenemax_ide_ui::property::Choice;
use serde_json::Value;
pub(crate) use simulation::Sandbox;
#[derive(Component)]
struct Relevance(String);
#[derive(Component)]
struct Field(String, String);
#[derive(Clone, Copy)]
enum Action {
    Close,
    Play,
    Step,
    Reset,
    Frame,
    Trail,
    Loop,
    Snippet,
    Example,
}
#[derive(Resource)]
pub(crate) struct State {
    pub(crate) open: bool,
    host: Option<Entity>,
    parts: Option<view::Parts>,
    data: Value,
    settings: Settings,
    actions: Vec<Action>,
    playing: bool,
    looping: bool,
    trail: bool,
    reset: bool,
    frame: bool,
    revision: u64,
    error: String,
    notice: String,
    remainder: f32,
    snapshot: simulation::Snapshot,
    elapsed: f32,
    render: preview::Preview,
}
impl Default for State {
    fn default() -> Self {
        let settings = Settings::default();
        Self {
            open: false,
            host: None,
            parts: None,
            data: serde_json::to_value(&settings).unwrap_or_default(),
            settings,
            actions: vec![],
            playing: false,
            looping: false,
            trail: true,
            reset: true,
            frame: true,
            revision: 0,
            error: String::new(),
            notice: String::new(),
            remainder: 0.,
            snapshot: default(),
            elapsed: 0.,
            render: default(),
        }
    }
}
pub(crate) fn menu_item(c: &mut Commands, parent: Entity, title: &str) -> Entity {
    let e = scenemax_ide_ui::button(c, parent, title, Name::new("Physics Simulation"));
    c.entity(e)
        .observe(|_: On<PointerPress>, mut s: ResMut<State>| s.open = true);
    e
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Inputs<'w, 's> {
    fields: Query<
        'w,
        's,
        (
            &'static mut Field,
            Option<&'static EditableText>,
            Option<&'static Choice>,
        ),
    >,
    visibility: Query<'w, 's, (&'static Relevance, &'static mut Node)>,
    texts: Query<'w, 's, &'static mut Text>,
    clipboard: Option<ResMut<'w, bevy::clipboard::Clipboard>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
    keys: Res<'w, ButtonInput<KeyCode>>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
}
pub(crate) fn update(
    mut c: Commands,
    mut s: ResMut<State>,
    mut sim: NonSendMut<Sandbox>,
    mut ui: Inputs,
    time: Res<Time>,
    mut prior: Local<Option<bevy::winit::UpdateMode>>,
) {
    if s.open && s.host.is_none() {
        let (host, parts) = view::build(&mut c, &s.data);
        s.host = Some(host);
        s.parts = Some(parts);
        s.reset = true;
    }
    if s.open && ui.keys.just_pressed(KeyCode::F8) {
        s.actions.push(Action::Play);
    }
    if s.open && ui.keys.just_pressed(KeyCode::Escape) {
        s.actions.push(Action::Close);
    }
    let mut changed = false;
    if s.open {
        for (mut field, text, choice) in &mut ui.fields {
            let value = if let Some(text) = text {
                text.editor.text().to_string()
            } else if let Some(choice) = choice {
                choice.0.clone()
            } else {
                continue;
            };
            if value == field.1 {
                continue;
            }
            field.1 = value.clone();
            changed = true;
            if let Some(slot) = s.data.pointer_mut(&field.0) {
                *slot = if slot.is_string() {
                    Value::String(value)
                } else {
                    value
                        .parse::<f64>()
                        .ok()
                        .filter(|n| n.is_finite())
                        .map_or(Value::Null, Value::from)
                };
            }
        }
        if changed {
            match serde_json::from_value::<Settings>(s.data.clone())
                .map_err(|_| "Enter a valid number in every numeric field".to_owned())
                .and_then(|v| {
                    v.validate()?;
                    Ok(v)
                }) {
                Ok(settings) => {
                    s.settings = settings;
                    s.error.clear();
                    s.reset = true;
                }
                Err(error) => {
                    s.error = error;
                    s.playing = false;
                }
            }
        }
    }
    let mut step = false;
    for action in std::mem::take(&mut s.actions) {
        match action {
            Action::Close => {
                s.open = false;
                s.playing = false;
            }
            Action::Play if s.error.is_empty() => {
                if s.elapsed >= s.settings.duration {
                    s.reset = true;
                }
                s.playing = !s.playing;
            }
            Action::Step if s.error.is_empty() => {
                s.playing = false;
                step = true;
                if s.elapsed >= s.settings.duration {
                    s.reset = true;
                }
            }
            Action::Reset => {
                s.playing = false;
                s.reset = true;
            }
            Action::Frame => s.frame = true,
            Action::Trail => {
                s.trail = !s.trail;
                s.revision += 1;
            }
            Action::Loop => s.looping = !s.looping,
            Action::Snippet | Action::Example if s.error.is_empty() => {
                let code = if matches!(action, Action::Snippet) {
                    s.settings.snippet()
                } else {
                    s.settings.example()
                };
                s.notice = match (code, ui.clipboard.as_mut()) {
                    (Ok(code), Some(clipboard)) => clipboard
                        .set_text(code)
                        .map(|_| "Code copied".into())
                        .unwrap_or_else(|e| format!("Clipboard: {e}")),
                    (Err(e), _) => e,
                    _ => "Clipboard unavailable".into(),
                };
            }
            _ => {}
        }
    }
    if !s.open {
        if let Some(host) = s.host.take() {
            c.entity(host).try_despawn();
            s.parts = None;
            s.render.clear(&mut c);
            sim.clear();
        }
    } else if s.error.is_empty() {
        if s.reset && !ui.mouse.pressed(MouseButton::Left) {
            let settings = s.settings.clone();
            match sim.reset(&settings) {
                Ok(()) => {
                    s.reset = false;
                    s.revision += 1;
                    s.remainder = 0.;
                }
                Err(e) => {
                    s.error = e;
                    s.playing = false;
                }
            }
        }
        if s.playing && !s.reset {
            s.remainder += time.delta_secs().min(0.125);
        }
        if step {
            sim.step();
        }
        let mut count = 0;
        while s.playing && !s.reset && s.remainder >= simulation::DT && count < 8 {
            sim.step();
            s.remainder -= simulation::DT;
            count += 1;
            if sim.time >= s.settings.duration {
                if s.looping {
                    s.reset = true;
                } else {
                    s.playing = false;
                }
                break;
            }
        }
        s.snapshot = sim.snapshot();
        s.elapsed = sim.time;
    }
    if let Some(winit) = ui.winit.as_mut() {
        if s.open && s.playing {
            if prior.is_none() {
                *prior = Some(winit.focused_mode);
            }
            winit.focused_mode = bevy::winit::UpdateMode::Continuous;
        } else if let Some(mode) = prior.take() {
            winit.focused_mode = mode;
        }
    }
    for (relevance, mut node) in &mut ui.visibility {
        let setting = &s.settings;
        let linear = ["Throw", "Force", "Impulse"].contains(&setting.command.as_str());
        let arc_enabled = setting.command == "Throw"
            && !["Vector", "Up", "Down"].contains(&setting.direction.as_str());
        let show = match relevance.0.as_str() {
            "/direction" => linear,
            "/power" => linear && setting.direction != "Vector",
            "/arc" => arc_enabled,
            "/angle" => arc_enabled && setting.arc == "Angle",
            "/blend" => arc_enabled && setting.arc == "Custom",
            "/spin" => setting.command == "Throw",
            "/vector" => !linear || setting.direction == "Vector",
            "/force_duration" => setting.command == "Force",
            "/stop_after" => setting.command == "Stop",
            "/destination" | "/target" => {
                linear && ["At target", "Toward target"].contains(&setting.direction.as_str())
            }
            _ => true,
        };
        let display = if show { Display::Flex } else { Display::None };
        if node.display != display {
            node.display = display;
        }
    }
    if let Some(parts) = &s.parts {
        if let Ok(mut t) = ui.texts.get_mut(parts.hint) {
            let hint = match s.settings.command.as_str() {
                "Throw" => {
                    "Launch speed: m/s. Spin: rad/s. Low/high arcs can hit the target; damping alters the flight."
                }
                "Impulse" => {
                    "Impulse: N s. Adds momentum once; doubling mass halves the velocity change."
                }
                "Force" => {
                    "Force: N. Accelerates while applied. Duration 0 sustains the force until stopped."
                }
                "Velocity" => {
                    "Velocity: m/s. Replaces linear velocity once; gravity and collisions continue acting."
                }
                "Angular velocity" => {
                    "Angular velocity: rad/s. Replaces spin once. Use the stripe to follow rotation."
                }
                "Torque" => {
                    "Torque: N m. Continuously accelerates rotation according to mass and shape inertia."
                }
                "Torque impulse" => "Angular impulse: N m s. Changes angular momentum once.",
                _ => {
                    "Stop demo: start with the vector velocity, then stop after the delay. Gravity still acts."
                }
            };
            if t.0 != hint {
                t.0 = hint.into();
            }
        }

        if let Ok(mut text) = ui.texts.get_mut(parts.code) {
            let code = s.settings.snippet().unwrap_or_default();
            if text.0 != code {
                text.0 = code;
            }
        }
        if let Ok(mut text) = ui.texts.get_mut(parts.status) {
            text.0 = if !s.error.is_empty() {
                s.error.clone()
            } else {
                format!(
                    "{}  |  t {:.2} / {:.1} s  |  pos ({:.2}, {:.2}, {:.2})  |  speed {:.2} m/s  |  spin {:.2} rad/s\n{}{}",
                    if s.playing { "Playing" } else { "Paused" },
                    s.elapsed,
                    s.settings.duration,
                    s.snapshot.position.x,
                    s.snapshot.position.y,
                    s.snapshot.position.z,
                    s.snapshot.velocity.length(),
                    s.snapshot.angular.length(),
                    if s.settings.launch().1 {
                        "Target unreachable at this speed: direct launch used. "
                    } else {
                        ""
                    },
                    s.notice
                )
            };
        }
        for (entity, caption) in [
            (parts.play, if s.playing { "Pause" } else { "Play" }),
            (
                parts.loop_label,
                if s.looping { "Loop: on" } else { "Loop: off" },
            ),
            (
                parts.trail_label,
                if s.trail { "Trail: on" } else { "Trail: off" },
            ),
        ] {
            if let Ok(mut t) = ui.texts.get_mut(entity)
                && t.0 != caption
            {
                t.0 = caption.into();
            }
        }
    }
}
pub(crate) fn smoke(mut s: ResMut<State>, mut once: Local<bool>) {
    if !*once {
        s.open = true;
        s.playing = true;
        *once = true;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn retained_form_validates_steps_and_releases_the_world() {
        let mut app = App::new();
        app.add_plugins(MinimalPlugins)
            .init_resource::<State>()
            .insert_non_send(Sandbox::default())
            .init_resource::<ButtonInput<KeyCode>>()
            .init_resource::<ButtonInput<MouseButton>>()
            .add_systems(Update, update);
        app.world_mut().resource_mut::<State>().open = true;
        app.update();
        let host = app.world().resource::<State>().host.unwrap();
        app.update();
        assert_eq!(app.world().resource::<State>().host, Some(host));
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push(Action::Step);
        app.update();
        assert_eq!(app.world().resource::<State>().elapsed, simulation::DT);
        {
            let mut fields = app.world_mut().query::<(&Field, &mut EditableText)>();
            for (field, mut input) in fields.iter_mut(app.world_mut()) {
                if field.0 == "/mass" {
                    input.editor_mut().set_text("invalid");
                }
            }
        }
        app.update();
        assert!(!app.world().resource::<State>().error.is_empty());
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push(Action::Play);
        app.update();
        assert!(!app.world().resource::<State>().playing);
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push(Action::Close);
        app.update();
        assert!(app.world().resource::<State>().host.is_none());
        assert_eq!(app.world().non_send::<Sandbox>().time, 0.);
    }
}
