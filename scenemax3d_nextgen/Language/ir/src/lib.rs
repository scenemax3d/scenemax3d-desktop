use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeIr {
    pub schema_version: u32,
    pub project: ProjectIr,
    pub window: WindowIr,
    #[serde(default)]
    pub entities: Vec<EntityIr>,
    #[serde(default)]
    pub actions: Vec<ActionIr>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProjectIr {
    pub name: Option<String>,
    pub guid: Option<String>,
    pub resource_root: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WindowIr {
    pub width: u32,
    pub height: u32,
    #[serde(default)]
    pub disable_audio: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EntityIr {
    pub name: String,
    pub runtime_name: String,
    pub kind: EntityKind,
    pub resource: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum EntityKind {
    Model3d,
    Camera,
    Light,
    Sphere,
    Box,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", tag = "kind")]
pub enum ActionIr {
    Move {
        target: String,
        axis: String,
        distance: f32,
        seconds: f32,
    },
    Rotate {
        target: String,
        axis: String,
        degrees: f32,
        seconds: f32,
    },
    Animate {
        target: String,
        clip: String,
        speed: f32,
    },
}
