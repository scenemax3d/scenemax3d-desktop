//! Native equivalent of the desktop Font Generator, with AngelCode BMFont output.
mod export;
mod raster;
#[cfg(test)]
mod tests;
pub use export::export;
use fontique::{Collection, FamilyInfo, FontInfo, FontStyle, FontWeight, FontWidth};
pub use raster::{Atlas, build_atlas, preview};
pub use scenemax_ide_core::font_generator::Options;
use std::io;

fn error(message: impl ToString) -> io::Error {
    io::Error::other(message.to_string())
}

/// Installed font families discovered on a background worker.
#[derive(Clone, Default)]
pub struct Catalog {
    families: Vec<FamilyInfo>,
}
impl Catalog {
    /// Query the platform's native system font collection.
    pub fn discover() -> io::Result<Self> {
        let mut collection = Collection::new(Default::default());
        collection.load_system_fonts();
        let names: Vec<_> = collection.family_names().map(str::to_owned).collect();
        let mut families: Vec<_> = names
            .iter()
            .filter_map(|name| collection.family_by_name(name))
            .collect();
        families.sort_by_key(|f| f.name().to_lowercase());
        families.dedup_by(|a, b| a.name().eq_ignore_ascii_case(b.name()));
        if families.is_empty() {
            return Err(error("No installed fonts were found."));
        }
        Ok(Self { families })
    }
    /// Sorted family names for the System Font selector.
    pub fn names(&self) -> Vec<String> {
        self.families.iter().map(|f| f.name().to_owned()).collect()
    }
    fn font(&self, options: &Options) -> io::Result<FontInfo> {
        let family = self
            .families
            .iter()
            .find(|f| f.name().eq_ignore_ascii_case(&options.family))
            .ok_or_else(|| error("Choose an installed font family."))?;
        family
            .match_font(FontWidth::NORMAL, style(options), weight(options), true)
            .cloned()
            .ok_or_else(|| error("This font family has no usable face."))
    }
}
fn style(o: &Options) -> FontStyle {
    if o.style & 2 != 0 {
        FontStyle::Italic
    } else {
        FontStyle::Normal
    }
}
fn weight(o: &Options) -> FontWeight {
    if o.style & 1 != 0 {
        FontWeight::BOLD
    } else {
        FontWeight::NORMAL
    }
}
