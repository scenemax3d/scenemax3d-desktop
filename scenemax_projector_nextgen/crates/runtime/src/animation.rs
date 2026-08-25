use super::*;
use bevy::animation::{
    RepeatAnimation, VariableCurve, animated_field,
    animation_curves::{AnimatableCurve, AnimatableKeyframeCurve, AnimatableProperty},
    graph::{AnimationNodeIndex, AnimationNodeType},
    transition::AnimationTransitions,
};
use bevy::camera::primitives::{Aabb, MeshAabb};
use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;
use std::time::Duration;

const DEFAULT_ANIMATION_TRANSITION: Duration = Duration::from_millis(250);
const MAX_PRESERVED_TRANSITION_NODES: usize = 32;
const VISUAL_COMPENSATION_CACHE_EPSILON: f32 = 0.0001;

#[cfg(test)]
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
        Statement::CameraMove(camera_move)
            if !camera_move.async_run && camera_move.duration_seconds > f32::EPSILON =>
        {
            Some(camera_move.duration_seconds.max(0.001))
        }
        Statement::CharacterJump(jump) if !jump.async_run => {
            Some(jump_duration_seconds(jump.speed))
        }
        Statement::SpritePlay(sprite_play)
            if !sprite_play.looped && sprite_play.duration_seconds > f32::EPSILON =>
        {
            Some(sprite_play.duration_seconds.max(0.001))
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

pub(super) fn play_pending_animations(
    mut commands: Commands,
    children: Query<&Children>,
    root_entities: Query<&SceneMaxEntity>,
    mut animations_to_play: Query<(Entity, &mut AnimationToPlay)>,
    asset_server: Res<AssetServer>,
    gltfs: Res<Assets<Gltf>>,
    gltf_nodes: Res<Assets<GltfNode>>,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    mut animation_clips: ResMut<Assets<AnimationClip>>,
    mut animation_durations: ResMut<SceneMaxAnimationDurations>,
    mut graphs: ResMut<Assets<AnimationGraph>>,
    animation_targets: Query<(&AnimationTargetId, Option<&Name>)>,
    mut transform_queries: ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: Res<Assets<Mesh>>,
    mut visual_queries: ParamSet<(
        Query<&mut SceneMaxAnimationVisualTransform>,
        Query<&SceneMaxAnimationVisualCompensationCache>,
    )>,
    mut players: Query<(
        &mut AnimationPlayer,
        Option<&mut AnimationGraphHandle>,
        Option<&mut AnimationTransitions>,
        Option<&Name>,
    )>,
) {
    for (root, mut animation_to_play) in &mut animations_to_play {
        let target_name = root_entities
            .get(root)
            .map(|entity| entity.name.as_str())
            .unwrap_or("<unknown>");
        let target_model_resource = animation_to_play.target_model_resource.clone();
        let Some(gltf) = gltfs.get(&animation_to_play.gltf) else {
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

        let baked_visual_rotation = animation_to_play
            .baked_external
            .as_ref()
            .is_some_and(|baked| baked.visual_rotation_baked);
        let visual_rotation_degrees = if animation_to_play.external_source && !baked_visual_rotation
        {
            animation_to_play.external_retarget.visual_rotation_degrees
        } else {
            [0.0, 0.0, 0.0]
        };
        let visual_translation = if animation_to_play.external_source {
            Vec3::from_array(animation_to_play.external_retarget.visual_translation)
        } else {
            Vec3::ZERO
        };
        apply_animation_visual_transform(
            root,
            visual_translation,
            visual_rotation_degrees,
            &children,
            &mut transform_queries,
            &meshes,
            &mut visual_queries,
            &mut commands,
        );
        if visual_transform_preroll_needed(
            &mut animation_to_play,
            visual_translation,
            visual_rotation_degrees,
        ) {
            if runtime_verbose_logging() {
                write_runtime_log_line(
                    LoggerLevel::Debug,
                    &format!(
                        "ANIM_VISUAL_PREROLL target={} clip={} visual_translation=({},{},{}) visual_rotation=({},{},{})",
                        target_name,
                        animation_to_play.runtime_clip,
                        format_scenemax_number(visual_translation.x),
                        format_scenemax_number(visual_translation.y),
                        format_scenemax_number(visual_translation.z),
                        format_scenemax_number(visual_rotation_degrees[0]),
                        format_scenemax_number(visual_rotation_degrees[1]),
                        format_scenemax_number(visual_rotation_degrees[2]),
                    ),
                );
            }
            continue;
        }
        let transition_duration = animation_transition_duration(
            &animation_to_play,
            visual_translation,
            visual_rotation_degrees,
        );

        if let Some(baked) = animation_to_play.baked_external.clone() {
            let Some(destination_player) = animation_players.first().copied() else {
                continue;
            };
            let destination_targets = collect_animation_targets_by_name(
                destination_player,
                &children,
                &animation_targets,
            );
            match load_baked_external_animation_clip(
                runtime_assets.asset_root.as_deref(),
                &baked,
                &destination_targets,
            ) {
                Ok(baked_clip) => {
                    let handle = animation_clips.add(baked_clip);
                    let duration_seconds =
                        animation_clip_duration_seconds(&animation_clips, &handle);
                    animation_durations.insert(
                        target_name,
                        &animation_to_play.runtime_clip,
                        &baked.clip,
                        duration_seconds,
                    );
                    play_animation_clip_on_players(
                        &mut commands,
                        handle,
                        vec![destination_player],
                        animation_to_play.speed,
                        animation_to_play.looped,
                        transition_duration,
                        &mut graphs,
                        &mut players,
                    );
                    commands.entity(root).remove::<AnimationToPlay>();
                    commands.entity(root).insert(CurrentAnimation {
                        clip: animation_to_play.runtime_clip.clone(),
                        looped: animation_to_play.looped,
                        speed: animation_to_play.speed.max(0.001),
                        elapsed_seconds: 0.0,
                        duration_seconds,
                    });
                    if runtime_verbose_logging() {
                        write_runtime_log_line(
                            LoggerLevel::Debug,
                            &format!(
                                "ANIM_BAKED_PLAY target={} clip={} model={} path={}",
                                target_name,
                                animation_to_play.runtime_clip,
                                baked.model,
                                baked.path,
                            ),
                        );
                    }
                    continue;
                }
                Err(error) => {
                    write_runtime_diagnostic_line(format!(
                        "ANIM_BAKED_MISS target={} clip={} model={} reason={}",
                        target_name, animation_to_play.runtime_clip, baked.model, error
                    ));
                    animation_to_play.baked_external = None;
                }
            }
        }

        let Some((resolved_clip_name, clip)) =
            find_named_animation_clip(gltf.named_animations.iter(), &animation_to_play.clip)
        else {
            if switch_to_external_animation_source(
                target_name,
                target_model_resource.as_deref(),
                &mut animation_to_play,
                &asset_server,
                &mut runtime_assets,
            ) {
                continue;
            }
            if gltf.animations.is_empty() {
                tracing::warn!(clip = %animation_to_play.runtime_clip, "GLTF model has no animation clips");
            } else {
                tracing::warn!(
                    clip = %animation_to_play.runtime_clip,
                    available = gltf.named_animations.len(),
                    "GLTF animation clip was not found; skipping fallback"
                );
            }
            commands.entity(root).remove::<AnimationToPlay>();
            continue;
        };

        let retargeted_clip = animation_clips.get(clip).and_then(|source_clip| {
            retarget_clip_to_visible_animation_player(
                source_clip,
                gltf,
                resolved_clip_name,
                animation_to_play.external_source,
                &animation_to_play.external_retarget,
                &animation_players,
                &children,
                &animation_targets,
                &transform_queries.p1(),
                &gltf_nodes,
                &players,
            )
        });
        if animation_to_play.external_source && retargeted_clip.is_none() {
            if !animation_to_play.retarget_wait_logged && runtime_verbose_logging() {
                write_runtime_log_line(
                    LoggerLevel::Debug,
                    &format!(
                        "ANIM_RETARGET_WAIT target={} clip={} source_clip={} reason=retarget_unavailable",
                        target_name, animation_to_play.runtime_clip, resolved_clip_name,
                    ),
                );
                animation_to_play.retarget_wait_logged = true;
            }
            continue;
        }
        let (clip_to_play, playback_players, _retargeted_curve_count) =
            if let Some((retargeted_clip, destination_player, curve_count)) = retargeted_clip {
                if let Some(bake_request) = animation_to_play.bake_request.as_ref() {
                    let _ = write_baked_animation_clip(
                        &retargeted_clip,
                        destination_player,
                        &children,
                        &animation_targets,
                        &transform_queries.p1(),
                        bake_request,
                        &animation_to_play.external_retarget,
                    );
                }
                let handle = animation_clips.add(retargeted_clip);
                (handle, vec![destination_player], curve_count)
            } else {
                (clip.clone(), animation_players.clone(), 0)
            };

        let duration_seconds = animation_clip_duration_seconds(&animation_clips, &clip_to_play);
        animation_durations.insert(
            target_name,
            &animation_to_play.runtime_clip,
            resolved_clip_name,
            duration_seconds,
        );

        play_animation_clip_on_players(
            &mut commands,
            clip_to_play.clone(),
            playback_players,
            animation_to_play.speed,
            animation_to_play.looped,
            transition_duration,
            &mut graphs,
            &mut players,
        );

        commands.entity(root).remove::<AnimationToPlay>();
        commands.entity(root).insert(CurrentAnimation {
            clip: animation_to_play.runtime_clip.clone(),
            looped: animation_to_play.looped,
            speed: animation_to_play.speed.max(0.001),
            elapsed_seconds: 0.0,
            duration_seconds,
        });
    }
}

fn play_animation_clip_on_players(
    commands: &mut Commands,
    clip_to_play: Handle<AnimationClip>,
    playback_players: Vec<Entity>,
    speed: f32,
    looped: bool,
    transition_duration: Duration,
    graphs: &mut Assets<AnimationGraph>,
    players: &mut Query<(
        &mut AnimationPlayer,
        Option<&mut AnimationGraphHandle>,
        Option<&mut AnimationTransitions>,
        Option<&Name>,
    )>,
) {
    for child in playback_players {
        if let Ok((mut player, mut graph_component, mut transitions, _name)) =
            players.get_mut(child)
        {
            let compact_transition = compact_transition_graph_for_player(
                clip_to_play.clone(),
                &player,
                graph_component.as_deref(),
                graphs,
            );
            let (graph, index, compact_from) =
                if let Some((graph, index, from_index, from_state)) = compact_transition {
                    (graph, index, Some((from_index, from_state)))
                } else {
                    let (graph, index) = transition_graph_for_player(
                        clip_to_play.clone(),
                        &player,
                        graph_component.as_deref(),
                        graphs,
                    );
                    (graph, index, None)
                };
            let graph_handle = graphs.add(graph);
            if let Some(graph_component) = graph_component.as_mut() {
                graph_component.0 = graph_handle.clone();
            } else {
                commands
                    .entity(child)
                    .insert(AnimationGraphHandle(graph_handle.clone()));
            }

            if let Some((from_index, from_state)) = compact_from {
                let mut new_transitions = AnimationTransitions::new();
                player.stop_all();
                new_transitions
                    .play(&mut player, from_index, Duration::ZERO)
                    .set_seek_time(from_state.seek_time)
                    .set_speed(from_state.speed)
                    .set_weight(from_state.weight)
                    .set_repeat(from_state.repeat);
                let active = new_transitions
                    .play(&mut player, index, transition_duration)
                    .set_speed(speed)
                    .set_weight(1.0);
                if looped {
                    active.repeat();
                }
                if let Some(transitions) = transitions.as_mut() {
                    **transitions = new_transitions;
                } else {
                    commands.entity(child).insert(new_transitions);
                }
            } else if let Some(transitions) = transitions.as_mut() {
                let active = transitions
                    .play(&mut player, index, transition_duration)
                    .set_speed(speed)
                    .set_weight(1.0);
                if looped {
                    active.repeat();
                }
            } else {
                let mut new_transitions = AnimationTransitions::new();
                {
                    let active = new_transitions
                        .play(&mut player, index, transition_duration)
                        .set_speed(speed)
                        .set_weight(1.0);
                    if looped {
                        active.repeat();
                    }
                }
                commands.entity(child).insert(new_transitions);
            }
        }
    }
}

fn visual_transform_preroll_needed(
    animation_to_play: &mut AnimationToPlay,
    visual_translation: Vec3,
    visual_rotation_degrees: [f32; 3],
) -> bool {
    if !animation_to_play.external_source || animation_to_play.visual_transform_preapplied {
        return false;
    }
    animation_to_play.visual_transform_preapplied = true;
    has_meaningful_visual_transform(visual_translation, visual_rotation_degrees)
}

fn animation_transition_duration(
    animation_to_play: &AnimationToPlay,
    visual_translation: Vec3,
    visual_rotation_degrees: [f32; 3],
) -> Duration {
    if animation_to_play.external_source
        && has_meaningful_visual_transform(visual_translation, visual_rotation_degrees)
        && !animation_to_play.visual_transform_preapplied
    {
        Duration::ZERO
    } else {
        DEFAULT_ANIMATION_TRANSITION
    }
}

fn has_meaningful_visual_transform(
    visual_translation: Vec3,
    visual_rotation_degrees: [f32; 3],
) -> bool {
    visual_translation.length() > 0.001
        || visual_rotation_degrees
            .iter()
            .any(|degrees| degrees.abs() > 1.0)
}

fn has_meaningful_visual_rotation(visual_rotation_degrees: [f32; 3]) -> bool {
    visual_rotation_degrees
        .iter()
        .any(|degrees| degrees.abs() > 1.0)
}

fn compact_transition_graph_for_player(
    new_clip: Handle<AnimationClip>,
    player: &AnimationPlayer,
    graph_handle: Option<&AnimationGraphHandle>,
    graphs: &Assets<AnimationGraph>,
) -> Option<(
    AnimationGraph,
    AnimationNodeIndex,
    AnimationNodeIndex,
    ActiveAnimationClipState,
)> {
    let active_states = active_animation_clip_states(player, graph_handle, graphs);
    let (_, from_state) = active_states.into_iter().next().filter(|_| {
        player
            .playing_animations()
            .filter(|(_, animation)| !animation.is_paused())
            .count()
            == 1
    })?;

    let mut graph = AnimationGraph::new();
    let from_index = graph.add_clip(from_state.clip.clone(), 1.0, graph.root);
    let new_index = graph.add_clip(new_clip, 1.0, graph.root);
    Some((graph, new_index, from_index, from_state))
}

fn transition_graph_for_player(
    new_clip: Handle<AnimationClip>,
    player: &AnimationPlayer,
    graph_handle: Option<&AnimationGraphHandle>,
    graphs: &Assets<AnimationGraph>,
) -> (AnimationGraph, AnimationNodeIndex) {
    let active_states = active_animation_clip_states(player, graph_handle, graphs);
    let Some(max_active_index) = active_states.iter().map(|(index, _)| index.index()).max() else {
        return AnimationGraph::from_clip(new_clip);
    };
    if max_active_index == 0 || max_active_index > MAX_PRESERVED_TRANSITION_NODES {
        return AnimationGraph::from_clip(new_clip);
    }

    let mut graph = AnimationGraph::new();
    let mut active_states = active_states.into_iter().peekable();
    for node_index in 1..=max_active_index {
        let (clip, weight) = match active_states.peek() {
            Some((active_index, _)) if active_index.index() == node_index => {
                let (_, active_state) = active_states.next().expect("peeked active clip exists");
                (active_state.clip, 1.0)
            }
            _ => (new_clip.clone(), 0.0),
        };
        graph.add_clip(clip, weight, graph.root);
    }
    let new_index = graph.add_clip(new_clip, 1.0, graph.root);
    (graph, new_index)
}

#[derive(Clone)]
struct ActiveAnimationClipState {
    clip: Handle<AnimationClip>,
    seek_time: f32,
    speed: f32,
    weight: f32,
    repeat: RepeatAnimation,
}

fn active_animation_clip_states(
    player: &AnimationPlayer,
    graph_handle: Option<&AnimationGraphHandle>,
    graphs: &Assets<AnimationGraph>,
) -> Vec<(AnimationNodeIndex, ActiveAnimationClipState)> {
    let Some(graph_handle) = graph_handle else {
        return Vec::new();
    };
    let Some(graph) = graphs.get(&graph_handle.0) else {
        return Vec::new();
    };
    let mut clips = player
        .playing_animations()
        .filter(|(_, animation)| !animation.is_paused())
        .filter_map(|(node_index, animation)| {
            graph
                .graph
                .node_weight(*node_index)
                .and_then(|node| match &node.node_type {
                    AnimationNodeType::Clip(clip) => Some((
                        *node_index,
                        ActiveAnimationClipState {
                            clip: clip.clone(),
                            seek_time: animation.seek_time(),
                            speed: animation.speed(),
                            weight: animation.weight(),
                            repeat: animation.repeat_mode(),
                        },
                    )),
                    _ => None,
                })
        })
        .collect::<Vec<_>>();
    clips.sort_by_key(|(index, _)| index.index());
    clips
}

fn apply_animation_visual_transform(
    root: Entity,
    translation: Vec3,
    rotation_degrees: [f32; 3],
    children: &Query<&Children>,
    transform_queries: &mut ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: &Assets<Mesh>,
    visual_queries: &mut ParamSet<(
        Query<&mut SceneMaxAnimationVisualTransform>,
        Query<&SceneMaxAnimationVisualCompensationCache>,
    )>,
    commands: &mut Commands,
) {
    if translation.length_squared() <= f32::EPSILON
        && rotation_degrees
            .iter()
            .all(|value| value.abs() <= f32::EPSILON)
    {
        restore_animation_visual_transform(
            root,
            children,
            &mut transform_queries.p0(),
            &mut visual_queries.p0(),
            commands,
        );
        return;
    }

    let rotation = Quat::from_euler(
        EulerRot::XYZ,
        rotation_degrees[0].to_radians(),
        rotation_degrees[1].to_radians(),
        rotation_degrees[2].to_radians(),
    );
    let Ok(child_list) = children.get(root) else {
        return;
    };
    let mut pending_compensation_cache = visual_queries
        .p1()
        .get(root)
        .ok()
        .cloned()
        .unwrap_or_default();

    if let Ok(mut visual_transform) = visual_queries.p0().get_mut(root) {
        visual_transform.translation = translation;
        visual_transform.rotation_degrees = rotation_degrees;
        for child in child_list.iter() {
            let Some(current_transform) = transform_queries.p2().get(child).ok().copied() else {
                continue;
            };
            let base_transform = *visual_transform
                .base_by_child
                .entry(child)
                .or_insert(current_transform);
            let cached_compensation = cached_visual_compensation(
                &pending_compensation_cache,
                child,
                &base_transform,
                translation,
                rotation_degrees,
            );
            let (next_transform, compensation) = compensated_visual_transform(
                child,
                &base_transform,
                translation,
                rotation,
                cached_compensation,
                children,
                transform_queries,
                meshes,
            );
            log_animation_visual_transform_apply(
                root,
                child,
                &base_transform,
                &next_transform,
                translation,
                rotation_degrees,
                children,
                transform_queries,
                meshes,
            );
            if let Ok(mut transform) = transform_queries.p0().get_mut(child) {
                *transform = next_transform;
                pending_compensation_cache.by_child.insert(
                    child,
                    SceneMaxAnimationVisualCompensation {
                        translation,
                        rotation_degrees,
                        base_transform,
                        compensation,
                    },
                );
            }
        }
        upsert_visual_compensation_cache(root, pending_compensation_cache, commands);
        return;
    }

    let mut visual_transform = SceneMaxAnimationVisualTransform {
        translation,
        rotation_degrees,
        ..Default::default()
    };
    let mut applied = false;
    for child in child_list.iter() {
        let Some(base_transform) = transform_queries.p2().get(child).ok().copied() else {
            continue;
        };
        let cached_compensation = cached_visual_compensation(
            &pending_compensation_cache,
            child,
            &base_transform,
            translation,
            rotation_degrees,
        );
        let (next_transform, compensation) = compensated_visual_transform(
            child,
            &base_transform,
            translation,
            rotation,
            cached_compensation,
            children,
            transform_queries,
            meshes,
        );
        log_animation_visual_transform_apply(
            root,
            child,
            &base_transform,
            &next_transform,
            translation,
            rotation_degrees,
            children,
            transform_queries,
            meshes,
        );
        if let Ok(mut transform) = transform_queries.p0().get_mut(child) {
            visual_transform.base_by_child.insert(child, base_transform);
            *transform = next_transform;
            pending_compensation_cache.by_child.insert(
                child,
                SceneMaxAnimationVisualCompensation {
                    translation,
                    rotation_degrees,
                    base_transform,
                    compensation,
                },
            );
            applied = true;
        }
    }
    if applied {
        commands.entity(root).insert(visual_transform);
        upsert_visual_compensation_cache(root, pending_compensation_cache, commands);
    }
}

fn log_animation_visual_transform_apply(
    root: Entity,
    child: Entity,
    base_transform: &Transform,
    next_transform: &Transform,
    translation: Vec3,
    rotation_degrees: [f32; 3],
    children: &Query<&Children>,
    transform_queries: &mut ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: &Assets<Mesh>,
) {
    if !runtime_verbose_logging() {
        return;
    }
    let base_min_y =
        transformed_subtree_min_y(child, base_transform, children, transform_queries, meshes);
    let next_min_y =
        transformed_subtree_min_y(child, next_transform, children, transform_queries, meshes);
    write_runtime_log_line(
        LoggerLevel::Debug,
        &format!(
            "ANIM_VISUAL_APPLY root={root:?} child={child:?} base_y={} next_y={} delta_y={} base_min_y={} next_min_y={} min_delta_y={} visual_translation=({},{},{}) visual_rotation=({},{},{})",
            format_scenemax_number(base_transform.translation.y),
            format_scenemax_number(next_transform.translation.y),
            format_scenemax_number(next_transform.translation.y - base_transform.translation.y),
            format_optional_scenemax_number(base_min_y),
            format_optional_scenemax_number(next_min_y),
            format_optional_delta(base_min_y, next_min_y),
            format_scenemax_number(translation.x),
            format_scenemax_number(translation.y),
            format_scenemax_number(translation.z),
            format_scenemax_number(rotation_degrees[0]),
            format_scenemax_number(rotation_degrees[1]),
            format_scenemax_number(rotation_degrees[2]),
        ),
    );
}

fn compensated_visual_transform(
    root: Entity,
    base_transform: &Transform,
    translation: Vec3,
    rotation: Quat,
    cached_compensation: Option<Vec3>,
    children: &Query<&Children>,
    transform_queries: &mut ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: &Assets<Mesh>,
) -> (Transform, Vec3) {
    let mut transform = Transform {
        translation: base_transform.translation + translation,
        rotation: base_transform.rotation * rotation,
        scale: base_transform.scale,
    };
    let compensation = cached_compensation.unwrap_or_else(|| {
        visual_rotation_bounds_compensation(
            root,
            base_transform,
            translation,
            rotation,
            children,
            transform_queries,
            meshes,
        )
    });
    transform.translation += compensation;
    (transform, compensation)
}

fn cached_visual_compensation(
    cache: &SceneMaxAnimationVisualCompensationCache,
    child: Entity,
    base_transform: &Transform,
    translation: Vec3,
    rotation_degrees: [f32; 3],
) -> Option<Vec3> {
    cache
        .by_child
        .get(&child)
        .filter(|entry| {
            visual_compensation_matches(entry, base_transform, translation, rotation_degrees)
        })
        .map(|entry| entry.compensation)
}

fn visual_compensation_matches(
    entry: &SceneMaxAnimationVisualCompensation,
    base_transform: &Transform,
    translation: Vec3,
    rotation_degrees: [f32; 3],
) -> bool {
    entry
        .translation
        .abs_diff_eq(translation, VISUAL_COMPENSATION_CACHE_EPSILON)
        && entry.base_transform.translation.abs_diff_eq(
            base_transform.translation,
            VISUAL_COMPENSATION_CACHE_EPSILON,
        )
        && entry
            .base_transform
            .rotation
            .abs_diff_eq(base_transform.rotation, VISUAL_COMPENSATION_CACHE_EPSILON)
        && entry
            .base_transform
            .scale
            .abs_diff_eq(base_transform.scale, VISUAL_COMPENSATION_CACHE_EPSILON)
        && entry
            .rotation_degrees
            .iter()
            .zip(rotation_degrees)
            .all(|(before, after)| (*before - after).abs() <= VISUAL_COMPENSATION_CACHE_EPSILON)
}

fn upsert_visual_compensation_cache(
    root: Entity,
    cache: SceneMaxAnimationVisualCompensationCache,
    commands: &mut Commands,
) {
    commands.entity(root).insert(cache);
}

fn visual_rotation_bounds_compensation(
    root: Entity,
    base_transform: &Transform,
    translation: Vec3,
    rotation: Quat,
    children: &Query<&Children>,
    transform_queries: &mut ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: &Assets<Mesh>,
) -> Vec3 {
    if rotation.abs_diff_eq(Quat::IDENTITY, f32::EPSILON) {
        return Vec3::ZERO;
    }
    let Some(base_bounds) =
        transformed_subtree_bounds(root, base_transform, children, transform_queries, meshes)
    else {
        return Vec3::ZERO;
    };
    let rotated_transform = Transform {
        translation: base_transform.translation + translation,
        rotation: base_transform.rotation * rotation,
        scale: base_transform.scale,
    };
    let Some(rotated_bounds) = transformed_subtree_bounds(
        root,
        &rotated_transform,
        children,
        transform_queries,
        meshes,
    ) else {
        return Vec3::ZERO;
    };
    let base_center = base_bounds.center();
    let rotated_center = rotated_bounds.center();
    Vec3::new(
        base_center.x + translation.x - rotated_center.x,
        base_bounds.min.y + translation.y - rotated_bounds.min.y,
        base_center.z + translation.z - rotated_center.z,
    )
}

fn transformed_subtree_min_y(
    root: Entity,
    root_transform: &Transform,
    children: &Query<&Children>,
    transform_queries: &mut ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: &Assets<Mesh>,
) -> Option<f32> {
    transformed_subtree_bounds(root, root_transform, children, transform_queries, meshes)
        .map(|bounds| bounds.min.y)
}

fn transformed_subtree_bounds(
    root: Entity,
    root_transform: &Transform,
    children: &Query<&Children>,
    transform_queries: &mut ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: &Assets<Mesh>,
) -> Option<VisualBounds> {
    let root_matrix = root_transform.to_matrix();
    transformed_subtree_bounds_recursive(
        root,
        Mat4::IDENTITY,
        root_matrix,
        children,
        transform_queries,
        meshes,
    )
}

fn transformed_subtree_bounds_recursive(
    entity: Entity,
    local_from_root: Mat4,
    world_from_root: Mat4,
    children: &Query<&Children>,
    transform_queries: &mut ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: &Assets<Mesh>,
) -> Option<VisualBounds> {
    let entity_matrix = world_from_root * local_from_root;
    let mut bounds = transformed_mesh_bounds(entity, entity_matrix, transform_queries, meshes);
    if let Ok(child_list) = children.get(entity) {
        for child in child_list {
            let child_transform = transform_queries
                .p2()
                .get(*child)
                .ok()
                .map(Transform::to_matrix)
                .unwrap_or(Mat4::IDENTITY);
            let child_bounds = transformed_subtree_bounds_recursive(
                *child,
                local_from_root * child_transform,
                world_from_root,
                children,
                transform_queries,
                meshes,
            );
            bounds = union_optional_bounds(bounds, child_bounds);
        }
    }
    bounds
}

fn transformed_mesh_bounds(
    entity: Entity,
    transform: Mat4,
    transform_queries: &mut ParamSet<(
        Query<&mut Transform>,
        Query<&Transform, With<AnimationTargetId>>,
        Query<&Transform>,
        Query<&Mesh3d>,
    )>,
    meshes: &Assets<Mesh>,
) -> Option<VisualBounds> {
    let mesh_handle = transform_queries.p3().get(entity).ok()?.clone();
    let aabb = meshes
        .get(&mesh_handle.0)
        .and_then(MeshAabb::compute_aabb)?;
    VisualBounds::from_points(
        aabb_corners(aabb)
            .into_iter()
            .map(|corner| transform.transform_point3(corner)),
    )
}

fn aabb_corners(aabb: Aabb) -> [Vec3; 8] {
    let min: Vec3 = aabb.min().into();
    let max: Vec3 = aabb.max().into();
    [
        Vec3::new(min.x, min.y, min.z),
        Vec3::new(min.x, min.y, max.z),
        Vec3::new(min.x, max.y, min.z),
        Vec3::new(min.x, max.y, max.z),
        Vec3::new(max.x, min.y, min.z),
        Vec3::new(max.x, min.y, max.z),
        Vec3::new(max.x, max.y, min.z),
        Vec3::new(max.x, max.y, max.z),
    ]
}

#[derive(Clone, Copy)]
struct VisualBounds {
    min: Vec3,
    max: Vec3,
}

impl VisualBounds {
    fn from_points(points: impl IntoIterator<Item = Vec3>) -> Option<Self> {
        let mut points = points.into_iter();
        let first = points.next()?;
        let mut bounds = Self {
            min: first,
            max: first,
        };
        for point in points {
            bounds.include(point);
        }
        Some(bounds)
    }

    fn center(self) -> Vec3 {
        (self.min + self.max) * 0.5
    }

    fn include(&mut self, point: Vec3) {
        self.min = self.min.min(point);
        self.max = self.max.max(point);
    }

    fn union(mut self, other: Self) -> Self {
        self.include(other.min);
        self.include(other.max);
        self
    }
}

fn union_optional_bounds(a: Option<VisualBounds>, b: Option<VisualBounds>) -> Option<VisualBounds> {
    match (a, b) {
        (Some(a), Some(b)) => Some(a.union(b)),
        (Some(a), None) => Some(a),
        (None, Some(b)) => Some(b),
        (None, None) => None,
    }
}

fn format_optional_scenemax_number(value: Option<f32>) -> String {
    value
        .map(format_scenemax_number)
        .unwrap_or_else(|| "<none>".to_owned())
}

fn format_optional_delta(before: Option<f32>, after: Option<f32>) -> String {
    before
        .zip(after)
        .map(|(before, after)| format_scenemax_number(after - before))
        .unwrap_or_else(|| "<none>".to_owned())
}

fn restore_animation_visual_transform(
    root: Entity,
    children: &Query<&Children>,
    transforms: &mut Query<&mut Transform>,
    visual_transforms: &mut Query<&mut SceneMaxAnimationVisualTransform>,
    commands: &mut Commands,
) {
    let Ok(visual_transform) = visual_transforms.get_mut(root) else {
        return;
    };
    for (child, base_transform) in &visual_transform.base_by_child {
        if children
            .get(root)
            .is_ok_and(|child_list| child_list.contains(child))
            && let Ok(mut transform) = transforms.get_mut(*child)
        {
            *transform = *base_transform;
        }
    }
    commands
        .entity(root)
        .remove::<SceneMaxAnimationVisualTransform>();
}

fn switch_to_external_animation_source(
    target_name: &str,
    target_model_resource: Option<&str>,
    animation_to_play: &mut AnimationToPlay,
    asset_server: &AssetServer,
    runtime_assets: &mut SceneMaxRuntimeAssets,
) -> bool {
    if animation_to_play.tried_external_source {
        return false;
    }
    animation_to_play.tried_external_source = true;

    let animation_key = normalized_animation_name(&animation_to_play.runtime_clip);
    if let Some(animation) = runtime_assets
        .external_animations_by_name
        .get(&animation_key)
        .cloned()
    {
        use_external_animation_source(
            target_name,
            target_model_resource,
            animation_to_play,
            &animation,
        );
        return true;
    }

    if runtime_assets
        .external_animation_misses
        .contains(&animation_key)
    {
        return false;
    }

    let Some(asset_root) = runtime_assets.asset_root.as_deref() else {
        runtime_assets
            .external_animation_misses
            .insert(animation_key);
        return false;
    };

    match scenemax_assets::resolve_animation_resource_with_builtin_fallback(
        asset_root,
        runtime_assets.builtin_asset_root.as_deref(),
        &animation_to_play.runtime_clip,
    ) {
        Ok(resource) => {
            let animation = RuntimeExternalAnimation {
                gltf: asset_server.load(resource.asset_path.clone()),
                clip: resource.clip_name,
                asset_path: resource.asset_path,
                retarget: resource.bevy_retarget,
                baked_retargets: resource
                    .bevy_baked_retargets
                    .into_iter()
                    .map(|baked| RuntimeBakedRetarget {
                        model: baked.model,
                        path: baked.path,
                        clip: baked.clip_name,
                        visual_rotation_baked: baked.visual_rotation_baked,
                    })
                    .collect(),
            };
            runtime_assets
                .external_animations_by_name
                .insert(animation_key, animation.clone());
            use_external_animation_source(
                target_name,
                target_model_resource,
                animation_to_play,
                &animation,
            );
            true
        }
        Err(error) => {
            runtime_assets
                .external_animation_misses
                .insert(animation_key);
            tracing::debug!(target = target_name, clip = %animation_to_play.runtime_clip, %error, "external animation source was not found");
            false
        }
    }
}

fn use_external_animation_source(
    target_name: &str,
    target_model_resource: Option<&str>,
    animation_to_play: &mut AnimationToPlay,
    animation: &RuntimeExternalAnimation,
) {
    if let Some(baked) = target_model_resource
        .and_then(|model| matching_baked_retarget(&animation.baked_retargets, model))
        .cloned()
    {
        animation_to_play.baked_external = Some(baked.clone());
        animation_to_play.external_retarget = animation.retarget.clone();
        animation_to_play.external_source = true;
        animation_to_play.visual_transform_preapplied = false;
        animation_to_play.retarget_wait_logged = false;
        if runtime_verbose_logging() {
            write_runtime_log_line(
                LoggerLevel::Debug,
                &format!(
                    "ANIM_EXTERNAL_BAKED target={} clip={} model={} baked_clip={} path={}",
                    target_name,
                    animation_to_play.runtime_clip,
                    baked.model,
                    baked.clip,
                    baked.path,
                ),
            );
        }
        return;
    }
    animation_to_play.gltf = animation.gltf.clone();
    animation_to_play.clip = animation.clip.clone();
    animation_to_play.baked_external = None;
    animation_to_play.external_retarget = animation.retarget.clone();
    animation_to_play.external_source = true;
    animation_to_play.visual_transform_preapplied = false;
    animation_to_play.retarget_wait_logged = false;
    if runtime_verbose_logging() {
        write_runtime_log_line(
            LoggerLevel::Debug,
            &format!(
                "ANIM_EXTERNAL_SOURCE target={} clip={} source_clip={}",
                target_name, animation_to_play.runtime_clip, animation.clip,
            ),
        );
    }
    tracing::debug!(
        target = target_name,
        clip = %animation_to_play.runtime_clip,
        source = %animation.asset_path,
        source_clip = %animation.clip,
        "using external animation source"
    );
}

pub(super) fn retarget_clip_to_visible_animation_player(
    source_clip: &AnimationClip,
    source_gltf: &Gltf,
    resolved_clip_name: &str,
    external_source: bool,
    external_retarget: &scenemax_assets::AnimationRetargetOptions,
    animation_players: &[Entity],
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    target_transforms: &Query<&Transform, With<AnimationTargetId>>,
    gltf_nodes: &Assets<GltfNode>,
    players: &Query<(
        &mut AnimationPlayer,
        Option<&mut AnimationGraphHandle>,
        Option<&mut AnimationTransitions>,
        Option<&Name>,
    )>,
) -> Option<(AnimationClip, Entity, usize)> {
    let destination_player = animation_players.first().copied()?;
    if external_source {
        return retarget_external_clip_to_visible_animation_player(
            source_clip,
            source_gltf,
            external_retarget,
            destination_player,
            children,
            animation_targets,
            target_transforms,
            gltf_nodes,
        )
        .or_else(|| {
            retarget_clip_by_destination_path_suffix(
                source_clip,
                destination_player,
                children,
                animation_targets,
                external_retarget,
            )
        });
    }

    let source_player = animation_players.iter().copied().find(|entity| {
        players
            .get(*entity)
            .ok()
            .and_then(|(_, _, _, name)| name)
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
            source_to_destination.insert(
                source_target,
                RetargetTarget {
                    destination: destination_target,
                    profile_bone: humanoid_profile_bone(&bone_name),
                },
            );
        }
    }
    if source_to_destination.is_empty() {
        return None;
    }

    retarget_clip_with_target_map(
        source_clip,
        destination_player,
        &source_to_destination,
        &preserve_animation_transform_tracks_options(),
        1.0,
    )
}

fn retarget_external_clip_to_visible_animation_player(
    source_clip: &AnimationClip,
    source_gltf: &Gltf,
    retarget: &scenemax_assets::AnimationRetargetOptions,
    destination_player: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    target_transforms: &Query<&Transform, With<AnimationTargetId>>,
    gltf_nodes: &Assets<GltfNode>,
) -> Option<(AnimationClip, Entity, usize)> {
    let destination_targets =
        collect_animation_targets_by_name(destination_player, children, animation_targets);
    if destination_targets.is_empty() {
        return None;
    }

    let source_paths = collect_gltf_animation_target_paths(source_gltf, gltf_nodes);
    if source_paths.is_empty() {
        return None;
    }
    let excluded_bones = retarget
        .exclude_bones
        .iter()
        .map(|bone| normalized_animation_name(bone))
        .collect::<HashSet<_>>();

    let mut root_source_targets = HashSet::new();
    let mut animated_path_depths = BTreeSet::new();
    for path in &source_paths {
        let source_target = AnimationTargetId::from_iter(path.path.iter());
        if !source_clip.curves().contains_key(&source_target) {
            continue;
        }
        animated_path_depths.insert(path.path.len());
    }
    let skipped_path_depths = animated_path_depths
        .into_iter()
        .take(retarget.skip_top_animated_targets)
        .collect::<HashSet<_>>();
    for path in &source_paths {
        let source_target = AnimationTargetId::from_iter(path.path.iter());
        if source_clip.curves().contains_key(&source_target)
            && skipped_path_depths.contains(&path.path.len())
        {
            root_source_targets.insert(source_target);
        }
    }

    let retarget_map = profile_retarget_map(
        &source_paths,
        source_clip,
        &destination_targets,
        &excluded_bones,
        &root_source_targets,
        &retarget.profile,
    )
    .unwrap_or_else(|| {
        exact_name_retarget_map(
            &source_paths,
            source_clip,
            &destination_targets,
            &excluded_bones,
            &root_source_targets,
        )
    });
    if retarget_map.is_empty() {
        return None;
    }
    let motion_scale = retarget_motion_scale(
        retarget,
        &source_paths,
        destination_player,
        children,
        animation_targets,
        target_transforms,
    );

    retarget_clip_with_target_map(
        source_clip,
        destination_player,
        &retarget_map,
        retarget,
        motion_scale,
    )
}

fn exact_name_retarget_map(
    source_paths: &[GltfAnimationTargetPath],
    source_clip: &AnimationClip,
    destination_targets: &HashMap<String, AnimationTargetId>,
    excluded_bones: &HashSet<String>,
    root_source_targets: &HashSet<AnimationTargetId>,
) -> HashMap<AnimationTargetId, RetargetTarget> {
    let mut retarget_map = HashMap::new();
    let mut ambiguous_source_targets = HashSet::new();
    for path in source_paths {
        let Some(target_name) = path.path.last() else {
            continue;
        };
        let source_target = AnimationTargetId::from_iter(path.path.iter());
        if !source_clip.curves().contains_key(&source_target)
            || root_source_targets.contains(&source_target)
        {
            continue;
        }
        let normalized_target_name = normalized_animation_name(target_name);
        if excluded_bones.contains(&normalized_target_name) {
            continue;
        }
        let Some(destination_target) = destination_targets.get(&normalized_target_name).copied()
        else {
            continue;
        };
        if retarget_map
            .insert(
                source_target,
                RetargetTarget {
                    destination: destination_target,
                    profile_bone: humanoid_profile_bone(target_name),
                },
            )
            .is_some_and(|existing| existing.destination != destination_target)
        {
            ambiguous_source_targets.insert(source_target);
        }
    }
    for source_target in ambiguous_source_targets {
        retarget_map.remove(&source_target);
    }

    retarget_map
}

fn profile_retarget_map(
    source_paths: &[GltfAnimationTargetPath],
    source_clip: &AnimationClip,
    destination_targets: &HashMap<String, AnimationTargetId>,
    excluded_bones: &HashSet<String>,
    root_source_targets: &HashSet<AnimationTargetId>,
    profile: &str,
) -> Option<HashMap<AnimationTargetId, RetargetTarget>> {
    let mode = retarget_profile_mode(profile)?;
    let destination_by_bone = profile_destination_targets(destination_targets);
    if destination_by_bone.is_empty() {
        return None;
    }

    let mut source_by_bone = HashMap::new();
    let mut ambiguous_bones = HashSet::new();
    for path in source_paths {
        let Some(target_name) = path.path.last() else {
            continue;
        };
        let source_target = AnimationTargetId::from_iter(path.path.iter());
        if !source_clip.curves().contains_key(&source_target)
            || root_source_targets.contains(&source_target)
        {
            continue;
        }
        let normalized_target_name = normalized_animation_name(target_name);
        if excluded_bones.contains(&normalized_target_name) {
            continue;
        }
        let Some(profile_bone) = humanoid_profile_bone(target_name) else {
            continue;
        };
        if source_by_bone
            .insert(profile_bone, source_target)
            .is_some_and(|existing| existing != source_target)
        {
            ambiguous_bones.insert(profile_bone);
        }
    }
    for bone in ambiguous_bones {
        source_by_bone.remove(bone);
    }

    let mut retarget_map = HashMap::new();
    for (profile_bone, source_target) in source_by_bone {
        if let Some(destination_target) = destination_by_bone.get(profile_bone).copied() {
            retarget_map.insert(
                source_target,
                RetargetTarget {
                    destination: destination_target,
                    profile_bone: Some(profile_bone),
                },
            );
        }
    }

    let min_bones = match mode {
        RetargetProfileMode::Auto => 4,
        RetargetProfileMode::Humanoid => 1,
    };
    (retarget_map.len() >= min_bones).then_some(retarget_map)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum RetargetProfileMode {
    Auto,
    Humanoid,
}

fn retarget_profile_mode(profile: &str) -> Option<RetargetProfileMode> {
    match normalized_animation_name(profile).as_str() {
        "auto" | "" => Some(RetargetProfileMode::Auto),
        "humanoid" | "human" => Some(RetargetProfileMode::Humanoid),
        "exact" | "none" | "off" => None,
        _ => Some(RetargetProfileMode::Auto),
    }
}

fn matching_baked_retarget<'a>(
    baked_retargets: &'a [RuntimeBakedRetarget],
    model_resource: &str,
) -> Option<&'a RuntimeBakedRetarget> {
    let model_key = normalized_animation_name(model_resource);
    baked_retargets
        .iter()
        .find(|baked| normalized_animation_name(&baked.model) == model_key)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BakedAnimationFile {
    #[serde(default = "baked_animation_format")]
    format: String,
    #[serde(default)]
    clip: String,
    duration: f32,
    targets: Vec<BakedAnimationTarget>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BakedAnimationTarget {
    name: String,
    #[serde(default)]
    translations: Vec<[f32; 4]>,
    #[serde(default)]
    rotations: Vec<[f32; 5]>,
}

fn baked_animation_format() -> String {
    "scenemax-bevy-baked-animation-v1".to_owned()
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct BakedAnimationOutput {
    format: &'static str,
    clip: String,
    model: String,
    visual_rotation_baked: bool,
    duration: f32,
    targets: Vec<BakedAnimationOutputTarget>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct BakedAnimationOutputTarget {
    name: String,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    translations: Vec<[f32; 4]>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    rotations: Vec<[f32; 5]>,
}

fn write_baked_animation_clip(
    clip: &AnimationClip,
    destination_player: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    target_transforms: &Query<&Transform, With<AnimationTargetId>>,
    bake_request: &RuntimeAnimationBakeRequest,
    retarget: &scenemax_assets::AnimationRetargetOptions,
) -> anyhow::Result<()> {
    let target_data = collect_animation_target_bake_data(
        destination_player,
        children,
        animation_targets,
        target_transforms,
    );
    let mut targets = Vec::new();
    let rebase_visual_rotation = has_meaningful_visual_rotation(retarget.visual_rotation_degrees);
    let mut rebased_visual_rotation = false;
    for (target_id, curves) in clip.curves() {
        let Some((name, destination_transform)) = target_data.get(target_id).cloned() else {
            continue;
        };
        let rebase_target_rotation = rebase_visual_rotation
            && humanoid_profile_bone(&name)
                .is_some_and(|bone| is_profile_motion_bone(bone, retarget));
        let mut output = BakedAnimationOutputTarget {
            name,
            translations: Vec::new(),
            rotations: Vec::new(),
        };
        for curve in curves {
            if is_transform_translation_curve(curve) {
                output.translations = sample_vec3_curve_for_bake(curve);
            } else if is_transform_rotation_curve(curve) {
                output.rotations = sample_quat_curve_for_bake(
                    curve,
                    rebase_target_rotation.then_some(destination_transform.rotation),
                );
                rebased_visual_rotation |= rebase_target_rotation && !output.rotations.is_empty();
            }
        }
        if !output.translations.is_empty() || !output.rotations.is_empty() {
            targets.push(output);
        }
    }
    if targets.is_empty() {
        anyhow::bail!("retargeted clip produced no serializable transform curves");
    }
    if let Some(parent) = bake_request.output_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let output = BakedAnimationOutput {
        format: "scenemax-bevy-baked-animation-v1",
        clip: bake_request.clip.clone(),
        model: bake_request.model.clone(),
        visual_rotation_baked: rebased_visual_rotation,
        duration: clip.duration(),
        targets,
    };
    fs::write(
        &bake_request.output_path,
        serde_json::to_string_pretty(&output)?,
    )?;
    Ok(())
}

fn collect_animation_target_bake_data(
    root: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    target_transforms: &Query<&Transform, With<AnimationTargetId>>,
) -> HashMap<AnimationTargetId, (String, Transform)> {
    let mut data = HashMap::new();
    for entity in children.iter_descendants(root) {
        let Ok((target_id, Some(name))) = animation_targets.get(entity) else {
            continue;
        };
        let Ok(transform) = target_transforms.get(entity) else {
            continue;
        };
        data.insert(*target_id, (name.as_str().to_owned(), *transform));
    }
    data
}

fn load_baked_external_animation_clip(
    asset_root: Option<&Path>,
    baked: &RuntimeBakedRetarget,
    destination_targets: &HashMap<String, AnimationTargetId>,
) -> anyhow::Result<AnimationClip> {
    let asset_root = asset_root.ok_or_else(|| anyhow::anyhow!("asset root is not available"))?;
    let path = asset_root.join(&baked.path);
    let raw = fs::read_to_string(&path)?;
    let baked_file: BakedAnimationFile = serde_json::from_str(&raw)?;
    if baked_file.format != "scenemax-bevy-baked-animation-v1" {
        anyhow::bail!("unsupported baked animation format '{}'", baked_file.format);
    }
    if !baked_file.clip.is_empty()
        && normalized_animation_name(&baked_file.clip) != normalized_animation_name(&baked.clip)
    {
        anyhow::bail!(
            "baked clip '{}' did not match requested clip '{}'",
            baked_file.clip,
            baked.clip
        );
    }
    let mut clip = AnimationClip::default();
    clip.set_duration(baked_file.duration.max(0.0));
    let mut curve_count = 0usize;
    for target in baked_file.targets {
        let target_key = normalized_animation_name(&target.name);
        let Some(destination_target) = destination_targets.get(&target_key).copied() else {
            continue;
        };
        if let Some(curve) = baked_translation_curve(&target.translations) {
            clip.add_variable_curve_to_target(destination_target, curve);
            curve_count += 1;
        }
        if let Some(curve) = baked_rotation_curve(&target.rotations) {
            clip.add_variable_curve_to_target(destination_target, curve);
            curve_count += 1;
        }
    }
    if curve_count == 0 {
        anyhow::bail!("baked clip had no curves matching the target skeleton");
    }
    Ok(clip)
}

fn baked_translation_curve(samples: &[[f32; 4]]) -> Option<VariableCurve> {
    let keyframes = samples.iter().filter_map(|sample| {
        let [time, x, y, z] = *sample;
        time.is_finite().then_some((time, Vec3::new(x, y, z)))
    });
    let keyframe_curve = AnimatableKeyframeCurve::new(keyframes).ok()?;
    Some(VariableCurve::new(AnimatableCurve::new(
        animated_field!(Transform::translation),
        keyframe_curve,
    )))
}

fn baked_rotation_curve(samples: &[[f32; 5]]) -> Option<VariableCurve> {
    let keyframes = samples.iter().filter_map(|sample| {
        let [time, x, y, z, w] = *sample;
        if !time.is_finite() {
            return None;
        }
        Some((time, Quat::from_xyzw(x, y, z, w).normalize()))
    });
    let keyframe_curve = AnimatableKeyframeCurve::new(keyframes).ok()?;
    Some(VariableCurve::new(AnimatableCurve::new(
        animated_field!(Transform::rotation),
        keyframe_curve,
    )))
}

fn profile_destination_targets(
    destination_targets: &HashMap<String, AnimationTargetId>,
) -> HashMap<&'static str, AnimationTargetId> {
    let mut by_bone = HashMap::new();
    let mut ambiguous_bones = HashSet::new();
    for (target_name, target_id) in destination_targets {
        let Some(profile_bone) = humanoid_profile_bone(target_name) else {
            continue;
        };
        if by_bone
            .insert(profile_bone, *target_id)
            .is_some_and(|existing| existing != *target_id)
        {
            ambiguous_bones.insert(profile_bone);
        }
    }
    for bone in ambiguous_bones {
        by_bone.remove(bone);
    }
    by_bone
}

fn humanoid_profile_bone(name: &str) -> Option<&'static str> {
    let key = retarget_profile_bone_key(name);
    HUMANOID_BONE_ALIASES
        .iter()
        .find(|(_, aliases)| aliases.iter().any(|alias| *alias == key))
        .map(|(bone, _)| *bone)
}

fn retarget_profile_bone_key(name: &str) -> String {
    let mut key = normalized_animation_name(name);
    for prefix in [
        "mixamorig",
        "mixamo",
        "bip001",
        "bip01",
        "bip",
        "jbip",
        "armature",
    ] {
        if let Some(stripped) = key.strip_prefix(prefix) {
            key = stripped.to_owned();
            break;
        }
    }
    key
}

const HUMANOID_BONE_ALIASES: &[(&str, &[&str])] = &[
    ("hips", &["hips", "hip", "pelvis"]),
    ("spine", &["spine", "spine0", "spine01", "body"]),
    (
        "chest",
        &["chest", "spine1", "spine01", "spine2", "spine02", "torso"],
    ),
    (
        "upper_chest",
        &["upperchest", "spine3", "spine03", "spine4", "spine04"],
    ),
    ("neck", &["neck"]),
    ("head", &["head"]),
    (
        "left_shoulder",
        &[
            "leftshoulder",
            "lshoulder",
            "shoulderl",
            "leftclavicle",
            "lclavicle",
            "claviclel",
        ],
    ),
    (
        "right_shoulder",
        &[
            "rightshoulder",
            "rshoulder",
            "shoulderr",
            "rightclavicle",
            "rclavicle",
            "clavicler",
        ],
    ),
    (
        "left_upper_arm",
        &[
            "leftupperarm",
            "lupperarm",
            "upperarml",
            "leftarm",
            "larm",
            "arml",
        ],
    ),
    (
        "right_upper_arm",
        &[
            "rightupperarm",
            "rupperarm",
            "upperarmr",
            "rightarm",
            "rarm",
            "armr",
        ],
    ),
    (
        "left_lower_arm",
        &[
            "leftlowerarm",
            "llowerarm",
            "lowerarml",
            "leftforearm",
            "lforearm",
            "forearml",
        ],
    ),
    (
        "right_lower_arm",
        &[
            "rightlowerarm",
            "rlowerarm",
            "lowerarmr",
            "rightforearm",
            "rforearm",
            "forearmr",
        ],
    ),
    (
        "left_hand",
        &[
            "lefthand",
            "lhand",
            "handl",
            "leftwrist",
            "lwrist",
            "wristl",
        ],
    ),
    (
        "right_hand",
        &[
            "righthand",
            "rhand",
            "handr",
            "rightwrist",
            "rwrist",
            "wristr",
        ],
    ),
    (
        "left_upper_leg",
        &[
            "leftupperleg",
            "lupperleg",
            "upperlegl",
            "leftupleg",
            "lupleg",
            "uplegl",
            "leftthigh",
            "lthigh",
            "thighl",
        ],
    ),
    (
        "right_upper_leg",
        &[
            "rightupperleg",
            "rupperleg",
            "upperlegr",
            "rightupleg",
            "rupleg",
            "uplegr",
            "rightthigh",
            "rthigh",
            "thighr",
        ],
    ),
    (
        "left_lower_leg",
        &[
            "leftlowerleg",
            "llowerleg",
            "lowerlegl",
            "leftleg",
            "lleg",
            "legl",
            "leftcalf",
            "lcalf",
            "calfl",
            "leftshin",
            "lshin",
            "shinl",
        ],
    ),
    (
        "right_lower_leg",
        &[
            "rightlowerleg",
            "rlowerleg",
            "lowerlegr",
            "rightleg",
            "rleg",
            "legr",
            "rightcalf",
            "rcalf",
            "calfr",
            "rightshin",
            "rshin",
            "shinr",
        ],
    ),
    (
        "left_foot",
        &[
            "leftfoot",
            "lfoot",
            "footl",
            "leftankle",
            "lankle",
            "anklel",
        ],
    ),
    (
        "right_foot",
        &[
            "rightfoot",
            "rfoot",
            "footr",
            "rightankle",
            "rankle",
            "ankler",
        ],
    ),
    (
        "left_toes",
        &[
            "lefttoe",
            "ltoe",
            "toel",
            "lefttoes",
            "ltoes",
            "toesl",
            "lefttoebase",
            "ltoebase",
            "toebasel",
        ],
    ),
    (
        "right_toes",
        &[
            "righttoe",
            "rtoe",
            "toer",
            "righttoes",
            "rtoes",
            "toesr",
            "righttoebase",
            "rtoebase",
            "toebaser",
        ],
    ),
];

fn retarget_clip_by_destination_path_suffix(
    source_clip: &AnimationClip,
    destination_player: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    retarget: &scenemax_assets::AnimationRetargetOptions,
) -> Option<(AnimationClip, Entity, usize)> {
    let destination_paths =
        collect_animation_target_paths(destination_player, children, animation_targets);
    if destination_paths.is_empty() {
        return None;
    }

    let mut retarget_map = HashMap::new();
    let mut ambiguous_source_targets = HashSet::new();
    for (path, destination_target) in destination_paths {
        for start in 0..path.len() {
            let source_candidate = AnimationTargetId::from_iter(path[start..].iter());
            if retarget_map
                .insert(
                    source_candidate,
                    RetargetTarget {
                        destination: destination_target,
                        profile_bone: path
                            .get(start)
                            .and_then(|target_name| humanoid_profile_bone(target_name)),
                    },
                )
                .is_some_and(|existing| existing.destination != destination_target)
            {
                ambiguous_source_targets.insert(source_candidate);
            }
        }
    }
    for source_target in ambiguous_source_targets {
        retarget_map.remove(&source_target);
    }

    retarget_clip_with_target_map(
        source_clip,
        destination_player,
        &retarget_map,
        retarget,
        1.0,
    )
}

fn preserve_animation_transform_tracks_options() -> scenemax_assets::AnimationRetargetOptions {
    scenemax_assets::AnimationRetargetOptions {
        remove_unimportant_translation_tracks: false,
        remove_motion_translation_tracks: false,
        remove_motion_rotation_tracks: false,
        normalize_motion_scale: false,
        ..Default::default()
    }
}

#[derive(Debug, Clone, Copy)]
struct RetargetTarget {
    destination: AnimationTargetId,
    profile_bone: Option<&'static str>,
}

fn retarget_clip_with_target_map(
    source_clip: &AnimationClip,
    destination_player: Entity,
    source_to_destination: &HashMap<AnimationTargetId, RetargetTarget>,
    retarget: &scenemax_assets::AnimationRetargetOptions,
    motion_scale: f32,
) -> Option<(AnimationClip, Entity, usize)> {
    let mut retargeted = AnimationClip::default();
    retargeted.set_duration(source_clip.duration());
    let mut stats = RetargetClipStats::default();
    let mut curve_count = 0usize;
    for (source_target, curves) in source_clip.curves() {
        let Some(destination_target) = source_to_destination.get(source_target).copied() else {
            continue;
        };
        for curve in curves {
            let retargeted_curve = if is_transform_translation_curve(curve) {
                stats.translation_curves += 1;
                if retarget.remove_motion_translation_tracks
                    && destination_target
                        .profile_bone
                        .is_some_and(|bone| is_profile_motion_bone(bone, retarget))
                {
                    stats.stripped_motion_translation_curves += 1;
                    continue;
                }
                if retarget.remove_unimportant_translation_tracks
                    && destination_target
                        .profile_bone
                        .is_none_or(|bone| !is_profile_motion_bone(bone, retarget))
                {
                    stats.stripped_unimportant_translation_curves += 1;
                    continue;
                }
                retarget_translation_curve(curve, retarget.locked_translation_axes, motion_scale)
                    .unwrap_or_else(|| curve.clone())
            } else if is_transform_rotation_curve(curve)
                && retarget.remove_motion_rotation_tracks
                && destination_target
                    .profile_bone
                    .is_some_and(|bone| is_profile_root_bone(bone, retarget))
            {
                stats.stripped_root_rotation_curves += 1;
                continue;
            } else {
                curve.clone()
            };
            retargeted
                .add_variable_curve_to_target(destination_target.destination, retargeted_curve);
            curve_count += 1;
        }
    }

    log_retarget_clip_stats(retarget, &stats, curve_count);
    (curve_count > 0).then_some((retargeted, destination_player, curve_count))
}

#[derive(Default)]
struct RetargetClipStats {
    translation_curves: usize,
    stripped_motion_translation_curves: usize,
    stripped_unimportant_translation_curves: usize,
    stripped_root_rotation_curves: usize,
}

fn log_retarget_clip_stats(
    retarget: &scenemax_assets::AnimationRetargetOptions,
    stats: &RetargetClipStats,
    kept_curve_count: usize,
) {
    if !animation_retarget_debug_enabled() {
        return;
    }
    write_runtime_log_line(
        LoggerLevel::Debug,
        &format!(
            "ANIM_RETARGET profile={} kept_curves={} translation_curves={} stripped_motion_translation={} stripped_unimportant_translation={} stripped_root_rotation={} remove_motion_translation={} remove_unimportant_translation={} remove_motion_rotation={}",
            retarget.profile,
            kept_curve_count,
            stats.translation_curves,
            stats.stripped_motion_translation_curves,
            stats.stripped_unimportant_translation_curves,
            stats.stripped_root_rotation_curves,
            retarget.remove_motion_translation_tracks as u8,
            retarget.remove_unimportant_translation_tracks as u8,
            retarget.remove_motion_rotation_tracks as u8,
        ),
    );
}

fn animation_retarget_debug_enabled() -> bool {
    if runtime_verbose_logging() {
        return true;
    }
    std::env::var("SCENEMAX_ANIMATION_RETARGET_DEBUG")
        .is_ok_and(|value| matches!(value.as_str(), "1" | "true" | "TRUE" | "on" | "ON"))
}

fn is_profile_motion_bone(
    profile_bone: &str,
    retarget: &scenemax_assets::AnimationRetargetOptions,
) -> bool {
    let bone = normalized_animation_name(profile_bone);
    bone == normalized_animation_name(&retarget.root_bone)
        || bone == normalized_animation_name(&retarget.scale_base_bone)
}

fn is_profile_root_bone(
    profile_bone: &str,
    retarget: &scenemax_assets::AnimationRetargetOptions,
) -> bool {
    normalized_animation_name(profile_bone) == normalized_animation_name(&retarget.root_bone)
}

fn retarget_motion_scale(
    retarget: &scenemax_assets::AnimationRetargetOptions,
    source_paths: &[GltfAnimationTargetPath],
    destination_player: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    target_transforms: &Query<&Transform, With<AnimationTargetId>>,
) -> f32 {
    if !retarget.normalize_motion_scale {
        return 1.0;
    }
    let Some(source_height) = source_profile_bone_height(source_paths, &retarget.scale_base_bone)
    else {
        return 1.0;
    };
    let Some(destination_height) = destination_profile_bone_height(
        destination_player,
        children,
        animation_targets,
        target_transforms,
        &retarget.scale_base_bone,
    ) else {
        return 1.0;
    };
    if source_height <= f32::EPSILON || destination_height <= f32::EPSILON {
        return 1.0;
    }
    (destination_height / source_height).clamp(0.05, 20.0)
}

fn source_profile_bone_height(
    source_paths: &[GltfAnimationTargetPath],
    profile_bone: &str,
) -> Option<f32> {
    let normalized_profile_bone = normalized_animation_name(profile_bone);
    source_paths
        .iter()
        .filter(|path| {
            path.path
                .last()
                .and_then(|name| humanoid_profile_bone(name))
                .is_some_and(|bone| normalized_animation_name(bone) == normalized_profile_bone)
        })
        .map(|path| translation_height(path.local_translation))
        .find(|height| *height > f32::EPSILON)
}

fn destination_profile_bone_height(
    root: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    target_transforms: &Query<&Transform, With<AnimationTargetId>>,
    profile_bone: &str,
) -> Option<f32> {
    let normalized_profile_bone = normalized_animation_name(profile_bone);
    for entity in children.iter_descendants(root) {
        let Ok((_, Some(name))) = animation_targets.get(entity) else {
            continue;
        };
        if !humanoid_profile_bone(name.as_str())
            .is_some_and(|bone| normalized_animation_name(bone) == normalized_profile_bone)
        {
            continue;
        }
        let Ok(transform) = target_transforms.get(entity) else {
            continue;
        };
        let height = translation_height(transform.translation);
        if height > f32::EPSILON {
            return Some(height);
        }
    }
    None
}

fn translation_height(translation: Vec3) -> f32 {
    let y = translation.y.abs();
    if y > f32::EPSILON {
        y
    } else {
        translation.length()
    }
}

fn retarget_translation_curve(
    curve: &VariableCurve,
    locked_axes: [bool; 3],
    motion_scale: f32,
) -> Option<VariableCurve> {
    if !is_transform_translation_curve(curve) {
        return None;
    }

    let domain = curve.0.domain();
    if !domain.is_bounded() {
        return None;
    }
    let start = domain.start();
    let end = domain.end();
    if !start.is_finite() || !end.is_finite() || end <= start {
        return None;
    }
    let scale = if motion_scale.is_finite() {
        motion_scale
    } else {
        1.0
    };
    if locked_axes.iter().all(|locked| !*locked) && (scale - 1.0).abs() <= f32::EPSILON {
        return None;
    }

    let base = sample_curve_vec3(curve, start)?;
    let span = end - start;
    let sample_count = ((span * 60.0).ceil() as usize).clamp(2, 240);
    let keyframes = (0..sample_count).map(|index| {
        let t = if sample_count == 1 {
            start
        } else {
            start + span * index as f32 / (sample_count - 1) as f32
        };
        let mut translation = sample_curve_vec3(curve, t).unwrap_or(base);
        translation = base + (translation - base) * scale;
        if locked_axes[0] {
            translation.x = base.x;
        }
        if locked_axes[1] {
            translation.y = base.y;
        }
        if locked_axes[2] {
            translation.z = base.z;
        }
        (t, translation)
    });
    let keyframe_curve = AnimatableKeyframeCurve::new(keyframes).ok()?;
    Some(VariableCurve::new(AnimatableCurve::new(
        animated_field!(Transform::translation),
        keyframe_curve,
    )))
}

fn is_transform_translation_curve(curve: &VariableCurve) -> bool {
    let translation_property = animated_field!(Transform::translation);
    curve.0.evaluator_id() == translation_property.evaluator_id()
}

fn is_transform_rotation_curve(curve: &VariableCurve) -> bool {
    let rotation_property = animated_field!(Transform::rotation);
    curve.0.evaluator_id() == rotation_property.evaluator_id()
}

fn sample_curve_vec3(curve: &VariableCurve, time: f32) -> Option<Vec3> {
    curve
        .0
        .sample_clamped(time)
        .downcast::<Vec3>()
        .ok()
        .map(|value| *value)
}

fn sample_curve_quat(curve: &VariableCurve, time: f32) -> Option<Quat> {
    curve
        .0
        .sample_clamped(time)
        .downcast::<Quat>()
        .ok()
        .map(|value| *value)
}

fn sample_vec3_curve_for_bake(curve: &VariableCurve) -> Vec<[f32; 4]> {
    let Some((start, end)) = bounded_curve_domain(curve) else {
        return Vec::new();
    };
    sampled_times(start, end)
        .filter_map(|time| {
            sample_curve_vec3(curve, time).map(|value| [time, value.x, value.y, value.z])
        })
        .collect()
}

fn sample_quat_curve_for_bake(
    curve: &VariableCurve,
    destination_base_rotation: Option<Quat>,
) -> Vec<[f32; 5]> {
    let Some((start, end)) = bounded_curve_domain(curve) else {
        return Vec::new();
    };
    let source_base_rotation = destination_base_rotation
        .and_then(|_| sample_curve_quat(curve, start))
        .map(Quat::normalize)
        .unwrap_or(Quat::IDENTITY);
    let destination_base_rotation = destination_base_rotation.unwrap_or(Quat::IDENTITY);
    sampled_times(start, end)
        .filter_map(|time| {
            sample_curve_quat(curve, time).map(|value| {
                let rotation = (destination_base_rotation
                    * source_base_rotation.inverse()
                    * value.normalize())
                .normalize();
                [time, rotation.x, rotation.y, rotation.z, rotation.w]
            })
        })
        .collect()
}

fn bounded_curve_domain(curve: &VariableCurve) -> Option<(f32, f32)> {
    let domain = curve.0.domain();
    if !domain.is_bounded() {
        return None;
    }
    let start = domain.start();
    let end = domain.end();
    (start.is_finite() && end.is_finite() && end >= start).then_some((start, end))
}

fn sampled_times(start: f32, end: f32) -> impl Iterator<Item = f32> {
    let span = end - start;
    let sample_count = ((span * 60.0).ceil() as usize).clamp(2, 240);
    (0..sample_count).map(move |index| {
        if sample_count == 1 {
            start
        } else {
            start + span * index as f32 / (sample_count - 1) as f32
        }
    })
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

#[derive(Debug, Clone)]
struct GltfAnimationTargetPath {
    path: Vec<String>,
    local_translation: Vec3,
}

fn collect_gltf_animation_target_paths(
    gltf: &Gltf,
    gltf_nodes: &Assets<GltfNode>,
) -> Vec<GltfAnimationTargetPath> {
    let mut paths = Vec::new();
    for node_handle in &gltf.nodes {
        let Some(node) = gltf_nodes.get(node_handle) else {
            continue;
        };
        if !node.is_animation_root {
            continue;
        }
        let mut path = Vec::new();
        collect_gltf_animation_target_paths_recursive(node, gltf_nodes, &mut path, &mut paths);
    }
    paths
}

fn collect_gltf_animation_target_paths_recursive(
    node: &GltfNode,
    gltf_nodes: &Assets<GltfNode>,
    path: &mut Vec<String>,
    paths: &mut Vec<GltfAnimationTargetPath>,
) {
    path.push(node.name.clone());
    paths.push(GltfAnimationTargetPath {
        path: path.clone(),
        local_translation: node.transform.translation,
    });
    for child_handle in &node.children {
        if let Some(child) = gltf_nodes.get(child_handle) {
            collect_gltf_animation_target_paths_recursive(child, gltf_nodes, path, paths);
        }
    }
    path.pop();
}

fn collect_animation_target_paths(
    root: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
) -> Vec<(Vec<String>, AnimationTargetId)> {
    let mut targets = Vec::new();
    let mut path = Vec::new();
    collect_animation_target_paths_recursive(
        root,
        children,
        animation_targets,
        &mut path,
        &mut targets,
    );
    targets
}

fn collect_animation_target_paths_recursive(
    entity: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    path: &mut Vec<String>,
    targets: &mut Vec<(Vec<String>, AnimationTargetId)>,
) {
    let Ok((target_id, name)) = animation_targets.get(entity) else {
        if let Ok(child_list) = children.get(entity) {
            for child in child_list {
                collect_animation_target_paths_recursive(
                    *child,
                    children,
                    animation_targets,
                    path,
                    targets,
                );
            }
        }
        return;
    };

    if let Some(name) = name {
        path.push(name.as_str().to_owned());
        targets.push((path.clone(), *target_id));
        if let Ok(child_list) = children.get(entity) {
            for child in child_list {
                collect_animation_target_paths_recursive(
                    *child,
                    children,
                    animation_targets,
                    path,
                    targets,
                );
            }
        }
        path.pop();
    }
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
    mut animations: Query<(
        &SceneMaxEntity,
        &mut CurrentAnimation,
        Option<&AnimationSpeedOverride>,
    )>,
) {
    let delta = time.delta_secs();
    for (entity, mut animation, speed_override) in &mut animations {
        let duration = animation.duration_seconds.max(0.001);
        let effective_speed = speed_override
            .map(|override_speed| override_speed.speed)
            .unwrap_or(animation.speed)
            .max(0.001);
        animation.elapsed_seconds += delta * effective_speed;
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

pub(super) fn restore_inactive_animation_visual_rotations(
    mut commands: Commands,
    children: Query<&Children>,
    mut transforms: Query<&mut Transform>,
    mut roots: Query<(Entity, &mut SceneMaxAnimationVisualTransform), Without<CurrentAnimation>>,
) {
    for (root, visual_transform) in &mut roots {
        if let Ok(child_list) = children.get(root) {
            for (child, base_transform) in &visual_transform.base_by_child {
                if child_list.contains(child)
                    && let Ok(mut transform) = transforms.get_mut(*child)
                {
                    *transform = *base_transform;
                }
            }
        }
        commands
            .entity(root)
            .remove::<SceneMaxAnimationVisualTransform>();
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
    mut roots: Query<
        (
            Entity,
            &mut AnimationSpeedOverride,
            Option<&CurrentAnimation>,
        ),
        With<SceneMaxEntity>,
    >,
    mut players: Query<&mut AnimationPlayer>,
) {
    for (root, mut speed_override, current_animation) in &mut roots {
        let player_entities = children.iter_descendants(root).collect::<Vec<_>>();
        let mut active_animation_count = 0;
        for player_entity in &player_entities {
            if let Ok(mut player) = players.get_mut(*player_entity) {
                active_animation_count +=
                    set_active_animation_speeds(&mut player, speed_override.speed);
            }
        }
        if active_animation_count > 0 && !speed_override.applied {
            speed_override.applied = true;
        }

        let Some(remaining_seconds) = speed_override.remaining_seconds.as_mut() else {
            commands.entity(root).remove::<AnimationSpeedOverride>();
            continue;
        };
        *remaining_seconds -= time.delta_secs();
        if *remaining_seconds <= 0.0 {
            let restore_speed = current_animation
                .map(|animation| animation.speed)
                .unwrap_or(1.0)
                .max(0.001);
            for player_entity in &player_entities {
                if let Ok(mut player) = players.get_mut(*player_entity) {
                    set_active_animation_speeds(&mut player, restore_speed);
                }
            }
            commands.entity(root).remove::<AnimationSpeedOverride>();
        }
    }
}

pub(super) fn set_active_animation_speeds(player: &mut AnimationPlayer, speed: f32) -> usize {
    let mut active_animation_count = 0;
    for (_, active_animation) in player.playing_animations_mut() {
        active_animation.set_speed(speed);
        active_animation_count += 1;
    }
    active_animation_count
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn humanoid_profile_matches_common_bone_aliases() {
        assert_eq!(humanoid_profile_bone("mixamorig:Hips"), Some("hips"));
        assert_eq!(
            humanoid_profile_bone("J_Bip_L_UpperArm"),
            Some("left_upper_arm")
        );
        assert_eq!(
            humanoid_profile_bone("Bip01 R ForeArm"),
            Some("right_lower_arm")
        );
        assert_eq!(humanoid_profile_bone("LeftToeBase"), Some("left_toes"));
        assert_eq!(humanoid_profile_bone("prop_socket"), None);
    }
}
