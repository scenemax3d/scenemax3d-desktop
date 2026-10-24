//! Project-scoped multiplayer settings orchestration.
use super::{Command, CommandQueue, Session};
use bevy::prelude::*;
use scenemax_ide_services::multiplayer::{Operation, Snapshot, Worker};
use std::{
    collections::{BTreeMap, VecDeque},
    path::PathBuf,
};
#[derive(Clone, Copy)]
pub(crate) enum Action {
    Open,
    Close,
    Save,
    Build,
    RunLocal,
    StopLocal,
    Cancel,
    OpenFolder,
    UseEnvironment,
    NoPassword,
}
#[derive(Resource, Default)]
pub(crate) struct State {
    pub open: bool,
    pub busy: bool,
    pub status: String,
    pub revision: u64,
    pub password: Option<String>,
    pending_password: Option<Option<String>>,
    pub fields: BTreeMap<String, String>,
    pub actions: VecDeque<Action>,
    server: Option<PathBuf>,
    snapshot: Option<Snapshot>,
    root: PathBuf,
    worker: Worker,
    local: scenemax_ide_services::multiplayer::local_server::LocalServer,
    run_after_build: bool,
    pub local_status: String,
}
impl State {
    fn start(&mut self, operation: Operation) {
        let building = matches!(
            &operation,
            Operation::Build(_) | Operation::BuildWithPassword(..)
        );
        match self.worker.start(self.root.clone(), operation) {
            Ok(()) => {
                self.busy = true;
                self.status = if building {
                    "Building multiplayer server…"
                } else {
                    "Working…"
                }
                .into();
            }
            Err(e) => {
                self.run_after_build = false;
                self.pending_password = None;
                self.status = e;
            }
        }
    }
    fn edited(&self) -> Result<Snapshot, String> {
        let mut snapshot = self.snapshot.clone().ok_or("Wait for settings to load")?;
        let get = |key: &str| self.fields.get(key).cloned().unwrap_or_default();
        snapshot.settings.server = get("server");
        snapshot.settings.project_guid = get("project_guid");
        snapshot.settings.session_name = get("session_name");
        snapshot.settings.platform = get("platform");
        snapshot.settings.port = get("port")
            .parse()
            .map_err(|_| "UDP port must be a whole number from 1 to 65535")?;
        snapshot.settings.session_id = get("session_id")
            .parse()
            .map_err(|_| "Session ID must be a nonnegative whole number")?;
        snapshot.settings.browser_gateway.enabled = get("gateway_enabled") == "Enabled";
        snapshot.settings.browser_gateway.websocket_url = get("gateway_url");
        snapshot.settings.browser_gateway.listen = get("gateway_listen");
        snapshot.settings.browser_gateway.allowed_origin = get("gateway_origin");
        snapshot.settings.validate()?;
        Ok(snapshot)
    }
}
pub(crate) fn update(
    mut state: ResMut<State>,
    session: Res<Session>,
    mut commands: ResMut<CommandQueue>,
    mut services: ResMut<super::EditorServices>,
) {
    if state.root != session.workspace.project().root() {
        state.local.stop();
        state.local_status.clear();
        state.run_after_build = false;
        state.password = None;
        if !state.root.as_os_str().is_empty() {
            state.open = false;
        }
        services
            .projector
            .set_password(session.workspace.project().root(), None);
    }
    if let Some(status) = state.local.poll() {
        state.local_status = status;
    }
    if state.busy && state.root != session.workspace.project().root() {
        state.worker.cancel();
    }
    if let Some(result) = state.worker.poll() {
        state.busy = false;
        match result {
            Ok(report) => {
                if let Some(password) = state.pending_password.take() {
                    if state.root == session.workspace.project().root() {
                        services.projector.set_password(&state.root, password);
                    }
                }
                if let Some(server) = report.server {
                    if state.run_after_build && state.root == session.workspace.project().root() {
                        match state.local.start_with_gateway(
                            &server,
                            report.snapshot.settings.port,
                            report
                                .gateway
                                .as_deref()
                                .map(|path| (path, &report.snapshot.settings.browser_gateway)),
                        ) {
                            Ok(()) => {
                                state.local_status =
                                    format!("UDP starting · port {}", report.snapshot.settings.port)
                            }
                            Err(error) => state.local_status = error,
                        }
                    }
                    state.server = Some(server);
                }
                state.run_after_build = false;
                if state.snapshot.is_none() {
                    let s = &report.snapshot.settings;
                    state.fields = BTreeMap::from([
                        ("server".into(), s.server.clone()),
                        ("port".into(), s.port.to_string()),
                        ("project_guid".into(), s.project_guid.clone()),
                        ("session_id".into(), s.session_id.to_string()),
                        ("session_name".into(), s.session_name.clone()),
                        ("platform".into(), s.platform.clone()),
                        (
                            "gateway_enabled".into(),
                            if s.browser_gateway.enabled {
                                "Enabled"
                            } else {
                                "Disabled"
                            }
                            .into(),
                        ),
                        (
                            "gateway_url".into(),
                            s.browser_gateway.websocket_url.clone(),
                        ),
                        ("gateway_listen".into(), s.browser_gateway.listen.clone()),
                        (
                            "gateway_origin".into(),
                            s.browser_gateway.allowed_origin.clone(),
                        ),
                    ]);
                    state.revision += 1;
                }
                state.snapshot = Some(report.snapshot);
                state.status = report.message;
            }
            Err(error) => {
                state.run_after_build = false;
                state.pending_password = None;
                state.status = error;
            }
        }
    }
    while let Some(action) = state.actions.pop_front() {
        match action {
            Action::Open if !state.busy => {
                state.root = session.workspace.project().root().to_owned();
                state.open = true;
                state.snapshot = None;
                state.fields.clear();
                state.server = None;
                state.revision += 1;
                state.start(Operation::Load);
            }
            Action::NoPassword if !state.busy => {
                state.password = Some(String::new());
                state.revision += 1;
                state.status = "No password selected. Save or Build Server to apply.".into();
            }
            Action::UseEnvironment if !state.busy => {
                state.password = None;
                state.revision += 1;
                state.status =
                    "Environment password selected. Save or Build Server to apply.".into();
            }
            Action::Close => {
                state.run_after_build = false;
                state.worker.cancel();
                state.open = false;
            }
            Action::StopLocal => {
                state.run_after_build = false;
                state.local.stop();
                state.local_status = "Local server and gateway stopped".into();
            }
            Action::Cancel if state.busy => {
                state.run_after_build = false;
                state.worker.cancel();
                state.status = "Cancelling multiplayer operation…".into();
            }
            Action::OpenFolder if state.root == session.workspace.project().root() => {
                if let Some(folder) = state.server.as_ref().and_then(|p| p.parent()) {
                    commands.0.push_back(Command::Explore(folder.to_owned()));
                } else {
                    state.status = "Build a server first to open its folder.".into();
                }
            }
            Action::Save | Action::Build | Action::RunLocal if !state.busy => {
                if state.root != session.workspace.project().root() {
                    state.status =
                        "The selected project changed; close and reopen Project Settings.".into();
                    continue;
                }
                match state.edited() {
                    Ok(snapshot) => {
                        if matches!(action, Action::RunLocal) {
                            if state.local.active() {
                                state.status = "The local server is already running. Stop it before changing its settings.".into();
                                continue;
                            }
                            let native = if cfg!(target_os = "windows") {
                                "windows-x64"
                            } else if cfg!(target_os = "macos") {
                                "macos-x64"
                            } else {
                                "linux-x64"
                            };
                            if snapshot.settings.platform != native {
                                state.status =
                                    format!("Select {native} to run the server on this computer.");
                                continue;
                            }
                            if !matches!(snapshot.settings.server.trim(), "127.0.0.1" | "localhost")
                            {
                                state.status = "Set Server IP / hostname to 127.0.0.1 to run and connect locally.".into();
                                continue;
                            }
                            if snapshot.settings.browser_gateway.enabled {
                                if let Err(error) =
                                    snapshot.settings.browser_gateway.validate_local()
                                {
                                    state.status = error;
                                    continue;
                                }
                            }
                            state.run_after_build = true;
                        }
                        state.pending_password = Some(state.password.clone());
                        let operation = if matches!(action, Action::Save) {
                            Operation::Save(snapshot)
                        } else if let Some(password) = state.password.clone() {
                            Operation::BuildWithPassword(snapshot, password)
                        } else {
                            Operation::Build(snapshot)
                        };
                        state.start(operation);
                    }
                    Err(error) => state.status = error,
                }
            }
            _ => {}
        }
    }
}
