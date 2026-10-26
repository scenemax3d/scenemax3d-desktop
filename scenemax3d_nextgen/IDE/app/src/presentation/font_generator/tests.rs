use super::*;
fn wait(app: &mut App) {
    for _ in 0..1000 {
        app.update();
        let s = app.world().resource::<State>();
        if s.catalog.is_some() && s.task.is_none() && s.due.is_none() {
            return;
        }
        std::thread::sleep(Duration::from_millis(5));
    }
    panic!("font worker timed out");
}
#[test]
fn native_form_keeps_controls_edits_charset_and_exports_runtime_artifacts() {
    bevy::tasks::IoTaskPool::get_or_init(bevy::tasks::TaskPool::new);
    let (mut app, dir, _) = crate::tests::app();
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Open);
    wait(&mut app);
    let host = app.world().resource::<State>().host.unwrap();
    let world = app.world_mut();
    let name = world
        .query::<(Entity, &Field)>()
        .iter(world)
        .find(|(_, f)| **f == Field::Name)
        .unwrap()
        .0;
    world
        .get_mut::<EditableText>(name)
        .unwrap()
        .editor_mut()
        .set_text("native_font");
    let choice = world
        .query::<(Entity, &Field)>()
        .iter(world)
        .find(|(_, f)| **f == Field::Charset)
        .unwrap()
        .0;
    world.get_mut::<property::Choice>(choice).unwrap().0 = "Basic Latin".into();
    app.update();
    assert_eq!(
        app.world().resource::<State>().options.characters,
        scenemax_ide_core::font_generator::BASIC_LATIN
    );
    assert_eq!(app.world().resource::<State>().host, Some(host));
    assert_eq!(
        app.world().get::<EditableText>(name).unwrap().value(),
        "native_font"
    );
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Export);
    wait(&mut app);
    assert!(dir.path().join("resources/fonts/native_font.fnt").is_file());
    assert!(dir.path().join("resources/fonts/native_font.png").is_file());
    assert!(app.world().resource::<State>().notice.is_some());
    assert!(!app.world().resource::<Session>().asset_operation_pending);
    assert!(
        !app.world()
            .resource::<Session>()
            .workspace
            .has_dirty_documents()
    );
    app.world_mut()
        .resource_mut::<State>()
        .actions
        .push(Action::Close);
    app.update();
    assert!(!app.world().resource::<State>().open);
}
#[test]
fn menu_dispatches_on_press() {
    use bevy::picking::{
        backend::HitData,
        pointer::{Location, PointerId},
    };
    let mut w = World::new();
    w.register_component::<Window>();
    w.init_resource::<State>();
    let host = w.spawn(Node::default()).id();
    let e = menu_item(&mut w.commands(), host, "Font Generator...");
    w.flush();
    w.trigger(PointerPress {
        pointer: Pointer::new(
            PointerId::Mouse,
            Location {
                target: bevy::camera::NormalizedRenderTarget::None {
                    width: 800,
                    height: 600,
                },
                position: Vec2::ZERO,
            },
        ),
        entity: e,
        button: PointerButton::Primary,
        count: 1,
        hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
    });
    w.flush();
    assert!(matches!(
        w.resource::<State>().actions.first(),
        Some(Action::Open)
    ));
}
