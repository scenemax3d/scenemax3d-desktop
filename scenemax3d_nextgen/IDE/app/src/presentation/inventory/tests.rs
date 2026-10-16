use super::*;
fn wait(app: &mut App) {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    for _ in 0..300 {
        app.update();
        if app.world().resource::<State>().task.is_none() {
            return;
        }
        std::thread::sleep(std::time::Duration::from_millis(5));
    }
    panic!("inventory worker timed out");
}
#[test]
fn inventory_opens_filters_retains_controls_and_closes_without_editing_document() {
    let (mut app, dir, _) = crate::tests::app();
    std::fs::create_dir_all(dir.path().join("resources/audio")).unwrap();
    std::fs::write(dir.path().join("resources/audio/a.wav"), "fixture").unwrap();
    std::fs::write(
        dir.path().join("resources/audio/audio-ext.json"),
        r#"{"sounds":[{"name":"sample_sound","path":"audio/a.wav"}]}"#,
    )
    .unwrap();
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Open);
    wait(&mut app);
    assert!(app.world().resource::<State>().open);
    assert!(
        app.world()
            .resource::<State>()
            .inventory
            .assets
            .iter()
            .any(|a| a.name == "sample_sound")
    );
    let shell = app.world().resource::<State>().host.unwrap();
    for _ in 0..3 {
        app.update();
        assert_eq!(app.world().resource::<State>().host, Some(shell));
    }
    let world = app.world_mut();
    let search = world
        .query::<(Entity, &Field)>()
        .iter(world)
        .find(|(_, f)| **f == Field::Search)
        .unwrap()
        .0;
    world
        .get_mut::<EditableText>(search)
        .unwrap()
        .editor_mut()
        .set_text("no_such_asset");
    app.update();
    assert!(app.world().resource::<State>().filtered().is_empty());
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Close);
    app.update();
    assert!(!app.world().resource::<State>().open);
    assert!(
        !app.world()
            .resource::<Session>()
            .workspace
            .has_dirty_documents()
    );
    assert!(app.world().get_entity(shell).is_err());
}
#[test]
fn archive_requires_review_and_cancel_leaves_resource_on_disk() {
    let (mut app, dir, _) = crate::tests::app();
    std::fs::write(dir.path().join("scripts/sample.smmat"), "{}").unwrap();
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Open);
    wait(&mut app);
    let id = app
        .world()
        .resource::<State>()
        .inventory
        .assets
        .iter()
        .find(|a| a.name == "sample")
        .unwrap()
        .id
        .clone();
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Select(id, false));
    app.update();
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Review(Operation::Archive));
    wait(&mut app);
    assert!(app.world().resource::<State>().plan.is_some());
    assert!(dir.path().join("scripts/sample.smmat").exists());
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Cancel);
    app.update();
    assert!(app.world().resource::<State>().plan.is_none());
    assert!(dir.path().join("scripts/sample.smmat").exists());
}

#[test]
fn menu_dispatches_on_press_before_menu_closes() {
    use bevy::picking::{
        backend::HitData,
        pointer::{Location, PointerId},
    };
    let mut world = World::new();
    world.register_component::<Window>();
    world.init_resource::<State>();
    let host = world.spawn(Node::default()).id();
    let row = menu_item(&mut world.commands(), host, "Project Inventory");
    world.flush();
    world.trigger(Pointer::new(
        PointerId::Mouse,
        Location {
            target: bevy::camera::NormalizedRenderTarget::None {
                width: 800,
                height: 600,
            },
            position: Vec2::ZERO,
        },
        Press {
            button: PointerButton::Primary,
            count: 1,
            hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
        },
        row,
    ));
    world.flush();
    assert!(matches!(
        world.resource::<State>().actions.first(),
        Some(Action::Open)
    ));
}
#[test]
fn committing_asset_operation_blocks_restart() {
    let (mut app, _dir, _) = crate::tests::app();
    app.world_mut()
        .resource_mut::<Session>()
        .asset_operation_pending = true;
    app.world_mut()
        .resource_mut::<CommandQueue>()
        .0
        .push_back(Command::Restart);
    app.update();
    assert!(!app.world().resource::<Session>().restarting);
    assert!(
        app.world()
            .resource::<Session>()
            .status
            .contains("asset operation")
    );
}
