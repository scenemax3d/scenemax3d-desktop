//! Registered assets, dependency inspection and reviewed project transfers.
mod scan;
#[cfg(test)]
mod tests;
mod transfer;
pub use scan::scan;
use serde_json::Value;
use std::{
    fs, io,
    path::{Path, PathBuf},
};
pub use transfer::{Operation, Plan, apply, rename, review};
/// Asset categories in the inventory sidebar.
pub const CATEGORIES: &[&str] = &[
    "All",
    "Models",
    "Audio",
    "Videos",
    "Sprites",
    "Fonts",
    "Animations",
    "Textures",
    "Shaders",
    "Materials",
    "Weapons",
    "Throw Motions",
    "Effekseer",
    "Scenes",
    "UI",
    "Skyboxes",
    "Terrain",
    "IK",
    "Cinematics",
];
/// One registration or designer resource available to a project.
#[derive(Clone, Debug)]
pub struct Asset {
    /// Stable identity within a scan.
    pub id: String,
    /// Runtime resource name.
    pub name: String,
    /// Sidebar category.
    pub category: String,
    /// Project or shared installation origin.
    pub origin: String,
    /// Canonical owning project.
    pub project: PathBuf,
    /// Canonical containing scripts/resources directory.
    pub root: PathBuf,
    /// Primary runtime file, when present.
    pub file: Option<PathBuf>,
    /// Registration index, relative to root.
    pub index: Option<PathBuf>,
    /// Array key in the index.
    pub key: String,
    /// Original registration metadata, preserved during transfer.
    pub data: Value,
    /// File size, excluding dependencies until reviewed.
    pub bytes: u64,
    /// Availability/compatibility diagnostic.
    pub status: String,
}
impl Asset {
    /// Shared assets cannot be renamed, moved or removed.
    pub fn owned(&self) -> bool {
        self.origin != "Shared"
    }
    /// Search across identity, category, source and metadata.
    pub fn matches(&self, query: &str) -> bool {
        format!(
            "{} {} {} {} {}",
            self.name, self.category, self.origin, self.status, self.data
        )
        .to_lowercase()
        .contains(&query.to_lowercase())
    }
}
/// A complete bounded inventory with nonfatal scan diagnostics.
#[derive(Clone, Debug, Default)]
pub struct Inventory {
    /// Assets available through registrations and file-based designer definitions.
    pub assets: Vec<Asset>,
    /// Index failures are surfaced rather than silently hidden.
    pub warnings: Vec<String>,
}
pub(super) fn error(s: impl Into<String>) -> io::Error {
    io::Error::other(s.into())
}
pub(super) fn json(path: &Path) -> io::Result<Value> {
    if fs::metadata(path)?.len() > 16 * 1024 * 1024 {
        return Err(error("Metadata exceeds 16 MiB"));
    }
    let s = fs::read_to_string(path)?;
    let source = s.trim_start_matches('\u{feff}');
    serde_json::from_str(source)
        .or_else(|_| serde_json::from_str(&normalize_json(source)))
        .map_err(Into::into)
}
// Java resource indexes allow trailing commas and bare object keys. Normalize only
// outside JSON strings; never evaluate source or alter quoted paths/shader text.
fn normalize_json(source: &str) -> String {
    let chars: Vec<_> = source.chars().collect();
    let mut out = String::new();
    let mut i = 0;
    let mut quoted = false;
    let mut escape = false;
    while i < chars.len() {
        let c = chars[i];
        if quoted {
            out.push(c);
            if escape {
                escape = false;
            } else if c == '\\' {
                escape = true;
            } else if c == '"' {
                quoted = false;
            }
            i += 1;
            continue;
        }
        if c == '"' {
            quoted = true;
            out.push(c);
            i += 1;
            continue;
        }
        if c == ','
            && chars[i + 1..]
                .iter()
                .find(|c| !c.is_whitespace())
                .is_some_and(|c| *c == '}' || *c == ']')
        {
            i += 1;
            continue;
        }
        if c.is_ascii_alphabetic() || c == '_' || c == '$' {
            let start = i;
            while i < chars.len()
                && (chars[i].is_ascii_alphanumeric() || chars[i] == '_' || chars[i] == '$')
            {
                i += 1;
            }
            let key = chars[start..i].iter().collect::<String>();
            if chars[i..].iter().find(|c| !c.is_whitespace()) == Some(&':') {
                out.push('"');
                out.push_str(&key);
                out.push('"');
            } else {
                out.push_str(&key);
            }
            continue;
        }
        out.push(c);
        i += 1;
    }
    out
}
pub(super) fn linked(meta: &fs::Metadata) -> bool {
    if meta.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        if meta.file_attributes() & 0x400 != 0 {
            return true;
        }
    }
    false
}
pub(super) fn confined(root: &Path, relative: &Path) -> io::Result<PathBuf> {
    if relative.is_absolute()
        || relative
            .components()
            .any(|c| !matches!(c, std::path::Component::Normal(_)))
    {
        return Err(error("Asset path must stay inside its root"));
    }
    let mut path = root.to_owned();
    for part in relative.components() {
        path.push(part);
        match fs::symlink_metadata(&path) {
            Ok(m) if linked(&m) => {
                return Err(error(format!(
                    "Linked asset path is not supported: {}",
                    path.display()
                )));
            }
            Ok(_) => {}
            Err(e) if e.kind() == io::ErrorKind::NotFound => {}
            Err(e) => return Err(e),
        }
    }
    Ok(path)
}
pub(super) fn walk(root: &Path) -> io::Result<Vec<PathBuf>> {
    let mut result = Vec::new();
    let mut pending = vec![(root.to_owned(), 0)];
    let mut count = 0;
    while let Some((dir, depth)) = pending.pop() {
        if !dir.exists() {
            continue;
        }
        if depth > 32 {
            return Err(error("Asset folders exceed 32 levels"));
        }
        for e in fs::read_dir(dir)? {
            let e = e?;
            count += 1;
            if count > 100_000 {
                return Err(error("Asset inventory exceeds 100,000 entries"));
            }
            let p = e.path();
            let m = fs::symlink_metadata(&p)?;
            if linked(&m) {
                continue;
            }
            if m.is_dir() {
                pending.push((p, depth + 1));
            } else if m.is_file() {
                result.push(p);
            }
        }
    }
    result.sort();
    Ok(result)
}
/// Read a bounded text preview without attempting to decode binary formats.
pub fn text_preview(asset: &Asset) -> io::Result<String> {
    let Some(file) = &asset.file else {
        return Ok(serde_json::to_string_pretty(&asset.data)?);
    };
    let ext = file
        .extension()
        .unwrap_or_default()
        .to_string_lossy()
        .to_lowercase();
    if [
        "json",
        "smmat",
        "smshader",
        "smbevyshader",
        "smenvshader",
        "smdesign",
        "smui",
        "smweapon",
        "smmotion",
        "smik",
        "code",
        "mat",
        "wgsl",
        "fnt",
        "txt",
    ]
    .contains(&ext.as_str())
    {
        use std::io::Read;
        let mut bytes = Vec::new();
        fs::File::open(file)?
            .take(64 * 1024)
            .read_to_end(&mut bytes)?;
        return Ok(String::from_utf8_lossy(&bytes).into_owned());
    }
    Ok(serde_json::to_string_pretty(&asset.data)?)
}
/// Open a containing folder or media file with the operating system's default application.
pub fn open_path(path: &Path) -> io::Result<()> {
    let path = path.canonicalize()?;
    #[cfg(windows)]
    let mut c = std::process::Command::new("explorer.exe");
    #[cfg(target_os = "macos")]
    let mut c = std::process::Command::new("open");
    #[cfg(all(not(windows), not(target_os = "macos")))]
    let mut c = std::process::Command::new("xdg-open");
    c.arg(path).spawn()?;
    Ok(())
}

/// Decode a bounded display thumbnail on a worker rather than uploading full source textures.
pub fn thumbnail(path: &Path) -> io::Result<(u32, u32, Vec<u8>)> {
    let mut reader = image::ImageReader::open(path)?.with_guessed_format()?;
    let mut limits = image::Limits::default();
    limits.max_image_width = Some(16384);
    limits.max_image_height = Some(16384);
    limits.max_alloc = Some(512 * 1024 * 1024);
    reader.limits(limits);
    let image = reader
        .decode()
        .map_err(|e| error(e.to_string()))?
        .thumbnail(512, 512)
        .to_rgba8();
    Ok((image.width(), image.height(), image.into_raw()))
}
