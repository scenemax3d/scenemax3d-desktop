//! Read-only Java defaults for an IDE-owned launch, prepared off the UI thread.
use super::{Settings, read};
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};

pub(crate) fn defaults(
    root: &Path,
    explicit_catalog: Option<&Path>,
) -> Result<Option<String>, String> {
    let root = root.canonicalize().map_err(|e| e.to_string())?;
    // Native settings remain authoritative, including explicit offline settings.
    if super::settings_path(&root)?.exists() {
        return Ok(None);
    }
    let catalog = explicit_catalog
        .map(PathBuf::from)
        .or_else(|| {
            root.ancestors()
                .map(|p| p.join("projects/projects.json"))
                .find(|p| p.is_file())
        })
        .unwrap_or_else(|| {
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../projects/projects.json")
        });
    let Some(settings) = import(&root, &catalog)? else {
        return Ok(None);
    };
    if settings.server.trim().is_empty() {
        return Ok(None);
    }
    settings.validate()?;
    // Explicit allowlist: never forward unknown catalog fields or passwords.
    Ok(Some(
        serde_json::json!({
            "multiplayer_server": settings.server,
            "multiplayer_port": settings.port.to_string(),
            "project_guid": settings.project_guid,
            "multiplayer_session_id": settings.session_id.to_string(),
            "multiplayer_session_name": settings.session_name,
        })
        .to_string(),
    ))
}

pub(super) fn import(root: &Path, catalog: &Path) -> Result<Option<Settings>, String> {
    if !catalog.exists() {
        return Ok(None);
    }
    let catalog = catalog.canonicalize().map_err(|e| e.to_string())?;
    let base = catalog
        .parent()
        .and_then(Path::parent)
        .ok_or("Invalid project catalog path")?;
    let bytes = read(&catalog, 1024 * 1024)?;
    let json: serde_json::Value =
        serde_json::from_slice(bytes.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&bytes))
            .map_err(|e| format!("Multiplayer project catalog: {e}"))?;
    let Some(rows) = json["projects"].as_array() else {
        return Ok(None);
    };
    if rows.len() > 2048 {
        return Err("Project catalog exceeds 2048 projects".into());
    }
    for row in rows {
        let Some(path) = row["path"].as_str() else {
            continue;
        };
        if base.join(path).canonicalize().ok().as_deref() != Some(root) {
            continue;
        }
        let hash = Sha256::digest(root.to_string_lossy().as_bytes());
        let guid = row["project_guid"]
            .as_str()
            .or(row["projectGuid"].as_str())
            .filter(|s| !s.is_empty())
            .map(str::to_owned)
            .unwrap_or_else(|| hash[..16].iter().map(|b| format!("{b:02x}")).collect());
        return Ok(Some(Settings {
            // No configured server must not cause an unsolicited loopback login.
            server: row["multiplayer_server_ip"].as_str().unwrap_or("").into(),
            port: row["multiplayer_server_port"]
                .as_u64()
                .and_then(|n| u16::try_from(n).ok())
                .filter(|n| *n > 0)
                .unwrap_or(9001),
            project_guid: guid,
            session_name: root
                .file_name()
                .and_then(|s| s.to_str())
                .unwrap_or("local")
                .into(),
            platform: match row["multiplayer_deploy_os"].as_str().unwrap_or("Windows") {
                "Linux" => "linux-x64",
                "Mac" | "macOS" => "macos-x64",
                _ => "windows-x64",
            }
            .into(),
            ..Default::default()
        }));
    }
    Ok(None)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn launch_imports_only_matching_connection_without_writing_or_forwarding_secrets() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().join("game");
        std::fs::create_dir_all(&root).unwrap();
        std::fs::create_dir_all(dir.path().join("projects")).unwrap();
        let catalog = dir.path().join("projects/projects.json");
        let source = serde_json::json!({"projects":[
            {"path":"other","multiplayer_server_ip":"wrong"},
            {"path":"game","multiplayer_server_ip":"192.0.2.42","multiplayer_server_port":9345,"projectGuid":"imported-guid","multiplayer_password":"never-forward"}
        ]}).to_string();
        std::fs::write(&catalog, &source).unwrap();
        let value = defaults(&root, None).unwrap().unwrap();
        assert!(value.contains("192.0.2.42"));
        assert!(value.contains("imported-guid"));
        assert!(!value.contains("never-forward"));
        assert!(!root.join(".scenemax-studio").exists());
        assert_eq!(std::fs::read_to_string(&catalog).unwrap(), source);
        std::fs::create_dir(root.join(".scenemax-studio")).unwrap();
        std::fs::write(root.join(".scenemax-studio/multiplayer.json"), "{}").unwrap();
        assert!(defaults(&root, Some(&catalog)).unwrap().is_none());
    }

    #[test]
    fn unconfigured_or_unrelated_projects_stay_offline_and_missing_guid_is_stable() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        let catalog = root.join("catalog.json");
        std::fs::write(
            &catalog,
            serde_json::json!({"projects":[{"path":root,"multiplayer_server_ip":""}]}).to_string(),
        )
        .unwrap();
        assert!(defaults(&root, Some(&catalog)).unwrap().is_none());
        let a = import(&root, &catalog).unwrap().unwrap();
        assert_eq!(
            a.project_guid,
            import(&root, &catalog).unwrap().unwrap().project_guid
        );
        assert_eq!(a.project_guid.len(), 32);
        std::fs::write(&catalog, "{\"projects\":[]}").unwrap();
        assert!(defaults(&root, Some(&catalog)).unwrap().is_none());
    }
}
