use crate::{
    StudioPlugin,
    application::{Command, CommandQueue, EditorServices, Session},
    presentation::{components::Editor, input::Action},
};
use bevy::{prelude::*, text::EditableText, window::WindowCloseRequested};
use scenemax_ide_services::Filesystem;
use std::path::PathBuf;

#[test]
fn mouse_caret_press_keeps_focus_and_accepts_native_typing() {
    use bevy::{
        ecs::system::RunSystemOnce,
        input::{
            ButtonState,
            keyboard::{Key, KeyboardInput},
        },
        input_focus::{InputFocus, InputFocusVisible, tab_navigation::TabNavigationPlugin},
        picking::{
            backend::HitData,
            pointer::{Location, PointerId},
        },
        text::{FontCx, LayoutCx},
        ui_widgets::EditableTextInputPlugin,
    };
    let mut app = App::new();
    app.init_resource::<InputFocus>()
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
    #[derive(Component)]
    struct TestEditor;
    let editor = scenemax_ide_ui::spawn_editor(
        &mut app.world_mut().commands(),
        host,
        TestEditor,
        "abc",
        true,
    );
    app.world_mut().flush();
    app.world_mut()
        .resource_mut::<InputFocus>()
        .set(editor, bevy::input_focus::FocusCause::Navigated);
    for count in [1, 2] {
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
                count,
                hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
            },
            editor,
        ));
        app.world_mut().flush(); // Includes TabNavigationPlugin's deferred AcquireFocus event.
        assert_eq!(app.world().resource::<InputFocus>().get(), Some(editor));
        assert!(
            !app.world()
                .get::<EditableText>(editor)
                .unwrap()
                .pending_edits
                .is_empty()
        );
        app.world_mut()
            .run_system_once(bevy::text::apply_text_edits)
            .unwrap();
        app.world_mut().write_message(KeyboardInput {
            key_code: KeyCode::KeyX,
            logical_key: Key::Character("x".into()),
            text: Some("x".into()),
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
        assert!(
            app.world()
                .get::<EditableText>(editor)
                .unwrap()
                .value()
                .to_string()
                .contains('x')
        );
    }
}

pub(crate) fn app() -> (App, tempfile::TempDir, PathBuf) {
    let dir = tempfile::tempdir().unwrap();
    std::fs::create_dir(dir.path().join("scripts")).unwrap();
    let path = dir.path().join("scripts/test.code");
    std::fs::write(&path, "// original\n").unwrap();
    let mut session = Session::new(Filesystem::open_project(dir.path()).unwrap());
    session
        .workspace
        .open_document(Filesystem::open_document(session.workspace.project(), &path).unwrap())
        .unwrap();
    let mut app = App::new();
    app.insert_resource(session)
        .insert_resource(EditorServices::new(PathBuf::new()).unwrap())
        .init_resource::<ButtonInput<KeyCode>>()
        .add_message::<WindowCloseRequested>()
        .add_message::<AppExit>()
        .init_resource::<Time>()
        .add_plugins(StudioPlugin);
    app.update();
    (app, dir, path)
}

#[test]
fn project_tree_wheel_from_child_scrolls_viewport_and_has_native_scrollbar() {
    use bevy::picking::{
        events::Scroll,
        pointer::{Location, PointerId},
    };
    let (mut app, _dir, _) = app();
    let world = app.world_mut();
    let browser = world
        .query_filtered::<Entity, With<crate::presentation::components::Browser>>()
        .single(world)
        .unwrap();
    world.entity_mut(browser).insert(ComputedNode {
        size: Vec2::new(250., 100.),
        content_size: Vec2::new(250., 1000.),
        inverse_scale_factor: 1.,
        ..default()
    });
    let child = world.spawn((Node::default(), ChildOf(browser))).id();
    assert!(
        world
            .query::<&bevy::ui_widgets::Scrollbar>()
            .iter(world)
            .any(|bar| bar.target == browser)
    );
    for (unit, y, expected) in [
        (bevy::input::mouse::MouseScrollUnit::Line, -3., 120.),
        (bevy::input::mouse::MouseScrollUnit::Pixel, -50., 170.),
        (bevy::input::mouse::MouseScrollUnit::Line, -100., 900.),
        (bevy::input::mouse::MouseScrollUnit::Line, 100., 0.),
    ] {
        world.trigger(Pointer::new(
            PointerId::Mouse,
            Location {
                target: bevy::camera::NormalizedRenderTarget::None {
                    width: 1000,
                    height: 1000,
                },
                position: Vec2::ZERO,
            },
            Scroll {
                unit,
                x: 0.,
                y,
                hit: bevy::picking::backend::HitData::new(Entity::PLACEHOLDER, 0., None, None),
                phase: bevy::input::touch::TouchPhase::Moved,
            },
            child,
        ));
        assert_eq!(world.get::<ScrollPosition>(browser).unwrap().y, expected);
    }
}
fn edit(app: &mut App, source: &str) {
    let world = app.world_mut();
    world
        .query_filtered::<&mut EditableText, With<Editor>>()
        .single_mut(world)
        .unwrap()
        .editor_mut()
        .set_text(source);
}
#[test]
fn save_button_includes_latest_widget_text() {
    let (mut app, _dir, path) = app();
    edit(&mut app, "// latest שלום\n");
    let world = app.world_mut();
    let id = world
        .query::<(Entity, &Action)>()
        .iter(world)
        .find(|(_, action)| matches!(action, Action::Save))
        .unwrap()
        .0;
    world.entity_mut(id).insert(Interaction::Pressed);
    app.update();
    finish_io(&mut app);
    assert_eq!(std::fs::read_to_string(path).unwrap(), "// latest שלום\n");
    assert!(
        !app.world()
            .resource::<Session>()
            .workspace
            .has_dirty_documents()
    );
}
#[test]
fn closing_dirty_widget_preserves_document_and_disk() {
    let (mut app, _dir, path) = app();
    edit(&mut app, "// unsaved\n");
    app.world_mut().write_message(WindowCloseRequested {
        window: Entity::PLACEHOLDER,
    });
    app.update();
    let session = app.world().resource::<Session>();
    assert!(session.closing);
    assert!(session.workspace.has_dirty_documents());
    assert_eq!(std::fs::read_to_string(path).unwrap(), "// original\n");
}
#[test]
fn switching_project_rejects_dirty_buffers() {
    let (mut app, dir, _path) = app();
    edit(&mut app, "// unsaved\n");
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::OpenProject(dir.path().to_owned()));
    app.update();
    let session = app.world().resource::<Session>();
    assert!(session.workspace.has_dirty_documents());
    assert!(session.status.contains("Save your documents"));
}
#[test]
fn command_and_keyboard_save_share_the_same_behavior() {
    let (mut app, _dir, path) = app();
    edit(&mut app, "// command\n");
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Save);
    app.update();
    finish_io(&mut app);
    assert_eq!(std::fs::read_to_string(&path).unwrap(), "// command\n");
    edit(&mut app, "// shortcut\n");
    let mut keys = app.world_mut().resource_mut::<ButtonInput<KeyCode>>();
    keys.press(KeyCode::ControlLeft);
    keys.press(KeyCode::KeyS);
    app.update();
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .clear();
    finish_io(&mut app);
    assert_eq!(std::fs::read_to_string(path).unwrap(), "// shortcut\n");
}

fn finish_io(app: &mut App) {
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
    while app
        .world()
        .resource::<EditorServices>()
        .storage
        .is_pending()
    {
        assert!(
            std::time::Instant::now() < deadline,
            "File worker did not finish"
        );
        std::thread::sleep(std::time::Duration::from_millis(1));
        app.update();
    }
}

fn dispatch(app: &mut App, command: Command) {
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(command);
    app.update();
}
#[test]
fn undo_redo_updates_the_retained_widget_without_creating_feedback_edits() {
    use crate::application::EditCommand;
    let (mut app, _dir, _path) = app();
    edit(&mut app, "changed");
    app.update();
    dispatch(&mut app, Command::Edit(EditCommand::Undo));
    app.update();
    let session = app.world().resource::<Session>();
    assert_eq!(
        session
            .workspace
            .document(session.workspace.require_active().unwrap())
            .unwrap()
            .text(),
        "// original\n"
    );
    dispatch(&mut app, Command::Edit(EditCommand::Redo));
    app.update();
    let world = app.world_mut();
    assert_eq!(
        world
            .query_filtered::<&EditableText, With<Editor>>()
            .single(world)
            .unwrap()
            .value()
            .to_string(),
        "changed"
    );
}
#[test]
fn new_script_save_close_and_reopen_preserve_source() {
    let (mut app, dir, _path) = app();
    dispatch(&mut app, Command::Create("new.code".into()));
    finish_io(&mut app);
    // Choose the newly created editor among retained tabs.
    let id = app
        .world()
        .resource::<Session>()
        .workspace
        .require_active()
        .unwrap();
    let world = app.world_mut();
    for (marker, mut input) in world
        .query::<(&Editor, &mut EditableText)>()
        .iter_mut(world)
    {
        if marker.0 == id {
            input.editor_mut().set_text("// new source");
        }
    }
    dispatch(&mut app, Command::Save);
    finish_io(&mut app);
    dispatch(&mut app, Command::CloseTab);
    assert!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .is_err()
    );
    assert_eq!(
        std::fs::read_to_string(dir.path().join("scripts/new.code")).unwrap(),
        "// new source"
    );
    dispatch(&mut app, Command::Open(dir.path().join("scripts/new.code")));
    finish_io(&mut app);
    let session = app.world().resource::<Session>();
    assert_eq!(
        session
            .workspace
            .document(session.workspace.require_active().unwrap())
            .unwrap()
            .text(),
        "// new source"
    );
}
#[test]
fn recovery_requires_restore_and_does_not_write_script_files() {
    use scenemax_ide_services::StorageRequest;
    let (mut app, dir, path) = app();
    let session = app.world().resource::<Session>();
    let root = session.workspace.project().root().to_owned();
    let mut snapshot = session
        .workspace
        .document(session.workspace.require_active().unwrap())
        .unwrap()
        .snapshot();
    snapshot.replace_text("// recovered".into());
    app.world_mut()
        .resource_mut::<EditorServices>()
        .storage
        .request(StorageRequest::Checkpoint {
            root,
            documents: vec![snapshot],
            retire: vec![],
            workspace: None,
            last_project: None,
        })
        .unwrap();
    finish_io(&mut app);
    dispatch(&mut app, Command::OpenProject(dir.path().to_owned()));
    finish_io(&mut app);
    assert_eq!(app.world().resource::<Session>().recoverable, 1);
    assert_eq!(std::fs::read_to_string(&path).unwrap(), "// original\n");
    dispatch(&mut app, Command::RestoreRecovery);
    finish_io(&mut app);
    let session = app.world().resource::<Session>();
    assert!(session.workspace.has_dirty_documents());
    assert_eq!(
        session
            .workspace
            .document(session.workspace.require_active().unwrap())
            .unwrap()
            .text(),
        "// recovered"
    );
    assert_eq!(std::fs::read_to_string(path).unwrap(), "// original\n");
}

#[test]
fn project_search_navigates_to_an_unsaved_match() {
    let (mut app, _dir, _path) = app();
    edit(&mut app, "// first\n// needle here");
    app.update();
    dispatch(&mut app, Command::FindProject("needle".into()));
    finish_io(&mut app);
    let hit = app.world().resource::<Session>().search_hits[0].clone();
    assert_eq!(hit.line, 2);
    dispatch(&mut app, Command::OpenAt(hit.path, hit.line));
    let session = app.world().resource::<Session>();
    assert_eq!(
        session
            .workspace
            .document(session.workspace.require_active().unwrap())
            .unwrap()
            .line_column(),
        (2, 1)
    );
}
#[test]
fn shortcuts_in_find_field_do_not_undo_the_script() {
    use crate::presentation::components::Field;
    let (mut app, _dir, _path) = app();
    edit(&mut app, "// changed");
    app.update();
    let world = app.world_mut();
    let field = world
        .query::<(Entity, &Field)>()
        .iter(world)
        .find(|(_, kind)| **kind == Field::Find)
        .unwrap()
        .0;
    world.insert_resource(bevy::input_focus::InputFocus::from_entity(field));
    let mut keys = world.resource_mut::<ButtonInput<KeyCode>>();
    keys.press(KeyCode::ControlLeft);
    keys.press(KeyCode::KeyZ);
    app.update();
    let session = app.world().resource::<Session>();
    assert_eq!(
        session
            .workspace
            .document(session.workspace.require_active().unwrap())
            .unwrap()
            .text(),
        "// changed"
    );
}

#[test]
fn dirty_tab_cancel_then_save_and_close_preserves_work() {
    let (mut app, _dir, path) = app();
    let id = app
        .world()
        .resource::<Session>()
        .workspace
        .require_active()
        .unwrap();
    edit(&mut app, "// changed");
    app.update();
    dispatch(&mut app, Command::CloseTab);
    assert_eq!(app.world().resource::<Session>().closing_tab, Some(id));
    dispatch(&mut app, Command::CancelTab);
    assert!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .is_dirty()
    );
    dispatch(&mut app, Command::CloseTab);
    dispatch(&mut app, Command::SaveCloseTab);
    finish_io(&mut app);
    assert!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .is_err()
    );
    assert_eq!(std::fs::read_to_string(path).unwrap(), "// changed");
}
#[test]
fn save_copy_preserves_external_original_and_dirty_tab_can_be_discarded() {
    let (mut app, dir, path) = app();
    let id = app
        .world()
        .resource::<Session>()
        .workspace
        .require_active()
        .unwrap();
    edit(&mut app, "// my edits");
    app.update();
    std::fs::write(&path, "// external edit").unwrap();
    dispatch(&mut app, Command::SaveCopy("copy.code".into()));
    finish_io(&mut app);
    assert_eq!(
        std::fs::read_to_string(dir.path().join("scripts/copy.code")).unwrap(),
        "// my edits"
    );
    dispatch(&mut app, Command::Select(id));
    dispatch(&mut app, Command::CloseTab);
    dispatch(&mut app, Command::DiscardTab);
    assert!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .is_err()
    );
    assert_eq!(std::fs::read_to_string(path).unwrap(), "// external edit");
}

#[test]
fn project_field_and_hidden_search_shortcut_are_connected() {
    use crate::presentation::{chrome::Panel, components::Field};
    let (mut app, _dir, _) = app();
    let world = app.world_mut();
    let project = world
        .query::<(&Field, &EditableText)>()
        .iter(world)
        .find(|(field, _)| **field == Field::Project)
        .unwrap()
        .1
        .value()
        .to_string();
    assert!(!project.is_empty());
    let node = world
        .query::<(&Panel, &Node)>()
        .iter(world)
        .find(|(panel, _)| **panel == Panel::Search)
        .unwrap()
        .1;
    assert_eq!(node.display, Display::None);
    let mut keys = world.resource_mut::<ButtonInput<KeyCode>>();
    keys.press(KeyCode::ControlLeft);
    keys.press(KeyCode::KeyF);
    app.update();
    let world = app.world_mut();
    let node = world
        .query::<(&Panel, &Node)>()
        .iter(world)
        .find(|(panel, _)| **panel == Panel::Search)
        .unwrap()
        .1;
    assert_eq!(node.display, Display::Flex);
    let mut keys = world.resource_mut::<ButtonInput<KeyCode>>();
    keys.reset_all();
    keys.press(KeyCode::Escape);
    app.update();
    let world = app.world_mut();
    assert!(
        world
            .query::<(&Panel, &Node)>()
            .iter(world)
            .all(|(_, node)| node.display == Display::None)
    );
}

#[test]
fn file_menu_opens_and_its_project_action_reveals_the_panel() {
    use crate::presentation::chrome::{ChromeAction, MenuPopup, Panel};
    let (mut app, _dir, _) = app();
    let world = app.world_mut();
    let menu = world
        .query::<(Entity, &ChromeAction)>()
        .iter(world)
        .find(|(_, action)| matches!(action, ChromeAction::Menu(0)))
        .unwrap()
        .0;
    world.entity_mut(menu).insert(Interaction::Pressed);
    world
        .resource_mut::<ButtonInput<MouseButton>>()
        .press(MouseButton::Left);
    app.update();
    let world = app.world_mut();
    assert_eq!(
        world
            .query_filtered::<&Node, With<MenuPopup>>()
            .iter(world)
            .filter(|node| node.display == Display::Flex)
            .count(),
        1
    );
    world.entity_mut(menu).insert(Interaction::None);
    let item = world
        .query::<(Entity, &ChromeAction)>()
        .iter(world)
        .find(|(_, action)| matches!(action, ChromeAction::Panel(Panel::Project)))
        .unwrap()
        .0;
    world.entity_mut(item).insert(Interaction::Pressed);
    app.update();
    let world = app.world_mut();
    assert!(
        world
            .query_filtered::<&Node, With<MenuPopup>>()
            .iter(world)
            .all(|node| node.display == Display::None)
    );
    assert_eq!(
        world
            .query::<(&Panel, &Node)>()
            .iter(world)
            .find(|(panel, _)| **panel == Panel::Project)
            .unwrap()
            .1
            .display,
        Display::Flex
    );
}

#[test]
fn tree_keyboard_rebuild_preserves_focus_and_collapses_the_root() {
    use crate::presentation::{browser::Folder, components::Browser};
    use bevy::input_focus::{FocusCause, InputFocus};
    let (mut app, _dir, _) = app();
    let world = app.world_mut();
    let root = world
        .query_filtered::<Entity, With<Folder>>()
        .iter(world)
        .next()
        .unwrap();
    let mut focus = InputFocus::default();
    focus.set(root, FocusCause::Navigated);
    world.insert_resource(focus);
    world
        .resource_mut::<ButtonInput<KeyCode>>()
        .press(KeyCode::ArrowLeft);
    app.update();
    let world = app.world_mut();
    assert_eq!(
        world
            .query_filtered::<&Children, With<Browser>>()
            .single(world)
            .unwrap()
            .len(),
        1
    );
    let focused = world.resource::<InputFocus>().get().unwrap();
    assert!(world.get::<Folder>(focused).is_some());
    let mut keys = world.resource_mut::<ButtonInput<KeyCode>>();
    keys.reset_all();
    keys.press(KeyCode::ArrowRight);
    app.update();
    let world = app.world_mut();
    assert!(
        world
            .query_filtered::<&Children, With<Browser>>()
            .single(world)
            .unwrap()
            .len()
            > 1
    );
}

#[test]
fn closing_an_inactive_tab_does_not_close_the_active_document() {
    let (mut app, _dir, _) = app();
    let original = app
        .world()
        .resource::<Session>()
        .workspace
        .active_id()
        .unwrap();
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Create("second.code".into()));
    app.update();
    finish_io(&mut app);
    let selected = app
        .world()
        .resource::<Session>()
        .workspace
        .active_id()
        .unwrap();
    assert_ne!(original, selected);
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::CloseDocument(original));
    app.update();
    let session = app.world().resource::<Session>();
    assert_eq!(session.workspace.active_id(), Some(selected));
    assert!(session.workspace.document(original).is_err());
}

#[test]
fn catalog_selection_populates_tree_opens_main_and_saves_edits() {
    use crate::application::ViewChange;
    use crate::presentation::components::Field;
    use scenemax_ide_core::{ProjectCatalog, ProjectSummary};
    let (mut app, _dir, _) = app();
    let second = tempfile::tempdir().unwrap();
    std::fs::create_dir(second.path().join("scripts")).unwrap();
    std::fs::create_dir(second.path().join("resources")).unwrap();
    std::fs::write(second.path().join("scripts/main"), "// second project").unwrap();
    std::fs::write(second.path().join("settings.json"), "{}").unwrap();
    let root = second.path().canonicalize().unwrap();
    app.world_mut().resource_mut::<Session>().catalog = ProjectCatalog {
        selected: None,
        projects: vec![ProjectSummary {
            name: "Second project".into(),
            root: root.clone(),
            last_active: 0,
        }],
    };
    app.world_mut().write_message(ViewChange::CatalogChanged);
    app.update();
    let world = app.world_mut();
    let row = world
        .query::<(Entity, &Action)>()
        .iter(world)
        .find(|(_, action)| matches!(action,Action::ChooseProject(path) if *path == root))
        .unwrap()
        .0;
    world.entity_mut(row).insert(Interaction::Pressed);
    app.update();
    finish_io(&mut app);
    let session = app.world().resource::<Session>();
    assert_eq!(session.workspace.project().root(), root);
    assert!(
        session
            .workspace
            .project()
            .entries()
            .iter()
            .any(|e| e.path.ends_with("settings.json"))
    );
    let id = session.workspace.active_id().unwrap();
    assert!(
        session
            .workspace
            .document(id)
            .unwrap()
            .path()
            .ends_with("scripts/main")
    );
    let world = app.world_mut();
    let field = world
        .query::<(&Field, &EditableText)>()
        .iter(world)
        .find(|(f, _)| **f == Field::Project)
        .unwrap()
        .1
        .value()
        .to_string();
    assert!(field.ends_with(second.path().file_name().unwrap().to_str().unwrap()));
    edit(&mut app, "// edited selected project");
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Save);
    app.update();
    finish_io(&mut app);
    assert_eq!(
        std::fs::read_to_string(second.path().join("scripts/main")).unwrap(),
        "// edited selected project"
    );
}

#[test]
fn f8_accepts_generated_code_and_scene_designer_outside_scripts() {
    for extension in ["code", "smdesign"] {
        let (mut app, dir, _) = app();
        let folder = dir.path().join("tmp/scene1");
        std::fs::create_dir_all(&folder).unwrap();
        let path = folder.join(format!("scene1.{extension}"));
        std::fs::write(
            &path,
            if extension == "code" {
                "// generated"
            } else {
                r#"{"entities":[]}"#
            },
        )
        .unwrap();
        {
            let mut session = app.world_mut().resource_mut::<Session>();
            let doc = scenemax_ide_services::Filesystem::open_document(
                session.workspace.project(),
                &path,
            )
            .unwrap();
            session.workspace.open_document(doc).unwrap();
        }
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::F8);
        app.update();
        // The harness has no projector; reaching validation checks the real F8 route.
        assert!(
            app.world()
                .resource::<Session>()
                .status
                .contains("Bevy projector not found")
        );
    }
}

#[test]
fn java_run_shortcuts_distinguish_project_from_active_file() {
    let (mut app, _dir, _) = app();
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .press(KeyCode::F10);
    app.update();
    assert!(
        app.world()
            .resource::<Session>()
            .status
            .contains("main file")
    );
    let mut keys = app.world_mut().resource_mut::<ButtonInput<KeyCode>>();
    keys.reset_all();
    keys.press(KeyCode::F8);
    app.update();
    assert!(
        app.world()
            .resource::<Session>()
            .status
            .contains("Bevy projector not found")
    );
}

#[test]
fn syntax_colors_follow_edits_and_undo_without_mutating_clean_documents() {
    use crate::application::EditCommand;
    use scenemax_ide_ui::TextHighlights;
    let (mut app, _dir, _path) = app();
    assert!(
        !app.world()
            .resource::<Session>()
            .workspace
            .has_dirty_documents()
    );
    let id = app
        .world()
        .resource::<Session>()
        .workspace
        .require_active()
        .unwrap();
    let before = app
        .world()
        .resource::<Session>()
        .workspace
        .document(id)
        .unwrap()
        .revision();
    app.update();
    assert_eq!(
        before,
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .revision()
    );
    edit(&mut app, "print \"שלום\" // comment");
    app.update();
    {
        let world = app.world_mut();
        let (input, highlights) = world
            .query_filtered::<(&EditableText, &TextHighlights), With<Editor>>()
            .single(world)
            .unwrap();
        assert_eq!(input.value(), highlights.source.as_str());
        assert_eq!(highlights.spans.len(), 3);
        assert_eq!(
            &highlights.source[highlights.spans[1].0.clone()],
            "\"שלום\""
        );
    }
    dispatch(&mut app, Command::Edit(EditCommand::Undo));
    let world = app.world_mut();
    let highlights = world
        .query_filtered::<&TextHighlights, With<Editor>>()
        .single(world)
        .unwrap();
    assert_eq!(highlights.source, "// original\n");
    assert!(!world.resource::<Session>().workspace.has_dirty_documents());
}

#[test]
fn syntax_refresh_preserves_native_selection_and_ime_preedit() {
    use bevy::text::{FontCx, LayoutCx};
    use scenemax_ide_ui::TextHighlights;
    let (mut app, _dir, _path) = app();
    edit(&mut app, "print café");
    app.update();
    let mut fonts = FontCx::default();
    let mut layouts = LayoutCx::default();
    let entity = {
        let world = app.world_mut();
        let (entity, mut input) = world
            .query_filtered::<(Entity, &mut EditableText), With<Editor>>()
            .single_mut(world)
            .unwrap();
        input
            .editor_mut()
            .driver(&mut fonts.context, &mut layouts.0)
            .select_byte_range(6, 11);
        entity
    };
    app.update();
    {
        let input = app.world().get::<EditableText>(entity).unwrap();
        assert_eq!(input.editor().raw_selection().anchor().index(), 6);
        assert_eq!(input.editor().raw_selection().focus().index(), 11);
    }
    app.world_mut()
        .get_mut::<EditableText>(entity)
        .unwrap()
        .editor_mut()
        .driver(&mut fonts.context, &mut layouts.0)
        .set_compose("é", Some((2, 2)));
    app.update();
    let input = app.world().get::<EditableText>(entity).unwrap();
    assert!(input.is_composing());
    assert_eq!(input.editor().raw_text(), "print é");
    assert_eq!(
        app.world().get::<TextHighlights>(entity).unwrap().source,
        "print café"
    );
    app.world_mut()
        .get_mut::<EditableText>(entity)
        .unwrap()
        .editor_mut()
        .driver(&mut fonts.context, &mut layouts.0)
        .finish_compose();
    app.update();
    assert_eq!(
        app.world().get::<TextHighlights>(entity).unwrap().source,
        "print é"
    );
}

#[test]
fn native_enter_indents_after_prior_events_and_is_one_undo_step() {
    use crate::application::EditCommand;
    use bevy::text::{FontCx, LayoutCx, TextEdit};
    let (mut app, _dir, _) = app();
    app.init_resource::<FontCx>()
        .init_resource::<LayoutCx>()
        .init_resource::<bevy::clipboard::Clipboard>();
    edit(&mut app, "    ");
    app.update();
    {
        let world = app.world_mut();
        let mut input = world
            .query_filtered::<&mut EditableText, With<Editor>>()
            .single_mut(world)
            .unwrap();
        input.pending_edits.clear();
        input.queue_edit(TextEdit::TextEnd(false));
        input.queue_edit(TextEdit::Insert("if ready then".into()));
        input.queue_edit(TextEdit::Insert("\n".into()));
        input.queue_edit(TextEdit::Insert("sys.print 1".into()));
    }
    app.update();
    let world = app.world_mut();
    let input = world
        .query_filtered::<&EditableText, With<Editor>>()
        .single(world)
        .unwrap();
    assert_eq!(
        input.value().to_string(),
        "    if ready then\n        sys.print 1"
    );
    dispatch(&mut app, Command::Edit(EditCommand::Undo));
    let world = app.world_mut();
    assert_eq!(
        world
            .query_filtered::<&EditableText, With<Editor>>()
            .single(world)
            .unwrap()
            .value()
            .to_string(),
        "    "
    );
}

#[test]
fn auto_indent_leaves_paste_batches_and_ime_to_native_input() {
    use bevy::text::{FontCx, LayoutCx, TextEdit};
    let (mut app, _dir, _) = app();
    app.init_resource::<FontCx>()
        .init_resource::<LayoutCx>()
        .init_resource::<bevy::clipboard::Clipboard>();
    let expected = vec![TextEdit::Paste, TextEdit::Insert("\n".into())];
    {
        let world = app.world_mut();
        world
            .query_filtered::<&mut EditableText, With<Editor>>()
            .single_mut(world)
            .unwrap()
            .pending_edits = expected.clone();
    }
    // Inspect the indentation stage before the native commit stage consumes the batch.
    use bevy::ecs::system::RunSystemOnce;
    app.world_mut()
        .run_system_once(crate::presentation::assistance::indent_newlines)
        .unwrap();
    let world = app.world_mut();
    assert_eq!(
        world
            .query_filtered::<&EditableText, With<Editor>>()
            .single(world)
            .unwrap()
            .pending_edits,
        expected
    );
}

#[test]
fn bracket_outlines_follow_caret_and_clear_inside_strings() {
    use bevy::text::{FontCx, LayoutCx};
    use scenemax_ide_ui::TextEmphasis;
    let (mut app, _dir, _) = app();
    edit(&mut app, "(12) \"[\"");
    app.update();
    let mut fonts = FontCx::default();
    let mut layouts = LayoutCx::default();
    let entity = {
        let world = app.world_mut();
        let (entity, mut input) = world
            .query_filtered::<(Entity, &mut EditableText), With<Editor>>()
            .single_mut(world)
            .unwrap();
        input
            .editor_mut()
            .driver(&mut fonts.context, &mut layouts.0)
            .select_byte_range(1, 1);
        entity
    };
    app.update();
    assert_eq!(
        app.world().get::<TextEmphasis>(entity).unwrap().ranges,
        vec![0..1, 3..4]
    );
    app.world_mut()
        .get_mut::<EditableText>(entity)
        .unwrap()
        .editor_mut()
        .driver(&mut fonts.context, &mut layouts.0)
        .select_byte_range(7, 7);
    app.update();
    assert!(
        app.world()
            .get::<TextEmphasis>(entity)
            .unwrap()
            .ranges
            .is_empty()
    );
}

fn completion_app(source: &str) -> (App, tempfile::TempDir, Entity) {
    use bevy::{
        input_focus::{FocusCause, InputFocus},
        text::{EditableTextSystems, FontCx, LayoutCx, TextEdit},
    };
    let (mut app, dir, _) = app();
    app.init_resource::<FontCx>()
        .init_resource::<LayoutCx>()
        .init_resource::<bevy::clipboard::Clipboard>()
        .init_resource::<InputFocus>()
        .init_resource::<crate::application::symbols::ProjectSymbols>();
    app.add_systems(
        PostUpdate,
        bevy::text::apply_text_edits.in_set(EditableTextSystems),
    );
    edit(&mut app, source);
    let entity = {
        let world = app.world_mut();
        let (entity, mut input) = world
            .query_filtered::<(Entity, &mut EditableText), With<Editor>>()
            .single_mut(world)
            .unwrap();
        input.pending_edits.clear();
        input.queue_edit(TextEdit::TextEnd(false));
        entity
    };
    app.world_mut()
        .resource_mut::<InputFocus>()
        .set(entity, FocusCause::Navigated);
    app.update();
    (app, dir, entity)
}
fn request_completion(app: &mut App) {
    let mut keys = app.world_mut().resource_mut::<ButtonInput<KeyCode>>();
    keys.press(KeyCode::ControlLeft);
    keys.press(KeyCode::Space);
    app.update();
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .reset_all();
}
#[test]
fn completion_accepts_native_enter_without_newline_and_undo_restores_prefix() {
    use crate::application::EditCommand;
    use bevy::text::TextEdit;
    let (mut app, _dir, entity) = completion_app("ab");
    request_completion(&mut app);
    assert!(
        app.world()
            .resource::<crate::presentation::completion::CompletionState>()
            .is_open(entity)
    );
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .press(KeyCode::Enter);
    app.world_mut()
        .get_mut::<EditableText>(entity)
        .unwrap()
        .queue_edit(TextEdit::Insert("\n".into()));
    app.update();
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .reset_all();
    assert_eq!(
        app.world()
            .get::<EditableText>(entity)
            .unwrap()
            .value()
            .to_string(),
        "abs()"
    );
    dispatch(&mut app, Command::Edit(EditCommand::Undo));
    assert_eq!(
        app.world()
            .get::<EditableText>(entity)
            .unwrap()
            .value()
            .to_string(),
        "ab"
    );
}
#[test]
fn completion_closes_after_caret_move_and_does_not_offer_comments() {
    use bevy::text::TextEdit;
    let (mut app, _dir, entity) = completion_app("pr");
    request_completion(&mut app);
    app.world_mut()
        .get_mut::<EditableText>(entity)
        .unwrap()
        .queue_edit(TextEdit::Left(false));
    app.update();
    assert!(
        !app.world()
            .resource::<crate::presentation::completion::CompletionState>()
            .is_open(entity)
    );
    let (mut app, _dir, entity) = completion_app("// pr");
    request_completion(&mut app);
    assert!(
        !app.world()
            .resource::<crate::presentation::completion::CompletionState>()
            .is_open(entity)
    );
}
#[test]
fn completion_tab_acceptance_rejects_stale_text() {
    let (mut app, _dir, entity) = completion_app("ab");
    request_completion(&mut app);
    edit(&mut app, "changed");
    app.world_mut()
        .resource_mut::<crate::presentation::completion::CompletionState>()
        .accept = true;
    app.update();
    assert_eq!(
        app.world()
            .get::<EditableText>(entity)
            .unwrap()
            .value()
            .to_string(),
        "changed"
    );
}
#[test]
fn completion_auto_opens_after_two_typed_characters() {
    use bevy::text::TextEdit;
    let (mut app, _dir, entity) = completion_app("");
    app.world_mut()
        .get_mut::<EditableText>(entity)
        .unwrap()
        .queue_edit(TextEdit::Insert("p".into()));
    app.update();
    assert!(
        !app.world()
            .resource::<crate::presentation::completion::CompletionState>()
            .is_open(entity)
    );
    app.world_mut()
        .get_mut::<EditableText>(entity)
        .unwrap()
        .queue_edit(TextEdit::Insert("r".into()));
    app.update();
    assert!(
        app.world()
            .resource::<crate::presentation::completion::CompletionState>()
            .is_open(entity)
    );
}

#[test]
fn completion_arrows_keep_caret_and_focused_tab_accepts_without_indenting() {
    use bevy::{
        input::{
            ButtonState,
            keyboard::{Key, KeyboardInput},
        },
        text::TextEdit,
    };
    let (mut app, _dir, entity) = completion_app("pr");
    request_completion(&mut app);
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .press(KeyCode::ArrowDown);
    app.world_mut()
        .get_mut::<EditableText>(entity)
        .unwrap()
        .queue_edit(TextEdit::Down(false));
    // A held arrow produces native motions without another just_pressed edge.
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .clear();
    app.update();
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .reset_all();
    assert_eq!(
        app.world()
            .get::<EditableText>(entity)
            .unwrap()
            .editor()
            .raw_selection()
            .focus()
            .index(),
        2
    );
    let window = app.world_mut().spawn(bevy::window::PrimaryWindow).id();
    app.add_message::<KeyboardInput>().add_systems(
        PreUpdate,
        bevy::input_focus::dispatch_focused_input::<KeyboardInput>,
    );
    app.world_mut().write_message(KeyboardInput {
        key_code: KeyCode::Tab,
        logical_key: Key::Tab,
        state: ButtonState::Pressed,
        text: None,
        repeat: false,
        window,
    });
    app.update();
    assert_eq!(
        app.world()
            .get::<EditableText>(entity)
            .unwrap()
            .value()
            .to_string(),
        "Pressed"
    );
}

#[test]
fn background_project_symbols_refresh_open_popup_and_insert_through_native_editor() {
    use crate::application::symbols::ProjectSymbols;
    let (mut app, dir, entity) = completion_app("pr");
    std::fs::write(dir.path().join("scripts/main"), "add \"library\" code").unwrap();
    std::fs::write(
        dir.path().join("scripts/library.code"),
        "var pr_project_value = 7",
    )
    .unwrap();
    dispatch(&mut app, Command::Refresh);
    finish_io(&mut app);
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
    loop {
        app.update();
        if app
            .world()
            .resource::<ProjectSymbols>()
            .suggestions(std::path::Path::new(""))
            .iter()
            .any(|s| s.label == "pr_project_value")
        {
            break;
        }
        assert!(
            std::time::Instant::now() < deadline,
            "project index did not finish"
        );
        std::thread::sleep(std::time::Duration::from_millis(2));
    }
    request_completion(&mut app);
    // Change the indexed disk source while the popup stays open. Refresh must
    // replace its old candidate without requiring another completion shortcut.
    std::fs::write(
        dir.path().join("scripts/library.code"),
        "var pr_replacement = 8",
    )
    .unwrap();
    dispatch(&mut app, Command::Refresh);
    finish_io(&mut app);
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
    loop {
        app.update();
        if app
            .world()
            .resource::<ProjectSymbols>()
            .suggestions(std::path::Path::new(""))
            .iter()
            .any(|s| s.label == "pr_replacement")
        {
            break;
        }
        assert!(
            std::time::Instant::now() < deadline,
            "refreshed project index did not finish"
        );
        std::thread::sleep(std::time::Duration::from_millis(2));
    }
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .press(KeyCode::Enter);
    app.update();
    assert_eq!(
        app.world()
            .get::<EditableText>(entity)
            .unwrap()
            .value()
            .to_string(),
        "pr_replacement"
    );
}

#[test]
fn embedded_designer_updates_saves_and_undoes_without_a_text_editor() {
    use crate::presentation::designer::{Designer, Property};
    let (mut app, dir, _) = app();
    let path = dir.path().join("welcome.smui");
    let source = include_str!("../../../Tests/fixtures/ide/ui_project/ui/welcome.smui");
    std::fs::write(&path, source).unwrap();
    dispatch(&mut app, Command::Open(path.clone()));
    finish_io(&mut app);
    for _ in 0..200 {
        app.update();
        if app
            .world_mut()
            .query_filtered::<Entity, With<crate::presentation::designer::Property>>()
            .iter(app.world())
            .next()
            .is_some()
        {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    let id = app
        .world()
        .resource::<Session>()
        .workspace
        .active_id()
        .unwrap();
    let host = {
        let world = app.world_mut();
        world.query_filtered::<(Entity,&crate::presentation::components::EditorHost),With<Designer>>().iter(world).find(|(_,h)|h.0==id).unwrap().0
    };
    {
        let world = app.world_mut();
        assert!(!world.query::<&Editor>().iter(world).any(|e| e.0 == id));
    }
    {
        let world = app.world_mut();
        for (property, mut input) in world
            .query::<(&Property, &mut EditableText)>()
            .iter_mut(world)
        {
            if property.host == host && property.key == "width" {
                input.editor_mut().set_text("1000");
            }
        }
    }
    app.update();
    assert!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .is_dirty()
    );
    dispatch(&mut app, Command::Save);
    finish_io(&mut app);
    let saved = std::fs::read_to_string(&path).unwrap();
    assert!(saved.contains("1000.0"));
    dispatch(
        &mut app,
        Command::Edit(crate::application::EditCommand::Undo),
    );
    assert_eq!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text(),
        source.replace("\r\n", "\n")
    );
    assert_eq!(std::fs::read_to_string(&path).unwrap(), saved);
}

#[test]
fn smdesign_opens_as_a_scene_document_and_rejects_script_edit_commands() {
    let (mut app, dir, _) = app();
    let path = dir.path().join("example.smdesign");
    let source = r#"{"entities":[{"name":"base","type":"BOX"}]}"#;
    std::fs::write(&path, source).unwrap();
    dispatch(&mut app, Command::Open(path.clone()));
    finish_io(&mut app);
    let id = app
        .world()
        .resource::<Session>()
        .workspace
        .active_id()
        .unwrap();
    let world = app.world_mut();
    assert!(world.query_filtered::<&crate::presentation::components::EditorHost,With<crate::presentation::scene3d::SceneHost>>().iter(world).any(|h|h.0==id));
    assert!(!world.query::<&Editor>().iter(world).any(|e| e.0 == id));
    dispatch(
        &mut app,
        Command::Edit(crate::application::EditCommand::Comment),
    );
    assert_eq!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text(),
        source
    );
    let changed = scenemax_ide_core::scene3d::patch(
        source,
        "/entities/0",
        &[("name".into(), serde_json::json!("Renamed"))],
    )
    .unwrap();
    app.world_mut()
        .resource_mut::<Session>()
        .workspace
        .document_mut(id)
        .unwrap()
        .replace_text(changed.clone());
    dispatch(
        &mut app,
        Command::Edit(crate::application::EditCommand::Undo),
    );
    assert_eq!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text(),
        source
    );
    dispatch(
        &mut app,
        Command::Edit(crate::application::EditCommand::Redo),
    );
    assert_eq!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text(),
        changed
    );
    dispatch(&mut app, Command::Save);
    finish_io(&mut app);
    assert_eq!(std::fs::read_to_string(path).unwrap(), changed);
}

#[test]
fn idle_ide_hides_output_panel_and_its_splitter() {
    let (mut app, _dir, _) = app();
    let world = app.world_mut();
    let panels: Vec<_> = world
        .query_filtered::<&Node, With<crate::presentation::labels::RunOutput>>()
        .iter(world)
        .collect();
    assert_eq!(panels.len(), 2);
    assert!(panels.iter().all(|node| node.display == Display::None));
}

#[test]
fn idle_clicks_do_not_invalidate_shell_layout_or_replace_controls() {
    #[derive(Resource, Default)]
    struct LayoutChanges(Vec<Entity>);
    let (mut app, _dir, _) = app();
    app.init_resource::<LayoutChanges>().add_systems(
        PostUpdate,
        |changed: Query<Entity, Changed<Node>>, mut changes: ResMut<LayoutChanges>| {
            changes.0 = changed.iter().collect();
        },
    );
    app.update();
    app.update();
    let world = app.world_mut();
    let original: Vec<_> = world
        .query_filtered::<Entity, With<Node>>()
        .iter(world)
        .collect();
    world
        .resource_mut::<ButtonInput<MouseButton>>()
        .press(MouseButton::Left);
    app.update();
    assert!(
        app.world().resource::<LayoutChanges>().0.is_empty(),
        "An unrelated click dirtied the UI layout"
    );
    assert!(original.iter().all(|e| app.world().get_entity(*e).is_ok()));
}

#[test]
fn menu_changes_are_materialized_before_the_layout_phase() {
    #[derive(Resource, Default)]
    struct MenuAtLayout(bool);
    let (mut app, _dir, _) = app();
    app.init_resource::<MenuAtLayout>().add_systems(
        PostUpdate,
        |menus: Query<&Node, With<crate::presentation::chrome::MenuPopup>>,
         mut snapshot: ResMut<MenuAtLayout>| {
            snapshot.0 = menus.iter().any(|n| n.display == Display::Flex);
        },
    );
    let world = app.world_mut();
    let menu = world
        .query::<(Entity, &crate::presentation::chrome::ChromeAction)>()
        .iter(world)
        .find(|(_, a)| matches!(a, crate::presentation::chrome::ChromeAction::Menu(0)))
        .unwrap()
        .0;
    world.entity_mut(menu).insert(Interaction::Pressed);
    world
        .resource_mut::<ButtonInput<MouseButton>>()
        .press(MouseButton::Left);
    app.update();
    assert!(
        app.world().resource::<MenuAtLayout>().0,
        "The menu changed after layout, leaving a frame with stale geometry"
    );
}

#[test]
fn save_shortcut_includes_native_input_queued_in_the_same_frame() {
    let (mut app, _dir, path) = app();
    app.init_resource::<bevy::text::FontCx>()
        .init_resource::<bevy::text::LayoutCx>()
        .init_resource::<bevy::clipboard::Clipboard>();
    let world = app.world_mut();
    world
        .query_filtered::<&mut EditableText, With<Editor>>()
        .single_mut(world)
        .unwrap()
        .queue_edit(bevy::text::TextEdit::Insert("// just typed".into()));
    let mut keys = world.resource_mut::<ButtonInput<KeyCode>>();
    keys.press(KeyCode::ControlLeft);
    keys.press(KeyCode::KeyS);
    app.update();
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .clear();
    finish_io(&mut app);
    assert_eq!(
        std::fs::read_to_string(path).unwrap(),
        "// original\n// just typed"
    );
}

#[test]
fn code_zoom_updates_text_and_gutter_without_editing_document() {
    use crate::presentation::components::{Field, Gutter};
    use bevy::input_focus::{FocusCause, InputFocus};
    let (mut app, _dir, _) = app();
    app.init_resource::<InputFocus>();
    let editor = {
        let world = app.world_mut();
        world
            .query_filtered::<Entity, With<Editor>>()
            .single(world)
            .unwrap()
    };
    app.world_mut()
        .resource_mut::<InputFocus>()
        .set(editor, FocusCause::Navigated);
    for (key, expected) in [
        (KeyCode::Equal, 17.),
        (KeyCode::NumpadAdd, 18.),
        (KeyCode::Minus, 17.),
        (KeyCode::NumpadSubtract, 16.),
    ] {
        let mut keys = app.world_mut().resource_mut::<ButtonInput<KeyCode>>();
        keys.reset_all();
        keys.press(KeyCode::ControlLeft);
        keys.press(key);
        app.update();
        assert_eq!(
            app.world().get::<TextFont>(editor).unwrap().font_size,
            FontSize::Px(expected)
        );
        assert_eq!(
            *app.world().get::<bevy::text::LineHeight>(editor).unwrap(),
            bevy::text::LineHeight::Px(expected * 22. / 16.)
        );
        let world = app.world_mut();
        let font = world
            .query_filtered::<&TextFont, With<Gutter>>()
            .single(world)
            .unwrap();
        assert_eq!(font.font_size, FontSize::Px(expected));
        assert_eq!(
            world
                .get::<EditableText>(editor)
                .unwrap()
                .value()
                .to_string(),
            "// original\n"
        );
    }
    let field = {
        let world = app.world_mut();
        world
            .query_filtered::<Entity, With<Field>>()
            .iter(world)
            .next()
            .unwrap()
    };
    app.world_mut()
        .resource_mut::<InputFocus>()
        .set(field, FocusCause::Navigated);
    let mut keys = app.world_mut().resource_mut::<ButtonInput<KeyCode>>();
    keys.reset_all();
    keys.press(KeyCode::ControlLeft);
    keys.press(KeyCode::Equal);
    app.update();
    assert_eq!(
        app.world().get::<TextFont>(editor).unwrap().font_size,
        FontSize::Px(16.)
    );
}

#[test]
fn material_document_uses_retained_controls_and_normal_undo() {
    use crate::{
        application::material::{Edit, MaterialLibrary},
        presentation::{components::EditorHost, material::MaterialHost},
    };
    let (mut app, dir, _) = app();
    let source = scenemax_assets::material::preset("Porcelain").to_string();
    std::fs::write(dir.path().join("scripts/finish.smmat"), &source).unwrap();
    let id = app
        .world_mut()
        .resource_mut::<Session>()
        .workspace
        .open_document(
            scenemax_ide_core::Document::from_bytes(
                dir.path().join("scripts/finish.smmat"),
                source.as_bytes().into(),
            )
            .unwrap(),
        )
        .unwrap();
    {
        let mut library = app.world_mut().resource_mut::<MaterialLibrary>();
        library.project = dir.path().canonicalize().unwrap();
        library.assets = Some(scenemax_ide_services::material::Library {
            root: dir.path().join("resources"),
            ..Default::default()
        });
    }
    app.world_mut()
        .write_message(crate::application::ViewChange::DocumentOpened(id));
    app.world_mut()
        .write_message(crate::application::ViewChange::ActiveChanged);
    app.update();
    app.update();
    let host = {
        let world = app.world_mut();
        world
            .query_filtered::<(Entity, &EditorHost), With<MaterialHost>>()
            .iter(world)
            .find(|(_, h)| h.0 == id)
            .unwrap()
            .0
    };
    let revision = app
        .world()
        .resource::<Session>()
        .workspace
        .document(id)
        .unwrap()
        .revision();
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Material(Edit {
            id,
            revision,
            pointer: "/metallic".into(),
            value: serde_json::json!(0.75),
            continuing: false,
        }));
    app.update();
    app.update();
    assert!(app.world().get_entity(host).is_ok());
    let document = app
        .world()
        .resource::<Session>()
        .workspace
        .document(id)
        .unwrap();
    assert!(document.is_dirty());
    assert_eq!(
        serde_json::from_str::<serde_json::Value>(document.text()).unwrap()["metallic"],
        0.75
    );
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Edit(crate::application::EditCommand::Undo));
    app.update();
    app.update();
    assert_eq!(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text(),
        source
    );
    {
        let world = app.world_mut();
        let mut fields =
            world.query_filtered::<&mut EditableText, With<crate::presentation::material::Field>>();
        let mut name = fields
            .iter_mut(world)
            .find(|f| f.value() == "New material")
            .unwrap();
        name.editor_mut().set_text("Custom surface");
    }
    app.update();
    app.update();
    dispatch(&mut app, Command::Save);
    finish_io(&mut app);
    let saved: serde_json::Value = serde_json::from_str(
        &std::fs::read_to_string(dir.path().join("scripts/finish.smmat")).unwrap(),
    )
    .unwrap();
    assert_eq!(saved["name"], "Custom surface");
    assert!(
        !app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .is_dirty()
    );
}

#[test]
fn close_and_reopen_restores_tabs_and_selected_document() {
    let (mut app, dir, first) = app();
    app.init_resource::<bevy::text::FontCx>()
        .init_resource::<bevy::text::LayoutCx>()
        .init_resource::<bevy::input_focus::InputFocus>();
    let other = dir.path().join("scripts/other.code");
    std::fs::write(&other, "// other").unwrap();
    dispatch(&mut app, Command::Open(other.clone()));
    finish_io(&mut app);
    let first_id = app
        .world()
        .resource::<Session>()
        .workspace
        .find_document(&first.canonicalize().unwrap())
        .unwrap();
    let editors: Vec<_> = {
        let world = app.world_mut();
        world
            .query_filtered::<Entity, With<Editor>>()
            .iter(world)
            .collect()
    };
    let mut fonts = bevy::text::FontCx::default();
    let mut layouts = bevy::text::LayoutCx::default();
    for entity in editors {
        app.world_mut()
            .resource_mut::<bevy::input_focus::InputFocus>()
            .set(entity, bevy::input_focus::FocusCause::Navigated);
        app.world_mut()
            .get_mut::<EditableText>(entity)
            .unwrap()
            .editor_mut()
            .driver(&mut fonts.context, &mut layouts.0)
            .select_byte_range(1, 3);
        app.update();
    }
    dispatch(&mut app, Command::Select(first_id));
    dispatch(&mut app, Command::RequestClose);
    finish_io(&mut app);
    assert!(!app.world().resource::<Messages<AppExit>>().is_empty());
    dispatch(&mut app, Command::OpenProject(dir.path().to_owned()));
    finish_io(&mut app);
    let session = app.world().resource::<Session>();
    let paths = session
        .workspace
        .documents()
        .map(|(_, doc)| doc.path().file_name().unwrap().to_str().unwrap())
        .collect::<Vec<_>>();
    assert_eq!(paths, ["test.code", "other.code"]);
    for (_, doc) in session.workspace.documents() {
        assert_eq!(
            doc.selection(),
            scenemax_ide_core::Selection {
                anchor: 1,
                focus: 3
            }
        );
    }
    assert_eq!(
        session
            .workspace
            .document(session.workspace.active_id().unwrap())
            .unwrap()
            .path(),
        first.canonicalize().unwrap()
    );
}
#[test]
fn restart_shortcut_can_cancel_unsaved_work_and_restarts_only_after_state_is_durable() {
    let (mut app, _dir, path) = app();
    edit(&mut app, "// unsaved restart edit");
    let mut keys = app.world_mut().resource_mut::<ButtonInput<KeyCode>>();
    keys.press(KeyCode::ControlLeft);
    keys.press(KeyCode::AltLeft);
    keys.press(KeyCode::KeyR);
    app.update();
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .reset_all();
    assert!(app.world().resource::<Session>().restarting);
    assert!(app.world().resource::<Session>().closing);
    assert!(app.world().resource::<Messages<AppExit>>().is_empty());
    dispatch(&mut app, Command::CancelClose);
    assert!(!app.world().resource::<Session>().restarting);
    assert!(
        app.world()
            .resource::<Session>()
            .workspace
            .has_dirty_documents()
    );
    dispatch(&mut app, Command::Restart);
    dispatch(&mut app, Command::SaveAll);
    finish_io(&mut app);
    let session = app.world().resource::<Session>();
    assert!(session.restart_project.lock().unwrap().is_some());
    assert!(
        session
            .workspace
            .project()
            .root()
            .join(".scenemax-studio/workspace.json")
            .is_file()
    );
    assert_eq!(
        std::fs::read_to_string(path).unwrap(),
        "// unsaved restart edit"
    );
    assert!(!app.world().resource::<Messages<AppExit>>().is_empty());
}
