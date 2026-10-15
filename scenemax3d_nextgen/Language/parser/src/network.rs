//! Multiplayer statements. Expressions keep their source spelling so strings,
//! arrays and numeric expressions retain their distinct value types.
use super::*;

#[derive(Debug, Clone, PartialEq)]
pub enum NetworkStatement {
    EntityData {
        target: String,
        field: String,
        value: String,
    },
    Variable {
        name: String,
        value: String,
    },
    Broadcast {
        event: String,
        message: Option<String>,
    },
    Send {
        recipient: Option<String>,
        event: String,
        message: Option<String>,
    },
    JoinSession {
        value: String,
    },
    On {
        /// Authored handler identity, retained when actions are cloned.
        handler_id: u64,
        event: String,
        argument: Option<String>,
        actions: Vec<Statement>,
    },
}

pub(super) fn parse(line: &str) -> Result<Option<NetworkStatement>, ParseError> {
    let trimmed = line.trim().trim_end_matches(';').trim();
    let lower = trimmed.to_ascii_lowercase();
    if let Some((left, value)) = trimmed.split_once('=') {
        if let Some(left) = left.trim().strip_suffix('#') {
            if let Some((target, field)) = left.split_once(".data.") {
                if is_variable_path(target.trim()) && is_variable_path(field.trim()) {
                    return Ok(Some(NetworkStatement::EntityData {
                        target: target.trim().into(),
                        field: field.trim().into(),
                        value: value.trim().into(),
                    }));
                }
            }
        }
    }
    if lower.starts_with("network var ") {
        let (name, value) = trimmed[12..]
            .split_once('=')
            .ok_or_else(|| ParseError::InvalidNetwork("network var requires a value".into()))?;
        if !is_variable_path(name.trim()) {
            return Err(ParseError::InvalidNetwork("invalid variable name".into()));
        }
        return Ok(Some(NetworkStatement::Variable {
            name: name.trim().into(),
            value: value.trim().into(),
        }));
    }
    if let Some(rest) = lower.strip_prefix("network.join session") {
        let value = trimmed[trimmed.len() - rest.len()..].trim();
        let value = value
            .strip_prefix('(')
            .and_then(|s| s.strip_suffix(')'))
            .unwrap_or(value)
            .trim();
        if value.is_empty() {
            return Err(ParseError::InvalidNetwork("missing session".into()));
        }
        return Ok(Some(NetworkStatement::JoinSession {
            value: value.into(),
        }));
    }
    if lower.starts_with("network.send") && trimmed[12..].starts_with(char::is_whitespace) {
        let event = trimmed[12..].trim();
        if event.is_empty() {
            return Err(ParseError::InvalidNetwork(
                "network.send requires an event".into(),
            ));
        }
        if !event.starts_with('(') {
            return Ok(Some(NetworkStatement::Send {
                recipient: None,
                event: event.into(),
                message: None,
            }));
        }
    }
    let Some(open) = trimmed.find('(') else {
        return Ok(None);
    };
    let head = trimmed[..open].trim().to_ascii_lowercase();
    if head != "network.broadcast" && head != "network.send" && !head.ends_with(".network.send") {
        return Ok(None);
    }
    let body = trimmed[open + 1..]
        .strip_suffix(')')
        .ok_or_else(|| ParseError::InvalidNetwork("unclosed network call".into()))?;
    let args = split_top_level_comma(body);
    if args.is_empty() || args.len() > 2 || args.iter().any(|s| s.trim().is_empty()) {
        return Err(ParseError::InvalidNetwork(
            "network call expects event and optional message".into(),
        ));
    }
    let event = args[0].trim().into();
    let message = args.get(1).map(|s| s.trim().into());
    Ok(Some(if head == "network.broadcast" {
        NetworkStatement::Broadcast { event, message }
    } else {
        let recipient = if head == "network.send" {
            None
        } else {
            Some(trimmed[..open].trim()[..head.len() - 13].into())
        };
        NetworkStatement::Send {
            recipient,
            event,
            message,
        }
    }))
}

pub(super) fn block(
    lines: &[String],
    index: usize,
) -> Result<Option<(Statement, usize)>, ParseError> {
    let line = lines[index].trim();
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("network.on") {
        return Ok(None);
    }
    let Some(open) = line.find('(') else {
        return Err(ParseError::InvalidNetwork("missing event arguments".into()));
    };
    let tail = &line[open + 1..];
    let close = matching_close_paren_index(tail)
        .ok_or_else(|| ParseError::InvalidNetwork("unclosed event arguments".into()))?;
    let suffix = tail[close + 1..].trim();
    if !matches!(
        suffix.to_ascii_lowercase().as_str(),
        "= do" | "=do" | "do" | "= {"
    ) {
        return Err(ParseError::InvalidNetwork("event requires do block".into()));
    }
    let args = split_top_level_comma(&tail[..close]);
    if args.is_empty() || args.len() > 2 || args[0].trim().is_empty() {
        return Err(ParseError::InvalidNetwork(
            "event expects name and optional interval/message argument".into(),
        ));
    }
    let (actions, next) = parse_action_block(lines, index + 1, false)?;
    Ok(Some((
        Statement::Network(NetworkStatement::On {
            handler_id: next_handler_id(),
            event: args[0].trim().into(),
            argument: args.get(1).map(|s| s.trim().into()),
            actions,
        }),
        next,
    )))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn nested_network_handlers_keep_function_and_branch_boundaries() {
        let program = parse_program(r#"
setup() = {
 var count = 0
 network.on ("first", message) = do
  count = count + 1
 end do
 if true {
  network.on ("second", message) = do
   count = count + 2
  end do
 }
 count = 10
}
run setup()
"#).unwrap();
        assert_eq!(program.statements.len(), 2);
        let Statement::FunctionDef(function) = &program.statements[0] else { panic!("function"); };
        assert_eq!(function.actions.len(), 4);
        assert!(matches!(&function.actions[1], Statement::Network(NetworkStatement::On {actions,..}) if actions.len() == 1));
        let Statement::If(branch) = &function.actions[2] else { panic!("branch"); };
        assert!(matches!(&branch.actions[0], Statement::Network(NetworkStatement::On {actions,..}) if actions.len() == 1));
        assert!(matches!(&function.actions[3], Statement::Assignment(a) if a.name == "count"));
    }
    #[test]
    fn java_collision_send_and_quoted_joint_names_are_preserved() {
        let program = parse_program("when local.\"Hand Joint\" collides with #authored.\"Target Joint\" do\nnetwork.send \"contact\"\nend do").unwrap();
        let Statement::WhenEvent(event) = &program.statements[0] else {
            panic!("expected when")
        };
        assert_eq!(
            event.condition,
            Condition::Collision {
                sources: vec!["local.\"Hand Joint\"".into()],
                target: "#authored.\"Target Joint\"".into()
            }
        );
        assert!(
            matches!(&event.actions[0], Statement::Network(NetworkStatement::Send { recipient: None, event, .. }) if event == "\"contact\"")
        );
    }
    #[test]
    fn parses_network_calls_values_and_nested_handlers() {
        let source = "network var values = [1, \"two\", [true, null]]\nnetwork.broadcast (\"event\", \"hello\")\nnetwork.on (\"tick\", 5) = do\n network.broadcast (\"reply\")\nend do\nnetwork.join session (1000)";
        let program = parse_program(source).unwrap();
        assert_eq!(program.statements.len(), 4);
        assert!(
            matches!(&program.statements[0],Statement::Network(NetworkStatement::Variable {name,..}) if name=="values")
        );
        assert!(
            matches!(&program.statements[2],Statement::Network(NetworkStatement::On {actions,..}) if actions.len()==1)
        );
        assert!(
            matches!(parse("object.network.send (\"hit\", 5)").unwrap(),Some(NetworkStatement::Send {recipient:Some(ref s),..}) if s=="object")
        );
    }
    #[test]
    fn preserves_multiplayer_declarations() {
        let p = parse_program("fixture => box: multiplayer, size (1,2,3)").unwrap();
        assert!(
            matches!(&p.statements[0],Statement::ModelDecl {options,..} if options.multiplayer && options.multiplayer_source.as_deref()==Some("box: multiplayer, size (1,2,3)"))
        );
    }
}

fn next_handler_id() -> u64 {
    static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);
    NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
}
