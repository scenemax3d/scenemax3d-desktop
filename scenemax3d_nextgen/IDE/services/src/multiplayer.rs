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
mod build;
pub(crate) mod launch;
pub mod local_server;

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
    /// Current saved/loaded settings snapshot.
    pub snapshot: Snapshot,
    /// User-visible result.
    pub message: String,
    /// Newly created server, if this operation built one.
    pub server: Option<PathBuf>,
}
/// Work performed outside the IDE frame thread.
pub enum Operation {
    /// Load native settings, importing Java defaults if needed.
    Load,
    /// Save against a captured source snapshot.
    Save(Snapshot),
    /// Compile source or use the prebuilt fallback, then install a unique server.
    Build(Snapshot),
    /// Build using an explicit session-only password, including an empty password.
    BuildWithPassword(Snapshot, String),
}
/// One owned bounded worker slot.
#[derive(Default)]
pub struct Worker {
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
        self.handle = Some(
            thread::Builder::new()
                .name("multiplayer-settings".into())
                .spawn(move || {
                    let _ = tx.send(run(&root, operation, &cancel));
                })
                .map_err(|e| e.to_string())?,
        );
        self.result = Some(Mutex::new(rx));
        Ok(())
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
            snapshot: load(&root)?,
            message: "Multiplayer settings loaded. Passwords are kept only for this IDE session."
                .into(),
            server: None,
        }),
        Operation::Save(mut snapshot) => {
            save(&root, &mut snapshot)?;
            Ok(Report {
                snapshot,
                message: "Multiplayer settings saved.".into(),
                server: None,
            })
        }
        Operation::Build(mut snapshot) => {
            snapshot.settings.validate()?;
            let s = &snapshot.settings;
            let installation = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../..");
            let (mut bytes, origin) = build::prepare(&installation, &s.platform, cancel)?;
            build::check(cancel)?;
            patch(&mut bytes, s, &root, password.as_deref())?;
            let directory = root.join("multiplayer_server");
            ensure_directory(&root, &directory)?;
            // Unique output prevents overwriting another build or a running server.
            let mut pending = tempfile::Builder::new()
                .prefix("scenemax-mp-")
                .suffix(if s.platform == "windows-x64" {
                    ".exe"
                } else {
                    ""
                })
                .tempfile_in(&directory)
                .map_err(|e| e.to_string())?;
            pending.write_all(&bytes).map_err(|e| e.to_string())?;
            pending.as_file().sync_all().map_err(|e| e.to_string())?;
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                pending
                    .as_file()
                    .set_permissions(fs::Permissions::from_mode(0o700))
                    .map_err(|e| e.to_string())?;
            }
            build::check(cancel)?;
            save(&root, &mut snapshot)?;
            let (_, path) = pending.keep().map_err(|e| e.to_string())?;
            Ok(Report {
                snapshot,
                message: format!("{origin}. Server created: {}", path.display()),
                server: Some(path),
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
                != root.join(path.file_name().ok_or("Invalid settings folder")?)
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
    let installation = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../..");
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
fn save(root: &Path, snapshot: &mut Snapshot) -> Result<(), String> {
    snapshot.settings.validate()?;
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
    let bytes = serde_json::to_vec_pretty(&snapshot.settings).map_err(|e| e.to_string())?;
    let parent = path.parent().ok_or("Invalid settings path")?;
    ensure_directory(root, parent)?;
    if let Some(original) = current {
        let mut document =
            scenemax_ide_core::Document::from_bytes(path, original).map_err(|e| e.to_string())?;
        document.replace_text(String::from_utf8(bytes.clone()).map_err(|e| e.to_string())?);
        crate::Filesystem::save_document(&mut document).map_err(|e| e.to_string())?;
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
    s.validate()?;
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
