use crate::startup::write_runtime_diagnostic_line;
use crate::{SceneMaxEffekseerEffect, SceneMaxEffekseerPlayback};
use bevy::prelude::*;
pub(crate) use scenemax_effects::effekseer_renderer_label;
pub(crate) struct SceneMaxEffekseerBridgePlugin;
impl Plugin for SceneMaxEffekseerBridgePlugin {
    fn build(&self, app: &mut App) {
        scenemax_effects::set_diagnostic_sink(|line| write_runtime_diagnostic_line(line));
        app.add_plugins(scenemax_effects::EffectsPlugin);
    }
}
pub(crate) fn update_effekseer_playbacks(
    time: Res<Time>,
    completions: Option<Res<scenemax_effects::PlaybackCompletions>>,
    mut commands: Commands,
    mut effects: Query<(
        Entity,
        &SceneMaxEffekseerEffect,
        &mut SceneMaxEffekseerPlayback,
        Option<&crate::multiplayer::EffectAction>,
        Option<&crate::NetworkEffectResume>,
    )>,
) {
    let delta_seconds = time.delta_secs();
    for (entity, effect, mut playback, action, resume) in &mut effects {
        if let Some(resume) = resume {
            if action.is_some_and(|action| {
                action.generation == playback.play_generation
                    && action.owner.as_ref() == Some(&resume.0.owner)
            }) {
                if !playback.looped && effect.one_shot_duration_seconds.is_finite() {
                    playback.elapsed_seconds = resume.0.seek_seconds(
                        effect.one_shot_duration_seconds.max(0.1),
                        playback.playback_speed,
                        web_time::Instant::now(),
                    );
                    tracing::debug!(
                        ?entity,
                        elapsed = playback.elapsed_seconds,
                        "resumed numbered remote effect"
                    );
                }
                commands
                    .entity(entity)
                    .remove::<crate::NetworkEffectResume>();
            }
        }
        if playback.looped {
            continue;
        }
        if completions
            .as_ref()
            .is_some_and(|feedback| feedback.contains(effect.instance_id, playback.play_generation))
        {
            commands
                .entity(entity)
                .remove::<SceneMaxEffekseerPlayback>();
            write_runtime_diagnostic_line(format!(
                "EFFEKSEER:STOP_NATIVE id={} generation={}",
                effect.instance_id, playback.play_generation
            ));
            continue;
        }
        playback.elapsed_seconds += delta_seconds * playback.playback_speed.max(0.001);
        if effect.one_shot_duration_seconds.is_finite()
            && playback.elapsed_seconds >= effect.one_shot_duration_seconds.max(0.1)
        {
            commands
                .entity(entity)
                .remove::<SceneMaxEffekseerPlayback>();
            write_runtime_diagnostic_line(format!(
                "EFFEKSEER:STOP_ONESHOT id={} asset={} elapsed={} duration={}",
                effect.instance_id,
                effect.asset_id,
                scenemax_runtime_vm_core::format_scenemax_number(playback.elapsed_seconds),
                scenemax_runtime_vm_core::format_scenemax_number(effect.one_shot_duration_seconds)
            ));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn unbounded_and_dynamic_effects_wait_for_their_native_generation_completion() {
        let mut app = App::new();
        app.init_resource::<Time>()
            .init_resource::<scenemax_effects::PlaybackCompletions>()
            .add_systems(Update, update_effekseer_playbacks);
        let entity = app
            .world_mut()
            .spawn((
                SceneMaxEffekseerEffect {
                    instance_id: 42,
                    asset_id: "fixture".into(),
                    effect_path: None,
                    one_shot_duration_seconds: f32::INFINITY,
                },
                SceneMaxEffekseerPlayback {
                    looped: false,
                    play_generation: 7,
                    playback_speed: 1.,
                    dynamic_inputs: [2., 0., 0., 0.],
                    elapsed_seconds: 100.,
                },
            ))
            .id();
        app.world_mut()
            .resource_mut::<scenemax_effects::PlaybackCompletions>()
            .0
            .lock()
            .unwrap()
            .insert((42, 6));
        app.update();
        assert!(
            app.world()
                .get::<SceneMaxEffekseerPlayback>(entity)
                .is_some()
        );
        app.world_mut()
            .resource_mut::<scenemax_effects::PlaybackCompletions>()
            .0
            .lock()
            .unwrap()
            .insert((42, 7));
        app.update();
        assert!(
            app.world()
                .get::<SceneMaxEffekseerPlayback>(entity)
                .is_none()
        );
    }
}
