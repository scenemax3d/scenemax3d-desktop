//! Persistent deployment reuse; credentials are checked against the patched binary,
//! never written to cache metadata or logs.
use super::{
    SavedBundle, Settings, build, build_bundle, patch, progress, read, remote, saved_bundles,
};
use sha2::{Digest, Sha256};
use std::{fs, path::Path, sync::atomic::AtomicBool};

fn hash_path(hash: &mut Sha256, path: &Path) -> Result<(), String> {
    hash.update(path.to_string_lossy().as_bytes());
    if !path.exists() {
        hash.update(b"missing");
    } else if path.is_dir() {
        let mut entries = fs::read_dir(path)
            .map_err(|e| e.to_string())?
            .map(|e| e.map(|e| e.path()))
            .collect::<Result<Vec<_>, _>>()
            .map_err(|e| e.to_string())?;
        entries.sort();
        for entry in entries {
            hash_path(hash, &entry)?;
        }
    } else {
        let bytes = read(path, 64 * 1024 * 1024)?;
        hash.update((bytes.len() as u64).to_le_bytes());
        hash.update(bytes);
    }
    Ok(())
}

fn fingerprint(s: &Settings) -> Result<String, String> {
    let installation = crate::workspace_state::installation_root()
        .unwrap_or_else(|| Path::new(env!("CARGO_MANIFEST_DIR")).join("../../.."));
    let mut hash = Sha256::new();
    hash.update(b"scenemax-deployment-build-v1");
    hash_path(
        &mut hash,
        &installation.join("tools/multiplayer-server/zig"),
    )?;
    hash_path(
        &mut hash,
        &installation
            .join("tools/multiplayer-server/bin")
            .join(&s.platform),
    )?;
    if s.browser_gateway.enabled {
        let workspace = crate::deployment::workspace();
        for input in [
            "Engine/multiplayer/src",
            "Engine/multiplayer/Cargo.toml",
            "Cargo.toml",
            "Cargo.lock",
            "rust-toolchain.toml",
            ".cargo/config.toml",
        ] {
            hash_path(&mut hash, &workspace.join(input))?;
        }
        hash_path(
            &mut hash,
            &workspace
                .join("../tools/browser-gateway/bin")
                .join(&s.platform),
        )?;
    }
    // A Studio update can change packaging/patching behavior even in installations
    // that contain no sources. A new executable invalidates old build receipts.
    let exe = std::env::current_exe().map_err(|e| e.to_string())?;
    let metadata = exe.metadata().map_err(|e| e.to_string())?;
    hash.update(format!(
        "{}:{}:{:?}",
        exe.display(),
        metadata.len(),
        metadata.modified().map_err(|e| e.to_string())?
    ));
    Ok(format!("{:x}", hash.finalize()))
}

pub(super) fn record(dir: &Path, s: &Settings) -> Result<(), String> {
    fs::write(dir.join("build-inputs.sha256"), fingerprint(s)?).map_err(|e| e.to_string())
}

fn reusable(
    bundle: &SavedBundle,
    root: &Path,
    s: &Settings,
    password: Option<&str>,
    inputs: &str,
) -> bool {
    let Some(dir) = bundle.server.parent() else {
        return false;
    };
    if read(&dir.join("build-inputs.sha256"), 1024).ok().as_deref() != Some(inputs.as_bytes())
        || remote::validate_bundle(dir, s).is_err()
    {
        return false;
    }
    let Ok(bytes) = read(&bundle.server, 64 * 1024 * 1024) else {
        return false;
    };
    let mut expected = bytes.clone();
    patch(&mut expected, s, root, password).is_ok() && expected == bytes
}

pub(super) fn prepare(
    root: &Path,
    s: &Settings,
    password: Option<&str>,
    cancel: &AtomicBool,
) -> Result<SavedBundle, String> {
    progress::stage(5, "Checking saved Linux server builds");
    let inputs = fingerprint(s)?;
    for bundle in saved_bundles(root) {
        build::check(cancel)?;
        if reusable(&bundle, root, s, password, &inputs) {
            progress::output("Reusing up-to-date Linux server bundle.\n");
            return Ok(bundle);
        }
    }
    progress::output("Building Linux server bundle automatically: no current saved build.\n");
    let (bundle, server, gateway) = build_bundle(root, s, password, cancel)?;
    if fingerprint(s)? != inputs {
        return Err("Build inputs changed during compilation. Retry deployment.".into());
    }
    build::check(cancel)?;
    let _ = bundle.keep();
    Ok(SavedBundle {
        server,
        gateway,
        settings: s.clone(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    #[ignore = "requires Zig or a Linux server template; builds locally without SSH"]
    fn automatic_build_then_reuse_and_rebuild_for_changed_password() {
        let root = tempfile::tempdir().unwrap();
        let root = root.path().canonicalize().unwrap();
        let mut s = super::super::load(&root).unwrap().settings;
        s.platform = "linux-x64".into();
        let cancel = AtomicBool::new(false);
        let first = prepare(&root, &s, Some("first"), &cancel).unwrap();
        let reused = prepare(&root, &s, Some("first"), &cancel).unwrap();
        assert_eq!(first.server, reused.server);
        let rebuilt = prepare(&root, &s, Some("second"), &cancel).unwrap();
        assert_ne!(first.server, rebuilt.server);
        remote::validate_bundle(rebuilt.server.parent().unwrap(), &s).unwrap();
    }

    #[test]
    fn reuse_requires_current_inputs_settings_password_and_intact_payload() {
        let root = tempfile::tempdir().unwrap();
        let dir = root.path().join("multiplayer_server/test");
        fs::create_dir_all(&dir).unwrap();
        let mut s = Settings::default();
        s.platform = "linux-x64".into();
        let mut bytes = b"\x7fELFSCENEMAX_MP_CONFIG_BEGIN".to_vec();
        bytes.extend([0; 4096]);
        bytes.extend(b"SCENEMAX_MP_CONFIG_END");
        patch(&mut bytes, &s, root.path(), Some("test-secret")).unwrap();
        let server = dir.join("scenemax-mp-server");
        fs::write(&server, bytes).unwrap();
        remote::record_bundle(&dir, &s).unwrap();
        fs::write(dir.join("build-inputs.sha256"), "inputs-v1").unwrap();
        let bundle = saved_bundles(root.path()).pop().unwrap();
        assert!(reusable(
            &bundle,
            root.path(),
            &s,
            Some("test-secret"),
            "inputs-v1"
        ));
        record(&dir, &s).unwrap();
        let reused = prepare(
            root.path(),
            &s,
            Some("test-secret"),
            &AtomicBool::new(false),
        )
        .unwrap();
        assert_eq!(reused.server, server);
        assert!(prepare(root.path(), &s, Some("test-secret"), &AtomicBool::new(true)).is_err());
        fs::write(dir.join("build-inputs.sha256"), "inputs-v1").unwrap();
        assert!(!reusable(
            &bundle,
            root.path(),
            &s,
            Some("different"),
            "inputs-v1"
        ));
        assert!(!reusable(&bundle, root.path(), &s, None, "inputs-v1"));
        assert!(!reusable(
            &bundle,
            root.path(),
            &s,
            Some("test-secret"),
            "inputs-v2"
        ));
        s.deployment.host = "another-host".into();
        assert!(reusable(
            &bundle,
            root.path(),
            &s,
            Some("test-secret"),
            "inputs-v1"
        ));
        s.port += 1;
        assert!(!reusable(
            &bundle,
            root.path(),
            &s,
            Some("test-secret"),
            "inputs-v1"
        ));
        s.port -= 1;
        fs::write(server, b"tampered").unwrap();
        assert!(!reusable(
            &bundle,
            root.path(),
            &s,
            Some("test-secret"),
            "inputs-v1"
        ));
    }
}
