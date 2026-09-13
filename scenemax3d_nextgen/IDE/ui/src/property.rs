//! Compact retained property controls shared by designer inspectors.
use crate::{button, label, theme::*};
use bevy::{prelude::*, text::EditableText};

/// Editable single-line value with normal native focus and selection behavior.
pub fn input<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    value: &str,
) -> Entity {
    let entity = crate::spawn_editor(commands, parent, marker, value, true);
    commands.entity(entity).insert((
        Node {
            width: percent(100.),
            height: px(26.),
            min_width: px(0.),
            padding: UiRect::axes(px(6.), px(3.)),
            flex_shrink: 0.,
            ..default()
        },
        EditableText {
            allow_newlines: false,
            max_characters: Some(8192),
            ..EditableText::new(value)
        },
        TextFont {
            font_size: FontSize::Px(12.),
            ..default()
        },
    ));
    entity
}

/// Checkbox value, read by the owning feature when applying a transaction.
#[derive(Component)]
pub struct Checked(pub bool);
/// Create a toggle with a visible checkmark.
pub fn checkbox<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    caption: &str,
    checked: bool,
) -> Entity {
    let title = caption.to_owned();
    let entity = button(
        commands,
        parent,
        &format!("{} {caption}", if checked { "☑" } else { "☐" }),
        marker,
    );
    commands.entity(entity).insert(Checked(checked)).observe(
        move |event: On<Pointer<Click>>,
              mut values: Query<(&mut Checked, &Children)>,
              mut texts: Query<&mut Text>| {
            if let Ok((mut value, children)) = values.get_mut(event.entity) {
                value.0 = !value.0;
                for child in children.iter() {
                    if let Ok(mut text) = texts.get_mut(child) {
                        text.0 = format!("{} {title}", if value.0 { "☑" } else { "☐" });
                    }
                }
            }
        },
    );
    entity
}

/// Selected serialized choice value.
#[derive(Component)]
pub struct Choice(pub String);

/// Scrollable property column with wheel bubbling and a native draggable scrollbar.
pub fn scroll_column(commands: &mut Commands, parent: Entity) -> Entity {
    let frame = commands
        .spawn((
            Node {
                display: Display::Grid,
                grid_template_columns: vec![GridTrack::flex(1.), GridTrack::px(12.)],
                grid_template_rows: vec![GridTrack::flex(1.)],
                flex_grow: 1.,
                min_height: px(0.),
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    let content = commands
        .spawn((
            Node {
                grid_row: GridPlacement::start(1),
                grid_column: GridPlacement::start(1),
                min_height: px(0.),
                min_width: px(0.),
                flex_direction: FlexDirection::Column,
                row_gap: px(4.),
                padding: px(4.).all(),
                overflow: Overflow::scroll_y(),
                ..default()
            },
            ChildOf(frame),
        ))
        .id();
    commands.entity(content).observe(
        move |mut event: On<Pointer<Scroll>>,
              mut q: Query<(&mut ScrollPosition, &ComputedNode)>| {
            if let Ok((mut scroll, node)) = q.get_mut(content) {
                let step = if event.unit == bevy::input::mouse::MouseScrollUnit::Line {
                    30.
                } else {
                    1.
                };
                let max =
                    (node.content_size().y - node.size().y).max(0.) * node.inverse_scale_factor();
                scroll.y = (scroll.y - event.y * step).clamp(0., max);
                event.propagate(false);
            }
        },
    );
    commands
        .spawn((
            Node {
                grid_row: GridPlacement::start(1),
                grid_column: GridPlacement::start(2),
                min_width: px(12.),
                ..default()
            },
            BackgroundColor(BG),
            bevy::ui_widgets::Scrollbar {
                orientation: bevy::ui_widgets::ControlOrientation::Vertical,
                target: content,
                min_thumb_length: 24.,
            },
            ChildOf(frame),
        ))
        .with_children(|p| {
            p.spawn((
                BackgroundColor(MUTED),
                bevy::ui_widgets::ScrollbarThumb {
                    border_radius: BorderRadius::all(px(4.)),
                    border: px(2.).all(),
                },
                BorderColor::all(BG),
            ));
        });
    content
}
/// Dropdown with explicit choices, retaining unknown imported values.
pub fn dropdown<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    value: &str,
    options: &[String],
) {
    let options: Vec<_> = options.iter().map(|v| (v.clone(), v.clone())).collect();
    dropdown_labeled(commands, parent, marker, value, &options);
}
/// A combo box whose displayed descriptions are independent of serialized values.
pub fn dropdown_labeled<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    value: &str,
    options: &[(String, String)],
) {
    let caption = options
        .iter()
        .find(|(id, _)| id == value)
        .map(|(_, label)| label.as_str())
        .unwrap_or(value);
    let control = button(
        commands,
        parent,
        &format!("{} ↓", if caption.is_empty() { "None" } else { caption }),
        marker,
    );
    commands.entity(control).insert((
        Choice(value.into()),
        crate::ButtonSurface(BG),
        BackgroundColor(BG),
        BorderColor::all(EDGE),
        Node {
            width: percent(100.),
            padding: UiRect::axes(px(6.), px(4.)),
            border: px(1.).all(),
            flex_shrink: 0.,
            ..default()
        },
    ));
    let popup = commands
        .spawn((
            Node {
                display: Display::None,
                width: percent(100.),
                max_height: px(180.),
                flex_direction: FlexDirection::Column,
                overflow: Overflow::scroll_y(),
                ..default()
            },
            BackgroundColor(BG),
            ChildOf(parent),
        ))
        .id();
    commands
        .entity(control)
        .observe(move |_: On<Pointer<Click>>, mut nodes: Query<&mut Node>| {
            if let Ok(mut node) = nodes.get_mut(popup) {
                node.display = if node.display == Display::None {
                    Display::Flex
                } else {
                    Display::None
                };
            }
        });
    commands.entity(popup).observe(
        move |mut event: On<Pointer<Scroll>>, mut q: Query<&mut ScrollPosition>| {
            event.propagate(false);
            if let Ok(mut scroll) = q.get_mut(popup) {
                scroll.y = (scroll.y - event.y * 24.).max(0.);
            }
        },
    );
    for (option, caption) in options {
        let value = option.clone();
        let caption = caption.clone();
        let row = button(
            commands,
            popup,
            if caption.is_empty() { "None" } else { &caption },
            Choice(option.clone()),
        );
        commands.entity(row).observe(
            move |_: On<Pointer<Click>>,
                  mut controls: Query<(&mut Choice, &Children)>,
                  mut text: Query<&mut Text>,
                  mut nodes: Query<&mut Node>| {
                if let Ok((mut choice, children)) = controls.get_mut(control) {
                    choice.0.clone_from(&value);
                    for child in children.iter() {
                        if let Ok(mut text) = text.get_mut(child) {
                            text.0 =
                                format!("{} ↓", if caption.is_empty() { "None" } else { &caption });
                        }
                    }
                }
                if let Ok(mut node) = nodes.get_mut(popup) {
                    node.display = Display::None;
                }
            },
        );
    }
}
/// A section heading in a property grid.
pub fn heading(commands: &mut Commands, parent: Entity, title: &str) {
    commands.spawn((
        label(title, 12.),
        Node {
            margin: UiRect::top(px(8.)),
            ..default()
        },
        ChildOf(parent),
    ));
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::{
        ecs::system::RunSystemOnce,
        picking::{
            events::Scroll,
            pointer::{Location, PointerId},
        },
    };
    #[test]
    fn inspector_wheel_bubbles_from_fields_to_scrollbar_target() {
        let mut world = World::new();
        // PointerTraversal queries Window as well as ChildOf, as registered by the real app.
        world.register_component::<Window>();
        let content = world
            .run_system_once(|mut commands: Commands| {
                let root = commands.spawn(Node::default()).id();
                scroll_column(&mut commands, root)
            })
            .unwrap();
        world.flush();
        world.entity_mut(content).insert(ComputedNode {
            size: Vec2::new(250., 100.),
            content_size: Vec2::new(250., 1000.),
            inverse_scale_factor: 1.,
            ..default()
        });
        let child = world.spawn((Node::default(), ChildOf(content))).id();
        world.flush();
        world.trigger(Pointer::new(
            PointerId::Mouse,
            Location {
                target: bevy::camera::NormalizedRenderTarget::None {
                    width: 1000,
                    height: 1000,
                },
                position: Vec2::ZERO,
            },
            Scroll {
                unit: bevy::input::mouse::MouseScrollUnit::Line,
                x: 0.,
                y: -3.,
                hit: bevy::picking::backend::HitData::new(Entity::PLACEHOLDER, 0., None, None),
                phase: bevy::input::touch::TouchPhase::Moved,
            },
            child,
        ));
        assert_eq!(world.get::<ScrollPosition>(content).unwrap().y, 90.);
        assert!(
            world
                .query::<&bevy::ui_widgets::Scrollbar>()
                .iter(&world)
                .any(|bar| bar.target == content)
        );
    }
}
