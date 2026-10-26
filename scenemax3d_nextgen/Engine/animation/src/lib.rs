//! Non-destructive animation intervals shared by the IDE and projector.
use bevy::{
    animation::{
        AnimationEvaluationError, VariableCurve,
        animation_curves::{AnimationCurve, AnimationCurveEvaluator, EvaluatorId},
        graph::AnimationNodeIndex,
    },
    prelude::*,
};

#[derive(Clone, Debug)]
struct IntervalCurve {
    source: VariableCurve,
    start: f32,
    end: f32,
    domain: bevy::curve::Interval,
}
impl AnimationCurve for IntervalCurve {
    fn clone_value(&self) -> Box<dyn AnimationCurve> {
        Box::new(self.clone())
    }
    fn domain(&self) -> bevy::curve::Interval {
        self.domain
    }
    fn evaluator_id(&self) -> EvaluatorId<'_> {
        self.source.0.evaluator_id()
    }
    fn create_evaluator(&self) -> Box<dyn AnimationCurveEvaluator> {
        self.source.0.create_evaluator()
    }
    fn apply(
        &self,
        evaluator: &mut dyn AnimationCurveEvaluator,
        t: f32,
        weight: f32,
        node: AnimationNodeIndex,
    ) -> Result<(), AnimationEvaluationError> {
        self.source.0.apply(
            evaluator,
            (self.start + t.clamp(0., self.domain.end())).min(self.end),
            weight,
            node,
        )
    }
    fn sample_clamped(&self, t: f32) -> Box<dyn std::any::Any> {
        self.source
            .0
            .sample_clamped((self.start + t.clamp(0., self.domain.end())).min(self.end))
    }
}
/// Wrap every source curve, preserving interpolation and morph/custom property types.
/// The resulting clip starts at zero and has the interval's duration. Source is unchanged.
/// A zero-length interval holds the exact endpoint pose for one millisecond.
pub fn slice(source: &AnimationClip, start: f32, end: f32) -> Result<AnimationClip, String> {
    if !start.is_finite()
        || !end.is_finite()
        || start < 0.
        || end < start
        || end > source.duration() + 0.0001
    {
        return Err("Invalid animation interval".into());
    }
    let domain =
        bevy::curve::Interval::new(0., (end - start).max(0.001)).map_err(|e| e.to_string())?;
    let mut clip = AnimationClip::default();
    for (target, curves) in source.curves() {
        for curve in curves {
            clip.add_variable_curve_to_target(
                *target,
                VariableCurve(Box::new(IntervalCurve {
                    source: curve.clone(),
                    start,
                    end,
                    domain,
                })),
            );
        }
    }
    clip.set_duration((end - start).max(0.001));
    Ok(clip)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn interval_samples_source_pose_and_has_its_own_duration() {
        let target = bevy::animation::AnimationTargetId::from_iter(["fixture"]);
        let mut original = AnimationClip::default();
        original.add_curve_to_target(
            target,
            AnimatableCurve::new(
                bevy::animation::animated_field!(Transform::translation),
                UnevenSampleAutoCurve::new([(0., Vec3::ZERO), (2., Vec3::X * 4.)]).unwrap(),
            ),
        );
        let sliced = slice(&original, 0.5, 1.5).unwrap();
        assert_eq!(sliced.duration(), 1.);
        let curve = &sliced.curves()[&target][0];
        assert_eq!(
            *curve.0.sample_clamped(0.).downcast::<Vec3>().unwrap(),
            Vec3::X
        );
        assert_eq!(
            *curve.0.sample_clamped(1.).downcast::<Vec3>().unwrap(),
            Vec3::X * 3.
        );
        assert_eq!(original.duration(), 2.);
        assert!(slice(&original, 2., 3.).is_err());
        let point = slice(&original, 1., 1.).unwrap();
        assert_eq!(point.duration(), 0.001);
        let curve = &point.curves()[&target][0];
        assert_eq!(
            *curve.0.sample_clamped(0.).downcast::<Vec3>().unwrap(),
            Vec3::X * 2.
        );
        assert_eq!(
            *curve.0.sample_clamped(1.).downcast::<Vec3>().unwrap(),
            Vec3::X * 2.
        );
    }
}
