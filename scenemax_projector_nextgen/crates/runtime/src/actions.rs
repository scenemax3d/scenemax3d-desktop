use super::*;

pub(super) fn apply_startup_runs(
    program: &Program,
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    delayed_actions: &mut DelayedActionQueue,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
) {
    let actions = program
        .statements
        .iter()
        .filter(|statement| is_startup_action(statement))
        .cloned()
        .collect::<Vec<_>>();
    let _ = apply_startup_action_sequence(
        &actions,
        commands,
        vars,
        object_pools,
        camera_system,
        delayed_actions,
        ui_queue,
        functions_by_name,
        entities_by_name,
        transforms_by_name,
        gltfs_by_name,
        guards_by_name,
        0,
    );
}

pub(super) fn is_startup_action(statement: &Statement) -> bool {
    !matches!(
        statement,
        Statement::ModelDecl { .. }
            | Statement::ObjectPool(_)
            | Statement::KeyEvent(_)
            | Statement::WhenEvent(_)
            | Statement::GuardDef { .. }
            | Statement::FightingCamera(_)
            | Statement::ThirdPersonCamera(_)
            | Statement::FunctionDef(_)
            | Statement::RunEvery { .. }
            | Statement::CameraPosition(_)
            | Statement::CameraRotation(_)
            | Statement::WaitForKey { .. }
            | Statement::SwitchTo { .. }
            | Statement::AddCode { .. }
            | Statement::NoOp { .. }
            | Statement::Unsupported { .. }
    )
}

pub(super) fn apply_startup_action_sequence(
    actions: &[Statement],
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    delayed_actions: &mut DelayedActionQueue,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
    depth: usize,
) -> ActionSequenceResult {
    if depth > 8 {
        tracing::warn!("skipping deeply recursive startup SceneMax action sequence");
        return ActionSequenceResult::Completed;
    }

    for (index, action) in actions.iter().enumerate() {
        match action {
            Statement::NoOp { .. } | Statement::Unsupported { .. } => {}
            Statement::Return | Statement::ReturnValue { .. } => {
                return ActionSequenceResult::Returned;
            }
            Statement::Wait { seconds } => {
                if enqueue_delayed_actions(
                    Some(delayed_actions),
                    *seconds,
                    actions[index + 1..].to_vec(),
                    None,
                    None,
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::WaitValue { value } => {
                let seconds = resolve_assignment_value_scoped_with_guards(
                    value,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                )
                .unwrap_or_default();
                if enqueue_delayed_actions(
                    Some(delayed_actions),
                    seconds,
                    actions[index + 1..].to_vec(),
                    None,
                    None,
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::RunFunction { name, args } => {
                let Some(function) = functions_by_name.get(name) else {
                    tracing::debug!(name, "startup SceneMax function was not parsed");
                    continue;
                };
                if !function_guard_matches(
                    function,
                    args,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    tracing::debug!(name, "startup SceneMax function guard is false");
                    continue;
                }
                let function_actions = actions_with_parent_continuation(
                    instantiate_function_actions(function, args),
                    parent_action_tail(actions, index),
                );
                let result = apply_startup_action_sequence(
                    &function_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    delayed_actions,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth + 1,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Async {
                actions: async_actions,
            } => {
                let _ = apply_startup_action_sequence(
                    async_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    delayed_actions,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth + 1,
                );
            }
            Statement::Repeat {
                times,
                actions: block_actions,
            } => {
                let repeated_actions = actions_with_parent_continuation(
                    repeat_actions(block_actions, *times),
                    parent_action_tail(actions, index),
                );
                let result = apply_startup_action_sequence(
                    &repeated_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    delayed_actions,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth + 1,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::If(statement) => {
                let selected_actions = if condition_matches(
                    &statement.condition,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    &statement.actions
                } else {
                    &statement.else_actions
                };
                let selected_actions = actions_with_parent_continuation(
                    selected_actions.clone(),
                    parent_action_tail(actions, index),
                );
                let result = apply_startup_action_sequence(
                    &selected_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    delayed_actions,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth + 1,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Guarded {
                condition,
                actions: block_actions,
            } => {
                if condition_matches(
                    condition,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    let guarded_actions = actions_with_parent_continuation(
                        block_actions.clone(),
                        parent_action_tail(actions, index),
                    );
                    let result = apply_startup_action_sequence(
                        &guarded_actions,
                        commands,
                        vars,
                        object_pools,
                        camera_system,
                        delayed_actions,
                        ui_queue,
                        functions_by_name,
                        entities_by_name,
                        transforms_by_name,
                        gltfs_by_name,
                        guards_by_name,
                        depth + 1,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                    return ActionSequenceResult::Completed;
                }
            }
            action if blocking_timed_action_seconds(action).is_some() => {
                let seconds = blocking_timed_action_seconds(action).unwrap_or_default();
                let result = apply_startup_action(
                    action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth,
                );
                if result.should_stop_parent() {
                    return result;
                }
                if enqueue_delayed_actions(
                    Some(delayed_actions),
                    seconds,
                    actions[index + 1..].to_vec(),
                    None,
                    None,
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            action => {
                let result = apply_startup_action(
                    action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
        }
    }

    ActionSequenceResult::Completed
}

pub(super) fn apply_startup_function_by_name(
    name: &str,
    args: &[String],
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
    depth: usize,
) -> ActionSequenceResult {
    if depth > 8 {
        tracing::warn!(name, "skipping deeply recursive startup SceneMax run");
        return ActionSequenceResult::Completed;
    }
    let Some(function) = functions_by_name.get(name) else {
        tracing::debug!(name, "startup SceneMax function was not parsed");
        return ActionSequenceResult::Completed;
    };
    if !function_guard_matches(
        function,
        args,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        None,
    ) {
        tracing::debug!(name, "startup SceneMax function guard is false");
        return ActionSequenceResult::Completed;
    }

    tracing::info!(name, "running SceneMax startup function");
    let actions = instantiate_function_actions(function, args);
    for action in &actions {
        let result = apply_startup_action(
            action,
            commands,
            vars,
            object_pools,
            camera_system,
            ui_queue,
            functions_by_name,
            entities_by_name,
            transforms_by_name,
            gltfs_by_name,
            guards_by_name,
            depth,
        );
        if result.should_stop_parent() {
            return result;
        }
    }
    ActionSequenceResult::Completed
}

pub(super) fn apply_startup_action(
    action: &Statement,
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
    depth: usize,
) -> ActionSequenceResult {
    match action {
        Statement::Assignment(assignment) | Statement::LocalAssignment(assignment) => {
            apply_assignment(
                assignment,
                vars,
                Some(transforms_by_name),
                guards_by_name,
                None,
            );
            ActionSequenceResult::Completed
        }
        Statement::CameraSystemSelect { name } => {
            select_camera_system(name, camera_system);
            ActionSequenceResult::Completed
        }
        Statement::CameraAttach(attach) => {
            attach_camera(attach, object_pools, None, camera_system);
            ActionSequenceResult::Completed
        }
        Statement::CameraChase { target } => {
            chase_camera(target, object_pools, None, camera_system);
            ActionSequenceResult::Completed
        }
        Statement::CameraAttachStop => {
            stop_camera_attachment(camera_system);
            ActionSequenceResult::Completed
        }
        Statement::Logger(logger) => {
            apply_logger_statement(
                logger,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            ActionSequenceResult::Completed
        }
        Statement::UiLoad { name } => {
            ui_queue
                .actions
                .push(SceneMaxUiAction::Load { name: name.clone() });
            ActionSequenceResult::Completed
        }
        Statement::UiShowHide(show_hide) => {
            ui_queue.actions.push(SceneMaxUiAction::ShowHide {
                target: show_hide.target.clone(),
                visible: show_hide.visible,
            });
            ActionSequenceResult::Completed
        }
        Statement::UiMessage(message) => {
            ui_queue.actions.push(SceneMaxUiAction::Message {
                target: message.target.clone(),
                text: message.text.clone(),
                effects: message.effects.clone(),
                duration_seconds: message.duration_seconds,
            });
            ActionSequenceResult::Completed
        }
        Statement::UiEase(ease) => {
            ui_queue.actions.push(SceneMaxUiAction::Ease {
                target: ease.target.clone(),
                easing: ease.easing.clone(),
                direction: ease.direction,
                duration_seconds: ease.duration_seconds,
            });
            ActionSequenceResult::Completed
        }
        Statement::UiSetProperty(property) => {
            ui_queue.actions.push(SceneMaxUiAction::SetProperty {
                target: property.target.clone(),
                property: property.property.clone(),
                value: property.value.clone(),
            });
            ActionSequenceResult::Completed
        }
        Statement::RunFunction { name, args } => {
            match apply_startup_function_by_name(
                name,
                args,
                commands,
                vars,
                object_pools,
                camera_system,
                ui_queue,
                functions_by_name,
                entities_by_name,
                transforms_by_name,
                gltfs_by_name,
                guards_by_name,
                depth + 1,
            ) {
                ActionSequenceResult::Suspended => ActionSequenceResult::Suspended,
                ActionSequenceResult::Completed | ActionSequenceResult::Returned => {
                    ActionSequenceResult::Completed
                }
            }
        }
        Statement::Async { actions } => {
            for nested_action in actions {
                let result = apply_startup_action(
                    nested_action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::If(statement) => {
            let selected_actions = if condition_matches(
                &statement.condition,
                vars,
                guards_by_name,
                Some(transforms_by_name),
                None,
            ) {
                &statement.actions
            } else {
                &statement.else_actions
            };
            for nested_action in selected_actions {
                let result = apply_startup_action(
                    nested_action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::Guarded { condition, actions } => {
            if condition_matches(
                condition,
                vars,
                guards_by_name,
                Some(transforms_by_name),
                None,
            ) {
                for nested_action in actions {
                    let result = apply_startup_action(
                        nested_action,
                        commands,
                        vars,
                        object_pools,
                        camera_system,
                        ui_queue,
                        functions_by_name,
                        entities_by_name,
                        transforms_by_name,
                        gltfs_by_name,
                        guards_by_name,
                        depth,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::Repeat { times, actions } => {
            for _ in 0..*times {
                for nested_action in actions {
                    let result = apply_startup_action(
                        nested_action,
                        commands,
                        vars,
                        object_pools,
                        camera_system,
                        ui_queue,
                        functions_by_name,
                        entities_by_name,
                        transforms_by_name,
                        gltfs_by_name,
                        guards_by_name,
                        depth,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::DoWhile { condition, actions } => {
            for _ in 0..128 {
                for nested_action in actions {
                    let result = apply_startup_action(
                        nested_action,
                        commands,
                        vars,
                        object_pools,
                        camera_system,
                        ui_queue,
                        functions_by_name,
                        entities_by_name,
                        transforms_by_name,
                        gltfs_by_name,
                        guards_by_name,
                        depth,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                }
                if !condition_matches(
                    condition,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    break;
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::Visibility { target, visible } => {
            if let Some(entity) = entities_by_name.get(target) {
                commands.entity(*entity).insert(if *visible {
                    Visibility::Inherited
                } else {
                    Visibility::Hidden
                });
            }
            ActionSequenceResult::Completed
        }
        Statement::Animate(animation) => {
            if let (Some(entity), Some(gltf)) = (
                entities_by_name.get(&animation.target),
                gltfs_by_name.get(&animation.target),
            ) {
                commands.entity(*entity).insert(AnimationToPlay {
                    clip: animation.clip.clone(),
                    looped: animation.looped,
                    speed: animation.speed,
                    gltf: gltf.clone(),
                });
            }
            ActionSequenceResult::Completed
        }
        Statement::AnimationSpeed(animation_speed) => {
            if let Some(entity) = entities_by_name.get(&animation_speed.target) {
                commands
                    .entity(*entity)
                    .insert(animation_speed_override(animation_speed));
            }
            ActionSequenceResult::Completed
        }
        Statement::CharacterMode(character_mode) => {
            if let Some(entity) = entities_by_name.get(&character_mode.target) {
                commands
                    .entity(*entity)
                    .insert(PendingCharacterMode(character_mode.clone()));
            }
            ActionSequenceResult::Completed
        }
        Statement::ClearCharacterMode { target } => {
            if let Some(entity) = entities_by_name.get(target) {
                clear_character_mode(commands, *entity);
            }
            ActionSequenceResult::Completed
        }
        Statement::CharacterIgnore(ignore) => {
            tracing::debug!(
                target = ignore.target,
                ignored = ignore.ignored,
                "SceneMax character.ignore is handled by collision layers"
            );
            ActionSequenceResult::Completed
        }
        Statement::LookAt { target, subject } => {
            let (Some(entity), Some(target_transform), Some(subject_transform)) = (
                entities_by_name.get(target),
                transforms_by_name.get(target).copied(),
                lookup_subject_transform(subject, transforms_by_name),
            ) else {
                return ActionSequenceResult::Completed;
            };
            let mut updated = target_transform;
            look_at_scenemax_forward(&mut updated, subject_transform.translation);
            commands.entity(*entity).insert(updated);
            transforms_by_name.insert(target.clone(), updated);
            ActionSequenceResult::Completed
        }
        Statement::Position(position) => {
            let Some(entity) = entities_by_name.get(&position.target) else {
                return ActionSequenceResult::Completed;
            };
            let Some(translation) = evaluate_position_statement(position, transforms_by_name)
            else {
                return ActionSequenceResult::Completed;
            };
            let mut transform = transforms_by_name
                .get(&position.target)
                .copied()
                .unwrap_or_default();
            transform.translation = translation;
            commands.entity(*entity).insert(transform);
            transforms_by_name.insert(position.target.clone(), transform);
            ActionSequenceResult::Completed
        }
        Statement::Turn(turn) => {
            if let Some(entity) = entities_by_name.get(&turn.target) {
                commands
                    .entity(*entity)
                    .insert(timed_turn_from_statement(turn));
            }
            ActionSequenceResult::Completed
        }
        Statement::Move(movement) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&movement.target),
                transforms_by_name.get(&movement.target),
            ) {
                append_timed_move(
                    commands,
                    *entity,
                    timed_move_from_statement(movement, transform),
                );
            }
            ActionSequenceResult::Completed
        }
        Statement::MoveTo(move_to) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&move_to.target),
                transforms_by_name.get(&move_to.target),
            ) {
                if let Some(timed_move) =
                    timed_move_to_from_statement(move_to, transform, transforms_by_name)
                {
                    append_timed_move(commands, *entity, timed_move);
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::CharacterJump(jump) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&jump.target),
                transforms_by_name.get(&jump.target),
            ) {
                commands
                    .entity(*entity)
                    .insert(timed_jump_from_statement(jump, transform));
            }
            ActionSequenceResult::Completed
        }
        Statement::PhysicsImpulse(impulse) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&impulse.target),
                transforms_by_name.get(&impulse.target),
            ) {
                apply_physics_impulse(commands, *entity, transform, impulse);
            }
            ActionSequenceResult::Completed
        }
        Statement::PhysicsStop { target } => {
            if let Some(entity) = entities_by_name.get(target) {
                apply_physics_stop(commands, *entity);
            }
            ActionSequenceResult::Completed
        }
        Statement::PhysicsThrowAt(throw_at) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&throw_at.target),
                transforms_by_name.get(&throw_at.target),
            ) {
                apply_physics_throw_at(
                    commands,
                    *entity,
                    transform,
                    throw_at,
                    vars,
                    transforms_by_name,
                );
            }
            ActionSequenceResult::Completed
        }
        Statement::Return | Statement::ReturnValue { .. } => ActionSequenceResult::Returned,
        _ => ActionSequenceResult::Completed,
    }
}

pub(super) fn switch_scene_on_key(
    keyboard: Res<ButtonInput<KeyCode>>,
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    mut startup_program: ResMut<SceneMaxStartupProgram>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut recurring_timers: ResMut<RecurringRunTimers>,
    mut physics_contacts: ResMut<SceneMaxPhysicsContacts>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut scene_queries: ParamSet<(
        Query<Entity, With<SceneMaxEntity>>,
        Query<&mut Transform, With<Camera3d>>,
    )>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    mut character_configs: ResMut<Assets<SceneMaxControlSchemeConfig>>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        return;
    };
    let Some(scene) = pending_key_switch(program, &keyboard).map(str::to_owned) else {
        return;
    };
    let Some(script_root) = context.script_root.as_ref() else {
        tracing::warn!(scene, "cannot switch scene without a startup script root");
        return;
    };
    let Some(asset_root) = context.asset_root.as_ref() else {
        tracing::warn!(scene, "cannot switch scene without an asset root");
        return;
    };

    let scene_main = script_root.join(&scene).join("main");
    match load_script_with_adds(&scene_main, &mut HashSet::new()) {
        Ok(program) => {
            for entity in &scene_queries.p0() {
                commands.entity(entity).despawn();
            }

            if let Ok(mut camera) = scene_queries.p1().single_mut() {
                *camera = camera_transform_from_program(&program);
            }

            vars.0.clear();
            object_pools.aliases.clear();
            object_pools.pools.clear();
            delayed_actions.actions.clear();
            recurring_timers.remaining_by_statement.clear();
            physics_contacts.active_pairs.clear();
            collider_bounds.clear();
            apply_initial_assignments(&program, &mut vars);
            apply_camera_systems(&program, &mut camera_system);
            let mut scene_ui_queue = SceneMaxUiActionQueue::default();
            spawn_scenemax_program(
                &mut commands,
                &asset_server,
                asset_root,
                context.builtin_asset_root.as_deref(),
                &program,
                &mut vars,
                &mut object_pools,
                &mut camera_system,
                &mut delayed_actions,
                &mut scene_ui_queue,
                &mut collider_bounds,
                &mut meshes,
                &mut materials,
                &mut character_configs,
            );
            startup_program.0 = Some(program);
            tracing::info!(scene, path = %scene_main.display(), "switched SceneMax scene");
        }
        Err(error) => tracing::error!(
            scene,
            path = %scene_main.display(),
            %error,
            "failed to switch SceneMax scene"
        ),
    }
}

pub(super) fn apply_key_events(
    time: Res<Time>,
    keyboard: Res<ButtonInput<KeyCode>>,
    startup_program: Res<SceneMaxStartupProgram>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    animation_durations: Res<SceneMaxAnimationDurations>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    let Some(program) = startup_program.0.as_ref() else {
        return;
    };
    if pending_key_switch(program, &keyboard).is_some() {
        return;
    }

    let mut transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    apply_transform_aliases(&mut transforms_by_name, &object_pools);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for statement in &program.statements {
        let Statement::KeyEvent(event) = statement else {
            continue;
        };
        if !key_event_matches(&event.key, event.trigger, &keyboard) {
            continue;
        }
        let guard_matches = event.guard.as_ref().is_none_or(|guard| {
            condition_matches(
                guard,
                &vars,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(&collider_bounds),
            )
        });
        if !guard_matches {
            write_key_event_probe(
                "KEY:SKIP",
                &event.key,
                event.trigger,
                &vars,
                &guards_by_name,
                &transforms_by_name,
                &collider_bounds,
            );
            continue;
        }
        write_key_event_probe(
            "KEY:FIRE",
            &event.key,
            event.trigger,
            &vars,
            &guards_by_name,
            &transforms_by_name,
            &collider_bounds,
        );

        let mut queued_animations = HashMap::new();
        let continuous_delta_seconds =
            (event.trigger == KeyTrigger::Pressed).then_some(time.delta_secs());
        apply_action_sequence(
            &event.actions,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            None,
            None,
            continuous_delta_seconds,
            &mut commands,
            &mut scene_entities,
        );
    }
}

pub(super) fn apply_when_events(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    animation_durations: Res<SceneMaxAnimationDurations>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut active_collisions: ResMut<ActiveCollisionEvents>,
    mut active_controllers: ResMut<ActiveActionControllers>,
    physics_contacts: Res<SceneMaxPhysicsContacts>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    let Some(program) = startup_program.0.as_ref() else {
        active_collisions.active_by_statement.clear();
        active_controllers.running.clear();
        return;
    };

    let mut transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    apply_transform_aliases(&mut transforms_by_name, &object_pools);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for (statement_index, statement) in program.statements.iter().enumerate() {
        let Statement::WhenEvent(event) = statement else {
            continue;
        };
        let guard_matches = event.guard.as_ref().is_none_or(|guard| {
            condition_matches(
                guard,
                &vars,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(&collider_bounds),
            )
        });
        let condition_matches_now = when_condition_matches(
            &event.condition,
            &vars,
            &guards_by_name,
            Some(&transforms_by_name),
            Some(&collider_bounds),
            &physics_contacts,
            &object_pools,
        );
        if let Some(after_condition) = &event.after_condition {
            let after_matches = when_condition_matches(
                after_condition,
                &vars,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(&collider_bounds),
                &physics_contacts,
                &object_pools,
            );
            if !guard_matches {
                active_collisions
                    .transition_armed_by_statement
                    .remove(&statement_index);
                continue;
            }
            if after_matches {
                active_collisions
                    .transition_armed_by_statement
                    .insert(statement_index);
                active_controllers
                    .running
                    .remove(&SceneMaxControllerKey::When(statement_index));
                continue;
            }
            if !condition_matches_now {
                active_controllers
                    .running
                    .remove(&SceneMaxControllerKey::When(statement_index));
                continue;
            }
            if !active_collisions
                .transition_armed_by_statement
                .remove(&statement_index)
            {
                continue;
            }
        }
        let is_collision_event = condition_contains_collision(&event.condition);
        if !guard_matches || !condition_matches_now {
            if is_collision_event {
                active_collisions
                    .active_by_statement
                    .remove(&statement_index);
            }
            active_controllers
                .running
                .remove(&SceneMaxControllerKey::When(statement_index));
            continue;
        }
        if is_collision_event
            && !active_collisions
                .active_by_statement
                .insert(statement_index)
        {
            continue;
        }
        if is_collision_event {
            write_collision_event_probe(
                statement_index,
                &event.condition,
                &transforms_by_name,
                &collider_bounds,
                &physics_contacts,
                &object_pools,
            );
        }
        let owner = SceneMaxControllerKey::When(statement_index);
        if active_controllers.running.contains(&owner) {
            continue;
        }

        let mut queued_animations = HashMap::new();
        active_controllers.running.insert(owner.clone());
        let result = apply_action_sequence(
            &event.actions,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            Some(owner.clone()),
            None,
            Some(time.delta_secs()),
            &mut commands,
            &mut scene_entities,
        );
        if !result.is_suspended() {
            active_controllers.running.remove(&owner);
        }
    }
    clear_transient_hit_flags(&mut vars);
}

pub(super) fn clear_transient_hit_flags(vars: &mut SceneMaxVars) {
    scenemax_runtime_vm_core::clear_transient_hit_flags(vars);
}

pub(super) fn write_collision_event_probe(
    statement_index: usize,
    condition: &Condition,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) {
    let Some(report) = collision_condition_report(
        condition,
        transforms_by_name,
        collider_bounds,
        physics_contacts,
        object_pools,
    ) else {
        return;
    };
    write_runtime_log_line(
        LoggerLevel::Info,
        &format!(
            "COLL:FIRE stmt={} source={} target={} avian={} fallback={} distance={} threshold={} owner_distance={}",
            statement_index,
            report.source,
            report.target,
            report.avian_contact as u8,
            report.fallback_match as u8,
            format_scenemax_number(report.distance),
            format_scenemax_number(report.threshold),
            format_scenemax_number(report.owner_distance),
        ),
    );
}

#[derive(Debug)]
pub(super) struct CollisionConditionReport {
    source: String,
    target: String,
    avian_contact: bool,
    fallback_match: bool,
    distance: f32,
    threshold: f32,
    owner_distance: f32,
}

pub(super) fn collision_condition_report(
    condition: &Condition,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) -> Option<CollisionConditionReport> {
    match condition {
        Condition::Collision { sources, target } => collision_pair_report(
            sources,
            target,
            transforms_by_name,
            collider_bounds,
            physics_contacts,
            object_pools,
        ),
        Condition::And(conditions) | Condition::Or(conditions) => {
            conditions.iter().find_map(|condition| {
                collision_condition_report(
                    condition,
                    transforms_by_name,
                    collider_bounds,
                    physics_contacts,
                    object_pools,
                )
            })
        }
        Condition::Not(condition) => collision_condition_report(
            condition,
            transforms_by_name,
            collider_bounds,
            physics_contacts,
            object_pools,
        ),
        _ => None,
    }
}

pub(super) fn collision_pair_report(
    sources: &[String],
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) -> Option<CollisionConditionReport> {
    let target_candidates = collision_reference_candidates_with_alias(target, object_pools);
    let mut best: Option<CollisionConditionReport> = None;
    for source in sources {
        let source_candidates = collision_reference_candidates_with_alias(source, object_pools);
        for source_name in &source_candidates {
            for target_name in &target_candidates {
                let avian_contact =
                    active_physics_contact_matches(source_name, target_name, physics_contacts);
                let (distance, threshold, fallback_match, owner_distance) =
                    collision_pair_distance_report(
                        source_name,
                        target_name,
                        transforms_by_name,
                        collider_bounds,
                    );
                let report = CollisionConditionReport {
                    source: source_name.clone(),
                    target: target_name.clone(),
                    avian_contact,
                    fallback_match,
                    distance,
                    threshold,
                    owner_distance,
                };
                if report.avian_contact || report.fallback_match {
                    return Some(report);
                }
                if best
                    .as_ref()
                    .is_none_or(|best| report.distance < best.distance)
                {
                    best = Some(report);
                }
            }
        }
    }
    best
}

pub(super) fn collision_pair_distance_report(
    source: &str,
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
) -> (f32, f32, bool, f32) {
    let source_exact = transforms_by_name.get(source).copied();
    let target_exact = transforms_by_name.get(target).copied();
    let owner_distance = collision_owner_distance(source, target, transforms_by_name);
    let Some(source_transform) =
        source_exact.or_else(|| collision_owner_transform(source, transforms_by_name))
    else {
        return (f32::INFINITY, 0.0, false, owner_distance);
    };
    let Some(target_transform) =
        target_exact.or_else(|| collision_owner_transform(target, transforms_by_name))
    else {
        return (f32::INFINITY, 0.0, false, owner_distance);
    };
    let threshold = if source_exact.is_some() && target_exact.is_some() {
        exact_collision_threshold(source, target, Some(collider_bounds))
    } else {
        collision_threshold(source, target)
    };
    let distance = source_transform
        .translation
        .distance(target_transform.translation);
    let matches =
        if let (Some(source_transform), Some(target_transform)) = (source_exact, target_exact) {
            if !player_hitbox_owner_distance_allows(source, target, transforms_by_name) {
                false
            } else {
                exact_collider_shapes_overlap(
                    source,
                    source_transform,
                    target,
                    target_transform,
                    Some(collider_bounds),
                )
                .unwrap_or(distance <= threshold)
            }
        } else {
            distance <= threshold
        };
    (distance, threshold, matches, owner_distance)
}

pub(super) fn update_recurring_runs(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    animation_durations: Res<SceneMaxAnimationDurations>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut recurring_timers: ResMut<RecurringRunTimers>,
    mut active_controllers: ResMut<ActiveActionControllers>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    let Some(program) = startup_program.0.as_ref() else {
        recurring_timers.remaining_by_statement.clear();
        active_controllers.running.clear();
        return;
    };

    let delta = time.delta_secs();
    let mut due_runs = Vec::new();
    for (index, statement) in program.statements.iter().enumerate() {
        let Statement::RunEvery {
            name,
            args,
            interval_seconds,
        } = statement
        else {
            continue;
        };
        let interval = interval_seconds.max(0.001);
        let remaining = recurring_timers
            .remaining_by_statement
            .entry(index)
            .or_insert(interval);
        *remaining -= delta;
        if *remaining <= 0.0 {
            let owner = SceneMaxControllerKey::Recurring(index);
            if active_controllers.running.contains(&owner) {
                *remaining = 0.0;
            } else {
                due_runs.push((index, name.clone(), args.clone()));
                while *remaining <= 0.0 {
                    *remaining += interval;
                }
            }
        }
    }

    if due_runs.is_empty() {
        return;
    }

    let mut transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    apply_transform_aliases(&mut transforms_by_name, &object_pools);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for (index, name, args) in due_runs {
        let mut queued_animations = HashMap::new();
        let owner = SceneMaxControllerKey::Recurring(index);
        active_controllers.running.insert(owner.clone());
        let result = apply_function_by_name(
            &name,
            &args,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            Some(owner.clone()),
            None,
            None,
            &mut commands,
            &mut scene_entities,
            0,
        );
        if !result.is_suspended() {
            active_controllers.running.remove(&owner);
        }
    }
}

pub(super) fn update_delayed_actions(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    animation_durations: Res<SceneMaxAnimationDurations>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut active_controllers: ResMut<ActiveActionControllers>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    let Some(program) = startup_program.0.as_ref() else {
        delayed_actions.actions.clear();
        active_controllers.running.clear();
        return;
    };

    let delta = time.delta_secs();
    let mut ready_actions = Vec::new();
    let mut pending_actions = Vec::new();
    for mut delayed in delayed_actions.actions.drain(..) {
        delayed.remaining_seconds -= delta;
        if delayed.remaining_seconds <= 0.0 {
            ready_actions.push(delayed);
        } else {
            pending_actions.push(delayed);
        }
    }
    delayed_actions.actions = pending_actions;

    if ready_actions.is_empty() {
        return;
    }

    let mut transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    apply_transform_aliases(&mut transforms_by_name, &object_pools);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for mut delayed in ready_actions {
        if delayed.actions.is_empty() {
            if let Some(owner) = delayed.owner {
                active_controllers.running.remove(&owner);
            }
            continue;
        }
        let mut queued_animations = HashMap::new();
        let result = apply_action_sequence(
            &delayed.actions,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            delayed.owner.clone(),
            delayed.scope.as_mut(),
            None,
            &mut commands,
            &mut scene_entities,
        );
        if !result.is_suspended() {
            if let Some(owner) = delayed.owner {
                active_controllers.running.remove(&owner);
            }
        }
    }
}

pub(super) fn enqueue_delayed_actions(
    delayed_actions: Option<&mut DelayedActionQueue>,
    seconds: f32,
    actions: Vec<Statement>,
    owner: Option<SceneMaxControllerKey>,
    scope: Option<SceneMaxScopeFrame>,
) -> bool {
    let Some(delayed_actions) = delayed_actions else {
        return false;
    };
    if actions.is_empty() && owner.is_none() {
        return false;
    }
    delayed_actions.actions.push(DelayedActions {
        remaining_seconds: seconds.max(0.0),
        actions,
        owner,
        scope,
    });
    true
}

pub(super) fn apply_action_sequence(
    actions: &[Statement],
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    mut camera_system: Option<&mut SceneMaxCameraSystem>,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    queued_animations: &mut HashMap<Entity, (String, bool)>,
    runtime_assets: &SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    mut delayed_actions: Option<&mut DelayedActionQueue>,
    mut ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
) -> ActionSequenceResult {
    apply_scoped_transform_aliases(transforms_by_name, scope.as_deref());

    for (index, action) in actions.iter().enumerate() {
        match action {
            Statement::NoOp { .. } => {}
            Statement::Unsupported { text } => {
                tracing::debug!(text, "skipping unsupported SceneMax runtime action");
            }
            Statement::Return | Statement::ReturnValue { .. } => {
                return ActionSequenceResult::Returned;
            }
            Statement::Wait { seconds } => {
                let remaining = actions[index + 1..].to_vec();
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    *seconds,
                    remaining,
                    owner.clone(),
                    scope.as_deref().cloned(),
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::WaitValue { value } => {
                let seconds = resolve_assignment_value_scoped_with_guards(
                    value,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                )
                .unwrap_or_default();
                let remaining = actions[index + 1..].to_vec();
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    seconds,
                    remaining,
                    owner.clone(),
                    scope.as_deref().cloned(),
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::WaitUntil { condition } => {
                if !condition_matches_scoped(
                    condition,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    if enqueue_delayed_actions(
                        delayed_actions.as_deref_mut(),
                        LOOP_CONTINUE_DELAY_SECONDS,
                        actions[index..].to_vec(),
                        owner.clone(),
                        scope.as_deref().cloned(),
                    ) {
                        return ActionSequenceResult::Suspended;
                    }
                    return ActionSequenceResult::Completed;
                }
            }
            Statement::AnimationSpeed(animation_speed)
                if animation_speed.condition.is_some()
                    && !animation_speed_condition_matches(
                        animation_speed,
                        vars,
                        object_pools,
                        guards_by_name,
                        Some(transforms_by_name),
                        Some(collider_bounds),
                        scene_entities,
                    ) =>
            {
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    LOOP_CONTINUE_DELAY_SECONDS,
                    actions[index..].to_vec(),
                    owner.clone(),
                    scope.as_deref().cloned(),
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::RunFunction { name, args } => {
                let Some(function) = functions_by_name.get(name) else {
                    tracing::debug!(
                        name,
                        "SceneMax function is not implemented or was not parsed"
                    );
                    continue;
                };
                if !function_guard_matches(
                    function,
                    args,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    tracing::debug!(name, "SceneMax function guard is false");
                    continue;
                }

                let function_actions = actions_with_parent_continuation(
                    instantiate_function_actions(function, args),
                    parent_action_tail(actions, index),
                );
                let mut function_scope = scope.as_deref().cloned().unwrap_or_default();
                let result = apply_action_sequence(
                    &function_actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    Some(&mut function_scope),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Async { actions } => {
                let _ = apply_action_sequence(
                    actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    None,
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
            }
            Statement::Repeat {
                times,
                actions: block_actions,
            } => {
                let repeated_actions = actions_with_parent_continuation(
                    repeat_actions(block_actions, *times),
                    parent_action_tail(actions, index),
                );
                let result = apply_action_sequence(
                    &repeated_actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::DoWhile {
                condition,
                actions: block_actions,
            } => {
                let mut loop_actions = block_actions.clone();
                loop_actions.push(Statement::LoopContinue {
                    condition: condition.clone(),
                    actions: block_actions.clone(),
                });
                loop_actions = actions_with_parent_continuation(
                    loop_actions,
                    parent_action_tail(actions, index),
                );
                let result = apply_action_sequence(
                    &loop_actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::LoopContinue {
                condition,
                actions: block_actions,
            } => {
                if condition_matches_scoped(
                    condition,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    let mut loop_and_tail = vec![Statement::DoWhile {
                        condition: condition.clone(),
                        actions: block_actions.clone(),
                    }];
                    loop_and_tail.extend_from_slice(parent_action_tail(actions, index));
                    if enqueue_delayed_actions(
                        delayed_actions.as_deref_mut(),
                        LOOP_CONTINUE_DELAY_SECONDS,
                        loop_and_tail,
                        owner.clone(),
                        scope.as_deref().cloned(),
                    ) {
                        return ActionSequenceResult::Suspended;
                    }
                }
            }
            Statement::If(statement) => {
                let selected_actions = if condition_matches_scoped(
                    &statement.condition,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    &statement.actions
                } else {
                    &statement.else_actions
                };
                let selected_actions = actions_with_parent_continuation(
                    selected_actions.clone(),
                    parent_action_tail(actions, index),
                );
                let result = apply_action_sequence(
                    &selected_actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Guarded {
                condition,
                actions: block_actions,
            } => {
                if condition_matches_scoped(
                    condition,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    let guarded_actions = actions_with_parent_continuation(
                        block_actions.clone(),
                        parent_action_tail(actions, index),
                    );
                    let result = apply_action_sequence(
                        &guarded_actions,
                        transforms_by_name,
                        vars,
                        object_pools,
                        camera_system.as_deref_mut(),
                        functions_by_name,
                        guards_by_name,
                        queued_animations,
                        runtime_assets,
                        animation_durations,
                        collider_bounds,
                        delayed_actions.as_deref_mut(),
                        ui_queue.as_deref_mut(),
                        owner.clone(),
                        scope.as_deref_mut(),
                        continuous_delta_seconds,
                        commands,
                        scene_entities,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                    return ActionSequenceResult::Completed;
                }
            }
            action if blocking_timed_action_seconds(action).is_some() => {
                let seconds = blocking_timed_action_seconds(action).unwrap_or_default();
                apply_key_action(
                    action,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    None,
                    commands,
                    scene_entities,
                );
                let remaining = actions[index + 1..].to_vec();
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    seconds,
                    remaining,
                    owner.clone(),
                    scope.as_deref().cloned(),
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Animate(animation) => {
                apply_key_action(
                    action,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if animation.blocking {
                    let remaining = actions[index + 1..].to_vec();
                    if enqueue_delayed_actions(
                        delayed_actions.as_deref_mut(),
                        estimated_animation_seconds(animation, animation_durations),
                        remaining,
                        owner.clone(),
                        scope.as_deref().cloned(),
                    ) {
                        return ActionSequenceResult::Suspended;
                    }
                    return ActionSequenceResult::Completed;
                }
            }
            action => {
                let result = apply_key_action(
                    action,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
        }
    }
    ActionSequenceResult::Completed
}

pub(super) fn apply_runtime_model_decl(
    name: &str,
    resource: &str,
    options: &EntityOptions,
    transforms_by_name: &mut HashMap<String, Transform>,
    runtime_assets: &SceneMaxRuntimeAssets,
    collider_bounds: &mut SceneMaxColliderBounds,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    let transform = primitive_transform_from_options(options);
    for (entity, scene_entity, mut existing_transform, _, _, visibility, _, _) in
        &mut scene_entities.p1()
    {
        if scene_entity.name != name {
            continue;
        }
        *existing_transform = transform;
        if let Some(mut visibility) = visibility {
            *visibility = if options.hidden {
                Visibility::Hidden
            } else {
                Visibility::Inherited
            };
        } else {
            commands.entity(entity).insert(if options.hidden {
                Visibility::Hidden
            } else {
                Visibility::Inherited
            });
        }
        transforms_by_name.insert(name.to_owned(), transform);
        if options.collider {
            register_collider_bounds(collider_bounds, name, options, transform);
        }
        tracing::debug!(
            name,
            resource,
            "updated runtime SceneMax placeholder entity"
        );
        return;
    }

    let visibility = if options.hidden {
        Visibility::Hidden
    } else {
        Visibility::Inherited
    };
    if options.collider {
        spawn_scenemax_collider_decl(commands, name, resource, options, transform, None);
        register_collider_bounds(collider_bounds, name, options, transform);
        transforms_by_name.insert(name.to_owned(), transform);
        tracing::info!(name, resource, "spawned runtime SceneMax collider");
        return;
    }
    let mut entity = commands.spawn((
        SceneMaxEntity {
            name: name.to_owned(),
            runtime_name: format!("{name}@runtime"),
        },
        transform,
        visibility,
    ));
    if let (Some(mesh), Some(material)) = (
        runtime_assets.placeholder_mesh.as_ref(),
        runtime_assets.placeholder_material.as_ref(),
    ) {
        entity.insert((Mesh3d(mesh.clone()), MeshMaterial3d(material.clone())));
    }
    let entity_id = entity.id();
    insert_physics_components(commands, entity_id, name, resource, options, &transform);
    transforms_by_name.insert(name.to_owned(), transform);
    tracing::info!(
        name,
        resource,
        "spawned runtime SceneMax placeholder entity"
    );
}

pub(super) fn apply_key_action(
    action: &Statement,
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    mut camera_system: Option<&mut SceneMaxCameraSystem>,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    queued_animations: &mut HashMap<Entity, (String, bool)>,
    runtime_assets: &SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    delayed_actions: Option<&mut DelayedActionQueue>,
    mut ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
) -> ActionSequenceResult {
    if matches!(action, Statement::NoOp { .. }) {
        return ActionSequenceResult::Completed;
    }
    if let Statement::Unsupported { text } = action {
        tracing::debug!(text, "skipping unsupported SceneMax runtime action");
        return ActionSequenceResult::Completed;
    }
    if let Statement::ModelDecl {
        name,
        resource,
        options,
    } = action
    {
        apply_runtime_model_decl(
            name,
            resource,
            options,
            transforms_by_name,
            runtime_assets,
            collider_bounds,
            commands,
            scene_entities,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::Assignment(assignment) | Statement::LocalAssignment(assignment) = action {
        if let AssignmentValue::PoolAcquire { pool } = &assignment.value {
            let Some(member) = acquire_pool_member(pool, object_pools) else {
                tracing::debug!(pool, "SceneMax object pool has no available members");
                return ActionSequenceResult::Completed;
            };
            if let Some(scope) = scope.as_deref_mut() {
                scope
                    .aliases
                    .insert(assignment.name.clone(), member.clone());
            } else {
                object_pools
                    .aliases
                    .insert(assignment.name.clone(), member.clone());
            }
            for (entity, scene_entity, transform, _, _, visibility, _, _) in
                &mut scene_entities.p1()
            {
                if scene_entity.name == member {
                    if let Some(mut visibility) = visibility {
                        *visibility = Visibility::Inherited;
                    } else {
                        commands.entity(entity).insert(Visibility::Inherited);
                    }
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                    commands
                        .entity(entity)
                        .insert((LinearVelocity::ZERO, AngularVelocity::ZERO));
                    break;
                }
            }
            return ActionSequenceResult::Completed;
        }
        let assigned_value = apply_assignment_scoped(
            assignment,
            vars,
            scope.as_deref_mut(),
            Some(transforms_by_name),
            guards_by_name,
            Some(collider_bounds),
            matches!(action, Statement::LocalAssignment(_)),
        );
        if assignment.name == "action"
            && assigned_value.is_some_and(|value| value.abs() <= f32::EPSILON)
        {
            for (entity, scene_entity, _, gltf, current_animation, _, _, _) in
                &mut scene_entities.p1()
            {
                if scene_entity.name != "player1" {
                    continue;
                }
                let already_queued =
                    queued_animations
                        .get(&entity)
                        .is_some_and(|(clip, looped)| {
                            *looped && requested_animation_names_match(clip, "idle2")
                        });
                let already_current = queued_animations.get(&entity).is_none()
                    && current_animation
                        .is_some_and(|current| current_animation_matches(current, "idle2", true));
                if already_queued || already_current {
                    continue;
                }
                if let Some(gltf) = gltf {
                    commands.entity(entity).insert(AnimationToPlay {
                        clip: "idle2".to_owned(),
                        looped: true,
                        speed: 1.0,
                        gltf: gltf.gltf.clone(),
                    });
                    queued_animations.insert(entity, ("idle2".to_owned(), true));
                }
            }
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::CameraSystemSelect { name } = action {
        if let Some(camera_system) = camera_system {
            select_camera_system(name, camera_system);
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::CameraAttach(attach) = action {
        if let Some(camera_system) = camera_system {
            attach_camera(attach, object_pools, scope.as_deref(), camera_system);
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::CameraChase { target } = action {
        if let Some(camera_system) = camera_system {
            chase_camera(target, object_pools, scope.as_deref(), camera_system);
        }
        return ActionSequenceResult::Completed;
    }
    if matches!(action, Statement::CameraAttachStop) {
        if let Some(camera_system) = camera_system {
            stop_camera_attachment(camera_system);
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::Logger(logger) = action {
        apply_logger_statement(
            logger,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        );
        return ActionSequenceResult::Completed;
    }
    if let Some(ui_action) = scenemax_ui_action_from_statement(action)
        && let Some(ui_queue) = ui_queue.as_deref_mut()
    {
        ui_queue.actions.push(ui_action);
        return ActionSequenceResult::Completed;
    }
    if let Statement::RunFunction { name, args } = action {
        return match apply_function_by_name(
            name,
            args,
            transforms_by_name,
            vars,
            object_pools,
            camera_system,
            functions_by_name,
            guards_by_name,
            queued_animations,
            runtime_assets,
            animation_durations,
            collider_bounds,
            delayed_actions,
            ui_queue,
            owner,
            scope,
            continuous_delta_seconds,
            commands,
            scene_entities,
            0,
        ) {
            ActionSequenceResult::Suspended => ActionSequenceResult::Suspended,
            ActionSequenceResult::Completed | ActionSequenceResult::Returned => {
                ActionSequenceResult::Completed
            }
        };
    }
    if let Statement::PoolRelease(release) = action {
        release_pool_action(
            release,
            object_pools,
            scope.as_deref_mut(),
            commands,
            scene_entities,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::Delete { target } = action {
        delete_scene_object(
            target,
            object_pools,
            scope.as_deref_mut(),
            commands,
            scene_entities,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::If(statement) = action {
        let selected_actions = if condition_matches_scoped(
            &statement.condition,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        ) {
            &statement.actions
        } else {
            &statement.else_actions
        };
        for nested_action in selected_actions {
            let result = apply_key_action(
                nested_action,
                transforms_by_name,
                vars,
                object_pools,
                camera_system.as_deref_mut(),
                functions_by_name,
                guards_by_name,
                queued_animations,
                runtime_assets,
                animation_durations,
                collider_bounds,
                None,
                ui_queue.as_deref_mut(),
                owner.clone(),
                scope.as_deref_mut(),
                continuous_delta_seconds,
                commands,
                scene_entities,
            );
            if result.should_stop_parent() {
                return result;
            }
        }
        return ActionSequenceResult::Completed;
    }

    for (
        entity,
        scene_entity,
        mut transform,
        gltf,
        current_animation,
        visibility,
        character_controller,
        mut character_motor,
    ) in &mut scene_entities.p1()
    {
        match action {
            Statement::Animate(animation)
                if target_matches_alias(
                    &animation.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                let already_queued =
                    queued_animations
                        .get(&entity)
                        .is_some_and(|(clip, looped)| {
                            *looped == animation.looped
                                && requested_animation_names_match(clip, &animation.clip)
                        });
                let already_current = animation.looped
                    && queued_animations.get(&entity).is_none()
                    && current_animation.is_some_and(|current| {
                        current_animation_matches(current, &animation.clip, animation.looped)
                    });
                if already_queued || already_current {
                    continue;
                }
                if let Some(gltf) = gltf {
                    commands.entity(entity).insert(AnimationToPlay {
                        clip: animation.clip.clone(),
                        looped: animation.looped,
                        speed: animation.speed,
                        gltf: gltf.gltf.clone(),
                    });
                    queued_animations.insert(entity, (animation.clip.clone(), animation.looped));
                }
            }
            Statement::AnimationSpeed(animation_speed)
                if target_matches_alias(
                    &animation_speed.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                commands
                    .entity(entity)
                    .insert(animation_speed_override(animation_speed));
            }
            Statement::LookAt { target, subject }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(subject_transform) =
                    lookup_subject_transform(subject, transforms_by_name)
                {
                    look_at_scenemax_forward(&mut transform, subject_transform.translation);
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                }
            }
            Statement::Position(position)
                if target_matches_alias(
                    &position.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(translation) = evaluate_position_statement(position, transforms_by_name)
                {
                    transform.translation = translation;
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                }
            }
            Statement::Turn(turn)
                if target_matches_alias(
                    &turn.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(delta_seconds) = continuous_delta_seconds {
                    let timed_turn = timed_turn_from_statement(turn);
                    transform.rotate_y(timed_turn.radians_per_second * delta_seconds);
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                } else {
                    commands
                        .entity(entity)
                        .insert(timed_turn_from_statement(turn));
                }
            }
            Statement::Move(movement)
                if target_matches_alias(
                    &movement.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                let timed_move = timed_move_from_statement(movement, &transform);
                if let (Some(character_controller), Some(character_motor)) =
                    (character_controller, character_motor.as_deref_mut())
                {
                    set_character_move_intent(
                        character_motor,
                        character_controller,
                        movement,
                        &transform,
                        continuous_delta_seconds,
                    );
                    if let Some(delta_seconds) = continuous_delta_seconds {
                        transform.translation += timed_move.velocity * delta_seconds;
                        sync_live_transform(
                            transforms_by_name,
                            object_pools,
                            scope.as_deref(),
                            &scene_entity.name,
                            *transform,
                        );
                    } else {
                        append_timed_move(commands, entity, timed_move);
                    }
                } else {
                    if let Some(delta_seconds) = continuous_delta_seconds {
                        transform.translation += timed_move.velocity * delta_seconds;
                        sync_live_transform(
                            transforms_by_name,
                            object_pools,
                            scope.as_deref(),
                            &scene_entity.name,
                            *transform,
                        );
                    } else {
                        append_timed_move(commands, entity, timed_move);
                    }
                }
            }
            Statement::MoveTo(move_to)
                if target_matches_alias(
                    &move_to.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(timed_move) =
                    timed_move_to_from_statement(move_to, &transform, transforms_by_name)
                {
                    if let Some(delta_seconds) = continuous_delta_seconds {
                        let delta = delta_seconds.min(timed_move.remaining_seconds);
                        transform.translation += timed_move.velocity * delta;
                        sync_live_transform(
                            transforms_by_name,
                            object_pools,
                            scope.as_deref(),
                            &scene_entity.name,
                            *transform,
                        );
                    } else {
                        append_timed_move(commands, entity, timed_move);
                    }
                }
            }
            Statement::CharacterJump(jump)
                if target_matches_alias(
                    &jump.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(character_motor) = character_motor.as_deref_mut() {
                    set_character_jump_intent(character_motor, jump);
                    commands
                        .entity(entity)
                        .insert(timed_jump_from_statement(jump, &transform));
                } else {
                    commands
                        .entity(entity)
                        .insert(timed_jump_from_statement(jump, &transform));
                }
            }
            Statement::CharacterMode(character_mode)
                if target_matches_alias(
                    &character_mode.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                commands
                    .entity(entity)
                    .insert(PendingCharacterMode(character_mode.clone()));
            }
            Statement::ClearCharacterMode { target }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                clear_character_mode(commands, entity);
            }
            Statement::CharacterIgnore(ignore)
                if target_matches_alias(
                    &ignore.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                tracing::debug!(
                    target = ignore.target,
                    ignored = ignore.ignored,
                    "SceneMax character.ignore is handled by collision layers"
                );
            }
            Statement::PhysicsImpulse(impulse)
                if target_matches_alias(
                    &impulse.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                apply_physics_impulse(commands, entity, &transform, impulse);
            }
            Statement::PhysicsStop { target }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                apply_physics_stop(commands, entity);
            }
            Statement::PhysicsThrowAt(throw_at)
                if target_matches_alias(
                    &throw_at.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                apply_physics_throw_at(
                    commands,
                    entity,
                    &transform,
                    throw_at,
                    vars,
                    transforms_by_name,
                );
            }
            Statement::Visibility { target, visible }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(mut visibility) = visibility {
                    *visibility = if *visible {
                        Visibility::Inherited
                    } else {
                        Visibility::Hidden
                    };
                } else {
                    commands.entity(entity).insert(if *visible {
                        Visibility::Inherited
                    } else {
                        Visibility::Hidden
                    });
                }
            }
            _ => {}
        }
    }
    ActionSequenceResult::Completed
}

pub(super) fn animation_speed_condition_matches(
    animation_speed: &AnimationSpeedStatement,
    vars: &SceneMaxVars,
    object_pools: &SceneMaxObjectPools,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
) -> bool {
    let Some(condition) = animation_speed.condition.as_ref() else {
        return true;
    };
    let mut scoped_vars = SceneMaxVars(vars.0.clone());
    let frames = scene_entities
        .p1()
        .iter()
        .find_map(|(_, scene_entity, _, _, current_animation, _, _, _)| {
            target_matches_alias(
                &animation_speed.target,
                &scene_entity.name,
                object_pools,
                None,
            )
            .then(|| {
                current_animation
                    .map(current_animation_percent)
                    .unwrap_or_default()
            })
        })
        .unwrap_or_default();
    scoped_vars.0.insert("frames".to_owned(), frames);
    scoped_vars
        .0
        .insert(format!("{}.frames", animation_speed.target), frames);
    condition_matches(
        condition,
        &scoped_vars,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
}

pub(super) fn apply_function_by_name(
    name: &str,
    args: &[String],
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    mut camera_system: Option<&mut SceneMaxCameraSystem>,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    queued_animations: &mut HashMap<Entity, (String, bool)>,
    runtime_assets: &SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    delayed_actions: Option<&mut DelayedActionQueue>,
    ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    depth: usize,
) -> ActionSequenceResult {
    if depth > 8 {
        tracing::warn!(name, "skipping deeply recursive SceneMax run");
        return ActionSequenceResult::Completed;
    }
    let Some(function) = functions_by_name.get(name) else {
        tracing::debug!(
            name,
            "SceneMax function is not implemented or was not parsed"
        );
        return ActionSequenceResult::Completed;
    };
    if !function_guard_matches(
        function,
        args,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        Some(collider_bounds),
    ) {
        tracing::debug!(name, "SceneMax function guard is false");
        return ActionSequenceResult::Completed;
    }

    let actions = instantiate_function_actions(function, args);
    let mut function_scope = scope.cloned().unwrap_or_default();
    apply_action_sequence(
        &actions,
        transforms_by_name,
        vars,
        object_pools,
        camera_system.as_deref_mut(),
        functions_by_name,
        guards_by_name,
        queued_animations,
        runtime_assets,
        animation_durations,
        collider_bounds,
        delayed_actions,
        ui_queue,
        owner,
        Some(&mut function_scope),
        continuous_delta_seconds,
        commands,
        scene_entities,
    )
}

pub(super) fn function_guard_matches(
    function: &FunctionRuntime,
    args: &[String],
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let Some(guard) = &function.guard else {
        return true;
    };
    let guard = substitute_function_condition(function, args, guard);
    condition_matches(
        &guard,
        vars,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
}

pub(super) fn apply_transform_aliases(
    transforms_by_name: &mut HashMap<String, Transform>,
    object_pools: &SceneMaxObjectPools,
) {
    for (alias, target) in &object_pools.aliases {
        if let Some(transform) = transforms_by_name.get(target).copied() {
            transforms_by_name.insert(alias.clone(), transform);
        }
    }
}

pub(super) fn apply_scoped_transform_aliases(
    transforms_by_name: &mut HashMap<String, Transform>,
    scope: Option<&SceneMaxScopeFrame>,
) {
    let Some(scope) = scope else {
        return;
    };
    for (alias, target) in &scope.aliases {
        if let Some(transform) = transforms_by_name.get(target).copied() {
            transforms_by_name.insert(alias.clone(), transform);
        }
    }
}

pub(super) fn resolve_object_alias(
    name: &str,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
) -> String {
    scope
        .and_then(|scope| scope.aliases.get(name).cloned())
        .or_else(|| object_pools.aliases.get(name).cloned())
        .unwrap_or_else(|| name.to_owned())
}

pub(super) fn target_matches_alias(
    target: &str,
    scene_name: &str,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
) -> bool {
    resolve_object_alias(target, object_pools, scope) == scene_name
}

pub(super) fn sync_live_transform(
    transforms_by_name: &mut HashMap<String, Transform>,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
    scene_name: &str,
    transform: Transform,
) {
    transforms_by_name.insert(scene_name.to_owned(), transform);
    for (alias, target) in &object_pools.aliases {
        if target == scene_name {
            transforms_by_name.insert(alias.clone(), transform);
        }
    }
    if let Some(scope) = scope {
        for (alias, target) in &scope.aliases {
            if target == scene_name {
                transforms_by_name.insert(alias.clone(), transform);
            }
        }
    }
}

pub(super) fn acquire_pool_member(
    pool: &str,
    object_pools: &mut SceneMaxObjectPools,
) -> Option<String> {
    let runtime = object_pools.pools.get_mut(pool)?;
    let member = runtime.available.pop()?;
    runtime.in_use.insert(member.clone());
    Some(member)
}

pub(super) fn release_pool_action(
    release: &PoolReleaseStatement,
    object_pools: &mut SceneMaxObjectPools,
    scope: Option<&mut SceneMaxScopeFrame>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    let mut scope = scope;
    let target = resolve_object_alias(&release.target, object_pools, scope.as_deref());
    if release_pool_member(&release.pool, &target, object_pools) {
        if let Some(scope) = scope.as_deref_mut() {
            scope.aliases.retain(|_, value| value != &target);
        }
        hide_and_stop_scene_entity(&target, commands, scene_entities);
    }
}

pub(super) fn delete_scene_object(
    target: &str,
    object_pools: &mut SceneMaxObjectPools,
    scope: Option<&mut SceneMaxScopeFrame>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    let mut scope = scope;
    let target = resolve_object_alias(target, object_pools, scope.as_deref());
    if release_pooled_member_by_name(&target, object_pools) {
        if let Some(scope) = scope.as_deref_mut() {
            scope.aliases.retain(|_, value| value != &target);
        }
        hide_and_stop_scene_entity(&target, commands, scene_entities);
        return;
    }
    object_pools.aliases.retain(|_, value| value != &target);
    if let Some(scope) = scope.as_deref_mut() {
        scope.aliases.retain(|_, value| value != &target);
    }
    for (entity, scene_entity, _, _, _, _, _, _) in &mut scene_entities.p1() {
        if scene_entity.name == target {
            commands.entity(entity).despawn();
            break;
        }
    }
}

pub(super) fn release_pool_member(
    pool: &str,
    target: &str,
    object_pools: &mut SceneMaxObjectPools,
) -> bool {
    let Some(runtime) = object_pools.pools.get_mut(pool) else {
        return false;
    };
    if !runtime.members.contains(target) {
        return false;
    }
    runtime.in_use.remove(target);
    if !runtime.available.iter().any(|member| member == target) {
        runtime.available.push(target.to_owned());
    }
    object_pools.aliases.retain(|_, value| value != target);
    true
}

pub(super) fn release_pooled_member_by_name(
    target: &str,
    object_pools: &mut SceneMaxObjectPools,
) -> bool {
    let Some(pool_name) = object_pools
        .pools
        .iter()
        .find_map(|(name, runtime)| runtime.members.contains(target).then(|| name.clone()))
    else {
        return false;
    };
    release_pool_member(&pool_name, target, object_pools)
}

pub(super) fn hide_and_stop_scene_entity(
    target: &str,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
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
    for (entity, scene_entity, _, _, _, visibility, _, _) in &mut scene_entities.p1() {
        if scene_entity.name != target {
            continue;
        }
        if let Some(mut visibility) = visibility {
            *visibility = Visibility::Hidden;
        } else {
            commands.entity(entity).insert(Visibility::Hidden);
        }
        commands
            .entity(entity)
            .insert((LinearVelocity::ZERO, AngularVelocity::ZERO));
        break;
    }
}

pub(super) fn parent_action_tail(actions: &[Statement], index: usize) -> &[Statement] {
    actions.get(index + 1..).unwrap_or_default()
}

pub(super) fn apply_builtin_navigation_controls(
    time: Res<Time>,
    keyboard: Res<ButtonInput<KeyCode>>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut commands: Commands,
    mut players: Query<(
        Entity,
        &SceneMaxEntity,
        &mut Transform,
        Option<&SceneMaxGltf>,
        Option<&CurrentAnimation>,
        Option<&SceneMaxCharacterController>,
        Option<&mut SceneMaxCharacterMotor>,
    )>,
) {
    if startup_program.0.as_ref().is_some_and(|program| {
        program
            .statements
            .iter()
            .any(|statement| matches!(statement, Statement::KeyEvent(_)))
    }) {
        return;
    }

    let delta_seconds = time.delta_secs();
    let turn_delta = if keyboard.pressed(KeyCode::ArrowLeft) {
        BUILTIN_PLAYER_TURN_SPEED_RADIANS * delta_seconds
    } else if keyboard.pressed(KeyCode::ArrowRight) {
        -BUILTIN_PLAYER_TURN_SPEED_RADIANS * delta_seconds
    } else {
        0.0
    };

    let move_direction = if keyboard.pressed(KeyCode::ArrowUp) {
        1.0
    } else if keyboard.pressed(KeyCode::ArrowDown) {
        -1.0
    } else {
        0.0
    };

    let should_restore_idle = keyboard.just_released(KeyCode::ArrowUp);
    if turn_delta == 0.0 && move_direction == 0.0 && !should_restore_idle {
        return;
    }

    for (
        entity,
        scene_entity,
        mut transform,
        gltf,
        current_animation,
        character_controller,
        character_motor,
    ) in &mut players
    {
        if scene_entity.name != "player1" {
            continue;
        }

        if turn_delta != 0.0 {
            transform.rotate_y(turn_delta);
        }

        if let (Some(character_controller), Some(mut character_motor)) =
            (character_controller, character_motor)
        {
            if move_direction != 0.0 {
                let direction = horizontal_forward(&transform) * move_direction;
                set_character_motion(
                    &mut character_motor,
                    direction,
                    BUILTIN_PLAYER_MOVE_SPEED / character_controller.move_speed.max(0.001),
                    CHARACTER_INPUT_TTL_SECONDS,
                );
            }
        } else if move_direction != 0.0 {
            let direction = horizontal_forward(&transform) * move_direction;
            transform.translation += direction * BUILTIN_PLAYER_MOVE_SPEED * delta_seconds;
        }

        if move_direction > 0.0 {
            queue_builtin_player_animation(
                &mut commands,
                entity,
                gltf,
                current_animation,
                "run_sword",
                true,
            );
        } else if should_restore_idle {
            queue_builtin_player_animation(
                &mut commands,
                entity,
                gltf,
                current_animation,
                "idle2",
                true,
            );
        }
    }
}

pub(super) fn key_code_from_scenemax(key: &str) -> Option<KeyCode> {
    match key.to_ascii_lowercase().as_str() {
        "space" => Some(KeyCode::Space),
        "enter" | "return" => Some(KeyCode::Enter),
        "up" => Some(KeyCode::ArrowUp),
        "down" => Some(KeyCode::ArrowDown),
        "left" => Some(KeyCode::ArrowLeft),
        "right" => Some(KeyCode::ArrowRight),
        "a" => Some(KeyCode::KeyA),
        "b" => Some(KeyCode::KeyB),
        "c" => Some(KeyCode::KeyC),
        "d" => Some(KeyCode::KeyD),
        "q" => Some(KeyCode::KeyQ),
        "s" => Some(KeyCode::KeyS),
        "w" => Some(KeyCode::KeyW),
        "x" => Some(KeyCode::KeyX),
        "z" => Some(KeyCode::KeyZ),
        _ => None,
    }
}

pub(super) fn apply_initial_assignments(program: &Program, vars: &mut SceneMaxVars) {
    let guards_by_name = collect_guards_by_name(program);
    for statement in &program.statements {
        if let Statement::Assignment(assignment) = statement {
            apply_assignment(assignment, vars, None, &guards_by_name, None);
        }
    }
}

pub(super) fn apply_logger_statement(
    logger: &LoggerStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) {
    let message = match &logger.message {
        LoggerMessage::Text(text) => text.clone(),
        LoggerMessage::Value(value) => resolve_assignment_value_scoped_with_guards(
            value,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        )
        .map(format_scenemax_number)
        .unwrap_or_else(|| "null".to_owned()),
    };
    write_runtime_log_line(logger.level, &message);
}

pub(super) struct RuntimeVmSpatial<'a> {
    transforms_by_name: Option<&'a HashMap<String, Transform>>,
    collider_bounds: Option<&'a SceneMaxColliderBounds>,
}

impl SceneMaxVmSpatial for RuntimeVmSpatial<'_> {
    fn symbol_value(&self, name: &str) -> Option<f32> {
        coordinate_value_from_name(name, self.transforms_by_name?)
    }

    fn distance(&self, left: &str, right: &str) -> Option<f32> {
        let transforms_by_name = self.transforms_by_name?;
        let left = transforms_by_name.get(left)?;
        let right = transforms_by_name.get(right)?;
        Some(left.translation.distance(right.translation))
    }

    fn collision_matches(&self, sources: &[String], target: &str) -> bool {
        collision_condition_matches(
            sources,
            target,
            self.transforms_by_name,
            self.collider_bounds,
        )
    }
}

pub(super) fn format_scenemax_number(value: f32) -> String {
    scenemax_runtime_vm_core::format_scenemax_number(value)
}

pub(super) fn apply_assignment(
    assignment: &scenemax_parser::AssignmentStatement,
    vars: &mut SceneMaxVars,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    guards_by_name: &HashMap<String, Condition>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    apply_assignment_scoped(
        assignment,
        vars,
        None,
        transforms_by_name,
        guards_by_name,
        collider_bounds,
        false,
    )
}

pub(super) fn apply_assignment_scoped(
    assignment: &scenemax_parser::AssignmentStatement,
    vars: &mut SceneMaxVars,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    guards_by_name: &HashMap<String, Condition>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    force_local: bool,
) -> Option<f32> {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    let Some(result) = scenemax_runtime_vm_core::apply_assignment_with_spatial(
        assignment,
        vars,
        scope.as_deref_mut(),
        guards_by_name,
        &spatial,
        force_local,
    ) else {
        tracing::debug!(
            name = %assignment.name,
            value = ?assignment.value,
            "SceneMax assignment value is not known yet"
        );
        return None;
    };
    write_state_assignment_probe(&assignment.name, result.previous, result.value, force_local);
    Some(result.value)
}

pub(super) fn resolve_assignment_value(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    transforms_by_name: Option<&HashMap<String, Transform>>,
) -> Option<f32> {
    resolve_assignment_value_with_guards(value, vars, &HashMap::new(), transforms_by_name, None)
}

pub(super) fn resolve_assignment_value_with_guards(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    scenemax_runtime_vm_core::resolve_assignment_value_with_guards(
        value,
        vars,
        guards_by_name,
        &spatial,
    )
}

pub(super) fn resolve_assignment_value_scoped_with_guards(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    scenemax_runtime_vm_core::resolve_assignment_value_scoped_with_guards(
        value,
        vars,
        scope,
        guards_by_name,
        &spatial,
    )
}

pub(super) fn condition_matches(
    condition: &Condition,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    scenemax_runtime_vm_core::condition_matches(condition, vars, guards_by_name, &spatial)
}

pub(super) fn condition_matches_scoped(
    condition: &Condition,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    scenemax_runtime_vm_core::condition_matches_scoped(
        condition,
        vars,
        scope,
        guards_by_name,
        &spatial,
    )
}

pub(super) fn when_condition_matches(
    condition: &Condition,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) -> bool {
    match condition {
        Condition::Collision { sources, target } => {
            physics_contact_condition_matches(
                sources,
                target,
                physics_contacts,
                object_pools,
                transforms_by_name,
            ) || collision_condition_matches(sources, target, transforms_by_name, collider_bounds)
        }
        Condition::Boolean(value) => *value,
        Condition::Not(condition) => !when_condition_matches(
            condition,
            vars,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
            physics_contacts,
            object_pools,
        ),
        Condition::And(conditions) => conditions.iter().all(|condition| {
            when_condition_matches(
                condition,
                vars,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
                physics_contacts,
                object_pools,
            )
        }),
        Condition::Or(conditions) => conditions.iter().any(|condition| {
            when_condition_matches(
                condition,
                vars,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
                physics_contacts,
                object_pools,
            )
        }),
        condition => condition_matches(
            condition,
            vars,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
    }
}

pub(super) fn condition_contains_collision(condition: &Condition) -> bool {
    match condition {
        Condition::Collision { .. } => true,
        Condition::Not(condition) => condition_contains_collision(condition),
        Condition::And(conditions) | Condition::Or(conditions) => {
            conditions.iter().any(condition_contains_collision)
        }
        _ => false,
    }
}

#[cfg(test)]
pub(super) fn physics_contact_matches(
    sources: &[String],
    target: &str,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) -> bool {
    let target_candidates = collision_reference_candidates_with_alias(target, object_pools);
    sources.iter().any(|source| {
        let source_candidates = collision_reference_candidates_with_alias(source, object_pools);
        source_candidates.iter().any(|source_name| {
            target_candidates.iter().any(|target_name| {
                active_physics_contact_matches(source_name, target_name, physics_contacts)
            })
        })
    })
}

pub(super) fn physics_contact_condition_matches(
    sources: &[String],
    target: &str,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
    transforms_by_name: Option<&HashMap<String, Transform>>,
) -> bool {
    let target_candidates = collision_reference_candidates_with_alias(target, object_pools);
    sources.iter().any(|source| {
        let source_candidates = collision_reference_candidates_with_alias(source, object_pools);
        source_candidates.iter().any(|source_name| {
            target_candidates.iter().any(|target_name| {
                active_physics_contact_matches(source_name, target_name, physics_contacts)
                    && transforms_by_name.is_none_or(|transforms_by_name| {
                        player_hitbox_owner_distance_allows(
                            source_name,
                            target_name,
                            transforms_by_name,
                        )
                    })
            })
        })
    })
}

pub(super) fn collision_reference_candidates_with_alias(
    reference: &str,
    object_pools: &SceneMaxObjectPools,
) -> Vec<String> {
    let mut candidates = collision_reference_candidates(reference);
    let resolved = resolve_object_alias(reference, object_pools, None);
    if resolved != reference {
        candidates.extend(collision_reference_candidates(&resolved));
    }
    candidates.sort();
    candidates.dedup();
    candidates
}

pub(super) fn active_physics_contact_matches(
    source: &str,
    target: &str,
    physics_contacts: &SceneMaxPhysicsContacts,
) -> bool {
    if physics_contacts
        .active_pairs
        .contains(&normalized_collision_pair(source, target))
    {
        return true;
    }

    if !is_owner_level_collision_reference(source) && !is_owner_level_collision_reference(target) {
        return false;
    }

    let expected_owner_pair =
        normalized_collision_pair(&collision_owner(source), &collision_owner(target));
    physics_contacts.active_pairs.iter().any(|(left, right)| {
        normalized_collision_pair(&collision_owner(left), &collision_owner(right))
            == expected_owner_pair
    })
}

pub(super) fn is_owner_level_collision_reference(reference: &str) -> bool {
    let normalized = reference.trim().trim_matches('"');
    collision_owner(normalized) == normalized
}

pub(super) fn normalized_collision_pair(left: &str, right: &str) -> (String, String) {
    if left <= right {
        (left.to_owned(), right.to_owned())
    } else {
        (right.to_owned(), left.to_owned())
    }
}
