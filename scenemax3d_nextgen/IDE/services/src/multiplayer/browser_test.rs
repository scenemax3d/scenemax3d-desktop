use super::{build, gateway, local_server::LocalServer, patch, progress};
use scenemax_ide_core::multiplayer::Settings;
use std::{
    path::Path,
    sync::atomic::AtomicBool,
    time::{Duration, Instant},
};

/// Connection destination for a browser test, independent of publishing options.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TestTarget {
    /// Start an IDE-owned loopback server and gateway.
    Local,
    /// Connect through the saved deployed gateway.
    Remote,
}

/// Processes stop before their temporary executables are removed.
pub struct LocalTest {
    /// Health and lifecycle of the two owned transports.
    pub server: LocalServer,
    _bundle: tempfile::TempDir,
}

pub(super) fn settings(saved: &Settings, target: TestTarget) -> Result<Settings, String> {
    let mut s = saved.clone();
    if target == TestTarget::Local {
        let port = s
            .port
            .checked_add(1)
            .ok_or("Local gateway requires UDP port below 65535")?;
        s.server = "127.0.0.1".into();
        s.platform = if cfg!(windows) {
            "windows-x64"
        } else if cfg!(target_os = "macos") {
            "macos-x64"
        } else {
            "linux-x64"
        }
        .into();
        s.browser_gateway.enabled = true;
        s.browser_gateway.websocket_url = format!("ws://127.0.0.1:{port}");
        s.browser_gateway.listen = format!("127.0.0.1:{port}");
        s.browser_gateway.allowed_origin = "http://localhost:8080".into();
    } else if !s.browser_gateway.enabled {
        return Err("Enable and configure the deployed browser gateway before testing with the remote server".into());
    }
    s.validate()?;
    crate::deployment::preview::local_origin_address(&s.browser_gateway.allowed_origin)
        .map_err(|e| e.to_string())?;
    Ok(s)
}

pub(super) fn start_local(
    root: &Path,
    s: &Settings,
    cancel: &AtomicBool,
) -> Result<LocalTest, String> {
    progress::stage(5, "Building local Zig server and browser gateway");
    let installation = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../..");
    let (mut bytes, _) = build::prepare(&installation, &s.platform, cancel)?;
    // Local loopback tests have no password; do not inherit a remote secret.
    patch(&mut bytes, s, root, None)?;
    let bundle = tempfile::tempdir().map_err(|e| e.to_string())?;
    let executable = bundle.path().join(if cfg!(windows) {
        "scenemax-mp-server.exe"
    } else {
        "scenemax-mp-server"
    });
    std::fs::write(&executable, bytes).map_err(|e| e.to_string())?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&executable, std::fs::Permissions::from_mode(0o700))
            .map_err(|e| e.to_string())?;
    }
    let bytes = gateway::prepare(&s.platform, cancel)?;
    let gateway = gateway::install(bundle.path(), s, &bytes)?;
    let mut local = LocalTest {
        server: LocalServer::default(),
        _bundle: bundle,
    };
    local
        .server
        .start_with_gateway(&executable, s.port, Some((&gateway, &s.browser_gateway)))?;
    let started = Instant::now();
    while !local.server.ready() {
        build::check(cancel)?;
        if let Some(status) = local.server.poll() {
            progress::stage(10, &status);
            if !local.server.active() {
                return Err(status);
            }
        }
        if started.elapsed() > Duration::from_secs(12) {
            return Err("Local server and gateway did not become ready".into());
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    Ok(local)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn local_override_preserves_remote_preferences_and_project_identity() {
        let mut saved = Settings::default();
        saved.project_guid = "browser-target-regression".into();
        saved.server = "remote.example".into();
        saved.platform = "linux-x64".into();
        saved.browser_gateway.enabled = true;
        saved.browser_gateway.websocket_url = "ws://remote.example:9002".into();
        let original = serde_json::to_value(&saved).unwrap();
        let local = settings(&saved, TestTarget::Local).unwrap();
        assert_eq!(local.server, "127.0.0.1");
        assert_eq!(
            local.browser_gateway.websocket_url,
            format!("ws://127.0.0.1:{}", saved.port + 1)
        );
        assert_eq!(local.project_guid, saved.project_guid);
        assert_eq!(local.session_id, saved.session_id);
        assert_eq!(serde_json::to_value(&saved).unwrap(), original);
        let remote = settings(&saved, TestTarget::Remote).unwrap();
        assert_eq!(serde_json::to_value(remote).unwrap(), original);
    }
    #[test]
    #[ignore = "requires native Zig template and gateway build tools"]
    fn local_transports_start_and_drop_releases_ports() {
        let root = tempfile::tempdir().unwrap();
        let udp = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        let port = udp.local_addr().unwrap().port();
        let tcp = std::net::TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, port + 1)).unwrap();
        let mut saved = Settings::default();
        saved.project_guid = "browser-startup-regression".into();
        saved.port = port;
        let s = settings(&saved, TestTarget::Local).unwrap();
        drop((udp, tcp));
        let local = start_local(root.path(), &s, &AtomicBool::new(false)).unwrap();
        assert!(local.server.ready());
        assert!(std::net::TcpStream::connect((std::net::Ipv4Addr::LOCALHOST, port + 1)).is_ok());
        drop(local);
        assert!(std::net::UdpSocket::bind((std::net::Ipv4Addr::LOCALHOST, port)).is_ok());
        assert!(std::net::TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, port + 1)).is_ok());
    }
}
