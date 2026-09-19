//! Retained Effekseer document with asynchronous, immutable preparation and import.
use super::components::EditorHost;
use crate::application::{Command, CommandQueue, Session, ViewChange};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::{DocumentRevision, effect_import as schema};
use scenemax_ide_services::imports::{self, effect::Prepared};
use scenemax_ide_ui::{button, label, property, theme::*};
use serde_json::{Value, json};
use std::sync::Arc;
pub(crate) mod preview;
mod view;
#[derive(Resource, Default)]
pub(crate) struct State {
    actions: Vec<(Entity, Action)>,
}
#[derive(Clone)]
enum Action {
    Browse,
    Load,
    Import,
    Play,
    Pause,
    Restart,
    Step,
    Properties,
    Home,
    Snapshot,
    Trigger(usize),
}
#[derive(Component)]
pub(crate) struct Field {
    host: Entity,
    key: &'static str,
    observed: String,
    invalid: bool,
}
#[derive(Component, Default)]
pub(crate) struct Import {
    revision: Option<DocumentRevision>,
    parts: Option<view::Parts>,
    prepared: Option<Arc<Prepared>>,
    task: Option<bevy::tasks::Task<Job>>,
    source: String,
    status: String,
    last_field: Option<Entity>,
    save_after_refresh: bool,
    seek_generation: u64,
    seek_frame: f32,
    seeking: bool,
    triggers: [u64; 4],
    playing: bool,
    frame: f32,
    generation: u64,
    step: bool,
    home: bool,
    snapshot: bool,
    world: Option<Entity>,
    camera: Option<Entity>,
    effect: Option<Entity>,
    target: Option<Handle<Image>>,
}
impl Import {
    fn restart(&mut self) {
        self.frame = 0.;
        self.generation += 1;
        self.seek_frame = 0.;
        self.seek_generation += 1;
        self.step = false;
    }
}
enum Job {
    Pick(std::io::Result<Option<std::path::PathBuf>>),
    Load(std::io::Result<Prepared>),
    Commit(std::io::Result<imports::Outcome>),
}
pub(crate) fn open(session: &mut Session, changes: &mut MessageWriter<ViewChange>) {
    let path = session
        .workspace
        .project()
        .root()
        .join(".scenemax-studio/imports/Import Effect.smeffectimport");
    if let Some(id) = session.workspace.find_document(&path) {
        session.workspace.select(id).ok();
        changes.write(ViewChange::ActiveChanged);
        return;
    }
    match imports::effect::open_draft(session.workspace.project().root()) {
        Ok(doc) => {
            if let Ok(id) = session.workspace.open_document(doc) {
                changes.write(ViewChange::DocumentOpened(id));
                changes.write(ViewChange::ActiveChanged);
            }
        }
        Err(e) => session.status = format!("Cannot open effect importer: {e}"),
    }
}
fn load(state: &mut Import, draft: &Value) {
    state.source = draft["source"].as_str().unwrap_or_default().into();
    let path = std::path::PathBuf::from(&state.source);
    state.prepared = None;
    state.playing = false;
    state.status = "Inspecting effect and collecting dependencies…".into();
    state.task = Some(
        bevy::tasks::IoTaskPool::get_or_init(Default::default)
            .spawn(async move { Job::Load(imports::effect::prepare(&path)) }),
    );
}
fn store(
    session: &mut Session,
    owner: &EditorHost,
    draft: &Value,
    continuing: bool,
    changes: &mut MessageWriter<ViewChange>,
) {
    if let Ok(doc) = session.workspace.document_mut(owner.0) {
        let text = serde_json::to_string_pretty(draft).unwrap_or_default();
        if continuing {
            doc.replace_text_continuing(text);
        } else {
            doc.replace_text(text);
        }
        changes.write(ViewChange::BufferChanged(owner.0));
    }
}
#[allow(clippy::type_complexity)] // ECS queries explicitly declare shared/disjoint access.
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Fields<'w, 's> {
    inputs: Query<
        'w,
        's,
        (
            Entity,
            &'static mut Field,
            Option<&'static mut EditableText>,
            Option<&'static mut property::Choice>,
            Option<&'static mut property::Checked>,
            Option<&'static Children>,
        ),
    >,
    texts: Query<'w, 's, &'static mut Text>,
    nodes: Query<'w, 's, &'static mut Node>,
    focus: Option<Res<'w, bevy::input_focus::InputFocus>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
    services: Res<'w, crate::application::EditorServices>,
    designers: Query<'w, 's, &'static mut super::designer::Designer>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut states: Query<(Entity, &EditorHost, &mut Import)>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<ViewChange>,
    mut actions: ResMut<State>,
    mut fields: Fields,
    mut queue: ResMut<CommandQueue>,
) {
    let actions = std::mem::take(&mut actions.actions);
    for (host, owner, mut state) in &mut states {
        let Ok(doc) = session.workspace.document(owner.0) else {
            continue;
        };
        if state.save_after_refresh && !fields.services.storage.is_pending() && queue.0.is_empty() {
            queue.0.push_back(Command::SavePath(doc.path().to_owned()));
            state.save_after_refresh = false;
        }
        let Ok(mut draft) = serde_json::from_str::<Value>(doc.text()) else {
            continue;
        };
        if state.parts.is_none() {
            state.parts = Some(view::build(&mut commands, host, &draft));
        }
        let external = state.revision != Some(doc.revision());
        let mut edits = Vec::new();
        for (entity, mut field, input, choice, checked, children) in &mut fields.inputs {
            if field.host != host {
                continue;
            }
            if external {
                let value = view::value(&draft, field.key);
                if let Some(mut input) = input
                    && input.value().to_string() != value
                {
                    input.editor_mut().set_text(&value);
                }
                if let Some(mut choice) = choice {
                    choice.0 = value.clone();
                    if let Some(children) = children {
                        for child in children.iter() {
                            if let Ok(mut text) = fields.texts.get_mut(child) {
                                text.0 = format!("{} ↓", view::caption(field.key, &value));
                            }
                        }
                    }
                }
                if let Some(mut check) = checked {
                    check.0 = draft[field.key].as_bool().unwrap_or(false);
                    if let Some(children) = children {
                        for child in children.iter() {
                            if let Ok(mut text) = fields.texts.get_mut(child)
                                && let Some((_, title)) = text.0.split_once(' ')
                            {
                                text.0 = format!("{} {title}", if check.0 { "☑" } else { "☐" });
                            }
                        }
                    }
                }
                field.observed = value;
                field.invalid = false;
                continue;
            }
            let value = if let Some(input) = input {
                input.value().to_string()
            } else if let Some(choice) = choice {
                choice.0.clone()
            } else if let Some(check) = checked {
                check.0.to_string()
            } else {
                continue;
            };
            if value == field.observed {
                continue;
            }
            field.observed = value.clone();
            let parsed = if draft[field.key].is_string() {
                Ok(json!(value))
            } else {
                serde_json::from_str(&value).map_err(|e| e.to_string())
            };
            let mut next = draft.clone();
            let result = parsed.and_then(|v| {
                next[field.key] = v;
                schema::validate(&next)
            });
            match result {
                Ok(()) => {
                    if field.key == "seek" {
                        state.generation += 1;
                        state.frame = next["seek"].as_f64().unwrap_or(0.) as f32;
                        state.seek_frame = state.frame;
                        state.seek_generation += 1;
                        state.seeking = true;
                        state.playing = false;
                    }
                    if field.key == "seed" {
                        state.restart();
                        state.seek_frame = 0.;
                        state.seek_generation += 1;
                    }
                    draft = next;
                    field.invalid = false;
                    edits.push(entity);
                }
                Err(e) => {
                    field.invalid = true;
                    state.status = e;
                }
            }
        }
        if !edits.is_empty() {
            let focus = fields.focus.as_ref().and_then(|f| f.get());
            let continuing = edits.len() == 1
                && state.last_field == Some(edits[0])
                && (focus == Some(edits[0]) || fields.mouse.pressed(MouseButton::Left))
                && !fields.mouse.just_pressed(MouseButton::Left);
            store(&mut session, owner, &draft, continuing, &mut changes);
            state.last_field = edits.first().copied();
        }
        state.revision = session
            .workspace
            .document(owner.0)
            .ok()
            .map(|d| d.revision());

        if let Some(task) = &mut state.task
            && let Some(result) = bevy::tasks::block_on(bevy::tasks::poll_once(task))
        {
            state.task = None;
            match result {
                Job::Pick(Ok(Some(path))) => {
                    draft["source"] = json!(path.to_string_lossy());
                    draft["name"] = json!(
                        path.file_stem()
                            .unwrap_or_default()
                            .to_string_lossy()
                            .chars()
                            .map(|c| if c.is_ascii_alphanumeric() || c == '-' {
                                c
                            } else {
                                '_'
                            })
                            .collect::<String>()
                    );
                    store(&mut session, owner, &draft, false, &mut changes);
                    load(&mut state, &draft);
                }
                Job::Pick(Ok(None)) => {}
                Job::Pick(Err(e)) | Job::Load(Err(e)) | Job::Commit(Err(e)) => {
                    state.status = e.to_string()
                }
                Job::Load(Ok(p)) => {
                    state.status = if p.complete() {
                        format!(
                            "Ready · {} dependencies · {:.1} MiB",
                            p.dependencies.len(),
                            p.bytes as f64 / 1048576.
                        )
                    } else {
                        "Missing dependencies — see package inspector".into()
                    };
                    if let Some(parts) = state.parts {
                        view::dependencies(&mut commands, parts.dependencies, &p);
                    }
                    state.playing = p.complete();
                    state.restart();
                    state.prepared = Some(Arc::new(p));
                }
                Job::Commit(Ok(out)) => {
                    state.status = format!(
                        "Imported {}",
                        out.document
                            .file_name()
                            .unwrap_or_default()
                            .to_string_lossy()
                    );
                    session.status = state.status.clone();
                    for mut designer in &mut fields.designers {
                        designer.reload_assets();
                    }
                    changes.write(ViewChange::ProjectIndexInvalidated);
                    queue.0.push_back(Command::Refresh);
                    state.save_after_refresh = true;
                }
            }
        }
        for (_, action) in actions.iter().filter(|(h, _)| *h == host) {
            if matches!(action, Action::Load | Action::Import)
                && fields
                    .inputs
                    .iter()
                    .any(|(_, f, _, _, _, _)| f.host == host && f.invalid)
            {
                state.status = "Correct the invalid field first".into();
                continue;
            }
            match action {
                Action::Browse if state.task.is_none() => {
                    state.task = Some(
                        bevy::tasks::IoTaskPool::get_or_init(Default::default)
                            .spawn(async { Job::Pick(imports::pick_file(imports::Kind::Effect)) }),
                    )
                }
                Action::Load if state.task.is_none() => load(&mut state, &draft),
                Action::Import if state.task.is_none() => {
                    if let Some(p) = state.prepared.clone() {
                        if draft["source"].as_str() != Some(&state.source) {
                            state.status = "Reload the changed source first".into();
                            continue;
                        }
                        let root = session.workspace.project().root().to_owned();
                        let draft = draft.clone();
                        state.status = "Importing effect package…".into();
                        state.task =
                            Some(
                                bevy::tasks::IoTaskPool::get_or_init(Default::default).spawn(
                                    async move {
                                        Job::Commit(imports::effect::commit(&root, &p, &draft))
                                    },
                                ),
                            );
                    } else {
                        state.status = "Choose and load an effect first".into();
                    }
                }
                Action::Play => {
                    if state.frame >= draft["duration"].as_f64().unwrap_or(300.) as f32 {
                        state.restart();
                    }
                    state.playing = true;
                }
                Action::Pause => state.playing = false,
                Action::Restart => {
                    state.restart();
                }
                Action::Step => {
                    state.playing = false;
                    state.step = true;
                }
                Action::Home => state.home = true,
                Action::Snapshot => state.snapshot = true,
                Action::Trigger(i) => state.triggers[*i] += 1,
                Action::Properties => {
                    if let Some(parts) = state.parts
                        && let Ok(mut n) = fields.nodes.get_mut(parts.props)
                    {
                        n.display = if n.display == Display::None {
                            Display::Flex
                        } else {
                            Display::None
                        };
                    }
                }
                _ => {}
            }
        }
        if let Some(parts) = state.parts
            && let Ok(mut t) = fields.texts.get_mut(parts.status)
            && t.0 != state.status
        {
            t.0 = state.status.clone();
        }
    }
}
pub(crate) fn smoke(
    mut tick: Local<u32>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<ViewChange>,
    states: Query<Entity, With<Import>>,
    mut inputs: Query<(&Field, &mut EditableText)>,
    mut actions: ResMut<State>,
    mut queue: ResMut<CommandQueue>,
) {
    *tick += 1;
    if *tick == 30 {
        open(&mut session, &mut changes);
    }
    if *tick == 40 {
        if let Some(id) = session.workspace.active_id()
            && let Ok(doc) = session.workspace.document_mut(id)
        {
            let mut v = schema::draft();
            v["source"] = json!(std::env::var("SCENEMAX_SMOKE_EFFECT").unwrap_or_default());
            v["name"] = json!("effect_fixture");
            doc.replace_text(serde_json::to_string_pretty(&v).unwrap_or_default());
            changes.write(ViewChange::BufferChanged(id));
        }
        for h in &states {
            actions.actions.push((h, Action::Load));
        }
    }
    if *tick == 200
        && let Ok(frame) = std::env::var("SCENEMAX_SMOKE_EFFECT_FRAME")
    {
        for (field, mut input) in &mut inputs {
            if field.key == "seek" {
                input.editor_mut().set_text(&frame);
            }
        }
    }
    if *tick == 400 && std::env::var_os("SCENEMAX_SMOKE_EFFECT_SNAPSHOT").is_some() {
        for h in &states {
            actions.actions.push((h, Action::Snapshot));
        }
    }
    if *tick == 350 && std::env::var_os("SCENEMAX_SMOKE_EFFECT_IMPORT").is_some() {
        for h in &states {
            actions.actions.push((h, Action::Import));
        }
    }
    if *tick == 490 && std::env::var_os("SCENEMAX_SMOKE_EFFECT_CLOSE").is_some() {
        queue.0.push_back(Command::CloseTab);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[test]
    fn editing_effect_properties_retains_widgets_and_commits_valid_values() {
        let (mut app, _dir, _) = crate::tests::app();
        app.world_mut()
            .run_system_once(|mut s: ResMut<Session>, mut c: MessageWriter<ViewChange>| {
                open(&mut s, &mut c)
            })
            .unwrap();
        app.update();
        app.update();
        let w = app.world_mut();
        let entity = w
            .query::<(Entity, &Field)>()
            .iter(w)
            .find(|(_, f)| f.key == "scale")
            .unwrap()
            .0;
        for value in ["2.5", "3.0"] {
            app.world_mut()
                .get_mut::<EditableText>(entity)
                .unwrap()
                .editor_mut()
                .set_text(value);
            app.update();
            assert!(app.world().get::<Field>(entity).is_some());
            let s = app.world().resource::<Session>();
            let d = s
                .workspace
                .document(s.workspace.active_id().unwrap())
                .unwrap();
            let v: Value = serde_json::from_str(d.text()).unwrap();
            assert_eq!(v["scale"].as_f64().unwrap(), value.parse::<f64>().unwrap());
        }
        app.world_mut()
            .get_mut::<EditableText>(entity)
            .unwrap()
            .editor_mut()
            .set_text("-1");
        app.update();
        assert!(app.world().get::<Field>(entity).unwrap().invalid);
    }
}
