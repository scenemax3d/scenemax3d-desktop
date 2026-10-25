use super::*;
use scenemax_parser::network::NetworkStatement;
use serde_json::Value;

#[derive(Default)]
pub(super) struct EventState {
    join: super::joins::JoinState,
    values: HashMap<String, Value>,
    shared_names: HashSet<String>,
    handlers: HashMap<String, Vec<RegisteredHandler>>,
    timers: HashMap<String, u32>,
    operations: Vec<Operation>,
    entity_data: HashMap<(u32, String), Value>,
}
struct RegisteredHandler {
    handler_id: u64,
    activation_id: u64,
    function: FunctionRuntime,
    scope: Option<SceneMaxScopeFrame>,
}

enum Operand {
    Unsupported,
    Value(Value),
    Name(String),
}
enum HandlerArgument {
    Message(String),
    Interval(Option<f64>),
}

enum Operation {
    EntityData(String, String, Operand),
    RetireEntityData(String),
    Declare(String, Operand),
    Assign(String, Value),
    Broadcast(Operand, Option<Operand>, Option<String>),
    Handler(
        u64,
        Operand,
        Option<HandlerArgument>,
        Vec<Statement>,
        Option<SceneMaxScopeFrame>,
    ),
    Join(Operand),
}

fn message_parameter(raw: &str) -> bool {
    let mut chars = raw.trim().chars();
    chars
        .next()
        .is_some_and(|c| c.is_ascii_alphabetic() || matches!(c, '_' | '$'))
        && chars.all(|c| c.is_ascii_alphanumeric() || matches!(c, '_' | '$'))
}

fn operand(
    raw: &str,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
) -> Operand {
    if let Ok(value) = serde_json::from_str(raw) {
        return Operand::Value(value);
    }
    if let Ok(p) = scenemax_parser::parse_program(&format!("var network_argument = {raw}")) {
        if let Some(Statement::Assignment(a)) = p.statements.first() {
            if let Some(value) = scenemax_runtime_vm_core::references::evaluate(
                &a.value,
                vars,
                scope,
                guards,
                &RuntimeVmSpatial {
                    transforms_by_name: Some(transforms),
                    collider_bounds: None,
                },
            ) {
                return value
                    .snapshot()
                    .map_or(Operand::Unsupported, Operand::Value);
            }
        }
    }
    Operand::Name(raw.trim().into())
}
fn resolve(value: Operand, state: &EventState) -> Option<Value> {
    match value {
        Operand::Unsupported => None,
        Operand::Value(value) => Some(value),
        Operand::Name(name) => state.values.get(&name).cloned(),
    }
}
fn text(value: Value) -> String {
    match value {
        Value::String(s) => s,
        _ => value.to_string(),
    }
}

pub(super) fn capture(
    commands: &mut Commands,
    statement: &Statement,
    vars: &mut SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
) -> bool {
    let arg = |raw: &str| operand(raw, vars, scope, guards, transforms);
    let operation = match statement {
        Statement::Network(NetworkStatement::EntityData {
            target,
            field,
            value,
        }) => Operation::EntityData(
            scenemax_runtime_vm_core::references::entity_name(target, vars, scope),
            field.clone(),
            arg(value),
        ),
        Statement::Network(NetworkStatement::Variable { name, value }) => {
            Operation::Declare(name.clone(), arg(value))
        }
        Statement::Network(NetworkStatement::Broadcast { event, message }) => {
            Operation::Broadcast(arg(event), message.as_deref().map(arg), None)
        }
        Statement::Network(NetworkStatement::Send {
            recipient: Some(recipient),
            event,
            message,
        }) => Operation::Broadcast(
            arg(event),
            message.as_deref().map(arg),
            Some(scenemax_runtime_vm_core::references::entity_name(
                recipient, vars, scope,
            )),
        ),
        Statement::Network(NetworkStatement::Send {
            recipient: None,
            event,
            message,
        }) => {
            let Some(target) = scope.and_then(|s| s.network_event_target.clone()) else {
                return true;
            };
            Operation::Broadcast(arg(event), message.as_deref().map(arg), Some(target))
        }
        Statement::Network(NetworkStatement::On {
            handler_id,
            event,
            argument,
            actions,
        }) => Operation::Handler(
            *handler_id,
            arg(event),
            argument.as_ref().map(|argument| {
                if message_parameter(argument) {
                    return HandlerArgument::Message(argument.trim().to_owned());
                }
                HandlerArgument::Interval(
                    match arg(argument) {
                        Operand::Value(value) => value
                            .as_f64()
                            .or_else(|| value.as_str()?.trim().parse().ok()),
                        _ => None,
                    }
                    .filter(|n| n.is_finite()),
                )
            }),
            actions.clone(),
            scope.cloned(),
        ),
        Statement::Network(NetworkStatement::JoinSession { value }) => Operation::Join(arg(value)),
        _ => return false,
    };
    if let Operation::EntityData(target, field, Operand::Value(value)) = &operation {
        if field != "shader" {
            scenemax_runtime_vm_core::typed::store(
                vars,
                &format!("{target}.data.{field}"),
                value.clone(),
            );
        }
    }
    if let Operation::Declare(name, Operand::Value(value)) = &operation {
        if !vars.0.contains_key(name) && !vars.1.contains_key(name) {
            scenemax_runtime_vm_core::typed::store(vars, name, value.clone());
        }
    }
    if matches!(&operation, Operation::Join(_)) {
        scenemax_runtime_vm_core::typed::store(vars, "network.joinPending", serde_json::json!(1));
    }
    commands.queue(move |world: &mut World| {
        if let Some(mut net) = world.get_resource_mut::<Multiplayer>() {
            if net.events.operations.len() < 4096 {
                net.events.operations.push(operation);
            }
        }
    });
    matches!(statement, Statement::Network(_))
}

// Retire data in execution order before a new instance takes the authored name.
pub(crate) fn retire_entity_data(commands: &mut Commands, name: &str, vars: &mut SceneMaxVars) {
    let prefix = format!("{name}.data.");
    vars.0.retain(|key, _| !key.starts_with(&prefix));
    vars.1.retain(|key, _| !key.starts_with(&prefix));
    vars.2.retain(|key, _| !key.starts_with(&prefix));
    let name = name.to_owned();
    commands.queue(move |world: &mut World| {
        if let Some(mut net) = world.get_resource_mut::<Multiplayer>() {
            // Lifecycle boundaries cannot be dropped behind queued data writes.
            net.events
                .operations
                .push(Operation::RetireEntityData(name));
        }
    });
}

// Called immediately after successful execution. Freeze the stored value here:
// evaluating the expression again changes random/spatial operands, while a
// deferred World lookup loses intermediate writes and event ordering.
pub(super) fn assignment_result(
    commands: &mut Commands,
    name: &str,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
) {
    let root = name.split('[').next().unwrap_or(name);
    if scope.is_some_and(|s| s.vars.contains_key(root) || s.typed.contains_key(root)) {
        return;
    }
    let Some(value) = scenemax_runtime_vm_core::typed::lookup(root, vars, None) else {
        return;
    };
    let name = root.to_owned();
    commands.queue(move |world: &mut World| {
        if let Some(mut net) = world.get_resource_mut::<Multiplayer>() {
            if net.events.operations.len() < 4096 {
                net.events.operations.push(Operation::Assign(name, value));
            }
        }
    });
}

// Java routes an explicitly owned entity back to this client, as well as remote owners.
fn recipient_owner(net: &Multiplayer, name: &str) -> Option<u16> {
    if let Some((_, request)) = net.locals.get(name) {
        let client = net.client.as_ref()?;
        if client.network_id(*request).is_some_and(|id| id != 0) && client.client_id() != 0 {
            return Some(client.client_id());
        }
    }
    net.owners.get(name).copied().filter(|id| *id != 0)
}

pub(super) fn flush(world: &mut World, net: &mut Multiplayer) {
    let pending = std::mem::take(&mut net.events.entity_data);
    for ((entity, field), value) in pending {
        receive(
            world,
            net,
            Incoming::EntityData {
                entity,
                field,
                value,
            },
        );
    }
    let operations = std::mem::take(&mut net.events.operations);
    for op in operations {
        let outgoing = match op {
            Operation::RetireEntityData(name) => {
                let prefix = format!("{name}.data.");
                net.events.values.retain(|key, _| !key.starts_with(&prefix));
                net.entity_updates.retain(|(target, _, _)| target != &name);
                None
            }
            Operation::EntityData(target, field, value) => {
                let unresolved = matches!(&value, Operand::Name(_));
                let Some(value) = resolve(value, &net.events) else {
                    continue;
                };
                if field != "shader" {
                    let name = format!("{target}.data.{field}");
                    // Resolved writes already executed at capture time. Do not
                    // overwrite a newer local-only write during deferred publication.
                    if unresolved {
                        apply_scalar(world, &name, &value);
                    }
                    if net.events.values.len() < 2048 {
                        net.events.values.insert(name, value.clone());
                    }
                }
                if net.entity_updates.len() < 4096 {
                    net.entity_updates.push((target, field, value));
                }
                None
            }
            Operation::Declare(name, value) => {
                let Some(value) = resolve(value, &net.events) else {
                    tracing::warn!(variable = name, "unresolved network variable initializer");
                    continue;
                };
                if net.events.values.len() >= 2048 {
                    continue;
                }
                net.events.shared_names.insert(name.clone());
                let had_value = net.events.values.contains_key(&name);
                net.events
                    .values
                    .entry(name.clone())
                    .or_insert(value.clone());
                // Capture initializes new declarations immediately. Preserve any
                // script edits made before this deferred flush, including offline.
                // A previously received server value still takes precedence.
                let initialized = world.get_resource::<SceneMaxVars>().is_some_and(|vars| {
                    scenemax_runtime_vm_core::typed::lookup(&name, vars, None).is_some()
                });
                if had_value || !initialized {
                    apply_scalar(world, &name, &net.events.values[&name]);
                }
                Some(Outgoing::Variable {
                    name,
                    initial: true,
                    value,
                })
            }
            Operation::Assign(name, value) => {
                if !net.events.shared_names.contains(&name) {
                    continue;
                }
                net.events.values.insert(name.clone(), value.clone());
                Some(Outgoing::Variable {
                    name,
                    initial: false,
                    value,
                })
            }
            Operation::Broadcast(event, message, recipient) => {
                let Some(event) = resolve(event, &net.events).map(text) else {
                    continue;
                };
                let message = message.and_then(|m| resolve(m, &net.events)).map(text);
                let target = if let Some(name) = recipient {
                    let Some(owner) = recipient_owner(net, &name) else {
                        tracing::warn!(entity = name, "network event recipient is not registered");
                        continue;
                    };
                    owner
                } else {
                    0
                };
                write_runtime_diagnostic_line(format!(
                    "NETWORK:EVENT_SEND name={event:?} target={target}"
                ));
                Some(Outgoing::Event {
                    target,
                    name: event,
                    message,
                })
            }
            Operation::Handler(handler_id, event, argument, actions, scope) => {
                let Some(event) = resolve(event, &net.events).map(text) else {
                    continue;
                };
                let event = event.trim().to_owned();
                if event.is_empty() {
                    continue;
                }
                let activation_id = scope.as_ref().map_or(0, |s| s.activation_id);
                if net.events.handlers.get(&event).is_some_and(|handlers| {
                    handlers
                        .iter()
                        .any(|h| h.handler_id == handler_id && h.activation_id == activation_id)
                }) {
                    continue;
                }
                let interval = argument
                    .as_ref()
                    .and_then(|arg| match arg {
                        HandlerArgument::Interval(value) => *value,
                        _ => None,
                    })
                    .filter(|n| *n > 0.);
                if net.events.handlers.values().map(Vec::len).sum::<usize>() >= 256 {
                    continue;
                }
                net.events
                    .handlers
                    .entry(event.clone())
                    .or_default()
                    .push(RegisteredHandler {
                        handler_id,
                        activation_id,
                        function: FunctionRuntime {
                            params: match argument {
                                Some(HandlerArgument::Message(name)) => vec![name],
                                _ => vec![],
                            },
                            guard: None,
                            guard_recheck: false,
                            actions,
                        },
                        scope,
                    });
                interval.map(|seconds| {
                    let interval_ms = (seconds * 1000.).clamp(1., u32::MAX as f64) as u32;
                    net.events.timers.insert(event.clone(), interval_ms);
                    Outgoing::ServerEvent {
                        name: event,
                        interval_ms,
                    }
                })
            }
            Operation::Join(value) => {
                if net.events.join.pending() {
                    net.events.join.reject_overlap();
                    continue;
                }
                let Some(value) = resolve(value, &net.events) else {
                    net.events.join.fail("invalid session selector");
                    continue;
                };
                let Some(selector) = super::joins::Selector::parse(value.clone()) else {
                    net.events.join.fail("invalid session selector");
                    continue;
                };
                let Some(client) = net.client.as_ref() else {
                    net.events.join.fail("offline");
                    continue;
                };
                if matches!(client.state(), State::Closed | State::Rejected) {
                    net.events.join.fail("connection unavailable");
                    continue;
                }
                if net
                    .client
                    .as_ref()
                    .is_some_and(|client| client.client_id() == 0)
                {
                    net.events
                        .operations
                        .push(Operation::Join(Operand::Value(value)));
                    continue;
                }
                let message = selector.message();
                if let Some(client) = net.client.as_mut() {
                    if let Err(error) = client.join(message) {
                        tracing::warn!(%error,"multiplayer session join failed");
                        net.events.join.fail("join request failed");
                        continue;
                    }
                    net.events
                        .join
                        .begin(selector, client.session_id(), Instant::now());
                    for (name, interval_ms) in &net.events.timers {
                        if let Err(error) = client.send(
                            None,
                            Outgoing::ServerEvent {
                                name: name.clone(),
                                interval_ms: *interval_ms,
                            },
                        ) {
                            tracing::warn!(%error, "multiplayer timer re-registration failed");
                        }
                    }
                }
                let names = net.remotes.values().cloned().collect::<HashSet<_>>();
                let mut query = world.query::<(Entity, &SceneMaxEntity)>();
                let entities = query
                    .iter(world)
                    .filter(|(_, s)| names.contains(&s.name))
                    .map(|(e, _)| e)
                    .collect::<Vec<_>>();
                for entity in entities {
                    let _ = world.despawn(entity);
                }
                // Queued remote declarations may not have materialized yet.
                if let Some(mut queue) = world.get_resource_mut::<DelayedActionQueue>() {
                    queue.actions.retain(|entry| {
                        !entry.actions.iter().any(|action| {
                            matches!(action, Statement::ModelDecl { name, .. } if names.contains(name))
                        })
                    });
                }
                for (id, name) in net.remotes.clone() {
                    remove_entity(world, net, id, &name);
                }
                net.locals.clear();
                net.sent_poses.clear();
                net.local_sequences.clear();
                net.local_actions.clear();
                net.remotes.clear();
                net.replay = replay::ReplayHistory::default();
                net.sources.clear();
                net.owners.clear();
                net.poses.clear();
                net.corrections.clear();
                net.remote_pending.clear();
                net.pending.clear();
                net.entity_updates.clear();
                net.events.entity_data.clear();
                net.widgets = super::widgets::WidgetSync::default();
                if let Some(mut queue) = world.get_resource_mut::<SceneMaxUiActionQueue>() {
                    queue.remote_actions.clear();
                }
                None
            }
        };
        if let (Some(client), Some(message)) = (net.client.as_mut(), outgoing) {
            if let Err(error) = client.send(None, message) {
                tracing::warn!(%error,"network operation rejected");
            }
        }
    }
}

pub(super) fn update_join(net: &mut Multiplayer, now: Instant) {
    net.events.join.update(net.client.as_ref(), now);
}

pub(super) fn join_pending(state: &EventState) -> bool {
    joining(state) || state.join.pending()
}

pub(super) fn join_error(state: &EventState) -> &str {
    state.join.error()
}

pub(super) fn joining(state: &EventState) -> bool {
    state
        .operations
        .iter()
        .any(|op| matches!(op, Operation::Join(_)))
}

fn apply_scalar(world: &mut World, name: &str, value: &Value) {
    if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
        scenemax_runtime_vm_core::typed::store(&mut vars, name, value.clone());
    }
}

pub(super) fn receive(world: &mut World, net: &mut Multiplayer, message: Incoming) {
    match message {
        Incoming::EntityData {
            entity,
            field,
            value,
        } => {
            if field.eq_ignore_ascii_case("shader") {
                queue_remote(
                    net,
                    entity,
                    format!(
                        "{{network_entity}}.shader = {}",
                        Value::String(if value.is_null() {
                            String::new()
                        } else {
                            text(value)
                        })
                    ),
                );
            } else if let Some(name) = net.remotes.get(&entity) {
                let name = format!("{name}.data.{field}");
                apply_scalar(world, &name, &value);
                if net.events.values.len() < 2048 {
                    net.events.values.insert(name, value);
                }
            } else if net.events.entity_data.len() < 4096 {
                net.events.entity_data.insert((entity, field), value);
            }
        }
        Incoming::Variable { name, value, .. } => {
            if name.starts_with("$ui:") {
                super::widgets::receive(&mut net.widgets, name, value);
                return;
            }
            if net.events.values.len() < 2048 || net.events.values.contains_key(&name) {
                net.events.shared_names.insert(name.clone());
                apply_scalar(world, &name, &value);
                net.events.values.insert(name, value);
            }
        }
        Incoming::Event { name, message } => {
            write_runtime_diagnostic_line(format!(
                "NETWORK:EVENT_RECEIVE name={name:?} handlers={}",
                net.events.handlers.get(name.trim()).map_or(0, Vec::len)
            ));
            if let Some(handlers) = net.events.handlers.get(name.trim()) {
                let text = message.unwrap_or_default();
                let value = text
                    .trim()
                    .parse::<f64>()
                    .ok()
                    .filter(|n| n.is_finite())
                    .map_or_else(|| Value::String(text.clone()), |n| serde_json::json!(n));
                for handler in handlers {
                    let mut scope = handler.scope.clone().unwrap_or_default();
                    scope.begin_activation();
                    scope.caller = None;
                    scope.return_value = None;
                    scope.return_reference = None;
                    scope.value_call_depth = 0;
                    scope.value_call_steps = 0;
                    scope.value_call_failed = false;
                    if let Some(param) = handler.function.params.first() {
                        scope.parameters.insert(param.clone());
                        scenemax_runtime_vm_core::typed::assign(
                            param,
                            value.clone(),
                            &mut SceneMaxVars::default(),
                            Some(&mut scope),
                            true,
                        );
                    }
                    enqueue_scoped(world, handler.function.actions.clone(), None, Some(scope));
                }
            }
        }
        _ => {}
    }
}

pub(super) fn remove_entity(world: &mut World, net: &mut Multiplayer, id: u32, name: &str) {
    // The same network ID can return after a room rejoin. Retire its old
    // script identity so replay can recreate the wire-addressed runtime name.
    if let Some(mut assets) = world.get_resource_mut::<SceneMaxRuntimeAssets>() {
        if let Some(reference) = assets.root_entity_declarations.remove(name) {
            reference.retire_entity();
        }
        assets.pending_entity_handles.remove(name);
        assets.gltf_handles_by_name.remove(name);
        assets.gltf_animation_names_by_name.remove(name);
        assets.model_resources_by_name.remove(name);
    }
    if let Some(mut pools) = world.get_resource_mut::<SceneMaxObjectPools>() {
        pools.root_entity_names.remove(name);
        pools.aliases.remove(name);
        pools.entity_values.2.remove(name);
    }
    if let Some(mut bounds) = world.get_resource_mut::<SceneMaxColliderBounds>() {
        bounds.radius_by_name.remove(name);
        bounds.shape_by_name.remove(name);
        bounds.owner_by_name.remove(name);
        bounds.hidden_by_name.remove(name);
    }
    if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
        vars.2.remove(name);
    }
    net.owners.remove(name);
    net.sources.remove(&id);
    net.events
        .entity_data
        .retain(|(entity, _), _| *entity != id);
    let prefix = format!("{name}.data.");
    net.events.values.retain(|key, _| !key.starts_with(&prefix));
    if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
        vars.0.retain(|key, _| !key.starts_with(&prefix));
        vars.0.remove(&format!("{name}.network.id"));
        vars.1.retain(|key, _| !key.starts_with(&prefix));
        vars.2.retain(|key, _| !key.starts_with(&prefix));
        vars.1.remove(&format!("{name}.network.id"));
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn explicit_recipient_routes_registered_local_remote_and_rejects_unacknowledged_or_unknown() {
        let (server, peer, mut client) = super::super::joins::tests::connection();
        super::super::joins::tests::accept(&server, peer, &mut client, 1000, "test");
        let request = client
            .register(Spawn {
                archetype: "box".into(),
                player: "fixture".into(),
                source_name: "subject".into(),
                command: "{network_entity} => box".into(),
            })
            .unwrap();
        let mut net = Multiplayer {
            client: Some(client),
            ..Default::default()
        };
        net.locals
            .insert("owned".into(), (Entity::PLACEHOLDER, request));
        net.owners.insert("remote".into(), 2);
        assert_eq!(recipient_owner(&net, "owned"), None);
        assert_eq!(recipient_owner(&net, "remote"), Some(2));
        assert_eq!(recipient_owner(&net, "absent"), None);
        let mut packet = Vec::new();
        scenemax_multiplayer::write_header(
            scenemax_multiplayer::CREATE_ENTITY_ACCEPTED,
            1,
            &mut packet,
        );
        packet.extend(101u32.to_le_bytes());
        packet.extend(request.to_le_bytes());
        server.send_to(&packet, peer).unwrap();
        let deadline = Instant::now() + Duration::from_secs(1);
        while net.client.as_ref().unwrap().network_id(request).is_none() {
            net.client.as_mut().unwrap().poll().unwrap();
            assert!(Instant::now() < deadline);
        }
        assert_eq!(recipient_owner(&net, "owned"), Some(1));
        net.locals.remove("owned");
        assert_eq!(recipient_owner(&net, "owned"), None);
    }

    use super::*;
    #[test]
    fn handler_parameter_names_do_not_evaluate_globals_or_become_timer_expressions() {
        for (argument, parameter, timer) in [
            ("received", true, false),
            ("0", false, false),
            ("-1", false, false),
            ("\"0.25\"", false, true),
            ("\"received\"", false, false),
        ] {
            let mut world = World::new();
            world.init_resource::<Multiplayer>();
            world.init_resource::<DelayedActionQueue>();
            let mut vars = SceneMaxVars::default();
            scenemax_runtime_vm_core::typed::store(&mut vars, "received", serde_json::json!(-77));
            let statement = scenemax_parser::parse_program(&format!(
                "network.on (\"event\", {argument}) = do\nLogger.info received\nend do"
            ))
            .unwrap()
            .statements
            .remove(0);
            capture(
                &mut world.commands(),
                &statement,
                &mut vars,
                None,
                &HashMap::new(),
                &HashMap::new(),
            );
            world.flush();
            let mut net = world.remove_resource::<Multiplayer>().unwrap();
            flush(&mut world, &mut net);
            assert_eq!(net.events.timers.contains_key("event"), timer);
            assert_eq!(
                !net.events.handlers["event"][0].function.params.is_empty(),
                parameter
            );
            receive(
                &mut world,
                &mut net,
                Incoming::Event {
                    name: "event".into(),
                    message: Some("7".into()),
                },
            );
            if parameter {
                assert_eq!(
                    world.resource::<DelayedActionQueue>().actions[0]
                        .scope
                        .as_ref()
                        .unwrap()
                        .typed["received"],
                    serde_json::json!(7.)
                );
            }
            assert_eq!(vars.0["received"], -77.);
        }
    }
    #[test]
    fn event_handlers_fan_out_with_private_mutable_messages_and_captured_collections() {
        let mut world = World::new();
        world.init_resource::<DelayedActionQueue>();
        let mut vars = SceneMaxVars::default();
        scenemax_runtime_vm_core::typed::store(&mut vars, "message", serde_json::json!(99));
        let mut scope = SceneMaxScopeFrame::default();
        let collection =
            scenemax_runtime_vm_core::references::Reference::new(serde_json::json!([1]));
        scenemax_runtime_vm_core::references::assign(
            "items",
            collection.clone(),
            &mut vars,
            Some(&mut scope),
            true,
        )
        .unwrap();
        let mut net = Multiplayer::default();
        for value in [2, 2, 3] {
            let actions = scenemax_parser::parse_program(&format!(
                "message = message + 1\nitems.push({value})"
            ))
            .unwrap()
            .statements;
            net.events.operations.push(Operation::Handler(
                value,
                Operand::Value(serde_json::json!("event")),
                Some(HandlerArgument::Message("message".into())),
                actions,
                Some(scope.clone()),
            ));
        }
        flush(&mut world, &mut net);
        receive(
            &mut world,
            &mut net,
            Incoming::Event {
                name: "event".into(),
                message: Some(" 7 ".into()),
            },
        );
        let pending = std::mem::take(&mut world.resource_mut::<DelayedActionQueue>().actions);
        assert_eq!(pending.len(), 2);
        for pending in pending {
            let mut scope = pending.scope.unwrap();
            assert_eq!(scope.typed["message"], serde_json::json!(7.));
            for action in pending.actions {
                let Statement::Assignment(assignment) = action else {
                    panic!("assignment");
                };
                scenemax_runtime_vm_core::apply_assignment_with_spatial(
                    &assignment,
                    &mut vars,
                    Some(&mut scope),
                    &HashMap::new(),
                    &scenemax_runtime_vm_core::NoSceneMaxVmSpatial,
                    false,
                )
                .unwrap();
            }
            assert_eq!(scope.vars["message"], 8.);
        }
        assert_eq!(vars.0["message"], 99.);
        assert!(scenemax_runtime_vm_core::typed::equal(
            &collection.snapshot().unwrap(),
            &serde_json::json!([1, 2, 3])
        ));
        receive(
            &mut world,
            &mut net,
            Incoming::Event {
                name: "event".into(),
                message: None,
            },
        );
        for pending in &world.resource::<DelayedActionQueue>().actions {
            assert_eq!(
                pending.scope.as_ref().unwrap().typed["message"],
                serde_json::json!("")
            );
        }
    }
    #[test]
    fn deferred_declaration_preserves_same_frame_array_edits_without_server() {
        let mut world = World::new();
        let mut vars = SceneMaxVars::default();
        scenemax_runtime_vm_core::typed::store(&mut vars, "shared", serde_json::json!([1]));
        world.insert_resource(vars);
        let mut net = Multiplayer::default();
        net.events.operations.push(Operation::Declare(
            "shared".into(),
            Operand::Value(serde_json::json!([])),
        ));
        net.events
            .operations
            .push(Operation::Assign("shared".into(), serde_json::json!([1])));
        flush(&mut world, &mut net);
        assert_eq!(
            world.resource::<SceneMaxVars>().1["shared"],
            serde_json::json!([1])
        );
        assert_eq!(net.events.values["shared"], serde_json::json!([1]));
        // An existing authoritative value still wins when the declaration arrives.
        net.events
            .values
            .insert("shared".into(), serde_json::json!([9]));
        net.events.operations.push(Operation::Declare(
            "shared".into(),
            Operand::Value(serde_json::json!([])),
        ));
        flush(&mut world, &mut net);
        assert_eq!(
            world.resource::<SceneMaxVars>().1["shared"],
            serde_json::json!([9])
        );
    }
    #[test]
    fn startup_prepass_does_not_execute_array_commands_past_waits() {
        let program =
            scenemax_parser::parse_program("var items = [1]\nwait 2 seconds\nitems.push(2)")
                .unwrap();
        let mut vars = SceneMaxVars::default();
        apply_initial_assignments(&program, &mut vars);
        assert!(scenemax_runtime_vm_core::typed::equal(
            &scenemax_runtime_vm_core::typed::lookup("items", &vars, None).unwrap(),
            &serde_json::json!([1]),
        ));
    }
    #[test]
    fn entity_wire_operands_cannot_fall_back_to_stale_named_values() {
        let mut vars = SceneMaxVars::default();
        scenemax_runtime_vm_core::references::assign(
            "source",
            scenemax_runtime_vm_core::references::Reference::entity("instance"),
            &mut vars,
            None,
            false,
        )
        .unwrap();
        let value = operand("source", &vars, None, &HashMap::new(), &HashMap::new());
        let mut state = EventState::default();
        state.values.insert("source".into(), serde_json::json!(99));
        assert_eq!(resolve(value, &state), None);
    }

    #[test]
    fn retirement_discards_old_data_without_erasing_new_instance_writes() {
        let mut world = World::new();
        world.init_resource::<Multiplayer>();
        let mut vars = SceneMaxVars::default();
        let write = |field: &str, value: &str| {
            Statement::Network(NetworkStatement::EntityData {
                target: "subject".into(),
                field: field.into(),
                value: value.into(),
            })
        };
        capture(
            &mut world.commands(),
            &write("old", "[1,2]"),
            &mut vars,
            None,
            &HashMap::new(),
            &HashMap::new(),
        );
        retire_entity_data(&mut world.commands(), "subject", &mut vars);
        assert!(!vars.1.contains_key("subject.data.old"));
        capture(
            &mut world.commands(),
            &write("fresh", "7"),
            &mut vars,
            None,
            &HashMap::new(),
            &HashMap::new(),
        );
        world.insert_resource(vars);
        world.flush();
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        net.events
            .values
            .insert("subject.data.previous".into(), serde_json::json!(99));
        flush(&mut world, &mut net);
        assert!(!net.events.values.contains_key("subject.data.previous"));
        assert!(!net.events.values.contains_key("subject.data.old"));
        assert_eq!(world.resource::<SceneMaxVars>().0["subject.data.fresh"], 7.);
        assert_eq!(net.entity_updates.len(), 1);
        assert_eq!(net.entity_updates[0].1, "fresh");
    }

    #[test]
    fn deferred_entity_publication_does_not_overwrite_newer_local_data() {
        let mut world = World::new();
        world.init_resource::<Multiplayer>();
        let mut vars = SceneMaxVars::default();
        let statement = Statement::Network(NetworkStatement::EntityData {
            target: "subject".into(),
            field: "score".into(),
            value: "4".into(),
        });
        capture(
            &mut world.commands(),
            &statement,
            &mut vars,
            None,
            &HashMap::new(),
            &HashMap::new(),
        );
        assert_eq!(vars.0["subject.data.score"], 4.);
        scenemax_runtime_vm_core::typed::store(
            &mut vars,
            "subject.data.score",
            serde_json::json!(5),
        );
        world.insert_resource(vars);
        world.flush();
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        flush(&mut world, &mut net);
        assert_eq!(world.resource::<SceneMaxVars>().0["subject.data.score"], 5.);
        assert!(matches!(&net.entity_updates[0], (target, field, value)
            if target == "subject" && field == "score" && *value == serde_json::json!(4)));
    }

    #[test]
    fn directed_events_freeze_the_live_parameter_target_before_rebinding() {
        let mut world = World::new();
        world.init_resource::<Multiplayer>();
        let mut vars = SceneMaxVars::default();
        let mut scope = SceneMaxScopeFrame::default();
        scope.aliases.insert("recipient".into(), "stale".into());
        for target in ["first", "second"] {
            scenemax_runtime_vm_core::references::assign(
                "recipient",
                scenemax_runtime_vm_core::references::Reference::new(serde_json::json!(target)),
                &mut vars,
                Some(&mut scope),
                true,
            )
            .unwrap();
            capture(
                &mut world.commands(),
                &Statement::Network(NetworkStatement::Send {
                    recipient: Some("recipient".into()),
                    event: "\"check\"".into(),
                    message: None,
                }),
                &mut vars,
                Some(&scope),
                &HashMap::new(),
                &HashMap::new(),
            );
        }
        world.flush();
        let targets = world
            .resource::<Multiplayer>()
            .events
            .operations
            .iter()
            .filter_map(|operation| {
                if let Operation::Broadcast(_, _, Some(target)) = operation {
                    Some(target.as_str())
                } else {
                    None
                }
            })
            .collect::<Vec<_>>();
        assert_eq!(targets, ["first", "second"]);
    }

    #[test]
    fn array_commands_capture_executed_result_and_skip_local_shadow() {
        let mut world = World::new();
        world.insert_resource(Multiplayer::default());
        let mut vars = SceneMaxVars::default();
        scenemax_runtime_vm_core::typed::store(&mut vars, "shared", serde_json::json!([1]));
        let mut local = SceneMaxScopeFrame::default();
        local.typed.insert("shared".into(), serde_json::json!([]));
        // Simulate the normal action executor. Capture must use its result,
        // even when it differs from re-evaluating the source expression.
        scenemax_runtime_vm_core::typed::store(&mut vars, "shared", serde_json::json!([1, 99]));
        for scope in [Some(&local), None] {
            assignment_result(&mut world.commands(), "shared", &vars, scope);
        }
        scenemax_runtime_vm_core::typed::store(&mut vars, "shared", serde_json::json!([]));
        world.insert_resource(vars);
        world.flush();
        let operations = &world.resource::<Multiplayer>().events.operations;
        assert_eq!(operations.len(), 1);
        assert!(
            matches!(&operations[0], Operation::Assign(name, value) if name == "shared" && *value == serde_json::json!([1,99]))
        );
    }
    #[test]
    fn startup_assignments_publish_each_executed_result_in_order() {
        let mut world = World::new();
        world.insert_resource(Multiplayer::default());
        let mut vars = SceneMaxVars::default();
        let program = scenemax_parser::parse_program(
            "var shared = [1]\nshared.push(2)\nshared[0] = 7\nshared.clear",
        )
        .unwrap();
        for action in &program.statements {
            apply_startup_leaf_action(
                action,
                &mut world.commands(),
                &mut vars,
                &mut SceneMaxObjectPools::default(),
                &mut SceneMaxCameraSystem::default(),
                &mut SceneMaxRuntimeAssets::default(),
                &mut SceneMaxUiActionQueue::default(),
                &HashMap::new(),
                &HashMap::new(),
                &mut HashMap::new(),
                &HashMap::new(),
                &HashMap::new(),
                &mut SceneMaxColliderBounds::default(),
                0,
            );
        }
        world.flush();
        let operations = &world.resource::<Multiplayer>().events.operations;
        let expected = [
            serde_json::json!([1]),
            serde_json::json!([1, 2]),
            serde_json::json!([7, 2]),
            serde_json::json!([]),
        ];
        assert_eq!(operations.len(), expected.len());
        for (operation, expected) in operations.iter().zip(expected) {
            assert!(matches!(operation, Operation::Assign(name, value)
                if name == "shared" && scenemax_runtime_vm_core::typed::equal(value, &expected)));
        }
    }
    #[test]
    fn random_assignment_and_following_event_share_the_executed_value() {
        let mut world = World::new();
        world.insert_resource(Multiplayer::default());
        let mut vars = SceneMaxVars::default();
        let program = scenemax_parser::parse_program(
            "sample = rnd(1000000)\nnetwork.broadcast (\"sampled\", sample)",
        )
        .unwrap();
        let mut expected = Vec::new();
        for _ in 0..16 {
            for action in &program.statements {
                apply_startup_leaf_action(
                    action,
                    &mut world.commands(),
                    &mut vars,
                    &mut SceneMaxObjectPools::default(),
                    &mut SceneMaxCameraSystem::default(),
                    &mut SceneMaxRuntimeAssets::default(),
                    &mut SceneMaxUiActionQueue::default(),
                    &HashMap::new(),
                    &HashMap::new(),
                    &mut HashMap::new(),
                    &HashMap::new(),
                    &HashMap::new(),
                    &mut SceneMaxColliderBounds::default(),
                    0,
                );
            }
            expected.push(scenemax_runtime_vm_core::typed::lookup("sample", &vars, None).unwrap());
        }
        world.flush();
        let operations = &world.resource::<Multiplayer>().events.operations;
        assert_eq!(operations.len(), 32);
        for (pair, expected) in operations.chunks_exact(2).zip(expected) {
            assert!(
                matches!(&pair[0], Operation::Assign(name, value) if name == "sample" && value == &expected)
            );
            assert!(
                matches!(&pair[1], Operation::Broadcast(_, Some(Operand::Value(value)), None) if value == &expected)
            );
        }
    }
    #[test]
    fn pool_factory_publishes_global_writes_and_preserves_local_return_boundary() {
        let mut world = World::new();
        world.insert_resource(Multiplayer::default());
        let mut vars = SceneMaxVars::default();
        scenemax_runtime_vm_core::typed::store(&mut vars, "sequence", serde_json::json!(0));
        world.insert_resource(vars);
        crate::value_calls::tests::run_in(
            &mut world,
            r#"
factory() = {
 var local_value = 9
 sequence = sequence + 1
 subject => box
 return subject
 sequence = 99
}
fixtures => Object.Pool(factory, size 2)
"#,
        );
        let vars = world.resource::<SceneMaxVars>();
        assert!(!vars.0.contains_key("local_value"));
        assert_eq!(vars.0["sequence"], 2.);
        let operations = &world.resource::<Multiplayer>().events.operations;
        let writes = operations
            .iter()
            .filter_map(|op| match op {
                Operation::Assign(name, value) if name == "sequence" => value.as_f64(),
                _ => None,
            })
            .collect::<Vec<_>>();
        assert_eq!(writes, vec![1., 2.]);
    }
    #[test]
    fn multiplayer_typed_updates_and_index_assignment_use_shared_vm_values() {
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        let mut net = Multiplayer::default();
        receive(
            &mut world,
            &mut net,
            Incoming::Variable {
                name: "shared".into(),
                initial: false,
                value: serde_json::json!(["original", [1, 2]]),
            },
        );
        let mut vars = world.remove_resource::<SceneMaxVars>().unwrap();
        assert_eq!(
            scenemax_runtime_vm_core::typed::lookup("shared", &vars, None),
            Some(serde_json::json!(["original", [1, 2]]))
        );
        world.insert_resource(net);
        let statement = scenemax_parser::parse_program("shared[0] = \"updated\"")
            .unwrap()
            .statements
            .remove(0);
        assert!(!capture(
            &mut world.commands(),
            &statement,
            &mut vars,
            None,
            &HashMap::new(),
            &HashMap::new()
        ));
        let Statement::Assignment(assignment) = &statement else {
            panic!()
        };
        apply_assignment(assignment, &mut vars, None, &HashMap::new(), None).unwrap();
        assignment_result(&mut world.commands(), &assignment.name, &vars, None);
        world.flush();
        assert!(
            matches!(&world.resource::<Multiplayer>().events.operations[0], Operation::Assign(name,value) if name=="shared" && value==&serde_json::json!(["updated",[1,2]]))
        );
    }

    #[test]
    fn multiplayer_implicit_send_uses_inherited_collision_target_and_never_broadcasts() {
        let mut world = World::new();
        world.insert_resource(Multiplayer::default());
        let statement = scenemax_parser::parse_program("network.send \"contact\"")
            .unwrap()
            .statements
            .remove(0);
        let scope = SceneMaxScopeFrame {
            network_event_target: Some("remote_fixture".into()),
            ..Default::default()
        };
        for scope in [None, Some(&scope)] {
            assert!(capture(
                &mut world.commands(),
                &statement,
                &mut SceneMaxVars::default(),
                scope,
                &HashMap::new(),
                &HashMap::new()
            ));
        }
        world.flush();
        let net = world.resource::<Multiplayer>();
        assert_eq!(net.events.operations.len(), 1);
        assert!(
            matches!(&net.events.operations[0], Operation::Broadcast(_, _, Some(target)) if target == "remote_fixture")
        );
    }

    #[test]
    fn multiplayer_session_transition_clears_unmaterialized_remote_and_data() {
        let (server, peer, mut client) = super::super::joins::tests::connection();
        super::super::joins::tests::accept(&server, peer, &mut client, 1000, "initial");
        let mut world = World::new();
        world.init_resource::<DelayedActionQueue>();
        world.init_resource::<SceneMaxVars>();
        let mut net = Multiplayer {
            client: Some(client),
            ..Default::default()
        };
        remote_spawn(
            &mut world,
            &mut net,
            101,
            Spawn {
                archetype: "box".into(),
                player: "fixture".into(),
                source_name: "authored".into(),
                command: "{network_entity} => box: multiplayer".into(),
            },
        );
        receive(
            &mut world,
            &mut net,
            Incoming::EntityData {
                entity: 101,
                field: "score".into(),
                value: serde_json::json!(5),
            },
        );
        net.events
            .values
            .insert("shared_count".into(), serde_json::json!(3));
        net.events
            .operations
            .push(Operation::Join(Operand::Value(serde_json::json!(1001))));
        flush(&mut world, &mut net);
        assert!(net.remotes.is_empty());
        assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
        assert!(
            !world
                .resource::<SceneMaxVars>()
                .0
                .contains_key("scenemax_remote_101.data.score")
        );
        assert!(
            !net.events
                .values
                .contains_key("scenemax_remote_101.data.score")
        );
        assert_eq!(net.events.values["shared_count"], serde_json::json!(3));
    }
}

pub(super) fn connection_closed(state: &mut EventState) {
    state.operations.clear();
    state.entity_data.clear();
    if state.join.pending() {
        state.join.fail("connection unavailable");
    }
}
