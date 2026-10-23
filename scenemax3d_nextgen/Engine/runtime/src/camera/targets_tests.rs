use super::*;
use bevy::ecs::system::RunSystemOnce;
use scenemax_runtime_vm_core::{references, typed};

fn world() -> (World, Entity) {
    let mut world = World::new();
    let mut time = Time::<()>::default();
    time.advance_by(std::time::Duration::from_secs(1));
    world.insert_resource(time);
    world.init_resource::<SceneMaxVars>();
    world.init_resource::<SceneMaxCameraSystem>();
    let camera = world
        .spawn((
            Camera3d::default(),
            Transform::default(),
            SceneMaxCameraModifierState::default(),
        ))
        .id();
    (world, camera)
}

fn subject(world: &mut World, name: &str, id: u32, position: Vec3) -> Entity {
    typed::store(
        &mut world.resource_mut::<SceneMaxVars>(),
        &format!("{name}.network.id"),
        serde_json::json!(id),
    );
    world
        .spawn((
            SceneMaxEntity {
                name: name.into(),
                runtime_name: name.into(),
            },
            Transform::from_translation(position),
        ))
        .id()
}

fn declare(world: &mut World, a: &str, b: &str) {
    let program = scenemax_parser::parse_program(&format!(
        "view = camera.system.fighting({a}, {b}, depth 18, height 3, side 1.5, min distance 10, max distance 28, damping 8)\ncamera.system = view"
    )).unwrap();
    let mut vars = world.remove_resource::<SceneMaxVars>().unwrap();
    let mut cameras = world.remove_resource::<SceneMaxCameraSystem>().unwrap();
    apply_startup_runs(
        &program,
        &mut world.commands(),
        &mut vars,
        &mut SceneMaxObjectPools::default(),
        &mut cameras,
        &mut SceneMaxRuntimeAssets::default(),
        &mut DelayedActionQueue::default(),
        &mut SceneMaxUiActionQueue::default(),
        &collect_functions_by_name(&program),
        &HashMap::new(),
        &mut HashMap::new(),
        &HashMap::new(),
        &HashMap::new(),
        &mut SceneMaxColliderBounds::default(),
    );
    world.insert_resource(vars);
    world.insert_resource(cameras);
    world.flush();
}

fn assert_position(world: &World, camera: Entity, expected: Vec3) {
    let actual = world.get::<Transform>(camera).unwrap().translation;
    assert!(
        actual.abs_diff_eq(expected, 0.01),
        "{actual:?} != {expected:?}"
    );
    assert!(world.get::<Transform>(camera).unwrap().rotation.is_finite());
}

#[test]
fn fighting_camera_tracks_either_single_target_and_duplicate_targets() {
    for (a, b) in [
        ("subject", "0"),
        ("0", "subject"),
        ("subject", "subject"),
        ("42", "0"),
        ("0", "42"),
        ("42", "42"),
    ] {
        let (mut world, camera) = world();
        subject(&mut world, "subject", 42, Vec3::new(10., 0., 4.));
        declare(&mut world, a, b);
        world.run_system_once(update_fighting_camera).unwrap();
        assert_position(&world, camera, Vec3::new(8.5, 4.05, 14.));
        assert_eq!(
            world
                .get::<SceneMaxCameraModifierState>(camera)
                .unwrap()
                .base_look_at,
            Some(Vec3::new(10., 1.05, 4.))
        );
    }
}

#[test]
fn fighting_camera_resolves_numeric_variables_network_properties_and_late_peers() {
    let (mut world, camera) = world();
    // This ID cannot be represented exactly as f32.
    subject(
        &mut world,
        "local_instance",
        16_777_217,
        Vec3::new(10., 0., 4.),
    );
    {
        let mut vars = world.resource_mut::<SceneMaxVars>();
        references::assign(
            "chosen",
            references::Reference::entity("local_instance"),
            &mut vars,
            None,
            false,
        )
        .unwrap();
        typed::store(&mut vars, "other_id", serde_json::json!(43));
    }
    declare(&mut world, "chosen.network.id", "other_id");
    world.run_system_once(update_fighting_camera).unwrap();
    assert_position(&world, camera, Vec3::new(8.5, 4.05, 14.));
    let peer = subject(
        &mut world,
        "replicated_instance",
        43,
        Vec3::new(20., 0., 4.),
    );
    world.run_system_once(update_fighting_camera).unwrap();
    assert_position(&world, camera, Vec3::new(16.5, 4.05, 15.5));
    world.despawn(peer);
    world.run_system_once(update_fighting_camera).unwrap();
    assert_position(&world, camera, Vec3::new(8.5, 4.05, 14.));
}

#[test]
fn fighting_camera_with_no_targets_frames_origin_without_nan() {
    let (mut world, camera) = world();
    subject(&mut world, "unregistered", 0, Vec3::splat(100.));
    declare(&mut world, "0", "0");
    world.run_system_once(update_fighting_camera).unwrap();
    assert_position(&world, camera, Vec3::new(-1.5, 4.05, 10.));
}

#[test]
fn function_camera_declarations_rebind_scoped_values_at_execution() {
    let (mut world, camera) = world();
    subject(&mut world, "instance", 42, Vec3::new(10., 0., 4.));
    declare(&mut world, "0", "0");
    let program = scenemax_parser::parse_program("configure = {\nview = camera.system.fighting(slot, 0, height 3, side 1.5)\ncamera.system = view\n}").unwrap();
    let Statement::FunctionDef(function) = &program.statements[0] else {
        panic!("expected function")
    };
    let actions = function.actions.clone();
    world
        .run_system_once(
            move |mut commands: Commands,
                  mut vars: ResMut<SceneMaxVars>,
                  mut cameras: ResMut<SceneMaxCameraSystem>,
                  mut entities: ParamSet<(
                Query<(
                    Entity,
                    &SceneMaxEntity,
                    &Transform,
                    Option<&GlobalTransform>,
                    Option<&ChildOf>,
                )>,
                Query<(
                    Entity,
                    &SceneMaxEntity,
                    &mut Transform,
                    Option<&SceneMaxGltf>,
                    Option<&CurrentAnimation>,
                    Option<&mut Visibility>,
                    Option<&SceneMaxCharacterController>,
                    Option<&mut SceneMaxCharacterMotor>,
                )>,
            )>| {
                let mut scope = SceneMaxScopeFrame::default();
                typed::store(&mut vars, "slot", serde_json::json!(99));
                references::assign(
                    "slot",
                    references::Reference::new(serde_json::json!(" 42 ")),
                    &mut vars,
                    Some(&mut scope),
                    true,
                )
                .unwrap();
                for action in &actions {
                    apply_key_action(
                        action,
                        &mut HashMap::new(),
                        &mut vars,
                        &mut SceneMaxObjectPools::default(),
                        Some(&mut cameras),
                        &HashMap::new(),
                        &HashMap::new(),
                        &mut HashMap::new(),
                        &mut SceneMaxRuntimeAssets::default(),
                        &SceneMaxAnimationDurations::default(),
                        &mut SceneMaxColliderBounds::default(),
                        None,
                        None,
                        None,
                        Some(&mut scope),
                        None,
                        &mut commands,
                        &mut entities,
                        &mut HashMap::new(),
                    );
                }
            },
        )
        .unwrap();
    world.run_system_once(update_fighting_camera).unwrap();
    assert_position(&world, camera, Vec3::new(8.5, 4.05, 14.));
}

#[test]
fn third_person_camera_accepts_network_identity_targets() {
    let (mut world, camera) = world();
    subject(
        &mut world,
        "replicated_instance",
        42,
        Vec3::new(10., 0., 4.),
    );
    let program = scenemax_parser::parse_program(
        "view = camera.system.third_person(42, distance 12, height 3, side 0)",
    )
    .unwrap();
    let mut cameras = SceneMaxCameraSystem::default();
    register_camera_system(
        &program.statements[0],
        world.resource::<SceneMaxVars>(),
        &SceneMaxObjectPools::default(),
        None,
        &mut cameras,
    );
    select_camera_system("view", &mut cameras);
    world.insert_resource(cameras);
    world.run_system_once(update_third_person_camera).unwrap();
    assert!(world.get::<Transform>(camera).unwrap().translation.length() > 1.);
    assert!(
        world
            .get::<SceneMaxCameraModifierState>(camera)
            .unwrap()
            .base_look_at
            .is_some()
    );
}
