//! Shared collection identity; JSON snapshots remain the external value boundary.
use super::*;
use serde_json::Value;
pub use scenemax_script_values::Reference;
/// A lexical variable binding. Cloned scopes share bindings; parameters and
/// declarations allocate new bindings, independently of collection identity.
#[derive(Debug, Clone)]
pub struct Binding(std::sync::Arc<std::sync::RwLock<Reference>>);
impl PartialEq for Binding { fn eq(&self, other: &Self) -> bool { std::sync::Arc::ptr_eq(&self.0, &other.0) } }
impl Binding {
    fn new(value: Reference) -> Self { Self(std::sync::Arc::new(std::sync::RwLock::new(value))) }
    fn value(&self) -> Option<Reference> { self.0.read().ok().map(|value| value.clone()) }
    fn replace(&self, value: Reference) -> Option<()> { *self.0.write().ok()? = value; Some(()) }
}
pub fn scope_value(name: &str, scope: &SceneMaxScopeFrame) -> Option<Reference> {
    let binding = scope.bindings.get(name)?;
    // Preserve the legacy numeric projection's explicit writes. Script writes
    // use bind(), which updates both projections and the shared binding.
    if let Some(n) = scope.vars.get(name) {
        if scope.typed.get(name).and_then(typed::number).map(|v| v as f32) != Some(*n) {
            return Some(Reference::new(typed::numeric_value(*n)));
        }
    }
    binding.value()
}
/// Resolve legacy named entity references using live lexical values before cached
/// aliases. Bounded traversal makes malformed alias cycles inert.
pub fn entity_name(name: &str, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>) -> String {
    entity_name_with_aliases(name, vars, scope, &HashMap::new())
}

pub fn entity_name_with_aliases(name: &str, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>, aliases: &HashMap<String, String>) -> String {
    let mut current = name.to_owned();
    let mut seen = std::collections::HashSet::new();
    for _ in 0..32 {
        if !seen.insert(current.clone()) { return name.to_owned(); }
        if let Some(identity) = bound_entity(&current, vars, scope) {
            return identity.runtime_name;
        }
        let local = scope.and_then(|s| scope_value(&current, s).and_then(|v| v.snapshot())
            .or_else(|| s.typed.get(&current).cloned())
            .or_else(|| s.vars.get(&current).map(|v| typed::numeric_value(*v))));
        let next = if let Some(value) = local {
            value.as_str().map(str::to_owned)
        } else if let Some(alias) = scope.and_then(|s| s.aliases.get(&current)).or_else(|| aliases.get(&current)) {
            Some(alias.clone())
        } else {
            vars.1.get(&current).and_then(Value::as_str).map(str::to_owned)
        };
        let Some(next) = next.filter(|next| !next.is_empty() && next != &current) else { return current; };
        current = next;
    }
    name.to_owned()
}

fn bound_entity(name: &str, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>) -> Option<scenemax_script_values::EntityIdentity> {
    if let Some(scope) = scope {
        if scope.bindings.contains_key(name) || scope.typed.contains_key(name) || scope.vars.contains_key(name) {
            return scope_value(name, scope).or_else(|| scope.references.get(name).cloned())?.entity_identity();
        }
    }
    vars.2.get(name)?.entity_identity()
}

/// Entity data and network metadata use the resolved instance name. Ordinary
/// object/list fields keep their own value semantics.
pub fn entity_path(name: &str, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>) -> String {
    // A real entity reference owns its entire property namespace, including
    // animation/spatial fields. Resolve before consulting stale authored-name data.
    for (offset, _) in name.match_indices('.').rev() {
        if let Some(identity) = bound_entity(&name[..offset], vars, scope) {
            return format!("{}{}", identity.runtime_name, &name[offset..]);
        }
    }
    for marker in [".data.", ".network."] {
        if let Some((target, suffix)) = name.split_once(marker) {
            return format!("{}{}{}", entity_name(target, vars, scope), marker, suffix);
        }
    }
    name.to_owned()
}

pub fn lookup(name: &str, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>) -> Option<Reference> {
    let resolved = entity_path(name, vars, scope);
    if resolved != name { return lookup(&resolved, vars, scope); }
    if let Some(scope) = scope {
        if let Some(value) = scope_value(name, scope) { return Some(value); }
        if scope.vars.contains_key(name) { return typed::lookup(name,vars,Some(scope)).map(Reference::new); }
        if scope.typed.contains_key(name) || scope.vars.contains_key(name) {
            return scope.references.get(name).cloned().or_else(|| typed::lookup(name, vars, Some(scope)).map(Reference::new));
        }
    }
    if vars.0.contains_key(name) { return typed::lookup(name,vars,None).map(Reference::new); }
    if vars.1.contains_key(name) {
        return vars.2.get(name).cloned().or_else(|| typed::lookup(name, vars, None).map(Reference::new));
    }
    for (i, _) in name.match_indices('.').rev() {
        if let Some(value) = lookup(&name[..i], vars, scope).and_then(|v| v.field(&Value::String(name[i+1..].into()))) { return Some(value); }
    }
    None
}
pub fn evaluate(value: &AssignmentValue, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>, guards: &HashMap<String, Condition>, spatial: &impl SceneMaxVmSpatial) -> Option<Reference> {
    match value {
        AssignmentValue::RuntimeValue(value) => Some(value.clone()),
        AssignmentValue::Symbol(name) => lookup(name, vars, scope)
            .or_else(|| scenemax_parser::parse_ui_target_path(name).map(|_| Reference::ui(name)))
            .or_else(|| typed::evaluate(value,vars,scope,guards,spatial).map(Reference::new)),
        AssignmentValue::Array(values) => values.iter().map(|v| evaluate(v,vars,scope,guards,spatial)).collect::<Option<Vec<_>>>().map(Reference::array),
        AssignmentValue::Access { value, index } => evaluate(value,vars,scope,guards,spatial)?.field(&typed::evaluate(index,vars,scope,guards,spatial)?),
        AssignmentValue::ArrayEdit { source, operation } => {
            use scenemax_parser::ArrayOperation;
            let mut value = evaluate(source,vars,scope,guards,spatial)?;
            if !value.is_array() { value = Reference::array(vec![]); }
            match operation {
                ArrayOperation::Push(v) => value.push(evaluate(v,vars,scope,guards,spatial)?)?,
                ArrayOperation::Reset(v) => value.fill(evaluate(v,vars,scope,guards,spatial)?)?,
                ArrayOperation::Pop => value.pop()?,
                ArrayOperation::Clear => value.clear()?,
            }
            Some(value)
        }
        _ => typed::evaluate(value,vars,scope,guards,spatial).map(Reference::new),
    }
}
pub fn equal(left: &Reference, right: &Reference) -> bool {
    fn at(left: &Reference, right: &Reference, depth: usize) -> bool {
        if depth > 128 { return false; }
        match (left.ui_path(), right.ui_path()) {
            (Some(a), Some(b)) => return a == b,
            (Some(_), _) | (_, Some(_)) => return false,
            _ => {}
        }
        match (left.entity_identity(), right.entity_identity()) {
            (Some(a), Some(b)) => return a.key == b.key,
            (Some(_), _) | (_, Some(_)) => return false,
            _ => {}
        }
        match (left.pool_identity(), right.pool_identity()) {
            (Some(a), Some(b)) => return a.key == b.key,
            (Some(_), _) | (_, Some(_)) => return false,
            _ => {}
        }
        match (left.controller_identity(), right.controller_identity()) {
            (Some(a), Some(b)) => return a.key == b.key,
            (Some(_), _) | (_, Some(_)) => return false,
            _ => {}
        }
        if let (Some(a), Some(b)) = (left.elements(), right.elements()) {
            return a.len() == b.len() && a.iter().zip(&b).all(|(a,b)| at(a,b,depth+1));
        }
        if let (Some(a), Some(b)) = (left.entries(), right.entries()) {
            return a.len() == b.len() && a.iter().all(|(key,a)| b.get(key).is_some_and(|b| at(a,b,depth+1)));
        }
        match (left.snapshot(), right.snapshot()) { (Some(a), Some(b)) => typed::equal(&a,&b), _ => false }
    }
    at(left, right, 0)
}

pub fn refresh(vars: &mut SceneMaxVars, scope: Option<&mut SceneMaxScopeFrame>) {
    for (name, value) in &vars.2 { if vars.1.contains_key(name) { if let Some(value) = value.snapshot() { vars.1.insert(name.clone(), value); } } }
    if let Some(scope) = scope {
        let bindings = scope.bindings.iter().filter_map(|(name, value)| Some((name.clone(), value.value()?))).collect::<Vec<_>>();
        for (name, value) in bindings { let _ = cache(scope, &name, &value); }
        for (name, value) in &scope.references { if scope.typed.contains_key(name) { if let Some(value) = value.snapshot() { scope.typed.insert(name.clone(), value); } } }
    }
}
pub fn assign(name: &str, value: Reference, vars: &mut SceneMaxVars, mut scope: Option<&mut SceneMaxScopeFrame>, force_local: bool) -> Option<()> {
    let resolved = entity_path(name, vars, scope.as_deref());
    let name = resolved.as_str();
    let refresh_collections = name.contains('[') || value.collection();
    if name.contains('[') {
        let expression = scenemax_parser::parse_assignment_value(name).ok()??;
        fn path(v: &AssignmentValue, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>) -> Option<(String,Vec<Value>)> {
            match v {
                AssignmentValue::Symbol(name) => Some((name.clone(),vec![])),
                AssignmentValue::Access { value,index } => { let (name,mut indices)=path(value,vars,scope)?; indices.push(typed::evaluate(index,vars,scope,&HashMap::new(),&NoSceneMaxVmSpatial)?); Some((name,indices)) }
                _ => None,
            }
        }
        let (root,mut indices)=path(&expression,vars,scope.as_deref())?;
        let last=indices.pop()?;
        let root_value=lookup(&root,vars,scope.as_deref())?;
        let mut target=root_value.clone();
        for index in indices { target=target.field(&index)?; }
        target.set(&last,value)?;
        bind(&root,root_value,vars,scope.as_deref_mut(),force_local)?;
    } else { bind(name,value,vars,scope.as_deref_mut(),force_local)?; }
    if refresh_collections { refresh(vars,scope); }
    Some(())
}
fn cache(scope: &mut SceneMaxScopeFrame, name: &str, value: &Reference) -> Option<()> {
    let snapshot = value.snapshot().or_else(|| value.opaque().then_some(Value::Null))?;
    if let Some(n) = typed::number(&snapshot) { scope.vars.insert(name.into(), n as f32); }
    else { scope.vars.remove(name); }
    scope.references.remove(name);
    if value.has_identity() { scope.references.insert(name.into(), value.clone()); }
    scope.typed.insert(name.into(), snapshot);
    Some(())
}
fn bind(name: &str, value: Reference, vars: &mut SceneMaxVars, scope: Option<&mut SceneMaxScopeFrame>, force_local: bool) -> Option<()> {
    if let Some(scope) = scope {
        let local = force_local || scope.bindings.contains_key(name) || scope.vars.contains_key(name) || scope.typed.contains_key(name)
            || !(vars.0.contains_key(name) || vars.1.contains_key(name) || is_runtime_field_path(name));
        if local {
            if !force_local && scope.bindings.contains_key(name) { scope.bindings.get(name)?.replace(value.clone())?; }
            else { scope.bindings.insert(name.into(), Binding::new(value.clone())); }
            return cache(scope, name, &value);
        }
    }
    typed::store(vars, name, value.snapshot().or_else(|| value.opaque().then_some(Value::Null))?);
    if value.has_identity() { vars.2.insert(name.into(), value); }
    Some(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn run(source: &str) -> SceneMaxVars {
        let mut vars=SceneMaxVars::default();
        apply_initial_assignments(&scenemax_parser::parse_program(source).unwrap(), &mut vars);
        vars
    }
    #[test]
    fn ui_references_keep_their_paths_and_do_not_serialize_as_text() {
        let mut vars = run("var targets = [UI.layer1.panel, UI.layer1.panel.caption]\nvar selected = targets[1]");
        let selected = lookup("selected", &vars, None).unwrap();
        assert_eq!(selected.ui_path().as_deref(), Some("UI.layer1.panel.caption"));
        assert!(selected.snapshot().is_none());
        assert!(equal(&selected, &Reference::ui("UI.layer1.panel.caption")));
        assert!(!equal(&selected, &Reference::ui("UI.layer1.panel")));
        assert_eq!(lookup("targets", &vars, None).unwrap().snapshot(), Some(serde_json::json!([null, null])));
        typed::store(&mut vars, "UI.layer1.entry.text", serde_json::json!("typed text"));
        let value = evaluate(&AssignmentValue::Symbol("UI.layer1.entry.text".into()), &vars, None, &HashMap::new(), &NoSceneMaxVmSpatial).unwrap();
        assert_eq!(value.snapshot(), Some(serde_json::json!("typed text")));
    }
    #[test]
    fn entity_properties_ignore_stale_authored_name_fields() {
        let mut vars = SceneMaxVars::default();
        assign("actor", Reference::entity("instance"), &mut vars, None, false).unwrap();
        typed::store(&mut vars, "actor.anim_finished", serde_json::json!(99));
        typed::store(&mut vars, "instance.anim_finished", serde_json::json!(0));
        assert_eq!(typed::lookup("actor.anim_finished", &vars, None), Some(serde_json::json!(0)));
        let first = Reference::entity("same_name");
        let second = Reference::entity("same_name");
        let a = Reference::new(serde_json::json!({"member": null}));
        let b = Reference::new(serde_json::json!({"member": null}));
        a.set(&Value::String("member".into()), first.clone()).unwrap();
        b.set(&Value::String("member".into()), second).unwrap();
        assert!(!equal(&a, &b));
        b.set(&Value::String("member".into()), first).unwrap();
        assert!(equal(&a, &b));
    }

    #[test]
    fn entity_bindings_and_array_arguments_preserve_identity_after_rebinding() {
        let mut vars = SceneMaxVars::default();
        let first = Reference::entity("subject");
        let replacement = Reference::entity("subject");
        assign("source", first.clone(), &mut vars, None, false).unwrap();
        let mut frame = SceneMaxScopeFrame::default();
        assign("parameter", lookup("source", &vars, None).unwrap(), &mut vars, Some(&mut frame), true).unwrap();
        assign("source", replacement.clone(), &mut vars, None, false).unwrap();
        assert_eq!(lookup("parameter", &vars, Some(&frame)).unwrap(), first);
        assert_eq!(lookup("source", &vars, None).unwrap(), replacement);
        let values = Reference::array(vec![first.clone()]);
        assign("items", values, &mut vars, Some(&mut frame), true).unwrap();
        let parsed = scenemax_parser::parse_assignment_value("items[0]").unwrap().unwrap();
        assert_eq!(evaluate(&parsed, &vars, Some(&frame), &HashMap::new(), &NoSceneMaxVmSpatial).unwrap(), first);
        assert_eq!(entity_name("parameter", &vars, Some(&frame)), "subject");
        assert_eq!(typed::lookup("parameter", &vars, Some(&frame)), None);
    }

    #[test]
    fn entity_paths_follow_live_bindings_and_bound_alias_cycles() {
        let mut vars = SceneMaxVars::default();
        typed::store(&mut vars, "first.data.score", serde_json::json!([1]));
        typed::store(&mut vars, "second.data.score", serde_json::json!([2]));
        typed::store(&mut vars, "second.network.id", serde_json::json!(42));
        let mut scope = SceneMaxScopeFrame::default();
        scope.aliases.insert("target".into(), "first".into());
        assert_eq!(typed::lookup("target.data.score", &vars, Some(&scope)), Some(serde_json::json!([1])));
        assign("target", Reference::new(serde_json::json!("second")), &mut vars, Some(&mut scope), true).unwrap();
        assert_eq!(lookup("target.data.score", &vars, Some(&scope)).unwrap().snapshot(), Some(serde_json::json!([2])));
        assert_eq!(typed::lookup("target.network.id", &vars, Some(&scope)), Some(serde_json::json!(42)));
        assign("target", Reference::new(serde_json::json!(9)), &mut vars, Some(&mut scope), false).unwrap();
        assert_eq!(typed::lookup("target.data.score", &vars, Some(&scope)), None);
        scope.aliases.insert("cycle_a".into(), "cycle_b".into());
        scope.aliases.insert("cycle_b".into(), "cycle_a".into());
        assert_eq!(typed::lookup("cycle_a.data.score", &vars, Some(&scope)), None);
    }

    #[test]
    fn inherited_bindings_share_writes_but_new_locals_and_parameters_shadow() {
        let mut vars = SceneMaxVars::default();
        let mut parent = SceneMaxScopeFrame::default();
        assign("count", Reference::new(serde_json::json!(1)), &mut vars, Some(&mut parent), true).unwrap();
        assign("items", Reference::new(serde_json::json!([1])), &mut vars, Some(&mut parent), true).unwrap();
        let old_list = lookup("items", &vars, Some(&parent)).unwrap();
        let mut child = parent.clone();
        assign("count", Reference::new(serde_json::json!(2)), &mut vars, Some(&mut child), false).unwrap();
        assign("items", Reference::new(serde_json::json!([9])), &mut vars, Some(&mut child), false).unwrap();
        assert_eq!(typed::lookup("count", &vars, Some(&parent)), Some(serde_json::json!(2)));
        assert_eq!(typed::lookup("items", &vars, Some(&parent)), Some(serde_json::json!([9])));
        assert_eq!(old_list.snapshot(), Some(serde_json::json!([1])));
        assign("count", Reference::new(serde_json::json!(3)), &mut vars, Some(&mut child), true).unwrap();
        assign("count", Reference::new(serde_json::json!(4)), &mut vars, Some(&mut child), false).unwrap();
        assert_eq!(typed::lookup("count", &vars, Some(&parent)), Some(serde_json::json!(2)));
        assert_eq!(typed::lookup("count", &vars, Some(&child)), Some(serde_json::json!(4)));
    }
    #[test]
    fn aliases_share_mutations_but_rebinding_preserves_the_old_list() {
        let vars=run("var original = [1]\nvar alias = original\nalias.push(2)\nvar before = original.length\noriginal = [9]\nalias.push(3)");
        assert_eq!(vars.0["before"],2.);
        assert!(typed::equal(&typed::lookup("original",&vars,None).unwrap(),&serde_json::json!([9])));
        assert!(typed::equal(&typed::lookup("alias",&vars,None).unwrap(),&serde_json::json!([1,2,3])));
    }
    #[test]
    fn nested_collection_identity_survives_parent_slot_replacement() {
        let vars=run("var child = [1]\nvar parent = [child, child]\nvar alias = parent[0]\nalias.push(2)\nparent[0] = [9]\nalias[0] = 7");
        assert!(typed::equal(&typed::lookup("parent",&vars,None).unwrap(),&serde_json::json!([[9],[7,2]])));
        assert!(typed::equal(&typed::lookup("child",&vars,None).unwrap(),&serde_json::json!([7,2])));
    }
    #[test]
    fn equal_lists_are_independent_and_wire_replacement_detaches_one_binding() {
        let mut vars=run("var first = [1]\nvar independent = [1]\nvar alias = first\nalias.push(2)");
        assert!(typed::equal(&vars.1["independent"],&serde_json::json!([1])));
        typed::store(&mut vars,"first",serde_json::json!([8]));
        let p=scenemax_parser::parse_program("alias.push(3)").unwrap();
        let Statement::Assignment(a)=&p.statements[0] else {panic!()};
        apply_assignment_with_spatial(a,&mut vars,None,&HashMap::new(),&NoSceneMaxVmSpatial,false).unwrap();
        assert_eq!(vars.1["first"],serde_json::json!([8]));
        assert!(typed::equal(&vars.1["alias"],&serde_json::json!([1,2,3])));
    }
}
