//! Run from a disposable runtime-only installation with an empty tool PATH.
use super::*;
use crate::deployment;
use scenemax_ide_core::deployment::Target;
use scenemax_multiplayer::{
    client::{Client, password_hash},
    protocol::{self, Incoming, Login, Outgoing},
};
use std::{
    net::{TcpListener, UdpSocket},
    time::{Duration, Instant},
};
use tungstenite::{Message, client::IntoClientRequest};

#[test]
#[ignore = "requires the packaged installation; run test-studio-multiplayer.ps1"]
fn installed_multiplayer_exports_and_transports_without_toolchains() {
    let installation = crate::workspace_state::installation_root()
        .expect("run test executable beside installation marker");
    let workspace = deployment::workspace();
    assert_eq!(workspace, installation.join("scenemax3d_nextgen"));
    assert!(!workspace.join("Cargo.toml").exists());
    assert!(!workspace.join("Engine").exists());
    for tool in ["cargo", "rustc", "rustup", "zig", "cl", "link", "cmake"] {
        assert!(
            std::process::Command::new(tool)
                .arg("--version")
                .output()
                .is_err(),
            "{tool} must be unavailable"
        );
    }
    let project = installation.join("acceptance-project");
    fs::create_dir_all(project.join("scripts")).unwrap();
    fs::create_dir_all(project.join("resources")).unwrap();
    fs::write(
        project.join("scripts/main"),
        "wait for network.ready == 1\nsys.print \"MULTIPLAYER_EXPORT_READY\"\n",
    )
    .unwrap();
    let mut snapshot = load(&project).unwrap();
    let udp = UdpSocket::bind("127.0.0.1:0").unwrap();
    let tcp = TcpListener::bind("127.0.0.1:0").unwrap();
    snapshot.settings.enabled = true;
    snapshot.settings.port = udp.local_addr().unwrap().port();
    snapshot.settings.browser_gateway.enabled = true;
    let web_port = tcp.local_addr().unwrap().port();
    snapshot.settings.browser_gateway.listen = format!("127.0.0.1:{web_port}");
    snapshot.settings.browser_gateway.websocket_url = format!("ws://127.0.0.1:{web_port}");
    drop((udp, tcp));
    let report = run(
        &project,
        Operation::BuildWithPassword(snapshot, String::new()),
        &AtomicBool::new(false),
    )
    .unwrap();
    assert!(report.message.contains("prebuilt"), "{}", report.message);
    let settings = &report.snapshot.settings;
    let mut local = local_server::LocalServer::default();
    local
        .start_with_gateway(
            report.server.as_ref().unwrap(),
            settings.port,
            Some((report.gateway.as_ref().unwrap(), &settings.browser_gateway)),
        )
        .unwrap();
    let deadline = Instant::now() + Duration::from_secs(10);
    while !local.ready() {
        let status = local.poll();
        assert!(local.active() && Instant::now() < deadline, "{status:?}");
        thread::sleep(Duration::from_millis(20));
    }
    let login = Login {
        password_hash: password_hash(""),
        project_guid: settings.project_guid.clone(),
        create_session: false,
        session_id: settings.session_id,
        session_name: settings.session_name.clone(),
        scene: "main".into(),
        player: "acceptance".into(),
    };
    let mut native =
        Client::connect(([127, 0, 0, 1], settings.port).into(), login.clone()).unwrap();
    let deadline = Instant::now() + Duration::from_secs(5);
    loop {
        if native
            .poll()
            .unwrap()
            .iter()
            .any(|e| matches!(e.message, Incoming::Ready))
        {
            break;
        }
        assert!(Instant::now() < deadline, "native UDP login failed");
        thread::sleep(Duration::from_millis(10));
    }
    let mut request = settings
        .browser_gateway
        .websocket_url
        .clone()
        .into_client_request()
        .unwrap();
    request.headers_mut().insert(
        "Origin",
        settings.browser_gateway.allowed_origin.parse().unwrap(),
    );
    let stream = std::net::TcpStream::connect(("127.0.0.1", web_port)).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .unwrap();
    let (mut browser, _) = tungstenite::client(request, stream).unwrap();
    browser
        .send(Message::Binary(
            protocol::encode(0, &Outgoing::Login(login)).unwrap().into(),
        ))
        .unwrap();
    let mut browser_id = 0;
    let mut ready = false;
    let deadline = Instant::now() + Duration::from_secs(10);
    while browser_id == 0 || !ready {
        assert!(Instant::now() < deadline, "browser login timed out");
        if let Message::Binary(packet) = browser.read().unwrap() {
            match protocol::decode(&packet, browser_id).unwrap().1 {
                Incoming::Accepted { client, .. } => browser_id = client,
                Incoming::Ready => ready = true,
                Incoming::Rejected => panic!("browser login rejected"),
                _ => {}
            }
        }
    }
    native
        .send(
            None,
            Outgoing::Variable {
                name: "native_to_browser".into(),
                initial: false,
                value: serde_json::json!(42),
            },
        )
        .unwrap();
    let deadline = Instant::now() + Duration::from_secs(10);
    loop {
        native.poll().unwrap();
        assert!(
            Instant::now() < deadline,
            "native-to-browser replication failed"
        );
        if let Message::Binary(packet) = browser.read().unwrap()
            && matches!(protocol::decode(&packet, browser_id).unwrap().1, Incoming::Variable { name, value, .. } if name == "native_to_browser" && value == serde_json::json!(42))
        {
            break;
        }
    }
    browser
        .send(Message::Binary(
            protocol::encode(
                browser_id,
                &Outgoing::Variable {
                    name: "browser_to_native".into(),
                    initial: false,
                    value: serde_json::json!(73),
                },
            )
            .unwrap()
            .into(),
        ))
        .unwrap();
    let deadline = Instant::now() + Duration::from_secs(5);
    loop {
        if native.poll().unwrap().iter().any(|e| matches!(&e.message, Incoming::Variable { name, value, .. } if name == "browser_to_native" && value == &serde_json::json!(73))) { break; }
        assert!(
            Instant::now() < deadline,
            "browser-to-native replication failed"
        );
        thread::sleep(Duration::from_millis(10));
    }
    println!(
        "PASS: packaged UDP server and WebSocket gateway log in and replicate both directions"
    );
    let mut export = scenemax_ide_core::deployment::Settings::default();
    export.name = "multiplayer-acceptance".into();
    export.output = project.join("exports").to_string_lossy().into_owned();
    for p in &mut export.platforms {
        p.selected = matches!(p.target, Target::Windows | Target::Web);
    }
    let mut builder = deployment::Builder::default();
    builder
        .start(deployment::Operation::Build(deployment::Request {
            multiplayer_override: None,
            project: project.clone(),
            entry: project.join("scripts/main"),
            workspace: workspace.clone(),
            settings: export,
        }))
        .unwrap();
    let deadline = Instant::now() + Duration::from_secs(90);
    while builder.busy() {
        assert!(Instant::now() < deadline);
        thread::sleep(Duration::from_millis(50));
    }
    let result = builder.report().unwrap();
    assert!(result.success, "{} {:?}", result.status, result.logs);
    assert_eq!(result.artifacts.len(), 2);
    for artifact in &result.artifacts {
        let mut archive = zip::ZipArchive::new(fs::File::open(artifact).unwrap()).unwrap();
        let mut source = String::new();
        let script = if artifact.extension().unwrap() == "exe" {
            "scripts/main"
        } else {
            "game/scripts/main"
        };
        archive
            .by_name(script)
            .unwrap()
            .read_to_string(&mut source)
            .unwrap();
        assert!(source.contains(&format!("//$[multiplayer_port]={};", settings.port)));
        assert!(source.contains(&settings.project_guid));
        if artifact.extension().unwrap() == "zip" {
            let config: serde_json::Value =
                serde_json::from_reader(archive.by_name("network.json").unwrap()).unwrap();
            assert_eq!(
                config["websocket_url"],
                settings.browser_gateway.websocket_url
            );
            let manifest: serde_json::Value =
                serde_json::from_reader(archive.by_name("game-manifest.json").unwrap()).unwrap();
            assert_eq!(manifest["multiplayer"], true);
            assert!(archive.by_name("runtime_bg.wasm").is_ok());
        }
        println!("PASS: multiplayer export {}", artifact.display());
    }
    let log = fs::read_to_string(result.log_path.unwrap()).unwrap();
    assert!(log.contains("no compilation required"));
    assert!(!workspace.join("target").exists());
    browser.close(None).ok();
    local.stop();
}
