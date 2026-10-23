//! Nonblocking, bounded UDP client. Socket ownership ends with this value.
use web_time::Instant;
use crate::protocol::{self, Incoming, Login, Outgoing, Session, Spawn};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, VecDeque},
    io,
    net::SocketAddr,
    time::Duration,
};
mod ends;
mod health;
#[cfg(not(target_arch = "wasm32"))]
use std::net::UdpSocket;
#[cfg(target_arch = "wasm32")]
#[path = "web_socket.rs"]
pub mod web_socket;
#[cfg(target_arch = "wasm32")]
use web_socket::UdpSocket;

const MAX_PENDING: usize = 4096;
const MAX_ENTITIES: usize = 2048;
const POLL_BUDGET: usize = 128;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error(transparent)]
    Io(#[from] io::Error),
    #[error(transparent)]
    Protocol(#[from] protocol::Error),
    #[error("multiplayer capacity exhausted: {0}")]
    Capacity(&'static str),
    #[error("multiplayer connection is closed")]
    Closed,
}

impl Error {
    /// Temporary local pressure can be retried without replacing the connection.
    pub fn is_retryable(&self) -> bool {
        matches!(self, Self::Capacity(_))
            || matches!(self, Self::Io(e)
            if matches!(e.kind(), io::ErrorKind::WouldBlock | io::ErrorKind::Interrupted))
    }
}

#[derive(Clone)]
struct Queued {
    request: Option<u32>,
    message: Outgoing,
    queued_at: Instant,
}
impl Queued {
    fn wire(&self, now: Instant) -> Option<Outgoing> {
        let mut message = self.message.clone();
        if let Outgoing::ActionStart { entity, action } = &mut message {
            // Playback interprets duration as remaining lifetime (slot 3 also
            // encodes loop phase). Motion commands retain authored trajectories.
            if matches!(action.slot, 3 | 4) && !(action.slot == 4 && action.duration_ms == u32::MAX)
            {
                let elapsed = now
                    .saturating_duration_since(self.queued_at)
                    .as_millis()
                    .min(u128::from(u32::MAX)) as u32;
                action.duration_ms = action.duration_ms.saturating_sub(elapsed);
                if action.duration_ms == 0 {
                    return Some(Outgoing::ActionEnd {
                        entity: *entity,
                        slot: action.slot,
                        sequence: action.sequence,
                    });
                }
            }
        }
        Some(message)
    }
}
fn action_key(message: &Outgoing) -> Option<(u32, u8, u16)> {
    match message {
        Outgoing::ActionStart { entity, action } => Some((*entity, action.slot, action.sequence)),
        Outgoing::ActionEnd {
            entity,
            slot,
            sequence,
        } => Some((*entity, *slot, *sequence)),
        _ => None,
    }
}
fn newer(a: u16, b: u16) -> bool {
    a != b && a.wrapping_sub(b) < 0x8000
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum State {
    Connecting,
    Synchronizing,
    Ready,
    Rejected,
    Closed,
}

/// An inbound packet, with the actual sender client ID retained for targeted events.
#[derive(Debug, Clone)]
pub struct Event {
    pub sender: u16,
    pub message: Incoming,
}

struct LocalEntity {
    spawn: Spawn,
    network_id: Option<u32>,
    last_send: Option<Instant>,
    destroyed: bool,
}

/// Runtime-independent transport. Call `poll` on each update and apply returned
/// events on the owning game thread. No background thread touches game state.
pub struct Client {
    #[cfg(test)]
    fail_next_transmit: std::cell::Cell<Option<io::ErrorKind>>,
    socket: UdpSocket,
    state: State,
    client_id: u16,
    session_id: u32,
    session_name: String,
    locals: BTreeMap<u32, LocalEntity>,
    next_request: u32,
    pending: VecDeque<Queued>,
    sessions: Vec<Option<Session>>,
    heartbeat: Instant,
    started: Instant,
    sync_quiet: Instant,
    // Only initial login retains the legacy quiet-period fallback. Silence
    // after an explicit transition is not evidence that it reached the server.
    allow_quiet_sync: bool,
    pending_session: Option<(u32, String)>,
    ready_received: bool,
    health: health::Health,
    ends: ends::Ends,
    closed: bool,
}

pub fn password_hash(password: &str) -> [u8; 32] {
    Sha256::digest(password.as_bytes()).into()
}

impl Client {
    /// The caller resolves hostnames off its UI thread before connecting.
    pub fn connect(address: SocketAddr, login: Login) -> Result<Self, Error> {
        let socket = UdpSocket::bind(if address.is_ipv4() {
            "0.0.0.0:0"
        } else {
            "[::]:0"
        })?;
        socket.connect(address)?;
        socket.set_nonblocking(true)?;
        // Validate before opening any session. Login is deliberately not retried:
        // protocol v1 allocates a new client ID on every login datagram.
        let bytes = protocol::encode(0, &Outgoing::Login(login))?;
        socket.send(&bytes)?;
        let now = Instant::now();
        Ok(Self {
            #[cfg(test)]
            fail_next_transmit: std::cell::Cell::new(None),
            socket,
            state: State::Connecting,
            client_id: 0,
            session_id: 0,
            session_name: String::new(),
            locals: BTreeMap::new(),
            next_request: 1,
            pending: VecDeque::new(),
            sessions: Vec::new(),
            heartbeat: now,
            started: now,
            sync_quiet: now,
            allow_quiet_sync: true,
            pending_session: None,
            ready_received: false,
            health: health::Health::new(now),
            ends: ends::Ends::default(),
            closed: false,
        })
    }

    pub fn state(&self) -> State {
        self.state
    }
    pub fn client_id(&self) -> u16 {
        self.client_id
    }
    pub fn session_id(&self) -> u32 {
        self.session_id
    }
    pub fn session_name(&self) -> &str {
        &self.session_name
    }
    pub fn sessions(&self) -> impl Iterator<Item = &Session> {
        self.sessions.iter().flatten()
    }
    /// Ends whose bounded confirmation window expired (including older relays
    /// without receipt support). This does not imply successful peer delivery.
    pub fn expired_action_ends(&self) -> u64 {
        self.ends.expired
    }
    pub fn network_id(&self, request: u32) -> Option<u32> {
        self.locals.get(&request).and_then(|e| e.network_id)
    }

    /// Register a local entity; the returned request ID stays stable across retries.
    pub fn register(&mut self, spawn: Spawn) -> Result<u32, Error> {
        if self.closed {
            return Err(Error::Closed);
        }
        if self.locals.len() >= MAX_ENTITIES {
            return Err(Error::Capacity("local entities"));
        }
        let request = self.next_request;
        protocol::encode(
            self.client_id,
            &Outgoing::Create {
                request,
                spawn: spawn.clone(),
            },
        )?;
        self.next_request = self
            .next_request
            .checked_add(1)
            .ok_or(Error::Capacity("request IDs"))?;
        self.locals.insert(
            request,
            LocalEntity {
                spawn,
                network_id: None,
                last_send: None,
                destroyed: false,
            },
        );
        Ok(request)
    }

    /// Queue until initial synchronization / create acknowledgement is complete.
    /// `request` resolves the message's entity ID immediately before sending.
    pub fn send(&mut self, request: Option<u32>, message: Outgoing) -> Result<(), Error> {
        if self.closed {
            return Err(Error::Closed);
        }
        protocol::encode(self.client_id, &message)?;
        if request.is_some_and(|id| !self.locals.contains_key(&id)) {
            return Err(Error::Protocol(protocol::Error::Invalid(
                "unknown local request",
            )));
        }
        if let Some((entity, slot, sequence)) = action_key(&message) {
            let same_slot = |queued: &Queued| {
                queued.request == request
                    && action_key(&queued.message)
                        .is_some_and(|(e, s, _)| s == slot && (request.is_some() || e == entity))
            };
            // Never allow a delayed start to replace a queued end/newer start.
            if self.pending.iter().any(|q| {
                same_slot(q)
                    && action_key(&q.message).is_some_and(|(_, _, seq)| {
                        newer(seq, sequence)
                            || (seq == sequence
                                && (matches!(message, Outgoing::ActionStart { .. })
                                    || matches!(q.message, Outgoing::ActionEnd { .. })))
                    })
            }) {
                return Ok(());
            }
            self.pending.retain(|q| {
                !same_slot(q)
                    || !action_key(&q.message).is_some_and(|(_, _, seq)| {
                        (newer(sequence, seq)
                            && (matches!(message, Outgoing::ActionEnd { .. })
                                || matches!(slot, 3 | 4)))
                            || (sequence == seq
                                && matches!(q.message, Outgoing::ActionStart { .. }))
                    })
            });
        }
        if self.pending.len() >= MAX_PENDING {
            return Err(Error::Capacity("pending messages"));
        }
        self.pending.push_back(Queued {
            request,
            message,
            queued_at: Instant::now(),
        });
        Ok(())
    }

    /// Preserve the create record until its acknowledgement, then destroy it.
    pub fn destroy(&mut self, request: u32) -> Result<(), Error> {
        let Some(local) = self.locals.get_mut(&request) else {
            return Ok(());
        };
        local.destroyed = true;
        self.pending.retain(|q| q.request != Some(request));
        if let Some(id) = local.network_id {
            self.ends.destroy(id);
            self.transmit(&Outgoing::Destroy(id))?;
            self.locals.remove(&request);
        } else if local.last_send.is_none() {
            self.locals.remove(&request);
        }
        Ok(())
    }

    /// Scene/session transitions invalidate local network IDs and all queued
    /// commands. Runtime ownership decides which local entities to register anew.
    pub fn join(&mut self, message: Outgoing) -> Result<(), Error> {
        if self.client_id == 0 || self.closed {
            return Err(Error::Closed);
        }
        if !matches!(
            message,
            Outgoing::JoinScene(_) | Outgoing::JoinSession { .. }
        ) {
            return Err(Error::Protocol(protocol::Error::Invalid(
                "invalid join request",
            )));
        }
        self.transmit(&message)?;
        self.pending_session = match message {
            Outgoing::JoinSession { id, name } => Some((id, name)),
            _ => None,
        };
        self.allow_quiet_sync = false;
        self.ready_received = false;
        self.locals.clear();
        self.pending.clear();
        self.state = State::Synchronizing;
        self.started = Instant::now();
        self.sync_quiet = self.started;
        self.health.reset(self.started);
        self.ends = ends::Ends::default();
        Ok(())
    }

    pub fn poll(&mut self) -> Result<Vec<Event>, Error> {
        if self.closed {
            return Ok(Vec::new());
        }
        let mut events = Vec::new();
        // Extra byte distinguishes oversized datagrams even on truncating OSes.
        let mut buffer = [0u8; crate::MAX_PACKET_SIZE + 1];
        for _ in 0..POLL_BUDGET {
            let size = match self.socket.recv(&mut buffer) {
                Ok(n) => n,
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => break,
                Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                Err(e) => return Err(e.into()),
            };
            let Ok((sender, message)) = protocol::decode(&buffer[..size], self.client_id) else {
                continue;
            };
            if self.health.receive(sender, self.client_id, &message) {
                continue;
            }
            match &message {
                Incoming::ActionEndReceipt {
                    entity,
                    slot,
                    sequence,
                } => {
                    if sender == 0 {
                        self.ends.receipt(*entity, *slot, *sequence);
                    }
                    continue;
                }
                Incoming::ActionEnd {
                    entity,
                    slot,
                    sequence,
                } => {
                    // A lost receipt is repaired when the relay repeats the end.
                    // Temporary send pressure must not discard a received end.
                    let receipt = Outgoing::ActionEndReceipt {
                        entity: *entity,
                        slot: *slot,
                        sequence: *sequence,
                    };
                    if let Err(error) = self.transmit(&receipt)
                        && !error.is_retryable()
                    {
                        return Err(error);
                    }
                    if !self.ends.receive(sender, *entity, *slot, *sequence) {
                        continue;
                    }
                }
                Incoming::Destroyed(entity) => self.ends.destroy(*entity),
                Incoming::Accepted {
                    client,
                    session,
                    name,
                } if *client != 0 => {
                    if !matches!(self.state, State::Connecting | State::Synchronizing)
                        || (!self.allow_quiet_sync && self.pending_session.is_none())
                        || (self.client_id != 0 && self.client_id != *client)
                        || self.pending_session.as_ref().is_some_and(|(id, expected)| {
                            if *id != 0 {
                                session != id
                            } else if !expected.is_empty() {
                                name != expected
                            } else {
                                *session == 0 || *session == self.session_id
                            }
                        })
                    {
                        continue;
                    }
                    self.pending_session = None;
                    self.client_id = *client;
                    self.session_id = *session;
                    self.session_name = name.clone();
                    self.state = if self.ready_received {
                        State::Ready
                    } else {
                        State::Synchronizing
                    };
                    // Repeated acceptance packets must not extend a join deadline.
                    self.sync_quiet = Instant::now();
                }
                Incoming::Rejected => {
                    self.state = State::Rejected;
                    self.closed = true;
                }
                Incoming::Disconnected => {
                    self.state = State::Closed;
                    self.closed = true;
                }
                Incoming::Ready => {
                    // UDP may deliver completion before acceptance. Retain it,
                    // but don't release queued work without the requested room.
                    self.ready_received = true;
                    if self.client_id != 0 && self.pending_session.is_none() {
                        self.state = State::Ready;
                    } else {
                        continue;
                    }
                }
                Incoming::Created { entity, request } => {
                    if let Some(local) = self.locals.get_mut(request) {
                        if local.destroyed {
                            local.network_id = Some(*entity);
                            if let Err(error) = self.transmit(&Outgoing::Destroy(*entity)) {
                                if !error.is_retryable() {
                                    return Err(error);
                                }
                            } else {
                                self.locals.remove(request);
                            }
                        } else {
                            local.network_id = Some(*entity);
                        }
                    }
                }
                Incoming::Sessions {
                    total,
                    offset,
                    sessions,
                } => {
                    if *offset == 0 || self.sessions.len() != usize::from(*total) {
                        self.sessions = vec![None; usize::from(*total)];
                    }
                    for (i, session) in sessions.iter().enumerate() {
                        self.sessions[usize::from(*offset) + i] = Some(session.clone());
                    }
                }
                Incoming::Snapshot(_) | Incoming::Variable { .. } | Incoming::EntityData { .. } => {
                    self.sync_quiet = Instant::now()
                }
                _ => {}
            }
            events.push(Event { sender, message });
            if self.closed {
                break;
            }
        }
        if self.closed {
            self.locals.clear();
            self.pending.clear();
            self.ends = ends::Ends::default();
            return Ok(events);
        }
        let now = Instant::now();
        if self.state == State::Ready && self.health.expired(now) {
            self.close();
            return Err(
                io::Error::new(io::ErrorKind::TimedOut, "multiplayer connection lost").into(),
            );
        }
        if matches!(self.state, State::Connecting | State::Synchronizing)
            && now.duration_since(self.started) > Duration::from_secs(10)
        {
            let message = if self.state == State::Connecting {
                "multiplayer login timed out"
            } else {
                "multiplayer synchronization timed out"
            };
            self.close();
            return Err(io::Error::new(io::ErrorKind::TimedOut, message).into());
        }
        // Match Java's bounded quiet-period fallback if Ready was lost in UDP.
        if self.state == State::Synchronizing
            && self.allow_quiet_sync
            && now.duration_since(self.started) >= Duration::from_millis(150)
            && now.duration_since(self.sync_quiet) >= Duration::from_millis(50)
        {
            self.state = State::Ready;
        }
        if self.client_id != 0 && now.duration_since(self.heartbeat) >= Duration::from_secs(1) {
            if let Err(error) = self.transmit(&Outgoing::Heartbeat) {
                if error.is_retryable() {
                    return Ok(events);
                }
                return Err(error);
            }
            self.heartbeat = now;
        }
        if self.state == State::Ready {
            for message in self.ends.due(now) {
                match self.transmit(&message) {
                    Ok(()) => self.ends.sent(&message, now),
                    Err(error) if error.is_retryable() => return Ok(events),
                    Err(error) => return Err(error),
                }
            }
            if let Some(probe) = self.health.probe(self.client_id, now) {
                match self.transmit(&probe) {
                    Ok(()) => self.health.sent(now),
                    Err(error) if error.is_retryable() => return Ok(events),
                    Err(error) => return Err(error),
                }
            }
            let destroyed = self
                .locals
                .iter()
                .filter_map(|(&request, local)| {
                    local.destroyed.then_some((request, local.network_id))
                })
                .collect::<Vec<_>>();
            for (request, id) in destroyed.into_iter().take(POLL_BUDGET) {
                if let Some(id) = id {
                    if let Err(error) = self.transmit(&Outgoing::Destroy(id)) {
                        if error.is_retryable() {
                            return Ok(events);
                        }
                        return Err(error);
                    }
                    self.locals.remove(&request);
                }
            }
            let mut retry = Vec::new();
            for (&request, local) in &self.locals {
                if local.network_id.is_none()
                    && local
                        .last_send
                        .is_none_or(|last| now.duration_since(last) >= Duration::from_millis(350))
                {
                    retry.push((request, local.spawn.clone()));
                    if retry.len() >= POLL_BUDGET {
                        break;
                    }
                }
            }
            for (request, spawn) in retry {
                if let Err(error) = self.transmit(&Outgoing::Create { request, spawn }) {
                    if error.is_retryable() {
                        return Ok(events);
                    }
                    return Err(error);
                }
                if let Some(local) = self.locals.get_mut(&request) {
                    local.last_send = Some(now);
                }
            }
            for _ in 0..self.pending.len().min(POLL_BUDGET) {
                let Some(queued) = self.pending.pop_front() else {
                    break;
                };
                let Some(mut message) = queued.wire(now) else {
                    continue;
                };
                if let Some(request) = queued.request {
                    let Some(id) = self.network_id(request) else {
                        self.pending.push_back(queued);
                        continue;
                    };
                    set_entity(&mut message, id);
                }
                if !self.ends.has_room(&message) {
                    self.pending.push_back(queued);
                    continue;
                }
                if let Err(e) = self.transmit(&message) {
                    self.pending.push_front(queued);
                    if e.is_retryable() {
                        return Ok(events);
                    }
                    return Err(e);
                }
                self.ends.sent(&message, now);
            }
        }
        Ok(events)
    }

    fn transmit(&self, message: &Outgoing) -> Result<(), Error> {
        #[cfg(test)]
        if let Some(kind) = self.fail_next_transmit.take() {
            return Err(io::Error::from(kind).into());
        }
        let packet = protocol::encode(self.client_id, message)?;
        self.socket.send(&packet)?;
        Ok(())
    }
    pub fn close(&mut self) {
        if !self.closed && self.client_id != 0 {
            let _ = self.transmit(&Outgoing::Disconnect);
        }
        self.closed = true;
        self.state = State::Closed;
        self.locals.clear();
        self.pending.clear();
        self.pending_session = None;
        self.ends = ends::Ends::default();
    }
}
impl Drop for Client {
    fn drop(&mut self) {
        self.close();
    }
}

fn set_entity(message: &mut Outgoing, id: u32) {
    match message {
        Outgoing::Destroy(entity)
        | Outgoing::Command { entity, .. }
        | Outgoing::Transform { entity, .. }
        | Outgoing::ActionStart { entity, .. }
        | Outgoing::ActionEnd { entity, .. }
        | Outgoing::EntityData { entity, .. } => *entity = id,
        _ => {}
    }
}

#[cfg(test)]
mod lifecycle_tests {
    use super::*;
    #[test]
    fn full_end_outbox_retains_expired_playback_and_allows_unrelated_traffic() {
        let (server, mut client, _) = connected();
        let now = Instant::now();
        for entity in 0..4096 {
            client.ends.sent(
                &Outgoing::ActionEnd {
                    entity,
                    slot: 3,
                    sequence: 1,
                },
                now,
            );
        }
        client
            .send(
                None,
                Outgoing::ActionStart {
                    entity: 9000,
                    action: protocol::Action {
                        slot: 3,
                        sequence: 2,
                        duration_ms: 1,
                        command: "subject.take".into(),
                    },
                },
            )
            .unwrap();
        client.pending.back_mut().unwrap().queued_at = now - Duration::from_secs(1);
        client
            .send(
                None,
                Outgoing::Event {
                    target: 1,
                    name: "unrelated".into(),
                    message: None,
                },
            )
            .unwrap();
        client.poll().unwrap();
        assert_eq!(client.pending.len(), 1);
        let mut bytes = [0; 1200];
        let (size, _) = server.recv_from(&mut bytes).unwrap();
        assert_eq!(
            crate::read_header(&bytes[..size]).unwrap().packet_type,
            crate::NETWORK_EVENT
        );
        client.ends.receipt(0, 3, 1);
        client
            .fail_next_transmit
            .set(Some(io::ErrorKind::WouldBlock));
        client.poll().unwrap();
        assert_eq!(client.pending.len(), 1);
        client.poll().unwrap();
        assert!(client.pending.is_empty());
        let (size, _) = server.recv_from(&mut bytes).unwrap();
        assert!(matches!(
            protocol::decode(&bytes[..size], 1).unwrap().1,
            Incoming::ActionEnd {
                entity: 9000,
                slot: 3,
                sequence: 2
            }
        ));
    }
    #[test]
    fn end_retry_state_is_cleared_by_destroy_join_and_close() {
        let (_server, mut client, request) = connected();
        let end = Outgoing::ActionEnd {
            entity: 77,
            slot: 3,
            sequence: 1,
        };
        let now = Instant::now();
        client.ends.sent(&end, now);
        client.destroy(request).unwrap();
        assert!(client.ends.due(now + Duration::from_secs(1)).is_empty());
        client.ends.sent(&end, now);
        client.join(Outgoing::JoinScene("next".into())).unwrap();
        assert!(client.ends.due(now + Duration::from_secs(1)).is_empty());
        client.ends.sent(&end, now);
        client.close();
        assert!(client.ends.due(now + Duration::from_secs(1)).is_empty());
    }
    #[test]
    fn relay_receipts_are_exact_size_and_only_server_receipts_release_retries() {
        let (server, mut client, _) = connected();
        let now = Instant::now();
        let end = Outgoing::ActionEnd {
            entity: 77,
            slot: 3,
            sequence: 1,
        };
        client.ends.sent(&end, now);
        let packet = protocol::encode(
            0,
            &Outgoing::ActionEndReceipt {
                entity: 77,
                slot: 3,
                sequence: 1,
            },
        )
        .unwrap();
        for size in 8..16 {
            assert!(protocol::decode(&packet[..size], 1).is_err());
        }
        let mut long = packet.clone();
        long.push(0);
        assert!(protocol::decode(&long, 1).is_err());
        let mut foreign = packet.clone();
        foreign[6..8].copy_from_slice(&2u16.to_le_bytes());
        server
            .send_to(&foreign, client.socket.local_addr().unwrap())
            .unwrap();
        std::thread::sleep(Duration::from_millis(5));
        client.poll().unwrap();
        assert_eq!(client.ends.due(now + Duration::from_secs(1)), [end]);
        server
            .send_to(&packet, client.socket.local_addr().unwrap())
            .unwrap();
        std::thread::sleep(Duration::from_millis(5));
        client.poll().unwrap();
        assert!(client.ends.due(now + Duration::from_secs(1)).is_empty());
    }
    fn deliver(server: &UdpSocket, client: &mut Client, kind: u8, payload: &[u8]) -> Vec<Event> {
        let mut packet = Vec::new();
        crate::write_header(kind, 0, &mut packet);
        packet.extend_from_slice(payload);
        server
            .send_to(&packet, client.socket.local_addr().unwrap())
            .unwrap();
        let deadline = Instant::now() + Duration::from_secs(1);
        loop {
            let events = client.poll().unwrap();
            if !events.is_empty() {
                return events;
            }
            // Ignored messages intentionally produce no event.
            if Instant::now() >= deadline {
                return events;
            }
            std::thread::sleep(Duration::from_millis(1));
        }
    }
    #[test]
    fn explicit_join_silence_times_out_and_discards_queued_state() {
        let (_server, mut client, _) = connected();
        client.join(Outgoing::JoinScene("other".into())).unwrap();
        client
            .send(
                None,
                Outgoing::Event {
                    target: 0,
                    name: "after-join".into(),
                    message: None,
                },
            )
            .unwrap();
        client.started = Instant::now() - Duration::from_secs(11);
        let error = client.poll().unwrap_err();
        assert!(matches!(error, Error::Io(ref e) if e.kind() == io::ErrorKind::TimedOut));
        assert_eq!(client.state(), State::Closed);
        assert!(client.pending.is_empty());
        assert!(client.locals.is_empty());
        assert!(client.poll().unwrap().is_empty());
    }
    #[test]
    fn session_join_requires_matching_acceptance_before_ready() {
        let (server, mut client, _) = connected();
        client.session_id = 1000;
        client
            .join(Outgoing::JoinSession {
                id: 2000,
                name: String::new(),
            })
            .unwrap();
        assert!(deliver(&server, &mut client, crate::INITIAL_SYNC_COMPLETE, &[]).is_empty());
        assert_eq!(client.state(), State::Synchronizing);
        let accepted = |id: u32| {
            let mut bytes = 1u16.to_le_bytes().to_vec();
            bytes.extend(id.to_le_bytes());
            bytes.resize(70, 0);
            bytes
        };
        assert!(deliver(&server, &mut client, crate::LOGIN_ACCEPTED, &accepted(1000)).is_empty());
        assert_eq!(client.session_id(), 1000);
        let deadline_origin = client.started;
        deliver(&server, &mut client, crate::LOGIN_ACCEPTED, &accepted(2000));
        assert_eq!(client.session_id(), 2000);
        assert_eq!(client.started, deadline_origin);
        // The earlier reordered Ready now has a matching acceptance.
        assert_eq!(client.state(), State::Ready);
        // A delayed acceptance after completion cannot move the client back.
        assert!(deliver(&server, &mut client, crate::LOGIN_ACCEPTED, &accepted(1000)).is_empty());
        assert_eq!(client.session_id(), 2000);
        assert_eq!(client.state(), State::Ready);
    }
    #[test]
    fn initial_login_keeps_quiet_fallback_after_acceptance() {
        let (_server, mut client, _) = connected();
        client.state = State::Synchronizing;
        client.started = Instant::now() - Duration::from_millis(200);
        client.sync_quiet = client.started;
        client.poll().unwrap();
        assert_eq!(client.state(), State::Ready);
    }
    fn connected() -> (UdpSocket, Client, u32) {
        let server = UdpSocket::bind("127.0.0.1:0").unwrap();
        server
            .set_read_timeout(Some(Duration::from_millis(100)))
            .unwrap();
        let mut client = Client::connect(
            server.local_addr().unwrap(),
            Login {
                password_hash: password_hash(""),
                project_guid: "fixture".into(),
                create_session: false,
                session_id: 1000,
                session_name: "fixture".into(),
                scene: "main".into(),
                player: "fixture".into(),
            },
        )
        .unwrap();
        server.recv_from(&mut [0; 1200]).unwrap();
        client.state = State::Ready;
        client.client_id = 1;
        let request = client
            .register(Spawn {
                archetype: "box".into(),
                player: "fixture".into(),
                source_name: "subject".into(),
                command: "{network_entity} => box".into(),
            })
            .unwrap();
        client.locals.get_mut(&request).unwrap().network_id = Some(77);
        (server, client, request)
    }
    fn start(sequence: u16, slot: u8, duration_ms: u32) -> Outgoing {
        Outgoing::ActionStart {
            entity: 0,
            action: protocol::Action {
                slot,
                sequence,
                duration_ms,
                command: "subject.take".into(),
            },
        }
    }
    fn receive(server: &UdpSocket) -> Incoming {
        let mut buf = [0; 1200];
        let size = server.recv(&mut buf).unwrap();
        protocol::decode(&buf[..size], 2).unwrap().1
    }
    #[test]
    fn playback_queue_ages_once_and_expires_without_replaying_finished_work() {
        let (server, mut client, request) = connected();
        client.send(Some(request), start(1, 3, 1000)).unwrap();
        client.pending.front_mut().unwrap().queued_at -= Duration::from_millis(400);
        client
            .fail_next_transmit
            .set(Some(io::ErrorKind::WouldBlock));
        assert!(client.poll().unwrap().is_empty());
        client.poll().unwrap();
        let Incoming::ActionStart { entity, action } = receive(&server) else {
            panic!()
        };
        assert_eq!(entity, 77);
        assert!((550..=600).contains(&action.duration_ms));
        client.send(Some(request), start(2, 3, 10)).unwrap();
        client.pending.front_mut().unwrap().queued_at -= Duration::from_secs(1);
        client.poll().unwrap();
        assert!(client.pending.is_empty());
        assert!(matches!(
            receive(&server),
            Incoming::ActionEnd {
                entity: 77,
                slot: 3,
                sequence: 2
            }
        ));
        for (slot, duration, expected) in [
            (3, u32::MAX, u32::MAX - 500),
            (4, u32::MAX, u32::MAX),
            (1, 1000, 1000),
        ] {
            let now = Instant::now();
            let q = Queued {
                request: None,
                message: start(1, slot, duration),
                queued_at: now - Duration::from_millis(500),
            };
            let Outgoing::ActionStart { action, .. } = q.wire(now).unwrap() else {
                panic!()
            };
            assert_eq!(action.duration_ms, expected);
        }
    }
    #[test]
    fn numbered_queue_coalesces_wraparound_end_before_start_and_keeps_other_slots() {
        let (_server, mut client, request) = connected();
        client
            .send(Some(request), start(u16::MAX, 3, 1000))
            .unwrap();
        client.send(Some(request), start(1, 1, 1000)).unwrap();
        client.send(Some(request), start(0, 3, 1000)).unwrap();
        assert_eq!(client.pending.len(), 2);
        let queued_at = client.pending.back().unwrap().queued_at;
        client.send(Some(request), start(0, 3, 1000)).unwrap();
        assert_eq!(client.pending.back().unwrap().queued_at, queued_at);
        client
            .send(
                Some(request),
                Outgoing::ActionEnd {
                    entity: 0,
                    slot: 3,
                    sequence: 0,
                },
            )
            .unwrap();
        client.send(Some(request), start(0, 3, 1000)).unwrap();
        client
            .send(Some(request), start(u16::MAX, 3, 1000))
            .unwrap();
        assert_eq!(client.pending.len(), 2);
        assert!(matches!(
            client.pending.back().unwrap().message,
            Outgoing::ActionEnd { sequence: 0, .. }
        ));
        // Motion deltas and incremental structural commands are not complete
        // playback replacements; keep distinct starts in their original order.
        for slot in [1, 64] {
            client.send(Some(request), start(2, slot, 1000)).unwrap();
            client.send(Some(request), start(3, slot, 1000)).unwrap();
            assert!(client.pending.iter().any(|q| matches!(&q.message,Outgoing::ActionStart { action,.. } if action.slot==slot && action.sequence==2)));
        }
    }
    #[test]
    fn full_queue_allows_supersession_and_failed_destroy_retries_on_poll() {
        let (server, mut client, request) = connected();
        client.send(Some(request), start(1, 3, 1000)).unwrap();
        for _ in 1..MAX_PENDING {
            client.send(None, Outgoing::Heartbeat).unwrap();
        }
        assert!(
            client
                .send(None, Outgoing::Heartbeat)
                .unwrap_err()
                .is_retryable()
        );
        client.send(Some(request), start(2, 3, 1000)).unwrap();
        assert_eq!(client.pending.len(), MAX_PENDING);
        client
            .fail_next_transmit
            .set(Some(io::ErrorKind::WouldBlock));
        assert!(client.destroy(request).unwrap_err().is_retryable());
        assert!(client.locals[&request].destroyed);
        client.pending.clear();
        client.poll().unwrap();
        assert_eq!(receive(&server), Incoming::Destroyed(77));
        assert!(!client.locals.contains_key(&request));
    }
}
