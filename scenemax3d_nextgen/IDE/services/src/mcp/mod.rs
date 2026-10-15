//! Lightweight local MCP transport and bounded editor bridge.
pub mod disk;
mod http;
use serde_json::{Value, json};
use std::{
    net::TcpListener,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
        mpsc,
    },
    thread,
    time::{Duration, Instant},
};
/// A tool invocation awaiting execution by the IDE main thread.
pub struct Call {
    /// Discoverable tool name.
    pub name: String,
    /// Validated object arguments.
    pub arguments: Value,
    /// Deadline prevents late queued mutations after an HTTP timeout.
    pub deadline: Instant,
    /// Completion channel. Return only after the operation completes.
    pub reply: mpsc::SyncSender<Result<Value, String>>,
}
/// Local server lifecycle. Dropping it stops listening and joins its worker.
pub struct Server {
    /// Actual bound URL.
    pub endpoint: String,
    receiver: Mutex<mpsc::Receiver<Call>>,
    stop: Arc<AtomicBool>,
    worker: Option<thread::JoinHandle<()>>,
    /// Bounded metadata-only request log; no document contents or secrets.
    pub log: Arc<Mutex<std::collections::VecDeque<String>>>,
}
impl Server {
    /// Bind loopback only. Port zero asks the OS for an unused port (tests).
    pub fn start(port: u16, token: Option<String>) -> std::io::Result<Self> {
        let listener = TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, port))?;
        listener.set_nonblocking(true)?;
        let endpoint = format!("http://127.0.0.1:{}/mcp", listener.local_addr()?.port());
        let (tx, rx) = mpsc::sync_channel(8);
        let stop = Arc::new(AtomicBool::new(false));
        let stopped = stop.clone();
        let log = Arc::new(Mutex::new(std::collections::VecDeque::new()));
        let records = log.clone();
        let url = endpoint.clone();
        let worker = thread::Builder::new()
            .name("scenemax-mcp".into())
            .spawn(move || {
                // Four bounded connections keep status/discovery responsive during a tool call.
                let mut workers = Vec::<thread::JoinHandle<()>>::new();
                while !stopped.load(Ordering::Relaxed) {
                    let mut index = 0;
                    while index < workers.len() {
                        if workers[index].is_finished() {
                            let _ = workers.swap_remove(index).join();
                        } else {
                            index += 1;
                        }
                    }
                    match listener.accept() {
                        Ok((stream, _)) => {
                            if workers.len() >= 4 {
                                drop(stream);
                                continue;
                            }
                            let (tx, token, url, stop, log) = (
                                tx.clone(),
                                token.clone(),
                                url.clone(),
                                stopped.clone(),
                                records.clone(),
                            );
                            workers.push(thread::spawn(move || {
                                http::serve(stream, &url, token.as_deref(), &tx, &stop, &log)
                            }));
                        }
                        Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                            thread::sleep(Duration::from_millis(15))
                        }
                        Err(_) => break,
                    }
                }
                for worker in workers {
                    let _ = worker.join();
                }
            })?;
        Ok(Self {
            endpoint,
            receiver: Mutex::new(rx),
            stop,
            worker: Some(worker),
            log,
        })
    }
    /// Nonblocking dequeue, called by the IDE orchestration system.
    pub fn poll(&self) -> Option<Call> {
        self.receiver.lock().ok()?.try_recv().ok()
    }
}
impl Drop for Server {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}
fn error(id: Value, code: i32, message: &str) -> Value {
    json!({"jsonrpc":"2.0","id":id,"error":{"code":code,"message":message}})
}
fn result(id: Value, value: Value) -> Value {
    json!({"jsonrpc":"2.0","id":id,"result":value})
}
fn rpc(request: Value, tx: &mpsc::SyncSender<Call>, stop: &AtomicBool) -> Option<Value> {
    let id = request.get("id").cloned().unwrap_or(Value::Null);
    if !request.is_object()
        || request["jsonrpc"] != "2.0"
        || !request["method"].is_string()
        || (!id.is_null() && !id.is_string() && !id.is_number())
    {
        return Some(error(id, -32600, "Invalid JSON-RPC request"));
    }
    let method = request["method"].as_str().unwrap_or("");
    request.get("id")?;
    Some(match method {
        "initialize" => {
            let version = request["params"]["protocolVersion"].as_str().unwrap_or("");
            let version = if ["2025-03-26", "2025-06-18", "2025-11-25"].contains(&version) {
                version
            } else {
                "2025-11-25"
            };
            result(
                id,
                json!({"protocolVersion":version,"serverInfo":{"name":"SceneMax Bevy IDE","version":env!("CARGO_PKG_VERSION")},"capabilities":{"tools":{"listChanged":false}},"instructions":"Use project.get_context first. Paths are project-relative. Read before editing; supply expected_revision for dirty buffers. Edits are undoable and saved through the IDE. Read tool schemas for supported parameters."}),
            )
        }
        "ping" => result(id, json!({})),
        "tools/list" => result(id, json!({"tools":scenemax_ide_core::automation::tools()})),
        "tools/call" => {
            let name = request["params"]["name"].as_str().unwrap_or("");
            let args = request["params"]
                .get("arguments")
                .cloned()
                .unwrap_or(json!({}));
            if let Err(e) = scenemax_ide_core::automation::validate_arguments(name, &args) {
                return Some(error(id, -32602, &e));
            }
            let (reply, receiver) = mpsc::sync_channel(1);
            let deadline = Instant::now() + Duration::from_secs(60);
            if tx
                .try_send(Call {
                    name: name.into(),
                    arguments: args,
                    deadline,
                    reply,
                })
                .is_err()
            {
                return Some(error(id, -32000, "IDE automation queue is full"));
            }
            let value = loop {
                match receiver.recv_timeout(Duration::from_millis(100)) {
                    Ok(value) => break value,
                    Err(mpsc::RecvTimeoutError::Disconnected) => {
                        break Err("IDE disconnected".into());
                    }
                    Err(_) if stop.load(Ordering::Relaxed) => break Err("IDE shutting down".into()),
                    Err(_) if Instant::now() >= deadline => {
                        break Err(
                            "Tool timed out; inspect document/status before retrying a mutation"
                                .into(),
                        );
                    }
                    Err(_) => {}
                }
            };
            match value {
                Ok(mut v) => {
                    let image = v.as_object_mut().and_then(|o| o.remove("_mcp_image"));
                    let mut content = vec![json!({"type":"text","text":v.to_string()})];
                    if let Some(data) = image {
                        content.push(json!({"type":"image","mimeType":"image/png","data":data}));
                    }
                    result(
                        id,
                        json!({"content":content,"structuredContent":v,"isError":false}),
                    )
                }
                Err(e) => result(
                    id,
                    json!({"content":[{"type":"text","text":e}],"isError":true}),
                ),
            }
        }
        _ => error(id, -32601, "Method not found"),
    })
}
#[cfg(test)]
mod tests;

/// Encode a captured RGBA render target as an MCP PNG content block.
pub fn encode_image(width: u32, height: u32, bytes: Vec<u8>) -> Result<Value, String> {
    use base64::Engine;
    let image = image::RgbaImage::from_raw(width, height, bytes).ok_or("Invalid captured image")?;
    let image = image::DynamicImage::ImageRgba8(image);
    let image = if width > 1600 || height > 1200 {
        image.resize(1600, 1200, image::imageops::FilterType::Triangle)
    } else {
        image
    };
    let mut png = std::io::Cursor::new(Vec::new());
    image
        .write_to(&mut png, image::ImageFormat::Png)
        .map_err(|e| e.to_string())?;
    Ok(
        json!({"width":image.width(),"height":image.height(),"_mcp_image":base64::engine::general_purpose::STANDARD.encode(png.into_inner())}),
    )
}
