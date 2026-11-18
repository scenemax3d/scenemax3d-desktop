//! Managed TLS edge for browser gateways. Certificate keys stay on the server.
use scenemax_ide_core::multiplayer::Settings;

pub(super) fn enabled(s: &Settings) -> bool {
    s.browser_gateway.enabled && s.browser_gateway.uses_tls()
}

pub(super) fn config(s: &Settings) -> Result<String, String> {
    let (host, port) = s
        .browser_gateway
        .tls_endpoint()?
        .ok_or("WSS endpoint required")?;
    let upstream = s.browser_gateway.deployment_listener()?.to_string();
    serde_json::to_string_pretty(&serde_json::json!({
        "admin": {"disabled": true},
        "apps": {
            "http": {"servers": {"gateway": {
                "listen": [format!(":{port}")],
                "automatic_https": {"disable_redirects": true},
                "tls_connection_policies": [{}],
                "routes": [{
                    "match": [{"host": [host]}],
                    "handle": [{"handler": "reverse_proxy", "upstreams": [{"dial": upstream}]}]
                }]
            }}},
            "tls": {"certificates": {"automate": [host]}}
        }
    }))
    .map_err(|e| e.to_string())
}

pub(super) fn unit(s: &Settings, project: &str) -> String {
    let d = &s.deployment;
    format!(
        "# SceneMax managed {project}\n[Unit]\nDescription=SceneMax secure browser gateway\nAfter=network-online.target {}-gateway.service\nWants=network-online.target\n[Service]\nType=simple\nDynamicUser=yes\nStateDirectory={}-tls\nStateDirectoryMode=0700\nEnvironment=XDG_DATA_HOME=/var/lib/{}-tls\nExecStart=/usr/bin/caddy run --config {}/current/caddy.json\nRestart=on-failure\nRestartSec=2\nNoNewPrivileges=yes\nProtectSystem=strict\nProtectHome=yes\nPrivateTmp=yes\nAmbientCapabilities=CAP_NET_BIND_SERVICE\n[Install]\nWantedBy=multi-user.target\n",
        d.service, d.service, d.service, d.directory
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn secure_edge_preserves_origin_and_uses_private_backend() {
        let mut s = Settings::default();
        s.browser_gateway.enabled = true;
        s.browser_gateway.websocket_url = "wss://multiplayer.example.com:9443/play".into();
        s.browser_gateway.listen = "0.0.0.0:9002".into();
        let value: serde_json::Value = serde_json::from_str(&config(&s).unwrap()).unwrap();
        // Optional fixture consumed by the real Caddy/WSS integration test.
        if let Ok(path) = std::env::var("SCENEMAX_TEST_TLS_CONFIG") {
            std::fs::write(path, config(&s).unwrap()).unwrap();
        }
        let server = &value["apps"]["http"]["servers"]["gateway"];
        assert_eq!(server["listen"][0], ":9443");
        assert_eq!(
            server["routes"][0]["handle"][0]["upstreams"][0]["dial"],
            "127.0.0.1:9002"
        );
        assert!(server["routes"][0]["handle"][0].get("headers").is_none());
        assert_eq!(
            value["apps"]["tls"]["certificates"]["automate"][0],
            "multiplayer.example.com"
        );
        assert!(unit(&s, "fixture").contains("StateDirectory="));
    }
}
