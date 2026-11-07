use super::{Settings, gateway, read};
use std::path::{Path, PathBuf};

/// A completed server build retained in the project directory.
#[derive(Clone)]
pub struct SavedBundle {
    /// Dedicated UDP executable.
    pub server: PathBuf,
    /// Optional browser gateway executable.
    pub gateway: Option<PathBuf>,
    /// Settings embedded at build time.
    pub settings: Settings,
}

impl SavedBundle {
    /// Reject changed settings and damaged executable payloads.
    pub fn validate(&self, settings: &Settings) -> Result<(), String> {
        let mut expected = self.settings.clone();
        expected.deployment = settings.deployment.clone();
        if expected != *settings {
            return Err("Settings changed since this server was built. Build Server again before running locally.".into());
        }
        let dir = self.server.parent().ok_or("Invalid build folder")?;
        let manifest: serde_json::Value =
            serde_json::from_slice(&read(&dir.join("build-manifest.json"), 1024 * 1024)?)
                .map_err(|e| e.to_string())?;
        use sha2::{Digest, Sha256};
        for path in std::iter::once(&self.server).chain(self.gateway.iter()) {
            let bytes = read(path, 64 * 1024 * 1024)?;
            let name = path
                .file_name()
                .and_then(|n| n.to_str())
                .ok_or("Invalid build filename")?;
            if manifest["files"][name].as_str() != Some(&format!("{:x}", Sha256::digest(bytes))) {
                return Err(
                    "Server build changed on disk. Build Server again before running locally."
                        .into(),
                );
            }
        }
        Ok(())
    }
}

/// Newest complete bundles first. Ignore incomplete or removed builds.
pub fn saved_bundles(root: &Path) -> Vec<SavedBundle> {
    let mut bundles = Vec::new();
    let Ok(entries) = std::fs::read_dir(root.join("multiplayer_server")) else {
        return Vec::new();
    };
    for entry in entries.flatten() {
        let dir = entry.path();
        let manifest = dir.join("build-manifest.json");
        let bundle = (|| -> Option<_> {
            let value: serde_json::Value =
                serde_json::from_slice(&read(&manifest, 1024 * 1024).ok()?).ok()?;
            let settings: Settings = serde_json::from_value(value.get("settings")?.clone()).ok()?;
            let server = dir.join(if settings.platform == "windows-x64" {
                "scenemax-mp-server.exe"
            } else {
                "scenemax-mp-server"
            });
            let gateway = settings
                .browser_gateway
                .enabled
                .then(|| dir.join(gateway::executable_name(&settings.platform)));
            if !server.is_file() || gateway.as_ref().is_some_and(|p| !p.is_file()) {
                return None;
            }
            Some((
                manifest.metadata().ok()?.modified().ok()?,
                SavedBundle {
                    server,
                    gateway,
                    settings,
                },
            ))
        })();
        if let Some(bundle) = bundle {
            bundles.push(bundle);
        }
    }
    bundles.sort_by(|a, b| b.0.cmp(&a.0));
    bundles.into_iter().map(|(_, bundle)| bundle).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn discovers_complete_builds_and_rejects_stale_or_modified_payloads() {
        let root = tempfile::tempdir().unwrap();
        let dir = root.path().join("multiplayer_server/build");
        std::fs::create_dir_all(&dir).unwrap();
        let settings = Settings::default();
        let name = if settings.platform == "windows-x64" {
            "scenemax-mp-server.exe"
        } else {
            "scenemax-mp-server"
        };
        let server = dir.join(name);
        std::fs::write(&server, b"test payload").unwrap();
        assert!(saved_bundles(root.path()).is_empty());
        super::super::remote::record_bundle(&dir, &settings).unwrap();
        let bundles = saved_bundles(root.path());
        assert_eq!(bundles.len(), 1);
        bundles[0].validate(&settings).unwrap();
        let mut changed = settings.clone();
        changed.session_name.push_str("changed");
        assert!(bundles[0].validate(&changed).is_err());
        std::fs::write(&server, b"modified").unwrap();
        assert!(bundles[0].validate(&settings).is_err());
        std::fs::remove_file(server).unwrap();
        assert!(saved_bundles(root.path()).is_empty());
    }
}
