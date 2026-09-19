use super::*;
#[derive(Clone, Copy)]
pub(super) struct Parts {
    pub background: Entity,
    pub frame_background: Entity,
    pub props: Entity,
    pub sheet: Entity,
    pub cells: Entity,
    pub selected: Entity,
    pub frame: Entity,
    pub frame_canvas: Entity,
    pub status: Entity,
    pub info: Entity,
    pub film: Entity,
}
#[derive(Component)]
struct Tool;
pub(super) fn area(c: &mut Commands, p: Entity, node: Node) -> Entity {
    c.spawn((node, BackgroundColor(PANEL), ChildOf(p))).id()
}
pub(super) fn value(v: &Value, key: &str) -> String {
    v[key]
        .as_str()
        .map(str::to_owned)
        .unwrap_or_else(|| v[key].to_string())
}
pub(super) fn caption(key: &str, value: &str) -> String {
    match (key, value) {
        ("mode", "loop") => "Loop",
        ("mode", "once") => "Play once",
        ("mode", "pingpong") => "Ping-pong",
        ("filter", "nearest") => "Pixel perfect",
        ("filter", "linear") => "Smooth",
        _ => value,
    }
    .into()
}
fn marker(host: Entity, key: &'static str, v: &Value) -> Field {
    Field {
        host,
        key,
        observed: value(v, key),
        invalid: false,
    }
}
fn control(c: &mut Commands, p: Entity, h: Entity, title: &str, action: Action) -> Entity {
    let paths: Vec<Vec<Vec2>> = match &action {
        Action::Play => vec![vec![
            Vec2::new(5., 3.),
            Vec2::new(16., 10.),
            Vec2::new(5., 17.),
            Vec2::new(5., 3.),
        ]],
        Action::Pause => vec![
            vec![Vec2::new(6., 3.), Vec2::new(6., 17.)],
            vec![Vec2::new(14., 3.), Vec2::new(14., 17.)],
        ],
        Action::Stop => vec![vec![
            Vec2::new(4., 4.),
            Vec2::new(16., 4.),
            Vec2::new(16., 16.),
            Vec2::new(4., 16.),
            Vec2::new(4., 4.),
        ]],
        Action::Step(delta) => {
            let flip = |x: f32| if *delta < 0 { 20. - x } else { x };
            vec![
                vec![
                    Vec2::new(flip(4.), 4.),
                    Vec2::new(flip(13.), 10.),
                    Vec2::new(flip(4.), 16.),
                ],
                vec![Vec2::new(flip(16.), 3.), Vec2::new(flip(16.), 17.)],
            ]
        }
        _ => Vec::new(),
    };
    let tip = match &action {
        Action::Step(n) if *n < 0 => "Previous frame",
        Action::Step(_) => "Next frame",
        _ => title,
    };
    let e = if paths.is_empty() {
        button(c, p, title, Tool)
    } else {
        scenemax_ide_ui::icons::button(c, p, tip, &paths, Tool)
    };
    c.entity(e)
        .observe(move |mut e: On<Pointer<Click>>, mut s: ResMut<State>| {
            if e.button == PointerButton::Primary {
                e.propagate(false);
                s.actions.push((h, action.clone()));
            }
        });
    e
}
fn numeric(
    c: &mut Commands,
    p: Entity,
    h: Entity,
    v: &Value,
    title: &str,
    key: &'static str,
    range: [f64; 3],
) {
    property::heading(c, p, title);
    scenemax_ide_ui::number::input(
        c,
        p,
        marker(h, key, v),
        &value(v, key),
        range[0],
        range[1],
        range[2],
    );
}
fn column() -> Node {
    Node {
        flex_direction: FlexDirection::Column,
        min_width: px(0.),
        min_height: px(0.),
        ..default()
    }
}
fn bar() -> Node {
    Node {
        align_items: AlignItems::Center,
        column_gap: px(5.),
        padding: px(4.).all(),
        flex_shrink: 0.,
        flex_wrap: FlexWrap::Wrap,
        ..default()
    }
}
pub(super) fn build(c: &mut Commands, h: Entity, v: &Value) -> Parts {
    let root = area(
        c,
        h,
        Node {
            width: percent(100.),
            height: percent(100.),
            ..column()
        },
    );
    let top = area(c, root, bar());
    control(c, top, h, "Browse…", Action::Browse);
    control(c, top, h, "Load / Reload", Action::Load);
    let input = property::input(c, top, marker(h, "source", v), &value(v, "source"));
    c.entity(input).insert(Node {
        flex_grow: 1.,
        min_width: px(150.),
        height: px(26.),
        padding: px(4.).all(),
        ..default()
    });
    control(c, top, h, "Import sprite", Action::Import);
    control(c, top, h, "Properties", Action::Properties);
    let body = area(
        c,
        root,
        Node {
            flex_grow: 1.,
            min_height: px(0.),
            ..default()
        },
    );
    let props = area(
        c,
        body,
        Node {
            width: px(270.),
            flex_shrink: 0.,
            height: percent(100.),
            ..column()
        },
    );
    let form = property::scroll_column(c, props);
    property::heading(c, form, "Asset name");
    property::input(c, form, marker(h, "name", v), &value(v, "name"));
    property::heading(c, form, "SHEET LAYOUT");
    for (title, key, range) in [
        ("Columns", "cols", [1., 64., 1.]),
        ("Rows", "rows", [1., 64., 1.]),
        (
            "Cell width · pixels (0 = automatic)",
            "pixelWidth",
            [0., 1024., 1.],
        ),
        (
            "Cell height · pixels (0 = automatic)",
            "pixelHeight",
            [0., 1024., 1.],
        ),
        ("Horizontal margin · pixels", "marginX", [0., 128., 1.]),
        ("Vertical margin · pixels", "marginY", [0., 128., 1.]),
        ("Horizontal spacing · pixels", "spacingX", [0., 64., 1.]),
        ("Vertical spacing · pixels", "spacingY", [0., 64., 1.]),
    ] {
        numeric(c, form, h, v, title, key, range);
    }
    property::heading(c, form, "GAME DISPLAY SIZE");
    numeric(
        c,
        form,
        h,
        v,
        "Width · game units",
        "frameWidth",
        [0.01, 10., 0.01],
    );
    numeric(
        c,
        form,
        h,
        v,
        "Height · game units (0 = preserve aspect)",
        "frameHeight",
        [0., 10., 0.01],
    );
    c.spawn((
        label(
            "Margins and gutters are removed on import.
All frames are imported in row-major order.",
            11.,
        ),
        ChildOf(form),
    ));
    let center = area(
        c,
        body,
        Node {
            flex_grow: 1.,
            height: percent(100.),
            ..column()
        },
    );
    let tools = area(c, center, bar());
    let viewport = area(
        c,
        center,
        Node {
            flex_grow: 1.,
            min_height: px(100.),
            overflow: Overflow::clip(),
            ..default()
        },
    );
    let sheet = area(
        c,
        viewport,
        Node {
            position_type: PositionType::Absolute,
            width: px(256.),
            height: px(256.),
            ..default()
        },
    );
    c.entity(sheet).insert((
        BackgroundColor(Color::NONE),
        ImageNode::default(),
        scenemax_ide_ui::canvas::Canvas::new(viewport, 256., 256.),
    ));
    scenemax_ide_ui::canvas::install_navigation(c, sheet, viewport, h, tools);
    property::checkbox(
        c,
        tools,
        marker(h, "grid", v),
        "Grid",
        v["grid"].as_bool().unwrap_or(true),
    );
    let filter_parent = area(
        c,
        tools,
        Node {
            width: px(140.),
            ..column()
        },
    );
    let options = [
        ("nearest".into(), "Pixel perfect".into()),
        ("linear".into(), "Smooth".into()),
    ];
    property::dropdown_labeled(
        c,
        filter_parent,
        marker(h, "filter", v),
        v["filter"].as_str().unwrap_or("nearest"),
        &options,
    );
    let cells = area(
        c,
        sheet,
        Node {
            position_type: PositionType::Absolute,
            width: percent(100.),
            height: percent(100.),
            ..default()
        },
    );
    c.entity(cells)
        .insert((BackgroundColor(Color::NONE), Pickable::IGNORE));
    let selected = area(
        c,
        sheet,
        Node {
            position_type: PositionType::Absolute,
            border: px(2.).all(),
            ..default()
        },
    );
    c.entity(selected).insert((
        BackgroundColor(Color::NONE),
        BorderColor::all(Color::srgb(1., 0.72, 0.12)),
        Pickable::IGNORE,
    ));
    c.entity(sheet).observe(
        move |mut e: On<Pointer<Click>>,
              q: Query<(&ComputedNode, &UiGlobalTransform)>,
              states: Query<&Import>,
              mut actions: ResMut<State>| {
            if e.button != PointerButton::Primary {
                return;
            }
            e.propagate(false);
            if let Ok(s) = states.get(h)
                && let (Some(layout), Some(p)) = (s.layout, &s.prepared)
                && let Ok((node, transform)) = q.get(sheet)
                && let Some(inverse) = transform.try_inverse()
            {
                let local = inverse
                    .transform_point2(e.pointer_location.position / node.inverse_scale_factor())
                    + node.size() * 0.5;
                let uv = local / node.size();
                if let Some(index) = layout.hit(uv.x * p.width as f32, uv.y * p.height as f32) {
                    actions.actions.push((h, Action::Select(index)));
                }
            }
        },
    );
    let film = area(
        c,
        center,
        Node {
            height: px(82.),
            flex_shrink: 0.,
            overflow: Overflow::scroll_x(),
            column_gap: px(5.),
            padding: px(5.).all(),
            ..default()
        },
    );
    c.entity(film).observe(
        move |mut e: On<Pointer<Scroll>>, mut q: Query<&mut ScrollPosition>| {
            e.propagate(false);
            if let Ok(mut s) = q.get_mut(film) {
                s.x = (s.x - (e.x + e.y) * 30.).max(0.);
            }
        },
    );
    let right = area(
        c,
        body,
        Node {
            width: px(245.),
            height: percent(100.),
            flex_shrink: 0.,
            padding: px(8.).all(),
            ..column()
        },
    );
    let right = property::scroll_column(c, right);
    c.spawn((label("ANIMATION PREVIEW", 12.), ChildOf(right)));
    let frame_tools = area(c, right, bar());
    let frame_view = area(
        c,
        right,
        Node {
            height: px(260.),
            flex_shrink: 0.,
            overflow: Overflow::clip(),
            ..default()
        },
    );
    let frame_canvas = area(
        c,
        frame_view,
        Node {
            position_type: PositionType::Absolute,
            ..default()
        },
    );
    c.entity(frame_canvas).insert(BackgroundColor(Color::NONE));
    c.entity(frame_canvas)
        .insert(scenemax_ide_ui::canvas::Canvas::new(frame_view, 128., 128.));
    scenemax_ide_ui::canvas::install_navigation(
        c,
        frame_canvas,
        frame_view,
        frame_view,
        frame_tools,
    );
    let frame = c
        .spawn((
            ImageNode::default(),
            Node {
                width: percent(100.),
                height: percent(100.),
                ..default()
            },
            ChildOf(frame_canvas),
            Pickable::IGNORE,
        ))
        .id();
    let info = c
        .spawn((label("Load an image to begin", 12.), ChildOf(right)))
        .id();
    let transport = area(c, right, bar());
    for (title, action) in [
        ("◀", Action::Step(-1)),
        ("Play", Action::Play),
        ("Pause", Action::Pause),
        ("Stop", Action::Stop),
        ("▶", Action::Step(1)),
    ] {
        control(c, transport, h, title, action);
    }
    numeric(
        c,
        right,
        h,
        v,
        "Preview frames per second",
        "fps",
        [1., 60., 1.],
    );
    numeric(
        c,
        right,
        h,
        v,
        "First frame (zero based)",
        "first",
        [0., 255., 1.],
    );
    numeric(
        c,
        right,
        h,
        v,
        "Last frame (zero based)",
        "last",
        [0., 255., 1.],
    );
    control(c, right, h, "Use all frames", Action::AllFrames);
    let modes = [
        ("loop".into(), "Loop".into()),
        ("once".into(), "Play once".into()),
        ("pingpong".into(), "Ping-pong".into()),
    ];
    property::dropdown_labeled(
        c,
        right,
        marker(h, "mode", v),
        v["mode"].as_str().unwrap_or("loop"),
        &modes,
    );
    c.spawn((
        label(
            "Playback range, FPS and filtering are
preview settings. Game playback is
controlled by your scripts.",
            11.,
        ),
        ChildOf(right),
    ));
    let status = c
        .spawn((
            label("Choose a PNG or JPEG sprite sheet", 12.),
            Node {
                padding: px(5.).all(),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    Parts {
        background: viewport,
        frame_background: frame_view,
        props,
        sheet,
        cells,
        selected,
        frame,
        frame_canvas,
        status,
        info,
        film,
    }
}
pub(super) fn thumbnail(
    c: &mut Commands,
    p: Entity,
    h: Entity,
    index: u32,
    image: Handle<Image>,
    rect: Rect,
) {
    let e = control(c, p, h, &index.to_string(), Action::Select(index));
    c.entity(e).insert(Node {
        width: px(64.),
        height: px(68.),
        flex_shrink: 0.,
        flex_direction: FlexDirection::Column,
        align_items: AlignItems::Center,
        ..default()
    });
    c.spawn((
        ImageNode::new(image).with_rect(rect),
        Node {
            width: px(44.),
            height: px(44.),
            ..default()
        },
        Pickable::IGNORE,
        ChildOf(e),
    ));
}
