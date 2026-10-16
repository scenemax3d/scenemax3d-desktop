//! Explicit animation frame endpoints (indices or percentages).
use super::ParseError;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct FrameEndpoint {
    pub value: f32,
    pub percent: bool,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct AnimationFrameRange {
    pub start: FrameEndpoint,
    pub end: FrameEndpoint,
}

impl AnimationFrameRange {
    /// Stable identity that survives the runtime's animation-name normalization.
    pub fn key(self) -> String {
        format!(
            "[frames{}{:08x}to{}{:08x}]",
            if self.start.percent { "p" } else { "f" },
            self.start.value.to_bits(),
            if self.end.percent { "p" } else { "f" },
            self.end.value.to_bits()
        )
    }
    pub fn suffix(self) -> String {
        format!(
            "[{}{}-{}{}]",
            self.start.value,
            if self.start.percent { "%" } else { "" },
            self.end.value,
            if self.end.percent { "%" } else { "" }
        )
    }
}

/// Encode names as bytes so punctuation cannot collide under name normalization.
pub fn selector_key(range: Option<AnimationFrameRange>, name: Option<&str>) -> String {
    if let Some(name) = name {
        let bytes: String = name
            .to_lowercase()
            .as_bytes()
            .iter()
            .map(|b| format!("{b:02x}"))
            .collect();
        format!("[named{bytes}]")
    } else {
        range.map(|r| r.key()).unwrap_or_default()
    }
}

pub fn selector_suffix(range: Option<AnimationFrameRange>, name: Option<&str>) -> String {
    name.map(|name| format!("[\"{name}\"]"))
        .unwrap_or_else(|| range.map(|r| r.suffix()).unwrap_or_default())
}

pub(super) fn parse_name(text: &str) -> Result<(Option<String>, &str), ParseError> {
    let text = text.trim_start();
    let Some(inner) = text.strip_prefix('[').map(str::trim_start) else {
        return Ok((None, text));
    };
    let Some(quoted) = inner.strip_prefix('"') else {
        return Ok((None, text));
    };
    let Some(end) = quoted.find('"') else {
        return Err(ParseError::InvalidNumber(text.into()));
    };
    let name = quoted[..end].trim();
    let Some(rest) = quoted[end + 1..].trim_start().strip_prefix(']') else {
        return Err(ParseError::InvalidNumber(text.into()));
    };
    if name.is_empty() || rest.trim_start().starts_with('[') {
        return Err(ParseError::InvalidNumber(text.into()));
    }
    Ok((Some(name.into()), rest))
}

pub(super) fn parse(text: &str) -> Result<(Option<AnimationFrameRange>, &str), ParseError> {
    let text = text.trim_start();
    let Some(inner) = text.strip_prefix('[') else {
        return Ok((None, text));
    };
    let Some(end) = inner.find(']') else {
        return Err(ParseError::InvalidNumber(text.into()));
    };
    let raw = &inner[..end];
    let endpoint = |s: &str| -> Option<FrameEndpoint> {
        let s = s.trim();
        let percent = s.ends_with('%');
        let value = s
            .strip_suffix('%')
            .unwrap_or(s)
            .trim()
            .parse::<f32>()
            .ok()?;
        value
            .is_finite()
            .then_some(FrameEndpoint { value, percent })
    };
    // Try separator positions so negative endpoints and exponent signs work.
    for (i, c) in raw.char_indices() {
        if c == '-' && i > 0 {
            if let (Some(start), Some(finish)) = (endpoint(&raw[..i]), endpoint(&raw[i + 1..])) {
                return Ok((
                    Some(AnimationFrameRange { start, end: finish }),
                    &inner[end + 1..],
                ));
            }
        }
    }
    Err(ParseError::InvalidNumber(raw.into()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{Statement, parse_program};
    #[test]
    fn direct_ranges_preserve_names_flags_and_endpoint_units() {
        for source in [
            "actor.motion[1.5-75%] at speed of rate async",
            "actor.\"Long take\" [1.5 - 75%] at speed of rate loop",
        ] {
            let p = parse_program(source).unwrap();
            let Statement::Animate(a) = &p.statements[0] else {
                panic!();
            };
            let r = a.frame_range.unwrap();
            assert_eq!(
                r.start,
                FrameEndpoint {
                    value: 1.5,
                    percent: false
                }
            );
            assert_eq!(
                r.end,
                FrameEndpoint {
                    value: 75.,
                    percent: true
                }
            );
            assert!(!a.blocking);
            assert!(a.clip == "motion" || a.clip == "Long take");
            assert_eq!(a.speed_value, crate::AssignmentValue::Symbol("rate".into()));
        }
        for text in ["[0-NaN]", "[0-50%%]", "[0-]", "[1-2", "[\"named\"]"] {
            assert!(parse(text).is_err(), "{text}");
        }
        assert_eq!(parse("[-2-1e-2]").unwrap().0.unwrap().end.value, 0.01);
        assert_ne!(
            parse("[1-2]").unwrap().0.unwrap().key(),
            parse("[1%-2%]").unwrap().0.unwrap().key()
        );
        assert_ne!(
            parse("[1.5-2]").unwrap().0.unwrap().key(),
            parse("[15-2]").unwrap().0.unwrap().key()
        );
    }
}

#[cfg(test)]
mod named_tests {
    use super::*;
    #[test]
    fn named_selector_preserves_clip_and_flags_without_identity_collisions() {
        let program =
            crate::parse_program("actor.\"Long take\" [ \" Section-A \" ] at speed of rate async")
                .unwrap();
        let crate::Statement::Animate(a) = &program.statements[0] else {
            panic!();
        };
        assert_eq!(a.clip, "Long take");
        assert_eq!(a.frame_range_name.as_deref(), Some("Section-A"));
        assert!(a.frame_range.is_none());
        assert!(!a.blocking);
        assert_eq!(a.speed_value, crate::AssignmentValue::Symbol("rate".into()));
        assert_eq!(
            selector_key(None, Some("SECTION-A")),
            selector_key(None, Some("section-a"))
        );
        assert_ne!(
            selector_key(None, Some("section-a")),
            selector_key(None, Some("sectiona"))
        );
        for text in [
            "actor.take[\"\"]",
            "actor.take[\"missing]",
            "actor.take[\"name\" trailing]",
            "actor.take[\"name\"][1-2]",
            "actor.take[\"a\"][\"b\"]",
        ] {
            assert!(crate::parse_program(text).is_err(), "{text}");
        }
    }
}

#[cfg(test)]
mod controller_tests {
    use crate::*;
    #[test]
    fn controller_ranges_and_speed_are_retained_and_chains_do_not_truncate() {
        for suffix in ["[1.5-75%]", "[\"Section A\"]", ""] {
            let source =
                format!("control = animation actor.\"Long take\"{suffix} at speed of rate");
            let p = parse_program(&source).unwrap();
            let Statement::Assignment(AssignmentStatement {
                value: AssignmentValue::AnimationController(c),
                ..
            }) = &p.statements[0]
            else {
                panic!();
            };
            assert_eq!(c.target, "actor");
            assert_eq!(c.clip, "Long take");
            assert_eq!(
                c.animation.speed_value,
                AssignmentValue::Symbol("rate".into())
            );
            assert!(c.animation.blocking);
            assert_eq!(
                animation_range::selector_suffix(
                    c.animation.frame_range,
                    c.animation.frame_range_name.as_deref()
                ),
                suffix
            );
        }
        for source in [
            "control = animation actor.\"Long take\" then then other",
            "control = animation actor.take then then other",
            "control = animation actor.take loop",
            "control = animation actor.take[\"\"]",
        ] {
            assert!(parse_program(source).is_err(), "{source}");
        }
        assert!(parse_program("control = animation actor.\"then loop async\"").is_ok());
    }
}

/// Split only top-level `then` tokens: names and speed expressions may contain it.
pub(super) fn controller_segments(text: &str) -> Result<Vec<&str>, ParseError> {
    let mut quote = false;
    let mut depth = 0i32;
    let mut start = 0;
    let mut parts = Vec::new();
    let bytes = text.as_bytes();
    for (i, c) in text.char_indices() {
        if c == '"' {
            quote = !quote;
            continue;
        }
        if quote {
            continue;
        }
        match c {
            '[' | '(' => depth += 1,
            ']' | ')' => depth -= 1,
            _ => {}
        }
        if depth < 0 {
            return Err(ParseError::InvalidNumber(text.into()));
        }
        if depth != 0 {
            continue;
        }
        for word in ["then", "loop", "async"] {
            if text[i..]
                .get(..word.len())
                .is_some_and(|s| s.eq_ignore_ascii_case(word))
                && (i == 0 || bytes[i - 1].is_ascii_whitespace())
                && (i + word.len() == bytes.len() || bytes[i + word.len()].is_ascii_whitespace())
            {
                if word != "then" {
                    return Err(ParseError::InvalidNumber(
                        "Controller loop/async modifiers are unsupported".into(),
                    ));
                }
                let part = text[start..i].trim();
                if part.is_empty() {
                    return Err(ParseError::InvalidNumber(text.into()));
                }
                parts.push(part);
                start = i + word.len();
            }
        }
    }
    let last = text[start..].trim();
    if quote || depth != 0 || last.is_empty() {
        return Err(ParseError::InvalidNumber(text.into()));
    }
    parts.push(last);
    Ok(parts)
}

#[cfg(test)]
mod chain_tests {
    use crate::*;
    #[test]
    fn chains_preserve_quoted_then_ranges_and_per_segment_speed() {
        let p=parse_program("c = animation actor.\"then take\"[\"then section\"] at speed of a + 1 THEN \"last take\"[25%-75%] at speed of b").unwrap();
        let Statement::Assignment(a) = &p.statements[0] else {
            panic!()
        };
        let AssignmentValue::AnimationController(c) = &a.value else {
            panic!()
        };
        assert_eq!(c.clip, "then take");
        assert_eq!(
            c.animation.frame_range_name.as_deref(),
            Some("then section")
        );
        assert_eq!(c.subsequent.len(), 1);
        assert_eq!(c.subsequent[0].target, "actor");
        assert_eq!(c.subsequent[0].clip, "last take");
        assert!(c.subsequent[0].frame_range.unwrap().start.percent);
        for invalid in [
            "c = animation actor.take then",
            "c = animation actor.take then then last",
            "c = animation actor.take then last loop",
        ] {
            assert!(parse_program(invalid).is_err());
        }
    }
}
