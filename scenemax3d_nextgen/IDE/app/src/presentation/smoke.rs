//! Explicitly requested GPU smoke capture; absent in normal sessions.
use bevy::prelude::*;
use std::path::PathBuf;

#[derive(Resource)]
pub(crate) struct SmokeCapture {
    pub(crate) remaining: u32,
    pub(crate) path: Option<PathBuf>,
    pub(crate) show_projects: bool,
    pub(crate) run_project: bool,
    pub(crate) show_completion: bool,
    pub(crate) scene_entry: Option<usize>,
}

pub(crate) fn smoke_capture(
    mut commands: Commands,
    capture: Option<ResMut<SmokeCapture>>,
    mut exit: MessageWriter<AppExit>,
    services: Res<crate::application::EditorServices>,
    mut queue: ResMut<crate::application::CommandQueue>,
    mut chrome: ResMut<super::chrome::ChromeState>,
    mut completion: SmokeCompletion,
) {
    let Some(mut capture) = capture else {
        return;
    };
    if !services.storage.is_pending() && !services.catalog_storage.is_pending() {
        if capture.show_completion
            && let Ok((entity, mut input)) = completion.editors.single_mut()
        {
            input.queue_edit(bevy::text::TextEdit::TextEnd(false));
            completion.state.preview(entity);
            capture.show_completion = false;
        }
        if capture.show_projects {
            chrome.preview_projects();
            capture.show_projects = false;
        }
        if capture.run_project {
            queue.0.push_back(crate::application::Command::RunProject);
            capture.run_project = false;
        }
    }
    if capture.remaining == 30
        && let Some(path) = capture.path.take()
    {
        commands
            .spawn(bevy::render::view::screenshot::Screenshot::primary_window())
            .observe(bevy::render::view::screenshot::save_to_disk(path));
    }
    capture.remaining = capture.remaining.saturating_sub(1);
    if capture.remaining == 0 {
        exit.write(AppExit::Success);
    }
}

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct SmokeCompletion<'w, 's> {
    state: ResMut<'w, super::completion::CompletionState>,
    editors: Query<
        'w,
        's,
        (Entity, &'static mut bevy::text::EditableText),
        With<super::components::Editor>,
    >,
}
