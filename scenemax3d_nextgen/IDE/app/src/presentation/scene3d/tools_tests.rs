use super::*;
use scenemax_ide_core::{Document, Project};
#[test]
fn toolbar_add_and_delete_use_document_history_and_select_inserted_entry() {
    let dir = tempfile::tempdir().unwrap();
    let source = r#"{"custom":42,"entities":[]}"#;
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
        .init_resource::<tools::Tools>()
        .init_resource::<navigation::Navigation>()
        .init_resource::<path::Drawing>()
        .add_message::<crate::application::ViewChange>()
        .add_systems(Update, tools::update);
    let action = app
        .world_mut()
        .spawn((tools::Action::Add("CINEMATIC_RIG"), Interaction::Pressed))
        .id();
    app.update();
    let s = app
        .world()
        .resource::<Session>()
        .workspace
        .document(id)
        .unwrap()
        .text()
        .to_owned();
    let value: serde_json::Value = serde_json::from_str(&s).unwrap();
    assert_eq!(value["custom"], 42);
    assert_eq!(
        value["entities"][0]["children"][0]["type"],
        "CINEMATIC_TRACK"
    );
    assert_eq!(
        app.world()
            .resource::<SceneState>()
            .pending_selection
            .as_deref(),
        Some("/entities/0")
    );
    let revision = app
        .world()
        .resource::<Session>()
        .workspace
        .document(id)
        .unwrap()
        .revision();
    {
        let mut state = app.world_mut().resource_mut::<SceneState>();
        state.current = Some((id, revision));
        state.scene = Some(scenemax_ide_services::scene3d::load(dir.path(), &s).unwrap());
    }
    app.world_mut()
        .entity_mut(action)
        .insert((tools::Action::Delete, Interaction::Pressed));
    app.update();
    let doc = app
        .world()
        .resource::<Session>()
        .workspace
        .document(id)
        .unwrap();
    let value: serde_json::Value = serde_json::from_str(doc.text()).unwrap();
    assert!(value["entities"].as_array().unwrap().is_empty());
    let mut session = app.world_mut().resource_mut::<Session>();
    let doc = session.workspace.document_mut(id).unwrap();
    doc.undo();
    assert_eq!(doc.text(), s);
}
