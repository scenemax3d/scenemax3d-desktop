//! Compact retained Project Settings multiplayer form.
use crate::application::multiplayer::{Action, State, Tab};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_ui::{label, property, theme::*};
#[derive(Component)]
pub(crate) struct Field(String);
#[derive(Component)]
pub(crate) struct MultiplayerToggle;
#[derive(Component)]
pub(crate) struct Status;
#[derive(Component)]
pub(crate) struct LocalStatus;
#[derive(Component)]
pub(crate) struct ServerAction(Action);
#[derive(Component)]
pub(crate) struct LocalReady;
#[derive(Component)]
pub(crate) struct ProgressBar;
#[derive(Component)]
pub(crate) struct ProgressLabel;
#[derive(Component)]
pub(crate) struct ConsoleLog;
#[derive(Component)]
pub(crate) struct LogViewport;
#[derive(Default, Resource)]
pub(crate) struct View {
    root: Option<Entity>,
    revision: u64,
}
pub(crate) fn smoke(
    mut state: ResMut<State>,
    services: Res<crate::application::EditorServices>,
    mut fired: Local<bool>,
    mut seeded: Local<bool>,
    mut launched: Local<bool>,
    mut queue: ResMut<crate::application::CommandQueue>,
) {
    if !*fired && !services.storage.is_pending() && !services.catalog_storage.is_pending() {
        state.actions.push_back(Action::Open);
        *fired = true;
    }
    if *fired && !*seeded && !state.busy && !state.fields.is_empty() {
        if let Ok(password) = std::env::var("SCENEMAX_SMOKE_MULTIPLAYER_PASSWORD") {
            state.password = Some(password);
            state.revision += 1;
            if std::env::var_os("SCENEMAX_SMOKE_MULTIPLAYER_SAVE_RUN").is_some() {
                state.actions.push_back(Action::Save);
            }
        }
        if let Ok(tab) = std::env::var("SCENEMAX_SMOKE_MULTIPLAYER_TAB") {
            state.tab = match tab.as_str() {
                "browser" => Tab::Browser,
                "build" => Tab::Server,
                "deployment" => Tab::Deployment,
                _ => Tab::Server,
            };
            state.revision += 1;
        }
        *seeded = true;
    }
    if *seeded
        && !*launched
        && !state.busy
        && state.status == "Multiplayer settings saved."
        && std::env::var_os("SCENEMAX_SMOKE_MULTIPLAYER_SAVE_RUN").is_some()
    {
        state.actions.push_back(Action::Close);
        queue.0.push_back(crate::application::Command::RunProject);
        *launched = true;
    }
}
pub(crate) fn menu_item(c: &mut Commands, parent: Entity, title: &str) -> Entity {
    let e = scenemax_ide_ui::button(c, parent, title, Name::new("Project Settings"));
    c.entity(e)
        .observe(|_: On<PointerPress>, mut s: ResMut<State>| s.actions.push_back(Action::Open));
    e
}
fn node(c: &mut Commands, p: Entity, mut n: Node) -> Entity {
    n.flex_shrink = 0.;
    c.spawn((n, ChildOf(p))).id()
}
fn text(c: &mut Commands, p: Entity, value: &str) -> Entity {
    c.spawn((
        label(value, 12.),
        Node {
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(p),
    ))
    .id()
}
pub(crate) fn input(
    mut state: ResMut<State>,
    fields: Query<(&Field, Option<&EditableText>, Option<&property::Choice>)>,
    passwords: Query<&scenemax_ide_ui::password::Password>,
    toggles: Query<&property::Checked, With<MultiplayerToggle>>,
    mut focus: Option<ResMut<bevy::input_focus::InputFocus>>,
    keys: Res<ButtonInput<KeyCode>>,
) {
    if !state.open {
        return;
    }
    if let Ok(toggle) = toggles.single() {
        if !state.busy && toggle.0 != state.enabled() {
            state.fields.insert("enabled".into(), toggle.0.to_string());
            state.revision += 1;
            if let Some(focus) = focus.as_mut() {
                focus.clear();
            }
            if !toggle.0 {
                state.actions.push_back(Action::StopTestGame);
                state.actions.push_back(Action::StopLocal);
            }
        }
    }
    if !state.enabled() {
        if keys.just_pressed(KeyCode::Escape) {
            state.actions.push_back(Action::Close);
        }
        return;
    }
    let mut endpoint_changed = false;
    for (field, editable, choice) in &fields {
        let value = editable
            .map(|e| e.value().to_string())
            .or_else(|| choice.map(|c| c.0.clone()));
        if let Some(value) = value {
            if matches!(field.0.as_str(), "server" | "port")
                && state.fields.get(&field.0) != Some(&value)
            {
                endpoint_changed = true;
            }
            state.fields.insert(field.0.clone(), value);
        }
    }
    if endpoint_changed {
        state.follow_server_endpoint();
    }
    for password in &passwords {
        if password.touched() {
            state.password = Some(password.value().to_owned());
        }
    }
    if keys.just_pressed(KeyCode::Escape) {
        state.actions.push_back(Action::Close);
    }
}

fn actions(c: &mut Commands, parent: Entity, items: &[(&str, Action)]) -> Entity {
    let row = node(
        c,
        parent,
        Node {
            column_gap: px(8.),
            flex_wrap: FlexWrap::Wrap,
            ..default()
        },
    );
    for &(caption, action) in items {
        let e = scenemax_ide_ui::button(c, row, caption, Name::new(caption.to_owned()));
        c.entity(e).insert(ServerAction(action)).observe(
            move |_: On<PointerClick>, mut s: ResMut<State>| {
                if s.action_enabled(action) {
                    s.actions.push_back(action);
                }
            },
        );
    }
    row
}
fn fields(c: &mut Commands, parent: Entity, state: &State, items: &[(&str, &str)]) {
    if state.tab == Tab::Deployment {
        let grid = node(
            c,
            parent,
            Node {
                display: Display::Grid,
                grid_template_columns: vec![RepeatedGridTrack::flex(2, 1.)],
                column_gap: px(16.),
                row_gap: px(8.),
                width: percent(100.),
                ..default()
            },
        );
        for &(key, caption) in items {
            let cell = node(
                c,
                grid,
                Node {
                    min_width: px(0.),
                    flex_direction: FlexDirection::Column,
                    row_gap: px(4.),
                    ..default()
                },
            );
            text(c, cell, caption);
            let holder = node(
                c,
                cell,
                Node {
                    height: px(28.),
                    min_width: px(0.),
                    width: percent(100.),
                    ..default()
                },
            );
            field_control(c, holder, state, key);
        }
        return;
    }
    for &(key, caption) in items {
        let row = node(
            c,
            parent,
            Node {
                column_gap: px(8.),
                align_items: AlignItems::Center,
                height: px(28.),
                min_height: px(28.),
                ..default()
            },
        );
        let l = text(c, row, caption);
        c.entity(l).insert(Node {
            width: px(180.),
            height: px(16.),
            flex_shrink: 0.,
            ..default()
        });
        let holder = node(
            c,
            row,
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                ..default()
            },
        );
        field_control(c, holder, state, key);
    }
}
fn field_control(c: &mut Commands, holder: Entity, state: &State, key: &str) {
    let value = state.fields.get(key).map(String::as_str).unwrap_or("");
    let options: &[&str] = match key {
        "gateway_enabled" => &["Disabled", "Enabled"],
        "remote_method" => &["OpenSSH", "PuTTY session"],
        "remote_sudo" => &["Use sudo", "Root login"],
        _ => &[],
    };
    if options.is_empty() {
        property::input(c, holder, Field(key.into()), value);
    } else {
        property::dropdown(
            c,
            holder,
            Field(key.into()),
            value,
            &options.iter().map(|s| s.to_string()).collect::<Vec<_>>(),
        );
    }
}
pub(crate) fn refresh(
    mut c: Commands,
    state: Res<State>,
    mut view: ResMut<View>,
    mut labels: Query<
        (
            &mut Text,
            Option<&Status>,
            Option<&LocalStatus>,
            Option<&ProgressLabel>,
            Option<&ConsoleLog>,
        ),
        Or<(
            With<Status>,
            With<LocalStatus>,
            With<ProgressLabel>,
            With<ConsoleLog>,
        )>,
    >,
    mut bars: Query<&mut Node, With<ProgressBar>>,
    mut log_scroll: Query<(&mut ScrollPosition, &ComputedNode), With<LogViewport>>,
) {
    if !state.open {
        if let Some(root) = view.root.take() {
            c.entity(root).despawn();
        }
        return;
    }
    if view.root.is_none() || view.revision != state.revision {
        if let Some(root) = view.root.take() {
            c.entity(root).despawn();
        }
        let root = c
            .spawn((
                Node {
                    position_type: PositionType::Absolute,
                    width: percent(100.),
                    height: percent(100.),
                    align_items: AlignItems::Center,
                    justify_content: JustifyContent::Center,
                    ..default()
                },
                BackgroundColor(Color::srgba(0., 0., 0., 0.6)),
                GlobalZIndex(190),
                bevy::input_focus::tab_navigation::TabGroup::modal(),
            ))
            .id();
        let panel = node(
            &mut c,
            root,
            Node {
                width: px(720.),
                max_width: percent(95.),
                max_height: percent(95.),
                overflow: Overflow::clip(),
                padding: px(16.).all(),
                row_gap: px(6.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
        );
        c.entity(panel).insert(BackgroundColor(PANEL));
        text(&mut c, panel, "Project Settings");
        property::checkbox(
            &mut c,
            panel,
            MultiplayerToggle,
            "Multiplayer game",
            state.enabled(),
        );
        if !state.enabled() {
            text(
                &mut c,
                panel,
                "Single-player game. Enable Multiplayer game to configure servers and multiplayer tests.",
            );
        }
        let outer_panel = panel;
        // Only the tab body scrolls; Save/Back/Next/Close stay visible.
        let panel = c
            .spawn((
                Node {
                    min_height: px(0.),
                    flex_shrink: 1.,
                    overflow: Overflow::scroll_y(),
                    flex_direction: FlexDirection::Column,
                    row_gap: px(6.),
                    ..default()
                },
                ChildOf(outer_panel),
            ))
            .id();
        c.entity(panel).insert(ScrollPosition::default()).observe(
            |mut event: On<PointerScroll>,
             mut nodes: Query<(&mut ScrollPosition, &ComputedNode)>| {
                if let Ok((mut scroll, n)) = nodes.get_mut(event.entity) {
                    let scale = if event.unit == bevy::input::mouse::MouseScrollUnit::Line {
                        40.
                    } else {
                        1.
                    };
                    scroll.y = (scroll.y - event.y * scale).clamp(
                        0.,
                        (n.content_size().y - n.size().y).max(0.) * n.inverse_scale_factor(),
                    );
                    event.propagate(false);
                }
            },
        );
        let tabs = node(
            &mut c,
            panel,
            Node {
                column_gap: px(8.),
                ..default()
            },
        );
        for (title, tab) in [
            ("1  Server", Tab::Server),
            ("2  Browser", Tab::Browser),
            ("3  Deployment", Tab::Deployment),
        ] {
            let e = scenemax_ide_ui::button(&mut c, tabs, title, Name::new(title));
            if tab == state.tab {
                c.entity(e).insert((
                    BackgroundColor(ACCENT),
                    scenemax_ide_ui::ButtonSurface(ACCENT),
                ));
            }
            c.entity(e)
                .observe(move |_: On<PointerClick>, mut s: ResMut<State>| {
                    s.actions.push_back(Action::SelectTab(tab))
                });
        }
        let mut copy_parent = panel;
        match state.tab {
            Tab::Server => {
                text(&mut c, panel, "Connection and shared session");
                fields(
                    &mut c,
                    panel,
                    &state,
                    &[
                        ("server", "Server IP / hostname"),
                        ("port", "UDP port"),
                        ("project_guid", "Project GUID"),
                        ("session_id", "Session ID"),
                        ("session_name", "Session name"),
                    ],
                );
                text(&mut c, panel, "Game password (this IDE session only)");
                scenemax_ide_ui::password::input(
                    &mut c,
                    panel,
                    Name::new("Multiplayer password"),
                    state.password.as_deref().unwrap_or(""),
                );
                actions(
                    &mut c,
                    panel,
                    &[
                        ("No password", Action::NoPassword),
                        ("Use environment password", Action::UseEnvironment),
                    ],
                );
                text(
                    &mut c,
                    panel,
                    "Save applies the password to game runs. Build uses the same password. Passwords are never saved in project files.",
                );
                let controls = node(
                    &mut c,
                    panel,
                    Node {
                        flex_direction: FlexDirection::Column,
                        row_gap: px(0.),
                        ..default()
                    },
                );
                actions(
                    &mut c,
                    controls,
                    &[
                        ("Build Server", Action::Build),
                        ("Cancel build", Action::Cancel),
                        ("Open Folder", Action::OpenFolder),
                    ],
                );
                copy_parent = actions(
                    &mut c,
                    controls,
                    &[
                        ("Run Server Locally", Action::RunLocal),
                        ("Stop Local Server", Action::StopLocal),
                    ],
                );
                text(
                    &mut c,
                    panel,
                    "Build creates Windows, Linux and macOS servers. Run automatically uses this computer’s OS.",
                );
                let status = text(&mut c, panel, &state.local_status);
                c.entity(status).insert(LocalStatus);
                let check = text(&mut c, copy_parent, "✓");
                c.entity(check).insert((
                    LocalReady,
                    TextColor(Color::srgb(0.2, 0.85, 0.35)),
                    Visibility::Hidden,
                    Name::new("Local server ready"),
                ));
            }
            Tab::Browser => {
                text(&mut c, panel, "Optional WebSocket access for browser games");
                fields(
                    &mut c,
                    panel,
                    &state,
                    &[
                        ("gateway_enabled", "Browser gateway"),
                        ("gateway_url", "Browser WebSocket URL"),
                        ("gateway_listen", "Gateway listen IP:port"),
                        ("gateway_origin", "Allowed game origin"),
                    ],
                );
                text(
                    &mut c,
                    panel,
                    "Server address edits fill the browser URL and gateway port (UDP + 1). You can override them here. Remote gateways listen on all interfaces.",
                );
                text(
                    &mut c,
                    panel,
                    "Allowed origin is where the game is hosted: scheme + host + optional port, without a path. For itch.io, use the embedded game's origin. HTTPS games require wss:// and TLS.",
                );
            }
            Tab::Deployment => {
                text(
                    &mut c,
                    panel,
                    "Install and update services on a Linux x64 cloud machine",
                );
                fields(
                    &mut c,
                    panel,
                    &state,
                    &[
                        ("remote_method", "SSH client"),
                        ("remote_host", "Host / saved session"),
                        ("remote_user", "Login (optional)"),
                        ("remote_port", "SSH port (0 = default)"),
                        ("remote_key", "Private key path (optional)"),
                        ("remote_sudo", "Installation permissions"),
                        ("remote_directory", "Remote install folder"),
                        ("remote_service", "Service name"),
                    ],
                );
                text(
                    &mut c,
                    panel,
                    "Use an SSH key/agent or PuTTY session. Trust the host key first; sudo must work without prompting.",
                );
                text(&mut c, panel, &format!("Bundle: {}", state.bundle_label()));
                text(
                    &mut c,
                    panel,
                    "Deploy installs and starts both services, with rollback on failure. Configure the cloud firewall and WSS proxy separately.",
                );
                actions(
                    &mut c,
                    panel,
                    &[
                        ("Test connection", Action::TestRemote),
                        ("Deploy & start", Action::DeployRemote),
                        ("Check status", Action::RemoteStatus),
                        ("Cancel", Action::Cancel),
                    ],
                );
                copy_parent = actions(
                    &mut c,
                    panel,
                    &[
                        (
                            "Test With Local Server",
                            Action::TestGame(scenemax_ide_services::multiplayer::TestTarget::Local),
                        ),
                        (
                            "Test With Remote Server",
                            Action::TestGame(
                                scenemax_ide_services::multiplayer::TestTarget::Remote,
                            ),
                        ),
                        ("Stop Test", Action::StopTestGame),
                    ],
                );
            }
        }
        if matches!(state.tab, Tab::Server | Tab::Deployment) {
            let copy = scenemax_ide_ui::button(
                &mut c,
                copy_parent,
                "Copy Log",
                Name::new("Copy multiplayer log"),
            );
            c.entity(copy).observe(
                |_: On<PointerClick>,
                 mut state: ResMut<State>,
                 clipboard: Option<ResMut<bevy::clipboard::Clipboard>>| {
                    state.status = if let Some(mut clipboard) = clipboard {
                        match clipboard.set_text(state.console_log()) {
                            Ok(()) => "Console log copied.".into(),
                            Err(error) => format!("Could not copy console log: {error}"),
                        }
                    } else {
                        "Clipboard unavailable.".into()
                    };
                },
            );
            let caption = text(&mut c, panel, &state.progress_label());
            c.entity(caption).insert(ProgressLabel);
            let track = node(
                &mut c,
                panel,
                Node {
                    width: percent(100.),
                    height: px(8.),
                    ..default()
                },
            );
            c.entity(track)
                .insert(BackgroundColor(Color::srgb(0.15, 0.17, 0.18)));
            let bar = node(
                &mut c,
                track,
                Node {
                    width: percent(state.progress.percent as f32),
                    height: percent(100.),
                    ..default()
                },
            );
            c.entity(bar).insert((ProgressBar, BackgroundColor(ACCENT)));
            text(&mut c, panel, "Console log");
            let console = node(
                &mut c,
                panel,
                Node {
                    height: px(150.),
                    width: percent(100.),
                    overflow: Overflow::scroll_y(),
                    padding: px(8.).all(),
                    ..default()
                },
            );
            c.entity(console).insert((LogViewport, ScrollPosition::default(), BackgroundColor(Color::srgb(0.08, 0.09, 0.1))))
                .observe(|mut event: On<PointerScroll>, mut nodes: Query<(&mut ScrollPosition, &ComputedNode), With<LogViewport>>| {
                    if let Ok((mut scroll, n)) = nodes.get_mut(event.entity) {
                        let scale = if event.unit == bevy::input::mouse::MouseScrollUnit::Line { 40. } else { 1. };
                        scroll.y = (scroll.y - event.y * scale).clamp(0., (n.content_size().y - n.size().y).max(0.) * n.inverse_scale_factor());
                        event.propagate(false);
                    }
                });
            let log = text(&mut c, console, &state.console_log());
            c.entity(log).insert((
                ConsoleLog,
                Node {
                    width: percent(100.),
                    flex_shrink: 0.,
                    ..default()
                },
            ));
        }
        if !state.enabled() {
            c.queue(move |world: &mut World| {
                let mut nodes = vec![panel];
                let mut index = 0;
                while index < nodes.len() {
                    if let Some(children) = world.get::<Children>(nodes[index]) {
                        nodes.extend(children.iter());
                    }
                    index += 1;
                }
                for entity in nodes {
                    world.entity_mut(entity).insert((
                        bevy::ui::InteractionDisabled,
                        Pickable::IGNORE,
                        bevy::input_focus::tab_navigation::TabIndex(-1),
                    ));
                    if let Some(mut color) = world.get_mut::<TextColor>(entity) {
                        color.0 = MUTED.with_alpha(0.45);
                    }
                }
            });
        }
        let panel = outer_panel;
        let mut footer = vec![("Save", Action::Save)];
        if state.enabled() && state.tab != Tab::Server {
            footer.push((
                "Back",
                Action::SelectTab(match state.tab {
                    Tab::Browser => Tab::Server,
                    _ => Tab::Browser,
                }),
            ));
        }
        if state.enabled() && state.tab != Tab::Deployment {
            footer.push((
                "Next",
                Action::SelectTab(match state.tab {
                    Tab::Server => Tab::Browser,
                    _ => Tab::Deployment,
                }),
            ));
        }
        footer.push(("Close", Action::Close));
        actions(&mut c, panel, &footer);
        let status = text(&mut c, panel, &state.status);
        c.entity(status).insert(Status);
        view.root = Some(root);
        view.revision = state.revision;
    }
    for mut bar in &mut bars {
        bar.width = percent(state.progress.percent as f32);
    }
    for (mut text, status, local, progress, console) in &mut labels {
        let value = if status.is_some() {
            state.status.lines().next().unwrap_or("").to_owned()
        } else if local.is_some() {
            if state.local_status.is_empty() {
                "Local servers stopped".into()
            } else {
                state.local_status.clone()
            }
        } else if progress.is_some() {
            state.progress_label()
        } else if console.is_some() {
            state.console_log()
        } else {
            continue;
        };
        if text.0 != value {
            if console.is_some() {
                for (mut scroll, node) in &mut log_scroll {
                    let bottom = (node.content_size().y - node.size().y).max(0.)
                        * node.inverse_scale_factor();
                    if scroll.y >= bottom - 24. {
                        scroll.y = bottom + 10000.;
                    }
                }
            }
            text.0 = value;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn operation_console_and_progress_update_without_rebuilding_fields() {
        let mut app = App::new();
        crate::tests::ui_resources(&mut app);
        app.init_resource::<State>()
            .init_resource::<View>()
            .add_systems(Update, (refresh, server_controls).chain());
        {
            let mut state = app.world_mut().resource_mut::<State>();
            state.open = true;
            state.tab = Tab::Server;
        }
        app.update();
        {
            let world = app.world_mut();
            assert_eq!(
                *world
                    .query_filtered::<&Visibility, With<LocalReady>>()
                    .single(world)
                    .unwrap(),
                Visibility::Hidden
            );
            assert!(
                world
                    .query::<(&ServerAction, Has<bevy::ui::InteractionDisabled>)>()
                    .iter(world)
                    .any(|(action, disabled)| matches!(action.0, Action::RunLocal) && disabled)
            );
            assert!(
                !world
                    .query::<&Name>()
                    .iter(world)
                    .any(|name| name.as_str() == "3  Build")
            );
        }
        {
            let mut state = app.world_mut().resource_mut::<State>();
            state.busy = true;
            state.progress.percent = 55;
            state.progress.stage = "Uploading server bundle".into();
            state.progress.log = "First line\nSecond line\n".into();
        }
        app.update();
        let world = app.world_mut();
        assert_eq!(
            world
                .query_filtered::<&Node, With<ProgressBar>>()
                .single(world)
                .unwrap()
                .width,
            percent(55.)
        );
        assert!(
            world
                .query_filtered::<&Text, With<ConsoleLog>>()
                .single(world)
                .unwrap()
                .0
                .contains("Second line")
        );
        assert!(
            world
                .query_filtered::<&Text, With<ProgressLabel>>()
                .single(world)
                .unwrap()
                .0
                .contains("elapsed")
        );
        {
            let mut state = world.resource_mut::<State>();
            state.busy = false;
            state.progress.percent = 100;
            state.progress.stage = "Completed".into();
        }
        app.update();
        let world = app.world_mut();
        assert_eq!(
            world
                .query_filtered::<&Text, With<ProgressLabel>>()
                .single(world)
                .unwrap()
                .0,
            "Completed"
        );
    }
    #[test]
    fn gateway_endpoint_follows_server_edits_and_keeps_hosting_origin() {
        let mut app = App::new();
        crate::tests::ui_resources(&mut app);
        app.init_resource::<State>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_systems(Update, input);
        {
            let mut state = app.world_mut().resource_mut::<State>();
            state.open = true;
            state.fields.insert("enabled".into(), "true".into());
            state.fields.insert("server".into(), "127.0.0.1".into());
            state.fields.insert("port".into(), "9001".into());
            state
                .fields
                .insert("gateway_origin".into(), "https://games.example".into());
        }
        let host = app
            .world_mut()
            .spawn((Field("server".into()), EditableText::new("192.0.2.10")))
            .id();
        let port = app
            .world_mut()
            .spawn((Field("port".into()), EditableText::new("9100")))
            .id();
        app.update();
        let fields = &app.world().resource::<State>().fields;
        assert_eq!(fields["gateway_url"], "wss://192.0.2.10:9101");
        assert_eq!(fields["gateway_listen"], "0.0.0.0:9101");
        assert_eq!(fields["gateway_origin"], "https://games.example");
        // Idle frames must preserve an explicit reverse-proxy URL override.
        app.world_mut()
            .resource_mut::<State>()
            .fields
            .insert("gateway_url".into(), "wss://proxy.example/game".into());
        app.update();
        assert_eq!(
            app.world().resource::<State>().fields["gateway_url"],
            "wss://proxy.example/game"
        );
        for invalid in ["", "0", "65535", "70000"] {
            *app.world_mut().get_mut::<EditableText>(port).unwrap() = EditableText::new(invalid);
            app.update();
            assert_eq!(
                app.world().resource::<State>().fields["gateway_listen"],
                "0.0.0.0:9101"
            );
        }
        *app.world_mut().get_mut::<EditableText>(port).unwrap() = EditableText::new("9001");
        *app.world_mut().get_mut::<EditableText>(host).unwrap() = EditableText::new("::1");
        app.update();
        assert_eq!(
            app.world().resource::<State>().fields["gateway_url"],
            "wss://[::1]:9002"
        );
        assert_eq!(
            app.world().resource::<State>().fields["gateway_listen"],
            "[::1]:9002"
        );
        *app.world_mut().get_mut::<EditableText>(host).unwrap() = EditableText::new("localhost");
        app.update();
        assert_eq!(
            app.world().resource::<State>().fields["gateway_listen"],
            "127.0.0.1:9002"
        );
    }
    #[test]
    fn gateway_fields_retain_edits_without_recreating_the_dialog() {
        let mut app = App::new();
        crate::tests::ui_resources(&mut app);
        app.init_resource::<State>()
            .init_resource::<View>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_systems(Update, (input, refresh).chain());
        app.world_mut().resource_mut::<State>().open = true;
        app.world_mut().resource_mut::<State>().tab = Tab::Browser;
        app.world_mut()
            .resource_mut::<State>()
            .fields
            .insert("enabled".into(), "true".into());
        app.update();
        let world = app.world_mut();
        let field = world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, f)| f.0 == "gateway_url")
            .unwrap()
            .0;
        *world.get_mut::<EditableText>(field).unwrap() =
            EditableText::new("wss://network.example.com/play");
        app.update();
        assert_eq!(
            app.world().resource::<State>().fields["gateway_url"],
            "wss://network.example.com/play"
        );
        assert!(app.world().get::<Field>(field).is_some());
        for tab in [Tab::Deployment, Tab::Server, Tab::Browser] {
            {
                let mut state = app.world_mut().resource_mut::<State>();
                state.tab = tab;
                state.revision += 1;
            }
            app.update();
            let world = app.world_mut();
            let names: Vec<_> = world
                .query::<&Field>()
                .iter(world)
                .map(|f| f.0.clone())
                .collect();
            assert_eq!(
                names.contains(&"gateway_url".to_owned()),
                tab == Tab::Browser
            );
            assert!(!names.contains(&"platform".to_owned()));
            assert_eq!(
                names.contains(&"remote_host".to_owned()),
                tab == Tab::Deployment
            );
        }
        assert_eq!(
            app.world().resource::<State>().fields["gateway_url"],
            "wss://network.example.com/play"
        );
    }
    #[test]
    fn multiplayer_settings_retain_fields_and_block_editor_shortcuts() {
        let (mut app, _dir, _) = crate::tests::app();
        {
            let mut state = app.world_mut().resource_mut::<State>();
            state.open = true;
            state.revision = 1;
            state.fields.insert("enabled".into(), "true".into());
            state.fields.insert("server".into(), "127.0.0.1".into());
        }
        app.update();
        app.update();
        let world = app.world_mut();
        let field = world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, f)| f.0 == "server")
            .unwrap()
            .0;
        *world.get_mut::<EditableText>(field).unwrap() = EditableText::new("192.0.2.10");
        app.update();
        app.update();
        assert_eq!(
            app.world().resource::<State>().fields["server"],
            "192.0.2.10"
        );
        assert!(app.world().get::<Field>(field).is_some());
        let status = app
            .world()
            .resource::<crate::application::Session>()
            .status
            .clone();
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::F8);
        app.update();
        assert_eq!(
            app.world().resource::<crate::application::Session>().status,
            status
        );
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push_back(Action::Close);
        app.update();
        assert!(app.world().get_entity(field).is_err());
    }
}

#[cfg(test)]
mod project_mode_tests {
    use super::*;
    #[test]
    fn unchecked_game_disables_fields_and_checkbox_reenables_them() {
        let mut app = App::new();
        crate::tests::ui_resources(&mut app);
        app.init_resource::<State>()
            .init_resource::<View>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_systems(Update, (input, refresh).chain());
        app.world_mut().resource_mut::<State>().open = true;
        app.update();
        let world = app.world_mut();
        let fields: Vec<_> = world
            .query_filtered::<Entity, With<Field>>()
            .iter(world)
            .collect();
        assert!(!fields.is_empty());
        assert!(
            fields
                .iter()
                .all(|e| world.get::<bevy::ui::InteractionDisabled>(*e).is_some())
        );
        let toggle = world
            .query_filtered::<Entity, With<MultiplayerToggle>>()
            .single(world)
            .unwrap();
        world.get_mut::<property::Checked>(toggle).unwrap().0 = true;
        app.update();
        assert!(app.world().resource::<State>().enabled());
        let world = app.world_mut();
        assert!(
            world
                .query_filtered::<Has<bevy::ui::InteractionDisabled>, With<Field>>()
                .iter(world)
                .all(|disabled| !disabled)
        );
    }
}

pub(crate) fn server_controls(
    mut c: Commands,
    state: Res<State>,
    buttons: Query<(Entity, &ServerAction, &Children)>,
    mut colors: Query<&mut TextColor, Without<LocalReady>>,
    mut checks: Query<&mut Visibility, With<LocalReady>>,
) {
    for (entity, action, children) in &buttons {
        if !matches!(action.0, Action::RunLocal | Action::Build) {
            continue;
        }
        let enabled = state.action_enabled(action.0);
        if enabled {
            c.entity(entity).remove::<bevy::ui::InteractionDisabled>();
        } else {
            c.entity(entity).insert(bevy::ui::InteractionDisabled);
        }
        for child in children.iter() {
            if let Ok(mut color) = colors.get_mut(child) {
                color.0 = if enabled { INK } else { MUTED.with_alpha(0.45) };
            }
        }
    }
    for mut visibility in &mut checks {
        *visibility = if state.local_ready() {
            Visibility::Visible
        } else {
            Visibility::Hidden
        };
    }
}
