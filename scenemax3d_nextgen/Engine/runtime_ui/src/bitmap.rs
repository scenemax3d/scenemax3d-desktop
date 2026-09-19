//! BMFont parsing and glyph placement shared by the game and designer.
use std::collections::HashMap;
#[derive(Clone, Debug)]
pub struct Glyph {
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
    pub size: f32,
    pub line_height: f32,
    pub glyphs: HashMap<char, Glyph>,
}
pub fn parse(source: &str) -> Result<Font, String> {
    let mut size: f32 = 0.;
    let mut height: f32 = 0.;
    let mut page = None;
    let mut glyphs = HashMap::new();
    for line in source.lines() {
        let field = |key: &str| -> Option<String> {
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
            page = field("file");
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
    Ok(Font {
        page: page.ok_or("Bitmap font has no atlas page")?,
        size: size.max(height).max(1.),
        line_height: height.max(size).max(1.),
        glyphs,
    })
}
/// Return source atlas rectangles and destination [x, y, width, height].
pub fn layout(
    font: &Font,
    text: &str,
    size: f32,
    width: f32,
    height: f32,
    alignment: &str,
) -> Vec<([f32; 4], [f32; 4])> {
    let scale = size / font.size.max(1.);
    let line_height = font.line_height.max(font.size) * scale;
    let lines: Vec<_> = text.split('\n').collect();
    let top = (height - line_height * lines.len() as f32) * 0.5;
    let mut result = Vec::new();
    for (i, line) in lines.iter().enumerate() {
        let length: f32 = line
            .chars()
            .filter(|c| *c != '\r')
            .map(|c| {
                font.glyphs
                    .get(&c)
                    .map_or(font.line_height * 0.35, |g| g.x_advance)
            })
            .sum::<f32>()
            * scale;
        let mut x = match alignment.to_ascii_lowercase().as_str() {
            "center" => (width - length) * 0.5,
            "right" => width - length,
            _ => 0.,
        };
        for c in line.chars().filter(|c| *c != '\r') {
            let Some(g) = font.glyphs.get(&c) else {
                x += line_height * 0.35;
                continue;
            };
            if g.width > 1. && g.height > 1. {
                result.push((
                    g.source,
                    [
                        x + g.x_offset * scale,
                        top + i as f32 * line_height + g.y_offset * scale,
                        g.width * scale,
                        g.height * scale,
                    ],
                ));
            }
            x += g.x_advance * scale;
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
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
