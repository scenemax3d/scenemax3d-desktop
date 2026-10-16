//! Evaluated SceneMax command emission; no project-specific entity knowledge.
use super::*;
use scenemax_parser::ik::IkAction;
pub(super) fn encode(
    action: &Statement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
) -> Option<(String, String)> {
    let number = |v: &AssignmentValue| {
        resolve_assignment_value_scoped_with_guards(v, vars, scope, guards, Some(transforms), None)
    };
    let vector = |v: Vec3| format!("({},{},{})", v.x, v.y, v.z);
    let reference = |value: &str| {
        let (name, joint) = crate::ik::split_target(value);
        let name = scenemax_runtime_vm_core::references::entity_name(name, vars, scope);
        crate::ik::reference(&format!("{{network_entity:{name}}}"), joint.as_deref())
    };
    Some(match action {
        Statement::LabelText { target, value } => (
            target.clone(),
            format!("{{network_entity}}.text = {}", crate::labels::wire_text(&crate::labels::text(value, vars, scope, guards, transforms))),
        ),
        Statement::RotationReset { target, rotation } => {
            let angles = evaluate_position_value_runtime(rotation, vars, scope, guards, transforms, None)?;
            if !angles.is_finite() { return None; }
            (target.clone(), format!("{{network_entity}}.rotate {}", vector(angles)))
        }
        Statement::Position(a) => {
            let p = evaluate_position_value_runtime(
                &a.position,
                vars,
                scope,
                guards,
                transforms,
                None,
            )?;
            (
                a.target.clone(),
                format!("{{network_entity}}.pos = {}", vector(p)),
            )
        }
        Statement::MoveTo(a) => {
            let p = evaluate_move_to_destination_runtime(
                &a.destination,
                vars,
                scope,
                guards,
                transforms,
                None,
            )?;
            // A single original displacement fits the existing 192-byte action
            // field and remains valid at an overshooting curve's zero crossings.
            if a.easing.is_some() || matches!(a.destination, MoveToDestination::Delta(_)) {
                let target = scenemax_runtime_vm_core::references::entity_name(&a.target, vars, scope);
                let delta = if matches!(a.destination, MoveToDestination::Delta(_)) { p } else { p - transforms.get(&target)?.translation };
                let duration = number(&a.duration_value)?;
                let easing = motion_ease::resolve(&a.easing, vars, scope, guards, transforms).wire();
                return Some((a.target.clone(), format!("{{network_entity}}.move by {} in {duration} seconds{easing} async", vector(delta))));
            }
            (
                a.target.clone(),
                format!(
                    "{{network_entity}}.move to {} in {} seconds async",
                    vector(p),
                    number(&a.duration_value)?
                ),
            )
        }
        Statement::Attach(a) => {
            let a = crate::attachments::evaluated(a, vars, scope, guards, transforms, None)?;
            let (target, joint) = crate::ik::split_target(&a.target);
            (
            target.into(),
            format!(
                "{}.attach to {}: pos ({},{},{})",
                crate::ik::reference("{network_entity}", joint.as_deref()),
                reference(&a.subject),
                a.offset.x,
                a.offset.y,
                a.offset.z
            ),
        )},
        Statement::EffekseerPlay(a) => {
            let mut options = Vec::new();
            if let Some(position) = &a.position {
                options.push(format!("pos {}", vector(evaluate_position_value_runtime(
                    position, vars, scope, guards, transforms, None)?)));
            }
            let mut attrs = Vec::new();
            if a.looped { attrs.push("\"loop\" 1".into()); }
            for (key, value) in &a.attrs {
                attrs.push(format!("{} {}", serde_json::Value::String(key.clone()), number(value)?));
            }
            // The attribute list identifies effect playback even without a position
            // or loop flag. Keep the parser's normal command syntax on the wire.
            options.push(format!("attr = [{}]", attrs.join(", ")));
            (a.target.clone(), format!("{{network_entity}}.play {} async", options.join(", ")))
        }
        Statement::Ik(a) => {
            let prefix = format!(
                "{{network_entity}}.ik{}",
                a.layer
                    .as_ref()
                    .map(|l| format!(".{l}"))
                    .unwrap_or_default()
            );
            let command = match &a.action {
                IkAction::Apply(asset) => {
                    format!("{prefix} = {}", serde_json::Value::String(resolve_ik_asset_name(asset,vars,scope,guards,Some(transforms),None)?))
                }
                IkAction::Remove => format!("{prefix} = empty"),
                IkAction::Target => format!("{prefix}.target = {}", reference(a.target.as_ref()?)),
                IkAction::Weight => format!("{prefix}.weight = {}", number(a.weight.as_ref()?)?),
                IkAction::Blend => format!("{prefix}.blend = {}", number(a.blend.as_ref()?)?),
                IkAction::Play | IkAction::Stop => {
                    let mut attrs = Vec::new();
                    if let Some(target) = &a.target {
                        attrs.push(format!("target {}", reference(target)));
                    }
                    if let Some(v) = &a.weight {
                        attrs.push(format!("weight {}", number(v)?));
                    }
                    if let Some(v) = &a.blend {
                        attrs.push(format!("blend {}", number(v)?));
                    }
                    format!(
                        "{prefix}.{}{}",
                        if matches!(a.action, IkAction::Play) {
                            "play"
                        } else {
                            "stop"
                        },
                        if attrs.is_empty() {
                            String::new()
                        } else {
                            format!(": {}", attrs.join(", "))
                        }
                    )
                }
                IkAction::PositionOffset(v) => {
                    format!("{prefix}.positionOffset = ({},{},{})", v[0], v[1], v[2])
                }
                IkAction::RotationOffset(v) => {
                    format!("{prefix}.rotationOffset = ({},{},{})", v[0], v[1], v[2])
                }
                IkAction::PolePosition(v) => {
                    format!("{prefix}.polePosition = ({},{},{})", v[0], v[1], v[2])
                }
            };
            (a.owner.clone(), command)
        }
        _ => return None,
    })
}
pub(super) fn references(
    source: &str,
    prefix: &str,
    resolve: impl Fn(&str) -> Option<String>,
) -> Option<String> {
    let mut output = String::new();
    let mut index = 0;
    let mut quoted = false;
    let mut escaped = false;
    while index < source.len() {
        let rest = &source[index..];
        let ch = rest.chars().next()?;
        if !quoted && rest.starts_with(prefix) {
            let tail = &rest[prefix.len()..];
            let (reference, consumed) = if prefix.ends_with('}') {
                ("", prefix.len())
            } else {
                let end = tail.find('}')?;
                (&tail[..end], prefix.len() + end + 1)
            };
            output.push_str(&resolve(reference)?);
            index += consumed;
            continue;
        }
        output.push(ch);
        if quoted {
            if escaped { escaped = false; }
            else if ch == '\\' { escaped = true; }
            else if ch == '"' { quoted = false; }
        } else if ch == '"' { quoted = true; }
        index += ch.len_utf8();
    }
    Some(output)
}
