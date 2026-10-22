use super::*;
#[test]
fn action_capture_freezes_rebound_targets_and_secondary_references() {
    let mut world = world();
    let (_server, _, client) = joins::tests::connection();
    world.insert_resource(Multiplayer { client: Some(client), ..default() });
    let mut vars = SceneMaxVars::default();
    let mut scope = SceneMaxScopeFrame::default();
    scope.aliases.insert("target".into(), "stale".into());
    scope.aliases.insert("anchor".into(), "stale_anchor".into());
    for (target, anchor) in [("first", "anchor_one"), ("second", "anchor_two")] {
        for (name, value) in [("target", target), ("anchor", anchor)] {
            scenemax_runtime_vm_core::typed::assign(name, serde_json::json!(value), &mut vars, Some(&mut scope), true);
        }
        for source in ["target.rotate (0,10,0)", "target.pos = (1,2,3)", "target.attach to anchor: pos (0,0,0)"] {
            let statement = scenemax_parser::parse_program(source).unwrap().statements.remove(0);
            let _ = capture(&mut world.commands(), &statement, &mut vars, Some(&scope), &HashMap::new(), &HashMap::new());
        }
    }
    world.flush();
    let net = world.resource::<Multiplayer>();
    assert_eq!(net.pending.iter().map(|(target, _, _)| target.as_str()).collect::<Vec<_>>(),
        ["first", "first", "first", "second", "second", "second"]);
    assert!(net.pending[2].1.contains("{network_entity:anchor_one}"));
    assert!(net.pending[5].1.contains("{network_entity:anchor_two}"));
}

#[test]
fn material_publication_requires_valid_document_and_target_and_never_echoes() {
    use bevy::asset::{AssetApp, AssetPlugin};
    let mut app = App::new();
    app.add_plugins((MinimalPlugins, AssetPlugin::default()))
        .init_asset::<Image>().init_asset::<StandardMaterial>()
        .add_plugins(scenemax_materials::MaterialsPlugin);
    app.world_mut().init_resource::<Multiplayer>();
    let root = std::env::temp_dir().join(format!("scenemax-material-wire-{}-{}",
        std::process::id(), std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
    scenemax_assets::filesystem::create_dir_all(root.join("scripts")).unwrap();
    let path = root.join("scripts/finish Ω.smmat");
    scenemax_assets::filesystem::write(&path, scenemax_assets::material::preset("Gold").to_string()).unwrap();
    let assets = SceneMaxRuntimeAssets { asset_root: Some(root.join("resources")), ..default() };
    let target = app.world_mut().spawn(SceneMaxEntity { name: "subject".into(), runtime_name: "subject".into() }).id();
    apply_entity_material(&mut app.world_mut().commands(), target, "finish Ω".into(), &assets);
    app.world_mut().flush();
    let binding = app.world().get::<scenemax_materials::MaterialBinding>(target).unwrap().material.clone();
    assert_eq!(app.world().resource::<Multiplayer>().pending.len(), 1);
    assert_eq!(app.world().resource::<Multiplayer>().pending[0].1, "{network_entity}.material = \"finish Ω\"");
    scenemax_assets::filesystem::write(&path, "{}").unwrap();
    for name in ["finish Ω", "missing"] {
        apply_entity_material(&mut app.world_mut().commands(), target, name.into(), &assets);
        app.world_mut().flush();
        assert_eq!(app.world().get::<scenemax_materials::MaterialBinding>(target).unwrap().material, binding);
        assert_eq!(app.world().resource::<Multiplayer>().pending.len(), 1);
    }
    apply_entity_material(&mut app.world_mut().commands(), target, String::new(), &assets);
    app.world_mut().flush();
    assert!(app.world().get::<scenemax_materials::MaterialBinding>(target).is_none());
    assert_eq!(app.world().resource::<Multiplayer>().pending.len(), 2);
    assert_eq!(app.world().resource::<Multiplayer>().pending[1].1, "{network_entity}.material = \"\"");
    scenemax_assets::filesystem::write(&path, scenemax_assets::material::preset("Gold").to_string()).unwrap();
    app.world_mut().resource_mut::<Multiplayer>().remotes.insert(1, "subject".into());
    apply_entity_material(&mut app.world_mut().commands(), target, "finish Ω".into(), &assets);
    app.world_mut().flush();
    assert!(app.world().get::<scenemax_materials::MaterialBinding>(target).is_some());
    assert_eq!(app.world().resource::<Multiplayer>().pending.len(), 2);
    apply_entity_material(&mut app.world_mut().commands(), target, "finish Ω".into(), &assets);
    app.world_mut().despawn(target);
    app.world_mut().flush();
    assert_eq!(app.world().resource::<Multiplayer>().pending.len(), 2);
    scenemax_assets::filesystem::remove_file(path).unwrap();
    scenemax_assets::filesystem::remove_dir(root.join("scripts")).unwrap();
    scenemax_assets::filesystem::remove_dir(root).unwrap();
}

#[test]
fn source_socket_snapshots_survive_both_orders_and_live_attach_restores_ownership() {
    for reverse in [false, true] {
        let mut world = world();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        let mut net = Multiplayer::default();
        let mut entities = HashMap::new();
        for (id, name, x) in [(1, "source", 2.), (2, "destination", 10.), (3, "carried", 0.)] {
            let pose = Transform::from_xyz(x, 0., 0.);
            let entity = world.spawn((SceneMaxEntity { name: name.into(), runtime_name: name.into() }, pose)).id();
            entities.insert(name.into(), (entity, pose));
            net.remotes.insert(id, name.into());
        }
        let source = entities["source"].0;
        let bone = world.spawn((Name::new("socket"), Transform::from_xyz(0., 1., 0.), ChildOf(source))).id();
        crate::attachments::tests::skin(&mut world, source, bone, 1.);
        let mut commands = vec![
            (1, r#"{network_entity}."socket".attach to {network_entity_id:2}"#),
            (3, r#"{network_entity}.attach to {network_entity_id:1}."socket": pos (1,0,0)"#),
        ];
        if reverse { commands.reverse(); }
        // Separate dispatches also cover snapshots arriving in different packets.
        for (id, command) in commands {
            queue_action_with_origin(&mut net, id, action_record(command, 1).unwrap(),
                Some((u32::MAX, u32::MAX)), true);
            dispatch_remote(&mut world, &mut net, &entities, Instant::now());
            let actions = std::mem::take(&mut world.resource_mut::<DelayedActionQueue>().actions)
                .into_iter().flat_map(|a| a.actions).collect();
            crate::pool_lifecycle::tests::execute_batch(&mut world, actions);
            crate::attachments::update(&mut world);
        }
        assert_eq!(world.get::<Transform>(entities["carried"].0).unwrap().translation, Vec3::new(13., 1., 0.));
        receive_action(&mut net, 3, action_record(r#"{network_entity}.attach to {network_entity_id:1}."socket": pos (1,0,0)"#, 2).unwrap());
        dispatch_remote(&mut world, &mut net, &entities, Instant::now());
        let actions = std::mem::take(&mut world.resource_mut::<DelayedActionQueue>().actions)
            .into_iter().flat_map(|a| a.actions).collect();
        crate::pool_lifecycle::tests::execute_batch(&mut world, actions);
        crate::attachments::update(&mut world);
        assert_eq!(world.get::<Transform>(entities["carried"].0).unwrap().translation, Vec3::new(3., 1., 0.));
        assert_eq!(world.get::<Transform>(source).unwrap().translation, Vec3::new(2., 0., 0.));
    }
}

#[test]
fn source_joint_attachment_wire_preserves_quoted_names_and_owner_validation() {
    let source = r#"child."socket .attach to \"Ω".attach to parent."socket: pos Ω": pos (1,2,3)"#;
    let program = scenemax_parser::parse_program(source).unwrap();
    let (owner, wire) = replication::encode(&program.statements[0], &SceneMaxVars::default(), None,
        &HashMap::new(), &HashMap::new()).unwrap();
    assert_eq!(owner, "child");
    assert!(wire.starts_with(r#"{network_entity}."socket .attach to \"Ω".attach to "#));
    assert!(wire.contains(r#"{network_entity:parent}."socket: pos Ω""#));
    assert_eq!(action_record(&wire, 1).unwrap().slot, 64);
    let wire = replication::references(&wire, "{network_entity:", |_| Some("remote_parent".into())).unwrap();
    let mut world = world();
    let mut net = Multiplayer::default();
    net.remotes.insert(1, "remote_child".into());
    remote_command(&mut world, &net, 1, &wire, None);
    let queue = world.resource::<DelayedActionQueue>();
    assert_eq!(queue.actions.len(), 1);
    let Statement::Attach(a) = &queue.actions[0].actions[0] else { panic!() };
    assert_eq!(crate::ik::split_target(&a.target), ("remote_child", Some("socket .attach to \"Ω".into())));
    assert_eq!(crate::ik::split_target(&a.subject), ("remote_parent", Some("socket: pos Ω".into())));
    assert_eq!(a.offset, SceneMaxVec3 { x:1., y:2., z:3. });
    remote_command(&mut world, &net, 1, r#"unrelated."socket".attach to remote_parent"#, None);
    assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 1);
}

fn world() -> World {
    let mut w = World::new();
    w.init_resource::<DelayedActionQueue>();
    w.init_resource::<SceneMaxVars>();
    w
}
fn spawn() -> Spawn {
    Spawn {
        archetype: "box".into(),
        player: "fixture".into(),
        source_name: "authored".into(),
        command: "{network_entity} => box: multiplayer, size (1,2,3)".into(),
    }
}

#[test]
fn evaluated_effect_commands_round_trip_loop_speed_inputs_and_empty_options() {
    for source in [
        "fx.play attr = [] async",
        "fx.play pos (1,2,3), attr = [\"loop\" 1, \"playback_speed\" 2, \"input0\" 0.5] async",
    ] {
        let program = scenemax_parser::parse_program(source).unwrap();
        let (_, encoded) = replication::encode(&program.statements[0], &SceneMaxVars::default(), None,
            &HashMap::new(), &HashMap::new()).unwrap();
        let action = action_record(&encoded, 1).unwrap();
        assert_eq!(action.slot, 4);
        let decoded = scenemax_parser::parse_program(&encoded.replace("{network_entity}", "fx")).unwrap();
        let Statement::EffekseerPlay(play) = &decoded.statements[0] else { panic!("effect command lost its type"); };
        assert!(play.async_run);
        if source.contains("input0") {
            assert!(play.looped);
            assert!(play.attrs.contains(&("input0".into(), AssignmentValue::Number(0.5))));
            assert!(play.attrs.contains(&("playback_speed".into(), AssignmentValue::Number(2.))));
        } else { assert!(!play.looped); }
    }
}

#[test]
fn held_input_playback_retains_a_lifetime_but_per_frame_motion_does_not() {
    let world = world();
    let mut queue = bevy::ecs::world::CommandQueue::default();
    let mut commands = Commands::new(&mut queue, &world);
    for (source, tracked) in [
        ("fx.play attr = [] async", true),
        ("subject.clip loop", true),
        ("subject.move forward 1 in 1 seconds", false),
    ] {
        let program = scenemax_parser::parse_program(source).unwrap();
        let lifetime = capture_timed(&mut commands, &program.statements[0], &mut SceneMaxVars::default(),
            None, &HashMap::new(), &HashMap::new(), Some(0.016));
        assert_eq!(lifetime.is_some(), tracked);
    }
}

#[test]
fn live_actions_emit_one_numbered_message_and_untracked_commands_stay_unnumbered() {
    let mut sequences = HashMap::new();
    for (source, slot) in [
        ("{network_entity}.move forward 10 in 2 seconds async", 1),
        ("{network_entity}.move to (1,2,3) in 2 seconds async", 1),
        ("{network_entity}.turn right 90 in 2 seconds async", 2),
        ("{network_entity}.fixture_clip at speed of 1 loop", 3),
        ("{network_entity}.pos = (1,2,3)", 252),
    ] {
        let Outgoing::ActionStart { entity, action } = local_command(&mut sequences, 7, source.into()) else {
            panic!("expected a numbered action for {source}");
        };
        assert_eq!(entity, 0);
        assert_eq!(action.slot, slot);
        assert_eq!(action.command, source);
    }
    assert_eq!(sequences[&7], 6);
    assert!(matches!(local_command(&mut sequences, 7, "Logger.info \"fixture\"".into()), Outgoing::Command { .. }));
    assert_eq!(sequences[&7], 6);
}

#[test]
fn live_motion_and_snapshot_share_identity_and_matching_end_cancels_dispatch() {
    let mut world = world();
    let mut net = Multiplayer::default();
    let entity = world.spawn_empty().id();
    net.remotes.insert(101, "scenemax_remote_101".into());
    let entities = HashMap::from([("scenemax_remote_101".into(), (entity, Transform::default()))]);
    let action = action_record("{network_entity}.move forward 10 in 2 seconds", 7).unwrap();
    receive_action(&mut net, 101, action.clone());
    receive_action(&mut net, 101, action.clone());
    queue_action(&mut net, 101, action.clone(), Some((1900, 2000)));
    assert_eq!(net.remote_pending.len(), 1);
    let received = net.remote_pending[0].received_at;
    dispatch_remote(&mut world, &mut net, &entities, received + Duration::from_millis(500));
    let queue = world.resource::<DelayedActionQueue>();
    assert_eq!(queue.actions.len(), 1);
    assert_eq!(queue.actions[0].owner, Some(SceneMaxControllerKey::NetworkAction { entity: 101, slot: 1, sequence: 7 }));
    assert!(matches!(&queue.actions[0].actions[0], Statement::Move(a) if a.distance == 7.5 && a.duration_seconds == 1.5));
    net.replay.end(101, 1, 7);
    controllers::prune(&mut world, &net.replay);
    receive_action(&mut net, 101, action);
    assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    assert!(net.remote_pending.is_empty());
}

#[test]
fn live_start_expires_while_missing_entity_and_end_before_start_blocks_replay() {
    let mut world = world();
    let mut net = Multiplayer::default();
    let action = action_record("{network_entity}.turn right 90 in 2 seconds", 1).unwrap();
    receive_action(&mut net, 101, action.clone());
    let received = net.remote_pending[0].received_at;
    dispatch_remote(&mut world, &mut net, &HashMap::new(), received + Duration::from_secs(2));
    assert!(net.remote_pending.is_empty());
    receive_action(&mut net, 101, action.clone());
    assert!(net.remote_pending.is_empty());
    net.replay.end(102, 2, 1);
    receive_action(&mut net, 102, action);
    assert!(net.remote_pending.is_empty());
}

#[test]
fn multiplayer_local_action_sequences_are_per_entity_and_wrap() {
    let mut sequences = HashMap::new();
    let source = "{network_entity}.pos = (1,2,3)";
    assert_eq!(local_action_record(&mut sequences, 1, source).unwrap().sequence, 1);
    assert_eq!(local_action_record(&mut sequences, 2, source).unwrap().sequence, 1);
    assert!(local_action_record(&mut sequences, 1, "Logger.info \"untracked\"").is_none());
    assert_eq!(local_action_record(&mut sequences, 1, source).unwrap().sequence, 2);
    sequences.insert(1, u16::MAX);
    assert_eq!(local_action_record(&mut sequences, 1, source).unwrap().sequence, u16::MAX);
    assert_eq!(local_action_record(&mut sequences, 1, source).unwrap().sequence, 0);
    assert_eq!(local_action_record(&mut sequences, 2, source).unwrap().sequence, 2);
}

#[test]
fn multiplayer_split_snapshots_merge_slots_without_replaying_or_rewinding() {
    use scenemax_multiplayer::protocol::{Action, SnapshotAction, SnapshotEntity};
    let mut world = world();
    let mut net = Multiplayer::default();
    let action = |slot, sequence| SnapshotAction {
        action: Action { slot, sequence, duration_ms: 10000, command: "{network_entity}.move forward 10 in 10 seconds".into() },
        remaining_ms: 5000,
    };
    let mut record = SnapshotEntity {
        id: 101, owner: 2, spawn: spawn(), pose: Pose::default(),
        animation_index: 0, animation: String::new(), actions: vec![action(1, 10)],
    };
    receive_snapshot(&mut world, &mut net, record.clone());
    net.poses.remove(&101); // First chunk's pose has been applied to the ECS.
    record.actions.push(action(2, 11));
    receive_snapshot(&mut world, &mut net, record.clone());
    receive_snapshot(&mut world, &mut net, record.clone());
    assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 1);
    assert_eq!(net.remote_pending.len(), 2);
    assert!(!net.poses.contains_key(&101));
    assert_eq!(net.owners["scenemax_remote_101"], 2);

    record.actions = vec![action(1, 9)];
    receive_snapshot(&mut world, &mut net, record.clone());
    assert_eq!(net.remote_pending.len(), 2);
    record.actions = vec![action(1, 12)];
    receive_snapshot(&mut world, &mut net, record.clone());
    assert!(!net.replay.current(101, 1, 10));
    assert!(net.replay.current(101, 1, 12));
    net.replay.end(101, 1, 12);
    receive_snapshot(&mut world, &mut net, record.clone());
    assert_eq!(net.remote_pending.len(), 3);
    assert!(!net.replay.current(101, 1, 12));

    record.actions = vec![action(3, 13)];
    record.actions[0].remaining_ms = 0;
    receive_snapshot(&mut world, &mut net, record);
    assert_eq!(net.remote_pending.len(), 3);
    assert!(!net.replay.current(101, 3, 13));
}

#[test]
fn multiplayer_delayed_snapshot_cannot_undo_live_pose_or_destroy() {
    use scenemax_multiplayer::protocol::SnapshotEntity;
    let mut world = world();
    let mut net = Multiplayer::default();
    let record = SnapshotEntity {
        id: 101, owner: 2, spawn: spawn(), pose: Pose::default(),
        animation_index: 0, animation: String::new(), actions: vec![],
    };
    let live = Pose { position: [5., 0., 0.], ..Default::default() };
    assert!(net.replay.live_pose(101));
    net.poses.insert(101, live);
    receive_snapshot(&mut world, &mut net, record.clone());
    assert_eq!(net.poses[&101], live);
    net.replay.destroy(101);
    net.remotes.remove(&101);
    receive_snapshot(&mut world, &mut net, record);
    remote_spawn(&mut world, &mut net, 101, spawn());
    queue_remote(&mut net, 101, "{network_entity}.pos = (0,0,0)".into());
    assert!(!net.remotes.contains_key(&101));
    assert!(net.remote_pending.is_empty());
    world.insert_resource(net);
    scene_changed(&mut world.commands(), "other");
    world.flush();
    assert!(!world.resource::<Multiplayer>().replay.is_destroyed(101));
}

#[test]
fn multiplayer_remote_spawn_uses_existing_queue_without_echo_flag() {
    let mut world = world();
    let mut net = Multiplayer::default();
    remote_spawn(&mut world, &mut net, 101, spawn());
    remote_spawn(&mut world, &mut net, 101, spawn());
    let queue = world.resource::<DelayedActionQueue>();
    assert_eq!(queue.actions.len(), 1);
    assert!(
        matches!(&queue.actions[0].actions[0],Statement::ModelDecl {name,options,..} if name=="scenemax_remote_101" && !options.multiplayer)
    );
    let mut malicious = spawn();
    malicious.command.push_str("\nadd \"outside.code\" code");
    remote_spawn(&mut world, &mut net, 102, malicious);
    assert!(!net.remotes.contains_key(&102));
    for source in ["box: pos (local_action(),0,0)", "box: scale local_action()", "box: pos (items.acquire,0,0)"] {
        let mut malicious = spawn();
        malicious.command = format!("{{network_entity}} => {source}");
        remote_spawn(&mut world, &mut net, 103, malicious);
        assert!(!net.remotes.contains_key(&103), "{source}");
    }
    assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 1);
}

#[test]
fn multiplayer_remote_actions_are_scoped_and_late_move_resumes_remaining_distance() {
    let mut world = world();
    let mut net = Multiplayer::default();
    net.remotes.insert(101, "scenemax_remote_101".into());
    remote_command(
        &mut world,
        &net,
        101,
        "local_entity.move forward 5 in 1 seconds",
        None,
    );
    remote_command(&mut world, &net, 101, "add \"outside.code\" code", None);
    assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    remote_command(
        &mut world,
        &net,
        101,
        "{network_entity}.move forward 10 in 2 seconds",
        Some((500, 2000)),
    );
    let queue = world.resource::<DelayedActionQueue>();
    assert!(
        matches!(&queue.actions[0].actions[0],Statement::Move(a) if a.distance==2.5 && a.duration_seconds==0.5)
    );
}

#[test]
fn multiplayer_events_declare_scalars_immediately_and_preserve_array_payloads() {
    let mut world = world();
    world.init_resource::<Multiplayer>();
    let mut vars = SceneMaxVars::default();
    let p=scenemax_parser::parse_program("network var total = 7\nnetwork var values = [1,\"text\",[true,null]]\nnetwork.on (\"tick\", 5) = do\n total = total + 1\nend do").unwrap();
    for s in &p.statements {
        capture(
            &mut world.commands(),
            s,
            &mut vars,
            None,
            &HashMap::new(),
            &HashMap::new(),
        );
    }
    assert_eq!(vars.0.get("total"), Some(&7.));
    world.flush();
    update(&mut world);
    let mut net = world.remove_resource::<Multiplayer>().unwrap();
    events::receive(
        &mut world,
        &mut net,
        Incoming::Variable {
            name: "total".into(),
            initial: false,
            value: serde_json::json!(9),
        },
    );
    assert_eq!(world.resource::<SceneMaxVars>().0.get("total"), Some(&9.));
    events::receive(
        &mut world,
        &mut net,
        Incoming::Event {
            name: "tick".into(),
            message: None,
        },
    );
    assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 1);
}

#[test]
fn multiplayer_action_slots_and_wire_source_parse() {
    let a = action_record("{network_entity}.move forward 5 in 2 seconds async", 7).unwrap();
    assert_eq!((a.slot, a.duration_ms, a.sequence), (1, 2000, 7));
    let a = action_record("{network_entity}.shader = \"fixture\"", 8).unwrap();
    assert_eq!(a.duration_ms, u32::MAX);
}

#[test]
fn relative_axis_commands_capture_evaluated_values_and_resume_without_changing_axis() {
    for (axis, expected_axis) in [("x", scenemax_parser::RotationAxis::X), ("z", scenemax_parser::RotationAxis::Z)] {
        let mut world = world();
        let (_server, _, client) = joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let mut vars = SceneMaxVars::default();
        vars.0.insert("angle".into(), 30.);
        vars.0.insert("duration".into(), 2.);
        let p = scenemax_parser::parse_program(&format!("subject.rotate({axis} - (angle + 15) * 2) in duration seconds")).unwrap();
        let statement = scenemax_runtime_script_core::substitute_statement(&p.statements[0],
            &HashMap::from([("subject".into(), "instance".into())]));
        let Statement::Turn(substituted) = &statement else { panic!(); };
        assert_eq!(substituted.axis, Some(expected_axis));
        assert_eq!(substituted.target, "instance");
        let lease = capture(&mut world.commands(), &statement, &mut vars, None,
            &HashMap::new(), &HashMap::new());
        assert!(lease.is_some());
        world.flush();
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        let (_, source, _) = net.pending.pop().unwrap();
        let action = action_record(&source, 7).unwrap();
        assert_eq!((action.slot, action.duration_ms), (2, 2000));
        net.remotes.insert(101, "scenemax_remote_101".into());
        let entity = world.spawn_empty().id();
        let entities = HashMap::from([("scenemax_remote_101".into(), (entity, Transform::default()))]);
        queue_action(&mut net, 101, action.clone(), Some((1500, 2000)));
        let received = net.remote_pending[0].received_at;
        dispatch_remote(&mut world, &mut net, &entities, received + Duration::from_millis(500));
        let queue = world.resource::<DelayedActionQueue>();
        let Statement::Turn(turn) = &queue.actions[0].actions[0] else { panic!("rotation lost on replay"); };
        assert_eq!(turn.axis, Some(expected_axis));
        assert_eq!(turn.degrees, -45.);
        assert_eq!(turn.duration_seconds, 1.);
        let mut controller = timed_turn_from_statement(turn);
        controller.controller_owner = queue.actions[0].owner.clone();
        world.entity_mut(entity).insert(TimedTurns { turns: vec![controller] });
        net.replay.end(101, 2, 7);
        controllers::prune(&mut world, &net.replay);
        assert!(world.get::<TimedTurns>(entity).is_none());
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        receive_action(&mut net, 101, action);
        assert!(net.remote_pending.is_empty());
    }
}

#[test]
fn label_text_uses_persistent_evaluated_replay_and_rejects_other_targets() {
    let mut world = world();
    let (_server, _, client) = joins::tests::connection();
    world.insert_resource(Multiplayer { client: Some(client), ..default() });
    let mut vars = SceneMaxVars::default();
    let message = "Snapshot \"quoted\" \\ Ω {network_entity} {network_entity:other}";
    scenemax_runtime_vm_core::typed::store(&mut vars, "message", serde_json::json!(message));
    // Declaration activation/reuse also snapshots its evaluated initial text.
    let declaration = scenemax_parser::parse_program("caption => label: multiplayer, text message").unwrap().statements.remove(0);
    capture(&mut world.commands(), &declaration, &mut vars, None, &HashMap::new(), &HashMap::new());
    world.flush();
    let initial = world.resource_mut::<Multiplayer>().pending.pop().unwrap();
    assert_eq!(action_record(&initial.1, 1).unwrap().slot, 254);
    let statement = scenemax_parser::parse_program("subject.text = message").unwrap().statements.remove(0);
    let statement = scenemax_runtime_script_core::substitute_statement(&statement, &HashMap::from([("subject".into(), "caption".into())]));
    assert!(capture(&mut world.commands(), &statement, &mut vars, None, &HashMap::new(), &HashMap::new()).is_none());
    world.flush();
    let mut net = world.remove_resource::<Multiplayer>().unwrap();
    let (target, source, lease) = net.pending.pop().unwrap();
    assert_eq!(target, "caption");
    assert!(lease.is_none());
    let action = action_record(&source, 7).unwrap();
    assert_eq!((action.slot, action.duration_ms), (254, u32::MAX));
    net.remotes.insert(101, "scenemax_remote_101".into());
    let entity = world.spawn_empty().id();
    let entities = HashMap::from([("scenemax_remote_101".into(), (entity, Transform::default()))]);
    queue_action(&mut net, 101, action.clone(), Some((u32::MAX, u32::MAX)));
    dispatch_remote(&mut world, &mut net, &entities, Instant::now() + Duration::from_secs(60));
    let queue = world.resource::<DelayedActionQueue>();
    let Statement::LabelText { target, value } = &queue.actions[0].actions[0] else { panic!() };
    assert_eq!(target, "scenemax_remote_101");
    assert_eq!(crate::labels::text(value, &SceneMaxVars::default(), None, &HashMap::new(), &HashMap::new()), message);
    receive_action(&mut net, 101, action);
    assert!(net.remote_pending.is_empty());
    let malicious = action_record("unrelated.text = \"bad\"", 8).unwrap();
    receive_action(&mut net, 101, malicious);
    dispatch_remote(&mut world, &mut net, &entities, Instant::now());
    assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 1);
}

#[test]
fn multiplayer_attach_ik_effect_and_position_emit_parseable_wire_commands() {
    for source in [
        "fixture.pos = (1,2,3)",
        "fixture.move to (4,5,6) in 2 seconds",
        "fixture.attach to parent: pos (1,2,3)",
        "fixture.ik = \"fixture_ik\"",
        "fixture.ik.contact.play: target parent, weight 0.5, blend 0.1",
        "fixture.ik.contact.stop: blend 0.2",
        "fixture.ik.contact.weight = 0.8",
    ] {
        let statement = scenemax_parser::parse_program(source)
            .unwrap()
            .statements
            .remove(0);
        let (_, text) = replication::encode(
            &statement,
            &SceneMaxVars::default(),
            None,
            &HashMap::new(),
            &HashMap::new(),
        )
        .unwrap();
        let text = replication::references(&text, "{network_entity:", |_| {
            Some("resolved_parent".into())
        })
        .unwrap()
        .replace("{network_entity}", "resolved_entity");
        let p = scenemax_parser::parse_program(&text).unwrap();
        assert_eq!(p.statements.len(), 1, "{text}");
        assert!(
            !matches!(p.statements[0], Statement::Unsupported { .. }),
            "{text}"
        );
        assert!(action_record(&text, 1).is_some(), "{text}");
    }
}

#[test]
fn multiplayer_held_input_sends_only_this_frames_movement() {
    let server = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
    let client = Client::connect(
        server.local_addr().unwrap(),
        Login {
            password_hash: password_hash(""),
            project_guid: String::new(),
            create_session: false,
            session_id: 1000,
            session_name: "test".into(),
            scene: "main".into(),
            player: "test".into(),
        },
    )
    .unwrap();
    let mut world = world();
    world.insert_resource(Multiplayer {
        client: Some(client),
        ..Default::default()
    });
    let action = scenemax_parser::parse_program("fixture.move forward 10 in 1 seconds")
        .unwrap()
        .statements
        .remove(0);
    capture_timed(
        &mut world.commands(),
        &action,
        &mut SceneMaxVars::default(),
        None,
        &HashMap::new(),
        &HashMap::new(),
        Some(0.02),
    );
    world.flush();
    let net = world.resource::<Multiplayer>();
    let p =
        scenemax_parser::parse_program(&net.pending[0].1.replace("{network_entity}", "fixture"))
            .unwrap();
    assert!(
        matches!(&p.statements[0],Statement::Move(a) if (a.distance-0.2).abs()<0.0001 && a.duration_seconds==0.02)
    );
}

#[test]
fn multiplayer_closed_connection_clears_script_readiness() {
    let server = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
    let mut client = Client::connect(
        server.local_addr().unwrap(),
        Login {
            password_hash: password_hash(""),
            project_guid: String::new(),
            create_session: false,
            session_id: 1000,
            session_name: "test".into(),
            scene: "main".into(),
            player: "test".into(),
        },
    )
    .unwrap();
    client.close();
    let mut world = world();
    world
        .resource_mut::<SceneMaxVars>()
        .0
        .insert("network.ready".into(), 1.);
    world.insert_resource(Multiplayer {
        client: Some(client),
        ..Default::default()
    });
    update(&mut world);
    assert_eq!(world.resource::<SceneMaxVars>().0["network.ready"], 0.);
}

#[test]
fn multiplayer_entity_id_is_readable_and_removed_with_remote() {
    let server = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
    let client = Client::connect(
        server.local_addr().unwrap(),
        Login {
            password_hash: password_hash(""),
            project_guid: String::new(),
            create_session: false,
            session_id: 1000,
            session_name: "test".into(),
            scene: "main".into(),
            player: "test".into(),
        },
    )
    .unwrap();
    let mut world = world();
    let mut net = Multiplayer {
        client: Some(client),
        ..Default::default()
    };
    net.remotes.insert(101, "remote_fixture".into());
    world.insert_resource(net);
    update(&mut world);
    assert_eq!(
        world.resource::<SceneMaxVars>().0["remote_fixture.network.id"],
        101.
    );
    let mut net = world.remove_resource::<Multiplayer>().unwrap();
    events::remove_entity(&mut world, &mut net, 101, "remote_fixture");
    assert!(
        !world
            .resource::<SceneMaxVars>()
            .0
            .contains_key("remote_fixture.network.id")
    );
}

#[test]
fn imported_launch_defaults_are_lowest_priority_and_cannot_supply_passwords() {
    let dir = tempfile::tempdir().unwrap();
    let root = dir.path();
    let defaults = r#"{"multiplayer_server":"catalog","multiplayer_port":"9345","project_guid":"catalog-guid","multiplayer_password":"secret"}"#;
    let resolve = |script: Option<&Path>| configured_settings(Some(root), script, Some(defaults), |_| None).unwrap().unwrap();
    let (host, port, login) = resolve(None);
    assert_eq!((host.as_str(), port, login.project_guid.as_str()), ("catalog", 9345, "catalog-guid"));
    assert_eq!(login.password_hash, password_hash(""));
    fs::create_dir(root.join(".scenemax-studio")).unwrap();
    fs::write(root.join(".scenemax-studio/multiplayer.json"), r#"{"server":"native","port":9346}"#).unwrap();
    assert_eq!(resolve(None).0, "native");
    let script = root.join("main");
    fs::write(&script, "//$[multiplayer_server]=script;\n//$[multiplayer_port]=9347;").unwrap();
    assert_eq!((resolve(Some(&script)).0, resolve(Some(&script)).1), ("script".into(), 9347));
    let result = configured_settings(Some(root), Some(&script), Some(defaults), |key| (key == "SCENEMAX_MULTIPLAYER_SERVER").then(|| "explicit".into())).unwrap().unwrap();
    assert_eq!(result.0, "explicit");
    assert!(configured_settings(None, None, Some("invalid"), |_| None).is_err());
    assert!(configured_settings(None, None, Some(&" ".repeat(16385)), |_| None).is_err());
}


#[test]
fn empty_explicit_password_overrides_packaged_metadata() {
    let dir = tempfile::tempdir().unwrap();
    let script = dir.path().join("main");
    fs::write(&script, "//$[multiplayer_server]=localhost;\n//$[multiplayer_password]=old-password;").unwrap();
    let (_, _, login) = configured_settings(None, Some(&script), None, |key|
        (key == "SCENEMAX_MULTIPLAYER_PASSWORD").then(String::new)).unwrap().unwrap();
    assert_eq!(login.password_hash, password_hash(""));
}

#[test]
fn snapshot_waiting_for_entity_counts_elapsed_once_before_replay() {
    let mut world = world();
    let mut net = Multiplayer::default();
    let action = action_record("{network_entity}.move forward 10 in 2 seconds", 1).unwrap();
    queue_action(&mut net, 101, action, Some((1000, 2000)));
    let received = net.remote_pending[0].received_at;
    dispatch_remote(&mut world, &mut net, &HashMap::new(), received + Duration::from_millis(200));
    dispatch_remote(&mut world, &mut net, &HashMap::new(), received + Duration::from_millis(300));
    assert_eq!(net.remote_pending.len(), 1);
    assert_eq!(net.remote_pending[0].received_at, received);
    net.remotes.insert(101, "scenemax_remote_101".into());
    let entity = world.spawn_empty().id();
    let entities = HashMap::from([("scenemax_remote_101".into(), (entity, Transform::default()))]);
    dispatch_remote(&mut world, &mut net, &entities, received + Duration::from_millis(500));
    assert!(net.remote_pending.is_empty());
    let queue = world.resource::<DelayedActionQueue>();
    assert!(matches!(&queue.actions[0].actions[0], Statement::Move(a) if a.distance == 2.5 && a.duration_seconds == 0.5));
}

#[test]
fn ik_property_commands_survive_same_frame_updates_and_wait_in_order_for_targets() {
    let mut world = world();
    let mut net = Multiplayer::default();
    net.remotes.insert(101, "scenemax_remote_101".into());
    net.remotes.insert(102, "scenemax_remote_102".into());
    let entity = world.spawn_empty().id();
    let mut entities = HashMap::from([("scenemax_remote_101".into(), (entity, Transform::default()))]);
    let sources = [
        "{network_entity}.ik = empty",
        "{network_entity}.ik = \"fixture\"",
        "{network_entity}.ik.contact.target = {network_entity_id:102}",
        "{network_entity}.ik.contact.blend = 0.2",
        "{network_entity}.ik.contact.play",
    ];
    for (i, source) in sources.iter().enumerate() {
        let action = action_record(source, i as u16 + 1).unwrap();
        receive_action(&mut net, 101, action.clone());
        receive_action(&mut net, 101, action); // A retransmission is still ignored.
    }
    assert_eq!(net.remote_pending.len(), 5);
    dispatch_remote(&mut world, &mut net, &entities, Instant::now());
    assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 2);
    assert_eq!(net.remote_pending.len(), 3);
    entities.insert("scenemax_remote_102".into(), (world.spawn_empty().id(), Transform::default()));
    dispatch_remote(&mut world, &mut net, &entities, Instant::now());
    controllers::prune(&mut world, &net.replay);
    let queue = &world.resource::<DelayedActionQueue>().actions;
    assert_eq!(queue.len(), 5);
    for (queued, source) in queue.iter().zip(sources) {
        let expected = source.replace("{network_entity}", "scenemax_remote_101")
            .replace("{network_entity_id:102}", "scenemax_remote_102");
        assert_eq!(queued.actions, scenemax_parser::parse_program(&expected).unwrap().statements);
        assert!(queued.owner.is_none());
    }
    assert!(net.remote_pending.is_empty());
}

#[test]
fn snapshot_expires_while_reference_missing_and_duplicate_cannot_restart_it() {
    let mut world = world();
    let mut net = Multiplayer::default();
    net.remotes.insert(101, "scenemax_remote_101".into());
    let entity = world.spawn_empty().id();
    let entities = HashMap::from([("scenemax_remote_101".into(), (entity, Transform::default()))]);
    let action = scenemax_multiplayer::protocol::Action { slot: 1, sequence: 7, duration_ms: 2000,
        command: "{network_entity}.move to {network_entity_id:202} in 2 seconds async".into() };
    queue_action(&mut net, 101, action.clone(), Some((500, 2000)));
    let received = net.remote_pending[0].received_at;
    dispatch_remote(&mut world, &mut net, &entities, received + Duration::from_millis(200));
    assert_eq!(net.remote_pending.len(), 1);
    dispatch_remote(&mut world, &mut net, &entities, received + Duration::from_millis(500));
    assert!(net.remote_pending.is_empty());
    assert!(!net.replay.current(101, 1, 7));
    queue_action(&mut net, 101, action, Some((500, 2000)));
    assert!(net.remote_pending.is_empty());
    assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
}

#[test]
fn pending_timing_preserves_indefinite_and_unnumbered_commands() {
    let now = Instant::now();
    let mut pending = RemoteCommand { entity: 1, command: String::new(), resume: Some((500, 2000)), action: None, received_at: now, snapshot: false };
    assert_eq!(pending.resume_at(now - Duration::from_millis(1)), Some((500, 2000)));
    assert_eq!(pending.resume_at(now + Duration::from_millis(u64::from(u32::MAX) + 1000)), Some((0, 2000)));
    pending.resume = Some((u32::MAX, u32::MAX));
    assert_eq!(pending.resume_at(now + Duration::from_secs(60)), pending.resume);
    pending.action = Some((3, 1));
    assert_eq!(pending.resume_at(now + Duration::from_millis(123)), Some((u32::MAX - 123, u32::MAX)));
    pending.resume = None;
    assert_eq!(pending.resume_at(now + Duration::from_secs(60)), None);
}

#[test]
fn rotation_reset_evaluates_substitution_and_replays_only_for_its_remote_owner() {
    let mut world = world();
    let mut vars = SceneMaxVars::default();
    vars.0.insert("angle".into(), 15.);
    let program = scenemax_parser::parse_program("subject.rotate ((value + 5) * 2, -30, 70)").unwrap();
    let statement = scenemax_runtime_script_core::substitute_statement(&program.statements[0],
        &HashMap::from([("subject".into(), "instance".into()), ("value".into(), "angle".into())]));
    let (target, encoded) = replication::encode(&statement, &vars, None, &HashMap::new(), &HashMap::new()).unwrap();
    assert_eq!(target, "instance");
    assert_eq!(encoded, "{network_entity}.rotate (40,-30,70)");
    // Reset is a one-shot command. Keeping it in the snapshot action slots would
    // overwrite the snapshot's newer orientation after subsequent relative turns.
    assert!(action_record(&encoded, 1).is_none());
    assert!(matches!(local_command(&mut HashMap::new(), 1, encoded.clone()), Outgoing::Command { .. }));
    let mut net = Multiplayer::default();
    net.remotes.insert(101, "scenemax_remote_101".into());
    let entity = world.spawn_empty().id();
    let entities = HashMap::from([("scenemax_remote_101".into(), (entity, Transform::default()))]);
    queue_remote(&mut net, 101, encoded);
    queue_remote(&mut net, 101, "unrelated.rotate (0,0,0)".into());
    dispatch_remote(&mut world, &mut net, &entities, Instant::now());
    let queue = world.resource::<DelayedActionQueue>();
    assert_eq!(queue.actions.len(), 1);
    let Statement::RotationReset { target, rotation } = &queue.actions[0].actions[0] else { panic!(); };
    assert_eq!(target, "scenemax_remote_101");
    assert_eq!(evaluate_position_value_runtime(rotation, &SceneMaxVars::default(), None,
        &HashMap::new(), &HashMap::new(), None), Some(Vec3::new(40., -30., 70.)));
}

#[test]
fn rotate_to_capture_and_late_join_keep_target_and_cancel_owned_controller() {
    let mut world = world();
    let (_server, _, client) = joins::tests::connection();
    world.insert_resource(Multiplayer { client: Some(client), ..default() });
    let mut vars = SceneMaxVars::default();
    vars.0.insert("angle".into(), 15.);
    vars.0.insert("duration".into(), 2.);
    let p = scenemax_parser::parse_program("subject.rotate to (y value + 5) in duration seconds").unwrap();
    let statement = scenemax_runtime_script_core::substitute_statement(&p.statements[0],
        &HashMap::from([("subject".into(), "instance".into()), ("value".into(), "angle".into())]));
    let lease = capture(&mut world.commands(), &statement, &mut vars, None, &HashMap::new(), &HashMap::new());
    assert!(lease.is_some());
    world.flush();
    let mut net = world.remove_resource::<Multiplayer>().unwrap();
    let (target, source, _) = net.pending.pop().unwrap();
    assert_eq!(target, "instance");
    assert_eq!(source, "{network_entity}.rotate to (y 20) in 2 seconds async");
    let action = action_record(&source, 7).unwrap();
    assert_eq!((action.slot, action.duration_ms), (2, 2000));
    net.remotes.insert(101, "scenemax_remote_101".into());
    let pose = Transform::from_rotation(Quat::from_rotation_y(10_f32.to_radians()));
    let entity = world.spawn(pose).id();
    let entities = HashMap::from([("scenemax_remote_101".into(), (entity, pose))]);
    queue_action(&mut net, 101, action.clone(), Some((1500, 2000)));
    let received = net.remote_pending[0].received_at;
    dispatch_remote(&mut world, &mut net, &entities, received + Duration::from_millis(500));
    let queue = world.resource::<DelayedActionQueue>();
    let Statement::Turn(turn) = &queue.actions[0].actions[0] else { panic!(); };
    assert!(turn.absolute);
    assert_eq!(turn.degrees, 20.);
    assert_eq!(turn.duration_seconds, 1.);
    let mut controller = timed_turn_from_statement_resolved(turn, turn.degrees, turn.duration_seconds, pose.rotation);
    let mut transform = pose;
    apply_turn_delta(&mut transform, &controller, 1.);
    assert!(transform.rotation.abs_diff_eq(Quat::from_rotation_y(20_f32.to_radians()), 0.0001));
    controller.controller_owner = queue.actions[0].owner.clone();
    world.entity_mut(entity).insert(TimedTurns { turns: vec![controller] });
    net.replay.end(101, 2, 7);
    controllers::prune(&mut world, &net.replay);
    assert!(world.get::<TimedTurns>(entity).is_none());
    assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    receive_action(&mut net, 101, action);
    assert!(net.remote_pending.is_empty());
}

#[test]
fn continuous_rotate_to_emits_only_the_current_clamped_relative_step() {
    for duration in [2., 0.] {
        let mut world = world();
        let (_server, _, client) = joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let p = scenemax_parser::parse_program(&format!("subject.rotate to (y 10) in {duration} seconds")).unwrap();
        let pose = Transform::from_rotation(Quat::from_rotation_y(350_f32.to_radians()));
        let lease = capture_timed(&mut world.commands(), &p.statements[0], &mut SceneMaxVars::default(),
            None, &HashMap::new(), &HashMap::from([("subject".into(), pose)]), Some(0.5));
        assert!(lease.is_none());
        world.flush();
        let (_, source, _) = world.resource_mut::<Multiplayer>().pending.pop().unwrap();
        let p = scenemax_parser::parse_program(&source.replace("{network_entity}", "remote")).unwrap();
        let Statement::Turn(turn) = &p.statements[0] else { panic!(); };
        assert!(!turn.absolute);
        assert_eq!(turn.duration_seconds, 0.5);
        assert!((turn.degrees - if duration == 0. { 20. } else { 5. }).abs() < 0.001);
    }
}

#[test]
fn axis_move_capture_substitutes_values_and_resumes_remaining_distance() {
    for (axis, direction, vector) in [("x", MoveDirection::AxisX, Vec3::X), ("y", MoveDirection::AxisY, Vec3::Y), ("z", MoveDirection::AxisZ, Vec3::Z)] {
        let mut world = world();
        let (_server, _, client) = joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let mut vars = SceneMaxVars::default();
        vars.0.insert("distance".into(), 3.);
        vars.0.insert("duration".into(), 2.);
        let p = scenemax_parser::parse_program(&format!("subject.move ({axis} - (amount + 1) * 2) in duration seconds")).unwrap();
        let statement = scenemax_runtime_script_core::substitute_statement(&p.statements[0],
            &HashMap::from([("subject".into(), "instance".into()), ("amount".into(), "distance".into())]));
        let lease = capture(&mut world.commands(), &statement, &mut vars, None, &HashMap::new(), &HashMap::new());
        assert!(lease.is_some());
        world.flush();
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        let (target, source, _) = net.pending.pop().unwrap();
        assert_eq!(target, "instance");
        assert_eq!(source, format!("{{network_entity}}.move ({axis} - 8) in 2 seconds async"));
        let action = action_record(&source, 7).unwrap();
        assert_eq!((action.slot, action.duration_ms), (1, 2000));
        net.remotes.insert(101, "scenemax_remote_101".into());
        let pose = Transform::from_rotation(Quat::from_rotation_y(1.2));
        let entity = world.spawn(pose).id();
        let entities = HashMap::from([("scenemax_remote_101".into(), (entity, pose))]);
        queue_action(&mut net, 101, action.clone(), Some((1500, 2000)));
        let received = net.remote_pending[0].received_at;
        dispatch_remote(&mut world, &mut net, &entities, received + Duration::from_millis(500));
        let queue = world.resource::<DelayedActionQueue>();
        let Statement::Move(movement) = &queue.actions[0].actions[0] else { panic!(); };
        assert_eq!(movement.direction, direction);
        assert_eq!((movement.distance, movement.duration_seconds), (-4., 1.));
        let mut controller = timed_move_from_statement(movement, &pose);
        assert!(controller.velocity.abs_diff_eq(-vector * 4., 0.0001));
        controller.controller_owner = queue.actions[0].owner.clone();
        world.entity_mut(entity).insert(TimedMoves { moves: vec![controller] });
        net.replay.end(101, 1, 7);
        controllers::prune(&mut world, &net.replay);
        assert!(world.get::<TimedMoves>(entity).is_none());
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        receive_action(&mut net, 101, action);
        assert!(net.remote_pending.is_empty());
    }
}

#[test]
fn continuous_axis_delta_is_evaluated_and_clamped_without_a_retained_lifetime() {
    for (duration, expected) in [(2., -1.), (0., -8.)] {
        let mut world = world();
        let (_server, _, client) = joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let p = scenemax_parser::parse_program(&format!("subject.move (z - 8) in {duration} seconds")).unwrap();
        let lease = capture_timed(&mut world.commands(), &p.statements[0], &mut SceneMaxVars::default(),
            None, &HashMap::new(), &HashMap::new(), Some(0.25));
        assert!(lease.is_none());
        world.flush();
        let (_, source, _) = world.resource_mut::<Multiplayer>().pending.pop().unwrap();
        let p = scenemax_parser::parse_program(&source.replace("{network_entity}", "remote")).unwrap();
        let Statement::Move(movement) = &p.statements[0] else { panic!(); };
        assert_eq!(movement.direction, MoveDirection::AxisZ);
        assert_eq!((movement.distance, movement.duration_seconds), (expected, 0.25));
    }
}

#[test]
fn eased_motion_capture_replays_original_curve_phase_and_evaluated_parameters() {
    for source in [
        "subject.move (x + 8) in 4 seconds ease in \"Power\" (power_arg)",
        "subject.turn left 80 in 4 seconds ease in \"Power\" (power_arg)",
        "subject.rotate to (y 90) in 4 seconds ease in \"Power\" (power_arg)",
        "subject.move to (9,2,3) in 4 seconds ease in \"Power\" (power_arg)",
    ] {
        let mut world = world();
        let (_server, _, client) = joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let mut vars = SceneMaxVars::default(); vars.0.insert("exponent".into(), 2.);
        let p = scenemax_parser::parse_program(source).unwrap();
        let action = scenemax_runtime_script_core::substitute_statement(&p.statements[0], &HashMap::from([("power_arg".into(), "exponent".into())]));
        let start = Transform::from_xyz(1.,2.,3.).with_rotation(Quat::from_rotation_y(10_f32.to_radians()));
        let lease = capture(&mut world.commands(), &action, &mut vars, None, &HashMap::new(), &HashMap::from([("subject".into(), start)]));
        assert!(lease.is_some()); world.flush();
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        let (_, wire, _) = net.pending.pop().unwrap();
        assert!(!wire.contains("exponent")); assert!(wire.contains("(2)"));
        let record = action_record(&wire, 9).unwrap();
        net.remotes.insert(101, "remote".into());
        let pose = if matches!(action, Statement::Turn(_)) {
            Transform::from_rotation(Quat::from_rotation_y(30_f32.to_radians()))
        } else { Transform::from_xyz(3.,2.,3.).with_rotation(start.rotation) };
        let entity = world.spawn(pose).id();
        queue_action(&mut net, 101, record.clone(), Some((2500, 4000)));
        let received = net.remote_pending[0].received_at;
        dispatch_remote(&mut world, &mut net, &HashMap::from([("remote".into(), (entity, pose))]), received + Duration::from_millis(500));
        let queue = world.resource::<DelayedActionQueue>();
        let owner = queue.actions[0].owner.clone();
        let actions = queue.actions[0].actions.clone();
        assert_eq!(actions.len(), 1);
        assert!(wire.len() < 192);
        let mut actual = pose;
        for action in &actions {
            match action {
                Statement::Move(a) => {
                    assert_eq!(a.easing.as_ref().unwrap().start_progress, 0.5);
                    assert_eq!(a.duration_seconds, 2.);
                    let mut c = timed_move_from_statement(a, &pose);
                    actual.translation += c.velocity * c.curve.step(c.duration_seconds, c.remaining_seconds, 1.);
                    c.remaining_seconds -= 1.; c.controller_owner = owner.clone();
                    // At 3/4 elapsed, displacement is 8*(3/4)^2 = 4.5, not
                    // a new ease-in over the remaining distance.
                    if a.direction == MoveDirection::AxisX { assert!((actual.translation.x - 5.5).abs() < 0.0001); }
                    world.entity_mut(entity).insert(TimedMoves { moves: vec![c] });
                }
                Statement::MoveTo(a) => {
                    assert!(matches!(a.destination, MoveToDestination::Delta(_)));
                    assert_eq!(a.easing.as_ref().unwrap().start_progress, 0.5);
                    let mut c = crate::actions::resolved_move_to(a, &pose, &SceneMaxVars::default(), None, &HashMap::new(), &HashMap::new(), None).unwrap();
                    actual.translation += c.velocity * c.curve.step(c.duration_seconds, c.remaining_seconds, 1.);
                    assert!((actual.translation.x - 5.5).abs() < 0.0001);
                    assert!(c.final_translation.unwrap().abs_diff_eq(Vec3::new(9., 2., 3.), 0.0001));
                    c.remaining_seconds -= 1.; c.controller_owner = owner.clone();
                    world.entity_mut(entity).insert(TimedMoves { moves: vec![c] });
                }
                Statement::Turn(a) => {
                    assert!(!a.absolute); assert!((a.degrees - 80.).abs() < 0.001);
                    assert_eq!(a.easing.as_ref().unwrap().start_progress, 0.5);
                    let mut c = timed_turn_from_statement_resolved(a, a.degrees, a.duration_seconds, pose.rotation);
                    apply_turn_delta(&mut actual, &c, 1.);
                    assert!(actual.rotation.abs_diff_eq(Quat::from_rotation_y(55_f32.to_radians()), 0.0001));
                    c.remaining_seconds -= 1.; c.controller_owner = owner.clone();
                    world.entity_mut(entity).insert(TimedTurns { turns: vec![c] });
                }
                _ => panic!(),
            }
        }
        net.replay.end(101, record.slot, 9); controllers::prune(&mut world, &net.replay);
        assert!(world.get::<TimedMoves>(entity).is_none()); assert!(world.get::<TimedTurns>(entity).is_none());
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        receive_action(&mut net, 101, record); assert!(net.remote_pending.is_empty());
    }
}

#[test]
fn continuous_eased_axis_and_target_turn_emit_evaluated_linear_steps() {
    for source in ["subject.move (x + 8) in 2 seconds ease in", "subject.rotate to (y 80) in 2 seconds ease in"] {
        let mut world = world();
        let (_server, _, client) = joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let p = scenemax_parser::parse_program(source).unwrap();
        let lease = capture_timed(&mut world.commands(), &p.statements[0], &mut SceneMaxVars::default(), None,
            &HashMap::new(), &HashMap::from([("subject".into(), Transform::default())]), Some(0.5));
        assert!(lease.is_none()); world.flush();
        let (_, wire, _) = world.resource_mut::<Multiplayer>().pending.pop().unwrap();
        let p = scenemax_parser::parse_program(&wire.replace("{network_entity}", "remote")).unwrap();
        match &p.statements[0] {
            Statement::Move(a) => { assert_eq!(a.distance, 0.5); assert!(a.easing.is_none()); }
            Statement::Turn(a) => { assert_eq!(a.degrees, 5.); assert!(!a.absolute); assert!(a.easing.is_none()); }
            _ => panic!(),
        }
    }
}

#[test]
fn explicit_animation_range_roundtrips_with_quoted_clip_speed_and_resume_owner() {
    for trajectory in ["", " rewind from 1.2 to 0.3 in 4 seconds ease in \"cubic\" ()"] {
    for suffix in ["[1.5-75%]", "[\"Section-A\"]"] {
    let mut world = world();
    let (_server, _, client) = joins::tests::connection();
    world.insert_resource(Multiplayer { client: Some(client), ..default() });
    let p = scenemax_parser::parse_program(&format!("subject.\"Long take\"{suffix} at speed of rate{trajectory} async")).unwrap();
    let statement = scenemax_runtime_script_core::substitute_statement(&p.statements[0], &HashMap::from([("rate".into(), "speed".into()), ("subject".into(), "instance".into())]));
    let mut vars = SceneMaxVars::default(); vars.0.insert("speed".into(), 0.5);
    let lease = capture(&mut world.commands(), &statement, &mut vars, None, &HashMap::new(), &HashMap::new());
    assert!(lease.is_some()); world.flush();
    let mut net = world.remove_resource::<Multiplayer>().unwrap();
    let (target, source, _) = net.pending.pop().unwrap();
    assert_eq!(target, "instance");
    assert_eq!(source, format!("{{network_entity}}.\"Long take\"{suffix} at speed of 0.5{trajectory}"));
    let action = action_record(&source, 8).unwrap(); assert_eq!(action.slot,3);
    net.remotes.insert(101, "remote".into());
    let entity = world.spawn((SceneMaxEntity { name: "remote".into(), runtime_name: "remote".into() }, Transform::default())).id();
    queue_action(&mut net,101,action.clone(),Some((800,2000)));
    let received=net.remote_pending[0].received_at;
    dispatch_remote(&mut world,&mut net,&HashMap::from([("remote".into(),(entity,Transform::default()))]),received+Duration::from_millis(100));
    let queue=world.resource::<DelayedActionQueue>();
    let Statement::Animate(remote)=&queue.actions[0].actions[0] else { panic!(); };
    let Statement::Animate(local)=&statement else { panic!(); };
    assert_eq!(remote.rewind,local.rewind); assert_eq!(remote.frame_range_name,local.frame_range_name); assert_eq!(remote.frame_range,local.frame_range); assert_eq!(remote.speed,0.5);
    let resume=world.get::<NetworkAnimationResume>(entity).unwrap(); assert!((resume.remaining_seconds-0.7).abs()<0.00001);
    assert_eq!(queue.actions[0].owner,Some(resume.owner.clone()));
    net.replay.end(101,3,8); controllers::prune(&mut world,&net.replay);
    assert!(world.get::<NetworkAnimationResume>(entity).is_none());
    assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    receive_action(&mut net,101,action); assert!(net.remote_pending.is_empty());
    }
    }
}

#[test]
fn startup_controller_alias_binds_local_playback_and_network_command_to_same_entity() {
    for endpoint_available in [false, true] {
        let mut world = world();
        let (_server, _, client) = super::joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let entity = world.spawn(SceneMaxEntity {
            name: "instance_7".into(), runtime_name: "instance_7".into(),
        }).id();
        let mut pools = SceneMaxObjectPools::default();
        pools.aliases.insert("selected".into(), "instance_7".into());
        let mut assets = SceneMaxRuntimeAssets::default();
        let mut vars = SceneMaxVars::default();
        vars.0.insert("rate".into(), 1.5);
        let program = scenemax_parser::parse_program(
            "control = animation selected.take[25%-75%] at speed of rate\ncontrol.run\nmarker = 1"
        ).unwrap();
        let gltfs = if endpoint_available {
            HashMap::from([("instance_7".into(), Handle::default())])
        } else { HashMap::new() };
        let mut delayed = DelayedActionQueue::default();
        apply_startup_runs(
            &program, &mut world.commands(), &mut vars, &mut pools,
            &mut SceneMaxCameraSystem::default(), &mut assets, &mut delayed,
            &mut SceneMaxUiActionQueue::default(), &HashMap::new(),
            &HashMap::from([("instance_7".into(), entity)]), &mut HashMap::new(),
            &gltfs, &HashMap::new(), &mut SceneMaxColliderBounds::default(),
        );
        // Deferred command capture must retain the endpoint selected at run time.
        pools.aliases.insert("selected".into(), "replacement".into());
        world.flush();
        let net = world.resource::<Multiplayer>();
        if endpoint_available {
            let request = world.get::<AnimationToPlay>(entity).unwrap();
            assert_eq!(request.speed, 1.5);
            assert_eq!(request.frame_range.unwrap().start.value, 25.);
            assert_eq!(net.pending.len(), 1);
            let (target, command, lifetime) = &net.pending[0];
            assert_eq!(target, "instance_7");
            assert!(command.contains("[25%-75%]"));
            assert!(command.contains("1.5"));
            assert!(lifetime.as_ref().unwrap().ptr_eq(&std::sync::Arc::downgrade(request.network_lifetime.as_ref().unwrap())));
            assert!(assets.animation_controllers_by_name["control"].running);
        } else {
            assert!(net.pending.is_empty());
            assert!(world.get::<AnimationToPlay>(entity).is_none());
            assert!(!assets.animation_controllers_by_name["control"].running);
        }
    }
}

#[test]
fn structural_joint_references_resolve_scoped_objects_and_keep_joint_names() {
    let mut scope = SceneMaxScopeFrame::default();
    scope.aliases.insert("selected".into(), "instance_7".into());
    for source in [
        "fixture.attach to selected.anchor: pos (1,2,3)",
        "fixture.ik.contact.target = selected.anchor",
        "fixture.ik.contact.play: target selected.anchor, weight 0.5",
        "fixture.ik.contact.target = selected.\"anchor Ω\"",
    ] {
        let statement = scenemax_parser::parse_program(source).unwrap().statements.remove(0);
        let (_, wire) = replication::encode(&statement, &SceneMaxVars::default(), Some(&scope), &HashMap::new(), &HashMap::new()).unwrap();
        assert!(wire.contains("{network_entity:instance_7}.\"anchor"),"{wire}");
        let resolved = replication::references(&wire,"{network_entity:",|name| {
            assert_eq!(name,"instance_7"); Some("remote_parent".into())
        }).unwrap();
        let resolved = replication::references(&resolved,"{network_entity}", |_|Some("remote_child".into())).unwrap();
        let parsed = scenemax_parser::parse_program(&resolved).unwrap();
        let reference = match &parsed.statements[0] {
            Statement::Attach(a) => a.subject.as_str(),
            Statement::Ik(a) => a.target.as_deref().unwrap(),
            _ => panic!("wrong command"),
        };
        let (object, joint) = crate::ik::split_target(reference);
        assert_eq!(object,"remote_parent");
        assert_eq!(joint.as_deref(),Some(if source.contains('Ω') {"anchor Ω"} else {"anchor"}));
    }
}

#[test]
fn quoted_placeholders_are_literal_and_real_missing_dependencies_still_wait() {
    let mut world = world();
    let mut net = Multiplayer::default();
    net.remotes.insert(1,"remote_child".into());
    let child = world.spawn_empty().id();
    let parent = world.spawn_empty().id();
    let mut entities = HashMap::from([("remote_child".into(),(child,Transform::default()))]);
    let joint = "anchor Ω {network_entity} {network_entity_id:99}";
    let command = format!("{{network_entity}}.ik.contact.target = {{network_entity_id:2}}.{}",serde_json::to_string(joint).unwrap());
    queue_remote(&mut net,1,command);
    dispatch_remote(&mut world,&mut net,&entities,Instant::now());
    assert_eq!(net.remote_pending.len(),1);
    assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    net.remotes.insert(2,"remote_parent".into());
    entities.insert("remote_parent".into(),(parent,Transform::default()));
    dispatch_remote(&mut world,&mut net,&entities,Instant::now());
    assert!(net.remote_pending.is_empty());
    let queue = world.resource::<DelayedActionQueue>();
    let Statement::Ik(a) = &queue.actions[0].actions[0] else {panic!()};
    assert_eq!(a.owner,"remote_child");
    assert_eq!(crate::ik::split_target(a.target.as_deref().unwrap()),("remote_parent",Some(joint.into())));
    let literal = r#""escaped \" {network_entity_id:99} \\ Ω" {network_entity_id:2}"#;
    let rewritten = replication::references(literal,"{network_entity_id:",|id| {
        assert_eq!(id,"2");Some("resolved".into())
    }).unwrap();
    assert!(rewritten.contains("{network_entity_id:99}"));
    assert!(rewritten.ends_with(" resolved"));
    assert!(replication::references("{network_entity_id:missing}","{network_entity_id:", |_|None).is_none());
    assert!(replication::references("{network_entity_id:missing","{network_entity_id:", |_|Some("x".into())).is_none());
}

#[test]
fn animation_names_and_ranges_keep_literal_placeholders_through_speed_updates() {
    let p = scenemax_parser::parse_program(r#"subject."take{network_entity}"["part{network_entity:other}"] at speed of 1"#).unwrap();
    let Statement::Animate(a) = &p.statements[0] else {panic!()};
    let wire = local::animation_command(a,1.);
    assert!(wire.contains(".\"take{network_entity}\""));
    let changed = local::with_speed(&wire,2.,false).unwrap();
    assert!(action_record(&changed,1).is_some());
    let mut world = world();
    let mut net = Multiplayer::default();net.remotes.insert(1,"remote".into());
    remote_command(&mut world,&net,1,&changed,None);
    let queue = world.resource::<DelayedActionQueue>();
    let Statement::Animate(a) = &queue.actions[0].actions[0] else {panic!()};
    assert_eq!(a.target,"remote");assert_eq!(a.clip,"take{network_entity}");
    assert_eq!(a.frame_range_name.as_deref(),Some("part{network_entity:other}"));
    assert_eq!(a.speed,2.);
}
#[test]
fn resource_commands_capture_scoped_values_and_preserve_quoted_literals() {
    let mut vars = SceneMaxVars::default();
    vars.1.insert("choice".into(), serde_json::json!("global"));
    let mut scope = SceneMaxScopeFrame::default();
    scope.typed.insert("choice".into(), serde_json::json!("finish Ω {network_entity}"));
    scope.aliases.insert("selected".into(), "instance".into());
    for property in ["material", "ik"] {
        for (expression, expected) in [("choice", "finish Ω {network_entity}"), ("\"choice\"", "choice"), ("unbound", "unbound"), ("\"pre\" + \"fix\"", "prefix")] {
            let statement = scenemax_parser::parse_program(&format!("selected.{property} = {expression}")).unwrap().statements.remove(0);
            let statement = scenemax_runtime_script_core::substitute_statement(&statement, &HashMap::from([("choice".into(), "choice".into())]));
            let mut world = world();
            let (_server, _, client) = joins::tests::connection();
            world.insert_resource(Multiplayer { client: Some(client), ..default() });
            capture(&mut world.commands(), &statement, &mut vars, Some(&scope), &HashMap::new(), &HashMap::new());
            world.flush();
            if let Statement::SetMaterial(a) = &statement {
                assert!(world.resource::<Multiplayer>().pending.is_empty());
                let entity = world.spawn(SceneMaxEntity { name: "instance".into(), runtime_name: "instance".into() }).id();
                let value = resolve_shader_name(&a.material, &vars, Some(&scope), &HashMap::new(), Some(&HashMap::new()), None);
                material_applied(&mut world, entity, value);
            }
            let mut net = world.remove_resource::<Multiplayer>().unwrap();
            let (target, wire, _) = net.pending.pop().unwrap();
            assert_eq!(target, "instance");
            net.remotes.insert(1, "remote".into());
            remote_command(&mut world, &net, 1, &wire, None);
            let queue = world.resource::<DelayedActionQueue>();
            let value = match &queue.actions[0].actions[0] {
                Statement::SetMaterial(a) => &a.material,
                Statement::Ik(a) => match &a.action { scenemax_parser::ik::IkAction::Apply(v) => v, _ => panic!() },
                _ => panic!(),
            };
            assert!(matches!(value, AssignmentValue::Literal(serde_json::Value::String(v)) if v == expected));
        }
    }
}
#[test]
fn startup_resource_commands_apply_to_the_same_pool_alias_as_wire_capture() {
    for property in ["material", "shader"] {
        let mut world = world();
        let (_server, _, client) = joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let binding = || scenemax_materials::MaterialBinding { material: Handle::default(), slot: String::new() };
        let selected = world.spawn((binding(), SceneMaxEntity { name: "instance".into(), runtime_name: "instance".into() })).id();
        world.entity_mut(selected).insert(crate::shader::SceneMaxOriginalMaterial(Handle::default()));
        let unrelated = world.spawn(binding()).id();
        let mut pools = SceneMaxObjectPools::default();
        pools.aliases.insert("selected".into(), "instance".into());
        let program = scenemax_parser::parse_program(&format!("selected.{property} = \"\"")).unwrap();
        apply_startup_runs(
            &program, &mut world.commands(), &mut SceneMaxVars::default(), &mut pools,
            &mut SceneMaxCameraSystem::default(), &mut SceneMaxRuntimeAssets::default(),
            &mut DelayedActionQueue::default(), &mut SceneMaxUiActionQueue::default(),
            &HashMap::new(), &HashMap::from([("instance".into(), selected), ("selected".into(), unrelated)]),
            &mut HashMap::new(), &HashMap::new(), &HashMap::new(), &mut SceneMaxColliderBounds::default(),
        );
        world.flush();
        assert!(world.get::<scenemax_materials::MaterialBinding>(selected).is_none());
        assert!(world.get::<scenemax_materials::MaterialBinding>(unrelated).is_some());
        if property == "material" {
            assert_eq!(world.resource::<Multiplayer>().pending[0].0, "instance");
        }
    }
}
#[test]
fn runtime_resource_commands_prefer_pending_entities_over_stale_query_matches() {
    use bevy::ecs::system::RunSystemOnce;
    for property in ["material", "shader"] {
        let mut world = world();
        let old = world.spawn((SceneMaxEntity { name: "instance".into(), runtime_name: "instance".into() }, Transform::default(),
            scenemax_materials::MaterialBinding { material: Handle::default(), slot: "old".into() })).id();
        world.run_system_once(move |mut commands: Commands, mut entities: ParamSet<(
            Query<(Entity, &SceneMaxEntity, &Transform, Option<&GlobalTransform>, Option<&ChildOf>)>,
            Query<(Entity, &SceneMaxEntity, &mut Transform, Option<&SceneMaxGltf>, Option<&CurrentAnimation>, Option<&mut Visibility>, Option<&SceneMaxCharacterController>, Option<&mut SceneMaxCharacterMotor>)>,
        )>| {
            let pending = commands.spawn((SceneMaxEntity { name: "instance".into(), runtime_name: "instance".into() }, Transform::default(),
                scenemax_materials::MaterialBinding { material: Handle::default(), slot: "new".into() },
                crate::shader::SceneMaxOriginalMaterial(Handle::default()))).id();
            let mut declared = HashMap::from([("instance".into(), pending)]);
            let mut pools = SceneMaxObjectPools::default();
            pools.aliases.insert("selected".into(), "wrong".into());
            let mut scope = SceneMaxScopeFrame::default();
            scope.aliases.insert("selected".into(), "instance".into());
            let statement = scenemax_parser::parse_program(&format!("selected.{property} = \"\"")).unwrap().statements.remove(0);
            apply_key_action(&statement, &mut HashMap::new(), &mut SceneMaxVars::default(), &mut pools,
                None, &HashMap::new(), &HashMap::new(), &mut HashMap::new(), &mut SceneMaxRuntimeAssets::default(),
                &SceneMaxAnimationDurations::default(), &mut SceneMaxColliderBounds::default(), None, None, None,
                Some(&mut scope), None, &mut commands, &mut entities, &mut declared);
        }).unwrap();
        assert!(world.get::<scenemax_materials::MaterialBinding>(old).is_some());
        let all = world.query::<(Entity, &SceneMaxEntity, Option<&scenemax_materials::MaterialBinding>)>()
            .iter(&world).map(|(e, _, binding)| (e, binding.is_some())).collect::<Vec<_>>();
        assert_eq!(all.len(), 2);
        assert!(all.iter().any(|(e, bound)| *e != old && !bound));
    }
}
#[test]
fn shader_failure_preserves_material_and_only_successful_local_resets_publish() {
    for expression in [r#""missing_fixture_shader""#, r#""""#, "null", "choice"] {
        for remote in [false, true] {
            let mut world = world();
            let mut net = Multiplayer::default();
            if remote { net.remotes.insert(1, "instance".into()); }
            world.insert_resource(net);
            let entity = world.spawn((
                SceneMaxEntity { name: "instance".into(), runtime_name: "instance".into() },
                scenemax_materials::MaterialBinding { material: Handle::default(), slot: "original".into() },
            )).id();
            let mut vars = SceneMaxVars::default();
            vars.1.insert("choice".into(), serde_json::Value::Null);
            let mut pools = SceneMaxObjectPools::default();
            pools.aliases.insert("selected".into(), "instance".into());
            let program = scenemax_parser::parse_program(&format!("selected.shader = {expression}")).unwrap();
            apply_startup_runs(
                &program, &mut world.commands(), &mut vars, &mut pools,
                &mut SceneMaxCameraSystem::default(), &mut SceneMaxRuntimeAssets::default(),
                &mut DelayedActionQueue::default(), &mut SceneMaxUiActionQueue::default(),
                &HashMap::new(), &HashMap::from([("instance".into(), entity)]),
                &mut HashMap::new(), &HashMap::new(), &HashMap::new(), &mut SceneMaxColliderBounds::default(),
            );
            assert!(world.resource::<Multiplayer>().entity_updates.is_empty());
            world.flush();
            let failed = expression.contains("missing");
            // Reset before any successful shader is a no-op for native material.
            assert!(world.get::<scenemax_materials::MaterialBinding>(entity).is_some());
            let net = world.resource::<Multiplayer>();
            assert_eq!(net.pending.len(), usize::from(!failed && !remote));
            if !failed && !remote {
                assert_eq!(net.pending[0].1, r#"{network_entity}.shader = """#);
            }
            assert!(net.entity_updates.is_empty());
        }
    }
}
#[test]
fn shader_publication_follows_mesh_application_and_preserves_literal_names() {
    let root = tempfile::tempdir().unwrap();
    scenemax_assets::filesystem::create_dir(root.path().join("scripts")).unwrap();
    scenemax_assets::filesystem::write(root.path().join("scripts/finish Ω.bvshader"), r#"{"mainColor":[1,0,0,1]}"#).unwrap();
    for (expression, has_mesh, succeeds) in [("choice", true, true), ("choice", false, false), (r#""choice""#, true, false)] {
        let mut world = world();
        world.insert_resource(Multiplayer::default());
        world.init_resource::<Assets<StandardMaterial>>();
        let original = world.resource_mut::<Assets<StandardMaterial>>().add(StandardMaterial::default());
        let entity = world.spawn((
            SceneMaxEntity { name: "instance".into(), runtime_name: "instance".into() },
            scenemax_materials::MaterialBinding { material: original.clone(), slot: "original".into() },
        )).id();
        if has_mesh { world.entity_mut(entity).insert(MeshMaterial3d(original.clone())); }
        let mut vars = SceneMaxVars::default();
        vars.1.insert("choice".into(), serde_json::json!("finish Ω"));
        let mut assets = SceneMaxRuntimeAssets { asset_root: Some(root.path().join("resources")), ..default() };
        let program = scenemax_parser::parse_program(&format!("instance.shader = {expression}")).unwrap();
        apply_startup_runs(
            &program, &mut world.commands(), &mut vars, &mut SceneMaxObjectPools::default(),
            &mut SceneMaxCameraSystem::default(), &mut assets,
            &mut DelayedActionQueue::default(), &mut SceneMaxUiActionQueue::default(),
            &HashMap::new(), &HashMap::from([("instance".into(), entity)]),
            &mut HashMap::new(), &HashMap::new(), &HashMap::new(), &mut SceneMaxColliderBounds::default(),
        );
        vars.1.insert("choice".into(), serde_json::json!("later"));
        world.flush();
        assert_eq!(world.get::<scenemax_materials::MaterialBinding>(entity).is_none(), succeeds);
        let updates = &world.resource::<Multiplayer>().pending;
        assert_eq!(updates.len(), usize::from(succeeds));
        if succeeds {
            assert_eq!(updates[0].1, r#"{network_entity}.shader = "finish Ω""#);
            assert_ne!(world.get::<MeshMaterial3d<StandardMaterial>>(entity).unwrap().0, original);
        } else if has_mesh {
            assert_eq!(world.get::<MeshMaterial3d<StandardMaterial>>(entity).unwrap().0, original);
        }
    }
}
#[test]
fn received_null_shader_data_replays_as_a_reset() {
    let mut world = world();
    let mut net = Multiplayer::default();
    net.remotes.insert(1, "remote".into());
    let entity = world.spawn_empty().id();
    events::receive(&mut world, &mut net, scenemax_multiplayer::protocol::Incoming::EntityData {
        entity: 1, field: "shader".into(), value: serde_json::Value::Null,
    });
    dispatch_remote(&mut world, &mut net, &HashMap::from([("remote".into(), (entity, Transform::default()))]), Instant::now());
    let queue = world.resource::<DelayedActionQueue>();
    assert!(matches!(&queue.actions[0].actions[0], Statement::SetShader(a)
        if a.target == "remote" && a.shader == AssignmentValue::Literal(serde_json::json!(""))));
}

#[test]
fn mixed_surface_publications_share_execution_order_and_keep_wire_types() {
    for shader_first in [false, true] {
        let mut world = world();
        world.insert_resource(Multiplayer::default());
        let entity = world.spawn(SceneMaxEntity { name: "instance".into(), runtime_name: "instance".into() }).id();
        if shader_first {
            shader_applied(&mut world, entity, "base".into());
            material_applied(&mut world, entity, "override".into());
        } else {
            material_applied(&mut world, entity, "override".into());
            shader_applied(&mut world, entity, "base".into());
        }
        let mut sequences = HashMap::new();
        let mut net = world.resource_mut::<Multiplayer>();
        assert!(net.entity_updates.is_empty());
        let sent: Vec<_> = net.pending.drain(..).map(|(_, command, _)| local_command(&mut sequences, 1, command)).collect();
        let shader_index = usize::from(!shader_first);
        assert!(matches!(&sent[shader_index], Outgoing::EntityData { field, value, .. }
            if field == "shader" && value == "base"));
        assert!(matches!(&sent[1-shader_index], Outgoing::ActionStart { action, .. }
            if action.slot == 251 && action.command.contains("override")));
    }
}

#[test]
fn remote_surface_history_waits_for_gltf_scene_descendants() {
    let mut world = world();
    let root = world.spawn(SceneMaxGltf { gltf: Handle::default(), animation_names: Vec::new() }).id();
    let mut net = Multiplayer::default();
    net.remotes.insert(1, "remote".into());
    let history = "{network_entity}.material = \"painted\"\n{network_entity}.shader = \"textured\"";
    queue_remote(&mut net, 1, history.into());
    queue_remote(&mut net, 1, "{network_entity}.shader = null".into());
    let entities = HashMap::from([("remote".into(), (root, Transform::default()))]);
    dispatch_remote(&mut world, &mut net, &entities, Instant::now());
    assert_eq!(net.remote_pending.len(), 2);
    assert_eq!(net.remote_pending[0].command, history);
    assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    assert!(surface_replay_ready(&world, root, "{network_entity}.pos = (1,2,3)"));
    let primitive = world.spawn_empty().id();
    assert!(surface_replay_ready(&world, primitive, history));
    net.replay.destroy(1);
    dispatch_remote(&mut world, &mut net, &entities, Instant::now());
    assert!(net.remote_pending.is_empty());
}
#[test]
fn null_ik_assets_do_not_replace_existing_rigs_or_emit_commands() {
    let root = tempfile::tempdir().unwrap();
    scenemax_assets::filesystem::create_dir(root.path().join("ik")).unwrap();
    scenemax_assets::filesystem::write(root.path().join("ik/null.smik"), r#"{"id":"null","layers":[]}"#).unwrap();
    for expression in ["null", "choice", r#""null""#] {
        let mut world = world();
        let (_server, _, client) = joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let before = serde_json::json!({"id":"original","layers":[]});
        let entity = world.spawn(scenemax_ik::Rig::new(before.clone())).id();
        let mut vars = SceneMaxVars::default();
        vars.1.insert("choice".into(), serde_json::Value::Null);
        let mut assets = SceneMaxRuntimeAssets { asset_root: Some(root.path().into()), ..default() };
        let program = scenemax_parser::parse_program(&format!("subject.ik = {expression}")).unwrap();
        apply_startup_runs(
            &program, &mut world.commands(), &mut vars, &mut SceneMaxObjectPools::default(),
            &mut SceneMaxCameraSystem::default(), &mut assets,
            &mut DelayedActionQueue::default(), &mut SceneMaxUiActionQueue::default(),
            &HashMap::new(), &HashMap::from([("subject".into(), entity)]),
            &mut HashMap::new(), &HashMap::new(), &HashMap::new(), &mut SceneMaxColliderBounds::default(),
        );
        world.flush();
        let quoted = expression.starts_with('"');
        assert_eq!(world.resource::<Multiplayer>().pending.len(), usize::from(quoted));
        assert_eq!(world.get::<scenemax_ik::Rig>(entity).unwrap().definition["id"], if quoted { "null" } else { "original" });
    }
    let p = scenemax_parser::parse_program("subject.ik = choice").unwrap();
    let mut scope = SceneMaxScopeFrame::default();
    scope.typed.insert("choice".into(), serde_json::Value::Null);
    let mut vars = SceneMaxVars::default();
    vars.1.insert("choice".into(), serde_json::json!("global_asset"));
    assert!(replication::encode(&p.statements[0], &vars, Some(&scope), &HashMap::new(), &HashMap::new()).is_none());
}
#[test]
fn remote_primitive_matrix_matches_startup_meshes_and_materials() {
    use bevy::asset::{AssetApp, AssetPlugin};
    use bevy::mesh::VertexAttributeValues;
    for resource in [
        "box: size (2,4,6)", "sphere: radius 2.5",
        "cylinder: radius (1,2), height 3",
        "hollow cylinder: radius (2,3), inner radius (0.5,1), height 4",
        "quad: size (3,5)", "wedge: size (2,3,4)",
        "cone: radius (0.5,2), height 3",
        "stairs: size (3,0.5,1), steps 4",
        "arch: size (4,5,2), thickness 0.4, segments 8",
    ] {
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, AssetPlugin::default()))
            .init_asset::<Mesh>().init_asset::<StandardMaterial>();
        let world = app.world_mut();
        world.init_resource::<SceneMaxVars>();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        world.init_resource::<DelayedActionQueue>();
        let source = format!("local => {resource}, multiplayer, pos (1,2,3), scale 2");
        let Statement::ModelDecl { resource: kind, options, .. } =
            scenemax_parser::parse_program(&source).unwrap().statements.remove(0) else { panic!("{source}") };
        let server = world.resource::<AssetServer>().clone();
        let expected = world.resource_scope(|world, mut meshes: Mut<Assets<Mesh>>| {
            let mut materials = world.resource_mut::<Assets<StandardMaterial>>();
            primitive_mesh(&options, &kind, &server, std::path::Path::new(""), None, &mut meshes, &mut materials).unwrap()
        });
        let mut net = Multiplayer::default();
        remote_spawn(world, &mut net, 1, Spawn {
            archetype: kind, player: "fixture".into(), source_name: "local".into(),
            command: format!("{{network_entity}} => {}", options.multiplayer_source.unwrap()),
        });
        let actions = world.resource_mut::<DelayedActionQueue>().actions.drain(..)
            .flat_map(|item| item.actions).collect();
        crate::pool_lifecycle::tests::execute_batch(world, actions);
        let (actual_mesh, actual_material, transform) = world.query::<(&Mesh3d, &MeshMaterial3d<StandardMaterial>, &Transform)>()
            .single(world).unwrap();
        assert_eq!(transform.translation, Vec3::new(1.,2.,3.), "{resource}");
        assert_eq!(transform.scale, Vec3::splat(2.), "{resource}");
        let meshes = world.resource::<Assets<Mesh>>();
        let expected_mesh = meshes.get(&expected.0.0).unwrap();
        let actual_mesh = meshes.get(&actual_mesh.0).unwrap();
        for attribute in [Mesh::ATTRIBUTE_POSITION, Mesh::ATTRIBUTE_NORMAL] {
            let Some(VertexAttributeValues::Float32x3(expected)) = expected_mesh.attribute(attribute) else { panic!() };
            let Some(VertexAttributeValues::Float32x3(actual)) = actual_mesh.attribute(attribute) else { panic!() };
            assert_eq!(expected, actual, "{resource}");
        }
        assert_eq!(expected_mesh.indices().unwrap().iter().collect::<Vec<_>>(),
            actual_mesh.indices().unwrap().iter().collect::<Vec<_>>(), "{resource}");
        let materials = world.resource::<Assets<StandardMaterial>>();
        assert_eq!(materials.get(&expected.1.0).unwrap().base_color,
            materials.get(&actual_material.0).unwrap().base_color, "{resource}");
    }
}
#[test]
fn remote_collider_declarations_remain_hidden_sensors_without_visual_meshes() {
    for resource in ["box: size (2,4,6)", "sphere: radius 2"] {
        let mut world = world();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        let mut net = Multiplayer::default();
        remote_spawn(&mut world, &mut net, 1, Spawn {
            archetype: resource.split(':').next().unwrap().into(),
            player: "fixture".into(), source_name: "hitbox".into(),
            command: format!("{{network_entity}} => {resource}, collider, multiplayer, pos (1,2,3)"),
        });
        let actions = world.resource_mut::<DelayedActionQueue>().actions.drain(..)
            .flat_map(|item| item.actions).collect();
        crate::pool_lifecycle::tests::execute_batch(&mut world, actions);
        let (entity, transform, visibility) = world.query::<(Entity, &Transform, &Visibility)>()
            .single(&world).unwrap();
        assert_eq!(*visibility, Visibility::Hidden);
        assert_eq!(transform.translation, Vec3::new(1.,2.,3.));
        assert!(world.get::<Sensor>(entity).is_some());
        assert!(world.get::<AvianCollider>(entity).is_some());
        assert!(world.get::<Mesh3d>(entity).is_none());
    }
}
#[test]
fn entity_registration_sends_materialized_transform_instead_of_sender_expressions() {
    let (server, peer, mut client) = joins::tests::connection();
    joins::tests::accept(&server, peer, &mut client, 1000, "initial");
    let mut world = world();
    world.insert_resource(Multiplayer { client: Some(client), ..default() });
    world.spawn((SceneMaxEntity { name: "subject".into(), runtime_name: "subject".into() },
        Transform::from_scale(Vec3::splat(3.5)).with_translation(Vec3::new(7.,8.,9.))));
    let Statement::ModelDecl { resource, options, .. } = scenemax_parser::parse_program(
        "subject => box: multiplayer, size (2,4,6), pos (owner_position(),2,3), scale sender_scale + 1"
    ).unwrap().statements.remove(0) else { panic!() };
    declaration(&mut world.commands(), "subject", &resource, &options);
    world.flush();
    update(&mut world);
    let mut bytes = [0; 1200];
    let count = loop {
        let (count, _) = server.recv_from(&mut bytes).unwrap();
        if bytes[5] == scenemax_multiplayer::CREATE_ENTITY_REQUEST { break count; }
    };
    let text = String::from_utf8_lossy(&bytes[..count]);
    let start = text.find("{network_entity} =>").unwrap();
    let source = text[start..].split('\0').next().unwrap();
    assert!(!source.contains("sender_scale") && !source.contains("owner_position"), "{source}");
    let source = source.replace("{network_entity}", "remote");
    let Statement::ModelDecl { options, .. } = scenemax_parser::parse_program(&source).unwrap().statements.remove(0) else { panic!() };
    assert_eq!(options.scale, Some(SceneMaxVec3 { x:3.5, y:3.5, z:3.5 }));
    assert!(options.scale_value.is_none());
    assert!(options.position_value.is_none());
    assert_eq!(options.position, Some(SceneMaxVec3 { x:7., y:8., z:9. }));
    assert_eq!(options.size, Some(SceneMaxVec3 { x:2., y:4., z:6. }));
}
#[test]
fn label_registration_preserves_materialized_scale_and_literal_text() {
    for scale in [Vec3::splat(0.75), Vec3::new(-2.,3.,4.), Vec3::ONE] {
        let (server, peer, mut client) = joins::tests::connection();
        joins::tests::accept(&server, peer, &mut client, 1000, "initial");
        let mut world = world();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        let Statement::ModelDecl { resource, options, .. } = scenemax_parser::parse_program(
            r#"caption => label: multiplayer, text "scale sender_value {network_entity}", scale sender_value"#
        ).unwrap().statements.remove(0) else { panic!() };
        crate::labels::spawn(&mut world.commands(), "caption", &options, Transform::from_scale(scale),
            &SceneMaxVars::default(), &HashMap::new());
        declaration(&mut world.commands(), "caption", &resource, &options);
        world.flush();
        update(&mut world);
        let mut bytes = [0; 1200];
        let count = loop {
            let (count, _) = server.recv_from(&mut bytes).unwrap();
            if bytes[5] == scenemax_multiplayer::CREATE_ENTITY_REQUEST { break count; }
        };
        let text = String::from_utf8_lossy(&bytes[..count]);
        let source = text[text.find("{network_entity} =>").unwrap()..].split('\0').next().unwrap();
        let source = replication::references(source, "{network_entity}", |_| Some("remote".into())).unwrap();
        let Statement::ModelDecl { options, .. } = scenemax_parser::parse_program(&source).unwrap().statements.remove(0) else { panic!() };
        assert_eq!(transform_from_options(&options, None).scale, scale);
        assert!(options.scale_value.is_none());
        assert_eq!(options.label.unwrap().text, Some(AssignmentValue::Literal(
            serde_json::json!("scale sender_value {network_entity}"))));
    }
}

#[test]
fn attachment_offsets_use_function_parameters_and_scope_then_emit_only_numbers() {
    let source = scenemax_parser::parse_program("child.attach to anchor: pos (amount + 1,height,-amount)").unwrap();
    let statement = scenemax_runtime_script_core::substitute_statement(&source.statements[0],
        &HashMap::from([("amount".into(), "2".into())]));
    let mut scope = SceneMaxScopeFrame::default();
    scope.vars.insert("height".into(), 4.);
    scope.aliases.insert("anchor".into(), "resolved_anchor".into());
    let (_, wire) = replication::encode(&statement, &SceneMaxVars::default(), Some(&scope),
        &HashMap::new(), &HashMap::new()).unwrap();
    assert_eq!(wire, "{network_entity}.attach to {network_entity:resolved_anchor}: pos (3,4,-2)");
    let decoded = wire.replace("{network_entity:resolved_anchor}", "remote_anchor")
        .replace("{network_entity}", "remote_child");
    let program = scenemax_parser::parse_program(&decoded).unwrap();
    let Statement::Attach(attach) = &program.statements[0] else { panic!() };
    assert!(attach.offset_value.is_none());
    assert_eq!(attach.offset, SceneMaxVec3 { x:3., y:4., z:-2. });
    assert!(replication::encode(&source.statements[0], &SceneMaxVars::default(), None,
        &HashMap::new(), &HashMap::new()).is_none());
}


#[test]
fn scoped_position_operands_freeze_numeric_wire_values_and_follow_rebinding() {
    let mut vars=SceneMaxVars::default();
    let mut scope=SceneMaxScopeFrame::default();
    scope.aliases.insert("anchor".into(), "stale".into());
    let transforms=HashMap::from([
        ("stale".into(),Transform::from_xyz(90.,90.,90.)),
        ("first".into(),Transform::from_xyz(1.,2.,3.)),
        ("second".into(),Transform::from_xyz(4.,5.,6.)),
        ("first.\"socket\"".into(),Transform::from_xyz(7.,8.,9.)),
    ]);
    for (name,expected) in [("first","(1,2,3)"),("second","(4,5,6)")] {
        scenemax_runtime_vm_core::typed::assign("anchor",serde_json::json!(name),&mut vars,Some(&mut scope),true);
        for source in ["subject.pos (anchor)", "subject.pos (anchor.x,anchor.y,anchor.z)",
            "subject.move to (anchor) in 1 seconds", "subject.move to (anchor.x,anchor.y,anchor.z) in 1 seconds",
            "subject.play pos (anchor), attr = [\"loop\" 1] async"] {
            let action=scenemax_parser::parse_program(source).unwrap().statements.remove(0);
            let (_,command)=replication::encode(&action,&vars,Some(&scope),&HashMap::new(),&transforms).unwrap_or_else(|| panic!("{source}: {action:?}"));
            assert!(command.contains(expected),"{source}: {command}");
            assert!(!command.contains("anchor"),"{command}");
        }
    }
    scenemax_runtime_vm_core::typed::assign("anchor",serde_json::json!("first"),&mut vars,Some(&mut scope),true);
    let action=scenemax_parser::parse_program("subject.pos (anchor.\"socket\")").unwrap().statements.remove(0);
    assert!(replication::encode(&action,&vars,Some(&scope),&HashMap::new(),&transforms).unwrap().1.contains("(7,8,9)"));
    scenemax_runtime_vm_core::typed::assign("anchor",serde_json::json!("missing"),&mut vars,Some(&mut scope),true);
    assert!(replication::encode(&action,&vars,Some(&scope),&HashMap::new(),&transforms).is_none());
}
