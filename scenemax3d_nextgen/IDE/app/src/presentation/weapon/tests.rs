use super::*;
fn settle(app: &mut App) {
    for _ in 0..500 {
        app.update();
        if app.world().resource::<State>().task.is_none() {
            app.update();
            return;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    panic!("weapon model scan timed out");
}
fn document(app: &App) -> Value {
    let session = app.world().resource::<Session>();
    weapon::parse(
        session
            .workspace
            .document(session.workspace.active_id().unwrap())
            .unwrap()
            .text(),
    )
    .unwrap()
}
#[test]
fn create_weapon_opens_retained_editor_and_saves_native_edits_with_undo() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Tree(
            scenemax_ide_services::TreeOperation::CreateWeapon {
                name: "Native test".into(),
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
    assert_eq!(document(&app)["id"], "weapon_native_test");
    let host = app.world().resource::<State>().host.unwrap();
    let field = {
        let world = app.world_mut();
        world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, f)| f.0 == "/postures/0/transform/offsetX")
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
    assert_eq!(document(&app)["postures"][0]["transform"]["offsetX"], 2.5);
    assert_eq!(app.world().resource::<State>().host, Some(host));
    assert!(app.world().get::<EditableText>(field).is_some());
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Edit(crate::application::EditCommand::Undo));
    app.update();
    app.update();
    assert_eq!(document(&app)["postures"][0]["transform"]["offsetX"], 0.);
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
        .join("resources/weapons/weapon_native_test.smweapon");
    for _ in 0..500 {
        app.update();
        if serde_json::from_str::<Value>(&std::fs::read_to_string(&path).unwrap()).unwrap()["postures"]
            [0]["transform"]["offsetX"]
            == 3.5
        {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    let saved: Value = serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap();
    assert_eq!(saved["postures"][0]["transform"]["offsetX"], 3.5);
}

#[test]
fn posture_selection_and_collider_actions_keep_correct_target() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    let source = weapon::edit(
        &weapon::template("fixture").unwrap().to_string(),
        "posture.add",
        Value::Null,
        0,
    )
    .unwrap();
    let path = dir.path().join("scripts/fixture.smweapon");
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
    app.update();
    settle(&mut app);
    let entity = {
        let w = app.world_mut();
        w.query::<(Entity, &Action)>()
            .iter(w)
            .find(|(_, a)| matches!(a, Action::Select(false, 1)))
            .unwrap()
            .0
    };
    app.world_mut()
        .entity_mut(entity)
        .insert(Interaction::Pressed);
    app.update();
    app.update();
    app.update();
    let field = {
        let w = app.world_mut();
        w.query::<(Entity, &Field)>()
            .iter(w)
            .find(|(_, f)| f.0 == "/postures/1/attachmentPoint")
            .unwrap()
            .0
    };
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("fixture_socket");
    app.update();
    app.update();
    assert_eq!(
        document(&app)["postures"][1]["attachmentPoint"],
        "fixture_socket"
    );
    assert_eq!(
        document(&app)["postures"][0]["attachmentPoint"],
        "RightHandSocket"
    );
    let section = {
        let w = app.world_mut();
        w.query::<(Entity, &Action)>()
            .iter(w)
            .find(|(_, a)| matches!(a, Action::Section(true)))
            .unwrap()
            .0
    };
    app.world_mut()
        .entity_mut(section)
        .insert(Interaction::Pressed);
    app.update();
    app.update();
    let add = {
        let w = app.world_mut();
        w.query::<(Entity, &Action)>()
            .iter(w)
            .find(|(_, a)| matches!(a, Action::Edit("collider.box")))
            .unwrap()
            .0
    };
    app.world_mut().entity_mut(add).insert(Interaction::Pressed);
    app.update();
    app.update();
    app.update();
    assert_eq!(document(&app)["colliders"][0]["shape"], "box");
    let field = {
        let w = app.world_mut();
        w.query::<(Entity, &Field)>()
            .iter(w)
            .find(|(_, f)| f.0 == "/colliders/0/shape")
            .unwrap()
            .0
    };
    app.world_mut().get_mut::<Choice>(field).unwrap().0 = "sphere".into();
    app.update();
    app.update();
    assert_eq!(document(&app)["colliders"][0]["shape"], "sphere");
    let animation = app
        .world_mut()
        .spawn((Control("animation"), Choice("First clip".into())))
        .id();
    app.update();
    assert!(app.world().resource::<State>().playing);
    assert!(app.world().resource::<State>().render.restart);
    app.world_mut().resource_mut::<State>().playing = false;
    app.world_mut().get_mut::<Choice>(animation).unwrap().0 = "Second clip".into();
    app.update();
    assert!(app.world().resource::<State>().playing);
    assert_eq!(app.world().resource::<State>().animation, "Second clip");
    app.world_mut()
        .get_mut::<Choice>(animation)
        .unwrap()
        .0
        .clear();
    app.update();
    assert!(!app.world().resource::<State>().playing);
}
