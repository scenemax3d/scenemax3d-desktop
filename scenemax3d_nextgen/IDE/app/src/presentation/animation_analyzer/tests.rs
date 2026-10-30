use super::*;
use scenemax_ui_input::Interaction;
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
    assert_eq!(
        saved["models"][0]["animationFrameRanges"][0]["end"],
        4,
        "{}",
        app.world().resource::<Session>().status
    );
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

#[test]
fn shared_model_ranges_save_globally_and_undo_without_changing_project() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    let shared = dir.path().join("resources");
    let project = dir.path().join("projects/game");
    std::fs::create_dir_all(project.join("scripts")).unwrap();
    app.insert_resource(Session::new(
        scenemax_ide_services::Filesystem::open_project(&project).unwrap(),
    ));
    std::fs::create_dir_all(shared.join("Models")).unwrap();
    std::fs::write(
        shared.join("Models/fixture.gltf"),
        include_bytes!(
            "../../../../../Tests/fixtures/animation_analyzer/project/resources/Models/fixture.gltf"
        ),
    )
    .unwrap();
    let catalog = shared.join("Models/models.json");
    let data = json!({"models":[{"name":"inventory_fixture","path":"Models/fixture.gltf","custom":"preserve"}]});
    std::fs::write(&catalog, data.to_string()).unwrap();
    let project_catalog = scenemax_ide_services::animation_analyzer::catalog(&project).unwrap();
    let before = std::fs::read(&project_catalog).unwrap();
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Open(project_catalog.clone()));
    app.world_mut().resource_mut::<State>().inventory =
        scenemax_ide_services::animation_analyzer::inventory_at(&shared).unwrap();
    for _ in 0..500 {
        app.update();
        if app.world().resource::<State>().source.is_some() {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    app.update();
    let state = app.world().resource::<State>();
    assert_eq!(
        state.data["models"][0]["name"],
        "inventory_fixture",
        "{}; {}",
        app.world().resource::<Session>().status,
        state.error
    );
    assert_eq!(state.project_count, 1);
    assert_eq!(
        state.source.as_ref().unwrap().root,
        shared.canonicalize().unwrap()
    );
    assert!(!state.source.as_ref().unwrap().timelines.is_empty());
    assert_eq!(std::fs::read(&project_catalog).unwrap(), before);
    let save = {
        let world = app.world_mut();
        world
            .query::<(Entity, &Action)>()
            .iter(world)
            .find(|(_, a)| matches!(a, Action::Save))
            .unwrap()
            .0
    };
    *app.world_mut().get_mut::<Interaction>(save).unwrap() = Interaction::Pressed;
    app.update();
    assert_eq!(std::fs::read(&project_catalog).unwrap(), before);
    assert_eq!(
        serde_json::from_slice::<Value>(&std::fs::read(&catalog).unwrap()).unwrap(),
        data
    );
    let add = {
        let world = app.world_mut();
        world
            .query::<(Entity, &Action)>()
            .iter(world)
            .find(|(_, action)| matches!(action, Action::Add))
            .unwrap()
            .0
    };
    *app.world_mut().get_mut::<Interaction>(save).unwrap() = Interaction::None;
    *app.world_mut().get_mut::<Interaction>(add).unwrap() = Interaction::Pressed;
    app.update();
    *app.world_mut().get_mut::<Interaction>(add).unwrap() = Interaction::None;
    app.update();
    assert_eq!(
        app.world().resource::<State>().data["models"][0]["animationFrameRanges"][0]["name"],
        "record_1"
    );
    let field = {
        let world = app.world_mut();
        world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, field)| field.0 == "/models/0/animationFrameRanges/0/name")
            .unwrap()
            .0
    };
    app.world_mut()
        .get_mut::<EditableText>(field)
        .unwrap()
        .editor_mut()
        .set_text("flight");
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Save);
    for _ in 0..500 {
        app.update();
        let saved: Value = serde_json::from_slice(&std::fs::read(&catalog).unwrap()).unwrap();
        if saved["models"][0]["animationFrameRanges"][0]["name"] == "flight" {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    let saved: Value = serde_json::from_slice(&std::fs::read(&catalog).unwrap()).unwrap();
    assert_eq!(
        saved["models"][0]["animationFrameRanges"][0]["name"],
        "flight"
    );
    assert_eq!(saved["models"][0]["custom"], "preserve");
    assert_eq!(std::fs::read(&project_catalog).unwrap(), before);
    let records = scenemax_assets::model_animation_records(
        &project.join("resources"),
        Some(&shared),
        "inventory_fixture",
    )
    .unwrap();
    assert_eq!(records[0].0.name, "flight");
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Edit(crate::application::EditCommand::Undo));
    app.update();
    app.update();
    assert_eq!(
        app.world().resource::<State>().data["models"][0]["animationFrameRanges"][0]["name"],
        "record_1"
    );
}

#[test]
fn numeric_scrub_formatting_does_not_pause_playback() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    let path = scenemax_ide_services::animation_analyzer::catalog(dir.path()).unwrap();
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Open(path));
    for _ in 0..500 {
        app.update();
        if app.world().resource::<State>().parts.is_some() {
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    app.update();
    let editor = {
        let w = app.world_mut();
        w.query::<(Entity, &Control)>()
            .iter(w)
            .find(|(_, c)| c.0 == "seek")
            .unwrap()
            .0
    };
    {
        let mut state = app.world_mut().resource_mut::<State>();
        state.playing = true;
        state.time = 0.;
        state.seek = None;
    }
    // Playback writes a fixed-decimal display; native number inputs use compact formatting.
    app.world_mut().get_mut::<Control>(editor).unwrap().1 = "0.0".into();
    app.world_mut()
        .get_mut::<EditableText>(editor)
        .unwrap()
        .editor_mut()
        .set_text("0");
    app.update();
    assert!(app.world().resource::<State>().playing);
    assert!(app.world().resource::<State>().seek.is_none());
    // An actual manual seek still pauses and requests the selected frame.
    app.world_mut()
        .get_mut::<EditableText>(editor)
        .unwrap()
        .editor_mut()
        .set_text("0.5");
    app.update();
    assert!(!app.world().resource::<State>().playing);
    assert_eq!(app.world().resource::<State>().seek, Some(0.5));
}
