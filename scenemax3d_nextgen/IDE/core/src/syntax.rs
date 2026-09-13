//! SceneMax lexical colors matching the Java editor; independent of rendering.
use std::ops::Range;
/// Lexical category used by the editor palette.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TokenKind {
    /// Language command or modifier.
    Keyword,
    /// Control flow and scope delimiter.
    Scope,
    /// Built-in object type.
    Type,
    /// Function declaration or built-in function.
    Function,
    /// Numeric or boolean literal.
    Literal,
    /// Quoted string or character.
    String,
    /// Line comment.
    Comment,
    /// Punctuation.
    Separator,
    /// Operator.
    Operator,
    /// Unterminated quoted literal.
    Error,
}
/// A non-overlapping UTF-8 byte range and its lexical category.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SyntaxToken {
    /// Range within the original source.
    pub range: Range<usize>,
    /// Color category.
    pub kind: TokenKind,
}
const SCOPE_WORDS: &[&str] = &[
    "do", "else", "end", "for", "foreach", "if", "switch", "then", "when", "while",
];
const KEYWORDS: &[&str] = &[
    "a",
    "accelerate",
    "accelerating_modifier",
    "add",
    "after",
    "ambient",
    "an",
    "and",
    "angle",
    "animate",
    "animation",
    "append",
    "apply",
    "async",
    "at",
    "attach",
    "audio",
    "backward",
    "belongs",
    "billboard",
    "brake",
    "breaking",
    "bump_modifier",
    "calibrate",
    "call",
    "camera",
    "cast",
    "chase",
    "check",
    "class",
    "clear",
    "cloud",
    "cloudiness",
    "code",
    "collider",
    "collides",
    "collision",
    "color",
    "cols",
    "commands",
    "compression",
    "contains",
    "cool",
    "damping",
    "data",
    "debug",
    "decelerating_modifier",
    "default",
    "delete",
    "depth",
    "detach",
    "direction",
    "directional",
    "distance",
    "down",
    "draw",
    "dungeon",
    "duration",
    "dynamic",
    "each",
    "earthquake_modifier",
    "ease",
    "effects",
    "emissions",
    "engine",
    "error",
    "every",
    "explosion_modifier",
    "exposure",
    "fall_modifier",
    "fighting",
    "file",
    "first_person",
    "flattening",
    "follow",
    "font",
    "forward",
    "frame",
    "frames",
    "from",
    "front",
    "full",
    "get",
    "gravity",
    "group",
    "having",
    "height",
    "hidden",
    "hide",
    "high",
    "hit_modifier",
    "horizontal",
    "horn",
    "hour",
    "http",
    "ignore",
    "in",
    "info",
    "inner",
    "input",
    "intensity",
    "is",
    "java",
    "joints",
    "json",
    "jump",
    "landing_modifier",
    "left",
    "length",
    "light",
    "lights",
    "load",
    "logger",
    "look",
    "looking",
    "loop",
    "low",
    "lumens",
    "material",
    "max",
    "medium",
    "message",
    "min",
    "minimap",
    "mode",
    "modifiers",
    "move",
    "name",
    "near_miss_modifier",
    "new",
    "not",
    "of",
    "offset",
    "once",
    "outline",
    "pause",
    "platformer",
    "play",
    "plugins",
    "point",
    "pop",
    "pos",
    "post",
    "power",
    "preset",
    "pressed",
    "print",
    "probe",
    "protected",
    "push",
    "put",
    "racing",
    "radius",
    "range",
    "ray",
    "rear",
    "receive",
    "record",
    "released",
    "replay",
    "reset",
    "resume",
    "reverse",
    "right",
    "roll",
    "rotate",
    "rotation",
    "rows",
    "rts",
    "run",
    "save",
    "scale",
    "scene",
    "screen",
    "seconds",
    "shadow",
    "shape",
    "shared",
    "shooting_modifier",
    "show",
    "size",
    "sky",
    "solar",
    "sound",
    "speedo",
    "spot",
    "start",
    "static",
    "steer",
    "stiffness",
    "stop",
    "strength",
    "suspension",
    "system",
    "tacho",
    "terrain",
    "texteffect",
    "third_person",
    "times",
    "to",
    "trailing",
    "transitions",
    "turbo",
    "turn",
    "type",
    "ui",
    "up",
    "using",
    "var",
    "vehicle",
    "vertical",
    "volume",
    "wait",
    "warm",
    "water",
    "where",
    "window",
    "wireframe",
    "with",
];
const DATA_TYPES: &[&str] = &[
    "arch",
    "body",
    "box",
    "character",
    "cone",
    "cylinder",
    "floating",
    "hollow",
    "kinematic",
    "model",
    "quad",
    "ragdoll",
    "rigid",
    "skybox",
    "sphere",
    "sprite",
    "stairs",
    "wedge",
];
const FUNCTIONS: &[&str] = &["abs", "function", "return", "rnd", "round"];

/// Scan incomplete source without parsing or changing the document.
/// Comments and quotes take precedence over keyword recognition. Ranges always
/// respect UTF-8 boundaries, including Unicode identifiers and malformed input.
pub fn highlight(source: &str) -> Vec<SyntaxToken> {
    let mut tokens = Vec::new();
    let mut i = 0;
    while i < source.len() {
        let start = i;
        let c = source[i..].chars().next().unwrap_or_default();
        i += c.len_utf8();
        let kind = if source[start..].starts_with("//") {
            i = source[start..]
                .find(['\r', '\n'])
                .map_or(source.len(), |n| start + n);
            Some(TokenKind::Comment)
        } else if c == '"' || c == '\'' {
            let mut escaped = false;
            let mut closed = false;
            while i < source.len() {
                let ch = source[i..].chars().next().unwrap_or_default();
                if ch == '\n' || ch == '\r' {
                    break;
                }
                i += ch.len_utf8();
                if escaped {
                    escaped = false;
                } else if ch == '\\' {
                    escaped = true;
                } else if ch == c {
                    closed = true;
                    break;
                }
            }
            Some(if closed {
                TokenKind::String
            } else {
                TokenKind::Error
            })
        } else if c.is_ascii_digit() {
            let mut dot = false;
            while let Some(ch) = source[i..].chars().next() {
                if ch.is_ascii_digit() {
                    i += 1;
                } else if ch == '.' && !dot {
                    dot = true;
                    i += 1;
                } else {
                    break;
                }
            }
            Some(TokenKind::Literal)
        } else if c.is_alphabetic() || c == '_' {
            while let Some(ch) = source[i..].chars().next() {
                if ch.is_alphanumeric() || ch == '_' {
                    i += ch.len_utf8();
                } else {
                    break;
                }
            }
            let word = source[start..i].to_ascii_lowercase();
            if SCOPE_WORDS.binary_search(&word.as_str()).is_ok() {
                Some(TokenKind::Scope)
            } else if KEYWORDS.binary_search(&word.as_str()).is_ok() {
                Some(TokenKind::Keyword)
            } else if DATA_TYPES.binary_search(&word.as_str()).is_ok() {
                Some(TokenKind::Type)
            } else if FUNCTIONS.binary_search(&word.as_str()).is_ok() {
                Some(TokenKind::Function)
            } else if ["true", "false", "on", "off"].contains(&word.as_str()) {
                Some(TokenKind::Literal)
            } else {
                None
            }
        } else if "{}()[],:;.".contains(c) {
            Some(TokenKind::Separator)
        } else if "+-*=<>!&|/%".contains(c) {
            Some(TokenKind::Operator)
        } else {
            None
        };
        if let Some(kind) = kind {
            tokens.push(SyntaxToken {
                range: start..i,
                kind,
            });
        }
    }
    tokens
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn java_categories_and_case_insensitive_words() {
        let source = "IF shape is a sphere then print abs(3.5) true end";
        let tokens = highlight(source);
        assert_eq!(tokens[0].kind, TokenKind::Scope);
        assert!(
            tokens
                .iter()
                .any(|t| &source[t.range.clone()] == "sphere" && t.kind == TokenKind::Type)
        );
        assert!(
            tokens
                .iter()
                .any(|t| &source[t.range.clone()] == "abs" && t.kind == TokenKind::Function)
        );
        assert!(
            tokens
                .iter()
                .any(|t| &source[t.range.clone()] == "3.5" && t.kind == TokenKind::Literal)
        );
    }
    #[test]
    fn quotes_comments_and_unicode_have_safe_ranges() {
        let source = "var café = \"hi\"";
        for token in highlight(source) {
            assert!(source.get(token.range).is_some());
        }
        let source = r#"print "if // café \" still" // end"#;
        let tokens = highlight(source);
        assert_eq!(tokens[1].kind, TokenKind::String);
        assert_eq!(tokens.last().unwrap().kind, TokenKind::Comment);
        assert_eq!(&source[tokens.last().unwrap().range.clone()], "// end");
    }
    #[test]
    fn unterminated_quotes_recover_on_the_next_line() {
        let tokens = highlight("\"unfinished\nprint 4");
        assert_eq!(tokens[0].kind, TokenKind::Error);
        assert_eq!(tokens[1].kind, TokenKind::Keyword);
        assert_eq!(tokens[2].kind, TokenKind::Literal);
    }
}
