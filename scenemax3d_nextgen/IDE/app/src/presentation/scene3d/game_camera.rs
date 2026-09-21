//! Java game-camera authoring and an embedded retained preview viewport.
use super::*;
use bevy::camera::visibility::RenderLayers;
#[derive(Component)]
pub(crate) struct Preview(pub Entity);
#[derive(Component)]
pub(crate) struct Inset;
#[derive(Component)]
pub(crate) struct CameraVisual;
#[derive(Component)]
pub(crate) struct CameraMarker;
pub(super) fn spawn(
    commands: &mut Commands,
    root: Entity,
    owner: Entity,
    images: &mut Assets<Image>,
    meshes: &mut Assets<Mesh>,
    materials: &mut Assets<StandardMaterial>,
    ambient: &serde_json::Value,
) {
    let mut image = Image::new_target_texture(320, 180, TextureFormat::Bgra8UnormSrgb, None);
    image.texture_descriptor.usage |= TextureUsages::TEXTURE_BINDING;
    commands.spawn((
        Camera3d::default(),
        Camera {
            order: -2,
            ..default()
        },
        Projection::Perspective(PerspectiveProjection {
            fov: 45_f32.to_radians(),
            aspect_ratio: 16. / 9.,
            near: 0.1,
            far: 1000.,
            ..default()
        }),
        RenderTarget::Image(images.add(image).into()),
        Preview(owner),
        super::ambient::light(ambient),
        Transform::default(),
        ChildOf(root),
    ));
    let material = materials.add(StandardMaterial {
        base_color: Color::srgb(1., 0.75, 0.15),
        unlit: true,
        ..default()
    });
    // An editor-only scale pivot keeps the camera pose and preview untouched.
    let marker = commands
        .spawn((
            CameraMarker,
            Transform::from_scale(Vec3::splat(0.15)),
            Visibility::Inherited,
            ChildOf(owner),
        ))
        .id();
    let corners = marker_points();
    let mut edges = Vec::new();
    for i in 0..4 {
        edges.extend([(Vec3::ZERO, corners[i]), (corners[i], corners[(i + 1) % 4])]);
    }
    // Blender-style up triangle makes orientation readable without a filled body.
    edges.extend([
        (corners[4], corners[5]),
        (corners[5], corners[6]),
        (corners[6], corners[4]),
    ]);
    for (a, b) in edges {
        let delta = b - a;
        commands.spawn((
            Mesh3d(meshes.add(Cylinder::new(1., delta.length()))),
            MeshMaterial3d(material.clone()),
            Transform::from_translation((a + b) * 0.5)
                .with_rotation(Quat::from_rotation_arc(Vec3::Y, delta.normalize()))
                .with_scale(Vec3::new(0.015, 1., 0.015)),
            RenderLayers::layer(1),
            CameraVisual,
            ChildOf(marker),
        ));
    }
}
pub(crate) fn synchronize(
    mut commands: Commands,
    objects: Query<&Transform, Without<Preview>>,
    mut cameras: Query<(Entity, &Preview, &mut Transform)>,
    insets: Query<Entity, Added<Inset>>,
    visuals: Query<Entity, Added<CameraVisual>>,
    mut materials: Option<ResMut<Assets<gizmo::GizmoMaterial>>>,
) {
    if let Some(materials) = materials.as_mut() {
        let color = LinearRgba::new(0.68, 0.72, 0.78, 1.);
        for entity in &visuals {
            commands
                .entity(entity)
                .remove::<MeshMaterial3d<StandardMaterial>>()
                .insert(MeshMaterial3d(materials.add(gizmo::GizmoMaterial {
                    base_color: color,
                    emissive: color,
                })));
        }
    }
    for (camera, preview, mut transform) in &mut cameras {
        if let Ok(source) = objects.get(preview.0) {
            // JME cameras look along local +Z; Bevy cameras look along -Z.
            let next = Transform::from_translation(source.translation)
                .with_rotation(source.rotation * Quat::from_rotation_y(std::f32::consts::PI));
            if *transform != next {
                *transform = next;
            }
        }
        for inset in &insets {
            commands.entity(inset).insert(ViewportNode::new(camera));
        }
    }
}
fn marker_points() -> [Vec3; 7] {
    [
        Vec3::new(-0.55, -0.31, 1.1),
        Vec3::new(0.55, -0.31, 1.1),
        Vec3::new(0.55, 0.31, 1.1),
        Vec3::new(-0.55, 0.31, 1.1),
        Vec3::new(-0.2, 0.35, 1.1),
        Vec3::new(0.2, 0.35, 1.1),
        Vec3::new(0., 0.65, 1.1),
    ]
}
fn units_per_pixel(projection: &Projection, depth: f32, height: f32) -> f32 {
    match projection {
        Projection::Perspective(p) => 2. * depth.max(p.near) * (p.fov * 0.5).tan() / height.max(1.),
        Projection::Orthographic(p) => p.area.height() / height.max(1.),
        _ => 0.01,
    }
    .max(0.000001)
}
// Retain a natural world-size display, with a readability floor and a zoom ceiling.
fn marker_scale(pixel: f32, orientation: Quat) -> f32 {
    let mut min = Vec2::ZERO;
    let mut max = Vec2::ZERO;
    for point in marker_points() {
        let point = (orientation * point).truncate();
        min = min.min(point);
        max = max.max(point);
    }
    let span = (max - min).max_element().max(0.1);
    1_f32.clamp(pixel * 80. / span, pixel * 140. / span)
}
type Markers<'w, 's> = Query<
    'w,
    's,
    (Entity, &'static ChildOf, &'static mut Transform),
    (With<CameraMarker>, Without<CameraVisual>),
>;
type Edges<'w, 's> = Query<
    'w,
    's,
    (
        &'static ChildOf,
        &'static mut Transform,
        &'static MeshMaterial3d<gizmo::GizmoMaterial>,
    ),
    (With<CameraVisual>, Without<CameraMarker>),
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct MarkerView<'w, 's> {
    cameras: Query<'w, 's, (&'static GlobalTransform, &'static Projection), With<Camera3d>>,
    ports: Query<'w, 's, (&'static gizmo::Viewport, &'static ComputedNode)>,
    globals: Query<'w, 's, &'static GlobalTransform>,
    markers: Markers<'w, 's>,
    edges: Edges<'w, 's>,
    objects: Query<'w, 's, &'static gizmo::SceneObject>,
    materials: Option<ResMut<'w, Assets<gizmo::GizmoMaterial>>>,
}
pub(crate) fn size_marker(state: Res<SceneState>, mut view: MarkerView) {
    let Some(camera) = state.camera else {
        return;
    };
    let Ok((camera_view, projection)) = view.cameras.get(camera) else {
        return;
    };
    let Some((_, node)) = view.ports.iter().find(|(port, _)| port.0 == camera) else {
        return;
    };
    let height = node.size().y * node.inverse_scale_factor();
    for (marker, parent, mut transform) in &mut view.markers {
        let Ok(owner) = view.globals.get(parent.parent()) else {
            continue;
        };
        let depth = (owner.translation() - camera_view.translation()).dot(*camera_view.forward());
        let parent_scale = owner.to_scale_rotation_translation().0.abs();
        if parent_scale.min_element() <= 1e-8 {
            continue;
        }
        let pixel = units_per_pixel(projection, depth, height);
        let size = marker_scale(pixel, camera_view.rotation().inverse() * owner.rotation());
        transform.scale = Vec3::splat(size) / parent_scale;
        let selected = view
            .objects
            .get(parent.parent())
            .is_ok_and(|o| o.0 == state.selected);
        let color = if selected {
            LinearRgba::new(1., 0.45, 0.06, 1.)
        } else {
            LinearRgba::new(0.68, 0.72, 0.78, 1.)
        };
        for (edge_parent, mut edge, material) in &mut view.edges {
            if edge_parent.parent() != marker {
                continue;
            }
            // Keep strokes readable independently of zoom and the display size.
            let radius = pixel * if selected { 1. } else { 0.75 } / size;
            edge.scale.x = radius;
            edge.scale.z = radius;
            if let Some(mut material) = view.materials.as_mut().and_then(|m| m.get_mut(&material.0))
            {
                material.base_color = color;
                material.emissive = color;
            }
        }
    }
}

pub(super) fn inset(commands: &mut Commands, parent: Entity) {
    let frame = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                right: px(10.),
                bottom: px(10.),
                width: px(256.),
                max_width: percent(45.),
                border: px(1.).all(),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BorderColor::all(EDGE),
            BackgroundColor(PANEL),
            ZIndex(6),
            ChildOf(parent),
        ))
        .id();
    commands.spawn((label("Game camera", 11.), ChildOf(frame)));
    commands.spawn((
        Inset,
        Node {
            width: percent(100.),
            aspect_ratio: Some(16. / 9.),
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(frame),
    ));
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn preview_matches_java_positive_z_camera_direction() {
        let mut app = App::new();
        app.add_systems(Update, synchronize);
        let source = Transform::from_xyz(1., 2., 3.).with_rotation(Quat::from_rotation_y(0.7));
        let owner = app.world_mut().spawn(source).id();
        let camera = app
            .world_mut()
            .spawn((Preview(owner), Transform::default()))
            .id();
        app.update();
        let result = app.world().get::<Transform>(camera).unwrap();
        assert_eq!(result.translation, source.translation);
        assert!(Vec3::from(result.forward()).abs_diff_eq(source.rotation * Vec3::Z, 0.0001));
        app.world_mut()
            .get_mut::<Transform>(owner)
            .unwrap()
            .translation
            .x = 15.;
        app.update();
        assert_eq!(
            app.world().get::<Transform>(camera).unwrap().translation.x,
            15.
        );
    }
}
