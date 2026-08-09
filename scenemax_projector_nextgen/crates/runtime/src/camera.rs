use super::*;

pub(super) fn apply_camera_systems(program: &Program, camera_system: &mut SceneMaxCameraSystem) {
    camera_system.fighting = None;
    camera_system.third_person.clear();
    camera_system.selected = None;
    camera_system.attached = None;

    camera_system.fighting = program.statements.iter().find_map(|statement| {
        let Statement::FightingCamera(camera) = statement else {
            return None;
        };
        Some(FightingCameraRuntime {
            name: camera.name.clone(),
            target_a: camera.target_a.clone(),
            target_b: camera.target_b.clone(),
            depth: camera.depth,
            height: camera.height,
            side: camera.side,
            min_distance: camera.min_distance,
            max_distance: camera.max_distance,
            damping: camera.damping,
        })
    });
    for statement in &program.statements {
        let Statement::ThirdPersonCamera(camera) = statement else {
            continue;
        };
        camera_system.third_person.insert(
            camera.name.clone(),
            ThirdPersonCameraRuntime {
                name: camera.name.clone(),
                target: camera.target.clone(),
                distance: camera.distance,
                height: camera.height,
                side: camera.side,
                look_ahead: camera.look_ahead,
                damping: camera.damping,
                fov: camera.fov,
                max_fov: camera.max_fov,
            },
        );
    }
    camera_system.selected = camera_system
        .fighting
        .as_ref()
        .map(|camera| camera.name.clone());

    if let Some(camera) = &camera_system.fighting {
        tracing::info!(
            name = %camera.name,
            target_a = %camera.target_a,
            target_b = %camera.target_b,
            "activated SceneMax fighting camera"
        );
    }
    for camera in camera_system.third_person.values() {
        tracing::info!(
            name = %camera.name,
            target = %camera.target,
            "registered SceneMax third-person camera"
        );
    }
}

pub(super) fn select_camera_system(name: &str, camera_system: &mut SceneMaxCameraSystem) {
    if camera_system
        .fighting
        .as_ref()
        .is_some_and(|camera| camera.name == name)
    {
        camera_system.selected = Some(name.to_owned());
        tracing::info!(name, "selected SceneMax camera system");
    } else if camera_system.third_person.contains_key(name) {
        camera_system.selected = Some(name.to_owned());
        tracing::info!(name, "selected SceneMax camera system");
    } else {
        tracing::debug!(name, "SceneMax camera system is not implemented");
    }
}

pub(super) fn attach_camera(
    attach: &CameraAttachStatement,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
    camera_system: &mut SceneMaxCameraSystem,
) {
    let target = resolve_object_alias(&attach.target, object_pools, scope);
    camera_system.attached = Some(CameraAttachmentRuntime {
        target: target.clone(),
        offset: vec3_from_scenemax(attach.offset),
    });
    tracing::info!(target, "attached SceneMax camera");
}

pub(super) fn chase_camera(
    target: &str,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
    camera_system: &mut SceneMaxCameraSystem,
) {
    let target = resolve_object_alias(target, object_pools, scope);
    camera_system.attached = Some(CameraAttachmentRuntime {
        target: target.clone(),
        offset: Vec3::new(0.0, 3.0, -12.0),
    });
    tracing::info!(target, "chasing SceneMax camera target");
}

pub(super) fn stop_camera_attachment(camera_system: &mut SceneMaxCameraSystem) {
    if camera_system.attached.take().is_some() {
        tracing::info!("stopped SceneMax camera attachment");
    }
}

pub(super) fn update_fighting_camera(
    time: Res<Time>,
    camera_system: Res<SceneMaxCameraSystem>,
    entities: Query<(&SceneMaxEntity, &Transform)>,
    mut cameras: Query<&mut Transform, (With<Camera3d>, Without<SceneMaxEntity>)>,
) {
    let Some(camera_settings) = camera_system.fighting.as_ref() else {
        return;
    };
    if camera_system
        .selected
        .as_ref()
        .is_some_and(|selected| selected != &camera_settings.name)
    {
        return;
    }

    let mut target_a = None;
    let mut target_b = None;
    for (entity, transform) in &entities {
        if entity.name == camera_settings.target_a {
            target_a = Some(*transform);
        } else if entity.name == camera_settings.target_b {
            target_b = Some(*transform);
        }
    }

    let (Some(target_a), Some(target_b)) = (target_a, target_b) else {
        return;
    };
    let Ok(mut camera) = cameras.single_mut() else {
        return;
    };

    let midpoint = (target_a.translation + target_b.translation) * 0.5;
    let mut fighter_axis = target_b.translation - target_a.translation;
    fighter_axis.y = 0.0;
    if fighter_axis.length_squared() <= f32::EPSILON {
        fighter_axis = Vec3::Z;
    }
    fighter_axis = fighter_axis.normalize();

    let view_axis = Vec3::new(fighter_axis.z, 0.0, -fighter_axis.x).normalize();
    let fighter_distance = target_a
        .translation
        .distance(target_b.translation)
        .clamp(camera_settings.min_distance, camera_settings.max_distance);
    let desired_depth = camera_settings.depth.max(fighter_distance * 0.65);
    let look_target = midpoint + Vec3::Y * camera_settings.height.max(1.0) * 0.35;
    let desired_translation = midpoint
        + view_axis * desired_depth
        + fighter_axis * camera_settings.side
        + Vec3::Y * camera_settings.height;

    let damping = camera_settings.damping.max(0.001);
    let blend = 1.0 - (-damping * time.delta_secs()).exp();
    camera.translation = camera.translation.lerp(desired_translation, blend);
    camera.look_at(look_target, Vec3::Y);
}

pub(super) fn update_third_person_camera(
    time: Res<Time>,
    camera_system: Res<SceneMaxCameraSystem>,
    entities: Query<(&SceneMaxEntity, &Transform)>,
    mut cameras: Query<&mut Transform, (With<Camera3d>, Without<SceneMaxEntity>)>,
) {
    let Some(selected) = camera_system.selected.as_ref() else {
        return;
    };
    let Some(camera_settings) = camera_system.third_person.get(selected) else {
        return;
    };

    let Some(target) = entities.iter().find_map(|(entity, transform)| {
        (entity.name == camera_settings.target).then_some(*transform)
    }) else {
        return;
    };
    let Ok(mut camera) = cameras.single_mut() else {
        return;
    };

    let forward = horizontal_forward(&target);
    let right = horizontal_right(&target);
    let look_target = target.translation
        + forward * camera_settings.look_ahead
        + Vec3::Y * camera_settings.height.max(1.0) * 0.35;
    let desired_translation = target.translation - forward * camera_settings.distance
        + right * camera_settings.side
        + Vec3::Y * camera_settings.height;

    let damping = camera_settings.damping.max(0.001);
    let blend = 1.0 - (-damping * time.delta_secs()).exp();
    camera.translation = camera.translation.lerp(desired_translation, blend);
    camera.look_at(look_target, Vec3::Y);

    let _ = (camera_settings.fov, camera_settings.max_fov);
}

pub(super) fn update_attached_camera(
    camera_system: Res<SceneMaxCameraSystem>,
    entities: Query<(&SceneMaxEntity, &Transform)>,
    mut cameras: Query<&mut Transform, (With<Camera3d>, Without<SceneMaxEntity>)>,
) {
    let Some(attachment) = camera_system.attached.as_ref() else {
        return;
    };
    let Some(target) = entities
        .iter()
        .find_map(|(entity, transform)| (entity.name == attachment.target).then_some(*transform))
    else {
        return;
    };
    let Ok(mut camera) = cameras.single_mut() else {
        return;
    };

    let desired_translation = target.translation + target.rotation * attachment.offset;
    camera.translation = desired_translation;
    camera.look_at(
        target.translation + Vec3::Y * attachment.offset.y.max(1.0) * 0.35,
        Vec3::Y,
    );
}

pub(super) fn setup_camera_and_lights(
    mut commands: Commands,
    startup_program: Res<SceneMaxStartupProgram>,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
) {
    runtime_assets.placeholder_mesh = Some(meshes.add(Cuboid::new(1.0, 1.0, 1.0)));
    runtime_assets.placeholder_material = Some(materials.add(Color::srgb_u8(185, 150, 65)));

    commands.insert_resource(GlobalAmbientLight {
        color: Color::WHITE,
        brightness: 220.0,
        ..default()
    });

    commands.spawn((
        DirectionalLight {
            illuminance: 24_000.0,
            shadow_maps_enabled: true,
            shadow_depth_bias: 0.08,
            shadow_normal_bias: 1.8,
            ..default()
        },
        Transform::from_xyz(-8.0, 14.0, 8.0).looking_at(Vec3::ZERO, Vec3::Y),
    ));

    commands.spawn((
        PointLight {
            color: Color::srgb(1.0, 0.86, 0.68),
            intensity: 55_000.0,
            range: 45.0,
            shadow_maps_enabled: true,
            ..default()
        },
        Transform::from_xyz(-7.0, 8.0, 8.0),
    ));

    commands.spawn((
        PointLight {
            color: Color::srgb(0.55, 0.7, 1.0),
            intensity: 18_000.0,
            range: 55.0,
            shadow_maps_enabled: false,
            ..default()
        },
        Transform::from_xyz(9.0, 5.0, -9.0),
    ));

    commands.spawn((
        Camera3d::default(),
        IsDefaultUiCamera,
        startup_program
            .0
            .as_ref()
            .map(camera_transform_from_program)
            .unwrap_or_else(default_camera_transform),
    ));
}

pub(super) fn camera_transform_from_program(program: &Program) -> Transform {
    let mut camera_transform = default_camera_transform();
    for statement in &program.statements {
        match statement {
            Statement::CameraPosition(position) => {
                camera_transform.translation = vec3_from_scenemax(*position);
            }
            Statement::CameraRotation(rotation) => {
                camera_transform.rotation = rotation_from_degrees(*rotation);
            }
            _ => {}
        }
    }
    camera_transform
}

pub(super) fn default_camera_transform() -> Transform {
    Transform::from_xyz(0.0, 0.0, 10.0).looking_at(Vec3::ZERO, Vec3::Y)
}
