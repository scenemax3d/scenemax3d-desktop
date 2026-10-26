//! Bounded live diagnostics shared with the settings worker.
use std::{
    cell::RefCell,
    sync::{Arc, Mutex},
};

/// Last known operation stage and a bounded console tail.
#[derive(Clone, Debug, Default)]
pub struct Progress {
    /// Stage-based percentage; not a time estimate.
    pub percent: u8,
    /// Current stage or final result.
    pub stage: String,
    /// Most recent 32 KiB of console output.
    pub log: String,
}
thread_local! {
    static CURRENT: RefCell<Option<Arc<Mutex<Progress>>>> = const { RefCell::new(None) };
}
pub(super) fn attach(progress: Arc<Mutex<Progress>>) {
    CURRENT.with(|slot| *slot.borrow_mut() = Some(progress));
}
pub(super) fn stage(percent: u8, label: &str) {
    CURRENT.with(|slot| {
        if let Some(shared) = slot.borrow().as_ref() {
            let mut p = shared.lock().unwrap_or_else(|e| e.into_inner());
            p.percent = percent;
            p.stage = label.into();
        }
    });
    output(&format!("\n[{label}]\n"));
}
pub(super) fn output(text: &str) {
    CURRENT.with(|slot| {
        if let Some(shared) = slot.borrow().as_ref() {
            let mut p = shared.lock().unwrap_or_else(|e| e.into_inner());
            p.log.extend(
                text.chars()
                    .filter(|c| !c.is_control() || matches!(c, '\n' | '\t'))
                    .take(65536),
            );
            if p.log.len() > 32768 {
                let mut cut = p.log.len() - 32768;
                while !p.log.is_char_boundary(cut) {
                    cut += 1;
                }
                p.log.drain(..cut);
            }
        }
    });
}
pub(super) fn replace_log(text: &str) {
    CURRENT.with(|slot| {
        if let Some(shared) = slot.borrow().as_ref() {
            shared.lock().unwrap_or_else(|e| e.into_inner()).log.clear();
        }
    });
    output(text);
}
