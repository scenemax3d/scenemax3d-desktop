//! Bounded end retries. Receipts confirm relay acceptance, not all peer delivery.
use super::*;

const CAPACITY: usize = 4096;
const RETRY: Duration = Duration::from_millis(250);
const RETENTION: Duration = Duration::from_secs(8);

struct Pending {
    sequence: u16,
    started: Instant,
    sent: Instant,
}
#[derive(Default)]
pub(super) struct Ends {
    pending: BTreeMap<(u32, u8), Pending>,
    seen: VecDeque<(u16, u32, u8, u16)>,
    pub(super) expired: u64,
}
impl Ends {
    pub(super) fn has_room(&self, message: &Outgoing) -> bool {
        let Outgoing::ActionEnd { entity, slot, .. } = message else {
            return true;
        };
        self.pending.contains_key(&(*entity, *slot)) || self.pending.len() < CAPACITY
    }
    pub(super) fn sent(&mut self, message: &Outgoing, now: Instant) {
        match message {
            Outgoing::ActionEnd {
                entity,
                slot,
                sequence,
            } => {
                let key = (*entity, *slot);
                if let Some(previous) = self.pending.get_mut(&key) {
                    if previous.sequence == *sequence {
                        previous.sent = now;
                        return;
                    }
                    if !newer(*sequence, previous.sequence) {
                        return;
                    }
                }
                self.pending.insert(
                    key,
                    Pending {
                        sequence: *sequence,
                        started: now,
                        sent: now,
                    },
                );
            }
            Outgoing::ActionStart { entity, action } => {
                let key = (*entity, action.slot);
                if self
                    .pending
                    .get(&key)
                    .is_some_and(|p| newer(action.sequence, p.sequence))
                {
                    self.pending.remove(&key);
                }
            }
            Outgoing::Destroy(entity) => self.destroy(*entity),
            _ => {}
        }
    }
    pub(super) fn receipt(&mut self, entity: u32, slot: u8, sequence: u16) {
        let key = (entity, slot);
        if self
            .pending
            .get(&key)
            .is_some_and(|p| p.sequence == sequence)
        {
            self.pending.remove(&key);
        }
    }
    pub(super) fn receive(&mut self, sender: u16, entity: u32, slot: u8, sequence: u16) -> bool {
        let key = (sender, entity, slot, sequence);
        if self.seen.contains(&key) {
            return false;
        }
        if self.seen.len() == CAPACITY {
            self.seen.pop_front();
        }
        self.seen.push_back(key);
        true
    }
    pub(super) fn destroy(&mut self, entity: u32) {
        self.pending.retain(|(id, _), _| *id != entity);
        self.seen.retain(|(_, id, _, _)| *id != entity);
    }
    pub(super) fn due(&mut self, now: Instant) -> Vec<Outgoing> {
        let before = self.pending.len();
        self.pending
            .retain(|_, p| now.saturating_duration_since(p.started) < RETENTION);
        self.expired = self
            .expired
            .saturating_add((before - self.pending.len()) as u64);
        let mut due = self
            .pending
            .iter()
            .filter(|(_, p)| now.saturating_duration_since(p.sent) >= RETRY)
            .collect::<Vec<_>>();
        // Oldest sent first: a busy low entity ID cannot starve other retries.
        due.sort_by_key(|(_, p)| p.sent);
        due.into_iter()
            .take(POLL_BUDGET)
            .map(|(&(entity, slot), p)| Outgoing::ActionEnd {
                entity,
                slot,
                sequence: p.sequence,
            })
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn end(entity: u32, sequence: u16) -> Outgoing {
        Outgoing::ActionEnd {
            entity,
            slot: 3,
            sequence,
        }
    }
    #[test]
    fn exact_receipts_wrap_supersession_and_duplicate_deadlines() {
        let mut ends = Ends::default();
        let now = Instant::now();
        ends.sent(&end(7, u16::MAX), now);
        ends.receipt(8, 3, u16::MAX);
        ends.receipt(7, 4, u16::MAX);
        ends.receipt(7, 3, 0);
        assert_eq!(ends.due(now + RETRY), [end(7, u16::MAX)]);
        ends.sent(
            &Outgoing::ActionStart {
                entity: 7,
                action: protocol::Action {
                    slot: 3,
                    sequence: 0,
                    duration_ms: 1000,
                    command: String::new(),
                },
            },
            now,
        );
        assert!(ends.due(now + RETRY).is_empty());
        ends.sent(&end(7, 0), now);
        ends.sent(&end(7, 0), now + Duration::from_secs(7));
        assert!(ends.due(now + RETENTION).is_empty());
        assert_eq!(ends.expired, 1);
        ends.sent(&end(7, 1), now);
        ends.receipt(7, 3, 1);
        assert!(ends.due(now + RETRY).is_empty());
    }
    #[test]
    fn pressure_does_not_evict_pending_ends_and_retries_are_fair() {
        let mut ends = Ends::default();
        let now = Instant::now();
        for id in 0..CAPACITY as u32 {
            assert!(ends.has_room(&end(id, 1)));
            ends.sent(&end(id, 1), now);
        }
        assert!(!ends.has_room(&end(CAPACITY as u32, 1)));
        let first = ends.due(now + RETRY);
        assert_eq!(first.len(), POLL_BUDGET);
        for message in &first {
            ends.sent(message, now + RETRY);
        }
        let second = ends.due(now + RETRY);
        assert!(second.iter().all(|m| !first.contains(m)));
        ends.destroy(0);
        assert!(ends.has_room(&end(CAPACITY as u32, 1)));
        assert!(ends.receive(1, 9, 3, 5));
        assert!(!ends.receive(1, 9, 3, 5));
        ends.destroy(9);
        assert!(ends.receive(1, 9, 3, 5));
    }
}
