//! Interruptible camera focus motion, independent of scene selection and rendering.
use super::*;
#[derive(Component)]
pub(crate) struct Travel {
    start: Orbit,
    target: Vec3,
    elapsed: f32,
}
pub(super) fn observe(commands: &mut Commands, row: Entity, camera: Entity, index: usize) {
    commands.entity(row).observe(
        move |event: On<Pointer<Click>>,
              mut commands: Commands,
              state: Res<SceneState>,
              cameras: Query<&Orbit>,
              objects: Query<(&gizmo::SceneObject, &GlobalTransform)>| {
            if event.button != PointerButton::Primary {
                return;
            }
            let Some(e) = state.scene.as_ref().and_then(|s| s.entities.get(index)) else {
                return;
            };
            if matches!(e.kind.as_str(), "SECTION" | "CODE") {
                return;
            }
            let target = objects
                .iter()
                .find(|(o, _)| o.0 == index)
                .map(|(_, t)| t.translation())
                .unwrap_or(Vec3::from_array(e.position));
            if let Ok(orbit) = cameras.get(camera) {
                commands.entity(camera).insert(Travel {
                    start: *orbit,
                    target,
                    elapsed: 0.,
                });
            }
        },
    );
}
fn blend(t: f32) -> f32 {
    let t = t.clamp(0., 1.);
    t * t * (3. - 2. * t)
}
pub(crate) fn update(
    mut commands: Commands,
    time: Res<Time>,
    mouse: Res<ButtonInput<MouseButton>>,
    mut cameras: Query<(Entity, &mut Orbit, &mut Transform, &mut Travel)>,
) {
    for (entity, mut orbit, mut transform, mut travel) in &mut cameras {
        if mouse.pressed(MouseButton::Right)
            || (orbit.yaw - travel.start.yaw).abs() > 0.0001
            || (orbit.pitch - travel.start.pitch).abs() > 0.0001
            || (orbit.distance - travel.start.distance).abs() > 0.0001
        {
            commands.entity(entity).remove::<Travel>();
            continue;
        }
        travel.elapsed += time.delta_secs();
        orbit.target = travel
            .start
            .target
            .lerp(travel.target, blend(travel.elapsed / 0.45));
        *transform = orbit.transform();
        if travel.elapsed >= 0.45 {
            commands.entity(entity).remove::<Travel>();
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn focus_moves_over_time_and_manual_navigation_cancels_it() {
        use bevy::ecs::system::RunSystemOnce;
        let mut world = World::new();
        let mut time = Time::<()>::default();
        time.advance_by(std::time::Duration::from_millis(225));
        world.insert_resource(time);
        world.init_resource::<ButtonInput<MouseButton>>();
        let start = Orbit {
            target: Vec3::ZERO,
            distance: 10.,
            yaw: 0.,
            pitch: 0.,
        };
        let camera = world
            .spawn((
                start,
                start.transform(),
                Travel {
                    start,
                    target: Vec3::X * 10.,
                    elapsed: 0.,
                },
            ))
            .id();
        world.run_system_once(update).unwrap();
        assert!(
            world
                .get::<Orbit>(camera)
                .unwrap()
                .target
                .abs_diff_eq(Vec3::X * 5., 0.001)
        );
        world
            .resource_mut::<ButtonInput<MouseButton>>()
            .press(MouseButton::Right);
        world.run_system_once(update).unwrap();
        assert!(world.get::<Travel>(camera).is_none());
        assert!(
            world
                .get::<Orbit>(camera)
                .unwrap()
                .target
                .abs_diff_eq(Vec3::X * 5., 0.001)
        );
    }
    #[test]
    fn focus_easing_has_exact_endpoints_and_slow_edges() {
        assert_eq!(blend(0.), 0.);
        assert_eq!(blend(1.), 1.);
        assert_eq!(blend(0.5), 0.5);
        assert!(blend(0.1) < 0.1);
        assert!(blend(0.9) > 0.9);
    }
}
