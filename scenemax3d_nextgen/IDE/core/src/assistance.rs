//! Pure editing assistance for SceneMax source.
use crate::{
    Selection,
    syntax::{SyntaxToken, TokenKind, highlight},
};

/// Newline plus the leading indentation at the selection start. Scope openers
/// add four spaces. A selected range is replaced by the native editor.
pub fn indented_newline(source: &str, selection: Selection) -> String {
    let start = selection.range().start;
    let Some(prefix) = source.get(..start) else {
        return "\n".into();
    };
    let line = prefix.rsplit('\n').next().unwrap_or("");
    let indent = line.len() - line.trim_start_matches([' ', '\t']).len();
    let tokens = highlight(line);
    let code = tokens.iter().rev().find(|t| t.kind != TokenKind::Comment);
    let opens = code.is_some_and(|t| {
        let word = &line[t.range.clone()];
        // The opener must be the final non-comment text, not an earlier keyword.
        let tail = line[t.range.end..].trim_start();
        (tail.is_empty() || tail.starts_with("//"))
            && ((t.kind == TokenKind::Scope
                && ["do", "then", "else"]
                    .iter()
                    .any(|w| word.eq_ignore_ascii_case(w)))
                || (t.kind == TokenKind::Separator && ["{", "[", "("].contains(&word)))
    });
    format!("\n{}{}", &line[..indent], if opens { "    " } else { "" })
}

/// Bidirectional bracket index, sorted by byte position. Strings and comments
/// are excluded by lexical classification. Mismatched nesting is not paired.
pub fn bracket_index(source: &str, tokens: &[SyntaxToken]) -> Vec<(usize, usize)> {
    let mut stack = Vec::new();
    let mut pairs = Vec::new();
    for token in tokens.iter().filter(|t| t.kind == TokenKind::Separator) {
        let Some(value) = source.get(token.range.clone()) else {
            continue;
        };
        match value {
            "(" | "[" | "{" => stack.push((value, token.range.start)),
            ")" | "]" | "}" => {
                if let Some((open, start)) = stack.pop() {
                    if matches!((open, value), ("(", ")") | ("[", "]") | ("{", "}")) {
                        pairs.push((start, token.range.start));
                        pairs.push((token.range.start, start));
                    } else {
                        stack.clear();
                    }
                }
            }
            _ => {}
        }
    }
    pairs.sort_unstable();
    pairs
}

/// Match the bracket immediately before the caret, then the bracket at it.
pub fn bracket_at(index: &[(usize, usize)], caret: usize) -> Option<(usize, usize)> {
    for position in caret
        .checked_sub(1)
        .into_iter()
        .chain(std::iter::once(caret))
    {
        if let Ok(i) = index.binary_search_by_key(&position, |p| p.0) {
            return Some(index[i]);
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;
    fn newline(source: &str) -> String {
        indented_newline(
            source,
            Selection {
                anchor: source.len(),
                focus: source.len(),
            },
        )
    }
    #[test]
    fn indentation_preserves_tabs_and_understands_scope_comments() {
        assert_eq!(newline("\t  sys.print 2"), "\n\t  ");
        assert_eq!(newline("    if ready THEN // note"), "\n        ");
        assert_eq!(newline("    sys.print \"do\""), "\n    ");
        assert_eq!(newline("    // then"), "\n    ");
        assert_eq!(newline("    do other"), "\n    ");
        assert_eq!(newline("  "), "\n  ");
    }
    #[test]
    fn newline_uses_selection_start_and_respects_unicode_boundaries() {
        let text = "    café tail";
        assert_eq!(
            indented_newline(
                text,
                Selection {
                    anchor: text.len(),
                    focus: 9
                }
            ),
            "\n    "
        );
        assert_eq!(
            indented_newline(
                text,
                Selection {
                    anchor: 8,
                    focus: 8
                }
            ),
            "\n"
        );
    }
    #[test]
    fn nested_brackets_ignore_literals_and_comments() {
        let source = "é ([1]) \"(\" // [";
        let index = bracket_index(source, &highlight(source));
        assert_eq!(bracket_at(&index, 3), Some((3, 7)));
        assert_eq!(bracket_at(&index, 6), Some((6, 4)));
        assert_eq!(bracket_at(&index, source.len()), None);
        assert!(bracket_index("([)]", &highlight("([)]")).is_empty());
    }
}
