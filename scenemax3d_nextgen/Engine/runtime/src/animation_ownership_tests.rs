use super::*;
use bevy::ecs::system::RunSystemOnce;

fn fixture() -> (
    World,
    Entity,
    [Entity; 3],
    bevy::animation::graph::AnimationNodeIndex,
) {
    let mut world = World::new();
    world.init_resource::<SceneMaxVars>();
    let mut time = Time::<()>::default();
    time.advance_by(std::time::Duration::from_millis(100));
    world.insert_resource(time);
    let mut graphs = Assets::<AnimationGraph>::default();
    let (graph, node) = AnimationGraph::from_clip(Handle::default());
    let original = graphs.add(graph.clone());
    let replacement = graphs.add(graph);
    let mut spawn = |handle| {
        let mut player = AnimationPlayer::default();
        player.play(node).repeat().set_speed(1.).seek_to(0.7);
        world.spawn((player, AnimationGraphHandle(handle))).id()
    };
    let owned = spawn(original.clone());
    let replaced = spawn(replacement);
    let independent = spawn(original.clone());
    let root = world
        .spawn((
            SceneMaxEntity {
                name: "subject".into(),
                runtime_name: "subject".into(),
            },
            CurrentAnimation {
                rewind: None,
                identity: None,
                controller_owner: None,
                network_lifetime: None,
                playback_graphs: vec![(owned, original.clone()), (replaced, original)],
                clip: "take".into(),
                looped: false,
                speed: 1.,
                elapsed_seconds: 1.5,
                duration_seconds: 2.,
            },
        ))
        .id();
    world.entity_mut(independent).insert(ChildOf(root));
    (world, root, [owned, replaced, independent], node)
}

#[test]
fn rewind_only_seeks_and_pauses_the_recorded_graph() {
    let (mut world, root, players, node) = fixture();
    world.get_mut::<CurrentAnimation>(root).unwrap().rewind = Some(animation_rewind::Playback {
        spec: scenemax_parser::animation_rewind::Playback {
            start: 1.5,
            target: 0.5,
            duration: 1.,
            easing: None,
        },
        elapsed: 0.,
        curve: motion_ease::Curve::literal(&None),
        _waiter: None,
    });
    for (delta, expected_time, expected_speed) in [(0.1, 1.4, 0.), (0.9, 0.5, 1.)] {
        world
            .resource_mut::<Time>()
            .advance_by(std::time::Duration::from_secs_f32(delta));
        world
            .run_system_once(update_current_animation_vars)
            .unwrap();
        let owned = world
            .get::<AnimationPlayer>(players[0])
            .unwrap()
            .animation(node)
            .unwrap();
        assert!((owned.seek_time() - expected_time).abs() < 0.0001);
        assert_eq!(owned.speed(), expected_speed);
        for entity in &players[1..] {
            let active = world
                .get::<AnimationPlayer>(*entity)
                .unwrap()
                .animation(node)
                .unwrap();
            assert_eq!(active.seek_time(), 0.7);
            assert_eq!(active.speed(), 1.);
        }
    }
}

#[test]
fn temporary_speed_and_restoration_preserve_independent_and_replaced_players() {
    let (mut world, root, players, node) = fixture();
    world
        .entity_mut(root)
        .insert(animation_speed_override_resolved(2., Some(0.2)));
    for expected in [2., 1.] {
        world
            .run_system_once(apply_animation_speed_overrides)
            .unwrap();
        assert_eq!(
            world
                .get::<AnimationPlayer>(players[0])
                .unwrap()
                .animation(node)
                .unwrap()
                .speed(),
            expected
        );
        for entity in &players[1..] {
            assert_eq!(
                world
                    .get::<AnimationPlayer>(*entity)
                    .unwrap()
                    .animation(node)
                    .unwrap()
                    .speed(),
                1.
            );
        }
    }
    assert!(world.get::<AnimationSpeedOverride>(root).is_none());
    world
        .entity_mut(root)
        .insert(animation_speed_override_resolved(3., None));
    world
        .run_system_once(apply_animation_speed_overrides)
        .unwrap();
    assert_eq!(world.get::<CurrentAnimation>(root).unwrap().speed, 3.);
    assert_eq!(
        world
            .get::<AnimationPlayer>(players[0])
            .unwrap()
            .animation(node)
            .unwrap()
            .speed(),
        3.
    );
    for entity in &players[1..] {
        assert_eq!(
            world
                .get::<AnimationPlayer>(*entity)
                .unwrap()
                .animation(node)
                .unwrap()
                .speed(),
            1.
        );
    }
}

#[test]
fn permanent_speed_is_retained_by_a_pending_animation_without_changing_its_identity() {
    let mut world = World::new();
    world.insert_resource(Time::<()>::default());
    let lease = multiplayer::ActionLease::new(());
    let weak = std::sync::Arc::downgrade(&lease);
    let root = world
        .spawn((
            SceneMaxEntity {
                name: "subject".into(),
                runtime_name: "subject".into(),
            },
            AnimationToPlay {
                rewind: None,
                frame_range: None,
                frame_range_name: None,
                controller_owner: None,
                network_lifetime: Some(lease),
                clip: "take".into(),
                runtime_clip: "take".into(),
                looped: false,
                speed: 0.5,
                gltf: Handle::default(),
                animation_names: vec![],
                target_model_resource: None,
                baked_external: None,
                bake_request: None,
                external_retarget: default(),
                external_source: false,
                tried_external_source: false,
                visual_transform_preapplied: false,
                retarget_wait_logged: false,
            },
            animation_speed_override_resolved(2., None),
        ))
        .id();
    world
        .run_system_once(apply_animation_speed_overrides)
        .unwrap();
    let request = world.get::<AnimationToPlay>(root).unwrap();
    assert_eq!(request.speed, 2.);
    assert!(weak.ptr_eq(&std::sync::Arc::downgrade(
        request.network_lifetime.as_ref().unwrap()
    )));
    assert!(world.get::<AnimationSpeedOverride>(root).is_none());
    world
        .run_system_once(apply_animation_speed_overrides)
        .unwrap();
    assert_eq!(world.get::<AnimationToPlay>(root).unwrap().speed, 2.);
}
