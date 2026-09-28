use super::jobs::SavePurpose;
use super::{EditorServices, Session, ViewChange};
use anyhow::{Result, bail};
use bevy::prelude::*;
use scenemax_ide_core::{DocumentId, EditorError};
use scenemax_ide_services::{SourceSnapshot, StorageRequest};
use std::{collections::VecDeque, path::PathBuf};

/// Every input surface dispatches the same application commands.
#[derive(Clone)]
pub(crate) enum Command {
    Analyzer(super::material::Edit),
    Material(super::material::Edit),
    Weapon(super::material::Edit),
    Motion(super::material::Edit),
    Ik(super::material::Edit),
    Tree(scenemax_ide_services::TreeOperation),
    SavePath(PathBuf),
    RunPath(PathBuf),
    ReloadPath(PathBuf),
    Explore(PathBuf),
    OpenWeb(String),
    OpenInstallationFolder,
    SaveCopy(PathBuf),
    SaveCloseTab,
    DiscardTab,
    CancelTab,
    FindProject(String),
    OpenAt(PathBuf, usize),
    ClearConsole,
    RestoreRecovery,
    DiscardRecovery,
    Edit(super::EditCommand),
    CloseTab,
    CloseDocument(DocumentId),
    Create(PathBuf),
    NewProject(PathBuf),
    Refresh,
    Open(PathBuf),
    Select(DocumentId),
    Save,
    SaveAll,
    Check,
    Run,
    RunProject,
    RefreshProjects,
    Stop,
    OpenProject(PathBuf),
    RequestClose,
    Restart,
    CancelClose,
    DiscardExit,
}
#[derive(Resource, Default)]
pub(crate) struct CommandQueue(pub(crate) VecDeque<Command>);

pub(crate) fn execute_commands(
    mut queue: ResMut<CommandQueue>,
    mut session: ResMut<Session>,
    mut services: ResMut<EditorServices>,
    mut changes: MessageWriter<ViewChange>,
    mut exit: MessageWriter<AppExit>,
) {
    for command in queue.0.drain(..) {
        if let Err(error) = execute(
            command,
            &mut session,
            &mut services,
            &mut changes,
            &mut exit,
        ) {
            session.status = format!("{error:#}");
        }
    }
}

pub(super) fn execute(
    command: Command,
    session: &mut Session,
    services: &mut EditorServices,
    changes: &mut MessageWriter<ViewChange>,
    _exit: &mut MessageWriter<AppExit>,
) -> Result<()> {
    if session.asset_operation_pending
        && !matches!(
            command,
            Command::Stop | Command::CancelClose | Command::ClearConsole
        )
    {
        bail!("Wait for the asset operation to finish before changing or closing the project");
    }
    if let Some(result) = super::tree_commands::execute(&command, session, services) {
        return result;
    }
    if session.composing
        && matches!(
            command,
            Command::Edit(_)
                | Command::CloseDocument(_)
                | Command::CloseTab
                | Command::Restart
                | Command::RequestClose
                | Command::DiscardExit
                | Command::OpenProject(_)
                | Command::NewProject(_)
                | Command::RestoreRecovery
        )
    {
        bail!("Finish or cancel text composition before this action");
    }
    match command {
        Command::Material(edit) => super::material::edit(edit, session, changes)?,
        Command::Ik(edit) => super::ik::edit(edit, session, changes)?,
        Command::Motion(edit) => super::motion::edit(edit, session, changes)?,
        Command::Analyzer(edit) => super::animation_analyzer::edit(edit, session, changes)?,
        Command::Weapon(edit) => super::weapon::edit(edit, session, changes)?,
        Command::Tree(_)
        | Command::SavePath(_)
        | Command::RunPath(_)
        | Command::ReloadPath(_)
        | Command::Explore(_) => unreachable!("Navigator command handled above"),
        Command::SaveCopy(path) => {
            let id = session.workspace.require_active()?;
            services.storage.request(StorageRequest::SaveCopy {
                root: session.workspace.project().root().to_owned(),
                path,
                document: session.workspace.document(id)?.snapshot(),
            })?;
            session.status = "Saving a new copy...".into();
        }
        Command::SaveCloseTab => {
            if let Some(id) = session.closing_tab {
                services.save(session, vec![id], SavePurpose::Tab(id))?;
            }
        }
        Command::DiscardTab => {
            if services.is_saving() {
                bail!("Wait for the pending write before discarding a tab");
            }
            if let Some(id) = session.closing_tab.take() {
                session.workspace.discard_document(id)?;
                services.recovery.stamp = None;
                changes.write(ViewChange::DocumentClosed(id));
                changes.write(ViewChange::ActiveChanged);
            }
        }
        Command::CancelTab => session.closing_tab = None,
        Command::FindProject(needle) => {
            let project = session.workspace.project();
            services.storage.request(StorageRequest::Search {
                root: project.root().to_owned(),
                paths: project.scripts().to_vec(),
                needle,
                buffers: session
                    .workspace
                    .documents()
                    .map(|(_, doc)| doc.snapshot())
                    .collect(),
            })?;
            session.search_hits.clear();
            changes.write(ViewChange::SearchResultsChanged);
            services.search_versions = session
                .workspace
                .documents()
                .map(|(id, doc)| (id, doc.revision()))
                .collect();
            session.status = "Searching project scripts...".into();
        }
        Command::OpenAt(path, line) => {
            if let Some(id) = session.workspace.find_document(&path) {
                session.workspace.select(id)?;
                session.workspace.document_mut(id)?.go_to_line(line);
                changes.write(ViewChange::ActiveChanged);
                changes.write(ViewChange::BufferChanged(id));
            } else {
                services.storage.request(StorageRequest::Open {
                    root: session.workspace.project().root().to_owned(),
                    path: path.clone(),
                })?;
                services.navigation = Some((path, line));
                session.status = "Opening search result...".into();
            }
        }
        Command::ClearConsole => {
            session.output.clear();
            session.output_revision += 1;
        }
        Command::RestoreRecovery => services.restore_recovery(session, changes)?,
        Command::DiscardRecovery => services.discard_recovery(session)?,
        Command::Edit(command) => super::editing::edit(command, session, changes)?,
        Command::CloseTab | Command::CloseDocument(_) => {
            if services.is_saving() {
                bail!("Wait for the save before closing a tab");
            }
            let id = if let Command::CloseDocument(id) = command {
                id
            } else {
                session.workspace.require_active()?
            };
            if session.workspace.document(id)?.is_dirty() {
                session.closing_tab = Some(id);
                return Ok(());
            }
            session.workspace.close_document(id)?;
            changes.write(ViewChange::DocumentClosed(id));
            changes.write(ViewChange::ActiveChanged);
        }
        Command::Create(path) => {
            services.storage.request(StorageRequest::Create {
                root: session.workspace.project().root().to_owned(),
                path,
            })?;
            session.status = "Creating script...".into();
        }
        Command::Refresh => {
            services.storage.request(StorageRequest::Refresh(
                session.workspace.project().root().to_owned(),
            ))?;
            session.status = "Refreshing project tree...".into();
        }
        Command::NewProject(path) => {
            if session.workspace.has_dirty_documents() {
                return Err(EditorError::UnsavedDocuments.into());
            }
            if services.projector.is_running() {
                bail!("Stop the projector before creating a project");
            }
            services.storage.request(StorageRequest::NewProject(path))?;
            session.status = "Creating project...".into();
        }
        Command::OpenInstallationFolder => {
            services
                .storage
                .request(StorageRequest::OpenInstallationFolder)?;
        }
        Command::OpenWeb(url) => {
            services.storage.request(StorageRequest::OpenWeb(url))?;
        }
        Command::Open(path) => {
            if let Some(id) = session.workspace.find_document(&path) {
                session.workspace.select(id)?;
                changes.write(ViewChange::ActiveChanged);
            } else {
                services.storage.request(StorageRequest::Open {
                    root: session.workspace.project().root().to_owned(),
                    path,
                })?;
                session.status = "Opening script…".into();
            }
        }
        Command::Select(id) => {
            session.workspace.select(id)?;
            changes.write(ViewChange::ActiveChanged);
        }
        Command::Save => {
            let id = session.workspace.require_active()?;
            services.save(session, vec![id], SavePurpose::Save)?;
        }
        Command::SaveAll => {
            let ids = session.workspace.documents().map(|(id, _)| id).collect();
            let purpose = if session.closing {
                SavePurpose::Close
            } else {
                SavePurpose::Save
            };
            services.save(session, ids, purpose)?;
        }
        Command::Check => {
            let id = session.workspace.require_active()?;
            let doc = session.workspace.document(id)?;
            if doc
                .path()
                .extension()
                .is_some_and(|e| e.eq_ignore_ascii_case("smmat"))
            {
                scenemax_assets::material::validate(&serde_json::from_str(doc.text())?)
                    .map_err(anyhow::Error::msg)?;
                session.status = "Material is valid".into();
                return Ok(());
            }
            if doc
                .path()
                .extension()
                .is_some_and(|e| e.eq_ignore_ascii_case("smeffectimport"))
            {
                scenemax_ide_core::effect_import::validate(&serde_json::from_str(doc.text())?)
                    .map_err(anyhow::Error::msg)?;
                session.status = "Effect import settings are valid".into();
                return Ok(());
            }
            if doc
                .path()
                .extension()
                .is_some_and(|e| e.eq_ignore_ascii_case("smspriteimport"))
            {
                scenemax_ide_core::sprite_import::validate(&serde_json::from_str(doc.text())?)
                    .map_err(anyhow::Error::msg)?;
                session.status = "Sprite import settings are valid".into();
                return Ok(());
            }
            if doc
                .path()
                .extension()
                .is_some_and(|e| e.eq_ignore_ascii_case("smmodelimport"))
            {
                let draft = serde_json::from_str(doc.text())?;
                scenemax_ide_core::model_import::validate(&draft).map_err(anyhow::Error::msg)?;
                session.status = "Model import settings are valid".into();
                return Ok(());
            }
            if doc
                .path()
                .extension()
                .is_some_and(|e| e.eq_ignore_ascii_case("smdesign"))
            {
                session.status =
                    "3D scene resources are checked during import; this document is not a script"
                        .into();
                return Ok(());
            }
            if doc
                .path()
                .extension()
                .is_some_and(|ext| ext.eq_ignore_ascii_case("smui"))
            {
                scenemax_ide_services::scene::preview(doc.text()).map_err(anyhow::Error::msg)?;
                session.status = "UI document structure and layout are valid".into();
                return Ok(());
            }

            services
                .diagnostics
                .request(SourceSnapshot::new(id, session.workspace.document(id)?))?;
            session.status = "Checking source snapshot…".into();
        }
        Command::RefreshProjects => {
            services.catalog_storage.request(StorageRequest::Catalog {
                root: services.catalog_root.clone(),
                path: services.catalog_path.clone(),
                last_project: None,
            })?;
            session.status = "Refreshing projects…".into();
        }
        Command::RunProject => {
            if session.workspace.project().entry_point().is_none() {
                bail!("Could not find a main file under the project's scripts folder");
            }
            services.projector.validate()?;
            let ids = session
                .workspace
                .documents()
                .filter(|(_, doc)| doc.is_dirty())
                .map(|(id, _)| id)
                .collect();
            services.save(session, ids, SavePurpose::ProjectRun)?;
        }
        Command::Run => {
            let id = session.workspace.require_active()?;
            let path = session.workspace.document(id)?.path();
            if session.workspace.project().run_target(path).is_none() {
                bail!("The active file is not a runnable SceneMax script");
            }
            if session
                .workspace
                .documents()
                .any(|(other, doc)| other != id && doc.is_dirty())
            {
                bail!("Save all documents before running so included scripts are current");
            }
            services.projector.validate()?;
            services.save(session, vec![id], SavePurpose::Run(id))?;
        }
        Command::Stop => {
            services.cancel_run();
            services.projector.stop()?;
            session.status = "Projector stopped".into();
        }
        Command::OpenProject(path) => {
            if session.workspace.has_dirty_documents() {
                return Err(EditorError::UnsavedDocuments.into());
            }
            if services.projector.is_running() {
                bail!("Stop the projector before switching projects");
            }
            services.storage.request(StorageRequest::SwitchProject {
                previous: session.workspace.project().root().to_owned(),
                workspace: scenemax_ide_services::workspace_state::WorkspaceState::capture(
                    &session.workspace,
                ),
                root: path,
            })?;
            session.status = "Opening project…".into();
        }

        Command::RequestClose | Command::Restart => {
            session.restarting = matches!(command, Command::Restart);
            session.closing_tab = None;
            if session.workspace.has_dirty_documents() || services.is_saving() {
                session.closing = true;
            } else {
                services.finish_session(session, false)?;
            }
        }
        Command::CancelClose => {
            session.restarting = false;
            session.closing = false;
            services.recovery.exit = None;
        }
        Command::DiscardExit => {
            if services.is_saving() {
                bail!("Wait for the current save before discarding and closing");
            }
            services.finish_session(session, true)?;
        }
    }
    Ok(())
}
