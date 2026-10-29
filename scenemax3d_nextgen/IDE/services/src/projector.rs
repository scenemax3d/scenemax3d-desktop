use crate::ServiceError;
use std::{
    io::{BufRead, BufReader, Read},
    path::{Path, PathBuf},
    process::{Child, Command, ExitStatus, Stdio},
    sync::{Mutex, mpsc},
    thread,
};

/// Owns exactly one standalone projector process and its bounded output queue.
pub struct ProjectorProcess {
    executable: PathBuf,
    child: Option<Child>,
    output: Mutex<mpsc::Receiver<String>>,
    sender: mpsc::SyncSender<String>,
    runtime_log: Option<crate::runtime_log::RuntimeLog>,
    catalog: Option<PathBuf>,
    pending: Option<PendingLaunch>,
    password: Option<(PathBuf, String)>,
}
struct PendingLaunch {
    root: PathBuf,
    result: Mutex<mpsc::Receiver<Result<Command, ServiceError>>>,
    handle: thread::JoinHandle<()>,
    cancelled: bool,
}
impl ProjectorProcess {
    /// Configure a projector executable independently from the IDE binary.
    pub fn new(executable: PathBuf) -> Self {
        let (sender, output) = mpsc::sync_channel(256);
        Self {
            executable,
            child: None,
            output: Mutex::new(output),
            sender,
            runtime_log: None,
            catalog: None,
            pending: None,
            password: None,
        }
    }
    /// Whether this service still owns a running or unreaped process.
    pub fn is_running(&self) -> bool {
        self.child.is_some() || self.pending.is_some()
    }
    /// Whether connection defaults are still being prepared off the UI thread.
    pub fn is_preparing(&self) -> bool {
        self.pending.is_some()
    }
    /// Select the same explicit Java catalog as the IDE's project chooser.
    pub fn set_catalog(&mut self, catalog: Option<PathBuf>) {
        self.catalog = catalog;
    }
    /// Set a session-only password for this project; None restores environment fallback.
    pub fn set_password(&mut self, root: &Path, password: Option<String>) {
        self.password = password.map(|value| (root.to_owned(), value));
    }
    /// Validate availability before a Run command performs saves.
    pub fn validate(&self) -> Result<(), ServiceError> {
        if self.is_running() {
            return Err(ServiceError::Busy("Projector"));
        }
        if !self.executable.is_file() {
            return Err(ServiceError::MissingProjector(self.executable.clone()));
        }
        Ok(())
    }
    /// Prepare launch asynchronously; poll_exit starts the child with typed arguments.
    pub fn start(&mut self, project_root: &Path, script: &Path) -> Result<(), ServiceError> {
        self.validate()?;
        let root = project_root.to_owned();
        let script = script.to_owned();
        let executable = self.executable.clone();
        let catalog = self.catalog.clone();
        let password = self
            .password
            .as_ref()
            .filter(|(path, _)| path == &root)
            .map(|(_, value)| value.clone());
        let work_root = root.clone();
        let (tx, rx) = mpsc::sync_channel(1);
        let handle = thread::Builder::new()
            .name("projector-launch".into())
            .spawn(move || {
                let prepared =
                    prepare_command(&executable, &work_root, &script, catalog.as_deref()).map(
                        |mut command| {
                            if let Some(password) = password {
                                command.env("SCENEMAX_MULTIPLAYER_PASSWORD", password);
                            }
                            command
                        },
                    );
                let _ = tx.send(prepared);
            })?;
        self.pending = Some(PendingLaunch {
            root,
            result: Mutex::new(rx),
            handle,
            cancelled: false,
        });
        Ok(())
    }
    fn spawn_prepared(
        &mut self,
        mut command: Command,
        project_root: &Path,
    ) -> Result<(), ServiceError> {
        let started = std::time::SystemTime::now();
        let mut child = command.spawn()?;
        if let Some(stream) = child.stdout.take() {
            read_output(stream, self.sender.clone());
        }
        if let Some(stream) = child.stderr.take() {
            read_output(stream, self.sender.clone());
        }
        match crate::runtime_log::RuntimeLog::start(
            project_root.join("scenemax-nextgen-runtime.log"),
            started,
            self.sender.clone(),
        ) {
            Ok(log) => self.runtime_log = Some(log),
            Err(error) => {
                let _ = child.kill();
                let _ = child.wait();
                return Err(error.into());
            }
        }
        self.child = Some(child);
        Ok(())
    }
    /// Terminate only the child owned by this service.
    pub fn stop(&mut self) -> Result<(), ServiceError> {
        if let Some(pending) = &mut self.pending {
            pending.cancelled = true;
        }
        if let Some(child) = self.child.as_mut() {
            child.kill()?;
        }
        Ok(())
    }
    /// Reap an exited child without blocking a frame.
    pub fn poll_exit(&mut self) -> Result<Option<ExitStatus>, ServiceError> {
        if let Some(pending) = self.pending.as_mut() {
            let result = pending
                .result
                .get_mut()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .try_recv();
            match result {
                Err(mpsc::TryRecvError::Empty) => return Ok(None),
                result => {
                    let pending = self.pending.take().ok_or(ServiceError::WorkerUnavailable)?;
                    let _ = pending.handle.join();
                    if pending.cancelled {
                        let _ = self
                            .sender
                            .try_send("Run cancelled before projector launch".into());
                        return Ok(None);
                    }
                    let command = result.map_err(|_| ServiceError::WorkerUnavailable)??;
                    self.spawn_prepared(command, &pending.root)?;
                    let _ = self.sender.try_send("Bevy projector started".into());
                }
            }
        }
        if let Some(child) = self.child.as_mut()
            && let Some(status) = child.try_wait()?
        {
            self.child = None;
            self.runtime_log = None;
            return Ok(Some(status));
        }
        Ok(None)
    }
    /// Drain a bounded amount of log output per frame.
    pub fn drain_output(&mut self, maximum: usize) -> Vec<String> {
        self.output
            .get_mut()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .try_iter()
            .take(maximum)
            .collect()
    }
}
impl Drop for ProjectorProcess {
    fn drop(&mut self) {
        if let Some(pending) = self.pending.take() {
            // Preparation only reads files; it never spawns a child on its own.
            let _ = pending.handle.join();
        }
        if let Some(child) = self.child.as_mut() {
            let _ = child.kill();
            let _ = child.wait();
        }
    }
}
fn prepare_command(
    executable: &Path,
    root: &Path,
    script: &Path,
    catalog: Option<&Path>,
) -> Result<Command, ServiceError> {
    let defaults = crate::multiplayer::launch::defaults(root, catalog)
        .map_err(|e| ServiceError::Process(std::io::Error::other(e)))?;
    let mut command = command(executable, root, script);
    if let Some(shared) = crate::resource_roots::shared(root) {
        command.env("SCENEMAX_BUILTIN_RESOURCES", shared);
    }
    // Clear inherited defaults to prevent settings leaking across project runs.
    command.env_remove("SCENEMAX_MULTIPLAYER_DEFAULTS");
    if let Some(defaults) = defaults {
        command.env("SCENEMAX_MULTIPLAYER_DEFAULTS", defaults);
    }
    Ok(command)
}
fn command(executable: &Path, project_root: &Path, script: &Path) -> Command {
    let mut command = Command::new(executable);
    command
        .arg("run")
        .arg("--project-root")
        .arg(project_root)
        .arg("--script")
        .arg(script)
        .current_dir(project_root)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
}
fn read_output(stream: impl Read + Send + 'static, sender: mpsc::SyncSender<String>) {
    thread::spawn(move || {
        let mut reader = BufReader::new(stream);
        let mut chunk = Vec::with_capacity(4096);
        loop {
            chunk.clear();
            match reader.by_ref().take(4096).read_until(b'\n', &mut chunk) {
                Ok(0) | Err(_) => break,
                Ok(_) => {
                    let _ = sender.try_send(String::from_utf8_lossy(&chunk).trim_end().to_owned());
                }
            }
        }
    });
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn session_password_is_project_scoped_and_empty_is_explicit() {
        let dir = tempfile::tempdir().unwrap();
        let catalog = dir.path().join("catalog.json");
        std::fs::write(&catalog, "{\"projects\":[]}").unwrap();
        let mut process = ProjectorProcess::new(std::env::current_exe().unwrap());
        process.set_catalog(Some(catalog));
        for value in [
            Some("private-password".to_owned()),
            Some(String::new()),
            None,
        ] {
            process.set_password(dir.path(), value.clone());
            process.start(dir.path(), Path::new("main")).unwrap();
            let pending = process.pending.take().unwrap();
            pending.handle.join().unwrap();
            let command = pending.result.lock().unwrap().recv().unwrap().unwrap();
            let actual = command
                .get_envs()
                .find(|(key, _)| *key == "SCENEMAX_MULTIPLAYER_PASSWORD")
                .and_then(|(_, value)| value)
                .map(|s| s.to_string_lossy().into_owned());
            assert_eq!(actual, value);
        }
        process.set_password(Path::new("another-project"), Some("unrelated".into()));
        process.start(dir.path(), Path::new("main")).unwrap();
        let pending = process.pending.take().unwrap();
        pending.handle.join().unwrap();
        let command = pending.result.lock().unwrap().recv().unwrap().unwrap();
        assert!(
            !command
                .get_envs()
                .any(|(key, _)| key == "SCENEMAX_MULTIPLAYER_PASSWORD")
        );
    }

    #[test]
    fn invalid_catalog_preparation_reports_error_and_releases_run() {
        let dir = tempfile::tempdir().unwrap();
        let catalog = dir.path().join("catalog.json");
        std::fs::write(&catalog, "invalid").unwrap();
        let mut process = ProjectorProcess::new(std::env::current_exe().unwrap());
        process.set_catalog(Some(catalog));
        process.start(dir.path(), Path::new("main")).unwrap();
        assert!(process.is_preparing());
        assert!(process.validate().is_err());
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
        loop {
            if let Err(error) = process.poll_exit() {
                assert!(error.to_string().contains("catalog"));
                break;
            }
            assert!(std::time::Instant::now() < deadline);
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert!(!process.is_running());
        process.validate().unwrap();
    }

    #[test]
    fn stopping_preparation_never_spawns_and_clears_inherited_defaults() {
        let dir = tempfile::tempdir().unwrap();
        let catalog = dir.path().join("catalog.json");
        std::fs::write(&catalog, "{\"projects\":[]}").unwrap();
        let executable = std::env::current_exe().unwrap();
        let command =
            prepare_command(&executable, dir.path(), Path::new("main"), Some(&catalog)).unwrap();
        assert!(
            command
                .get_envs()
                .any(|(key, value)| key == "SCENEMAX_MULTIPLAYER_DEFAULTS" && value.is_none())
        );
        let mut process = ProjectorProcess::new(executable);
        process.set_catalog(Some(catalog));
        process.start(dir.path(), Path::new("main")).unwrap();
        process.stop().unwrap();
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
        while process.is_running() {
            process.poll_exit().unwrap();
            assert!(process.child.is_none());
            assert!(std::time::Instant::now() < deadline);
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert!(!dir.path().join("scenemax-nextgen-runtime.log").exists());
    }

    #[test]
    fn paths_with_spaces_are_individual_arguments_not_shell_text() {
        let command = command(
            Path::new("projector"),
            Path::new("project with spaces"),
            Path::new("project with spaces/scripts/main"),
        );
        let args: Vec<_> = command
            .get_args()
            .map(|s| s.to_string_lossy().into_owned())
            .collect();
        assert_eq!(
            args,
            [
                "run",
                "--project-root",
                "project with spaces",
                "--script",
                "project with spaces/scripts/main"
            ]
        );
    }
}
