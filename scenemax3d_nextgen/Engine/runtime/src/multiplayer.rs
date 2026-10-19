//! Game-thread adapter for the independent SceneMax UDP client.
use super::*;
use scenemax_multiplayer::{
    client::{Client, State, password_hash},
    protocol::{Incoming, Login, Outgoing, Pose, Spawn},
};
use std::{
    net::ToSocketAddrs,
    time::{Duration, Instant},
};
mod lifecycle;
mod external_config;
pub(super) mod collisions;
mod controllers;
mod local;
pub(super) use local::EffectAction;
pub(super) type ActionLease = std::sync::Arc<()>;
mod events;
pub(super) use events::retire_entity_data;
mod joins;
pub(super) mod widgets;
pub(super) use collisions::expand as collision_events;
mod replication;
mod replay;
#[cfg(test)]
mod tests;
#[cfg(test)]
mod registration_tests;

#[derive(Resource, Default)]
pub(super) struct Multiplayer {
    client: Option<Client>,
    declarations: HashMap<String, (String, EntityOptions)>,
    // Authored selector names survive distinct scoped/replacement runtime identities.
    local_sources: HashMap<String, String>,
    locals: HashMap<String, (Entity, u32)>,
    remotes: HashMap<u32, String>,
    poses: HashMap<u32, Pose>,
    pending: Vec<(String, String, Option<std::sync::Weak<()>>)>,
    entity_updates: Vec<(String, String, serde_json::Value)>,
    remote_pending: Vec<RemoteCommand>,
    replay: replay::ReplayHistory,
    local_sequences: HashMap<u32, u16>,
    local_actions: local::LocalActions,
    rejected_declarations: HashSet<String>,
    last_correction: Option<Instant>,
    disconnected_cleaned: bool,
    player: String,
    events: events::EventState,
    widgets: widgets::WidgetSync,
    owners: HashMap<String, u16>,
    sources: HashMap<u32, String>,
}

struct RemoteCommand {
    entity: u32,
    command: String,
    resume: Option<(u32, u32)>,
    action: Option<(u8, u16)>,
    received_at: Instant,
    snapshot: bool,
}
impl RemoteCommand {
    fn resume_at(&self, now: Instant) -> Option<(u32, u32)> {
        self.resume.map(|(remaining, total)| {
            if remaining == u32::MAX && self.action.is_none_or(|(slot, _)| slot != 3) { return (remaining, total); }
            let elapsed = now.saturating_duration_since(self.received_at).as_millis();
            let elapsed = u32::try_from(elapsed).unwrap_or(u32::MAX);
            (remaining.saturating_sub(elapsed), total)
        })
    }
}

pub(super) fn initialize(app: &mut App, project: Option<&Path>, script: Option<&Path>) {
    let mut network = Multiplayer::default();
    match settings(project, script) {
        Ok(Some((host, port, login))) => {
            write_runtime_diagnostic_line(format!("NETWORK:CONNECT server={host:?} port={port}"));
            network.player = login.player.clone();
            let result = (host.as_str(), port)
                .to_socket_addrs()
                .map_err(|e| e.to_string())
                .and_then(|mut addresses| {
                    addresses
                        .next()
                        .ok_or_else(|| "server has no address".into())
                })
                .and_then(|address| Client::connect(address, login).map_err(|e| e.to_string()));
            match result {
                Ok(client) => {
                    network.client = Some(client);
                    tracing::info!("SceneMax multiplayer connecting");
                }
                Err(error) => {
                    write_runtime_diagnostic_line(format!("NETWORK:CONNECT_FAILED error={error}"));
                    tracing::error!(%error, "SceneMax multiplayer connection failed");
                }
            }
        }
        Ok(None) => {}
        Err(error) => {
            write_runtime_diagnostic_line(format!("NETWORK:CONFIG_ERROR {error}"));
            tracing::error!(%error, "invalid SceneMax multiplayer configuration");
        }
    }
    app.insert_resource(network).add_systems(Last, update);
}

fn settings(
    project: Option<&Path>,
    script: Option<&Path>,
) -> Result<Option<(String, u16, Login)>, String> {
    let defaults = env::var("SCENEMAX_MULTIPLAYER_DEFAULTS").ok();
    let executable = env::var_os("SCENEMAX_DISTRIBUTION_EXE")
        .map(PathBuf::from)
        .map(Ok)
        .unwrap_or_else(env::current_exe)
        .map_err(|error| format!("Cannot locate executable for network.json: {error}"))?;
    external_config::settings(project, script, defaults.as_deref(), &executable, |name| env::var(name).ok())
}

fn configured_settings(
    project: Option<&Path>,
    script: Option<&Path>,
    defaults: Option<&str>,
    environment: impl Fn(&str) -> Option<String>,
) -> Result<Option<(String, u16, Login)>, String> {
    let mut metadata = HashMap::new();
    if let Some(defaults) = defaults {
        if defaults.len() > 16384 { return Err("Multiplayer launch defaults exceed 16 KiB".into()); }
        let values: HashMap<String, String> = serde_json::from_str(defaults)
            .map_err(|_| "Invalid multiplayer launch defaults")?;
        for key in ["multiplayer_server", "multiplayer_port", "project_guid", "multiplayer_session_id", "multiplayer_session_name"] {
            if let Some(value) = values.get(key) { metadata.insert(key.to_owned(), value.clone()); }
        }
    }
    if let Some(root) = project {
        let path = root.join(".scenemax-studio/multiplayer.json");
        if path.exists() {
            use std::io::Read;
            let mut bytes = Vec::new();
            fs::File::open(&path)
                .map_err(|e| e.to_string())?
                .take(1024 * 1024 + 1)
                .read_to_end(&mut bytes)
                .map_err(|e| e.to_string())?;
            if bytes.len() > 1024 * 1024 {
                return Err("Multiplayer settings exceed 1 MiB".into());
            }
            let json: serde_json::Value =
                serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
            for (key, field) in [
                ("multiplayer_server", "server"),
                ("multiplayer_port", "port"),
                ("project_guid", "project_guid"),
                ("multiplayer_session_id", "session_id"),
                ("multiplayer_session_name", "session_name"),
            ] {
                if let Some(value) = json.get(field) {
                    metadata.insert(
                        key.into(),
                        value
                            .as_str()
                            .map(str::to_owned)
                            .unwrap_or_else(|| value.to_string()),
                    );
                }
            }
        }
    }
    if let Some(script) = script {
        use std::io::Read;
        if let Ok(file) = fs::File::open(script) {
            let mut source = String::new();
            let _ = file.take(16384).read_to_string(&mut source);
            for line in source.lines() {
                if let Some((key, value)) = line
                    .trim()
                    .strip_prefix("//$[")
                    .and_then(|s| s.split_once("]="))
                {
                    metadata.insert(key.to_owned(), value.trim_end_matches(';').to_owned());
                }
            }
        }
    }
    let get = |name: &str, key: &str, default: &str| {
        environment(&format!("SCENEMAX_MULTIPLAYER_{name}"))
            .filter(|s| name == "PASSWORD" || !s.trim().is_empty())
            .or_else(|| metadata.get(key).cloned())
            .unwrap_or_else(|| default.into())
    };
    let host = get("SERVER", "multiplayer_server", "");
    if host.is_empty() {
        return Ok(None);
    }
    let port = get("PORT", "multiplayer_port", "9001")
        .parse::<u16>()
        .map_err(|_| "invalid multiplayer port")?;
    if port == 0 {
        return Err("multiplayer port must be nonzero".into());
    }
    let name = project
        .and_then(Path::file_name)
        .and_then(|s| s.to_str())
        .unwrap_or("local");
    let player = env::var("USERNAME")
        .or_else(|_| env::var("USER"))
        .unwrap_or_else(|_| "client".into());
    Ok(Some((
        host,
        port,
        Login {
            password_hash: password_hash(&get("PASSWORD", "multiplayer_password", "")),
            project_guid: get("PROJECT_GUID", "project_guid", ""),
            create_session: get("CREATE_SESSION", "multiplayer_create_session", "false")
                .eq_ignore_ascii_case("true"),
            session_id: get("SESSION_ID", "multiplayer_session_id", "1000")
                .parse()
                .map_err(|_| "invalid multiplayer session ID")?,
            session_name: get("SESSION_NAME", "multiplayer_session_name", name),
            scene: get("SCENE", "multiplayer_scene", "main"),
            player: get(
                "PLAYER",
                "multiplayer_player",
                &format!("{player}-{}", std::process::id()),
            ),
        },
    )))
}

pub(super) fn declaration(
    commands: &mut Commands,
    name: &str,
    resource: &str,
    options: &EntityOptions,
) {
    declaration_with_source(commands, name, None, resource, options);
}

pub(super) fn declaration_with_source(
    commands: &mut Commands,
    name: &str,
    source_name: Option<&str>,
    resource: &str,
    options: &EntityOptions,
) {
    let source_name = source_name.map(str::to_owned);
    if !options.multiplayer {
        return;
    }
    let (name, resource, options) = (name.to_owned(), resource.to_owned(), options.clone());
    commands.queue(move |world: &mut World| {
        let pending_key = format!("{name}.network.registrationPending");
        let mut pending = false;
        if let Some(mut net) = world.get_resource_mut::<Multiplayer>() {
            if net.client.is_some() && net.declarations.len() < 2048 {
                pending = true;
                net.rejected_declarations.remove(&name);
                if let Some(source_name) = source_name {
                    net.local_sources.insert(name.clone(), source_name);
                } else {
                    net.local_sources.entry(name.clone()).or_insert_with(|| name.clone());
                }
                net.declarations.insert(name, (resource, options));
            }
        }
        if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
            scenemax_runtime_vm_core::typed::store(&mut vars, &pending_key, serde_json::json!(if pending { 1 } else { 0 }));
        }
    });
}

/// Resume the declaring script only after its local network identity is usable.
/// The condition is published after deferred creation and released on failure.
pub(super) fn registration_continuation(
    action: &Statement, tail: &[Statement], vars: &SceneMaxVars,
    pools: &SceneMaxObjectPools, scope: Option<&SceneMaxScopeFrame>,
) -> Vec<Statement> {
    let mut continuation = Vec::new();
    if let Statement::ModelDecl { name, options, .. } = action {
        if options.multiplayer {
            let target = scenemax_runtime_vm_core::references::entity_name_with_aliases(name, vars, scope, &pools.aliases);
            write_runtime_diagnostic_line(format!("NETWORK:REGISTRATION_WAIT target={target:?} continuation_actions={}", tail.len()));
            continuation.push(Statement::WaitUntil { condition: Condition::EqualsNumber {
                name: format!("{target}.network.registrationPending"), value: 0.,
            }});
        }
    }
    continuation.extend_from_slice(tail);
    continuation
}

pub(super) fn scene_changed(commands: &mut Commands, scene: &str) {
    let scene = scene.trim().replace('\\', "/").trim_matches('/').to_owned();
    commands.queue(move |world: &mut World| {
        if let Some(mut net) = world.get_resource_mut::<Multiplayer>() {
            if let Some(client) = net.client.as_mut() {
                if let Err(error) = client.join(Outgoing::JoinScene(if scene.is_empty() {
                    "main".into()
                } else {
                    scene
                })) {
                    tracing::warn!(%error,"multiplayer scene join failed");
                }
            }
            net.declarations.clear();
            net.local_sources.clear();
            net.locals.clear();
            net.local_sequences.clear();
            net.local_actions.clear();
            net.remotes.clear();
            net.replay = replay::ReplayHistory::default();
            net.poses.clear();
            net.pending.clear();
            net.entity_updates.clear();
            net.remote_pending.clear();
            net.rejected_declarations.clear();
            net.owners.clear();
            net.sources.clear();
            net.events = events::EventState::default();
            net.widgets = widgets::WidgetSync::default();
        }
        if let Some(mut queue) = world.get_resource_mut::<SceneMaxUiActionQueue>() {
            queue.remote_actions.clear();
        }
    });
}

pub(super) fn material_applied(world: &mut World, entity: Entity, value: String) {
    let Some(target) = world.get::<SceneMaxEntity>(entity).map(|s| s.name.clone()) else { return; };
    if let Some(mut net) = world.get_resource_mut::<Multiplayer>() {
        if !net.remotes.values().any(|name| name == &target) && net.pending.len() < 4096 {
            net.pending.push((target, format!("{{network_entity}}.material = {}", serde_json::Value::String(value)), None));
        }
    }
}

pub(super) fn shader_applied(world: &mut World, entity: Entity, value: String) {
    let Some(target) = world.get::<SceneMaxEntity>(entity).map(|s| s.name.clone()) else {
        return;
    };
    if let Some(mut net) = world.get_resource_mut::<Multiplayer>() {
        if !net.remotes.values().any(|name| name == &target) && net.pending.len() < 4096 {
            net.pending.push((target, format!("{{network_entity}}.shader = {}", serde_json::Value::String(value)), None));
        }
    }
}

pub(super) fn assignment_result(
    commands: &mut Commands,
    name: &str,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
) {
    events::assignment_result(commands, name, vars, scope);
}

pub(super) fn capture(
    commands: &mut Commands,
    action: &Statement,
    vars: &mut SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
) -> Option<ActionLease> {
    capture_timed(commands, action, vars, scope, guards, transforms, None)
}

pub(super) fn capture_timed(
    commands: &mut Commands,
    action: &Statement,
    vars: &mut SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
    continuous: Option<f32>,
) -> Option<ActionLease> {
    // Shader state is captured by the renderer binding after successful application.
    if matches!(action, Statement::SetShader(_)) {
        return None;
    }
    if events::capture(commands, action, vars, scope, guards, transforms) {
        return None;
    }
    let value = |v: &AssignmentValue| {
        resolve_assignment_value_scoped_with_guards(v, vars, scope, guards, Some(transforms), None)
    };
    let encoded = match action {
        Statement::ModelDecl {
            name,
            resource,
            options,
        } => {
            declaration(commands, name, resource, options);
            if options.multiplayer && resource.eq_ignore_ascii_case("label") {
                let text = options.label.as_ref().and_then(|label| label.text.as_ref())
                    .map(|value| crate::labels::text(value, vars, scope, guards, transforms))
                    .unwrap_or_else(|| name.clone());
                Some((name.clone(), format!("{{network_entity}}.text = {}", crate::labels::wire_text(&text))))
            } else {
                return None;
            }
        }
        Statement::Move(a) => {
            value(&a.distance_value)
                .zip(value(&a.duration_value))
                .map(|(d, t)| {
                    let curve = motion_ease::resolve(&a.easing, vars, scope, guards, transforms);
                    let easing = if continuous.is_none() { curve.wire() } else { String::new() };
                    let (d, t) = continuous
                        .map(|dt| (d * curve.step(t.max(0.001), t.max(0.001), if a.direction.is_axis() { dt.min(t.max(0.001)) } else { dt }) / t.max(0.001), dt))
                        .unwrap_or((d, t));
                    let direction = match a.direction {
                        MoveDirection::Forward => "forward",
                        MoveDirection::Backward => "backward",
                        MoveDirection::Left => "left",
                        MoveDirection::Right => "right",
                        MoveDirection::Up => "up",
                        MoveDirection::Down => "down",
                        MoveDirection::AxisX => "x",
                        MoveDirection::AxisY => "y",
                        MoveDirection::AxisZ => "z",
                    };
                    let command = if a.direction.is_axis() {
                        let sign = if d < 0. { "-" } else { "+" };
                        format!("{{network_entity}}.move ({direction} {sign} {}) in {t} seconds{easing} async", d.abs())
                    } else {
                        format!("{{network_entity}}.move {direction} {d} in {t} seconds{easing} async")
                    };
                    (a.target.clone(), command)
                })
        }
        Statement::Turn(a) => {
            value(&a.degrees_value)
                .zip(value(&a.duration_value))
                .map(|(d, t)| {
                    let d = if (continuous.is_some() || a.easing.is_some()) && a.absolute {
                        let target = scope.and_then(|s| s.aliases.get(&a.target)).unwrap_or(&a.target);
                        resolved_turn_degrees(a, d, transforms.get(target).map(|t| t.rotation).unwrap_or(Quat::IDENTITY))
                    } else { d };
                    let curve = motion_ease::resolve(&a.easing, vars, scope, guards, transforms);
                    let easing = if continuous.is_none() { curve.wire() } else { String::new() };
                    let (d, t) = continuous
                        .map(|dt| (d * curve.step(t.max(0.001), t.max(0.001), if a.absolute { dt.min(t.max(0.001)) } else { dt }) / t.max(0.001), dt))
                        .unwrap_or((d, t));
                    let command = if let Some(axis) = a.axis {
                        let axis = match axis {
                            scenemax_parser::RotationAxis::X => "x",
                            scenemax_parser::RotationAxis::Y => "y",
                            scenemax_parser::RotationAxis::Z => "z",
                        };
                        if a.absolute && continuous.is_none() && a.easing.is_none() {
                            format!("{{network_entity}}.rotate to ({axis} {d}) in {t} seconds{easing} async")
                        } else {
                            let sign = if d < 0. { "-" } else { "+" };
                            format!("{{network_entity}}.rotate({axis}{sign}{}) in {t} seconds{easing} async", d.abs())
                        }
                    } else {
                        format!("{{network_entity}}.turn left {d} in {t} seconds{easing} async")
                    };
                    (
                        a.target.clone(),
                        command,
                    )
                })
        }
        Statement::MoveTo(a) if a.easing.is_some() && continuous.is_some() => {
            let target = scope.and_then(|s| s.aliases.get(&a.target)).unwrap_or(&a.target);
            let start = transforms.get(target)?.translation;
            let end = evaluate_move_to_destination_runtime(&a.destination, vars, scope, guards, transforms, None)?;
            let duration = value(&a.duration_value)?.max(0.001);
            let dt = continuous?;
            let curve = motion_ease::resolve(&a.easing, vars, scope, guards, transforms);
            let delta = if matches!(a.destination, MoveToDestination::Delta(_)) { end } else { end - start };
            let end = start + delta * curve.step(duration, duration, dt.min(duration)) / duration;
            Some((a.target.clone(), format!("{{network_entity}}.move to ({},{},{}) in {dt} seconds async", end.x, end.y, end.z)))
        }
        Statement::Animate(a) => value(&a.speed_value)
            .map(|speed| (a.target.clone(), local::animation_command(a, speed))),
        Statement::CharacterMode(a) => Some((
            a.target.clone(),
            format!(
                "{{network_entity}}.switch to character mode{}",
                a.gravity
                    .map(|g| format!(" with gravity {g}"))
                    .unwrap_or_default()
            ),
        )),
        Statement::ClearCharacterMode { target } => Some((
            target.clone(),
            "{network_entity}.clear character mode".into(),
        )),
        Statement::SetMaterial(_) => None,
        _ => replication::encode(action, vars, scope, guards, transforms),
    };
    let Some((target, text)) = encoded else {
        return None;
    };
    let target = scenemax_runtime_vm_core::references::entity_name(&target, vars, scope);
    let lease = ((continuous.is_none() && matches!(action, Statement::Move(_) | Statement::MoveTo(_) | Statement::Turn(_)))
        || matches!(action, Statement::Animate(_) | Statement::EffekseerPlay(_) | Statement::Attach(_)))
        .then(|| std::sync::Arc::new(()));
    let lifetime = lease.as_ref().map(std::sync::Arc::downgrade);
    commands.queue(move |world: &mut World| {
        if let Some(mut net) = world.get_resource_mut::<Multiplayer>() {
            if net.client.is_some() && net.pending.len() < 4096 {
                net.pending.push((target, text, lifetime));
            }
        }
    });
    lease
}

fn update(world: &mut World) {
    let Some(mut net) = world.remove_resource::<Multiplayer>() else {
        return;
    };
    events::flush(world, &mut net);
    local::prune_effects(world);
    if net.client.is_some() {
        update_connected(world, &mut net);
    } else if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
        vars.0.insert("network.ready".into(), 0.);
    }
    events::update_join(&mut net, Instant::now());
    lifecycle::cleanup_disconnected(world, &mut net);
    widgets::flush(world, &mut net);
    controllers::prune(world, &net.replay);
    publish_state(world, &net);
    world.insert_resource(net);
}

fn update_connected(world: &mut World, net: &mut Multiplayer) {
    local::refresh_animation_speeds(world, net);
    let inactive = pool_lifecycle::inactive(world);
    let mut query = world.query::<(Entity, &SceneMaxEntity, &Transform)>();
    let entities = query
        .iter(world)
        .filter(|(_, scene, _)| !inactive.contains(&scene.name))
        .map(|(id, s, t)| (s.name.clone(), (id, *t)))
        .collect::<HashMap<_, _>>();
    let Some(client) = net.client.as_mut() else {
        return;
    };
    if matches!(client.state(), State::Rejected | State::Closed) {
        if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
            vars.0.insert("network.ready".into(), 0.);
        }
        return;
    }
    let removed = net
        .locals
        .iter()
        .filter(|(name, (id, _))| entities.get(*name).is_none_or(|(current, _)| current != id))
        .map(|(n, (_, r))| (n.clone(), *r))
        .collect::<Vec<_>>();
    for (name, request) in removed {
        let _ = client.destroy(request);
        net.locals.remove(&name);
        net.local_sequences.remove(&request);
        net.local_actions.remove(request);
        if !entities.contains_key(&name) && !inactive.contains(&name) {
            net.declarations.remove(&name);
            net.local_sources.remove(&name);
            net.pending.retain(|(target, _, _)| target != &name);
        }
        net.rejected_declarations.remove(&name);
        if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
            vars.0.remove(&format!("{name}.network.id"));
            vars.1.remove(&format!("{name}.network.id"));
        }
    }
    for (name, (resource, options)) in &net.declarations {
        if net.locals.contains_key(name) || net.rejected_declarations.contains(name) {
            continue;
        }
        let Some((entity, transform)) = entities.get(name) else {
            continue;
        };
        let raw = options.multiplayer_source.as_deref().unwrap_or(resource);
        let evaluated = options.scale_value.as_ref().and_then(|_| {
            scenemax_parser::evaluated_entity_scale_source(raw, SceneMaxVec3 {
                x: transform.scale.x, y: transform.scale.y, z: transform.scale.z,
            })
        });
        let raw = evaluated.as_deref().unwrap_or(raw);
        let positioned = if options.position.is_some() || options.position_value.is_some() {
            scenemax_parser::evaluated_entity_position_source(raw, SceneMaxVec3 {
                x: transform.translation.x, y: transform.translation.y, z: transform.translation.z,
            })
        } else { None };
        let raw = positioned.as_deref().unwrap_or(raw);
        let source = world.get::<crate::labels::Label>(*entity)
            .map(|label| crate::labels::spawn_source(label, options, transform.scale))
            .unwrap_or_else(|| format!("{{network_entity}} => {raw}"));
        match client.register(Spawn {
            archetype: resource.clone(),
            player: net.player.clone(),
            source_name: net.local_sources.get(name).unwrap_or(name).clone(),
            command: source,
        }) {
            Ok(request) => {
                net.locals.insert(name.clone(), (*entity, request));
            }
            Err(error) => {
                if !error.is_retryable() { net.rejected_declarations.insert(name.clone()); }
                tracing::warn!(%error,entity=name,"multiplayer registration failed");
            }
        }
    }
    for (name, command, lifetime) in std::mem::take(&mut net.pending) {
        if inactive.contains(&name) { continue; }
        // A controller that already ended while waiting for IDs must not start
        // later on peers. Continuous input has no retained timed controller.
        if lifetime.as_ref().is_some_and(|lease| lease.strong_count() == 0) { continue; }
        if let Some((entity, request)) = net.locals.get(&name) {
            let Some(resolved) = replication::references(&command, "{network_entity:", |name| {
                net.locals
                    .get(name)
                    .and_then(|(_, r)| client.network_id(*r))
                    .map(|id| format!("{{network_entity_id:{id}}}"))
            }) else {
                if net.pending.len() < 4096 {
                    net.pending.push((name, command, lifetime));
                }
                continue;
            };
            let mut command = resolved;
            let animation_duration = if action_record(&command, 0).is_some_and(|action| action.slot == 3) {
                let current = world.get::<CurrentAnimation>(*entity);
                let speed = local::effective_speed(world, *entity);
                if let Some(speed) = speed { command = local::with_speed(&command, speed, false).unwrap_or(command); }
                let duration = local::animation_duration_at_speed(current, lifetime.as_ref(), speed);
                if duration.is_none() || client.network_id(*request).is_none() {
                    if net.pending.len() < 4096 { net.pending.push((name, command, lifetime)); }
                    continue;
                }
                duration
            } else if action_record(&command, 0).is_some_and(|action| action.slot == 4) {
                let duration = local::effect_duration(world, *entity, lifetime.as_ref());
                if duration.is_none() || client.network_id(*request).is_none() {
                    if net.pending.len() < 4096 { net.pending.push((name, command, lifetime)); }
                    continue;
                }
                duration
            } else { None };
            let retry_command = command.clone();
            let mut message = local_command(&mut net.local_sequences, *request, command);
            if let (Some(duration), Outgoing::ActionStart { action, .. }) = (animation_duration, &mut message) {
                action.duration_ms = duration;
            }
            let sent_command = command_for_animation(&message);
            let identity = match &message {
                Outgoing::ActionStart { action, .. } => Some((action.slot, action.sequence)),
                _ => None,
            };
            match client.send(Some(*request), message) {
                Ok(()) => {
                    if let Some((slot, sequence)) = identity {
                        if slot == 3 {
                            if let (Some(command), Some(weak), Some(speed)) = (sent_command, lifetime.clone(), local::effective_speed(world, *entity)) {
                                net.local_actions.animations.insert(*request, (name.clone(), command, weak, speed));
                            }
                        }
                        net.local_actions.started(*request, slot, sequence, lifetime);
                    }
                }
                Err(error) => {
                    if error.is_retryable() && net.pending.len() < 4096 {
                        net.pending.push((name, retry_command, lifetime));
                    }
                    tracing::warn!(%error,"multiplayer command rejected");
                }
            }
        } else if net.declarations.contains_key(&name) && net.pending.len() < 4096 {
            net.pending.push((name, command, lifetime));
        }
    }
    net.local_actions.flush(client);
    for (name, field, value) in std::mem::take(&mut net.entity_updates) {
        if let Some((_, request)) = net.locals.get(&name) {
            if let Err(error) = client.send(
                Some(*request),
                Outgoing::EntityData {
                    entity: 0,
                    field,
                    value,
                },
            ) {
                tracing::warn!(%error,"multiplayer entity data rejected");
            }
        } else if net.declarations.contains_key(&name) && net.entity_updates.len() < 4096 {
            net.entity_updates.push((name, field, value));
        }
    }
    if net
        .last_correction
        .is_none_or(|last| last.elapsed() >= Duration::from_millis(250))
    {
        for (name, (_, request)) in &net.locals {
            if let Some((_, transform)) = entities.get(name) {
                if let Err(error) = client.send(
                    Some(*request),
                    Outgoing::Transform {
                        entity: 0,
                        pose: Pose {
                            position: transform.translation.to_array(),
                            rotation: transform.rotation.to_array(),
                        },
                    },
                ) {
                    tracing::warn!(%error,"multiplayer transform rejected");
                }
            }
        }
        net.last_correction = Some(Instant::now());
    }
    let events = match client.poll() {
        Ok(events) => events,
        Err(error) => {
            write_runtime_diagnostic_line(format!("NETWORK:TRANSPORT_ERROR state={:?} closing={} error={error}", client.state(), !error.is_retryable()));
            tracing::error!(%error,"multiplayer transport failed");
            if !error.is_retryable() { client.close(); }
            if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
                vars.0.insert("network.ready".into(), 0.);
            }
            return;
        }
    };
    let local_client = client.client_id();
    for event in events {
        match event.message {
            Incoming::Accepted {
                client, session, ..
            } => tracing::info!(client, session, "SceneMax multiplayer joined"),
            Incoming::Rejected => {
                write_runtime_diagnostic_line("NETWORK:REJECTED server rejected login".to_owned());
                tracing::error!("SceneMax multiplayer login rejected");
            }
            Incoming::RemoteCreated { entity, spawn } => {
                remote_spawn(world, net, entity, spawn);
                if let Some(name) = net.remotes.get(&entity) {
                    net.owners.insert(name.clone(), event.sender);
                }
            }
            Incoming::Snapshot(records) => {
                for record in records {
                    if record.owner == local_client {
                        continue;
                    }
                    receive_snapshot(world, net, record);
                }
            }
            Incoming::Command { entity, command } => queue_remote(net, entity, command),
            Incoming::ActionStart { entity, action } => {
                receive_action(net, entity, action);
            }
            Incoming::ActionEnd { entity, slot, sequence } => {
                net.replay.end(entity, slot, sequence);
            }
            Incoming::Transform { entity, pose } => {
                if net.poses.len() < 2048 && net.replay.live_pose(entity) {
                    net.poses.insert(entity, pose);
                }
            }
            Incoming::Destroyed(id) => {
                net.replay.destroy(id);
                net.poses.remove(&id);
                net.remote_pending.retain(|command| command.entity != id);
                if let Some(name) = net.remotes.remove(&id) {
                    events::remove_entity(world, net, id, &name);
                    // A create and destroy can arrive in one frame, before the
                    // deferred declaration has materialized an ECS entity.
                    if let Some(mut queue) = world.get_resource_mut::<DelayedActionQueue>() {
                        queue.actions.retain(|entry| {
                            !entry
                                .actions
                                .iter()
                                .any(|a| matches!(a,Statement::ModelDecl {name:n,..} if n==&name))
                        });
                    }
                    if let Some((entity, _)) = entities.get(&name) {
                        let _ = world.despawn(*entity);
                    }
                }
            }
            message => events::receive(world, net, message),
        }
    }
    dispatch_remote(world, net, &entities, Instant::now());
    net.poses.retain(|id, pose| {
        let Some((entity, _)) = net.remotes.get(id).and_then(|name| entities.get(name)) else {
            return true;
        };
        if let Some(mut t) = world.get_mut::<Transform>(*entity) {
            let rotation = Quat::from_array(pose.rotation);
            if rotation.length_squared() > 0.0001 {
                t.rotation = rotation.normalize();
            }
            t.translation = Vec3::from_array(pose.position);
        }
        false
    });
}

fn dispatch_remote(world: &mut World, net: &mut Multiplayer, entities: &HashMap<String, (Entity, Transform)>, now: Instant) {
    let pending = std::mem::take(&mut net.remote_pending);
    let mut blocked_ik = HashSet::new();
    for pending in pending {
        let id = pending.entity;
        let action = pending.action;
        if net.replay.is_destroyed(id)
            || action.is_some_and(|(slot, sequence)| !net.replay.current(id, slot, sequence))
        {
            continue;
        }
        let instant_ik = is_ik_command(&pending.command);
        if instant_ik && blocked_ik.contains(&id) {
            net.remote_pending.push(pending);
            continue;
        }
        let resume = pending.resume_at(now);
        if resume.is_some_and(|(remaining, _)| remaining == 0) {
            if let Some((slot, sequence)) = action { net.replay.end(id, slot, sequence); }
            tracing::debug!(entity = id, "expired multiplayer action while waiting for dependencies");
            continue;
        }
        if net
            .remotes
            .get(&id)
            .and_then(|name| entities.get(name))
            .is_some_and(|(entity, _)| surface_replay_ready(world, *entity, &pending.command))
        {
            let resolved = replication::references(&pending.command, "{network_entity_id:", |reference| {
                let id = reference.parse::<u32>().ok()?;
                net.remotes
                    .get(&id)
                    .filter(|name| entities.contains_key(*name))
                    .cloned()
                    .or_else(|| {
                        net.locals.iter().find_map(|(name, (_, request))| {
                            (entities.contains_key(name)
                                && net.client.as_ref()?.network_id(*request) == Some(id))
                            .then(|| name.clone())
                        })
                    })
            });
            if let Some(command) = resolved {
                let owner = action.map(|(slot, sequence)| SceneMaxControllerKey::NetworkAction { entity: id, slot, sequence });
                replay_remote_command_owned(world, net, id, &command, resume, owner, pending.snapshot);
            } else if net.remote_pending.len() < 4096 {
                if instant_ik { blocked_ik.insert(id); }
                net.remote_pending.push(pending);
            }
        } else if net.remote_pending.len() < 4096 {
            if instant_ik { blocked_ik.insert(id); }
            net.remote_pending.push(pending);
        }
    }
}

fn is_ik_command(command: &str) -> bool {
    replication::references(command, "{network_entity}", |_| Some("network_target".into()))
        .and_then(|source| scenemax_parser::parse_program(&source).ok())
        .is_some_and(|program| matches!(program.statements.as_slice(), [Statement::Ik(_)]))
}

fn surface_replay_ready(world: &World, entity: Entity, command: &str) -> bool {
    if world.get::<SceneMaxGltf>(entity).is_none() { return true; }
    let Some(source) = replication::references(command, "{network_entity}", |_| Some("network_target".into())) else { return true; };
    let Ok(program) = scenemax_parser::parse_program(&source) else { return true; };
    if !program.statements.iter().any(|statement| matches!(statement, Statement::SetShader(_) | Statement::SetMaterial(_))) {
        return true;
    }
    // Root creation precedes asynchronous glTF descendants. Preserve queued
    // surface history until all scene meshes exist, including slot names.
    crate::shader::entity_scene_ready(world, entity)
}

fn queue_remote(net: &mut Multiplayer, id: u32, command: String) {
    if !net.replay.is_destroyed(id) && net.remote_pending.len() < 4096 {
        net.remote_pending.push(RemoteCommand { entity: id, command, resume: None, action: None, received_at: Instant::now(), snapshot: false });
    }
}

fn receive_action(net: &mut Multiplayer, id: u32, action: scenemax_multiplayer::protocol::Action) {
    // Live starts and snapshots share the same identity. Count time waiting for
    // dependencies for finite live actions, just as for snapshot replay.
    let resume = Some((action.duration_ms, action.duration_ms));
    queue_action(net, id, action, resume);
}

fn queue_action(net: &mut Multiplayer, id: u32, action: scenemax_multiplayer::protocol::Action, resume: Option<(u32, u32)>) {
    queue_action_with_origin(net, id, action, resume, false);
}

fn queue_action_with_origin(net: &mut Multiplayer, id: u32, action: scenemax_multiplayer::protocol::Action, resume: Option<(u32, u32)>, snapshot: bool) {
    if resume.is_some_and(|(remaining, _)| remaining == 0) {
        net.replay.end(id, action.slot, action.sequence);
        return;
    }
    if action.command.trim().is_empty() || net.remote_pending.len() >= 4096 {
        return;
    }
    if net.replay.start(id, action.slot, action.sequence) {
        // IK commands mutate independent layer properties immediately. The slot
        // watermark rejects stale/duplicate arrivals, but a later property must
        // not cancel an already accepted target, blend, apply or remove command.
        let lifetime = (!is_ik_command(&action.command)).then_some((action.slot, action.sequence));
        net.remote_pending.push(RemoteCommand {
            entity: id, command: action.command, resume,
            action: lifetime,
            received_at: Instant::now(),
            snapshot,
        });
    } else {
        tracing::debug!(entity = id, slot = action.slot, sequence = action.sequence,
            "ignored duplicate or stale multiplayer action");
    }
}

fn receive_snapshot(world: &mut World, net: &mut Multiplayer, record: scenemax_multiplayer::protocol::SnapshotEntity) {
    if net.replay.is_destroyed(record.id) { return }
    remote_spawn(world, net, record.id, record.spawn);
    let Some(name) = net.remotes.get(&record.id) else { return };
    net.owners.insert(name.clone(), record.owner);
    if net.replay.snapshot_pose(record.id) {
        net.poses.insert(record.id, record.pose);
    }
    for action in record.actions {
        let resume = Some((action.remaining_ms, action.action.duration_ms));
        queue_action_with_origin(net, record.id, action.action, resume, true);
    }
}

fn action_record(source: &str, sequence: u16) -> Option<scenemax_multiplayer::protocol::Action> {
    let p = scenemax_parser::parse_program(&replication::references(source, "{network_entity}", |_| Some("network_target".into()))?)
        .ok()?;
    let (slot, seconds) = match p.statements.first()? {
        Statement::Move(a) => (1, Some(a.duration_seconds)),
        Statement::Turn(a) => (2, Some(a.duration_seconds)),
        Statement::MoveTo(a) => (1, Some(a.duration_seconds)),
        Statement::Animate(_) => (3, None),
        Statement::EffekseerPlay(_) => (4, None),
        Statement::CharacterMode(_) | Statement::ClearCharacterMode { .. } => (253, None),
        Statement::SetShader(_) => (255, None),
        Statement::SetMaterial(_) => (251, None),
        Statement::Position(_) => (252, None),
        Statement::LabelText { .. } => (254, None),
        Statement::Attach(_) => (64, None),
        Statement::Ik(a) => {
            let slot = a
                .layer
                .as_ref()
                .map(|name| {
                    let hash = name.encode_utf16().fold(0i32, |hash, c| {
                        hash.wrapping_mul(31).wrapping_add(i32::from(c))
                    });
                    (66 + hash.rem_euclid(180)) as u8
                })
                .unwrap_or(65);
            (slot, None)
        }
        _ => return None,
    };
    let duration_ms = seconds
        .map(|s| (s * 1000.).max(1.) as u32)
        .unwrap_or(u32::MAX);
    Some(scenemax_multiplayer::protocol::Action {
        slot,
        sequence,
        duration_ms,
        command: source.into(),
    })
}

fn local_action_record(
    sequences: &mut HashMap<u32, u16>,
    request: u32,
    source: &str,
) -> Option<scenemax_multiplayer::protocol::Action> {
    let mut action = action_record(source, 0)?;
    // Assign action sequences per registered entity. Unnumbered commands
    // and other entities must not exhaust this entity's serial-number window.
    let next = sequences.entry(request).or_insert(1);
    action.sequence = *next;
    *next = next.wrapping_add(1);
    Some(action)
}

fn local_command(sequences: &mut HashMap<u32, u16>, request: u32, command: String) -> Outgoing {
    // Surface assignments share the publication queue so same-frame writes keep
    // their execution order. Shaders retain the existing entity-data wire form.
    if let Some(source) = replication::references(&command, "{network_entity}", |_| Some("network_target".into())) {
        if let Ok(program) = scenemax_parser::parse_program(&source) {
            if let [Statement::SetShader(shader)] = program.statements.as_slice() {
                if let AssignmentValue::Literal(value) = &shader.shader {
                    return Outgoing::EntityData { entity: 0, field: "shader".into(), value: value.clone() };
                }
            }
        }
    }
    // One execution message only: a separate unnumbered copy would run twice
    // and could not be matched safely to an end or a later snapshot.
    match local_action_record(sequences, request, &command) {
        Some(action) => Outgoing::ActionStart { entity: 0, action },
        None => Outgoing::Command { entity: 0, command },
    }
}

fn remote_spawn(world: &mut World, net: &mut Multiplayer, id: u32, spawn: Spawn) {
    if net.replay.is_destroyed(id) || net.remotes.contains_key(&id) || net.remotes.len() >= 2048 {
        return;
    }
    let name = format!("scenemax_remote_{id}");
    if net.declarations.contains_key(&name)
        || world
            .query::<&SceneMaxEntity>()
            .iter(world)
            .any(|entity| entity.name == name)
    {
        tracing::warn!(id, "remote entity name conflicts with existing scene data");
        return;
    }
    let Some(source) = replication::references(&spawn.command, "{network_entity}", |_| Some(name.clone())) else { return; };
    match scenemax_parser::parse_program(&source) {
        Ok(mut program) => {
            // The wire's spawn field may only declare this one entity. It must
            // never execute arbitrary includes, scene changes, or extra actions.
            if program.statements.len() != 1 || crate::value_calls::action_contains(&program.statements[0]) {
                tracing::warn!(id, "invalid remote spawn");
                return;
            }
            let Some(Statement::ModelDecl {
                name: declared,
                options,
                ..
            }) = program.statements.first_mut()
            else {
                return;
            };
            if *declared != name {
                return;
            }
            options.multiplayer = false;
            options.multiplayer_source = None;
            net.remotes.insert(id, name);
            net.sources.insert(id, spawn.source_name);
            enqueue(world, program.statements);
        }
        Err(error) => tracing::warn!(%error,id,"remote spawn parse failed"),
    }
}

#[cfg(test)]
fn remote_command(
    world: &mut World,
    net: &Multiplayer,
    id: u32,
    source: &str,
    resume: Option<(u32, u32)>,
) {
    remote_command_owned(world, net, id, source, resume, None);
}

#[cfg(test)]
fn remote_command_owned(world: &mut World, net: &Multiplayer, id: u32, source: &str,
    resume: Option<(u32, u32)>, owner: Option<SceneMaxControllerKey>) {
    replay_remote_command_owned(world, net, id, source, resume, owner, false);
}

fn replay_remote_command_owned(world: &mut World, net: &Multiplayer, id: u32, source: &str,
    resume: Option<(u32, u32)>, owner: Option<SceneMaxControllerKey>, snapshot: bool) {
    let Some(name) = net.remotes.get(&id) else {
        return;
    };
    let Some(source) = replication::references(source, "{network_entity}", |_| Some(name.clone())) else { return; };
    if let Ok(mut program) = scenemax_parser::parse_program(&source) {
        // Restrict replay to the replicated entity, with no user input, includes,
        // functions, or local game-control statements accepted from UDP.
        let valid = program.statements.iter().all(|s| match s {
            Statement::Move(a) => a.target == *name,
            Statement::Turn(a) => a.target == *name,
            Statement::MoveTo(a) => a.target == *name,
            Statement::Position(a) => a.target == *name,
            Statement::RotationReset { target, .. } => target == name,
            Statement::Animate(a) => a.target == *name,
            Statement::EffekseerPlay(a) => a.target == *name,
            Statement::CharacterMode(a) => a.target == *name,
            Statement::ClearCharacterMode { target } => target == name,
            Statement::SetShader(a) => a.target == *name,
            Statement::SetMaterial(a) => a.target == *name,
            Statement::Ik(a) => a.owner == *name,
            Statement::LabelText { target, .. } => target == name,
            Statement::Attach(a) => crate::ik::split_target(&a.target).0 == name,
            _ => false,
        });
        if valid {
            if snapshot {
                for action in &mut program.statements {
                    if let Statement::Attach(attach) = action { attach.restore_destination_node = false; }
                }
            }
            if program.statements.iter().any(|action| matches!(action, Statement::EffekseerPlay(_))) {
                if let (Some(owner), Some((remaining, _))) = (owner.clone(), resume.filter(|(r, _)| *r != u32::MAX)) {
                    let entity = world.query::<(Entity, &SceneMaxEntity)>().iter(world)
                        .find_map(|(entity, scene)| (scene.name == *name).then_some(entity));
                    if let Some(entity) = entity {
                        world.entity_mut(entity).insert(NetworkEffectResume(NetworkAnimationResume {
                            owner, remaining_seconds: remaining as f64 / 1000., received_at: Instant::now(),
                        }));
                    }
                }
            }
            if program.statements.iter().any(|action| matches!(action, Statement::Animate(_))) {
                if let (Some(owner), Some((remaining, _))) = (owner.clone(), resume) {
                    let entity = world.query::<(Entity, &SceneMaxEntity)>().iter(world)
                        .find_map(|(entity, scene)| (scene.name == *name).then_some(entity));
                    if let Some(entity) = entity {
                        world.entity_mut(entity).insert(NetworkAnimationResume {
                            owner, remaining_seconds: remaining as f64 / 1000., received_at: Instant::now(),
                        });
                    }
                }
            }
            if let Some((remaining, total)) =
                resume.filter(|(r, t)| *r != u32::MAX && *t != u32::MAX && *t > 0)
            {
                let fraction = (remaining as f32 / total as f32).clamp(0., 1.);
                let seconds = remaining as f32 / 1000.;
                for action in &mut program.statements {
                    match action {
                        Statement::Move(a) => {
                            if let Some(ease) = &mut a.easing { ease.start_progress = 1. - fraction; }
                            else { a.distance *= fraction; }
                            a.distance_value = AssignmentValue::Number(a.distance);
                            a.duration_seconds = seconds;
                            a.duration_value = AssignmentValue::Number(seconds);
                        }
                        Statement::Turn(a) => {
                            if let Some(ease) = &mut a.easing { ease.start_progress = 1. - fraction; }
                            else if !a.absolute { a.degrees *= fraction; }
                            a.degrees_value = AssignmentValue::Number(a.degrees);
                            a.duration_seconds = seconds;
                            a.duration_value = AssignmentValue::Number(seconds);
                        }
                        Statement::MoveTo(a) => {
                            if let Some(ease) = &mut a.easing { ease.start_progress = 1. - fraction; }
                            else if let MoveToDestination::Delta(PositionValue::Coordinates(values)) = &mut a.destination {
                                // Wire displacements are evaluated numeric expressions.
                                for value in values { if let PositionExpr::Number(v) = value { *v *= fraction; } }
                            }
                            a.duration_seconds = seconds;
                            a.duration_value = AssignmentValue::Number(seconds);
                        }
                        _ => {}
                    }
                }
            }
            enqueue_owned(world, program.statements, owner);
        }
    }
}

fn enqueue(world: &mut World, actions: Vec<Statement>) {
    enqueue_owned(world, actions, None);
}
fn enqueue_owned(world: &mut World, actions: Vec<Statement>, owner: Option<SceneMaxControllerKey>) {
    enqueue_scoped(world, actions, owner, None);
}
fn enqueue_scoped(world: &mut World, actions: Vec<Statement>, owner: Option<SceneMaxControllerKey>, scope: Option<SceneMaxScopeFrame>) {
    if let Some(mut queue) = world.get_resource_mut::<DelayedActionQueue>() {
        if queue.actions.len() < 4096 {
            queue.actions.push(DelayedActions { motion_wait: None, controller_guard: None,
                remaining_seconds: 0.,
                animation_wait: None,
                actions,
                owner,
                scope,
            });
        }
    }
}

fn publish_state(world: &mut World, net: &Multiplayer) {
    if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
        scenemax_runtime_vm_core::typed::store(&mut vars, "network.joinPending", serde_json::json!(if events::join_pending(&net.events) { 1 } else { 0 }));
        scenemax_runtime_vm_core::typed::store(&mut vars, "network.joinError", serde_json::json!(events::join_error(&net.events)));
    }
    if let Some(client) = net.client.as_ref() {
        if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
            let ready = client.state() == State::Ready && !events::join_pending(&net.events);
            let mut sessions: Vec<_> = client.sessions().map(|s| serde_json::json!({"id":s.id,"name":s.name,"clients":s.clients,"connectedClients":s.clients})).collect();
            if client.session_id() != 0
                && !sessions
                    .iter()
                    .any(|s| s["id"].as_u64() == Some(u64::from(client.session_id())))
            {
                sessions.push(serde_json::json!({"id":client.session_id(),"name":client.session_name(),"clients":1,"connectedClients":1}));
            }
            scenemax_runtime_vm_core::typed::store(
                &mut vars,
                "network.state",
                serde_json::json!({"ready":ready,"sessionId":client.session_id(),"sessionName":client.session_name(),"sessions":sessions,"unconfirmedActionEnds":client.expired_action_ends()}),
            );
            scenemax_runtime_vm_core::typed::store(
                &mut vars,
                "network.state.sessionId",
                serde_json::json!(client.session_id()),
            );
            vars.0.insert(
                "network.ready".into(),
                if ready {
                    1.
                } else {
                    0.
                },
            );
            vars.0
                .insert("network.state.sessionId".into(), client.session_id() as f32);
            vars.0.insert(
                "network.state.clientId".into(),
                f32::from(client.client_id()),
            );
            for name in net.declarations.keys() {
                let id = net
                    .locals
                    .get(name)
                    .and_then(|(_, request)| client.network_id(*request))
                    .unwrap_or(0);
                scenemax_runtime_vm_core::typed::store(
                    &mut vars,
                    &format!("{name}.network.id"),
                    serde_json::json!(id),
                );
                let pending = id == 0 && !net.rejected_declarations.contains(name)
                    && !matches!(client.state(), State::Closed | State::Rejected);
                let key = format!("{name}.network.registrationPending");
                let previous = vars.0.get(&key).copied();
                if previous != Some(if pending { 1. } else { 0. }) {
                    write_runtime_diagnostic_line(format!("NETWORK:REGISTRATION target={name:?} id={id} pending={pending} rejected={} state={:?}",
                        net.rejected_declarations.contains(name), client.state()));
                }
                scenemax_runtime_vm_core::typed::store(&mut vars,
                    &format!("{name}.network.registrationPending"), serde_json::json!(if pending { 1 } else { 0 }));
            }
            for (id, name) in &net.remotes {
                scenemax_runtime_vm_core::typed::store(
                    &mut vars,
                    &format!("{name}.network.id"),
                    serde_json::json!(id),
                );
            }
        }
    }

    if net.client.is_none() {
        if let Some(mut vars) = world.get_resource_mut::<SceneMaxVars>() {
            scenemax_runtime_vm_core::typed::store(
                &mut vars,
                "network.state",
                serde_json::json!({"ready": false, "sessionId":0, "sessionName":"", "sessions":[]}),
            );
        }
    }
}

fn command_for_animation(message: &Outgoing) -> Option<String> {
    match message { Outgoing::ActionStart { action, .. } if action.slot == 3 => Some(action.command.clone()), _ => None }
}
