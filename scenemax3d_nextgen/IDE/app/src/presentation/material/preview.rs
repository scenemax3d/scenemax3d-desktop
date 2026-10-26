//! Isolated preview stage with shared game material evaluation.
use super::super::scene3d::Orbit;
use super::*;
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, primitives::Aabb, visibility::RenderLayers},
    render::render_resource::{
        Extent3d, TextureDimension, TextureFormat, TextureUsages, TextureViewDescriptor,
        TextureViewDimension,
    },
    ui::widget::ViewportNode,
};
const LAYER: usize = 5;
#[derive(Component)]
struct PreviewCamera;
#[derive(Component)]
struct PreviewObject;
#[derive(Component)]
struct PreviewLight;
#[derive(Default)]
pub(crate) struct Cache {
    source: String,
    model: Option<Handle<bevy::gltf::Gltf>>,
    waiting: bool,
    auto_frame: bool,
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Render<'w, 's> {
    smoke: Option<Res<'w, super::super::smoke::SmokeCapture>>,
    server: Option<Res<'w, AssetServer>>,
    textures: Option<ResMut<'w, scenemax_materials::TextureCache>>,
    assets: Option<Res<'w, crate::project_assets::ProjectAssets>>,
    images: Option<ResMut<'w, Assets<Image>>>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    gltfs: Option<Res<'w, Assets<bevy::gltf::Gltf>>>,
    parents: Query<'w, 's, &'static ChildOf>,
    imported_cameras: Query<'w, 's, (Entity, &'static mut Camera), Without<PreviewCamera>>,
    nodes: Query<
        'w,
        's,
        (
            Entity,
            &'static GlobalTransform,
            Option<&'static Aabb>,
            Option<&'static bevy::gltf::GltfMaterialName>,
        ),
    >,
    cameras: Query<'w, 's, (&'static mut Orbit, &'static mut Transform), With<PreviewCamera>>,
    objects: Query<'w, 's, &'static mut Transform, (With<PreviewObject>, Without<PreviewCamera>)>,
    lights: Query<'w, 's, &'static mut DirectionalLight, With<PreviewLight>>,
    thumbs: Query<
        'w,
        's,
        (
            &'static TextureThumb,
            &'static mut ImageNode,
            &'static mut Node,
        ),
    >,
}
fn environment() -> Image {
    let mut data = Vec::new();
    for face in 0..6 {
        for y in 0..32 {
            for x in 0..32 {
                let softbox = (8..24).contains(&x) && (3..20).contains(&y);
                let n = if face == 2 {
                    170
                } else if (face == 0 || face == 4) && softbox {
                    245
                } else {
                    38
                };
                data.extend_from_slice(&[n, n, n, 255]);
            }
        }
    }
    let mut image = Image::new(
        Extent3d {
            width: 32,
            height: 32,
            depth_or_array_layers: 6,
        },
        TextureDimension::D2,
        data,
        TextureFormat::Rgba8UnormSrgb,
        RenderAssetUsages::all(),
    );
    image.texture_view_descriptor = Some(TextureViewDescriptor {
        dimension: Some(TextureViewDimension::Cube),
        ..default()
    });
    image
}
fn observe(commands: &mut Commands, viewport: Entity, camera: Entity) {
    commands.entity(viewport).observe(
        move |mut e: On<PointerDrag>,
              mut cameras: Query<(&mut Orbit, &mut Transform), With<PreviewCamera>>| {
            if e.button != PointerButton::Secondary {
                return;
            }
            e.propagate(false);
            if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                orbit.yaw -= e.delta.x * 0.007;
                orbit.pitch = (orbit.pitch + e.delta.y * 0.007).clamp(-1.4, 1.4);
                *transform = orbit.transform();
            }
        },
    );
    commands.entity(viewport).observe(
        move |mut e: On<PointerScroll>,
              mut cameras: Query<(&mut Orbit, &mut Transform), With<PreviewCamera>>| {
            e.propagate(false);
            if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                let factor = if e.unit == bevy::input::mouse::MouseScrollUnit::Line {
                    0.12
                } else {
                    0.003
                };
                orbit.distance = (orbit.distance * (-e.y * factor).exp()).clamp(0.05, 1e5);
                *transform = orbit.transform();
            }
        },
    );
}
pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    library: Res<MaterialLibrary>,
    time: Res<Time>,
    mut render: Render,
    mut cache: Local<Cache>,
) {
    let Some(parts) = state.parts.as_ref() else {
        *cache = Cache::default();
        return;
    };
    let viewport = parts.viewport;
    let slot_panel = parts.slots;
    let (Some(server), Some(assets), Some(images), Some(meshes), Some(materials), Some(gltfs)) = (
        render.server.as_ref(),
        render.assets.as_ref(),
        render.images.as_mut(),
        render.meshes.as_mut(),
        render.materials.as_mut(),
        render.gltfs.as_ref(),
    ) else {
        return;
    };
    let Some(library) = &library.assets else {
        return;
    };
    if state.world.is_none() {
        if render.smoke.is_some()
            && let Ok(model) = std::env::var("SCENEMAX_SMOKE_MATERIAL_MODEL")
            && library.models.contains(&model)
        {
            state.model = model;
        }
        let root = commands
            .spawn((Transform::default(), Visibility::default()))
            .id();
        let mut image = Image::new_uninit(
            Extent3d {
                width: 1024,
                height: 768,
                depth_or_array_layers: 1,
            },
            TextureDimension::D2,
            TextureFormat::Bgra8UnormSrgb,
            RenderAssetUsages::all(),
        );
        image.texture_descriptor.usage = TextureUsages::TEXTURE_BINDING
            | TextureUsages::COPY_DST
            | TextureUsages::RENDER_ATTACHMENT;
        let target = images.add(image);
        let env = images.add(environment());
        let orbit = Orbit {
            target: Vec3::ZERO,
            distance: 4.,
            yaw: 0.5,
            pitch: 0.2,
        };
        let camera = commands
            .spawn((
                PreviewCamera,
                Camera3d::default(),
                bevy::pbr::ScreenSpaceTransmission::default(),
                bevy::camera::Hdr,
                bevy::camera::Exposure { ev100: 10.0 },
                bevy::core_pipeline::tonemapping::Tonemapping::AcesFitted,
                Camera {
                    order: -5,
                    clear_color: ClearColorConfig::Custom(Color::srgb_u8(26, 29, 35)),
                    ..default()
                },
                RenderTarget::Image(target.into()),
                RenderLayers::layer(LAYER),
                AmbientLight {
                    brightness: 90.,
                    ..default()
                },
                bevy::light::GeneratedEnvironmentMapLight {
                    environment_map: env,
                    intensity: 800.,
                    ..default()
                },
                orbit,
                orbit.transform(),
                ChildOf(root),
            ))
            .id();
        for (yaw, illuminance) in [(-0.8, 11000.), (2.2, 4000.)] {
            commands.spawn((
                PreviewLight,
                DirectionalLight {
                    illuminance,
                    ..default()
                },
                Transform::from_rotation(Quat::from_euler(EulerRot::XYZ, -0.7, yaw, 0.)),
                RenderLayers::layer(LAYER),
                ChildOf(root),
            ));
        }
        commands.spawn((
            Mesh3d(meshes.add(Plane3d::default().mesh().size(200., 200.))),
            MeshMaterial3d(materials.add(StandardMaterial {
                base_color: Color::srgb(0.055, 0.064, 0.08),
                perceptual_roughness: 0.65,
                ..default()
            })),
            Transform::from_xyz(0., -1.05, 0.),
            RenderLayers::layer(LAYER),
            ChildOf(root),
        ));
        state.material = materials.add(StandardMaterial::default());
        state.world = Some(root);
        state.camera = Some(camera);
        state.model_dirty = true;
        commands.entity(viewport).insert(ViewportNode::new(camera));
        observe(&mut commands, viewport, camera);
        cache.source.clear();
    }
    let Some(root) = state.world else {
        return;
    };
    for (entity, mut camera) in &mut render.imported_cameras {
        if camera.is_active && render.parents.iter_ancestors(entity).any(|p| p == root) {
            camera.is_active = false;
        }
    }
    let source = state.data.to_string();
    if cache.source != source {
        let surface = scenemax_materials::standard(&state.data, |path, srgb| {
            let Some(path) = assets.asset(&library.root, &library.root.join(path)) else {
                return Handle::default();
            };
            if let Some(textures) = render.textures.as_mut() {
                textures.load(server, images, path, srgb)
            } else {
                Handle::default()
            }
        });
        if let Ok(surface) = surface
            && let Some(mut existing) = materials.get_mut(&state.material)
        {
            *existing = surface;
        }
        cache.source = source;
        if let Some(object) = state.object {
            commands
                .entity(object)
                .insert(scenemax_materials::MaterialBinding {
                    material: state.material.clone(),
                    slot: state.data["targetSlot"].as_str().unwrap_or_default().into(),
                });
        }
        for (thumb, mut image, mut node) in &mut render.thumbs {
            let path = state.data["textures"][&thumb.0].as_str().unwrap_or("");
            node.display = if path.is_empty() {
                Display::None
            } else {
                Display::Flex
            };
            if !path.is_empty()
                && let Some(path) = assets.asset(&library.root, &library.root.join(path))
            {
                image.image = server.load(path);
            }
        }
    }
    for mut light in &mut render.lights {
        light.color = if state.warm {
            Color::srgb(1., 0.78, 0.58)
        } else {
            Color::WHITE
        };
    }
    if state.model_dirty {
        if let Some(object) = state.object.take() {
            commands.entity(object).try_despawn();
        }
        let object = commands
            .spawn((
                PreviewObject,
                Transform::default(),
                Visibility::default(),
                RenderLayers::layer(LAYER),
                scenemax_materials::MaterialBinding {
                    material: state.material.clone(),
                    slot: state.data["targetSlot"].as_str().unwrap_or_default().into(),
                },
                ChildOf(root),
            ))
            .id();
        state.object = Some(object);
        state.model_dirty = false;
        state.slots.clear();
        cache.model = None;
        cache.waiting = false;
        if state.model.is_empty() {
            let mut mesh = match state.shape {
                1 => Mesh::from(Cuboid::new(1.5, 1.5, 1.5)),
                2 => Mesh::from(Plane3d::default().mesh().size(2.5, 2.5)),
                _ => Sphere::new(1.).mesh().uv(64, 32),
            };
            let _ = mesh.generate_tangents();
            commands.entity(object).insert((
                Mesh3d(meshes.add(mesh)),
                MeshMaterial3d(state.material.clone()),
            ));
            state.frame = true;
        } else if let Some(path) = assets.asset(&library.root, &library.root.join(&state.model)) {
            cache.model = Some(server.load(path));
            cache.waiting = true;
            cache.auto_frame = true;
        }
    }
    if cache.waiting
        && let Some(handle) = &cache.model
    {
        if let Some(model) = gltfs.get(handle) {
            if let (Some(scene), Some(object)) = (
                model
                    .default_scene
                    .as_ref()
                    .or_else(|| model.scenes.first()),
                state.object,
            ) {
                commands
                    .entity(object)
                    .insert(WorldAssetRoot(scene.clone()));
            }
            cache.waiting = false;
        } else if let Some(bevy::asset::LoadState::Failed(e)) = server.get_load_state(handle.id()) {
            state.error = format!("Model preview failed: {e}");
            cache.waiting = false;
        }
    }
    let mut slots = std::collections::BTreeSet::new();
    let mut min = Vec3::splat(f32::INFINITY);
    let mut max = Vec3::splat(f32::NEG_INFINITY);
    if let Some(object) = state.object {
        for (entity, transform, aabb, name) in &render.nodes {
            if entity != object && !render.parents.iter_ancestors(entity).any(|p| p == object) {
                continue;
            }
            commands.entity(entity).insert(RenderLayers::layer(LAYER));
            if let Some(name) = name {
                slots.insert(name.0.clone());
            }
            if let Some(aabb) = aabb {
                for x in [-1., 1.] {
                    for y in [-1., 1.] {
                        for z in [-1., 1.] {
                            let p = transform.transform_point(
                                Vec3::from(aabb.center)
                                    + Vec3::from(aabb.half_extents) * Vec3::new(x, y, z),
                            );
                            min = min.min(p);
                            max = max.max(p);
                        }
                    }
                }
            }
        }
        if state.rotating
            && let Ok(mut transform) = render.objects.get_mut(object)
        {
            transform.rotate_y(time.delta_secs() * 0.35);
        }
    }
    let slots: Vec<_> = slots.into_iter().collect();
    if slots != state.slots {
        state.slots = slots.clone();
        commands.entity(slot_panel).despawn_children();
        scenemax_ide_ui::button(
            &mut commands,
            slot_panel,
            "All slots",
            Action::Slot(String::new()),
        );
        for slot in slots {
            scenemax_ide_ui::button(&mut commands, slot_panel, &slot, Action::Slot(slot.clone()));
        }
    }
    if (state.frame || cache.auto_frame)
        && min.is_finite()
        && max.is_finite()
        && let Some(camera) = state.camera
        && let Ok((mut orbit, mut transform)) = render.cameras.get_mut(camera)
    {
        orbit.target = (min + max) * 0.5;
        orbit.distance = (max - min).length().max(0.1) * 1.5;
        *transform = orbit.transform();
        state.frame = false;
        cache.auto_frame = false;
    }
    for (_, key, srgb) in scenemax_assets::material::TEXTURES {
        if let Some(relative) = state.data["textures"][key]
            .as_str()
            .filter(|p| !p.is_empty())
            && let Some(path) = assets.asset(&library.root, &library.root.join(relative))
            && let Some(error) = render
                .textures
                .as_ref()
                .and_then(|cache| cache.error(server, path, *srgb))
        {
            state.error = format!("Texture failed: {relative}: {error}");
            break;
        }
        if let Some(path) = state.data["textures"][key]
            .as_str()
            .filter(|p| !p.is_empty())
            && !library.textures.iter().any(|p| p == path)
        {
            state.error = format!("Missing project texture: {path}");
            break;
        }
    }
}
