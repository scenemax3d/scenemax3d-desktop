//! Isolated offscreen model world; camera and document controls survive property edits.
use super::super::scene3d::Orbit;
use super::*;
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, primitives::Aabb, visibility::RenderLayers},
    render::render_resource::{Extent3d, TextureDimension, TextureFormat, TextureUsages},
    ui::widget::ViewportNode,
};
#[derive(Component)]
pub(crate) struct Pose;
#[derive(Component)]
pub(crate) struct WorldOwner(pub Entity);
#[derive(Component)]
pub(super) struct Visual;
#[derive(Component)]
pub(super) struct Camera;
#[derive(Component)]
pub(super) struct Rest(pub Transform);
#[derive(Default, Reflect, bevy::gizmos::config::GizmoConfigGroup)]
pub(crate) struct ImportLines;
pub(super) fn observe(commands: &mut Commands, viewport: Entity, host: Entity) {
    commands.entity(viewport).observe(
        move |mut e: On<Pointer<Drag>>,
              states: Query<&Import>,
              mut cameras: Query<(&mut Orbit, &mut Transform), With<Camera>>| {
            if !matches!(e.button, PointerButton::Secondary | PointerButton::Middle) {
                return;
            }
            e.propagate(false);
            let Ok(state) = states.get(host) else {
                return;
            };
            let Some(camera) = state.camera else {
                return;
            };
            if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                if state.pan || e.button == PointerButton::Middle {
                    let distance = orbit.distance;
                    orbit.target += transform.rotation
                        * Vec3::new(-e.delta.x, e.delta.y, 0.)
                        * distance
                        * 0.002;
                } else {
                    orbit.yaw -= e.delta.x * 0.007;
                    orbit.pitch = (orbit.pitch + e.delta.y * 0.007).clamp(-1.5, 1.5);
                }
                *transform = orbit.transform();
            }
        },
    );
    commands.entity(viewport).observe(
        move |mut e: On<Pointer<Scroll>>,
              states: Query<&Import>,
              mut cameras: Query<(&mut Orbit, &mut Transform), With<Camera>>| {
            e.propagate(false);
            let Ok(state) = states.get(host) else {
                return;
            };
            let Some(camera) = state.camera else {
                return;
            };
            if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                let factor = if e.unit == bevy::input::mouse::MouseScrollUnit::Line {
                    0.1
                } else {
                    0.003
                };
                orbit.distance = (orbit.distance * (-e.y * factor).exp()).clamp(0.01, 1e6);
                *transform = orbit.transform();
            }
        },
    );
}
#[allow(clippy::type_complexity)] // Bevy query tuples encode disjoint ECS access.
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Render<'w, 's> {
    server: Option<Res<'w, AssetServer>>,
    assets: Option<Res<'w, crate::project_assets::ProjectAssets>>,
    images: Option<ResMut<'w, Assets<Image>>>,
    gltfs: Option<Res<'w, Assets<bevy::gltf::Gltf>>>,
    graphs: Option<ResMut<'w, Assets<AnimationGraph>>>,
    poses: Query<'w, 's, &'static mut Transform, (With<Pose>, Without<Camera>, Without<Visual>)>,
    visuals: Query<'w, 's, &'static mut Transform, (With<Visual>, Without<Camera>, Without<Pose>)>,
    cameras: Query<
        'w,
        's,
        (&'static mut Orbit, &'static mut Transform),
        (With<Camera>, Without<Pose>, Without<Visual>),
    >,
    nodes: Query<
        'w,
        's,
        (
            Entity,
            &'static GlobalTransform,
            Option<&'static Aabb>,
            Option<&'static Transform>,
            Option<&'static Rest>,
        ),
        (Without<Pose>, Without<Visual>, Without<Camera>),
    >,
    parents: Query<'w, 's, &'static ChildOf>,
    worlds: Query<'w, 's, (Entity, &'static WorldOwner)>,
    source_cameras: Query<'w, 's, (Entity, &'static mut bevy::camera::Camera), Without<Camera>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
}
fn vec(v: &Value) -> Vec3 {
    Vec3::new(
        v[0].as_f64().unwrap_or(0.) as f32,
        v[1].as_f64().unwrap_or(0.) as f32,
        v[2].as_f64().unwrap_or(0.) as f32,
    )
}
pub(super) fn number(v: &Value, key: &str) -> f32 {
    v[key].as_f64().unwrap_or(0.) as f32
}
pub(crate) fn update(
    mut commands: Commands,
    mut states: Query<(Entity, &EditorHost, &mut Import)>,
    mut global: ResMut<State>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<ViewChange>,
    mut view: Render,
) {
    global.target = None;
    for (world, owner) in &view.worlds {
        if states.get(owner.0).is_err() {
            commands.entity(world).try_despawn();
        }
    }
    for (host, owner, mut state) in &mut states {
        if !view.mouse.pressed(MouseButton::Left) {
            state.gizmo_editing = false;
        }
        let active = session.workspace.active_id() == Some(owner.0);
        if !active {
            if let Some(world) = state.world.take() {
                if let Some(camera) = state.camera
                    && let Ok((orbit, _)) = view.cameras.get(camera)
                {
                    state.camera_view = Some(*orbit);
                }
                commands.entity(world).try_despawn();
                state.camera = None;
                state.pose = None;
                state.loaded = false;
            }
            continue;
        }
        let Ok(doc) = session.workspace.document(owner.0) else {
            continue;
        };
        let Ok(mut draft) = serde_json::from_str::<Value>(doc.text()) else {
            continue;
        };
        let (Some(server), Some(assets), Some(images), Some(gltfs), Some(graphs)) = (
            view.server.as_deref(),
            view.assets.as_deref(),
            view.images.as_deref_mut(),
            view.gltfs.as_deref(),
            view.graphs.as_deref_mut(),
        ) else {
            continue;
        };
        let Some(parts) = state.parts else {
            continue;
        };
        if state.world.is_none() {
            let root = commands
                .spawn((
                    Transform::default(),
                    Visibility::default(),
                    Name::new("Model import preview"),
                    WorldOwner(host),
                ))
                .id();
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
                | TextureUsages::COPY_DST
                | TextureUsages::RENDER_ATTACHMENT;
            let target = images.add(image);
            let orbit = state.camera_view.unwrap_or(Orbit {
                target: Vec3::Y,
                distance: 6.,
                yaw: 0.65,
                pitch: 0.25,
            });
            let camera = commands
                .spawn((
                    Camera,
                    Camera3d::default(),
                    RenderLayers::from_layers(&[1, 3]),
                    bevy::prelude::Camera {
                        order: -2,
                        clear_color: ClearColorConfig::Custom(Color::srgb_u8(32, 35, 41)),
                        ..default()
                    },
                    AmbientLight {
                        brightness: 220.,
                        ..default()
                    },
                    RenderTarget::Image(target.into()),
                    orbit,
                    orbit.transform(),
                    ChildOf(root),
                ))
                .id();
            for (yaw, illuminance) in [(-0.8, 10000.), (2.2, 4000.)] {
                commands.spawn((
                    DirectionalLight {
                        illuminance,
                        ..default()
                    },
                    RenderLayers::layer(3),
                    Transform::from_rotation(Quat::from_euler(EulerRot::XYZ, -0.8, yaw, 0.)),
                    ChildOf(root),
                ));
            }
            let pose = commands
                .spawn((
                    Pose,
                    super::super::scene3d::gizmo::SceneObject(usize::MAX, Transform::default()),
                    Transform::default(),
                    Visibility::default(),
                    ChildOf(root),
                ))
                .id();
            let visual = commands
                .spawn((
                    Visual,
                    Transform::default(),
                    Visibility::default(),
                    ChildOf(pose),
                ))
                .id();
            commands.entity(parts.viewport).insert((
                ViewportNode::new(camera),
                super::super::scene3d::gizmo::Viewport(camera),
            ));
            commands.entity(parts.viewport).despawn_children();
            super::super::scene3d::navigation::spawn(&mut commands, parts.viewport, camera);
            state.world = Some(root);
            state.camera = Some(camera);
            state.pose = Some(pose);
            state.visual = Some(visual);
            state.last_pose = Transform::default();
            state.framed = false;
        }
        let (Some(root), Some(camera), Some(pose), Some(visual)) =
            (state.world, state.camera, state.pose, state.visual)
        else {
            continue;
        };
        global.target = Some((root, camera, usize::MAX));
        if state.asset.is_none()
            && let Some(prepared) = &state.prepared
            && let Some(path) = assets.asset(&prepared.root, &prepared.path)
        {
            state.asset = Some(server.load(path));
        }
        if !state.loaded
            && let Some(handle) = &state.asset
        {
            if let Some(gltf) = gltfs.get(handle) {
                if let Some(scene) = gltf.default_scene.as_ref().or(gltf.scenes.first()) {
                    commands.spawn((
                        WorldAssetRoot(scene.clone()),
                        Transform::default(),
                        ChildOf(visual),
                    ));
                    let (graph, clips) =
                        AnimationGraph::from_clips(gltf.animations.iter().cloned());
                    state.graph = Some(graphs.add(graph));
                    state.clips = clips;
                    state.loaded = true;
                    state.stop = true;
                } else {
                    state.status = "This file contains no scene to preview".into();
                }
            } else if let Some(bevy::asset::LoadState::Failed(error)) =
                server.get_load_state(handle.id())
            {
                state.status = format!("Cannot load model: {error}");
            }
        }
        if let Ok(mut transform) = view.poses.get_mut(pose) {
            if *transform != state.last_pose {
                if draft["preview"]["proportional"].as_bool() == Some(true)
                    && let Some(axis) =
                        (0..3).find(|&i| transform.scale[i] != state.last_pose.scale[i])
                {
                    transform.scale = state.last_pose.scale
                        * (transform.scale[axis] / state.last_pose.scale[axis]);
                }
                let r = transform.rotation.to_euler(EulerRot::XYZ);
                draft["preview"]["position"] = json!(transform.translation.to_array());
                draft["preview"]["rotation"] =
                    json!([r.0.to_degrees(), r.1.to_degrees(), r.2.to_degrees()]);
                for (key, value) in ["scaleX", "scaleY", "scaleZ"]
                    .into_iter()
                    .zip(transform.scale.to_array())
                {
                    draft[key] = json!(value);
                }
                if let Ok(doc) = session.workspace.document_mut(owner.0) {
                    let source = serde_json::to_string_pretty(&draft).unwrap_or_default();
                    if state.gizmo_editing {
                        doc.replace_text_continuing(source);
                    } else {
                        doc.replace_text(source);
                    }
                    state.gizmo_editing = true;
                    changes.write(ViewChange::BufferChanged(owner.0));
                }
            } else {
                let r = vec(&draft["preview"]["rotation"]) * std::f32::consts::PI / 180.;
                *transform = Transform {
                    translation: vec(&draft["preview"]["position"]),
                    rotation: Quat::from_euler(EulerRot::XYZ, r.x, r.y, r.z),
                    scale: Vec3::new(
                        number(&draft, "scaleX"),
                        number(&draft, "scaleY"),
                        number(&draft, "scaleZ"),
                    ),
                };
            }
            state.last_pose = *transform;
        }
        if let Ok(mut transform) = view.visuals.get_mut(visual) {
            *transform = Transform {
                translation: Vec3::new(
                    number(&draft, "transX"),
                    number(&draft, "transY"),
                    number(&draft, "transZ"),
                ),
                rotation: Quat::from_rotation_y(number(&draft, "rotateY").to_radians()),
                ..default()
            };
        }
        for (entity, mut source_camera) in &mut view.source_cameras {
            if source_camera.is_active && view.parents.iter_ancestors(entity).any(|p| p == root) {
                source_camera.is_active = false;
            }
        }
        let mut min = Vec3::splat(f32::INFINITY);
        let mut max = Vec3::splat(f32::NEG_INFINITY);
        for (entity, gt, aabb, transform, rest) in &view.nodes {
            if !view.parents.iter_ancestors(entity).any(|p| p == root) {
                continue;
            }
            commands.entity(entity).insert(RenderLayers::layer(3));
            if rest.is_none()
                && let Some(transform) = transform
            {
                commands.entity(entity).insert(Rest(*transform));
            }
            if let Some(aabb) = aabb {
                for x in [-1., 1.] {
                    for y in [-1., 1.] {
                        for z in [-1., 1.] {
                            let p = gt.transform_point(
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
        if (!state.framed || state.pending_fit)
            && min.is_finite()
            && max.is_finite()
            && let Ok((mut orbit, mut transform)) = view.cameras.get_mut(camera)
        {
            orbit.target = (min + max) * 0.5;
            orbit.distance = ((max - min).length() * 1.5).max(0.1);
            *transform = orbit.transform();
            state.framed = true;
            state.pending_fit = false;
        }
    }
}
/// Grid, physics capsule and skeleton are editor overlays, never part of the imported model.
pub(crate) fn overlays(
    mut lines: Gizmos<ImportLines>,
    states: Query<(&EditorHost, &Import)>,
    session: Res<Session>,
    poses: Query<&Transform, With<Pose>>,
    nodes: Query<(Entity, &GlobalTransform)>,
    parents: Query<&ChildOf>,
    skins: Query<(Entity, &bevy::mesh::skinning::SkinnedMesh)>,
) {
    for (owner, state) in &states {
        if session.workspace.active_id() != Some(owner.0) {
            continue;
        }
        let Ok(doc) = session.workspace.document(owner.0) else {
            continue;
        };
        let Ok(v) = serde_json::from_str::<Value>(doc.text()) else {
            continue;
        };
        if v["preview"]["grid"].as_bool() == Some(true) {
            for i in -10..=10 {
                let color = if i == 0 {
                    Color::srgb(0.4, 0.45, 0.5)
                } else {
                    Color::srgb(0.21, 0.24, 0.28)
                };
                lines.line(
                    Vec3::new(i as f32, 0., -10.),
                    Vec3::new(i as f32, 0., 10.),
                    color,
                );
                lines.line(
                    Vec3::new(-10., 0., i as f32),
                    Vec3::new(10., 0., i as f32),
                    color,
                );
            }
        }
        if v["preview"]["capsule"].as_bool() == Some(true)
            && let Some(pose) = state.pose
            && let Ok(pose) = poses.get(pose)
        {
            let c = &v["character"];
            let radius = number(c, "capsuleRadius");
            let height = number(c, "capsuleHeight");
            let center = pose.translation
                + Vec3::new(
                    number(c, "calibrateX"),
                    number(c, "calibrateY") + radius + height * 0.5,
                    number(c, "calibrateZ"),
                );
            let color = Color::srgb(0.3, 0.9, 0.7);
            for sign in [-1., 1.] {
                let y = height * 0.5 * sign;
                for i in 0..48 {
                    let a = i as f32 * std::f32::consts::TAU / 48.;
                    let b = (i + 1) as f32 * std::f32::consts::TAU / 48.;
                    lines.line(
                        center + Vec3::new(a.cos() * radius, y, a.sin() * radius),
                        center + Vec3::new(b.cos() * radius, y, b.sin() * radius),
                        color,
                    );
                }
            }
            for axis in [Vec3::X, Vec3::Z] {
                for sign in [-1., 1.] {
                    lines.line(
                        center + axis * radius * sign - Vec3::Y * height * 0.5,
                        center + axis * radius * sign + Vec3::Y * height * 0.5,
                        color,
                    );
                }
                for side in [-1., 1.] {
                    for i in 0..24 {
                        let a = i as f32 * std::f32::consts::PI / 24.;
                        let b = (i + 1) as f32 * std::f32::consts::PI / 24.;
                        lines.line(
                            center
                                + Vec3::Y * (height * 0.5 + a.sin() * radius) * side
                                + axis * a.cos() * radius,
                            center
                                + Vec3::Y * (height * 0.5 + b.sin() * radius) * side
                                + axis * b.cos() * radius,
                            color,
                        );
                    }
                }
            }
        }
        if v["preview"]["skeleton"].as_bool() == Some(true)
            && let Some(root) = state.world
        {
            for (entity, skin) in &skins {
                if !parents.iter_ancestors(entity).any(|p| p == root) {
                    continue;
                }
                for joint in &skin.joints {
                    if let Ok((_, gt)) = nodes.get(*joint)
                        && let Ok(parent) = parents.get(*joint)
                        && skin.joints.contains(&parent.parent())
                        && let Ok((_, p)) = nodes.get(parent.parent())
                    {
                        lines.line(
                            gt.translation(),
                            p.translation(),
                            Color::srgb(1., 0.75, 0.25),
                        );
                    }
                }
            }
        }
    }
}
