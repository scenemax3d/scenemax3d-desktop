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
    /// Browser transport and deployment endpoint (no credentials).
    pub browser_gateway: BrowserGateway,
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
            browser_gateway: BrowserGateway::default(),
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
        if self.browser_gateway.enabled {
            self.browser_gateway.validate()?;
        }
        Ok(())
    }
}

/// Settings for the optional WebSocket adapter alongside the UDP server.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(default)]
pub struct BrowserGateway {
    /// Build and start the adapter with the dedicated server.
    pub enabled: bool,
    /// Public endpoint written into browser exports.
    pub websocket_url: String,
    /// Deployment TCP bind address. Local runs always bind to loopback.
    pub listen: String,
    /// Exact browser Origin accepted by the adapter.
    pub allowed_origin: String,
}
impl Default for BrowserGateway {
    fn default() -> Self {
        Self {
            enabled: false,
            websocket_url: "ws://localhost:9002".into(),
            listen: "127.0.0.1:9002".into(),
            allowed_origin: "http://localhost:8080".into(),
        }
    }
}
impl BrowserGateway {
    /// Validate listener, public endpoint and exact Origin independently.
    pub fn validate(&self) -> Result<(), String> {
        self.listen_address()?;
        let endpoint = checked_url(&self.websocket_url, &["ws", "wss"]).map_err(
            |_| "Enter a ws:// or wss:// browser gateway URL without credentials or a fragment",
        )?;
        let origin = checked_url(&self.allowed_origin, &["http", "https"])
            .map_err(|_| "Enter the game's http:// or https:// origin")?;
        if origin.origin().ascii_serialization() != self.allowed_origin {
            return Err("Allowed origin must be an exact origin, for example http://localhost:8080 (no path or trailing slash)".into());
        }
        if origin.scheme() == "https" && endpoint.scheme() != "wss" {
            return Err("HTTPS games require a wss:// gateway URL and TLS termination".into());
        }
        Ok(())
    }
    /// Local runs must match the endpoint embedded in the local browser export.
    pub fn validate_local(&self) -> Result<(), String> {
        self.validate()?;
        let url = checked_url(&self.websocket_url, &["ws"]).map_err(|_| "For Run Server Locally, use a ws://localhost gateway URL. Use Build Server for your public wss:// deployment.".to_string())?;
        if !matches!(url.host_str(), Some("localhost" | "127.0.0.1"))
            || url.port_or_known_default() != Some(self.listen_address()?.port())
        {
            return Err("For Run Server Locally, the browser URL must use localhost or 127.0.0.1 and the gateway listener port.".into());
        }
        Ok(())
    }
    /// Parsed TCP listener used for builds and local lifecycle configuration.
    pub fn listen_address(&self) -> Result<std::net::SocketAddr, String> {
        self.listen
            .parse::<std::net::SocketAddr>()
            .ok()
            .filter(|a| a.port() != 0)
            .ok_or_else(|| "Gateway listener must be an IP:port, for example 127.0.0.1:9002".into())
    }
}
fn checked_url(value: &str, schemes: &[&str]) -> Result<url::Url, ()> {
    if value.len() > 2048 || value.chars().any(|c| c.is_whitespace() || c.is_control()) {
        return Err(());
    }
    let parsed = url::Url::parse(value).map_err(|_| ())?;
    if !schemes.contains(&parsed.scheme())
        || parsed.host_str().is_none()
        || !parsed.username().is_empty()
        || parsed.password().is_some()
        || parsed.fragment().is_some()
        || parsed.port() == Some(0)
    {
        return Err(());
    }
    Ok(parsed)
}

#[cfg(test)]
mod gateway_tests {
    use super::*;
    #[test]
    fn older_settings_default_to_udp_only_and_preserve_unknown_fields() {
        let settings: Settings =
            serde_json::from_str(r#"{"project_guid":"test","future":42}"#).unwrap();
        assert!(!settings.browser_gateway.enabled);
        assert_eq!(serde_json::to_value(settings).unwrap()["future"], 42);
    }
    #[test]
    fn validates_browser_endpoints_and_exact_origins() {
        let mut gateway = BrowserGateway::default();
        gateway.validate().unwrap();
        for bad in [
            "https://example.com",
            "ws://user:secret@example.com",
            "ws://example.com/#x",
            "ws://example.com:0",
            "ws://bad host",
        ] {
            gateway.websocket_url = bad.into();
            assert!(gateway.validate().is_err(), "{bad}");
        }
        gateway.websocket_url = "wss://games.example.com/network".into();
        gateway.allowed_origin = "https://game.example.com".into();
        gateway.validate().unwrap();
        gateway.allowed_origin.push('/');
        assert!(gateway.validate().is_err());
        gateway.allowed_origin = "https://game.example.com".into();
        gateway.websocket_url = "ws://localhost:9002".into();
        assert!(gateway.validate().is_err());
    }
    #[test]
    fn local_run_requires_matching_loopback_endpoint() {
        let mut gateway = BrowserGateway::default();
        gateway.validate_local().unwrap();
        gateway.websocket_url = "ws://localhost:9100".into();
        assert!(gateway.validate_local().is_err());
        gateway.listen = "0.0.0.0:9100".into();
        gateway.validate_local().unwrap();
        gateway.websocket_url = "wss://network.example.com".into();
        gateway.validate().unwrap();
        assert!(gateway.validate_local().is_err());
    }
}
