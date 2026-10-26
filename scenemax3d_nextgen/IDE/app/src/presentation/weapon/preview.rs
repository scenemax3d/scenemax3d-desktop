//! Isolated model, rig, animation, collider and transform preview.
use super::*;
use crate::presentation::scene3d::{
    Orbit,
    gizmo::{GizmoMaterial, math},
};
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, primitives::Aabb, visibility::RenderLayers},
    render::render_resource::{Extent3d, TextureDimension, TextureFormat, TextureUsages},
    ui::widget::ViewportNode,
};
const LAYER: usize = 10;
#[derive(Component)]
struct PreviewCamera;
#[derive(Component)]
struct Pose;
#[derive(Component)]
struct GizmoRoot;
#[derive(Component)]
struct Wire;
#[derive(Default)]
pub(super) struct Preview {
    root: Option<Entity>,
    camera: Option<Entity>,
    pose: Option<Entity>,
    holder: Option<Entity>,
    models: Vec<(Entity, Handle<bevy::gltf::Gltf>, bool)>,
    key: String,
    pub frame: bool,
    frame_wait: u8,
    pub reload: bool,
    pub restart: bool,
    adapted: bool,
    graph: Option<Handle<AnimationGraph>>,
    clips: Vec<(
        String,
        bevy::animation::graph::AnimationNodeIndex,
        Handle<AnimationClip>,
    )>,
    colliders: String,
    socket_key: String,
    gizmo: Option<Entity>,
    gizmo_mode: bool,
    drag: Option<DragState>,
}
struct DragState {
    axis: Vec3,
    origin: Vec3,
    basis: Quat,
    parent_scale: Vec3,
    start: Transform,
    param: f32,
    vector: Vec3,
    pointer: String,
    first: bool,
    rotate: bool,
}
impl Preview {
    pub(super) fn first_clip(&self) -> Option<String> {
        self.clips.first().map(|v| v.0.clone())
    }
    pub(super) fn ready(&self) -> bool {
        self.root.is_some() && self.models.is_empty() && !self.frame
    }

    pub(super) fn clear(&mut self, c: &mut Commands) {
        if let Some(root) = self.root.take() {
            c.entity(root).try_despawn();
        }
        *self = Self::default();
    }
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Render<'w, 's> {
    server: Option<Res<'w, AssetServer>>,
    paths: Option<Res<'w, crate::project_assets::ProjectAssets>>,
    images: Option<ResMut<'w, Assets<Image>>>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    gizmo_materials: Option<ResMut<'w, Assets<GizmoMaterial>>>,
    gltfs: Option<Res<'w, Assets<bevy::gltf::Gltf>>>,
    graphs: Option<ResMut<'w, Assets<AnimationGraph>>>,
    animations: Option<ResMut<'w, Assets<AnimationClip>>>,
    parents: Query<'w, 's, &'static ChildOf>,
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
    nodes: Query<
        'w,
        's,
        (
            Entity,
            &'static GlobalTransform,
            Option<&'static Aabb>,
            Option<&'static Name>,
        ),
    >,
    cameras: Query<
        'w,
        's,
        (
            &'static mut Orbit,
            &'static mut Transform,
            &'static GlobalTransform,
            &'static Projection,
        ),
        With<PreviewCamera>,
    >,
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
    wires: Query<'w, 's, Entity, With<Wire>>,
    ports: Query<'w, 's, (&'static ComputedNode, &'static UiGlobalTransform)>,
    windows: Query<'w, 's, &'static Window, With<bevy::window::PrimaryWindow>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
}
pub(crate) fn update(
    mut c: Commands,
    mut s: ResMut<State>,
    mut r: Render,
    session: Res<Session>,
    mut queue: ResMut<CommandQueue>,
    mut previous_mode: Local<Option<bevy::winit::UpdateMode>>,
) {
    if let Some(winit) = r.winit.as_mut() {
        if s.host.is_some() && (s.playing || !s.render.models.is_empty()) {
            if previous_mode.is_none() {
                *previous_mode = Some(winit.focused_mode);
            }
            winit.focused_mode = bevy::winit::UpdateMode::Continuous;
        } else if let Some(mode) = previous_mode.take() {
            winit.focused_mode = mode;
        }
    }
    let (
        Some(server),
        Some(paths),
        Some(images),
        Some(meshes),
        Some(materials),
        Some(gltfs),
        Some(graphs),
        Some(gizmo_materials),
    ) = (
        r.server.as_ref(),
        r.paths.as_ref(),
        r.images.as_mut(),
        r.meshes.as_mut(),
        r.materials.as_mut(),
        r.gltfs.as_ref(),
        r.graphs.as_mut(),
        r.gizmo_materials.as_mut(),
    )
    else {
        return;
    };
    let Some(parts) = s.parts.as_ref() else {
        return;
    };
    let viewport = parts.viewport;
    let animation_ui = parts.animation;
    let socket_ui = parts.sockets;
    let key = format!(
        "{}|{}",
        s.data["modelAssetId"], s.data["designerMetadata"]["previewPlayerModelAssetId"]
    );
    if s.render.root.is_none() || s.render.key != key || s.render.reload {
        s.render.clear(&mut c);
        s.render.key = key;
        s.render.frame = true;
        s.render.frame_wait = 12;
        s.sockets.clear();
        s.preview_status.clear();
        let root = c.spawn((Transform::default(), Visibility::default())).id();
        s.render.root = Some(root);
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
                    order: -10,
                    clear_color: ClearColorConfig::Custom(Color::srgb(0.065, 0.075, 0.095)),
                    ..default()
                },
                RenderTarget::Image(images.add(image).into()),
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
        s.render.camera = Some(camera);
        c.entity(viewport).insert(ViewportNode::new(camera));
        if !s.viewport_bound {
            s.viewport_bound = true;
            c.entity(viewport).observe(
                |mut e: On<PointerDrag>,
                 s: Res<State>,
                 mut q: Query<(&mut Orbit, &mut Transform), With<PreviewCamera>>| {
                    if e.button == PointerButton::Secondary {
                        e.propagate(false);
                        if let Some(camera) = s.render.camera
                            && let Ok((mut o, mut t)) = q.get_mut(camera)
                        {
                            o.yaw -= e.delta.x * 0.007;
                            o.pitch = (o.pitch + e.delta.y * 0.007).clamp(-1.45, 1.45);
                            *t = o.transform();
                        }
                    }
                },
            );
            c.entity(viewport).observe(
                |mut e: On<PointerScroll>,
                 s: Res<State>,
                 mut q: Query<(&mut Orbit, &mut Transform), With<PreviewCamera>>| {
                    e.propagate(false);
                    if let Some(camera) = s.render.camera
                        && let Ok((mut o, mut t)) = q.get_mut(camera)
                    {
                        let delta = if e.unit == bevy::input::mouse::MouseScrollUnit::Line {
                            e.y * 0.12
                        } else {
                            e.y * 0.001
                        };
                        o.distance = (o.distance * (1. + delta)).clamp(0.02, 100000.);
                        *t = o.transform();
                    }
                },
            );
        }
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
        let pose = c
            .spawn((
                Pose,
                Transform::default(),
                Visibility::default(),
                ChildOf(root),
            ))
            .id();
        s.render.pose = Some(pose);
        for holder in [true, false] {
            let name = if holder {
                s.data["designerMetadata"]["previewPlayerModelAssetId"].as_str()
            } else {
                s.data["modelAssetId"].as_str()
            }
            .unwrap_or("")
            .to_owned();
            if name.is_empty() {
                continue;
            }
            let Some(asset) = s.models.iter().find(|a| a.name == name).cloned() else {
                s.preview_status = format!("Registered model not found: {name}");
                continue;
            };
            let Some(file) = asset.file.as_ref().filter(|p| {
                p.extension().is_some_and(|e| {
                    e.eq_ignore_ascii_case("gltf") || e.eq_ignore_ascii_case("glb")
                })
            }) else {
                s.preview_status = format!("{name}: native preview requires glTF/GLB");
                continue;
            };
            if let Some(path) = paths.asset(&asset.root, file) {
                let object = c
                    .spawn((
                        resource_transform(&asset.data),
                        Visibility::default(),
                        RenderLayers::layer(LAYER),
                        ChildOf(if holder { root } else { pose }),
                    ))
                    .id();
                if holder {
                    s.render.holder = Some(object);
                }
                s.render.models.push((object, server.load(path), holder));
            }
        }
        c.entity(animation_ui).despawn_children();
        s.animation.clear();
        s.playing = false;
    }
    let Some(root) = s.render.root else { return };
    let Some(pose) = s.render.pose else { return };
    let mut waiting = Vec::new();
    let pending = std::mem::take(&mut s.render.models);
    for (object, handle, holder) in pending {
        if let Some(gltf) = gltfs.get(&handle) {
            if let Some(scene) = gltf.default_scene.as_ref().or_else(|| gltf.scenes.first()) {
                c.entity(object).insert(WorldAssetRoot(scene.clone()));
            }
            if holder {
                let (graph, nodes) = AnimationGraph::from_clips(gltf.animations.clone());
                s.render.graph = Some(graphs.add(graph));
                s.render.clips = gltf
                    .animations
                    .iter()
                    .cloned()
                    .zip(nodes)
                    .enumerate()
                    .map(|(i, (h, n))| {
                        let name = gltf
                            .named_animations
                            .iter()
                            .find(|(_, v)| **v == h)
                            .map(|(k, _)| k.to_string())
                            .unwrap_or_else(|| format!("Animation {}", i + 1));
                        (name, n, h)
                    })
                    .collect();
                let choices = std::iter::once(String::new())
                    .chain(s.render.clips.iter().map(|v| v.0.clone()))
                    .collect::<Vec<_>>();
                c.entity(animation_ui).despawn_children();
                c.spawn((
                    scenemax_ide_ui::label("Test animation", 12.),
                    ChildOf(animation_ui),
                ));
                scenemax_ide_ui::property::dropdown(
                    &mut c,
                    animation_ui,
                    Control("animation"),
                    "",
                    &choices,
                );
            }
        } else if let Some(bevy::asset::LoadState::Failed(e)) = server.get_load_state(handle.id()) {
            s.preview_status = format!("Model preview failed: {e}");
        } else {
            waiting.push((object, handle, holder));
        }
    }
    s.render.models = waiting;
    let mut bounds = (Vec3::splat(f32::INFINITY), Vec3::splat(f32::NEG_INFINITY));

    let posture = s.data["postures"][s.posture].clone();
    let socket = posture["attachmentPoint"].as_str().unwrap_or("");
    let mut attachment = Transform::default();
    let standalone = s.data["designerMetadata"]["previewPlayerModelAssetId"]
        .as_str()
        .unwrap_or("")
        .is_empty();
    let mut found = socket.is_empty() || standalone;
    let joints: std::collections::HashSet<_> = r
        .skins
        .iter()
        .filter(|(e, _)| {
            s.render
                .holder
                .is_some_and(|h| r.parents.iter_ancestors(*e).any(|p| p == h))
        })
        .flat_map(|(_, skin)| skin.joints.iter().copied())
        .collect();
    let mut candidates = Vec::new();
    for (entity, gt, aabb, name) in &r.nodes {
        if !r.parents.iter_ancestors(entity).any(|p| p == root) {
            continue;
        }
        c.entity(entity).insert(RenderLayers::layer(LAYER));
        if let Some(holder) = s.render.holder
            && r.parents.iter_ancestors(entity).any(|p| p == holder)
            && let Some(name) = name
        {
            candidates.push((entity, name.to_string(), joints.contains(&entity)));
        }
        if let Some(aabb) = aabb
            && !r.wires.contains(entity)
            && !s
                .render
                .gizmo
                .is_some_and(|g| r.parents.iter_ancestors(entity).any(|p| p == g))
        {
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
    let sockets = super::playback::bone_names(&candidates);
    let socket_key = format!("{}:{socket}:{sockets:?}", s.posture);
    if s.render.socket_key != socket_key {
        c.entity(socket_ui).despawn_children();
        c.spawn((
            scenemax_ide_ui::label("Attachment bone", 12.),
            ChildOf(socket_ui),
        ));
        let pointer = format!("/postures/{}/attachmentPoint", s.posture);
        let options = std::iter::once(String::new())
            .chain(sockets.iter().cloned())
            .collect::<Vec<_>>();
        scenemax_ide_ui::property::dropdown(
            &mut c,
            socket_ui,
            Field(pointer, socket.into()),
            socket,
            &options,
        );
        s.render.socket_key = socket_key;
        s.sockets = sockets;
    }
    let local = transform(&posture["transform"]);
    let resolved = if standalone {
        Ok(None)
    } else {
        super::attachment::resolve(socket, &candidates)
    };
    c.entity(pose).remove::<super::attachment::BoneAttachment>();
    if let Ok(Some(joint)) = resolved.as_ref()
        && let Ok((_, gt, _, _)) = r.nodes.get(*joint)
    {
        let (_, rotation, translation) = gt.to_scale_rotation_translation();
        attachment = Transform {
            translation,
            rotation,
            scale: Vec3::ONE,
        };
        found = true;
        c.entity(pose).insert(super::attachment::BoneAttachment {
            joint: *joint,
            offset: local,
        });
    }
    let world = attachment.mul_transform(local);
    c.entity(pose).insert((
        world,
        if found {
            Visibility::Inherited
        } else {
            Visibility::Hidden
        },
    ));
    if let Err(error) = resolved {
        s.preview_status = format!("{error}; weapon preview hidden");
    } else if s.preview_status.starts_with("Attachment") || s.preview_status.is_empty() {
        s.preview_status = if s.data["modelAssetId"].as_str().unwrap_or("").is_empty() {
            "No weapon model · collider-only weapon".into()
        } else {
            "Live preview".into()
        };
    }
    for (entity, mut camera) in &mut r.imported {
        if r.parents.iter_ancestors(entity).any(|p| p == root) {
            camera.is_active = false;
        }
    }
    let wire_key = format!("{}:{}", s.data["colliders"], s.collider);
    if s.render.colliders != wire_key {
        for entity in &r.wires {
            c.entity(entity).try_despawn();
        }
        for (i, v) in s.data["colliders"]
            .as_array()
            .into_iter()
            .flatten()
            .enumerate()
        {
            let color = if i == s.collider {
                Color::srgb(1., 0.75, 0.1)
            } else {
                Color::srgb(0.1, 0.9, 0.7)
            };
            c.spawn((
                Wire,
                Mesh3d(meshes.add(wire(v["shape"].as_str() == Some("sphere")))),
                MeshMaterial3d(materials.add(StandardMaterial {
                    base_color: color,
                    unlit: true,
                    ..default()
                })),
                transform(&v["transform"]),
                RenderLayers::layer(LAYER),
                ChildOf(pose),
            ));
        }
        s.render.colliders = wire_key;
    }
    if s.render.models.is_empty() && bounds.0.is_finite() && s.render.frame_wait > 0 {
        s.render.frame_wait -= 1;
    }
    if s.render.frame
        && s.render.frame_wait == 0
        && s.render.models.is_empty()
        && bounds.0.is_finite()
        && let Some(camera) = s.render.camera
        && let Ok((mut o, mut t, _, _)) = r.cameras.get_mut(camera)
    {
        o.target = (bounds.0 + bounds.1) * 0.5;
        o.distance = (bounds.1 - bounds.0).length().max(0.1) * 1.5;
        *t = o.transform();
        s.render.frame = false;
    }
    if !s.render.adapted
        && !joints.is_empty()
        && !s.render.clips.is_empty()
        && let Some(animations) = r.animations.as_mut()
    {
        let targets: Vec<_> = r
            .targets
            .iter()
            .filter(|(entity, _, _)| {
                s.render
                    .holder
                    .is_some_and(|h| r.parents.iter_ancestors(*entity).any(|p| p == h))
            })
            .map(|(entity, id, name)| (*id, name.to_string(), joints.contains(&entity)))
            .collect();
        if targets.iter().any(|(_, _, used)| *used)
            && s.render
                .clips
                .iter()
                .all(|(_, _, handle)| animations.get(handle).is_some())
        {
            let prepared: Vec<_> = s
                .render
                .clips
                .iter()
                .filter_map(|(name, _, handle)| {
                    animations
                        .get(handle)
                        .map(|clip| (name.clone(), super::playback::adapt(clip, &targets)))
                })
                .collect();
            let handles: Vec<_> = prepared
                .into_iter()
                .map(|(name, clip)| (name, animations.add(clip)))
                .collect();
            let (graph, nodes) =
                AnimationGraph::from_clips(handles.iter().map(|(_, handle)| handle.clone()));
            s.render.graph = Some(graphs.add(graph));
            s.render.clips = handles
                .into_iter()
                .zip(nodes)
                .map(|((name, handle), node)| (name, node, handle))
                .collect();
            s.render.adapted = true;
            s.render.restart = true;
        }
    }
    if let Some(graph) = s.render.graph.clone() {
        let selected = s.render.clips.iter().find(|v| v.0 == s.animation).cloned();
        let restart = s.render.restart;
        let playing = s.playing;
        let speed = s.speed / 100.;
        let mut bound_any = false;
        for (entity, mut player, bound) in &mut r.players {
            if !s
                .render
                .holder
                .is_some_and(|h| r.parents.iter_ancestors(entity).any(|p| p == h))
            {
                continue;
            }
            bound_any = true;
            let graph_changed = bound.is_none_or(|bound| bound.0 != graph);
            if graph_changed {
                c.entity(entity).insert(AnimationGraphHandle(graph.clone()));
            }
            if restart
                || graph_changed
                || selected
                    .as_ref()
                    .is_some_and(|(_, node, _)| playing && player.animation(*node).is_none())
            {
                player.stop_all();
                if let Some((_, node, _)) = &selected {
                    player.play(*node).repeat();
                }
            }
            for (_, anim) in player.playing_animations_mut() {
                anim.set_speed(speed);
                if playing {
                    anim.resume();
                } else {
                    anim.pause();
                }
            }
            if let Some((_, node, clip)) = &selected
                && let Some(anim) = player.animation(*node)
                && let Some(duration) = r
                    .animations
                    .as_ref()
                    .and_then(|a| a.get(clip))
                    .map(AnimationClip::duration)
                && duration > 0.
                && found
            {
                s.preview_status = format!(
                    "{} · {:.0}% · {}",
                    s.animation,
                    100. * (anim.seek_time() / duration).fract(),
                    if playing { "Playing" } else { "Paused" }
                );
            }
        }
        if bound_any {
            s.render.restart = false;
        }
    }
    // Local-axis gizmos share the editor's ray math and always-visible material.
    let target = if s.colliders {
        s.data["colliders"]
            .get(s.collider)
            .map(|v| world.mul_transform(transform(&v["transform"])))
            .unwrap_or(world)
    } else {
        world
    };
    let size = s
        .render
        .camera
        .and_then(|camera| r.cameras.get(camera).ok())
        .map(|(_, _, gt, _)| gt.translation().distance(target.translation) * 0.13)
        .unwrap_or(0.5)
        .max(0.02);
    if s.render.gizmo.is_none() || s.render.gizmo_mode != s.rotate {
        if let Some(g) = s.render.gizmo.take() {
            c.entity(g).try_despawn();
        }
        let g = c
            .spawn((
                GizmoRoot,
                Transform::default(),
                Visibility::default(),
                ChildOf(root),
            ))
            .id();
        for (axis, color) in [
            (Vec3::X, Color::srgb(1., 0.2, 0.2)),
            (Vec3::Y, Color::srgb(0.2, 1., 0.3)),
            (Vec3::Z, Color::srgb(0.2, 0.5, 1.)),
        ] {
            let mesh = if s.rotate {
                Torus::new(0.975, 1.025).mesh().build()
            } else {
                Cylinder::new(0.025, 1.).mesh().build()
            };
            let t = Transform {
                translation: if s.rotate { Vec3::ZERO } else { axis * 0.5 },
                rotation: Quat::from_rotation_arc(Vec3::Y, axis),
                ..default()
            };
            c.spawn((
                Mesh3d(meshes.add(mesh)),
                MeshMaterial3d(gizmo_materials.add(GizmoMaterial {
                    base_color: color.to_linear(),
                    emissive: color.to_linear(),
                })),
                t,
                RenderLayers::layer(LAYER),
                ChildOf(g),
            ));
        }
        s.render.gizmo = Some(g);
        s.render.gizmo_mode = s.rotate;
    }
    let basis = if s.colliders {
        world.rotation
    } else {
        attachment.rotation
    };
    if let Some(g) = s.render.gizmo {
        c.entity(g).insert(Transform {
            translation: target.translation,
            rotation: basis,
            scale: Vec3::splat(size),
        });
    }
    let ray = r
        .windows
        .single()
        .ok()
        .and_then(Window::cursor_position)
        .and_then(|cursor| {
            let (node, ui) = r.ports.get(viewport).ok()?;
            let local = ui
                .try_inverse()?
                .transform_point2(cursor / node.inverse_scale_factor());
            let uv = local / node.size() + Vec2::splat(0.5);
            if uv.min_element() < 0. || uv.max_element() > 1. {
                return None;
            }
            let (_, _, gt, projection) = r.cameras.get(s.render.camera?).ok()?;
            let Projection::Perspective(p) = projection else {
                return None;
            };
            let half = (p.fov * 0.5).tan();
            let d = gt.rotation()
                * Vec3::new(
                    (uv.x * 2. - 1.) * half * p.aspect_ratio,
                    (1. - uv.y * 2.) * half,
                    -1.,
                );
            Some(Ray3d::new(gt.translation(), Dir3::new(d).ok()?))
        });
    if r.mouse.just_pressed(MouseButton::Left)
        && let Some(ray) = ray
    {
        let axes = [Vec3::X, Vec3::Y, Vec3::Z].map(|a| basis * a);
        let hit = axes
            .into_iter()
            .filter_map(|axis| {
                let d = if s.rotate {
                    math::ray_circle_distance(&ray, target.translation, axis, size)
                } else {
                    math::closest_distance_ray_segment(
                        &ray,
                        target.translation,
                        target.translation + axis * size,
                    )
                }?;
                (d < size * 0.12).then_some((axis, d))
            })
            .min_by(|a, b| a.1.total_cmp(&b.1));
        if let Some((axis, _)) = hit {
            let pointer = if s.colliders {
                format!("/colliders/{}/transform", s.collider)
            } else {
                format!("/postures/{}/transform", s.posture)
            };
            if let Some(value) = s.data.pointer(&pointer) {
                let start = transform(value);
                let vector = math::ray_plane_point(&ray, target.translation, axis)
                    .map(|v| (v - target.translation).normalize_or_zero())
                    .unwrap_or(Vec3::X);
                s.render.drag = Some(DragState {
                    axis,
                    origin: target.translation,
                    basis,
                    parent_scale: if s.colliders { world.scale } else { Vec3::ONE },
                    start,
                    param: math::ray_line_param(&ray, target.translation, axis).unwrap_or(0.),
                    vector,
                    pointer,
                    first: true,
                    rotate: s.rotate,
                });
                s.playing = false;
            }
        }
    }
    if !r.mouse.pressed(MouseButton::Left) {
        s.render.drag = None;
    }
    if let Some(ray) = ray
        && let Some(drag) = s.render.drag.as_mut()
    {
        let mut t = drag.start;
        if drag.rotate {
            if let Some(p) = math::ray_plane_point(&ray, drag.origin, drag.axis) {
                let v = (p - drag.origin).normalize_or_zero();
                let angle = drag
                    .axis
                    .dot(drag.vector.cross(v))
                    .atan2(drag.vector.dot(v));
                t.rotation = Quat::from_axis_angle(drag.basis.inverse() * drag.axis, angle)
                    * drag.start.rotation;
            }
        } else if let Some(param) = math::ray_line_param(&ray, drag.origin, drag.axis) {
            t.translation +=
                drag.basis.inverse() * drag.axis * (param - drag.param) / drag.parent_scale;
        }
        if let Some(id) = session.workspace.active_id()
            && let Ok(doc) = session.workspace.document(id)
        {
            let value = transform_value(t);
            let key = drag.pointer.clone();
            if doc.text()
                != weapon::edit(doc.text(), &key, value.clone(), 0)
                    .unwrap_or_else(|_| doc.text().into())
            {
                queue.0.push_front(Command::Weapon(Edit {
                    id,
                    revision: doc.revision(),
                    pointer: key,
                    value,
                    continuing: !drag.first,
                }));
                drag.first = false;
            }
        }
    }
}
fn n(v: &Value, k: &str, default: f32) -> f32 {
    v[k].as_f64().map(|n| n as f32).unwrap_or(default)
}
fn transform(v: &Value) -> Transform {
    Transform {
        translation: Vec3::new(
            n(v, "offsetX", 0.),
            n(v, "offsetY", 0.),
            n(v, "offsetZ", 0.),
        ),
        rotation: Quat::from_euler(
            EulerRot::XYZ,
            n(v, "rotationX", 0.).to_radians(),
            n(v, "rotationY", 0.).to_radians(),
            n(v, "rotationZ", 0.).to_radians(),
        ),
        scale: Vec3::new(n(v, "scaleX", 1.), n(v, "scaleY", 1.), n(v, "scaleZ", 1.)),
    }
}
pub(crate) fn resource_transform(v: &Value) -> Transform {
    Transform {
        translation: Vec3::new(
            n(v, "localTranslationX", 0.),
            n(v, "localTranslationY", 0.),
            n(v, "localTranslationZ", 0.),
        ),
        rotation: Quat::from_rotation_y(n(v, "rotateY", 0.).to_radians()),
        scale: Vec3::new(n(v, "scaleX", 1.), n(v, "scaleY", 1.), n(v, "scaleZ", 1.)),
    }
}
fn transform_value(t: Transform) -> Value {
    let (x, y, z) = t.rotation.to_euler(EulerRot::XYZ);
    json!({"offsetX":t.translation.x,"offsetY":t.translation.y,"offsetZ":t.translation.z,"rotationX":x.to_degrees(),"rotationY":y.to_degrees(),"rotationZ":z.to_degrees(),"scaleX":t.scale.x,"scaleY":t.scale.y,"scaleZ":t.scale.z})
}
fn wire(sphere: bool) -> Mesh {
    let mut positions: Vec<[f32; 3]> = Vec::new();
    if sphere {
        for axis in [Vec3::X, Vec3::Y, Vec3::Z] {
            let (a, b) = math::perpendicular_pair(axis);
            for i in 0..64 {
                for j in [i, i + 1] {
                    let angle = j as f32 / 64. * std::f32::consts::TAU;
                    positions.push(((a * angle.cos() + b * angle.sin()) * 0.5).to_array());
                }
            }
        }
    } else {
        for axis in 0..3 {
            for a in [-0.5, 0.5] {
                for b in [-0.5, 0.5] {
                    for k in [-0.5, 0.5] {
                        let mut v = [0.; 3];
                        v[axis] = k;
                        v[(axis + 1) % 3] = a;
                        v[(axis + 2) % 3] = b;
                        positions.push(v);
                    }
                }
            }
        }
    }
    let len = positions.len();
    Mesh::new(
        bevy::mesh::PrimitiveTopology::LineList,
        RenderAssetUsages::all(),
    )
    .with_inserted_attribute(Mesh::ATTRIBUTE_POSITION, positions)
    .with_inserted_attribute(Mesh::ATTRIBUTE_NORMAL, vec![[0., 1., 0.]; len])
    .with_inserted_attribute(Mesh::ATTRIBUTE_UV_0, vec![[0., 0.]; len])
}
