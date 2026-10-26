//! Browser networking continues answering WebSocket pings while rendering is
//! suspended. Only a matching pong renews the authenticated UDP peer's lease.
use scenemax_multiplayer::protocol::{self, Incoming, Outgoing};
use std::time::{Duration, Instant};

pub(super) struct Liveness {
    client: u16,
    last_ping: Instant,
    sequence: u64,
    pending: Option<(Vec<u8>, Instant)>,
}
impl Liveness {
    pub(super) fn new(now: Instant) -> Self {
        Self {
            client: 0,
            last_ping: now,
            sequence: 0,
            pending: None,
        }
    }
    pub(super) fn receive(&mut self, bytes: &[u8]) {
        // Keep the hot forwarding path allocation-free; only login/lifecycle
        // messages need decoding. Do not parse snapshots or gameplay payloads.
        if !scenemax_multiplayer::read_header(bytes).is_ok_and(|header| {
            matches!(
                header.packet_type,
                scenemax_multiplayer::LOGIN_ACCEPTED
                    | scenemax_multiplayer::LOGIN_REJECTED
                    | scenemax_multiplayer::DISCONNECT
            )
        }) {
            return;
        }
        match protocol::decode(bytes, self.client) {
            // This method is called only on the connected upstream UDP socket.
            // The Zig relay puts the assigned client ID in the acceptance header.
            Ok((_, Incoming::Accepted { client, .. })) => self.client = client,
            Ok((_, Incoming::Disconnected | Incoming::Rejected)) => self.client = 0,
            _ => {}
        }
    }
    pub(super) fn ping(&mut self, now: Instant) -> Option<Vec<u8>> {
        if self.pending.is_some() || now.duration_since(self.last_ping) < Duration::from_secs(3) {
            return None;
        }
        self.sequence = self.sequence.wrapping_add(1);
        let bytes = self.sequence.to_le_bytes().to_vec();
        self.pending = Some((bytes.clone(), now));
        self.last_ping = now;
        Some(bytes)
    }
    pub(super) fn pong(&mut self, bytes: &[u8]) -> Option<Vec<u8>> {
        if !self
            .pending
            .as_ref()
            .is_some_and(|(expected, _)| expected == bytes)
        {
            return None;
        }
        self.pending = None;
        (self.client != 0).then(|| protocol::encode(self.client, &Outgoing::Heartbeat).unwrap())
    }
    pub(super) fn expired(&self, now: Instant) -> bool {
        self.pending
            .as_ref()
            .is_some_and(|(_, sent)| now.duration_since(*sent) >= Duration::from_secs(10))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn accepted(client: u16) -> Vec<u8> {
        let mut bytes = Vec::new();
        scenemax_multiplayer::write_header(
            scenemax_multiplayer::LOGIN_ACCEPTED,
            client,
            &mut bytes,
        );
        bytes.extend(client.to_le_bytes());
        bytes.extend(1000u32.to_le_bytes());
        bytes.extend([0; 64]);
        bytes
    }
    #[test]
    fn only_matching_pong_renews_authenticated_udp_client() {
        let start = Instant::now();
        let mut state = Liveness::new(start);
        let ping = state.ping(start + Duration::from_secs(3)).unwrap();
        assert!(state.pong(&ping).is_none()); // no server acceptance yet
        state.receive(&accepted(42));
        let ping = state.ping(start + Duration::from_secs(6)).unwrap();
        assert!(state.pong(b"unsolicited").is_none());
        let heartbeat = state.pong(&ping).unwrap();
        assert_eq!(
            heartbeat,
            protocol::encode(42, &Outgoing::Heartbeat).unwrap()
        );
        assert!(state.pong(&ping).is_none()); // replay cannot renew lease
        assert!(!state.expired(start + Duration::from_secs(20)));
    }
    #[test]
    fn unanswered_ping_expires_even_if_other_frames_continue() {
        let start = Instant::now();
        let mut state = Liveness::new(start);
        state.receive(&accepted(7));
        state.ping(start + Duration::from_secs(3)).unwrap();
        assert!(state.ping(start + Duration::from_secs(6)).is_none());
        state.receive(&accepted(7));
        assert!(state.pong(b"wrong").is_none());
        assert!(!state.expired(start + Duration::from_secs(12)));
        assert!(state.expired(start + Duration::from_secs(13)));
    }
}
