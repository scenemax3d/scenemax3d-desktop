//! Connection-owned state is discarded without touching authored local entities.
use super::*;

pub(super) fn cleanup_disconnected(world: &mut World, net: &mut Multiplayer) {
    if !net
        .client
        .as_ref()
        .is_some_and(|client| matches!(client.state(), State::Closed | State::Rejected))
    {
        net.disconnected_cleaned = false;
        return;
    }
    if net.disconnected_cleaned {
        return;
    }
    net.disconnected_cleaned = true;
    let names = net.remotes.values().cloned().collect::<HashSet<_>>();
    let entities = world
        .query::<(Entity, &SceneMaxEntity)>()
        .iter(world)
        .filter_map(|(entity, scene)| names.contains(&scene.name).then_some(entity))
        .collect::<Vec<_>>();
    for entity in entities {
        let _ = world.despawn(entity);
    }
    if let Some(mut queue) = world.get_resource_mut::<DelayedActionQueue>() {
        queue.actions.retain(|entry| {
            !entry.actions.iter().any(|action|
            matches!(action, Statement::ModelDecl { name, .. } if names.contains(name)))
        });
    }
    for (id, name) in net.remotes.clone() {
        events::remove_entity(world, net, id, &name);
    }
    if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
        for name in net.locals.keys() {
            vars.0.remove(&format!("{name}.network.id"));
            vars.1.remove(&format!("{name}.network.id"));
        }
    }
    net.locals.clear();
    net.local_actions.clear();
    net.local_sequences.clear();
    net.remotes.clear();
    net.replay = replay::ReplayHistory::default();
    net.poses.clear();
    net.pending.clear();
    net.entity_updates.clear();
    net.remote_pending.clear();
    net.owners.clear();
    net.sources.clear();
    net.rejected_declarations.clear();
    net.widgets = widgets::WidgetSync::default();
    events::connection_closed(&mut net.events);
    if let Some(mut queue) = world.get_resource_mut::<SceneMaxUiActionQueue>() {
        queue.remote_actions.clear();
    }
    controllers::prune(world, &net.replay);
}

#[cfg(test)]
mod tests {
    use super::*;
    fn setup() -> (std::net::UdpSocket, World, Multiplayer) {
        let (server, peer, mut client) = joins::tests::connection();
        joins::tests::accept(&server, peer, &mut client, 1000, "initial");
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        (
            server,
            world,
            Multiplayer {
                client: Some(client),
                ..Default::default()
            },
        )
    }
    fn spawn(world: &mut World, name: &str) -> Entity {
        world
            .spawn((
                SceneMaxEntity {
                    name: name.into(),
                    runtime_name: name.into(),
                },
                Transform::default(),
            ))
            .id()
    }
    #[test]
    fn departed_remote_identity_can_rejoin_without_renaming_or_reviving_old_references() {
        let mut world = World::new();
        crate::value_calls::tests::run_in(&mut world, "scenemax_remote_17 => box");
        let old = world
            .resource::<SceneMaxRuntimeAssets>()
            .root_entity_declarations["scenemax_remote_17"]
            .clone();
        let old_identity = old.entity_identity().unwrap();
        let entity = world
            .query::<(Entity, &SceneMaxEntity)>()
            .iter(&world)
            .find(|(_, scene)| scene.name == "scenemax_remote_17")
            .unwrap()
            .0;
        world.despawn(entity);
        events::remove_entity(
            &mut world,
            &mut Multiplayer::default(),
            17,
            "scenemax_remote_17",
        );
        crate::value_calls::tests::run_in(&mut world, "scenemax_remote_17 => sphere");
        let current = world
            .resource::<SceneMaxRuntimeAssets>()
            .root_entity_declarations["scenemax_remote_17"]
            .entity_identity()
            .unwrap();
        assert_eq!(current.runtime_name, "scenemax_remote_17");
        assert_ne!(current, old_identity);
        assert_ne!(
            old.entity_identity().unwrap().runtime_name,
            current.runtime_name
        );
        assert_eq!(
            world
                .query::<&SceneMaxEntity>()
                .iter(&world)
                .next()
                .unwrap()
                .name,
            current.runtime_name
        );
    }

    #[test]
    fn authored_collision_source_survives_runtime_identity_and_recapture() {
        let (_server, mut world, net) = setup();
        world.insert_resource(net);
        let options = EntityOptions {
            multiplayer: true,
            ..Default::default()
        };
        for runtime in ["scoped_instance", "replacement_instance"] {
            declaration_with_source(
                &mut world.commands(),
                runtime,
                Some("authored"),
                "box",
                &options,
            );
            // Label/state recapture must not overwrite the authored selector.
            declaration(&mut world.commands(), runtime, "box", &options);
            spawn(&mut world, runtime);
        }
        world.flush();
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        update_connected(&mut world, &mut net);
        assert_eq!(net.locals.len(), 2);
        for runtime in ["scoped_instance", "replacement_instance"] {
            assert_eq!(net.local_sources[runtime], "authored");
            world.despawn(net.locals[runtime].0);
        }
        update_connected(&mut world, &mut net);
        assert!(net.local_sources.is_empty());
        assert!(net.declarations.is_empty());
    }

    #[test]
    fn delayed_materialization_same_name_reload_and_despawn_keep_registration_consistent() {
        let (_server, mut world, mut net) = setup();
        net.declarations
            .insert("subject".into(), ("box".into(), EntityOptions::default()));
        net.pending.push((
            "subject".into(),
            "{network_entity}.pos = (1,2,3)".into(),
            None,
        ));
        update_connected(&mut world, &mut net);
        assert_eq!(net.pending.len(), 1);
        assert!(net.locals.is_empty());
        let original = spawn(&mut world, "subject");
        update_connected(&mut world, &mut net);
        let request = net.locals["subject"].1;
        assert!(net.pending.is_empty());
        world.despawn(original);
        let replacement = spawn(&mut world, "subject");
        update_connected(&mut world, &mut net);
        assert_eq!(net.locals["subject"].0, replacement);
        assert_ne!(net.locals["subject"].1, request);
        assert!(net.declarations.contains_key("subject"));
        world.despawn(replacement);
        update_connected(&mut world, &mut net);
        assert!(net.locals.is_empty());
        assert!(net.declarations.is_empty());
    }
    #[test]
    fn inactive_pool_members_retire_and_reacquire_with_fresh_registration() {
        let (_server, mut world, mut net) = setup();
        let entity = spawn(&mut world, "subject");
        let mut pools = SceneMaxObjectPools::default();
        pools.pools.insert(
            "reserve".into(),
            ObjectPoolRuntime {
                members: HashSet::from(["subject".into()]),
                ..Default::default()
            },
        );
        world.insert_resource(pools);
        net.declarations
            .insert("subject".into(), ("box".into(), EntityOptions::default()));
        update_connected(&mut world, &mut net);
        assert!(net.locals.is_empty());
        world
            .resource_mut::<SceneMaxObjectPools>()
            .pools
            .get_mut("reserve")
            .unwrap()
            .in_use
            .insert("subject".into());
        update_connected(&mut world, &mut net);
        let first = net.locals["subject"].1;
        world
            .resource_mut::<SceneMaxVars>()
            .0
            .insert("subject.network.id".into(), 42.);
        world
            .resource_mut::<SceneMaxObjectPools>()
            .pools
            .get_mut("reserve")
            .unwrap()
            .in_use
            .clear();
        net.pending.push((
            "subject".into(),
            "{network_entity}.pos = (1,2,3)".into(),
            None,
        ));
        update_connected(&mut world, &mut net);
        assert!(net.locals.is_empty() && net.pending.is_empty());
        assert!(net.declarations.contains_key("subject"));
        assert!(
            !world
                .resource::<SceneMaxVars>()
                .0
                .contains_key("subject.network.id")
        );
        world
            .resource_mut::<SceneMaxObjectPools>()
            .pools
            .get_mut("reserve")
            .unwrap()
            .in_use
            .insert("subject".into());
        update_connected(&mut world, &mut net);
        assert_eq!(net.locals["subject"].0, entity);
        assert_ne!(net.locals["subject"].1, first);
    }
    #[test]
    fn registration_capacity_recovers_without_poisoning_the_declaration() {
        let (_server, mut world, mut net) = setup();
        let client = net.client.as_mut().unwrap();
        let mut last = 0;
        for _ in 0..2048 {
            last = client
                .register(Spawn {
                    archetype: "box".into(),
                    player: "fixture".into(),
                    source_name: "reserved".into(),
                    command: "{network_entity} => box".into(),
                })
                .unwrap();
        }
        spawn(&mut world, "subject");
        net.declarations
            .insert("subject".into(), ("box".into(), EntityOptions::default()));
        update_connected(&mut world, &mut net);
        assert!(net.locals.is_empty());
        assert!(!net.rejected_declarations.contains("subject"));
        net.client.as_mut().unwrap().destroy(last).unwrap();
        update_connected(&mut world, &mut net);
        assert!(net.locals.contains_key("subject"));
    }
    #[test]
    fn action_enqueue_pressure_retains_the_live_request_for_retry() {
        let (_server, mut world, mut net) = setup();
        spawn(&mut world, "subject");
        net.declarations
            .insert("subject".into(), ("box".into(), EntityOptions::default()));
        update_connected(&mut world, &mut net);
        while net
            .client
            .as_mut()
            .unwrap()
            .send(None, Outgoing::Heartbeat)
            .is_ok()
        {}
        let lease = ActionLease::new(());
        net.pending.push((
            "subject".into(),
            "{network_entity}.move forward 1 in 2 seconds async".into(),
            Some(std::sync::Arc::downgrade(&lease)),
        ));
        update_connected(&mut world, &mut net);
        assert_eq!(net.pending.len(), 1);
        update_connected(&mut world, &mut net);
        assert!(net.pending.is_empty());
    }
    #[test]
    fn disconnect_removes_remote_entities_queued_spawns_and_ids_but_preserves_locals() {
        let (_server, mut world, mut net) = setup();
        let local = spawn(&mut world, "subject");
        let remote = spawn(&mut world, "remote_subject");
        net.remotes.insert(5, "remote_subject".into());
        net.remotes.insert(6, "pending_subject".into());
        net.locals.insert("subject".into(), (local, 1));
        net.owners.insert("remote_subject".into(), 2);
        world
            .resource_mut::<SceneMaxVars>()
            .0
            .insert("subject.network.id".into(), 77.);
        world
            .resource_mut::<SceneMaxVars>()
            .0
            .insert("remote_subject.data.value".into(), 42.);
        world
            .resource_mut::<SceneMaxVars>()
            .0
            .insert("subject.data.value".into(), 9.);
        let mut queue = DelayedActionQueue::default();
        enqueue_delayed_actions(
            Some(&mut queue),
            0.,
            scenemax_parser::parse_program("pending_subject => box")
                .unwrap()
                .statements,
            None,
            None,
        );
        world.insert_resource(queue);
        net.client.as_mut().unwrap().close();
        cleanup_disconnected(&mut world, &mut net);
        assert!(world.get_entity(remote).is_err());
        assert!(world.get_entity(local).is_ok());
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        assert!(net.remotes.is_empty() && net.locals.is_empty() && net.owners.is_empty());
        let vars = world.resource::<SceneMaxVars>();
        assert!(!vars.0.contains_key("subject.network.id"));
        assert!(!vars.0.contains_key("remote_subject.data.value"));
        assert_eq!(vars.0["subject.data.value"], 9.);
        cleanup_disconnected(&mut world, &mut net);
        assert!(world.get_entity(local).is_ok());
    }
}
