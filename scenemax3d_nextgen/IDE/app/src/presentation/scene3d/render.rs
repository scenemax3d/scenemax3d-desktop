//! Owned 3D world and offscreen camera construction.
use super::*;
#[derive(Component)]
pub(crate) struct DesignLight;
pub(super) fn spawn_world(
    commands: &mut Commands,
    images: &mut Assets<Image>,
    meshes: &mut Assets<Mesh>,
    materials: &mut Assets<StandardMaterial>,
    server: &AssetServer,
    scene: &Scene3d,
    project_assets: &crate::project_assets::ProjectAssets,
) -> (Entity, Entity) {
    let root = commands
        .spawn((Transform::default(), Visibility::default()))
        .id();
    let mut image = Image::new_uninit(
        bevy::render::render_resource::Extent3d {
            width: 640,
            height: 480,
            depth_or_array_layers: 1,
        },
        TextureDimension::D2,
        TextureFormat::Bgra8UnormSrgb,
        RenderAssetUsages::all(),
    );
    image.texture_descriptor.usage =
        TextureUsages::TEXTURE_BINDING | TextureUsages::COPY_DST | TextureUsages::RENDER_ATTACHMENT;
    let target = images.add(image);
    let orbit = overview(scene);
    let camera = commands
        .spawn((
            Camera3d::default(),
            bevy::camera::visibility::RenderLayers::from_layers(&[0, 1]),
            super::ambient::light(&scene.ambient),
            Camera {
                order: -1,
                clear_color: ClearColorConfig::Custom(Color::srgb_u8(25, 29, 38)),
                ..default()
            },
            RenderTarget::Image(target.into()),
            orbit.transform(),
            orbit,
            ChildOf(root),
        ))
        .id();
    commands.spawn((
        DesignLight,
        Visibility::Inherited,
        DirectionalLight {
            illuminance: 15000.,
            ..default()
        },
        Transform::from_rotation(Quat::from_euler(EulerRot::XYZ, -0.8, -0.6, 0.)),
        ChildOf(root),
    ));
    commands.spawn((
        DesignLight,
        Visibility::Inherited,
        DirectionalLight {
            illuminance: 5000.,
            ..default()
        },
        Transform::from_rotation(Quat::from_euler(EulerRot::XYZ, -1.4, 2., 0.)),
        ChildOf(root),
    ));
    let mut nodes = Vec::new();
    for (index, e) in scene.entities.iter().enumerate() {
        // Hidden is a runtime flag; the designer must still render this entry.
        let q = Quat::from_array(e.rotation);
        let transform = Transform {
            translation: Vec3::from_array(e.position),
            rotation: if q.length_squared() > 0.00001 {
                q.normalize()
            } else {
                Quat::IDENTITY
            },
            scale: Vec3::from_array(e.scale),
        };
        let entity = commands
            .spawn((
                super::gizmo::SceneObject(index, transform),
                Name::new(e.name.clone()),
                transform,
                Visibility::default(),
                ChildOf(
                    super::cinematic::rig_parent(scene, index)
                        .map(|i| nodes[i])
                        .unwrap_or(root),
                ),
            ))
            .id();
        nodes.push(entity);
        if e.kind == "CAMERA" {
            super::game_camera::spawn(
                commands,
                root,
                entity,
                images,
                meshes,
                materials,
                &scene.ambient,
            );
        }
        if e.kind == "PATH" {
            super::cinematic::line(
                commands,
                entity,
                meshes,
                materials,
                &super::path::points(&e.properties["pathData"]),
                Color::srgb(0.9, 0.7, 0.2),
            );
        }
        if e.kind == "CINEMATIC_TRACK" {
            super::cinematic::spawn(
                commands,
                entity,
                meshes,
                materials,
                &e.properties["cinematicTrackData"],
            );
        }
        if e.kind == "LIGHT" {
            commands.entity(entity).insert(PointLight {
                intensity: e.properties["lightIntensity"].as_f64().unwrap_or(900.) as f32,
                range: e.properties["lightRange"].as_f64().unwrap_or(12.) as f32,
                ..default()
            });
            commands.spawn((
                Mesh3d(meshes.add(Sphere::new(0.15))),
                MeshMaterial3d(materials.add(StandardMaterial {
                    base_color: Color::srgb(1., 0.85, 0.3),
                    unlit: true,
                    ..default()
                })),
                ChildOf(entity),
            ));
        }
        if let Some(path) = &e.model {
            let Some(asset) = scene
                .resource_root
                .as_ref()
                .and_then(|root| project_assets.asset(root, path))
            else {
                continue;
            };
            let handle: Handle<WorldAsset> = server.load(asset.with_label("Scene0"));
            commands.spawn((
                WorldAssetRoot(handle),
                Transform::default(),
                ChildOf(entity),
            ));
        } else if [
            "BOX",
            "QUAD",
            "SPHERE",
            "WEDGE",
            "CYLINDER",
            "CONE",
            "HOLLOW_CYLINDER",
            "STAIRS",
            "ARCH",
        ]
        .contains(&e.kind.as_str())
        {
            let mesh = if let Some(mesh) = super::primitives::mesh(&e.kind, &e.properties) {
                meshes.add(mesh)
            } else if e.kind == "SPHERE" {
                meshes.add(Sphere::new(e.size[0] * 0.5))
            } else {
                meshes.add(Cuboid::new(e.size[0], e.size[1], e.size[2]))
            };
            commands.entity(entity).insert((
                Mesh3d(mesh),
                MeshMaterial3d(materials.add(StandardMaterial {
                    base_color: Color::srgb_u8(110, 125, 145),
                    perceptual_roughness: 0.85,
                    cull_mode: None,
                    ..default()
                })),
            ));
        }
    }
    (root, camera)
}

pub(super) fn overview(scene: &Scene3d) -> Orbit {
    Orbit {
        target: scene
            .entities
            .iter()
            .find(|e| e.kind == "BOX" && !e.hidden)
            .or_else(|| {
                scene
                    .entities
                    .iter()
                    .find(|e| !e.hidden && e.model.is_some())
            })
            .map(|e| Vec3::from_array(e.position))
            .unwrap_or(Vec3::ZERO),
        distance: 220.,
        yaw: 0.65,
        pitch: 0.4,
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use bevy::{
        asset::{AssetApp, AssetPlugin},
        ecs::system::RunSystemOnce,
    };

    #[test]
    fn runtime_hidden_geometry_is_still_spawned_in_designer() {
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, AssetPlugin::default()))
            .init_asset::<Image>()
            .init_asset::<Mesh>()
            .init_asset::<StandardMaterial>();
        app.world_mut()
            .run_system_once(
                |mut commands: Commands,
                 mut images: ResMut<Assets<Image>>,
                 mut meshes: ResMut<Assets<Mesh>>,
                 mut materials: ResMut<Assets<StandardMaterial>>,
                 server: Res<AssetServer>| {
                    let dir = tempfile::tempdir().unwrap();
                    let scene = scenemax_ide_services::scene3d::load(
                        dir.path(),
                        r#"{"entities":[{"type":"BOX","name":"Hidden geometry","hidden":true}]}"#,
                    )
                    .unwrap();
                    assert!(scene.entities[0].hidden);
                    spawn_world(
                        &mut commands,
                        &mut images,
                        &mut meshes,
                        &mut materials,
                        &server,
                        &scene,
                        &crate::project_assets::ProjectAssets::default(),
                    );
                },
            )
            .unwrap();
        let world = app.world_mut();
        assert_eq!(
            world
                .query::<&super::super::game_camera::Preview>()
                .iter(world)
                .count(),
            1
        );
        assert!(world.query::<&Mesh3d>().iter(world).count() > 1);
    }
}
