//! Entity stop is distinct from animation-controller stop. Java's MoveController,
//! RotateController and RotateToController observe it; MoveTo and clip playback
//! do not. Dropping motion leases uses the existing multiplayer end protocol.
use super::*;

pub(super) fn bind_wait(
    queue: &mut DelayedActionQueue,
    action: &Statement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
) {
    let target = match action {
        Statement::Move(a) => &a.target,
        Statement::Turn(a) => &a.target,
        _ => return,
    };
    if let Some(wait) = queue.actions.last_mut() {
        wait.motion_wait = Some(scenemax_runtime_vm_core::references::entity_name(
            target, vars, scope,
        ));
    }
}

pub(super) fn enqueue(
    action: &AnimationControllerActionStatement,
    commands: &mut Commands,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
) -> bool {
    if action.action != AnimationControllerAction::Stop {
        return false;
    }
    let Some(identity) =
        scenemax_runtime_vm_core::references::lookup(&action.controller, vars, scope)
            .and_then(|value| value.entity_identity())
    else {
        return false;
    };
    commands.queue(move |world: &mut World| {
        let entity = world
            .query::<(Entity, &SceneMaxEntity)>()
            .iter(world)
            .find_map(|(entity, scene)| (scene.name == identity.runtime_name).then_some(entity));
        let Some(entity) = entity else {
            return;
        };
        if let Some(mut queue) = world.get_resource_mut::<DelayedActionQueue>() {
            for wait in &mut queue.actions {
                if wait.motion_wait.as_deref() == Some(identity.runtime_name.as_str()) {
                    wait.remaining_seconds = 0.;
                    wait.motion_wait = None;
                }
            }
        }
        if let Some(mut moves) = world.get_mut::<TimedMoves>(entity) {
            // Destination commands are a separate Java controller family.
            moves
                .moves
                .retain(|movement| movement.final_translation.is_some());
            if moves.moves.is_empty() {
                world.entity_mut(entity).remove::<TimedMoves>();
            }
        }
        world.entity_mut(entity).remove::<TimedTurns>();
        if let Some(mut motor) = world.get_mut::<SceneMaxCharacterMotor>(entity) {
            motor.network_lifetime = None;
            motor.timed_motion_owner = None;
            motor.desired_motion = Vec3::ZERO;
            motor.motion_ttl_seconds = 0.;
            motor.timed_motion = Vec3::ZERO;
            motor.timed_motion_remaining_seconds = 0.;
            motor.timed_motion_loop = None;
        }
    });
    true
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn stop_drops_motion_leases_but_preserves_animation_physics_and_jump() {
        let mut world = World::new();
        crate::value_calls::tests::run_in(
            &mut world,
            "subject => box\nsubject.move(x+5) in 5 seconds async\nsubject.rotate(y+90) in 5 seconds async",
        );
        while !world.resource::<DelayedActionQueue>().actions.is_empty() {
            crate::value_calls::tests::resume_next(&mut world);
        }
        let entity = world
            .query::<(Entity, &SceneMaxEntity)>()
            .iter(&world)
            .find(|(_, s)| s.name == "subject")
            .unwrap()
            .0;
        let lease = std::sync::Arc::new(());
        let weak = std::sync::Arc::downgrade(&lease);
        world.get_mut::<TimedMoves>(entity).unwrap().moves[0].network_lifetime =
            Some(lease.clone());
        world.get_mut::<TimedTurns>(entity).unwrap().turns[0].network_lifetime = Some(lease);
        world.entity_mut(entity).insert((
            LinearVelocity(Vec3::X),
            Visibility::Hidden,
            TimedJump {
                elapsed_seconds: 0.,
                duration_seconds: 2.,
                start_y: 0.,
                height: 2.,
            },
            CurrentAnimation {
                rewind: None,
                identity: None,
                controller_owner: None,
                network_lifetime: None,
                playback_graphs: vec![],
                clip: "fixture".into(),
                looped: true,
                speed: 1.,
                elapsed_seconds: 0.,
                duration_seconds: 2.,
            },
            SceneMaxCharacterMotor {
                timed_motion: Vec3::X,
                timed_motion_remaining_seconds: 5.,
                pending_jump_speed: Some(3.),
                ..default()
            },
        ));
        crate::value_calls::tests::run_in(&mut world, "subject.stop");
        assert!(weak.upgrade().is_none());
        assert!(world.get::<CurrentAnimation>(entity).is_some());
        assert!(world.get::<TimedJump>(entity).is_some());
        assert_eq!(world.get::<LinearVelocity>(entity).unwrap().0, Vec3::X);
        assert_eq!(
            *world.get::<Visibility>(entity).unwrap(),
            Visibility::Hidden
        );
        let motor = world.get::<SceneMaxCharacterMotor>(entity).unwrap();
        assert_eq!(motor.timed_motion_remaining_seconds, 0.);
        assert_eq!(motor.pending_jump_speed, Some(3.));
    }
}
