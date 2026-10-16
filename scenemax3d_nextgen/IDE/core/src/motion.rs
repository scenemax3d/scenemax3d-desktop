//! Lossless throw-motion authoring contract shared with the existing runtime.
use serde_json::{Value, json};
/// Available templates in the original menu order.
pub const TYPES: &[(&str, &str)] = &[
    ("target_arc", "Target Arc Throw"),
    ("ballistic", "Ballistic Throw"),
    ("straight", "Straight Projectile"),
    ("homing", "Homing Throw"),
    ("returning", "Returning Throw"),
    ("physics", "Physics Throw"),
];
/// Create a named runtime-compatible starter definition.
pub fn template(name: &str, kind: &str) -> Result<Value, String> {
    if name.trim().is_empty() {
        return Err("Enter a motion name".into());
    }
    if !TYPES.iter().any(|(k, _)| *k == kind) {
        return Err("Unknown motion type".into());
    }
    let slug = name
        .trim()
        .to_ascii_lowercase()
        .split(|c: char| !c.is_ascii_alphanumeric())
        .filter(|s| !s.is_empty())
        .collect::<Vec<_>>()
        .join("_");
    let slug = if slug.is_empty() {
        "throw_motion"
    } else {
        &slug
    };
    let id = if slug.starts_with("motion_") {
        slug.to_owned()
    } else {
        format!("motion_{slug}")
    };
    let mut v = json!({"type":"SceneMaxThrowMotionDefinition", "schemaVersion":"1.0",
        "id":id,"displayName":name.trim(),"motionType":kind,"parameters":defaults(),"designerMetadata":{}});
    type_defaults(&mut v);
    Ok(v)
}
fn defaults() -> Value {
    json!({
        "initialSpeed": 16.0,
        "launchAngle": 35.0,
        "gravityScale": 1.0,
        "duration": 1.2,
        "arcHeight": 3.0,
        "arcMode": "relative_height",
        "easingFunction": "ease_in_out",
        "speed": 18.0,
        "acceleration": 0.0,
        "maxDistance": 30.0,
        "maxLifetime": 4.0,
        "alignToVelocity": true,
        "alignToPath": true,
        "spinSpeed": 720.0,
        "collisionMode": "spherecast",
        "collisionRadius": 0.25,
        "stopOnImpact": true,
        "targetMode": "point",
        "turnRate": 180.0,
        "maxTurnAngle": 90.0,
        "homingDelay": 0.15,
        "homingStrength": 1.0,
        "loseTargetBehavior": "continue",
        "outboundDuration": 0.75,
        "outboundDistance": 12.0,
        "outboundArcHeight": 1.0,
        "returnSpeed": 18.0,
        "returnDelay": 0.15,
        "canHitOnReturn": true,
        "ownerTarget": "thrower",
        "catchRadius": 0.75,
        "forceMode": "impulse",
        "massOverride": 0.0,
        "drag": 0.0,
        "impactBehavior": "stop",
        "switchToPhysicsOnImpact": false
    })
}
fn type_defaults(v: &mut Value) {
    v["parameters"]["switchToPhysicsOnImpact"] = json!(v["motionType"] == "physics");
    if v["motionType"] == "returning" {
        let p = &v["parameters"];
        let n = |key: &str| p[key].as_f64().unwrap_or(0.);
        v["parameters"]["duration"] = json!(
            (n("outboundDuration")
                + n("returnDelay")
                + n("outboundDistance") / n("returnSpeed").max(1.))
            .max(0.1)
        );
    }
}
/// Read a document and fill omitted defaults without dropping extensions.
pub fn parse(text: &str) -> Result<Value, String> {
    let mut v: Value = serde_json::from_str(text).map_err(|e| e.to_string())?;
    if !v.is_object() {
        return Err("Motion must be a JSON object".into());
    }
    let base = template("Throw Motion", "target_arc")?;
    for (key, value) in base.as_object().into_iter().flatten() {
        if v.get(key).is_none() {
            v[key] = value.clone();
        }
    }
    if !v["parameters"].is_object() {
        return Err("Motion parameters must be an object".into());
    }
    let mut d = defaults();
    if v["motionType"] == "physics" {
        d["switchToPhysicsOnImpact"] = json!(true);
    }
    if v["motionType"] == "returning" {
        d["duration"] = json!(0.75 + 0.15 + 12. / 18.);
    }
    for (key, value) in d.as_object().into_iter().flatten() {
        if v["parameters"].get(key).is_none() {
            v["parameters"][key] = value.clone();
        }
    }
    Ok(v)
}
/// Validate runtime requirements before publication.
pub fn validate(v: &Value) -> Result<(), String> {
    if v["id"].as_str().is_none_or(|s| s.trim().is_empty()) {
        return Err("Motion ID is required".into());
    }
    let kind = v["motionType"].as_str().unwrap_or("");
    if !TYPES.iter().any(|(k, _)| *k == kind) && kind != "custom_curve" {
        return Err("Unsupported motion type".into());
    }
    let p = &v["parameters"];
    for (key, default) in defaults().as_object().into_iter().flatten() {
        let value = &p[key];
        if (default.is_number()
            && value
                .as_f64()
                .is_none_or(|n| !n.is_finite() || n.abs() > 1.0e9))
            || (default.is_boolean() && !value.is_boolean())
            || (default.is_string() && !value.is_string())
        {
            return Err(format!("Invalid parameter: {key}"));
        }
    }
    let speed = match kind {
        "physics" | "ballistic" => number(p, "initialSpeed"),
        "returning" => (number(p, "outboundDistance") / number(p, "outboundDuration").max(0.1))
            .max(number(p, "returnSpeed")),
        "straight" | "homing" => number(p, "speed"),
        _ => 1.,
    };
    if speed <= 0. {
        return Err("Speed must be greater than zero".into());
    }
    if matches!(kind, "target_arc" | "returning" | "custom_curve") && number(p, "duration") <= 0. {
        return Err("Duration must be greater than zero".into());
    }
    if number(p, "collisionRadius") < 0. {
        return Err("Collision radius cannot be negative".into());
    }
    Ok(())
}
/// Replace a property while retaining unknown fields.
pub fn edit(text: &str, pointer: &str, value: Value) -> Result<String, String> {
    let mut v = parse(text)?;
    let old = v.pointer(pointer).ok_or("Unknown motion property")?;
    if (old.is_number() && !value.is_number())
        || (old.is_boolean() && !value.is_boolean())
        || (old.is_string() && !value.is_string())
    {
        return Err("Invalid property value".into());
    }
    *v.pointer_mut(pointer).ok_or("Unknown motion property")? = value;
    if pointer == "/motionType" {
        type_defaults(&mut v);
    }
    serde_json::to_string_pretty(&v).map_err(|e| e.to_string())
}
/// Read a numeric parameter for preview sampling.
pub fn number(p: &Value, key: &str) -> f32 {
    p[key].as_f64().unwrap_or(0.) as f32
}
/// Return non-blocking authoring diagnostics.
pub fn warnings(v: &Value) -> Vec<&'static str> {
    let mut out = vec![];
    if number(&v["parameters"], "maxLifetime") <= 0. {
        out.push("Max lifetime should be positive for runtime cleanup.");
    }
    if v["motionType"] == "custom_curve" {
        out.push("Custom curve authoring is reserved, as in the original tool.");
    }
    out
}

/// A field exposed by the original designer, including its spinner range.
pub struct Field {
    /// Serialized parameter name.
    pub key: &'static str,
    /// Form caption.
    pub label: &'static str,
    /// Slider minimum.
    pub min: f32,
    /// Slider maximum.
    pub max: f32,
    /// Slider increment.
    pub step: f32,
    /// Enumeration choices; empty for numbers.
    pub choices: &'static [&'static str],
}
/// List visible parameters for the selected motion type.
pub fn fields(kind: &str) -> Vec<Field> {
    let keys: &[&str] = match kind {
        "physics" => &[
            "initialSpeed",
            "launchAngle",
            "forceMode",
            "massOverride",
            "drag",
            "gravityScale",
            "maxLifetime",
        ],
        "ballistic" => &[
            "initialSpeed",
            "launchAngle",
            "gravityScale",
            "maxDistance",
            "maxLifetime",
            "alignToVelocity",
        ],
        "straight" => &[
            "speed",
            "acceleration",
            "maxDistance",
            "maxLifetime",
            "alignToVelocity",
        ],
        "homing" => &[
            "speed",
            "acceleration",
            "turnRate",
            "homingDelay",
            "homingStrength",
            "loseTargetBehavior",
            "maxLifetime",
        ],
        "returning" => &[
            "outboundDuration",
            "outboundDistance",
            "outboundArcHeight",
            "returnSpeed",
            "returnDelay",
            "canHitOnReturn",
            "catchRadius",
            "easing",
        ],
        _ => &["duration", "arcHeight", "easing", "alignToPath"],
    };
    keys.iter()
        .filter_map(|key| match *key {
            "initialSpeed" => Some(Field {
                key: "initialSpeed",
                label: "Initial Speed",
                min: 0.0,
                max: 999.0,
                step: 0.1,
                choices: &[],
            }),
            "launchAngle" => Some(Field {
                key: "launchAngle",
                label: "Launch Angle",
                min: -89.0,
                max: 89.0,
                step: 1.0,
                choices: &[],
            }),
            "forceMode" => Some(Field {
                key: "forceMode",
                label: "Force Mode",
                min: 0.0,
                max: 1.0,
                step: 1.0,
                choices: &["impulse", "velocity", "continuous_force"],
            }),
            "massOverride" => Some(Field {
                key: "massOverride",
                label: "Mass Override",
                min: 0.0,
                max: 999.0,
                step: 0.1,
                choices: &[],
            }),
            "drag" => Some(Field {
                key: "drag",
                label: "Drag",
                min: 0.0,
                max: 999.0,
                step: 0.01,
                choices: &[],
            }),
            "gravityScale" => Some(Field {
                key: "gravityScale",
                label: "Gravity Scale",
                min: 0.0,
                max: 20.0,
                step: 0.05,
                choices: &[],
            }),
            "maxLifetime" => Some(Field {
                key: "maxLifetime",
                label: "Max Lifetime",
                min: 0.05,
                max: 999.0,
                step: 0.1,
                choices: &[],
            }),
            "maxDistance" => Some(Field {
                key: "maxDistance",
                label: "Max Distance",
                min: 0.0,
                max: 9999.0,
                step: 0.5,
                choices: &[],
            }),
            "alignToVelocity" => Some(Field {
                key: "alignToVelocity",
                label: "Align To Velocity",
                min: 0.0,
                max: 1.0,
                step: 1.0,
                choices: &["true", "false"],
            }),
            "speed" => Some(Field {
                key: "speed",
                label: "Speed",
                min: 0.0,
                max: 999.0,
                step: 0.1,
                choices: &[],
            }),
            "acceleration" => Some(Field {
                key: "acceleration",
                label: "Acceleration",
                min: -999.0,
                max: 999.0,
                step: 0.1,
                choices: &[],
            }),
            "turnRate" => Some(Field {
                key: "turnRate",
                label: "Turn Rate",
                min: 1.0,
                max: 3600.0,
                step: 5.0,
                choices: &[],
            }),
            "homingDelay" => Some(Field {
                key: "homingDelay",
                label: "Homing Delay",
                min: 0.0,
                max: 60.0,
                step: 0.05,
                choices: &[],
            }),
            "homingStrength" => Some(Field {
                key: "homingStrength",
                label: "Homing Strength",
                min: 0.0,
                max: 10.0,
                step: 0.05,
                choices: &[],
            }),
            "loseTargetBehavior" => Some(Field {
                key: "loseTargetBehavior",
                label: "Lose Target",
                min: 0.0,
                max: 1.0,
                step: 1.0,
                choices: &["continue", "stop", "fall", "switch_to_physics"],
            }),
            "outboundDuration" => Some(Field {
                key: "outboundDuration",
                label: "Outbound Duration",
                min: 0.05,
                max: 60.0,
                step: 0.05,
                choices: &[],
            }),
            "outboundDistance" => Some(Field {
                key: "outboundDistance",
                label: "Outbound Distance",
                min: 0.0,
                max: 999.0,
                step: 0.5,
                choices: &[],
            }),
            "outboundArcHeight" => Some(Field {
                key: "outboundArcHeight",
                label: "Outbound Arc Height",
                min: -100.0,
                max: 100.0,
                step: 0.1,
                choices: &[],
            }),
            "returnSpeed" => Some(Field {
                key: "returnSpeed",
                label: "Return Speed",
                min: 0.0,
                max: 999.0,
                step: 0.1,
                choices: &[],
            }),
            "returnDelay" => Some(Field {
                key: "returnDelay",
                label: "Return Delay",
                min: 0.0,
                max: 60.0,
                step: 0.05,
                choices: &[],
            }),
            "canHitOnReturn" => Some(Field {
                key: "canHitOnReturn",
                label: "Can Hit On Return",
                min: 0.0,
                max: 1.0,
                step: 1.0,
                choices: &["true", "false"],
            }),
            "catchRadius" => Some(Field {
                key: "catchRadius",
                label: "Catch Radius",
                min: 0.0,
                max: 100.0,
                step: 0.05,
                choices: &[],
            }),
            "easing" => Some(Field {
                key: "easingFunction",
                label: "Easing",
                min: 0.0,
                max: 1.0,
                step: 1.0,
                choices: &["linear", "ease_in", "ease_out", "ease_in_out"],
            }),
            "duration" => Some(Field {
                key: "duration",
                label: "Duration",
                min: 0.05,
                max: 60.0,
                step: 0.05,
                choices: &[],
            }),
            "arcHeight" => Some(Field {
                key: "arcHeight",
                label: "Arc Height",
                min: -100.0,
                max: 100.0,
                step: 0.1,
                choices: &[],
            }),
            "alignToPath" => Some(Field {
                key: "alignToPath",
                label: "Align To Path",
                min: 0.0,
                max: 1.0,
                step: 1.0,
                choices: &["true", "false"],
            }),
            _ => None,
        })
        .collect()
}

pub mod sampling;

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn templates_match_java_defaults_and_hidden_parameters() {
        for (kind, _) in TYPES {
            let v = template("Test Throw", kind).unwrap();
            assert_eq!(v["id"], "motion_test_throw");
            assert_eq!(v["parameters"]["initialSpeed"], 16.);
            assert_eq!(v["parameters"]["speed"], 18.);
            assert_eq!(v["parameters"]["targetMode"], "point");
            assert_eq!(
                v["parameters"]["switchToPhysicsOnImpact"],
                *kind == "physics"
            );
            assert_eq!(v["parameters"]["collisionMode"], "spherecast");
            validate(&v).unwrap();
            assert!(!fields(kind).is_empty());
        }
        let v = template("motion_existing", "returning").unwrap();
        assert_eq!(v["id"], "motion_existing");
        assert!((number(&v["parameters"], "duration") - (0.75 + 0.15 + 12. / 18.)).abs() < 0.00001);
    }
    #[test]
    fn edits_preserve_hidden_and_unknown_values() {
        let mut v = template("fixture", "target_arc").unwrap();
        v["designerMetadata"] = json!({"extension":[1,2,3]});
        v["parameters"]["future"] = json!(42);
        v["parameters"]["spinSpeed"] = json!(99.);
        let changed = edit(&v.to_string(), "/motionType", json!("physics")).unwrap();
        let parsed = parse(&changed).unwrap();
        assert_eq!(parsed["parameters"]["future"], 42);
        assert_eq!(parsed["designerMetadata"], v["designerMetadata"]);
        assert_eq!(parsed["parameters"]["spinSpeed"], 99.);
        assert_eq!(parsed["parameters"]["switchToPhysicsOnImpact"], true);
        assert!(edit(&changed, "/parameters/speed", json!("bad")).is_err());
    }
    #[test]
    fn validation_rejects_unusable_motion_but_keeps_java_warnings() {
        let mut v = template("fixture", "straight").unwrap();
        v["parameters"]["speed"] = json!(0.);
        assert!(validate(&v).is_err());
        v["parameters"]["speed"] = json!(10.);
        v["parameters"]["maxLifetime"] = json!(0.);
        validate(&v).unwrap();
        assert_eq!(warnings(&v).len(), 1);
        v["parameters"]["collisionRadius"] = json!(-1.);
        assert!(validate(&v).is_err());
    }
    #[test]
    fn target_arc_and_returning_reach_exact_endpoints() {
        let mut v = template("fixture", "target_arc").unwrap();
        v["parameters"]["duration"] = json!(1.13);
        let samples = sampling::sample(&v, 12., 2.);
        assert_eq!(samples.first().unwrap().position, [0., 1.2, 0.]);
        assert_eq!(samples.last().unwrap().position, [0., 3.2, 12.]);
        let mid = sampling::at(&samples, 0.56).unwrap();
        assert!(mid.position[1] > 5.);
        let v = template("fixture", "returning").unwrap();
        let samples = sampling::sample(&v, 12., 0.);
        assert_eq!(samples.last().unwrap().position, [0., 1.2, 0.]);
        assert!(samples.windows(2).all(|s| s[1].time > s[0].time));
        assert!(samples.iter().any(|s| s.velocity == [0.; 3]));
    }
    #[test]
    fn integrators_match_java_preview_semantics() {
        let v = template("fixture", "ballistic").unwrap();
        let samples = sampling::sample(&v, 12., 0.);
        let vy = 35_f32.to_radians().sin() * 16. - 9.81 / 60.;
        assert!((samples[1].position[1] - (1.2 + vy / 60.)).abs() < 0.00001);
        assert_eq!(samples.last().unwrap().position[1], 0.);
        let v = template("fixture", "straight").unwrap();
        let samples = sampling::sample(&v, 12., 8.);
        assert_eq!(samples[1].position, [0., 1.2, 0.3]);
        let v = template("fixture", "homing").unwrap();
        let samples = sampling::sample(&v, 12., 0.);
        assert_eq!(samples.last().unwrap().position, [0., 1.2, 12.]);
    }
    #[test]
    fn imported_large_duration_is_bounded() {
        let mut v = template("fixture", "target_arc").unwrap();
        v["parameters"]["duration"] = json!(1e20);
        assert!(sampling::sample(&v, 12., 0.).len() <= 3601);
    }
}
