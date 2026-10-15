//! IK assets and independently controlled constraint layers.
use super::*;
#[derive(Debug, Clone, PartialEq)]
pub struct IkStatement {
    pub owner: String,
    pub layer: Option<String>,
    pub action: IkAction,
    pub target: Option<String>,
    pub weight: Option<AssignmentValue>,
    pub blend: Option<AssignmentValue>,
}
#[derive(Debug, Clone, PartialEq)]
pub enum IkAction {
    Apply(AssignmentValue),
    Remove,
    Target,
    Weight,
    Blend,
    Play,
    Stop,
    PositionOffset([f32; 3]),
    RotationOffset([f32; 3]),
    PolePosition([f32; 3]),
}
pub(super) fn parse(line: &str) -> Result<Option<IkStatement>, ParseError> {
    let Some((owner, rest)) = line.split_once(".ik") else {
        return Ok(None);
    };
    if !is_variable_path(owner.trim()) {
        return Ok(None);
    }
    let mut cmd = IkStatement {
        owner: owner.trim().into(),
        layer: None,
        action: IkAction::Remove,
        target: None,
        weight: None,
        blend: None,
    };
    let expr = |s: &str| {
        let value = s.trim();
        parse_assignment_value(value)
            .map(|v| v.unwrap_or(AssignmentValue::Symbol(value.into())))
    };
    if let Some(value) = rest.trim().strip_prefix('=') {
        if value.trim() != "empty" {
            cmd.action = IkAction::Apply(expr(value)?);
        }
        return Ok(Some(cmd));
    }
    let Some(rest) = rest.strip_prefix('.') else {
        return Ok(None);
    };
    let Some((layer, op)) = rest.split_once('.') else {
        return Ok(None);
    };
    if !is_variable_path(layer) {
        return Ok(None);
    }
    cmd.layer = Some(layer.into());
    if let Some((op, value)) = op.split_once('=') {
        match op.trim() {
            "positionOffset" | "rotationOffset" | "polePosition" => {
                let raw = value.trim();
                let tuple = raw
                    .strip_prefix('(')
                    .and_then(|s| s.strip_suffix(')'))
                    .ok_or_else(|| ParseError::InvalidNumber(raw.into()))?;
                let values = tuple
                    .split(',')
                    .map(|n| n.trim().parse::<f32>())
                    .collect::<Result<Vec<_>, _>>()
                    .map_err(|_| ParseError::InvalidNumber(raw.into()))?;
                if values.len() != 3 || values.iter().any(|n| !n.is_finite() || n.abs() > 1e10) {
                    return Err(ParseError::InvalidNumber(raw.into()));
                }
                let v = [values[0], values[1], values[2]];
                cmd.action = match op.trim() {
                    "positionOffset" => IkAction::PositionOffset(v),
                    "rotationOffset" => IkAction::RotationOffset(v),
                    _ => IkAction::PolePosition(v),
                };
            }
            "target" => {
                cmd.action = IkAction::Target;
                cmd.target = Some(value.trim().into());
            }
            "weight" => {
                cmd.action = IkAction::Weight;
                cmd.weight = Some(expr(value)?);
            }
            "blend" => {
                cmd.action = IkAction::Blend;
                cmd.blend = Some(expr(value)?);
            }
            _ => return Ok(None),
        }
    } else {
        let (op, options) = op.split_once(':').unwrap_or((op, ""));
        cmd.action = match op.trim() {
            "play" => IkAction::Play,
            "stop" => IkAction::Stop,
            _ => return Ok(None),
        };
        for option in split_top_level_comma(options).into_iter().map(str::trim).filter(|s| !s.is_empty()) {
            if let Some(value) = option.strip_prefix("target ") {
                cmd.target = Some(value.trim().into());
            } else if let Some(value) = option.strip_prefix("weight ") {
                cmd.weight = Some(expr(value)?);
            } else if let Some(value) = option.strip_prefix("blend ") {
                cmd.blend = Some(expr(value)?);
            } else {
                return Ok(None);
            }
        }
    }
    Ok(Some(cmd))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn layer_control_values_keep_nested_call_arguments_together() {
        let statement = parse("subject.ik.contact.play: weight amount(0.2,0.3), blend duration(0.1,0.2)").unwrap().unwrap();
        assert!(matches!(statement.weight, Some(AssignmentValue::Call { args, .. }) if args.len() == 2));
        assert!(matches!(statement.blend, Some(AssignmentValue::Call { args, .. }) if args.len() == 2));
    }
    #[test]
    fn parses_ik_in_program() {
        let program=parse_program("rider.ik = \"riding\"\nrider.ik.contact.target = mount.\"socket\"\nrider.ik.contact.play").unwrap();
        assert_eq!(
            program
                .statements
                .iter()
                .filter(|s| matches!(s, Statement::Ik(_)))
                .count(),
            3
        );
    }
    #[test]
    fn parses_asset_and_layer_controls() {
        assert!(
            matches!(parse(r#"rider.ik = "riding""#).unwrap().unwrap().action,IkAction::Apply(AssignmentValue::Literal(serde_json::Value::String(s))) if s=="riding")
        );
        let cmd =
            parse(r#"rider.ik.contact.play : target mount."socket.01", weight 0.75, blend 0.2"#)
                .unwrap()
                .unwrap();
        assert_eq!(cmd.target.as_deref(), Some(r#"mount."socket.01""#));
        assert_eq!(cmd.layer.as_deref(), Some("contact"));
        assert!(matches!(cmd.action, IkAction::Play));
        assert!(matches!(
            parse("rider.ik = empty").unwrap().unwrap().action,
            IkAction::Remove
        ));
    }
}

#[cfg(test)]
mod pose_tests {
    use super::*;
    #[test]
    fn scene_pose_overrides_parse_and_reject_invalid_vectors() {
        for (key, expected) in [
            ("positionOffset", IkAction::PositionOffset([1., -2., 3.])),
            ("rotationOffset", IkAction::RotationOffset([1., -2., 3.])),
            ("polePosition", IkAction::PolePosition([1., -2., 3.])),
        ] {
            let source = format!("subject.ik.contact.{key} = (1, -2, 3)");
            assert_eq!(parse(&source).unwrap().unwrap().action, expected);
            assert!(matches!(
                &parse_program(&source).unwrap().statements[0],
                Statement::Ik(_)
            ));
            for value in ["(1,2)", "(NaN,0,0)", "(1,2,3,4)", "(inf,0,0)"] {
                assert!(parse(&format!("subject.ik.contact.{key} = {value}")).is_err());
            }
        }
    }
}
