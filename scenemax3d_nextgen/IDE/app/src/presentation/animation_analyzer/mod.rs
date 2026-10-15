//! Retained animation records editor; authoring lives in the model catalog document.
pub(crate) mod preview;
#[cfg(test)]
mod tests;
mod view;
use super::components::EditorHost;
use crate::application::{Command, CommandQueue, Session, material::Edit};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::{DocumentId, DocumentRevision};
use scenemax_ide_services::animation_analyzer::Source;
use scenemax_ide_ui::property::Choice;
use serde_json::{Value, json};

#[derive(Component)]
pub(crate) struct AnalyzerHost;
#[derive(Component)]
pub(crate) struct Field(String, String);
#[derive(Component)]
pub(crate) struct Control(&'static str, String);
#[derive(Component)]
struct RecordRow(usize);
#[derive(Component)]
pub(crate) enum Action {
    Save,
    Undo,
    Redo,
    Refresh,
    Add,
    Delete,
    Select(usize),
    UseFull,
    Full,
    Range,
    Pause,
    Stop,
    Fit,
    Reset,
    Mode(u8),
}
#[derive(Resource, Default)]
pub(crate) struct State {
    open: bool,
    opening: Option<(
        std::path::PathBuf,
        bevy::tasks::Task<Result<std::path::PathBuf, String>>,
    )>,
    current: Option<(DocumentId, DocumentRevision)>,
    host: Option<Entity>,
    parts: Option<view::Parts>,
    data: Value,
    model: usize,
    filter: String,
    selected: Option<usize>,
    task: Option<bevy::tasks::Task<Result<Source, String>>>,
    reload: bool,
    source: Option<std::sync::Arc<Source>>,
    redraw: bool,
    transport: bool,
    error: String,
    animation: usize,
    start: usize,
    end: usize,
    speed: f32,
    playing: bool,
    range: bool,
    restart: bool,
    stop: bool,
    seek: Option<f32>,
    time: f32,
    mode: u8,
    render: preview::Preview,
}
pub(crate) fn menu_item(c: &mut Commands, parent: Entity, title: &str) -> Entity {
    let row = scenemax_ide_ui::button(c, parent, title, Name::new("Model Animation Analyzer"));
    c.entity(row)
        .observe(|_: On<Pointer<Press>>, mut s: ResMut<State>| s.open = true);
    row
}
fn load(s: &mut State, path: &std::path::Path) {
    if s.task.is_some() {
        s.reload = true;
        s.source = None;
        s.playing = false;
        return;
    }
    let path = path.to_owned();
    let data = s.data.clone();
    let model = s.model;
    s.source = None;
    s.error = "Loading model animation timelines…".into();
    s.task = Some(bevy::tasks::IoTaskPool::get().spawn(async move {
        scenemax_ide_services::animation_analyzer::source(&path, &data, model)
    }));
    s.playing = false;
    s.selected = None;
    s.animation = 0;
}
type Fields<'w, 's> = Query<
    'w,
    's,
    (
        &'static mut Field,
        Option<&'static mut EditableText>,
        Option<&'static mut Choice>,
        Option<&'static Children>,
    ),
>;
type Controls<'w, 's> = Query<
    'w,
    's,
    (
        &'static mut Control,
        Option<&'static mut EditableText>,
        Option<&'static Choice>,
    ),
    Without<Field>,
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Ui<'w, 's> {
    fields: Fields<'w, 's>,
    controls: Controls<'w, 's>,
    buttons: Query<'w, 's, (&'static Interaction, &'static Action), Changed<Interaction>>,
    texts: Query<'w, 's, &'static mut Text>,
    rows: Query<'w, 's, (&'static RecordRow, &'static mut BackgroundColor)>,
}
pub(crate) fn update(
    mut c: Commands,
    mut session: ResMut<Session>,
    mut s: ResMut<State>,
    hosts: Query<(Entity, &EditorHost), With<AnalyzerHost>>,
    mut queue: ResMut<CommandQueue>,
    mut ui: Ui,
    mut changes: MessageWriter<crate::application::ViewChange>,
) {
    if s.open && s.opening.is_none() {
        s.open = false;
        let root = session.workspace.project().root().to_owned();
        let copy = root.clone();
        s.opening = Some((
            root,
            bevy::tasks::IoTaskPool::get()
                .spawn(async move { scenemax_ide_services::animation_analyzer::catalog(&copy) }),
        ));
    }
    if let Some((root, task)) = s.opening.as_mut()
        && let Some(result) = bevy::tasks::block_on(bevy::tasks::poll_once(task))
    {
        if root == session.workspace.project().root() {
            match result {
                Ok(path) => queue.0.push_back(Command::Open(path)),
                Err(e) => {
                    session.status = e.clone();
                    s.error = e;
                }
            }
        }
        s.opening = None;
    }
    let target = session
        .workspace
        .active_id()
        .and_then(|id| hosts.iter().find(|(_, h)| h.0 == id).map(|(h, _)| (h, id)));
    if target.map(|v| v.1) != s.current.map(|v| v.0) || target.map(|v| v.0) != s.host {
        s.render.clear(&mut c);
        if let Some(host) = s.host.take()
            && hosts.contains(host)
        {
            c.entity(host).despawn_children();
        }
        let opening = s.opening.take();
        let same_document = target.map(|v| v.1) == s.current.map(|v| v.0);
        let (model, filter, mode) = if same_document {
            (s.model, s.filter.clone(), s.mode)
        } else {
            (0, String::new(), 0)
        };
        *s = State {
            opening,
            model,
            filter,
            mode,
            speed: 100.,
            ..default()
        };
    }
    let Some((host, id)) = target else {
        return;
    };
    let Ok(doc) = session.workspace.document(id) else {
        return;
    };
    if s.current == Some((id, doc.revision()))
        && s.task.is_none()
        && !s.redraw
        && !s.transport
        && !ui.buttons.iter().any(|(i, _)| *i == Interaction::Pressed)
        && !ui.fields.iter().any(|(f, t, v, _)| {
            t.is_some_and(|t| t.value().to_string() != f.1) || v.is_some_and(|v| v.0 != f.1)
        })
        && !ui.controls.iter().any(|(f, t, v)| {
            t.is_some_and(|t| t.value().to_string() != f.1) || v.is_some_and(|v| v.0 != f.1)
        })
    {
        scrub(&s, &mut ui.controls);
        status(&s, &mut ui.texts);
        return;
    }
    let doc = doc.snapshot();
    let data: Value = match serde_json::from_str(doc.text()) {
        Ok(v) => v,
        Err(e) => {
            if s.host != Some(host) {
                c.spawn((
                    scenemax_ide_ui::label(format!("Invalid model catalog: {e}"), 13.),
                    ChildOf(host),
                ));
            }
            s.host = Some(host);
            s.current = Some((id, doc.revision()));
            return;
        }
    };
    let count = data["models"].as_array().map_or(0, Vec::len);
    s.model = s.model.min(count.saturating_sub(1));
    let stamp = (id, doc.revision());
    let first = s.parts.is_none();
    if first {
        s.parts = Some(view::build(&mut c, host));
        s.host = Some(host);
        s.redraw = true;
        s.transport = true;
    }
    if s.data["models"][s.model]["animationFrameRanges"]
        .as_array()
        .map(Vec::len)
        != data["models"][s.model]["animationFrameRanges"]
            .as_array()
            .map(Vec::len)
    {
        s.redraw = true;
    }
    let source_changed = s.data["models"][s.model]["path"] != data["models"][s.model]["path"]
        || s.data["models"][s.model]["sourceModel"] != data["models"][s.model]["sourceModel"];
    if s.data["models"].as_array().map(Vec::len) != data["models"].as_array().map(Vec::len) {
        s.redraw = true;
    }
    s.data = data.clone();
    if first || source_changed {
        s.render.clear(&mut c);
        load(&mut s, doc.path());
    }
    if let Some(task) = s.task.as_mut()
        && let Some(result) = bevy::tasks::block_on(bevy::tasks::poll_once(task))
    {
        s.task = None;
        if s.reload {
            s.reload = false;
            load(&mut s, doc.path());
        } else {
            match result {
                Ok(source) => {
                    s.end = source
                        .timelines
                        .first()
                        .map_or(0, |t| t.times.len().saturating_sub(1));
                    s.error = if source.timelines.is_empty() {
                        "This model contains no animations".into()
                    } else {
                        String::new()
                    };
                    s.source = Some(std::sync::Arc::new(source));
                    s.transport = true;
                    s.redraw = true;
                }
                Err(e) => s.error = e,
            }
        }
    }
    if s.current != Some(stamp) {
        if let Some(row) = s.selected {
            select(&mut s, row);
        }
        for (mut field, text, choice, children) in &mut ui.fields {
            let value = field_value(&data, &field.0);
            if field.1 != value {
                if let Some(mut text) = text {
                    text.editor_mut().set_text(&value);
                }
                if let Some(mut choice) = choice {
                    choice.0 = value.clone();
                    if let Some(children) = children {
                        for child in children.iter() {
                            if let Ok(mut text) = ui.texts.get_mut(child) {
                                text.0 = format!("{value} ↓");
                            }
                        }
                    }
                }
                field.1 = value;
            }
        }
    }
    s.current = Some(stamp);
    let mut pending = data.clone();
    let mut changed = false;
    for (mut field, text, choice, _) in &mut ui.fields {
        let value = text
            .map(|t| t.value().to_string())
            .or_else(|| choice.map(|v| v.0.clone()))
            .unwrap_or_default();
        if value == field.1 {
            continue;
        }
        field.1 = value.clone();
        let parsed = if field.0.ends_with("/start") || field.0.ends_with("/end") {
            match value.parse::<u64>() {
                Ok(v) => json!(v),
                Err(_) => {
                    s.error = "Frames must be nonnegative integers".into();
                    continue;
                }
            }
        } else {
            json!(value)
        };
        if let Ok(text) =
            scenemax_ide_core::animation_analyzer::edit(&pending.to_string(), &field.0, parsed)
            && let Ok(v) = serde_json::from_str(&text)
        {
            pending = v;
            changed = true;
        }
    }
    if changed {
        // One catalog transaction combines all native field changes in this frame.
        if let Err(e) = crate::application::animation_analyzer::edit(
            Edit {
                id,
                revision: doc.revision(),
                pointer: "replace".into(),
                value: pending.clone(),
                continuing: false,
            },
            &mut session,
            &mut changes,
        ) {
            s.error = e.to_string();
        }
        s.data = pending;
        s.error.clear();
        if let Some(row) = s.selected {
            select(&mut s, row);
        }
    }
    let mut range_edited = false;
    for (mut control, text, choice) in &mut ui.controls {
        let value = text
            .map(|t| t.value().to_string())
            .or_else(|| choice.map(|v| v.0.clone()))
            .unwrap_or_default();
        if value == control.1 {
            continue;
        }
        control.1 = value.clone();
        match control.0 {
            "filter" => {
                s.filter = value;
                s.redraw = true;
            }
            "model" => {
                if let Ok(index) = value.parse::<usize>()
                    && index < count
                {
                    s.model = index;
                    s.render.clear(&mut c);
                    load(&mut s, doc.path());
                    s.redraw = true;
                }
            }
            "animation" => {
                if let Ok(index) = value.parse::<usize>() {
                    s.animation = index;
                    s.start = 0;
                    s.end = s
                        .source
                        .as_ref()
                        .and_then(|v| v.timelines.get(index))
                        .map_or(0, |t| t.times.len().saturating_sub(1));
                    s.selected = None;
                    s.transport = true;
                    s.restart = true;
                }
            }
            "speed" => {
                if let Ok(v) = value.parse::<f32>()
                    && v.is_finite()
                {
                    s.speed = v.clamp(1., 200.);
                }
            }
            "start" | "end" => {
                if let Ok(v) = value.parse::<usize>() {
                    if control.0 == "start" {
                        s.start = v;
                    } else {
                        s.end = v;
                    }
                    s.restart = true;
                    range_edited = true;
                    s.range = true;
                }
            }
            "seek" => {
                if let Ok(v) = value.parse::<f32>()
                    && v.is_finite()
                {
                    s.seek = Some(v);
                    s.playing = false;
                    s.restart = true;
                }
            }
            _ => {}
        }
    }
    for (interaction, action) in &ui.buttons {
        if *interaction != Interaction::Pressed {
            continue;
        }
        match action {
            Action::Save => {
                queue.0.push_back(Command::Save);
            }
            Action::Undo => queue
                .0
                .push_back(Command::Edit(crate::application::EditCommand::Undo)),
            Action::Redo => queue
                .0
                .push_back(Command::Edit(crate::application::EditCommand::Redo)),
            Action::Refresh => {
                if session.workspace.document(id).is_ok_and(|d| d.is_dirty()) {
                    s.render.clear(&mut c);
                    load(&mut s, doc.path());
                } else {
                    queue
                        .0
                        .push_back(Command::ReloadPath(doc.path().to_owned()));
                }
            }
            Action::Select(row) => select(&mut s, *row),
            Action::Add | Action::Delete => {
                let base = format!("/models/{}", s.model);
                let (pointer, value) = if matches!(action, Action::Add) {
                    let rows = s.data["models"][s.model]["animationFrameRanges"].as_array();
                    let mut number = 1;
                    while rows.is_some_and(|rows| {
                        rows.iter().any(|r| {
                            r["name"].as_str().is_some_and(|name| {
                                name.eq_ignore_ascii_case(&format!("record_{number}"))
                            })
                        })
                    }) {
                        number += 1;
                    }
                    let source = s
                        .source
                        .as_ref()
                        .and_then(|v| v.timelines.get(s.animation))
                        .map(|t| t.name.clone())
                        .unwrap_or_default();
                    if s.start >= s.end || source.is_empty() {
                        s.error = "Choose an animation and a nonempty frame range first".into();
                        continue;
                    }
                    (
                        format!("{base}/add"),
                        json!({"name":format!("record_{number}"),"sourceAnimation":source,"start":s.start,"end":s.end}),
                    )
                } else {
                    let Some(row) = s.selected else {
                        continue;
                    };
                    (format!("{base}/animationFrameRanges/delete"), json!(row))
                };
                queue.0.push_back(Command::Analyzer(Edit {
                    id,
                    revision: session
                        .workspace
                        .document(id)
                        .map(|d| d.revision())
                        .unwrap_or(doc.revision()),
                    pointer,
                    value,
                    continuing: false,
                }));
                s.selected = None;
            }
            Action::UseFull => {
                s.start = 0;
                s.end = s
                    .source
                    .as_ref()
                    .and_then(|v| v.timelines.get(s.animation))
                    .map_or(0, |t| t.times.len().saturating_sub(1));
                range_edited = true;
                s.transport = true;
                s.restart = true;
            }
            Action::Full => {
                s.range = false;
                s.playing = true;
                s.restart = true;
            }
            Action::Range => {
                s.range = true;
                s.playing = true;
                s.restart = true;
            }
            Action::Pause => s.playing = !s.playing,
            Action::Stop => {
                s.playing = false;
                s.stop = true;
            }
            Action::Fit => s.render.frame = true,
            Action::Reset => s.render.reset = true,
            Action::Mode(mode) => s.mode = *mode,
        }
    }
    if range_edited && let Some(row) = s.selected {
        let revision = session
            .workspace
            .document(id)
            .map(|d| d.revision())
            .unwrap_or(doc.revision());
        let mut data = s.data.clone();
        data["models"][s.model]["animationFrameRanges"][row]["start"] = json!(s.start);
        data["models"][s.model]["animationFrameRanges"][row]["end"] = json!(s.end);
        if let Err(e) = crate::application::animation_analyzer::edit(
            Edit {
                id,
                revision,
                pointer: "replace".into(),
                value: data,
                continuing: false,
            },
            &mut session,
            &mut changes,
        ) {
            s.error = e.to_string();
        }
    }
    if range_edited && let Some(row) = s.selected {
        for (mut field, text, _, _) in &mut ui.fields {
            for (key, value) in [("start", s.start), ("end", s.end)] {
                if field.0 == format!("/models/{}/animationFrameRanges/{row}/{key}", s.model) {
                    field.1 = value.to_string();
                    if let Some(mut text) = text {
                        text.editor_mut().set_text(&field.1);
                    }
                    break;
                }
            }
        }
    }
    if range_edited || changed {
        s.current = session
            .workspace
            .document(id)
            .ok()
            .map(|d| (id, d.revision()));
    }
    if s.redraw {
        if let Some(parts) = s.parts {
            c.entity(parts.table).despawn_children();
            view::table(&mut c, parts.table, &s);
        }
        s.redraw = false;
    }
    if s.transport {
        if let Some(parts) = s.parts {
            c.entity(parts.transport).despawn_children();
            view::transport(&mut c, parts.transport, &s);
        }
        s.transport = false;
    }
    for (row, mut color) in &mut ui.rows {
        let value = if s.selected == Some(row.0) {
            scenemax_ide_ui::theme::SELECTED
        } else {
            scenemax_ide_ui::theme::PANEL
        };
        if color.0 != value {
            color.0 = value;
        }
    }
    scrub(&s, &mut ui.controls);
    status(&s, &mut ui.texts);
}
fn scrub(s: &State, controls: &mut Controls) {
    if !s.playing {
        return;
    }
    for (mut control, text, _) in controls {
        if control.0 == "seek"
            && let Some(mut text) = text
        {
            let value = format!("{:.1}", s.time);
            if control.1 != value {
                text.editor_mut().set_text(&value);
                control.1 = value;
            }
        }
    }
}
fn status(s: &State, texts: &mut Query<&mut Text>) {
    if let Some(parts) = s.parts
        && let Ok(mut text) = texts.get_mut(parts.status)
    {
        let message = if s.error.is_empty() {
            format!(
                "{} | frame {:.1} / {} | {:.0}% | speed {:.0}% | Save writes records to the model catalog",
                if s.playing { "Playing" } else { "Paused" },
                s.time,
                s.source
                    .as_ref()
                    .and_then(|v| v.timelines.get(s.animation))
                    .map_or(0, |t| t.times.len().saturating_sub(1)),
                s.time * 100.
                    / s.source
                        .as_ref()
                        .and_then(|v| v.timelines.get(s.animation))
                        .map_or(1, |t| t.times.len().saturating_sub(1).max(1))
                        as f32,
                s.speed
            )
        } else {
            s.error.clone()
        };
        if text.0 != message {
            text.0 = message;
        }
    }
}
fn select(s: &mut State, row: usize) {
    let record = &s.data["models"][s.model]["animationFrameRanges"][row];
    let source = record["sourceAnimation"]
        .as_str()
        .filter(|v| !v.is_empty())
        .or_else(|| s.data["models"][s.model]["animationFrameRangesSourceAnimation"].as_str())
        .or_else(|| s.data["models"][s.model]["sourceAnimation"].as_str())
        .unwrap_or("");
    if let Some(index) = s
        .source
        .as_ref()
        .and_then(|v| v.timelines.iter().position(|t| t.name == source))
    {
        s.animation = index;
    } else if source.is_empty() && s.source.as_ref().is_some_and(|v| v.timelines.len() == 1) {
        s.animation = 0;
    } else {
        s.animation = usize::MAX;
        s.playing = false;
        s.stop = true;
        s.error = format!("Source animation '{source}' is unavailable");
    }
    s.start = record["start"].as_u64().unwrap_or(0) as usize;
    s.end = record["end"].as_u64().unwrap_or(0) as usize;
    if record.is_null() {
        s.selected = None;
        return;
    }
    s.selected = Some(row);
    s.range = true;
    s.restart = true;
    s.transport = true;
}
fn field_value(data: &Value, pointer: &str) -> String {
    let value = data.pointer(pointer).unwrap_or(&Value::Null);
    if pointer.ends_with("/sourceAnimation")
        && value.as_str().is_none_or(str::is_empty)
        && let Some((model, _)) = pointer.split_once("/animationFrameRanges/")
    {
        let model = data.pointer(model).unwrap_or(&Value::Null);
        return display(
            model
                .get("animationFrameRangesSourceAnimation")
                .or_else(|| model.get("sourceAnimation"))
                .unwrap_or(&Value::Null),
        );
    }
    display(value)
}
fn display(v: &Value) -> String {
    v.as_str().map(str::to_owned).unwrap_or_else(|| {
        if v.is_null() {
            String::new()
        } else {
            v.to_string()
        }
    })
}

impl State {
    pub(crate) fn gizmo_target(&self) -> Option<(Entity, Entity, Entity, u8)> {
        Some((
            self.render.root?,
            self.render.camera?,
            self.render.model?,
            self.mode,
        ))
    }
}

/// Opt-in capture helper, installed only by a bounded smoke launch.
pub(crate) fn smoke(
    mut s: ResMut<State>,
    mut frame: Local<u32>,
    mut buttons: Query<(&Action, &mut Interaction)>,
) {
    if s.render.model.is_none() || s.render.graph.is_none() {
        return;
    }
    *frame += 1;
    if std::env::var("SCENEMAX_SMOKE_ANALYZER").as_deref() == Ok("exercise") {
        for (action, mut interaction) in &mut buttons {
            let pressed = matches!(
                (action, *frame),
                (Action::Mode(1), 60)
                    | (Action::Mode(2), 100)
                    | (Action::Refresh, 140)
                    | (Action::Mode(0), 220)
                    | (Action::Refresh, 260)
            );
            if pressed {
                *interaction = Interaction::Pressed;
            } else if *interaction == Interaction::Pressed {
                *interaction = Interaction::None;
            }
        }
    } else if *frame == 1 && s.source.is_some() && s.render.graph.is_some() {
        select(&mut s, 1);
        s.playing = true;
    }
}
