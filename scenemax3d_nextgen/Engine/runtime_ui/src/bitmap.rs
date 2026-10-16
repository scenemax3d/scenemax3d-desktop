//! BMFont parsing and glyph placement shared by the game and designer.
use std::collections::HashMap;
#[derive(Clone, Debug)]
pub struct Glyph {
    pub page: u32,
    pub source: [f32; 4],
    pub width: f32,
    pub height: f32,
    pub x_offset: f32,
    pub y_offset: f32,
    pub x_advance: f32,
}
#[derive(Clone, Debug)]
pub struct Font {
    pub page: String,
    pub pages: HashMap<u32, String>,
    pub kernings: HashMap<(char, char), f32>,
    pub size: f32,
    pub line_height: f32,
    pub glyphs: HashMap<char, Glyph>,
}
pub fn parse(source: &str) -> Result<Font, String> {
    let mut size: f32 = 0.;
    let mut height: f32 = 0.;
    let mut pages = HashMap::new();
    let mut kernings = HashMap::new();
    let mut glyphs = HashMap::new();
    for line in source.lines() {
        let field = |key: &str| -> Option<String> {
            let quoted = format!("{key}=\"");
            if let Some(rest) = line.split_once(&quoted).map(|(_, rest)| rest) {
                return rest.split_once('"').map(|(value, _)| value.to_owned());
            }
            let prefix = format!("{key}=");
            line.split_whitespace()
                .find_map(|p| p.strip_prefix(&prefix))
                .map(|s| s.trim_matches('"').to_owned())
        };
        let number = |key: &str, fallback: f32| {
            field(key)
                .and_then(|s| s.parse::<f32>().ok())
                .filter(|n| n.is_finite())
                .unwrap_or(fallback)
        };
        if line.starts_with("info ") {
            size = number("size", size).abs();
        }
        if line.starts_with("common ") {
            height = number("lineHeight", height);
        }
        if line.starts_with("page ") {
            if let (Some(id), Some(file)) = (
                field("id").and_then(|v| v.parse::<u32>().ok()),
                field("file"),
            ) {
                pages.insert(id, file);
            }
        }
        if line.starts_with("kerning ") {
            let character = |key| {
                field(key)
                    .and_then(|v| v.parse().ok())
                    .and_then(char::from_u32)
            };
            if let (Some(a), Some(b)) = (character("first"), character("second")) {
                kernings.insert((a, b), number("amount", 0.));
            }
        }
        if line.starts_with("char ") {
            let Some(ch) = field("id")
                .and_then(|s| s.parse().ok())
                .and_then(char::from_u32)
            else {
                continue;
            };
            let x = number("x", 0.);
            let y = number("y", 0.);
            let width = number("width", 0.);
            let h = number("height", 0.);
            glyphs.insert(
                ch,
                Glyph {
                    page: field("page").and_then(|v| v.parse().ok()).unwrap_or(0),
                    source: [x, y, x + width, y + h],
                    width,
                    height: h,
                    x_offset: number("xoffset", 0.),
                    y_offset: number("yoffset", 0.),
                    x_advance: number("xadvance", width),
                },
            );
        }
    }
    if glyphs.is_empty() {
        return Err("Bitmap font has no glyphs".into());
    }
    if glyphs
        .values()
        .any(|glyph| !pages.contains_key(&glyph.page))
    {
        return Err("Bitmap font references a missing atlas page".into());
    }
    Ok(Font {
        page: pages
            .iter()
            .min_by_key(|(id, _)| *id)
            .map(|(_, p)| p.clone())
            .ok_or("Bitmap font has no atlas page")?,
        pages,
        kernings,
        size: size.max(height).max(1.),
        line_height: height.max(size).max(1.),
        glyphs,
    })
}
/// Width includes kerning, and resets pair context at each line boundary.
pub fn line_width(font: &Font, text: &str) -> f32 {
    let mut previous = None;
    text.chars()
        .filter(|c| *c != '\r')
        .map(|c| {
            let kern = previous
                .and_then(|p| font.kernings.get(&(p, c)))
                .copied()
                .unwrap_or(0.);
            previous = Some(c);
            kern + font
                .glyphs
                .get(&c)
                .map_or(font.line_height * 0.35, |g| g.x_advance)
        })
        .sum()
}
#[derive(Debug, Clone)]
pub struct PlacedGlyph {
    pub page: u32,
    pub source: [f32; 4],
    pub rect: [f32; 4],
}
/// Return atlas page and pixel rectangles without assuming all glyphs share an image.
pub fn layout_paged(
    font: &Font,
    text: &str,
    size: f32,
    width: f32,
    height: f32,
    alignment: &str,
) -> Vec<PlacedGlyph> {
    let scale = size / font.size.max(1.);
    let line_height = font.line_height.max(font.size) * scale;
    let lines: Vec<_> = text.split('\n').collect();
    let top = (height - line_height * lines.len() as f32) * 0.5;
    let mut result = Vec::new();
    for (i, line) in lines.iter().enumerate() {
        let length = line_width(font, line) * scale;
        let mut x = match alignment.to_ascii_lowercase().as_str() {
            "center" => (width - length) * 0.5,
            "right" => width - length,
            _ => 0.,
        };
        let mut previous = None;
        for c in line.chars().filter(|c| *c != '\r') {
            x += previous
                .and_then(|p| font.kernings.get(&(p, c)))
                .copied()
                .unwrap_or(0.)
                * scale;
            previous = Some(c);
            let Some(g) = font.glyphs.get(&c) else {
                x += line_height * 0.35;
                continue;
            };
            if g.width > 1. && g.height > 1. {
                result.push(PlacedGlyph {
                    page: g.page,
                    source: g.source,
                    rect: [
                        x + g.x_offset * scale,
                        top + i as f32 * line_height + g.y_offset * scale,
                        g.width * scale,
                        g.height * scale,
                    ],
                });
            }
            x += g.x_advance * scale;
        }
    }
    result
}
/// Single-page consumers retain the original API.
pub fn layout(
    font: &Font,
    text: &str,
    size: f32,
    width: f32,
    height: f32,
    alignment: &str,
) -> Vec<([f32; 4], [f32; 4])> {
    layout_paged(font, text, size, width, height, alignment)
        .into_iter()
        .map(|g| (g.source, g.rect))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn multiple_pages_and_kerning_preserve_atlas_identity_and_alignment() {
        let font=parse("info size=16\ncommon lineHeight=16\npage id=0 file=\"first page.png\"\npage id=2 file=\"second.png\"\nchar id=65 width=8 height=12 xadvance=10 page=0\nchar id=86 width=8 height=12 xadvance=10 page=2\nkerning first=65 second=86 amount=-3").unwrap();
        assert_eq!(font.pages[&0], "first page.png");
        let glyphs = layout_paged(&font, "AV\nVA", 16., 40., 32., "center");
        assert_eq!(
            glyphs.iter().map(|g| g.page).collect::<Vec<_>>(),
            vec![0, 2, 2, 0]
        );
        assert_eq!(glyphs[0].rect[0], 11.5);
        assert_eq!(glyphs[1].rect[0], 18.5);
        assert_eq!(glyphs[2].rect[0], 10.);
        assert_eq!(glyphs[3].rect[0], 20.);
        assert!(parse("page id=0 file=\"a.png\"\nchar id=65 page=1").is_err());
    }
    #[test]
    fn font_metrics_and_centered_multiline_layout_match_runtime() {
        let f = parse("info size=16\ncommon lineHeight=20\npage id=0 file=\"atlas.png\"\nchar id=65 x=8 y=4 width=10 height=12 xoffset=1 yoffset=2 xadvance=12").unwrap();
        assert_eq!(f.size, 20.);
        let q = layout(&f, "AA\nA", 20., 100., 80., "center");
        assert_eq!(q[0], ([8., 4., 18., 16.], [39., 22., 10., 12.]));
        assert_eq!(q[2].1, [45., 42., 10., 12.]);
        assert!(parse("info size=12").is_err());
    }
}
