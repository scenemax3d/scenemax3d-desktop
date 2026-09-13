use super::*;
use bevy::{ecs::system::RunSystemOnce, text::EditableText};
use scenemax_ide_core::{Document, Project};
use serde_json::Value;

#[test]
fn scene_selection_retains_viewport_camera_rows_and_repeated_selection_draft() {
    let dir = tempfile::tempdir().unwrap();
    let source = r#"{"entities":[{"type":"SECTION","children":[{"type":"BOX","name":"Box","position":[1,2,3]}]}]}"#;
    let scene = scenemax_ide_services::scene3d::load(dir.path(), source).unwrap();
    let doc =
        Document::from_bytes(dir.path().join("scene.smdesign"), source.as_bytes().into()).unwrap();
    let revision = doc.revision();
    let mut session = Session::new(Project::new(dir.path().into(), vec![]));
    let id = session.workspace.open_document(doc).unwrap();
    let mut app = App::new();
    let orbit = render::overview(&scene);
    let transform = orbit.transform();
    let camera = app
        .world_mut()
        .spawn((Camera3d::default(), orbit, transform))
        .id();
    let host = app
        .world_mut()
        .spawn((EditorHost(id), SceneHost, Node::default()))
        .id();
    app.insert_resource(session)
        .insert_resource(EditorServices::new(std::path::PathBuf::new()).unwrap());
    app.insert_resource(SceneState {
        current: Some((id, revision)),
        completed: Some((id, revision)),
        camera: Some(camera),
        scene: Some(scene),
        ..default()
    });
    app.world_mut()
        .run_system_once(
            move |mut commands: Commands, mut state: ResMut<SceneState>| {
                state.parts = Some(view::build(
                    &mut commands,
                    host,
                    camera,
                    state.scene.as_ref().unwrap(),
                    0,
                    &state.collapsed,
                ));
            },
        )
        .unwrap();
    app.add_systems(Update, (update, synchronize_tree).chain());
    app.update();
    let world = app.world_mut();
    let viewport = world
        .query_filtered::<Entity, With<gizmo::Viewport>>()
        .single(world)
        .unwrap();
    let rows: Vec<_> = world
        .query_filtered::<Entity, With<view::SceneRow>>()
        .iter(world)
        .collect();
    let select = world
        .query::<(Entity, &Choose)>()
        .iter(world)
        .find(|(_, c)| matches!(c, Choose::Select(1)))
        .unwrap()
        .0;
    world.entity_mut(select).insert(Interaction::Pressed);
    app.update();
    let world = app.world_mut();
    assert_eq!(
        world
            .query_filtered::<Entity, With<gizmo::Viewport>>()
            .single(world)
            .unwrap(),
        viewport
    );
    assert_eq!(*world.get::<Transform>(camera).unwrap(), transform);
    assert!(rows.iter().all(|r| world.get_entity(*r).is_ok()));
    let field = world
        .query::<(Entity, &inspector::Property)>()
        .iter(world)
        .find(|(_, p)| p.0 == "name")
        .unwrap()
        .0;
    world
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("Unapplied draft");
    world.entity_mut(select).insert(Interaction::None);
    app.update();
    app.world_mut()
        .entity_mut(select)
        .insert(Interaction::Pressed);
    app.update();
    assert_eq!(
        app.world()
            .get::<EditableText>(field)
            .unwrap()
            .value()
            .to_string(),
        "Unapplied draft"
    );
    let world = app.world_mut();
    let toggle = world
        .query::<(Entity, &Choose)>()
        .iter(world)
        .find(|(_, c)| matches!(c, Choose::Toggle(0)))
        .unwrap()
        .0;
    world.entity_mut(toggle).insert(Interaction::Pressed);
    app.update();
    assert!(rows.iter().all(|r| app.world().get_entity(*r).is_ok()));
    assert!(app.world().get_entity(viewport).is_ok());
    assert!(app.world().get_entity(field).is_ok());
    let world = app.world_mut();
    let camera_select = world
        .query::<(Entity, &Choose)>()
        .iter(world)
        .find(|(_, c)| matches!(c, Choose::Select(2)))
        .unwrap()
        .0;
    let camera_row = world.get::<ChildOf>(camera_select).unwrap().parent();
    let slot = world.get::<ChildOf>(camera_row).unwrap().parent();
    assert!(
        world.get::<view::CameraSlot>(slot).is_some(),
        "Camera must remain outside the scrolling object list"
    );
    let list = world
        .query_filtered::<Entity, With<view::ObjectList>>()
        .single(world)
        .unwrap();
    world.get_mut::<ScrollPosition>(list).unwrap().y = 10000.;
    world.entity_mut(camera_select).insert(Interaction::Pressed);
    app.update();
    let world = app.world_mut();
    assert_eq!(world.resource::<SceneState>().selected, 2);
    let properties: Vec<_> = world
        .query::<&inspector::Property>()
        .iter(world)
        .map(|p| p.0.clone())
        .collect();
    for key in [
        "position:0",
        "position:1",
        "position:2",
        "angles:0",
        "angles:1",
        "angles:2",
    ] {
        assert!(
            properties.iter().any(|p| p == key),
            "Missing camera field {key}"
        );
    }
    assert!(world.get_entity(viewport).is_ok());
}

#[test]
fn native_property_apply_is_undoable_and_stale_drafts_are_rejected() {
    let dir = tempfile::tempdir().unwrap();
    let source =
        r#"{"entities":[{"type":"BOX","name":"Box","position":[1,2,3],"future":{"keep":true}}]}"#;
    let scene = scenemax_ide_services::scene3d::load(dir.path(), source).unwrap();
    let doc =
        Document::from_bytes(dir.path().join("scene.smdesign"), source.as_bytes().into()).unwrap();
    let revision = doc.revision();
    let mut session = Session::new(Project::new(dir.path().into(), vec![]));
    let id = session.workspace.open_document(doc).unwrap();
    let mut app = App::new();
    app.add_message::<crate::application::ViewChange>();
    app.insert_resource(session).insert_resource(SceneState {
        current: Some((id, revision)),
        scene: Some(scene),
        ..default()
    });
    app.world_mut().spawn((
        inspector::Property("position:0".into(), "1.0000".into()),
        EditableText::new("12.5"),
    ));
    app.world_mut()
        .spawn((Interaction::Pressed, inspector::Apply(false)));
    app.world_mut().run_system_once(inspector::apply).unwrap();
    let mut session = app.world_mut().resource_mut::<Session>();
    let doc = session.workspace.document_mut(id).unwrap();
    let edited: Value = serde_json::from_str(doc.text()).unwrap();
    assert_eq!(edited["entities"][0]["position"][0], 12.5);
    assert_eq!(edited["entities"][0]["future"]["keep"], true);
    assert!(doc.is_dirty());
    doc.undo();
    assert_eq!(doc.text(), source);
    app.world_mut().run_system_once(inspector::apply).unwrap();
    let session = app.world().resource::<Session>();
    assert!(session.status.contains("Document changed"));
    assert_eq!(session.workspace.document(id).unwrap().text(), source);
}

#[test]
fn cinematic_children_keep_local_transform_under_rig_and_sections_are_organizational() {
    let dir = tempfile::tempdir().unwrap();
    let scene = scenemax_ide_services::scene3d::load(dir.path(), r#"{"entities":[{"type":"SECTION","position":[999,0,0],"children":[{"type":"CINEMATIC_RIG","position":[10,20,30],"children":[{"type":"SECTION","children":[{"type":"CINEMATIC_TRACK","position":[1,2,3],"cinematicTrackData":{"radiusX":4,"radiusZ":2}}]}]}]}]}"#).unwrap();
    assert_eq!(cinematic::rig_parent(&scene, 1), None);
    assert_eq!(cinematic::rig_parent(&scene, 3), Some(1));
    assert_eq!(scene.entities[3].position, [1., 2., 3.]);
    assert_eq!(
        scene.entities[3].pointer,
        "/entities/0/children/0/children/0/children/0"
    );
    assert!(scene.entities[3].note.is_empty());
}
