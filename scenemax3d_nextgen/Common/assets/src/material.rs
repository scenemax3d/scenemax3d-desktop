//! Versioned, lossless PBR material documents. Texture paths are resource-relative.
use serde_json::{Value, json};

/// Editable scalar metadata, also used to validate documents.
#[derive(Clone, Copy)]
pub struct Scalar {
    /// Serialized field key.
    pub key: &'static str,
    /// Artist-facing label.
    pub label: &'static str,
    /// Inspector section.
    pub section: &'static str,
    /// Inclusive limits and initial value.
    pub range: [f32; 3],
}
/// Supported controls map directly to Bevy's PBR surface.
pub const SCALARS: &[Scalar] = &[
    Scalar {
        key: "metallic",
        label: "Metallic",
        section: "Surface",
        range: [0., 1., 0.],
    },
    Scalar {
        key: "roughness",
        label: "Roughness",
        section: "Surface",
        range: [0.045, 1., 0.45],
    },
    Scalar {
        key: "reflectance",
        label: "Reflectance",
        section: "Surface",
        range: [0., 1., 0.5],
    },
    Scalar {
        key: "emissionStrength",
        label: "Emission strength",
        section: "Emission",
        range: [0., 40., 0.],
    },
    Scalar {
        key: "clearcoat",
        label: "Coat weight",
        section: "Clearcoat",
        range: [0., 1., 0.],
    },
    Scalar {
        key: "clearcoatRoughness",
        label: "Coat roughness",
        section: "Clearcoat",
        range: [0.045, 1., 0.1],
    },
    Scalar {
        key: "anisotropy",
        label: "Anisotropy",
        section: "Anisotropy",
        range: [0., 1., 0.],
    },
    Scalar {
        key: "anisotropyRotation",
        label: "Rotation (degrees)",
        section: "Anisotropy",
        range: [0., 360., 0.],
    },
    Scalar {
        key: "transmission",
        label: "Transmission",
        section: "Transmission",
        range: [0., 1., 0.],
    },
    Scalar {
        key: "thickness",
        label: "Thickness",
        section: "Transmission",
        range: [0., 5., 0.1],
    },
    Scalar {
        key: "ior",
        label: "Index of refraction",
        section: "Transmission",
        range: [1., 2.5, 1.5],
    },
    Scalar {
        key: "alphaCutoff",
        label: "Alpha cutoff",
        section: "Rendering",
        range: [0., 1., 0.5],
    },
    Scalar {
        key: "uvScaleX",
        label: "Tile U",
        section: "UV mapping",
        range: [0.01, 32., 1.],
    },
    Scalar {
        key: "uvScaleY",
        label: "Tile V",
        section: "UV mapping",
        range: [0.01, 32., 1.],
    },
    Scalar {
        key: "uvOffsetX",
        label: "Offset U",
        section: "UV mapping",
        range: [-10., 10., 0.],
    },
    Scalar {
        key: "uvOffsetY",
        label: "Offset V",
        section: "UV mapping",
        range: [-10., 10., 0.],
    },
    Scalar {
        key: "uvRotation",
        label: "Rotation (degrees)",
        section: "UV mapping",
        range: [0., 360., 0.],
    },
];
/// Texture channels: label, key, and whether the image is color data (sRGB).
pub const TEXTURES: &[(&str, &str, bool)] = &[
    ("Base color", "baseColor", true),
    ("Normal · linear", "normal", false),
    (
        "Metallic B / roughness G · linear",
        "metallicRoughness",
        false,
    ),
    ("Occlusion R · linear", "occlusion", false),
    ("Emission", "emission", true),
];
/// Reusable starting surfaces, not game-specific assets.
pub const PRESETS: &[&str] = &[
    "Porcelain",
    "Brushed metal",
    "Gold",
    "Car paint",
    "Glass",
    "Rubber",
    "Emissive",
];
/// Start a material document with explicit defaults.
pub fn preset(name: &str) -> Value {
    let mut v = json!({"version":1,"name":"New material","baseColor":"#B7C9E2","emissionColor":"#FFFFFF","alphaMode":"Opaque","doubleSided":false,"unlit":false,"flipNormalY":false,"textures":{},"targetSlot":""});
    for field in SCALARS {
        v[field.key] = json!(field.range[2]);
    }
    match name {
        "Brushed metal" => {
            v["metallic"] = json!(1.);
            v["roughness"] = json!(0.28);
            v["anisotropy"] = json!(0.7);
        }
        "Gold" => {
            v["metallic"] = json!(1.);
            v["roughness"] = json!(0.2);
            v["baseColor"] = json!("#E8B454");
        }
        "Car paint" => {
            v["baseColor"] = json!("#A51E35");
            v["metallic"] = json!(0.7);
            v["clearcoat"] = json!(1.);
            v["roughness"] = json!(0.3);
        }
        "Glass" => {
            v["baseColor"] = json!("#E0F5F3");
            v["transmission"] = json!(1.);
            v["roughness"] = json!(0.06);
        }
        "Rubber" => {
            v["baseColor"] = json!("#252830");
            v["roughness"] = json!(0.92);
        }
        "Emissive" => {
            v["baseColor"] = json!("#15242A");
            v["emissionColor"] = json!("#46DDEB");
            v["emissionStrength"] = json!(5.);
        }
        _ => {
            v["clearcoat"] = json!(0.3);
        }
    }
    v
}
/// Parse RGB or RGBA hex colors, preserving artist-authored sRGB values.
pub fn color(text: &str) -> Result<[f32; 4], String> {
    let s = text.trim().trim_start_matches('#');
    if !matches!(s.len(), 6 | 8) || !s.is_ascii() {
        return Err("Use #RRGGBB or #RRGGBBAA".into());
    }
    let mut out = [1.; 4];
    for (i, component) in out.iter_mut().enumerate().take(s.len() / 2) {
        *component = u8::from_str_radix(&s[i * 2..i * 2 + 2], 16)
            .map_err(|_| "Invalid hex color")? as f32
            / 255.;
    }
    Ok(out)
}
/// Reject references escaping project resources, including URI and Windows paths.
pub fn resource_path(path: &str) -> bool {
    !path.is_empty()
        && !path.contains([':', '\\', '#'])
        && !path.starts_with('/')
        && path
            .split('/')
            .all(|p| !p.is_empty() && p != "." && p != "..")
}
/// Validate without dropping extension fields.
pub fn validate(v: &Value) -> Result<(), String> {
    if v["version"] != 1 {
        return Err("Unsupported material version".into());
    }
    for key in ["name", "targetSlot"] {
        if !v[key].is_string() {
            return Err(format!("{key} must be text"));
        }
    }
    for key in ["doubleSided", "unlit", "flipNormalY"] {
        if !v[key].is_boolean() {
            return Err(format!("{key} must be true or false"));
        }
    }
    if !v["textures"].is_object() {
        return Err("Textures must be an object".into());
    }
    for (_, key, _) in TEXTURES {
        if !v["textures"][key].is_null() && !v["textures"][key].is_string() {
            return Err(format!("{key}: expected a texture path"));
        }
    }
    for field in SCALARS {
        let n = v[field.key]
            .as_f64()
            .ok_or_else(|| format!("{} must be a number", field.label))?;
        if !n.is_finite()
            || n < f64::from(field.range[0]) - 0.000001
            || n > f64::from(field.range[1]) + 0.000001
        {
            return Err(format!(
                "{} must be between {} and {}",
                field.label, field.range[0], field.range[1]
            ));
        }
    }
    for field in ["baseColor", "emissionColor"] {
        color(v[field].as_str().ok_or("Missing color")?)?;
    }
    if !matches!(
        v["alphaMode"].as_str(),
        Some("Opaque" | "Mask" | "Blend" | "Add")
    ) {
        return Err("Invalid alpha mode".into());
    }
    for (_, key, _) in TEXTURES {
        if let Some(path) = v["textures"][key].as_str()
            && !path.is_empty()
            && !resource_path(path)
        {
            return Err(format!("{key}: use a path inside project resources"));
        }
    }
    Ok(())
}
/// Apply a property edit while preserving unknown metadata.
pub fn patch(source: &str, pointer: &str, value: Value) -> Result<String, String> {
    let mut v: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    if let Some(key) = pointer.strip_prefix("/textures/") {
        if !TEXTURES.iter().any(|(_, k, _)| *k == key) {
            return Err("Unknown texture channel".into());
        }
        if !v["textures"].is_object() {
            v["textures"] = json!({});
        }
        v["textures"][key] = value;
    } else {
        *v.pointer_mut(pointer).ok_or("Unknown material property")? = value;
    }
    validate(&v)?;
    serde_json::to_string_pretty(&v)
        .map(|s| s + "\n")
        .map_err(|e| e.to_string())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn presets_and_lossless_edits() {
        for p in PRESETS {
            validate(&preset(p)).unwrap();
        }
        let mut v = preset("Gold");
        v["future"] = json!({"keep":true});
        let s = patch(&v.to_string(), "/roughness", json!(0.8)).unwrap();
        let v: Value = serde_json::from_str(&s).unwrap();
        assert_eq!(v["future"]["keep"], true);
        assert!(patch(&s, "/metallic", json!(2)).is_err());
        assert!(patch(&s, "/textures/baseColor", json!("../secret.png")).is_err());
        assert!(color("#ééé").is_err());
    }
}
