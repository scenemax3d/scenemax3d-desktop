use super::*;
fn settle(app: &mut App) {
    app.update();
    app.update();
    app.update();
}
fn document(app: &App) -> Value {
    let session = app.world().resource::<Session>();
    ik::parse(
        session
            .workspace
            .document(session.workspace.active_id().unwrap())
            .unwrap()
            .text(),
    )
    .unwrap()
}
#[test]
fn create_ik_opens_retained_editor_and_saves_native_edits_with_undo() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Tree(
            scenemax_ide_services::TreeOperation::CreateIk {
                name: "Native test".into(),
                kind: "TwoBoneIK".into(),
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
    assert_eq!(document(&app)["id"], "ik_native_test");
    let host = app.world().resource::<State>().host.unwrap();
    let field = {
        let world = app.world_mut();
        world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, f)| f.0 == "/layers/0/weight")
            .unwrap()
            .0
    };
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("0.5");
    app.update();
    app.update();
    assert_eq!(document(&app)["layers"][0]["weight"], 0.5);
    assert_eq!(app.world().resource::<State>().host, Some(host));
    assert!(app.world().get::<EditableText>(field).is_some());
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Edit(crate::application::EditCommand::Undo));
    app.update();
    app.update();
    assert_eq!(document(&app)["layers"][0]["weight"], 1.);
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("0.75");
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Save);
    app.update();
    app.update();
    let path = dir.path().join("resources/ik/ik_native_test.smik");
    for _ in 0..500 {
        app.update();
        if serde_json::from_str::<Value>(&std::fs::read_to_string(&path).unwrap()).unwrap()["layers"]
            [0]["weight"]
            == 0.75
        {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    let saved: Value = serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap();
    assert_eq!(saved["layers"][0]["weight"], 0.75);
}

#[test]
fn layer_actions_and_target_bone_edit_change_the_selected_runtime_contact() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    let source = ik::template("Contacts", "TwoBoneIK").unwrap().to_string();
    let path = dir.path().join("scripts/contacts.smik");
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
    let add = {
        let w = app.world_mut();
        w.query::<(Entity, &Action)>()
            .iter(w)
            .find(|(_, a)| matches!(a, Action::Add))
            .unwrap()
            .0
    };
    app.world_mut().entity_mut(add).insert(Interaction::Pressed);
    settle(&mut app);
    assert_eq!(document(&app)["layers"].as_array().unwrap().len(), 2);
    assert_eq!(app.world().resource::<State>().layer, 1);
    app.world_mut()
        .spawn((Control("target_bone"), Choice("grip.socket".into())));
    settle(&mut app);
    assert_eq!(
        document(&app)["layers"][1]["target"],
        r#"target."grip.socket""#
    );
    assert_eq!(document(&app)["layers"][0]["target"], "");
    let id_field = {
        let w = app.world_mut();
        w.query::<(Entity, &Field)>()
            .iter(w)
            .find(|(_, f)| f.0 == "/layers/1/id")
            .unwrap()
            .0
    };
    let old_name = document(&app)["layers"][1]["name"].clone();
    app.world_mut()
        .get_mut::<EditableText>(id_field)
        .unwrap()
        .editor_mut()
        .set_text("new_contact");
    settle(&mut app);
    assert_eq!(document(&app)["layers"][1]["id"], "new_contact");
    assert_eq!(document(&app)["layers"][1]["name"], old_name);
    assert!(app.world().get::<EditableText>(id_field).is_some());
    {
        let w = app.world_mut();
        let child = w
            .query::<(&Control, &Children)>()
            .iter(w)
            .find(|(control, _)| control.0 == "layer")
            .unwrap()
            .1[0];
        assert!(
            w.get::<Text>(child)
                .unwrap()
                .0
                .starts_with("new_contact · ")
        );
    }
    let play = {
        let w = app.world_mut();
        w.query::<(Entity, &Action)>()
            .iter(w)
            .find(|(_, a)| matches!(a, Action::PlayAll))
            .unwrap()
            .0
    };
    app.world_mut()
        .entity_mut(play)
        .insert(Interaction::Pressed);
    app.update();
    assert_eq!(app.world().resource::<State>().running, 3);
}
