//! Same-device WebGPU backend. Browser code owns asynchronous asset loading.
use super::*;
use bevy::{
    app::SubApp,
    core_pipeline::{core_3d::main_transparent_pass_3d, tonemapping::tonemapping},
    render::{render_resource::*, renderer::RenderDevice, view::ViewDepthStencilTexture},
};
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = scenemaxEffects, catch, js_name = background)]
    fn background_browser(
        encoder: &JsValue,
        source: &JsValue,
        viewport: &[u32],
        format: &str,
    ) -> Result<(), JsValue>;
    #[wasm_bindgen(js_namespace = scenemaxEffects, catch, js_name = sync)]
    fn sync_browser(device: &JsValue, state: &str, delta: f32) -> Result<String, JsValue>;
    #[wasm_bindgen(js_namespace = scenemaxEffects, catch, js_name = draw)]
    fn draw_browser(
        pass: &JsValue,
        projection: &[f32],
        camera: &[f32],
        ids: &str,
        format: &str,
    ) -> Result<(), JsValue>;
}

#[derive(Resource, Default)]
struct BrowserState {
    initialized: bool,
    failed: bool,
}

pub(super) fn install(app: &mut SubApp) {
    app.init_resource::<BrowserState>()
        .add_systems(
            Render,
            synchronize.in_set(bevy::render::RenderSystems::PrepareResources),
        )
        .add_systems(
            Core3d,
            render.after(main_transparent_pass_3d).before(tonemapping),
        );
}

// The pinned external-pass API exposes no sample count. Keep color and depth
// single-sampled together. Native cameras and projects without effects are untouched.
pub(super) fn configure_cameras(
    effects: Query<(), With<SceneMaxEffekseerEffect>>,
    mut cameras: Query<&mut Msaa, With<Camera3d>>,
) {
    if effects.is_empty() {
        return;
    }
    for mut msaa in &mut cameras {
        if *msaa != Msaa::Off {
            *msaa = Msaa::Off;
        }
    }
}

fn synchronize(
    extracted: Res<ExtractedEffekseerEffects>,
    device: Res<RenderDevice>,
    time: Res<Time>,
    completions: Res<crate::PlaybackCompletions>,
    diagnostics: Res<crate::Diagnostics>,
    mut state: ResMut<BrowserState>,
) {
    if state.failed || (!state.initialized && extracted.instances.is_empty()) {
        return;
    }
    let Some(device) = device.wgpu_device().browser_device() else {
        return;
    };
    state.initialized = true;
    let snapshot: Vec<_> = extracted.instances.iter().map(|i| serde_json::json!({
        "id":i.instance_id.to_string(), "generation":i.play_generation.to_string(),
        "path":i.effect_path.as_ref().map(|p| p.to_string_lossy().replace('\\', "/")).unwrap_or_default(),
        "playing":i.playing, "looped":i.looped, "speed":i.playback_speed,
        "elapsed":i.elapsed_seconds, "inputs":i.dynamic_inputs,
        "matrix":Mat4::from_cols_array(&i.transform).transpose().to_cols_array(),
    })).collect();
    let result = sync_browser(
        device,
        &serde_json::to_string(&snapshot).unwrap_or_default(),
        extracted.delta.unwrap_or_else(|| time.delta_secs()),
    );
    match result {
        Ok(json) => {
            if let Ok(finished) = serde_json::from_str::<Vec<(String, String)>>(&json) {
                if let Ok(mut entries) = completions.0.lock() {
                    for (id, generation) in finished {
                        if let (Ok(id), Ok(generation)) = (id.parse(), generation.parse()) {
                            // Independently validate browser feedback against this frame.
                            if extracted.instances.iter().any(|i| {
                                i.instance_id == id
                                    && i.play_generation == generation
                                    && i.playing
                                    && !i.looped
                            }) {
                                entries.insert((id, generation));
                            }
                        }
                    }
                }
            }
        }
        Err(error) => {
            state.failed = true;
            let message = format!("Effekseer browser bridge failed: {error:?}");
            error!("{message}");
            if let Ok(mut diagnostics) = diagnostics.0.lock() {
                *diagnostics = message;
            }
        }
    }
}

fn render(
    view: ViewQuery<(
        &ExtractedCamera,
        &ExtractedView,
        &ViewTarget,
        &ViewDepthStencilTexture,
        Option<&bevy::camera::visibility::RenderLayers>,
    )>,
    extracted: Res<ExtractedEffekseerEffects>,
    state: Res<BrowserState>,
    mut ctx: RenderContext,
) {
    if !state.initialized || state.failed {
        return;
    }
    let (camera, view, target, depth, layers) = view.into_inner();
    let format = match target.main_texture_format() {
        TextureFormat::Rgba8Unorm => "rgba8unorm",
        TextureFormat::Rgba8UnormSrgb => "rgba8unorm-srgb",
        TextureFormat::Bgra8Unorm => "bgra8unorm",
        TextureFormat::Bgra8UnormSrgb => "bgra8unorm-srgb",
        TextureFormat::Rg11b10Ufloat => "rg11b10ufloat",
        _ => "unsupported",
    };
    let layers = layers.cloned().unwrap_or_default();
    let ids: Vec<_> = extracted
        .instances
        .iter()
        .filter(|i| i.playing && i.layers.intersects(&layers))
        .map(|i| i.instance_id.to_string())
        .collect();
    if ids.is_empty() {
        return;
    }
    let size = target.main_texture().size();
    let (origin, extent) = camera
        .viewport
        .as_ref()
        .map(|v| (v.physical_position, v.physical_size))
        .unwrap_or((UVec2::ZERO, UVec2::new(size.width, size.height)));
    if let (Some(encoder), Some(source)) = (
        ctx.command_encoder().browser_command_encoder(),
        target.main_texture_view().browser_texture_view(),
    ) {
        if let Err(error) = background_browser(
            encoder,
            source,
            &[origin.x, origin.y, extent.x, extent.y],
            format,
        ) {
            error!(?error, "Effekseer background capture failed");
            return;
        }
    }
    let mut pass = ctx
        .command_encoder()
        .begin_render_pass(&RenderPassDescriptor {
            label: Some("SceneMax effects"),
            color_attachments: &[Some(target.get_color_attachment())],
            depth_stencil_attachment: Some(depth.get_attachment(StoreOp::Store)),
            ..default()
        });
    if let Some(viewport) = &camera.viewport {
        pass.set_viewport(
            viewport.physical_position.x as f32,
            viewport.physical_position.y as f32,
            viewport.physical_size.x as f32,
            viewport.physical_size.y as f32,
            viewport.depth.start,
            viewport.depth.end,
        );
    }
    if let Some(raw) = pass.browser_render_pass() {
        if let Err(error) = draw_browser(
            raw,
            &view.clip_from_view.to_cols_array(),
            &view.world_from_view.to_matrix().inverse().to_cols_array(),
            &serde_json::to_string(&ids).unwrap_or_default(),
            format,
        ) {
            error!(?error, "Effekseer browser draw failed");
        }
    }
}
