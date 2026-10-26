//! Reconcile periodic poses without repeatedly teleporting a replayed controller.
use super::*;

/// Commands drive playback; poses are occasional drift repair, not a stream.
#[derive(Default)]
pub(super) struct SendSchedule(HashMap<u32, (Instant, Pose)>);

impl SendSchedule {
    pub(super) fn due(&self, request: u32, pose: Pose, now: Instant) -> bool {
        let Some((sent, previous)) = self.0.get(&request) else {
            return true;
        };
        if now.saturating_duration_since(*sent) < Duration::from_secs(5) {
            return false;
        }
        Vec3::from_array(previous.position).distance_squared(Vec3::from_array(pose.position))
            >= 0.0004
            || Quat::from_array(previous.rotation)
                .dot(Quat::from_array(pose.rotation))
                .abs()
                <= 0.998
    }
    pub(super) fn sent(&mut self, request: u32, pose: Pose, now: Instant) {
        self.0.insert(request, (now, pose));
    }
    pub(super) fn remove(&mut self, request: u32) {
        self.0.remove(&request);
    }
    pub(super) fn clear(&mut self) {
        self.0.clear();
    }
}

#[derive(Default)]
pub(super) struct Correction {
    initialized: bool,
    translation: Vec3,
    rotation: Quat,
    remaining: f32,
}

impl Correction {
    pub(super) fn discard(&mut self, rotation: bool) {
        if rotation {
            self.rotation = Quat::IDENTITY;
        } else {
            self.translation = Vec3::ZERO;
        }
    }

    pub(super) fn receive(&mut self, transform: &mut Transform, pose: Pose) {
        let position = Vec3::from_array(pose.position);
        let rotation = Quat::from_array(pose.rotation);
        if !position.is_finite() || !rotation.is_finite() || rotation.length_squared() < 0.0001 {
            return;
        }
        let rotation = rotation.normalize();
        if !self.initialized {
            transform.translation = position;
            transform.rotation = rotation;
            self.initialized = true;
            return;
        }
        self.translation = position - transform.translation;
        self.rotation = rotation * transform.rotation.inverse();
        self.remaining = 0.15;
    }

    pub(super) fn advance(&mut self, transform: &mut Transform, delta: f32) {
        if self.remaining <= 0. || delta <= 0. {
            return;
        }
        let step = delta.min(self.remaining);
        let fraction = step / self.remaining;
        let translation = self.translation * fraction;
        let rotation = Quat::IDENTITY.slerp(self.rotation, fraction);
        // Apply the remaining error, not a stale absolute target. Movement and
        // rotation replayed between packets must continue to advance normally.
        transform.translation += translation;
        transform.rotation = (rotation * transform.rotation).normalize();
        self.translation -= translation;
        self.rotation = (self.rotation * rotation.inverse()).normalize();
        self.remaining -= step;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn pose(x: f32, yaw: f32) -> Pose {
        Pose {
            position: [x, 0., 0.],
            rotation: Quat::from_rotation_y(yaw).to_array(),
        }
    }

    #[test]
    fn moving_entities_send_initial_pose_then_at_most_once_every_five_seconds() {
        let start = Instant::now();
        let mut schedule = SendSchedule::default();
        let mut sent = Vec::new();
        // Simulate 60 Hz movement for ten seconds without sleeping.
        for frame in 0..=600 {
            let now = start + Duration::from_secs_f64(frame as f64 / 60.);
            let pose = pose(frame as f32, frame as f32 * 0.01);
            if schedule.due(1, pose, now) {
                schedule.sent(1, pose, now);
                sent.push(frame);
            }
        }
        assert_eq!(sent, vec![0, 300, 600]);
        assert!(schedule.due(2, pose(0., 0.), start + Duration::from_secs(10)));
        schedule.remove(1);
        assert!(schedule.due(1, pose(0., 0.), start));
    }

    #[test]
    fn stationary_poses_and_equivalent_quaternions_are_not_resent() {
        let start = Instant::now();
        let mut schedule = SendSchedule::default();
        schedule.sent(1, pose(0., 0.), start);
        let later = start + Duration::from_secs(30);
        assert!(!schedule.due(1, pose(0.001, 0.), later));
        let mut equivalent = pose(0., 0.);
        equivalent.rotation = [0., 0., 0., -1.];
        assert!(!schedule.due(1, equivalent, later));
        assert!(schedule.due(1, pose(0., 0.5), later));
        assert!(schedule.due(1, pose(1., 0.), later));
        // Testing eligibility must not consume the send slot on a failed enqueue.
        assert!(schedule.due(1, pose(1., 0.), later));
        schedule.clear();
        assert!(schedule.due(1, pose(0., 0.), start));
    }

    #[test]
    fn initial_pose_snaps_but_live_correction_preserves_continuing_motion() {
        let mut correction = Correction::default();
        let mut transform = Transform::default();
        correction.receive(&mut transform, pose(20., 0.));
        assert_eq!(transform.translation.x, 20.);
        correction.receive(&mut transform, pose(21., 0.6));
        assert_eq!(transform.translation.x, 20.);
        for frame in 1..=15 {
            transform.translation.x += 0.1;
            transform.rotate_y(0.01);
            correction.advance(&mut transform, 0.01);
            assert!((transform.translation.x - (20. + frame as f32 / 6.)).abs() < 0.0001);
        }
        assert!(
            transform
                .rotation
                .abs_diff_eq(Quat::from_rotation_y(0.75), 0.0001)
        );
        correction.advance(&mut transform, 1.);
        assert!((transform.translation.x - 22.5).abs() < 0.0001);
    }

    #[test]
    fn replacement_correction_converges_without_accumulating_old_error() {
        let mut correction = Correction::default();
        let mut transform = Transform::default();
        correction.receive(&mut transform, pose(0., 0.));
        correction.receive(&mut transform, pose(10., 1.));
        correction.advance(&mut transform, 0.075);
        assert!((transform.translation.x - 5.).abs() < 0.0001);
        correction.receive(&mut transform, pose(6., -1.));
        correction.advance(&mut transform, 0.15);
        assert!((transform.translation.x - 6.).abs() < 0.0001);
        assert!(
            transform
                .rotation
                .abs_diff_eq(Quat::from_rotation_y(-1.), 0.0001)
        );
        correction.receive(&mut transform, pose(f32::NAN, 0.));
        correction.advance(&mut transform, 1.);
        assert!(transform.translation.is_finite());
    }

    #[test]
    fn absolute_commands_discard_only_the_replaced_pose_channel() {
        let mut correction = Correction::default();
        let mut transform = Transform::default();
        correction.receive(&mut transform, pose(1., 0.));
        correction.receive(&mut transform, pose(-1., 0.6));
        correction.advance(&mut transform, 0.03);
        transform.translation.x = -1.;
        correction.discard(false);
        correction.advance(&mut transform, 0.2);
        assert_eq!(transform.translation.x, -1.);
        assert!(
            transform
                .rotation
                .abs_diff_eq(Quat::from_rotation_y(0.6), 0.0001)
        );

        correction.receive(&mut transform, pose(3., 1.));
        transform.rotation = Quat::IDENTITY;
        correction.discard(true);
        correction.advance(&mut transform, 0.2);
        assert!((transform.translation.x - 3.).abs() < 0.0001);
        assert_eq!(transform.rotation, Quat::IDENTITY);
    }

    #[test]
    fn executed_position_command_does_not_reapply_pending_network_drift() {
        let mut world = World::new();
        let mut transform = Transform::default();
        let mut correction = Correction::default();
        correction.receive(&mut transform, pose(1., 0.));
        correction.receive(&mut transform, pose(-1., 0.));
        correction.advance(&mut transform, 0.03);
        let entity = world
            .spawn((
                SceneMaxEntity {
                    name: "replica".into(),
                    runtime_name: "replica".into(),
                },
                transform,
            ))
            .id();
        let mut net = Multiplayer::default();
        net.remotes.insert(7, "replica".into());
        net.corrections.insert(7, correction);
        world.insert_resource(net);
        crate::value_calls::tests::run_in(&mut world, "replica.pos(-1,0,0)");
        world.flush();
        let mut correction = world
            .resource_mut::<Multiplayer>()
            .corrections
            .remove(&7)
            .unwrap();
        let mut transform = world.get_mut::<Transform>(entity).unwrap();
        correction.advance(&mut transform, 0.2);
        assert_eq!(transform.translation, Vec3::new(-1., 0., 0.));
    }
}
