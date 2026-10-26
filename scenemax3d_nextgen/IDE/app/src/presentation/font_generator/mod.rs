//! Retained native Font Generator form and bounded background jobs.
#[cfg(test)]
mod tests;
mod view;
use crate::application::{Command, CommandQueue, Session};
use bevy::{
    input_focus::{InputFocus, tab_navigation::TabGroup},
    prelude::*,
    text::EditableText,
};
use scenemax_ide_services::font_generator::{self as service, Catalog, Options};
use scenemax_ide_ui::{property, theme::*};
use std::{
    path::PathBuf,
    time::{Duration, Instant},
};

#[derive(Component, Clone, Copy, PartialEq, Eq)]
enum Field {
    Name,
    Family,
    Style,
    Size,
    Outline,
    ShadowX,
    ShadowY,
    Padding,
    Color(usize),
    Preview,
    Charset,
    Characters,
    Smooth,
    Gradient,
}
#[derive(Component)]
struct Status;
#[derive(Component)]
struct Preview;
#[derive(Clone)]
enum Action {
    Open,
    Close,
    Refresh,
    Export,
    Dismiss,
}
enum Outcome {
    Catalog(Catalog),
    Preview(u64, Vec<u8>),
    Export(String),
}
#[derive(Resource)]
pub(crate) struct State {
    pub(crate) open: bool,
    host: Option<Entity>,
    notice: Option<Entity>,
    options: Options,
    catalog: Option<Catalog>,
    project: PathBuf,
    actions: Vec<Action>,
    task: Option<bevy::tasks::Task<Result<Outcome, String>>>,
    exporting: bool,
    export_requested: bool,
    revision: u64,
    due: Option<Instant>,
    charset: String,
    status: String,
    image: Option<Handle<Image>>,
}
impl Default for State {
    fn default() -> Self {
        Self {
            open: false,
            host: None,
            notice: None,
            options: Options::default(),
            catalog: None,
            project: PathBuf::new(),
            actions: vec![],
            task: None,
            exporting: false,
            export_requested: false,
            revision: 0,
            due: None,
            charset: "Latin + Hebrew".into(),
            status: String::new(),
            image: None,
        }
    }
}
impl State {
    fn job(&mut self, work: impl FnOnce() -> Result<Outcome, String> + Send + 'static) {
        self.task = Some(bevy::tasks::IoTaskPool::get().spawn(async move { work() }));
    }
    fn changed(&mut self) {
        self.revision += 1;
        self.due = Some(Instant::now() + Duration::from_millis(160));
    }
}
pub(crate) fn menu_item(c: &mut Commands, parent: Entity, title: &str) -> Entity {
    let e = scenemax_ide_ui::button(c, parent, title, Name::new("Font Generator"));
    c.entity(e)
        .observe(|mut e: On<PointerPress>, mut s: ResMut<State>| {
            if e.button == PointerButton::Primary {
                e.propagate(false);
                s.actions.push(Action::Open);
            }
        });
    e
}
fn number(o: &Options, f: Field) -> i32 {
    match f {
        Field::Size => o.size as i32,
        Field::Outline => o.outline as i32,
        Field::Padding => o.padding as i32,
        Field::ShadowX => o.shadow[0],
        Field::ShadowY => o.shadow[1],
        _ => 0,
    }
}
fn range(f: Field) -> (i32, i32) {
    match f {
        Field::Size => (12, 256),
        Field::Outline => (0, 24),
        Field::Padding => (0, 32),
        _ => (-32, 32),
    }
}
fn set_number(o: &mut Options, f: Field, n: i32) {
    match f {
        Field::Size => o.size = n as u32,
        Field::Outline => o.outline = n as u32,
        Field::Padding => o.padding = n as u32,
        Field::ShadowX => o.shadow[0] = n,
        Field::ShadowY => o.shadow[1] = n,
        _ => {}
    }
}
fn hex(c: [u8; 4]) -> String {
    format!("#{:02X}{:02X}{:02X}{:02X}", c[0], c[1], c[2], c[3])
}
fn parse_color(s: &str) -> Option<[u8; 4]> {
    scenemax_assets::material::color(s)
        .ok()
        .map(|v| v.map(|x| (x.clamp(0., 1.) * 255.).round() as u8))
}

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Inputs<'w, 's> {
    designers: Query<'w, 's, &'static mut super::designer::Designer>,
    fields: Query<'w, 's, (&'static Field, &'static mut EditableText)>,
    choices: Query<'w, 's, (&'static Field, &'static property::Choice)>,
    checks: Query<'w, 's, (&'static Field, &'static property::Checked)>,
    statuses: Query<'w, 's, &'static mut Text, With<Status>>,
    previews: Query<'w, 's, Entity, With<Preview>>,
    images: Option<ResMut<'w, Assets<Image>>>,
    keys: Res<'w, ButtonInput<KeyCode>>,
    focus: Option<ResMut<'w, InputFocus>>,
}
pub(crate) fn update(
    mut c: Commands,
    mut s: ResMut<State>,
    mut session: ResMut<Session>,
    mut queue: ResMut<CommandQueue>,
    mut i: Inputs,
) {
    if let Some(task) = s.task.as_mut()
        && let Some(result) = bevy::tasks::block_on(bevy::tasks::poll_once(task))
    {
        s.task = None;
        let exported = s.exporting;
        if exported {
            session.asset_operation_pending = false;
            s.exporting = false;
        }
        match result {
            Ok(Outcome::Catalog(catalog)) => {
                let names = catalog.names();
                if !names.contains(&s.options.family) {
                    s.options.family = names.first().cloned().unwrap_or_default();
                }
                s.catalog = Some(catalog);
                if let Some(host) = s.host.take() {
                    c.entity(host).try_despawn();
                }
                view::shell(&mut c, &mut s);
                s.changed();
                s.status = "Ready".into();
            }
            Ok(Outcome::Preview(rev, pixels)) if s.open && rev == s.revision => {
                if let Some(images) = i.images.as_mut() {
                    let handle = images.add(Image::new(
                        bevy::render::render_resource::Extent3d {
                            width: 640,
                            height: 320,
                            depth_or_array_layers: 1,
                        },
                        bevy::render::render_resource::TextureDimension::D2,
                        pixels,
                        bevy::render::render_resource::TextureFormat::Rgba8UnormSrgb,
                        bevy::asset::RenderAssetUsages::all(),
                    ));
                    for entity in &i.previews {
                        c.entity(entity).insert(ImageNode::new(handle.clone()));
                    }
                    s.image = Some(handle);
                }
                s.status =
                    "Preview ready · exports .fnt + .png and fonts-ext.json registration".into();
            }
            Ok(Outcome::Preview(..)) => {}
            Ok(Outcome::Export(message)) => {
                for mut designer in &mut i.designers {
                    designer.reload_assets();
                }
                s.status = "Font exported and registered successfully".into();
                view::notice(&mut c, &mut s, "Font exported", &message);
                queue.0.push_back(Command::Refresh);
            }
            Err(e) => {
                s.status = e.clone();
                if exported || s.export_requested || s.catalog.is_none() {
                    s.export_requested = false;
                    view::notice(&mut c, &mut s, "Font Generator", &e);
                }
            }
        }
    }
    if s.open && s.project != session.workspace.project().root() && !s.exporting {
        s.actions.push(Action::Close);
    }
    let before = s.options.clone();
    let mut invalid = None;
    if s.open {
        for (field, text) in &mut i.fields {
            let value = text.value().to_string();
            match *field {
                Field::Name => s.options.name = value.trim().into(),
                Field::Preview => s.options.preview = value,
                Field::Characters => s.options.characters = value,
                Field::Size | Field::Outline | Field::ShadowX | Field::ShadowY | Field::Padding => {
                    let (min, max) = range(*field);
                    if let Ok(n) = value.parse::<i32>()
                        && (min..=max).contains(&n)
                    {
                        set_number(&mut s.options, *field, n);
                    } else {
                        invalid = Some(format!("Enter a whole number between {min} and {max}."));
                    }
                }
                Field::Color(index) => {
                    if let Some(color) = parse_color(&value) {
                        s.options.colors[index] = color;
                    } else {
                        invalid = Some("Enter a color as #RRGGBB or #RRGGBBAA.".into());
                    }
                }
                _ => {}
            }
        }
        for (field, choice) in &i.choices {
            match *field {
                Field::Family => s.options.family = choice.0.clone(),
                Field::Style => {
                    s.options.style = match choice.0.as_str() {
                        "Bold" => 1,
                        "Italic" => 2,
                        "Bold Italic" => 3,
                        _ => 0,
                    }
                }
                Field::Charset if s.charset != choice.0 => {
                    s.charset = choice.0.clone();
                    let chars = match s.charset.as_str() {
                        "Basic Latin" => {
                            Some(scenemax_ide_core::font_generator::BASIC_LATIN.into())
                        }
                        "Latin + Hebrew" => Some(format!(
                            "{}{}",
                            scenemax_ide_core::font_generator::BASIC_LATIN,
                            scenemax_ide_core::font_generator::HEBREW
                        )),
                        _ => None,
                    };
                    if let Some(chars) = chars {
                        s.options.characters = chars;
                        for (f, mut text) in &mut i.fields {
                            if *f == Field::Characters {
                                text.editor_mut().set_text(&s.options.characters);
                            }
                        }
                    }
                }
                _ => {}
            }
        }
        for (field, checked) in &i.checks {
            match field {
                Field::Smooth => s.options.smooth = checked.0,
                Field::Gradient => s.options.gradient = checked.0,
                _ => {}
            }
        }
        if s.options != before {
            s.changed();
        }
        if i.keys.just_pressed(KeyCode::Escape) {
            let a = if s.notice.is_some() {
                Action::Dismiss
            } else {
                Action::Close
            };
            s.actions.push(a);
        }
    }
    for action in std::mem::take(&mut s.actions) {
        if s.exporting {
            continue;
        }
        match action {
            Action::Open if !s.open => {
                s.open = true;
                s.project = session.workspace.project().root().into();
                if let Some(focus) = i.focus.as_mut() {
                    focus.clear();
                }
                view::shell(&mut c, &mut s);
                if s.catalog.is_none() {
                    s.status = "Loading system fonts…".into();
                    s.job(|| {
                        Catalog::discover()
                            .map(Outcome::Catalog)
                            .map_err(|e| e.to_string())
                    });
                } else {
                    s.changed();
                }
            }
            Action::Close => {
                if let Some(host) = s.host.take() {
                    c.entity(host).try_despawn();
                }
                s.open = false;
                s.task = None;
                s.due = None;
                s.export_requested = false;
                s.notice = None;
                s.image = None;
                if let Some(focus) = i.focus.as_mut() {
                    focus.clear();
                }
            }
            Action::Refresh => {
                s.changed();
                s.due = Some(Instant::now());
            }
            Action::Export => {
                if let Some(e) = invalid
                    .clone()
                    .or_else(|| s.options.validate().err())
                    .or_else(|| s.options.validate_name().err())
                {
                    view::notice(&mut c, &mut s, "Check font settings", &e);
                } else {
                    s.export_requested = true;
                }
            }
            Action::Dismiss => {
                if let Some(e) = s.notice.take() {
                    c.entity(e).try_despawn();
                }
            }
            _ => {}
        }
    }
    if s.open
        && s.task.is_none()
        && let Some(catalog) = s.catalog.clone()
    {
        if s.export_requested {
            s.export_requested = false;
            if let Some(e) = invalid.as_ref() {
                view::notice(&mut c, &mut s, "Check font settings", e);
                return;
            }
            s.exporting = true;
            session.asset_operation_pending = true;
            s.status = "Generating atlas and registering font…".into();
            let project = s.project.clone();
            let options = s.options.clone();
            s.job(move || {
                service::export(&project, &catalog, &options)
                    .map(Outcome::Export)
                    .map_err(|e| e.to_string())
            });
        } else if s.due.is_some_and(|d| Instant::now() >= d) && invalid.is_none() {
            s.due = None;
            let options = s.options.clone();
            let rev = s.revision;
            s.job(move || {
                service::preview(&catalog, &options)
                    .map(|img| Outcome::Preview(rev, img.into_raw()))
                    .map_err(|e| e.to_string())
            });
        }
    }
    let status = invalid.as_deref().unwrap_or(&s.status);
    for mut text in &mut i.statuses {
        if text.0 != status {
            text.0 = status.into();
        }
    }
}
pub(crate) fn smoke(
    mut state: ResMut<State>,
    session: Res<Session>,
    services: Res<crate::application::EditorServices>,
    mut opened: Local<bool>,
) {
    if !*opened && !services.storage.is_pending() && session.status != "Opening project…" {
        state.actions.push(Action::Open);
        *opened = true;
    }
}
