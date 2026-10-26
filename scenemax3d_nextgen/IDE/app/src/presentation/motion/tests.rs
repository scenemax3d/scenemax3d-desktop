use super::*;
use scenemax_ui_input::Interaction;
fn settle(app: &mut App) {
    app.update();
    app.update();
    app.update();
}
fn document(app: &App) -> Value {
    let session = app.world().resource::<Session>();
    motion::parse(
        session
            .workspace
            .document(session.workspace.active_id().unwrap())
            .unwrap()
            .text(),
    )
    .unwrap()
}
#[test]
fn create_motion_opens_retained_editor_and_saves_native_edits_with_undo() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Tree(
            scenemax_ide_services::TreeOperation::CreateMotion {
                name: "Native test".into(),
                kind: "target_arc".into(),
            },
        ));
    for _ in 0..500 {
        app.update();
        if app.world().resource::<State>().current.is_some() {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    settle(&mut app);
    assert_eq!(document(&app)["id"], "motion_native_test");
    let host = app.world().resource::<State>().host.unwrap();
    let field = {
        let world = app.world_mut();
        world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, f)| f.0 == "/parameters/arcHeight")
            .unwrap()
            .0
    };
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("2.5");
    app.update();
    app.update();
    assert_eq!(document(&app)["parameters"]["arcHeight"], 2.5);
    assert_eq!(app.world().resource::<State>().host, Some(host));
    assert!(app.world().get::<EditableText>(field).is_some());
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Edit(crate::application::EditCommand::Undo));
    app.update();
    app.update();
    assert_eq!(document(&app)["parameters"]["arcHeight"], 3.);
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("3.5");
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Save);
    app.update();
    app.update();
    let path = dir
        .path()
        .join("resources/throw_motions/motion_native_test.smmotion");
    for _ in 0..500 {
        app.update();
        if serde_json::from_str::<Value>(&std::fs::read_to_string(&path).unwrap()).unwrap()["parameters"]
            ["arcHeight"]
            == 3.5
        {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    let saved: Value = serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap();
    assert_eq!(saved["parameters"]["arcHeight"], 3.5);
}

#[test]
fn type_selection_rebuilds_parameters_and_playback_controls_work() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    let v = motion::template("Preview fixture", "target_arc").unwrap();
    let source = v.to_string();
    let path = dir.path().join("scripts/fixture.smmotion");
    std::fs::write(&path, &source).unwrap();
    let id = app
        .world_mut()
        .resource_mut::<Session>()
        .workspace
        .open_document(scenemax_ide_core::Document::from_bytes(path, source.into_bytes()).unwrap())
        .unwrap();
    app.world_mut()
        .write_message(crate::application::ViewChange::DocumentOpened(id));
    app.world_mut()
        .write_message(crate::application::ViewChange::ActiveChanged);
    settle(&mut app);
    let entity = {
        let w = app.world_mut();
        w.query::<(Entity, &Field)>()
            .iter(w)
            .find(|(_, f)| f.0 == "/motionType")
            .unwrap()
            .0
    };
    app.world_mut().get_mut::<Choice>(entity).unwrap().0 = "returning".into();
    settle(&mut app);
    assert_eq!(document(&app)["motionType"], "returning");
    {
        let w = app.world_mut();
        assert!(
            w.query::<&Field>()
                .iter(w)
                .any(|f| f.0 == "/parameters/returnSpeed")
        );
    }
    let entity = {
        let w = app.world_mut();
        w.query::<(Entity, &Action)>()
            .iter(w)
            .find(|(_, a)| matches!(a, Action::Play))
            .unwrap()
            .0
    };
    app.world_mut()
        .entity_mut(entity)
        .insert(Interaction::Pressed);
    app.update();
    assert!(app.world().resource::<State>().playing);
    app.world_mut().entity_mut(entity).insert(Interaction::None);
    app.update();
    let entity = {
        let w = app.world_mut();
        w.query::<(Entity, &Action)>()
            .iter(w)
            .find(|(_, a)| matches!(a, Action::Step))
            .unwrap()
            .0
    };
    app.world_mut()
        .entity_mut(entity)
        .insert(Interaction::Pressed);
    app.update();
    assert!(!app.world().resource::<State>().playing);
    assert!((app.world().resource::<State>().time - 1. / 30.).abs() < 0.00001);
}
