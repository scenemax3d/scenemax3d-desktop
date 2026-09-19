//! Pure Effekseer import settings and validation.
use serde_json::{Value, json};
/// Default import and preview settings.
pub fn draft() -> Value {
    json!({"version":1,"source":"","name":"effect","speed":1.0,"duration":300.0,
    "scale":1.0,"x":0.0,"y":0.0,"z":0.0,"pitch":0.0,"yaw":0.0,"roll":0.0,
    "input0":0.0,"input1":0.0,"input2":0.0,"input3":0.0,
    "seed":1,"seek":0.0,"targetX":0.0,"targetY":0.0,"targetZ":0.0,"red":255.,"green":255.,"blue":255.,"alpha":255.,"loop":true,"grid":true,"background":"studio"})
}
/// Reject invalid or unbounded simulation settings.
pub fn validate(v: &Value) -> Result<(), String> {
    for (key, min, max) in [
        ("seed", 0., 2147483647.),
        ("seek", 0., 36000.),
        ("targetX", -1e6, 1e6),
        ("targetY", -1e6, 1e6),
        ("targetZ", -1e6, 1e6),
        ("red", 0., 255.),
        ("green", 0., 255.),
        ("blue", 0., 255.),
        ("alpha", 0., 255.),
        ("speed", 0.01, 8.),
        ("duration", 1., 36000.),
        ("scale", 0.001, 10000.),
        ("x", -1e6, 1e6),
        ("y", -1e6, 1e6),
        ("z", -1e6, 1e6),
        ("pitch", -360., 360.),
        ("yaw", -360., 360.),
        ("roll", -360., 360.),
        ("input0", -1e6, 1e6),
        ("input1", -1e6, 1e6),
        ("input2", -1e6, 1e6),
        ("input3", -1e6, 1e6),
    ] {
        let n = v[key]
            .as_f64()
            .filter(|n| n.is_finite())
            .ok_or_else(|| format!("Invalid {key}"))?;
        if n < min || n > max {
            return Err(format!("{key} must be between {min} and {max}"));
        }
    }
    if v["seed"].as_f64().unwrap_or(0.).fract() != 0. {
        return Err("Seed must be an integer".into());
    }
    if !v["loop"].is_boolean() || !v["grid"].is_boolean() {
        return Err("Invalid preview toggle".into());
    }
    if !["studio", "black", "gray", "white"].contains(&v["background"].as_str().unwrap_or_default())
    {
        return Err("Invalid backdrop".into());
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn bounded_simulation_settings() {
        let mut v = draft();
        assert!(validate(&v).is_ok());
        v["speed"] = json!(-1);
        assert!(validate(&v).is_err());
        v = draft();
        v["duration"] = json!(1e9);
        assert!(validate(&v).is_err());
    }
}
