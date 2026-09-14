use super::components::*;
use crate::application::Session;
use bevy::prelude::*;
pub(crate) fn refresh_labels(
    session: Res<Session>,
    mut status: Single<&mut Text, (With<Status>, Without<TabLabel>)>,
    mut labels: Query<(&TabLabel, &mut Text), Without<Status>>,
    mut prompt: Single<&mut Node, (With<ClosePrompt>, Without<RecoveryPrompt>)>,
    mut recovery: Single<&mut Node, (With<RecoveryPrompt>, Without<ClosePrompt>)>,
) {
    if !session.is_changed() {
        return;
    }
    if status.0 != session.status {
        status.0.clone_from(&session.status);
    }
    let recovery_display = if session.recoverable > 0 {
        Display::Flex
    } else {
        Display::None
    };
    if recovery.display != recovery_display {
        recovery.display = recovery_display;
    }
    let display = if session.closing {
        Display::Flex
    } else {
        Display::None
    };
    if prompt.display != display {
        prompt.display = display;
    }
    for (label, mut text) in &mut labels {
        if let Ok(doc) = session.workspace.document(label.0) {
            let caption = format!(
                "{}{}{}",
                "",
                doc.path().file_name().unwrap_or_default().to_string_lossy(),
                if doc.is_dirty() { " *" } else { "" }
            );
            if text.0 != caption {
                text.0 = caption;
            }
        }
    }
}

// Bound the rendered tail independently from the retained diagnostic history.
const CONSOLE_LINES: usize = 200;
const CONSOLE_BYTES: usize = 24 * 1024;
fn console_tail(lines: &std::collections::VecDeque<String>) -> String {
    let mut bytes = 0;
    let mut selected = Vec::new();
    for line in lines.iter().rev().take(CONSOLE_LINES) {
        if bytes + line.len() + 1 > CONSOLE_BYTES {
            break;
        }
        bytes += line.len() + 1;
        selected.push(line.as_str());
    }
    let truncated = selected.len() < lines.len();
    selected.reverse();
    let mut text = if truncated {
        "[Showing the latest output]\n".to_owned()
    } else {
        String::new()
    };
    text.push_str(&selected.join("\n"));
    text
}
pub(crate) fn refresh_console(
    session: Res<Session>,
    services: Res<crate::application::EditorServices>,
    mut revision: Local<Option<u64>>,
    mut text: Single<&mut Text, With<ConsoleText>>,
    mut scroll: Single<&mut ScrollPosition, With<ConsolePane>>,
) {
    // Hidden text still participates in text preparation. Never feed a large
    // exit-time log update into a collapsed, zero-width text surface.
    if !services.projector.is_running() {
        if !text.0.is_empty() {
            text.0.clear();
        }
        if scroll.y != 0. {
            scroll.y = 0.;
        }
        *revision = None;
        return;
    }
    if *revision == Some(session.output_revision) {
        return;
    }
    *revision = Some(session.output_revision);
    text.0 = console_tail(&session.output);
    scroll.y = (CONSOLE_LINES as f32 + 1.) * 24.;
}

pub(crate) fn tab_close_prompt(
    session: Res<Session>,
    mut node: Single<&mut Node, With<TabClosePrompt>>,
    mut caption: Single<&mut Text, With<TabCloseCaption>>,
) {
    let display = if session.closing_tab.is_some() {
        Display::Flex
    } else {
        Display::None
    };
    if node.display != display {
        node.display = display;
    }
    if let Some(id) = session.closing_tab
        && let Ok(doc) = session.workspace.document(id)
    {
        let text = format!(
            "Unsaved edits in {}",
            doc.path().file_name().unwrap_or_default().to_string_lossy()
        );
        if caption.0 != text {
            caption.0 = text;
        }
    }
}

#[derive(Component)]
pub(crate) struct RunOutput;
pub(crate) fn output_visibility(
    services: Res<crate::application::EditorServices>,
    mut panels: Query<&mut Node, With<RunOutput>>,
) {
    let display = if services.projector.is_running() {
        Display::Flex
    } else {
        Display::None
    };
    for mut node in &mut panels {
        if node.display != display {
            node.display = display;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn console_layout_is_bounded_and_preserves_latest_unicode_lines() {
        let lines = (0..2000)
            .map(|i| format!("{i}: {}", "🦀".repeat(499)))
            .collect();
        let tail = console_tail(&lines);
        assert!(tail.len() < CONSOLE_BYTES + 100);
        assert!(tail.contains("1999:"));
        assert!(tail.starts_with("[Showing"));
        assert!(!tail.contains("1900:"));
        assert_eq!(lines.len(), 2000);
    }
    #[test]
    fn exited_projector_clears_hidden_text_without_discarding_history() {
        use bevy::ecs::system::RunSystemOnce;
        let mut world = World::new();
        let mut session = Session::new(scenemax_ide_core::Project::new(
            std::path::PathBuf::new(),
            vec![],
        ));
        session.append_output("Projector exited: success");
        world.insert_resource(session);
        world.insert_resource(
            crate::application::EditorServices::new(std::path::PathBuf::new()).unwrap(),
        );
        let console = world
            .spawn((ConsoleText, Text::new("large prior output")))
            .id();
        let pane = world
            .spawn((ConsolePane, ScrollPosition(Vec2::new(0., 10000.))))
            .id();
        world.run_system_once(refresh_console).unwrap();
        assert!(world.get::<Text>(console).unwrap().0.is_empty());
        assert_eq!(world.get::<ScrollPosition>(pane).unwrap().y, 0.);
        assert_eq!(world.resource::<Session>().output.len(), 1);
    }
}
