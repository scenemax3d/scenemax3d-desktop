//! Settings shared by the native font generator form and renderer.

/// The Java tool's ordered Basic Latin repertoire.
pub const BASIC_LATIN: &str = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
/// The Java tool's additional Hebrew letters, including final forms.
pub const HEBREW: &str = "אבגדהוזחטיכלמנסעפצקרשתךםןףץ";

/// Editable generation settings. Colors are straight-alpha RGBA bytes.
#[derive(Clone, Debug, PartialEq)]
pub struct Options {
    /// Runtime registration and output basename.
    pub name: String,
    /// Installed font family.
    pub family: String,
    /// Plain, Bold, Italic, or Bold Italic (0..=3).
    pub style: u8,
    /// Font size in pixels (12..=256).
    pub size: u32,
    /// Round outline radius (0..=24).
    pub outline: u32,
    /// Shadow displacement (-32..=32).
    pub shadow: [i32; 2],
    /// Transparent glyph padding (0..=32).
    pub padding: u32,
    /// Smooth glyph and outline edges.
    pub smooth: bool,
    /// Vertical gradient instead of solid fill.
    pub gradient: bool,
    /// Fill, gradient, outline, and shadow colors.
    pub colors: [[u8; 4]; 4],
    /// Ordered custom repertoire, deduplicated during export.
    pub characters: String,
    /// Preview-only sample text.
    pub preview: String,
}
impl Default for Options {
    fn default() -> Self {
        Self {
            name: "headline_font".into(),
            family: "Arial".into(),
            style: 0,
            size: 64,
            outline: 3,
            shadow: [3, 3],
            padding: 6,
            smooth: true,
            gradient: true,
            colors: [
                [255, 242, 163, 255],
                [255, 127, 39, 255],
                [51, 18, 77, 255],
                [0, 0, 0, 255],
            ],
            characters: format!("{BASIC_LATIN}{HEBREW}"),
            preview: "SceneMax Font 123".into(),
        }
    }
}
impl Options {
    /// Validate bounded raster work and the Java form's numeric ranges.
    pub fn validate(&self) -> Result<(), String> {
        if !(12..=256).contains(&self.size)
            || self.outline > 24
            || self.padding > 32
            || self.shadow.iter().any(|n| !(-32..=32).contains(n))
            || self.style > 3
        {
            return Err(
                "Use size 12–256, outline 0–24, padding 0–32, and shadow offsets −32–32.".into(),
            );
        }
        if self.characters.chars().count() > 4096 || self.preview.chars().count() > 512 {
            return Err("Use at most 4,096 characters and 512 preview characters.".into());
        }
        Ok(())
    }
    /// Validate a portable output name using the Java tool's accepted alphabet.
    pub fn validate_name(&self) -> Result<(), String> {
        if self.name.is_empty()
            || self.name.len() > 120
            || !self
                .name
                .bytes()
                .all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-')
        {
            return Err(
                "Use only letters, digits, '_' or '-' in the asset name (1–120 characters).".into(),
            );
        }
        Ok(())
    }
    /// Preserve character order, remove duplicates, and always include a space.
    pub fn charset(&self) -> Vec<char> {
        let mut seen = std::collections::BTreeSet::new();
        self.characters
            .chars()
            .chain(std::iter::once(' '))
            .filter(|c| seen.insert(*c))
            .collect()
    }
}
