//! Adapted from Renzora; see IDE/third_party/renzora_gizmo/NOTICE.md.
use bevy::prelude::*;
pub(crate) fn ray_line_param(ray: &Ray3d, origin: Vec3, dir: Vec3) -> Option<f32> {
    let d = ray.direction.as_vec3();
    let b = d.dot(dir);
    let denom = 1.0 - b * b;
    if denom.abs() < 1e-6 {
        return None;
    }
    let w0 = ray.origin - origin;
    Some((dir.dot(w0) - b * d.dot(w0)) / denom)
}

/// World point where `ray` meets the plane through `origin` with `normal`.
/// `None` when the ray is parallel to (or pointing away from) the plane.
pub(crate) fn ray_plane_point(ray: &Ray3d, origin: Vec3, normal: Vec3) -> Option<Vec3> {
    let d = ray.direction.as_vec3();
    let denom = d.dot(normal);
    if denom.abs() < 1e-6 {
        return None;
    }
    let t = (origin - ray.origin).dot(normal) / denom;
    if t < 0.0 {
        return None;
    }
    Some(ray.origin + d * t)
}

pub(crate) fn closest_distance_ray_segment(ray: &Ray3d, seg_a: Vec3, seg_b: Vec3) -> Option<f32> {
    let ro: Vec3 = ray.origin;
    let rd: Vec3 = ray.direction.as_vec3();
    let sd = seg_b - seg_a;
    let sl = sd.length();
    if sl < 1e-6 {
        return None;
    }
    let su = sd / sl;
    let w0 = ro - seg_a;
    let a = rd.dot(rd);
    let b = rd.dot(su);
    let c = su.dot(su);
    let d = rd.dot(w0);
    let e = su.dot(w0);
    let denom = a * c - b * b;
    if denom.abs() < 1e-8 {
        return None;
    }
    let t_ray = (b * e - c * d) / denom;
    let t_seg = (a * e - b * d) / denom;
    if t_ray < 0.0 {
        return None;
    }
    let tc = t_seg.clamp(0.0, sl);
    Some((ro + rd * t_ray - (seg_a + su * tc)).length())
}

pub(crate) fn ray_circle_distance(
    ray: &Ray3d,
    center: Vec3,
    normal: Vec3,
    radius: f32,
) -> Option<f32> {
    let (p1, p2) = perpendicular_pair(normal);
    let segs = 32;
    let mut best: Option<f32> = None;
    for i in 0..segs {
        let a0 = (i as f32 / segs as f32) * std::f32::consts::TAU;
        let a1 = ((i + 1) as f32 / segs as f32) * std::f32::consts::TAU;
        let s0 = center + (p1 * a0.cos() + p2 * a0.sin()) * radius;
        let s1 = center + (p1 * a1.cos() + p2 * a1.sin()) * radius;
        if let Some(d) = closest_distance_ray_segment(ray, s0, s1)
            && best.is_none_or(|b| d < b)
        {
            best = Some(d);
        }
    }
    best
}

pub(crate) fn perpendicular_pair(normal: Vec3) -> (Vec3, Vec3) {
    let p1 = if normal.y.abs() > 0.9 {
        Vec3::X
    } else {
        normal.cross(Vec3::Y).normalize()
    };
    let p2 = normal.cross(p1).normalize();
    (p1, p2)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn axis_drag_projects_cursor_and_rejects_parallel_rays() {
        let ray = Ray3d::new(Vec3::new(3., 0., 10.), Dir3::NEG_Z);
        assert!((ray_line_param(&ray, Vec3::ZERO, Vec3::X).unwrap() - 3.).abs() < 0.0001);
        assert!(ray_line_param(&ray, Vec3::ZERO, Vec3::Z).is_none());
        assert_eq!(
            ray_plane_point(&ray, Vec3::ZERO, Vec3::Z),
            Some(Vec3::new(3., 0., 0.))
        );
        assert!(ray_plane_point(&ray, Vec3::new(0., 0., 11.), Vec3::Z).is_none());
    }
    #[test]
    fn rotation_ring_picking_matches_visible_radius() {
        let ray = Ray3d::new(Vec3::new(1.6, 0., 10.), Dir3::NEG_Z);
        assert!(ray_circle_distance(&ray, Vec3::ZERO, Vec3::Z, 1.6).unwrap() < 0.001);
        let miss = Ray3d::new(Vec3::new(3., 0., 10.), Dir3::NEG_Z);
        assert!(ray_circle_distance(&miss, Vec3::ZERO, Vec3::Z, 1.6).unwrap() > 1.);
    }
}
