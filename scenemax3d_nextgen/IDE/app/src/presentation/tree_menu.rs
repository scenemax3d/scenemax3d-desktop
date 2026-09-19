//! Retained, path-targeted project tree context menu and local operation dialogs.
use crate::application::{Command, CommandQueue, Session, ViewChange};
use bevy::{
    input_focus::{FocusCause, InputFocus},
    prelude::*,
    text::EditableText,
};
use scenemax_ide_services::TreeOperation;
use scenemax_ide_ui::{button, label, theme::*};
use std::path::PathBuf;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Dialog {
    AddScene,
    Script,
    Scene,
    Ui,
    Folder,
    Rename,
    Move,
    Delete,
    Reload,
}
#[derive(Component, Clone)]
enum Action {
    Run,
    Save,
    Refresh,
    Copy,
    Explore,
    Prompt(Dialog),
    Confirm,
    Cancel,
}
#[derive(Component)]
pub(crate) struct Input;
#[derive(Component)]
pub(crate) struct Host;
#[derive(Clone)]
struct Target {
    path: PathBuf,
    directory: bool,
    position: Vec2,
}
#[derive(Resource, Default)]
pub(crate) struct State {
    pending_action: Option<Action>,
    target: Option<Target>,
    dialog: Option<Dialog>,
    generation: u64,
}
impl State {
    pub(crate) fn open(&mut self, path: PathBuf, directory: bool, position: Vec2) {
        self.target = Some(Target {
            path,
            directory,
            position,
        });
        self.dialog = None;
        self.generation += 1;
    }
    fn close(&mut self) {
        self.target = None;
        self.dialog = None;
        self.generation += 1;
    }
    pub(crate) fn is_open(&self) -> bool {
        self.target.is_some()
    }
}

pub(crate) fn setup(commands: &mut Commands, root: Entity) {
    commands.spawn((
        Host,
        GlobalZIndex(300),
        Node {
            display: Display::None,
            position_type: PositionType::Absolute,
            width: percent(100.),
            height: percent(100.),
            ..default()
        },
        ChildOf(root),
    ));
}

fn entries(
    directory: bool,
    runnable: bool,
    root: bool,
    main: bool,
) -> Vec<(&'static str, Option<Action>)> {
    let mut items = vec![];
    if runnable {
        items.push(("Run                         F8", Some(Action::Run)));
    }
    if directory {
        items.push(("Add Scene…", Some(Action::Prompt(Dialog::AddScene))));
    } else {
        items.push(("Save", Some(Action::Save)));
        items.push(("Reload from disk", Some(Action::Prompt(Dialog::Reload))));
    }
    items.extend([
        ("Refresh Project Files", Some(Action::Refresh)),
        ("Copy absolute path", Some(Action::Copy)),
        ("Open in explorer", Some(Action::Explore)),
    ]);
    if directory {
        items.extend([
            ("Create New Script", Some(Action::Prompt(Dialog::Script))),
            (
                "Create Designer Document",
                Some(Action::Prompt(Dialog::Scene)),
            ),
            ("Create UI Document", Some(Action::Prompt(Dialog::Ui))),
            ("Create Material Document", None),
            ("Create Weapon", None),
            ("Create Throw Motion", None),
            ("Create IK Asset", None),
            ("Create SkyBox…", None),
            ("Create Bevy Shader Document", None),
            ("Create Environment Shader Document", None),
            ("Create Sub Folder…", Some(Action::Prompt(Dialog::Folder))),
            (
                "Delete Folder…",
                (!root).then_some(Action::Prompt(Dialog::Delete)),
            ),
            (
                "Rename Folder…",
                (!root).then_some(Action::Prompt(Dialog::Rename)),
            ),
            ("Clean Backup Files…", None),
            ("Send To…", None),
            ("Upload To Cloud…", None),
            ("Import Program From Zip File…", None),
            ("Upload Program To Web…", None),
            ("Export to native Android…", None),
        ]);
    } else {
        items.extend([
            ("Delete…", (!main).then_some(Action::Prompt(Dialog::Delete))),
            ("Rename…", Some(Action::Prompt(Dialog::Rename))),
            ("Send To…", None),
            ("Move To…", Some(Action::Prompt(Dialog::Move))),
        ]);
    }
    items
}

fn operation(dialog: Dialog, target: &Target, value: &str, root: &std::path::Path) -> Command {
    let value = value.trim();
    let name = |extension: &str| {
        if value.to_lowercase().ends_with(extension) {
            value.to_owned()
        } else {
            format!("{value}{extension}")
        }
    };
    match dialog {
        Dialog::AddScene => Command::Tree(TreeOperation::AddScene { parent: target.path.clone(), name: value.into() }),
        Dialog::Reload => Command::ReloadPath(target.path.clone()),
        Dialog::Delete => Command::Tree(TreeOperation::Delete(target.path.clone())),
        Dialog::Rename => Command::Tree(TreeOperation::Move { path: target.path.clone(), parent: target.path.parent().unwrap_or(root).to_owned(), name: value.into() }),
        Dialog::Move => Command::Tree(TreeOperation::Move { path: target.path.clone(), parent: root.join(value), name: target.path.file_name().unwrap_or_default().to_string_lossy().into() }),
        Dialog::Script | Dialog::Scene | Dialog::Ui | Dialog::Folder => Command::Tree(TreeOperation::Create {
            parent: target.path.clone(),
            name: match dialog { Dialog::Scene => name(".smdesign"), Dialog::Ui => name(".smui"), _ => value.into() },
            source: match dialog {
                Dialog::Folder => None,
                Dialog::Scene => Some("{\n  \"version\": 1,\n  \"entities\": []\n}\n".into()),
                Dialog::Ui => Some("{\n  \"name\": \"UI\",\n  \"canvasWidth\": 1280,\n  \"canvasHeight\": 720,\n  \"layers\": [{\"name\": \"Layer 1\", \"widgets\": []}]\n}\n".into()),
                _ => Some(String::new()),
            },
        }),
    }
}

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Views<'w, 's> {
    keys: Res<'w, ButtonInput<KeyCode>>,
    focus: Option<ResMut<'w, InputFocus>>,
    clipboard: Option<ResMut<'w, bevy::clipboard::Clipboard>>,
    host: Single<'w, 's, (Entity, &'static mut Node), With<Host>>,
    inputs: Query<'w, 's, &'static EditableText, With<Input>>,
    window: Single<'w, 's, &'static Window, With<bevy::window::PrimaryWindow>>,
}

pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    mut views: Views,
    mut session: ResMut<Session>,
    mut queue: ResMut<CommandQueue>,
    mut changes: MessageReader<ViewChange>,
    mut rendered: Local<u64>,
) {
    if changes
        .read()
        .any(|e| matches!(e, ViewChange::ProjectOpened))
        && state.is_open()
    {
        state.close();
    }
    if state.is_open() && views.keys.just_pressed(KeyCode::Escape) {
        state.close();
    }
    let action = state.pending_action.take().or_else(|| {
        (state.dialog.is_some() && views.keys.just_pressed(KeyCode::Enter))
            .then_some(Action::Confirm)
    });
    if let (Some(action), Some(target)) = (action, state.target.clone()) {
        match action {
            Action::Prompt(dialog) => {
                let dirty = session
                    .workspace
                    .find_document(&target.path)
                    .and_then(|id| session.workspace.document(id).ok())
                    .is_some_and(|d| d.is_dirty());
                if dialog == Dialog::Reload && !dirty {
                    queue.0.push_back(Command::ReloadPath(target.path));
                    state.close();
                } else {
                    state.dialog = Some(dialog);
                    state.generation += 1;
                }
            }
            Action::Confirm => {
                if let Some(dialog) = state.dialog {
                    let value = views
                        .inputs
                        .iter()
                        .next()
                        .map(|i| i.value().to_string())
                        .unwrap_or_default();
                    if !matches!(dialog, Dialog::Delete | Dialog::Reload) && value.trim().is_empty()
                    {
                        session.status = "Enter a name or destination folder".into();
                    } else {
                        queue.0.push_back(operation(
                            dialog,
                            &target,
                            &value,
                            session.workspace.project().root(),
                        ));
                        state.close();
                    }
                }
            }
            other => {
                match other {
                    Action::Run => queue.0.push_back(Command::RunPath(target.path)),
                    Action::Save => queue.0.push_back(Command::SavePath(target.path)),
                    Action::Refresh => queue.0.push_back(Command::Refresh),
                    Action::Explore => queue.0.push_back(Command::Explore(target.path)),
                    Action::Copy => {
                        session.status = match views.clipboard.as_mut().map(|clipboard| {
                            clipboard.set_text(super::projects::display_path(&target.path))
                        }) {
                            Some(Ok(())) => "Absolute path copied".into(),
                            Some(Err(e)) => format!("Could not copy path: {e}"),
                            None => "Clipboard is unavailable".into(),
                        };
                    }
                    _ => {}
                }
                state.close();
            }
        }
    }
    if *rendered == state.generation {
        return;
    }
    *rendered = state.generation;
    let (host, ref mut node) = *views.host;
    commands.entity(host).despawn_children();
    node.display = if state.is_open() {
        Display::Flex
    } else {
        Display::None
    };
    let Some(target) = &state.target else {
        return;
    };
    let backdrop = action_button(&mut commands, host, "", Action::Cancel);
    commands.entity(backdrop).insert((
        Node {
            position_type: PositionType::Absolute,
            width: percent(100.),
            height: percent(100.),
            ..default()
        },
        BackgroundColor(Color::NONE),
        scenemax_ide_ui::NoButtonFeedback,
    ));
    let window = &views.window;
    let width = if state.dialog.is_some() { 430. } else { 310. };
    let natural_height = if target.directory { 700. } else { 287. };
    let height = (window.height() - 24.).clamp(100., natural_height);
    let position = if state.dialog.is_some() {
        Vec2::new(
            (window.width() - width) * 0.5,
            (window.height() - 240.) * 0.5,
        )
    } else {
        Vec2::new(
            target.position.x.min(window.width() - width - 8.).max(4.),
            target.position.y.min(window.height() - height - 8.).max(4.),
        )
    };
    let popup = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                left: px(position.x),
                top: px(position.y),
                width: px(width),
                max_height: px(height),
                flex_direction: FlexDirection::Column,
                padding: px(6.).all(),
                border: px(1.).all(),
                overflow: Overflow::scroll_y(),
                ..default()
            },
            BackgroundColor(PANEL),
            BorderColor::all(EDGE),
            ChildOf(host),
        ))
        .observe(
            |mut event: On<Pointer<Scroll>>,
             mut nodes: Query<(&mut ScrollPosition, &ComputedNode)>| {
                event.propagate(false);
                if let Ok((mut scroll, node)) = nodes.get_mut(event.entity) {
                    let step = if event.unit == bevy::input::mouse::MouseScrollUnit::Line {
                        36.
                    } else {
                        1.
                    };
                    scroll.y = (scroll.y - event.y * step).clamp(
                        0.,
                        ((node.content_size().y - node.size().y) * node.inverse_scale_factor())
                            .max(0.),
                    );
                }
            },
        )
        .id();
    if let Some(dialog) = state.dialog {
        if let Some(focus) = views.focus.as_mut() {
            dialog_view(&mut commands, popup, target, dialog, focus);
        }
    } else {
        for (name, action) in entries(
            target.directory,
            session.workspace.project().scripts().contains(&target.path),
            target.path == session.workspace.project().root(),
            target.path.file_name().is_some_and(|n| n == "main"),
        ) {
            if let Some(action) = action {
                menu_button(&mut commands, popup, name, action);
            } else {
                let text = commands
                    .spawn((
                        label(name, 13.),
                        Node {
                            height: px(25.),
                            flex_shrink: 0.,
                            padding: UiRect::horizontal(px(8.)),
                            ..default()
                        },
                        ChildOf(popup),
                    ))
                    .id();
                commands.entity(text).insert(TextColor(MUTED));
            }
        }
    }
}

fn menu_button(commands: &mut Commands, host: Entity, name: &str, action: Action) {
    let row = action_button(commands, host, name, action);
    commands.entity(row).insert(Node {
        height: px(25.),
        flex_shrink: 0.,
        align_items: AlignItems::Center,
        padding: UiRect::horizontal(px(8.)),
        ..default()
    });
}

fn dialog_view(
    commands: &mut Commands,
    host: Entity,
    target: &Target,
    dialog: Dialog,
    focus: &mut InputFocus,
) {
    commands
        .entity(host)
        .insert(bevy::input_focus::tab_navigation::TabGroup::modal());
    let title = match dialog {
        Dialog::AddScene => "Add Scene",
        Dialog::Script => "Create New Script",
        Dialog::Scene => "Create Designer Document",
        Dialog::Ui => "Create UI Document",
        Dialog::Folder => "Create Sub Folder",
        Dialog::Rename => "Rename",
        Dialog::Move => "Move To",
        Dialog::Delete => "Delete",
        Dialog::Reload => "Reload from disk",
    };
    commands.spawn((label(title, 15.), ChildOf(host)));
    commands.spawn((
        label(
            target
                .path
                .file_name()
                .unwrap_or_default()
                .to_string_lossy(),
            12.,
        ),
        ChildOf(host),
    ));
    if matches!(dialog, Dialog::Delete | Dialog::Reload) {
        let description = if dialog == Dialog::Delete {
            "Remove this item and its contents?\nDeleted items are kept in .scenemax-studio trash."
        } else {
            "Discard unsaved changes and reload from disk?"
        };
        commands.spawn((label(description, 13.), ChildOf(host)));
    } else {
        commands.spawn((
            label(
                if dialog == Dialog::Move {
                    "Destination folder (relative to project or absolute)"
                } else {
                    "Name"
                },
                12.,
            ),
            ChildOf(host),
        ));
        let value = if dialog == Dialog::Rename {
            target
                .path
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .into_owned()
        } else {
            String::new()
        };
        let input = commands
            .spawn((
                Input,
                bevy::input_focus::tab_navigation::TabIndex(0),
                EditableText::new(value),
                Node {
                    width: percent(100.),
                    padding: px(6.).all(),
                    margin: UiRect::vertical(px(8.)),
                    ..default()
                },
                TextFont {
                    font: bevy::text::FontSource::SansSerif,
                    font_size: FontSize::Px(13.),
                    ..default()
                },
                TextColor(INK),
                scenemax_ide_ui::theme::TEXT_CURSOR_STYLE,
                BackgroundColor(BG),
                ChildOf(host),
            ))
            .id();
        focus.set(input, FocusCause::Navigated);
    }
    let buttons = commands
        .spawn((
            Node {
                justify_content: JustifyContent::End,
                column_gap: px(8.),
                margin: UiRect::top(px(12.)),
                ..default()
            },
            ChildOf(host),
        ))
        .id();
    action_button(commands, buttons, "Cancel", Action::Cancel);
    action_button(
        commands,
        buttons,
        if dialog == Dialog::Delete {
            "Delete"
        } else if dialog == Dialog::Reload {
            "Reload"
        } else {
            "OK"
        },
        Action::Confirm,
    );
}

// Wait for a completed click before replacing the popup with a dialog. Reacting to
// Interaction::Pressed lets the newly spawned backdrop inherit the held mouse
// button and immediately dismiss the dialog on the following frame.
fn action_button(commands: &mut Commands, parent: Entity, caption: &str, action: Action) -> Entity {
    let row = button(commands, parent, caption, action.clone());
    commands.entity(row).observe(
        move |mut event: On<Pointer<Click>>, mut state: ResMut<State>| {
            if event.button == PointerButton::Primary {
                event.propagate(false);
                state.pending_action = Some(action.clone());
            }
        },
    );
    row
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn dialog_name_field_accepts_native_keyboard_after_mouse_focus() {
        use bevy::{
            ecs::system::RunSystemOnce,
            input::{
                ButtonState,
                keyboard::{Key, KeyboardInput},
            },
            input_focus::{InputFocusVisible, tab_navigation::TabNavigationPlugin},
            picking::{
                backend::HitData,
                pointer::{Location, PointerId},
            },
            text::{FontCx, LayoutCx},
            ui_widgets::EditableTextInputPlugin,
        };
        for dialog in [
            Dialog::Script,
            Dialog::Scene,
            Dialog::Ui,
            Dialog::Folder,
            Dialog::AddScene,
            Dialog::Rename,
            Dialog::Move,
        ] {
            let mut app = App::new();
            app.init_resource::<State>()
                .init_resource::<InputFocus>()
                .init_resource::<InputFocusVisible>()
                .init_resource::<ButtonInput<Key>>()
                .init_resource::<UiScale>()
                .init_resource::<FontCx>()
                .init_resource::<LayoutCx>()
                .init_resource::<bevy::clipboard::Clipboard>()
                .add_message::<KeyboardInput>()
                .add_plugins((TabNavigationPlugin, EditableTextInputPlugin));
            let window = app
                .world_mut()
                .spawn((Window::default(), bevy::window::PrimaryWindow))
                .id();
            let host = app
                .world_mut()
                .spawn((Node::default(), ChildOf(window)))
                .id();
            let target = Target {
                path: "project/scripts".into(),
                directory: true,
                position: Vec2::ZERO,
            };
            app.world_mut()
                .resource_scope(|world, mut focus: Mut<InputFocus>| {
                    dialog_view(&mut world.commands(), host, &target, dialog, &mut focus);
                });
            app.world_mut().flush();
            let field = app.world().resource::<InputFocus>().get().unwrap();
            // Click-to-focus must recover even after focus has moved elsewhere.
            app.world_mut().resource_mut::<InputFocus>().clear();
            app.world_mut().trigger(Pointer::new(
                PointerId::Mouse,
                Location {
                    target: bevy::camera::NormalizedRenderTarget::None {
                        width: 800,
                        height: 600,
                    },
                    position: Vec2::new(20., 20.),
                },
                Press {
                    button: PointerButton::Primary,
                    count: 1,
                    hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
                },
                field,
            ));
            app.world_mut().flush();
            assert_eq!(
                app.world().resource::<InputFocus>().get(),
                Some(field),
                "{dialog:?}"
            );
            app.world_mut()
                .run_system_once(bevy::text::apply_text_edits)
                .unwrap();
            for character in ["n", "e", "w"] {
                app.world_mut().write_message(KeyboardInput {
                    key_code: KeyCode::KeyN,
                    logical_key: Key::Character(character.into()),
                    text: Some(character.into()),
                    state: ButtonState::Pressed,
                    repeat: false,
                    window,
                });
                app.world_mut()
                    .run_system_once(bevy::input_focus::dispatch_focused_input::<KeyboardInput>)
                    .unwrap();
                app.world_mut()
                    .run_system_once(bevy::text::apply_text_edits)
                    .unwrap();
            }
            assert!(
                app.world()
                    .get::<EditableText>(field)
                    .unwrap()
                    .value()
                    .to_string()
                    .contains("new"),
                "{dialog:?}"
            );
        }
    }
    fn click(app: &mut App, entity: Entity) {
        use bevy::picking::{
            backend::HitData,
            pointer::{Location, PointerId},
        };
        app.world_mut().trigger(Pointer::new(
            PointerId::Mouse,
            Location {
                target: bevy::camera::NormalizedRenderTarget::None {
                    width: 800,
                    height: 600,
                },
                position: Vec2::ZERO,
            },
            Click {
                button: PointerButton::Primary,
                count: 1,
                duration: std::time::Duration::ZERO,
                hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
            },
            entity,
        ));
    }
    #[test]
    fn popup_targets_clicked_file_and_dialog_escape_dismisses() {
        let mut app = App::new();
        let mut session = Session::new(scenemax_ide_core::Project::new(
            "project".into(),
            vec!["project/second.code".into()],
        ));
        let first = session
            .workspace
            .open_document(
                scenemax_ide_core::Document::from_bytes("project/first.code".into(), vec![])
                    .unwrap(),
            )
            .unwrap();
        app.insert_resource(session)
            .init_resource::<State>()
            .init_resource::<CommandQueue>()
            .init_resource::<InputFocus>()
            .init_resource::<bevy::clipboard::Clipboard>()
            .init_resource::<bevy::text::FontCx>()
            .init_resource::<bevy::text::LayoutCx>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_message::<ViewChange>()
            .add_systems(
                Update,
                (scenemax_ide_ui::commit_pending_input, update).chain(),
            );
        app.world_mut()
            .spawn((Window::default(), bevy::window::PrimaryWindow));
        app.world_mut().spawn((Host, Node::default()));
        app.world_mut().resource_mut::<State>().open(
            "project/second.code".into(),
            false,
            Vec2::new(100., 200.),
        );
        app.update();
        let backdrop = app
            .world_mut()
            .query::<(Entity, &Action)>()
            .iter(app.world())
            .find(|(_, a)| matches!(a, Action::Cancel))
            .unwrap()
            .0;
        assert!(
            app.world()
                .get::<scenemax_ide_ui::NoButtonFeedback>(backdrop)
                .is_some()
        );
        // Hover changes must not rebuild the popup or its full-window input surface.
        app.world_mut()
            .entity_mut(backdrop)
            .insert(Interaction::Hovered);
        app.update();
        assert!(
            app.world()
                .get::<scenemax_ide_ui::NoButtonFeedback>(backdrop)
                .is_some()
        );
        assert!(app.world().resource::<State>().is_open());
        let row = app
            .world_mut()
            .query::<(Entity, &Action)>()
            .iter(app.world())
            .find(|(_, a)| matches!(a, Action::Run))
            .unwrap()
            .0;
        click(&mut app, row);
        app.update();
        assert!(
            matches!(app.world_mut().resource_mut::<CommandQueue>().0.pop_front(), Some(Command::RunPath(p)) if p == std::path::Path::new("project/second.code"))
        );
        assert_eq!(
            app.world().resource::<Session>().workspace.active_id(),
            Some(first)
        );
        assert!(!app.world().resource::<State>().is_open());
        app.world_mut()
            .resource_mut::<State>()
            .open("project/scripts".into(), true, Vec2::ZERO);
        app.update();
        let row = app
            .world_mut()
            .query::<(Entity, &Action)>()
            .iter(app.world())
            .find(|(_, a)| matches!(a, Action::Prompt(Dialog::Script)))
            .unwrap()
            .0;
        app.world_mut().entity_mut(row).insert(Interaction::Pressed);
        app.update();
        assert!(app.world().resource::<State>().dialog.is_none());
        click(&mut app, row);
        app.update();
        let field = app.world().resource::<InputFocus>().get().unwrap();
        assert!(app.world().get::<Input>(field).is_some());
        let backdrop = app
            .world_mut()
            .query_filtered::<Entity, With<scenemax_ide_ui::NoButtonFeedback>>()
            .single(app.world())
            .unwrap();
        app.world_mut()
            .entity_mut(backdrop)
            .insert(Interaction::Pressed);
        for character in ["n", "e", "w", "_", "s", "c", "r", "i", "p", "t"] {
            app.world_mut()
                .get_mut::<EditableText>(field)
                .unwrap()
                .queue_edit(bevy::text::TextEdit::Insert(character.into()));
            app.world_mut()
                .write_message(ViewChange::ProjectTreeChanged);
            app.update();
            assert_eq!(app.world().resource::<State>().dialog, Some(Dialog::Script));
            assert_eq!(app.world().resource::<InputFocus>().get(), Some(field));
        }
        assert_eq!(
            app.world().get::<EditableText>(field).unwrap().value(),
            "new_script"
        );
        let confirm = app
            .world_mut()
            .query::<(Entity, &Action)>()
            .iter(app.world())
            .find(|(_, a)| matches!(a, Action::Confirm))
            .unwrap()
            .0;
        click(&mut app, confirm);
        app.update();
        assert!(
            matches!(app.world_mut().resource_mut::<CommandQueue>().0.pop_front(), Some(Command::Tree(TreeOperation::Create { name, parent, .. })) if name == "new_script" && parent == std::path::Path::new("project/scripts"))
        );
        assert!(!app.world().resource::<State>().is_open());
        app.world_mut()
            .resource_mut::<State>()
            .open("project/scripts".into(), true, Vec2::ZERO);
        app.update();
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::Escape);
        app.update();
        assert!(!app.world().resource::<State>().is_open());
        assert!(app.world().resource::<CommandQueue>().0.is_empty());
    }
    #[test]
    fn menus_are_contextual_and_protect_root_and_main() {
        assert!(entries(false, true, false, true).iter().any(|(name, action)| *name == "Run                         F8" && action.is_some()));
        assert!(
            entries(false, true, false, true)
                .iter()
                .any(|(name, action)| *name == "Delete…" && action.is_none())
        );
        assert!(
            entries(true, false, true, false)
                .iter()
                .any(|(name, action)| *name == "Delete Folder…" && action.is_none())
        );
        assert!(
            !entries(false, false, false, false)
                .iter()
                .any(|(_, a)| matches!(a, Some(Action::Run)))
        );
        assert!(
            !entries(true, false, false, false)
                .iter()
                .any(|(name, _)| name.contains("Java"))
        );
    }
    #[test]
    fn creation_and_rename_use_clicked_path() {
        let target = Target {
            path: PathBuf::from("project/scripts/selected"),
            directory: true,
            position: Vec2::ZERO,
        };
        let Command::Tree(TreeOperation::Create {
            parent,
            name,
            source,
        }) = operation(
            Dialog::Scene,
            &target,
            "new_scene",
            std::path::Path::new("project"),
        )
        else {
            panic!()
        };
        assert_eq!(parent, target.path);
        assert_eq!(name, "new_scene.smdesign");
        assert!(
            serde_json::from_str::<serde_json::Value>(&source.unwrap()).unwrap()["entities"]
                .is_array()
        );
    }
}
