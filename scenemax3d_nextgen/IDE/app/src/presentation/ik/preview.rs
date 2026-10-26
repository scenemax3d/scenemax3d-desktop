//! Native rig/target preview using exactly the runtime IK adapter.
use super::*;
use crate::presentation::{
    scene3d::Orbit,
    weapon::{playback, preview::resource_transform},
};
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, primitives::Aabb, visibility::RenderLayers},
    render::render_resource::{Extent3d, TextureDimension, TextureFormat, TextureUsages},
    ui::widget::ViewportNode,
};
use scenemax_ik::{Rig, Target};
mod gizmo;
const LAYER: usize = 12;
#[derive(Component)]
struct PreviewCamera;
#[derive(Component)]
struct DebugBone;
#[derive(Default)]
pub(super) struct Preview {
    root: Option<Entity>,
    camera: Option<Entity>,
    actor: Option<Entity>,
    target: Option<Entity>,
    pole: Option<Entity>,
    gizmo: Option<Entity>,
    error: String,
    key: String,
    rig_key: String,
    bones_key: String,
    bound: bool,
    wait: u8,
    pending: Vec<(Entity, Handle<bevy::gltf::Gltf>, bool)>,
    graph: Option<Handle<AnimationGraph>>,
    clips: Vec<(
        String,
        bevy::animation::graph::AnimationNodeIndex,
        Handle<AnimationClip>,
    )>,
    adapted: bool,
    pub restart: bool,
    pub reset: bool,
    drag: Option<gizmo::Drag>,
    gizmo_mode: String,
    debug_mesh: Option<Handle<Mesh>>,
    debug_material: Option<Handle<StandardMaterial>>,
}
impl Preview {
    pub(super) fn first_clip(&self) -> Option<String> {
        self.clips.first().map(|v| v.0.clone())
    }
    pub fn clear(&mut self, c: &mut Commands) {
        if let Some(root) = self.root.take() {
            c.entity(root).try_despawn();
        }
        *self = default();
    }
}
type Cameras<'w, 's> = Query<
    'w,
    's,
    (
        &'static mut Orbit,
        &'static mut Transform,
        &'static GlobalTransform,
        &'static Projection,
    ),
    With<PreviewCamera>,
>;
type Nodes<'w, 's> = Query<
    'w,
    's,
    (
        Entity,
        &'static GlobalTransform,
        Option<&'static Aabb>,
        Option<&'static Name>,
    ),
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Render<'w, 's> {
    server: Option<Res<'w, AssetServer>>,
    paths: Option<Res<'w, crate::project_assets::ProjectAssets>>,
    images: Option<ResMut<'w, Assets<Image>>>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    gizmo_materials: Option<ResMut<'w, Assets<crate::presentation::scene3d::gizmo::GizmoMaterial>>>,
    gltfs: Option<Res<'w, Assets<bevy::gltf::Gltf>>>,
    graphs: Option<ResMut<'w, Assets<AnimationGraph>>>,
    animations: Option<ResMut<'w, Assets<AnimationClip>>>,
    parents: Query<'w, 's, &'static ChildOf>,
    nodes: Nodes<'w, 's>,
    skins: Query<'w, 's, (Entity, &'static bevy::mesh::skinning::SkinnedMesh)>,
    targets: Query<
        'w,
        's,
        (
            Entity,
            &'static bevy::animation::AnimationTargetId,
            &'static Name,
        ),
    >,
    players: Query<
        'w,
        's,
        (
            Entity,
            &'static mut AnimationPlayer,
            Option<&'static AnimationGraphHandle>,
        ),
    >,
    cameras: Cameras<'w, 's>,
    imported: Query<'w, 's, (Entity, &'static mut Camera), Without<PreviewCamera>>,
    rigs: Query<'w, 's, &'static Rig>,
    debug: Query<'w, 's, Entity, With<DebugBone>>,
    ports: Query<'w, 's, (&'static ComputedNode, &'static UiGlobalTransform)>,
    windows: Query<'w, 's, &'static Window, With<bevy::window::PrimaryWindow>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
}
fn vec(v: &Value, default: Vec3) -> Vec3 {
    let get = |i| v[i].as_f64().map(|n| n as f32);
    Vec3::new(
        get(0).unwrap_or(default.x),
        get(1).unwrap_or(default.y),
        get(2).unwrap_or(default.z),
    )
}
fn pose(data: &Value, target: bool) -> Transform {
    let prefix = if target {
        "previewTarget"
    } else {
        "previewModel"
    };
    let m = &data["designerMetadata"];
    let rotation = vec(&m[format!("{prefix}Rotation")], Vec3::ZERO) * std::f32::consts::PI / 180.;
    Transform {
        translation: vec(&m[format!("{prefix}Position")], Vec3::ZERO),
        rotation: Quat::from_euler(EulerRot::XYZ, rotation.x, rotation.y, rotation.z),
        scale: vec(&m[format!("{prefix}Scale")], Vec3::ONE).max(Vec3::splat(0.001)),
    }
}
pub(crate) fn update(
    mut c: Commands,
    mut s: ResMut<State>,
    mut r: Render,
    session: Res<Session>,
    mut queue: ResMut<CommandQueue>,
    mut prior: Local<Option<bevy::winit::UpdateMode>>,
) {
    if let Some(winit) = r.winit.as_mut() {
        if s.host.is_some() && (s.playing || s.running > 1 || !s.render.pending.is_empty()) {
            if prior.is_none() {
                *prior = Some(winit.focused_mode);
            }
            winit.focused_mode = bevy::winit::UpdateMode::Continuous;
        } else if let Some(mode) = prior.take() {
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
        Some(parts),
    ) = (
        r.server.as_ref(),
        r.paths.as_ref(),
        r.images.as_mut(),
        r.meshes.as_mut(),
        r.materials.as_mut(),
        r.gltfs.as_ref(),
        r.graphs.as_mut(),
        s.parts.as_ref(),
    )
    else {
        return;
    };
    let (viewport, animation_ui, bone_ui) = (parts.viewport, parts.animation, parts.target_bones);
    let key = format!(
        "{}|{}|{}",
        s.data["targetModelId"],
        s.data["designerMetadata"]["previewTargetModel"],
        s.models.len()
    );
    if s.render.root.is_none() || key != s.render.key || s.render.reset {
        let bound = s.render.bound;
        s.render.clear(&mut c);
        s.render.bound = bound;
        s.render.key = key;
        s.render.wait = 12;
        s.frame = true;
        s.bones.clear();
        s.target_bones.clear();
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
            target: Vec3::Y,
            distance: 5.,
            yaw: 0.45,
            pitch: 0.2,
        };
        let camera = c
            .spawn((
                PreviewCamera,
                Camera3d::default(),
                bevy::pbr::ScreenSpaceTransmission::default(),
                Camera {
                    order: -12,
                    clear_color: ClearColorConfig::Custom(Color::srgb(0.05, 0.06, 0.08)),
                    ..default()
                },
                RenderTarget::Image(images.add(image).into()),
                RenderLayers::layer(LAYER),
                AmbientLight {
                    brightness: 250.,
                    ..default()
                },
                orbit.transform(),
                orbit,
                ChildOf(root),
            ))
            .id();
        s.render.camera = Some(camera);
        c.entity(viewport).insert(ViewportNode::new(camera));
        if !s.render.bound {
            c.entity(viewport).observe(
                |mut e: On<PointerDrag>,
                 mut q: Query<(&mut Orbit, &mut Transform), With<PreviewCamera>>| {
                    if e.button == PointerButton::Secondary {
                        e.propagate(false);
                        for (mut o, mut t) in &mut q {
                            o.yaw -= e.delta.x * 0.006;
                            o.pitch = (o.pitch + e.delta.y * 0.006).clamp(-1.4, 1.4);
                            *t = o.transform();
                        }
                    }
                },
            );
            c.entity(viewport).observe(
                |mut e: On<PointerScroll>,
                 mut q: Query<(&mut Orbit, &mut Transform), With<PreviewCamera>>| {
                    e.propagate(false);
                    let delta = if e.unit == bevy::input::mouse::MouseScrollUnit::Line {
                        e.y * 0.12
                    } else {
                        e.y * 0.001
                    };
                    for (mut o, mut t) in &mut q {
                        o.distance = (o.distance * (1. + delta)).clamp(0.02, 100000.);
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
        s.render.pole = Some(
            c.spawn((
                Mesh3d(meshes.add(Sphere::new(0.06).mesh().uv(12, 8))),
                MeshMaterial3d(materials.add(StandardMaterial {
                    base_color: Color::srgb(0.65, 0.3, 1.),
                    unlit: true,
                    ..default()
                })),
                Transform::from_translation(vec(
                    &s.data["designerMetadata"]["previewPolePosition"],
                    Vec3::Z,
                )),
                RenderLayers::layer(LAYER),
                ChildOf(root),
            ))
            .id(),
        );
        for actor in [true, false] {
            let wrapper = c
                .spawn((pose(&s.data, !actor), Visibility::default(), ChildOf(root)))
                .id();
            if actor {
                s.render.actor = Some(wrapper);
            } else {
                s.render.target = Some(wrapper);
            }
            let name = if actor {
                s.data["targetModelId"].as_str()
            } else {
                s.data["designerMetadata"]["previewTargetModel"].as_str()
            }
            .unwrap_or("")
            .to_owned();
            if name.is_empty() {
                continue;
            }
            if !actor && matches!(name.as_str(), "Sphere" | "Box") {
                let mesh = if name == "Sphere" {
                    Sphere::new(0.12).mesh().uv(20, 16)
                } else {
                    Cuboid::new(0.2, 0.2, 0.2).mesh().build()
                };
                c.spawn((
                    Mesh3d(meshes.add(mesh)),
                    MeshMaterial3d(materials.add(StandardMaterial {
                        base_color: Color::srgb(1., 0.5, 0.08),
                        ..default()
                    })),
                    RenderLayers::layer(LAYER),
                    ChildOf(wrapper),
                ));
                continue;
            }
            if let Some(asset) = s.models.iter().find(|a| a.name == name)
                && let Some(file) = asset.file.as_ref().filter(|f| {
                    f.extension().is_some_and(|e| {
                        e.eq_ignore_ascii_case("glb") || e.eq_ignore_ascii_case("gltf")
                    })
                })
                && let Some(path) = paths.asset(&asset.root, file)
            {
                let object = c
                    .spawn((
                        resource_transform(&asset.data),
                        Visibility::default(),
                        ChildOf(wrapper),
                    ))
                    .id();
                s.render.pending.push((object, server.load(path), actor));
            } else {
                s.render.error = format!("{name}: select a registered glTF/GLB model");
            }
        }
        c.entity(animation_ui).despawn_children();
        s.animation.clear();
        s.playing = false;
    }
    let (Some(root), Some(actor), Some(target)) = (s.render.root, s.render.actor, s.render.target)
    else {
        return;
    };
    c.entity(actor).insert(pose(&s.data, false));
    c.entity(target).insert(pose(&s.data, true));
    let pending = std::mem::take(&mut s.render.pending);
    for (object, handle, is_actor) in pending {
        if let Some(gltf) = gltfs.get(&handle) {
            if let Some(scene) = gltf.default_scene.as_ref().or_else(|| gltf.scenes.first()) {
                c.entity(object).insert(WorldAssetRoot(scene.clone()));
            }
            if is_actor {
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
                scenemax_ide_ui::property::dropdown(
                    &mut c,
                    animation_ui,
                    Control("animation"),
                    "",
                    &choices,
                );
            }
        } else if let Some(bevy::asset::LoadState::Failed(e)) = server.get_load_state(handle.id()) {
            s.render.error = format!("Model failed: {e}");
        } else {
            s.render.pending.push((object, handle, is_actor));
        }
    }
    let under = |e, owner| r.parents.iter_ancestors(e).any(|p| p == owner);
    let joints: std::collections::HashSet<_> = r
        .skins
        .iter()
        .filter(|(e, _)| under(*e, actor))
        .flat_map(|(_, s)| s.joints.iter().copied())
        .collect();
    let target_joints: std::collections::HashSet<_> = r
        .skins
        .iter()
        .filter(|(e, _)| under(*e, target))
        .flat_map(|(_, s)| s.joints.iter().copied())
        .collect();
    let mut bones = vec![];
    let mut target_bones = vec![];
    let mut bounds = (Vec3::splat(f32::INFINITY), Vec3::splat(f32::NEG_INFINITY));
    for (e, gt, aabb, name) in &r.nodes {
        if !under(e, root) {
            continue;
        }
        c.entity(e).insert(RenderLayers::layer(LAYER));
        if let Some(name) = name {
            if joints.contains(&e) {
                bones.push(name.to_string());
            }
            if target_joints.contains(&e) {
                target_bones.push(name.to_string());
            }
        }
        if let Some(aabb) = aabb
            && !r.debug.contains(e)
            && !s.render.gizmo.is_some_and(|g| under(e, g))
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
    for (e, mut camera) in &mut r.imported {
        if under(e, root) {
            camera.is_active = false;
        }
    }
    bones.sort();
    bones.dedup();
    target_bones.sort();
    target_bones.dedup();
    if bones != s.bones {
        s.bones = bones;
        s.redraw = true;
    }
    let selected_target = s.data["layers"][s.layer]["target"].as_str().unwrap_or("");
    let selected_bone = selected_target
        .split_once(".\"")
        .and_then(|(_, b)| serde_json::from_str::<String>(&format!("\"{b}")).ok())
        .or_else(|| selected_target.split_once('.').map(|(_, b)| b.to_string()))
        .unwrap_or_default();
    let bone_key = format!("{:?}|{}|{}", target_bones, selected_bone, s.layer);
    if bone_key != s.render.bones_key {
        s.render.bones_key = bone_key;
        s.target_bones = target_bones;
        s.target_bone_selection = selected_bone.clone();
        c.entity(bone_ui).despawn_children();
        c.spawn((
            scenemax_ide_ui::label("Target bone (or object origin)", 12.),
            ChildOf(bone_ui),
        ));
        scenemax_ide_ui::property::dropdown(
            &mut c,
            bone_ui,
            Control("target_bone"),
            &selected_bone,
            &std::iter::once(String::new())
                .chain(s.target_bones.iter().cloned())
                .collect::<Vec<_>>(),
        );
    }
    if s.render.pending.is_empty() && bounds.0.is_finite() && s.render.wait > 0 {
        s.render.wait -= 1;
    }
    if s.frame
        && s.render.wait == 0
        && bounds.0.is_finite()
        && let Some(camera) = s.render.camera
        && let Ok((mut o, mut t, _, _)) = r.cameras.get_mut(camera)
    {
        o.target = (bounds.0 + bounds.1) * 0.5;
        o.distance = (bounds.1 - bounds.0).length().max(0.2) * 1.5;
        *t = o.transform();
        s.frame = false;
    }
    if !s.render.adapted
        && !joints.is_empty()
        && !s.render.clips.is_empty()
        && let Some(animations) = r.animations.as_mut()
    {
        let targets = r
            .targets
            .iter()
            .filter(|(e, _, _)| under(*e, actor))
            .map(|(e, id, name)| (*id, name.to_string(), joints.contains(&e)))
            .collect::<Vec<_>>();
        if targets.iter().any(|(_, _, used)| *used)
            && s.render
                .clips
                .iter()
                .all(|(_, _, h)| animations.get(h).is_some())
        {
            let prepared = s
                .render
                .clips
                .iter()
                .filter_map(|(name, _, h)| {
                    animations
                        .get(h)
                        .map(|clip| (name.clone(), playback::adapt(clip, &targets)))
                })
                .collect::<Vec<_>>();
            let handles = prepared
                .into_iter()
                .map(|(n, clip)| (n, animations.add(clip)))
                .collect::<Vec<_>>();
            let (graph, nodes) = AnimationGraph::from_clips(handles.iter().map(|(_, h)| h.clone()));
            s.render.graph = Some(graphs.add(graph));
            s.render.clips = handles
                .into_iter()
                .zip(nodes)
                .map(|((n, h), i)| (n, i, h))
                .collect();
            s.render.adapted = true;
            s.render.restart = true;
        }
    }
    if let Some(graph) = s.render.graph.clone() {
        let selected = s.render.clips.iter().find(|c| c.0 == s.animation).cloned();
        let mut bound = false;
        for (e, mut player, old) in &mut r.players {
            if !under(e, actor) {
                continue;
            }
            bound = true;
            let changed = old.is_none_or(|h| h.0 != graph);
            if changed {
                c.entity(e).insert(AnimationGraphHandle(graph.clone()));
            }
            if changed
                || s.render.restart
                || selected
                    .as_ref()
                    .is_some_and(|(_, n, _)| s.playing && player.animation(*n).is_none())
            {
                player.stop_all();
                if let Some((_, n, _)) = &selected {
                    player.play(*n).repeat();
                }
            }
            for (_, anim) in player.playing_animations_mut() {
                if s.playing {
                    anim.resume();
                } else {
                    anim.pause();
                }
            }
        }
        if bound {
            s.render.restart = false;
        }
    }
    if let Some(pole) = s.render.pole {
        c.entity(pole).insert(Transform::from_translation(vec(
            &s.data["designerMetadata"]["previewPolePosition"],
            Vec3::Z,
        )));
    }
    let rig_key = format!(
        "{}|{}|{}|{}",
        s.data["layers"], s.layer, s.running, s.request
    );
    if rig_key != s.render.rig_key && !joints.is_empty() {
        s.render.rig_key = rig_key;
        c.queue(move |world: &mut World| scenemax_ik::detach(world, actor));
        if s.running > 0 {
            let mut data = s.data.clone();
            if s.running != 3 {
                data["layers"] = json!([s.data["layers"][s.layer].clone()]);
            }
            let mut rig = Rig::new(data);
            rig.hold = s.running == 1;
            for layer in rig.definition["layers"].as_array().into_iter().flatten() {
                let key = layer["target"].as_str().unwrap_or("").to_string();
                let bone = key
                    .split_once(".\"")
                    .and_then(|(_, b)| serde_json::from_str::<String>(&format!("\"{b}")).ok())
                    .or_else(|| key.split_once('.').map(|(_, b)| b.to_string()));
                rig.targets.insert(
                    key,
                    Target {
                        entity: target,
                        joint: bone,
                    },
                );
                if let Some(pole) = s.render.pole
                    && let Some(key) = layer["poleTarget"].as_str().filter(|s| !s.is_empty())
                {
                    rig.targets.insert(
                        key.into(),
                        Target {
                            entity: pole,
                            joint: None,
                        },
                    );
                }
            }
            c.entity(actor).insert(rig);
        }
    }
    s.preview_status = if !s.render.error.is_empty() {
        s.render.error.clone()
    } else if !s.render.pending.is_empty() {
        "Loading model…".into()
    } else if joints.is_empty() {
        "Select a model with a skinned skeleton.".into()
    } else if let Ok(rig) = r.rigs.get(actor) {
        if rig.status.is_empty() {
            format!(
                "{} bones · {} · {}",
                joints.len(),
                if s.running == 1 {
                    "Pose solved"
                } else {
                    "IK playing"
                },
                s.animation
            )
        } else {
            rig.status.join(" · ")
        }
    } else {
        format!("{} bones · IK stopped · {}", joints.len(), s.animation)
    };
    // Highlight chain joints with small colored markers; updated from the actual solved pose.
    for e in &r.debug {
        c.entity(e).try_despawn();
    }
    let selected = &s.data["layers"][s.layer];
    let marked = selected["chain"]
        .as_object()
        .into_iter()
        .flat_map(|o| o.values())
        .filter_map(Value::as_str)
        .map(str::to_string)
        .collect::<Vec<_>>();
    if !marked.is_empty() {
        let mesh = s
            .render
            .debug_mesh
            .get_or_insert_with(|| meshes.add(Sphere::new(0.025).mesh().uv(8, 6)))
            .clone();
        let material = s
            .render
            .debug_material
            .get_or_insert_with(|| {
                materials.add(StandardMaterial {
                    base_color: Color::srgb(0.2, 1., 0.5),
                    unlit: true,
                    ..default()
                })
            })
            .clone();
        for (e, gt, _, name) in &r.nodes {
            if joints.contains(&e) && name.is_some_and(|n| marked.iter().any(|v| v == n.as_str())) {
                c.spawn((
                    DebugBone,
                    Mesh3d(mesh.clone()),
                    MeshMaterial3d(material.clone()),
                    Transform::from_translation(gt.translation()),
                    RenderLayers::layer(LAYER),
                    ChildOf(root),
                ));
            }
        }
    }
    gizmo::update(&mut c, &mut s, &mut r, &session, &mut queue, viewport);
}
