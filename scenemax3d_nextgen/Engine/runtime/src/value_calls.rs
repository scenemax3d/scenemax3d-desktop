//! Evaluation order for effectful script value calls; execution stays in actions.
use super::*;
use scenemax_runtime_vm_core::references::Reference;
use scenemax_parser::ArrayOperation;

pub(super) enum Request {
    Leaf(AssignmentValue),
    Call(String, Vec<Reference>),
    Acquire(String),
}

pub(super) fn contains(value: &AssignmentValue) -> bool {
    match value {
        AssignmentValue::Call { .. } | AssignmentValue::PoolAcquire { .. } => true,
        AssignmentValue::Array(items) => items.iter().any(contains),
        AssignmentValue::Json(v) | AssignmentValue::Round { value: v } | AssignmentValue::RandomInt { max: v } => contains(v),
        AssignmentValue::Binary { left, right, .. } | AssignmentValue::Access { value: left, index: right } => contains(left) || contains(right),
        AssignmentValue::ArrayEdit { source, operation } => contains(source) || match operation { ArrayOperation::Push(v) | ArrayOperation::Reset(v) => contains(v), _ => false },
        AssignmentValue::Condition(c) => condition_contains(c),
        _ => false,
    }
}
fn condition_contains(c: &Condition) -> bool {
    match c {
        Condition::EqualsValue { left, right } | Condition::NotEqualsValue { left, right } | Condition::CompareValue { left, right, .. } => contains(left) || contains(right),
        Condition::Compare { value, .. } => contains(value),
        Condition::Not(c) => condition_contains(c),
        Condition::And(items) | Condition::Or(items) => items.iter().any(condition_contains),
        _ => false,
    }
}
fn literal(value: Reference) -> Box<AssignmentValue> { Box::new(AssignmentValue::RuntimeValue(value)) }

pub(super) fn evaluate(value: &AssignmentValue, host: &mut impl FnMut(Request) -> Option<Reference>) -> Option<Reference> {
    let resolved = match value {
        AssignmentValue::PoolAcquire { pool } => return host(Request::Acquire(pool.clone())),
        AssignmentValue::Call { name, args } => {
            let args = args.iter().map(|arg| evaluate(arg, host)).collect::<Option<Vec<_>>>()?;
            return host(Request::Call(name.clone(), args));
        }
        AssignmentValue::Array(items) => return items.iter().map(|v| evaluate(v, host)).collect::<Option<Vec<_>>>().map(Reference::array),
        AssignmentValue::Json(v) => AssignmentValue::Json(literal(evaluate(v, host)?)),
        AssignmentValue::Round { value } => AssignmentValue::Round { value: literal(evaluate(value, host)?) },
        AssignmentValue::RandomInt { max } => AssignmentValue::RandomInt { max: literal(evaluate(max, host)?) },
        AssignmentValue::Access { value, index } => AssignmentValue::Access { value: literal(evaluate(value, host)?), index: literal(evaluate(index, host)?) },
        AssignmentValue::Binary { left, operator, right } => AssignmentValue::Binary {
            left: literal(evaluate(left, host)?), operator: *operator, right: literal(evaluate(right, host)?),
        },
        AssignmentValue::ArrayEdit { source, operation } => {
            let source = literal(evaluate(source, host)?);
            let operation = match operation {
                ArrayOperation::Push(v) => ArrayOperation::Push(literal(evaluate(v, host)?)),
                ArrayOperation::Reset(v) => ArrayOperation::Reset(literal(evaluate(v, host)?)),
                other => other.clone(),
            };
            AssignmentValue::ArrayEdit { source, operation }
        }
        AssignmentValue::Condition(c) => return condition(c, host).map(|v| Reference::new(serde_json::Value::Bool(v))),
        other => other.clone(),
    };
    host(Request::Leaf(resolved))
}
fn condition(c: &Condition, host: &mut impl FnMut(Request) -> Option<Reference>) -> Option<bool> {
    let resolved = match c {
        Condition::Not(c) => return condition(c, host).map(|v| !v),
        Condition::And(items) => { for c in items { if !condition(c, host)? { return Some(false); } } return Some(true); }
        Condition::Or(items) => { for c in items { if condition(c, host)? { return Some(true); } } return Some(false); }
        Condition::EqualsValue { left, right } => Condition::EqualsValue { left: *literal(evaluate(left, host)?), right: *literal(evaluate(right, host)?) },
        Condition::NotEqualsValue { left, right } => Condition::NotEqualsValue { left: *literal(evaluate(left, host)?), right: *literal(evaluate(right, host)?) },
        Condition::CompareValue { left, operator, right } => Condition::CompareValue { left: *literal(evaluate(left, host)?), operator: *operator, right: *literal(evaluate(right, host)?) },
        Condition::Compare { name, operator, value } => Condition::CompareValue { left: *literal(evaluate(&AssignmentValue::Symbol(name.clone()), host)?), operator: *operator, right: *literal(evaluate(value, host)?) },
        other => other.clone(),
    };
    host(Request::Leaf(AssignmentValue::Condition(Box::new(resolved))))?.snapshot()?.as_bool()
}

// Only immediate operands are visited. Branch bodies are evaluated when selected,
// and function bodies are evaluated on invocation, never during definition.
pub(super) fn map(action: &mut Statement, visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    match action {
        Statement::Assignment(a) | Statement::SharedAssignment(a) | Statement::LocalAssignment(a) => map_assignment(a, visit)?,
        Statement::ReturnValue { value } | Statement::WaitValue { value } => visit(value)?,
        Statement::Logger(LoggerStatement { message: LoggerMessage::Value(v), .. }) => visit(v)?,
        Statement::If(s) => map_condition(&mut s.condition, visit)?,
        Statement::Guarded { condition, .. } | Statement::WaitUntil { condition } | Statement::LoopContinue { condition, .. } => map_condition(condition, visit)?,
        Statement::SetEnvironmentShader { shader } => visit(shader)?,
        Statement::SetSkybox { skybox } => visit(skybox)?,
        Statement::SetShader(s) => visit(&mut s.shader)?,
        Statement::SetMaterial(s) => visit(&mut s.material)?,
        Statement::ModelDecl { options, .. } => {
            let mut prefix = options.resource_value.iter_mut().collect::<Vec<_>>();
            let mut trailing = options.scale_value.iter_mut().collect::<Vec<_>>();
            trailing.extend(options.geometry_values.iter_mut().flat_map(|attr| attr.values.iter_mut()));
            if let Some(position) = &mut options.position_value {
                map_position_with_prefix(position, &mut prefix, &mut trailing, visit)?;
            } else {
                prefix.extend(trailing);
                map_values(&mut prefix, visit)?;
            }
            if let Some(label) = &mut options.label {
                let mut values = Vec::new();
                if let Some(text) = &mut label.text { values.push(text); }
                if let Some(alpha) = &mut label.transparency { values.push(alpha); }
                map_values(&mut values, visit)?;
            }
        }
        Statement::Ik(s) => {
            let mut values = Vec::new();
            if let scenemax_parser::ik::IkAction::Apply(value) = &mut s.action {
                values.push(value);
            }
            if let Some(value) = &mut s.blend { values.push(value); }
            if let Some(value) = &mut s.weight { values.push(value); }
            map_values(&mut values, visit)?;
        }
        Statement::EffekseerPlay(s) => {
            let mut attrs = s.attrs.iter_mut().map(|(_, value)| value).collect::<Vec<_>>();
            if let Some(position) = &mut s.position { map_position_values(position, &mut attrs, visit)?; }
            else { map_values(&mut attrs, visit)?; }
        }
        Statement::Position(s) => map_position(&mut s.position, None, visit)?,
        Statement::RotationReset { rotation, .. } => map_position(rotation, None, visit)?,
        Statement::Attach(s) => { if let Some(offset) = &mut s.offset_value { map_position(offset, None, visit)?; } }
        Statement::Move(s) => map_values(&mut [&mut s.distance_value, &mut s.duration_value], visit)?,
        Statement::Turn(s) => map_values(&mut [&mut s.degrees_value, &mut s.duration_value], visit)?,
        Statement::CameraMove(s) => map_values(&mut [&mut s.distance_value, &mut s.duration_value], visit)?,
        Statement::MoveTo(s) => match &mut s.destination {
            MoveToDestination::Delta(p) | MoveToDestination::Position(p) => map_position(p, Some(&mut s.duration_value), visit)?,
            MoveToDestination::EntityForward { distance_value, .. } => map_values(&mut [distance_value, &mut s.duration_value], visit)?,
        },
        Statement::Animate(s) => visit(&mut s.speed_value)?,
        Statement::SpritePlay(s) => {
            let mut values = vec![&mut s.from_frame_value, &mut s.to_frame_value, &mut s.duration_value];
            values.extend(s.playback_seconds.iter_mut());
            map_values(&mut values, visit)?;
        }
        Statement::UiSetProperty(s) => {
            use scenemax_parser::{UiPropertyValue, UiPropertyValuePart};
            match &mut s.value {
                UiPropertyValue::Expression(value) => visit(value)?,
                UiPropertyValue::Concatenation(parts) => {
                    let mut values = parts.iter_mut().filter_map(|part| match part {
                        UiPropertyValuePart::Expression(value) => Some(value), _ => None,
                    }).collect::<Vec<_>>();
                    map_values(&mut values, visit)?;
                }
                _ => {}
            }
        }

        // RunFunction arguments bind references in the executor, not source strings.
        Statement::Network(network) => {
            use scenemax_parser::network::NetworkStatement;
            match network {
                NetworkStatement::Variable { value, .. } | NetworkStatement::EntityData { value, .. }
                    | NetworkStatement::JoinSession { value } => map_source(value, visit)?,
                NetworkStatement::Broadcast { event, message } | NetworkStatement::Send { event, message, .. } => {
                    let mut sources = vec![event];
                    if let Some(message) = message { sources.push(message); }
                    map_sources(&mut sources, visit)?;
                }
                NetworkStatement::On { event, argument, .. } => {
                    map_source(event, visit)?;
                    if let Some(argument) = argument { map_source(argument, visit)?; }
                }
            }
        }
        _ => {}
    }
    Some(())
}
fn map_values(values: &mut [&mut AssignmentValue], visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    let mut batch = AssignmentValue::Array(values.iter().map(|v| (**v).clone()).collect());
    if contains(&batch) {
        visit(&mut batch)?;
        if let AssignmentValue::RuntimeValue(resolved) = batch {
            for (target, value) in values.iter_mut().zip(resolved.elements()?) { **target = AssignmentValue::RuntimeValue(value); }
        }
    }
    Some(())
}
fn map_assignment(a: &mut scenemax_parser::AssignmentStatement, visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    // Keep the mutating operation intact: reducing it to a JSON literal would
    // replace a shared list instead of editing the existing collection.
    if let AssignmentValue::ArrayEdit { operation, .. } = &mut a.value {
        if let ArrayOperation::Push(value) | ArrayOperation::Reset(value) = operation { visit(value)?; }
        return Some(());
    }
    fn path(value: AssignmentValue, indices: &mut Vec<AssignmentValue>) -> Option<String> {
        match value {
            AssignmentValue::Symbol(name) => Some(name),
            AssignmentValue::Access { value, index } => { let root = path(*value, indices)?; indices.push(*index); Some(root) }
            _ => None,
        }
    }
    if a.name.contains('[') {
        if let Ok(Some(target)) = scenemax_parser::parse_assignment_value(&a.name) {
            let mut indices = Vec::new();
            if let Some(root) = path(target, &mut indices) {
                // Reference VariableAssignmentController evaluates RHS before index.
                let mut batch = vec![a.value.clone()];
                batch.extend(indices);
                let mut value = AssignmentValue::Array(batch);
                if contains(&value) {
                    visit(&mut value)?;
                    if let AssignmentValue::RuntimeValue(values) = value {
                        let mut values = values.elements()?.into_iter();
                        a.value = AssignmentValue::RuntimeValue(values.next()?);
                        a.name = root;
                        for index in values { a.name.push_str(&format!("[{}]", index.snapshot()?)); }
                    }
                    return Some(());
                }
            }
        }
    }
    visit(&mut a.value)
}
fn map_position(position: &mut PositionValue, trailing: Option<&mut AssignmentValue>, visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    let mut trailing = trailing.into_iter().collect::<Vec<_>>();
    map_position_values(position, &mut trailing, visit)
}
fn map_position_values(position: &mut PositionValue, trailing: &mut [&mut AssignmentValue], visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    map_position_with_prefix(position, &mut [], trailing, visit)
}
fn map_position_with_prefix(position: &mut PositionValue, prefix: &mut [&mut AssignmentValue], trailing: &mut [&mut AssignmentValue], visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    let PositionValue::Coordinates(coords) = position else {
        let mut refs = prefix.iter_mut().map(|v| &mut **v).collect::<Vec<_>>();
        refs.extend(trailing.iter_mut().map(|v| &mut **v));
        return map_values(&mut refs, visit);
    };
    let mut values = coords.iter().map(|coord| match coord {
        PositionExpr::Number(n) => AssignmentValue::Number(*n),
        PositionExpr::Value(value) => value.clone(),
        PositionExpr::EntityAxis { entity, axis, offset } => AssignmentValue::Binary {
            left: Box::new(AssignmentValue::Symbol(format!("{entity}.{}", match axis { SceneMaxAxis::X => "x", SceneMaxAxis::Y => "y", SceneMaxAxis::Z => "z" }))),
            operator: scenemax_parser::ArithmeticOperator::Add, right: Box::new(AssignmentValue::Number(*offset)),
        },
    }).collect::<Vec<_>>();
    let mut refs = prefix.iter_mut().map(|v| &mut **v).collect::<Vec<_>>();
    refs.extend(values.iter_mut());
    for value in trailing { refs.push(&mut **value); }
    map_values(&mut refs, visit)?;
    *coords = values.into_iter().map(PositionExpr::Value).collect();
    Some(())
}
fn map_sources(sources: &mut [&mut String], visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    let values = sources.iter().map(|s| scenemax_parser::parse_assignment_value(s).ok().flatten()).collect::<Option<Vec<_>>>();
    if let Some(values) = values {
        let mut value = AssignmentValue::Array(values);
        if contains(&value) {
            visit(&mut value)?;
            if let AssignmentValue::RuntimeValue(values) = value {
                for (source, value) in sources.iter_mut().zip(values.elements()?) { **source = value.snapshot()?.to_string(); }
            }
        }
    }
    Some(())
}
fn map_source(source: &mut String, visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    if let Ok(Some(mut value)) = scenemax_parser::parse_assignment_value(source) {
        if contains(&value) {
            visit(&mut value)?;
            if let AssignmentValue::RuntimeValue(value) = value { *source = value.snapshot()?.to_string(); }
        }
    }
    Some(())
}
fn map_condition(c: &mut Condition, visit: &mut impl FnMut(&mut AssignmentValue) -> Option<()>) -> Option<()> {
    let mut value = AssignmentValue::Condition(Box::new(c.clone()));
    visit(&mut value)?;
    *c = match value {
        AssignmentValue::Condition(c) => *c,
        AssignmentValue::RuntimeValue(v) => Condition::Boolean(v.snapshot()?.as_bool()?),
        _ => return None,
    };
    Some(())
}
pub(super) fn action_contains(action: &Statement) -> bool {
    if let Statement::DoWhile { condition, .. } = action { return condition_contains(condition); }
    let mut found = false;
    let _ = map(&mut action.clone(), &mut |value| { found |= contains(value); Some(()) });
    found
}

// Textual resource/widget operands are resolved when executed. Expressions keep
// their variable bindings, and nested bodies are not frozen before they run.
pub(super) fn resolve_text_operands(action: &Statement, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>) -> Option<Statement> {
    if let Some(action) = resolve_ui_reference(action, vars, scope) { return Some(action); }
    let scope = scope?;
    let bindings = scope.parameters.iter().filter_map(|name| {
        scenemax_runtime_vm_core::typed::lookup(name, vars, Some(scope))?.as_str().map(|v| (name.clone(), v.to_owned()))
    }).collect::<HashMap<_,_>>();
    if bindings.is_empty() { return None; }
    use scenemax_runtime_script_core::{substitute_reference, substitute_ui_target_path};
    let mut action = action.clone();
    match &mut action {
        Statement::UiLoad { name } => *name = substitute_reference(name, &bindings),
        Statement::ChannelDraw(s) => { s.resource = substitute_reference(&s.resource, &bindings); s.channel = substitute_reference(&s.channel, &bindings); }
        Statement::UiMessage(s) => { s.target = substitute_ui_target_path(&s.target, &bindings); s.text = substitute_reference(&s.text, &bindings); }
        Statement::UiShowHide(s) => s.target = substitute_ui_target_path(&s.target, &bindings),
        Statement::UiEase(s) => s.target = substitute_ui_target_path(&s.target, &bindings),
        Statement::UiSetProperty(s) => s.target = substitute_ui_target_path(&s.target, &bindings),
        _ => return None,
    }
    Some(action)
}

/// Resolve widget aliases before dispatch so local and multiplayer edits use
/// exactly the same UI action queue as explicit UI paths.
fn resolve_ui_reference(action: &Statement, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>) -> Option<Statement> {
    use scenemax_parser::{UiShowHideStatement, UiSetPropertyStatement, UiPropertyValue, parse_ui_target_path};
    let target = |name: &str| {
        let value = scenemax_parser::parse_assignment_value(name).ok()??;
        let reference = scenemax_runtime_vm_core::references::evaluate(
            &value, vars, scope, &HashMap::new(), &scenemax_runtime_vm_core::NoSceneMaxVmSpatial)?;
        parse_ui_target_path(&reference.ui_path()?)
    };
    match action {
        Statement::Visibility { target: name, visible } => Some(Statement::UiShowHide(UiShowHideStatement {
            target: target(name)?, visible: *visible,
        })),
        Statement::LabelText { target: name, value } => Some(Statement::UiSetProperty(UiSetPropertyStatement {
            target: target(name)?, property: "text".into(), value: UiPropertyValue::Expression(value.clone()),
        })),
        Statement::Assignment(assignment) => {
            let (name, property) = assignment.name.rsplit_once('.')?;
            Some(Statement::UiSetProperty(UiSetPropertyStatement {
                target: target(name)?, property: property.into(), value: UiPropertyValue::Expression(assignment.value.clone()),
            }))
        }
        Statement::UiEase(ease) if ease.target_reference.is_some() => {
            let mut ease = ease.clone();
            let name = ease.target_reference.take()?;
            let Some(resolved) = target(&name) else {
                return Some(Statement::Unsupported { text: format!("UI ease target '{name}' is not a UI reference") });
            };
            ease.target = resolved;
            Some(Statement::UiEase(ease))
        }
        _ => None,
    }
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[derive(Resource)]
    struct Batch(Vec<Statement>);
    #[derive(Resource)]
    struct ResumeScope(SceneMaxScopeFrame);
    #[derive(Resource)]
    pub(crate) struct ContinuousDelta(pub(crate) f32);
    #[test]
    fn for_each_model_keeps_entity_references_and_resumes_after_waits() {
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        for (name, model, id) in [("alpha", true, 17), ("beta", true, 18), ("shape", false, 19)] {
            let mut entity = world.spawn((SceneMaxEntity { name: name.into(), runtime_name: name.into() }, Transform::default()));
            if model { entity.insert(SceneMaxGltf { gltf: Handle::default(), animation_names: vec![] }); }
            scenemax_runtime_vm_core::typed::store(&mut world.resource_mut::<SceneMaxVars>(), &format!("{name}.network.id"), serde_json::json!(id));
        }
        run_in(&mut world, "var matches=[]\nvar visited=0\nfor each model (m) do\nif (m.network.id > 0) {\nmatches.push(m)\n}\nwait 0.1 seconds\nvisited=visited+1\nend do\nvar chosen=matches[0]\nvar result=chosen.network.id\nvar complete=1");
        while !world.resource::<DelayedActionQueue>().actions.is_empty() { resume_next(&mut world); }
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["visited"], 2.);
        assert_eq!(vars.0["result"], 17.);
        assert_eq!(vars.0["complete"], 1.);
        assert!(!vars.2.contains_key("m"));
        assert_eq!(scenemax_runtime_vm_core::typed::lookup("matches.length", vars, None), Some(serde_json::json!(2)));
        let empty = run("var visited=0\nfor each model (m) do\nvisited=visited+1\nend do\nvar complete=1");
        assert_eq!(empty.resource::<SceneMaxVars>().0["visited"], 0.);
        assert_eq!(empty.resource::<SceneMaxVars>().0["complete"], 1.);
    }
    fn execute(
        mut commands: Commands,
        batch: Res<Batch>,
        resume: Option<Res<ResumeScope>>,
        continuous: Option<Res<ContinuousDelta>>,
        mut vars: ResMut<SceneMaxVars>,
        mut pools: ResMut<SceneMaxObjectPools>,
        mut assets: ResMut<SceneMaxRuntimeAssets>,
        mut delayed: ResMut<DelayedActionQueue>,
        mut bounds: ResMut<SceneMaxColliderBounds>,
        mut cameras: Option<ResMut<SceneMaxCameraSystem>>,
        mut ui_queue: Option<ResMut<SceneMaxUiActionQueue>>,
        mut entities: ParamSet<(
            Query<(
                Entity,
                &SceneMaxEntity,
                &Transform,
                Option<&GlobalTransform>,
                Option<&ChildOf>,
            )>,
            Query<(
                Entity,
                &SceneMaxEntity,
                &mut Transform,
                Option<&SceneMaxGltf>,
                Option<&CurrentAnimation>,
                Option<&mut Visibility>,
                Option<&SceneMaxCharacterController>,
                Option<&mut SceneMaxCharacterMotor>,
            )>,
        )>,
    ) {
        let mut transforms = entities
            .p0()
            .iter()
            .map(|(_, s, t, _, _)| (s.name.clone(), *t))
            .collect();
        let mut scope = resume.map(|r| r.0.clone());
        apply_action_sequence(
            &batch.0,
            &mut transforms,
            &mut vars,
            &mut pools,
            cameras.as_deref_mut(),
            &collect_functions_by_name(&Program::new(batch.0.clone())),
            &HashMap::new(),
            &mut HashMap::new(),
            &mut assets,
            &SceneMaxAnimationDurations::default(),
            &mut bounds,
            Some(&mut delayed),
            ui_queue.as_deref_mut(),
            None,
            scope.as_mut(),
            continuous.as_ref().map(|value| value.0),
            &mut commands,
            &mut entities,
        );
    }

    fn run(source: &str) -> World {
        let mut world = World::new();
        run_in(&mut world, source);
        world
    }
    pub(crate) fn run_in(world: &mut World, source: &str) {

        world.init_resource::<SceneMaxVars>();
        world.init_resource::<SceneMaxObjectPools>();
        world.init_resource::<SceneMaxRuntimeAssets>();
        world.init_resource::<SceneMaxColliderBounds>();
        world.init_resource::<DelayedActionQueue>();
        world.insert_resource(Batch(scenemax_parser::parse_program(source).unwrap().statements));
        world.run_system_once(execute).unwrap();
    }
    pub(crate) fn resume_next(world: &mut World) {
        let pending = world.resource_mut::<DelayedActionQueue>().actions.remove(0);
        let mut actions = world.resource::<Batch>().0.iter().filter(|s| matches!(s, Statement::FunctionDef(_))).cloned().collect::<Vec<_>>();
        actions.extend(pending.actions);
        world.insert_resource(Batch(actions));
        world.remove_resource::<ResumeScope>();
        if let Some(scope) = pending.scope { world.insert_resource(ResumeScope(scope)); }
        world.run_system_once(execute).unwrap();
    }
    #[test]
    fn ui_references_survive_arrays_function_arguments_and_async_locals() {
        let mut world = World::new();
        world.init_resource::<SceneMaxUiActionQueue>();
        run_in(&mut world, r#"
var widgets = [UI.hud.layer1.panel, UI.hud.layer1.panel.caption]
var descriptions = ["First", "Second"]
display(items, action) = {
 var panel = items[0]
 var caption = items[1]
 panel.Show
 caption.text = descriptions[action-1]
 panel.ease("EaseOutBounce", Right, 2) async
}
run display(widgets, 2) async
"#);
        resume_next(&mut world);
        let actions = &world.resource::<SceneMaxUiActionQueue>().actions;
        assert_eq!(actions.len(), 3, "{actions:?}");
        assert!(matches!(&actions[0], SceneMaxUiAction::ShowHide { target, visible: true }
            if target.ui_name.as_deref() == Some("hud") && target.widget_path == ["panel"]));
        assert!(matches!(&actions[1], SceneMaxUiAction::SetProperty { target, property, value }
            if target.widget_path == ["panel", "caption"] && property == "text" && value == "Second"));
        assert!(matches!(&actions[2], SceneMaxUiAction::Ease { target, easing, direction: scenemax_parser::UiEaseDirection::Right, duration_seconds: 2., .. }
            if target.widget_path == ["panel"] && easing == "EaseOutBounce"));
        assert_eq!(world.resource::<SceneMaxVars>().2["widgets"].elements().unwrap()[0].ui_path().as_deref(), Some("UI.hud.layer1.panel"));
    }

    #[test]
    fn ui_aliases_support_global_visibility_and_properties_without_redirecting_labels() {
        let mut world = World::new();
        world.init_resource::<SceneMaxUiActionQueue>();
        run_in(&mut world, r#"
var widget = UI.layer1.panel
widget.Hide
widget.fontSize = 24
"#);
        let actions = &world.resource::<SceneMaxUiActionQueue>().actions;
        assert_eq!(actions.len(), 2, "{actions:?}");
        assert!(matches!(&actions[0], SceneMaxUiAction::ShowHide { visible: false, .. }));
        assert!(matches!(&actions[1], SceneMaxUiAction::SetProperty { property, value, .. } if property == "fontSize" && value == "24"));
        let ordinary = Statement::LabelText { target: "world_label".into(), value: scenemax_parser::parse_assignment_value("\"Unchanged\"").unwrap().unwrap() };
        assert!(resolve_ui_reference(&ordinary, world.resource::<SceneMaxVars>(), None).is_none());
        run_in(&mut world, "var layer1 = UI.layer1.panel\nUI.layer1.ease(\"EaseOutBounce\", Left, 1) async");
        assert!(matches!(world.resource::<SceneMaxUiActionQueue>().actions.last(), Some(SceneMaxUiAction::Ease { target, .. })
            if target.layer == "layer1" && target.widget_path.is_empty()));
    }
    #[test]
    fn for_loops_return_without_increment_or_running_the_tail() {
        let world = run(r#"
var calls = 0
find() = {
 for (var i=0;i<4;i=i+1) {
  calls = calls + 1
  if (i == 1) {
   return i
  }
 }
 return 99
}
var answer = find()
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["answer"], 1.);
        assert_eq!(vars.0["calls"], 2.);
    }

    #[test]
    fn for_loops_build_rows_with_nested_loops_and_waits() {
        let mut world = run(r#"
var rows = ""
var rooms = ["North", "South"]
var completed = 0
populate = {
 for (var j=0;j<rooms.Length;j=j+1) {
  var count = 0
  for (var i=0;i<2;i=i+1) {
   wait 0.01 seconds
   count = count + 1
  }
  rows = rows + rooms[j] + "|" + count + ";"
 }
}
run populate
for (var k=0;k<0;k=k+1) {
 rows = "wrong"
}
completed = completed + 1
"#);
        for _ in 0..50 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.1["rows"], serde_json::json!("North|2;South|2;"));
        assert_eq!(vars.0["completed"], 1.);
    }

    #[test]
    fn rechecked_function_guard_stops_delayed_continuation_when_false() {
        for nested_async in [false, true] {
            for guard_still_true in [false, true] {
                let body = "before = 1\nwait 0.1 seconds\nafter = 1";
                let body = if nested_async { format!("do async\n{body}\nend do") } else { body.into() };
                let mut world = run(&format!("var can_go = 1\nvar before = 0\nvar after = 0\nvar finished = 0\n#[can_go == 1]\nhandler = {{\n{body}\n}}\nrun handler\nfinished = 1"));
                if nested_async { resume_next(&mut world); }
                assert_eq!(world.resource::<SceneMaxVars>().0["before"], 1.);
                assert_eq!(world.resource::<SceneMaxVars>().0["after"], 0.);
                world.resource_mut::<SceneMaxVars>().0.insert("can_go".into(), if guard_still_true { 1. } else { 0. });
                resume_next(&mut world);
                let vars = world.resource::<SceneMaxVars>();
                assert_eq!(vars.0["after"], if guard_still_true { 1. } else { 0. });
                assert_eq!(vars.0["finished"], 1.);
                assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
            }
        }
    }
    #[test]
    fn value_functions_execute_side_effects_once_in_argument_order_and_keep_locals_private() {
        let world = run(r#"
var calls = 0
var label = "outer"
next(value) = {
 calls = calls + 1
 var label = "inner"
 return [value, calls, label]
 calls = 999
}
combine(first, second) = {
 return [first, second]
}
var result = combine(next(4), next(8))
var chosen = next(12)[1]
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["calls"], 3.);
        assert_eq!(vars.0["chosen"], 3.);
        assert_eq!(vars.1["label"], serde_json::json!("outer"));
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["result"], &serde_json::json!([[4,1,"inner"],[8,2,"inner"]])));
        assert!(!vars.1.contains_key("first"));
    }
    #[test]
    fn return_inside_branch_stops_function_but_caller_continues() {
        let world = run(r#"
choose(value) = {
 if (value > 2) {
  return "large"
 }
 return "small"
}
var large = choose(4)
var small = choose(1)
var finished = 1
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.1["large"], serde_json::json!("large"));
        assert_eq!(vars.1["small"], serde_json::json!("small"));
        assert_eq!(vars.0["finished"], 1.);
    }
    #[test]
    fn calls_preserve_expression_read_order_and_boolean_short_circuit() {
        let world = run(r#"
var count = 1
increment() = {
 count = count + 1
 return count
}
var result = count + increment() + count
var skipped = (false && increment() == 3)
var skipped_again = (true || increment() == 3)
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["result"], 5.);
        assert_eq!(vars.0["count"], 2.);
        assert_eq!(vars.0["skipped"], 0.);
        assert_eq!(vars.0["skipped_again"], 1.);
    }
    #[test]
    fn value_function_runs_normal_scene_actions_and_nested_void_functions() {
        let mut world = run(r#"
var changed = 0
mark() = {
 changed = 1
 return
}
create_value() = {
 run mark()
 created => box: pos (1,2,3)
 return 7
}
var result = create_value()
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["result"], 7.);
        assert_eq!(world.resource::<SceneMaxVars>().0["changed"], 1.);
        let found = world.query::<(&SceneMaxEntity, &Transform)>().iter(&world)
            .next().map(|(_, transform)| transform.translation);
        assert_eq!(found, Some(Vec3::new(1.,2.,3.)));
    }
    #[test]
    fn synchronous_loops_recheck_value_calls_after_each_body() {
        let world = run(r#"
var checked = 0
continue_to(value) = {
 checked = checked + 1
 return value < 3
}
count_to_three() = {
 var count = 0
 do
  count = count + 1
 while continue_to(count) == true
 return count
}
var result = count_to_three()
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["result"], 3.);
        assert_eq!(vars.0["checked"], 3.);
    }
    #[test]
    fn delayed_loop_retains_authored_value_condition_for_next_iteration() {
        let mut world = run(r#"
var count = 0
keep_going() = {
 return count < 2
}
do
 count = count + 1
while keep_going() == true
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["count"], 1.);
        let queue = world.resource::<DelayedActionQueue>();
        assert_eq!(queue.actions.len(), 1);
        assert!(matches!(&queue.actions[0].actions[0], Statement::EndScope));
        assert!(matches!(&queue.actions[0].actions[1], Statement::DoWhile { condition, .. }
            if condition_contains(condition)));
        resume_next(&mut world);
        assert_eq!(world.resource::<SceneMaxVars>().0["count"], 2.);
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    }
    #[test]
    fn value_functions_allow_immediate_waits_and_recheck_effectful_predicates() {
        let world = run(r#"
var checks = 0
ready() = {
 checks = checks + 1
 return checks >= 3
}
immediate(delay) = {
 wait delay seconds
 wait for ready() == true
 return checks
}
var result = immediate(0)
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["result"], 3.);
        assert_eq!(vars.0["checks"], 3.);
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    }
    #[test]
    fn null_and_unfinished_condition_are_not_successful_value_results() {
        let world = run(r#"
null_value() = {
 return null
}
never_ready() = {
 wait for false
 return 7
}
unknown_delay() = {
 wait missing_duration seconds
 return 9
}
var rejected_null = null_value()
var rejected_condition = never_ready()
var rejected_duration = unknown_delay()
var finished = 1
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert!(!vars.1.contains_key("rejected_null"));
        assert!(!vars.1.contains_key("rejected_condition"));
        assert!(!vars.1.contains_key("rejected_duration"));
        assert_eq!(vars.0["finished"], 1.);
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    }
    #[test]
    fn blocking_motion_cannot_publish_a_return_or_leak_a_continuation() {
        let world = run(r#"
var after_motion = 0
timed() = {
 moving_subject => box
 moving_subject.move (x + 2) in 1 seconds
 after_motion = 1
 return 4
}
wrapper() = {
 run timed()
 return 9
}
var rejected_motion = wrapper()
var finished = 1
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["after_motion"], 0.);
        assert!(!vars.1.contains_key("rejected_motion"));
        assert_eq!(vars.0["finished"], 1.);
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    }
    #[test]
    fn indexed_assignment_evaluates_rhs_before_destination_indices_once() {
        let world = run(r#"
var trace = ""
var grid = [[0,0],[0,0]]
rhs() = {
 trace = trace + "R"
 return 9
}
row() = {
 trace = trace + "A"
 return 1
}
column() = {
 trace = trace + "B"
 return 0
}
grid[row()][column()] = rhs()
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.1["trace"], serde_json::json!("RAB"));
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["grid"], &serde_json::json!([[0,0],[9,0]])));
    }
    #[test]
    fn position_operands_freeze_reads_before_and_after_an_effectful_call() {
        let mut world = run(r#"
var coordinate = 1
advance() = {
 coordinate = coordinate + 1
 return coordinate
}
place() = {
 positioned => box
 positioned.pos = (coordinate, advance(), coordinate)
 positioned.rotate (0,0,90)
 positioned.hide
 return positioned.x
}
var done = place()
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["done"], 1.);
        assert_eq!(world.resource::<SceneMaxVars>().0["coordinate"], 2.);
        let position = world.query::<(&SceneMaxEntity, &Transform)>().iter(&world)
            .next().map(|(_, transform)| transform.translation);
        assert_eq!(position, Some(Vec3::new(1.,2.,2.)));
        let (_, transform, visibility) = world.query::<(&SceneMaxEntity, &Transform, &Visibility)>().iter(&world)
            .next().unwrap();
        assert!(transform.rotation.abs_diff_eq(Quat::from_rotation_z(std::f32::consts::FRAC_PI_2), 0.0001));
        assert_eq!(*visibility, Visibility::Hidden);
    }

    #[test]
    fn ik_function_operands_execute_once_in_reference_evaluation_order() {
        let mut world = run(r#"
var trace = 0
asset() = {
 trace = trace * 10 + 1
 return null
}
blend() = {
 trace = trace * 10 + 2
 return 0.1
}
weight() = {
 trace = trace * 10 + 3
 return 0.5
}
subject => box
subject.ik = asset()
subject.ik.contact.play: weight weight(), blend blend()
"#);
        while !world.resource::<DelayedActionQueue>().actions.is_empty() {
            resume_next(&mut world);
        }
        assert_eq!(world.resource::<SceneMaxVars>().0["trace"], 123.);
    }
    #[test]
    fn movement_operands_resolve_calls_in_source_order() {
        let mut action = scenemax_parser::parse_program("subject.move forward amount() in duration() seconds async").unwrap().statements.remove(0);
        let mut called = Vec::new();
        map(&mut action, &mut |value| {
            let resolved = evaluate(value, &mut |request| match request {
                Request::Acquire(_) => panic!("unexpected pool acquisition"),
                Request::Call(name, args) => { assert!(args.is_empty()); called.push(name); Some(Reference::new(serde_json::json!(called.len()))) }
                Request::Leaf(value) => scenemax_runtime_vm_core::references::evaluate(&value, &SceneMaxVars::default(), None,
                    &HashMap::new(), &scenemax_runtime_vm_core::NoSceneMaxVmSpatial),
            })?;
            *value = AssignmentValue::RuntimeValue(resolved);
            Some(())
        }).unwrap();
        assert_eq!(called, ["amount", "duration"]);
        let Statement::Move(movement) = action else { panic!() };
        assert!(matches!(movement.distance_value, AssignmentValue::RuntimeValue(v) if v.snapshot() == Some(serde_json::json!(1))));
        assert!(matches!(movement.duration_value, AssignmentValue::RuntimeValue(v) if v.snapshot() == Some(serde_json::json!(2))));
    }
    #[test]
    fn local_array_alias_with_call_operand_mutates_shared_collection() {
        let world = run(r#"
var values = [1]
identity(value) = {
 return value
}
edit() = {
 var alias = values
 alias.push(identity(2))
 return values.length
}
var count = edit()
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["count"], 2.);
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["values"], &serde_json::json!([1,2])));
        assert!(!vars.1.contains_key("alias"));
    }
    #[test]
    fn array_arguments_returns_and_nested_results_preserve_identity() {
        let world = run(r#"
var original = [1]
change(items) = {
 items.push(2)
 return items
}
identity(items) = {
 return items
}
var returned = change(original)
returned.push(3)
var parent = [original, identity(original)]
var child = identity(parent)[1]
child[0] = 7
"#);
        let vars = world.resource::<SceneMaxVars>();
        for name in ["original", "returned", "child"] {
            assert!(scenemax_runtime_vm_core::typed::equal(&vars.1[name], &serde_json::json!([7,2,3])), "{name}: {:?}", vars.1[name]);
        }
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["parent"], &serde_json::json!([[7,2,3],[7,2,3]])));
    }
    #[test]
    fn rebinding_an_array_parameter_detaches_only_that_parameter() {
        let world = run(r#"
var original = [1]
replace(items) = {
 items.push(2)
 items = [9]
 return items
}
var returned = replace(original)
returned.push(10)
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["original"], &serde_json::json!([1,2])));
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["returned"], &serde_json::json!([9,10])));
        assert!(!vars.1.contains_key("items"));
    }
    #[test]
    fn scoped_redeclaration_does_not_redirect_saved_reference() {
        let mut world = run(r#"
var saved = 0
make() = {
 subject => box
 saved = subject
 subject => sphere: pos (7,0,0)
 saved.hide
 return subject
}
var fresh = make()
"#);
        let vars = world.resource::<SceneMaxVars>();
        let saved = scenemax_runtime_vm_core::references::lookup("saved", vars, None).unwrap().entity_identity().unwrap();
        let fresh = scenemax_runtime_vm_core::references::lookup("fresh", vars, None).unwrap().entity_identity().unwrap();
        assert_ne!(saved, fresh);
        let current = world.query::<(&SceneMaxEntity, &Visibility)>().iter(&world).collect::<Vec<_>>();
        assert_eq!(current.len(), 1);
        assert_eq!(current[0].0.name, fresh.runtime_name);
        assert_eq!(*current[0].1, Visibility::Inherited);
    }

    #[test]
    fn pool_factories_create_once_and_reuse_identity_transform_and_fifo_order() {
        let mut world = run(r#"
var calls = 0
make() = {
 calls = calls + 1
 if (calls > 0) {
  subject => box
  subject.pos = (calls,0,0)
  return subject
 }
 calls = 999
}
items => Object.Pool(make, size 2)
var initial_calls = calls
var first = items.acquire
first.pos = (12,0,0)
items.release first
var second = items.acquire
var reused = items.acquire
var same = first == reused
var reused_x = reused.x
var before_growth = calls
var extra = items.acquire
var after_growth = calls
"#);
        for _ in 0..12 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["initial_calls"], 2.);
        assert_eq!(vars.0["before_growth"], 2.);
        assert_eq!(vars.0["after_growth"], 3.);
        assert_eq!(vars.0["same"], 1.);
        assert_eq!(vars.0["reused_x"], 12.);
        assert_ne!(vars.2["first"], vars.2["second"]);
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 3);
    }

    #[test]
    fn pool_factory_uses_declaration_scope_and_duplicate_declaration_is_inert() {
        let mut world = run(r#"
var calls = 0
make() = {
 calls = calls + 1
 subject => box
 subject.pos = (captured,0,0)
 return subject
}
owner() = {
 var captured = 7
 items => Object.Pool(make, size 1)
 items => Object.Pool(make, size 8)
 var first = items.acquire
 return first
}
var result = owner()
var location = result.x
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["calls"], 1.);
        assert_eq!(world.resource::<SceneMaxVars>().0["location"], 7.);
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 1);
    }

    #[test]
    fn pool_values_preserve_identity_through_aliases_arguments_returns_and_rebinding() {
        let mut world = run(r#"
var creates = 0
make() = {
 creates = creates + 1
 subject => box: pos (creates,0,0)
 return subject
}
owner() = {
 items => Object.Pool(make, size 1)
 return items
}
get(from) = {
 return from.acquire
}
put(into, member) = {
 into.release member
 return 1
}
var source = owner()
var saved = [source]
var alias = saved[0]
var first = get(alias)
var released = put(alias, first)
var reused = get(source)
var same_member = first == reused
var same_pool = source == alias
source = 0
source => Object.Pool(make, size 0)
var different = source != alias
var other = get(source)
var old_extra = get(alias)
var count = creates
"#);
        for _ in 0..12 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["same_member"], 1.);
        assert_eq!(vars.0["same_pool"], 1.);
        assert_eq!(vars.0["different"], 1.);
        assert_eq!(vars.0["count"], 3.);
        assert!(vars.2["source"].pool_identity().is_some());
        assert!(vars.2["source"].entity_identity().is_none());
        assert!(vars.2["source"].snapshot().is_none());
        assert_eq!(vars.2["saved"].snapshot(), Some(serde_json::json!([null])));
    }

    #[test]
    fn retained_pool_growth_uses_current_factory_and_helper_definitions() {
        let mut world = run(r#"
location() = {
 return 17
}
make() = {
 subject => box
 subject.pos = (location(),0,0)
 return subject
}
shared items => Object.Pool(make, size 0)
shared var holder = [items]
"#);
        assert!(world.resource::<SceneMaxObjectPools>().shared_variables.contains("items"));
        assert!(crate::shared_entities::pool_names(world.resource::<SceneMaxVars>(), world.resource::<SceneMaxObjectPools>()).contains("items"));
        run_in(&mut world, r#"
location() = {
 return 99
}
make() = {
 subject => sphere
 subject.pos = (location(),0,0)
 return subject
}
shared items => Object.Pool(make, size 99)
var alias = holder[0]
var created = alias.acquire
var position = created.x
"#);
        for _ in 0..4 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        assert_eq!(world.resource::<SceneMaxVars>().0["position"], 99.);
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 1);
    }

    #[test]
    fn retained_pool_reachability_includes_pools_in_captured_local_bindings() {
        let world = run(r#"
make() = {
 subject => box
 return subject
}
from_parent() = {
 return parent.acquire
}
owner() = {
 parent => Object.Pool(make, size 0)
 result => Object.Pool(from_parent, size 0)
 return result
}
shared var keeper = owner()
"#);
        let pools = world.resource::<SceneMaxObjectPools>();
        let retained = crate::shared_entities::pool_names(world.resource::<SceneMaxVars>(), pools);
        assert_eq!(retained.len(), 2);
        assert!(pools.pools.keys().all(|name| retained.contains(name)));
        assert!(crate::shared_entities::pool_names(&SceneMaxVars::default(), pools).is_empty());
    }

    #[test]
    fn root_pool_rebinding_keeps_saved_pool_and_blocks_scalar_acquisition() {
        let mut world = run(r#"
make() = {
 subject => box
 return subject
}
get(from) = {
 return from.acquire
}
items => Object.Pool(make, size 1)
var saved = items
var first = get(saved)
saved.release first
items = 0
var invalid = items.acquire
items => Object.Pool(make, size 0)
var reused = get(saved)
var fresh = get(items)
var same = first == reused
var distinct = fresh != reused
var old_is_pool = saved != 0
var old_is_not_entity = saved != first
"#);
        for _ in 0..12 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let vars = world.resource::<SceneMaxVars>();
        for key in ["same", "distinct", "old_is_pool", "old_is_not_entity"] { assert_eq!(vars.0[key], 1., "{key}"); }
        assert!(!vars.1.contains_key("invalid"));
        assert_ne!(vars.2["items"].pool_identity(), vars.2["saved"].pool_identity());
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 2);
    }

    #[test]
    fn declaration_operands_run_once_in_order_with_scoped_values_and_shared_skip() {
        let mut world = run(r#"
var calls = 0
var order = []
sample(value) = {
 calls = calls + 1
 order.push(value)
 return value
}
make(value) = {
 subject => box: pos (sample(value), sample(value + 1), sample(value + 2)), scale sample(value + 3)
 return subject
}
var first = make(2)
var second = make(6)
shared retained => box: pos (sample(10),0,0)
retained => sphere: pos (sample(99),0,0), scale sample(99)
var positions = [first.x, first.y, first.z, second.x, retained.x]
"#);
        for _ in 0..8 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["calls"], 9.);
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["order"], &serde_json::json!([2,3,4,5,6,7,8,9,10])));
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["positions"], &serde_json::json!([2,3,4,6,10])));
        let first = vars.2["first"].entity_identity().unwrap().runtime_name;
        let second = vars.2["second"].entity_identity().unwrap().runtime_name;
        let transforms = world.query::<(&SceneMaxEntity, &Transform)>().iter(&world).map(|(e,t)| (e.name.clone(),t.scale)).collect::<HashMap<_,_>>();
        assert_eq!(transforms[&first], Vec3::splat(5.));
        assert_eq!(transforms[&second], Vec3::splat(9.));
    }

    #[test]
    fn evaluated_geometry_drives_colliders_and_source_pool_copies_only_rotation() {
        let mut world = run(r#"
var extent = 2
var angle = 30
var calls = 0
measure(a,b) = {
 calls = calls + 1
 return a+b
}
prototype => box: size (measure(extent,1),extent,extent/2), rotate (0,angle,0)
sensor => collider sphere: radius measure(extent,1)
copies => Object.Pool(prototype, size 1)
var first = copies.acquire
angle = 60
extent = 10
var second = copies.acquire
copies.release first
var reused = copies.acquire
var same = reused == first
"#);
        for _ in 0..12 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["calls"],2.); // Source copies omit primitive dimensions.
        assert_eq!(vars.0["same"],1.);
        let first = vars.2["first"].entity_identity().unwrap().runtime_name;
        let second = vars.2["second"].entity_identity().unwrap().runtime_name;
        let bounds = world.resource::<SceneMaxColliderBounds>();
        assert!(matches!(bounds.shape_by_name["prototype"], ColliderBoundShape::Box { half_extents } if half_extents == Vec3::new(1.5,1.,0.5)));
        assert!(matches!(bounds.shape_by_name["sensor"], ColliderBoundShape::Sphere { radius } if radius == 3.));
        assert!(matches!(bounds.shape_by_name[&first], ColliderBoundShape::Box { half_extents } if half_extents == Vec3::splat(0.5)));
        let transforms = world.query::<(&SceneMaxEntity,&Transform)>().iter(&world).map(|(s,t)| (s.name.clone(),*t)).collect::<HashMap<_,_>>();
        assert!(transforms[&first].rotation.abs_diff_eq(Quat::from_rotation_y(30f32.to_radians()),1e-5));
        assert!(transforms[&second].rotation.abs_diff_eq(Quat::from_rotation_y(60f32.to_radians()),1e-5));
    }

    #[test]
    fn source_pool_uses_authored_expressions_instead_of_live_state_or_sharing() {
        let mut world = run(r#"
var amount = 4
shared prototype => box: multiplayer, pos (amount,1,2), scale 2
prototype.pos = (99,0,0)
items => Object.Pool(prototype, size 1)
var first = items.acquire
var first_x = first.x
amount = 7
var second = items.acquire
var second_x = second.x
first.pos = (12,0,0)
items.release first
var reused = items.acquire
var same = reused == first
var reused_x = reused.x
"#);
        for _ in 0..12 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let vars = world.resource::<SceneMaxVars>();
        for (name, expected) in [("first_x",4.),("second_x",7.),("same",1.),("reused_x",12.)] { assert_eq!(vars.0[name], expected, "{name}"); }
        let pools = world.resource::<SceneMaxObjectPools>();
        let pool = &pools.pools["items"];
        let (_, options) = pool.source_template.as_ref().unwrap();
        assert!(!options.shared && !options.multiplayer);
        assert!(pool.members.iter().all(|name| !pools.shared_entities.contains(name)));
        let name = vars.2["first"].entity_identity().unwrap().runtime_name;
        assert_eq!(world.query::<(&SceneMaxEntity, &Transform)>().iter(&world).find(|(e,_)| e.name == name).unwrap().1.scale, Vec3::splat(2.));
    }

    #[test]
    fn source_pool_keeps_its_definition_after_source_redeclaration() {
        let mut world = run(r#"
prototype => box: pos (4,0,0)
old_pool => Object.Pool(prototype, size 0)
prototype => sphere: pos (99,0,0)
new_pool => Object.Pool(prototype, size 0)
var old_member = old_pool.acquire
var new_member = new_pool.acquire
var positions = [old_member.x, new_member.x]
"#);
        for _ in 0..8 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        assert!(scenemax_runtime_vm_core::typed::equal(&world.resource::<SceneMaxVars>().1["positions"], &serde_json::json!([4,99])));
        let pools = world.resource::<SceneMaxObjectPools>();
        assert_eq!(pools.pools["old_pool"].source_template.as_ref().unwrap().0, "box");
        assert_eq!(pools.pools["new_pool"].source_template.as_ref().unwrap().0, "sphere");
    }

    #[test]
    fn source_pool_retains_scoped_template_and_live_local_binding_after_return() {
        let mut world = run(r#"
owner() = {
 var amount = 3
 prototype => box: pos (amount,0,0)
 items => Object.Pool(prototype, size 0)
 amount = 8
 return items
}
var pool = owner()
var member = pool.acquire
var position = member.x
"#);
        for _ in 0..6 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        assert_eq!(world.resource::<SceneMaxVars>().0["position"], 8.);
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 2);
    }

    #[test]
    fn pool_fill_growth_and_fifo_reuse_cross_256_members() {
        let mut world = run("make() = {\nsubject => box\nreturn subject\n}\nitems => Object.Pool(make, size 257)");
        let original = world.resource::<SceneMaxObjectPools>().pools["items"].available.clone();
        assert_eq!(original.len(), 257);
        let mut source = String::from("make() = {\nsubject => box\nreturn subject\n}\n");
        for index in 0..258 { source.push_str(&format!("var member_{index} = items.acquire\n")); }
        source.push_str("items.release member_0\nitems.release member_256\nvar reused_first = items.acquire\nvar reused_last = items.acquire\n");
        run_in(&mut world, &source);
        for _ in 0..270 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        let vars = world.resource::<SceneMaxVars>();
        for (index, name) in original.iter().enumerate() {
            assert_eq!(&vars.2[&format!("member_{index}")].entity_identity().unwrap().runtime_name, name);
        }
        assert_eq!(vars.2["reused_first"], vars.2["member_0"]);
        assert_eq!(vars.2["reused_last"], vars.2["member_256"]);
        let pool = &world.resource::<SceneMaxObjectPools>().pools["items"];
        assert_eq!(pool.members.len(), 258);
        assert_eq!(pool.in_use.len(), 258);
        assert_eq!(pool.created_count, 258);
        assert!(pool.available.is_empty());
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 258);
    }

    #[test]
    fn pool_size_evaluates_once_in_scope_before_factory_fill_and_skips_duplicates() {
        let mut world = run(r#"
var sizes = 0
var creates = 0
size(value) = {
 sizes = sizes + 1
 return value + 0.9
}
make() = {
 creates = creates + 1
 subject => box
 return subject
}
owner(amount) = {
 items => Object.Pool(make, size size(amount))
 items => Object.Pool(make, size size(99))
 return creates
}
var filled = owner(3)
empty => Object.Pool(make, size -4)
text => Object.Pool(make, size "4")
flag => Object.Pool(make, size true)
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["sizes"], 1.);
        assert_eq!(vars.0["filled"], 3.);
        assert_eq!(vars.0["creates"], 3.);
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 3);
        for name in ["empty", "text", "flag"] {
            assert!(world.resource::<SceneMaxObjectPools>().pools[name].members.is_empty());
        }
    }

    #[test]
    fn pool_acquisition_in_arrays_arguments_returns_and_short_circuit_conditions() {
        let mut world = run(r#"
var calls = 0
make() = {
 calls = calls + 1
 subject => box: pos (calls,0,0)
 return subject
}
identity(value) = {
 return value
}
next() = {
 return items.acquire
}
items => Object.Pool(make, size 0)
var values = [items.acquire, identity(items.acquire), next()]
var first = values[0]
var second = values[1]
var third = values[2]
var positions = [first.x, second.x, third.x]
var skipped = false and items.acquire == first
var skipped_again = true or items.acquire == first
var final_calls = calls
"#);
        for _ in 0..8 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["final_calls"], 3.);
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["positions"], &serde_json::json!([1,2,3])));
        assert_ne!(vars.2["first"], vars.2["second"]);
        assert_ne!(vars.2["second"], vars.2["third"]);
        assert_eq!(world.resource::<SceneMaxObjectPools>().pools["items"].in_use.len(), 3);
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 3);
    }

    #[test]
    fn pool_growth_retains_declaration_bindings_after_owner_returns() {
        let factory = "make() = {\nsubject => box\nsubject.pos = (captured,0,0)\nreturn subject\n}\n";
        let mut world = run(&format!("{factory}owner() = {{\nvar captured = 7\nitems => Object.Pool(make, size 0)\ncaptured = 9\nreturn 0\n}}\nvar complete = owner()"));
        // Address the retained runtime pool directly to isolate its captured scope
        // from pool-variable/parameter transport, which is a separate feature.
        let pool = world.resource::<SceneMaxObjectPools>().pools.keys().next().unwrap().clone();
        {
            let mut pools = world.resource_mut::<SceneMaxObjectPools>();
            let captured = pools.pools.get_mut(&pool).unwrap().factory_scope.as_mut().unwrap();
            captured.value_call_depth = 16;
            captured.value_call_steps = 2048;
        }
        run_in(&mut world, &format!("{factory}var captured = 100\nvar result = {pool}.acquire\nvar location = result.x"));
        for _ in 0..4 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        assert_eq!(world.resource::<SceneMaxVars>().0["location"], 9.);
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 1);
    }

    #[test]
    fn pool_factories_reject_non_entities_and_bound_recursive_acquisition() {
        for body in ["return 7", "var nested = items.acquire\nreturn nested"] {
            let mut world = run(&format!("var calls = 0\nmake() = {{\ncalls = calls + 1\n{body}\n}}\nitems => Object.Pool(make, size 0)\nvar result = items.acquire"));
            let calls = world.resource::<SceneMaxVars>().0["calls"];
            assert!(calls >= 1. && calls <= 16., "calls={calls}");
            assert!(world.resource::<SceneMaxObjectPools>().pools["items"].members.is_empty());
            assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 0);
        }
    }

    #[test]
    fn character_timed_and_held_movement_feed_physics_without_transform_bypass() {
        let mut world = run("subject => box");
        let entity = world.query::<(Entity, &SceneMaxEntity)>().iter(&world).find(|(_,e)| e.name == "subject").unwrap().0;
        world.entity_mut(entity).insert((SceneMaxCharacterController {
            move_speed: 7., gravity: 60., capsule_radius: 0.5, capsule_height: 1., capsule_center_y: 1., float_height: 0.1,
        }, SceneMaxCharacterMotor::default(), TnuaConfig::<SceneMaxControlScheme>(Handle::default()), TnuaController::<SceneMaxControlScheme>::default()));
        run_in(&mut world, "subject.move forward 4 for 2 seconds async");
        assert!(world.get::<TimedMoves>(entity).is_none());
        assert_eq!(world.get::<Transform>(entity).unwrap().translation, Vec3::ZERO);
        assert!((world.get::<SceneMaxCharacterMotor>(entity).unwrap().timed_motion.length() - 2./7.).abs() < 0.0001);
        world.insert_resource(ContinuousDelta(0.1));
        run_in(&mut world, "subject.move right 0.1");
        world.remove_resource::<ContinuousDelta>();
        assert_eq!(world.get::<Transform>(entity).unwrap().translation, Vec3::ZERO);
        assert!(world.get::<TimedMoves>(entity).is_none());
        run_in(&mut world, "subject.character.jump at speed of 20 async");
        assert!(world.get::<TimedJump>(entity).is_none());
        assert!(world.get::<SceneMaxCharacterMotor>(entity).unwrap().pending_jump_speed.is_some());
        world.init_resource::<Assets<SceneMaxControlSchemeConfig>>();
        world.init_resource::<SceneMaxStartupProgram>();
        let mut time = Time::<()>::default();
        time.advance_by(std::time::Duration::from_secs_f32(0.05));
        world.insert_resource(time);
        world.run_system_once(feed_tnua_character_controllers).unwrap();
        let motion = world.get::<TnuaController<SceneMaxControlScheme>>(entity).unwrap().basis.desired_motion;
        assert!(motion.abs_diff_eq(Vec3::new(-1./7., 0., 2./7.), 0.0001), "{motion:?}");
        world.resource_mut::<Time>().advance_by(std::time::Duration::from_secs(3));
        world.run_system_once(feed_tnua_character_controllers).unwrap();
        world.run_system_once(feed_tnua_character_controllers).unwrap();
        assert_eq!(world.get::<TnuaController<SceneMaxControlScheme>>(entity).unwrap().basis.desired_motion, Vec3::ZERO);
        assert!(world.get::<bevy_tnua::TnuaMotor>(entity).is_some());
        run_in(&mut world, "subject.clear character mode");
        assert!(world.get::<bevy_tnua::TnuaMotor>(entity).is_none());
        assert!(world.get::<bevy_tnua::TnuaRigidBodyTracker>(entity).is_none());
        assert!(world.get::<SceneMaxCharacterMotor>(entity).is_none());
        assert_eq!(world.get::<LinearVelocity>(entity).unwrap().0, Vec3::ZERO);
    }

    #[test]
    fn concurrent_rotation_commands_advance_and_finish_independently() {
        let mut world = run("subject => box\nsubject.rotate(x+90) in 1 seconds async\nsubject.rotate(z+120) in 2 seconds async");
        while !world.resource::<DelayedActionQueue>().actions.is_empty() { resume_next(&mut world); }
        let entity = world.query::<(Entity, &SceneMaxEntity)>().iter(&world).find(|(_,e)| e.name == "subject").unwrap().0;
        assert_eq!(world.get::<TimedTurns>(entity).unwrap().turns.len(), 2);
        world.init_resource::<SceneMaxStartupProgram>();
        let mut time = Time::<()>::default();
        time.advance_by(std::time::Duration::from_secs(1));
        world.insert_resource(time);
        world.run_system_once(update_timed_turns).unwrap();
        let first = Quat::from_rotation_x(90_f32.to_radians()) * Quat::from_rotation_z(60_f32.to_radians());
        assert!(world.get::<Transform>(entity).unwrap().rotation.abs_diff_eq(first, 0.0001));
        assert_eq!(world.get::<TimedTurns>(entity).unwrap().turns.len(), 1);
        world.run_system_once(update_timed_turns).unwrap();
        let final_rotation = Quat::from_rotation_x(90_f32.to_radians()) * Quat::from_rotation_z(120_f32.to_radians());
        assert!(world.get::<Transform>(entity).unwrap().rotation.abs_diff_eq(final_rotation, 0.0001));
        assert!(world.get::<TimedTurns>(entity).is_none());
    }

    #[test]
    fn returned_controller_aliases_keep_independent_targets_and_stop_the_selected_run() {
        let mut world = run("first => box\nsecond => box");
        while !world.resource::<DelayedActionQueue>().actions.is_empty() { resume_next(&mut world); }
        for name in ["first", "second"] {
            world.resource_mut::<SceneMaxRuntimeAssets>().gltf_handles_by_name.insert(name.into(), Handle::default());
        }
        run_in(&mut world, "make(subject) = {\ncontrol = animation subject.take\nreturn control\n}\nvar a = make(first)\nvar b = make(second)\nvar copy = a\nvar stored = [a,b]\nvar restored = stored[0]\nvar same = restored == copy\nvar different = a != b");
        assert_eq!(world.resource::<SceneMaxVars>().0["same"], 1.);
        assert_eq!(world.resource::<SceneMaxVars>().0["different"], 1.);
        let a = world.resource::<SceneMaxVars>().2["a"].controller_identity().unwrap().runtime_name;
        let b = world.resource::<SceneMaxVars>().2["b"].controller_identity().unwrap().runtime_name;
        assert_ne!(a,b);
        run_in(&mut world, "copy.event(take,50) = {\nvar reached = 1\n}\ncopy.run");
        let assets = world.resource::<SceneMaxRuntimeAssets>();
        assert_eq!(assets.animation_controllers_by_name[&a].events.len(),1, "batch={:?} copy={:?}", world.resource::<Batch>().0, world.resource::<SceneMaxVars>().2.get("copy"));
        assert!(assets.animation_controllers_by_name[&b].events.is_empty());
        let first_run = assets.animation_controllers_by_name[&a].source.as_ref().unwrap().0;
        assert_eq!(world.get::<SceneMaxEntity>(first_run).unwrap().name,"first");
        run_in(&mut world, "b.run");
        let second_run = world.resource::<SceneMaxRuntimeAssets>().animation_controllers_by_name[&b].source.as_ref().unwrap().0;
        assert_eq!(world.get::<SceneMaxEntity>(second_run).unwrap().name,"second");
        run_in(&mut world, "stop_selected(value) = {\nvalue.stop\n}\nrun stop_selected(restored)");
        let assets = world.resource::<SceneMaxRuntimeAssets>();
        assert!(!assets.animation_controllers_by_name[&a].running);
        assert!(assets.animation_controllers_by_name[&b].running);
        assert!(assets.pending_animation_controller_stops.iter().any(|(entity,_)| *entity == first_run));
        run_in(&mut world, "var copy = 0\ncopy.stop");
        assert!(world.resource::<SceneMaxRuntimeAssets>().animation_controllers_by_name[&b].running);
    }


    #[test]
    fn shared_cinematic_identity_and_rig_survive_scene_camera_reset() {
        let mut world = World::new();
        let mut cameras = SceneMaxCameraSystem::default();
        for id in ["first", "second"] {
            cameras.cinematic_rigs.insert(id.into(), RuntimeCinematicRig {
                id: id.into(), name: id.into(), position: Vec3::ZERO, rotation: Quat::IDENTITY,
                scale: Vec3::ONE, target_entity_name: String::new(), target_offset: Vec3::ZERO,
                ease_in: String::new(), ease_out: String::new(),
                tracks_by_id: HashMap::from([("track".into(), RuntimeCinematicTrack {
                    id: "track".into(), local_position: Vec3::ZERO, local_rotation: Quat::IDENTITY,
                    local_scale: Vec3::ONE, radius_x: 1., radius_z: 1., anchor_count: 16,
                })]),
                segments: vec![RuntimeCinematicSegment { track_id: "track".into(), start_anchor: 0, end_anchor: 4 }],
                has_relative_target_placement: false, relative_rig_position_to_target: Vec3::ZERO,
                relative_rig_rotation_to_target: Quat::IDENTITY,
            });
        }
        world.insert_resource(cameras);

        run_in(&mut world, "shared shot => cinematic.camera.first\nshared var original = shot\ntransient => cinematic.camera.second");
        while !world.resource::<DelayedActionQueue>().actions.is_empty() { resume_next(&mut world); }
        crate::shared_entities::propagate_attachments(&mut world);
        let names = world.resource::<SceneMaxObjectPools>().shared_entities.clone();
        assert!(names.contains("shot"));
        let program = scenemax_parser::parse_program("shot => cinematic.camera.second").unwrap();
        crate::shared_entities::reset_cameras(&program, &mut world.resource_mut::<SceneMaxCameraSystem>(), &names);
        assert_eq!(world.resource::<SceneMaxCameraSystem>().cinematic_vars["shot"].rig_id, "first");
        assert!(!world.resource::<SceneMaxCameraSystem>().cinematic_vars.contains_key("transient"));
        run_in(&mut world, "shot => cinematic.camera.second\nvar same = original == shot\noriginal.play : duration 1, async");
        while !world.resource::<DelayedActionQueue>().actions.is_empty() { resume_next(&mut world); }
        assert_eq!(world.resource::<SceneMaxVars>().0["same"], 1.);
        assert_eq!(world.resource::<SceneMaxCameraSystem>().active_cinematic.as_ref().unwrap().rig.id,"first");
        run_in(&mut world, "original.play : duration 1\nvar after_cinematic = 1");
        let weak = world.resource::<DelayedActionQueue>().actions.last().unwrap().animation_wait.as_ref().unwrap().controller_run.clone().unwrap();
        assert_eq!(weak.strong_count(),1);
        assert!(!world.resource::<SceneMaxVars>().0.contains_key("after_cinematic"));
        world.resource_mut::<SceneMaxCameraSystem>().active_cinematic = None;
        assert_eq!(weak.strong_count(),0);
        resume_next(&mut world);
        assert_eq!(world.resource::<SceneMaxVars>().0["after_cinematic"],1.);

    }
    #[test]
    fn cinematic_references_survive_calls_and_select_their_own_rigs() {
        let mut world = World::new();
        let mut cameras = SceneMaxCameraSystem::default();
        for id in ["first", "second"] {
            cameras.cinematic_rigs.insert(id.into(), RuntimeCinematicRig {
                id: id.into(), name: id.into(), position: Vec3::ZERO, rotation: Quat::IDENTITY,
                scale: Vec3::ONE, target_entity_name: String::new(), target_offset: Vec3::ZERO,
                ease_in: String::new(), ease_out: String::new(),
                tracks_by_id: HashMap::from([("track".into(), RuntimeCinematicTrack {
                    id: "track".into(), local_position: Vec3::ZERO, local_rotation: Quat::IDENTITY,
                    local_scale: Vec3::ONE, radius_x: 1., radius_z: 1., anchor_count: 16,
                })]),
                segments: vec![RuntimeCinematicSegment { track_id: "track".into(), start_anchor: 0, end_anchor: 4 }],
                has_relative_target_placement: false, relative_rig_position_to_target: Vec3::ZERO,
                relative_rig_rotation_to_target: Quat::IDENTITY,
            });
        }
        world.insert_resource(cameras);
        run_in(&mut world, "make() = {\nshot => cinematic.camera.first\nreturn shot\n}\nvar a = make()\nvar b = make()\nvar distinct = a != b\nroot => cinematic.camera.first\nvar old = root\nroot => cinematic.camera.second\nvar alias = root\n");
        for _ in 0..8 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        assert_eq!(world.resource::<SceneMaxVars>().0["distinct"], 1.);
        assert_ne!(world.resource::<SceneMaxVars>().2["old"], world.resource::<SceneMaxVars>().2["root"]);
        for (target, expected) in [("a", Some("first")), ("b", Some("first")), ("alias", Some("second")), ("old", None)] {
            world.resource_mut::<SceneMaxCameraSystem>().active_cinematic = None;
            run_in(&mut world, &format!("{target}.play : duration 1, async"));
            assert_eq!(world.resource::<SceneMaxCameraSystem>().active_cinematic.as_ref().map(|a| a.rig.id.as_str()), expected);
        }
        run_in(&mut world, "view(camera_ref) = {\nlocal_target => box\ncamera_ref.play : duration 1, target local_target\n}\nrun view(a)");
        for _ in 0..8 {
            if world.resource::<SceneMaxCameraSystem>().active_cinematic.as_ref().is_some_and(|a| a.look_at.is_some()) { break; }
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let active = world.resource::<SceneMaxCameraSystem>().active_cinematic.as_ref().unwrap();
        assert_eq!(active.rig.id, "first");
        let Some(CinematicLookAt::Entity(target)) = active.look_at.as_ref() else { panic!("missing scoped look-at"); };
        assert_ne!(target, "local_target");
        let target = target.clone();
        assert!(world.query::<&SceneMaxEntity>().iter(&world).any(|e| e.name == target));
        run_in(&mut world, "missing => cinematic.camera.absent");
        assert!(!world.resource::<SceneMaxVars>().2.contains_key("missing"));
    }

    #[test]
    fn scoped_lights_and_probes_return_distinct_instance_references() {
        for declaration in ["subject => Lights.point: pos (value,0,0)", "Lights.Add Probe \"subject\" Having pos (value,0,0)"] {
            let mut world = run(&format!("make(value) = {{\n{declaration}\nreturn subject\n}}\nvar first = make(3)\nvar second = make(8)\nvar distinct = first != second\nvar first_x = first.x\nvar second_x = second.x"));
            let vars = world.resource::<SceneMaxVars>();
            assert_eq!(vars.0["distinct"], 1.);
            assert_eq!(vars.0["first_x"], 3.);
            assert_eq!(vars.0["second_x"], 8.);
            assert_eq!(world.query::<&SceneMaxLight>().iter(&world).count(), 2);
            assert!(world.resource::<SceneMaxRuntimeAssets>().pending_entity_handles.is_empty());
        }
    }

    #[test]
    fn shared_light_redeclaration_keeps_identity_type_and_transform() {
        let mut world = run(r#"
shared lamp => Lights.point: pos (3,0,0), intensity 2
var original = lamp
lamp => Lights.spot: pos (99,0,0), intensity 10
var unchanged = lamp == original
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["unchanged"], 1.);
        let lights = world.query::<(&Transform, &PointLight)>().iter(&world).collect::<Vec<_>>();
        assert_eq!(lights.len(), 1);
        assert_eq!(lights[0].0.translation.x, 3.);
        assert_eq!(world.query::<&SpotLight>().iter(&world).count(), 0);
    }

    #[test]
    fn root_light_replacement_invalidates_old_reference_and_components() {
        let mut world = run(r#"
lamp => Lights.point: pos (3,0,0)
var original = lamp
lamp => Lights.spot: pos (8,0,0)
original.pos = (99,0,0)
var different = original != lamp
var location = lamp.x
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["different"], 1.);
        assert_eq!(world.resource::<SceneMaxVars>().0["location"], 8.);
        assert_eq!(world.query::<&PointLight>().iter(&world).count(), 0);
        assert_eq!(world.query::<&SpotLight>().iter(&world).count(), 1);
    }

    #[test]
    fn existing_shared_variable_skips_effectful_initializer() {
        let world = run(r#"
var calls = 0
next_value() = {
 calls = calls + 1
 return calls
}
shared var saved = next_value()
shared var saved = next_value()
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["calls"], 1.);
        assert_eq!(world.resource::<SceneMaxVars>().0["saved"], 1.);
    }

    #[test]
    fn shared_entity_redeclaration_keeps_identity_transform_and_data() {
        let mut world = run(r#"
shared subject => box: pos (3,0,0)
var original = subject
subject.data.score = 7
subject => sphere: pos (99,0,0)
var unchanged = original == subject
var location = subject.x
var score = subject.data.score
"#);
        for _ in 0..4 {
            if world.resource::<DelayedActionQueue>().actions.is_empty() { break; }
            resume_next(&mut world);
        }
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["unchanged"], 1.);
        assert_eq!(vars.0["location"], 3.);
        assert_eq!(vars.0["score"], 7.);
        assert_eq!(world.query::<&SceneMaxEntity>().iter(&world).count(), 1);
    }

    #[test]
    fn nested_declaration_reuses_inherited_shared_entity() {
        let world = run(r#"
child() = {
 subject => sphere: pos (99,0,0)
 return subject
}
parent() = {
 shared subject => box: pos (3,0,0)
 return [subject, child()]
}
var results = parent()
var identical = results[0] == results[1]
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["identical"], 1.);
    }

    #[test]
    fn root_entity_replacement_keeps_saved_reference_on_retired_instance() {
        let mut world = run(r#"
subject => box: pos (3,0,0)
var original = subject
subject => sphere: pos (8,0,0)
var replacement = subject
var different = original != replacement
original.pos = (99,0,0)
subject.pos = (12,0,0)
var replacement_x = replacement.x
"#);
        resume_next(&mut world);
        resume_next(&mut world);
        let vars = world.resource::<SceneMaxVars>();
        let original = scenemax_runtime_vm_core::references::lookup("original", vars, None).and_then(|v| v.entity_identity()).expect(&format!("root values: {vars:?}"));
        let replacement = scenemax_runtime_vm_core::references::lookup("replacement", vars, None).unwrap().entity_identity().unwrap();
        assert_ne!(original.runtime_name, replacement.runtime_name);
        assert_eq!(vars.0["different"], 1.);
        assert_eq!(vars.0["replacement_x"], 12.);
        let entities = world.query::<(&SceneMaxEntity, &Transform)>().iter(&world)
            .map(|(s,t)| (s.name.clone(), t.translation.x)).collect::<HashMap<_,_>>();
        assert_eq!(entities.len(), 1);
        assert_eq!(entities[&replacement.runtime_name], 12.);
        assert!(!entities.contains_key(&original.runtime_name));
    }

    #[test]
    fn returned_entity_properties_and_comparisons_use_instance_identity() {
        let world = run(r#"
make(value) = {
 subject => box
 subject.pos = (value,0,0)
 return subject
}
var first = make(3)
var second = make(8)
var alias = first
var same = first == alias
var different = first != second
var not_string = first != "subject"
var not_zero = first != 0
var not_null = first != null
var same_list = [first] == [alias]
var distinct_list = [first] != [second]
var first_x = first.x
var second_x = second.x
"#);
        let vars = world.resource::<SceneMaxVars>();
        for name in ["same", "different", "not_string", "not_zero", "not_null", "same_list", "distinct_list"] {
            assert_eq!(vars.0.get(name), Some(&1.), "{name}");
        }
        assert_eq!(vars.0["first_x"], 3.);
        assert_eq!(vars.0["second_x"], 8.);
    }

    #[test]
    fn nested_declaration_shadows_without_retiring_the_parent_instance() {
        let mut world = run(r#"
var child = 0
nested() = {
 subject => sphere: pos (8,0,0)
 return subject
}
outer() = {
 subject => box: pos (3,0,0)
 child = nested()
 return subject
}
var parent = outer()
"#);
        let vars = world.resource::<SceneMaxVars>();
        let parent = scenemax_runtime_vm_core::references::lookup("parent", vars, None).unwrap().entity_identity().unwrap();
        let child = scenemax_runtime_vm_core::references::lookup("child", vars, None).unwrap().entity_identity().unwrap();
        let entities = world.query::<(&SceneMaxEntity, &Transform)>().iter(&world)
            .map(|(s,t)| (s.name.clone(), t.translation.x)).collect::<HashMap<_,_>>();
        assert_eq!(entities.len(), 2);
        assert_eq!(entities[&parent.runtime_name], 3.);
        assert_eq!(entities[&child.runtime_name], 8.);
    }

    #[test]
    fn scoped_factory_calls_return_distinct_instances() {
        let mut world = run(r#"
make(value) = {
 subject => box: pos (value,2,3), scale value
 return subject
}
var first = make(4)
var second = make(9)
first.hide
"#);
        let vars = world.resource::<SceneMaxVars>();
        let first = scenemax_runtime_vm_core::references::lookup("first", vars, None).unwrap().entity_identity().unwrap();
        let second = scenemax_runtime_vm_core::references::lookup("second", vars, None).unwrap().entity_identity().unwrap();
        assert_ne!(first.key, second.key);
        assert_ne!(first.runtime_name, second.runtime_name);
        let entities = world.query::<(&SceneMaxEntity, &Transform, &Visibility)>().iter(&world)
            .map(|(s,t,v)| (s.name.clone(), (t.translation, t.scale, *v))).collect::<HashMap<_,_>>();
        assert_eq!(entities.len(), 2);
        assert_eq!(entities[&first.runtime_name], (Vec3::new(4.,2.,3.), Vec3::splat(4.), Visibility::Hidden));
        assert_eq!(entities[&second.runtime_name].0, Vec3::new(9.,2.,3.));
        assert_eq!(entities[&second.runtime_name].1, Vec3::splat(9.));
    }

    #[test]
    fn entity_values_survive_function_arguments_returns_and_collection_access() {
        let mut world = run("");
        let original = Reference::entity("subject");
        let replacement = Reference::entity("subject");
        {
            let mut vars = world.resource_mut::<SceneMaxVars>();
            scenemax_runtime_vm_core::references::assign("source", original.clone(), &mut vars, None, false).unwrap();
            scenemax_runtime_vm_core::references::assign("replacement", replacement.clone(), &mut vars, None, false).unwrap();
        }
        world.insert_resource(Batch(scenemax_parser::parse_program(r#"
var retained = 0
route(value) = {
 var items = [value]
 return items[0]
}
ordinary(value) = {
 retained = value
 value = replacement
}
var result = route(source)
run ordinary(source)
"#).unwrap().statements));
        world.run_system_once(execute).unwrap();
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(scenemax_runtime_vm_core::references::lookup("result", vars, None).unwrap(), original);
        assert_eq!(scenemax_runtime_vm_core::references::lookup("retained", vars, None).unwrap(), original);
        assert_eq!(scenemax_runtime_vm_core::references::lookup("source", vars, None).unwrap(), original);
        assert_eq!(scenemax_runtime_vm_core::references::lookup("replacement", vars, None).unwrap(), replacement);
    }

    #[test]
    fn entity_data_uses_rebound_parameters_and_is_visible_to_next_command() {
        let world = run(r#"
change(target) = {
 target.data.score# = 4
 var previous = target.data.score
 target = "second"
 target.data.score = previous + 1
 return target.data.score
}
var result = change("first")
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["first.data.score"], 4.);
        assert_eq!(vars.0["second.data.score"], 5.);
        assert_eq!(vars.0["result"], 5.);
        assert!(!vars.0.contains_key("target.data.score"));
    }

    #[test]
    fn redeclaration_replaces_existing_entity_instead_of_reusing_components() {
        let mut world = run("subject => box\nwait 0.01 seconds\nsubject => label: text \"fresh\"");
        // The first declaration suspends at its lifecycle boundary.
        let old = world.query::<(Entity, &SceneMaxEntity)>().iter(&world)
            .find(|(_, scene)| scene.name == "subject").unwrap().0;
        #[derive(Component)]
        struct RetiredOnly;
        world.entity_mut(old).insert(RetiredOnly);
        scenemax_runtime_vm_core::typed::store(world.resource_mut::<SceneMaxVars>().as_mut(), "subject.data.old", serde_json::json!([1,2]));
        let child = world.spawn(ChildOf(old)).id();
        world.resource_mut::<SceneMaxColliderBounds>().radius_by_name.insert("subject".into(), 99.);
        world.resource_mut::<SceneMaxRuntimeAssets>().model_resources_by_name.insert("subject".into(), "old-resource".into());
        resume_next(&mut world);
        resume_next(&mut world);
        assert!(world.get_entity(old).is_err());
        let current_name = world.resource::<SceneMaxRuntimeAssets>().root_entity_declarations["subject"].entity_identity().unwrap().runtime_name;
        let current = world.query::<(Entity, &SceneMaxEntity)>().iter(&world)
            .filter(|(_, scene)| scene.name == current_name).map(|(entity, _)| entity).collect::<Vec<_>>();
        assert_eq!(current.len(), 1);
        assert_ne!(old, current[0]);
        assert!(world.get::<RetiredOnly>(current[0]).is_none());
        assert!(!world.resource::<SceneMaxVars>().1.contains_key("subject.data.old"));
        assert!(world.get_entity(child).is_err());
        assert_eq!(world.get::<crate::labels::Label>(current[0]).unwrap().text, "fresh");
        assert!(!world.resource::<SceneMaxColliderBounds>().radius_by_name.contains_key("subject"));
        assert!(!world.resource::<SceneMaxRuntimeAssets>().model_resources_by_name.contains_key("subject"));
    }

    #[test]
    fn synchronous_redeclarations_retire_pending_instances_and_keep_last_transform() {
        let mut world = run(r#"
create() = {
 subject => box: pos (1,2,3)
 subject => sphere: pos (4,5,6)
 subject.hide
 return 1
}
var result = create()
"#);
        let current = world.query::<(&SceneMaxEntity, &Transform, &Visibility)>().iter(&world)
            .collect::<Vec<_>>();
        assert_eq!(current.len(), 1);
        assert_eq!(current[0].1.translation, Vec3::new(4.,5.,6.));
        assert_eq!(*current[0].2, Visibility::Hidden);
    }

    #[test]
    fn explicit_blocks_isolate_declarations_and_unwind_delayed_returns() {
        let mut world = run(r#"
var result = 0
var completed = 0
var local = 7
choose() = {
 do
  var local = 3
  return local
 end do
 return 999
}
outer() = {
 do
  var local = 5
  result = local + choose()
  wait 0.1 seconds
  return
 end do
 result = 999
}
do
 var local = 99
end do
run outer()
completed = local
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["result"], 8.);
        resume_next(&mut world);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["completed"], 7.);
        assert_eq!(vars.0["local"], 7.);
        assert_eq!(vars.0["result"], 8.);
    }

    #[test]
    fn bounded_repeat_with_scopes_completes_all_iterations() {
        let world = run("var result = 0\ndo 128 times\n var local = 1\n result = result + local\nend do\nvar finished = 1");
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["result"], 128.);
        assert_eq!(vars.0["finished"], 1.);
        assert!(!vars.1.contains_key("local"));
    }
    #[test]
    fn branch_locals_are_isolated_and_returns_cross_scope_boundaries() {
        let world = run(r#"
var result = 0
var completed = 0
choose() = {
 var local = 1
 if true {
  var local = 9
  if true {
   return local
  }
 }
 return 100
}
outer() = {
 var local = 2
 if true {
  var local = 10
 }
 result = local + choose()
 return
 result = 999
}
run outer()
completed = 1
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["result"], 11.);
        assert_eq!(vars.0["completed"], 1.);
        assert!(!vars.1.contains_key("local"));
    }
    #[test]
    fn repeated_block_locals_reset_and_parent_continues_after_delayed_return() {
        let mut world = run(r#"
var result = 0
var completed = 0
outer() = {
 var local = 5
 do 2 times
  var local = 1
  local = local + 1
  result = result + local
 end do
 if true {
  var local = 99
  wait 0.1 seconds
  return
 }
 result = 999
}
run outer()
completed = 1
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["result"], 4.);
        resume_next(&mut world);
        assert_eq!(world.resource::<SceneMaxVars>().0["completed"], 1.);
        assert_eq!(world.resource::<SceneMaxVars>().0["result"], 4.);
    }
    #[test]
    fn do_while_iterations_leave_local_shadow_before_continuation() {
        let mut world = run(r#"
var result = 0
outer() = {
 var count = 0
 var local = 5
 do
  var local = 1
  count = count + 1
 while count < 2
 result = local + count
}
run outer()
"#);
        resume_next(&mut world);
        assert_eq!(world.resource::<SceneMaxVars>().0["result"], 7.);
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    }
    #[test]
    fn ordinary_and_value_calls_update_inherited_locals_without_aliasing_parameters() {
        let world = run(r#"
var result = 0
var result_list = []
change() = {
 count = count + 1
 items = [9]
 return count
}
shadow(count) = {
 count = 99
 var items = [100]
}
outer() = {
 var count = 1
 var items = [1]
 run change()
 var returned = change()
 run shadow(count)
 result = count + returned
 result_list = items
}
run outer()
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.0["result"], 6.);
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["result_list"], &serde_json::json!([9])));
        assert!(!vars.1.contains_key("count"));
    }
    #[test]
    fn asynchronous_child_observes_later_parent_write_and_updates_parent_binding() {
        let mut world = run(r#"
var result = 0
outer() = {
 var count = 1
 do async
  count = count + 1
 end do
 count = count + 1
 wait 0.1 seconds
 result = count
}
run outer()
"#);
        assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 2);
        resume_next(&mut world);
        resume_next(&mut world);
        assert_eq!(world.resource::<SceneMaxVars>().0["result"], 3.);
    }
    #[test]
    fn ordinary_calls_bind_arrays_once_and_restore_the_callers_scope_on_return() {
        let world = run(r#"
var items = [1]
var count = 0
var after = 0
identity(value) = {
 count = count + 1
 return value
}
change(items, amount) = {
 items.push(amount)
 amount = amount + 1
 items.push(amount)
 items = [9]
 var after = 99
 if true {
  return
 }
 count = 999
}
run change(identity(items), 2)
after = 1
var length = items.length
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["items"], &serde_json::json!([1,2,3])));
        assert_eq!(vars.0["count"], 1.);
        assert_eq!(vars.0["after"], 1.);
        assert_eq!(vars.0["length"], 3.);
        assert!(!vars.1.contains_key("amount"));
    }
    #[test]
    fn scoped_text_operands_read_rebound_parameters_without_rewriting_assignments() {
        let mut scope = SceneMaxScopeFrame::default();
        scope.parameters.insert("resource".into());
        scope.typed.insert("resource".into(), serde_json::json!("first"));
        let vars = SceneMaxVars::default();
        let action = Statement::UiLoad { name: "resource".into() };
        assert!(matches!(resolve_text_operands(&action, &vars, Some(&scope)), Some(Statement::UiLoad {name}) if name == "first"));
        scope.typed.insert("resource".into(), serde_json::json!("second"));
        assert!(matches!(resolve_text_operands(&action, &vars, Some(&scope)), Some(Statement::UiLoad {name}) if name == "second"));
        let assignment = scenemax_parser::parse_program("resource = \"other\"").unwrap().statements.remove(0);
        assert!(resolve_text_operands(&assignment, &vars, Some(&scope)).is_none());
    }
    #[test]
    fn ordinary_string_parameters_are_mutable_local_values() {
        let world = run(r#"
var original = "before"
var observed = ""
change(value) = {
 value = "after"
 observed = value
}
run change(original)
var finished = original
"#);
        let vars = world.resource::<SceneMaxVars>();
        assert_eq!(vars.1["original"], serde_json::json!("before"));
        assert_eq!(vars.1["observed"], serde_json::json!("after"));
        assert_eq!(vars.1["finished"], serde_json::json!("before"));
    }
    #[test]
    fn delayed_function_return_restores_nested_callers_and_continues_each_once() {
        let mut world = run(r#"
var items = [1]
var marker = 0
inner(items) = {
 var marker = 99
 wait 0.1 seconds
 items.push(2)
 return
 items.push(999)
}
outer(items) = {
 run inner(items)
 items.push(3)
 return
}
run outer(items)
marker = 1
items.push(4)
"#);
        assert_eq!(world.resource::<SceneMaxVars>().0["marker"], 0.);
        let pending = world.resource_mut::<DelayedActionQueue>().actions.remove(0);
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        let mut actions = world.resource::<Batch>().0.iter().filter(|s| matches!(s, Statement::FunctionDef(_))).cloned().collect::<Vec<_>>();
        actions.extend(pending.actions);
        world.insert_resource(Batch(actions));
        world.insert_resource(ResumeScope(pending.scope.unwrap()));
        world.run_system_once(execute).unwrap();
        let vars = world.resource::<SceneMaxVars>();
        assert!(scenemax_runtime_vm_core::typed::equal(&vars.1["items"], &serde_json::json!([1,2,3,4])));
        assert_eq!(vars.0["marker"], 1.);
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
    }
    #[test]
    fn value_calls_reject_suspension_missing_return_and_unbounded_recursion() {
        let world = run(r#"
waiting() = {
 wait 1 seconds
 return 9
}
recur() = {
 return recur()
}
empty() = {
 return
}
var rejected_wait = waiting()
var rejected_recursion = recur()
var rejected_empty = empty()
var finished = 1
"#);
        let vars = world.resource::<SceneMaxVars>();
        for name in ["rejected_wait", "rejected_recursion", "rejected_empty"] {
            assert!(!vars.0.contains_key(name) && !vars.1.contains_key(name));
        }
        assert_eq!(vars.0["finished"], 1.);
    }
}
