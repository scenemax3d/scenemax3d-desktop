//! Retained MCP connection/status monitor.
use crate::application::mcp::State;
use bevy::prelude::*;
use bevy::text::{EditableText, FontSource, TextEdit};
use scenemax_ide_ui::{button, label, theme::*};
use scenemax_ui_input::{Button, Interaction};
#[derive(Component)]
pub(crate) struct Monitor(bool);
#[derive(Component)]
pub(crate) struct Connection;
#[derive(Component)]
pub(crate) struct Log;
#[derive(Component)]
pub(crate) struct GameMonitorText;

// Bevy uses the same edit queue for selection, clipboard and typing.
pub(crate) fn keep_game_monitor_read_only(
    mut fields: Query<&mut EditableText, With<GameMonitorText>>,
) {
    for mut field in &mut fields {
        if !field.pending_edits.is_empty() {
            field.pending_edits.retain(|edit| {
                !matches!(
                    edit,
                    TextEdit::Insert(_)
                        | TextEdit::Cut
                        | TextEdit::Paste
                        | TextEdit::Backspace
                        | TextEdit::BackspaceWord
                        | TextEdit::Delete
                        | TextEdit::DeleteWord
                        | TextEdit::ImeSetCompose { .. }
                        | TextEdit::ImeCommit { .. }
                )
            });
        }
    }
}
#[derive(Component)]
pub(crate) enum Toggle {
    Ide,
    Game,
    Close,
}
#[derive(Component)]
pub(crate) struct GameMcpStatus;

#[derive(Resource)]
pub(crate) struct GameMonitor {
    open: bool,
    start_requested: bool,
    status: &'static str,
    activity: String,
    instructions: String,
}
impl GameMonitor {
    pub(crate) fn new(open: bool) -> Self {
        let helper = std::env::current_exe()
            .unwrap_or_default()
            .with_file_name(format!("scenemax_game_mcp{}", std::env::consts::EXE_SUFFIX));
        Self {
            open,
            start_requested: open,
            status: "checking",
            activity: String::new(),
            instructions: format!(
                "Connect your AI agent\nAdd a local MCP server in the agent's settings, then reconnect.\nTransport: stdio\nCommand: {}\nArgument: stdio\n\nRun your game. Press Ctrl+F12 in the game to copy its pairing code. The code appears for one second.\nPaste the code to your agent and ask it to pair. Use run_partial_code to send commands and unpair to disconnect.",
                helper.display()
            ),
        }
    }
}

fn probe_game_service(start: bool) -> Result<Option<String>, String> {
    if let Ok(activity) = scenemax_game_mcp::recent_activity() {
        return Ok(Some(activity));
    }
    if scenemax_game_mcp::serving() {
        return Ok(Some(
            "This service version does not provide activity logs.".into(),
        ));
    }
    if !start {
        return Ok(None);
    }
    let listener = scenemax_game_mcp::listen().map_err(|err| err.to_string())?;
    std::thread::spawn(move || {
        if let Err(error) = scenemax_game_mcp::serve(listener) {
            bevy::log::error!("Game MCP stopped: {error}");
        }
    });
    scenemax_game_mcp::recent_activity()
        .map(Some)
        .map_err(|err| err.to_string())
}

pub(crate) fn update_game_service(
    mut monitor: ResMut<GameMonitor>,
    mut worker: Local<Option<std::thread::JoinHandle<Result<Option<String>, String>>>>,
    mut next_check: Local<Option<std::time::Instant>>,
    mut labels: Query<&mut Text, With<GameMcpStatus>>,
) {
    if worker.as_ref().is_some_and(|task| task.is_finished()) {
        match worker
            .take()
            .unwrap()
            .join()
            .unwrap_or_else(|_| Err("Service check failed".into()))
        {
            Ok(Some(activity)) => {
                monitor.status = "running";
                monitor.activity = activity;
            }
            Ok(None) => {
                if monitor.status != "error" {
                    monitor.status = "stopped";
                }
            }
            Err(error) => {
                monitor.status = "error";
                monitor.activity = error;
            }
        }
        *next_check = Some(std::time::Instant::now() + std::time::Duration::from_secs(2));
    }
    if worker.is_none()
        && (monitor.start_requested || next_check.is_none_or(|t| t <= std::time::Instant::now()))
    {
        let start = std::mem::take(&mut monitor.start_requested);
        if start && monitor.status != "running" {
            monitor.status = "starting";
        }
        *worker = Some(std::thread::spawn(move || probe_game_service(start)));
    }
    for mut label in &mut labels {
        if !label.0.ends_with(monitor.status) {
            label.0 = format!("Game MCP: {}", monitor.status);
        }
    }
}

#[derive(Component)]
pub(crate) struct UdpStatus;
pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    connections: Query<Entity, With<Connection>>,
    panels: Query<(Entity, &Monitor)>,
    mut fields: Query<&mut EditableText, With<GameMonitorText>>,
    mut game: ResMut<GameMonitor>,
    mut logs: Query<&mut Text, (With<Log>, Without<UdpStatus>)>,
    multiplayer: Res<crate::application::multiplayer::State>,
    mut udp: Query<&mut Text, (With<UdpStatus>, Without<Log>)>,
    buttons: Query<(&Interaction, &Toggle), Changed<Interaction>>,
) {
    let udp_text = if multiplayer.local_status.is_empty() {
        "UDP local: stopped"
    } else if multiplayer.local_status.len() > 75 {
        "UDP server error · see Project Settings"
    } else {
        &multiplayer.local_status
    };
    for mut text in &mut udp {
        if text.0 != udp_text {
            text.0 = udp_text.to_owned();
        }
    }
    if connections.is_empty() {
        let row = commands
            .spawn((
                Node {
                    position_type: PositionType::Absolute,
                    right: px(12.),
                    bottom: px(2.),
                    height: px(23.),
                    column_gap: px(10.),
                    align_items: AlignItems::Center,
                    ..default()
                },
                ZIndex(100),
            ))
            .id();
        let udp_button = commands
            .spawn((
                Button,
                Name::new("Open UDP server settings"),
                Node {
                    padding: UiRect::axes(px(5.), px(2.)),
                    align_items: AlignItems::Center,
                    ..default()
                },
                BackgroundColor(Color::NONE),
                scenemax_ide_ui::ButtonSurface(Color::NONE),
                ChildOf(row),
            ))
            .observe(
                |_: On<PointerClick>,
                 mut settings: ResMut<crate::application::multiplayer::State>| {
                    if !settings.open {
                        settings
                            .actions
                            .push_back(crate::application::multiplayer::Action::Open);
                    }
                },
            )
            .id();
        commands.spawn((label(udp_text, 12.), UdpStatus, ChildOf(udp_button)));
        let game_button = commands
            .spawn((
                Button,
                Toggle::Game,
                Name::new("Game MCP"),
                Node {
                    padding: UiRect::axes(px(5.), px(2.)),
                    align_items: AlignItems::Center,
                    ..default()
                },
                BackgroundColor(Color::NONE),
                scenemax_ide_ui::ButtonSurface(Color::NONE),
                ChildOf(row),
            ))
            .id();
        commands.spawn((
            label(&format!("Game MCP: {}", game.status), 12.),
            GameMcpStatus,
            ChildOf(game_button),
        ));
        button(&mut commands, row, "IDE MCP", Toggle::Ide);
        commands.entity(row).insert(Connection);
    }
    for (_, action) in buttons
        .iter()
        .filter(|(input, _)| **input == Interaction::Pressed)
    {
        match action {
            Toggle::Game => {
                game.open = !game.open;
                game.start_requested |= game.open && game.status != "running";
                state.monitor = false;
            }
            Toggle::Ide => {
                state.monitor = !state.monitor;
                game.open = false;
            }
            Toggle::Close => {
                state.monitor = false;
                game.open = false;
            }
        }
    }
    let mut visible = false;
    for (entity, panel) in &panels {
        if (game.open || state.monitor) && panel.0 == game.open {
            visible = true;
        } else {
            commands.entity(entity).despawn();
        }
    }
    if !game.open && !state.monitor {
        return;
    }
    let text = if game.open {
        format!(
            "Status: {} · {}\n\n{}\n\nRecent activity (newest first)\n{}",
            game.status,
            scenemax_game_mcp::ADDRESS,
            game.instructions,
            if game.activity.is_empty() {
                "No activity yet"
            } else {
                &game.activity
            }
        )
    } else {
        format!(
            "{}\n\nLocal automation for the SceneMax choreographic programming language.\n{} tools · MCP Streamable HTTP · /mcp/status for health checks\n\nConfigure SCENEMAX_MCP_PORT (default 8765) before launch.\nOptional SCENEMAX_MCP_TOKEN enables Bearer authentication.\nSCENEMAX_MCP_DISABLED=1 disables the server.\nSee IDE/MCP.md for agent setup, schemas and examples.\n\nRecent requests (metadata only):\n{}",
            state.status,
            scenemax_ide_core::automation::tools().len(),
            state
                .logs()
                .into_iter()
                .rev()
                .take(14)
                .collect::<Vec<_>>()
                .join("\n")
        )
    };
    if !visible {
        let panel = commands
            .spawn((
                Monitor(game.open),
                Node {
                    position_type: PositionType::Absolute,
                    right: px(20.),
                    bottom: px(32.),
                    width: px(760.),
                    max_height: percent(85.),
                    padding: UiRect::all(px(16.)),
                    flex_direction: FlexDirection::Column,
                    row_gap: px(8.),
                    overflow: Overflow::scroll_y(),
                    ..default()
                },
                BackgroundColor(PANEL),
                ZIndex(1000),
            ))
            .id();
        commands.spawn((
            label(
                if game.open {
                    "SceneMax Game MCP Monitor"
                } else {
                    "SceneMax IDE MCP Monitor"
                },
                18.,
            ),
            ChildOf(panel),
        ));
        button(&mut commands, panel, "Close", Toggle::Close);
        if game.open {
            commands.spawn((
                GameMonitorText,
                bevy::input_focus::tab_navigation::TabIndex(0),
                bevy::ui_widgets::TextInput,
                Node {
                    width: percent(100.),
                    ..default()
                },
                EditableText {
                    allow_newlines: true,
                    visible_lines: Some(text.lines().count() as f32 + 2.),
                    ..EditableText::new(&text)
                },
                TextFont {
                    font: FontSource::sans_serif(),
                    font_size: FontSize::Px(12.),
                    ..default()
                },
                TextColor(INK),
                TEXT_CURSOR_STYLE,
                ChildOf(panel),
            ));
        } else {
            commands.spawn((label(&text, 12.), Log, ChildOf(panel)));
        }
    } else if game.open {
        for mut field in &mut fields {
            if field.value() != text.as_str() {
                field.visible_lines = Some(text.lines().count() as f32 + 2.);
                field.editor_mut().set_text(&text);
            }
        }
    } else {
        for mut value in &mut logs {
            if value.0 != text {
                value.0 = text.clone();
            }
        }
    }
}
