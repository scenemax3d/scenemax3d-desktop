//! Owned, cancellable Git jobs. All filesystem and CLI work stays off the UI thread.
mod process;
mod repository;
use scenemax_ide_core::{
    Document, DocumentId,
    git::{Operation, Snapshot},
};
use std::{
    path::PathBuf,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
        mpsc,
    },
    thread::JoinHandle,
};

/// Completed Git job, including disk documents after working-tree operations.
pub struct Outcome {
    /// Operation success/output or actionable failure.
    pub result: Result<String, String>,
    /// Updated repository summary, also returned after conflicts.
    pub snapshot: Result<Snapshot, String>,
    /// Original snapshot and newly read document; missing files are reported separately.
    pub documents: Vec<(DocumentId, Document, Result<Document, String>)>,
    /// Chosen clone parent directory.
    pub folder: Option<PathBuf>,
}
struct Context {
    #[cfg(test)]
    global: Option<PathBuf>,
    cancel: Arc<AtomicBool>,
    output: Arc<Mutex<String>>,
    progress: bool,
}
impl Context {
    fn check(&self) -> Result<(), String> {
        if self.cancel.load(Ordering::Relaxed) {
            Err("Git operation cancelled; inspect status before retrying".into())
        } else {
            Ok(())
        }
    }
    fn log(&self, value: &str) {
        if let Ok(mut text) = self.output.lock() {
            *text = value.into();
        }
    }
}
/// Single-job worker with bounded output and explicit cancellation/cleanup.
#[derive(Default)]
pub struct Worker {
    receiver: Option<Mutex<mpsc::Receiver<Outcome>>>,
    handle: Option<JoinHandle<()>>,
    cancel: Arc<AtomicBool>,
    output: Arc<Mutex<String>>,
}
impl Worker {
    /// Submit one operation against the captured project and document snapshots.
    pub fn start(
        &mut self,
        root: PathBuf,
        operation: Operation,
        documents: Vec<(DocumentId, Document)>,
        limit: usize,
    ) -> Result<(), String> {
        if self.receiver.is_some() {
            return Err("A Git operation is already running".into());
        }
        self.cancel = Arc::new(AtomicBool::new(false));
        self.output = Arc::new(Mutex::new(String::new()));
        let context = Context {
            #[cfg(test)]
            global: None,
            cancel: self.cancel.clone(),
            output: self.output.clone(),
            progress: matches!(
                operation,
                Operation::Clone(..)
                    | Operation::Push
                    | Operation::Pull(_)
                    | Operation::Fetch
                    | Operation::Commit(_, true)
            ),
        };
        let (tx, rx) = mpsc::sync_channel(1);
        self.handle = Some(
            std::thread::Builder::new()
                .name("ide-git".into())
                .spawn(move || {
                    let result = repository::execute(&root, &operation, &context);
                    let folder = if matches!(operation, Operation::Browse) {
                        result
                            .as_ref()
                            .ok()
                            .filter(|s| !s.is_empty())
                            .map(PathBuf::from)
                    } else {
                        None
                    };
                    let snapshot = if context.cancel.load(Ordering::Relaxed) {
                        Err("Refresh Git status after cancellation".into())
                    } else if matches!(operation, Operation::Browse) {
                        Ok(Snapshot::default())
                    } else {
                        let inspect = Context {
                            #[cfg(test)]
                            global: None,
                            progress: false,
                            cancel: context.cancel.clone(),
                            output: context.output.clone(),
                        };
                        repository::inspect(&root, limit, &inspect)
                    };
                    let documents = if operation.changes_files() {
                        let project = crate::Filesystem::open_project(&root);
                        documents
                            .into_iter()
                            .map(|(id, original)| {
                                let read = project.as_ref().map_err(|e| e.to_string()).and_then(
                                    |project| {
                                        crate::Filesystem::open_document(project, original.path())
                                            .map_err(|e| e.to_string())
                                    },
                                );
                                (id, original, read)
                            })
                            .collect()
                    } else {
                        Vec::new()
                    };
                    let _ = tx.send(Outcome {
                        result,
                        snapshot,
                        documents,
                        folder,
                    });
                })
                .map_err(|e| e.to_string())?,
        );
        self.receiver = Some(Mutex::new(rx));
        Ok(())
    }
    /// Poll a completed job without waiting.
    pub fn poll(&mut self) -> Option<Outcome> {
        let result = self.receiver.as_ref()?.lock().ok()?.try_recv().ok()?;
        self.receiver = None;
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
        Some(result)
    }
    /// Request cancellation of this worker's child process only.
    pub fn cancel(&self) {
        self.cancel.store(true, Ordering::Relaxed);
    }
    /// Latest bounded network progress.
    pub fn progress(&self) -> String {
        self.output.lock().map(|s| s.clone()).unwrap_or_default()
    }
}
impl Drop for Worker {
    fn drop(&mut self) {
        self.cancel();
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
    }
}

#[cfg(test)]
mod tests;
