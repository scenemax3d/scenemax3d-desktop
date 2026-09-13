use super::*;
use bevy::ecs::system::RunSystemOnce;

#[test]
fn viewport_handle_drag_moves_rotates_scales_and_escape_restores() {
    for (kind, mode) in [
        ("BOX", Mode::Move),
        ("BOX", Mode::Rotate),
        ("BOX", Mode::Scale),
        ("CAMERA", Mode::Move),
        ("CAMERA", Mode::Rotate),
    ] {
        let mut world = World::new();
        world.init_resource::<Assets<Mesh>>();
        world.init_resource::<Assets<GizmoMaterial>>();
        world.init_resource::<ButtonInput<MouseButton>>();
        world.init_resource::<ButtonInput<KeyCode>>();
        world.insert_resource(State { mode, ..default() });
        let owner = world.spawn_empty().id();
        let camera = world
            .spawn((
                Camera3d::default(),
                GlobalTransform::from_translation(Vec3::new(0., 0., 10.)),
                Projection::Perspective(PerspectiveProjection {
                    fov: std::f32::consts::FRAC_PI_2,
                    aspect_ratio: 1.,
                    ..default()
                }),
            ))
            .id();
        let object = world
            .spawn((
                SceneObject(0, Transform::IDENTITY),
                Transform::IDENTITY,
                ChildOf(owner),
            ))
            .id();
        world.spawn((
            Viewport(camera),
            ComputedNode {
                size: Vec2::splat(200.),
                inverse_scale_factor: 1.,
                ..default()
            },
            UiGlobalTransform::from_translation(Vec2::splat(100.)),
        ));
        let mut window = Window {
            resolution: (200, 200).into(),
            ..default()
        };
        let initial = if mode == Mode::Rotate {
            Vec2::new(180., 100.)
        } else {
            Vec2::new(115., 100.)
        };
        window.set_cursor_position(Some(initial));
        let window = world.spawn(window).id();
        let dir = tempfile::tempdir().unwrap();
        let source = if kind == "CAMERA" {
            r#"{"entities":[]}"#
        } else {
            r#"{"entities":[{"type":"BOX"}]}"#
        };
        world.insert_resource(SceneState {
            scene: Some(scenemax_ide_services::scene3d::load(dir.path(), source).unwrap()),
            world: Some(owner),
            camera: Some(camera),
            ..default()
        });
        world
            .resource_mut::<ButtonInput<MouseButton>>()
            .press(MouseButton::Left);
        world.run_system_once(update).unwrap();
        assert!(world.resource::<State>().drag.is_some());
        world.resource_mut::<ButtonInput<MouseButton>>().clear();
        let moved = if mode == Mode::Rotate {
            Vec2::new(100., 20.)
        } else {
            Vec2::new(130., 100.)
        };
        world
            .get_mut::<Window>(window)
            .unwrap()
            .set_cursor_position(Some(moved));
        world.run_system_once(update).unwrap();
        let result = world.get::<Transform>(object).unwrap();
        match mode {
            Mode::Move => assert!((result.translation.x - 1.5).abs() < 0.01),
            Mode::Scale => assert!((result.scale.x - 1.15).abs() < 0.01),
            Mode::Rotate => assert!((result.rotation * Vec3::X).distance(Vec3::Y) < 0.01),
        }
        world
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::Escape);
        world.run_system_once(update).unwrap();
        assert_eq!(
            *world.get::<Transform>(object).unwrap(),
            Transform::IDENTITY
        );
        assert!(world.resource::<State>().drag.is_none());
    }
}
