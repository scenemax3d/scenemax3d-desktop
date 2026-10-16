//! Deterministic design-time trajectory sampling, matching the Java preview.
use super::number;
use serde_json::Value;
/// A fixed-step trajectory pose.
#[derive(Clone, Copy, Debug)]
pub struct Sample {
    /// Seconds since launch.
    pub time: f32,
    /// Position in preview world units.
    pub position: [f32; 3],
    /// Velocity in world units per second.
    pub velocity: [f32; 3],
    /// Accumulated rotation in degrees, retained for format parity.
    pub spin: f32,
}
fn add(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    std::array::from_fn(|i| a[i] + b[i])
}
fn sub(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    std::array::from_fn(|i| a[i] - b[i])
}
fn mul(a: [f32; 3], b: f32) -> [f32; 3] {
    a.map(|v| v * b)
}
fn length(a: [f32; 3]) -> f32 {
    a.iter().map(|v| v * v).sum::<f32>().sqrt()
}
fn normal(a: [f32; 3]) -> [f32; 3] {
    mul(a, 1. / length(a).max(0.00001))
}
fn lerp(a: [f32; 3], b: [f32; 3], t: f32) -> [f32; 3] {
    add(a, mul(sub(b, a), t))
}
fn ease(t: f32, name: &str) -> f32 {
    match name {
        "ease_in" => t * t,
        "ease_out" => 1. - (1. - t).powi(2),
        "ease_in_out" => {
            if t < 0.5 {
                2. * t * t
            } else {
                1. - (-2. * t + 2.).powi(2) / 2.
            }
        }
        _ => t,
    }
}
/// Preview is deliberately bounded to one minute / 3,601 samples for imported data.
/// Physics preview is kinematic, as in Java; collision/world interaction belongs to runtime.
pub fn sample(v: &Value, distance: f32, height: f32) -> Vec<Sample> {
    let p = &v["parameters"];
    let n = |key| number(p, key);
    let kind = v["motionType"].as_str().unwrap_or("target_arc");
    let start = [0., 1.2, 0.];
    let target = [0., 1.2 + height, distance];
    let dt = 1. / 60.;
    let easing = p["easingFunction"].as_str().unwrap_or("linear");
    let mut out = vec![];
    let mut emit = |time, position, velocity| {
        out.push(Sample {
            time,
            position,
            velocity,
            spin: n("spinSpeed") * time,
        })
    };
    let mut pos = start;
    if matches!(kind, "target_arc" | "returning") {
        let duration = if kind == "returning" {
            n("outboundDuration")
        } else {
            n("duration")
        }
        .clamp(0.1, 60.);
        let end = if kind == "returning" {
            add(start, [0., 0., n("outboundDistance")])
        } else {
            target
        };
        let arc = if kind == "returning" {
            n("outboundArcHeight")
        } else {
            n("arcHeight")
        };
        let count = (duration / dt).ceil() as usize;
        for i in 0..=count {
            let time = (i as f32 * dt).min(duration);
            let raw = time / duration;
            let mut next = lerp(start, end, ease(raw, easing));
            next[1] += arc * 4. * raw * (1. - raw);
            let vel = if i == 0 {
                normal(sub(end, start))
            } else {
                mul(sub(next, pos), 1. / dt)
            };
            emit(time, next, vel);
            pos = next;
        }
        if kind == "returning" {
            let delay = n("returnDelay").clamp(0., 60.);
            if delay > 0. {
                emit(duration + delay, pos, [0.; 3]);
            }
            let return_time = (length(sub(pos, start)) / n("returnSpeed").max(0.1)).clamp(0.1, 60.);
            let vel = mul(normal(sub(start, pos)), n("returnSpeed").max(0.1));
            for i in 1..=(return_time / dt).ceil() as usize {
                let time = (i as f32 * dt).min(return_time);
                emit(
                    duration + delay + time,
                    lerp(pos, start, ease(time / return_time, easing)),
                    vel,
                );
            }
        }
        return out;
    }
    let mut speed = if matches!(kind, "physics" | "ballistic" | "custom_curve") {
        n("initialSpeed")
    } else {
        n("speed")
    }
    .max(0.1);
    let angle = n("launchAngle").to_radians();
    let mut velocity = [0., angle.sin() * speed, angle.cos() * speed];
    let mut dir = if kind == "homing" {
        normal([0., 0.08, 1.])
    } else {
        [0., 0., 1.]
    };
    let mut traveled = 0.;
    for i in 0..=(n("maxLifetime").clamp(0.2, 60.) / dt) as usize {
        let time = i as f32 * dt;
        if kind == "straight" {
            if traveled > n("maxDistance").max(0.1) {
                break;
            }
            velocity = mul(dir, speed);
            emit(time, pos, velocity);
            let step = (speed * dt).max(0.);
            pos = add(pos, mul(dir, step));
            traveled += step;
            speed += n("acceleration") * dt;
        } else if kind == "homing" {
            let desired = sub(target, pos);
            if length(desired) > 0.01 && time >= n("homingDelay") {
                dir = normal(lerp(
                    dir,
                    normal(desired),
                    (n("turnRate").max(1.).to_radians() * dt * n("homingStrength").max(0.))
                        .clamp(0., 1.),
                ));
            }
            velocity = mul(dir, speed);
            emit(time, pos, velocity);
            pos = add(pos, mul(velocity, dt));
            if length(sub(pos, target)) <= n("collisionRadius").max(0.2) {
                emit(time + dt, target, velocity);
                break;
            }
        } else {
            emit(time, pos, velocity);
            velocity[1] -= 9.81 * n("gravityScale") * dt;
            pos = add(pos, mul(velocity, dt));
            if time > 0.1 && pos[1] <= 0. {
                pos[1] = 0.;
                emit(time + dt, pos, velocity);
                break;
            }
        }
    }
    out
}
/// Pick the first sample at or after the requested time, as in Java.
pub fn at(samples: &[Sample], time: f32) -> Option<Sample> {
    let index = samples.partition_point(|s| s.time < time);
    samples.get(index).or_else(|| samples.last()).copied()
}
