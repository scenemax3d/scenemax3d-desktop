//! Lossless native IK authoring definitions, compatible with Java assets.
use serde_json::{Value, json};
/// Supported original solver names.
pub const SOLVERS: &[&str] = &[
    "TwoBoneIK",
    "ThreeBoneIK",
    "LookAtIK",
    "FABRIK",
    "FootIK",
    "AimIK",
];
/// Create an unbound layer.
pub fn layer(name: &str) -> Value {
    let mut v = json!({"id": "", "name": "IK Layer", "solverType": "TwoBoneIK", "enabled": true, "weight": 1.0, "priority": 0, "blendTime": 0.0, "debug": false, "target": "", "poleTarget": "", "matchTargetRotation": false, "allowStretch": false, "maxStretch": 1.05, "rotationSpeed": 20.0, "maxAngle": 90.0, "smoothing": 0.15, "iterations": 8, "tolerance": 0.01, "groundDistance": 1.0, "slopeAlignmentStrength": 0.75, "pelvisCompensation": 0.5, "blendSpeed": 10.0, "chain": {"root": "", "middle": "", "secondMiddle": "", "end": "", "start": ""}, "affectedJoints": [], "positionOffset": [0, 0, 0], "rotationOffset": [0, 0, 0], "aimAxis": [0, 0, 1]});
    v["id"] = json!(format!("layer_{}", slug(name)));
    v["name"] = json!(name);
    v
}
fn slug(s: &str) -> String {
    let s = s
        .to_ascii_lowercase()
        .split(|c: char| !c.is_ascii_alphanumeric())
        .filter(|s| !s.is_empty())
        .collect::<Vec<_>>()
        .join("_");
    if s.is_empty() { "definition".into() } else { s }
}
/// New runtime asset with one editable layer.
pub fn template(name: &str, solver: &str) -> Result<Value, String> {
    if name.trim().is_empty() {
        return Err("Enter an IK asset name".into());
    }
    if !SOLVERS.contains(&solver) {
        return Err("Unknown IK solver".into());
    }
    let mut first = layer(name.trim());
    first["solverType"] = json!(solver);
    Ok(
        json!({"type":"SceneMaxIKDefinition","schemaVersion":"1.0","id":format!("ik_{}",slug(name)),"name":name.trim(),"targetModelId":"","layers":[first],"designerMetadata":{"previewTargetModel":"Sphere","previewTargetBone":"","previewPolePosition":[0,1,1],"previewTargetPosition":[1,1,0],"previewTargetRotation":[0,0,0],"previewTargetScale":[1,1,1],"previewModelPosition":[0,0,0],"previewModelRotation":[0,0,0],"previewModelScale":[1,1,1]}}),
    )
}
fn fill(v: &mut Value, defaults: &Value) {
    if let (Some(v), Some(d)) = (v.as_object_mut(), defaults.as_object()) {
        for (k, d) in d {
            if let Some(value) = v.get_mut(k) {
                if value.is_object() {
                    fill(value, d);
                }
            } else {
                v.insert(k.clone(), d.clone());
            }
        }
    }
}
/// Load current or legacy single-layer JSON without discarding extensions.
pub fn parse(text: &str) -> Result<Value, String> {
    let mut v: Value = serde_json::from_str(text).map_err(|e| e.to_string())?;
    if !v.is_object() {
        return Err("IK asset must be an object".into());
    }
    if v.get("layers").is_none() && v.get("solverType").is_some() {
        v["layers"] = json!([v.clone()]);
    }
    fill(&mut v, &template("IK Definition", "TwoBoneIK")?);
    let layers = v["layers"]
        .as_array_mut()
        .ok_or("Layers must be an array")?;
    for l in layers {
        if !l.is_object() {
            return Err("Layer must be an object".into());
        }
        if l.get("chain").is_none() {
            l["chain"] = json!({"root":l["rootJoint"].as_str().unwrap_or(""),"middle":l["middleJoint"].as_str().unwrap_or(""),"secondMiddle":l["secondMiddleJoint"].as_str().or(l["middle2Joint"].as_str()).unwrap_or(""),"end":l["endJoint"].as_str().unwrap_or(""),"start":l["startJoint"].as_str().unwrap_or("")});
        }
        fill(l, &layer("IK Layer"));
    }
    Ok(v)
}
/// Structural validation allows saving incomplete authoring work.
pub fn validate(v: &Value) -> Result<(), String> {
    if v["id"]
        .as_str()
        .is_none_or(|s| s.trim().is_empty() || s.contains(['/', '\\']) || s == "..")
    {
        return Err("IK ID is required".into());
    }
    let layers = v["layers"].as_array().ok_or("Layers must be an array")?;
    let mut ids = std::collections::HashSet::new();
    for l in layers {
        let id = l["id"].as_str().unwrap_or("");
        if id.is_empty() || !ids.insert(id.to_string()) {
            return Err("Layer IDs must be nonempty and unique".into());
        }
        if !SOLVERS.contains(&l["solverType"].as_str().unwrap_or("")) {
            return Err("Unknown IK solver".into());
        }
        if !l["chain"].is_object()
            || !l["affectedJoints"]
                .as_array()
                .is_some_and(|a| a.iter().all(Value::is_string))
        {
            return Err("Invalid joint configuration".into());
        }
        for (key, min, max) in [
            ("weight", 0., 1.),
            ("blendTime", 0., 3600.),
            ("maxStretch", 1., 5.),
            ("iterations", 1., 64.),
            ("maxAngle", 0., 180.),
            ("tolerance", 0.000001, 100.),
        ] {
            if !l[key]
                .as_f64()
                .is_some_and(|n| n.is_finite() && n >= min && n <= max)
            {
                return Err(format!("{key} must be between {min} and {max}"));
            }
        }
        for key in ["positionOffset", "rotationOffset", "aimAxis"] {
            if !l[key].as_array().is_some_and(|a| {
                a.len() == 3 && a.iter().all(|n| n.as_f64().is_some_and(f64::is_finite))
            }) {
                return Err(format!("{key} must contain three finite numbers"));
            }
        }
    }
    Ok(())
}
/// Chain/target issues shown while authoring an incomplete asset.
pub fn diagnostics(v: &Value) -> Vec<String> {
    let mut out = vec![];
    if let Err(e) = validate(v) {
        out.push(e);
    }
    for l in v["layers"].as_array().into_iter().flatten() {
        let solver = l["solverType"].as_str().unwrap_or("");
        let id = l["id"].as_str().unwrap_or("Layer");
        let keys: &[&str] = match solver {
            "TwoBoneIK" | "FootIK" => &["root", "middle", "end"],
            "ThreeBoneIK" => &["root", "middle", "secondMiddle", "end"],
            "FABRIK" => &["start", "end"],
            _ => &[],
        };
        for key in keys {
            if l["chain"][key].as_str().unwrap_or("").is_empty() {
                out.push(format!("{id}: choose {key} joint"));
            }
        }
        if matches!(solver, "LookAtIK" | "AimIK")
            && l["affectedJoints"].as_array().is_none_or(|a| a.is_empty())
            && l["chain"]["end"].as_str().unwrap_or("").is_empty()
        {
            out.push(format!("{id}: choose affected joints"));
        }
        if l["target"].as_str().unwrap_or("").is_empty() {
            out.push(format!(
                "{id}: set a runtime target object or object.\"joint\""
            ));
        }
    }
    out
}
/// Edit a property or perform a layer collection action.
pub fn edit(text: &str, key: &str, value: Value, index: usize) -> Result<String, String> {
    let mut v = parse(text)?;
    if key == "target_bone" {
        let index = value["index"].as_u64().ok_or("Select a layer")? as usize;
        let bone = value["bone"].as_str().ok_or("Invalid bone")?;
        let l = v["layers"].get_mut(index).ok_or("Select a layer")?;
        let old = l["target"].as_str().unwrap_or("");
        let object = old
            .split('.')
            .next()
            .filter(|s| !s.is_empty())
            .unwrap_or("target");
        l["target"] = json!(if bone.is_empty() {
            object.to_owned()
        } else {
            format!(
                "{object}.{}",
                serde_json::to_string(bone).map_err(|e| e.to_string())?
            )
        });
        v["designerMetadata"]["previewTargetBone"] = json!(bone);
    } else if key.starts_with('/') {
        let target = v.pointer_mut(key).ok_or("Unknown IK property")?;
        *target = value;
    } else {
        let layers = v["layers"].as_array_mut().ok_or("Missing layers")?;
        match key {
            "add" => {
                let mut l = layer("IK Layer");
                unique_id(&mut l, layers);
                layers.push(l);
            }
            "duplicate" => {
                let mut l = layers.get(index).ok_or("Select a layer")?.clone();
                l["name"] = json!(format!("{} Copy", l["name"].as_str().unwrap_or("Layer")));
                unique_id(&mut l, layers);
                layers.insert(index + 1, l);
            }
            "delete" => {
                if index < layers.len() {
                    layers.remove(index);
                }
            }
            "preset" => {
                let mut l = value;
                unique_id(&mut l, layers);
                layers.push(l);
            }
            _ => return Err("Unknown IK edit".into()),
        }
    }
    serde_json::to_string_pretty(&v).map_err(|e| e.to_string())
}
fn unique_id(l: &mut Value, layers: &[Value]) {
    let base = l["id"].as_str().unwrap_or("layer").to_string();
    let mut id = base.clone();
    let mut i = 2;
    while layers.iter().any(|l| l["id"] == id) {
        id = format!("{base}_{i}");
        i += 1;
    }
    l["id"] = json!(id);
}
/// Original use-case preset captions.
pub const PRESETS: &[&str] = &[
    "Right hand reach",
    "Left hand reach",
    "Right arm with shoulder",
    "Left arm with shoulder",
    "Right foot placement",
    "Left foot placement",
    "Head look at",
    "Upper body aim",
];
/// Match the chosen use case against the loaded model's actual bones.
pub fn preset(index: usize, joints: &[String]) -> Value {
    let index = index.min(7);
    let name = PRESETS[index];
    let mut l = layer(name);
    let normalize = |s: &str| {
        s.rsplit(':')
            .next()
            .unwrap_or(s)
            .chars()
            .filter(|c| c.is_ascii_alphanumeric())
            .collect::<String>()
            .to_ascii_lowercase()
    };
    let find = |patterns: &[&str]| -> String {
        for pattern in patterns {
            let mut candidates = joints
                .iter()
                .map(|j| {
                    let n = normalize(j);
                    let score = if n == *pattern {
                        3
                    } else if n.ends_with(pattern) {
                        2
                    } else if n.contains(pattern) {
                        1
                    } else {
                        0
                    };
                    (score, j)
                })
                .filter(|(score, _)| *score > 0)
                .collect::<Vec<_>>();
            candidates.sort_by_key(|b| std::cmp::Reverse(b.0));
            if let Some((score, name)) = candidates.first()
                && candidates.get(1).is_none_or(|v| v.0 < *score)
            {
                return (*name).clone();
            }
        }
        String::new()
    };
    match index {
        0 => {
            l["solverType"] = json!("TwoBoneIK");
            l["chain"]["root"] = json!(find(&[
                "rightarm",
                "rightupperarm",
                "upperarmr",
                "rupperarm",
                "rightshoulder"
            ]));
            l["chain"]["middle"] = json!(find(&[
                "rightforearm",
                "rightlowerarm",
                "forearmr",
                "lowerarmr",
                "rightelbow"
            ]));
            l["chain"]["end"] = json!(find(&["righthand", "rightwrist", "handr", "wristr"]));
        }
        1 => {
            l["solverType"] = json!("TwoBoneIK");
            l["chain"]["root"] = json!(find(&[
                "leftarm",
                "leftupperarm",
                "upperarml",
                "lupperarm",
                "leftshoulder"
            ]));
            l["chain"]["middle"] = json!(find(&[
                "leftforearm",
                "leftlowerarm",
                "forearml",
                "lowerarml",
                "leftelbow"
            ]));
            l["chain"]["end"] = json!(find(&["lefthand", "leftwrist", "handl", "wristl"]));
        }
        2 => {
            l["solverType"] = json!("ThreeBoneIK");
            l["chain"]["root"] = json!(find(&["rightshoulder", "shoulderr", "rshoulder"]));
            l["chain"]["middle"] = json!(find(&[
                "rightarm",
                "rightupperarm",
                "upperarmr",
                "rupperarm"
            ]));
            l["chain"]["secondMiddle"] = json!(find(&[
                "rightforearm",
                "rightlowerarm",
                "forearmr",
                "lowerarmr",
                "rightelbow"
            ]));
            l["chain"]["end"] = json!(find(&["righthand", "rightwrist", "handr", "wristr"]));
        }
        3 => {
            l["solverType"] = json!("ThreeBoneIK");
            l["chain"]["root"] = json!(find(&["leftshoulder", "shoulderl", "lshoulder"]));
            l["chain"]["middle"] =
                json!(find(&["leftarm", "leftupperarm", "upperarml", "lupperarm"]));
            l["chain"]["secondMiddle"] = json!(find(&[
                "leftforearm",
                "leftlowerarm",
                "forearml",
                "lowerarml",
                "leftelbow"
            ]));
            l["chain"]["end"] = json!(find(&["lefthand", "leftwrist", "handl", "wristl"]));
        }
        4 => {
            l["solverType"] = json!("FootIK");
            l["chain"]["root"] = json!(find(&[
                "rightupleg",
                "rightupperleg",
                "rightthigh",
                "thighr",
                "upperlegr"
            ]));
            l["chain"]["middle"] = json!(find(&[
                "rightleg",
                "rightlowerleg",
                "rightknee",
                "shinr",
                "calfr",
                "lowerlegr"
            ]));
            l["chain"]["end"] = json!(find(&["rightfoot", "rightankle", "footr", "ankler"]));
        }
        5 => {
            l["solverType"] = json!("FootIK");
            l["chain"]["root"] = json!(find(&[
                "leftupleg",
                "leftupperleg",
                "leftthigh",
                "thighl",
                "upperlegl"
            ]));
            l["chain"]["middle"] = json!(find(&[
                "leftleg",
                "leftlowerleg",
                "leftknee",
                "shinl",
                "calfl",
                "lowerlegl"
            ]));
            l["chain"]["end"] = json!(find(&["leftfoot", "leftankle", "footl", "anklel"]));
        }
        6 => {
            l["solverType"] = json!("LookAtIK");
            l["chain"]["end"] = json!(find(&["head"]));
            l["affectedJoints"] = json!(
                [find(&["neck"]), find(&["head"])]
                    .into_iter()
                    .filter(|n| !n.is_empty())
                    .collect::<Vec<_>>()
            );
        }
        7 => {
            l["solverType"] = json!("AimIK");
            l["chain"]["end"] = json!(find(&["rightshoulder", "rightarm", "righthand"]));
            l["affectedJoints"] = json!(
                [
                    find(&["spine"]),
                    find(&["spine1"]),
                    find(&["spine2"]),
                    find(&["chest"]),
                    find(&["rightshoulder"])
                ]
                .into_iter()
                .filter(|n| !n.is_empty())
                .collect::<Vec<_>>()
            );
        }
        _ => {}
    }
    l
}

/// Recognize current and legacy IK document extensions.
pub fn is_file(path: &std::path::Path) -> bool {
    path.file_name().is_some_and(|s| {
        let s = s.to_string_lossy().to_ascii_lowercase();
        s.ends_with(".smik") || s.ends_with(".ik.json")
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn preserves_legacy_extensions_and_authors_bone_references() {
        let v=parse(r#"{"id":"legacy","solverType":"ThreeBoneIK","rootJoint":"a","middle2Joint":"c","extension":{"keep":true}}"#).unwrap();
        assert_eq!(v["layers"][0]["chain"]["secondMiddle"], "c");
        assert_eq!(v["extension"]["keep"], true);
        let text = edit(
            &v.to_string(),
            "target_bone",
            json!({"index":0,"bone":"socket.with.dots"}),
            0,
        )
        .unwrap();
        let v = parse(&text).unwrap();
        assert_eq!(v["layers"][0]["target"], "target.\"socket.with.dots\"");
        assert_eq!(v["extension"]["keep"], true);
    }
    #[test]
    fn presets_use_actual_names_and_layers_have_unique_ids() {
        let bones = ["rig:RightArm", "rig:RightForeArm", "rig:RightHand"].map(str::to_string);
        let p = preset(0, &bones);
        assert_eq!(p["chain"]["root"], bones[0]);
        assert_eq!(p["chain"]["middle"], bones[1]);
        assert_eq!(p["chain"]["end"], bones[2]);
        let text = template("Contact", "TwoBoneIK").unwrap().to_string();
        let text = edit(&text, "duplicate", json!(0), 0).unwrap();
        let v = parse(&text).unwrap();
        validate(&v).unwrap();
        assert_ne!(v["layers"][0]["id"], v["layers"][1]["id"]);
    }
}
