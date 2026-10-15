//! Controller rewind requests and self-contained playback trajectories.
use super::*;
#[derive(Debug, Clone, PartialEq)]
pub struct Request {
    pub percent: AssignmentValue,
    pub duration: AssignmentValue,
    pub easing: Option<motion_ease::MotionEase>,
}
#[derive(Debug, Clone, PartialEq)]
pub struct Playback {
    pub start: f32,
    pub target: f32,
    pub duration: f32,
    pub easing: Option<motion_ease::MotionEase>,
}
pub(super) fn parse_playback(text: &str) -> Result<Option<Playback>, ParseError> {
    let padded = format!(" {text}");
    let text = padded.as_str();
    let Some((_, end)) = find_case_insensitive_span(text, " rewind from ") else {
        return Ok(None);
    };
    let text = &text[end..];
    let Some((split, end)) = find_case_insensitive_span(text, " to ") else {
        return Err(ParseError::InvalidNumber(text.into()));
    };
    let start = text[..split]
        .trim()
        .parse::<f32>()
        .map_err(|_| ParseError::InvalidNumber(text.into()))?;
    let tail = &text[end..];
    let Some((split, _)) = find_case_insensitive_span(tail, " in ") else {
        return Err(ParseError::InvalidNumber(text.into()));
    };
    let target = tail[..split]
        .trim()
        .parse::<f32>()
        .map_err(|_| ParseError::InvalidNumber(text.into()))?;
    let duration = parse_duration_assignment_value(tail, " in ")?
        .and_then(|(v, _)| static_assignment_number(&v))
        .ok_or_else(|| ParseError::InvalidNumber(text.into()))?;
    if !start.is_finite()
        || !target.is_finite()
        || !duration.is_finite()
        || target < 0.
        || start < target
        || duration < 0.
    {
        return Err(ParseError::InvalidNumber(text.into()));
    }
    let easing = motion_ease::parse(text)?;
    if easing.as_ref().is_some_and(|e| {
        e.params
            .iter()
            .any(|v| !matches!(v,AssignmentValue::Number(n) if n.is_finite()))
    }) {
        return Err(ParseError::InvalidNumber(text.into()));
    }
    Ok(Some(Playback {
        start,
        target,
        duration,
        easing,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn controller_requests_and_playback_wire_parse_without_losing_easing() {
        let p = parse_program(
            "control.rewind amount in seconds_count seconds ease out \"Back\" (overshoot)",
        )
        .unwrap();
        let Statement::AnimationControllerAction(a) = &p.statements[0] else {
            panic!()
        };
        assert_eq!(a.action, AnimationControllerAction::Rewind);
        let r = a.rewind.as_ref().unwrap();
        assert_eq!(r.percent, AssignmentValue::Symbol("amount".into()));
        assert_eq!(r.duration, AssignmentValue::Symbol("seconds_count".into()));
        assert_eq!(
            r.easing.as_ref().unwrap().params,
            vec![AssignmentValue::Symbol("overshoot".into())]
        );
        let p=parse_program("actor.\"Long take\"[\"part\"] at speed of 0.5 rewind from 1.2 to 0.3 in 4 seconds ease in \"Cubic\"").unwrap();
        let Statement::Animate(a) = &p.statements[0] else {
            panic!()
        };
        assert_eq!(a.speed, 0.5);
        assert_eq!(a.rewind.as_ref().unwrap().target, 0.3);
        for source in [
            "control.rewind 30",
            "control.rewind in 2 seconds",
            "actor.take rewind from NaN to 0 in 1 seconds",
            "actor.take rewind from 0 to 1 in 2 seconds",
        ] {
            assert!(parse_program(source).is_err(), "{source}");
        }
    }
}
