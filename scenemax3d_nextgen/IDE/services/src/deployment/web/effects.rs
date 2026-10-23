//! Conservative feature preflight and integrity-checked, offline browser runtime.
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    fs,
    io::{self, Read},
    path::Path,
};

const FILES: &[(&str, &[u8])] = &[
    (
        "background.mjs",
        include_bytes!("../../../../../Engine/effects/web/background.mjs"),
    ),
    (
        "resources.mjs",
        include_bytes!("../../../../../Engine/effects/web/resources.mjs"),
    ),
    (
        "README.txt",
        include_bytes!("../../../../../Engine/effects/web/MAINTENANCE.md"),
    ),
    (
        "bridge.js",
        include_bytes!("../../../../../Engine/effects/web/bridge.js"),
    ),
    (
        "playback.mjs",
        include_bytes!("../../../../../Engine/effects/web/playback.mjs"),
    ),
    (
        "vendor/effekseer.js",
        include_bytes!("../../../../../Engine/effects/web/vendor/effekseer.js"),
    ),
    (
        "vendor/effekseer.d.ts",
        include_bytes!("../../../../../Engine/effects/web/vendor/effekseer.d.ts"),
    ),
    (
        "vendor/effekseer-webgpu.js",
        include_bytes!("../../../../../Engine/effects/web/vendor/effekseer-webgpu.js"),
    ),
    (
        "vendor/effekseer-webgpu.wasm",
        include_bytes!("../../../../../Engine/effects/web/vendor/effekseer-webgpu.wasm"),
    ),
    (
        "vendor/LICENSE-EffekseerForWeb.txt",
        include_bytes!("../../../../../Engine/effects/web/vendor/LICENSE-EffekseerForWeb.txt"),
    ),
    (
        "vendor/LICENSE-Effekseer.txt",
        include_bytes!("../../../../../Engine/effects/web/vendor/LICENSE-Effekseer.txt"),
    ),
    (
        "vendor/LICENSE-dependencies.txt",
        include_bytes!("../../../../../Engine/effects/web/vendor/LICENSE-dependencies.txt"),
    ),
    (
        "vendor/LICENSE-LLGI.txt",
        include_bytes!("../../../../../Engine/effects/web/vendor/LICENSE-LLGI.txt"),
    ),
    (
        "vendor/LICENSE-Emscripten.txt",
        include_bytes!("../../../../../Engine/effects/web/vendor/LICENSE-Emscripten.txt"),
    ),
    (
        "vendor/SHA256SUMS",
        include_bytes!("../../../../../Engine/effects/web/vendor/SHA256SUMS"),
    ),
];

pub(super) fn bundle(site: &Path) -> io::Result<()> {
    let sums = include_str!("../../../../../Engine/effects/web/vendor/SHA256SUMS");
    for (name, bytes) in FILES {
        if let Some(file) = name.strip_prefix("vendor/").filter(|f| *f != "SHA256SUMS") {
            let expected = sums
                .lines()
                .find_map(|line| {
                    line.split_once("  ")
                        .filter(|(_, n)| *n == file)
                        .map(|(h, _)| h)
                })
                .ok_or_else(|| io::Error::other(format!("Missing Effekseer checksum: {file}")))?;
            if format!("{:x}", Sha256::digest(bytes)) != expected {
                return Err(io::Error::other(format!(
                    "Bundled Effekseer checksum mismatch: {file}"
                )));
            }
        }
        let path = site.join("effects").join(name);
        fs::create_dir_all(path.parent().unwrap())?;
        fs::write(path, bytes)?;
    }
    Ok(())
}

fn invalid(message: &str) -> io::Error {
    io::Error::other(message)
}
struct Reader<'a> {
    bytes: &'a [u8],
    pos: usize,
}
impl<'a> Reader<'a> {
    fn take(&mut self, n: usize) -> io::Result<&'a [u8]> {
        let end = self
            .pos
            .checked_add(n)
            .ok_or_else(|| invalid("Effect size overflow"))?;
        let data = self
            .bytes
            .get(self.pos..end)
            .ok_or_else(|| invalid("Truncated effect metadata"))?;
        self.pos = end;
        Ok(data)
    }
    fn u16(&mut self) -> io::Result<usize> {
        Ok(u16::from_le_bytes(self.take(2)?.try_into().unwrap()) as usize)
    }
    fn u32(&mut self) -> io::Result<usize> {
        Ok(u32::from_le_bytes(self.take(4)?.try_into().unwrap()) as usize)
    }
    fn dictionary(&mut self) -> io::Result<HashMap<usize, String>> {
        let mut result = HashMap::new();
        for _ in 0..self.u16()? {
            let len = self.u16()?;
            let value = String::from_utf8(self.take(len)?.to_vec()).map_err(io::Error::other)?;
            let key = self.u16()?;
            if result.insert(key, value).is_some() {
                return Err(invalid("Duplicate effect metadata key"));
            }
        }
        Ok(result)
    }
    fn nodes(
        &mut self,
        keys: &HashMap<usize, String>,
        values: &HashMap<usize, String>,
        parent: &str,
        depth: usize,
    ) -> io::Result<bool> {
        let mut distortion = false;
        if depth > 64 {
            return Err(invalid("Effect metadata is too deeply nested"));
        }
        for _ in 0..self.u16()? {
            let key = self.u16()?;
            let name = keys
                .get(&key)
                .ok_or_else(|| invalid("Unknown effect metadata key"))?;
            let path = format!("{parent}/{name}");
            if self.u32()? != 0 {
                let key = self.u16()?;
                let value = values
                    .get(&key)
                    .ok_or_else(|| invalid("Unknown effect metadata value"))?;
                distortion |= check_feature(&path, value)?;
            }
            if self.u32()? != 0 {
                distortion |= self.nodes(keys, values, &path, depth + 1)?;
            }
        }
        Ok(distortion)
    }
}

fn check_feature(path: &str, value: &str) -> io::Result<bool> {
    let enabled =
        value.eq_ignore_ascii_case("true") || value.parse::<f64>().is_ok_and(|v| v != 0.0);
    let unsupported =
        if path.ends_with("/RendererCommonValues/Material") && !matches!(value, "0" | "6" | "7") {
            Some("custom materials")
        } else if path.contains("/SoftParticle") && enabled {
            Some("soft particles")
        } else if path.ends_with("/GpuParticles/Enabled") && enabled {
            Some("GPU-simulated particles")
        } else {
            None
        };
    match unsupported {
        Some(feature) => Err(invalid(&format!(
            "Web effects do not yet support {feature}. Use a supported effect variant or a desktop export."
        ))),
        None => Ok(
            (path.ends_with("/RendererCommonValues/Material") && value == "6")
                || (path.ends_with("/RendererCommonValues/Distortion") && enabled),
        ),
    }
}

pub(super) fn validate(bytes: &[u8]) -> io::Result<bool> {
    let mut distortion = false;
    // Resource-table parsing also checks for a runtime payload.
    for (kind, _) in crate::imports::effect::references(bytes)? {
        distortion |= kind == "Distortion texture";
        if kind == "Material" {
            return Err(invalid(
                "Web effects do not yet support custom materials. Use a supported effect variant or a desktop export.",
            ));
        }
    }
    if !bytes.starts_with(b"EFKE") {
        return Err(invalid(
            "Web effects require .efkefc files with editor metadata for feature validation. Re-export from Effekseer.",
        ));
    }
    let mut chunks = Reader { bytes, pos: 8 };
    let mut editor = None;
    while chunks.pos < bytes.len() {
        let tag = chunks.take(4)?;
        let len = chunks.u32()?;
        let data = chunks.take(len)?;
        if tag == b"EDIT" {
            editor = Some(data);
        }
    }
    let editor = editor.ok_or_else(|| {
        invalid(
            "Effect has no editor metadata for Web feature validation. Re-export from Effekseer.",
        )
    })?;
    let mut decoded = Vec::new();
    flate2::read::ZlibDecoder::new(editor)
        .take(16 * 1024 * 1024 + 1)
        .read_to_end(&mut decoded)?;
    if decoded.len() > 16 * 1024 * 1024 {
        return Err(invalid("Effect editor metadata exceeds 16 MiB"));
    }
    let mut reader = Reader {
        bytes: &decoded,
        pos: 0,
    };
    let keys = reader.dictionary()?;
    let values = reader.dictionary()?;
    distortion |= reader.nodes(&keys, &values, "", 0)?;
    if reader.pos != decoded.len() {
        return Err(invalid("Unrecognized effect metadata layout"));
    }
    Ok(distortion)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn bundled_runtime_matches_pinned_checksums() {
        let root = tempfile::tempdir().unwrap();
        bundle(root.path()).unwrap();
        assert!(
            root.path()
                .join("effects/vendor/effekseer-webgpu.wasm")
                .metadata()
                .unwrap()
                .len()
                > 1_000_000
        );
    }
    #[test]
    fn unsupported_features_are_actionable() {
        assert!(
            check_feature("/Node/RendererCommonValues/Material", "128")
                .unwrap_err()
                .to_string()
                .contains("custom materials")
        );
        assert!(check_feature("/Node/RendererCommonValues/Material", "0").is_ok());
        assert!(check_feature("/Node/RendererCommonValues/Material", "6").unwrap());
        assert!(check_feature("/Node/RendererCommonValues/Distortion", "True").unwrap());
        assert!(
            check_feature(
                "/Node/AdvancedRendererCommonValues/SoftParticleParams/DistanceFar",
                "1"
            )
            .is_err()
        );
        assert!(check_feature("/Node/GpuParticles/Enabled", "True").is_err());
        assert!(validate(b"EFKE").is_err());
    }
    #[test]
    fn upstream_sprite_fixture_passes_bounded_metadata_validation() {
        assert!(!validate(include_bytes!("../../../../../Tests/fixtures/web_effects/project/resources/effects/fixture/effect.efkefc")).unwrap());
    }
    #[test]
    fn distortion_fixture_requests_scene_color_capture() {
        assert!(validate(include_bytes!("../../../../../Tests/fixtures/web_distortion/project/resources/effects/screen_warp/effect.efkefc")).unwrap());
    }
}
