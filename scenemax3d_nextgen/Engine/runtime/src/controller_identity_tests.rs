//! Controller cancellation follows playback identity, not mutable script aliases.
use super::*;
use bevy::ecs::system::RunSystemOnce;

fn setup() -> (World, SceneMaxRuntimeAssets, Entity) {
    let mut world = World::new();
    let entity = world
        .spawn(SceneMaxEntity {
            name: "instance_7".into(),
            runtime_name: "instance_7".into(),
        })
        .id();
    let mut assets = SceneMaxRuntimeAssets::default();
    let program = scenemax_parser::parse_program("control = animation selected.take").unwrap();
    let Statement::Assignment(assignment) = &program.statements[0] else {
        panic!()
    };
    let AssignmentValue::AnimationController(value) = &assignment.value else {
        panic!()
    };
    register_animation_controller_assignment("control", value, &mut assets);
    start_runtime_animation_controller(
        assets
            .animation_controllers_by_name
            .get_mut("control")
            .unwrap(),
        &mut world.commands(),
        entity,
        Handle::default(),
        vec![],
        None,
        None,
    );
    world.flush();
    (world, assets, entity)
}

fn stop(world: &mut World, assets: &mut SceneMaxRuntimeAssets) {
    apply_startup_animation_controller_action(
        &AnimationControllerActionStatement {
            controller: "control".into(),
            action: AnimationControllerAction::Stop,
            rewind: None,
        },
        &SceneMaxObjectPools::default(),
        &mut world.commands(),
        assets,
        &HashMap::new(),
        &HashMap::new(),
        &mut SceneMaxVars::default(),
        &HashMap::new(),
        &HashMap::new(),
    );
}

#[test]
fn alias_stop_cancels_queued_and_playing_instances_without_touching_alias_replacement() {
    for playing in [false, true] {
        let (mut world, mut assets, entity) = setup();
        let lease = world
            .get::<AnimationToPlay>(entity)
            .unwrap()
            .network_lifetime
            .clone()
            .unwrap();
        let lifetime = std::sync::Arc::downgrade(&lease);
        let current = |lease: multiplayer::ActionLease| CurrentAnimation {
            rewind: None,
            identity: Some(std::sync::Arc::downgrade(&lease)),
            clip: "take".into(),
            controller_owner: None,
            network_lifetime: Some(lease),
            playback_graphs: vec![],
            looped: false,
            speed: 1.,
            elapsed_seconds: 0.,
            duration_seconds: 4.,
        };
        // The authored alias now names a different object. Its animation is unrelated.
        let replacement = world
            .spawn((
                SceneMaxEntity {
                    name: "selected".into(),
                    runtime_name: "selected".into(),
                },
                current(multiplayer::ActionLease::new(())),
            ))
            .id();
        if playing {
            world
                .entity_mut(entity)
                .insert(current(lease.clone()))
                .remove::<AnimationToPlay>();
        }
        drop(lease);
        let run = std::sync::Arc::downgrade(
            assets.animation_controllers_by_name["control"]
                .run_lifetime
                .as_ref()
                .unwrap(),
        );
        stop(&mut world, &mut assets);
        assert_eq!(run.strong_count(), 0); // Release the blocked continuation.
        assert_eq!(assets.pending_animation_controller_stops[0].0, entity);
        world.insert_resource(assets);
        world
            .run_system_once(apply_pending_animation_controller_stops)
            .unwrap();
        assert!(world.get::<AnimationToPlay>(entity).is_none());
        assert!(world.get::<CurrentAnimation>(entity).is_none());
        assert!(world.get::<CurrentAnimation>(replacement).is_some());
        assert_eq!(lifetime.strong_count(), 0); // Enables the existing replicated end path.
    }
}

#[test]
fn alias_stop_does_not_cancel_a_new_run_on_the_same_entity() {
    let (mut world, mut assets, entity) = setup();
    stop(&mut world, &mut assets);
    start_runtime_animation_controller(
        assets
            .animation_controllers_by_name
            .get_mut("control")
            .unwrap(),
        &mut world.commands(),
        entity,
        Handle::default(),
        vec![],
        None,
        Some((2., None)),
    );
    world.flush();
    world.insert_resource(assets);
    world
        .run_system_once(apply_pending_animation_controller_stops)
        .unwrap();
    assert_eq!(world.get::<AnimationToPlay>(entity).unwrap().speed, 2.);
}

#[test]
fn despawned_stop_does_not_follow_a_reused_name_or_entity_index() {
    let (mut world, mut assets, entity) = setup();
    stop(&mut world, &mut assets);
    world.despawn(entity);
    let replacement = world
        .spawn(SceneMaxEntity {
            name: "instance_7".into(),
            runtime_name: "instance_7".into(),
        })
        .id();
    start_runtime_animation_controller(
        assets
            .animation_controllers_by_name
            .get_mut("control")
            .unwrap(),
        &mut world.commands(),
        replacement,
        Handle::default(),
        vec![],
        None,
        Some((3., None)),
    );
    world.flush();
    world.insert_resource(assets);
    world
        .run_system_once(apply_pending_animation_controller_stops)
        .unwrap();
    assert_eq!(world.get::<AnimationToPlay>(replacement).unwrap().speed, 3.);
}

#[test]
fn unresolved_asset_timeout_cleans_up_the_resolved_alias_entity() {
    let (mut world, assets, entity) = setup();
    let lifetime = assets.animation_controllers_by_name["control"]
        .lifetime
        .clone();
    world.entity_mut(entity).insert(Transform::default());
    world.insert_resource(assets);
    world.init_resource::<SceneMaxVars>();
    world.init_resource::<SceneMaxStartupProgram>();
    world.init_resource::<DelayedActionQueue>();
    let mut time = Time::<()>::default();
    time.advance_by(std::time::Duration::from_secs(60));
    world.insert_resource(time);
    world
        .run_system_once(update_animation_runtime_controllers)
        .unwrap();
    world
        .run_system_once(apply_pending_animation_controller_stops)
        .unwrap();
    assert!(world.get::<AnimationToPlay>(entity).is_none());
    assert_eq!(lifetime.strong_count(), 0);
    assert!(
        !world
            .resource::<SceneMaxRuntimeAssets>()
            .animation_controllers_by_name["control"]
            .running
    );
}

#[test]
fn runtime_controller_run_prefers_scoped_target_over_pool_alias() {
    let (mut world, mut assets, pooled) = setup();
    world.entity_mut(pooled).insert(Transform::default());
    let scoped = world
        .spawn((
            SceneMaxEntity {
                name: "scoped_instance".into(),
                runtime_name: "scoped_instance".into(),
            },
            Transform::default(),
        ))
        .id();
    assets
        .gltf_handles_by_name
        .insert("instance_7".into(), Handle::default());
    assets
        .gltf_handles_by_name
        .insert("scoped_instance".into(), Handle::default());
    world.insert_resource(assets);
    world.init_resource::<SceneMaxVars>();
    world
        .run_system_once(
            |mut commands: Commands,
             mut assets: ResMut<SceneMaxRuntimeAssets>,
             mut vars: ResMut<SceneMaxVars>,
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
                let mut pools = SceneMaxObjectPools::default();
                pools.aliases.insert("selected".into(), "instance_7".into());
                let mut scope = SceneMaxScopeFrame::default();
                scope
                    .aliases
                    .insert("selected".into(), "scoped_instance".into());
                apply_runtime_animation_controller_action(
                    &AnimationControllerActionStatement {
                        controller: "control".into(),
                        action: AnimationControllerAction::Run,
                        rewind: None,
                    },
                    &mut commands,
                    &mut assets,
                    &pools,
                    Some(&scope),
                    &mut entities,
                    &mut HashMap::new(),
                    &mut vars,
                    &HashMap::new(),
                    &HashMap::new(),
                );
            },
        )
        .unwrap();
    world
        .run_system_once(apply_pending_animation_controller_stops)
        .unwrap();
    assert!(world.get::<AnimationToPlay>(pooled).is_none());
    assert!(world.get::<AnimationToPlay>(scoped).is_some());
    assert_eq!(
        world
            .resource::<SceneMaxRuntimeAssets>()
            .animation_controllers_by_name["control"]
            .source
            .as_ref()
            .unwrap()
            .0,
        scoped
    );
}

#[test]
fn controller_restart_retires_old_playback_even_when_retargeting_fails() {
    for playing in [false, true] {
        for destination in ["instance_7", "replacement", "missing"] {
            let (mut world, mut assets, original) = setup();
            let old = assets.animation_controllers_by_name["control"]
                .lifetime
                .clone();
            let old_run = std::sync::Arc::downgrade(
                assets.animation_controllers_by_name["control"]
                    .run_lifetime
                    .as_ref()
                    .unwrap(),
            );
            if playing {
                let lease = world
                    .get::<AnimationToPlay>(original)
                    .unwrap()
                    .network_lifetime
                    .clone();
                world
                    .entity_mut(original)
                    .remove::<AnimationToPlay>()
                    .insert(CurrentAnimation {
                        rewind: None,
                        identity: Some(old.clone()),
                        clip: "take".into(),
                        controller_owner: None,
                        network_lifetime: lease,
                        playback_graphs: vec![],
                        looped: false,
                        speed: 1.,
                        elapsed_seconds: 1.,
                        duration_seconds: 4.,
                    });
            }
            let replacement = world
                .spawn(SceneMaxEntity {
                    name: "replacement".into(),
                    runtime_name: "replacement".into(),
                })
                .id();
            let mut pools = SceneMaxObjectPools::default();
            pools.aliases.insert("selected".into(), destination.into());
            apply_startup_animation_controller_action(
                &AnimationControllerActionStatement {
                    controller: "control".into(),
                    action: AnimationControllerAction::Run,
                    rewind: None,
                },
                &pools,
                &mut world.commands(),
                &mut assets,
                &HashMap::from([
                    ("instance_7".into(), original),
                    ("replacement".into(), replacement),
                ]),
                &HashMap::from([
                    ("instance_7".into(), Handle::default()),
                    ("replacement".into(), Handle::default()),
                ]),
                &mut SceneMaxVars::default(),
                &HashMap::new(),
                &HashMap::new(),
            );
            world.flush();
            assert_eq!(old_run.strong_count(), 0);
            world.insert_resource(assets);
            world
                .run_system_once(apply_pending_animation_controller_stops)
                .unwrap();
            assert_eq!(old.strong_count(), 0);
            assert!(world.get::<CurrentAnimation>(original).is_none());
            assert_eq!(
                world.get::<AnimationToPlay>(original).is_some(),
                destination == "instance_7"
            );
            assert_eq!(
                world.get::<AnimationToPlay>(replacement).is_some(),
                destination == "replacement"
            );
            assert_eq!(
                world
                    .resource::<SceneMaxRuntimeAssets>()
                    .animation_controllers_by_name["control"]
                    .running,
                destination != "missing"
            );
        }
    }
}

#[test]
fn late_registered_controller_events_fire_once_when_threshold_is_already_reached() {
    let (mut world, assets, entity) = setup();
    let request = world.entity_mut(entity).take::<AnimationToPlay>().unwrap();
    let identity = request
        .network_lifetime
        .as_ref()
        .map(std::sync::Arc::downgrade);
    world.entity_mut(entity).insert((
        Transform::default(),
        CurrentAnimation {
            rewind: None,
            identity,
            clip: "take".into(),
            controller_owner: None,
            network_lifetime: request.network_lifetime,
            playback_graphs: vec![],
            looped: false,
            speed: 1.,
            elapsed_seconds: 0.75,
            duration_seconds: 1.,
        },
    ));
    world.insert_resource(assets);
    world.init_resource::<SceneMaxVars>();
    world.init_resource::<SceneMaxStartupProgram>();
    world.init_resource::<DelayedActionQueue>();
    world.insert_resource(Time::<()>::default());
    world
        .run_system_once(update_animation_runtime_controllers)
        .unwrap();
    for (animation, percent) in [("take", 25.), ("take", 90.), ("other", 10.)] {
        register_animation_controller_event(
            &AnimationControllerEventStatement {
                controller: "control".into(),
                animation: animation.into(),
                percent: AssignmentValue::Number(percent),
                actions: scenemax_parser::parse_program("marker=1")
                    .unwrap()
                    .statements,
            },
            &SceneMaxVars::default(),
            None,
            &HashMap::new(),
            None,
            None,
            &mut world.resource_mut::<SceneMaxRuntimeAssets>(),
        );
    }
    for (progress, count) in [(0.75, 1), (0.75, 1), (0.95, 2), (0.1, 2), (0.95, 2)] {
        world
            .get_mut::<CurrentAnimation>(entity)
            .unwrap()
            .elapsed_seconds = progress;
        world
            .run_system_once(update_animation_runtime_controllers)
            .unwrap();
        assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), count);
    }
}

#[test]
fn local_controller_stop_preserves_unowned_children_and_replacement_graphs() {
    let (mut world, mut assets, root) = setup();
    let request = world.entity_mut(root).take::<AnimationToPlay>().unwrap();
    let mut graphs = Assets::<AnimationGraph>::default();
    let (graph, node) = AnimationGraph::from_clip(Handle::default());
    let original = graphs.add(graph.clone());
    let newer = graphs.add(graph);
    let mut spawn_player = |handle: Handle<AnimationGraph>| {
        let mut player = AnimationPlayer::default();
        player.play(node).repeat();
        world
            .spawn((
                player,
                AnimationGraphHandle(handle),
                AnimationTransitions::new(),
            ))
            .id()
    };
    let owned = spawn_player(original.clone());
    let replaced = spawn_player(newer);
    let unowned = spawn_player(original.clone());
    // An attached child shares ancestry but not the controller's playback.
    world.entity_mut(unowned).insert(ChildOf(root));
    // The recorded owned player need not still be a descendant at cancellation.
    world.entity_mut(root).insert(CurrentAnimation {
        rewind: None,
        identity: request
            .network_lifetime
            .as_ref()
            .map(std::sync::Arc::downgrade),
        clip: "take".into(),
        controller_owner: None,
        network_lifetime: request.network_lifetime,
        playback_graphs: vec![(owned, original.clone()), (replaced, original)],
        looped: false,
        speed: 1.,
        elapsed_seconds: 1.,
        duration_seconds: 4.,
    });
    stop(&mut world, &mut assets);
    world.insert_resource(assets);
    world
        .run_system_once(apply_pending_animation_controller_stops)
        .unwrap();
    assert!(world.get::<CurrentAnimation>(root).is_none());
    assert!(
        world
            .get::<AnimationPlayer>(owned)
            .unwrap()
            .playing_animations()
            .next()
            .is_none()
    );
    for player in [replaced, unowned] {
        assert!(
            world
                .get::<AnimationPlayer>(player)
                .unwrap()
                .playing_animations()
                .next()
                .is_some()
        );
    }
}
