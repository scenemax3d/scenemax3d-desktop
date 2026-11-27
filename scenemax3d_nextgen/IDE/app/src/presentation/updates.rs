//! Manual release details and background update notifications.
use crate::application::{Command, CommandQueue, Session};
use bevy::{input_focus::tab_navigation::TabGroup, prelude::*};
use scenemax_ide_services::updates::{Ready, Worker};
use scenemax_ide_ui::{button, label, property, theme::*};

#[derive(Component)]
struct UpdateButton;

#[derive(Resource, Default)]
pub(crate) struct State {
    worker: Option<Worker>,
    release: Option<Ready>,
    message: String,
    pub(crate) open: bool,
    dirty: bool,
    checking: bool,
    dialog: Option<Entity>,
    banner: Option<Entity>,
    notified: Option<String>,
}
impl State {
    pub(crate) fn start() -> Self {
        Self {
            worker: scenemax_ide_services::updates::start(),
            ..default()
        }
    }
    pub(crate) fn check_now(&mut self) {
        self.open = true;
        self.dirty = true;
        if self.checking {
            return;
        }
        self.release = None;
        match self
            .worker
            .as_ref()
            .ok_or_else(|| {
                "Update checks are available in an installed Windows version of SceneMax Studio."
                    .to_string()
            })
            .and_then(Worker::check_now)
        {
            Ok(()) => {
                self.checking = true;
                self.message = "Checking for updates…".into();
            }
            Err(error) => self.message = error,
        }
    }
}

pub(crate) fn poll(
    mut commands: Commands,
    mut state: ResMut<State>,
    mut session: ResMut<Session>,
    keys: Res<ButtonInput<KeyCode>>,
) {
    if state.open && keys.just_pressed(KeyCode::Escape) {
        state.open = false;
        state.dirty = true;
    }
    while let Some(result) = state.worker.as_ref().and_then(Worker::poll) {
        state.dirty = true;
        match result {
            Err(error) => {
                state.checking = false;
                session.append_output(&error);
                state.message = error;
                if let Some(release) = state.release.as_mut() {
                    release.status = "error".into();
                }
            }
            Ok(release) => {
                state.checking = release.status == "downloading";
                state.message = match release.status.as_str() {
                    "current" => format!("You're up to date. Latest release: {}", release.version),
                    "downloading" => format!(
                        "SceneMax {} is available. Downloading and verifying the installer…",
                        release.version
                    ),
                    "ready" => format!("SceneMax {} is ready to install.", release.version),
                    _ => release.notes.clone(),
                };
                if release.status == "ready" && state.notified.as_ref() != Some(&release.version) {
                    state.notified = Some(release.version.clone());
                    if let Some(old) = state.banner.take() {
                        commands.entity(old).despawn();
                    }
                    state.banner = Some(banner(&mut commands, &release.version));
                }
                state.release = Some(release);
            }
        }
    }
    if !state.dirty {
        return;
    }
    state.dirty = false;
    if !state.release.as_ref().is_some_and(|r| r.status == "ready") {
        if let Some(old) = state.banner.take() {
            commands.entity(old).despawn();
        }
        state.notified = None;
    }
    if let Some(old) = state.dialog.take() {
        commands.entity(old).despawn();
    }
    if !state.open {
        return;
    }
    let overlay = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                width: percent(100.),
                height: percent(100.),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                ..default()
            },
            GlobalZIndex(400),
            BackgroundColor(Color::srgba(0., 0., 0., 0.65)),
            TabGroup::modal(),
        ))
        .id();
    let card = commands
        .spawn((
            Node {
                width: px(760.),
                max_width: percent(94.),
                height: px(560.),
                max_height: percent(90.),
                flex_direction: FlexDirection::Column,
                padding: UiRect::all(px(24.)),
                row_gap: px(14.),
                ..default()
            },
            BackgroundColor(BG),
            ChildOf(overlay),
        ))
        .id();
    let header = super::modal::header(&mut commands, card, "SceneMax updates");
    let close = button(&mut commands, header, "Close", UpdateButton);
    commands
        .entity(close)
        .observe(|_: On<PointerClick>, mut state: ResMut<State>| {
            state.open = false;
            state.dirty = true;
        });
    commands.spawn((
        label(&state.message, 15.),
        Node {
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(card),
    ));
    let notes = state
        .release
        .as_ref()
        .map(|r| r.notes.as_str())
        .filter(|n| !n.trim().is_empty())
        .unwrap_or(if state.checking {
            "Release notes will appear here."
        } else {
            "No release notes provided."
        });
    let scroll = property::scroll_column(&mut commands, card);
    commands.spawn((
        label(notes, 14.),
        Node {
            flex_shrink: 0.,
            max_width: percent(100.),
            ..default()
        },
        ChildOf(scroll),
    ));
    if state.release.as_ref().is_some_and(|r| r.status == "ready") {
        install_button(&mut commands, card);
    } else if !state.checking {
        let retry = button(&mut commands, card, "Check again", UpdateButton);
        commands
            .entity(retry)
            .observe(|_: On<PointerClick>, mut state: ResMut<State>| {
                state.check_now();
            });
    }
    state.dialog = Some(overlay);
}

fn install_button(commands: &mut Commands, parent: Entity) {
    let install = button(commands, parent, "Update and restart", UpdateButton);
    commands.entity(install).observe(
        |_: On<PointerClick>, mut queue: ResMut<CommandQueue>, mut state: ResMut<State>| {
            if !state.release.as_ref().is_some_and(|r| r.status == "ready") {
                return;
            }
            state.open = false;
            state.dirty = true;
            queue.0.push_back(Command::RestartForUpdate);
        },
    );
}
fn banner(commands: &mut Commands, version: &str) -> Entity {
    let banner = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                right: px(18.),
                bottom: px(36.),
                padding: UiRect::all(px(14.)),
                column_gap: px(12.),
                align_items: AlignItems::Center,
                ..default()
            },
            BackgroundColor(HEADER),
            GlobalZIndex(100),
        ))
        .id();
    commands.spawn((
        label(format!("SceneMax {version} is ready"), 14.),
        ChildOf(banner),
    ));
    let details = button(commands, banner, "Release notes", UpdateButton);
    commands
        .entity(details)
        .observe(|_: On<PointerClick>, mut state: ResMut<State>| {
            state.open = true;
            state.dirty = true;
        });
    install_button(commands, banner);
    let later = button(commands, banner, "Later", UpdateButton);
    commands
        .entity(later)
        .observe(move |_: On<PointerClick>, mut commands: Commands| {
            commands.entity(banner).insert(Visibility::Hidden);
        });
    banner
}

#[cfg(test)]
mod tests {
    use super::*;

    fn app(state: State) -> App {
        let mut app = App::new();
        app.insert_resource(state)
            .insert_resource(Session::new(scenemax_ide_core::Project::new(
                std::path::PathBuf::new(),
                vec![],
            )))
            .init_resource::<CommandQueue>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_systems(Update, poll);
        app
    }

    #[test]
    fn manual_check_reports_unavailable_and_escape_closes_dialog() {
        let mut state = State::default();
        state.check_now();
        assert!(state.open);
        assert!(!state.checking);
        let mut app = app(state);
        app.update();
        assert!(app.world().resource::<State>().dialog.is_some());
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::Escape);
        app.update();
        assert!(app.world().resource::<State>().dialog.is_none());
        assert!(app.world().resource::<CommandQueue>().0.is_empty());
    }

    #[test]
    fn release_notes_and_install_action_appear_only_when_ready() {
        let mut app = app(State {
            open: true,
            dirty: true,
            checking: true,
            release: Some(Ready {
                version: "autumn".into(),
                status: "downloading".into(),
                notes: "New tools\nEditing fixes".into(),
            }),
            ..default()
        });
        app.update();
        let texts: Vec<_> = app
            .world_mut()
            .query::<&Text>()
            .iter(app.world())
            .map(|t| t.0.clone())
            .collect();
        assert!(texts.iter().any(|t| t.contains("Editing fixes")));
        assert!(!texts.iter().any(|t| t == "Update and restart"));
        {
            let mut state = app.world_mut().resource_mut::<State>();
            state.release.as_mut().unwrap().status = "ready".into();
            state.checking = false;
            state.dirty = true;
        }
        app.update();
        assert!(
            app.world_mut()
                .query::<&Text>()
                .iter(app.world())
                .any(|t| t.0 == "Update and restart")
        );
    }
}
