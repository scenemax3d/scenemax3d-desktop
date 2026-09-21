//! Cinematic camera preview in the document viewport; never starts the runtime.
use super::*;
use serde_json::{Value, json};

#[derive(Component, Clone, Copy)]
pub(crate) enum Action {
    Play,
    Stop,
}
#[derive(Resource, Default)]
pub(crate) struct Playback {
    active: Option<Preview>,
}

/// Keep camera animation smooth while retaining the IDE's idle power-saving mode.
pub(crate) fn cadence(
    playback: Res<Playback>,
    material: Option<Res<super::super::material::State>>,
    rigs: Query<&scenemax_ik::Rig, With<gizmo::SceneObject>>,
    settings: Option<ResMut<bevy::winit::WinitSettings>>,
    mut previous: Local<Option<bevy::winit::UpdateMode>>,
) {
    let Some(mut settings) = settings else {
        return;
    };
    let playing = playback.active.is_some()
        || material.is_some_and(|m| m.animating())
        || rigs.iter().any(|rig| {
            rig.active
                && rig.definition["layers"]
                    .as_array()
                    .into_iter()
                    .flatten()
                    .any(|layer| layer["enabled"].as_bool().unwrap_or(false))
        });
    if playing && previous.is_none() {
        *previous = Some(settings.focused_mode);
        settings.focused_mode = bevy::winit::UpdateMode::Continuous;
    } else if !playing && let Some(mode) = previous.take() {
        settings.focused_mode = mode;
    }
}
struct Preview {
    camera: Entity,
    saved: Transform,
    stamp: (DocumentId, DocumentRevision),
    rig: Value,
    segments: Vec<Value>,
    segment: usize,
    elapsed: f32,
    total_weight: f32,
    is_rig: bool,
}

pub(crate) fn update(
    actions: Query<(&Interaction, &Action), Changed<Interaction>>,
    scene: Res<SceneState>,
    mut playback: ResMut<Playback>,
    mut cameras: Query<&mut Transform, With<Camera3d>>,
    objects: Query<(&gizmo::SceneObject, &GlobalTransform)>,
    time: Res<Time>,
    mut session: ResMut<Session>,
) {
    let stop = actions
        .iter()
        .any(|(i, a)| *i == Interaction::Pressed && matches!(a, Action::Stop));
    if let Some(p) = &playback.active
        && (stop || scene.current != Some(p.stamp) || scene.camera != Some(p.camera))
    {
        if let Ok(mut camera) = cameras.get_mut(p.camera) {
            *camera = p.saved;
        }
        playback.active = None;
    }
    if actions
        .iter()
        .any(|(i, a)| *i == Interaction::Pressed && matches!(a, Action::Play))
    {
        let result = (|| -> Result<Preview, String> {
            let data = scene.scene.as_ref().ok_or("Scene is loading")?;
            let e = data
                .entities
                .get(scene.selected)
                .ok_or("Select a rig or track")?;
            let is_rig = e.kind == "CINEMATIC_RIG";
            let segments = if is_rig {
                e.properties["cinematicSegments"]
                    .as_array()
                    .cloned()
                    .unwrap_or_default()
            } else {
                let t = &e.properties["cinematicTrackData"];
                vec![
                    json!({"trackId":e.id, "startAnchor":t["selectedStartAnchor"].as_i64().filter(|v| *v>=0).unwrap_or(0), "endAnchor":t["selectedEndAnchor"].as_i64().filter(|v| *v>=0).unwrap_or(359), "speed":t["previewSpeed"].as_f64().unwrap_or(30.)}),
                ]
            };
            if segments.is_empty() {
                return Err("This rig has no cinematic segments".into());
            }
            for segment in &segments {
                if !data.entities.iter().any(|e| {
                    e.kind == "CINEMATIC_TRACK" && e.id == segment["trackId"].as_str().unwrap_or("")
                }) {
                    return Err("A cinematic segment references a missing track".into());
                }
            }
            let camera = scene.camera.ok_or("No scene camera")?;
            let saved = playback
                .active
                .as_ref()
                .map(|p| p.saved)
                .unwrap_or(*cameras.get(camera).map_err(|_| "No scene camera")?);
            let total_weight = segments.iter().map(weight).sum();
            let rig = if is_rig {
                e.properties.clone()
            } else {
                cinematic::rig_parent(data, scene.selected)
                    .map(|i| data.entities[i].properties.clone())
                    .unwrap_or(Value::Null)
            };
            Ok(Preview {
                camera,
                saved,
                stamp: scene.current.ok_or("No document")?,
                rig,
                segments,
                segment: 0,
                elapsed: 0.,
                total_weight,
                is_rig,
            })
        })();
        match result {
            Ok(p) => {
                playback.active = Some(p);
                session.status =
                    "Cinematic preview · Stop preview restores the editor camera".into();
            }
            Err(e) => session.status = e,
        }
    }
    let (Some(p), Some(data)) = (&mut playback.active, &scene.scene) else {
        return;
    };
    p.elapsed += time.delta_secs();
    let segment = &p.segments[p.segment];
    let duration = if p.is_rig {
        p.rig["cinematicPreviewDuration"]
            .as_f64()
            .unwrap_or(5.)
            .max(0.1) as f32
            * weight(segment)
            / p.total_weight
    } else {
        weight(segment)
    };
    let progress = (p.elapsed / duration).clamp(0., 1.);
    let track_id = segment["trackId"].as_str().unwrap_or("");
    let Some(index) = data.entities.iter().position(|e| e.id == track_id) else {
        return;
    };
    let Some((_, gt)) = objects.iter().find(|(o, _)| o.0 == index) else {
        return;
    };
    let track = &data.entities[index].properties["cinematicTrackData"];
    let count = track["anchorCount"].as_u64().unwrap_or(360).clamp(8, 4096) as f32;
    let start = segment["startAnchor"].as_f64().unwrap_or(0.) as f32;
    let end = segment["endAnchor"].as_f64().unwrap_or(0.) as f32;
    let ease_in = if p.is_rig && p.segment == 0 {
        p.rig["cinematicEaseIn"].as_str().unwrap_or("linear")
    } else {
        "linear"
    };
    let ease_out = if p.is_rig && p.segment + 1 == p.segments.len() {
        p.rig["cinematicEaseOut"].as_str().unwrap_or("linear")
    } else {
        "linear"
    };
    let cursor = start + (end - start).rem_euclid(count) * eased(progress, ease_in, ease_out);
    let position = gt.transform_point(interpolated(track, cursor));
    let target = data
        .entities
        .iter()
        .position(|e| e.id == p.rig["cinematicTargetEntityId"].as_str().unwrap_or(""))
        .and_then(|i| objects.iter().find(|(o, _)| o.0 == i))
        .map(|(_, t)| {
            t.translation()
                + Vec3::from_array(std::array::from_fn(|i| {
                    p.rig["cinematicTargetOffset"][i]
                        .as_f64()
                        .unwrap_or(if i == 1 { 1.5 } else { 0. }) as f32
                }))
        })
        .unwrap_or_else(|| gt.transform_point(interpolated(track, cursor + 1.)));
    if let Ok(mut camera) = cameras.get_mut(p.camera) {
        *camera = Transform::from_translation(position).looking_at(target, Vec3::Y);
    }
    if progress >= 1. {
        p.segment += 1;
        p.elapsed = 0.;
        if p.segment >= p.segments.len() {
            if let Ok(mut camera) = cameras.get_mut(p.camera) {
                *camera = p.saved;
            }
            playback.active = None;
            session.status = "Cinematic preview finished".into();
        }
    }
}
fn weight(s: &Value) -> f32 {
    s["speed"].as_f64().unwrap_or(30.).max(0.1) as f32
}
fn interpolated(data: &Value, cursor: f32) -> Vec3 {
    cinematic::track_point(data, cursor.floor()).lerp(
        cinematic::track_point(data, cursor.floor() + 1.),
        cursor.fract(),
    )
}
fn eased(p: f32, start: &str, end: &str) -> f32 {
    if start == "linear" {
        ease(p, end)
    } else if end == "linear" {
        ease(p, start)
    } else if p < 0.5 {
        0.5 * ease(p * 2., start)
    } else {
        0.5 + 0.5 * ease((p - 0.5) * 2., end)
    }
}
fn ease(p: f32, name: &str) -> f32 {
    match name {
        "ease_in_quad" => p * p,
        "ease_out_quad" => 1. - (1. - p).powi(2),
        "ease_in_cubic" => p.powi(3),
        "ease_out_cubic" => 1. - (1. - p).powi(3),
        "ease_in_expo" => {
            if p <= 0. {
                0.
            } else {
                2_f32.powf(10. * (p - 1.))
            }
        }
        "ease_out_expo" => {
            if p >= 1. {
                1.
            } else {
                1. - 2_f32.powf(-10. * p)
            }
        }
        "ease_in_sine" => 1. - (p * std::f32::consts::FRAC_PI_2).cos(),
        "ease_out_sine" => (p * std::f32::consts::FRAC_PI_2).sin(),
        _ => p,
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn easing_matches_java_endpoints_and_combined_midpoint() {
        for name in [
            "linear",
            "ease_in_quad",
            "ease_out_quad",
            "ease_in_cubic",
            "ease_out_cubic",
            "ease_in_expo",
            "ease_out_expo",
            "ease_in_sine",
            "ease_out_sine",
        ] {
            assert!((ease(0., name)).abs() < 1e-6);
            assert!((ease(1., name) - 1.).abs() < 1e-6);
        }
        assert!((eased(0.5, "ease_in_quad", "ease_out_quad") - 0.5).abs() < 1e-6);
    }
}
