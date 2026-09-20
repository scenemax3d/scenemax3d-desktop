use super::{EditorServices, Session, ViewChange};
use anyhow::{Result, bail};
use bevy::prelude::*;
use scenemax_ide_core::{DocumentId, DocumentRevision, Project};
use scenemax_ide_services::{RecoveryBatch, StorageRequest};
use std::{
    path::PathBuf,
    time::{Duration, Instant},
};
type Stamp = (
    Vec<(DocumentId, DocumentRevision, u64, bool)>,
    scenemax_ide_services::workspace_state::WorkspaceState,
);
pub(crate) struct RecoveryState {
    pub(crate) candidates: Option<RecoveryBatch>,
    pub(crate) retire: Vec<PathBuf>,
    pub(crate) enabled: bool,
    pub(crate) pending: bool,
    pub(crate) exit: Option<bool>,
    pub(crate) stamp: Option<Stamp>,
    checked: Instant,
}
impl Default for RecoveryState {
    fn default() -> Self {
        Self {
            candidates: None,
            retire: vec![],
            enabled: false,
            pending: false,
            exit: None,
            stamp: None,
            checked: Instant::now(),
        }
    }
}
fn stamp(session: &Session) -> Stamp {
    (
        session
            .workspace
            .documents()
            .map(|(id, doc)| (id, doc.revision(), doc.saved_version(), doc.is_dirty()))
            .collect(),
        scenemax_ide_services::workspace_state::WorkspaceState::capture(&session.workspace),
    )
}
impl EditorServices {
    fn checkpoint(&mut self, session: &Session, discard: bool) -> Result<()> {
        let documents = if discard {
            vec![]
        } else {
            session
                .workspace
                .documents()
                .filter(|(_, doc)| doc.is_dirty())
                .map(|(_, doc)| doc.snapshot())
                .collect()
        };
        self.storage.request(StorageRequest::Checkpoint {
            root: session.workspace.project().root().to_owned(),
            documents,
            retire: self.recovery.retire.clone(),
            workspace: Some(
                scenemax_ide_services::workspace_state::WorkspaceState::capture(&session.workspace),
            ),
            last_project: self.last_project_file.clone(),
        })?;
        self.recovery.pending = true;
        self.recovery.stamp = Some(stamp(session));
        Ok(())
    }
    pub(crate) fn finish_session(&mut self, session: &Session, discard: bool) -> Result<()> {
        self.checkpoint(session, true)?;
        self.recovery.exit = Some(discard);
        Ok(())
    }
    pub(crate) fn restore_recovery(
        &mut self,
        session: &mut Session,
        changes: &mut MessageWriter<ViewChange>,
    ) -> Result<()> {
        if self.storage.is_pending() {
            bail!("Wait for the current file operation");
        }
        if session.workspace.has_dirty_documents() {
            bail!("Save your current edits before restoring recovery buffers");
        }
        let Some(batch) = self.recovery.candidates.take() else {
            bail!("No recovery buffers available");
        };
        let project = session.workspace.project();
        let project = Project::new(project.root().to_owned(), project.scripts().to_vec());
        session.workspace.switch_project(project)?;
        session.closing_tab = None;
        session.search_hits.clear();
        changes.write(ViewChange::SearchResultsChanged);
        changes.write(ViewChange::ProjectOpened);
        for document in batch.documents {
            let id = session.workspace.open_document(document)?;
            changes.write(ViewChange::DocumentOpened(id));
        }
        changes.write(ViewChange::ActiveChanged);
        self.recovery.retire.extend(batch.sources);
        session.recoverable = 0;
        session.status = "Recovered unsaved buffers; review them before saving".into();
        self.checkpoint(session, false)?;
        Ok(())
    }
    pub(crate) fn discard_recovery(&mut self, session: &mut Session) -> Result<()> {
        if self.storage.is_pending() {
            bail!("Wait for the current file operation");
        }
        if let Some(batch) = self.recovery.candidates.take() {
            self.recovery.retire.extend(batch.sources);
        }
        session.recoverable = 0;
        self.checkpoint(session, false)?;
        session.status = "Recovery copies discarded; script files were not changed".into();
        Ok(())
    }
}
pub(crate) fn checkpoint_buffers(
    mut services: ResMut<EditorServices>,
    mut session: ResMut<Session>,
) {
    if !services.recovery.enabled
        || services.storage.is_pending()
        || services.recovery.checked.elapsed() < Duration::from_secs(2)
    {
        return;
    }
    services.recovery.checked = Instant::now();
    if services.recovery.stamp.as_ref() == Some(&stamp(&session)) {
        return;
    }
    if let Err(error) = services.checkpoint(&session, false) {
        session.status = format!("Recovery checkpoint failed: {error}");
    }
}
