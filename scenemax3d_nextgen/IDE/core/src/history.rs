use crate::Selection;
use std::collections::VecDeque;
const LIMIT: usize = 8 * 1024 * 1024;
#[derive(Debug, Clone)]
struct Edit {
    start: usize,
    removed: String,
    inserted: String,
    before: Selection,
    after: Selection,
}
#[derive(Debug, Clone, Default)]
pub(crate) struct History {
    undo: VecDeque<Edit>,
    redo: Vec<Edit>,
    bytes: usize,
}
impl History {
    pub(crate) fn record(&mut self, old: &str, new: &str, before: Selection, after: Selection) {
        let start = old
            .chars()
            .zip(new.chars())
            .take_while(|(a, b)| a == b)
            .map(|(c, _)| c.len_utf8())
            .sum::<usize>();
        let suffix = old[start..]
            .chars()
            .rev()
            .zip(new[start..].chars().rev())
            .take_while(|(a, b)| a == b)
            .map(|(c, _)| c.len_utf8())
            .sum::<usize>();
        let edit = Edit {
            start,
            removed: old[start..old.len() - suffix].into(),
            inserted: new[start..new.len() - suffix].into(),
            before,
            after,
        };
        for e in self.redo.drain(..) {
            self.bytes -= e.removed.len() + e.inserted.len();
        }
        self.bytes += edit.removed.len() + edit.inserted.len();
        self.undo.push_back(edit);
        while (self.bytes > LIMIT && self.undo.len() > 1) || self.undo.len() > 1000 {
            if let Some(e) = self.undo.pop_front() {
                self.bytes -= e.removed.len() + e.inserted.len();
            } else {
                break;
            }
        }
    }
    pub(crate) fn record_continuing(
        &mut self,
        old: &str,
        new: &str,
        before: Selection,
        after: Selection,
    ) {
        if self.redo.is_empty()
            && let Some(previous) = self.undo.pop_back()
        {
            let mut original = old.to_owned();
            original.replace_range(
                previous.start..previous.start + previous.inserted.len(),
                &previous.removed,
            );
            self.bytes -= previous.removed.len() + previous.inserted.len();
            if original != new {
                self.record(&original, new, previous.before, after);
            }
        } else {
            self.record(old, new, before, after);
        }
    }
    pub(crate) fn selection_after_edit(&mut self, selection: Selection) {
        if let Some(edit) = self.undo.back_mut() {
            edit.after = selection;
        }
    }
    pub(crate) fn undo(&mut self, text: &mut String) -> Option<Selection> {
        let e = self.undo.pop_back()?;
        text.replace_range(e.start..e.start + e.inserted.len(), &e.removed);
        let selection = e.before;
        self.redo.push(e);
        Some(selection)
    }
    pub(crate) fn redo(&mut self, text: &mut String) -> Option<Selection> {
        let e = self.redo.pop()?;
        text.replace_range(e.start..e.start + e.removed.len(), &e.inserted);
        let selection = e.after;
        self.undo.push_back(e);
        Some(selection)
    }
}
