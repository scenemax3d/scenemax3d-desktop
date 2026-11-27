//! Installed Windows Studio updates, isolated from the UI and engine.
use std::{
    io::{BufRead, BufReader, Write},
    path::{Path, PathBuf},
    process::{Command, Stdio},
    sync::{
        Mutex,
        mpsc::{self, Receiver, Sender},
    },
    thread,
    time::Duration,
};

/// A verified installer available in the installation's update cache.
#[derive(Clone, Debug, serde::Deserialize)]
pub struct Ready {
    /// Stable release version displayed in the notification.
    pub version: String,
    /// Current, downloading, ready, or busy.
    #[serde(default = "ready_status")]
    pub status: String,
    /// Release notes from GitHub, displayed as plain text.
    #[serde(default)]
    pub notes: String,
}
fn ready_status() -> String {
    "ready".into()
}

/// Background worker with an interruptible daily timer and explicit check requests.
pub struct Worker {
    requests: Sender<()>,
    events: Mutex<Receiver<Result<Ready, String>>>,
}
impl Worker {
    /// Bypass the daily interval for an explicit user request.
    pub fn check_now(&self) -> Result<(), String> {
        self.requests
            .send(())
            .map_err(|_| "Update worker is unavailable. Restart Studio and try again.".into())
    }
    /// Poll without blocking the UI.
    pub fn poll(&self) -> Option<Result<Ready, String>> {
        self.events.lock().ok()?.try_recv().ok()
    }
}

fn command(root: &Path, cache: &Path) -> Command {
    let powershell = std::env::var_os("SystemRoot")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(r"C:\Windows"))
        .join("System32/WindowsPowerShell/v1.0/powershell.exe");
    let mut command = Command::new(powershell);
    command
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
        ])
        .arg(cache.join("updater.ps1"))
        .arg("-Root")
        .arg(root)
        .arg("-Cache")
        .arg(cache);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    command
}

fn cache(root: &Path) -> PathBuf {
    root.join("user-state/updates")
}

/// Start a disk/network worker only for a versioned installed Windows copy.
pub fn start() -> Option<Worker> {
    if !cfg!(all(target_os = "windows", target_arch = "x86_64")) {
        return None;
    }
    let root = crate::workspace_state::installation_root()?;
    if !root.join("studio-release-tag.txt").is_file() && !root.join("studio-version.txt").is_file() {
        return None;
    }
    let (send, receive) = mpsc::channel();
    let (requests, requested) = mpsc::channel();
    thread::Builder::new()
        .name("studio-updates".into())
        .spawn(move || {
            let cache = cache(&root);
            let setup = std::fs::create_dir_all(&cache).and_then(|()| {
                let mut script = tempfile::NamedTempFile::new_in(&cache)?;
                script.write_all(include_bytes!("updates.ps1"))?;
                script.as_file().sync_all()?;
                script
                    .persist(cache.join("updater.ps1"))
                    .map_err(|e| e.error)?;
                Ok::<_, std::io::Error>(())
            });
            if let Err(error) = setup {
                let _ = send.send(Err(error.to_string()));
                return;
            }
            let mut force = false;
            loop {
                let result = (|| -> Result<(), String> {
                    let mut cmd = command(&root, &cache);
                    if force {
                        cmd.arg("-Force");
                    }
                    let mut child = cmd
                        .stdout(Stdio::piped())
                        .stderr(Stdio::null())
                        .spawn()
                        .map_err(|e| e.to_string())?;
                    if let Some(stdout) = child.stdout.take() {
                        for line in BufReader::new(stdout).lines() {
                            let line = line.map_err(|e| e.to_string())?;
                            if line.trim().is_empty() {
                                continue;
                            }
                            let event = serde_json::from_str(&line).map_err(|e| e.to_string());
                            let _ = send.send(event);
                        }
                    }
                    if !child.wait().map_err(|e| e.to_string())?.success() {
                        return Err(format!(
                            "Update check failed. Check your connection and try again. Details: {}",
                            cache.join("error.log").display()
                        ));
                    }
                    Ok(())
                })();
                if let Err(error) = result {
                    if send.send(Err(error)).is_err() {
                        break;
                    }
                }
                // Manual requests wake immediately; only automatic requests use the daily gate.
                force = match requested.recv_timeout(Duration::from_secs(3600)) {
                    Ok(()) => {
                        while requested.try_recv().is_ok() {}
                        true
                    }
                    Err(mpsc::RecvTimeoutError::Timeout) => false,
                    Err(mpsc::RecvTimeoutError::Disconnected) => break,
                };
            }
        })
        .ok()?;
    Some(Worker {
        requests,
        events: Mutex::new(receive),
    })
}

/// Launch the installer helper after the normal save/close handoff succeeded.
/// The helper waits for this process to release executable and DLL handles.
pub fn apply(project: &Path) -> std::io::Result<()> {
    let root = crate::workspace_state::installation_root()
        .ok_or_else(|| std::io::Error::other("Not an installed Studio copy"))?;
    command(&root, &cache(&root))
        .arg("-Operation")
        .arg("Apply")
        .arg("-ParentProcess")
        .arg(std::process::id().to_string())
        .arg("-Project")
        .arg(project)
        .spawn()?;
    Ok(())
}
