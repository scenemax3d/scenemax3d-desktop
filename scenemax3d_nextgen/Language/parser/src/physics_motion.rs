//! Explicit world-space physics commands shared by runtime and authoring previews.
#[derive(Debug, Clone, PartialEq)]
pub enum Action {
    Velocity([f32; 3]),
    AngularVelocity([f32; 3]),
    Impulse([f32; 3]),
    Force([f32; 3], Option<f32>),
    Torque([f32; 3], bool),
    Configure {
        mass: f32,
        drag: f32,
        gravity: f32,
        restitution: f32,
        friction: f32,
    },
}
/// Parse the explicit-vector extensions. Legacy directional syntax remains with its original parser.
pub fn parse(text: &str) -> Result<Option<Action>, String> {
    let lower = text.trim().to_ascii_lowercase();
    let Some(rest) = lower.strip_prefix("physics ") else {
        return Ok(None);
    };
    if let Some(rest) = rest.strip_prefix("configure ") {
        let words: Vec<_> = rest.split_whitespace().collect();
        if words.len() != 10
            || words.iter().step_by(2).copied().collect::<Vec<_>>()
                != ["mass", "drag", "gravity", "restitution", "friction"]
        {
            return Err(
                "physics configure requires mass, drag, gravity, restitution and friction".into(),
            );
        }
        let numbers: Result<Vec<f32>, _> = words
            .iter()
            .skip(1)
            .step_by(2)
            .map(|v| v.parse::<f32>())
            .collect();
        let v = numbers.map_err(|_| "Invalid physics configuration")?;
        if v.iter().any(|v| !v.is_finite())
            || v[0] <= 0.
            || v[1] < 0.
            || v[2] < 0.
            || !(0. ..=1.).contains(&v[3])
            || v[4] < 0.
        {
            return Err("Physics configuration is outside its valid range".into());
        }
        return Ok(Some(Action::Configure {
            mass: v[0],
            drag: v[1],
            gravity: v[2],
            restitution: v[3],
            friction: v[4],
        }));
    }
    let Some(start) = rest.find('(') else {
        return Ok(None);
    };
    let kind = rest[..start].trim();
    if !["velocity", "angular velocity", "impulse", "force", "torque"].contains(&kind) {
        return Ok(None);
    }
    let end = rest
        .find(')')
        .ok_or("Missing physics vector closing parenthesis")?;
    let values: Result<Vec<f32>, _> = rest[start + 1..end]
        .split(',')
        .map(|v| v.trim().parse::<f32>())
        .collect();
    let values = values.map_err(|_| "Physics vectors require three finite numbers")?;
    if values.len() != 3 || values.iter().any(|v| !v.is_finite()) {
        return Err("Physics vectors require three finite numbers".into());
    }
    let v = [values[0], values[1], values[2]];
    let tail = rest[end + 1..].trim();
    let action = match kind {
        "velocity" if tail.is_empty() => Action::Velocity(v),
        "angular velocity" if tail.is_empty() => Action::AngularVelocity(v),
        "impulse" if tail.is_empty() => Action::Impulse(v),
        "torque" if tail.is_empty() || tail == "impulse" => Action::Torque(v, tail == "impulse"),
        "force" => {
            let duration = if tail.is_empty() {
                None
            } else {
                let parts: Vec<_> = tail.split_whitespace().collect();
                if parts.len() != 3
                    || parts[0] != "for"
                    || !["second", "seconds"].contains(&parts[2])
                {
                    return Err("Force duration: for <number> seconds".into());
                }
                let n = parts[1]
                    .parse::<f32>()
                    .map_err(|_| "Invalid force duration")?;
                if !n.is_finite() || n <= 0. {
                    return Err("Force duration must be positive".into());
                }
                Some(n)
            };
            Action::Force(v, duration)
        }
        _ => return Err("Unexpected physics command suffix".into()),
    };
    Ok(Some(action))
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn validates_vectors_and_suffixes() {
        assert_eq!(
            parse("physics force (1, 2, 3) for 0.5 seconds").unwrap(),
            Some(Action::Force([1., 2., 3.], Some(0.5)))
        );
        for invalid in [
            "physics velocity (NaN,0,0)",
            "physics torque (1,2)",
            "physics impulse (1,2,3) rubbish",
            "physics force (1,2,3) for -1 seconds",
        ] {
            assert!(parse(invalid).is_err(), "{invalid}");
        }
        assert!(parse("physics impulse up 10").unwrap().is_none());
    }
}
