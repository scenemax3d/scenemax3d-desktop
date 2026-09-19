//! Validated model import drafts, independent of the rendering and filesystem adapters.
use serde_json::{Value, json};
/// New import document. Preview pose is deliberately separate from saved asset offsets.
pub fn draft() -> Value {
    json!({"version":1,"archiveEntry":"","source":"","name":"model","scaleX":1.0,"scaleY":1.0,"scaleZ":1.0,
        "transX":0.0,"transY":0.0,"transZ":0.0,"rotateY":0.0,"isStatic":false,
        "character":{"calibrateX":0.0,"calibrateY":0.0,"calibrateZ":0.0,"capsuleRadius":2.0,"capsuleHeight":2.0,"stepHeight":0.05},
        "preview":{"position":[0.0,0.0,0.0],"rotation":[0.0,0.0,0.0],"proportional":true,"capsule":false,"grid":true,"skeleton":false,"loop":true,"speed":1.0},
        "optimization":{"enabled":false,"textures":true,"maxTextureSize":2048,"jpegQuality":82,"convertOpaquePng":true,"simplify":false,"ratio":0.75}})
}
/// Validate edited values without requiring the source to have been selected yet.
pub fn validate(v: &Value) -> Result<(), String> {
    for key in ["scaleX", "scaleY", "scaleZ"] {
        positive(v, key, false)?;
    }
    for key in ["transX", "transY", "transZ", "rotateY"] {
        finite(v, key)?;
    }
    for key in ["calibrateX", "calibrateY", "calibrateZ"] {
        finite(&v["character"], key)?;
    }
    for key in ["capsuleRadius", "capsuleHeight"] {
        positive(&v["character"], key, false)?;
    }
    positive(&v["character"], "stepHeight", true)?;
    for key in ["position", "rotation"] {
        let values = v["preview"][key]
            .as_array()
            .filter(|a| a.len() == 3)
            .ok_or("Invalid preview pose")?;
        if values
            .iter()
            .any(|n| !n.as_f64().is_some_and(f64::is_finite))
        {
            return Err("Invalid preview pose".into());
        }
    }
    positive(&v["preview"], "speed", false)?;
    for path in [
        "/isStatic",
        "/preview/proportional",
        "/preview/grid",
        "/preview/capsule",
        "/preview/skeleton",
        "/preview/loop",
        "/optimization/enabled",
        "/optimization/textures",
        "/optimization/convertOpaquePng",
        "/optimization/simplify",
    ] {
        if !v.pointer(path).is_some_and(Value::is_boolean) {
            return Err(format!("{path} must be a checkbox value"));
        }
    }
    for (key, min, max) in [
        ("maxTextureSize", 0., 16384.),
        ("jpegQuality", 1., 100.),
        ("ratio", 0.001, 1.),
    ] {
        let n = finite(&v["optimization"], key)?;
        if n < min || n > max || (key != "ratio" && n.fract() != 0.) {
            return Err(format!("Invalid {key}"));
        }
    }
    Ok(())
}
fn finite(v: &Value, k: &str) -> Result<f64, String> {
    v[k].as_f64()
        .filter(|v| v.is_finite())
        .ok_or_else(|| format!("{k} must be a finite number"))
}
fn positive(v: &Value, k: &str, zero: bool) -> Result<(), String> {
    let n = finite(v, k)?;
    if n > 0. || (zero && n == 0.) {
        Ok(())
    } else {
        Err(format!("{k} must be positive"))
    }
}
/// Fields consumed by the existing Java/Bevy resource readers. Preview-only pose is omitted.
pub fn metadata(v: &Value) -> Result<Value, String> {
    validate(v)?;
    let mut result = json!({});
    for key in [
        "scaleX",
        "scaleY",
        "scaleZ",
        "transX",
        "transY",
        "transZ",
        "rotateY",
        "isStatic",
        "character",
    ] {
        result[key] = v[key].clone();
    }
    Ok(result)
}
/// Set a draft field, keeping proportional scales linked and validating the whole result.
pub fn edit(v: &Value, path: &str, value: Value) -> Result<Value, String> {
    let mut next = v.clone();
    *next.pointer_mut(path).ok_or("Unknown import property")? = value;
    if ["/scaleX", "/scaleY", "/scaleZ"].contains(&path)
        && v["preview"]["proportional"].as_bool() == Some(true)
    {
        let old = v
            .pointer(path)
            .and_then(Value::as_f64)
            .ok_or("Invalid scale")?;
        let new = next
            .pointer(path)
            .and_then(Value::as_f64)
            .ok_or("Invalid scale")?;
        for key in ["scaleX", "scaleY", "scaleZ"] {
            if format!("/{key}") != path {
                next[key] = json!(v[key].as_f64().ok_or("Invalid scale")? * new / old);
            }
        }
    }
    validate(&next)?;
    Ok(next)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn proportional_scale_preserves_ratios_and_preview_pose_is_not_imported() {
        let mut v = draft();
        v["scaleY"] = json!(2.);
        let v = edit(&v, "/scaleX", json!(3.)).unwrap();
        assert_eq!(v["scaleY"], 6.);
        assert!(metadata(&v).unwrap().get("preview").is_none());
        assert!(edit(&v, "/scaleX", json!(-1.)).is_err());
    }
}
