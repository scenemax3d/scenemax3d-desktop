//! Project-scoped multiplayer settings orchestration.
use super::{Command, CommandQueue, Session};
use bevy::prelude::*;
use scenemax_ide_services::multiplayer::{Operation, RemoteAction, Snapshot, TestTarget, Worker};
use std::{
    collections::{BTreeMap, VecDeque},
    path::PathBuf,
};
#[derive(Clone, Copy)]
pub(crate) enum Action {
    TestGame(TestTarget),
    StopTestGame,
    SelectTab(Tab),
    TestRemote,
    RemoteStatus,
    DeployRemote,
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
#[derive(Clone, Copy, Default, PartialEq, Eq)]
pub(crate) enum Tab {
    #[default]
    Server,
    Browser,
    Deployment,
}
#[derive(Resource, Default)]
pub(crate) struct State {
    preview: scenemax_ide_services::deployment::preview::Preview,
    preview_message: String,
    test_after_save: Option<TestTarget>,
    pub queued_test: Option<TestTarget>,
    test_local: Option<scenemax_ide_services::multiplayer::LocalTest>,
    pub open: bool,
    pub tab: Tab,
    pub busy: bool,
    pub progress: scenemax_ide_services::multiplayer::Progress,
    started: Option<std::time::Instant>,
    saving: bool,
    close_after_save: bool,
    pub status: String,
    pub revision: u64,
    pub password: Option<String>,
    pending_password: Option<Option<String>>,
    built_password: Option<Option<String>>,
    pub fields: BTreeMap<String, String>,
    pub actions: VecDeque<Action>,
    server: Option<PathBuf>,
    bundles: Vec<scenemax_ide_services::multiplayer::SavedBundle>,
    snapshot: Option<Snapshot>,
    root: PathBuf,
    worker: Worker,
    local: scenemax_ide_services::multiplayer::local_server::LocalServer,
    pub local_status: String,
    local_events: String,
}
impl State {
    pub fn local_ready(&self) -> bool {
        self.local.ready()
    }
    fn local_bundle(&self) -> Option<&scenemax_ide_services::multiplayer::SavedBundle> {
        let platform = scenemax_ide_services::multiplayer::native_server_platform();
        self.bundles
            .iter()
            .find(|b| b.settings.platform == platform && b.server.is_file())
    }
    pub fn action_enabled(&self, action: Action) -> bool {
        match action {
            Action::Build => self.enabled() && !self.busy,
            Action::RunLocal => self.enabled() && !self.busy && self.local_bundle().is_some(),
            _ => true,
        }
    }
    pub fn enabled(&self) -> bool {
        self.fields.get("enabled").is_some_and(|v| v == "true")
    }
    pub fn enabled_for(&self, root: &std::path::Path) -> Option<bool> {
        (self.root == root && self.snapshot.is_some()).then(|| self.enabled())
    }
    pub fn request_browser_test(&mut self, root: &std::path::Path, target: TestTarget) {
        if self.root == root && self.snapshot.is_some() {
            self.open = true;
        } else {
            self.actions.push_back(Action::Open);
        }
        self.queued_test = Some(target);
    }
    pub fn progress_label(&self) -> String {
        if self.busy {
            format!(
                "{} · {}% of stages · {}s elapsed",
                self.progress.stage,
                self.progress.percent,
                self.started.map(|t| t.elapsed().as_secs()).unwrap_or(0)
            )
        } else if self.progress.stage.is_empty() {
            "Ready — operation progress and console output appear here".into()
        } else {
            self.progress.stage.clone()
        }
    }
    fn close(&mut self) {
        self.queued_test = None;
        self.test_after_save = None;
        if self.saving {
            self.close_after_save = true;
            self.status = "Saving settings before closing…".into();
            return;
        }
        self.worker.cancel();
        self.open = false;
    }
    fn finish_save(&mut self, succeeded: bool) {
        if self.saving && self.close_after_save && succeeded {
            self.open = false;
        }
        self.saving = false;
        self.close_after_save = false;
    }
    /// Refresh browser defaults only in response to edits to the UDP endpoint.
    pub fn follow_server_endpoint(&mut self) {
        let host = self.fields.get("server").map(|s| s.trim()).unwrap_or("");
        let host = host.trim_start_matches('[').trim_end_matches(']');
        let Some(port) = self
            .fields
            .get("port")
            .and_then(|p| p.parse::<u16>().ok())
            .filter(|p| *p > 0)
            .and_then(|p| p.checked_add(1))
        else {
            return;
        };
        if host.is_empty() {
            return;
        }
        let ip = host.parse::<std::net::IpAddr>().ok();
        let local = host.eq_ignore_ascii_case("localhost") || ip.is_some_and(|ip| ip.is_loopback());
        let listen_host = match ip {
            Some(std::net::IpAddr::V6(_)) if local => "[::1]",
            Some(std::net::IpAddr::V6(_)) => "[::]",
            _ if local => "127.0.0.1",
            _ => "0.0.0.0",
        };
        let scheme = if self
            .fields
            .get("gateway_url")
            .is_some_and(|url| url.starts_with("wss://"))
            || self
                .fields
                .get("gateway_origin")
                .is_some_and(|origin| origin.starts_with("https://"))
        {
            "wss"
        } else {
            "ws"
        };
        let url_host = if host.contains(':') {
            format!("[{host}]")
        } else {
            host.to_owned()
        };
        self.fields.insert(
            "gateway_url".into(),
            format!("{scheme}://{url_host}:{port}"),
        );
        self.fields
            .insert("gateway_listen".into(), format!("{listen_host}:{port}"));
    }
    pub fn console_log(&self) -> String {
        if self.local_events.is_empty() {
            self.progress.log.clone()
        } else {
            format!(
                "{}\nLocal server activity:\n{}",
                self.progress.log, self.local_events
            )
        }
    }
    fn set_local_status(&mut self, status: String) {
        if status != self.local_status {
            self.local_events.push_str(&status);
            self.local_events.push('\n');
            while self.local_events.len() > 16 * 1024 {
                let end = self
                    .local_events
                    .find('\n')
                    .unwrap_or(self.local_events.len() - 1)
                    + 1;
                self.local_events.drain(..end);
            }
        }
        self.local_status = status;
    }
    pub fn bundle_label(&self) -> String {
        self.server
            .as_ref()
            .and_then(|p| p.parent())
            .map(|p| p.display().to_string())
            .unwrap_or_else(|| {
                "No bundle built in this settings session. Use Build Server on the Server tab first.".into()
            })
    }
    fn start(&mut self, operation: Operation) {
        let saving = matches!(&operation, Operation::Save(_));
        let remote = match &operation {
            Operation::Remote(_, _, RemoteAction::Test) => Some("Checking SSH connection…"),
            Operation::Remote(_, _, RemoteAction::Status) => Some("Reading remote service status…"),
            Operation::Remote(_, _, RemoteAction::Deploy) => {
                Some("Uploading and installing server bundle…")
            }
            _ => None,
        };
        let building = matches!(
            &operation,
            Operation::Build(_) | Operation::BuildWithPassword(..) | Operation::BuildAll(..)
        );
        match self.worker.start(self.root.clone(), operation) {
            Ok(()) => {
                self.started = Some(std::time::Instant::now());
                self.progress = Default::default();
                self.progress.stage = "Starting".into();
                self.saving = saving;
                self.busy = true;
                self.status = if let Some(message) = remote {
                    message
                } else if building {
                    "Building multiplayer server…"
                } else {
                    "Working…"
                }
                .into();
            }
            Err(e) => {
                self.pending_password = None;
                self.status = e;
            }
        }
    }
    fn edited(&self) -> Result<Snapshot, String> {
        let mut snapshot = self.snapshot.clone().ok_or("Wait for settings to load")?;
        snapshot.settings.enabled = self.enabled();
        if !snapshot.settings.enabled {
            return Ok(snapshot);
        }
        let get = |key: &str| self.fields.get(key).cloned().unwrap_or_default();
        snapshot.settings.server = get("server");
        snapshot.settings.project_guid = get("project_guid");
        snapshot.settings.session_name = get("session_name");
        snapshot.settings.platform = "linux-x64".into();
        snapshot.settings.port = get("port")
            .parse()
            .map_err(|_| "UDP port must be a whole number from 1 to 65535")?;
        snapshot.settings.session_id = get("session_id")
            .parse()
            .map_err(|_| "Session ID must be a nonnegative whole number")?;
        snapshot.settings.browser_gateway.enabled = get("gateway_enabled") == "Enabled";
        if snapshot.settings.browser_gateway.enabled && snapshot.settings.port == u16::MAX {
            return Err(
                "Use a UDP port from 1 to 65534 to leave room for the browser gateway on port + 1"
                    .into(),
            );
        }
        snapshot.settings.browser_gateway.websocket_url = get("gateway_url");
        snapshot.settings.browser_gateway.listen = get("gateway_listen");
        snapshot.settings.browser_gateway.allowed_origin = get("gateway_origin");
        let d = &mut snapshot.settings.deployment;
        d.method = if get("remote_method") == "PuTTY session" {
            "putty"
        } else {
            "openssh"
        }
        .into();
        d.host = get("remote_host");
        d.user = get("remote_user");
        d.port = get("remote_port")
            .parse()
            .map_err(|_| "SSH port must be 0 (client default) or 1–65535")?;
        d.identity_file = get("remote_key");
        d.directory = get("remote_directory");
        d.service = get("remote_service");
        d.sudo = get("remote_sudo") == "Use sudo";
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
        state.preview.stop();
        state.test_after_save = None;
        state.local.stop();
        state.test_local = None;
        state.local_status.clear();
        state.local_events.clear();
        state.password = None;
        if !state.root.as_os_str().is_empty() {
            state.open = false;
        }
        services
            .projector
            .set_password(session.workspace.project().root(), None);
    }
    if let Some(local) = &mut state.test_local {
        if let Some(status) = local.server.poll() {
            state.set_local_status(status);
        }
    }
    if let Some(status) = state.local.poll() {
        state.set_local_status(status);
    }
    if state.test_after_save.is_some() && !services.is_saving() && !services.storage.is_pending() {
        let target = state.test_after_save.take().unwrap();
        if session.workspace.has_dirty_documents() {
            state.status = "Test Game cancelled: resolve the document save error and retry.".into();
        } else {
            state.actions.push_back(Action::TestGame(target));
        }
    }
    let preview = state.preview.status();
    if !preview.message.is_empty() && preview.message != state.preview_message {
        state.preview_message = preview.message.clone();
        state.status = preview.message.clone();
        state.progress.stage = if preview.url.is_some() {
            "Local browser game ready".into()
        } else {
            preview.message.clone()
        };
        state
            .progress
            .log
            .push_str(&format!("\n{}", preview.message));
    }
    if state.busy && state.root != session.workspace.project().root() {
        state.worker.cancel();
    }
    if let Some(result) = state.worker.poll() {
        state.progress = state.worker.progress();
        state.busy = false;
        state.finish_save(result.is_ok());
        match result {
            Ok(report) => {
                if let Some(artifact) = report.web_artifact {
                    if state.open && state.root == session.workspace.project().root() {
                        state.test_local = report.local_test;
                        let result = state.preview.start(artifact);
                        if let Err(error) = result {
                            state
                                .progress
                                .log
                                .push_str(&format!("\nLocal preview failed: {error}"));
                            state.progress.stage = "Local preview failed".into();
                        }
                    }
                }
                if report.server.is_some() {
                    state.built_password = state.pending_password.clone();
                }
                if let Some(password) = state.pending_password.take() {
                    if state.root == session.workspace.project().root() {
                        services.projector.set_password(&state.root, password);
                    }
                }
                if let Some(server) = report.server {
                    state.server = Some(server);
                }
                if state.snapshot.is_none() {
                    let s = &report.snapshot.settings;
                    state.fields = BTreeMap::from([
                        ("enabled".into(), s.enabled.to_string()),
                        ("server".into(), s.server.clone()),
                        ("port".into(), s.port.to_string()),
                        ("project_guid".into(), s.project_guid.clone()),
                        ("session_id".into(), s.session_id.to_string()),
                        ("session_name".into(), s.session_name.clone()),
                        (
                            "remote_method".into(),
                            if s.deployment.method == "putty" {
                                "PuTTY session"
                            } else {
                                "OpenSSH"
                            }
                            .into(),
                        ),
                        ("remote_host".into(), s.deployment.host.clone()),
                        ("remote_user".into(), s.deployment.user.clone()),
                        ("remote_port".into(), s.deployment.port.to_string()),
                        ("remote_key".into(), s.deployment.identity_file.clone()),
                        ("remote_directory".into(), s.deployment.directory.clone()),
                        ("remote_service".into(), s.deployment.service.clone()),
                        (
                            "remote_sudo".into(),
                            if s.deployment.sudo {
                                "Use sudo"
                            } else {
                                "Root login"
                            }
                            .into(),
                        ),
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
                state.bundles = scenemax_ide_services::multiplayer::saved_bundles(&state.root);
                state.snapshot = Some(report.snapshot);
                state.status = report.message;
            }
            Err(error) => {
                state.queued_test = None;
                state.pending_password = None;
                state.status = error;
            }
        }
    }
    if state.busy {
        state.progress = state.worker.progress();
    }
    if !state.busy
        && state.snapshot.is_some()
        && !state.actions.iter().any(|a| matches!(a, Action::Open))
    {
        if let Some(target) = state.queued_test.take() {
            state.tab = Tab::Deployment;
            state.revision += 1;
            state.actions.push_back(Action::TestGame(target));
        }
    }
    while let Some(action) = state.actions.pop_front() {
        if !state.enabled()
            && matches!(
                action,
                Action::TestGame(_)
                    | Action::TestRemote
                    | Action::RemoteStatus
                    | Action::DeployRemote
                    | Action::Build
                    | Action::RunLocal
                    | Action::SelectTab(_)
                    | Action::OpenFolder
                    | Action::UseEnvironment
                    | Action::NoPassword
            )
        {
            state.status = "Enable Multiplayer game in Project Settings first.".into();
            continue;
        }
        match action {
            Action::TestGame(target) if !state.busy => {
                let result = (|| -> Result<(), String> {
                    if state.root != session.workspace.project().root() {
                        return Err("The project changed before browser testing started. Try again from the current project.".into());
                    }
                    let snapshot = state.edited()?;
                    if session.workspace.has_dirty_documents()
                        || services.is_saving()
                        || services.storage.is_pending()
                    {
                        state.test_after_save = Some(target);
                        commands.0.push_back(Command::SaveAll);
                        state.status = "Saving game scripts before browser build…".into();
                        return Ok(());
                    }
                    let entry = session
                        .workspace
                        .project()
                        .entry_point()
                        .ok_or("This project has no main script")?
                        .to_owned();
                    state.preview.stop();
                    state.preview_message.clear();
                    state.test_local = None;
                    if target == TestTarget::Local {
                        state.local.stop();
                    }
                    state.start(Operation::TestGame(snapshot, entry, target));
                    Ok(())
                })();
                if let Err(error) = result {
                    state.status = error;
                }
            }
            Action::StopTestGame => {
                state.preview.stop();
                state.test_local = None;
                state.status = "Local browser test stopped.".into();
            }
            Action::SelectTab(tab) => {
                state.tab = tab;
                state.revision += 1;
            }
            Action::TestRemote | Action::RemoteStatus | Action::DeployRemote if !state.busy => {
                if state.root != session.workspace.project().root() {
                    continue;
                }
                match state.edited() {
                    Ok(snapshot) => {
                        let kind = match action {
                            Action::TestRemote => RemoteAction::Test,
                            Action::RemoteStatus => RemoteAction::Status,
                            _ => RemoteAction::Deploy,
                        };
                        if matches!(action, Action::DeployRemote)
                            && (state.server.is_none()
                                || state.built_password.as_ref() != Some(&state.password))
                        {
                            state.status =
                                "Use Build Server on the Server tab before deploying.".into();
                            continue;
                        }
                        let bundle = state
                            .server
                            .as_ref()
                            .and_then(|p| p.parent())
                            .map(PathBuf::from);
                        state.start(Operation::Remote(snapshot, bundle, kind));
                    }
                    Err(error) => state.status = error,
                }
            }
            Action::Open if !state.busy => {
                state.root = session.workspace.project().root().to_owned();
                state.open = true;
                state.snapshot = None;
                state.fields.clear();
                state.server = None;
                state.bundles.clear();
                state.built_password = None;
                state.tab = Tab::Server;
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
            Action::Close => state.close(),
            Action::StopLocal => {
                state.test_local = None;
                state.local.stop();
                state.set_local_status("Local server and gateway stopped".into());
            }
            Action::Cancel if state.busy => {
                state.test_after_save = None;
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
                    Ok(mut snapshot) => {
                        if matches!(action, Action::RunLocal) {
                            snapshot.settings.platform =
                                scenemax_ide_services::multiplayer::native_server_platform().into();
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
                            let Some(bundle) = state.local_bundle().cloned() else {
                                state.status = "Build a server first.".into();
                                continue;
                            };
                            if let Err(error) = bundle.validate(&snapshot.settings) {
                                state.status = error;
                                continue;
                            }
                            state.test_local = None;
                            state.local.stop();
                            let local_status = match state.local.start_with_gateway(
                                &bundle.server,
                                snapshot.settings.port,
                                bundle
                                    .gateway
                                    .as_deref()
                                    .map(|p| (p, &snapshot.settings.browser_gateway)),
                            ) {
                                Ok(()) => format!("UDP starting · port {}", snapshot.settings.port),
                                Err(error) => error,
                            };
                            state.set_local_status(local_status);
                            state.status = state.local_status.clone();
                            continue;
                        }
                        state.pending_password = Some(state.password.clone());
                        let operation = if matches!(action, Action::Save) {
                            Operation::Save(snapshot)
                        } else {
                            Operation::BuildAll(snapshot, state.password.clone())
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

#[cfg(test)]
mod save_close_tests {
    use super::*;

    #[test]
    fn console_keeps_local_transitions_without_repeating_health_polls() {
        let mut state = State::default();
        state.progress.log = "Build completed\n".into();
        state.set_local_status("UDP starting".into());
        state.set_local_status("UDP listening".into());
        state.set_local_status("UDP listening".into());
        state.set_local_status("Local server and gateway stopped".into());
        let log = state.console_log();
        assert!(log.contains("Build completed"));
        assert!(log.contains("UDP starting"));
        assert_eq!(log.matches("UDP listening").count(), 1);
        assert!(log.contains("Local server and gateway stopped"));
    }

    #[test]
    fn local_run_availability_tracks_builds_platform_and_busy_state() {
        let dir = tempfile::tempdir().unwrap();
        let server = dir.path().join("server");
        std::fs::write(&server, b"test").unwrap();
        let mut state = State::default();
        state.fields.insert("enabled".into(), "true".into());
        state.fields.insert("platform".into(), "windows-x64".into());
        assert!(!state.action_enabled(Action::RunLocal));
        state
            .bundles
            .push(scenemax_ide_services::multiplayer::SavedBundle {
                server: server.clone(),
                gateway: None,
                settings: scenemax_ide_core::multiplayer::Settings {
                    platform: scenemax_ide_services::multiplayer::native_server_platform().into(),
                    ..Default::default()
                },
            });
        assert!(state.action_enabled(Action::RunLocal));
        assert!(state.action_enabled(Action::Build));
        state.busy = true;
        assert!(!state.action_enabled(Action::RunLocal));
        assert!(!state.action_enabled(Action::Build));
        state.busy = false;
        state.fields.insert("platform".into(), "linux-x64".into());
        assert!(state.action_enabled(Action::RunLocal));
        state.fields.insert("platform".into(), "windows-x64".into());
        std::fs::remove_file(server).unwrap();
        assert!(!state.action_enabled(Action::RunLocal));
        assert!(!state.local_ready());
    }

    #[test]
    fn closing_after_save_waits_for_success_and_keeps_errors_visible() {
        let mut state = State::default();
        state.open = true;
        state.saving = true;
        state.close();
        assert!(state.open);
        assert!(state.close_after_save);
        state.finish_save(true);
        assert!(!state.open);

        state.open = true;
        state.saving = true;
        state.close();
        state.finish_save(false);
        assert!(state.open);
        assert!(!state.close_after_save);
        state.close();
        assert!(!state.open);
    }
}
