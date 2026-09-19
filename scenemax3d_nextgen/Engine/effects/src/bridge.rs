use std::path::PathBuf;

#[cfg(feature = "effekseer_native")]
use std::{
    collections::{HashSet, hash_map::DefaultHasher},
    ffi::{CStr, c_void},
    hash::{Hash, Hasher},
};

#[cfg(feature = "effekseer_native")]
use ash::vk::Handle;

use bevy::{
    core_pipeline::{Core3d, upscaling::upscaling},
    prelude::*,
    render::{
        Extract, ExtractSchedule, Render, RenderApp,
        camera::ExtractedCamera,
        renderer::{RenderAdapterInfo, RenderContext, ViewQuery},
        view::{ExtractedView, ViewTarget},
    },
};

#[cfg(feature = "effekseer_native")]
use bevy::{
    core_pipeline::blit::{BlitPipeline, BlitPipelineKey},
    render::{render_resource::*, renderer::RenderAdapter},
};

#[cfg(feature = "effekseer_native")]
use crate::runtime_verbose_logging;

#[cfg(feature = "effekseer_native")]
use crate::write_runtime_diagnostic_line;

use crate::{Effect as SceneMaxEffekseerEffect, Playback as SceneMaxEffekseerPlayback};

pub struct SceneMaxEffekseerBridgePlugin;

impl Plugin for SceneMaxEffekseerBridgePlugin {
    fn build(&self, app: &mut App) {
        let diagnostics = crate::Diagnostics::default();
        app.insert_resource(diagnostics.clone());
        let Some(render_app) = app.get_sub_app_mut(RenderApp) else {
            return;
        };

        render_app.insert_resource(diagnostics);
        render_app
            .init_resource::<ExtractedEffekseerEffects>()
            .init_resource::<EffekseerBridgeState>()
            .add_systems(ExtractSchedule, extract_effekseer_effects)
            .add_systems(Core3d, render_effekseer_3d_pass.after(upscaling))
            .add_systems(
                Render,
                announce_effekseer_bridge_state.in_set(bevy::render::RenderSystems::Cleanup),
            );

        #[cfg(feature = "effekseer_native")]
        render_app
            .init_resource::<NativeEffekseerBridge>()
            .add_systems(
                Render,
                release_closed_preview.in_set(bevy::render::RenderSystems::Cleanup),
            );
    }
}

pub const fn effekseer_renderer_label() -> &'static str {
    if cfg!(feature = "effekseer_native") {
        "bevy-native"
    } else {
        "bevy-native-unbuilt"
    }
}

#[derive(Debug, Clone)]
#[allow(dead_code)]
struct ExtractedEffekseerInstance {
    instance_id: u64,
    asset_id: String,
    effect_path: Option<PathBuf>,
    translation: Vec3,
    playing: bool,
    looped: bool,
    play_generation: u64,
    playback_speed: f32,
    dynamic_inputs: [f32; 4],
    transform: [f32; 16],
    layers: bevy::camera::visibility::RenderLayers,
    preview: Option<crate::PreviewOptions>,
}

#[derive(Debug, Default, Resource)]
struct ExtractedEffekseerEffects {
    instances: Vec<ExtractedEffekseerInstance>,
    delta: Option<f32>,
}

#[derive(Debug, Default, Resource)]
struct EffekseerBridgeState {
    announced_backend: bool,
    announced_native_unbuilt: bool,
    #[cfg(feature = "effekseer_native")]
    native_had_playing_instances: bool,
}

#[allow(clippy::type_complexity)] // ECS extraction explicitly declares optional component access.
fn extract_effekseer_effects(
    mut extracted: ResMut<ExtractedEffekseerEffects>,
    clock: Extract<Option<Res<crate::PreviewClock>>>,
    effects: Extract<
        Query<(
            Entity,
            &SceneMaxEffekseerEffect,
            Option<&SceneMaxEffekseerPlayback>,
            Option<&GlobalTransform>,
            Option<&bevy::camera::visibility::RenderLayers>,
            Option<&crate::PreviewOptions>,
        )>,
    >,
) {
    extracted.delta = clock.as_ref().and_then(|c| c.delta_seconds);
    extracted.instances.clear();
    for (_entity, effect, playback, transform, layers, preview) in effects.iter() {
        let playback = playback.cloned();
        extracted.instances.push(ExtractedEffekseerInstance {
            layers: layers.cloned().unwrap_or_default(),
            preview: preview.copied(),
            instance_id: effect.instance_id,
            asset_id: effect.asset_id.clone(),
            effect_path: effect.effect_path.clone(),
            translation: transform
                .map(GlobalTransform::translation)
                .unwrap_or(Vec3::ZERO),
            playing: playback.is_some(),
            looped: playback.as_ref().is_some_and(|playback| playback.looped),
            play_generation: playback
                .as_ref()
                .map(|playback| playback.play_generation)
                .unwrap_or(0),
            playback_speed: playback
                .as_ref()
                .map(|playback| playback.playback_speed)
                .unwrap_or(1.0),
            dynamic_inputs: playback
                .as_ref()
                .map(|playback| playback.dynamic_inputs)
                .unwrap_or([0.0; 4]),
            transform: transform
                .map(|transform| effekseer_bridge_matrix_array(transform.to_matrix()))
                .unwrap_or_else(|| effekseer_bridge_matrix_array(Mat4::IDENTITY)),
        });
    }
}

fn announce_effekseer_bridge_state(
    adapter_info: Option<Res<RenderAdapterInfo>>,
    extracted: Res<ExtractedEffekseerEffects>,
    mut state: ResMut<EffekseerBridgeState>,
) {
    if extracted.instances.is_empty() {
        return;
    }

    if !state.announced_backend {
        if let Some(adapter_info) = adapter_info {
            tracing::info!(
                backend = %adapter_info.backend,
                renderer = effekseer_renderer_label(),
                "SceneMax Effekseer Bevy bridge detected render backend"
            );
        } else {
            tracing::info!(
                renderer = effekseer_renderer_label(),
                "SceneMax Effekseer Bevy bridge is waiting for render backend information"
            );
        }
        state.announced_backend = true;
    }

    if !cfg!(feature = "effekseer_native") && !state.announced_native_unbuilt {
        tracing::warn!(
            effects = extracted.instances.len(),
            "SceneMax Effekseer effects are queued for the Bevy bridge, but native Effekseer rendering is not enabled"
        );
        state.announced_native_unbuilt = true;
    }
}

#[allow(clippy::too_many_arguments)] // Bevy injects render-world resources independently.
fn render_effekseer_3d_pass(
    view: ViewQuery<(
        &ExtractedCamera,
        &ExtractedView,
        &ViewTarget,
        Option<&bevy::camera::visibility::RenderLayers>,
    )>,
    extracted: Res<ExtractedEffekseerEffects>,
    adapter_info: Option<Res<RenderAdapterInfo>>,
    #[cfg(feature = "effekseer_native")] render_adapter: Res<RenderAdapter>,
    #[cfg(feature = "effekseer_native")] blit_pipeline: Res<BlitPipeline>,
    #[cfg(feature = "effekseer_native")] mut blit_pipelines: ResMut<
        SpecializedRenderPipelines<BlitPipeline>,
    >,
    #[cfg(feature = "effekseer_native")] mut pipeline_cache: ResMut<PipelineCache>,
    #[cfg(feature = "effekseer_native")] time: Res<Time>,
    #[cfg(feature = "effekseer_native")] mut native: ResMut<NativeEffekseerBridge>,
    #[cfg(feature = "effekseer_native")] mut state: ResMut<EffekseerBridgeState>,
    diagnostics: Res<crate::Diagnostics>,
    ctx: RenderContext,
) {
    let (camera, extracted_view, target, layers) = view.into_inner();
    let layers = layers.cloned().unwrap_or_default();
    let instances: Vec<_> = extracted
        .instances
        .iter()
        .filter(|i| i.layers.intersects(&layers))
        .cloned()
        .collect();
    if instances.is_empty() {
        return;
    }
    #[cfg(not(feature = "effekseer_native"))]
    let _ = (camera, extracted_view, target);
    let playing_count = instances.iter().filter(|instance| instance.playing).count();

    #[cfg(feature = "effekseer_native")]
    {
        let mut ctx = ctx;
        let should_flush_stopped_native_handles =
            playing_count == 0 && state.native_had_playing_instances && !native.renderer.is_null();
        state.native_had_playing_instances = playing_count > 0;
        if playing_count == 0 && !should_flush_stopped_native_handles {
            return;
        }

        let backend = adapter_info
            .as_deref()
            .map(|info| info.backend.to_string())
            .unwrap_or_else(|| "unknown".to_owned());
        let result = render_effekseer_vulkan(
            &mut native,
            &render_adapter,
            &mut ctx,
            camera,
            extracted_view,
            target,
            &instances,
            extracted.delta.unwrap_or_else(|| time.delta_secs()),
        );
        if let Ok(mut message) = diagnostics.0.lock() {
            *message = if let Some(stats) = native_stats(native.renderer) {
                format!(
                    "{result:?} · {} particles · {} draw calls · {} vertices",
                    stats.total_instance_count, stats.draw_call_count, stats.draw_vertex_count
                )
            } else {
                format!("{result:?} · {playing_count} effect instance(s)")
            };
        }
        if result == NativeEffekseerRenderResult::Rendered {
            composite_effekseer_vulkan(
                &mut native,
                &mut ctx,
                target,
                &blit_pipeline,
                &mut blit_pipelines,
                &mut pipeline_cache,
            );
        }
        tracing::trace!(
            backend,
            ?result,
            instances = extracted.instances.len(),
            playing = playing_count,
            flushing_stopped = should_flush_stopped_native_handles,
            "SceneMax Effekseer Bevy render pass reached native handoff"
        );
    }

    #[cfg(not(feature = "effekseer_native"))]
    if playing_count == 0 {
        return;
    }

    #[cfg(not(feature = "effekseer_native"))]
    {
        let _ = diagnostics;
        let _ = adapter_info;
        let _ = ctx;
    }
}

#[cfg(feature = "effekseer_native")]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum NativeEffekseerRenderResult {
    Rendered,
    WaitingForVulkan,
    CreateFailed,
    MissingEffectPath,
    LoadFailed,
}

#[cfg(feature = "effekseer_native")]
#[derive(Debug, Default, Resource)]
struct NativeEffekseerBridge {
    renderer: *mut SceneMaxEffekseerRenderer,
    preview_owned: bool,
    loaded_effects: HashSet<u64>,
    composite_texture: Option<EffekseerCompositeTexture>,
    warned_create_failed: bool,
    warned_missing_vulkan: bool,
    render_log_counter: u64,
    composite_log_counter: u64,
    last_stats_signature: Option<NativeEffekseerStatsSignature>,
}

#[cfg(feature = "effekseer_native")]
#[derive(Debug)]
struct EffekseerCompositeTexture {
    view: TextureView,
    size: bevy::math::UVec2,
    format: TextureFormat,
}

#[cfg(feature = "effekseer_native")]
// SAFETY: the native object is exclusively owned by this render-world resource and transferred only with that world.
unsafe impl Send for NativeEffekseerBridge {}

#[cfg(feature = "effekseer_native")]
// SAFETY: every FFI access requires exclusive ResMut access in the render schedule; no shared native mutation is exposed.
unsafe impl Sync for NativeEffekseerBridge {}

#[cfg(feature = "effekseer_native")]
impl Drop for NativeEffekseerBridge {
    fn drop(&mut self) {
        if !self.renderer.is_null() {
            // SAFETY: this resource owns the non-null native renderer and destroys it exactly once.
            unsafe { scenemax_effekseer_destroy(self.renderer) };
            self.renderer = std::ptr::null_mut();
        }
    }
}

#[cfg(feature = "effekseer_native")]
#[allow(clippy::too_many_arguments)] // Native handoff keeps view, device and frame lifetimes explicit.
fn render_effekseer_vulkan(
    native: &mut NativeEffekseerBridge,
    render_adapter: &RenderAdapter,
    ctx: &mut RenderContext,
    camera: &ExtractedCamera,
    view: &ExtractedView,
    target: &ViewTarget,
    instances: &[ExtractedEffekseerInstance],
    delta_seconds: f32,
) -> NativeEffekseerRenderResult {
    let Some((target_format, color_format, depth_format)) =
        vulkan_formats_for_present_target(render_adapter, target)
    else {
        if !native.warned_missing_vulkan {
            tracing::warn!(
                "SceneMax Effekseer native bridge needs Bevy/wgpu to run on the Vulkan backend"
            );
            native.warned_missing_vulkan = true;
        }
        return NativeEffekseerRenderResult::WaitingForVulkan;
    };

    let preview_owned = instances.iter().any(|instance| instance.preview.is_some());
    if preview_owned
        && !native.loaded_effects.is_empty()
        && instances.iter().any(|instance| {
            !native
                .loaded_effects
                .contains(&effect_id_for_instance(instance))
        })
    {
        // A preview reload owns a fresh immutable package; release the previous GPU package.
        // Native Drop waits for the Vulkan device before releasing its resources.
        *native = NativeEffekseerBridge::default();
    }
    native.preview_owned = preview_owned;
    if native.renderer.is_null()
        && !create_native_vulkan_bridge(native, ctx, color_format, depth_format)
    {
        return NativeEffekseerRenderResult::CreateFailed;
    }

    let Some(target_size) = effekseer_target_size(camera, view) else {
        return NativeEffekseerRenderResult::WaitingForVulkan;
    };
    ensure_effekseer_composite_texture(native, ctx.render_device(), target_size, target_format);
    let Some(render_target) = vulkan_composite_render_target_for_view(native, view) else {
        return NativeEffekseerRenderResult::WaitingForVulkan;
    };

    let mut native_instances = Vec::new();
    for instance in instances {
        if let Some(options) = &instance.preview {
            // SAFETY: renderer is initialized; the C API copies the repr(C) options immediately.
            unsafe {
                scenemax_effekseer_preview_options(native.renderer, instance.instance_id, options);
            }
        }
    }
    for instance in instances.iter().filter(|instance| instance.playing) {
        let effect_id = effect_id_for_instance(instance);
        if !native.loaded_effects.contains(&effect_id) {
            let Some(path) = instance.effect_path.as_ref() else {
                return NativeEffekseerRenderResult::MissingEffectPath;
            };
            let path = path.to_string_lossy();
            // SAFETY: the renderer is initialized and the UTF-8 buffer stays alive for the synchronous load call.
            let loaded = unsafe {
                scenemax_effekseer_load_effect(
                    native.renderer,
                    effect_id,
                    path.as_bytes().as_ptr(),
                    path.len(),
                )
            };
            if !loaded {
                write_runtime_diagnostic_line(format!(
                    "EFFEKSEER:NATIVE_LOAD asset={} id={} ok=0 status={} path={}",
                    instance.asset_id,
                    effect_id,
                    native_status(native.renderer),
                    path
                ));
                return NativeEffekseerRenderResult::LoadFailed;
            }
            write_runtime_diagnostic_line(format!(
                "EFFEKSEER:NATIVE_LOAD asset={} id={} ok=1 status={} path={}",
                instance.asset_id,
                effect_id,
                native_status(native.renderer),
                path
            ));
            native.loaded_effects.insert(effect_id);
        }

        native_instances.push(SceneMaxEffekseerInstanceDesc {
            id: instance.instance_id,
            effect_id,
            play_generation: instance.play_generation,
            position: SceneMaxEffekseerVector3 {
                x: instance.translation.x,
                y: instance.translation.y,
                z: instance.translation.z,
            },
            transform: instance.transform,
            playback_speed: instance.playback_speed,
            looped: instance.looped,
            dynamic_inputs: instance.dynamic_inputs,
        });
    }

    if runtime_verbose_logging() && native.render_log_counter < 5 {
        for instance in instances.iter().filter(|instance| instance.playing).take(3) {
            let (scale, rotation, _) = Mat4::from_cols_array(&instance.transform)
                .transpose()
                .to_scale_rotation_translation();
            let (_, yaw, _) = rotation.to_euler(EulerRot::YXZ);
            write_runtime_diagnostic_line(format!(
                "EFFEKSEER:INSTANCE id={} asset={} pos=({:.3},{:.3},{:.3}) scale=({:.3},{:.3},{:.3}) yaw={:.3}",
                instance.instance_id,
                instance.asset_id,
                instance.translation.x,
                instance.translation.y,
                instance.translation.z,
                scale.x,
                scale.y,
                scale.z,
                yaw
            ));
        }
    }

    let view_from_world = view.world_from_view.to_matrix().inverse();
    let projection = effekseer_bridge_matrix_array(view.clip_from_view);
    let camera_view = effekseer_bridge_matrix_array(view_from_world);
    let camera_position = view.world_from_view.translation().to_array();

    // SAFETY: all slices and repr(C) buffers remain alive through synchronous native submission on the render thread.
    unsafe {
        scenemax_effekseer_render_vulkan_submit(
            native.renderer,
            &render_target,
            projection.as_ptr(),
            camera_view.as_ptr(),
            camera_position.as_ptr(),
            delta_seconds,
            native_instances.as_ptr(),
            native_instances.len(),
        );
    }

    log_native_effekseer_stats(
        native,
        NativeEffekseerRenderResult::Rendered,
        native_instances.len(),
    );
    NativeEffekseerRenderResult::Rendered
}

fn effekseer_bridge_matrix_array(matrix: Mat4) -> [f32; 16] {
    matrix.transpose().to_cols_array()
}

#[cfg(feature = "effekseer_native")]
fn composite_effekseer_vulkan(
    native: &mut NativeEffekseerBridge,
    ctx: &mut RenderContext,
    target: &ViewTarget,
    blit_pipeline: &BlitPipeline,
    blit_pipelines: &mut SpecializedRenderPipelines<BlitPipeline>,
    pipeline_cache: &mut PipelineCache,
) {
    let Some(texture) = native.composite_texture.as_ref() else {
        return;
    };
    let Some(target_format) = target.out_texture_view_format() else {
        return;
    };
    let Some(output) = target.out_texture() else {
        return;
    };

    let key = BlitPipelineKey {
        target_format,
        blend_state: Some(effekseer_composite_blend_state()),
        samples: 1,
        source_space: None,
    };
    let pipeline_id = blit_pipelines.specialize(pipeline_cache, blit_pipeline, key);
    let Some(pipeline) = pipeline_cache.get_render_pipeline(pipeline_id) else {
        return;
    };

    let bind_group =
        blit_pipeline.create_bind_group(ctx.render_device(), &texture.view, pipeline_cache);
    let mut render_pass = ctx
        .command_encoder()
        .begin_render_pass(&RenderPassDescriptor {
            label: Some("scenemax_effekseer_composite_pass"),
            color_attachments: &[Some(RenderPassColorAttachment {
                view: output,
                depth_slice: None,
                resolve_target: None,
                ops: Operations {
                    load: LoadOp::Load,
                    store: StoreOp::Store,
                },
            })],
            depth_stencil_attachment: None,
            timestamp_writes: None,
            occlusion_query_set: None,
            multiview_mask: None,
        });
    render_pass.set_pipeline(pipeline);
    render_pass.set_bind_group(0, &bind_group, &[]);
    render_pass.draw(0..3, 0..1);

    native.composite_log_counter = native.composite_log_counter.saturating_add(1);
    if native.composite_log_counter <= 3
        || (runtime_verbose_logging() && native.composite_log_counter.is_multiple_of(300))
    {
        write_runtime_diagnostic_line(format!(
            "EFFEKSEER:COMPOSITE ok=1 blend=additive-color target_format={:?} texture={}x{} calls={}",
            target_format, texture.size.x, texture.size.y, native.composite_log_counter
        ));
    }
}

#[cfg(feature = "effekseer_native")]
fn effekseer_composite_blend_state() -> BlendState {
    BlendState {
        color: BlendComponent {
            src_factor: BlendFactor::One,
            dst_factor: BlendFactor::One,
            operation: BlendOperation::Add,
        },
        alpha: BlendComponent {
            src_factor: BlendFactor::Zero,
            dst_factor: BlendFactor::One,
            operation: BlendOperation::Add,
        },
    }
}

#[cfg(feature = "effekseer_native")]
fn create_native_vulkan_bridge(
    native: &mut NativeEffekseerBridge,
    ctx: &RenderContext,
    color_format: u32,
    depth_format: u32,
) -> bool {
    // SAFETY: Vulkan device handles remain owned by Bevy for the render world lifetime; native creation borrows them.
    let created = unsafe {
        ctx.render_device()
            .wgpu_device()
            .as_hal::<wgpu::hal::api::Vulkan>()
            .and_then(|device| {
                let native_context = SceneMaxEffekseerVulkanContext {
                    physical_device: device.raw_physical_device().as_raw() as usize as *mut c_void,
                    device: device.raw_device().handle().as_raw() as usize as *mut c_void,
                    queue: device.raw_queue().as_raw() as usize as *mut c_void,
                    queue_family_index: device.queue_family_index(),
                    color_format,
                    depth_format,
                    frames_in_flight: 3,
                    sprite_count: 8000,
                };
                let renderer = scenemax_effekseer_create_vulkan(&native_context);
                (!renderer.is_null()).then_some(renderer)
            })
    };

    if let Some(renderer) = created {
        native.renderer = renderer;
        tracing::info!("SceneMax Effekseer native Vulkan bridge created");
        write_runtime_diagnostic_line(format!(
            "EFFEKSEER:NATIVE_CREATE ok=1 status={}",
            native_status(native.renderer)
        ));
        true
    } else {
        if !native.warned_create_failed {
            tracing::warn!("SceneMax Effekseer native Vulkan bridge creation failed");
            native.warned_create_failed = true;
        }
        write_runtime_diagnostic_line("EFFEKSEER:NATIVE_CREATE ok=0 status=create_failed");
        false
    }
}

#[cfg(feature = "effekseer_native")]
fn log_native_effekseer_stats(
    native: &mut NativeEffekseerBridge,
    result: NativeEffekseerRenderResult,
    submitted_instances: usize,
) {
    native.render_log_counter = native.render_log_counter.saturating_add(1);
    let Some(stats) = native_stats(native.renderer) else {
        if native.render_log_counter <= 3
            || (runtime_verbose_logging() && native.render_log_counter.is_multiple_of(300))
        {
            write_runtime_diagnostic_line(format!(
                "EFFEKSEER:NATIVE result={result:?} stats=missing submitted={submitted_instances} status={}",
                native_status(native.renderer)
            ));
        }
        return;
    };

    let signature = NativeEffekseerStatsSignature::from_stats(&stats);
    let verbose = runtime_verbose_logging();
    let should_log = native.render_log_counter <= 3
        || (verbose
            && (native.render_log_counter.is_multiple_of(300)
                || native.last_stats_signature != Some(signature)));
    if !should_log {
        return;
    }
    native.last_stats_signature = Some(signature);

    write_runtime_diagnostic_line(format!(
        "EFFEKSEER:NATIVE result={result:?} submitted={} loaded={} handles={}/{} total_instances={} draw_calls={} draw_vertices={} target={}x{} viewport={}x{} render_pass_ok={} begin_ok={} calls=render:{} play:{} load:{} retired_passes={} status={}",
        submitted_instances,
        stats.loaded_effect_count,
        stats.active_handle_count,
        stats.tracked_handle_count,
        stats.total_instance_count,
        stats.draw_call_count,
        stats.draw_vertex_count,
        stats.render_width,
        stats.render_height,
        stats.viewport_width,
        stats.viewport_height,
        stats.last_render_pass_ok as u8,
        stats.last_begin_rendering_ok as u8,
        stats.render_call_count,
        stats.play_call_count,
        stats.load_call_count,
        stats.retired_render_pass_count,
        native_status(native.renderer)
    ));
}

#[cfg(feature = "effekseer_native")]
fn native_status(renderer: *mut SceneMaxEffekseerRenderer) -> String {
    if renderer.is_null() {
        return "missing_renderer".to_owned();
    }
    // SAFETY: the live renderer owns the NUL-terminated status string; it is copied before another native call can modify it.
    unsafe {
        let status = scenemax_effekseer_status(renderer);
        if status.is_null() {
            "missing_status".to_owned()
        } else {
            CStr::from_ptr(status).to_string_lossy().into_owned()
        }
    }
}

#[cfg(feature = "effekseer_native")]
fn native_stats(renderer: *mut SceneMaxEffekseerRenderer) -> Option<SceneMaxEffekseerStats> {
    if renderer.is_null() {
        return None;
    }
    let mut stats = SceneMaxEffekseerStats::default();
    // SAFETY: renderer is non-null and the repr(C) output buffer is valid and exclusively borrowed.
    let ok = unsafe { scenemax_effekseer_get_stats(renderer, &mut stats) };
    ok.then_some(stats)
}

#[cfg(feature = "effekseer_native")]
fn vulkan_formats_for_present_target(
    render_adapter: &RenderAdapter,
    target: &ViewTarget,
) -> Option<(TextureFormat, u32, u32)> {
    // SAFETY: the backend-specific accessor checks the adapter type and the guard remains valid for this query.
    unsafe {
        render_adapter
            .as_hal::<wgpu::hal::api::Vulkan>()
            .and_then(|adapter| {
                let target_format = target.out_texture_view_format()?;
                let color_format = vulkan_format_for_texture_with_adapter(&adapter, target_format);
                Some((target_format, color_format, 0))
            })
    }
}

#[cfg(feature = "effekseer_native")]
fn vulkan_format_for_texture_with_adapter(
    adapter: &wgpu::hal::vulkan::Adapter,
    format: bevy::render::render_resource::TextureFormat,
) -> u32 {
    adapter.texture_format_as_raw(format).as_raw() as u32
}

#[cfg(feature = "effekseer_native")]
fn effekseer_target_size(
    camera: &ExtractedCamera,
    view: &ExtractedView,
) -> Option<bevy::math::UVec2> {
    let target_size = camera
        .physical_target_size
        .or(camera.physical_viewport_size)
        .unwrap_or_else(|| bevy::math::UVec2::new(view.viewport.z, view.viewport.w));
    if target_size.x == 0 || target_size.y == 0 {
        return None;
    }
    Some(target_size)
}

#[cfg(feature = "effekseer_native")]
fn ensure_effekseer_composite_texture(
    native: &mut NativeEffekseerBridge,
    render_device: &bevy::render::renderer::RenderDevice,
    size: bevy::math::UVec2,
    format: TextureFormat,
) {
    let needs_recreate = native
        .composite_texture
        .as_ref()
        .is_none_or(|texture| texture.size != size || texture.format != format);
    if !needs_recreate {
        return;
    }

    let texture = render_device.create_texture(&TextureDescriptor {
        label: Some("scenemax_effekseer_composite_texture"),
        size: Extent3d {
            width: size.x,
            height: size.y,
            depth_or_array_layers: 1,
        },
        mip_level_count: 1,
        sample_count: 1,
        dimension: TextureDimension::D2,
        format,
        usage: TextureUsages::RENDER_ATTACHMENT | TextureUsages::TEXTURE_BINDING,
        view_formats: &[],
    });
    let view = texture.create_view(&TextureViewDescriptor::default());
    native.composite_texture = Some(EffekseerCompositeTexture { view, size, format });
    write_runtime_diagnostic_line(format!(
        "EFFEKSEER:COMPOSITE_TEXTURE recreated=1 size={}x{} format={:?}",
        size.x, size.y, format
    ));
}

#[cfg(feature = "effekseer_native")]
fn vulkan_composite_render_target_for_view(
    native: &NativeEffekseerBridge,
    view: &ExtractedView,
) -> Option<SceneMaxEffekseerVulkanRenderTarget> {
    let texture = native.composite_texture.as_ref()?;
    // SAFETY: the typed HAL accessor returns None for non-Vulkan textures and retains the backing view guard.
    let color_view = unsafe { texture.view.as_hal::<wgpu::hal::api::Vulkan>()? };

    let viewport_width = view
        .viewport
        .z
        .min(texture.size.x.saturating_sub(view.viewport.x));
    let viewport_height = view
        .viewport
        .w
        .min(texture.size.y.saturating_sub(view.viewport.y));
    if viewport_width == 0 || viewport_height == 0 {
        return None;
    }

    Some(SceneMaxEffekseerVulkanRenderTarget {
        // SAFETY: color_view guards the Vulkan view through target construction; the owning texture lives through submission.
        color_image_view: unsafe { color_view.raw_handle().as_raw() as usize as *mut c_void },
        depth_image_view: std::ptr::null_mut(),
        width: texture.size.x,
        height: texture.size.y,
        viewport_x: view.viewport.x,
        viewport_y: view.viewport.y,
        viewport_width,
        viewport_height,
        clear_color: 1,
        final_shader_read: 1,
    })
}

#[cfg(feature = "effekseer_native")]
fn effect_id_for_instance(instance: &ExtractedEffekseerInstance) -> u64 {
    let mut hasher = DefaultHasher::new();
    instance.asset_id.hash(&mut hasher);
    if let Some(path) = &instance.effect_path {
        path.hash(&mut hasher);
    }
    hasher.finish().max(1)
}

#[repr(C)]
#[allow(dead_code)]
pub(crate) struct SceneMaxEffekseerVector3 {
    pub x: f32,
    pub y: f32,
    pub z: f32,
}

#[repr(C)]
#[allow(dead_code)]
pub(crate) struct SceneMaxEffekseerInstanceDesc {
    pub id: u64,
    pub effect_id: u64,
    pub play_generation: u64,
    pub position: SceneMaxEffekseerVector3,
    pub transform: [f32; 16],
    pub playback_speed: f32,
    pub looped: bool,
    pub dynamic_inputs: [f32; 4],
}

#[cfg(feature = "effekseer_native")]
#[repr(C)]
#[allow(dead_code)]
pub(crate) struct SceneMaxEffekseerVulkanContext {
    pub physical_device: *mut c_void,
    pub device: *mut c_void,
    pub queue: *mut c_void,
    pub queue_family_index: u32,
    pub color_format: u32,
    pub depth_format: u32,
    pub frames_in_flight: u32,
    pub sprite_count: i32,
}

#[cfg(feature = "effekseer_native")]
#[repr(C)]
#[allow(dead_code)]
pub(crate) struct SceneMaxEffekseerVulkanRenderTarget {
    pub color_image_view: *mut c_void,
    pub depth_image_view: *mut c_void,
    pub width: u32,
    pub height: u32,
    pub viewport_x: u32,
    pub viewport_y: u32,
    pub viewport_width: u32,
    pub viewport_height: u32,
    pub clear_color: u32,
    pub final_shader_read: u32,
}

#[cfg(feature = "effekseer_native")]
#[repr(C)]
#[derive(Debug, Default, Clone, Copy)]
#[allow(dead_code)]
pub(crate) struct SceneMaxEffekseerStats {
    pub loaded_effect_count: u32,
    pub tracked_handle_count: u32,
    pub active_handle_count: u32,
    pub total_instance_count: i32,
    pub draw_call_count: i32,
    pub draw_vertex_count: i32,
    pub render_width: u32,
    pub render_height: u32,
    pub viewport_width: u32,
    pub viewport_height: u32,
    pub retired_render_pass_count: u32,
    pub render_call_count: u64,
    pub play_call_count: u64,
    pub load_call_count: u64,
    pub last_render_pass_ok: bool,
    pub last_begin_rendering_ok: bool,
}

#[cfg(feature = "effekseer_native")]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct NativeEffekseerStatsSignature {
    active_handle_count: u32,
    total_instance_count: i32,
    draw_call_count: i32,
    draw_vertex_count: i32,
    last_render_pass_ok: bool,
    last_begin_rendering_ok: bool,
}

#[cfg(feature = "effekseer_native")]
impl NativeEffekseerStatsSignature {
    fn from_stats(stats: &SceneMaxEffekseerStats) -> Self {
        Self {
            active_handle_count: stats.active_handle_count,
            total_instance_count: stats.total_instance_count,
            draw_call_count: stats.draw_call_count,
            draw_vertex_count: stats.draw_vertex_count,
            last_render_pass_ok: stats.last_render_pass_ok,
            last_begin_rendering_ok: stats.last_begin_rendering_ok,
        }
    }
}

#[cfg(feature = "effekseer_native")]
#[repr(C)]
#[allow(dead_code)]
pub(crate) struct SceneMaxEffekseerDx12Context {
    pub device: *mut c_void,
    pub command_queue: *mut c_void,
    pub color_format: u32,
    pub depth_format: u32,
    pub frames_in_flight: u32,
    pub sprite_count: i32,
}

#[cfg(feature = "effekseer_native")]
#[repr(C)]
#[allow(dead_code)]
pub(crate) struct SceneMaxEffekseerRenderer {
    _private: [u8; 0],
}

#[cfg(feature = "effekseer_native")]
#[allow(dead_code)]
unsafe extern "C" {
    pub(crate) fn scenemax_effekseer_create_vulkan(
        context: *const SceneMaxEffekseerVulkanContext,
    ) -> *mut SceneMaxEffekseerRenderer;

    pub(crate) fn scenemax_effekseer_create_dx12(
        context: *const SceneMaxEffekseerDx12Context,
    ) -> *mut SceneMaxEffekseerRenderer;

    pub(crate) fn scenemax_effekseer_destroy(renderer: *mut SceneMaxEffekseerRenderer);

    pub(crate) fn scenemax_effekseer_load_effect(
        renderer: *mut SceneMaxEffekseerRenderer,
        id: u64,
        path_utf8: *const u8,
        path_len: usize,
    ) -> bool;

    pub(crate) fn scenemax_effekseer_play(
        renderer: *mut SceneMaxEffekseerRenderer,
        effect_id: u64,
        instance: *const SceneMaxEffekseerInstanceDesc,
    ) -> u64;

    pub(crate) fn scenemax_effekseer_render_vulkan(
        renderer: *mut SceneMaxEffekseerRenderer,
        command_buffer: *mut c_void,
        target: *const SceneMaxEffekseerVulkanRenderTarget,
        projection_column_major: *const f32,
        camera_view_column_major: *const f32,
        camera_position: *const f32,
        delta_seconds: f32,
        instances: *const SceneMaxEffekseerInstanceDesc,
        instance_count: usize,
    );

    pub(crate) fn scenemax_effekseer_render_vulkan_submit(
        renderer: *mut SceneMaxEffekseerRenderer,
        target: *const SceneMaxEffekseerVulkanRenderTarget,
        projection_column_major: *const f32,
        camera_view_column_major: *const f32,
        camera_position: *const f32,
        delta_seconds: f32,
        instances: *const SceneMaxEffekseerInstanceDesc,
        instance_count: usize,
    );

    pub(crate) fn scenemax_effekseer_render_dx12(
        renderer: *mut SceneMaxEffekseerRenderer,
        command_list: *mut c_void,
        projection_column_major: *const f32,
        camera_view_column_major: *const f32,
        instances: *const SceneMaxEffekseerInstanceDesc,
        instance_count: usize,
    );

    pub(crate) fn scenemax_effekseer_status(
        renderer: *mut SceneMaxEffekseerRenderer,
    ) -> *const std::ffi::c_char;

    pub(crate) fn scenemax_effekseer_get_stats(
        renderer: *mut SceneMaxEffekseerRenderer,
        out_stats: *mut SceneMaxEffekseerStats,
    ) -> bool;
}

#[cfg(feature = "effekseer_native")]
unsafe extern "C" {
    fn scenemax_effekseer_preview_options(
        renderer: *mut SceneMaxEffekseerRenderer,
        id: u64,
        options: *const crate::PreviewOptions,
    );
}

#[cfg(feature = "effekseer_native")]
fn release_closed_preview(
    mut native: ResMut<NativeEffekseerBridge>,
    extracted: Res<ExtractedEffekseerEffects>,
) {
    if native.preview_owned
        && extracted
            .instances
            .iter()
            .all(|instance| instance.preview.is_none())
    {
        *native = NativeEffekseerBridge::default();
    }
}
#[cfg(all(test, feature = "effekseer_native"))]
mod lifecycle_tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[test]
    #[allow(clippy::field_reassign_with_default)] // Struct update would move fields out of a Drop owner.
    fn closing_last_preview_releases_its_cached_package() {
        let mut world = World::new();
        world.insert_resource(ExtractedEffekseerEffects::default());
        let mut native = NativeEffekseerBridge::default();
        native.preview_owned = true;
        native.loaded_effects.insert(7);
        world.insert_resource(native);
        world.run_system_once(release_closed_preview).unwrap();
        let native = world.resource::<NativeEffekseerBridge>();
        assert!(!native.preview_owned);
        assert!(native.loaded_effects.is_empty());
    }
}
