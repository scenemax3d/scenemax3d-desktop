//! Lossless folding projections: hidden text remains in the document, never in a placeholder.
use crate::{
    Selection,
    syntax::{TokenKind, highlight},
};
use std::ops::Range;

/// A complete multiline block whose body may be hidden.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Region {
    /// Opening token's byte offset.
    pub start: usize,
    /// Body lines, excluding the opening and closing lines.
    pub body: Range<usize>,
}

/// Match nested braces and do/end-do scopes, excluding comments and strings.
pub fn regions(source: &str) -> Vec<Region> {
    let tokens = highlight(source);
    let tokens: Vec<_> = tokens
        .iter()
        .filter(|t| {
            !matches!(
                t.kind,
                TokenKind::Comment | TokenKind::String | TokenKind::Error
            )
        })
        .collect();
    let mut stack = Vec::new();
    let mut result = Vec::new();
    let mut i = 0;
    while i < tokens.len() {
        let token = tokens[i];
        let word = source[token.range.clone()].to_ascii_lowercase();
        let close = if word == "end"
            && tokens.get(i + 1).is_some_and(|t| {
                source[t.range.clone()].eq_ignore_ascii_case("do")
                    && source[token.range.end..t.range.start]
                        .chars()
                        .all(char::is_whitespace)
            }) {
            i += 1;
            Some("do")
        } else if word == "}" {
            Some("{")
        } else {
            None
        };
        if let Some(close) = close {
            if stack.last().is_some_and(|(kind, _)| *kind == close) {
                let (_, start) = stack.pop().unwrap();
                let body_start = source[start..].find('\n').map(|n| start + n + 1);
                let body_end = source[..token.range.start].rfind('\n').map_or(0, |n| n + 1);
                if let Some(body_start) = body_start
                    && body_start < body_end
                {
                    result.push(Region {
                        start,
                        body: body_start..body_end,
                    });
                }
            }
        } else if word == "do" || word == "{" {
            stack.push((if word == "do" { "do" } else { "{" }, token.range.start));
        }
        i += 1;
    }
    result.sort_by_key(|r| r.start);
    // One gutter control per opening line (outer block wins).
    result.dedup_by_key(|r| source[..r.start].bytes().filter(|b| *b == b'\n').count());
    result
}

/// Source plus its visible projection and collapsed body ranges.
#[derive(Clone, Default, Debug)]
pub struct Projection {
    /// Full source, including folded text.
    pub source: String,
    /// Text presented to the native editor.
    pub visible: String,
    /// Collapsed bodies in source coordinates.
    pub collapsed: Vec<Range<usize>>,
}
impl Projection {
    /// Start with an expanded document.
    pub fn new(source: String) -> Self {
        Self {
            visible: source.clone(),
            source,
            collapsed: vec![],
        }
    }
    /// Toggle a body without editing source.
    pub fn toggle(&mut self, body: Range<usize>) {
        if let Some(i) = self.collapsed.iter().position(|r| *r == body) {
            self.collapsed.remove(i);
        } else {
            self.collapsed.push(body);
        }
        self.rebuild();
    }
    /// Only outermost hidden ranges affect the projection.
    pub fn hidden(&self) -> Vec<Range<usize>> {
        let mut sorted = self.collapsed.clone();
        sorted.sort_by_key(|r| (r.start, std::cmp::Reverse(r.end)));
        let mut result: Vec<Range<usize>> = Vec::new();
        for r in sorted {
            if result.last().is_none_or(|p| r.start >= p.end) {
                result.push(r);
            }
        }
        result
    }
    fn rebuild(&mut self) {
        let mut start = 0;
        let mut visible = String::new();
        for r in self.hidden() {
            visible.push_str(&self.source[start..r.start]);
            start = r.end;
        }
        visible.push_str(&self.source[start..]);
        self.visible = visible;
    }
    /// Map a visible caret to full source.
    pub fn to_source(&self, position: usize) -> usize {
        self.source_offset(position, true)
    }
    fn source_offset(&self, position: usize, right: bool) -> usize {
        let mut skipped = 0;
        for r in self.hidden() {
            let at = r.start - skipped;
            if position > at || (position == at && right) {
                skipped += r.len();
            } else {
                break;
            }
        }
        (position + skipped).min(self.source.len())
    }
    /// Map source to the visible text, clamping hidden positions to their fold boundary.
    pub fn to_visible(&self, position: usize) -> usize {
        let mut skipped = 0;
        for r in self.hidden() {
            if position < r.start {
                break;
            }
            if position < r.end {
                return r.start - skipped;
            }
            skipped += r.len();
        }
        position.saturating_sub(skipped).min(self.visible.len())
    }
    /// Reveal the body containing a source caret, for search/go-to-line navigation.
    pub fn reveal(&mut self, position: usize) {
        self.collapsed.retain(|r| !r.contains(&position));
        self.rebuild();
    }
    /// Apply native edits to the projection while preserving untouched hidden text.
    /// Returned selection uses full-document byte offsets.
    pub fn edit(&mut self, text: &str, selection: Selection) -> Selection {
        if text != self.visible {
            let mut prefix = self
                .visible
                .bytes()
                .zip(text.bytes())
                .take_while(|(a, b)| a == b)
                .count();
            while !self.visible.is_char_boundary(prefix) || !text.is_char_boundary(prefix) {
                prefix -= 1;
            }
            let mut suffix = self.visible[prefix..]
                .bytes()
                .rev()
                .zip(text[prefix..].bytes().rev())
                .take_while(|(a, b)| a == b)
                .count();
            while !self.visible.is_char_boundary(self.visible.len() - suffix)
                || !text.is_char_boundary(text.len() - suffix)
            {
                suffix -= 1;
            }
            let old_end = self.visible.len() - suffix;
            let start = self.to_source(prefix);
            let end = if old_end == prefix {
                start
            } else {
                self.source_offset(old_end, false).max(start)
            };
            let insert = &text[prefix..text.len() - suffix];
            let shift = insert.len() as isize - (end - start) as isize;
            self.source.replace_range(start..end, insert);
            self.collapsed.retain_mut(|r| {
                if r.end <= start {
                    true
                } else if r.start >= end {
                    r.start = r.start.checked_add_signed(shift).unwrap();
                    r.end = r.end.checked_add_signed(shift).unwrap();
                    true
                } else {
                    false
                }
            });
            let selection = Selection {
                anchor: self.to_source(selection.anchor),
                focus: self.to_source(selection.focus),
            };
            // Invalidated delimiters must not leave invisible source behind.
            let valid = regions(&self.source);
            self.collapsed
                .retain(|r| valid.iter().any(|f| f.body == *r));
            self.rebuild();
            return selection;
        }
        Selection {
            anchor: self.to_source(selection.anchor),
            focus: self.to_source(selection.focus),
        }
    }
    /// Source line number for each displayed line (one-based).
    pub fn lines(&self) -> Vec<usize> {
        let starts: Vec<_> = std::iter::once(0)
            .chain(self.source.match_indices('\n').map(|(i, _)| i + 1))
            .collect();
        std::iter::once(0)
            .chain(self.visible.match_indices('\n').map(|(i, _)| i + 1))
            .map(|i| starts.partition_point(|s| *s <= self.to_source(i)))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn pairs_nested_do_and_braces_ignoring_strings_comments_and_unclosed_blocks() {
        let source = "DO\n // end do }\n log \"end do {\"\n {\n  café\n }\n do\n  x\n end DO\nEND do\ndo\n unfinished";
        let found = regions(source);
        assert_eq!(found.len(), 3);
        let mut p = Projection::new(source.into());
        p.toggle(found[2].body.clone());
        p.toggle(found[0].body.clone());
        assert_eq!(p.source, source);
        assert!(p.visible.starts_with("DO\nEND do"));
        p.toggle(found[0].body.clone());
        assert!(!p.visible.contains("  x"));
        p.toggle(found[2].body.clone());
        assert_eq!(p.visible, source);
    }
    #[test]
    fn edits_and_navigation_keep_folded_source_and_offsets() {
        let mut p = Projection::new("do\n  café\nend do\nprint 1".into());
        p.toggle(regions(&p.source)[0].body.clone());
        assert_eq!(p.visible, "do\nend do\nprint 1");
        assert_eq!(p.lines(), vec![1, 3, 4]);
        let text = p.visible.replace("print 1", "print 20");
        let caret = p.edit(
            &text,
            Selection {
                anchor: text.len(),
                focus: text.len(),
            },
        );
        assert_eq!(p.source, "do\n  café\nend do\nprint 20");
        assert_eq!(caret.focus, p.source.len());
        let text = format!("// α\n{}", p.visible);
        p.edit(
            &text,
            Selection {
                anchor: 0,
                focus: 0,
            },
        );
        assert_eq!(p.collapsed.len(), 1);
        p.reveal(p.source.find("café").unwrap());
        assert!(p.collapsed.is_empty());
        assert_eq!(p.visible, p.source);
    }
}
