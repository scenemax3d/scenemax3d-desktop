use super::super::scene3d::Orbit;
use super::*;
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, primitives::Aabb, visibility::RenderLayers},
    render::render_resource::{Extent3d, TextureDimension, TextureFormat, TextureUsages},
    ui::widget::ViewportNode,
};
const LAYER: usize = 9;
#[derive(Component)]
struct PreviewCamera;
#[derive(Component)]
struct PreviewObject;
#[derive(Component)]
struct AudioPreview(String, u64);
#[derive(Default)]
pub(super) struct Preview {
    pub(super) thumbnails: std::collections::BTreeMap<String, Handle<Image>>,
    root: Option<Entity>,
    object: Option<Entity>,
    camera: Option<Entity>,
    viewport: Option<Entity>,
    key: String,
    asset: Option<Handle<bevy::gltf::Gltf>>,
    waiting: bool,
    pub(super) frame: bool,
    pub(super) spin: bool,
    pub(super) playing: bool,
    pub(super) clip: usize,
    pub(super) restart: bool,
    graph: Option<Handle<AnimationGraph>>,
    clips: Vec<bevy::animation::graph::AnimationNodeIndex>,
}
impl Preview {
    pub(super) fn clear(&mut self, c: &mut Commands) {
        if let Some(root) = self.root.take() {
            c.entity(root).try_despawn();
        }
        self.object = None;
        self.camera = None;
        self.viewport = None;
        self.key.clear();
        self.asset = None;
        self.graph = None;
        self.clips.clear();
    }
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Render<'w, 's> {
    server: Option<Res<'w, AssetServer>>,
    paths: Option<Res<'w, crate::project_assets::ProjectAssets>>,
    images: Option<ResMut<'w, Assets<Image>>>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    textures: Option<ResMut<'w, scenemax_materials::TextureCache>>,
    gltfs: Option<Res<'w, Assets<bevy::gltf::Gltf>>>,
    graphs: Option<ResMut<'w, Assets<AnimationGraph>>>,
    all_thumbs: Query<'w, 's, (), With<view::Thumb>>,
    thumbs: Query<'w, 's, (Entity, &'static view::Thumb), Added<view::Thumb>>,
    parents: Query<'w, 's, &'static ChildOf>,
    nodes: Query<'w, 's, (Entity, &'static GlobalTransform, Option<&'static Aabb>)>,
    cameras: Query<'w, 's, (&'static mut Orbit, &'static mut Transform), With<PreviewCamera>>,
    objects: Query<'w, 's, &'static mut Transform, (With<PreviewObject>, Without<PreviewCamera>)>,
    imported: Query<'w, 's, (Entity, &'static mut Camera), Without<PreviewCamera>>,
    players: Query<
        'w,
        's,
        (
            Entity,
            &'static mut AnimationPlayer,
            Option<&'static AnimationGraphHandle>,
        ),
    >,
    audio: Query<'w, 's, (Entity, &'static AudioPreview, &'static AudioPlayer)>,
    time: Res<'w, Time>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
}
pub(crate) fn update(
    mut c: Commands,
    mut state: ResMut<State>,
    mut r: Render,
    mut previous_mode: Local<Option<bevy::winit::UpdateMode>>,
    mut thumbnails: Local<Thumbnails>,
) {
    if let Some(winit) = r.winit.as_mut() {
        if state.open && (state.render.spin || state.render.playing) {
            if previous_mode.is_none() {
                *previous_mode = Some(winit.focused_mode);
            }
            winit.focused_mode = bevy::winit::UpdateMode::Continuous;
        } else if let Some(mode) = previous_mode.take() {
            winit.focused_mode = mode;
        }
    }
    let mut audio_exists = false;
    for (entity, audio, player) in &r.audio {
        if let Some(server) = r.server.as_ref()
            && let Some(bevy::asset::LoadState::Failed(error)) =
                server.get_load_state(player.0.id())
        {
            state.status = format!("Audio preview failed: {error}");
            state.play_audio = false;
        }
        if state.open
            && state.play_audio
            && state.active.as_ref() == Some(&audio.0)
            && state.audio_generation == audio.1
        {
            audio_exists = true;
        } else {
            c.entity(entity).try_despawn();
        }
    }
    if !audio_exists
        && state.open
        && state.play_audio
        && let Some(a) = state.selected_asset()
        && let Some(file) = &a.file
        && let (Some(paths), Some(server)) = (r.paths.as_ref(), r.server.as_ref())
        && let Some(path) = paths.asset(&a.root, file)
    {
        c.spawn((
            AudioPreview(a.id.clone(), state.audio_generation),
            AudioPlayer::new(server.load(path)),
            PlaybackSettings::ONCE,
        ));
    }

    let (Some(server), Some(paths), Some(images), Some(meshes), Some(materials), Some(gltfs)) = (
        r.server.as_ref(),
        r.paths.as_ref(),
        r.images.as_mut(),
        r.meshes.as_mut(),
        r.materials.as_mut(),
        r.gltfs.as_ref(),
    ) else {
        return;
    };
    if thumbnails.generation != state.generation {
        thumbnails.cache.clear();
        thumbnails.pending.clear();
        thumbnails.generation = state.generation;
    }
    for (entity, thumb) in &r.thumbs {
        if let Some(handle) = thumbnails.cache.get(&thumb.path) {
            c.entity(entity).try_insert(ImageNode::new(handle.clone()));
        } else {
            thumbnails
                .pending
                .push_back((entity, thumb.path.clone(), state.generation));
        }
    }
    let mut i = 0;
    while i < thumbnails.jobs.len() {
        if let Some(result) =
            bevy::tasks::block_on(bevy::tasks::poll_once(&mut thumbnails.jobs[i].3))
        {
            let (entity, path, generation, _) = thumbnails.jobs.swap_remove(i);
            if generation == state.generation
                && let Ok((width, height, bytes)) = result
            {
                let handle = images.add(Image::new(
                    Extent3d {
                        width,
                        height,
                        depth_or_array_layers: 1,
                    },
                    TextureDimension::D2,
                    bytes,
                    TextureFormat::Rgba8UnormSrgb,
                    RenderAssetUsages::all(),
                ));
                c.entity(entity).try_insert(ImageNode::new(handle.clone()));
                thumbnails.cache.insert(path, handle);
                if thumbnails.cache.len() > 128
                    && let Some(first) = thumbnails.cache.keys().next().cloned()
                {
                    thumbnails.cache.remove(&first);
                }
            }
        } else {
            i += 1;
        }
    }
    thumbnails
        .pending
        .retain(|(entity, _, _)| r.all_thumbs.contains(*entity));
    while thumbnails.jobs.len() < 2 {
        let Some((entity, path, generation)) = thumbnails.pending.pop_front() else {
            break;
        };
        let input = path.clone();
        let task = bevy::tasks::IoTaskPool::get()
            .spawn(async move { inventory::thumbnail(&input).map_err(|e| e.to_string()) });
        thumbnails.jobs.push((entity, path, generation, task));
    }
    let selected = state.selected_asset().cloned();
    let Some(viewport) = state.viewport.filter(|_| state.open) else {
        state.render.clear(&mut c);
        return;
    };
    let Some(asset) = selected else {
        return;
    };
    if state.render.key != asset.id || state.render.viewport != Some(viewport) {
        state.render.clear(&mut c);
        state.render.key = asset.id.clone();
        state.render.viewport = Some(viewport);
        state.render.frame = true;
        state.render.clip = 0;
        state.render.restart = true;
        state.render.playing = false;
        let root = c.spawn((Transform::default(), Visibility::default())).id();
        let mut image = Image::new_uninit(
            Extent3d {
                width: 800,
                height: 600,
                depth_or_array_layers: 1,
            },
            TextureDimension::D2,
            TextureFormat::Bgra8UnormSrgb,
            RenderAssetUsages::all(),
        );
        image.texture_descriptor.usage = TextureUsages::TEXTURE_BINDING
            | TextureUsages::RENDER_ATTACHMENT
            | TextureUsages::COPY_DST;
        let target = images.add(image);
        state
            .render
            .thumbnails
            .insert(asset.id.clone(), target.clone());
        if state.render.thumbnails.len() > 32
            && let Some(first) = state.render.thumbnails.keys().next().cloned()
        {
            state.render.thumbnails.remove(&first);
        }
        state.redraw = true;
        let orbit = Orbit {
            target: Vec3::ZERO,
            distance: 4.,
            yaw: 0.5,
            pitch: 0.25,
        };
        let camera = c
            .spawn((
                PreviewCamera,
                Camera3d::default(),
                bevy::pbr::ScreenSpaceTransmission::default(),
                Camera {
                    order: -9,
                    clear_color: ClearColorConfig::Custom(Color::srgb(0.065, 0.075, 0.095)),
                    ..default()
                },
                RenderTarget::Image(target.into()),
                RenderLayers::layer(LAYER),
                AmbientLight {
                    brightness: 220.,
                    ..default()
                },
                orbit.transform(),
                orbit,
                ChildOf(root),
            ))
            .id();
        for (yaw, illuminance) in [(-0.8, 9000.), (2.2, 3500.)] {
            c.spawn((
                DirectionalLight {
                    illuminance,
                    ..default()
                },
                Transform::from_rotation(Quat::from_euler(EulerRot::XYZ, -0.7, yaw, 0.)),
                RenderLayers::layer(LAYER),
                ChildOf(root),
            ));
        }
        let object = c
            .spawn((
                PreviewObject,
                Transform::default(),
                Visibility::default(),
                RenderLayers::layer(LAYER),
                ChildOf(root),
            ))
            .id();
        if asset
            .file
            .as_ref()
            .is_some_and(|p| p.extension().is_some_and(|e| e == "smmat"))
        {
            let resources = asset.project.join("resources");
            let surface = scenemax_materials::standard(&asset.data, |p, srgb| {
                let Some(path) = paths.asset(&resources, &resources.join(p)) else {
                    return Handle::default();
                };
                if let Some(textures) = r.textures.as_mut() {
                    textures.load(server, images, path, srgb)
                } else {
                    Handle::default()
                }
            });
            match surface {
                Ok(surface) => {
                    let mut mesh = Sphere::new(1.).mesh().uv(48, 24);
                    let _ = mesh.generate_tangents();
                    c.entity(object).insert((
                        Mesh3d(meshes.add(mesh)),
                        MeshMaterial3d(materials.add(surface)),
                    ));
                }
                Err(e) => state.status = format!("Material preview: {e}"),
            }
        } else if let Some(file) = &asset.file
            && let Some(path) = paths.asset(&asset.root, file)
        {
            state.render.asset = Some(server.load(path));
            state.render.waiting = true;
        }
        c.entity(viewport).insert(ViewportNode::new(camera));
        c.entity(viewport).observe(move |mut e:On<PointerDrag>,mut cameras:Query<(&mut Orbit,&mut Transform),With<PreviewCamera>>|{if e.button==PointerButton::Secondary{e.propagate(false);if let Ok((mut o,mut t))=cameras.get_mut(camera){o.yaw-=e.delta.x*0.007;o.pitch=(o.pitch+e.delta.y*0.007).clamp(-1.45,1.45);*t=o.transform();}}});
        c.entity(viewport).observe(move |mut e:On<PointerScroll>,mut cameras:Query<(&mut Orbit,&mut Transform),With<PreviewCamera>>|{e.propagate(false);if let Ok((mut o,mut t))=cameras.get_mut(camera){o.distance=(o.distance*(1.+e.y*0.001)).clamp(0.05,100000.);*t=o.transform();}});
        state.render.root = Some(root);
        state.render.camera = Some(camera);
        state.render.object = Some(object);
    }
    let Some(root) = state.render.root else {
        return;
    };
    let Some(object) = state.render.object else {
        return;
    };
    if state.render.waiting
        && let Some(handle) = &state.render.asset
    {
        if let Some(gltf) = gltfs.get(handle) {
            if let Some(scene) = gltf.default_scene.as_ref().or_else(|| gltf.scenes.first()) {
                c.entity(object).insert(WorldAssetRoot(scene.clone()));
            }
            if let Some(graphs) = r.graphs.as_mut() {
                let (graph, clips) = AnimationGraph::from_clips(gltf.animations.clone());
                state.render.graph = Some(graphs.add(graph));
                state.render.clips = clips;
            }
            state.render.waiting = false;
        } else if let Some(bevy::asset::LoadState::Failed(e)) = server.get_load_state(handle.id()) {
            state.status = format!("Preview failed: {e}");
            state.render.waiting = false;
        }
    }
    for (entity, mut camera) in &mut r.imported {
        if r.parents.iter_ancestors(entity).any(|p| p == root) {
            camera.is_active = false;
        }
    }
    let mut min = Vec3::splat(f32::INFINITY);
    let mut max = Vec3::splat(f32::NEG_INFINITY);
    for (entity, transform, aabb) in &r.nodes {
        if entity != object && !r.parents.iter_ancestors(entity).any(|p| p == object) {
            continue;
        }
        c.entity(entity).insert(RenderLayers::layer(LAYER));
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
    if state.render.frame
        && min.is_finite()
        && max.is_finite()
        && let Some(camera) = state.render.camera
        && let Ok((mut orbit, mut transform)) = r.cameras.get_mut(camera)
    {
        orbit.target = (min + max) * 0.5;
        orbit.distance = (max - min).length().max(0.1) * 1.5;
        *transform = orbit.transform();
        state.render.frame = false;
    }
    if state.render.spin
        && let Ok(mut transform) = r.objects.get_mut(object)
    {
        transform.rotate_y(r.time.delta_secs() * 0.35);
    }
    let mut found = false;
    if let Some(graph) = state.render.graph.clone()
        && !state.render.clips.is_empty()
    {
        let node = state.render.clips[state.render.clip % state.render.clips.len()];
        for (entity, mut player, bound) in &mut r.players {
            if !r.parents.iter_ancestors(entity).any(|p| p == root) {
                continue;
            }
            found = true;
            if bound.is_none() {
                c.entity(entity).insert(AnimationGraphHandle(graph.clone()));
            }
            if state.render.restart {
                player.stop_all();
                player.play(node).repeat();
            }
            for (_, animation) in player.playing_animations_mut() {
                if state.render.playing {
                    animation.resume();
                } else {
                    animation.pause();
                }
            }
        }
    }
    if found {
        state.render.restart = false;
    }
}

type ThumbnailTask = bevy::tasks::Task<Result<(u32, u32, Vec<u8>), String>>;
#[derive(Default)]
pub(crate) struct Thumbnails {
    generation: u64,
    cache: std::collections::BTreeMap<PathBuf, Handle<Image>>,
    pending: std::collections::VecDeque<(Entity, PathBuf, u64)>,
    jobs: Vec<(Entity, PathBuf, u64, ThumbnailTask)>,
}
