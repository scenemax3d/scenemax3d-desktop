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

#[derive(Debug, Error, PartialEq)]
pub enum ParseError {
    #[error("invalid number '{0}'")]
    InvalidNumber(String),
}

pub fn parse_program(source: &str) -> Result<Program, ParseError> {
    let logical_lines = logical_lines(source);
    let mut statements = Vec::new();
    let mut index = 0;

    while index < logical_lines.len() {
        let line = logical_lines[index].trim();
        if line.is_empty() {
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

        statements.push(parse_statement(line)?);
        index += 1;
    }

    Ok(Program { statements })
}

fn parse_statement(line: &str) -> Result<Statement, ParseError> {
    if let Some(scene) = parse_switch(line) {
        return Ok(Statement::SwitchTo { scene });
    }

    if let Some(key) = parse_wait_for_key(line) {
        return Ok(Statement::WaitForKey { key });
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

    if let Some(animation) = parse_animation(line)? {
        return Ok(Statement::Animate(animation));
    }

    Ok(unsupported(line))
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

fn parse_camera_command(line: &str, command: &str) -> Result<Option<SceneMaxVec3>, ParseError> {
    if !line.to_ascii_lowercase().starts_with(command) {
        return Ok(None);
    }
    parse_vec3_after(line, command).map(Some)
}

fn parse_model_decl(line: &str) -> Result<Option<Statement>, ParseError> {
    if let Some((name, rest)) = line.split_once("=>") {
        let (resource, options_text) = split_resource_and_options(rest);
        return Ok(Some(Statement::ModelDecl {
            name: name.trim().to_owned(),
            resource: normalize_resource(resource),
            options: parse_entity_options(options_text)?,
        }));
    }

    let lower = line.to_ascii_lowercase();
    if let Some(index) = lower.find(" is a ") {
        let name = line[..index].trim();
        let rest = &line[index + " is a ".len()..];
        let (resource, options_text) = split_resource_and_options(rest);
        return Ok(Some(Statement::ModelDecl {
            name: name.to_owned(),
            resource: normalize_resource(resource),
            options: parse_entity_options(options_text)?,
        }));
    }

    Ok(None)
}

fn parse_animation(line: &str) -> Result<Option<AnimationStatement>, ParseError> {
    let Some((target, rest)) = line.split_once('.') else {
        return Ok(None);
    };
    let target = target.trim();
    if target.is_empty()
        || target.eq_ignore_ascii_case("camera")
        || target.eq_ignore_ascii_case("scene")
        || target.eq_ignore_ascii_case("ui")
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
}
