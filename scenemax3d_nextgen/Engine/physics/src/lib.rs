//! Shared Avian physics command adapter. No editor or game-specific state.
use avian3d::prelude::*;
use bevy::prelude::*;
pub use scenemax_parser::physics_motion::Action;
#[derive(Component, Default)]
struct Pending(Vec<Option<Action>>);
#[derive(Component, Default)]
struct Applied {
    force: Vec3,
    remaining: Option<f32>,
    torque: Vec3,
}
/// Installs command integration into the same physics schedule used by the runtime.
pub struct MotionPlugin;
impl Plugin for MotionPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(
            PhysicsSchedule,
            integrate
                .after(PhysicsStepSystems::First)
                .before(PhysicsStepSystems::BroadPhase),
        );
    }
}
/// Configure or queue an action. Multiple actions on the same frame retain source order.
pub fn enqueue(commands: &mut Commands, entity: Entity, action: &Action) {
    match *action {
        Action::Configure {
            mass,
            drag,
            gravity,
            restitution,
            friction,
        } => {
            commands.queue(move |world: &mut World| {
                let properties = world.get::<Collider>(entity).map(|collider| {
                    let unit_mass = Mass::from_shape(collider, 1.).0;
                    MassPropertiesBundle::from_shape(collider, mass / unit_mass.max(0.000001))
                });
                if let Some(properties) = properties
                    && let Ok(mut entity) = world.get_entity_mut(entity)
                {
                    entity.insert(properties);
                }
            });
            commands.entity(entity).insert((
                Mass(mass),
                LinearDamping(drag),
                AngularDamping(drag),
                GravityScale(0.),
                ConstantLinearAcceleration::new(0., -gravity, 0.),
                Restitution::new(restitution),
                Friction::new(friction),
            ));
        }
        _ => {
            queue(commands, entity, Some(action.clone()));
        }
    }
}
fn queue(commands: &mut Commands, entity: Entity, action: Option<Action>) {
    commands.queue(move |world: &mut World| {
        if let Ok(mut e) = world.get_entity_mut(entity) {
            e.remove::<Sleeping>();
            if let Some(mut pending) = e.get_mut::<Pending>() {
                pending.0.push(action);
            } else {
                e.insert(Pending(vec![action]));
            }
            if !e.contains::<Applied>() {
                e.insert(Applied::default());
            }
        }
    });
}
/// Stop immediately and discard queued/sustained actions, preserving legacy stop ordering.
pub fn stop(commands: &mut Commands, entity: Entity) {
    commands
        .entity(entity)
        .insert((LinearVelocity::ZERO, AngularVelocity::ZERO))
        .remove::<(Pending, Applied)>();
}
type BodyQueries<'w, 's> = ParamSet<
    'w,
    's,
    (
        Query<'w, 's, Forces>,
        Query<'w, 's, (&'static mut LinearVelocity, &'static mut AngularVelocity)>,
    ),
>;
#[derive(bevy::ecs::system::SystemParam)]
struct Bodies<'w, 's> {
    queries: BodyQueries<'w, 's>,
}
fn integrate(
    time: Res<Time>,
    mut q: Query<(Entity, &mut Pending, &mut Applied)>,
    mut bodies: Bodies,
) {
    for (entity, mut pending, mut active) in &mut q {
        for action in pending.0.drain(..) {
            match action {
                Some(Action::Velocity(v)) => {
                    if let Ok((mut linear, _)) = bodies.queries.p1().get_mut(entity) {
                        linear.0 = Vec3::from_array(v);
                    }
                }
                Some(Action::AngularVelocity(v)) => {
                    if let Ok((_, mut angular)) = bodies.queries.p1().get_mut(entity) {
                        angular.0 = Vec3::from_array(v);
                    }
                }
                None => {
                    *active = Applied::default();
                    if let Ok((mut linear, mut angular)) = bodies.queries.p1().get_mut(entity) {
                        linear.0 = Vec3::ZERO;
                        angular.0 = Vec3::ZERO;
                    }
                }
                Some(Action::Impulse(v)) => {
                    if let Ok(mut body) = bodies.queries.p0().get_mut(entity) {
                        body.apply_linear_impulse(Vec3::from_array(v));
                    }
                }
                Some(Action::Force(v, remaining)) => {
                    active.force = Vec3::from_array(v);
                    active.remaining = remaining;
                }
                Some(Action::Torque(v, true)) => {
                    if let Ok(mut body) = bodies.queries.p0().get_mut(entity) {
                        body.apply_angular_impulse(Vec3::from_array(v));
                    }
                }
                Some(Action::Torque(v, false)) => active.torque = Vec3::from_array(v),
                _ => {}
            }
        }
        let dt = time.delta_secs();
        let fraction = active
            .remaining
            .map_or(1., |t| if dt > 0. { (t / dt).clamp(0., 1.) } else { 0. });
        if let Ok(mut body) = bodies.queries.p0().get_mut(entity) {
            if active.force != Vec3::ZERO && fraction > 0. {
                body.apply_force(active.force * fraction);
            }
            if active.torque != Vec3::ZERO {
                body.apply_torque(active.torque);
            }
        }
        if let Some(t) = active.remaining.as_mut() {
            *t = (*t - dt).max(0.);
        }
    }
}
