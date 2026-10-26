//! Owned localhost preview of an exported Web ZIP; requires no external server SDK.
#[cfg(test)]
#[path = "preview_tests.rs"]
mod tests;
use std::{
    fs::{self, File},
    io::{self, Read, Write},
    net::{TcpListener, TcpStream},
    path::{Component, Path, PathBuf},
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, Instant},
};

/// Cheap UI snapshot of the independent preview worker.
#[derive(Clone, Default)]
pub struct Status {
    /// Extraction or server activity is in progress.
    pub active: bool,
    /// Local browser address, once ready.
    pub url: Option<String>,
    /// Progress, ready state or actionable failure.
    pub message: String,
}
/// Keeps one preview alive until stopped, replaced, or the IDE exits.
#[derive(Default)]
pub struct Preview {
    status: Arc<Mutex<Status>>,
    cancel: Option<Arc<AtomicBool>>,
    workers: Vec<std::thread::JoinHandle<()>>,
}
impl Preview {
    /// Read status without waiting for filesystem or network IO.
    pub fn status(&self) -> Status {
        self.status
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .clone()
    }
    /// Extract a completed ZIP in the background, serve it, and open the default browser.
    pub fn start(&mut self, artifact: PathBuf) -> io::Result<()> {
        self.start_server(artifact, true, None)
    }
    /// Open an existing build on an available localhost port without consulting multiplayer settings.
    pub fn start_standalone(&mut self, artifact: PathBuf) -> io::Result<()> {
        self.start_server(
            artifact,
            true,
            Some(((std::net::Ipv4Addr::LOCALHOST, 0).into(), String::new())),
        )
    }
    /// Serve at the exact local Origin accepted by the multiplayer gateway.
    pub fn start_at_origin(&mut self, artifact: PathBuf, origin: &str) -> io::Result<()> {
        let address = local_origin_address(origin)?;
        self.stop();
        self.start_server(artifact, true, Some((address, format!("{origin}/"))))
    }
    /// Serve the same exported ZIP without launching a browser (CLI/tests).
    pub fn serve(&mut self, artifact: PathBuf) -> io::Result<()> {
        self.start_server(artifact, false, None)
    }
    fn start_server(
        &mut self,
        artifact: PathBuf,
        open: bool,
        endpoint: Option<(std::net::SocketAddr, String)>,
    ) -> io::Result<()> {
        let current = self.status();
        if current.active {
            if let Some(url) = current.url.filter(|_| open) {
                let status = self.status.clone();
                std::thread::Builder::new()
                    .name("scenemax-preview-browser".into())
                    .spawn(move || {
                        if let Err(error) = open_browser(&url) {
                            status
                                .lock()
                                .unwrap_or_else(std::sync::PoisonError::into_inner)
                                .message = format!(
                                "Server is running at {url}; could not open browser: {error}"
                            );
                        }
                    })?;
            }
            return Ok(());
        }
        self.stop();
        self.status = Arc::new(Mutex::new(Status {
            active: true,
            message: "Preparing local Web preview…".into(),
            ..Default::default()
        }));
        let state = self.status.clone();
        let cancel = Arc::new(AtomicBool::new(false));
        self.cancel = Some(cancel.clone());
        let result = std::thread::Builder::new()
            .name("scenemax-web-preview".into())
            .spawn(move || {
                let result = serve_archive_at(&artifact, &cancel, &state, open, endpoint);
                let mut state = state
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                state.active = false;
                state.url = None;
                state.message = match result {
                    Err(error) if !cancel.load(Ordering::Relaxed) => {
                        format!("Local preview failed: {error}")
                    }
                    _ => "Local preview stopped.".into(),
                };
            });
        match result {
            Ok(worker) => {
                self.workers.retain(|worker| !worker.is_finished());
                self.workers.push(worker);
            }
            Err(error) => {
                self.stop();
                return Err(error);
            }
        }
        Ok(())
    }
    /// Stop this preview asynchronously; the worker releases its temporary files.
    pub fn stop(&mut self) {
        if let Some(cancel) = self.cancel.take() {
            cancel.store(true, Ordering::Relaxed);
        }
        let mut state = self
            .status
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        *state = Status::default();
    }
}
impl Drop for Preview {
    fn drop(&mut self) {
        self.stop();
        for worker in self.workers.drain(..) {
            let _ = worker.join();
        }
    }
}
fn check(cancel: &AtomicBool) -> io::Result<()> {
    if cancel.load(Ordering::Relaxed) {
        Err(io::ErrorKind::Interrupted.into())
    } else {
        Ok(())
    }
}
fn unpack(artifact: &Path, root: &Path, cancel: &AtomicBool) -> io::Result<()> {
    let mut zip = zip::ZipArchive::new(File::open(artifact)?)?;
    if zip.len() > 100_000 {
        return Err(io::Error::other("Web archive contains too many files"));
    }
    let mut total = 0u64;
    let mut buffer = [0; 65536];
    for i in 0..zip.len() {
        check(cancel)?;
        let mut entry = zip.by_index(i)?;
        let relative = entry
            .enclosed_name()
            .ok_or_else(|| io::Error::other("Invalid Web archive path"))?;
        if relative.components().count() > 64
            || relative.to_string_lossy().contains([':', '\\'])
            || entry
                .unix_mode()
                .is_some_and(|mode| mode & 0o170000 == 0o120000)
        {
            return Err(io::Error::other("Unsupported Web archive entry"));
        }
        let path = root.join(relative);
        if entry.is_dir() {
            fs::create_dir_all(path)?;
            continue;
        }
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut file = File::options().write(true).create_new(true).open(path)?;
        loop {
            check(cancel)?;
            let count = entry.read(&mut buffer)?;
            if count == 0 {
                break;
            }
            total += count as u64;
            if total > 16 * 1024 * 1024 * 1024 {
                return Err(io::Error::other("Web archive exceeds 16 GiB"));
            }
            file.write_all(&buffer[..count])?;
        }
    }
    if !root.join("index.html").is_file() {
        return Err(io::Error::other("Web ZIP is missing index.html"));
    }
    Ok(())
}
#[cfg(test)]
fn serve_archive(
    artifact: &Path,
    cancel: &AtomicBool,
    state: &Mutex<Status>,
    open: bool,
) -> io::Result<()> {
    serve_archive_at(artifact, cancel, state, open, None)
}
/// Validate a local HTTP origin without binding to an external network interface.
pub fn local_origin_address(origin: &str) -> io::Result<std::net::SocketAddr> {
    let authority = origin.strip_prefix("http://").ok_or_else(|| io::Error::other("Browser testing requires an allowed game origin such as http://localhost:8080. The deployed gateway must allow this same origin."))?;
    let authority = authority
        .strip_prefix("localhost:")
        .map(|p| format!("127.0.0.1:{p}"))
        .unwrap_or_else(|| authority.to_owned());
    let address = authority.parse::<std::net::SocketAddr>().map_err(|_| io::Error::other("Browser testing requires a local HTTP origin with an explicit port, for example http://localhost:8080"))?;
    if !address.ip().is_loopback() || address.port() == 0 {
        return Err(io::Error::other(
            "Browser testing only serves on a loopback address and a nonzero port",
        ));
    }
    Ok(address)
}
fn serve_archive_at(
    artifact: &Path,
    cancel: &AtomicBool,
    state: &Mutex<Status>,
    open: bool,
    endpoint: Option<(std::net::SocketAddr, String)>,
) -> io::Result<()> {
    let directory = tempfile::Builder::new()
        .prefix("scenemax-web-preview-")
        .tempdir()?;
    unpack(artifact, directory.path(), cancel)?;
    check(cancel)?;
    let endpoint = match endpoint {
        Some(endpoint) => Some(endpoint),
        None => exported_endpoint(directory.path())?,
    };
    let address = endpoint
        .as_ref()
        .map(|(address, _)| *address)
        .unwrap_or_else(|| (std::net::Ipv4Addr::LOCALHOST, 0).into());
    let listener = TcpListener::bind(address).map_err(|e| io::Error::other(format!("Cannot serve local game at {address}: {e}. Stop any other server using this port and retry.")))?;
    listener.set_nonblocking(true)?;
    let host = listener.local_addr()?.to_string();
    let url = endpoint
        .map(|(_, url)| url)
        .filter(|url| !url.is_empty())
        .unwrap_or_else(|| format!("http://{host}/"));
    let host = url
        .trim_start_matches("http://")
        .trim_end_matches('/')
        .to_owned();
    {
        let mut state = state
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        state.active = true;
        state.url = Some(url.clone());
        state.message = format!("Running at {url} — close the IDE or use Stop Test to stop.");
    }
    if open {
        open_browser(&url)?;
    }
    // Keep the extracted files alive until every connection has stopped.
    std::thread::scope(|scope| {
        let mut connections: Vec<std::thread::ScopedJoinHandle<'_, ()>> = Vec::new();
        while !cancel.load(Ordering::Relaxed) {
            connections.retain(|worker| !worker.is_finished());
            if connections.len() >= 16 {
                std::thread::sleep(Duration::from_millis(15));
                continue;
            }
            match listener.accept() {
                Ok((stream, _)) => {
                    let root = directory.path();
                    let host = &host;
                    connections.push(scope.spawn(move || {
                        if let Err(error) = respond(stream, root, host, cancel) {
                            if !cancel.load(Ordering::Relaxed)
                                && !matches!(error.kind(), io::ErrorKind::ConnectionReset | io::ErrorKind::ConnectionAborted | io::ErrorKind::BrokenPipe)
                            {
                                state.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
                                    .message = format!("Local preview request failed: {error}. Reload the browser to retry.");
                            }
                        }
                    }));
                }
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => {
                    std::thread::sleep(Duration::from_millis(15))
                }
                Err(e) => {
                    cancel.store(true, Ordering::Relaxed);
                    return Err(e);
                }
            }
        }
        Ok(())
    })
}
fn exported_endpoint(root: &Path) -> io::Result<Option<(std::net::SocketAddr, String)>> {
    let path = root.join("network.json");
    if !path.is_file() {
        return Ok(None);
    }
    let network: serde_json::Value = serde_json::from_slice(&fs::read(path)?)?;
    if network["websocket_url"].as_str().unwrap_or("").is_empty() {
        return Ok(None);
    }
    let origin = network["allowed_origin"].as_str().filter(|s| !s.is_empty())
        .ok_or_else(|| io::Error::other("This multiplayer export has no allowed game origin. Rebuild it, or use Project Settings > Multiplayer > Deployment > Test With Remote Server."))?;
    let address = local_origin_address(origin)?;
    Ok(Some((address, format!("{origin}/"))))
}

fn open_browser(url: &str) -> io::Result<()> {
    #[cfg(windows)]
    let program = "explorer.exe";
    #[cfg(target_os = "macos")]
    let program = "open";
    #[cfg(all(not(windows), not(target_os = "macos")))]
    let program = "xdg-open";
    let mut command = std::process::Command::new(program);
    command.arg(url);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    let mut child = command.spawn()?;
    std::thread::spawn(move || {
        let _ = child.wait();
    });
    Ok(())
}
fn url_path(target: &str) -> Option<PathBuf> {
    let path = target.split('?').next()?.strip_prefix('/')?;
    let mut decoded = Vec::new();
    let mut bytes = path.bytes();
    while let Some(byte) = bytes.next() {
        decoded.push(if byte == b'%' {
            let high = (bytes.next()? as char).to_digit(16)?;
            let low = (bytes.next()? as char).to_digit(16)?;
            (high * 16 + low) as u8
        } else {
            byte
        });
    }
    let text = String::from_utf8(decoded).ok()?;
    if text.contains(['\\', ':']) || text.chars().any(char::is_control) {
        return None;
    }
    let path = PathBuf::from(text);
    if path
        .components()
        .any(|c| !matches!(c, Component::Normal(_) | Component::CurDir))
    {
        return None;
    }
    Some(path)
}
fn mime(path: &Path) -> &'static str {
    match path
        .extension()
        .and_then(|s| s.to_str())
        .unwrap_or("")
        .to_ascii_lowercase()
        .as_str()
    {
        "html" => "text/html; charset=utf-8",
        "js" | "mjs" => "text/javascript; charset=utf-8",
        "wasm" => "application/wasm",
        "json" | "gltf" => "application/json",
        "css" => "text/css",
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "svg" => "image/svg+xml",
        "ogg" => "audio/ogg",
        "mp3" => "audio/mpeg",
        "wav" => "audio/wav",
        _ => "application/octet-stream",
    }
}
fn respond(mut stream: TcpStream, root: &Path, host: &str, cancel: &AtomicBool) -> io::Result<()> {
    // Explicit nonblocking IO preserves exact partial-write counts on Windows,
    // including when browser streaming compilation temporarily stops reading.
    stream.set_nonblocking(true)?;
    let mut header = Vec::new();
    let mut byte = [0];
    let header_deadline = Instant::now() + Duration::from_secs(2);
    while header.len() < 16384 && !header.ends_with(b"\r\n\r\n") {
        check(cancel)?;
        if Instant::now() >= header_deadline {
            return Ok(());
        }
        match stream.read(&mut byte) {
            Ok(0) => return Ok(()),
            Ok(_) => header.push(byte[0]),
            Err(error)
                if matches!(
                    error.kind(),
                    io::ErrorKind::WouldBlock
                        | io::ErrorKind::TimedOut
                        | io::ErrorKind::Interrupted
                ) =>
            {
                std::thread::sleep(Duration::from_millis(5));
                continue;
            }
            Err(error) => return Err(error),
        }
    }
    let request = String::from_utf8_lossy(&header);
    let mut lines = request.lines();
    let parts: Vec<_> = lines.next().unwrap_or("").split_whitespace().collect();
    let allowed_host = lines.clone().any(|line| {
        line.split_once(':')
            .is_some_and(|(key, value)| key.eq_ignore_ascii_case("host") && value.trim() == host)
    });
    if !allowed_host
        || !header.ends_with(b"\r\n\r\n")
        || parts.len() != 3
        || !matches!(parts[0], "GET" | "HEAD")
    {
        return stream.write_all(
            b"HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
        );
    }
    let Some(relative) = url_path(parts[1]) else {
        return stream.write_all(
            b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
        );
    };
    let mut path = root.join(relative);
    if path.is_dir() {
        path.push("index.html");
    }
    let Ok(mut file) = File::open(&path) else {
        return stream.write_all(
            b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
        );
    };
    let metadata = file.metadata()?;
    if !metadata.is_file() {
        return Ok(());
    }
    let response = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: {}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n",
        mime(&path),
        metadata.len()
    );
    write_response(&mut stream, response.as_bytes(), cancel)?;
    if parts[0] != "HEAD" {
        let mut buffer = [0; 65536];
        loop {
            check(cancel)?;
            let count = file.read(&mut buffer)?;
            if count == 0 {
                break;
            }
            write_response(&mut stream, &buffer[..count], cancel)?;
        }
    }
    Ok(())
}

// Preserve partial writes when browser streaming compilation applies backpressure.
// Short retry intervals keep Stop responsive; only a prolonged stall ends a transfer.
fn write_response(stream: &mut TcpStream, mut bytes: &[u8], cancel: &AtomicBool) -> io::Result<()> {
    let mut last_progress = Instant::now();
    while !bytes.is_empty() {
        check(cancel)?;
        match stream.write(bytes) {
            Ok(0) => return Err(io::ErrorKind::WriteZero.into()),
            Ok(count) => {
                bytes = &bytes[count..];
                last_progress = Instant::now();
            }
            Err(error)
                if matches!(
                    error.kind(),
                    io::ErrorKind::WouldBlock
                        | io::ErrorKind::TimedOut
                        | io::ErrorKind::Interrupted
                ) =>
            {
                if last_progress.elapsed() >= Duration::from_secs(60) {
                    return Err(io::Error::new(
                        io::ErrorKind::TimedOut,
                        "Browser stopped receiving data for 60 seconds",
                    ));
                }
                std::thread::sleep(Duration::from_millis(5));
            }
            Err(error) => return Err(error),
        }
    }
    Ok(())
}
