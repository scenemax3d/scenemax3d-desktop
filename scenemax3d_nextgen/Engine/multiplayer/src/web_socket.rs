//! Browser datagram framing over a configured WebSocket-to-UDP gateway.
use std::{
    cell::RefCell,
    collections::{BTreeMap, VecDeque},
    io,
    net::SocketAddr,
};
use wasm_bindgen::{JsCast, closure::Closure};
use web_sys::{MessageEvent, WebSocket};
thread_local! {
    static URL: RefCell<String> = const { RefCell::new(String::new()) };
    static SOCKETS: RefCell<BTreeMap<u32, Socket>> = const { RefCell::new(BTreeMap::new()) };
    static NEXT: std::cell::Cell<u32> = const { std::cell::Cell::new(1) };
}
struct Socket {
    socket: WebSocket,
    inbound: VecDeque<Vec<u8>>,
    pending: VecDeque<Vec<u8>>,
    _message: Closure<dyn FnMut(MessageEvent)>,
}
/// A browser connection label, independent of native process IDs.
pub fn client_id() -> String {
    format!(
        "{:.0}-{:08x}",
        js_sys::Date::now(),
        (js_sys::Math::random() * u32::MAX as f64) as u32
    )
}
/// Set the server-managed browser gateway URL before creating a connection.
pub fn configure(url: &str) -> Result<(), String> {
    if !url.starts_with("ws://") && !url.starts_with("wss://") {
        return Err("Multiplayer needs a ws:// or wss:// gateway URL in network.json".into());
    }
    URL.with(|u| *u.borrow_mut() = url.into());
    Ok(())
}
/// Sendable handle; JavaScript objects stay on the browser's owning thread.
pub struct UdpSocket(u32);
impl UdpSocket {
    pub fn bind(_: &str) -> io::Result<Self> {
        let url = URL.with(|u| u.borrow().clone());
        let socket = WebSocket::new(&url)
            .map_err(|_| io::Error::other("Cannot open browser multiplayer gateway"))?;
        socket.set_binary_type(web_sys::BinaryType::Arraybuffer);
        let id = NEXT.with(|n| {
            let id = n.get();
            n.set(id + 1);
            id
        });
        let callback = Closure::wrap(Box::new(move |event: MessageEvent| {
            if let Ok(bytes) = event.data().dyn_into::<js_sys::ArrayBuffer>() {
                let bytes = js_sys::Uint8Array::new(&bytes).to_vec();
                SOCKETS.with(|s| {
                    if let Some(s) = s.borrow_mut().get_mut(&id) {
                        if bytes.len() <= 65507 && s.inbound.len() < 4096 {
                            s.inbound.push_back(bytes);
                        }
                    }
                });
            }
        }) as Box<dyn FnMut(MessageEvent)>);
        socket.set_onmessage(Some(callback.as_ref().unchecked_ref()));
        SOCKETS.with(|s| {
            s.borrow_mut().insert(
                id,
                Socket {
                    socket,
                    inbound: VecDeque::new(),
                    pending: VecDeque::new(),
                    _message: callback,
                },
            )
        });
        Ok(Self(id))
    }
    pub fn connect(&self, _: SocketAddr) -> io::Result<()> {
        Ok(())
    }
    pub fn set_nonblocking(&self, _: bool) -> io::Result<()> {
        Ok(())
    }
    pub fn send(&self, bytes: &[u8]) -> io::Result<usize> {
        SOCKETS.with(|s| {
            let mut sockets = s.borrow_mut();
            let s = sockets
                .get_mut(&self.0)
                .ok_or_else(|| io::Error::other("Socket closed"))?;
            match s.socket.ready_state() {
                WebSocket::CONNECTING if s.pending.len() < 128 => {
                    s.pending.push_back(bytes.to_vec());
                    Ok(bytes.len())
                }
                WebSocket::OPEN if s.socket.buffered_amount() < 4 * 1024 * 1024 => {
                    s.socket
                        .send_with_u8_array(bytes)
                        .map_err(|_| io::Error::other("WebSocket send failed"))?;
                    Ok(bytes.len())
                }
                WebSocket::OPEN | WebSocket::CONNECTING => Err(io::ErrorKind::WouldBlock.into()),
                _ => Err(io::ErrorKind::ConnectionAborted.into()),
            }
        })
    }
    pub fn recv(&self, buffer: &mut [u8]) -> io::Result<usize> {
        SOCKETS.with(|s| {
            let mut sockets = s.borrow_mut();
            let s = sockets
                .get_mut(&self.0)
                .ok_or_else(|| io::Error::other("Socket closed"))?;
            if s.socket.ready_state() == WebSocket::OPEN {
                while let Some(bytes) = s.pending.pop_front() {
                    s.socket
                        .send_with_u8_array(&bytes)
                        .map_err(|_| io::Error::other("WebSocket send failed"))?;
                }
            } else if s.socket.ready_state() >= WebSocket::CLOSING {
                return Err(io::ErrorKind::ConnectionAborted.into());
            }
            let bytes = s.inbound.pop_front().ok_or(io::ErrorKind::WouldBlock)?;
            if bytes.len() > buffer.len() {
                return Err(io::ErrorKind::InvalidData.into());
            }
            buffer[..bytes.len()].copy_from_slice(&bytes);
            Ok(bytes.len())
        })
    }
}
impl Drop for UdpSocket {
    fn drop(&mut self) {
        SOCKETS.with(|s| {
            if let Some(s) = s.borrow_mut().remove(&self.0) {
                s.socket.set_onmessage(None);
                let _ = s.socket.close();
            }
        });
    }
}
