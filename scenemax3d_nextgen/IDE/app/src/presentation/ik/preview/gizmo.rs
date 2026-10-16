use super::*;
use crate::presentation::scene3d::gizmo::{GizmoMaterial, math};
pub(super) struct Drag {
    axis: usize,
    origin: Vec3,
    start: Vec3,
    param: f32,
    vector: Vec3,
    key: String,
    mode: String,
    first: bool,
    last: f32,
}
pub(super) fn update(
    c: &mut Commands,
    s: &mut State,
    r: &mut Render,
    session: &Session,
    queue: &mut CommandQueue,
    viewport: Entity,
) {
    let (Some(meshes), Some(materials), Some(root)) =
        (r.meshes.as_mut(), r.gizmo_materials.as_mut(), s.render.root)
    else {
        return;
    };
    let t = pose(&s.data, s.edit_target);
    let size = s
        .render
        .camera
        .and_then(|e| r.cameras.get(e).ok())
        .map(|(_, _, g, _)| g.translation().distance(t.translation) * 0.12)
        .unwrap_or(0.5)
        .max(0.02);
    if s.render.gizmo.is_none() || s.render.gizmo_mode != s.mode {
        if let Some(e) = s.render.gizmo.take() {
            c.entity(e).try_despawn();
        }
        let g = c
            .spawn((Transform::default(), Visibility::default(), ChildOf(root)))
            .id();
        s.render.gizmo = Some(g);
        s.render.gizmo_mode = s.mode.into();
        for (axis, color) in [
            (Vec3::X, Color::srgb(1., 0.15, 0.15)),
            (Vec3::Y, Color::srgb(0.15, 1., 0.25)),
            (Vec3::Z, Color::srgb(0.2, 0.4, 1.)),
        ] {
            let rotate = s.mode == "Rotate";
            let mesh = if rotate {
                Torus::new(0.975, 1.025).mesh().build()
            } else {
                Cylinder::new(0.025, 1.).mesh().build()
            };
            c.spawn((
                Mesh3d(meshes.add(mesh)),
                MeshMaterial3d(materials.add(GizmoMaterial {
                    base_color: color.to_linear(),
                    emissive: color.to_linear(),
                })),
                Transform {
                    translation: if rotate { Vec3::ZERO } else { axis * 0.5 },
                    rotation: Quat::from_rotation_arc(Vec3::Y, axis),
                    ..default()
                },
                RenderLayers::layer(LAYER),
                ChildOf(g),
            ));
        }
    }
    if let Some(e) = s.render.gizmo {
        c.entity(e)
            .insert(Transform::from_translation(t.translation).with_scale(Vec3::splat(size)));
    }
    if !r.mouse.pressed(MouseButton::Left) {
        s.render.drag = None;
    }
    let Some(cursor) = r.windows.iter().next().and_then(Window::cursor_position) else {
        return;
    };
    let Ok((node, ui)) = r.ports.get(viewport) else {
        return;
    };
    let Some(inverse) = ui.try_inverse() else {
        return;
    };
    let local = inverse.transform_point2(cursor / node.inverse_scale_factor());
    let uv = local / node.size() + Vec2::splat(0.5);
    if uv.min_element() < 0. || uv.max_element() > 1. {
        return;
    }
    let Some((_, _, gt, Projection::Perspective(p))) =
        s.render.camera.and_then(|e| r.cameras.get(e).ok())
    else {
        return;
    };
    let v = Vec3::new(
        (uv.x * 2. - 1.) * p.aspect_ratio * (p.fov * 0.5).tan(),
        (1. - uv.y * 2.) * (p.fov * 0.5).tan(),
        -1.,
    );
    let Ok(direction) = Dir3::new(gt.rotation() * v) else {
        return;
    };
    let ray = Ray3d::new(gt.translation(), direction);
    if r.mouse.just_pressed(MouseButton::Left) {
        let picked = [Vec3::X, Vec3::Y, Vec3::Z]
            .iter()
            .enumerate()
            .filter_map(|(i, a)| {
                let d = if s.mode == "Rotate" {
                    math::ray_circle_distance(&ray, t.translation, *a, size)
                } else {
                    math::closest_distance_ray_segment(
                        &ray,
                        t.translation,
                        t.translation + *a * size,
                    )
                }?;
                (d < size * 0.15).then_some((i, d))
            })
            .min_by(|a, b| a.1.total_cmp(&b.1));
        if let Some((axis, _)) = picked {
            let a = [Vec3::X, Vec3::Y, Vec3::Z][axis];
            let key = format!(
                "/designerMetadata/preview{}{}",
                if s.edit_target { "Target" } else { "Model" },
                if s.mode == "Move" {
                    "Position"
                } else if s.mode == "Rotate" {
                    "Rotation"
                } else {
                    "Scale"
                }
            );
            s.render.drag = Some(Drag {
                axis,
                origin: t.translation,
                start: vec(s.data.pointer(&key).unwrap_or(&Value::Null), Vec3::ZERO),
                param: math::ray_line_param(&ray, t.translation, a).unwrap_or(0.),
                vector: math::ray_plane_point(&ray, t.translation, a)
                    .map(|p| (p - t.translation).normalize_or_zero())
                    .unwrap_or(Vec3::ZERO),
                key,
                mode: s.mode.into(),
                first: true,
                last: 0.,
            });
        }
    }
    let Some(d) = s.render.drag.as_mut() else {
        return;
    };
    let a = [Vec3::X, Vec3::Y, Vec3::Z][d.axis];
    let delta = if d.mode == "Rotate" {
        let Some(p) = math::ray_plane_point(&ray, d.origin, a) else {
            return;
        };
        let v = (p - d.origin).normalize_or_zero();
        a.dot(d.vector.cross(v)).atan2(d.vector.dot(v)).to_degrees()
    } else {
        let Some(p) = math::ray_line_param(&ray, d.origin, a) else {
            return;
        };
        (p - d.param) / if d.mode == "Scale" { size } else { 1. }
    };
    if (delta - d.last).abs() < 1e-5 {
        return;
    }
    d.last = delta;
    let mut value = d.start;
    value[d.axis] += delta;
    if d.mode == "Scale" {
        value[d.axis] = value[d.axis].max(0.01);
    }
    let Some(id) = session.workspace.active_id() else {
        return;
    };
    let Ok(doc) = session.workspace.document(id) else {
        return;
    };
    queue.0.push_front(Command::Ik(Edit {
        id,
        revision: doc.revision(),
        pointer: d.key.clone(),
        value: json!(value.to_array()),
        continuing: !d.first,
    }));
    d.first = false;
}
