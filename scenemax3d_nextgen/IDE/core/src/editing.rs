use crate::{Document, Selection};
impl Document {
    /// Find a literal, case-sensitive match with wraparound from the selection.
    pub fn find(&mut self, needle: &str, backwards: bool) -> bool {
        if needle.is_empty() {
            return false;
        }
        let range = self.selection().range();
        let found = if backwards {
            self.text()[..range.start]
                .rfind(needle)
                .or_else(|| self.text().rfind(needle))
        } else {
            self.text()[range.end..]
                .find(needle)
                .map(|i| i + range.end)
                .or_else(|| self.text().find(needle))
        };
        if let Some(start) = found {
            self.select(Selection {
                anchor: start,
                focus: start + needle.len(),
            });
            true
        } else {
            false
        }
    }
    /// Replace the selected exact match, then find the next occurrence.
    pub fn replace_match(&mut self, needle: &str, replacement: &str) -> bool {
        if needle.is_empty() {
            return false;
        }
        let range = self.selection().range();
        if &self.text()[range.clone()] != needle {
            return self.find(needle, false);
        }
        let mut text = self.text().to_owned();
        text.replace_range(range.clone(), replacement);
        self.replace_text(text);
        let end = range.start + replacement.len();
        self.select(Selection {
            anchor: end,
            focus: end,
        });
        self.find(needle, false);
        true
    }
    /// Replace all literal matches as one undoable edit, bounded to 8 MiB.
    pub fn replace_all(&mut self, needle: &str, replacement: &str) -> Result<usize, &'static str> {
        if needle.is_empty() {
            return Err("Enter text to find");
        }
        let count = self.text().matches(needle).count();
        let size = self
            .text()
            .len()
            .saturating_sub(count.saturating_mul(needle.len()))
            .saturating_add(count.saturating_mul(replacement.len()));
        if size > 8 * 1024 * 1024 {
            return Err("Replacement would exceed the 8 MiB editing limit");
        }
        self.replace_text(self.text().replace(needle, replacement));
        Ok(count)
    }
    /// Move to a one-based line, clamping requests past EOF to the last line.
    pub fn go_to_line(&mut self, line: usize) {
        let start = if line <= 1 {
            0
        } else {
            self.text()
                .match_indices('\n')
                .take(line - 1)
                .map(|(i, _)| i + 1)
                .last()
                .unwrap_or(0)
        };
        self.select(Selection {
            anchor: start,
            focus: start,
        });
    }
    /// One-based caret line and Unicode scalar column.
    pub fn line_column(&self) -> (usize, usize) {
        let prefix = &self.text()[..self.selection().focus];
        (
            prefix.bytes().filter(|b| *b == b'\n').count() + 1,
            prefix.rsplit('\n').next().unwrap_or("").chars().count() + 1,
        )
    }
    /// Indent or outdent complete selected lines using four spaces.
    pub fn indent(&mut self, outdent: bool) {
        self.transform_lines(outdent, false);
    }
    /// Toggle line comments for the selected lines as one transaction.
    pub fn toggle_comment(&mut self) {
        self.transform_lines(false, true);
    }
    fn transform_lines(&mut self, outdent: bool, comment: bool) {
        let range = self.selection().range();
        let start = self.text()[..range.start].rfind('\n').map_or(0, |i| i + 1);
        let end = if range.end > range.start && self.text()[..range.end].ends_with('\n') {
            range.end - 1
        } else {
            self.text()[range.end..]
                .find('\n')
                .map_or(self.text().len(), |i| range.end + i)
        };
        let selected = &self.text()[start..end];
        let uncomment = !selected.is_empty()
            && comment
            && selected
                .lines()
                .all(|line| line.trim_start().starts_with("//"));
        let transformed = selected
            .split('\n')
            .map(|line| {
                if comment {
                    if uncomment {
                        let indent = line.len() - line.trim_start().len();
                        format!(
                            "{}{}",
                            &line[..indent],
                            line[indent + 2..]
                                .strip_prefix(' ')
                                .unwrap_or(&line[indent + 2..])
                        )
                    } else {
                        format!("// {line}")
                    }
                } else if outdent {
                    if let Some(rest) = line.strip_prefix('\t') {
                        rest.to_owned()
                    } else {
                        line[line.bytes().take(4).take_while(|b| *b == b' ').count()..].to_owned()
                    }
                } else {
                    format!("    {line}")
                }
            })
            .collect::<Vec<_>>()
            .join("\n");
        let mut text = self.text().to_owned();
        text.replace_range(start..end, &transformed);
        self.replace_text(text);
        self.select(Selection {
            anchor: start,
            focus: start + transformed.len(),
        });
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn doc(text: &str) -> Document {
        Document::from_bytes("main".into(), text.as_bytes().to_vec()).unwrap()
    }
    #[test]
    fn unicode_edit_undo_redo_and_branching_preserve_baseline() {
        let mut d = doc("שלום 🦀");
        d.replace_text("שלום world 🦀".into());
        d.mark_saved();
        assert!(d.undo());
        assert_eq!(d.text(), "שלום 🦀");
        assert!(d.is_dirty());
        assert!(d.redo());
        assert!(!d.is_dirty());
        d.undo();
        d.replace_text("new".into());
        assert!(!d.redo());
    }
    #[test]
    fn empty_script_can_be_commented_and_undone() {
        let mut d = doc("");
        d.toggle_comment();
        assert_eq!(d.text(), "// ");
        d.undo();
        assert_eq!(d.text(), "");
    }
    #[test]
    fn literal_find_wrap_replace_all_and_undo() {
        let mut d = doc("α one α two");
        assert!(d.find("α", false));
        assert_eq!(d.selection().range(), 0..2);
        assert!(d.find("α", false));
        assert_eq!(d.selection().range(), 7..9);
        assert_eq!(d.replace_all("α", "β").unwrap(), 2);
        d.undo();
        assert_eq!(d.text(), "α one α two");
    }
    #[test]
    fn line_commands_are_single_undo_transactions() {
        let mut d = doc("one\n  two\nthree");
        d.select(Selection {
            anchor: 0,
            focus: 10,
        });
        d.indent(false);
        assert_eq!(d.text(), "    one\n      two\nthree");
        d.undo();
        assert_eq!(d.text(), "one\n  two\nthree");
        d.go_to_line(3);
        assert_eq!(d.line_column(), (3, 1));
    }
}
