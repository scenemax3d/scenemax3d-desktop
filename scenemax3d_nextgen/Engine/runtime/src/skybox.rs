use std::{
    collections::VecDeque,
    fs,
    path::{Path, PathBuf},
};

use bevy::{
    camera::Exposure,
    core_pipeline::tonemapping::Tonemapping,
    light::{
        Atmosphere, AtmosphereEnvironmentMapLight, EnvironmentMapLight, FogVolume,
        GeneratedEnvironmentMapLight, Skybox, VolumetricFog, VolumetricLight,
        atmosphere::ScatteringMedium, light_consts::lux,
    },
    pbr::{AtmosphereMode, AtmosphereSettings},
    prelude::*,
};
use serde::{Deserialize, Serialize};

use super::{
    SceneMaxEntity, SceneMaxEnvironmentDirectionalLight, SceneMaxRuntimeAssets,
    format_scenemax_number, write_runtime_diagnostic_line,
};

const SKYBOX_EXTENSION: &str = "smskybox";
const MODE_HDRI_CUBEMAP: &str = "hdri_cubemap";
const MODE_PROCEDURAL_ATMOSPHERE: &str = "procedural_atmosphere";

#[derive(Component)]
struct SceneMaxSkyboxAtmosphere;

#[derive(Resource, Default)]
struct SceneMaxSkyboxRetainedAssets {
    scattering_media: Vec<Handle<ScatteringMedium>>,
    images: Vec<Handle<Image>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BevySkyboxDefinition {
    #[serde(default = "default_skybox_id")]
    pub id: String,
    #[serde(default = "default_skybox_display_name")]
    pub display_name: String,
    #[serde(default = "default_skybox_mode")]
    pub mode: String,
    #[serde(default)]
    pub cubemap: String,
    #[serde(default)]
    pub diffuse_map: String,
    #[serde(default)]
    pub specular_map: String,
    #[serde(default = "default_skybox_brightness")]
    pub skybox_brightness: f32,
    #[serde(default = "default_environment_intensity")]
    pub environment_intensity: f32,
    #[serde(default)]
    pub rotation_y_degrees: f32,
    #[serde(default = "default_true")]
    pub generated_environment_map: bool,
    #[serde(default = "default_true")]
    pub affects_lightmapped_meshes: bool,
    #[serde(default = "default_atmosphere_preset")]
    pub atmosphere_preset: String,
    #[serde(default = "default_atmosphere_quality")]
    pub atmosphere_quality: String,
    #[serde(default = "default_exposure_ev100")]
    pub exposure_ev100: f32,
    #[serde(default = "default_sun_azimuth")]
    pub sun_azimuth_degrees: f32,
    #[serde(default = "default_sun_elevation")]
    pub sun_elevation_degrees: f32,
    #[serde(default = "default_sun_illuminance_scale")]
    pub sun_illuminance_scale: f32,
    #[serde(default = "default_sun_color")]
    pub sun_color: String,
    #[serde(default = "default_ground_albedo")]
    pub ground_albedo: String,
    #[serde(default = "default_true")]
    pub atmosphere_environment_map: bool,
    #[serde(default = "default_environment_map_size")]
    pub atmosphere_environment_map_size: u32,
    #[serde(default)]
    pub volumetric_fog: bool,
    #[serde(default)]
    pub fog_ambient_intensity: f32,
    #[serde(default)]
    pub notes: String,
}

impl Default for BevySkyboxDefinition {
    fn default() -> Self {
        Self {
            id: default_skybox_id(),
            display_name: default_skybox_display_name(),
            mode: default_skybox_mode(),
            cubemap: String::new(),
            diffuse_map: String::new(),
            specular_map: String::new(),
            skybox_brightness: default_skybox_brightness(),
            environment_intensity: default_environment_intensity(),
            rotation_y_degrees: 0.0,
            generated_environment_map: true,
            affects_lightmapped_meshes: true,
            atmosphere_preset: default_atmosphere_preset(),
            atmosphere_quality: default_atmosphere_quality(),
            exposure_ev100: default_exposure_ev100(),
            sun_azimuth_degrees: default_sun_azimuth(),
            sun_elevation_degrees: default_sun_elevation(),
            sun_illuminance_scale: default_sun_illuminance_scale(),
            sun_color: default_sun_color(),
            ground_albedo: default_ground_albedo(),
            atmosphere_environment_map: true,
            atmosphere_environment_map_size: default_environment_map_size(),
            volumetric_fog: false,
            fog_ambient_intensity: 0.0,
            notes: String::new(),
        }
    }
}

pub(super) fn apply_skybox(
    commands: &mut Commands,
    skybox_name: String,
    runtime_assets: &SceneMaxRuntimeAssets,
) {
    let skybox_name = skybox_name.trim().to_owned();
    if skybox_name.is_empty() || skybox_name.eq_ignore_ascii_case("none") {
        commands.queue(clear_skybox_world);
        write_runtime_diagnostic_line("SKYBOX cleared");
        return;
    }

    let Some(asset_root) = runtime_assets.asset_root.as_deref() else {
        write_runtime_diagnostic_line(format!(
            "SKYBOX:MISS name={skybox_name} reason=no_asset_root"
        ));
        return;
    };
    let Some(asset_server) = runtime_assets.asset_server.clone() else {
        write_runtime_diagnostic_line(format!(
            "SKYBOX:MISS name={skybox_name} reason=no_asset_server"
        ));
        return;
    };

    let Some((definition, source_path)) = load_skybox_definition(asset_root, &skybox_name) else {
        write_runtime_diagnostic_line(format!(
            "SKYBOX:MISS name={skybox_name} reason=definition_not_found"
        ));
        return;
    };

    let asset_root = asset_root.to_path_buf();
    commands.queue(move |world: &mut World| {
        apply_skybox_definition_world(world, &asset_server, &asset_root, &definition);
        write_runtime_diagnostic_line(format!(
            "SKYBOX applied id={} mode={} source={}",
            definition.id,
            definition.mode,
            source_path.display()
        ));
    });
}

fn clear_skybox_world(world: &mut World) {
    let cameras: Vec<Entity> = world
        .query_filtered::<Entity, (With<Camera3d>, Without<SceneMaxEntity>)>()
        .iter(world)
        .collect();
    for entity in cameras {
        if let Ok(mut entity_mut) = world.get_entity_mut(entity) {
            entity_mut.remove::<(
                Skybox,
                EnvironmentMapLight,
                GeneratedEnvironmentMapLight,
                AtmosphereSettings,
                AtmosphereEnvironmentMapLight,
                Exposure,
                VolumetricFog,
                Tonemapping,
            )>();
        }
    }

    let lights: Vec<Entity> = world
        .query_filtered::<Entity, With<SceneMaxEnvironmentDirectionalLight>>()
        .iter(world)
        .collect();
    for entity in lights {
        if let Ok(mut entity_mut) = world.get_entity_mut(entity) {
            entity_mut.remove::<VolumetricLight>();
        }
    }

    let atmospheres: Vec<Entity> = world
        .query_filtered::<Entity, With<SceneMaxSkyboxAtmosphere>>()
        .iter(world)
        .collect();
    for entity in atmospheres {
        let _ = world.despawn(entity);
    }
}

pub(crate) fn apply_skybox_definition_world(
    world: &mut World,
    asset_server: &AssetServer,
    asset_root: &Path,
    definition: &BevySkyboxDefinition,
) {
    clear_skybox_world(world);
    match normalized_mode(&definition.mode).as_str() {
        MODE_HDRI_CUBEMAP => apply_hdri_cubemap_world(world, asset_server, asset_root, definition),
        _ => apply_procedural_atmosphere_world(world, definition),
    }
}

fn apply_hdri_cubemap_world(
    world: &mut World,
    asset_server: &AssetServer,
    asset_root: &Path,
    definition: &BevySkyboxDefinition,
) {
    let rotation = Quat::from_rotation_y(definition.rotation_y_degrees.to_radians());
    let cubemap = normalized_asset_path(asset_root, &definition.cubemap);
    let diffuse_map = normalized_asset_path(asset_root, &definition.diffuse_map);
    let specular_map = normalized_asset_path(asset_root, &definition.specular_map);

    let cameras: Vec<Entity> = world
        .query_filtered::<Entity, (With<Camera3d>, Without<SceneMaxEntity>)>()
        .iter(world)
        .collect();

    if cubemap.is_none() {
        let mut fallback_environment = {
            let mut images = world.resource_mut::<Assets<Image>>();
            EnvironmentMapLight::hemispherical_gradient(
                &mut images,
                Color::srgb(0.32, 0.58, 0.98),
                Color::srgb(0.72, 0.84, 0.92),
                Color::srgb(0.08, 0.10, 0.12),
            )
        };
        fallback_environment.intensity = definition.environment_intensity.max(0.0);
        fallback_environment.rotation = rotation;
        fallback_environment.affects_lightmapped_mesh_diffuse =
            definition.affects_lightmapped_meshes;
        retain_skybox_image(world, fallback_environment.diffuse_map.clone());
        for entity in cameras {
            if let Ok(mut entity_mut) = world.get_entity_mut(entity) {
                entity_mut.insert((
                    Skybox {
                        image: Some(fallback_environment.diffuse_map.clone()),
                        brightness: definition.skybox_brightness.max(0.0),
                        rotation,
                    },
                    fallback_environment.clone(),
                ));
            }
        }
        write_runtime_diagnostic_line(
            "SKYBOX:HDRI using generated gradient because no cubemap asset is set",
        );
        return;
    }

    let cubemap_handle: Handle<Image> = asset_server.load(cubemap.unwrap());
    retain_skybox_image(world, cubemap_handle.clone());
    let explicit_environment_handles = if definition.generated_environment_map {
        None
    } else if let (Some(diffuse_map), Some(specular_map)) =
        (diffuse_map.clone(), specular_map.clone())
    {
        let diffuse_handle = asset_server.load(diffuse_map);
        let specular_handle = asset_server.load(specular_map);
        retain_skybox_image(world, diffuse_handle.clone());
        retain_skybox_image(world, specular_handle.clone());
        Some((diffuse_handle, specular_handle))
    } else {
        None
    };
    for entity in cameras {
        if let Ok(mut entity_mut) = world.get_entity_mut(entity) {
            entity_mut.insert(Skybox {
                image: Some(cubemap_handle.clone()),
                brightness: definition.skybox_brightness.max(0.0),
                rotation,
            });

            if definition.generated_environment_map {
                entity_mut.insert(GeneratedEnvironmentMapLight {
                    environment_map: cubemap_handle.clone(),
                    intensity: definition.environment_intensity.max(0.0),
                    rotation,
                    affects_lightmapped_mesh_diffuse: definition.affects_lightmapped_meshes,
                });
            } else if let Some((diffuse_handle, specular_handle)) =
                explicit_environment_handles.as_ref()
            {
                entity_mut.insert(EnvironmentMapLight {
                    diffuse_map: diffuse_handle.clone(),
                    specular_map: specular_handle.clone(),
                    intensity: definition.environment_intensity.max(0.0),
                    rotation,
                    affects_lightmapped_mesh_diffuse: definition.affects_lightmapped_meshes,
                });
            }
        }
    }
}

fn apply_procedural_atmosphere_world(world: &mut World, definition: &BevySkyboxDefinition) {
    let medium = {
        let mut scattering_mediums = world.resource_mut::<Assets<ScatteringMedium>>();
        scattering_mediums.add(ScatteringMedium::earth(256, 256))
    };
    retain_skybox_scattering_medium(world, medium.clone());
    let mut atmosphere = Atmosphere::earth(medium);
    if let Some(albedo) = vec3_from_hex(&definition.ground_albedo) {
        atmosphere.ground_albedo = albedo;
    }
    world.spawn((atmosphere, SceneMaxSkyboxAtmosphere));

    let mode = if definition
        .atmosphere_quality
        .eq_ignore_ascii_case("raymarched")
    {
        AtmosphereMode::Raymarched
    } else {
        AtmosphereMode::LookupTexture
    };

    let cameras: Vec<Entity> = world
        .query_filtered::<Entity, (With<Camera3d>, Without<SceneMaxEntity>)>()
        .iter(world)
        .collect();
    for entity in cameras {
        if let Ok(mut entity_mut) = world.get_entity_mut(entity) {
            entity_mut.insert((
                AtmosphereSettings {
                    rendering_method: mode,
                    ..default()
                },
                Exposure {
                    ev100: definition.exposure_ev100,
                },
                Tonemapping::AcesFitted,
            ));
            if definition.atmosphere_environment_map {
                let size = definition.atmosphere_environment_map_size.clamp(32, 4096);
                entity_mut.insert(AtmosphereEnvironmentMapLight {
                    intensity: atmosphere_environment_intensity(definition),
                    affects_lightmapped_mesh_diffuse: definition.affects_lightmapped_meshes,
                    size: UVec2::new(size, size),
                });
            }
            if definition.volumetric_fog {
                entity_mut.insert(VolumetricFog {
                    ambient_intensity: definition.fog_ambient_intensity.max(0.0),
                    ..default()
                });
            }
        }
    }

    let direction = sun_direction(
        definition.sun_azimuth_degrees,
        definition.sun_elevation_degrees,
    );
    let mut volumetric_light_entities = Vec::new();
    for (entity, mut light, mut transform) in world
        .query_filtered::<
            (Entity, &mut DirectionalLight, &mut Transform),
            With<SceneMaxEnvironmentDirectionalLight>,
        >()
        .iter_mut(world)
    {
        light.illuminance = lux::RAW_SUNLIGHT * definition.sun_illuminance_scale.max(0.0);
        light.color = color_from_hex(&definition.sun_color).unwrap_or(Color::WHITE);
        *transform = Transform::from_translation(direction * 100.0).looking_at(Vec3::ZERO, Vec3::Y);
        if definition.volumetric_fog {
            volumetric_light_entities.push(entity);
        }
    }
    for entity in volumetric_light_entities {
        world.entity_mut(entity).insert(VolumetricLight);
    }

    if definition.volumetric_fog {
        world.spawn((
            FogVolume::default(),
            Transform::from_scale(Vec3::new(1000.0, 500.0, 1000.0))
                .with_translation(Vec3::Y * 40.0),
            SceneMaxSkyboxAtmosphere,
        ));
    }

    write_runtime_diagnostic_line(format!(
        "SKYBOX:ATMOSPHERE sun=({}, {}, {}) exposure={}",
        format_scenemax_number(direction.x),
        format_scenemax_number(direction.y),
        format_scenemax_number(direction.z),
        format_scenemax_number(definition.exposure_ev100)
    ));
}

fn retain_skybox_scattering_medium(world: &mut World, handle: Handle<ScatteringMedium>) {
    if !world.contains_resource::<SceneMaxSkyboxRetainedAssets>() {
        world.insert_resource(SceneMaxSkyboxRetainedAssets::default());
    }
    world
        .resource_mut::<SceneMaxSkyboxRetainedAssets>()
        .scattering_media
        .push(handle);
}

fn retain_skybox_image(world: &mut World, handle: Handle<Image>) {
    if !world.contains_resource::<SceneMaxSkyboxRetainedAssets>() {
        world.insert_resource(SceneMaxSkyboxRetainedAssets::default());
    }
    world
        .resource_mut::<SceneMaxSkyboxRetainedAssets>()
        .images
        .push(handle);
}

pub(super) fn load_skybox_definition(
    asset_root: &Path,
    skybox_name: &str,
) -> Option<(BevySkyboxDefinition, PathBuf)> {
    let project_root = asset_root.parent().unwrap_or(asset_root);
    let wanted = skybox_name.trim();
    if wanted.is_empty() {
        return None;
    }

    for path in direct_skybox_paths(project_root, wanted) {
        if let Some(definition) = read_skybox_definition(&path)
            && definition_matches(&definition, &path, wanted)
        {
            return Some((definition, path));
        }
    }

    for path in find_skybox_files(project_root) {
        if let Some(definition) = read_skybox_definition(&path)
            && definition_matches(&definition, &path, wanted)
        {
            return Some((definition, path));
        }
    }
    None
}

fn definition_matches(definition: &BevySkyboxDefinition, path: &Path, wanted: &str) -> bool {
    definition.id.eq_ignore_ascii_case(wanted)
        || definition.display_name.eq_ignore_ascii_case(wanted)
        || path
            .file_stem()
            .and_then(|value| value.to_str())
            .is_some_and(|stem| stem.eq_ignore_ascii_case(wanted))
}

fn direct_skybox_paths(project_root: &Path, skybox_name: &str) -> Vec<PathBuf> {
    let file_name = if skybox_name
        .to_ascii_lowercase()
        .ends_with(&format!(".{SKYBOX_EXTENSION}"))
    {
        skybox_name.to_owned()
    } else {
        format!("{skybox_name}.{SKYBOX_EXTENSION}")
    };
    vec![
        project_root.join(&file_name),
        project_root.join("scripts").join(&file_name),
        project_root
            .join("resources")
            .join("skyboxes")
            .join(&file_name),
        project_root
            .join("resources")
            .join("Skyboxes")
            .join(&file_name),
    ]
}

fn find_skybox_files(root: &Path) -> Vec<PathBuf> {
    let mut result = Vec::new();
    let mut pending = VecDeque::new();
    pending.push_back(root.to_path_buf());
    while let Some(path) = pending.pop_front() {
        let Ok(metadata) = fs::metadata(&path) else {
            continue;
        };
        if metadata.is_file() {
            if path
                .extension()
                .and_then(|value| value.to_str())
                .is_some_and(|ext| ext.eq_ignore_ascii_case(SKYBOX_EXTENSION))
            {
                result.push(path);
            }
            continue;
        }
        if !metadata.is_dir() || skip_scan_dir(&path) {
            continue;
        }
        let Ok(entries) = fs::read_dir(&path) else {
            continue;
        };
        for entry in entries.flatten() {
            pending.push_back(entry.path());
        }
    }
    result
}

fn skip_scan_dir(path: &Path) -> bool {
    path.file_name()
        .and_then(|value| value.to_str())
        .is_some_and(|name| {
            matches!(
                name.to_ascii_lowercase().as_str(),
                ".git" | "target" | "node_modules" | ".gradle"
            )
        })
}

fn read_skybox_definition(path: &Path) -> Option<BevySkyboxDefinition> {
    let text = fs::read_to_string(path).ok()?;
    serde_json::from_str(&text).ok()
}

pub(crate) fn load_skybox_definition_file(path: &Path) -> BevySkyboxDefinition {
    read_skybox_definition(path).unwrap_or_default()
}

pub(crate) fn save_skybox_definition_file(
    path: &Path,
    definition: &BevySkyboxDefinition,
) -> anyhow::Result<()> {
    let text = serde_json::to_string_pretty(definition)?;
    fs::write(path, text)?;
    Ok(())
}

fn normalized_asset_path(asset_root: &Path, value: &str) -> Option<String> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return None;
    }
    let path = Path::new(trimmed);
    if path.is_absolute() {
        if let Ok(relative) = path.strip_prefix(asset_root) {
            return Some(relative.to_string_lossy().replace('\\', "/"));
        }
    }
    Some(trimmed.replace('\\', "/"))
}

fn normalized_mode(mode: &str) -> String {
    let normalized = mode.trim().to_ascii_lowercase().replace(['-', ' '], "_");
    if normalized == MODE_HDRI_CUBEMAP {
        MODE_HDRI_CUBEMAP.to_owned()
    } else {
        MODE_PROCEDURAL_ATMOSPHERE.to_owned()
    }
}

fn sun_direction(azimuth_degrees: f32, elevation_degrees: f32) -> Vec3 {
    let azimuth = azimuth_degrees.to_radians();
    let elevation = elevation_degrees.to_radians();
    Vec3::new(
        elevation.cos() * azimuth.sin(),
        elevation.sin(),
        elevation.cos() * azimuth.cos(),
    )
    .normalize_or_zero()
}

fn color_from_hex(value: &str) -> Option<Color> {
    let [r, g, b] = parse_hex_rgb(value)?;
    Some(Color::srgb_u8(r, g, b))
}

fn vec3_from_hex(value: &str) -> Option<Vec3> {
    let [r, g, b] = parse_hex_rgb(value)?;
    Some(Vec3::new(
        r as f32 / 255.0,
        g as f32 / 255.0,
        b as f32 / 255.0,
    ))
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

fn atmosphere_environment_intensity(definition: &BevySkyboxDefinition) -> f32 {
    (definition.environment_intensity / default_environment_intensity()).clamp(0.0, 16.0)
}

fn default_skybox_id() -> String {
    "skybox".to_owned()
}

fn default_skybox_display_name() -> String {
    "Skybox".to_owned()
}

fn default_skybox_mode() -> String {
    MODE_PROCEDURAL_ATMOSPHERE.to_owned()
}

fn default_skybox_brightness() -> f32 {
    5000.0
}

fn default_environment_intensity() -> f32 {
    2000.0
}

fn default_true() -> bool {
    true
}

fn default_atmosphere_preset() -> String {
    "EARTH".to_owned()
}

fn default_atmosphere_quality() -> String {
    "LOOKUP_TEXTURE".to_owned()
}

fn default_exposure_ev100() -> f32 {
    13.0
}

fn default_sun_azimuth() -> f32 {
    35.0
}

fn default_sun_elevation() -> f32 {
    42.0
}

fn default_sun_illuminance_scale() -> f32 {
    1.0
}

fn default_sun_color() -> String {
    "#fff4d6".to_owned()
}

fn default_ground_albedo() -> String {
    "#4d4d4d".to_owned()
}

fn default_environment_map_size() -> u32 {
    512
}
