use super::*;
use bevy::input_focus::tab_navigation::TabGroup;
use scenemax_ide_ui::{label, property, theme::*};
#[derive(Component)]
pub(super) struct Thumb {
    pub path: PathBuf,
}
pub(super) fn control(c: &mut Commands, parent: Entity, title: &str, action: Action) -> Entity {
    let e = scenemax_ide_ui::button(c, parent, title, Name::new(title.to_owned()));
    c.entity(e).observe(
        move |mut event: On<Pointer<Click>>,
              mut state: ResMut<State>,
              keys: Res<ButtonInput<KeyCode>>,
              mut focus: Option<ResMut<InputFocus>>| {
            if event.button == PointerButton::Primary {
                event.propagate(false);
                let mut a = action.clone();
                if let Action::Select(_, multi) = &mut a {
                    if let Some(focus) = focus.as_mut() {
                        focus.clear();
                    }
                    *multi = keys.any_pressed([KeyCode::ControlLeft, KeyCode::ControlRight]);
                }
                state.actions.push(a);
            }
        },
    );
    e
}
fn node(c: &mut Commands, parent: Entity, n: Node) -> Entity {
    c.spawn((n, ChildOf(parent))).id()
}
fn row(c: &mut Commands, parent: Entity) -> Entity {
    node(
        c,
        parent,
        Node {
            align_items: AlignItems::Center,
            column_gap: px(6.),
            flex_shrink: 0.,
            flex_wrap: FlexWrap::Wrap,
            ..default()
        },
    )
}
fn text(c: &mut Commands, parent: Entity, value: impl Into<String>, size: f32) -> Entity {
    c.spawn((label(value, size), ChildOf(parent))).id()
}
pub(super) fn shell(c: &mut Commands, s: &mut State, session: &Session) -> Entity {
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
            BackgroundColor(Color::srgba(0., 0., 0., 0.55)),
            GlobalZIndex(350),
            TabGroup::modal(),
        ))
        .id();
    let panel = node(
        c,
        host,
        Node {
            width: percent(96.),
            height: percent(94.),
            padding: px(12.).all(),
            row_gap: px(8.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    c.entity(panel).insert(BackgroundColor(PANEL));
    let top = row(c, panel);
    text(c, top, "PROJECT INVENTORY", 19.);
    let spacer = node(
        c,
        top,
        Node {
            flex_grow: 1.,
            ..default()
        },
    );
    let _ = spacer;
    control(c, top, "Refresh", Action::Refresh);
    control(c, top, "Close", Action::Close);
    text(
        c,
        panel,
        format!(
            "{} • Registered resources and authoring assets",
            session
                .workspace
                .project()
                .root()
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
        ),
        12.,
    );
    let search = row(c, panel);
    text(c, search, "Search", 12.);
    let field = property::input(c, search, Field::Search, &s.search);
    c.entity(field).insert(Node {
        flex_grow: 1.,
        width: px(280.),
        height: px(28.),
        ..default()
    });
    control(c, search, "Sort: name / size", Action::Sort);
    control(c, search, "Select page", Action::SelectVisible);
    control(c, search, "Clear selection", Action::Clear);
    let body = node(
        c,
        panel,
        Node {
            flex_grow: 1.,
            min_height: px(0.),
            column_gap: px(10.),
            ..default()
        },
    );
    let side = node(
        c,
        body,
        Node {
            width: px(165.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    c.entity(side).insert(BackgroundColor(BG));
    s.sidebar = Some(property::scroll_column(c, side));
    let center = node(
        c,
        body,
        Node {
            flex_grow: 1.,
            min_width: px(250.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    s.gallery = Some(property::scroll_column(c, center));
    let pager = row(c, center);
    control(c, pager, "‹ Previous", Action::Page(-1));
    control(c, pager, "Next ›", Action::Page(1));
    text(c, pager, "Ctrl+click selects multiple assets", 11.);
    let details = node(
        c,
        body,
        Node {
            width: percent(36.),
            min_width: px(320.),
            max_width: px(600.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    c.entity(details).insert(BackgroundColor(BG));
    s.details = Some(property::scroll_column(c, details));
    let transfer = row(c, panel);
    text(c, transfer, "Destination", 12.);
    let target = node(
        c,
        transfer,
        Node {
            width: px(330.),
            ..default()
        },
    );
    let options = session
        .catalog
        .projects
        .iter()
        .filter(|p| p.root != s.root)
        .map(|p| (p.root.to_string_lossy().into_owned(), p.name.clone()))
        .collect::<Vec<_>>();
    if options.is_empty() {
        property::input(c, target, Field::Target, &s.target);
    } else {
        property::dropdown_labeled(c, target, Field::Target, &s.target, &options);
    }
    control(
        c,
        transfer,
        "Copy to project…",
        Action::Review(Operation::Copy),
    );
    control(
        c,
        transfer,
        "Move to project…",
        Action::Review(Operation::Move),
    );
    control(c, transfer, "Remove…", Action::Review(Operation::Archive));
    let bar = node(
        c,
        panel,
        Node {
            height: px(4.),
            flex_shrink: 0.,
            ..default()
        },
    );
    c.entity(bar).insert(BackgroundColor(HEADER));
    c.spawn((
        Progress,
        Node {
            height: percent(100.),
            width: px(0.),
            ..default()
        },
        BackgroundColor(Color::srgb(0.35, 0.62, 0.95)),
        ChildOf(bar),
    ));
    c.spawn((Status, label("Scanning assets…", 12.), ChildOf(panel)));
    host
}
pub(super) fn categories(c: &mut Commands, s: &State) {
    let Some(host) = s.sidebar else {
        return;
    };
    c.entity(host).despawn_children();
    for category in inventory::CATEGORIES {
        let count = s
            .inventory
            .assets
            .iter()
            .filter(|a| *category == "All" || a.category == *category)
            .count();
        let button = control(
            c,
            host,
            &format!("{category}  {count}"),
            Action::Category((*category).into()),
        );
        if s.category == *category {
            c.entity(button).insert((
                scenemax_ide_ui::NoButtonFeedback,
                BackgroundColor(Color::srgb(0.22, 0.29, 0.38)),
            ));
        }
    }
}
pub(super) fn gallery(c: &mut Commands, s: &mut State) {
    let Some(host) = s.gallery else {
        return;
    };
    c.entity(host).despawn_children();
    let assets = s.filtered();
    s.page = s.page.min(assets.len().saturating_sub(1) / 60);
    text(
        c,
        host,
        format!(
            "{} • {} results • {} selected • Page {} / {}",
            s.category,
            assets.len(),
            s.selected.len(),
            s.page + 1,
            assets.len().div_ceil(60).max(1)
        ),
        12.,
    );
    let grid = node(
        c,
        host,
        Node {
            flex_wrap: FlexWrap::Wrap,
            column_gap: px(6.),
            row_gap: px(6.),
            ..default()
        },
    );
    if assets.is_empty() {
        text(
            c,
            grid,
            "No matching assets. Try another category or search.",
            14.,
        );
    }
    for a in assets.iter().skip(s.page * 60).take(60) {
        let selected = s.selected.contains(&a.id);
        let card = control(c, grid, "", Action::Select(a.id.clone(), false));
        c.entity(card).despawn_children();
        c.entity(card).insert((
            Card,
            scenemax_ide_ui::NoButtonFeedback,
            Node {
                width: px(162.),
                height: px(152.),
                padding: px(8.).all(),
                flex_direction: FlexDirection::Column,
                align_items: AlignItems::Start,
                row_gap: px(3.),
                overflow: Overflow::clip(),
                border: px(1.).all(),
                ..default()
            },
            BorderColor::all(if selected {
                Color::srgb(0.45, 0.7, 1.)
            } else {
                EDGE
            }),
            BackgroundColor(if selected {
                Color::srgb(0.22, 0.29, 0.38)
            } else {
                HEADER
            }),
        ));
        if let Some(thumbnail) = s.render.thumbnails.get(&a.id) {
            c.spawn((
                ImageNode::new(thumbnail.clone()),
                Node {
                    width: percent(100.),
                    height: px(65.),
                    ..default()
                },
                ChildOf(card),
            ));
        } else if let Some(path) = &a.file
            && image(path)
        {
            c.spawn((
                Thumb { path: path.clone() },
                ImageNode::default(),
                Node {
                    width: percent(100.),
                    height: px(65.),
                    ..default()
                },
                ChildOf(card),
            ));
        } else {
            let icon = match a.category.as_str() {
                "Models" => "◇  3D",
                "Audio" => "♫  AUDIO",
                "Animations" => "▷  MOTION",
                "Materials" => "●  SURFACE",
                "Scenes" => "▧  SCENE",
                _ => "▤  ASSET",
            };
            text(c, card, icon, 20.);
        }
        text(
            c,
            card,
            format!("{}{}", if selected { "✓ " } else { "" }, a.name),
            13.,
        );
        text(c, card, format!("{} • {}", a.category, a.origin), 10.);
        text(
            c,
            card,
            format!("{:.1} KiB • {}", a.bytes as f64 / 1024., a.status),
            10.,
        );
    }
}
pub(super) fn image(path: &std::path::Path) -> bool {
    path.extension().is_some_and(|e| {
        ["png", "jpg", "jpeg"]
            .iter()
            .any(|x| e.eq_ignore_ascii_case(x))
    })
}
pub(super) fn details(c: &mut Commands, s: &mut State) {
    let Some(host) = s.details else {
        return;
    };
    c.entity(host).despawn_children();
    s.viewport = None;
    let Some(a) = s.selected_asset().cloned() else {
        text(c, host, "Select an asset to preview", 17.);
        text(
            c,
            host,
            "Browse registrations, inspect metadata, and transfer complete asset bundles between projects.",
            13.,
        );
        for warning in s.inventory.warnings.iter().take(30) {
            text(c, host, warning, 12.);
        }
        return;
    };
    text(c, host, &a.name, 20.);
    text(
        c,
        host,
        format!("{} / {} • {}", a.category, a.origin, a.status),
        12.,
    );
    let toolbar = row(c, host);
    control(c, toolbar, "Open", Action::Inspect);
    control(c, toolbar, "Folder", Action::Folder);
    control(c, toolbar, "Copy name", Action::CopyName);
    control(c, toolbar, "Metadata / source", Action::Source);
    let file = a.file.as_ref();
    if file.is_some_and(|p| {
        p.extension()
            .is_some_and(|e| e.eq_ignore_ascii_case("gltf") || e.eq_ignore_ascii_case("glb"))
    }) || file.is_some_and(|p| p.extension().is_some_and(|e| e == "smmat"))
    {
        let viewport = node(
            c,
            host,
            Node {
                height: px(255.),
                min_height: px(255.),
                width: percent(100.),
                flex_shrink: 0.,
                ..default()
            },
        );
        s.viewport = Some(viewport);
        let tools = row(c, host);
        control(c, tools, "Frame", Action::Frame);
        control(c, tools, "Turntable", Action::Turntable);
        control(c, tools, "Play / pause", Action::Animate);
        control(c, tools, "Next clip", Action::Clip);
        text(c, host, "Right-drag to orbit • Wheel to zoom", 11.);
    } else if let Some(path) = file
        && image(path)
    {
        c.spawn((
            Thumb { path: path.clone() },
            ImageNode::default(),
            Node {
                width: percent(100.),
                height: px(235.),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(host),
        ));
    }
    if a.category == "Audio" {
        let tools = row(c, host);
        control(c, tools, "▶ Play", Action::Play);
        control(c, tools, "■ Stop", Action::Stop);
    }
    if a.category == "Videos" {
        text(
            c,
            host,
            "Open plays this video in your default media player.",
            12.,
        );
    }
    if a.owned() && a.index.is_some() {
        let tools = row(c, host);
        let input = property::input(c, tools, Field::Rename, &s.rename);
        c.entity(input).insert(Node {
            width: px(190.),
            height: px(27.),
            ..default()
        });
        control(c, tools, "Rename resource", Action::Rename);
        text(
            c,
            host,
            "Renaming changes the registered name; script references are not rewritten.",
            11.,
        );
    }
    text(
        c,
        host,
        format!(
            "File: {}",
            file.map(|p| p.display().to_string())
                .unwrap_or_else(|| "Metadata only".into())
        ),
        11.,
    );
    if let Some(index) = &a.index {
        text(c, host, format!("Index: {}", index.display()), 11.);
    }
    let data = if s.source {
        s.text.clone()
    } else {
        serde_json::to_string_pretty(&a.data).unwrap_or_default()
    };
    let truncated = data.chars().take(12000).collect::<String>();
    text(c, host, truncated, 12.);
}
pub(super) fn review(c: &mut Commands, s: &mut State) {
    if let Some(e) = s.review_host.take() {
        c.entity(e).try_despawn();
    }
    let Some(plan) = s.plan.as_ref() else {
        return;
    };
    let Some(parent) = s.host else {
        return;
    };
    let overlay = node(
        c,
        parent,
        Node {
            position_type: PositionType::Absolute,
            width: percent(100.),
            height: percent(100.),
            align_items: AlignItems::Center,
            justify_content: JustifyContent::Center,
            ..default()
        },
    );
    c.entity(overlay).insert((
        BackgroundColor(Color::srgba(0., 0., 0., 0.7)),
        GlobalZIndex(450),
        TabGroup::modal(),
    ));
    let panel = node(
        c,
        overlay,
        Node {
            width: percent(75.),
            height: percent(80.),
            padding: px(18.).all(),
            flex_direction: FlexDirection::Column,
            row_gap: px(10.),
            ..default()
        },
    );
    c.entity(panel).insert(BackgroundColor(PANEL));
    text(c, panel, "REVIEW ASSET OPERATION", 19.);
    text(
        c,
        panel,
        format!(
            "{} assets including dependencies • {:.1} MiB • {} conflicts",
            plan.assets,
            plan.bytes as f64 / 1048576.,
            plan.conflicts.len()
        ),
        14.,
    );
    text(
        c,
        panel,
        "Existing different files will never be overwritten. Removed assets are archived with registration backups. Script references are not rewritten.",
        12.,
    );
    let content = property::scroll_column(c, panel);
    for line in &plan.conflicts {
        text(c, content, format!("CONFLICT: {line}"), 13.);
    }
    for line in plan.lines.iter().take(1500) {
        text(c, content, line, 12.);
    }
    let buttons = row(c, panel);
    control(c, buttons, "Cancel", Action::Cancel);
    if plan.conflicts.is_empty() {
        control(c, buttons, "Confirm operation", Action::Confirm);
    }
    s.review_host = Some(overlay);
}

pub(super) fn message(c: &mut Commands, s: &mut State, title: &str, message: &str) {
    if let Some(e) = s.review_host.take() {
        c.entity(e).try_despawn();
    }
    let Some(parent) = s.host else {
        return;
    };
    let overlay = node(
        c,
        parent,
        Node {
            position_type: PositionType::Absolute,
            width: percent(100.),
            height: percent(100.),
            align_items: AlignItems::Center,
            justify_content: JustifyContent::Center,
            ..default()
        },
    );
    c.entity(overlay).insert((
        BackgroundColor(Color::srgba(0., 0., 0., 0.6)),
        GlobalZIndex(450),
        TabGroup::modal(),
    ));
    let panel = node(
        c,
        overlay,
        Node {
            width: px(620.),
            padding: px(20.).all(),
            flex_direction: FlexDirection::Column,
            row_gap: px(12.),
            ..default()
        },
    );
    c.entity(panel).insert(BackgroundColor(PANEL));
    text(c, panel, title, 21.);
    text(c, panel, message, 14.);
    control(c, panel, "Done", Action::Cancel);
    s.review_host = Some(overlay);
}
