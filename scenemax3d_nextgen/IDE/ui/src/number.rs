//! Numeric property inputs with retained pointer sliders and precise text entry.
use crate::{property, theme::*};
use bevy::{
    prelude::*,
    text::{EditableText, TextEdit},
};
#[derive(Component)]
pub(crate) struct Slider {
    input: Entity,
    min: f64,
    max: f64,
    step: f64,
}
#[derive(Component)]
pub(crate) struct Thumb(Entity);
/// Numeric editor and slider. Typed values remain unrestricted by the slider span.
pub fn input<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    marker: T,
    value: &str,
    min: f64,
    max: f64,
    step: f64,
) -> Entity {
    let input = property::input(commands, parent, marker, value);
    let slider = commands
        .spawn((
            Slider {
                input,
                min,
                max,
                step,
            },
            Node {
                width: percent(100.),
                height: px(10.),
                flex_shrink: 0.,
                margin: UiRect::bottom(px(3.)),
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(parent),
        ))
        .id();
    commands.spawn((
        Node {
            position_type: PositionType::Absolute,
            top: px(4.),
            width: percent(100.),
            height: px(2.),
            ..default()
        },
        BackgroundColor(EDGE),
        Pickable::IGNORE,
        ChildOf(slider),
    ));
    commands.spawn((
        Thumb(slider),
        Node {
            position_type: PositionType::Absolute,
            top: px(1.),
            width: px(6.),
            height: px(8.),
            border_radius: BorderRadius::all(px(3.)),
            ..default()
        },
        BackgroundColor(Color::srgb(0.45, 0.6, 0.82)),
        Pickable::IGNORE,
        ChildOf(slider),
    ));
    commands.entity(slider).observe(
        |mut event: On<Pointer<Press>>,
         sliders: Query<(&Slider, &ComputedNode, &UiGlobalTransform)>,
         mut inputs: Query<&mut EditableText>| {
            event.propagate(false);
            set(
                event.entity,
                event.pointer_location.position,
                &sliders,
                &mut inputs,
            );
        },
    );
    commands.entity(slider).observe(
        |mut event: On<Pointer<Drag>>,
         sliders: Query<(&Slider, &ComputedNode, &UiGlobalTransform)>,
         mut inputs: Query<&mut EditableText>| {
            event.propagate(false);
            set(
                event.entity,
                event.pointer_location.position,
                &sliders,
                &mut inputs,
            );
        },
    );
    input
}
fn set(
    entity: Entity,
    position: Vec2,
    sliders: &Query<(&Slider, &ComputedNode, &UiGlobalTransform)>,
    inputs: &mut Query<&mut EditableText>,
) {
    let Ok((slider, node, ui)) = sliders.get(entity) else {
        return;
    };
    let Some(inverse) = ui.try_inverse() else {
        return;
    };
    let fraction = (inverse
        .transform_point2(position / node.inverse_scale_factor())
        .x
        / node.size().x
        + 0.5)
        .clamp(0., 1.) as f64;
    let value =
        ((slider.min + (slider.max - slider.min) * fraction) / slider.step).round() * slider.step;
    if let Ok(mut input) = inputs.get_mut(slider.input) {
        input.queue_edit(TextEdit::SelectAll);
        input.queue_edit(TextEdit::Insert(
            format!("{value:.4}")
                .trim_end_matches('0')
                .trim_end_matches('.')
                .into(),
        ));
    }
}
pub(crate) fn update(
    sliders: Query<&Slider>,
    inputs: Query<&EditableText>,
    mut thumbs: Query<(&Thumb, &mut Node)>,
) {
    for (thumb, mut node) in &mut thumbs {
        let Ok(slider) = sliders.get(thumb.0) else {
            continue;
        };
        let Ok(input) = inputs.get(slider.input) else {
            continue;
        };
        let Ok(value) = input.value().to_string().parse::<f64>() else {
            continue;
        };
        let left =
            percent(((value - slider.min) / (slider.max - slider.min)).clamp(0., 1.) as f32 * 96.);
        if node.left != left {
            node.left = left;
        }
    }
}
