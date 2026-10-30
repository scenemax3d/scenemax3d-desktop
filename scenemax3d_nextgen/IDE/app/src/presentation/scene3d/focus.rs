//! Interruptible camera focus motion, independent of scene selection and rendering.
use super::*;
#[derive(Component)]
pub(crate) struct Travel {
    start: Orbit,
    target: Vec3,
    distance: f32,
    elapsed: f32,
}
const DURATION: f32 = 0.5;
#[derive(Component)]
pub(crate) struct Request(usize, bool);

pub(super) fn added(commands: &mut Commands, camera: Entity, index: usize) {
    commands.entity(camera).insert(Request(index, true));
}

/// Use the same activation as hierarchy selection, including keyboard activation.
/// Disclosure buttons and viewport picking do not move the camera.
pub(crate) fn selection(
    mut commands: Commands,
    state: Res<SceneState>,
    choices: Query<(&Interaction, &Choose), Changed<Interaction>>,
) {
    let Some(camera) = state.camera else {
        return;
    };
    for (interaction, choice) in &choices {
        if *interaction == Interaction::Pressed
            && let Choose::Select(index) = choice
            && state.selected == *index
        {
            commands.entity(camera).insert(Request(*index, false));
        }
    }
}
fn blend(t: f32) -> f32 {
    let t = t.clamp(0., 1.);
    t * t * (3. - 2. * t)
}
pub(crate) fn update(
    mut commands: Commands,
    time: Res<Time>,
    mouse: Res<ButtonInput<MouseButton>>,
    state: Res<SceneState>,
    objects: Query<(&gizmo::SceneObject, &GlobalTransform)>,
    mut cameras: Query<(
        Entity,
        &mut Orbit,
        &mut Transform,
        Option<&mut Travel>,
        Option<&Request>,
    )>,
) {
    for (entity, mut orbit, mut transform, travel, request) in &mut cameras {
        if let Some(request) = request {
            commands.entity(entity).remove::<Request>();
            if let Some(entry) = state
                .scene
                .as_ref()
                .and_then(|scene| scene.entities.get(request.0))
                && !matches!(entry.kind.as_str(), "SECTION" | "CODE")
            {
                let target = objects
                    .iter()
                    // Newly rebuilt scene transforms have not propagated yet.
                    .filter(|_| !request.1)
                    .find(|(object, _)| object.0 == request.0)
                    .map(|(_, transform)| transform.translation())
                    .unwrap_or(Vec3::from_array(entry.position));
                commands.entity(entity).insert(Travel {
                    start: *orbit,
                    target,
                    distance: if request.1 {
                        super::primitives::view_distance(entry).unwrap_or(orbit.distance)
                    } else {
                        orbit.distance
                    },
                    elapsed: 0.,
                });
            }
            // The triggering frame may follow a long idle wait. Start at the
            // current position; only subsequent continuous frames advance travel.
            continue;
        }
        let Some(mut travel) = travel else {
            continue;
        };
        let expected = travel
            .start
            .target
            .lerp(travel.target, blend(travel.elapsed / DURATION));
        if mouse.pressed(MouseButton::Right)
            || mouse.pressed(MouseButton::Middle)
            || !orbit.target.abs_diff_eq(expected, 0.0001)
            || (orbit.yaw - travel.start.yaw).abs() > 0.0001
            || (orbit.pitch - travel.start.pitch).abs() > 0.0001
            || (orbit.distance
                - (travel.start.distance
                    + (travel.distance - travel.start.distance) * blend(travel.elapsed / DURATION)))
            .abs()
                > 0.0001
        {
            commands.entity(entity).remove::<Travel>();
            continue;
        }
        travel.elapsed += time.delta_secs();
        orbit.distance = travel.start.distance
            + (travel.distance - travel.start.distance) * blend(travel.elapsed / DURATION);
        orbit.target = travel
            .start
            .target
            .lerp(travel.target, blend(travel.elapsed / DURATION));
        *transform = orbit.transform();
        if travel.elapsed >= DURATION {
            commands.entity(entity).remove::<Travel>();
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn new_primitive_smoothly_frames_at_true_scale_without_changing_geometry() {
        use bevy::ecs::system::RunSystemOnce;
        use std::time::Duration;
        let dir = tempfile::tempdir().unwrap();
        let entry = scenemax_ide_core::scene3d::structure::template("BOX").unwrap();
        let scene = scenemax_ide_services::scene3d::load(
            dir.path(),
            &serde_json::json!({"entities":[entry]}).to_string(),
        )
        .unwrap();
        assert_eq!(scene.entities[0].size, [1.; 3]);
        assert_eq!(scene.entities[0].scale, [1.; 3]);
        let mut world = World::new();
        world.init_resource::<Time>();
        world.init_resource::<ButtonInput<MouseButton>>();
        world.insert_resource(SceneState {
            scene: Some(scene),
            ..default()
        });
        let start = Orbit {
            target: Vec3::ZERO,
            distance: 220.,
            yaw: 0.65,
            pitch: 0.4,
        };
        let camera = world
            .spawn((start, start.transform(), Request(0, true)))
            .id();
        world.run_system_once(update).unwrap();
        assert_eq!(world.get::<Orbit>(camera).unwrap().distance, 220.);
        world
            .resource_mut::<Time>()
            .advance_by(Duration::from_millis(250));
        world.run_system_once(update).unwrap();
        assert!((world.get::<Orbit>(camera).unwrap().distance - 111.75).abs() < 0.001);
        world.run_system_once(update).unwrap();
        let orbit = world.get::<Orbit>(camera).unwrap();
        assert_eq!(orbit.distance, 3.5);
        assert_eq!(orbit.target, Vec3::new(0., 0.5, 0.));
        assert!(world.get::<Travel>(camera).is_none());
    }
    #[test]
    fn focus_moves_over_time_and_manual_navigation_cancels_it() {
        use bevy::ecs::system::RunSystemOnce;
        let mut world = World::new();
        let mut time = Time::<()>::default();
        time.advance_by(std::time::Duration::from_millis(250));
        world.insert_resource(time);
        world.init_resource::<ButtonInput<MouseButton>>();
        world.init_resource::<SceneState>();
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
                    distance: start.distance,
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
    fn hierarchy_activation_retargets_from_current_view_and_finishes_in_half_a_second() {
        use bevy::ecs::system::RunSystemOnce;
        use std::time::Duration;
        let dir = tempfile::tempdir().unwrap();
        let scene = scenemax_ide_services::scene3d::load(
            dir.path(),
            r#"{"entities":[{"type":"MODEL","name":"First"},{"type":"MODEL","name":"Second"}]}"#,
        )
        .unwrap();
        let mut world = World::new();
        let mut time = Time::<()>::default();
        time.advance_by(Duration::from_secs(5));
        world.insert_resource(time);
        world.init_resource::<ButtonInput<MouseButton>>();
        let start = Orbit {
            target: Vec3::ZERO,
            distance: 10.,
            yaw: 0.6,
            pitch: 0.3,
        };
        let camera = world.spawn((start, start.transform())).id();
        world.insert_resource(SceneState {
            scene: Some(scene),
            camera: Some(camera),
            ..default()
        });
        let destination = Vec3::new(30., 4., 9.);
        world.spawn((
            gizmo::SceneObject(0, Transform::default()),
            GlobalTransform::from_translation(destination),
        ));
        world.spawn((
            gizmo::SceneObject(1, Transform::default()),
            GlobalTransform::from_translation(-destination),
        ));
        let row = world.spawn((Choose::Select(0), Interaction::Pressed)).id();
        world.run_system_once(selection).unwrap();
        world.run_system_once(update).unwrap();
        // Selecting an already selected row also focuses it, without consuming the idle delta.
        assert_eq!(world.get::<Orbit>(camera).unwrap().target, Vec3::ZERO);
        assert!(world.get::<Travel>(camera).is_some());
        world
            .resource_mut::<Time>()
            .advance_by(Duration::from_millis(250));
        world.run_system_once(update).unwrap();
        let midway = world.get::<Orbit>(camera).unwrap().target;
        assert!(midway.abs_diff_eq(destination * 0.5, 0.001));
        world.entity_mut(row).insert(Choose::Select(1));
        world.resource_mut::<SceneState>().selected = 1;
        world.run_system_once(selection).unwrap();
        world.run_system_once(update).unwrap();
        assert_eq!(world.get::<Orbit>(camera).unwrap().target, midway);
        world
            .resource_mut::<Time>()
            .advance_by(Duration::from_millis(250));
        world.run_system_once(update).unwrap();
        assert!(
            world
                .get::<Orbit>(camera)
                .unwrap()
                .target
                .abs_diff_eq(midway.lerp(-destination, 0.5), 0.001)
        );
        world.run_system_once(update).unwrap();
        let orbit = world.get::<Orbit>(camera).unwrap();
        assert_eq!(orbit.target, -destination);
        assert_eq!(
            (orbit.yaw, orbit.pitch, orbit.distance),
            (start.yaw, start.pitch, start.distance)
        );
        assert!(world.get::<Travel>(camera).is_none());
        // Expanding the hierarchy must not refocus the preview.
        world.entity_mut(row).insert(Choose::Toggle(1));
        world.run_system_once(selection).unwrap();
        assert!(world.get::<Request>(camera).is_none());
    }

    #[test]
    fn focus_temporarily_enables_continuous_redraw_and_restores_idle_mode() {
        use bevy::winit::{UpdateMode, WinitSettings};
        use std::time::Duration;
        let mut app = App::new();
        app.init_resource::<Time>()
            .init_resource::<ButtonInput<MouseButton>>()
            .init_resource::<SceneState>()
            .init_resource::<playback::Playback>()
            .insert_resource(WinitSettings {
                focused_mode: UpdateMode::reactive(Duration::from_millis(100)),
                ..WinitSettings::continuous()
            })
            .add_systems(Update, (update, playback::cadence).chain());
        let start = Orbit {
            target: Vec3::ZERO,
            distance: 10.,
            yaw: 0.,
            pitch: 0.,
        };
        let camera = app
            .world_mut()
            .spawn((
                start,
                start.transform(),
                Travel {
                    start,
                    target: Vec3::X,
                    distance: start.distance,
                    elapsed: 0.,
                },
            ))
            .id();
        app.update();
        assert!(matches!(
            app.world().resource::<WinitSettings>().focused_mode,
            UpdateMode::Continuous
        ));
        app.world_mut()
            .resource_mut::<Time>()
            .advance_by(Duration::from_millis(500));
        app.update();
        assert!(app.world().get::<Travel>(camera).is_none());
        assert!(!matches!(
            app.world().resource::<WinitSettings>().focused_mode,
            UpdateMode::Continuous
        ));
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
