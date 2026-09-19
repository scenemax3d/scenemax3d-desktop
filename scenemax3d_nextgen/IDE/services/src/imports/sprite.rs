//! Decoding and immutable staging for the retained sprite importer.
use super::*;
use scenemax_ide_core::sprite_import::{self as schema, Layout};
/// Decoded source snapshot, owned until the document closes or loads another file.
pub struct Prepared {
    /// Decoded image width.
    pub width: u32,
    /// Decoded image height.
    pub height: u32,
    /// RGBA8 pixels in top-left row order.
    pub rgba: Vec<u8>,
    /// Canonical source path.
    pub source: PathBuf,
}
/// Decode on a worker. The project and the selected file are never modified.
pub fn prepare(source: &Path) -> io::Result<Prepared> {
    let source = source.canonicalize()?;
    let mut reader = image::ImageReader::open(&source)?.with_guessed_format()?;
    let mut limits = image::Limits::default();
    limits.max_image_width = Some(16384);
    limits.max_image_height = Some(16384);
    limits.max_alloc = Some(256 * 1024 * 1024);
    reader.limits(limits);
    let pixels = reader
        .decode()
        .map_err(|e| error(e.to_string()))?
        .to_rgba8();
    Ok(Prepared {
        width: pixels.width(),
        height: pixels.height(),
        rgba: pixels.into_raw(),
        source,
    })
}
/// Bake uniform row-major frames and use the existing atomic runtime registration.
pub fn commit(root: &Path, prepared: &Prepared, draft: &Value) -> io::Result<Outcome> {
    let layout = Layout::new(draft, [prepared.width, prepared.height]).map_err(error)?;
    let source = image::RgbaImage::from_raw(prepared.width, prepared.height, prepared.rgba.clone())
        .ok_or_else(|| error("Invalid source pixels"))?;
    let mut packed = image::RgbaImage::new(layout.cols * layout.width, layout.rows * layout.height);
    for frame in 0..layout.count() {
        let [x, y, w, h] = layout.rect(frame);
        let cell = image::imageops::crop_imm(&source, x, y, w, h).to_image();
        image::imageops::replace(
            &mut packed,
            &cell,
            (frame % layout.cols * w) as i64,
            (frame / layout.cols * h) as i64,
        );
    }
    let staging = tempfile::Builder::new()
        .prefix("scenemax-sprite-")
        .tempdir()?;
    let path = staging.path().join("sheet.png");
    packed.save(&path).map_err(|e| error(e.to_string()))?;
    import(
        root,
        &Request {
            kind: Kind::Sprite,
            source: path,
            name: draft["name"].as_str().unwrap_or_default().into(),
            rows: layout.rows,
            cols: layout.cols,
            frame_width: draft["frameWidth"].as_f64().unwrap_or(1.) as f32,
            frame_height: draft["frameHeight"].as_f64().unwrap_or(0.) as f32,
            clip: String::new(),
            scale: 1.,
            model: None,
            effect: None,
        },
    )
}
/// Persist import settings through the normal document Save/Undo workflow.
pub fn open_draft(project: &Path) -> io::Result<scenemax_ide_core::Document> {
    use std::io::Read;
    let root = project.canonicalize()?;
    let parent = root.join(".scenemax-studio");
    if parent.exists() && !parent.canonicalize()?.starts_with(&root) {
        return Err(error("Draft folder escapes project"));
    }
    let folder = parent.join("imports");
    fs::create_dir_all(&folder)?;
    let folder = folder.canonicalize()?;
    if !folder.starts_with(&root) {
        return Err(error("Draft folder escapes project"));
    }
    let path = folder.join("Import Sprite.smspriteimport");
    match fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&path)
    {
        Ok(mut f) => f.write_all(&serde_json::to_vec_pretty(&schema::draft())?)?,
        Err(e) if e.kind() == io::ErrorKind::AlreadyExists => {}
        Err(e) => return Err(e),
    }
    let path = path.canonicalize()?;
    if !path.starts_with(&root) {
        return Err(error("Draft escapes project"));
    }
    let mut bytes = Vec::new();
    fs::File::open(&path)?
        .take(1024 * 1024 + 1)
        .read_to_end(&mut bytes)?;
    if bytes.len() > 1024 * 1024 {
        return Err(error("Draft is too large"));
    }
    let draft: Value = serde_json::from_slice(&bytes)?;
    schema::validate(&draft).map_err(error)?;
    scenemax_ide_core::Document::from_bytes(path, bytes).map_err(|e| error(e.to_string()))
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn repacks_gutters_without_touching_original_or_losing_alpha() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("source.png");
        let mut image = image::RgbaImage::new(7, 3);
        for x in [1, 2, 4, 5] {
            image.put_pixel(x, 1, image::Rgba([x as u8, 90, 100, 120]));
        }
        image.save(&path).unwrap();
        let original = fs::read(&path).unwrap();
        let prepared = prepare(&path).unwrap();
        let mut v = schema::draft();
        v["cols"] = json!(2);
        v["marginX"] = json!(1);
        v["marginY"] = json!(1);
        v["spacingX"] = json!(1);
        let result = commit(root.path(), &prepared, &v).unwrap();
        let result = image::open(result.asset).unwrap().to_rgba8();
        assert_eq!(result.dimensions(), (4, 1));
        assert_eq!(result.get_pixel(2, 0).0, [4, 90, 100, 120]);
        assert_eq!(fs::read(&path).unwrap(), original);
        assert!(commit(root.path(), &prepared, &v).is_err());
    }
    #[test]
    fn invalid_layout_does_not_publish() {
        let root = tempfile::tempdir().unwrap();
        let p = Prepared {
            width: 2,
            height: 1,
            rgba: vec![255; 8],
            source: root.path().join("unused"),
        };
        let mut v = schema::draft();
        v["cols"] = json!(3);
        assert!(commit(root.path(), &p, &v).is_err());
        assert!(!root.path().join("resources").exists());
    }
}
