//! Animation graph binding, transport, scrubbing and bind-pose restoration.
use super::*;
#[allow(clippy::type_complexity)] // Bevy query tuples encode disjoint ECS access.
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Playback<'w, 's> {
    players: Query<
        'w,
        's,
        (
            Entity,
            &'static mut AnimationPlayer,
            Option<&'static AnimationGraphHandle>,
        ),
    >,
    parents: Query<'w, 's, &'static ChildOf>,
    rests: Query<'w, 's, (Entity, &'static render::Rest)>,
    choices: Query<
        'w,
        's,
        (
            &'static view::ClipPick,
            &'static mut property::Choice,
            &'static Children,
        ),
    >,
    scrubs: Query<'w, 's, (&'static view::Scrub, &'static EditableText)>,
    gltfs: Option<Res<'w, Assets<bevy::gltf::Gltf>>>,
    clips: Option<Res<'w, Assets<AnimationClip>>>,
    texts: Query<'w, 's, &'static mut Text>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut states: Query<(Entity, &EditorHost, &mut Import)>,
    session: Res<Session>,
    mut view: Playback,
    mut previous_mode: Local<Option<bevy::winit::UpdateMode>>,
) {
    let mut running = false;
    for (host, owner, mut state) in &mut states {
        if session.workspace.active_id() != Some(owner.0) {
            continue;
        }
        let Some(root) = state.world else {
            continue;
        };
        let Ok(doc) = session.workspace.document(owner.0) else {
            continue;
        };
        let Ok(draft) = serde_json::from_str::<Value>(doc.text()) else {
            continue;
        };
        if let Some((_, choice, _)) = view.choices.iter().find(|(pick, _, _)| pick.0 == host)
            && state.chosen != choice.0
        {
            state.chosen = choice.0.clone();
            state.clip = choice.0.parse().ok();
            state.restart = state.clip.is_some();
            state.playing = state.clip.is_some();
            state.stop = state.clip.is_none();
        }
        if let Some((_, input)) = view.scrubs.iter().find(|(scrub, _)| scrub.0 == host) {
            let value = input.value().to_string();
            if value != state.last_scrub {
                state.last_scrub = value.clone();
                if let Ok(p) = value.parse::<f32>() {
                    state.seek = Some(p.clamp(0., 100.));
                    if state.clip.is_none() && !state.clips.is_empty() {
                        state.clip = Some(0);
                    }
                    state.restart = true;
                    state.playing = false;
                }
            }
        }
        if state.restart && state.clip.is_none() && !state.clips.is_empty() {
            state.clip = Some(0);
        }
        if let Some((_, mut choice, children)) =
            view.choices.iter_mut().find(|(pick, _, _)| pick.0 == host)
        {
            let chosen = state.clip.map(|i| i.to_string()).unwrap_or_default();
            if choice.0 != chosen {
                choice.0 = chosen.clone();
                state.chosen = chosen;
                let title = state
                    .clip
                    .and_then(|i| state.prepared.as_ref()?.clips.get(i))
                    .map(String::as_str)
                    .unwrap_or("Bind pose");
                for child in children.iter() {
                    if let Ok(mut text) = view.texts.get_mut(child) {
                        text.0 = format!("{title} ↓");
                    }
                }
            }
        }
        let duration = state
            .asset
            .as_ref()
            .and_then(|h| view.gltfs.as_ref()?.get(h))
            .and_then(|g| g.animations.get(state.clip?))
            .and_then(|h| view.clips.as_ref()?.get(h))
            .map_or(0., AnimationClip::duration);
        let Some(graph) = state.graph.clone() else {
            continue;
        };
        let node = state.clip.and_then(|i| state.clips.get(i).copied());
        let mut time = 0.;
        let mut found = false;
        for (entity, mut player, bound) in &mut view.players {
            if !view.parents.iter_ancestors(entity).any(|p| p == root) {
                continue;
            }
            found = true;
            if bound.is_none() {
                commands
                    .entity(entity)
                    .insert(AnimationGraphHandle(graph.clone()));
            }
            if state.stop {
                player.stop_all();
                continue;
            }
            if let Some(node) = node {
                if state.restart || (state.playing && player.animation(node).is_none()) {
                    player.stop_all();
                    player.play(node);
                }
                for (_, animation) in player.playing_animations_mut() {
                    animation.set_speed(render::number(&draft["preview"], "speed"));
                    animation.set_repeat(if draft["preview"]["loop"].as_bool() == Some(true) {
                        bevy::animation::RepeatAnimation::Forever
                    } else {
                        bevy::animation::RepeatAnimation::Never
                    });
                    if state.playing {
                        animation.resume();
                    } else {
                        animation.pause();
                    }
                    if let Some(percent) = state.seek {
                        animation.seek_to(duration * percent / 100.);
                    }
                    time = animation.seek_time();
                }
            }
        }
        if state.stop {
            for (entity, rest) in &view.rests {
                if view.parents.iter_ancestors(entity).any(|p| p == root) {
                    commands.entity(entity).insert(rest.0);
                }
            }
        }
        if found {
            state.restart = false;
            state.stop = false;
            state.seek = None;
        }
        running |= state.playing && found;
        if let Some(parts) = state.parts
            && let Ok(mut text) = view.texts.get_mut(parts.timeline)
        {
            let caption = format!("{time:.2} / {duration:.2} s");
            if text.0 != caption {
                text.0 = caption;
            }
        }
    }
    if let Some(winit) = view.winit.as_deref_mut() {
        if running {
            if previous_mode.is_none() {
                *previous_mode = Some(winit.focused_mode);
            }
            winit.focused_mode = bevy::winit::UpdateMode::Continuous;
        } else if let Some(mode) = previous_mode.take() {
            winit.focused_mode = mode;
        }
    }
}
