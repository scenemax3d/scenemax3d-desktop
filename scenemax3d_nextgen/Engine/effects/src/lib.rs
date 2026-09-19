//! Generic native Effekseer rendering shared by the projector and retained IDE previews.
use bevy::prelude::*;
use std::{
    path::PathBuf,
    sync::{Arc, Mutex, OnceLock},
};
mod bridge;
pub use bridge::{SceneMaxEffekseerBridgePlugin as EffectsPlugin, effekseer_renderer_label};
/// Source effect. Ownership and playback policy belong to the embedding application.
#[derive(Debug, Clone, Component)]
pub struct Effect {
    pub instance_id: u64,
    pub asset_id: String,
    pub effect_path: Option<PathBuf>,
    pub one_shot_duration_seconds: f32,
}
#[derive(Debug, Clone, Component)]
pub struct Playback {
    pub looped: bool,
    pub play_generation: u64,
    pub playback_speed: f32,
    pub dynamic_inputs: [f32; 4],
    pub elapsed_seconds: f32,
}
/// Optional editor-owned simulation clock; zero freezes particles while orbiting.
#[derive(Resource, Default)]
pub struct PreviewClock {
    pub delta_seconds: Option<f32>,
}
#[derive(Resource, Clone)]
pub struct Diagnostics(pub Arc<Mutex<String>>);
impl Default for Diagnostics {
    fn default() -> Self {
        Self(Arc::new(Mutex::new("Waiting for effect".into())))
    }
}
static LOG: OnceLock<fn(String)> = OnceLock::new();
/// Preserve application diagnostics without depending on the projector or IDE.
pub fn set_diagnostic_sink(sink: fn(String)) {
    let _ = LOG.set(sink);
}
#[cfg(feature = "effekseer_native")]
fn write_runtime_diagnostic_line(line: impl Into<String>) {
    let line = line.into();
    if let Some(sink) = LOG.get() {
        sink(line);
    } else {
        tracing::debug!("{line}");
    }
}
#[cfg(feature = "effekseer_native")]
fn runtime_verbose_logging() -> bool {
    std::env::var("SCENEMAX_RUNTIME_VERBOSE_LOG").is_ok_and(|v| {
        matches!(
            v.trim().to_ascii_lowercase().as_str(),
            "1" | "true" | "yes" | "on"
        )
    })
}

/// Native preview controls, intentionally separate from runtime playback defaults.
#[derive(Component, Clone, Copy, Debug)]
#[repr(C)]
pub struct PreviewOptions {
    pub seek_generation: u64,
    pub seek_frame: f32,
    pub seed: i32,
    pub target: [f32; 3],
    pub color: [u8; 4],
    pub triggers: [u64; 4],
}

#[cfg(test)]
mod tests {
    #[test]
    fn preview_options_match_the_native_abi() {
        use std::mem::{offset_of, size_of};
        assert_eq!(size_of::<super::PreviewOptions>(), 64);
        assert_eq!(offset_of!(super::PreviewOptions, triggers), 32);
        assert_eq!(offset_of!(super::PreviewOptions, color), 28);
    }
}
