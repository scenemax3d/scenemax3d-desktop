use std::collections::HashMap;

use scenemax_parser::{
    AnimationSpeedStatement, AnimationStatement, AssignmentValue, AttachStatement,
    CameraAttachStatement, CharacterJumpStatement, CharacterModeStatement, Condition,
    LoggerMessage, LoggerStatement, PoolReleaseStatement, PositionExpr, PositionStatement,
    PositionValue, Program, Statement, UiTargetPath,
};

#[derive(Debug, Clone, PartialEq)]
pub struct FunctionRuntime {
    pub params: Vec<String>,
    pub guard: Option<Condition>,
    pub actions: Vec<Statement>,
}

pub fn collect_animations_by_target(program: &Program) -> HashMap<String, AnimationStatement> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::Animate(animation) => Some((animation.target.clone(), animation.clone())),
            _ => None,
        })
        .collect()
}

pub fn collect_visibility_by_target(program: &Program) -> HashMap<String, bool> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::Visibility { target, visible } => Some((target.clone(), *visible)),
            _ => None,
        })
        .collect()
}

pub fn collect_turn_by_target(
    program: &Program,
) -> HashMap<String, scenemax_parser::TurnStatement> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::Turn(turn) => Some((turn.target.clone(), turn.clone())),
            _ => None,
        })
        .collect()
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
    if function.params.is_empty() || args.is_empty() {
        return function.actions.clone();
    }
    let bindings = function
        .params
        .iter()
        .zip(args.iter())
        .map(|(param, arg)| (param.clone(), arg.clone()))
        .collect::<HashMap<_, _>>();
    function
        .actions
        .iter()
        .map(|action| substitute_statement(action, &bindings))
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
        Statement::Animate(animation) => Statement::Animate(AnimationStatement {
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
        Statement::Turn(turn) => Statement::Turn(scenemax_parser::TurnStatement {
            target: substitute_path(&turn.target, bindings),
            degrees: turn.degrees,
            degrees_value: substitute_assignment_value(&turn.degrees_value, bindings),
            duration_seconds: turn.duration_seconds,
            duration_value: substitute_assignment_value(&turn.duration_value, bindings),
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
            })
        }
        Statement::UiEase(ease) => Statement::UiEase(scenemax_parser::UiEaseStatement {
            target: substitute_ui_target_path(&ease.target, bindings),
            easing: ease.easing.clone(),
            direction: ease.direction,
            duration_seconds: ease.duration_seconds,
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
                    .or_else(|| Some(LoggerMessage::Text(arg.clone())))
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
        AssignmentValue::Number(value) => AssignmentValue::Number(*value),
        AssignmentValue::Condition(condition) => {
            AssignmentValue::Condition(Box::new(substitute_condition(condition, bindings)))
        }
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
            .and_then(|arg| arg.parse::<f32>().ok())
            .map(AssignmentValue::Number)
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
    use super::*;
    use scenemax_parser::{MoveDirection, parse_program};

    #[test]
    fn collects_last_animation_for_target() {
        let program = parse_program("d=>dragon\nd.fly\nd.idle loop").unwrap();
        let animations = collect_animations_by_target(&program);

        assert_eq!(
            animations.get("d"),
            Some(&AnimationStatement {
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
    fn nested_branch_continuation_keeps_ai_parent_tail_after_blocking_action() {
        let branch_actions = vec![Statement::Animate(AnimationStatement {
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
}
