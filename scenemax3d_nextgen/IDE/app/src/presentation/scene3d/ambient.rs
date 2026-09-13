//! In-document ambient lighting editor with Java serialization.
use super::*;
use bevy::text::EditableText;
use scenemax_ide_ui::property::{self, Checked};
use serde_json::{Value, json};
#[derive(Component)]
pub(crate) struct Field(&'static str);
#[derive(Component)]
pub(crate) struct Apply(Entity, DocumentId, DocumentRevision);
pub(super) fn spawn(
    commands: &mut Commands,
    parent: Entity,
    id: DocumentId,
    revision: DocumentRevision,
    p: &Value,
) {
    let panel = commands
        .spawn((
            super::tools::Picker,
            Node {
                position_type: PositionType::Absolute,
                top: px(32.),
                right: px(0.),
                width: px(240.),
                padding: px(10.).all(),
                row_gap: px(4.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BackgroundColor(PANEL),
            GlobalZIndex(90),
            ChildOf(parent),
        ))
        .id();
    property::heading(commands, panel, "Ambient light");
    for (key, title, fallback) in [
        ("name", "Name", "scene_ambient"),
        ("color", "Color (#RRGGBB)", "#ffffff"),
    ] {
        commands.spawn((label(title, 12.), ChildOf(panel)));
        property::input(
            commands,
            panel,
            Field(key),
            p[key].as_str().unwrap_or(fallback),
        );
    }
    commands.spawn((label("Brightness", 12.), ChildOf(panel)));
    property::input(
        commands,
        panel,
        Field("brightness"),
        &p["brightness"].as_f64().unwrap_or(220.).to_string(),
    );
    property::checkbox(
        commands,
        panel,
        Field("enabled"),
        "Enabled",
        p["enabled"].as_bool().unwrap_or(false),
    );
    property::checkbox(
        commands,
        panel,
        Field("affectsLightmappedMeshes"),
        "Affect lightmapped meshes",
        p["affectsLightmappedMeshes"].as_bool().unwrap_or(true),
    );
    button(commands, panel, "Apply", Apply(panel, id, revision));
    let close = button(commands, panel, "Cancel", Name::new("Close ambient light"));
    commands
        .entity(close)
        .observe(move |_: On<Pointer<Click>>, mut commands: Commands| {
            commands.entity(panel).try_despawn();
        });
}
pub(crate) fn update(
    mut commands: Commands,
    actions: Query<(&Interaction, &Apply), Changed<Interaction>>,
    fields: Query<(&Field, Option<&EditableText>, Option<&Checked>)>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<crate::application::ViewChange>,
) {
    for (interaction, apply) in &actions {
        if *interaction != Interaction::Pressed {
            continue;
        }
        let result = (|| -> Result<(), String> {
            let doc = session
                .workspace
                .document_mut(apply.1)
                .map_err(|e| e.to_string())?;
            if doc.revision() != apply.2 {
                return Err("Document changed; reopen ambient settings".into());
            }
            let mut source: Value = serde_json::from_str(doc.text()).map_err(|e| e.to_string())?;
            if !source["bevyAmbientLight"].is_object() {
                source["bevyAmbientLight"] = json!({});
            }
            let p = &mut source["bevyAmbientLight"];
            p["configured"] = json!(true);
            for (field, input, checked) in &fields {
                p[field.0] = if let Some(checked) = checked {
                    json!(checked.0)
                } else {
                    let value = input.ok_or("Missing field")?.value().to_string();
                    if field.0 == "brightness" {
                        let n = value.parse::<f32>().map_err(|_| "Invalid brightness")?;
                        if !n.is_finite() || !(0. ..=100000.).contains(&n) {
                            return Err("Brightness must be between 0 and 100000".into());
                        }
                        json!(n)
                    } else {
                        if field.0 == "color" {
                            Srgba::hex(&value).map_err(|_| "Enter a hexadecimal color")?;
                        }
                        if value.trim().is_empty() {
                            return Err("Name cannot be empty".into());
                        }
                        json!(value)
                    }
                };
            }
            doc.replace_text(
                serde_json::to_string_pretty(&source).map_err(|e| e.to_string())? + "\n",
            );
            changes.write(crate::application::ViewChange::BufferChanged(apply.1));
            commands.entity(apply.0).try_despawn();
            Ok(())
        })();
        session.status =
            result.map_or_else(|e| e, |()| "Ambient light updated — Ctrl+S to save".into());
    }
}
pub(super) fn light(p: &Value) -> AmbientLight {
    AmbientLight {
        color: p["color"]
            .as_str()
            .and_then(|s| Srgba::hex(s).ok())
            .map(Color::from)
            .unwrap_or(Color::WHITE),
        brightness: if p["enabled"].as_bool().unwrap_or(false) {
            p["brightness"].as_f64().unwrap_or(220.) as f32
        } else {
            0.
        },
        affects_lightmapped_meshes: p["affectsLightmappedMeshes"].as_bool().unwrap_or(true),
    }
}
