use super::*;
use image::{Rgba, RgbaImage};
use swash::{FontRef, scale::ScaleContext};
use zeno::{Cap, Join, Mask, Stroke, Transform};

struct Glyph {
    ch: char,
    image: RgbaImage,
    offset: i32,
    advance: i32,
    x: u32,
    y: u32,
}
/// A single-page bitmap atlas and compatible AngelCode text descriptor.
pub struct Atlas {
    /// Straight-alpha RGBA PNG pixels.
    pub image: RgbaImage,
    /// AngelCode text, using the requested asset name for face and page.
    pub descriptor: String,
    /// Number of unique exported characters, including space.
    pub characters: usize,
}
struct Raster {
    glyphs: Vec<Glyph>,
    line_height: u32,
    base: u32,
    ascent: i32,
    descent: i32,
}

fn raster(catalog: &Catalog, o: &Options) -> io::Result<Raster> {
    o.validate().map_err(error)?;
    let info = catalog.font(o)?;
    let bytes = info
        .load(None)
        .ok_or_else(|| error("Cannot load the selected system font."))?;
    let font = FontRef::from_index(bytes.as_ref(), info.index() as usize)
        .ok_or_else(|| error("Unsupported system font data."))?;
    let synthesis = info.synthesis(FontWidth::NORMAL, style(o), weight(o));
    let variations: Vec<_> = synthesis
        .variation_settings()
        .iter()
        .map(|(tag, value)| swash::Setting {
            tag: u32::from_be_bytes(tag.to_be_bytes()),
            value: *value,
        })
        .collect();
    let coordinates: Vec<_> = font
        .variations()
        .normalized_coords(variations.iter().copied())
        .collect();
    let mut context = ScaleContext::new();
    let mut scaler = context
        .builder(font)
        .size(o.size as f32)
        .hint(false)
        .variations(variations)
        .build();
    let metrics = font.metrics(&coordinates).scale(o.size as f32);
    let ascent = metrics.ascent.ceil().max(1.) as i32;
    let descent = metrics.descent.abs().ceil() as i32;
    let left = (o.padding + o.outline) as i32 + (-o.shadow[0]).max(0);
    let right = (o.padding + o.outline) as i32 + o.shadow[0].max(0);
    let top = (o.padding + o.outline) as i32 + (-o.shadow[1]).max(0);
    let bottom = (o.padding + o.outline) as i32 + o.shadow[1].max(0);
    let height =
        (ascent + descent + metrics.leading.round().max(0.) as i32 + top + bottom).max(1) as u32;
    if height > 2048 {
        return Err(error("Font line height exceeds the atlas limit."));
    }
    let base = (top + ascent) as u32;
    let mut glyphs = Vec::new();
    let mut pixels = 0u64;
    for ch in o.charset() {
        let id = font.charmap().map(ch);
        let advance = font
            .glyph_metrics(&coordinates)
            .scale(o.size as f32)
            .advance_width(id)
            .round()
            .max(1.) as i32;
        if ch.is_whitespace() {
            glyphs.push(Glyph {
                ch,
                image: RgbaImage::new(1, 1),
                offset: 0,
                advance,
                x: 0,
                y: 0,
            });
            continue;
        }
        let mut outline = scaler.scale_outline(id).ok_or_else(|| {
            error(format!(
                "No scalable outline for U+{:04X} in {}.",
                ch as u32, o.family
            ))
        })?;
        if synthesis.embolden() {
            outline.embolden(o.size as f32 / 24., o.size as f32 / 24.);
        }
        let skew = synthesis.skew().unwrap_or(0.).to_radians().tan();
        outline.transform(&Transform::new(1., 0., skew, -1., 0., 0.));
        let bounds = outline.bounds();
        let draw_x = left + (-bounds.min.x.floor() as i32).max(0);
        let width = (advance + left + right)
            .max(draw_x + bounds.max.x.floor() as i32 + 1 + right)
            .max(1) as u32;
        if width > 4096 {
            return Err(error("A glyph is wider than the atlas limit."));
        }
        pixels += u64::from(width) * u64::from(height);
        if pixels > 32 * 1024 * 1024 {
            return Err(error(
                "Atlas is too large. Reduce font size or the character set.",
            ));
        }
        let mut image = RgbaImage::new(width, height);
        let transform = Transform::translation(draw_x as f32, base as f32);
        if o.shadow != [0, 0] && o.colors[3][3] != 0 {
            let (mask, _) = Mask::new(outline.path())
                .size(width, height)
                .transform(Some(Transform::translation(
                    (draw_x + o.shadow[0]) as f32,
                    (base as i32 + o.shadow[1]) as f32,
                )))
                .render();
            paint(&mut image, &mask, o.smooth, |_| o.colors[3]);
        }
        if o.outline != 0 {
            let (mask, _) = Mask::new(outline.path())
                .size(width, height)
                .transform(Some(transform))
                .style(
                    Stroke::new(o.outline as f32 * 2.)
                        .join(Join::Round)
                        .cap(Cap::Round),
                )
                .render();
            paint(&mut image, &mask, o.smooth, |_| o.colors[2]);
        }
        let (mask, _) = Mask::new(outline.path())
            .size(width, height)
            .transform(Some(transform))
            .render();
        paint(&mut image, &mask, o.smooth, |y| {
            if !o.gradient {
                return o.colors[0];
            }
            let t = ((y as f32 - base as f32 - bounds.min.y)
                / (bounds.max.y - bounds.min.y).max(1.))
            .clamp(0., 1.);
            std::array::from_fn(|i| {
                (o.colors[0][i] as f32 * (1. - t) + o.colors[1][i] as f32 * t).round() as u8
            })
        });
        glyphs.push(Glyph {
            ch,
            image,
            offset: -draw_x,
            advance,
            x: 0,
            y: 0,
        });
    }
    Ok(Raster {
        glyphs,
        line_height: height,
        base,
        ascent,
        descent,
    })
}
fn over(dst: &mut Rgba<u8>, src: [u8; 4], coverage: u8) {
    let a = src[3] as f32 / 255. * coverage as f32 / 255.;
    let old = dst[3] as f32 / 255.;
    let out = a + old * (1. - a);
    if out == 0. {
        return;
    }
    for i in 0..3 {
        dst[i] = ((src[i] as f32 * a + dst[i] as f32 * old * (1. - a)) / out).round() as u8;
    }
    dst[3] = (out * 255.).round() as u8;
}
fn paint(image: &mut RgbaImage, mask: &[u8], smooth: bool, color: impl Fn(u32) -> [u8; 4]) {
    let width = image.width();
    for (i, pixel) in image.pixels_mut().enumerate() {
        let alpha = mask[i];
        over(
            pixel,
            color(i as u32 / width),
            if smooth {
                alpha
            } else if alpha >= 128 {
                255
            } else {
                0
            },
        );
    }
}
/// Rasterize styled glyphs into one power-of-two page, using Java's 1024px shelf packing.
pub fn build_atlas(catalog: &Catalog, options: &Options) -> io::Result<Atlas> {
    options.validate_name().map_err(error)?;
    let mut r = raster(catalog, options)?;
    let (mut x, mut y, mut row, mut used) = (0, 0, 0, 0);
    for g in &mut r.glyphs {
        if x + g.image.width() > 1024 && x > 0 {
            x = 0;
            y += row;
            row = 0;
        }
        g.x = x;
        g.y = y;
        x += g.image.width();
        row = row.max(g.image.height());
        used = used.max(x);
    }
    let width = used.max(1).next_power_of_two();
    let height = (y + row).max(1).next_power_of_two();
    if height > 16384 || u64::from(width) * u64::from(height) > 32 * 1024 * 1024 {
        return Err(error(
            "Atlas is too large. Reduce font size or the character set.",
        ));
    }
    let mut image = RgbaImage::new(width, height);
    let name = &options.name;
    // Preserve the original descriptor contract: effects are baked into pixels;
    // size is the padded line height, and no additional runtime padding/kerning is applied.
    let mut descriptor = format!(
        "info face=\"{name}\" size={} bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1\ncommon lineHeight={} base={} scaleW={width} scaleH={height} pages=1 packed=0\npage id=0 file=\"{name}.png\"\nchars count={}\n",
        r.line_height,
        r.line_height,
        r.base,
        r.glyphs.len()
    );
    for g in &r.glyphs {
        image::imageops::replace(&mut image, &g.image, g.x.into(), g.y.into());
        descriptor.push_str(&format!("char id={} x={} y={} width={} height={} xoffset={} yoffset=0 xadvance={} page=0 chnl=15\n",g.ch as u32,g.x,g.y,g.image.width(),g.image.height(),g.offset,g.advance));
    }
    descriptor.push_str("kernings count=0\n");
    Ok(Atlas {
        image,
        descriptor,
        characters: r.glyphs.len(),
    })
}
/// Render the same styled glyphs on the Java tool's 640×320 dark preview canvas.
pub fn preview(catalog: &Catalog, options: &Options) -> io::Result<RgbaImage> {
    let text = if options.preview.trim().is_empty() {
        "SceneMax Font"
    } else {
        options.preview.trim()
    };
    let mut o = options.clone();
    o.characters = text.into();
    let r = raster(catalog, &o)?;
    let mut image = RgbaImage::from_pixel(640, 320, Rgba([18, 18, 22, 255]));
    for y in 8..312 {
        for x in 8..632 {
            let dx = (17i32 - x as i32).max(x as i32 - 622).max(0);
            let dy = (17i32 - y as i32).max(y as i32 - 302).max(0);
            if dx * dx + dy * dy <= 81 {
                image.put_pixel(x, y, Rgba([46, 46, 56, 255]));
            }
        }
    }
    let baseline = (r.base as i32 + 4)
        .max((320 + r.ascent - r.descent) / 2)
        .min(304);
    let mut pen = 24;
    for ch in text.chars() {
        if let Some(g) = r.glyphs.iter().find(|g| g.ch == ch) {
            for (x, y, p) in g.image.enumerate_pixels() {
                let dx = pen + g.offset + x as i32;
                let dy = baseline - r.base as i32 + y as i32;
                if (0..640).contains(&dx) && (0..320).contains(&dy) {
                    over(image.get_pixel_mut(dx as u32, dy as u32), p.0, 255);
                }
            }
            pen += g.advance;
            if pen > 700 {
                break;
            }
        }
    }
    Ok(image)
}
