use super::*;

pub(super) fn collider_decl_transform(
    name: &str,
    options: &EntityOptions,
    attaches_by_target: &HashMap<String, AttachStatement>,
    transforms_by_name: &HashMap<String, Transform>,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Transform {
    if let Some(attach) = attaches_by_target.get(name) {
        let owner = attach_owner(&attach.subject);
        if let Some(owner_transform) = transforms_by_name.get(&owner).copied() {
            return virtual_collider_transform(owner_transform, attach_fallback_offset(attach));
        }
    }
    primitive_transform_from_options_resolved(
        options,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        collider_bounds,
    )
}

pub(super) fn spawn_scenemax_collider_decl(
    commands: &mut Commands,
    name: &str,
    resource: &str,
    options: &EntityOptions,
    transform: Transform,
    attach: Option<&AttachStatement>,
) -> Entity {
    let shape = options
        .collision_shape
        .unwrap_or_else(|| default_collision_shape(name, resource, SceneMaxBodyKind::Static));
    let body = if attach.is_some() {
        AvianRigidBody::Kinematic
    } else {
        AvianRigidBody::Static
    };
    let mut entity = commands.spawn((
        SceneMaxEntity {
            name: name.to_owned(),
            runtime_name: format!("{name}@collider"),
        },
        transform,
        Visibility::Hidden,
        body,
        avian_collider(shape, options, &transform),
        hitbox_collision_layers(),
        Sensor,
        CollisionEventsEnabled,
    ));
    if let Some(attach) = attach {
        entity.insert(SceneMaxVirtualCollider {
            owner: attach_owner(&attach.subject),
            bone: attach_bone_name(&attach.subject),
            local_offset: vec3_from_scenemax(attach.offset),
            fallback_offset: attach_fallback_offset(attach),
        });
    }
    entity.id()
}

pub(super) fn primitive_mesh(
    options: &EntityOptions,
    resource: &str,
    asset_server: &AssetServer,
    asset_root: &Path,
    builtin_asset_root: Option<&Path>,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
) -> Option<(Mesh3d, MeshMaterial3d<StandardMaterial>)> {
    let mesh = match primitive_kind(resource)? {
        SceneMaxPrimitiveKind::Box => {
            let size = options.size.unwrap_or(SceneMaxVec3 {
                x: 1.0,
                y: 1.0,
                z: 1.0,
            });
            meshes.add(Cuboid::new(
                size.x.abs().max(0.001),
                size.y.abs().max(0.001),
                size.z.abs().max(0.001),
            ))
        }
        SceneMaxPrimitiveKind::Sphere => {
            meshes.add(Sphere::new(options.radius.unwrap_or(1.0).abs().max(0.001)))
        }
        SceneMaxPrimitiveKind::Cylinder => meshes.add(cylinder_like_mesh(
            options.radius_top.unwrap_or(1.0),
            options.radius_bottom.unwrap_or(1.0),
            options.height.unwrap_or(2.0),
            32,
            true,
        )),
        SceneMaxPrimitiveKind::HollowCylinder => meshes.add(hollow_cylinder_mesh(
            options.radius_top.unwrap_or(1.0),
            options.radius_bottom.unwrap_or(1.0),
            options.inner_radius_top.unwrap_or(0.5),
            options.inner_radius_bottom.unwrap_or(0.5),
            options.height.unwrap_or(2.0),
            32,
        )),
        SceneMaxPrimitiveKind::Quad => {
            let size = options.size.unwrap_or(SceneMaxVec3 {
                x: 1.0,
                y: 1.0,
                z: 1.0,
            });
            meshes.add(quad_mesh(size.x, size.y))
        }
        SceneMaxPrimitiveKind::Wedge => {
            let size = options.size.unwrap_or(SceneMaxVec3 {
                x: 1.0,
                y: 1.0,
                z: 1.0,
            });
            meshes.add(wedge_mesh(size.x, size.y, size.z))
        }
        SceneMaxPrimitiveKind::Cone => meshes.add(cylinder_like_mesh(
            options.radius_top.unwrap_or(0.0),
            options.radius_bottom.unwrap_or(1.0),
            options.height.unwrap_or(2.0),
            32,
            true,
        )),
        SceneMaxPrimitiveKind::Stairs => {
            let size = options.size.unwrap_or(SceneMaxVec3 {
                x: 2.0,
                y: 0.25,
                z: 0.4,
            });
            meshes.add(stairs_mesh(
                size.x,
                size.y,
                size.z,
                options.steps.unwrap_or(6),
            ))
        }
        SceneMaxPrimitiveKind::Arch => {
            let size = options.size.unwrap_or(SceneMaxVec3 {
                x: 2.0,
                y: 2.5,
                z: 0.5,
            });
            meshes.add(arch_mesh(
                size.x,
                size.y,
                size.z,
                options.thickness.unwrap_or(0.35),
                options.segments.unwrap_or(12),
            ))
        }
    };
    let material = primitive_standard_material(
        options,
        resource,
        asset_server,
        asset_root,
        builtin_asset_root,
    );
    Some((Mesh3d(mesh), MeshMaterial3d(materials.add(material))))
}

pub(super) fn primitive_fallback_color(resource: &str) -> Option<Color> {
    Some(match primitive_kind(resource)? {
        SceneMaxPrimitiveKind::Box => Color::srgb_u8(120, 135, 150),
        SceneMaxPrimitiveKind::Sphere => Color::srgb_u8(80, 170, 230),
        SceneMaxPrimitiveKind::Cylinder => Color::srgb_u8(90, 190, 160),
        SceneMaxPrimitiveKind::HollowCylinder => Color::srgb_u8(60, 200, 210),
        SceneMaxPrimitiveKind::Quad => Color::srgb_u8(210, 80, 190),
        SceneMaxPrimitiveKind::Wedge => Color::srgb_u8(220, 140, 45),
        SceneMaxPrimitiveKind::Cone => Color::srgb_u8(230, 210, 60),
        SceneMaxPrimitiveKind::Stairs => Color::srgb_u8(150, 95, 45),
        SceneMaxPrimitiveKind::Arch => Color::srgb_u8(170, 175, 180),
    })
}

fn primitive_standard_material(
    options: &EntityOptions,
    resource: &str,
    asset_server: &AssetServer,
    asset_root: &Path,
    builtin_asset_root: Option<&Path>,
) -> StandardMaterial {
    let fallback = primitive_fallback_color(resource).unwrap_or(Color::WHITE);
    let Some(material_name) = options.material.as_deref() else {
        return StandardMaterial {
            base_color: fallback,
            ..default()
        };
    };
    match resolve_scenemax_material(material_name, asset_root, builtin_asset_root) {
        Some(material) => {
            let mut standard = StandardMaterial {
                base_color: material.diffuse.unwrap_or(Color::WHITE),
                base_color_texture: material
                    .diffuse_map
                    .as_ref()
                    .map(|path| asset_server.load(path.clone())),
                normal_map_texture: material
                    .normal_map
                    .as_ref()
                    .map(|path| asset_server.load(path.clone())),
                emissive: material.glow_color.unwrap_or(LinearRgba::BLACK),
                emissive_texture: material
                    .glow_map
                    .as_ref()
                    .map(|path| asset_server.load(path.clone())),
                double_sided: material.double_sided,
                cull_mode: if material.double_sided {
                    None
                } else {
                    Some(bevy::render::render_resource::Face::Back)
                },
                alpha_mode: if material.transparent {
                    AlphaMode::Blend
                } else {
                    AlphaMode::Opaque
                },
                ..default()
            };
            if standard.emissive_texture.is_some() && standard.emissive == LinearRgba::BLACK {
                standard.emissive = LinearRgba::WHITE;
            }
            write_runtime_diagnostic_line(format!(
                "MATERIAL:APPLY name={} diffuse={} normal={} glow={} double_sided={} transparent={}",
                material_name,
                material.diffuse_map.as_deref().unwrap_or("<none>"),
                material.normal_map.as_deref().unwrap_or("<none>"),
                material.glow_map.as_deref().unwrap_or("<none>"),
                material.double_sided as u8,
                material.transparent as u8
            ));
            standard
        }
        None => {
            write_runtime_diagnostic_line(format!(
                "MATERIAL:MISS name={} primitive={}",
                material_name, resource
            ));
            StandardMaterial {
                base_color: fallback,
                ..default()
            }
        }
    }
}

#[derive(Debug, Default)]
struct SceneMaxResolvedMaterial {
    diffuse: Option<Color>,
    diffuse_map: Option<String>,
    normal_map: Option<String>,
    glow_color: Option<LinearRgba>,
    glow_map: Option<String>,
    double_sided: bool,
    transparent: bool,
}

fn resolve_scenemax_material(
    name: &str,
    asset_root: &Path,
    builtin_asset_root: Option<&Path>,
) -> Option<SceneMaxResolvedMaterial> {
    resolve_scenemax_material_in_root(name, asset_root, "").or_else(|| {
        builtin_asset_root
            .and_then(|root| resolve_scenemax_material_in_root(name, root, "builtin://"))
    })
}

fn resolve_scenemax_material_in_root(
    name: &str,
    root: &Path,
    asset_prefix: &str,
) -> Option<SceneMaxResolvedMaterial> {
    let index_path = root.join("material").join("materials-ext.json");
    let index_text = fs::read_to_string(index_path).ok()?;
    let index: serde_json::Value = serde_json::from_str(&index_text).ok()?;
    let entry = index
        .get("materials")
        .and_then(serde_json::Value::as_array)?
        .iter()
        .find(|entry| {
            entry
                .get("name")
                .and_then(serde_json::Value::as_str)
                .is_some_and(|value| value.eq_ignore_ascii_case(name))
        })?;
    let material_path = entry.get("path").and_then(serde_json::Value::as_str)?;
    let material_text = fs::read_to_string(root.join(material_path)).ok()?;
    let mut material = parse_j3m_material(&material_text, asset_prefix);
    material.double_sided = entry
        .get("doubleSided")
        .and_then(serde_json::Value::as_bool)
        .unwrap_or(material.double_sided);
    material.transparent = entry
        .get("transparent")
        .and_then(serde_json::Value::as_bool)
        .unwrap_or(material.transparent);
    Some(material)
}

fn parse_j3m_material(source: &str, asset_prefix: &str) -> SceneMaxResolvedMaterial {
    let mut material = SceneMaxResolvedMaterial::default();
    for line in source.lines() {
        let line = line.trim();
        if let Some(value) = line.strip_prefix("DiffuseMap") {
            material.diffuse_map = j3m_map_path(value, asset_prefix);
        } else if let Some(value) = line.strip_prefix("NormalMap") {
            material.normal_map = j3m_map_path(value, asset_prefix);
        } else if let Some(value) = line.strip_prefix("GlowMap") {
            material.glow_map = j3m_map_path(value, asset_prefix);
        } else if let Some(value) = line.strip_prefix("Diffuse") {
            material.diffuse = j3m_color(value).map(|[r, g, b, a]| Color::srgba(r, g, b, a));
        } else if let Some(value) = line.strip_prefix("GlowColor") {
            material.glow_color = j3m_color(value).map(|[r, g, b, a]| LinearRgba::new(r, g, b, a));
        }
    }
    material
}

fn j3m_map_path(value: &str, asset_prefix: &str) -> Option<String> {
    let (_, path) = value.split_once(':')?;
    let path = path.trim().replace('\\', "/");
    if path.is_empty() {
        None
    } else {
        Some(format!("{asset_prefix}{path}"))
    }
}

fn j3m_color(value: &str) -> Option<[f32; 4]> {
    let (_, values) = value.split_once(':')?;
    let values = values
        .split_whitespace()
        .map(str::parse::<f32>)
        .collect::<Result<Vec<_>, _>>()
        .ok()?;
    match values.as_slice() {
        [r, g, b] => Some([*r, *g, *b, 1.0]),
        [r, g, b, a] => Some([*r, *g, *b, *a]),
        _ => None,
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum SceneMaxPrimitiveKind {
    Box,
    Sphere,
    Cylinder,
    HollowCylinder,
    Quad,
    Wedge,
    Cone,
    Stairs,
    Arch,
}

fn primitive_kind(resource: &str) -> Option<SceneMaxPrimitiveKind> {
    match resource.to_ascii_lowercase().as_str() {
        "box" => Some(SceneMaxPrimitiveKind::Box),
        "sphere" => Some(SceneMaxPrimitiveKind::Sphere),
        "cylinder" => Some(SceneMaxPrimitiveKind::Cylinder),
        "hollow cylinder" | "hollowcylinder" => Some(SceneMaxPrimitiveKind::HollowCylinder),
        "quad" => Some(SceneMaxPrimitiveKind::Quad),
        "wedge" => Some(SceneMaxPrimitiveKind::Wedge),
        "cone" => Some(SceneMaxPrimitiveKind::Cone),
        "stairs" => Some(SceneMaxPrimitiveKind::Stairs),
        "arch" => Some(SceneMaxPrimitiveKind::Arch),
        _ => return None,
    }
}

pub(super) fn is_primitive_resource(resource: &str) -> bool {
    primitive_kind(resource).is_some()
}

fn quad_mesh(width: f32, height: f32) -> Mesh {
    let width = width.abs().max(0.001);
    let height = height.abs().max(0.001);
    let mut mesh = Mesh::new(
        PrimitiveTopology::TriangleList,
        RenderAssetUsages::default(),
    )
    .with_inserted_attribute(
        Mesh::ATTRIBUTE_POSITION,
        vec![
            [0.0, 0.0, 0.0],
            [width, 0.0, 0.0],
            [width, height, 0.0],
            [0.0, height, 0.0],
        ],
    )
    .with_inserted_attribute(Mesh::ATTRIBUTE_NORMAL, vec![[0.0, 0.0, 1.0]; 4])
    .with_inserted_attribute(
        Mesh::ATTRIBUTE_UV_0,
        vec![[0.0, 1.0], [1.0, 1.0], [1.0, 0.0], [0.0, 0.0]],
    )
    .with_inserted_indices(Indices::U32(vec![0, 1, 2, 2, 3, 0]));
    let _ = mesh.generate_tangents();
    mesh
}

fn wedge_mesh(width: f32, height: f32, depth: f32) -> Mesh {
    let half_width = width.abs().max(0.001) * 0.5;
    let half_height = height.abs().max(0.001) * 0.5;
    let half_depth = depth.abs().max(0.001) * 0.5;
    let positions = vec![
        [-half_width, -half_height, -half_depth],
        [half_width, -half_height, -half_depth],
        [-half_width, -half_height, half_depth],
        [half_width, -half_height, half_depth],
        [-half_width, half_height, half_depth],
        [half_width, half_height, half_depth],
    ];
    let indices = vec![
        0, 2, 1, 1, 2, 3, 2, 4, 3, 3, 4, 5, 0, 1, 4, 1, 5, 4, 0, 4, 2, 1, 3, 5,
    ];
    mesh_from_positions_indices(positions, indices)
}

fn cylinder_like_mesh(
    radius_top: f32,
    radius_bottom: f32,
    height: f32,
    segments: usize,
    capped: bool,
) -> Mesh {
    let segments = segments.max(3);
    let radius_top = radius_top.abs().max(0.0001);
    let radius_bottom = radius_bottom.abs().max(0.0001);
    let half_height = height.abs().max(0.0001) * 0.5;
    let mut positions = Vec::with_capacity(segments * 2 + if capped { 2 } else { 0 });
    for y in [half_height, -half_height] {
        let radius = if y > 0.0 { radius_top } else { radius_bottom };
        for i in 0..segments {
            let angle = std::f32::consts::TAU * i as f32 / segments as f32;
            positions.push([angle.cos() * radius, y, angle.sin() * radius]);
        }
    }

    let mut indices = Vec::with_capacity(segments * 12);
    for i in 0..segments {
        let next = (i + 1) % segments;
        let top_a = i as u32;
        let top_b = next as u32;
        let bottom_a = (segments + i) as u32;
        let bottom_b = (segments + next) as u32;
        indices.extend_from_slice(&[top_a, bottom_a, top_b, top_b, bottom_a, bottom_b]);
    }
    if capped {
        let top_center = positions.len() as u32;
        positions.push([0.0, half_height, 0.0]);
        let bottom_center = positions.len() as u32;
        positions.push([0.0, -half_height, 0.0]);
        for i in 0..segments {
            let next = (i + 1) % segments;
            indices.extend_from_slice(&[top_center, next as u32, i as u32]);
            indices.extend_from_slice(&[
                bottom_center,
                (segments + i) as u32,
                (segments + next) as u32,
            ]);
        }
    }
    mesh_from_positions_indices(positions, indices)
}

fn hollow_cylinder_mesh(
    outer_top: f32,
    outer_bottom: f32,
    inner_top: f32,
    inner_bottom: f32,
    height: f32,
    segments: usize,
) -> Mesh {
    let segments = segments.max(3);
    let outer_top = outer_top.abs().max(0.0001);
    let outer_bottom = outer_bottom.abs().max(0.0001);
    let inner_top = inner_top.abs().min(outer_top * 0.95).max(0.0001);
    let inner_bottom = inner_bottom.abs().min(outer_bottom * 0.95).max(0.0001);
    let half_height = height.abs().max(0.0001) * 0.5;
    let mut positions = Vec::with_capacity(segments * 4);
    for (radius, y) in [
        (outer_top, half_height),
        (outer_bottom, -half_height),
        (inner_top, half_height),
        (inner_bottom, -half_height),
    ] {
        for i in 0..segments {
            let angle = std::f32::consts::TAU * i as f32 / segments as f32;
            positions.push([angle.cos() * radius, y, angle.sin() * radius]);
        }
    }

    let outer_top_offset = 0;
    let outer_bottom_offset = segments;
    let inner_top_offset = segments * 2;
    let inner_bottom_offset = segments * 3;
    let mut indices = Vec::with_capacity(segments * 24);
    for i in 0..segments {
        let next = (i + 1) % segments;
        push_ring_quad(
            &mut indices,
            outer_top_offset + i,
            outer_bottom_offset + i,
            outer_top_offset + next,
            outer_bottom_offset + next,
        );
        push_ring_quad(
            &mut indices,
            inner_top_offset + next,
            inner_bottom_offset + next,
            inner_top_offset + i,
            inner_bottom_offset + i,
        );
        push_ring_quad(
            &mut indices,
            outer_top_offset + next,
            inner_top_offset + next,
            outer_top_offset + i,
            inner_top_offset + i,
        );
        push_ring_quad(
            &mut indices,
            outer_bottom_offset + i,
            inner_bottom_offset + i,
            outer_bottom_offset + next,
            inner_bottom_offset + next,
        );
    }
    mesh_from_positions_indices(positions, indices)
}

fn stairs_mesh(width: f32, step_height: f32, step_depth: f32, steps: usize) -> Mesh {
    let width = width.abs().max(0.05);
    let step_height = step_height.abs().max(0.01);
    let step_depth = step_depth.abs().max(0.01);
    let steps = steps.max(1);
    let total_height = step_height * steps as f32;
    let total_depth = step_depth * steps as f32;
    let mut builder = BoxMeshBuilder::default();
    for i in 0..steps {
        let box_height = step_height * (i + 1) as f32;
        let center = Vec3::new(
            0.0,
            -total_height * 0.5 + box_height * 0.5,
            -total_depth * 0.5 + step_depth * (i as f32 + 0.5),
        );
        builder.add_box(width, box_height, step_depth, center, Quat::IDENTITY);
    }
    builder.finish()
}

fn arch_mesh(width: f32, height: f32, depth: f32, thickness: f32, segments: usize) -> Mesh {
    let width = width.abs().max(0.2);
    let height = height.abs().max(0.2);
    let depth = depth.abs().max(0.05);
    let thickness = thickness.abs().max(0.05).min(width * 0.45);
    let segments = segments.max(4);
    let outer_radius = width * 0.5;
    let inner_radius = (outer_radius - thickness).max(0.05);
    let spring_height = (height - outer_radius).max(0.0);
    let total_height = spring_height + outer_radius;
    let y_offset = -total_height * 0.5;
    let leg_height = spring_height.max(0.05);
    let mut builder = BoxMeshBuilder::default();
    builder.add_box(
        thickness,
        leg_height,
        depth,
        Vec3::new(
            -width * 0.5 + thickness * 0.5,
            y_offset + leg_height * 0.5,
            0.0,
        ),
        Quat::IDENTITY,
    );
    builder.add_box(
        thickness,
        leg_height,
        depth,
        Vec3::new(
            width * 0.5 - thickness * 0.5,
            y_offset + leg_height * 0.5,
            0.0,
        ),
        Quat::IDENTITY,
    );

    let radius_mid = (outer_radius + inner_radius) * 0.5;
    let segment_length = (radius_mid * std::f32::consts::PI / segments as f32).max(0.05);
    let segment_thickness = (outer_radius - inner_radius).max(0.05);
    let center_y = y_offset + leg_height;
    for i in 0..segments {
        let t0 = std::f32::consts::PI - (std::f32::consts::PI * i as f32 / segments as f32);
        let t1 = std::f32::consts::PI - (std::f32::consts::PI * (i + 1) as f32 / segments as f32);
        let angle = (t0 + t1) * 0.5;
        let center = Vec3::new(
            angle.cos() * radius_mid,
            center_y + angle.sin() * radius_mid,
            0.0,
        );
        builder.add_box(
            segment_length,
            segment_thickness,
            depth,
            center,
            Quat::from_rotation_z(angle - std::f32::consts::FRAC_PI_2),
        );
    }
    builder.finish()
}

fn push_ring_quad(indices: &mut Vec<u32>, a: usize, b: usize, c: usize, d: usize) {
    indices.extend_from_slice(&[a as u32, b as u32, c as u32, c as u32, b as u32, d as u32]);
}

fn mesh_from_positions_indices(positions: Vec<[f32; 3]>, indices: Vec<u32>) -> Mesh {
    let normals = smooth_normals(&positions, &indices);
    let uvs = vec![[0.0, 0.0]; positions.len()];
    Mesh::new(
        PrimitiveTopology::TriangleList,
        RenderAssetUsages::default(),
    )
    .with_inserted_attribute(Mesh::ATTRIBUTE_POSITION, positions)
    .with_inserted_attribute(Mesh::ATTRIBUTE_NORMAL, normals)
    .with_inserted_attribute(Mesh::ATTRIBUTE_UV_0, uvs)
    .with_inserted_indices(Indices::U32(indices))
}

fn smooth_normals(positions: &[[f32; 3]], indices: &[u32]) -> Vec<[f32; 3]> {
    let mut normals = vec![Vec3::ZERO; positions.len()];
    for triangle in indices.chunks_exact(3) {
        let a = Vec3::from_array(positions[triangle[0] as usize]);
        let b = Vec3::from_array(positions[triangle[1] as usize]);
        let c = Vec3::from_array(positions[triangle[2] as usize]);
        let normal = (b - a).cross(c - a);
        if normal.length_squared() <= f32::EPSILON {
            continue;
        }
        let normal = normal.normalize();
        for index in triangle {
            normals[*index as usize] += normal;
        }
    }
    normals
        .into_iter()
        .map(|normal| normal.try_normalize().unwrap_or(Vec3::Y).to_array())
        .collect()
}

#[derive(Default)]
struct BoxMeshBuilder {
    positions: Vec<[f32; 3]>,
    indices: Vec<u32>,
}

impl BoxMeshBuilder {
    fn add_box(&mut self, width: f32, height: f32, depth: f32, center: Vec3, rotation: Quat) {
        let half = Vec3::new(width * 0.5, height * 0.5, depth * 0.5);
        let base = self.positions.len() as u32;
        let corners = [
            Vec3::new(-half.x, -half.y, -half.z),
            Vec3::new(half.x, -half.y, -half.z),
            Vec3::new(half.x, half.y, -half.z),
            Vec3::new(-half.x, half.y, -half.z),
            Vec3::new(-half.x, -half.y, half.z),
            Vec3::new(half.x, -half.y, half.z),
            Vec3::new(half.x, half.y, half.z),
            Vec3::new(-half.x, half.y, half.z),
        ];
        self.positions.extend(
            corners
                .into_iter()
                .map(|corner| (center + rotation * corner).to_array()),
        );
        self.indices.extend_from_slice(&[
            base,
            base + 2,
            base + 1,
            base,
            base + 3,
            base + 2,
            base + 4,
            base + 5,
            base + 6,
            base + 4,
            base + 6,
            base + 7,
            base,
            base + 1,
            base + 5,
            base,
            base + 5,
            base + 4,
            base + 3,
            base + 6,
            base + 2,
            base + 3,
            base + 7,
            base + 6,
            base + 1,
            base + 2,
            base + 6,
            base + 1,
            base + 6,
            base + 5,
            base,
            base + 4,
            base + 7,
            base,
            base + 7,
            base + 3,
        ]);
    }

    fn finish(self) -> Mesh {
        mesh_from_positions_indices(self.positions, self.indices)
    }
}

pub(super) fn collect_model_declarations(program: &Program) -> Vec<ModelRuntimeDecl> {
    program
        .statements
        .iter()
        .filter_map(|statement| {
            let Statement::ModelDecl {
                name,
                resource,
                options,
            } = statement
            else {
                return None;
            };
            if cinematic_resource_id(resource).is_some() {
                return None;
            }
            Some(ModelRuntimeDecl {
                name: name.clone(),
                resource: resource.clone(),
                options: options.clone(),
            })
        })
        .collect()
}

pub(super) fn instantiate_object_pool_declarations(
    program: &Program,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    object_pools: &mut SceneMaxObjectPools,
) -> Vec<ModelRuntimeDecl> {
    let mut declarations = Vec::new();
    for statement in &program.statements {
        let Statement::ObjectPool(pool) = statement else {
            continue;
        };
        let Some(prototype) = object_pool_prototype(pool, functions_by_name) else {
            tracing::warn!(
                pool = %pool.name,
                factory = %pool.factory,
                "SceneMax object pool factory has no model declaration"
            );
            continue;
        };

        let mut runtime = ObjectPoolRuntime {
            factory: pool.factory.clone(),
            prototype: Some(prototype.clone()),
            ..Default::default()
        };
        for index in 0..pool.size.min(256) {
            let member_name = format!("__pool_{}_{}", pool.name, index);
            runtime.available.push(member_name.clone());
            runtime.members.insert(member_name.clone());
            let mut options = prototype.options.clone();
            options.hidden = true;
            declarations.push(ModelRuntimeDecl {
                name: member_name,
                resource: prototype.resource.clone(),
                options,
            });
            runtime.created_count += 1;
        }
        runtime.available.reverse();
        object_pools.pools.insert(pool.name.clone(), runtime);
        tracing::info!(
            pool = %pool.name,
            factory = %pool.factory,
            size = pool.size,
            "prepared SceneMax object pool"
        );
    }
    declarations
}

pub(super) fn object_pool_prototype(
    pool: &ObjectPoolStatement,
    functions_by_name: &HashMap<String, FunctionRuntime>,
) -> Option<ModelRuntimeDecl> {
    let function = functions_by_name.get(&pool.factory)?;
    function.actions.iter().find_map(|action| {
        let Statement::ModelDecl {
            name,
            resource,
            options,
        } = action
        else {
            return None;
        };
        let returned_name = function.actions.iter().find_map(|action| {
            let Statement::ReturnValue {
                value: AssignmentValue::Symbol(value),
            } = action
            else {
                return None;
            };
            Some(value)
        });
        if returned_name.is_some_and(|returned_name| returned_name != name) {
            return None;
        }
        Some(ModelRuntimeDecl {
            name: String::new(),
            resource: resource.clone(),
            options: options.clone(),
        })
    })
}

pub(super) fn spawn_unsupported_model_placeholder(
    commands: &mut Commands,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
    name: &str,
    resource: &str,
    options: &EntityOptions,
    transform: Transform,
    visibility_by_target: &HashMap<String, bool>,
) -> Entity {
    let (mesh, material) = unsupported_model_placeholder_mesh(resource, meshes, materials);
    commands
        .spawn((
            SceneMaxEntity {
                name: name.to_owned(),
                runtime_name: format!("{name}@placeholder"),
            },
            mesh,
            material,
            transform,
            initial_visibility(name, options, visibility_by_target),
        ))
        .id()
}

pub(super) fn unsupported_model_placeholder_mesh(
    resource: &str,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
) -> (Mesh3d, MeshMaterial3d<StandardMaterial>) {
    let lower = resource.to_ascii_lowercase();
    let (mesh, color) = if lower.contains("crystal") {
        (meshes.add(Sphere::new(0.8)), Color::srgb_u8(75, 210, 255))
    } else if lower.contains("axe") {
        (
            meshes.add(Cuboid::new(0.25, 0.12, 1.2)),
            Color::srgb_u8(150, 150, 160),
        )
    } else if lower.contains("wooden_box") || lower.contains("box") {
        (
            meshes.add(Cuboid::new(1.0, 1.0, 1.0)),
            Color::srgb_u8(150, 95, 45),
        )
    } else if lower.contains("gate") {
        (
            meshes.add(Cuboid::new(1.0, 2.0, 0.25)),
            Color::srgb_u8(80, 110, 150),
        )
    } else {
        (
            meshes.add(Cuboid::new(1.0, 1.0, 1.0)),
            Color::srgb_u8(120, 135, 150),
        )
    };
    (Mesh3d(mesh), MeshMaterial3d(materials.add(color)))
}

pub(super) fn initial_visibility(
    name: &str,
    options: &EntityOptions,
    visibility_by_target: &HashMap<String, bool>,
) -> Visibility {
    match visibility_by_target.get(name).copied() {
        Some(true) => Visibility::Inherited,
        Some(false) => Visibility::Hidden,
        None if options.hidden => Visibility::Hidden,
        None => Visibility::Inherited,
    }
}

pub(super) fn primitive_transform_from_options_resolved(
    options: &EntityOptions,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Transform {
    transform_from_options_resolved(
        options,
        None,
        vars,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
}

pub(super) fn insert_physics_components(
    commands: &mut Commands,
    entity: Entity,
    name: &str,
    resource: &str,
    options: &EntityOptions,
    transform: &Transform,
) {
    let Some(body_kind) = physics_body_kind(options) else {
        return;
    };
    let Some(shape) = physics_collision_shape(name, resource, options, body_kind) else {
        return;
    };
    let body = match body_kind {
        SceneMaxBodyKind::Static => AvianRigidBody::Static,
        SceneMaxBodyKind::Kinematic => AvianRigidBody::Kinematic,
        SceneMaxBodyKind::Dynamic => AvianRigidBody::Dynamic,
    };
    let collider = avian_collider(shape, options, transform);
    commands.entity(entity).insert((
        body,
        collider,
        solid_collision_layers(body_kind),
        CollisionEventsEnabled,
    ));
    tracing::debug!(
        name,
        resource,
        ?body_kind,
        ?shape,
        "attached Avian physics body"
    );
}

pub(super) fn physics_body_kind(options: &EntityOptions) -> Option<SceneMaxBodyKind> {
    options.body_kind.or_else(|| {
        options
            .collision_shape
            .is_some_and(|shape| shape != SceneMaxCollisionShape::None)
            .then_some(SceneMaxBodyKind::Static)
    })
}

pub(super) fn physics_collision_shape(
    name: &str,
    resource: &str,
    options: &EntityOptions,
    body_kind: SceneMaxBodyKind,
) -> Option<SceneMaxCollisionShape> {
    match options.collision_shape {
        Some(SceneMaxCollisionShape::None) => None,
        Some(shape) => Some(shape),
        None => Some(default_collision_shape(name, resource, body_kind)),
    }
}

pub(super) fn default_collision_shape(
    _name: &str,
    resource: &str,
    _body_kind: SceneMaxBodyKind,
) -> SceneMaxCollisionShape {
    if resource.eq_ignore_ascii_case("sphere") {
        SceneMaxCollisionShape::Sphere
    } else {
        SceneMaxCollisionShape::Box
    }
}

pub(super) fn avian_collider(
    shape: SceneMaxCollisionShape,
    options: &EntityOptions,
    transform: &Transform,
) -> AvianCollider {
    let dimensions = collider_dimensions(options, transform);
    match shape {
        SceneMaxCollisionShape::None => AvianCollider::cuboid(0.1, 0.1, 0.1),
        SceneMaxCollisionShape::Box => {
            AvianCollider::cuboid(dimensions.x, dimensions.y, dimensions.z)
        }
        SceneMaxCollisionShape::Sphere => AvianCollider::sphere(dimensions.max_element() * 0.5),
        SceneMaxCollisionShape::Capsule => {
            let radius = dimensions.x.max(dimensions.z).max(0.2) * 0.3;
            let height = dimensions.y.max(radius * 2.0);
            AvianCollider::capsule(radius, height)
        }
    }
}

pub(super) fn collider_dimensions(options: &EntityOptions, transform: &Transform) -> Vec3 {
    if let Some(radius) = options.radius {
        return Vec3::splat((radius * 2.0).max(0.1));
    }
    options
        .size
        .map(vec3_from_scenemax)
        .unwrap_or_else(|| transform.scale.abs())
        .max(Vec3::splat(0.1))
}

pub(super) fn collider_bound_shape(
    options: &EntityOptions,
    transform: Transform,
) -> ColliderBoundShape {
    let dimensions = collider_dimensions(options, &transform);
    match options
        .collision_shape
        .unwrap_or(SceneMaxCollisionShape::Box)
    {
        SceneMaxCollisionShape::Sphere => ColliderBoundShape::Sphere {
            radius: dimensions.max_element() * 0.5,
        },
        SceneMaxCollisionShape::Box => ColliderBoundShape::Box {
            half_extents: dimensions * 0.5,
        },
        SceneMaxCollisionShape::Capsule => {
            let radius = dimensions.x.max(dimensions.z).max(0.2) * 0.3;
            let height = dimensions.y.max(radius * 2.0);
            ColliderBoundShape::Capsule {
                radius,
                half_height: height * 0.5,
            }
        }
        SceneMaxCollisionShape::None => ColliderBoundShape::Sphere { radius: 0.0 },
    }
}

pub(super) fn register_collider_bounds(
    collider_bounds: &mut SceneMaxColliderBounds,
    name: &str,
    options: &EntityOptions,
    transform: Transform,
) {
    let shape = collider_bound_shape(options, transform);
    let radius = shape.bounding_radius().max(0.01);
    collider_bounds
        .radius_by_name
        .insert(name.to_owned(), radius);
    collider_bounds.shape_by_name.insert(name.to_owned(), shape);
}

pub(super) fn register_collider_owner(
    collider_bounds: &mut SceneMaxColliderBounds,
    name: &str,
    owner: &str,
) {
    collider_bounds
        .owner_by_name
        .insert(name.to_owned(), owner.to_owned());
}

pub(super) fn solid_collision_layers(body_kind: SceneMaxBodyKind) -> CollisionLayers {
    match body_kind {
        SceneMaxBodyKind::Static => world_collision_layers(),
        SceneMaxBodyKind::Kinematic | SceneMaxBodyKind::Dynamic => {
            CollisionLayers::from_bits(PHYSICS_LAYER_WORLD, PHYSICS_LAYER_CHARACTER)
        }
    }
}

pub(super) fn world_collision_layers() -> CollisionLayers {
    CollisionLayers::from_bits(
        PHYSICS_LAYER_WORLD,
        PHYSICS_LAYER_WORLD | PHYSICS_LAYER_CHARACTER,
    )
}

pub(super) fn character_collision_layers() -> CollisionLayers {
    CollisionLayers::from_bits(PHYSICS_LAYER_CHARACTER, PHYSICS_LAYER_WORLD)
}

pub(super) fn hitbox_collision_layers() -> CollisionLayers {
    CollisionLayers::from_bits(PHYSICS_LAYER_HITBOX, PHYSICS_LAYER_HITBOX)
}

pub(super) fn virtual_collider_transform(
    owner_transform: Transform,
    local_offset: Vec3,
) -> Transform {
    Transform {
        translation: owner_transform.translation
            + owner_transform.rotation * (local_offset * owner_transform.scale.abs()),
        rotation: owner_transform.rotation,
        scale: Vec3::ONE,
    }
}

pub(super) fn attach_owner(subject: &str) -> String {
    collision_owner(subject)
}

pub(super) fn attach_fallback_offset(attach: &AttachStatement) -> Vec3 {
    attachment_bone_offset(&attach.subject) + vec3_from_scenemax(attach.offset)
}

pub(super) fn attach_bone_name(subject: &str) -> Option<String> {
    let start = subject.find('"')?;
    let after_start = &subject[start + 1..];
    let end = after_start.find('"')?;
    let bone = after_start[..end].trim();
    (!bone.is_empty()).then(|| bone.to_owned())
}

pub(super) fn attachment_bone_offset(subject: &str) -> Vec3 {
    let lower = subject.to_ascii_lowercase();
    if lower.contains("head") {
        Vec3::new(0.0, 1.65, 0.0)
    } else if lower.contains("lefthand") {
        Vec3::new(-0.45, 1.15, 0.2)
    } else if lower.contains("righthand") {
        Vec3::new(0.45, 1.15, 0.2)
    } else if lower.contains("leftfoot") {
        Vec3::new(-0.22, 0.18, 0.18)
    } else if lower.contains("rightfoot") {
        Vec3::new(0.22, 0.18, 0.18)
    } else {
        Vec3::ZERO
    }
}

pub(super) fn update_virtual_colliders(
    owners: Query<(Entity, &SceneMaxEntity, &Transform), Without<SceneMaxVirtualCollider>>,
    children: Query<&Children>,
    named_nodes: Query<(&Name, &GlobalTransform)>,
    mut colliders: Query<(&SceneMaxVirtualCollider, &mut Transform)>,
) {
    let owner_transforms = owners
        .iter()
        .map(|(owner_entity, scene_entity, transform)| {
            (scene_entity.name.clone(), (owner_entity, *transform))
        })
        .collect::<HashMap<_, _>>();

    for (collider, mut transform) in &mut colliders {
        let Some((owner_entity, owner_transform)) =
            owner_transforms.get(collider.owner.as_str()).copied()
        else {
            continue;
        };
        if let Some(bone) = collider.bone.as_deref() {
            if let Some(bone_transform) =
                find_descendant_transform_by_name(owner_entity, bone, &children, &named_nodes)
            {
                *transform = attachment_node_transform(bone_transform, collider.local_offset);
                continue;
            }
        }
        *transform = virtual_collider_transform(owner_transform, collider.fallback_offset);
    }
}

pub(super) fn find_descendant_transform_by_name(
    root: Entity,
    wanted_name: &str,
    children: &Query<&Children>,
    named_nodes: &Query<(&Name, &GlobalTransform)>,
) -> Option<Transform> {
    for child in children.get(root).ok()?.iter() {
        if let Ok((name, transform)) = named_nodes.get(child) {
            if names_match(name.as_str(), wanted_name) {
                return Some(transform.compute_transform());
            }
        }
        if let Some(transform) =
            find_descendant_transform_by_name(child, wanted_name, children, named_nodes)
        {
            return Some(transform);
        }
    }
    None
}

pub(super) fn names_match(actual: &str, wanted: &str) -> bool {
    actual.eq_ignore_ascii_case(wanted)
        || actual
            .rsplit(['/', '.'])
            .next()
            .is_some_and(|tail| tail.eq_ignore_ascii_case(wanted))
}

pub(super) fn attachment_node_transform(
    node_transform: Transform,
    local_offset: Vec3,
) -> Transform {
    Transform {
        translation: node_transform.translation
            + node_transform.rotation * (local_offset * node_transform.scale.abs()),
        rotation: node_transform.rotation,
        scale: Vec3::ONE,
    }
}

pub(super) fn apply_look_at_commands(
    program: &Program,
    commands: &mut Commands,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
) {
    for statement in &program.statements {
        let Statement::LookAt { target, subject } = statement else {
            continue;
        };
        let Some(entity) = entities_by_name.get(target).copied() else {
            continue;
        };
        let (Some(target_transform), Some(subject_transform)) = (
            transforms_by_name.get(target).copied(),
            lookup_subject_transform(subject, transforms_by_name),
        ) else {
            continue;
        };
        let mut updated = target_transform;
        look_at_scenemax_forward(&mut updated, subject_transform.translation);
        commands.entity(entity).insert(updated);
        transforms_by_name.insert(target.clone(), updated);
    }
}

pub(super) fn apply_character_modes(
    program: &Program,
    commands: &mut Commands,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &HashMap<String, Transform>,
    character_configs: &mut ResMut<Assets<SceneMaxControlSchemeConfig>>,
) {
    let mut support_samples = Vec::new();
    for statement in &program.statements {
        let Statement::CharacterMode(character_mode) = statement else {
            continue;
        };
        let Some(entity) = entities_by_name.get(&character_mode.target).copied() else {
            tracing::warn!(
                target = character_mode.target,
                "SceneMax character mode target was not spawned"
            );
            continue;
        };
        let transform = transforms_by_name
            .get(&character_mode.target)
            .copied()
            .unwrap_or_default();
        let dimensions = character_dimensions_for_transform(&transform);
        support_samples.push((character_mode.target.clone(), transform, dimensions));
        insert_tnua_character_controller(
            commands,
            entity,
            character_mode,
            transform,
            character_configs,
        );
    }
    spawn_character_stage_support(commands, &support_samples);
}

#[derive(Debug, Clone, Copy)]
pub(super) struct SceneMaxCharacterDimensions {
    pub(super) capsule_radius: f32,
    pub(super) capsule_height: f32,
    pub(super) capsule_center_y: f32,
    pub(super) float_height: f32,
    pub(super) foot_contact_offset: f32,
    pub(super) visual_drop: f32,
}

pub(super) fn character_dimensions_for_transform(
    transform: &Transform,
) -> SceneMaxCharacterDimensions {
    let character_scale = transform.scale.abs().max_element().max(1.0);
    let capsule_radius = DEFAULT_CHARACTER_CAPSULE_RADIUS * character_scale;
    let capsule_height = DEFAULT_CHARACTER_CAPSULE_HEIGHT * character_scale;
    let visual_drop = DEFAULT_CHARACTER_VISUAL_DROP * character_scale;
    let capsule_half_height = character_capsule_half_height(capsule_radius, capsule_height);
    SceneMaxCharacterDimensions {
        capsule_radius,
        capsule_height,
        capsule_center_y: capsule_half_height,
        float_height: DEFAULT_CHARACTER_FLOAT_HEIGHT * character_scale,
        foot_contact_offset: DEFAULT_CHARACTER_FOOT_CONTACT_OFFSET * character_scale,
        visual_drop,
    }
}

pub(super) fn character_capsule_half_height(radius: f32, height: f32) -> f32 {
    height * 0.5 + radius
}

pub(super) fn insert_tnua_character_controller(
    commands: &mut Commands,
    entity: Entity,
    character_mode: &CharacterModeStatement,
    transform: Transform,
    character_configs: &mut ResMut<Assets<SceneMaxControlSchemeConfig>>,
) {
    let gravity = character_mode.gravity.unwrap_or(DEFAULT_CHARACTER_GRAVITY);
    let dimensions = character_dimensions_for_transform(&transform);
    let radius = dimensions.capsule_radius;
    let height = dimensions.capsule_height;
    let float_height = dimensions.float_height;
    let capsule_center_y = dimensions.capsule_center_y;
    let foot_contact_offset = dimensions.foot_contact_offset;
    let capsule_collider = AvianCollider::compound(vec![(
        Vec3::Y * capsule_center_y,
        Quat::IDENTITY,
        AvianCollider::capsule(radius, height),
    )]);
    let config = character_configs.add(SceneMaxControlSchemeConfig {
        basis: TnuaBuiltinWalkConfig {
            speed: DEFAULT_CHARACTER_MOVE_SPEED,
            float_height,
            free_fall_extra_gravity: gravity,
            ..Default::default()
        },
        jump: TnuaBuiltinJumpConfig {
            height: jump_height(35.0),
            fall_extra_gravity: gravity * 0.35,
            shorten_extra_gravity: gravity,
            ..Default::default()
        },
    });

    commands.entity(entity).insert((
        SceneMaxCharacterController {
            move_speed: DEFAULT_CHARACTER_MOVE_SPEED,
            gravity,
            capsule_radius: radius,
            capsule_height: height,
            capsule_center_y,
            float_height,
        },
        SceneMaxCharacterMotor::default(),
        AvianRigidBody::Dynamic,
        capsule_collider,
        character_collision_layers(),
        LinearVelocity::ZERO,
        AngularVelocity::ZERO,
        TnuaController::<SceneMaxControlScheme>::default(),
        TnuaConfig::<SceneMaxControlScheme>(config),
        TnuaAvian3dSensorShape(AvianCollider::cylinder(
            radius * 0.98,
            DEFAULT_CHARACTER_SENSOR_HEIGHT,
        )),
        LockedAxes::ROTATION_LOCKED.unlock_rotation_y(),
        CollisionEventsEnabled,
    ));
    tracing::info!(
        target = character_mode.target,
        gravity,
        radius,
        height,
        capsule_center_y,
        float_height,
        foot_contact_offset,
        visual_drop = dimensions.visual_drop,
        "enabled Tnua SceneMax character mode"
    );
    write_runtime_diagnostic_line(format!(
        "CHARACTER target={} scale=({:.3},{:.3},{:.3}) capsule_radius={:.3} capsule_height={:.3} capsule_center_y={:.3} float_height={:.3} foot_contact_offset={:.3} visual_drop={:.3}",
        character_mode.target,
        transform.scale.x,
        transform.scale.y,
        transform.scale.z,
        radius,
        height,
        capsule_center_y,
        float_height,
        foot_contact_offset,
        dimensions.visual_drop,
    ));
}

pub(super) fn apply_pending_character_modes(
    mut commands: Commands,
    mut character_configs: ResMut<Assets<SceneMaxControlSchemeConfig>>,
    pending: Query<(Entity, &Transform, &PendingCharacterMode)>,
    supports: Query<Entity, With<SceneMaxStageSupport>>,
) {
    let pending_modes = pending.iter().collect::<Vec<_>>();
    if pending_modes.is_empty() {
        return;
    }

    if supports.is_empty() {
        let support_samples = pending_modes
            .iter()
            .map(|(_, transform, pending_mode)| {
                (
                    pending_mode.0.target.clone(),
                    **transform,
                    character_dimensions_for_transform(transform),
                )
            })
            .collect::<Vec<_>>();
        spawn_character_stage_support(&mut commands, &support_samples);
    }

    for (entity, _transform, pending_mode) in pending_modes {
        insert_tnua_character_controller(
            &mut commands,
            entity,
            &pending_mode.0,
            *_transform,
            &mut character_configs,
        );
        commands.entity(entity).remove::<PendingCharacterMode>();
    }
}

pub(super) fn cleanup_character_supports(
    mut commands: Commands,
    supports: Query<Entity, With<SceneMaxStageSupport>>,
    character_owners: Query<
        &SceneMaxEntity,
        Or<(
            With<SceneMaxCharacterController>,
            With<PendingCharacterMode>,
        )>,
    >,
) {
    if character_owners.is_empty() {
        for support_entity in &supports {
            commands.entity(support_entity).despawn();
        }
    }
}

pub(super) fn clear_character_mode(commands: &mut Commands, entity: Entity) {
    let mut entity_commands = commands.entity(entity);
    entity_commands.remove::<SceneMaxCharacterController>();
    entity_commands.remove::<SceneMaxCharacterMotor>();
    entity_commands.remove::<PendingCharacterMode>();
    entity_commands.remove::<TnuaController<SceneMaxControlScheme>>();
    entity_commands.remove::<TnuaConfig<SceneMaxControlScheme>>();
    entity_commands.remove::<TnuaAvian3dSensorShape>();
    entity_commands.remove::<LockedAxes>();
    entity_commands.insert((
        AvianRigidBody::Kinematic,
        character_collision_layers(),
        LinearVelocity::ZERO,
        AngularVelocity::ZERO,
    ));
}

pub(super) fn spawn_character_stage_support(
    commands: &mut Commands,
    samples: &[(String, Transform, SceneMaxCharacterDimensions)],
) {
    let support_samples = preferred_stage_support_samples(samples);
    if support_samples.is_empty() {
        return;
    }

    let sample_count = support_samples.len() as f32;
    let center = support_samples
        .iter()
        .map(|(_, transform, _)| transform.translation)
        .fold(Vec3::ZERO, |sum, translation| sum + translation)
        / sample_count;
    let support_y = support_samples
        .iter()
        .map(|(_, transform, dimensions)| {
            character_stage_support_y(transform.translation.y, *dimensions)
        })
        .sum::<f32>()
        / sample_count;
    let spread = support_samples
        .iter()
        .map(|(_, transform, _)| {
            Vec2::new(
                transform.translation.x - center.x,
                transform.translation.z - center.z,
            )
            .length()
        })
        .fold(DEFAULT_STAGE_SUPPORT_HALF_SIZE, f32::max);
    let half_size = (spread + 80.0).max(DEFAULT_STAGE_SUPPORT_HALF_SIZE);

    commands.spawn((
        SceneMaxEntity {
            name: "__tnua_stage_support".to_owned(),
            runtime_name: "__tnua_stage_support@physics".to_owned(),
        },
        SceneMaxStageSupport { half_size },
        Transform::from_translation(Vec3::new(center.x, support_y, center.z)),
        Visibility::Hidden,
        AvianRigidBody::Static,
        AvianCollider::cuboid(half_size, 0.2, half_size),
        world_collision_layers(),
    ));
    tracing::info!(
        support_y,
        half_size,
        samples = support_samples.len(),
        "spawned coarse SceneMax character stage support"
    );
}

pub(super) fn character_stage_support_y(
    center_y: f32,
    dimensions: SceneMaxCharacterDimensions,
) -> f32 {
    center_y - dimensions.float_height - dimensions.foot_contact_offset
}

pub(super) fn update_scenemax_debug_gizmos(
    debug_mode: Res<SceneMaxDebugMode>,
    collider_bounds: Res<SceneMaxColliderBounds>,
    mut gizmos: Gizmos,
    mut debug_helpers: Query<
        &mut Visibility,
        Or<(With<SceneMaxVirtualCollider>, With<SceneMaxStageSupport>)>,
    >,
    scene_entities: Query<(Entity, &SceneMaxEntity, &Transform)>,
    stage_supports: Query<(&SceneMaxStageSupport, &Transform)>,
    characters: Query<(&SceneMaxEntity, &Transform, &SceneMaxCharacterController)>,
    virtual_colliders: Query<(), With<SceneMaxVirtualCollider>>,
) {
    let target_visibility = if debug_mode.enabled {
        Visibility::Inherited
    } else {
        Visibility::Hidden
    };
    for mut visibility in &mut debug_helpers {
        if *visibility != target_visibility {
            *visibility = target_visibility;
        }
    }

    if !debug_mode.enabled {
        return;
    }

    let collider_color = Color::srgb(0.2, 0.75, 1.0);
    let virtual_collider_color = Color::srgb(1.0, 0.78, 0.15);
    let support_color = Color::srgb(0.2, 1.0, 0.35);
    let character_color = Color::srgb(1.0, 0.25, 0.9);
    let float_color = Color::srgb(0.6, 1.0, 0.95);

    for (entity, scene_entity, transform) in &scene_entities {
        let Some(shape) = collider_bounds
            .shape_by_name
            .get(&scene_entity.name)
            .copied()
        else {
            continue;
        };
        let color = if virtual_colliders.get(entity).is_ok() {
            virtual_collider_color
        } else {
            collider_color
        };
        draw_debug_collider_shape(&mut gizmos, *transform, shape, color);
    }

    for (support, transform) in &stage_supports {
        let mut support_transform = *transform;
        support_transform.scale = Vec3::new(support.half_size * 2.0, 0.4, support.half_size * 2.0);
        gizmos.cube(support_transform, support_color);
    }

    for (_scene_entity, transform, controller) in &characters {
        let capsule_center =
            transform.translation + transform.rotation * (Vec3::Y * controller.capsule_center_y);
        draw_debug_capsule(
            &mut gizmos,
            capsule_center,
            transform.rotation,
            controller.capsule_radius,
            controller.capsule_height * 0.5,
            character_color,
        );

        let float_bottom = transform.translation - Vec3::Y * controller.float_height;
        gizmos.line(transform.translation, float_bottom, float_color);

        let mut sensor_transform = Transform::from_translation(float_bottom);
        sensor_transform.scale = Vec3::new(
            controller.capsule_radius * 2.0,
            DEFAULT_CHARACTER_SENSOR_HEIGHT,
            controller.capsule_radius * 2.0,
        );
        gizmos.cube(sensor_transform, float_color);
    }
}

fn draw_debug_collider_shape(
    gizmos: &mut Gizmos,
    transform: Transform,
    shape: ColliderBoundShape,
    color: Color,
) {
    match shape {
        ColliderBoundShape::Box { half_extents } => {
            let mut cube_transform = transform;
            cube_transform.scale = transform.scale * (half_extents * 2.0);
            gizmos.cube(cube_transform, color);
        }
        ColliderBoundShape::Sphere { radius } => {
            gizmos
                .sphere(
                    Isometry3d::new(transform.translation, transform.rotation),
                    radius,
                    color,
                )
                .resolution(24);
        }
        ColliderBoundShape::Capsule {
            radius,
            half_height,
        } => draw_debug_capsule(
            gizmos,
            transform.translation,
            transform.rotation,
            radius,
            half_height,
            color,
        ),
    }
}

fn draw_debug_capsule(
    gizmos: &mut Gizmos,
    center: Vec3,
    rotation: Quat,
    radius: f32,
    half_height: f32,
    color: Color,
) {
    let axis = rotation * Vec3::Y;
    let top = center + axis * half_height;
    let bottom = center - axis * half_height;
    gizmos
        .sphere(Isometry3d::new(top, rotation), radius, color)
        .resolution(18);
    gizmos
        .sphere(Isometry3d::new(bottom, rotation), radius, color)
        .resolution(18);

    for direction in [Vec3::X, -Vec3::X, Vec3::Z, -Vec3::Z] {
        let radial = rotation * direction * radius;
        gizmos.line(top + radial, bottom + radial, color);
    }
}

pub(super) fn preferred_stage_support_samples(
    samples: &[(String, Transform, SceneMaxCharacterDimensions)],
) -> Vec<(String, Transform, SceneMaxCharacterDimensions)> {
    let player_samples = samples
        .iter()
        .filter(|(name, _, _)| name.to_ascii_lowercase().starts_with("player"))
        .cloned()
        .collect::<Vec<_>>();
    if player_samples.is_empty() {
        samples.to_vec()
    } else {
        player_samples
    }
}

pub(super) fn update_avian_collision_contacts(
    mut starts: MessageReader<CollisionStart>,
    mut ends: MessageReader<CollisionEnd>,
    mut contacts: ResMut<SceneMaxPhysicsContacts>,
    scene_entities: Query<&SceneMaxEntity>,
) {
    for event in starts.read() {
        if let Some(pair) = collision_event_pair(
            event.collider1,
            event.body1,
            event.collider2,
            event.body2,
            &scene_entities,
        ) {
            contacts.active_pairs.insert(pair);
        }
    }

    for event in ends.read() {
        if let Some(pair) = collision_event_pair(
            event.collider1,
            event.body1,
            event.collider2,
            event.body2,
            &scene_entities,
        ) {
            contacts.active_pairs.remove(&pair);
        }
    }
}

pub(super) fn collision_event_pair(
    collider1: Entity,
    body1: Option<Entity>,
    collider2: Entity,
    body2: Option<Entity>,
    scene_entities: &Query<&SceneMaxEntity>,
) -> Option<(String, String)> {
    let left = collision_event_entity_name(collider1, body1, scene_entities)?;
    let right = collision_event_entity_name(collider2, body2, scene_entities)?;
    Some(normalized_collision_pair(&left, &right))
}

pub(super) fn collision_event_entity_name(
    collider: Entity,
    body: Option<Entity>,
    scene_entities: &Query<&SceneMaxEntity>,
) -> Option<String> {
    body.and_then(|body| scene_entities.get(body).ok())
        .or_else(|| scene_entities.get(collider).ok())
        .map(|entity| entity.name.clone())
}

pub(super) fn collision_condition_matches(
    sources: &[String],
    target: &str,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let Some(transforms_by_name) = transforms_by_name else {
        return false;
    };
    let target_exact = transforms_by_name.get(target).copied();
    let Some(target_transform) = target_exact
        .or_else(|| collision_owner_transform(target, transforms_by_name, collider_bounds))
    else {
        return false;
    };
    sources.iter().any(|source| {
        let source_exact = transforms_by_name.get(source).copied();
        if let (Some(source_transform), Some(target_transform)) = (source_exact, target_exact) {
            if !attached_collider_owner_distance_allows(
                source,
                target,
                transforms_by_name,
                collider_bounds,
            ) {
                return false;
            }
            if let Some(matches) = exact_collider_shapes_overlap(
                source,
                source_transform,
                target,
                target_transform,
                collider_bounds,
            ) {
                return matches;
            }
            return source_transform
                .translation
                .distance(target_transform.translation)
                <= exact_collision_threshold(source, target, collider_bounds);
        }
        collision_owner_transform(source, transforms_by_name, collider_bounds).is_some_and(
            |source_transform| {
                source_transform
                    .translation
                    .distance(target_transform.translation)
                    <= collision_threshold(source, target)
            },
        )
    })
}

pub(super) fn collision_owner_transform(
    reference: &str,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<Transform> {
    let owner = collision_owner_with_bounds(reference, collider_bounds);
    transforms_by_name
        .get(reference)
        .copied()
        .or_else(|| transforms_by_name.get(&owner).copied())
}

pub(super) fn collision_owner_distance(
    source: &str,
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> f32 {
    let source_owner = collision_owner_with_bounds(source, collider_bounds);
    let target_owner = collision_owner_with_bounds(target, collider_bounds);
    let (Some(source_transform), Some(target_transform)) = (
        transforms_by_name.get(&source_owner),
        transforms_by_name.get(&target_owner),
    ) else {
        return f32::INFINITY;
    };
    source_transform
        .translation
        .distance(target_transform.translation)
}

pub(super) fn attached_collider_owner_distance_allows(
    source: &str,
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let Some(collider_bounds) = collider_bounds else {
        return true;
    };
    let Some(source_owner) = collider_bounds.owner_by_name.get(source) else {
        return true;
    };
    let Some(target_owner) = collider_bounds.owner_by_name.get(target) else {
        return true;
    };
    if source_owner == target_owner {
        return true;
    }
    let owner_distance =
        collision_owner_distance(source, target, transforms_by_name, Some(collider_bounds));
    !owner_distance.is_finite() || owner_distance <= MAX_ATTACHED_COLLIDER_OWNER_DISTANCE
}

pub(super) fn collision_reference_candidates(reference: &str) -> Vec<String> {
    vec![reference.trim().trim_matches('"').to_owned()]
}

pub(super) fn collision_owner(reference: &str) -> String {
    let normalized = reference.trim().trim_matches('"');
    normalized
        .split(['.', '[', '"'])
        .next()
        .unwrap_or(normalized)
        .to_owned()
}

pub(super) fn collision_owner_with_bounds(
    reference: &str,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> String {
    let normalized = reference.trim().trim_matches('"');
    collider_bounds
        .and_then(|bounds| bounds.owner_by_name.get(normalized))
        .cloned()
        .unwrap_or_else(|| collision_owner(reference))
}

pub(super) fn collision_threshold(source: &str, target: &str) -> f32 {
    let _ = (source, target);
    2.5
}

pub(super) fn exact_collider_shapes_overlap(
    source: &str,
    source_transform: Transform,
    target: &str,
    target_transform: Transform,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<bool> {
    let collider_bounds = collider_bounds?;
    let source_shape = collider_bounds.shape_by_name.get(source).copied()?;
    let target_shape = collider_bounds.shape_by_name.get(target).copied()?;
    Some(collider_shapes_overlap(
        source_shape,
        source_transform,
        target_shape,
        target_transform,
    ))
}

pub(super) fn collider_shapes_overlap(
    source_shape: ColliderBoundShape,
    source_transform: Transform,
    target_shape: ColliderBoundShape,
    target_transform: Transform,
) -> bool {
    match (source_shape, target_shape) {
        (
            ColliderBoundShape::Sphere {
                radius: source_radius,
            },
            ColliderBoundShape::Sphere {
                radius: target_radius,
            },
        ) => {
            source_transform
                .translation
                .distance(target_transform.translation)
                <= source_radius + target_radius
        }
        (ColliderBoundShape::Box { half_extents }, ColliderBoundShape::Sphere { radius }) => {
            sphere_overlaps_box(
                target_transform.translation,
                radius,
                source_transform,
                half_extents,
            )
        }
        (ColliderBoundShape::Sphere { radius }, ColliderBoundShape::Box { half_extents }) => {
            sphere_overlaps_box(
                source_transform.translation,
                radius,
                target_transform,
                half_extents,
            )
        }
        _ => {
            source_transform
                .translation
                .distance(target_transform.translation)
                <= source_shape.bounding_radius() + target_shape.bounding_radius()
        }
    }
}

pub(super) fn sphere_overlaps_box(
    sphere_center: Vec3,
    sphere_radius: f32,
    box_transform: Transform,
    half_extents: Vec3,
) -> bool {
    let local_center = box_transform
        .rotation
        .inverse()
        .mul_vec3(sphere_center - box_transform.translation);
    let closest = local_center.clamp(-half_extents, half_extents);
    local_center.distance_squared(closest) <= sphere_radius * sphere_radius
}

pub(super) fn exact_collision_threshold(
    source: &str,
    target: &str,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> f32 {
    collider_radius(source, collider_bounds) + collider_radius(target, collider_bounds)
}

pub(super) fn collider_radius(
    reference: &str,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> f32 {
    collider_bounds
        .and_then(|bounds| bounds.radius_by_name.get(reference).copied())
        .unwrap_or_else(|| collision_part_radius(reference))
}

pub(super) fn collision_part_radius(reference: &str) -> f32 {
    let lower = reference.to_ascii_lowercase();
    if lower.contains("body") {
        0.85
    } else if lower.contains("head") {
        0.65
    } else if lower.contains("hand") || lower.contains("foot") {
        0.55
    } else {
        1.25
    }
}

pub(super) fn coordinate_value_from_name(
    name: &str,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<f32> {
    let (entity, axis) = name.rsplit_once('.')?;
    let transform = transforms_by_name.get(entity)?;
    match axis.to_ascii_lowercase().as_str() {
        "x" => Some(transform.translation.x),
        "y" => Some(transform.translation.y),
        "z" => Some(transform.translation.z),
        _ => None,
    }
}

pub(super) fn transform_from_options(
    options: &EntityOptions,
    asset_scale: Option<[f32; 3]>,
) -> Transform {
    let translation = options
        .position
        .map(vec3_from_scenemax)
        .unwrap_or(Vec3::ZERO);
    let asset_scale = asset_scale
        .map(|scale| Vec3::new(scale[0], scale[1], scale[2]))
        .unwrap_or(Vec3::ONE);
    let scale = options.scale.map(vec3_from_scenemax).unwrap_or(asset_scale);
    let rotation = options
        .rotation_degrees
        .map(rotation_from_degrees)
        .unwrap_or(Quat::IDENTITY);

    Transform {
        translation,
        rotation,
        scale,
    }
}

pub(super) fn transform_from_options_resolved(
    options: &EntityOptions,
    asset_scale: Option<[f32; 3]>,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Transform {
    let mut transform = transform_from_options(options, asset_scale);
    if let Some(position) = options.position_value.as_ref().and_then(|position| {
        resolve_position_value_runtime(
            position,
            vars,
            None,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        )
    }) {
        transform.translation = position;
    }
    transform
}

pub(super) fn timed_turn_from_statement(turn: &scenemax_parser::TurnStatement) -> TimedTurn {
    timed_turn_from_statement_resolved(turn, turn.degrees, turn.duration_seconds)
}

pub(super) fn timed_turn_from_statement_resolved(
    turn: &scenemax_parser::TurnStatement,
    degrees: f32,
    duration_seconds: f32,
) -> TimedTurn {
    let duration = duration_seconds.max(0.001);
    TimedTurn {
        remaining_seconds: duration,
        duration_seconds: duration,
        radians_per_second: degrees.to_radians() / duration,
        loop_condition: turn.loop_condition.clone(),
    }
}

#[cfg(test)]
pub(super) fn timed_move_from_statement(
    movement: &scenemax_parser::MoveStatement,
    transform: &Transform,
) -> TimedMove {
    timed_move_from_statement_resolved(
        movement,
        transform,
        movement.distance,
        movement.duration_seconds,
    )
}

pub(super) fn timed_move_from_statement_resolved(
    movement: &scenemax_parser::MoveStatement,
    transform: &Transform,
    distance: f32,
    duration_seconds: f32,
) -> TimedMove {
    let duration = duration_seconds.max(0.001);
    let direction = movement_direction_vector(movement.direction, transform);

    TimedMove {
        remaining_seconds: duration,
        duration_seconds: duration,
        velocity: direction * directional_move_speed_resolved(distance, duration_seconds),
        final_translation: None,
        loop_condition: movement.loop_condition.clone(),
    }
}

#[cfg(test)]
pub(super) fn directional_move_speed(movement: &scenemax_parser::MoveStatement) -> f32 {
    directional_move_speed_resolved(movement.distance, movement.duration_seconds)
}

pub(super) fn directional_move_speed_resolved(distance: f32, duration_seconds: f32) -> f32 {
    if duration_seconds > 0.0 {
        distance / duration_seconds.max(0.001)
    } else {
        distance / 0.001
    }
}

pub(super) fn append_timed_move(commands: &mut Commands, entity: Entity, timed_move: TimedMove) {
    commands.queue(move |world: &mut World| {
        let Ok(mut entity_mut) = world.get_entity_mut(entity) else {
            return;
        };
        let mut timed_move = Some(timed_move);
        if let Some(mut moves) = entity_mut.get_mut::<TimedMoves>() {
            moves.moves.push(timed_move.take().unwrap());
        } else {
            entity_mut.insert(TimedMoves {
                moves: vec![timed_move.take().unwrap()],
            });
        }
    });
}

#[cfg(test)]
pub(super) fn timed_jump_from_statement(
    jump: &CharacterJumpStatement,
    transform: &Transform,
) -> TimedJump {
    timed_jump_from_statement_resolved(jump, transform, jump.speed)
}

pub(super) fn timed_jump_from_statement_resolved(
    _jump: &CharacterJumpStatement,
    transform: &Transform,
    speed: f32,
) -> TimedJump {
    TimedJump {
        elapsed_seconds: 0.0,
        duration_seconds: jump_duration_seconds(speed),
        start_y: transform.translation.y,
        height: jump_height(speed),
    }
}

pub(super) fn apply_physics_impulse_resolved(
    commands: &mut Commands,
    entity: Entity,
    transform: &Transform,
    impulse: &scenemax_parser::PhysicsImpulseStatement,
    strength: f32,
) {
    let direction = physics_direction_vector(impulse.direction, transform);
    commands
        .entity(entity)
        .insert(LinearVelocity(direction * strength));
}

pub(super) fn apply_physics_stop(commands: &mut Commands, entity: Entity) {
    commands
        .entity(entity)
        .insert((LinearVelocity::ZERO, AngularVelocity::ZERO));
}

pub(super) fn apply_physics_throw_at(
    commands: &mut Commands,
    entity: Entity,
    transform: &Transform,
    throw_at: &scenemax_parser::PhysicsThrowAtStatement,
    vars: &SceneMaxVars,
    transforms_by_name: &HashMap<String, Transform>,
) {
    let Some(target_transform) = lookup_subject_transform(&throw_at.subject, transforms_by_name)
    else {
        return;
    };
    let Some(power) = resolve_assignment_value(&throw_at.power, vars, Some(transforms_by_name))
    else {
        return;
    };
    let mut direction = target_transform.translation - transform.translation;
    if direction.length_squared() <= f32::EPSILON {
        return;
    }
    direction = direction.normalize();
    commands
        .entity(entity)
        .insert(LinearVelocity(direction * power));
}

pub(super) fn physics_direction_vector(
    direction: scenemax_parser::PhysicsDirection,
    transform: &Transform,
) -> Vec3 {
    match direction {
        scenemax_parser::PhysicsDirection::Up => Vec3::Y,
        scenemax_parser::PhysicsDirection::Down => -Vec3::Y,
        scenemax_parser::PhysicsDirection::Forward => horizontal_forward(transform),
        scenemax_parser::PhysicsDirection::Backward => -horizontal_forward(transform),
        scenemax_parser::PhysicsDirection::Left => -horizontal_right(transform),
        scenemax_parser::PhysicsDirection::Right => horizontal_right(transform),
    }
}

pub(super) fn set_character_move_intent_resolved(
    motor: &mut SceneMaxCharacterMotor,
    controller: &SceneMaxCharacterController,
    movement: &scenemax_parser::MoveStatement,
    transform: &Transform,
    distance: f32,
    duration_seconds: f32,
    continuous_delta_seconds: Option<f32>,
) {
    let duration = duration_seconds.max(0.001);
    let speed = character_directional_move_speed_resolved(
        distance,
        duration_seconds,
        continuous_delta_seconds,
    );
    let direction = movement_direction_vector(movement.direction, transform);
    let speed_ratio = speed / controller.move_speed.max(0.001);

    if continuous_delta_seconds.is_some() {
        set_character_motion(motor, direction, speed_ratio, CHARACTER_INPUT_TTL_SECONDS);
    } else {
        motor.timed_motion = direction.normalize_or_zero() * speed_ratio;
        motor.timed_motion_remaining_seconds = duration;
    }
}

#[cfg(test)]
pub(super) fn character_directional_move_speed(
    movement: &scenemax_parser::MoveStatement,
    continuous_delta_seconds: Option<f32>,
) -> f32 {
    character_directional_move_speed_resolved(
        movement.distance,
        movement.duration_seconds,
        continuous_delta_seconds,
    )
}

pub(super) fn character_directional_move_speed_resolved(
    distance: f32,
    duration_seconds: f32,
    continuous_delta_seconds: Option<f32>,
) -> f32 {
    if let Some(delta_seconds) = continuous_delta_seconds {
        distance / delta_seconds.max(0.001)
    } else {
        directional_move_speed_resolved(distance, duration_seconds)
    }
}

pub(super) fn movement_direction_vector(direction: MoveDirection, transform: &Transform) -> Vec3 {
    match direction {
        MoveDirection::Forward => horizontal_forward(transform),
        MoveDirection::Backward => -horizontal_forward(transform),
        MoveDirection::Left => horizontal_right(transform),
        MoveDirection::Right => -horizontal_right(transform),
        MoveDirection::Up => Vec3::Y,
        MoveDirection::Down => -Vec3::Y,
    }
}

pub(super) fn set_character_motion(
    motor: &mut SceneMaxCharacterMotor,
    direction: Vec3,
    speed_ratio: f32,
    ttl_seconds: f32,
) {
    motor.desired_motion = direction.normalize_or_zero() * speed_ratio;
    motor.motion_ttl_seconds = ttl_seconds;
}

pub(super) fn set_character_jump_intent_resolved(motor: &mut SceneMaxCharacterMotor, speed: f32) {
    motor.pending_jump_speed = Some(speed);
    motor.jump_hold_seconds = motor
        .jump_hold_seconds
        .max(character_jump_feed_seconds(speed));
}

pub(super) fn jump_height(speed: f32) -> f32 {
    (speed * 0.16).clamp(1.2, 8.0)
}

pub(super) fn jump_duration_seconds(speed: f32) -> f32 {
    (speed * 0.025).clamp(0.45, 1.15)
}

pub(super) fn character_jump_feed_seconds(speed: f32) -> f32 {
    jump_duration_seconds(speed).max(CHARACTER_JUMP_FEED_SECONDS)
}

pub(super) fn jump_y_offset(progress: f32, height: f32) -> f32 {
    let progress = progress.clamp(0.0, 1.0);
    4.0 * height * progress * (1.0 - progress)
}

pub(super) fn horizontal_forward(transform: &Transform) -> Vec3 {
    let mut direction = transform.rotation * Vec3::Z;
    direction.y = 0.0;
    if direction.length_squared() <= f32::EPSILON {
        return Vec3::Z;
    }
    direction.normalize()
}

pub(super) fn horizontal_right(transform: &Transform) -> Vec3 {
    let mut direction = transform.rotation * Vec3::X;
    direction.y = 0.0;
    if direction.length_squared() <= f32::EPSILON {
        return Vec3::X;
    }
    direction.normalize()
}

#[cfg(test)]
pub(super) fn evaluate_position_statement(
    position: &scenemax_parser::PositionStatement,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Vec3> {
    evaluate_position_value(&position.position, transforms_by_name)
}

#[cfg(test)]
pub(super) fn evaluate_position_value(
    position: &PositionValue,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Vec3> {
    match position {
        PositionValue::Entity(entity) => {
            Some(lookup_subject_transform(entity, transforms_by_name)?.translation)
        }
        PositionValue::Coordinates(values) if values.len() == 3 => Some(Vec3::new(
            evaluate_position_expr(&values[0], transforms_by_name)?,
            evaluate_position_expr(&values[1], transforms_by_name)?,
            evaluate_position_expr(&values[2], transforms_by_name)?,
        )),
        _ => None,
    }
}

pub(super) fn resolve_position_value_runtime(
    position: &PositionValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<Vec3> {
    match position {
        PositionValue::Entity(entity) => {
            Some(lookup_subject_transform(entity, transforms_by_name?)?.translation)
        }
        PositionValue::Coordinates(values) if values.len() == 3 => Some(Vec3::new(
            resolve_position_expr_runtime(
                &values[0],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
            resolve_position_expr_runtime(
                &values[1],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
            resolve_position_expr_runtime(
                &values[2],
                vars,
                scope,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
            )?,
        )),
        _ => None,
    }
}

pub(super) fn resolve_position_expr_runtime(
    value: &PositionExpr,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    match value {
        PositionExpr::Number(value) => Some(*value),
        PositionExpr::Value(value) => resolve_assignment_value_scoped_with_guards(
            value,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
        PositionExpr::EntityAxis {
            entity,
            axis,
            offset,
        } => {
            let transform = transforms_by_name?.get(entity)?;
            let base = match axis {
                SceneMaxAxis::X => transform.translation.x,
                SceneMaxAxis::Y => transform.translation.y,
                SceneMaxAxis::Z => transform.translation.z,
            };
            Some(base + offset)
        }
    }
}

#[cfg(test)]
pub(super) fn evaluate_position_expr(
    value: &PositionExpr,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<f32> {
    match value {
        PositionExpr::Number(value) => Some(*value),
        PositionExpr::Value(_) => None,
        PositionExpr::EntityAxis {
            entity,
            axis,
            offset,
        } => {
            let transform = transforms_by_name.get(entity)?;
            let base = match axis {
                SceneMaxAxis::X => transform.translation.x,
                SceneMaxAxis::Y => transform.translation.y,
                SceneMaxAxis::Z => transform.translation.z,
            };
            Some(base + offset)
        }
    }
}

pub(super) fn lookup_subject_transform(
    subject: &str,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Transform> {
    if let Some(transform) = transforms_by_name.get(subject).copied() {
        return Some(transform);
    }
    let name = subject.split_whitespace().next()?;
    transforms_by_name.get(name).copied()
}

pub(super) fn look_at_scenemax_forward(transform: &mut Transform, target_translation: Vec3) {
    let mut direction = target_translation - transform.translation;
    direction.y = 0.0;
    if direction.length_squared() <= f32::EPSILON {
        return;
    }
    let direction = direction.normalize();
    transform.rotation = Quat::from_rotation_y(direction.x.atan2(direction.z));
}

pub(super) fn update_timed_turns(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    vars: Res<SceneMaxVars>,
    collider_bounds: Res<SceneMaxColliderBounds>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(Entity, &mut Transform, &mut TimedTurn)>,
    )>,
) {
    let transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    let guards_by_name = startup_program
        .0
        .as_ref()
        .map(collect_guards_by_name)
        .unwrap_or_default();

    for (entity, mut transform, mut turn) in &mut scene_entities.p1() {
        let delta = time.delta_secs().min(turn.remaining_seconds);
        transform.rotate_y(turn.radians_per_second * delta);
        turn.remaining_seconds -= delta;
        if turn.remaining_seconds <= 0.0 {
            if turn.loop_condition.as_ref().is_some_and(|condition| {
                condition_matches(
                    condition,
                    &vars,
                    &guards_by_name,
                    Some(&transforms_by_name),
                    Some(&collider_bounds),
                )
            }) {
                turn.remaining_seconds = turn.duration_seconds;
                continue;
            }
            commands.entity(entity).remove::<TimedTurn>();
        }
    }
}

pub(super) fn update_timed_moves(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    vars: Res<SceneMaxVars>,
    collider_bounds: Res<SceneMaxColliderBounds>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(Entity, &mut Transform, &mut TimedMoves)>,
    )>,
) {
    let transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    let guards_by_name = startup_program
        .0
        .as_ref()
        .map(collect_guards_by_name)
        .unwrap_or_default();

    for (entity, mut transform, mut movements) in &mut scene_entities.p1() {
        let mut active_moves = Vec::with_capacity(movements.moves.len());
        for mut movement in movements.moves.drain(..) {
            let delta = time.delta_secs().min(movement.remaining_seconds);
            transform.translation += movement.velocity * delta;
            movement.remaining_seconds -= delta;
            if movement.remaining_seconds <= 0.0 {
                if let Some(final_translation) = movement.final_translation {
                    transform.translation = final_translation;
                }
                if movement.loop_condition.as_ref().is_some_and(|condition| {
                    condition_matches(
                        condition,
                        &vars,
                        &guards_by_name,
                        Some(&transforms_by_name),
                        Some(&collider_bounds),
                    )
                }) {
                    movement.remaining_seconds = movement.duration_seconds;
                    movement.final_translation = None;
                    active_moves.push(movement);
                }
            } else {
                active_moves.push(movement);
            }
        }
        movements.moves = active_moves;
        if movements.moves.is_empty() {
            commands.entity(entity).remove::<TimedMoves>();
        }
    }
}

pub(super) fn update_timed_jumps(
    time: Res<Time>,
    mut commands: Commands,
    mut jumps: Query<(Entity, &mut Transform, &mut TimedJump)>,
) {
    for (entity, mut transform, mut jump) in &mut jumps {
        jump.elapsed_seconds += time.delta_secs();
        let progress = jump.elapsed_seconds / jump.duration_seconds.max(0.001);
        transform.translation.y = jump.start_y + jump_y_offset(progress, jump.height);
        if progress >= 1.0 {
            transform.translation.y = jump.start_y;
            commands.entity(entity).remove::<TimedJump>();
        }
    }
}

pub(super) fn feed_tnua_character_controllers(
    time: Res<Time>,
    mut character_configs: ResMut<Assets<SceneMaxControlSchemeConfig>>,
    mut characters: Query<(
        &SceneMaxCharacterController,
        &mut SceneMaxCharacterMotor,
        &TnuaConfig<SceneMaxControlScheme>,
        &mut TnuaController<SceneMaxControlScheme>,
    )>,
) {
    let delta = time.delta_secs();
    for (settings, mut motor, config_handle, mut controller) in &mut characters {
        controller.initiate_action_feeding();

        let mut desired_motion = Vec3::ZERO;
        if motor.timed_motion_remaining_seconds > 0.0 {
            desired_motion += motor.timed_motion;
            motor.timed_motion_remaining_seconds =
                (motor.timed_motion_remaining_seconds - delta).max(0.0);
        }
        if motor.motion_ttl_seconds > 0.0 {
            desired_motion += motor.desired_motion;
            motor.motion_ttl_seconds = (motor.motion_ttl_seconds - delta).max(0.0);
        }

        controller.basis = TnuaBuiltinWalk {
            desired_motion,
            desired_forward: None,
        };

        if motor.jump_hold_seconds > 0.0 {
            if let Some(speed) = motor.pending_jump_speed.take() {
                if let Some(mut config) = character_configs.get_mut(&config_handle.0) {
                    config.jump.height = jump_height(speed);
                }
            }
            controller.action(SceneMaxControlScheme::Jump(TnuaBuiltinJump {
                allow_in_air: true,
                ..Default::default()
            }));
            motor.jump_hold_seconds = (motor.jump_hold_seconds - delta).max(0.0);
        }

        let _ = settings.gravity;
    }
}

pub(super) fn vec3_from_scenemax(value: SceneMaxVec3) -> Vec3 {
    Vec3::new(value.x, value.y, value.z)
}

pub(super) fn rotation_from_degrees(value: SceneMaxVec3) -> Quat {
    Quat::from_euler(
        EulerRot::XYZ,
        value.x.to_radians(),
        value.y.to_radians(),
        value.z.to_radians(),
    )
}

#[cfg(test)]
mod material_tests {
    use super::*;

    #[test]
    fn parses_j3m_texture_maps_for_bevy_materials() {
        let material = parse_j3m_material(
            r#"
Material wall : Common/MatDefs/Light/Lighting.j3md {
    MaterialParameters {
        Diffuse : 0.78 0.8 0.84 1.0
        GlowColor : 0.1 0.2 0.3 1.0
        DiffuseMap : material/wall/diffuse_diffuse.png
        NormalMap : material/wall/normal_normal.png
        GlowMap : material/wall/glow_glow.png
    }
}
"#,
            "",
        );

        assert_eq!(
            material.diffuse_map.as_deref(),
            Some("material/wall/diffuse_diffuse.png")
        );
        assert_eq!(
            material.normal_map.as_deref(),
            Some("material/wall/normal_normal.png")
        );
        assert_eq!(
            material.glow_map.as_deref(),
            Some("material/wall/glow_glow.png")
        );
        assert!(material.diffuse.is_some());
        assert!(material.glow_color.is_some());
    }

    #[test]
    fn quad_mesh_origin_matches_jme_quad() {
        let mesh = quad_mesh(2.0, 3.0);
        let positions = mesh.attribute(Mesh::ATTRIBUTE_POSITION).unwrap();

        let bevy::mesh::VertexAttributeValues::Float32x3(positions) = positions else {
            panic!("quad mesh positions should be Float32x3");
        };

        assert_eq!(
            positions,
            &vec![
                [0.0, 0.0, 0.0],
                [2.0, 0.0, 0.0],
                [2.0, 3.0, 0.0],
                [0.0, 3.0, 0.0],
            ]
        );
    }

    #[test]
    fn model_script_scale_overrides_asset_scale() {
        let transform = transform_from_options(
            &EntityOptions {
                scale: Some(SceneMaxVec3 {
                    x: 2.0,
                    y: 2.0,
                    z: 2.0,
                }),
                ..Default::default()
            },
            Some([0.02, 0.02, 0.02]),
        );

        assert_eq!(transform.scale, Vec3::splat(2.0));
    }

    #[test]
    fn model_asset_scale_applies_when_script_scale_is_absent() {
        let transform = transform_from_options(
            &EntityOptions {
                scale: None,
                ..Default::default()
            },
            Some([0.02, 0.02, 0.02]),
        );

        assert_eq!(transform.scale, Vec3::splat(0.02));
    }

    #[test]
    fn primitive_resource_detection_stays_generic() {
        assert!(is_primitive_resource("box"));
        assert!(is_primitive_resource("sphere"));
        assert!(!is_primitive_resource("bone"));
    }
}
