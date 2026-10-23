//! Cancel only controllers owned by invalidated numbered remote actions.
use super::*;

pub(super) fn prune(world: &mut World, replay: &replay::ReplayHistory) {
    let cancelled = |owner: Option<&SceneMaxControllerKey>| match owner {
        Some(SceneMaxControllerKey::NetworkAction {
            entity,
            slot,
            sequence,
        }) => !replay.current(*entity, *slot, *sequence),
        _ => false,
    };
    crate::attachments::prune(world, cancelled);
    if let Some(mut queue) = world.get_resource_mut::<DelayedActionQueue>() {
        queue
            .actions
            .retain(|action| !cancelled(action.owner.as_ref()));
    }
    if let Some(mut active) = world.get_resource_mut::<ActiveActionControllers>() {
        active.running.retain(|owner| !cancelled(Some(owner)));
    }
    for mut motor in world.query::<&mut SceneMaxCharacterMotor>().iter_mut(world) {
        if cancelled(motor.timed_motion_owner.as_ref()) {
            motor.timed_motion = Vec3::ZERO;
            motor.timed_motion_remaining_seconds = 0.;
            motor.timed_motion_owner = None;
            motor.network_lifetime = None;
        }
    }
    let mut empty = Vec::new();
    for (entity, mut movements) in world.query::<(Entity, &mut TimedMoves)>().iter_mut(world) {
        let previous = movements.moves.len();
        movements
            .moves
            .retain(|movement| !cancelled(movement.controller_owner.as_ref()));
        if movements.moves.len() != previous {
            tracing::debug!(?entity, "cancelled numbered remote movement");
            if movements.moves.is_empty() {
                empty.push(entity);
            }
        }
    }
    for entity in empty {
        world.entity_mut(entity).remove::<TimedMoves>();
    }
    let mut empty = Vec::new();
    for (entity, mut turns) in world.query::<(Entity, &mut TimedTurns)>().iter_mut(world) {
        let previous = turns.turns.len();
        turns
            .turns
            .retain(|turn| !cancelled(turn.controller_owner.as_ref()));
        if turns.turns.len() != previous {
            tracing::debug!(?entity, "cancelled numbered remote turn");
        }
        if turns.turns.is_empty() {
            empty.push(entity);
        }
    }
    for entity in empty {
        world.entity_mut(entity).remove::<TimedTurns>();
    }

    let pending: Vec<_> = world
        .query::<(Entity, &AnimationToPlay)>()
        .iter(world)
        .filter(|(_, request)| cancelled(request.controller_owner.as_ref()))
        .map(|(entity, _)| entity)
        .collect();
    for entity in pending {
        world.entity_mut(entity).remove::<AnimationToPlay>();
    }
    let effects: Vec<_> = world
        .query::<(Entity, &EffectAction)>()
        .iter(world)
        .filter(|(_, action)| cancelled(action.owner.as_ref()))
        .map(|(entity, action)| (entity, action.generation))
        .collect();
    for (entity, generation) in effects {
        if world
            .get::<SceneMaxEffekseerPlayback>(entity)
            .is_some_and(|playback| playback.play_generation == generation)
        {
            world
                .entity_mut(entity)
                .remove::<SceneMaxEffekseerPlayback>();
            tracing::debug!(?entity, "cancelled numbered remote effect");
        }
        world.entity_mut(entity).remove::<EffectAction>();
    }
    let effect_resumes: Vec<_> = world
        .query::<(Entity, &NetworkEffectResume)>()
        .iter(world)
        .filter(|(_, resume)| cancelled(Some(&resume.0.owner)))
        .map(|(entity, _)| entity)
        .collect();
    for entity in effect_resumes {
        world.entity_mut(entity).remove::<NetworkEffectResume>();
    }
    let resumes: Vec<_> = world
        .query::<(Entity, &NetworkAnimationResume)>()
        .iter(world)
        .filter(|(_, resume)| cancelled(Some(&resume.owner)))
        .map(|(entity, _)| entity)
        .collect();
    for entity in resumes {
        world.entity_mut(entity).remove::<NetworkAnimationResume>();
    }
    let animations: Vec<_> = world
        .query::<(Entity, &CurrentAnimation)>()
        .iter(world)
        .filter(|(_, current)| cancelled(current.controller_owner.as_ref()))
        .map(|(entity, current)| (entity, current.playback_graphs.clone()))
        .collect();
    for (root, graphs) in animations {
        for (entity, graph) in graphs {
            // A newer local request or another playback path may have replaced
            // the graph before the network end arrived. Never stop that graph.
            if world
                .get::<AnimationGraphHandle>(entity)
                .is_some_and(|current| current.0 == graph)
            {
                if let Some(mut player) = world.get_mut::<AnimationPlayer>(entity) {
                    player.stop_all();
                }
                if let Some(mut transitions) = world.get_mut::<AnimationTransitions>(entity) {
                    *transitions = AnimationTransitions::new();
                }
            }
        }
        world.entity_mut(root).remove::<CurrentAnimation>();
        tracing::debug!(?root, "cancelled numbered remote animation");
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn owner(entity: u32, slot: u8, sequence: u16) -> SceneMaxControllerKey {
        SceneMaxControllerKey::NetworkAction {
            entity,
            slot,
            sequence,
        }
    }
    #[test]
    fn rotation_cancellation_preserves_other_numbered_and_local_turns() {
        let mut world = World::new();
        let p = scenemax_parser::parse_program("subject.rotate(x+90) in 2 seconds").unwrap();
        let Statement::Turn(statement) = &p.statements[0] else {
            panic!();
        };
        let mut turns = Vec::new();
        for key in [Some(owner(10, 2, 1)), Some(owner(10, 2, 2)), None] {
            let mut turn = timed_turn_from_statement(statement);
            turn.controller_owner = key;
            turns.push(turn);
        }
        let entity = world.spawn(TimedTurns { turns }).id();
        let mut replay = replay::ReplayHistory::default();
        replay.start(10, 2, 1);
        replay.start(10, 2, 2);
        replay.end(10, 2, 1);
        prune(&mut world, &replay);
        assert_eq!(world.get::<TimedTurns>(entity).unwrap().turns.len(), 2);
        assert_eq!(
            world.get::<TimedTurns>(entity).unwrap().turns[0].controller_owner,
            Some(owner(10, 2, 2))
        );
        replay.end(10, 2, 2);
        prune(&mut world, &replay);
        assert_eq!(world.get::<TimedTurns>(entity).unwrap().turns.len(), 1);
        assert!(
            world.get::<TimedTurns>(entity).unwrap().turns[0]
                .controller_owner
                .is_none()
        );
    }

    #[test]
    fn end_during_asset_loading_removes_only_matching_animation_request() {
        let mut world = World::new();
        let request = |sequence| AnimationToPlay {
            rewind: None,
            frame_range: None,
            frame_range_name: None,
            controller_owner: Some(owner(10, 3, sequence)),
            network_lifetime: None,
            clip: "fixture".into(),
            runtime_clip: "fixture".into(),
            looped: true,
            speed: 1.,
            gltf: Handle::default(),
            animation_names: Vec::new(),
            target_model_resource: None,
            baked_external: None,
            bake_request: None,
            external_retarget: default(),
            external_source: false,
            tried_external_source: false,
            visual_transform_preapplied: false,
            retarget_wait_logged: false,
        };
        let old = world.spawn(request(u16::MAX)).id();
        let newer = world.spawn(request(0)).id();
        for (entity, sequence) in [(old, u16::MAX), (newer, 0)] {
            world.entity_mut(entity).insert(NetworkAnimationResume {
                owner: owner(10, 3, sequence),
                remaining_seconds: 1.,
                received_at: Instant::now(),
            });
        }
        let mut local_request = request(0);
        local_request.controller_owner = None;
        let local = world.spawn(local_request).id();
        let mut replay = replay::ReplayHistory::default();
        replay.start(10, 3, u16::MAX);
        replay.start(10, 3, 0);
        replay.end(10, 3, u16::MAX);
        prune(&mut world, &replay);
        assert!(world.get::<AnimationToPlay>(old).is_none());
        assert!(world.get::<AnimationToPlay>(newer).is_some());
        assert!(world.get::<NetworkAnimationResume>(old).is_none());
        assert!(world.get::<NetworkAnimationResume>(newer).is_some());
        assert!(world.get::<AnimationToPlay>(local).is_some());
        replay.end(10, 3, 0);
        prune(&mut world, &replay);
        assert!(world.get::<AnimationToPlay>(newer).is_none());
        assert!(world.get::<NetworkAnimationResume>(newer).is_none());
        assert!(world.get::<AnimationToPlay>(local).is_some());
    }
    #[test]
    fn animation_ends_stop_only_owned_graphs_and_preserve_newer_or_local_playback() {
        let mut world = World::new();
        let mut assets = Assets::<AnimationGraph>::default();
        let (graph, node) = AnimationGraph::from_clip(Handle::default());
        let original = assets.add(graph.clone());
        let newer = assets.add(graph);
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
        let matching = spawn_player(original.clone());
        let replaced = spawn_player(newer);
        let local = spawn_player(original.clone());
        let mut replay = replay::ReplayHistory::default();
        replay.start(10, 3, 2);
        let root = world
            .spawn(CurrentAnimation {
                rewind: None,
                identity: None,
                controller_owner: Some(owner(10, 3, 2)),
                network_lifetime: None,
                playback_graphs: vec![(matching, original.clone()), (replaced, original.clone())],
                clip: "fixture".into(),
                looped: true,
                speed: 1.,
                elapsed_seconds: 0.,
                duration_seconds: 1.,
            })
            .id();
        let local_root = world
            .spawn(CurrentAnimation {
                rewind: None,
                identity: None,
                controller_owner: None,
                network_lifetime: None,
                playback_graphs: vec![(local, original)],
                clip: "fixture".into(),
                looped: true,
                speed: 1.,
                elapsed_seconds: 0.,
                duration_seconds: 1.,
            })
            .id();
        replay.end(10, 3, 1);
        prune(&mut world, &replay);
        assert!(
            world
                .get::<AnimationPlayer>(matching)
                .unwrap()
                .playing_animations()
                .next()
                .is_some()
        );
        replay.end(10, 3, 2);
        prune(&mut world, &replay);
        assert!(world.get::<CurrentAnimation>(root).is_none());
        assert!(
            world
                .get::<AnimationPlayer>(matching)
                .unwrap()
                .playing_animations()
                .next()
                .is_none()
        );
        assert!(
            world
                .get::<AnimationPlayer>(replaced)
                .unwrap()
                .playing_animations()
                .next()
                .is_some()
        );
        assert!(
            world
                .get::<AnimationPlayer>(local)
                .unwrap()
                .playing_animations()
                .next()
                .is_some()
        );
        assert!(world.get::<CurrentAnimation>(local_root).is_some());
    }
    fn movement(owner: Option<SceneMaxControllerKey>) -> TimedMove {
        TimedMove {
            character_motion: false,
            curve: Default::default(),
            network_lifetime: None,
            controller_owner: owner,
            remaining_seconds: 4.,
            duration_seconds: 4.,
            velocity: Vec3::X,
            final_translation: Some(Vec3::splat(99.)),
            loop_condition: None,
        }
    }
    #[test]
    fn ends_and_supersession_cancel_only_owned_motion_and_preserve_local_input() {
        let mut world = World::new();
        let mut replay = replay::ReplayHistory::default();
        replay.start(10, 1, 1);
        replay.start(10, 1, 2);
        let entity = world
            .spawn((
                Transform::from_xyz(3., 4., 5.),
                TimedMoves {
                    moves: vec![
                        movement(None),
                        movement(Some(owner(10, 1, 1))),
                        movement(Some(owner(10, 1, 2))),
                    ],
                },
                SceneMaxCharacterMotor {
                    timed_motion_owner: Some(owner(10, 1, 2)),
                    timed_motion: Vec3::X,
                    timed_motion_remaining_seconds: 4.,
                    desired_motion: Vec3::Z,
                    motion_ttl_seconds: 1.,
                    ..default()
                },
            ))
            .id();
        prune(&mut world, &replay);
        assert_eq!(world.get::<TimedMoves>(entity).unwrap().moves.len(), 2);
        replay.end(10, 1, 1);
        prune(&mut world, &replay);
        assert_eq!(world.get::<TimedMoves>(entity).unwrap().moves.len(), 2);
        replay.end(10, 1, 2);
        prune(&mut world, &replay);
        assert_eq!(world.get::<TimedMoves>(entity).unwrap().moves.len(), 1);
        assert_eq!(
            world.get::<Transform>(entity).unwrap().translation,
            Vec3::new(3., 4., 5.)
        );
        let motor = world.get::<SceneMaxCharacterMotor>(entity).unwrap();
        assert_eq!(motor.timed_motion, Vec3::ZERO);
        assert_eq!(motor.timed_motion_remaining_seconds, 0.);
        assert_eq!(motor.desired_motion, Vec3::Z);
        assert_eq!(motor.motion_ttl_seconds, 1.);
    }
    #[test]
    fn numbered_dispatch_owns_continuations_and_ends_remove_pending_and_turns() {
        let mut world = World::new();
        world.init_resource::<DelayedActionQueue>();
        let mut net = Multiplayer::default();
        net.remotes.insert(10, "scenemax_remote_10".into());
        let entity = world
            .spawn(TimedTurns {
                turns: vec![TimedTurn {
                    curve: Default::default(),
                    target_degrees: None,
                    axis: None,
                    network_lifetime: None,
                    controller_owner: Some(owner(10, 2, 0)),
                    remaining_seconds: 2.,
                    duration_seconds: 2.,
                    radians_per_second: 1.,
                    loop_condition: None,
                }],
            })
            .id();
        let action = action_record("{network_entity}.turn right 90 in 2 seconds", 0).unwrap();
        queue_action(&mut net, 10, action, Some((2000, 2000)));
        let now = net.remote_pending[0].received_at;
        dispatch_remote(
            &mut world,
            &mut net,
            &HashMap::from([("scenemax_remote_10".into(), (entity, Transform::default()))]),
            now,
        );
        assert_eq!(
            world.resource::<DelayedActionQueue>().actions[0].owner,
            Some(owner(10, 2, 0))
        );
        net.replay.end(10, 2, u16::MAX);
        prune(&mut world, &net.replay);
        assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 1);
        assert!(world.get::<TimedTurns>(entity).is_some());
        net.replay.end(10, 2, 0);
        prune(&mut world, &net.replay);
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        assert!(world.get::<TimedTurns>(entity).is_none());
    }
    #[test]
    fn destroyed_and_cleared_history_remove_remote_but_not_local_controllers() {
        let mut world = World::new();
        let mut replay = replay::ReplayHistory::default();
        replay.start(10, 1, 1);
        let remote = world
            .spawn(TimedMoves {
                moves: vec![movement(Some(owner(10, 1, 1)))],
            })
            .id();
        let local = world
            .spawn(TimedMoves {
                moves: vec![movement(Some(SceneMaxControllerKey::Key(4)))],
            })
            .id();
        replay.destroy(10);
        prune(&mut world, &replay);
        assert!(world.get::<TimedMoves>(remote).is_none());
        assert!(world.get::<TimedMoves>(local).is_some());
        replay.start(11, 1, 1);
        world.entity_mut(remote).insert(TimedMoves {
            moves: vec![movement(Some(owner(11, 1, 1)))],
        });
        prune(&mut world, &replay::ReplayHistory::default());
        assert!(world.get::<TimedMoves>(remote).is_none());
        assert!(world.get::<TimedMoves>(local).is_some());
    }
}
