//! A single evaluated effect command is shared by local playback and wire replay.
use super::*;

pub(super) fn resolve(
    play: &EffekseerPlayStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
    bounds: Option<&SceneMaxColliderBounds>,
) -> Option<EffekseerPlayStatement> {
    let mut result = play.clone();
    if let Some(position) = &play.position {
        let p = evaluate_position_value_runtime(position, vars, scope, guards, transforms, bounds)?;
        if !p.is_finite() {
            return None;
        }
        result.position = Some(PositionValue::Coordinates(
            p.to_array().into_iter().map(PositionExpr::Number).collect(),
        ));
    }
    let mut looped = play.looped;
    let mut speed = 1.;
    let mut inputs = [0.; 4];
    for (key, operand) in &play.attrs {
        let value = resolve_script_value(operand, vars, scope, guards, Some(transforms), bounds)?;
        match key.as_str() {
            "loop" | "looping" => {
                looped = match &value {
                    serde_json::Value::Bool(v) => *v,
                    serde_json::Value::Number(n) => n.as_f64().is_some_and(|n| n != 0.),
                    serde_json::Value::String(v) => {
                        matches!(v.trim().to_ascii_lowercase().as_str(), "true" | "yes" | "1")
                    }
                    _ => false,
                }
            }
            "play_back_speed" | "playback_speed" | "speed" => speed = number(&value)?.max(0.001),
            "input0" | "homing force" => inputs[0] = number(&value)?,
            "input1" | "orbit bias" => inputs[1] = number(&value)?,
            "input2" | "velocity damping" => inputs[2] = number(&value)?,
            "input3" => inputs[3] = number(&value)?,
            _ => {}
        }
    }
    result.looped = looped;
    result.attrs = vec![
        (
            "loop".into(),
            AssignmentValue::Number(if looped { 1. } else { 0. }),
        ),
        ("play_back_speed".into(), AssignmentValue::Number(speed)),
    ];
    result.attrs.extend(
        inputs
            .into_iter()
            .enumerate()
            .map(|(i, n)| (format!("input{i}"), AssignmentValue::Number(n))),
    );
    Some(result)
}
fn number(value: &serde_json::Value) -> Option<f32> {
    let n = value
        .as_f64()
        .map(|n| n as f32)
        .or_else(|| value.as_str()?.trim().parse::<f32>().ok())?;
    n.is_finite().then_some(n)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::value_calls::tests::{resume_next, run_in};
    fn setup() -> (World, Entity) {
        let mut world = World::new();
        let entity = world
            .spawn((
                SceneMaxEntity {
                    name: "subject".into(),
                    runtime_name: "subject".into(),
                },
                Transform::default(),
                SceneMaxEffekseerEffect {
                    instance_id: 1,
                    asset_id: "fixture".into(),
                    effect_path: None,
                    one_shot_duration_seconds: 2.,
                },
            ))
            .id();
        (world, entity)
    }
    #[test]
    fn effect_functions_freeze_position_and_controls_in_expression_order() {
        let (mut world, entity) = setup();
        run_in(
            &mut world,
            "var count = 1\nnext() = {\ncount = count + 1\nreturn count\n}\nsubject.play pos (count,next(),count), attr = [\"input0\" next(), \"input1\" count, \"input2\" next(), \"input3\" count, \"speed\" next(), \"looping\" \"yes\"]",
        );
        while !world.resource::<DelayedActionQueue>().actions.is_empty() {
            resume_next(&mut world);
        }
        assert_eq!(world.resource::<SceneMaxVars>().0["count"], 5.);
        assert_eq!(
            world.get::<Transform>(entity).unwrap().translation,
            Vec3::new(1., 2., 2.)
        );
        let p = world.get::<SceneMaxEffekseerPlayback>(entity).unwrap();
        assert_eq!(p.dynamic_inputs, [3., 3., 4., 4.]);
        assert_eq!(p.playback_speed, 5.);
        assert!(p.looped);
    }
    #[test]
    fn effect_named_controls_and_typed_loop_values_have_canonical_wire_operands() {
        for (literal, expected) in [
            ("true", true),
            ("false", false),
            ("2", true),
            ("0", false),
            ("\" YES \"", true),
            ("\"no\"", false),
            ("\"1\"", true),
        ] {
            let source = format!(
                "subject.play loop, attr = [\"looping\" {literal}, \"speed\" \"2.5\", \"homing force\" 2, \"orbit bias\" 3, \"velocity damping\" 4, \"input3\" 5]"
            );
            let Statement::EffekseerPlay(play) =
                &scenemax_parser::parse_program(&source).unwrap().statements[0]
            else {
                panic!()
            };
            let p = resolve(
                play,
                &SceneMaxVars::default(),
                None,
                &HashMap::new(),
                &HashMap::new(),
                None,
            )
            .unwrap();
            assert_eq!(p.looped, expected);
            assert_eq!(
                p.attrs.iter().map(|(_, v)| v.clone()).collect::<Vec<_>>(),
                [if expected { 1. } else { 0. }, 2.5, 2., 3., 4., 5.].map(AssignmentValue::Number)
            );
        }
    }
}
