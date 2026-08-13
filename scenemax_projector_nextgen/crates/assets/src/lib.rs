use std::{
    fs,
    path::{Path, PathBuf},
};

use anyhow::{Context, Result};
use serde::Deserialize;
use thiserror::Error;

#[derive(Debug, Default)]
pub struct AssetAuditReport {
    pub project: PathBuf,
    pub gltf_models: Vec<ModelAsset>,
    pub unsupported_j3o_models: Vec<ModelAsset>,
    pub missing_models: Vec<ModelAsset>,
}

#[derive(Debug, Clone)]
pub struct ModelAsset {
    pub name: String,
    pub path: PathBuf,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ModelResource {
    pub name: String,
    pub asset_path: String,
    pub scale: Option<[f32; 3]>,
    pub translation: Option<[f32; 3]>,
    pub rotation_y_degrees: Option<f32>,
    pub character_physics: Option<ModelCharacterPhysics>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ModelCharacterPhysics {
    pub bevy_visual_offset_y: Option<f32>,
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum AssetLookupError {
    #[error("model resource '{0}' was not found")]
    ModelNotFound(String),
    #[error("model resource '{name}' uses unsupported asset format: {asset_path}")]
    UnsupportedModelFormat { name: String, asset_path: String },
}

#[derive(Debug, Deserialize)]
struct ModelsIndex {
    #[serde(default)]
    models: Vec<ModelEntry>,
}

#[derive(Debug, Deserialize)]
struct ModelEntry {
    name: String,
    path: String,
    #[serde(rename = "sourceModel")]
    source_model: Option<String>,
    #[serde(rename = "scaleX")]
    scale_x: Option<f32>,
    #[serde(rename = "scaleY")]
    scale_y: Option<f32>,
    #[serde(rename = "scaleZ")]
    scale_z: Option<f32>,
    #[serde(rename = "transX")]
    trans_x: Option<f32>,
    #[serde(rename = "transY")]
    trans_y: Option<f32>,
    #[serde(rename = "transZ")]
    trans_z: Option<f32>,
    #[serde(rename = "rotateY")]
    rotate_y: Option<f32>,
    physics: Option<ModelPhysicsEntry>,
}

#[derive(Debug, Deserialize)]
struct ModelPhysicsEntry {
    character: Option<ModelCharacterPhysicsEntry>,
}

#[derive(Debug, Deserialize)]
struct ModelCharacterPhysicsEntry {
    #[serde(rename = "bevyVisualOffsetY", alias = "bevyCalibrateY")]
    bevy_visual_offset_y: Option<f32>,
}

pub fn audit_project(project_root: &Path) -> Result<AssetAuditReport> {
    let resources = project_root.join("resources");
    let index_path = find_models_index_path(&resources);

    let mut report = AssetAuditReport {
        project: project_root.to_path_buf(),
        ..Default::default()
    };

    if !index_path.is_file() {
        return Ok(report);
    }

    let index = read_models_index(&index_path)?;

    for model in index.models {
        let asset = ModelAsset {
            name: model.name,
            path: resources.join(model.path.replace('/', std::path::MAIN_SEPARATOR_STR)),
        };
        let lower = asset.path.to_string_lossy().to_ascii_lowercase();
        if !asset.path.is_file() {
            report.missing_models.push(asset);
        } else if lower.ends_with(".glb") || lower.ends_with(".gltf") {
            report.gltf_models.push(asset);
        } else if lower.ends_with(".j3o") {
            report.unsupported_j3o_models.push(asset);
        }
    }

    Ok(report)
}

pub fn resolve_model_resource(
    resources_root: &Path,
    model_name: &str,
) -> Result<ModelResource, AssetLookupError> {
    resolve_model_resource_from_indexes(
        &project_models_index_paths(resources_root),
        model_name,
        None,
    )
}

pub fn resolve_model_resource_with_builtin_fallback(
    resources_root: &Path,
    builtin_resources_root: Option<&Path>,
    model_name: &str,
) -> Result<ModelResource, AssetLookupError> {
    match resolve_model_resource(resources_root, model_name) {
        Ok(model) => Ok(model),
        Err(AssetLookupError::ModelNotFound(_)) => {
            let Some(builtin_resources_root) = builtin_resources_root else {
                return Err(AssetLookupError::ModelNotFound(model_name.to_owned()));
            };
            resolve_model_resource_from_indexes(
                &builtin_models_index_paths(builtin_resources_root),
                model_name,
                Some("builtin"),
            )
        }
        Err(error) => Err(error),
    }
}

fn resolve_model_resource_from_indexes(
    index_paths: &[PathBuf],
    model_name: &str,
    asset_source: Option<&str>,
) -> Result<ModelResource, AssetLookupError> {
    for index_path in index_paths.iter().filter(|path| path.is_file()) {
        let Ok(index) = read_models_index(index_path) else {
            continue;
        };

        let Some(entry) = index
            .models
            .iter()
            .find(|entry| entry.name.eq_ignore_ascii_case(model_name))
        else {
            continue;
        };

        return resolve_model_entry(&index, entry, asset_source);
    }

    Err(AssetLookupError::ModelNotFound(model_name.to_owned()))
}

fn resolve_model_entry(
    index: &ModelsIndex,
    entry: &ModelEntry,
    asset_source: Option<&str>,
) -> Result<ModelResource, AssetLookupError> {
    let entry = if is_supported_gltf_path(&entry.path) {
        entry
    } else if let Some(source_model) = entry.source_model.as_ref() {
        index
            .models
            .iter()
            .find(|source| {
                source.name.eq_ignore_ascii_case(source_model)
                    && is_supported_gltf_path(&source.path)
            })
            .ok_or_else(|| AssetLookupError::UnsupportedModelFormat {
                name: entry.name.clone(),
                asset_path: entry.path.clone(),
            })?
    } else {
        entry
    };

    let lower = entry.path.to_ascii_lowercase();
    if !lower.ends_with(".glb") && !lower.ends_with(".gltf") {
        return Err(AssetLookupError::UnsupportedModelFormat {
            name: entry.name.clone(),
            asset_path: entry.path.clone(),
        });
    }

    let asset_path = asset_path_for_source(&entry.path, asset_source);
    Ok(ModelResource {
        name: entry.name.clone(),
        asset_path,
        scale: zip_vec3(entry.scale_x, entry.scale_y, entry.scale_z),
        translation: zip_vec3(entry.trans_x, entry.trans_y, entry.trans_z),
        rotation_y_degrees: entry.rotate_y,
        character_physics: entry
            .physics
            .as_ref()
            .and_then(|physics| physics.character.as_ref())
            .and_then(|character| {
                character
                    .bevy_visual_offset_y
                    .map(|bevy_visual_offset_y| ModelCharacterPhysics {
                        bevy_visual_offset_y: Some(bevy_visual_offset_y),
                    })
            }),
    })
}

fn is_supported_gltf_path(path: &str) -> bool {
    let lower = path.to_ascii_lowercase();
    lower.ends_with(".glb") || lower.ends_with(".gltf")
}

fn zip_vec3(x: Option<f32>, y: Option<f32>, z: Option<f32>) -> Option<[f32; 3]> {
    Some([x?, y?, z?])
}

fn find_models_index_path(resources_root: &Path) -> PathBuf {
    project_models_index_paths(resources_root)
        .into_iter()
        .find(|path| path.is_file())
        .unwrap_or_else(|| resources_root.join("models").join("models-ext.json"))
}

fn project_models_index_paths(resources_root: &Path) -> Vec<PathBuf> {
    vec![
        resources_root.join("Models").join("models-ext.json"),
        resources_root.join("models").join("models-ext.json"),
    ]
}

fn builtin_models_index_paths(resources_root: &Path) -> Vec<PathBuf> {
    vec![
        resources_root.join("Models").join("models-ext.json"),
        resources_root.join("Models").join("models.json"),
        resources_root.join("models").join("models-ext.json"),
        resources_root.join("models").join("models.json"),
    ]
}

fn asset_path_for_source(path: &str, asset_source: Option<&str>) -> String {
    let normalized = path.replace('\\', "/");
    match asset_source {
        Some(source) => format!("{source}://{normalized}"),
        None => normalized,
    }
}

fn read_models_index(index_path: &Path) -> Result<ModelsIndex> {
    let raw = fs::read_to_string(index_path)
        .with_context(|| format!("failed to read {}", index_path.display()))?;
    serde_json::from_str(&raw)
        .or_else(|_| serde_json::from_str(&strip_trailing_json_commas(&raw)))
        .with_context(|| format!("failed to parse {}", index_path.display()))
}

fn strip_trailing_json_commas(raw: &str) -> String {
    let mut output = String::with_capacity(raw.len());
    let mut chars = raw.chars().peekable();
    let mut in_string = false;
    let mut escape = false;

    while let Some(ch) = chars.next() {
        if in_string {
            output.push(ch);
            if escape {
                escape = false;
            } else if ch == '\\' {
                escape = true;
            } else if ch == '"' {
                in_string = false;
            }
            continue;
        }

        if ch == '"' {
            in_string = true;
            output.push(ch);
            continue;
        }

        if ch == ',' {
            let mut lookahead = chars.clone();
            while matches!(lookahead.peek(), Some(next) if next.is_whitespace()) {
                lookahead.next();
            }
            if matches!(lookahead.peek(), Some('}') | Some(']')) {
                continue;
            }
        }

        output.push(ch);
    }

    output
}

impl AssetAuditReport {
    pub fn print(&self) {
        println!("SceneMax NextGen asset audit: {}", self.project.display());
        println!("  GLB/GLTF models: {}", self.gltf_models.len());
        println!(
            "  Unsupported J3O models: {}",
            self.unsupported_j3o_models.len()
        );
        println!("  Missing models: {}", self.missing_models.len());

        if !self.unsupported_j3o_models.is_empty() {
            println!();
            println!("Unsupported J3O models:");
            for model in &self.unsupported_j3o_models {
                println!("  {} -> {}", model.name, model.path.display());
            }
        }

        if !self.missing_models.is_empty() {
            println!();
            println!("Missing models:");
            for model in &self.missing_models {
                println!("  {} -> {}", model.name, model.path.display());
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use std::fs;

    use super::*;

    #[test]
    fn resolves_gltf_model_resource_case_insensitively() {
        let dir = std::env::temp_dir().join(format!("scenemax-assets-test-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        let models_dir = dir.join("resources").join("Models");
        fs::create_dir_all(&models_dir).unwrap();
        fs::write(
            models_dir.join("models-ext.json"),
            r#"{"models":[{"name":"dragon","path":"Models/dragon/dragon.glb"}]}"#,
        )
        .unwrap();

        let model = resolve_model_resource(&dir.join("resources"), "Dragon").unwrap();

        assert_eq!(
            model,
            ModelResource {
                name: "dragon".to_owned(),
                asset_path: "Models/dragon/dragon.glb".to_owned(),
                scale: None,
                translation: None,
                rotation_y_degrees: None,
                character_physics: None,
            }
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn rejects_j3o_model_resource_for_nextgen() {
        let dir =
            std::env::temp_dir().join(format!("scenemax-assets-j3o-test-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        let models_dir = dir.join("resources").join("Models");
        fs::create_dir_all(&models_dir).unwrap();
        fs::write(
            models_dir.join("models-ext.json"),
            r#"{"models":[{"name":"dragon","path":"Models/dragon/dragon.j3o"}]}"#,
        )
        .unwrap();

        let error = resolve_model_resource(&dir.join("resources"), "dragon").unwrap_err();

        assert_eq!(
            error,
            AssetLookupError::UnsupportedModelFormat {
                name: "dragon".to_owned(),
                asset_path: "Models/dragon/dragon.j3o".to_owned(),
            }
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn resolves_native_j3o_to_supported_source_model() {
        let dir = std::env::temp_dir().join(format!(
            "scenemax-assets-source-test-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        let models_dir = dir.join("resources").join("Models");
        fs::create_dir_all(&models_dir).unwrap();
        fs::write(
            models_dir.join("models-ext.json"),
            r#"{"models":[
                {"name":"fighter1","path":"Models/fighter1/fighter1.glb","scaleX":3,"scaleY":3,"scaleZ":3},
                {"name":"fighter1_native","path":"Models/fighter1/fighter1_native.j3o","sourceModel":"fighter1"}
            ]}"#,
        )
        .unwrap();

        let model = resolve_model_resource(&dir.join("resources"), "fighter1_native").unwrap();

        assert_eq!(
            model,
            ModelResource {
                name: "fighter1".to_owned(),
                asset_path: "Models/fighter1/fighter1.glb".to_owned(),
                scale: Some([3.0, 3.0, 3.0]),
                translation: None,
                rotation_y_degrees: None,
                character_physics: None,
            }
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn falls_back_to_builtin_models_json() {
        let dir = std::env::temp_dir().join(format!(
            "scenemax-assets-builtin-test-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        let project_models_dir = dir.join("project").join("resources").join("Models");
        let builtin_models_dir = dir.join("builtin").join("resources").join("Models");
        fs::create_dir_all(&project_models_dir).unwrap();
        fs::create_dir_all(&builtin_models_dir).unwrap();
        fs::write(
            builtin_models_dir.join("models-ext.json"),
            r#"{"models":[{"name":"ninja_fighter","path":"Models/ninja-fighter/ninja.gltf"}]}"#,
        )
        .unwrap();
        fs::write(
            builtin_models_dir.join("models.json"),
            r#"{"models":[{"name":"gemini","path":"Models/gemini/scene.gltf","scaleX":0.025,"scaleY":0.025,"scaleZ":0.025,}]}"#,
        )
        .unwrap();

        let model = resolve_model_resource_with_builtin_fallback(
            &dir.join("project").join("resources"),
            Some(&dir.join("builtin").join("resources")),
            "Gemini",
        )
        .unwrap();

        assert_eq!(
            model,
            ModelResource {
                name: "gemini".to_owned(),
                asset_path: "builtin://Models/gemini/scene.gltf".to_owned(),
                scale: Some([0.025, 0.025, 0.025]),
                translation: None,
                rotation_y_degrees: None,
                character_physics: None,
            }
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn project_model_index_wins_over_builtin_fallback() {
        let dir = std::env::temp_dir().join(format!(
            "scenemax-assets-project-wins-test-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        let project_models_dir = dir.join("project").join("resources").join("Models");
        let builtin_models_dir = dir.join("builtin").join("resources").join("Models");
        fs::create_dir_all(&project_models_dir).unwrap();
        fs::create_dir_all(&builtin_models_dir).unwrap();
        fs::write(
            project_models_dir.join("models-ext.json"),
            r#"{"models":[{"name":"gemini","path":"Models/gemini/project.glb","scaleX":1,"scaleY":1,"scaleZ":1}]}"#,
        )
        .unwrap();
        fs::write(
            builtin_models_dir.join("models.json"),
            r#"{"models":[{"name":"gemini","path":"Models/gemini/builtin.gltf","scaleX":2,"scaleY":2,"scaleZ":2}]}"#,
        )
        .unwrap();

        let model = resolve_model_resource_with_builtin_fallback(
            &dir.join("project").join("resources"),
            Some(&dir.join("builtin").join("resources")),
            "gemini",
        )
        .unwrap();

        assert_eq!(
            model,
            ModelResource {
                name: "gemini".to_owned(),
                asset_path: "Models/gemini/project.glb".to_owned(),
                scale: Some([1.0, 1.0, 1.0]),
                translation: None,
                rotation_y_degrees: None,
                character_physics: None,
            }
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn resolves_bevy_character_visual_offset_from_model_physics() {
        let dir = std::env::temp_dir().join(format!(
            "scenemax-assets-character-physics-test-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        let models_dir = dir.join("resources").join("Models");
        fs::create_dir_all(&models_dir).unwrap();
        fs::write(
            models_dir.join("models-ext.json"),
            r#"{"models":[{
                "name":"avatar",
                "path":"Models/avatar/avatar.glb",
                "physics":{"character":{"calibrateY":-2.5,"bevyVisualOffsetY":-0.18}}
            }]}"#,
        )
        .unwrap();

        let model = resolve_model_resource(&dir.join("resources"), "avatar").unwrap();

        assert_eq!(
            model.character_physics,
            Some(ModelCharacterPhysics {
                bevy_visual_offset_y: Some(-0.18)
            })
        );

        let _ = fs::remove_dir_all(&dir);
    }
}
