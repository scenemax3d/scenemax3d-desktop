//! Validate property drafts before changing the document. No I/O or ECS side effects.
use super::*;
use scenemax_ide_services::scene3d::Entity3d;
use serde_json::{Value, json};
use std::collections::BTreeMap;

pub(super) fn transaction(
    e: &Entity3d,
    scene: &Scene3d,
    values: &BTreeMap<String, String>,
    proportional: bool,
) -> Result<Vec<(String, Value)>, String> {
    let mut patch = Vec::new();
    let number = |key: &str, text: &str| -> Result<f32, String> {
        text.trim()
            .parse::<f32>()
            .ok()
            .filter(|v| v.is_finite() && v.abs() < 1e10)
            .ok_or_else(|| format!("{key}: enter a finite number"))
    };
    for (key, original) in [
        ("position", e.position),
        ("scale", e.scale),
        (
            "cinematicTargetOffset",
            std::array::from_fn(|i| {
                e.properties["cinematicTargetOffset"][i]
                    .as_f64()
                    .unwrap_or(0.) as f32
            }),
        ),
    ] {
        let mut vector = original;
        let mut changed = Vec::new();
        for (i, n) in vector.iter_mut().enumerate() {
            if let Some(text) = values.get(&format!("{key}:{i}")) {
                *n = number(key, text)?;
                changed.push(i);
            }
        }
        if !changed.is_empty() {
            if key == "scale" && proportional {
                if changed.len() != 1 {
                    return Err("With proportional scale, change one axis at a time".into());
                }
                let axis = changed[0];
                if original[axis].abs() < 1e-8 {
                    return Err("Disable proportional scale to change a zero axis".into());
                }
                vector = original.map(|v| v * vector[axis] / original[axis]);
            }
            patch.push((key.into(), json!(vector)));
        }
    }
    let q = Quat::from_array(e.rotation).normalize();
    let (y, z, x) = q.to_euler(EulerRot::YZX);
    let mut angles = [x, y, z];
    let mut rotated = false;
    for (i, angle) in angles.iter_mut().enumerate() {
        if let Some(text) = values.get(&format!("angles:{i}")) {
            *angle = number("Rotation", text)?.to_radians();
            rotated = true;
        }
    }
    if rotated {
        patch.push((
            "rotation".into(),
            json!(Quat::from_euler(EulerRot::YZX, angles[1], angles[2], angles[0]).to_array()),
        ));
    }
    let mut track = e.properties["cinematicTrackData"].clone();
    if !track.is_object() {
        track = json!({});
    }
    let mut layers = e.properties["ikLayers"].clone();
    let mut segments = e.properties["cinematicSegments"].clone();
    for (key, text) in values {
        if let Some(key) = key.strip_prefix("track:") {
            let n = number(key, text)?;
            let integer = matches!(
                key,
                "anchorCount" | "selectedStartAnchor" | "selectedEndAnchor"
            );
            if (integer && n.fract() != 0.)
                || (key == "anchorCount" && !(8. ..=4096.).contains(&n))
                || (matches!(key, "radiusX" | "radiusZ" | "previewSpeed") && n < 0.1)
                || (key.starts_with("selected") && n < -1.)
            {
                return Err(format!("Invalid {key}"));
            }
            track[key] = if integer { json!(n as i64) } else { json!(n) };
        } else if let Some(rest) = key.strip_prefix("ik:") {
            let (index, key) = rest.split_once(':').ok_or("Invalid IK field")?;
            let index: usize = index.parse().map_err(|_| "Invalid IK layer")?;
            let layer = layers.get_mut(index).ok_or("Missing IK layer")?;
            layer[key] = if key == "enabled" {
                json!(text == "true")
            } else if key == "target" {
                json!(text.trim())
            } else {
                let n = number(key, text)?;
                if n < 0. {
                    return Err(format!("IK {key} cannot be negative"));
                }
                json!(n)
            };
        } else if let Some(rest) = key.strip_prefix("segment:") {
            let (index, key) = rest.split_once(':').ok_or("Invalid segment field")?;
            let index: usize = index.parse().map_err(|_| "Invalid segment index")?;
            let segment = segments.get_mut(index).ok_or("Missing segment")?;
            let n = number(key, text)?;
            if n < 0. || (key == "speed" && n < 0.1) || (key != "speed" && n.fract() != 0.) {
                return Err(format!("Invalid segment {key}"));
            }
            segment[key] = if key == "speed" {
                json!(n)
            } else {
                json!(n as i64)
            };
        } else if let Some(key) = key.strip_prefix("number:") {
            let n = number(key, text)?;
            if n < 0. {
                return Err(format!("{key} cannot be negative"));
            }
            patch.push((key.into(), json!(n)));
        } else if !key.contains(':') {
            let value = if key == "codeText" {
                json!(text)
            } else if matches!(
                key.as_str(),
                "hidden"
                    | "multiplayer"
                    | "staticModel"
                    | "dynamicModel"
                    | "vehicleModel"
                    | "staticEntity"
                    | "colliderEntity"
            ) {
                json!(text == "true")
            } else {
                json!(text.trim())
            };
            patch.push((key.clone(), value));
            if key == "cinematicTargetEntityName" {
                let target = scene.entities.iter().find(|e| e.name == text.trim());
                patch.push((
                    "cinematicTargetEntityId".into(),
                    json!(target.map(|e| e.id.as_str()).unwrap_or("")),
                ));
            }
            if key == "attachTo" && !text.is_empty() {
                let mut target = text.as_str();
                for _ in 0..=scene.entities.len() {
                    if target == e.name {
                        return Err("Attachment would create a cycle".into());
                    }
                    let Some(next) = scene.entities.iter().find(|o| o.name == target) else {
                        break;
                    };
                    target = next.properties["attachTo"].as_str().unwrap_or("");
                    if target.is_empty() {
                        break;
                    }
                }
            }
        }
    }
    if values.keys().any(|k| k.starts_with("track:")) {
        let count = track["anchorCount"].as_i64().unwrap_or(360);
        for key in ["selectedStartAnchor", "selectedEndAnchor"] {
            if track[key].as_i64().unwrap_or(-1) >= count {
                return Err(format!("{key} must be below {count}"));
            }
        }
        patch.push(("cinematicTrackData".into(), track));
    }
    if values.keys().any(|k| k.starts_with("segment:")) {
        patch.push(("cinematicSegments".into(), segments));
    }
    if values.keys().any(|k| k.starts_with("ik:")) {
        patch.push(("ikLayers".into(), layers));
    }
    Ok(patch)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rig_table_edits_java_segment_values_without_losing_metadata() {
        let dir = tempfile::tempdir().unwrap();
        let scene = scenemax_ide_services::scene3d::load(dir.path(), r#"{"entities":[{"type":"CINEMATIC_RIG","cinematicSegments":[{"trackName":"rail","startAnchor":1,"endAnchor":8,"speed":30,"custom":true}]}]}"#).unwrap();
        let values = BTreeMap::from([
            ("segment:0:startAnchor".into(), "2".into()),
            ("segment:0:endAnchor".into(), "9".into()),
            ("segment:0:speed".into(), "45.5".into()),
            ("cinematicEaseIn".into(), "ease_in_sine".into()),
        ]);
        let patch = transaction(&scene.entities[0], &scene, &values, false).unwrap();
        let segments = &patch
            .iter()
            .find(|(k, _)| k == "cinematicSegments")
            .unwrap()
            .1;
        assert_eq!(
            segments[0],
            json!({"trackName":"rail","startAnchor":2,"endAnchor":9,"speed":45.5,"custom":true})
        );
        assert!(patch.contains(&("cinematicEaseIn".into(), json!("ease_in_sine"))));
    }
    #[test]
    fn proportional_scale_validation_and_rotation_match_java() {
        let dir = tempfile::tempdir().unwrap();
        let scene = scenemax_ide_services::scene3d::load(
            dir.path(),
            r#"{"entities":[{"type":"BOX","scale":[2,3,4]}]}"#,
        )
        .unwrap();
        let draft = BTreeMap::from([
            ("scale:0".into(), "4".into()),
            ("angles:1".into(), "90".into()),
        ]);
        let patch = transaction(&scene.entities[0], &scene, &draft, true).unwrap();
        assert_eq!(patch[0].1, json!([4., 6., 8.]));
        let expected = Quat::from_rotation_y(std::f32::consts::FRAC_PI_2);
        let array: [f32; 4] = serde_json::from_value(patch[1].1.clone()).unwrap();
        assert!(expected.abs_diff_eq(Quat::from_array(array), 1e-5));
        assert!(
            transaction(
                &scene.entities[0],
                &scene,
                &BTreeMap::from([("scale:0".into(), "NaN".into())]),
                true
            )
            .is_err()
        );
        assert!(
            transaction(&scene.entities[0], &scene, &BTreeMap::new(), true)
                .unwrap()
                .is_empty()
        );
    }
}
