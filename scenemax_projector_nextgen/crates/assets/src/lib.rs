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
    let index_path = find_models_index_path(resources_root);
    if !index_path.is_file() {
        return Err(AssetLookupError::ModelNotFound(model_name.to_owned()));
    }

    let Ok(index) = read_models_index(&index_path) else {
        return Err(AssetLookupError::ModelNotFound(model_name.to_owned()));
    };

    let Some(entry) = index
        .models
        .iter()
        .find(|entry| entry.name.eq_ignore_ascii_case(model_name))
    else {
        return Err(AssetLookupError::ModelNotFound(model_name.to_owned()));
    };

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

    Ok(ModelResource {
        name: entry.name.clone(),
        asset_path: entry.path.replace('\\', "/"),
        scale: zip_vec3(entry.scale_x, entry.scale_y, entry.scale_z),
        translation: zip_vec3(entry.trans_x, entry.trans_y, entry.trans_z),
        rotation_y_degrees: entry.rotate_y,
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
    let upper = resources_root.join("Models").join("models-ext.json");
    if upper.is_file() {
        upper
    } else {
        resources_root.join("models").join("models-ext.json")
    }
}

fn read_models_index(index_path: &Path) -> Result<ModelsIndex> {
    let raw = fs::read_to_string(index_path)
        .with_context(|| format!("failed to read {}", index_path.display()))?;
    serde_json::from_str(&raw).with_context(|| format!("failed to parse {}", index_path.display()))
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
            }
        );

        let _ = fs::remove_dir_all(&dir);
    }
}
