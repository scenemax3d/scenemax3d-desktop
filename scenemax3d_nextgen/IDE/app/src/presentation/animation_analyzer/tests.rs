use super::*;
#[test]
fn retained_records_save_same_frame_and_undo_without_losing_unknown_fields() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    let folder = dir.path().join("resources/Models");
    std::fs::create_dir_all(&folder).unwrap();
    std::fs::write(
        folder.join("fixture.gltf"),
        include_bytes!(
            "../../../../../Tests/fixtures/animation_analyzer/project/resources/Models/fixture.gltf"
        ),
    )
    .unwrap();
    let path = folder.join("models-ext.json");
    std::fs::write(&path,include_bytes!("../../../../../Tests/fixtures/animation_analyzer/project/resources/Models/models-ext.json")).unwrap();
    app.world_mut().resource_mut::<State>().open = true;
    for _ in 0..500 {
        app.update();
        if app.world().resource::<State>().source.is_some() {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    app.update();
    app.world_mut().resource_mut::<State>().mode = 2;
    for _ in 0..2 {
        let old_host = app.world().resource::<State>().host.unwrap();
        let refresh = {
            let w = app.world_mut();
            w.query::<(Entity, &Action)>()
                .iter(w)
                .find(|(_, a)| matches!(a, Action::Refresh))
                .unwrap()
                .0
        };
        *app.world_mut().get_mut::<Interaction>(refresh).unwrap() = Interaction::Pressed;
        app.update();
        if let Some(mut interaction) = app.world_mut().get_mut::<Interaction>(refresh) {
            *interaction = Interaction::None;
        }
        for _ in 0..500 {
            app.update();
            let state = app.world().resource::<State>();
            if state.host.is_some_and(|h| h != old_host) && state.source.is_some() {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(5));
        }
        let state = app.world().resource::<State>();
        assert_ne!(state.host, Some(old_host));
        assert_eq!(state.mode, 2);
        let parts = state.parts.unwrap();
        assert!(app.world().get_entity(parts.viewport).is_ok());
        assert!(app.world().get_entity(parts.transport).is_ok());
    }
    for mode in [1, 2, 0] {
        let button = {
            let w = app.world_mut();
            w.query::<(Entity, &Action)>()
                .iter(w)
                .find(|(_, a)| matches!(a, Action::Mode(m) if *m == mode))
                .unwrap()
                .0
        };
        *app.world_mut().get_mut::<Interaction>(button).unwrap() = Interaction::Pressed;
        app.update();
        assert_eq!(app.world().resource::<State>().mode, mode);
        *app.world_mut().get_mut::<Interaction>(button).unwrap() = Interaction::None;
        app.update();
    }
    let host = app.world().resource::<State>().host.unwrap();
    let field = {
        let w = app.world_mut();
        w.query::<(Entity, &Field)>()
            .iter(w)
            .find(|(_, f)| f.0 == "/models/0/animationFrameRanges/0/end")
            .unwrap()
            .0
    };
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("4");
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Save);
    for _ in 0..500 {
        app.update();
        let saved: Value = serde_json::from_str(&std::fs::read_to_string(&path).unwrap()).unwrap();
        if saved["models"][0]["animationFrameRanges"][0]["end"] == 4 {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    let saved: Value = serde_json::from_str(&std::fs::read_to_string(&path).unwrap()).unwrap();
    assert_eq!(saved["models"][0]["animationFrameRanges"][0]["end"], 4);
    assert_eq!(
        saved["models"][0]["animationFrameRanges"][0]["custom"],
        "keep"
    );
    assert_eq!(saved["extra"], "preserve");
    assert_eq!(app.world().resource::<State>().host, Some(host));
    assert!(app.world().get::<EditableText>(field).is_some());
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Edit(crate::application::EditCommand::Undo));
    app.update();
    app.update();
    assert_eq!(
        app.world().resource::<State>().data["models"][0]["animationFrameRanges"][0]["end"],
        3
    );
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::CloseTab);
    app.update();
    assert!(
        app.world()
            .resource::<Session>()
            .workspace
            .active_id()
            .is_some()
    ); // dirty close is guarded
}
