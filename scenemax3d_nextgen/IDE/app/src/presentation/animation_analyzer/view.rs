use super::*;
use scenemax_ide_ui::{button, label, number, property, theme::*};
#[derive(Clone, Copy)]
pub(super) struct Parts {
    pub table: Entity,
    pub transport: Entity,
    pub viewport: Entity,
    pub status: Entity,
}
fn row(c: &mut Commands, p: Entity) -> Entity {
    c.spawn((
        Node {
            column_gap: px(5.),
            row_gap: px(4.),
            flex_wrap: FlexWrap::Wrap,
            flex_shrink: 0.,
            padding: px(4.).all(),
            ..default()
        },
        ChildOf(p),
    ))
    .id()
}
pub(super) fn build(c: &mut Commands, host: Entity) -> Parts {
    c.entity(host).insert(Node {
        width: percent(100.),
        height: percent(100.),
        flex_direction: FlexDirection::Column,
        min_height: px(0.),
        ..default()
    });
    let tools = row(c, host);
    c.entity(tools).insert(BackgroundColor(HEADER));
    c.spawn((label("MODEL ANIMATION ANALYZER", 12.), ChildOf(tools)));
    for (title, action) in [
        ("Save", Action::Save),
        ("Undo", Action::Undo),
        ("Redo", Action::Redo),
        ("Refresh", Action::Refresh),
    ] {
        button(c, tools, title, action);
    }
    let body = c
        .spawn((
            Node {
                flex_grow: 1.,
                min_height: px(0.),
                ..default()
            },
            ChildOf(host),
        ))
        .id();
    let left = c
        .spawn((
            Node {
                width: percent(36.),
                min_width: px(400.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(body),
        ))
        .id();
    c.spawn((label("Filter models", 11.), ChildOf(left)));
    property::input(c, left, Control("filter", String::new()), "");
    let table = property::scroll_column(c, left);
    let actions = row(c, left);
    button(c, actions, "Add range", Action::Add);
    button(c, actions, "Delete selected", Action::Delete);
    let right = c
        .spawn((
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            ChildOf(body),
        ))
        .id();
    let tools = row(c, right);
    for (title, action) in [
        ("Move", Action::Mode(0)),
        ("Rotate", Action::Mode(1)),
        ("Scale", Action::Mode(2)),
        ("Reset transform", Action::Reset),
        ("Reset view", Action::Fit),
    ] {
        button(c, tools, title, action);
    }
    c.spawn((
        label(
            "Right-drag orbit · Middle-drag pan · Wheel zoom · Left-drag axis gizmo",
            10.,
        ),
        ChildOf(right),
    ));
    let viewport = c
        .spawn((
            Node {
                width: percent(100.),
                flex_grow: 1.,
                min_height: px(100.),
                overflow: Overflow::clip(),
                ..default()
            },
            BackgroundColor(Color::srgb(0.08, 0.09, 0.11)),
            ChildOf(right),
        ))
        .id();
    let transport = c
        .spawn((
            Node {
                flex_direction: FlexDirection::Column,
                flex_shrink: 0.,
                row_gap: px(4.),
                padding: px(6.).all(),
                ..default()
            },
            ChildOf(right),
        ))
        .id();
    let status = c.spawn((label("Loading…", 11.), ChildOf(host))).id();
    Parts {
        table,
        transport,
        viewport,
        status,
    }
}
pub(super) fn table(c: &mut Commands, parent: Entity, s: &State) {
    c.spawn((label("Model", 12.), ChildOf(parent)));
    let options: Vec<_> = s.data["models"]
        .as_array()
        .into_iter()
        .flatten()
        .enumerate()
        .filter(|(_, m)| {
            m["name"]
                .as_str()
                .unwrap_or("")
                .to_lowercase()
                .contains(&s.filter.to_lowercase())
        })
        .map(|(i, m)| {
            (
                i.to_string(),
                m["name"].as_str().unwrap_or("Unnamed").to_owned(),
            )
        })
        .collect();
    property::dropdown_labeled(
        c,
        parent,
        Control("model", s.model.to_string()),
        &s.model.to_string(),
        &options,
    );
    if s.shared_catalog {
        c.spawn((
            label(
                "Shared model catalog · Saved ranges apply to all projects using these models",
                11.,
            ),
            ChildOf(parent),
        ));
    }
    c.spawn((label("Animation frame ranges", 12.), ChildOf(parent)));
    let names = s
        .source
        .as_ref()
        .map(|v| {
            v.timelines
                .iter()
                .map(|t| t.name.clone())
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();
    let heading = c
        .spawn((
            Node {
                display: Display::Grid,
                grid_template_columns: vec![
                    GridTrack::px(28.),
                    GridTrack::flex(2.),
                    GridTrack::flex(2.),
                    GridTrack::flex(1.),
                    GridTrack::flex(1.),
                ],
                column_gap: px(4.),
                width: percent(100.),
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    for title in ["", "Name", "Source animation", "Start", "End"] {
        c.spawn((label(title, 10.), ChildOf(heading)));
    }
    for (index, _) in s.data["models"][s.model]["animationFrameRanges"]
        .as_array()
        .into_iter()
        .flatten()
        .enumerate()
    {
        let r = c
            .spawn((
                Node {
                    display: Display::Grid,
                    grid_template_columns: vec![
                        GridTrack::px(28.),
                        GridTrack::flex(2.),
                        GridTrack::flex(2.),
                        GridTrack::flex(1.),
                        GridTrack::flex(1.),
                    ],
                    column_gap: px(4.),
                    width: percent(100.),
                    min_width: px(0.),
                    ..default()
                },
                ChildOf(parent),
            ))
            .id();
        c.entity(r).insert((
            RecordRow(index),
            BackgroundColor(if s.selected == Some(index) {
                SELECTED
            } else {
                PANEL
            }),
        ));
        button(c, r, &format!("{}", index + 1), Action::Select(index));
        for key in ["name", "sourceAnimation", "start", "end"] {
            let cell = c
                .spawn((
                    Node {
                        flex_direction: FlexDirection::Column,
                        min_width: px(0.),
                        ..default()
                    },
                    ChildOf(r),
                ))
                .id();
            let value = field_value(
                &s.data,
                &format!("/models/{}/animationFrameRanges/{index}/{key}", s.model),
            );
            let field = Field(
                format!("/models/{}/animationFrameRanges/{index}/{key}", s.model),
                value.clone(),
            );
            if s.model >= s.project_count {
                c.spawn((label(&value, 12.), ChildOf(cell)));
            } else if key == "sourceAnimation" {
                property::dropdown(c, cell, field, &value, &names);
            } else if key == "start" || key == "end" {
                number::input(c, cell, field, &value, 0., 1000., 1.);
            } else {
                property::input(c, cell, field, &value);
            }
        }
    }
    c.spawn((
        label(
            "Select a row, then Play range. Frame numbers follow the source keyframes.",
            11.,
        ),
        ChildOf(parent),
    ));
}
pub(super) fn transport(c: &mut Commands, parent: Entity, s: &State) {
    let timelines = s
        .source
        .as_ref()
        .map(|v| v.timelines.as_slice())
        .unwrap_or(&[]);
    let options: Vec<_> = timelines
        .iter()
        .enumerate()
        .map(|(i, t)| (i.to_string(), t.name.clone()))
        .collect();
    property::dropdown_labeled(
        c,
        parent,
        Control("animation", s.animation.to_string()),
        &s.animation.to_string(),
        &options,
    );
    let buttons = row(c, parent);
    for (title, action) in [
        ("Use full range", Action::UseFull),
        ("Play full", Action::Full),
        ("Play range", Action::Range),
        ("Pause / Resume", Action::Pause),
        ("Stop", Action::Stop),
    ] {
        button(c, buttons, title, action);
    }
    let max = timelines
        .get(s.animation)
        .map_or(0, |t| t.times.len().saturating_sub(1));
    for (key, title, value, limit, step) in [
        ("start", "Start frame", s.start as f64, max as f64, 1.),
        ("end", "End frame", s.end as f64, max as f64, 1.),
        ("seek", "Scrub frame", s.time as f64, max as f64, 0.1),
        ("speed", "Speed (%)", s.speed as f64, 200., 1.),
    ] {
        let r = row(c, parent);
        c.entity(r).insert(Node {
            display: Display::Grid,
            grid_template_columns: vec![
                GridTrack::px(90.),
                GridTrack::px(70.),
                GridTrack::flex(1.),
            ],
            align_items: AlignItems::Center,
            min_height: px(28.),
            width: percent(100.),
            column_gap: px(6.),
            ..default()
        });
        c.spawn((label(title, 11.), ChildOf(r)));
        let value = value.to_string();
        number::input(
            c,
            r,
            Control(key, value.clone()),
            &value,
            if key == "speed" { 1. } else { 0. },
            limit.max(1.),
            step,
        );
    }
}
