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

pub(crate) fn refresh_console(
    session: Res<Session>,
    mut revision: Local<u64>,
    mut text: Single<&mut Text, With<ConsoleText>>,
    mut scroll: Single<&mut ScrollPosition, With<ConsolePane>>,
) {
    if *revision == session.output_revision {
        return;
    }
    *revision = session.output_revision;
    text.0 = session
        .output
        .iter()
        .map(String::as_str)
        .collect::<Vec<_>>()
        .join("\n");
    scroll.y = f32::MAX;
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
