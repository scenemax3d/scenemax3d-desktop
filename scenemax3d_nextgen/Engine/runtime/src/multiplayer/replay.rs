//! Per-scene replay history for the sequence numbers already present on the wire.
//! Unnumbered v1 commands/transforms cannot be totally ordered by this history.
use std::collections::{HashMap, HashSet, VecDeque};

const MAX_ENTITIES: usize = 2048;
const MAX_TOMBSTONES: usize = 4096;

#[derive(Default)]
struct EntityHistory {
    pose_received: bool,
    // At most 256 slots per entity; ended slots retain their sequence watermark.
    slots: HashMap<u8, (u16, bool)>,
}

#[derive(Default)]
pub(super) struct ReplayHistory {
    entities: HashMap<u32, EntityHistory>,
    destroyed: HashSet<u32>,
    destruction_order: VecDeque<u32>,
}

fn newer(sequence: u16, previous: u16) -> bool {
    let distance = sequence.wrapping_sub(previous);
    distance != 0 && distance < 0x8000
}

impl ReplayHistory {
    fn entity(&mut self, id: u32) -> Option<&mut EntityHistory> {
        if self.is_destroyed(id)
            || (!self.entities.contains_key(&id) && self.entities.len() >= MAX_ENTITIES)
        {
            return None;
        }
        Some(self.entities.entry(id).or_default())
    }

    pub(super) fn is_destroyed(&self, id: u32) -> bool {
        self.destroyed.contains(&id)
    }

    pub(super) fn snapshot_pose(&mut self, id: u32) -> bool {
        let Some(entity) = self.entity(id) else {
            return false;
        };
        let first = !entity.pose_received;
        entity.pose_received = true;
        first
    }

    pub(super) fn live_pose(&mut self, id: u32) -> bool {
        let Some(entity) = self.entity(id) else {
            return false;
        };
        entity.pose_received = true;
        true
    }

    pub(super) fn start(&mut self, id: u32, slot: u8, sequence: u16) -> bool {
        if slot == 0 {
            return false;
        }
        let Some(entity) = self.entity(id) else {
            return false;
        };
        if entity
            .slots
            .get(&slot)
            .is_some_and(|(previous, _)| !newer(sequence, *previous))
        {
            return false;
        }
        entity.slots.insert(slot, (sequence, true));
        true
    }

    pub(super) fn end(&mut self, id: u32, slot: u8, sequence: u16) {
        if slot == 0 {
            return;
        }
        let Some(entity) = self.entity(id) else {
            return;
        };
        if entity
            .slots
            .get(&slot)
            .is_some_and(|(previous, _)| *previous != sequence && !newer(sequence, *previous))
        {
            return;
        }
        entity.slots.insert(slot, (sequence, false));
    }

    pub(super) fn current(&self, id: u32, slot: u8, sequence: u16) -> bool {
        self.entities
            .get(&id)
            .and_then(|entity| entity.slots.get(&slot))
            == Some(&(sequence, true))
    }

    pub(super) fn destroy(&mut self, id: u32) {
        self.entities.remove(&id);
        if self.destroyed.insert(id) {
            self.destruction_order.push_back(id);
            if self.destruction_order.len() > MAX_TOMBSTONES {
                if let Some(oldest) = self.destruction_order.pop_front() {
                    self.destroyed.remove(&oldest);
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sequence_slots_reject_duplicates_stale_starts_and_stale_ends() {
        let mut history = ReplayHistory::default();
        assert!(history.start(1, 3, 65535));
        assert!(!history.start(1, 3, 65535));
        assert!(history.start(1, 3, 0));
        assert!(!history.start(1, 3, 65534));
        history.end(1, 3, 65535);
        assert!(history.current(1, 3, 0));
        history.end(1, 3, 0);
        assert!(!history.start(1, 3, 0));
        assert!(history.start(1, 4, 0));
        history.end(1, 5, 7);
        assert!(!history.start(1, 5, 6));
        assert!(!history.start(1, 5, 7));
        assert!(history.start(1, 5, 8));
    }

    #[test]
    fn split_snapshot_pose_does_not_rewind_live_state_and_destroy_is_bounded() {
        let mut history = ReplayHistory::default();
        assert!(history.snapshot_pose(1));
        assert!(!history.snapshot_pose(1));
        assert!(history.live_pose(2));
        assert!(!history.snapshot_pose(2));
        history.destroy(2);
        assert!(!history.start(2, 1, 1));
        assert!(!history.snapshot_pose(2));
        for id in 3..5000 {
            history.destroy(id);
        }
        assert_eq!(history.destroyed.len(), MAX_TOMBSTONES);
        assert_eq!(history.destruction_order.len(), MAX_TOMBSTONES);
        assert!(history.is_destroyed(4999));
        for id in 10000..13000 {
            history.live_pose(id);
        }
        assert_eq!(history.entities.len(), MAX_ENTITIES);
    }
}
