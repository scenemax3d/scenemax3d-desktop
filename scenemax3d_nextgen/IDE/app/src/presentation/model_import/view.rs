use super::*;
#[derive(Component)]
struct CameraTool;
#[derive(Clone, Copy)]
pub(super) struct Parts {
    pub viewport: Entity,
    pub clips: Entity,
    pub status: Entity,
    pub timeline: Entity,
    pub entries: Entity,
}
#[derive(Component)]
pub(super) struct ClipPick(pub Entity);
#[derive(Component)]
pub(super) struct Scrub(pub Entity);
fn area(commands: &mut Commands, parent: Entity, node: Node) -> Entity {
    commands
        .spawn((node, BackgroundColor(PANEL), ChildOf(parent)))
        .id()
}
fn marker(host: Entity, path: &str, v: &Value) -> Field {
    let value = v.pointer(path).cloned().unwrap_or(Value::Null);
    Field {
        host,
        path: path.into(),
        invalid: false,
        observed: value
            .as_str()
            .map(str::to_owned)
            .unwrap_or_else(|| value.to_string()),
    }
}
#[allow(clippy::too_many_arguments)] // Declarative numeric field builder.
fn number(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    v: &Value,
    path: &str,
    min: f64,
    max: f64,
    step: f64,
) {
    let f = marker(host, path, v);
    let value = f.observed.clone();
    scenemax_ide_ui::number::input(commands, parent, f, &value, min, max, step);
}
#[allow(clippy::too_many_arguments)] // Three axes share the same numeric bounds.
fn row(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    v: &Value,
    title: &str,
    paths: [&str; 3],
    min: f64,
    max: f64,
    step: f64,
) {
    property::heading(commands, parent, title);
    let row = area(
        commands,
        parent,
        Node {
            column_gap: px(5.),
            ..default()
        },
    );
    for (axis, path) in ["X", "Y", "Z"].into_iter().zip(paths) {
        let column = area(
            commands,
            row,
            Node {
                flex_grow: 1.,
                flex_basis: px(0.),
                min_width: px(0.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
        );
        commands.spawn((label(axis, 11.), ChildOf(column)));
        number(commands, column, host, v, path, min, max, step);
    }
}
fn check(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    v: &Value,
    path: &str,
    title: &str,
) {
    property::checkbox(
        commands,
        parent,
        marker(host, path, v),
        title,
        v.pointer(path).and_then(Value::as_bool).unwrap_or(false),
    );
}
pub(super) fn build(commands: &mut Commands, host: Entity, v: &Value) -> Parts {
    let root = area(
        commands,
        host,
        Node {
            width: percent(100.),
            height: percent(100.),
            min_width: px(0.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    let bar = area(
        commands,
        root,
        Node {
            align_items: AlignItems::Center,
            column_gap: px(5.),
            padding: px(4.).all(),
            flex_shrink: 0.,
            ..default()
        },
    );
    control(commands, bar, host, "Browse…", Action::Browse);
    control(commands, bar, host, "Load / Reload", Action::Load);
    let source = marker(host, "/source", v);
    let value = source.observed.clone();
    let e = property::input(commands, bar, source, &value);
    commands.entity(e).insert(Node {
        flex_grow: 1.,
        min_width: px(100.),
        height: px(26.),
        padding: px(4.).all(),
        ..default()
    });
    control(commands, bar, host, "Import model", Action::Import);
    let body = area(
        commands,
        root,
        Node {
            width: percent(100.),
            flex_grow: 1.,
            min_height: px(0.),
            ..default()
        },
    );
    let sidebar = area(
        commands,
        body,
        Node {
            width: px(350.),
            min_width: px(250.),
            height: percent(100.),
            flex_direction: FlexDirection::Column,
            flex_shrink: 0.,
            ..default()
        },
    );
    let toggle = button(
        commands,
        bar,
        "Properties",
        Name::new("Toggle import properties"),
    );
    commands
        .entity(toggle)
        .observe(move |_: On<Pointer<Click>>, mut nodes: Query<&mut Node>| {
            if let Ok(mut node) = nodes.get_mut(sidebar) {
                node.display = if node.display == Display::None {
                    Display::Flex
                } else {
                    Display::None
                };
            }
        });
    let props = property::scroll_column(commands, sidebar);
    let entries = area(
        commands,
        props,
        Node {
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    property::heading(commands, props, "Asset name");
    let f = marker(host, "/name", v);
    let value = f.observed.clone();
    property::input(commands, props, f, &value);
    row(
        commands,
        props,
        host,
        v,
        "Scale (saved with asset)",
        ["/scaleX", "/scaleY", "/scaleZ"],
        0.01,
        10.,
        0.01,
    );
    check(
        commands,
        props,
        host,
        v,
        "/preview/proportional",
        "Proportional scale",
    );
    row(
        commands,
        props,
        host,
        v,
        "Import translation",
        ["/transX", "/transY", "/transZ"],
        -10.,
        10.,
        0.05,
    );
    property::heading(commands, props, "Import Y rotation (degrees)");
    number(commands, props, host, v, "/rotateY", -180., 180., 1.);
    check(commands, props, host, v, "/isStatic", "Static model");
    row(
        commands,
        props,
        host,
        v,
        "Preview position",
        [
            "/preview/position/0",
            "/preview/position/1",
            "/preview/position/2",
        ],
        -10.,
        10.,
        0.05,
    );
    row(
        commands,
        props,
        host,
        v,
        "Preview rotation (degrees)",
        [
            "/preview/rotation/0",
            "/preview/rotation/1",
            "/preview/rotation/2",
        ],
        -180.,
        180.,
        1.,
    );
    control(
        commands,
        props,
        host,
        "Reset preview pose",
        Action::ResetPose,
    );
    row(
        commands,
        props,
        host,
        v,
        "Physics calibration (meters)",
        [
            "/character/calibrateX",
            "/character/calibrateY",
            "/character/calibrateZ",
        ],
        -5.,
        5.,
        0.01,
    );
    for (title, path, min, max) in [
        ("Capsule radius", "/character/capsuleRadius", 0.01, 5.),
        ("Capsule height", "/character/capsuleHeight", 0.01, 10.),
        ("Step height", "/character/stepHeight", 0., 2.),
    ] {
        property::heading(commands, props, title);
        number(commands, props, host, v, path, min, max, 0.01);
    }
    check(
        commands,
        props,
        host,
        v,
        "/preview/capsule",
        "Show physics capsule",
    );
    check(
        commands,
        props,
        host,
        v,
        "/preview/grid",
        "Show ground grid",
    );
    check(
        commands,
        props,
        host,
        v,
        "/preview/skeleton",
        "Show skeleton",
    );
    property::heading(commands, props, "Optimization (apply with Load / Reload)");
    for (path, title) in [
        ("/optimization/enabled", "Enable optimization"),
        ("/optimization/textures", "Optimize textures"),
        (
            "/optimization/convertOpaquePng",
            "Convert opaque color textures to JPEG",
        ),
        ("/optimization/simplify", "Reduce static mesh triangles"),
    ] {
        check(commands, props, host, v, path, title);
    }
    for (title, path, min, max, step) in [
        (
            "Maximum texture size (0 = original)",
            "/optimization/maxTextureSize",
            0.,
            4096.,
            512.,
        ),
        ("JPEG quality", "/optimization/jpegQuality", 50., 95., 1.),
        ("Mesh triangle ratio", "/optimization/ratio", 0.05, 1., 0.05),
    ] {
        property::heading(commands, props, title);
        number(commands, props, host, v, path, min, max, step);
    }
    let center = area(
        commands,
        body,
        Node {
            flex_grow: 1.,
            min_width: px(0.),
            height: percent(100.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    let tools = area(
        commands,
        center,
        Node {
            align_items: AlignItems::Center,
            column_gap: px(4.),
            padding: px(3.).all(),
            flex_shrink: 0.,
            ..default()
        },
    );
    super::super::scene3d::gizmo::toolbar(commands, tools);
    for (key, title, action) in [
        ("orbit", "Orbit camera (right drag)", Action::Orbit),
        ("pan", "Pan camera (right or middle drag)", Action::Pan),
    ] {
        let e = super::super::scene3d::tools::control(commands, tools, key, title, CameraTool);
        commands.entity(e).observe(
            move |mut event: On<Pointer<Click>>, mut state: ResMut<State>| {
                if event.button == PointerButton::Primary {
                    event.propagate(false);
                    state.actions.push((host, action.clone()));
                }
            },
        );
    }
    control(commands, tools, host, "Frame model", Action::Fit);
    let viewport = area(
        commands,
        center,
        Node {
            flex_grow: 1.,
            min_width: px(0.),
            min_height: px(0.),
            overflow: Overflow::clip(),
            ..default()
        },
    );
    render::observe(commands, viewport, host);
    let transport = area(
        commands,
        center,
        Node {
            column_gap: px(5.),
            align_items: AlignItems::Center,
            padding: px(5.).all(),
            flex_shrink: 0.,
            ..default()
        },
    );
    let clips = area(
        commands,
        transport,
        Node {
            width: px(240.),
            flex_shrink: 0.,
            ..default()
        },
    );
    commands.spawn((label("No bundled animations loaded", 12.), ChildOf(clips)));
    control(commands, transport, host, "▶ Play", Action::Play);
    control(commands, transport, host, "Ⅱ Pause", Action::Pause);
    control(commands, transport, host, "■ Stop", Action::Stop);
    check(commands, transport, host, v, "/preview/loop", "Loop");
    let speed = area(
        commands,
        transport,
        Node {
            width: px(70.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    number(commands, speed, host, v, "/preview/speed", 0.1, 3., 0.1);
    let timeline = commands
        .spawn((label("0.00 s", 11.), ChildOf(transport)))
        .id();
    let scrub = area(
        commands,
        center,
        Node {
            padding: UiRect::horizontal(px(8.)),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    scenemax_ide_ui::number::input(commands, scrub, Scrub(host), "0", 0., 100., 0.1);
    let status = commands
        .spawn((
            label("Choose a model to preview before importing", 12.),
            Node {
                padding: px(6.).all(),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    Parts {
        viewport,
        entries,
        clips,
        status,
        timeline,
    }
}
pub(super) fn clips(commands: &mut Commands, parent: Entity, host: Entity, names: &[String]) {
    commands.entity(parent).despawn_children();
    let options: Vec<_> = std::iter::once((String::new(), "Bind pose".into()))
        .chain(
            names
                .iter()
                .enumerate()
                .map(|(i, name)| (i.to_string(), name.clone())),
        )
        .collect();
    property::dropdown_labeled(commands, parent, ClipPick(host), "", &options);
}

pub(super) fn entries(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    draft: &Value,
    entries: &[String],
) {
    commands.entity(parent).despawn_children();
    if entries.is_empty() {
        return;
    }
    property::heading(commands, parent, "Model in ZIP (then Load / Reload)");
    let options: Vec<_> = std::iter::once((String::new(), "Automatic".to_owned()))
        .chain(entries.iter().map(|s| (s.clone(), s.clone())))
        .collect();
    property::dropdown_labeled(
        commands,
        parent,
        marker(host, "/archiveEntry", draft),
        draft["archiveEntry"].as_str().unwrap_or_default(),
        &options,
    );
}
