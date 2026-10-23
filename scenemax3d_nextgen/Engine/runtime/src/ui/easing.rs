//! SceneMax UI easing curves. The direction names describe visible travel;
//! ease-in enters from the opposite side and ease-out exits and hides.
pub(super) fn normalized(name: &str) -> String {
    name.trim()
        .to_ascii_lowercase()
        .replace(['_', '-', ' '], "")
}

pub(super) fn progress(t: f32, name: &str) -> f32 {
    let t = t.clamp(0., 1.);
    let name = normalized(name);
    let (out, curve) = if let Some(curve) = name.strip_prefix("easeout") {
        (true, curve)
    } else if let Some(curve) = name.strip_prefix("easein") {
        (false, curve)
    } else {
        return t;
    };
    if curve == "custom" {
        return if out {
            bezier(t, 0.075, 0.82, 0.165, 1.)
        } else {
            bezier(t, 0.6, 0.04, 0.98, 0.335)
        };
    }
    if curve == "bezier" {
        return if out {
            bezier(t, 0., 0., 0.58, 1.)
        } else {
            bezier(t, 0.42, 0., 1., 1.)
        };
    }
    let p = if out { 1. - t } else { t };
    let value = match curve {
        "quad" => p * p,
        "cubic" => p.powi(3),
        "quart" => p.powi(4),
        "quint" => p.powi(5),
        "power" => p.powf(2.5),
        "sine" => 1. - (p * std::f32::consts::FRAC_PI_2).cos(),
        "circ" => 1. - (1. - p * p).sqrt(),
        "expo" => {
            if p <= 0. {
                0.
            } else {
                2_f32.powf(10. * p - 10.)
            }
        }
        "back" => 2.70158 * p.powi(3) - 1.70158 * p * p,
        "elastic" => {
            if p <= 0. || p >= 1. {
                p
            } else {
                -2_f32.powf(10. * p - 10.) * ((p * 10. - 10.75) * std::f32::consts::TAU / 3.).sin()
            }
        }
        "bounce" => 1. - super::ease_out_bounce(1. - p),
        _ => p,
    };
    if out { 1. - value } else { value }
}

fn bezier(progress: f32, x1: f32, y1: f32, x2: f32, y2: f32) -> f32 {
    fn component(t: f32, a: f32, b: f32) -> f32 {
        3. * (1. - t).powi(2) * t * a + 3. * (1. - t) * t * t * b + t.powi(3)
    }
    let (mut low, mut high, mut t) = (0., 1., progress);
    for _ in 0..12 {
        let x = component(t, x1, x2);
        if (x - progress).abs() < 0.0005 {
            break;
        }
        if x < progress {
            low = t;
        } else {
            high = t;
        }
        t = (low + high) * 0.5;
    }
    component(t, y1, y2)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn supported_curves_have_correct_endpoints_and_in_out_shapes() {
        for curve in [
            "Quad", "Cubic", "Quart", "Quint", "Sine", "Circ", "Expo", "Back", "Elastic", "Bounce",
            "Power", "Bezier", "Custom",
        ] {
            for direction in ["In", "Out"] {
                let name = format!("Ease{direction}{curve}");
                assert!(progress(0., &name).abs() < 0.0001, "{name}");
                assert!((progress(1., &name) - 1.).abs() < 0.0001, "{name}");
            }
        }
        assert_eq!(progress(0.5, "EaseInQuad"), 0.25);
        assert_eq!(progress(0.5, "EaseOutQuad"), 0.75);
        assert_eq!(progress(0.5, " ease-out_quad "), 0.75);
        assert!(progress(0.25, "EaseInBack") < 0.);
        assert!(progress(0.75, "EaseOutBack") > 1.);
    }
}
