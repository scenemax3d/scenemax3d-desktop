//! Headless isolated Avian world. The generated commands go through the runtime adapter.
use avian3d::prelude::*;
use bevy::{prelude::*, time::TimeUpdateStrategy};
use scenemax_ide_core::physics::Settings;
use scenemax_ide_services::physics::Action;
use std::time::Duration;
pub(super) const DT: f32 = 1. / 64.;
#[derive(Default)]
pub(crate) struct Sandbox {
    app: Option<App>,
    body: Option<Entity>,
    stop: Option<f32>,
    pub time: f32,
}
#[derive(Default, Clone)]
pub(super) struct Snapshot {
    pub position: Vec3,
    pub rotation: Quat,
    pub velocity: Vec3,
    pub angular: Vec3,
}
impl Sandbox {
    pub(super) fn clear(&mut self) {
        *self = Self::default();
    }
    pub(super) fn reset(&mut self, settings: &Settings) -> Result<(), String> {
        let actions = scenemax_ide_services::physics::actions(settings)?;
        let mut app = App::new();
        app.add_plugins((
            MinimalPlugins,
            TransformPlugin,
            bevy::asset::AssetPlugin::default(),
            bevy::mesh::MeshPlugin,
            bevy::scene::ScenePlugin,
            PhysicsPlugins::default(),
            scenemax_physics::MotionPlugin,
        ))
        .insert_resource(TimeUpdateStrategy::ManualDuration(Duration::from_secs_f32(
            DT,
        )));
        app.finish();
        app.cleanup();
        let collider = if settings.shape == "Sphere" {
            Collider::sphere(0.5)
        } else {
            Collider::cuboid(1., 1., 1.)
        };
        app.world_mut().spawn((
            RigidBody::Static,
            Collider::cuboid(80., 1., 80.),
            Transform::from_xyz(0., -0.5, 0.),
            Restitution::new(settings.restitution),
            Friction::new(settings.friction),
        ));
        let body = app
            .world_mut()
            .spawn((
                RigidBody::Dynamic,
                collider,
                Transform::from_translation(Vec3::from_array(settings.position)),
                LinearVelocity::ZERO,
                AngularVelocity::ZERO,
            ))
            .id();
        {
            let mut commands = app.world_mut().commands();
            for action in actions.into_iter().flatten() {
                scenemax_physics::enqueue(&mut commands, body, &action);
            }
            if settings.command == "Stop" {
                scenemax_physics::enqueue(&mut commands, body, &Action::Velocity(settings.vector));
            }
        }
        app.world_mut().flush();
        // First update primes clocks and mass properties without advancing simulation time.
        app.update();
        self.app = Some(app);
        self.body = Some(body);
        self.time = 0.;
        self.stop = (settings.command == "Stop").then_some(settings.stop_after);
        Ok(())
    }
    pub(super) fn step(&mut self) {
        let (Some(app), Some(body)) = (&mut self.app, self.body) else {
            return;
        };
        if self.stop.is_some_and(|t| self.time + DT * 0.01 >= t) {
            scenemax_physics::stop(&mut app.world_mut().commands(), body);
            app.world_mut().flush();
            self.stop = None;
        }
        app.update();
        self.time += DT;
    }
    pub(super) fn snapshot(&self) -> Snapshot {
        let (Some(app), Some(body)) = (&self.app, self.body) else {
            return Snapshot::default();
        };
        let e = app.world().entity(body);
        Snapshot {
            position: e.get::<Position>().map_or(Vec3::ZERO, |v| v.0),
            rotation: e.get::<Rotation>().map_or(Quat::IDENTITY, |v| v.0),
            velocity: e.get::<LinearVelocity>().map_or(Vec3::ZERO, |v| v.0),
            angular: e.get::<AngularVelocity>().map_or(Vec3::ZERO, |v| v.0),
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn preview_integrates_real_mass_force_and_stop() {
        let mut sim = Sandbox::default();
        let s = Settings {
            command: "Force".into(),
            direction: "Vector".into(),
            vector: [4., 0., 0.],
            mass: 2.,
            gravity: 0.,
            position: [0., 20., 0.],
            force_duration: 0.5,
            ..Default::default()
        };
        sim.reset(&s).unwrap();
        for _ in 0..64 {
            sim.step();
        }
        assert!(
            (sim.snapshot().velocity.x - 1.).abs() < 0.04,
            "{:?}",
            sim.snapshot().velocity
        );
        let s = Settings {
            command: "Stop".into(),
            vector: [4., 0., 0.],
            gravity: 0.,
            position: [0., 20., 0.],
            stop_after: 0.5,
            ..s
        };
        sim.reset(&s).unwrap();
        for _ in 0..64 {
            sim.step();
        }
        assert!(sim.snapshot().velocity.length() < 0.001);
        assert!((sim.snapshot().position.x - 2.).abs() < 0.1);
    }
    #[test]
    fn floor_collides_and_reset_is_repeatable() {
        let mut sim = Sandbox::default();
        let s = Settings {
            command: "Velocity".into(),
            vector: [0., 0., 0.],
            position: [0., 3., 0.],
            restitution: 0.,
            ..Default::default()
        };
        sim.reset(&s).unwrap();
        for _ in 0..256 {
            sim.step();
        }
        let first = sim.snapshot().position;
        assert!((first.y - 0.5).abs() < 0.06, "{first:?}");
        sim.reset(&s).unwrap();
        for _ in 0..256 {
            sim.step();
        }
        assert!(first.distance(sim.snapshot().position) < 0.001);
    }
    #[test]
    fn impulses_and_torque_use_mass_and_shape_inertia() {
        let mut sim = Sandbox::default();
        for mass in [1., 2.] {
            let s = Settings {
                command: "Impulse".into(),
                direction: "Vector".into(),
                vector: [4., 0., 0.],
                mass,
                gravity: 0.,
                position: [0., 20., 0.],
                ..Default::default()
            };
            sim.reset(&s).unwrap();
            sim.step();
            assert!((sim.snapshot().velocity.x - 4. / mass).abs() < 0.001);
            for (shape, expected) in [("Box", 12. / mass), ("Sphere", 20. / mass)] {
                let s = Settings {
                    command: "Torque impulse".into(),
                    vector: [0., 2., 0.],
                    shape: shape.into(),
                    ..s.clone()
                };
                sim.reset(&s).unwrap();
                sim.step();
                assert!(
                    (sim.snapshot().angular.y - expected).abs() < 0.02,
                    "{shape}: {:?}",
                    sim.snapshot().angular
                );
            }
        }
    }
    #[test]
    fn same_tick_commands_preserve_order_and_stop_cancels_force() {
        let mut sim = Sandbox::default();
        let s = Settings {
            command: "Force".into(),
            direction: "Vector".into(),
            vector: [4., 0., 0.],
            force_duration: 0.,
            gravity: 0.,
            position: [0., 20., 0.],
            ..Default::default()
        };
        sim.reset(&s).unwrap();
        sim.step();
        let body = sim.body.unwrap();
        let app = sim.app.as_mut().unwrap();
        {
            let mut c = app.world_mut().commands();
            scenemax_physics::enqueue(&mut c, body, &Action::Velocity([8., 0., 0.]));
            scenemax_physics::stop(&mut c, body);
            scenemax_physics::enqueue(&mut c, body, &Action::Impulse([2., 0., 0.]));
        }
        app.world_mut().flush();
        for _ in 0..64 {
            sim.step();
        }
        assert!((sim.snapshot().velocity.x - 2.).abs() < 0.001);
    }
}
