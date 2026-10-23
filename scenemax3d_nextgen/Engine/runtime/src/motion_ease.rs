//! Evaluated motion curves. Integrate curve differences, retaining snapshot phase.
use super::*;
use scenemax_parser::motion_ease::{EaseMode, MotionEase};

#[derive(Debug, Clone, Default)]
pub(super) struct Curve {
    spec: Option<MotionEase>,
    params: Vec<f32>,
}

impl Curve {
    pub(super) fn resolve(
        spec: &Option<MotionEase>,
        mut number: impl FnMut(&AssignmentValue) -> Option<f32>,
    ) -> Self {
        let Some(spec) = spec else {
            return Self::default();
        };
        let Some(params) = spec
            .params
            .iter()
            .map(&mut number)
            .collect::<Option<Vec<_>>>()
        else {
            return Self::default();
        };
        if params.iter().any(|p| !p.is_finite()) {
            return Self::default();
        }
        let mut spec = spec.clone();
        spec.family = spec
            .family
            .to_ascii_lowercase()
            .replace(['_', '-', ' '], "");
        for prefix in ["easeinout", "easein", "easeout"] {
            if let Some(tail) = spec.family.strip_prefix(prefix) {
                spec.family = tail.into();
                break;
            }
        }
        if spec.family.is_empty() {
            spec.family = "quad".into();
        }
        Self {
            spec: Some(spec),
            params,
        }
    }

    pub(super) fn literal(spec: &Option<MotionEase>) -> Self {
        Self::resolve(spec, |v| {
            if let AssignmentValue::Number(v) = v {
                Some(*v)
            } else {
                None
            }
        })
    }

    /// Time-equivalent displacement for a velocity expressed over remaining duration.
    pub(super) fn step(&self, duration: f32, remaining: f32, dt: f32) -> f32 {
        let Some(spec) = &self.spec else {
            return dt;
        };
        let start = spec.start_progress.clamp(0., 1.);
        let from = start + (1. - start) * (1. - remaining / duration);
        let to = start + (1. - start) * (1. - (remaining - dt).max(0.) / duration);
        duration * (self.apply(to) - self.apply(from))
    }

    pub(super) fn literal_spec(&self) -> Option<MotionEase> {
        self.spec.clone().map(|mut spec| {
            spec.params = self
                .params
                .iter()
                .copied()
                .map(AssignmentValue::Number)
                .collect();
            spec
        })
    }

    pub(super) fn wire(&self) -> String {
        let Some(spec) = &self.spec else {
            return String::new();
        };
        let mode = match spec.mode {
            EaseMode::In => "in",
            EaseMode::Out => "out",
            EaseMode::InOut => "in out",
        };
        let params = self
            .params
            .iter()
            .map(ToString::to_string)
            .collect::<Vec<_>>()
            .join(",");
        format!(
            " ease {mode} {} ({params})",
            serde_json::Value::String(spec.family.clone())
        )
    }

    pub(super) fn apply(&self, progress: f32) -> f32 {
        let p = progress.clamp(0., 1.);
        let Some(spec) = &self.spec else {
            return p;
        };
        if p == 0. || p == 1. || spec.family == "linear" {
            return p;
        }
        let window_index = match spec.family.as_str() {
            "sine" | "quad" | "cubic" | "quart" | "quint" | "expo" | "circ" | "bounce" => Some(0),
            "back" | "power" => Some(1),
            "elastic" => Some(2),
            "bezier" | "cubicbezier" => Some(4),
            _ => None,
        };
        let value = if let Some(window) = window_index.and_then(|i| self.params.get(i)).copied() {
            let e = window.clamp(
                0.,
                if spec.mode == EaseMode::InOut {
                    0.5
                } else {
                    1.
                },
            );
            if e == 0. {
                p
            } else if spec.mode != EaseMode::Out && p <= e {
                e * self.ease_in(p / e)
            } else if spec.mode != EaseMode::In && p >= 1. - e {
                1. - e + e * self.ease_out((p - (1. - e)) / e)
            } else {
                p
            }
        } else {
            match spec.mode {
                EaseMode::In => self.ease_in(p),
                EaseMode::Out => self.ease_out(p),
                EaseMode::InOut if p < 0.5 => 0.5 * self.ease_in(p * 2.),
                EaseMode::InOut => 0.5 + 0.5 * self.ease_out((p - 0.5) * 2.),
            }
        };
        if value.is_finite() { value } else { p }
    }

    fn param(&self, index: usize, default: f32) -> f32 {
        self.params.get(index).copied().unwrap_or(default)
    }
    fn ease_in(&self, p: f32) -> f32 {
        use std::f32::consts::{FRAC_PI_2, TAU};
        match self
            .spec
            .as_ref()
            .map(|s| s.family.as_str())
            .unwrap_or("quad")
        {
            "linear" => p,
            "sine" => 1. - (p * FRAC_PI_2).cos(),
            "cubic" => p.powi(3),
            "quart" => p.powi(4),
            "quint" => p.powi(5),
            "expo" => {
                if p <= 0. {
                    0.
                } else {
                    2_f32.powf(10. * p - 10.)
                }
            }
            "circ" => 1. - (1. - p * p).max(0.).sqrt(),
            "back" => {
                let o = self.param(0, 1.70158);
                (o + 1.) * p.powi(3) - o * p * p
            }
            "elastic" => {
                if p <= 0. || p >= 1. {
                    return p;
                }
                let a = self.param(0, 1.).max(0.0001);
                let period = self.param(1, 0.3).max(0.0001);
                let shift = period / TAU * (1. / a).min(1.).asin();
                -a * 2_f32.powf(10. * (p - 1.)) * ((p - 1. - shift) * TAU / period).sin()
            }
            "bounce" => 1. - bounce(1. - p),
            "power" => p.powf(self.param(0, 2.5)),
            "bezier" | "cubicbezier" => self.bezier(p, [0.42, 0., 1., 1.]),
            _ => p * p,
        }
    }
    fn ease_out(&self, p: f32) -> f32 {
        match self
            .spec
            .as_ref()
            .map(|s| s.family.as_str())
            .unwrap_or("quad")
        {
            "bounce" => bounce(p),
            "bezier" | "cubicbezier" => self.bezier(p, [0., 0., 0.58, 1.]),
            _ => 1. - self.ease_in(1. - p),
        }
    }
    fn bezier(&self, p: f32, defaults: [f32; 4]) -> f32 {
        let [x1, y1, x2, y2] = std::array::from_fn(|i| self.param(i, defaults[i]));
        let component = |t: f32, a: f32, b: f32| {
            3. * (1. - t).powi(2) * t * a + 3. * (1. - t) * t * t * b + t.powi(3)
        };
        let (mut low, mut high, mut t) = (0., 1., p);
        for _ in 0..12 {
            let x = component(t, x1, x2);
            if (x - p).abs() < 0.0005 {
                break;
            }
            if x < p {
                low = t;
            } else {
                high = t;
            }
            t = (low + high) * 0.5;
        }
        component(t, y1, y2)
    }
}

fn bounce(p: f32) -> f32 {
    let (t, offset) = if p < 1. / 2.75 {
        (p, 0.)
    } else if p < 2. / 2.75 {
        (p - 1.5 / 2.75, 0.75)
    } else if p < 2.5 / 2.75 {
        (p - 2.25 / 2.75, 0.9375)
    } else {
        (p - 2.625 / 2.75, 0.984375)
    };
    7.5625 * t * t + offset
}

pub(super) fn resolve(
    spec: &Option<MotionEase>,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
) -> Curve {
    Curve::resolve(spec, |v| {
        resolve_assignment_value_scoped_with_guards(v, vars, scope, guards, Some(transforms), None)
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    fn curve(mode: EaseMode, family: &str, params: &[f32], phase: f32) -> Curve {
        Curve::literal(&Some(MotionEase {
            mode,
            family: family.into(),
            params: params.iter().map(|v| AssignmentValue::Number(*v)).collect(),
            start_progress: phase,
        }))
    }
    #[test]
    fn curves_match_analytical_samples_windows_and_endpoints() {
        for (family, expected) in [
            ("quad", 0.0625),
            ("cubic", 0.015625),
            ("quart", 0.00390625),
            ("quint", 0.0009765625),
            ("sine", 1. - (std::f32::consts::PI / 8.).cos()),
            ("expo", 2_f32.powf(-7.5)),
            ("circ", 1. - 0.9375_f32.sqrt()),
            ("power", 0.03125),
        ] {
            let c = curve(EaseMode::In, family, &[], 0.);
            assert!((c.apply(0.25) - expected).abs() < 0.00001, "{family}");
        }
        assert_eq!(curve(EaseMode::Out, "quad", &[], 0.).apply(0.5), 0.75);
        assert_eq!(curve(EaseMode::In, "", &[0.25], 0.).apply(0.125), 0.0625);
        assert_eq!(
            curve(EaseMode::Out, "Ease-Out-Quad", &[], 0.).apply(0.5),
            0.75
        );
        assert_eq!(curve(EaseMode::InOut, "quad", &[], 0.).apply(0.25), 0.125);
        let c = curve(EaseMode::InOut, "cubic", &[0.25], 0.);
        assert_eq!(c.apply(0.125), 0.03125);
        assert_eq!(c.apply(0.5), 0.5);
        assert_eq!(c.apply(0.875), 0.96875);
        assert!(curve(EaseMode::Out, "back", &[], 0.).apply(0.7) > 1.);
        for family in [
            "linear",
            "sine",
            "quad",
            "cubic",
            "quart",
            "quint",
            "expo",
            "circ",
            "back",
            "elastic",
            "bounce",
            "power",
            "bezier",
            "cubicbezier",
            "unknown",
        ] {
            for mode in [EaseMode::In, EaseMode::Out, EaseMode::InOut] {
                let c = curve(mode, family, &[], 0.);
                assert_eq!(c.apply(0.), 0.);
                assert_eq!(c.apply(1.), 1.);
                assert!((0..=100).all(|i| c.apply(i as f32 / 100.).is_finite()));
            }
        }
    }
    #[test]
    fn late_join_integrates_original_phase_even_when_remaining_displacement_is_zero_or_negative() {
        for (family, params, phase) in [
            ("quad", vec![], 0.5),
            ("back", vec![], 0.7),
            ("bounce", vec![], 1. / 2.75),
            ("elastic", vec![1.2, 0.4], 0.6),
            ("power", vec![3., 0.25], 0.1),
        ] {
            let full = curve(EaseMode::Out, family, &params, 0.);
            let late = curve(EaseMode::Out, family, &params, phase);
            let duration = 8. * (1. - phase);
            let mut position = 3. + 12. * full.apply(phase);
            let mut remaining = duration;
            for _ in 0..4 {
                let dt = duration / 4.;
                position += 12. / duration * late.step(duration, remaining, dt);
                remaining -= dt;
                let expected = 3. + 12. * full.apply(1. - remaining / 8.);
                assert!(
                    (position - expected).abs() < 0.0001,
                    "{family}: {position} vs {expected}"
                );
            }
            assert!((position - 15.).abs() < 0.0001);
        }
    }
    #[test]
    fn invalid_evaluated_parameters_fall_back_to_finite_linear_motion() {
        let c = curve(EaseMode::In, "power", &[f32::NAN], 0.);
        assert_eq!(c.step(2., 2., 0.5), 0.5);
        assert!(c.wire().is_empty());
        assert_eq!(
            curve(EaseMode::In, "power", &[-1000.], 0.).apply(0.25),
            0.25
        );
    }
    #[test]
    fn timed_systems_apply_eased_deltas_complete_and_release_leases() {
        use bevy::ecs::system::RunSystemOnce;
        for duration in [2., 0.] {
            let p = scenemax_parser::parse_program(&format!("subject.move (x + 8) in {duration} seconds ease in\nsubject.rotate (z + 80) in {duration} seconds ease in")).unwrap();
            let Statement::Move(movement) = &p.statements[0] else {
                panic!();
            };
            let Statement::Turn(turn) = &p.statements[1] else {
                panic!();
            };
            let mut movement = timed_move_from_statement(movement, &Transform::default());
            let mut turn = timed_turn_from_statement(turn);
            let lease = std::sync::Arc::new(());
            let weak = std::sync::Arc::downgrade(&lease);
            movement.network_lifetime = Some(lease.clone());
            turn.network_lifetime = Some(lease);
            let mut world = World::new();
            world.init_resource::<SceneMaxVars>();
            world.init_resource::<SceneMaxStartupProgram>();
            world.init_resource::<SceneMaxColliderBounds>();
            let mut time = Time::<()>::default();
            time.advance_by(std::time::Duration::from_secs_f32(0.5));
            world.insert_resource(time);
            let entity = world
                .spawn((
                    Transform::default(),
                    TimedMoves {
                        moves: vec![movement],
                    },
                    TimedTurns { turns: vec![turn] },
                ))
                .id();
            world.run_system_once(update_timed_moves).unwrap();
            world.run_system_once(update_timed_turns).unwrap();
            let fraction = if duration == 0. { 1. } else { 0.0625 };
            let t = world.get::<Transform>(entity).unwrap();
            assert!((t.translation.x - 8. * fraction).abs() < 0.0001);
            assert!(t.rotation.abs_diff_eq(
                Quat::from_rotation_z((80_f32 * fraction).to_radians()),
                0.0001
            ));
            world
                .resource_mut::<Time>()
                .advance_by(std::time::Duration::from_secs(3));
            world.run_system_once(update_timed_moves).unwrap();
            world.run_system_once(update_timed_turns).unwrap();
            let t = world.get::<Transform>(entity).unwrap();
            assert!((t.translation.x - 8.).abs() < 0.0001);
            assert!(
                t.rotation
                    .abs_diff_eq(Quat::from_rotation_z(80_f32.to_radians()), 0.0001)
            );
            assert!(world.get::<TimedMoves>(entity).is_none());
            assert!(world.get::<TimedTurns>(entity).is_none());
            assert!(weak.upgrade().is_none());
        }
    }
}
