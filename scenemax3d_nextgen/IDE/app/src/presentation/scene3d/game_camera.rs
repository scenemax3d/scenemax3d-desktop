//! Java game-camera authoring and an embedded retained preview viewport.
use super::*;
use bevy::camera::visibility::RenderLayers;
#[derive(Component)]
pub(crate) struct Preview(pub Entity);
#[derive(Component)]
pub(crate) struct Inset;
#[derive(Component)]
pub(crate) struct CameraVisual;
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
    // Triangle geometry provides a selectable camera body; wire edges show its direction.
    commands.spawn((
        Mesh3d(meshes.add(Cuboid::new(1.4, 1., 1.))),
        MeshMaterial3d(material.clone()),
        RenderLayers::layer(1),
        CameraVisual,
        ChildOf(owner),
    ));
    let corners = [
        Vec3::new(-2., -1.125, 4.),
        Vec3::new(2., -1.125, 4.),
        Vec3::new(2., 1.125, 4.),
        Vec3::new(-2., 1.125, 4.),
    ];
    for i in 0..4 {
        for (a, b) in [(Vec3::ZERO, corners[i]), (corners[i], corners[(i + 1) % 4])] {
            let delta = b - a;
            commands.spawn((
                Mesh3d(meshes.add(Cylinder::new(0.018, delta.length()))),
                MeshMaterial3d(material.clone()),
                Transform::from_translation((a + b) * 0.5)
                    .with_rotation(Quat::from_rotation_arc(Vec3::Y, delta.normalize())),
                RenderLayers::layer(1),
                CameraVisual,
                ChildOf(owner),
            ));
        }
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
        let color = LinearRgba::new(1., 0.65, 0.1, 1.);
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
