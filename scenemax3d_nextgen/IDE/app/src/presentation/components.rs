use bevy::prelude::*;
use scenemax_ide_core::DocumentId;
#[derive(Component)]
pub(crate) struct Browser;
#[derive(Component)]
pub(crate) struct Tabs;
#[derive(Component)]
pub(crate) struct Editors;
#[derive(Component)]
pub(crate) struct Editor(pub(crate) DocumentId);
#[derive(Component)]
pub(crate) struct Status;
#[derive(Component)]
pub(crate) struct ProjectPath;
#[derive(Component)]
pub(crate) struct ClosePrompt;
#[derive(Component)]
pub(crate) struct TabLabel(pub(crate) DocumentId);

#[derive(Component)]
pub(crate) struct EditorHost(pub(crate) DocumentId);
#[derive(Component)]
pub(crate) struct TabButton(pub(crate) DocumentId);
#[derive(Component)]
pub(crate) struct Gutter(pub(crate) DocumentId);
#[derive(Component)]
pub(crate) struct CaretLabel;
#[derive(Component, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Field {
    ProjectFilter,
    Project,
    Find,
    Replace,
    Line,
    NewScript,
}

#[derive(Component)]
pub(crate) struct RecoveryPrompt;

#[derive(Component)]
pub(crate) struct ConsoleText;
#[derive(Component)]
pub(crate) struct ConsolePane;

#[derive(Component)]
pub(crate) struct SearchResults;

#[derive(Component)]
pub(crate) struct TabClosePrompt;
#[derive(Component)]
pub(crate) struct TabCloseCaption;
