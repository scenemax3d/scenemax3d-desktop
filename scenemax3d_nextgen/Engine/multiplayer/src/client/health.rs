//! Bidirectional liveness over protocol-v1 self-directed events. No new packets.
use super::*;

const NAME: &str = "$scenemax:transport:probe";
const INTERVAL: Duration = Duration::from_secs(1);
const TIMEOUT: Duration = Duration::from_secs(10);

pub(super) struct Health {
    sequence: u64,
    last_send: Instant,
    pending: Option<(String, Instant)>,
}
impl Health {
    pub(super) fn new(now: Instant) -> Self {
        Self {
            sequence: 0,
            last_send: now,
            pending: None,
        }
    }
    pub(super) fn reset(&mut self, now: Instant) {
        // Keep the sequence across joins so an old echo cannot confirm a new one.
        self.pending = None;
        self.last_send = now;
    }
    pub(super) fn expired(&self, now: Instant) -> bool {
        self.pending
            .as_ref()
            .is_some_and(|(_, started)| now.saturating_duration_since(*started) >= TIMEOUT)
    }
    pub(super) fn probe(&mut self, client: u16, now: Instant) -> Option<Outgoing> {
        if now.saturating_duration_since(self.last_send) < INTERVAL {
            return None;
        }
        if self.pending.is_none() {
            self.sequence = self.sequence.wrapping_add(1);
            self.pending = Some((self.sequence.to_string(), now));
        }
        Some(Outgoing::Event {
            target: client,
            name: NAME.into(),
            message: self.pending.as_ref().map(|(token, _)| token.clone()),
        })
    }
    pub(super) fn sent(&mut self, now: Instant) {
        self.last_send = now;
    }
    pub(super) fn receive(&mut self, sender: u16, client: u16, message: &Incoming) -> bool {
        let Incoming::Event { name, message } = message else {
            return false;
        };
        if name != NAME {
            return false;
        }
        if client != 0
            && sender == client
            && self
                .pending
                .as_ref()
                .is_some_and(|(token, _)| message.as_ref() == Some(token))
        {
            self.pending = None;
        }
        // The reserved transport event must never invoke a script handler.
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn echo(probe: Outgoing) -> Incoming {
        let Outgoing::Event { name, message, .. } = probe else {
            panic!("probe event");
        };
        Incoming::Event { name, message }
    }
    #[test]
    fn retries_keep_deadline_and_only_matching_self_echo_restores_health() {
        let start = Instant::now();
        let mut health = Health::new(start);
        assert!(health.probe(7, start).is_none());
        let first = echo(health.probe(7, start + INTERVAL).unwrap());
        health.sent(start + INTERVAL);
        assert!(health.receive(8, 7, &first));
        for second in 2..11 {
            assert_eq!(
                echo(health.probe(7, start + INTERVAL * second).unwrap()),
                first
            );
            health.sent(start + INTERVAL * second);
        }
        assert!(health.expired(start + INTERVAL * 11));
        assert!(health.receive(7, 7, &first));
        assert!(!health.expired(start + INTERVAL * 11));
        let next = echo(health.probe(7, start + INTERVAL * 11).unwrap());
        assert_ne!(next, first);
        health.receive(7, 7, &first);
        assert!(health.expired(start + INTERVAL * 21));
    }
    #[test]
    fn join_reset_invalidates_old_echo_and_preserves_application_events() {
        let start = Instant::now();
        let mut health = Health::new(start);
        let old = echo(health.probe(7, start + INTERVAL).unwrap());
        health.reset(start + INTERVAL * 2);
        let new = echo(health.probe(7, start + INTERVAL * 3).unwrap());
        health.receive(7, 7, &old);
        assert!(health.expired(start + INTERVAL * 13));
        health.receive(7, 7, &new);
        assert!(!health.expired(start + INTERVAL * 13));
        assert!(!health.receive(
            7,
            7,
            &Incoming::Event {
                name: "application".into(),
                message: None
            }
        ));
    }
}
