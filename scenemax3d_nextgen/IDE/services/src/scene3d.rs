//! Read-only import of Java scene documents. Never executes embedded code.
mod catalog;
use serde_json::Value;
use std::path::{Path, PathBuf};

/// Imported scene with per-entity diagnostics, retaining unsupported entries.
pub struct Scene3d {
    /// Java document ambient lighting settings.
    pub ambient: Value,
    /// Native PBR surfaces referenced by scene material names.
    pub materials: std::collections::BTreeMap<String, Value>,
    /// Available project-local model, shader, material and IK names.
    pub catalog: std::collections::BTreeMap<String, Vec<String>>,
    /// Saved editor camera, when present.
    pub camera: Option<SceneCamera>,
    /// Canonical resource root used by the scoped asset reader.
    pub resource_root: Option<PathBuf>,
    /// Entries in document order.
    pub entities: Vec<Entity3d>,
}
/// Saved Java editor view, independent of rendering APIs.
pub struct SceneCamera {
    /// Camera world position.
    pub position: [f32; 3],
    /// Camera quaternion, XYZW.
    pub rotation: [f32; 4],
}
/// Scene entry and resolved model resource.
pub struct Entity3d {
    /// Original object, including unsupported authoring metadata.
    pub properties: Value,
    /// JSON pointer identifying this object in the document.
    pub pointer: String,
    /// Parent entry index in the flattened, preorder hierarchy.
    pub parent: Option<usize>,
    /// Nesting depth in the document hierarchy.
    pub depth: usize,
    /// Stable serialized identity.
    pub id: String,
    /// User-visible name.
    pub name: String,
    /// Java entity type.
    pub kind: String,
    /// World translation.
    pub position: [f32; 3],
    /// Quaternion in XYZW order.
    pub rotation: [f32; 4],
    /// Entity scale.
    pub scale: [f32; 3],
    /// Full primitive dimensions (Java BOX stores half extents).
    pub size: [f32; 3],
    /// Initial visibility.
    pub hidden: bool,
    /// Validated model asset, when available.
    pub model: Option<PathBuf>,
    /// Unsupported/missing-resource information.
    pub note: String,
}
fn vector<const N: usize>(value: &Value, fallback: [f32; N]) -> Result<[f32; N], String> {
    if value.is_null() {
        return Ok(fallback);
    }
    let a = value
        .as_array()
        .filter(|a| a.len() == N)
        .ok_or("Invalid transform vector")?;
    let mut result = fallback;
    for (i, n) in a.iter().enumerate() {
        result[i] = n
            .as_f64()
            .filter(|n| n.is_finite() && n.abs() < 1e12)
            .ok_or("Invalid transform number")? as f32;
    }
    Ok(result)
}
/// Resolve project models on a filesystem worker, without modifying the source.
pub fn load(root: &Path, source: &str) -> Result<Scene3d, String> {
    if source.len() > 8 * 1024 * 1024 {
        return Err("Scene exceeds 8 MiB".into());
    }
    let value: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    let entries = value["entities"]
        .as_array()
        .ok_or("Expected a .smdesign entities array")?;
    if entries.len() > 2000 {
        return Err("Scene exceeds 2,000 entries".into());
    }
    let resources = root.join("resources");
    let canonical = resources.canonicalize().ok();
    let mut entities = Vec::new();
    let mut pending: Vec<_> = entries
        .iter()
        .enumerate()
        .rev()
        .map(|(i, e)| (e, None, 0usize, format!("/entities/{i}")))
        .collect();
    while let Some((e, parent, depth, pointer)) = pending.pop() {
        if entities.len() >= 2000 || depth > 32 {
            return Err("Scene hierarchy exceeds 2,000 entries or 32 levels".into());
        }
        let index = entities.len();
        let kind = e["type"]
            .as_str()
            .ok_or("Missing scene entity type")?
            .to_owned();
        let mut item = Entity3d {
            properties: e.clone(),
            pointer: pointer.clone(),
            parent,
            depth,
            id: e["id"]
                .as_str()
                .map(str::to_owned)
                .unwrap_or_else(|| index.to_string()),
            name: e["name"].as_str().unwrap_or("Unnamed").into(),
            kind: kind.clone(),
            position: vector(&e["position"], [0.; 3])?,
            rotation: vector(&e["rotation"], [0., 0., 0., 1.])?,
            scale: vector(&e["scale"], [1.; 3])?,
            size: [1.; 3],
            hidden: e["hidden"].as_bool().unwrap_or(false),
            model: None,
            note: String::new(),
        };
        let number = |key, default| e[key].as_f64().unwrap_or(default) as f32;
        match kind.as_str() {
            "BOX" => {
                item.size = [
                    number("sizeX", 0.5) * 2.,
                    number("sizeY", 0.5) * 2.,
                    number("sizeZ", 0.5) * 2.,
                ]
            }
            "QUAD" => item.size = [number("quadWidth", 1.), number("quadHeight", 1.), 0.01],
            "SPHERE" => item.size = [number("radius", 0.5) * 2.; 3],
            "MODEL" => {
                let resource = e["resourcePath"].as_str().unwrap_or("");
                match scenemax_assets::resolve_model_resource(&resources, resource) {
                    Ok(model) => {
                        let path = resources.join(&model.asset_path).canonicalize();
                        match path {
                            Ok(path)
                                if canonical
                                    .as_ref()
                                    .is_some_and(|root| path.starts_with(root)) =>
                            {
                                item.model = Some(path);
                            }
                            _ => {
                                item.note = format!(
                                    "Model file missing or outside project resources: {resource}"
                                )
                            }
                        }
                        // Java restores the serialized node scale, overriding resource defaults.
                        item.scale = vector(&e["scale"], model.scale.unwrap_or([1.; 3]))?;
                    }
                    Err(e) => item.note = e.to_string(),
                }
            }
            "CINEMATIC_RIG" | "CINEMATIC_TRACK" | "SECTION" | "CYLINDER" | "CONE"
            | "HOLLOW_CYLINDER" | "WEDGE" | "STAIRS" | "ARCH" | "LIGHT" | "PATH" => {}
            _ => item.note = "Listed only; this entry is not rendered or executed yet".into(),
        }
        if !item.size.iter().all(|n| n.is_finite() && *n > 0.) {
            return Err(format!("Invalid dimensions for {}", item.name));
        }
        entities.push(item);
        if let Some(children) = e.get("children") {
            let children = children.as_array().ok_or("Expected scene children array")?;
            pending.extend(children.iter().enumerate().rev().map(|(i, child)| {
                (
                    child,
                    Some(index),
                    depth + 1,
                    format!("{pointer}/children/{i}"),
                )
            }));
        }
    }
    let position = vector(&value["gameCamera"]["position"], [0., 2., 10.])?;
    let rotation = vector(&value["gameCamera"]["rotation"], [0., 1., 0., 0.])?;
    if rotation.iter().all(|v| *v == 0.) {
        return Err("Invalid game camera rotation".into());
    }
    entities.push(Entity3d {
        properties: value
            .get("gameCamera")
            .cloned()
            .unwrap_or_else(|| serde_json::json!({"position":position,"rotation":rotation})),
        pointer: "/gameCamera".into(),
        parent: None,
        depth: 0,
        id: "__game_camera__".into(),
        name: "Game Camera".into(),
        kind: "CAMERA".into(),
        position,
        rotation,
        scale: [1.; 3],
        size: [2.; 3],
        hidden: false,
        model: None,
        note: String::new(),
    });
    let materials = crate::material::documents(root)?;
    let catalog = catalog::load(&resources, &materials);
    Ok(Scene3d {
        materials,
        ambient: value["bevyAmbientLight"].clone(),
        catalog,
        camera: if value["camera"].is_object() {
            Some(SceneCamera {
                position: vector(&value["camera"]["position"], [0.; 3])?,
                rotation: vector(&value["camera"]["rotation"], [0., 0., 0., 1.])?,
            })
        } else {
            None
        },
        entities,
        resource_root: canonical,
    })
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn nested_sections_preserve_order_and_saved_model_scale_overrides_resource_default() {
        let dir = tempfile::tempdir().unwrap();
        let models = dir.path().join("resources/Models");
        std::fs::create_dir_all(&models).unwrap();
        std::fs::write(models.join("sample.glb"), []).unwrap();
        std::fs::write(models.join("models-ext.json"), r#"{"models":[{"name":"sample","path":"Models/sample.glb","scaleX":0.01,"scaleY":0.02,"scaleZ":0.03}]}"#).unwrap();
        let scene = load(dir.path(), r#"{"entities":[{"type":"SECTION","name":"Group","children":[{"type":"MODEL","name":"Saved","resourcePath":"sample","scale":[2,3,4]},{"type":"SECTION","children":[{"type":"MODEL","resourcePath":"sample"}]}]},{"type":"BOX"}]}"#).unwrap();
        assert_eq!(scene.entities.len(), 6);
        assert_eq!(scene.entities[1].parent, Some(0));
        assert_eq!(scene.entities[3].parent, Some(2));
        assert_eq!(scene.entities[3].depth, 2);
        assert_eq!(scene.entities[4].parent, None);
        assert_eq!(scene.entities[1].scale, [2., 3., 4.]);
        assert_eq!(scene.entities[3].scale, [0.01, 0.02, 0.03]);
        assert!(scene.entities[1].model.is_some());
    }
    #[test]
    fn imports_geometry_and_retains_code_without_running_it() {
        let d = tempfile::tempdir().unwrap();
        let scene=load(d.path(),r#"{"entities":[{"id":"box-id","name":"base","type":"BOX","sizeX":4,"sizeY":2,"sizeZ":3,"position":[1,2,3]},{"name":"logic","type":"CODE","codeText":"never execute"}]}"#).unwrap();
        assert_eq!(scene.entities[0].size, [8., 4., 6.]);
        assert_eq!(scene.entities[0].position, [1., 2., 3.]);
        assert_eq!(scene.entities[1].kind, "CODE");
        assert!(!scene.entities[1].note.is_empty());
        assert!(load(d.path(), r#"{"entities":[{"type":"BOX","position":[0]}]}"#).is_err());
    }
}
