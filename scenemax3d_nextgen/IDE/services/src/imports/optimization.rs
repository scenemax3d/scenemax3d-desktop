//! Native glTF optimization with conservative animation/material preservation.
use super::*;
use std::process::{Command, Stdio};
/// Run the bundled native optimizer, keeping named nodes and animation tracks.
pub(super) fn pack(input: &Path, output: &Path, ratio: Option<f64>) -> io::Result<()> {
    let exe = std::env::var_os("SCENEMAX_GLTFPACK")
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../Tools/vendor/gltfpack/gltfpack.exe")
        });
    let log = tempfile::NamedTempFile::new()?;
    let mut cmd = Command::new(exe);
    cmd.arg("-i")
        .arg(input)
        .arg("-o")
        .arg(output)
        .args(["-noq", "-kn", "-km", "-ke", "-ac", "-af", "0"]);
    if let Some(ratio) = ratio {
        cmd.arg("-si")
            .arg(ratio.to_string())
            .args(["-slb", "-se", "0.01"]);
    }
    cmd.stdout(Stdio::from(log.reopen()?))
        .stderr(Stdio::from(log.reopen()?));
    files::hidden(&mut cmd);
    let mut child = cmd.spawn()?;
    let start = std::time::Instant::now();
    loop {
        if let Some(status) = child.try_wait()? {
            if status.success() {
                return Ok(());
            }
            return Err(error(format!(
                "Model optimization failed: {}",
                fs::read_to_string(log.path())?
            )));
        }
        if start.elapsed().as_secs() > 300 {
            child.kill()?;
            child.wait()?;
            return Err(error("Model optimization timed out"));
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
}
pub(super) fn optimize(
    input: &Path,
    folder: &Path,
    options: &Value,
    is_static: bool,
) -> io::Result<PathBuf> {
    let ratio = if options["simplify"].as_bool() == Some(true) {
        if !is_static {
            return Err(error("Mesh reduction is available for static models only"));
        }
        Some(
            options["ratio"]
                .as_f64()
                .filter(|r| *r > 0. && *r <= 1.)
                .ok_or_else(|| error("Mesh ratio must be greater than 0 and at most 1"))?,
        )
    } else {
        None
    };
    if ratio.is_some()
        && files::gltf(input)?["skins"]
            .as_array()
            .is_some_and(|s| !s.is_empty())
    {
        return Err(error(
            "Mesh reduction cannot be applied to a skinned character",
        ));
    }
    let work = folder.join("optimized");
    fs::create_dir_all(&work)?;
    let gltf = work.join("model.gltf");
    pack(input, &gltf, ratio)?;
    if options["textures"].as_bool() == Some(true) {
        textures(&gltf, options)?;
    }
    let output = work.join("model.glb");
    pack(&gltf, &output, None)?;
    Ok(output)
}
fn textures(file: &Path, options: &Value) -> io::Result<()> {
    use image::ImageEncoder;
    let root = file
        .parent()
        .ok_or_else(|| error("Missing optimization directory"))?;
    let mut data: Value = serde_json::from_slice(&fs::read(file)?)?;
    let max = options["maxTextureSize"]
        .as_f64()
        .filter(|n| n.is_finite() && *n >= 0. && *n <= 16384. && n.fract() == 0.)
        .ok_or_else(|| error("Texture limit must be between 0 and 16384"))? as u32;
    let quality = options["jpegQuality"]
        .as_f64()
        .filter(|n| n.is_finite() && (1.0..=100.0).contains(n) && n.fract() == 0.)
        .ok_or_else(|| error("JPEG quality must be between 1 and 100"))? as u8;
    let mut color_images = std::collections::HashSet::new();
    for material in data["materials"].as_array().into_iter().flatten() {
        for key in ["baseColorTexture", "metallicRoughnessTexture"] {
            if let Some(index) = material["pbrMetallicRoughness"][key]["index"].as_u64()
                && let Some(source) = data["textures"][index as usize]["source"].as_u64()
            {
                color_images.insert(source as usize);
            }
        }
    }
    let count = data["images"].as_array().map_or(0, Vec::len);
    for index in 0..count {
        let definition = &data["images"][index];
        let bytes = if let Some(uri) = definition["uri"].as_str() {
            let path = root.join(uri).canonicalize()?;
            if !path.starts_with(root.canonicalize()?) {
                return Err(error("Optimized texture escapes its folder"));
            }
            fs::read(path)?
        } else if let Some(view) = definition["bufferView"].as_u64() {
            let view = &data["bufferViews"][view as usize];
            let buffer = &data["buffers"][view["buffer"].as_u64().unwrap_or(0) as usize];
            let path = root
                .join(
                    buffer["uri"]
                        .as_str()
                        .ok_or_else(|| error("Missing image buffer"))?,
                )
                .canonicalize()?;
            if !path.starts_with(root.canonicalize()?) {
                return Err(error("Image buffer escapes its folder"));
            }
            let bytes = fs::read(path)?;
            let start = view["byteOffset"].as_u64().unwrap_or(0) as usize;
            let end = start
                .checked_add(view["byteLength"].as_u64().unwrap_or(0) as usize)
                .ok_or_else(|| error("Invalid image buffer"))?;
            bytes
                .get(start..end)
                .ok_or_else(|| error("Invalid image buffer"))?
                .to_vec()
        } else {
            return Err(error("Unsupported image storage"));
        };
        let mut image = image::load_from_memory(&bytes).map_err(|e| error(e.to_string()))?;
        if max > 0 && (image.width() > max || image.height() > max) {
            image = image.resize(max, max, image::imageops::FilterType::Lanczos3);
        }
        let opaque = image.to_rgba8().pixels().all(|p| p[3] == 255);
        let jpeg = opaque
            && color_images.contains(&index)
            && options["convertOpaquePng"].as_bool() == Some(true);
        let name = format!("image-{index}.{}", if jpeg { "jpg" } else { "png" });
        let mut out = fs::File::create(root.join(&name))?;
        if jpeg {
            image::codecs::jpeg::JpegEncoder::new_with_quality(&mut out, quality)
                .encode_image(&image)
                .map_err(|e| error(e.to_string()))?;
        } else {
            let pixels = image.to_rgba8();
            image::codecs::png::PngEncoder::new(&mut out)
                .write_image(
                    &pixels,
                    image.width(),
                    image.height(),
                    image::ExtendedColorType::Rgba8,
                )
                .map_err(|e| error(e.to_string()))?;
        }
        let definition = data["images"][index]
            .as_object_mut()
            .ok_or_else(|| error("Invalid image definition"))?;
        definition.remove("bufferView");
        definition.remove("mimeType");
        definition.insert("uri".into(), json!(name));
    }
    fs::write(file, serde_json::to_vec_pretty(&data)?)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn texture_optimization_resizes_preserves_alpha_and_image_names() {
        let root = tempfile::tempdir().unwrap();
        for (name, alpha) in [("opaque", 255), ("alpha", 80)] {
            image::RgbaImage::from_pixel(16, 8, image::Rgba([30, 100, 200, alpha]))
                .save(root.path().join(format!("{name}.png")))
                .unwrap();
        }
        let path = root.path().join("model.gltf");
        fs::write(&path,serde_json::to_vec(&json!({"images":[{"uri":"opaque.png","name":"color"},{"uri":"alpha.png"}],
            "textures":[{"source":0},{"source":1}],"materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}},{"pbrMetallicRoughness":{"baseColorTexture":{"index":1}}}]})).unwrap()).unwrap();
        let mut options = scenemax_ide_core::model_import::draft()["optimization"].clone();
        options["maxTextureSize"] = json!(8);
        textures(&path, &options).unwrap();
        let data: Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
        assert_eq!(data["images"][0]["name"], "color");
        assert_eq!(data["images"][0]["uri"], "image-0.jpg");
        assert_eq!(data["images"][1]["uri"], "image-1.png");
        let result = image::open(root.path().join("image-1.png"))
            .unwrap()
            .to_rgba8();
        assert_eq!(result.dimensions(), (8, 4));
        assert_eq!(result.get_pixel(0, 0)[3], 80);
    }
}
