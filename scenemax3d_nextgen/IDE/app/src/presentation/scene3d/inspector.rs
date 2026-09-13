//! Java-compatible property grid with live, undoable document edits.
use super::*;
use bevy::text::EditableText;
use scenemax_ide_ui::property::{self, Checked, Choice};
use serde_json::{Value, json};

#[derive(Component)]
pub(crate) struct Property(pub String, pub String);
#[derive(Component)]
pub(crate) struct Apply(pub bool);
#[derive(Component)]
pub(crate) struct Proportional;

pub(super) fn build(commands: &mut Commands, parent: Entity, scene: &Scene3d, index: usize) {
    let Some(e) = scene.entities.get(index) else {
        return;
    };
    let p = &e.properties;
    property::heading(commands, parent, &format!("Properties · {}", e.kind));
    if e.kind == "CAMERA" {
        vector(commands, parent, "Position", "position", e.position);
        let (y, z, x) = Quat::from_array(e.rotation)
            .normalize()
            .to_euler(EulerRot::YZX);
        vector(
            commands,
            parent,
            "Rotation (degrees)",
            "angles",
            [x.to_degrees(), y.to_degrees(), z.to_degrees()],
        );
        return;
    }
    if e.kind == "CINEMATIC_RIG" {
        super::rig::basics(commands, parent, e);
        super::rig::build(commands, parent, scene, e);
        return;
    }
    field(commands, parent, "Name", "name", &e.name);
    if e.kind != "SECTION" {
        choice(
            commands,
            parent,
            "Attach to",
            "attachTo",
            p,
            &scene
                .entities
                .iter()
                .filter(|other| other.id != e.id && other.kind == "MODEL")
                .map(|e| e.name.clone())
                .chain(std::iter::once(String::new()))
                .collect::<Vec<_>>(),
        );
        vector(commands, parent, "Position", "position", e.position);
        // JME Quaternion.fromAngles composes Y * Z * X; match its inspector angles.
        let q = Quat::from_array(e.rotation).normalize();
        let (y, z, x) = q.to_euler(EulerRot::YZX);
        vector(
            commands,
            parent,
            "Rotation (degrees)",
            "angles",
            [x.to_degrees(), y.to_degrees(), z.to_degrees()],
        );
        property::checkbox(commands, parent, Proportional, "Proportional scale", true);
        vector(commands, parent, "Scale", "scale", e.scale);
    }
    if matches!(
        e.kind.as_str(),
        "MODEL"
            | "BOX"
            | "SPHERE"
            | "QUAD"
            | "WEDGE"
            | "CONE"
            | "CYLINDER"
            | "HOLLOW_CYLINDER"
            | "STAIRS"
            | "ARCH"
    ) {
        for (key, title) in [
            ("hidden", "Hidden at runtime"),
            ("multiplayer", "Multiplayer"),
        ] {
            toggle(commands, parent, title, key, p);
        }
        choice(
            commands,
            parent,
            "Shader",
            "shader",
            p,
            scene
                .catalog
                .get("shader")
                .map(Vec::as_slice)
                .unwrap_or(&[]),
        );
        choice(
            commands,
            parent,
            "Shadow mode",
            "shadowMode",
            p,
            &strings(&["none", "cast", "receive", "both"]),
        );
        if e.kind == "MODEL" {
            choice(
                commands,
                parent,
                "Model asset",
                "resourcePath",
                p,
                scene
                    .catalog
                    .get("resourcePath")
                    .map(Vec::as_slice)
                    .unwrap_or(&[]),
            );
            choice(
                commands,
                parent,
                "Collision shape",
                "modelCollisionShape",
                p,
                &strings(&["default", "box", "boxes", "mesh", "none"]),
            );
            for (key, title) in [
                ("staticModel", "Static"),
                ("dynamicModel", "Dynamic"),
                ("vehicleModel", "Vehicle"),
            ] {
                toggle(commands, parent, title, key, p);
            }
            field(
                commands,
                parent,
                "Joint mapping",
                "jointMapping",
                p["jointMapping"].as_str().unwrap_or(""),
            );
        } else {
            choice(
                commands,
                parent,
                "Material",
                "material",
                p,
                scene
                    .catalog
                    .get("material")
                    .map(Vec::as_slice)
                    .unwrap_or(&[]),
            );
            toggle(commands, parent, "Static", "staticEntity", p);
            toggle(commands, parent, "Collider", "colliderEntity", p);
            for key in [
                "radius",
                "sizeX",
                "sizeY",
                "sizeZ",
                "quadWidth",
                "quadHeight",
                "wedgeWidth",
                "wedgeHeight",
                "wedgeDepth",
                "radiusTop",
                "radiusBottom",
                "innerRadiusTop",
                "innerRadiusBottom",
                "height",
                "stairsWidth",
                "stairsStepHeight",
                "stairsStepDepth",
                "stairsStepCount",
                "archWidth",
                "archHeight",
                "archDepth",
                "archThickness",
                "archSegments",
            ] {
                if p[key].is_number() {
                    field(
                        commands,
                        parent,
                        key,
                        &format!("number:{key}"),
                        &p[key].to_string(),
                    );
                }
            }
        }
        property::heading(commands, parent, "Inverse kinematics");
        choice(
            commands,
            parent,
            "IK asset",
            "ikAsset",
            p,
            scene
                .catalog
                .get("ikAsset")
                .map(Vec::as_slice)
                .unwrap_or(&[]),
        );
    }
    if let Some(layers) = p["ikLayers"].as_array() {
        for (i, layer) in layers.iter().enumerate() {
            property::heading(
                commands,
                parent,
                layer["layerName"].as_str().unwrap_or("IK layer"),
            );
            let key = format!("ik:{i}:enabled");
            let enabled = layer["enabled"].as_bool().unwrap_or(false);
            property::checkbox(
                commands,
                parent,
                Property(key, enabled.to_string()),
                "Play",
                enabled,
            );
            for (key, title, default) in [
                ("target", "Target", ""),
                ("blend", "Blend", "0.2"),
                ("weight", "Weight", "1"),
            ] {
                let value = if let Some(s) = layer[key].as_str() {
                    s.to_owned()
                } else if layer[key].is_number() {
                    layer[key].to_string()
                } else {
                    default.into()
                };
                field(commands, parent, title, &format!("ik:{i}:{key}"), &value);
            }
        }
    }
    if e.kind == "CINEMATIC_TRACK" {
        property::heading(commands, parent, "Cinematic track");
        for (key, title, default) in [
            ("radiusX", "Radius X", 2.5),
            ("radiusZ", "Radius Z", 2.5),
            ("anchorCount", "Anchors", 360.),
            ("selectedStartAnchor", "Start anchor", -1.),
            ("selectedEndAnchor", "End anchor", -1.),
            ("previewSpeed", "Preview speed", 30.),
        ] {
            field(
                commands,
                parent,
                title,
                &format!("track:{key}"),
                &p["cinematicTrackData"][key]
                    .as_f64()
                    .unwrap_or(default)
                    .to_string(),
            );
        }
    }
    if e.kind == "CINEMATIC_TRACK" {
        button(
            commands,
            parent,
            "Add saved range to rig",
            super::segments::Action::AddRange,
        );
    }
    if !e.note.is_empty() {
        commands.spawn((label(&e.note, 11.), ChildOf(parent)));
    }
}
pub(super) fn actions(commands: &mut Commands, parent: Entity, scene: &Scene3d, index: usize) {
    let Some(e) = scene.entities.get(index) else {
        return;
    };
    let parent = commands
        .spawn((
            Node {
                flex_wrap: FlexWrap::Wrap,
                column_gap: px(2.),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    commands.spawn((label("Live properties", 11.), ChildOf(parent)));
    if matches!(e.kind.as_str(), "CINEMATIC_TRACK" | "CINEMATIC_RIG") {
        button(commands, parent, "Play", super::playback::Action::Play);
        button(
            commands,
            parent,
            "Stop preview",
            super::playback::Action::Stop,
        );
    }
}
fn strings(values: &[&str]) -> Vec<String> {
    values.iter().map(|s| (*s).into()).collect()
}
pub(super) fn field(commands: &mut Commands, parent: Entity, title: &str, key: &str, value: &str) {
    commands.spawn((label(title, 11.), ChildOf(parent)));
    if (key.starts_with("track:")
        || key.starts_with("segment:")
        || (key.starts_with("ik:") && !key.ends_with(":target"))
        || key.starts_with("number:")
        || key.starts_with("position:")
        || key.starts_with("angles:")
        || key.starts_with("scale:")
        || key.starts_with("cinematicTargetOffset:"))
        && let Ok(current) = value.parse::<f64>()
    {
        let (min, max, step) = if key.starts_with("angles:") {
            (-180., 180., 1.)
        } else if key.starts_with("scale:") {
            (0.01, current.max(10.) * 2., 0.01)
        } else if key.starts_with("track:")
            || key.starts_with("segment:")
            || key.starts_with("ik:")
            || key.starts_with("number:")
        {
            (
                if key.contains("selected") {
                    -1.
                } else if key.ends_with("anchorCount") {
                    8.
                } else {
                    0.
                },
                if key.ends_with("anchorCount") {
                    4096.
                } else {
                    current.max(10.) * 2.
                },
                if key.to_lowercase().contains("anchor") || key.contains("Count") {
                    1.
                } else {
                    0.1
                },
            )
        } else {
            (current - 100., current + 100., 0.1)
        };
        scenemax_ide_ui::number::input(
            commands,
            parent,
            Property(key.into(), value.into()),
            value,
            min,
            max,
            step,
        );
    } else {
        property::input(commands, parent, Property(key.into(), value.into()), value);
    }
}
fn toggle(commands: &mut Commands, parent: Entity, title: &str, key: &str, p: &Value) {
    property::checkbox(
        commands,
        parent,
        Property(key.into(), p[key].as_bool().unwrap_or(false).to_string()),
        title,
        p[key].as_bool().unwrap_or(false),
    );
}
pub(super) fn choice(
    commands: &mut Commands,
    parent: Entity,
    title: &str,
    key: &str,
    p: &Value,
    options: &[String],
) {
    commands.spawn((label(title, 11.), ChildOf(parent)));
    let value = p[key]
        .as_str()
        .unwrap_or(if key.starts_with("cinematicEase") {
            "linear"
        } else if key == "shadowMode" {
            "none"
        } else if key == "modelCollisionShape" {
            "default"
        } else {
            ""
        });
    property::dropdown(
        commands,
        parent,
        Property(key.into(), value.into()),
        value,
        options,
    );
}
pub(super) fn vector(
    commands: &mut Commands,
    parent: Entity,
    title: &str,
    key: &str,
    values: [f32; 3],
) {
    property::heading(commands, parent, title);
    let row = commands
        .spawn((
            Node {
                width: percent(100.),
                column_gap: px(4.),
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    for (i, value) in values.into_iter().enumerate() {
        let cell = commands
            .spawn((
                Node {
                    width: percent(33.333),
                    min_width: px(0.),
                    flex_direction: FlexDirection::Column,
                    ..default()
                },
                ChildOf(row),
            ))
            .id();
        field(
            commands,
            cell,
            ["X", "Y", "Z"][i],
            &format!("{key}:{i}"),
            &format!("{value:.4}"),
        );
    }
}

type PropertyFields<'w, 's> = Query<
    'w,
    's,
    (
        &'static Property,
        Option<&'static EditableText>,
        Option<&'static Checked>,
        Option<&'static Choice>,
    ),
>;

pub(crate) fn apply(
    actions: Query<(&Interaction, &Apply), Changed<Interaction>>,
    fields: PropertyFields,
    proportional: Query<&Checked, With<Proportional>>,
    objects: Query<(&gizmo::SceneObject, &Transform)>,
    state: Res<SceneState>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<crate::application::ViewChange>,
) {
    for (interaction, action) in &actions {
        if *interaction != Interaction::Pressed {
            continue;
        }
        let result = (|| -> Result<(), String> {
            let (id, revision) = state.current.ok_or("No scene document")?;
            let scene = state.scene.as_ref().ok_or("Scene is loading")?;
            let entry = scene
                .entities
                .get(state.selected)
                .ok_or("Select a scene entry")?;
            let doc = session
                .workspace
                .document_mut(id)
                .map_err(|e| e.to_string())?;
            if doc.revision() != revision {
                return Err("Document changed; wait for the properties to reload".into());
            }
            let patch = if action.0 {
                let (_, t) = objects
                    .iter()
                    .find(|(o, _)| o.0 == state.selected)
                    .ok_or("No transform")?;
                vec![
                    ("position".into(), json!(t.translation.to_array())),
                    ("rotation".into(), json!(t.rotation.to_array())),
                    ("scale".into(), json!(t.scale.to_array())),
                ]
            } else {
                let values = fields
                    .iter()
                    .filter_map(|(p, text, checked, choice)| {
                        let value = if let Some(text) = text {
                            text.value().to_string()
                        } else if let Some(checked) = checked {
                            checked.0.to_string()
                        } else {
                            choice.map(|v| v.0.clone()).unwrap_or_default()
                        };
                        (value != p.1).then(|| (p.0.clone(), value))
                    })
                    .collect();
                super::properties::transaction(
                    entry,
                    scene,
                    &values,
                    proportional.iter().any(|p| p.0),
                )?
            };
            if patch.is_empty() {
                return Ok(());
            }
            let source = scenemax_ide_core::scene3d::patch(doc.text(), &entry.pointer, &patch)?;
            doc.replace_text(source);
            changes.write(crate::application::ViewChange::BufferChanged(id));
            Ok(())
        })();
        session.status = result.map_or_else(
            |e| e,
            |()| "Scene properties applied · Ctrl+S to save · Ctrl+Z to undo".into(),
        );
    }
}
