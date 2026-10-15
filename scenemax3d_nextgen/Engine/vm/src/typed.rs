//! Script values shared by local execution and multiplayer. Numeric engine APIs
//! keep their f32 boundary; script strings, collections and wire IDs retain type.
use super::*;
pub use serde_json::Value;

pub(super) fn numeric_value(value: f32) -> Value {
    value
        .to_string()
        .parse::<f64>()
        .ok()
        .filter(|n| n.is_finite())
        .map_or(Value::Null, |n| serde_json::json!(n))
}

pub fn number(value: &Value) -> Option<f64> {
    value
        .as_f64()
        .or_else(|| value.as_bool().map(|b| if b { 1. } else { 0. }))
}
pub fn text(value: &Value) -> String {
    if let Some(n) = value.as_f64() {
        if n.is_finite() && n.fract() == 0. {
            return format!("{n:.0}");
        }
    }
    value
        .as_str()
        .map(str::to_owned)
        .unwrap_or_else(|| value.to_string())
}
pub fn lookup(
    name: &str,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
) -> Option<Value> {
    let resolved = references::entity_path(name, vars, scope);
    if resolved != name { return lookup(&resolved, vars, scope); }
    if let Some(scope) = scope {
        if let Some(value) = references::scope_value(name, scope) { return value.snapshot(); }
        if let Some(v) = scope.typed.get(name) {
            if !scope.vars.contains_key(name) { if let Some(reference) = scope.references.get(name) { return reference.snapshot(); } }
            return Some(current(v, scope.vars.get(name)));
        }
        if let Some(v) = scope.vars.get(name) {
            return Some(numeric_value(*v));
        }
    }
    if let Some(v) = vars.1.get(name) {
        if !vars.0.contains_key(name) { if let Some(reference) = vars.2.get(name) { return reference.snapshot(); } }
        return Some(current(v, vars.0.get(name)));
    }
    if let Some(v) = vars.0.get(name) {
        return Some(numeric_value(*v));
    }
    // Longest registered prefix wins for object fields and array/string length.
    for (i, _) in name.match_indices('.').rev() {
        let Some(base) = lookup(&name[..i], vars, scope) else {
            continue;
        };
        if let Some(value) = field(&base, &Value::String(name[i + 1..].into())) {
            return Some(value);
        }
    }
    None
}
fn current(value: &Value, scalar: Option<&f32>) -> Value {
    if let Some(scalar) = scalar {
        if number(value).map(|n| n as f32) != Some(*scalar) {
            return numeric_value(*scalar);
        }
    }
    value.clone()
}
pub fn equal(a: &Value, b: &Value) -> bool {
    if let (Value::Array(a), Value::Array(b)) = (a,b) {
        return a.len() == b.len() && a.iter().zip(b).all(|(a,b)| equal(a,b));
    }
    if let (Value::Object(a), Value::Object(b)) = (a,b) {
        return a.len() == b.len() && a.iter().all(|(key,a)| b.get(key).is_some_and(|b| equal(a,b)));
    }
    match (number(a), number(b)) {
        (Some(a), Some(b)) => (a - b).abs() <= f64::from(f32::EPSILON),
        _ => a == b,
    }
}
fn field(value: &Value, index: &Value) -> Option<Value> {
    if matches!(value, Value::Array(_) | Value::String(_))
        && index.as_str().is_some_and(|name| name.eq_ignore_ascii_case("length")) {
        return match value {
            Value::Array(a) => Some(serde_json::json!(a.len())),
            Value::String(s) => Some(serde_json::json!(s.encode_utf16().count())),
            _ => None,
        };
    }
    match (value, index) {
        (Value::Object(map), Value::String(key)) => map.get(key).cloned(),
        (Value::Array(array), _) => {
            let n = number(index)?;
            if n < 0. || n.fract() != 0. {
                return None;
            }
            array.get(n as usize).cloned()
        }
        _ => None,
    }
}
pub fn evaluate(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    spatial: &impl SceneMaxVmSpatial,
) -> Option<Value> {
    match value {
        AssignmentValue::ArrayEdit { source, operation } => {
            use scenemax_parser::ArrayOperation;
            // Java initializes an empty values list for existing scalar variables.
            // An undeclared variable must remain undeclared.
            let previous = evaluate(source, vars, scope, guards, spatial)?;
            let mut items = previous.as_array().cloned().unwrap_or_default();
            match operation {
                ArrayOperation::Push(value) => items.push(evaluate(value, vars, scope, guards, spatial)?),
                ArrayOperation::Pop => { items.pop(); }
                ArrayOperation::Clear => items.clear(),
                ArrayOperation::Reset(value) => {
                    let value = evaluate(value, vars, scope, guards, spatial)?;
                    items.fill(value);
                }
            }
            Some(Value::Array(items))
        }
        AssignmentValue::RuntimeValue(v) => v.snapshot(),
        AssignmentValue::Literal(v) => Some(v.clone()),
        AssignmentValue::Json(v) => {
            let v = evaluate(v,vars,scope,guards,spatial)?;
            match v { Value::String(s) => serde_json::from_str(&s).ok(), Value::Object(_) | Value::Array(_) => Some(v), _ => None }
        }
        AssignmentValue::Number(n) => n
            .to_string()
            .parse::<f64>()
            .ok()
            .filter(|n| n.is_finite())
            .map(|n| serde_json::json!(n)),
        AssignmentValue::Symbol(name) => lookup(name, vars, scope)
            .or_else(|| spatial.symbol_value(&references::entity_path(name, vars, scope)).map(|n| serde_json::json!(n))),
        AssignmentValue::Array(items) => items
            .iter()
            .map(|v| evaluate(v, vars, scope, guards, spatial))
            .collect::<Option<Vec<_>>>()
            .map(Value::Array),
        AssignmentValue::Access { value, index } => field(
            &evaluate(value, vars, scope, guards, spatial)?,
            &evaluate(index, vars, scope, guards, spatial)?,
        ),
        AssignmentValue::Binary {
            left,
            operator,
            right,
        } => {
            let a = evaluate(left, vars, scope, guards, spatial)?;
            let b = evaluate(right, vars, scope, guards, spatial)?;
            if *operator == ArithmeticOperator::Add && (a.is_string() || b.is_string()) {
                return Some(Value::String(text(&a) + &text(&b)));
            }
            let (a, b) = (number(&a)?, number(&b)?);
            let n = match operator {
                ArithmeticOperator::Add => a + b,
                ArithmeticOperator::Subtract => a - b,
                ArithmeticOperator::Multiply => a * b,
                ArithmeticOperator::Divide if b != 0. => a / b,
                ArithmeticOperator::Modulo if b != 0. => a % b,
                _ => return None,
            };
            n.is_finite().then(|| serde_json::json!(n))
        }
        AssignmentValue::Condition(c) => Some(Value::Bool(condition_matches_scoped(
            c, vars, scope, guards, spatial,
        ))),
        _ => resolve_assignment_value_scoped_with_guards(value, vars, scope, guards, spatial)
            .map(|n| serde_json::json!(n)),
    }
}
pub fn store(vars: &mut SceneMaxVars, name: &str, value: Value) {
    vars.2.remove(name);
    if value.is_array() || value.is_object() { vars.2.insert(name.into(), references::Reference::new(value.clone())); }
    if let Some(n) = number(&value) {
        vars.0.insert(name.into(), n as f32);
    } else {
        vars.0.remove(name);
    }
    vars.1.insert(name.into(), value);
}
pub fn assign(
    name: &str,
    value: Value,
    vars: &mut SceneMaxVars,
    scope: Option<&mut SceneMaxScopeFrame>,
    force_local: bool,
) {
    let _ = references::assign(name, references::Reference::new(value), vars, scope, force_local);
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn java_array_commands_keep_length_and_handle_empty_and_scalar_values() {
        let source = "var items = [1, 2]\nitems.push([true, null])\nitems.pop\nitems.reset(\"filled\")\nvar saved = items\nitems.clear\nitems.pop\nitems.reset(9)\nvar scalar = 3\nscalar.push(7)\nmissing.push(1)";
        let program = scenemax_parser::parse_program(source).unwrap();
        let mut vars = SceneMaxVars::default();
        apply_initial_assignments(&program, &mut vars);
        // Java assigns the same List; clearing through items also clears saved.
        assert_eq!(lookup("saved", &vars, None), Some(serde_json::json!([])));
        assert_eq!(lookup("items", &vars, None), Some(serde_json::json!([])));
        assert!(equal(&lookup("scalar", &vars, None).unwrap(), &serde_json::json!([7])));
        assert_eq!(lookup("missing", &vars, None), None);
    }

    #[test]
    fn array_command_respects_local_shadow_and_nested_expression_operand() {
        let mut vars = SceneMaxVars::default();
        store(&mut vars, "items", serde_json::json!(["global"]));
        let mut scope = SceneMaxScopeFrame::default();
        scope.typed.insert("items".into(), serde_json::json!([]));
        let program = scenemax_parser::parse_program("items.push([\"a,b\", 2 + 3])").unwrap();
        let Statement::Assignment(a) = &program.statements[0] else { panic!("array assignment") };
        apply_assignment_with_spatial(a, &mut vars, Some(&mut scope), &HashMap::new(), &NoSceneMaxVmSpatial, false).unwrap();
        assert!(equal(&lookup("items", &vars, Some(&scope)).unwrap(), &serde_json::json!([["a,b",5]])));
        assert_eq!(lookup("items", &vars, None), Some(serde_json::json!(["global"])));
    }
    fn eval(source: &str, vars: &SceneMaxVars) -> Value {
        let expr = scenemax_parser::parse_assignment_value(source)
            .unwrap()
            .unwrap();
        evaluate(&expr, vars, None, &HashMap::new(), &NoSceneMaxVmSpatial).unwrap()
    }
    #[test]
    fn script_arrays_strings_index_updates_and_numeric_projection() {
        assert!(equal(&serde_json::json!([1, [2]]), &serde_json::json!([1.0, [2.0]])));
        let source = "var title = \"hello, //world\"\nvar items = [1, title, [true, null]]\nvar index = 1\nitems[index] = title + \"!\"\nvar count = items.length\nvar flag = items[2][0]";
        let program = scenemax_parser::parse_program(source).unwrap();
        assert_eq!(program.statements.len(), 6, "{:#?}", program.statements);
        let mut vars = SceneMaxVars::default();
        apply_initial_assignments(&program, &mut vars);
        assert_eq!(
            eval("items[1]", &vars),
            Value::String("hello, //world!".into())
        );
        assert_eq!(eval("items[2][1]", &vars), Value::Null);
        assert_eq!(vars.0["count"], 3.);
        assert_eq!(eval("items.Length", &vars), serde_json::json!(3));
        assert_eq!(eval("items.LENGTH", &vars), serde_json::json!(3));
        assert_eq!(vars.0["flag"], 1.);
        assert_eq!(
            eval("items[1] == \"hello, //world!\"", &vars),
            Value::Bool(true)
        );
    }
    #[test]
    fn session_objects_and_exact_wire_ids_are_script_values() {
        let mut vars = SceneMaxVars::default();
        store(
            &mut vars,
            "network.state",
            serde_json::json!({"ready":true,"sessionId":4294967295u32,"sessionName":"room","sessions":[{"id":16777217,"name":"other","clients":2}]}),
        );
        assert_eq!(
            eval("network.state.sessions[0].name", &vars),
            Value::String("other".into())
        );
        assert_eq!(eval("json(network.state).sessions[0].name", &vars), Value::String("other".into()));
        assert_eq!(eval("network.state.ready == true", &vars), Value::Bool(true));
        assert_eq!(eval("network.state.sessions[0].id > 16777216", &vars), Value::Bool(true));
        store(&mut vars, "encoded", Value::String("{\"name\":\"decoded\"}".into()));
        assert_eq!(eval("JSON(encoded).name", &vars), Value::String("decoded".into()));
        assert_eq!(
            eval("network.state.sessions.length", &vars),
            serde_json::json!(1)
        );
        assert_eq!(
            eval("network.state.sessionId", &vars),
            serde_json::json!(4294967295u32)
        );
        assert_eq!(
            eval("network.state.sessions[0].id == 16777217", &vars),
            Value::Bool(true)
        );
    }
    #[test]
    fn typed_scope_shadowing_and_legacy_numeric_writes_remain_consistent() {
        let mut vars = SceneMaxVars::default();
        store(&mut vars, "value", Value::String("global".into()));
        let mut scope = SceneMaxScopeFrame::default();
        assign(
            "value",
            Value::String("local".into()),
            &mut vars,
            Some(&mut scope),
            true,
        );
        assert_eq!(
            lookup("value", &vars, Some(&scope)),
            Some(Value::String("local".into()))
        );
        assert_eq!(
            lookup("value", &vars, None),
            Some(Value::String("global".into()))
        );
        vars.0.insert("value".into(), 8.);
        assert_eq!(number(&lookup("value", &vars, None).unwrap()), Some(8.));
        let before = vars.clone();
        assign("value[999]", Value::Null, &mut vars, None, false);
        assert_eq!(vars, before);
    }
}
