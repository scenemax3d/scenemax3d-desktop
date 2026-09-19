use super::*;
#[derive(Clone, Copy)]
pub(super) struct Parts {
    pub props: Entity,
    pub viewport: Entity,
    pub status: Entity,
    pub dependencies: Entity,
    pub timeline: Entity,
    pub diagnostics: Entity,
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
pub(super) fn caption(_key: &str, value: &str) -> String {
    value.into()
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
        Action::Restart => {
            let mut arc: Vec<_> = (0..=20)
                .map(|i| {
                    let a = 0.4 + i as f32 * 5.1 / 20.;
                    Vec2::new(10. + 7. * a.cos(), 10. + 7. * a.sin())
                })
                .collect();
            arc.extend([Vec2::new(14., 3.), Vec2::new(18., 4.), Vec2::new(18., 0.)]);
            vec![arc]
        }
        Action::Step => {
            let flip = |x: f32| x;
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
    let tip = title;
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
fn section(c: &mut Commands, p: Entity, title: &str, expanded: bool) -> Entity {
    let header = button(
        c,
        p,
        &format!("{} {title}", if expanded { "-" } else { "+" }),
        Tool,
    );
    let content = area(
        c,
        p,
        Node {
            display: if expanded {
                Display::Flex
            } else {
                Display::None
            },
            flex_shrink: 0.,
            row_gap: px(3.),
            ..column()
        },
    );
    let title = title.to_owned();
    c.entity(header).observe(
        move |mut e: On<Pointer<Click>>,
              mut nodes: Query<&mut Node>,
              children: Query<&Children>,
              mut texts: Query<&mut Text>| {
            if e.button != PointerButton::Primary {
                return;
            }
            e.propagate(false);
            if let Ok(mut node) = nodes.get_mut(content) {
                let show = node.display == Display::None;
                node.display = if show { Display::Flex } else { Display::None };
                if let Ok(children) = children.get(header) {
                    for child in children.iter() {
                        if let Ok(mut text) = texts.get_mut(child) {
                            text.0 = format!("{} {title}", if show { "-" } else { "+" });
                        }
                    }
                }
            }
        },
    );
    content
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
    control(c, top, h, "Reload", Action::Load);
    let source = property::input(c, top, marker(h, "source", v), &value(v, "source"));
    c.entity(source).insert(Node {
        flex_grow: 1.,
        height: px(26.),
        min_width: px(80.),
        padding: px(4.).all(),
        ..default()
    });
    control(c, top, h, "Import effect", Action::Import);
    control(c, top, h, "Inspector", Action::Properties);
    let body = area(
        c,
        root,
        Node {
            flex_grow: 1.,
            min_height: px(0.),
            ..default()
        },
    );
    let center = area(
        c,
        body,
        Node {
            flex_grow: 1.,
            ..column()
        },
    );
    let tools = area(c, center, bar());
    for (title, action) in [
        ("Play", Action::Play),
        ("Pause", Action::Pause),
        ("Restart", Action::Restart),
        ("Step one frame", Action::Step),
        ("Reset view", Action::Home),
        ("Snapshot", Action::Snapshot),
    ] {
        control(c, tools, h, title, action);
    }
    let timeline = c
        .spawn((label("FRAME 0  /  300", 12.), ChildOf(tools)))
        .id();
    let viewport = area(
        c,
        center,
        Node {
            flex_grow: 1.,
            min_height: px(0.),
            overflow: Overflow::clip(),
            ..default()
        },
    );
    c.entity(viewport).insert(BackgroundColor(Color::BLACK));
    preview::observe(c, viewport, h);
    let footer = area(c, center, bar());
    let diagnostics = c
        .spawn((
            label(
                "Native Effekseer · right-drag orbit · middle-drag pan · wheel zoom",
                11.,
            ),
            ChildOf(footer),
        ))
        .id();
    let status = c
        .spawn((
            label("Choose an Effekseer effect to begin", 12.),
            ChildOf(center),
        ))
        .id();
    let props = area(
        c,
        body,
        Node {
            width: px(320.),
            flex_shrink: 0.,
            ..column()
        },
    );
    let form = property::scroll_column(c, props);
    property::heading(c, form, "EFFECT PACKAGE");
    property::heading(c, form, "Asset name");
    property::input(c, form, marker(h, "name", v), &value(v, "name"));
    let inspector = form;
    let form = section(c, inspector, "PLAYBACK · 60 FPS", true);
    numeric(c, form, h, v, "Speed", "speed", [0.01, 4., 0.05]);
    numeric(
        c,
        form,
        h,
        v,
        "Preview length · frames",
        "duration",
        [1., 1800., 1.],
    );
    property::checkbox(
        c,
        form,
        marker(h, "loop", v),
        "Loop preview",
        v["loop"].as_bool().unwrap_or(true),
    );
    numeric(c, form, h, v, "Scrub to frame", "seek", [0., 1800., 1.]);
    numeric(c, form, h, v, "Replay seed", "seed", [0., 9999., 1.]);
    let form = section(c, inspector, "EMITTER TRANSFORM", false);
    for (title, key, range) in [
        ("Scale", "scale", [0.001, 10., 0.01]),
        ("Position X", "x", [-20., 20., 0.1]),
        ("Position Y", "y", [-20., 20., 0.1]),
        ("Position Z", "z", [-20., 20., 0.1]),
        ("Pitch · degrees", "pitch", [-180., 180., 1.]),
        ("Yaw · degrees", "yaw", [-180., 180., 1.]),
        ("Roll · degrees", "roll", [-180., 180., 1.]),
    ] {
        numeric(c, form, h, v, title, key, range);
    }
    let form = section(c, inspector, "DYNAMIC INPUTS", false);
    c.spawn((
        label("Live inputs exposed by the effect's equations", 11.),
        ChildOf(form),
    ));
    for (title, key) in [
        ("Input 0", "input0"),
        ("Input 1", "input1"),
        ("Input 2", "input2"),
        ("Input 3", "input3"),
    ] {
        numeric(c, form, h, v, title, key, [-10., 10., 0.1]);
    }
    property::heading(c, form, "TRIGGERS");
    let triggers = area(c, form, bar());
    for i in 0..4 {
        control(c, triggers, h, &format!("Fire {i}"), Action::Trigger(i));
    }
    let form = section(c, inspector, "ATTRACTION TARGET", false);
    for (title, key) in [
        ("Target X", "targetX"),
        ("Target Y", "targetY"),
        ("Target Z", "targetZ"),
    ] {
        numeric(c, form, h, v, title, key, [-20., 20., 0.1]);
    }
    let form = section(c, inspector, "COLOR MULTIPLIER", false);
    for (title, key) in [
        ("Red", "red"),
        ("Green", "green"),
        ("Blue", "blue"),
        ("Opacity", "alpha"),
    ] {
        numeric(c, form, h, v, title, key, [0., 255., 1.]);
    }
    let form = section(c, inspector, "VIEWPORT", false);
    property::checkbox(
        c,
        form,
        marker(h, "grid", v),
        "Ground grid",
        v["grid"].as_bool().unwrap_or(true),
    );
    property::dropdown_labeled(
        c,
        form,
        marker(h, "background", v),
        &value(v, "background"),
        &[
            ("studio".into(), "Studio".into()),
            ("black".into(), "Black".into()),
            ("gray".into(), "Mid gray".into()),
            ("white".into(), "White".into()),
        ],
    );
    let form = section(c, inspector, "DEPENDENCIES", false);
    let dependencies = area(c, form, Node { ..column() });
    c.spawn((
        label(
            "Textures, models, materials, sounds and curves are checked before import.",
            11.,
        ),
        ChildOf(dependencies),
    ));
    Parts {
        props,
        viewport,
        status,
        dependencies,
        timeline,
        diagnostics,
    }
}
pub(super) fn dependencies(c: &mut Commands, parent: Entity, p: &Prepared) {
    c.entity(parent).despawn_children();
    c.spawn((
        label(
            &format!(
                "Format {} · {} referenced resources",
                p.version,
                p.dependencies.len()
            ),
            11.,
        ),
        ChildOf(parent),
    ));
    if p.dependencies.is_empty() {
        c.spawn((label("Self-contained effect", 12.), ChildOf(parent)));
    }
    for d in &p.dependencies {
        let row = area(
            c,
            parent,
            Node {
                padding: px(4.).all(),
                ..column()
            },
        );
        c.spawn((
            label(
                &format!(
                    "{} · {}",
                    if d.size.is_some() { "Ready" } else { "MISSING" },
                    d.kind
                ),
                11.,
            ),
            ChildOf(row),
        ))
        .insert(TextColor(if d.size.is_some() {
            MUTED
        } else {
            Color::srgb(1., 0.45, 0.35)
        }));
        c.spawn((label(&d.path, 12.), ChildOf(row)));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[test]
    fn dependency_rows_accept_present_and_missing_resource_colors() {
        let source = tempfile::tempdir().unwrap();
        let path = source.path().join("fixture.efk");
        let mut b = b"SKFE".to_vec();
        b.extend(0u32.to_le_bytes());
        b.extend(2u32.to_le_bytes());
        for path in ["present.png", "missing.png"] {
            let p: Vec<_> = path.encode_utf16().chain(Some(0)).collect();
            b.extend((p.len() as u32).to_le_bytes());
            for c in p {
                b.extend(c.to_le_bytes());
            }
        }
        std::fs::write(&path, b).unwrap();
        std::fs::write(source.path().join("present.png"), [0]).unwrap();
        let prepared = imports::effect::prepare(&path).unwrap();
        let mut app = App::new();
        let parent = app.world_mut().spawn(Node::default()).id();
        app.world_mut()
            .run_system_once(move |mut c: Commands| dependencies(&mut c, parent, &prepared))
            .unwrap();
        let w = app.world_mut();
        let labels: Vec<_> = w.query::<&Text>().iter(w).map(|t| t.0.clone()).collect();
        assert!(labels.iter().any(|t| t.starts_with("MISSING")));
        assert!(labels.iter().any(|t| t.starts_with("Ready")));
    }
}
