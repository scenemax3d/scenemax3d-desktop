use bevy::prelude::*;
use scenemax_ide_core::{DocumentId, EditorWorkspace, Project};

#[derive(Resource)]
pub(crate) struct Session {
    pub(crate) workspace: EditorWorkspace,
    pub(crate) catalog: scenemax_ide_core::ProjectCatalog,
    pub(crate) status: String,
    pub(crate) output: std::collections::VecDeque<String>,
    pub(crate) output_revision: u64,
    pub(crate) closing: bool,
    pub(crate) restarting: bool,
    pub(crate) restart_project: std::sync::Arc<std::sync::Mutex<Option<std::path::PathBuf>>>,
    pub(crate) closing_tab: Option<DocumentId>,
    pub(crate) composing: bool,
    pub(crate) recoverable: usize,
    pub(crate) search_hits: Vec<scenemax_ide_core::SearchHit>,
}
impl Session {
    pub(crate) fn new(project: Project) -> Self {
        Self { workspace: EditorWorkspace::new(project), catalog: Default::default(), closing: false, restarting: false, restart_project: Default::default(), closing_tab: None, composing: false, recoverable: 0, search_hits: vec![], output: Default::default(), output_revision: 0,
            status: "Open a script. Ctrl+S: save | Ctrl+Enter: syntax check | F5: run active script | Shift+F5: stop".into() }
    }
    pub(crate) fn append_output(&mut self, message: &str) {
        for line in message.lines() {
            self.output.push_back(line.chars().take(2000).collect());
            while self.output.len() > 2000 {
                self.output.pop_front();
            }
        }
        self.output_revision = self.output_revision.saturating_add(1);
    }
}

#[derive(Message)]
pub(crate) enum ViewChange {
    CatalogChanged,
    DocumentOpened(DocumentId),
    DocumentClosed(DocumentId),
    BufferChanged(DocumentId),
    SearchResultsChanged,
    ProjectOpened,
    ActiveChanged,
    ProjectTreeChanged,
    ProjectIndexInvalidated,
    MaterialsChanged,
}
