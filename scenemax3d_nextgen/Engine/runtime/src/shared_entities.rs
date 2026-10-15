//! Shared scene entity retention. Authored names and runtime addresses stay separate.
use super::*;

pub(super) fn retained(name: &str, vars: &SceneMaxVars, pools: &SceneMaxObjectPools, scope: Option<&SceneMaxScopeFrame>) -> bool {
    let target = scenemax_runtime_vm_core::references::entity_name_with_aliases(name, vars, scope, &pools.aliases);
    pools.shared_entities.contains(&target)
}

pub(super) fn values(vars: &SceneMaxVars, names: &HashSet<String>, declarations: &HashSet<String>) -> SceneMaxVars {
    let keep = |name: &str| names.iter().any(|entity| name.starts_with(&format!("{entity}.data.")))
        || declarations.contains(name) && vars.2.get(name).and_then(|v| v.entity_identity()).is_some_and(|v| names.contains(&v.runtime_name));
    SceneMaxVars(vars.0.iter().filter(|(n,_)| keep(n)).map(|(n,v)| (n.clone(),*v)).collect(),
        vars.1.iter().filter(|(n,_)| keep(n)).map(|(n,v)| (n.clone(),v.clone())).collect(),
        vars.2.iter().filter(|(n,_)| keep(n)).map(|(n,v)| (n.clone(),v.clone())).collect())
}

pub(super) fn clear_scene(commands: &mut Commands, names: HashSet<String>) {
    commands.queue(move |world: &mut World| {
        let entities = world.query::<(Entity, &SceneMaxEntity)>().iter(world)
            .map(|(e,s)| (e, names.contains(&s.name))).collect::<HashMap<_,_>>();
        // A retained instance may sit beneath a non-retained scene root. Detach
        // before recursive despawn, retaining its world-space transform.
        for (&entity, &keep) in &entities {
            if !keep { continue; }
            let mut parent = world.get::<ChildOf>(entity).map(ChildOf::parent);
            let mut remove_parent = false;
            for _ in 0..256 {
                let Some(p) = parent else { break; };
                if let Some(keep_parent) = entities.get(&p) { remove_parent = !keep_parent; break; }
                parent = world.get::<ChildOf>(p).map(ChildOf::parent);
            }
            if remove_parent {
                let pose = world.get::<GlobalTransform>(entity).map(GlobalTransform::compute_transform);
                world.entity_mut(entity).remove::<ChildOf>();
                if let Some(pose) = pose { world.entity_mut(entity).insert(pose); }
            }
        }
        for (entity, keep) in entities { if !keep { let _ = world.despawn(entity); } }
    });
}

/// Attachment ownership can make an otherwise ordinary entity persistent.
/// Traverse both actual children and logical attachment bindings until stable.
pub(super) fn propagate_attachments(world: &mut World) {
    let Some(pools) = world.get_resource::<SceneMaxObjectPools>() else { return; };
    if pools.shared_entities.is_empty() {
        world.resource_mut::<SceneMaxObjectPools>().shared_lights.clear();
        return;
    }
    let mut shared = pools.shared_entities.clone();
    let entries = world.query::<(Entity, &SceneMaxEntity, Option<&crate::attachments::Binding>)>().iter(world)
        .map(|(e,s,b)| (e,s.name.clone(),b.filter(|b| b.audited).map(|b| crate::ik::split_target(&b.subject).0.to_owned()))).collect::<Vec<_>>();
    let live_names = entries.iter().map(|(_,name,_)| name.as_str()).collect::<HashSet<_>>();
    // Cinematic camera instances are definitions, not rendered scene entities.
    let cameras = world.get_resource::<SceneMaxCameraSystem>();
    shared.retain(|name| live_names.contains(name.as_str()) || cameras.is_some_and(|c| c.cinematic_vars.contains_key(name)));
    let scene_names = entries.iter().map(|(e,n,_)| (*e,n.clone())).collect::<HashMap<_,_>>();
    loop {
        let mut changed = false;
        for (entity, name, binding) in &entries {
            if shared.contains(name) { continue; }
            let mut inherited = binding.as_ref().is_some_and(|owner| shared.contains(owner));
            let mut parent = world.get::<ChildOf>(*entity).map(ChildOf::parent);
            for _ in 0..256 {
                let Some(p) = parent else { break; };
                if scene_names.get(&p).is_some_and(|n| shared.contains(n)) { inherited = true; break; }
                parent = world.get::<ChildOf>(p).map(ChildOf::parent);
            }
            if inherited { changed |= shared.insert(name.clone()); }
        }
        if !changed { break; }
    }
    let mut pools = world.resource_mut::<SceneMaxObjectPools>();
    pools.shared_lights.retain(|name| shared.contains(name));
    pools.shared_entities = shared;
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    fn object(world: &mut World, name: &str) -> Entity {
        world.spawn((SceneMaxEntity { name: name.into(), runtime_name: name.into() }, Transform::default(), GlobalTransform::from_translation(Vec3::new(4.,5.,6.)))).id()
    }
    #[test]
    fn scene_clear_preserves_shared_descendants_and_detaches_from_retired_roots() {
        let mut world = World::new();
        let old = object(&mut world, "old");
        let retained = object(&mut world, "kept");
        let child = object(&mut world, "child");
        world.entity_mut(retained).insert(ChildOf(old));
        world.entity_mut(child).insert(ChildOf(retained));
        world.init_resource::<SceneMaxObjectPools>();
        world.resource_mut::<SceneMaxObjectPools>().shared_entities.insert("kept".into());
        propagate_attachments(&mut world);
        assert!(world.resource::<SceneMaxObjectPools>().shared_entities.contains("child"));
        world.run_system_once(|mut commands: Commands, pools: Res<SceneMaxObjectPools>| clear_scene(&mut commands, pools.shared_entities.clone())).unwrap();
        assert!(world.get_entity(old).is_err());
        assert!(world.get_entity(retained).is_ok());
        assert!(world.get_entity(child).is_ok());
        assert!(world.get::<ChildOf>(retained).is_none());
        assert_eq!(world.get::<Transform>(retained).unwrap().translation, Vec3::new(4.,5.,6.));
        assert_eq!(world.get::<ChildOf>(child).unwrap().parent(), retained);
    }
    #[test]
    fn retained_values_keep_entity_references_and_data_without_stale_network_ids() {
        let mut vars = SceneMaxVars::default();
        let reference = scenemax_runtime_vm_core::references::Reference::entity("kept");
        scenemax_runtime_vm_core::references::assign("alias", reference.clone(), &mut vars, None, false);
        for name in ["kept.data.score", "other.data.score", "kept.network.id"] {
            scenemax_runtime_vm_core::typed::store(&mut vars, name, serde_json::json!(7));
        }
        scenemax_runtime_vm_core::references::assign("kept", reference.clone(), &mut vars, None, false);
        let kept = values(&vars, &HashSet::from(["kept".into()]), &HashSet::from(["kept".into()]));
        assert_eq!(kept.2["kept"], reference);
        assert!(!kept.2.contains_key("alias"));
        assert_eq!(kept.0["kept.data.score"], 7.);
        assert!(!kept.0.contains_key("other.data.score"));
        assert!(!kept.0.contains_key("kept.network.id"));
    }
}

/// Pools reachable through shared values remain usable; membership alone never
/// promotes their entities to shared scene ownership.
pub(super) fn pool_names(vars: &SceneMaxVars, pools: &SceneMaxObjectPools) -> HashSet<String> {
    fn visit(value: &scenemax_runtime_vm_core::references::Reference, names: &mut HashSet<String>, depth: usize) {
        if depth > 128 { return; }
        if let Some(pool) = value.pool_identity() { names.insert(pool.runtime_name); }
        if let Some(items) = value.elements() { for item in items { visit(&item, names, depth + 1); } }
        if let Some(items) = value.entries() { for item in items.values() { visit(item, names, depth + 1); } }
    }
    let mut names = HashSet::new();
    for value in vars.2.values() { visit(value, &mut names, 0); }
    let mut inspected = HashSet::new();
    loop {
        let pending = names.difference(&inspected).cloned().collect::<Vec<_>>();
        if pending.is_empty() { break; }
        for name in pending {
            inspected.insert(name.clone());
            if let Some(scope) = pools.pools.get(&name).and_then(|pool| pool.factory_scope.as_ref()) {
                for key in scope.references.keys().chain(scope.bindings.keys()) {
                    if let Some(value) = scenemax_runtime_vm_core::references::scope_value(key, scope).or_else(|| scope.references.get(key).cloned()) {
                        visit(&value, &mut names, 0);
                    }
                }
            }
        }
    }
    names
}

/// Preserve shared cinematic definitions while replacing scene camera controls.
pub(super) fn reset_cameras(program: &Program, cameras: &mut SceneMaxCameraSystem, names: &HashSet<String>) {
    let kept = cameras.cinematic_vars.iter().filter(|(name,_)| names.contains(*name))
        .map(|(name,value)| (name.clone(),value.clone())).collect::<HashMap<_,_>>();
    let rigs = kept.values().filter_map(|value| {
        let key = value.rig_id.to_ascii_lowercase();
        cameras.cinematic_rigs.get(&key).cloned().map(|rig| (key,rig))
    }).collect::<HashMap<_,_>>();
    apply_camera_systems(program, cameras);
    cameras.cinematic_vars.extend(kept);
    cameras.cinematic_rigs.extend(rigs);
}
