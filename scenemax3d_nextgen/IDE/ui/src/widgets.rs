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
    (
        Changed<Interaction>,
        With<Button>,
        Without<NoButtonFeedback>,
    ),
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

/// Suppress visual button feedback for invisible input surfaces such as popup backdrops.
/// Pointer interaction and click handling remain enabled.
#[derive(Component)]
pub struct NoButtonFeedback;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn invisible_click_surface_stays_transparent_through_pointer_transitions() {
        let mut app = App::new();
        app.add_systems(Update, button_feedback);
        let backdrop = app
            .world_mut()
            .spawn((Button, NoButtonFeedback, BackgroundColor(Color::NONE)))
            .id();
        let item = app.world_mut().spawn((Button, BackgroundColor(PANEL))).id();
        for (interaction, expected) in [
            (Interaction::None, PANEL),
            (Interaction::Hovered, EDGE),
            (Interaction::Pressed, SELECTED),
            (Interaction::Hovered, EDGE),
            (Interaction::None, PANEL),
        ] {
            app.world_mut().entity_mut(backdrop).insert(interaction);
            app.world_mut().entity_mut(item).insert(interaction);
            app.update();
            assert_eq!(
                app.world().get::<BackgroundColor>(backdrop).unwrap().0,
                Color::NONE
            );
            assert_eq!(app.world().get::<Interaction>(backdrop), Some(&interaction));
            assert_eq!(
                app.world().get::<BackgroundColor>(item).unwrap().0,
                expected
            );
        }
    }
}
