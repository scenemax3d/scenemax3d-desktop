use super::*;
use scenemax_ide_ui::{button, label, number, property, theme::*};
pub(super) struct Parts {
    pub inspector: Entity,
    pub viewport: Entity,
    pub status: Entity,
    pub animation: Entity,
    pub sockets: Entity,
}
fn row(c: &mut Commands, parent: Entity) -> Entity {
    c.spawn((
        Node {
            flex_wrap: FlexWrap::Wrap,
            column_gap: px(6.),
            row_gap: px(4.),
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(parent),
    ))
    .id()
}
fn title(c: &mut Commands, p: Entity, s: &str) {
    c.spawn((label(s, 12.), ChildOf(p)));
}
fn input(c: &mut Commands, p: Entity, data: &Value, key: &str) {
    let v = display(data.pointer(key).unwrap_or(&Value::Null));
    property::input(c, p, Field(key.into(), v.clone()), &v);
}
fn dropdown(c: &mut Commands, p: Entity, data: &Value, key: &str, options: &[String]) {
    let v = data.pointer(key).and_then(Value::as_str).unwrap_or("");
    property::dropdown(c, p, Field(key.into(), v.into()), v, options);
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
    title(c, toolbar, "WEAPON DESIGNER");
    for (caption, action) in [
        ("Save", Action::Save),
        ("Undo", Action::Undo),
        ("Redo", Action::Redo),
        ("Refresh models", Action::Refresh),
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
                width: px(355.),
                min_width: px(300.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(body),
        ))
        .id();
    let tabs = row(c, left);
    button(c, tabs, "Overview & postures", Action::Section(false));
    button(c, tabs, "Colliders", Action::Section(true));
    let inspector = property::scroll_column(c, left);
    let right = c
        .spawn((
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                flex_direction: FlexDirection::Column,
                row_gap: px(6.),
                padding: px(8.).all(),
                ..default()
            },
            ChildOf(body),
        ))
        .id();
    let tools = row(c, right);
    button(c, tools, "Move", Action::Mode(false));
    button(c, tools, "Rotate", Action::Mode(true));
    button(c, tools, "Reset view", Action::Frame);
    title(
        c,
        right,
        "Right-drag: orbit · Wheel: zoom · Left-drag colored axis: transform",
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
    let animation = row(c, right);
    let playback = row(c, right);
    title(c, playback, "Animation speed %");
    let speed = c
        .spawn((
            Node {
                width: px(140.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            ChildOf(playback),
        ))
        .id();
    number::input(c, speed, Control("speed"), "100", 1., 100., 1.);
    button(c, playback, "Stop / Resume", Action::Pause);
    let sockets = row(c, right);
    let status = c
        .spawn((
            label("Loading weapon…", 12.),
            Node {
                padding: px(6.).all(),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(host),
        ))
        .id();
    Parts {
        inspector,
        viewport,
        status,
        animation,
        sockets,
    }
}
pub(super) fn inspector(c: &mut Commands, p: Entity, s: &State) {
    let d = &s.data;
    if !s.colliders {
        title(c, p, "Weapon ID");
        input(c, p, d, "/id");
        let options = std::iter::once(String::new())
            .chain(s.models.iter().map(|m| m.name.clone()))
            .collect::<Vec<_>>();
        title(
            c,
            p,
            "Weapon model · changing model resets posture transforms",
        );
        dropdown(c, p, d, "/modelAssetId", &options);
        title(c, p, "Player preview model");
        dropdown(
            c,
            p,
            d,
            "/designerMetadata/previewPlayerModelAssetId",
            &options,
        );
        title(c, p, "POSTURES");
        for (i, v) in d["postures"].as_array().into_iter().flatten().enumerate() {
            let name = format!(
                "{}{} · {}{}",
                if i == s.posture { "● " } else { "" },
                v["id"].as_str().unwrap_or(""),
                v["name"].as_str().unwrap_or(""),
                if v["id"] == d["defaultPostureId"] {
                    " (default)"
                } else {
                    ""
                }
            );
            button(c, p, &name, Action::Select(false, i));
        }
        let r = row(c, p);
        for (caption, key) in [
            ("Add", "posture.add"),
            ("Duplicate", "posture.duplicate"),
            ("Delete", "posture.delete"),
            ("Set default", "posture.default"),
        ] {
            button(c, r, caption, Action::Edit(key));
        }
        let prefix = format!("/postures/{}", s.posture);
        for (caption, key) in [
            ("Posture ID", "id"),
            ("Name", "name"),
            (
                "Attach to · choose a bone below the preview or type a socket name",
                "attachmentPoint",
            ),
        ] {
            title(c, p, caption);
            input(c, p, d, &format!("{prefix}/{key}"));
        }
        button(c, p, "Clear attachment", Action::ClearAttachment);
        transforms(c, p, d, &prefix);
    } else {
        title(c, p, "COLLIDERS · attached to the selected posture");
        for (i, v) in d["colliders"].as_array().into_iter().flatten().enumerate() {
            button(
                c,
                p,
                &format!(
                    "{}{} · {}",
                    if i == s.collider { "● " } else { "" },
                    v["name"].as_str().unwrap_or(""),
                    v["shape"].as_str().unwrap_or("")
                ),
                Action::Select(true, i),
            );
        }
        let r = row(c, p);
        for (caption, key) in [
            ("Add box", "collider.box"),
            ("Add sphere", "collider.sphere"),
            ("Duplicate", "collider.duplicate"),
            ("Delete", "collider.delete"),
        ] {
            button(c, r, caption, Action::Edit(key));
        }
        if d["colliders"].get(s.collider).is_some() {
            let prefix = format!("/colliders/{}", s.collider);
            title(c, p, "Name");
            input(c, p, d, &format!("{prefix}/name"));
            title(c, p, "Shape");
            dropdown(
                c,
                p,
                d,
                &format!("{prefix}/shape"),
                &["box".into(), "sphere".into()],
            );
            transforms(c, p, d, &prefix);
        } else {
            title(c, p, "Add a collider to define the weapon's hit volume.");
        }
    }
}
fn transforms(c: &mut Commands, p: Entity, d: &Value, prefix: &str) {
    for (caption, key, min, max, step) in [
        ("Position", "offset", -999., 999., 0.01),
        ("Rotation (degrees)", "rotation", -360., 360., 1.),
        ("Scale", "scale", 0.01, 999., 0.01),
    ] {
        title(c, p, caption);
        let r = row(c, p);
        for axis in ["X", "Y", "Z"] {
            let col = c
                .spawn((
                    Node {
                        width: percent(31.),
                        flex_direction: FlexDirection::Column,
                        ..default()
                    },
                    ChildOf(r),
                ))
                .id();
            title(c, col, axis);
            let path = format!("{prefix}/transform/{key}{axis}");
            let v = display(d.pointer(&path).unwrap_or(&Value::Null));
            number::input(c, col, Field(path, v.clone()), &v, min, max, step);
        }
    }
}
