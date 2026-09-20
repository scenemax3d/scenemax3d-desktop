use super::*;
use bevy::{
    input_focus::{FocusCause, InputFocus},
    text::EditableText,
};
use scenemax_ide_core::{Document, Project};
use serde_json::Value;

#[test]
fn leaving_scene_removes_inactive_property_controls() {
    use bevy::ecs::system::RunSystemOnce;
    let dir = tempfile::tempdir().unwrap();
    let mut session = Session::new(Project::new(dir.path().into(), vec![]));
    let id = session
        .workspace
        .open_document(
            Document::from_bytes(
                dir.path().join("scene.smdesign"),
                br#"{"entities":[]}"#.to_vec(),
            )
            .unwrap(),
        )
        .unwrap();
    let revision = session.workspace.document(id).unwrap().revision();
    session
        .workspace
        .open_document(
            Document::from_bytes(dir.path().join("script.code"), b"// script".to_vec()).unwrap(),
        )
        .unwrap();
    let mut world = World::new();
    world.insert_resource(session);
    world.insert_resource(EditorServices::new(std::path::PathBuf::new()).unwrap());
    world.insert_resource(SceneState {
        current: Some((id, revision)),
        ..default()
    });
    let host = world
        .spawn((SceneHost, EditorHost(id), Node::default()))
        .id();
    let field = world
        .spawn((
            inspector::Property("name".into(), "Old".into()),
            EditableText::new("Old"),
            ChildOf(host),
        ))
        .id();
    world.run_system_once(update).unwrap();
    assert!(world.get_entity(host).is_ok());
    assert!(world.get_entity(field).is_err());
}
#[test]
fn properties_and_gizmo_share_live_document_without_losing_focus_or_undo() {
    verify_live_transform(
        r#"{"entities":[{"type":"BOX","name":"Box","position":[1,2,3],"scale":[1,1,1],"future":42}]}"#,
        "/entities/0",
    );
}
#[test]
fn game_camera_properties_and_gizmo_share_live_document_and_undo() {
    verify_live_transform(
        r#"{"entities":[],"gameCamera":{"position":[1,2,3],"rotation":[0,0,0,1],"future":42}}"#,
        "/gameCamera",
    );
}
fn verify_live_transform(source: &str, pointer: &str) {
    let dir = tempfile::tempdir().unwrap();
    let mut session = Session::new(Project::new(dir.path().into(), vec![]));
    let id = session
        .workspace
        .open_document(
            Document::from_bytes(dir.path().join("scene.smdesign"), source.as_bytes().into())
                .unwrap(),
        )
        .unwrap();
    let revision = session.workspace.document(id).unwrap().revision();
    let mut app = App::new();
    app.insert_resource(session)
        .insert_resource(SceneState {
            current: Some((id, revision)),
            scene: Some(scenemax_ide_services::scene3d::load(dir.path(), source).unwrap()),
            ..default()
        })
        .init_resource::<ButtonInput<MouseButton>>()
        .init_resource::<InputFocus>()
        .add_message::<crate::application::ViewChange>()
        .add_systems(Update, live::update);
    let field = app
        .world_mut()
        .spawn((
            inspector::Property("position:0".into(), "1".into()),
            EditableText::new("2"),
        ))
        .id();
    let original = Transform::from_xyz(1., 2., 3.);
    let object = app
        .world_mut()
        .spawn((gizmo::SceneObject(0, original), original))
        .id();
    app.world_mut()
        .resource_mut::<InputFocus>()
        .set(field, FocusCause::Navigated);
    app.update();
    assert_eq!(
        app.world().get::<Transform>(object).unwrap().translation.x,
        2.
    );
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("-");
    app.update();
    assert_eq!(
        app.world().get::<Transform>(object).unwrap().translation.x,
        2.
    );
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("5");
    app.update();
    assert_eq!(
        app.world().get::<Transform>(object).unwrap().translation.x,
        5.
    );
    assert_eq!(app.world().resource::<InputFocus>().get(), Some(field));
    app.world_mut()
        .get_mut::<Transform>(object)
        .unwrap()
        .translation
        .x = 9.;
    app.update();
    assert_eq!(
        app.world()
            .get::<EditableText>(field)
            .unwrap()
            .value()
            .to_string()
            .parse::<f32>()
            .unwrap(),
        9.
    );
    let mut session = app.world_mut().resource_mut::<Session>();
    let doc = session.workspace.document_mut(id).unwrap();
    let data: Value = serde_json::from_str(doc.text()).unwrap();
    assert_eq!(data.pointer(pointer).unwrap()["position"][0], 9.);
    assert_eq!(data.pointer(pointer).unwrap()["future"], 42);
    doc.undo();
    let data: Value = serde_json::from_str(doc.text()).unwrap();
    assert_eq!(data.pointer(pointer).unwrap()["position"][0], 5.);
    doc.undo();
    assert_eq!(doc.text(), source);
}
#[test]
fn viewport_selection_expands_owning_branch_and_updates_selected_index() {
    let dir = tempfile::tempdir().unwrap();
    let scene = scenemax_ide_services::scene3d::load(
        dir.path(),
        r#"{"entities":[{"type":"SECTION","children":[{"type":"MODEL","name":"Nested"}]}]}"#,
    )
    .unwrap();
    let mut state = SceneState {
        scene: Some(scene),
        collapsed: [0].into_iter().collect(),
        ..default()
    };
    let mut world = World::new();
    picking::select(&mut world.commands(), &mut state, 1);
    assert_eq!(state.selected, 1);
    assert!(!state.collapsed.contains(&0));
}

#[test]
fn code_node_editor_preserves_source_and_document_undo() {
    use bevy::ecs::system::RunSystemOnce;
    let dir = tempfile::tempdir().unwrap();
    let source =
        r#"{"entities":[{"type":"CODE","name":"Logic","codeText":"// original\n","future":42}]}"#;
    let scene = scenemax_ide_services::scene3d::load(dir.path(), source).unwrap();
    let mut session = Session::new(Project::new(dir.path().into(), vec![]));
    let id = session
        .workspace
        .open_document(
            Document::from_bytes(dir.path().join("scene.smdesign"), source.as_bytes().into())
                .unwrap(),
        )
        .unwrap();
    let revision = session.workspace.document(id).unwrap().revision();
    let mut app = App::new();
    app.insert_resource(session)
        .insert_resource(SceneState {
            current: Some((id, revision)),
            scene: Some(scene),
            ..default()
        })
        .init_resource::<ButtonInput<MouseButton>>()
        .init_resource::<InputFocus>()
        .add_message::<crate::application::ViewChange>()
        .add_systems(Update, live::update);
    app.world_mut()
        .run_system_once(|mut commands: Commands, state: Res<SceneState>| {
            let parent = commands.spawn(Node::default()).id();
            inspector::build(&mut commands, parent, state.scene.as_ref().unwrap(), 0);
        })
        .unwrap();
    let world = app.world_mut();
    let editor = world
        .query_filtered::<Entity, With<inspector::CodeEditor>>()
        .single(world)
        .unwrap();
    let code = "\n    Logger.info \"café\"\n\t// keep indentation\n\n";
    assert!(world.get::<EditableText>(editor).unwrap().allow_newlines);
    world
        .get_mut::<EditableText>(editor)
        .unwrap()
        .editor_mut()
        .set_text(code);
    world
        .resource_mut::<InputFocus>()
        .set(editor, FocusCause::Navigated);
    app.update();
    assert_eq!(app.world().resource::<InputFocus>().get(), Some(editor));
    let mut session = app.world_mut().resource_mut::<Session>();
    let doc = session.workspace.document_mut(id).unwrap();
    let saved: Value = serde_json::from_str(doc.text()).unwrap();
    assert_eq!(saved["entities"][0]["codeText"], code);
    assert_eq!(saved["entities"][0]["future"], 42);
    doc.undo();
    assert_eq!(doc.text(), source);
}

#[test]
fn native_code_node_input_and_ctrl_s_regenerate_disk_in_the_same_frame() {
    use crate::application::{Command, CommandQueue, ViewChange};
    use bevy::{
        ecs::system::RunSystemOnce,
        text::{FontCx, LayoutCx, TextEdit},
    };
    let (mut app, dir, _) = crate::tests::app();
    let path = dir.path().join("scripts/scene.smdesign");
    let source = r#"{"entities":[{"type":"CODE","name":"Logic","codeText":"// original\n"}]}"#;
    std::fs::write(&path, source).unwrap();
    std::fs::write(path.with_extension("code"), "// stale output").unwrap();
    let id = {
        let mut session = app.world_mut().resource_mut::<Session>();
        let document =
            scenemax_ide_services::Filesystem::open_document(session.workspace.project(), &path)
                .unwrap();
        session.workspace.open_document(document).unwrap()
    };
    app.world_mut()
        .resource_mut::<Messages<ViewChange>>()
        .write(ViewChange::DocumentOpened(id));
    app.world_mut()
        .resource_mut::<Messages<ViewChange>>()
        .write(ViewChange::ActiveChanged);
    app.update();
    for _ in 0..200 {
        app.update();
        if !app
            .world()
            .resource::<EditorServices>()
            .scene_storage
            .is_pending()
        {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    let revision = app
        .world()
        .resource::<Session>()
        .workspace
        .document(id)
        .unwrap()
        .revision();
    app.world_mut().insert_resource(SceneState {
        current: Some((id, revision)),
        completed: Some((id, revision)),
        scene: Some(scenemax_ide_services::scene3d::load(dir.path(), source).unwrap()),
        ..default()
    });
    app.init_resource::<InputFocus>()
        .init_resource::<FontCx>()
        .init_resource::<LayoutCx>()
        .init_resource::<bevy::clipboard::Clipboard>();
    app.world_mut()
        .run_system_once(|mut commands: Commands, state: Res<SceneState>| {
            let host = commands.spawn(Node::default()).id();
            inspector::build(&mut commands, host, state.scene.as_ref().unwrap(), 0);
        })
        .unwrap();
    let world = app.world_mut();
    let editor = world
        .query_filtered::<Entity, With<inspector::CodeEditor>>()
        .single(world)
        .unwrap();
    world
        .resource_mut::<InputFocus>()
        .set(editor, FocusCause::Navigated);
    world
        .get_mut::<EditableText>(editor)
        .unwrap()
        .queue_edit(TextEdit::Insert("Logger.info \"updated\"\n".into()));
    world
        .resource_mut::<ButtonInput<KeyCode>>()
        .press(KeyCode::ControlLeft);
    world
        .resource_mut::<ButtonInput<KeyCode>>()
        .press(KeyCode::KeyS);
    app.update();
    app.world_mut()
        .resource_mut::<ButtonInput<KeyCode>>()
        .reset_all();
    for _ in 0..500 {
        app.update();
        if !app
            .world()
            .resource::<EditorServices>()
            .storage
            .is_pending()
        {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    let session = app.world().resource::<Session>();
    assert!(
        std::fs::read_to_string(path.with_extension("code"))
            .unwrap()
            .contains("Logger.info \"updated\""),
        "{}",
        session.status
    );
    assert!(std::fs::read_to_string(&path).unwrap().contains("updated"));
    assert!(!session.workspace.document(id).unwrap().is_dirty());
    // Explicit Save also repairs stale output without requiring another edit.
    std::fs::write(path.with_extension("code"), "// stale again").unwrap();
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Save);
    app.update();
    for _ in 0..500 {
        app.update();
        if !app
            .world()
            .resource::<EditorServices>()
            .storage
            .is_pending()
        {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    assert!(
        std::fs::read_to_string(path.with_extension("code"))
            .unwrap()
            .contains("Logger.info \"updated\"")
    );
}
