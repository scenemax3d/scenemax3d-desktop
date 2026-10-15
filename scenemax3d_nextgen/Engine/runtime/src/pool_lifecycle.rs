//! Lifecycle boundaries for reusable authored entities.
use super::*;

pub(super) fn needs_flush(action: &Statement) -> bool {
    matches!(
        action,
        Statement::ModelDecl { .. } | Statement::Delete { .. } | Statement::PoolRelease(_)
    ) || matches!(action, Statement::Assignment(a) | Statement::LocalAssignment(a) if matches!(a.value, AssignmentValue::PoolAcquire { .. }))
}

pub(super) fn inactive(world: &World) -> HashSet<String> {
    world
        .get_resource::<SceneMaxObjectPools>()
        .map(|pools| {
            pools
                .pools
                .values()
                .flat_map(|pool| pool.members.difference(&pool.in_use).cloned())
                .collect()
        })
        .unwrap_or_default()
}

#[derive(Component)]
struct PooledCollision {
    collider: Option<AvianCollider>,
    layers: Option<CollisionLayers>,
}

pub(super) fn set_visible(commands: &mut Commands, name: &str, visible: bool) {
    let name = name.to_owned();
    commands.queue(move |world: &mut World| {
        let entity = world.query::<(Entity, &SceneMaxEntity)>().iter(world).find_map(|(e,s)| (s.name == name).then_some(e));
        if let Some(entity) = entity {
            if visible {
                if let Some(saved) = world.entity_mut(entity).take::<PooledCollision>() {
                    if let Some(collider) = saved.collider { world.entity_mut(entity).insert(collider); }
                    if let Some(layers) = saved.layers { world.entity_mut(entity).insert(layers); }
                }
            } else if world.get::<PooledCollision>(entity).is_none() {
                let saved = PooledCollision { collider: world.get::<AvianCollider>(entity).cloned(),
                    layers: world.get::<CollisionLayers>(entity).copied() };
                world.entity_mut(entity).insert(saved).insert(CollisionLayers::NONE).remove::<AvianCollider>();
            }
            world.entity_mut(entity).insert((if visible { Visibility::Inherited } else { Visibility::Hidden }, SceneMaxRuntimeVisibility { visible }));
        }
    });
}

pub(super) fn release(commands: &mut Commands, target: &str) {
    set_visible(commands, target, false);
    let target = target.to_owned();
    commands.queue(move |world: &mut World| stop(world, &target));
}

fn stop(world: &mut World, target: &str) {
    let entity = world
        .query::<(Entity, &SceneMaxEntity)>()
        .iter(world)
        .find_map(|(entity, scene)| (scene.name == target).then_some(entity));
    let Some(entity) = entity else {
        return;
    };
    crate::attachments::release_object(world, entity);
    let graphs = world
        .get::<CurrentAnimation>(entity)
        .map(|a| a.playback_graphs.clone())
        .unwrap_or_default();
    for (player, graph) in graphs {
        if world
            .get::<AnimationGraphHandle>(player)
            .is_some_and(|handle| handle.0 == graph)
        {
            if let Some(mut animation) = world.get_mut::<AnimationPlayer>(player) {
                animation.stop_all();
            }
            if let Some(mut transitions) = world.get_mut::<AnimationTransitions>(player) {
                *transitions = AnimationTransitions::new();
            }
        }
    }
    world.entity_mut(entity).remove::<(
        CurrentAnimation,
        AnimationToPlay,
        AnimationSpeedOverride,
        NetworkAnimationResume,
    )>();
    world.entity_mut(entity).remove::<(
        SceneMaxEffekseerPlayback,
        multiplayer::EffectAction,
        NetworkEffectResume,
        PendingCharacterMode,
    )>();
    if let Some(mut motor) = world.get_mut::<SceneMaxCharacterMotor>(entity) {
        *motor = SceneMaxCharacterMotor::default();
    }
    if let Some(mut assets) = world.get_resource_mut::<SceneMaxRuntimeAssets>() {
        for controller in assets
            .animation_controllers_by_name
            .values_mut()
            .filter(|c| c.target == target || c.source.as_ref().is_some_and(|s| s.0 == entity))
        {
            controller.run_lifetime = None;
            controller.running = false;
            controller.advance_pending = false;
            controller.current_run_started = false;
        }
    }
}

pub(super) fn network_scope(
    scope: Option<&SceneMaxScopeFrame>,
    pools: &SceneMaxObjectPools,
) -> Option<SceneMaxScopeFrame> {
    if pools.aliases.is_empty() {
        return None;
    }
    let mut merged = scope.cloned().unwrap_or_default();
    for (alias, target) in &pools.aliases {
        merged
            .aliases
            .entry(alias.clone())
            .or_insert_with(|| target.clone());
    }
    Some(merged)
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;

    pub(crate) fn execute_batch(world: &mut World, statements: Vec<Statement>) {
        world.insert_resource(Batch(statements));
        world.run_system_once(execute).unwrap();
    }

    #[test]
    fn runtime_box_declaration_builds_authored_mesh_dimensions() {
        use bevy::asset::{AssetApp, AssetPlugin};
        use bevy::mesh::VertexAttributeValues;
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, AssetPlugin::default()))
            .init_asset::<Mesh>().init_asset::<StandardMaterial>();
        let world = app.world_mut();
        world.init_resource::<SceneMaxVars>();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        world.init_resource::<DelayedActionQueue>();
        world.insert_resource(Batch(scenemax_parser::parse_program(
            "subject => box: size (2,4,6), pos (1,2,3), scale 2"
        ).unwrap().statements));
        world.run_system_once(execute).unwrap();
        let (mesh, transform) = world.query::<(&Mesh3d, &Transform)>().single(world).unwrap();
        assert_eq!(transform.translation, Vec3::new(1.,2.,3.));
        assert_eq!(transform.scale, Vec3::splat(2.));
        let mesh = world.resource::<Assets<Mesh>>().get(&mesh.0).unwrap();
        let VertexAttributeValues::Float32x3(points) = mesh.attribute(Mesh::ATTRIBUTE_POSITION).unwrap() else { panic!() };
        let min = points.iter().fold(Vec3::splat(f32::INFINITY), |a,p| a.min(Vec3::from_array(*p)));
        let max = points.iter().fold(Vec3::splat(f32::NEG_INFINITY), |a,p| a.max(Vec3::from_array(*p)));
        assert_eq!(max-min, Vec3::new(2.,4.,6.));
    }

    #[derive(Resource)]
    struct Batch(Vec<Statement>);
    fn execute(
        mut commands: Commands,
        batch: Res<Batch>,
        mut vars: ResMut<SceneMaxVars>,
        mut pools: ResMut<SceneMaxObjectPools>,
        mut assets: ResMut<SceneMaxRuntimeAssets>,
        mut delayed: ResMut<DelayedActionQueue>,
        mut bounds: ResMut<SceneMaxColliderBounds>,
        mut entities: ParamSet<(
            Query<(
                Entity,
                &SceneMaxEntity,
                &Transform,
                Option<&GlobalTransform>,
                Option<&ChildOf>,
            )>,
            Query<(
                Entity,
                &SceneMaxEntity,
                &mut Transform,
                Option<&SceneMaxGltf>,
                Option<&CurrentAnimation>,
                Option<&mut Visibility>,
                Option<&SceneMaxCharacterController>,
                Option<&mut SceneMaxCharacterMotor>,
            )>,
        )>,
    ) {
        let mut transforms = entities
            .p0()
            .iter()
            .map(|(_, s, t, _, _)| (s.name.clone(), *t))
            .collect();
        apply_action_sequence(
            &batch.0,
            &mut transforms,
            &mut vars,
            &mut pools,
            None,
            &HashMap::new(),
            &HashMap::new(),
            &mut HashMap::new(),
            &mut assets,
            &SceneMaxAnimationDurations::default(),
            &mut bounds,
            Some(&mut delayed),
            None,
            None,
            None,
            None,
            &mut commands,
            &mut entities,
        );
    }
    #[test]
    fn declaration_delete_and_immediate_use_observe_deferred_entities() {
        let source = "subject => box\nsubject.delete\nsubject => box\nsubject.pos = (3,4,5)";
        let program = scenemax_parser::parse_program(source).unwrap();
        assert_eq!(collect_model_declarations(&program).len(), 1);
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        world.init_resource::<DelayedActionQueue>();
        world.insert_resource(Batch(program.statements));
        let mut first = None;
        for step in 0..4 {
            world.run_system_once(execute).unwrap();
            let entities = world
                .query::<(Entity, &SceneMaxEntity, &Transform)>()
                .iter(&world)
                .map(|(e, _, t)| (e, *t))
                .collect::<Vec<_>>();
            if step == 0 {
                assert_eq!(entities.len(), 1);
                first = Some(entities[0].0);
            }
            if step == 1 {
                assert!(entities.is_empty());
            }
            if step == 3 {
                assert_eq!(entities.len(), 1);
                assert_ne!(Some(entities[0].0), first);
                assert_eq!(entities[0].1.translation, Vec3::new(3., 4., 5.));
            }
            let next = world
                .resource_mut::<DelayedActionQueue>()
                .actions
                .pop()
                .map(|a| a.actions)
                .unwrap_or_default();
            world.insert_resource(Batch(next));
        }
    }
    #[test]
    fn release_stops_owned_animation_effect_motor_and_rejects_invalid_releases() {
        let mut world = World::new();
        world.init_resource::<SceneMaxRuntimeAssets>();
        let lease = multiplayer::ActionLease::new(());
        let weak = std::sync::Arc::downgrade(&lease);
        let (graph, node) = AnimationGraph::from_clip(Handle::default());
        let mut graphs = Assets::<AnimationGraph>::default();
        let handle = graphs.add(graph);
        let mut player = AnimationPlayer::default();
        player.play(node).repeat();
        let child = world
            .spawn((player, AnimationGraphHandle(handle.clone())))
            .id();
        let root = world
            .spawn((
                SceneMaxEntity {
                    name: "subject".into(),
                    runtime_name: "subject".into(),
                },
                CurrentAnimation {
                    rewind: None,
                    identity: Some(weak.clone()),
                    controller_owner: None,
                    network_lifetime: Some(lease.clone()),
                    playback_graphs: vec![(child, handle)],
                    clip: "take".into(),
                    looped: true,
                    speed: 1.,
                    elapsed_seconds: 0.,
                    duration_seconds: 1.,
                },
                SceneMaxCharacterMotor {
                    network_lifetime: Some(lease.clone()),
                    timed_motion: Vec3::X,
                    timed_motion_remaining_seconds: 3.,
                    pending_jump_speed: Some(2.),
                    ..default()
                },
                SceneMaxEffekseerPlayback {
                    looped: true,
                    play_generation: 1,
                    playback_speed: 1.,
                    dynamic_inputs: [0.; 4],
                    elapsed_seconds: 0.,
                },
                multiplayer::EffectAction {
                    generation: 1,
                    owner: None,
                    lifetime: Some(lease),
                },
            ))
            .id();
        let controller =
            scenemax_parser::parse_program("control = animation subject.take").unwrap();
        let Statement::Assignment(assignment) = &controller.statements[0] else {
            panic!("assignment");
        };
        let AssignmentValue::AnimationController(value) = &assignment.value else {
            panic!("controller");
        };
        let run = multiplayer::ActionLease::new(());
        let waiting = std::sync::Arc::downgrade(&run);
        world
            .resource_mut::<SceneMaxRuntimeAssets>()
            .animation_controllers_by_name
            .insert(
                "control".into(),
                RuntimeAnimationController {
                    declaration_scope: None,
                    rewind_wait: Default::default(),
                    segments: vec![(*value.animation).clone()],
                    segment: 0,
                    run_lifetime: Some(run),
                    source: None,
                    scope: None,
                    advance_pending: true,
                    start_wait: 0.,
                    lifetime: weak.clone(),
                    animation: (*value.animation).clone(),
                    target: "subject".into(),
                    clip: "take".into(),
                    events: Vec::new(),
                    running: true,
                    previous_percent: 0.,
                    current_run_started: true,
                },
            );
        stop(&mut world, "subject");
        assert_eq!(waiting.strong_count(), 0);
        let assets = world.resource::<SceneMaxRuntimeAssets>();
        let controller = &assets.animation_controllers_by_name["control"];
        assert!(
            !controller.running && !controller.advance_pending && !controller.current_run_started
        );
        assert_eq!(weak.strong_count(), 0);
        assert!(world.get::<CurrentAnimation>(root).is_none());
        assert!(world.get::<SceneMaxEffekseerPlayback>(root).is_none());
        assert!(
            world
                .get::<AnimationPlayer>(child)
                .unwrap()
                .playing_animations()
                .next()
                .is_none()
        );
        let motor = world.get::<SceneMaxCharacterMotor>(root).unwrap();
        assert_eq!(motor.timed_motion, Vec3::ZERO);
        assert!(motor.pending_jump_speed.is_none());
        let mut pools = SceneMaxObjectPools::default();
        pools.pools.insert(
            "pool".into(),
            ObjectPoolRuntime {
                members: HashSet::from(["subject".into()]),
                in_use: HashSet::from(["subject".into()]),
                ..default()
            },
        );
        pools.aliases.insert("alias".into(), "subject".into());
        assert!(!release_pool_member("pool", "foreign", &mut pools));
        assert!(release_pool_member("pool", "subject", &mut pools));
        assert!(!release_pool_member("pool", "subject", &mut pools));
        assert_eq!(pools.pools["pool"].available.len(), 1);
        assert!(pools.aliases.is_empty());
    }
    #[test]
    fn global_aliases_are_captured_without_overwriting_function_aliases() {
        let mut pools = SceneMaxObjectPools::default();
        pools.aliases.insert("global".into(), "member_a".into());
        pools.aliases.insert("shadow".into(), "member_a".into());
        let mut scope = SceneMaxScopeFrame::default();
        scope.aliases.insert("shadow".into(), "member_b".into());
        let merged = network_scope(Some(&scope), &pools).unwrap();
        assert_eq!(merged.aliases["global"], "member_a");
        assert_eq!(merged.aliases["shadow"], "member_b");
    }
}

pub(super) fn source_options(source: &EntityOptions) -> EntityOptions {
    // RuntimeObjectPool.copyVariableDef copies transform/body fields, not sharing,
    // network annotation, material overrides or specialized primitive dimensions.
    EntityOptions {
        geometry_values: source.geometry_values.iter().filter(|attr| attr.name == "rotate").cloned().collect(),
        resource_value: source.resource_value.clone(), sprite: source.sprite, collider: source.collider,
        position: source.position, position_value: source.position_value.clone(),
        rotation_degrees: source.rotation_degrees, scale: source.scale,
        scale_value: source.scale_value.clone(), body_kind: source.body_kind,
        collision_shape: source.collision_shape.clone(), hidden: true, ..default()
    }
}
