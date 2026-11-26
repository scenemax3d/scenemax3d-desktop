//! Local game pairing. Browser connections can register themselves as games;
//! only a native client presenting a game's one-time code can submit commands.
use anyhow::{Result, anyhow, bail, ensure};
use std::{
    collections::{HashMap, VecDeque},
    net::{TcpListener, TcpStream},
    sync::{
        Arc, Mutex,
        atomic::{AtomicUsize, Ordering},
    },
    thread,
    time::{Duration, Instant},
};
use tungstenite::{
    Message, WebSocket,
    handshake::server::{ErrorResponse, Request, Response},
    protocol::WebSocketConfig,
};

pub const ADDRESS: &str = "127.0.0.1:8766";
pub const MAX_CODE: usize = 256 * 1024;
const PAIR_LIFETIME: Duration = Duration::from_secs(300);
const IO_TIMEOUT: Duration = Duration::from_secs(30);
type Activity = Arc<Mutex<VecDeque<String>>>;

fn record(activity: &Activity, event: &str) {
    let mut entries = activity.lock().unwrap();
    if entries.len() == 24 { entries.pop_front(); }
    entries.push_back(event.to_owned());
}

type Socket = WebSocket<TcpStream>;
type Pending = Arc<Mutex<HashMap<String, (Instant, Socket)>>>;

fn read_text(socket: &mut Socket) -> Result<String> {
    loop {
        match socket.read()? {
            Message::Text(text) => return Ok(text.to_string()),
            Message::Ping(_) => socket.flush()?,
            Message::Pong(_) => (),
            _ => bail!("connection closed or unexpected message"),
        }
    }
}

fn connect(path: &str) -> Result<Socket> {
    let stream = TcpStream::connect_timeout(&ADDRESS.parse()?, Duration::from_secs(2))?;
    stream.set_read_timeout(Some(IO_TIMEOUT))?;
    stream.set_write_timeout(Some(IO_TIMEOUT))?;
    let config = WebSocketConfig::default()
        .max_message_size(Some(MAX_CODE))
        .max_frame_size(Some(MAX_CODE));
    Ok(tungstenite::client::client_with_config(
        format!("ws://{ADDRESS}/{path}"),
        stream,
        Some(config),
    )?
    .0)
}

pub fn serving() -> bool {
    connect("status")
        .and_then(|mut socket| read_text(&mut socket))
        .is_ok_and(|text| text == "SceneMax Game MCP")
}

/// Recent service activity, newest first. Available to native local clients only.
pub fn recent_activity() -> Result<String> {
    let mut socket = connect("activity")?;
    read_text(&mut socket)
}

/// Bind synchronously so Studio can report a port conflict before starting.
pub fn listen() -> Result<TcpListener> {
    Ok(TcpListener::bind(ADDRESS)?)
}

pub fn serve(listener: TcpListener) -> Result<()> {
    let pending = Pending::default();
    let activity = Activity::default();
    record(&activity, "Service started");
    let workers = Arc::new(AtomicUsize::new(0));
    listener.set_nonblocking(true)?;
    loop {
        pending
            .lock()
            .unwrap()
            .retain(|_, (time, _)| time.elapsed() < PAIR_LIFETIME);
        match listener.accept() {
            Ok((stream, _)) if workers.load(Ordering::Relaxed) < 64 => {
                let pending = pending.clone();
                let workers = workers.clone();
                let activity = activity.clone();
                workers.fetch_add(1, Ordering::Relaxed);
                thread::spawn(move || {
                    let _ = handle(stream, &pending, &activity);
                    workers.fetch_sub(1, Ordering::Relaxed);
                });
            }
            Ok(_) => (),
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(50))
            }
            Err(e) => return Err(e.into()),
        }
    }
}

fn handle(stream: TcpStream, pending: &Pending, activity: &Activity) -> Result<()> {
    // Winsock inherits the listener's nonblocking mode on accepted sockets.
    stream.set_nonblocking(false)?;
    stream.set_read_timeout(Some(IO_TIMEOUT))?;
    stream.set_write_timeout(Some(IO_TIMEOUT))?;
    let mut path = String::new();
    let config = WebSocketConfig::default()
        .max_message_size(Some(MAX_CODE))
        .max_frame_size(Some(MAX_CODE));
    let mut socket = tungstenite::accept_hdr_with_config(
        stream,
        |req: &Request, res: Response| {
            path = req.uri().path().into();
            let browser = req.headers().contains_key("origin");
            let host = req.headers().get("host").and_then(|h| h.to_str().ok());
            if host != Some(ADDRESS)
                || !(path == "/game" || !browser && matches!(path.as_str(), "/agent" | "/status" | "/activity"))
            {
                let mut err = ErrorResponse::new(None);
                *err.status_mut() = tungstenite::http::StatusCode::FORBIDDEN;
                return Err(err);
            }
            Ok(res)
        },
        Some(config),
    )
    .map_err(|err| anyhow!("{err}"))?;
    match path.as_str() {
        "/status" => socket.send("SceneMax Game MCP".into())?,
        "/activity" => {
            let entries = activity.lock().unwrap().iter().rev().cloned().collect::<Vec<_>>().join("\n");
            socket.send(Message::text(entries))?;
        }
        "/game" => {
            let mut bytes = [0; 12];
            getrandom::fill(&mut bytes).map_err(|e| anyhow!("{e}"))?;
            let code: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
            let mut games = pending.lock().unwrap();
            ensure!(games.len() < 128, "too many pending games");
            socket.send(Message::text(&code))?;
            games.insert(code, (Instant::now(), socket));
            record(activity, "Game requested a pairing code");
        }
        "/agent" => {
            let code = read_text(&mut socket)?;
            let entry = pending.lock().unwrap().remove(&code);
            let Some((_, mut game)) = entry.filter(|(t, _)| t.elapsed() < PAIR_LIFETIME) else {
                record(activity, "Pairing rejected");
                socket.send("403".into())?;
                return Ok(());
            };
            game.send(Message::Binary(b"paired".to_vec().into()))?;
            ensure!(read_text(&mut game)? == "ready", "game disconnected");
            socket.send("202".into())?;
            socket.get_ref().set_read_timeout(None)?;
            record(activity, "Agent paired with game");
            let result = relay(&mut socket, &mut game, activity);
            record(activity, "Agent session ended");
            let _ = game.close(None);
            result?;
        }
        _ => unreachable!(),
    }
    Ok(())
}

fn relay(socket: &mut Socket, game: &mut Socket, activity: &Activity) -> Result<()> {
            while let Ok(code) = read_text(socket) {
                game.send(Message::text(code))?;
                let reply = read_text(game)?;
                ensure!(
                    matches!(reply.as_str(), "202" | "400" | "503"),
                    "invalid game response"
                );
                record(activity, match reply.as_str() { "202" => "Command accepted (202)", "400" => "Command rejected (400)", _ => "Game unavailable (503)" });
                socket.send(Message::text(reply))?;
            }
    Ok(())
}

/// Native projector end. Pairing metadata is separate from raw source frames.
pub fn run_game(on_code: impl Fn(&str), mut execute: impl FnMut(String) -> bool) -> Result<()> {
    let mut socket = connect("game")?;
    on_code(&read_text(&mut socket)?);
    socket.get_ref().set_read_timeout(None)?;
    match socket.read()? {
        Message::Binary(data) if &data[..] == b"paired" => {
            on_code("");
            socket.send("ready".into())?
        }
        _ => bail!("pairing expired"),
    }
    while let Ok(code) = read_text(&mut socket) {
        socket.send(if execute(code) { "202" } else { "400" }.into())?;
    }
    Ok(())
}

pub struct Agent(Socket);
impl Agent {
    pub fn pair(code: &str) -> Result<Self> {
        let mut socket = connect("agent")?;
        socket.send(Message::text(code))?;
        ensure!(
            read_text(&mut socket)? == "202",
            "invalid or expired pairing code"
        );
        Ok(Self(socket))
    }
    pub fn submit(&mut self, source: &str) -> Result<String> {
        ensure!(source.len() <= MAX_CODE, "snippet exceeds 256 KiB");
        self.0.send(Message::text(source))?;
        read_text(&mut self.0)
    }
}
