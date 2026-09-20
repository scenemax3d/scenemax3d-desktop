use super::{Session, ViewChange};
use anyhow::{Result, bail};
use bevy::prelude::*;
use scenemax_ide_core::DocumentId;
use scenemax_ide_services::{
    Diagnostics, ProjectorProcess, ServiceError, Storage, StorageRequest, StorageResult,
};
use std::path::PathBuf;

#[derive(Clone)]
pub(crate) enum SavePurpose {
    PathRun(PathBuf),
    Save,
    Close,
    Tab(DocumentId),
    Run(DocumentId),
    ProjectRun,
}

#[derive(Resource)]
pub(crate) struct EditorServices {
    pub(crate) diagnostics: Diagnostics,
    pub(crate) symbols: scenemax_ide_services::SymbolIndexer,
    pub(crate) projector: ProjectorProcess,
    pub(crate) storage: Storage,
    pub(crate) catalog_storage: Storage,
    pub(crate) scene_storage: Storage,
    pub(crate) material_storage: Storage,
    pub(crate) catalog_root: PathBuf,
    pub(crate) last_project_file: Option<PathBuf>,
    pub(crate) catalog_path: Option<PathBuf>,
    pub(crate) catalog_startup: Option<(PathBuf, Option<PathBuf>)>,
    pending_save: Option<SavePurpose>,
    pub(crate) navigation: Option<(PathBuf, usize)>,
    pub(crate) search_versions: Vec<(DocumentId, scenemax_ide_core::DocumentRevision)>,
    pub(crate) recovery: super::recovery::RecoveryState,
}
impl EditorServices {
    pub(crate) fn new(executable: PathBuf) -> Result<Self, ServiceError> {
        Ok(Self {
            diagnostics: Diagnostics::new()?,
            symbols: scenemax_ide_services::SymbolIndexer::new()?,
            projector: ProjectorProcess::new(executable),
            storage: Storage::new()?,
            catalog_storage: Storage::new()?,
            scene_storage: Storage::new()?,
            material_storage: Storage::new()?,
            catalog_root: PathBuf::from("."),
            last_project_file: None,
            catalog_path: None,
            catalog_startup: None,
            pending_save: None,
            navigation: None,
            search_versions: vec![],
            recovery: Default::default(),
        })
    }
    pub(crate) fn is_saving(&self) -> bool {
        self.pending_save.is_some() || self.recovery.pending
    }
    pub(crate) fn cancel_run(&mut self) {
        if matches!(
            self.pending_save,
            Some(SavePurpose::Run(_) | SavePurpose::PathRun(_) | SavePurpose::ProjectRun)
        ) {
            self.pending_save = Some(SavePurpose::Save);
        }
    }
    pub(crate) fn save(
        &mut self,
        session: &mut Session,
        ids: Vec<DocumentId>,
        purpose: SavePurpose,
    ) -> Result<()> {
        if self.storage.is_pending() {
            bail!("A file operation is already running; wait for it to finish");
        }
        let snapshots = ids
            .into_iter()
            .map(|id| {
                session
                    .workspace
                    .document(id)
                    .map(|doc| (id, doc.snapshot()))
            })
            .collect::<Result<Vec<_>, _>>()?;
        for (_, designer) in &snapshots {
            if designer
                .path()
                .extension()
                .is_some_and(|e| e.eq_ignore_ascii_case("smdesign"))
            {
                let generated = designer.path().with_extension("code");
                if session
                    .workspace
                    .documents()
                    .any(|(_, d)| d.path() == generated && d.is_dirty())
                {
                    bail!(
                        "The generated file {} has unsaved edits. Move those edits into a code node or the init/end scripts before saving the designer.",
                        generated.display()
                    );
                }
            }
        }
        self.storage.request(StorageRequest::Save(snapshots))?;
        self.pending_save = Some(purpose);
        session.status = "Saving document snapshots…".into();
        Ok(())
    }
}

// Apply results separately from polling so timing-sensitive transitions can be
// exercised deterministically without relying on disk or scheduler speed.
pub(crate) fn apply_storage(
    result: StorageResult,
    services: &mut EditorServices,
    session: &mut Session,
    changes: &mut MessageWriter<ViewChange>,
    exit: &mut MessageWriter<AppExit>,
) -> Result<()> {
    match result {
        StorageResult::WorkspaceProject(project, documents, active) => {
            apply_storage(
                StorageResult::Project(Ok((project, None))),
                services,
                session,
                changes,
                exit,
            )?;
            for doc in documents {
                let id = session.workspace.open_document(doc)?;
                changes.write(ViewChange::DocumentOpened(id));
            }
            if let Some(id) = active
                .as_ref()
                .and_then(|path| session.workspace.find_document(path))
            {
                session.workspace.select(id)?;
            }
            changes.write(ViewChange::ActiveChanged);
            session.status = "Workspace restored".into();
        }

        StorageResult::Generated {
            results,
            companions,
        } => {
            let mut newer = false;
            for generated in companions {
                let path = generated.path().to_owned();
                if let Some(id) = session.workspace.find_document(&path) {
                    let doc = session.workspace.document_mut(id)?;
                    if doc.is_dirty() {
                        // Input typed while the worker ran must survive; update only its disk baseline.
                        doc.acknowledge_saved(generated);
                        newer = true;
                    } else {
                        let selection = doc.selection();
                        doc.replace_text(generated.text().to_owned());
                        doc.acknowledge_saved(generated);
                        doc.select(selection);
                    }
                    changes.write(ViewChange::BufferChanged(id));
                }
                let project = session.workspace.project();
                let mut entries = project.entries().to_vec();
                if !entries.iter().any(|e| e.path == path) {
                    entries.push(scenemax_ide_core::ProjectEntry {
                        path: path.clone(),
                        is_directory: false,
                    });
                    entries.sort_by(|a, b| a.path.cmp(&b.path));
                    let truncated = project.tree_truncated();
                    let mut scripts = project.scripts().to_vec();
                    if !scripts.contains(&path) {
                        scripts.push(path);
                    }
                    let project =
                        scenemax_ide_core::Project::new(project.root().to_owned(), scripts)
                            .with_entries(entries, truncated);
                    session.workspace.refresh_project(project);
                }
            }
            changes.write(ViewChange::ProjectTreeChanged);
            apply_storage(
                StorageResult::Saved(results),
                services,
                session,
                changes,
                exit,
            )?;
            if !newer && session.status == "Saved" {
                session.status = "Saved · designer code regenerated".into();
            }
            if newer {
                session.status =
                    "Designer code regenerated; newer edits in the generated tab remain unsaved"
                        .into();
            }
        }
        StorageResult::MaterialLibrary(_) => {}
        StorageResult::Catalog(result) => {
            session.catalog = result?;
            changes.write(ViewChange::CatalogChanged);
            if let Some((fallback, script)) = services.catalog_startup.take() {
                let root = session.catalog.selected.clone().unwrap_or(fallback);
                services
                    .storage
                    .request(StorageRequest::Project { root, script })?;
            }
        }
        StorageResult::Search(result) => {
            let report = result?;
            if services.search_versions.iter().any(|(id, version)| {
                !session
                    .workspace
                    .document(*id)
                    .is_ok_and(|doc| doc.revision() == *version)
            }) {
                bail!("Source changed while searching; search again for current locations");
            }
            session.status = format!(
                "{} matching lines in {} files searched; {} skipped{}",
                report.hits.len(),
                report.scanned,
                report.skipped,
                if report.truncated {
                    "; results limited"
                } else {
                    ""
                }
            );
            session.search_hits = report.hits;
            changes.write(ViewChange::SearchResultsChanged);
        }
        StorageResult::RecoveryLoaded(result) => {
            services.recovery.enabled = true;
            let batch = result?;
            session.recoverable = batch.documents.len();
            if session.recoverable > 0 {
                session.status = format!(
                    "{} unsaved buffers available for recovery",
                    session.recoverable
                );
            }
            if batch.documents.is_empty() {
                services.recovery.retire.extend(batch.sources);
            } else {
                services.recovery.candidates = Some(batch);
            }
            services.recovery.enabled = true;
        }
        StorageResult::Checkpoint(result) => {
            services.recovery.pending = false;
            if let Err(error) = result {
                services.recovery.stamp = None;
                services.recovery.exit = None;
                return Err(error.into());
            }
            services.recovery.retire.clear();
            if let Some(discard) = services.recovery.exit.take() {
                if discard || !session.workspace.has_dirty_documents() {
                    if session.restarting {
                        *session
                            .restart_project
                            .lock()
                            .unwrap_or_else(|e| e.into_inner()) =
                            Some(session.workspace.project().root().to_owned());
                    }
                    exit.write(AppExit::Success);
                } else {
                    session.status = "Newer edits remain unsaved; close cancelled".into();
                }
            }
        }
        StorageResult::Explored(result) => {
            result?;
            session.status = "Opened in explorer".into();
        }
        StorageResult::Tree(result) => {
            super::tree_commands::apply(result?, session, changes)?;
            services.storage.request(StorageRequest::Refresh(
                session.workspace.project().root().to_owned(),
            ))?;
        }
        StorageResult::Reload(version, result) => {
            let document = result?;
            let id = if let Some((id, revision)) = version {
                if !session
                    .workspace
                    .document(id)
                    .is_ok_and(|d| d.revision() == revision)
                {
                    bail!("Reload cancelled because the document changed while reading disk");
                }
                *session.workspace.document_mut(id)? = document;
                changes.write(ViewChange::DocumentClosed(id));
                id
            } else {
                session.workspace.open_document(document)?
            };
            changes.write(ViewChange::DocumentOpened(id));
            changes.write(ViewChange::BufferChanged(id));
            changes.write(ViewChange::ActiveChanged);
            session.status = "Selected file reloaded from disk".into();
        }
        StorageResult::Scene3d(_) => bail!("Unexpected scene worker result"),
        StorageResult::Refreshed(result) => {
            session.workspace.refresh_project(result?);
            changes.write(ViewChange::ProjectIndexInvalidated);
            changes.write(ViewChange::ProjectTreeChanged);
            session.status = "Script inventory refreshed".into();
        }
        StorageResult::Created(result) => {
            let (project, document) = result?;
            session.workspace.refresh_project(project);
            let id = session.workspace.open_document(document)?;
            changes.write(ViewChange::DocumentOpened(id));
            changes.write(ViewChange::ActiveChanged);
            changes.write(ViewChange::ProjectTreeChanged);
            session.status = "Script created".into();
        }
        StorageResult::Project(result) => {
            let (project, initial) = result?;
            if services.projector.is_running() {
                bail!("Stop the projector before switching projects");
            }
            // Edits may have happened while the inventory was loading.
            session.workspace.switch_project(project)?;
            session.closing_tab = None;
            session.search_hits.clear();
            changes.write(ViewChange::SearchResultsChanged);
            changes.write(ViewChange::ProjectOpened);
            if let Some(document) = initial {
                let id = session.workspace.open_document(document)?;
                changes.write(ViewChange::DocumentOpened(id));
                changes.write(ViewChange::ActiveChanged);
            }
            session.status = "Project opened".into();
            session.recoverable = 0;
            services.recovery = Default::default();
            services.storage.request(StorageRequest::LoadRecovery(
                session.workspace.project().root().to_owned(),
            ))?;
        }
        StorageResult::Open(result) => {
            let navigation = services.navigation.take();
            let document = result?;
            let existing = session.workspace.find_document(document.path());
            let id = session.workspace.open_document(document)?;
            if existing.is_none() {
                changes.write(ViewChange::DocumentOpened(id));
            }
            changes.write(ViewChange::ActiveChanged);
            if let Some((path, line)) = navigation
                && session.workspace.document(id)?.path() == path
            {
                session.workspace.document_mut(id)?.go_to_line(line);
                changes.write(ViewChange::BufferChanged(id));
            }
            session.status = "Script opened".into();
        }
        StorageResult::Saved(results) => {
            let purpose = services.pending_save.take();
            let mut failures = Vec::new();
            for (id, result) in results {
                match result {
                    Ok(saved) => {
                        if saved
                            .path()
                            .extension()
                            .is_some_and(|e| e.eq_ignore_ascii_case("smmat"))
                        {
                            changes.write(ViewChange::MaterialsChanged);
                        }
                        if !session.workspace.document_mut(id)?.acknowledge_saved(saved) {
                            failures.push(
                                "Save acknowledgement did not match the open document".to_owned(),
                            );
                        }
                    }
                    Err(error) => failures.push(error.to_string()),
                }
            }
            if !failures.is_empty() {
                bail!("Save incomplete: {}", failures.join("; "));
            }
            let dirty = session.workspace.has_dirty_documents();
            session.status = if dirty {
                "Saved snapshot; newer or other edits remain unsaved"
            } else {
                "Saved"
            }
            .into();
            match purpose {
                Some(SavePurpose::Tab(id)) if session.closing_tab == Some(id) => {
                    if !session.workspace.document(id)?.is_dirty() {
                        session.workspace.close_document(id)?;
                        session.closing_tab = None;
                        changes.write(ViewChange::DocumentClosed(id));
                        changes.write(ViewChange::ActiveChanged);
                    } else {
                        session.status = "Newer edits remain unsaved in this tab".into();
                    }
                }
                Some(SavePurpose::Close) if session.closing && !dirty => {
                    services.finish_session(session, false)?;
                }
                Some(SavePurpose::Run(id)) if !dirty && !session.closing => {
                    services.projector.start(
                        session.workspace.project().root(),
                        session.workspace.document(id)?.path(),
                    )?;
                    session.status = "Bevy projector started".into();
                }
                Some(SavePurpose::ProjectRun) if !dirty && !session.closing => {
                    let project = session.workspace.project();
                    let entry = project.entry_point().ok_or_else(|| {
                        anyhow::anyhow!("No main file under the project's scripts folder")
                    })?;
                    services.projector.start(project.root(), entry)?;
                    session.status = "Bevy project started".into();
                }
                Some(SavePurpose::PathRun(path)) if !dirty && !session.closing => {
                    services
                        .projector
                        .start(session.workspace.project().root(), &path)?;
                    session.status = "Bevy projector started".into();
                }
                Some(SavePurpose::Run(_) | SavePurpose::PathRun(_) | SavePurpose::ProjectRun) => {
                    session.status = "Run cancelled: save newer edits and run again".into()
                }
                _ => {}
            }
        }
    }
    Ok(())
}
pub(crate) fn poll_jobs(
    mut services: ResMut<EditorServices>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<super::ViewChange>,
    mut exit: MessageWriter<AppExit>,
) {
    match services.catalog_storage.poll() {
        Ok(Some(result)) => {
            if let Err(error) =
                apply_storage(result, &mut services, &mut session, &mut changes, &mut exit)
            {
                session.status = format!("{error:#}");
            }
        }
        Err(error) => session.status = error.to_string(),
        Ok(None) => {}
    }
    match services.storage.poll() {
        Ok(Some(result)) => {
            if let Err(error) =
                apply_storage(result, &mut services, &mut session, &mut changes, &mut exit)
            {
                session.status = format!("{error:#}");
            }
        }
        Err(error) => {
            services.pending_save = None;
            session.status = error.to_string();
        }
        Ok(None) => {}
    }
    match services.diagnostics.poll() {
        Ok(Some(report)) if report.snapshot.is_current(&session.workspace) => {
            session.status = report.message
        }
        Ok(Some(_)) => {
            session.status = "Source changed during syntax checking; run Check again".into()
        }
        Ok(None) => {}
        Err(error) => session.status = error.to_string(),
    }
    for line in services.projector.drain_output(32) {
        session.append_output(&line);
    }
    match services.projector.poll_exit() {
        Ok(Some(status)) => {
            session.status = format!("Projector exited: {status}");
            let message = session.status.clone();
            session.append_output(&message);
        }
        Err(error) => session.status = error.to_string(),
        Ok(None) => {}
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use scenemax_ide_services::Filesystem;
    use std::time::{Duration, Instant};

    #[test]
    fn designer_save_all_refreshes_generated_tab_and_uses_saved_init() {
        let (mut app, dir, _) = setup();
        let root = app
            .world()
            .resource::<Session>()
            .workspace
            .project()
            .root()
            .to_owned();
        let source =
            r#"{"entities":[{"type":"CODE","name":"Logic","codeText":"Logger.info \"old\""}]}"#;
        let designer_path = root.join("scripts/scene.smdesign");
        let code_path = root.join("scripts/scene.code");
        let init_path = root.join("scripts/scene_init.code");
        std::fs::write(&designer_path, source).unwrap();
        std::fs::write(&code_path, "// prior output\r\n").unwrap();
        std::fs::write(&init_path, "// init").unwrap();
        let ids = {
            let mut session = app.world_mut().resource_mut::<Session>();
            [designer_path.clone(), code_path.clone(), init_path].map(|p| {
                let doc = Filesystem::open_document(session.workspace.project(), &p).unwrap();
                session.workspace.open_document(doc).unwrap()
            })
        };
        text(&mut app, ids[0], &source.replace("old", "new"));
        text(&mut app, ids[2], "Logger.info \"init\"\n");
        save(&mut app, ids.to_vec(), SavePurpose::Save);
        finish(&mut app);
        let session = app.world().resource::<Session>();
        let code = std::fs::read_to_string(&code_path).unwrap();
        assert!(code.contains("Logger.info \"new\""));
        assert!(code.contains("Logger.info \"init\""));
        assert_eq!(session.workspace.document(ids[1]).unwrap().text(), code);
        assert!(!session.workspace.has_dirty_documents());
        assert!(session.status.contains("regenerated"));
        assert!(
            session
                .workspace
                .project()
                .entries()
                .iter()
                .any(|e| e.path == code_path)
        );
        assert!(dir.path().exists());
    }

    #[test]
    fn reload_does_not_overwrite_edits_made_while_reading() {
        let (mut app, dir, id) = setup();
        let session = app.world().resource::<Session>();
        let root = session.workspace.project().root().to_owned();
        let revision = session.workspace.document(id).unwrap().revision();
        std::fs::write(dir.path().join("scripts/main"), "disk version").unwrap();
        app.world_mut()
            .resource_mut::<EditorServices>()
            .storage
            .request(StorageRequest::Reload {
                root: root.clone(),
                path: root.join("scripts/main"),
                version: Some((id, revision)),
            })
            .unwrap();
        text(&mut app, id, "newer unsaved edit");
        finish(&mut app);
        let session = app.world().resource::<Session>();
        assert_eq!(
            session.workspace.document(id).unwrap().text(),
            "newer unsaved edit"
        );
        assert!(session.status.contains("Reload cancelled"));
    }
    #[test]
    fn navigator_rename_follows_open_buffer_and_keeps_its_edits() {
        let (mut app, dir, id) = setup();
        let root = app
            .world()
            .resource::<Session>()
            .workspace
            .project()
            .root()
            .to_owned();
        app.world_mut()
            .resource_mut::<EditorServices>()
            .storage
            .request(StorageRequest::Tree {
                root: root.clone(),
                operation: scenemax_ide_services::TreeOperation::Move {
                    path: root.join("scripts/main"),
                    parent: root.join("scripts"),
                    name: "renamed.code".into(),
                },
            })
            .unwrap();
        text(&mut app, id, "typed during rename");
        finish(&mut app);
        let session = app.world().resource::<Session>();
        let doc = session.workspace.document(id).unwrap();
        assert_eq!(doc.path(), root.join("scripts/renamed.code"));
        assert_eq!(doc.text(), "typed during rename");
        assert!(doc.is_dirty());
        assert_eq!(
            std::fs::read_to_string(dir.path().join("scripts/renamed.code")).unwrap(),
            "original"
        );
    }

    fn setup() -> (App, tempfile::TempDir, DocumentId) {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        let path = dir.path().join("scripts/main");
        std::fs::write(&path, "original").unwrap();
        let mut session = Session::new(Filesystem::open_project(dir.path()).unwrap());
        let id = session
            .workspace
            .open_document(Filesystem::open_document(session.workspace.project(), &path).unwrap())
            .unwrap();
        let mut app = App::new();
        app.insert_resource(session)
            .insert_resource(EditorServices::new(PathBuf::new()).unwrap())
            .add_message::<ViewChange>()
            .add_message::<AppExit>()
            .add_systems(Update, poll_jobs);
        (app, dir, id)
    }
    fn text(app: &mut App, id: DocumentId, value: &str) {
        app.world_mut()
            .resource_mut::<Session>()
            .workspace
            .document_mut(id)
            .unwrap()
            .replace_text(value.into());
    }
    fn save(app: &mut App, ids: Vec<DocumentId>, purpose: SavePurpose) {
        app.world_mut()
            .resource_scope(|world, mut services: Mut<EditorServices>| {
                services
                    .save(&mut world.resource_mut::<Session>(), ids, purpose)
                    .unwrap();
            });
    }
    fn finish(app: &mut App) {
        let deadline = Instant::now() + Duration::from_secs(5);
        while app
            .world()
            .resource::<EditorServices>()
            .storage
            .is_pending()
        {
            assert!(Instant::now() < deadline, "Storage worker timed out");
            std::thread::sleep(Duration::from_millis(1));
            app.update();
        }
    }
    #[test]
    fn typing_during_save_preserves_newer_edits_and_allows_next_save() {
        let (mut app, dir, id) = setup();
        text(&mut app, id, "snapshot");
        save(&mut app, vec![id], SavePurpose::Save);
        text(&mut app, id, "newer"); // Before any result can be applied.
        finish(&mut app);
        assert_eq!(
            std::fs::read_to_string(dir.path().join("scripts/main")).unwrap(),
            "snapshot"
        );
        let session = app.world().resource::<Session>();
        assert_eq!(session.workspace.document(id).unwrap().text(), "newer");
        assert!(session.workspace.has_dirty_documents());
        save(&mut app, vec![id], SavePurpose::Save);
        finish(&mut app);
        assert_eq!(
            std::fs::read_to_string(dir.path().join("scripts/main")).unwrap(),
            "newer"
        );
        assert!(
            !app.world()
                .resource::<Session>()
                .workspace
                .has_dirty_documents()
        );
    }
    #[test]
    fn close_after_save_does_not_exit_when_new_edits_arrive() {
        let (mut app, _dir, id) = setup();
        text(&mut app, id, "snapshot");
        app.world_mut().resource_mut::<Session>().closing = true;
        save(&mut app, vec![id], SavePurpose::Close);
        text(&mut app, id, "newer");
        finish(&mut app);
        assert!(app.world().resource::<Messages<AppExit>>().is_empty());
        assert!(app.world().resource::<Session>().closing);
    }
    #[test]
    fn cancelling_close_while_saving_keeps_the_window_open() {
        let (mut app, _dir, id) = setup();
        text(&mut app, id, "snapshot");
        app.world_mut().resource_mut::<Session>().closing = true;
        save(&mut app, vec![id], SavePurpose::Close);
        app.world_mut().resource_mut::<Session>().closing = false;
        finish(&mut app);
        assert!(app.world().resource::<Messages<AppExit>>().is_empty());
        assert!(
            !app.world()
                .resource::<Session>()
                .workspace
                .has_dirty_documents()
        );
    }
    #[test]
    fn successful_save_and_close_waits_for_disk_completion() {
        let (mut app, dir, id) = setup();
        text(&mut app, id, "snapshot");
        app.world_mut().resource_mut::<Session>().closing = true;
        save(&mut app, vec![id], SavePurpose::Close);
        assert!(app.world().resource::<Messages<AppExit>>().is_empty());
        finish(&mut app);
        assert!(!app.world().resource::<Messages<AppExit>>().is_empty());
        assert_eq!(
            std::fs::read_to_string(dir.path().join("scripts/main")).unwrap(),
            "snapshot"
        );
    }
    #[test]
    fn project_loading_rechecks_dirty_buffers_at_completion() {
        let (mut app, _dir, id) = setup();
        let other = tempfile::tempdir().unwrap();
        app.world_mut()
            .resource_mut::<EditorServices>()
            .storage
            .request(StorageRequest::Project {
                root: other.path().to_owned(),
                script: None,
            })
            .unwrap();
        text(&mut app, id, "edited during scan");
        finish(&mut app);
        let session = app.world().resource::<Session>();
        assert_eq!(
            session.workspace.document(id).unwrap().text(),
            "edited during scan"
        );
        assert!(session.status.contains("Save your documents"));
    }
    #[test]
    fn save_all_acknowledges_successes_and_preserves_failed_buffers() {
        let (mut app, dir, id) = setup();
        let path = dir.path().join("scripts/other");
        std::fs::write(&path, "original").unwrap();
        let mut session = app.world_mut().resource_mut::<Session>();
        let doc = Filesystem::open_document(session.workspace.project(), &path).unwrap();
        let other = session.workspace.open_document(doc).unwrap();
        text(&mut app, id, "first saved");
        text(&mut app, other, "second edited");
        std::fs::write(&path, "external").unwrap();
        save(&mut app, vec![id, other], SavePurpose::Close);
        finish(&mut app);
        let session = app.world().resource::<Session>();
        assert!(!session.workspace.document(id).unwrap().is_dirty());
        assert!(session.workspace.document(other).unwrap().is_dirty());
        assert!(session.status.contains("Save incomplete"));
        assert_eq!(std::fs::read_to_string(path).unwrap(), "external");
        assert!(app.world().resource::<Messages<AppExit>>().is_empty());
    }
    #[test]
    fn newer_edits_cancel_deferred_run() {
        let (mut app, _dir, id) = setup();
        text(&mut app, id, "snapshot");
        save(&mut app, vec![id], SavePurpose::Run(id));
        text(&mut app, id, "newer");
        finish(&mut app);
        assert!(
            app.world()
                .resource::<Session>()
                .status
                .contains("Run cancelled")
        );
        assert!(
            !app.world()
                .resource::<EditorServices>()
                .projector
                .is_running()
        );
    }
    #[test]
    fn stop_cancels_run_queued_behind_a_save() {
        let (mut app, _dir, id) = setup();
        text(&mut app, id, "snapshot");
        save(&mut app, vec![id], SavePurpose::Run(id));
        app.world_mut()
            .resource_mut::<EditorServices>()
            .cancel_run();
        finish(&mut app);
        assert_eq!(app.world().resource::<Session>().status, "Saved");
        assert!(
            !app.world()
                .resource::<EditorServices>()
                .projector
                .is_running()
        );
    }
    #[test]
    fn project_run_is_cancelled_by_edits_arriving_during_save() {
        let (mut app, _dir, id) = setup();
        text(&mut app, id, "snapshot");
        save(&mut app, vec![id], SavePurpose::ProjectRun);
        text(&mut app, id, "newer");
        finish(&mut app);
        assert!(
            app.world()
                .resource::<Session>()
                .status
                .contains("Run cancelled")
        );
        assert!(
            !app.world()
                .resource::<EditorServices>()
                .projector
                .is_running()
        );
    }
    #[test]
    fn stop_cancels_project_run_waiting_for_save() {
        let (mut app, _dir, id) = setup();
        text(&mut app, id, "snapshot");
        save(&mut app, vec![id], SavePurpose::ProjectRun);
        app.world_mut()
            .resource_mut::<EditorServices>()
            .cancel_run();
        finish(&mut app);
        assert!(
            !app.world()
                .resource::<EditorServices>()
                .projector
                .is_running()
        );
        assert_eq!(app.world().resource::<Session>().status, "Saved");
    }
}
