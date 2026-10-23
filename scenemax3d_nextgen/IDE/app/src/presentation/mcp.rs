//! Retained MCP connection/status monitor.
use crate::application::mcp::State;
use bevy::prelude::*;
use scenemax_ide_ui::{button, label, theme::*};
#[derive(Component)]
pub(crate) struct Monitor;
#[derive(Component)]
pub(crate) struct Connection;
#[derive(Component)]
pub(crate) struct Log;
#[derive(Component)]
pub(crate) struct Toggle;
#[derive(Component)]
pub(crate) struct UdpStatus;
pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    connections: Query<Entity, With<Connection>>,
    panels: Query<Entity, With<Monitor>>,
    mut logs: Query<&mut Text, (With<Log>, Without<UdpStatus>)>,
    multiplayer: Res<crate::application::multiplayer::State>,
    mut udp: Query<&mut Text, (With<UdpStatus>, Without<Log>)>,
    buttons: Query<&Interaction, (With<Toggle>, Changed<Interaction>)>,
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
                |_: On<Pointer<Click>>,
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
        button(&mut commands, row, "MCP", Toggle);
        commands.entity(row).insert(Connection);
    }
    if buttons.iter().any(|v| *v == Interaction::Pressed) {
        state.monitor = !state.monitor;
    }
    if !state.monitor {
        for e in &panels {
            commands.entity(e).despawn();
        }
        return;
    }
    let text = format!(
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
    );
    if panels.is_empty() {
        let panel = commands
            .spawn((
                Monitor,
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
        commands.spawn((label("SceneMax MCP Monitor", 18.), ChildOf(panel)));
        button(&mut commands, panel, "Close", Toggle);
        commands.spawn((label(&text, 12.), Log, ChildOf(panel)));
    } else {
        for mut value in &mut logs {
            if value.0 != text {
                value.0 = text.clone();
            }
        }
    }
}
