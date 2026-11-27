use std::{
    io::{BufRead, BufReader, Write},
    process::{Child, ChildStdin, ChildStdout, Command, Stdio},
    sync::mpsc,
    thread,
    time::Duration,
};

use serde_json::{Value, json};
use tungstenite::client::IntoClientRequest;

struct Process(Child);
impl Drop for Process {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}

struct Client {
    _process: Process,
    input: ChildStdin,
    output: BufReader<ChildStdout>,
}
impl Client {
    fn new() -> Self {
        let mut process = Command::new(env!("CARGO_BIN_EXE_scenemax_game_mcp"))
            .arg("stdio")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .spawn()
            .unwrap();
        let input = process.stdin.take().unwrap();
        let output = BufReader::new(process.stdout.take().unwrap());
        Self {
            _process: Process(process),
            input,
            output,
        }
    }

    fn request(&mut self, method: &str, params: Value) -> Value {
        writeln!(
            self.input,
            "{}",
            json!({"jsonrpc":"2.0","id":1,"method":method,"params":params})
        )
        .unwrap();
        self.input.flush().unwrap();
        let mut line = String::new();
        self.output.read_line(&mut line).unwrap();
        let reply: Value = serde_json::from_str(&line).unwrap();
        reply["result"].clone()
    }

    fn tool(&mut self, name: &str, args: Value) -> Value {
        self.request("tools/call", json!({"name":name,"arguments":args}))
    }
}

#[test]
fn local_game_pairing_is_single_use_and_commands_reach_only_the_paired_game() {
    let mut service = Process(
        Command::new(env!("CARGO_BIN_EXE_scenemax_game_mcp"))
            .arg("serve")
            .stderr(Stdio::piped())
            .spawn()
            .unwrap(),
    );
    let mut ready = String::new();
    BufReader::new(service.0.stderr.take().unwrap())
        .read_line(&mut ready)
        .unwrap();
    assert!(ready.contains("listening"), "{ready}");

    let mut forbidden = format!("ws://{}/agent", scenemax_game_mcp::ADDRESS)
        .into_client_request()
        .unwrap();
    forbidden
        .headers_mut()
        .insert("Origin", "https://example.org".parse().unwrap());
    assert!(tungstenite::connect(forbidden).is_err());

    let (pair, codes) = mpsc::channel();
    let (sent, received) = mpsc::channel();
    let game = thread::spawn(move || {
        scenemax_game_mcp::run_game(
            |code| {
                if !code.is_empty() {
                    pair.send(code.to_owned()).unwrap();
                }
            },
            |code| {
                let valid = code != "invalid";
                sent.send(code).unwrap();
                valid
            },
        )
        .unwrap()
    });
    let code = codes.recv_timeout(Duration::from_secs(5)).unwrap();
    let mut agent = Client::new();
    let init = agent.request("initialize", json!({"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}));
    assert_eq!(init["serverInfo"]["name"], "scenemax-game-mcp");
    assert_eq!(
        agent.request("tools/list", json!({}))["tools"]
            .as_array()
            .unwrap()
            .len(),
        3
    );
    assert_eq!(
        agent.tool("run_partial_code", json!({"code":"invalid"}))["isError"],
        true
    );
    assert_eq!(agent.tool("pair", json!({"code":code}))["isError"], false);
    let mut other = Client::new();
    assert_eq!(other.tool("pair", json!({"code":code}))["isError"], true);
    let (second_pair, second_codes) = mpsc::channel();
    let (second_sent, second_received) = mpsc::channel();
    let second_game = thread::spawn(move || {
        scenemax_game_mcp::run_game(
            |code| {
                if !code.is_empty() {
                    second_pair.send(code.to_owned()).unwrap();
                }
            },
            |source| {
                second_sent.send(source).unwrap();
                true
            },
        )
        .unwrap()
    });
    let second_code = second_codes.recv_timeout(Duration::from_secs(5)).unwrap();
    assert_ne!(code, second_code);
    assert_eq!(
        other.tool("pair", json!({"code":second_code}))["isError"],
        false
    );
    for (source, status) in [
        ("amount = 5\nwait 1 second\namount = 6", "202"),
        ("invalid", "400"),
    ] {
        assert_eq!(
            agent.tool("run_partial_code", json!({"code":source}))["content"][0]["text"],
            status
        );
        assert_eq!(
            received.recv_timeout(Duration::from_secs(1)).unwrap(),
            source
        );
    }
    assert_eq!(agent.tool("unpair", json!({}))["isError"], false);
    assert_eq!(
        agent.tool("run_partial_code", json!({"code":"invalid"}))["isError"],
        true
    );
    assert_eq!(
        other.tool("run_partial_code", json!({"code":"second_game_only"}))["isError"],
        false
    );
    assert_eq!(
        second_received
            .recv_timeout(Duration::from_secs(1))
            .unwrap(),
        "second_game_only"
    );
    assert_eq!(other.tool("unpair", json!({}))["isError"], false);
    second_game.join().unwrap();
    game.join().unwrap();
    let activity = scenemax_game_mcp::recent_activity().unwrap();
    for event in [
        "Service started",
        "Agent paired with game",
        "Command accepted (202)",
        "Command rejected (400)",
        "Agent session ended",
    ] {
        assert!(activity.contains(event), "{activity}");
    }
    assert!(!activity.contains(&code));
    assert!(!activity.contains("second_game_only"));
}
