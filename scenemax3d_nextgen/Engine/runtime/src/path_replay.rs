use super::*;
use scenemax_parser::path_replay::Action;

type Pose = (Vec3, Quat);

#[derive(Component)]
pub(super) struct Replay {
    poses: Vec<Pose>,
    offsets: [f32; 6],
    duration: f32,
    elapsed: f32,
    paused: bool,
    repeat: Option<Condition>,
    scope: Option<SceneMaxScopeFrame>,
}

fn poses(
    name: &str,
    offsets: [f32; 6],
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
) -> Option<Vec<Pose>> {
    let value = scenemax_runtime_vm_core::typed::lookup(name, vars, scope)?;
    decode(&value, offsets)
}

fn decode(value: &serde_json::Value, offsets: [f32; 6]) -> Option<Vec<Pose>> {
    let values = value.as_array()?;
    if values.is_empty() || values.len() % 6 != 0 {
        return None;
    }
    values
        .chunks_exact(6)
        .map(|chunk| {
            let mut v = offsets;
            for (field, raw) in v.iter_mut().zip(chunk) {
                *field += raw.as_f64()? as f32;
                if !field.is_finite() {
                    return None;
                }
            }
            Some((
                Vec3::new(v[0], v[1], v[2]),
                rotation_from_degrees(SceneMaxVec3 {
                    x: v[3],
                    y: v[4],
                    z: v[5],
                })
                .normalize(),
            ))
        })
        .collect()
}

pub(super) fn dispatch(
    commands: &mut Commands,
    entity: Entity,
    action: &Action,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
    bounds: &SceneMaxColliderBounds,
) {
    let number = |expr: &AssignmentValue| {
        resolve_assignment_value_scoped_with_guards(
            expr,
            vars,
            scope,
            guards,
            Some(transforms),
            Some(bounds),
        )
        .filter(|v| v.is_finite())
    };
    match action {
        Action::Play {
            array,
            duration,
            start,
            offsets,
            repeat,
        } => {
            let Some(duration) = number(duration).filter(|d| *d > 0.) else {
                warn!("Invalid path replay duration");
                return;
            };
            let mut delta = [0.; 6];
            for (axis, expr) in offsets {
                let Some(value) = number(expr) else {
                    warn!("Invalid path replay offset");
                    return;
                };
                delta[*axis] = value;
            }
            let Some(poses) = poses(array, delta, vars, scope) else {
                warn!("Invalid path replay array {array}");
                return;
            };
            let start = start.as_ref().map_or(Some(0.), number);
            let Some(start) =
                start.filter(|s| *s >= 0. && s.fract() == 0. && (*s as usize) < poses.len())
            else {
                warn!("Invalid path replay start");
                return;
            };
            let elapsed = duration * (start / (poses.len() - 1).max(1) as f32);
            commands.entity(entity).insert(Replay {
                poses,
                offsets: delta,
                duration,
                elapsed,
                paused: false,
                repeat: repeat.clone(),
                scope: scope.cloned(),
            });
        }
        Action::Stop => {
            commands.entity(entity).remove::<Replay>();
        }
        Action::Pause | Action::Resume => {
            let paused = matches!(action, Action::Pause);
            commands.entity(entity).queue(move |mut e: EntityWorldMut| {
                if let Some(mut r) = e.get_mut::<Replay>() {
                    r.paused = paused;
                }
            });
        }
        Action::Speed(expr) => {
            let Some(duration) = number(expr).filter(|d| *d > 0.) else {
                warn!("Invalid path replay duration");
                return;
            };
            commands.entity(entity).queue(move |mut e: EntityWorldMut| {
                if let Some(mut r) = e.get_mut::<Replay>() {
                    r.elapsed = (r.elapsed / r.duration) * duration;
                    r.duration = duration;
                }
            });
        }
        Action::Switch(array) => {
            let Some(samples) = scenemax_runtime_vm_core::typed::lookup(array, vars, scope) else {
                warn!("Missing path replay array {array}");
                return;
            };
            commands.entity(entity).queue(move |mut e: EntityWorldMut| {
                if let Some(mut r) = e.get_mut::<Replay>() {
                    if let Some(new_poses) = decode(&samples, r.offsets) {
                        r.poses = new_poses;
                        r.elapsed = 0.;
                    } else {
                        warn!("Invalid path replay switch array");
                    }
                }
            });
        }
    }
}

fn sample(poses: &[Pose], fraction: f32) -> Pose {
    let frame = fraction.clamp(0., 1.) * (poses.len() - 1) as f32;
    let i = (frame as usize).min(poses.len() - 1);
    let a = poses[i];
    let b = poses[(i + 1).min(poses.len() - 1)];
    (a.0.lerp(b.0, frame.fract()), a.1.slerp(b.1, frame.fract()))
}

pub(super) fn advance(
    time: Res<Time>,
    vars: Res<SceneMaxVars>,
    program: Res<SceneMaxStartupProgram>,
    bounds: Res<SceneMaxColliderBounds>,
    mut commands: Commands,
    mut set: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(Entity, &mut Transform, &mut Replay)>,
    )>,
) {
    let delta = time.delta_secs();
    let needs_context = set
        .p1()
        .iter()
        .any(|(_, _, r)| !r.paused && r.repeat.is_some() && r.elapsed + delta >= r.duration);
    let context = needs_context.then(|| {
        (
            program
                .0
                .as_ref()
                .map(collect_guards_by_name)
                .unwrap_or_default(),
            set.p0()
                .iter()
                .map(|(e, t)| (e.name.clone(), *t))
                .collect::<HashMap<_, _>>(),
        )
    });
    for (entity, mut transform, mut replay) in &mut set.p1() {
        if replay.paused {
            continue;
        }
        replay.elapsed += delta;
        if replay.elapsed >= replay.duration {
            if replay.repeat.as_ref().zip(context.as_ref()).is_some_and(
                |(cond, (guards, transforms))| {
                    condition_matches_scoped(
                        cond,
                        &vars,
                        replay.scope.as_ref(),
                        guards,
                        Some(transforms),
                        Some(&bounds),
                    )
                },
            ) {
                replay.elapsed %= replay.duration;
            } else {
                replay.elapsed = replay.duration;
                commands.entity(entity).remove::<Replay>();
            }
        }
        (transform.translation, transform.rotation) =
            sample(&replay.poses, replay.elapsed / replay.duration);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn packed_samples_reach_each_waypoint_and_take_the_short_turn() {
        let route = decode(
            &json!([0, 0, 0, 0, 350, 0, 10, 0, 0, 0, 10, 0, 20, 0, 0, 0, 90, 0]),
            [0.; 6],
        )
        .unwrap();
        let (position, rotation) = sample(&route, 0.25);
        assert_eq!(position, Vec3::X * 5.);
        assert!((rotation * Vec3::Z - Vec3::Z).length() < 1e-5);
        assert_eq!(sample(&route, 0.5).0, route[1].0);
        assert_eq!(sample(&route, 1.).0, route[2].0);
        assert!(decode(&json!([1, 2]), [0.; 6]).is_none());
    }
}
