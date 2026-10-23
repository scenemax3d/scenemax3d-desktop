//! Deferred numeric declaration attributes. The same resolved operands build
//! local geometry and the SceneMax declaration sent to other participants.
use super::*;

#[derive(Debug, Clone, PartialEq)]
pub struct Attribute {
    pub name: &'static str,
    pub values: Vec<AssignmentValue>,
}

const NAMES: &[&str] = &[
    "size",
    "rotate",
    "radius",
    "inner radius",
    "height",
    "steps",
    "thickness",
    "segments",
];

fn span<'a>(text: &'a str, name: &str) -> Option<(usize, &'a str)> {
    let index = find_entity_option(text, name)?;
    if name == "radius" && text[..index].trim_end().ends_with("inner") {
        return None;
    }
    let after = text[index + name.len()..].trim_start();
    let mut end = after.len();
    let mut depth = 0usize;
    let mut quote = None;
    let mut escaped = false;
    for (i, ch) in after.char_indices() {
        if let Some(q) = quote {
            if escaped {
                escaped = false;
            } else if ch == '\\' {
                escaped = true;
            } else if ch == q {
                quote = None;
            }
            continue;
        }
        match ch {
            '"' | '\'' => quote = Some(ch),
            '(' | '[' => depth += 1,
            ')' | ']' => depth = depth.saturating_sub(1),
            ',' if depth == 0 => {
                end = i;
                break;
            }
            _ => {}
        }
    }
    for name in [
        "pos",
        "rotate",
        "scale",
        "size",
        "inner radius",
        "radius",
        "height",
        "steps",
        "thickness",
        "segments",
        "material",
        "hidden",
        "multiplayer",
        "shadow",
        "collision",
        "body",
        "mass",
        "async",
        "loop",
        "billboard",
        "and",
    ] {
        if let Some(i) = find_entity_option(after, name).filter(|i| *i > 0) {
            end = end.min(i);
        }
    }
    let raw = after[..end].trim();
    Some((text.len() - after.len(), raw))
}

pub(super) fn extract(text: &str) -> Result<(String, Vec<Attribute>), ParseError> {
    let mut found = Vec::new();
    for &name in NAMES {
        let Some((start, raw)) = span(text, name) else {
            continue;
        };
        let tuple =
            matches!(name, "size" | "rotate" | "radius" | "inner radius") && raw.starts_with('(');
        let parts = if tuple {
            let inside = raw.strip_prefix('(').unwrap();
            let end = matching_close_paren_index(inside)
                .ok_or_else(|| ParseError::InvalidNumber(raw.into()))?;
            if !inside[end + 1..].trim().is_empty() {
                vec![raw]
            } else {
                split_top_level_comma(&inside[..end])
            }
        } else {
            vec![raw]
        };
        if parts.iter().all(|part| part.trim().parse::<f32>().is_ok()) {
            continue;
        }
        let count = parts.len();
        let valid = match name {
            "size" => count == 2 || count == 3,
            "rotate" => count == 3,
            "radius" => count == 1 || count == 2,
            "inner radius" => count == 2,
            _ => count == 1,
        };
        if !valid {
            return Err(ParseError::InvalidNumber(raw.into()));
        }
        let values = parts
            .into_iter()
            .map(|part| {
                parse_assignment_value(part.trim())?
                    .ok_or_else(|| ParseError::InvalidNumber(part.into()))
            })
            .collect::<Result<Vec<_>, _>>()?;
        let placeholder = if count > 1 {
            format!("({})", vec!["1"; count].join(","))
        } else {
            "1".into()
        };
        found.push((start, raw.len(), placeholder, Attribute { name, values }));
    }
    found.sort_by_key(|entry| entry.0);
    let mut numeric = text.to_owned();
    for (start, length, placeholder, _) in found.iter().rev() {
        numeric.replace_range(*start..start + length, placeholder);
    }
    Ok((numeric, found.into_iter().map(|entry| entry.3).collect()))
}

/// Resolve atomically: failure leaves the authored options untouched.
pub fn resolve(
    options: &EntityOptions,
    mut evaluate: impl FnMut(&AssignmentValue) -> Option<f32>,
) -> Option<EntityOptions> {
    let mut result = options.clone();
    for attr in &options.geometry_values {
        let values = attr
            .values
            .iter()
            .map(&mut evaluate)
            .collect::<Option<Vec<_>>>()?;
        if !values.iter().all(|v| v.is_finite()) {
            return None;
        }
        let first = values[0];
        match attr.name {
            "size" => {
                result.size = Some(SceneMaxVec3 {
                    x: first,
                    y: values[1],
                    z: *values.get(2).unwrap_or(&1.),
                })
            }
            "rotate" => {
                result.rotation_degrees = Some(SceneMaxVec3 {
                    x: first,
                    y: values[1],
                    z: values[2],
                })
            }
            "radius" if values.len() == 2 => {
                result.radius_top = Some(first);
                result.radius_bottom = Some(values[1]);
            }
            "radius" => result.radius = Some(first),
            "inner radius" => {
                result.inner_radius_top = Some(first);
                result.inner_radius_bottom = Some(values[1]);
            }
            "height" => result.height = Some(first),
            "steps" => result.steps = Some(first.round().max(1.) as usize),
            "thickness" => result.thickness = Some(first),
            "segments" => result.segments = Some(first.round().max(1.) as usize),
            _ => return None,
        }
        if let Some(source) = result.multiplayer_source.as_mut() {
            let (_, opts) = split_resource_and_options(source);
            let (start, raw) = span(opts, attr.name)?;
            let start = opts.as_ptr() as usize - source.as_ptr() as usize + start;
            let end = start + raw.len();
            let numbers = values
                .iter()
                .map(f32::to_string)
                .collect::<Vec<_>>()
                .join(",");
            let replacement = if values.len() > 1 {
                format!("({numbers})")
            } else {
                numbers
            };
            source.replace_range(start..end, &replacement);
        }
    }
    result.geometry_values.clear();
    Some(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn options(source: &str) -> EntityOptions {
        let program = parse_program(source).unwrap();
        let Statement::ModelDecl { options, .. } = &program.statements[0] else {
            panic!("declaration")
        };
        options.clone()
    }
    #[test]
    fn every_primitive_freezes_dimensions_into_a_self_contained_spawn() {
        for (kind, attributes, expected) in [
            ("box", "size (v,v+1,v+2)", "size (2,2,2)"),
            ("sphere", "radius v", "radius 2"),
            (
                "cylinder",
                "radius (v,v+1), height v",
                "radius (2,2), height 2",
            ),
            (
                "hollow cylinder",
                "radius (v,v), inner radius (v/2,v/3), height v",
                "radius (2,2), inner radius (2,2), height 2",
            ),
            ("quad", "size (v,v)", "size (2,2)"),
            ("wedge", "size (v,v,v)", "size (2,2,2)"),
            ("cone", "radius (v,v), height v", "radius (2,2), height 2"),
            ("stairs", "size (v,v,v), steps v", "size (2,2,2), steps 2"),
            (
                "arch",
                "size (v,v,v), thickness v, segments v",
                "size (2,2,2), thickness 2, segments 2",
            ),
        ] {
            let original = options(&format!("shape => {kind}: multiplayer, {attributes}"));
            assert!(!original.geometry_values.is_empty(), "{kind}");
            let evaluated = resolve(&original, |_| Some(2.)).unwrap();
            let source = evaluated.multiplayer_source.as_ref().unwrap();
            assert!(source.ends_with(expected), "{source}");
            assert_eq!(options(&format!("peer => {source}")), evaluated, "{kind}");
        }
    }
    #[test]
    fn nested_calls_and_rotation_preserve_strings_and_resolve_atomically() {
        let original = options(
            "shape => box: multiplayer, size (pick(1,2),3,4), rotate (0,angle(2,3),0), material \"size (x,y,z), rotate bogus\"",
        );
        assert_eq!(original.geometry_values.len(), 2);
        assert!(resolve(&original, |_| None).is_none());
        let mut count = 0;
        let evaluated = resolve(&original, |value| {
            count += 1;
            Some(if let AssignmentValue::Number(v) = value {
                *v
            } else {
                25.
            })
        })
        .unwrap();
        assert_eq!(count, 6);
        assert_eq!(evaluated.rotation_degrees.unwrap().y, 25.);
        assert_eq!(evaluated.size.unwrap().x, 25.);
        assert_eq!(evaluated.material, original.material);
        assert!(
            evaluated
                .multiplayer_source
                .unwrap()
                .contains("rotate (0,25,0)")
        );
        assert_eq!(original.geometry_values.len(), 2);
    }
}
