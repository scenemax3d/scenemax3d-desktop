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
    mut commands: Commands,
    mut effects: Query<(
        Entity,
        &SceneMaxEffekseerEffect,
        &mut SceneMaxEffekseerPlayback,
    )>,
) {
    let delta_seconds = time.delta_secs();
    for (entity, effect, mut playback) in &mut effects {
        if playback.looped {
            continue;
        }
        playback.elapsed_seconds += delta_seconds * playback.playback_speed.max(0.001);
        if playback.elapsed_seconds >= effect.one_shot_duration_seconds.max(0.1) {
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
