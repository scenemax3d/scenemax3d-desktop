//! SceneMax completion candidates, independent of UI and storage.
use crate::syntax::{TokenKind, highlight};
use std::{collections::BTreeSet, ops::Range};
/// One completion suggestion.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Completion {
    /// User-visible name.
    pub label: String,
    /// Text inserted in place of the typed prefix.
    pub insert: String,
    /// Source/category shown in the popup.
    pub category: &'static str,
}
/// Suggestions for one exact caret position.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Completions {
    /// UTF-8 range of the prefix to replace.
    pub range: Range<usize>,
    /// Ranked, bounded suggestions.
    pub items: Vec<Completion>,
}
const KEYWORDS: &[&str] = &[
    "if",
    "else",
    "do",
    "end",
    "then",
    "when",
    "for",
    "foreach",
    "while",
    "return",
    "stop",
    "switch",
    "Var",
    "Shared",
    "Function",
    "is a",
    "is an",
    "Sphere",
    "Box",
    "Cylinder",
    "Quad",
    "Hollow",
    "Sprite",
    "Model",
    "Dynamic",
    "Static",
    "Collider",
    "Vehicle",
    "Belongs",
    "Group",
    "Move",
    "Rotate",
    "Scale",
    "Animate",
    "Play",
    "Hide",
    "Show",
    "Delete",
    "Turn",
    "Roll",
    "Look",
    "Pos",
    "Stop",
    "Push",
    "Pop",
    "Clear",
    "Print",
    "Accelerate",
    "Steer",
    "Brake",
    "Turbo",
    "Reset",
    "Attach",
    "Detach",
    "Record",
    "Replay",
    "Run",
    "Call",
    "Async",
    "Having",
    "And",
    "In",
    "At",
    "From",
    "To",
    "With",
    "Speed",
    "Of",
    "Loop",
    "Once",
    "Every",
    "Mass",
    "Velocity",
    "Angular",
    "Restitution",
    "Friction",
    "Material",
    "Radius",
    "Height",
    "Size",
    "Gravity",
    "Shadow",
    "Mode",
    "Hidden",
    "Collision",
    "Shape",
    "Calibrate",
    "Joints",
    "Data",
    "Camera",
    "Chase",
    "Follow",
    "Trailing",
    "Dungeon",
    "Default",
    "Fighting",
    "third_person",
    "first_person",
    "racing",
    "platformer",
    "rts",
    "Modifiers",
    "Apply",
    "hit_modifier",
    "fall_modifier",
    "shooting_modifier",
    "accelerating_modifier",
    "decelerating_modifier",
    "bump_modifier",
    "landing_modifier",
    "earthquake_modifier",
    "explosion_modifier",
    "near_miss_modifier",
    "Vertical",
    "Horizontal",
    "Rotation",
    "Max",
    "Min",
    "Distance",
    "Damping",
    "Type",
    "SkyBox",
    "Solar",
    "System",
    "Terrain",
    "Water",
    "Cloud",
    "Flattening",
    "Cloudiness",
    "Hour",
    "Depth",
    "Strength",
    "Audio",
    "Sound",
    "Volume",
    "Logger",
    "info",
    "debug",
    "error",
    "Lights",
    "Light",
    "Probe",
    "directional",
    "point",
    "spot",
    "sky",
    "ambient",
    "Direction",
    "Intensity",
    "Lumens",
    "Range",
    "Preset",
    "Exposure",
    "Low",
    "Medium",
    "High",
    "Warm",
    "Cool",
    "Screen",
    "Scene",
    "Pause",
    "Resume",
    "Full",
    "Window",
    "Effects",
    "Minimap",
    "Using",
    "Code",
    "Add",
    "Wait",
    "Seconds",
    "For",
    "Is",
    "Pressed",
    "Released",
    "Character",
    "RagDoll",
    "Kinematic",
    "Floating",
    "Rigid",
    "Body",
    "Engine",
    "Power",
    "Breaking",
    "Suspension",
    "Compression",
    "Stiffness",
    "Length",
    "Front",
    "Rear",
    "Input",
    "Reverse",
    "Horn",
    "Forward",
    "Backward",
    "Left",
    "Right",
    "Up",
    "Down",
    "Billboard",
    "Wireframe",
    "Info",
    "Outline",
    "Offset",
    "Duration",
    "Emissions",
    "Start",
    "End",
    "Draw",
    "Frames",
    "Frame",
    "Append",
    "Color",
    "Font",
    "Cast",
    "Receive",
    "Debug",
    "On",
    "Off",
    "Protected",
    "True",
    "False",
    "New",
    "Class",
    "Save",
    "After",
    "Collides",
    "Ray",
    "Check",
    "File",
    "Name",
    "Contains",
    "Each",
    "Where",
    "Http",
    "Get",
    "Post",
    "Put",
    "UI",
    "Load",
    "Message",
    "TextEffect",
    "Ease",
    "Java",
    "Attach",
    "Plugins",
    "Animation",
    "Rows",
    "Cols",
    "Times",
    "Inner",
    "Transitions",
    "Commands",
    "Ignore",
    "Jump",
    "Speedo",
    "Tacho",
    "Angle",
    "JSON",
    "Looking",
    "Not",
];
const BUILTIN_FUNCTIONS: &[&str] = &["Distance", "Angle", "Jump", "abs", "rnd", "round"];
const COLORS: &[&str] = &[
    "Red",
    "Green",
    "Blue",
    "White",
    "Black",
    "Brown",
    "Cyan",
    "Gray",
    "DarkGray",
    "LightGray",
    "Magenta",
    "Orange",
    "Pink",
    "Yellow",
    "Warm",
    "Cool",
];
const EFFECTS: &[&str] = &[
    "Flash",
    "Explosion",
    "Debris",
    "Spark",
    "Smoketrail",
    "Shockwave",
    "Fire",
    "Flame",
    "Destination",
    "Gradient",
    "Orbital",
    "TimeOrbit",
];
const INPUT_KEYS: &[&str] = &[
    "Key A",
    "Key B",
    "Key C",
    "Key D",
    "Key E",
    "Key F",
    "Key G",
    "Key H",
    "Key I",
    "Key J",
    "Key K",
    "Key L",
    "Key M",
    "Key N",
    "Key O",
    "Key P",
    "Key Q",
    "Key R",
    "Key S",
    "Key T",
    "Key U",
    "Key V",
    "Key W",
    "Key X",
    "Key Y",
    "Key Z",
    "Key Space",
    "Key Left",
    "Key Right",
    "Key Up",
    "Key Down",
    "Key Del",
    "Key 0",
    "Key 1",
    "Key 2",
    "Key 3",
    "Key 4",
    "Key 5",
    "Key 6",
    "Key 7",
    "Key 8",
    "Key 9",
    "Mouse Left",
    "Mouse Right",
];

/// Suggest static vocabulary and local declarations, including incomplete code.
/// `@` contexts return local symbols only. Strings/comments suppress suggestions.
pub fn complete(source: &str, caret: usize) -> Option<Completions> {
    complete_with_symbols(source, caret, &[])
}
/// Merge a validated external symbol snapshot with current unsaved local declarations.
pub fn complete_with_symbols(
    source: &str,
    caret: usize,
    external: &[Completion],
) -> Option<Completions> {
    let prefix_source = source.get(..caret)?;
    let prefix_tokens = highlight(prefix_source);
    if prefix_tokens.last().is_some_and(|t| {
        t.range.end == caret && matches!(t.kind, TokenKind::Comment | TokenKind::Error)
    }) {
        return None;
    }
    let start = prefix_source
        .char_indices()
        .rev()
        .find(|(_, c)| !(c.is_alphanumeric() || *c == '_' || *c == '@'))
        .map_or(0, |(i, c)| i + c.len_utf8());
    let prefix = &source[start..caret];
    let pointer = prefix.starts_with('@');
    let filter = prefix.trim_start_matches('@').to_lowercase();
    let mut seen = BTreeSet::new();
    let mut items = Vec::new();
    let mut add = |label: &str, insert: String, category: &'static str| {
        if label.to_lowercase().starts_with(&filter)
            && seen.insert((label.to_lowercase(), category))
        {
            items.push(Completion {
                label: label.into(),
                insert,
                category,
            });
        }
    };
    // Lexically mask comments/strings before collecting declaration words.
    let ignored: Vec<_> = highlight(source)
        .into_iter()
        .filter(|t| {
            matches!(
                t.kind,
                TokenKind::Comment | TokenKind::String | TokenKind::Error
            )
        })
        .map(|t| t.range)
        .collect();
    let mut words = Vec::new();
    let mut begin = None;
    for (i, c) in source
        .char_indices()
        .chain(std::iter::once((source.len(), ' ')))
    {
        let masked = ignored
            .get(ignored.partition_point(|r| r.end <= i))
            .is_some_and(|r| r.contains(&i));
        if !masked && (c.is_alphanumeric() || c == '_') {
            begin.get_or_insert(i);
        } else if let Some(start) = begin.take() {
            words.push((start, i));
        }
    }
    let mut local_names = BTreeSet::new();
    for pair in words.windows(2) {
        let ((a, b), (c, d)) = (pair[0], pair[1]);
        // Only whitespace may separate declaration keyword and name.
        if !source[b..c].chars().all(char::is_whitespace) {
            continue;
        }
        let category = match source[a..b].to_ascii_lowercase().as_str() {
            "var" | "shared" => "Variable",
            "function" => "Function",
            _ => continue,
        };
        let name = &source[c..d];
        if c == start || !name.starts_with(|ch: char| ch.is_alphabetic() || ch == '_') {
            continue;
        }
        local_names.insert(name.to_lowercase());
        add(
            name,
            if pointer {
                format!("@{name}")
            } else {
                name.into()
            },
            category,
        );
    }
    let mut external_names = BTreeSet::new();
    for item in external {
        let name = item.label.to_lowercase();
        if !local_names.contains(&name) && external_names.insert(name) {
            add(
                &item.label,
                if pointer {
                    format!("@{}", item.insert)
                } else {
                    item.insert.clone()
                },
                item.category,
            );
        }
    }
    if !pointer {
        for (values, category) in [
            (BUILTIN_FUNCTIONS, "Built-in"),
            (KEYWORDS, "Keyword"),
            (COLORS, "Color"),
            (EFFECTS, "Effect"),
            (INPUT_KEYS, "Input Key"),
        ] {
            for name in values {
                add(
                    name,
                    if category == "Built-in" {
                        format!("{name}()")
                    } else {
                        (*name).into()
                    },
                    category,
                );
            }
        }
    }
    items.sort_by_key(|item| {
        (
            item.label.to_lowercase() != filter,
            item.category == "Keyword",
            item.label.to_lowercase(),
            item.category,
        )
    });
    items.truncate(100);
    Some(Completions {
        range: start..caret,
        items,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn external_symbols_deduplicate_and_local_names_take_precedence() {
        let item = |name: &str| Completion {
            label: name.into(),
            insert: name.into(),
            category: "Project number",
        };
        let external = vec![item("café"), item("CAFÉ"), item("count")];
        let text = "var count = 1\n@c";
        let result = complete_with_symbols(text, text.len(), &external).unwrap();
        assert_eq!(
            result
                .items
                .iter()
                .filter(|i| i.label.to_lowercase() == "count")
                .count(),
            1
        );
        assert_eq!(
            result
                .items
                .iter()
                .filter(|i| i.label.to_lowercase() == "café")
                .count(),
            1
        );
        assert!(result.items.iter().any(|i| i.insert == "@café"));
        assert!(
            result
                .items
                .iter()
                .any(|i| i.label == "count" && i.category != "Project number")
        );
    }
    #[test]
    fn java_catalog_preserves_builtin_and_multiword_insertions() {
        assert!(
            complete("ab", 2)
                .unwrap()
                .items
                .iter()
                .any(|c| c.insert == "abs()")
        );
        assert!(
            complete("is", 2)
                .unwrap()
                .items
                .iter()
                .any(|c| c.insert == "is a")
        );
    }
    #[test]
    fn unsaved_unicode_declarations_and_pointer_context() {
        let text = "var café = 1\n@ca";
        let result = complete(text, text.len()).unwrap();
        assert_eq!(result.items.len(), 1);
        assert_eq!(result.items[0].insert, "@café");
        assert_eq!(&text[result.range], "@ca");
    }
    #[test]
    fn comments_strings_and_invalid_boundaries_are_excluded() {
        assert!(complete("// pr", 5).is_none());
        assert!(complete("\"pr", 3).is_none());
        assert!(complete("é", 1).is_none());
        let text = "// var pretend\n\"var private\"\npr";
        assert!(
            !complete(text, text.len())
                .unwrap()
                .items
                .iter()
                .any(|i| i.category == "Variable")
        );
    }
}
