use super::*;
use std::{
    io::{Read, Write},
    net::TcpStream,
};
const MAX_BODY: usize = 8 * 1024 * 1024;
fn response(stream: &mut TcpStream, status: u16, body: Option<Value>) {
    let body = body.map(|v| v.to_string()).unwrap_or_default();
    let reason = match status {
        200 => "OK",
        202 => "Accepted",
        400 => "Bad Request",
        403 => "Forbidden",
        404 => "Not Found",
        405 => "Method Not Allowed",
        413 => "Payload Too Large",
        415 => "Unsupported Media Type",
        _ => "Error",
    };
    let _ = write!(
        stream,
        "HTTP/1.1 {status} {reason}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\nAllow: POST\r\n\r\n{body}",
        body.len()
    );
    let _ = stream.shutdown(std::net::Shutdown::Write);
    // Drain already queued request bytes so Windows does not reset a rejected POST.
    let _ = stream.set_nonblocking(true);
    let mut discarded = 0;
    let mut chunk = [0; 8192];
    while discarded < MAX_BODY {
        match stream.read(&mut chunk) {
            Ok(0) | Err(_) => break,
            Ok(n) => discarded += n,
        }
    }
}
pub(super) fn serve(
    mut stream: TcpStream,
    endpoint: &str,
    token: Option<&str>,
    tx: &mpsc::SyncSender<Call>,
    stop: &AtomicBool,
    log: &Mutex<std::collections::VecDeque<String>>,
) {
    // Windows accepted sockets may inherit the nonblocking listener mode.
    let _ = stream.set_nonblocking(false);
    let _ = stream.set_read_timeout(Some(Duration::from_secs(2)));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(2)));
    let start = Instant::now();
    let mut bytes = Vec::new();
    let end = loop {
        let mut byte = [0];
        if stop.load(Ordering::Relaxed)
            || start.elapsed() > Duration::from_secs(5)
            || bytes.len() >= 16384
            || stream.read_exact(&mut byte).is_err()
        {
            response(&mut stream, 400, None);
            return;
        }
        bytes.push(byte[0]);
        if bytes.ends_with(b"\r\n\r\n") {
            break bytes.len();
        }
    };
    let Ok(header) = std::str::from_utf8(&bytes[..end]) else {
        response(&mut stream, 400, None);
        return;
    };
    let mut lines = header.split("\r\n");
    let first: Vec<_> = lines.next().unwrap_or("").split_whitespace().collect();
    if first.len() != 3 || first[2] != "HTTP/1.1" {
        response(&mut stream, 400, None);
        return;
    }
    let mut headers = std::collections::BTreeMap::new();
    for line in lines.filter(|line| !line.is_empty()) {
        let Some((name, value)) = line.split_once(':') else {
            response(&mut stream, 400, None);
            return;
        };
        if headers
            .insert(name.to_ascii_lowercase(), value.trim())
            .is_some()
        {
            response(&mut stream, 400, None);
            return;
        }
    }
    let authority = endpoint
        .trim_start_matches("http://")
        .trim_end_matches("/mcp");
    let localhost = authority.replace("127.0.0.1", "localhost");
    let host = headers.get("host").copied().unwrap_or("");
    let origin = headers.get("origin").copied();
    if (host != authority && host != localhost)
        || origin.is_some_and(|o| {
            o != format!("http://{authority}") && o != format!("http://{localhost}")
        })
        || token.is_some_and(|t| {
            headers.get("authorization").copied() != Some(format!("Bearer {t}").as_str())
        })
    {
        response(&mut stream, 403, None);
        return;
    }
    if first[0] == "GET" && first[1] == "/mcp/status" {
        response(
            &mut stream,
            200,
            Some(
                json!({"running":true,"endpoint":endpoint,"protocol":"MCP Streamable HTTP","tools":scenemax_ide_core::automation::tools().len()}),
            ),
        );
        return;
    }
    if first[1] != "/mcp" {
        response(&mut stream, 404, None);
        return;
    }
    if first[0] != "POST" {
        response(&mut stream, 405, None);
        return;
    }
    if headers.contains_key("transfer-encoding") {
        response(&mut stream, 400, None);
        return;
    }
    if headers
        .get("content-type")
        .is_none_or(|v| v.split(';').next() != Some("application/json"))
    {
        response(&mut stream, 415, None);
        return;
    }
    if headers
        .get("mcp-protocol-version")
        .is_some_and(|v| !["2025-03-26", "2025-06-18", "2025-11-25"].contains(v))
    {
        response(&mut stream, 400, None);
        return;
    }
    let Some(len) = headers
        .get("content-length")
        .and_then(|v| v.parse::<usize>().ok())
    else {
        response(&mut stream, 400, None);
        return;
    };
    if len > MAX_BODY {
        response(&mut stream, 413, None);
        return;
    }
    let mut body = Vec::with_capacity(len);
    while body.len() < len {
        let mut chunk = [0; 8192];
        let n = (len - body.len()).min(chunk.len());
        match stream.read(&mut chunk[..n]) {
            Ok(0) | Err(_) => {
                response(&mut stream, 400, None);
                return;
            }
            Ok(n) => body.extend_from_slice(&chunk[..n]),
        }
        if stop.load(Ordering::Relaxed) || start.elapsed() > Duration::from_secs(10) {
            response(&mut stream, 400, None);
            return;
        }
    }
    let request = serde_json::from_slice::<Value>(&body);
    let summary = request
        .as_ref()
        .map(|r| {
            format!(
                "{} {}",
                r["method"]
                    .as_str()
                    .unwrap_or("?")
                    .chars()
                    .take(100)
                    .collect::<String>(),
                r["params"]["name"]
                    .as_str()
                    .unwrap_or("")
                    .chars()
                    .take(100)
                    .collect::<String>()
            )
        })
        .unwrap_or("parse error".into());
    let value = match request {
        Ok(r) => rpc(r, tx, stop),
        Err(_) => Some(error(Value::Null, -32700, "Invalid JSON")),
    };
    if let Ok(mut entries) = log.lock() {
        entries.push_back(format!(
            "{summary} · {} ms · {}",
            start.elapsed().as_millis(),
            if value
                .as_ref()
                .is_some_and(|v| v.get("error").is_some() || v["result"]["isError"] == true)
            {
                "error"
            } else {
                "ok"
            }
        ));
        while entries.len() > 200 {
            entries.pop_front();
        }
    }
    response(&mut stream, if value.is_some() { 200 } else { 202 }, value);
}
