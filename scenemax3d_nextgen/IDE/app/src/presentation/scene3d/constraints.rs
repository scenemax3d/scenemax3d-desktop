//! Declarative scene attachments and IK preview. Never executes scene code nodes.
use super::*;
use bevy::mesh::skinning::SkinnedMesh;
use scenemax_ik::{Rig, Target};
use serde_json::json;
use std::collections::{HashMap, HashSet};

#[derive(Component)]
struct Attachment {
    anchor: Entity,
    home: Entity,
}
#[derive(Component, Default)]
pub(crate) struct Issues(Vec<String>);

fn reference(text: &str) -> (&str, Option<String>) {
    let text = text.trim();
    if let Some((object, joint)) = text.split_once(".\"") {
        return (
            object.trim(),
            Some(
                serde_json::from_str(&format!("\"{joint}"))
                    .unwrap_or_else(|_| joint.trim_end_matches('"').into()),
            ),
        );
    }
    if let Some((object, joint)) = text.split_once('.') {
        return (object, Some(joint.into()));
    }
    (text, None)
}
pub(super) fn global(world: &World, entity: Entity) -> Option<GlobalTransform> {
    let mut chain = Vec::new();
    let mut current = entity;
    for _ in 0..256 {
        chain.push(*world.get::<Transform>(current)?);
        if let Some(parent) = world.get::<ChildOf>(current) {
            current = parent.parent();
        } else {
            return Some(
                chain
                    .iter()
                    .rev()
                    .fold(GlobalTransform::IDENTITY, |g, t| g.mul_transform(*t)),
            );
        }
    }
    None
}
pub(super) fn joint(world: &World, root: Entity, wanted: &str) -> Result<Entity, String> {
    let mut pending = vec![root];
    let mut nodes = vec![];
    let mut skin = HashSet::new();
    while let Some(entity) = pending.pop() {
        if entity != root && world.get::<gizmo::SceneObject>(entity).is_some() {
            continue;
        }
        if let Some(mesh) = world.get::<SkinnedMesh>(entity) {
            skin.extend(mesh.joints.iter().copied());
        }
        if let Some(name) = world.get::<Name>(entity) {
            nodes.push((entity, name.to_string()));
        }
        if let Some(children) = world.get::<Children>(entity) {
            pending.extend(children.iter());
        }
    }
    let candidates = nodes
        .into_iter()
        .map(|(e, n)| (e, n, skin.contains(&e)))
        .collect::<Vec<_>>();
    super::super::weapon::attachment::resolve(wanted, &candidates)?
        .ok_or_else(|| "Empty attachment joint".into())
}
pub(super) fn target(
    world: &World,
    names: &HashMap<String, Vec<Entity>>,
    text: &str,
) -> Result<(Entity, Option<String>), String> {
    let (name, bone) = reference(text);
    let candidates = names
        .get(&name.to_ascii_lowercase())
        .ok_or_else(|| format!("Object not found: {name}"))?;
    if candidates.len() != 1 {
        return Err(format!("Ambiguous object: {name}"));
    }
    let root = candidates[0];
    if let Some(bone) = &bone {
        joint(world, root, bone)?;
    }
    Ok((root, bone))
}
pub(super) fn cycle(world: &World, mut parent: Entity, owner: Entity) -> bool {
    for _ in 0..256 {
        if parent == owner {
            return true;
        }
        let Some(next) = world.get::<ChildOf>(parent) else {
            return false;
        };
        parent = next.parent();
    }
    true
}
pub(crate) fn update(world: &mut World) {
    let Some(state) = world.get_resource::<SceneState>() else {
        return;
    };
    let Some(root) = state.world else {
        return;
    };
    let Some(scene) = &state.scene else {
        return;
    };
    let properties = scene
        .entities
        .iter()
        .map(|e| e.properties.clone())
        .collect::<Vec<_>>();
    let definitions = scene.ik_assets.clone();
    let objects = world
        .query::<(Entity, &gizmo::SceneObject, &Name)>()
        .iter(world)
        .filter(|(e, _, _)| cycle(world, *e, root))
        .map(|(e, o, n)| (e, o.0, n.to_string()))
        .collect::<Vec<_>>();
    let mut names: HashMap<String, Vec<Entity>> = HashMap::new();
    for (entity, _, name) in &objects {
        names
            .entry(name.to_ascii_lowercase())
            .or_default()
            .push(*entity);
    }
    // Resolve and parent first; hierarchy evaluation then handles any declaration order.
    for (entity, index, _) in &objects {
        let Some(p) = properties.get(*index) else {
            continue;
        };
        let mut errors = vec![];
        let reference = p["attachTo"].as_str().unwrap_or("").trim();
        let resolved = if reference.is_empty() {
            None
        } else {
            match target(world, &names, reference).and_then(|(root, bone)| {
                let parent = if let Some(bone) = bone {
                    joint(world, root, &bone)?
                } else {
                    root
                };
                if cycle(world, parent, *entity) {
                    return Err("Attachment cycle".into());
                }
                Ok(parent)
            }) {
                Ok(parent) => Some(parent),
                Err(e) => {
                    errors.push(format!("Attachment: {e}"));
                    None
                }
            }
        };
        if let Some(parent) = resolved {
            let anchor = if let Some(a) = world.get::<Attachment>(*entity) {
                a.anchor
            } else {
                let Some(home) = world.get::<ChildOf>(*entity).map(ChildOf::parent) else {
                    continue;
                };
                let anchor = world
                    .spawn((Transform::default(), Visibility::Inherited, ChildOf(parent)))
                    .id();
                world
                    .entity_mut(*entity)
                    .insert((Attachment { anchor, home }, ChildOf(anchor)));
                anchor
            };
            world.entity_mut(anchor).insert(ChildOf(parent));
        } else if let Some(a) = world.entity_mut(*entity).take::<Attachment>() {
            world.entity_mut(*entity).insert(ChildOf(a.home));
            world.despawn(a.anchor);
        }
        let id = p["ikAsset"].as_str().unwrap_or("");
        if id.is_empty() {
            scenemax_ik::detach(world, *entity);
        } else if let Some(source) = definitions.get(&id.to_ascii_lowercase()) {
            let mut definition = source.clone();
            for play in p["ikLayers"]
                .as_array()
                .into_iter()
                .flatten()
                .filter(|l| l["enabled"].as_bool().unwrap_or(false))
            {
                let id = play["layerId"].as_str().unwrap_or("");
                if !source["layers"].as_array().into_iter().flatten().any(|l| {
                    ["id", "name"]
                        .iter()
                        .any(|k| l[k].as_str().is_some_and(|v| v.eq_ignore_ascii_case(id)))
                }) {
                    errors.push(format!("IK layer not found: {id}"));
                }
            }

            for layer in definition["layers"].as_array_mut().into_iter().flatten() {
                layer["enabled"] = json!(false);
                if let Some(play) = p["ikLayers"].as_array().into_iter().flatten().find(|play| {
                    let id = play["layerId"].as_str().unwrap_or("");
                    ["id", "name"].iter().any(|key| {
                        layer[key]
                            .as_str()
                            .is_some_and(|v| v.eq_ignore_ascii_case(id))
                    })
                }) {
                    layer["enabled"] = json!(play["enabled"].as_bool().unwrap_or(false));
                    if let Some(t) = play["target"].as_str().filter(|t| !t.is_empty()) {
                        layer["target"] = json!(t);
                    }
                    for key in ["positionOffset", "rotationOffset", "polePosition"] {
                        if let Some(value) = play.get(key) {
                            layer[key] = value.clone();
                        }
                    }
                    if play.get("rotationOffset").is_some() {
                        layer["matchTargetRotation"] = json!(true);
                    }
                    layer["runtimeWeight"] = json!(play["weight"].as_f64().unwrap_or(1.));
                    layer["blendTime"] = json!(play["blend"].as_f64().unwrap_or(0.2));
                }
            }
            let mut targets = HashMap::new();
            for layer in definition["layers"].as_array().into_iter().flatten() {
                for key in ["target", "poleTarget"] {
                    let Some(text) = layer[key].as_str().filter(|s| !s.is_empty()) else {
                        continue;
                    };
                    if let Ok((entity, joint)) = target(world, &names, text) {
                        targets.insert(text.to_string(), Target { entity, joint });
                    }
                }
            }
            if let Some(mut rig) = world.get_mut::<Rig>(*entity) {
                rig.definition = definition;
                rig.targets = targets;
            } else {
                let mut rig = Rig::new(definition);
                rig.targets = targets;
                world.entity_mut(*entity).insert(rig);
            }
        } else {
            scenemax_ik::detach(world, *entity);
            errors.push(format!("IK asset missing, invalid or ambiguous: {id}"));
        }
        world.entity_mut(*entity).insert(Issues(errors));
    }
    // Match the runtime attachment wrapper: inherit position/rotation, cancel scale.
    let mut anchors = world
        .query::<&Attachment>()
        .iter(world)
        .map(|a| a.anchor)
        .collect::<Vec<_>>();
    anchors.sort_by_key(|e| {
        let mut depth = 0;
        let mut current = *e;
        while let Some(p) = world.get::<ChildOf>(current) {
            depth += 1;
            current = p.parent();
            if depth > 256 {
                break;
            }
        }
        depth
    });
    for anchor in anchors {
        let Some(parent) = world.get::<ChildOf>(anchor).map(ChildOf::parent) else {
            continue;
        };
        if let Some(g) = global(world, parent) {
            let scale = g.to_scale_rotation_translation().0;
            if scale.is_finite()
                && scale.abs().min_element() > 0.000001
                && let Some(mut t) = world.get_mut::<Transform>(anchor)
            {
                t.scale = scale.recip();
            }
        }
    }
}
pub(crate) fn diagnostics(
    objects: Query<(&Name, &Issues, Option<&Rig>)>,
    mut labels: Query<(&mut Text, &mut Node), With<AssetStatus>>,
    mut last: Local<String>,
) {
    let mut errors = vec![];
    let mut layers = 0;
    for (name, issues, rig) in &objects {
        errors.extend(issues.0.iter().map(|e| format!("{name}: {e}")));
        if let Some(rig) = rig {
            errors.extend(rig.status.iter().map(|e| format!("{name}: {e}")));
            layers += rig.definition["layers"]
                .as_array()
                .into_iter()
                .flatten()
                .filter(|l| l["enabled"].as_bool().unwrap_or(false))
                .count();
        }
    }
    let message = if !errors.is_empty() {
        errors.join(" · ")
    } else if layers > 0 {
        format!("IK preview active · {layers} layers")
    } else {
        String::new()
    };
    if *last != message {
        if !errors.is_empty() {
            warn!("Scene preview: {message}");
        }
        last.clone_from(&message);
    }
    if !message.is_empty() {
        for (mut text, mut node) in &mut labels {
            text.0 = message.clone();
            node.display = Display::Flex;
        }
    }
}

/// Exercise the same world-to-local rotation path as dragging the scene gizmo.
pub(crate) fn smoke_rotation(
    capture: Option<Res<crate::presentation::smoke::SmokeCapture>>,
    state: Res<SceneState>,
    mut objects: Query<(&gizmo::SceneObject, &mut Transform, &ChildOf)>,
    globals: Query<&GlobalTransform>,
    mut frame: Local<u32>,
    images: Option<Res<Assets<Image>>>,
) {
    if capture.is_none() || std::env::var_os("SCENEMAX_SMOKE_ROTATE_TARGET").is_none() {
        return;
    }
    *frame += 1;
    if (*frame).is_multiple_of(100)
        && let Some(images) = images
    {
        let bytes: u64 = images
            .iter()
            .map(|(_, image)| {
                let size = image.texture_descriptor.size;
                size.width as u64 * size.height as u64 * size.depth_or_array_layers as u64 * 4
            })
            .sum();
        eprintln!(
            "rotation audit frame={} world={:?} images={} bytes={}",
            *frame,
            state.world,
            images.len(),
            bytes
        );
    }
    if *frame < 60 {
        return;
    }
    for (object, mut local, parent) in &mut objects {
        if object.0 != state.selected {
            continue;
        }
        let Ok(parent) = globals.get(parent.parent()) else {
            continue;
        };
        let mut pose = parent.mul_transform(*local).compute_transform();
        let axis = [Vec3::X, Vec3::Y, Vec3::Z][(*frame as usize / 60) % 3];
        pose.rotation = Quat::from_axis_angle(axis, 0.05) * pose.rotation;
        *local = Transform::from_matrix(Mat4::from(parent.affine().inverse()) * pose.to_matrix());
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn app(source: &str) -> (App, Vec<Entity>) {
        let dir = tempfile::tempdir().unwrap();
        let scene = scenemax_ide_services::scene3d::load(dir.path(), source).unwrap();
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, TransformPlugin, scenemax_ik::IkPlugin))
            .add_systems(PostUpdate, update.before(scenemax_ik::Solve));
        let root = app.world_mut().spawn(Transform::default()).id();
        let objects = scene
            .entities
            .iter()
            .enumerate()
            .map(|(i, e)| {
                let t = Transform {
                    translation: Vec3::from_array(e.position),
                    rotation: Quat::from_array(e.rotation),
                    scale: Vec3::from_array(e.scale),
                };
                app.world_mut()
                    .spawn((
                        gizmo::SceneObject(i, t),
                        Name::new(e.name.clone()),
                        t,
                        ChildOf(root),
                    ))
                    .id()
            })
            .collect();
        app.insert_resource(SceneState {
            scene: Some(scene),
            world: Some(root),
            ..default()
        });
        (app, objects)
    }
    #[test]
    fn bone_attachment_cancels_scale_preserves_offsets_and_follows_current_pose() {
        let (mut app, e) = app(
            r#"{"entities":[{"type":"BOX","name":"subject","attachTo":"mount.\"socket.with spaces\"","position":[0.5,0,0],"scale":[2,2,2]},{"type":"BOX","name":"mount","scale":[3,3,3]}]}"#,
        );
        let bone = app
            .world_mut()
            .spawn((
                Name::new("socket.with spaces"),
                Transform::from_xyz(1., 2., 0.),
                ChildOf(e[1]),
            ))
            .id();
        let authored = *app.world().get::<Transform>(e[0]).unwrap();
        app.update();
        assert!(
            app.world()
                .get::<GlobalTransform>(e[0])
                .unwrap()
                .translation()
                .abs_diff_eq(Vec3::new(3.5, 6., 0.), 1e-4)
        );
        app.world_mut().get_mut::<Transform>(bone).unwrap().rotation =
            Quat::from_rotation_z(std::f32::consts::FRAC_PI_2);
        app.update();
        let pose = app.world().get::<GlobalTransform>(e[0]).unwrap();
        assert!(pose.translation().abs_diff_eq(Vec3::new(3., 6.5, 0.), 1e-4));
        assert!(
            pose.to_scale_rotation_translation()
                .0
                .abs_diff_eq(Vec3::splat(2.), 1e-4)
        );
        assert_eq!(*app.world().get::<Transform>(e[0]).unwrap(), authored);
        app.world_mut()
            .resource_mut::<SceneState>()
            .scene
            .as_mut()
            .unwrap()
            .entities[0]
            .properties["attachTo"] = json!("");
        app.update();
        assert!(app.world().get::<Attachment>(e[0]).is_none());
        assert_eq!(*app.world().get::<Transform>(e[0]).unwrap(), authored);
    }
    #[test]
    fn enabled_scene_layer_solves_and_disabling_restores_pose() {
        let (mut app, e) = app(
            r#"{"entities":[{"type":"BOX","name":"subject","ikAsset":"asset","ikLayers":[{"layerId":"reach","enabled":true,"target":"goal","weight":1,"blend":0}]},{"type":"SPHERE","name":"goal","position":[1,1,0]}]}"#,
        );
        let upper = app
            .world_mut()
            .spawn((Name::new("upper"), Transform::default(), ChildOf(e[0])))
            .id();
        let mid = app
            .world_mut()
            .spawn((
                Name::new("middle"),
                Transform::from_xyz(0., 1., 0.),
                ChildOf(upper),
            ))
            .id();
        let end = app
            .world_mut()
            .spawn((
                Name::new("tip"),
                Transform::from_xyz(0., 1., 0.),
                ChildOf(mid),
            ))
            .id();
        app.world_mut().resource_mut::<SceneState>().scene.as_mut().unwrap().ik_assets.insert("asset".into(),json!({"layers":[{"id":"reach","solverType":"TwoBoneIK","chain":{"root":"upper","middle":"middle","end":"tip"},"iterations":64}]}));
        app.update();
        assert!(app.world().get::<Rig>(e[0]).unwrap().status.is_empty());
        assert!(
            app.world()
                .get::<GlobalTransform>(end)
                .unwrap()
                .translation()
                .distance(Vec3::new(1., 1., 0.))
                < 0.05
        );
        app.world_mut()
            .resource_mut::<SceneState>()
            .scene
            .as_mut()
            .unwrap()
            .entities[0]
            .properties["ikLayers"][0]["enabled"] = json!(false);
        app.update();
        assert_eq!(
            app.world().get::<Transform>(upper).unwrap().rotation,
            Quat::IDENTITY
        );
    }
    #[test]
    fn missing_bones_and_cycles_report_errors_without_falling_back_to_model() {
        let (mut app, e) = app(
            r#"{"entities":[{"type":"BOX","name":"first","attachTo":"second.\"missing\""},{"type":"BOX","name":"second"}]}"#,
        );
        app.update();
        assert!(app.world().get::<Attachment>(e[0]).is_none());
        assert!(!app.world().get::<Issues>(e[0]).unwrap().0.is_empty());
        let mut state = app.world_mut().resource_mut::<SceneState>();
        let scene = state.scene.as_mut().unwrap();
        scene.entities[0].properties["attachTo"] = json!("second");
        scene.entities[1].properties["attachTo"] = json!("first");
        app.update();
        assert!(e[..2].iter().any(|e| {
            app.world()
                .get::<Issues>(*e)
                .unwrap()
                .0
                .iter()
                .any(|s| s.contains("cycle"))
        }));
    }
}
