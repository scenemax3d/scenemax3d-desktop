use std::path::{Path, PathBuf};

use bevy::{
    asset::{AssetPlugin, io::AssetSourceBuilder},
    log::LogPlugin,
    prelude::*,
    window::{PresentMode, WindowResolution},
};

use super::{
    BevySkyboxDefinition, SceneMaxLaunchContext, apply_skybox_definition_world,
    find_builtin_resources_root, load_skybox_definition_file, save_skybox_definition_file,
};

#[derive(Debug, Clone)]
pub struct BevySkyboxDesignerLaunch {
    pub skybox: PathBuf,
    pub project_root: Option<PathBuf>,
}

#[derive(Resource)]
struct BevySkyboxDesignerState {
    skybox_file: PathBuf,
    project_root: PathBuf,
    asset_root: PathBuf,
    image_assets: Vec<String>,
    definition: BevySkyboxDefinition,
    status: String,
    dirty: bool,
}

#[derive(Component)]
struct SkyboxDesignerRoot;

#[derive(Component)]
struct SkyboxDesignerButton;

#[derive(Component)]
struct SkyboxStatusText;

#[derive(Component)]
struct SkyboxSummaryText;

#[derive(Component, Clone, Copy)]
enum SkyboxDesignerAction {
    Save,
    Reload,
    ModeProcedural,
    ModeCubemap,
    QualityLookup,
    QualityRaymarched,
    SunAzimuthMinus,
    SunAzimuthPlus,
    SunElevationMinus,
    SunElevationPlus,
    SunIntensityMinus,
    SunIntensityPlus,
    SunColorNext,
    GroundAlbedoNext,
    AtmosphereMapSizeMinus,
    AtmosphereMapSizePlus,
    ToggleVolumetricFog,
    FogAmbientMinus,
    FogAmbientPlus,
    ExposureMinus,
    ExposurePlus,
    RotationMinus,
    RotationPlus,
    BrightnessMinus,
    BrightnessPlus,
    EnvironmentMinus,
    EnvironmentPlus,
    CubemapNext,
    DiffuseNext,
    SpecularNext,
    ClearImages,
    ToggleGeneratedEnvironmentMap,
    ToggleAtmosphereEnvironmentMap,
    ToggleLightmappedMeshes,
}

const PANEL_BG: Color = Color::srgba(0.045, 0.052, 0.064, 0.94);
const BUTTON_BG: Color = Color::srgb(0.105, 0.125, 0.150);
const BUTTON_HOVER: Color = Color::srgb(0.145, 0.175, 0.210);
const BUTTON_ACTIVE: Color = Color::srgb(0.100, 0.390, 0.500);
const TEXT_MAIN: Color = Color::srgb(0.935, 0.955, 0.975);
const TEXT_MUTED: Color = Color::srgb(0.620, 0.690, 0.760);
const TEXT_ACCENT: Color = Color::srgb(0.590, 0.870, 0.950);
const SUN_COLOR_PRESETS: [&str; 6] = [
    "#fff4d6", "#ffffff", "#ffd49a", "#ff9f5a", "#b9ccff", "#ff6b4a",
];
const GROUND_ALBEDO_PRESETS: [&str; 6] = [
    "#4d4d4d", "#25313b", "#3f4f36", "#5c4a36", "#7b7f86", "#2a2433",
];

pub fn run_bevy_skybox_designer(launch: BevySkyboxDesignerLaunch) {
    let project_root = resolve_project_root(&launch);
    let asset_root = project_root.join("resources");
    let builtin_asset_root =
        find_builtin_resources_root(Some(&project_root), None, Some(&asset_root));
    let definition = load_skybox_definition_file(&launch.skybox);
    let image_assets = collect_image_assets(&asset_root);
    let asset_file_path = asset_root.to_string_lossy().to_string();

    let mut app = App::new();
    if let Some(builtin_asset_root) = builtin_asset_root.as_ref() {
        let builtin_source_path = builtin_asset_root.to_string_lossy().to_string();
        app.register_asset_source(
            "builtin",
            AssetSourceBuilder::platform_default(&builtin_source_path, None),
        );
    }
    app.insert_resource(ClearColor(Color::srgb(0.030, 0.035, 0.045)))
        .insert_resource(BevySkyboxDesignerState {
            skybox_file: launch.skybox,
            project_root: project_root.clone(),
            asset_root: asset_root.clone(),
            image_assets,
            definition,
            status: "Skybox designer ready".to_owned(),
            dirty: false,
        })
        .insert_resource(SceneMaxLaunchContext {
            script_root: Some(project_root.join("scripts")),
            asset_root: Some(asset_root),
            builtin_asset_root,
            window_width: 1320,
            window_height: 820,
        })
        .add_plugins(
            DefaultPlugins
                .build()
                .disable::<LogPlugin>()
                .set(AssetPlugin {
                    file_path: asset_file_path,
                    ..default()
                })
                .set(WindowPlugin {
                    primary_window: Some(Window {
                        title: "SceneMax Bevy Skybox Designer".to_owned(),
                        resolution: WindowResolution::new(1320, 820),
                        present_mode: PresentMode::AutoVsync,
                        ..default()
                    }),
                    ..default()
                }),
        )
        .add_systems(Startup, setup_skybox_designer)
        .add_systems(
            Update,
            (
                handle_skybox_designer_buttons,
                update_skybox_designer_buttons,
                update_skybox_designer_text,
                keyboard_shortcuts,
            ),
        )
        .run();
}

fn setup_skybox_designer(
    mut commands: Commands,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    asset_server: Res<AssetServer>,
    state: Res<BevySkyboxDesignerState>,
) {
    commands.spawn((
        Camera3d::default(),
        Transform::from_xyz(-4.8, 3.0, 6.2).looking_at(Vec3::ZERO, Vec3::Y),
    ));
    commands.spawn((
        Mesh3d(meshes.add(Sphere::new(1.05).mesh().uv(64, 32))),
        MeshMaterial3d(materials.add(StandardMaterial {
            base_color: Color::srgb(0.82, 0.88, 0.92),
            metallic: 0.85,
            perceptual_roughness: 0.18,
            ..default()
        })),
        Transform::from_xyz(-1.2, 1.2, 0.0),
    ));
    commands.spawn((
        Mesh3d(meshes.add(Cuboid::new(3.5, 0.12, 3.5))),
        MeshMaterial3d(materials.add(StandardMaterial {
            base_color: Color::srgb(0.17, 0.19, 0.21),
            perceptual_roughness: 0.64,
            ..default()
        })),
        Transform::from_xyz(0.3, -0.08, 0.0),
    ));
    commands.spawn((
        DirectionalLight {
            illuminance: 24_000.0,
            shadow_maps_enabled: true,
            ..default()
        },
        super::SceneMaxEnvironmentDirectionalLight,
        Transform::from_xyz(-6.0, 8.0, 4.0).looking_at(Vec3::ZERO, Vec3::Y),
    ));
    commands.insert_resource(GlobalAmbientLight {
        color: Color::WHITE,
        brightness: 140.0,
        ..default()
    });
    commands.spawn((Camera2d,));
    spawn_ui(&mut commands, &state);

    let asset_root = state.asset_root.clone();
    let definition = state.definition.clone();
    let asset_server = asset_server.clone();
    commands.queue(move |world: &mut World| {
        apply_skybox_definition_world(world, &asset_server, &asset_root, &definition);
    });
}

fn spawn_ui(commands: &mut Commands, state: &BevySkyboxDesignerState) {
    commands
        .spawn((
            Node {
                width: px(390.0),
                height: Val::Percent(100.0),
                flex_direction: FlexDirection::Column,
                row_gap: px(10.0),
                padding: UiRect::all(px(14.0)),
                ..default()
            },
            BackgroundColor(PANEL_BG),
            SkyboxDesignerRoot,
        ))
        .with_children(|root| {
            root.spawn(text("Bevy Skybox Designer", 20.0, TEXT_MAIN));
            root.spawn(text(&state.definition.display_name, 14.0, TEXT_MUTED));
            root.spawn(scroll_panel()).with_children(|root| {
                root.spawn(text("Mode", 12.0, TEXT_ACCENT));
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "Procedural",
                        SkyboxDesignerAction::ModeProcedural,
                        is_procedural(&state.definition),
                        166.0,
                    ));
                    row.spawn(button(
                        "HDRI / Cubemap",
                        SkyboxDesignerAction::ModeCubemap,
                        is_cubemap(&state.definition),
                        166.0,
                    ));
                });

                root.spawn(text("Procedural Sky", 12.0, TEXT_ACCENT));
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "Lookup",
                        SkyboxDesignerAction::QualityLookup,
                        !state
                            .definition
                            .atmosphere_quality
                            .eq_ignore_ascii_case("raymarched"),
                        88.0,
                    ));
                    row.spawn(button(
                        "Raymarched",
                        SkyboxDesignerAction::QualityRaymarched,
                        state
                            .definition
                            .atmosphere_quality
                            .eq_ignore_ascii_case("raymarched"),
                        112.0,
                    ));
                });
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "- Sun Az",
                        SkyboxDesignerAction::SunAzimuthMinus,
                        false,
                        86.0,
                    ));
                    row.spawn(button(
                        "+ Sun Az",
                        SkyboxDesignerAction::SunAzimuthPlus,
                        false,
                        86.0,
                    ));
                    row.spawn(button(
                        "- Elev",
                        SkyboxDesignerAction::SunElevationMinus,
                        false,
                        82.0,
                    ));
                    row.spawn(button(
                        "+ Elev",
                        SkyboxDesignerAction::SunElevationPlus,
                        false,
                        82.0,
                    ));
                });
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "- Sun Lux",
                        SkyboxDesignerAction::SunIntensityMinus,
                        false,
                        96.0,
                    ));
                    row.spawn(button(
                        "+ Sun Lux",
                        SkyboxDesignerAction::SunIntensityPlus,
                        false,
                        96.0,
                    ));
                    row.spawn(button(
                        "Sun Color",
                        SkyboxDesignerAction::SunColorNext,
                        false,
                        104.0,
                    ));
                });
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "- Exposure",
                        SkyboxDesignerAction::ExposureMinus,
                        false,
                        110.0,
                    ));
                    row.spawn(button(
                        "+ Exposure",
                        SkyboxDesignerAction::ExposurePlus,
                        false,
                        110.0,
                    ));
                    row.spawn(button(
                        "Ground Color",
                        SkyboxDesignerAction::GroundAlbedoNext,
                        false,
                        124.0,
                    ));
                });
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "Atmosphere IBL",
                        SkyboxDesignerAction::ToggleAtmosphereEnvironmentMap,
                        state.definition.atmosphere_environment_map,
                        132.0,
                    ));
                    row.spawn(button(
                        "- Map Size",
                        SkyboxDesignerAction::AtmosphereMapSizeMinus,
                        false,
                        102.0,
                    ));
                    row.spawn(button(
                        "+ Map Size",
                        SkyboxDesignerAction::AtmosphereMapSizePlus,
                        false,
                        102.0,
                    ));
                });
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "Volumetric Fog",
                        SkyboxDesignerAction::ToggleVolumetricFog,
                        state.definition.volumetric_fog,
                        132.0,
                    ));
                    row.spawn(button(
                        "- Fog Amb",
                        SkyboxDesignerAction::FogAmbientMinus,
                        false,
                        96.0,
                    ));
                    row.spawn(button(
                        "+ Fog Amb",
                        SkyboxDesignerAction::FogAmbientPlus,
                        false,
                        96.0,
                    ));
                });

                root.spawn(text("HDRI / Cubemap Sky", 12.0, TEXT_ACCENT));
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "Next Cubemap",
                        SkyboxDesignerAction::CubemapNext,
                        false,
                        118.0,
                    ));
                    row.spawn(button(
                        "Next Diffuse",
                        SkyboxDesignerAction::DiffuseNext,
                        false,
                        112.0,
                    ));
                    row.spawn(button(
                        "Next Specular",
                        SkyboxDesignerAction::SpecularNext,
                        false,
                        118.0,
                    ));
                });
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "Generated IBL",
                        SkyboxDesignerAction::ToggleGeneratedEnvironmentMap,
                        state.definition.generated_environment_map,
                        142.0,
                    ));
                    row.spawn(button(
                        "Lightmapped",
                        SkyboxDesignerAction::ToggleLightmappedMeshes,
                        state.definition.affects_lightmapped_meshes,
                        128.0,
                    ));
                });
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "- Rotate",
                        SkyboxDesignerAction::RotationMinus,
                        false,
                        96.0,
                    ));
                    row.spawn(button(
                        "+ Rotate",
                        SkyboxDesignerAction::RotationPlus,
                        false,
                        96.0,
                    ));
                });
                root.spawn(button_row()).with_children(|row| {
                    row.spawn(button(
                        "- Bright",
                        SkyboxDesignerAction::BrightnessMinus,
                        false,
                        96.0,
                    ));
                    row.spawn(button(
                        "+ Bright",
                        SkyboxDesignerAction::BrightnessPlus,
                        false,
                        96.0,
                    ));
                    row.spawn(button(
                        "- IBL",
                        SkyboxDesignerAction::EnvironmentMinus,
                        false,
                        72.0,
                    ));
                    row.spawn(button(
                        "+ IBL",
                        SkyboxDesignerAction::EnvironmentPlus,
                        false,
                        72.0,
                    ));
                });
                root.spawn(button(
                    "Clear Images",
                    SkyboxDesignerAction::ClearImages,
                    false,
                    132.0,
                ));

                root.spawn(text("Summary", 12.0, TEXT_ACCENT));
                root.spawn((
                    text(summary_text(state), 12.0, TEXT_MAIN),
                    SkyboxSummaryText,
                    Node {
                        max_width: px(350.0),
                        ..default()
                    },
                ));
            });
            root.spawn(button_row()).with_children(|row| {
                row.spawn(button("Save", SkyboxDesignerAction::Save, false, 96.0));
                row.spawn(button("Reload", SkyboxDesignerAction::Reload, false, 96.0));
            });
            root.spawn((text(&state.status, 12.0, TEXT_MUTED), SkyboxStatusText));
        });
}

fn handle_skybox_designer_buttons(
    mut commands: Commands,
    mut state: ResMut<BevySkyboxDesignerState>,
    asset_server: Res<AssetServer>,
    interactions: Query<
        (&Interaction, &SkyboxDesignerAction),
        (Changed<Interaction>, With<SkyboxDesignerButton>),
    >,
) {
    let mut changed = false;
    for (interaction, action) in &interactions {
        if *interaction != Interaction::Pressed {
            continue;
        }
        changed |= apply_action(&mut state, *action);
    }
    if changed {
        let asset_root = state.asset_root.clone();
        let definition = state.definition.clone();
        let asset_server = asset_server.clone();
        commands.queue(move |world: &mut World| {
            apply_skybox_definition_world(world, &asset_server, &asset_root, &definition);
        });
    }
}

fn apply_action(state: &mut BevySkyboxDesignerState, action: SkyboxDesignerAction) -> bool {
    match action {
        SkyboxDesignerAction::Save => {
            match save_skybox_definition_file(&state.skybox_file, &state.definition) {
                Ok(()) => {
                    state.status = "Saved skybox definition".to_owned();
                    state.dirty = false;
                }
                Err(err) => {
                    state.status = format!("Save failed: {err}");
                }
            }
            false
        }
        SkyboxDesignerAction::Reload => {
            state.definition = load_skybox_definition_file(&state.skybox_file);
            state.status = "Reloaded from disk".to_owned();
            state.dirty = false;
            true
        }
        SkyboxDesignerAction::ModeProcedural => {
            state.definition.mode = "procedural_atmosphere".to_owned();
            mark_dirty(state);
            true
        }
        SkyboxDesignerAction::ModeCubemap => {
            state.definition.mode = "hdri_cubemap".to_owned();
            mark_dirty(state);
            true
        }
        SkyboxDesignerAction::QualityLookup => {
            state.definition.atmosphere_quality = "LOOKUP_TEXTURE".to_owned();
            mark_dirty(state);
            true
        }
        SkyboxDesignerAction::QualityRaymarched => {
            state.definition.atmosphere_quality = "RAYMARCHED".to_owned();
            mark_dirty(state);
            true
        }
        SkyboxDesignerAction::SunAzimuthMinus => adjust(state, |d| d.sun_azimuth_degrees -= 5.0),
        SkyboxDesignerAction::SunAzimuthPlus => adjust(state, |d| d.sun_azimuth_degrees += 5.0),
        SkyboxDesignerAction::SunElevationMinus => adjust(state, |d| {
            d.sun_elevation_degrees = (d.sun_elevation_degrees - 3.0).clamp(-6.0, 89.0)
        }),
        SkyboxDesignerAction::SunElevationPlus => adjust(state, |d| {
            d.sun_elevation_degrees = (d.sun_elevation_degrees + 3.0).clamp(-6.0, 89.0)
        }),
        SkyboxDesignerAction::SunIntensityMinus => adjust(state, |d| {
            d.sun_illuminance_scale = (d.sun_illuminance_scale - 0.1).max(0.0)
        }),
        SkyboxDesignerAction::SunIntensityPlus => adjust(state, |d| d.sun_illuminance_scale += 0.1),
        SkyboxDesignerAction::SunColorNext => {
            let next = next_palette_color(&state.definition.sun_color, &SUN_COLOR_PRESETS);
            adjust(state, |d| d.sun_color = next)
        }
        SkyboxDesignerAction::GroundAlbedoNext => {
            let next = next_palette_color(&state.definition.ground_albedo, &GROUND_ALBEDO_PRESETS);
            adjust(state, |d| d.ground_albedo = next)
        }
        SkyboxDesignerAction::AtmosphereMapSizeMinus => adjust(state, |d| {
            d.atmosphere_environment_map_size = halve_map_size(d.atmosphere_environment_map_size)
        }),
        SkyboxDesignerAction::AtmosphereMapSizePlus => adjust(state, |d| {
            d.atmosphere_environment_map_size = double_map_size(d.atmosphere_environment_map_size)
        }),
        SkyboxDesignerAction::ToggleVolumetricFog => {
            adjust(state, |d| d.volumetric_fog = !d.volumetric_fog)
        }
        SkyboxDesignerAction::FogAmbientMinus => adjust(state, |d| {
            d.fog_ambient_intensity = (d.fog_ambient_intensity - 0.05).max(0.0)
        }),
        SkyboxDesignerAction::FogAmbientPlus => adjust(state, |d| d.fog_ambient_intensity += 0.05),
        SkyboxDesignerAction::ExposureMinus => adjust(state, |d| d.exposure_ev100 -= 0.25),
        SkyboxDesignerAction::ExposurePlus => adjust(state, |d| d.exposure_ev100 += 0.25),
        SkyboxDesignerAction::RotationMinus => adjust(state, |d| d.rotation_y_degrees -= 5.0),
        SkyboxDesignerAction::RotationPlus => adjust(state, |d| d.rotation_y_degrees += 5.0),
        SkyboxDesignerAction::BrightnessMinus => adjust(state, |d| {
            d.skybox_brightness = (d.skybox_brightness - 500.0).max(0.0)
        }),
        SkyboxDesignerAction::BrightnessPlus => adjust(state, |d| d.skybox_brightness += 500.0),
        SkyboxDesignerAction::EnvironmentMinus => adjust(state, |d| {
            d.environment_intensity = (d.environment_intensity - 250.0).max(0.0)
        }),
        SkyboxDesignerAction::EnvironmentPlus => {
            adjust(state, |d| d.environment_intensity += 250.0)
        }
        SkyboxDesignerAction::CubemapNext => {
            let next = next_image_asset(state, &state.definition.cubemap);
            adjust(state, |d| d.cubemap = next)
        }
        SkyboxDesignerAction::DiffuseNext => {
            let next = next_image_asset(state, &state.definition.diffuse_map);
            adjust(state, |d| d.diffuse_map = next)
        }
        SkyboxDesignerAction::SpecularNext => {
            let next = next_image_asset(state, &state.definition.specular_map);
            adjust(state, |d| d.specular_map = next)
        }
        SkyboxDesignerAction::ClearImages => adjust(state, |d| {
            d.cubemap.clear();
            d.diffuse_map.clear();
            d.specular_map.clear();
        }),
        SkyboxDesignerAction::ToggleGeneratedEnvironmentMap => adjust(state, |d| {
            d.generated_environment_map = !d.generated_environment_map
        }),
        SkyboxDesignerAction::ToggleAtmosphereEnvironmentMap => adjust(state, |d| {
            d.atmosphere_environment_map = !d.atmosphere_environment_map
        }),
        SkyboxDesignerAction::ToggleLightmappedMeshes => adjust(state, |d| {
            d.affects_lightmapped_meshes = !d.affects_lightmapped_meshes
        }),
    }
}

fn adjust(state: &mut BevySkyboxDesignerState, f: impl FnOnce(&mut BevySkyboxDefinition)) -> bool {
    f(&mut state.definition);
    mark_dirty(state);
    true
}

fn mark_dirty(state: &mut BevySkyboxDesignerState) {
    state.dirty = true;
    state.status = "Unsaved changes".to_owned();
}

fn next_image_asset(state: &BevySkyboxDesignerState, current: &str) -> String {
    if state.image_assets.is_empty() {
        return current.to_owned();
    }
    let current = current.trim();
    let index = state
        .image_assets
        .iter()
        .position(|asset| asset.eq_ignore_ascii_case(current))
        .map(|index| index + 1)
        .unwrap_or(0)
        % state.image_assets.len();
    state.image_assets[index].clone()
}

fn next_palette_color(current: &str, palette: &[&str]) -> String {
    if palette.is_empty() {
        return current.to_owned();
    }
    let current = current.trim();
    let index = palette
        .iter()
        .position(|color| color.eq_ignore_ascii_case(current))
        .map(|index| index + 1)
        .unwrap_or(0)
        % palette.len();
    palette[index].to_owned()
}

fn halve_map_size(value: u32) -> u32 {
    (value.max(64) / 2).clamp(32, 4096)
}

fn double_map_size(value: u32) -> u32 {
    value.saturating_mul(2).clamp(32, 4096)
}

fn update_skybox_designer_buttons(
    state: Res<BevySkyboxDesignerState>,
    mut buttons: Query<
        (&Interaction, &SkyboxDesignerAction, &mut BackgroundColor),
        With<SkyboxDesignerButton>,
    >,
) {
    if !state.is_changed() {
        return;
    }
    for (interaction, action, mut color) in &mut buttons {
        let active = match action {
            SkyboxDesignerAction::ModeProcedural => is_procedural(&state.definition),
            SkyboxDesignerAction::ModeCubemap => is_cubemap(&state.definition),
            SkyboxDesignerAction::QualityLookup => !state
                .definition
                .atmosphere_quality
                .eq_ignore_ascii_case("raymarched"),
            SkyboxDesignerAction::QualityRaymarched => state
                .definition
                .atmosphere_quality
                .eq_ignore_ascii_case("raymarched"),
            SkyboxDesignerAction::ToggleGeneratedEnvironmentMap => {
                state.definition.generated_environment_map
            }
            SkyboxDesignerAction::ToggleAtmosphereEnvironmentMap => {
                state.definition.atmosphere_environment_map
            }
            SkyboxDesignerAction::ToggleVolumetricFog => state.definition.volumetric_fog,
            SkyboxDesignerAction::ToggleLightmappedMeshes => {
                state.definition.affects_lightmapped_meshes
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

fn update_skybox_designer_text(
    state: Res<BevySkyboxDesignerState>,
    mut summaries: Query<&mut Text, With<SkyboxSummaryText>>,
    mut statuses: Query<&mut Text, (With<SkyboxStatusText>, Without<SkyboxSummaryText>)>,
) {
    if !state.is_changed() {
        return;
    }
    for mut text in &mut summaries {
        *text = Text::new(summary_text(&state));
    }
    for mut text in &mut statuses {
        *text = Text::new(format!(
            "{}{}",
            state.status,
            if state.dirty { " *" } else { "" }
        ));
    }
}

fn keyboard_shortcuts(
    keyboard: Res<ButtonInput<KeyCode>>,
    mut state: ResMut<BevySkyboxDesignerState>,
) {
    if keyboard.pressed(KeyCode::ControlLeft) && keyboard.just_pressed(KeyCode::KeyS) {
        match save_skybox_definition_file(&state.skybox_file, &state.definition) {
            Ok(()) => {
                state.status = "Saved skybox definition".to_owned();
                state.dirty = false;
            }
            Err(err) => state.status = format!("Save failed: {err}"),
        }
    }
}

fn text(value: impl Into<String>, size: f32, color: Color) -> impl Bundle {
    (
        Text::new(value.into()),
        TextFont::from_font_size(size),
        TextColor(color),
    )
}

fn button_row() -> impl Bundle {
    Node {
        width: Val::Percent(100.0),
        flex_direction: FlexDirection::Row,
        flex_wrap: FlexWrap::Wrap,
        column_gap: px(8.0),
        row_gap: px(8.0),
        ..default()
    }
}

fn scroll_panel() -> impl Bundle {
    (
        Node {
            width: Val::Percent(100.0),
            flex_direction: FlexDirection::Column,
            flex_grow: 1.0,
            min_height: px(0.0),
            row_gap: px(10.0),
            padding: UiRect::right(px(8.0)),
            overflow: Overflow::scroll_y(),
            scrollbar_width: 10.0,
            ..default()
        },
        ScrollPosition(Vec2::ZERO),
    )
}

fn button(
    label: &'static str,
    action: SkyboxDesignerAction,
    active: bool,
    width: f32,
) -> impl Bundle {
    (
        Node {
            width: px(width),
            height: px(32.0),
            align_items: AlignItems::Center,
            justify_content: JustifyContent::Center,
            padding: UiRect::horizontal(px(8.0)),
            ..default()
        },
        Button,
        SkyboxDesignerButton,
        action,
        BackgroundColor(if active { BUTTON_ACTIVE } else { BUTTON_BG }),
        children![(
            Text::new(label),
            TextFont::from_font_size(12.0),
            TextColor(TEXT_MAIN)
        )],
    )
}

fn summary_text(state: &BevySkyboxDesignerState) -> String {
    let d = &state.definition;
    format!(
        "ID: {}\nFile: {}\nProject: {}\nMode: {}\nCubemap: {}\nDiffuse: {}\nSpecular: {}\nSkybox brightness: {:.0}\nHDRI IBL intensity: {:.0}\nAtmosphere IBL scale: {:.2}\nRotation Y: {:.1}\nAtmosphere preset: {}\nAtmosphere quality: {}\nAtmosphere IBL size: {}\nSun az/elev: {:.1} / {:.1}\nSun lux scale: {:.2}\nSun color: {}\nGround albedo: {}\nVolumetric fog: {} ({:.2})\nExposure EV100: {:.2}",
        d.id,
        state.skybox_file.display(),
        state.project_root.display(),
        d.mode,
        empty(&d.cubemap),
        empty(&d.diffuse_map),
        empty(&d.specular_map),
        d.skybox_brightness,
        d.environment_intensity,
        d.environment_intensity / 2000.0,
        d.rotation_y_degrees,
        d.atmosphere_preset,
        d.atmosphere_quality,
        d.atmosphere_environment_map_size,
        d.sun_azimuth_degrees,
        d.sun_elevation_degrees,
        d.sun_illuminance_scale,
        d.sun_color,
        d.ground_albedo,
        d.volumetric_fog,
        d.fog_ambient_intensity,
        d.exposure_ev100
    )
}

fn collect_image_assets(asset_root: &Path) -> Vec<String> {
    let mut result = Vec::new();
    let mut pending = vec![asset_root.to_path_buf()];
    while let Some(path) = pending.pop() {
        let Ok(metadata) = std::fs::metadata(&path) else {
            continue;
        };
        if metadata.is_dir() {
            let Ok(entries) = std::fs::read_dir(&path) else {
                continue;
            };
            for entry in entries.flatten() {
                pending.push(entry.path());
            }
            continue;
        }
        if !metadata.is_file() || !is_supported_image_asset(&path) {
            continue;
        }
        if let Ok(relative) = path.strip_prefix(asset_root) {
            result.push(relative.to_string_lossy().replace('\\', "/"));
        }
    }
    result.sort_by_key(|value| value.to_ascii_lowercase());
    result
}

fn is_supported_image_asset(path: &Path) -> bool {
    path.extension()
        .and_then(|value| value.to_str())
        .is_some_and(|ext| {
            matches!(
                ext.to_ascii_lowercase().as_str(),
                "ktx2" | "hdr" | "dds" | "png" | "jpg" | "jpeg" | "exr"
            )
        })
}

fn empty(value: &str) -> &str {
    if value.trim().is_empty() {
        "(not set)"
    } else {
        value.trim()
    }
}

fn is_procedural(definition: &BevySkyboxDefinition) -> bool {
    !is_cubemap(definition)
}

fn is_cubemap(definition: &BevySkyboxDefinition) -> bool {
    definition.mode.eq_ignore_ascii_case("hdri_cubemap")
}

fn resolve_project_root(launch: &BevySkyboxDesignerLaunch) -> PathBuf {
    if let Some(project_root) = launch.project_root.as_ref() {
        return project_root.clone();
    }
    let mut current = launch.skybox.parent();
    while let Some(path) = current {
        if path.join("resources").is_dir() && path.join("scripts").is_dir() {
            return path.to_path_buf();
        }
        current = path.parent();
    }
    launch
        .skybox
        .parent()
        .unwrap_or_else(|| Path::new("."))
        .to_path_buf()
}
