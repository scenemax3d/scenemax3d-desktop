//! Scene-local IK contacts and bend handles; shared assets and skin transforms remain untouched.
use super::*;
use serde_json::{Value, json};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Kind {
    Contact,
    Bend,
}
#[derive(Component)]
pub(crate) struct Proxy;
#[derive(Component, Clone, Copy)]
pub(crate) struct Handle {
    pub layer: usize,
    pub kind: Kind,
}
#[derive(Component)]
pub(crate) struct Hint;
#[derive(Resource, Default)]
pub(crate) struct State {
    owner: Option<Entity>,
    pub(crate) proxy: Option<Entity>,
    active: Option<Handle>,
    pub(crate) requested: Option<Handle>,
    pub(crate) reset: Option<usize>,
    pub(crate) exit: bool,
    visuals: Vec<Entity>,
    sphere: Option<bevy::asset::Handle<Mesh>>,
    line: Option<bevy::asset::Handle<Mesh>>,
    colors: Vec<bevy::asset::Handle<gizmo::GizmoMaterial>>,
    shown: Transform,
    gesture: Option<(DocumentId, DocumentRevision, u64)>,
    smoke_frame: u32,
}
impl State {
    pub(crate) fn is_bend(&self) -> bool {
        self.active.is_some_and(|h| h.kind == Kind::Bend)
    }
}
fn vec(v: &Value) -> Vec3 {
    Vec3::from_array(std::array::from_fn(|i| v[i].as_f64().unwrap_or(0.) as f32))
}
fn rotation(v: &Value) -> Quat {
    let a = vec(v).map(f32::to_radians);
    Quat::from_euler(EulerRot::XYZ, a.x, a.y, a.z)
}
fn goal(world: &World, rig: &scenemax_ik::Rig, layer: &Value) -> Option<GlobalTransform> {
    let reference = layer["target"].as_str()?;
    let target = rig.targets.get(reference)?;
    let entity = match &target.joint {
        Some(name) => constraints::joint(world, target.entity, name).ok()?,
        None => target.entity,
    };
    constraints::global(world, entity)
}
fn pose(world: &World, owner: Entity, layer: &Value, kind: Kind) -> Option<Transform> {
    if kind == Kind::Bend {
        let point = if let Some(p) = layer.get("polePosition") {
            constraints::global(world, owner)?.transform_point(vec(p))
        } else if let Some(target) = world
            .get::<scenemax_ik::Rig>(owner)?
            .targets
            .get(layer["poleTarget"].as_str().unwrap_or(""))
        {
            let entity = match &target.joint {
                Some(name) => constraints::joint(world, target.entity, name).ok()?,
                None => target.entity,
            };
            constraints::global(world, entity)?.translation()
        } else {
            let middle =
                constraints::joint(world, owner, layer["chain"]["middle"].as_str()?).ok()?;
            constraints::global(world, middle)?.translation()
        };
        return Some(Transform::from_translation(point));
    }
    let rig = world.get::<scenemax_ik::Rig>(owner)?;
    let target = goal(world, rig, layer)?;
    let orientation = if layer["matchTargetRotation"].as_bool().unwrap_or(false) {
        target.rotation() * rotation(&layer["rotationOffset"])
    } else {
        let end = constraints::joint(world, owner, layer["chain"]["end"].as_str()?).ok()?;
        constraints::global(world, end)?.rotation()
    };
    Some(
        Transform::from_translation(target.transform_point(vec(&layer["positionOffset"])))
            .with_rotation(orientation),
    )
}
fn resolved_layer(world: &World, owner: Entity, index: usize) -> Option<Value> {
    let state = world.get_resource::<SceneState>()?;
    let play = &state
        .scene
        .as_ref()?
        .entities
        .get(state.selected)?
        .properties["ikLayers"][index];
    let id = play["layerId"].as_str()?;
    world.get::<scenemax_ik::Rig>(owner)?.definition["layers"]
        .as_array()?
        .iter()
        .find(|l| {
            ["id", "name"]
                .iter()
                .any(|k| l[k].as_str().is_some_and(|s| s.eq_ignore_ascii_case(id)))
        })
        .cloned()
}
// Runs after the actual solver, using current local transforms rather than last-frame globals.
pub(crate) fn refresh(world: &mut World) {
    let Some(mut controls) = world.remove_resource::<State>() else {
        return;
    };
    refresh_inner(world, &mut controls);
    world.insert_resource(controls);
}
fn refresh_inner(world: &mut World, controls: &mut State) {
    let scene = world.resource::<SceneState>();
    let root = scene.world;
    let selected = scene.selected;
    let count = scene
        .scene
        .as_ref()
        .and_then(|s| s.entities.get(selected))
        .and_then(|e| e.properties["ikLayers"].as_array())
        .map_or(0, Vec::len);
    let owner = world
        .query::<(Entity, &gizmo::SceneObject, &scenemax_ik::Rig)>()
        .iter(world)
        .find(|(e, o, _)| {
            o.0 == selected && root.is_some_and(|root| constraints::cycle(world, *e, root))
        })
        .map(|(e, _, _)| e);
    if owner != controls.owner {
        for e in controls.visuals.drain(..) {
            world.despawn(e);
        }
        if let Some(e) = controls.proxy.take() {
            world.despawn(e);
        }
        controls.owner = owner;
        controls.active = None;
        controls.gesture = None;
    }
    let Some(owner) = owner else {
        controls.requested = None;
        for mut text in world
            .query_filtered::<&mut Text, With<Hint>>()
            .iter_mut(world)
        {
            if text.0 != "Select a model with IK to tune its joints" {
                text.0 = "Select a model with IK to tune its joints".into();
            }
        }
        return;
    };
    let Some(root) = root else {
        return;
    };
    if std::mem::take(&mut controls.exit) {
        controls.active = None;
        controls.gesture = None;
        if let Some(e) = controls.proxy.take() {
            world.despawn(e);
        }
    }
    if let Some(h) = controls.requested.take() {
        controls.active = Some(h);
        controls.gesture = None;
    }
    if !world.contains_resource::<Assets<gizmo::GizmoMaterial>>() {
        return;
    }
    if controls.sphere.is_none() {
        let mut meshes = world.resource_mut::<Assets<Mesh>>();
        controls.sphere = Some(meshes.add(Sphere::new(1.).mesh().uv(12, 8)));
        controls.line = Some(meshes.add(Cylinder::new(1., 1.)));
        let mut materials = world.resource_mut::<Assets<gizmo::GizmoMaterial>>();
        for color in [
            Color::srgb(0.2, 0.9, 1.),
            Color::srgb(1., 0.65, 0.15),
            Color::srgb(0.6, 0.7, 0.8),
        ] {
            controls.colors.push(materials.add(gizmo::GizmoMaterial {
                base_color: color.to_linear(),
                emissive: color.to_linear(),
            }));
        }
    }
    let camera = world
        .resource::<SceneState>()
        .camera
        .and_then(|e| constraints::global(world, e));
    let size_at = |p: Vec3| {
        camera
            .map_or(0.04, |c| c.translation().distance(p) * 0.007)
            .max(0.002)
    };
    let mut draws: Vec<(Transform, bool, usize, Option<Handle>)> = vec![];
    let mut hint =
        "IK joints: cyan = contact · amber = knee/elbow direction · click a joint to tune"
            .to_string();
    for index in 0..count {
        let Some(layer) = resolved_layer(world, owner, index) else {
            continue;
        };
        if !layer["enabled"].as_bool().unwrap_or(false) {
            continue;
        }
        let mut chain = vec![];
        if let Some(end) = layer["chain"]["end"]
            .as_str()
            .and_then(|n| constraints::joint(world, owner, n).ok())
        {
            let root_name = layer["chain"][if layer["solverType"] == "FABRIK" {
                "start"
            } else {
                "root"
            }]
            .as_str()
            .unwrap_or("");
            if let Ok(start) = constraints::joint(world, owner, root_name) {
                let mut node = end;
                for _ in 0..65 {
                    if let Some(g) = constraints::global(world, node) {
                        chain.push((node, g.translation()));
                    }
                    if node == start {
                        break;
                    }
                    let Some(parent) = world.get::<ChildOf>(node) else {
                        break;
                    };
                    node = parent.parent();
                }
                if chain.last().is_none_or(|(e, _)| *e != start) {
                    chain.clear();
                }
            }
        }
        let middle = layer["chain"]["middle"]
            .as_str()
            .and_then(|n| constraints::joint(world, owner, n).ok());
        for (i, (e, p)) in chain.iter().enumerate() {
            let kind = if i == 0 {
                Some(Kind::Contact)
            } else if Some(*e) == middle {
                Some(Kind::Bend)
            } else {
                None
            };
            let color = match kind {
                Some(Kind::Contact) => 0,
                Some(Kind::Bend) => 1,
                None => 2,
            };
            draws.push((
                Transform::from_translation(*p).with_scale(Vec3::splat(size_at(*p))),
                false,
                color,
                kind.map(|kind| Handle { layer: index, kind }),
            ));
        }
        for pair in chain.windows(2) {
            add_line(
                &mut draws,
                pair[0].1,
                pair[1].1,
                size_at(pair[0].1) * 0.17,
                2,
            );
        }
        if let Some(pole) = pose(world, owner, &layer, Kind::Bend) {
            draws.push((
                pole.with_scale(Vec3::splat(size_at(pole.translation) * 0.85)),
                false,
                1,
                Some(Handle {
                    layer: index,
                    kind: Kind::Bend,
                }),
            ));
            if let Some(p) = middle.and_then(|e| constraints::global(world, e)) {
                add_line(
                    &mut draws,
                    p.translation(),
                    pole.translation,
                    size_at(pole.translation) * 0.1,
                    1,
                );
            }
        }
        if let Some(h) = controls.active.filter(|h| h.layer == index) {
            if let Some(t) = pose(world, owner, &layer, h.kind) {
                let proxy = *controls
                    .proxy
                    .get_or_insert_with(|| world.spawn((Proxy, t, ChildOf(root))).id());
                world.entity_mut(proxy).insert(t);
                controls.shown = t;
                let id = layer["id"].as_str().unwrap_or("IK layer");
                hint = format!(
                    "{id} · {} · Ctrl+S saves scene pose · Ctrl+Z undoes drag",
                    if h.kind == Kind::Bend {
                        "Bend direction: Move handle (root stays fixed)"
                    } else {
                        "Contact: Move / Rotate handle"
                    }
                );
                if let Some((_, end)) = chain.first() {
                    let error = end.distance(t.translation);
                    if h.kind == Kind::Contact && error > size_at(*end) * 2. {
                        hint += " · Contact not reached: adjust distance, weight or chain";
                        add_line(&mut draws, *end, t.translation, size_at(*end) * 0.15, 1);
                    }
                }
            } else {
                if let Some(e) = controls.proxy.take() {
                    world.despawn(e);
                }
                hint =
                    "IK control unavailable: check enabled layer, chain joints and target".into();
            }
        }
    }
    if controls.active.is_some_and(|h| {
        resolved_layer(world, owner, h.layer)
            .is_none_or(|l| !l["enabled"].as_bool().unwrap_or(false))
    }) {
        controls.active = None;
        if let Some(e) = controls.proxy.take() {
            world.despawn(e);
        }
    }
    while controls.visuals.len() > draws.len() {
        if let Some(e) = controls.visuals.pop() {
            world.despawn(e);
        }
    }
    let (Some(line_mesh), Some(sphere_mesh)) = (controls.line.clone(), controls.sphere.clone())
    else {
        return;
    };
    for (i, (t, line, color, handle)) in draws.into_iter().enumerate() {
        if controls.visuals.len() <= i {
            controls.visuals.push(world.spawn_empty().id());
        }
        let mut e = world.entity_mut(controls.visuals[i]);
        e.insert((
            Mesh3d(if line {
                line_mesh.clone()
            } else {
                sphere_mesh.clone()
            }),
            MeshMaterial3d(controls.colors[color].clone()),
            t,
            Visibility::Visible,
            bevy::camera::visibility::RenderLayers::layer(1),
            ChildOf(root),
        ));
        if let Some(h) = handle {
            e.insert(h);
        } else {
            e.remove::<Handle>();
        }
    }
    for mut text in world
        .query_filtered::<&mut Text, With<Hint>>()
        .iter_mut(world)
    {
        if text.0 != hint {
            text.0.clone_from(&hint);
        }
    }
}
fn add_line(
    draws: &mut Vec<(Transform, bool, usize, Option<Handle>)>,
    a: Vec3,
    b: Vec3,
    width: f32,
    color: usize,
) {
    let delta = b - a;
    if let Some(direction) = delta.try_normalize() {
        draws.push((
            Transform::from_translation((a + b) * 0.5)
                .with_rotation(Quat::from_rotation_arc(Vec3::Y, direction))
                .with_scale(Vec3::new(width, delta.length(), width)),
            true,
            color,
            None,
        ));
    }
}

// Consume a gizmo change before the normal document property binding/save queue.
pub(crate) fn commit(world: &mut World) {
    let Some(mut controls) = world.remove_resource::<State>() else {
        return;
    };
    smoke(world, &mut controls);
    commit_inner(world, &mut controls);
    world.insert_resource(controls);
}
fn commit_inner(world: &mut World, controls: &mut State) {
    if world
        .resource::<ButtonInput<MouseButton>>()
        .just_pressed(MouseButton::Left)
    {
        controls.gesture = None;
    }
    let Some(owner) = controls.owner else {
        return;
    };
    let reset = controls.reset.take();
    let selected = world.resource::<SceneState>().selected;
    if world
        .get::<gizmo::SceneObject>(owner)
        .is_none_or(|o| o.0 != selected)
    {
        return;
    }
    let handle = reset
        .map(|layer| Handle {
            layer,
            kind: Kind::Contact,
        })
        .or(controls.active);
    let Some(handle) = handle else {
        return;
    };
    let Some(layer) = resolved_layer(world, owner, handle.layer) else {
        return;
    };
    let Some((id, revision)) = world.resource::<SceneState>().current else {
        return;
    };
    let scene = world.resource::<SceneState>();
    let Some(entry) = scene.scene.as_ref().and_then(|s| s.entities.get(selected)) else {
        return;
    };
    let pointer = entry.pointer.clone();
    let mut plays = entry.properties["ikLayers"].clone();
    let Some(play) = plays.get_mut(handle.layer) else {
        return;
    };
    if reset.is_some() {
        let Some(fields) = play.as_object_mut() else {
            return;
        };
        for key in ["positionOffset", "rotationOffset", "polePosition"] {
            fields.remove(key);
        }
    } else {
        let Some(t) = controls
            .proxy
            .and_then(|e| world.get::<Transform>(e))
            .copied()
        else {
            return;
        };
        if t.translation.abs_diff_eq(controls.shown.translation, 1e-5)
            && t.rotation.abs_diff_eq(controls.shown.rotation, 1e-5)
        {
            return;
        }
        if !t.translation.is_finite() || !t.rotation.is_finite() {
            return;
        }
        if handle.kind == Kind::Bend {
            let Some(owner_g) = constraints::global(world, owner) else {
                return;
            };
            let p = owner_g.affine().inverse().transform_point3(t.translation);
            if !p.is_finite() {
                return;
            }
            play["polePosition"] = json!(p.to_array());
        } else {
            let Some(rig) = world.get::<scenemax_ik::Rig>(owner) else {
                return;
            };
            let Some(target) = goal(world, rig, &layer) else {
                return;
            };
            if !t.translation.abs_diff_eq(controls.shown.translation, 1e-5) {
                let p = target.affine().inverse().transform_point3(t.translation);
                if !p.is_finite() {
                    return;
                }
                play["positionOffset"] = json!(p.to_array());
            }
            if !t.rotation.abs_diff_eq(controls.shown.rotation, 1e-5) {
                let q = target.rotation().inverse() * t.rotation;
                let (x, y, z) = q.to_euler(EulerRot::XYZ);
                play["rotationOffset"] = json!([x.to_degrees(), y.to_degrees(), z.to_degrees()]);
            }
        }
    }
    let mouse = world.resource::<ButtonInput<MouseButton>>();
    let first = mouse.just_pressed(MouseButton::Left) || reset.is_some();
    let mut session = world.resource_mut::<Session>();
    let Ok(doc) = session.workspace.document_mut(id) else {
        return;
    };
    if doc.revision() != revision {
        controls.gesture = None;
        return;
    }
    let saved = doc.saved_version();
    let Ok(source) = scenemax_ide_core::scene3d::patch(
        doc.text(),
        &pointer,
        &[("ikLayers".into(), plays.clone())],
    ) else {
        return;
    };
    if !first && controls.gesture == Some((id, revision, saved)) {
        doc.replace_text_continuing(source);
    } else {
        doc.replace_text(source);
    }
    let stamp = (id, doc.revision());
    controls.gesture = Some((id, stamp.1, saved));
    session.status =
        "IK pose updated · Ctrl+S saves and regenerates game code · Ctrl+Z to undo".into();
    let mut scene = world.resource_mut::<SceneState>();
    scene.current = Some(stamp);
    scene.completed = Some(stamp);
    if let Some(entry) = scene
        .scene
        .as_mut()
        .and_then(|s| s.entities.get_mut(selected))
    {
        entry.properties["ikLayers"] = plays;
    }
    world.write_message(crate::application::ViewChange::BufferChanged(id));
}

// Explicit capture-only audit, never active in a normal authoring session.
fn smoke(world: &mut World, controls: &mut State) {
    if !world.contains_resource::<crate::presentation::smoke::SmokeCapture>() {
        return;
    }
    let Ok(mode) = std::env::var("SCENEMAX_SMOKE_SCENE_IK") else {
        return;
    };
    controls.smoke_frame += 1;
    if controls.smoke_frame == 100 {
        controls.requested = Some(Handle {
            layer: 0,
            kind: if mode == "contact" {
                Kind::Contact
            } else {
                Kind::Bend
            },
        });
    }
    if mode == "exercise" && controls.smoke_frame > 150 {
        if controls.smoke_frame == 250 {
            controls.requested = Some(Handle {
                layer: 0,
                kind: Kind::Contact,
            });
        }
        let kind = controls.active.map(|h| h.kind);
        if let Some(e) = controls.proxy
            && let Some(mut t) = world.get_mut::<Transform>(e)
        {
            if kind == Some(Kind::Bend) {
                t.translation.z += 0.001;
            } else {
                t.rotation = Quat::from_rotation_y(0.002) * t.rotation;
            }
        }
    }
    if controls.smoke_frame.is_multiple_of(100) {
        let images = world.get_resource::<Assets<Image>>().map_or(0, Assets::len);
        eprintln!(
            "IK handle audit: frame={} entities={} images={} active={:?}",
            controls.smoke_frame,
            world.entities().len(),
            images,
            controls.active.map(|h| h.kind)
        );
    }
}

pub(super) fn controls(commands: &mut Commands, parent: Entity, layer: usize) {
    let row = commands
        .spawn((
            Node {
                flex_direction: FlexDirection::Row,
                flex_wrap: FlexWrap::Wrap,
                column_gap: px(5.),
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    for (caption, kind) in [
        ("Contact handle", Kind::Contact),
        ("Bend handle", Kind::Bend),
    ] {
        let e = button(commands, row, caption, Name::new(caption));
        commands
            .entity(e)
            .observe(move |_: On<Pointer<Click>>, mut state: ResMut<State>| {
                state.requested = Some(Handle { layer, kind });
            });
    }
    let exit = button(
        commands,
        row,
        "Model transform",
        Name::new("Model transform"),
    );
    commands
        .entity(exit)
        .observe(|_: On<Pointer<Click>>, mut state: ResMut<State>| {
            state.exit = true;
        });
    let e = button(commands, row, "Reset pose", Name::new("Reset IK pose"));
    commands
        .entity(e)
        .observe(move |_: On<Pointer<Click>>, mut state: ResMut<State>| {
            state.reset = Some(layer);
        });
}

#[cfg(test)]
mod tests;
