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
#[allow(clippy::too_many_arguments)]
pub(super) fn build(
    commands: &mut Commands,
    host: Entity,
    scene: &ScenePreview,
    selected: Option<&str>,
    assets: Option<&crate::project_assets::ProjectAssets>,
    server: Option<&AssetServer>,
    collapsed: &std::collections::HashSet<String>,
    properties_collapsed: bool,
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
    chrome::toolbar(commands, tools, host);
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
    hierarchy::build(commands, body, host, scene, selected, collapsed);
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
    let mut entities = std::collections::HashMap::new();
    let mut layers = std::collections::HashMap::new();
    for w in scene.widgets.iter().filter(|w| w.visible) {
        let parent = w
            .pointer
            .rsplit_once("/children/")
            .and_then(|(p, _)| entities.get(p).copied());
        let layer_key = w.pointer.split("/widgets/").next().unwrap_or("");
        let layer_order = layers.len() as i32;
        let layer = *layers.entry(layer_key.to_owned()).or_insert_with(|| {
            commands
                .spawn((
                    Node {
                        position_type: PositionType::Absolute,
                        width: percent(100.),
                        height: percent(100.),
                        ..default()
                    },
                    ZIndex(layer_order),
                    Pickable::IGNORE,
                    ChildOf(canvas),
                ))
                .id()
        });
        let entity = draw_widget(
            commands,
            canvas,
            host,
            scene,
            w,
            parent.or(Some(layer)),
            assets,
            server,
        );
        commands.entity(entity).insert(Outline::new(
            px(1.),
            px(0.),
            if selected == Some(w.pointer.as_str()) {
                SELECTED
            } else {
                Color::NONE
            },
        ));
        entities.insert(w.pointer.clone(), entity);
    }
    commands.spawn((
        label(
            if scene.warnings.is_empty() {
                "Runtime assets · Design resolution · Scripts are not executing".into()
            } else {
                scene.warnings.join("; ")
            },
            11.,
        ),
        ChildOf(middle),
    ));
    let toggle = button(
        commands,
        body,
        if properties_collapsed { "‹" } else { "›" },
        Name::new("Collapse or expand UI properties"),
    );
    commands.entity(toggle).insert(Node {
        width: px(24.),
        min_width: px(24.),
        height: px(28.),
        padding: px(4.).all(),
        flex_shrink: 0.,
        ..default()
    });
    let inspector = area(
        commands,
        body,
        Node {
            width: px(300.),
            display: if properties_collapsed {
                Display::None
            } else {
                Display::Flex
            },
            min_height: px(0.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            padding: px(14.).all(),
            row_gap: px(8.),
            overflow: Overflow::scroll_y(),
            ..default()
        },
        PANEL,
    );
    commands.entity(inspector).insert(chrome::PropertiesPanel);
    chrome::collapse(commands, toggle, inspector, host);
    button(commands, inspector, "Apply properties", Apply(host));
    let inspector = scenemax_ide_ui::property::scroll_column(commands, inspector);
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
        inspector_fields(commands, inspector, host, scene, w);

        commands.spawn((
            label("Apply records one undo step.", 11.),
            ChildOf(inspector),
        ));
    }
}
fn color(value: &str) -> Color {
    Srgba::hex(value.trim())
        .map(Color::Srgba)
        .unwrap_or(Color::WHITE)
}
#[allow(clippy::too_many_arguments)]
fn draw_widget(
    commands: &mut Commands,
    canvas: Entity,
    host: Entity,
    scene: &ScenePreview,
    w: &PreviewWidget,
    parent: Option<Entity>,
    assets: Option<&crate::project_assets::ProjectAssets>,
    server: Option<&AssetServer>,
) -> Entity {
    let [mut x, mut y, width, height] = w.rect;
    let mut pw = scene.width;
    let mut ph = scene.height;
    if let Some((pointer, _)) = w.pointer.rsplit_once("/children/")
        && let Some(p) = scene.widgets.iter().find(|p| p.pointer == pointer)
    {
        x -= p.rect[0] + p.definition.padding_left;
        y -= p.rect[1] + p.definition.padding_top;
        pw = (p.rect[2] - p.definition.padding_left - p.definition.padding_right).max(1.);
        ph = (p.rect[3] - p.definition.padding_top - p.definition.padding_bottom).max(1.);
    }
    let entity = area(
        commands,
        parent.unwrap_or(canvas),
        Node {
            position_type: PositionType::Absolute,
            left: percent(x / pw * 100.),
            top: percent(y / ph * 100.),
            width: percent(width / pw * 100.),
            height: percent(height / ph * 100.),
            ..default()
        },
        if ["PANEL", "TEXT_VIEW", "EDIT_TEXT", "IMAGE"].contains(&w.kind.as_str()) {
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
        ZIndex(w.definition.z_order),
    ));
    if let Some(visual) = &w.visual {
        if let (Some(assets), Some(server)) = (assets, server)
            && let Some(path) = assets.asset(&visual.root, &visual.path)
        {
            let image = server.load(path);
            if visual.text {
                for (source, dest) in &visual.quads {
                    commands.spawn((
                        Node {
                            position_type: PositionType::Absolute,
                            left: percent(dest[0] / width.max(1.) * 100.),
                            top: percent(dest[1] / height.max(1.) * 100.),
                            width: percent(dest[2] / width.max(1.) * 100.),
                            height: percent(dest[3] / height.max(1.) * 100.),
                            ..default()
                        },
                        ImageNode {
                            image: image.clone(),
                            color: color(&w.text_color),
                            rect: Some(Rect::new(source[0], source[1], source[2], source[3])),
                            ..default()
                        },
                        Pickable::IGNORE,
                        ChildOf(entity),
                    ));
                }
            } else {
                commands.entity(entity).insert(ImageNode {
                    image,
                    rect: visual
                        .quads
                        .first()
                        .map(|(r, _)| Rect::new(r[0], r[1], r[2], r[3])),
                    ..default()
                });
            }
        }
    } else if w.kind == "IMAGE" {
        commands
            .entity(entity)
            .insert(ImageNode::solid_color(Color::srgba(0.9, 0.72, 0.38, 0.85)));
    } else if ["TEXT_VIEW", "EDIT_TEXT", "BUTTON", "LIST_VIEW"].contains(&w.kind.as_str())
        && !w.text.is_empty()
    {
        commands.entity(entity).insert((
            Text::new(&w.text),
            TextFont {
                font_size: FontSize::Px(w.font_size),
                ..default()
            },
            TextColor(color(&w.text_color)),
            TextLayout::justify(match w.alignment.to_ascii_lowercase().as_str() {
                "center" => Justify::Center,
                "right" => Justify::Right,
                _ => Justify::Left,
            })
            .with_no_wrap(),
            CanvasText::new(canvas, w.font_size),
        ));
    }
    entity
}
mod properties;
use properties::inspector_fields;
pub(super) use properties::{FieldKind, parse_field};

mod chrome;
pub(super) mod hierarchy;
