//! Shared press-to-act UI state for SceneMax tools and runtime widgets.
#![forbid(unsafe_code)]
#![deny(missing_docs)]
use bevy::{
    picking::{
        PickingSystems,
        hover::{HoverMap, Hovered},
    },
    prelude::*,
};

/// Button surface using Bevy's pointer interaction handling.
#[derive(Component, Default)]
#[require(Node, Interaction, bevy::ui::FocusPolicy::Block)]
pub struct Button;

/// Retained interaction state consumed by presentation and command systems.
#[derive(Component, Default, Clone, Copy, Debug, PartialEq, Eq)]
#[require(Hovered, bevy::ui_widgets::Button)]
pub enum Interaction {
    /// A pointer is pressing the surface.
    Pressed,
    /// A pointer is over the surface.
    Hovered,
    /// The surface is inactive.
    #[default]
    None,
}

/// Projects Bevy picking state before application command processing.
pub struct UiInputPlugin;
impl Plugin for UiInputPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(PreUpdate, update.after(PickingSystems::Last));
    }
}

type Surfaces<'w, 's> = Query<
    'w,
    's,
    (
        &'static Hovered,
        Has<bevy::ui::Pressed>,
        Option<&'static InheritedVisibility>,
        Option<&'static Node>,
        &'static mut Interaction,
    ),
>;
fn update(
    hover: Option<Res<HoverMap>>,
    mouse: Option<Res<ButtonInput<MouseButton>>>,
    touches: Option<Res<bevy::input::touch::Touches>>,
    mut surfaces: Surfaces,
) {
    // Headless command tests can supply interaction state without a picking backend.
    if hover.is_none() {
        return;
    }
    // Context-menu presses must not also execute the primary button action.
    let primary_down = mouse.as_ref().is_none_or(|m| m.pressed(MouseButton::Left))
        || touches.as_ref().is_some_and(|t| t.iter().next().is_some());
    for (hovered, pressed, visibility, node, mut state) in &mut surfaces {
        let visible =
            visibility.is_none_or(|v| v.get()) && node.is_none_or(|n| n.display != Display::None);
        let next = if !visible || !hovered.get() {
            Interaction::None
        } else if pressed && primary_down {
            Interaction::Pressed
        } else {
            Interaction::Hovered
        };
        state.set_if_neq(next);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn press_release_leave_and_hidden_surfaces() {
        let mut app = App::new();
        app.init_resource::<HoverMap>().add_plugins(UiInputPlugin);
        let entity = app.world_mut().spawn(Button).id();
        for (hover, pressed, display, expected) in [
            (true, false, Display::Flex, Interaction::Hovered),
            (true, true, Display::Flex, Interaction::Pressed),
            (false, true, Display::Flex, Interaction::None),
            (true, false, Display::Flex, Interaction::Hovered),
            (true, true, Display::None, Interaction::None),
        ] {
            let mut surface = app.world_mut().entity_mut(entity);
            surface.insert((
                Hovered(hover),
                Node {
                    display,
                    ..default()
                },
            ));
            if pressed {
                surface.insert(bevy::ui::Pressed);
            } else {
                surface.remove::<bevy::ui::Pressed>();
            }
            // A standalone test node does not participate in visibility propagation.
            surface.remove::<InheritedVisibility>();
            app.update();
            assert_eq!(*app.world().get::<Interaction>(entity).unwrap(), expected);
        }
    }
    #[test]
    fn secondary_press_does_not_execute_primary_action() {
        let mut app = App::new();
        app.init_resource::<HoverMap>()
            .init_resource::<ButtonInput<MouseButton>>()
            .add_plugins(UiInputPlugin);
        let entity = app
            .world_mut()
            .spawn((Button, Hovered(true), bevy::ui::Pressed))
            .id();
        app.world_mut()
            .entity_mut(entity)
            .remove::<InheritedVisibility>();
        app.world_mut()
            .resource_mut::<ButtonInput<MouseButton>>()
            .press(MouseButton::Right);
        app.update();
        assert_eq!(
            *app.world().get::<Interaction>(entity).unwrap(),
            Interaction::Hovered
        );
        let mut mouse = app.world_mut().resource_mut::<ButtonInput<MouseButton>>();
        mouse.release(MouseButton::Right);
        mouse.press(MouseButton::Left);
        app.update();
        assert_eq!(
            *app.world().get::<Interaction>(entity).unwrap(),
            Interaction::Pressed
        );
    }
    #[test]
    fn native_pointer_events_reach_the_retained_button_state() {
        use bevy::picking::{
            backend::HitData,
            pointer::{Location, PointerId},
        };
        let mut app = App::new();
        app.init_resource::<HoverMap>()
            .init_resource::<ButtonInput<MouseButton>>()
            .add_plugins((UiInputPlugin, bevy::ui_widgets::ButtonPlugin));
        let entity = app.world_mut().spawn((Button, Hovered(true))).id();
        app.world_mut()
            .entity_mut(entity)
            .remove::<InheritedVisibility>();
        let pointer = Pointer::new(
            PointerId::Mouse,
            Location {
                target: bevy::camera::NormalizedRenderTarget::None {
                    width: 100,
                    height: 100,
                },
                position: Vec2::splat(10.),
            },
        );
        app.world_mut()
            .resource_mut::<ButtonInput<MouseButton>>()
            .press(MouseButton::Left);
        app.world_mut().trigger(PointerPress {
            entity,
            pointer: pointer.clone(),
            button: PointerButton::Primary,
            hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
            count: 1,
        });
        app.world_mut().flush();
        app.update();
        assert_eq!(
            *app.world().get::<Interaction>(entity).unwrap(),
            Interaction::Pressed
        );
        app.world_mut()
            .resource_mut::<ButtonInput<MouseButton>>()
            .release(MouseButton::Left);
        app.world_mut().trigger(PointerRelease {
            entity,
            pointer,
            button: PointerButton::Primary,
            hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
        });
        app.world_mut().flush();
        app.update();
        assert_eq!(
            *app.world().get::<Interaction>(entity).unwrap(),
            Interaction::Hovered
        );
    }
}
