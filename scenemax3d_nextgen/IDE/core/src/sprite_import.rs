//! Sprite sheet geometry and preview timing, independent of rendering and storage.
use serde_json::{Value, json};
/// Fresh sprite import document. Playback controls affect preview only.
pub fn draft() -> Value {
    json!({"version":1,"source":"","name":"sprite","rows":1,"cols":1,
    "pixelWidth":0,"pixelHeight":0,"marginX":0,"marginY":0,"spacingX":0,"spacingY":0,
    "frameWidth":1.0,"frameHeight":0.0,"fps":12.0,"first":0,"last":0,"mode":"loop","filter":"nearest","grid":true})
}
/// Validate form fields even before an image is loaded.
pub fn validate(v: &Value) -> Result<(), String> {
    for key in [
        "rows",
        "cols",
        "pixelWidth",
        "pixelHeight",
        "marginX",
        "marginY",
        "spacingX",
        "spacingY",
        "first",
        "last",
    ] {
        let n = integer(v, key)?;
        if n > 16384 || (["rows", "cols"].contains(&key) && n == 0) {
            return Err(format!("Invalid {key}"));
        }
    }
    if integer(v, "rows")? * integer(v, "cols")? > 4096 {
        return Err("Maximum 4096 frames per sheet".into());
    }
    for key in ["frameWidth", "frameHeight", "fps"] {
        let n = v[key]
            .as_f64()
            .filter(|n| n.is_finite())
            .ok_or_else(|| format!("Invalid {key}"))?;
        if n < 0. || (key != "frameHeight" && n == 0.) || (key == "fps" && n > 240.) {
            return Err(format!("Invalid {key}"));
        }
    }
    if !["loop", "once", "pingpong"].contains(&v["mode"].as_str().unwrap_or_default()) {
        return Err("Invalid playback mode".into());
    }
    if !["nearest", "linear"].contains(&v["filter"].as_str().unwrap_or_default()) {
        return Err("Invalid preview filter".into());
    }
    if !v["grid"].is_boolean() {
        return Err("Invalid grid option".into());
    }
    Ok(())
}
/// Integral pixel/grid setting; sliders may encode whole values as JSON floats.
pub fn integer(v: &Value, key: &str) -> Result<u32, String> {
    v[key]
        .as_f64()
        .filter(|n| n.is_finite() && *n >= 0. && *n <= u32::MAX as f64 && n.fract() == 0.)
        .map(|n| n as u32)
        .ok_or_else(|| format!("{key} must be a whole number"))
}
/// Validated top-left-origin, row-major sprite sheet layout.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Layout {
    /// Number of frame rows.
    pub rows: u32,
    /// Number of frame columns.
    pub cols: u32,
    /// Frame width in source pixels.
    pub width: u32,
    /// Frame height in source pixels.
    pub height: u32,
    /// Symmetric outer margin in pixels, X/Y.
    pub margin: [u32; 2],
    /// Gutter width in pixels, X/Y.
    pub spacing: [u32; 2],
}
impl Layout {
    /// Resolve automatic cell dimensions and reject clipped or fractional cells.
    pub fn new(v: &Value, image: [u32; 2]) -> Result<Self, String> {
        validate(v)?;
        let rows = integer(v, "rows")?;
        let cols = integer(v, "cols")?;
        let margin = [integer(v, "marginX")?, integer(v, "marginY")?];
        let spacing = [integer(v, "spacingX")?, integer(v, "spacingY")?];
        let mut cell = [0; 2];
        for (axis, (count, key)) in [(cols, "pixelWidth"), (rows, "pixelHeight")]
            .into_iter()
            .enumerate()
        {
            let used = 2 * margin[axis] + (count - 1) * spacing[axis];
            let available = image[axis]
                .checked_sub(used)
                .ok_or("Margins or spacing exceed the image")?;
            let explicit = integer(v, key)?;
            if explicit == 0 && available % count != 0 {
                return Err(format!(
                    "Image does not divide evenly: set {key} or adjust margins/spacing"
                ));
            }
            cell[axis] = if explicit == 0 {
                available / count
            } else {
                explicit
            };
            if cell[axis] == 0 || cell[axis] * count > available {
                return Err("Frame rectangles exceed the source image".into());
            }
        }
        Ok(Self {
            rows,
            cols,
            width: cell[0],
            height: cell[1],
            margin,
            spacing,
        })
    }
    /// Source pixel rectangle [left, top, width, height].
    pub fn rect(self, index: u32) -> [u32; 4] {
        let index = index.min(self.count() - 1);
        [
            self.margin[0] + index % self.cols * (self.width + self.spacing[0]),
            self.margin[1] + index / self.cols * (self.height + self.spacing[1]),
            self.width,
            self.height,
        ]
    }
    /// Total imported frame count.
    pub fn count(self) -> u32 {
        self.rows * self.cols
    }
    /// Hit-test a source image point. Gutters and excluded edges select nothing.
    pub fn hit(self, x: f32, y: f32) -> Option<u32> {
        if x < self.margin[0] as f32 || y < self.margin[1] as f32 {
            return None;
        }
        let x = x as u32 - self.margin[0];
        let y = y as u32 - self.margin[1];
        let sx = self.width + self.spacing[0];
        let sy = self.height + self.spacing[1];
        let col = x / sx;
        let row = y / sy;
        (col < self.cols && row < self.rows && x % sx < self.width && y % sy < self.height)
            .then_some(row * self.cols + col)
    }
}
/// Frame at an elapsed time, including ping-pong endpoints without duplicated dwell.
pub fn playback_frame(elapsed: f64, fps: f64, first: u32, last: u32, mode: &str) -> (u32, bool) {
    let last = last.max(first);
    let count = (last - first + 1) as u64;
    let tick = (elapsed.max(0.) * fps) as u64;
    if mode == "once" {
        return (first + tick.min(count - 1) as u32, tick >= count);
    }
    if mode == "pingpong" && count > 1 {
        let phase = tick % (2 * (count - 1));
        return (first + phase.min(2 * (count - 1) - phase) as u32, false);
    }
    (first + (tick % count) as u32, false)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn layout_handles_gutters_and_top_left_order() {
        let mut v = draft();
        v["cols"] = json!(3);
        v["rows"] = json!(2);
        v["marginX"] = json!(2);
        v["marginY"] = json!(1);
        v["spacingX"] = json!(1);
        let l = Layout::new(&v, [36, 22]).unwrap();
        assert_eq!(l.rect(4), [13, 11, 10, 10]);
        assert_eq!(l.hit(14., 12.), Some(4));
        assert_eq!(l.hit(12., 5.), None);
        assert!(Layout::new(&v, [35, 22]).is_err());
    }
    #[test]
    fn animation_modes_have_predictable_endpoints() {
        assert_eq!(
            (0..7)
                .map(|n| playback_frame(n as f64, 1., 2, 4, "pingpong").0)
                .collect::<Vec<_>>(),
            vec![2, 3, 4, 3, 2, 3, 4]
        );
        assert_eq!(playback_frame(5., 1., 2, 4, "once"), (4, true));
        assert_eq!(playback_frame(5., 1., 2, 4, "loop"), (4, false));
    }
}
