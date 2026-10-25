//! Lifecycle of the local dedicated server owned by the IDE.
use std::{
    io::{Read, Seek, SeekFrom},
    path::Path,
    process::{Child, Command, Stdio},
    time::{Duration, Instant},
};

/// An owned child; never terminates servers started elsewhere.
#[derive(Default)]
pub struct LocalServer {
    child: Option<Child>,
    gateway: Option<GatewayProcess>,
    log: Option<tempfile::NamedTempFile>,
    started: Option<Instant>,
    port: u16,
    listening: bool,
    last_poll: Option<Instant>,
}
impl LocalServer {
    /// Both configured transports confirmed their listening sockets.
    pub fn ready(&self) -> bool {
        self.listening && self.gateway.as_ref().is_none_or(|g| g.ready)
    }
    /// Whether this IDE owns a running or starting server.
    pub fn active(&self) -> bool {
        self.child.is_some()
    }
    /// Start a freshly configured native executable with local-only binding.
    pub fn start(&mut self, executable: &Path, port: u16) -> Result<(), String> {
        if self.active() {
            return Err("A local UDP server is already running. Stop it first.".into());
        }
        let log = tempfile::NamedTempFile::new().map_err(|e| e.to_string())?;
        let stream = log.reopen().map_err(|e| e.to_string())?;
        let mut command = Command::new(executable);
        command
            .current_dir(executable.parent().ok_or("Invalid server path")?)
            .env("SCENEMAX_MP_BIND_ADDRESS", "127.0.0.1")
            .env("SCENEMAX_MP_PORT", port.to_string())
            .env_remove("SCENEMAX_MULTIPLAYER_PASSWORD")
            .stdin(Stdio::null())
            .stdout(Stdio::from(stream.try_clone().map_err(|e| e.to_string())?))
            .stderr(Stdio::from(stream));
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        self.child = Some(
            command
                .spawn()
                .map_err(|e| format!("Could not start local server: {e}"))?,
        );
        self.log = Some(log);
        self.port = port;
        self.listening = false;
        self.last_poll = None;
        self.started = Some(Instant::now());
        Ok(())
    }
    /// Start both owned transports; a gateway startup failure also stops UDP.
    pub fn start_with_gateway(
        &mut self,
        executable: &Path,
        port: u16,
        gateway: Option<(&Path, &scenemax_ide_core::multiplayer::BrowserGateway)>,
    ) -> Result<(), String> {
        self.start(executable, port)?;
        if let Some((path, settings)) = gateway {
            match GatewayProcess::start(path, port, settings) {
                Ok(process) => self.gateway = Some(process),
                Err(error) => {
                    self.stop();
                    return Err(error);
                }
            }
        }
        Ok(())
    }
    /// Poll process health and the server's successful bind announcement.
    pub fn poll(&mut self) -> Option<String> {
        if self
            .last_poll
            .is_some_and(|t| t.elapsed() < Duration::from_millis(250))
        {
            return None;
        }
        self.last_poll = Some(Instant::now());
        if let Some(gateway) = &mut self.gateway {
            if let Err(error) = gateway.poll() {
                self.stop();
                return Some(error);
            }
        }
        let child = self.child.as_mut()?;
        let exit = match child.try_wait() {
            Ok(exit) => exit,
            Err(e) => {
                self.stop();
                return Some(format!("UDP server status failed: {e}"));
            }
        };
        let mut output = String::new();
        if let Some(log) = &self.log
            && (!self.listening || exit.is_some())
        {
            if let Ok(mut file) = log.reopen() {
                let _ = file.seek(SeekFrom::Start(0));
                let _ = file.take(8192).read_to_string(&mut output);
            }
        }
        if let Some(exit) = exit {
            self.stop();
            return Some(format!(
                "UDP server stopped ({exit}). {}",
                output.lines().take(3).collect::<Vec<_>>().join(" ")
            ));
        }
        if confirmed_listening(&output, self.port) {
            self.listening = true;
        }
        if self.listening {
            let gateway = self
                .gateway
                .as_ref()
                .map(|g| {
                    format!(
                        " · Gateway {} · ws://127.0.0.1:{}",
                        if g.ready { "listening" } else { "starting" },
                        g.port
                    )
                })
                .unwrap_or_default();
            return Some(format!("UDP listening · 127.0.0.1:{}{gateway}", self.port));
        }
        if self
            .started
            .is_some_and(|t| t.elapsed() > Duration::from_secs(10))
        {
            self.stop();
            return Some("UDP server failed to confirm listening within 10 seconds.".into());
        }
        Some(format!("UDP starting · port {}", self.port))
    }
    /// Stop and reap only the server launched by this instance.
    pub fn stop(&mut self) {
        self.gateway = None;
        if let Some(mut child) = self.child.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
        self.log = None;
        self.listening = false;
    }
}
impl Drop for LocalServer {
    fn drop(&mut self) {
        self.stop();
    }
}
fn confirmed_listening(output: &str, port: u16) -> bool {
    output.lines().any(|line| {
        line.starts_with("[SceneMax MP] server listening address=127.0.0.1 ")
            && line
                .split_whitespace()
                .any(|word| word == format!("port={port}"))
    })
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn listening_requires_successful_bind_and_exact_port() {
        assert!(confirmed_listening(
            "[SceneMax MP] server listening address=127.0.0.1 port=9001 game=\"test\"",
            9001
        ));
        assert!(!confirmed_listening(
            "[SceneMax MP] server listening address=127.0.0.1 port=90010",
            9001
        ));
        assert!(!confirmed_listening("error: AddressInUse port=9001", 9001));
    }
    #[test]
    #[ignore = "requires the native prebuilt Zig server"]
    fn native_server_reports_listening_rejects_duplicate_and_stops() {
        let platform = if cfg!(windows) {
            "windows-x64"
        } else if cfg!(target_os = "macos") {
            "macos-x64"
        } else {
            "linux-x64"
        };
        let name = if cfg!(windows) {
            "scenemax-mp-server.exe"
        } else {
            "scenemax-mp-server"
        };
        let template = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../../tools/multiplayer-server/bin")
            .join(platform)
            .join(name);
        let dir = tempfile::tempdir().unwrap();
        let executable = dir.path().join(name);
        std::fs::copy(template, &executable).unwrap();
        let socket = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        let port = socket.local_addr().unwrap().port();
        drop(socket);
        let mut server = LocalServer::default();
        server.start(&executable, port).unwrap();
        let deadline = Instant::now() + Duration::from_secs(5);
        loop {
            let Some(status) = server.poll() else {
                std::thread::sleep(Duration::from_millis(20));
                continue;
            };
            if status.starts_with("UDP listening") {
                break;
            }
            assert!(server.active() && Instant::now() < deadline, "{status}");
            std::thread::sleep(Duration::from_millis(20));
        }
        assert!(server.start(&executable, port).is_err());
        assert!(std::net::UdpSocket::bind(("127.0.0.1", port)).is_err());
        let mut conflicting = LocalServer::default();
        conflicting.start(&executable, port).unwrap();
        loop {
            let Some(status) = conflicting.poll() else {
                std::thread::sleep(Duration::from_millis(20));
                continue;
            };
            if !conflicting.active() {
                assert!(status.contains("stopped"));
                break;
            }
            assert!(Instant::now() < deadline, "{status}");
            std::thread::sleep(Duration::from_millis(20));
        }
        server.stop();
        assert!(!server.active());
        assert!(std::net::UdpSocket::bind(("127.0.0.1", port)).is_ok());
    }

    #[test]
    #[ignore = "set SCENEMAX_TEST_WEB_GATEWAY; requires native Zig template"]
    fn gateway_pair_conflict_and_exit_stop_both_owned_processes() {
        let gateway = std::path::PathBuf::from(
            std::env::var_os("SCENEMAX_TEST_WEB_GATEWAY").expect("gateway executable"),
        );
        let platform = if cfg!(windows) {
            "windows-x64"
        } else if cfg!(target_os = "macos") {
            "macos-x64"
        } else {
            "linux-x64"
        };
        let name = if cfg!(windows) {
            "scenemax-mp-server.exe"
        } else {
            "scenemax-mp-server"
        };
        let template = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../../tools/multiplayer-server/bin")
            .join(platform)
            .join(name);
        let udp = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        let port = udp.local_addr().unwrap().port();
        drop(udp);
        let tcp = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let tcp_port = tcp.local_addr().unwrap().port();
        let mut settings = scenemax_ide_core::multiplayer::BrowserGateway::default();
        settings.listen = format!("127.0.0.1:{tcp_port}");
        let mut pair = LocalServer::default();
        pair.start_with_gateway(&template, port, Some((&gateway, &settings)))
            .unwrap();
        let deadline = Instant::now() + Duration::from_secs(10);
        // Occupied TCP port must stop the UDP child as well.
        while pair.active() {
            pair.poll();
            assert!(Instant::now() < deadline);
            std::thread::sleep(Duration::from_millis(30));
        }
        assert!(std::net::UdpSocket::bind(("127.0.0.1", port)).is_ok());
        drop(tcp);
        pair.start_with_gateway(&template, port, Some((&gateway, &settings)))
            .unwrap();
        loop {
            let status = pair.poll().unwrap_or_default();
            if status.contains("Gateway listening") {
                break;
            }
            assert!(pair.active() && Instant::now() < deadline, "{status}");
            std::thread::sleep(Duration::from_millis(30));
        }
        assert!(std::net::TcpListener::bind(("127.0.0.1", tcp_port)).is_err());
        pair.gateway.as_mut().unwrap().child.kill().unwrap();
        while pair.active() {
            pair.poll();
            assert!(Instant::now() < deadline);
            std::thread::sleep(Duration::from_millis(30));
        }
        assert!(std::net::TcpListener::bind(("127.0.0.1", tcp_port)).is_ok());
        assert!(std::net::UdpSocket::bind(("127.0.0.1", port)).is_ok());
        // Explicit stop also releases both sockets.
        pair.start_with_gateway(&template, port, Some((&gateway, &settings)))
            .unwrap();
        pair.stop();
        assert!(!pair.active());
    }
}

struct GatewayProcess {
    child: Child,
    log: tempfile::NamedTempFile,
    started: Instant,
    port: u16,
    ready: bool,
}
impl GatewayProcess {
    fn start(
        path: &Path,
        udp_port: u16,
        settings: &scenemax_ide_core::multiplayer::BrowserGateway,
    ) -> Result<Self, String> {
        settings.validate()?;
        let port = settings.listen_address()?.port();
        let log = tempfile::NamedTempFile::new().map_err(|e| e.to_string())?;
        let output = log.reopen().map_err(|e| e.to_string())?;
        let mut command = Command::new(path);
        command
            .args([
                format!("127.0.0.1:{port}"),
                format!("127.0.0.1:{udp_port}"),
                settings.allowed_origin.clone(),
            ])
            .env_remove("SCENEMAX_MULTIPLAYER_PASSWORD")
            .stdin(Stdio::null())
            .stdout(Stdio::from(output.try_clone().map_err(|e| e.to_string())?))
            .stderr(Stdio::from(output));
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        let child = command
            .spawn()
            .map_err(|e| format!("Could not start browser gateway: {e}"))?;
        Ok(Self {
            child,
            log,
            port,
            started: Instant::now(),
            ready: false,
        })
    }
    fn poll(&mut self) -> Result<(), String> {
        let exit = self.child.try_wait().map_err(|e| e.to_string())?;
        let mut output = String::new();
        if !self.ready || exit.is_some() {
            let mut file = self.log.reopen().map_err(|e| e.to_string())?;
            file.seek(SeekFrom::Start(0)).map_err(|e| e.to_string())?;
            file.take(8192)
                .read_to_string(&mut output)
                .map_err(|e| e.to_string())?;
        }
        if let Some(exit) = exit {
            return Err(format!(
                "Browser gateway stopped ({exit}); local UDP server stopped. {}",
                output.lines().take(3).collect::<Vec<_>>().join(" ")
            ));
        }
        self.ready |= output.lines().any(|line| {
            line.starts_with(&format!("Browser gateway 127.0.0.1:{} -> UDP ", self.port))
        });
        if !self.ready && self.started.elapsed() > Duration::from_secs(10) {
            return Err("Browser gateway did not confirm listening within 10 seconds; local servers stopped.".into());
        }
        Ok(())
    }
}
impl Drop for GatewayProcess {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}
