//! Playback of packed position and Euler-angle samples.
use super::*;

#[derive(Debug, Clone, PartialEq)]
pub enum Action {
    Play {
        array: String,
        duration: AssignmentValue,
        start: Option<AssignmentValue>,
        offsets: Vec<(usize, AssignmentValue)>,
        repeat: Option<Condition>,
    },
    Pause,
    Resume,
    Stop,
    Speed(AssignmentValue),
    Switch(String),
}

fn invalid(text: &str) -> ParseError {
    ParseError::InvalidNumber(format!("Invalid path replay: {text}"))
}

fn expr(text: &str) -> Result<AssignmentValue, ParseError> {
    parse_assignment_value(text.trim())?.ok_or_else(|| invalid(text))
}

fn seconds(text: &str) -> Result<AssignmentValue, ParseError> {
    let (value, unit) = text
        .trim()
        .rsplit_once(char::is_whitespace)
        .ok_or_else(|| invalid(text))?;
    if !unit.eq_ignore_ascii_case("seconds") && !unit.eq_ignore_ascii_case("second") {
        return Err(invalid(text));
    }
    expr(value)
}

pub(super) fn parse(line: &str) -> Result<Option<Statement>, ParseError> {
    let Some((target, tail)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let Some((verb, args)) = tail.split_once(char::is_whitespace) else {
        return Ok(None);
    };
    if !verb.eq_ignore_ascii_case("replay") || !is_variable_path(&target) {
        return Ok(None);
    }
    let args = args.trim();
    // Animation clips can also be named "replay" and take normal playback modifiers.
    if ["async", "loop", "loop async"]
        .iter()
        .any(|modifier| args.eq_ignore_ascii_case(modifier))
    {
        return Ok(None);
    }
    let args = args
        .rsplit_once(char::is_whitespace)
        .filter(|(_, word)| word.eq_ignore_ascii_case("async"))
        .map_or(args, |(s, _)| s.trim_end());
    let action = if args.eq_ignore_ascii_case("stop") {
        Action::Stop
    } else if args.eq_ignore_ascii_case("pause") {
        Action::Pause
    } else if args.eq_ignore_ascii_case("resume") {
        Action::Resume
    } else if let Some(("", value)) = split_once_case_insensitive(args, "speed ") {
        Action::Speed(seconds(value)?)
    } else if let Some(("", name)) = split_once_case_insensitive(args, "switch to ") {
        if !is_variable_path(name.trim()) {
            return Err(invalid(args));
        }
        Action::Switch(name.trim().to_owned())
    } else {
        let (timing, options) = args.split_once(':').unwrap_or((args, ""));
        let (timing, repeat) = if let Some((before, condition)) =
            split_once_case_insensitive(timing, " loop while ")
        {
            (
                before,
                Some(parse_condition(condition.trim())?.ok_or_else(|| invalid(condition))?),
            )
        } else {
            (timing, None)
        };
        let (from, time) =
            split_once_case_insensitive(timing, " in ").ok_or_else(|| invalid(args))?;
        let (name, start) =
            if let Some((name, index)) = split_once_case_insensitive(from, " start at ") {
                (name.trim(), Some(expr(index)?))
            } else {
                (from.trim(), None)
            };
        if !is_variable_path(name) {
            return Err(invalid(name));
        }
        let mut offsets = Vec::new();
        let mut remaining = options.trim();
        while !remaining.is_empty() {
            let (part, rest) =
                split_once_case_insensitive(remaining, " and ").unwrap_or((remaining, ""));
            let (axis, value) =
                split_once_case_insensitive(part, " offset ").ok_or_else(|| invalid(part))?;
            let field = ["x", "y", "z", "rx", "ry", "rz"]
                .iter()
                .position(|s| s.eq_ignore_ascii_case(axis.trim()))
                .ok_or_else(|| invalid(axis))?;
            offsets.push((field, expr(value)?));
            remaining = rest.trim();
        }
        Action::Play {
            array: name.to_owned(),
            duration: seconds(time)?,
            start,
            offsets,
            repeat,
        }
    };
    Ok(Some(Statement::PathReplay {
        target,
        action: Box::new(action),
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn replay_in_function_is_a_path_action() {
        let program = parse_program("fly = {\n var pts = [0,0,0,0,0,0]\n actor.replay pts in 3 seconds loop while 1==1\n}\n").unwrap();
        assert!(format!("{program:?}").contains("PathReplay"));
        assert!(
            matches!(parse("actor.replay pts start at 2 in 4 seconds : y offset 3 and rz offset -5").unwrap(), Some(Statement::PathReplay{action,..}) if matches!(*action, Action::Play{start:Some(_),ref offsets,..} if offsets.len()==2))
        );
    }
}
