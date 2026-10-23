//! Resolve script operands once, then attach generic motion to the local view camera.
use super::*;

enum Motion {
    Position(Vec3),
    LookAt(Vec3),
    Rotation(Vec3),
    Turn(scenemax_parser::TurnStatement, f32, f32, motion_ease::Curve),
    Move(scenemax_parser::MoveStatement, f32, f32, motion_ease::Curve),
    Destination(Vec3, bool, f32, motion_ease::Curve),
    Axis(SceneMaxAxis, f32, f32),
}

pub(crate) fn enqueue(
    commands: &mut Commands,
    action: &Statement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
    bounds: &SceneMaxColliderBounds,
    owner: Option<SceneMaxControllerKey>,
    continuous: Option<f32>,
) -> bool {
    let number = |value: &AssignmentValue, fallback| {
        resolve_assignment_value_scoped_with_guards(
            value,
            vars,
            scope,
            guards,
            Some(transforms),
            Some(bounds),
        )
        .filter(|v| v.is_finite())
        .unwrap_or(fallback)
    };
    let curve = |ease| motion_ease::resolve(ease, vars, scope, guards, transforms);
    let resolved = match action {
        Statement::LookAt { target, subject } if target.eq_ignore_ascii_case("camera") => {
            lookup_subject_transform(subject, transforms).map(|t| Motion::LookAt(t.translation))
        }
        Statement::CameraPosition(p) => Some(Motion::Position(Vec3::new(p.x, p.y, p.z))),
        Statement::CameraRotation(p) => Some(Motion::Rotation(Vec3::new(p.x, p.y, p.z))),
        Statement::Position(p) if p.target.eq_ignore_ascii_case("camera") => {
            evaluate_position_value_runtime(
                &p.position,
                vars,
                scope,
                guards,
                transforms,
                Some(bounds),
            )
            .map(Motion::Position)
        }
        Statement::RotationReset { target, rotation } if target.eq_ignore_ascii_case("camera") => {
            evaluate_position_value_runtime(rotation, vars, scope, guards, transforms, Some(bounds))
                .map(Motion::Rotation)
        }
        Statement::CameraMove(m) => Some(Motion::Axis(
            m.axis,
            number(&m.distance_value, m.distance),
            number(&m.duration_value, m.duration_seconds),
        )),
        Statement::Turn(t) if t.target.eq_ignore_ascii_case("camera") => Some(Motion::Turn(
            t.clone(),
            number(&t.degrees_value, t.degrees),
            number(&t.duration_value, t.duration_seconds),
            curve(&t.easing),
        )),
        Statement::Move(m) if m.target.eq_ignore_ascii_case("camera") => Some(Motion::Move(
            m.clone(),
            number(&m.distance_value, m.distance),
            number(&m.duration_value, m.duration_seconds),
            curve(&m.easing),
        )),
        Statement::MoveTo(m) if m.target.eq_ignore_ascii_case("camera") => {
            evaluate_move_to_destination_runtime(
                &m.destination,
                vars,
                scope,
                guards,
                transforms,
                Some(bounds),
            )
            .map(|dest| {
                Motion::Destination(
                    dest,
                    matches!(m.destination, MoveToDestination::Delta(_)),
                    number(&m.duration_value, m.duration_seconds),
                    curve(&m.easing),
                )
            })
        }
        _ => return false,
    };
    if let Some(motion) = resolved {
        commands.queue(move |world: &mut World| apply(world, motion, owner, continuous));
    }
    true
}

fn apply(
    world: &mut World,
    motion: Motion,
    owner: Option<SceneMaxControllerKey>,
    continuous: Option<f32>,
) {
    let Some((entity, mut transform)) = world
        .query_filtered::<(Entity, &Transform), (With<Camera3d>, Without<SceneMaxEntity>)>()
        .iter(world)
        .next()
        .map(|(e, t)| (e, *t))
    else {
        return;
    };
    let mut movement = None;
    let mut turn = None;
    match motion {
        Motion::Position(p) if p.is_finite() => transform.translation = p,
        Motion::LookAt(p)
            if p.is_finite() && p.distance_squared(transform.translation) > f32::EPSILON =>
        {
            transform.look_at(p, Vec3::Y)
        }
        Motion::Rotation(p) if p.is_finite() => {
            transform.rotation = camera_rotation_from_degrees(SceneMaxVec3 {
                x: p.x,
                y: p.y,
                z: p.z,
            })
        }
        Motion::Turn(t, degrees, duration, curve) => {
            let mut controller = timed_turn_from_statement_resolved(
                &t,
                degrees,
                duration,
                script_rotation(transform.rotation),
            );
            controller.curve = curve;
            controller.controller_owner = owner;
            if let Some(delta) = continuous {
                apply_camera_turn(
                    &mut transform,
                    &controller,
                    if t.absolute {
                        delta.min(controller.duration_seconds)
                    } else {
                        delta
                    },
                );
            } else {
                turn = Some(controller);
            }
        }
        Motion::Move(m, distance, duration, curve) => {
            let mut controller =
                timed_move_from_statement_resolved(&m, &transform, distance, duration);
            controller.character_motion = false;
            controller.velocity = camera_direction(m.direction, &transform)
                * directional_move_speed_resolved(distance, duration);
            controller.curve = curve;
            controller.controller_owner = owner;
            if let Some(delta) = continuous {
                let delta = if m.direction.is_axis() {
                    delta.min(controller.duration_seconds)
                } else {
                    delta
                };
                transform.translation += controller.velocity
                    * controller.curve.step(
                        controller.duration_seconds,
                        controller.remaining_seconds,
                        delta,
                    );
            } else {
                movement = Some(controller);
            }
        }
        Motion::Destination(destination, relative, duration, curve) => {
            let duration = duration.max(0.001);
            let delta = if relative {
                destination
            } else {
                destination - transform.translation
            };
            let end =
                transform.translation + delta * curve.step(duration, duration, duration) / duration;
            let controller = TimedMove {
                character_motion: false,
                curve,
                network_lifetime: None,
                controller_owner: owner,
                remaining_seconds: duration,
                duration_seconds: duration,
                velocity: delta / duration,
                final_translation: Some(end),
                loop_condition: None,
            };
            if let Some(dt) = continuous {
                transform.translation += controller.velocity
                    * controller.curve.step(duration, duration, dt.min(duration));
            } else {
                movement = Some(controller);
            }
        }
        Motion::Axis(axis, distance, duration) => {
            let duration = duration.max(0.001);
            let direction = match axis {
                SceneMaxAxis::X => Vec3::X,
                SceneMaxAxis::Y => Vec3::Y,
                SceneMaxAxis::Z => Vec3::Z,
            };
            if let Some(dt) = continuous {
                transform.translation += direction * distance * dt.min(duration) / duration;
            } else {
                movement = Some(TimedMove {
                    character_motion: false,
                    curve: Default::default(),
                    network_lifetime: None,
                    controller_owner: owner,
                    remaining_seconds: duration,
                    duration_seconds: duration,
                    velocity: direction * distance / duration,
                    final_translation: None,
                    loop_condition: None,
                });
            }
        }
        _ => return,
    }
    let mut camera = world.entity_mut(entity);
    camera.insert(transform);
    if let Some(m) = movement {
        if let Some(mut existing) = camera.get_mut::<TimedMoves>() {
            existing.moves.push(m);
        } else {
            camera.insert(TimedMoves { moves: vec![m] });
        }
    }
    if let Some(t) = turn {
        if let Some(mut existing) = camera.get_mut::<TimedTurns>() {
            existing.turns.push(t);
        } else {
            camera.insert(TimedTurns { turns: vec![t] });
        }
    }
}

fn script_rotation(rotation: Quat) -> Quat {
    rotation * Quat::from_rotation_y(-std::f32::consts::PI)
}
fn camera_direction(direction: MoveDirection, transform: &Transform) -> Vec3 {
    match direction {
        MoveDirection::Forward => *transform.forward(),
        MoveDirection::Backward => *transform.back(),
        MoveDirection::Left => *transform.left() * Vec3::new(1., 0., 1.),
        MoveDirection::Right => *transform.right() * Vec3::new(1., 0., 1.),
        MoveDirection::Up => *transform.up() * Vec3::Y,
        MoveDirection::Down => *transform.down() * Vec3::Y,
        _ => movement_direction_vector(direction, transform),
    }
}
pub(crate) fn apply_camera_turn(transform: &mut Transform, turn: &TimedTurn, dt: f32) {
    let (mut y, mut z, mut x) = script_rotation(transform.rotation).to_euler(EulerRot::YZX);
    let delta = turn.radians_per_second
        * turn
            .curve
            .step(turn.duration_seconds, turn.remaining_seconds, dt);
    match turn.axis {
        Some(scenemax_parser::RotationAxis::X) => x += delta,
        Some(scenemax_parser::RotationAxis::Z) => z += delta,
        _ => y += delta,
    }
    transform.rotation =
        Quat::from_euler(EulerRot::YZX, y, z, x) * Quat::from_rotation_y(std::f32::consts::PI);
}
pub(crate) fn camera_target_delta(
    axis: scenemax_parser::RotationAxis,
    target: f32,
    rotation: Quat,
) -> f32 {
    target_turn_degrees(axis, target, script_rotation(rotation))
}

#[cfg(test)]
mod tests;

/// The view entity survives scene changes; its old script controllers must not.
pub(crate) fn clear(commands: &mut Commands) {
    commands.queue(|world: &mut World| {
        let cameras: Vec<_> = world
            .query_filtered::<Entity, (With<Camera3d>, Without<SceneMaxEntity>)>()
            .iter(world)
            .collect();
        for camera in cameras {
            world
                .entity_mut(camera)
                .remove::<(TimedMoves, TimedTurns, TimedCameraMoves)>();
        }
    });
}
