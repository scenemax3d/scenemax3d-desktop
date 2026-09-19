//! Document-owned model import designer, with private staging and a retained 3D preview.
use super::components::EditorHost;
use crate::application::{Command, CommandQueue, Session, ViewChange};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::{DocumentRevision, model_import as schema};
use scenemax_ide_services::imports::{self, model::Prepared};
use scenemax_ide_ui::{button, label, property, theme::*};
use serde_json::{Value, json};
pub(crate) mod playback;
pub(crate) mod render;
mod view;
#[derive(Resource, Default)]
pub(crate) struct State {
    actions: Vec<(Entity, Action)>,
    pub(crate) target: Option<(Entity, Entity, usize)>,
}
#[derive(Clone)]
enum Action {
    Browse,
    Load,
    Import,
    Fit,
    ResetPose,
    Play,
    Pause,
    Stop,
    Pan,
    Orbit,
}
#[derive(Component)]
pub(crate) struct Field {
    host: Entity,
    path: String,
    observed: String,
    invalid: bool,
}
#[derive(Component, Default)]
pub(crate) struct Import {
    revision: Option<DocumentRevision>,
    parts: Option<view::Parts>,
    prepared: Option<Prepared>,
    task: Option<bevy::tasks::Task<Job>>,
    world: Option<Entity>,
    camera: Option<Entity>,
    pose: Option<Entity>,
    visual: Option<Entity>,
    asset: Option<Handle<bevy::gltf::Gltf>>,
    graph: Option<Handle<AnimationGraph>>,
    clips: Vec<bevy::animation::graph::AnimationNodeIndex>,
    loaded: bool,
    prepared_valid: bool,
    framed: bool,
    pending_fit: bool,
    pan: bool,
    status: String,
    playing: bool,
    clip: Option<usize>,
    chosen: String,
    last_scrub: String,
    restart: bool,
    stop: bool,
    seek: Option<f32>,
    last_pose: Transform,
    last_field: Option<Entity>,
    camera_view: Option<super::scene3d::Orbit>,
    loaded_source: String,
    loaded_options: Value,
    gizmo_editing: bool,
}
enum Job {
    Pick(std::io::Result<Option<std::path::PathBuf>>),
    Prepare(std::io::Result<Prepared>),
    Commit(std::io::Result<imports::Outcome>),
}
pub(crate) fn open(session: &mut Session, changes: &mut MessageWriter<ViewChange>) {
    let path = session
        .workspace
        .project()
        .root()
        .join(".scenemax-studio/imports/Import 3D Model.smmodelimport");
    if let Some(id) = session.workspace.find_document(&path) {
        session.workspace.select(id).ok();
        changes.write(ViewChange::ActiveChanged);
        return;
    }
    match imports::model::open_draft(session.workspace.project().root()) {
        Ok(doc) => {
            if let Ok(id) = session.workspace.open_document(doc) {
                changes.write(ViewChange::DocumentOpened(id));
                changes.write(ViewChange::ActiveChanged);
            }
        }
        Err(error) => session.status = format!("Cannot open model importer: {error}"),
    }
}
fn control(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    title: &str,
    action: Action,
) -> Entity {
    let e = button(commands, parent, title, Name::new(title.to_owned()));
    commands
        .entity(e)
        .observe(move |mut e: On<Pointer<Click>>, mut state: ResMut<State>| {
            if e.button == PointerButton::Primary {
                e.propagate(false);
                state.actions.push((host, action.clone()));
            }
        });
    e
}
fn preparation_options(draft: &Value) -> Value {
    json!([
        draft["archiveEntry"],
        draft["optimization"],
        draft["isStatic"]
    ])
}
fn load(state: &mut Import, draft: &Value) {
    state.prepared_valid = false;
    state.loaded_source = draft["source"].as_str().unwrap_or_default().to_owned();
    state.loaded_options = preparation_options(draft);
    let source = std::path::PathBuf::from(&state.loaded_source);
    let draft = draft.clone();
    state.status = "Preparing model and dependencies…".into();
    state.task = Some(
        bevy::tasks::IoTaskPool::get_or_init(Default::default).spawn(async move {
            Job::Prepare(imports::model::prepare_with_options(&source, &draft))
        }),
    );
}
#[allow(clippy::type_complexity)] // Bevy query tuples encode disjoint ECS access.
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Fields<'w, 's> {
    inputs: Query<
        'w,
        's,
        (
            Entity,
            &'static mut Field,
            Option<&'static mut EditableText>,
            Option<&'static mut property::Checked>,
            Option<&'static mut property::Choice>,
        ),
    >,
    focus: Option<Res<'w, bevy::input_focus::InputFocus>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
    texts: Query<'w, 's, &'static mut Text>,
    children: Query<'w, 's, &'static Children>,
    designers: Query<'w, 's, &'static mut super::designer::Designer>,
    scene: Option<ResMut<'w, super::scene3d::SceneState>>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut states: Query<(Entity, &EditorHost, &mut Import)>,
    mut session: ResMut<Session>,
    mut changed: MessageWriter<ViewChange>,
    mut actions: ResMut<State>,
    mut fields: Fields,
    mut queue: ResMut<CommandQueue>,
) {
    let pending = std::mem::take(&mut actions.actions);
    for (host, owner, mut state) in &mut states {
        let Ok(doc) = session.workspace.document(owner.0) else {
            continue;
        };
        let Ok(mut draft) = serde_json::from_str::<Value>(doc.text()) else {
            state.status = "Invalid model import draft".into();
            continue;
        };
        if state.parts.is_none() {
            state.parts = Some(view::build(&mut commands, host, &draft));
        }
        let external = state.revision != Some(doc.revision());
        let mut edits = Vec::new();
        for (entity, mut field, text, checked, choice) in fields
            .inputs
            .iter_mut()
            .filter(|(_, f, _, _, _)| f.host == host)
        {
            if external {
                field.invalid = false;
                let v = draft.pointer(&field.path).cloned().unwrap_or(Value::Null);
                let value = if let Some(s) = v.as_str() {
                    s.to_owned()
                } else {
                    v.to_string()
                };
                if let Some(mut input) = text
                    && input.value().to_string() != value
                {
                    input.editor_mut().set_text(&value);
                }
                if let Some(mut check) = checked {
                    check.0 = v.as_bool().unwrap_or(false);
                    if let Ok(children) = fields.children.get(entity) {
                        for child in children.iter() {
                            if let Ok(mut text) = fields.texts.get_mut(child)
                                && let Some((_, title)) = text.0.split_once(' ')
                            {
                                text.0 = format!("{} {title}", if check.0 { "☑" } else { "☐" });
                            }
                        }
                    }
                }
                if let Some(mut choice) = choice {
                    choice.0 = value.clone();
                }
                field.observed = value;
                continue;
            }
            let value = if let Some(input) = text {
                text_value(&input)
            } else if let Some(check) = checked {
                check.0.to_string()
            } else if let Some(choice) = choice {
                choice.0.clone()
            } else {
                continue;
            };
            if value == field.observed {
                continue;
            }
            field.observed = value.clone();
            let old = draft.pointer(&field.path).cloned().unwrap_or(Value::Null);
            let parsed = if old.is_string() {
                Ok(json!(value))
            } else {
                serde_json::from_str::<Value>(&value).map_err(|e| e.to_string())
            };
            match parsed.and_then(|v| schema::edit(&draft, &field.path, v)) {
                Ok(next) => {
                    field.invalid = false;
                    draft = next;
                    edits.push(entity);
                }
                Err(e) => {
                    field.invalid = true;
                    state.status = e;
                }
            }
        }
        if !edits.is_empty() {
            if let Ok(doc) = session.workspace.document_mut(owner.0) {
                let focused = fields.focus.as_ref().and_then(|f| f.get());
                let group = edits.len() == 1
                    && state.last_field == Some(edits[0])
                    && (focused == Some(edits[0]) || fields.mouse.pressed(MouseButton::Left))
                    && !fields.mouse.just_pressed(MouseButton::Left);
                let source = serde_json::to_string_pretty(&draft).unwrap_or_default();
                if group {
                    doc.replace_text_continuing(source);
                } else {
                    doc.replace_text(source);
                }
                state.last_field = edits.first().copied();
                changed.write(ViewChange::BufferChanged(owner.0));
            }
            // Synchronize linked values now, without overwriting the next frame's keystrokes.
            for (entity, mut field, text, _, _) in &mut fields.inputs {
                if field.host != host || edits.contains(&entity) || field.invalid {
                    continue;
                }
                let value = draft.pointer(&field.path).cloned().unwrap_or(Value::Null);
                let value = value
                    .as_str()
                    .map(str::to_owned)
                    .unwrap_or_else(|| value.to_string());
                if value != field.observed {
                    if let Some(mut text) = text {
                        text.editor_mut().set_text(&value);
                    }
                    field.observed = value;
                }
            }
            state.revision = session
                .workspace
                .document(owner.0)
                .ok()
                .map(|d| d.revision());
        } else {
            state.revision = session
                .workspace
                .document(owner.0)
                .ok()
                .map(|d| d.revision());
        }
        if let Some(task) = &mut state.task
            && let Some(result) = bevy::tasks::block_on(bevy::tasks::poll_once(task))
        {
            state.task = None;
            match result {
                Job::Pick(Ok(Some(path))) => {
                    draft["source"] = json!(path.to_string_lossy());
                    draft["archiveEntry"] = json!("");
                    draft["name"] = json!(
                        path.file_stem()
                            .unwrap_or_default()
                            .to_string_lossy()
                            .chars()
                            .map(|c| if c.is_ascii_alphanumeric() || c == '_' || c == '-' {
                                c
                            } else {
                                '_'
                            })
                            .collect::<String>()
                    );
                    if let Ok(doc) = session.workspace.document_mut(owner.0) {
                        doc.replace_text(serde_json::to_string_pretty(&draft).unwrap_or_default());
                        changed.write(ViewChange::BufferChanged(owner.0));
                    }
                    load(&mut state, &draft);
                }
                Job::Pick(Ok(None)) => state.status = "File selection cancelled".into(),
                Job::Pick(Err(e)) | Job::Prepare(Err(e)) | Job::Commit(Err(e)) => {
                    state.status = e.to_string()
                }
                Job::Prepare(Ok(prepared)) => {
                    state.prepared_valid = true;
                    if let Some(world) = state.world.take() {
                        commands.entity(world).try_despawn();
                    }
                    state.asset = None;
                    state.clip = None;
                    state.chosen.clear();
                    state.playing = false;
                    state.restart = false;
                    state.seek = None;
                    state.last_scrub = "0".into();
                    state.camera = None;
                    state.pose = None;
                    state.loaded = false;
                    state.framed = false;
                    state.status = format!(
                        "{} meshes · {} materials · {} nodes · {} animations",
                        prepared.meshes,
                        prepared.materials,
                        prepared.nodes,
                        prepared.clips.len()
                    );
                    if let Some(parts) = state.parts {
                        view::clips(&mut commands, parts.clips, host, &prepared.clips);
                        view::entries(
                            &mut commands,
                            parts.entries,
                            host,
                            &draft,
                            &prepared.entries,
                        );
                    }
                    state.prepared = Some(prepared);
                }
                Job::Commit(Ok(result)) => {
                    state.status = format!("Imported {}", result.asset.display());
                    session.status = state.status.clone();
                    for mut designer in &mut fields.designers {
                        designer.reload_assets();
                    }
                    if let Some(scene) = &mut fields.scene {
                        scene.reload_assets();
                    }
                    queue.0.push_back(Command::Refresh);
                    changed.write(ViewChange::ProjectIndexInvalidated);
                    if session.workspace.active_id() == Some(owner.0) {
                        queue.0.push_back(Command::Save);
                    }
                }
            }
        }
        for (_, action) in pending.iter().filter(|(h, _)| *h == host) {
            if matches!(action, Action::Import | Action::Load)
                && fields
                    .inputs
                    .iter()
                    .any(|(_, f, _, _, _)| f.host == host && f.invalid)
            {
                state.status = "Correct the invalid field before loading or importing".into();
                continue;
            }
            match action {
                Action::Browse if state.task.is_none() => {
                    state.task = Some(
                        bevy::tasks::IoTaskPool::get_or_init(Default::default)
                            .spawn(async { Job::Pick(imports::pick_file(imports::Kind::Model)) }),
                    );
                }
                Action::Load if state.task.is_none() => load(&mut state, &draft),
                Action::Import if state.task.is_none() => {
                    if let Some(prepared) = &state.prepared {
                        if !state.loaded || !state.prepared_valid {
                            state.status =
                                "Wait for a successful model preview before importing".into();
                            continue;
                        }
                        if draft["source"]
                            .as_str()
                            .is_some_and(|p| p != state.loaded_source)
                            || preparation_options(&draft) != state.loaded_options
                        {
                            state.status = "Load / Reload to preview the changed source or optimization settings before importing".into();
                            continue;
                        }
                        let req = imports::Request {
                            kind: imports::Kind::Model,
                            source: prepared.path.clone(),
                            name: draft["name"].as_str().unwrap_or_default().into(),
                            rows: 1,
                            cols: 1,
                            frame_width: 1.,
                            frame_height: 0.,
                            clip: String::new(),
                            scale: 1.,
                            model: Some(draft.clone()),
            effect: None,
                        };
                        let root = session.workspace.project().root().to_owned();
                        state.task = Some(
                            bevy::tasks::IoTaskPool::get_or_init(Default::default)
                                .spawn(async move { Job::Commit(imports::import(&root, &req)) }),
                        );
                        state.status = "Importing model…".into();
                    } else {
                        state.status = "Choose and load a model first".into();
                    }
                }
                Action::Fit => state.pending_fit = true,
                Action::ResetPose => {
                    draft["preview"]["position"] = json!([0., 0., 0.]);
                    draft["preview"]["rotation"] = json!([0., 0., 0.]);
                    if let Ok(doc) = session.workspace.document_mut(owner.0) {
                        doc.replace_text(serde_json::to_string_pretty(&draft).unwrap_or_default());
                        changed.write(ViewChange::BufferChanged(owner.0));
                    }
                }
                Action::Play => {
                    state.playing = true;
                    state.restart = state.clip.is_none();
                }
                Action::Pause => state.playing = false,
                Action::Stop => {
                    state.playing = false;
                    state.stop = true;
                }
                Action::Pan => state.pan = true,
                Action::Orbit => state.pan = false,
                _ => {}
            }
        }
        if let Some(parts) = state.parts
            && let Ok(mut text) = fields.texts.get_mut(parts.status)
            && text.0 != state.status
        {
            text.0 = state.status.clone();
        }
    }
}
fn text_value(input: &EditableText) -> String {
    input.value().to_string()
}

/// Explicit smoke-run hook, registered only when a model fixture is requested.
pub(crate) fn smoke(
    mut tick: Local<u32>,
    services: Res<crate::application::EditorServices>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<ViewChange>,
    mut states: Query<(Entity, &mut Import)>,
    mut actions: ResMut<State>,
) {
    if services.storage.is_pending() || services.catalog_storage.is_pending() {
        return;
    }
    *tick += 1;
    if *tick == 30 {
        open(&mut session, &mut changes);
    }
    if *tick == 40 {
        if let Some(id) = session.workspace.active_id()
            && let Ok(doc) = session.workspace.document_mut(id)
        {
            let mut draft = schema::draft();
            draft["source"] = json!(std::env::var("SCENEMAX_SMOKE_MODEL").unwrap_or_default());
            draft["preview"]["capsule"] = json!(true);
            draft["optimization"]["enabled"] =
                json!(std::env::var_os("SCENEMAX_SMOKE_OPTIMIZE").is_some());
            doc.replace_text(serde_json::to_string_pretty(&draft).unwrap_or_default());
            changes.write(ViewChange::BufferChanged(id));
        }
        for (host, _) in &mut states {
            actions.actions.push((host, Action::Load));
        }
    }
    if *tick == 400 {
        for (host, _) in &mut states {
            actions.actions.push((host, Action::Play));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[test]
    fn native_fields_keep_retained_entities_and_accept_consecutive_edits() {
        let (mut app, _dir, _) = crate::tests::app();
        app.world_mut()
            .run_system_once(
                |mut session: ResMut<Session>, mut changes: MessageWriter<ViewChange>| {
                    open(&mut session, &mut changes)
                },
            )
            .unwrap();
        app.update();
        app.update();
        let world = app.world_mut();
        let input = world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, f)| f.path == "/scaleX")
            .unwrap()
            .0;
        let host = world
            .query_filtered::<Entity, With<Import>>()
            .single(world)
            .unwrap();
        for value in ["2", "2.5", "2.75"] {
            app.world_mut()
                .get_mut::<EditableText>(input)
                .unwrap()
                .editor_mut()
                .set_text(value);
            app.update();
            let world = app.world();
            let session = world.resource::<Session>();
            let doc = session
                .workspace
                .document(session.workspace.active_id().unwrap())
                .unwrap();
            let draft: Value = serde_json::from_str(doc.text()).unwrap();
            assert_eq!(draft["scaleX"].as_f64(), value.parse().ok());
            assert_eq!(draft["scaleY"].as_f64(), draft["scaleX"].as_f64());
            assert!(world.get::<Import>(host).is_some());
        }
        // Dropping the document host also cleans its detached 3D world.
        let orphan = app
            .world_mut()
            .spawn((render::WorldOwner(host), Transform::default()))
            .id();
        app.world_mut().entity_mut(host).despawn();
        app.update();
        assert!(app.world().get_entity(orphan).is_err());
    }
}
