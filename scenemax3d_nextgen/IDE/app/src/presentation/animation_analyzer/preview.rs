//! Owned glTF stage; interval playback shares the projector's curve adapter.
use super::*;
use crate::presentation::scene3d::Orbit;
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, primitives::Aabb, visibility::RenderLayers},
    render::render_resource::{Extent3d, TextureDimension, TextureFormat, TextureUsages},
    ui::widget::ViewportNode,
};
const LAYER: usize = 13;
#[derive(Component)]
struct CameraMarker;
#[derive(Component)]
pub(crate) struct ModelMarker {
    pub(crate) pivot: Vec3,
}
#[derive(Component)]
struct Rest(Transform);
#[derive(Default)]
pub(super) struct Preview {
    viewport: Option<Entity>,
    pub(super) root: Option<Entity>,
    pub(super) camera: Option<Entity>,
    pub(super) model: Option<Entity>,
    gltf: Option<Handle<bevy::gltf::Gltf>>,
    loaded: bool,
    bound: bool,
    pub(super) graph: Option<Handle<AnimationGraph>>,
    node: Option<bevy::animation::graph::AnimationNodeIndex>,
    key: Option<(usize, bool, usize, usize)>,
    pub frame: bool,
    pub reset: bool,
}
impl Preview {
    pub(super) fn clear(&mut self, c: &mut Commands) {
        if let Some(viewport) = self.viewport.take() {
            c.entity(viewport)
                .try_remove::<(ViewportNode, crate::presentation::scene3d::gizmo::Viewport)>();
        }
        if let Some(root) = self.root.take() {
            c.entity(root).try_despawn();
        }
        let bound = self.bound;
        *self = Self { bound, ..default() };
    }
}
type Players<'w, 's> = Query<
    'w,
    's,
    (
        Entity,
        &'static mut AnimationPlayer,
        Option<&'static AnimationGraphHandle>,
    ),
>;
type Nodes<'w, 's> = Query<
    'w,
    's,
    (
        Entity,
        &'static GlobalTransform,
        Option<&'static Aabb>,
        Option<&'static RenderLayers>,
    ),
>;
type Rests<'w, 's> = Query<
    'w,
    's,
    (Entity, &'static Transform, Option<&'static Rest>),
    (Without<ModelMarker>, Without<CameraMarker>),
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Render<'w, 's> {
    server: Option<Res<'w, AssetServer>>,
    paths: Option<Res<'w, crate::project_assets::ProjectAssets>>,
    images: Option<ResMut<'w, Assets<Image>>>,
    gltfs: Option<Res<'w, Assets<bevy::gltf::Gltf>>>,
    clips: Option<ResMut<'w, Assets<AnimationClip>>>,
    graphs: Option<ResMut<'w, Assets<AnimationGraph>>>,
    viewports: Query<'w, 's, Entity, With<Node>>,
    parents: Query<'w, 's, &'static ChildOf>,
    players: Players<'w, 's>,
    nodes: Nodes<'w, 's>,
    cameras: Query<'w, 's, (&'static mut Orbit, &'static mut Transform), With<CameraMarker>>,
    models:
        Query<'w, 's, (&'static mut Transform, &'static mut ModelMarker), Without<CameraMarker>>,
    imported: Query<'w, 's, (Entity, &'static mut Camera), Without<CameraMarker>>,
    targets: Query<
        'w,
        's,
        (
            Entity,
            &'static bevy::animation::AnimationTargetId,
            &'static Name,
        ),
    >,
    skins: Query<'w, 's, (Entity, &'static bevy::mesh::skinning::SkinnedMesh)>,
    rests: Rests<'w, 's>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
}
pub(crate) fn update(
    mut c: Commands,
    mut s: ResMut<State>,
    mut r: Render,
    mut previous: Local<Option<bevy::winit::UpdateMode>>,
) {
    if let Some(winit) = r.winit.as_mut() {
        if s.host.is_some()
            && (s.playing || s.task.is_some() || s.source.is_some() && !s.render.loaded)
        {
            if previous.is_none() {
                *previous = Some(winit.focused_mode);
            }
            winit.focused_mode = bevy::winit::UpdateMode::Continuous;
        } else if let Some(mode) = previous.take() {
            winit.focused_mode = mode;
        }
    }
    let (Some(parts), Some(source)) = (s.parts, s.source.clone()) else {
        return;
    };
    // Reload replaces the editor host after the UI transaction phase.
    // Do not enqueue work against controls belonging to that retired host.
    if !r.viewports.contains(parts.viewport) {
        s.render.clear(&mut c);
        return;
    }
    let (Some(server), Some(paths), Some(images), Some(gltfs), Some(clips), Some(graphs)) = (
        r.server.as_ref(),
        r.paths.as_ref(),
        r.images.as_mut(),
        r.gltfs.as_ref(),
        r.clips.as_mut(),
        r.graphs.as_mut(),
    ) else {
        return;
    };
    if s.render.root.is_none() {
        let root = c.spawn((Transform::default(), Visibility::default())).id();
        let mut image = Image::new_uninit(
            Extent3d {
                width: 1000,
                height: 750,
                depth_or_array_layers: 1,
            },
            TextureDimension::D2,
            TextureFormat::Bgra8UnormSrgb,
            RenderAssetUsages::all(),
        );
        image.texture_descriptor.usage = TextureUsages::TEXTURE_BINDING
            | TextureUsages::RENDER_ATTACHMENT
            | TextureUsages::COPY_DST;
        let orbit = Orbit {
            target: Vec3::Y,
            distance: 5.,
            yaw: 0.45,
            pitch: 0.2,
        };
        let camera = c
            .spawn((
                CameraMarker,
                Camera3d::default(),
                bevy::pbr::ScreenSpaceTransmission::default(),
                Camera {
                    order: -13,
                    clear_color: ClearColorConfig::Custom(Color::srgb(0.06, 0.07, 0.09)),
                    ..default()
                },
                RenderTarget::Image(images.add(image).into()),
                RenderLayers::layer(LAYER),
                AmbientLight {
                    brightness: 300.,
                    ..default()
                },
                orbit.transform(),
                orbit,
                ChildOf(root),
            ))
            .id();
        c.entity(parts.viewport).insert((
            ViewportNode::new(camera),
            crate::presentation::scene3d::gizmo::Viewport(camera),
        ));
        if !s.render.bound {
            c.entity(parts.viewport).observe(|mut e:On<PointerDrag>,mut camera:Query<(&mut Orbit,&mut Transform),With<CameraMarker>>| {
                e.propagate(false);
                for (mut o,mut t) in &mut camera {
                    match e.button {
                        PointerButton::Secondary=>{o.yaw-=e.delta.x*0.006;o.pitch=(o.pitch+e.delta.y*0.006).clamp(-1.4,1.4);*t=o.transform();}
                        PointerButton::Middle=>{let distance=o.distance; o.target+=t.rotation*Vec3::new(-e.delta.x,e.delta.y,0.)*distance*0.002;*t=o.transform();}
                        PointerButton::Primary=>{}
                    }
                }
            });
            c.entity(parts.viewport).observe(
                |mut e: On<PointerScroll>,
                 mut q: Query<(&mut Orbit, &mut Transform), With<CameraMarker>>| {
                    e.propagate(false);
                    let factor = if e.unit == bevy::input::mouse::MouseScrollUnit::Line {
                        0.12
                    } else {
                        0.002
                    };
                    for (mut o, mut t) in &mut q {
                        o.distance = (o.distance * (-e.y * factor).exp()).clamp(0.02, 1e5);
                        *t = o.transform();
                    }
                },
            );
            s.render.bound = true;
        }
        for yaw in [-0.8, 2.2] {
            c.spawn((
                DirectionalLight {
                    illuminance: 6000.,
                    ..default()
                },
                Transform::from_rotation(Quat::from_euler(EulerRot::XYZ, -0.7, yaw, 0.)),
                RenderLayers::layer(LAYER),
                ChildOf(root),
            ));
        }
        let model = c
            .spawn((
                ModelMarker { pivot: Vec3::ZERO },
                super::super::weapon::preview::resource_transform(&s.data["models"][s.model]),
                Visibility::default(),
                ChildOf(root),
            ))
            .id();
        s.render.viewport = Some(parts.viewport);
        s.render.root = Some(root);
        s.render.model = Some(model);
        s.render.camera = Some(camera);
        s.render.frame = true;
        if let Some(path) = paths.asset(&source.root, &source.file) {
            s.render.gltf = Some(server.load(path));
        } else {
            s.error = "Cannot resolve preview model".into();
        }
    }
    let (Some(root), Some(model), Some(handle)) =
        (s.render.root, s.render.model, s.render.gltf.clone())
    else {
        return;
    };
    let under = |e, root| r.parents.iter_ancestors(e).any(|p| p == root);
    if s.render.reset {
        if let Ok((mut t, _)) = r.models.get_mut(model) {
            *t = super::super::weapon::preview::resource_transform(&s.data["models"][s.model]);
        }
        s.render.reset = false;
    }
    let Some(gltf) = gltfs.get(&handle) else {
        if let Some(bevy::asset::LoadState::Failed(e)) = server.get_load_state(handle.id()) {
            s.error = format!("Model failed: {e}");
            s.render.loaded = true;
        }
        return;
    };
    if !s.render.loaded {
        if let Some(scene) = gltf.default_scene.as_ref().or_else(|| gltf.scenes.first()) {
            c.entity(model).insert(WorldAssetRoot(scene.clone()));
        }
        s.render.loaded = true;
        s.restart = true;
    }
    let mut bounds = (Vec3::splat(f32::INFINITY), Vec3::splat(f32::NEG_INFINITY));
    for (e, gt, aabb, layers) in &r.nodes {
        if !under(e, model) {
            continue;
        }
        if layers.is_none_or(|l| !l.intersects(&RenderLayers::layer(LAYER))) {
            c.entity(e).insert(RenderLayers::layer(LAYER));
        }
        if let Some(aabb) = aabb {
            for x in [-1., 1.] {
                for y in [-1., 1.] {
                    for z in [-1., 1.] {
                        let p = gt.transform_point(
                            Vec3::from(aabb.center)
                                + Vec3::from(aabb.half_extents) * Vec3::new(x, y, z),
                        );
                        bounds.0 = bounds.0.min(p);
                        bounds.1 = bounds.1.max(p);
                    }
                }
            }
        }
    }
    for (e, mut camera) in &mut r.imported {
        if under(e, root) {
            camera.is_active = false;
        }
    }
    for (e, t, rest) in &r.rests {
        if under(e, model) {
            if s.stop {
                if let Some(rest) = rest {
                    c.entity(e).insert(rest.0);
                }
            } else if rest.is_none() {
                c.entity(e).insert(Rest(*t));
            }
        }
    }
    if s.render.frame
        && bounds.0.is_finite()
        && let Some(camera) = s.render.camera
        && let Ok((mut o, mut t)) = r.cameras.get_mut(camera)
    {
        o.target = (bounds.0 + bounds.1) * 0.5;
        if let Ok((_, gt, _, _)) = r.nodes.get(model)
            && let Ok((_, mut marker)) = r.models.get_mut(model)
        {
            marker.pivot = gt.affine().inverse().transform_point3(o.target);
        }
        o.distance = (bounds.1 - bounds.0).length().max(0.2) * 1.5;
        *t = o.transform();
        s.render.frame = false;
    }
    if s.stop {
        for (e, mut player, _) in &mut r.players {
            if under(e, model) {
                player.stop_all();
            }
        }
        s.stop = false;
        s.restart = false;
        s.time = 0.;
        return;
    }
    let Some(timeline) = source.timelines.get(s.animation) else {
        return;
    };
    let key = (s.animation, s.range, s.start, s.end);
    if (s.render.key != Some(key) || s.restart) && r.players.iter().any(|(e, _, _)| under(e, model))
    {
        let Some(handle) = gltf.animations.get(timeline.index) else {
            return;
        };
        let Some(original) = clips.get(handle) else {
            return;
        };
        let joints: std::collections::HashSet<_> = r
            .skins
            .iter()
            .filter(|(e, _)| under(*e, model))
            .flat_map(|(_, skin)| skin.joints.iter().copied())
            .collect();
        let targets = r
            .targets
            .iter()
            .filter(|(e, _, _)| under(*e, model))
            .map(|(e, id, name)| (*id, name.to_string(), joints.contains(&e)))
            .collect::<Vec<_>>();
        let adapted = super::super::weapon::playback::adapt(original, &targets);
        let clip = if s.range {
            let record = scenemax_assets::animation_ranges::Record {
                name: "Preview".into(),
                source: timeline.name.clone(),
                start: s.start,
                end: s.end,
            };
            match timeline
                .interval(&record)
                .and_then(|(a, b)| scenemax_animation::slice(&adapted, a, b))
            {
                Ok(clip) => clip,
                Err(e) => {
                    s.error = e;
                    s.playing = false;
                    return;
                }
            }
        } else {
            adapted
        };
        let (graph, node) = AnimationGraph::from_clip(clips.add(clip));
        s.render.graph = Some(graphs.add(graph));
        s.render.node = Some(node);
        s.error.clear();
        s.render.key = Some(key);
        s.restart = true;
    }
    let (Some(graph), Some(node)) = (s.render.graph.clone(), s.render.node) else {
        return;
    };
    let start = if s.range {
        timeline.times.get(s.start).copied().unwrap_or(0.)
    } else {
        0.
    };
    let mut found = false;
    for (e, mut player, bound) in &mut r.players {
        if !under(e, model) {
            continue;
        }
        found = true;
        if s.stop {
            player.stop_all();
            continue;
        }
        if bound.is_none_or(|h| h.0 != graph) {
            c.entity(e).insert(AnimationGraphHandle(graph.clone()));
        }
        if s.restart || s.playing && player.animation(node).is_none() {
            player.stop_all();
            player.play(node).repeat();
        }
        if let Some(animation) = player.animation_mut(node) {
            animation.set_speed(s.speed / 100.);
            if s.playing {
                animation.resume();
            } else {
                animation.pause();
            }
            if let Some(frame) = s.seek {
                let i = frame.max(0.) as usize;
                let time = timeline
                    .times
                    .get(i)
                    .copied()
                    .unwrap_or_else(|| *timeline.times.last().unwrap_or(&0.));
                let next = timeline.times.get(i + 1).copied().unwrap_or(time);
                let time = time + (next - time) * frame.fract();
                let end = if s.range {
                    timeline.times.get(s.end).copied().unwrap_or(time)
                } else {
                    *timeline.times.last().unwrap_or(&time)
                };
                animation.seek_to((time.clamp(start, end) - start).max(0.));
            }
            let seconds = animation.seek_time() + start;
            let i = timeline
                .times
                .partition_point(|t| *t <= seconds)
                .saturating_sub(1);
            let a = timeline.times.get(i).copied().unwrap_or(0.);
            let b = timeline.times.get(i + 1).copied().unwrap_or(a);
            s.time = i as f32 + if b > a { (seconds - a) / (b - a) } else { 0. };
        }
    }
    if found {
        s.restart = false;
        s.stop = false;
        s.seek = None;
    }
}
