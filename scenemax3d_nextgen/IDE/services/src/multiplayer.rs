//! Bounded background settings and dedicated Zig server installation.
use scenemax_ide_core::multiplayer::Settings;
use sha2::{Digest, Sha256};
use std::{
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
        mpsc,
    },
    thread,
};
mod browser_test;
mod build;
pub use browser_test::{LocalTest, TestTarget};
mod gateway;
pub(crate) mod launch;
pub mod local_server;
mod progress;
mod remote;
#[cfg(test)]
mod installed_tests;
pub use progress::Progress;
pub use remote::RemoteAction;

/// A configuration snapshot used for optimistic conflict detection.
#[derive(Clone, Debug)]
pub struct Snapshot {
    /// Parsed settings, including unknown JSON.
    pub settings: Settings,
    /// Original bytes; None denotes a new file.
    pub original: Option<Vec<u8>>,
}
/// Completed background operation.
pub struct Report {
    /// Owned local test processes and their temporary bundle.
    pub local_test: Option<browser_test::LocalTest>,
    /// Built browser archive to serve locally after Test Game.
    pub web_artifact: Option<PathBuf>,
    /// Current saved/loaded settings snapshot.
    pub snapshot: Snapshot,
    /// User-visible result.
    pub message: String,
    /// Newly created server, if this operation built one.
    pub server: Option<PathBuf>,
    /// Optional browser adapter built alongside the UDP server.
    pub gateway: Option<PathBuf>,
}
/// Work performed outside the IDE frame thread.
pub enum Operation {
    /// Save multiplayer settings and export the project for local browser testing.
    TestGame(Snapshot, PathBuf, TestTarget),
    /// Load native settings, importing Java defaults if needed.
    Load,
    /// Save against a captured source snapshot.
    Save(Snapshot),
    /// Compile source or use the prebuilt fallback, then install a unique server.
    Build(Snapshot),
    /// Build using an explicit session-only password, including an empty password.
    BuildWithPassword(Snapshot, String),
    /// Test SSH, inspect services, or deploy an already-built bundle.
    Remote(Snapshot, Option<PathBuf>, RemoteAction),
}
/// One owned bounded worker slot.
#[derive(Default)]
pub struct Worker {
    progress: Arc<Mutex<Progress>>,
    result: Option<Mutex<mpsc::Receiver<Result<Report, String>>>>,
    handle: Option<thread::JoinHandle<()>>,
    cancel: Arc<AtomicBool>,
}
impl Worker {
    /// Start one operation with captured project identity.
    pub fn start(&mut self, root: PathBuf, operation: Operation) -> Result<(), String> {
        if self.result.is_some() {
            return Err("Multiplayer operation already running".into());
        }
        let (tx, rx) = mpsc::sync_channel(1);
        self.cancel = Arc::new(AtomicBool::new(false));
        let cancel = self.cancel.clone();
        self.progress = Arc::new(Mutex::new(Progress::default()));
        let progress = self.progress.clone();
        self.handle = Some(
            thread::Builder::new()
                .name("multiplayer-settings".into())
                .spawn(move || {
                    progress::attach(progress);
                    progress::stage(0, "Starting");
                    let result = run(&root, operation, &cancel);
                    let error = match &result {
                        Err(error) => Some(error.as_str()),
                        Ok(report)
                            if report.message.starts_with("Remote operation failed:")
                                || report.message.starts_with("Test Game failed:") =>
                        {
                            Some(report.message.as_str())
                        }
                        _ => None,
                    };
                    if let Some(error) = error {
                        progress::stage(
                            0,
                            if cancel.load(Ordering::Relaxed) {
                                "Cancelled"
                            } else {
                                "Failed"
                            },
                        );
                        progress::output(error);
                    } else {
                        progress::stage(100, "Completed");
                        if let Ok(report) = &result {
                            progress::output(&report.message);
                        }
                    }
                    let _ = tx.send(result);
                })
                .map_err(|e| e.to_string())?,
        );
        self.result = Some(Mutex::new(rx));
        Ok(())
    }
    /// Request cancellation of owned build work; never terminate a game server.
    pub fn progress(&self) -> Progress {
        self.progress
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .clone()
    }
    /// Request cancellation of owned build work; never terminate a game server.
    pub fn cancel(&self) {
        self.cancel.store(true, Ordering::Relaxed);
    }
    /// Poll without blocking an IDE frame.
    pub fn poll(&mut self) -> Option<Result<Report, String>> {
        let result = match self
            .result
            .as_mut()?
            .get_mut()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .try_recv()
        {
            Ok(r) => r,
            Err(mpsc::TryRecvError::Empty) => return None,
            Err(_) => Err("Multiplayer worker disconnected".into()),
        };
        self.result = None;
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
        Some(result)
    }
}
impl Drop for Worker {
    fn drop(&mut self) {
        self.cancel();
        if let Some(h) = self.handle.take() {
            let _ = h.join();
        }
    }
}

fn run(root: &Path, operation: Operation, cancel: &AtomicBool) -> Result<Report, String> {
    let root = root.canonicalize().map_err(|e| e.to_string())?;
    build::check(cancel)?;
    let (operation, password) = match operation {
        Operation::BuildWithPassword(snapshot, password) => {
            (Operation::Build(snapshot), Some(password))
        }
        operation => (
            operation,
            std::env::var("SCENEMAX_MULTIPLAYER_PASSWORD").ok(),
        ),
    };
    match operation {
        Operation::BuildWithPassword(..) => unreachable!("normalized above"),
        Operation::Load => Ok(Report {
            local_test: None,
            web_artifact: None,
            snapshot: load(&root)?,
            message: "Multiplayer settings loaded. Passwords are kept only for this IDE session."
                .into(),
            server: None,
            gateway: None,
        }),
        Operation::Remote(mut snapshot, bundle, action) => {
            progress::stage(5, "Saving deployment settings");
            snapshot.settings.deployment.validate()?;
            save(&root, &mut snapshot)?;
            let message = remote::execute(&snapshot.settings, bundle.as_deref(), action, cancel)
                .unwrap_or_else(|error| format!("Remote operation failed: {error}"));
            Ok(Report {
                local_test: None,
                web_artifact: None,
                snapshot,
                message,
                server: None,
                gateway: None,
            })
        }
        Operation::Save(mut snapshot) => {
            save(&root, &mut snapshot)?;
            Ok(Report {
                local_test: None,
                web_artifact: None,
                snapshot,
                message: "Multiplayer settings saved.".into(),
                server: None,
                gateway: None,
            })
        }
        Operation::Build(mut snapshot) => {
            progress::stage(5, "Validating build settings");
            snapshot.settings.validate()?;
            let s = &snapshot.settings;
            let installation = crate::workspace_state::installation_root()
                .unwrap_or_else(|| Path::new(env!("CARGO_MANIFEST_DIR")).join("../../.."));
            progress::stage(15, "Building Zig UDP server");
            let (mut bytes, origin) = build::prepare(&installation, &s.platform, cancel)?;
            build::check(cancel)?;
            patch(&mut bytes, s, &root, password.as_deref())?;
            progress::stage(45, "Packaging UDP server");
            let directory = root.join("multiplayer_server");
            ensure_directory(&root, &directory)?;
            // A bundle becomes visible only after every requested executable is ready.
            let bundle = tempfile::Builder::new()
                .prefix("scenemax-mp-")
                .tempdir_in(&directory)
                .map_err(|e| e.to_string())?;
            let path = bundle.path().join(if s.platform == "windows-x64" {
                "scenemax-mp-server.exe"
            } else {
                "scenemax-mp-server"
            });
            fs::write(&path, &bytes).map_err(|e| e.to_string())?;
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                fs::set_permissions(&path, fs::Permissions::from_mode(0o700))
                    .map_err(|e| e.to_string())?;
            }
            let gateway = if s.browser_gateway.enabled {
                progress::stage(55, "Building browser gateway");
                let bytes = gateway::prepare(&s.platform, cancel)?;
                Some(gateway::install(bundle.path(), s, &bytes)?)
            } else {
                None
            };
            build::check(cancel)?;
            progress::stage(90, "Writing bundle manifest and settings");
            remote::record_bundle(bundle.path(), &snapshot.settings)?;
            save(&root, &mut snapshot)?;
            let _ = bundle.keep();
            Ok(Report {
                local_test: None,
                web_artifact: None,
                snapshot,
                message: format!(
                    "{origin}. {} created: {}",
                    if gateway.is_some() {
                        "UDP server and browser gateway"
                    } else {
                        "Server"
                    },
                    path.parent().unwrap().display()
                ),
                server: Some(path),
                gateway,
            })
        }
        Operation::TestGame(mut snapshot, entry, target) => {
            let settings = browser_test::settings(&snapshot.settings, target)?;
            save(&root, &mut snapshot)?;
            // Return the saved snapshot even on build failure so retry does not
            // mistake our own save for an external edit.
            let mut local_test = None;
            let result = (|| -> Result<PathBuf, String> {
                if target == TestTarget::Local {
                    local_test = Some(browser_test::start_local(&root, &settings, cancel)?);
                }
                let mut request = crate::deployment::browser_test_request(root.clone(), entry)
                    .map_err(|e| e.to_string())?;
                request.multiplayer_override = Some(settings.clone());
                let mut builder = crate::deployment::Builder::default();
                builder
                    .start(crate::deployment::Operation::BuildTemporary(request))
                    .map_err(|e| e.to_string())?;
                let mut revision = None;
                loop {
                    if cancel.load(Ordering::Relaxed) {
                        builder.cancel();
                    }
                    if let Some(report) = builder.report() {
                        if revision != Some(report.revision) {
                            revision = Some(report.revision);
                            progress::stage(report.progress as u8, &report.status);
                            progress::replace_log(
                                &report.logs.iter().cloned().collect::<Vec<_>>().join("\n"),
                            );
                        }
                        if report.finished {
                            if !report.success {
                                return Err(report.status);
                            }
                            return report
                                .web_artifact
                                .ok_or("Web build did not produce a browser archive".into());
                        }
                    }
                    std::thread::sleep(std::time::Duration::from_millis(100));
                }
            })();
            let (web_artifact, message) = match result {
                Ok(path) => (
                    Some(path),
                    "Browser game built; starting local preview…".into(),
                ),
                Err(error) => (None, format!("Test Game failed: {error}")),
            };
            Ok(Report {
                local_test: if web_artifact.is_some() {
                    local_test
                } else {
                    None
                },
                snapshot,
                message,
                server: None,
                gateway: None,
                web_artifact,
            })
        }
    }
}
fn read(path: &Path, max: u64) -> Result<Vec<u8>, String> {
    let mut bytes = Vec::new();
    fs::File::open(path)
        .map_err(|e| format!("{}: {e}", path.display()))?
        .take(max + 1)
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    if bytes.len() as u64 > max {
        return Err("Multiplayer file exceeds size limit".into());
    }
    Ok(bytes)
}
fn ensure_directory(root: &Path, path: &Path) -> Result<(), String> {
    if path.exists() {
        if fs::symlink_metadata(path)
            .map_err(|e| e.to_string())?
            .file_type()
            .is_symlink()
            || path.canonicalize().map_err(|e| e.to_string())?
                != root
                    .canonicalize()
                    .map_err(|e| e.to_string())?
                    .join(path.file_name().ok_or("Invalid settings folder")?)
        {
            return Err("Linked multiplayer directories are not supported".into());
        }
    } else {
        fs::create_dir(path).map_err(|e| e.to_string())?;
    }
    Ok(())
}
fn settings_path(root: &Path) -> Result<PathBuf, String> {
    let directory = root.join(".scenemax-studio");
    if directory.exists() {
        ensure_directory(root, &directory)?;
    }
    let path = directory.join("multiplayer.json");
    if path.exists()
        && fs::symlink_metadata(&path)
            .map_err(|e| e.to_string())?
            .file_type()
            .is_symlink()
    {
        return Err("Linked multiplayer settings are not supported".into());
    }
    Ok(path)
}
/// Whether the project explicitly opts into multiplayer.
pub fn enabled(root: &Path) -> Result<bool, String> {
    Ok(load(root)?.settings.enabled)
}

fn load(root: &Path) -> Result<Snapshot, String> {
    let path = settings_path(root)?;
    if path.exists() {
        let bytes = read(&path, 1024 * 1024)?;
        let settings = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
        return Ok(Snapshot {
            settings,
            original: Some(bytes),
        });
    }
    let installation = crate::workspace_state::installation_root()
        .unwrap_or_else(|| Path::new(env!("CARGO_MANIFEST_DIR")).join("../../.."));
    let mut settings = launch::import(root, &installation.join("projects/projects.json"))?
        .unwrap_or_else(|| Settings {
            session_name: root
                .file_name()
                .and_then(|s| s.to_str())
                .unwrap_or("local")
                .into(),
            ..Default::default()
        });
    if settings.project_guid.is_empty() {
        let hash = Sha256::digest(root.to_string_lossy().as_bytes());
        settings.project_guid = hash[..16].iter().map(|b| format!("{b:02x}")).collect();
    }
    Ok(Snapshot {
        settings,
        original: None,
    })
}
pub(crate) fn initialize_project(root: &Path, enabled: bool) -> Result<(), String> {
    let mut snapshot = load(root)?;
    snapshot.settings.enabled = enabled;
    // Room names have a shorter wire limit than filesystem folder names.
    if snapshot.settings.session_name.len() >= 64 {
        snapshot.settings.session_name = "local".into();
    }
    save(root, &mut snapshot)
}
fn save(root: &Path, snapshot: &mut Snapshot) -> Result<(), String> {
    if snapshot.settings.enabled {
        snapshot.settings.validate()?;
    }
    let path = settings_path(root)?;
    let current = if path.exists() {
        Some(read(&path, 1024 * 1024)?)
    } else {
        None
    };
    if current != snapshot.original {
        return Err(
            "Multiplayer settings changed externally; close and reopen Project Settings.".into(),
        );
    }
    let mut bytes = serde_json::to_vec_pretty(&snapshot.settings).map_err(|e| e.to_string())?;
    let parent = path.parent().ok_or("Invalid settings path")?;
    ensure_directory(root, parent)?;
    if let Some(original) = current {
        let mut document =
            scenemax_ide_core::Document::from_bytes(path, original).map_err(|e| e.to_string())?;
        document.replace_text(String::from_utf8(bytes.clone()).map_err(|e| e.to_string())?);
        crate::Filesystem::save_document(&mut document).map_err(|e| e.to_string())?;
        // The document preserves disk line endings (and untouched formatting).
        // Conflict detection must use the bytes actually saved, not JSON's LF encoding.
        bytes = document.saved_bytes().to_vec();
    } else {
        let mut tmp = tempfile::NamedTempFile::new_in(parent).map_err(|e| e.to_string())?;
        tmp.write_all(&bytes).map_err(|e| e.to_string())?;
        tmp.persist_noclobber(&path).map_err(|e| e.to_string())?;
    }
    snapshot.original = Some(bytes);
    Ok(())
}
fn patch(
    bytes: &mut [u8],
    s: &Settings,
    root: &Path,
    password: Option<&str>,
) -> Result<(), String> {
    let begin = b"SCENEMAX_MP_CONFIG_BEGIN";
    let end = b"SCENEMAX_MP_CONFIG_END";
    let locations = bytes
        .windows(begin.len())
        .enumerate()
        .filter(|(i, w)| {
            *w == begin
                && bytes.get(i + begin.len() + 4096..i + begin.len() + 4096 + end.len())
                    == Some(end.as_slice())
        })
        .map(|(i, _)| i + begin.len())
        .collect::<Vec<_>>();
    if locations.len() != 1 {
        return Err("Server template must contain exactly one valid configuration block".into());
    }
    let p = &mut bytes[locations[0]..locations[0] + 4096];
    p.fill(0);
    p[..8].copy_from_slice(b"SMXMPCFG");
    p[8..12].copy_from_slice(&1u32.to_le_bytes());
    p[12..16].copy_from_slice(&u32::from(s.port).to_le_bytes());
    let fixed = |dest: &mut [u8], text: &str| -> Result<(), String> {
        if text.len() >= dest.len() {
            return Err("Server configuration field is too long".into());
        }
        dest[..text.len()].copy_from_slice(text.as_bytes());
        Ok(())
    };
    fixed(&mut p[16..144], &s.session_name)?;
    fixed(&mut p[144..400], &root.to_string_lossy())?;
    fixed(&mut p[432..496], &s.project_guid)?;
    if let Some(password) = password.filter(|s| !s.is_empty()) {
        p[400..432].copy_from_slice(&Sha256::digest(password.as_bytes()));
    }
    Ok(())
}

pub(crate) fn embed_settings(root: &Path, entry: &Path) -> Result<(), String> {
    if !settings_path(root)?.is_file() {
        return Ok(());
    }
    let s = load(root)?.settings;
    embed_values(&s, entry)
}

pub(crate) fn embed_values(s: &Settings, entry: &Path) -> Result<(), String> {
    if s.enabled {
        s.validate()?;
    }
    let s = s.clone();
    let fields = [
        ("multiplayer_server", s.server),
        ("multiplayer_port", s.port.to_string()),
        ("project_guid", s.project_guid),
        ("multiplayer_session_id", s.session_id.to_string()),
        ("multiplayer_session_name", s.session_name),
        ("multiplayer_create_session", "false".into()),
        ("multiplayer_scene", "main".into()),
    ];
    let source = String::from_utf8(read(entry, 16 * 1024 * 1024)?).map_err(|e| e.to_string())?;
    let mut result = fields
        .iter()
        .filter(|_| s.enabled)
        .map(|(key, value)| format!("//$[{key}]={value};\n"))
        .collect::<String>();
    for line in source.lines() {
        if fields
            .iter()
            .any(|(key, _)| line.trim().starts_with(&format!("//$[{key}]=")))
        {
            continue;
        }
        result.push_str(line);
        result.push('\n');
    }
    fs::write(entry, result).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    #[ignore = "requires configured project and browser build toolchain"]
    fn build_configured_browser_test() {
        let root = PathBuf::from(std::env::var("SCENEMAX_TEST_BROWSER_PROJECT").unwrap());
        let snapshot = load(&root).unwrap();
        let target = if std::env::var("SCENEMAX_TEST_BROWSER_LOCAL").is_ok() {
            TestTarget::Local
        } else {
            TestTarget::Remote
        };
        let gateway = browser_test::settings(&snapshot.settings, target)
            .unwrap()
            .browser_gateway
            .websocket_url;
        let before = read(&settings_path(&root).unwrap(), 65536).unwrap();
        let report = run(
            &root,
            Operation::TestGame(snapshot, root.join("scripts/main"), target),
            &AtomicBool::new(false),
        )
        .unwrap();
        let archive = report.web_artifact.expect(&report.message);
        let mut zip = zip::ZipArchive::new(fs::File::open(&archive).unwrap()).unwrap();
        let mut network = String::new();
        zip.by_name("network.json")
            .unwrap()
            .read_to_string(&mut network)
            .unwrap();
        assert!(network.contains(&gateway));
        let after = read(&settings_path(&root).unwrap(), 65536).unwrap();
        assert_eq!(
            serde_json::from_slice::<serde_json::Value>(&before).unwrap(),
            serde_json::from_slice::<serde_json::Value>(&after).unwrap()
        );
        println!("Browser test archive: {}\n{network}", archive.display());
    }
    #[test]
    fn deployment_settings_survive_repeated_saves_with_windows_newlines() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        let mut snapshot = load(&root).unwrap();
        save(&root, &mut snapshot).unwrap();
        let path = settings_path(&root).unwrap();
        let text = fs::read_to_string(&path).unwrap().replace('\n', "\r\n");
        fs::write(&path, text).unwrap();
        let mut snapshot = load(&root).unwrap();
        snapshot.settings.deployment.method = "putty".into();
        snapshot.settings.deployment.host = "saved-session".into();
        snapshot.settings.deployment.user = "deployer".into();
        snapshot.settings.deployment.port = 2222;
        snapshot.settings.deployment.identity_file = "C:/keys/deploy.ppk".into();
        snapshot.settings.deployment.directory = "/opt/test-game".into();
        snapshot.settings.deployment.service = "test-game".into();
        snapshot.settings.deployment.sudo = false;
        save(&root, &mut snapshot).unwrap();
        assert_eq!(
            snapshot.original.as_ref().unwrap(),
            &fs::read(&path).unwrap()
        );
        // A second save is also the first step of Test Connection.
        save(&root, &mut snapshot).unwrap();
        assert_eq!(
            load(&root).unwrap().settings.deployment,
            snapshot.settings.deployment
        );
        snapshot.settings.deployment.host = "updated-session".into();
        save(&root, &mut snapshot).unwrap();
        assert_eq!(
            load(&root).unwrap().settings.deployment.host,
            "updated-session"
        );
        // Exercise Test Connection's save/report path without contacting a server.
        snapshot.settings.deployment.identity_file =
            root.join("missing-key.ppk").display().to_string();
        for _ in 0..2 {
            let report = run(
                &root,
                Operation::Remote(snapshot, None, RemoteAction::Test),
                &AtomicBool::new(false),
            )
            .unwrap();
            assert!(report.message.contains("SSH identity file was not found"));
            snapshot = report.snapshot;
            assert_eq!(
                snapshot.original.as_ref().unwrap(),
                &fs::read(&path).unwrap()
            );
        }
    }
    #[test]
    fn save_preserves_unknown_fields_and_rejects_external_changes() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        let mut snapshot = load(&root).unwrap();
        snapshot
            .settings
            .extra
            .insert("future".into(), serde_json::json!({"keep":true}));
        save(&root, &mut snapshot).unwrap();
        let mut stale = snapshot.clone();
        snapshot.settings.port = 9100;
        save(&root, &mut snapshot).unwrap();
        stale.settings.port = 9200;
        assert!(
            save(&root, &mut stale)
                .unwrap_err()
                .contains("changed externally")
        );
        let loaded = load(&root).unwrap();
        assert_eq!(loaded.settings.port, 9100);
        assert_eq!(
            loaded.settings.extra["future"],
            serde_json::json!({"keep":true})
        );
    }
    #[test]
    fn server_patch_matches_zig_offsets_and_requires_one_block() {
        let begin = b"SCENEMAX_MP_CONFIG_BEGIN";
        let end = b"SCENEMAX_MP_CONFIG_END";
        let mut bytes = begin.to_vec();
        bytes.resize(begin.len() + 4096, 0);
        bytes.extend(end);
        let settings = Settings {
            project_guid: "fixture-guid".into(),
            port: 9123,
            ..Default::default()
        };
        patch(
            &mut bytes,
            &settings,
            Path::new("fixture"),
            Some("password"),
        )
        .unwrap();
        let p = &bytes[begin.len()..];
        assert_eq!(&p[..8], b"SMXMPCFG");
        assert_eq!(&p[12..16], &9123u32.to_le_bytes());
        assert_eq!(&p[400..432], Sha256::digest(b"password").as_slice());
        assert_eq!(&p[432..444], b"fixture-guid");
        assert!(
            !bytes
                .windows(b"password".len())
                .any(|window| window == b"password")
        );
        patch(&mut bytes, &settings, Path::new("fixture"), Some("")).unwrap();
        assert_eq!(&bytes[begin.len() + 400..begin.len() + 432], &[0; 32]);
        let mut duplicated = bytes.clone();
        duplicated.extend(&bytes);
        assert!(patch(&mut duplicated, &settings, Path::new("fixture"), None).is_err());
    }
    #[test]
    fn packaged_metadata_is_current_and_never_contains_password() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        let mut snapshot = load(&root).unwrap();
        snapshot.settings.enabled = true;
        snapshot.settings.server = "192.0.2.1".into();
        save(&root, &mut snapshot).unwrap();
        let staged = root.join("staged-main");
        fs::write(&staged, "//$[multiplayer_server]=stale;\nvar value = 1\n").unwrap();
        embed_settings(&root, &staged).unwrap();
        let source = fs::read_to_string(staged).unwrap();
        assert!(source.contains("//$[multiplayer_server]=192.0.2.1;"));
        assert!(!source.contains("stale"));
        assert!(!source.contains("password"));
        assert!(source.contains("var value = 1"));
    }
}

/// Website origin accepted by the project's browser gateway.
pub(crate) fn browser_allowed_origin(root: &Path, required: bool) -> Result<String, String> {
    if !required {
        return Ok(String::new());
    }
    Ok(load(root)?.settings.browser_gateway.allowed_origin)
}

/// Public browser endpoint from the project's saved multiplayer settings.
pub(crate) fn browser_gateway_url(root: &Path, required: bool) -> Result<String, String> {
    if !required {
        return Ok(String::new());
    }
    let settings = load(root)?.settings;
    if !settings.browser_gateway.enabled {
        return Err("Enable Browser gateway and save its WebSocket URL in Project Settings > Multiplayer before exporting a multiplayer browser game.".into());
    }
    settings.browser_gateway.validate()?;
    Ok(settings.browser_gateway.websocket_url)
}

#[cfg(test)]
mod browser_settings_tests {
    use super::*;
    #[test]
    fn export_uses_saved_url_and_rejects_missing_gateway_for_multiplayer() {
        let project = tempfile::tempdir().unwrap();
        assert_eq!(browser_gateway_url(project.path(), false).unwrap(), "");
        let mut snapshot = load(project.path()).unwrap();
        assert!(browser_gateway_url(project.path(), true).is_err());
        snapshot.settings.browser_gateway.enabled = true;
        snapshot.settings.browser_gateway.websocket_url = "wss://network.example.com/play".into();
        snapshot.settings.browser_gateway.allowed_origin = "https://games.example.com".into();
        save(project.path(), &mut snapshot).unwrap();
        assert_eq!(
            browser_gateway_url(project.path(), true).unwrap(),
            "wss://network.example.com/play"
        );
        assert_eq!(
            load(project.path()).unwrap().settings.browser_gateway,
            snapshot.settings.browser_gateway
        );
    }

    #[test]
    #[ignore = "requires Zig and the native Rust gateway build toolchain"]
    fn build_browser_bundle_then_run_and_stop_both() {
        let project = tempfile::tempdir().unwrap();
        let mut snapshot = load(project.path()).unwrap();
        let udp = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        snapshot.settings.port = udp.local_addr().unwrap().port();
        drop(udp);
        let tcp = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let port = tcp.local_addr().unwrap().port();
        drop(tcp);
        snapshot.settings.platform = if cfg!(windows) {
            "windows-x64"
        } else if cfg!(target_os = "macos") {
            "macos-x64"
        } else {
            "linux-x64"
        }
        .into();
        snapshot.settings.browser_gateway.enabled = true;
        snapshot.settings.browser_gateway.listen = format!("127.0.0.1:{port}");
        snapshot.settings.browser_gateway.websocket_url = format!("ws://localhost:{port}");
        let report = run(
            project.path(),
            Operation::BuildWithPassword(snapshot, String::new()),
            &AtomicBool::new(false),
        )
        .unwrap();
        let server = report.server.unwrap();
        let gateway = report.gateway.unwrap();
        assert_eq!(server.parent(), gateway.parent());
        assert!(server.with_file_name("gateway.json").is_file());
        assert_eq!(
            browser_gateway_url(project.path(), true).unwrap(),
            format!("ws://localhost:{port}")
        );
        let mut local = local_server::LocalServer::default();
        local
            .start_with_gateway(
                &server,
                report.snapshot.settings.port,
                Some((&gateway, &report.snapshot.settings.browser_gateway)),
            )
            .unwrap();
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(10);
        loop {
            let status = local.poll().unwrap_or_default();
            if status.contains("Gateway listening") {
                break;
            }
            assert!(
                local.active() && std::time::Instant::now() < deadline,
                "{status}"
            );
            std::thread::sleep(std::time::Duration::from_millis(30));
        }
        local.stop();
        assert!(std::net::TcpListener::bind(("127.0.0.1", port)).is_ok());
        assert!(std::net::UdpSocket::bind(("127.0.0.1", report.snapshot.settings.port)).is_ok());
        // The distributed executable also works without command-line arguments or a matching cwd.
        let mut command = std::process::Command::new(&gateway);
        command
            .current_dir(project.path())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null());
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        let mut child = command.spawn().unwrap();
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
        let connected = loop {
            if std::net::TcpStream::connect(("127.0.0.1", port)).is_ok() {
                break true;
            }
            if std::time::Instant::now() >= deadline || child.try_wait().unwrap().is_some() {
                break false;
            }
            std::thread::sleep(std::time::Duration::from_millis(30));
        };
        let _ = child.kill();
        let _ = child.wait();
        assert!(
            connected,
            "bundled gateway must read its adjacent configuration"
        );
    }
}

#[cfg(test)]
mod project_mode_tests {
    use super::*;
    #[test]
    fn offline_setting_roundtrips_and_removes_packaged_connection_metadata() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        let mut snapshot = load(&root).unwrap();
        assert!(!snapshot.settings.enabled);
        snapshot.settings.enabled = true;
        snapshot.settings.server = "saved.example".into();
        save(&root, &mut snapshot).unwrap();
        assert!(enabled(&root).unwrap());
        snapshot.settings.enabled = false;
        save(&root, &mut snapshot).unwrap();
        let loaded = load(&root).unwrap();
        assert!(!loaded.settings.enabled);
        assert_eq!(loaded.settings.server, "saved.example");
        let entry = root.join("main");
        fs::write(
            &entry,
            "//$[multiplayer_server]=old;\n//$[project_guid]=old;\nvar value = 1\n",
        )
        .unwrap();
        embed_settings(&root, &entry).unwrap();
        assert_eq!(fs::read_to_string(entry).unwrap(), "var value = 1\n");
    }
}
