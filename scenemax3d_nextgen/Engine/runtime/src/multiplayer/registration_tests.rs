use super::*;

#[test]
fn registration_continuation_waits_for_ack_and_publishes_id_before_resuming() {
    let (server, peer, mut client) = joins::tests::connection();
    joins::tests::accept(&server, peer, &mut client, 1000, "fixture");
    let request = client
        .register(Spawn {
            archetype: "box".into(),
            player: "fixture".into(),
            source_name: "subject".into(),
            command: "{network_entity} => box".into(),
        })
        .unwrap();
    let mut net = Multiplayer {
        client: Some(client),
        ..Default::default()
    };
    let program =
        scenemax_parser::parse_program("subject => box: multiplayer\nslot = subject.network.id")
            .unwrap();
    let Statement::ModelDecl {
        resource, options, ..
    } = &program.statements[0]
    else {
        panic!()
    };
    net.declarations
        .insert("instance".into(), (resource.clone(), options.clone()));
    net.locals
        .insert("instance".into(), (Entity::PLACEHOLDER, request));
    let mut world = World::new();
    world.init_resource::<SceneMaxVars>();
    let mut scope = SceneMaxScopeFrame::default();
    scope.aliases.insert("subject".into(), "instance".into());
    let continuation = registration_continuation(
        &program.statements[0],
        &program.statements[1..],
        world.resource::<SceneMaxVars>(),
        &SceneMaxObjectPools::default(),
        Some(&scope),
    );
    let Statement::WaitUntil { condition } = &continuation[0] else {
        panic!()
    };
    publish_state(&mut world, &net);
    assert!(!condition_matches(
        condition,
        world.resource::<SceneMaxVars>(),
        &HashMap::new(),
        None,
        None
    ));
    let mut packet = Vec::new();
    scenemax_multiplayer::write_header(
        scenemax_multiplayer::CREATE_ENTITY_ACCEPTED,
        1,
        &mut packet,
    );
    packet.extend(42u32.to_le_bytes());
    packet.extend(request.to_le_bytes());
    server.send_to(&packet, peer).unwrap();
    let deadline = Instant::now() + Duration::from_secs(1);
    while net.client.as_ref().unwrap().network_id(request).is_none() {
        net.client.as_mut().unwrap().poll().unwrap();
        assert!(Instant::now() < deadline);
    }
    publish_state(&mut world, &net);
    assert!(condition_matches(
        condition,
        world.resource::<SceneMaxVars>(),
        &HashMap::new(),
        None,
        None
    ));
    assert_eq!(
        world.resource::<SceneMaxVars>().0["instance.network.id"],
        42.
    );
    assert_eq!(continuation[1], program.statements[1]);
}

#[test]
fn registration_failure_releases_continuation_and_offline_creation_does_not_wait() {
    let (_server, _peer, client) = joins::tests::connection();
    let program = scenemax_parser::parse_program("subject => box: multiplayer").unwrap();
    let Statement::ModelDecl {
        name,
        resource,
        options,
    } = &program.statements[0]
    else {
        panic!()
    };
    let mut world = World::new();
    world.init_resource::<SceneMaxVars>();
    world.init_resource::<Multiplayer>();
    declaration(&mut world.commands(), name, resource, options);
    world.flush();
    assert_eq!(
        world.resource::<SceneMaxVars>().0["subject.network.registrationPending"],
        0.
    );
    let mut net = Multiplayer {
        client: Some(client),
        ..Default::default()
    };
    net.declarations
        .insert(name.clone(), (resource.clone(), options.clone()));
    publish_state(&mut world, &net);
    assert_eq!(
        world.resource::<SceneMaxVars>().0["subject.network.registrationPending"],
        1.
    );
    net.rejected_declarations.insert(name.clone());
    publish_state(&mut world, &net);
    assert_eq!(
        world.resource::<SceneMaxVars>().0["subject.network.registrationPending"],
        0.
    );
}
