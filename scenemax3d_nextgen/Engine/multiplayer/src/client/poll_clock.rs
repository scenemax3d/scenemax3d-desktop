//! Browser socket callbacks cannot run while a synchronous frame loads assets.
//! On resumption, let the event loop deliver queued responses before expiring
//! the connection. Keep the original deadlines: a silent peer still times out.
use std::time::Duration;
use web_time::Instant;

pub(super) struct PollClock {
    previous: Instant,
    resume_until: Instant,
}

impl PollClock {
    pub(super) fn new(now: Instant) -> Self {
        Self {
            previous: now,
            resume_until: now,
        }
    }

    pub(super) fn can_expire(&mut self, now: Instant) -> bool {
        if now.saturating_duration_since(self.previous) >= Duration::from_secs(2) {
            self.resume_until = now + Duration::from_secs(1);
        }
        self.previous = now;
        now >= self.resume_until
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn loading_pause_yields_to_callbacks_without_resetting_deadlines() {
        let start = Instant::now();
        let mut clock = PollClock::new(start);
        assert!(clock.can_expire(start));
        assert!(clock.can_expire(start + Duration::from_millis(40)));
        let resumed = start + Duration::from_secs(15);
        assert!(!clock.can_expire(resumed));
        assert!(!clock.can_expire(resumed + Duration::from_millis(400)));
        assert!(!clock.can_expire(resumed + Duration::from_millis(800)));
        // No response arrived: existing login/sync/health deadlines apply again.
        assert!(clock.can_expire(resumed + Duration::from_secs(1)));
        assert!(clock.can_expire(resumed + Duration::from_millis(1040)));
    }
    #[test]
    fn normal_polling_and_one_hz_background_ticks_do_not_mask_timeouts() {
        let start = Instant::now();
        let mut clock = PollClock::new(start);
        for seconds in 0..30 {
            assert!(clock.can_expire(start + Duration::from_secs(seconds)));
        }
    }
}
