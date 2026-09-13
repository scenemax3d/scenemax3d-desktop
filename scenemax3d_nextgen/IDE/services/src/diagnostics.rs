use crate::ServiceError;
use scenemax_ide_core::{Document, DocumentId, DocumentRevision, EditorWorkspace};
use std::{
    path::PathBuf,
    sync::{Mutex, mpsc},
    thread,
};

/// Immutable input to background syntax checking.
#[derive(Debug, Clone)]
pub struct SourceSnapshot {
    id: DocumentId,
    revision: DocumentRevision,
    path: PathBuf,
    source: String,
}
impl SourceSnapshot {
    /// Capture the exact source and version associated with a command.
    pub fn new(id: DocumentId, document: &Document) -> Self {
        Self {
            id,
            revision: document.revision(),
            path: document.path().to_owned(),
            source: document.text().to_owned(),
        }
    }
    /// Reject results for edited, closed or previous-project buffers.
    pub fn is_current(&self, workspace: &EditorWorkspace) -> bool {
        workspace
            .document(self.id)
            .is_ok_and(|doc| doc.revision() == self.revision && doc.path() == self.path)
    }
}

/// Result tagged with the source snapshot that produced it.
pub struct SyntaxReport {
    /// Source identity and revision.
    pub snapshot: SourceSnapshot,
    /// Syntax success or parser error, without resource-resolution claims.
    pub message: String,
}

/// One worker and one outstanding request; repeated Check cannot spawn unbounded jobs.
pub struct Diagnostics {
    requests: mpsc::SyncSender<SourceSnapshot>,
    results: Mutex<mpsc::Receiver<SyntaxReport>>,
    pending: bool,
}
impl Diagnostics {
    /// Start a dedicated parser worker. It exits when this service is dropped.
    pub fn new() -> Result<Self, ServiceError> {
        let (requests, incoming) = mpsc::sync_channel::<SourceSnapshot>(1);
        let (outgoing, results) = mpsc::sync_channel(1);
        thread::Builder::new()
            .name("ide-diagnostics".into())
            .spawn(move || {
                while let Ok(snapshot) = incoming.recv() {
                    let result = match scenemax_parser::parse_program(&snapshot.source) {
                        Ok(_) => {
                            "syntax OK (resource resolution is checked by the projector)".into()
                        }
                        Err(error) => error.to_string(),
                    };
                    let message = format!("{}: {result}", snapshot.path.display());
                    if outgoing.send(SyntaxReport { snapshot, message }).is_err() {
                        break;
                    }
                }
            })?;
        Ok(Self {
            requests,
            results: Mutex::new(results),
            pending: false,
        })
    }
    /// Submit an explicit source snapshot without blocking the UI.
    pub fn request(&mut self, snapshot: SourceSnapshot) -> Result<(), ServiceError> {
        if self.pending {
            return Err(ServiceError::Busy("Syntax check"));
        }
        self.requests
            .try_send(snapshot)
            .map_err(|_| ServiceError::WorkerUnavailable)?;
        self.pending = true;
        Ok(())
    }
    /// Receive a completed report. The caller must check its snapshot version.
    pub fn poll(&mut self) -> Result<Option<SyntaxReport>, ServiceError> {
        match self
            .results
            .get_mut()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .try_recv()
        {
            Ok(report) => {
                self.pending = false;
                Ok(Some(report))
            }
            Err(mpsc::TryRecvError::Empty) => Ok(None),
            Err(mpsc::TryRecvError::Disconnected) => {
                self.pending = false;
                Err(ServiceError::WorkerUnavailable)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use scenemax_ide_core::Project;
    #[test]
    fn rejects_results_after_edit_and_project_switch() {
        let mut workspace = EditorWorkspace::new(Project::new("project".into(), vec![]));
        let id = workspace
            .open_document(
                Document::from_bytes("project/main".into(), b"// original".to_vec()).unwrap(),
            )
            .unwrap();
        let snapshot = SourceSnapshot::new(id, workspace.document(id).unwrap());
        assert!(snapshot.is_current(&workspace));
        workspace
            .document_mut(id)
            .unwrap()
            .replace_text("// edit".into());
        assert!(!snapshot.is_current(&workspace));
        workspace.document_mut(id).unwrap().mark_saved();
        let snapshot = SourceSnapshot::new(id, workspace.document(id).unwrap());
        workspace
            .switch_project(Project::new("other".into(), vec![]))
            .unwrap();
        assert!(!snapshot.is_current(&workspace));
    }
    #[test]
    fn sample_scripts_parse_with_the_runtime_parser() {
        for source in [
            include_str!("../../../Tests/fixtures/ide/sample_project/scripts/main"),
            include_str!("../../../Tests/fixtures/ide/sample_project/scripts/notes.code"),
        ] {
            scenemax_parser::parse_program(source).unwrap();
        }
    }
}
