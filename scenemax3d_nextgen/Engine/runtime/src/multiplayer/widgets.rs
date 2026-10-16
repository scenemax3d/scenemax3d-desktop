//! Bounded, typed widget state over the existing Zig shared-variable channel.
//! Wire keys contain a stable path digest; packets cannot execute script source.
use super::*;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};

const LIMIT: usize = 2048;
const SLOTS: [&str; 12] = [
    "text",
    "visible",
    "frame",
    "textcolor",
    "backgroundcolor",
    "fontsize",
    "font",
    "image_state",
    "surface_shader",
    "image_shader",
    "list_state",
    "edit_state",
];

#[derive(Default)]
pub(super) struct WidgetSync {
    pending: HashMap<String, Value>,
    received: HashMap<String, Received>,
    sequence: u32,
}

struct Received {
    value: Value,
    applied: Option<Entity>,
    received_at: std::time::Instant,
}

fn key(path: &str, slot: &str) -> String {
    let digest = format!("{:x}", Sha256::digest(path.as_bytes()));
    format!("$ui:{}:{slot}", &digest[..32])
}

// Control state can exceed one legacy shared-value packet. The manifest and
// content-addressed pieces keep a snapshot coherent across reordered delivery.
fn control_piece_slot(slot: &str) -> bool {
    ["list_state_", "edit_state_"].iter().any(|prefix| slot.strip_prefix(prefix)
        .is_some_and(|n| n.parse::<usize>().is_ok_and(|n| n < 128)))
}
fn valid_control_packet(slot: &str, value: &Value) -> bool {
    let Some(a) = value.as_array().filter(|a| a.len()==2) else { return false; };
    let token = a[0].as_str().is_some_and(|s| s.len()==16 && s.bytes().all(|c|c.is_ascii_hexdigit()));
    token && if matches!(slot,"list_state"|"edit_state") {
        a[1].as_u64().is_some_and(|n| (1..=128).contains(&n))
    } else { control_piece_slot(slot) && a[1].is_string() }
}
fn publish_control(world: &mut World, entity: Entity, path: &str, slot: &str, raw: String) {
    let token = format!("{:x}", Sha256::digest(raw.as_bytes()))[..16].to_owned();
    let mut parts = Vec::new(); let mut part = String::new();
    for c in raw.chars() {
        let mut next = part.clone(); next.push(c);
        if scenemax_multiplayer::protocol::encode_value(&json!([token,next])).is_err() {
            parts.push(std::mem::take(&mut part));
        }
        part.push(c);
    }
    if !part.is_empty() { parts.push(part); }
    if parts.len()>128 { tracing::warn!(path,"UI control snapshot exceeds bounded transport capacity"); return; }
    for (i,part) in parts.iter().enumerate() { publish_value(world,entity,path,&format!("{slot}_{i}"),json!([token,part])); }
    publish_value(world,entity,path,slot,json!([token,parts.len()]));
}
fn assemble_control(state:&WidgetSync,path:&str,slot:&str)->Option<Value> {
    if !matches!(slot,"list_state"|"edit_state") {return None;}
    let manifest=&state.received.get(&key(path,slot))?.value;
    if !valid_control_packet(slot,manifest) {return None;}
    let token=manifest[0].as_str()?; let count=manifest[1].as_u64()? as usize;
    let mut raw=String::new();
    for i in 0..count {
        let piece=&state.received.get(&key(path,&format!("{slot}_{i}")))?.value;
        if piece[0].as_str()?!=token {return None;}
        raw.push_str(piece[1].as_str()?);
    }
    if !format!("{:x}",Sha256::digest(raw.as_bytes())).starts_with(token) {return None;}
    Some(Value::String(raw))
}

#[cfg(test)]
mod control_tests {
    use super::*;
    #[test]
    fn control_parts_wait_for_complete_matching_snapshot_in_any_arrival_order() {
        let (_server, _peer, client) = super::super::joins::tests::connection();
        let mut world=World::new(); let entity=world.spawn_empty().id();
        world.insert_resource(Multiplayer { client:Some(client), ..Default::default() });
        let raw="Unicode λ🙂 and quoted \"text\". ".repeat(40);
        publish_control(&mut world,entity,"fixture.layer.table","list_state",raw.clone());
        let outgoing=world.resource::<Multiplayer>().widgets.pending.clone();
        assert!(outgoing.len()>2);
        let mut received=WidgetSync::default();
        let manifest=key("fixture.layer.table","list_state");
        receive(&mut received,manifest.clone(),outgoing[&manifest].clone());
        assert!(assemble_control(&received,"fixture.layer.table","list_state").is_none());
        for (key,value) in outgoing.iter().filter(|(key,_)| *key!=&manifest) {
            assert!(scenemax_multiplayer::protocol::encode_value(value).is_ok());
            receive(&mut received,key.clone(),value.clone());
        }
        assert_eq!(assemble_control(&received,"fixture.layer.table","list_state"),Some(Value::String(raw)));
        let piece=key("fixture.layer.table","list_state_0");
        let mut stale=outgoing[&piece].clone(); stale[0]=json!("0000000000000000");
        receive(&mut received,piece,stale);
        assert!(assemble_control(&received,"fixture.layer.table","list_state").is_none());
    }
}

fn slot(name: &str) -> Option<&str> {
    let rest = name.strip_prefix("$ui:")?;
    let (digest, slot) = rest.split_once(':')?;
    (digest.len() == 32 && digest.bytes().all(|c| c.is_ascii_hexdigit()) && (SLOTS.contains(&slot) || control_piece_slot(slot)))
        .then_some(slot)
}

pub(crate) fn action_target(action: &SceneMaxUiAction) -> Option<&UiTargetPath> {
    match action {
        SceneMaxUiAction::SetProperty { target, .. }
        | SceneMaxUiAction::ImageState { target, .. }
        | SceneMaxUiAction::SurfaceShader { target, .. }
        | SceneMaxUiAction::ImageShader { target, .. }
        | SceneMaxUiAction::Message { target, .. }
        | SceneMaxUiAction::ShowHide { target, .. }
        | SceneMaxUiAction::Ease { target, .. } => Some(target),
        _ => None,
    }
}

pub(in crate::multiplayer) fn receive(state: &mut WidgetSync, name: String, value: Value) {
    let Some(slot) = slot(&name) else {
        return;
    };
    if !valid_control_packet(slot, &value) && decode(slot, &value, dummy_target()).is_none()
        || scenemax_multiplayer::protocol::encode_value(&value).is_err()
    {
        return;
    }
    if let Some(previous) = state.received.get(&name) {
        if previous.value == value {
            return;
        }
        // Numbered easing updates from the same client cannot rewind each other.
        if slot == "visible" && let (Some(old), Some(new)) = (previous.value.as_array(), value.as_array()) {
            if old[0] == new[0] {
                let old = old[1].as_u64().unwrap_or(0) as u32;
                let new = new[1].as_u64().unwrap_or(0) as u32;
                if new.wrapping_sub(old) >= (1 << 31) {
                    return;
                }
            }
        }
    }
    if state.received.len() < LIMIT || state.received.contains_key(&name) {
        state.received.insert(
            name,
            Received {
                value,
                applied: None,
                received_at: std::time::Instant::now(),
            },
        );
    }
}

fn dummy_target() -> UiTargetPath {
    UiTargetPath {
        ui_name: None,
        layer: String::new(),
        widget_path: Vec::new(),
    }
}

fn decode(slot: &str, value: &Value, target: UiTargetPath) -> Option<SceneMaxUiAction> {
    match slot {
        "edit_state" => {
            let raw = value.as_str()?;
            let state: crate::ui::edits::Snapshot = serde_json::from_str(raw).ok()?;
            state.valid().then(|| SceneMaxUiAction::SetProperty { target, property: "__edit_snapshot".into(), value: raw.into() })
        }
        "list_state" => {
            let raw = value.as_str()?;
            let state: crate::ui::lists::Snapshot = serde_json::from_str(raw).ok()?;
            state.valid().then(|| SceneMaxUiAction::SetProperty { target, property: "__list_snapshot".into(), value: raw.into() })
        }
        "image_shader" => {
            type Source = (Option<String>, Option<String>, usize);
            let (original, current, restored, position, shader): (Source, Source, bool, Option<usize>, Option<String>) = serde_json::from_value(value.clone()).ok()?;
            let state = crate::ui::image_shaders::Snapshot {
                original: crate::ui::images::Snapshot { image: original.0, sprite: original.1, frame: original.2 },
                current: crate::ui::images::Snapshot { image: current.0, sprite: current.1, frame: current.2 },
                restored, position, shader,
            };
            state.valid().then_some(SceneMaxUiAction::ImageShader { target, state })
        }
        "surface_shader" => {
            let (name, captured, original, current): (String, bool, Option<[f32;4]>, Option<[f32;4]>) = serde_json::from_value(value.clone()).ok()?;
            let state = crate::ui::shaders::Snapshot { name, captured, original, current };
            state.valid().then_some(SceneMaxUiAction::SurfaceShader { target, state })
        }
        "image_state" => {
            let (image, sprite, frame): (Option<String>, Option<String>, usize) = serde_json::from_value(value.clone()).ok()?;
            let state = crate::ui::images::Snapshot { image, sprite, frame };
            state.valid().then_some(SceneMaxUiAction::ImageState { target, state })
        }
        "text" | "frame" | "textcolor" | "backgroundcolor" | "fontsize" | "font" => {
            let value = value.as_str()?;
            if crate::ui::properties::is_style(slot) && !crate::ui::properties::valid(slot, value) {
                return None;
            }
            if slot == "frame" && parse_ui_frame_value(value).is_none() {
                return None;
            }
            Some(SceneMaxUiAction::SetProperty {
                target,
                property: slot.into(),
                value: value.into(),
            })
        }
        "visible" if value.is_boolean() => Some(SceneMaxUiAction::ShowHide {
            target,
            visible: value.as_bool()?,
        }),
        "visible" => {
            let a = value.as_array()?;
            if a.len() != 5 || a[0].as_u64()? > u16::MAX.into() || a[1].as_u64()? > u32::MAX.into()
            {
                return None;
            }
            let easing = a[2].as_str()?;
            let direction = match a[3].as_str()? {
                "up" => UiEaseDirection::Up,
                "down" => UiEaseDirection::Down,
                "left" => UiEaseDirection::Left,
                "right" => UiEaseDirection::Right,
                _ => return None,
            };
            let duration = a[4].as_f64()?;
            if !duration.is_finite() || !(0.0..=86400.0).contains(&duration) {
                return None;
            }
            Some(SceneMaxUiAction::Ease {
                received_at: None,
                target,
                easing: easing.into(),
                direction,
                duration_seconds: duration as f32,
            })
        }
        _ => None,
    }
}

pub(crate) fn capture(
    commands: &mut Commands,
    runtime: &SceneMaxUiRuntime,
    action: &SceneMaxUiAction,
) {
    let Some(target) = action_target(action) else {
        return;
    };
    let Some(widget) = resolve_ui_target(runtime, target).filter(|w| w.multiplayer) else {
        return;
    };
    let Some(path) = resolve_ui_target_path(runtime, target) else {
        return;
    };
    if widget.widget_type == "EDIT_TEXT" { return; }
    let entity = widget.entity;
    let mut action = action.clone();
    if let SceneMaxUiAction::SetProperty {
        property, value, ..
    } = &mut action
    {
        let Some(canonical) = crate::ui::properties::canonical(widget, property) else {
            return;
        };
        *property = canonical.into();
        // Actual image widgets publish the coupled state after applying the command.
        if matches!(widget.widget_type.as_str(), "IMAGE" | "PANEL") && matches!(canonical, "image" | "sprite" | "frame") {
            return;
        }
        if crate::ui::properties::is_style(property)
            && !crate::ui::properties::valid(property, value)
        {
            return;
        }
    }
    commands.queue(move |world: &mut World| {
        if matches!(&action, SceneMaxUiAction::SetProperty { property, .. } if property == "backgroundcolor")
            && crate::ui::shaders::snapshot(world, entity).is_some() { return; }
        let Some(mut net) = world.get_resource_mut::<Multiplayer>() else {
            return;
        };
        let Some(client) = net.client.as_ref() else {
            return;
        };
        if matches!(client.state(), State::Closed | State::Rejected) {
            return;
        }
        let sender = client.client_id();
        let (slot, value) = match action {
            SceneMaxUiAction::Message { text, .. } => ("text", Value::String(text)),
            SceneMaxUiAction::SetProperty {
                property, value, ..
            } => match property.to_ascii_lowercase().as_str() {
                "text" => ("text", Value::String(value)),
                "textcolor" => ("textcolor", Value::String(value)),
                "backgroundcolor" => ("backgroundcolor", Value::String(value)),
                "fontsize" => ("fontsize", Value::String(value)),
                "font" => ("font", Value::String(value)),
                "frame" if parse_ui_frame_value(&value).is_some() => {
                    ("frame", Value::String(value))
                }
                "visible" => (
                    "visible",
                    Value::Bool(!matches!(
                        value.to_ascii_lowercase().as_str(),
                        "0" | "false"
                    )),
                ),
                _ => return,
            },
            SceneMaxUiAction::ShowHide { visible, .. } => ("visible", Value::Bool(visible)),
            SceneMaxUiAction::Ease {
                easing,
                direction,
                duration_seconds,
                ..
            } => {
                if !duration_seconds.is_finite() || !(0.0..=86400.0).contains(&duration_seconds) {
                    return;
                }
                net.widgets.sequence = net.widgets.sequence.wrapping_add(1);
                let direction = match direction {
                    UiEaseDirection::Up => "up",
                    UiEaseDirection::Down => "down",
                    UiEaseDirection::Left => "left",
                    UiEaseDirection::Right => "right",
                };
                (
                    "visible",
                    json!([
                        sender,
                        net.widgets.sequence,
                        easing,
                        direction,
                        duration_seconds
                    ]),
                )
            }
            _ => return,
        };
        if let Err(error) = scenemax_multiplayer::protocol::encode_value(&value) {
            tracing::warn!(%error, "widget update exceeds multiplayer transport limits");
            return;
        }
        let name = key(&path, slot);
        if net.widgets.pending.len() < LIMIT || net.widgets.pending.contains_key(&name) {
            net.widgets.pending.insert(name.clone(), value.clone());
            // The sender's own echo must not restart message/ease animations.
            if net.widgets.received.len() < LIMIT || net.widgets.received.contains_key(&name) {
                net.widgets.received.insert(
                    name,
                    Received {
                        value,
                        applied: Some(entity),
                        received_at: std::time::Instant::now(),
                    },
                );
            }
        }
    });
}

pub(crate) fn capture_edit(world: &mut World, entity: Entity, path: &str) {
    let Some(state) = crate::ui::edits::snapshot(world, entity) else { return; };
    let Ok(value) = serde_json::to_string(&state) else { return; };
    publish_control(world, entity, path, "edit_state", value);
}

pub(crate) fn capture_list(world: &mut World, entity: Entity, path: &str) {
    let Some(state) = crate::ui::lists::snapshot(world, entity) else { return; };
    let Ok(value) = serde_json::to_string(&state) else { return; };
    publish_control(world, entity, path, "list_state", value);
}

pub(crate) fn capture_image(world: &mut World, entity: Entity, path: &str) {
    if crate::ui::image_shaders::snapshot(world, entity).is_some() {
        capture_image_shader(world, entity, path);
        return;
    }
    let Some(state) = crate::ui::images::snapshot(world, entity) else { return; };
    let value = json!([state.image, state.sprite, state.frame]);
    publish_value(world, entity, path, "image_state", value);
}

pub(crate) fn capture_image_shader(world: &mut World, entity: Entity, path: &str) {
    let Some(state) = crate::ui::image_shaders::snapshot(world, entity) else { return; };
    let value = json!([
        [state.original.image, state.original.sprite, state.original.frame],
        [state.current.image, state.current.sprite, state.current.frame],
        state.restored, state.position, state.shader,
    ]);
    publish_value(world, entity, path, "image_shader", value);
}

pub(crate) fn capture_shader(world: &mut World, entity: Entity, path: &str) {
    let Some(state) = crate::ui::shaders::snapshot(world, entity) else { return; };
    let value = json!([state.name, state.captured, state.original, state.current]);
    publish_value(world, entity, path, "surface_shader", value);
}

fn publish_value(world: &mut World, entity: Entity, path: &str, slot: &str, value: Value) {
    if scenemax_multiplayer::protocol::encode_value(&value).is_err() { return; }
    let Some(mut net) = world.get_resource_mut::<Multiplayer>() else { return; };
    let Some(client) = net.client.as_ref() else { return; };
    if matches!(client.state(), State::Closed | State::Rejected) { return; }
    let name = key(path, slot);
    if net.widgets.pending.len() >= LIMIT && !net.widgets.pending.contains_key(&name) { return; }
    net.widgets.pending.insert(name.clone(), value.clone());
    if net.widgets.received.len() < LIMIT || net.widgets.received.contains_key(&name) {
        net.widgets.received.insert(name, Received {
            value, applied: Some(entity), received_at: std::time::Instant::now(),
        });
    }
}

pub(super) fn flush(world: &mut World, net: &mut Multiplayer) {
    if let Some(client) = net.client.as_mut() {
        if matches!(client.state(), State::Closed | State::Rejected) {
            net.widgets = WidgetSync::default();
            return;
        }
        net.widgets.pending.retain(|name, value| {
            client
                .send(
                    None,
                    Outgoing::Variable {
                        name: name.clone(),
                        initial: false,
                        value: value.clone(),
                    },
                )
                .is_err()
        });
    }
    if net.widgets.received.is_empty() || !world.contains_resource::<SceneMaxUiActionQueue>() {
        return;
    }
    let Some(runtime) = world.get_resource::<SceneMaxUiRuntime>() else {
        return;
    };
    let mut actions = Vec::new();
    for (ui_name, loaded) in &runtime.loaded {
        for (path, widget) in &loaded.targets {
            if !widget.multiplayer {
                continue;
            }
            let Some(rest) = path.strip_prefix(&format!("{ui_name}.")) else {
                continue;
            };
            let Some((layer, widgets)) = rest.split_once('.') else {
                continue;
            };
            let target = UiTargetPath {
                ui_name: Some(ui_name.clone()),
                layer: layer.into(),
                widget_path: widgets.split('.').map(str::to_owned).collect(),
            };
            for slot in SLOTS {
                if matches!(slot, "image_state" | "frame") && net.widgets.received.contains_key(&key(path, "image_shader")) {
                    continue;
                }
                if slot == "backgroundcolor" && net.widgets.received.contains_key(&key(path, "surface_shader")) {
                    continue;
                }
                if slot == "frame" && net.widgets.received.contains_key(&key(path, "image_state")) {
                    continue;
                }
                let control = assemble_control(&net.widgets, path, slot);
                let Some(update) = net.widgets.received.get_mut(&key(path, slot)) else {
                    continue;
                };
                if update.applied == Some(widget.entity) {
                    continue;
                }
                if let Some(mut action) = decode(slot, control.as_ref().unwrap_or(&update.value), target.clone()) {
                    if let SceneMaxUiAction::Ease { received_at, .. } = &mut action {
                        *received_at = Some(update.received_at);
                    }
                    actions.push(action);
                    update.applied = Some(widget.entity);
                }
            }
        }
    }
    if let Some(mut queue) = world.get_resource_mut::<SceneMaxUiActionQueue>() {
        queue.remote_actions.extend(actions);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;

    const PATH: &str = "fixture.overlay.panel.caption";

    fn target() -> UiTargetPath {
        UiTargetPath {
            ui_name: Some("fixture".into()),
            layer: "overlay".into(),
            widget_path: vec!["panel".into(), "caption".into()],
        }
    }

    fn app() -> App {
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, bevy::asset::AssetPlugin::default()));
        app.insert_resource(SceneMaxLaunchContext {
            script_root: None,
            asset_root: None,
            builtin_asset_root: None,
            window_width: 800,
            window_height: 600,
        });
        app.init_resource::<SceneMaxStartupProgram>();
        app.init_resource::<SceneMaxUiRuntime>();
        app.init_resource::<SceneMaxUiActionQueue>();
        app
    }

    fn widget(world: &mut World, marked: bool) -> Entity {
        let entity = world
            .spawn((
                Text::new("local"),
                TextColor::default(),
                UiTransform::default(),
                Visibility::Inherited,
            ))
            .id();
        let mut runtime = world.resource_mut::<SceneMaxUiRuntime>();
        runtime.active_ui_name = Some("fixture".into());
        runtime
            .loaded
            .entry("fixture".into())
            .or_default()
            .targets
            .insert(
                PATH.into(),
                SceneMaxUiTarget {
                    entity,
                    text_entity: Some(entity),
                    multiplayer: marked,
                    widget_type: "TEXT_VIEW".into(),
                    ui_scale: 1.0,
                },
            );
        entity
    }

    fn apply(world: &mut World) {
        world.run_system_once(apply_scenemax_ui_actions).unwrap();
        world.flush();
    }

    fn replay(world: &mut World, net: &mut Multiplayer) {
        flush(world, net);
        world.insert_resource(std::mem::take(net));
        apply(world);
        *net = world.remove_resource::<Multiplayer>().unwrap();
    }

    fn load_image(world: &mut World) -> (tempfile::TempDir, Entity) {
        let root = tempfile::tempdir().unwrap();
        std::fs::write(root.path().join("fixture.smui"), json!({
            "name":"fixture", "layers":[{"name":"overlay", "widgets":[{
                "name":"panel", "type":"PANEL", "children":[{
                    "name":"caption", "type":"IMAGE", "multiplayer":true,
                    "spriteName":"missing atlas", "spriteFrame":3
                }]
            }]}]
        }).to_string()).unwrap();
        world.resource_mut::<SceneMaxLaunchContext>().script_root = Some(root.path().to_owned());
        world.resource_mut::<SceneMaxUiActionQueue>().actions.push(SceneMaxUiAction::Load { name: "fixture".into() });
        apply(world);
        let entity = resolve_ui_target(world.resource::<SceneMaxUiRuntime>(), &target()).unwrap().entity;
        assert!(crate::ui::images::snapshot(world, entity).is_some(), "spawned image state missing");
        assert_eq!(resolve_ui_target(world.resource::<SceneMaxUiRuntime>(), &target()).unwrap().widget_type, "IMAGE");
        (root, entity)
    }

    fn shader_panel(world: &mut World) -> Entity {
        world.init_resource::<Assets<crate::ui::shaders::SurfaceShader>>();
        let entity = widget(world, true);
        world.entity_mut(entity).insert(BackgroundColor(Color::srgb(0.,0.,1.)));
        world.resource_mut::<SceneMaxUiRuntime>().loaded.get_mut("fixture").unwrap()
            .targets.get_mut(PATH).unwrap().widget_type = "PANEL".into();
        entity
    }

    #[test]
    fn image_shader_replay_preserves_reset_and_authored_sources_without_echo() {
        let mut app = app();
        let world = app.world_mut();
        let (_server, _, client) = super::super::joins::tests::connection();
        let mut net = Multiplayer { client: Some(client), ..default() };
        let value = json!([[null,"missing atlas",3],["missing.png",null,0],true,null,null]);
        receive(&mut net.widgets, key(PATH,"image_shader"), value.clone());
        flush(world, &mut net);
        assert!(world.resource::<SceneMaxUiActionQueue>().remote_actions.is_empty());
        let (_root, entity) = load_image(world);
        receive(&mut net.widgets, key(PATH,"image_state"), json!(["stale.png",null,9]));
        receive(&mut net.widgets, key(PATH,"frame"), json!("8"));
        replay(world, &mut net);
        assert_eq!(world.get::<ImageNode>(entity).unwrap().color, Color::srgba(0.5,0.5,0.5,0.3));
        assert_eq!(crate::ui::images::snapshot(world, entity).unwrap().image.as_deref(), Some("missing.png"));
        assert!(net.widgets.pending.is_empty());
        for bad in [json!([[null,null,0],[null,null,0],true,null,""]),
            json!([[null,null,0],[null,null,0],true,-1,null]),
            json!([["bad\0path",null,0],[null,null,0],false,null,null]),
            json!([[null,null,0],["x".repeat(300),null,0],false,null,null])] {
            receive(&mut net.widgets, key(PATH,"image_shader"), bad);
        }
        assert_eq!(net.widgets.received[&key(PATH,"image_shader")].value, value);
        let (_second_root, second) = load_image(world);
        replay(world, &mut net);
        assert_eq!(world.get::<ImageNode>(second).unwrap().color, Color::srgba(0.5,0.5,0.5,0.3));
        world.resource_mut::<SceneMaxUiActionQueue>().remote_actions.push(SceneMaxUiAction::SetProperty {
            target: target(), property: "frame".into(), value: "2".into(),
        });
        world.insert_resource(net);
        apply(world);
        assert_eq!(world.get::<ImageNode>(second).unwrap().color, Color::srgba(1.,0.,1.,1.));
        assert!(world.resource::<Multiplayer>().widgets.pending.is_empty());
    }

    #[test]
    fn image_shader_capture_combines_first_source_reset_and_resource_commands() {
        let mut app = app();
        let world = app.world_mut();
        let (root, entity) = load_image(world);
        std::fs::create_dir(root.path().join("scripts")).unwrap();
        std::fs::write(root.path().join("scripts/tint.bvshader"), r#"{"mainColor":[0,1,0,1],"blocks":["TINT"]}"#).unwrap();
        world.resource_mut::<SceneMaxLaunchContext>().asset_root = Some(root.path().join("resources"));
        world.init_resource::<Assets<crate::ui::shaders::SurfaceShader>>();
        let (_server, _, client) = super::super::joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        for (property, value) in [("shader","tint"),("image","missing.png"),("shader",""),("shader","invalid")] {
            world.resource_mut::<SceneMaxUiActionQueue>().actions.push(SceneMaxUiAction::SetProperty {
                target: target(), property: property.into(), value: value.into(),
            });
        }
        apply(world);
        assert_eq!(world.get::<ImageNode>(entity).unwrap().color, Color::srgba(0.5,0.5,0.5,0.3));
        assert_eq!(world.resource::<Multiplayer>().widgets.pending[&key(PATH,"image_shader")],
            json!([[null,"missing atlas",3],["missing.png",null,0],true,null,null]));
    }

    #[test]
    fn shader_capture_keeps_first_and_current_backgrounds_after_reset_and_color_edits() {
        let mut app = app();
        let world = app.world_mut();
        let root = tempfile::tempdir().unwrap();
        std::fs::create_dir(root.path().join("scripts")).unwrap();
        std::fs::write(root.path().join("scripts/tint.bvshader"), r#"{"mainColor":[0,1,0,1],"blocks":["TINT"]}"#).unwrap();
        world.resource_mut::<SceneMaxLaunchContext>().asset_root = Some(root.path().join("resources"));
        let entity = shader_panel(world);
        let (_server, _, client) = super::super::joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        for (property, value) in [("shader","tint"),("shader",""),("color","#ff0000"),("shader","tint"),("shader","")] {
            world.resource_mut::<SceneMaxUiActionQueue>().actions.push(SceneMaxUiAction::SetProperty {
                target: target(), property: property.into(), value: value.into(),
            });
        }
        apply(world);
        assert_eq!(world.get::<BackgroundColor>(entity).unwrap().0, Color::srgb(0.,0.,1.));
        assert_eq!(world.resource::<Multiplayer>().widgets.pending[&key(PATH,"surface_shader")],
            json!(["",true,[0.,0.,1.,1.],[0.,0.,1.,1.]]));
        for (property, value) in [("color","#00ff00"),("shader","missing")] {
            world.resource_mut::<SceneMaxUiActionQueue>().actions.push(SceneMaxUiAction::SetProperty {
                target: target(), property: property.into(), value: value.into(),
            });
        }
        apply(world);
        assert_eq!(world.resource::<Multiplayer>().widgets.pending[&key(PATH,"surface_shader")],
            json!(["",true,[0.,0.,1.,1.],[0.,1.,0.,1.]]));
    }

    #[test]
    fn shader_snapshot_recreates_backgrounds_without_echo_or_stale_color_override() {
        let mut app = app();
        let world = app.world_mut();
        let (_server, _, client) = super::super::joins::tests::connection();
        let mut net = Multiplayer { client: Some(client), ..default() };
        let value = json!(["",true,[0.,0.,1.,1.],[0.,1.,0.,1.]]);
        receive(&mut net.widgets, key(PATH,"surface_shader"), value.clone());
        flush(world, &mut net);
        assert!(world.resource::<SceneMaxUiActionQueue>().remote_actions.is_empty());
        let first = shader_panel(world);
        replay(world, &mut net);
        assert_eq!(world.get::<BackgroundColor>(first).unwrap().0, Color::srgb(0.,1.,0.));
        receive(&mut net.widgets, key(PATH,"backgroundcolor"), json!("#ff0000"));
        for bad in [json!(["",true,[0,0,2,1],null]), json!(["tint",false,null,null]),
            json!(["bad\0name",true,null,null]),json!(["",true,[1,2],null]),
            json!(["x".repeat(300),true,null,null])] {
            receive(&mut net.widgets, key(PATH,"surface_shader"), bad);
        }
        assert_eq!(net.widgets.received[&key(PATH,"surface_shader")].value,value);
        let second = shader_panel(world);
        replay(world, &mut net);
        assert_eq!(world.get::<BackgroundColor>(second).unwrap().0, Color::srgb(0.,1.,0.));
        crate::ui::shaders::apply(world, second, "");
        assert_eq!(world.get::<BackgroundColor>(second).unwrap().0, Color::srgb(0.,0.,1.));
        assert!(net.widgets.pending.is_empty());
    }

    #[test]
    fn image_capture_is_atomic_after_aliases_and_same_batch_frame_resets() {
        let mut app = app();
        let world = app.world_mut();
        let (_root, entity) = load_image(world);
        let (_server, _, client) = super::super::joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        for (property, value) in [("frame", "2.9"), ("imagepath", "missing.png"), ("spritename", "missing atlas")] {
            world.resource_mut::<SceneMaxUiActionQueue>().actions.push(SceneMaxUiAction::SetProperty {
                target: target(), property: property.into(), value: value.into(),
            });
        }
        apply(world);
        let net = world.resource::<Multiplayer>();
        assert_eq!(net.widgets.pending.len(), 1);
        let value = json!([null,"missing atlas",0]);
        assert_eq!(net.widgets.pending[&key(PATH,"image_state")], value);
        assert_eq!(net.widgets.received[&key(PATH,"image_state")].applied, Some(entity));
        assert_eq!(crate::ui::images::snapshot(world, entity).unwrap().frame, 0);
    }

    #[test]
    fn image_snapshot_defers_reloads_rejects_bad_values_and_dominates_stale_frame() {
        let mut app = app();
        let world = app.world_mut();
        let (_server, _, client) = super::super::joins::tests::connection();
        let mut net = Multiplayer { client: Some(client), ..default() };
        let value = json!(["missing.png",null,0]);
        receive(&mut net.widgets, key(PATH,"image_state"), value.clone());
        flush(world, &mut net);
        assert!(world.resource::<SceneMaxUiActionQueue>().remote_actions.is_empty());
        let (_root, first) = load_image(world);
        receive(&mut net.widgets, key(PATH,"frame"), json!("3"));
        replay(world, &mut net);
        assert_eq!(world.get::<ImageNode>(first).unwrap().color, Color::srgba(1.,0.,1.,1.));
        assert_eq!(crate::ui::images::snapshot(world, first).unwrap().frame, 0);
        for bad in [json!(["bad\0name",null,1]),
            json!([null,null,-1]),
            json!([null,"x".repeat(300),1]),
            json!([null,null,1,"ignored"])] {
            receive(&mut net.widgets, key(PATH,"image_state"), bad);
        }
        assert_eq!(net.widgets.received[&key(PATH,"image_state")].value, value);
        let loaded = world.resource_mut::<SceneMaxUiRuntime>().loaded.remove("fixture").unwrap();
        for root in loaded.root_entities { world.despawn(root); }
        world.resource_mut::<SceneMaxUiActionQueue>().actions.push(SceneMaxUiAction::Load { name:"fixture".into() });
        apply(world);
        let second = resolve_ui_target(world.resource::<SceneMaxUiRuntime>(), &target()).unwrap().entity;
        assert_ne!(first, second);
        replay(world, &mut net);
        assert_eq!(world.get::<ImageNode>(second).unwrap().color, Color::srgba(1.,0.,1.,1.));
        receive(&mut net.widgets, key(PATH,"frame"), json!("2"));
        replay(world, &mut net);
        assert_eq!(crate::ui::images::snapshot(world, second).unwrap().frame, 0);
        assert!(net.widgets.pending.is_empty());
    }

    #[test]
    fn deferred_snapshot_applies_text_visibility_and_reload_without_echo() {
        let mut app = app();
        let world = app.world_mut();
        let mut net = Multiplayer::default();
        let (_server, _, client) = super::super::joins::tests::connection();
        net.client = Some(client);
        events::receive(
            world,
            &mut net,
            Incoming::Variable {
                name: key(PATH, "text"),
                initial: false,
                value: json!("שלום \"UI\"\nline"),
            },
        );
        receive(&mut net.widgets, key(PATH, "visible"), json!(false));
        flush(world, &mut net);
        assert!(
            world
                .resource::<SceneMaxUiActionQueue>()
                .remote_actions
                .is_empty()
        );
        let local = widget(world, false);
        flush(world, &mut net);
        apply(world);
        assert_eq!(world.get::<Text>(local).unwrap().0, "local");
        let marked = widget(world, true);
        replay(world, &mut net);
        assert_eq!(world.get::<Text>(marked).unwrap().0, "שלום \"UI\"\nline");
        assert_eq!(
            *world.get::<Visibility>(marked).unwrap(),
            Visibility::Hidden
        );
        assert!(net.widgets.pending.is_empty());
        flush(world, &mut net);
        assert!(
            world
                .resource::<SceneMaxUiActionQueue>()
                .remote_actions
                .is_empty()
        );
        let reloaded = widget(world, true);
        flush(world, &mut net);
        apply(world);
        assert_eq!(world.get::<Text>(reloaded).unwrap().0, "שלום \"UI\"\nline");
    }

    #[test]
    fn ease_replay_is_typed_deduplicated_and_visibility_supersedes_it() {
        let mut app = app();
        let world = app.world_mut();
        let entity = widget(world, true);
        let mut net = Multiplayer::default();
        let value = json!([1, 8, "quad", "left", 2.0]);
        receive(&mut net.widgets, key(PATH, "visible"), value.clone());
        flush(world, &mut net);
        apply(world);
        assert_eq!(
            world
                .get::<SceneMaxUiEase>(entity)
                .unwrap()
                .duration_seconds,
            2.0
        );
        world
            .get_mut::<SceneMaxUiEase>(entity)
            .unwrap()
            .elapsed_seconds = 1.;
        receive(&mut net.widgets, key(PATH, "visible"), value);
        receive(
            &mut net.widgets,
            key(PATH, "visible"),
            json!([1, 7, "quad", "right", 4.]),
        );
        flush(world, &mut net);
        apply(world);
        assert_eq!(
            world.get::<SceneMaxUiEase>(entity).unwrap().elapsed_seconds,
            1.
        );
        receive(&mut net.widgets, key(PATH, "visible"), json!(false));
        flush(world, &mut net);
        apply(world);
        assert_eq!(
            *world.get::<Visibility>(entity).unwrap(),
            Visibility::Hidden
        );
        assert!(net.widgets.pending.is_empty());
    }

    #[test]
    fn deferred_ease_preserves_curve_and_age_through_duplicates_and_reload() {
        let mut app = app();
        let world = app.world_mut();
        let mut net = Multiplayer::default();
        let name = key(PATH, "visible");
        let value = json!([1, 8, "quad", "left", 20.0]);
        receive(&mut net.widgets, name.clone(), value.clone());
        let origin = std::time::Instant::now() - std::time::Duration::from_secs(10);
        net.widgets.received.get_mut(&name).unwrap().received_at = origin;
        flush(world, &mut net); // No UI loaded yet.
        receive(&mut net.widgets, name.clone(), value);
        receive(
            &mut net.widgets,
            name.clone(),
            json!([1, 7, "quad", "left", 20.]),
        );
        assert_eq!(net.widgets.received[&name].received_at, origin);
        for _ in 0..2 {
            let entity = widget(world, true);
            replay(world, &mut net);
            let ease = world.get::<SceneMaxUiEase>(entity).unwrap();
            assert!(ease.elapsed_seconds >= 10.);
            assert_eq!(ease.duration_seconds, 20.);
            let t = ease.elapsed_seconds / 20.;
            // Resume the original quadratic curve, not a new shortened curve.
            assert_eq!(
                world.get::<UiTransform>(entity).unwrap().translation,
                Val2::percent(-115. * (1. - t * t), 0.)
            );
        }
        assert!(net.widgets.pending.is_empty());
    }

    #[test]
    fn ease_ages_in_action_queue_and_expired_replay_removes_old_animation() {
        let mut app = app();
        let world = app.world_mut();
        let entity = widget(world, true);
        let mut net = Multiplayer::default();
        let name = key(PATH, "visible");
        receive(
            &mut net.widgets,
            name.clone(),
            json!([1, 8, "quad", "left", 2.]),
        );
        flush(world, &mut net);
        let queue = &mut world.resource_mut::<SceneMaxUiActionQueue>().remote_actions;
        let SceneMaxUiAction::Ease { received_at, .. } = &mut queue[0] else {
            panic!()
        };
        // Simulate time between dispatch and entity creation without sleeping.
        *received_at = Some(std::time::Instant::now() - std::time::Duration::from_secs(3));
        world.entity_mut(entity).insert(SceneMaxUiEase {
            start: Vec2::new(115., 0.),
            elapsed_seconds: 0.,
            duration_seconds: 10.,
            easing: "linear".into(),
        });
        apply(world);
        assert!(world.get::<SceneMaxUiEase>(entity).is_none());
        assert_eq!(
            world.get::<UiTransform>(entity).unwrap().translation,
            Val2::percent(0., 0.)
        );
        assert_eq!(
            *world.get::<Visibility>(entity).unwrap(),
            Visibility::Inherited
        );
        assert!(net.widgets.pending.is_empty());
    }

    #[test]
    fn new_ease_gets_new_origin_and_local_ease_starts_at_zero() {
        let mut app = app();
        let world = app.world_mut();
        let entity = widget(world, true);
        let mut net = Multiplayer::default();
        let name = key(PATH, "visible");
        receive(
            &mut net.widgets,
            name.clone(),
            json!([1, 8, "quad", "left", 2.]),
        );
        let old = std::time::Instant::now() - std::time::Duration::from_secs(3);
        net.widgets.received.get_mut(&name).unwrap().received_at = old;
        receive(
            &mut net.widgets,
            name.clone(),
            json!([1, 9, "quad", "right", 2.]),
        );
        assert!(net.widgets.received[&name].received_at > old);
        world
            .resource_mut::<SceneMaxUiActionQueue>()
            .actions
            .push(SceneMaxUiAction::Ease {
                target: target(),
                easing: "quad".into(),
                direction: UiEaseDirection::Right,
                duration_seconds: 2.,
                received_at: None,
            });
        apply(world);
        assert_eq!(
            world.get::<SceneMaxUiEase>(entity).unwrap().elapsed_seconds,
            0.
        );
        assert_eq!(
            world.get::<UiTransform>(entity).unwrap().translation,
            Val2::percent(115., 0.)
        );
    }

    #[test]
    fn capture_uses_resolved_values_and_only_marked_supported_actions() {
        let mut app = app();
        let world = app.world_mut();
        widget(world, true);
        let (_server, _, client) = super::super::joins::tests::connection();
        world.insert_resource(Multiplayer {
            client: Some(client),
            ..default()
        });
        world
            .resource_mut::<SceneMaxUiActionQueue>()
            .actions
            .extend([
                SceneMaxUiAction::SetProperty {
                    target: target(),
                    property: "text".into(),
                    value: "evaluated 42".into(),
                },
                SceneMaxUiAction::ShowHide {
                    target: target(),
                    visible: false,
                },
                SceneMaxUiAction::SetProperty {
                    target: target(),
                    property: "frame".into(),
                    value: "3".into(),
                },
                SceneMaxUiAction::SetProperty {
                    target: target(),
                    property: "unsupported".into(),
                    value: "ignored".into(),
                },
            ]);
        apply(world);
        let net = world.resource::<Multiplayer>();
        assert_eq!(net.widgets.pending.len(), 3);
        assert_eq!(
            net.widgets.pending[&key(PATH, "text")],
            json!("evaluated 42")
        );
        assert_eq!(net.widgets.pending[&key(PATH, "frame")], json!("3"));
        world.resource_mut::<Multiplayer>().widgets.pending.clear();
        widget(world, false);
        world
            .resource_mut::<SceneMaxUiActionQueue>()
            .actions
            .push(SceneMaxUiAction::ShowHide {
                target: target(),
                visible: true,
            });
        apply(world);
        assert!(world.resource::<Multiplayer>().widgets.pending.is_empty());
    }

    #[test]
    fn malformed_payloads_and_script_source_are_not_executable() {
        let mut state = WidgetSync::default();
        for value in [
            json!({"code":"UI.fixture.overlay.panel.caption.Hide"}),
            json!("UI.Load other"),
            json!([1, 1, "quad", "invalid", 2]),
            json!([1, 1, "quad", "left", -1]),
            json!([1]),
        ] {
            receive(&mut state, key(PATH, "visible"), value);
        }
        receive(&mut state, "$ui:invalid:text".into(), json!("ignored"));
        receive(&mut state, key(PATH, "frame"), json!("not a frame"));
        receive(&mut state, key(PATH, "text"), json!("x".repeat(256)));
        assert!(state.received.is_empty());
        for index in 0..LIMIT + 1 {
            receive(
                &mut state,
                key(&format!("{PATH}{index}"), "text"),
                json!("bounded"),
            );
        }
        assert_eq!(state.received.len(), LIMIT);
    }

    #[test]
    fn scene_change_clears_pending_widget_state() {
        let mut world = World::new();
        world.insert_resource(SceneMaxUiActionQueue {
            remote_actions: vec![SceneMaxUiAction::ShowHide {
                target: target(),
                visible: false,
            }],
            ..default()
        });
        let mut net = Multiplayer::default();
        receive(&mut net.widgets, key(PATH, "text"), json!("old scene"));
        world.insert_resource(net);
        world
            .run_system_once(|mut commands: Commands| scene_changed(&mut commands, "next"))
            .unwrap();
        assert!(world.resource::<Multiplayer>().widgets.received.is_empty());
        assert!(
            world
                .resource::<SceneMaxUiActionQueue>()
                .remote_actions
                .is_empty()
        );
    }

    #[test]
    fn saved_schema_keeps_nested_flags_and_defaults_unmarked() {
        let document: SceneMaxUiDocument = serde_json::from_value(json!({
            "name":"fixture", "layers":[{"name":"overlay", "widgets":[{
                "name":"panel", "type":"Panel", "children":[{
                    "name":"caption", "type":"TextView", "multiplayer":true
                }]
            }]}]
        }))
        .unwrap();
        assert!(!document.layers[0].widgets[0].multiplayer);
        assert!(document.layers[0].widgets[0].children[0].multiplayer);
    }

    #[test]
    fn styles_keep_independent_snapshot_slots_and_survive_later_text() {
        let mut app = app();
        let world = app.world_mut();
        let entity = widget(world, true);
        world.entity_mut(entity).insert(SceneMaxUiTextVisualState {
            base_color: Color::WHITE,
            base_transform: UiTransform::default(),
        });
        let (_server, _, client) = super::super::joins::tests::connection();
        world.insert_resource(Multiplayer {
            client: Some(client),
            ..default()
        });
        for (property, value) in [
            ("color", "#00ff0080"),
            ("fontsize", "48"),
            ("text", "styled"),
        ] {
            world.resource_mut::<SceneMaxUiActionQueue>().actions.push(
                SceneMaxUiAction::SetProperty {
                    target: target(),
                    property: property.into(),
                    value: value.into(),
                },
            );
        }
        apply(world);
        let mut sender = world.remove_resource::<Multiplayer>().unwrap();
        assert_eq!(sender.widgets.pending.len(), 3);
        assert_eq!(
            sender.widgets.pending[&key(PATH, "textcolor")],
            json!("#00ff0080")
        );
        let mut receiver = Multiplayer::default();
        for (name, value) in sender.widgets.pending.drain() {
            receive(&mut receiver.widgets, name, value);
        }
        let receiver_entity = widget(world, true);
        world
            .entity_mut(receiver_entity)
            .insert(SceneMaxUiTextVisualState {
                base_color: Color::WHITE,
                base_transform: UiTransform::default(),
            });
        replay(world, &mut receiver);
        let expected = crate::ui::properties::color("#00ff0080").unwrap();
        assert_eq!(world.get::<TextColor>(receiver_entity).unwrap().0, expected);
        assert_eq!(
            world.get::<TextFont>(receiver_entity).unwrap().font_size,
            FontSize::Px(48.)
        );
        world
            .resource_mut::<SceneMaxUiActionQueue>()
            .actions
            .push(SceneMaxUiAction::SetProperty {
                target: target(),
                property: "text".into(),
                value: "later".into(),
            });
        apply(world);
        assert_eq!(world.get::<TextColor>(receiver_entity).unwrap().0, expected);
    }

    #[test]
    fn font_alias_uses_typed_snapshot_slot_and_invalid_names_preserve_it() {
        let mut app = app();
        let world = app.world_mut();
        widget(world, true);
        let (_server, _, client) = super::super::joins::tests::connection();
        world.insert_resource(Multiplayer { client: Some(client), ..default() });
        world.resource_mut::<SceneMaxUiActionQueue>().actions.push(SceneMaxUiAction::SetProperty {
            target: target(), property: "fontname".into(), value: "custom Ω".into(),
        });
        apply(world);
        let mut net = world.remove_resource::<Multiplayer>().unwrap();
        let wire = net.widgets.pending.remove(&key(PATH, "font")).unwrap();
        assert_eq!(wire, json!("custom Ω"));
        let mut receiver = WidgetSync::default();
        receive(&mut receiver, key(PATH, "font"), wire);
        receive(&mut receiver, key(PATH, "font"), json!("bad\0name"));
        assert_eq!(receiver.received[&key(PATH, "font")].value, json!("custom Ω"));
        assert!(matches!(decode("font", &json!(""), target()), Some(SceneMaxUiAction::SetProperty { property, value, .. })
            if property == "font" && value.is_empty()));
    }

    #[test]
    fn button_aliases_route_background_and_text_separately_and_reject_invalid_styles() {
        let mut app = app();
        let world = app.world_mut();
        let entity = widget(world, true);
        world
            .resource_mut::<SceneMaxUiRuntime>()
            .loaded
            .get_mut("fixture")
            .unwrap()
            .targets
            .get_mut(PATH)
            .unwrap()
            .widget_type = "BUTTON".into();
        for (property, value) in [
            ("color", "#102030"),
            ("buttontextcolor", "#abcdef"),
            ("fontsize", "NaN"),
        ] {
            world.resource_mut::<SceneMaxUiActionQueue>().actions.push(
                SceneMaxUiAction::SetProperty {
                    target: target(),
                    property: property.into(),
                    value: value.into(),
                },
            );
        }
        apply(world);
        assert_eq!(
            world.get::<BackgroundColor>(entity).unwrap().0,
            crate::ui::properties::color("#102030").unwrap()
        );
        assert_eq!(
            world.get::<TextColor>(entity).unwrap().0,
            crate::ui::properties::color("#abcdef").unwrap()
        );
        let mut state = WidgetSync::default();
        for value in ["NaN", "inf", "0", "-2", "100000"] {
            receive(&mut state, key(PATH, "fontsize"), json!(value));
        }
        for value in ["💚💚", "#zzzzzz", "#12345"] {
            receive(&mut state, key(PATH, "textcolor"), json!(value));
        }
        assert!(state.received.is_empty());
    }

    #[test]
    fn style_updates_refresh_bitmap_text_and_animation_base_color() {
        let mut app = app();
        let world = app.world_mut();
        let entity = widget(world, true);
        world.entity_mut(entity).remove::<Text>();
        world.entity_mut(entity).insert((
            SceneMaxUiBitmapText {
                text: "bitmap".into(),
                font_name: "fixture".into(),
                font_size: 12.,
                color: Color::WHITE,
                alignment: Justify::Left,
                widget_width: 300.,
                widget_height: 100.,
                glyph_entities: vec![],
            },
            SceneMaxUiMessageAnimation {
                full_text: "bitmap".into(),
                effect_names: vec![],
                elapsed_seconds: 0.,
                duration_seconds: 2.,
                base_color: Color::WHITE,
                base_transform: UiTransform::default(),
            },
        ));
        world
            .resource_mut::<SceneMaxUiRuntime>()
            .loaded
            .get_mut("fixture")
            .unwrap()
            .targets
            .get_mut(PATH)
            .unwrap()
            .ui_scale = 0.5;
        for (property, value) in [("textcolor", "#ff0000"), ("fontsize", "60")] {
            world.resource_mut::<SceneMaxUiActionQueue>().actions.push(
                SceneMaxUiAction::SetProperty {
                    target: target(),
                    property: property.into(),
                    value: value.into(),
                },
            );
        }
        apply(world);
        let bitmap = world.get::<SceneMaxUiBitmapText>(entity).unwrap();
        assert_eq!(bitmap.font_size, scaled_font_size(60., 0.5));
        assert_eq!(
            bitmap.color,
            crate::ui::properties::color("#ff0000").unwrap()
        );
        assert_eq!(
            world
                .get::<SceneMaxUiMessageAnimation>(entity)
                .unwrap()
                .base_color,
            bitmap.color
        );
    }

    #[test]
    fn load_then_text_waits_for_spawned_entities() {
        let root = tempfile::tempdir().unwrap();
        std::fs::write(
            root.path().join("fixture.smui"),
            json!({
                "name":"fixture", "layers":[{"name":"overlay", "widgets":[{
                    "name":"panel", "type":"PANEL", "children":[{
                        "name":"caption", "type":"TEXT_VIEW", "multiplayer":true, "text":"initial"
                    }]
                }]}]
            })
            .to_string(),
        )
        .unwrap();
        let mut app = app();
        let world = app.world_mut();
        world.resource_mut::<SceneMaxLaunchContext>().script_root = Some(root.path().to_owned());
        world
            .resource_mut::<SceneMaxUiActionQueue>()
            .actions
            .extend([
                SceneMaxUiAction::Load {
                    name: "fixture".into(),
                },
                SceneMaxUiAction::SetProperty {
                    target: target(),
                    property: "text".into(),
                    value: "after load".into(),
                },
            ]);
        apply(world);
        assert_eq!(world.resource::<SceneMaxUiActionQueue>().actions.len(), 1);
        apply(world);
        let entity = resolve_ui_target(world.resource::<SceneMaxUiRuntime>(), &target())
            .unwrap()
            .entity;
        assert_eq!(world.get::<Text>(entity).unwrap().0, "after load");
        assert!(world.resource::<SceneMaxUiActionQueue>().actions.is_empty());
    }

    #[test]
    fn remote_frame_uses_existing_sprite_sheet_and_local_edit_wins_next_frame() {
        let mut app = app();
        let world = app.world_mut();
        let entity = widget(world, true);
        world.entity_mut(entity).insert((
            ImageNode::default(),
            SceneMaxUiSpriteSheet {
                cols: 4,
                rows: 1,
                image_width: 400,
                image_height: 100,
            },
        ));
        let mut net = Multiplayer::default();
        receive(&mut net.widgets, key(PATH, "frame"), json!("2.9"));
        receive(&mut net.widgets, key(PATH, "text"), json!("remote"));
        world
            .resource_mut::<SceneMaxUiActionQueue>()
            .actions
            .push(SceneMaxUiAction::SetProperty {
                target: target(),
                property: "text".into(),
                value: "new local".into(),
            });
        replay(world, &mut net);
        assert_eq!(world.get::<Text>(entity).unwrap().0, "new local");
        assert_eq!(
            world.get::<ImageNode>(entity).unwrap().rect,
            Some(ui_sprite_frame_rect(2, 4, 1, 400, 100))
        );
    }
}
