//! Pure physics laboratory settings and generated SceneMax source.
use serde::{Deserialize, Serialize};
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
/// Editable laboratory parameters in metres, seconds, kilograms and radians.
pub struct Settings {
    /// Motion operation.
    pub command: String,
    /// World-space aiming mode.
    pub direction: String,
    /// Ballistic arc or explicit angle.
    pub arc: String,
    /// Unit box or diameter-one sphere.
    pub shape: String,

    /// Script body identifier.
    pub object: String,
    /// Script target identifier.
    pub target: String,

    /// Launch speed or impulse/force magnitude.
    pub power: f32,
    /// Elevation in degrees.
    pub angle: f32,
    /// Interpolation between low and high launch angles.
    pub blend: f32,

    /// Linear or angular command vector.
    pub vector: [f32; 3],
    /// Initial angular velocity in radians per second.
    pub spin: [f32; 3],
    /// Initial body centre.
    pub position: [f32; 3],
    /// Aim marker centre.
    pub destination: [f32; 3],

    /// Body mass in kilograms.
    pub mass: f32,
    /// Linear and angular damping.
    pub drag: f32,
    /// Downward acceleration.
    pub gravity: f32,
    /// Collision bounciness, zero to one.
    pub restitution: f32,
    /// Floor and body friction.
    pub friction: f32,

    /// Preview duration in seconds.
    pub duration: f32,
    /// Force duration; zero means sustained.
    pub force_duration: f32,
    /// Time before stop in the complete example.
    pub stop_after: f32,
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            command: "Throw".into(),
            direction: "At target".into(),
            arc: "Medium".into(),
            shape: "Box".into(),
            object: "body".into(),
            target: "aim".into(),
            power: 12.,
            angle: 35.,
            blend: 0.5,
            vector: [0., 8., 20.],
            spin: [0., 8., 0.],
            position: [0., 1.05, 0.],
            destination: [0., 1.05, 12.],
            mass: 1.,
            drag: 0.,
            gravity: 9.81,
            restitution: 0.35,
            friction: 0.88,
            duration: 6.,
            force_duration: 0.5,
            stop_after: 1.,
        }
    }
}
/// Available operations.
pub const COMMANDS: &[&str] = &[
    "Throw",
    "Impulse",
    "Force",
    "Velocity",
    "Angular velocity",
    "Torque",
    "Torque impulse",
    "Stop",
];
/// Available aiming modes.
pub const DIRECTIONS: &[&str] = &[
    "At target",
    "Toward target",
    "Vector",
    "Forward",
    "Backward",
    "Left",
    "Right",
    "Up",
    "Down",
];
/// Available arc modes.
pub const ARCS: &[&str] = &["None", "Low", "Medium", "High", "Custom", "Angle"];
/// Format a world-space SceneMax vector.
pub fn vector(v: [f32; 3]) -> String {
    format!("({:.6}, {:.6}, {:.6})", v[0], v[1], v[2])
}
fn normalize(v: [f32; 3], power: f32) -> [f32; 3] {
    let length = v.iter().map(|x| x * x).sum::<f32>().sqrt();
    if length < 0.00001 {
        [0.; 3]
    } else {
        v.map(|x| x / length * power)
    }
}
impl Settings {
    /// Validate names and finite physical ranges.
    pub fn validate(&self) -> Result<(), String> {
        for name in [&self.object, &self.target] {
            if name.is_empty()
                || !name.chars().next().is_some_and(|c| c.is_ascii_alphabetic())
                || !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
            {
                return Err("Names must start with a letter and contain only letters, digits or underscores".into());
            }
        }
        if self.object == self.target
            || ["floor", "camera", "scene", "screen"]
                .contains(&self.object.to_ascii_lowercase().as_str())
            || ["floor", "camera", "scene", "screen"]
                .contains(&self.target.to_ascii_lowercase().as_str())
        {
            return Err("Use distinct names other than floor/camera/scene/screen".into());
        }
        let numbers = [
            self.power,
            self.angle,
            self.blend,
            self.mass,
            self.drag,
            self.gravity,
            self.restitution,
            self.friction,
            self.duration,
            self.force_duration,
            self.stop_after,
        ];
        if numbers
            .iter()
            .chain(self.vector.iter())
            .chain(self.spin.iter())
            .chain(self.position.iter())
            .chain(self.destination.iter())
            .any(|n| !n.is_finite() || n.abs() > 10000.)
        {
            return Err("Enter finite numbers between -10000 and 10000".into());
        }
        if self.mass < 0.001
            || self.drag < 0.
            || self.gravity < 0.
            || !(0. ..=1.).contains(&self.restitution)
            || self.friction < 0.
            || !(0.1..=120.).contains(&self.duration)
            || self.force_duration < 0.
            || self.stop_after < 0.
            || self.power < 0.
            || !(0. ..=1.).contains(&self.blend)
            || !(-89. ..=89.).contains(&self.angle)
        {
            return Err("Check ranges: mass >= 0.001, nonnegative material settings, restitution/arc 0-1, duration 0.1-120 s, angle -89 to 89 degrees".into());
        }
        if !COMMANDS.contains(&self.command.as_str())
            || !DIRECTIONS.contains(&self.direction.as_str())
            || !ARCS.contains(&self.arc.as_str())
            || !["Box", "Sphere"].contains(&self.shape.as_str())
        {
            return Err("Unknown physics option".into());
        }
        Ok(())
    }
    /// Solve the initial world-space velocity at the authored positions.
    pub fn launch(&self) -> ([f32; 3], bool) {
        let delta = std::array::from_fn(|i| self.destination[i] - self.position[i]);
        let direct = match self.direction.as_str() {
            "Vector" => self.vector,
            "Forward" => [0., 0., self.power],
            "Backward" => [0., 0., -self.power],
            "Left" => [-self.power, 0., 0.],
            "Right" => [self.power, 0., 0.],
            "Up" => [0., self.power, 0.],
            "Down" => [0., -self.power, 0.],
            _ => normalize(delta, self.power),
        };
        if self.command != "Throw" || ["Vector", "Up", "Down"].contains(&self.direction.as_str()) {
            return (direct, false);
        }
        let horizontal = [direct[0], 0., direct[2]];
        let distance = (delta[0] * delta[0] + delta[2] * delta[2]).sqrt();
        let angle = if self.arc == "Angle" {
            self.angle.to_radians()
        } else if self.direction == "At target"
            && self.arc != "None"
            && self.gravity > 0.
            && distance > 0.001
        {
            let v2 = self.power * self.power;
            let root =
                v2 * v2 - self.gravity * (self.gravity * distance * distance + 2. * delta[1] * v2);
            if root < 0. {
                return (normalize(delta, self.power), true);
            }
            let low = ((v2 - root.sqrt()) / (self.gravity * distance)).atan();
            let high = ((v2 + root.sqrt()) / (self.gravity * distance)).atan();
            let blend = match self.arc.as_str() {
                "Low" => 0.,
                "High" => 1.,
                "Custom" => self.blend,
                _ => 0.5,
            };
            low + (high - low) * blend
        } else if self.arc != "None" && self.direction != "At target" {
            match self.arc.as_str() {
                "Low" => 20_f32,
                "High" => 70.,
                "Custom" => 15. + 60. * self.blend,
                _ => 45.,
            }
            .to_radians()
        } else {
            return (direct, false);
        };
        let mut v = normalize(horizontal, self.power * angle.cos());
        v[1] = self.power * angle.sin();
        (v, false)
    }
    /// Body settings command.
    pub fn configuration(&self, name: &str) -> String {
        format!(
            "{name}.physics configure mass {} drag {} gravity {} restitution {} friction {}",
            self.mass, self.drag, self.gravity, self.restitution, self.friction
        )
    }
    /// Motion command only.
    pub fn motion(&self) -> String {
        let name = &self.object;
        let v = vector(self.launch().0);
        match self.command.as_str() {
            "Throw" => format!(
                "{name}.physics velocity {v}\n{name}.physics angular velocity {}",
                vector(self.spin)
            ),
            "Impulse" => format!("{name}.physics impulse {v}"),
            "Force" => format!(
                "{name}.physics force {v}{}",
                if self.force_duration > 0. {
                    format!(" for {} seconds", self.force_duration)
                } else {
                    String::new()
                }
            ),
            "Velocity" => format!("{name}.physics velocity {}", vector(self.vector)),
            "Angular velocity" => {
                format!("{name}.physics angular velocity {}", vector(self.vector))
            }
            "Torque" | "Torque impulse" => format!(
                "{name}.physics torque {}{}",
                vector(self.vector),
                if self.command == "Torque impulse" {
                    " impulse"
                } else {
                    ""
                }
            ),
            _ => format!("{name}.physics stop"),
        }
    }
    /// Validated configuration and motion for an existing body.
    pub fn snippet(&self) -> Result<String, String> {
        self.validate()?;
        Ok(format!(
            "{}\n{}",
            self.configuration(&self.object),
            self.motion()
        ))
    }
    /// Standalone scene with the same body, floor and command.
    pub fn example(&self) -> Result<String, String> {
        self.validate()?;
        let shape = if self.shape == "Sphere" {
            "sphere : mass 1, radius 0.5, collision shape sphere"
        } else {
            "box : mass 1, size (1,1,1), collision shape box"
        };
        let prelude = if self.command == "Stop" {
            format!(
                "{}.physics velocity {}\nwait {} seconds\n",
                self.object,
                vector(self.vector),
                self.stop_after
            )
        } else {
            String::new()
        };
        Ok(format!(
            "// Physics Simulation - world-space coordinates and launch vector.\n// Use the snippet with an existing physics body.\nfloor => static box : size (80,1,80), pos (0,-0.5,0), collision shape box\n{}\n{} => {} , pos {}\n{} => sphere : radius 0.2, pos {}\ncamera.pos{}\ncamera.rotate(30,-45,0)\n{}\n{}{}\n",
            self.configuration("floor"),
            self.object,
            shape,
            vector(self.position),
            self.target,
            vector(self.destination),
            vector([
                self.position[0] + 22.,
                self.position[1] + 18.,
                self.position[2] - 22.
            ]),
            self.configuration(&self.object),
            prelude,
            self.motion()
        ))
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn low_and_high_arcs_hit_target() {
        for arc in ["Low", "High"] {
            let s = Settings {
                arc: arc.into(),
                ..Default::default()
            };
            let (v, w) = s.launch();
            assert!(!w);
            let t = 12. / v[2];
            assert!((v[1] * t - 0.5 * s.gravity * t * t).abs() < 0.001);
        }
    }
    #[test]
    fn unreachable_target_is_reported() {
        let s = Settings {
            power: 1.,
            ..Default::default()
        };
        assert!(s.launch().1);
    }
    #[test]
    fn invalid_values_do_not_emit_code() {
        let s = Settings {
            mass: 0.,
            ..Default::default()
        };
        assert!(s.example().is_err());
    }
    #[test]
    fn vertical_throw_directions_preserve_their_sign() {
        for (direction, sign) in [("Up", 1.), ("Down", -1.)] {
            let s = Settings {
                direction: direction.into(),
                ..Default::default()
            };
            assert_eq!(s.launch().0, [0., sign * s.power, 0.]);
        }
    }
}
