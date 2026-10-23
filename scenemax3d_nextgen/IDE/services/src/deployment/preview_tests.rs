use super::*;
fn archive(root: &Path, entries: &[(&str, &[u8])]) -> PathBuf {
    let path = root.join("web.zip");
    let mut zip = zip::ZipWriter::new(File::create(&path).unwrap());
    for (name, bytes) in entries {
        zip.start_file(*name, zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap();
    path
}
fn request(host: &str, target: &str, header_host: &str, method: &str) -> Vec<u8> {
    let mut socket = TcpStream::connect(host).unwrap();
    socket
        .set_read_timeout(Some(Duration::from_secs(5)))
        .unwrap();
    write!(
        socket,
        "{method} {target} HTTP/1.1\r\nHost: {header_host}\r\n\r\n"
    )
    .unwrap();
    let mut response = Vec::new();
    socket.read_to_end(&mut response).unwrap();
    response
}
#[test]
fn exported_zip_http_and_stop_lifecycle() {
    let temp = tempfile::tempdir().unwrap();
    let zip = archive(
        temp.path(),
        &[
            ("index.html", b"<canvas></canvas>"),
            ("runtime.wasm", b"\0asm"),
            ("game/space name.txt", b"asset"),
        ],
    );
    let cancel = Arc::new(AtomicBool::new(false));
    let status = Arc::new(Mutex::new(Status::default()));
    let (worker_cancel, worker_status) = (cancel.clone(), status.clone());
    let thread =
        std::thread::spawn(move || serve_archive(&zip, &worker_cancel, &worker_status, false));
    let deadline = std::time::Instant::now() + Duration::from_secs(5);
    let url = loop {
        if let Some(url) = status.lock().unwrap().url.clone() {
            break url;
        }
        assert!(std::time::Instant::now() < deadline);
        std::thread::sleep(Duration::from_millis(10));
    };
    let host = url.trim_start_matches("http://").trim_end_matches('/');
    assert!(request(host, "/", host, "GET").ends_with(b"<canvas></canvas>"));
    let wasm = request(host, "/runtime.wasm", host, "GET");
    assert!(String::from_utf8_lossy(&wasm).contains("Content-Type: application/wasm"));
    assert!(wasm.ends_with(b"\0asm"));
    assert!(request(host, "/game/space%20name.txt", host, "GET").ends_with(b"asset"));
    assert!(request(host, "/runtime.wasm", host, "HEAD").ends_with(b"\r\n\r\n"));
    assert!(request(host, "/%2e%2e/outside", host, "GET").starts_with(b"HTTP/1.1 403"));
    assert!(request(host, "/", "unrelated.example", "GET").starts_with(b"HTTP/1.1 400"));
    assert!(request(host, "/missing", host, "GET").starts_with(b"HTTP/1.1 404"));
    cancel.store(true, Ordering::Relaxed);
    thread.join().unwrap().unwrap();
    assert!(TcpStream::connect(host).is_err());
}
#[test]
fn large_response_survives_a_paused_reader() {
    let temp = tempfile::tempdir().unwrap();
    let bytes = vec![73u8; 32 * 1024 * 1024];
    fs::write(temp.path().join("runtime.wasm"), &bytes).unwrap();
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    listener.set_nonblocking(true).unwrap();
    let host = listener.local_addr().unwrap().to_string();
    let mut client = TcpStream::connect(&host).unwrap();
    client
        .set_read_timeout(Some(Duration::from_secs(15)))
        .unwrap();
    write!(client, "GET /runtime.wasm HTTP/1.1\r\nHost: {host}\r\n\r\n").unwrap();
    let (stream, _) = listener.accept().unwrap();
    let root = temp.path().to_owned();
    let worker = std::thread::spawn(move || respond(stream, &root, &host, &AtomicBool::new(false)));
    // Browsers can pause consumption during Wasm streaming compilation.
    std::thread::sleep(Duration::from_secs(3));
    let mut received = Vec::new();
    client.read_to_end(&mut received).unwrap();
    worker.join().unwrap().unwrap();
    let body = received.windows(4).position(|w| w == b"\r\n\r\n").unwrap() + 4;
    assert_eq!(received.len() - body, bytes.len());
    assert!(&received[body..] == bytes);
}
#[test]
fn unsafe_archives_and_paths_are_rejected() {
    let temp = tempfile::tempdir().unwrap();
    let output = tempfile::tempdir().unwrap();
    let zip = archive(temp.path(), &[("../outside", b"bad")]);
    assert!(unpack(&zip, output.path(), &AtomicBool::new(false)).is_err());
    let zip = archive(temp.path(), &[("data.txt", b"no entry page")]);
    assert!(unpack(&zip, output.path(), &AtomicBool::new(false)).is_err());
    for path in [
        "/%2e%2e/secret",
        "/%5cserver/share",
        "/C:/secret",
        "/%00",
        "/%zz",
    ] {
        assert!(url_path(path).is_none(), "{path}");
    }
    assert_eq!(
        url_path("/caf%C3%A9.txt").unwrap(),
        PathBuf::from("café.txt")
    );
}

#[test]
fn stopping_interrupts_a_stalled_download() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let _client = TcpStream::connect(listener.local_addr().unwrap()).unwrap();
    let (mut stream, _) = listener.accept().unwrap();
    stream.set_nonblocking(true).unwrap();
    let cancel = Arc::new(AtomicBool::new(false));
    let worker_cancel = cancel.clone();
    let worker = std::thread::spawn(move || -> io::Result<()> {
        let bytes = vec![0; 4 * 1024 * 1024];
        loop {
            write_response(&mut stream, &bytes, &worker_cancel)?;
        }
    });
    std::thread::sleep(Duration::from_millis(500));
    let stopped = Instant::now();
    cancel.store(true, Ordering::Relaxed);
    assert_eq!(
        worker.join().unwrap().unwrap_err().kind(),
        io::ErrorKind::Interrupted
    );
    assert!(stopped.elapsed() < Duration::from_secs(2));
}

/// Manual browser smoke test against the same server used by the IDE.
#[test]
#[ignore = "requires SCENEMAX_PREVIEW_ARCHIVE and a browser"]
fn exported_game_browser_preview() {
    let artifact = PathBuf::from(std::env::var_os("SCENEMAX_PREVIEW_ARCHIVE").unwrap());
    let status_file = PathBuf::from(std::env::var_os("SCENEMAX_PREVIEW_URL_FILE").unwrap());
    let cancel = AtomicBool::new(false);
    let state = Mutex::new(Status::default());
    std::thread::scope(|scope| {
        let server = scope.spawn(|| serve_archive(&artifact, &cancel, &state, false));
        let started = Instant::now();
        loop {
            if let Some(url) = state.lock().unwrap().url.clone() {
                fs::write(&status_file, url).unwrap();
                break;
            }
            assert!(
                !server.is_finished(),
                "Preview server exited before becoming ready"
            );
            assert!(started.elapsed() < Duration::from_secs(30));
            std::thread::sleep(Duration::from_millis(25));
        }
        // Removing the URL file lets browser automation stop the fixture early.
        while status_file.exists() && started.elapsed() < Duration::from_secs(180) {
            std::thread::sleep(Duration::from_millis(100));
        }
        cancel.store(true, Ordering::Relaxed);
        server.join().unwrap().unwrap();
    });
}
