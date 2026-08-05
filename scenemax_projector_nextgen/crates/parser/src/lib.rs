use thiserror::Error;

pub mod generated {
    #![allow(clippy::all)]
    #![allow(dead_code)]
    #![allow(non_camel_case_types)]
    #![allow(non_snake_case)]
    #![allow(non_upper_case_globals)]
    #![allow(unused_imports)]
    #![allow(unused_mut)]
    #![allow(unused_parens)]
    #![allow(unused_variables)]

    pub mod scenemaxnextgenlexer;
    pub mod scenemaxnextgenlistener;
    pub mod scenemaxnextgenparser;
    pub mod scenemaxnextgenvisitor;
}

#[derive(Debug, Clone, PartialEq)]
pub struct Program {
    pub statements: Vec<Statement>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Statement {
    ModelDecl {
        name: String,
        resource: String,
        options: EntityOptions,
    },
    Animate(AnimationStatement),
    Visibility {
        target: String,
        visible: bool,
    },
    LookAt {
        target: String,
        subject: String,
    },
    Turn(TurnStatement),
    Move(MoveStatement),
    KeyEvent(KeyEventStatement),
    WhenEvent(WhenEventStatement),
    If(IfStatement),
    Async {
        actions: Vec<Statement>,
    },
    Wait {
        seconds: f32,
    },
    Assignment(AssignmentStatement),
    FightingCamera(FightingCameraStatement),
    CameraSystemSelect {
        name: String,
    },
    FunctionDef(FunctionDefStatement),
    RunFunction {
        name: String,
    },
    CameraPosition(SceneMaxVec3),
    CameraRotation(SceneMaxVec3),
    WaitForKey {
        key: String,
    },
    SwitchTo {
        scene: String,
    },
    AddCode {
        path: String,
    },
    Unsupported {
        text: String,
    },
}

#[derive(Debug, Clone, Copy, Default, PartialEq)]
pub struct EntityOptions {
    pub position: Option<SceneMaxVec3>,
    pub rotation_degrees: Option<SceneMaxVec3>,
    pub scale: Option<SceneMaxVec3>,
    pub size: Option<SceneMaxVec3>,
    pub hidden: bool,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct SceneMaxVec3 {
    pub x: f32,
    pub y: f32,
    pub z: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AnimationStatement {
    pub target: String,
    pub clip: String,
    pub speed: f32,
    pub looped: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TurnStatement {
    pub target: String,
    pub degrees: f32,
    pub duration_seconds: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MoveStatement {
    pub target: String,
    pub direction: MoveDirection,
    pub distance: f32,
    pub duration_seconds: f32,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum MoveDirection {
    Forward,
    Backward,
}

#[derive(Debug, Clone, PartialEq)]
pub struct KeyEventStatement {
    pub key: String,
    pub trigger: KeyTrigger,
    pub actions: Vec<Statement>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum KeyTrigger {
    Pressed,
    PressedOnce,
    Released,
}

#[derive(Debug, Clone, PartialEq)]
pub struct WhenEventStatement {
    pub condition: Condition,
    pub actions: Vec<Statement>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Condition {
    EqualsNumber { name: String, value: f32 },
    NotEqualsNumber { name: String, value: f32 },
    EqualsSymbol { name: String, value: String },
    NotEqualsSymbol { name: String, value: String },
}

#[derive(Debug, Clone, PartialEq)]
pub struct AssignmentStatement {
    pub name: String,
    pub value: AssignmentValue,
}

#[derive(Debug, Clone, PartialEq)]
pub enum AssignmentValue {
    Number(f32),
    Symbol(String),
}

#[derive(Debug, Clone, PartialEq)]
pub struct FightingCameraStatement {
    pub name: String,
    pub target_a: String,
    pub target_b: String,
    pub depth: f32,
    pub height: f32,
    pub side: f32,
    pub min_distance: f32,
    pub max_distance: f32,
    pub damping: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct IfStatement {
    pub condition: Condition,
    pub actions: Vec<Statement>,
    pub else_actions: Vec<Statement>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct FunctionDefStatement {
    pub name: String,
    pub actions: Vec<Statement>,
}

#[derive(Debug, Error, PartialEq)]
pub enum ParseError {
    #[error("invalid number '{0}'")]
    InvalidNumber(String),
}

pub fn parse_program(source: &str) -> Result<Program, ParseError> {
    let logical_lines = logical_lines(source);
    let mut statements = Vec::new();
    let mut index = 0;
    let mut block_depth = 0usize;

    while index < logical_lines.len() {
        let line = logical_lines[index].trim();
        if line.is_empty() {
            index += 1;
            continue;
        }

        if is_condition_guard(line) {
            index += 1;
            continue;
        }

        if block_depth > 0 {
            block_depth = update_block_depth(block_depth, line);
            index += 1;
            continue;
        }

        if let Some((event, next_index)) = parse_key_event_block(&logical_lines, index)? {
            statements.push(Statement::KeyEvent(event));
            index = next_index;
            continue;
        }

        if let Some((event, next_index)) = parse_when_event_block(&logical_lines, index)? {
            statements.push(Statement::WhenEvent(event));
            index = next_index;
            continue;
        }

        if let Some((function, next_index)) = parse_function_def_block(&logical_lines, index)? {
            statements.push(Statement::FunctionDef(function));
            index = next_index;
            continue;
        }

        if opens_runtime_block(line) {
            block_depth = update_block_depth(block_depth, line);
            statements.push(unsupported(line));
            index += 1;
            continue;
        }

        if starts_with_keyword(line, "add") {
            let mut block = line.to_owned();
            while !has_code_terminator(&block) && index + 1 < logical_lines.len() {
                index += 1;
                block.push(' ');
                block.push_str(logical_lines[index].trim());
            }
            let paths = parse_quoted_strings(&block);
            if paths.is_empty() {
                statements.push(unsupported(line));
            } else {
                statements.extend(paths.into_iter().map(|path| Statement::AddCode { path }));
            }
            index += 1;
            continue;
        }

        if let Some(assignments) = parse_assignment_list(line)? {
            statements.extend(assignments.into_iter().map(Statement::Assignment));
            index += 1;
            continue;
        }

        statements.push(parse_statement(line)?);
        index += 1;
    }

    Ok(Program { statements })
}

fn opens_runtime_block(line: &str) -> bool {
    let lower = line.to_ascii_lowercase();
    lower == "do async"
        || lower == "do"
        || lower.starts_with("do ")
        || (lower.starts_with("when ") && lower.ends_with(" do"))
        || line.ends_with('{')
}

fn parse_key_event_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(KeyEventStatement, usize)>, ParseError> {
    let Some((key, trigger)) = parse_key_event_header(logical_lines[index].trim()) else {
        return Ok(None);
    };

    let mut depth = 1usize;
    let mut actions = Vec::new();
    let mut cursor = index + 1;
    while cursor < logical_lines.len() {
        let line = logical_lines[cursor].trim();
        let lower = line.to_ascii_lowercase();

        if lower == "end do" {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                break;
            }
            continue;
        }

        if depth == 1 && is_close_else_open(line) {
            break;
        }

        if lower.starts_with('}') {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                break;
            }
            if lower.ends_with('{') {
                depth += 1;
            }
            continue;
        }

        if is_if_header(line) {
            if let Some((statement, next_index)) = parse_if_block(logical_lines, cursor)? {
                actions.push(Statement::If(statement));
                cursor = next_index;
            } else {
                cursor = skip_control_block(logical_lines, cursor);
            }
            continue;
        }

        if lower == "do async" {
            let (nested_actions, next_index) = parse_action_block(logical_lines, cursor + 1)?;
            actions.push(Statement::Async {
                actions: nested_actions,
            });
            cursor = next_index;
            continue;
        }

        if lower == "do" {
            let (nested_actions, next_index) = parse_action_block(logical_lines, cursor + 1)?;
            actions.extend(nested_actions);
            cursor = next_index;
            continue;
        }

        if lower.starts_with("do ") {
            cursor = skip_control_block(logical_lines, cursor);
            continue;
        }

        if should_parse_key_action_line(line) {
            let action = parse_statement(line)?;
            if !matches!(action, Statement::Unsupported { .. }) {
                actions.push(action);
            }
        }

        if opens_runtime_block(line) {
            depth += 1;
        }
        cursor += 1;
    }

    Ok(Some((
        KeyEventStatement {
            key,
            trigger,
            actions,
        },
        cursor,
    )))
}

fn parse_when_event_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(WhenEventStatement, usize)>, ParseError> {
    let line = logical_lines[index].trim();
    if line.to_ascii_lowercase().starts_with("when key ") {
        return Ok(None);
    }
    let Some(condition) = parse_when_event_header(line)? else {
        return Ok(None);
    };

    let (actions, next_index) = parse_action_block(logical_lines, index + 1)?;
    Ok(Some((
        WhenEventStatement { condition, actions },
        next_index,
    )))
}

fn parse_function_def_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(FunctionDefStatement, usize)>, ParseError> {
    let Some(name) = parse_function_def_header(logical_lines[index].trim()) else {
        return Ok(None);
    };
    let (actions, next_index) = parse_action_block(logical_lines, index + 1)?;
    Ok(Some((FunctionDefStatement { name, actions }, next_index)))
}

fn parse_function_def_header(line: &str) -> Option<String> {
    let Some((name, rest)) = line.split_once('=') else {
        return None;
    };
    if rest.trim() != "{" {
        return None;
    }
    let name = name.trim().split('(').next().unwrap_or_default().trim();
    if is_variable_name(name) {
        Some(name.to_owned())
    } else {
        None
    }
}

fn parse_when_event_header(line: &str) -> Result<Option<Condition>, ParseError> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("when ") || !(lower.ends_with(" do") || lower.ends_with(" do async")) {
        return Ok(None);
    }
    let condition_text = line["when ".len()..line.len() - trailing_do_len(&lower)].trim();
    parse_condition(condition_text)
}

fn parse_condition(text: &str) -> Result<Option<Condition>, ParseError> {
    if let Some((name, value)) = parse_comparison_value(text, "!=")? {
        return Ok(Some(match value {
            AssignmentValue::Number(value) => Condition::NotEqualsNumber { name, value },
            AssignmentValue::Symbol(value) => Condition::NotEqualsSymbol { name, value },
        }));
    }
    if let Some((name, value)) = parse_comparison_value(text, "==")? {
        return Ok(Some(match value {
            AssignmentValue::Number(value) => Condition::EqualsNumber { name, value },
            AssignmentValue::Symbol(value) => Condition::EqualsSymbol { name, value },
        }));
    }
    Ok(None)
}

fn parse_comparison_value(
    text: &str,
    operator: &str,
) -> Result<Option<(String, AssignmentValue)>, ParseError> {
    let Some((left, right)) = text.split_once(operator) else {
        return Ok(None);
    };
    let name = left.trim();
    if !is_variable_path(name) {
        return Ok(None);
    }
    let raw_value = right.trim().trim_end_matches('{').trim();
    let Some(value) = parse_assignment_value(raw_value)? else {
        return Ok(None);
    };
    Ok(Some((name.to_owned(), value)))
}

fn parse_key_event_header(line: &str) -> Option<(String, KeyTrigger)> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("when key ") || !(lower.ends_with(" do") || lower.ends_with(" do async"))
    {
        return None;
    }

    let after_key = line["when key ".len()..].trim();
    let key = after_key.split_whitespace().next()?.to_ascii_lowercase();
    let trigger = if lower.contains(" is released do") {
        KeyTrigger::Released
    } else if lower.contains(" is pressed once do") {
        KeyTrigger::PressedOnce
    } else if lower.contains(" is pressed do") {
        KeyTrigger::Pressed
    } else {
        return None;
    };

    Some((key, trigger))
}

fn parse_action_block(
    logical_lines: &[String],
    mut cursor: usize,
) -> Result<(Vec<Statement>, usize), ParseError> {
    let mut depth = 1usize;
    let mut actions = Vec::new();
    while cursor < logical_lines.len() {
        let line = logical_lines[cursor].trim();
        let lower = line.to_ascii_lowercase();

        if lower == "end do" {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                break;
            }
            continue;
        }

        if depth == 1 && is_close_else_open(line) {
            break;
        }

        if lower.starts_with('}') {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                break;
            }
            if lower.ends_with('{') {
                depth += 1;
            }
            continue;
        }

        if is_if_header(line) {
            if let Some((statement, next_index)) = parse_if_block(logical_lines, cursor)? {
                actions.push(Statement::If(statement));
                cursor = next_index;
            } else {
                cursor = skip_control_block(logical_lines, cursor);
            }
            continue;
        }

        if lower == "do async" {
            let (nested_actions, next_index) = parse_action_block(logical_lines, cursor + 1)?;
            actions.push(Statement::Async {
                actions: nested_actions,
            });
            cursor = next_index;
            continue;
        }

        if lower == "do" {
            let (nested_actions, next_index) = parse_action_block(logical_lines, cursor + 1)?;
            actions.extend(nested_actions);
            cursor = next_index;
            continue;
        }

        if lower.starts_with("do ") {
            cursor = skip_control_block(logical_lines, cursor);
            continue;
        }

        if should_parse_key_action_line(line) {
            let action = parse_statement(line)?;
            if !matches!(action, Statement::Unsupported { .. }) {
                actions.push(action);
            }
        }

        if opens_runtime_block(line) {
            depth += 1;
        }
        cursor += 1;
    }

    Ok((actions, cursor))
}

fn parse_if_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(IfStatement, usize)>, ParseError> {
    let Some(condition) = parse_if_header(logical_lines[index].trim())? else {
        return Ok(None);
    };

    let (actions, mut next_index) = parse_action_block(logical_lines, index + 1)?;
    let mut else_actions = Vec::new();
    if next_index < logical_lines.len() && is_close_else_open(logical_lines[next_index].trim()) {
        let (parsed_else_actions, after_else) = parse_action_block(logical_lines, next_index + 1)?;
        else_actions = parsed_else_actions;
        next_index = after_else;
    }

    Ok(Some((
        IfStatement {
            condition,
            actions,
            else_actions,
        },
        next_index,
    )))
}

fn parse_if_header(line: &str) -> Result<Option<Condition>, ParseError> {
    if !is_if_header(line) {
        return Ok(None);
    }
    let without_open_brace = line.trim().trim_end_matches('{').trim();
    let Some(after_if) = without_open_brace
        .strip_prefix("if")
        .or_else(|| without_open_brace.strip_prefix("IF"))
    else {
        return Ok(None);
    };
    let condition_text = after_if
        .trim()
        .strip_prefix('(')
        .and_then(|value| value.strip_suffix(')'))
        .unwrap_or_else(|| after_if.trim())
        .trim();
    parse_condition(condition_text)
}

fn is_if_header(line: &str) -> bool {
    let lower = line.trim().to_ascii_lowercase();
    (lower.starts_with("if ") || lower.starts_with("if(")) && lower.ends_with('{')
}

fn is_close_else_open(line: &str) -> bool {
    let lower = line.trim().to_ascii_lowercase();
    lower.starts_with("} else") && lower.ends_with('{')
}

fn skip_control_block(logical_lines: &[String], index: usize) -> usize {
    let mut depth = 1usize;
    let mut cursor = index + 1;
    while cursor < logical_lines.len() {
        let line = logical_lines[cursor].trim();
        let lower = line.to_ascii_lowercase();

        if lower.starts_with('}') {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                if lower.contains("else") && lower.ends_with('{') {
                    depth = 1;
                    continue;
                }
                break;
            }
            if lower.ends_with('{') {
                depth += 1;
            }
            continue;
        }

        if opens_runtime_block(line) {
            depth += 1;
        }
        cursor += 1;
    }
    cursor
}

fn trailing_do_len(lower: &str) -> usize {
    if lower.ends_with(" do async") {
        " do async".len()
    } else {
        " do".len()
    }
}

fn should_parse_key_action_line(line: &str) -> bool {
    let trimmed = line.trim();
    if trimmed.is_empty()
        || is_condition_guard(trimmed)
        || trimmed.eq_ignore_ascii_case("do")
        || trimmed.eq_ignore_ascii_case("do async")
        || trimmed.to_ascii_lowercase().starts_with("do ")
        || trimmed.to_ascii_lowercase().starts_with("if ")
        || trimmed.to_ascii_lowercase().starts_with("else")
        || trimmed.ends_with('{')
    {
        return false;
    }
    true
}

fn is_condition_guard(line: &str) -> bool {
    let trimmed = line.trim();
    trimmed.starts_with('[') || trimmed.starts_with("#[")
}

fn update_block_depth(current: usize, line: &str) -> usize {
    let mut depth = current;
    let lower = line.to_ascii_lowercase();
    if lower == "end do" || lower.starts_with('}') {
        depth = depth.saturating_sub(1);
    }
    if opens_runtime_block(line) {
        depth += 1;
    }
    depth
}

fn parse_statement(line: &str) -> Result<Statement, ParseError> {
    if let Some(camera) = parse_fighting_camera(line)? {
        return Ok(Statement::FightingCamera(camera));
    }

    if let Some(camera_system) = parse_camera_system_select(line) {
        return Ok(Statement::CameraSystemSelect {
            name: camera_system,
        });
    }

    if let Some(run_function) = parse_run_function(line) {
        return Ok(Statement::RunFunction { name: run_function });
    }

    if let Some(assignment) = parse_assignment(line)? {
        return Ok(Statement::Assignment(assignment));
    }

    if let Some(scene) = parse_switch(line) {
        return Ok(Statement::SwitchTo { scene });
    }

    if let Some(key) = parse_wait_for_key(line) {
        return Ok(Statement::WaitForKey { key });
    }

    if let Some(seconds) = parse_wait(line) {
        return Ok(Statement::Wait { seconds });
    }

    if let Some(position) = parse_camera_command(line, "camera.pos")? {
        return Ok(Statement::CameraPosition(position));
    }

    if let Some(rotation) = parse_camera_command(line, "camera.rotate")? {
        return Ok(Statement::CameraRotation(rotation));
    }

    if let Some(statement) = parse_model_decl(line)? {
        return Ok(statement);
    }

    if let Some(visibility) = parse_visibility(line) {
        return Ok(visibility);
    }

    if let Some(look_at) = parse_look_at(line) {
        return Ok(look_at);
    }

    if let Some(turn) = parse_turn(line)? {
        return Ok(Statement::Turn(turn));
    }

    if let Some(movement) = parse_move(line)? {
        return Ok(Statement::Move(movement));
    }

    if let Some(animation) = parse_animation(line)? {
        return Ok(Statement::Animate(animation));
    }

    Ok(unsupported(line))
}

fn parse_camera_system_select(line: &str) -> Option<String> {
    let (left, right) = line.split_once('=')?;
    if !left.trim().eq_ignore_ascii_case("camera.system") {
        return None;
    }
    let name = right.trim();
    if is_variable_name(name) {
        Some(name.to_owned())
    } else {
        None
    }
}

fn parse_run_function(line: &str) -> Option<String> {
    let lower = line.to_ascii_lowercase();
    if lower.contains(" every ") || !starts_with_keyword(line, "run") {
        return None;
    }
    let after_run = line["run".len()..].trim();
    let name = after_run
        .split(|value: char| value.is_whitespace() || value == '(')
        .next()
        .unwrap_or_default()
        .trim();
    if is_variable_name(name) {
        Some(name.to_owned())
    } else {
        None
    }
}

fn parse_fighting_camera(line: &str) -> Result<Option<FightingCameraStatement>, ParseError> {
    let Some((name, rest)) = line.split_once('=') else {
        return Ok(None);
    };
    let rest = rest.trim();
    let Some(args_text) = rest
        .strip_prefix("camera.system.fighting")
        .and_then(|after| after.trim().strip_prefix('('))
        .and_then(|after| after.strip_suffix(')'))
    else {
        return Ok(None);
    };

    let args = args_text.split(',').map(str::trim).collect::<Vec<_>>();
    let (Some(target_a), Some(target_b)) = (args.first(), args.get(1)) else {
        return Ok(None);
    };
    if !is_variable_name(name.trim()) || !is_variable_name(target_a) || !is_variable_name(target_b)
    {
        return Ok(None);
    }

    Ok(Some(FightingCameraStatement {
        name: name.trim().to_owned(),
        target_a: (*target_a).to_owned(),
        target_b: (*target_b).to_owned(),
        depth: parse_named_argument(args_text, "depth")?.unwrap_or(18.0),
        height: parse_named_argument(args_text, "height")?.unwrap_or(3.0),
        side: parse_named_argument(args_text, "side")?.unwrap_or(0.0),
        min_distance: parse_named_argument(args_text, "min distance")?.unwrap_or(8.0),
        max_distance: parse_named_argument(args_text, "max distance")?.unwrap_or(28.0),
        damping: parse_named_argument(args_text, "damping")?.unwrap_or(8.0),
    }))
}

fn parse_named_argument(text: &str, name: &str) -> Result<Option<f32>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find(&name.to_ascii_lowercase()) else {
        return Ok(None);
    };
    let raw = text[index + name.len()..]
        .trim()
        .split(|value: char| value.is_whitespace() || value == ',')
        .find(|part| !part.is_empty())
        .unwrap_or_default();
    if raw.is_empty() {
        return Ok(None);
    }
    raw.parse::<f32>()
        .map(Some)
        .map_err(|_| ParseError::InvalidNumber(raw.to_owned()))
}

fn parse_assignment(line: &str) -> Result<Option<AssignmentStatement>, ParseError> {
    let Some(mut assignments) = parse_assignment_list(line)? else {
        return Ok(None);
    };
    if assignments.len() == 1 {
        Ok(assignments.pop())
    } else {
        Ok(None)
    }
}

fn parse_assignment_list(line: &str) -> Result<Option<Vec<AssignmentStatement>>, ParseError> {
    let line = line.trim();
    if line.contains("=>")
        || line.contains("==")
        || line.contains("!=")
        || line.contains(">=")
        || line.contains("<=")
        || line.starts_with('[')
    {
        return Ok(None);
    }

    let normalized = line
        .strip_prefix("shared var ")
        .or_else(|| line.strip_prefix("var "))
        .unwrap_or(line)
        .trim();
    if normalized.starts_with('@') || normalized.to_ascii_lowercase().starts_with("camera.system") {
        return Ok(None);
    }

    let mut assignments = Vec::new();
    for segment in split_assignment_segments(normalized) {
        let Some((name, raw_value)) = segment.split_once('=') else {
            continue;
        };
        let name = name.trim();
        if name.starts_with('@') || !is_variable_path(name) {
            continue;
        }
        let raw_value = clean_assignment_value(raw_value);
        let Some(value) = parse_assignment_value(raw_value)? else {
            continue;
        };
        assignments.push(AssignmentStatement {
            name: name.to_owned(),
            value,
        });
    }

    Ok((!assignments.is_empty()).then_some(assignments))
}

fn split_assignment_segments(text: &str) -> Vec<&str> {
    text.split(',')
        .map(str::trim)
        .filter(|segment| !segment.is_empty())
        .collect()
}

fn clean_assignment_value(raw_value: &str) -> &str {
    raw_value
        .split('[')
        .next()
        .unwrap_or_default()
        .trim()
        .trim_end_matches(';')
        .trim_end_matches(',')
        .trim()
}

fn parse_assignment_value(raw_value: &str) -> Result<Option<AssignmentValue>, ParseError> {
    if raw_value.is_empty()
        || raw_value.contains("==")
        || raw_value.contains("!=")
        || raw_value.contains("&&")
        || raw_value.contains("||")
    {
        return Ok(None);
    }
    if let Ok(value) = raw_value.parse::<f32>() {
        return Ok(Some(AssignmentValue::Number(value)));
    }
    if is_variable_path(raw_value) {
        return Ok(Some(AssignmentValue::Symbol(raw_value.to_owned())));
    }
    Ok(None)
}

fn is_variable_name(name: &str) -> bool {
    let mut chars = name.chars();
    chars
        .next()
        .is_some_and(|value| value.is_ascii_alphabetic() || value == '_')
        && chars.all(|value| value.is_ascii_alphanumeric() || value == '_')
}

fn is_variable_path(name: &str) -> bool {
    !name.is_empty() && name.split('.').all(is_variable_name)
}

fn parse_switch(line: &str) -> Option<String> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("switch to ") {
        return None;
    }
    parse_quoted_strings(line).into_iter().next()
}

fn parse_wait_for_key(line: &str) -> Option<String> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("wait for key ") || !lower.contains(" to be pressed") {
        return None;
    }
    let key = line["wait for key ".len()..]
        .split_whitespace()
        .next()
        .unwrap_or_default();
    if key.is_empty() {
        None
    } else {
        Some(key.to_ascii_lowercase())
    }
}

fn parse_wait(line: &str) -> Option<f32> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("wait ") {
        return None;
    }
    let raw_seconds = line["wait ".len()..].split_whitespace().next()?;
    raw_seconds.parse::<f32>().ok()
}

fn parse_camera_command(line: &str, command: &str) -> Result<Option<SceneMaxVec3>, ParseError> {
    if !line.to_ascii_lowercase().starts_with(command) {
        return Ok(None);
    }
    parse_vec3_after(line, command).map(Some)
}

fn parse_model_decl(line: &str) -> Result<Option<Statement>, ParseError> {
    if let Some((name, rest)) = line.split_once("=>") {
        let (resource, options_text) = split_resource_and_options(rest);
        let resource = normalize_resource(resource);
        if is_deferred_or_non_model_resource(rest, &resource) {
            return Ok(Some(unsupported(line)));
        }
        return Ok(Some(Statement::ModelDecl {
            name: name.trim().to_owned(),
            resource,
            options: parse_entity_options(options_text)?,
        }));
    }

    let lower = line.to_ascii_lowercase();
    if let Some(index) = lower.find(" is a ") {
        let name = line[..index].trim();
        let rest = &line[index + " is a ".len()..];
        let (resource, options_text) = split_resource_and_options(rest);
        let resource = normalize_resource(resource);
        if is_deferred_or_non_model_resource(rest, &resource) {
            return Ok(Some(unsupported(line)));
        }
        return Ok(Some(Statement::ModelDecl {
            name: name.to_owned(),
            resource,
            options: parse_entity_options(options_text)?,
        }));
    }

    Ok(None)
}

fn parse_visibility(line: &str) -> Option<Statement> {
    let (target, command) = split_dot_command(line)?;
    match command.to_ascii_lowercase().as_str() {
        "show" => Some(Statement::Visibility {
            target,
            visible: true,
        }),
        "hide" => Some(Statement::Visibility {
            target,
            visible: false,
        }),
        _ => None,
    }
}

fn parse_look_at(line: &str) -> Option<Statement> {
    let (target, rest) = line.split_once(".look at ")?;
    let target = target.trim();
    if target.is_empty() {
        return None;
    }
    let subject = rest
        .trim()
        .strip_prefix('(')
        .and_then(|value| value.strip_suffix(')'))
        .unwrap_or_else(|| rest.trim())
        .trim();
    if subject.is_empty() {
        None
    } else {
        Some(Statement::LookAt {
            target: target.to_owned(),
            subject: subject.to_owned(),
        })
    }
}

fn parse_turn(line: &str) -> Result<Option<TurnStatement>, ParseError> {
    let Some((target, rest)) = line.split_once(".turn") else {
        return Ok(None);
    };
    let target = target.trim();
    if target.is_empty() {
        return Ok(None);
    }

    let parts = rest.split_whitespace().collect::<Vec<_>>();
    let Some(degree_index) = parts.iter().position(|part| part.parse::<f32>().is_ok()) else {
        return Ok(None);
    };
    let mut degrees = parts[degree_index]
        .parse::<f32>()
        .map_err(|_| ParseError::InvalidNumber(parts[degree_index].to_owned()))?;
    if parts
        .get(degree_index.saturating_sub(1))
        .is_some_and(|part| part.eq_ignore_ascii_case("right"))
    {
        degrees = -degrees;
    }

    let duration_seconds = parse_duration_seconds(rest)?.unwrap_or(0.0);
    Ok(Some(TurnStatement {
        target: target.to_owned(),
        degrees,
        duration_seconds,
    }))
}

fn parse_move(line: &str) -> Result<Option<MoveStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    let lower = rest.to_ascii_lowercase();
    if !lower.starts_with("move ") {
        return Ok(None);
    }

    let parts = rest.split_whitespace().collect::<Vec<_>>();
    let Some(direction_text) = parts.get(1) else {
        return Ok(None);
    };
    let direction = match direction_text.to_ascii_lowercase().as_str() {
        "forward" => MoveDirection::Forward,
        "backward" => MoveDirection::Backward,
        _ => return Ok(None),
    };
    let raw_distance = parts.get(2).copied().unwrap_or_default();
    let distance = raw_distance
        .parse::<f32>()
        .map_err(|_| ParseError::InvalidNumber(raw_distance.to_owned()))?;
    let duration_seconds = parse_for_duration_seconds(rest)?.unwrap_or(0.0);

    Ok(Some(MoveStatement {
        target,
        direction,
        distance,
        duration_seconds,
    }))
}

fn parse_animation(line: &str) -> Result<Option<AnimationStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    if target.is_empty()
        || target.eq_ignore_ascii_case("camera")
        || target.eq_ignore_ascii_case("scene")
        || target.eq_ignore_ascii_case("ui")
        || target.eq_ignore_ascii_case("screen")
        || target.eq_ignore_ascii_case("audio")
        || target.eq_ignore_ascii_case("lights")
        || target.eq_ignore_ascii_case("skybox")
        || target.contains(' ')
    {
        return Ok(None);
    }

    let rest = rest.trim();
    if rest.is_empty() || rest.starts_with('=') {
        return Ok(None);
    }

    let (clip, after_clip) = if let Some(stripped) = rest.strip_prefix('"') {
        let Some(end) = stripped.find('"') else {
            return Ok(None);
        };
        (stripped[..end].to_owned(), &stripped[end + 1..])
    } else {
        let end = rest
            .find(|value: char| value.is_whitespace() || value == ':' || value == '(')
            .unwrap_or(rest.len());
        (rest[..end].to_owned(), &rest[end..])
    };

    if clip.is_empty() {
        return Ok(None);
    }

    if clip.contains('.') || after_clip.contains('=') {
        return Ok(None);
    }

    Ok(Some(AnimationStatement {
        target: target.to_owned(),
        clip,
        speed: parse_speed(after_clip)?,
        looped: contains_keyword(after_clip, "loop"),
    }))
}

fn parse_speed(text: &str) -> Result<f32, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find("speed of") else {
        return Ok(1.0);
    };
    let raw = text[index + "speed of".len()..]
        .trim()
        .split(|value: char| value.is_whitespace() || value == ',')
        .next()
        .unwrap_or("1");
    raw.parse::<f32>()
        .map_err(|_| ParseError::InvalidNumber(raw.to_owned()))
}

fn parse_for_duration_seconds(text: &str) -> Result<Option<f32>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(for_index) = lower.find(" for ") else {
        return Ok(None);
    };
    let raw = text[for_index + " for ".len()..]
        .trim()
        .split_whitespace()
        .next()
        .unwrap_or_default();
    if raw.is_empty() {
        return Ok(None);
    }
    raw.parse::<f32>()
        .map(Some)
        .map_err(|_| ParseError::InvalidNumber(raw.to_owned()))
}

fn parse_duration_seconds(text: &str) -> Result<Option<f32>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(in_index) = lower.find(" in ") else {
        return Ok(None);
    };
    let raw = text[in_index + " in ".len()..]
        .trim()
        .split_whitespace()
        .next()
        .unwrap_or_default();
    if raw.is_empty() {
        return Ok(None);
    }
    raw.parse::<f32>()
        .map(Some)
        .map_err(|_| ParseError::InvalidNumber(raw.to_owned()))
}

fn split_resource_and_options(rest: &str) -> (&str, &str) {
    if let Some((resource, options)) = rest.split_once(':') {
        (resource.trim(), options.trim())
    } else {
        let lower = rest.to_ascii_lowercase();
        let option_start = [" pos ", " scale ", " rotate", " hidden", " and "]
            .into_iter()
            .filter_map(|needle| lower.find(needle))
            .min();
        if let Some(index) = option_start {
            (rest[..index].trim(), rest[index..].trim())
        } else {
            (rest.trim(), "")
        }
    }
}

fn normalize_resource(resource: &str) -> String {
    let mut parts = resource.split_whitespace().filter(|part| {
        !part.eq_ignore_ascii_case("dynamic")
            && !part.eq_ignore_ascii_case("static")
            && !part.eq_ignore_ascii_case("collider")
    });
    parts.next().unwrap_or(resource.trim()).trim().to_owned()
}

fn is_deferred_or_non_model_resource(raw_resource: &str, resource: &str) -> bool {
    let raw_lower = raw_resource.to_ascii_lowercase();
    let resource_lower = resource.to_ascii_lowercase();
    raw_lower.contains(" sprite")
        || resource_lower.starts_with("videos.")
        || resource_lower.starts_with("cinematic.camera.")
        || resource_lower.starts_with("object.pool")
}

fn parse_entity_options(text: &str) -> Result<EntityOptions, ParseError> {
    let scale = parse_scalar_after(text, "scale")?.map(|value| SceneMaxVec3 {
        x: value,
        y: value,
        z: value,
    });
    Ok(EntityOptions {
        position: parse_vec3_after(text, "pos").ok(),
        rotation_degrees: parse_vec3_after(text, "rotate").ok(),
        scale,
        size: parse_vec3_after(text, "size").ok(),
        hidden: contains_keyword(text, "hidden"),
    })
}

fn parse_vec3_after(text: &str, name: &str) -> Result<SceneMaxVec3, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find(&name.to_ascii_lowercase()) else {
        return Err(ParseError::InvalidNumber(name.to_owned()));
    };
    let after_name = &text[index + name.len()..];
    let Some(open_index) = after_name.find('(') else {
        return Err(ParseError::InvalidNumber(name.to_owned()));
    };
    let after_open = &after_name[open_index + 1..];
    let Some(close_index) = after_open.find(')') else {
        return Err(ParseError::InvalidNumber(name.to_owned()));
    };
    let raw_values = &after_open[..close_index];
    let values = raw_values
        .split(',')
        .map(|raw| {
            let value = raw.trim();
            value
                .parse::<f32>()
                .map_err(|_| ParseError::InvalidNumber(value.to_owned()))
        })
        .collect::<Result<Vec<_>, _>>()?;

    if values.len() != 3 {
        return Err(ParseError::InvalidNumber(raw_values.to_owned()));
    }

    Ok(SceneMaxVec3 {
        x: values[0],
        y: values[1],
        z: values[2],
    })
}

fn parse_scalar_after(text: &str, name: &str) -> Result<Option<f32>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find(&name.to_ascii_lowercase()) else {
        return Ok(None);
    };
    let after = text[index + name.len()..].trim_start();
    if after.starts_with('(') {
        return Ok(None);
    }
    let raw = after
        .split(|value: char| value.is_whitespace() || value == ',' || value == ':' || value == ')')
        .next()
        .unwrap_or_default();
    if raw.is_empty() {
        return Ok(None);
    }
    raw.parse::<f32>()
        .map(Some)
        .map_err(|_| ParseError::InvalidNumber(raw.to_owned()))
}

fn logical_lines(source: &str) -> Vec<String> {
    let mut result: Vec<String> = Vec::new();
    for raw_line in source.lines() {
        let line = strip_comment(raw_line).trim();
        if line.is_empty() {
            continue;
        }

        if line.starts_with(',') || result.last().is_some_and(|previous| is_open_add(previous)) {
            if let Some(previous) = result.last_mut() {
                previous.push(' ');
                previous.push_str(line);
            }
        } else {
            result.push(line.to_owned());
        }
    }
    result
}

fn is_open_add(line: &str) -> bool {
    starts_with_keyword(line, "add") && !has_code_terminator(line)
}

fn has_code_terminator(line: &str) -> bool {
    line.split(|value: char| value.is_whitespace() || value == ',')
        .any(|part| part.eq_ignore_ascii_case("code"))
}

fn strip_comment(line: &str) -> &str {
    let mut in_quote = false;
    for (index, value) in line.char_indices() {
        if value == '"' {
            in_quote = !in_quote;
        }
        if !in_quote && line[index..].starts_with("//") {
            return &line[..index];
        }
    }
    line
}

fn parse_quoted_strings(text: &str) -> Vec<String> {
    let mut values = Vec::new();
    let mut chars = text.char_indices().peekable();
    while let Some((_, value)) = chars.next() {
        if value != '"' {
            continue;
        }

        let mut raw = String::new();
        while let Some((_, value)) = chars.next() {
            if value == '"' {
                break;
            }
            if value == '\\' {
                if let Some((_, escaped)) = chars.peek().copied() {
                    raw.push(escaped);
                    chars.next();
                    continue;
                }
            }
            raw.push(value);
        }
        values.push(raw);
    }
    values
}

fn starts_with_keyword(text: &str, keyword: &str) -> bool {
    let text = text.trim_start();
    text.len() >= keyword.len()
        && text[..keyword.len()].eq_ignore_ascii_case(keyword)
        && text[keyword.len()..]
            .chars()
            .next()
            .is_none_or(|value| value.is_whitespace() || value == '"' || value == '(')
}

fn split_dot_command(line: &str) -> Option<(String, String)> {
    let (target, rest) = split_dot_command_rest(line)?;
    let command = rest
        .split(|value: char| value.is_whitespace() || value == ':' || value == '(')
        .next()
        .unwrap_or_default();
    if command.is_empty() {
        None
    } else {
        Some((target, command.to_owned()))
    }
}

fn split_dot_command_rest(line: &str) -> Option<(String, &str)> {
    let (target, rest) = line.split_once('.')?;
    Some((target.trim().to_owned(), rest.trim()))
}

fn contains_keyword(text: &str, keyword: &str) -> bool {
    text.split(|value: char| !value.is_ascii_alphanumeric() && value != '_')
        .any(|part| part.eq_ignore_ascii_case(keyword))
}

fn unsupported(line: &str) -> Statement {
    Statement::Unsupported {
        text: line.to_owned(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_model_and_looping_animation() {
        let program = parse_program("d=>dragon\nd.fly loop").unwrap();

        assert_eq!(
            program,
            Program {
                statements: vec![
                    Statement::ModelDecl {
                        name: "d".to_owned(),
                        resource: "dragon".to_owned(),
                        options: EntityOptions::default(),
                    },
                    Statement::Animate(AnimationStatement {
                        target: "d".to_owned(),
                        clip: "fly".to_owned(),
                        speed: 1.0,
                        looped: true,
                    }),
                ],
            }
        );
    }

    #[test]
    fn parses_animation_speed_and_quoted_clip() {
        let program = parse_program("d.\"Fly Forward\" at speed of 0.5 loop").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Animate(AnimationStatement {
                target: "d".to_owned(),
                clip: "Fly Forward".to_owned(),
                speed: 0.5,
                looped: true,
            })]
        );
    }

    #[test]
    fn parses_add_code_statement_for_runtime_jit_parsing() {
        let program = parse_program("Add \"levels/intro.code\" Code").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::AddCode {
                path: "levels/intro.code".to_owned(),
            }]
        );
    }

    #[test]
    fn parses_multifile_add_code_statement() {
        let program =
            parse_program("add \"game_init.code\",\n    \"/game_input/input\" code").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::AddCode {
                    path: "game_init.code".to_owned(),
                },
                Statement::AddCode {
                    path: "/game_input/input".to_owned(),
                },
            ]
        );
    }

    #[test]
    fn parses_designer_model_declaration_options() {
        let program = parse_program(
            "player1 => dynamic fighter1_native : hidden, pos (40.0,-89.25,30.0), scale 3.0, rotate(0.0,173.0,-0.0)",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::ModelDecl {
                name: "player1".to_owned(),
                resource: "fighter1_native".to_owned(),
                options: EntityOptions {
                    position: Some(SceneMaxVec3 {
                        x: 40.0,
                        y: -89.25,
                        z: 30.0,
                    }),
                    rotation_degrees: Some(SceneMaxVec3 {
                        x: 0.0,
                        y: 173.0,
                        z: -0.0,
                    }),
                    scale: Some(SceneMaxVec3 {
                        x: 3.0,
                        y: 3.0,
                        z: 3.0,
                    }),
                    size: None,
                    hidden: true,
                },
            }]
        );
    }

    #[test]
    fn parses_switch_and_camera_commands() {
        let program = parse_program(
            "switch to \"game_intro\"\ncamera.pos(0.0,2.0,10.0)\ncamera.rotate(0,180,0)",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::SwitchTo {
                    scene: "game_intro".to_owned(),
                },
                Statement::CameraPosition(SceneMaxVec3 {
                    x: 0.0,
                    y: 2.0,
                    z: 10.0,
                }),
                Statement::CameraRotation(SceneMaxVec3 {
                    x: 0.0,
                    y: 180.0,
                    z: 0.0,
                }),
            ]
        );
    }

    #[test]
    fn parses_wait_for_key() {
        let program = parse_program("wait for key space to be pressed").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WaitForKey {
                key: "space".to_owned(),
            }]
        );
    }

    #[test]
    fn parses_visibility_look_at_turn_and_size() {
        let program = parse_program(
            "floor => static box : size (100.0,1.0,100.0), pos (0.0,-15.0,0.0)\nplayer1.show\nplayer1.look at (player2)\nring.turn left 360 in 50 seconds async",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ModelDecl {
                    name: "floor".to_owned(),
                    resource: "box".to_owned(),
                    options: EntityOptions {
                        position: Some(SceneMaxVec3 {
                            x: 0.0,
                            y: -15.0,
                            z: 0.0,
                        }),
                        rotation_degrees: None,
                        scale: None,
                        size: Some(SceneMaxVec3 {
                            x: 100.0,
                            y: 1.0,
                            z: 100.0,
                        }),
                        hidden: false,
                    },
                },
                Statement::Visibility {
                    target: "player1".to_owned(),
                    visible: true,
                },
                Statement::LookAt {
                    target: "player1".to_owned(),
                    subject: "player2".to_owned(),
                },
                Statement::Turn(TurnStatement {
                    target: "ring".to_owned(),
                    degrees: 360.0,
                    duration_seconds: 50.0,
                }),
            ]
        );
    }

    #[test]
    fn parses_move_statement() {
        let program = parse_program("player1.move forward 0.5 for 0.25 seconds async").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Move(MoveStatement {
                target: "player1".to_owned(),
                direction: MoveDirection::Forward,
                distance: 0.5,
                duration_seconds: 0.25,
            })]
        );
    }

    #[test]
    fn parses_key_event_actions_for_runtime_execution() {
        let program = parse_program(
            "[@allow_move]\nwhen key A is pressed once do async\n  player1.look at (player2)\n  player1.move forward 0.3 for 0.2 seconds async\n  player1.mma_kick1 at speed of 2.5 async\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "a".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                actions: vec![
                    Statement::LookAt {
                        target: "player1".to_owned(),
                        subject: "player2".to_owned(),
                    },
                    Statement::Move(MoveStatement {
                        target: "player1".to_owned(),
                        direction: MoveDirection::Forward,
                        distance: 0.3,
                        duration_seconds: 0.2,
                    }),
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "mma_kick1".to_owned(),
                        speed: 2.5,
                        looped: false,
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_key_event_if_else_without_flattening_branches() {
        let program = parse_program(
            "when key X is pressed once do\n  player1.pull_start at speed of 4\n  if (player2.data.trapped == 1) {\n    if(rnd(3)==0) {\n      player1.turn left 360 in 0.5 seconds async\n    }\n    player1.kip_up\n  } else {\n    player1.idle2 loop\n  }\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "x".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                actions: vec![
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "pull_start".to_owned(),
                        speed: 4.0,
                        looped: false,
                    }),
                    Statement::If(IfStatement {
                        condition: Condition::EqualsNumber {
                            name: "player2.data.trapped".to_owned(),
                            value: 1.0,
                        },
                        actions: vec![Statement::Animate(AnimationStatement {
                            target: "player1".to_owned(),
                            clip: "kip_up".to_owned(),
                            speed: 1.0,
                            looped: false,
                        })],
                        else_actions: vec![Statement::Animate(AnimationStatement {
                            target: "player1".to_owned(),
                            clip: "idle2".to_owned(),
                            speed: 1.0,
                            looped: true,
                        })],
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_numeric_and_symbolic_assignments() {
        let program = parse_program("move_forward=1\naction=PLAYER_ACTION_IDLE").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Assignment(AssignmentStatement {
                    name: "move_forward".to_owned(),
                    value: AssignmentValue::Number(1.0),
                }),
                Statement::Assignment(AssignmentStatement {
                    name: "action".to_owned(),
                    value: AssignmentValue::Symbol("PLAYER_ACTION_IDLE".to_owned()),
                }),
            ]
        );
    }

    #[test]
    fn parses_comma_separated_constants() {
        let program = parse_program("var PLAYER_ACTION_X_1 = 7, PLAYER_ACTION_X_2 = 8,").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Assignment(AssignmentStatement {
                    name: "PLAYER_ACTION_X_1".to_owned(),
                    value: AssignmentValue::Number(7.0),
                }),
                Statement::Assignment(AssignmentStatement {
                    name: "PLAYER_ACTION_X_2".to_owned(),
                    value: AssignmentValue::Number(8.0),
                }),
            ]
        );
    }

    #[test]
    fn parses_wait_and_async_actions() {
        let program = parse_program(
            "when key Z is pressed once do\n  do async\n    wait 0.3 seconds\n    action=PLAYER_ACTION_IDLE\n  end do\n  player1.duck_right1 async\n  wait 1 second\n  player1.idle2 loop\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "z".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                actions: vec![
                    Statement::Async {
                        actions: vec![
                            Statement::Wait { seconds: 0.3 },
                            Statement::Assignment(AssignmentStatement {
                                name: "action".to_owned(),
                                value: AssignmentValue::Symbol("PLAYER_ACTION_IDLE".to_owned()),
                            }),
                        ],
                    },
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "duck_right1".to_owned(),
                        speed: 1.0,
                        looped: false,
                    }),
                    Statement::Wait { seconds: 1.0 },
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "idle2".to_owned(),
                        speed: 1.0,
                        looped: true,
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_dotted_numeric_assignment_without_treating_it_as_animation() {
        let program = parse_program("player1.data.attack_legs = 1").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Assignment(AssignmentStatement {
                name: "player1.data.attack_legs".to_owned(),
                value: AssignmentValue::Number(1.0),
            })]
        );
    }

    #[test]
    fn parses_simple_when_condition_actions() {
        let program = parse_program(
            "when move_forward == 1 do\n  player1.\"run_sword\" loop\n  player1.move forward 0.2 for 0.5 seconds\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WhenEvent(WhenEventStatement {
                condition: Condition::EqualsNumber {
                    name: "move_forward".to_owned(),
                    value: 1.0,
                },
                actions: vec![
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "run_sword".to_owned(),
                        speed: 1.0,
                        looped: true,
                    }),
                    Statement::Move(MoveStatement {
                        target: "player1".to_owned(),
                        direction: MoveDirection::Forward,
                        distance: 0.2,
                        duration_seconds: 0.5,
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_dotted_when_condition() {
        let program = parse_program(
            "when player1.data.attack_legs == 1 do\n  player1.leg_takedown_attacker at speed of 2.5\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WhenEvent(WhenEventStatement {
                condition: Condition::EqualsNumber {
                    name: "player1.data.attack_legs".to_owned(),
                    value: 1.0,
                },
                actions: vec![Statement::Animate(AnimationStatement {
                    target: "player1".to_owned(),
                    clip: "leg_takedown_attacker".to_owned(),
                    speed: 2.5,
                    looped: false,
                })],
            })]
        );
    }

    #[test]
    fn parses_fighting_camera_system_declaration() {
        let program = parse_program(
            "fight_cam = camera.system.fighting(player1, player2, depth 18, height 3, side 1.5, min distance 10, max distance 28, damping 8)",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FightingCamera(FightingCameraStatement {
                name: "fight_cam".to_owned(),
                target_a: "player1".to_owned(),
                target_b: "player2".to_owned(),
                depth: 18.0,
                height: 3.0,
                side: 1.5,
                min_distance: 10.0,
                max_distance: 28.0,
                damping: 8.0,
            })]
        );
    }

    #[test]
    fn parses_function_definition_and_run_command() {
        let program = parse_program(
            "game_start = {\n  boss.show\n  boss.Idle loop\n  run set_camera_on_player\n}\nrun game_start",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "game_start".to_owned(),
                    actions: vec![
                        Statement::Visibility {
                            target: "boss".to_owned(),
                            visible: true,
                        },
                        Statement::Animate(AnimationStatement {
                            target: "boss".to_owned(),
                            clip: "Idle".to_owned(),
                            speed: 1.0,
                            looped: true,
                        }),
                        Statement::RunFunction {
                            name: "set_camera_on_player".to_owned(),
                        },
                    ],
                }),
                Statement::RunFunction {
                    name: "game_start".to_owned(),
                },
            ]
        );
    }

    #[test]
    fn parses_camera_system_selection() {
        let program = parse_program("camera.system = fight_cam").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::CameraSystemSelect {
                name: "fight_cam".to_owned(),
            }]
        );
    }

    #[test]
    fn function_block_handles_else_lines_without_swallowing_next_statement() {
        let program = parse_program(
            "set_camera_on_player = {\n  if(enemy_ko==1) {\n    camera.system = crystal_hunt_cam\n  } else {\n    camera.system = fight_cam\n  }\n}\nrun set_camera_on_player",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "set_camera_on_player".to_owned(),
                    actions: vec![Statement::If(IfStatement {
                        condition: Condition::EqualsNumber {
                            name: "enemy_ko".to_owned(),
                            value: 1.0,
                        },
                        actions: vec![Statement::CameraSystemSelect {
                            name: "crystal_hunt_cam".to_owned(),
                        }],
                        else_actions: vec![Statement::CameraSystemSelect {
                            name: "fight_cam".to_owned(),
                        }],
                    })],
                }),
                Statement::RunFunction {
                    name: "set_camera_on_player".to_owned(),
                },
            ]
        );
    }

    #[test]
    fn does_not_extract_statements_inside_deferred_blocks() {
        let program =
            parse_program("enemy_knockout = {\nwin1 => you_win1 sprite : scale 3\n}\nplayer1.show")
                .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "enemy_knockout".to_owned(),
                    actions: Vec::new(),
                }),
                Statement::Visibility {
                    target: "player1".to_owned(),
                    visible: true,
                },
            ]
        );
    }

    #[test]
    fn leaves_non_model_entity_systems_unsupported_for_now() {
        let program = parse_program(
            "intro_camera=>cinematic.camera.cinematic_rig_1\nthrow_text => throw_text sprite : hidden\nvid=>videos.foggy_day1\nrocks => Object.Pool(create_rock, size 5)",
        )
        .unwrap();

        assert!(
            program
                .statements
                .iter()
                .all(|statement| matches!(statement, Statement::Unsupported { .. }))
        );
    }
}
