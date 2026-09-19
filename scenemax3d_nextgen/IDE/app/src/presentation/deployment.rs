//! Retained package form. All work is dispatched through application actions.
use crate::application::deployment::{Action, Deployment, PathField};
use bevy::{
    input_focus::{FocusCause, InputFocus, tab_navigation::TabGroup},
    prelude::*,
    text::EditableText,
};
use scenemax_ide_core::deployment::{Settings, Target};
use scenemax_ide_ui::{button, label, property, theme::*};

#[derive(Component, Clone, Copy)]
pub(crate) enum Field {
    Name,
    Version,
    Output,
    Builtin,
    Itch,
    Butler,
    Triple(usize),
    Runtime(usize),
    Recipe(usize),
    Channel(usize),
}
#[derive(Component)]
struct Toggle(Option<usize>);
#[derive(Component)]
struct Effects;
#[derive(Component)]
struct Rebuild;
#[derive(Component)]
struct AllResources;
#[derive(Component)]
struct Progress;
#[derive(Component)]
struct Status;
#[derive(Component)]
struct Logs;
#[derive(Component)]
struct LogViewport;
#[derive(Component)]
struct FollowLog;
#[derive(Component)]
struct Artifacts;
#[derive(Component)]
struct Form;
#[derive(Component)]
struct Detail(usize);
#[derive(Component)]
struct BusySummary;

#[derive(Resource, Default)]
pub(crate) struct View {
    host: Option<Entity>,
    result: Option<Entity>,
    tab: usize,
    form_revision: u64,
}

pub(crate) fn menu_item(c: &mut Commands, parent: Entity) {
    let e = button(
        c,
        parent,
        "Package & Deploy…",
        Name::new("Package & Deploy"),
    );
    c.entity(e).observe(
        |mut event: On<Pointer<Press>>, mut state: ResMut<Deployment>| {
            if event.button == PointerButton::Primary {
                event.propagate(false);
                state.actions.push_back(Action::Open);
            }
        },
    );
}
fn action(c: &mut Commands, p: Entity, title: &str, action: Action) -> Entity {
    let e = button(c, p, title, Name::new(title.to_owned()));
    c.entity(e).observe(
        move |mut event: On<Pointer<Click>>, mut state: ResMut<Deployment>| {
            if event.button == PointerButton::Primary {
                event.propagate(false);
                state.actions.push_back(action.clone());
            }
        },
    );
    e
}
fn area(c: &mut Commands, parent: Entity, node: Node, color: Color) -> Entity {
    c.spawn((node, BackgroundColor(color), ChildOf(parent)))
        .id()
}
fn text(c: &mut Commands, p: Entity, title: &str, size: f32, color: Color) -> Entity {
    c.spawn((label(title, size), ChildOf(p)))
        .insert(TextColor(color))
        .id()
}
fn field(c: &mut Commands, p: Entity, title: &str, kind: Field, value: &str) -> Entity {
    text(c, p, title, 11., MUTED);
    let path = match kind {
        Field::Output => Some(PathField::Output),
        Field::Builtin => Some(PathField::Builtin),
        Field::Butler => Some(PathField::Butler),
        Field::Runtime(i) => Some(PathField::Runtime(i)),
        Field::Recipe(i) => Some(PathField::Recipe(i)),
        _ => None,
    };
    if let Some(path) = path {
        let row = area(c, p, row(), BG);
        let input = property::input(c, row, kind, value);
        c.entity(input).insert(Node {
            flex_grow: 1.,
            width: px(0.),
            height: px(26.),
            padding: UiRect::axes(px(6.), px(3.)),
            min_width: px(0.),
            ..default()
        });
        action(c, row, "...", Action::Browse(path));
        input
    } else {
        property::input(c, p, kind, value)
    }
}
fn row() -> Node {
    Node {
        column_gap: px(6.),
        align_items: AlignItems::Center,
        flex_shrink: 0.,
        ..default()
    }
}
fn column() -> Node {
    Node {
        flex_direction: FlexDirection::Column,
        row_gap: px(7.),
        flex_shrink: 0.,
        min_height: px(0.),
        min_width: px(0.),
        ..default()
    }
}
fn spawn(c: &mut Commands, settings: &Settings, focus: &mut InputFocus) -> Entity {
    let host = c
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                width: percent(100.),
                height: percent(100.),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                ..default()
            },
            GlobalZIndex(350),
            BackgroundColor(Color::srgba(0., 0., 0., 0.5)),
            TabGroup::modal(),
        ))
        .id();
    let panel = area(
        c,
        host,
        Node {
            width: px(1080.),
            max_width: percent(95.),
            height: px(780.),
            max_height: percent(94.),
            border: px(1.).all(),
            ..column()
        },
        PANEL,
    );
    c.entity(panel).insert(BorderColor::all(EDGE));
    let header = area(
        c,
        panel,
        Node {
            padding: UiRect::axes(px(18.), px(14.)),
            justify_content: JustifyContent::SpaceBetween,
            ..row()
        },
        HEADER,
    );
    let heading = area(c, header, column(), HEADER);
    text(c, heading, "Package & Deploy", 21., Color::WHITE);
    text(
        c,
        heading,
        "Build your release. Reach your players.",
        12.,
        MUTED,
    );
    action(c, header, "Close", Action::Close);
    let body = area(
        c,
        panel,
        Node {
            flex_grow: 1.,
            padding: UiRect::horizontal(px(14.)),
            column_gap: px(14.),
            min_height: px(0.),
            ..default()
        },
        PANEL,
    );
    let left = area(
        c,
        body,
        Node {
            width: percent(54.),
            ..column()
        },
        PANEL,
    );
    let summary = text(c, left, "", 13., INK);
    c.entity(summary).insert(BusySummary);
    let scroll = property::scroll_column(c, left);
    c.entity(scroll).insert(Form);
    text(c, scroll, "01   RELEASE DETAILS", 12., INK);
    let first = field(c, scroll, "Game filename", Field::Name, &settings.name);
    field(
        c,
        scroll,
        "Release version",
        Field::Version,
        &settings.version,
    );
    field(
        c,
        scroll,
        "Output folder · each build gets a new subfolder",
        Field::Output,
        &settings.output,
    );
    field(
        c,
        scroll,
        "Shared resources (optional; included in the package)",
        Field::Builtin,
        &settings.builtin_resources,
    );
    text(c, scroll, "02   PLATFORMS", 12., INK);
    let cards = area(
        c,
        scroll,
        Node {
            display: Display::Grid,
            grid_template_columns: vec![GridTrack::flex(1.), GridTrack::flex(1.)],
            row_gap: px(5.),
            column_gap: px(5.),
            ..default()
        },
        PANEL,
    );
    for (i, p) in settings.platforms.iter().enumerate() {
        let card = area(
            c,
            cards,
            Node {
                padding: px(4.).all(),
                justify_content: JustifyContent::SpaceBetween,
                ..row()
            },
            BG,
        );
        property::checkbox(c, card, Toggle(Some(i)), p.target.label(), p.selected);
        let e = button(c, card, "Edit", Name::new("Configure platform"));
        c.entity(e).observe(
            move |mut event: On<Pointer<Click>>, mut view: ResMut<View>| {
                event.propagate(false);
                view.tab = i;
            },
        );
    }
    for (i, p) in settings.platforms.iter().enumerate() {
        let detail = area(
            c,
            scroll,
            Node {
                padding: px(9.).all(),
                ..column()
            },
            BG,
        );
        c.entity(detail).insert(Detail(i));
        text(c, detail, p.target.label(), 13., INK);
        if p.target.desktop() {
            field(c, detail, "Rust target triple", Field::Triple(i), &p.triple);
            field(
                c,
                detail,
                "Prebuilt projector (optional; blank reuses release runtime)",
                Field::Runtime(i),
                &p.runtime,
            );
            text(
                c,
                detail,
                "The runtime and its adjacent native libraries are embedded. Cross builds need the target's linker / SDK.",
                11.,
                MUTED,
            );
        } else {
            field(
                c,
                detail,
                "Platform SDK recipe (.json)",
                Field::Recipe(i),
                &p.recipe,
            );
            text(
                c,
                detail,
                match p.target {
                    Target::Android => {
                        "Requires an Android runtime host and SDK builder producing an APK."
                    }
                    Target::Ios => {
                        "Requires a macOS SDK builder, iOS runtime host and signing identity."
                    }
                    _ => "Requires a browser runtime builder producing index.html and Wasm.",
                },
                11.,
                MUTED,
            );
            text(
                c,
                detail,
                "SDK recipes are documented in IDE/DEPLOYMENT.md. These runtime hosts are not bundled yet.",
                11.,
                Color::srgb(0.94, 0.72, 0.39),
            );
        }
        field(c, detail, "itch.io channel", Field::Channel(i), &p.channel);
    }
    property::checkbox(
        c,
        scroll,
        Effects,
        "Include native Effekseer support",
        settings.native_effects,
    );
    property::checkbox(
        c,
        scroll,
        Rebuild,
        "Rebuild engine from source (slower)",
        settings.rebuild_runtime,
    );
    property::checkbox(
        c,
        scroll,
        AllResources,
        "Include all assets (dynamic resource names)",
        settings.include_all_resources,
    );
    text(
        c,
        scroll,
        "Default: package referenced assets and reuse the release engine. First engine build may take several minutes.",
        11.,
        MUTED,
    );
    text(c, scroll, "03   PUBLISH TO ITCH.IO", 12., INK);
    property::checkbox(
        c,
        scroll,
        Toggle(None),
        "Upload automatically after all builds succeed",
        settings.upload,
    );
    field(
        c,
        scroll,
        "Game page URL or username/game",
        Field::Itch,
        &settings.itch_page,
    );
    field(
        c,
        scroll,
        "Butler executable",
        Field::Butler,
        &settings.butler,
    );
    action(c, scroll, "Sign in with Butler…", Action::Login);
    text(
        c,
        scroll,
        "Uses Butler login or BUTLER_API_KEY from the environment. Credentials are never saved with your game.",
        11.,
        MUTED,
    );
    let right = area(
        c,
        body,
        Node {
            flex_grow: 1.,
            width: percent(46.),
            padding: px(12.).all(),
            ..column()
        },
        HEADER,
    );
    text(c, right, "BUILD ACTIVITY", 12., INK);
    c.spawn((label("Ready to build", 13.), Status, ChildOf(right)));
    let track = area(
        c,
        right,
        Node {
            height: px(7.),
            width: percent(100.),
            flex_shrink: 0.,
            ..default()
        },
        EDGE,
    );
    let fill = area(
        c,
        track,
        Node {
            height: percent(100.),
            width: percent(0.),
            ..default()
        },
        ACCENT,
    );
    c.entity(fill).insert(Progress);
    text(
        c,
        right,
        "Phase progress · compiler and upload details below",
        10.,
        MUTED,
    );
    let log = property::scroll_column(c, right);
    c.entity(log)
        .insert(LogViewport)
        .entry::<Node>()
        .and_modify(|mut node| node.overflow.x = OverflowAxis::Hidden);
    c.spawn((
        label("Build logs will appear here.", 11.),
        Logs,
        Node {
            width: percent(100.),
            min_width: px(0.),
            flex_shrink: 0.,
            ..default()
        },
        TextLayout::linebreak(bevy::text::LineBreak::WordOrCharacter),
        ChildOf(log),
    ));
    property::checkbox(c, right, FollowLog, "Follow log", true);
    let artifacts = text(c, right, "", 11., INK);
    c.entity(artifacts).insert(Artifacts);
    let links = area(c, right, row(), HEADER);
    action(c, links, "Open output", Action::Reveal);
    action(c, links, "Log folder", Action::Log);
    action(c, links, "Size report", Action::SizeReport);
    action(c, right, "Upload / retry existing builds", Action::Upload);
    let footer = area(
        c,
        panel,
        Node {
            padding: px(12.).all(),
            justify_content: JustifyContent::SpaceBetween,
            ..row()
        },
        HEADER,
    );
    text(
        c,
        footer,
        "Saves all documents before building · existing releases stay intact",
        11.,
        MUTED,
    );
    let buttons = area(c, footer, row(), HEADER);
    action(c, buttons, "Cancel build", Action::Cancel);
    action(c, buttons, "Check requirements", Action::Check);
    let build = action(c, buttons, "Build release", Action::Build);
    c.entity(build).insert((
        scenemax_ide_ui::ButtonSurface(SELECTED),
        BackgroundColor(SELECTED),
    ));
    focus.set(first, FocusCause::Navigated);
    host
}

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Fields<'w, 's> {
    inputs: Query<'w, 's, (&'static Field, &'static EditableText)>,
    toggles: Query<'w, 's, (&'static Toggle, &'static property::Checked)>,
    effects: Query<'w, 's, &'static property::Checked, With<Effects>>,
    rebuild: Query<'w, 's, &'static property::Checked, With<Rebuild>>,
    all_resources: Query<'w, 's, &'static property::Checked, With<AllResources>>,
}
pub(crate) fn collect(mut state: ResMut<Deployment>, fields: Fields) {
    if state.busy {
        return;
    }
    let Some(settings) = state.settings.as_mut() else {
        return;
    };
    for (kind, value) in &fields.inputs {
        let destination = match *kind {
            Field::Builtin => &mut settings.builtin_resources,
            Field::Name => &mut settings.name,
            Field::Version => &mut settings.version,
            Field::Output => &mut settings.output,
            Field::Itch => &mut settings.itch_page,
            Field::Butler => &mut settings.butler,
            Field::Triple(i) => &mut settings.platforms[i].triple,
            Field::Runtime(i) => &mut settings.platforms[i].runtime,
            Field::Recipe(i) => &mut settings.platforms[i].recipe,
            Field::Channel(i) => &mut settings.platforms[i].channel,
        };
        let value = value.value().to_string();
        if *destination != value {
            *destination = value;
        }
    }
    for (toggle, value) in &fields.toggles {
        if let Some(i) = toggle.0 {
            settings.platforms[i].selected = value.0;
        } else {
            settings.upload = value.0;
        }
    }
    if let Ok(value) = fields.effects.single() {
        settings.native_effects = value.0;
    }
    if let Ok(value) = fields.rebuild.single() {
        settings.rebuild_runtime = value.0;
    }
    if let Ok(value) = fields.all_resources.single() {
        settings.include_all_resources = value.0;
    }
}
type VisualNodes<'w, 's> = Query<
    'w,
    's,
    (
        &'static mut Node,
        Option<&'static Detail>,
        Option<&'static Progress>,
        Option<&'static Form>,
    ),
>;
type VisualText<'w, 's> = Query<
    'w,
    's,
    (
        &'static mut Text,
        Option<&'static Status>,
        Option<&'static Logs>,
        Option<&'static Artifacts>,
        Option<&'static BusySummary>,
    ),
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Visuals<'w, 's> {
    nodes: VisualNodes<'w, 's>,
    texts: VisualText<'w, 's>,
    fields: Query<'w, 's, (&'static Field, &'static mut EditableText)>,
    follow: Query<'w, 's, &'static property::Checked, With<FollowLog>>,
    scroll: Query<'w, 's, (&'static mut ScrollPosition, &'static ComputedNode), With<LogViewport>>,
}
pub(crate) fn refresh(
    mut c: Commands,
    mut view: ResMut<View>,
    state: Res<Deployment>,
    mut focus: Option<ResMut<InputFocus>>,
    mut visuals: Visuals,
) {
    if !state.open {
        if let Some(host) = view.host.take() {
            c.entity(host).despawn();
            view.result = None;
            if let Some(focus) = focus.as_mut() {
                focus.clear();
            }
        }
        return;
    }
    let Some(mut focus) = focus else {
        return;
    };
    if view.host.is_none() {
        if let Some(settings) = &state.settings {
            view.host = Some(spawn(&mut c, settings, &mut focus));
        }
        return;
    }
    if state.busy && focus.get().is_some() {
        focus.clear();
    }
    if let Some((success, message)) = &state.notification {
        if view.result.is_none()
            && let Some(host) = view.host
        {
            focus.clear();
            let overlay = area(
                &mut c,
                host,
                Node {
                    position_type: PositionType::Absolute,
                    width: percent(100.),
                    height: percent(100.),
                    align_items: AlignItems::Center,
                    justify_content: JustifyContent::Center,
                    ..default()
                },
                Color::srgba(0., 0., 0., 0.72),
            );
            c.entity(overlay)
                .insert((GlobalZIndex(370), TabGroup::modal()));
            let panel = area(
                &mut c,
                overlay,
                Node {
                    width: px(570.),
                    max_width: percent(90.),
                    padding: px(24.).all(),
                    row_gap: px(16.),
                    ..column()
                },
                PANEL,
            );
            let color = if *success {
                Color::srgb(0.42, 0.88, 0.62)
            } else {
                Color::srgb(1., 0.55, 0.46)
            };
            text(
                &mut c,
                panel,
                if *success {
                    "Release complete"
                } else {
                    "Build stopped"
                },
                24.,
                color,
            );
            text(&mut c, panel, message, 14., INK);
            if *success {
                let paths = state
                    .report
                    .artifacts
                    .iter()
                    .map(|p| p.to_string_lossy())
                    .collect::<Vec<_>>()
                    .join("\n");
                text(&mut c, panel, &paths, 12., MUTED);
            } else {
                text(
                    &mut c,
                    panel,
                    "The operation has ended. Review the build log for details, correct the issue, then build again.",
                    12.,
                    MUTED,
                );
            }
            let buttons = area(&mut c, panel, row(), PANEL);
            if *success {
                action(&mut c, buttons, "Open output folder", Action::Reveal);
            }
            if state.report.log_path.is_some() {
                action(&mut c, buttons, "Open build log", Action::Log);
            }
            if state.report.size_report_path.is_some() {
                action(&mut c, buttons, "Size report", Action::SizeReport);
            }
            let done = action(&mut c, buttons, "Done", Action::DismissResult);
            focus.set(done, FocusCause::Navigated);
            view.result = Some(overlay);
        }
    } else if let Some(result) = view.result.take() {
        c.entity(result).despawn();
        focus.clear();
    }
    if visuals.follow.single().is_ok_and(|value| value.0) {
        for (mut scroll, node) in &mut visuals.scroll {
            let bottom =
                (node.content_size().y - node.size().y).max(0.) * node.inverse_scale_factor();
            if scroll.y != bottom {
                scroll.y = bottom;
            }
        }
    }
    if view.form_revision != state.form_revision
        && let Some(settings) = &state.settings
    {
        for (field, mut input) in &mut visuals.fields {
            let value = match *field {
                Field::Output => Some(&settings.output),
                Field::Builtin => Some(&settings.builtin_resources),
                Field::Butler => Some(&settings.butler),
                Field::Runtime(i) => Some(&settings.platforms[i].runtime),
                Field::Recipe(i) => Some(&settings.platforms[i].recipe),
                _ => None,
            };
            if let Some(value) = value {
                input.editor_mut().set_text(value);
            }
        }
        view.form_revision = state.form_revision;
    }
    for (mut node, detail, progress, form) in &mut visuals.nodes {
        if let Some(detail) = detail {
            let display = if detail.0 == view.tab {
                Display::Flex
            } else {
                Display::None
            };
            if node.display != display {
                node.display = display;
            }
        }
        if progress.is_some() {
            let width = percent(state.report.progress);
            if node.width != width {
                node.width = width;
            }
        }
        if form.is_some() {
            let display = if state.busy {
                Display::None
            } else {
                Display::Flex
            };
            if node.display != display {
                node.display = display;
            }
        }
    }
    let logs = state.report.logs.iter().cloned().collect::<String>();
    let artifacts = state
        .report
        .artifacts
        .iter()
        .map(|p| p.file_name().unwrap_or_default().to_string_lossy())
        .collect::<Vec<_>>()
        .join("\n");
    for (mut text, status, log, artifact, summary) in &mut visuals.texts {
        let value = if summary.is_some() {
            Some(if state.busy {
                state.settings.as_ref().map(|s| format!("{}  /  {}\n\n{}\n\nYour release settings are locked for this operation.\nFollow the live build log on the right.\n\nCancel stops the tools owned by this build.\nCompleted artifacts and the build log are kept.", s.name, s.version, s.platforms.iter().filter(|p| p.selected).map(|p| p.target.label()).collect::<Vec<_>>().join("\n"))).unwrap_or_default()
            } else {
                String::new()
            })
        } else if status.is_some() {
            Some(format!(
                "{}%  {}",
                state.report.progress as u32, state.report.status
            ))
        } else if log.is_some() {
            Some(if logs.is_empty() {
                "The build log will appear here.\n\n1. Choose platforms and configure their tools.\n2. Check requirements.\n3. Build your release.\n\nSelected builds must all finish before upload starts.".into()
            } else {
                logs.clone()
            })
        } else if artifact.is_some() {
            Some(artifacts.clone())
        } else {
            None
        };
        if let Some(value) = value
            && text.0 != value
        {
            text.0 = value;
        }
    }
}

pub(crate) fn smoke(
    mut done: Local<bool>,
    session: Res<crate::application::Session>,
    services: Res<crate::application::EditorServices>,
    mut state: ResMut<Deployment>,
) {
    if !*done
        && !services.storage.is_pending()
        && session.workspace.project().entry_point().is_some()
    {
        state.actions.push_back(Action::Open);
        *done = true;
    }
}
pub(crate) fn keyboard(
    keys: Res<ButtonInput<KeyCode>>,
    mut closes: MessageReader<bevy::window::WindowCloseRequested>,
    mut state: ResMut<Deployment>,
) {
    if !state.open {
        closes.clear();
        return;
    }
    if closes.read().next().is_some() {
        state.actions.push_back(Action::Exit);
    } else if keys.just_pressed(KeyCode::Escape) && !state.busy {
        state.actions.push_back(Action::Close);
    }
}

pub(crate) fn smoke_build(mut done: Local<bool>, mut state: ResMut<Deployment>) {
    if !*done && state.open && !state.busy && state.settings.as_ref().is_some_and(|s| !s.upload) {
        state.actions.push_back(Action::Build);
        *done = true;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[test]
    fn result_modal_is_prominent_retained_and_dismissible() {
        let mut app = App::new();
        app.init_resource::<InputFocus>()
            .init_resource::<Deployment>()
            .init_resource::<View>();
        {
            let mut state = app.world_mut().resource_mut::<Deployment>();
            state.open = true;
            state.settings = Some(Settings::default());
            state.notification = Some((false, "Missing scene dependency".into()));
        }
        app.world_mut().run_system_once(refresh).unwrap();
        app.world_mut().run_system_once(refresh).unwrap();
        let result = app.world().resource::<View>().result.unwrap();
        assert!(
            app.world_mut()
                .query::<&Text>()
                .iter(app.world())
                .any(|t| t.0 == "Build stopped")
        );
        app.world_mut().run_system_once(refresh).unwrap();
        assert_eq!(app.world().resource::<View>().result, Some(result));
        app.world_mut().resource_mut::<Deployment>().notification = None;
        app.world_mut().run_system_once(refresh).unwrap();
        assert!(app.world().resource::<View>().result.is_none());
        assert!(app.world().get_entity(result).is_err());
    }
    #[test]
    fn progress_keeps_the_form_entities_and_unfinished_edits() {
        let mut app = App::new();
        app.init_resource::<InputFocus>()
            .init_resource::<Deployment>()
            .init_resource::<View>();
        {
            let mut state = app.world_mut().resource_mut::<Deployment>();
            state.open = true;
            state.settings = Some(Settings::default());
        }
        app.world_mut().run_system_once(refresh).unwrap();
        app.world_mut().flush();
        let host = app.world().resource::<View>().host.unwrap();
        let editor = app
            .world_mut()
            .query::<(Entity, &Field)>()
            .iter(app.world())
            .find(|(_, f)| matches!(f, Field::Name))
            .unwrap()
            .0;
        app.world_mut()
            .get_mut::<EditableText>(editor)
            .unwrap()
            .editor_mut()
            .set_text("release-draft");
        app.world_mut().run_system_once(collect).unwrap();
        assert_eq!(
            app.world()
                .resource::<Deployment>()
                .settings
                .as_ref()
                .unwrap()
                .name,
            "release-draft"
        );
        {
            let mut state = app.world_mut().resource_mut::<Deployment>();
            state.busy = true;
            state.report.progress = 42.;
            state.report.status = "Compiling".into();
        }
        app.world_mut().run_system_once(refresh).unwrap();
        app.world_mut().flush();
        assert_eq!(app.world().resource::<View>().host, Some(host));
        assert_eq!(
            app.world()
                .get::<EditableText>(editor)
                .unwrap()
                .value()
                .to_string(),
            "release-draft"
        );
        assert_eq!(app.world().resource::<InputFocus>().get(), None);
        app.world_mut().resource_mut::<Deployment>().busy = false;
        app.world_mut().run_system_once(refresh).unwrap();
        assert_eq!(app.world().resource::<View>().host, Some(host));
    }
}
