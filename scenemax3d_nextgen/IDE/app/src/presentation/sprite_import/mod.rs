//! Retained sprite import document: form transactions and worker-owned import jobs.
use super::components::EditorHost;
use crate::application::{Command, CommandQueue, Session, ViewChange};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::{DocumentRevision, sprite_import as schema};
use scenemax_ide_services::imports::{self, sprite::Prepared};
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
    Stop,
    Step(i32),
    Select(u32),
    AllFrames,
    Properties,
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
    image: Option<Handle<Image>>,
    layout: Option<schema::Layout>,
    drawn: Option<(schema::Layout, bool)>,
    source: String,
    status: String,
    frame: u32,
    playing: bool,
    elapsed: f64,
    last_field: Option<Entity>,
    filter: String,
    save_after_refresh: bool,
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
        .join(".scenemax-studio/imports/Import Sprite.smspriteimport");
    if let Some(id) = session.workspace.find_document(&path) {
        session.workspace.select(id).ok();
        changes.write(ViewChange::ActiveChanged);
        return;
    }
    match imports::sprite::open_draft(session.workspace.project().root()) {
        Ok(doc) => {
            if let Ok(id) = session.workspace.open_document(doc) {
                changes.write(ViewChange::DocumentOpened(id));
                changes.write(ViewChange::ActiveChanged);
            }
        }
        Err(e) => session.status = format!("Cannot open sprite importer: {e}"),
    }
}
fn load(state: &mut Import, draft: &Value) {
    state.source = draft["source"].as_str().unwrap_or_default().into();
    let path = std::path::PathBuf::from(&state.source);
    state.prepared = None;
    state.image = None;
    state.layout = None;
    state.drawn = None;
    state.playing = false;
    state.status = "Decoding sprite sheet…".into();
    state.task = Some(
        bevy::tasks::IoTaskPool::get_or_init(Default::default)
            .spawn(async move { Job::Load(imports::sprite::prepare(&path)) }),
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
                    if field.key == "fps" {
                        state.elapsed *= draft["fps"].as_f64().unwrap_or(12.)
                            / next["fps"].as_f64().unwrap_or(12.);
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
                            .map(|c| if c.is_ascii_alphanumeric() || c == '_' || c == '-' {
                                c
                            } else {
                                '_'
                            })
                            .collect::<String>()
                    );
                    store(&mut session, owner, &draft, false, &mut changes);
                    load(&mut state, &draft);
                }
                Job::Pick(Ok(None)) => state.status = "File selection cancelled".into(),
                Job::Pick(Err(e)) | Job::Load(Err(e)) | Job::Commit(Err(e)) => {
                    state.status = e.to_string()
                }
                Job::Load(Ok(p)) => {
                    state.status = format!(
                        "{} × {} pixels · RGBA preview · source unchanged",
                        p.width, p.height
                    );
                    state.prepared = Some(Arc::new(p));
                    state.frame = 0;
                    state.elapsed = 0.;
                }
                Job::Commit(Ok(outcome)) => {
                    state.status = format!("Imported {}", outcome.asset.display());
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
        if let Some(p) = &state.prepared {
            match schema::Layout::new(&draft, [p.width, p.height]) {
                Ok(layout) => {
                    if state.layout != Some(layout) {
                        state.status = format!(
                            "{} frames · {} × {} pixels per frame · ready to preview or import",
                            layout.count(),
                            layout.width,
                            layout.height
                        );
                        let old_count = state.layout.map_or(1, |l| l.count());
                        if draft["last"].as_f64() == Some((old_count - 1) as f64) {
                            draft["last"] = json!(layout.count() - 1);
                            store(&mut session, owner, &draft, !edits.is_empty(), &mut changes);
                            for (_, mut field, input, _, _, _) in &mut fields.inputs {
                                if field.host == host && field.key == "last" {
                                    let value = view::value(&draft, "last");
                                    if let Some(mut input) = input {
                                        input.editor_mut().set_text(&value);
                                    }
                                    field.observed = value;
                                    field.invalid = false;
                                }
                            }
                            state.revision = session
                                .workspace
                                .document(owner.0)
                                .ok()
                                .map(|d| d.revision());
                        }
                        state.frame = state.frame.min(layout.count() - 1);
                        state.elapsed = 0.;
                        state.layout = Some(layout);
                    }
                }
                Err(e) => {
                    state.layout = None;
                    state.playing = false;
                    state.status = e;
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
                state.status = "Correct the invalid field before loading or importing".into();
                continue;
            }
            match action {
                Action::Browse if state.task.is_none() => {
                    state.task = Some(
                        bevy::tasks::IoTaskPool::get_or_init(Default::default)
                            .spawn(async { Job::Pick(imports::pick_file(imports::Kind::Sprite)) }),
                    )
                }
                Action::Load if state.task.is_none() => load(&mut state, &draft),
                Action::Import if state.task.is_none() => {
                    if let Some(p) = state.prepared.clone() {
                        if draft["source"].as_str() != Some(&state.source) {
                            state.status = "Load the changed source before importing".into();
                            continue;
                        }
                        let root = session.workspace.project().root().to_owned();
                        let draft = draft.clone();
                        state.status = "Packing frames and importing sprite…".into();
                        state.task =
                            Some(
                                bevy::tasks::IoTaskPool::get_or_init(Default::default).spawn(
                                    async move {
                                        Job::Commit(imports::sprite::commit(&root, &p, &draft))
                                    },
                                ),
                            );
                    } else {
                        state.status = "Choose and load a sprite sheet first".into();
                    }
                }
                Action::Play => {
                    if state.layout.is_some() {
                        let first = schema::integer(&draft, "first").unwrap_or(0);
                        let last = schema::integer(&draft, "last").unwrap_or(first);
                        if draft["mode"].as_str() == Some("once")
                            && state.elapsed * draft["fps"].as_f64().unwrap_or(12.)
                                >= last.saturating_sub(first) as f64 + 1.
                        {
                            state.elapsed = 0.;
                        }
                        state.playing = true;
                    }
                }
                Action::Pause => state.playing = false,
                Action::Stop => {
                    state.playing = false;
                    state.elapsed = 0.;
                    state.frame = schema::integer(&draft, "first").unwrap_or(0);
                }
                Action::Select(frame) => {
                    state.frame = *frame;
                    state.playing = false;
                    state.elapsed =
                        frame.saturating_sub(schema::integer(&draft, "first").unwrap_or(0)) as f64
                            / draft["fps"].as_f64().unwrap_or(12.);
                }
                Action::Step(delta) => {
                    if let Some(layout) = state.layout {
                        state.playing = false;
                        state.frame = (state.frame as i64 + *delta as i64)
                            .rem_euclid(layout.count() as i64)
                            as u32;
                        state.elapsed = 0.;
                    }
                }
                Action::AllFrames => {
                    if let Some(layout) = state.layout {
                        draft["first"] = json!(0);
                        draft["last"] = json!(layout.count() - 1);
                        store(&mut session, owner, &draft, false, &mut changes);
                        state.elapsed = 0.;
                    }
                }
                Action::Properties => {
                    if let Some(parts) = state.parts
                        && let Ok(mut node) = fields.nodes.get_mut(parts.props)
                    {
                        node.display = if node.display == Display::None {
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
            && let Ok(mut text) = fields.texts.get_mut(parts.status)
            && text.0 != state.status
        {
            text.0 = state.status.clone();
        }
    }
}

/// Exercise the actual document and renderer against an explicit smoke fixture.
pub(crate) fn smoke(
    mut tick: Local<u32>,
    services: Res<crate::application::EditorServices>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<ViewChange>,
    states: Query<Entity, With<Import>>,
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
            let mut v = schema::draft();
            v["source"] = json!(std::env::var("SCENEMAX_SMOKE_SPRITE").unwrap_or_default());
            v["cols"] = json!(4);
            v["rows"] = json!(2);
            v["name"] = json!("preview_fixture");
            doc.replace_text(serde_json::to_string_pretty(&v).unwrap_or_default());
            changes.write(ViewChange::BufferChanged(id));
        }
        for host in &states {
            actions.actions.push((host, Action::Load));
        }
    }
    if *tick == 400 && std::env::var_os("SCENEMAX_SMOKE_SPRITE_IMPORT").is_some() {
        for host in &states {
            actions.actions.push((host, Action::Import));
        }
    }
    if *tick == 200 {
        for host in &states {
            actions.actions.push((host, Action::Play));
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[test]
    fn native_fields_keep_focus_and_preview_geometry_updates_without_replacing_controls() {
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
        let (host, input) = {
            let input = world
                .query::<(Entity, &Field)>()
                .iter(world)
                .find(|(_, f)| f.key == "cols")
                .unwrap()
                .0;
            let host = world
                .query_filtered::<Entity, With<Import>>()
                .single(world)
                .unwrap();
            (host, input)
        };
        world.get_mut::<Import>(host).unwrap().prepared = Some(Arc::new(Prepared {
            width: 64,
            height: 32,
            rgba: vec![255; 64 * 32 * 4],
            source: "fixture.png".into(),
        }));
        app.update();
        for n in [2, 4, 8] {
            app.world_mut()
                .get_mut::<EditableText>(input)
                .unwrap()
                .editor_mut()
                .set_text(&n.to_string());
            app.update();
            assert_eq!(
                app.world()
                    .get::<Import>(host)
                    .unwrap()
                    .layout
                    .unwrap()
                    .cols,
                n
            );
            assert!(app.world().get::<Field>(input).is_some());
        }
    }
}
