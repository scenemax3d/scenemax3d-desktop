//! Source-keyframe interval resolution for explicit animation requests.
use super::*;
use scenemax_parser::animation_range::{AnimationFrameRange, FrameEndpoint};

impl AnimationToPlay {
    pub(super) fn playback_name(&self) -> String {
        format!(
            "{}{}",
            self.runtime_clip,
            scenemax_parser::animation_range::selector_key(
                self.frame_range,
                self.frame_range_name.as_deref()
            )
        )
    }
}

pub(super) fn timeline(
    request: &AnimationToPlay,
    index: usize,
    server: &AssetServer,
    assets: &mut SceneMaxRuntimeAssets,
) -> Result<Option<Vec<f32>>, String> {
    let id = request.gltf.id();
    if !assets.frame_timelines.contains_key(&id) {
        let Some(path) = server.get_path(id) else {
            return Ok(None);
        };
        let root = match path.source() {
            bevy::asset::io::AssetSourceId::Default => assets.asset_root.as_ref(),
            bevy::asset::io::AssetSourceId::Name(name) if name.as_ref() == "builtin" => {
                assets.builtin_asset_root.as_ref()
            }
            _ => None,
        }
        .ok_or_else(|| "Animation asset source has no resource root".to_owned())?;
        assets.frame_timelines.insert(
            id,
            scenemax_assets::animation_ranges::timelines(root, &root.join(path.path())),
        );
    }
    let timelines = assets
        .frame_timelines
        .get(&id)
        .ok_or("Missing animation timelines")?
        .as_ref()
        .map_err(Clone::clone)?;
    Ok(timelines
        .iter()
        .find(|t| t.index == index)
        .map(|t| t.times.clone()))
}

/// Fractional indices interpolate adjacent key times; percentages use frame count,
/// not duration. For example, 50% of [0, .1, 2] is .1 seconds, not 1 second.
pub(super) fn interval(
    range: AnimationFrameRange,
    duration: f32,
    times: Option<&[f32]>,
    base: Option<(f32, f32)>,
) -> (f32, f32) {
    let (offset, end) = base.unwrap_or((0., duration));
    let length = (end - offset).max(0.);
    let clipped;
    let times = if base.is_some() {
        clipped = times.map(|times| {
            let mut result = vec![0.];
            result.extend(
                times
                    .iter()
                    .filter(|&&t| t > offset && t < end)
                    .map(|t| t - offset),
            );
            if length > 0. {
                result.push(length);
            }
            result
        });
        clipped.as_deref()
    } else {
        times
    };
    let max_frame = times
        .filter(|t| !t.is_empty())
        .map(|t| t.len().saturating_sub(1) as f32)
        .unwrap_or(length * 24.);
    let time = |endpoint: FrameEndpoint| {
        let frame = (if endpoint.percent {
            endpoint.value / 100. * max_frame
        } else {
            endpoint.value
        })
        .clamp(0., max_frame);
        let t = if let Some(times) = times.filter(|t| !t.is_empty()) {
            let lower = (frame.floor() as usize).min(times.len() - 1);
            let upper = (lower + 1).min(times.len() - 1);
            times[lower] + (times[upper] - times[lower]) * frame.fract()
        } else {
            frame / 24.
        };
        offset + t.clamp(0., length)
    };
    let start = time(range.start);
    (start, time(range.end).max(start))
}

#[cfg(test)]
mod tests {
    use super::*;
    fn range(start: f32, end: f32, percent: bool) -> AnimationFrameRange {
        AnimationFrameRange {
            start: FrameEndpoint {
                value: start,
                percent,
            },
            end: FrameEndpoint {
                value: end,
                percent,
            },
        }
    }
    #[test]
    fn source_frame_time_mapping_handles_fractional_percent_clamping_and_subclips() {
        let times = [0., 0.1, 0.35, 0.8, 1.2, 1.6, 2.];
        let (a, b) = interval(range(1.5, 4., false), 2., Some(&times), None);
        assert!((a - 0.225).abs() < 0.0001);
        assert!((b - 1.2).abs() < 0.0001);
        assert_eq!(
            interval(range(0., 50., true), 2., Some(&times), None),
            (0., 0.8)
        );
        assert_eq!(
            interval(range(-10., 200., true), 2., Some(&times), None),
            (0., 2.)
        );
        assert_eq!(
            interval(range(4., 1., false), 2., Some(&times), None),
            (1.2, 1.2)
        );
        let (a, b) = interval(range(0., 50., true), 2., Some(&times), Some((0.1, 1.2)));
        assert!((a - 0.1).abs() < 0.0001);
        assert!((b - 0.575).abs() < 0.0001);
        assert_eq!(interval(range(24., 36., false), 2., None, None), (1., 1.5));
    }
}

pub(super) fn named_range(
    model: Option<&str>,
    name: &str,
    assets: &mut SceneMaxRuntimeAssets,
) -> Result<AnimationFrameRange, String> {
    let model = model.ok_or("Animation range requires a model resource")?;
    let key = (model.to_lowercase(), name.to_lowercase());
    if !assets.named_frame_bounds.contains_key(&key) {
        let root = assets
            .asset_root
            .as_deref()
            .ok_or("Animation range requires a resource root")?;
        let result = scenemax_assets::model_animation_frame_bounds(
            root,
            assets.builtin_asset_root.as_deref(),
            model,
            name,
        );
        assets.named_frame_bounds.insert(key.clone(), result);
    }
    let (start, end) = *assets
        .named_frame_bounds
        .get(&key)
        .unwrap()
        .as_ref()
        .map_err(Clone::clone)?;
    Ok(AnimationFrameRange {
        start: FrameEndpoint {
            value: start,
            percent: false,
        },
        end: FrameEndpoint {
            value: end,
            percent: false,
        },
    })
}
