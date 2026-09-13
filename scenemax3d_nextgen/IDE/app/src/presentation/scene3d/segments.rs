//! Cinematic segment authoring using the Java rig serialization.
use super::*;
use serde_json::json;

#[derive(Component, Clone, Copy)]
pub(crate) enum Action {
    AddRange,
    MoveSelected(i32),
    RemoveSelected,
    Remove(usize),
    Earlier(usize),
    Later(usize),
}

pub(crate) fn update(
    actions: Query<(&Interaction, &Action), Changed<Interaction>>,
    state: Res<SceneState>,
    selection: Option<Res<super::rig::Selection>>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<crate::application::ViewChange>,
) {
    for (interaction, action) in &actions {
        if *interaction != Interaction::Pressed {
            continue;
        }
        let result = (|| -> Result<(), String> {
            let (id, revision) = state.current.ok_or("No scene")?;
            let scene = state.scene.as_ref().ok_or("Scene is loading")?;
            let index = if matches!(action, Action::AddRange) {
                cinematic::rig_parent(scene, state.selected)
                    .ok_or("The selected track does not belong to a cinematic rig")?
            } else {
                state.selected
            };
            let rig = &scene.entities[index];
            let mut segments = rig.properties["cinematicSegments"]
                .as_array()
                .cloned()
                .unwrap_or_default();
            let action = match *action {
                Action::MoveSelected(direction) => {
                    let i = selection
                        .as_ref()
                        .filter(|s| s.rig == rig.id)
                        .and_then(|s| s.row)
                        .ok_or("Select a table row")?;
                    if direction < 0 {
                        Action::Earlier(i)
                    } else {
                        Action::Later(i)
                    }
                }
                Action::RemoveSelected => Action::Remove(
                    selection
                        .as_ref()
                        .filter(|s| s.rig == rig.id)
                        .and_then(|s| s.row)
                        .ok_or("Select a table row")?,
                ),
                action => action,
            };
            match &action {
                Action::MoveSelected(_) | Action::RemoveSelected => unreachable!(),
                Action::AddRange => {
                    let track = &scene.entities[state.selected];
                    let data = &track.properties["cinematicTrackData"];
                    let start = data["selectedStartAnchor"]
                        .as_i64()
                        .filter(|n| *n >= 0)
                        .ok_or("Set and apply the track start anchor first")?;
                    let end = data["selectedEndAnchor"]
                        .as_i64()
                        .filter(|n| *n >= 0)
                        .ok_or("Set and apply the track end anchor first")?;
                    segments.push(json!({"trackId":track.id,"trackName":track.name,"startAnchor":start,"endAnchor":end,"speed":data["previewSpeed"].as_f64().unwrap_or(30.)}));
                }
                Action::Remove(i) => {
                    if *i >= segments.len() {
                        return Err("Segment no longer exists".into());
                    }
                    segments.remove(*i);
                }
                Action::Earlier(i) => {
                    if *i > 0 && *i < segments.len() {
                        segments.swap(*i, *i - 1);
                    }
                }
                Action::Later(i) => {
                    if *i + 1 < segments.len() {
                        segments.swap(*i, *i + 1);
                    }
                }
            }
            let doc = session
                .workspace
                .document_mut(id)
                .map_err(|e| e.to_string())?;
            if doc.revision() != revision {
                return Err("Scene changed; retry after reload".into());
            }
            let source = scenemax_ide_core::scene3d::patch(
                doc.text(),
                &rig.pointer,
                &[("cinematicSegments".into(), json!(segments))],
            )?;
            doc.replace_text(source);
            changes.write(crate::application::ViewChange::BufferChanged(id));
            Ok(())
        })();
        session.status = result.map_or_else(
            |e| e,
            |()| "Cinematic segments updated · Ctrl+S to save · Ctrl+Z to undo".into(),
        );
    }
}
