//! Java primitive dimensions converted into editor-owned preview meshes.
use super::*;
use serde_json::Value;
#[derive(Default)]
struct Faces {
    positions: Vec<[f32; 3]>,
    normals: Vec<[f32; 3]>,
}
impl Faces {
    fn triangle(&mut self, a: Vec3, b: Vec3, c: Vec3) {
        let normal = (b - a).cross(c - a).normalize_or_zero().to_array();
        self.positions
            .extend([a.to_array(), b.to_array(), c.to_array()]);
        self.normals.extend([normal; 3]);
    }
    fn quad(&mut self, a: Vec3, b: Vec3, c: Vec3, d: Vec3) {
        self.triangle(a, b, c);
        self.triangle(a, c, d);
    }
    fn prism(&mut self, polygon: &[Vec2], depth: f32) {
        let v = |i: usize, z: f32| Vec3::new(polygon[i].x, polygon[i].y, z);
        for i in 1..polygon.len() - 1 {
            self.triangle(v(0, depth / 2.), v(i, depth / 2.), v(i + 1, depth / 2.));
            self.triangle(v(0, -depth / 2.), v(i + 1, -depth / 2.), v(i, -depth / 2.));
        }
        for i in 0..polygon.len() {
            let j = (i + 1) % polygon.len();
            self.quad(
                v(i, -depth / 2.),
                v(j, -depth / 2.),
                v(j, depth / 2.),
                v(i, depth / 2.),
            );
        }
    }
    fn mesh(self) -> Mesh {
        Mesh::new(
            bevy::mesh::PrimitiveTopology::TriangleList,
            RenderAssetUsages::all(),
        )
        .with_inserted_attribute(Mesh::ATTRIBUTE_POSITION, self.positions)
        .with_inserted_attribute(Mesh::ATTRIBUTE_NORMAL, self.normals)
    }
}
pub(super) fn mesh(kind: &str, p: &Value) -> Option<Mesh> {
    let n = |key: &str, fallback: f32| {
        p[key]
            .as_f64()
            .map(|v| v as f32)
            .filter(|v| v.is_finite())
            .unwrap_or(fallback)
            .clamp(0.001, 10000.)
    };
    let mut f = Faces::default();
    match kind {
        "CYLINDER" | "CONE" | "HOLLOW_CYLINDER" => {
            let top = if kind == "CONE" && p["radiusTop"].as_f64().unwrap_or(0.) == 0. {
                0.
            } else {
                n("radiusTop", 1.)
            };
            let bottom = n("radiusBottom", 1.);
            let h = n("height", 2.) / 2.;
            let it = if kind == "HOLLOW_CYLINDER" {
                n("innerRadiusTop", 0.5).min(top)
            } else {
                0.
            };
            let ib = if kind == "HOLLOW_CYLINDER" {
                n("innerRadiusBottom", 0.5).min(bottom)
            } else {
                0.
            };
            for i in 0..48 {
                let a = i as f32 * std::f32::consts::TAU / 48.;
                let b = (i + 1) as f32 * std::f32::consts::TAU / 48.;
                let v = |angle: f32, r: f32, y: f32| Vec3::new(angle.cos() * r, y, angle.sin() * r);
                f.quad(
                    v(a, bottom, -h),
                    v(b, bottom, -h),
                    v(b, top, h),
                    v(a, top, h),
                );
                f.quad(v(a, it, h), v(b, it, h), v(b, ib, -h), v(a, ib, -h));
                f.quad(v(a, top, h), v(b, top, h), v(b, it, h), v(a, it, h));
                f.quad(
                    v(a, ib, -h),
                    v(b, ib, -h),
                    v(b, bottom, -h),
                    v(a, bottom, -h),
                );
            }
        }
        "WEDGE" => {
            let w = n("wedgeWidth", 1.) / 2.;
            let h = n("wedgeHeight", 1.) / 2.;
            f.prism(
                &[Vec2::new(-w, -h), Vec2::new(w, -h), Vec2::new(w, h)],
                n("wedgeDepth", 1.),
            );
        }
        "STAIRS" => {
            let count = p["stairsStepCount"].as_u64().unwrap_or(6).clamp(1, 256) as usize;
            let w = n("stairsWidth", 2.) / 2.;
            let h = n("stairsStepHeight", 0.25);
            let d = n("stairsStepDepth", 0.4);
            for i in 0..count {
                let low = -(count as f32) * h / 2.;
                let high = low + (i + 1) as f32 * h;
                let mut block = Faces::default();
                block.prism(
                    &[
                        Vec2::new(-w, low),
                        Vec2::new(w, low),
                        Vec2::new(w, high),
                        Vec2::new(-w, high),
                    ],
                    d,
                );
                let z = (i as f32 - (count - 1) as f32 / 2.) * d;
                for v in &mut block.positions {
                    v[2] += z;
                }
                f.positions.extend(block.positions);
                f.normals.extend(block.normals);
            }
        }
        "ARCH" => {
            let w = n("archWidth", 2.) / 2.;
            let h = n("archHeight", 2.5);
            let t = n("archThickness", 0.35).min(w * 0.99);
            let depth = n("archDepth", 0.5);
            let segments = p["archSegments"].as_u64().unwrap_or(12).clamp(3, 256);
            let cy = h / 2. - w;
            for i in 0..segments {
                let a = i as f32 * std::f32::consts::PI / segments as f32;
                let b = (i + 1) as f32 * std::f32::consts::PI / segments as f32;
                let point = |angle: f32, r: f32| Vec2::new(angle.cos() * r, cy + angle.sin() * r);
                f.prism(
                    &[point(a, w), point(b, w), point(b, w - t), point(a, w - t)],
                    depth,
                );
            }
            for sign in [-1., 1.] {
                f.prism(
                    &[
                        Vec2::new(sign * w, -h / 2.),
                        Vec2::new(sign * (w - t), -h / 2.),
                        Vec2::new(sign * (w - t), cy),
                        Vec2::new(sign * w, cy),
                    ],
                    depth,
                );
            }
        }
        _ => return None,
    }
    Some(f.mesh())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn all_added_primitives_have_finite_geometry() {
        for kind in [
            "WEDGE",
            "CYLINDER",
            "CONE",
            "HOLLOW_CYLINDER",
            "STAIRS",
            "ARCH",
        ] {
            let p = scenemax_ide_core::scene3d::structure::template(kind).unwrap();
            let m = mesh(kind, &p).unwrap();
            let Some(bevy::mesh::VertexAttributeValues::Float32x3(v)) =
                m.attribute(Mesh::ATTRIBUTE_POSITION)
            else {
                panic!("missing positions")
            };
            assert!(v.len() > 12);
            assert!(v.iter().flatten().all(|v| v.is_finite()));
        }
    }
}
