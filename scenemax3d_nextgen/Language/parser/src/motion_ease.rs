//! Optional easing suffix shared by entity movement and rotation commands.
use super::*;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EaseMode {
    In,
    Out,
    InOut,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MotionEase {
    pub mode: EaseMode,
    pub family: String,
    pub params: Vec<AssignmentValue>,
    /// Runtime snapshot phase; never accepted from source text.
    pub start_progress: f32,
}

pub(super) fn parse(text: &str) -> Result<Option<MotionEase>, ParseError> {
    let Some((_, end)) = find_case_insensitive_span(text, " ease ") else {
        return Ok(None);
    };
    let tail = take_until_clause(text[end..].trim(), &[" async", " loop ", " while "]);
    let first_end = tail.find(char::is_whitespace).unwrap_or(tail.len());
    let first = &tail[..first_end];
    let mut rest = tail[first_end..].trim();
    let mode = if first.eq_ignore_ascii_case("in") {
        let next_end = rest.find(char::is_whitespace).unwrap_or(rest.len());
        if rest[..next_end].eq_ignore_ascii_case("out") {
            rest = rest[next_end..].trim();
            EaseMode::InOut
        } else {
            EaseMode::In
        }
    } else if first.eq_ignore_ascii_case("out") {
        EaseMode::Out
    } else {
        return Err(ParseError::InvalidNumber(tail.into()));
    };
    let mut family = "quad".to_owned();
    if let Some(quoted) = rest.strip_prefix('"') {
        let Some(end) = quoted.find('"') else {
            return Err(ParseError::InvalidNumber(tail.into()));
        };
        family = quoted[..end].into();
        rest = quoted[end + 1..].trim();
    }
    let mut params = Vec::new();
    if let Some(inner) = rest.strip_prefix('(') {
        let Some(end) = matching_close_paren_index(inner) else {
            return Err(ParseError::InvalidNumber(tail.into()));
        };
        if !inner[..end].trim().is_empty() {
            let mut depth = 0usize;
            let mut start = 0;
            for (index, ch) in inner[..end].char_indices() {
                match ch {
                    '(' | '[' => depth += 1,
                    ')' | ']' => depth = depth.saturating_sub(1),
                    ',' if depth == 0 => {
                        if inner[start..index].trim().is_empty() {
                            return Err(ParseError::InvalidNumber(tail.into()));
                        }
                        start = index + 1;
                    }
                    _ => {}
                }
            }
            if inner[start..end].trim().is_empty() {
                return Err(ParseError::InvalidNumber(tail.into()));
            }
            for raw in split_top_level_comma(&inner[..end]) {
                params.push(
                    parse_assignment_value(raw)?
                        .ok_or_else(|| ParseError::InvalidNumber(raw.into()))?,
                );
            }
        }
        rest = inner[end + 1..].trim();
    }
    if !rest.is_empty() || params.len() > 5 {
        return Err(ParseError::InvalidNumber(tail.into()));
    }
    Ok(Some(MotionEase {
        mode,
        family,
        params,
        start_progress: 0.,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn easing_suffixes_preserve_motion_expressions_and_flags() {
        for command in [
            "move (x + (amount + 1) * 2)",
            "move forward amount",
            "move to (1,2,3)",
            "turn left angle",
            "rotate (z - angle)",
            "rotate to (y angle)",
        ] {
            let program = parse_program(&format!("subject.{command} in duration seconds ease in out \"Power\" (exponent + 1, window / 2) async")).unwrap();
            let (ease, duration, asynchronous) = match &program.statements[0] {
                Statement::Move(a) => (&a.easing, &a.duration_value, a.async_run),
                Statement::MoveTo(a) => (&a.easing, &a.duration_value, a.async_run),
                Statement::Turn(a) => (&a.easing, &a.duration_value, a.async_run),
                _ => panic!(),
            };
            let ease = ease.as_ref().unwrap();
            assert_eq!(ease.mode, EaseMode::InOut);
            assert_eq!(ease.family, "Power");
            assert_eq!(ease.params.len(), 2);
            assert_eq!(ease.start_progress, 0.);
            assert_eq!(duration, &AssignmentValue::Symbol("duration".into()));
            assert!(asynchronous);
        }
        let spaced =
            parse_program("subject.move (x + 2) in 2 seconds ease IN   OUT \"Sine\"").unwrap();
        assert!(
            matches!(&spaced.statements[0], Statement::Move(a) if a.easing.as_ref().unwrap().mode == EaseMode::InOut)
        );
        let p = parse_program("subject.move (y + 2) ease in").unwrap();
        assert!(
            matches!(&p.statements[0], Statement::Move(a) if a.duration_seconds == 1. && a.easing.is_some())
        );
        assert!(parse_program("subject.move (x + 1) in 2 seconds ease sideways").is_err());
        assert!(
            parse_program("subject.turn left 90 in 2 seconds ease in \"Power\" (1,,2)").is_err()
        );
    }
}
