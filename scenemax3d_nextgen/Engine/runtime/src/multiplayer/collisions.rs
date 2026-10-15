//! Bind Java #source selectors to individual remote entities.
use super::*;

pub(crate) type PolledEvent = (
    SceneMaxWhenEventKey,
    String,
    SceneMaxControllerKey,
    scenemax_parser::WhenEventStatement,
    Option<SceneMaxScopeFrame>,
);
pub(crate) type BoundEvent = (
    SceneMaxWhenEventKey,
    String,
    SceneMaxControllerKey,
    scenemax_parser::WhenEventStatement,
    Option<SceneMaxScopeFrame>,
);

pub(crate) fn resolve_local_targets(condition: &Condition, vars: &SceneMaxVars, scope: Option<&SceneMaxScopeFrame>, pools: &SceneMaxObjectPools) -> Condition {
    let resolve = |name: &str| {
        if name.starts_with('#') { return name.to_owned(); }
        let end = name.find(['.', '[']).unwrap_or(name.len());
        let target = scenemax_runtime_vm_core::references::entity_name_with_aliases(&name[..end], vars, scope, &pools.aliases);
        format!("{target}{}", &name[end..])
    };
    match condition {
        Condition::Collision { sources, target } => Condition::Collision { sources:sources.iter().map(|s|resolve(s)).collect(), target:resolve(target) },
        Condition::And(items) => Condition::And(items.iter().map(|c|resolve_local_targets(c,vars,scope,pools)).collect()),
        Condition::Or(items) => Condition::Or(items.iter().map(|c|resolve_local_targets(c,vars,scope,pools)).collect()),
        Condition::Not(item) => Condition::Not(Box::new(resolve_local_targets(item,vars,scope,pools))),
        other => other.clone(),
    }
}

fn has_selector(condition: &Condition) -> bool {
    match condition {
        Condition::Collision { sources, target } => {
            target.starts_with('#') || sources.iter().any(|s| s.starts_with('#'))
        }
        Condition::And(items) | Condition::Or(items) => items.iter().any(has_selector),
        Condition::Not(item) => has_selector(item),
        _ => false,
    }
}

fn selects_source(condition: &Condition, source: &str) -> bool {
    let matches = |reference: &str| {
        reference.strip_prefix('#').is_some_and(|s| {
            s[..s.find(['.', '[']).unwrap_or(s.len())].eq_ignore_ascii_case(source)
        })
    };
    match condition {
        Condition::Collision { sources, target } => {
            matches(target) || sources.iter().any(|s| matches(s))
        }
        Condition::And(items) | Condition::Or(items) => {
            items.iter().any(|c| selects_source(c, source))
        }
        Condition::Not(item) => selects_source(item, source),
        _ => false,
    }
}

fn bind(condition: &Condition, source: &str, runtime: &str) -> Condition {
    let reference = |s: &str| -> Option<String> {
        let Some(s) = s.strip_prefix('#') else {
            return Some(s.into());
        };
        let end = s.find(['.', '[']).unwrap_or(s.len());
        s[..end]
            .eq_ignore_ascii_case(source)
            .then(|| format!("{runtime}{}", &s[end..]))
    };
    match condition {
        Condition::Collision { sources, target } => {
            let Some(target) = reference(target) else {
                return Condition::Boolean(false);
            };
            let sources: Vec<_> = sources.iter().filter_map(|s| reference(s)).collect();
            if sources.is_empty() {
                Condition::Boolean(false)
            } else {
                Condition::Collision { sources, target }
            }
        }
        Condition::And(items) => {
            Condition::And(items.iter().map(|c| bind(c, source, runtime)).collect())
        }
        Condition::Or(items) => {
            Condition::Or(items.iter().map(|c| bind(c, source, runtime)).collect())
        }
        Condition::Not(item) => Condition::Not(Box::new(bind(item, source, runtime))),
        c => c.clone(),
    }
}

pub(crate) fn expand(events: Vec<PolledEvent>, network: Option<&Multiplayer>) -> Vec<BoundEvent> {
    let mut result = Vec::new();
    for (key, label, owner, event, scope) in events {
        if !has_selector(&event.condition) {
            result.push((key, label, owner, event, scope));
            continue;
        }
        let Some(network) = network else {
            continue;
        };
        let mut remotes: Vec<_> = network.remotes.iter().collect();
        remotes.sort_by_key(|(id, _)| **id);
        for (&id, name) in remotes {
            let Some(source) = network.sources.get(&id) else {
                continue;
            };
            if !selects_source(&event.condition, source) {
                continue;
            }
            if !network.owners.get(name).is_some_and(|id| *id != 0) {
                continue;
            }
            let mut bound = event.clone();
            bound.condition = bind(&event.condition, source, name);
            let key = match key {
                SceneMaxWhenEventKey::TopLevel(i) => SceneMaxWhenEventKey::NetworkTopLevel(i, id),
                SceneMaxWhenEventKey::Registered(i) => {
                    SceneMaxWhenEventKey::NetworkRegistered(i, id)
                }
                _ => continue,
            };
            result.push((
                key,
                format!("{label}:network:{id}"),
                SceneMaxControllerKey::NetworkWhen(Box::new(owner.clone()), id),
                bound,
                Some(SceneMaxScopeFrame {
                    network_event_target: Some(name.clone()),
                    ..scope.clone().unwrap_or_default()
                }),
            ));
        }
    }
    result
}

#[cfg(test)]
mod tests {
    #[test]
    fn registered_remote_collisions_preserve_each_activation_scope_and_capture_joint_identity() {
        let mut scope=SceneMaxScopeFrame::default();scope.begin_activation();
        scope.aliases.insert("probe".into(),"captured".into());scope.vars.insert("damage".into(),7.);
        let condition=Condition::Collision {sources:vec!["probe.\"Joint\"".into()],target:"#source".into()};
        let resolved=resolve_local_targets(&condition,&SceneMaxVars::default(),Some(&scope),&SceneMaxObjectPools::default());
        assert!(matches!(&resolved,Condition::Collision{sources,..} if sources==&["captured.\"Joint\""]));
        let Statement::WhenEvent(mut event)=scenemax_parser::parse_program("when probe collides with #source do\nwait 1 seconds\nnetwork.send \"hit\"\nend do").unwrap().statements.remove(0) else {panic!()};
        event.condition=resolved;
        let mut net=Multiplayer::default();net.remotes.insert(1,"remote".into());net.sources.insert(1,"source".into());net.owners.insert("remote".into(),2);
        let result=expand(vec![(SceneMaxWhenEventKey::Registered(1),"fixture".into(),SceneMaxControllerKey::RegisteredWhen(1),event,Some(scope.clone()))],Some(&net));
        let captured=result[0].4.as_ref().unwrap();assert_eq!(captured.vars["damage"],7.);assert_eq!(captured.activation_id,scope.activation_id);assert_eq!(captured.network_event_target.as_deref(),Some("remote"));
    }

    use super::*;
    #[test]
    fn multiplayer_missing_joint_does_not_collide_using_entity_origin() {
        let transforms = HashMap::from([
            ("local".into(), Transform::default()),
            ("remote".into(), Transform::default()),
        ]);
        let sources = vec!["local".into()];
        assert!(!collision_condition_matches(
            &sources,
            "remote.\"missing\"",
            Some(&transforms),
            None
        ));
        let contacts = SceneMaxPhysicsContacts {
            active_pairs: HashSet::from([normalized_collision_pair("local", "remote")]),
        };
        assert!(!physics_contact_condition_matches(
            &sources,
            "remote.\"missing\"",
            &contacts,
            &SceneMaxObjectPools::default(),
            Some(&transforms),
            None
        ));
    }
    #[test]
    fn multiplayer_collision_selectors_preserve_joint_and_match_source_case_insensitively() {
        let condition = Condition::Collision {
            sources: vec!["local".into()],
            target: "#Authored.\"Joint\"".into(),
        };
        assert_eq!(
            bind(&condition, "authored", "remote"),
            Condition::Collision {
                sources: vec!["local".into()],
                target: "remote.\"Joint\"".into()
            }
        );
        assert_eq!(
            bind(&condition, "different", "remote"),
            Condition::Boolean(false)
        );
    }

    #[test]
    fn multiplayer_collision_handlers_are_distinct_per_remote_and_keep_target_scope() {
        let program = scenemax_parser::parse_program("when local collides with #authored do\nwait 0.1 seconds\nnetwork.send \"contact\"\nend do").unwrap();
        let Statement::WhenEvent(event) = &program.statements[0] else {
            panic!("expected collision event")
        };
        let mut net = Multiplayer::default();
        for id in [101, 102] {
            let name = format!("remote_{id}");
            net.remotes.insert(id, name.clone());
            net.sources.insert(id, "authored".into());
            net.owners.insert(name, id as u16);
        }
        let bound = expand(
            vec![(
                SceneMaxWhenEventKey::TopLevel(1),
                "1".into(),
                SceneMaxControllerKey::When(1),
                event.clone(),
                None,
            )],
            Some(&net),
        );
        assert_eq!(bound.len(), 2);
        assert_ne!(bound[0].0, bound[1].0);
        assert_ne!(bound[0].2, bound[1].2);
        assert_eq!(
            bound[0].4.as_ref().unwrap().network_event_target.as_deref(),
            Some("remote_101")
        );
        assert_eq!(
            bound[1].4.as_ref().unwrap().network_event_target.as_deref(),
            Some("remote_102")
        );
    }
}
