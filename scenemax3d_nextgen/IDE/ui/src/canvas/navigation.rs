//! Retained document-local canvas navigation. No document mutations or rebuilding.
use super::*;
#[derive(Component, Default)]
pub(crate) struct View {
    pub zoom: Option<f32>,
    pub pan: Vec2,
}
impl View {
    fn zoom_at(&mut self, factor: f32, cursor: Vec2, fit: f32) {
        if !factor.is_finite() || !cursor.is_finite() {
            return;
        }
        let old = self.zoom.unwrap_or(fit);
        let next = (old * factor).clamp(0.05, 8.);
        self.pan = cursor - (cursor - self.pan) * (next / old);
        self.zoom = Some(next);
    }
}
#[derive(Component)]
pub(crate) struct ZoomLabel(pub Entity);
/// Add wheel zoom, right/middle drag panning and retained zoom controls.
/// `owner` keeps view state alive across canvas reconstruction after document edits.
pub fn install_navigation(
    commands: &mut Commands,
    canvas: Entity,
    viewport: Entity,
    owner: Entity,
    toolbar: Entity,
) {
    commands.queue(move |world: &mut World| {
        if world.get::<View>(owner).is_none() {
            world.entity_mut(owner).insert(View::default());
        }
        if let Some(mut c) = world.get_mut::<Canvas>(canvas) {
            c.owner = Some(owner);
        }
    });
    for (title, action) in [("−", -1), ("+", 1), ("100%", 0), ("Fit", 2)] {
        let button = crate::button(
            commands,
            toolbar,
            title,
            Name::new(match action {
                -1 => "Zoom out",
                1 => "Zoom in",
                0 => "Actual size",
                _ => "Fit canvas",
            }),
        );
        commands.entity(button).observe(
            move |event: On<Pointer<Click>>,
                  mut views: Query<&mut View>,
                  canvases: Query<&Canvas>,
                  nodes: Query<&ComputedNode>| {
                if event.button != PointerButton::Primary {
                    return;
                }
                let (Ok(mut view), Ok(c), Ok(node)) = (
                    views.get_mut(owner),
                    canvases.get(canvas),
                    nodes.get(viewport),
                ) else {
                    return;
                };
                match action {
                    2 => *view = View::default(),
                    0 => {
                        view.zoom = Some(1.);
                        view.pan = Vec2::ZERO;
                    }
                    _ => view.zoom_at(
                        if action > 0 { 1.25 } else { 0.8 },
                        Vec2::ZERO,
                        fit_scale(
                            node.size() * node.inverse_scale_factor(),
                            Vec2::new(c.width, c.height),
                        ),
                    ),
                }
            },
        );
    }
    commands.spawn((
        crate::label("100%", 11.),
        ZoomLabel(canvas),
        ChildOf(toolbar),
    ));
    commands.entity(viewport).observe(
        move |mut event: On<Pointer<Scroll>>,
              mut views: Query<&mut View>,
              canvases: Query<&Canvas>,
              nodes: Query<(&ComputedNode, &UiGlobalTransform)>,
              keys: Option<Res<ButtonInput<KeyCode>>>| {
            let (Ok(mut view), Ok(c), Ok((node, transform))) = (
                views.get_mut(owner),
                canvases.get(canvas),
                nodes.get(viewport),
            ) else {
                return;
            };
            event.propagate(false);
            let pixels = event.unit == bevy::input::mouse::MouseScrollUnit::Pixel;
            let shift = keys
                .as_ref()
                .is_some_and(|k| k.pressed(KeyCode::ShiftLeft) || k.pressed(KeyCode::ShiftRight));
            if shift {
                view.pan += Vec2::new(event.x, event.y) * if pixels { 1. } else { 32. };
            } else {
                view.pan.x += event.x * if pixels { 1. } else { 32. };
                let Some(inverse) = transform.try_inverse() else {
                    return;
                };
                let cursor = inverse.transform_point2(
                    event.pointer_location.position / node.inverse_scale_factor(),
                ) * node.inverse_scale_factor();
                let factor = (event.y * if pixels { 0.003 } else { 0.16 })
                    .clamp(-2., 2.)
                    .exp();
                view.zoom_at(
                    factor,
                    cursor,
                    fit_scale(
                        node.size() * node.inverse_scale_factor(),
                        Vec2::new(c.width, c.height),
                    ),
                );
            }
        },
    );
    commands.entity(viewport).observe(
        move |mut event: On<Pointer<Drag>>, mut views: Query<&mut View>| {
            if !matches!(
                event.button,
                PointerButton::Middle | PointerButton::Secondary
            ) {
                return;
            }
            event.propagate(false);
            if let Ok(mut view) = views.get_mut(owner) {
                view.pan += event.delta;
            }
        },
    );
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn zoom_keeps_the_canvas_point_under_the_cursor_and_obeys_limits() {
        let mut view = View {
            zoom: Some(0.5),
            pan: Vec2::new(30., -10.),
        };
        let cursor = Vec2::new(120., 80.);
        let point = (cursor - view.pan) / view.zoom.unwrap();
        view.zoom_at(2., cursor, 0.5);
        assert!(((cursor - view.pan) / view.zoom.unwrap() - point).length() < 0.001);
        view.zoom_at(10000., cursor, 0.5);
        assert_eq!(view.zoom, Some(8.));
        view.zoom_at(0.00001, cursor, 0.5);
        assert_eq!(view.zoom, Some(0.05));
        let old = view.pan;
        view.zoom_at(f32::NAN, cursor, 0.5);
        assert_eq!(view.pan, old);
    }
    #[test]
    fn fit_reset_returns_to_auto_size_and_center() {
        let mut view = View::default();
        view.zoom_at(2., Vec2::ZERO, 0.4);
        assert_eq!(view.zoom, Some(0.8));
        view = View::default();
        assert_eq!(view.zoom, None);
        assert_eq!(view.pan, Vec2::ZERO);
        assert_eq!(
            fit_scale(Vec2::new(816., 616.), Vec2::new(1600., 1200.)),
            0.5
        );
    }
    #[test]
    fn native_pointer_events_pan_zoom_and_survive_canvas_replacement() {
        use bevy::{
            ecs::system::RunSystemOnce,
            picking::pointer::{Location, PointerId},
        };
        let mut world = World::new();
        // Match the window component registration used by native PointerTraversal.
        world.register_component::<Window>();
        world.init_resource::<ButtonInput<KeyCode>>();
        let owner = world.spawn_empty().id();
        let viewport = world
            .spawn((
                Node::default(),
                ComputedNode {
                    size: Vec2::new(816., 616.),
                    inverse_scale_factor: 1.,
                    ..default()
                },
                UiGlobalTransform::default(),
            ))
            .id();
        let toolbar = world.spawn(Node::default()).id();
        let canvas = world
            .spawn((
                Node::default(),
                Canvas::new(viewport, 1600., 1200.),
                ChildOf(viewport),
            ))
            .id();
        let child = world.spawn((Node::default(), ChildOf(canvas))).id();
        install_navigation(&mut world.commands(), canvas, viewport, owner, toolbar);
        world.flush();
        let location = Location {
            target: bevy::camera::NormalizedRenderTarget::None {
                width: 816,
                height: 616,
            },
            position: Vec2::ZERO,
        };
        world.trigger(Pointer::new(
            PointerId::Mouse,
            location.clone(),
            Scroll {
                hit: bevy::picking::backend::HitData::new(Entity::PLACEHOLDER, 0., None, None),
                phase: bevy::input::touch::TouchPhase::Moved,
                unit: bevy::input::mouse::MouseScrollUnit::Line,
                x: 0.,
                y: 1.,
            },
            child,
        ));
        assert!(world.get::<View>(owner).unwrap().zoom.unwrap() > 0.5);
        world.trigger(Pointer::new(
            PointerId::Mouse,
            location.clone(),
            Drag {
                button: PointerButton::Secondary,
                distance: Vec2::new(40., 20.),
                delta: Vec2::new(40., 20.),
            },
            child,
        ));
        assert_eq!(world.get::<View>(owner).unwrap().pan, Vec2::new(40., 20.));
        world.trigger(Pointer::new(
            PointerId::Mouse,
            location.clone(),
            Drag {
                button: PointerButton::Primary,
                distance: Vec2::splat(100.),
                delta: Vec2::splat(100.),
            },
            child,
        ));
        assert_eq!(world.get::<View>(owner).unwrap().pan, Vec2::new(40., 20.));
        world
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::ShiftLeft);
        world.trigger(Pointer::new(
            PointerId::Mouse,
            location,
            Scroll {
                hit: bevy::picking::backend::HitData::new(Entity::PLACEHOLDER, 0., None, None),
                phase: bevy::input::touch::TouchPhase::Moved,
                unit: bevy::input::mouse::MouseScrollUnit::Pixel,
                x: -4.,
                y: 12.,
            },
            child,
        ));
        assert_eq!(world.get::<View>(owner).unwrap().pan, Vec2::new(36., 32.));
        world.run_system_once(super::super::fit).unwrap();
        assert_eq!(
            world.get::<Node>(canvas).unwrap().position_type,
            PositionType::Absolute
        );
        let zoom = world.get::<View>(owner).unwrap().zoom;
        world.despawn(canvas);
        let replacement = world
            .spawn((
                Node::default(),
                Canvas::new(viewport, 1600., 1200.),
                ChildOf(viewport),
            ))
            .id();
        install_navigation(&mut world.commands(), replacement, viewport, owner, toolbar);
        world.flush();
        assert_eq!(world.get::<View>(owner).unwrap().zoom, zoom);
        assert_eq!(world.get::<View>(owner).unwrap().pan, Vec2::new(36., 32.));
    }
}
