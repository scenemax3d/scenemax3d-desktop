use super::*;
use scenemax_ide_ui::{label, property, theme::*};
use scenemax_ui_input::Button;
pub(super) struct Parts {
    pub viewport: Entity,
    pub code: Entity,
    pub status: Entity,
    pub hint: Entity,
    pub play: Entity,
    pub loop_label: Entity,
    pub trail_label: Entity,
}
fn node(c: &mut Commands, p: Entity, n: Node) -> Entity {
    c.spawn((n, ChildOf(p))).id()
}
fn title(c: &mut Commands, p: Entity, t: &str) -> Entity {
    c.spawn((label(t, 12.), ChildOf(p))).id()
}
fn row(c: &mut Commands, p: Entity) -> Entity {
    node(
        c,
        p,
        Node {
            column_gap: px(6.),
            row_gap: px(4.),
            flex_wrap: FlexWrap::Wrap,
            flex_shrink: 0.,
            ..default()
        },
    )
}
fn button(c: &mut Commands, p: Entity, t: &str, a: Action) -> Entity {
    let e = scenemax_ide_ui::button(c, p, t, Name::new(t.to_owned()));
    c.entity(e)
        .observe(move |_: On<PointerClick>, mut s: ResMut<State>| s.actions.push(a));
    e
}
fn button_label(c: &mut Commands, p: Entity, t: &str, a: Action) -> Entity {
    let e = c
        .spawn((
            Button,
            Node {
                padding: UiRect::axes(px(10.), px(5.)),
                ..default()
            },
            BackgroundColor(HEADER),
            scenemax_ide_ui::ButtonSurface(HEADER),
            ChildOf(p),
        ))
        .id();
    c.entity(e)
        .observe(move |_: On<PointerClick>, mut s: ResMut<State>| s.actions.push(a));
    title(c, e, t)
}
fn display(value: &Value) -> String {
    if let Some(text) = value.as_str() {
        return text.into();
    }
    if let Some(n) = value.as_f64() {
        return (n as f32).to_string();
    }
    value.to_string()
}
fn field(
    c: &mut Commands,
    p: Entity,
    data: &Value,
    key: &str,
    caption: &str,
    choices: Option<&[&str]>,
) {
    let r = node(
        c,
        p,
        Node {
            column_gap: px(7.),
            align_items: AlignItems::Center,
            flex_shrink: 0.,
            ..default()
        },
    );
    c.entity(r).insert(Relevance(key.to_owned()));
    let l = title(c, r, caption);
    c.entity(l).insert(Node {
        width: px(123.),
        flex_shrink: 0.,
        ..default()
    });
    let holder = node(
        c,
        r,
        Node {
            flex_grow: 1.,
            min_width: px(0.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    let v = data.pointer(key).unwrap_or(&Value::Null);
    let text = display(v);
    if let Some(options) = choices {
        property::dropdown(
            c,
            holder,
            Field(key.into(), text.clone()),
            &text,
            &options.iter().map(|s| s.to_string()).collect::<Vec<_>>(),
        );
    } else if v.is_number() {
        let (min, max, step) = match key {
            "/restitution" | "/blend" => (0., 1., 0.01),
            "/angle" => (-89., 89., 1.),
            "/mass" => (0.1, 20., 0.1),
            "/duration" => (0.1, 30., 0.1),
            "/force_duration" | "/stop_after" => (0., 10., 0.1),
            "/drag" | "/friction" => (0., 2., 0.01),
            _ => (0., 60., 0.1),
        };
        scenemax_ide_ui::number::input(
            c,
            holder,
            Field(key.into(), text.clone()),
            &text,
            min,
            max,
            step,
        );
    } else {
        property::input(c, holder, Field(key.into(), text.clone()), &text);
    }
}
fn vector(c: &mut Commands, p: Entity, data: &Value, key: &str, caption: &str) {
    let group = node(
        c,
        p,
        Node {
            flex_direction: FlexDirection::Column,
            flex_shrink: 0.,
            ..default()
        },
    );
    c.entity(group).insert(Relevance(format!("/{key}")));
    title(c, group, caption);
    let r = row(c, group);
    for (i, axis) in ["X", "Y", "Z"].iter().enumerate() {
        let col = node(
            c,
            r,
            Node {
                width: percent(31.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
        );
        title(c, col, axis);
        let path = format!("/{key}/{i}");
        let text = display(data.pointer(&path).unwrap_or(&Value::Null));
        property::input(c, col, Field(path, text.clone()), &text);
    }
}
pub(super) fn build(c: &mut Commands, data: &Value) -> (Entity, Parts) {
    let host = c
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                width: percent(100.),
                height: percent(100.),
                padding: UiRect::axes(px(22.), px(45.)),
                ..default()
            },
            BackgroundColor(Color::srgba(0., 0., 0., 0.65)),
            GlobalZIndex(180),
            bevy::input_focus::tab_navigation::TabGroup::modal(),
        ))
        .id();
    let panel = node(
        c,
        host,
        Node {
            width: percent(100.),
            height: percent(100.),
            min_height: px(0.),
            flex_direction: FlexDirection::Column,
            padding: px(10.).all(),
            row_gap: px(8.),
            ..default()
        },
    );
    c.entity(panel).insert(BackgroundColor(PANEL));
    let header = row(c, panel);
    title(
        c,
        header,
        "PHYSICS SIMULATION  |  SceneMax choreographic programming language",
    );
    button(c, header, "Close", Action::Close);
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
    let left = node(
        c,
        body,
        Node {
            width: px(340.),
            min_width: px(290.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    let controls = property::scroll_column(c, left);
    title(c, controls, "MOTION");
    field(
        c,
        controls,
        data,
        "/command",
        "Command",
        Some(scenemax_ide_core::physics::COMMANDS),
    );
    field(
        c,
        controls,
        data,
        "/direction",
        "Aim / direction",
        Some(scenemax_ide_core::physics::DIRECTIONS),
    );
    field(c, controls, data, "/power", "Power / speed", None);
    field(
        c,
        controls,
        data,
        "/arc",
        "Throw arc",
        Some(scenemax_ide_core::physics::ARCS),
    );
    field(c, controls, data, "/angle", "Angle (degrees)", None);
    field(c, controls, data, "/blend", "Custom arc (0-1)", None);
    vector(
        c,
        controls,
        data,
        "vector",
        "Vector: velocity / impulse / force / torque",
    );
    vector(c, controls, data, "spin", "Throw spin (rad/s; 0 = none)");
    field(
        c,
        controls,
        data,
        "/force_duration",
        "Force seconds (0=on)",
        None,
    );
    field(c, controls, data, "/stop_after", "Stop after (s)", None);
    title(c, controls, "BODY AND WORLD");
    field(
        c,
        controls,
        data,
        "/shape",
        "Shape",
        Some(&["Box", "Sphere"]),
    );
    for (k, t) in [
        ("object", "Body name"),
        ("target", "Target name"),
        ("mass", "Mass (kg)"),
        ("drag", "Damping"),
        ("gravity", "Gravity (m/s2)"),
        ("restitution", "Restitution (0-1)"),
        ("friction", "Friction"),
        ("duration", "Preview duration (s)"),
    ] {
        field(c, controls, data, &format!("/{k}"), t, None);
    }
    vector(c, controls, data, "position", "Start position (m)");
    vector(c, controls, data, "destination", "Target position (m)");
    let right = node(
        c,
        body,
        Node {
            flex_grow: 1.,
            min_width: px(0.),
            flex_direction: FlexDirection::Column,
            row_gap: px(6.),
            ..default()
        },
    );
    let tools = row(c, right);
    let play = button_label(c, tools, "Play", Action::Play);
    button(c, tools, "Step 1/64 s", Action::Step);
    button(c, tools, "Reset", Action::Reset);
    button(c, tools, "Frame", Action::Frame);
    let loop_label = button_label(c, tools, "Loop: off", Action::Loop);
    let trail_label = button_label(c, tools, "Trail: on", Action::Trail);
    title(
        c,
        right,
        "Drag to orbit | Wheel to zoom | Blue: body | Red: target | Green: measured trail",
    );
    let hint = title(c, right, "");
    let viewport = node(
        c,
        right,
        Node {
            flex_grow: 1.,
            min_height: px(130.),
            ..default()
        },
    );
    c.entity(viewport).insert(BackgroundColor(BG));
    let status = title(c, right, "Preparing physics world...");
    let copy = row(c, right);
    title(c, copy, "GENERATED SCENEMAX CODE");
    button(c, copy, "Copy snippet", Action::Snippet);
    button(c, copy, "Copy full example", Action::Example);
    let code_host = node(
        c,
        right,
        Node {
            height: px(105.),
            min_height: px(70.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    c.entity(code_host).insert(BackgroundColor(BG));
    let scroll = property::scroll_column(c, code_host);
    let code = title(c, scroll, "");
    title(
        c,
        right,
        "World-space vectors. Throws bake the current target into velocity. Changes reset the simulation.",
    );
    (
        host,
        Parts {
            viewport,
            code,
            status,
            hint,
            play,
            loop_label,
            trail_label,
        },
    )
}
