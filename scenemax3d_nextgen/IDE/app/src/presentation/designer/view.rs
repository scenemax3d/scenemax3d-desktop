//! Retained scene canvas, hierarchy and inspector construction.
use super::*;
#[derive(Clone, Copy)]
pub(super) struct Parts {
    inspector: Entity,
}
fn area(commands: &mut Commands, parent: Entity, node: Node, color: Color) -> Entity {
    let scrolls = node.overflow.y == OverflowAxis::Scroll;
    let entity = commands
        .spawn((node, BackgroundColor(color), ChildOf(parent)))
        .id();
    if scrolls {
        commands.entity(entity).observe(
            |mut event: On<Pointer<Scroll>>,
             mut nodes: Query<(&mut ScrollPosition, &ComputedNode)>| {
                if let Ok((mut scroll, node)) = nodes.get_mut(event.entity) {
                    let scale = if event.unit == bevy::input::mouse::MouseScrollUnit::Line {
                        40.
                    } else {
                        1.
                    };
                    let max = (node.content_size().y - node.size().y).max(0.)
                        * node.inverse_scale_factor();
                    scroll.y = (scroll.y - event.y * scale).clamp(0., max);
                    event.propagate(false);
                }
            },
        );
    }
    entity
}
pub(super) fn build(
    commands: &mut Commands,
    host: Entity,
    scene: &ScenePreview,
    selected: Option<&str>,
) -> Parts {
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
        BG,
    );
    let toolbar = area(
        commands,
        root,
        Node {
            align_items: AlignItems::Center,
            padding: px(8.).all(),
            column_gap: px(10.),
            flex_shrink: 0.,
            ..default()
        },
        PANEL,
    );
    commands.spawn((
        label(
            format!(
                "UI DESIGNER   /   {}   ·   {} × {}",
                scene.name, scene.width, scene.height
            ),
            13.,
        ),
        ChildOf(toolbar),
    ));
    button(commands, toolbar, "Save", Action::Save);
    button(commands, toolbar, "Undo", Action::Undo);
    button(commands, toolbar, "Redo", Action::Redo);
    let tools = area(
        commands,
        root,
        Node {
            padding: UiRect::axes(px(8.), px(3.)),
            align_items: AlignItems::Center,
            flex_shrink: 0.,
            ..default()
        },
        PANEL,
    );
    commands.spawn((label("Add widget", 12.), ChildOf(tools)));
    for (caption, kind) in [
        ("Panel", "PANEL"),
        ("Text", "TEXT_VIEW"),
        ("Button", "BUTTON"),
    ] {
        button(
            commands,
            tools,
            caption,
            Structure {
                host,
                kind: Some(kind),
            },
        );
    }
    button(
        commands,
        tools,
        "Delete selected",
        Structure { host, kind: None },
    );
    let body = area(
        commands,
        root,
        Node {
            width: percent(100.),
            flex_grow: 1.,
            min_height: px(0.),
            ..default()
        },
        BG,
    );
    let tree = area(
        commands,
        body,
        Node {
            width: px(190.),
            min_width: px(140.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            padding: px(10.).all(),
            overflow: Overflow::scroll_y(),
            ..default()
        },
        PANEL,
    );
    commands.spawn((label("HIERARCHY", 12.), ChildOf(tree)));
    let mut last_layer = String::new();
    for w in &scene.widgets {
        if last_layer != w.layer {
            commands.spawn((
                label(format!("Layer: {}", w.layer), 13.),
                Node {
                    margin: UiRect::top(px(12.)),
                    ..default()
                },
                ChildOf(tree),
            ));
            last_layer.clone_from(&w.layer);
        }
        let row = button(
            commands,
            tree,
            &format!("{}{}", "  ".repeat(w.depth), w.name),
            Pick {
                host,
                pointer: w.pointer.clone(),
            },
        );
        if Some(w.pointer.as_str()) == selected {
            commands.entity(row).insert((
                scenemax_ide_ui::ButtonSurface(SELECTED),
                BackgroundColor(SELECTED),
            ));
        }
    }
    let middle = area(
        commands,
        body,
        Node {
            flex_grow: 1.,
            min_width: px(0.),
            flex_direction: FlexDirection::Column,
            padding: px(14.).all(),
            ..default()
        },
        Color::srgb_u8(36, 38, 43),
    );
    commands.spawn((
        label(
            "CANVAS   ·   Fit to viewport   ·   Select a widget to inspect",
            12.,
        ),
        ChildOf(middle),
    ));
    let viewport = area(
        commands,
        middle,
        Node {
            flex_grow: 1.,
            min_height: px(0.),
            width: percent(100.),
            justify_content: JustifyContent::Center,
            align_items: AlignItems::Center,
            overflow: Overflow::clip(),
            ..default()
        },
        Color::NONE,
    );
    let canvas = area(
        commands,
        viewport,
        Node {
            width: px(640.),
            height: px(360.),
            flex_shrink: 0.,
            overflow: Overflow::clip(),
            ..default()
        },
        Color::srgb_u8(18, 21, 28),
    );
    commands
        .entity(canvas)
        .insert(Canvas::new(viewport, scene.width, scene.height));
    for w in scene.widgets.iter().filter(|w| w.visible) {
        draw_widget(commands, canvas, host, scene, w, selected);
    }
    commands.spawn((
        label(
            "Preview: panels, text and buttons. Other widget types are labeled placeholders.",
            11.,
        ),
        ChildOf(middle),
    ));
    let inspector = area(
        commands,
        body,
        Node {
            width: px(224.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            padding: px(14.).all(),
            row_gap: px(8.),
            overflow: Overflow::scroll_y(),
            ..default()
        },
        PANEL,
    );
    let parts = Parts { inspector };
    inspect(commands, host, parts, scene, selected);
    parts
}
pub(super) fn inspect(
    commands: &mut Commands,
    host: Entity,
    parts: Parts,
    scene: &ScenePreview,
    selected: Option<&str>,
) {
    let inspector = parts.inspector;
    commands.entity(inspector).despawn_children();
    commands.spawn((label("PROPERTIES", 12.), ChildOf(inspector)));
    if let Some(w) = scene
        .widgets
        .iter()
        .find(|w| Some(w.pointer.as_str()) == selected)
    {
        commands.spawn((
            label(format!("{}\n{}", w.name, w.kind), 15.),
            ChildOf(inspector),
        ));
        for (key, value) in [
            ("Text", w.text.clone()),
            ("Width", w.width.to_string()),
            ("Height", w.height.to_string()),
            ("Font size", w.font_size.to_string()),
            ("Color", w.color.clone()),
        ] {
            commands.spawn((label(key, 12.), ChildOf(inspector)));
            commands.spawn((
                Property { host, key },
                EditableText {
                    visible_lines: Some(1.),
                    allow_newlines: false,
                    max_characters: Some(10000),
                    ..EditableText::new(value)
                },
                Node {
                    width: percent(100.),
                    padding: px(4.).all(),
                    height: px(28.),
                    min_height: px(28.),
                    flex_shrink: 0.,
                    ..default()
                },
                TextFont {
                    font: bevy::text::FontSource::SansSerif,
                    font_size: FontSize::Px(13.),
                    ..default()
                },
                TextColor(INK),
                bevy::text::TextCursorStyle {
                    color: Color::WHITE,
                    ..default()
                },
                BackgroundColor(BG),
                ChildOf(inspector),
            ));
        }
        button(commands, inspector, "Apply properties", Apply(host));
        commands.spawn((
            label(
                "Apply records one undo step.\nConstraints remain unchanged.",
                11.,
            ),
            ChildOf(inspector),
        ));
    }
}
fn color(value: &str) -> Color {
    Srgba::hex(value)
        .map(Color::Srgba)
        .unwrap_or(Color::srgb_u8(60, 65, 75))
}
fn draw_widget(
    commands: &mut Commands,
    canvas: Entity,
    host: Entity,
    scene: &ScenePreview,
    w: &PreviewWidget,
    selected: Option<&str>,
) {
    let [x, y, width, height] = w.rect;
    let entity = area(
        commands,
        canvas,
        Node {
            position_type: PositionType::Absolute,
            left: percent(x / scene.width * 100.),
            top: percent(y / scene.height * 100.),
            width: percent(width / scene.width * 100.),
            height: percent(height / scene.height * 100.),
            align_items: AlignItems::Center,
            justify_content: if w.kind == "TEXT_VIEW" {
                match w.alignment.to_ascii_lowercase().as_str() {
                    "center" => JustifyContent::Center,
                    "right" => JustifyContent::End,
                    _ => JustifyContent::Start,
                }
            } else {
                JustifyContent::Center
            },
            overflow: Overflow::clip(),
            border: px(if selected == Some(w.pointer.as_str()) {
                2.
            } else {
                0.
            })
            .all(),
            ..default()
        },
        if w.kind == "TEXT_VIEW" {
            Color::NONE
        } else {
            color(&w.color)
        },
    );
    commands.entity(entity).insert((
        Interaction::None,
        Pick {
            host,
            pointer: w.pointer.clone(),
        },
        BorderColor::all(Color::srgb_u8(95, 158, 255)),
    ));
    let text = if ["PANEL", "TEXT_VIEW", "BUTTON"].contains(&w.kind.as_str()) {
        w.text.clone()
    } else {
        format!("[{}] {}", w.kind, w.name)
    };
    if !text.is_empty() {
        commands.spawn((
            Text::new(text),
            TextFont {
                font: bevy::text::FontSource::SansSerif,
                font_size: FontSize::Px(w.font_size),
                ..default()
            },
            TextColor(color(&w.text_color)),
            CanvasText::new(canvas, w.font_size),
            ChildOf(entity),
        ));
    }
}
