//! Local controller lifetimes are independent of script continuation ownership.
use super::*;
use std::sync::Weak;

#[derive(Component)]
pub(crate) struct EffectAction {
    pub(crate) generation: u64,
    pub(crate) owner: Option<SceneMaxControllerKey>,
    pub(crate) lifetime: Option<ActionLease>,
}

pub(super) fn prune_effects(world: &mut World) {
    let finished: Vec<_> = world
        .query::<(Entity, &EffectAction, Option<&SceneMaxEffekseerPlayback>)>()
        .iter(world)
        .filter(|(_, action, playback)| {
            playback.is_none_or(|playback| playback.play_generation != action.generation)
        })
        .map(|(entity, _, _)| entity)
        .collect();
    for entity in finished {
        world.entity_mut(entity).remove::<EffectAction>();
    }
}

pub(super) fn effect_duration(
    world: &World,
    entity: Entity,
    lifetime: Option<&Weak<()>>,
) -> Option<u32> {
    let action = world.get::<EffectAction>(entity)?;
    if !lifetime?.ptr_eq(&std::sync::Arc::downgrade(action.lifetime.as_ref()?)) {
        return None;
    }
    let playback = world.get::<SceneMaxEffekseerPlayback>(entity)?;
    if playback.play_generation != action.generation {
        return None;
    }
    if playback.looped {
        return Some(u32::MAX);
    }
    let effect = world.get::<SceneMaxEffekseerEffect>(entity)?;
    if !effect.one_shot_duration_seconds.is_finite() {
        return Some(u32::MAX);
    }
    let remaining = (effect.one_shot_duration_seconds.max(0.1) - playback.elapsed_seconds).max(0.)
        / playback.playback_speed.max(0.001);
    if !remaining.is_finite() || remaining <= 0. {
        return None;
    }
    Some(
        (f64::from(remaining) * 1000.)
            .ceil()
            .clamp(1., f64::from(u32::MAX - 1)) as u32,
    )
}

#[cfg(test)]
pub(super) fn animation_duration(
    current: Option<&CurrentAnimation>,
    lifetime: Option<&Weak<()>>,
) -> Option<u32> {
    animation_duration_at_speed(current, lifetime, None)
}

pub(super) fn animation_duration_at_speed(
    current: Option<&CurrentAnimation>,
    lifetime: Option<&Weak<()>>,
    speed: Option<f32>,
) -> Option<u32> {
    let current = current?;
    let speed = speed.unwrap_or(current.speed);
    let lease = current.network_lifetime.as_ref()?;
    if !lifetime?.ptr_eq(&std::sync::Arc::downgrade(lease)) {
        return None;
    }
    if current.looped {
        // Reserve the existing long-lived timer as a phase clock. Subtract in
        // integer milliseconds; f32 cannot represent millisecond steps here.
        let phase = current
            .elapsed_seconds
            .rem_euclid(current.duration_seconds.max(0.001));
        let age_ms = (f64::from(phase) / f64::from(speed.max(0.001)) * 1000.).round() as u32;
        return Some(u32::MAX.saturating_sub(age_ms).max(1));
    }
    let seconds = current
        .rewind
        .as_ref()
        .map(|r| r.remaining(current.duration_seconds, speed))
        .unwrap_or_else(|| {
            (current.duration_seconds - current.elapsed_seconds).max(0.) / speed.max(0.001)
        });
    if !seconds.is_finite() || seconds <= 0. {
        return None;
    }
    Some(
        (f64::from(seconds) * 1000.)
            .ceil()
            .clamp(1., f64::from(u32::MAX - 1)) as u32,
    )
}

#[derive(Default)]
pub(super) struct LocalActions {
    // Only the current action for each registered entity/slot can need an end.
    pub(super) animations: HashMap<u32, (String, String, Weak<()>, f32)>,
    running: HashMap<(u32, u8), (u16, Weak<()>)>,
}

impl LocalActions {
    pub(super) fn started(
        &mut self,
        request: u32,
        slot: u8,
        sequence: u16,
        lifetime: Option<Weak<()>>,
    ) {
        self.running.remove(&(request, slot));
        if let Some(lifetime) = lifetime {
            self.running.insert((request, slot), (sequence, lifetime));
        }
    }

    fn finished(&self) -> Vec<(u32, u8, u16)> {
        self.running
            .iter()
            .filter_map(|(&(request, slot), (sequence, lifetime))| {
                (lifetime.strong_count() == 0).then_some((request, slot, *sequence))
            })
            .collect()
    }

    pub(super) fn flush(&mut self, client: &mut Client) {
        for (request, slot, sequence) in self.finished() {
            match client.send(
                Some(request),
                Outgoing::ActionEnd {
                    entity: 0,
                    slot,
                    sequence,
                },
            ) {
                Ok(()) => {
                    self.running.remove(&(request, slot));
                    tracing::debug!(request, slot, sequence, "ended local multiplayer action");
                }
                // Keep the end pending if the bounded transport queue is full.
                Err(error) => tracing::warn!(%error, "multiplayer action end rejected"),
            }
        }
    }

    pub(super) fn remove(&mut self, request: u32) {
        self.running.retain(|(id, _), _| *id != request);
        self.animations.remove(&request);
    }

    pub(super) fn clear(&mut self) {
        self.running.clear();
        self.animations.clear();
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn runtime_bounded_effects_publish_persistent_lifetimes_until_completion() {
        use super::*;
        let mut world = World::new();
        let lease = std::sync::Arc::new(());
        let weak = std::sync::Arc::downgrade(&lease);
        let entity = world
            .spawn((
                SceneMaxEffekseerEffect {
                    instance_id: 1,
                    asset_id: "fixture".into(),
                    effect_path: None,
                    one_shot_duration_seconds: f32::INFINITY,
                },
                SceneMaxEffekseerPlayback {
                    looped: false,
                    play_generation: 1,
                    playback_speed: 2.,
                    elapsed_seconds: 20.,
                    dynamic_inputs: [2., 0., 0., 0.],
                },
                EffectAction {
                    generation: 1,
                    owner: None,
                    lifetime: Some(lease),
                },
            ))
            .id();
        assert_eq!(effect_duration(&world, entity, Some(&weak)), Some(u32::MAX));
        world
            .entity_mut(entity)
            .remove::<SceneMaxEffekseerPlayback>();
        prune_effects(&mut world);
        assert_eq!(effect_duration(&world, entity, Some(&weak)), None);
        assert!(weak.upgrade().is_none());
    }

    use super::*;

    #[test]
    fn effects_finish_emit_lifetime_end_and_cancel_only_the_matching_generation() {
        use bevy::ecs::system::RunSystemOnce;
        let mut world = World::new();
        let lease = ActionLease::new(());
        let weak = std::sync::Arc::downgrade(&lease);
        let entity = world
            .spawn((
                SceneMaxEffekseerEffect {
                    instance_id: 1,
                    asset_id: "fixture".into(),
                    effect_path: None,
                    one_shot_duration_seconds: 4.,
                },
                SceneMaxEffekseerPlayback {
                    looped: false,
                    play_generation: 1,
                    playback_speed: 2.,
                    dynamic_inputs: [0.; 4],
                    elapsed_seconds: 0.,
                },
                EffectAction {
                    generation: 1,
                    owner: None,
                    lifetime: Some(lease),
                },
            ))
            .id();
        assert_eq!(effect_duration(&world, entity, Some(&weak)), Some(2000));
        let mut time = Time::<()>::default();
        time.advance_by(Duration::from_secs(2));
        world.insert_resource(time);
        world.run_system_once(update_effekseer_playbacks).unwrap();
        prune_effects(&mut world);
        assert_eq!(weak.strong_count(), 0);
        let mut replay = replay::ReplayHistory::default();
        replay.start(10, 4, 1);
        world.entity_mut(entity).insert((
            SceneMaxEffekseerPlayback {
                looped: true,
                play_generation: 2,
                playback_speed: 1.,
                dynamic_inputs: [0.; 4],
                elapsed_seconds: 0.,
            },
            EffectAction {
                generation: 1,
                owner: Some(SceneMaxControllerKey::NetworkAction {
                    entity: 10,
                    slot: 4,
                    sequence: 1,
                }),
                lifetime: None,
            },
        ));
        replay.end(10, 4, 1);
        controllers::prune(&mut world, &replay);
        assert_eq!(
            world
                .get::<SceneMaxEffekseerPlayback>(entity)
                .unwrap()
                .play_generation,
            2
        );
        world.entity_mut(entity).insert(EffectAction {
            generation: 2,
            owner: Some(SceneMaxControllerKey::NetworkAction {
                entity: 10,
                slot: 4,
                sequence: 2,
            }),
            lifetime: None,
        });
        replay.start(10, 4, 2);
        replay.end(10, 4, 1);
        controllers::prune(&mut world, &replay);
        assert!(world.get::<SceneMaxEffekseerPlayback>(entity).is_some());
        replay.end(10, 4, 2);
        controllers::prune(&mut world, &replay);
        assert!(world.get::<SceneMaxEffekseerPlayback>(entity).is_none());
    }

    #[test]
    fn effect_resume_is_owner_and_generation_bound_accounts_for_wait_and_expires() {
        use bevy::ecs::system::RunSystemOnce;
        let mut world = World::new();
        world.insert_resource(Time::<()>::default());
        let owner = SceneMaxControllerKey::NetworkAction {
            entity: 10,
            slot: 4,
            sequence: 2,
        };
        let entity = world
            .spawn((
                SceneMaxEffekseerEffect {
                    instance_id: 1,
                    asset_id: "fixture".into(),
                    effect_path: None,
                    one_shot_duration_seconds: 8.,
                },
                SceneMaxEffekseerPlayback {
                    looped: false,
                    play_generation: 2,
                    playback_speed: 2.,
                    dynamic_inputs: [0.; 4],
                    elapsed_seconds: 0.,
                },
                EffectAction {
                    generation: 1,
                    owner: Some(owner.clone()),
                    lifetime: None,
                },
                NetworkEffectResume(NetworkAnimationResume {
                    owner: owner.clone(),
                    remaining_seconds: 3.,
                    received_at: Instant::now() - Duration::from_secs(1),
                }),
            ))
            .id();
        world.run_system_once(update_effekseer_playbacks).unwrap();
        assert_eq!(
            world
                .get::<SceneMaxEffekseerPlayback>(entity)
                .unwrap()
                .elapsed_seconds,
            0.
        );
        world.get_mut::<EffectAction>(entity).unwrap().generation = 2;
        world.get_mut::<EffectAction>(entity).unwrap().owner = None;
        world.run_system_once(update_effekseer_playbacks).unwrap();
        assert_eq!(
            world
                .get::<SceneMaxEffekseerPlayback>(entity)
                .unwrap()
                .elapsed_seconds,
            0.
        );
        world.get_mut::<EffectAction>(entity).unwrap().owner = Some(owner.clone());
        world.run_system_once(update_effekseer_playbacks).unwrap();
        let elapsed = world
            .get::<SceneMaxEffekseerPlayback>(entity)
            .unwrap()
            .elapsed_seconds;
        assert!((4. ..4.5).contains(&elapsed), "{elapsed}");
        assert!(world.get::<NetworkEffectResume>(entity).is_none());
        world.run_system_once(update_effekseer_playbacks).unwrap();
        assert_eq!(
            world
                .get::<SceneMaxEffekseerPlayback>(entity)
                .unwrap()
                .elapsed_seconds,
            elapsed
        );
        world
            .entity_mut(entity)
            .insert(NetworkEffectResume(NetworkAnimationResume {
                owner,
                remaining_seconds: 0.5,
                received_at: Instant::now() - Duration::from_secs(1),
            }));
        world.run_system_once(update_effekseer_playbacks).unwrap();
        assert!(world.get::<SceneMaxEffekseerPlayback>(entity).is_none());
        assert!(world.get::<NetworkEffectResume>(entity).is_none());
    }

    #[test]
    fn native_effect_completion_releases_only_matching_finite_playback() {
        use bevy::ecs::system::RunSystemOnce;
        let mut world = World::new();
        world.insert_resource(Time::<()>::default());
        let feedback = scenemax_effects::PlaybackCompletions::default();
        feedback.0.lock().unwrap().insert((7, 1));
        world.insert_resource(feedback.clone());
        let lease = ActionLease::new(());
        let weak = std::sync::Arc::downgrade(&lease);
        let entity = world
            .spawn((
                SceneMaxEffekseerEffect {
                    instance_id: 7,
                    asset_id: "fixture".into(),
                    effect_path: None,
                    one_shot_duration_seconds: 60.,
                },
                SceneMaxEffekseerPlayback {
                    looped: false,
                    play_generation: 2,
                    playback_speed: 1.,
                    dynamic_inputs: [0.; 4],
                    elapsed_seconds: 0.,
                },
                EffectAction {
                    generation: 2,
                    owner: None,
                    lifetime: Some(lease),
                },
            ))
            .id();
        world.run_system_once(update_effekseer_playbacks).unwrap();
        assert!(world.get::<SceneMaxEffekseerPlayback>(entity).is_some());
        feedback.0.lock().unwrap().insert((7, 2));
        world
            .get_mut::<SceneMaxEffekseerPlayback>(entity)
            .unwrap()
            .looped = true;
        world.run_system_once(update_effekseer_playbacks).unwrap();
        assert!(world.get::<SceneMaxEffekseerPlayback>(entity).is_some());
        world
            .get_mut::<SceneMaxEffekseerPlayback>(entity)
            .unwrap()
            .looped = false;
        world.run_system_once(update_effekseer_playbacks).unwrap();
        assert!(world.get::<SceneMaxEffekseerPlayback>(entity).is_none());
        prune_effects(&mut world);
        assert_eq!(weak.strong_count(), 0);
        world.run_system_once(update_effekseer_playbacks).unwrap();
    }

    #[test]
    fn finite_animation_duration_waits_for_matching_playback_and_uses_speed_and_remaining_time() {
        let lease = ActionLease::new(());
        let lifetime = std::sync::Arc::downgrade(&lease);
        let mut current = CurrentAnimation {
            rewind: None,
            identity: None,
            controller_owner: None,
            network_lifetime: Some(lease),
            playback_graphs: Vec::new(),
            clip: "fixture".into(),
            looped: false,
            speed: 2.,
            elapsed_seconds: 1.,
            duration_seconds: 5.,
        };
        assert_eq!(animation_duration(None, Some(&lifetime)), None);
        assert_eq!(animation_duration(Some(&current), Some(&Weak::new())), None);
        assert_eq!(
            animation_duration(Some(&current), Some(&lifetime)),
            Some(2000)
        );
        current.looped = true;
        assert_eq!(
            animation_duration(Some(&current), Some(&lifetime)),
            Some(u32::MAX - 500)
        );
        current.looped = false;
        current.elapsed_seconds = 5.;
        assert_eq!(animation_duration(Some(&current), Some(&lifetime)), None);
    }

    #[test]
    fn animation_resume_accounts_for_asset_wait_speed_and_expiry() {
        let now = Instant::now();
        let resume = NetworkAnimationResume {
            owner: SceneMaxControllerKey::NetworkAction {
                entity: 10,
                slot: 3,
                sequence: 1,
            },
            remaining_seconds: 2.,
            received_at: now,
        };
        assert_eq!(resume.seek_seconds(5., 2., now), 1.);
        assert_eq!(
            resume.seek_seconds(5., 2., now + Duration::from_millis(500)),
            2.
        );
        assert_eq!(
            resume.seek_seconds(5., 2., now + Duration::from_secs(3)),
            5.
        );
        assert_eq!(resume.seek_seconds(1., 1., now), 0.);
    }

    #[test]
    fn end_waits_for_all_controller_parts_and_superseded_actions_cannot_end_newer_work() {
        let mut actions = LocalActions::default();
        let movement = ActionLease::new(());
        let motor = movement.clone();
        actions.started(1, 1, u16::MAX, Some(std::sync::Arc::downgrade(&movement)));
        drop(movement);
        assert!(actions.finished().is_empty());
        drop(motor);
        assert_eq!(actions.finished(), [(1, 1, u16::MAX)]);
        let newer = ActionLease::new(());
        actions.started(1, 1, 0, Some(std::sync::Arc::downgrade(&newer)));
        assert!(actions.finished().is_empty());
        drop(newer);
        assert_eq!(actions.finished(), [(1, 1, 0)]);
        actions.remove(1);
        assert!(actions.finished().is_empty());
    }

    #[test]
    fn untracked_replacements_destruction_and_session_reset_drop_old_lifetimes() {
        let mut actions = LocalActions::default();
        for (request, slot) in [(1, 1), (1, 2), (2, 1)] {
            actions.started(request, slot, 1, Some(Weak::new()));
        }
        actions.started(1, 1, 2, None);
        actions.remove(2);
        assert_eq!(actions.finished(), [(1, 2, 1)]);
        actions.clear();
        assert!(actions.finished().is_empty());
    }

    #[test]
    fn actual_motion_systems_release_lifetimes_on_completion_and_component_removal() {
        let mut world = World::new();
        let mut time = Time::<()>::default();
        time.advance_by(Duration::from_secs(1));
        world.insert_resource(time);
        world.insert_resource(SceneMaxStartupProgram(None, None));
        world.init_resource::<SceneMaxVars>();
        world.init_resource::<SceneMaxColliderBounds>();
        let mut actions = LocalActions::default();
        let move_lease = ActionLease::new(());
        let turn_lease = ActionLease::new(());
        actions.started(1, 1, 1, Some(std::sync::Arc::downgrade(&move_lease)));
        actions.started(1, 2, 2, Some(std::sync::Arc::downgrade(&turn_lease)));
        let entity = world
            .spawn((
                Transform::default(),
                TimedMoves {
                    moves: vec![TimedMove {
                        character_motion: false,
                        curve: Default::default(),
                        network_lifetime: Some(move_lease),
                        controller_owner: None,
                        remaining_seconds: 2.,
                        duration_seconds: 2.,
                        velocity: Vec3::X,
                        final_translation: None,
                        loop_condition: None,
                    }],
                },
                TimedTurns {
                    turns: vec![TimedTurn {
                        curve: Default::default(),
                        target_degrees: None,
                        axis: None,
                        network_lifetime: Some(turn_lease),
                        controller_owner: None,
                        remaining_seconds: 3.,
                        duration_seconds: 3.,
                        radians_per_second: 1.,
                        loop_condition: None,
                    }],
                },
            ))
            .id();
        let mut schedule = Schedule::default();
        schedule.add_systems((update_timed_moves, update_timed_turns).chain());
        schedule.run(&mut world);
        assert!(actions.finished().is_empty());
        schedule.run(&mut world);
        assert_eq!(actions.finished(), [(1, 1, 1)]);
        assert_eq!(
            world.get::<Transform>(entity).unwrap().translation,
            Vec3::X * 2.
        );
        assert!(world.get::<TimedTurns>(entity).is_some());
        world.entity_mut(entity).remove::<TimedTurns>();
        assert_eq!(actions.finished().len(), 2);
    }

    #[test]
    fn failed_end_enqueue_is_retained_for_retry() {
        let server = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        let mut client = Client::connect(
            server.local_addr().unwrap(),
            Login {
                password_hash: password_hash(""),
                project_guid: "fixture".into(),
                create_session: false,
                session_id: 1000,
                session_name: "fixture".into(),
                scene: "main".into(),
                player: "fixture".into(),
            },
        )
        .unwrap();
        let request = client
            .register(Spawn {
                archetype: "box".into(),
                player: "fixture".into(),
                source_name: "fixture".into(),
                command: "{network_entity} => box".into(),
            })
            .unwrap();
        let mut actions = LocalActions::default();
        actions.started(request, 1, 4, Some(Weak::new()));
        client.close();
        actions.flush(&mut client);
        assert_eq!(actions.finished(), [(request, 1, 4)]);
        actions.clear();
        assert!(actions.finished().is_empty());
    }
}

pub(super) fn animation_command(a: &scenemax_parser::AnimationStatement, speed: f32) -> String {
    format!(
        "{{network_entity}}.{}{} at speed of {speed}{}{}",
        if a.clip.chars().any(|c| !c.is_alphanumeric() && c != '_') {
            serde_json::Value::String(a.clip.clone()).to_string()
        } else {
            a.clip.clone()
        },
        scenemax_parser::animation_range::selector_suffix(
            a.frame_range,
            a.frame_range_name.as_deref()
        ),
        if a.looped { " loop" } else { "" },
        animation_rewind::wire(&a.rewind)
    )
}

pub(super) fn with_speed(command: &str, speed: f32, clear_rewind: bool) -> Option<String> {
    let mut p = scenemax_parser::parse_program(&super::replication::references(
        command,
        "{network_entity}",
        |_| Some("network_target".into()),
    )?)
    .ok()?;
    let Statement::Animate(a) = p.statements.first_mut()? else {
        return None;
    };
    if clear_rewind {
        a.rewind = None;
    }
    Some(animation_command(a, speed))
}

pub(super) fn effective_speed(world: &World, entity: Entity) -> Option<f32> {
    let current = world.get::<CurrentAnimation>(entity)?;
    Some(
        if current.rewind.is_some() {
            current.speed
        } else {
            world
                .get::<AnimationSpeedOverride>(entity)
                .map_or(current.speed, |v| v.speed)
        }
        .max(0.001),
    )
}

pub(super) fn refresh_animation_speeds(world: &World, net: &mut Multiplayer) {
    net.local_actions
        .animations
        .retain(|_, (name, command, weak, old_speed)| {
            let Some((entity, _)) = net.locals.get(name) else {
                return false;
            };
            let Some(current) = world.get::<CurrentAnimation>(*entity) else {
                return false;
            };
            if current
                .network_lifetime
                .as_ref()
                .is_none_or(|lease| !weak.ptr_eq(&std::sync::Arc::downgrade(lease)))
            {
                return false;
            }
            let speed = effective_speed(world, *entity).unwrap_or(current.speed);
            if current.rewind.is_none() && speed != *old_speed && net.pending.len() < 4096 {
                if let Some(command) = with_speed(command, speed, true) {
                    net.pending
                        .push((name.clone(), command, Some(weak.clone())));
                }
            }
            true
        });
}

#[cfg(test)]
mod phase_tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;

    #[test]
    fn loop_clock_preserves_milliseconds_across_cycles_and_asset_wait() {
        let now = Instant::now();
        for (phase, speed) in [(0.123, 0.5), (1.499, 2.), (0., 1.)] {
            let mut world = World::new();
            let lease = ActionLease::new(());
            let weak = std::sync::Arc::downgrade(&lease);
            let entity = world
                .spawn(CurrentAnimation {
                    rewind: None,
                    identity: None,
                    controller_owner: None,
                    network_lifetime: Some(lease),
                    playback_graphs: vec![],
                    clip: "take".into(),
                    looped: true,
                    speed,
                    elapsed_seconds: 6. + phase,
                    duration_seconds: 1.5,
                })
                .id();
            let duration =
                animation_duration(world.get::<CurrentAnimation>(entity), Some(&weak)).unwrap();
            let resume = NetworkAnimationResume {
                owner: SceneMaxControllerKey::NetworkAction {
                    entity: 1,
                    slot: 3,
                    sequence: 1,
                },
                remaining_seconds: f64::from(duration - 7234) / 1000.,
                received_at: now,
            };
            let seek = resume.loop_seek_seconds(1.5, speed, now + Duration::from_millis(321));
            let expected = (phase + 7.555 * speed).rem_euclid(1.5);
            assert!((seek - expected).abs() < 0.0011, "{seek} {expected}");
        }
    }

    #[test]
    fn persistent_and_temporary_speed_update_player_clock_and_replication() {
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        let mut time = Time::<()>::default();
        time.advance_by(Duration::from_millis(100));
        world.insert_resource(time);
        let lease = ActionLease::new(());
        let weak = std::sync::Arc::downgrade(&lease);
        let entity = world
            .spawn((
                SceneMaxEntity {
                    name: "subject".into(),
                    runtime_name: "subject".into(),
                },
                CurrentAnimation {
                    rewind: None,
                    identity: Some(weak.clone()),
                    controller_owner: None,
                    network_lifetime: Some(lease),
                    playback_graphs: vec![],
                    clip: "take".into(),
                    looped: false,
                    speed: 1.,
                    elapsed_seconds: 1.,
                    duration_seconds: 5.,
                },
                animation_speed_override_resolved(2., None),
            ))
            .id();
        let node = bevy::animation::graph::AnimationNodeIndex::new(1);
        let mut player = AnimationPlayer::default();
        player.play(node);
        let child = world
            .spawn((
                player,
                AnimationGraphHandle(Handle::default()),
                ChildOf(entity),
            ))
            .id();
        world
            .get_mut::<CurrentAnimation>(entity)
            .unwrap()
            .playback_graphs
            .push((child, Handle::default()));
        world
            .run_system_once(apply_animation_speed_overrides)
            .unwrap();
        assert_eq!(world.get::<CurrentAnimation>(entity).unwrap().speed, 2.);
        assert_eq!(
            world
                .get::<AnimationPlayer>(child)
                .unwrap()
                .animation(node)
                .unwrap()
                .speed(),
            2.
        );
        assert!(world.get::<AnimationSpeedOverride>(entity).is_none());
        let mut net = Multiplayer::default();
        net.locals.insert("subject".into(), (entity, 1));
        net.local_actions.animations.insert(
            1,
            (
                "subject".into(),
                "{network_entity}.take at speed of 1".into(),
                weak.clone(),
                1.,
            ),
        );
        refresh_animation_speeds(&world, &mut net);
        assert_eq!(net.pending.len(), 1);
        assert!(net.pending[0].1.contains("speed of 2"));
        assert_eq!(
            animation_duration_at_speed(
                world.get::<CurrentAnimation>(entity),
                Some(&weak),
                effective_speed(&world, entity)
            ),
            Some(2000)
        );
        world
            .entity_mut(entity)
            .insert(animation_speed_override_resolved(0.5, Some(0.2)));
        world
            .run_system_once(apply_animation_speed_overrides)
            .unwrap();
        assert_eq!(effective_speed(&world, entity), Some(0.5));
        world
            .run_system_once(apply_animation_speed_overrides)
            .unwrap();
        assert_eq!(effective_speed(&world, entity), Some(2.));
        assert_eq!(
            world
                .get::<AnimationPlayer>(child)
                .unwrap()
                .animation(node)
                .unwrap()
                .speed(),
            2.
        );
        world
            .get_mut::<CurrentAnimation>(entity)
            .unwrap()
            .network_lifetime = Some(ActionLease::new(()));
        net.pending.clear();
        refresh_animation_speeds(&world, &mut net);
        assert!(net.pending.is_empty());
        assert!(net.local_actions.animations.is_empty());
    }
}
