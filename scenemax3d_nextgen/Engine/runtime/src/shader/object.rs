//! Authored block shaders use the mesh vertex pipeline (including skinning).
use super::*;
use bevy::{reflect::TypePath, render::render_resource::AsBindGroup, shader::ShaderRef};

pub(crate) struct ObjectShaderPlugin;
impl Plugin for ObjectShaderPlugin {
    fn build(&self, app: &mut App) {
        bevy::asset::embedded_asset!(app, "object.wesl");
        app.add_plugins(MaterialPlugin::<BlockMaterial>::default())
            .add_systems(
                PostUpdate,
                update.after(bevy::transform::TransformSystems::Propagate),
            );
    }
}
#[derive(Asset, TypePath, AsBindGroup, Clone, Debug)]
pub(super) struct BlockMaterial {
    #[uniform(0)]
    main: Vec4,
    #[uniform(1)]
    parameters: Vec4,
    #[uniform(2)]
    motion: Vec4,
    #[texture(3)]
    #[sampler(4)]
    texture: Option<Handle<Image>>,
    #[uniform(5)]
    local_from_world: Mat4,
    alpha: AlphaMode,
}
impl Material for BlockMaterial {
    fn fragment_shader() -> ShaderRef {
        "embedded://scenemax_runtime/shader/object.wesl".into()
    }
    fn alpha_mode(&self) -> AlphaMode {
        self.alpha
    }
    fn enable_prepass() -> bool {
        false
    }
    fn enable_shadows() -> bool {
        false
    }
}
#[derive(Component, Clone)]
pub(super) struct BlockSurface {
    pub standard: Handle<StandardMaterial>,
    material: Handle<BlockMaterial>,
}
pub(super) fn install(
    world: &mut World,
    entity: Entity,
    shader: &SceneMaxObjectShader,
    standard: Handle<StandardMaterial>,
    source: Option<&StandardMaterial>,
) {
    if ![
        "GLOW",
        "PULSE",
        "DISSOLVE",
        "RIM_LIGHT",
        "SCROLL_UV",
        "FLICKER",
        "WATER_WAVES",
        "HOLOGRAM_LINES",
        "TOON_RAMP",
    ]
    .iter()
    .any(|b| shader.blocks.contains(*b))
    {
        remove(world, entity);
        return;
    }
    // Asset storage is initialized in headless tests too; rendering is optional.
    if !world.contains_resource::<Assets<BlockMaterial>>() {
        world.init_resource::<Assets<BlockMaterial>>();
    }
    let names = [
        "TINT",
        "GLOW",
        "PULSE",
        "DISSOLVE",
        "RIM_LIGHT",
        "SCROLL_UV",
        "FLICKER",
        "WATER_WAVES",
        "HOLOGRAM_LINES",
        "TOON_RAMP",
        "TRANSPARENCY",
    ];
    let flags = names.iter().enumerate().fold(0u32, |bits, (i, n)| {
        bits | if shader.blocks.contains(*n) {
            1 << i
        } else {
            0
        }
    });
    let material = world
        .resource_mut::<Assets<BlockMaterial>>()
        .add(BlockMaterial {
            main: Color::srgba(
                shader.main_color[0],
                shader.main_color[1],
                shader.main_color[2],
                shader.main_color[3],
            )
            .to_linear()
            .to_vec4(),
            parameters: Vec4::new(
                shader.glow_strength.max(0.),
                shader.pulse_speed.max(0.05),
                shader.transparency.clamp(0., 1.),
                shader.edge_width.max(0.01),
            ),
            motion: Vec4::new(shader.scroll_speed, 0., flags as f32, 0.),
            texture: if shader.use_original_texture {
                source.and_then(|m| m.base_color_texture.clone())
            } else {
                None
            },
            local_from_world: Mat4::IDENTITY,
            alpha: alpha_mode_from_key(
                &shader.alpha_mode,
                shader.main_color[3] * (1. - shader.transparency),
                shader.alpha_cutoff,
                &shader.blocks,
            ),
        });
    world
        .entity_mut(entity)
        .remove::<MeshMaterial3d<BlockMaterial>>()
        .insert(BlockSurface { standard, material });
}
pub(super) fn current(world: &World, entity: Entity) -> Option<Handle<StandardMaterial>> {
    world
        .get::<MeshMaterial3d<StandardMaterial>>(entity)
        .map(|m| m.0.clone())
        .or_else(|| {
            world
                .get::<BlockSurface>(entity)
                .map(|s| s.standard.clone())
        })
}
pub(super) fn remove(world: &mut World, entity: Entity) {
    world
        .entity_mut(entity)
        .remove::<(BlockSurface, MeshMaterial3d<BlockMaterial>)>();
}
fn update(world: &mut World) {
    let time = std::env::var("SCENEMAX_OBJECT_SHADER_AUDIT_TIME")
        .ok()
        .and_then(|s| s.parse::<f32>().ok())
        .filter(|t| t.is_finite())
        .unwrap_or_else(|| world.resource::<Time>().elapsed_secs());
    let entries: Vec<_> = world
        .query::<(Entity, &BlockSurface)>()
        .iter(world)
        .map(|(e, s)| (e, s.clone()))
        .collect();
    for (entity, surface) in entries {
        let mut ancestor = Some(entity);
        let mut binding = None;
        while let Some(node) = ancestor {
            if let Some(b) = world.get::<scenemax_materials::MaterialBinding>(node) {
                binding = Some(b.clone());
                break;
            }
            ancestor = world.get::<ChildOf>(node).map(|p| p.parent());
        }
        let binding = binding.filter(|b| {
            b.slot.is_empty()
                || world
                    .get::<bevy::gltf::GltfMaterialName>(entity)
                    .is_some_and(|n| n.0 == b.slot)
        });
        if let Some(binding) = binding {
            world
                .entity_mut(entity)
                .remove::<MeshMaterial3d<BlockMaterial>>()
                .insert((
                    MeshMaterial3d(binding.material),
                    scenemax_materials::OriginalMaterial(surface.standard),
                ));
        } else {
            let matrix = world
                .get::<GlobalTransform>(entity)
                .map(|t| t.to_matrix().inverse())
                .unwrap_or(Mat4::IDENTITY);
            if let Some(mut material) = world
                .resource_mut::<Assets<BlockMaterial>>()
                .get_mut(&surface.material)
            {
                material.motion.y = time;
                material.local_from_world = matrix;
            }
            world
                .entity_mut(entity)
                .remove::<(
                    MeshMaterial3d<StandardMaterial>,
                    scenemax_materials::OriginalMaterial,
                )>()
                .insert(MeshMaterial3d(surface.material));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn block_materials_keep_authored_parameters_and_restore_native_overrides() {
        let mut world = World::new();
        world.init_resource::<Assets<StandardMaterial>>();
        world.init_resource::<Time>();
        let original = world
            .resource_mut::<Assets<StandardMaterial>>()
            .add(StandardMaterial::default());
        let root = world.spawn_empty().id();
        let mesh = world
            .spawn((
                MeshMaterial3d(original.clone()),
                GlobalTransform::default(),
                ChildOf(root),
            ))
            .id();
        let shader=ShaderDocumentSource::Json(serde_json::json!({"blocks":["TINT","PULSE","RIM_LIGHT","DISSOLVE"],"pulseSpeed":2.5,"edgeWidth":0.12,"transparency":0.4})).into_object_shader("test").unwrap();
        apply_object_shader_to_entities(&mut world, &[mesh], &shader);
        update(&mut world);
        assert!(
            world
                .get::<MeshMaterial3d<StandardMaterial>>(mesh)
                .is_none()
        );
        let block = world
            .get::<MeshMaterial3d<BlockMaterial>>(mesh)
            .unwrap()
            .0
            .clone();
        let material = world
            .resource::<Assets<BlockMaterial>>()
            .get(&block)
            .unwrap();
        assert_eq!(material.motion.z as u32, 1 | 4 | 8 | 16);
        assert_eq!(material.parameters.y, 2.5);
        let paint = world
            .resource_mut::<Assets<StandardMaterial>>()
            .add(StandardMaterial::default());
        world
            .entity_mut(root)
            .insert(scenemax_materials::MaterialBinding {
                material: paint.clone(),
                slot: String::new(),
            });
        update(&mut world);
        assert_eq!(
            world
                .get::<MeshMaterial3d<StandardMaterial>>(mesh)
                .unwrap()
                .0,
            paint
        );
        assert!(world.get::<MeshMaterial3d<BlockMaterial>>(mesh).is_none());
        clear_native_material_world(&mut world, root);
        update(&mut world);
        assert_eq!(
            world.get::<MeshMaterial3d<BlockMaterial>>(mesh).unwrap().0,
            block
        );
        restore_original_materials(&mut world, &[mesh]);
        assert_eq!(
            world
                .get::<MeshMaterial3d<StandardMaterial>>(mesh)
                .unwrap()
                .0,
            original
        );
        assert!(world.get::<BlockSurface>(mesh).is_none());
    }
}
