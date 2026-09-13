use super::tabs::{editor, tab};
use super::{components::*, input::Action};
use crate::application::Session;
use bevy::{
    prelude::*,
    text::{EditableText, TextCursorStyle},
};
use scenemax_ide_ui::{
    button, label,
    panels::{PanelAxis, PanelHost, ResizablePanel, SplitterEdge, splitter, tool_panel},
    theme::*,
};
fn display_path(path: &std::path::Path) -> String {
    let path = path.to_string_lossy();
    if let Some(unc) = path.strip_prefix(r"\\?\UNC\") {
        format!(r"\\{unc}")
    } else {
        path.strip_prefix(r"\\?\").unwrap_or(&path).to_owned()
    }
}
pub(crate) fn setup(mut commands: Commands, session: Res<Session>) {
    commands.spawn(Camera2d);
    let root = commands
        .spawn((
            Node {
                width: percent(100.),
                height: percent(100.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BackgroundColor(BG),
        ))
        .id();
    super::chrome::menu_bar(&mut commands, root);
    super::titlebar::resize_edges(&mut commands, root);
    let recovery = commands
        .spawn((
            RecoveryPrompt,
            Node {
                display: Display::None,
                padding: px(8.).all(),
                align_items: AlignItems::Center,
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    commands.spawn((
        label(
            "Unsaved buffers from an earlier session are available.",
            14.,
        ),
        ChildOf(recovery),
    ));
    button(
        &mut commands,
        recovery,
        "Restore buffers",
        Action::RestoreRecovery,
    );
    button(
        &mut commands,
        recovery,
        "Discard recovery copies",
        Action::DiscardRecovery,
    );
    let project_bar = commands
        .spawn((
            super::chrome::Panel::Project,
            Node {
                display: Display::None,
                padding: px(8.).all(),
                column_gap: px(8.),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    commands.entity(project_bar).insert((
        Node {
            display: Display::None,
            position_type: PositionType::Absolute,
            top: percent(12.),
            left: percent(15.),
            width: percent(70.),
            height: percent(72.),
            flex_direction: FlexDirection::Column,
            padding: px(16.).all(),
            row_gap: px(8.),
            border: px(1.).all(),
            ..default()
        },
        BackgroundColor(PANEL),
        BorderColor::all(EDGE),
        GlobalZIndex(90),
    ));
    commands.spawn((label("Project Explorer", 20.), ChildOf(project_bar)));
    field(
        &mut commands,
        project_bar,
        Field::ProjectFilter,
        "",
        "Filter projects by name",
        400.,
    );
    super::projects::choices(&mut commands, project_bar, false);
    let project_actions = commands
        .spawn((
            Node {
                column_gap: px(6.),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(project_bar),
        ))
        .id();
    button(
        &mut commands,
        project_actions,
        "Refresh",
        Action::RefreshProjects,
    );
    commands.spawn((label("Project folder", 12.), ChildOf(project_bar)));
    commands.spawn((
        ProjectPath,
        Field::Project,
        EditableText::new(display_path(session.workspace.project().root())),
        Node {
            height: px(34.),
            flex_shrink: 0.,
            min_width: px(0.),
            padding: px(8.).all(),
            ..default()
        },
        TextFont {
            font: bevy::text::FontSource::SansSerif,
            font_size: FontSize::Px(14.),
            ..default()
        },
        TextColor(INK),
        TextCursorStyle {
            color: Color::WHITE,
            ..default()
        },
        BackgroundColor(PANEL),
        ChildOf(project_bar),
    ));
    let project_footer = commands
        .spawn((
            Node {
                justify_content: JustifyContent::FlexEnd,
                column_gap: px(8.),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(project_bar),
        ))
        .id();
    button(
        &mut commands,
        project_footer,
        "Open project folder",
        Action::OpenProject,
    );
    button(
        &mut commands,
        project_footer,
        "New project",
        Action::NewProject,
    );
    button(
        &mut commands,
        project_footer,
        "Close",
        super::chrome::ChromeAction::Dismiss,
    );
    let find_bar = commands
        .spawn((
            super::chrome::Panel::Search,
            Node {
                display: Display::None,
                padding: px(8.).all(),
                column_gap: px(6.),
                flex_shrink: 0.,
                flex_wrap: FlexWrap::Wrap,
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    field(&mut commands, find_bar, Field::Find, "", "Find text", 180.);
    button(&mut commands, find_bar, "Previous", Action::FindPrevious);
    button(&mut commands, find_bar, "Next", Action::FindNext);
    field(
        &mut commands,
        find_bar,
        Field::Replace,
        "",
        "Replace with",
        160.,
    );
    button(&mut commands, find_bar, "Replace", Action::Replace);
    button(&mut commands, find_bar, "Replace all", Action::ReplaceAll);
    field(&mut commands, find_bar, Field::Line, "1", "Line", 60.);
    button(&mut commands, find_bar, "Go", Action::GoToLine);
    button(&mut commands, find_bar, "Comment", Action::Comment);
    button(
        &mut commands,
        find_bar,
        "Find in project",
        Action::FindProject,
    );
    button(
        &mut commands,
        find_bar,
        "Close",
        super::chrome::ChromeAction::Dismiss,
    );
    let help = commands
        .spawn((
            super::chrome::Panel::Help,
            Node {
                display: Display::None,
                padding: px(12.).all(),
                align_items: AlignItems::Center,
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(root),
        ))
        .id();
    commands.spawn((
        label("SceneMax Studio · Rust / Bevy IDE preview", 13.),
        ChildOf(help),
    ));
    button(
        &mut commands,
        help,
        "Close",
        super::chrome::ChromeAction::Dismiss,
    );
    let body = commands
        .spawn((
            Node {
                flex_grow: 1.,
                min_height: px(0.),
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    let sidebar = commands
        .spawn((
            ResizablePanel::new(PanelAxis::Width, 280.),
            Node {
                width: px(280.),
                flex_shrink: 0.,
                flex_direction: FlexDirection::Column,
                overflow: Overflow::clip(),
                border: UiRect::right(px(1.)),
                ..default()
            },
            BorderColor::all(EDGE),
            BackgroundColor(PANEL),
            ChildOf(body),
        ))
        .id();
    let project_header = commands
        .spawn((
            Node {
                height: px(38.),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::SpaceBetween,
                padding: UiRect::horizontal(px(10.)),
                ..default()
            },
            ChildOf(sidebar),
        ))
        .id();
    commands.spawn((label("Project", 14.), ChildOf(project_header)));
    button(
        &mut commands,
        project_header,
        "+",
        super::chrome::ChromeAction::Panel(super::chrome::Panel::NewFile),
    );
    button(&mut commands, project_header, "Refresh", Action::Refresh);
    let new_file = commands
        .spawn((
            super::chrome::Panel::NewFile,
            Node {
                display: Display::None,
                flex_direction: FlexDirection::Column,
                padding: px(8.).all(),
                ..default()
            },
            ChildOf(sidebar),
        ))
        .id();
    field(
        &mut commands,
        new_file,
        Field::NewScript,
        "new_script.code",
        "Script name (relative to scripts/)",
        242.,
    );
    let file_actions = commands.spawn((Node::default(), ChildOf(new_file))).id();
    button(&mut commands, file_actions, "Create", Action::NewScript);
    button(&mut commands, file_actions, "Save copy", Action::SaveCopy);
    button(
        &mut commands,
        file_actions,
        "Close",
        super::chrome::ChromeAction::Dismiss,
    );
    field(
        &mut commands,
        sidebar,
        Field::Filter,
        "",
        "Filter project files",
        254.,
    );
    commands.spawn((SearchResults, Node { display: Display::None, height: px(180.), flex_shrink: 0., flex_direction: FlexDirection::Column, overflow: Overflow::scroll_y(), ..default() }, ChildOf(sidebar)))
        .observe(|event: On<Pointer<Scroll>>, mut query: Query<(&mut ScrollPosition, &ComputedNode), With<SearchResults>>| {
            if let Ok((mut scroll, node)) = query.get_mut(event.entity) {
                let scale = if event.unit == bevy::input::mouse::MouseScrollUnit::Line { 40. } else { 1. };
                scroll.y = (scroll.y-event.y*scale).clamp(0., ((node.content_size().y-node.size().y)*node.inverse_scale_factor()).max(0.));
            }
        });
    let tree_frame = commands
        .spawn((
            Node {
                display: Display::Grid,
                flex_grow: 1.,
                flex_basis: px(0.),
                min_height: px(0.),
                grid_template_columns: vec![
                    RepeatedGridTrack::flex(1, 1.),
                    RepeatedGridTrack::px(1, 12.),
                ],
                grid_template_rows: vec![RepeatedGridTrack::flex(1, 1.)],
                overflow: Overflow::clip(),
                ..default()
            },
            ChildOf(sidebar),
        ))
        .id();
    let browser = commands
        .spawn((
            Browser,
            PanelHost("project-tree"),
            Node {
                flex_direction: FlexDirection::Column,
                min_height: px(0.),
                min_width: px(0.),
                grid_row: GridPlacement::start(1),
                grid_column: GridPlacement::start(1),
                overflow: Overflow::scroll_y(),
                ..default()
            },
            ScrollPosition::default(),
            ChildOf(tree_frame),
        ))
        .id();
    commands.entity(browser).observe(
        move |mut event: On<Pointer<Scroll>>,
              mut query: Query<(&mut ScrollPosition, &ComputedNode)>| {
            // Pointer events bubble from labels/rows; scroll the viewport, not the hit child.
            if let Ok((mut scroll, node)) = query.get_mut(browser) {
                let dy = event.y
                    * if event.unit == bevy::input::mouse::MouseScrollUnit::Line {
                        40.
                    } else {
                        1.
                    };
                let max =
                    (node.content_size().y - node.size().y).max(0.) * node.inverse_scale_factor();
                scroll.y = (scroll.y - dy).clamp(0., max);
                event.propagate(false);
            }
        },
    );
    commands
        .spawn((
            Node {
                grid_row: GridPlacement::start(1),
                grid_column: GridPlacement::start(2),
                min_width: px(12.),
                ..default()
            },
            BackgroundColor(BG),
            bevy::ui_widgets::Scrollbar {
                orientation: bevy::ui_widgets::ControlOrientation::Vertical,
                target: browser,
                min_thumb_length: 24.,
            },
            ChildOf(tree_frame),
        ))
        .with_children(|parent| {
            parent.spawn((
                BackgroundColor(MUTED),
                bevy::ui_widgets::ScrollbarThumb {
                    border_radius: BorderRadius::all(px(4.)),
                    border: px(2.).all(),
                },
                BorderColor::all(BG),
            ));
        });
    splitter(
        &mut commands,
        body,
        sidebar,
        PanelAxis::Width,
        SplitterEdge::After,
    );
    let center = commands
        .spawn((
            Node {
                flex_direction: FlexDirection::Column,
                flex_grow: 1.,
                min_width: px(0.),
                ..default()
            },
            ChildOf(body),
        ))
        .id();
    let tabs = commands
        .spawn((
            Tabs,
            Node {
                min_height: px(28.),
                flex_shrink: 0.,
                overflow: Overflow::clip(),
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(center),
        ))
        .id();
    let editors = commands
        .spawn((
            Editors,
            PanelHost("script-editors"),
            Node {
                flex_grow: 1.,
                min_height: px(0.),
                overflow: Overflow::clip(),
                ..default()
            },
            ChildOf(center),
        ))
        .id();
    for (index, doc) in session.workspace.documents() {
        tab(&mut commands, tabs, index, doc);
        editor(
            &mut commands,
            editors,
            index,
            doc,
            session.workspace.active_id() == Some(index),
        );
    }
    commands.spawn((
        CaretLabel,
        label("Open a script to begin", 13.),
        Node {
            padding: UiRect::axes(px(12.), px(4.)),
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(root),
    ));
    let tab_close = commands
        .spawn((
            TabClosePrompt,
            Node {
                display: Display::None,
                align_items: AlignItems::Center,
                flex_wrap: FlexWrap::Wrap,
                padding: px(8.).all(),
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    commands.spawn((
        TabCloseCaption,
        label("Unsaved tab", 14.),
        ChildOf(tab_close),
    ));
    button(
        &mut commands,
        tab_close,
        "Save and close tab",
        Action::SaveCloseTab,
    );
    button(
        &mut commands,
        tab_close,
        "Discard tab edits",
        Action::DiscardTab,
    );
    button(&mut commands, tab_close, "Cancel", Action::CancelTab);
    let close = commands
        .spawn((
            ClosePrompt,
            Node {
                display: Display::None,
                align_items: AlignItems::Center,
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    commands.spawn((
        label(
            "Unsaved documents. Save all before closing, or discard edits:",
            15.,
        ),
        ChildOf(close),
    ));
    button(&mut commands, close, "Save all", Action::SaveAll);
    button(&mut commands, close, "Cancel", Action::CancelClose);
    button(
        &mut commands,
        close,
        "Discard edits and exit",
        Action::DiscardExit,
    );
    commands.spawn((
        Status,
        label(&session.status, 12.),
        Node {
            padding: UiRect::axes(px(12.), px(4.)),
            height: px(26.),
            flex_shrink: 0.,
            overflow: Overflow::clip(),
            ..default()
        },
        BackgroundColor(PANEL),
        ChildOf(root),
    ));
    let output = tool_panel(&mut commands, center, "run-output", "Run / Output");
    commands.entity(output.root).insert((
        super::labels::RunOutput,
        ResizablePanel::new(PanelAxis::Height, 170.),
        Node {
            display: Display::None,
            height: px(170.),
            flex_direction: FlexDirection::Column,
            flex_shrink: 0.,
            min_height: px(0.),
            overflow: Overflow::clip(),
            ..default()
        },
    ));
    let divider = splitter(
        &mut commands,
        center,
        output.root,
        PanelAxis::Height,
        SplitterEdge::Before,
    );
    commands.entity(divider).insert(super::labels::RunOutput);
    commands.entity(divider).insert(Node {
        display: Display::None,
        height: px(5.),
        flex_shrink: 0.,
        ..default()
    });
    // Place the divider immediately before the output; moving it never rebuilds panel content.
    commands.entity(center).insert_children(2, &[divider]);
    button(&mut commands, output.header, "Clear", Action::ClearConsole);
    let console = commands.spawn((ConsolePane, Node { width: percent(100.), height: percent(100.), overflow: Overflow::scroll_y(), padding: px(8.).all(), ..default() },
        BackgroundColor(PANEL), ScrollPosition::default(), ChildOf(output.content)))
        .observe(|event: On<Pointer<Scroll>>, mut panes: Query<(&mut ScrollPosition, &ComputedNode), With<ConsolePane>>| {
            if let Ok((mut scroll, node)) = panes.get_mut(event.entity) {
                let scale = if event.unit == bevy::input::mouse::MouseScrollUnit::Line { 40. } else { 1. };
                scroll.y = (scroll.y - event.y * scale).clamp(0., ((node.content_size().y-node.size().y)*node.inverse_scale_factor()).max(0.));
            }
        }).id();
    commands.spawn((
        ConsoleText,
        label("Projector output appears here", 13.),
        ChildOf(console),
    ));
}

fn field(
    commands: &mut Commands,
    parent: Entity,
    kind: Field,
    value: &str,
    caption: &str,
    width: f32,
) {
    let host = commands
        .spawn((
            Node {
                flex_direction: FlexDirection::Column,
                flex_shrink: 1.,
                min_width: px(0.),
                max_width: percent(100.),
                margin: px(3.).all(),
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    commands.spawn((label(caption, 12.), ChildOf(host)));
    commands.spawn((
        kind,
        EditableText::new(value),
        Node {
            width: px(width),
            max_width: percent(100.),
            min_width: px(0.),
            padding: px(6.).all(),
            ..default()
        },
        TextFont {
            font: bevy::text::FontSource::SansSerif,
            font_size: FontSize::Px(13.),
            ..default()
        },
        TextColor(INK),
        TextCursorStyle {
            color: Color::WHITE,
            ..default()
        },
        BackgroundColor(BG),
        ChildOf(host),
    ));
}
