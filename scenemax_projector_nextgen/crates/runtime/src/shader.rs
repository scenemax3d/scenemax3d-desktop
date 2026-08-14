use super::*;

const DEFAULT_AMBIENT_BRIGHTNESS: f32 = 220.0;
const DEFAULT_DIRECTIONAL_ILLUMINANCE: f32 = 24_000.0;

#[derive(Debug, Clone)]
pub(super) struct SceneMaxObjectShader {
    name: String,
    main_color: [f32; 4],
    glow_strength: f32,
    transparency: f32,
    use_original_texture: bool,
    blocks: HashSet<String>,
}

#[derive(Debug, Clone)]
pub(super) struct SceneMaxEnvironmentShader {
    name: String,
    layers: HashSet<String>,
    fog_color: [f32; 4],
    fog_density: f32,
    rain_color: [f32; 4],
    rain_intensity: f32,
    rain_overlay_opacity: f32,
    snow_color: [f32; 4],
    snow_intensity: f32,
    sky_tint: [f32; 4],
    sky_brightness: f32,
    ambient_color: [f32; 4],
    ambient_intensity: f32,
    light_color: [f32; 4],
    light_intensity: f32,
    light_pitch: f32,
    light_yaw: f32,
}

#[derive(Debug, Clone, Component)]
pub(super) struct SceneMaxOriginalMaterial(pub(super) Handle<StandardMaterial>);

#[derive(Debug, Component)]
pub(super) struct SceneMaxShaderApplied;

#[derive(Debug, Component)]
pub(super) struct SceneMaxEnvironmentDirectionalLight;

pub(super) fn apply_entity_shader(
    commands: &mut Commands,
    target: Entity,
    shader_name: String,
    runtime_assets: &SceneMaxRuntimeAssets,
) {
    let asset_root = runtime_assets.asset_root.clone();
    let builtin_asset_root = runtime_assets.builtin_asset_root.clone();
    let shader = if shader_name.trim().is_empty() {
        None
    } else {
        match resolve_object_shader(
            &shader_name,
            asset_root.as_deref(),
            builtin_asset_root.as_deref(),
        ) {
            Some(shader) => Some(shader),
            None => {
                write_runtime_diagnostic_line(format!(
                    "SHADER:MISS target={target:?} name={}",
                    shader_name.trim()
                ));
                tracing::warn!(shader = %shader_name, "SceneMax shader resource was not found");
                return;
            }
        }
    };

    commands.queue(move |world: &mut World| {
        let mut targets = Vec::new();
        collect_entity_and_descendants(world, target, &mut targets);
        if targets.is_empty() {
            return;
        }
        if let Some(shader) = shader {
            apply_object_shader_to_entities(world, &targets, &shader);
        } else {
            restore_original_materials(world, &targets);
        }
    });
}

pub(super) fn apply_environment_shader(
    commands: &mut Commands,
    shader_name: String,
    runtime_assets: &SceneMaxRuntimeAssets,
) {
    let asset_root = runtime_assets.asset_root.clone();
    let builtin_asset_root = runtime_assets.builtin_asset_root.clone();
    let shader = if shader_name.trim().is_empty() {
        None
    } else {
        match resolve_environment_shader(
            &shader_name,
            asset_root.as_deref(),
            builtin_asset_root.as_deref(),
        ) {
            Some(shader) => Some(shader),
            None => {
                write_runtime_diagnostic_line(format!(
                    "ENV_SHADER:MISS name={}",
                    shader_name.trim()
                ));
                tracing::warn!(
                    shader = %shader_name,
                    "SceneMax environment shader resource was not found"
                );
                return;
            }
        }
    };

    commands.queue(move |world: &mut World| match shader {
        Some(shader) => apply_environment_shader_to_world(world, &shader),
        None => reset_environment_shader_world(world),
    });
}

pub(super) fn clear_environment_shader(commands: &mut Commands) {
    commands.queue(|world: &mut World| {
        reset_environment_shader_world(world);
    });
}

fn collect_entity_and_descendants(world: &World, entity: Entity, output: &mut Vec<Entity>) {
    output.push(entity);
    let Some(children) = world.get::<Children>(entity) else {
        return;
    };
    let child_entities = children.iter().collect::<Vec<_>>();
    for child in child_entities {
        collect_entity_and_descendants(world, child, output);
    }
}

fn apply_object_shader_to_entities(
    world: &mut World,
    targets: &[Entity],
    shader: &SceneMaxObjectShader,
) {
    let mut applied = 0usize;
    for entity in targets {
        let Some(current_material) = world
            .get::<MeshMaterial3d<StandardMaterial>>(*entity)
            .map(|material| material.0.clone())
        else {
            continue;
        };
        let original_material = world
            .get::<SceneMaxOriginalMaterial>(*entity)
            .map(|material| material.0.clone())
            .unwrap_or_else(|| current_material.clone());
        if world.get::<SceneMaxOriginalMaterial>(*entity).is_none() {
            world
                .entity_mut(*entity)
                .insert(SceneMaxOriginalMaterial(original_material.clone()));
        }
        let source_material = world
            .resource::<Assets<StandardMaterial>>()
            .get(&original_material)
            .cloned();
        let shader_material = shader.standard_material(source_material.as_ref());
        let shader_handle = world
            .resource_mut::<Assets<StandardMaterial>>()
            .add(shader_material);
        world
            .entity_mut(*entity)
            .insert((MeshMaterial3d(shader_handle), SceneMaxShaderApplied));
        applied += 1;
    }
    write_runtime_diagnostic_line(format!(
        "SHADER:APPLY name={} entities={applied} tint=({:.3},{:.3},{:.3},{:.3}) glow={:.3} transparency={:.3}",
        shader.name,
        shader.main_color[0],
        shader.main_color[1],
        shader.main_color[2],
        shader.main_color[3],
        shader.glow_strength,
        shader.transparency
    ));
}

fn restore_original_materials(world: &mut World, targets: &[Entity]) {
    let mut restored = 0usize;
    for entity in targets {
        let Some(original) = world.get::<SceneMaxOriginalMaterial>(*entity).cloned() else {
            continue;
        };
        world.entity_mut(*entity).insert(MeshMaterial3d(original.0));
        world
            .entity_mut(*entity)
            .remove::<SceneMaxOriginalMaterial>();
        world.entity_mut(*entity).remove::<SceneMaxShaderApplied>();
        restored += 1;
    }
    write_runtime_diagnostic_line(format!("SHADER:CLEAR entities={restored}"));
}

fn apply_environment_shader_to_world(world: &mut World, shader: &SceneMaxEnvironmentShader) {
    let ambient_color = color_from_rgba(shader.ambient_color);
    world.insert_resource(GlobalAmbientLight {
        color: ambient_color,
        brightness: DEFAULT_AMBIENT_BRIGHTNESS * shader.ambient_intensity.max(0.0),
        ..default()
    });
    world.insert_resource(ClearColor(color_from_rgba(environment_clear_color(shader))));

    let mut lights = world.query_filtered::<
        (&mut DirectionalLight, &mut Transform),
        With<SceneMaxEnvironmentDirectionalLight>,
    >();
    for (mut light, mut transform) in lights.iter_mut(world) {
        light.color = color_from_rgba(shader.light_color);
        light.illuminance = DEFAULT_DIRECTIONAL_ILLUMINANCE * shader.light_intensity.max(0.0);
        *transform = Transform::from_rotation(Quat::from_euler(
            EulerRot::YXZ,
            shader.light_yaw.to_radians(),
            shader.light_pitch.to_radians(),
            0.0,
        ));
    }

    write_runtime_diagnostic_line(format!(
        "ENV_SHADER:APPLY name={} layers={} ambient={:.3} light={:.3} screen_overlay=disabled",
        shader.name,
        shader.layers.iter().cloned().collect::<Vec<_>>().join("|"),
        shader.ambient_intensity,
        shader.light_intensity,
    ));
}

fn reset_environment_shader_world(world: &mut World) {
    world.insert_resource(GlobalAmbientLight {
        color: Color::WHITE,
        brightness: DEFAULT_AMBIENT_BRIGHTNESS,
        ..default()
    });
    let mut lights = world.query_filtered::<
        (&mut DirectionalLight, &mut Transform),
        With<SceneMaxEnvironmentDirectionalLight>,
    >();
    for (mut light, mut transform) in lights.iter_mut(world) {
        light.color = Color::WHITE;
        light.illuminance = DEFAULT_DIRECTIONAL_ILLUMINANCE;
        light.shadow_maps_enabled = true;
        light.shadow_depth_bias = 0.08;
        light.shadow_normal_bias = 1.8;
        *transform = Transform::from_xyz(-8.0, 14.0, 8.0).looking_at(Vec3::ZERO, Vec3::Y);
    }
    write_runtime_diagnostic_line("ENV_SHADER:CLEAR");
}

impl SceneMaxObjectShader {
    fn standard_material(&self, source: Option<&StandardMaterial>) -> StandardMaterial {
        let mut material = source.cloned().unwrap_or_default();
        let alpha =
            (self.main_color[3] * (1.0 - self.transparency.clamp(0.0, 1.0))).clamp(0.0, 1.0);
        if self.blocks.contains("TINT") || self.blocks.contains("TOON_RAMP") {
            material.base_color = Color::srgba(
                self.main_color[0],
                self.main_color[1],
                self.main_color[2],
                alpha,
            );
        } else {
            material.base_color = material.base_color.with_alpha(alpha);
        }
        if !self.use_original_texture {
            material.base_color_texture = None;
            material.normal_map_texture = None;
            material.emissive_texture = None;
        }
        if self.blocks.contains("GLOW")
            || self.blocks.contains("PULSE")
            || self.blocks.contains("RIM_LIGHT")
        {
            let glow = self.glow_strength.max(0.0);
            material.emissive = LinearRgba::new(
                self.main_color[0] * glow,
                self.main_color[1] * glow,
                self.main_color[2] * glow,
                1.0,
            );
        }
        if alpha < 0.999 || self.blocks.contains("TRANSPARENCY") {
            material.alpha_mode = AlphaMode::Blend;
        }
        material
    }
}

fn resolve_object_shader(
    name: &str,
    asset_root: Option<&Path>,
    builtin_asset_root: Option<&Path>,
) -> Option<SceneMaxObjectShader> {
    resolve_shader_document(name, "shaders", "shaders-ext.json", "shaders", asset_root)
        .or_else(|| resolve_shader_document(name, "shaders", "shaders.json", "shaders", asset_root))
        .or_else(|| {
            resolve_shader_document(
                name,
                "shaders",
                "shaders-ext.json",
                "shaders",
                builtin_asset_root,
            )
        })
        .or_else(|| {
            resolve_shader_document(
                name,
                "shaders",
                "shaders.json",
                "shaders",
                builtin_asset_root,
            )
        })
        .and_then(|document| document.into_object_shader(name))
        .or_else(|| {
            find_source_shader_document(name, ".smshader", asset_root)
                .and_then(|value| ShaderDocumentSource::Json(value).into_object_shader(name))
        })
}

fn resolve_environment_shader(
    name: &str,
    asset_root: Option<&Path>,
    builtin_asset_root: Option<&Path>,
) -> Option<SceneMaxEnvironmentShader> {
    resolve_shader_document(
        name,
        "environment_shaders",
        "environment-shaders-ext.json",
        "environmentShaders",
        asset_root,
    )
    .or_else(|| {
        resolve_shader_document(
            name,
            "environment_shaders",
            "environment-shaders.json",
            "environmentShaders",
            asset_root,
        )
    })
    .or_else(|| {
        resolve_shader_document(
            name,
            "environment_shaders",
            "environment-shaders-ext.json",
            "environmentShaders",
            builtin_asset_root,
        )
    })
    .or_else(|| {
        resolve_shader_document(
            name,
            "environment_shaders",
            "environment-shaders.json",
            "environmentShaders",
            builtin_asset_root,
        )
    })
    .and_then(|document| document.into_environment_shader(name))
    .or_else(|| {
        find_source_shader_document(name, ".smenvshader", asset_root)
            .and_then(|value| ShaderDocumentSource::Json(value).into_environment_shader(name))
    })
}

enum ShaderDocumentSource {
    Json(serde_json::Value),
    J3m(String),
}

impl ShaderDocumentSource {
    fn into_object_shader(self, fallback_name: &str) -> Option<SceneMaxObjectShader> {
        match self {
            ShaderDocumentSource::Json(value) => Some(SceneMaxObjectShader {
                name: value
                    .get("name")
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or(fallback_name)
                    .to_owned(),
                main_color: rgba_array(value.get("mainColor"), [1.0, 1.0, 1.0, 1.0]),
                glow_strength: f32_field(&value, "glowStrength", 0.15),
                transparency: f32_field(&value, "transparency", 0.0),
                use_original_texture: bool_field(&value, "useOriginalTexture", true),
                blocks: string_set(value.get("blocks")),
            }),
            ShaderDocumentSource::J3m(source) => {
                Some(parse_j3m_object_shader(&source, fallback_name))
            }
        }
    }

    fn into_environment_shader(self, fallback_name: &str) -> Option<SceneMaxEnvironmentShader> {
        match self {
            ShaderDocumentSource::Json(value) => {
                Some(parse_environment_shader_json(&value, fallback_name))
            }
            ShaderDocumentSource::J3m(_) => None,
        }
    }
}

fn resolve_shader_document(
    name: &str,
    folder: &str,
    index_file: &str,
    array_key: &str,
    root: Option<&Path>,
) -> Option<ShaderDocumentSource> {
    let root = root?;
    let source = fs::read_to_string(root.join(folder).join(index_file)).ok()?;
    let index = serde_json::from_str::<serde_json::Value>(&source).ok()?;
    let entries = index.get(array_key).and_then(serde_json::Value::as_array)?;
    let entry = entries.iter().find(|entry| {
        entry
            .get("name")
            .and_then(serde_json::Value::as_str)
            .is_some_and(|entry_name| entry_name.eq_ignore_ascii_case(name))
    })?;
    let path = entry.get("path").and_then(serde_json::Value::as_str)?;
    let path = root.join(path.replace('/', std::path::MAIN_SEPARATOR_STR));
    let json_path = if folder == "environment_shaders" {
        path.parent().map(|parent| parent.join("environment.json"))
    } else {
        None
    };
    if let Some(json_path) = json_path
        && let Ok(source) = fs::read_to_string(json_path)
        && let Ok(value) = serde_json::from_str::<serde_json::Value>(&source)
    {
        return Some(ShaderDocumentSource::Json(value));
    }
    let j3m_path = path.with_extension("j3m");
    fs::read_to_string(j3m_path)
        .ok()
        .map(ShaderDocumentSource::J3m)
}

fn find_source_shader_document(
    name: &str,
    extension: &str,
    asset_root: Option<&Path>,
) -> Option<serde_json::Value> {
    let project_root = asset_root?.parent()?;
    let scripts = project_root.join("scripts");
    let file_name = format!("{name}{extension}");
    find_file_recursively(&scripts, &file_name, 64).and_then(|path| {
        fs::read_to_string(path)
            .ok()
            .and_then(|source| serde_json::from_str::<serde_json::Value>(&source).ok())
    })
}

fn find_file_recursively(root: &Path, file_name: &str, limit: usize) -> Option<PathBuf> {
    if limit == 0 {
        return None;
    }
    for entry in fs::read_dir(root).ok()?.filter_map(Result::ok) {
        let path = entry.path();
        if path.is_file()
            && path
                .file_name()
                .and_then(|name| name.to_str())
                .is_some_and(|name| name.eq_ignore_ascii_case(file_name))
        {
            return Some(path);
        }
        if path.is_dir()
            && let Some(found) = find_file_recursively(&path, file_name, limit - 1)
        {
            return Some(found);
        }
    }
    None
}

fn parse_j3m_object_shader(source: &str, fallback_name: &str) -> SceneMaxObjectShader {
    let mut shader = SceneMaxObjectShader {
        name: fallback_name.to_owned(),
        main_color: [1.0, 1.0, 1.0, 1.0],
        glow_strength: 0.15,
        transparency: 0.0,
        use_original_texture: true,
        blocks: HashSet::from(["TINT".to_owned()]),
    };
    for line in source.lines().map(str::trim) {
        let Some((key, value)) = line.split_once(':') else {
            continue;
        };
        match key.trim() {
            "MainColor" => shader.main_color = parse_float_array(value, shader.main_color),
            "GlowStrength" => {
                shader.glow_strength = value.trim().parse().unwrap_or(shader.glow_strength)
            }
            "Transparency" => {
                shader.transparency = value.trim().parse().unwrap_or(shader.transparency)
            }
            "UseOriginalTexture" => {
                shader.use_original_texture = value.trim().eq_ignore_ascii_case("true")
            }
            _ => {}
        }
    }
    shader
}

fn parse_environment_shader_json(
    value: &serde_json::Value,
    fallback_name: &str,
) -> SceneMaxEnvironmentShader {
    SceneMaxEnvironmentShader {
        name: value
            .get("name")
            .and_then(serde_json::Value::as_str)
            .unwrap_or(fallback_name)
            .to_owned(),
        layers: string_set(value.get("layers")),
        fog_color: nested_rgba(value, "fog", "color", "fogColor", [0.72, 0.8, 0.9, 1.0]),
        fog_density: nested_f32(value, "fog", "density", "fogDensity", 0.0),
        rain_color: nested_rgba(value, "rain", "color", "rainColor", [0.82, 0.9, 1.0, 1.0]),
        rain_intensity: nested_f32(value, "rain", "intensity", "rainIntensity", 0.0),
        rain_overlay_opacity: nested_f32(value, "rain", "overlayOpacity", "overlayOpacity", 0.0),
        snow_color: nested_rgba(value, "snow", "color", "snowColor", [0.95, 0.97, 1.0, 1.0]),
        snow_intensity: nested_f32(value, "snow", "intensity", "snowIntensity", 0.0),
        sky_tint: nested_rgba(
            value,
            "skyTweaks",
            "tint",
            "skyTint",
            [0.52, 0.67, 0.92, 1.0],
        ),
        sky_brightness: nested_f32(value, "skyTweaks", "brightness", "skyBrightness", 1.0),
        ambient_color: nested_rgba(
            value,
            "ambientColor",
            "color",
            "ambientColor",
            [1.0, 1.0, 1.0, 1.0],
        ),
        ambient_intensity: nested_f32(value, "ambientColor", "intensity", "ambientIntensity", 1.0),
        light_color: nested_rgba(
            value,
            "lighting",
            "color",
            "lightColor",
            [1.0, 1.0, 1.0, 1.0],
        ),
        light_intensity: nested_f32(value, "lighting", "intensity", "lightIntensity", 1.0),
        light_pitch: nested_f32(value, "lighting", "pitch", "lightPitch", -35.0),
        light_yaw: nested_f32(value, "lighting", "yaw", "lightYaw", -35.0),
    }
}

fn environment_clear_color(shader: &SceneMaxEnvironmentShader) -> [f32; 4] {
    let mut color = [
        shader.sky_tint[0] * shader.sky_brightness,
        shader.sky_tint[1] * shader.sky_brightness,
        shader.sky_tint[2] * shader.sky_brightness,
        1.0,
    ];
    if shader.layers.contains("FOG") {
        color = mix_rgb(
            color,
            shader.fog_color,
            (shader.fog_density * 0.08).clamp(0.0, 0.55),
        );
    }
    if shader.layers.contains("RAIN") {
        color = mix_rgb(
            color,
            shader.rain_color,
            (shader.rain_intensity * shader.rain_overlay_opacity).clamp(0.0, 0.35),
        );
    }
    if shader.layers.contains("SNOW") {
        color = mix_rgb(
            color,
            shader.snow_color,
            (shader.snow_intensity * 0.18).clamp(0.0, 0.4),
        );
    }
    color
}

fn mix_rgb(base: [f32; 4], layer: [f32; 4], amount: f32) -> [f32; 4] {
    let amount = amount.clamp(0.0, 1.0);
    [
        base[0] * (1.0 - amount) + layer[0] * amount,
        base[1] * (1.0 - amount) + layer[1] * amount,
        base[2] * (1.0 - amount) + layer[2] * amount,
        1.0,
    ]
}

fn color_from_rgba(color: [f32; 4]) -> Color {
    Color::srgba(
        color[0].clamp(0.0, 1.0),
        color[1].clamp(0.0, 1.0),
        color[2].clamp(0.0, 1.0),
        color[3].clamp(0.0, 1.0),
    )
}

fn string_set(value: Option<&serde_json::Value>) -> HashSet<String> {
    value
        .and_then(serde_json::Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(serde_json::Value::as_str)
        .map(|value| value.to_ascii_uppercase())
        .collect()
}

fn bool_field(value: &serde_json::Value, key: &str, fallback: bool) -> bool {
    value
        .get(key)
        .and_then(serde_json::Value::as_bool)
        .unwrap_or(fallback)
}

fn f32_field(value: &serde_json::Value, key: &str, fallback: f32) -> f32 {
    value
        .get(key)
        .and_then(serde_json::Value::as_f64)
        .map(|value| value as f32)
        .unwrap_or(fallback)
}

fn nested_f32(
    value: &serde_json::Value,
    object_key: &str,
    nested_key: &str,
    flat_key: &str,
    fallback: f32,
) -> f32 {
    value
        .get(object_key)
        .and_then(|object| object.get(nested_key))
        .and_then(serde_json::Value::as_f64)
        .or_else(|| value.get(flat_key).and_then(serde_json::Value::as_f64))
        .map(|value| value as f32)
        .unwrap_or(fallback)
}

fn nested_rgba(
    value: &serde_json::Value,
    object_key: &str,
    nested_key: &str,
    flat_key: &str,
    fallback: [f32; 4],
) -> [f32; 4] {
    value
        .get(object_key)
        .and_then(|object| object.get(nested_key))
        .or_else(|| value.get(flat_key))
        .map(|value| rgba_array(Some(value), fallback))
        .unwrap_or(fallback)
}

fn rgba_array(value: Option<&serde_json::Value>, fallback: [f32; 4]) -> [f32; 4] {
    let Some(values) = value.and_then(serde_json::Value::as_array) else {
        return fallback;
    };
    let mut rgba = fallback;
    for (index, value) in values.iter().take(4).enumerate() {
        if let Some(value) = value.as_f64() {
            rgba[index] = value as f32;
        }
    }
    rgba
}

fn parse_float_array(value: &str, fallback: [f32; 4]) -> [f32; 4] {
    let values = value
        .split_whitespace()
        .filter_map(|part| part.parse::<f32>().ok())
        .collect::<Vec<_>>();
    if values.len() < 4 {
        return fallback;
    }
    [values[0], values[1], values[2], values[3]]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_exported_j3m_shader_parameters() {
        let shader = parse_j3m_object_shader(
            "Material flash : shaders/flash/flash.j3md {\n    MainColor : 1.0 0.0 0.0 1.0\n    GlowStrength : 2.4\n    Transparency : 0.2\n    UseOriginalTexture : false\n}",
            "flash",
        );

        assert_eq!(shader.main_color, [1.0, 0.0, 0.0, 1.0]);
        assert_eq!(shader.glow_strength, 2.4);
        assert_eq!(shader.transparency, 0.2);
        assert!(!shader.use_original_texture);
    }

    #[test]
    fn parses_environment_json_from_exported_metadata() {
        let value = serde_json::json!({
            "name": "mist",
            "layers": ["FOG", "AMBIENT_COLOR"],
            "fog": { "density": 1.2, "color": [0.4, 0.5, 0.6, 1.0] },
            "ambientColor": { "intensity": 0.7, "color": [0.2, 0.3, 0.4, 1.0] }
        });

        let shader = parse_environment_shader_json(&value, "fallback");

        assert_eq!(shader.name, "mist");
        assert!(shader.layers.contains("FOG"));
        assert_eq!(shader.fog_density, 1.2);
        assert_eq!(shader.ambient_intensity, 0.7);
    }
}
