use super::*;

const SCENEMAX_LUMENS_DIVISOR: f32 = 800.0;
const SCENEMAX_AMBIENT_BRIGHTNESS: f32 = 220.0;
const SCENEMAX_DIRECTIONAL_ILLUMINANCE: f32 = 24_000.0;
const SCENEMAX_POINT_INTENSITY: f32 = 45_000.0;
const SCENEMAX_SPOT_INTENSITY: f32 = 45_000.0;

#[derive(Debug, Clone, Copy, Component)]
pub(super) enum SceneMaxFallbackLight {
    Directional { illuminance: f32 },
    Point { intensity: f32 },
}

#[derive(Debug, Clone, Component)]
pub(super) struct SceneMaxLight;

#[derive(Debug, Clone, Copy, Resource)]
pub(super) struct SceneMaxAuthoredAmbientLight;

pub(super) fn collect_light_declarations(program: &Program) -> Vec<LightDeclarationStatement> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::LightDecl(light) => Some(light.clone()),
            _ => None,
        })
        .collect()
}

pub(super) fn set_fallback_lighting_enabled(commands: &mut Commands, enabled: bool) {
    commands.queue(move |world: &mut World| {
        let mut fallback_lights = world.query::<(
            Option<&mut DirectionalLight>,
            Option<&mut PointLight>,
            &SceneMaxFallbackLight,
        )>();
        for (directional, point, fallback) in fallback_lights.iter_mut(world) {
            match (directional, point, fallback) {
                (Some(mut light), _, SceneMaxFallbackLight::Directional { illuminance }) => {
                    light.illuminance = if enabled { *illuminance } else { 0.0 };
                }
                (_, Some(mut light), SceneMaxFallbackLight::Point { intensity }) => {
                    light.intensity = if enabled { *intensity } else { 0.0 };
                }
                _ => {}
            }
        }
    });
}

pub(super) fn apply_default_ambient_light(commands: &mut Commands) {
    commands.insert_resource(GlobalAmbientLight {
        color: Color::WHITE,
        brightness: SCENEMAX_AMBIENT_BRIGHTNESS,
        ..default()
    });
}

pub(super) fn spawn_scenemax_light_decl(
    commands: &mut Commands,
    light: &LightDeclarationStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> (Entity, Transform) {
    set_fallback_lighting_enabled(commands, false);
    let transform = light_transform(
        light,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    );
    let intensity = resolve_light_intensity(light, vars, scope, guards_by_name, transforms_by_name);
    let base_color = resolve_light_color(light.color.as_deref(), Color::WHITE);

    let mut entity = commands.spawn((
        SceneMaxEntity {
            name: light.name.clone(),
            runtime_name: format!("{}@light", light.name),
        },
        SceneMaxLight,
        transform,
    ));
    let mut ambient_light = None;

    match light.light_type {
        LightType::Directional => {
            entity.insert(DirectionalLight {
                color: base_color,
                illuminance: SCENEMAX_DIRECTIONAL_ILLUMINANCE * intensity,
                shadow_maps_enabled: is_light_shadow_requested(light),
                shadow_depth_bias: 0.08,
                shadow_normal_bias: 1.8,
                ..default()
            });
        }
        LightType::Point => {
            entity.insert(PointLight {
                color: base_color,
                intensity: SCENEMAX_POINT_INTENSITY * intensity,
                range: resolve_light_value(
                    light.range.as_ref(),
                    vars,
                    scope,
                    guards_by_name,
                    transforms_by_name,
                    collider_bounds,
                    10.0,
                ),
                shadow_maps_enabled: is_light_shadow_requested(light),
                ..default()
            });
        }
        LightType::Spot => {
            let outer_angle = resolve_light_value(
                light.angle.as_ref(),
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
                35.0,
            )
            .to_radians();
            entity.insert(SpotLight {
                color: base_color,
                intensity: SCENEMAX_SPOT_INTENSITY * intensity,
                range: resolve_light_value(
                    light.range.as_ref(),
                    vars,
                    scope,
                    guards_by_name,
                    transforms_by_name,
                    collider_bounds,
                    20.0,
                ),
                inner_angle: outer_angle * 0.7,
                outer_angle,
                shadow_maps_enabled: is_light_shadow_requested(light),
                ..default()
            });
        }
        LightType::Ambient | LightType::Sky | LightType::Probe => {
            let ambient_color = resolve_ambient_light_color(light, base_color);
            ambient_light = Some(GlobalAmbientLight {
                color: ambient_color,
                brightness: SCENEMAX_AMBIENT_BRIGHTNESS * intensity,
                affects_lightmapped_meshes: light.affects_lightmapped_meshes.unwrap_or_default(),
                ..default()
            });
        }
    }

    let entity_id = entity.id();
    drop(entity);
    if let Some(ambient_light) = ambient_light {
        match light.light_type {
            LightType::Ambient => {
                commands.insert_resource(ambient_light);
                commands.insert_resource(SceneMaxAuthoredAmbientLight);
            }
            LightType::Sky | LightType::Probe => {
                let light_type = light.light_type;
                commands.queue(move |world: &mut World| {
                    if world.contains_resource::<SceneMaxAuthoredAmbientLight>() {
                        write_runtime_diagnostic_line(format!(
                            "LIGHT:AMBIENT_SKIP type={light_type:?} reason=authored_ambient"
                        ));
                    } else {
                        world.insert_resource(ambient_light);
                    }
                });
            }
            _ => {}
        }
    }
    write_runtime_diagnostic_line(format!(
        "LIGHT:DECL target={} type={:?} intensity={} color={}",
        light.name,
        light.light_type,
        format_scenemax_number(intensity),
        light.color.as_deref().unwrap_or("<default>")
    ));
    (entity_id, transform)
}

pub(super) fn apply_light_probe_add(
    commands: &mut Commands,
    probe: &LightProbeAddStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> (Entity, Transform) {
    let light = LightDeclarationStatement {
        name: probe.name.clone(),
        light_type: LightType::Probe,
        color: None,
        intensity: Some(AssignmentValue::Number(1.0)),
        intensity_unit: None,
        position: probe.position.clone(),
        direction: None,
        shadow_mode: None,
        range: None,
        look_at: None,
        angle: None,
        preset: Some(probe.name.clone()),
        exposure: None,
        ambient_color: None,
        affects_lightmapped_meshes: None,
    };
    spawn_scenemax_light_decl(
        commands,
        &light,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
}

fn light_transform(
    light: &LightDeclarationStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Transform {
    let position = light
        .position
        .as_ref()
        .and_then(|position| {
            resolve_position_value_runtime(
                position,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
        })
        .unwrap_or(Vec3::ZERO);
    let direction = light_direction(
        light,
        position,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    );
    Transform::from_translation(position).looking_to(direction, Vec3::Y)
}

fn light_direction(
    light: &LightDeclarationStatement,
    position: Vec3,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Vec3 {
    if let Some(target) = light.look_at.as_ref()
        && let Some(transform) = transforms_by_name.and_then(|transforms| transforms.get(target))
    {
        let direction = transform.translation - position;
        if direction.length_squared() >= 0.0001 {
            return direction.normalize();
        }
    }
    if let Some(values) = light.direction.as_ref()
        && values.len() == 3
    {
        let direction = Vec3::new(
            resolve_position_expr_runtime(
                &values[0],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .unwrap_or(0.0),
            resolve_position_expr_runtime(
                &values[1],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .unwrap_or(-1.0),
            resolve_position_expr_runtime(
                &values[2],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .unwrap_or(0.0),
        );
        if direction.length_squared() >= 0.0001 {
            return direction.normalize();
        }
    }
    match light.light_type {
        LightType::Directional => Vec3::new(-0.1, -0.7, -1.0).normalize(),
        _ => Vec3::NEG_Y,
    }
}

fn resolve_light_intensity(
    light: &LightDeclarationStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
) -> f32 {
    let intensity = resolve_light_value(
        light.intensity.as_ref(),
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        None,
        1.0,
    );
    if light
        .intensity_unit
        .as_deref()
        .is_some_and(|unit| unit.starts_with("lumen"))
    {
        intensity / SCENEMAX_LUMENS_DIVISOR
    } else {
        intensity
    }
}

fn resolve_light_value(
    value: Option<&AssignmentValue>,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    fallback: f32,
) -> f32 {
    value
        .and_then(|value| {
            resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
        })
        .unwrap_or(fallback)
}

fn resolve_ambient_light_color(light: &LightDeclarationStatement, fallback: Color) -> Color {
    if let Some(color) = light.ambient_color.as_deref() {
        return resolve_light_color(Some(color), fallback);
    }
    if let Some(preset) = light.preset.as_deref() {
        return sky_preset_color(preset, fallback);
    }
    fallback
}

fn resolve_light_color(value: Option<&str>, fallback: Color) -> Color {
    let Some(value) = value.map(str::trim).filter(|value| !value.is_empty()) else {
        return fallback;
    };
    if let Some(color) = parse_hex_color(value) {
        return color;
    }
    match value.to_ascii_lowercase().as_str() {
        "warm" => Color::srgb(1.0, 0.82, 0.58),
        "cool" => Color::srgb(0.62, 0.78, 1.0),
        "red" => Color::srgb(1.0, 0.0, 0.0),
        "green" => Color::srgb(0.0, 1.0, 0.0),
        "blue" => Color::srgb(0.0, 0.0, 1.0),
        "white" => Color::WHITE,
        "black" => Color::BLACK,
        "yellow" => Color::srgb(1.0, 1.0, 0.0),
        "orange" => Color::srgb(1.0, 0.5, 0.0),
        "pink" => Color::srgb(1.0, 0.68, 0.68),
        "cyan" => Color::srgb(0.0, 1.0, 1.0),
        "magenta" => Color::srgb(1.0, 0.0, 1.0),
        "gray" | "grey" => Color::srgb(0.5, 0.5, 0.5),
        _ => fallback,
    }
}

fn parse_hex_color(value: &str) -> Option<Color> {
    let hex = value.trim().strip_prefix('#')?;
    if hex.len() != 6 {
        return None;
    }
    let rgb = u32::from_str_radix(hex, 16).ok()?;
    Some(Color::srgb_u8(
        ((rgb >> 16) & 0xff) as u8,
        ((rgb >> 8) & 0xff) as u8,
        (rgb & 0xff) as u8,
    ))
}

fn sky_preset_color(preset: &str, fallback: Color) -> Color {
    match preset.to_ascii_lowercase().as_str() {
        "night neon" => Color::srgb(0.12, 0.18, 0.32),
        "sunny day" => Color::srgb(0.78, 0.86, 1.0),
        "overcast" => Color::srgb(0.55, 0.58, 0.62),
        _ => fallback,
    }
}

fn is_light_shadow_requested(light: &LightDeclarationStatement) -> bool {
    light
        .shadow_mode
        .as_deref()
        .is_some_and(|mode| !mode.eq_ignore_ascii_case("off") && !mode.eq_ignore_ascii_case("none"))
}
