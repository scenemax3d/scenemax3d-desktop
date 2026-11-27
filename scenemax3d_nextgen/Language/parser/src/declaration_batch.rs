use super::*;
use std::collections::HashSet;

#[derive(Default)]
pub(super) struct Marks {
    start: Option<usize>,
}

impl Marks {
    pub fn marker(&mut self, line: &str, actions: &mut Vec<Statement>) -> Result<bool, ParseError> {
        if line.eq_ignore_ascii_case(":begin") {
            if self.start.replace(actions.len()).is_some() {
                return Err(ParseError::InvalidBatch("nested :begin"));
            }
        } else if line.eq_ignore_ascii_case(":end") {
            let start = self
                .start
                .take()
                .ok_or(ParseError::InvalidBatch(":end without :begin"))?;
            let mut names = HashSet::new();
            for stmt in actions.split_off(start) {
                let name = independent_name(&stmt);
                if !names.is_empty() && name.is_none_or(|n| names.contains(n)) {
                    actions.push(Statement::FlushDeclarations);
                    names.clear();
                }
                if let Some(name) = name {
                    if names.is_empty() {
                        actions.push(Statement::BeginDeclarations);
                    }
                    names.insert(name.to_owned());
                }
                actions.push(stmt);
            }
            if !names.is_empty() {
                actions.push(Statement::FlushDeclarations);
            }
        } else {
            return Ok(false);
        }
        Ok(true)
    }

    pub fn finish(&self) -> Result<(), ParseError> {
        if self.start.is_some() {
            Err(ParseError::InvalidBatch("missing :end in this block"))
        } else {
            Ok(())
        }
    }
}

fn constant_position(value: &Option<PositionValue>) -> bool {
    value.as_ref().is_none_or(|p| matches!(p, PositionValue::Coordinates(v) if v.iter().all(|c| matches!(c, PositionExpr::Number(_)))))
}

fn constant(value: &Option<AssignmentValue>) -> bool {
    value
        .as_ref()
        .is_none_or(|v| matches!(v, AssignmentValue::Number(_)))
}

// Dynamic operands and multiplayer registration can depend on an applied ECS command.
fn independent_name(stmt: &Statement) -> Option<&str> {
    match stmt {
        Statement::ModelDecl { name, options, .. }
            if !options.shared
                && !options.multiplayer
                && options.resource_value.is_none()
                && options.label.is_none()
                && constant_position(&options.position_value)
                && constant(&options.scale_value)
                && options
                    .geometry_values
                    .iter()
                    .flat_map(|a| &a.values)
                    .all(|v| matches!(v, AssignmentValue::Number(_))) =>
        {
            Some(name)
        }
        Statement::LightDecl(l)
            if !l.shared
                && l.look_at.is_none()
                && constant_position(&l.position)
                && l.direction.as_ref().is_none_or(|coords| {
                    coords.iter().all(|v| matches!(v, PositionExpr::Number(_)))
                })
                && [&l.intensity, &l.range, &l.angle, &l.exposure]
                    .into_iter()
                    .all(constant) =>
        {
            Some(&l.name)
        }
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn batches_flush_before_observers_and_at_the_end() {
        let p=parse_program("build = {\n:begin\na => box: pos (1,2,3)\nb => sphere\nlogger.info a.x\nc => box\n:end\n}").unwrap();
        let Statement::FunctionDef(f) = &p.statements[0] else {
            panic!()
        };
        assert!(matches!(
            &f.actions[..],
            [
                Statement::BeginDeclarations,
                Statement::ModelDecl { .. },
                Statement::ModelDecl { .. },
                Statement::FlushDeclarations,
                _,
                Statement::BeginDeclarations,
                Statement::ModelDecl { .. },
                Statement::FlushDeclarations
            ]
        ));
        assert!(parse_program("build = {\n:begin\na => box\n}").is_err());
        assert!(parse_program(":end").is_err());
    }
}
