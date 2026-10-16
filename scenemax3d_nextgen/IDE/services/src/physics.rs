//! Validate the exact generated script before it reaches a physics preview.
use scenemax_ide_core::physics::Settings;
pub use scenemax_parser::physics_motion::Action;
/// Validated commands in source order. Stop is represented by None.
pub fn actions(settings: &Settings) -> Result<Vec<Option<Action>>, String> {
    let text = settings.snippet()?;
    scenemax_parser::parse_program(&text)
        .map_err(|e| e.to_string())?
        .statements
        .into_iter()
        .map(|s| match s {
            scenemax_parser::Statement::PhysicsMotion { action, .. } => Ok(Some(action)),
            scenemax_parser::Statement::PhysicsStop { .. } => Ok(None),
            _ => Err("Generated command was not parsed as physics".into()),
        })
        .collect()
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn all_commands_generate_real_runtime_statements() {
        for command in scenemax_ide_core::physics::COMMANDS {
            let s = Settings {
                command: (*command).into(),
                ..Default::default()
            };
            let p = scenemax_parser::parse_program(&s.example().unwrap()).unwrap();
            for (name, expected) in [
                ("floor", [0., -0.5, 0.]),
                (s.object.as_str(), s.position),
                (s.target.as_str(), s.destination),
            ] {
                let position = p
                    .statements
                    .iter()
                    .find_map(|statement| match statement {
                        scenemax_parser::Statement::ModelDecl {
                            name: n, options, ..
                        } if n == name => options.position,
                        _ => None,
                    })
                    .unwrap_or_else(|| panic!("Missing parsed position for {name}"));
                assert_eq!([position.x, position.y, position.z], expected);
            }

            assert!(
                !p.statements
                    .iter()
                    .any(|s| matches!(s, scenemax_parser::Statement::Unsupported { .. })),
                "{command}: {:?}",
                p.statements
            );
            assert!(p.statements.iter().any(|s|matches!(s,scenemax_parser::Statement::ModelDecl{resource,options,..} if resource=="box" && options.body_kind==Some(scenemax_parser::SceneMaxBodyKind::Dynamic))), "{command}: {:?}",p.statements);
            assert_eq!(
                actions(&s).unwrap().len(),
                if *command == "Throw" { 3 } else { 2 }
            );
        }
    }
    #[test]
    fn every_aim_arc_and_shape_emits_supported_source() {
        for direction in scenemax_ide_core::physics::DIRECTIONS {
            for arc in scenemax_ide_core::physics::ARCS {
                for shape in ["Box", "Sphere"] {
                    let s = Settings {
                        direction: (*direction).into(),
                        arc: (*arc).into(),
                        shape: shape.into(),
                        ..Default::default()
                    };
                    let program = scenemax_parser::parse_program(&s.example().unwrap()).unwrap();
                    assert!(
                        !program
                            .statements
                            .iter()
                            .any(|s| matches!(s, scenemax_parser::Statement::Unsupported { .. }))
                    );
                    assert_eq!(actions(&s).unwrap().len(), 3);
                }
            }
        }
    }
}
