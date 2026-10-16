//! Native multiplayer project settings, separate from game/runtime state.
use serde::{Deserialize, Serialize};
/// Non-secret multiplayer connection and server-build settings.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(default)]
pub struct Settings {
    /// Server hostname or IP.
    pub server: String,
    /// UDP listener port.
    pub port: u16,
    /// Stable project identity shared by clients and the server.
    pub project_guid: String,
    /// Requested room ID (zero allocates a room).
    pub session_id: u32,
    /// Display name for a created room.
    pub session_name: String,
    /// Server template platform.
    pub platform: String,
    /// Unknown fields survive native settings edits.
    #[serde(flatten)]
    pub extra: std::collections::BTreeMap<String, serde_json::Value>,
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            server: "127.0.0.1".into(),
            port: 9001,
            project_guid: String::new(),
            session_id: 1000,
            session_name: "local".into(),
            platform: "windows-x64".into(),
            extra: Default::default(),
        }
    }
}
impl Settings {
    /// Validate fixed wire limits and supported dedicated server targets.
    pub fn validate(&self) -> Result<(), String> {
        if self.server.trim().is_empty()
            || self.server.len() > 253
            || self.server.contains(['\0', '\r', '\n'])
        {
            return Err("Enter a server hostname or IP address".into());
        }
        if self.port == 0 {
            return Err("UDP port must be 1–65535".into());
        }
        for (label, text) in [
            ("Project GUID", &self.project_guid),
            ("Session name", &self.session_name),
        ] {
            if text.trim().is_empty() || text.len() >= 64 || text.contains(['\0', '\r', '\n']) {
                return Err(format!(
                    "{label} must contain 1–63 UTF-8 bytes without line breaks"
                ));
            }
        }
        if !["windows-x64", "linux-x64", "macos-x64"].contains(&self.platform.as_str()) {
            return Err("Choose Windows, Linux or macOS x64".into());
        }
        Ok(())
    }
}
