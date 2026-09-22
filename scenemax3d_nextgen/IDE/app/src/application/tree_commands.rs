//! Path-targeted navigator commands never depend on the active editor tab.
use super::jobs::SavePurpose;
use super::{Command, EditorServices, Session};
use anyhow::{Result, bail};
use scenemax_ide_services::{StorageRequest, TreeOperation};

pub(super) fn apply(
    outcome: scenemax_ide_services::TreeOutcome,
    session: &mut Session,
    changes: &mut bevy::prelude::MessageWriter<super::ViewChange>,
) -> Result<()> {
    use super::ViewChange;
    for (source, target) in outcome.paths {
        let affected = session
            .workspace
            .documents()
            .filter(|(_, d)| d.path().starts_with(&source))
            .map(|(id, _)| id)
            .collect::<Vec<_>>();
        for id in affected {
            if let Some(target) = &target {
                let doc = session.workspace.document_mut(id)?;
                let suffix = doc.path().strip_prefix(&source)?;
                doc.relocate(if suffix.as_os_str().is_empty() {
                    target.clone()
                } else {
                    target.join(suffix)
                });
                changes.write(ViewChange::DocumentClosed(id));
                changes.write(ViewChange::DocumentOpened(id));
            } else if !session.workspace.document(id)?.is_dirty() {
                session.workspace.close_document(id)?;
                changes.write(ViewChange::DocumentClosed(id));
            } else {
                session.status =
                    "Removed file has newer unsaved edits; its buffer was retained. Use Save Copy."
                        .into();
            }
        }
    }
    if let Some(document) = outcome.document {
        let id = session.workspace.open_document(document)?;
        changes.write(ViewChange::DocumentOpened(id));
    }
    changes.write(ViewChange::ActiveChanged);
    changes.write(ViewChange::ProjectIndexInvalidated);
    Ok(())
}

pub(super) fn execute(
    command: &Command,
    session: &mut Session,
    services: &mut EditorServices,
) -> Option<Result<()>> {
    if !matches!(
        command,
        Command::Tree(_)
            | Command::SavePath(_)
            | Command::RunPath(_)
            | Command::ReloadPath(_)
            | Command::Explore(_)
    ) {
        return None;
    }
    Some((|| {
        let root = session.workspace.project().root().to_owned();
        match command {
            Command::SavePath(path) => {
                if let Some(id) = session.workspace.find_document(path) {
                    services.save(session, vec![id], SavePurpose::Save)?;
                } else {
                    session.status = "File has no unsaved changes".into();
                }
            }
            Command::RunPath(path) => {
                if session.workspace.project().run_target(path).as_deref() != Some(path.as_path()) {
                    bail!("The selected file is not a runnable script");
                }
                services.projector.validate()?;
                let ids = session
                    .workspace
                    .documents()
                    .filter(|(_, d)| d.is_dirty())
                    .map(|(id, _)| id)
                    .collect();
                services.save(session, ids, SavePurpose::PathRun(path.clone()))?;
            }
            Command::ReloadPath(path) => {
                let version = session.workspace.find_document(path).and_then(|id| {
                    session
                        .workspace
                        .document(id)
                        .ok()
                        .map(|doc| (id, doc.revision()))
                });
                services.storage.request(StorageRequest::Reload {
                    root,
                    path: path.clone(),
                    version,
                })?;
                session.status = "Reloading selected file…".into();
            }
            Command::Explore(path) => {
                services
                    .storage
                    .request(StorageRequest::Explore(path.clone()))?;
            }
            Command::Tree(operation) => {
                if services.projector.is_running() {
                    bail!("Stop the game before changing project files");
                }
                if services.is_saving() {
                    bail!("Wait for the current save to finish");
                }
                let affected = match operation {
                    TreeOperation::Move { path, .. } | TreeOperation::Delete(path) => Some(path),
                    _ => None,
                };
                if let Some(path) = affected {
                    // A companion script may be deleted with its designer document.
                    if session.workspace.documents().any(|(_, d)| {
                        d.is_dirty()
                            && (d.path().starts_with(path) || d.path().parent() == path.parent())
                    }) {
                        bail!("Save the affected documents before changing these files");
                    }
                }
                services.storage.request(StorageRequest::Tree {
                    root,
                    operation: operation.clone(),
                })?;
                session.status = "Updating project files…".into();
            }
            _ => unreachable!(),
        }
        Ok(())
    })())
}
