//! Shared PBR rendering contract for material previews and game objects.
#![forbid(unsafe_code)]
use bevy::{math::Affine2, prelude::*, render::render_resource::Face};
use serde_json::Value;
/// Build exactly the same PBR surface in editor and runtime. The caller owns
/// scoped texture loading; color data is sRGB, normal/packed channels are linear.
pub fn standard(
    v: &Value,
    mut texture: impl FnMut(&str, bool) -> Handle<Image>,
) -> Result<StandardMaterial, String> {
    scenemax_assets::material::validate(v)?;
    let number = |k: &str| v[k].as_f64().unwrap_or_default() as f32;
    let rgba = scenemax_assets::material::color(v["baseColor"].as_str().unwrap_or_default())?;
    let glow = scenemax_assets::material::color(v["emissionColor"].as_str().unwrap_or_default())?;
    let color = Color::srgba(rgba[0], rgba[1], rgba[2], rgba[3]);
    let emissive = Color::srgb(glow[0], glow[1], glow[2]).to_linear() * number("emissionStrength");
    let mut m = StandardMaterial {
        base_color: color,
        metallic: number("metallic"),
        perceptual_roughness: number("roughness"),
        reflectance: number("reflectance"),
        emissive,
        clearcoat: number("clearcoat"),
        clearcoat_perceptual_roughness: number("clearcoatRoughness"),
        anisotropy_strength: number("anisotropy"),
        anisotropy_rotation: number("anisotropyRotation").to_radians(),
        specular_transmission: number("transmission"),
        thickness: number("thickness"),
        ior: number("ior"),
        double_sided: v["doubleSided"].as_bool().unwrap_or(false),
        cull_mode: if v["doubleSided"] == true {
            None
        } else {
            Some(Face::Back)
        },
        unlit: v["unlit"].as_bool().unwrap_or(false),
        flip_normal_map_y: v["flipNormalY"].as_bool().unwrap_or(false),
        alpha_mode: match v["alphaMode"].as_str() {
            Some("Mask") => AlphaMode::Mask(number("alphaCutoff")),
            Some("Blend") => AlphaMode::Blend,
            Some("Add") => AlphaMode::Add,
            _ => AlphaMode::Opaque,
        },
        uv_transform: Affine2::from_scale_angle_translation(
            Vec2::new(number("uvScaleX"), number("uvScaleY")),
            number("uvRotation").to_radians(),
            Vec2::new(number("uvOffsetX"), number("uvOffsetY")),
        ),
        ..default()
    };
    for (_, key, srgb) in scenemax_assets::material::TEXTURES {
        let Some(path) = v["textures"][key].as_str().filter(|p| !p.is_empty()) else {
            continue;
        };
        let image = Some(texture(path, *srgb));
        match *key {
            "baseColor" => m.base_color_texture = image,
            "normal" => m.normal_map_texture = image,
            "metallicRoughness" => m.metallic_roughness_texture = image,
            "occlusion" => m.occlusion_texture = image,
            "emission" => m.emissive_texture = image,
            _ => {}
        }
    }
    Ok(m)
}
/// A per-instance material override. Empty slot selects every mesh in the subtree.
#[derive(Component, Clone)]
pub struct MaterialBinding {
    /// Surface instance shared by this object subtree.
    pub material: Handle<StandardMaterial>,
    /// Exact glTF material name; empty means every slot.
    pub slot: String,
}
/// Original imported handle retained so slot changes and removal are reversible.
#[derive(Component)]
pub struct OriginalMaterial(pub Handle<StandardMaterial>);
type MaterialMeshes<'w, 's> = Query<
    'w,
    's,
    (
        Entity,
        &'static mut MeshMaterial3d<StandardMaterial>,
        Option<&'static bevy::gltf::GltfMaterialName>,
        Option<&'static OriginalMaterial>,
    ),
>;
/// Keep asynchronous glTF descendants and material overrides synchronized.
pub fn apply_bindings(
    mut commands: Commands,
    bindings: Query<(Entity, &MaterialBinding)>,
    parents: Query<&ChildOf>,
    mut meshes: MaterialMeshes,
) {
    for (entity, mut current, name, original) in &mut meshes {
        let binding = bindings.get(entity).ok().or_else(|| {
            parents
                .iter_ancestors(entity)
                .find_map(|p| bindings.get(p).ok())
        });
        if let Some((_, binding)) =
            binding.filter(|(_, b)| b.slot.is_empty() || name.is_some_and(|n| n.0 == b.slot))
        {
            if current.0 != binding.material {
                if original.is_none() {
                    commands
                        .entity(entity)
                        .insert(OriginalMaterial(current.0.clone()));
                }
                current.0 = binding.material.clone();
            }
        } else if let Some(original) = original {
            current.0 = original.0.clone();
            commands.entity(entity).remove::<OriginalMaterial>();
        }
    }
}
/// Installs instance-local material assignment without depending on either app.
pub struct MaterialsPlugin;
impl Plugin for MaterialsPlugin {
    fn build(&self, app: &mut App) {
        app.init_resource::<TextureCache>().add_systems(
            Update,
            (resolve_textures, apply_bindings, prepare_tangents).chain(),
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn slot_override_is_instance_local_and_restores_originals() {
        let mut app = App::new();
        app.init_resource::<Assets<Image>>()
            .add_plugins(MaterialsPlugin);
        let original = Handle::<StandardMaterial>::default();
        let mut materials = Assets::<StandardMaterial>::default();
        let custom = materials.add(StandardMaterial::default());
        let root = app
            .world_mut()
            .spawn(MaterialBinding {
                material: custom.clone(),
                slot: "Paint".into(),
            })
            .id();
        let matching = app
            .world_mut()
            .spawn((
                MeshMaterial3d(original.clone()),
                bevy::gltf::GltfMaterialName("Paint".into()),
                ChildOf(root),
            ))
            .id();
        let untouched = app
            .world_mut()
            .spawn((
                MeshMaterial3d(original.clone()),
                bevy::gltf::GltfMaterialName("Rubber".into()),
                ChildOf(root),
            ))
            .id();
        app.update();
        assert_eq!(
            app.world()
                .get::<MeshMaterial3d<StandardMaterial>>(matching)
                .unwrap()
                .0,
            custom
        );
        assert_eq!(
            app.world()
                .get::<MeshMaterial3d<StandardMaterial>>(untouched)
                .unwrap()
                .0,
            original
        );
        app.world_mut().entity_mut(root).remove::<MaterialBinding>();
        app.update();
        assert_eq!(
            app.world()
                .get::<MeshMaterial3d<StandardMaterial>>(matching)
                .unwrap()
                .0,
            original
        );
    }
    #[test]
    fn texture_variants_publish_real_pixels_in_each_color_space() {
        let mut app = App::new();
        app.init_resource::<Assets<Image>>()
            .add_plugins(MaterialsPlugin);
        let pixels = Image {
            data: Some(vec![128, 64, 32, 255]),
            ..default()
        };
        let (source, color, data) = {
            let mut images = app.world_mut().resource_mut::<Assets<Image>>();
            (
                images.add(pixels),
                images.reserve_handle(),
                images.reserve_handle(),
            )
        };
        {
            let mut cache = app.world_mut().resource_mut::<TextureCache>();
            cache.0.insert(
                ("same.png".into(), true),
                (source.clone(), color.clone(), false),
            );
            cache
                .0
                .insert(("same.png".into(), false), (source, data.clone(), false));
        }
        assert!(
            app.world()
                .resource::<Assets<Image>>()
                .get(&color)
                .is_none()
        );
        app.update();
        let images = app.world().resource::<Assets<Image>>();
        let color = images.get(&color).unwrap();
        let data = images.get(&data).unwrap();
        assert!(color.texture_descriptor.format.is_srgb());
        assert!(!data.texture_descriptor.format.is_srgb());
        assert_eq!(color.data, Some(vec![128, 64, 32, 255]));
        assert_eq!(data.data, color.data);
    }

    #[test]
    fn pbr_channels_use_correct_color_space_and_surface_values() {
        let mut data = scenemax_assets::material::preset("Gold");
        data["textures"] = serde_json::json!({"baseColor":"color.png","normal":"normal.png","metallicRoughness":"orm.png"});
        let mut calls = Vec::new();
        let material = standard(&data, |p, s| {
            calls.push((p.to_owned(), s));
            Handle::default()
        })
        .unwrap();
        assert_eq!(
            calls,
            vec![
                ("color.png".into(), true),
                ("normal.png".into(), false),
                ("orm.png".into(), false)
            ]
        );
        assert_eq!(material.metallic, 1.);
        assert_eq!(material.perceptual_roughness, 0.2);
    }
}

type TextureKey = (bevy::asset::AssetPath<'static>, bool);
type TextureEntry = (Handle<Image>, Handle<Image>, bool);

/// Separate GPU images for color and data use of the same source file.
#[derive(Resource, Default)]
pub struct TextureCache(std::collections::HashMap<TextureKey, TextureEntry>);
impl TextureCache {
    /// Surface asynchronous loader failures in authoring tools.
    pub fn error(
        &self,
        server: &AssetServer,
        path: bevy::asset::AssetPath<'static>,
        srgb: bool,
    ) -> Option<String> {
        let (source, _, _) = self.0.get(&(path, srgb))?;
        match server.get_load_state(source.id()) {
            Some(bevy::asset::LoadState::Failed(error)) => Some(error.to_string()),
            _ => None,
        }
    }
    /// Request a texture without letting one channel's sRGB setting affect another.
    pub fn load(
        &mut self,
        server: &AssetServer,
        images: &mut Assets<Image>,
        path: bevy::asset::AssetPath<'static>,
        srgb: bool,
    ) -> Handle<Image> {
        self.0
            .entry((path.clone(), srgb))
            .or_insert_with(|| {
                let source = server.load(path);
                let target = images.reserve_handle();
                (source, target, false)
            })
            .1
            .clone()
    }
}
fn resolve_textures(mut cache: ResMut<TextureCache>, mut images: ResMut<Assets<Image>>) {
    for ((_, srgb), (source, target, ready)) in &mut cache.0 {
        if *ready {
            continue;
        }
        if let Some(mut image) = images.get(source).cloned() {
            image.texture_descriptor.format = if *srgb {
                image.texture_descriptor.format.add_srgb_suffix()
            } else {
                image.texture_descriptor.format.remove_srgb_suffix()
            };
            image.sampler =
                bevy::image::ImageSampler::Descriptor(bevy::image::ImageSamplerDescriptor {
                    address_mode_u: bevy::image::ImageAddressMode::Repeat,
                    address_mode_v: bevy::image::ImageAddressMode::Repeat,
                    ..default()
                });
            if images.insert(target.id(), image).is_ok() {
                *ready = true;
            }
        }
    }
}

type NativeMeshes<'w, 's> = Query<
    'w,
    's,
    (&'static Mesh3d, &'static MeshMaterial3d<StandardMaterial>),
    Or<(With<OriginalMaterial>, With<MaterialBinding>)>,
>;
// Imported glTF surfaces may not have needed tangents until this override was applied.
fn prepare_tangents(
    meshes: Option<ResMut<Assets<Mesh>>>,
    materials: Option<Res<Assets<StandardMaterial>>>,
    instances: NativeMeshes,
    mut attempted: Local<std::collections::HashSet<AssetId<Mesh>>>,
) {
    let (Some(mut meshes), Some(materials)) = (meshes, materials) else {
        return;
    };
    for (mesh, material) in &instances {
        let Some(surface) = materials.get(&material.0) else {
            continue;
        };
        if surface.anisotropy_strength == 0. && surface.normal_map_texture.is_none() {
            continue;
        }
        let Some(data) = meshes.get(&mesh.0) else {
            continue;
        };
        if data.contains_attribute(Mesh::ATTRIBUTE_TANGENT) || !attempted.insert(mesh.0.id()) {
            continue;
        }
        if let Some(mut data) = meshes.get_mut(&mesh.0)
            && let Err(error) = data.generate_tangents()
        {
            warn!("Material needs UVs and normals to generate tangents: {error}");
        }
    }
}
