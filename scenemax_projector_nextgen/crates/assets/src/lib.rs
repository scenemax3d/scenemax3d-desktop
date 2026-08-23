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
pub struct AnimationResource {
    pub name: String,
    pub asset_path: String,
    pub clip_name: String,
    pub bevy_retarget: AnimationRetargetOptions,
    pub bevy_baked_retargets: Vec<AnimationBakedRetarget>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AnimationBakedRetarget {
    pub model: String,
    pub path: String,
    pub clip_name: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AnimationRetargetOptions {
    pub profile: String,
    pub skip_top_animated_targets: usize,
    pub exclude_bones: Vec<String>,
    pub root_bone: String,
    pub scale_base_bone: String,
    pub remove_unimportant_translation_tracks: bool,
    pub remove_motion_translation_tracks: bool,
    pub remove_motion_rotation_tracks: bool,
    pub normalize_motion_scale: bool,
    pub visual_translation: [f32; 3],
    pub visual_rotation_degrees: [f32; 3],
    pub locked_translation_axes: [bool; 3],
}

impl Default for AnimationRetargetOptions {
    fn default() -> Self {
        Self {
            profile: "auto".to_owned(),
            skip_top_animated_targets: 1,
            exclude_bones: Vec::new(),
            root_bone: "Root".to_owned(),
            scale_base_bone: "Hips".to_owned(),
            remove_unimportant_translation_tracks: true,
            remove_motion_translation_tracks: true,
            remove_motion_rotation_tracks: false,
            normalize_motion_scale: true,
            visual_translation: [0.0, 0.0, 0.0],
            visual_rotation_degrees: [0.0, 0.0, 0.0],
            locked_translation_axes: [false, false, false],
        }
    }
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
    #[error("animation resource '{0}' was not found")]
    AnimationNotFound(String),
    #[error("animation resource '{name}' uses unsupported asset format: {asset_path}")]
    UnsupportedAnimationFormat { name: String, asset_path: String },
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

#[derive(Debug, Deserialize)]
struct AnimationsIndex {
    #[serde(default)]
    animations: Vec<AnimationEntry>,
}

#[derive(Debug, Deserialize)]
struct AnimationEntry {
    name: String,
    path: String,
    #[serde(rename = "clipName")]
    clip_name: Option<String>,
    #[serde(rename = "bevyRetarget")]
    bevy_retarget: Option<AnimationRetargetEntry>,
    #[serde(rename = "bevyBakedRetargets", default)]
    bevy_baked_retargets: Vec<AnimationBakedRetargetEntry>,
}

#[derive(Debug, Deserialize)]
struct AnimationBakedRetargetEntry {
    model: String,
    path: String,
    #[serde(rename = "clipName")]
    clip_name: Option<String>,
}

#[derive(Debug, Deserialize)]
struct AnimationRetargetEntry {
    profile: Option<String>,
    #[serde(rename = "skipTopAnimatedTargets")]
    skip_top_animated_targets: Option<usize>,
    #[serde(rename = "skipRootTarget")]
    skip_root_target: Option<bool>,
    #[serde(rename = "excludeBones", default)]
    exclude_bones: Vec<String>,
    #[serde(rename = "rootBone")]
    root_bone: Option<String>,
    #[serde(rename = "scaleBaseBone")]
    scale_base_bone: Option<String>,
    #[serde(rename = "removeUnimportantTranslationTracks")]
    remove_unimportant_translation_tracks: Option<bool>,
    #[serde(rename = "removeMotionTranslationTracks")]
    remove_motion_translation_tracks: Option<bool>,
    #[serde(rename = "removeMotionRotationTracks")]
    remove_motion_rotation_tracks: Option<bool>,
    #[serde(rename = "normalizeMotionScale")]
    normalize_motion_scale: Option<bool>,
    #[serde(rename = "visualTranslation")]
    visual_translation: Option<AnimationRetargetVectorEntry>,
    #[serde(rename = "visualRotationDegrees")]
    visual_rotation_degrees: Option<AnimationRetargetVectorEntry>,
    #[serde(rename = "lockedTranslationAxes")]
    locked_translation_axes: Option<AnimationRetargetAxesEntry>,
}

#[derive(Debug, Deserialize)]
struct AnimationRetargetVectorEntry {
    #[serde(default)]
    x: f32,
    #[serde(default)]
    y: f32,
    #[serde(default)]
    z: f32,
}

#[derive(Debug, Deserialize)]
struct AnimationRetargetAxesEntry {
    #[serde(default)]
    x: bool,
    #[serde(default)]
    y: bool,
    #[serde(default)]
    z: bool,
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

pub fn resolve_animation_resource(
    resources_root: &Path,
    animation_name: &str,
) -> Result<AnimationResource, AssetLookupError> {
    resolve_animation_resource_from_indexes(
        &project_animation_index_paths(resources_root),
        animation_name,
        None,
    )
}

pub fn resolve_animation_resource_with_builtin_fallback(
    resources_root: &Path,
    builtin_resources_root: Option<&Path>,
    animation_name: &str,
) -> Result<AnimationResource, AssetLookupError> {
    match resolve_animation_resource(resources_root, animation_name) {
        Ok(animation) => Ok(animation),
        Err(AssetLookupError::AnimationNotFound(_)) => {
            let Some(builtin_resources_root) = builtin_resources_root else {
                return Err(AssetLookupError::AnimationNotFound(
                    animation_name.to_owned(),
                ));
            };
            resolve_animation_resource_from_indexes(
                &builtin_animation_index_paths(builtin_resources_root),
                animation_name,
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

fn resolve_animation_resource_from_indexes(
    index_paths: &[PathBuf],
    animation_name: &str,
    asset_source: Option<&str>,
) -> Result<AnimationResource, AssetLookupError> {
    for index_path in index_paths.iter().filter(|path| path.is_file()) {
        let Ok(index) = read_animations_index(index_path) else {
            continue;
        };

        let Some(entry) = index
            .animations
            .iter()
            .find(|entry| entry.name.eq_ignore_ascii_case(animation_name))
        else {
            continue;
        };

        return resolve_animation_entry(entry, asset_source);
    }

    Err(AssetLookupError::AnimationNotFound(
        animation_name.to_owned(),
    ))
}

fn resolve_animation_entry(
    entry: &AnimationEntry,
    asset_source: Option<&str>,
) -> Result<AnimationResource, AssetLookupError> {
    if !is_supported_gltf_path(&entry.path) {
        return Err(AssetLookupError::UnsupportedAnimationFormat {
            name: entry.name.clone(),
            asset_path: entry.path.clone(),
        });
    }

    Ok(AnimationResource {
        name: entry.name.clone(),
        asset_path: asset_path_for_source(&entry.path, asset_source),
        clip_name: entry
            .clip_name
            .as_deref()
            .filter(|clip_name| !clip_name.is_empty())
            .unwrap_or(entry.name.as_str())
            .to_owned(),
        bevy_retarget: animation_retarget_options(entry.bevy_retarget.as_ref()),
        bevy_baked_retargets: animation_baked_retargets(entry),
    })
}

fn animation_baked_retargets(entry: &AnimationEntry) -> Vec<AnimationBakedRetarget> {
    entry
        .bevy_baked_retargets
        .iter()
        .filter_map(|baked| {
            let model = baked.model.trim();
            let path = baked.path.trim();
            if model.is_empty() || path.is_empty() {
                return None;
            }
            Some(AnimationBakedRetarget {
                model: model.to_owned(),
                path: path.replace('\\', "/"),
                clip_name: baked
                    .clip_name
                    .as_deref()
                    .filter(|clip_name| !clip_name.trim().is_empty())
                    .unwrap_or(entry.name.as_str())
                    .to_owned(),
            })
        })
        .collect()
}

fn animation_retarget_options(entry: Option<&AnimationRetargetEntry>) -> AnimationRetargetOptions {
    let mut options = AnimationRetargetOptions::default();
    let Some(entry) = entry else {
        return options;
    };

    if let Some(profile) = entry
        .profile
        .as_deref()
        .and_then(normalized_retarget_profile)
    {
        options.profile = profile.to_owned();
    }

    if let Some(skip_top_animated_targets) = entry.skip_top_animated_targets {
        options.skip_top_animated_targets = skip_top_animated_targets.min(16);
    } else if let Some(skip_root_target) = entry.skip_root_target {
        options.skip_top_animated_targets = usize::from(skip_root_target);
    }

    options.exclude_bones = entry
        .exclude_bones
        .iter()
        .map(|bone| bone.trim())
        .filter(|bone| !bone.is_empty())
        .map(ToOwned::to_owned)
        .collect();
    if let Some(root_bone) = normalized_retarget_bone_name(entry.root_bone.as_deref()) {
        options.root_bone = root_bone;
    }
    if let Some(scale_base_bone) = normalized_retarget_bone_name(entry.scale_base_bone.as_deref()) {
        options.scale_base_bone = scale_base_bone;
    }
    if let Some(remove) = entry.remove_unimportant_translation_tracks {
        options.remove_unimportant_translation_tracks = remove;
    }
    if let Some(remove) = entry.remove_motion_translation_tracks {
        options.remove_motion_translation_tracks = remove;
    }
    if let Some(remove) = entry.remove_motion_rotation_tracks {
        options.remove_motion_rotation_tracks = remove;
    }
    if let Some(normalize) = entry.normalize_motion_scale {
        options.normalize_motion_scale = normalize;
    }
    if let Some(rotation) = entry.visual_rotation_degrees.as_ref() {
        options.visual_rotation_degrees = [rotation.x, rotation.y, rotation.z];
    }
    if let Some(translation) = entry.visual_translation.as_ref() {
        options.visual_translation = [translation.x, translation.y, translation.z];
    }
    if let Some(axes) = entry.locked_translation_axes.as_ref() {
        options.locked_translation_axes = [axes.x, axes.y, axes.z];
    }
    options
}

fn normalized_retarget_profile(profile: &str) -> Option<&'static str> {
    let normalized = profile
        .chars()
        .filter(|value| value.is_ascii_alphanumeric())
        .flat_map(|value| value.to_lowercase())
        .collect::<String>();
    match normalized.as_str() {
        "auto" => Some("auto"),
        "humanoid" | "human" => Some("humanoid"),
        "exact" | "none" | "off" => Some("exact"),
        _ => None,
    }
}

fn normalized_retarget_bone_name(bone: Option<&str>) -> Option<String> {
    bone.map(str::trim)
        .filter(|bone| !bone.is_empty())
        .map(ToOwned::to_owned)
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

fn project_animation_index_paths(resources_root: &Path) -> Vec<PathBuf> {
    vec![
        resources_root
            .join("Animations")
            .join("animations-ext.json"),
        resources_root
            .join("animations")
            .join("animations-ext.json"),
        resources_root.join("Animations").join("animations.json"),
        resources_root.join("animations").join("animations.json"),
    ]
}

fn builtin_animation_index_paths(resources_root: &Path) -> Vec<PathBuf> {
    vec![
        resources_root
            .join("Animations")
            .join("animations-ext.json"),
        resources_root.join("Animations").join("animations.json"),
        resources_root
            .join("animations")
            .join("animations-ext.json"),
        resources_root.join("animations").join("animations.json"),
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

fn read_animations_index(index_path: &Path) -> Result<AnimationsIndex> {
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
    fn resolves_external_animation_resource_case_insensitively() {
        let dir = std::env::temp_dir().join(format!(
            "scenemax-assets-animation-test-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        let animations_dir = dir.join("resources").join("animations");
        fs::create_dir_all(&animations_dir).unwrap();
        fs::write(
            animations_dir.join("animations-ext.json"),
            r#"{"animations":[{"name":"spin_attack","path":"animations/spin_attack/spin.glb","clipName":"SourceClip",}]}"#,
        )
        .unwrap();

        let animation = resolve_animation_resource(&dir.join("resources"), "Spin_Attack").unwrap();

        assert_eq!(
            animation,
            AnimationResource {
                name: "spin_attack".to_owned(),
                asset_path: "animations/spin_attack/spin.glb".to_owned(),
                clip_name: "SourceClip".to_owned(),
                bevy_retarget: AnimationRetargetOptions::default(),
                bevy_baked_retargets: Vec::new(),
            }
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn resolves_external_animation_baked_retargets() {
        let dir = std::env::temp_dir().join(format!(
            "scenemax-assets-animation-baked-retarget-test-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        let animations_dir = dir.join("resources").join("animations");
        fs::create_dir_all(&animations_dir).unwrap();
        fs::write(
            animations_dir.join("animations-ext.json"),
            r#"{"animations":[{"name":"throw","path":"animations/throw/throw.glb","bevyBakedRetargets":[{"model":"hero","path":"animations/throw/baked/hero.json","clipName":"hero_throw"}]}]}"#,
        )
        .unwrap();

        let animation = resolve_animation_resource(&dir.join("resources"), "throw").unwrap();

        assert_eq!(
            animation.bevy_baked_retargets,
            vec![AnimationBakedRetarget {
                model: "hero".to_owned(),
                path: "animations/throw/baked/hero.json".to_owned(),
                clip_name: "hero_throw".to_owned(),
            }]
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn resolves_external_animation_retarget_options() {
        let dir = std::env::temp_dir().join(format!(
            "scenemax-assets-animation-retarget-test-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        let animations_dir = dir.join("resources").join("animations");
        fs::create_dir_all(&animations_dir).unwrap();
        fs::write(
            animations_dir.join("animations-ext.json"),
            r#"{"animations":[{"name":"throw","path":"animations/throw/throw.glb","bevyRetarget":{"profile":"humanoid","skipTopAnimatedTargets":2,"excludeBones":["Hips",""],"rootBone":"Root","scaleBaseBone":"Hips","removeUnimportantTranslationTracks":true,"removeMotionTranslationTracks":true,"removeMotionRotationTracks":false,"normalizeMotionScale":true,"lockedTranslationAxes":{"y":true}}}]}"#,
        )
        .unwrap();

        let animation = resolve_animation_resource(&dir.join("resources"), "throw").unwrap();

        assert_eq!(
            animation.bevy_retarget,
            AnimationRetargetOptions {
                profile: "humanoid".to_owned(),
                skip_top_animated_targets: 2,
                exclude_bones: vec!["Hips".to_owned()],
                root_bone: "Root".to_owned(),
                scale_base_bone: "Hips".to_owned(),
                remove_unimportant_translation_tracks: true,
                remove_motion_translation_tracks: true,
                remove_motion_rotation_tracks: false,
                normalize_motion_scale: true,
                visual_translation: [0.0, 0.0, 0.0],
                visual_rotation_degrees: [0.0, 0.0, 0.0],
                locked_translation_axes: [false, true, false],
            }
        );

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn rejects_j3o_animation_resource_for_nextgen() {
        let dir = std::env::temp_dir().join(format!(
            "scenemax-assets-animation-j3o-test-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&dir);
        let animations_dir = dir.join("resources").join("animations");
        fs::create_dir_all(&animations_dir).unwrap();
        fs::write(
            animations_dir.join("animations-ext.json"),
            r#"{"animations":[{"name":"spin_attack","path":"animations/spin_attack/spin.j3o"}]}"#,
        )
        .unwrap();

        let error = resolve_animation_resource(&dir.join("resources"), "spin_attack").unwrap_err();

        assert_eq!(
            error,
            AssetLookupError::UnsupportedAnimationFormat {
                name: "spin_attack".to_owned(),
                asset_path: "animations/spin_attack/spin.j3o".to_owned(),
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
