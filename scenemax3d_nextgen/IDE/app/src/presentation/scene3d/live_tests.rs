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
