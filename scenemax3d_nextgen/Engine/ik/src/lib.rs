//! Shared SceneMax IK adapter for the runtime and native asset preview.
//! The positional solve uses the pinned Bevy community IK implementation.
use bevy::{mesh::skinning::SkinnedMesh, prelude::*};
use bevy_mod_inverse_kinematics::IkConstraint;
use serde_json::Value;
use std::collections::{HashMap, HashSet};

#[derive(Clone, Debug)]
pub struct Target {
    pub entity: Entity,
    pub joint: Option<String>,
}
#[derive(Component)]
pub struct Rig {
    pub definition: Value,
    pub targets: HashMap<String, Target>,
    pub status: Vec<String>,
    pub active: bool,
    pub hold: bool,
    previous: HashMap<Entity, (Transform, Transform)>,
    weights: HashMap<String, f32>,
    pole_handle: Option<Entity>,
}
impl Rig {
    /// Current unscaled activation, for script weight changes during a fade.
    pub fn runtime_weight(&self, layer_id: &str) -> f32 {
        let layer = self.definition["layers"].as_array().and_then(|layers| {
            layers.iter().find(|l| {
                ["id", "name"].iter().any(|key| {
                    l[key]
                        .as_str()
                        .is_some_and(|s| s.eq_ignore_ascii_case(layer_id))
                })
            })
        });
        let Some(layer) = layer else {
            return 0.;
        };
        if n(layer, "blendTime", 0.) > 0. {
            return self.weights.get(s(layer, "id")).copied().unwrap_or(0.);
        }
        if layer["enabled"].as_bool().unwrap_or(true) {
            n(layer, "runtimeWeight", 1.).clamp(0., 1.)
        } else {
            0.
        }
    }

    pub fn new(definition: Value) -> Self {
        Self {
            definition,
            targets: default(),
            status: vec![],
            active: true,
            hold: false,
            previous: default(),
            weights: default(),
            pole_handle: None,
        }
    }
}
#[derive(SystemSet, Debug, Clone, PartialEq, Eq, Hash)]
pub struct Solve;
pub struct IkPlugin;
impl Plugin for IkPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(PreUpdate, restore).add_systems(
            PostUpdate,
            (prepare_poles, solve)
                .chain()
                .in_set(Solve)
                .after(bevy::app::AnimationSystems)
                .before(bevy::transform::TransformSystems::Propagate),
        );
    }
}
// One reusable scratch transform per rig, owned by its hierarchy (never a game object).
fn prepare_poles(world: &mut World) {
    let owners = world
        .query::<(Entity, &Rig)>()
        .iter(world)
        .filter(|(_, r)| {
            r.pole_handle.is_none()
                && r.definition["layers"]
                    .as_array()
                    .is_some_and(|ls| ls.iter().any(|l| l.get("polePosition").is_some()))
        })
        .map(|(e, _)| e)
        .collect::<Vec<_>>();
    for owner in owners {
        let handle = world.spawn((Transform::default(), ChildOf(owner))).id();
        world.get_mut::<Rig>(owner).unwrap().pole_handle = Some(handle);
    }
}
fn restore(mut rigs: Query<&mut Rig>, mut transforms: Query<&mut Transform>) {
    for mut rig in &mut rigs {
        if rig.hold && !rig.active {
            continue;
        }
        for (e, (before, after)) in rig.previous.drain() {
            if let Ok(mut t) = transforms.get_mut(e)
                && *t == after
            {
                *t = before;
            }
        }
    }
}
pub fn detach(world: &mut World, entity: Entity) {
    if let Some(mut rig) = world.get_mut::<Rig>(entity) {
        let pole = rig.pole_handle.take();
        let old = std::mem::take(&mut rig.previous);
        if let Some(pole) = pole {
            world.despawn(pole);
        }
        for (e, (before, after)) in old {
            if let Some(mut t) = world.get_mut::<Transform>(e)
                && *t == after
            {
                *t = before;
            }
        }
    }
    if let Ok(mut e) = world.get_entity_mut(entity) {
        e.remove::<Rig>();
    }
}
fn under(mut entity: Entity, root: Entity, parents: &Query<&ChildOf>) -> bool {
    for _ in 0..256 {
        if entity == root {
            return true;
        }
        let Ok(p) = parents.get(entity) else {
            return false;
        };
        entity = p.parent();
    }
    false
}
fn joint(
    root: Entity,
    wanted: &str,
    names: &Query<(Entity, &Name)>,
    skins: &Query<(Entity, &SkinnedMesh)>,
    parents: &Query<&ChildOf>,
) -> Result<Entity, String> {
    let used: HashSet<Entity> = skins
        .iter()
        .filter(|(e, _)| under(*e, root, parents))
        .flat_map(|(_, s)| s.joints.iter().copied())
        .collect();
    let mut matches = names
        .iter()
        .filter(|(e, n)| {
            under(*e, root, parents)
                && (used.is_empty() || used.contains(e))
                && (n.as_str() == wanted
                    || n.as_str().ends_with(&format!("/{wanted}"))
                    || n.as_str().ends_with(&format!(".{wanted}")))
        })
        .map(|(e, _)| e);
    let first = matches
        .next()
        .ok_or_else(|| format!("Joint not found: {wanted}"))?;
    if matches.next().is_some() {
        return Err(format!("Ambiguous joint: {wanted}"));
    }
    Ok(first)
}
fn global(
    entity: Entity,
    parents: &Query<&ChildOf>,
    transforms: &Query<(&mut Transform, &mut GlobalTransform)>,
) -> Result<GlobalTransform, String> {
    let mut chain = vec![];
    let mut e = entity;
    for _ in 0..256 {
        let (t, _) = transforms.get(e).map_err(|_| "Missing transform")?;
        chain.push(*t);
        if let Ok(p) = parents.get(e) {
            e = p.parent();
        } else {
            break;
        }
    }
    Ok(chain
        .iter()
        .rev()
        .fold(GlobalTransform::IDENTITY, |g, t| g.mul_transform(*t)))
}
fn n(v: &Value, key: &str, default: f32) -> f32 {
    v[key]
        .as_f64()
        .filter(|n| n.is_finite())
        .map_or(default, |n| n as f32)
}
fn s<'a>(v: &'a Value, key: &str) -> &'a str {
    v[key].as_str().unwrap_or("")
}
fn vector(v: &Value, default: Vec3) -> Vec3 {
    v.as_array()
        .filter(|a| a.len() == 3)
        .map(|a| {
            Vec3::new(
                a[0].as_f64().unwrap_or(default.x as f64) as f32,
                a[1].as_f64().unwrap_or(default.y as f64) as f32,
                a[2].as_f64().unwrap_or(default.z as f64) as f32,
            )
        })
        .unwrap_or(default)
}
fn target(
    rig: &Rig,
    key: &str,
    names: &Query<(Entity, &Name)>,
    skins: &Query<(Entity, &SkinnedMesh)>,
    parents: &Query<&ChildOf>,
) -> Result<Entity, String> {
    let t = rig
        .targets
        .get(key)
        .ok_or_else(|| format!("Target not found: {key}"))?;
    if let Some(name) = &t.joint {
        joint(t.entity, name, names, skins, parents)
    } else {
        Ok(t.entity)
    }
}
fn solve(
    mut rigs: Query<(Entity, &mut Rig)>,
    parents: Query<&ChildOf>,
    names: Query<(Entity, &Name)>,
    skins: Query<(Entity, &SkinnedMesh)>,
    mut transforms: Query<(&mut Transform, &mut GlobalTransform)>,
    time: Res<Time>,
) {
    for (owner, mut rig) in &mut rigs {
        if !rig.active {
            continue;
        }
        rig.status.clear();
        let mut layers = rig.definition["layers"]
            .as_array()
            .cloned()
            .unwrap_or_default();
        layers.sort_by(|a, b| n(a, "priority", 0.).total_cmp(&n(b, "priority", 0.)));
        for layer in layers {
            let id = s(&layer, "id").to_string();
            let desired = if layer["enabled"].as_bool().unwrap_or(true) {
                n(&layer, "runtimeWeight", 1.).clamp(0., 1.)
            } else {
                0.
            };
            let blend = if rig.hold {
                0.
            } else {
                n(&layer, "blendTime", 0.).max(0.)
            };
            let weight =
                rig.weights
                    .entry(id.clone())
                    .or_insert(if blend > 0. { 0. } else { desired });
            *weight = if blend <= 0. {
                desired
            } else {
                let step = time.delta_secs() / blend;
                *weight + (desired - *weight).clamp(-step, step)
            };
            // Blend runtime activation first; authored influence scales the result.
            // Blending the product changes fade duration for partially weighted layers.
            let weight = *weight * n(&layer, "weight", 1.).clamp(0., 1.);
            if weight <= 0. {
                continue;
            }
            if let Err(error) = solve_layer(
                (owner, &layer, weight),
                &mut rig,
                &parents,
                &names,
                &skins,
                &mut transforms,
            ) {
                rig.status.push(format!("{id}: {error}"));
            }
        }
        if rig.hold {
            rig.active = false;
        }
    }
}
fn solve_layer(
    (owner, layer, weight): (Entity, &Value, f32),
    rig: &mut Rig,
    parents: &Query<&ChildOf>,
    names: &Query<(Entity, &Name)>,
    skins: &Query<(Entity, &SkinnedMesh)>,
    transforms: &mut Query<(&mut Transform, &mut GlobalTransform)>,
) -> Result<(), String> {
    let resolve = |name: &str| joint(owner, name, names, skins, parents);
    let endpoint = target(rig, s(layer, "target"), names, skins, parents)?;
    let target_world = global(endpoint, parents, transforms)?;
    let goal = target_world.transform_point(vector(&layer["positionOffset"], Vec3::ZERO));
    if !goal.is_finite() {
        return Err("Target position must be finite".into());
    }
    let kind = s(layer, "solverType");
    if !matches!(
        kind,
        "TwoBoneIK" | "ThreeBoneIK" | "FABRIK" | "FootIK" | "LookAtIK" | "AimIK"
    ) {
        return Err(format!("Unknown IK solver: {kind}"));
    }

    if matches!(kind, "LookAtIK" | "AimIK") {
        let affected = layer["affectedJoints"]
            .as_array()
            .cloned()
            .unwrap_or_default();
        let affected = if affected.is_empty() {
            vec![layer["chain"]["end"].clone()]
        } else {
            affected
        };
        for name in affected {
            let e = resolve(name.as_str().unwrap_or(""))?;
            let before = *transforms.get(e).map_err(|_| "Missing joint")?.0;
            let current = global(e, parents, transforms)?;
            let dir = (goal - current.translation()).normalize_or_zero();
            if dir == Vec3::ZERO {
                continue;
            }
            let axis = current.rotation() * vector(&layer["aimAxis"], Vec3::Z).normalize_or_zero();
            if axis == Vec3::ZERO || !axis.is_finite() {
                return Err("Aim axis must be nonzero".into());
            }
            let delta = Quat::from_rotation_arc(axis, dir);
            let angle = delta.angle_between(Quat::IDENTITY);
            let max = n(layer, "maxAngle", 90.).clamp(0., 180.).to_radians();
            let delta = Quat::IDENTITY.slerp(delta, if angle > max { max / angle } else { 1. });
            let parent = parents.get(e).map_err(|_| "Joint has no parent")?.parent();
            let parent_rotation = global(parent, parents, transforms)?.rotation();
            let after = Transform {
                rotation: before.rotation.slerp(
                    parent_rotation.inverse() * delta * current.rotation(),
                    weight,
                ),
                ..before
            };
            if !after.rotation.is_finite() {
                return Err("Cannot aim a zero-scale or invalid joint".into());
            }
            let (mut t, mut g) = transforms.get_mut(e).map_err(|_| "Missing joint")?;
            *t = after;
            *g = current;
            rig.previous
                .entry(e)
                .and_modify(|v| v.1 = after)
                .or_insert((before, after));
        }
        return Ok(());
    }
    let chain = &layer["chain"];
    let end = resolve(s(chain, "end"))?;
    let root = resolve(if kind == "FABRIK" {
        s(chain, "start")
    } else {
        s(chain, "root")
    })?;
    let mut reverse = vec![end];
    let mut e = end;
    while e != root && reverse.len() < 65 {
        e = parents
            .get(e)
            .map_err(|_| "Chain root is not an ancestor of end")?
            .parent();
        reverse.push(e);
    }
    if e != root || reverse.len() < 2 {
        return Err("Invalid IK chain".into());
    }
    if matches!(kind, "TwoBoneIK" | "ThreeBoneIK" | "FootIK") {
        let middle = resolve(s(chain, "middle"))?;
        if !reverse.contains(&middle) || middle == root || middle == end {
            return Err("Middle joint must be inside the chain".into());
        }
        if kind == "ThreeBoneIK" {
            let second = resolve(s(chain, "secondMiddle"))?;
            if !reverse.contains(&second)
                || second == middle
                || second == root
                || second == end
                || reverse.iter().position(|e| *e == second)
                    >= reverse.iter().position(|e| *e == middle)
            {
                return Err("Second middle joint must be inside the chain".into());
            }
        }
    }
    let parent = parents
        .get(root)
        .map_err(|_| "Chain root has no parent")?
        .parent();
    if under(endpoint, root, parents) || endpoint == parent {
        return Err("Target cannot belong to the solved chain".into());
    }
    let base = global(parent, parents, transforms)?;
    let inverse = base.affine().inverse();
    if !inverse.is_finite() {
        return Err("Cannot solve a zero-scale rig".into());
    }
    let authored_pole = layer
        .get("polePosition")
        .map(|v| {
            global(owner, parents, transforms)
                .map(|g| g.transform_point(vector(v, Vec3::splat(f32::NAN))))
        })
        .transpose()?;
    if authored_pole.is_some_and(|v| !v.is_finite()) {
        return Err("Pole position must be finite".into());
    }
    let pole_entity = if authored_pole.is_some() {
        rig.pole_handle
    } else if s(layer, "poleTarget").is_empty() {
        None
    } else {
        Some(target(rig, s(layer, "poleTarget"), names, skins, parents)?)
    };
    let before = reverse
        .iter()
        .map(|e| {
            transforms
                .get(*e)
                .map(|(t, _)| (*e, *t))
                .map_err(|_| "Missing joint".to_string())
        })
        .collect::<Result<Vec<_>, _>>()?;
    if before
        .iter()
        .any(|(_, t)| !t.scale.abs_diff_eq(Vec3::ONE, 0.0001))
    {
        return Err("IK joints require unit local scale; apply scaling to the model root".into());
    }
    if layer["allowStretch"].as_bool().unwrap_or(false) {
        let points = reverse
            .iter()
            .map(|e| {
                global(*e, parents, transforms).map(|g| inverse.transform_point3(g.translation()))
            })
            .collect::<Result<Vec<_>, _>>()?;
        let reach = points.windows(2).map(|p| p[0].distance(p[1])).sum::<f32>();
        let distance = points
            .last()
            .map_or(0., |p| p.distance(inverse.transform_point3(goal)));
        if reach > 1e-6 {
            let scale = (distance / reach).clamp(1., n(layer, "maxStretch", 1.05).clamp(1., 5.));
            for e in reverse.iter().take(reverse.len() - 1) {
                transforms
                    .get_mut(*e)
                    .map_err(|_| "Missing joint")?
                    .0
                    .translation *= scale;
            }
        }
    }
    // A perfectly straight limb has no bend plane. Seed a tiny bend before calling
    // the iterative solver; otherwise a reachable axial target can normalize zero.
    if reverse.len() >= 3 {
        let root_pos = global(root, parents, transforms)?.translation();
        let end_pos = global(end, parents, transforms)?.translation();
        let axis = end_pos - root_pos;
        if axis.length_squared() > 1e-10
            && axis.cross(goal - root_pos).length_squared() < 1e-8
            && root_pos.distance(goal) < axis.length() * 0.999
        {
            let hinge = root;
            let child = transforms
                .get(end)
                .map_err(|_| "Missing end")?
                .0
                .translation;
            if let Some(direction) = child.try_normalize() {
                let mut t = transforms.get_mut(hinge).map_err(|_| "Missing hinge")?.0;
                t.rotation *= Quat::from_axis_angle(direction.any_orthonormal_vector(), 0.05);
            }
        }
    }
    // Solve in the chain parent's coordinates so imported model/world scale is respected.
    for e in reverse.iter().chain(std::iter::once(&parent)) {
        let g = global(*e, parents, transforms)?;
        *transforms.get_mut(*e).map_err(|_| "Missing joint")?.1 =
            GlobalTransform::from(inverse * g.affine());
    }
    let old_target = *transforms.get(endpoint).map_err(|_| "Missing target")?.1;
    *transforms
        .get_mut(endpoint)
        .map_err(|_| "Missing target")?
        .1 = GlobalTransform::from_translation(inverse.transform_point3(goal));
    let mut old_pole = None;
    let pole = if let Some(e) = pole_entity {
        if reverse.contains(&e) || e == endpoint || e == parent {
            None
        } else {
            let g = global(e, parents, transforms)?;
            let p = inverse.transform_point3(authored_pole.unwrap_or(g.translation()));
            let root_pos = transforms
                .get(root)
                .map_err(|_| "Missing root")?
                .1
                .translation();
            let to_goal = inverse.transform_point3(goal) - root_pos;
            if to_goal.cross(p - root_pos).length_squared() < 1e-8 {
                None
            } else {
                old_pole = Some((e, *transforms.get(e).map_err(|_| "Missing pole")?.1));
                *transforms.get_mut(e).map_err(|_| "Missing pole")?.1 =
                    GlobalTransform::from_translation(p);
                Some(e)
            }
        }
    } else {
        None
    };
    let constraint = IkConstraint {
        chain_length: reverse.len() - 1,
        iterations: 1,
        target: endpoint,
        pole_target: pole,
        pole_angle: 0.,
        enabled: true,
    };
    let mut result = Ok(());
    for _ in 0..n(layer, "iterations", 8.).clamp(1., 64.) as usize {
        result = constraint
            .solve(end, parents, transforms)
            .map_err(|e| e.to_string());
        if result.is_err() {
            break;
        }
        let p = transforms
            .get(end)
            .map_err(|_| "Missing end")?
            .0
            .translation;
        let end_parent = parents.get(end).map_err(|_| "Missing end parent")?.parent();
        let solved = transforms
            .get(end_parent)
            .map_err(|_| "Missing end parent")?
            .1
            .transform_point(p);
        if solved.distance(inverse.transform_point3(goal))
            <= n(layer, "tolerance", 0.01).max(0.00001)
        {
            break;
        }
    }
    *transforms
        .get_mut(endpoint)
        .map_err(|_| "Missing target")?
        .1 = old_target;
    if let Some((e, g)) = old_pole {
        *transforms.get_mut(e).map_err(|_| "Missing pole")?.1 = g;
    }
    *transforms.get_mut(parent).map_err(|_| "Missing parent")?.1 = base;
    let valid = reverse.iter().all(|e| {
        transforms
            .get(*e)
            .is_ok_and(|(t, _)| t.rotation.is_finite())
    });
    for (e, old) in before {
        let mut after = *transforms.get(e).map_err(|_| "Missing joint")?.0;
        if !valid || result.is_err() {
            after = old;
        } else {
            after.rotation = old.rotation.slerp(after.rotation, weight);
            after.translation = old.translation.lerp(after.translation, weight);
        }
        *transforms.get_mut(e).map_err(|_| "Missing joint")?.0 = after;
        rig.previous
            .entry(e)
            .and_modify(|v| v.1 = after)
            .or_insert((old, after));
    }
    if valid && result.is_ok() && layer["matchTargetRotation"].as_bool().unwrap_or(false) {
        let p = parents.get(end).map_err(|_| "Missing end parent")?.parent();
        let parent_rotation = global(p, parents, transforms)?.rotation();
        let offset = vector(&layer["rotationOffset"], Vec3::ZERO) * std::f32::consts::PI / 180.;
        let desired = parent_rotation.inverse()
            * target_world.rotation()
            * Quat::from_euler(EulerRot::XYZ, offset.x, offset.y, offset.z);
        if !desired.is_finite() {
            return Err("Target orientation requires a nonzero finite scale".into());
        }
        let (mut t, _) = transforms.get_mut(end).map_err(|_| "Missing end")?;
        t.rotation = t.rotation.slerp(desired, weight);
        let after = *t;
        if let Some(v) = rig.previous.get_mut(&end) {
            v.1 = after;
        }
    }
    for e in reverse.iter().rev() {
        let g = global(*e, parents, transforms)?;
        *transforms.get_mut(*e).map_err(|_| "Missing joint")?.1 = g;
    }
    if !valid {
        return Err("Degenerate chain or target; adjust the pole/target".into());
    }
    result
}

#[cfg(test)]
mod tests;
