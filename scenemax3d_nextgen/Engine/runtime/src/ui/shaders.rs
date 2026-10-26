//! Native UI material path for flat widget surfaces.
use super::*;
use bevy::{reflect::TypePath, render::render_resource::AsBindGroup, shader::ShaderRef};

pub(crate) struct SurfaceShaderPlugin;
impl Plugin for SurfaceShaderPlugin {
    fn build(&self, app: &mut App) {
        bevy::asset::embedded_asset!(app, "panel_shader.wesl");
        app.add_plugins(UiMaterialPlugin::<SurfaceShader>::default())
            .add_systems(Update, animate)
            .add_systems(
                PostUpdate,
                text_shaders::refresh.after(bevy::ui::UiSystems::Layout),
            );
    }
}

#[derive(AsBindGroup, Asset, TypePath, Debug, Clone)]
pub(crate) struct SurfaceShader {
    #[uniform(0)]
    color: Vec4,
    #[texture(1)]
    #[sampler(2)]
    texture: Option<Handle<Image>>,
    #[uniform(3)]
    parameters: Vec4,
    #[uniform(4)]
    motion: Vec4,
    #[uniform(5)]
    uv_rect: Vec4,
    #[uniform(6)]
    main: Vec4,
}
impl SurfaceShader {
    pub(super) fn textured(color: Color, texture: Option<Handle<Image>>) -> Self {
        Self {
            color: color.to_linear().to_vec4(),
            texture,
            parameters: Vec4::new(0., 1., 0., 0.01),
            motion: Vec4::ZERO,
            uv_rect: Vec4::new(0., 0., 1., 1.),
            main: color.to_linear().to_vec4(),
        }
    }
}
impl SurfaceShader {
    pub(super) fn configured(
        name: &str,
        context: &SceneMaxLaunchContext,
        texture: Option<Handle<Image>>,
    ) -> Option<Self> {
        let config = crate::shader::resolve_ui_effects(name, context)?;
        let mut material = Self::textured(config.color, texture);
        material.parameters = config.parameters;
        material.motion = config.motion;
        material.main = config.main.to_linear().to_vec4();
        Some(material)
    }
    pub(super) fn glyph(mut self, rect: Vec4, tint: Color, alpha_mask: bool) -> Self {
        self.uv_rect = rect;
        self.color *= tint.to_linear().to_vec4();
        self.motion.w = if alpha_mask { 1. } else { 0. };
        self
    }
}
fn animate(time: Res<Time>, mut materials: ResMut<Assets<SurfaceShader>>) {
    let now = std::env::var("SCENEMAX_UI_SHADER_AUDIT_TIME")
        .ok()
        .and_then(|s| s.parse::<f32>().ok())
        .filter(|v| v.is_finite())
        .unwrap_or_else(|| time.elapsed_secs());
    for (_, material) in materials.iter_mut() {
        material.motion.y = now;
    }
}
impl UiMaterial for SurfaceShader {
    fn fragment_shader() -> ShaderRef {
        "embedded://scenemax_runtime/ui/panel_shader.wesl".into()
    }
}

#[derive(Component, Clone)]
struct OriginalBackground(Option<BackgroundColor>);

#[derive(Component)]
struct LastShader(String);

#[derive(Debug, Clone)]
pub(crate) struct Snapshot {
    pub name: String,
    pub captured: bool,
    pub original: Option<[f32; 4]>,
    pub current: Option<[f32; 4]>,
}
impl Snapshot {
    pub(crate) fn valid(&self) -> bool {
        !self.name.contains('\0')
            && (self.name.trim().is_empty() || self.captured)
            && self
                .original
                .iter()
                .chain(self.current.iter())
                .flatten()
                .all(|v| v.is_finite() && (0.0..=1.0).contains(v))
    }
}

pub(crate) fn snapshot(world: &World, entity: Entity) -> Option<Snapshot> {
    if text_shaders::is_text(world, entity) {
        return text_shaders::snapshot(world, entity);
    }
    let name = world.get::<LastShader>(entity)?.0.clone();
    let original = world.get::<OriginalBackground>(entity);
    Some(Snapshot {
        name,
        captured: original.is_some(),
        original: original
            .and_then(|v| v.0.as_ref())
            .map(|v| v.0.to_srgba().to_f32_array()),
        current: world
            .get::<BackgroundColor>(entity)
            .map(|v| v.0.to_srgba().to_f32_array()),
    })
}

pub(crate) fn restore(world: &mut World, entity: Entity, state: Snapshot) {
    if text_shaders::is_text(world, entity) {
        text_shaders::restore(world, entity, state);
        return;
    }
    if !state.valid() || world.get_entity(entity).is_err() {
        return;
    }
    if !state.name.trim().is_empty()
        && (!world.contains_resource::<Assets<SurfaceShader>>()
            || crate::shader::resolve_ui_surface_color(
                &state.name,
                world.resource::<SceneMaxLaunchContext>(),
            )
            .is_none())
    {
        return;
    }
    let color = |rgba: [f32; 4]| BackgroundColor(Color::srgba(rgba[0], rgba[1], rgba[2], rgba[3]));
    if state.captured {
        world
            .entity_mut(entity)
            .insert(OriginalBackground(state.original.map(color)));
    } else {
        world.entity_mut(entity).remove::<OriginalBackground>();
    }
    apply(world, entity, &state.name);
    if state.name.trim().is_empty() {
        world.entity_mut(entity).remove::<BackgroundColor>();
        if let Some(current) = state.current {
            world.entity_mut(entity).insert(color(current));
        }
    }
}

pub(crate) fn active(world: &World, entity: Entity) -> bool {
    world.get::<MaterialNode<SurfaceShader>>(entity).is_some()
}

pub(crate) fn apply(world: &mut World, entity: Entity, name: &str) -> bool {
    if text_shaders::is_text(world, entity) {
        return text_shaders::apply(world, entity, name);
    }
    if world.get_entity(entity).is_err() {
        return false;
    }
    if name.trim().is_empty() {
        if let Some(original) = world.get::<OriginalBackground>(entity).cloned() {
            let mut target = world.entity_mut(entity);
            target
                .remove::<MaterialNode<SurfaceShader>>()
                .remove::<BackgroundColor>();
            if let Some(color) = original.0 {
                target.insert(color);
            }
        }
        write_runtime_diagnostic_line(format!("UI:SHADER reset target={entity:?}"));
        world.entity_mut(entity).insert(LastShader(String::new()));
        return true;
    }
    let Some((_color, _)) =
        crate::shader::resolve_ui_surface_color(name, world.resource::<SceneMaxLaunchContext>())
    else {
        write_runtime_diagnostic_line(format!(
            "UI:SHADER unavailable name={name} target={entity:?}"
        ));
        return false;
    };
    if !world.contains_resource::<Assets<SurfaceShader>>() {
        return false;
    }
    if world.get::<OriginalBackground>(entity).is_none() {
        let original = world.get::<BackgroundColor>(entity).cloned();
        world
            .entity_mut(entity)
            .insert(OriginalBackground(original));
    }
    let Some(definition) =
        SurfaceShader::configured(name, world.resource::<SceneMaxLaunchContext>(), None)
    else {
        return false;
    };
    let material = world
        .resource_mut::<Assets<SurfaceShader>>()
        .add(definition);
    world
        .entity_mut(entity)
        .remove::<BackgroundColor>()
        .insert((MaterialNode(material), LastShader(name.to_owned())));
    write_runtime_diagnostic_line(format!("UI:SHADER applied name={name} target={entity:?}"));
    true
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn shader_blocks_keep_authored_parameters_and_invalid_names_are_rejected() {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir(dir.path().join("scripts")).unwrap();
        let context = SceneMaxLaunchContext {
            script_root: None,
            asset_root: Some(dir.path().join("resources")),
            builtin_asset_root: None,
            window_width: 640,
            window_height: 360,
        };
        for (block, bit) in [
            ("GLOW", 2),
            ("PULSE", 4),
            ("DISSOLVE", 8),
            ("SCROLL_UV", 32),
            ("FLICKER", 64),
            ("WATER_WAVES", 128),
            ("HOLOGRAM_LINES", 256),
            ("TOON_RAMP", 512),
        ] {
            fs::write(dir.path().join("scripts/sample.bvshader"),serde_json::json!({"blocks":[block],"glowStrength":0.3,"pulseSpeed":2.5,"edgeWidth":0.04,"scrollSpeed":-1.5,"transparency":0.6}).to_string()).unwrap();
            let material = SurfaceShader::configured("sample", &context, None).unwrap();
            assert_eq!(material.motion.z, bit as f32);
            assert_eq!(material.motion.x, -1.5);
            assert_eq!(material.parameters, Vec4::new(0.3, 2.5, 0.6, 0.04));
            if block == "DISSOLVE" {
                assert_eq!(material.color.w, 1.);
            } else {
                assert!((material.color.w - 0.4).abs() < 0.0001);
            }
        }
    }
    #[test]
    fn surface_shader_preserves_first_background_and_rejects_unsupported_definitions() {
        let root = tempfile::tempdir().unwrap();
        fs::create_dir(root.path().join("scripts")).unwrap();
        fs::write(
            root.path().join("scripts/tint.bvshader"),
            r#"{"mainColor":[0,1,0,0.8],"transparency":0.5,"blocks":["TINT","TRANSPARENCY"]}"#,
        )
        .unwrap();
        fs::write(
            root.path().join("scripts/unsupported.bvshader"),
            r#"{"blocks":["UNSUPPORTED"]}"#,
        )
        .unwrap();
        let mut world = World::new();
        world.init_resource::<Assets<SurfaceShader>>();
        world.insert_resource(SceneMaxLaunchContext {
            script_root: None,
            asset_root: Some(root.path().join("resources")),
            builtin_asset_root: None,
            window_width: 640,
            window_height: 360,
        });
        let original = Color::srgb(1., 0., 0.);
        let entity = world.spawn(BackgroundColor(original)).id();
        apply(&mut world, entity, "tint");
        let material = world
            .get::<MaterialNode<SurfaceShader>>(entity)
            .unwrap()
            .0
            .clone();
        assert_eq!(
            world
                .resource::<Assets<SurfaceShader>>()
                .get(&material)
                .unwrap()
                .color,
            Vec4::new(0., 1., 0., 0.4)
        );
        assert_eq!(world.get::<BackgroundColor>(entity).unwrap().0, Color::NONE);
        for name in ["missing", "unsupported"] {
            apply(&mut world, entity, name);
            assert_eq!(
                world.get::<MaterialNode<SurfaceShader>>(entity).unwrap().0,
                material
            );
        }
        apply(&mut world, entity, "");
        assert_eq!(world.get::<BackgroundColor>(entity).unwrap().0, original);
        world
            .entity_mut(entity)
            .insert(BackgroundColor(Color::WHITE));
        apply(&mut world, entity, "tint");
        apply(&mut world, entity, "");
        assert_eq!(world.get::<BackgroundColor>(entity).unwrap().0, original);
        assert!(!active(&world, entity));
    }
}
