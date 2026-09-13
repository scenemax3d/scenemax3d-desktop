//! Java cinematic ellipse overlays, owned by their scene document.
use super::*;
use serde_json::Value;

pub(super) fn track_point(data: &Value, anchor: f32) -> Vec3 {
    let count = data["anchorCount"].as_u64().unwrap_or(360).clamp(8, 4096) as f32;
    let angle = std::f32::consts::TAU * anchor / count;
    Vec3::new(
        angle.cos() * data["radiusX"].as_f64().unwrap_or(2.5).max(0.1) as f32,
        0.,
        angle.sin() * data["radiusZ"].as_f64().unwrap_or(2.5).max(0.1) as f32,
    )
}

pub(super) fn spawn(
    commands: &mut Commands,
    parent: Entity,
    meshes: &mut Assets<Mesh>,
    materials: &mut Assets<StandardMaterial>,
    data: &Value,
) {
    let count = data["anchorCount"].as_u64().unwrap_or(360).clamp(8, 4096) as usize;
    let start = data["selectedStartAnchor"].as_i64().unwrap_or(-1);
    let end = data["selectedEndAnchor"].as_i64().unwrap_or(-1);
    let points: Vec<_> = (0..=count).map(|i| track_point(data, i as f32)).collect();
    line(
        commands,
        parent,
        meshes,
        materials,
        &points,
        Color::srgb(0.8, 0.88, 1.),
    );
    if start >= 0 && end >= 0 {
        let start = start as usize % count;
        let end = end as usize % count;
        let selected: Vec<_> = (0..=(end + count - start) % count)
            .map(|i| points[(start + i) % count])
            .collect();
        line(
            commands,
            parent,
            meshes,
            materials,
            &selected,
            Color::srgb(1., 0.8, 0.2),
        );
    }
    let normal = materials.add(StandardMaterial {
        base_color: Color::srgb(0.5, 0.9, 1.),
        unlit: true,
        ..default()
    });
    let selected = materials.add(StandardMaterial {
        base_color: Color::srgb(1., 0.45, 0.15),
        unlit: true,
        ..default()
    });
    let small = meshes.add(Sphere::new(0.028).mesh().uv(6, 4));
    let large = meshes.add(Sphere::new(0.05).mesh().uv(6, 4));
    let highlight = meshes.add(Sphere::new(0.1).mesh().uv(10, 6));
    for (i, point) in points.iter().take(count).enumerate() {
        let is_selected = i as i64 == start || i as i64 == end;
        commands.spawn((
            Mesh3d(if is_selected {
                highlight.clone()
            } else if i % 15 == 0 {
                large.clone()
            } else {
                small.clone()
            }),
            MeshMaterial3d(if is_selected {
                selected.clone()
            } else {
                normal.clone()
            }),
            Transform::from_translation(*point),
            ChildOf(parent),
        ));
    }
}
pub(super) fn line(
    commands: &mut Commands,
    parent: Entity,
    meshes: &mut Assets<Mesh>,
    materials: &mut Assets<StandardMaterial>,
    points: &[Vec3],
    color: Color,
) {
    if points.len() < 2 {
        return;
    }
    let mesh = Mesh::new(
        bevy::mesh::PrimitiveTopology::LineStrip,
        RenderAssetUsages::all(),
    )
    .with_inserted_attribute(
        Mesh::ATTRIBUTE_POSITION,
        points.iter().map(|p| p.to_array()).collect::<Vec<_>>(),
    )
    .with_inserted_attribute(Mesh::ATTRIBUTE_NORMAL, vec![[0., 1., 0.]; points.len()]);
    commands.spawn((
        Mesh3d(meshes.add(mesh)),
        MeshMaterial3d(materials.add(StandardMaterial {
            base_color: color,
            unlit: true,
            cull_mode: None,
            ..default()
        })),
        Transform::default(),
        ChildOf(parent),
    ));
}

/// Sections are organizational; Java rigs own local transforms and start at the scene root.
pub(super) fn rig_parent(scene: &Scene3d, index: usize) -> Option<usize> {
    if scene.entities[index].kind == "CINEMATIC_RIG" {
        return None;
    }
    let mut parent = scene.entities[index].parent;
    while let Some(i) = parent {
        if scene.entities[i].kind == "CINEMATIC_RIG" {
            return Some(i);
        }
        parent = scene.entities[i].parent;
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn ellipse_anchors_match_java_xz_plane_and_close() {
        let data = serde_json::json!({"radiusX":8,"radiusZ":3,"anchorCount":360});
        assert!(track_point(&data, 0.).abs_diff_eq(Vec3::new(8., 0., 0.), 1e-5));
        assert!(track_point(&data, 90.).abs_diff_eq(Vec3::new(0., 0., 3.), 1e-5));
        assert!(track_point(&data, 360.).abs_diff_eq(track_point(&data, 0.), 1e-5));
    }
}
