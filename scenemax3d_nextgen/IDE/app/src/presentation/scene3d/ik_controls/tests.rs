use super::*;
use scenemax_ide_core::{Document, Project};
fn fixture() -> (App, DocumentId, Entity, Entity, Entity, String) {
    let dir = tempfile::tempdir().unwrap();
    let source = r#"{"entities":[{"type":"MODEL","name":"subject","resourcePath":"rig","ikAsset":"contacts","ikLayers":[{"layerId":"contact","enabled":true,"target":"anchor","blend":0,"weight":1}],"future":42},{"type":"SPHERE","name":"anchor","position":[1,1,0]}]}"#.to_string();
    let mut session = Session::new(Project::new(dir.path().into(), vec![]));
    let id = session
        .workspace
        .open_document(
            Document::from_bytes(
                dir.path().join("scene.smdesign"),
                source.as_bytes().to_vec(),
            )
            .unwrap(),
        )
        .unwrap();
    let stamp = (id, session.workspace.document(id).unwrap().revision());
    let mut scene = scenemax_ide_services::scene3d::load(dir.path(), &source).unwrap();
    scene.ik_assets.insert("contacts".into(), json!({"id":"contacts","layers":[{"id":"contact","solverType":"TwoBoneIK","chain":{"root":"upper","middle":"middle","end":"tip"},"iterations":64}]}));
    let mut app = App::new();
    app.add_plugins((MinimalPlugins, TransformPlugin, scenemax_ik::IkPlugin))
        .insert_resource(session)
        .init_resource::<State>()
        .init_resource::<Assets<Mesh>>()
        .init_resource::<Assets<gizmo::GizmoMaterial>>()
        .init_resource::<ButtonInput<MouseButton>>()
        .add_message::<crate::application::ViewChange>()
        .add_systems(Update, commit)
        .add_systems(PostUpdate, constraints::update.before(scenemax_ik::Solve))
        .add_systems(
            PostUpdate,
            refresh
                .after(scenemax_ik::Solve)
                .before(bevy::transform::TransformSystems::Propagate),
        );
    let w = app.world_mut();
    let root = w.spawn(Transform::default()).id();
    let owner = w
        .spawn((
            Name::new("subject"),
            gizmo::SceneObject(0, Transform::IDENTITY),
            Transform::default(),
            ChildOf(root),
        ))
        .id();
    let upper = w
        .spawn((Name::new("upper"), Transform::default(), ChildOf(owner)))
        .id();
    let mid = w
        .spawn((
            Name::new("middle"),
            Transform::from_xyz(0., 1., 0.),
            ChildOf(upper),
        ))
        .id();
    let end = w
        .spawn((
            Name::new("tip"),
            Transform::from_xyz(0., 1., 0.),
            ChildOf(mid),
        ))
        .id();
    w.spawn((
        Name::new("anchor"),
        gizmo::SceneObject(1, Transform::from_xyz(1., 1., 0.)),
        Transform::from_xyz(1., 1., 0.),
        ChildOf(root),
    ));
    w.insert_resource(SceneState {
        scene: Some(scene),
        current: Some(stamp),
        completed: Some(stamp),
        world: Some(root),
        ..default()
    });
    app.update();
    (app, id, owner, mid, end, source)
}
fn select(app: &mut App, kind: Kind) -> Entity {
    app.world_mut().resource_mut::<State>().requested = Some(Handle { layer: 0, kind });
    app.update();
    app.world().resource::<State>().proxy.unwrap()
}
#[test]
fn dragging_contacts_and_bends_persists_scene_only_and_undo_groups_gestures() {
    let (mut app, id, owner, mid, end, source) = fixture();
    let definition = app
        .world()
        .resource::<SceneState>()
        .scene
        .as_ref()
        .unwrap()
        .ik_assets
        .clone();
    let proxy = select(&mut app, Kind::Contact);
    app.world_mut()
        .get_mut::<Transform>(proxy)
        .unwrap()
        .translation
        .x -= 0.2;
    app.update();
    app.world_mut()
        .get_mut::<Transform>(proxy)
        .unwrap()
        .translation
        .x -= 0.1;
    app.update();
    let text = app
        .world()
        .resource::<Session>()
        .workspace
        .document(id)
        .unwrap()
        .text()
        .to_string();
    let value: Value = serde_json::from_str(&text).unwrap();
    assert!(
        (value["entities"][0]["ikLayers"][0]["positionOffset"][0]
            .as_f64()
            .unwrap()
            + 0.3)
            .abs()
            < 0.0001
    );
    assert!(
        constraints::global(app.world(), end)
            .unwrap()
            .translation()
            .distance(Vec3::new(0.7, 1., 0.))
            < 0.05
    );
    assert_eq!(
        *app.world().get::<Transform>(owner).unwrap(),
        Transform::IDENTITY
    );
    let proxy = select(&mut app, Kind::Bend);
    app.world_mut()
        .get_mut::<Transform>(proxy)
        .unwrap()
        .translation = Vec3::new(0., 1., 1.);
    app.update();
    assert!(
        constraints::global(app.world(), mid)
            .unwrap()
            .translation()
            .z
            > 0.2
    );
    assert_eq!(
        app.world()
            .resource::<SceneState>()
            .scene
            .as_ref()
            .unwrap()
            .ik_assets,
        definition
    );
    let mut session = app.world_mut().resource_mut::<Session>();
    let doc = session.workspace.document_mut(id).unwrap();
    let data: Value = serde_json::from_str(doc.text()).unwrap();
    assert_eq!(data["entities"][0]["future"], 42);
    let code =
        scenemax_ide_core::scene_codegen::generate(doc.text(), "scene", "", "", &[]).unwrap();
    assert!(code.contains(".polePosition = (0,1,1)"), "{code}");
    doc.undo();
    assert_eq!(doc.text(), text);
    doc.undo();
    assert_eq!(doc.text(), source);
}
#[test]
fn contact_rotation_enables_orientation_and_reset_restores_asset_and_pool_stays_bounded() {
    let (mut app, id, owner, _, end, _) = fixture();
    let proxy = select(&mut app, Kind::Contact);
    app.world_mut()
        .get_mut::<Transform>(proxy)
        .unwrap()
        .rotation = Quat::from_rotation_y(0.7);
    app.update();
    let rig = app.world().get::<scenemax_ik::Rig>(owner).unwrap();
    assert_eq!(rig.definition["layers"][0]["matchTargetRotation"], true);
    assert!(
        constraints::global(app.world(), end)
            .unwrap()
            .rotation()
            .abs_diff_eq(Quat::from_rotation_y(0.7), 0.01)
    );
    let count = app.world().entities().len();
    for _ in 0..20 {
        app.update();
    }
    assert_eq!(app.world().entities().len(), count);
    app.world_mut().resource_mut::<State>().reset = Some(0);
    app.update();
    let data: Value = serde_json::from_str(
        app.world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text(),
    )
    .unwrap();
    assert!(
        data["entities"][0]["ikLayers"][0]
            .get("rotationOffset")
            .is_none()
    );
    assert!(
        app.world()
            .get::<scenemax_ik::Rig>(owner)
            .unwrap()
            .definition["layers"][0]
            .get("matchTargetRotation")
            .is_none()
    );
    app.world_mut().resource_mut::<SceneState>().selected = 1;
    app.update();
    assert!(app.world().resource::<State>().proxy.is_none());
    assert!(app.world().resource::<State>().visuals.is_empty());
}
