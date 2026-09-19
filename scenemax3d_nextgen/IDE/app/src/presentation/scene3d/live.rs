//! Bidirectional property binding without replacing the viewport or focused controls.
use super::*;
use bevy::{input_focus::InputFocus, text::EditableText};
use scenemax_ide_ui::property::{Checked, Choice};
use serde_json::{Value, json};
use std::collections::BTreeMap;
#[derive(Default)]
pub(crate) struct Gesture {
    last: Option<(
        DocumentId,
        DocumentRevision,
        usize,
        Option<Entity>,
        u64,
        bool,
    )>,
    held: bool,
}
type Fields<'w, 's> = Query<
    'w,
    's,
    (
        &'static mut inspector::Property,
        Option<&'static mut EditableText>,
        Option<&'static Checked>,
        Option<&'static Choice>,
    ),
>;
type Objects<'w, 's> = Query<
    'w,
    's,
    (
        Entity,
        &'static mut gizmo::SceneObject,
        &'static mut Transform,
        Option<&'static mut Mesh3d>,
        Option<&'static mut PointLight>,
    ),
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct View<'w, 's> {
    fields: Fields<'w, 's>,
    proportional: Query<'w, 's, &'static Checked, With<inspector::Proportional>>,
    objects: Objects<'w, 's>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    focus: Option<Res<'w, InputFocus>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<SceneState>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<crate::application::ViewChange>,
    mut view: View,
    mut gesture: Local<Gesture>,
) {
    let Some((id, revision)) = state.current else {
        return;
    };
    let Some(scene) = state.scene.as_ref() else {
        return;
    };
    let Some(entry) = scene.entities.get(state.selected) else {
        return;
    };
    let Ok(doc) = session.workspace.document(id) else {
        return;
    };
    if doc.revision() != revision {
        gesture.last = None;
        return;
    }
    let mut values = BTreeMap::new();
    for (p, text, checked, choice) in &view.fields {
        let value = if let Some(text) = text {
            text.value().to_string()
        } else if let Some(checked) = checked {
            checked.0.to_string()
        } else {
            choice.map(|v| v.0.clone()).unwrap_or_default()
        };
        if value != p.1 {
            values.insert(p.0.clone(), value);
        }
    }
    let transform = view
        .objects
        .iter()
        .find(|(_, o, _, _, _)| o.0 == state.selected)
        .map(|(_, _, t, _, _)| *t);
    let from_gizmo = values.is_empty()
        && transform.is_some_and(|t| {
            !t.translation
                .abs_diff_eq(Vec3::from_array(entry.position), 0.00001)
                || !t.scale.abs_diff_eq(Vec3::from_array(entry.scale), 0.00001)
                || !t
                    .rotation
                    .abs_diff_eq(Quat::from_array(entry.rotation), 0.00001)
        });
    let focus = view.focus.as_ref().and_then(|f| f.get());
    let held = view.mouse.pressed(MouseButton::Left);
    if !held && focus.is_none() && !gesture.held {
        gesture.last = None;
    }
    gesture.held = held;
    if values.is_empty() && !from_gizmo {
        return;
    }
    let patch = if from_gizmo {
        let Some(t) = transform else {
            return;
        };
        vec![
            ("position".into(), json!(t.translation.to_array())),
            ("rotation".into(), json!(t.rotation.to_array())),
            ("scale".into(), json!(t.scale.to_array())),
        ]
    } else {
        match super::properties::transaction(
            entry,
            scene,
            &values,
            view.proportional.iter().any(|p| p.0),
        ) {
            Ok(p) => p,
            Err(error) => {
                session.status = error;
                return;
            }
        }
    };
    if patch.is_empty() {
        return;
    }
    let source = match scenemax_ide_core::scene3d::patch(doc.text(), &entry.pointer, &patch) {
        Ok(source) => source,
        Err(error) => {
            session.status = error;
            return;
        }
    };
    let parsed: Value = match serde_json::from_str(&source) {
        Ok(v) => v,
        Err(_) => return,
    };
    let saved = doc.saved_version();
    let continue_gesture = gesture.last
        == Some((id, revision, state.selected, focus, saved, from_gizmo))
        && !view.mouse.just_pressed(MouseButton::Left);
    let Ok(doc) = session.workspace.document_mut(id) else {
        return;
    };
    if continue_gesture {
        doc.replace_text_continuing(source);
    } else {
        doc.replace_text(source);
    }
    let stamp = (id, doc.revision());
    gesture.last = Some((id, stamp.1, state.selected, focus, saved, from_gizmo));
    changes.write(crate::application::ViewChange::BufferChanged(id));
    // Asset replacement stays asynchronous on the existing storage worker.
    if patch
        .iter()
        .any(|(key, _)| key == "resourcePath" || key == "shader" || key == "material")
    {
        return;
    }
    state.current = Some(stamp);
    state.completed = Some(stamp);
    let selected = state.selected;
    let Some(scene) = state.scene.as_mut() else {
        return;
    };
    for (index, e) in scene.entities.iter_mut().enumerate() {
        let Some(p) = parsed.pointer(&e.pointer) else {
            continue;
        };
        if *p == e.properties {
            continue;
        }
        e.properties = p.clone();
        e.name = p["name"].as_str().unwrap_or(&e.name).to_owned();
        e.hidden = p["hidden"].as_bool().unwrap_or(false);
        e.position = array(p, "position", e.position);
        e.rotation = array(p, "rotation", e.rotation);
        e.scale = array(p, "scale", e.scale);
        let n = |key: &str, default: f32| p[key].as_f64().unwrap_or(default as f64) as f32;
        e.size = match e.kind.as_str() {
            "BOX" => [
                n("sizeX", 0.5) * 2.,
                n("sizeY", 0.5) * 2.,
                n("sizeZ", 0.5) * 2.,
            ],
            "SPHERE" => [n("radius", 0.5) * 2.; 3],
            "QUAD" => [n("quadWidth", 1.), n("quadHeight", 1.), 0.01],
            _ => e.size,
        };
        if let Some((entity, mut object, mut t, mesh, light)) =
            view.objects.iter_mut().find(|(_, o, _, _, _)| o.0 == index)
        {
            let new = Transform {
                translation: Vec3::from_array(e.position),
                rotation: Quat::from_array(e.rotation).normalize(),
                scale: Vec3::from_array(e.scale),
            };
            if *t != new {
                *t = new;
            }
            object.1 = new;
            let shape = patch.iter().any(|(key, _)| {
                !matches!(
                    key.as_str(),
                    "name" | "position" | "rotation" | "scale" | "hidden"
                )
            });
            if shape
                && index == selected
                && let Some(meshes) = view.meshes.as_mut()
            {
                if let Some(mut mesh) = mesh {
                    let geometry = match e.kind.as_str() {
                        "SPHERE" => Some(Mesh::from(Sphere::new(e.size[0] / 2.))),
                        "BOX" | "QUAD" => {
                            Some(Mesh::from(Cuboid::from_size(Vec3::from_array(e.size))))
                        }
                        _ => super::primitives::mesh(&e.kind, p),
                    };
                    if let Some(geometry) = geometry {
                        mesh.0 = meshes.add(geometry);
                    }
                }
                if e.kind == "CINEMATIC_TRACK"
                    && let Some(materials) = view.materials.as_mut()
                {
                    commands.entity(entity).despawn_children();
                    super::cinematic::spawn(
                        &mut commands,
                        entity,
                        meshes,
                        materials,
                        &p["cinematicTrackData"],
                    );
                }
            }
            if let Some(mut light) = light {
                light.intensity = n("lightIntensity", 900.);
                light.range = n("lightRange", 12.);
            }
        }
    }
    let e = &scene.entities[selected];
    let (y, z, x) = Quat::from_array(e.rotation).to_euler(EulerRot::YZX);
    for (mut property, input, _, _) in &mut view.fields {
        if let Some(mut input) = input {
            let vector = if property.0.starts_with("position:") {
                Some(e.position)
            } else if property.0.starts_with("scale:") {
                Some(e.scale)
            } else if property.0.starts_with("angles:") {
                Some([x.to_degrees(), y.to_degrees(), z.to_degrees()])
            } else {
                None
            };
            if let Some(vector) = vector
                && let Some(i) = property
                    .0
                    .rsplit(':')
                    .next()
                    .and_then(|s| s.parse::<usize>().ok())
                    .filter(|i| *i < 3)
            {
                let current = input.value().to_string().parse::<f32>().ok();
                if current.is_none_or(|v| (v - vector[i]).abs() > 0.0001) {
                    input.editor_mut().set_text(&format!("{:.4}", vector[i]));
                }
                property.1 = input.value().to_string();
            } else if let Some(value) = values.get(&property.0) {
                property.1 = value.clone();
            }
        } else if let Some(value) = values.get(&property.0) {
            property.1 = value.clone();
        }
    }
    session.status = "Scene updated · Ctrl+S to save · Ctrl+Z to undo".into();
}
fn array<const N: usize>(p: &Value, key: &str, default: [f32; N]) -> [f32; N] {
    std::array::from_fn(|i| p[key][i].as_f64().map(|v| v as f32).unwrap_or(default[i]))
}
