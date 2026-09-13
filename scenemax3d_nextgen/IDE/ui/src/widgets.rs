//! Reusable widgets with caller-owned action components.
use crate::theme::*;
use bevy::{prelude::*, text::FontSource};
/// Create a themed proportional label.
pub fn label(value: impl Into<String>, size: f32) -> impl Bundle {
    (
        Text::new(value),
        TextFont {
            font: FontSource::SansSerif,
            font_size: FontSize::Px(size),
            ..default()
        },
        TextColor(INK),
    )
}

/// Create a button carrying a caller-owned component.
pub fn button<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    caption: &str,
    action: T,
) -> Entity {
    let id = commands
        .spawn((
            Button,
            action,
            Node {
                padding: UiRect::axes(px(10.), px(5.)),
                align_items: AlignItems::Center,
                margin: px(2.).all(),
                flex_shrink: 0.,
                ..default()
            },
            BackgroundColor(PANEL),
        ))
        .with_children(|p| {
            p.spawn(label(caption, 13.));
        })
        .id();
    commands.entity(parent).add_child(id);
    id
}

type ButtonFeedbackQuery<'w, 's> = Query<
    'w,
    's,
    (
        &'static Interaction,
        &'static mut BackgroundColor,
        Option<&'static ButtonSurface>,
    ),
    (Changed<Interaction>, With<Button>),
>;

pub(crate) fn button_feedback(mut buttons: ButtonFeedbackQuery) {
    for (interaction, mut color, surface) in &mut buttons {
        color.0 = match interaction {
            Interaction::Pressed => SELECTED,
            Interaction::Hovered => EDGE,
            Interaction::None => surface.map_or(PANEL, |s| s.0),
        };
    }
}

/// Resting background for buttons whose surface differs from the default panel.
#[derive(Component)]
pub struct ButtonSurface(pub Color);
