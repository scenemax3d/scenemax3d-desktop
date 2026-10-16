use super::*;
use scenemax_ide_ui::{button, label, number, property, theme::*};
pub(super) struct Parts {
    pub inspector: Entity,
    pub viewport: Entity,
    pub status: Entity,
}
fn row(c: &mut Commands, p: Entity) -> Entity {
    c.spawn((
        Node {
            flex_wrap: FlexWrap::Wrap,
            column_gap: px(6.),
            row_gap: px(4.),
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(p),
    ))
    .id()
}
fn title(c: &mut Commands, p: Entity, s: &str) {
    c.spawn((label(s, 12.), ChildOf(p)));
}
pub(super) fn caption(key: &str, value: &str) -> String {
    if key == "/motionType" {
        motion::TYPES
            .iter()
            .find(|(k, _)| *k == value)
            .map(|(_, l)| l.to_string())
            .unwrap_or(value.into())
    } else {
        value.into()
    }
}
pub(super) fn build(c: &mut Commands, host: Entity) -> Parts {
    c.entity(host).insert(Node {
        width: percent(100.),
        height: percent(100.),
        min_height: px(0.),
        flex_direction: FlexDirection::Column,
        ..default()
    });
    let toolbar = row(c, host);
    c.entity(toolbar).insert(BackgroundColor(HEADER));
    title(c, toolbar, "THROW MOTION DESIGNER");
    for (caption, action) in [
        ("Save", Action::Save),
        ("Undo", Action::Undo),
        ("Redo", Action::Redo),
    ] {
        button(c, toolbar, caption, action);
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
                width: px(350.),
                min_width: px(290.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(body),
        ))
        .id();
    let inspector = property::scroll_column(c, left);
    let right = c
        .spawn((
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                flex_direction: FlexDirection::Column,
                row_gap: px(7.),
                padding: px(8.).all(),
                ..default()
            },
            ChildOf(body),
        ))
        .id();
    let tools = row(c, right);
    for (caption, action) in [
        ("Play", Action::Play),
        ("Step", Action::Step),
        ("Reset", Action::Reset),
        ("Reset view", Action::Frame),
        ("Trajectory: on", Action::Trajectory),
        ("Samples: on", Action::Samples),
    ] {
        button(c, tools, caption, action);
    }
    let targets = row(c, right);
    for (caption, key, value, min, max) in [
        ("Target distance", "distance", "12", 2., 40.),
        ("Target height", "height", "0", -5., 12.),
    ] {
        let col = c
            .spawn((
                Node {
                    width: px(200.),
                    flex_direction: FlexDirection::Column,
                    ..default()
                },
                ChildOf(targets),
            ))
            .id();
        title(c, col, caption);
        number::input(c, col, Control(key), value, min, max, 1.);
    }
    title(
        c,
        right,
        "Drag: orbit · Wheel: zoom · Blue: start · Red: target · Green: velocity",
    );
    let viewport = c
        .spawn((
            Node {
                flex_grow: 1.,
                min_height: px(100.),
                ..default()
            },
            BackgroundColor(BG),
            ChildOf(right),
        ))
        .id();
    let status = c.spawn((label("Preview", 12.), ChildOf(right))).id();
    title(
        c,
        right,
        "Preview shows the trajectory. Run the game to test collisions with your scene.",
    );
    Parts {
        inspector,
        viewport,
        status,
    }
}
pub(super) fn inspector(c: &mut Commands, p: Entity, data: &Value) {
    title(c, p, "OVERVIEW");
    for (caption, key) in [("Motion ID", "/id"), ("Name", "/displayName")] {
        title(c, p, caption);
        let v = display(&data[key.trim_start_matches('/')]);
        property::input(c, p, Field(key.into(), v.clone()), &v);
    }
    title(c, p, "Motion type");
    let kind = data["motionType"].as_str().unwrap_or("target_arc");
    let options = motion::TYPES
        .iter()
        .map(|(k, l)| (k.to_string(), l.to_string()))
        .collect::<Vec<_>>();
    property::dropdown_labeled(
        c,
        p,
        Field("/motionType".into(), kind.into()),
        kind,
        &options,
    );
    title(c, p, "MOTION");
    for f in motion::fields(kind) {
        title(
            c,
            p,
            if kind == "physics" && f.key == "launchAngle" {
                "Upward angle"
            } else {
                f.label
            },
        );
        let key = format!("/parameters/{}", f.key);
        let v = display(data.pointer(&key).unwrap_or(&Value::Null));
        let marker = Field(key, v.clone());
        if f.choices.is_empty() {
            number::input(c, p, marker, &v, f.min.into(), f.max.into(), f.step.into());
        } else {
            property::dropdown(
                c,
                p,
                marker,
                &v,
                &f.choices.iter().map(|s| s.to_string()).collect::<Vec<_>>(),
            );
        }
    }
}
