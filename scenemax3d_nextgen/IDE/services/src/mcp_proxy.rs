//! Native newline-delimited stdio bridge to an already running local SceneMax IDE.
#![forbid(unsafe_code)]
use serde_json::{Value, json};
use std::{
    io::{self, BufRead, Read, Write},
    net::TcpStream,
    time::Duration,
};
fn forward(url: &str, token: Option<&str>, line: &str) -> Result<Option<Value>, String> {
    let authority = url
        .strip_prefix("http://")
        .and_then(|u| u.strip_suffix("/mcp"))
        .ok_or("Use http://127.0.0.1:PORT/mcp")?;
    let (host, port) = authority.rsplit_once(':').ok_or("Missing port")?;
    if !["127.0.0.1", "localhost"].contains(&host) {
        return Err("The proxy only connects to the local IDE".into());
    }
    let port = port.parse::<u16>().map_err(|e| e.to_string())?;
    let mut stream = TcpStream::connect_timeout(
        &std::net::SocketAddr::from(([127, 0, 0, 1], port)),
        Duration::from_secs(3),
    )
    .map_err(|e| e.to_string())?;
    stream
        .set_read_timeout(Some(Duration::from_secs(65)))
        .map_err(|e| e.to_string())?;
    stream
        .set_write_timeout(Some(Duration::from_secs(5)))
        .map_err(|e| e.to_string())?;
    let auth = token
        .map(|t| format!("Authorization: Bearer {t}\r\n"))
        .unwrap_or_default();
    if token.is_some_and(|t| t.chars().any(char::is_control)) {
        return Err("Invalid token".into());
    }
    write!(stream,"POST /mcp HTTP/1.1\r\nHost: {authority}\r\nContent-Type: application/json\r\nAccept: application/json, text/event-stream\r\nContent-Length: {}\r\n{auth}Connection: close\r\n\r\n{line}",line.len()).map_err(|e|e.to_string())?;
    let mut bytes = Vec::new();
    stream
        .take(32 * 1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    if bytes.len() > 32 * 1024 * 1024 {
        return Err("MCP response too large".into());
    }
    let text = String::from_utf8(bytes).map_err(|e| e.to_string())?;
    let (header, body) = text.split_once("\r\n\r\n").ok_or("Invalid HTTP response")?;
    if header.starts_with("HTTP/1.1 202") {
        return Ok(None);
    }
    if !header.starts_with("HTTP/1.1 200") {
        return Err(format!(
            "IDE HTTP error: {}",
            header.lines().next().unwrap_or("Unknown")
        ));
    }
    serde_json::from_str(body)
        .map(Some)
        .map_err(|e| e.to_string())
}
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<_> = std::env::args().skip(1).collect();
    if args.iter().any(|a| a == "--help") {
        eprintln!(
            "scenemax_mcp_proxy [http://127.0.0.1:8765/mcp]\nRequires a running IDE. Optional SCENEMAX_MCP_TOKEN environment variable."
        );
        return Ok(());
    }
    let url = args
        .first()
        .cloned()
        .or_else(|| std::env::var("SCENEMAX_MCP_URL").ok())
        .unwrap_or("http://127.0.0.1:8765/mcp".into());
    let token = std::env::var("SCENEMAX_MCP_TOKEN").ok();
    let mut stdin = io::stdin().lock();
    let mut stdout = io::stdout().lock();
    loop {
        let mut bytes = Vec::new();
        let n = (&mut stdin)
            .take(8 * 1024 * 1024 + 1)
            .read_until(b'\n', &mut bytes)?;
        if n == 0 {
            break;
        }
        if n > 8 * 1024 * 1024 {
            return Err("MCP input exceeds 8 MiB".into());
        }
        let line = std::str::from_utf8(&bytes)?.trim();
        if line.is_empty() {
            continue;
        }
        let request: Result<Value, _> = serde_json::from_str(line);
        let id = request.as_ref().ok().and_then(|r| r.get("id")).cloned();
        let response = match forward(&url, token.as_deref(), line) {
            Ok(v) => v,
            Err(e) => {
                eprintln!("SceneMax MCP: {e}");
                id.clone()
                    .map(|id| json!({"jsonrpc":"2.0","id":id,"error":{"code":-32000,"message":e}}))
            }
        };
        if let Some(response) = response {
            writeln!(stdout, "{response}")?;
            stdout.flush()?;
        }
    }
    Ok(())
}
