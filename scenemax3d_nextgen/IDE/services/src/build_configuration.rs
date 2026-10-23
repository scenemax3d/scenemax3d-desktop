//! Background prerequisite discovery and official Windows installer orchestration.
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeMap,
    io,
    process::{Command, Stdio},
    sync::{Arc, Mutex},
    thread,
};

/// A discovered build prerequisite.
#[derive(Clone, Default, Deserialize, Serialize)]
pub struct Package {
    /// Display name.
    pub name: String,
    /// Whether all required files were found.
    pub installed: bool,
    /// Version and installation location, or missing components.
    pub detail: String,
}
/// Discovery and installer progress snapshot.
#[derive(Clone, Default, Deserialize, Serialize)]
pub struct Report {
    /// Discovered packages.
    #[serde(default)]
    pub packages: Vec<Package>,
    /// Human-readable current phase.
    #[serde(default)]
    pub status: String,
    /// Download percentage; installation itself has indeterminate progress.
    #[serde(default)]
    pub progress: f32,
    /// Bounded console output.
    #[serde(default)]
    pub log: String,
    /// A worker is still running.
    #[serde(skip)]
    pub busy: bool,
    /// Build process environment discovered on this computer.
    #[serde(default)]
    pub environment: BTreeMap<String, String>,
    /// Absolute Cargo executable.
    #[serde(default)]
    pub cargo: String,
}
/// Requested prerequisite operation.
#[derive(Clone, Copy)]
pub enum Operation {
    /// Refresh local installations without downloading anything.
    Scan,
    /// Install the workspace's Rust toolchain through rustup.
    Rust,
    /// Install MSVC, Windows SDK and CMake through Visual Studio setup.
    Cpp,
    /// Install the browser target and matching JavaScript binding generator.
    Web,
}
/// Single background worker; installer processes remain alive if the dialog closes.
#[derive(Default)]
pub struct Setup {
    report: Arc<Mutex<Report>>,
}
impl Setup {
    /// Read a snapshot without waiting for a process.
    pub fn report(&self) -> Report {
        self.report.lock().map(|r| r.clone()).unwrap_or_default()
    }
    /// Start a scan or explicit installation, rejecting concurrent work.
    pub fn start(&self, operation: Operation) {
        let Ok(mut r) = self.report.lock() else {
            return;
        };
        if r.busy {
            return;
        }
        r.busy = true;
        r.progress = 0.;
        r.status = "Searching installed build tools…".into();
        r.log.clear();
        drop(r);
        let report = self.report.clone();
        thread::spawn(move || {
            let result = run(operation, &report);
            if let Ok(mut r) = report.lock() {
                r.busy = false;
                if let Err(e) = result {
                    r.status = format!("Setup failed: {e}");
                }
            }
        });
    }
}
fn config_path() -> Option<std::path::PathBuf> {
    std::env::var_os("LOCALAPPDATA")
        .map(|p| std::path::PathBuf::from(p).join("SceneMax/build-tools.json"))
}
fn saved() -> Report {
    config_path()
        .and_then(|p| std::fs::read(p).ok())
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default()
}
/// Resolve the previously discovered Cargo executable, falling back to PATH.
pub fn cargo_path() -> std::path::PathBuf {
    let r = saved();
    if !r.cargo.is_empty() && std::path::Path::new(&r.cargo).is_file() {
        r.cargo.into()
    } else {
        "cargo".into()
    }
}
/// Apply detected build paths only to the child process, never the IDE's global environment.
pub fn prepare(command: &mut Command) {
    apply_environment(command, saved().environment);
}
/// Binding generator version pinned by the browser projector.
pub const WASM_BINDGEN_VERSION: &str = "0.2.108";
/// Resolve the managed binding generator without changing other Rust installations.
pub fn wasm_bindgen_path() -> std::path::PathBuf {
    if let Some(root) = std::env::var_os("LOCALAPPDATA") {
        let path = std::path::PathBuf::from(root)
            .join("SceneMax/web-tools")
            .join(WASM_BINDGEN_VERSION)
            .join("bin/wasm-bindgen.exe");
        if path.is_file() {
            return path;
        }
    }
    let cargo = cargo_path();
    if let Some(parent) = cargo.parent() {
        let path = parent.join(if cfg!(windows) {
            "wasm-bindgen.exe"
        } else {
            "wasm-bindgen"
        });
        if path.is_file() {
            return path;
        }
    }
    "wasm-bindgen".into()
}
fn apply_environment(command: &mut Command, environment: BTreeMap<String, String>) {
    for (key, value) in environment {
        if key.eq_ignore_ascii_case("PATH") {
            let mut paths: Vec<_> = std::env::split_paths(&value).collect();
            if let Some(current) = std::env::var_os("PATH") {
                for path in std::env::split_paths(&current) {
                    if !paths.contains(&path) {
                        paths.push(path);
                    }
                }
            }
            if let Ok(path) = std::env::join_paths(paths) {
                command.env("PATH", path);
            }
        } else {
            command.env(key, value);
        }
    }
}
fn line(report: &Arc<Mutex<Report>>, line: &str) {
    if let Ok(mut r) = report.lock() {
        if let Some(json) = line.strip_prefix("SCENEMAX_RESULT ") {
            if let Ok(found) = serde_json::from_str::<Report>(json) {
                r.packages = found.packages;
                r.environment = found.environment;
                r.cargo = found.cargo;
            }
        } else if let Some(value) = line.strip_prefix("SCENEMAX_PROGRESS ") {
            if let Ok(value) = value.parse::<f32>() {
                r.progress = value.clamp(0., 100.);
            }
        } else {
            if let Some(status) = line.strip_prefix("SCENEMAX_STATUS ") {
                r.status = status.into();
            }
            r.log
                .push_str(line.strip_prefix("SCENEMAX_STATUS ").unwrap_or(line));
            r.log.push('\n');
            if r.log.len() > 24_000 {
                let mut cut = r.log.len() - 20_000;
                while !r.log.is_char_boundary(cut) {
                    cut += 1;
                }
                r.log.drain(..cut);
            }
        }
    }
}
fn run(operation: Operation, report: &Arc<Mutex<Report>>) -> io::Result<()> {
    if !cfg!(all(windows, target_arch = "x86_64")) {
        return Err(io::Error::other(
            "Automatic prerequisite setup currently supports Windows x64.",
        ));
    }
    let directory = tempfile::tempdir()?;
    let script = directory.path().join("setup.ps1");
    std::fs::write(&script, include_str!("build_configuration.ps1"))?;
    let mut command = Command::new("powershell.exe");
    command
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
        ])
        .arg(script)
        .arg("-Operation")
        .arg(match operation {
            Operation::Scan => "Scan",
            Operation::Rust => "Rust",
            Operation::Cpp => "Cpp",
            Operation::Web => "Web",
        })
        .arg("-Toolchain")
        .arg(toolchain())
        .arg("-BindgenVersion")
        .arg(WASM_BINDGEN_VERSION)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    let mut child = command.spawn()?;
    use std::io::BufRead;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| io::Error::other("Missing setup output"))?;
    let errors = report.clone();
    let reader = thread::spawn(move || {
        for value in io::BufReader::new(stderr).lines().map_while(Result::ok) {
            line(&errors, &value);
        }
    });
    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| io::Error::other("Missing setup output"))?;
    for value in io::BufReader::new(stdout).lines().map_while(Result::ok) {
        line(report, &value);
    }
    let status = child.wait()?;
    let _ = reader.join();
    if !status.success() {
        return Err(io::Error::other(format!(
            "Installer exited with {status}. See console output; refresh after resolving the error."
        )));
    }
    if let Some(path) = config_path() {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let r = report
            .lock()
            .map_err(|_| io::Error::other("Setup state unavailable"))?;
        if r.packages.len() != 6 {
            return Err(io::Error::other(
                "Setup did not return a complete detection report.",
            ));
        }
        use std::io::Write;
        let parent = path
            .parent()
            .ok_or_else(|| io::Error::other("Invalid build settings directory"))?;
        let mut file = tempfile::NamedTempFile::new_in(parent)?;
        file.write_all(&serde_json::to_vec_pretty(&*r)?)?;
        file.persist(path).map_err(|e| e.error)?;
    }
    Ok(())
}
fn toolchain() -> &'static str {
    include_str!("../../../rust-toolchain.toml")
        .lines()
        .find_map(|l| {
            l.strip_prefix("channel = \"")
                .and_then(|v| v.strip_suffix('"'))
        })
        .unwrap_or("stable")
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn protocol_preserves_worker_state_and_bounds_unicode_logs() {
        let r = Arc::new(Mutex::new(Report {
            busy: true,
            ..Default::default()
        }));
        line(
            &r,
            "SCENEMAX_RESULT {\"packages\":[{\"name\":\"Rust\",\"installed\":true,\"detail\":\"C:/custom\"}],\"cargo\":\"C:/custom/cargo.exe\"}",
        );
        line(&r, "SCENEMAX_PROGRESS 120");
        line(&r, &"日".repeat(10_000));
        let r = r.lock().unwrap();
        assert!(r.busy && r.packages[0].installed);
        assert_eq!(r.progress, 100.);
        assert!(r.log.len() <= 24_000);
    }
    #[test]
    fn detected_environment_applies_to_child_only() {
        let before = std::env::var_os("PATH");
        let mut command = Command::new("cargo");
        apply_environment(
            &mut command,
            BTreeMap::from([
                ("LIB".into(), "custom-sdk".into()),
                ("RUSTUP_TOOLCHAIN".into(), "custom-toolchain".into()),
            ]),
        );
        assert!(
            command
                .get_envs()
                .any(|(k, v)| k == "LIB" && v == Some(std::ffi::OsStr::new("custom-sdk")))
        );
        assert_eq!(std::env::var_os("PATH"), before);
    }
    #[test]
    #[cfg(windows)]
    fn scan_reports_missing_toolchain_without_installing_it() {
        let directory = tempfile::tempdir().unwrap();
        let script = directory.path().join("scan.ps1");
        std::fs::write(&script, include_str!("build_configuration.ps1")).unwrap();
        let output = Command::new("powershell.exe")
            .args([
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
            ])
            .arg(script)
            .args(["-Operation", "Scan", "-Toolchain", "0.0.0"])
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        let output = String::from_utf8_lossy(&output.stdout);
        let json = output
            .lines()
            .find_map(|l| l.strip_prefix("SCENEMAX_RESULT "))
            .unwrap();
        let report: Report = serde_json::from_str(json).unwrap();
        assert_eq!(report.packages.len(), 6);
        assert!(!report.packages[0].installed);
        assert!(!report.environment.contains_key("RUSTUP_TOOLCHAIN"));
    }
}
