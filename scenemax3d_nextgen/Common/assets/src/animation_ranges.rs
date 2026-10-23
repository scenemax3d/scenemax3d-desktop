//! Java-compatible named animation records and glTF keyframe timelines.
use base64::Engine;
use serde::{Deserialize, Serialize};
use std::{io::Read, path::Path};

/// A named, non-destructive interval in a model's source animation.
#[derive(Clone, Debug, Deserialize, Serialize, PartialEq)]
pub struct Record {
    /// Runtime clip identifier.
    pub name: String,
    /// Source glTF animation name (or `Animation N` for unnamed clips).
    #[serde(default, rename = "sourceAnimation")]
    pub source: String,
    /// Inclusive keyframe index at the beginning.
    pub start: usize,
    /// Endpoint keyframe index.
    pub end: usize,
}
/// Source animation timing, using the longest transform track as in Java.
#[derive(Clone, Debug)]
pub struct Timeline {
    /// Stable source name.
    pub name: String,
    /// Source animation index.
    pub index: usize,
    /// Keyframe times in seconds.
    pub times: Vec<f32>,
}
impl Timeline {
    /// Resolve a validated interval without silently clamping invalid records.
    pub fn interval(&self, record: &Record) -> Result<(f32, f32), String> {
        if record.start >= record.end || record.end >= self.times.len() {
            return Err(format!(
                "{}: require 0 ≤ start < end ≤ {}",
                record.name,
                self.times.len().saturating_sub(1)
            ));
        }
        Ok((self.times[record.start], self.times[record.end]))
    }
}
/// Read records, honoring Java's legacy shared source field.
pub fn records(model: &serde_json::Value) -> Result<Vec<Record>, String> {
    let mut rows: Vec<Record> = serde_json::from_value(
        model
            .get("animationFrameRanges")
            .cloned()
            .unwrap_or_else(|| serde_json::json!([])),
    )
    .map_err(|e| e.to_string())?;
    let fallback = model["animationFrameRangesSourceAnimation"]
        .as_str()
        .or_else(|| model["sourceAnimation"].as_str())
        .unwrap_or("");
    let mut names = std::collections::HashSet::new();
    for row in &mut rows {
        if row.source.trim().is_empty() {
            row.source = fallback.into();
        }
        if row.name.trim().is_empty()
            || !names.insert(row.name.to_lowercase())
            || row.start >= row.end
        {
            return Err("Records need unique names and start < end".into());
        }
    }
    Ok(rows)
}
fn read(path: &Path) -> Result<Vec<u8>, String> {
    const MAX: u64 = 512 * 1024 * 1024;
    let mut bytes = Vec::new();
    crate::filesystem::File::open(path)
        .map_err(|e| e.to_string())?
        .take(MAX + 1)
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    if bytes.len() as u64 > MAX {
        return Err("glTF buffer exceeds 512 MiB".into());
    }
    Ok(bytes)
}
/// Read glTF/GLB timing with contained sidecars, embedded buffers and sparse accessors.
pub fn timelines(root: &Path, path: &Path) -> Result<Vec<Timeline>, String> {
    let root = crate::filesystem::canonicalize(&root).map_err(|e| e.to_string())?;
    let path = crate::filesystem::canonicalize(&path).map_err(|e| e.to_string())?;
    if !path.starts_with(&root) {
        return Err("Model is outside its resource root".into());
    }
    let gltf = gltf::Gltf::from_slice(&read(&path)?).map_err(|e| e.to_string())?;
    let mut buffers = Vec::new();
    for buffer in gltf.buffers() {
        let bytes = match buffer.source() {
            gltf::buffer::Source::Bin => gltf.blob.clone().ok_or("Missing GLB buffer")?,
            gltf::buffer::Source::Uri(uri) if uri.starts_with("data:") => {
                let (header, data) = uri.split_once(',').ok_or("Malformed data URI")?;
                if !header.ends_with(";base64") {
                    return Err("Unsupported data URI".into());
                }
                base64::engine::general_purpose::STANDARD
                    .decode(data)
                    .map_err(|e| e.to_string())?
            }
            gltf::buffer::Source::Uri(uri) => {
                let uri = percent_encoding::percent_decode_str(uri)
                    .decode_utf8()
                    .map_err(|e| e.to_string())?;
                let sidecar = crate::filesystem::canonicalize(
                    path.parent()
                        .ok_or("Missing model folder")?
                        .join(uri.as_ref()),
                )
                .map_err(|e| e.to_string())?;
                if !sidecar.starts_with(&root) {
                    return Err("glTF buffer escapes resource root".into());
                }
                read(&sidecar)?
            }
        };
        if bytes.len() < buffer.length() {
            return Err("Truncated glTF buffer".into());
        }
        buffers.push(bytes);
    }
    gltf.animations()
        .map(|animation| {
            let mut times = Vec::new();
            let mut duration = 0.0_f32;
            for channel in animation.channels() {
                if channel.sampler().input().count() > 4_000_000 {
                    return Err("Animation input exceeds four million frames".into());
                }
                let reader = channel.reader(|b| buffers.get(b.index()).map(Vec::as_slice));
                let candidate: Vec<f32> = reader
                    .read_inputs()
                    .ok_or("Missing animation input")?
                    .collect();
                duration = duration.max(candidate.last().copied().unwrap_or(0.));
                if channel.target().property() != gltf::animation::Property::MorphTargetWeights
                    && candidate.len() > times.len()
                {
                    times = candidate;
                }
            }
            if times.is_empty() && duration.is_finite() && duration > 0. {
                let count = (duration * 24.).round() as usize;
                if count > 4_000_000 {
                    return Err("Animation duration exceeds supported frame count".into());
                }
                times = (0..=count)
                    .map(|i| (i as f32 / 24.).min(duration))
                    .collect();
            }
            if times.iter().any(|t| !t.is_finite() || *t < 0.)
                || times.windows(2).any(|w| w[0] >= w[1])
            {
                return Err("Animation times must be finite and strictly increasing".into());
            }
            Ok(Timeline {
                name: animation
                    .name()
                    .map(str::to_owned)
                    .unwrap_or_else(|| format!("Animation {}", animation.index() + 1)),
                index: animation.index(),
                times,
            })
        })
        .collect()
}

/// Named bracket selectors read numeric endpoints independently of record source.
pub fn frame_bounds(model: &serde_json::Value, selector: &str) -> Result<(f32, f32), String> {
    let row = model
        .get("animationFrameRanges")
        .and_then(serde_json::Value::as_array)
        .and_then(|rows| {
            rows.iter().find(|row| {
                row["name"]
                    .as_str()
                    .is_some_and(|name| name.trim().eq_ignore_ascii_case(selector))
            })
        })
        .ok_or_else(|| format!("Animation frame range '{selector}' was not found"))?;
    let endpoint = |key: &str| -> Result<f32, String> {
        let value = row[key]
            .as_f64()
            .ok_or_else(|| format!("Animation frame range '{selector}' has invalid {key}"))?
            as f32;
        if value.is_finite() {
            Ok(value)
        } else {
            Err(format!(
                "Animation frame range '{selector}' has non-finite {key}"
            ))
        }
    };
    Ok((endpoint("start")?, endpoint("end")?))
}

#[cfg(test)]
mod selector_tests {
    use super::*;
    #[test]
    fn named_bounds_are_case_insensitive_fractional_and_source_independent() {
        let model = serde_json::json!({"animationFrameRanges": [
            {"name":" Segment-A ", "start":1.5, "end":5.25, "sourceAnimation":"unrelated"},
            {"name":"Reverse", "start":9, "end":-2},
            {"name":"bad", "start":"1", "end":3},
            {"name":"overflow", "start":1e100, "end":3}
        ]});
        assert_eq!(frame_bounds(&model, "segment-a").unwrap(), (1.5, 5.25));
        assert_eq!(frame_bounds(&model, "reverse").unwrap(), (9., -2.));
        for name in ["segmenta", "missing", "bad", "overflow"] {
            assert!(frame_bounds(&model, name).is_err());
        }
    }
}
