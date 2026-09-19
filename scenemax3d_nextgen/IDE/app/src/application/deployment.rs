//! Deployment orchestration; no widgets, filesystem access or process ownership.
use super::{Command, CommandQueue, EditorServices, Session};
use bevy::prelude::*;
use scenemax_ide_core::deployment::Settings;
use scenemax_ide_services::deployment::{Builder, Operation, Report, Request};
use std::{collections::VecDeque, path::PathBuf};

#[derive(Clone)]
pub(crate) enum Action {
    Open,
    Check,
    Build,
    Upload,
    Login,
    Cancel,
    Close,
    DismissResult,
    Exit,
    Reveal,
    Log,
    Browse(PathField),
}
#[derive(Clone, Copy)]
pub(crate) enum PathField {
    Output,
    Builtin,
    Butler,
    Runtime(usize),
    Recipe(usize),
}
#[derive(Resource, Default)]
pub(crate) struct Deployment {
    pub open: bool,
    pub settings: Option<Settings>,
    pub report: Report,
    pub actions: VecDeque<Action>,
    pub busy: bool,
    pub form_revision: u64,
    pub notification: Option<(bool, String)>,
    notify_job: bool,
    project: PathBuf,
    builder: Builder,
    pending: Option<Request>,
    loading: bool,
    observed: Option<u64>,
    picking: Option<PathField>,
}
impl Deployment {
    fn start(&mut self, operation: Operation) -> Result<(), String> {
        let notify = matches!(operation, Operation::Build(_) | Operation::Upload(..));
        self.builder.start(operation).map_err(|e| e.to_string())?;
        self.notify_job = notify;
        self.notification = None;
        self.busy = true;
        self.report = Report::default();
        self.observed = None;
        Ok(())
    }
    fn request(&self, session: &Session) -> Result<Request, String> {
        if self.project != session.workspace.project().root() {
            return Err("The selected project changed. Close and reopen Package & Deploy.".into());
        }
        let entry = session
            .workspace
            .project()
            .entry_point()
            .ok_or("This project has no main script.")?
            .to_owned();
        let mut settings = self
            .settings
            .clone()
            .ok_or("Wait for deployment settings to load.")?;
        settings.validate()?;
        let resolve = |value: &mut String| {
            if !value.is_empty() && !std::path::Path::new(value).is_absolute() {
                *value = self.project.join(&*value).to_string_lossy().into_owned();
            }
        };
        resolve(&mut settings.output);
        resolve(&mut settings.builtin_resources);
        for platform in &mut settings.platforms {
            resolve(&mut platform.runtime);
            resolve(&mut platform.recipe);
        }
        Ok(Request {
            project: self.project.clone(),
            entry,
            workspace: scenemax_ide_services::deployment::workspace(),
            settings,
        })
    }
}
pub(crate) fn update(
    mut state: ResMut<Deployment>,
    mut commands: ResMut<CommandQueue>,
    session: Res<Session>,
    services: Res<EditorServices>,
) {
    if let Some(report) = state.builder.report() {
        if state.observed != Some(report.revision) {
            state.observed = Some(report.revision);
            state.report = report;
            if state.report.finished && state.notify_job {
                state.notify_job = false;
                state.notification = Some((state.report.success, state.report.status.clone()));
            }
        }
        state.busy = state.builder.busy() || state.pending.is_some();
        if state.loading && state.report.finished {
            state.loading = false;
            state.report.progress = 0.;
            if let Some(settings) = state.report.settings.take() {
                state.settings = Some(settings);
            } else {
                state.settings = Some(Settings {
                    output: state
                        .project
                        .join("build_games")
                        .to_string_lossy()
                        .into_owned(),
                    ..Default::default()
                });
            }
        }
        if state.report.finished
            && let Some(field) = state.picking.take()
            && let Some(path) = state.report.picked.take()
            && let Some(settings) = state.settings.as_mut()
        {
            let value = path.to_string_lossy().into_owned();
            match field {
                PathField::Output => settings.output = value,
                PathField::Builtin => settings.builtin_resources = value,
                PathField::Butler => settings.butler = value,
                PathField::Runtime(i) => settings.platforms[i].runtime = value,
                PathField::Recipe(i) => settings.platforms[i].recipe = value,
            }
            state.form_revision += 1;
        }
    }
    if state.pending.is_some() && !services.is_saving() && !services.storage.is_pending() {
        let request = state.pending.take();
        if session.workspace.has_dirty_documents() {
            state.report.status = "Build cancelled: some documents remain unsaved. Resolve the save error and try again.".into();
            state.busy = false;
        } else if let Some(request) = request {
            if request.project != session.workspace.project().root() {
                state.report.status = "Project changed before the save completed.".into();
                state.busy = false;
            } else if let Err(error) = state.start(Operation::Build(request)) {
                state.report.status = error;
                state.busy = false;
            }
        }
        if !state.busy {
            state.report.finished = true;
            state.report.success = false;
            state.notification = Some((false, state.report.status.clone()));
        }
    }
    let actions: Vec<_> = state.actions.drain(..).collect();
    for action in actions {
        let result = (|| -> Result<(), String> {
            match action {
                Action::Open => {
                    state.open = true;
                    if state.busy {
                        return Ok(());
                    }
                    if state.settings.is_some()
                        && state.project == session.workspace.project().root()
                    {
                        return Ok(());
                    }
                    state.project = session.workspace.project().root().to_owned();
                    state.settings = None;
                    state.loading = true;
                    let root = state.project.clone();
                    state.start(Operation::Load(root))?;
                }
                Action::Close => {
                    if state.notification.is_some() {
                        state.notification = None;
                    } else if !state.busy {
                        state.open = false;
                    }
                }
                Action::DismissResult => state.notification = None,
                Action::Exit => {
                    state.builder.cancel();
                    state.pending = None;
                    state.open = false;
                    commands.0.push_back(Command::RequestClose);
                }
                Action::Cancel => {
                    if !state.busy {
                        return Ok(());
                    }
                    state.pending = None;
                    state.builder.cancel();
                    state.report.status = "Cancelling the current operation…".into();
                }
                Action::Reveal | Action::Log => {
                    let path = if matches!(action, Action::Log) {
                        state.report.log_path.clone()
                    } else {
                        state
                            .report
                            .artifacts
                            .first()
                            .and_then(|p| p.parent())
                            .map(PathBuf::from)
                            .or_else(|| {
                                state
                                    .report
                                    .log_path
                                    .as_ref()
                                    .and_then(|p| p.parent())
                                    .map(PathBuf::from)
                            })
                    };
                    if let Some(path) = path {
                        commands.0.push_back(Command::Explore(path));
                    }
                }
                Action::Check | Action::Build => {
                    if state.busy {
                        return Ok(());
                    }
                    if services.storage.is_pending() || services.is_saving() {
                        return Err("Wait for the current file operation to finish.".into());
                    }
                    let request = state.request(&session)?;
                    if matches!(action, Action::Check) {
                        state.start(Operation::Check(request))?;
                    } else {
                        if services.projector.is_running() {
                            return Err("Stop the running game before packaging.".into());
                        }
                        if session.workspace.has_dirty_documents() {
                            state.pending = Some(request);
                            state.busy = true;
                            state.report.status = "Saving all documents before packaging…".into();
                            commands.0.push_back(Command::SaveAll);
                        } else {
                            state.start(Operation::Build(request))?;
                        }
                    }
                }
                Action::Login => {
                    if state.busy {
                        return Ok(());
                    }
                    let butler = state
                        .settings
                        .as_ref()
                        .ok_or("Wait for settings to load.")?
                        .butler
                        .clone();
                    state.start(Operation::Login(butler))?;
                }
                Action::Upload => {
                    if state.busy {
                        return Ok(());
                    }
                    let request = state.request(&session)?;
                    let artifacts = state.report.artifacts.clone();
                    let log = state
                        .report
                        .log_path
                        .clone()
                        .ok_or("Build a release before publishing.")?;
                    if artifacts.is_empty() {
                        return Err("Build a release before publishing.".into());
                    }
                    state.start(Operation::Upload(request, artifacts, log))?;
                }
                Action::Browse(field) => {
                    if !state.busy {
                        state.picking = Some(field);
                        state.start(Operation::Browse(matches!(
                            field,
                            PathField::Output | PathField::Builtin
                        )))?;
                    }
                }
            }
            Ok(())
        })();
        if let Err(error) = result {
            state.notification = Some((false, error.clone()));
            state.report.status = error;
            state.report.success = false;
            state.report.revision += 1;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use scenemax_ide_services::Filesystem;
    fn setup() -> (App, tempfile::TempDir, PathBuf) {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir(dir.path().join("scripts")).unwrap();
        let path = dir.path().join("scripts/main");
        std::fs::write(&path, "original").unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        let root = project.root().to_owned();
        let document = Filesystem::open_document(&project, &path).unwrap();
        let mut session = Session::new(project);
        let id = session.workspace.open_document(document).unwrap();
        session
            .workspace
            .document_mut(id)
            .unwrap()
            .replace_text("edited".into());
        let mut settings = Settings {
            output: root.join("output").to_string_lossy().into_owned(),
            native_effects: false,
            ..Default::default()
        };
        for p in &mut settings.platforms {
            p.runtime = root
                .join("missing-projector")
                .to_string_lossy()
                .into_owned();
        }
        let mut state = Deployment {
            project: root,
            settings: Some(settings),
            open: true,
            ..Default::default()
        };
        state.actions.push_back(Action::Build);
        let mut app = App::new();
        app.insert_resource(state)
            .insert_resource(session)
            .insert_resource(EditorServices::new(PathBuf::new()).unwrap())
            .init_resource::<CommandQueue>()
            .add_message::<super::super::ViewChange>()
            .add_message::<AppExit>()
            .add_systems(
                Update,
                (
                    update,
                    super::super::execute_commands,
                    super::super::poll_jobs,
                )
                    .chain(),
            );
        (app, dir, path)
    }
    fn finish(app: &mut App) {
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(10);
        app.update();
        while app.world().resource::<Deployment>().busy {
            assert!(std::time::Instant::now() < deadline);
            std::thread::sleep(std::time::Duration::from_millis(5));
            app.update();
        }
    }
    #[test]
    fn build_saves_dirty_buffers_before_tool_preflight() {
        let (mut app, _dir, path) = setup();
        finish(&mut app);
        assert_eq!(std::fs::read_to_string(path).unwrap(), "edited");
        assert!(
            !app.world()
                .resource::<Session>()
                .workspace
                .has_dirty_documents()
        );
        let state = app.world().resource::<Deployment>();
        assert!(state.report.finished && !state.report.success); // Missing runtime, no build tool is launched.
        assert!(state.report.artifacts.is_empty());
    }
    #[test]
    fn save_conflict_prevents_any_build_or_upload() {
        let (mut app, _dir, path) = setup();
        std::fs::write(&path, "external change").unwrap();
        finish(&mut app);
        assert_eq!(std::fs::read_to_string(path).unwrap(), "external change");
        assert!(
            app.world()
                .resource::<Session>()
                .workspace
                .has_dirty_documents()
        );
        let state = app.world().resource::<Deployment>();
        assert!(state.builder.report().is_none());
        assert!(state.report.status.contains("unsaved"));
    }
}
