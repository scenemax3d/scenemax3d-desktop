//! Fixed-upstream browser gateway. Terminate public WSS/TLS at a reverse proxy.
use std::{
    io,
    net::{SocketAddr, TcpListener, TcpStream, UdpSocket},
    sync::{
        Arc,
        atomic::{AtomicUsize, Ordering},
    },
    time::{Duration, Instant},
};
use tungstenite::{
    Error, Message,
    handshake::server::{ErrorResponse, Request, Response},
};
fn serve(
    stream: TcpStream,
    upstream: SocketAddr,
    origin: String,
) -> Result<(), Box<dyn std::error::Error>> {
    stream.set_nodelay(true)?;
    stream.set_read_timeout(Some(Duration::from_secs(10)))?;
    stream.set_write_timeout(Some(Duration::from_secs(10)))?;
    let config = tungstenite::protocol::WebSocketConfig::default()
        .max_message_size(Some(65507))
        .max_frame_size(Some(65507))
        .max_write_buffer_size(4 * 1024 * 1024);
    let mut socket = tungstenite::accept_hdr_with_config(
        stream,
        move |request: &Request, response: Response| {
            if request
                .headers()
                .get("origin")
                .and_then(|v| v.to_str().ok())
                != Some(origin.as_str())
            {
                let mut error = ErrorResponse::new(Some("Origin not allowed".into()));
                *error.status_mut() = tungstenite::http::StatusCode::FORBIDDEN;
                return Err(error);
            }
            Ok(response)
        },
        Some(config),
    )?;
    socket.get_ref().set_nonblocking(true)?;
    let udp = UdpSocket::bind(if upstream.is_ipv4() {
        "0.0.0.0:0"
    } else {
        "[::]:0"
    })?;
    udp.connect(upstream)?;
    udp.set_nonblocking(true)?;
    let mut buffer = [0; 65507];
    let mut last = Instant::now();
    let mut window = Instant::now();
    let mut packets = 0u32;
    loop {
        if window.elapsed() > Duration::from_secs(1) {
            window = Instant::now();
            packets = 0;
        }
        for _ in 0..128 {
            match socket.read() {
                Ok(Message::Binary(bytes)) => {
                    packets += 1;
                    if packets > 4096 {
                        return Err("Client packet rate exceeded".into());
                    }
                    udp.send(&bytes)?;
                    last = Instant::now();
                }
                Ok(Message::Close(_)) => return Ok(()),
                Ok(_) => {}
                Err(Error::Io(e)) if e.kind() == io::ErrorKind::WouldBlock => break,
                Err(e) => return Err(e.into()),
            }
        }
        for _ in 0..128 {
            match udp.recv(&mut buffer) {
                Ok(n) => match socket.send(Message::Binary(buffer[..n].to_vec().into())) {
                    Ok(()) => {}
                    Err(Error::Io(e)) if e.kind() == io::ErrorKind::WouldBlock => break,
                    Err(e) => return Err(e.into()),
                },
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => break,
                Err(e) => return Err(e.into()),
            }
        }
        match socket.flush() {
            Ok(()) => {}
            Err(Error::Io(e)) if e.kind() == io::ErrorKind::WouldBlock => {}
            Err(e) => return Err(e.into()),
        }
        if last.elapsed() > Duration::from_secs(120) {
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(2));
    }
}
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut args: Vec<_> = std::env::args().skip(1).collect();
    // IDE bundles can be launched directly without a terminal or working-directory assumptions.
    if args.is_empty() {
        let config = std::env::current_exe()?.with_file_name("gateway.json");
        let bytes = std::fs::read(&config)?;
        if bytes.len() > 16384 {
            return Err("Gateway configuration exceeds 16 KiB".into());
        }
        let value: serde_json::Value = serde_json::from_slice(&bytes)?;
        for key in ["listen", "upstream", "allowed_origin"] {
            args.push(
                value[key]
                    .as_str()
                    .ok_or("Invalid gateway configuration")?
                    .to_owned(),
            );
        }
    }
    if args.len() != 3 {
        eprintln!(
            "Usage: scenemax_web_gateway LISTEN_ADDRESS UDP_SERVER_ADDRESS ALLOWED_ORIGIN\nExample: scenemax_web_gateway 127.0.0.1:9002 127.0.0.1:9001 http://localhost:8080\nUse a TLS reverse proxy for public wss:// access. Each browser connection owns a distinct UDP socket."
        );
        std::process::exit(2);
    }
    let upstream: SocketAddr = args[1].parse()?;
    let listener = TcpListener::bind(&args[0])?;
    let active = Arc::new(AtomicUsize::new(0));
    println!(
        "Browser gateway {} -> UDP {}; allowed origin {}",
        args[0], upstream, args[2]
    );
    for stream in listener.incoming() {
        let stream = stream?;
        if active.load(Ordering::Relaxed) >= 64 {
            continue;
        }
        active.fetch_add(1, Ordering::Relaxed);
        let active = active.clone();
        let origin = args[2].clone();
        std::thread::spawn(move || {
            if let Err(error) = serve(stream, upstream, origin) {
                eprintln!("Browser connection ended: {error}");
            }
            active.fetch_sub(1, Ordering::Relaxed);
        });
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tungstenite::client::IntoClientRequest;
    #[test]
    fn rejects_unconfigured_browser_origins() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = std::thread::spawn(move || {
            let (stream, _) = listener.accept().unwrap();
            assert!(
                serve(
                    stream,
                    "127.0.0.1:9001".parse().unwrap(),
                    "http://localhost:8080".into()
                )
                .is_err()
            );
        });
        let mut request = format!("ws://{address}").into_client_request().unwrap();
        request
            .headers_mut()
            .insert("origin", "http://other.example".parse().unwrap());
        match tungstenite::connect(request) {
            Err(Error::Http(response)) => assert_eq!(response.status(), 403),
            _ => panic!("Unexpected origin was not rejected"),
        }
        server.join().unwrap();
    }
    #[test]
    fn binary_packets_round_trip_without_changing_the_udp_protocol() {
        let udp = UdpSocket::bind("127.0.0.1:0").unwrap();
        udp.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
        let upstream = udp.local_addr().unwrap();
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = std::thread::spawn(move || {
            let (stream, _) = listener.accept().unwrap();
            serve(stream, upstream, "http://localhost:8080".into()).map_err(|e| e.to_string())
        });
        let echo = std::thread::spawn(move || {
            let mut bytes = [0; 32];
            let (n, peer) = udp.recv_from(&mut bytes).unwrap();
            udp.send_to(&bytes[..n], peer).unwrap();
        });
        let mut request = format!("ws://{address}").into_client_request().unwrap();
        request
            .headers_mut()
            .insert("origin", "http://localhost:8080".parse().unwrap());
        let (mut socket, _) = tungstenite::connect(request).unwrap();
        socket
            .send(Message::Binary(vec![0, 1, 2, 255].into()))
            .unwrap();
        assert_eq!(
            socket.read().unwrap(),
            Message::Binary(vec![0, 1, 2, 255].into())
        );
        socket.close(None).unwrap();
        echo.join().unwrap();
        let _ = server.join().unwrap();
    }
}
