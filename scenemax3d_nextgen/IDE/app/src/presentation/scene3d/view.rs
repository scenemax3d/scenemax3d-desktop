//! Retained hierarchy, viewport and inspector for imported 3D scenes.
use super::*;
#[derive(Clone, Copy)]
pub(super) struct Parts {
    inspector: Entity,
}
#[derive(Component)]
pub(crate) struct SceneRow(usize);
#[derive(Component)]
pub(crate) struct ObjectList;
#[derive(Component)]
pub(crate) struct CameraSlot;
fn panel(commands: &mut Commands, parent: Entity, node: Node) -> Entity {
    commands
        .spawn((node, BackgroundColor(PANEL), ChildOf(parent)))
        .id()
}
pub(super) fn build(
    commands: &mut Commands,
    host: Entity,
    camera: Entity,
    scene: &Scene3d,
    selected: usize,
    collapsed: &std::collections::HashSet<usize>,
) -> Parts {
    commands.entity(host).despawn_children();
    let root = panel(
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
    super::tools::toolbar(commands, root);
    commands.spawn((
        label("", 11.),
        AssetStatus,
        Node {
            display: Display::None,
            flex_shrink: 0.,
            padding: UiRect::horizontal(px(8.)),
            ..default()
        },
        ChildOf(root),
    ));

    commands.spawn((
        label("Select a model with IK to tune its joints", 11.),
        super::ik_controls::Hint,
        ChildOf(root),
    ));
    let body = panel(
        commands,
        root,
        Node {
            width: percent(100.),
            flex_grow: 1.,
            min_height: px(0.),
            ..default()
        },
    );
    let tree = panel(
        commands,
        body,
        Node {
            width: px(230.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            height: percent(100.),
            min_height: px(0.),
            ..default()
        },
    );
    commands.spawn((
        label("SCENE HIERARCHY", 11.),
        Node {
            padding: px(8.).all(),
            ..default()
        },
        ChildOf(tree),
    ));
    let camera_slot = commands
        .spawn((
            CameraSlot,
            Node {
                width: percent(100.),
                flex_direction: FlexDirection::Column,
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(tree),
        ))
        .id();
    let objects = scenemax_ide_ui::property::scroll_column(commands, tree);
    commands.entity(objects).insert(ObjectList);
    for (i, e) in scene.entities.iter().enumerate() {
        let mut ancestor = e.parent;
        let mut visible = true;
        while let Some(parent) = ancestor {
            if collapsed.contains(&parent) {
                visible = false;
                break;
            }
            ancestor = scene.entities[parent].parent;
        }
        let branch = scene
            .entities
            .get(i + 1)
            .is_some_and(|child| child.parent == Some(i));
        let icon = match e.kind.as_str() {
            "CAMERA" => "◉",
            "SECTION" => "▱",
            "MODEL" => "◇",
            "CODE" => "{}",
            "CINEMATIC_RIG" => "▷",
            "CINEMATIC_TRACK" => "↳",
            _ => "□",
        };
        let row = scenemax_ide_ui::tree::row(
            commands,
            if e.kind == "CAMERA" {
                camera_slot
            } else {
                objects
            },
            scenemax_ide_ui::tree::TreeRow {
                caption: &e.name,
                icon,
                depth: e.depth,
                expanded: branch.then(|| !collapsed.contains(&i)),
                selected: i == selected,
                muted: e.hidden,
            },
            Choose::Select(i),
            Choose::Toggle(i),
        );
        commands.entity(row).insert(SceneRow(i));
        if !visible {
            commands.entity(row).insert(Node {
                display: Display::None,
                width: percent(100.),
                height: px(24.),
                min_height: px(24.),
                flex_shrink: 0.,
                padding: UiRect::left(px(6. + e.depth as f32 * 16.)),
                align_items: AlignItems::Center,
                overflow: Overflow::clip(),
                ..default()
            });
        }
        super::focus::observe(commands, row, camera, i);
    }
    let viewport_frame = panel(
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
    super::navigation::spawn(commands, viewport_frame, camera);
    super::game_camera::inset(commands, viewport_frame);
    let viewport = commands
        .spawn((
            super::gizmo::Viewport(camera),
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                min_height: px(0.),
                ..default()
            },
            ViewportNode::new(camera),
            ChildOf(viewport_frame),
        ))
        .observe(
            move |event: On<Pointer<Drag>>,
                  nav: Res<super::navigation::Navigation>,
                  mut cameras: Query<(&mut Orbit, &mut Transform)>| {
                if event.button != PointerButton::Secondary {
                    return;
                }
                if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                    if nav.pan {
                        let rotation = transform.rotation;
                        let distance = orbit.distance;
                        orbit.target += rotation
                            * Vec3::new(-event.delta.x, event.delta.y, 0.)
                            * distance
                            * 0.002;
                    } else {
                        orbit.yaw -= event.delta.x * 0.007;
                        orbit.pitch = (orbit.pitch + event.delta.y * 0.007).clamp(-1.45, 1.45);
                    }
                    *transform = orbit.transform();
                }
            },
        )
        .observe(
            move |event: On<Pointer<Scroll>>, mut cameras: Query<(&mut Orbit, &mut Transform)>| {
                if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                    orbit.distance =
                        (orbit.distance * (1. - event.y * 0.1).clamp(0.2, 5.)).clamp(0.2, 100000.);
                    *transform = orbit.transform();
                }
            },
        )
        .id();
    super::path::observe(commands, viewport, camera);
    super::picking::observe(commands, viewport);
    let toggle = button(
        commands,
        body,
        "›",
        Name::new("Collapse or expand properties"),
    );
    commands.entity(toggle).insert(Node {
        width: px(24.),
        min_width: px(24.),
        height: px(28.),
        padding: px(4.).all(),
        ..default()
    });
    let inspector_frame = panel(
        commands,
        body,
        Node {
            width: px(380.),
            height: percent(100.),
            min_height: px(0.),
            flex_shrink: 0.,
            padding: px(8.).all(),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    commands.entity(toggle).observe(
        move |_: On<Pointer<Click>>,
              mut nodes: Query<&mut Node>,
              children: Query<&Children>,
              mut texts: Query<&mut Text>| {
            if let Ok(mut node) = nodes.get_mut(inspector_frame) {
                let collapsed = node.display != Display::None;
                node.display = if collapsed {
                    Display::None
                } else {
                    Display::Flex
                };
                if let Ok(children) = children.get(toggle) {
                    for child in children.iter() {
                        if let Ok(mut text) = texts.get_mut(child) {
                            text.0 = if collapsed { "‹" } else { "›" }.into();
                        }
                    }
                }
            }
        },
    );
    let parts = Parts {
        inspector: inspector_frame,
    };
    inspect(commands, parts, scene, selected);
    parts
}
pub(super) fn inspect(commands: &mut Commands, parts: Parts, scene: &Scene3d, selected: usize) {
    let inspector_frame = parts.inspector;
    commands.entity(inspector_frame).despawn_children();
    super::inspector::actions(commands, inspector_frame, scene, selected);
    let inspector = scenemax_ide_ui::property::scroll_column(commands, inspector_frame);
    super::inspector::build(commands, inspector, scene, selected);
}

pub(crate) fn synchronize_tree(
    state: Res<SceneState>,
    mut rows: Query<(
        &SceneRow,
        &mut Node,
        &mut scenemax_ide_ui::tree::TreeAppearance,
    )>,
) {
    let Some(scene) = &state.scene else {
        return;
    };
    for (row, mut node, mut appearance) in &mut rows {
        let Some(entry) = scene.entities.get(row.0) else {
            continue;
        };
        let mut parent = entry.parent;
        let mut visible = true;
        while let Some(i) = parent {
            if state.collapsed.contains(&i) {
                visible = false;
                break;
            }
            parent = scene.entities[i].parent;
        }
        let display = if visible {
            Display::Flex
        } else {
            Display::None
        };
        if node.display != display {
            node.display = display;
        }
        let selected = row.0 == state.selected;
        if appearance.selected != selected {
            appearance.selected = selected;
        }
        if appearance.expanded.is_some() {
            let expanded = Some(!state.collapsed.contains(&row.0));
            if appearance.expanded != expanded {
                appearance.expanded = expanded;
            }
        }
    }
}

pub(crate) fn synchronize_names(
    state: Res<SceneState>,
    choices: Query<&Choose>,
    mut captions: Query<(&ChildOf, &mut Text), With<scenemax_ide_ui::tree::Caption>>,
) {
    let Some(scene) = state.scene.as_ref() else {
        return;
    };
    for (parent, mut caption) in &mut captions {
        if let Ok(Choose::Select(index)) = choices.get(parent.parent())
            && let Some(entry) = scene.entities.get(*index)
            && caption.0 != entry.name
        {
            caption.0 = entry.name.clone();
        }
    }
}
