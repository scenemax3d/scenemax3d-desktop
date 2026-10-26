//! Compact retained property controls shared by designer inspectors.
use crate::{label, theme::*};
use bevy::{prelude::*, text::EditableText};
use scenemax_ui_input::Button;

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
        bevy::ui_widgets::TextInput,
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
#[derive(Component)]
struct CheckboxIndicator {
    indicator: Entity,
    check: Entity,
}

/// Update a checkbox and its visual state programmatically.
pub fn set_checked(commands: &mut Commands, entity: Entity, checked: bool) {
    commands.queue(move |world: &mut World| {
        let Some(visual) = world.get::<CheckboxIndicator>(entity) else {
            return;
        };
        let (indicator, check) = (visual.indicator, visual.check);
        if let Some(mut value) = world.get_mut::<Checked>(entity) {
            value.0 = checked;
        }
        if let Some(mut background) = world.get_mut::<BackgroundColor>(indicator) {
            background.0 = if checked { ACCENT } else { HEADER };
        }
        if let Some(mut border) = world.get_mut::<BorderColor>(indicator) {
            *border = BorderColor::all(if checked { ACCENT } else { MUTED });
        }
        if let Some(mut node) = world.get_mut::<Node>(check) {
            node.display = if checked {
                Display::Flex
            } else {
                Display::None
            };
        }
    });
}
/// Create a toggle with a visible checkmark.
pub fn checkbox<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    caption: &str,
    checked: bool,
) -> Entity {
    let entity = commands
        .spawn((
            Button,
            marker,
            Name::new(caption.to_owned()),
            Node {
                padding: UiRect::axes(px(10.), px(5.)),
                margin: px(2.).all(),
                column_gap: px(8.),
                align_items: AlignItems::Center,
                flex_shrink: 0.,
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(parent),
        ))
        .id();
    // Draw the indicator independently of the label font and its fallback glyphs.
    let indicator = commands
        .spawn((
            Node {
                width: px(18.),
                height: px(18.),
                border: px(2.).all(),
                border_radius: BorderRadius::all(px(3.)),
                flex_shrink: 0.,
                ..default()
            },
            BackgroundColor(if checked { ACCENT } else { HEADER }),
            BorderColor::all(if checked { ACCENT } else { MUTED }),
            Pickable::IGNORE,
            ChildOf(entity),
        ))
        .id();
    let check = commands
        .spawn((
            Node {
                width: px(14.),
                height: px(14.),
                display: if checked {
                    Display::Flex
                } else {
                    Display::None
                },
                ..default()
            },
            Pickable::IGNORE,
            ChildOf(indicator),
        ))
        .id();
    for (a, b) in [
        (Vec2::new(2., 7.), Vec2::new(5., 10.)),
        (Vec2::new(5., 10.), Vec2::new(12., 3.)),
    ] {
        let stroke = crate::icons::line(commands, check, a, b, Color::WHITE);
        commands
            .entity(stroke)
            .entry::<Node>()
            .and_modify(move |mut node| {
                node.height = px(2.);
                node.top = px((a.y + b.y) / 2. - 1.);
            });
    }
    commands.spawn((label(caption, 13.), Pickable::IGNORE, ChildOf(entity)));
    commands
        .entity(entity)
        .insert(CheckboxIndicator { indicator, check });
    commands.entity(entity).insert(Checked(checked)).observe(
        move |event: On<PointerClick>,
              mut values: Query<&mut Checked>,
              mut indicators: Query<(&mut BackgroundColor, &mut BorderColor)>,
              mut nodes: Query<&mut Node>| {
            if event.button != PointerButton::Primary {
                return;
            }
            if let Ok(mut value) = values.get_mut(entity) {
                value.0 = !value.0;
                if let Ok((mut background, mut border)) = indicators.get_mut(indicator) {
                    background.0 = if value.0 { ACCENT } else { HEADER };
                    *border = BorderColor::all(if value.0 { ACCENT } else { MUTED });
                }
                if let Ok(mut node) = nodes.get_mut(check) {
                    node.display = if value.0 {
                        Display::Flex
                    } else {
                        Display::None
                    };
                }
            }
        },
    );
    entity
}

/// Selected serialized choice value.
#[derive(Component)]
pub struct Choice(pub String);
#[derive(Component)]
pub(crate) struct ChoiceOptions(Vec<String>);

// Selection can also change through undo, document reloads and preview controls.
pub(crate) fn synchronize_choices(
    mut commands: Commands,
    choices: Query<(Entity, &Choice, &ChoiceOptions), Changed<Choice>>,
    children: Query<&Children>,
    rows: Query<(
        &bevy::feathers::controls::OptionIndex,
        Has<bevy::ui::Selected>,
    )>,
) {
    for (entity, choice, options) in &choices {
        if let Some(index) = options.0.iter().position(|value| *value == choice.0) {
            for row in children.iter_descendants(entity) {
                if let Ok((option, selected)) = rows.get(row)
                    && option.0 == index
                    && !selected
                {
                    commands.trigger(bevy::ui_widgets::SetSelected { entity, row });
                    break;
                }
            }
        }
    }
}

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
        move |mut event: On<PointerScroll>, mut q: Query<(&mut ScrollPosition, &ComputedNode)>| {
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
    use bevy::{
        feathers::controls::{FeathersSelect, OptionIndex, list_rows_from_strings},
        scene::prelude::*,
        ui_widgets::ValueChange,
    };
    // Preserve imported values, including empty ones, until explicitly changed.
    let mut options = options.to_vec();
    if !options.iter().any(|(id, _)| id == value) {
        options.insert(0, (value.to_owned(), value.to_owned()));
    }
    let selected = options.iter().position(|(id, _)| id == value);
    let rows = list_rows_from_strings(
        options
            .iter()
            .map(|(_, caption)| if caption.is_empty() { "None" } else { caption }),
        selected,
    );
    let control = commands
        .spawn_scene(bsn! {
            @FeathersSelect { @options: rows, @max_visible: 8usize }
            Node { width: percent(100.), min_width: px(0.), flex_shrink: 0. }
            ChildOf(parent)
        })
        .insert((
            marker,
            Choice(value.to_owned()),
            ChoiceOptions(options.iter().map(|(id, _)| id.clone()).collect()),
        ))
        .id();
    commands.entity(control).observe(
        move |event: On<ValueChange<Entity>>,
              rows: Query<&OptionIndex>,
              mut choices: Query<&mut Choice>| {
            if event.source == control
                && let Ok(index) = rows.get(event.value)
                && let Some((value, _)) = options.get(index.0)
                && let Ok(mut choice) = choices.get_mut(control)
            {
                choice.0.clone_from(value);
            }
        },
    );
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
            events::PointerScroll,
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
        world.trigger(PointerScroll {
            pointer: Pointer::new(
                PointerId::Mouse,
                Location {
                    target: bevy::camera::NormalizedRenderTarget::None {
                        width: 1000,
                        height: 1000,
                    },
                    position: Vec2::ZERO,
                },
            ),
            entity: child,
            unit: bevy::input::mouse::MouseScrollUnit::Line,
            x: 0.,
            y: -3.,
            hit: bevy::picking::backend::HitData::new(Entity::PLACEHOLDER, 0., None, None),
            phase: bevy::input::touch::TouchPhase::Moved,
        });
        assert_eq!(world.get::<ScrollPosition>(content).unwrap().y, 90.);
        assert!(
            world
                .query::<&bevy::ui_widgets::Scrollbar>()
                .iter(&world)
                .any(|bar| bar.target == content)
        );
    }
}
