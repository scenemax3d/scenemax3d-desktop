//! Retained import settings and asynchronous service dispatch.

use crate::application::{Command, CommandQueue, Session, ViewChange};

use bevy::{
    input_focus::{
        FocusCause, InputFocus,
        tab_navigation::{TabGroup, TabIndex},
    },
    prelude::*,
    text::EditableText,
};

use scenemax_ide_services::imports::{self, Kind, Request};

use scenemax_ide_ui::{button, label, theme::*};

use std::path::PathBuf;

#[derive(Clone)]

enum Action {
    Open(Kind),
    Browse,
    Import,
    Close,
    Explore,
}

#[derive(Component, Clone, Copy, PartialEq)]

enum Field {
    Source,
    Name,
    Rows,
    Cols,
    Width,
    Height,
    Clip,
    Scale,
}

#[derive(Component)]

struct Status;

enum Result {
    Pick(std::io::Result<Option<PathBuf>>),
    Import(std::io::Result<imports::Outcome>),
}

#[derive(Resource, Default)]

pub(crate) struct State {
    action: Option<Action>,

    host: Option<Entity>,

    project: PathBuf,

    kind: Option<Kind>,

    task: Option<bevy::tasks::Task<Result>>,
}

impl State {
    pub(crate) fn preview(&mut self, kind: Kind) {
        self.action = Some(Action::Open(kind));
    }
    pub(crate) fn is_open(&self) -> bool {
        self.host.is_some()
    }
}

pub(crate) fn menu_item(
    commands: &mut Commands,
    parent: Entity,
    title: &str,
    command: &str,
) -> Option<Entity> {
    let action = match command {
        "add_model" => Action::Open(Kind::Model),
        "add_sprite" => Action::Open(Kind::Sprite),

        "import_audio" => Action::Open(Kind::Audio),
        "import_video" => Action::Open(Kind::Video),

        "import_animation" => Action::Open(Kind::Animation),
        "import_effekseer" => Action::Open(Kind::Effect),

        "open_assets_folder" => Action::Explore,

        _ => return None,
    };

    let row = control(commands, parent, title, action.clone());
    // Menu chrome closes on press, so dispatch before the release can lose its target.
    commands
        .entity(row)
        .observe(move |mut e: On<Pointer<Press>>, mut state: ResMut<State>| {
            if e.button == PointerButton::Primary {
                e.propagate(false);
                state.action = Some(action.clone());
            }
        });
    Some(row)
}

fn control(commands: &mut Commands, parent: Entity, title: &str, action: Action) -> Entity {
    let entity = button(commands, parent, title, Name::new(title.to_owned()));

    commands
        .entity(entity)
        .observe(move |mut e: On<Pointer<Click>>, mut state: ResMut<State>| {
            if e.button == PointerButton::Primary {
                e.propagate(false);
                state.action = Some(action.clone());
            }
        });
    entity
}

fn field(
    commands: &mut Commands,
    parent: Entity,
    caption: &str,
    kind: Field,
    initial: &str,
) -> Entity {
    commands.spawn((label(caption, 12.), ChildOf(parent)));

    commands
        .spawn((
            kind,
            TabIndex(0),
            EditableText::new(initial),
            Node {
                width: percent(100.),
                padding: px(6.).all(),
                min_height: px(30.),
                flex_shrink: 0.,
                ..default()
            },
            TextFont {
                font: bevy::text::FontSource::SansSerif,
                font_size: FontSize::Px(13.),
                ..default()
            },
            TextColor(INK),
            TEXT_CURSOR_STYLE,
            BackgroundColor(BG),
            ChildOf(parent),
        ))
        .id()
}

fn dialog(commands: &mut Commands, kind: Kind, focus: &mut InputFocus) -> Entity {
    let host = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                width: percent(100.),
                height: percent(100.),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                ..default()
            },
            GlobalZIndex(300),
            BackgroundColor(Color::srgba(0., 0., 0., 0.35)),
            TabGroup::modal(),
        ))
        .id();

    let panel = commands
        .spawn((
            Node {
                width: px(540.),
                max_height: percent(90.),
                padding: px(16.).all(),
                row_gap: px(6.),
                flex_direction: FlexDirection::Column,
                overflow: Overflow::scroll_y(),
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(host),
        ))
        .id();

    commands.spawn((label(kind.title(), 17.), ChildOf(panel)));

    commands.spawn((
        label(format!("Formats: {}", kind.extensions().join(", ")), 12.),
        ChildOf(panel),
    ));

    let first = field(commands, panel, "Source file", Field::Source, "");

    control(commands, panel, "Browse…", Action::Browse);

    field(commands, panel, "Asset name", Field::Name, "");

    match kind {
        Kind::Sprite => {
            for (title, kind, value) in [
                ("Rows", Field::Rows, "1"),
                ("Columns", Field::Cols, "1"),
                ("Frame width", Field::Width, "1"),
                ("Frame height (0 = automatic)", Field::Height, "0"),
            ] {
                field(commands, panel, title, kind, value);
            }
        }

        Kind::Model => {
            field(commands, panel, "Uniform scale", Field::Scale, "1");
        }

        Kind::Animation => {
            field(
                commands,
                panel,
                "Clip name (blank = first clip)",
                Field::Clip,
                "",
            );
        }

        _ => {}
    }

    commands.spawn((Status, label("", 12.), ChildOf(panel)));

    let buttons = commands
        .spawn((
            Node {
                justify_content: JustifyContent::End,
                column_gap: px(8.),
                ..default()
            },
            ChildOf(panel),
        ))
        .id();

    control(commands, buttons, "Cancel", Action::Close);
    control(commands, buttons, "Import", Action::Import);

    focus.set(first, FocusCause::Navigated);
    host
}

#[derive(bevy::ecs::system::SystemParam)]

pub(crate) struct Views<'w, 's> {
    fields: Query<'w, 's, (&'static Field, &'static mut EditableText)>,

    status: Query<'w, 's, &'static mut Text, With<Status>>,

    focus: Option<ResMut<'w, InputFocus>>,

    keys: Res<'w, ButtonInput<KeyCode>>,
    designers: Query<'w, 's, &'static mut super::designer::Designer>,
    scene: Option<ResMut<'w, super::scene3d::SceneState>>,
}

pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    mut views: Views,

    mut session: ResMut<Session>,
    mut queue: ResMut<CommandQueue>,
    mut changes: MessageWriter<ViewChange>,
) {
    let mut message = None;

    if let Some(task) = &mut state.task
        && let Some(result) = bevy::tasks::block_on(bevy::tasks::poll_once(task))
    {
        state.task = None;

        match result {
            Result::Pick(Ok(Some(path))) => {
                for (field, mut input) in &mut views.fields {
                    if *field == Field::Source {
                        input.editor_mut().set_text(&path.to_string_lossy());
                    }

                    if *field == Field::Name && input.value().to_string().trim().is_empty() {
                        let name: String = path
                            .file_stem()
                            .unwrap_or_default()
                            .to_string_lossy()
                            .chars()
                            .map(|c| {
                                if c.is_ascii_alphanumeric() || c == '_' || c == '-' {
                                    c
                                } else {
                                    '_'
                                }
                            })
                            .collect();

                        input.editor_mut().set_text(&name);
                    }
                }

                message = Some("Ready to import".into());
            }

            Result::Pick(Ok(None)) => {
                message = Some("File selection cancelled".into());
            }

            Result::Pick(Err(e)) | Result::Import(Err(e)) => {
                message = Some(e.to_string());
            }

            Result::Import(Ok(outcome)) => {
                session.status = format!("Imported {}", outcome.asset.display());

                if session.workspace.project().root() == state.project {
                    changes.write(ViewChange::ProjectIndexInvalidated);

                    for mut designer in &mut views.designers {
                        designer.reload_assets();
                    }
                    if let Some(scene) = &mut views.scene {
                        scene.reload_assets();
                    }
                    queue.0.push_back(Command::Refresh);
                }

                state.action = Some(Action::Close);
            }
        }
    }

    if views.keys.just_pressed(KeyCode::Escape) && state.host.is_some() && state.task.is_none() {
        state.action = Some(Action::Close);
    }

    if let Some(action) = state.action.take() {
        match action {
            Action::Open(Kind::Effect) if state.host.is_none() => { super::effect_import::open(&mut session, &mut changes); }
            Action::Open(Kind::Sprite) if state.host.is_none() => { super::sprite_import::open(&mut session, &mut changes); }
            Action::Open(Kind::Model) if state.host.is_none() => { super::model_import::open(&mut session,&mut changes); }
            Action::Open(kind) if state.host.is_none() => {
                state.project = session.workspace.project().root().to_owned();
                state.kind = Some(kind);

                let mut fallback = InputFocus::default();
                state.host = Some(dialog(
                    &mut commands,
                    kind,
                    views.focus.as_deref_mut().unwrap_or(&mut fallback),
                ));
            }

            Action::Explore => {
                queue.0.push_back(Command::Explore(
                    session.workspace.project().root().join("resources"),
                ));
            }

            Action::Close if state.task.is_none() => {
                if let Some(host) = state.host.take() {
                    commands.entity(host).despawn();
                }

                state.kind = None;
                if let Some(focus) = &mut views.focus {
                    focus.clear();
                }
            }

            Action::Browse if state.task.is_none() => {
                if let Some(kind) = state.kind {
                    state.task = Some(
                        bevy::tasks::IoTaskPool::get()
                            .spawn(async move { Result::Pick(imports::pick_file(kind)) }),
                    );

                    message = Some("Choose a file…".into());
                }
            }

            Action::Import if state.task.is_none() => {
                if let Some(kind) = state.kind {
                    let value = |f| {
                        views
                            .fields
                            .iter()
                            .find(|(field, _)| **field == f)
                            .map(|(_, input)| input.value().to_string())
                            .unwrap_or_default()
                    };

                    let parse = |f, default: &str| {
                        let v = value(f);
                        if v.is_empty() { default.to_owned() } else { v }
                    };

                    let request = (|| -> std::result::Result<Request, String> {
                        Ok(Request {
                            kind,
                            model: None,
            effect: None,
                            source: value(Field::Source).into(),
                            name: value(Field::Name),

                            rows: parse(Field::Rows, "1")
                                .parse()
                                .map_err(|_| "Rows must be a positive integer")?,

                            cols: parse(Field::Cols, "1")
                                .parse()
                                .map_err(|_| "Columns must be a positive integer")?,

                            frame_width: parse(Field::Width, "1")
                                .parse()
                                .map_err(|_| "Invalid frame width")?,

                            frame_height: parse(Field::Height, "0")
                                .parse()
                                .map_err(|_| "Invalid frame height")?,

                            scale: parse(Field::Scale, "1")
                                .parse()
                                .map_err(|_| "Invalid model scale")?,
                            clip: value(Field::Clip),
                        })
                    })();

                    match request {
                        Ok(request) => {
                            let root = state.project.clone();

                            state.task = Some(bevy::tasks::IoTaskPool::get().spawn(async move {
                                Result::Import(imports::import(&root, &request))
                            }));

                            message = Some("Importing…".into());
                        }
                        Err(e) => message = Some(e),
                    }
                }
            }

            _ => {}
        }
    }

    if let Some(message) = message {
        for mut text in &mut views.status {
            text.0 = message.clone();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn assets_menu_dispatches_before_the_popup_closes_on_press() {
        use bevy::picking::{
            backend::HitData,
            pointer::{Location, PointerId},
        };
        let mut world = World::new();
        world.register_component::<Window>();
        world.init_resource::<State>();
        let host = world.spawn(Node::default()).id();
        let row = menu_item(&mut world.commands(), host, "Import Sprite", "add_sprite").unwrap();
        world.flush();
        world.trigger(Pointer::new(
            PointerId::Mouse,
            Location {
                target: bevy::camera::NormalizedRenderTarget::None {
                    width: 800,
                    height: 600,
                },
                position: Vec2::ZERO,
            },
            Press {
                button: PointerButton::Primary,
                count: 1,
                hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
            },
            row,
        ));
        world.flush();
        assert!(matches!(
            world.resource::<State>().action,
            Some(Action::Open(Kind::Sprite))
        ));
    }
    #[test]
    fn import_dialog_keeps_fields_and_commits_to_the_captured_project() {
        bevy::tasks::IoTaskPool::get_or_init(Default::default);
        let project = tempfile::tempdir().unwrap();
        let source = tempfile::tempdir().unwrap();
        let file = source.path().join("sound.wav");
        std::fs::write(&file, b"audio fixture").unwrap();
        let mut app = App::new();
        app.insert_resource(Session::new(scenemax_ide_core::Project::new(
            project.path().to_owned(),
            vec![],
        )))
        .init_resource::<State>()
        .init_resource::<InputFocus>()
        .init_resource::<CommandQueue>()
        .init_resource::<ButtonInput<KeyCode>>()
        .add_message::<ViewChange>()
        .add_systems(Update, update);
        app.world_mut().resource_mut::<State>().preview(Kind::Audio);
        app.update();
        let host = app.world().resource::<State>().host.unwrap();
        let world = app.world_mut();
        for (field, mut input) in world.query::<(&Field, &mut EditableText)>().iter_mut(world) {
            input.editor_mut().set_text(if *field == Field::Source {
                file.to_str().unwrap()
            } else {
                "voice"
            });
        }
        app.update();
        assert_eq!(app.world().resource::<State>().host, Some(host));
        app.world_mut().resource_mut::<State>().action = Some(Action::Import);
        for _ in 0..400 {
            app.update();
            if !app.world().resource::<State>().is_open() {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(5));
        }
        assert!(!app.world().resource::<State>().is_open());
        let index: serde_json::Value = serde_json::from_slice(
            &std::fs::read(project.path().join("resources/audio/audio-ext.json")).unwrap(),
        )
        .unwrap();
        assert_eq!(index["sounds"][0]["name"], "voice");
    }
}
