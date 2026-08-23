use std::{
    collections::HashMap,
    sync::atomic::{AtomicU64, Ordering},
    time::{SystemTime, UNIX_EPOCH},
};

use bevy_ecs::prelude::Resource;
use scenemax_parser::{
    ArithmeticOperator, AssignmentStatement, AssignmentValue, ComparisonOperator, Condition,
    Program, Statement,
};

static SCENEMAX_RANDOM_STATE: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Resource, Default, Clone, PartialEq)]
pub struct SceneMaxVars(pub HashMap<String, f32>);

#[derive(Debug, Clone, Default, PartialEq)]
pub struct SceneMaxScopeFrame {
    pub vars: HashMap<String, f32>,
    pub aliases: HashMap<String, String>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct AssignmentResult {
    pub previous: Option<f32>,
    pub value: f32,
}

pub trait SceneMaxVmSpatial {
    fn symbol_value(&self, name: &str) -> Option<f32>;
    fn distance(&self, left: &str, right: &str) -> Option<f32>;
    fn collision_matches(&self, sources: &[String], target: &str) -> bool;
}

#[derive(Debug, Default, Clone, Copy)]
pub struct NoSceneMaxVmSpatial;

impl SceneMaxVmSpatial for NoSceneMaxVmSpatial {
    fn symbol_value(&self, _name: &str) -> Option<f32> {
        None
    }

    fn distance(&self, _left: &str, _right: &str) -> Option<f32> {
        None
    }

    fn collision_matches(&self, _sources: &[String], _target: &str) -> bool {
        false
    }
}

pub fn apply_initial_assignments(program: &Program, vars: &mut SceneMaxVars) {
    let guards_by_name = scenemax_runtime_script_core::collect_guards_by_name(program);
    let spatial = NoSceneMaxVmSpatial;
    for statement in &program.statements {
        match statement {
            Statement::Assignment(assignment) => {
                let _ = apply_assignment_with_spatial(
                    assignment,
                    vars,
                    None,
                    &guards_by_name,
                    &spatial,
                    false,
                );
            }
            Statement::SharedAssignment(assignment) if !vars.0.contains_key(&assignment.name) => {
                let _ = apply_assignment_with_spatial(
                    assignment,
                    vars,
                    None,
                    &guards_by_name,
                    &spatial,
                    false,
                );
            }
            _ => {}
        }
    }
}

pub fn format_scenemax_number(value: f32) -> String {
    if value.is_finite() && (value - value.round()).abs() <= f32::EPSILON {
        format!("{}", value.round() as i64)
    } else {
        format!("{value}")
    }
}

pub fn apply_assignment(
    assignment: &AssignmentStatement,
    vars: &mut SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
) -> Option<AssignmentResult> {
    let spatial = NoSceneMaxVmSpatial;
    apply_assignment_with_spatial(assignment, vars, None, guards_by_name, &spatial, false)
}

pub fn apply_assignment_with_spatial(
    assignment: &AssignmentStatement,
    vars: &mut SceneMaxVars,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    spatial: &impl SceneMaxVmSpatial,
    force_local: bool,
) -> Option<AssignmentResult> {
    let previous = resolve_symbol_value_scoped(&assignment.name, vars, scope.as_deref(), spatial);
    let value = resolve_assignment_value_scoped_with_guards(
        &assignment.value,
        vars,
        scope.as_deref(),
        guards_by_name,
        spatial,
    )?;
    assign_symbol_value(
        &assignment.name,
        value,
        vars,
        scope.as_deref_mut(),
        force_local,
    );
    Some(AssignmentResult { previous, value })
}

pub fn assign_symbol_value(
    name: &str,
    value: f32,
    vars: &mut SceneMaxVars,
    scope: Option<&mut SceneMaxScopeFrame>,
    force_local: bool,
) {
    let Some(scope) = scope else {
        vars.0.insert(name.to_owned(), value);
        return;
    };
    if force_local {
        scope.vars.insert(name.to_owned(), value);
    } else if scope.vars.contains_key(name) {
        scope.vars.insert(name.to_owned(), value);
    } else if vars.0.contains_key(name) || is_runtime_field_path(name) {
        vars.0.insert(name.to_owned(), value);
    } else {
        scope.vars.insert(name.to_owned(), value);
    }
}

pub fn is_runtime_field_path(name: &str) -> bool {
    name.contains('.')
}

pub fn resolve_assignment_value(value: &AssignmentValue, vars: &SceneMaxVars) -> Option<f32> {
    let spatial = NoSceneMaxVmSpatial;
    resolve_assignment_value_with_guards(value, vars, &HashMap::new(), &spatial)
}

pub fn resolve_assignment_value_with_guards(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    spatial: &impl SceneMaxVmSpatial,
) -> Option<f32> {
    resolve_assignment_value_scoped_with_guards(value, vars, None, guards_by_name, spatial)
}

pub fn resolve_assignment_value_scoped_with_guards(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    spatial: &impl SceneMaxVmSpatial,
) -> Option<f32> {
    match value {
        AssignmentValue::Number(value) => Some(*value),
        AssignmentValue::Symbol(name) => resolve_symbol_value_scoped(name, vars, scope, spatial),
        AssignmentValue::CameraModifier(_) => None,
        AssignmentValue::ThrowMotion(_) => None,
        AssignmentValue::AnimationController(_) => None,
        AssignmentValue::Condition(condition) => {
            Some(
                condition_matches_scoped(condition, vars, scope, guards_by_name, spatial) as u8
                    as f32,
            )
        }
        AssignmentValue::RandomInt { max } => {
            let max = resolve_assignment_value_scoped_with_guards(
                max,
                vars,
                scope,
                guards_by_name,
                spatial,
            )?
            .max(1.0) as u32;
            Some((pseudo_random_u32() % max) as f32)
        }
        AssignmentValue::Round { value } => {
            let value = resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                spatial,
            )?;
            Some(value.round())
        }
        AssignmentValue::Distance { left, right } => spatial.distance(left, right),
        AssignmentValue::PoolAcquire { .. } => None,
        AssignmentValue::Binary {
            left,
            operator,
            right,
        } => {
            let left = resolve_assignment_value_scoped_with_guards(
                left,
                vars,
                scope,
                guards_by_name,
                spatial,
            )?;
            let right = resolve_assignment_value_scoped_with_guards(
                right,
                vars,
                scope,
                guards_by_name,
                spatial,
            )?;
            Some(match operator {
                ArithmeticOperator::Add => left + right,
                ArithmeticOperator::Subtract => left - right,
                ArithmeticOperator::Multiply => left * right,
                ArithmeticOperator::Divide if right.abs() > f32::EPSILON => left / right,
                ArithmeticOperator::Divide => return None,
                ArithmeticOperator::Modulo if right.abs() > f32::EPSILON => left % right,
                ArithmeticOperator::Modulo => return None,
            })
        }
    }
}

pub fn condition_matches(
    condition: &Condition,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    spatial: &impl SceneMaxVmSpatial,
) -> bool {
    condition_matches_scoped(condition, vars, None, guards_by_name, spatial)
}

pub fn condition_matches_scoped(
    condition: &Condition,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    spatial: &impl SceneMaxVmSpatial,
) -> bool {
    match condition {
        Condition::EqualsNumber { name, value } => {
            (resolve_symbol_value_scoped(name, vars, scope, spatial).unwrap_or_default() - *value)
                .abs()
                <= f32::EPSILON
        }
        Condition::NotEqualsNumber { name, value } => {
            (resolve_symbol_value_scoped(name, vars, scope, spatial).unwrap_or_default() - *value)
                .abs()
                > f32::EPSILON
        }
        Condition::EqualsSymbol { name, value } => {
            let Some(value) = resolve_symbol_value_scoped(value, vars, scope, spatial) else {
                return false;
            };
            (resolve_symbol_value_scoped(name, vars, scope, spatial).unwrap_or_default() - value)
                .abs()
                <= f32::EPSILON
        }
        Condition::NotEqualsSymbol { name, value } => {
            let Some(value) = resolve_symbol_value_scoped(value, vars, scope, spatial) else {
                return false;
            };
            (resolve_symbol_value_scoped(name, vars, scope, spatial).unwrap_or_default() - value)
                .abs()
                > f32::EPSILON
        }
        Condition::Compare {
            name,
            operator,
            value,
        } => {
            let Some(right) = resolve_assignment_value_scoped_with_guards(
                value,
                vars,
                scope,
                guards_by_name,
                spatial,
            ) else {
                return false;
            };
            let left = resolve_symbol_value_scoped(name, vars, scope, spatial).unwrap_or_default();
            compare_values(left, *operator, right)
        }
        Condition::CompareValue {
            left,
            operator,
            right,
        } => {
            let (Some(left), Some(right)) = (
                resolve_assignment_value_scoped_with_guards(
                    left,
                    vars,
                    scope,
                    guards_by_name,
                    spatial,
                ),
                resolve_assignment_value_scoped_with_guards(
                    right,
                    vars,
                    scope,
                    guards_by_name,
                    spatial,
                ),
            ) else {
                return false;
            };
            compare_values(left, *operator, right)
        }
        Condition::EqualsValue { left, right } => {
            let (Some(left), Some(right)) = (
                resolve_assignment_value_scoped_with_guards(
                    left,
                    vars,
                    scope,
                    guards_by_name,
                    spatial,
                ),
                resolve_assignment_value_scoped_with_guards(
                    right,
                    vars,
                    scope,
                    guards_by_name,
                    spatial,
                ),
            ) else {
                return false;
            };
            (left - right).abs() <= f32::EPSILON
        }
        Condition::NotEqualsValue { left, right } => {
            let (Some(left), Some(right)) = (
                resolve_assignment_value_scoped_with_guards(
                    left,
                    vars,
                    scope,
                    guards_by_name,
                    spatial,
                ),
                resolve_assignment_value_scoped_with_guards(
                    right,
                    vars,
                    scope,
                    guards_by_name,
                    spatial,
                ),
            ) else {
                return false;
            };
            (left - right).abs() > f32::EPSILON
        }
        Condition::Truthy { name } => {
            resolve_symbol_value_scoped(name, vars, scope, spatial)
                .unwrap_or_default()
                .abs()
                > f32::EPSILON
        }
        Condition::Collision { sources, target } => spatial.collision_matches(sources, target),
        Condition::Boolean(value) => *value,
        Condition::Not(condition) => {
            !condition_matches_scoped(condition, vars, scope, guards_by_name, spatial)
        }
        Condition::Alias(name) => guards_by_name.get(name).is_some_and(|condition| {
            condition_matches_scoped(condition, vars, scope, guards_by_name, spatial)
        }),
        Condition::And(conditions) => conditions.iter().all(|condition| {
            condition_matches_scoped(condition, vars, scope, guards_by_name, spatial)
        }),
        Condition::Or(conditions) => conditions.iter().any(|condition| {
            condition_matches_scoped(condition, vars, scope, guards_by_name, spatial)
        }),
    }
}

pub fn variable_is_zero(vars: &SceneMaxVars, name: &str) -> bool {
    vars.0.get(name).copied().unwrap_or_default().abs() <= f32::EPSILON
}

pub fn resolve_symbol_value_scoped(
    name: &str,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    spatial: &impl SceneMaxVmSpatial,
) -> Option<f32> {
    scope
        .and_then(|scope| scope.vars.get(name).copied())
        .or_else(|| {
            vars.0
                .get(name)
                .copied()
                .or_else(|| spatial.symbol_value(name))
        })
}

fn compare_values(left: f32, operator: ComparisonOperator, right: f32) -> bool {
    match operator {
        ComparisonOperator::Greater => left > right,
        ComparisonOperator::GreaterOrEqual => left >= right,
        ComparisonOperator::Less => left < right,
        ComparisonOperator::LessOrEqual => left <= right,
    }
}

pub fn pseudo_random_u32() -> u32 {
    let mut state = SCENEMAX_RANDOM_STATE.load(Ordering::Relaxed);
    if state == 0 {
        let seed = random_seed();
        let _ =
            SCENEMAX_RANDOM_STATE.compare_exchange(0, seed, Ordering::Relaxed, Ordering::Relaxed);
        state = SCENEMAX_RANDOM_STATE.load(Ordering::Relaxed);
    }

    loop {
        let next = state
            .wrapping_mul(6_364_136_223_846_793_005)
            .wrapping_add(1_442_695_040_888_963_407);
        match SCENEMAX_RANDOM_STATE.compare_exchange_weak(
            state,
            next,
            Ordering::Relaxed,
            Ordering::Relaxed,
        ) {
            Ok(_) => return (next >> 32) as u32,
            Err(updated) if updated != 0 => state = updated,
            Err(_) => state = random_seed(),
        }
    }
}

fn random_seed() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| {
            (duration.as_nanos() as u64)
                ^ 0x9E37_79B9_7F4A_7C15
                ^ ((std::process::id() as u64) << 32)
        })
        .ok()
        .filter(|seed| *seed != 0)
        .unwrap_or(0xA076_1D64_78BD_642F)
}

#[cfg(test)]
pub fn reset_pseudo_random_for_test(seed: u64) {
    SCENEMAX_RANDOM_STATE.store(seed.max(1), Ordering::Relaxed);
}

#[cfg(test)]
pub fn sample_pseudo_random_moduli(max: u32, count: usize) -> std::collections::HashSet<u32> {
    (0..count)
        .map(|_| pseudo_random_u32() % max.max(1))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use scenemax_parser::{ArithmeticOperator, parse_program};

    #[derive(Default)]
    struct TestSpatial {
        symbols: HashMap<String, f32>,
        distances: HashMap<(String, String), f32>,
    }

    impl SceneMaxVmSpatial for TestSpatial {
        fn symbol_value(&self, name: &str) -> Option<f32> {
            self.symbols.get(name).copied()
        }

        fn distance(&self, left: &str, right: &str) -> Option<f32> {
            self.distances
                .get(&(left.to_owned(), right.to_owned()))
                .or_else(|| self.distances.get(&(right.to_owned(), left.to_owned())))
                .copied()
        }

        fn collision_matches(&self, _sources: &[String], _target: &str) -> bool {
            false
        }
    }

    #[test]
    fn initializes_multiline_scene_max_constants() {
        let program = parse_program(
            "var PLAYER_ACTION_IDLE = 0,\n    PLAYER_ACTION_X_1 = 7, PLAYER_ACTION_X_2 = 8,\n    PLAYER_ACTION_C = 9\nvar GAME_STATE_BEFORE_START = 0,\n    GAME_STATE_START = 1,\n    GAME_STATE_OVER = 2\nvar game_status=GAME_STATE_START",
        )
        .unwrap();
        let mut vars = SceneMaxVars::default();

        apply_initial_assignments(&program, &mut vars);

        assert_eq!(vars.0.get("PLAYER_ACTION_X_2").copied(), Some(8.0));
        assert_eq!(vars.0.get("GAME_STATE_START").copied(), Some(1.0));
        assert_eq!(vars.0.get("game_status").copied(), Some(1.0));
    }

    #[test]
    fn evaluates_arithmetic_assignment_value() {
        let program = parse_program("score = 5\nscore = score + 10").unwrap();
        let mut vars = SceneMaxVars::default();

        apply_initial_assignments(&program, &mut vars);

        assert_eq!(vars.0.get("score").copied(), Some(15.0));
    }

    #[test]
    fn shared_initial_assignment_does_not_overwrite_existing_value() {
        let program = parse_program("shared var score = 0 [0..]\nvar life2 = 10").unwrap();
        let mut vars = SceneMaxVars(HashMap::from([
            ("score".to_owned(), 42.0),
            ("life2".to_owned(), 3.0),
        ]));

        apply_initial_assignments(&program, &mut vars);

        assert_eq!(vars.0.get("score").copied(), Some(42.0));
        assert_eq!(vars.0.get("life2").copied(), Some(10.0));
    }

    #[test]
    fn evaluates_guard_alias_inside_condition_assignment() {
        let program = parse_program(
            "var @can_attack = enemy_ko==0 && op_hit==0\nvar should_attack = @can_attack",
        )
        .unwrap();
        let mut vars = SceneMaxVars::default();
        vars.0.insert("enemy_ko".to_owned(), 0.0);
        vars.0.insert("op_hit".to_owned(), 0.0);

        apply_initial_assignments(&program, &mut vars);

        assert_eq!(vars.0.get("should_attack").copied(), Some(1.0));

        vars.0.insert("op_hit".to_owned(), 1.0);
        let guards = scenemax_runtime_script_core::collect_guards_by_name(&program);
        let spatial = NoSceneMaxVmSpatial;
        let _ = apply_assignment_with_spatial(
            &scenemax_parser::AssignmentStatement {
                name: "should_attack".to_owned(),
                value: AssignmentValue::Condition(Box::new(Condition::Alias(
                    "can_attack".to_owned(),
                ))),
            },
            &mut vars,
            None,
            &guards,
            &spatial,
            false,
        );

        assert_eq!(vars.0.get("should_attack").copied(), Some(0.0));
    }

    #[test]
    fn scoped_expression_vm_keeps_function_locals_out_of_global_state() {
        let mut vars = SceneMaxVars(HashMap::from([
            ("op_action".to_owned(), 0.0),
            ("counter".to_owned(), 2.0),
        ]));
        let mut scope = SceneMaxScopeFrame::default();
        let guards = HashMap::new();
        let spatial = NoSceneMaxVmSpatial;

        let _ = apply_assignment_with_spatial(
            &scenemax_parser::AssignmentStatement {
                name: "dist".to_owned(),
                value: AssignmentValue::Number(4.25),
            },
            &mut vars,
            Some(&mut scope),
            &guards,
            &spatial,
            false,
        );
        let _ = apply_assignment_with_spatial(
            &scenemax_parser::AssignmentStatement {
                name: "op_action".to_owned(),
                value: AssignmentValue::Number(3.0),
            },
            &mut vars,
            Some(&mut scope),
            &guards,
            &spatial,
            false,
        );

        assert_eq!(scope.vars.get("dist").copied(), Some(4.25));
        assert_eq!(vars.0.get("dist"), None);
        assert_eq!(vars.0.get("op_action").copied(), Some(3.0));
        assert!(condition_matches_scoped(
            &Condition::Compare {
                name: "dist".to_owned(),
                operator: ComparisonOperator::Less,
                value: AssignmentValue::Number(5.5),
            },
            &vars,
            Some(&scope),
            &guards,
            &spatial,
        ));
    }

    #[test]
    fn expression_vm_evaluates_not_boolean_and_inequality() {
        let mut vars = SceneMaxVars(HashMap::from([
            ("flag".to_owned(), 0.0),
            ("enemy_ko".to_owned(), 0.0),
        ]));
        let guards = HashMap::new();
        let spatial = NoSceneMaxVmSpatial;

        let _ = apply_assignment_with_spatial(
            &scenemax_parser::AssignmentStatement {
                name: "flag".to_owned(),
                value: AssignmentValue::Condition(Box::new(Condition::Boolean(true))),
            },
            &mut vars,
            None,
            &guards,
            &spatial,
            false,
        );

        assert_eq!(vars.0.get("flag").copied(), Some(1.0));
        assert!(condition_matches(
            &Condition::Or(vec![
                Condition::Not(Box::new(Condition::Truthy {
                    name: "missing_flag".to_owned(),
                })),
                Condition::NotEqualsValue {
                    left: AssignmentValue::Symbol("enemy_ko".to_owned()),
                    right: AssignmentValue::Number(1.0),
                },
            ]),
            &vars,
            &guards,
            &spatial,
        ));
    }

    #[test]
    fn evaluates_distance_condition_modulo_round_and_random_values() {
        let mut vars = SceneMaxVars::default();
        vars.0.insert("life2".to_owned(), 3.0);
        vars.0.insert("high_kick_counter".to_owned(), 6.0);
        let guards = HashMap::new();
        let mut spatial = TestSpatial::default();
        spatial
            .distances
            .insert(("player1".to_owned(), "player2".to_owned()), 5.0);

        assert_eq!(
            resolve_assignment_value_with_guards(
                &AssignmentValue::Distance {
                    left: "player1".to_owned(),
                    right: "player2".to_owned(),
                },
                &vars,
                &guards,
                &spatial,
            ),
            Some(5.0)
        );
        assert_eq!(
            resolve_assignment_value_with_guards(
                &AssignmentValue::Binary {
                    left: Box::new(AssignmentValue::Symbol("high_kick_counter".to_owned())),
                    operator: ArithmeticOperator::Modulo,
                    right: Box::new(AssignmentValue::Number(3.0)),
                },
                &vars,
                &guards,
                &spatial,
            ),
            Some(0.0)
        );
        assert_eq!(
            resolve_assignment_value_with_guards(
                &AssignmentValue::Round {
                    value: Box::new(AssignmentValue::Number(2.6)),
                },
                &vars,
                &guards,
                &spatial,
            ),
            Some(3.0)
        );

        reset_pseudo_random_for_test(0x1234_5678_9ABC_DEF0);
        assert!(sample_pseudo_random_moduli(4, 16).len() > 1);
    }
}
