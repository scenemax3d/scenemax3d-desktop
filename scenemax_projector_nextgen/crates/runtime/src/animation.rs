use super::*;

pub(super) fn blocking_timed_action_seconds(action: &Statement) -> Option<f32> {
    match action {
        Statement::Turn(turn)
            if !turn.async_run
                && turn.loop_condition.is_none()
                && turn.duration_seconds > f32::EPSILON =>
        {
            Some(turn.duration_seconds.max(0.001))
        }
        Statement::Move(movement)
            if !movement.async_run
                && movement.loop_condition.is_none()
                && movement.duration_seconds > f32::EPSILON =>
        {
            Some(movement.duration_seconds.max(0.001))
        }
        Statement::MoveTo(move_to)
            if !move_to.async_run && move_to.duration_seconds > f32::EPSILON =>
        {
            Some(move_to.duration_seconds.max(0.001))
        }
        Statement::CharacterJump(jump) if !jump.async_run => {
            Some(jump_duration_seconds(jump.speed))
        }
        Statement::CinematicPlay(play) if !play.async_run => Some(play.duration_seconds.max(0.1)),
        _ => None,
    }
}

pub(super) fn estimated_animation_seconds(
    animation: &AnimationStatement,
    animation_durations: &SceneMaxAnimationDurations,
) -> f32 {
    if let Some(duration) = animation_durations.lookup(&animation.target, &animation.clip) {
        return (duration / animation.speed.max(0.1)).clamp(0.08, 3.5);
    }
    if is_jump_animation_clip(&animation.clip) {
        return (jump_duration_seconds(35.0) / animation.speed.max(0.1)).clamp(0.65, 1.4);
    }
    estimated_animation_seconds_from_speed(animation.speed)
}

pub(super) fn is_jump_animation_clip(clip: &str) -> bool {
    let lower = clip.to_ascii_lowercase();
    lower.contains("jump") || lower.contains("fly_kick") || lower.contains("flying_kick")
}

pub(super) fn estimated_animation_seconds_from_speed(speed: f32) -> f32 {
    (DEFAULT_ANIMATION_CLIP_SECONDS / speed.max(0.1)).clamp(0.25, 2.0)
}

pub(super) fn animation_clip_duration_seconds(
    animation_clips: &Assets<AnimationClip>,
    clip: &Handle<AnimationClip>,
) -> f32 {
    animation_clips
        .get(clip)
        .map(AnimationClip::duration)
        .filter(|duration| *duration > 0.0)
        .unwrap_or(DEFAULT_ANIMATION_CLIP_SECONDS)
}

pub(super) fn queue_builtin_player_animation(
    commands: &mut Commands,
    entity: Entity,
    gltf: Option<&SceneMaxGltf>,
    current_animation: Option<&CurrentAnimation>,
    clip: &str,
    looped: bool,
) {
    if current_animation.is_some_and(|current| current_animation_matches(current, clip, looped)) {
        return;
    }
    let Some(gltf) = gltf else {
        return;
    };
    commands.entity(entity).insert(AnimationToPlay {
        clip: clip.to_owned(),
        looped,
        speed: 1.0,
        gltf: gltf.gltf.clone(),
    });
}

pub(super) fn current_animation_matches(
    current: &CurrentAnimation,
    requested_clip: &str,
    looped: bool,
) -> bool {
    current.looped == looped
        && animation_name_matches(&current.clip, &normalized_animation_name(requested_clip))
}

pub(super) fn pending_key_switch<'a>(
    program: &'a Program,
    keyboard: &ButtonInput<KeyCode>,
) -> Option<&'a str> {
    let mut saw_pressed_key = false;
    for statement in &program.statements {
        match statement {
            Statement::WaitForKey { key } if is_pressed_key(key, keyboard) => {
                saw_pressed_key = true;
            }
            Statement::SwitchTo { scene } if saw_pressed_key => return Some(scene),
            _ => {}
        }
    }
    None
}

pub(super) fn is_pressed_key(key: &str, keyboard: &ButtonInput<KeyCode>) -> bool {
    let Some(key_code) = key_code_from_scenemax(key) else {
        return false;
    };
    keyboard.just_pressed(key_code)
}

pub(super) fn key_event_matches(
    key: &str,
    trigger: KeyTrigger,
    keyboard: &ButtonInput<KeyCode>,
) -> bool {
    let Some(key_code) = key_code_from_scenemax(key) else {
        return false;
    };
    match trigger {
        KeyTrigger::Pressed => keyboard.pressed(key_code),
        KeyTrigger::PressedOnce => keyboard.just_pressed(key_code),
        KeyTrigger::Released => keyboard.just_released(key_code),
    }
}

pub(super) fn restore_default_idle_animations(
    mut commands: Commands,
    vars: Res<SceneMaxVars>,
    characters: Query<(
        Entity,
        &SceneMaxEntity,
        Option<&SceneMaxGltf>,
        Option<&CurrentAnimation>,
        Option<&AnimationToPlay>,
    )>,
) {
    if !scene_is_ready_for_player_idle(&vars) {
        return;
    }
    for (entity, scene_entity, gltf, current_animation, pending_animation) in &characters {
        if scene_entity.name != "player1" || pending_animation.is_some() {
            continue;
        }
        if current_animation.is_some_and(|current| {
            current_animation_matches(current, "idle2", true)
                || (!current.looped && current.elapsed_seconds < current.duration_seconds)
        }) {
            continue;
        }
        let Some(gltf) = gltf else {
            continue;
        };
        commands.entity(entity).insert(AnimationToPlay {
            clip: "idle2".to_owned(),
            looped: true,
            speed: 1.0,
            gltf: gltf.gltf.clone(),
        });
    }
}

pub(super) fn scene_is_ready_for_player_idle(vars: &SceneMaxVars) -> bool {
    variable_is_zero(vars, "action")
        && variable_is_zero(vars, "move_forward")
        && variable_is_zero(vars, "player_hit")
        && variable_is_zero(vars, "player1_ko")
        && variable_is_zero(vars, "player1.data.is_jumping")
}

pub(super) fn variable_is_zero(vars: &SceneMaxVars, name: &str) -> bool {
    scenemax_runtime_vm_core::variable_is_zero(vars, name)
}

pub(super) fn play_pending_animations(
    mut commands: Commands,
    children: Query<&Children>,
    root_entities: Query<&SceneMaxEntity>,
    animations_to_play: Query<(Entity, &AnimationToPlay)>,
    gltfs: Res<Assets<Gltf>>,
    mut animation_clips: ResMut<Assets<AnimationClip>>,
    mut animation_durations: ResMut<SceneMaxAnimationDurations>,
    mut graphs: ResMut<Assets<AnimationGraph>>,
    animation_targets: Query<(&AnimationTargetId, Option<&Name>)>,
    mut players: Query<(
        &mut AnimationPlayer,
        Option<&mut AnimationGraphHandle>,
        Option<&Name>,
    )>,
) {
    for (root, animation_to_play) in &animations_to_play {
        let target_name = root_entities
            .get(root)
            .map(|entity| entity.name.as_str())
            .unwrap_or("<unknown>");
        let Some(gltf) = gltfs.get(&animation_to_play.gltf) else {
            continue;
        };

        let Some((resolved_clip_name, clip)) =
            find_named_animation_clip(gltf.named_animations.iter(), &animation_to_play.clip)
        else {
            write_runtime_log_line(
                LoggerLevel::Info,
                &format!(
                    "ANIM:MISS target={} requested={} available={}",
                    target_name,
                    animation_to_play.clip,
                    animation_names_summary(gltf)
                ),
            );
            if gltf.animations.is_empty() {
                tracing::warn!(clip = %animation_to_play.clip, "GLTF model has no animation clips");
            } else {
                tracing::warn!(
                    clip = %animation_to_play.clip,
                    available = gltf.named_animations.len(),
                    "GLTF animation clip was not found; skipping fallback"
                );
            }
            commands.entity(root).remove::<AnimationToPlay>();
            continue;
        };

        let mut animation_players = Vec::new();
        for child in children.iter_descendants(root) {
            if players.get(child).is_ok() {
                animation_players.push(child);
            }
        }

        if animation_players.is_empty() {
            tracing::debug!(
                target = target_name,
                clip = %animation_to_play.clip,
                "waiting for GLTF AnimationPlayer entity"
            );
            continue;
        }

        let retargeted_clip = animation_clips.get(clip).and_then(|source_clip| {
            retarget_clip_to_visible_animation_player(
                source_clip,
                resolved_clip_name,
                &animation_players,
                &children,
                &animation_targets,
                &players,
            )
        });
        let (clip_to_play, playback_players, retargeted_curve_count) =
            if let Some((retargeted_clip, destination_player, curve_count)) = retargeted_clip {
                let handle = animation_clips.add(retargeted_clip);
                (handle, vec![destination_player], curve_count)
            } else {
                (clip.clone(), animation_players.clone(), 0)
            };

        let (graph, index) = AnimationGraph::from_clip(clip_to_play.clone());
        let graph_handle = graphs.add(graph);
        let duration_seconds = animation_clip_duration_seconds(&animation_clips, &clip_to_play);
        animation_durations.insert(
            target_name,
            &animation_to_play.clip,
            resolved_clip_name,
            duration_seconds,
        );

        let animation_player_count = playback_players.len();
        let mut animation_player_names = Vec::new();
        for child in playback_players {
            if let Ok((mut player, graph_component, name)) = players.get_mut(child) {
                animation_player_names.push(
                    name.map(|name| name.as_str().to_owned())
                        .unwrap_or_else(|| format!("{child:?}")),
                );
                if let Some(mut graph_component) = graph_component {
                    graph_component.0 = graph_handle.clone();
                } else {
                    commands
                        .entity(child)
                        .insert(AnimationGraphHandle(graph_handle.clone()));
                }
                let active = player
                    .stop_all()
                    .start(index)
                    .set_speed(animation_to_play.speed)
                    .set_weight(1.0);
                if animation_to_play.looped {
                    active.repeat();
                }
            }
        }

        write_runtime_log_line(
            LoggerLevel::Info,
            &format!(
                "ANIM:PLAY target={} requested={} resolved={} looped={} speed={} duration={} players={}",
                target_name,
                animation_to_play.clip,
                resolved_clip_name,
                animation_to_play.looped as u8,
                format_scenemax_number(animation_to_play.speed),
                format_scenemax_number(duration_seconds),
                animation_player_count,
            ),
        );
        write_runtime_log_line(
            LoggerLevel::Info,
            &format!(
                "ANIM:PLAYERS target={} names={} retargeted_curves={}",
                target_name,
                animation_player_names.join("|"),
                retargeted_curve_count
            ),
        );
        commands.entity(root).remove::<AnimationToPlay>();
        commands.entity(root).insert(CurrentAnimation {
            clip: resolved_clip_name.to_owned(),
            looped: animation_to_play.looped,
            speed: animation_to_play.speed.max(0.001),
            elapsed_seconds: 0.0,
            duration_seconds,
        });
    }
}

pub(super) fn retarget_clip_to_visible_animation_player(
    source_clip: &AnimationClip,
    resolved_clip_name: &str,
    animation_players: &[Entity],
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    players: &Query<(
        &mut AnimationPlayer,
        Option<&mut AnimationGraphHandle>,
        Option<&Name>,
    )>,
) -> Option<(AnimationClip, Entity, usize)> {
    let destination_player = animation_players.first().copied()?;
    let source_player = animation_players.iter().copied().find(|entity| {
        players
            .get(*entity)
            .ok()
            .and_then(|(_, _, name)| name)
            .is_some_and(|name| {
                animation_name_matches(
                    name.as_str(),
                    &normalized_animation_name(resolved_clip_name),
                )
            })
    })?;
    if source_player == destination_player {
        return None;
    }

    let source_targets =
        collect_animation_targets_by_name(source_player, children, animation_targets);
    let destination_targets =
        collect_animation_targets_by_name(destination_player, children, animation_targets);
    if source_targets.is_empty() || destination_targets.is_empty() {
        return None;
    }

    let mut source_to_destination = HashMap::new();
    for (bone_name, source_target) in source_targets {
        if let Some(destination_target) = destination_targets.get(&bone_name).copied() {
            source_to_destination.insert(source_target, destination_target);
        }
    }
    if source_to_destination.is_empty() {
        return None;
    }

    let mut retargeted = AnimationClip::default();
    retargeted.set_duration(source_clip.duration());
    let mut curve_count = 0usize;
    for (source_target, curves) in source_clip.curves() {
        let Some(destination_target) = source_to_destination.get(source_target).copied() else {
            continue;
        };
        for curve in curves {
            retargeted.add_variable_curve_to_target(destination_target, curve.clone());
            curve_count += 1;
        }
    }

    (curve_count > 0).then_some((retargeted, destination_player, curve_count))
}

pub(super) fn collect_animation_targets_by_name(
    root: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
) -> HashMap<String, AnimationTargetId> {
    let mut targets = HashMap::new();
    collect_animation_targets_by_name_recursive(root, children, animation_targets, &mut targets);
    targets
}

pub(super) fn collect_animation_targets_by_name_recursive(
    entity: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    targets: &mut HashMap<String, AnimationTargetId>,
) {
    if let Ok((target_id, Some(name))) = animation_targets.get(entity) {
        targets.insert(normalized_animation_name(name.as_str()), *target_id);
    }
    if let Ok(child_list) = children.get(entity) {
        for child in child_list {
            collect_animation_targets_by_name_recursive(
                *child,
                children,
                animation_targets,
                targets,
            );
        }
    }
}

pub(super) fn animation_names_summary(gltf: &Gltf) -> String {
    let mut names = gltf
        .named_animations
        .keys()
        .take(16)
        .map(|name| name.as_ref())
        .collect::<Vec<_>>()
        .join(",");
    if gltf.named_animations.len() > 16 {
        names.push_str(",...");
    }
    names
}

pub(super) fn find_named_animation_clip<'a>(
    named_animations: impl IntoIterator<Item = (&'a Box<str>, &'a Handle<AnimationClip>)>,
    requested: &str,
) -> Option<(&'a str, &'a Handle<AnimationClip>)> {
    let requested_key = normalized_animation_name(requested);
    let mut case_match = None;
    let mut normalized_match = None;
    for (name, clip) in named_animations {
        let name = name.as_ref();
        if name == requested {
            case_match = preferred_animation_match(case_match, (name, clip), &requested_key);
            continue;
        }
        if name.eq_ignore_ascii_case(requested) {
            case_match = preferred_animation_match(case_match, (name, clip), &requested_key);
        }
        if animation_name_matches(name, &requested_key) {
            normalized_match =
                preferred_animation_match(normalized_match, (name, clip), &requested_key);
        }
    }
    case_match.or(normalized_match)
}

pub(super) fn preferred_animation_match<'a>(
    current: Option<(&'a str, &'a Handle<AnimationClip>)>,
    candidate: (&'a str, &'a Handle<AnimationClip>),
    requested_key: &str,
) -> Option<(&'a str, &'a Handle<AnimationClip>)> {
    let Some(current) = current else {
        return Some(candidate);
    };
    (animation_candidate_score(candidate.0, requested_key)
        >= animation_candidate_score(current.0, requested_key))
    .then_some(candidate)
    .or(Some(current))
}

pub(super) fn update_current_animation_vars(
    time: Res<Time>,
    mut vars: ResMut<SceneMaxVars>,
    mut animations: Query<(&SceneMaxEntity, &mut CurrentAnimation)>,
) {
    let delta = time.delta_secs();
    for (entity, mut animation) in &mut animations {
        let duration = animation.duration_seconds.max(0.001);
        animation.elapsed_seconds += delta * animation.speed.max(0.001);
        let elapsed = if animation.looped {
            animation.elapsed_seconds % duration
        } else {
            animation.elapsed_seconds.min(duration)
        };
        let percent = animation_percent_from_elapsed(elapsed, duration);
        vars.0
            .insert(format!("{}.anim_percent", entity.name), percent);
        vars.0.insert(
            format!("{}.anim_finished", entity.name),
            (!animation.looped && animation.elapsed_seconds >= duration) as u8 as f32,
        );
    }
}

pub(super) fn current_animation_percent(animation: &CurrentAnimation) -> f32 {
    let duration = animation.duration_seconds.max(0.001);
    let elapsed = if animation.looped {
        animation.elapsed_seconds % duration
    } else {
        animation.elapsed_seconds.min(duration)
    };
    animation_percent_from_elapsed(elapsed, duration)
}

pub(super) fn animation_percent_from_elapsed(elapsed: f32, duration: f32) -> f32 {
    ((elapsed / duration.max(0.001)) * 100.0).clamp(0.0, 100.0)
}

pub(super) fn animation_speed_override_resolved(
    speed: f32,
    duration_seconds: Option<f32>,
) -> AnimationSpeedOverride {
    AnimationSpeedOverride {
        speed: speed.max(0.001),
        remaining_seconds: duration_seconds,
        applied: false,
    }
}

pub(super) fn apply_animation_speed_overrides(
    time: Res<Time>,
    mut commands: Commands,
    children: Query<&Children>,
    mut roots: Query<(Entity, &mut AnimationSpeedOverride), With<SceneMaxEntity>>,
    mut players: Query<&mut AnimationPlayer>,
) {
    for (root, mut speed_override) in &mut roots {
        let player_entities = children.iter_descendants(root).collect::<Vec<_>>();
        if !speed_override.applied {
            for player_entity in &player_entities {
                if let Ok(mut player) = players.get_mut(*player_entity) {
                    set_active_animation_speeds(&mut player, speed_override.speed);
                }
            }
            speed_override.applied = true;
        }

        let Some(remaining_seconds) = speed_override.remaining_seconds.as_mut() else {
            commands.entity(root).remove::<AnimationSpeedOverride>();
            continue;
        };
        *remaining_seconds -= time.delta_secs();
        if *remaining_seconds <= 0.0 {
            for player_entity in &player_entities {
                if let Ok(mut player) = players.get_mut(*player_entity) {
                    set_active_animation_speeds(&mut player, 1.0);
                }
            }
            commands.entity(root).remove::<AnimationSpeedOverride>();
        }
    }
}

pub(super) fn set_active_animation_speeds(player: &mut AnimationPlayer, speed: f32) {
    for (_, active_animation) in player.playing_animations_mut() {
        active_animation.set_speed(speed);
    }
}
