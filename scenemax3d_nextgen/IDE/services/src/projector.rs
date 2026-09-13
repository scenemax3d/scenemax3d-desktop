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
        }
    }
    /// Whether this service still owns a running or unreaped process.
    pub fn is_running(&self) -> bool {
        self.child.is_some()
    }
    /// Validate availability before a Run command performs saves.
    pub fn validate(&self) -> Result<(), ServiceError> {
        if self.child.is_some() {
            return Err(ServiceError::Busy("Projector"));
        }
        if !self.executable.is_file() {
            return Err(ServiceError::MissingProjector(self.executable.clone()));
        }
        Ok(())
    }
    /// Launch using typed arguments; never interpolate a shell command.
    pub fn start(&mut self, project_root: &Path, script: &Path) -> Result<(), ServiceError> {
        self.validate()?;
        let started = std::time::SystemTime::now();
        let mut child = command(&self.executable, project_root, script).spawn()?;
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
        if let Some(child) = self.child.as_mut() {
            child.kill()?;
        }
        Ok(())
    }
    /// Reap an exited child without blocking a frame.
    pub fn poll_exit(&mut self) -> Result<Option<ExitStatus>, ServiceError> {
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
        if let Some(child) = self.child.as_mut() {
            let _ = child.kill();
            let _ = child.wait();
        }
    }
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
