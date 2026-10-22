//! Rewind trajectories preserve original endpoints and wall-clock phase.
use super::*;
use scenemax_parser::animation_rewind::Playback as Spec;
#[derive(Debug, Clone)]
pub(super) struct Playback {
    pub spec: Spec,
    pub elapsed: f32,
    pub curve: motion_ease::Curve,
    pub _waiter: Option<multiplayer::ActionLease>,
}
#[derive(Debug)]
pub(super) struct Pending {
    controller: String,
    run: std::sync::Weak<()>,
    percent: f32,
    duration: f32,
    easing: Option<scenemax_parser::motion_ease::MotionEase>,
    waiter: multiplayer::ActionLease,
}
pub(super) fn enqueue(
    action: &AnimationControllerActionStatement,
    assets: &mut SceneMaxRuntimeAssets,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
) {
    let Some(r) = &action.rewind else { return };
    let number = |v: &AssignmentValue| {
        resolve_assignment_value_scoped_with_guards(v, vars, scope, guards, Some(transforms), None)
    };
    let (Some(percent), Some(duration)) = (number(&r.percent), number(&r.duration)) else {
        return;
    };
    if !percent.is_finite() || !duration.is_finite() {
        return;
    }
    let easing = motion_ease::Curve::resolve(&r.easing, number).literal_spec();
    let Some(c) = assets
        .animation_controllers_by_name
        .get_mut(&action.controller)
    else {
        return;
    };
    let Some(run) = c.run_lifetime.as_ref() else {
        return;
    };
    let waiter = multiplayer::ActionLease::new(());
    c.rewind_wait = std::sync::Arc::downgrade(&waiter);
    assets.pending_animation_rewinds.push(Pending {
        controller: action.controller.clone(),
        run: std::sync::Arc::downgrade(run),
        percent: percent.max(0.),
        duration: duration.max(0.),
        easing,
        waiter,
    });
}
pub(super) fn apply_pending(
    mut commands: Commands,
    mut assets: ResMut<SceneMaxRuntimeAssets>,
    mut vars: ResMut<SceneMaxVars>,
    mut roots: Query<(
        &SceneMaxEntity,
        &mut CurrentAnimation,
        Option<&AnimationSpeedOverride>,
    )>,
) {
    for p in std::mem::take(&mut assets.pending_animation_rewinds) {
        let Some(c) = assets.animation_controllers_by_name.get_mut(&p.controller) else {
            continue;
        };
        if !c.running
            || c.run_lifetime
                .as_ref()
                .is_none_or(|r| !p.run.ptr_eq(&std::sync::Arc::downgrade(r)))
        {
            continue;
        }
        let Some((entity, _, _, _)) = c.source.as_ref() else {
            continue;
        };
        let Ok((name, mut current, override_speed)) = roots.get_mut(*entity) else {
            continue;
        };
        if current
            .identity
            .as_ref()
            .is_none_or(|id| !id.ptr_eq(&c.lifetime))
        {
            continue;
        }
        let start = current.elapsed_seconds.clamp(0., current.duration_seconds);
        let target = (start - current.duration_seconds * p.percent / 100.).max(0.);
        let speed = override_speed
            .map(|s| s.speed)
            .unwrap_or(current.speed)
            .max(0.001);
        let spec = Spec {
            start,
            target,
            duration: p.duration,
            easing: p.easing,
        };
        let mut animation = c.animation.clone();
        animation.target = name.name.clone();
        animation.speed = speed;
        animation.speed_value = AssignmentValue::Number(speed);
        animation.rewind = Some(spec.clone());
        let lease = multiplayer::capture(
            &mut commands,
            &Statement::Animate(animation),
            &mut vars,
            None,
            &HashMap::new(),
            &HashMap::new(),
        )
        .unwrap_or_else(|| multiplayer::ActionLease::new(()));
        c.lifetime = std::sync::Arc::downgrade(&lease);
        c.advance_pending = false;
        current.identity = Some(c.lifetime.clone());
        current.network_lifetime = Some(lease);
        current.speed = speed;
        current.rewind = Some(Playback {
            curve: motion_ease::Curve::literal(&spec.easing),
            spec: spec.clone(),
            elapsed: 0.,
            _waiter: Some(p.waiter),
        });
        write_runtime_diagnostic_line(format!(
            "ANIMCTRL:REWIND controller={} start={} target={} duration={} speed={}",
            p.controller, start, target, spec.duration, speed
        ));
    }
}
impl Playback {
    pub(super) fn position(&self, duration: f32, speed: f32) -> f32 {
        if self.elapsed >= self.spec.duration {
            return (self.spec.target + (self.elapsed - self.spec.duration) * speed)
                .clamp(0., duration);
        }
        (self.spec.start
            + (self.spec.target - self.spec.start)
                * self.curve.apply(self.elapsed / self.spec.duration))
        .clamp(0., duration)
    }
    pub(super) fn remaining(&self, duration: f32, speed: f32) -> f32 {
        (self.spec.duration - self.elapsed).max(0.)
            + (duration - self.spec.target).max(0.) / speed.max(0.001)
    }
}
pub(super) fn resume(
    spec: &Spec,
    duration: f32,
    speed: f32,
    resume: Option<&NetworkAnimationResume>,
) -> (f32, Option<Playback>) {
    let mut spec = spec.clone();
    spec.start = spec.start.min(duration);
    spec.target = spec.target.min(spec.start);
    let total = spec.duration + (duration - spec.target) / speed.max(0.001);
    let age = resume
        .map(|r| {
            (total - (r.remaining_seconds - r.received_at.elapsed().as_secs_f64()).max(0.) as f32).max(0.)
        })
        .unwrap_or(0.);
    let p = Playback {
        curve: motion_ease::Curve::literal(&spec.easing),
        spec,
        elapsed: age,
        _waiter: None,
    };
    let seek = p.position(duration, speed);
    let running = p.elapsed < p.spec.duration;
    (seek, running.then_some(p))
}
pub(super) fn wire(spec: &Option<Spec>) -> String {
    spec.as_ref()
        .map(|s| {
            format!(
                " rewind from {} to {} in {} seconds{}",
                s.start,
                s.target,
                s.duration,
                motion_ease::Curve::literal(&s.easing).wire()
            )
        })
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    fn spec() -> Spec {
        Spec {
            start: 1.2,
            target: 0.3,
            duration: 4.,
            easing: Some(scenemax_parser::motion_ease::MotionEase {
                mode: scenemax_parser::motion_ease::EaseMode::In,
                family: "Cubic".into(),
                params: vec![],
                start_progress: 0.,
            }),
        }
    }
    #[test]
    fn snapshots_resume_curve_phase_and_forward_playback() {
        let s = spec();
        let owner = SceneMaxControllerKey::NetworkAction {
            entity: 1,
            slot: 3,
            sequence: 1,
        };
        for age in [0., 1., 2., 4., 5.] {
            let total = 4. + (2. - 0.3) / 0.5;
            let resume_state = NetworkAnimationResume {
                owner: owner.clone(),
                remaining_seconds: f64::from(total - age),
                received_at: web_time::Instant::now(),
            };
            let (seek, state) = resume(&s, 2., 0.5, Some(&resume_state));
            let expected = if age < 4. {
                1.2 - 0.9 * (age / 4.0_f32).powi(3)
            } else {
                0.3 + (age - 4.) * 0.5
            };
            assert!(
                (seek - expected).abs() < 0.002,
                "{age}: {seek} != {expected}"
            );
            assert_eq!(state.is_some(), age < 4.);
        }
        let mut zero = s.clone();
        zero.duration = 0.;
        assert!((resume(&zero, 2., 0.5, None).0 - 0.3).abs() < 0.00001);
    }
    #[test]
    fn rewind_seeks_bevy_player_pauses_and_releases_waiter_then_resumes() {
        let mut world = World::new();
        world.init_resource::<SceneMaxVars>();
        let mut time = Time::<()>::default();
        time.advance_by(std::time::Duration::from_secs(1));
        world.insert_resource(time);
        let node = bevy::animation::graph::AnimationNodeIndex::new(1);
        let mut player = AnimationPlayer::default();
        player.play(node).set_speed(0.5);
        let player_entity = world.spawn((player, AnimationGraphHandle(Handle::default()))).id();
        let token = multiplayer::ActionLease::new(());
        let waiter = std::sync::Arc::downgrade(&token);
        let spec = spec();
        let entity = world
            .spawn((
                SceneMaxEntity {
                    name: "subject".into(),
                    runtime_name: "subject".into(),
                },
                CurrentAnimation {
                    rewind: Some(Playback {
                        curve: motion_ease::Curve::literal(&spec.easing),
                        spec,
                        elapsed: 0.,
                        _waiter: Some(token),
                    }),
                    identity: None,
                    controller_owner: None,
                    network_lifetime: Some(multiplayer::ActionLease::new(())),
                    playback_graphs: vec![(player_entity, Handle::default())],
                    clip: "take".into(),
                    looped: false,
                    speed: 0.5,
                    elapsed_seconds: 1.2,
                    duration_seconds: 2.,
                },
            ))
            .id();
        world
            .run_system_once(update_current_animation_vars)
            .unwrap();
        let active = world
            .get::<AnimationPlayer>(player_entity)
            .unwrap()
            .animation(node)
            .unwrap();
        assert!((active.seek_time() - 1.1859375).abs() < 0.0001);
        assert_eq!(active.speed(), 0.);
        assert_eq!(waiter.strong_count(), 1);
        world
            .resource_mut::<Time>()
            .advance_by(std::time::Duration::from_secs(3));
        world
            .run_system_once(update_current_animation_vars)
            .unwrap();
        let active = world
            .get::<AnimationPlayer>(player_entity)
            .unwrap()
            .animation(node)
            .unwrap();
        assert!((active.seek_time() - 0.3).abs() < 0.0001);
        assert_eq!(active.speed(), 0.5);
        assert!(world
            .get::<CurrentAnimation>(entity)
            .unwrap()
            .rewind
            .is_none());
        assert_eq!(waiter.strong_count(), 0);
    }
}
