use crate::EditorError;
use std::path::{Path, PathBuf};

/// A monotonically increasing version of a document's text.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DocumentRevision(u64);

/// UTF-8 byte offsets for the anchor and caret. Always clamped to character boundaries.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct Selection {
    /// Fixed end of a selection.
    pub anchor: usize,
    /// Moving caret end of a selection.
    pub focus: usize,
}
impl Selection {
    /// Ordered byte range.
    pub fn range(self) -> std::ops::Range<usize> {
        self.anchor.min(self.focus)..self.anchor.max(self.focus)
    }
}

/// UTF-8 buffer and last saved state. Disk access belongs to services.
#[derive(Debug, Clone)]
pub struct Document {
    path: PathBuf,
    text: String,
    saved_bytes: Vec<u8>,
    saved_text: String,
    crlf: bool,
    bom: bool,
    revision: DocumentRevision,
    saved_version: u64,
    selection: Selection,
    history: crate::history::History,
}
impl Document {
    /// Decode bytes from storage, preserving BOM and newline conventions.
    pub fn from_bytes(path: PathBuf, bytes: Vec<u8>) -> Result<Self, EditorError> {
        let raw = std::str::from_utf8(&bytes)?;
        let bom = raw.starts_with('\u{feff}');
        let crlf = raw.contains("\r\n");
        let text = raw
            .strip_prefix('\u{feff}')
            .unwrap_or(raw)
            .replace("\r\n", "\n");
        Ok(Self {
            path,
            saved_text: text.clone(),
            text,
            saved_bytes: bytes,
            crlf,
            bom,
            revision: DocumentRevision(0),
            saved_version: 0,
            selection: Selection::default(),
            history: crate::history::History::default(),
        })
    }
    /// Canonical path supplied by storage.
    pub fn path(&self) -> &Path {
        &self.path
    }
    /// Follow a completed disk rename without losing edits, selection or undo history.
    pub fn relocate(&mut self, path: PathBuf) {
        self.path = path;
    }
    /// Current text, with CRLF normalized to LF internally.
    pub fn text(&self) -> &str {
        &self.text
    }
    /// Version associated with background jobs.
    pub fn revision(&self) -> DocumentRevision {
        self.revision
    }
    /// Last observed disk bytes for optimistic conflict detection.
    pub fn saved_bytes(&self) -> &[u8] {
        &self.saved_bytes
    }
    /// Whether the buffer differs from its last saved text.
    pub fn is_dirty(&self) -> bool {
        self.text != self.saved_text
    }
    /// Replace text; redundant UI updates do not change its revision.
    pub fn replace_text(&mut self, text: String) {
        let text = if text.contains("\r\n") {
            text.replace("\r\n", "\n")
        } else {
            text
        };
        if self.text != text {
            let after = Selection {
                anchor: text.len(),
                focus: text.len(),
            };
            self.history
                .record(&self.text, &text, self.selection, after);
            self.selection = after;
            self.text = text;
            self.revision.0 = self.revision.0.saturating_add(1);
        }
    }
    /// Continue one editor gesture while keeping a single undo transaction.
    /// Callers must verify no unrelated edit or save occurred since the previous update.
    pub fn replace_text_continuing(&mut self, text: String) {
        if self.text != text {
            let after = Selection {
                anchor: text.len(),
                focus: text.len(),
            };
            self.history
                .record_continuing(&self.text, &text, self.selection, after);
            self.text = text;
            self.selection = after;
            self.revision.0 = self.revision.0.saturating_add(1);
        }
    }
    /// Record a committed widget edit and its resulting caret/selection.
    pub fn edit_text(&mut self, text: String, selection: Selection) {
        let changed = self.text != text;
        self.replace_text(text);
        self.select(selection);
        if changed {
            self.history.selection_after_edit(self.selection);
        }
    }
    /// Monotonic version of the acknowledged disk baseline.
    pub fn saved_version(&self) -> u64 {
        self.saved_version
    }
    /// Current caret and selection, independent of renderer entities.
    pub fn selection(&self) -> Selection {
        self.selection
    }
    /// Update selection without making a source edit.
    pub fn select(&mut self, selection: Selection) {
        let clamp = |value: usize| {
            let mut value = value.min(self.text.len());
            while !self.text.is_char_boundary(value) {
                value -= 1;
            }
            value
        };
        self.selection = Selection {
            anchor: clamp(selection.anchor),
            focus: clamp(selection.focus),
        };
    }
    /// Capture a persistence snapshot without copying undo history.
    pub fn snapshot(&self) -> Self {
        Self {
            path: self.path.clone(),
            text: self.text.clone(),
            saved_bytes: self.saved_bytes.clone(),
            saved_text: self.saved_text.clone(),
            crlf: self.crlf,
            bom: self.bom,
            revision: self.revision,
            saved_version: self.saved_version,
            selection: self.selection,
            history: Default::default(),
        }
    }
    /// Undo one edit transaction; the saved baseline remains unchanged.
    pub fn undo(&mut self) -> bool {
        if let Some(selection) = self.history.undo(&mut self.text) {
            self.selection = selection;
            self.revision.0 = self.revision.0.saturating_add(1);
            true
        } else {
            false
        }
    }
    /// Redo an undone transaction. New edits invalidate the redo branch.
    pub fn redo(&mut self) -> bool {
        if let Some(selection) = self.history.redo(&mut self.text) {
            self.selection = selection;
            self.revision.0 = self.revision.0.saturating_add(1);
            true
        } else {
            false
        }
    }
    /// Encode the current buffer with its original newline and BOM convention.
    pub fn encoded_bytes(&self) -> Vec<u8> {
        let mut text = if self.crlf {
            self.text.replace('\n', "\r\n")
        } else {
            self.text.clone()
        };
        if self.bom {
            text.insert(0, '\u{feff}');
        }
        text.into_bytes()
    }
    /// Apply an ordered, successfully saved snapshot without replacing newer edits.
    /// The storage coordinator must serialize writes and acknowledge each once.
    /// Returns false if the snapshot belongs to another file or is still dirty.
    pub fn acknowledge_saved(&mut self, saved: Document) -> bool {
        if self.path != saved.path || saved.is_dirty() {
            return false;
        }
        self.saved_version = self.saved_version.saturating_add(1);
        self.saved_bytes = saved.saved_bytes;
        self.saved_text = saved.saved_text;
        true
    }
    /// Acknowledge a successful synchronous write with exclusive buffer access.
    /// Storage must call this only after file replacement succeeds.
    pub fn mark_saved(&mut self) {
        self.saved_version = self.saved_version.saturating_add(1);
        self.saved_bytes = self.encoded_bytes();
        self.saved_text.clone_from(&self.text);
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn revisions_track_text_changes_not_redundant_ui_updates() {
        let mut doc = Document::from_bytes("test.code".into(), b"initial".to_vec()).unwrap();
        let initial = doc.revision();
        doc.replace_text("initial".into());
        assert_eq!(doc.revision(), initial);
        doc.replace_text("changed".into());
        assert_ne!(doc.revision(), initial);
        assert!(doc.is_dirty());
        doc.mark_saved();
        assert!(!doc.is_dirty());
    }
    #[test]
    fn returning_to_saved_text_is_clean_but_has_a_new_revision() {
        let mut doc = Document::from_bytes("test.code".into(), b"initial".to_vec()).unwrap();
        let initial = doc.revision();
        doc.replace_text("changed".into());
        doc.replace_text("initial".into());
        assert!(!doc.is_dirty());
        assert_ne!(doc.revision(), initial);
    }

    #[test]
    fn pasted_crlf_text_does_not_duplicate_carriage_returns_on_save() {
        let mut doc = Document::from_bytes("test.code".into(), b"original\r\n".to_vec()).unwrap();
        doc.replace_text("pasted\r\nlines\r\n".into());
        assert_eq!(doc.text(), "pasted\nlines\n");
        assert_eq!(doc.encoded_bytes(), b"pasted\r\nlines\r\n");
    }
}

#[cfg(test)]
mod async_save_tests {
    use super::*;
    #[test]
    fn completed_snapshot_preserves_newer_text_and_next_save_baseline() {
        let mut doc = Document::from_bytes("test.code".into(), b"old".to_vec()).unwrap();
        doc.replace_text("first".into());
        let mut snapshot = doc.clone();
        doc.replace_text("second".into());
        let revision = doc.revision();
        snapshot.mark_saved();
        assert!(doc.acknowledge_saved(snapshot));
        assert_eq!(doc.text(), "second");
        assert_eq!(doc.saved_bytes(), b"first");
        assert_eq!(doc.revision(), revision);
        assert!(doc.is_dirty());
    }
    #[test]
    fn reverting_to_old_disk_text_during_save_is_still_dirty_after_completion() {
        let mut doc = Document::from_bytes("test.code".into(), b"old".to_vec()).unwrap();
        doc.replace_text("first".into());
        let mut snapshot = doc.clone();
        doc.replace_text("old".into());
        snapshot.mark_saved();
        assert!(doc.acknowledge_saved(snapshot));
        assert!(doc.is_dirty());
    }
}
