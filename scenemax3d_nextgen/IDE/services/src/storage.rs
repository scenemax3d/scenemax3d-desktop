//! Bounded disk worker. Owned snapshots never borrow live editor state.
use crate::{Filesystem, ServiceError};
use scenemax_ide_core::{Document, DocumentId, Project};
use std::{
    path::PathBuf,
    sync::{Mutex, mpsc},
    thread,
};

/// A single owned filesystem operation submitted without blocking the caller.
pub enum StorageRequest {
    /// Load material editor resources.
    MaterialLibrary(PathBuf),
    /// Mutate project files on the disk worker.
    Tree {
        /// Canonical project root.
        root: PathBuf,
        /// Requested operation.
        operation: crate::TreeOperation,
    },
    /// Reload a document after an explicit application-level decision.
    Reload {
        /// Canonical project root.
        root: PathBuf,
        /// Requested path.
        path: PathBuf,
        /// Existing buffer and revision, if open.
        version: Option<(DocumentId, scenemax_ide_core::DocumentRevision)>,
    },
    /// Open a containing directory using the desktop shell.
    Explore(PathBuf),
    /// Open an HTTPS project link in the default browser.
    OpenWeb(String),
    /// Load a Java 3D scene and resolve its project model resources.
    Scene3d {
        /// Canonical project root.
        root: PathBuf,
        /// Immutable scene source.
        source: String,
    },
    /// Persist the previous project navigation before switching projects.
    SwitchProject {
        /// Current canonical project root.
        previous: PathBuf,
        /// Navigation snapshot of the previous project.
        workspace: crate::workspace_state::WorkspaceState,
        /// Destination project root.
        root: PathBuf,
    },
    /// Read the Java-compatible project catalog on a worker.
    Catalog {
        /// Installation/project directory from which to discover the catalog.
        root: PathBuf,
        /// Optional application last-project state for startup selection.
        last_project: Option<PathBuf>,
        /// Explicit catalog path, if supplied.
        path: Option<PathBuf>,
    },
    /// Save a copy into a new script without modifying the original buffer.
    SaveCopy {
        /// Canonical project directory.
        root: PathBuf,
        /// New script name relative to scripts/.
        path: PathBuf,
        /// Immutable source snapshot.
        document: Document,
    },
    /// Search a project inventory, using unsaved snapshots for open documents.
    Search {
        /// Canonical project directory.
        root: PathBuf,
        /// Script inventory to search.
        paths: Vec<PathBuf>,
        /// Literal case-sensitive query.
        needle: String,
        /// Open buffer snapshots, which take precedence over disk contents.
        buffers: Vec<Document>,
    },
    /// Read recoverable buffers without changing source files.
    LoadRecovery(PathBuf),
    /// Persist dirty buffers and retire explicitly accepted immutable checkpoints.
    Checkpoint {
        /// Canonical project directory.
        root: PathBuf,
        /// Dirty document snapshots.
        documents: Vec<Document>,
        /// Open tabs and selected document; independent of dirty recovery contents.
        workspace: Option<crate::workspace_state::WorkspaceState>,
        /// Optional application-wide last-project state path.
        last_project: Option<PathBuf>,
        /// Checkpoint paths accepted or discarded by the user.
        retire: Vec<PathBuf>,
    },
    /// Create a new project at a previously nonexistent directory.
    NewProject(PathBuf),
    /// Refresh inventory without replacing buffers.
    Refresh(PathBuf),
    /// Create a script relative to scripts/.
    Create {
        /// Canonical project directory.
        root: PathBuf,
        /// New script name or path under scripts/.
        path: PathBuf,
    },
    /// Discover a project and optionally load its initial script as one operation.
    Project {
        /// Requested project directory.
        root: PathBuf,
        /// Optional script relative to that project.
        script: Option<PathBuf>,
    },
    /// Load a document within the current canonical project root.
    Open {
        /// Canonical project directory.
        root: PathBuf,
        /// Absolute or project-relative script path.
        path: PathBuf,
    },
    /// Save owned copies in order, retaining each individual outcome.
    Save(Vec<(DocumentId, Document)>),
}
/// Completed operations; failures are data and never overwrite live buffers.
pub enum StorageResult {
    /// Project loaded with restored tab order and selection.
    WorkspaceProject(Project, Vec<Document>, Option<PathBuf>),
    /// Saved designer snapshots and freshly generated companion documents.
    Generated {
        /// Per-document save outcomes.
        results: Vec<(DocumentId, Result<Document, ServiceError>)>,
        /// Successfully regenerated code files.
        companions: Vec<Document>,
    },
    /// Material resources resolved on the worker.
    MaterialLibrary(Result<crate::material::Library, String>),
    /// Result of a navigator mutation; refresh is requested separately.
    Tree(Result<crate::TreeOutcome, ServiceError>),
    /// Reload result with the original buffer version for race protection.
    Reload(
        Option<(DocumentId, scenemax_ide_core::DocumentRevision)>,
        Result<Document, ServiceError>,
    ),
    /// Result of launching the desktop file manager.
    Explored(Result<(), ServiceError>),
    /// Result of opening a web link.
    WebOpened(Result<(), ServiceError>),
    /// Imported scene and resource diagnostics.
    Scene3d(Result<crate::scene3d::Scene3d, String>),
    /// Available projects, independently of an open project.
    Catalog(Result<scenemax_ide_core::ProjectCatalog, ServiceError>),
    /// Results of project-wide literal search.
    Search(Result<crate::SearchReport, ServiceError>),
    /// Recovered buffers awaiting the user's decision.
    RecoveryLoaded(Result<crate::RecoveryBatch, ServiceError>),
    /// Completion of a durable recovery checkpoint.
    Checkpoint(Result<(), ServiceError>),
    /// Refreshed project inventory.
    Refreshed(Result<Project, ServiceError>),
    /// New script and refreshed inventory.
    Created(Result<(Project, Document), ServiceError>),
    /// Loaded project with optional initial document, or a filesystem error.
    Project(Result<(Project, Option<Document>), ServiceError>),
    /// Loaded document or error.
    Open(Result<Document, ServiceError>),
    /// Successful snapshots and failures, in request order, including partial success.
    Saved(Vec<(DocumentId, Result<Document, ServiceError>)>),
}
/// One worker with at most one outstanding operation, including unconsumed results.
/// Dropping the service disconnects its channels; in-progress writes finish on the
/// worker. The application must await writes before orderly process exit.
pub struct Storage {
    requests: mpsc::SyncSender<StorageRequest>,
    results: Mutex<mpsc::Receiver<StorageResult>>,
    pending: bool,
}
impl Storage {
    /// Start the disk worker. No filesystem work executes on the submitting thread.
    pub fn new() -> Result<Self, ServiceError> {
        let (requests, incoming) = mpsc::sync_channel::<StorageRequest>(1);
        let (outgoing, results) = mpsc::sync_channel(1);
        thread::Builder::new()
            .name("ide-files".into())
            .spawn(move || {
                let mut journal = crate::recovery::RecoveryJournal::default();
                while let Ok(request) = incoming.recv() {
                    let result = perform(request, &mut journal);
                    if outgoing.send(result).is_err() {
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
    /// Whether another request or its unconsumed result owns the worker slot.
    pub fn is_pending(&self) -> bool {
        self.pending
    }
    /// Submit without waiting. Repeated actions cannot grow an unbounded queue.
    pub fn request(&mut self, request: StorageRequest) -> Result<(), ServiceError> {
        if self.pending {
            return Err(ServiceError::Busy("File operation"));
        }
        self.requests
            .try_send(request)
            .map_err(|_| ServiceError::StorageUnavailable)?;
        self.pending = true;
        Ok(())
    }
    /// Take a completed result. Only poll while an operation is outstanding.
    pub fn poll(&mut self) -> Result<Option<StorageResult>, ServiceError> {
        if !self.pending {
            return Ok(None);
        }
        match self
            .results
            .get_mut()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .try_recv()
        {
            Ok(result) => {
                self.pending = false;
                Ok(Some(result))
            }
            Err(mpsc::TryRecvError::Empty) => Ok(None),
            Err(mpsc::TryRecvError::Disconnected) => {
                self.pending = false;
                Err(ServiceError::StorageUnavailable)
            }
        }
    }
}
fn perform(
    request: StorageRequest,
    journal: &mut crate::recovery::RecoveryJournal,
) -> StorageResult {
    match request {
        StorageRequest::SwitchProject {
            previous,
            workspace,
            root,
        } => {
            if let Err(e) = crate::workspace_state::save(&previous, &workspace, None) {
                return StorageResult::Project(Err(e));
            }
            perform(StorageRequest::Project { root, script: None }, journal)
        }
        StorageRequest::Tree { root, operation } => {
            StorageResult::Tree(crate::tree_operations::perform(&root, operation))
        }
        StorageRequest::Reload {
            root,
            path,
            version,
        } => StorageResult::Reload(
            version,
            Filesystem::open_document(&Project::new(root, vec![]), &path),
        ),
        StorageRequest::OpenWeb(url) => StorageResult::WebOpened((|| {
            if !url.starts_with("https://") || url.chars().any(char::is_control) {
                return Err(ServiceError::Limit("Expected an HTTPS link"));
            }
            #[cfg(windows)]
            let program = "explorer.exe";
            #[cfg(target_os = "macos")]
            let program = "open";
            #[cfg(all(not(windows), not(target_os = "macos")))]
            let program = "xdg-open";
            std::process::Command::new(program).arg(&url).spawn()?;
            Ok(())
        })()),
        StorageRequest::Explore(path) => StorageResult::Explored((|| {
            let folder = if path.is_dir() {
                path.as_path()
            } else {
                path.parent()
                    .ok_or(ServiceError::Limit("No containing directory"))?
            };
            #[cfg(windows)]
            let program = "explorer.exe";
            #[cfg(target_os = "macos")]
            let program = "open";
            #[cfg(all(not(windows), not(target_os = "macos")))]
            let program = "xdg-open";
            std::process::Command::new(program).arg(folder).spawn()?;
            Ok(())
        })()),
        StorageRequest::MaterialLibrary(root) => {
            StorageResult::MaterialLibrary(crate::material::load(&root))
        }
        StorageRequest::Scene3d { root, source } => {
            StorageResult::Scene3d(crate::scene3d::load(&root, &source))
        }
        StorageRequest::Catalog {
            root,
            path,
            last_project,
        } => StorageResult::Catalog(crate::catalog::load_catalog(&root, path.as_deref()).map(
            |mut catalog| {
                if let Some(selected) = last_project
                    .as_deref()
                    .and_then(crate::workspace_state::last_project)
                {
                    catalog.selected = Some(selected);
                }
                catalog
            },
        )),
        StorageRequest::SaveCopy {
            root,
            path,
            document,
        } => StorageResult::Created((|| {
            let project = Project::new(root.clone(), vec![]);
            let doc = Filesystem::save_copy(&project, &path, &document)?;
            Ok((Filesystem::open_project(&root)?, doc))
        })()),
        StorageRequest::Search {
            root,
            paths,
            needle,
            buffers,
        } => StorageResult::Search(crate::search::search(root, paths, &needle, buffers)),
        StorageRequest::LoadRecovery(root) => {
            StorageResult::RecoveryLoaded(crate::recovery::RecoveryJournal::load(&root))
        }
        StorageRequest::Checkpoint {
            root,
            documents,
            retire,
            workspace,
            last_project,
        } => StorageResult::Checkpoint((|| {
            if let Some(state) = workspace {
                crate::workspace_state::save(&root, &state, last_project.as_deref())?;
            }
            journal.checkpoint(&root, documents, retire)
        })()),
        StorageRequest::NewProject(root) => {
            StorageResult::Project(Filesystem::create_project(&root).map(|(p, d)| (p, Some(d))))
        }
        StorageRequest::Refresh(root) => StorageResult::Refreshed(Filesystem::open_project(&root)),
        StorageRequest::Create { root, path } => StorageResult::Created((|| {
            let project = Project::new(root.clone(), vec![]);
            let doc = Filesystem::create_document(&project, &path)?;
            Ok((Filesystem::open_project(&root)?, doc))
        })()),
        StorageRequest::Project { root, script } => {
            let project = match Filesystem::open_project(&root) {
                Ok(p) => p,
                Err(e) => return StorageResult::Project(Err(e)),
            };
            if let Some((mut docs, mut active)) = crate::workspace_state::restore(&project) {
                if let Some(path) = script {
                    match Filesystem::open_document(&project, &path) {
                        Ok(doc) => {
                            active = Some(doc.path().to_owned());
                            if !docs.iter().any(|d| d.path() == doc.path()) {
                                docs.push(doc);
                            }
                        }
                        Err(e) => return StorageResult::Project(Err(e)),
                    }
                }
                StorageResult::WorkspaceProject(project, docs, active)
            } else {
                let script = script.or_else(|| project.entry_point().map(PathBuf::from));
                StorageResult::Project(
                    script
                        .map(|p| Filesystem::open_document(&project, &p))
                        .transpose()
                        .map(|doc| (project, doc)),
                )
            }
        }
        StorageRequest::Open { root, path } => StorageResult::Open(Filesystem::open_document(
            &Project::new(root, vec![]),
            &path,
        )),
        StorageRequest::Save(mut documents) => {
            // Save init/end buffers before generating any designer in Save All.
            documents.sort_by_key(|(_, doc)| {
                doc.path()
                    .extension()
                    .is_some_and(|e| e.eq_ignore_ascii_case("smdesign"))
            });
            let generated_paths = documents
                .iter()
                .filter(|(_, doc)| {
                    doc.path()
                        .extension()
                        .is_some_and(|e| e.eq_ignore_ascii_case("smdesign"))
                })
                .map(|(_, doc)| doc.path().with_extension("code"))
                .collect::<Vec<_>>();
            documents.retain(|(_, doc)| {
                doc.is_dirty() || !generated_paths.iter().any(|p| p == doc.path())
            });
            let mut companions = Vec::new();
            let mut failed = false;
            let results = documents
                .into_iter()
                .map(|(id, mut doc)| {
                    let designer = doc
                        .path()
                        .extension()
                        .is_some_and(|e| e.eq_ignore_ascii_case("smdesign"));
                    let result = if failed && designer {
                        Err(ServiceError::Limit(
                            "Designer save cancelled because another document failed to save",
                        ))
                    } else {
                        crate::scene_save::save(&mut doc).map(|generated| {
                            if let Some(generated) = generated {
                                companions.push(generated);
                            }
                            doc
                        })
                    };
                    failed |= result.is_err();
                    (id, result)
                })
                .collect();
            if companions.is_empty() {
                StorageResult::Saved(results)
            } else {
                StorageResult::Generated {
                    results,
                    companions,
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{Duration, Instant};
    fn finish(storage: &mut Storage) -> StorageResult {
        let deadline = Instant::now() + Duration::from_secs(5);
        loop {
            if let Some(result) = storage.poll().unwrap() {
                return result;
            }
            assert!(Instant::now() < deadline);
            thread::sleep(Duration::from_millis(1));
        }
    }
    #[test]
    fn bounded_worker_loads_initial_script_and_recovers_after_failed_request() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        std::fs::write(dir.path().join("scripts/main"), "// source").unwrap();
        let mut storage = Storage::new().unwrap();
        storage
            .request(StorageRequest::Project {
                root: dir.path().join("missing"),
                script: None,
            })
            .unwrap();
        assert!(matches!(
            storage.request(StorageRequest::Save(vec![])),
            Err(ServiceError::Busy(_))
        ));
        assert!(matches!(
            finish(&mut storage),
            StorageResult::Project(Err(_))
        ));
        storage
            .request(StorageRequest::Project {
                root: dir.path().to_owned(),
                script: Some("scripts/main".into()),
            })
            .unwrap();
        match finish(&mut storage) {
            StorageResult::Project(Ok((project, Some(document)))) => {
                assert_eq!(project.scripts().len(), 1);
                assert_eq!(document.text(), "// source");
            }
            _ => panic!("Expected loaded project and script"),
        }
        assert!(!storage.is_pending());
    }
}
