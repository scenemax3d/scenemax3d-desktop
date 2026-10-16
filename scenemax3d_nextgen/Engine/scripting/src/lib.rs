use std::collections::HashMap;

use scenemax_parser::{
    AnimationControllerEventStatement, AnimationControllerValue, AnimationSpeedStatement,
    AnimationStatement, AssignmentValue, AttachStatement, CameraAttachStatement,
    CharacterJumpStatement, CharacterModeStatement, Condition, LoggerMessage, LoggerStatement,
    PoolReleaseStatement, PositionExpr, PositionStatement, PositionValue, Program, Statement,
    ThrowMotionAsset, ThrowMotionTarget, ThrowMotionValue, UiTargetPath,
};

#[derive(Debug, Clone, PartialEq)]
pub struct FunctionRuntime {
    pub params: Vec<String>,
    pub guard: Option<Condition>,
    pub guard_recheck: bool,
    pub actions: Vec<Statement>,
}

pub fn collect_animations_by_target(program: &Program) -> HashMap<String, AnimationStatement> {
    let mut animations = HashMap::new();
    for statement in &program.statements {
        if initial_state_scan_boundary(statement) {
            break;
        }
        if let Statement::Animate(animation) = statement {
            animations.insert(animation.target.clone(), animation.clone());
        }
    }
    animations
}

pub fn collect_visibility_by_target(program: &Program) -> HashMap<String, bool> {
    let mut visibility = HashMap::new();
    for statement in &program.statements {
        if initial_state_scan_boundary(statement) {
            break;
        }
        if let Statement::Visibility { target, visible } = statement {
            visibility.insert(target.clone(), *visible);
        }
    }
    visibility
}

pub fn initial_state_scan_boundary(statement: &Statement) -> bool {
    matches!(
        statement,
        Statement::ScopedBlock { .. }
            | Statement::Wait { .. }
            | Statement::WaitValue { .. }
            | Statement::WaitUntil { .. }
            | Statement::WaitForKey { .. }
            | Statement::SwitchTo { .. }
            | Statement::Animate(AnimationStatement {
                blocking: true,
                looped: false,
                ..
            })
            | Statement::Turn(scenemax_parser::TurnStatement {
                async_run: false,
                loop_condition: None,
                ..
            })
            | Statement::Move(scenemax_parser::MoveStatement {
                async_run: false,
                loop_condition: None,
                ..
            })
            | Statement::MoveTo(scenemax_parser::MoveToStatement {
                async_run: false,
                ..
            })
            | Statement::CameraMove(scenemax_parser::CameraMoveStatement {
                async_run: false,
                ..
            })
            | Statement::CharacterJump(scenemax_parser::CharacterJumpStatement {
                async_run: false,
                ..
            })
            | Statement::CinematicPlay(scenemax_parser::CinematicPlayStatement {
                async_run: false,
                ..
            })
    )
}

pub fn collect_turn_by_target(
    program: &Program,
) -> HashMap<String, scenemax_parser::TurnStatement> {
    let mut turns = HashMap::new();
    for statement in &program.statements {
        if initial_state_scan_boundary(statement) {
            break;
        }
        if let Statement::Turn(turn) = statement {
            turns.insert(turn.target.clone(), turn.clone());
        }
    }
    turns
}

pub fn collect_attaches_by_target(program: &Program) -> HashMap<String, AttachStatement> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::Attach(attach) => Some((attach.target.clone(), attach.clone())),
            _ => None,
        })
        .collect()
}

pub fn collect_functions_by_name(program: &Program) -> HashMap<String, FunctionRuntime> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::FunctionDef(function) => Some((
                function.name.clone(),
                FunctionRuntime {
                    params: function.params.clone(),
                    guard: function.guard.clone(),
                    guard_recheck: function.guard_recheck,
                    actions: function.actions.clone(),
                },
            )),
            _ => None,
        })
        .collect()
}

pub fn collect_guards_by_name(program: &Program) -> HashMap<String, Condition> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::GuardDef { name, condition } => Some((name.clone(), condition.clone())),
            _ => None,
        })
        .collect()
}

pub fn instantiate_function_actions(function: &FunctionRuntime, args: &[String]) -> Vec<Statement> {
    let actions = if function.params.is_empty() || args.is_empty() {
        function.actions.clone()
    } else {
        let bindings = function
            .params
            .iter()
            .zip(args.iter())
            .filter(|(param, arg)| param != arg)
            .map(|(param, arg)| (param.clone(), arg.clone()))
            .collect::<HashMap<_, _>>();
        function
            .actions
            .iter()
            .map(|action| substitute_statement(action, &bindings))
            .collect()
    };

    if function.guard_recheck {
        if let Some(condition) = function.guard.as_ref() {
            return wrap_actions_with_rechecked_guard(
                actions,
                substitute_function_condition(function, args, condition),
            );
        }
    }

    actions
}

fn wrap_actions_with_rechecked_guard(
    actions: Vec<Statement>,
    condition: Condition,
) -> Vec<Statement> {
    actions
        .into_iter()
        .map(|mut action| {
            // Blocks are expanded or scheduled independently by the runtime. Carry
            // the function guard into their bodies so resumed commands recheck it.
            match &mut action {
                Statement::ScopedBlock { actions }
                | Statement::Async { actions }
                | Statement::Guarded { actions, .. }
                | Statement::Repeat { actions, .. }
                | Statement::DoWhile { actions, .. }
            | Statement::ForEachModel { actions, .. }
                | Statement::LoopContinue { actions, .. } => {
                    *actions = wrap_actions_with_rechecked_guard(
                        std::mem::take(actions),
                        condition.clone(),
                    );
                }
                Statement::If(branch) => {
                    branch.actions = wrap_actions_with_rechecked_guard(
                        std::mem::take(&mut branch.actions),
                        condition.clone(),
                    );
                    branch.else_actions = wrap_actions_with_rechecked_guard(
                        std::mem::take(&mut branch.else_actions),
                        condition.clone(),
                    );
                }
                _ => {}
            }
            Statement::Guarded {
                condition: condition.clone(),
                actions: vec![action],
            }
        })
        .collect()
}

pub fn substitute_function_condition(
    function: &FunctionRuntime,
    args: &[String],
    condition: &Condition,
) -> Condition {
    if function.params.is_empty() || args.is_empty() {
        return condition.clone();
    }
    let bindings = function
        .params
        .iter()
        .zip(args.iter())
        .map(|(param, arg)| (param.clone(), arg.clone()))
        .collect::<HashMap<_, _>>();
    substitute_condition(condition, &bindings)
}

pub fn substitute_statement(
    statement: &Statement,
    bindings: &HashMap<String, String>,
) -> Statement {
    match statement {
        Statement::ModelDecl { name, resource, options } if options.label.is_some() => {
            let mut options = options.clone();
            if let Some(label) = options.label.as_mut() {
                label.text = label.text.as_ref().map(|value| substitute_assignment_value(value, bindings));
                label.transparency = label.transparency.as_ref().map(|value| substitute_assignment_value(value, bindings));
            }
            Statement::ModelDecl { name: substitute_path(name, bindings), resource: resource.clone(), options }
        }
        Statement::Animate(animation) => Statement::Animate(AnimationStatement {
 rewind: animation.rewind.clone(),
            frame_range: animation.frame_range,
            frame_range_name: animation.frame_range_name.clone(),
            target: substitute_path(&animation.target, bindings),
            clip: animation.clip.clone(),
            speed: animation.speed,
            speed_value: substitute_assignment_value(&animation.speed_value, bindings),
            looped: animation.looped,
            blocking: animation.blocking,
        }),
        Statement::SpritePlay(sprite_play) => {
            Statement::SpritePlay(scenemax_parser::SpritePlayStatement {
                target: substitute_path(&sprite_play.target, bindings),
                from_frame: sprite_play.from_frame,
                from_frame_value: substitute_assignment_value(
                    &sprite_play.from_frame_value,
                    bindings,
                ),
                to_frame: sprite_play.to_frame,
                to_frame_value: substitute_assignment_value(&sprite_play.to_frame_value, bindings),
                duration_seconds: sprite_play.duration_seconds,
                duration_value: substitute_assignment_value(&sprite_play.duration_value, bindings),
                looped: sprite_play.looped,
                repeat_count: sprite_play.repeat_count,
                playback_seconds: sprite_play.playback_seconds.as_ref().map(|v| substitute_assignment_value(v, bindings)),
            })
        }
        Statement::AnimationSpeed(animation_speed) => {
            Statement::AnimationSpeed(AnimationSpeedStatement {
                target: substitute_path(&animation_speed.target, bindings),
                speed: animation_speed.speed,
                speed_value: substitute_assignment_value(&animation_speed.speed_value, bindings),
                duration_seconds: animation_speed.duration_seconds,
                duration_value: animation_speed
                    .duration_value
                    .as_ref()
                    .map(|value| substitute_assignment_value(value, bindings)),
                condition: animation_speed
                    .condition
                    .as_ref()
                    .map(|condition| substitute_condition(condition, bindings)),
            })
        }
        Statement::LabelText { target, value } => Statement::LabelText {
            target: substitute_path(target, bindings),
            value: substitute_assignment_value(value, bindings),
        },
        Statement::Visibility { target, visible } => Statement::Visibility {
            target: substitute_path(target, bindings),
            visible: *visible,
        },
        Statement::LookAt { target, subject } => Statement::LookAt {
            target: substitute_path(target, bindings),
            subject: substitute_reference(subject, bindings),
        },
        Statement::Position(position) => Statement::Position(PositionStatement {
            target: substitute_path(&position.target, bindings),
            position: substitute_position_value(&position.position, bindings),
        }),
        Statement::RotationReset { target, rotation } => Statement::RotationReset {
            target: substitute_path(target, bindings),
            rotation: substitute_position_value(rotation, bindings),
        },
        Statement::Turn(turn) => Statement::Turn(scenemax_parser::TurnStatement {
            target: substitute_path(&turn.target, bindings),
            axis: turn.axis,
            absolute: turn.absolute,
            degrees: turn.degrees,
            degrees_value: substitute_assignment_value(&turn.degrees_value, bindings),
            duration_seconds: turn.duration_seconds,
            duration_value: substitute_assignment_value(&turn.duration_value, bindings),
            easing: turn.easing.as_ref().map(|ease| {
                let mut ease = ease.clone();
                ease.params = ease.params.iter().map(|v| substitute_assignment_value(v, bindings)).collect();
                ease
            }),
            loop_condition: turn
                .loop_condition
                .as_ref()
                .map(|condition| substitute_condition(condition, bindings)),
            async_run: turn.async_run,
        }),
        Statement::Move(movement) => Statement::Move(scenemax_parser::MoveStatement {
            target: substitute_path(&movement.target, bindings),
            direction: movement.direction,
            distance: movement.distance,
            distance_value: substitute_assignment_value(&movement.distance_value, bindings),
            duration_seconds: movement.duration_seconds,
            duration_value: substitute_assignment_value(&movement.duration_value, bindings),
            easing: movement.easing.as_ref().map(|ease| {
                let mut ease = ease.clone();
                ease.params = ease.params.iter().map(|v| substitute_assignment_value(v, bindings)).collect();
                ease
            }),
            loop_condition: movement
                .loop_condition
                .as_ref()
                .map(|condition| substitute_condition(condition, bindings)),
            async_run: movement.async_run,
        }),
        Statement::MoveTo(move_to) => Statement::MoveTo(scenemax_parser::MoveToStatement {
            target: substitute_path(&move_to.target, bindings),
            destination: substitute_move_to_destination(&move_to.destination, bindings),
            duration_seconds: move_to.duration_seconds,
            duration_value: substitute_assignment_value(&move_to.duration_value, bindings),
            easing: move_to.easing.as_ref().map(|ease| {
                let mut ease = ease.clone();
                ease.params = ease.params.iter().map(|v| substitute_assignment_value(v, bindings)).collect();
                ease
            }),
            async_run: move_to.async_run,
        }),
        Statement::CameraMove(camera_move) => {
            Statement::CameraMove(scenemax_parser::CameraMoveStatement {
                axis: camera_move.axis,
                distance: camera_move.distance,
                distance_value: substitute_assignment_value(&camera_move.distance_value, bindings),
                duration_seconds: camera_move.duration_seconds,
                duration_value: substitute_assignment_value(&camera_move.duration_value, bindings),
                async_run: camera_move.async_run,
            })
        }
        Statement::CameraChase { target } => Statement::CameraChase {
            target: substitute_path(target, bindings),
        },
        Statement::CameraModifierApply(apply) => {
            Statement::CameraModifierApply(scenemax_parser::CameraModifierApplyStatement {
                target: substitute_path(&apply.target, bindings),
                modifier: substitute_path(&apply.modifier, bindings),
                overrides: apply
                    .overrides
                    .iter()
                    .map(|(name, value)| {
                        (name.clone(), substitute_assignment_value(value, bindings))
                    })
                    .collect(),
            })
        }
        Statement::Attach(attach) => Statement::Attach(AttachStatement {
            target: substitute_path(&attach.target, bindings),
            subject: substitute_path(&attach.subject, bindings),
            offset: attach.offset,
            offset_value: attach.offset_value.as_ref().map(|v| substitute_position_value(v, bindings)),
            restore_destination_node: attach.restore_destination_node,
        }),
        Statement::CameraAttach(attach) => Statement::CameraAttach(CameraAttachStatement {
            target: substitute_path(&attach.target, bindings),
            offset: attach.offset,
        }),
        Statement::CameraAttachStop => Statement::CameraAttachStop,
        Statement::Logger(logger) => Statement::Logger(LoggerStatement {
            level: logger.level,
            message: substitute_logger_message(&logger.message, bindings),
        }),
        Statement::CharacterMode(character_mode) => {
            Statement::CharacterMode(CharacterModeStatement {
                target: substitute_path(&character_mode.target, bindings),
                gravity: character_mode.gravity,
                gravity_value: character_mode
                    .gravity_value
                    .as_ref()
                    .map(|value| substitute_assignment_value(value, bindings)),
            })
        }
        Statement::ClearCharacterMode { target } => Statement::ClearCharacterMode {
            target: substitute_path(target, bindings),
        },
        Statement::CharacterIgnore(ignore) => {
            Statement::CharacterIgnore(scenemax_parser::CharacterIgnoreStatement {
                target: substitute_path(&ignore.target, bindings),
                ignored: substitute_path(&ignore.ignored, bindings),
            })
        }
        Statement::CharacterJump(jump) => Statement::CharacterJump(CharacterJumpStatement {
            target: substitute_path(&jump.target, bindings),
            speed: jump.speed,
            speed_value: substitute_assignment_value(&jump.speed_value, bindings),
            async_run: jump.async_run,
        }),
        Statement::PhysicsMotion { target, action } => Statement::PhysicsMotion {
            target: substitute_path(target, bindings), action: action.clone(),
        },
        Statement::PhysicsImpulse(impulse) => {
            Statement::PhysicsImpulse(scenemax_parser::PhysicsImpulseStatement {
                target: substitute_path(&impulse.target, bindings),
                direction: impulse.direction,
                strength: impulse.strength,
                strength_value: substitute_assignment_value(&impulse.strength_value, bindings),
            })
        }
        Statement::PhysicsStop { target } => Statement::PhysicsStop {
            target: substitute_path(target, bindings),
        },
        Statement::PhysicsThrowAt(throw_at) => {
            Statement::PhysicsThrowAt(scenemax_parser::PhysicsThrowAtStatement {
                target: substitute_path(&throw_at.target, bindings),
                subject: substitute_path(&throw_at.subject, bindings),
                power: substitute_assignment_value(&throw_at.power, bindings),
            })
        }
        Statement::PoolRelease(release) => Statement::PoolRelease(PoolReleaseStatement {
            pool: substitute_path(&release.pool, bindings),
            target: substitute_path(&release.target, bindings),
        }),
        Statement::Delete { target } => Statement::Delete {
            target: substitute_path(target, bindings),
        },
        Statement::UiLoad { name } => Statement::UiLoad {
            name: substitute_reference(name, bindings),
        },
        Statement::ChannelDraw(draw) => {
            Statement::ChannelDraw(scenemax_parser::ChannelDrawStatement {
                channel: substitute_path(&draw.channel, bindings),
                resource: substitute_reference(&draw.resource, bindings),
                clear: draw.clear,
                pos_x: draw
                    .pos_x
                    .as_ref()
                    .map(|value| substitute_assignment_value(value, bindings)),
                pos_y: draw
                    .pos_y
                    .as_ref()
                    .map(|value| substitute_assignment_value(value, bindings)),
                width: draw
                    .width
                    .as_ref()
                    .map(|value| substitute_assignment_value(value, bindings)),
                height: draw
                    .height
                    .as_ref()
                    .map(|value| substitute_assignment_value(value, bindings)),
                frame: draw
                    .frame
                    .as_ref()
                    .map(|value| substitute_assignment_value(value, bindings)),
                stretch: draw.stretch,
            })
        }
        Statement::UiShowHide(show_hide) => {
            Statement::UiShowHide(scenemax_parser::UiShowHideStatement {
                target: substitute_ui_target_path(&show_hide.target, bindings),
                visible: show_hide.visible,
            })
        }
        Statement::UiMessage(message) => {
            Statement::UiMessage(scenemax_parser::UiMessageStatement {
                target: substitute_ui_target_path(&message.target, bindings),
                text: substitute_reference(&message.text, bindings),
                effects: message.effects.clone(),
                duration_seconds: message.duration_seconds,
                async_run: message.async_run,
            })
        }
        Statement::UiEase(ease) => Statement::UiEase(scenemax_parser::UiEaseStatement {
            target: substitute_ui_target_path(&ease.target, bindings),
            easing: ease.easing.clone(),
            direction: ease.direction,
            duration_seconds: ease.duration_seconds,
            async_run: ease.async_run,
        }),
        Statement::UiSetProperty(property) => {
            Statement::UiSetProperty(scenemax_parser::UiSetPropertyStatement {
                target: substitute_ui_target_path(&property.target, bindings),
                property: property.property.clone(),
                value: substitute_ui_property_value(&property.value, bindings),
            })
        }
        Statement::If(statement) => Statement::If(scenemax_parser::IfStatement {
            condition: substitute_condition(&statement.condition, bindings),
            actions: substitute_statements(&statement.actions, bindings),
            else_actions: substitute_statements(&statement.else_actions, bindings),
        }),
        Statement::Guarded { condition, actions } => Statement::Guarded {
            condition: substitute_condition(condition, bindings),
            actions: substitute_statements(actions, bindings),
        },
        Statement::Repeat { times, actions } => Statement::Repeat {
            times: *times,
            actions: substitute_statements(actions, bindings),
        },
        Statement::DoWhile { condition, actions } => Statement::DoWhile {
            condition: substitute_condition(condition, bindings),
            actions: substitute_statements(actions, bindings),
        },
        Statement::ForEachModel { variable, actions } => {
            let mut inner = bindings.clone();
            inner.remove(variable);
            Statement::ForEachModel { variable: variable.clone(), actions: substitute_statements(actions, &inner) }
        },
        Statement::WaitValue { value } => Statement::WaitValue {
            value: substitute_assignment_value(value, bindings),
        },
        Statement::WaitUntil { condition } => Statement::WaitUntil {
            condition: substitute_condition(condition, bindings),
        },
        Statement::ReturnValue { value } => Statement::ReturnValue {
            value: substitute_assignment_value(value, bindings),
        },
        Statement::LoopContinue { condition, actions } => Statement::LoopContinue {
            condition: substitute_condition(condition, bindings),
            actions: substitute_statements(actions, bindings),
        },
        Statement::ScopedBlock { actions } => Statement::ScopedBlock {
            actions: substitute_statements(actions, bindings),
        },
        Statement::Async { actions } => Statement::Async {
            actions: substitute_statements(actions, bindings),
        },
        Statement::Assignment(assignment)
        | Statement::SharedAssignment(assignment)
        | Statement::LocalAssignment(assignment) => {
            let assignment = scenemax_parser::AssignmentStatement {
                name: substitute_path(&assignment.name, bindings),
                value: substitute_assignment_value(&assignment.value, bindings),
            };
            if matches!(statement, Statement::LocalAssignment(_)) {
                Statement::LocalAssignment(assignment)
            } else if matches!(statement, Statement::SharedAssignment(_)) {
                Statement::SharedAssignment(assignment)
            } else {
                Statement::Assignment(assignment)
            }
        }
        Statement::AnimationControllerAction(action) => Statement::AnimationControllerAction(
            scenemax_parser::AnimationControllerActionStatement { rewind: action.rewind.as_ref().map(|r| scenemax_parser::animation_rewind::Request {
                percent: substitute_assignment_value(&r.percent, bindings), duration: substitute_assignment_value(&r.duration, bindings),
                easing: r.easing.as_ref().map(|e| { let mut e=e.clone(); e.params=e.params.iter().map(|v| substitute_assignment_value(v,bindings)).collect(); e }),
            }),
                controller: substitute_path(&action.controller, bindings),
                action: action.action,
            },
        ),
        Statement::AnimationControllerEvent(event) => {
            Statement::AnimationControllerEvent(AnimationControllerEventStatement {
                controller: substitute_path(&event.controller, bindings),
                animation: event.animation.clone(),
                percent: substitute_assignment_value(&event.percent, bindings),
                actions: substitute_statements(&event.actions, bindings),
            })
        }
        Statement::Network(command) => {
            use scenemax_parser::network::NetworkStatement as N;
            let expr = |s: &str| substitute_reference(s, bindings);
            Statement::Network(match command {
                N::EntityData {target,field,value} => N::EntityData {target:substitute_path(target,bindings),field:field.clone(),value:expr(value)},
                N::Variable {name,value} => N::Variable {name:substitute_path(name,bindings),value:expr(value)},
                N::Broadcast {event,message} => N::Broadcast {event:expr(event),message:message.as_deref().map(expr)},
                N::Send {recipient,event,message} => N::Send {recipient:recipient.as_deref().map(|s|substitute_path(s,bindings)),event:expr(event),message:message.as_deref().map(expr)},
                N::JoinSession {value} => N::JoinSession {value:expr(value)},
                N::On {handler_id,event,argument,actions} => N::On {handler_id:*handler_id,event:expr(event),argument:argument.as_deref().map(expr),actions:substitute_statements(actions,bindings)},
            })
        }
        Statement::Ik(command) => {
            let mut command = command.clone();
            command.owner = substitute_path(&command.owner, bindings);
            command.target = command
                .target
                .as_ref()
                .map(|t| substitute_path(t, bindings));
            command.weight = command
                .weight
                .as_ref()
                .map(|v| substitute_assignment_value(v, bindings));
            command.blend = command
                .blend
                .as_ref()
                .map(|v| substitute_assignment_value(v, bindings));
            if let scenemax_parser::ik::IkAction::Apply(v) = &command.action {
                command.action =
                    scenemax_parser::ik::IkAction::Apply(substitute_assignment_value(v, bindings));
            }
            Statement::Ik(command)
        }
        Statement::SetMaterial(material) => {
            Statement::SetMaterial(scenemax_parser::SetMaterialStatement {
                target: substitute_path(&material.target, bindings),
                material: substitute_assignment_value(&material.material, bindings),
            })
        }
        Statement::SetShader(shader) => Statement::SetShader(scenemax_parser::SetShaderStatement {
            target: substitute_path(&shader.target, bindings),
            shader: substitute_assignment_value(&shader.shader, bindings),
        }),
        Statement::SetEnvironmentShader { shader } => Statement::SetEnvironmentShader {
            shader: substitute_assignment_value(shader, bindings),
        },
        Statement::SetSkybox { skybox } => Statement::SetSkybox {
            skybox: substitute_assignment_value(skybox, bindings),
        },
        Statement::RunFunction { name, args } => Statement::RunFunction {
            name: name.clone(),
            args: args
                .iter()
                .map(|arg| substitute_reference(arg, bindings))
                .collect(),
        },
        Statement::RunEvery {
            name,
            args,
            interval_seconds,
            interval_value,
        } => Statement::RunEvery {
            name: name.clone(),
            args: args
                .iter()
                .map(|arg| substitute_reference(arg, bindings))
                .collect(),
            interval_seconds: *interval_seconds,
            interval_value: substitute_assignment_value(interval_value, bindings),
        },
        statement => statement.clone(),
    }
}

pub fn substitute_logger_message(
    message: &LoggerMessage,
    bindings: &HashMap<String, String>,
) -> LoggerMessage {
    match message {
        LoggerMessage::Text(text) => LoggerMessage::Text(text.clone()),
        LoggerMessage::Value(AssignmentValue::Symbol(name)) => bindings
            .get(name)
            .and_then(|arg| {
                arg.parse::<f32>()
                    .ok()
                    .map(|value| LoggerMessage::Value(AssignmentValue::Number(value)))
                    .or_else(|| Some(LoggerMessage::Text(serde_json::from_str::<String>(arg).unwrap_or_else(|_| arg.clone()))))
            })
            .unwrap_or_else(|| {
                LoggerMessage::Value(substitute_assignment_value(
                    &AssignmentValue::Symbol(name.clone()),
                    bindings,
                ))
            }),
        LoggerMessage::Value(value) => {
            LoggerMessage::Value(substitute_assignment_value(value, bindings))
        }
    }
}

pub fn substitute_ui_target_path(
    path: &UiTargetPath,
    bindings: &HashMap<String, String>,
) -> UiTargetPath {
    UiTargetPath {
        ui_name: path
            .ui_name
            .as_ref()
            .map(|name| substitute_reference(name, bindings)),
        layer: substitute_path(&path.layer, bindings),
        widget_path: path
            .widget_path
            .iter()
            .map(|part| substitute_path(part, bindings))
            .collect(),
    }
}

pub fn substitute_statements(
    statements: &[Statement],
    bindings: &HashMap<String, String>,
) -> Vec<Statement> {
    statements
        .iter()
        .map(|statement| substitute_statement(statement, bindings))
        .collect()
}

pub fn substitute_position_value(
    position: &PositionValue,
    bindings: &HashMap<String, String>,
) -> PositionValue {
    match position {
        PositionValue::Entity(entity) => PositionValue::Entity(substitute_path(entity, bindings)),
        PositionValue::Coordinates(values) => PositionValue::Coordinates(
            values
                .iter()
                .map(|value| match value {
                    PositionExpr::Number(value) => PositionExpr::Number(*value),
                    PositionExpr::Value(value) => {
                        PositionExpr::Value(substitute_assignment_value(value, bindings))
                    }
                    PositionExpr::EntityAxis {
                        entity,
                        axis,
                        offset,
                    } => PositionExpr::EntityAxis {
                        entity: substitute_path(entity, bindings),
                        axis: *axis,
                        offset: *offset,
                    },
                })
                .collect(),
        ),
    }
}

pub fn substitute_move_to_destination(
    destination: &scenemax_parser::MoveToDestination,
    bindings: &HashMap<String, String>,
) -> scenemax_parser::MoveToDestination {
    match destination {
        scenemax_parser::MoveToDestination::Delta(position) => scenemax_parser::MoveToDestination::Delta(substitute_position_value(position, bindings)),
        scenemax_parser::MoveToDestination::Position(position) => {
            scenemax_parser::MoveToDestination::Position(substitute_position_value(
                position, bindings,
            ))
        }
        scenemax_parser::MoveToDestination::EntityForward {
            entity,
            distance,
            distance_value,
        } => scenemax_parser::MoveToDestination::EntityForward {
            entity: substitute_path(entity, bindings),
            distance: *distance,
            distance_value: substitute_assignment_value(distance_value, bindings),
        },
    }
}

pub fn substitute_condition(
    condition: &Condition,
    bindings: &HashMap<String, String>,
) -> Condition {
    match condition {
        Condition::EqualsNumber { name, value } => Condition::EqualsNumber {
            name: substitute_path(name, bindings),
            value: *value,
        },
        Condition::NotEqualsNumber { name, value } => Condition::NotEqualsNumber {
            name: substitute_path(name, bindings),
            value: *value,
        },
        Condition::EqualsSymbol { name, value } => Condition::EqualsSymbol {
            name: substitute_path(name, bindings),
            value: substitute_path(value, bindings),
        },
        Condition::NotEqualsSymbol { name, value } => Condition::NotEqualsSymbol {
            name: substitute_path(name, bindings),
            value: substitute_path(value, bindings),
        },
        Condition::EqualsValue { left, right } => Condition::EqualsValue {
            left: substitute_assignment_value(left, bindings),
            right: substitute_assignment_value(right, bindings),
        },
        Condition::NotEqualsValue { left, right } => Condition::NotEqualsValue {
            left: substitute_assignment_value(left, bindings),
            right: substitute_assignment_value(right, bindings),
        },
        Condition::Compare {
            name,
            operator,
            value,
        } => Condition::Compare {
            name: substitute_path(name, bindings),
            operator: *operator,
            value: substitute_assignment_value(value, bindings),
        },
        Condition::CompareValue {
            left,
            operator,
            right,
        } => Condition::CompareValue {
            left: substitute_assignment_value(left, bindings),
            operator: *operator,
            right: substitute_assignment_value(right, bindings),
        },
        Condition::Truthy { name } => Condition::Truthy {
            name: substitute_path(name, bindings),
        },
        Condition::Collision { sources, target } => Condition::Collision {
            sources: sources
                .iter()
                .map(|source| substitute_path(source, bindings))
                .collect(),
            target: substitute_path(target, bindings),
        },
        Condition::Boolean(value) => Condition::Boolean(*value),
        Condition::Not(condition) => {
            Condition::Not(Box::new(substitute_condition(condition, bindings)))
        }
        Condition::Alias(name) => Condition::Alias(name.clone()),
        Condition::And(conditions) => Condition::And(
            conditions
                .iter()
                .map(|condition| substitute_condition(condition, bindings))
                .collect(),
        ),
        Condition::Or(conditions) => Condition::Or(
            conditions
                .iter()
                .map(|condition| substitute_condition(condition, bindings))
                .collect(),
        ),
    }
}

pub fn substitute_assignment_value(
    value: &AssignmentValue,
    bindings: &HashMap<String, String>,
) -> AssignmentValue {
    match value {
        AssignmentValue::RuntimeValue(value) => AssignmentValue::RuntimeValue(value.clone()),
        AssignmentValue::Literal(value) => AssignmentValue::Literal(value.clone()),
        AssignmentValue::ArrayEdit { source, operation } => {
            use scenemax_parser::ArrayOperation;
            AssignmentValue::ArrayEdit {
                source: Box::new(substitute_assignment_value(source, bindings)),
                operation: match operation {
                    ArrayOperation::Push(v) => ArrayOperation::Push(Box::new(substitute_assignment_value(v, bindings))),
                    ArrayOperation::Reset(v) => ArrayOperation::Reset(Box::new(substitute_assignment_value(v, bindings))),
                    other => other.clone(),
                },
            }
        }
        AssignmentValue::Json(value) => AssignmentValue::Json(Box::new(substitute_assignment_value(value,bindings))),
        AssignmentValue::Array(values) => AssignmentValue::Array(values.iter().map(|v| substitute_assignment_value(v, bindings)).collect()),
        AssignmentValue::Access {value,index} => AssignmentValue::Access { value: Box::new(substitute_assignment_value(value,bindings)), index: Box::new(substitute_assignment_value(index,bindings)) },
        AssignmentValue::Number(value) => AssignmentValue::Number(*value),
        AssignmentValue::CameraModifier(value) => AssignmentValue::CameraModifier(value.clone()),
        AssignmentValue::Condition(condition) => {
            AssignmentValue::Condition(Box::new(substitute_condition(condition, bindings)))
        }
        AssignmentValue::ThrowMotion(value) => {
            AssignmentValue::ThrowMotion(Box::new(substitute_throw_motion_value(value, bindings)))
        }
        AssignmentValue::AnimationController(value) => {
            AssignmentValue::AnimationController(AnimationControllerValue {
                subsequent: value.subsequent.iter().map(|animation| match substitute_statement(&Statement::Animate(animation.clone()), bindings) {
                    Statement::Animate(animation) => animation,
                    _ => unreachable!(),
                }).collect(),
                animation: match substitute_statement(&Statement::Animate((*value.animation).clone()), bindings) {
                    Statement::Animate(animation) => Box::new(animation),
                    _ => unreachable!(),
                },
                target: substitute_path(&value.target, bindings),
                clip: value.clip.clone(),
            })
        }
        AssignmentValue::Call { name, args } => AssignmentValue::Call {
            name: name.clone(),
            args: args.iter().map(|arg| substitute_assignment_value(arg, bindings)).collect(),
        },
        AssignmentValue::RandomInt { max } => AssignmentValue::RandomInt {
            max: Box::new(substitute_assignment_value(max, bindings)),
        },
        AssignmentValue::Round { value } => AssignmentValue::Round {
            value: Box::new(substitute_assignment_value(value, bindings)),
        },
        AssignmentValue::Distance { left, right } => AssignmentValue::Distance {
            left: substitute_path(left, bindings),
            right: substitute_path(right, bindings),
        },
        AssignmentValue::PoolAcquire { pool } => AssignmentValue::PoolAcquire {
            pool: substitute_path(pool, bindings),
        },
        AssignmentValue::Symbol(name) => bindings
            .get(name)
            .and_then(|arg| scenemax_parser::parse_assignment_value(arg).ok().flatten())
            .unwrap_or_else(|| AssignmentValue::Symbol(substitute_path(name, bindings))),
        AssignmentValue::Binary {
            left,
            operator,
            right,
        } => AssignmentValue::Binary {
            left: Box::new(substitute_assignment_value(left, bindings)),
            operator: *operator,
            right: Box::new(substitute_assignment_value(right, bindings)),
        },
    }
}

fn substitute_throw_motion_value(
    value: &ThrowMotionValue,
    bindings: &HashMap<String, String>,
) -> ThrowMotionValue {
    ThrowMotionValue {
        asset: match &value.asset {
            ThrowMotionAsset::Literal(asset) => ThrowMotionAsset::Literal(asset.clone()),
            ThrowMotionAsset::Expression(asset) => {
                ThrowMotionAsset::Expression(Box::new(substitute_assignment_value(asset, bindings)))
            }
        },
        target: value.target.as_ref().map(|target| match target {
            ThrowMotionTarget::Object(target) => {
                ThrowMotionTarget::Object(substitute_path(target, bindings))
            }
            ThrowMotionTarget::Position(values) => ThrowMotionTarget::Position([
                substitute_assignment_value(&values[0], bindings),
                substitute_assignment_value(&values[1], bindings),
                substitute_assignment_value(&values[2], bindings),
            ]),
        }),
    }
}

pub fn substitute_ui_property_value(
    value: &scenemax_parser::UiPropertyValue,
    bindings: &HashMap<String, String>,
) -> scenemax_parser::UiPropertyValue {
    match value {
        scenemax_parser::UiPropertyValue::Literal(text) => {
            scenemax_parser::UiPropertyValue::Literal(substitute_reference(text, bindings))
        }
        scenemax_parser::UiPropertyValue::Expression(value) => {
            scenemax_parser::UiPropertyValue::Expression(substitute_assignment_value(
                value, bindings,
            ))
        }
        scenemax_parser::UiPropertyValue::Concatenation(parts) => {
            scenemax_parser::UiPropertyValue::Concatenation(
                parts
                    .iter()
                    .map(|part| substitute_ui_property_value_part(part, bindings))
                    .collect(),
            )
        }
    }
}

fn substitute_ui_property_value_part(
    part: &scenemax_parser::UiPropertyValuePart,
    bindings: &HashMap<String, String>,
) -> scenemax_parser::UiPropertyValuePart {
    match part {
        scenemax_parser::UiPropertyValuePart::Literal(text) => {
            scenemax_parser::UiPropertyValuePart::Literal(substitute_reference(text, bindings))
        }
        scenemax_parser::UiPropertyValuePart::Expression(value) => {
            scenemax_parser::UiPropertyValuePart::Expression(substitute_assignment_value(
                value, bindings,
            ))
        }
    }
}

pub fn substitute_path(text: &str, bindings: &HashMap<String, String>) -> String {
    for (param, arg) in bindings {
        if text == param {
            return arg.clone();
        }
        if let Some(rest) = text.strip_prefix(&format!("{param}.")) {
            return format!("{arg}.{rest}");
        }
    }
    text.to_owned()
}

pub fn substitute_reference(text: &str, bindings: &HashMap<String, String>) -> String {
    let substituted = substitute_path(text, bindings);
    if substituted != text {
        return substituted;
    }
    for (param, arg) in bindings {
        if let Some(rest) = text.strip_prefix(&format!("{param} ")) {
            return format!("{arg} {rest}");
        }
    }
    text.to_owned()
}

pub fn repeat_actions(actions: &[Statement], times: usize) -> Vec<Statement> {
    let bounded_times = times.min(128);
    let mut repeated = Vec::with_capacity(actions.len().saturating_mul(bounded_times));
    for _ in 0..bounded_times {
        repeated.extend(actions.iter().cloned());
    }
    repeated
}

pub fn collect_shared_assignment_names(program: &Program) -> std::collections::HashSet<String> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::SharedAssignment(assignment) => Some(assignment.name.clone()),
            Statement::ObjectPool(pool) if pool.shared => Some(pool.name.clone()),
            _ => None,
        })
        .collect()
}

pub fn actions_with_parent_continuation(
    mut block_actions: Vec<Statement>,
    parent_tail: &[Statement],
) -> Vec<Statement> {
    block_actions.extend(parent_tail.iter().cloned());
    block_actions
}

pub fn requested_animation_names_match(left: &str, right: &str) -> bool {
    normalized_animation_name(left) == normalized_animation_name(right)
}

pub fn animation_candidate_score(candidate: &str, requested_key: &str) -> usize {
    let normalized = normalized_animation_name(candidate);
    let mut score = usize::from(normalized == requested_key) * 10;
    score += candidate
        .split(['|', ':', '/', '\\', '.'])
        .filter(|part| normalized_animation_name(part) == requested_key)
        .count()
        * 20;
    score += candidate
        .rsplit_once('.')
        .and_then(|(_, suffix)| suffix.parse::<usize>().ok())
        .unwrap_or_default();
    score
}

pub fn animation_name_matches(candidate: &str, requested_key: &str) -> bool {
    normalized_animation_name(candidate) == requested_key
        || candidate
            .split(['|', ':', '/', '\\', '.'])
            .any(|part| normalized_animation_name(part) == requested_key)
}

pub fn normalized_animation_name(name: &str) -> String {
    name.chars()
        .filter(|value| value.is_ascii_alphanumeric())
        .flat_map(|value| value.to_lowercase())
        .collect()
}

#[cfg(test)]
mod tests {
    #[test]
    fn array_command_function_arguments_keep_typed_values() {
        use super::*;
        let program = scenemax_parser::parse_program("append(value) = {\n items.push(value)\n items.reset(value)\n}").unwrap();
        let functions = collect_functions_by_name(&program);
        let actions = instantiate_function_actions(&functions["append"], &["\"a,b\"".into()]);
        for action in actions {
            let Statement::Assignment(a) = action else { panic!("assignment") };
            let AssignmentValue::ArrayEdit { operation, .. } = a.value else { panic!("array edit") };
            let (scenemax_parser::ArrayOperation::Push(value) | scenemax_parser::ArrayOperation::Reset(value)) = operation else { panic!("operand") };
            assert_eq!(*value, AssignmentValue::Literal(serde_json::json!("a,b")));
        }
    }
    use super::*;
    use scenemax_parser::{MoveDirection, parse_program};

    #[test]
    fn collects_last_animation_for_target() {
        let program = parse_program("d=>dragon\nd.fly async\nd.idle loop").unwrap();
        let animations = collect_animations_by_target(&program);

        assert_eq!(
            animations.get("d"),
            Some(&AnimationStatement {
 rewind: None,
                frame_range: None,
                frame_range_name: None,
                target: "d".to_owned(),
                clip: "idle".to_owned(),
                speed: 1.0,
                speed_value: AssignmentValue::Number(1.0),
                looped: true,
                blocking: false,
            })
        );
    }

    #[test]
    fn startup_state_collection_stops_at_first_blocking_boundary() {
        let program = parse_program(
            "intro => dragon\nboss => boss: hidden\nboss.show\nboss.Idle loop\nring.turn left 360 in 50 seconds async\nwait 1 seconds\nintro.hide\nboss.hide\nintro.Run loop\nintro.turn right 45 in 0.1 seconds",
        )
        .unwrap();

        let visibility = collect_visibility_by_target(&program);
        let animations = collect_animations_by_target(&program);
        let turns = collect_turn_by_target(&program);

        assert_eq!(visibility.get("boss").copied(), Some(true));
        assert_eq!(visibility.get("intro").copied(), None);
        assert_eq!(
            animations
                .get("boss")
                .map(|animation| animation.clip.as_str()),
            Some("Idle")
        );
        assert_eq!(animations.get("intro"), None);
        assert!(turns.contains_key("ring"));
        assert!(!turns.contains_key("intro"));
    }

    #[test]
    fn nested_branch_continuation_keeps_ai_parent_tail_after_blocking_action() {
        let branch_actions = vec![Statement::Animate(AnimationStatement {
 rewind: None,
            frame_range: None,
            frame_range_name: None,
            target: "player2".to_owned(),
            clip: "FlyKick".to_owned(),
            speed: 2.9,
            speed_value: AssignmentValue::Number(2.9),
            looped: false,
            blocking: true,
        })];
        let parent_actions = vec![
            Statement::If(scenemax_parser::IfStatement {
                condition: Condition::EqualsNumber {
                    name: "far_choice".to_owned(),
                    value: 1.0,
                },
                actions: branch_actions.clone(),
                else_actions: Vec::new(),
            }),
            Statement::Assignment(scenemax_parser::AssignmentStatement {
                name: "op_action".to_owned(),
                value: AssignmentValue::Number(0.0),
            }),
            Statement::Guarded {
                condition: Condition::EqualsNumber {
                    name: "enemy_ko".to_owned(),
                    value: 0.0,
                },
                actions: vec![Statement::Animate(AnimationStatement {
 rewind: None,
                    frame_range: None,
                    frame_range_name: None,
                    target: "player2".to_owned(),
                    clip: "Idle".to_owned(),
                    speed: 1.0,
                    speed_value: AssignmentValue::Number(1.0),
                    looped: true,
                    blocking: false,
                })],
            },
        ];

        let continuation = actions_with_parent_continuation(branch_actions, &parent_actions[1..]);

        assert_eq!(continuation.len(), 3);
        assert!(matches!(
            &continuation[1],
            Statement::Assignment(assignment)
                if assignment.name == "op_action"
                    && assignment.value == AssignmentValue::Number(0.0)
        ));
    }

    #[test]
    fn normalizes_scene_max_animation_names_for_gltf_lookup() {
        assert_eq!(
            normalized_animation_name("Fly_Kick"),
            normalized_animation_name("fly kick")
        );
        assert!(animation_name_matches(
            "mixamo.com|High-Kick",
            &normalized_animation_name("HighKick")
        ));
        assert!(requested_animation_names_match("idle2", "Idle 2"));
    }

    #[test]
    fn collects_shared_assignment_names_for_scene_switch_state() {
        let program = parse_program("shared var score = 0, timer = 30\nvar life2 = 10").unwrap();
        let shared = collect_shared_assignment_names(&program);

        assert!(shared.contains("score"));
        assert!(shared.contains("timer"));
        assert!(!shared.contains("life2"));
    }

    #[test]
    fn instantiates_parameterized_function_actions() {
        let program = parse_program(
            "op_punch(p2) = {\n  p2.move forward 0.2 for 0.2 seconds\n  p2.look at (player1)\n  if (p2.data.is_down == 0) {\n    p2.CrossPunch\n  }\n}\nrun op_punch(player2)",
        )
        .unwrap();
        let functions = collect_functions_by_name(&program);
        let function = functions.get("op_punch").unwrap();

        let actions = instantiate_function_actions(function, &["player2".to_owned()]);

        assert_eq!(
            actions,
            vec![
                Statement::Move(scenemax_parser::MoveStatement {
                    easing: None,
                    target: "player2".to_owned(),
                    direction: MoveDirection::Forward,
                    distance: 0.2,
                    distance_value: AssignmentValue::Number(0.2),
                    duration_seconds: 0.2,
                    duration_value: AssignmentValue::Number(0.2),
                    loop_condition: None,
                    async_run: false,
                }),
                Statement::LookAt {
                    target: "player2".to_owned(),
                    subject: "player1".to_owned(),
                },
                Statement::If(scenemax_parser::IfStatement {
                    condition: Condition::EqualsNumber {
                        name: "player2.data.is_down".to_owned(),
                        value: 0.0,
                    },
                    actions: vec![Statement::Animate(AnimationStatement {
 rewind: None,
                        frame_range: None,
                        frame_range_name: None,
                        target: "player2".to_owned(),
                        clip: "CrossPunch".to_owned(),
                        speed: 1.0,
                        speed_value: AssignmentValue::Number(1.0),
                        looped: false,
                        blocking: true,
                    })],
                    else_actions: Vec::new(),
                }),
            ]
        );
    }

    #[test]
    fn material_assignment_substitutes_target_and_surface_arguments() {
        let program = parse_program("paint(target, surface) = {\n target.material = surface\n}\nrun paint(object, \"finish\")").unwrap();
        let functions = collect_functions_by_name(&program);
        let actions = instantiate_function_actions(
            functions.get("paint").unwrap(),
            &["object".into(), "finish".into()],
        );
        assert!(
            matches!(&actions[0], Statement::SetMaterial(scenemax_parser::SetMaterialStatement { target, material: AssignmentValue::Symbol(name) }) if target == "object" && name == "finish")
        );
    }

    #[test]
    fn quoted_resource_names_do_not_substitute_function_bindings() {
        let program = parse_program("target.material = \"surface\"\ntarget.shader = \"surface\"\ntarget.ik = \"surface\"").unwrap();
        let bindings = HashMap::from([("target".into(), "instance".into()), ("surface".into(), "replacement".into())]);
        for statement in &program.statements {
            let resolved = substitute_statement(statement, &bindings);
            let (target, value) = match &resolved {
                Statement::SetMaterial(a) => (&a.target, &a.material),
                Statement::SetShader(a) => (&a.target, &a.shader),
                Statement::Ik(a) => (&a.owner, match &a.action { scenemax_parser::ik::IkAction::Apply(value) => value, _ => panic!() }),
                _ => panic!(),
            };
            assert_eq!(target, "instance");
            assert!(matches!(value, AssignmentValue::Literal(serde_json::Value::String(name)) if name == "surface"));
        }
    }

    #[test]
    fn instantiates_logger_string_function_args_as_text() {
        let program = parse_program(
            "log_key(name, clip) = {\n  Logger.info name\n  Logger.info clip\n}\nrun log_key(\"A\", \"mma_kick1\")",
        )
        .unwrap();
        let functions = collect_functions_by_name(&program);
        let function = functions.get("log_key").unwrap();

        let actions =
            instantiate_function_actions(function, &["A".to_owned(), "mma_kick1".to_owned()]);

        assert_eq!(
            actions,
            vec![
                Statement::Logger(LoggerStatement {
                    level: scenemax_parser::LoggerLevel::Info,
                    message: LoggerMessage::Text("A".to_owned()),
                }),
                Statement::Logger(LoggerStatement {
                    level: scenemax_parser::LoggerLevel::Info,
                    message: LoggerMessage::Text("mma_kick1".to_owned()),
                }),
            ]
        );
    }
    #[test]
    fn substitutes_physics_body_function_argument() {
        let program = parse_program("launch(item) = {\nitem.physics velocity (0,4,8)\nitem.physics torque (0,2,0) impulse\n}").unwrap();
        let functions=collect_functions_by_name(&program);
        let actions=instantiate_function_actions(functions.get("launch").unwrap(), &["sample_body".into()]);
        assert!(actions.iter().all(|s|matches!(s,Statement::PhysicsMotion{target,..} if target=="sample_body")));
        assert_eq!(actions.len(),2);
    }

}

#[cfg(test)]
mod controller_substitution_tests {
    use super::*;
    #[test]
    fn controller_function_arguments_reach_animation_speed_and_target() {
        let p = scenemax_parser::parse_program("control = animation subject.take[1.5-75%] at speed of rate then other[2-4] at speed of rate").unwrap();
        let statement = substitute_statement(&p.statements[0], &HashMap::from([("subject".into(),"instance".into()),("rate".into(),"0.25".into())]));
        let Statement::Assignment(a) = statement else { panic!(); };
        let AssignmentValue::AnimationController(c) = a.value else { panic!(); };
        assert_eq!(c.target, "instance"); assert_eq!(c.animation.target, "instance");
        assert_eq!(c.animation.speed_value, AssignmentValue::Number(0.25));
        assert_eq!(c.animation.frame_range.unwrap().start.value, 1.5);
        assert_eq!(c.subsequent[0].target,"instance");
        assert_eq!(c.subsequent[0].speed_value,AssignmentValue::Number(0.25));
    }
}

#[cfg(test)]
mod rewind_substitution_tests {
 use super::*;
 #[test]
 fn rewind_request_substitutes_controller_time_percent_and_ease_params() {
 let p=scenemax_parser::parse_program("controller.rewind amount in seconds_count seconds ease out \"Back\" (overshoot)").unwrap();
 let statement=substitute_statement(&p.statements[0],&HashMap::from([("controller".into(),"local_control".into()),("amount".into(),"30".into()),("seconds_count".into(),"4".into()),("overshoot".into(),"2".into())]));
 let Statement::AnimationControllerAction(a)=statement else{panic!()};assert_eq!(a.controller,"local_control");let r=a.rewind.unwrap();assert_eq!(r.percent,AssignmentValue::Number(30.));assert_eq!(r.duration,AssignmentValue::Number(4.));assert_eq!(r.easing.unwrap().params,vec![AssignmentValue::Number(2.)]);
 }
}

#[cfg(test)]
mod value_call_substitution_tests {
    use super::*;
    #[test]
    fn parameters_substitute_inside_nested_calls_without_renaming_callees() {
        let value = scenemax_parser::parse_assignment_value("construct(amount, wrap(label))[index]").unwrap().unwrap();
        let bindings = HashMap::from([("amount".into(), "3".into()), ("label".into(), "\"value\"".into()),
            ("index".into(), "1".into()), ("construct".into(), "wrong".into())]);
        let actual = substitute_assignment_value(&value, &bindings);
        let expected = scenemax_parser::parse_assignment_value("construct(3, wrap(\"value\"))[1]").unwrap().unwrap();
        assert_eq!(actual, expected);
    }
}
