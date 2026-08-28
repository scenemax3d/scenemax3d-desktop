use super::*;
use bevy::transform::commands::BuildChildrenTransformExt;
use serde::Deserialize;

pub(super) fn apply_startup_runs(
    program: &Program,
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    runtime_assets: &mut SceneMaxRuntimeAssets,
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
        .take_while(|statement| {
            !matches!(
                statement,
                Statement::WaitForKey { .. } | Statement::SwitchTo { .. }
            )
        })
        .filter(|statement| is_startup_action(statement))
        .cloned()
        .collect::<Vec<_>>();
    write_runtime_diagnostic_line(format!(
        "STARTUP:RUNS collected={} actions={}",
        actions.len(),
        describe_statement_list(&actions)
    ));
    let _ = apply_startup_action_sequence(
        &actions,
        commands,
        vars,
        object_pools,
        camera_system,
        runtime_assets,
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
        Statement::LightDecl(_)
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
    runtime_assets: &mut SceneMaxRuntimeAssets,
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
            Statement::KeyEvent(event) => {
                register_key_event(&mut delayed_actions.registered_key_events, event.clone());
            }
            Statement::WhenEvent(event) => {
                register_when_event(&mut delayed_actions.registered_when_events, event.clone());
            }
            Statement::RunEvery { .. } => {
                register_run_every(&mut delayed_actions.registered_run_every, action.clone());
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
                    write_runtime_diagnostic_line(format!(
                        "FUNCTION:MISS phase=startup_sequence name={name}"
                    ));
                    tracing::debug!(name, "startup SceneMax function was not parsed");
                    continue;
                };
                let resolved_args = resolve_call_args(
                    args,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                if !function_guard_matches(
                    function,
                    &resolved_args,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    write_runtime_diagnostic_line(format!(
                        "FUNCTION:SKIP phase=startup_sequence name={name} reason=guard_false"
                    ));
                    tracing::debug!(name, "startup SceneMax function guard is false");
                    continue;
                }
                write_runtime_diagnostic_line(format!(
                    "FUNCTION:RUN phase=startup_sequence name={name} actions={}",
                    describe_statement_list(&function.actions)
                ));
                let function_actions = actions_with_parent_continuation(
                    instantiate_function_actions(function, &resolved_args),
                    parent_action_tail(actions, index),
                );
                let result = apply_startup_action_sequence(
                    &function_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    runtime_assets,
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
                    runtime_assets,
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
                    runtime_assets,
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
                    runtime_assets,
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
                        runtime_assets,
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
            Statement::AnimationControllerAction(action)
                if action.action == AnimationControllerAction::Run =>
            {
                let seconds =
                    animation_controller_duration_for_action(action, runtime_assets, None)
                        .unwrap_or(0.001)
                        .max(0.001);
                apply_startup_animation_controller_action(
                    action,
                    commands,
                    runtime_assets,
                    entities_by_name,
                    gltfs_by_name,
                );
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
            Statement::ThrowMotionApply(apply) if !apply.async_run => {
                let seconds = throw_motion_duration_for_apply(apply, runtime_assets)
                    .unwrap_or(0.001)
                    .max(0.001);
                let result = apply_startup_action(
                    action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    runtime_assets,
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
            action
                if resolved_blocking_timed_action_seconds(
                    action,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                )
                .is_some() =>
            {
                let seconds = resolved_blocking_timed_action_seconds(
                    action,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                )
                .unwrap_or_default();
                write_runtime_diagnostic_line(format!(
                    "STARTUP:BLOCKING action={} seconds={} tail={}",
                    describe_statement(action),
                    format_scenemax_number(seconds),
                    describe_statement_list(&actions[index + 1..])
                ));
                let result = apply_startup_action(
                    action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    runtime_assets,
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
                    runtime_assets,
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
    runtime_assets: &mut SceneMaxRuntimeAssets,
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
        write_runtime_diagnostic_line(format!("FUNCTION:MISS phase=startup name={name}"));
        tracing::debug!(name, "startup SceneMax function was not parsed");
        return ActionSequenceResult::Completed;
    };
    let resolved_args = resolve_call_args(
        args,
        vars,
        None,
        guards_by_name,
        Some(transforms_by_name),
        None,
    );
    if !function_guard_matches(
        function,
        &resolved_args,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        None,
    ) {
        write_runtime_diagnostic_line(format!(
            "FUNCTION:SKIP phase=startup name={name} reason=guard_false"
        ));
        tracing::debug!(name, "startup SceneMax function guard is false");
        return ActionSequenceResult::Completed;
    }

    write_runtime_diagnostic_line(format!(
        "FUNCTION:RUN phase=startup name={name} actions={}",
        describe_statement_list(&function.actions)
    ));
    tracing::info!(name, "running SceneMax startup function");
    let actions = instantiate_function_actions(function, &resolved_args);
    for action in &actions {
        let result = apply_startup_action(
            action,
            commands,
            vars,
            object_pools,
            camera_system,
            runtime_assets,
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
    runtime_assets: &mut SceneMaxRuntimeAssets,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
    depth: usize,
) -> ActionSequenceResult {
    match action {
        Statement::ModelDecl {
            name,
            resource,
            options,
        } => {
            register_cinematic_camera_var(name, resource, camera_system);
            if let Some(entity) = entities_by_name.get(name) {
                commands.entity(*entity).insert(if options.hidden {
                    Visibility::Hidden
                } else {
                    Visibility::Inherited
                });
            }
            ActionSequenceResult::Completed
        }
        Statement::LightDecl(light) => {
            let (_entity, transform) = spawn_scenemax_light_decl(
                commands,
                light,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            transforms_by_name.insert(light.name.clone(), transform);
            ActionSequenceResult::Completed
        }
        Statement::LightProbeAdd(probe) => {
            let (_entity, transform) = apply_light_probe_add(
                commands,
                probe,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            transforms_by_name.insert(probe.name.clone(), transform);
            ActionSequenceResult::Completed
        }
        Statement::SetEnvironmentShader { shader } => {
            let shader_name = resolve_shader_name(
                shader,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            apply_environment_shader(commands, shader_name, runtime_assets);
            ActionSequenceResult::Completed
        }
        Statement::SetShader(shader) => {
            let shader_name = resolve_shader_name(
                &shader.shader,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            if let Some(entity) = entities_by_name.get(&shader.target) {
                apply_entity_shader(commands, *entity, shader_name, runtime_assets);
            } else {
                write_runtime_diagnostic_line(format!(
                    "SHADER:TARGET_MISS phase=startup target={}",
                    shader.target
                ));
            }
            ActionSequenceResult::Completed
        }
        Statement::Weapon(weapon) => {
            enqueue_weapon_action(weapon, runtime_assets);
            ActionSequenceResult::Completed
        }
        Statement::AnimationControllerAction(action) => {
            apply_startup_animation_controller_action(
                action,
                commands,
                runtime_assets,
                entities_by_name,
                gltfs_by_name,
            );
            ActionSequenceResult::Completed
        }
        Statement::AnimationControllerEvent(event) => {
            register_animation_controller_event(
                event,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
                runtime_assets,
            );
            ActionSequenceResult::Completed
        }
        Statement::ThrowMotionApply(apply) => {
            enqueue_throw_motion_application(apply, runtime_assets);
            ActionSequenceResult::Completed
        }
        Statement::ThrowMotionEvent(event) => {
            register_throw_motion_event(event, runtime_assets);
            ActionSequenceResult::Completed
        }
        Statement::Assignment(assignment)
        | Statement::SharedAssignment(assignment)
        | Statement::LocalAssignment(assignment) => {
            if let AssignmentValue::CameraModifier(value) = &assignment.value {
                register_camera_modifier(camera_system, &assignment.name, value);
            } else if let AssignmentValue::AnimationController(value) = &assignment.value {
                register_animation_controller_assignment(&assignment.name, value, runtime_assets);
            } else if let AssignmentValue::ThrowMotion(value) = &assignment.value {
                register_throw_motion_assignment(
                    &assignment.name,
                    value,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                    runtime_assets,
                );
            } else {
                apply_assignment(
                    assignment,
                    vars,
                    Some(transforms_by_name),
                    guards_by_name,
                    None,
                );
            }
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
        Statement::CameraModifierApply(apply) => {
            let overrides = resolved_camera_modifier_overrides(
                &apply.overrides,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            apply_camera_modifier(camera_system, &apply.target, &apply.modifier, &overrides);
            ActionSequenceResult::Completed
        }
        Statement::CameraMove(camera_move) => {
            let distance = resolve_draw_value(
                Some(&camera_move.distance_value),
                camera_move.distance,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            let duration_seconds = resolve_duration_value(
                &camera_move.duration_value,
                camera_move.duration_seconds,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            append_timed_camera_move(
                commands,
                timed_camera_move_from_statement_resolved(camera_move, distance, duration_seconds),
            );
            write_runtime_diagnostic_line(format!(
                "CAMERA:MOVE queue source=startup axis={} distance={} duration={} async={}",
                axis_label(camera_move.axis),
                format_scenemax_number(distance),
                format_scenemax_number(duration_seconds),
                camera_move.async_run as u8
            ));
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
        Statement::Audio(audio) => {
            apply_audio_statement(
                audio,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
                runtime_assets,
                commands,
            );
            ActionSequenceResult::Completed
        }
        Statement::DebugMode { enabled } => {
            set_debug_mode(commands, *enabled);
            ActionSequenceResult::Completed
        }
        Statement::UiLoad { name } => {
            write_runtime_diagnostic_line(format!("UI:QUEUE load {name} source=startup"));
            ui_queue
                .actions
                .push(SceneMaxUiAction::Load { name: name.clone() });
            ActionSequenceResult::Completed
        }
        Statement::ChannelDraw(draw) => {
            ui_queue.actions.push(scenemax_draw_action_from_statement(
                draw,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            ));
            ActionSequenceResult::Completed
        }
        Statement::Print(print) => {
            ui_queue.actions.push(scenemax_print_action_from_statement(
                print,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            ));
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
            let value = resolve_ui_property_value(
                &property.value,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            ui_queue.actions.push(SceneMaxUiAction::SetProperty {
                target: property.target.clone(),
                property: property.property.clone(),
                value,
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
                runtime_assets,
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
                    runtime_assets,
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
                    runtime_assets,
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
                        runtime_assets,
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
                        runtime_assets,
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
                        runtime_assets,
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
                let speed = resolve_animation_speed_value(
                    &animation.speed_value,
                    animation.speed,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                commands.entity(*entity).insert(AnimationToPlay {
                    clip: animation.clip.clone(),
                    runtime_clip: animation.clip.clone(),
                    looped: animation.looped,
                    speed,
                    gltf: gltf.clone(),
                    target_model_resource: None,
                    baked_external: None,
                    bake_request: None,
                    external_retarget: Default::default(),
                    external_source: false,
                    tried_external_source: false,
                    visual_transform_preapplied: false,
                    retarget_wait_logged: false,
                });
            }
            ActionSequenceResult::Completed
        }
        Statement::SpritePlay(sprite_play) => {
            if let Some(entity) = entities_by_name.get(&sprite_play.target) {
                commands.entity(*entity).insert(resolved_sprite_animation(
                    sprite_play,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ));
                write_runtime_diagnostic_line(format!(
                    "started sprite animation target={} frames={}..{} duration={:.3}s loop={}",
                    sprite_play.target,
                    sprite_play.from_frame,
                    sprite_play.to_frame,
                    sprite_play.duration_seconds,
                    sprite_play.looped
                ));
            } else {
                write_runtime_diagnostic_line(format!(
                    "sprite animation target={} was not found",
                    sprite_play.target
                ));
            }
            ActionSequenceResult::Completed
        }
        Statement::EffekseerPlay(play) => {
            if let Some(entity) = entities_by_name.get(&play.target) {
                if let Some(translation) = resolved_effekseer_play_translation(
                    play,
                    vars,
                    None,
                    guards_by_name,
                    transforms_by_name,
                    None,
                ) {
                    let mut transform = transforms_by_name
                        .get(&play.target)
                        .copied()
                        .unwrap_or_default();
                    transform.translation = translation;
                    commands.entity(*entity).insert(transform);
                    transforms_by_name.insert(play.target.clone(), transform);
                }
                commands.entity(*entity).insert(resolved_effekseer_playback(
                    play,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ));
                write_effekseer_bridge_play(&play.target, play);
            } else {
                write_runtime_diagnostic_line(format!(
                    "EFFEKSEER:PLAY_MISS target={} renderer={}",
                    play.target,
                    effekseer_renderer_label()
                ));
            }
            ActionSequenceResult::Completed
        }
        Statement::CinematicPlay(play) => {
            start_cinematic_camera(play, transforms_by_name, object_pools, None, camera_system);
            ActionSequenceResult::Completed
        }
        Statement::AnimationSpeed(animation_speed) => {
            if let Some(entity) = entities_by_name.get(&animation_speed.target) {
                commands
                    .entity(*entity)
                    .insert(resolved_animation_speed_override(
                        animation_speed,
                        vars,
                        None,
                        guards_by_name,
                        Some(transforms_by_name),
                        None,
                    ));
            }
            ActionSequenceResult::Completed
        }
        Statement::CharacterMode(character_mode) => {
            if let Some(entity) = entities_by_name.get(&character_mode.target) {
                let resolved = resolved_character_mode(
                    character_mode,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                write_runtime_diagnostic_line(format!(
                    "CHARACTER:PENDING_REQUEST phase=startup target={} gravity={} entity={:?}",
                    resolved.target,
                    resolved
                        .gravity
                        .map(format_scenemax_number)
                        .unwrap_or_else(|| "default".to_owned()),
                    entity
                ));
                commands
                    .entity(*entity)
                    .insert(PendingCharacterMode(resolved));
            } else {
                write_runtime_diagnostic_line(format!(
                    "CHARACTER:PENDING_MISS phase=startup target={}",
                    character_mode.target
                ));
            }
            ActionSequenceResult::Completed
        }
        Statement::ClearCharacterMode { target } => {
            if let Some(entity) = entities_by_name.get(target) {
                clear_character_mode(
                    commands,
                    *entity,
                    Some(target),
                    transforms_by_name.get(target),
                );
            } else {
                write_runtime_diagnostic_line(format!(
                    "CHARACTER:CLEAR_MISS phase=startup target={target}"
                ));
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
            let Some(translation) = evaluate_position_value_runtime(
                &position.position,
                vars,
                None,
                guards_by_name,
                transforms_by_name,
                None,
            ) else {
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
                let degrees = resolve_draw_value(
                    Some(&turn.degrees_value),
                    turn.degrees,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                let duration_seconds = resolve_duration_value(
                    &turn.duration_value,
                    turn.duration_seconds,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                commands
                    .entity(*entity)
                    .insert(timed_turn_from_statement_resolved(
                        turn,
                        degrees,
                        duration_seconds,
                    ));
            }
            ActionSequenceResult::Completed
        }
        Statement::Move(movement) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&movement.target),
                transforms_by_name.get(&movement.target),
            ) {
                let distance = resolve_draw_value(
                    Some(&movement.distance_value),
                    movement.distance,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                let duration_seconds = resolve_draw_value(
                    Some(&movement.duration_value),
                    movement.duration_seconds,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                append_timed_move(
                    commands,
                    *entity,
                    timed_move_from_statement_resolved(
                        movement,
                        transform,
                        distance,
                        duration_seconds,
                    ),
                );
            }
            ActionSequenceResult::Completed
        }
        Statement::MoveTo(move_to) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&move_to.target),
                transforms_by_name.get(&move_to.target),
            ) {
                if let Some(timed_move) = resolved_move_to(
                    move_to,
                    transform,
                    vars,
                    None,
                    guards_by_name,
                    transforms_by_name,
                    None,
                ) {
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
                let speed = resolve_draw_value(
                    Some(&jump.speed_value),
                    jump.speed,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                commands
                    .entity(*entity)
                    .insert(timed_jump_from_statement_resolved(jump, transform, speed));
            }
            ActionSequenceResult::Completed
        }
        Statement::PhysicsImpulse(impulse) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&impulse.target),
                transforms_by_name.get(&impulse.target),
            ) {
                let strength = resolve_draw_value(
                    Some(&impulse.strength_value),
                    impulse.strength,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                );
                apply_physics_impulse_resolved(commands, *entity, transform, impulse, strength);
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
    let Some(script_root) = startup_program.1.as_ref().or(context.script_root.as_ref()) else {
        tracing::warn!(scene, "cannot switch scene without a startup script root");
        return;
    };
    let Some(asset_root) = context.asset_root.as_ref() else {
        tracing::warn!(scene, "cannot switch scene without an asset root");
        return;
    };

    let scene_main = scene_main_path(script_root, &scene);
    write_runtime_diagnostic_line(format!(
        "switch key accepted; loading scene {scene} from {}",
        scene_main.display()
    ));
    match load_script_with_adds(&scene_main, &mut HashSet::new()) {
        Ok((program, scene_script_root)) => {
            for entity in &scene_queries.p0() {
                commands.entity(entity).despawn();
            }

            if let Ok(mut camera) = scene_queries.p1().single_mut() {
                *camera = camera_transform_from_program(&program);
            }

            retain_scene_switch_shared_vars(&mut vars, startup_program.0.as_ref(), &program);
            object_pools.aliases.clear();
            object_pools.pools.clear();
            delayed_actions.actions.clear();
            recurring_timers.remaining_by_statement.clear();
            clear_environment_shader(&mut commands);
            commands.insert_resource(ActiveActionControllers::default());
            physics_contacts.active_pairs.clear();
            collider_bounds.clear();
            apply_initial_assignments(&program, &mut vars);
            apply_camera_systems(&program, &mut camera_system);
            load_cinematic_rigs(
                &program,
                &mut camera_system,
                Some(&scene_script_root),
                context.asset_root.as_deref(),
            );
            let mut scene_ui_queue = SceneMaxUiActionQueue::default();
            let startup_gltfs = spawn_scenemax_program(
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
            commands.insert_resource(SceneMaxStartupActionState::waiting_for_gltfs(startup_gltfs));
            startup_program.0 = Some(program);
            startup_program.1 = Some(scene_script_root);
            if let Some(program) = startup_program.0.as_ref() {
                log_lifecycle_when_summary(program);
            }
            tracing::info!(scene, path = %scene_main.display(), "switched SceneMax scene");
            write_runtime_diagnostic_line(format!(
                "switched SceneMax scene {scene} from {}",
                scene_main.display()
            ));
        }
        Err(error) => {
            tracing::error!(
                scene,
                path = %scene_main.display(),
                %error,
                "failed to switch SceneMax scene"
            );
            write_runtime_diagnostic_line(format!(
                "failed to switch SceneMax scene {scene} from {}: {error}",
                scene_main.display()
            ));
        }
    }
}

pub(super) fn retain_scene_switch_shared_vars(
    vars: &mut SceneMaxVars,
    previous_program: Option<&Program>,
    next_program: &Program,
) {
    let Some(previous_program) = previous_program else {
        vars.0.clear();
        return;
    };
    let previous_shared = collect_shared_assignment_names(previous_program);
    let next_shared = collect_shared_assignment_names(next_program);
    vars.0
        .retain(|name, _| previous_shared.contains(name) && next_shared.contains(name));
}

#[cfg(test)]
mod scene_switch_tests {
    use super::*;
    use scenemax_parser::parse_program;

    #[test]
    fn scene_switch_retains_only_shared_values_declared_by_both_scenes() {
        let previous_program =
            parse_program("shared var score = 0\nshared var timer = 0\nvar life2 = 10").unwrap();
        let next_program = parse_program("shared var score = 0\nvar timer = 0").unwrap();
        let mut vars = SceneMaxVars(HashMap::from([
            ("score".to_owned(), 25.0),
            ("timer".to_owned(), 12.0),
            ("life2".to_owned(), 3.0),
        ]));

        retain_scene_switch_shared_vars(&mut vars, Some(&previous_program), &next_program);

        assert_eq!(vars.0.get("score").copied(), Some(25.0));
        assert_eq!(vars.0.get("timer"), None);
        assert_eq!(vars.0.get("life2"), None);
    }
}

pub(super) fn scene_main_path(current_script_root: &Path, scene: &str) -> PathBuf {
    current_script_root
        .parent()
        .unwrap_or(current_script_root)
        .join(scene)
        .join("main")
}

pub(super) fn apply_key_events(
    time: Res<Time>,
    keyboard: Res<ButtonInput<KeyCode>>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
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
    bone_queries: SceneMaxBoneQueries,
) {
    let Some(program) = startup_program.0.as_ref() else {
        return;
    };
    if pending_key_switch(program, &keyboard).is_some() {
        return;
    }

    let mut transforms_by_name =
        build_action_transform_map(program, &object_pools, scene_entities.p0(), &bone_queries);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    let mut polled_key_events = Vec::new();
    for (statement_index, statement) in program.statements.iter().enumerate() {
        if let Statement::KeyEvent(event) = statement {
            polled_key_events.push((
                statement_index.to_string(),
                key_event_controller_key(statement_index, event.trigger),
                event.clone(),
            ));
        }
    }
    for registered in &delayed_actions.registered_key_events.events {
        polled_key_events.push((
            format!("registered:{}", registered.id),
            registered_key_event_controller_key(registered.id, registered.event.trigger),
            registered.event.clone(),
        ));
    }

    for (statement_index, owner, event) in polled_key_events {
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
        if owner
            .as_ref()
            .is_some_and(|owner| active_controllers.running.contains(owner))
        {
            if owner
                .as_ref()
                .is_some_and(|owner| !delayed_actions_has_owner(&delayed_actions, owner))
            {
                if let Some(owner) = &owner {
                    write_runtime_diagnostic_line(format!(
                        "CTRL:KEY_STALE_OWNER_RECOVER stmt={} key={} owner={} active={} delayed={} state={}",
                        statement_index,
                        event.key,
                        describe_controller_key(owner),
                        describe_active_controllers(&active_controllers),
                        describe_delayed_queue(&delayed_actions),
                        describe_runtime_state(&vars)
                    ));
                    active_controllers.running.remove(owner);
                }
            } else {
                if runtime_verbose_logging() {
                    write_runtime_diagnostic_line(format!(
                        "KEY:SKIP_RUNNING stmt={} key={} trigger={} owner={} active={} delayed={} state={}",
                        statement_index,
                        event.key,
                        key_trigger_label(event.trigger),
                        owner
                            .as_ref()
                            .map(describe_controller_key)
                            .unwrap_or_else(|| "-".to_owned()),
                        describe_active_controllers(&active_controllers),
                        describe_delayed_queue(&delayed_actions),
                        describe_runtime_state(&vars)
                    ));
                }
                continue;
            }
        }
        if let Some(owner) = &owner {
            cancel_other_key_handlers(owner, &mut active_controllers, &mut delayed_actions);
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
        if event.trigger != KeyTrigger::Pressed || runtime_verbose_logging() {
            write_runtime_diagnostic_line(format!(
                "KEY:FIRE stmt={} key={} trigger={} actions={}",
                statement_index,
                event.key,
                key_trigger_label(event.trigger),
                describe_statement_list(&event.actions)
            ));
        }
        if runtime_verbose_logging() {
            write_runtime_diagnostic_line(format!(
                "CTRL:KEY_FIRE_CONTEXT stmt={} key={} trigger={} owner={} active={} delayed={} state={}",
                statement_index,
                event.key,
                key_trigger_label(event.trigger),
                owner
                    .as_ref()
                    .map(describe_controller_key)
                    .unwrap_or_else(|| "-".to_owned()),
                describe_active_controllers(&active_controllers),
                describe_delayed_queue(&delayed_actions),
                describe_runtime_state(&vars)
            ));
        }

        let mut queued_animations = HashMap::new();
        let continuous_delta_seconds =
            (event.trigger == KeyTrigger::Pressed).then_some(time.delta_secs());
        if let Some(owner) = &owner {
            active_controllers.running.insert(owner.clone());
        }
        let result = apply_action_sequence(
            &event.actions,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &mut runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            owner.clone(),
            None,
            continuous_delta_seconds,
            &mut commands,
            &mut scene_entities,
        );
        if !result.is_suspended()
            && let Some(owner) = owner
            && !delayed_actions_has_owner(&delayed_actions, &owner)
        {
            active_controllers.running.remove(&owner);
        }
    }
}

pub(super) fn cancel_other_key_handlers(
    owner: &SceneMaxControllerKey,
    active_controllers: &mut ActiveActionControllers,
    delayed_actions: &mut DelayedActionQueue,
) {
    if !is_key_controller(owner) {
        return;
    }
    let active_before = describe_active_controllers(active_controllers);
    let delayed_before = describe_delayed_queue(delayed_actions);
    active_controllers
        .running
        .retain(|running| !is_key_controller(running) || running == owner);
    delayed_actions
        .actions
        .retain(|delayed| !delayed_owner_is_other_key(delayed.owner.as_ref(), owner));
    let active_after = describe_active_controllers(active_controllers);
    let delayed_after = describe_delayed_queue(delayed_actions);
    if active_before != active_after || delayed_before != delayed_after {
        write_runtime_diagnostic_line(format!(
            "CTRL:KEY_CANCEL_OTHERS owner={} active_before={} active_after={} delayed_before={} delayed_after={}",
            describe_controller_key(owner),
            active_before,
            active_after,
            delayed_before,
            delayed_after
        ));
    }
}

pub(super) fn delayed_owner_is_other_key(
    delayed_owner: Option<&SceneMaxControllerKey>,
    owner: &SceneMaxControllerKey,
) -> bool {
    delayed_owner.is_some_and(is_key_controller) && delayed_owner != Some(owner)
}

fn is_key_controller(owner: &SceneMaxControllerKey) -> bool {
    matches!(
        owner,
        SceneMaxControllerKey::Key(_) | SceneMaxControllerKey::RegisteredKey(_)
    )
}

pub(super) fn delayed_actions_has_owner(
    delayed_actions: &DelayedActionQueue,
    owner: &SceneMaxControllerKey,
) -> bool {
    delayed_actions
        .actions
        .iter()
        .any(|delayed| delayed.owner.as_ref() == Some(owner))
}

pub(super) fn cancel_delayed_actions_for_owner(
    delayed_actions: &mut DelayedActionQueue,
    owner: &SceneMaxControllerKey,
) {
    let delayed_before = describe_delayed_queue(delayed_actions);
    delayed_actions
        .actions
        .retain(|delayed| delayed.owner.as_ref() != Some(owner));
    let delayed_after = describe_delayed_queue(delayed_actions);
    if delayed_before != delayed_after {
        write_runtime_diagnostic_line(format!(
            "CTRL:CANCEL_OWNER owner={} delayed_before={} delayed_after={}",
            describe_controller_key(owner),
            delayed_before,
            delayed_after
        ));
    }
}

pub(super) fn async_function_controller_key(
    actions: &[Statement],
) -> Option<SceneMaxControllerKey> {
    match actions {
        [Statement::RunFunction { name, .. }] => {
            Some(SceneMaxControllerKey::AsyncFunction(name.clone()))
        }
        _ => None,
    }
}

pub(super) fn describe_controller_key(owner: &SceneMaxControllerKey) -> String {
    match owner {
        SceneMaxControllerKey::Key(index) => format!("K{index}"),
        SceneMaxControllerKey::RegisteredKey(index) => format!("RK{index}"),
        SceneMaxControllerKey::When(index) => format!("W{index}"),
        SceneMaxControllerKey::RegisteredWhen(index) => format!("RW{index}"),
        SceneMaxControllerKey::Recurring(index) => format!("R{index}"),
        SceneMaxControllerKey::RegisteredRecurring(index) => format!("RR{index}"),
        SceneMaxControllerKey::AsyncFunction(name) => format!("A:{name}"),
    }
}

pub(super) fn describe_active_controllers(active_controllers: &ActiveActionControllers) -> String {
    if active_controllers.running.is_empty() {
        return "<empty>".to_owned();
    }
    let mut owners = active_controllers
        .running
        .iter()
        .map(describe_controller_key)
        .collect::<Vec<_>>();
    owners.sort();
    owners.join(",")
}

pub(super) fn describe_delayed_queue(delayed_actions: &DelayedActionQueue) -> String {
    if delayed_actions.actions.is_empty() {
        return "count=0".to_owned();
    }
    let entries = delayed_actions
        .actions
        .iter()
        .take(10)
        .enumerate()
        .map(|(index, delayed)| {
            let owner = delayed
                .owner
                .as_ref()
                .map(describe_controller_key)
                .unwrap_or_else(|| "-".to_owned());
            let first = delayed
                .actions
                .first()
                .map(describe_statement)
                .unwrap_or_else(|| "<empty>".to_owned());
            format!(
                "{}:{}s:{}:{}",
                index,
                format_scenemax_number(delayed.remaining_seconds.max(0.0)),
                owner,
                first
            )
        })
        .collect::<Vec<_>>()
        .join(" | ");
    format!("count={} [{}]", delayed_actions.actions.len(), entries)
}

pub(super) fn describe_runtime_state(vars: &SceneMaxVars) -> String {
    let mut names = vars.0.keys().map(String::as_str).collect::<Vec<_>>();
    names.sort_unstable();
    let entries = names
        .iter()
        .take(16)
        .map(|name| {
            let value = vars
                .0
                .get(*name)
                .map(|value| format_scenemax_number(*value))
                .unwrap_or_else(|| "null".to_owned());
            format!("{name}={value}")
        })
        .collect::<Vec<_>>()
        .join(" ");
    if vars.0.len() > 16 {
        format!("vars={} [{} ...]", vars.0.len(), entries)
    } else {
        format!("vars={} [{}]", vars.0.len(), entries)
    }
}

pub(super) fn truncate_log_field(value: String, max_chars: usize) -> String {
    let mut chars = value.chars();
    let truncated = chars.by_ref().take(max_chars).collect::<String>();
    if chars.next().is_some() {
        format!("{truncated}...")
    } else {
        truncated
    }
}

pub(super) fn describe_condition_brief(condition: &Condition) -> String {
    truncate_log_field(format!("{condition:?}"), 180)
}

pub(super) fn lifecycle_probe_condition(condition: &Condition) -> bool {
    !condition_contains_collision(condition)
}

pub(super) fn key_event_controller_key(
    statement_index: usize,
    trigger: KeyTrigger,
) -> Option<SceneMaxControllerKey> {
    (trigger == KeyTrigger::PressedOnce).then_some(SceneMaxControllerKey::Key(statement_index))
}

pub(super) fn registered_key_event_controller_key(
    event_id: usize,
    trigger: KeyTrigger,
) -> Option<SceneMaxControllerKey> {
    (trigger == KeyTrigger::PressedOnce).then_some(SceneMaxControllerKey::RegisteredKey(event_id))
}

pub(super) fn register_key_event(
    registered_key_events: &mut RegisteredKeyEvents,
    event: KeyEventStatement,
) {
    if registered_key_events
        .events
        .iter()
        .any(|registered| registered.event == event)
    {
        return;
    }
    let id = registered_key_events.next_id;
    registered_key_events.next_id += 1;
    registered_key_events
        .events
        .push(RegisteredKeyEvent { id, event });
}

pub(super) fn register_when_event(
    registered_when_events: &mut RegisteredWhenEvents,
    event: WhenEventStatement,
) {
    if registered_when_events
        .events
        .iter()
        .any(|registered| registered.event == event)
    {
        return;
    }
    let id = registered_when_events.next_id;
    registered_when_events.next_id += 1;
    registered_when_events
        .events
        .push(RegisteredWhenEvent { id, event });
}

pub(super) fn register_run_every(
    registered_run_every: &mut RegisteredRunEveryEvents,
    statement: Statement,
) {
    if registered_run_every
        .events
        .iter()
        .any(|registered| registered.statement == statement)
    {
        return;
    }
    let id = registered_run_every.next_id;
    registered_run_every.next_id += 1;
    registered_run_every
        .events
        .push(RegisteredRunEvery { id, statement });
}

#[cfg(test)]
mod key_event_controller_tests {
    use super::*;

    #[test]
    fn pressed_once_key_events_are_owned_by_statement_index() {
        assert_eq!(
            key_event_controller_key(7, KeyTrigger::PressedOnce),
            Some(SceneMaxControllerKey::Key(7))
        );
        assert_eq!(key_event_controller_key(7, KeyTrigger::Pressed), None);
        assert_eq!(key_event_controller_key(7, KeyTrigger::Released), None);
        assert_eq!(
            registered_key_event_controller_key(3, KeyTrigger::PressedOnce),
            Some(SceneMaxControllerKey::RegisteredKey(3))
        );
    }

    #[test]
    fn registering_same_key_event_is_idempotent() {
        let event = KeyEventStatement {
            key: "Q".to_owned(),
            trigger: KeyTrigger::PressedOnce,
            guard: None,
            actions: vec![Statement::Assignment(
                scenemax_parser::AssignmentStatement {
                    name: "marker".to_owned(),
                    value: AssignmentValue::Number(1.0),
                },
            )],
        };
        let mut registered = RegisteredKeyEvents::default();

        register_key_event(&mut registered, event.clone());
        register_key_event(&mut registered, event);

        assert_eq!(registered.events.len(), 1);
        assert_eq!(registered.events[0].id, 0);
    }

    #[test]
    fn registering_same_run_every_is_idempotent() {
        let statement = Statement::RunEvery {
            name: "flash_debug_marker".to_owned(),
            args: Vec::new(),
            interval_seconds: 0.5,
            interval_value: AssignmentValue::Number(0.5),
        };
        let mut registered = RegisteredRunEveryEvents::default();

        register_run_every(&mut registered, statement.clone());
        register_run_every(&mut registered, statement);

        assert_eq!(registered.events.len(), 1);
        assert_eq!(registered.events[0].id, 0);
    }

    #[test]
    fn changing_pressed_once_keys_cancels_previous_key_continuations() {
        let first = SceneMaxControllerKey::Key(1);
        let second = SceneMaxControllerKey::RegisteredKey(2);
        let recurring = SceneMaxControllerKey::Recurring(3);
        let mut active_controllers = ActiveActionControllers {
            running: HashSet::from([first.clone(), second.clone(), recurring.clone()]),
        };
        let mut delayed_actions = DelayedActionQueue {
            actions: vec![
                DelayedActions {
                    remaining_seconds: 0.1,
                    actions: vec![Statement::NoOp {
                        text: "first".to_owned(),
                    }],
                    owner: Some(first.clone()),
                    scope: None,
                },
                DelayedActions {
                    remaining_seconds: 0.1,
                    actions: vec![Statement::NoOp {
                        text: "second".to_owned(),
                    }],
                    owner: Some(second.clone()),
                    scope: None,
                },
                DelayedActions {
                    remaining_seconds: 0.1,
                    actions: vec![Statement::NoOp {
                        text: "recurring".to_owned(),
                    }],
                    owner: Some(recurring.clone()),
                    scope: None,
                },
                DelayedActions {
                    remaining_seconds: 0.1,
                    actions: vec![Statement::NoOp {
                        text: "detached async".to_owned(),
                    }],
                    owner: None,
                    scope: None,
                },
            ],
            ..Default::default()
        };

        cancel_other_key_handlers(&second, &mut active_controllers, &mut delayed_actions);

        assert!(!active_controllers.running.contains(&first));
        assert!(active_controllers.running.contains(&second));
        assert!(active_controllers.running.contains(&recurring));
        assert_eq!(delayed_actions.actions.len(), 3);
        assert!(!delayed_actions_has_owner(&delayed_actions, &first));
        assert!(delayed_actions_has_owner(&delayed_actions, &second));
        assert!(delayed_actions_has_owner(&delayed_actions, &recurring));
        assert!(
            delayed_actions
                .actions
                .iter()
                .any(|delayed| delayed.owner.is_none())
        );
    }

    #[test]
    fn async_run_function_actions_are_owned_by_function_name() {
        let owner = async_function_controller_key(&[Statement::RunFunction {
            name: "spawn_round".to_owned(),
            args: Vec::new(),
        }]);

        assert_eq!(
            owner,
            Some(SceneMaxControllerKey::AsyncFunction(
                "spawn_round".to_owned()
            ))
        );
        assert_eq!(
            async_function_controller_key(&[Statement::NoOp {
                text: "detached block".to_owned()
            }]),
            None
        );
    }

    #[test]
    fn startup_actions_preserve_async_block_before_following_declaration_and_animation() {
        let program = scenemax_parser::parse_program(
            "do async\n  audio.play \"monster_roar\"\n  wait 3 seconds\nend do\ntg => tiger3 async\ntg.\"Idle_Lie Prone\" loop",
        )
        .unwrap();

        let actions = program
            .statements
            .iter()
            .filter(|statement| is_startup_action(statement))
            .collect::<Vec<_>>();

        assert!(matches!(actions.first(), Some(Statement::Async { .. })));
        assert!(matches!(
            actions.get(1),
            Some(Statement::ModelDecl { name, resource, .. })
                if name == "tg" && resource == "tiger3"
        ));
        assert!(matches!(
            actions.get(2),
            Some(Statement::Animate(animation))
                if animation.target == "tg" && animation.clip == "Idle_Lie Prone"
        ));
    }

    #[test]
    fn cancel_delayed_actions_for_owner_keeps_unrelated_continuations() {
        let restart_owner = SceneMaxControllerKey::AsyncFunction("restart".to_owned());
        let effect_owner = SceneMaxControllerKey::AsyncFunction("effect".to_owned());
        let mut delayed_actions = DelayedActionQueue {
            actions: vec![
                DelayedActions {
                    remaining_seconds: 0.5,
                    actions: vec![Statement::NoOp {
                        text: "old restart".to_owned(),
                    }],
                    owner: Some(restart_owner.clone()),
                    scope: None,
                },
                DelayedActions {
                    remaining_seconds: 0.2,
                    actions: vec![Statement::NoOp {
                        text: "effect".to_owned(),
                    }],
                    owner: Some(effect_owner.clone()),
                    scope: None,
                },
                DelayedActions {
                    remaining_seconds: 0.1,
                    actions: vec![Statement::NoOp {
                        text: "legacy detached".to_owned(),
                    }],
                    owner: None,
                    scope: None,
                },
            ],
            ..Default::default()
        };

        cancel_delayed_actions_for_owner(&mut delayed_actions, &restart_owner);

        assert_eq!(delayed_actions.actions.len(), 2);
        assert!(!delayed_actions_has_owner(&delayed_actions, &restart_owner));
        assert!(delayed_actions_has_owner(&delayed_actions, &effect_owner));
        assert!(
            delayed_actions
                .actions
                .iter()
                .any(|delayed| delayed.owner.is_none())
        );
    }
}

pub(super) fn apply_when_events(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
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
    bone_queries: SceneMaxBoneQueries,
) {
    let Some(program) = startup_program.0.as_ref() else {
        active_collisions.active_by_event.clear();
        active_controllers.running.clear();
        return;
    };

    let mut transforms_by_name =
        build_action_transform_map(program, &object_pools, scene_entities.p0(), &bone_queries);
    let mut transient_collision_vars = HashSet::new();
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    let mut polled_when_events = Vec::new();
    for (statement_index, statement) in program.statements.iter().enumerate() {
        if let Statement::WhenEvent(event) = statement {
            polled_when_events.push((
                SceneMaxWhenEventKey::TopLevel(statement_index),
                statement_index.to_string(),
                SceneMaxControllerKey::When(statement_index),
                event.clone(),
            ));
        }
    }
    for registered in &delayed_actions.registered_when_events.events {
        polled_when_events.push((
            SceneMaxWhenEventKey::Registered(registered.id),
            format!("registered:{}", registered.id),
            SceneMaxControllerKey::RegisteredWhen(registered.id),
            registered.event.clone(),
        ));
    }

    for (event_key, event_label, owner, event) in polled_when_events {
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
        let lifecycle_probe = lifecycle_probe_condition(&event.condition);
        if lifecycle_probe && runtime_verbose_logging() {
            write_runtime_diagnostic_line(format!(
                "WHEN:PROBE stmt={} guard={} condition={} matches={} active={} delayed={} state={}",
                event_label,
                guard_matches as u8,
                describe_condition_brief(&event.condition),
                condition_matches_now as u8,
                describe_active_controllers(&active_controllers),
                describe_delayed_queue(&delayed_actions),
                describe_runtime_state(&vars)
            ));
        }
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
                    .transition_armed_by_event
                    .remove(&event_key);
                continue;
            }
            if after_matches {
                active_collisions
                    .transition_armed_by_event
                    .insert(event_key);
                active_controllers.running.remove(&owner);
                continue;
            }
            if !condition_matches_now {
                active_controllers.running.remove(&owner);
                continue;
            }
            if !active_collisions
                .transition_armed_by_event
                .remove(&event_key)
            {
                continue;
            }
        }
        let is_collision_event = condition_contains_collision(&event.condition);
        if !guard_matches || !condition_matches_now {
            if is_collision_event {
                active_collisions.active_by_event.remove(&event_key);
            }
            active_controllers.running.remove(&owner);
            continue;
        }
        if is_collision_event && !active_collisions.active_by_event.insert(event_key) {
            continue;
        }
        if is_collision_event {
            write_collision_event_probe(
                &event_label,
                &event.condition,
                &transforms_by_name,
                &collider_bounds,
                &physics_contacts,
                &object_pools,
            );
            collect_transient_collision_assignments(&event.actions, &mut transient_collision_vars);
        }
        if active_controllers.running.contains(&owner) {
            if delayed_actions_has_owner(&delayed_actions, &owner) {
                write_runtime_diagnostic_line(format!(
                    "WHEN:SKIP_RUNNING stmt={} owner={} condition={} active={} delayed={} state={}",
                    event_label,
                    describe_controller_key(&owner),
                    describe_condition_brief(&event.condition),
                    describe_active_controllers(&active_controllers),
                    describe_delayed_queue(&delayed_actions),
                    describe_runtime_state(&vars)
                ));
                continue;
            }
            write_runtime_diagnostic_line(format!(
                "CTRL:WHEN_STALE_OWNER_RECOVER stmt={} owner={} condition={} active={} delayed={} state={}",
                event_label,
                describe_controller_key(&owner),
                describe_condition_brief(&event.condition),
                describe_active_controllers(&active_controllers),
                describe_delayed_queue(&delayed_actions),
                describe_runtime_state(&vars)
            ));
            active_controllers.running.remove(&owner);
        }

        let mut queued_animations = HashMap::new();
        active_controllers.running.insert(owner.clone());
        write_runtime_diagnostic_line(format!(
            "WHEN:FIRE stmt={} owner={} condition={} active={} delayed={} state={}",
            event_label,
            describe_controller_key(&owner),
            describe_condition_brief(&event.condition),
            describe_active_controllers(&active_controllers),
            describe_delayed_queue(&delayed_actions),
            describe_runtime_state(&vars)
        ));
        let result = apply_action_sequence(
            &event.actions,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &mut runtime_assets,
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
        if result.is_suspended() {
            write_runtime_diagnostic_line(format!(
                "WHEN:PASS_SUSPEND stmt={} owner={} active={} delayed={} state={}",
                event_label,
                describe_controller_key(&owner),
                describe_active_controllers(&active_controllers),
                describe_delayed_queue(&delayed_actions),
                describe_runtime_state(&vars)
            ));
            break;
        } else {
            active_controllers.running.remove(&owner);
        }
    }
    clear_transient_collision_vars(&mut vars, &transient_collision_vars);
}

pub(super) fn clear_transient_collision_vars(vars: &mut SceneMaxVars, names: &HashSet<String>) {
    for name in names {
        vars.0.insert(name.clone(), 0.0);
    }
}

pub(super) fn collect_transient_collision_assignments(
    actions: &[Statement],
    names: &mut HashSet<String>,
) {
    for action in actions {
        match action {
            Statement::Assignment(assignment)
            | Statement::SharedAssignment(assignment)
            | Statement::LocalAssignment(assignment)
                if is_transient_collision_assignment_value(&assignment.value) =>
            {
                names.insert(assignment.name.clone());
            }
            Statement::Guarded { actions, .. }
            | Statement::Repeat { actions, .. }
            | Statement::DoWhile { actions, .. }
            | Statement::LoopContinue { actions, .. }
            | Statement::Async { actions } => {
                collect_transient_collision_assignments(actions, names);
            }
            Statement::If(statement) => {
                collect_transient_collision_assignments(&statement.actions, names);
                collect_transient_collision_assignments(&statement.else_actions, names);
            }
            _ => {}
        }
    }
}

pub(super) fn is_transient_collision_assignment_value(value: &AssignmentValue) -> bool {
    match value {
        AssignmentValue::Condition(_) => true,
        _ => false,
    }
}

pub(super) fn write_collision_event_probe(
    event_label: &str,
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
            event_label,
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
                let avian_contact = active_physics_contact_matches(
                    source_name,
                    target_name,
                    physics_contacts,
                    Some(collider_bounds),
                );
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
    let owner_distance =
        collision_owner_distance(source, target, transforms_by_name, Some(collider_bounds));
    let Some(source_transform) = source_exact
        .or_else(|| collision_owner_transform(source, transforms_by_name, Some(collider_bounds)))
    else {
        return (f32::INFINITY, 0.0, false, owner_distance);
    };
    let Some(target_transform) = target_exact
        .or_else(|| collision_owner_transform(target, transforms_by_name, Some(collider_bounds)))
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
            if !attached_collider_owner_distance_allows(
                source,
                target,
                transforms_by_name,
                Some(collider_bounds),
            ) {
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
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
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
    bone_queries: SceneMaxBoneQueries,
) {
    let Some(program) = startup_program.0.as_ref() else {
        recurring_timers.remaining_by_statement.clear();
        active_controllers.running.clear();
        return;
    };

    let delta = time.delta_secs();
    let mut due_runs = Vec::new();
    let mut transforms_by_name =
        build_action_transform_map(program, &object_pools, scene_entities.p0(), &bone_queries);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);
    for (index, statement) in program.statements.iter().enumerate() {
        let Statement::RunEvery {
            name,
            args,
            interval_seconds,
            interval_value,
        } = statement
        else {
            continue;
        };
        let interval = resolve_duration_value(
            interval_value,
            *interval_seconds,
            &vars,
            None,
            &guards_by_name,
            Some(&transforms_by_name),
            Some(&collider_bounds),
        )
        .max(0.001);
        let remaining = recurring_timers
            .remaining_by_statement
            .entry(index)
            .or_insert(interval);
        *remaining -= delta;
        if *remaining <= 0.0 {
            let owner = SceneMaxControllerKey::Recurring(index);
            if active_controllers.running.contains(&owner) {
                if delayed_actions_has_owner(&delayed_actions, &owner) {
                    if runtime_verbose_logging() {
                        write_runtime_diagnostic_line(format!(
                            "RECUR:SKIP_RUNNING stmt={} name={} owner={} active={} delayed={} state={}",
                            index,
                            name,
                            describe_controller_key(&owner),
                            describe_active_controllers(&active_controllers),
                            describe_delayed_queue(&delayed_actions),
                            describe_runtime_state(&vars)
                        ));
                    }
                    *remaining = 0.0;
                } else {
                    write_runtime_diagnostic_line(format!(
                        "CTRL:RECUR_STALE_OWNER_RECOVER stmt={} name={} owner={} active={} delayed={} state={}",
                        index,
                        name,
                        describe_controller_key(&owner),
                        describe_active_controllers(&active_controllers),
                        describe_delayed_queue(&delayed_actions),
                        describe_runtime_state(&vars)
                    ));
                    active_controllers.running.remove(&owner);
                    due_runs.push((
                        SceneMaxControllerKey::Recurring(index),
                        index.to_string(),
                        name.clone(),
                        args.clone(),
                    ));
                    while *remaining <= 0.0 {
                        *remaining += interval;
                    }
                }
            } else {
                due_runs.push((
                    SceneMaxControllerKey::Recurring(index),
                    index.to_string(),
                    name.clone(),
                    args.clone(),
                ));
                while *remaining <= 0.0 {
                    *remaining += interval;
                }
            }
        }
    }
    for registered in &delayed_actions.registered_run_every.events {
        let Statement::RunEvery {
            name,
            args,
            interval_seconds,
            interval_value,
        } = &registered.statement
        else {
            continue;
        };
        let interval = resolve_duration_value(
            interval_value,
            *interval_seconds,
            &vars,
            None,
            &guards_by_name,
            Some(&transforms_by_name),
            Some(&collider_bounds),
        )
        .max(0.001);
        let remaining = recurring_timers
            .remaining_by_registered
            .entry(registered.id)
            .or_insert(interval);
        *remaining -= delta;
        if *remaining <= 0.0 {
            let owner = SceneMaxControllerKey::RegisteredRecurring(registered.id);
            if active_controllers.running.contains(&owner) {
                if delayed_actions_has_owner(&delayed_actions, &owner) {
                    if runtime_verbose_logging() {
                        write_runtime_diagnostic_line(format!(
                            "RECUR:SKIP_RUNNING stmt=registered:{} name={} owner={} active={} delayed={} state={}",
                            registered.id,
                            name,
                            describe_controller_key(&owner),
                            describe_active_controllers(&active_controllers),
                            describe_delayed_queue(&delayed_actions),
                            describe_runtime_state(&vars)
                        ));
                    }
                    *remaining = 0.0;
                } else {
                    write_runtime_diagnostic_line(format!(
                        "CTRL:RECUR_STALE_OWNER_RECOVER stmt=registered:{} name={} owner={} active={} delayed={} state={}",
                        registered.id,
                        name,
                        describe_controller_key(&owner),
                        describe_active_controllers(&active_controllers),
                        describe_delayed_queue(&delayed_actions),
                        describe_runtime_state(&vars)
                    ));
                    active_controllers.running.remove(&owner);
                    due_runs.push((
                        SceneMaxControllerKey::RegisteredRecurring(registered.id),
                        format!("registered:{}", registered.id),
                        name.clone(),
                        args.clone(),
                    ));
                    while *remaining <= 0.0 {
                        *remaining += interval;
                    }
                }
            } else {
                due_runs.push((
                    SceneMaxControllerKey::RegisteredRecurring(registered.id),
                    format!("registered:{}", registered.id),
                    name.clone(),
                    args.clone(),
                ));
                while *remaining <= 0.0 {
                    *remaining += interval;
                }
            }
        }
    }

    if due_runs.is_empty() {
        return;
    }

    for (owner, label, name, args) in due_runs {
        let mut queued_animations = HashMap::new();
        active_controllers.running.insert(owner.clone());
        if runtime_verbose_logging() {
            write_runtime_diagnostic_line(format!(
                "RECUR:FIRE stmt={} name={} owner={} active={} delayed={} state={}",
                label,
                name,
                describe_controller_key(&owner),
                describe_active_controllers(&active_controllers),
                describe_delayed_queue(&delayed_actions),
                describe_runtime_state(&vars)
            ));
        }
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
            &mut runtime_assets,
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
        if runtime_verbose_logging() {
            write_runtime_diagnostic_line(format!(
                "RECUR:DONE stmt={} name={} owner={} result={:?} active={} delayed={} state={}",
                label,
                name,
                describe_controller_key(&owner),
                result,
                describe_active_controllers(&active_controllers),
                describe_delayed_queue(&delayed_actions),
                describe_runtime_state(&vars)
            ));
        }
    }
}

pub(super) fn update_delayed_actions(
    time: Res<Time>,
    keyboard: Res<ButtonInput<KeyCode>>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
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
    bone_queries: SceneMaxBoneQueries,
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
    if runtime_verbose_logging() {
        write_runtime_diagnostic_line(format!(
            "DELAY:READY count={} active={} pending={} state={} actions={}",
            ready_actions.len(),
            describe_active_controllers(&active_controllers),
            describe_delayed_queue(&delayed_actions),
            describe_runtime_state(&vars),
            ready_actions
                .iter()
                .map(|delayed| {
                    format!(
                        "owner={} {}",
                        delayed
                            .owner
                            .as_ref()
                            .map(describe_controller_key)
                            .unwrap_or_else(|| "-".to_owned()),
                        describe_statement_list(&delayed.actions)
                    )
                })
                .collect::<Vec<_>>()
                .join(" || ")
        ));
    }

    let mut transforms_by_name =
        build_action_transform_map(program, &object_pools, scene_entities.p0(), &bone_queries);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for mut delayed in ready_actions {
        if let Some(Statement::WaitForKey { key }) = delayed.actions.first() {
            if is_pressed_key(key, &keyboard) {
                if runtime_verbose_logging() {
                    write_runtime_diagnostic_line(format!(
                        "DELAY:WAIT_FOR_KEY_ACCEPT key={} owner={} active={} delayed={} state={}",
                        key,
                        delayed
                            .owner
                            .as_ref()
                            .map(describe_controller_key)
                            .unwrap_or_else(|| "-".to_owned()),
                        describe_active_controllers(&active_controllers),
                        describe_delayed_queue(&delayed_actions),
                        describe_runtime_state(&vars)
                    ));
                }
                delayed.actions.remove(0);
            } else {
                if runtime_verbose_logging() {
                    write_runtime_diagnostic_line(format!(
                        "DELAY:WAIT_FOR_KEY_POLL key={} owner={} active={} delayed={} state={}",
                        key,
                        delayed
                            .owner
                            .as_ref()
                            .map(describe_controller_key)
                            .unwrap_or_else(|| "-".to_owned()),
                        describe_active_controllers(&active_controllers),
                        describe_delayed_queue(&delayed_actions),
                        describe_runtime_state(&vars)
                    ));
                }
                delayed.remaining_seconds = LOOP_CONTINUE_DELAY_SECONDS;
                delayed_actions.actions.push(delayed);
                continue;
            }
        }
        if delayed.actions.is_empty() {
            if let Some(owner) = delayed.owner {
                if !delayed_actions_has_owner(&delayed_actions, &owner) {
                    active_controllers.running.remove(&owner);
                }
                if runtime_verbose_logging() {
                    write_runtime_diagnostic_line(format!(
                        "DELAY:DONE_EMPTY owner={} active={} delayed={} state={}",
                        describe_controller_key(&owner),
                        describe_active_controllers(&active_controllers),
                        describe_delayed_queue(&delayed_actions),
                        describe_runtime_state(&vars)
                    ));
                }
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
            &mut runtime_assets,
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
                if !delayed_actions_has_owner(&delayed_actions, &owner) {
                    active_controllers.running.remove(&owner);
                }
                if runtime_verbose_logging() {
                    write_runtime_diagnostic_line(format!(
                        "DELAY:DONE owner={} result={:?} active={} delayed={} state={}",
                        describe_controller_key(&owner),
                        result,
                        describe_active_controllers(&active_controllers),
                        describe_delayed_queue(&delayed_actions),
                        describe_runtime_state(&vars)
                    ));
                }
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
    if runtime_verbose_logging() {
        let delayed = delayed_actions.actions.last().unwrap();
        write_runtime_diagnostic_line(format!(
            "DELAY:ENQUEUE seconds={} actions={} owner={}",
            format_scenemax_number(seconds.max(0.0)),
            describe_statement_list(&delayed.actions),
            delayed
                .owner
                .as_ref()
                .map(describe_controller_key)
                .unwrap_or_else(|| "-".to_owned())
        ));
    }
    true
}

pub(super) fn describe_statement_list(actions: &[Statement]) -> String {
    if actions.is_empty() {
        return "<empty>".to_owned();
    }
    actions
        .iter()
        .take(8)
        .map(describe_statement)
        .collect::<Vec<_>>()
        .join(" -> ")
}

pub(super) fn describe_statement(action: &Statement) -> String {
    match action {
        Statement::CameraMove(camera_move) => format!(
            "CameraMove({} {} {}s async={})",
            axis_label(camera_move.axis),
            format_scenemax_number(camera_move.distance),
            format_scenemax_number(camera_move.duration_seconds),
            camera_move.async_run as u8
        ),
        Statement::CinematicPlay(play) => format!(
            "CinematicPlay({} {}s async={})",
            play.target,
            format_scenemax_number(play.duration_seconds),
            play.async_run as u8
        ),
        Statement::Async { actions } => format!("Async({})", describe_statement_list(actions)),
        Statement::RunFunction { name, .. } => format!("RunFunction({name})"),
        Statement::Audio(audio) => format!(
            "Audio({} {} loop={})",
            match audio.action {
                AudioAction::Play => "play",
                AudioAction::Stop => "stop",
            },
            audio
                .sound
                .as_deref()
                .unwrap_or_else(|| audio.sound_value.as_ref().map_or("<expr>", |_| "<expr>")),
            audio.looped as u8
        ),
        Statement::UiLoad { name } => format!("UiLoad({name})"),
        Statement::UiEase(ease) => format!("UiEase({:?})", ease.target),
        Statement::UiMessage(message) => format!("UiMessage({:?})", message.target),
        Statement::Wait { seconds } => format!("Wait({}s)", format_scenemax_number(*seconds)),
        Statement::WaitValue { .. } => "WaitValue".to_owned(),
        Statement::WaitForKey { key } => format!("WaitForKey({key})"),
        Statement::ChannelDraw(draw) if draw.clear => format!("ChannelDrawClear({})", draw.channel),
        Statement::ChannelDraw(draw) => format!("ChannelDraw({})", draw.channel),
        Statement::Print(print) => format!("Print({})", print.channel),
        Statement::EffekseerPlay(play) => {
            format!("EffekseerPlay({} loop={})", play.target, play.looped as u8)
        }
        Statement::Weapon(_) => "Weapon".to_owned(),
        Statement::AnimationControllerAction(action) => {
            format!(
                "AnimationControllerAction({}.{:?})",
                action.controller, action.action
            )
        }
        Statement::AnimationControllerEvent(event) => {
            format!(
                "AnimationControllerEvent({}.event({}))",
                event.controller, event.animation
            )
        }
        Statement::Unsupported { text } => format!("Unsupported({text})"),
        other => format!("{other:?}"),
    }
}

pub(super) fn axis_label(axis: SceneMaxAxis) -> &'static str {
    match axis {
        SceneMaxAxis::X => "x",
        SceneMaxAxis::Y => "y",
        SceneMaxAxis::Z => "z",
    }
}

pub(super) fn resolved_blocking_timed_action_seconds(
    action: &Statement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    match action {
        Statement::Turn(turn) if !turn.async_run && turn.loop_condition.is_none() => {
            let seconds = resolve_duration_value(
                &turn.duration_value,
                turn.duration_seconds,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            );
            (seconds > f32::EPSILON).then_some(seconds.max(0.001))
        }
        Statement::Move(movement) if !movement.async_run && movement.loop_condition.is_none() => {
            let seconds = resolve_duration_value(
                &movement.duration_value,
                movement.duration_seconds,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            );
            (seconds > f32::EPSILON).then_some(seconds.max(0.001))
        }
        Statement::MoveTo(move_to) if !move_to.async_run => {
            let seconds = resolve_duration_value(
                &move_to.duration_value,
                move_to.duration_seconds,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            );
            (seconds > f32::EPSILON).then_some(seconds.max(0.001))
        }
        Statement::CameraMove(camera_move) if !camera_move.async_run => {
            let seconds = resolve_duration_value(
                &camera_move.duration_value,
                camera_move.duration_seconds,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            );
            (seconds > f32::EPSILON).then_some(seconds.max(0.001))
        }
        Statement::CharacterJump(jump) if !jump.async_run => {
            let speed = resolve_animation_speed_value(
                &jump.speed_value,
                jump.speed,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            );
            Some(jump_duration_seconds(speed))
        }
        Statement::SpritePlay(sprite_play) if !sprite_play.looped => {
            let seconds = resolve_duration_value(
                &sprite_play.duration_value,
                sprite_play.duration_seconds,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            );
            (seconds > f32::EPSILON).then_some(seconds.max(0.001))
        }
        Statement::UiMessage(message) if !message.async_run => {
            (message.duration_seconds > f32::EPSILON).then_some(message.duration_seconds.max(0.001))
        }
        Statement::CinematicPlay(play) if !play.async_run => Some(play.duration_seconds.max(0.1)),
        _ => None,
    }
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
    runtime_assets: &mut SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    mut delayed_actions: Option<&mut DelayedActionQueue>,
    mut ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
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
) -> ActionSequenceResult {
    apply_scoped_transform_aliases(transforms_by_name, scope.as_deref());
    let mut runtime_declared_entities = HashMap::<String, Entity>::new();

    for (index, action) in actions.iter().enumerate() {
        match action {
            Statement::NoOp { .. } => {}
            Statement::Unsupported { text } => {
                tracing::debug!(text, "skipping unsupported SceneMax runtime action");
            }
            Statement::Return | Statement::ReturnValue { .. } => {
                return ActionSequenceResult::Returned;
            }
            Statement::KeyEvent(event) => {
                if let Some(delayed_actions) = delayed_actions.as_deref_mut() {
                    register_key_event(&mut delayed_actions.registered_key_events, event.clone());
                }
            }
            Statement::WhenEvent(event) => {
                if let Some(delayed_actions) = delayed_actions.as_deref_mut() {
                    register_when_event(&mut delayed_actions.registered_when_events, event.clone());
                }
            }
            Statement::RunEvery { .. } => {
                if let Some(delayed_actions) = delayed_actions.as_deref_mut() {
                    register_run_every(&mut delayed_actions.registered_run_every, action.clone());
                }
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
            Statement::WaitForKey { .. } => {
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
                    write_runtime_diagnostic_line(format!(
                        "FUNCTION:MISS phase=runtime_sequence name={name}"
                    ));
                    tracing::debug!(
                        name,
                        "SceneMax function is not implemented or was not parsed"
                    );
                    continue;
                };
                let resolved_args = resolve_call_args(
                    args,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                );
                if !function_guard_matches(
                    function,
                    &resolved_args,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    if runtime_verbose_logging() {
                        write_runtime_diagnostic_line(format!(
                            "FUNCTION:SKIP phase=runtime_sequence name={name} reason=guard_false"
                        ));
                    }
                    tracing::debug!(name, "SceneMax function guard is false");
                    continue;
                }

                if runtime_verbose_logging() {
                    write_runtime_diagnostic_line(format!(
                        "FUNCTION:RUN phase=runtime_sequence name={name} actions={}",
                        describe_statement_list(&function.actions)
                    ));
                }
                let function_actions = actions_with_parent_continuation(
                    instantiate_function_actions(function, &resolved_args),
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
                let async_owner = async_function_controller_key(actions);
                if let Some(async_owner) = async_owner.as_ref()
                    && let Some(delayed_actions) = delayed_actions.as_deref_mut()
                {
                    cancel_delayed_actions_for_owner(delayed_actions, async_owner);
                }
                let async_owner_label = async_owner
                    .as_ref()
                    .map(describe_controller_key)
                    .unwrap_or_else(|| "-".to_owned());
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    0.0,
                    actions.clone(),
                    async_owner,
                    scope.as_deref().cloned(),
                ) {
                    if runtime_verbose_logging() {
                        write_runtime_diagnostic_line(format!(
                            "ASYNC:QUEUE owner={} actions={}",
                            async_owner_label,
                            describe_statement_list(actions)
                        ));
                    }
                    continue;
                }
                let mut async_scope = scope.as_deref().cloned().unwrap_or_default();
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
                    Some(&mut async_scope),
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
            Statement::AnimationControllerAction(action)
                if action.action == AnimationControllerAction::Run =>
            {
                let seconds = animation_controller_duration_for_action(
                    action,
                    runtime_assets,
                    Some(animation_durations),
                )
                .unwrap_or(0.001)
                .max(0.001);
                apply_runtime_animation_controller_action(
                    action,
                    commands,
                    runtime_assets,
                    object_pools,
                    scope.as_deref(),
                    scene_entities,
                    queued_animations,
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
            Statement::ThrowMotionApply(apply) if !apply.async_run => {
                let seconds = throw_motion_duration_for_apply(apply, runtime_assets)
                    .unwrap_or(0.001)
                    .max(0.001);
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
                    &mut runtime_declared_entities,
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
            action
                if resolved_blocking_timed_action_seconds(
                    action,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                )
                .is_some() =>
            {
                if continuous_delta_seconds.is_some()
                    && continuous_timed_action_applies_per_frame(action)
                {
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
                        &mut runtime_declared_entities,
                    );
                    continue;
                }
                let seconds = resolved_blocking_timed_action_seconds(
                    action,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                )
                .unwrap_or_default();
                write_runtime_diagnostic_line(format!(
                    "RUNTIME:BLOCKING action={} seconds={} tail={}",
                    describe_statement(action),
                    format_scenemax_number(seconds),
                    describe_statement_list(&actions[index + 1..])
                ));
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
                    &mut runtime_declared_entities,
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
                    &mut runtime_declared_entities,
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
                    &mut runtime_declared_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
        }
    }
    ActionSequenceResult::Completed
}

pub(super) fn continuous_timed_action_applies_per_frame(action: &Statement) -> bool {
    matches!(
        action,
        Statement::Move(_) | Statement::MoveTo(_) | Statement::Turn(_)
    )
}

pub(super) fn apply_runtime_model_decl(
    name: &str,
    resource: &str,
    options: &EntityOptions,
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    collider_bounds: &mut SceneMaxColliderBounds,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
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
) -> Option<Entity> {
    let transform = primitive_transform_from_options_resolved(
        options,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        Some(collider_bounds),
    );
    let visibility = if options.hidden {
        Visibility::Hidden
    } else {
        Visibility::Inherited
    };
    if let Some(asset_id) = effekseer_asset_id(resource) {
        let effect_path = runtime_assets
            .asset_root
            .as_deref()
            .and_then(|asset_root| resolve_effekseer_effect_path(asset_root, &asset_id));
        for (entity, scene_entity, mut existing_transform, _, _, existing_visibility, _, _) in
            &mut scene_entities.p1()
        {
            if scene_entity.name != name {
                continue;
            }
            *existing_transform = transform;
            if let Some(mut existing_visibility) = existing_visibility {
                *existing_visibility = visibility;
            } else {
                commands.entity(entity).insert(visibility);
            }
            commands.entity(entity).insert(SceneMaxEffekseerEffect {
                instance_id: next_effekseer_instance_id(),
                asset_id: asset_id.clone(),
                effect_path: effect_path.clone(),
                one_shot_duration_seconds: effekseer_one_shot_duration_seconds(
                    effect_path.as_deref(),
                ),
            });
            transforms_by_name.insert(name.to_owned(), transform);
            write_runtime_diagnostic_line(format!(
                "EFFEKSEER:DECL target={} asset={} path={} renderer={} mode=update",
                name,
                asset_id,
                effect_path
                    .as_ref()
                    .map(|path| path.display().to_string())
                    .unwrap_or_else(|| "<missing>".to_owned()),
                effekseer_renderer_label()
            ));
            tracing::warn!(
                name,
                asset = asset_id,
                renderer = effekseer_renderer_label(),
                "updated runtime Effekseer effect for the Bevy bridge"
            );
            return Some(entity);
        }

        let entity_id = commands
            .spawn((
                SceneMaxEntity {
                    name: name.to_owned(),
                    runtime_name: format!("{name}@runtime"),
                },
                SceneMaxEffekseerEffect {
                    instance_id: next_effekseer_instance_id(),
                    asset_id: asset_id.clone(),
                    effect_path: effect_path.clone(),
                    one_shot_duration_seconds: effekseer_one_shot_duration_seconds(
                        effect_path.as_deref(),
                    ),
                },
                transform,
                visibility,
            ))
            .id();
        insert_physics_components(commands, entity_id, name, resource, options, &transform);
        transforms_by_name.insert(name.to_owned(), transform);
        write_runtime_diagnostic_line(format!(
            "EFFEKSEER:DECL target={} asset={} path={} renderer={} mode=spawn",
            name,
            asset_id,
            effect_path
                .as_ref()
                .map(|path| path.display().to_string())
                .unwrap_or_else(|| "<missing>".to_owned()),
            effekseer_renderer_label()
        ));
        tracing::warn!(
            name,
            asset = asset_id,
            renderer = effekseer_renderer_label(),
            "registered runtime Effekseer effect for the Bevy bridge"
        );
        return Some(entity_id);
    }
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
        return Some(entity);
    }

    if options.collider {
        spawn_scenemax_collider_decl(commands, name, resource, options, transform, None);
        register_collider_bounds(collider_bounds, name, options, transform);
        transforms_by_name.insert(name.to_owned(), transform);
        tracing::info!(name, resource, "spawned runtime SceneMax collider");
        return None;
    }

    if let Some(model_transform) = spawn_runtime_gltf_model_decl(
        name,
        resource,
        options,
        transform,
        runtime_assets,
        collider_bounds,
        commands,
    ) {
        transforms_by_name.insert(name.to_owned(), model_transform);
        return None;
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
    Some(entity_id)
}

pub(super) fn effekseer_asset_id(resource: &str) -> Option<String> {
    let prefix = "effects.effekseer.";
    let lower = resource.to_ascii_lowercase();
    lower
        .starts_with(prefix)
        .then(|| resource[prefix.len()..].trim())
        .filter(|asset_id| !asset_id.is_empty())
        .map(str::to_owned)
}

pub(super) fn resolve_effekseer_effect_path(asset_root: &Path, asset_id: &str) -> Option<PathBuf> {
    let folder = asset_root.join("effects").join(asset_id);
    let entries = fs::read_dir(folder).ok()?;
    entries
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .find(|path| {
            path.extension()
                .and_then(|extension| extension.to_str())
                .is_some_and(|extension| {
                    extension.eq_ignore_ascii_case("efkefc")
                        || extension.eq_ignore_ascii_case("efk")
                })
        })
}

pub(super) fn effekseer_one_shot_duration_seconds(effect_path: Option<&Path>) -> f32 {
    const FALLBACK_SECONDS: f32 = 2.0;
    let Some(effect_path) = effect_path else {
        return FALLBACK_SECONDS;
    };
    let Some(parent) = effect_path.parent() else {
        return FALLBACK_SECONDS;
    };
    let Ok(entries) = fs::read_dir(parent) else {
        return FALLBACK_SECONDS;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if !path
            .extension()
            .and_then(|extension| extension.to_str())
            .is_some_and(|extension| extension.eq_ignore_ascii_case("efkproj"))
        {
            continue;
        }
        let Ok(source) = fs::read_to_string(path) else {
            continue;
        };
        let start = xml_numeric_tag(&source, "StartFrame").unwrap_or(0.0);
        if let Some(end) = xml_numeric_tag(&source, "EndFrame") {
            return ((end - start).max(1.0) / 60.0).max(0.1);
        }
    }
    FALLBACK_SECONDS
}

fn xml_numeric_tag(source: &str, tag: &str) -> Option<f32> {
    let open = format!("<{tag}>");
    let close = format!("</{tag}>");
    let start = source.find(&open)? + open.len();
    let end = source[start..].find(&close)? + start;
    source[start..end].trim().parse::<f32>().ok()
}

fn resolved_effekseer_play_translation(
    play: &EffekseerPlayStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<Vec3> {
    evaluate_position_value_runtime(
        play.position.as_ref()?,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
}

fn resolved_effekseer_playback(
    play: &EffekseerPlayStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> SceneMaxEffekseerPlayback {
    let mut looped = play.looped;
    let mut playback_speed = 1.0;
    let mut dynamic_inputs = [0.0; 4];
    for (key, value) in &play.attrs {
        let literal_truthy = assignment_value_is_truthy(value);
        let resolved = resolve_assignment_value_scoped_with_guards(
            value,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        );
        match key.as_str() {
            "loop" => {
                looped = literal_truthy || resolved.is_some_and(|value| value != 0.0);
            }
            "play_back_speed" | "playback_speed" => {
                playback_speed = resolved.unwrap_or(playback_speed).max(0.001);
            }
            "input0" => dynamic_inputs[0] = resolved.unwrap_or(dynamic_inputs[0]),
            "input1" => dynamic_inputs[1] = resolved.unwrap_or(dynamic_inputs[1]),
            "input2" => dynamic_inputs[2] = resolved.unwrap_or(dynamic_inputs[2]),
            "input3" => dynamic_inputs[3] = resolved.unwrap_or(dynamic_inputs[3]),
            _ => {}
        }
    }
    SceneMaxEffekseerPlayback {
        looped,
        play_generation: next_effekseer_play_generation(),
        playback_speed,
        dynamic_inputs,
        elapsed_seconds: 0.0,
    }
}

fn next_effekseer_play_generation() -> u64 {
    static GENERATION: AtomicU64 = AtomicU64::new(1);
    GENERATION.fetch_add(1, Ordering::Relaxed)
}

fn assignment_value_is_truthy(value: &AssignmentValue) -> bool {
    match value {
        AssignmentValue::Number(value) => *value != 0.0,
        AssignmentValue::Symbol(value) => value.eq_ignore_ascii_case("true"),
        _ => false,
    }
}

fn write_effekseer_bridge_play(target: &str, play: &EffekseerPlayStatement) {
    let playback = play
        .attrs
        .iter()
        .find_map(|(key, value)| {
            matches!(key.as_str(), "play_back_speed" | "playback_speed")
                .then(|| format!("{value:?}"))
        })
        .unwrap_or_else(|| "1".to_owned());
    write_runtime_diagnostic_line(format!(
        "EFFEKSEER:PLAY target={} loop={} speed={} attrs={} renderer={}",
        target,
        play.looped as u8,
        playback,
        play.attrs.len(),
        effekseer_renderer_label()
    ));
}

fn spawn_runtime_gltf_model_decl(
    name: &str,
    resource: &str,
    options: &EntityOptions,
    transform: Transform,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    collider_bounds: &mut SceneMaxColliderBounds,
    commands: &mut Commands,
) -> Option<Transform> {
    let model = runtime_model_resource(resource, runtime_assets)?;
    let asset_server = runtime_assets.asset_server.as_ref()?;
    let resolved_model_resource = model.name.clone();

    let mut model_transform = transform;
    if options.scale.is_none()
        && let Some(scale) = model.scale
    {
        model_transform.scale *= Vec3::new(scale[0], scale[1], scale[2]);
    }
    let bevy_visual_offset_y = model
        .character_physics
        .as_ref()
        .and_then(|character| character.bevy_visual_offset_y);
    let asset_path = model.asset_path;
    let gltf: Handle<Gltf> = asset_server.load(asset_path.clone());
    let scene =
        WorldAssetRoot(asset_server.load(GltfAssetLabel::Scene(0).from_asset(asset_path.clone())));
    let entity_id = commands
        .spawn((
            SceneMaxEntity {
                name: name.to_owned(),
                runtime_name: format!("{name}@runtime"),
            },
            SceneMaxModelResource {
                resource: resolved_model_resource.clone(),
            },
            SceneMaxGltf { gltf: gltf.clone() },
            scene,
            model_transform,
            if options.hidden {
                Visibility::Hidden
            } else {
                Visibility::Inherited
            },
        ))
        .id();
    insert_gltf_visual_offset(commands, entity_id, bevy_visual_offset_y);
    insert_physics_components(
        commands,
        entity_id,
        name,
        resource,
        options,
        &model_transform,
    );
    if options.collider {
        register_collider_bounds(collider_bounds, name, options, model_transform);
    }
    runtime_assets
        .gltf_handles_by_name
        .insert(name.to_owned(), gltf);
    runtime_assets
        .model_resources_by_name
        .insert(name.to_owned(), resolved_model_resource);
    tracing::info!(
        name,
        resource,
        path = %asset_path,
        "spawned runtime SceneMax GLTF model"
    );
    write_runtime_diagnostic_line(format!(
        "resolved runtime GLTF model {name}=>{resource} at {asset_path}"
    ));
    Some(model_transform)
}

fn runtime_model_resource(
    resource: &str,
    runtime_assets: &SceneMaxRuntimeAssets,
) -> Option<scenemax_assets::ModelResource> {
    let asset_root = runtime_assets.asset_root.as_deref()?;
    scenemax_assets::resolve_model_resource_with_builtin_fallback(
        asset_root,
        runtime_assets.builtin_asset_root.as_deref(),
        resource,
    )
    .ok()
}

fn register_animation_controller_assignment(
    name: &str,
    value: &AnimationControllerValue,
    runtime_assets: &mut SceneMaxRuntimeAssets,
) {
    runtime_assets.animation_controllers_by_name.insert(
        name.to_owned(),
        RuntimeAnimationController {
            target: value.target.clone(),
            clip: value.clip.clone(),
            events: Vec::new(),
            running: false,
            previous_percent: -1.0,
            current_run_started: false,
        },
    );
    write_runtime_diagnostic_line(format!(
        "ANIMCTRL:ASSIGN controller={} target={} clip={}",
        name, value.target, value.clip
    ));
}

fn register_animation_controller_event(
    event: &AnimationControllerEventStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    runtime_assets: &mut SceneMaxRuntimeAssets,
) {
    let Some(percent) = resolve_assignment_value_scoped_with_guards(
        &event.percent,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    ) else {
        return;
    };
    if let Some(controller) = runtime_assets
        .animation_controllers_by_name
        .get_mut(&event.controller)
    {
        controller.events.push(RuntimeAnimationControllerEvent {
            animation: normalized_animation_name(&event.animation),
            percent: percent.clamp(0.0, 100.0),
            actions: event.actions.clone(),
            fired: false,
        });
        write_runtime_diagnostic_line(format!(
            "ANIMCTRL:EVENT controller={} animation={} percent={} actions={}",
            event.controller,
            event.animation,
            format_scenemax_number(percent.clamp(0.0, 100.0)),
            event.actions.len()
        ));
    } else {
        write_runtime_diagnostic_line(format!(
            "ANIMCTRL:EVENT_MISS controller={} animation={} reason=controller_not_registered",
            event.controller, event.animation
        ));
    }
}

fn animation_controller_duration_for_action(
    action: &AnimationControllerActionStatement,
    runtime_assets: &SceneMaxRuntimeAssets,
    animation_durations: Option<&SceneMaxAnimationDurations>,
) -> Option<f32> {
    if action.action != AnimationControllerAction::Run {
        return None;
    }
    let controller = runtime_assets
        .animation_controllers_by_name
        .get(&action.controller)?;
    animation_durations
        .and_then(|durations| durations.lookup(&controller.target, &controller.clip))
        .or(Some(DEFAULT_ANIMATION_CLIP_SECONDS))
        .map(|duration| duration.clamp(0.08, 3.5))
}

fn apply_startup_animation_controller_action(
    action: &AnimationControllerActionStatement,
    commands: &mut Commands,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    entities_by_name: &HashMap<String, Entity>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
) {
    let Some(controller) = runtime_assets
        .animation_controllers_by_name
        .get_mut(&action.controller)
    else {
        return;
    };
    match action.action {
        AnimationControllerAction::Run => {
            let target = controller.target.clone();
            let clip = controller.clip.clone();
            let (Some(entity), Some(gltf)) = (
                entities_by_name.get(&controller.target).copied(),
                gltfs_by_name.get(&controller.target).cloned(),
            ) else {
                write_runtime_diagnostic_line(format!(
                    "ANIMCTRL:RUN_MISS controller={} target={} clip={} phase=startup reason=target_or_gltf_not_found",
                    action.controller, target, clip
                ));
                return;
            };
            let target_model_resource =
                runtime_assets.model_resources_by_name.get(&target).cloned();
            start_runtime_animation_controller(
                controller,
                commands,
                entity,
                gltf,
                target_model_resource,
            );
            write_runtime_diagnostic_line(format!(
                "ANIMCTRL:RUN controller={} target={} clip={} entity={:?} phase=startup",
                action.controller, target, clip, entity
            ));
        }
        AnimationControllerAction::Stop => {
            let target = stop_runtime_animation_controller(controller);
            write_runtime_diagnostic_line(format!(
                "ANIMCTRL:STOP controller={} target={} phase=startup",
                action.controller, target
            ));
            runtime_assets
                .pending_animation_controller_stops
                .push(target);
        }
    }
}

fn apply_runtime_animation_controller_action(
    action: &AnimationControllerActionStatement,
    commands: &mut Commands,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
    scene_entities: &mut ParamSet<(
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
    queued_animations: &mut HashMap<Entity, (String, bool)>,
) {
    match action.action {
        AnimationControllerAction::Run => {
            let Some((target, clip)) = runtime_assets
                .animation_controllers_by_name
                .get(&action.controller)
                .map(|controller| (controller.target.clone(), controller.clip.clone()))
            else {
                write_runtime_diagnostic_line(format!(
                    "ANIMCTRL:RUN_MISS controller={} reason=controller_not_registered",
                    action.controller
                ));
                return;
            };
            let resolved = scene_entities.p1().iter().find_map(
                |(entity, scene_entity, _, gltf, _, _, _, _)| {
                    if !target_matches_alias(&target, &scene_entity.name, object_pools, scope) {
                        return None;
                    }
                    let gltf = gltf.map(|gltf| gltf.gltf.clone()).or_else(|| {
                        runtime_assets
                            .gltf_handles_by_name
                            .get(&scene_entity.name)
                            .cloned()
                    });
                    let model_resource = runtime_assets
                        .model_resources_by_name
                        .get(&scene_entity.name)
                        .cloned();
                    gltf.map(|gltf| (entity, gltf, model_resource))
                },
            );
            let Some((entity, gltf, target_model_resource)) = resolved else {
                write_runtime_diagnostic_line(format!(
                    "ANIMCTRL:RUN_MISS controller={} target={} clip={} reason=target_or_gltf_not_found cached_gltfs={}",
                    action.controller,
                    target,
                    clip,
                    runtime_assets.gltf_handles_by_name.len()
                ));
                return;
            };
            let Some(controller) = runtime_assets
                .animation_controllers_by_name
                .get_mut(&action.controller)
            else {
                write_runtime_diagnostic_line(format!(
                    "ANIMCTRL:RUN_MISS controller={} reason=controller_not_registered_after_resolve",
                    action.controller
                ));
                return;
            };
            start_runtime_animation_controller(
                controller,
                commands,
                entity,
                gltf,
                target_model_resource,
            );
            write_runtime_diagnostic_line(format!(
                "ANIMCTRL:RUN controller={} target={} clip={} entity={:?}",
                action.controller, target, clip, entity
            ));
            queued_animations.insert(entity, (clip, false));
        }
        AnimationControllerAction::Stop => {
            let Some(controller) = runtime_assets
                .animation_controllers_by_name
                .get_mut(&action.controller)
            else {
                write_runtime_diagnostic_line(format!(
                    "ANIMCTRL:STOP_MISS controller={} reason=controller_not_registered",
                    action.controller
                ));
                return;
            };
            let target = stop_runtime_animation_controller(controller);
            write_runtime_diagnostic_line(format!(
                "ANIMCTRL:STOP controller={} target={}",
                action.controller, target
            ));
            runtime_assets
                .pending_animation_controller_stops
                .push(target);
        }
    }
}

fn start_runtime_animation_controller(
    controller: &mut RuntimeAnimationController,
    commands: &mut Commands,
    entity: Entity,
    gltf: Handle<Gltf>,
    target_model_resource: Option<String>,
) {
    controller.running = true;
    controller.current_run_started = false;
    controller.previous_percent = -1.0;
    for event in &mut controller.events {
        event.fired = false;
    }
    commands.entity(entity).insert(AnimationToPlay {
        clip: controller.clip.clone(),
        runtime_clip: controller.clip.clone(),
        looped: false,
        speed: 1.0,
        gltf: gltf.clone(),
        target_model_resource,
        baked_external: None,
        bake_request: None,
        external_retarget: Default::default(),
        external_source: false,
        tried_external_source: false,
        visual_transform_preapplied: false,
        retarget_wait_logged: false,
    });
}

fn stop_runtime_animation_controller(controller: &mut RuntimeAnimationController) -> String {
    controller.running = false;
    controller.current_run_started = false;
    controller.previous_percent = -1.0;
    controller.target.clone()
}

pub(super) fn update_animation_runtime_controllers(
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    animations: Query<(&SceneMaxEntity, &CurrentAnimation)>,
) {
    for controller in runtime_assets.animation_controllers_by_name.values_mut() {
        if !controller.running {
            continue;
        }
        let Some(current) = animations
            .iter()
            .find_map(|(entity, current)| (entity.name == controller.target).then_some(current))
        else {
            continue;
        };
        if !current_animation_matches(current, &controller.clip, false) {
            continue;
        }

        controller.current_run_started = true;
        let current_percent = current_animation_percent(current);
        if controller.previous_percent > current_percent {
            controller.previous_percent = -1.0;
        }

        let previous_percent = controller.previous_percent;
        let mut fired_actions = Vec::new();
        for event in &mut controller.events {
            if event.fired || !animation_name_matches(&current.clip, &event.animation) {
                continue;
            }
            if previous_percent < event.percent && current_percent >= event.percent {
                event.fired = true;
                fired_actions.push(event.actions.clone());
            }
        }
        controller.previous_percent = current_percent;

        for actions in fired_actions {
            if !actions.is_empty() {
                enqueue_delayed_actions(Some(&mut delayed_actions), 0.0, actions, None, None);
            }
        }

        if controller.current_run_started && !current.looped && current_percent >= 100.0 {
            controller.running = false;
        }
    }
}

pub(super) fn apply_pending_animation_controller_stops(
    mut commands: Commands,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    children: Query<&Children>,
    roots: Query<(Entity, &SceneMaxEntity), With<SceneMaxEntity>>,
    mut players: Query<&mut AnimationPlayer>,
) {
    if runtime_assets.pending_animation_controller_stops.is_empty() {
        return;
    }
    let pending = std::mem::take(&mut runtime_assets.pending_animation_controller_stops);
    for target in pending {
        let Some((root, _)) = roots.iter().find(|(_, entity)| entity.name == target) else {
            continue;
        };
        commands
            .entity(root)
            .remove::<AnimationToPlay>()
            .remove::<CurrentAnimation>();
        for child in children.iter_descendants(root) {
            if let Ok(mut player) = players.get_mut(child) {
                player.stop_all();
            }
        }
    }
}

fn enqueue_weapon_action(statement: &WeaponStatement, runtime_assets: &mut SceneMaxRuntimeAssets) {
    runtime_assets
        .pending_weapon_actions
        .push(statement.clone());
}

fn enqueue_throw_motion_application(
    statement: &ThrowMotionApplyStatement,
    runtime_assets: &mut SceneMaxRuntimeAssets,
) {
    runtime_assets
        .pending_throw_motion_applications
        .push(statement.clone());
}

fn register_throw_motion_assignment(
    name: &str,
    value: &ThrowMotionValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    runtime_assets: &mut SceneMaxRuntimeAssets,
) {
    let Some(motion_asset_id) = resolve_throw_motion_asset_id(
        &value.asset,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    ) else {
        return;
    };
    let target = value.target.as_ref().and_then(|target| {
        resolve_throw_motion_target(
            target,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        )
    });
    runtime_assets.throw_motions_by_name.insert(
        name.to_owned(),
        RuntimeThrowMotionValue {
            motion_asset_id,
            target,
            events: Vec::new(),
        },
    );
}

fn register_throw_motion_event(
    event: &ThrowMotionEventStatement,
    runtime_assets: &mut SceneMaxRuntimeAssets,
) {
    if let Some(value) = runtime_assets.throw_motions_by_name.get_mut(&event.motion) {
        value.events.push(event.clone());
    }
}

fn resolve_throw_motion_asset_id(
    asset: &ThrowMotionAsset,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<String> {
    match asset {
        ThrowMotionAsset::Literal(value) => Some(value.clone()),
        ThrowMotionAsset::Expression(value) => match value.as_ref() {
            AssignmentValue::Symbol(value) => Some(value.clone()),
            value => resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .map(format_scenemax_number),
        },
    }
}

fn resolve_throw_motion_target(
    target: &ThrowMotionTarget,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<RuntimeThrowMotionTarget> {
    match target {
        ThrowMotionTarget::Object(name) => Some(RuntimeThrowMotionTarget::Object(name.clone())),
        ThrowMotionTarget::Position(values) => Some(RuntimeThrowMotionTarget::Position(Vec3::new(
            resolve_assignment_value_scoped_with_guards(
                &values[0],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
            resolve_assignment_value_scoped_with_guards(
                &values[1],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
            resolve_assignment_value_scoped_with_guards(
                &values[2],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
        ))),
    }
}

pub(super) fn apply_pending_weapon_actions(
    mut commands: Commands,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    scene_entities: Query<(Entity, &SceneMaxEntity)>,
    equipped_weapons: Query<(Entity, &SceneMaxEquippedWeapon)>,
    children: Query<&Children>,
    named_nodes: Query<(Entity, &Name)>,
    global_transforms: Query<&GlobalTransform>,
) {
    if runtime_assets.pending_weapon_actions.is_empty() {
        return;
    }

    let Some(asset_server) = runtime_assets.asset_server.clone() else {
        return;
    };
    let Some(asset_root) = runtime_assets.asset_root.clone() else {
        runtime_assets.pending_weapon_actions.clear();
        return;
    };
    let builtin_asset_root = runtime_assets.builtin_asset_root.clone();
    let pending_actions = std::mem::take(&mut runtime_assets.pending_weapon_actions);
    let owner_entities = scene_entities
        .iter()
        .map(|(entity, scene_entity)| (scene_entity.name.clone(), entity))
        .collect::<HashMap<_, _>>();

    for pending in pending_actions {
        match pending.action.clone() {
            WeaponAction::Unequip => {
                despawn_equipped_weapon(
                    &pending.owner,
                    &mut commands,
                    &equipped_weapons,
                    &mut collider_bounds,
                );
            }
            WeaponAction::Detach => {
                detach_equipped_weapon(&pending.owner, &mut commands, &equipped_weapons);
            }
            WeaponAction::Equip { weapon } => {
                equip_runtime_weapon(
                    &pending.owner,
                    &weapon,
                    None,
                    &mut commands,
                    &asset_server,
                    &asset_root,
                    builtin_asset_root.as_deref(),
                    &owner_entities,
                    &equipped_weapons,
                    &children,
                    &named_nodes,
                    &global_transforms,
                    &mut collider_bounds,
                );
            }
            WeaponAction::Posture { posture } => {
                let Some(weapon) = equipped_weapons
                    .iter()
                    .find(|(_, equipped)| equipped.owner == pending.owner)
                    .map(|(_, equipped)| equipped.weapon.clone())
                else {
                    continue;
                };
                equip_runtime_weapon(
                    &pending.owner,
                    &weapon,
                    Some(&posture),
                    &mut commands,
                    &asset_server,
                    &asset_root,
                    builtin_asset_root.as_deref(),
                    &owner_entities,
                    &equipped_weapons,
                    &children,
                    &named_nodes,
                    &global_transforms,
                    &mut collider_bounds,
                );
            }
        }
    }
}

fn equip_runtime_weapon(
    owner: &str,
    weapon: &str,
    posture_id_or_name: Option<&str>,
    commands: &mut Commands,
    asset_server: &AssetServer,
    asset_root: &Path,
    builtin_asset_root: Option<&Path>,
    owner_entities: &HashMap<String, Entity>,
    equipped_weapons: &Query<(Entity, &SceneMaxEquippedWeapon)>,
    children: &Query<&Children>,
    named_nodes: &Query<(Entity, &Name)>,
    global_transforms: &Query<&GlobalTransform>,
    collider_bounds: &mut SceneMaxColliderBounds,
) {
    let Some(owner_entity) = owner_entities.get(owner).copied() else {
        return;
    };
    let Some(definition) = load_weapon_definition(asset_root, weapon) else {
        return;
    };
    let Some(model_asset_id) = definition.model_asset_id.as_deref() else {
        return;
    };
    let posture = definition.posture(posture_id_or_name);
    let parent_entity = if let Some(attachment_point) =
        posture.and_then(|posture| posture.attachment_point.as_deref())
    {
        if let Some(bone_entity) =
            find_descendant_entity_by_name(owner_entity, attachment_point, children, named_nodes)
        {
            bone_entity
        } else {
            owner_entity
        }
    } else {
        owner_entity
    };
    let Some(model) = scenemax_assets::resolve_model_resource_with_builtin_fallback(
        asset_root,
        builtin_asset_root,
        model_asset_id,
    )
    .ok() else {
        return;
    };

    despawn_equipped_weapon(owner, commands, equipped_weapons, collider_bounds);

    let compensation_scale = global_transforms
        .get(parent_entity)
        .map(|global| {
            let (scale, _, _) = global.to_scale_rotation_translation();
            Vec3::new(
                inverse_scale_component(scale.x),
                inverse_scale_component(scale.y),
                inverse_scale_component(scale.z),
            )
        })
        .unwrap_or(Vec3::ONE);
    let posture_transform = posture
        .and_then(|posture| posture.transform.as_ref())
        .map(weapon_transform)
        .unwrap_or_default();
    let mut visual_transform = Transform::default();
    if let Some(scale) = model.scale {
        visual_transform.scale = Vec3::new(scale[0], scale[1], scale[2]);
    }
    let asset_path = model.asset_path;
    let gltf: Handle<Gltf> = asset_server.load(asset_path.clone());
    let scene =
        WorldAssetRoot(asset_server.load(GltfAssetLabel::Scene(0).from_asset(asset_path.clone())));
    let runtime_name = format!("{owner}.weapon");
    let root_entity = commands
        .spawn((
            SceneMaxEntity {
                name: runtime_name.clone(),
                runtime_name: format!("{runtime_name}@weapon_root"),
            },
            SceneMaxEquippedWeapon {
                owner: owner.to_owned(),
                weapon: weapon.to_owned(),
                colliders: weapon_collider_references(&runtime_name, &definition),
            },
            Transform::from_scale(compensation_scale),
            Visibility::Inherited,
            Name::new(runtime_name.clone()),
        ))
        .id();
    let transform_entity = commands
        .spawn((
            posture_transform,
            Visibility::Inherited,
            Name::new(format!("{runtime_name}.transform")),
        ))
        .id();
    let visual_entity = commands
        .spawn((
            SceneMaxEntity {
                name: format!("{runtime_name}.visual"),
                runtime_name: format!("{runtime_name}@weapon_visual"),
            },
            SceneMaxGltf { gltf },
            scene,
            visual_transform,
            Visibility::Inherited,
            Name::new(format!("{runtime_name}.visual")),
        ))
        .id();
    commands.entity(root_entity).add_child(transform_entity);
    commands.entity(transform_entity).add_child(visual_entity);
    spawn_weapon_colliders(
        commands,
        collider_bounds,
        transform_entity,
        &runtime_name,
        &definition,
    );
    commands.entity(parent_entity).add_child(root_entity);
}

pub(super) fn apply_pending_throw_motion_applications(
    mut commands: Commands,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    scene_entities: Query<(Entity, &SceneMaxEntity, &Transform, &GlobalTransform)>,
    equipped_weapons: Query<(
        Entity,
        &SceneMaxEquippedWeapon,
        &Transform,
        &GlobalTransform,
    )>,
) {
    if runtime_assets.pending_throw_motion_applications.is_empty() {
        return;
    }

    let pending = std::mem::take(&mut runtime_assets.pending_throw_motion_applications);
    for apply in pending {
        let Some(value) = runtime_assets
            .throw_motions_by_name
            .get(&apply.motion)
            .cloned()
        else {
            continue;
        };
        let Some(definition) =
            load_throw_motion_definition(&value.motion_asset_id, &mut runtime_assets)
        else {
            continue;
        };
        let Some((entity, transform, global_transform)) =
            resolve_throw_motion_target_entity(&apply, &scene_entities, &equipped_weapons)
        else {
            continue;
        };
        let start_world = global_transform.translation();
        let direction = resolve_throw_motion_direction(
            start_world,
            transform,
            &value,
            &scene_entities,
            &equipped_weapons,
        );
        let (right_axis, up_axis, forward_axis) = throw_motion_basis(direction);
        let target_distance = throw_motion_path_distance(&definition);
        let samples = sample_throw_motion_definition(
            &definition,
            Vec3::ZERO,
            Vec3::new(0.0, 0.0, target_distance),
            1.0 / 60.0,
        );
        if samples.is_empty() {
            continue;
        }
        let events = value
            .events
            .iter()
            .map(|event| ActiveThrowMotionEvent {
                event: event.event.clone(),
                index_percent: event
                    .index_percent
                    .as_ref()
                    .and_then(throw_motion_event_index),
                actions: event.actions.clone(),
                fired: false,
            })
            .collect();
        commands.entity(entity).insert(SceneMaxThrowMotion {
            samples,
            start_world,
            previous_world: start_world,
            right_axis,
            up_axis,
            forward_axis,
            elapsed_seconds: 0.0,
            previous_index_percent: -1.0,
            events,
            end_event_fired: false,
        });
    }
}

pub(super) fn update_throw_motions(
    time: Res<Time>,
    mut commands: Commands,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut motions: Query<(Entity, &mut Transform, &mut SceneMaxThrowMotion)>,
) {
    let delta = time.delta_secs().max(0.0);
    for (entity, mut transform, mut motion) in &mut motions {
        motion.elapsed_seconds += delta;
        let sample = sample_throw_motion_at(&motion.samples, motion.elapsed_seconds);
        let next_world = throw_motion_to_world(
            sample.position,
            motion.start_world,
            motion.right_axis,
            motion.up_axis,
            motion.forward_axis,
        );
        let delta_world = next_world - motion.previous_world;
        transform.translation += delta_world;
        motion.previous_world = next_world;

        let current_index = throw_motion_index_percent(&motion);
        let previous_index = motion.previous_index_percent;
        let mut fired_actions = Vec::new();
        for event in &mut motion.events {
            if event.fired || !event.event.eq_ignore_ascii_case("on_index") {
                continue;
            }
            let Some(index_percent) = event.index_percent else {
                continue;
            };
            if previous_index < index_percent && current_index >= index_percent {
                event.fired = true;
                fired_actions.push(event.actions.clone());
            }
        }

        let finished = motion
            .samples
            .last()
            .is_some_and(|sample| motion.elapsed_seconds >= sample.time);
        if finished && !motion.end_event_fired {
            motion.end_event_fired = true;
            fired_actions.extend(
                motion
                    .events
                    .iter()
                    .filter(|event| event.event.eq_ignore_ascii_case("on_end"))
                    .map(|event| event.actions.clone()),
            );
        }
        motion.previous_index_percent = current_index;

        for actions in fired_actions {
            if !actions.is_empty() {
                delayed_actions.actions.push(DelayedActions {
                    remaining_seconds: 0.0,
                    actions,
                    owner: None,
                    scope: None,
                });
            }
        }
        if finished {
            commands.entity(entity).remove::<SceneMaxThrowMotion>();
        }
    }
}

fn resolve_throw_motion_target_entity(
    apply: &ThrowMotionApplyStatement,
    scene_entities: &Query<(Entity, &SceneMaxEntity, &Transform, &GlobalTransform)>,
    equipped_weapons: &Query<(
        Entity,
        &SceneMaxEquippedWeapon,
        &Transform,
        &GlobalTransform,
    )>,
) -> Option<(Entity, Transform, GlobalTransform)> {
    if apply.target_is_equipped_weapon {
        return equipped_weapons
            .iter()
            .find(|(_, equipped, _, _)| equipped.owner == apply.target)
            .map(|(entity, _, transform, global_transform)| {
                (entity, *transform, *global_transform)
            });
    }
    scene_entities
        .iter()
        .find(|(_, scene_entity, _, _)| scene_entity.name == apply.target)
        .map(|(entity, _, transform, global_transform)| (entity, *transform, *global_transform))
}

fn resolve_throw_motion_direction(
    start_world: Vec3,
    transform: Transform,
    value: &RuntimeThrowMotionValue,
    scene_entities: &Query<(Entity, &SceneMaxEntity, &Transform, &GlobalTransform)>,
    equipped_weapons: &Query<(
        Entity,
        &SceneMaxEquippedWeapon,
        &Transform,
        &GlobalTransform,
    )>,
) -> Vec3 {
    let target = match &value.target {
        Some(RuntimeThrowMotionTarget::Object(target)) => scene_entities
            .iter()
            .find(|(_, scene_entity, _, _)| scene_entity.name == *target)
            .map(|(_, _, _, global_transform)| global_transform.translation())
            .or_else(|| {
                let lower = target.to_ascii_lowercase();
                if !lower.ends_with(".weapon") {
                    return None;
                }
                let owner = target[..target.len() - ".weapon".len()].trim();
                equipped_weapons
                    .iter()
                    .find(|(_, equipped, _, _)| equipped.owner == owner)
                    .map(|(_, _, _, global_transform)| global_transform.translation())
            }),
        Some(RuntimeThrowMotionTarget::Position(position)) => Some(*position),
        None => None,
    };
    let mut direction = target
        .map(|target| target - start_world)
        .unwrap_or(Vec3::ZERO);
    if direction.length_squared() < 0.0001 {
        direction = transform.rotation * Vec3::Z;
    }
    if direction.length_squared() < 0.0001 {
        direction = Vec3::Z;
    }
    direction.normalize()
}

fn throw_motion_basis(forward: Vec3) -> (Vec3, Vec3, Vec3) {
    let forward_axis = if forward.length_squared() > 0.0001 {
        forward.normalize()
    } else {
        Vec3::Z
    };
    let world_up = if forward_axis.dot(Vec3::Y).abs() > 0.96 {
        Vec3::X
    } else {
        Vec3::Y
    };
    let right_axis = world_up.cross(forward_axis).normalize_or_zero();
    let up_axis = forward_axis.cross(right_axis).normalize_or_zero();
    (right_axis, up_axis, forward_axis)
}

fn throw_motion_to_world(
    local: Vec3,
    start_world: Vec3,
    right_axis: Vec3,
    up_axis: Vec3,
    forward_axis: Vec3,
) -> Vec3 {
    start_world + right_axis * local.x + up_axis * local.y + forward_axis * local.z
}

fn throw_motion_index_percent(motion: &SceneMaxThrowMotion) -> f32 {
    let total = motion
        .samples
        .last()
        .map(|sample| sample.time)
        .unwrap_or(0.0);
    if total <= f32::EPSILON {
        return 100.0;
    }
    (motion.elapsed_seconds / total).clamp(0.0, 1.0) * 100.0
}

fn throw_motion_event_index(value: &AssignmentValue) -> Option<f32> {
    match value {
        AssignmentValue::Number(value) => Some(value.clamp(0.0, 100.0)),
        _ => None,
    }
}

fn throw_motion_duration_for_apply(
    apply: &ThrowMotionApplyStatement,
    runtime_assets: &mut SceneMaxRuntimeAssets,
) -> Option<f32> {
    let motion_asset_id = runtime_assets
        .throw_motions_by_name
        .get(&apply.motion)?
        .motion_asset_id
        .clone();
    let definition = load_throw_motion_definition(&motion_asset_id, runtime_assets)?;
    sample_throw_motion_definition(
        &definition,
        Vec3::ZERO,
        Vec3::new(0.0, 0.0, throw_motion_path_distance(&definition)),
        1.0 / 60.0,
    )
    .last()
    .map(|sample| sample.time.max(0.001))
}

fn load_throw_motion_definition(
    motion_asset_id: &str,
    runtime_assets: &mut SceneMaxRuntimeAssets,
) -> Option<RuntimeThrowMotionDefinition> {
    if let Some(definition) = runtime_assets
        .throw_motion_definitions_by_id
        .get(motion_asset_id)
        .cloned()
    {
        return Some(definition);
    }
    let asset_root = runtime_assets.asset_root.clone()?;
    let definition = read_throw_motion_definition(&asset_root, motion_asset_id)?;
    runtime_assets
        .throw_motion_definitions_by_id
        .insert(motion_asset_id.to_owned(), definition.clone());
    Some(definition)
}

fn read_throw_motion_definition(
    asset_root: &Path,
    motion_asset_id: &str,
) -> Option<RuntimeThrowMotionDefinition> {
    for path in direct_throw_motion_definition_paths(asset_root, motion_asset_id) {
        if let Some(definition) = read_throw_motion_definition_file(&path) {
            return Some(definition);
        }
    }
    for dir in throw_motion_definition_dirs(asset_root) {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if !path
                .extension()
                .and_then(|extension| extension.to_str())
                .is_some_and(|extension| extension.eq_ignore_ascii_case("smmotion"))
            {
                continue;
            }
            let Some(definition) = read_throw_motion_definition_file(&path) else {
                continue;
            };
            let stem_matches = path
                .file_stem()
                .and_then(|stem| stem.to_str())
                .is_some_and(|stem| stem.eq_ignore_ascii_case(motion_asset_id));
            let id_matches = definition
                .id
                .as_deref()
                .is_some_and(|id| id.eq_ignore_ascii_case(motion_asset_id));
            if stem_matches || id_matches {
                return Some(definition);
            }
        }
    }
    None
}

fn direct_throw_motion_definition_paths(asset_root: &Path, motion_asset_id: &str) -> Vec<PathBuf> {
    throw_motion_definition_dirs(asset_root)
        .into_iter()
        .map(|dir| dir.join(format!("{motion_asset_id}.smmotion")))
        .collect()
}

fn throw_motion_definition_dirs(asset_root: &Path) -> Vec<PathBuf> {
    vec![
        asset_root.join("throw_motions"),
        asset_root.join("Throw_Motions"),
        asset_root.join("motions"),
        asset_root.join("Motions"),
    ]
}

fn read_throw_motion_definition_file(path: &Path) -> Option<RuntimeThrowMotionDefinition> {
    let text = fs::read_to_string(path).ok()?;
    serde_json::from_str(&text).ok()
}

fn sample_throw_motion_at(samples: &[ThrowMotionSample], time: f32) -> ThrowMotionSample {
    let Some(first) = samples.first().copied() else {
        return ThrowMotionSample {
            time,
            position: Vec3::ZERO,
            velocity: Vec3::ZERO,
            spin_degrees: 0.0,
        };
    };
    let last = samples.last().copied().unwrap_or(first);
    if time <= 0.0 {
        return first;
    }
    if time >= last.time {
        return last;
    }
    for pair in samples.windows(2) {
        let previous = pair[0];
        let current = pair[1];
        if current.time >= time {
            let span = (current.time - previous.time).max(0.0001);
            let u = ((time - previous.time) / span).clamp(0.0, 1.0);
            return ThrowMotionSample {
                time,
                position: previous.position.lerp(current.position, u),
                velocity: previous.velocity.lerp(current.velocity, u),
                spin_degrees: previous.spin_degrees
                    + (current.spin_degrees - previous.spin_degrees) * u,
            };
        }
    }
    last
}

fn sample_throw_motion_definition(
    definition: &RuntimeThrowMotionDefinition,
    start: Vec3,
    target: Vec3,
    fixed_delta: f32,
) -> Vec<ThrowMotionSample> {
    let dt = fixed_delta.max(0.01);
    match normalized_throw_motion_type(&definition.motion_type).as_str() {
        "target_arc" => sample_throw_motion_target_arc(&definition.parameters, start, target, dt),
        "straight" => sample_throw_motion_straight(&definition.parameters, start, target, dt),
        "homing" => sample_throw_motion_homing(&definition.parameters, start, target, dt),
        "returning" => sample_throw_motion_returning(&definition.parameters, start, dt),
        _ => sample_throw_motion_ballistic(&definition.parameters, start, dt),
    }
}

fn sample_throw_motion_ballistic(
    parameters: &RuntimeThrowMotionParameters,
    start: Vec3,
    dt: f32,
) -> Vec<ThrowMotionSample> {
    let speed = parameters.initial_speed.max(0.1);
    let angle = parameters.launch_angle.to_radians();
    let mut position = start;
    let mut velocity = Vec3::new(0.0, angle.sin() * speed, angle.cos() * speed);
    let gravity = 9.81 * parameters.gravity_scale;
    let max_time = parameters.max_lifetime.max(0.2);
    let mut samples = Vec::new();
    let mut time = 0.0;
    while time <= max_time {
        samples.push(throw_motion_sample(parameters, time, position, velocity));
        velocity.y -= gravity * dt;
        position += velocity * dt;
        time += dt;
    }
    samples
}

fn sample_throw_motion_target_arc(
    parameters: &RuntimeThrowMotionParameters,
    start: Vec3,
    target: Vec3,
    dt: f32,
) -> Vec<ThrowMotionSample> {
    let duration = parameters.duration.max(0.1);
    let mut previous = start;
    let mut samples = Vec::new();
    let mut time = 0.0;
    while time <= duration + 0.0001 {
        let raw = (time / duration).clamp(0.0, 1.0);
        let u = throw_motion_ease(raw, &parameters.easing_function);
        let mut position = start.lerp(target, u);
        position.y += parameters.arc_height * 4.0 * raw * (1.0 - raw);
        let velocity = if time <= 0.0 {
            (target - start).normalize_or_zero()
        } else {
            (position - previous) / dt
        };
        samples.push(throw_motion_sample(parameters, time, position, velocity));
        previous = position;
        time += dt;
    }
    samples
}

fn sample_throw_motion_straight(
    parameters: &RuntimeThrowMotionParameters,
    start: Vec3,
    target: Vec3,
    dt: f32,
) -> Vec<ThrowMotionSample> {
    let mut speed = parameters.speed.max(0.1);
    let acceleration = parameters.acceleration;
    let max_distance = parameters.max_distance.max(0.1);
    let max_time = parameters.max_lifetime.max(0.2);
    let direction = throw_motion_direction_to_target(start, target);
    let mut position = start;
    let mut distance = 0.0;
    let mut samples = Vec::new();
    let mut time = 0.0;
    while time <= max_time && distance <= max_distance {
        let velocity = direction * speed;
        samples.push(throw_motion_sample(parameters, time, position, velocity));
        let step = (speed * dt).max(0.0);
        position += direction * step;
        distance += step;
        speed += acceleration * dt;
        time += dt;
    }
    samples
}

fn sample_throw_motion_homing(
    parameters: &RuntimeThrowMotionParameters,
    start: Vec3,
    target: Vec3,
    dt: f32,
) -> Vec<ThrowMotionSample> {
    let speed = parameters.speed.max(0.1);
    let max_time = parameters.max_lifetime.max(0.2);
    let turn_rate = parameters.turn_rate.max(1.0).to_radians();
    let mut position = start;
    let mut direction = Vec3::new(0.0, 0.08, 1.0).normalize();
    let mut samples = Vec::new();
    let mut time = 0.0;
    while time <= max_time {
        let desired = target - position;
        if desired.length_squared() > 0.0001 && time >= parameters.homing_delay {
            let blend = (turn_rate * dt * parameters.homing_strength.max(0.0)).clamp(0.0, 1.0);
            direction = direction
                .lerp(desired.normalize(), blend)
                .normalize_or_zero();
        }
        let velocity = direction * speed;
        samples.push(throw_motion_sample(parameters, time, position, velocity));
        position += velocity * dt;
        if position.distance(target) <= parameters.collision_radius.max(0.2) {
            samples.push(throw_motion_sample(parameters, time + dt, target, velocity));
            break;
        }
        time += dt;
    }
    samples
}

fn sample_throw_motion_returning(
    parameters: &RuntimeThrowMotionParameters,
    start: Vec3,
    dt: f32,
) -> Vec<ThrowMotionSample> {
    let outbound_duration = parameters.outbound_duration.max(0.1);
    let outbound_target = start + Vec3::new(0.0, 0.0, parameters.outbound_distance);
    let mut previous = start;
    let mut samples = Vec::new();
    let mut time = 0.0;
    while time <= outbound_duration + 0.0001 {
        let u = (time / outbound_duration).clamp(0.0, 1.0);
        let eased = throw_motion_ease(u, &parameters.easing_function);
        let mut position = start.lerp(outbound_target, eased);
        position.y += parameters.outbound_arc_height * 4.0 * u * (1.0 - u);
        let velocity = if time <= 0.0 {
            (outbound_target - start).normalize_or_zero()
        } else {
            (position - previous) / dt
        };
        samples.push(throw_motion_sample(parameters, time, position, velocity));
        previous = position;
        time += dt;
    }
    let delay = parameters.return_delay.max(0.0);
    if delay > 0.0 {
        samples.push(throw_motion_sample(
            parameters,
            outbound_duration + delay,
            previous,
            Vec3::ZERO,
        ));
    }
    let return_speed = parameters.return_speed.max(0.1);
    let return_distance = previous.distance(start);
    let return_duration = (return_distance / return_speed).max(0.1);
    time = dt;
    while time <= return_duration + 0.0001 {
        let u = (time / return_duration).clamp(0.0, 1.0);
        let position = previous.lerp(start, throw_motion_ease(u, &parameters.easing_function));
        let velocity = (start - previous).normalize_or_zero() * return_speed;
        samples.push(throw_motion_sample(
            parameters,
            outbound_duration + delay + time,
            position,
            velocity,
        ));
        time += dt;
    }
    samples
}

fn throw_motion_sample(
    parameters: &RuntimeThrowMotionParameters,
    time: f32,
    position: Vec3,
    velocity: Vec3,
) -> ThrowMotionSample {
    ThrowMotionSample {
        time,
        position,
        velocity,
        spin_degrees: parameters.spin_speed * time,
    }
}

fn throw_motion_direction_to_target(start: Vec3, target: Vec3) -> Vec3 {
    let mut direction = target - start;
    direction.y = 0.0;
    if direction.length_squared() < 0.0001 {
        return Vec3::Z;
    }
    direction.normalize()
}

fn throw_motion_ease(time: f32, easing: &str) -> f32 {
    match easing.trim().to_ascii_lowercase().as_str() {
        "ease_in" => time * time,
        "ease_out" => 1.0 - (1.0 - time) * (1.0 - time),
        "ease_in_out" if time < 0.5 => 2.0 * time * time,
        "ease_in_out" => 1.0 - (-2.0 * time + 2.0).powi(2) / 2.0,
        _ => time,
    }
}

fn throw_motion_path_distance(definition: &RuntimeThrowMotionDefinition) -> f32 {
    match normalized_throw_motion_type(&definition.motion_type).as_str() {
        "straight" | "homing" => definition.parameters.max_distance.max(0.1),
        "returning" => definition.parameters.outbound_distance.max(0.1),
        "target_arc" => 12.0,
        _ => 12.0,
    }
}

fn normalized_throw_motion_type(value: &str) -> String {
    value.trim().to_ascii_lowercase().replace('-', "_")
}

fn despawn_equipped_weapon(
    owner: &str,
    commands: &mut Commands,
    equipped_weapons: &Query<(Entity, &SceneMaxEquippedWeapon)>,
    collider_bounds: &mut SceneMaxColliderBounds,
) {
    for (entity, equipped) in equipped_weapons {
        if equipped.owner == owner {
            for collider in &equipped.colliders {
                unregister_weapon_collider_bounds(collider_bounds, collider);
            }
            commands.entity(entity).despawn();
        }
    }
}

fn detach_equipped_weapon(
    owner: &str,
    commands: &mut Commands,
    equipped_weapons: &Query<(Entity, &SceneMaxEquippedWeapon)>,
) {
    for (entity, equipped) in equipped_weapons {
        if equipped.owner == owner {
            commands.entity(entity).remove_parent_in_place();
        }
    }
}

fn inverse_scale_component(scale: f32) -> f32 {
    if scale.is_finite() && scale.abs() > 0.000001 {
        1.0 / scale
    } else {
        1.0
    }
}

fn find_descendant_entity_by_name(
    root: Entity,
    wanted_name: &str,
    children: &Query<&Children>,
    named_nodes: &Query<(Entity, &Name)>,
) -> Option<Entity> {
    for child in children.get(root).ok()?.iter() {
        if let Ok((entity, name)) = named_nodes.get(child)
            && names_match(name.as_str(), wanted_name)
        {
            return Some(entity);
        }
        if let Some(entity) =
            find_descendant_entity_by_name(child, wanted_name, children, named_nodes)
        {
            return Some(entity);
        }
    }
    None
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuntimeWeaponDefinition {
    id: Option<String>,
    #[serde(default)]
    model_asset_id: Option<String>,
    #[serde(default)]
    default_posture_id: Option<String>,
    #[serde(default)]
    postures: Vec<RuntimeWeaponPosture>,
    #[serde(default)]
    colliders: Vec<RuntimeWeaponCollider>,
}

impl RuntimeWeaponDefinition {
    fn posture(&self, id_or_name: Option<&str>) -> Option<&RuntimeWeaponPosture> {
        if let Some(id_or_name) = id_or_name {
            let id_or_name = id_or_name.trim();
            if !id_or_name.is_empty()
                && let Some(posture) = self.postures.iter().find(|posture| {
                    posture
                        .id
                        .as_deref()
                        .is_some_and(|id| id.eq_ignore_ascii_case(id_or_name))
                        || posture
                            .name
                            .as_deref()
                            .is_some_and(|name| name.eq_ignore_ascii_case(id_or_name))
                })
            {
                return Some(posture);
            }
        }
        self.default_posture_id
            .as_deref()
            .and_then(|default_id| {
                self.postures.iter().find(|posture| {
                    posture
                        .id
                        .as_deref()
                        .is_some_and(|id| id.eq_ignore_ascii_case(default_id))
                        || posture
                            .name
                            .as_deref()
                            .is_some_and(|name| name.eq_ignore_ascii_case(default_id))
                })
            })
            .or_else(|| self.postures.first())
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuntimeWeaponCollider {
    #[serde(default)]
    name: String,
    #[serde(default)]
    shape: Option<String>,
    #[serde(default)]
    transform: Option<RuntimeWeaponTransform>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuntimeWeaponPosture {
    #[serde(default)]
    id: Option<String>,
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    attachment_point: Option<String>,
    #[serde(default)]
    transform: Option<RuntimeWeaponTransform>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuntimeWeaponTransform {
    #[serde(default)]
    offset_x: f32,
    #[serde(default)]
    offset_y: f32,
    #[serde(default)]
    offset_z: f32,
    #[serde(default)]
    rotation_x: f32,
    #[serde(default)]
    rotation_y: f32,
    #[serde(default)]
    rotation_z: f32,
    #[serde(default = "one_f32")]
    scale_x: f32,
    #[serde(default = "one_f32")]
    scale_y: f32,
    #[serde(default = "one_f32")]
    scale_z: f32,
}

fn one_f32() -> f32 {
    1.0
}

fn weapon_transform(transform: &RuntimeWeaponTransform) -> Transform {
    Transform {
        translation: Vec3::new(transform.offset_x, transform.offset_y, transform.offset_z),
        rotation: Quat::from_euler(
            EulerRot::XYZ,
            transform.rotation_x.to_radians(),
            transform.rotation_y.to_radians(),
            transform.rotation_z.to_radians(),
        ),
        scale: Vec3::new(transform.scale_x, transform.scale_y, transform.scale_z),
    }
}

fn weapon_collider_references(
    weapon_runtime_name: &str,
    definition: &RuntimeWeaponDefinition,
) -> Vec<String> {
    definition
        .colliders
        .iter()
        .filter_map(|collider| weapon_collider_reference(weapon_runtime_name, collider))
        .collect()
}

fn weapon_collider_reference(
    weapon_runtime_name: &str,
    collider: &RuntimeWeaponCollider,
) -> Option<String> {
    let name = collider.name.trim();
    (!name.is_empty()).then(|| format!("{weapon_runtime_name}.colliders[\"{name}\"]"))
}

fn spawn_weapon_colliders(
    commands: &mut Commands,
    collider_bounds: &mut SceneMaxColliderBounds,
    parent_entity: Entity,
    weapon_runtime_name: &str,
    definition: &RuntimeWeaponDefinition,
) {
    for collider in &definition.colliders {
        let Some(runtime_name) = weapon_collider_reference(weapon_runtime_name, collider) else {
            continue;
        };
        let transform = collider
            .transform
            .as_ref()
            .map(weapon_transform)
            .unwrap_or_default();
        let shape = weapon_collider_shape(collider.shape.as_deref());
        let options = EntityOptions {
            collision_shape: Some(shape),
            ..Default::default()
        };
        let collider_entity = commands
            .spawn((
                SceneMaxEntity {
                    name: runtime_name.clone(),
                    runtime_name: format!("{runtime_name}@weapon_collider"),
                },
                transform,
                Visibility::Hidden,
                AvianRigidBody::Kinematic,
                avian_collider(shape, &options, &transform),
                hitbox_collision_layers(),
                Sensor,
                CollisionEventsEnabled,
                Name::new(runtime_name.clone()),
            ))
            .id();
        commands.entity(parent_entity).add_child(collider_entity);
        register_collider_bounds(collider_bounds, &runtime_name, &options, transform);
        register_collider_owner(collider_bounds, &runtime_name, weapon_runtime_name);
    }
}

fn weapon_collider_shape(shape: Option<&str>) -> SceneMaxCollisionShape {
    match shape.unwrap_or("box").trim().to_ascii_lowercase().as_str() {
        "sphere" => SceneMaxCollisionShape::Sphere,
        "capsule" => SceneMaxCollisionShape::Capsule,
        "none" => SceneMaxCollisionShape::None,
        _ => SceneMaxCollisionShape::Box,
    }
}

fn unregister_weapon_collider_bounds(
    collider_bounds: &mut SceneMaxColliderBounds,
    collider_name: &str,
) {
    collider_bounds.radius_by_name.remove(collider_name);
    collider_bounds.shape_by_name.remove(collider_name);
    collider_bounds.owner_by_name.remove(collider_name);
    collider_bounds.hidden_by_name.remove(collider_name);
}

#[cfg(test)]
mod weapon_runtime_tests {
    use super::*;

    #[test]
    fn weapon_collider_references_use_script_lookup_syntax() {
        let definition: RuntimeWeaponDefinition = serde_json::from_str(
            r#"{
                "modelAssetId": "training_blade",
                "colliders": [
                    { "name": "hit_sphere", "shape": "sphere" }
                ]
            }"#,
        )
        .unwrap();

        assert_eq!(
            weapon_collider_references("actor.weapon", &definition),
            vec!["actor.weapon.colliders[\"hit_sphere\"]".to_owned()]
        );
        assert_eq!(
            weapon_collider_shape(definition.colliders[0].shape.as_deref()),
            SceneMaxCollisionShape::Sphere
        );
    }
}

fn load_weapon_definition(asset_root: &Path, weapon: &str) -> Option<RuntimeWeaponDefinition> {
    for path in direct_weapon_definition_paths(asset_root, weapon) {
        if let Some(definition) = read_weapon_definition(&path) {
            return Some(definition);
        }
    }
    for dir in weapon_definition_dirs(asset_root) {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if !path
                .extension()
                .and_then(|extension| extension.to_str())
                .is_some_and(|extension| extension.eq_ignore_ascii_case("smweapon"))
            {
                continue;
            }
            let Some(definition) = read_weapon_definition(&path) else {
                continue;
            };
            let stem_matches = path
                .file_stem()
                .and_then(|stem| stem.to_str())
                .is_some_and(|stem| stem.eq_ignore_ascii_case(weapon));
            let id_matches = definition
                .id
                .as_deref()
                .is_some_and(|id| id.eq_ignore_ascii_case(weapon));
            if stem_matches || id_matches {
                return Some(definition);
            }
        }
    }
    None
}

fn direct_weapon_definition_paths(asset_root: &Path, weapon: &str) -> Vec<PathBuf> {
    weapon_definition_dirs(asset_root)
        .into_iter()
        .map(|dir| dir.join(format!("{weapon}.smweapon")))
        .collect()
}

fn weapon_definition_dirs(asset_root: &Path) -> Vec<PathBuf> {
    vec![asset_root.join("weapons"), asset_root.join("Weapons")]
}

fn read_weapon_definition(path: &Path) -> Option<RuntimeWeaponDefinition> {
    let text = fs::read_to_string(path).ok()?;
    serde_json::from_str(&text).ok()
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
    runtime_assets: &mut SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    mut delayed_actions: Option<&mut DelayedActionQueue>,
    mut ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
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
    runtime_declared_entities: &mut HashMap<String, Entity>,
) -> ActionSequenceResult {
    if matches!(action, Statement::NoOp { .. }) {
        return ActionSequenceResult::Completed;
    }
    if let Statement::KeyEvent(event) = action {
        if let Some(delayed_actions) = delayed_actions.as_deref_mut() {
            register_key_event(&mut delayed_actions.registered_key_events, event.clone());
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::WhenEvent(event) = action {
        if let Some(delayed_actions) = delayed_actions.as_deref_mut() {
            register_when_event(&mut delayed_actions.registered_when_events, event.clone());
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::RunEvery { .. } = action {
        if let Some(delayed_actions) = delayed_actions.as_deref_mut() {
            register_run_every(&mut delayed_actions.registered_run_every, action.clone());
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::Unsupported { text } = action {
        tracing::debug!(text, "skipping unsupported SceneMax runtime action");
        return ActionSequenceResult::Completed;
    }
    if let Statement::LightDecl(light) = action {
        for (entity, scene_entity, _, _, _, _, _, _) in &mut scene_entities.p1() {
            if scene_entity.name == light.name {
                commands.entity(entity).despawn();
                break;
            }
        }
        let (entity, transform) = spawn_scenemax_light_decl(
            commands,
            light,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        );
        transforms_by_name.insert(light.name.clone(), transform);
        runtime_declared_entities.insert(light.name.clone(), entity);
        return ActionSequenceResult::Completed;
    }
    if let Statement::LightProbeAdd(probe) = action {
        for (entity, scene_entity, _, _, _, _, _, _) in &mut scene_entities.p1() {
            if scene_entity.name == probe.name {
                commands.entity(entity).despawn();
                break;
            }
        }
        let (entity, transform) = apply_light_probe_add(
            commands,
            probe,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        );
        transforms_by_name.insert(probe.name.clone(), transform);
        runtime_declared_entities.insert(probe.name.clone(), entity);
        return ActionSequenceResult::Completed;
    }
    if let Statement::ModelDecl {
        name,
        resource,
        options,
    } = action
    {
        if let Some(camera_system) = camera_system.as_deref_mut()
            && register_cinematic_camera_var(name, resource, camera_system)
        {
            return ActionSequenceResult::Completed;
        }
        if let Some(entity) = apply_runtime_model_decl(
            name,
            resource,
            options,
            transforms_by_name,
            vars,
            guards_by_name,
            runtime_assets,
            collider_bounds,
            commands,
            scene_entities,
        ) {
            runtime_declared_entities.insert(name.clone(), entity);
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::CinematicPlay(play) = action {
        if let Some(camera_system) = camera_system.as_deref_mut() {
            start_cinematic_camera(
                play,
                transforms_by_name,
                object_pools,
                scope.as_deref(),
                camera_system,
            );
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::SetEnvironmentShader { shader } = action {
        let shader_name = resolve_shader_name(
            shader,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        );
        apply_environment_shader(commands, shader_name, runtime_assets);
        return ActionSequenceResult::Completed;
    }
    if let Statement::SetShader(shader) = action {
        let shader_name = resolve_shader_name(
            &shader.shader,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        );
        let target_entity =
            scene_entities
                .p1()
                .iter()
                .find_map(|(entity, scene_entity, _, _, _, _, _, _)| {
                    target_matches_alias(
                        &shader.target,
                        &scene_entity.name,
                        object_pools,
                        scope.as_deref(),
                    )
                    .then_some(entity)
                });
        if let Some(entity) = target_entity {
            apply_entity_shader(commands, entity, shader_name, runtime_assets);
        } else {
            write_runtime_diagnostic_line(format!(
                "SHADER:TARGET_MISS phase=runtime target={}",
                shader.target
            ));
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::Weapon(weapon) = action {
        enqueue_weapon_action(weapon, runtime_assets);
        return ActionSequenceResult::Completed;
    }
    if let Statement::AnimationControllerAction(action) = action {
        apply_runtime_animation_controller_action(
            action,
            commands,
            runtime_assets,
            object_pools,
            scope.as_deref(),
            scene_entities,
            queued_animations,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::AnimationControllerEvent(event) = action {
        register_animation_controller_event(
            event,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
            runtime_assets,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::ThrowMotionApply(apply) = action {
        enqueue_throw_motion_application(apply, runtime_assets);
        return ActionSequenceResult::Completed;
    }
    if let Statement::ThrowMotionEvent(event) = action {
        register_throw_motion_event(event, runtime_assets);
        return ActionSequenceResult::Completed;
    }
    if let Statement::Assignment(assignment)
    | Statement::SharedAssignment(assignment)
    | Statement::LocalAssignment(assignment) = action
    {
        if let AssignmentValue::PoolAcquire { pool } = &assignment.value {
            let Some(member) = acquire_pool_member(
                pool,
                transforms_by_name,
                vars,
                object_pools,
                functions_by_name,
                guards_by_name,
                runtime_assets,
                collider_bounds,
                commands,
                scene_entities,
            ) else {
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
        if let AssignmentValue::CameraModifier(value) = &assignment.value {
            if let Some(camera_system) = camera_system.as_deref_mut() {
                register_camera_modifier(camera_system, &assignment.name, value);
            }
            return ActionSequenceResult::Completed;
        }
        if let AssignmentValue::AnimationController(value) = &assignment.value {
            register_animation_controller_assignment(&assignment.name, value, runtime_assets);
            return ActionSequenceResult::Completed;
        }
        if let AssignmentValue::ThrowMotion(value) = &assignment.value {
            register_throw_motion_assignment(
                &assignment.name,
                value,
                vars,
                scope.as_deref(),
                guards_by_name,
                Some(transforms_by_name),
                Some(collider_bounds),
                runtime_assets,
            );
            return ActionSequenceResult::Completed;
        }
        apply_assignment_scoped(
            assignment,
            vars,
            scope.as_deref_mut(),
            Some(transforms_by_name),
            guards_by_name,
            Some(collider_bounds),
            matches!(action, Statement::LocalAssignment(_)),
        );
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
    if let Statement::CameraModifierApply(apply) = action {
        if let Some(camera_system) = camera_system {
            let overrides = resolved_camera_modifier_overrides(
                &apply.overrides,
                vars,
                scope.as_deref(),
                guards_by_name,
                Some(transforms_by_name),
                Some(collider_bounds),
            );
            apply_camera_modifier(camera_system, &apply.target, &apply.modifier, &overrides);
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::CameraMove(camera_move) = action {
        let distance = resolve_draw_value(
            Some(&camera_move.distance_value),
            camera_move.distance,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        );
        let duration_seconds = resolve_duration_value(
            &camera_move.duration_value,
            camera_move.duration_seconds,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        );
        append_timed_camera_move(
            commands,
            timed_camera_move_from_statement_resolved(camera_move, distance, duration_seconds),
        );
        write_runtime_diagnostic_line(format!(
            "CAMERA:MOVE queue source=runtime axis={} distance={} duration={} async={}",
            axis_label(camera_move.axis),
            format_scenemax_number(distance),
            format_scenemax_number(duration_seconds),
            camera_move.async_run as u8
        ));
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
    if let Statement::Audio(audio) = action {
        apply_audio_statement(
            audio,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
            runtime_assets,
            commands,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::DebugMode { enabled } = action {
        set_debug_mode(commands, *enabled);
        return ActionSequenceResult::Completed;
    }
    if let Statement::UiSetProperty(property) = action {
        if let Some(ui_queue) = ui_queue.as_deref_mut() {
            let value = resolve_ui_property_value(
                &property.value,
                vars,
                scope.as_deref(),
                guards_by_name,
                Some(transforms_by_name),
                Some(collider_bounds),
            );
            ui_queue.actions.push(SceneMaxUiAction::SetProperty {
                target: property.target.clone(),
                property: property.property.clone(),
                value,
            });
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::ChannelDraw(draw) = action {
        if let Some(ui_queue) = ui_queue.as_deref_mut() {
            ui_queue.actions.push(scenemax_draw_action_from_statement(
                draw,
                vars,
                scope.as_deref(),
                guards_by_name,
                Some(transforms_by_name),
                Some(collider_bounds),
            ));
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::Print(print) = action {
        if let Some(ui_queue) = ui_queue.as_deref_mut() {
            ui_queue.actions.push(scenemax_print_action_from_statement(
                print,
                vars,
                scope.as_deref(),
                guards_by_name,
                Some(transforms_by_name),
                Some(collider_bounds),
            ));
        }
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
        let resolved_target = resolve_object_alias(target, object_pools, scope.as_deref());
        if let Some(entity) = runtime_declared_entities.remove(&resolved_target) {
            commands.entity(entity).despawn();
            transforms_by_name.remove(&resolved_target);
            write_runtime_diagnostic_line(format!(
                "RUNTIME:DELETE_IMMEDIATE target={} entity={:?}",
                resolved_target, entity
            ));
            return ActionSequenceResult::Completed;
        }
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
                runtime_declared_entities,
            );
            if result.should_stop_parent() {
                return result;
            }
        }
        return ActionSequenceResult::Completed;
    }

    if let Statement::EffekseerPlay(play) = action {
        let resolved_target = resolve_object_alias(&play.target, object_pools, scope.as_deref());
        if let Some(entity) = runtime_declared_entities.get(&resolved_target).copied() {
            let mut transform = transforms_by_name
                .get(&resolved_target)
                .copied()
                .unwrap_or_default();
            if let Some(translation) = resolved_effekseer_play_translation(
                play,
                vars,
                scope.as_deref(),
                guards_by_name,
                transforms_by_name,
                Some(collider_bounds),
            ) {
                transform.translation = translation;
                transforms_by_name.insert(resolved_target.clone(), transform);
                commands.entity(entity).insert(transform);
            }
            commands.entity(entity).insert(resolved_effekseer_playback(
                play,
                vars,
                scope.as_deref(),
                guards_by_name,
                Some(transforms_by_name),
                Some(collider_bounds),
            ));
            write_runtime_diagnostic_line(format!(
                "EFFEKSEER:PLAY_IMMEDIATE target={} entity={:?}",
                resolved_target, entity
            ));
            write_effekseer_bridge_play(&resolved_target, play);
            return ActionSequenceResult::Completed;
        }
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
                    let speed = resolve_animation_speed_value(
                        &animation.speed_value,
                        animation.speed,
                        vars,
                        scope.as_deref(),
                        guards_by_name,
                        Some(transforms_by_name),
                        Some(collider_bounds),
                    );
                    commands.entity(entity).insert(AnimationToPlay {
                        clip: animation.clip.clone(),
                        runtime_clip: animation.clip.clone(),
                        looped: animation.looped,
                        speed,
                        gltf: gltf.gltf.clone(),
                        target_model_resource: None,
                        baked_external: None,
                        bake_request: None,
                        external_retarget: Default::default(),
                        external_source: false,
                        tried_external_source: false,
                        visual_transform_preapplied: false,
                        retarget_wait_logged: false,
                    });
                    queued_animations.insert(entity, (animation.clip.clone(), animation.looped));
                }
            }
            Statement::SpritePlay(sprite_play)
                if target_matches_alias(
                    &sprite_play.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                commands.entity(entity).insert(resolved_sprite_animation(
                    sprite_play,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ));
                write_runtime_diagnostic_line(format!(
                    "started sprite animation target={} frames={}..{} duration={:.3}s loop={}",
                    scene_entity.name,
                    sprite_play.from_frame,
                    sprite_play.to_frame,
                    sprite_play.duration_seconds,
                    sprite_play.looped
                ));
            }
            Statement::EffekseerPlay(play)
                if target_matches_alias(
                    &play.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(translation) = resolved_effekseer_play_translation(
                    play,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    transforms_by_name,
                    Some(collider_bounds),
                ) {
                    transform.translation = translation;
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                }
                commands.entity(entity).insert(resolved_effekseer_playback(
                    play,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ));
                write_effekseer_bridge_play(&scene_entity.name, play);
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
                    .insert(resolved_animation_speed_override(
                        animation_speed,
                        vars,
                        scope.as_deref(),
                        guards_by_name,
                        Some(transforms_by_name),
                        Some(collider_bounds),
                    ));
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
                if let Some(translation) = evaluate_position_value_runtime(
                    &position.position,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    transforms_by_name,
                    Some(collider_bounds),
                ) {
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
                let degrees = resolve_draw_value(
                    Some(&turn.degrees_value),
                    turn.degrees,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                );
                let duration_seconds = resolve_duration_value(
                    &turn.duration_value,
                    turn.duration_seconds,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                );
                if let Some(delta_seconds) = continuous_delta_seconds {
                    let timed_turn =
                        timed_turn_from_statement_resolved(turn, degrees, duration_seconds);
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
                        .insert(timed_turn_from_statement_resolved(
                            turn,
                            degrees,
                            duration_seconds,
                        ));
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
                let distance = resolve_draw_value(
                    Some(&movement.distance_value),
                    movement.distance,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                );
                let duration_seconds = resolve_draw_value(
                    Some(&movement.duration_value),
                    movement.duration_seconds,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                );
                let timed_move = timed_move_from_statement_resolved(
                    movement,
                    &transform,
                    distance,
                    duration_seconds,
                );
                if let (Some(character_controller), Some(character_motor)) =
                    (character_controller, character_motor.as_deref_mut())
                {
                    set_character_move_intent_resolved(
                        character_motor,
                        character_controller,
                        movement,
                        &transform,
                        distance,
                        duration_seconds,
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
                if let Some(timed_move) = resolved_move_to(
                    move_to,
                    &transform,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    transforms_by_name,
                    Some(collider_bounds),
                ) {
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
                let speed = resolve_draw_value(
                    Some(&jump.speed_value),
                    jump.speed,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                );
                if let Some(character_motor) = character_motor.as_deref_mut() {
                    set_character_jump_intent_resolved(character_motor, speed);
                    commands
                        .entity(entity)
                        .insert(timed_jump_from_statement_resolved(jump, &transform, speed));
                } else {
                    commands
                        .entity(entity)
                        .insert(timed_jump_from_statement_resolved(jump, &transform, speed));
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
                let resolved = resolved_character_mode(
                    character_mode,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                );
                write_runtime_diagnostic_line(format!(
                    "CHARACTER:PENDING_REQUEST phase=runtime target={} matched_entity={} gravity={} owner={} pos=({},{},{})",
                    resolved.target,
                    scene_entity.name,
                    resolved
                        .gravity
                        .map(format_scenemax_number)
                        .unwrap_or_else(|| "default".to_owned()),
                    owner
                        .as_ref()
                        .map(describe_controller_key)
                        .unwrap_or_else(|| "-".to_owned()),
                    format_scenemax_number(transform.translation.x),
                    format_scenemax_number(transform.translation.y),
                    format_scenemax_number(transform.translation.z)
                ));
                commands
                    .entity(entity)
                    .insert(PendingCharacterMode(resolved));
            }
            Statement::ClearCharacterMode { target }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                write_runtime_diagnostic_line(format!(
                    "CHARACTER:CLEAR_REQUEST phase=runtime target={} matched_entity={} owner={} pos=({},{},{})",
                    target,
                    scene_entity.name,
                    owner
                        .as_ref()
                        .map(describe_controller_key)
                        .unwrap_or_else(|| "-".to_owned()),
                    format_scenemax_number(transform.translation.x),
                    format_scenemax_number(transform.translation.y),
                    format_scenemax_number(transform.translation.z)
                ));
                clear_character_mode(commands, entity, Some(&scene_entity.name), Some(&transform));
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
                let strength = resolve_draw_value(
                    Some(&impulse.strength_value),
                    impulse.strength,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                );
                apply_physics_impulse_resolved(commands, entity, &transform, impulse, strength);
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
                set_collider_hidden(collider_bounds, &scene_entity.name, !*visible);
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

fn set_debug_mode(commands: &mut Commands, enabled: bool) {
    commands.insert_resource(SceneMaxDebugMode { enabled });
    write_runtime_diagnostic_line(format!("debug mode {}", if enabled { "on" } else { "off" }));
}

pub(super) fn animation_speed_condition_matches(
    animation_speed: &AnimationSpeedStatement,
    vars: &SceneMaxVars,
    object_pools: &SceneMaxObjectPools,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    scene_entities: &mut ParamSet<(
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
    runtime_assets: &mut SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    mut delayed_actions: Option<&mut DelayedActionQueue>,
    ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
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
    depth: usize,
) -> ActionSequenceResult {
    if depth > 8 {
        tracing::warn!(name, "skipping deeply recursive SceneMax run");
        return ActionSequenceResult::Completed;
    }
    let Some(function) = functions_by_name.get(name) else {
        write_runtime_diagnostic_line(format!("FUNCTION:MISS phase=runtime name={name}"));
        tracing::debug!(
            name,
            "SceneMax function is not implemented or was not parsed"
        );
        return ActionSequenceResult::Completed;
    };
    let resolved_args = resolve_call_args(
        args,
        vars,
        scope.as_deref(),
        guards_by_name,
        Some(transforms_by_name),
        Some(collider_bounds),
    );
    if !function_guard_matches(
        function,
        &resolved_args,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        Some(collider_bounds),
    ) {
        if runtime_verbose_logging() {
            write_runtime_diagnostic_line(format!(
                "FUNCTION:SKIP phase=runtime name={name} reason=guard_false"
            ));
        }
        tracing::debug!(name, "SceneMax function guard is false");
        return ActionSequenceResult::Completed;
    }

    if runtime_verbose_logging() {
        write_runtime_diagnostic_line(format!(
            "FUNCTION:RUN phase=runtime name={name} actions={}",
            describe_statement_list(&function.actions)
        ));
    }
    let actions = instantiate_function_actions(function, &resolved_args);
    let mut function_scope = scope.cloned().unwrap_or_default();
    let result = apply_action_sequence(
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
        delayed_actions.as_deref_mut(),
        ui_queue,
        owner,
        Some(&mut function_scope),
        continuous_delta_seconds,
        commands,
        scene_entities,
    );
    result
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

pub(super) fn build_action_transform_map(
    program: &Program,
    object_pools: &SceneMaxObjectPools,
    scene_entities: Query<(
        Entity,
        &SceneMaxEntity,
        &Transform,
        Option<&GlobalTransform>,
        Option<&ChildOf>,
    )>,
    bone_queries: &SceneMaxBoneQueries,
) -> HashMap<String, Transform> {
    let mut transforms_by_name = HashMap::new();
    let scene_roots = collect_scene_transform_roots(&mut transforms_by_name, scene_entities);
    let targets = collect_bone_alias_targets(program);
    let bone_start = std::time::Instant::now();
    let alias_count = insert_bone_transform_aliases(
        &scene_roots,
        &targets,
        &bone_queries.children,
        &bone_queries.named_nodes,
        &mut transforms_by_name,
    );
    PERF_TRANSFORM_BUILDS.fetch_add(1, Ordering::Relaxed);
    PERF_BONE_ALIAS_NS.fetch_add(bone_start.elapsed().as_nanos() as u64, Ordering::Relaxed);
    PERF_BONE_TARGET_RESOLVES.fetch_add(targets.len() as u64, Ordering::Relaxed);
    PERF_BONE_ALIASES_INSERTED.fetch_add(alias_count as u64, Ordering::Relaxed);
    apply_transform_aliases(&mut transforms_by_name, object_pools);
    transforms_by_name
}

fn collect_scene_transform_roots(
    transforms_by_name: &mut HashMap<String, Transform>,
    scene_entities: Query<(
        Entity,
        &SceneMaxEntity,
        &Transform,
        Option<&GlobalTransform>,
        Option<&ChildOf>,
    )>,
) -> Vec<(Entity, String)> {
    scene_entities
        .iter()
        .map(
            |(entity, scene_entity, transform, global_transform, parent)| {
                transforms_by_name.insert(
                    scene_entity.name.clone(),
                    action_map_transform(transform, global_transform, parent),
                );
                (entity, scene_entity.name.clone())
            },
        )
        .collect()
}

fn action_map_transform(
    transform: &Transform,
    global_transform: Option<&GlobalTransform>,
    parent: Option<&ChildOf>,
) -> Transform {
    if parent.is_some() {
        global_transform
            .map(GlobalTransform::compute_transform)
            .unwrap_or(*transform)
    } else {
        *transform
    }
}

#[cfg(test)]
mod transform_map_tests {
    use super::*;

    #[test]
    fn action_map_prefers_global_transform_for_parented_entities() {
        let local = Transform::from_xyz(0.0, 0.0, 0.0);
        let global = GlobalTransform::from(Transform::from_xyz(4.0, 5.0, 6.0));

        let parent = ChildOf(Entity::PLACEHOLDER);
        let transform = action_map_transform(&local, Some(&global), Some(&parent));

        assert_eq!(transform.translation, Vec3::new(4.0, 5.0, 6.0));
    }

    #[test]
    fn action_map_prefers_local_transform_for_unparented_entities() {
        let local = Transform::from_xyz(7.0, 8.0, 9.0);
        let stale_global = GlobalTransform::from(Transform::from_xyz(1.0, 2.0, 3.0));

        let transform = action_map_transform(&local, Some(&stale_global), None);

        assert_eq!(transform.translation, Vec3::new(7.0, 8.0, 9.0));
    }
}

fn collect_bone_alias_targets(program: &Program) -> Vec<SceneMaxBoneAliasTarget> {
    let mut seen = HashSet::new();
    let mut targets = Vec::new();
    collect_bone_alias_targets_from_statements(&program.statements, &mut seen, &mut targets);
    targets
}

fn collect_bone_alias_targets_from_statements(
    statements: &[Statement],
    seen: &mut HashSet<String>,
    targets: &mut Vec<SceneMaxBoneAliasTarget>,
) {
    for statement in statements {
        match statement {
            Statement::Position(position) => {
                collect_bone_alias_target_from_position_value(&position.position, seen, targets);
            }
            Statement::EffekseerPlay(play) => {
                if let Some(position) = &play.position {
                    collect_bone_alias_target_from_position_value(position, seen, targets);
                }
            }
            Statement::MoveTo(move_to) => {
                if let MoveToDestination::Position(position) = &move_to.destination {
                    collect_bone_alias_target_from_position_value(position, seen, targets);
                }
            }
            Statement::LookAt { subject, .. } => {
                collect_bone_alias_target_from_subject(subject, seen, targets);
            }
            Statement::CinematicPlay(play) => {
                if let Some(CinematicLookAt::Entity(subject)) = &play.look_at {
                    collect_bone_alias_target_from_subject(subject, seen, targets);
                }
            }
            Statement::KeyEvent(event) => {
                collect_bone_alias_targets_from_statements(&event.actions, seen, targets);
            }
            Statement::WhenEvent(event) => {
                collect_bone_alias_targets_from_statements(&event.actions, seen, targets);
            }
            Statement::If(statement) => {
                collect_bone_alias_targets_from_statements(&statement.actions, seen, targets);
                collect_bone_alias_targets_from_statements(&statement.else_actions, seen, targets);
            }
            Statement::Guarded { actions, .. }
            | Statement::Repeat { actions, .. }
            | Statement::DoWhile { actions, .. }
            | Statement::LoopContinue { actions, .. }
            | Statement::Async { actions } => {
                collect_bone_alias_targets_from_statements(actions, seen, targets);
            }
            Statement::FunctionDef(function) => {
                collect_bone_alias_targets_from_statements(&function.actions, seen, targets);
            }
            _ => {}
        }
    }
}

fn collect_bone_alias_target_from_position_value(
    position: &PositionValue,
    seen: &mut HashSet<String>,
    targets: &mut Vec<SceneMaxBoneAliasTarget>,
) {
    if let PositionValue::Entity(subject) = position {
        collect_bone_alias_target_from_subject(subject, seen, targets);
    }
}

fn collect_bone_alias_target_from_subject(
    subject: &str,
    seen: &mut HashSet<String>,
    targets: &mut Vec<SceneMaxBoneAliasTarget>,
) {
    let Some((owner, bone)) = parse_quoted_bone_subject(subject) else {
        return;
    };
    let alias = quoted_bone_alias(&owner, &bone);
    if seen.insert(alias.to_ascii_lowercase()) {
        targets.push(SceneMaxBoneAliasTarget { alias, owner, bone });
    }
}

fn parse_quoted_bone_subject(subject: &str) -> Option<(String, String)> {
    let quote_start = subject.find('"')?;
    let owner = subject[..quote_start].trim().trim_end_matches('.').trim();
    let after_start = &subject[quote_start + 1..];
    let quote_end = after_start.find('"')?;
    let bone = after_start[..quote_end].trim();
    (!owner.is_empty() && !bone.is_empty()).then(|| (owner.to_owned(), bone.to_owned()))
}

fn insert_bone_transform_aliases(
    scene_roots: &[(Entity, String)],
    targets: &[SceneMaxBoneAliasTarget],
    children: &Query<&Children>,
    named_nodes: &Query<(&Name, &GlobalTransform)>,
    transforms_by_name: &mut HashMap<String, Transform>,
) -> usize {
    let mut inserted = 0;
    for target in targets {
        let Some((entity, _)) = scene_roots
            .iter()
            .find(|(_, owner_name)| owner_name.eq_ignore_ascii_case(&target.owner))
        else {
            continue;
        };
        let Some(transform) =
            find_descendant_transform_by_name(*entity, &target.bone, children, named_nodes)
        else {
            continue;
        };
        transforms_by_name.insert(target.alias.clone(), transform);
        inserted += 1;
    }
    inserted
}

pub(super) fn quoted_bone_alias(owner_name: &str, bone_name: &str) -> String {
    format!("{owner_name}.\"{bone_name}\"")
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

const OBJECT_POOL_MAX_MEMBERS: usize = 256;
const OBJECT_POOL_RESERVE_LOW_WATERMARK: usize = 4;
const OBJECT_POOL_GROW_BATCH: usize = 8;

pub(super) fn activate_pending_pool_members(
    mut object_pools: ResMut<SceneMaxObjectPools>,
    scene_entities: Query<&SceneMaxEntity>,
) {
    if object_pools
        .pools
        .values()
        .all(|runtime| runtime.pending_available.is_empty())
    {
        return;
    }
    let live_names = scene_entities
        .iter()
        .map(|entity| entity.name.clone())
        .collect::<HashSet<_>>();
    for (pool, runtime) in &mut object_pools.pools {
        let ready = runtime
            .pending_available
            .iter()
            .filter(|member| live_names.contains(*member))
            .cloned()
            .collect::<Vec<_>>();
        if ready.is_empty() {
            continue;
        }
        for member in ready {
            runtime.pending_available.remove(&member);
            if !runtime.in_use.contains(&member)
                && !runtime
                    .available
                    .iter()
                    .any(|available| available == &member)
            {
                runtime.available.push(member.clone());
            }
            write_runtime_diagnostic_line(format!(
                "object pool {pool} reserve ready {member}; available={} pending={}",
                runtime.available.len(),
                runtime.pending_available.len()
            ));
        }
    }
}

pub(super) fn acquire_pool_member(
    pool: &str,
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    collider_bounds: &mut SceneMaxColliderBounds,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
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
) -> Option<String> {
    apply_pool_factory_acquire_side_effects(
        pool,
        vars,
        object_pools,
        functions_by_name,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    );
    let member = if let Some(member) = object_pools
        .pools
        .get_mut(pool)
        .and_then(|runtime| runtime.available.pop())
    {
        member
    } else {
        grow_pool_member(
            pool,
            transforms_by_name,
            vars,
            object_pools,
            functions_by_name,
            guards_by_name,
            runtime_assets,
            collider_bounds,
            commands,
            scene_entities,
            false,
        )?
    };
    let runtime = object_pools.pools.get_mut(pool)?;
    runtime.in_use.insert(member.clone());
    write_runtime_diagnostic_line(format!(
        "object pool {pool} acquire {member}; available={} in_use={}",
        runtime.available.len(),
        runtime.in_use.len()
    ));
    grow_pool_reserve(
        pool,
        transforms_by_name,
        vars,
        object_pools,
        functions_by_name,
        guards_by_name,
        runtime_assets,
        collider_bounds,
        commands,
        scene_entities,
    );
    Some(member)
}

fn grow_pool_reserve(
    pool: &str,
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    collider_bounds: &mut SceneMaxColliderBounds,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
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
    let should_grow = object_pools.pools.get(pool).is_some_and(|runtime| {
        runtime.available.len() <= OBJECT_POOL_RESERVE_LOW_WATERMARK
            && runtime.members.len() < OBJECT_POOL_MAX_MEMBERS
    });
    if !should_grow {
        return;
    }

    for _ in 0..OBJECT_POOL_GROW_BATCH {
        let can_grow = object_pools
            .pools
            .get(pool)
            .is_some_and(|runtime| runtime.members.len() < OBJECT_POOL_MAX_MEMBERS);
        if !can_grow {
            break;
        }
        let Some(member) = grow_pool_member(
            pool,
            transforms_by_name,
            vars,
            object_pools,
            functions_by_name,
            guards_by_name,
            runtime_assets,
            collider_bounds,
            commands,
            scene_entities,
            true,
        ) else {
            break;
        };
        if let Some(runtime) = object_pools.pools.get_mut(pool) {
            runtime.pending_available.insert(member.clone());
            write_runtime_diagnostic_line(format!(
                "object pool {pool} reserve spawning {member}; available={} pending={} in_use={}",
                runtime.available.len(),
                runtime.pending_available.len(),
                runtime.in_use.len()
            ));
        }
    }
}

fn grow_pool_member(
    pool: &str,
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    _functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    runtime_assets: &mut SceneMaxRuntimeAssets,
    collider_bounds: &mut SceneMaxColliderBounds,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
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
    hidden: bool,
) -> Option<String> {
    let (member, resource, mut options, factory) = {
        let runtime = object_pools.pools.get_mut(pool)?;
        let prototype = runtime.prototype.clone()?;
        if runtime.members.len() >= OBJECT_POOL_MAX_MEMBERS {
            write_runtime_diagnostic_line(format!(
                "object pool {pool} exhausted; max members reached ({OBJECT_POOL_MAX_MEMBERS})"
            ));
            tracing::debug!(
                pool,
                max_members = OBJECT_POOL_MAX_MEMBERS,
                "SceneMax object pool reached the runtime member cap"
            );
            return None;
        }
        if !is_primitive_resource(&prototype.resource)
            && runtime_model_resource(&prototype.resource, runtime_assets).is_none()
        {
            write_runtime_diagnostic_line(format!(
                "object pool {pool} exhausted; no preloaded members available for model resource {}",
                prototype.resource
            ));
            tracing::debug!(
                pool,
                resource = %prototype.resource,
                "SceneMax object pool exhausted; skipping runtime placeholder growth for model resource"
            );
            return None;
        }
        let member = format!("__pool_{}_{}", pool, runtime.created_count);
        runtime.created_count += 1;
        runtime.members.insert(member.clone());
        (
            member,
            prototype.resource,
            prototype.options,
            runtime.factory.clone(),
        )
    };
    if hidden {
        options.hidden = true;
    }
    apply_runtime_model_decl(
        &member,
        &resource,
        &options,
        transforms_by_name,
        vars,
        guards_by_name,
        runtime_assets,
        collider_bounds,
        commands,
        scene_entities,
    );
    tracing::info!(pool, factory, member, "grew SceneMax object pool member");
    Some(member)
}

pub(super) fn apply_pool_factory_acquire_side_effects(
    pool: &str,
    vars: &mut SceneMaxVars,
    object_pools: &SceneMaxObjectPools,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
) {
    let Some(factory) = object_pools
        .pools
        .get(pool)
        .map(|runtime| runtime.factory.as_str())
    else {
        return;
    };
    let Some(function) = functions_by_name.get(factory) else {
        return;
    };
    let mut factory_scope = SceneMaxScopeFrame::default();
    for action in &function.actions {
        match action {
            Statement::Assignment(assignment) | Statement::SharedAssignment(assignment) => {
                apply_assignment_scoped(
                    assignment,
                    vars,
                    Some(&mut factory_scope),
                    Some(transforms_by_name),
                    guards_by_name,
                    Some(collider_bounds),
                    false,
                );
            }
            Statement::LocalAssignment(assignment) => {
                apply_assignment_scoped(
                    assignment,
                    vars,
                    Some(&mut factory_scope),
                    Some(transforms_by_name),
                    guards_by_name,
                    Some(collider_bounds),
                    true,
                );
            }
            Statement::Return | Statement::ReturnValue { .. } => break,
            _ => {}
        }
    }
}

pub(super) fn release_pool_action(
    release: &PoolReleaseStatement,
    object_pools: &mut SceneMaxObjectPools,
    scope: Option<&mut SceneMaxScopeFrame>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
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
    write_runtime_diagnostic_line(format!(
        "object pool {pool} release {target}; available={} in_use={}",
        runtime.available.len(),
        runtime.in_use.len()
    ));
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
            .insert((LinearVelocity::ZERO, AngularVelocity::ZERO))
            .remove::<TimedMoves>()
            .remove::<TimedTurn>()
            .remove::<TimedJump>();
        break;
    }
}

pub(super) fn parent_action_tail(actions: &[Statement], index: usize) -> &[Statement] {
    actions.get(index + 1..).unwrap_or_default()
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
        match statement {
            Statement::Assignment(assignment) => {
                apply_assignment(assignment, vars, None, &guards_by_name, None);
            }
            Statement::SharedAssignment(assignment) if !vars.0.contains_key(&assignment.name) => {
                apply_assignment(assignment, vars, None, &guards_by_name, None);
            }
            _ => {}
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

pub(super) fn scenemax_draw_action_from_statement(
    draw: &ChannelDrawStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> SceneMaxUiAction {
    SceneMaxUiAction::Draw(SceneMaxDrawAction {
        channel: draw.channel.clone(),
        resource: draw.resource.clone(),
        clear: draw.clear,
        pos_x: resolve_draw_value(
            draw.pos_x.as_ref(),
            0.0,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
        pos_y: resolve_draw_value(
            draw.pos_y.as_ref(),
            0.0,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
        width: draw.width.as_ref().map(|value| {
            resolve_draw_value(
                Some(value),
                0.0,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .max(0.0)
        }),
        height: draw.height.as_ref().map(|value| {
            resolve_draw_value(
                Some(value),
                0.0,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .max(0.0)
        }),
        frame: resolve_draw_value(
            draw.frame.as_ref(),
            0.0,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        )
        .round()
        .max(0.0) as usize,
        stretch: draw.stretch,
    })
}

pub(super) fn scenemax_print_action_from_statement(
    print: &PrintStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> SceneMaxUiAction {
    let position = print
        .position
        .as_ref()
        .and_then(|position| {
            transforms_by_name.and_then(|transforms_by_name| {
                evaluate_position_value_runtime(
                    position,
                    vars,
                    scope,
                    guards_by_name,
                    transforms_by_name,
                    collider_bounds,
                )
            })
        })
        .unwrap_or(Vec3::ZERO);
    let font_size = print.font_size.as_ref().map(|value| {
        resolve_draw_value(
            Some(value),
            0.0,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        )
        .max(0.0)
    });
    SceneMaxUiAction::Print(SceneMaxPrintAction {
        channel: print.channel.clone(),
        text: resolve_ui_property_value(
            &print.text,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
        pos_x: position.x,
        pos_y: position.y,
        color: print.color.clone(),
        font_size,
        font: print.font.clone(),
        append: print.append,
    })
}

fn resolve_draw_value(
    value: Option<&AssignmentValue>,
    default_value: f32,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> f32 {
    value
        .and_then(|value| {
            resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
        })
        .filter(|value| value.is_finite())
        .unwrap_or(default_value)
}

fn resolved_camera_modifier_overrides(
    overrides: &[(String, AssignmentValue)],
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Vec<(String, f32)> {
    overrides
        .iter()
        .filter_map(|(name, value)| {
            resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .filter(|value| value.is_finite())
            .map(|value| (name.clone(), value))
        })
        .collect()
}

fn resolve_animation_speed_value(
    value: &AssignmentValue,
    fallback: f32,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> f32 {
    resolve_draw_value(
        Some(value),
        fallback,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
    .max(0.001)
}

fn resolve_frame_value(
    value: &AssignmentValue,
    fallback: usize,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> usize {
    resolve_draw_value(
        Some(value),
        fallback as f32,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
    .max(0.0)
    .round() as usize
}

fn resolve_duration_value(
    value: &AssignmentValue,
    fallback: f32,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> f32 {
    resolve_draw_value(
        Some(value),
        fallback,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
}

fn resolved_animation_speed_override(
    animation_speed: &AnimationSpeedStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> AnimationSpeedOverride {
    let speed = resolve_animation_speed_value(
        &animation_speed.speed_value,
        animation_speed.speed,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    );
    let duration_seconds = animation_speed.duration_value.as_ref().map(|value| {
        resolve_duration_value(
            value,
            animation_speed.duration_seconds.unwrap_or_default(),
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        )
    });
    animation_speed_override_resolved(speed, duration_seconds)
}

fn resolved_character_mode(
    character_mode: &CharacterModeStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> CharacterModeStatement {
    let mut character_mode = character_mode.clone();
    if let Some(value) = character_mode.gravity_value.as_ref() {
        character_mode.gravity = Some(resolve_draw_value(
            Some(value),
            character_mode.gravity.unwrap_or_default(),
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ));
    }
    character_mode
}

fn resolved_sprite_animation(
    sprite_play: &SpritePlayStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> SceneMaxSpriteAnimation {
    sprite_animation_from_statement_resolved(
        sprite_play,
        resolve_frame_value(
            &sprite_play.from_frame_value,
            sprite_play.from_frame,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
        resolve_frame_value(
            &sprite_play.to_frame_value,
            sprite_play.to_frame,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
        resolve_duration_value(
            &sprite_play.duration_value,
            sprite_play.duration_seconds,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
    )
}

fn resolved_move_to(
    move_to: &MoveToStatement,
    transform: &Transform,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<TimedMove> {
    let destination = evaluate_move_to_destination_runtime(
        &move_to.destination,
        vars,
        scope,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )?;
    let duration = resolve_duration_value(
        &move_to.duration_value,
        move_to.duration_seconds,
        vars,
        scope,
        guards_by_name,
        Some(transforms_by_name),
        collider_bounds,
    )
    .max(0.001);
    Some(TimedMove {
        remaining_seconds: duration,
        duration_seconds: duration,
        velocity: (destination - transform.translation) / duration,
        final_translation: Some(destination),
        loop_condition: None,
    })
}

fn evaluate_move_to_destination_runtime(
    destination: &MoveToDestination,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<Vec3> {
    match destination {
        MoveToDestination::Position(position) => evaluate_position_value_runtime(
            position,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
        MoveToDestination::EntityForward {
            entity,
            distance,
            distance_value,
        } => {
            let transform = transforms_by_name.get(entity)?;
            let distance = resolve_draw_value(
                Some(distance_value),
                *distance,
                vars,
                scope,
                guards_by_name,
                Some(transforms_by_name),
                collider_bounds,
            );
            Some(transform.translation + horizontal_forward(transform) * distance)
        }
    }
}

fn evaluate_position_value_runtime(
    position: &PositionValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<Vec3> {
    match position {
        PositionValue::Entity(entity) => {
            Some(lookup_subject_transform(entity, transforms_by_name)?.translation)
        }
        PositionValue::Coordinates(values) if values.len() == 3 => Some(Vec3::new(
            evaluate_position_expr_runtime(
                &values[0],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
            evaluate_position_expr_runtime(
                &values[1],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
            evaluate_position_expr_runtime(
                &values[2],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
        )),
        _ => None,
    }
}

fn evaluate_position_expr_runtime(
    value: &PositionExpr,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    match value {
        PositionExpr::Number(value) => Some(*value),
        PositionExpr::Value(value) => resolve_assignment_value_scoped_with_guards(
            value,
            vars,
            scope,
            guards_by_name,
            Some(transforms_by_name),
            collider_bounds,
        ),
        PositionExpr::EntityAxis {
            entity,
            axis,
            offset,
        } => {
            let transform = transforms_by_name.get(entity)?;
            let base = match axis {
                SceneMaxAxis::X => transform.translation.x,
                SceneMaxAxis::Y => transform.translation.y,
                SceneMaxAxis::Z => transform.translation.z,
            };
            Some(base + offset)
        }
    }
}

pub(super) fn resolve_call_args(
    args: &[String],
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Vec<String> {
    args.iter()
        .map(|arg| {
            let trimmed = arg.trim();
            if trimmed.starts_with('"') || trimmed.starts_with('\'') {
                return arg.clone();
            }
            let Ok(Some(value)) = scenemax_parser::parse_runtime_value(trimmed) else {
                return arg.clone();
            };
            resolve_assignment_value_scoped_with_guards(
                &value,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .map(format_scenemax_number)
            .unwrap_or_else(|| arg.clone())
        })
        .collect()
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

pub(super) fn resolve_ui_property_value(
    value: &scenemax_parser::UiPropertyValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> String {
    match value {
        scenemax_parser::UiPropertyValue::Literal(text) => text.clone(),
        scenemax_parser::UiPropertyValue::Expression(value) => {
            resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .map(format_scenemax_number)
            .unwrap_or_else(|| assignment_value_fallback_text(value))
        }
        scenemax_parser::UiPropertyValue::Concatenation(parts) => parts
            .iter()
            .map(|part| {
                resolve_ui_property_value_part(
                    part,
                    vars,
                    scope,
                    guards_by_name,
                    transforms_by_name,
                    collider_bounds,
                )
            })
            .collect::<Vec<_>>()
            .join(""),
    }
}

fn resolve_ui_property_value_part(
    part: &scenemax_parser::UiPropertyValuePart,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> String {
    match part {
        scenemax_parser::UiPropertyValuePart::Literal(text) => text.clone(),
        scenemax_parser::UiPropertyValuePart::Expression(value) => {
            resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )
            .map(format_scenemax_number)
            .unwrap_or_else(|| assignment_value_fallback_text(value))
        }
    }
}

pub(super) fn assignment_value_fallback_text(value: &AssignmentValue) -> String {
    match value {
        AssignmentValue::Number(value) => format_scenemax_number(*value),
        AssignmentValue::Symbol(name) => name.clone(),
        _ => "null".to_owned(),
    }
}

pub(super) fn resolve_shader_name(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> String {
    match value {
        AssignmentValue::Symbol(name) => name.clone(),
        AssignmentValue::Number(value) => format_scenemax_number(*value),
        _ => resolve_assignment_value_scoped_with_guards(
            value,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        )
        .map(format_scenemax_number)
        .unwrap_or_else(|| assignment_value_fallback_text(value)),
    }
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
                collider_bounds,
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
                active_physics_contact_matches(source_name, target_name, physics_contacts, None)
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
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let target_candidates = collision_reference_candidates_with_alias(target, object_pools);
    sources.iter().any(|source| {
        let source_candidates = collision_reference_candidates_with_alias(source, object_pools);
        source_candidates.iter().any(|source_name| {
            if collision_reference_hidden(source_name, collider_bounds) {
                return false;
            }
            target_candidates.iter().any(|target_name| {
                if collision_reference_hidden(target_name, collider_bounds) {
                    return false;
                }
                active_physics_contact_matches(
                    source_name,
                    target_name,
                    physics_contacts,
                    collider_bounds,
                ) && transforms_by_name.is_none_or(|transforms_by_name| {
                    attached_collider_owner_distance_allows(
                        source_name,
                        target_name,
                        transforms_by_name,
                        collider_bounds,
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
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    if collision_reference_hidden(source, collider_bounds)
        || collision_reference_hidden(target, collider_bounds)
    {
        return false;
    }
    if physics_contacts
        .active_pairs
        .contains(&normalized_collision_pair(source, target))
    {
        return true;
    }

    if !is_owner_level_collision_reference(source, collider_bounds)
        && !is_owner_level_collision_reference(target, collider_bounds)
    {
        return false;
    }

    let expected_owner_pair = normalized_collision_pair(
        &collision_owner_with_bounds(source, collider_bounds),
        &collision_owner_with_bounds(target, collider_bounds),
    );
    physics_contacts.active_pairs.iter().any(|(left, right)| {
        normalized_collision_pair(
            &collision_owner_with_bounds(left, collider_bounds),
            &collision_owner_with_bounds(right, collider_bounds),
        ) == expected_owner_pair
    })
}

pub(super) fn is_owner_level_collision_reference(
    reference: &str,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let normalized = normalize_collision_reference(reference);
    collision_owner_with_bounds(normalized, collider_bounds) == normalized
}

pub(super) fn normalized_collision_pair(left: &str, right: &str) -> (String, String) {
    if left <= right {
        (left.to_owned(), right.to_owned())
    } else {
        (right.to_owned(), left.to_owned())
    }
}
