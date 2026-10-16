//! Generic object/bone attachments with the same scale-cancelled local pose as the designer.
use super::*;
use avian3d::prelude::{Position as AvianPosition, Rotation as AvianRotation};
use bevy::mesh::skinning::SkinnedMesh;
#[derive(Component, Clone)]
pub(super) struct Binding {
    pub(super) subject: String,
    local: Transform,
    diagnostic: Option<String>,
    pub(super) audited: bool,
    _lifetime: Option<multiplayer::ActionLease>,
    owner: Option<SceneMaxControllerKey>,
}

// Attachment nodes are separate from skeleton joints and model roots.
#[derive(Component, Clone)]
struct SourceSocket {
    owner: Entity,
    joint: String,
}

pub(super) fn release_object(world: &mut World, entity: Entity) {
    let Some(binding) = world.get::<Binding>(entity).cloned() else { return; };
    world.entity_mut(entity).remove::<Binding>().insert(binding.local);
    if let Some(mut position) = world.get_mut::<AvianPosition>(entity) {
        position.0 = binding.local.translation;
    }
    if let Some(mut rotation) = world.get_mut::<AvianRotation>(entity) {
        rotation.0 = binding.local.rotation;
    }
}

pub(super) fn prune(world: &mut World, cancelled: impl Fn(Option<&SceneMaxControllerKey>) -> bool) {
    let entities: Vec<_> = world.query::<(Entity, &Binding)>().iter(world)
        .filter(|(_, binding)| cancelled(binding.owner.as_ref())).map(|(entity, _)| entity).collect();
    for entity in entities {
        if world.get::<SourceSocket>(entity).is_some() { world.despawn(entity); }
        else { release_object(world, entity); }
    }
}

pub(super) fn enqueue_source(
    commands: &mut Commands,
    entity: Option<Entity>,
    source_joint: Option<String>,
    subject: String,
    offset: SceneMaxVec3,
    restore_destination: bool,
    owner: Option<SceneMaxControllerKey>,
    lifetime: Option<multiplayer::ActionLease>,
) {
    let Some(source_joint) = source_joint else {
        enqueue_with_restore(commands, entity, subject, offset, restore_destination, owner, lifetime);
        return;
    };
    commands.queue(move |world: &mut World| {
        let Some(source_owner) = entity.filter(|e| world.get_entity(*e).is_ok()) else { return; };
        let existing = socket(world, source_owner, &source_joint);
        let entity = existing.unwrap_or_else(|| world.spawn((
            SourceSocket { owner: source_owner, joint: source_joint },
            Transform::default(),
            ChildOf(source_owner),
        )).id());
        enqueue_with_restore(&mut world.commands(), Some(entity), subject, offset, restore_destination, owner, lifetime);
    });
}

fn socket(world: &World, owner: Entity, name: &str) -> Option<Entity> {
    world.get::<Children>(owner)?.iter().find(|e| world.get::<SourceSocket>(*e)
        .is_some_and(|s| names_match(&s.joint, name)))
}

pub(super) fn evaluated(
    attach: &AttachStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
    bounds: Option<&SceneMaxColliderBounds>,
) -> Option<AttachStatement> {
    let offset = match &attach.offset_value {
        Some(value) => evaluate_position_value_runtime(value, vars, scope, guards, transforms, bounds)?,
        None => vec3_from_scenemax(attach.offset),
    };
    if !offset.is_finite() { return None; }
    Some(AttachStatement {
        offset: SceneMaxVec3 { x:offset.x, y:offset.y, z:offset.z },
        offset_value: None,
        ..attach.clone()
    })
}

#[cfg(test)]
pub(super) fn enqueue(
    commands: &mut Commands,
    entity: Option<Entity>,
    subject: String,
    offset: SceneMaxVec3,
) {
    enqueue_with_restore(commands, entity, subject, offset, true, None, None);
}

fn enqueue_with_restore(
    commands: &mut Commands,
    entity: Option<Entity>,
    subject: String,
    offset: SceneMaxVec3,
    restore_destination: bool,
    owner: Option<SceneMaxControllerKey>,
    lifetime: Option<multiplayer::ActionLease>,
) {
    commands.queue(move |world: &mut World| {
        let Some(entity) = entity else {
            write_runtime_diagnostic_line("Attachment owner not found");
            return;
        };
        // Looking up a destination attachment node restores its ownership to
        // the source model, as a later explicit attach-to-joint command does.
        let (target, target_joint) = crate::ik::split_target(&subject);
        if let Some(target_joint) = target_joint.filter(|_| restore_destination) {
            let owner = world.query::<(Entity, &SceneMaxEntity)>().iter(world)
                .find_map(|(e, s)| (s.name == target || s.runtime_name == target).then_some(e));
            if let Some(socket) = owner.and_then(|owner| socket(world, owner, &target_joint)) {
                if socket != entity { world.despawn(socket); }
            }
        }
        let Some(mut local) = world
            .get::<Binding>(entity)
            .map(|b| b.local)
            .or_else(|| {
                world
                    .get::<SceneMaxVirtualCollider>(entity)
                    .map(|c| c.authored)
            })
            .or_else(|| world.get::<Transform>(entity).copied())
        else {
            return;
        };
        local.translation = vec3_from_scenemax(offset);
        world
            .entity_mut(entity)
            .remove::<SceneMaxVirtualCollider>()
            .insert(Binding {
                subject,
                local,
                diagnostic: None,
                audited: false,
                _lifetime: lifetime,
                owner,
            });
        if world.get::<AvianRigidBody>(entity).is_some() {
            world.entity_mut(entity).insert(AvianRigidBody::Kinematic);
        }
    });
}
fn under(world: &World, mut e: Entity, root: Entity) -> bool {
    for _ in 0..256 {
        if e == root {
            return true;
        }
        let Some(p) = world.get::<ChildOf>(e) else {
            return false;
        };
        e = p.parent();
    }
    false
}
fn global(world: &World, mut entity: Entity) -> Result<GlobalTransform, String> {
    let mut chain = vec![];
    for _ in 0..256 {
        chain.push(
            *world
                .get::<Transform>(entity)
                .ok_or("Missing attachment transform")?,
        );
        if let Some(p) = world.get::<ChildOf>(entity) {
            entity = p.parent();
        } else {
            return Ok(chain
                .iter()
                .rev()
                .fold(GlobalTransform::IDENTITY, |g, t| g.mul_transform(*t)));
        }
    }
    Err("Attachment hierarchy cycle".into())
}
fn joint(world: &World, root: Entity, name: &str) -> Result<Entity, String> {
    let mut pending = vec![root];
    let mut candidates = vec![];
    let mut used = std::collections::HashSet::new();
    while let Some(e) = pending.pop() {
        if e != root && world.get::<SceneMaxEntity>(e).is_some() {
            continue;
        }
        if let Some(s) = world.get::<SkinnedMesh>(e) {
            used.extend(s.joints.iter().copied());
        }
        if world
            .get::<Name>(e)
            .is_some_and(|n| names_match(n.as_str(), name))
        {
            candidates.push(e);
        }
        if let Some(children) = world.get::<Children>(e) {
            pending.extend(children.iter());
        }
    }
    if candidates.iter().any(|e| used.contains(e)) {
        candidates.retain(|e| used.contains(e));
    }
    match candidates.as_slice() {
        [e] => Ok(*e),
        [] => Err(format!("Attachment joint not found: {name}")),
        _ => Err(format!("Ambiguous attachment joint: {name}")),
    }
}

fn source_pose(world: &World, root: Entity, bone: Entity) -> Result<Transform, String> {
    let pose = global(world, bone)?;
    let mut pending = vec![root];
    let mut mesh_pending = false;
    while let Some(entity) = pending.pop() {
        if entity != root && world.get::<SceneMaxEntity>(entity).is_some() { continue; }
        if let Some(index) = world.get::<SkinnedMesh>(entity)
            .and_then(|skin| skin.joints.iter().position(|joint| *joint == bone))
        {
            if let Some(handle) = world.get::<Mesh3d>(entity) {
                if let Some(mesh) = world.get_resource::<Assets<Mesh>>().and_then(|meshes| meshes.get(&handle.0)) {
                    if mesh_uses_joint(mesh, index) { return Ok(pose.compute_transform()); }
                } else {
                    mesh_pending = true;
                }
            }
        }
        if let Some(children) = world.get::<Children>(entity) { pending.extend(children.iter()); }
    }
    if mesh_pending { return Err("Attachment skin geometry is still loading".into()); }
    // A joint without an associated skin geometry uses its model-space pose.
    Ok(Transform::from_matrix(Mat4::from(global(world, root)?.affine().inverse()) * pose.to_matrix()))
}
fn mesh_uses_joint(mesh: &Mesh, joint: usize) -> bool {
    use bevy::mesh::VertexAttributeValues;
    let Some(VertexAttributeValues::Uint16x4(indices)) = mesh.attribute(Mesh::ATTRIBUTE_JOINT_INDEX) else { return false; };
    let Some(VertexAttributeValues::Float32x4(weights)) = mesh.attribute(Mesh::ATTRIBUTE_JOINT_WEIGHT) else { return false; };
    indices.iter().zip(weights).any(|(indices, weights)| indices.iter().zip(weights)
        .any(|(index, weight)| usize::from(*index) == joint && weight.is_finite() && *weight != 0.))
}

fn apply(
    world: &mut World,
    entity: Entity,
    names: &HashMap<String, Vec<Entity>>,
    visiting: &mut std::collections::HashSet<Entity>,
    done: &mut HashMap<Entity, Result<(), String>>,
) -> Result<(), String> {
    if let Some(result) = done.get(&entity) {
        return result.clone();
    }
    let Some(binding) = world.get::<Binding>(entity).cloned() else {
        return Ok(());
    };
    if !visiting.insert(entity) {
        return Err("Attachment cycle".into());
    }
    let result = (|| {
        let (owner_name, bone) = crate::ik::split_target(&binding.subject);
        let owners = names
            .get(&owner_name.to_ascii_lowercase())
            .ok_or_else(|| format!("Attachment object not found: {owner_name}"))?;
        if owners.len() != 1 {
            return Err(format!("Ambiguous attachment object: {owner_name}"));
        }
        let owner = owners[0];
        let source = world.get::<SourceSocket>(entity).cloned();
        if source.as_ref().is_some_and(|s| s.owner == owner) {
            return Err("Attachment cycle".into());
        }
        if under(world, owner, entity) {
            return Err("Attachment cycle".into());
        }
        apply(world, owner, names, visiting, done)?;
        let anchor = if let Some(bone) = bone {
            if let Some(socket) = socket(world, owner, &bone) {
                apply(world, socket, names, visiting, done)?;
                socket
            } else {
                joint(world, owner, &bone)?
            }
        } else {
            owner
        };
        let anchor = global(world, anchor)?;
        // Cancel inherited scale, preserving authored offsets, rotation and object scale.
        let attached = if let Some(source) = source {
            apply(world, source.owner, names, visiting, done)?;
            let bone = joint(world, source.owner, &source.joint)?;
            // Animation refreshes the separate attachment node from the source
            // joint's geometry-space ancestry after it has been reparented.
            source_pose(world, source.owner, bone)?
        } else {
            binding.local
        };
        let pose = Transform {
            translation: anchor.translation() + anchor.rotation() * attached.translation,
            rotation: anchor.rotation() * attached.rotation,
            scale: attached.scale,
        };
        let local = if let Some(parent) = world.get::<ChildOf>(entity) {
            Transform::from_matrix(
                Mat4::from(global(world, parent.parent())?.affine().inverse()) * pose.to_matrix(),
            )
        } else {
            pose
        };
        if !local.translation.is_finite() || !local.rotation.is_finite() || !local.scale.is_finite()
        {
            return Err("Invalid attachment transform".into());
        }
        world.entity_mut(entity).insert(local);
        if let Some(mut p) = world.get_mut::<AvianPosition>(entity) {
            p.0 = pose.translation;
        }
        if let Some(mut r) = world.get_mut::<AvianRotation>(entity) {
            r.0 = pose.rotation;
        }
        if !binding.audited {
            if std::env::var_os("SCENEMAX_ATTACHMENT_AUDIT").is_some() {
                if let Some(name) = world.get::<SceneMaxEntity>(entity) {
                    write_runtime_diagnostic_line(format!(
                        "ATTACHMENT_BOUND {} subject={:?} pos={:?} scale={:?}",
                        name.name, binding.subject, pose.translation, pose.scale
                    ));
                }
            }
            if let Some(mut current) = world.get_mut::<Binding>(entity) { current.audited = true; }
        }
        Ok(())
    })();
    visiting.remove(&entity);
    done.insert(entity, result.clone());
    let message = result.as_ref().err().cloned();
    if world
        .get::<Binding>(entity)
        .is_some_and(|b| b.diagnostic != message)
    {
        if let Some(error) = &message {
            write_runtime_diagnostic_line(error);
        }
        if let Some(mut b) = world.get_mut::<Binding>(entity) {
            b.diagnostic = message;
        }
    }
    result
}
pub(super) fn update(world: &mut World) {
    let mut names: HashMap<String, Vec<Entity>> = HashMap::new();
    for (e, n) in world.query::<(Entity, &SceneMaxEntity)>().iter(world) {
        names
            .entry(n.name.to_ascii_lowercase())
            .or_default()
            .push(e);
        if !n.runtime_name.eq_ignore_ascii_case(&n.name) {
            names
                .entry(n.runtime_name.to_ascii_lowercase())
                .or_default()
                .push(e);
        }
    }
    let entities = world
        .query_filtered::<Entity, With<Binding>>()
        .iter(world)
        .collect::<Vec<_>>();
    let mut done = HashMap::new();
    let mut visiting = std::collections::HashSet::new();
    for e in entities {
        let _ = apply(world, e, &names, &mut visiting, &mut done);
    }
    crate::shared_entities::propagate_attachments(world);
}

// Explicit local rendering audit: captures a frame and exits only when requested.
pub(super) fn audit(
    mut commands: Commands,
    mut frame: Local<u32>,
    entities: Query<(&SceneMaxEntity, &Transform, Option<&scenemax_ik::Rig>, Option<&Binding>)>,
    mut exit: MessageWriter<AppExit>,
    cameras: Query<&Transform, (With<Camera3d>, Without<SceneMaxEntity>)>,
    playbacks: Query<(&SceneMaxEntity, Option<&SceneMaxEffekseerPlayback>, Option<&CurrentAnimation>)>,
    sprites: Query<(&SceneMaxEntity, &SceneMaxSprite, Option<&SceneMaxSpriteAnimation>, &Visibility)>,
    meshes: Res<Assets<Mesh>>,
    geometry: Query<(&SceneMaxEntity, &Mesh3d)>,
    physics: Query<(&SceneMaxEntity, &AvianCollider, Option<&avian3d::prelude::Position>, Option<&LinearVelocity>, &Transform)>,
) {
    let Ok(path) = std::env::var("SCENEMAX_ATTACHMENT_AUDIT") else {
        return;
    };
    *frame += 1;
    let limit = std::env::var("SCENEMAX_ATTACHMENT_AUDIT_FRAMES")
        .ok()
        .and_then(|s| s.parse::<u32>().ok())
        .unwrap_or(600)
        .max(60);
    if *frame == limit {
        for (name, handle) in &geometry {
            if let Some(VertexAttributeValues::Float32x3(points)) = meshes.get(&handle.0).and_then(|mesh| mesh.attribute(Mesh::ATTRIBUTE_POSITION)) {
                let (mut min, mut max) = (Vec3::splat(f32::INFINITY), Vec3::splat(f32::NEG_INFINITY));
                for point in points { let point = Vec3::from_array(*point); min=min.min(point); max=max.max(point); }
                write_runtime_diagnostic_line(format!("GEOMETRY_AUDIT {} min={:?} max={:?} vertices={}", name.name, min, max, points.len()));
            }
        }
        for (name, sheet, playback, visibility) in &sprites {
            let frame = meshes.get(&sheet.mesh).and_then(|mesh| mesh.attribute(Mesh::ATTRIBUTE_UV_0))
                .and_then(|uv| if let VertexAttributeValues::Float32x2(uv) = uv { uv.first() } else { None })
                .map(|uv| (uv[0] * sheet.cols as f32).round() as usize
                    + ((uv[1] * sheet.rows as f32).round() as usize).saturating_sub(1) * sheet.cols);
            write_runtime_diagnostic_line(format!("SPRITE_AUDIT {} frame={frame:?} frames={} active={} visible={}",
                name.name, sheet.frame_count, playback.is_some(), !matches!(visibility, Visibility::Hidden)));
        }
        for (name, effect, animation) in &playbacks {
            if let Some(p) = effect { write_runtime_diagnostic_line(format!("EFFECT_AUDIT {} loop={} speed={} inputs={:?}", name.name, p.looped, p.playback_speed, p.dynamic_inputs)); }
            if let Some(a) = animation { write_runtime_diagnostic_line(format!("ANIMATION_AUDIT {} clip={} loop={} elapsed={}", name.name, a.clip, a.looped, a.elapsed_seconds)); }
        }
        for (name, collider, position, velocity, transform) in &physics {
            let bounds = collider.shape_scaled().compute_local_aabb();
            write_runtime_diagnostic_line(format!("PHYSICS_AUDIT {} scale={:?} local_min={:?} local_max={:?} position={:?} velocity={:?}",
                name.name, collider.scale(), bounds.mins, bounds.maxs, position, velocity));
            for (other, _, other_position, _, _) in &physics {
                if other.name == name.name { continue; }
                if let Some(origin) = other_position {
                    let hit = collider.cast_ray(transform.translation, transform.rotation, origin.0 + Vec3::Y * 10.0, Vec3::NEG_Y, 20.0, false);
                    write_runtime_diagnostic_line(format!("PHYSICS_SUPPORT_AUDIT {} under={} ray_hit={:?}", name.name, other.name, hit));
                }
            }
        }
        for t in &cameras { write_runtime_diagnostic_line(format!("CAMERA_AUDIT pos={:?} rot={:?}", t.translation, t.rotation)); }
        for (name, t, rig, binding) in &entities {
            write_runtime_diagnostic_line(format!(
                "ATTACHMENT_AUDIT {} pos={:?} rot={:?} scale={:?} ik={:?}",
                name.name,
                t.translation,
                t.rotation,
                t.scale,
                rig.map(|r| &r.status)
            ));
            if let Some(binding) = binding {
                write_runtime_diagnostic_line(format!(
                    "ATTACHMENT_BINDING {} subject={:?} offset={:?} error={:?}",
                    name.name, binding.subject, binding.local.translation, binding.diagnostic
                ));
            }
        }
        commands
            .spawn(bevy::render::view::screenshot::Screenshot::primary_window())
            .observe(bevy::render::view::screenshot::save_to_disk(path));
    }
    if *frame > limit + 30 {
        exit.write(AppExit::Success);
    }
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    #[test]
    fn source_socket_retirement_is_owned_by_the_current_network_generation() {
        let mut world = World::new();
        let root = object(&mut world, "source", Transform::default());
        let key = |sequence| SceneMaxControllerKey::NetworkAction { entity: 10, slot: 64, sequence };
        for sequence in [1, 2] {
            enqueue_source(&mut world.commands(), Some(root), Some("socket".into()),
                "destination".into(), SceneMaxVec3 { x:0., y:0., z:0. }, true, Some(key(sequence)), None);
            world.flush();
        }
        prune(&mut world, |owner| owner == Some(&key(1)));
        assert!(socket(&world, root, "socket").is_some());
        prune(&mut world, |owner| owner == Some(&key(2)));
        assert!(socket(&world, root, "socket").is_none());
        assert!(world.get_entity(root).is_ok());
    }

    pub(crate) fn skin(world: &mut World, root: Entity, bone: Entity, weight: f32) -> Handle<Mesh> {
        use bevy::mesh::VertexAttributeValues;
        world.init_resource::<Assets<Mesh>>();
        let mut mesh = Mesh::new(bevy::mesh::PrimitiveTopology::TriangleList, default());
        mesh.insert_attribute(Mesh::ATTRIBUTE_JOINT_INDEX, VertexAttributeValues::Uint16x4(vec![[0,0,0,0]]));
        mesh.insert_attribute(Mesh::ATTRIBUTE_JOINT_WEIGHT, vec![[weight,0.,0.,0.]]);
        let handle = world.resource_mut::<Assets<Mesh>>().add(mesh);
        world.spawn((Mesh3d(handle.clone()), SkinnedMesh { joints: vec![bone], inverse_bindposes: default() }, ChildOf(root)));
        handle
    }

    #[test]
    fn source_pose_ignores_zero_influences_and_waits_for_missing_meshes() {
        let mut world = World::new();
        let root = object(&mut world, "source", Transform::from_xyz(7.,0.,0.));
        let bone = world.spawn((Transform::from_xyz(0.,2.,0.), ChildOf(root))).id();
        let handle = skin(&mut world, root, bone, 0.);
        assert_eq!(source_pose(&world, root, bone).unwrap().translation, Vec3::new(0.,2.,0.));
        world.resource_mut::<Assets<Mesh>>().get_mut(&handle).unwrap()
            .insert_attribute(Mesh::ATTRIBUTE_JOINT_WEIGHT, vec![[0.,0.,0.5,0.]]);
        assert_eq!(source_pose(&world, root, bone).unwrap().translation, Vec3::new(7.,2.,0.));
        world.resource_mut::<Assets<Mesh>>().remove(handle.id());
        assert!(source_pose(&world, root, bone).unwrap_err().contains("still loading"));
    }

    #[test]
    fn pooled_object_release_detaches_and_reacquire_does_not_restore_old_binding() {
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        world.init_resource::<DelayedActionQueue>();
        let parent = object(&mut world, "anchor", Transform::from_xyz(10., 0., 0.));
        let child = object(&mut world, "member", Transform::from_scale(Vec3::splat(0.5)));
        world.entity_mut(child).insert((Visibility::Inherited, AvianPosition(Vec3::ZERO), AvianRotation::default()));
        world.resource_mut::<SceneMaxObjectPools>().pools.insert("items".into(), ObjectPoolRuntime {
            members: HashSet::from(["member".into()]),
            in_use: HashSet::from(["member".into()]),
            ..default()
        });
        world.resource_mut::<SceneMaxObjectPools>().aliases.insert("selected".into(), "member".into());
        bind(&mut world, child, "anchor", Vec3::X);
        update(&mut world);
        assert_eq!(world.get::<Transform>(child).unwrap().translation, Vec3::new(11., 0., 0.));
        let program = scenemax_parser::parse_program("items.release selected").unwrap();
        crate::pool_lifecycle::tests::execute_batch(&mut world, program.statements);
        assert!(world.get::<Binding>(child).is_none());
        assert_eq!(world.get::<Transform>(child).unwrap().translation, Vec3::X);
        assert_eq!(world.get::<AvianPosition>(child).unwrap().0, Vec3::X);
        let program = scenemax_parser::parse_program("var selected = items.acquire").unwrap();
        crate::pool_lifecycle::tests::execute_batch(&mut world, program.statements);
        world.get_mut::<Transform>(parent).unwrap().translation.x = 20.;
        update(&mut world);
        assert_eq!(world.get::<Transform>(child).unwrap().translation, Vec3::X);
        assert_eq!(world.get::<Transform>(child).unwrap().scale, Vec3::splat(0.5));
        assert!(world.resource::<SceneMaxObjectPools>().pools["items"].in_use.contains("member"));
    }

    #[test]
    fn unskinned_source_socket_uses_model_space_and_owner_deletion_cleans_it_up() {
        let mut world = World::new();
        let source = object(&mut world, "source", Transform::from_xyz(7., 0., 0.));
        world.spawn((Name::new("socket"), Transform::from_xyz(0., 2., 0.), ChildOf(source)));
        object(&mut world, "destination", Transform::from_xyz(10., 0., 0.));
        enqueue_source(&mut world.commands(), Some(source), Some("socket".into()),
            "destination".into(), SceneMaxVec3 { x:0., y:0., z:0. }, true, None, None);
        world.flush();
        update(&mut world);
        let attachment = socket(&world, source, "socket").unwrap();
        assert_eq!(global(&world, attachment).unwrap().translation(), Vec3::new(10., 2., 0.));
        world.despawn(source);
        assert!(world.get_entity(attachment).is_err());
    }

    #[test]
    fn source_socket_self_attachment_does_not_modify_the_model() {
        let mut world = World::new();
        let pose = Transform::from_xyz(7., 0., 0.);
        let source = object(&mut world, "source", pose);
        world.spawn((Name::new("socket"), Transform::from_xyz(0., 2., 0.), ChildOf(source)));
        enqueue_source(&mut world.commands(), Some(source), Some("socket".into()),
            "source".into(), SceneMaxVec3 { x:0., y:0., z:0. }, true, None, None);
        world.flush();
        update(&mut world);
        let attachment = socket(&world, source, "socket").unwrap();
        assert!(world.get::<Binding>(attachment).unwrap().diagnostic.is_some());
        assert_eq!(*world.get::<Transform>(source).unwrap(), pose);
    }

    #[test]
    fn source_socket_moves_attached_objects_without_moving_the_skeleton() {
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        world.init_resource::<DelayedActionQueue>();
        let source_pose = Transform::from_xyz(2., 0., 0.);
        let source = object(&mut world, "source", source_pose);
        let bone = world.spawn((Name::new("socket"), Transform::from_xyz(0., 1., 0.), ChildOf(source))).id();
        skin(&mut world, source, bone, 1.);
        object(&mut world, "destination", Transform::from_xyz(10., 0., 0.).with_scale(Vec3::splat(4.)));
        let carried = object(&mut world, "carried", Transform::from_scale(Vec3::splat(0.5)));
        bind(&mut world, carried, "source.\"socket\"", Vec3::X);
        let actions = scenemax_parser::parse_program(
            "source.\"socket\".attach to destination: pos (9,0,0)"
        ).unwrap().statements;
        crate::pool_lifecycle::tests::execute_batch(&mut world, actions);
        update(&mut world);
        assert!(socket(&world, source, "socket").is_some());
        let lifetime = std::sync::Arc::downgrade(world.get::<Binding>(
            socket(&world, source, "socket").unwrap()).unwrap()._lifetime.as_ref().unwrap());
        assert_eq!(*world.get::<Transform>(source).unwrap(), source_pose);
        assert_eq!(world.get::<ChildOf>(bone).unwrap().parent(), source);
        assert_eq!(world.get::<Transform>(bone).unwrap().translation, Vec3::Y);
        // The animation update replaces the transient attach offset with the
        // joint/geometry ancestry, matching a reparented attachment node.
        assert_eq!(world.get::<Transform>(carried).unwrap().translation, Vec3::new(13., 1., 0.));
        assert_eq!(world.get::<Transform>(carried).unwrap().scale, Vec3::splat(0.5));
        world.get_mut::<Transform>(bone).unwrap().translation.y = 3.;
        update(&mut world);
        assert_eq!(world.get::<Transform>(carried).unwrap().translation, Vec3::new(13., 3., 0.));
        // Explicitly attaching to that source socket again restores its model
        // ownership, including objects already attached to the same socket.
        let other = object(&mut world, "other", Transform::default());
        bind(&mut world, other, "source.\"socket\"", Vec3::ZERO);
        update(&mut world);
        assert!(socket(&world, source, "socket").is_none());
        assert_eq!(lifetime.strong_count(), 0);
        assert_eq!(world.get::<Transform>(carried).unwrap().translation, Vec3::new(3., 3., 0.));
        assert_eq!(world.get::<Transform>(other).unwrap().translation, Vec3::new(2., 3., 0.));
    }

    fn object(world: &mut World, name: &str, transform: Transform) -> Entity {
        world
            .spawn((
                SceneMaxEntity {
                    name: name.into(),
                    runtime_name: format!("{name}@1"),
                },
                transform,
            ))
            .id()
    }
    fn bind(world: &mut World, e: Entity, subject: &str, offset: Vec3) {
        enqueue(
            &mut world.commands(),
            Some(e),
            subject.into(),
            SceneMaxVec3 {
                x: offset.x,
                y: offset.y,
                z: offset.z,
            },
        );
        world.flush();
    }
    #[test]
    fn shared_attachment_ownership_propagates_and_remains_after_detach() {
        let mut world = World::new();
        world.init_resource::<SceneMaxObjectPools>();
        object(&mut world, "persistent", Transform::default());
        let attached = object(&mut world, "attached", Transform::default());
        let descendant = object(&mut world, "descendant", Transform::default());
        let missing = object(&mut world, "missing", Transform::default());
        world.resource_mut::<SceneMaxObjectPools>().shared_entities.insert("persistent".into());
        bind(&mut world, attached, "persistent", Vec3::ZERO);
        bind(&mut world, descendant, "attached", Vec3::ZERO);
        bind(&mut world, missing, "persistent.unknown_joint", Vec3::ZERO);
        update(&mut world);
        let names = &world.resource::<SceneMaxObjectPools>().shared_entities;
        assert!(names.contains("attached") && names.contains("descendant"));
        assert!(!names.contains("missing"));
        release_object(&mut world, attached);
        update(&mut world);
        assert!(world.resource::<SceneMaxObjectPools>().shared_entities.contains("attached"));
    }

    #[test]
    fn runtime_offsets_evaluate_before_binding_and_do_not_follow_variable_changes() {
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        world.init_resource::<DelayedActionQueue>();
        object(&mut world, "anchor", Transform::from_xyz(10., 0., 0.));
        let child = object(&mut world, "child", Transform::default());
        let program = scenemax_parser::parse_program(
            "amount = 2\nchild.attach to anchor: pos (amount + 1,amount * 2,-amount)\namount = 99"
        ).unwrap();
        crate::pool_lifecycle::tests::execute_batch(&mut world, program.statements);
        update(&mut world);
        assert_eq!(world.get::<Transform>(child).unwrap().translation, Vec3::new(13., 4., -2.));
        assert_eq!(world.get::<Binding>(child).unwrap().local.translation, Vec3::new(3., 4., -2.));
        let invalid = scenemax_parser::parse_program("child.attach to anchor: pos (missing,0,0)").unwrap();
        crate::pool_lifecycle::tests::execute_batch(&mut world, invalid.statements);
        assert_eq!(world.get::<Binding>(child).unwrap().local.translation, Vec3::new(3., 4., -2.));
    }

    #[test]
    fn animated_attachment_matches_designer_scale_cancelled_pose_and_live_skin() {
        let mut world = World::new();
        let mount = object(
            &mut world,
            "mount",
            Transform::from_xyz(2., 3., 4.).with_scale(Vec3::splat(3.7)),
        );
        world.spawn((
            Name::new("socket"),
            Transform::from_xyz(100., 0., 0.),
            ChildOf(mount),
        ));
        let bone = world
            .spawn((
                Name::new("socket"),
                Transform::from_xyz(0., 1., 0.),
                ChildOf(mount),
            ))
            .id();
        world.spawn((
            SkinnedMesh {
                joints: vec![bone],
                inverse_bindposes: default(),
            },
            ChildOf(mount),
        ));
        let authored = Transform::from_xyz(0., 0., 0.)
            .with_rotation(Quat::from_rotation_x(-0.9))
            .with_scale(Vec3::splat(3.));
        let subject = object(&mut world, "subject", authored);
        let offset = Vec3::new(0.1, -0.6, 1.);
        bind(&mut world, subject, "mount.\"socket\"", offset);
        for angle in [0., 0.5, 1.] {
            world.get_mut::<Transform>(bone).unwrap().rotation = Quat::from_rotation_z(angle);
            update(&mut world);
            let anchor = global(&world, bone).unwrap();
            let designer = anchor
                .mul_transform(Transform::from_scale(
                    anchor.to_scale_rotation_translation().0.recip(),
                ))
                .mul_transform(Transform {
                    translation: offset,
                    ..authored
                })
                .compute_transform();
            let actual = world.get::<Transform>(subject).unwrap();
            assert!(actual.translation.abs_diff_eq(designer.translation, 1e-4));
            assert!(actual.rotation.abs_diff_eq(designer.rotation, 1e-4));
            assert!(actual.scale.abs_diff_eq(designer.scale, 1e-4));
        }
    }
    #[test]
    fn collider_contact_preserves_rotation_scale_and_nested_dependency_order() {
        let mut world = World::new();
        let collider_pose = Transform::from_xyz(1., 2., 3.)
            .with_rotation(Quat::from_rotation_y(0.8))
            .with_scale(Vec3::splat(0.3));
        let contact = object(&mut world, "contact", collider_pose);
        world.entity_mut(contact).insert(SceneMaxVirtualCollider {
            owner: "carrier".into(),
            bone: None,
            local_offset: Vec3::ZERO,
            fallback_offset: Vec3::ZERO,
            authored: collider_pose,
        });
        let carrier = object(
            &mut world,
            "carrier",
            Transform::from_scale(Vec3::splat(2.)),
        );
        let base = object(
            &mut world,
            "base",
            Transform::from_xyz(4., 0., 0.).with_scale(Vec3::splat(5.)),
        );
        bind(&mut world, contact, "carrier", Vec3::Y);
        bind(&mut world, carrier, "base", Vec3::X);
        update(&mut world);
        assert!(world.get::<SceneMaxVirtualCollider>(contact).is_none());
        let t = *world.get::<Transform>(contact).unwrap();
        assert!(t.translation.abs_diff_eq(Vec3::new(5., 1., 0.), 1e-5));
        assert_eq!(t.scale, collider_pose.scale);
        assert_eq!(t.rotation, collider_pose.rotation);
        assert!(
            t.transform_point(Vec3::X)
                .abs_diff_eq(t.translation + t.rotation * (Vec3::X * 0.3), 1e-5)
        );
        bind(&mut world, base, "contact", Vec3::ZERO);
        update(&mut world);
        assert!(world.get::<Binding>(base).unwrap().diagnostic.is_some());
        assert_eq!(*world.get::<Transform>(contact).unwrap(), t);
    }
    #[test]
    fn missing_joint_never_falls_back_to_model_origin() {
        let mut world = World::new();
        object(&mut world, "anchor", Transform::from_xyz(10., 0., 0.));
        let e = object(&mut world, "subject", Transform::from_xyz(1., 2., 3.));
        bind(&mut world, e, "anchor.\"unloaded socket\"", Vec3::ZERO);
        update(&mut world);
        assert_eq!(
            world.get::<Transform>(e).unwrap().translation,
            Vec3::new(1., 2., 3.)
        );
        assert!(
            world
                .get::<Binding>(e)
                .unwrap()
                .diagnostic
                .as_ref()
                .unwrap()
                .contains("joint not found")
        );
    }
}
