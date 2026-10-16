//! Git orchestration: captured project identity, editor guards and revision-aware reloads.
use super::{Command, CommandQueue, EditorServices, Session, ViewChange};
use bevy::prelude::*;
use scenemax_ide_core::git::{Config, Operation, Snapshot};
use scenemax_ide_services::git::Worker;
use std::{
    collections::{BTreeMap, BTreeSet, VecDeque},
    path::PathBuf,
};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
pub(crate) enum Page {
    #[default]
    Commit,
    Branches,
    History,
    Config,
    Clone,
    Init,
    Push,
    Pull,
    Rebase,
    Fetch,
    Stash,
    Pop,
    Ignore,
}
impl Page {
    pub fn menu(command: &str) -> Option<Self> {
        Some(match command {
            "git_init" => Self::Init,
            "git_clone" => Self::Clone,
            "git_commit" => Self::Commit,
            "git_push" => Self::Push,
            "git_pull" => Self::Pull,
            "git_pull_rebase" => Self::Rebase,
            "git_fetch" => Self::Fetch,
            "git_branches" => Self::Branches,
            "git_log" => Self::History,
            "git_stash" => Self::Stash,
            "git_stash_pop" => Self::Pop,
            "git_create_gitignore" => Self::Ignore,
            "git_config" => Self::Config,
            _ => return None,
        })
    }
    pub fn title(self) -> &'static str {
        match self {
            Self::Commit => "Stage & Commit",
            Self::Branches => "Branches",
            Self::History => "Commit History",
            Self::Config => "Configuration",
            Self::Clone => "Clone Repository",
            Self::Init => "Initialize Repository",
            Self::Push => "Push",
            Self::Pull => "Pull",
            Self::Rebase => "Pull (Rebase)",
            Self::Fetch => "Fetch",
            Self::Stash => "Stash Changes",
            Self::Pop => "Pop Stash",
            Self::Ignore => "Create .gitignore",
        }
    }
}
#[derive(Clone)]
pub(crate) enum Action {
    Open(Page),
    Close,
    Cancel,
    Refresh,
    Run(Operation),
    Confirm,
    Dismiss,
    SelectFile(String),
    SelectBranch(String),
    OpenClone,
}
#[derive(Resource, Default)]
pub(crate) struct State {
    pub open: bool,
    pub page: Page,
    pub busy: bool,
    pub status: String,
    pub output: String,
    pub output_revision: u64,
    pub snapshot: Snapshot,
    pub fields: BTreeMap<String, String>,
    pub selected: BTreeSet<String>,
    pub branch: String,
    pub show_remote: bool,
    pub confirm: Option<Operation>,
    pub actions: VecDeque<Action>,
    pub revision: u64,
    pub form_revision: u64,
    pub project: PathBuf,
    pub clone_ready: Option<PathBuf>,
    worker: Worker,
    operation: Option<Operation>,
    locked: bool,
}
impl State {
    pub fn field(&self, key: &str) -> String {
        self.fields.get(key).cloned().unwrap_or_default()
    }
    pub fn limit(&self) -> usize {
        self.field("limit")
            .parse::<usize>()
            .unwrap_or(100)
            .clamp(1, 500)
    }
    pub fn config(&self) -> Config {
        Config {
            name: self.field("name"),
            email: self.field("email"),
            branch: self.field("default_branch"),
            helper: self.field("helper"),
            origin: self.field("origin"),
        }
    }
    fn start(
        &mut self,
        operation: Operation,
        session: &mut Session,
        services: &EditorServices,
    ) -> Result<(), String> {
        if self.busy {
            return Err("Wait for the running Git operation".into());
        }
        if self.project != session.workspace.project().root() {
            return Err("Project changed; reopen the Git dialog".into());
        }
        if operation.mutates() {
            if session.workspace.has_dirty_documents() {
                return Err("Save all editor documents before this Git operation".into());
            }
            if services.is_saving()
                || services.storage.is_pending()
                || session.asset_operation_pending
            {
                return Err("Wait for the current file operation".into());
            }
            if services.projector.is_running() {
                return Err("Stop the game before changing repository files".into());
            }
        }
        let documents = if operation.changes_files() {
            session
                .workspace
                .documents()
                .map(|(id, d)| (id, d.snapshot()))
                .collect()
        } else {
            Vec::new()
        };
        self.worker.start(
            self.project.clone(),
            operation.clone(),
            documents,
            self.limit(),
        )?;
        self.locked = operation.mutates();
        if self.locked {
            session.asset_operation_pending = true;
        }
        self.operation = Some(operation);
        self.busy = true;
        self.status = "Git is working…".into();
        self.output.clear();
        self.output_revision += 1;
        Ok(())
    }
}
pub(crate) fn update(
    mut state: ResMut<State>,
    mut session: ResMut<Session>,
    services: Res<EditorServices>,
    mut queue: ResMut<CommandQueue>,
    mut changes: MessageWriter<ViewChange>,
) {
    if state.busy {
        let progress = state.worker.progress();
        if !progress.is_empty() && state.output != progress {
            state.output = progress;
            state.output_revision += 1;
        }
    }
    if let Some(outcome) = state.worker.poll() {
        state.busy = false;
        if state.locked {
            session.asset_operation_pending = false;
            state.locked = false;
        }
        let operation = state.operation.take();
        if state.project == session.workspace.project().root() {
            let success = outcome.result.is_ok();
            state.status = if success {
                "Completed".into()
            } else {
                "Git operation failed — see details below".into()
            };
            state.output = outcome.result.unwrap_or_else(|e| e);
            if let Some(folder) = outcome.folder {
                state.fields.insert(
                    "destination".into(),
                    folder.join("repository").to_string_lossy().into_owned(),
                );
                state.form_revision += 1;
            } else if !matches!(operation, Some(Operation::Browse)) {
                match outcome.snapshot {
                    Ok(snapshot) => {
                        if matches!(
                            operation,
                            Some(Operation::Inspect(_) | Operation::Configure(_))
                        ) {
                            for (k, v) in [
                                ("name", snapshot.config.name.clone()),
                                ("email", snapshot.config.email.clone()),
                                (
                                    "default_branch",
                                    if snapshot.config.branch.is_empty() {
                                        "main".into()
                                    } else {
                                        snapshot.config.branch.clone()
                                    },
                                ),
                                ("helper", snapshot.config.helper.clone()),
                                ("origin", snapshot.config.origin.clone()),
                            ] {
                                state.fields.insert(k.into(), v);
                            }
                            if state.page == Page::Config {
                                state.form_revision += 1;
                            }
                        }
                        state.snapshot = snapshot;
                    }
                    Err(error) => {
                        state.status = "Git unavailable or repository inspection failed".into();
                        state.output.push_str(&format!("\n{error}"));
                    }
                }
            }
            for (id, before, loaded) in outcome.documents {
                if !session.workspace.document(id).is_ok_and(|d| {
                    !d.is_dirty() && d.revision() == before.revision() && d.path() == before.path()
                }) {
                    continue;
                }
                match loaded {
                    Ok(doc) if doc.saved_bytes() != before.saved_bytes() => {
                        if let Ok(current) = session.workspace.document_mut(id) {
                            *current = doc;
                        }
                        changes.write(ViewChange::DocumentClosed(id));
                        changes.write(ViewChange::DocumentOpened(id));
                        changes.write(ViewChange::BufferChanged(id));
                    }
                    Err(error) => {
                        state
                            .output
                            .push_str(&format!("\nOpen document retained: {error}"));
                    }
                    _ => {}
                }
            }
            if let Some(Operation::Clone(_, target)) = operation.as_ref().filter(|_| success) {
                state.clone_ready = Some(target.clone());
            }
            if success && matches!(operation, Some(Operation::Commit(..))) {
                state.fields.insert("message".into(), String::new());
                state.form_revision += 1;
            }
            if operation.is_some_and(|op| op.mutates()) {
                queue.0.push_back(Command::Refresh);
            }
            state.revision += 1;
            state.output_revision += 1;
        } else if state.open {
            let page = state.page;
            state.actions.push_back(Action::Open(page));
        }
    }
    while let Some(action) = state.actions.pop_front() {
        let result = match action {
            Action::Open(page) if !state.busy => {
                let root = session.workspace.project().root().to_owned();
                if state.project != root {
                    state.fields.clear();
                    state.selected.clear();
                    state.snapshot = Snapshot::default();
                    state.clone_ready = None;
                }
                state.project = root;
                state
                    .fields
                    .entry("limit".into())
                    .or_insert_with(|| "100".into());
                state.page = page;
                state.open = true;
                state.confirm = None;
                state.form_revision += 1;
                let limit = state.limit();
                state.start(Operation::Inspect(limit), &mut session, &services)
            }
            Action::Close if !state.busy => {
                state.open = false;
                state.confirm = None;
                Ok(())
            }
            Action::Cancel if state.busy => {
                state.worker.cancel();
                state.status = "Cancelling Git…".into();
                Ok(())
            }
            Action::Dismiss => {
                state.confirm = None;
                state.revision += 1;
                Ok(())
            }
            Action::Confirm => {
                if let Some(op) = state.confirm.take() {
                    state.revision += 1;
                    state.start(op, &mut session, &services)
                } else {
                    Ok(())
                }
            }
            Action::Refresh => {
                let limit = state.limit();
                state.start(Operation::Inspect(limit), &mut session, &services)
            }
            Action::Run(op) => {
                if matches!(
                    op,
                    Operation::Discard(_)
                        | Operation::Delete(..)
                        | Operation::Merge(_)
                        | Operation::Abort(_)
                        | Operation::Configure(_)
                        | Operation::Push
                        | Operation::Commit(_, true)
                        | Operation::Stash
                        | Operation::Pop
                ) {
                    state.confirm = Some(op);
                    state.revision += 1;
                    Ok(())
                } else {
                    state.start(op, &mut session, &services)
                }
            }
            Action::SelectFile(path) => {
                if !state.selected.remove(&path) {
                    state.selected.insert(path);
                }
                state.revision += 1;
                Ok(())
            }
            Action::SelectBranch(name) => {
                state.branch = name;
                state.revision += 1;
                Ok(())
            }
            Action::OpenClone => {
                if let Some(path) = state.clone_ready.clone() {
                    queue.0.push_back(Command::OpenProject(path));
                    state.open = false;
                }
                Ok(())
            }
            _ => Ok(()),
        };
        if let Err(error) = result {
            state.status = error;
        }
    }
}
