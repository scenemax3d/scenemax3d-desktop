use super::*;
#[cfg(feature = "effekseer_native")]
use bevy::render::{
    RenderPlugin,
    settings::{Backends, WgpuSettings},
};
use bevy::{
    input::mouse::{AccumulatedMouseMotion, MouseScrollUnit, MouseWheel},
    ui::RelativeCursorPosition,
    window::PrimaryWindow,
};
use serde::{Deserialize, Serialize};
use std::time::Instant;

const PANEL_WIDTH: f32 = 380.0;
const TEXT_MAIN: Color = Color::srgb(0.92, 0.96, 1.0);
const TEXT_MUTED: Color = Color::srgb(0.62, 0.68, 0.76);
const TEXT_ACCENT: Color = Color::srgb(0.42, 0.78, 1.0);
const PANEL_BG: Color = Color::srgba(0.055, 0.07, 0.09, 0.94);
const BUTTON_BG: Color = Color::srgba(0.12, 0.16, 0.20, 0.96);
const BUTTON_HOVER: Color = Color::srgba(0.18, 0.24, 0.30, 0.98);
const BUTTON_ACTIVE: Color = Color::srgba(0.12, 0.36, 0.50, 1.0);
const SLIDER_TRACK: Color = Color::srgb(0.090, 0.105, 0.125);
const SLIDER_THUMB: Color = Color::srgb(0.930, 0.985, 1.0);
const BRIGHTNESS_MAX: f32 = 2000.0;
const SCROLL_LINE_HEIGHT: f32 = 28.0;

#[derive(Debug, Clone)]
pub struct BevyAmbientLightDesignerLaunch {
    pub design: PathBuf,
    pub project_root: PathBuf,
    pub camera_snapshot: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct BevyAmbientLightSettings {
    #[serde(default)]
    configured: bool,
    #[serde(default)]
    enabled: bool,
    #[serde(default = "default_ambient_name")]
    name: String,
    #[serde(default = "default_ambient_color")]
    color: String,
    #[serde(default = "default_ambient_brightness")]
    brightness: f32,
    #[serde(default = "default_affects_lightmapped_meshes")]
    affects_lightmapped_meshes: bool,
}

#[derive(Debug, Resource)]
struct AmbientLightDesignerState {
    design_path: PathBuf,
    asset_root: PathBuf,
    source: String,
    settings: BevyAmbientLightSettings,
    status: String,
    scene_bounds: DesignerSceneBounds,
    initial_ide_camera: Option<DesignerCameraOrbit>,
    camera_target: Vec3,
    camera_distance: f32,
    camera_yaw: f32,
    camera_pitch: f32,
    scene_load_delay_frames: u8,
    scene_loaded: bool,
}

#[derive(Debug, Resource)]
struct AmbientLightLastAppliedSettings(Option<BevyAmbientLightSettings>);

#[derive(Debug, Clone, Copy, PartialEq, Component)]
enum AmbientLightDesignerAction {
    ToggleEnabled,
    Brightness(i32),
    Color(usize, i32),
    AdjustSlider(AmbientLightSliderParam, f32),
    Preset(usize),
    ToggleLightmappedMeshes,
    RestoreIdeView,
    FrameScene,
    ViewPreset(AmbientLightDesignerViewPreset),
    Reset,
    Save,
    Close,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Component)]
enum AmbientLightDesignerViewPreset {
    Front,
    Right,
    Top,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Component)]
enum AmbientLightSliderParam {
    Brightness,
    Red,
    Green,
    Blue,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Component)]
enum AmbientLightDesignerValue {
    Enabled,
    Brightness,
    Color,
    LightmappedMeshes,
    Camera,
    Status,
}

#[derive(Debug, Component)]
struct AmbientLightDesignerButton;

#[derive(Debug, Component)]
struct AmbientLightDesignerValueText(AmbientLightDesignerValue);

#[derive(Debug, Component)]
struct AmbientLightSliderValueText(AmbientLightSliderParam);

#[derive(Debug, Component)]
struct AmbientLightSliderTrack(AmbientLightSliderParam);

#[derive(Debug, Component)]
struct AmbientLightSliderFill(AmbientLightSliderParam);

#[derive(Debug, Component)]
struct AmbientLightSliderThumb(AmbientLightSliderParam);

#[derive(Debug, Component)]
struct AmbientLightColorSwatch;

#[derive(Debug, Component)]
struct AmbientLightScrollPanel;

#[derive(Debug, Component)]
struct AmbientLightDesignerCamera;

#[derive(Debug, Component)]
struct AmbientLightPreviewKey;

#[derive(Debug, Clone, Copy)]
struct AmbientPreset {
    label: &'static str,
    color: &'static str,
    brightness: f32,
}

#[derive(Debug, Clone, Copy)]
struct DesignerSceneBounds {
    center: Vec3,
    radius: f32,
}

#[derive(Debug, Clone, Copy)]
struct DesignerCameraOrbit {
    target: Vec3,
    distance: f32,
    yaw: f32,
    pitch: f32,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesignerCameraSnapshot {
    position: [f32; 3],
    target: [f32; 3],
}

const AMBIENT_PRESETS: [AmbientPreset; 8] = [
    AmbientPreset {
        label: "Neutral",
        color: "#ffffff",
        brightness: 220.0,
    },
    AmbientPreset {
        label: "Bright",
        color: "#ffffff",
        brightness: 650.0,
    },
    AmbientPreset {
        label: "Daylight",
        color: "#fff5dc",
        brightness: 900.0,
    },
    AmbientPreset {
        label: "Sky",
        color: "#d6ecff",
        brightness: 700.0,
    },
    AmbientPreset {
        label: "Moon",
        color: "#9bbcff",
        brightness: 120.0,
    },
    AmbientPreset {
        label: "Dusk",
        color: "#ffd0a0",
        brightness: 165.0,
    },
    AmbientPreset {
        label: "Fog",
        color: "#b7c5d8",
        brightness: 190.0,
    },
    AmbientPreset {
        label: "Dark",
        color: "#586070",
        brightness: 55.0,
    },
];

pub fn run_bevy_ambient_light_designer(launch: BevyAmbientLightDesignerLaunch) {
    let asset_root = launch.project_root.join("resources");
    let asset_file_path = asset_root.to_string_lossy().to_string();
    let builtin_asset_root = builtin_assets_root();
    let state = AmbientLightDesignerState::load(launch, asset_root);

    let mut app = App::new();
    if let Some(builtin_asset_root) = builtin_asset_root.as_ref() {
        let source_path = builtin_asset_root.to_string_lossy().to_string();
        app.register_asset_source(
            "builtin",
            AssetSourceBuilder::platform_default(&source_path, None),
        );
    }

    let default_plugins = DefaultPlugins
        .build()
        .disable::<LogPlugin>()
        .set(AssetPlugin {
            file_path: asset_file_path,
            ..default()
        })
        .set(WindowPlugin {
            primary_window: Some(Window {
                title: "SceneMax Bevy Ambient Light Designer".to_owned(),
                present_mode: PresentMode::AutoVsync,
                resolution: WindowResolution::new(1440, 900).with_scale_factor_override(1.0),
                ..default()
            }),
            ..default()
        });
    #[cfg(feature = "effekseer_native")]
    let default_plugins = default_plugins.set(RenderPlugin {
        render_creation: WgpuSettings {
            backends: Some(Backends::DX12),
            ..default()
        }
        .into(),
        ..default()
    });

    app.insert_resource(ClearColor(Color::srgb(0.025, 0.032, 0.042)))
        .insert_resource(WinitSettings::game())
        .insert_resource(SceneMaxLaunchContext {
            script_root: state.design_path.parent().map(Path::to_path_buf),
            asset_root: Some(state.asset_root.clone()),
            builtin_asset_root: builtin_asset_root.clone(),
            window_width: 1440,
            window_height: 900,
        })
        .insert_resource(SceneMaxRuntimeAssets::default())
        .init_resource::<SceneMaxVars>()
        .init_resource::<SceneMaxObjectPools>()
        .init_resource::<SceneMaxCameraSystem>()
        .init_resource::<DelayedActionQueue>()
        .init_resource::<SceneMaxUiActionQueue>()
        .init_resource::<SceneMaxColliderBounds>()
        .init_resource::<SceneMaxAnimationDurations>()
        .init_resource::<Assets<SceneMaxControlSchemeConfig>>()
        .insert_resource(AmbientLightLastAppliedSettings(None))
        .insert_resource(state)
        .add_plugins(default_plugins)
        .add_systems(Startup, setup_ambient_light_designer)
        .add_systems(
            Update,
            (
                load_ambient_light_preview_scene,
                handle_ambient_light_buttons,
                handle_ambient_light_sliders,
                handle_ambient_light_panel_scroll,
                update_ambient_light_button_colors,
                apply_ambient_light_preview,
                apply_gltf_visual_offsets,
                update_ambient_light_camera,
                draw_ambient_light_camera_gizmos,
                update_ambient_light_ui_state,
                exit_ambient_light_on_escape,
            )
                .chain(),
        )
        .run();
}

impl AmbientLightDesignerState {
    fn load(launch: BevyAmbientLightDesignerLaunch, asset_root: PathBuf) -> Self {
        let settings = load_bevy_ambient_light_settings_for_designer(&launch.design);
        let source = designer_preview_source(&launch.design).unwrap_or_else(|error| {
            format!("preview_placeholder => box : size (8,1,8), pos (0,-1,0)\n// {error}")
        });
        let bounds = designer_scene_bounds(&launch.design).unwrap_or(DesignerSceneBounds {
            center: Vec3::ZERO,
            radius: 32.0,
        });
        let initial_ide_camera = launch
            .camera_snapshot
            .as_deref()
            .and_then(parse_designer_camera_snapshot)
            .as_ref()
            .map(camera_orbit_from_sync_snapshot);
        let camera_orbit = initial_ide_camera
            .as_ref()
            .copied()
            .or_else(|| designer_camera_orbit(&launch.design, bounds))
            .unwrap_or_else(|| orbit_from_target_and_angles(bounds.center, bounds.radius));
        Self {
            design_path: launch.design,
            asset_root,
            source,
            settings,
            status: "Ready".to_owned(),
            scene_bounds: bounds,
            initial_ide_camera,
            camera_target: camera_orbit.target,
            camera_distance: camera_orbit.distance,
            camera_yaw: camera_orbit.yaw,
            camera_pitch: camera_orbit.pitch,
            scene_load_delay_frames: 2,
            scene_loaded: false,
        }
    }
}

impl Default for BevyAmbientLightSettings {
    fn default() -> Self {
        Self {
            configured: false,
            enabled: false,
            name: default_ambient_name(),
            color: default_ambient_color(),
            brightness: default_ambient_brightness(),
            affects_lightmapped_meshes: default_affects_lightmapped_meshes(),
        }
    }
}

pub(super) fn apply_bevy_ambient_light_for_script_root(
    commands: &mut Commands,
    script_root: Option<&Path>,
) {
    let Some(script_root) = script_root else {
        return;
    };
    let Some(settings) = load_bevy_ambient_light_settings_from_script_root(script_root) else {
        return;
    };
    if settings.enabled {
        write_runtime_diagnostic_line(format!(
            "BEVY_AMBIENT:APPLY source=smdesign name={} brightness={:.3} color={} affects_lightmapped_meshes={}",
            settings.name.trim(),
            settings.brightness.max(0.0),
            settings.color.trim(),
            settings.affects_lightmapped_meshes
        ));
        apply_bevy_ambient_light_settings(commands, &settings);
        set_fallback_lighting_enabled(commands, false);
    }
}

fn setup_ambient_light_designer(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    state: Res<AmbientLightDesignerState>,
) {
    runtime_assets.asset_server = Some(asset_server.clone());
    runtime_assets.asset_root = Some(state.asset_root.clone());
    runtime_assets.builtin_asset_root = builtin_assets_root();
    runtime_assets.placeholder_mesh = Some(meshes.add(Cuboid::new(1.0, 1.0, 1.0)));
    runtime_assets.placeholder_material = Some(materials.add(Color::srgb_u8(185, 150, 65)));

    commands.spawn((
        Camera3d::default(),
        AmbientLightDesignerCamera,
        ambient_designer_camera_transform(&state),
    ));
    commands.spawn((
        DirectionalLight {
            illuminance: 24_000.0,
            shadow_maps_enabled: true,
            shadow_depth_bias: 0.08,
            shadow_normal_bias: 1.8,
            ..default()
        },
        SceneMaxEnvironmentDirectionalLight,
        AmbientLightPreviewKey,
        SceneMaxFallbackLight::Directional {
            illuminance: 24_000.0,
        },
        Transform::from_xyz(-8.0, 14.0, 8.0).looking_at(Vec3::ZERO, Vec3::Y),
    ));
    commands.spawn((
        PointLight {
            color: Color::srgb(1.0, 0.86, 0.68),
            intensity: 55_000.0,
            range: 45.0,
            shadow_maps_enabled: true,
            ..default()
        },
        AmbientLightPreviewKey,
        SceneMaxFallbackLight::Point {
            intensity: 55_000.0,
        },
        Transform::from_xyz(-7.0, 8.0, 8.0),
    ));
    commands.spawn((
        PointLight {
            color: Color::srgb(0.55, 0.7, 1.0),
            intensity: 18_000.0,
            range: 55.0,
            shadow_maps_enabled: false,
            ..default()
        },
        AmbientLightPreviewKey,
        SceneMaxFallbackLight::Point {
            intensity: 18_000.0,
        },
        Transform::from_xyz(9.0, 5.0, -9.0),
    ));
    apply_bevy_ambient_light_settings(&mut commands, &state.settings);
    spawn_ambient_light_designer_ui(&mut commands, &state);
}

fn load_ambient_light_preview_scene(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    mut character_configs: ResMut<Assets<SceneMaxControlSchemeConfig>>,
    mut state: ResMut<AmbientLightDesignerState>,
) {
    if state.scene_loaded {
        return;
    }
    if state.scene_load_delay_frames > 0 {
        state.scene_load_delay_frames -= 1;
        state.status = "Loading visible scene preview...".to_owned();
        return;
    }

    let source = state.source.clone();
    let asset_root = state.asset_root.clone();
    let load_started_at = Instant::now();
    let program = match scenemax_parser::parse_program(&source) {
        Ok(program) => program,
        Err(error) => {
            tracing::warn!(%error, "failed to parse Designer preview source");
            state.scene_loaded = true;
            state.status = format!("Scene preview parse failed: {error}");
            return;
        }
    };
    let builtin_root = runtime_assets.builtin_asset_root.clone();
    let _startup_gltfs = spawn_scenemax_program_for_designer_preview(
        &mut commands,
        &asset_server,
        &asset_root,
        builtin_root.as_deref(),
        &program,
        &mut vars,
        &mut object_pools,
        &mut camera_system,
        &mut delayed_actions,
        &mut ui_queue,
        &mut collider_bounds,
        &mut meshes,
        &mut materials,
        &mut character_configs,
    );
    state.scene_loaded = true;
    let elapsed = load_started_at.elapsed();
    state.status = format!("Scene preview loaded in {:.1}s", elapsed.as_secs_f32());
    write_runtime_diagnostic_line(format!(
        "AMBIENT_DESIGNER:SCENE_PREVIEW_LOADED seconds={:.3}",
        elapsed.as_secs_f32()
    ));
}

fn spawn_ambient_light_designer_ui(commands: &mut Commands, state: &AmbientLightDesignerState) {
    commands.spawn((
        Camera2d,
        Camera {
            order: 1,
            clear_color: ClearColorConfig::None,
            ..default()
        },
        IsDefaultUiCamera,
    ));
    commands
        .spawn(Node {
            position_type: PositionType::Absolute,
            left: px(0.0),
            top: px(0.0),
            width: Val::Percent(100.0),
            height: Val::Percent(100.0),
            flex_direction: FlexDirection::Row,
            justify_content: JustifyContent::SpaceBetween,
            padding: UiRect::all(px(18.0)),
            ..default()
        })
        .with_children(|root| {
            root.spawn(panel_frame(PANEL_WIDTH)).with_children(|panel| {
                panel.spawn(text_bundle("Bevy Ambient Light", 24.0, TEXT_MAIN));
                panel.spawn(text_bundle(
                    state
                        .design_path
                        .file_name()
                        .and_then(|name| name.to_str())
                        .unwrap_or("Scene"),
                    12.0,
                    TEXT_MUTED,
                ));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Save",
                        AmbientLightDesignerAction::Save,
                        false,
                        92.0,
                    ));
                    row.spawn(action_button(
                        "Close",
                        AmbientLightDesignerAction::Close,
                        false,
                        92.0,
                    ));
                });
                panel.spawn(value_text(AmbientLightDesignerValue::Status));

                panel.spawn(section_label("State"));
                panel.spawn(value_text(AmbientLightDesignerValue::Enabled));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Enabled",
                        AmbientLightDesignerAction::ToggleEnabled,
                        state.settings.enabled,
                        112.0,
                    ));
                    row.spawn(action_button(
                        "Reset",
                        AmbientLightDesignerAction::Reset,
                        false,
                        92.0,
                    ));
                });

                panel.spawn(section_label("Brightness"));
                panel.spawn(value_text(AmbientLightDesignerValue::Brightness));
                panel.spawn(slider_row(
                    AmbientLightSliderParam::Brightness,
                    state.settings.brightness,
                ));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "-40",
                        AmbientLightDesignerAction::Brightness(-40),
                        false,
                        72.0,
                    ));
                    row.spawn(action_button(
                        "-5",
                        AmbientLightDesignerAction::Brightness(-5),
                        false,
                        72.0,
                    ));
                    row.spawn(action_button(
                        "+5",
                        AmbientLightDesignerAction::Brightness(5),
                        false,
                        72.0,
                    ));
                    row.spawn(action_button(
                        "+40",
                        AmbientLightDesignerAction::Brightness(40),
                        false,
                        72.0,
                    ));
                });

                panel.spawn(section_label("Color"));
                panel.spawn(value_text(AmbientLightDesignerValue::Color));
                panel.spawn(color_swatch(&state.settings.color));
                for param in [
                    AmbientLightSliderParam::Red,
                    AmbientLightSliderParam::Green,
                    AmbientLightSliderParam::Blue,
                ] {
                    panel.spawn(slider_row(param, param.value(&state.settings)));
                }
                panel.spawn(button_grid()).with_children(|grid| {
                    for channel in 0..3 {
                        grid.spawn(action_button(
                            channel_button_label(channel, -8),
                            AmbientLightDesignerAction::Color(channel, -8),
                            false,
                            84.0,
                        ));
                        grid.spawn(action_button(
                            channel_button_label(channel, 8),
                            AmbientLightDesignerAction::Color(channel, 8),
                            false,
                            84.0,
                        ));
                    }
                });

                panel.spawn(section_label("Presets"));
                panel.spawn(button_grid()).with_children(|grid| {
                    for (index, preset) in AMBIENT_PRESETS.iter().enumerate() {
                        grid.spawn(action_button(
                            preset.label,
                            AmbientLightDesignerAction::Preset(index),
                            false,
                            104.0,
                        ));
                    }
                });

                panel.spawn(section_label("Lightmaps"));
                panel.spawn(value_text(AmbientLightDesignerValue::LightmappedMeshes));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Affects",
                        AmbientLightDesignerAction::ToggleLightmappedMeshes,
                        state.settings.affects_lightmapped_meshes,
                        112.0,
                    ));
                });

                panel.spawn(section_label("Camera"));
                panel.spawn(value_text(AmbientLightDesignerValue::Camera));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "IDE View",
                        AmbientLightDesignerAction::RestoreIdeView,
                        false,
                        104.0,
                    ));
                    row.spawn(action_button(
                        "Frame",
                        AmbientLightDesignerAction::FrameScene,
                        false,
                        82.0,
                    ));
                });
                panel.spawn(button_grid()).with_children(|grid| {
                    grid.spawn(action_button(
                        "Front",
                        AmbientLightDesignerAction::ViewPreset(
                            AmbientLightDesignerViewPreset::Front,
                        ),
                        false,
                        84.0,
                    ));
                    grid.spawn(action_button(
                        "Right",
                        AmbientLightDesignerAction::ViewPreset(
                            AmbientLightDesignerViewPreset::Right,
                        ),
                        false,
                        84.0,
                    ));
                    grid.spawn(action_button(
                        "Top",
                        AmbientLightDesignerAction::ViewPreset(AmbientLightDesignerViewPreset::Top),
                        false,
                        84.0,
                    ));
                });

                panel.spawn(section_label("Status"));
                panel.spawn(value_text(AmbientLightDesignerValue::Status));
            });
        });
}

fn handle_ambient_light_buttons(
    mut state: ResMut<AmbientLightDesignerState>,
    mut exit: MessageWriter<AppExit>,
    mut interactions: Query<
        (&Interaction, &AmbientLightDesignerAction),
        (Changed<Interaction>, With<AmbientLightDesignerButton>),
    >,
) {
    for (interaction, action) in &mut interactions {
        if *interaction != Interaction::Pressed {
            continue;
        }
        match *action {
            AmbientLightDesignerAction::ToggleEnabled => {
                state.settings.enabled = !state.settings.enabled;
                state.status = if state.settings.enabled {
                    "Ambient light enabled".to_owned()
                } else {
                    "Ambient light disabled".to_owned()
                };
            }
            AmbientLightDesignerAction::Brightness(delta) => {
                state.settings.brightness =
                    (state.settings.brightness + delta as f32).clamp(0.0, BRIGHTNESS_MAX);
                state.status = "Brightness updated".to_owned();
            }
            AmbientLightDesignerAction::Color(channel, delta) => {
                let mut rgb = parse_hex_rgb(&state.settings.color).unwrap_or([255, 255, 255]);
                rgb[channel] = (rgb[channel] as i32 + delta).clamp(0, 255) as u8;
                state.settings.color = format!("#{:02x}{:02x}{:02x}", rgb[0], rgb[1], rgb[2]);
                state.status = "Color updated".to_owned();
            }
            AmbientLightDesignerAction::AdjustSlider(param, delta) => {
                let value = param.value(&state.settings) + delta;
                param.set_value(&mut state.settings, value);
                state.status = format!("{} updated", param.label());
            }
            AmbientLightDesignerAction::Preset(index) => {
                if let Some(preset) = AMBIENT_PRESETS.get(index) {
                    state.settings.enabled = true;
                    state.settings.color = preset.color.to_owned();
                    state.settings.brightness = preset.brightness;
                    state.status = format!("Preset: {}", preset.label);
                }
            }
            AmbientLightDesignerAction::ToggleLightmappedMeshes => {
                state.settings.affects_lightmapped_meshes =
                    !state.settings.affects_lightmapped_meshes;
                state.status = "Lightmap contribution updated".to_owned();
            }
            AmbientLightDesignerAction::RestoreIdeView => {
                if let Some(orbit) = state.initial_ide_camera {
                    apply_camera_orbit(&mut state, orbit);
                    state.status = "Restored launch IDE camera".to_owned();
                } else {
                    state.status = "No IDE camera snapshot".to_owned();
                }
            }
            AmbientLightDesignerAction::FrameScene => {
                let bounds = state.scene_bounds;
                let orbit = orbit_from_target_and_angles(bounds.center, bounds.radius);
                apply_camera_orbit(&mut state, orbit);
                state.status = "Framed scene".to_owned();
            }
            AmbientLightDesignerAction::ViewPreset(preset) => {
                let orbit = view_preset_orbit(&state, preset);
                apply_camera_orbit(&mut state, orbit);
                state.status = "Camera view updated".to_owned();
            }
            AmbientLightDesignerAction::Reset => {
                state.settings = BevyAmbientLightSettings::default();
                state.status = "Reset to defaults".to_owned();
            }
            AmbientLightDesignerAction::Save => {
                state.settings.configured = true;
                match save_bevy_ambient_light_settings_to_design(
                    &state.design_path,
                    &state.settings,
                ) {
                    Ok(()) => {
                        state.status = format!(
                            "Saved: brightness {:.0}, color {}",
                            state.settings.brightness,
                            state.settings.color.trim()
                        );
                    }
                    Err(error) => state.status = format!("Save failed: {error}"),
                }
            }
            AmbientLightDesignerAction::Close => {
                exit.write(AppExit::Success);
            }
        }
    }
}

fn handle_ambient_light_sliders(
    sliders: Query<(
        &Interaction,
        &RelativeCursorPosition,
        &AmbientLightSliderTrack,
    )>,
    mut state: ResMut<AmbientLightDesignerState>,
) {
    for (interaction, cursor, slider) in &sliders {
        if *interaction != Interaction::Pressed {
            continue;
        }
        let Some(position) = cursor.normalized else {
            continue;
        };
        let (min, max, _) = slider.0.range();
        let amount = (position.x + 0.5).clamp(0.0, 1.0);
        slider
            .0
            .set_value(&mut state.settings, min + (max - min) * amount);
        state.status = format!("{} updated", slider.0.label());
    }
}

fn handle_ambient_light_panel_scroll(
    mut wheel_events: MessageReader<MouseWheel>,
    mut panels: Query<
        (
            &mut ScrollPosition,
            &Node,
            &ComputedNode,
            &RelativeCursorPosition,
        ),
        With<AmbientLightScrollPanel>,
    >,
) {
    let Ok((mut scroll_position, node, computed, cursor)) = panels.single_mut() else {
        return;
    };
    if cursor.normalized.is_none() || node.overflow.y != OverflowAxis::Scroll {
        return;
    }

    let max_offset = ((computed.content_size() - computed.size())
        * computed.inverse_scale_factor())
    .max(Vec2::ZERO);
    for event in wheel_events.read() {
        let unit = match event.unit {
            MouseScrollUnit::Line => SCROLL_LINE_HEIGHT,
            MouseScrollUnit::Pixel => 1.0,
        };
        scroll_position.y = (scroll_position.y - event.y * unit).clamp(0.0, max_offset.y);
    }
}

fn update_ambient_light_button_colors(
    state: Res<AmbientLightDesignerState>,
    mut buttons: Query<
        (
            &Interaction,
            &AmbientLightDesignerAction,
            &mut BackgroundColor,
        ),
        With<AmbientLightDesignerButton>,
    >,
) {
    for (interaction, action, mut color) in &mut buttons {
        let active = match *action {
            AmbientLightDesignerAction::ToggleEnabled => state.settings.enabled,
            AmbientLightDesignerAction::ToggleLightmappedMeshes => {
                state.settings.affects_lightmapped_meshes
            }
            _ => false,
        };
        *color = BackgroundColor(match (*interaction, active) {
            (Interaction::Hovered, true) | (Interaction::Pressed, true) => BUTTON_ACTIVE,
            (Interaction::Hovered, false) | (Interaction::Pressed, false) => BUTTON_HOVER,
            (_, true) => BUTTON_ACTIVE,
            _ => BUTTON_BG,
        });
    }
}

fn apply_ambient_light_preview(
    state: Res<AmbientLightDesignerState>,
    mut commands: Commands,
    mut last_applied: ResMut<AmbientLightLastAppliedSettings>,
) {
    if last_applied.0.as_ref() == Some(&state.settings) {
        return;
    }
    apply_bevy_ambient_light_settings(&mut commands, &state.settings);
    last_applied.0 = Some(state.settings.clone());
}

fn update_ambient_light_ui_state(
    state: Res<AmbientLightDesignerState>,
    mut texts: ParamSet<(
        Query<(&AmbientLightDesignerValueText, &mut Text)>,
        Query<(&AmbientLightSliderValueText, &mut Text)>,
    )>,
    mut slider_nodes: ParamSet<(
        Query<(&AmbientLightSliderFill, &mut Node)>,
        Query<(&AmbientLightSliderThumb, &mut Node)>,
    )>,
    mut color_swatches: Query<&mut BackgroundColor, With<AmbientLightColorSwatch>>,
) {
    if !state.is_changed() {
        return;
    }
    for (value, mut text) in &mut texts.p0() {
        text.0 = match value.0 {
            AmbientLightDesignerValue::Enabled => {
                if state.settings.enabled {
                    "Enabled".to_owned()
                } else {
                    "Disabled".to_owned()
                }
            }
            AmbientLightDesignerValue::Brightness => {
                format!("{:.0} brightness", state.settings.brightness)
            }
            AmbientLightDesignerValue::Color => state.settings.color.clone(),
            AmbientLightDesignerValue::LightmappedMeshes => {
                if state.settings.affects_lightmapped_meshes {
                    "Affects lightmapped meshes".to_owned()
                } else {
                    "Does not affect lightmapped meshes".to_owned()
                }
            }
            AmbientLightDesignerValue::Camera => {
                if state.initial_ide_camera.is_some() {
                    "Launch IDE view captured".to_owned()
                } else {
                    "Local camera control".to_owned()
                }
            }
            AmbientLightDesignerValue::Status => state.status.clone(),
        };
    }
    for (value, mut text) in &mut texts.p1() {
        let current = value.0.value(&state.settings);
        text.0 = value.0.format_value(current);
    }
    for (fill, mut node) in &mut slider_nodes.p0() {
        let (min, max, _) = fill.0.range();
        node.width = Val::Percent(normalized_percent(fill.0.value(&state.settings), min, max));
    }
    for (thumb, mut node) in &mut slider_nodes.p1() {
        let (min, max, _) = thumb.0.range();
        node.left = Val::Percent(normalized_percent(thumb.0.value(&state.settings), min, max));
    }
    for mut background in &mut color_swatches {
        *background = BackgroundColor(color_from_hex(&state.settings.color));
    }
}

fn update_ambient_light_camera(
    time: Res<Time>,
    windows: Query<&Window, With<PrimaryWindow>>,
    keyboard: Res<ButtonInput<KeyCode>>,
    mouse_buttons: Res<ButtonInput<MouseButton>>,
    mouse_motion: Res<AccumulatedMouseMotion>,
    mut mouse_wheel_reader: MessageReader<MouseWheel>,
    mut state: ResMut<AmbientLightDesignerState>,
    mut cameras: Query<&mut Transform, With<AmbientLightDesignerCamera>>,
) {
    let dt = time.delta_secs();
    let mut local_control = false;
    let preview_active = windows
        .single()
        .ok()
        .is_some_and(cursor_in_ambient_light_preview);

    if preview_active && mouse_motion.delta != Vec2::ZERO {
        let delta = mouse_motion.delta;
        if mouse_buttons.pressed(MouseButton::Right)
            && (keyboard.pressed(KeyCode::ShiftLeft) || keyboard.pressed(KeyCode::ShiftRight))
            || mouse_buttons.pressed(MouseButton::Middle)
        {
            pan_ambient_camera(&mut state, delta);
            local_control = true;
        } else if mouse_buttons.pressed(MouseButton::Right) {
            state.camera_yaw -= delta.x * 0.005;
            state.camera_pitch = (state.camera_pitch + delta.y * 0.005).clamp(-1.45, 1.45);
            local_control = true;
        }
    }

    if preview_active {
        for event in mouse_wheel_reader.read() {
            let unit = match event.unit {
                MouseScrollUnit::Line => 0.10,
                MouseScrollUnit::Pixel => 0.004,
            };
            let zoom_factor = (1.0 - event.y * unit).clamp(0.18, 5.0);
            state.camera_distance = (state.camera_distance * zoom_factor).clamp(1.0, 2400.0);
            local_control = true;
        }
    } else {
        mouse_wheel_reader.clear();
    }

    if keyboard.pressed(KeyCode::ArrowLeft) {
        state.camera_yaw += dt;
        local_control = true;
    }
    if keyboard.pressed(KeyCode::ArrowRight) {
        state.camera_yaw -= dt;
        local_control = true;
    }
    if keyboard.pressed(KeyCode::ArrowUp) || keyboard.pressed(KeyCode::KeyW) {
        if keyboard.pressed(KeyCode::ArrowUp) {
            state.camera_pitch = (state.camera_pitch + dt * 0.7).clamp(-1.45, 1.45);
        } else {
            translate_ambient_camera(&mut state, Vec3::NEG_Z, dt);
        }
        local_control = true;
    }
    if keyboard.pressed(KeyCode::ArrowDown) || keyboard.pressed(KeyCode::KeyS) {
        if keyboard.pressed(KeyCode::ArrowDown) {
            state.camera_pitch = (state.camera_pitch - dt * 0.7).clamp(-1.45, 1.45);
        } else {
            translate_ambient_camera(&mut state, Vec3::Z, dt);
        }
        local_control = true;
    }
    if keyboard.pressed(KeyCode::KeyA) {
        translate_ambient_camera(&mut state, Vec3::NEG_X, dt);
        local_control = true;
    }
    if keyboard.pressed(KeyCode::KeyD) {
        translate_ambient_camera(&mut state, Vec3::X, dt);
        local_control = true;
    }
    if keyboard.pressed(KeyCode::KeyQ) {
        translate_ambient_camera(&mut state, Vec3::NEG_Y, dt);
        local_control = true;
    }
    if keyboard.pressed(KeyCode::KeyE) {
        translate_ambient_camera(&mut state, Vec3::Y, dt);
        local_control = true;
    }
    if keyboard.pressed(KeyCode::Equal) || keyboard.pressed(KeyCode::NumpadAdd) {
        state.camera_distance = (state.camera_distance - dt * 60.0).clamp(8.0, 1600.0);
        local_control = true;
    }
    if keyboard.pressed(KeyCode::Minus) || keyboard.pressed(KeyCode::NumpadSubtract) {
        state.camera_distance = (state.camera_distance + dt * 60.0).clamp(8.0, 1600.0);
        local_control = true;
    }

    if local_control {
        state.status = "Local camera control".to_owned();
    }
    for mut transform in &mut cameras {
        *transform = ambient_designer_camera_transform(&state);
    }
}

fn parse_designer_camera_snapshot(source: &str) -> Option<DesignerCameraSnapshot> {
    serde_json::from_str(&source).ok()
}

fn camera_orbit_from_sync_snapshot(snapshot: &DesignerCameraSnapshot) -> DesignerCameraOrbit {
    orbit_from_position_and_target(
        vec3_from_array(snapshot.position),
        vec3_from_array(snapshot.target),
    )
}

fn vec3_from_array(value: [f32; 3]) -> Vec3 {
    Vec3::new(value[0], value[1], value[2])
}

fn apply_camera_orbit(state: &mut AmbientLightDesignerState, orbit: DesignerCameraOrbit) {
    state.camera_target = orbit.target;
    state.camera_distance = orbit.distance;
    state.camera_yaw = orbit.yaw;
    state.camera_pitch = orbit.pitch;
}

fn view_preset_orbit(
    state: &AmbientLightDesignerState,
    preset: AmbientLightDesignerViewPreset,
) -> DesignerCameraOrbit {
    let distance = state
        .camera_distance
        .max((state.scene_bounds.radius * 2.4).clamp(18.0, 900.0));
    match preset {
        AmbientLightDesignerViewPreset::Front => DesignerCameraOrbit {
            target: state.camera_target,
            distance,
            yaw: 0.0,
            pitch: 0.0,
        },
        AmbientLightDesignerViewPreset::Right => DesignerCameraOrbit {
            target: state.camera_target,
            distance,
            yaw: -std::f32::consts::FRAC_PI_2,
            pitch: 0.0,
        },
        AmbientLightDesignerViewPreset::Top => DesignerCameraOrbit {
            target: state.camera_target,
            distance,
            yaw: 0.0,
            pitch: -1.45,
        },
    }
}

fn pan_ambient_camera(state: &mut AmbientLightDesignerState, delta: Vec2) {
    let transform = ambient_designer_camera_transform(state);
    let right = transform.rotation * Vec3::X;
    let up = transform.rotation * Vec3::Y;
    let scale = state.camera_distance * 0.0018;
    state.camera_target -= right * delta.x * scale;
    state.camera_target += up * delta.y * scale;
}

fn translate_ambient_camera(state: &mut AmbientLightDesignerState, local_axis: Vec3, dt: f32) {
    let transform = ambient_designer_camera_transform(state);
    let world_axis = transform.rotation * local_axis;
    let speed = state.camera_distance.max(4.0) * 0.65 * dt;
    state.camera_target += world_axis * speed;
}

fn cursor_in_ambient_light_preview(window: &Window) -> bool {
    window
        .cursor_position()
        .is_some_and(|position| position.x >= PANEL_WIDTH + 42.0)
}

fn draw_ambient_light_camera_gizmos(state: Res<AmbientLightDesignerState>, mut gizmos: Gizmos) {
    let target = state.camera_target;
    let size = (state.scene_bounds.radius * 0.08).clamp(1.0, 10.0);
    gizmos.line(
        target - Vec3::X * size,
        target + Vec3::X * size,
        Color::srgb(1.0, 0.22, 0.18),
    );
    gizmos.line(
        target - Vec3::Y * size,
        target + Vec3::Y * size,
        Color::srgb(0.30, 0.95, 0.42),
    );
    gizmos.line(
        target - Vec3::Z * size,
        target + Vec3::Z * size,
        Color::srgb(0.30, 0.58, 1.0),
    );

    let center = state.scene_bounds.center;
    let axis = (state.scene_bounds.radius * 0.35).clamp(4.0, 80.0);
    gizmos.line(
        center,
        center + Vec3::X * axis,
        Color::srgb(0.8, 0.18, 0.14),
    );
    gizmos.line(
        center,
        center + Vec3::Y * axis,
        Color::srgb(0.18, 0.75, 0.28),
    );
    gizmos.line(
        center,
        center + Vec3::Z * axis,
        Color::srgb(0.20, 0.40, 0.95),
    );
}

fn exit_ambient_light_on_escape(
    keyboard: Res<ButtonInput<KeyCode>>,
    mut exit: MessageWriter<AppExit>,
) {
    if keyboard.just_pressed(KeyCode::Escape) {
        exit.write(AppExit::Success);
    }
}

fn apply_bevy_ambient_light_settings(commands: &mut Commands, settings: &BevyAmbientLightSettings) {
    let ambient = if settings.enabled {
        GlobalAmbientLight {
            color: color_from_hex(&settings.color),
            brightness: settings.brightness.max(0.0),
            affects_lightmapped_meshes: settings.affects_lightmapped_meshes,
            ..default()
        }
    } else {
        GlobalAmbientLight {
            color: Color::WHITE,
            brightness: 0.0,
            affects_lightmapped_meshes: settings.affects_lightmapped_meshes,
            ..default()
        }
    };
    commands.insert_resource(ambient);
    commands.insert_resource(SceneMaxAuthoredAmbientLight);
}

fn ambient_designer_camera_transform(state: &AmbientLightDesignerState) -> Transform {
    let yaw = Quat::from_rotation_y(state.camera_yaw);
    let pitch = Quat::from_rotation_x(state.camera_pitch);
    let offset = yaw * pitch * Vec3::new(0.0, 0.0, state.camera_distance);
    Transform::from_translation(state.camera_target + offset)
        .looking_at(state.camera_target, Vec3::Y)
}

fn panel_frame(width: f32) -> impl Bundle {
    (
        Node {
            width: px(width),
            max_height: Val::Percent(100.0),
            flex_direction: FlexDirection::Column,
            row_gap: px(10.0),
            padding: UiRect::all(px(16.0)),
            border: UiRect::all(px(1.0)),
            overflow: Overflow::scroll_y(),
            scrollbar_width: 10.0,
            ..default()
        },
        BackgroundColor(PANEL_BG),
        BorderColor::all(Color::srgba(0.32, 0.42, 0.5, 0.55)),
        ScrollPosition(Vec2::ZERO),
        RelativeCursorPosition::default(),
        AmbientLightScrollPanel,
    )
}

fn section_label(label: &'static str) -> impl Bundle {
    (
        Text::new(label),
        TextFont::from_font_size(14.0),
        TextColor(TEXT_ACCENT),
        Node {
            margin: UiRect::top(px(8.0)),
            ..default()
        },
    )
}

fn value_text(value: AmbientLightDesignerValue) -> impl Bundle {
    (
        Text::new(""),
        TextFont::from_font_size(12.0),
        TextColor(TEXT_MUTED),
        AmbientLightDesignerValueText(value),
    )
}

fn text_bundle(text: impl Into<String>, size: f32, color: Color) -> impl Bundle {
    (
        Text::new(text.into()),
        TextFont::from_font_size(size),
        TextColor(color),
    )
}

impl AmbientLightSliderParam {
    fn label(self) -> &'static str {
        match self {
            AmbientLightSliderParam::Brightness => "Brightness",
            AmbientLightSliderParam::Red => "Red",
            AmbientLightSliderParam::Green => "Green",
            AmbientLightSliderParam::Blue => "Blue",
        }
    }

    fn range(self) -> (f32, f32, f32) {
        match self {
            AmbientLightSliderParam::Brightness => (0.0, BRIGHTNESS_MAX, 10.0),
            AmbientLightSliderParam::Red
            | AmbientLightSliderParam::Green
            | AmbientLightSliderParam::Blue => (0.0, 255.0, 1.0),
        }
    }

    fn value(self, settings: &BevyAmbientLightSettings) -> f32 {
        match self {
            AmbientLightSliderParam::Brightness => settings.brightness,
            AmbientLightSliderParam::Red => {
                parse_hex_rgb(&settings.color).unwrap_or([255, 255, 255])[0] as f32
            }
            AmbientLightSliderParam::Green => {
                parse_hex_rgb(&settings.color).unwrap_or([255, 255, 255])[1] as f32
            }
            AmbientLightSliderParam::Blue => {
                parse_hex_rgb(&settings.color).unwrap_or([255, 255, 255])[2] as f32
            }
        }
    }

    fn set_value(self, settings: &mut BevyAmbientLightSettings, value: f32) {
        let (min, max, _) = self.range();
        let value = value.clamp(min, max);
        match self {
            AmbientLightSliderParam::Brightness => {
                settings.brightness = value;
            }
            AmbientLightSliderParam::Red
            | AmbientLightSliderParam::Green
            | AmbientLightSliderParam::Blue => {
                let mut rgb = parse_hex_rgb(&settings.color).unwrap_or([255, 255, 255]);
                let channel = match self {
                    AmbientLightSliderParam::Red => 0,
                    AmbientLightSliderParam::Green => 1,
                    AmbientLightSliderParam::Blue => 2,
                    AmbientLightSliderParam::Brightness => unreachable!(),
                };
                rgb[channel] = value.round().clamp(0.0, 255.0) as u8;
                settings.color = format!("#{:02x}{:02x}{:02x}", rgb[0], rgb[1], rgb[2]);
            }
        }
    }

    fn format_value(self, value: f32) -> String {
        match self {
            AmbientLightSliderParam::Brightness => format!("{value:.0}"),
            AmbientLightSliderParam::Red
            | AmbientLightSliderParam::Green
            | AmbientLightSliderParam::Blue => format!("{:.0}", value.clamp(0.0, 255.0)),
        }
    }

    fn fill_color(self) -> Color {
        match self {
            AmbientLightSliderParam::Brightness => Color::srgb(0.96, 0.86, 0.46),
            AmbientLightSliderParam::Red => Color::srgb(0.95, 0.22, 0.18),
            AmbientLightSliderParam::Green => Color::srgb(0.25, 0.82, 0.34),
            AmbientLightSliderParam::Blue => Color::srgb(0.30, 0.55, 1.0),
        }
    }
}

fn normalized_percent(value: f32, min: f32, max: f32) -> f32 {
    if (max - min).abs() <= f32::EPSILON {
        return 0.0;
    }
    ((value - min) / (max - min)).clamp(0.0, 1.0) * 100.0
}

fn slider_row(param: AmbientLightSliderParam, value: f32) -> impl Bundle {
    let (min, max, step) = param.range();
    let pct = normalized_percent(value, min, max);
    (
        Node {
            flex_direction: FlexDirection::Column,
            row_gap: px(5.0),
            ..default()
        },
        children![
            (
                Node {
                    flex_direction: FlexDirection::Row,
                    justify_content: JustifyContent::SpaceBetween,
                    align_items: AlignItems::Center,
                    ..default()
                },
                children![
                    text_bundle(param.label(), 12.0, TEXT_MAIN),
                    (
                        Text::new(param.format_value(value)),
                        TextFont::from_font_size(12.0),
                        TextColor(TEXT_MUTED),
                        AmbientLightSliderValueText(param),
                    )
                ]
            ),
            (
                Node {
                    flex_direction: FlexDirection::Row,
                    align_items: AlignItems::Center,
                    column_gap: px(7.0),
                    ..default()
                },
                children![
                    action_button(
                        "-",
                        AmbientLightDesignerAction::AdjustSlider(param, -step),
                        false,
                        34.0
                    ),
                    (
                        Node {
                            width: px(260.0),
                            height: px(18.0),
                            border: UiRect::all(px(1.0)),
                            box_sizing: BoxSizing::BorderBox,
                            overflow: Overflow::clip(),
                            ..default()
                        },
                        Button,
                        AmbientLightSliderTrack(param),
                        RelativeCursorPosition::default(),
                        BackgroundColor(SLIDER_TRACK),
                        BorderColor::all(Color::srgba(0.4, 0.5, 0.58, 0.45)),
                        children![
                            (
                                Node {
                                    position_type: PositionType::Absolute,
                                    left: px(0.0),
                                    top: px(0.0),
                                    width: Val::Percent(pct),
                                    height: Val::Percent(100.0),
                                    ..default()
                                },
                                BackgroundColor(param.fill_color()),
                                AmbientLightSliderFill(param),
                            ),
                            (
                                Node {
                                    position_type: PositionType::Absolute,
                                    left: Val::Percent(pct),
                                    top: px(0.0),
                                    width: px(8.0),
                                    height: Val::Percent(100.0),
                                    ..default()
                                },
                                BackgroundColor(SLIDER_THUMB),
                                AmbientLightSliderThumb(param),
                            )
                        ]
                    ),
                    action_button(
                        "+",
                        AmbientLightDesignerAction::AdjustSlider(param, step),
                        false,
                        34.0
                    ),
                ]
            )
        ],
    )
}

fn color_swatch(color: &str) -> impl Bundle {
    (
        Node {
            width: Val::Percent(100.0),
            height: px(26.0),
            border: UiRect::all(px(1.0)),
            box_sizing: BoxSizing::BorderBox,
            ..default()
        },
        BackgroundColor(color_from_hex(color)),
        BorderColor::all(Color::srgba(0.72, 0.80, 0.86, 0.55)),
        AmbientLightColorSwatch,
    )
}

fn toolbar_row() -> impl Bundle {
    Node {
        width: Val::Percent(100.0),
        flex_direction: FlexDirection::Row,
        column_gap: px(8.0),
        row_gap: px(8.0),
        flex_wrap: FlexWrap::Wrap,
        ..default()
    }
}

fn button_grid() -> impl Bundle {
    Node {
        width: Val::Percent(100.0),
        flex_direction: FlexDirection::Row,
        column_gap: px(8.0),
        row_gap: px(8.0),
        flex_wrap: FlexWrap::Wrap,
        ..default()
    }
}

fn action_button(
    label: impl Into<String>,
    action: AmbientLightDesignerAction,
    active: bool,
    width: f32,
) -> impl Bundle {
    (
        Node {
            width: px(width),
            height: px(34.0),
            border: UiRect::all(px(1.0)),
            box_sizing: BoxSizing::BorderBox,
            justify_content: JustifyContent::Center,
            align_items: AlignItems::Center,
            padding: UiRect::horizontal(px(8.0)),
            ..default()
        },
        Button,
        AmbientLightDesignerButton,
        action,
        BackgroundColor(if active { BUTTON_ACTIVE } else { BUTTON_BG }),
        BorderColor::all(Color::srgba(0.4, 0.5, 0.58, 0.5)),
        children![(
            Text::new(label.into()),
            TextFont::from_font_size(12.0),
            TextColor(TEXT_MAIN),
        )],
    )
}

fn channel_button_label(channel: usize, delta: i32) -> String {
    let name = match channel {
        0 => "R",
        1 => "G",
        _ => "B",
    };
    if delta < 0 {
        format!("{name}{delta}")
    } else {
        format!("{name}+{delta}")
    }
}

fn designer_preview_source(path: &Path) -> Result<String> {
    let source = fs::read_to_string(path)?;
    let value: serde_json::Value = serde_json::from_str(&source)?;
    let mut lines = Vec::new();
    if let Some(shader) = value
        .get("sceneEnvironmentShader")
        .and_then(serde_json::Value::as_str)
        .filter(|shader| !shader.trim().is_empty())
    {
        lines.push(format!("Scene.environment.shader = \"{}\"", shader.trim()));
    }
    collect_designer_entity_code(value.get("entities"), &mut lines);
    Ok(lines.join("\n"))
}

fn collect_designer_entity_code(value: Option<&serde_json::Value>, lines: &mut Vec<String>) {
    let Some(array) = value.and_then(serde_json::Value::as_array) else {
        return;
    };
    for entity in array {
        let entity_type = entity
            .get("type")
            .and_then(serde_json::Value::as_str)
            .unwrap_or_default();
        if entity_type == "CODE" {
            if let Some(code) = entity
                .get("codeText")
                .and_then(serde_json::Value::as_str)
                .filter(|code| !code.trim().is_empty())
            {
                lines.push(code.trim().to_owned());
            }
        } else if let Some(code) = entity
            .get("sceneMaxCode")
            .and_then(serde_json::Value::as_str)
            .filter(|code| !code.trim().is_empty())
        {
            lines.push(code.trim().to_owned());
        }
        collect_designer_entity_code(entity.get("children"), lines);
    }
}

fn designer_scene_bounds(path: &Path) -> Option<DesignerSceneBounds> {
    let source = fs::read_to_string(path).ok()?;
    let value: serde_json::Value = serde_json::from_str(&source).ok()?;
    let mut points = Vec::new();
    collect_designer_entity_bounds(value.get("entities"), &mut points);
    if points.is_empty() {
        return None;
    }
    let mut min = points[0];
    let mut max = points[0];
    for point in points.iter().copied().skip(1) {
        min = min.min(point);
        max = max.max(point);
    }
    let center = (min + max) * 0.5;
    let radius = points
        .iter()
        .map(|point| point.distance(center))
        .fold(0.0_f32, f32::max)
        .max((max - min).length() * 0.5)
        .max(12.0);
    Some(DesignerSceneBounds { center, radius })
}

fn collect_designer_entity_bounds(value: Option<&serde_json::Value>, points: &mut Vec<Vec3>) {
    let Some(array) = value.and_then(serde_json::Value::as_array) else {
        return;
    };
    for entity in array {
        if should_include_designer_entity_in_bounds(entity)
            && let Some(position) = vec3_array(entity.get("position"))
        {
            let half_size = designer_entity_half_size(entity);
            points.push(position - half_size);
            points.push(position + half_size);
        }
        collect_designer_entity_bounds(entity.get("children"), points);
    }
}

fn should_include_designer_entity_in_bounds(entity: &serde_json::Value) -> bool {
    if entity
        .get("hidden")
        .and_then(serde_json::Value::as_bool)
        .unwrap_or(false)
        || entity
            .get("colliderEntity")
            .and_then(serde_json::Value::as_bool)
            .unwrap_or(false)
    {
        return false;
    }
    matches!(
        entity
            .get("type")
            .and_then(serde_json::Value::as_str)
            .unwrap_or_default(),
        "BOX"
            | "SPHERE"
            | "CYLINDER"
            | "CONE"
            | "HOLLOW_CYLINDER"
            | "QUAD"
            | "STAIRS"
            | "ARCH"
            | "MODEL"
    )
}

fn designer_entity_half_size(entity: &serde_json::Value) -> Vec3 {
    let scale = vec3_array(entity.get("scale")).unwrap_or(Vec3::ONE);
    let type_name = entity
        .get("type")
        .and_then(serde_json::Value::as_str)
        .unwrap_or_default();
    let raw_size = match type_name {
        "BOX" => Vec3::new(
            f32_json(entity, "sizeX", 1.0),
            f32_json(entity, "sizeY", 1.0),
            f32_json(entity, "sizeZ", 1.0),
        ),
        "SPHERE" => Vec3::splat(f32_json(entity, "radius", 1.0) * 2.0),
        "CYLINDER" | "CONE" | "HOLLOW_CYLINDER" => Vec3::new(
            f32_json(entity, "radiusBottom", f32_json(entity, "radius", 1.0)) * 2.0,
            f32_json(entity, "height", 1.0),
            f32_json(entity, "radiusBottom", f32_json(entity, "radius", 1.0)) * 2.0,
        ),
        "QUAD" => Vec3::new(
            f32_json(entity, "quadWidth", 1.0),
            f32_json(entity, "quadHeight", 1.0),
            0.1,
        ),
        "STAIRS" => Vec3::new(
            f32_json(entity, "stairsWidth", 1.0),
            f32_json(entity, "stairsStepHeight", 1.0)
                * f32_json(entity, "stairsStepCount", 1.0).max(1.0),
            f32_json(entity, "stairsStepDepth", 1.0)
                * f32_json(entity, "stairsStepCount", 1.0).max(1.0),
        ),
        "ARCH" => Vec3::new(
            f32_json(entity, "archWidth", 1.0),
            f32_json(entity, "archHeight", 1.0),
            f32_json(entity, "archDepth", 1.0),
        ),
        _ => Vec3::splat(1.0),
    };
    (raw_size * scale.abs()) * 0.5
}

fn designer_camera_orbit(path: &Path, bounds: DesignerSceneBounds) -> Option<DesignerCameraOrbit> {
    companion_code_camera_orbit(path, bounds).or_else(|| designer_game_camera_orbit(path, bounds))
}

fn companion_code_camera_orbit(
    path: &Path,
    bounds: DesignerSceneBounds,
) -> Option<DesignerCameraOrbit> {
    let code_path = companion_code_path(path)?;
    let source = fs::read_to_string(code_path).ok()?;
    if !source.contains("camera.pos") && !source.contains("camera.rotate") {
        return None;
    }
    let program = scenemax_parser::parse_program(&source).ok()?;
    let transform = camera_transform_from_program(&program);
    Some(orbit_from_camera_transform(transform, bounds))
}

fn companion_code_path(path: &Path) -> Option<PathBuf> {
    let parent = path.parent()?;
    let stem = path.file_stem()?.to_str()?;
    Some(parent.join(format!("{stem}.code")))
}

fn designer_game_camera_orbit(
    path: &Path,
    bounds: DesignerSceneBounds,
) -> Option<DesignerCameraOrbit> {
    let source = fs::read_to_string(path).ok()?;
    let value: serde_json::Value = serde_json::from_str(&source).ok()?;
    let camera = value.get("gameCamera")?;
    let position = vec3_array(camera.get("position"))?;
    let rotation =
        quat_array(camera.get("rotation"))? * Quat::from_rotation_y(std::f32::consts::PI);
    Some(orbit_from_camera_parts(position, rotation, bounds))
}

fn orbit_from_camera_transform(
    transform: Transform,
    bounds: DesignerSceneBounds,
) -> DesignerCameraOrbit {
    orbit_from_camera_parts(transform.translation, transform.rotation, bounds)
}

fn orbit_from_camera_parts(
    position: Vec3,
    rotation: Quat,
    bounds: DesignerSceneBounds,
) -> DesignerCameraOrbit {
    let forward = rotation * Vec3::NEG_Z;
    let focus_distance = bounds.radius.clamp(18.0, 160.0);
    let projected_target = position + forward.normalize_or_zero() * focus_distance;
    let target = if projected_target.is_finite() {
        projected_target
    } else {
        bounds.center
    };
    orbit_from_position_and_target(position, target)
}

fn orbit_from_target_and_angles(target: Vec3, radius: f32) -> DesignerCameraOrbit {
    DesignerCameraOrbit {
        target,
        distance: (radius * 2.4).clamp(18.0, 900.0),
        yaw: -0.35,
        pitch: -0.42,
    }
}

fn orbit_from_position_and_target(position: Vec3, target: Vec3) -> DesignerCameraOrbit {
    let offset = position - target;
    let distance = offset.length().clamp(8.0, 1600.0);
    let dir = if distance > f32::EPSILON {
        offset / distance
    } else {
        Vec3::new(0.0, 0.4, 1.0).normalize()
    };
    let yaw = dir.x.atan2(dir.z);
    let pitch = (-dir.y).asin().clamp(-1.2, -0.08);
    DesignerCameraOrbit {
        target,
        distance,
        yaw,
        pitch,
    }
}

pub(super) fn load_bevy_ambient_light_settings_from_script_root(
    script_root: &Path,
) -> Option<BevyAmbientLightSettings> {
    let mut candidates = Vec::new();
    collect_smdesign_candidates(script_root, &mut candidates, 0);
    candidates.sort();
    candidates
        .iter()
        .filter_map(|path| load_bevy_ambient_light_settings_from_design(path))
        .find(|settings| settings.enabled)
}

fn collect_smdesign_candidates(root: &Path, result: &mut Vec<PathBuf>, depth: usize) {
    if depth > 2 {
        return;
    }
    let Ok(entries) = fs::read_dir(root) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            collect_smdesign_candidates(&path, result, depth + 1);
        } else if path
            .extension()
            .and_then(|extension| extension.to_str())
            .is_some_and(|extension| extension.eq_ignore_ascii_case("smdesign"))
        {
            result.push(path);
        }
    }
}

fn load_bevy_ambient_light_settings_from_design(path: &Path) -> Option<BevyAmbientLightSettings> {
    let source = fs::read_to_string(path).ok()?;
    let value: serde_json::Value = serde_json::from_str(&source).ok()?;
    value
        .get("bevyAmbientLight")
        .cloned()
        .and_then(|value| serde_json::from_value::<BevyAmbientLightSettings>(value).ok())
        .or_else(|| ambient_settings_from_legacy_light_entity(value.get("entities")))
}

fn load_bevy_ambient_light_settings_for_designer(path: &Path) -> BevyAmbientLightSettings {
    let Some(settings) = load_bevy_ambient_light_settings_from_design(path) else {
        return default_preview_ambient_settings();
    };
    if settings.configured || settings.enabled || !settings.is_unconfigured_default() {
        settings
    } else {
        default_preview_ambient_settings()
    }
}

fn ambient_settings_from_legacy_light_entity(
    value: Option<&serde_json::Value>,
) -> Option<BevyAmbientLightSettings> {
    let array = value?.as_array()?;
    for entity in array {
        if entity
            .get("type")
            .and_then(serde_json::Value::as_str)
            .is_some_and(|entity_type| entity_type == "LIGHT")
            && entity
                .get("lightType")
                .and_then(serde_json::Value::as_str)
                .is_some_and(|light_type| light_type.eq_ignore_ascii_case("ambient"))
        {
            let mut settings = BevyAmbientLightSettings::default();
            settings.configured = true;
            settings.enabled = true;
            settings.name = entity
                .get("name")
                .and_then(serde_json::Value::as_str)
                .unwrap_or("level_ambient")
                .to_owned();
            settings.color = entity
                .get("lightColor")
                .and_then(serde_json::Value::as_str)
                .unwrap_or("#ffffff")
                .to_owned();
            let intensity = entity
                .get("lightIntensity")
                .and_then(serde_json::Value::as_f64)
                .unwrap_or(1.0) as f32;
            let unit = entity
                .get("lightIntensityUnit")
                .and_then(serde_json::Value::as_str)
                .unwrap_or_default();
            settings.brightness = if unit.starts_with("lumen") {
                intensity / 800.0 * 220.0
            } else {
                intensity * 220.0
            };
            return Some(settings);
        }
        if let Some(settings) = ambient_settings_from_legacy_light_entity(entity.get("children")) {
            return Some(settings);
        }
    }
    None
}

impl BevyAmbientLightSettings {
    fn is_unconfigured_default(&self) -> bool {
        !self.configured
            && !self.enabled
            && self.name.trim() == default_ambient_name()
            && self
                .color
                .trim()
                .eq_ignore_ascii_case(&default_ambient_color())
            && (self.brightness - default_ambient_brightness()).abs() <= f32::EPSILON
            && self.affects_lightmapped_meshes == default_affects_lightmapped_meshes()
    }
}

fn save_bevy_ambient_light_settings_to_design(
    path: &Path,
    settings: &BevyAmbientLightSettings,
) -> Result<()> {
    let source = fs::read_to_string(path)?;
    let mut value: serde_json::Value = serde_json::from_str(&source)?;
    let object = value
        .as_object_mut()
        .ok_or_else(|| anyhow::anyhow!("Designer document root must be a JSON object"))?;
    object.insert(
        "bevyAmbientLight".to_owned(),
        serde_json::to_value(settings)?,
    );
    fs::write(path, serde_json::to_string_pretty(&value)?)?;
    Ok(())
}

fn color_from_hex(value: &str) -> Color {
    let [r, g, b] = parse_hex_rgb(value).unwrap_or([255, 255, 255]);
    Color::srgb_u8(r, g, b)
}

fn parse_hex_rgb(value: &str) -> Option<[u8; 3]> {
    let trimmed = value.trim().trim_matches('"').trim_start_matches('#');
    if trimmed.len() != 6 {
        return None;
    }
    Some([
        u8::from_str_radix(&trimmed[0..2], 16).ok()?,
        u8::from_str_radix(&trimmed[2..4], 16).ok()?,
        u8::from_str_radix(&trimmed[4..6], 16).ok()?,
    ])
}

fn vec3_array(value: Option<&serde_json::Value>) -> Option<Vec3> {
    let array = value.and_then(serde_json::Value::as_array)?;
    if array.len() < 3 {
        return None;
    }
    Some(Vec3::new(
        array[0].as_f64()? as f32,
        array[1].as_f64()? as f32,
        array[2].as_f64()? as f32,
    ))
}

fn quat_array(value: Option<&serde_json::Value>) -> Option<Quat> {
    let array = value.and_then(serde_json::Value::as_array)?;
    if array.len() < 4 {
        return None;
    }
    let quat = Quat::from_xyzw(
        array[0].as_f64()? as f32,
        array[1].as_f64()? as f32,
        array[2].as_f64()? as f32,
        array[3].as_f64()? as f32,
    );
    Some(if quat.is_finite() {
        quat.normalize()
    } else {
        Quat::IDENTITY
    })
}

fn f32_json(value: &serde_json::Value, field: &str, fallback: f32) -> f32 {
    value
        .get(field)
        .and_then(serde_json::Value::as_f64)
        .map(|number| number as f32)
        .unwrap_or(fallback)
}

fn builtin_assets_root() -> Option<PathBuf> {
    env::current_dir().ok().and_then(|current| {
        let repo_root = if current
            .file_name()
            .and_then(|name| name.to_str())
            .is_some_and(|name| name == "scenemax_projector_nextgen")
        {
            current.parent().map(Path::to_path_buf)
        } else {
            Some(current)
        }?;
        let resources = repo_root.join("resources-basic").join("resources");
        resources.is_dir().then_some(resources)
    })
}

fn default_ambient_name() -> String {
    "level_ambient".to_owned()
}

fn default_ambient_color() -> String {
    "#ffffff".to_owned()
}

fn default_ambient_brightness() -> f32 {
    220.0
}

fn default_affects_lightmapped_meshes() -> bool {
    true
}

fn default_preview_ambient_settings() -> BevyAmbientLightSettings {
    BevyAmbientLightSettings {
        configured: false,
        enabled: true,
        ..default()
    }
}
