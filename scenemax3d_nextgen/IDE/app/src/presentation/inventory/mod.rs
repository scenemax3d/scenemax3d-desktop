//! Retained project asset browser; disk work is delegated to inventory services.
mod preview;
mod view;
pub(crate) use preview::update as preview_update;
#[cfg(test)]
mod tests;
use crate::application::{Command, CommandQueue, Session};
use bevy::{input_focus::InputFocus, prelude::*, text::EditableText};
use scenemax_ide_services::inventory::{self, Asset, Inventory, Operation, Plan};
use std::{collections::BTreeSet, path::PathBuf, sync::mpsc};
#[derive(Clone, Debug)]
pub(super) enum Action {
    Open,
    Close,
    Refresh,
    Category(String),
    Select(String, bool),
    SelectVisible,
    Clear,
    Page(i32),
    Sort,
    Inspect,
    Folder,
    CopyName,
    Play,
    Stop,
    Frame,
    Turntable,
    Clip,
    Animate,
    Review(Operation),
    Confirm,
    Cancel,
    Rename,
    Source,
}
#[derive(Component, Clone, Copy, PartialEq)]
pub(super) enum Field {
    Search,
    Target,
    Rename,
}
#[derive(Component)]
pub(super) struct Status;
#[derive(Component)]
pub(super) struct Progress;
#[derive(Component)]
pub(super) struct Card;
pub(super) enum Outcome {
    Scan(Inventory),
    Review(Plan),
    Applied(String),
    Text(String),
    Done,
}
type JobResult = Result<Outcome, String>;
#[derive(Resource)]
pub(crate) struct State {
    pub(crate) open: bool,
    pub(super) actions: Vec<Action>,
    pub(super) host: Option<Entity>,
    pub(super) gallery: Option<Entity>,
    pub(super) details: Option<Entity>,
    pub(super) sidebar: Option<Entity>,
    pub(super) review_host: Option<Entity>,
    pub(super) viewport: Option<Entity>,
    pub(super) root: PathBuf,
    pub(super) inventory: Inventory,
    pub(super) selected: BTreeSet<String>,
    pub(super) active: Option<String>,
    pub(super) category: String,
    pub(super) search: String,
    pub(super) page: usize,
    pub(super) sort_size: bool,
    pub(super) target: String,
    pub(super) rename: String,
    pub(super) status: String,
    pub(super) source: bool,
    pub(super) text: String,
    pub(super) plan: Option<Plan>,
    task: Option<bevy::tasks::Task<JobResult>>,
    progress: Option<std::sync::Mutex<mpsc::Receiver<(usize, usize)>>>,
    pub(super) fraction: f32,
    play_audio: bool,
    audio_generation: u64,
    generation: u64,
    pub(super) redraw: bool,
    pub(super) detail_dirty: bool,
    pub(super) categories_dirty: bool,
    render: preview::Preview,
}
impl Default for State {
    fn default() -> Self {
        Self {
            open: false,
            actions: vec![],
            host: None,
            gallery: None,
            details: None,
            sidebar: None,
            review_host: None,
            viewport: None,
            root: PathBuf::new(),
            inventory: Inventory::default(),
            selected: BTreeSet::new(),
            active: None,
            category: "All".into(),
            search: String::new(),
            page: 0,
            sort_size: false,
            target: String::new(),
            rename: String::new(),
            status: String::new(),
            source: false,
            text: String::new(),
            plan: None,
            task: None,
            progress: None,
            fraction: 0.,
            play_audio: false,
            audio_generation: 0,
            generation: 0,
            redraw: false,
            detail_dirty: false,
            categories_dirty: false,
            render: default(),
        }
    }
}
impl State {
    fn selected_asset(&self) -> Option<&Asset> {
        self.inventory
            .assets
            .iter()
            .find(|a| Some(&a.id) == self.active.as_ref())
    }
    fn filtered(&self) -> Vec<Asset> {
        let mut v: Vec<_> = self
            .inventory
            .assets
            .iter()
            .filter(|a| {
                (self.category == "All" || a.category == self.category) && a.matches(&self.search)
            })
            .cloned()
            .collect();
        if self.sort_size {
            v.sort_by_key(|a| std::cmp::Reverse(a.bytes));
        }
        v
    }
    fn job(&mut self, work: impl FnOnce() -> JobResult + Send + 'static) {
        self.fraction = 0.;
        self.task = Some(bevy::tasks::IoTaskPool::get().spawn(async move { work() }));
    }
    fn scan(&mut self) {
        let root = self.root.clone();
        self.status = "Scanning registered project and shared assets…".into();
        self.job(move || {
            inventory::scan(&root)
                .map(Outcome::Scan)
                .map_err(|e| e.to_string())
        });
    }
    fn stop_media(&mut self) {
        self.play_audio = false;
    }
}
pub(crate) fn menu_item(commands: &mut Commands, parent: Entity, title: &str) -> Entity {
    let row = scenemax_ide_ui::button(commands, parent, title, Name::new("Project Inventory"));
    commands
        .entity(row)
        .observe(|mut e: On<Pointer<Press>>, mut state: ResMut<State>| {
            if e.button == PointerButton::Primary {
                e.propagate(false);
                state.actions.push(Action::Open);
            }
        });
    row
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Inputs<'w, 's> {
    fields: Query<'w, 's, (Entity, &'static Field, &'static EditableText)>,
    choices: Query<'w, 's, (&'static Field, &'static scenemax_ide_ui::property::Choice)>,
    keys: Res<'w, ButtonInput<KeyCode>>,
    focus: Option<ResMut<'w, InputFocus>>,
    clipboard: Option<ResMut<'w, bevy::clipboard::Clipboard>>,
    texts: Query<'w, 's, &'static mut Text, With<Status>>,
    bars: Query<'w, 's, &'static mut Node, With<Progress>>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    mut session: ResMut<Session>,
    mut queue: ResMut<CommandQueue>,
    mut inputs: Inputs,
) {
    if state.open && state.root != session.workspace.project().root() {
        state.actions.push(Action::Close);
    }
    if let Some(rx) = &state.progress {
        let last = rx.lock().ok().and_then(|rx| rx.try_iter().last());
        if let Some((n, total)) = last {
            state.fraction = n as f32 / total.max(1) as f32;
            state.status = format!("Applying asset transfer: {n}/{total}");
        }
    }
    if let Some(task) = &mut state.task
        && let Some(result) = bevy::tasks::block_on(bevy::tasks::poll_once(task))
    {
        state.task = None;
        session.asset_operation_pending = false;
        state.progress = None;
        match result {
            Ok(Outcome::Scan(inv)) => {
                state.generation += 1;
                state.render.thumbnails.clear();
                state.inventory = inv;
                let available: BTreeSet<_> = state
                    .inventory
                    .assets
                    .iter()
                    .map(|a| a.id.clone())
                    .collect();
                state.selected.retain(|id| available.contains(id));
                state.status = format!(
                    "{} assets • {} scan warnings",
                    state.inventory.assets.len(),
                    state.inventory.warnings.len()
                );
                state.redraw = true;
                state.categories_dirty = true;
                state.detail_dirty = true;
            }
            Ok(Outcome::Review(plan)) => {
                state.status = format!(
                    "Review: {} assets • {:.1} MiB • {} conflicts",
                    plan.assets,
                    plan.bytes as f64 / 1048576.,
                    plan.conflicts.len()
                );
                state.plan = Some(plan);
                view::review(&mut commands, &mut state);
            }
            Ok(Outcome::Applied(message)) => {
                state.status = message.clone();
                view::message(&mut commands, &mut state, "Operation complete", &message);
                state.fraction = 1.;
                queue.0.push_back(Command::Refresh);
                state.selected.clear();
                state.active = None;
                state.redraw = true;
                state.detail_dirty = true;
                state.scan();
            }
            Ok(Outcome::Text(text)) => {
                state.text = text;
                state.detail_dirty = true;
            }
            Ok(Outcome::Done) => {}
            Err(e) => {
                state.status = format!("Operation failed: {e}");
                state.fraction = 0.;
                view::message(&mut commands, &mut state, "Operation failed", &e);
            }
        }
    }
    if state.open {
        for (_, field, text) in &inputs.fields {
            let v = text.value().to_string();
            match field {
                Field::Search if state.search != v => {
                    state.search = v;
                    state.page = 0;
                    state.redraw = true;
                }
                Field::Target => state.target = v,
                Field::Rename => state.rename = v,
                _ => {}
            }
        }
        for (field, choice) in &inputs.choices {
            if *field == Field::Target {
                state.target = choice.0.clone();
            }
        }
        let typing = inputs
            .focus
            .as_ref()
            .and_then(|f| f.get())
            .is_some_and(|e| inputs.fields.get(e).is_ok());
        if !typing && state.plan.is_none() && state.task.is_none() {
            let filtered = state.filtered();
            let current = filtered
                .iter()
                .position(|a| Some(&a.id) == state.active.as_ref())
                .unwrap_or(0);
            let next = if inputs.keys.just_pressed(KeyCode::ArrowDown)
                || inputs.keys.just_pressed(KeyCode::ArrowRight)
            {
                Some((current + 1).min(filtered.len().saturating_sub(1)))
            } else if inputs.keys.just_pressed(KeyCode::ArrowUp)
                || inputs.keys.just_pressed(KeyCode::ArrowLeft)
            {
                Some(current.saturating_sub(1))
            } else if inputs.keys.just_pressed(KeyCode::Home) {
                Some(0)
            } else if inputs.keys.just_pressed(KeyCode::End) {
                Some(filtered.len().saturating_sub(1))
            } else {
                None
            };
            if let Some(index) = next
                && let Some(a) = filtered.get(index)
            {
                state.page = index / 60;
                state.actions.push(Action::Select(a.id.clone(), false));
            }
            if inputs.keys.just_pressed(KeyCode::Enter) {
                state.actions.push(Action::Inspect);
            }
        }
        if inputs.keys.just_pressed(KeyCode::Escape) {
            let action = if state.plan.is_some() {
                Action::Cancel
            } else {
                Action::Close
            };
            state.actions.push(action);
        }
    }
    let actions = std::mem::take(&mut state.actions);
    for action in actions {
        if state.task.is_some() && !matches!(action, Action::Stop) {
            state.status = "Please wait for the current asset operation to finish".into();
            continue;
        }
        if state.plan.is_some()
            && !matches!(action, Action::Confirm | Action::Cancel | Action::Close)
        {
            continue;
        }
        if matches!(action, Action::Review(_) | Action::Rename)
            && session.workspace.has_dirty_documents()
        {
            state.status =
                "Save your project edits before transferring, renaming or removing assets.".into();
            continue;
        }
        match action {
            Action::Open => {
                if !state.open {
                    state.open = true;
                    state.root = session.workspace.project().root().into();
                    state.host = Some(view::shell(&mut commands, &mut state, &session));
                    if let Some(focus) = inputs.focus.as_mut() {
                        focus.clear();
                    }
                    state.scan();
                }
            }
            Action::Close => {
                state.stop_media();
                if let Some(h) = state.host.take() {
                    commands.entity(h).try_despawn();
                }
                state.render.clear(&mut commands);
                state.open = false;
                state.plan = None;
                state.review_host = None;
                state.viewport = None;
            }
            Action::Refresh => {
                state.stop_media();
                state.scan();
            }
            Action::Category(c) => {
                state.category = c;
                state.categories_dirty = true;
                state.page = 0;
                state.redraw = true;
            }
            Action::Select(id, multi) => {
                state.stop_media();
                if !multi {
                    state.selected.clear();
                }
                if multi && state.selected.contains(&id) {
                    state.selected.remove(&id);
                } else {
                    state.selected.insert(id.clone());
                }
                state.active = Some(id);
                state.rename = state
                    .selected_asset()
                    .map(|a| a.name.clone())
                    .unwrap_or_default();
                state.source = false;
                state.text.clear();
                state.detail_dirty = true;
                state.redraw = true;
            }
            Action::SelectVisible => {
                let ids: Vec<_> = state
                    .filtered()
                    .iter()
                    .skip(state.page * 60)
                    .take(60)
                    .map(|a| a.id.clone())
                    .collect();
                state.selected.extend(ids);
                state.redraw = true;
            }
            Action::Clear => {
                state.selected.clear();
                state.redraw = true;
            }
            Action::Page(delta) => {
                state.page = state
                    .page
                    .saturating_add_signed(delta as isize)
                    .min(state.filtered().len().saturating_sub(1) / 60);
                state.redraw = true;
            }
            Action::Sort => {
                state.sort_size = !state.sort_size;
                state.redraw = true;
            }
            Action::CopyName => {
                if let Some(a) = state.selected_asset()
                    && let Some(clipboard) = inputs.clipboard.as_mut()
                {
                    state.status = match clipboard.set_text(a.name.clone()) {
                        Ok(_) => "Resource name copied".into(),
                        Err(e) => format!("Clipboard failed: {e}"),
                    };
                }
            }
            Action::Inspect => {
                if let Some(a) = state.selected_asset().cloned()
                    && let Some(file) = a.file
                {
                    if a.origin == "Designer" {
                        queue.0.push_back(Command::Open(file));
                        state.actions.push(Action::Close);
                    } else {
                        state.job(move || {
                            inventory::open_path(&file)
                                .map(|_| Outcome::Done)
                                .map_err(|e| e.to_string())
                        });
                    }
                }
            }
            Action::Folder => {
                if let Some(file) = state
                    .selected_asset()
                    .and_then(|a| a.file.clone())
                    .and_then(|p| p.parent().map(PathBuf::from))
                {
                    state.job(move || {
                        inventory::open_path(&file)
                            .map(|_| Outcome::Done)
                            .map_err(|e| e.to_string())
                    });
                }
            }
            Action::Play => {
                state.audio_generation += 1;
                state.play_audio = true;
                state.status = "Playing audio • Stop ends playback".into();
            }
            Action::Stop => state.stop_media(),
            Action::Frame => state.render.frame = true,
            Action::Turntable => state.render.spin = !state.render.spin,
            Action::Animate => state.render.playing = !state.render.playing,
            Action::Clip => {
                state.render.clip = state.render.clip.wrapping_add(1);
                state.render.restart = true;
            }
            Action::Source => {
                state.source = !state.source;
                if state.source
                    && let Some(a) = state.selected_asset().cloned()
                {
                    state.job(move || {
                        inventory::text_preview(&a)
                            .map(Outcome::Text)
                            .map_err(|e| e.to_string())
                    });
                }
                state.detail_dirty = true;
            }
            Action::Rename => {
                session.asset_operation_pending = true;
                let inv = state.inventory.clone();
                let id = state.active.clone().unwrap_or_default();
                let name = state.rename.clone();
                state.job(move || {
                    inventory::rename(&inv, &id, &name)
                        .map(Outcome::Applied)
                        .map_err(|e| e.to_string())
                });
            }
            Action::Review(op) => {
                let inv = state.inventory.clone();
                let ids = state.selected.iter().cloned().collect::<Vec<_>>();
                let target = if state.target.is_empty() {
                    None
                } else {
                    Some(PathBuf::from(&state.target))
                };
                state.status = "Resolving files, registrations and dependencies…".into();
                state.job(move || {
                    inventory::review(&inv, &ids, target.as_deref(), op)
                        .map(Outcome::Review)
                        .map_err(|e| e.to_string())
                });
            }
            Action::Cancel => {
                state.plan = None;
                if let Some(e) = state.review_host.take() {
                    commands.entity(e).try_despawn();
                }
            }
            Action::Confirm => {
                if let Some(plan) = state.plan.take() {
                    if !plan.conflicts.is_empty() {
                        state.plan = Some(plan);
                        continue;
                    }
                    state.stop_media();
                    if let Some(e) = state.review_host.take() {
                        commands.entity(e).try_despawn();
                    }
                    let (tx, rx) = mpsc::channel();
                    state.progress = Some(std::sync::Mutex::new(rx));
                    state.status = "Applying reviewed transfer…".into();
                    session.asset_operation_pending = true;
                    state.job(move || {
                        inventory::apply(plan, |n, total| {
                            let _ = tx.send((n, total));
                        })
                        .map(Outcome::Applied)
                        .map_err(|e| e.to_string())
                    });
                }
            }
        }
    }
    if state.open {
        if state.categories_dirty {
            view::categories(&mut commands, &state);
            state.categories_dirty = false;
        }
        if state.redraw {
            view::gallery(&mut commands, &mut state);
            state.redraw = false;
        }
        if state.detail_dirty {
            view::details(&mut commands, &mut state);
            state.detail_dirty = false;
        }
        for mut text in &mut inputs.texts {
            if text.0 != state.status {
                text.0 = state.status.clone();
            }
        }
        for mut node in &mut inputs.bars {
            let width = percent(state.fraction * 100.);
            if node.width != width {
                node.width = width;
            }
        }
    }
}

/// Inventory GPU smoke hook, active only when explicitly enabled by the smoke runner.
pub(crate) fn smoke(
    mut state: ResMut<State>,
    services: Res<crate::application::EditorServices>,
    session: Res<Session>,
    mut started: Local<bool>,
    mut selected: Local<bool>,
) {
    if services.storage.is_pending() || services.catalog_storage.is_pending() {
        return;
    }
    if !*started && session.status != "Opening project…" {
        state.actions.push(Action::Open);
        *started = true;
    }
    if *started
        && !*selected
        && state.open
        && state.task.is_none()
        && !state.inventory.assets.is_empty()
    {
        let requested = std::env::var("SCENEMAX_SMOKE_INVENTORY").unwrap_or_default();
        if let Some(a) = state
            .inventory
            .assets
            .iter()
            .find(|a| a.name == requested)
            .or_else(|| {
                state
                    .inventory
                    .assets
                    .iter()
                    .find(|a| a.category == "Models" && a.status == "Ready")
            })
        {
            let id = a.id.clone();
            state.actions.push(Action::Select(id, false));
        }
        *selected = true;
    }
}
