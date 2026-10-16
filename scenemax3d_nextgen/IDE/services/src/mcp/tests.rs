use super::*;
use std::io::{Read, Write};
fn post(server: &Server, body: &str, extra: &str) -> String {
    let authority = server
        .endpoint
        .trim_start_matches("http://")
        .trim_end_matches("/mcp");
    let mut stream = std::net::TcpStream::connect(authority).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .unwrap();
    write!(stream,"POST /mcp HTTP/1.1\r\nHost: {authority}\r\nContent-Type: application/json\r\nAccept: application/json, text/event-stream\r\nContent-Length: {}\r\n{extra}\r\n{body}",body.len()).unwrap();
    let mut response = String::new();
    stream.read_to_string(&mut response).unwrap();
    response
}
#[test]
fn discovery_notifications_and_transport_rejections() {
    let server = Server::start(0, None).unwrap();
    let response = post(
        &server,
        r#"{"jsonrpc":"2.0","id":"init","method":"initialize","params":{"protocolVersion":"2025-03-26"}}"#,
        "",
    );
    assert!(response.contains("2025-03-26"));
    assert!(response.contains("SceneMax Bevy IDE"));
    let response = post(
        &server,
        r#"{"jsonrpc":"2.0","id":2,"method":"tools/list"}"#,
        "",
    );
    assert!(response.contains("designer.add_entities_batch"));
    assert!(response.contains("inputSchema"));
    assert!(
        post(
            &server,
            r#"{"jsonrpc":"2.0","method":"notifications/initialized"}"#,
            ""
        )
        .starts_with("HTTP/1.1 202")
    );
    assert!(post(&server, "{", "").contains("-32700"));
    assert!(post(&server, "[]", "").contains("-32600"));
    assert!(post(&server,r#"{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"file.modify","arguments":{"unexpected":1}}}"#,"").contains("-32602"));
    assert!(
        post(&server, "{}", "Origin: https://unrelated.example\r\n").starts_with("HTTP/1.1 403")
    );
    assert!(post(&server, "{}", "MCP-Protocol-Version: bad\r\n").starts_with("HTTP/1.1 400"));
}
#[test]
fn authenticated_tool_completion_is_structured_and_errors_are_tools_errors() {
    let server = Arc::new(Server::start(0, Some("test-token".into())).unwrap());
    assert!(post(&server, "{}", "").starts_with("HTTP/1.1 403"));
    let client = server.clone();
    let request = thread::spawn(move || {
        post(
            &client,
            r#"{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"project.get_context","arguments":{}}}"#,
            "Authorization: Bearer test-token\r\n",
        )
    });
    let start = Instant::now();
    let call = loop {
        if let Some(call) = server.poll() {
            break call;
        }
        assert!(start.elapsed() < Duration::from_secs(3));
        thread::sleep(Duration::from_millis(5));
    };
    assert_eq!(call.name, "project.get_context");
    call.reply.send(Ok(json!({"root":"fixture"}))).unwrap();
    let response = request.join().unwrap();
    assert!(response.contains("structuredContent"));
    assert!(response.contains("fixture"));
    let (tx, rx) = mpsc::sync_channel(1);
    let thread = thread::spawn(move || {
        let call: Call = rx.recv().unwrap();
        call.reply.send(Err("Conflict".into())).unwrap();
    });
    let response=rpc(json!({"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"editor.save_active"}}),&tx,&AtomicBool::new(false)).unwrap();
    thread.join().unwrap();
    assert_eq!(response["result"]["isError"], true);
}
#[test]
fn accepts_body_delivered_after_headers_on_windows() {
    let server = Server::start(0, None).unwrap();
    let authority = server
        .endpoint
        .trim_start_matches("http://")
        .trim_end_matches("/mcp");
    let mut stream = std::net::TcpStream::connect(authority).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(3)))
        .unwrap();
    let body = r#"{"jsonrpc":"2.0","id":1,"method":"ping"}"#;
    write!(stream,"POST /mcp HTTP/1.1\r\nHost: {authority}\r\nContent-Type: application/json\r\nContent-Length: {}\r\n\r\n",body.len()).unwrap();
    stream.flush().unwrap();
    thread::sleep(Duration::from_millis(100));
    stream.write_all(body.as_bytes()).unwrap();
    let mut result = String::new();
    stream.read_to_string(&mut result).unwrap();
    assert!(result.starts_with("HTTP/1.1 200"));
    assert!(result.contains("\"result\":{}"));
}

#[test]
fn shutdown_releases_port_for_ide_restart() {
    let server = Server::start(0, None).unwrap();
    let port = server
        .endpoint
        .trim_end_matches("/mcp")
        .rsplit(':')
        .next()
        .unwrap()
        .parse::<u16>()
        .unwrap();
    drop(server);
    let replacement = Server::start(port, None).unwrap();
    assert!(
        post(
            &replacement,
            r#"{"jsonrpc":"2.0","id":1,"method":"ping"}"#,
            ""
        )
        .contains("\"result\":{}")
    );
}
