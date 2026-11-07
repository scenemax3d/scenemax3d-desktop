//! Editor-only silhouette mask. Meshes share the live skeleton and transforms;
//! the overlay never changes the document, source materials, or game camera.
use super::{SceneState, gizmo};
use bevy::{
    camera::{RenderTarget, visibility::RenderLayers},
    mesh::skinning::SkinnedMesh,
    prelude::*,
    render::render_resource::{AsBindGroup, TextureFormat},
    shader::ShaderRef,
};
use std::collections::{HashMap, HashSet};

const MASK_LAYER: usize = 20;

pub(crate) struct OutlinePlugin;
impl Plugin for OutlinePlugin {
    fn build(&self, app: &mut App) {
        bevy::asset::embedded_asset!(app, "selection_outline.wesl");
        app.add_plugins(UiMaterialPlugin::<OutlineMaterial>::default())
            .init_resource::<State>()
            .add_systems(
                Update,
                update.after(super::focus::update).after(super::update),
            )
            .add_systems(
                PostUpdate,
                resize.after(bevy::ui::widget::update_viewport_render_target_size),
            );
    }
}

#[derive(Asset, TypePath, AsBindGroup, Debug, Clone)]
struct OutlineMaterial {
    #[texture(0)]
    mask: Handle<Image>,
}
impl UiMaterial for OutlineMaterial {
    fn fragment_shader() -> ShaderRef {
        "embedded://scenemax_ide/presentation/scene3d/selection_outline.wesl".into()
    }
}

/// Excluded from picking and from the source-mesh search.
#[derive(Component)]
pub(crate) struct MaskMesh;

#[derive(Resource, Default)]
struct State {
    camera: Option<Entity>,
    mask_camera: Option<Entity>,
    overlay: Option<Entity>,
    mask: Handle<Image>,
    copies: HashMap<Entity, Entity>,
    material: Handle<OutlineMaterial>,
}

fn owner(world: &World, mut entity: Entity) -> Option<usize> {
    for _ in 0..128 {
        if let Some(object) = world.get::<gizmo::SceneObject>(entity) {
            return Some(object.0);
        }
        entity = world.get::<ChildOf>(entity)?.parent();
    }
    None
}

fn clear(world: &mut World, state: &mut State) {
    for entity in state
        .copies
        .drain()
        .map(|(_, e)| e)
        .chain(state.mask_camera.take())
        .chain(state.overlay.take())
    {
        if let Ok(entity) = world.get_entity_mut(entity) {
            entity.despawn();
        }
    }
    state.camera = None;
    state.mask = Handle::default();
    state.material = Handle::default();
}

fn update(world: &mut World) {
    world.resource_scope(|world, mut state: Mut<State>| {
        let scene = world.resource::<SceneState>();
        let selected = scene.selected;
        let camera = scene.camera.filter(|e| world.get_entity(*e).is_ok());
        if state.camera != camera
            || state.overlay.is_some_and(|e| world.get_entity(e).is_err())
            || state
                .mask_camera
                .is_some_and(|e| world.get_entity(e).is_err())
        {
            clear(world, &mut state);
        }
        let Some(camera) = camera else { return };
        if state.camera.is_none() {
            let viewport = world
                .query::<(Entity, &gizmo::Viewport)>()
                .iter(world)
                .find_map(|(e, v)| (v.0 == camera).then_some(e));
            let Some(viewport) = viewport else { return };
            let mask = world
                .resource_mut::<Assets<Image>>()
                .add(Image::new_target_texture(
                    1,
                    1,
                    TextureFormat::Bgra8UnormSrgb,
                    None,
                ));
            let material = world
                .resource_mut::<Assets<OutlineMaterial>>()
                .add(OutlineMaterial { mask: mask.clone() });
            state.overlay = Some(
                world
                    .spawn((
                        MaterialNode(material.clone()),
                        Node {
                            position_type: PositionType::Absolute,
                            width: percent(100.),
                            height: percent(100.),
                            ..default()
                        },
                        Pickable::IGNORE,
                        // Explicitly above the viewport's image regardless of
                        // asynchronous UI material/image preparation order.
                        ZIndex(1),
                        ChildOf(viewport),
                    ))
                    .id(),
            );
            state.mask_camera = Some(
                world
                    .spawn((
                        Camera3d::default(),
                        bevy::pbr::ScreenSpaceTransmission::default(),
                        Camera {
                            order: -3,
                            clear_color: ClearColorConfig::Custom(Color::NONE),
                            ..default()
                        },
                        RenderTarget::Image(mask.clone().into()),
                        RenderLayers::layer(MASK_LAYER),
                        // Follow editor navigation, including smooth focus travel.
                        Transform::IDENTITY,
                        ChildOf(camera),
                    ))
                    .id(),
            );
            state.mask = mask;
            state.material = material;
            state.camera = Some(camera);
        }
        if let Some(projection) = world.get::<Projection>(camera).cloned() {
            world
                .entity_mut(state.mask_camera.unwrap())
                .insert(projection);
        }
        // Late-loaded scene descendants are included automatically. Using their
        // original material also preserves alpha-cutout silhouettes.
        let sources: Vec<_> = world
            .query_filtered::<Entity, (
                With<Mesh3d>,
                With<MeshMaterial3d<StandardMaterial>>,
                Without<MaskMesh>,
            )>()
            .iter(world)
            .filter(|e| owner(world, *e) == Some(selected))
            .collect();
        let wanted: HashSet<_> = sources.iter().copied().collect();
        state.copies.retain(|source, copy| {
            if wanted.contains(source) && world.get_entity(*copy).is_ok() {
                return true;
            }
            if let Ok(e) = world.get_entity_mut(*copy) {
                e.despawn();
            }
            false
        });
        for source in sources {
            let mesh = world.get::<Mesh3d>(source).unwrap().clone();
            let material = world
                .get::<MeshMaterial3d<StandardMaterial>>(source)
                .unwrap()
                .clone();
            let skin = world.get::<SkinnedMesh>(source).cloned();
            let copy = *state.copies.entry(source).or_insert_with(|| {
                world
                    .spawn((
                        MaskMesh,
                        Transform::IDENTITY,
                        Visibility::Inherited,
                        RenderLayers::layer(MASK_LAYER),
                        Pickable::IGNORE,
                        bevy::light::NotShadowCaster,
                        // The source's current world transform and visibility propagate
                        // to this identity child, even during gizmo drags and animation.
                        ChildOf(source),
                    ))
                    .id()
            });
            world.entity_mut(copy).insert((mesh, material));
            if let Some(skin) = skin {
                world.entity_mut(copy).insert(skin);
            } else {
                world.entity_mut(copy).remove::<SkinnedMesh>();
            }
        }
    });
}

fn resize(
    state: Res<State>,
    targets: Query<&RenderTarget>,
    mut images: ResMut<Assets<Image>>,
    mut materials: ResMut<Assets<OutlineMaterial>>,
) {
    let Some(source) = state
        .camera
        .and_then(|e| targets.get(e).ok())
        .and_then(RenderTarget::as_image)
        .and_then(|h| images.get(h))
    else {
        return;
    };
    let size = source.texture_descriptor.size;
    if let Some(mut mask) = images.get_mut(&state.mask)
        && mask.texture_descriptor.size != size
    {
        mask.resize(size);
        // Resizing replaces the GPU texture. UiMaterial caches its bind group;
        // invalidate it as well, or it keeps sampling the old (empty) texture.
        if let Some(mut material) = materials.get_mut(&state.material) {
            material.mask = state.mask.clone();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;

    #[test]
    fn selection_tracks_late_meshes_skeletons_and_cleans_up_on_tab_close() {
        let mut world = World::new();
        world.init_resource::<State>();
        world.init_resource::<Assets<Image>>();
        world.init_resource::<Assets<OutlineMaterial>>();
        let image = world
            .resource_mut::<Assets<Image>>()
            .add(Image::new_target_texture(
                640,
                480,
                TextureFormat::Bgra8UnormSrgb,
                None,
            ));
        let camera = world
            .spawn((
                Transform::IDENTITY,
                Projection::default(),
                RenderTarget::Image(image.into()),
            ))
            .id();
        world.spawn(gizmo::Viewport(camera));
        world.insert_resource(SceneState {
            camera: Some(camera),
            selected: 1,
            ..default()
        });
        let model = world
            .spawn((
                gizmo::SceneObject(1, Transform::IDENTITY),
                Transform::from_scale(Vec3::splat(2.)),
            ))
            .id();
        let other = world
            .spawn((
                gizmo::SceneObject(2, Transform::IDENTITY),
                Mesh3d::default(),
                MeshMaterial3d::<StandardMaterial>::default(),
            ))
            .id();
        update(&mut world);
        assert!(world.resource::<State>().copies.is_empty());
        let joint = world.spawn(Transform::IDENTITY).id();
        let mesh = world
            .spawn((
                Mesh3d::default(),
                MeshMaterial3d::<StandardMaterial>::default(),
                SkinnedMesh {
                    joints: vec![joint],
                    ..default()
                },
                ChildOf(model),
            ))
            .id();
        update(&mut world);
        let copy = world.resource::<State>().copies[&mesh];
        assert_eq!(world.get::<ChildOf>(copy).unwrap().parent(), mesh);
        assert_eq!(world.get::<SkinnedMesh>(copy).unwrap().joints, vec![joint]);
        assert_eq!(*world.get::<Transform>(copy).unwrap(), Transform::IDENTITY);
        assert!(
            !world
                .get::<RenderLayers>(copy)
                .unwrap()
                .intersects(&RenderLayers::layer(0))
        );
        assert!(world.get::<bevy::light::NotShadowCaster>(copy).is_some());
        update(&mut world);
        assert_eq!(
            world.resource::<State>().copies.len(),
            1,
            "no recursive mesh copies"
        );
        assert_eq!(world.resource::<State>().copies[&mesh], copy);
        world.run_system_once(resize).unwrap();
        let state = world.resource::<State>();
        assert_eq!(
            world
                .resource::<Assets<Image>>()
                .get(&state.mask)
                .unwrap()
                .width(),
            640
        );
        let overlay = state.overlay.unwrap();
        let mask_camera = state.mask_camera.unwrap();
        world.resource_mut::<SceneState>().selected = 2;
        update(&mut world);
        assert!(world.get_entity(copy).is_err());
        assert!(world.resource::<State>().copies.contains_key(&other));
        assert!(world.get::<MaskMesh>(mesh).is_none());
        world.resource_mut::<SceneState>().camera = None;
        update(&mut world);
        assert!(world.resource::<State>().copies.is_empty());
        assert!(world.get_entity(overlay).is_err());
        assert!(world.get_entity(mask_camera).is_err());
        assert!(world.get_entity(mesh).is_ok());
    }
}
