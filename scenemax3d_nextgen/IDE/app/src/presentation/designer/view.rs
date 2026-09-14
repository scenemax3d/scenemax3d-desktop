//! Retained scene canvas, hierarchy and inspector construction.
use super::*;
#[derive(Clone)]
pub(super) struct Parts {
    inspector: Entity,
    canvas: Entity,
    footer: Entity,
    widgets: std::collections::HashMap<String, WidgetParts>,
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
    let navigation = area(
        commands,
        middle,
        Node {
            align_items: AlignItems::Center,
            column_gap: px(4.),
            flex_shrink: 0.,
            ..default()
        },
        Color::NONE,
    );
    commands.spawn((label("Canvas", 12.), ChildOf(navigation)));
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
    scenemax_ide_ui::canvas::install_navigation(commands, canvas, viewport, host, navigation);
    chrome::document_actions(commands, navigation);
    let mut entities = std::collections::HashMap::new();
    let mut layers = std::collections::HashMap::new();
    for w in scene.widgets.iter() {
        let parent = w
            .pointer
            .rsplit_once("/children/")
            .and_then(|(p, _)| entities.get(p).map(|p: &WidgetParts| p.entity));
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
        let widget = draw_widget(
            commands,
            canvas,
            host,
            scene,
            w,
            parent.or(Some(layer)),
            assets,
            server,
        );
        commands.entity(widget.entity).insert(Outline::new(
            px(1.),
            px(0.),
            if selected == Some(w.pointer.as_str()) {
                SELECTED
            } else {
                Color::NONE
            },
        ));
        entities.insert(w.pointer.clone(), widget);
    }
    let footer = commands
        .spawn((
            label(
                if scene.warnings.is_empty() {
                    "Wheel: zoom · Right/middle drag: pan · Shift+wheel: scroll".into()
                } else {
                    scene.warnings.join("; ")
                },
                11.,
            ),
            ChildOf(middle),
        ))
        .id();
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
    commands.spawn((
        label("Changes update the preview immediately", 11.),
        ChildOf(inspector),
    ));
    let inspector = scenemax_ide_ui::property::scroll_column(commands, inspector);
    let parts = Parts {
        inspector,
        canvas,
        footer,
        widgets: entities,
    };
    inspect(commands, host, &parts, scene, selected);
    parts
}
pub(super) fn inspect(
    commands: &mut Commands,
    host: Entity,
    parts: &Parts,
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
            InspectorTitle(host),
            ChildOf(inspector),
        ));
        inspector_fields(commands, inspector, host, scene, w);
    }
}
fn color(value: &str) -> Color {
    Srgba::hex(value.trim())
        .map(Color::Srgba)
        .unwrap_or(Color::WHITE)
}
#[derive(Clone)]
struct WidgetParts {
    entity: Entity,
    glyphs: Vec<Entity>,
    hidden_frame: Entity,
}
#[derive(Component)]
struct InspectorTitle(Entity);
fn widget_node(scene: &ScenePreview, w: &PreviewWidget) -> Node {
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
    Node {
        position_type: PositionType::Absolute,
        left: percent(x / pw * 100.),
        top: percent(y / ph * 100.),
        width: percent(width / pw * 100.),
        height: percent(height / ph * 100.),
        ..default()
    }
}
fn background(w: &PreviewWidget) -> Color {
    if ["PANEL", "TEXT_VIEW", "EDIT_TEXT", "IMAGE"].contains(&w.kind.as_str()) {
        Color::NONE
    } else {
        color(&w.color)
    }
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
) -> WidgetParts {
    let entity = area(
        commands,
        parent.unwrap_or(canvas),
        widget_node(scene, w),
        background(w),
    );
    commands.entity(entity).insert((
        Interaction::None,
        Pick {
            host,
            pointer: w.pointer.clone(),
        },
        ZIndex(w.definition.z_order),
    ));
    let glyphs = draw_visual(commands, entity, canvas, w, assets, server);
    let hidden_frame = hidden::frame(commands, entity, !w.visible);
    WidgetParts {
        entity,
        glyphs,
        hidden_frame,
    }
}
fn draw_visual(
    commands: &mut Commands,
    entity: Entity,
    canvas: Entity,
    w: &PreviewWidget,
    assets: Option<&crate::project_assets::ProjectAssets>,
    server: Option<&AssetServer>,
) -> Vec<Entity> {
    let mut glyphs = Vec::new();
    let [_, _, width, height] = w.rect;
    if let Some(visual) = &w.visual {
        if let (Some(assets), Some(server)) = (assets, server)
            && let Some(path) = assets.asset(&visual.root, &visual.path)
        {
            let image = server.load(path);
            if visual.text {
                for (source, dest) in &visual.quads {
                    let glyph = commands
                        .spawn((
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
                        ))
                        .id();
                    glyphs.push(glyph);
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
    glyphs
}
mod hidden;
mod properties;
use properties::inspector_fields;
pub(super) use properties::{FieldKind, parse_field};

mod chrome;
pub(super) mod hierarchy;

/// Update only canvas widgets and labels; focused inspector controls remain alive.
pub(super) fn update_preview(
    commands: &mut Commands,
    host: Entity,
    parts: &mut Parts,
    scene: &ScenePreview,
    previous: &ScenePreview,
    assets: Option<&crate::project_assets::ProjectAssets>,
    server: Option<&AssetServer>,
) {
    for w in &scene.widgets {
        let (Some(rendered), Some(old)) = (
            parts.widgets.get_mut(&w.pointer),
            previous.widgets.iter().find(|old| old.pointer == w.pointer),
        ) else {
            continue;
        };
        if w.properties == old.properties && w.rect == old.rect && w.visible == old.visible {
            continue;
        }
        commands.entity(rendered.entity).insert((
            widget_node(scene, w),
            BackgroundColor(background(w)),
            ZIndex(w.definition.z_order),
        ));
        commands
            .entity(rendered.hidden_frame)
            .insert(hidden::node(!w.visible));
        let same_visual = match (&w.visual, &old.visual) {
            (Some(a), Some(b)) => a.path == b.path && a.quads == b.quads && a.text == b.text,
            (None, None) => true,
            _ => false,
        };
        if !same_visual
            || w.text != old.text
            || w.text_color != old.text_color
            || w.font_size != old.font_size
            || w.alignment != old.alignment
            || w.rect[2..] != old.rect[2..]
        {
            for glyph in rendered.glyphs.drain(..) {
                commands.entity(glyph).try_despawn();
            }
            commands
                .entity(rendered.entity)
                .remove::<(Text, ImageNode, CanvasText)>();
            rendered.glyphs =
                draw_visual(commands, rendered.entity, parts.canvas, w, assets, server);
        }
    }
    let names: std::collections::HashMap<_, _> = scene
        .widgets
        .iter()
        .map(|w| {
            (
                w.pointer.clone(),
                (w.name.clone(), w.kind.clone(), w.visible),
            )
        })
        .collect();
    commands
        .entity(parts.footer)
        .insert(Text::new(if scene.warnings.is_empty() {
            "Wheel: zoom · Right/middle drag: pan · Shift+wheel: scroll".into()
        } else {
            scene.warnings.join("; ")
        }));
    commands.queue(move |world: &mut World| {
        let selected = world.get::<Designer>(host).and_then(|d| d.selected.clone());
        for (caption, mut text, mut tint) in world
            .query::<(&hierarchy::WidgetCaption, &mut Text, &mut TextColor)>()
            .iter_mut(world)
        {
            if caption.host == host
                && let Some((name, _, visible)) = names.get(&caption.pointer)
            {
                if text.0 != *name {
                    text.0 = name.clone();
                }
                tint.0 = if *visible {
                    INK
                } else {
                    Color::srgb_u8(125, 131, 141)
                };
            }
        }
        for (title, mut text) in world
            .query::<(&InspectorTitle, &mut Text)>()
            .iter_mut(world)
        {
            if title.0 == host
                && let Some((name, kind, _)) = selected.as_ref().and_then(|p| names.get(p))
            {
                let value = format!("{name}\n{kind}");
                if text.0 != value {
                    text.0 = value;
                }
            }
        }
    });
}
