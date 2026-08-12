use super::*;

#[derive(Component, Debug, Clone)]
pub(super) struct TimedCameraMoves {
    pub moves: Vec<TimedCameraMove>,
}

#[derive(Debug, Clone)]
pub(super) struct TimedCameraMove {
    pub axis: SceneMaxAxis,
    pub total_seconds: f32,
    pub remaining_seconds: f32,
    pub velocity: f32,
    pub next_log_seconds: f32,
}

pub(super) fn timed_camera_move_from_statement_resolved(
    camera_move: &CameraMoveStatement,
    distance: f32,
    duration_seconds: f32,
) -> TimedCameraMove {
    let duration = duration_seconds.max(0.001);
    TimedCameraMove {
        axis: camera_move.axis,
        total_seconds: duration,
        remaining_seconds: duration,
        velocity: distance / duration,
        next_log_seconds: 1.0,
    }
}

pub(super) fn append_timed_camera_move(commands: &mut Commands, timed_move: TimedCameraMove) {
    commands.queue(move |world: &mut World| {
        let Some((entity, start_position)) = ({
            let mut query = world
                .query_filtered::<(Entity, &Transform), (With<Camera3d>, Without<SceneMaxEntity>)>(
                );
            query
                .iter(world)
                .next()
                .map(|(entity, transform)| (entity, transform.translation))
        }) else {
            write_runtime_diagnostic_line("CAMERA:MOVE attach failed: no default Camera3d found");
            return;
        };
        let Ok(mut entity_mut) = world.get_entity_mut(entity) else {
            write_runtime_diagnostic_line("CAMERA:MOVE attach failed: camera entity unavailable");
            return;
        };
        let mut timed_move = Some(timed_move);
        if let Some(mut moves) = entity_mut.get_mut::<TimedCameraMoves>() {
            moves.moves.push(timed_move.take().unwrap());
        } else {
            entity_mut.insert(TimedCameraMoves {
                moves: vec![timed_move.take().unwrap()],
            });
        }
        write_runtime_diagnostic_line(format!(
            "CAMERA:MOVE attached to default camera start=({}, {}, {})",
            format_scenemax_number(start_position.x),
            format_scenemax_number(start_position.y),
            format_scenemax_number(start_position.z)
        ));
    });
}

pub(super) fn update_timed_camera_moves(
    time: Res<Time>,
    mut commands: Commands,
    mut cameras: Query<
        (Entity, &mut Transform, &mut TimedCameraMoves),
        (With<Camera3d>, Without<SceneMaxEntity>),
    >,
) {
    for (entity, mut transform, mut movements) in &mut cameras {
        let mut active_moves = Vec::with_capacity(movements.moves.len());
        for mut movement in movements.moves.drain(..) {
            let delta = time.delta_secs().min(movement.remaining_seconds);
            match movement.axis {
                SceneMaxAxis::X => transform.translation.x += movement.velocity * delta,
                SceneMaxAxis::Y => transform.translation.y += movement.velocity * delta,
                SceneMaxAxis::Z => transform.translation.z += movement.velocity * delta,
            }
            movement.remaining_seconds -= delta;
            let elapsed_seconds = movement.total_seconds - movement.remaining_seconds;
            if elapsed_seconds >= movement.next_log_seconds && movement.remaining_seconds > 0.0 {
                write_runtime_diagnostic_line(format!(
                    "CAMERA:MOVE progress axis={} elapsed={} remaining={} pos=({}, {}, {})",
                    axis_label(movement.axis),
                    format_scenemax_number(elapsed_seconds),
                    format_scenemax_number(movement.remaining_seconds),
                    format_scenemax_number(transform.translation.x),
                    format_scenemax_number(transform.translation.y),
                    format_scenemax_number(transform.translation.z)
                ));
                movement.next_log_seconds += 1.0;
            }
            if movement.remaining_seconds > 0.0 {
                active_moves.push(movement);
            } else {
                write_runtime_diagnostic_line(format!(
                    "CAMERA:MOVE finished axis={} pos=({}, {}, {})",
                    axis_label(movement.axis),
                    format_scenemax_number(transform.translation.x),
                    format_scenemax_number(transform.translation.y),
                    format_scenemax_number(transform.translation.z)
                ));
            }
        }
        movements.moves = active_moves;
        if movements.moves.is_empty() {
            commands.entity(entity).remove::<TimedCameraMoves>();
        }
    }
}

pub(super) fn apply_camera_systems(program: &Program, camera_system: &mut SceneMaxCameraSystem) {
    camera_system.fighting = None;
    camera_system.third_person.clear();
    camera_system.selected = None;
    camera_system.attached = None;
    camera_system.modifiers.clear();
    camera_system.active_modifiers.clear();
    camera_system.modifier_seed_counter = 0;
    camera_system.cinematic_vars.clear();
    camera_system.cinematic_rigs.clear();
    camera_system.active_cinematic = None;

    camera_system.fighting = program.statements.iter().find_map(|statement| {
        let Statement::FightingCamera(camera) = statement else {
            return None;
        };
        Some(FightingCameraRuntime {
            name: camera.name.clone(),
            target_a: camera.target_a.clone(),
            target_b: camera.target_b.clone(),
            height: camera.height,
            side: camera.side,
            min_distance: camera.min_distance,
            max_distance: camera.max_distance,
            zoom_factor: camera.zoom_factor,
            damping: camera.damping,
            look_ahead: camera.look_ahead,
            fov: camera.fov,
            max_fov: camera.max_fov,
            initialized: false,
            smoothed_look_at: Vec3::ZERO,
            last_side_dir: Vec3::Z,
        })
    });
    register_cinematic_camera_declarations(&program.statements, camera_system);
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

pub(super) fn register_camera_modifier(
    camera_system: &mut SceneMaxCameraSystem,
    name: &str,
    value: &CameraModifierValue,
) {
    camera_system
        .modifiers
        .insert(name.to_owned(), runtime_camera_modifier_from_parser(value));
    write_runtime_diagnostic_line(format!(
        "CAMERA:MODIFIER register name={name} type={}",
        value.modifier_type
    ));
}

pub(super) fn apply_camera_modifier(
    camera_system: &mut SceneMaxCameraSystem,
    target: &str,
    modifier: &str,
    overrides: &[(String, f32)],
) {
    if !camera_system_has_target(camera_system, target) {
        write_runtime_diagnostic_line(format!(
            "CAMERA:MODIFIER skip target={target} modifier={modifier} reason=unknown_camera_system"
        ));
        return;
    }
    let Some(base_value) = camera_system.modifiers.get(modifier) else {
        write_runtime_diagnostic_line(format!(
            "CAMERA:MODIFIER skip target={target} modifier={modifier} reason=unknown_modifier"
        ));
        return;
    };
    let mut value = base_value.clone();
    apply_camera_modifier_overrides(&mut value, overrides);
    camera_system.modifier_seed_counter += 1;
    let seed = camera_system.modifier_seed_counter as f32 * 0.731;
    camera_system.active_modifiers.push(ActiveCameraModifier {
        value,
        seed,
        elapsed_seconds: 0.0,
    });
    write_runtime_diagnostic_line(format!(
        "CAMERA:MODIFIER apply target={target} modifier={modifier} active={}",
        camera_system.active_modifiers.len()
    ));
}

fn runtime_camera_modifier_from_parser(value: &CameraModifierValue) -> RuntimeCameraModifier {
    RuntimeCameraModifier {
        modifier_type: value.modifier_type.clone(),
        duration: value.duration,
        amplitude: value.amplitude,
        frequency: value.frequency,
        x: value.x,
        y: value.y,
        z: value.z,
        rx: value.rx,
        ry: value.ry,
        rz: value.rz,
        fov: value.fov,
    }
}

fn camera_system_has_target(camera_system: &SceneMaxCameraSystem, target: &str) -> bool {
    camera_system
        .fighting
        .as_ref()
        .is_some_and(|camera| camera.name == target)
        || camera_system.third_person.contains_key(target)
        || camera_system
            .selected
            .as_ref()
            .is_some_and(|selected| selected == target)
}

fn apply_camera_modifier_overrides(value: &mut RuntimeCameraModifier, overrides: &[(String, f32)]) {
    for (name, override_value) in overrides {
        match name.as_str() {
            "duration" => value.duration = *override_value,
            "amplitude" => value.amplitude = *override_value,
            "frequency" => value.frequency = *override_value,
            "x" => value.x = *override_value,
            "y" => value.y = *override_value,
            "z" => value.z = *override_value,
            "rx" => value.rx = *override_value,
            "ry" => value.ry = *override_value,
            "rz" => value.rz = *override_value,
            "fov" => value.fov = *override_value,
            _ => {}
        }
    }
}

fn register_cinematic_camera_declarations(
    statements: &[Statement],
    camera_system: &mut SceneMaxCameraSystem,
) {
    for statement in statements {
        match statement {
            Statement::ModelDecl { name, resource, .. } => {
                if let Some(rig_id) = cinematic_resource_id(resource) {
                    camera_system.cinematic_vars.insert(
                        name.clone(),
                        CinematicCameraRuntimeRef {
                            rig_id: rig_id.to_owned(),
                        },
                    );
                    write_runtime_diagnostic_line(format!(
                        "registered cinematic camera {name}=>{resource}"
                    ));
                }
            }
            Statement::KeyEvent(event) => {
                register_cinematic_camera_declarations(&event.actions, camera_system)
            }
            Statement::WhenEvent(event) => {
                register_cinematic_camera_declarations(&event.actions, camera_system)
            }
            Statement::FunctionDef(function) => {
                register_cinematic_camera_declarations(&function.actions, camera_system)
            }
            Statement::If(statement) => {
                register_cinematic_camera_declarations(&statement.actions, camera_system);
                register_cinematic_camera_declarations(&statement.else_actions, camera_system);
            }
            Statement::Guarded { actions, .. }
            | Statement::Repeat { actions, .. }
            | Statement::DoWhile { actions, .. }
            | Statement::LoopContinue { actions, .. }
            | Statement::Async { actions } => {
                register_cinematic_camera_declarations(actions, camera_system);
            }
            _ => {}
        }
    }
}

pub(super) fn cinematic_resource_id(resource: &str) -> Option<&str> {
    resource
        .strip_prefix("cinematic.camera.")
        .or_else(|| resource.strip_prefix("Cinematic.Camera."))
        .filter(|id| !id.trim().is_empty())
}

pub(super) fn load_cinematic_rigs(
    program: &Program,
    camera_system: &mut SceneMaxCameraSystem,
    script_root: Option<&Path>,
    asset_root: Option<&Path>,
) {
    let mut remaining = HashSet::new();
    collect_cinematic_rig_ids(&program.statements, &mut remaining);
    if remaining.is_empty() {
        return;
    }

    let mut roots = Vec::new();
    if let Some(script_root) = script_root {
        roots.push(script_root.to_path_buf());
    }
    if let Some(project_root) = asset_root.and_then(Path::parent) {
        roots.push(project_root.join("scripts"));
    }
    let mut seen_roots = HashSet::new();
    for root in roots {
        if remaining.is_empty() {
            break;
        }
        let Some(root) = canonicalize_existing(&root) else {
            continue;
        };
        if !seen_roots.insert(root.clone()) {
            continue;
        }
        for designer_file in collect_smdesign_files(&root) {
            let Ok(text) = fs::read_to_string(&designer_file) else {
                continue;
            };
            let Ok(json) = serde_json::from_str::<serde_json::Value>(&text) else {
                tracing::warn!(
                    path = %designer_file.display(),
                    "failed to parse SceneMax designer file for cinematics"
                );
                continue;
            };
            parse_cinematic_rigs_from_entities(
                json.get("entities").and_then(serde_json::Value::as_array),
                &mut remaining,
                &designer_file,
                &text,
                camera_system,
            );
        }
    }
}

fn collect_cinematic_rig_ids(statements: &[Statement], wanted: &mut HashSet<String>) {
    for statement in statements {
        match statement {
            Statement::ModelDecl { resource, .. } => {
                if let Some(id) = cinematic_resource_id(resource) {
                    wanted.insert(id.to_ascii_lowercase());
                }
            }
            Statement::KeyEvent(event) => collect_cinematic_rig_ids(&event.actions, wanted),
            Statement::WhenEvent(event) => collect_cinematic_rig_ids(&event.actions, wanted),
            Statement::FunctionDef(function) => {
                collect_cinematic_rig_ids(&function.actions, wanted)
            }
            Statement::If(statement) => {
                collect_cinematic_rig_ids(&statement.actions, wanted);
                collect_cinematic_rig_ids(&statement.else_actions, wanted);
            }
            Statement::Guarded { actions, .. }
            | Statement::Repeat { actions, .. }
            | Statement::DoWhile { actions, .. }
            | Statement::LoopContinue { actions, .. }
            | Statement::Async { actions } => collect_cinematic_rig_ids(actions, wanted),
            _ => {}
        }
    }
}

fn collect_smdesign_files(root: &Path) -> Vec<PathBuf> {
    let mut result = Vec::new();
    collect_smdesign_files_recursive(root, &mut result, 0);
    result
}

fn collect_smdesign_files_recursive(root: &Path, result: &mut Vec<PathBuf>, depth: usize) {
    if depth > 8 {
        return;
    }
    let Ok(entries) = fs::read_dir(root) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            collect_smdesign_files_recursive(&path, result, depth + 1);
        } else if path
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("smdesign"))
        {
            result.push(path);
        }
    }
}

fn parse_cinematic_rigs_from_entities(
    entities: Option<&Vec<serde_json::Value>>,
    remaining: &mut HashSet<String>,
    source_path: &Path,
    source_text: &str,
    camera_system: &mut SceneMaxCameraSystem,
) {
    let Some(entities) = entities else {
        return;
    };
    for entity in entities {
        if entity
            .get("type")
            .and_then(serde_json::Value::as_str)
            .is_some_and(|value| value == "CINEMATIC_RIG")
        {
            let id = string_field(entity, "cinematicRuntimeId")
                .or_else(|| string_field(entity, "id"))
                .unwrap_or_default();
            let key = id.to_ascii_lowercase();
            if remaining.contains(&key)
                && let Some(rig) = parse_cinematic_rig(entity, source_path, source_text)
            {
                tracing::info!(
                    id = %rig.id,
                    name = %rig.name,
                    path = %source_path.display(),
                    "loaded SceneMax cinematic rig"
                );
                write_runtime_diagnostic_line(format!(
                    "loaded cinematic rig {} from {}",
                    rig.id,
                    source_path.display()
                ));
                camera_system.cinematic_rigs.insert(key, rig);
                remaining.remove(&id.to_ascii_lowercase());
            }
        }
        parse_cinematic_rigs_from_entities(
            entity.get("children").and_then(serde_json::Value::as_array),
            remaining,
            source_path,
            source_text,
            camera_system,
        );
    }
}

fn parse_cinematic_rig(
    json: &serde_json::Value,
    source_path: &Path,
    source_text: &str,
) -> Option<RuntimeCinematicRig> {
    let mut rig = RuntimeCinematicRig {
        id: string_field(json, "cinematicRuntimeId").or_else(|| string_field(json, "id"))?,
        name: string_field(json, "name").unwrap_or_else(|| "Cinematic Rig".to_owned()),
        position: vec3_json(json.get("position"), Vec3::ZERO),
        rotation: quat_json(json.get("rotation"), Quat::IDENTITY),
        scale: vec3_json(json.get("scale"), Vec3::ONE),
        target_entity_name: string_field(json, "cinematicTargetEntityName").unwrap_or_default(),
        target_offset: vec3_json(json.get("cinematicTargetOffset"), Vec3::new(0.0, 1.5, 0.0)),
        ease_in: string_field(json, "cinematicEaseIn").unwrap_or_else(|| "linear".to_owned()),
        ease_out: string_field(json, "cinematicEaseOut").unwrap_or_else(|| "linear".to_owned()),
        tracks_by_id: HashMap::new(),
        segments: Vec::new(),
        has_relative_target_placement: false,
        relative_rig_position_to_target: Vec3::ZERO,
        relative_rig_rotation_to_target: Quat::IDENTITY,
    };

    if let Some(children) = json.get("children").and_then(serde_json::Value::as_array) {
        for child in children {
            if child
                .get("type")
                .and_then(serde_json::Value::as_str)
                .is_some_and(|value| value == "CINEMATIC_TRACK")
                && let Some(track) = parse_cinematic_track(child)
            {
                rig.tracks_by_id.insert(track.id.clone(), track);
            }
        }
    }

    if let Some(segments) = json
        .get("cinematicSegments")
        .and_then(serde_json::Value::as_array)
    {
        for segment in segments {
            rig.segments.push(RuntimeCinematicSegment {
                track_id: string_field(segment, "trackId").unwrap_or_default(),
                start_anchor: i32_field(segment, "startAnchor", 0),
                end_anchor: i32_field(segment, "endAnchor", 0),
            });
        }
    }

    populate_cinematic_relative_target_placement(&mut rig, json, source_path, source_text);
    Some(rig)
}

fn parse_cinematic_track(json: &serde_json::Value) -> Option<RuntimeCinematicTrack> {
    let data = json.get("cinematicTrackData");
    Some(RuntimeCinematicTrack {
        id: string_field(json, "id")?,
        local_position: vec3_json(json.get("position"), Vec3::ZERO),
        local_rotation: quat_json(json.get("rotation"), Quat::IDENTITY),
        local_scale: vec3_json(json.get("scale"), Vec3::ONE),
        radius_x: f32_field_opt(data, "radiusX").unwrap_or(2.5),
        radius_z: f32_field_opt(data, "radiusZ").unwrap_or(2.5),
        anchor_count: i32_field_opt(data, "anchorCount").unwrap_or(360).max(8),
    })
}

fn populate_cinematic_relative_target_placement(
    rig: &mut RuntimeCinematicRig,
    rig_json: &serde_json::Value,
    _source_path: &Path,
    source_text: &str,
) {
    if rig.target_entity_name.is_empty() {
        return;
    }
    let Ok(root) = serde_json::from_str::<serde_json::Value>(source_text) else {
        return;
    };
    let target_id = string_field(rig_json, "cinematicTargetEntityId").unwrap_or_default();
    let Some(target) = find_designer_entity(
        root.get("entities").and_then(serde_json::Value::as_array),
        &target_id,
        &rig.target_entity_name,
    ) else {
        return;
    };
    let target_pos = vec3_json(target.get("position"), Vec3::ZERO);
    let target_rot = quat_json(target.get("rotation"), Quat::IDENTITY);
    let target_point = target_pos + rig.target_offset;
    rig.relative_rig_position_to_target = target_rot.inverse() * (rig.position - target_point);
    rig.relative_rig_rotation_to_target = target_rot.inverse() * rig.rotation;
    rig.has_relative_target_placement = true;
}

fn find_designer_entity<'a>(
    entities: Option<&'a Vec<serde_json::Value>>,
    target_id: &str,
    target_name: &str,
) -> Option<&'a serde_json::Value> {
    for entity in entities? {
        let entity_id = string_field(entity, "id").unwrap_or_default();
        let entity_name = string_field(entity, "name").unwrap_or_default();
        if (!target_id.is_empty() && entity_id == target_id)
            || (!target_name.is_empty() && entity_name == target_name)
        {
            return Some(entity);
        }
        if let Some(nested) = find_designer_entity(
            entity.get("children").and_then(serde_json::Value::as_array),
            target_id,
            target_name,
        ) {
            return Some(nested);
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scene_local_cinematic_rig_is_not_overwritten_by_project_scan() {
        let root = std::env::temp_dir().join(format!(
            "scenemax_cinematic_rig_precedence_{}_{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let scene_root = root.join("running").join("game_intro");
        let project_scripts = root.join("scripts").join("other_scene");
        let asset_root = root.join("resources");
        fs::create_dir_all(&scene_root).unwrap();
        fs::create_dir_all(&project_scripts).unwrap();
        fs::create_dir_all(&asset_root).unwrap();
        fs::write(
            scene_root.join("game_intro.smdesign"),
            cinematic_rig_json("Intro Rig"),
        )
        .unwrap();
        fs::write(
            project_scripts.join("other.smdesign"),
            cinematic_rig_json("Other Rig"),
        )
        .unwrap();
        let program = Program {
            statements: vec![Statement::ModelDecl {
                name: "intro_camera".to_owned(),
                resource: "cinematic.camera.cinematic_rig_1".to_owned(),
                options: EntityOptions::default(),
            }],
        };
        let mut camera_system = SceneMaxCameraSystem::default();

        load_cinematic_rigs(
            &program,
            &mut camera_system,
            Some(&scene_root),
            Some(&asset_root),
        );

        assert_eq!(
            camera_system
                .cinematic_rigs
                .get("cinematic_rig_1")
                .map(|rig| rig.name.as_str()),
            Some("Intro Rig")
        );

        let _ = fs::remove_dir_all(root);
    }

    fn cinematic_rig_json(name: &str) -> String {
        format!(
            r#"{{
  "entities": [
    {{
      "type": "CINEMATIC_RIG",
      "cinematicRuntimeId": "cinematic_rig_1",
      "name": "{name}",
      "children": [
        {{
          "type": "CINEMATIC_TRACK",
          "id": "track1",
          "cinematicTrackData": {{"anchorCount": 16}}
        }}
      ],
      "cinematicSegments": [
        {{"trackId": "track1", "startAnchor": 0, "endAnchor": 8}}
      ]
    }}
  ]
}}"#
        )
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

pub(super) fn start_cinematic_camera(
    play: &CinematicPlayStatement,
    transforms_by_name: &HashMap<String, Transform>,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
    camera_system: &mut SceneMaxCameraSystem,
) {
    let Some(camera_ref) = camera_system.cinematic_vars.get(&play.target).cloned() else {
        write_runtime_diagnostic_line(format!("cinematic camera {} was not declared", play.target));
        return;
    };
    let Some(rig) = camera_system
        .cinematic_rigs
        .get(&camera_ref.rig_id.to_ascii_lowercase())
        .cloned()
    else {
        write_runtime_diagnostic_line(format!(
            "cinematic rig {} for camera {} was not found",
            camera_ref.rig_id, play.target
        ));
        return;
    };
    let playback = build_cinematic_playback_segments(&rig, play.reverse);
    if playback.is_empty() {
        write_runtime_diagnostic_line(format!("cinematic rig {} has no playable segments", rig.id));
        return;
    }

    let mut active = ActiveCinematicCamera {
        rig,
        playback,
        elapsed_seconds: 0.0,
        duration_seconds: play.duration_seconds.max(0.1),
        look_at: play.look_at.clone(),
        reverse: play.reverse,
        locked_target_placement_rotation: None,
    };
    distribute_cinematic_segment_durations(&mut active);
    update_cinematic_rig_transform(&mut active, transforms_by_name, object_pools, scope);
    tracing::info!(
        camera = %play.target,
        rig = %active.rig.id,
        duration = active.duration_seconds,
        reverse = active.reverse,
        "started SceneMax cinematic camera"
    );
    write_runtime_diagnostic_line(format!(
        "started cinematic camera {} rig={} duration={:.3}s reverse={}",
        play.target, active.rig.id, active.duration_seconds, active.reverse
    ));
    camera_system.active_cinematic = Some(active);
}

fn build_cinematic_playback_segments(
    rig: &RuntimeCinematicRig,
    reverse: bool,
) -> Vec<CinematicPlaybackSegment> {
    let mut playback = Vec::new();
    let segments: Box<dyn Iterator<Item = &RuntimeCinematicSegment>> = if reverse {
        Box::new(rig.segments.iter().rev())
    } else {
        Box::new(rig.segments.iter())
    };
    for segment in segments {
        let Some(track) = rig.tracks_by_id.get(&segment.track_id) else {
            continue;
        };
        let (start_anchor, anchor_distance) = if reverse {
            (
                normalize_anchor(segment.end_anchor, track.anchor_count),
                -compute_forward_anchor_distance(
                    segment.start_anchor as f32,
                    segment.end_anchor,
                    track.anchor_count,
                ),
            )
        } else {
            let start = normalize_anchor(segment.start_anchor, track.anchor_count);
            (
                start,
                compute_forward_anchor_distance(
                    start as f32,
                    segment.end_anchor,
                    track.anchor_count,
                ),
            )
        };
        playback.push(CinematicPlaybackSegment {
            track: track.clone(),
            start_anchor,
            anchor_distance,
            duration_seconds: 0.0,
            first_segment: false,
            last_segment: false,
        });
    }
    let last_index = playback.len().saturating_sub(1);
    for (index, segment) in playback.iter_mut().enumerate() {
        segment.first_segment = index == 0;
        segment.last_segment = index == last_index;
    }
    playback
}

fn distribute_cinematic_segment_durations(active: &mut ActiveCinematicCamera) {
    let total_anchor_distance = active
        .playback
        .iter()
        .map(|segment| segment.anchor_distance.abs().max(0.0001))
        .sum::<f32>();
    if total_anchor_distance <= 0.000001 {
        let even = active.duration_seconds / active.playback.len().max(1) as f32;
        for segment in &mut active.playback {
            segment.duration_seconds = even;
        }
    } else {
        for segment in &mut active.playback {
            segment.duration_seconds = active.duration_seconds
                * (segment.anchor_distance.abs().max(0.0001) / total_anchor_distance);
        }
    }
}

pub(super) fn update_cinematic_camera(
    time: Res<Time>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    object_pools: Res<SceneMaxObjectPools>,
    startup_program: Res<SceneMaxStartupProgram>,
    scene_entities: Query<(Entity, &SceneMaxEntity, &Transform)>,
    bone_queries: SceneMaxBoneQueries,
    mut cameras: Query<
        (&mut Transform, &mut SceneMaxCameraModifierState),
        (With<Camera3d>, Without<SceneMaxEntity>),
    >,
) {
    let Some(mut active) = camera_system.active_cinematic.take() else {
        return;
    };
    let Some(program) = startup_program.0.as_ref() else {
        return;
    };
    let transforms_by_name =
        build_action_transform_map(program, &object_pools, scene_entities, &bone_queries);
    update_cinematic_rig_transform(&mut active, &transforms_by_name, &object_pools, None);

    let delta = time.delta_secs();
    active.elapsed_seconds = (active.elapsed_seconds + delta).min(active.duration_seconds);
    if let Ok((mut camera, mut modifier_state)) = cameras.single_mut()
        && let Some((camera_pos, look_at)) = cinematic_camera_frame(&active, &transforms_by_name)
    {
        camera.translation = camera_pos;
        camera.look_at(look_at, Vec3::Y);
        modifier_state.base_look_at = Some(look_at);
    }

    if active.elapsed_seconds < active.duration_seconds - 0.000001 {
        camera_system.active_cinematic = Some(active);
    } else {
        tracing::info!(rig = %active.rig.id, "finished SceneMax cinematic camera");
    }
}

fn cinematic_camera_frame(
    active: &ActiveCinematicCamera,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<(Vec3, Vec3)> {
    let mut remaining = active.elapsed_seconds;
    let mut selected = active.playback.last()?;
    for segment in &active.playback {
        selected = segment;
        if remaining <= segment.duration_seconds || segment.last_segment {
            break;
        }
        remaining -= segment.duration_seconds;
    }
    let segment_progress = if selected.duration_seconds <= 0.000001 {
        1.0
    } else {
        (remaining / selected.duration_seconds).clamp(0.0, 1.0)
    };
    let eased = apply_cinematic_rig_easing(
        &active.rig,
        segment_progress,
        selected.first_segment,
        selected.last_segment,
        active.reverse,
    );
    let anchor_cursor = advance_anchor_cursor(
        selected.start_anchor,
        selected.anchor_distance,
        eased,
        selected.track.anchor_count,
    );
    let camera_pos = compute_track_world_position(&active.rig, &selected.track, anchor_cursor);
    let look_ahead_step = if selected.anchor_distance < 0.0 {
        -1.0
    } else {
        1.0
    };
    let fallback = compute_track_world_position(
        &active.rig,
        &selected.track,
        anchor_cursor + look_ahead_step,
    );
    let look_at = resolve_cinematic_look_at(active, transforms_by_name).unwrap_or(fallback);
    Some((camera_pos, look_at))
}

fn resolve_cinematic_look_at(
    active: &ActiveCinematicCamera,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Vec3> {
    match &active.look_at {
        Some(CinematicLookAt::Entity(target)) => {
            lookup_cinematic_target_transform(target, transforms_by_name)
                .map(|transform| transform.translation + active.rig.target_offset)
        }
        Some(CinematicLookAt::RelativePosition(text)) => {
            evaluate_cinematic_relative_position(text, transforms_by_name)
        }
        None => {
            lookup_cinematic_target_transform(&active.rig.target_entity_name, transforms_by_name)
                .map(|transform| transform.translation + active.rig.target_offset)
        }
    }
}

fn lookup_cinematic_target_transform(
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Transform> {
    lookup_subject_transform(target, transforms_by_name).or_else(|| {
        let owner = target.split('.').next()?;
        transforms_by_name.get(owner).copied()
    })
}

fn evaluate_cinematic_relative_position(
    text: &str,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Vec3> {
    let mut parts = text.split_whitespace();
    let entity = parts.next()?;
    let base = lookup_cinematic_target_transform(entity, transforms_by_name)?;
    let mut point = base.translation;
    while let Some(direction) = parts.next() {
        let amount = parts
            .next()
            .and_then(|value| value.parse::<f32>().ok())
            .unwrap_or(0.0);
        let delta = match direction.to_ascii_lowercase().as_str() {
            "forward" => horizontal_forward(&base) * amount,
            "back" | "backward" => -horizontal_forward(&base) * amount,
            "right" => horizontal_right(&base) * amount,
            "left" => -horizontal_right(&base) * amount,
            "up" => Vec3::Y * amount,
            "down" => -Vec3::Y * amount,
            _ => Vec3::ZERO,
        };
        point += delta;
    }
    Some(point)
}

fn update_cinematic_rig_transform(
    active: &mut ActiveCinematicCamera,
    transforms_by_name: &HashMap<String, Transform>,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
) {
    if !active.rig.has_relative_target_placement {
        return;
    }
    let Some(target) =
        current_cinematic_target_transform(active, transforms_by_name, object_pools, scope)
    else {
        return;
    };
    let placement_rotation = if should_follow_live_cinematic_target_rotation(active) {
        target.rotation
    } else if let Some(locked) = active.locked_target_placement_rotation {
        locked
    } else {
        active.locked_target_placement_rotation = Some(target.rotation);
        target.rotation
    };
    active.rig.position = target.translation
        + active.rig.target_offset
        + placement_rotation * active.rig.relative_rig_position_to_target;
    active.rig.rotation = placement_rotation * active.rig.relative_rig_rotation_to_target;
}

fn current_cinematic_target_transform(
    active: &ActiveCinematicCamera,
    transforms_by_name: &HashMap<String, Transform>,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
) -> Option<Transform> {
    match &active.look_at {
        Some(CinematicLookAt::Entity(target)) => {
            let resolved = resolve_object_alias(target, object_pools, scope);
            lookup_cinematic_target_transform(&resolved, transforms_by_name)
                .or_else(|| lookup_cinematic_target_transform(target, transforms_by_name))
        }
        Some(CinematicLookAt::RelativePosition(text)) => {
            let entity = text.split_whitespace().next()?;
            lookup_cinematic_target_transform(entity, transforms_by_name)
        }
        None => {
            lookup_cinematic_target_transform(&active.rig.target_entity_name, transforms_by_name)
        }
    }
}

fn should_follow_live_cinematic_target_rotation(active: &ActiveCinematicCamera) -> bool {
    matches!(active.look_at, Some(CinematicLookAt::RelativePosition(_)))
}

fn compute_track_world_position(
    rig: &RuntimeCinematicRig,
    track: &RuntimeCinematicTrack,
    anchor_cursor: f32,
) -> Vec3 {
    let count = track.anchor_count.max(8);
    let mut wrapped = anchor_cursor % count as f32;
    if wrapped < 0.0 {
        wrapped += count as f32;
    }
    let index0 = wrapped.floor() as i32;
    let index1 = (index0 + 1) % count;
    let alpha = wrapped - index0 as f32;
    let local = anchor_local_point(track, index0).lerp(anchor_local_point(track, index1), alpha);
    let track_translation = rig.position + rig.rotation * (track.local_position * rig.scale);
    let track_rotation = rig.rotation * track.local_rotation;
    let track_scale = rig.scale * track.local_scale;
    track_translation + track_rotation * (local * track_scale)
}

fn anchor_local_point(track: &RuntimeCinematicTrack, anchor_index: i32) -> Vec3 {
    let count = track.anchor_count.max(8);
    let normalized = normalize_anchor(anchor_index, count);
    let angle = std::f32::consts::TAU * normalized as f32 / count as f32;
    Vec3::new(
        angle.cos() * track.radius_x,
        0.0,
        angle.sin() * track.radius_z,
    )
}

fn normalize_anchor(anchor_index: i32, anchor_count: i32) -> i32 {
    let mut normalized = anchor_index % anchor_count;
    if normalized < 0 {
        normalized += anchor_count;
    }
    normalized
}

fn compute_forward_anchor_distance(current_cursor: f32, end_anchor: i32, anchor_count: i32) -> f32 {
    let mut current = current_cursor % anchor_count as f32;
    if current < 0.0 {
        current += anchor_count as f32;
    }
    let mut end = end_anchor as f32 % anchor_count as f32;
    if end < 0.0 {
        end += anchor_count as f32;
    }
    if end >= current {
        end - current
    } else {
        anchor_count as f32 - current + end
    }
}

fn advance_anchor_cursor(
    start_anchor: i32,
    distance: f32,
    progress: f32,
    anchor_count: i32,
) -> f32 {
    let mut cursor = start_anchor as f32 + distance * progress.clamp(0.0, 1.0);
    while cursor >= anchor_count as f32 {
        cursor -= anchor_count as f32;
    }
    while cursor < 0.0 {
        cursor += anchor_count as f32;
    }
    cursor
}

fn apply_cinematic_rig_easing(
    rig: &RuntimeCinematicRig,
    progress: f32,
    first_segment: bool,
    last_segment: bool,
    reverse: bool,
) -> f32 {
    let p = progress.clamp(0.0, 1.0);
    let ease_in = if first_segment {
        if reverse { &rig.ease_out } else { &rig.ease_in }
    } else {
        "linear"
    };
    let ease_out = if last_segment {
        if reverse { &rig.ease_in } else { &rig.ease_out }
    } else {
        "linear"
    };
    let use_ease_in = !ease_in.eq_ignore_ascii_case("linear");
    let use_ease_out = !ease_out.eq_ignore_ascii_case("linear");
    match (use_ease_in, use_ease_out) {
        (false, false) => p,
        (true, false) => apply_boundary_ease(ease_in, p, reverse),
        (false, true) => apply_boundary_ease(ease_out, p, reverse),
        (true, true) if p < 0.5 => 0.5 * apply_boundary_ease(ease_in, p * 2.0, reverse),
        (true, true) => 0.5 + 0.5 * apply_boundary_ease(ease_out, (p - 0.5) * 2.0, reverse),
    }
}

fn apply_boundary_ease(ease_id: &str, t: f32, reverse: bool) -> f32 {
    if !reverse {
        apply_single_ease(ease_id, t)
    } else {
        1.0 - apply_single_ease(ease_id, 1.0 - t.clamp(0.0, 1.0))
    }
}

fn apply_single_ease(ease_id: &str, t: f32) -> f32 {
    let p = t.clamp(0.0, 1.0);
    match ease_id {
        "ease_in_quad" => p * p,
        "ease_out_quad" => 1.0 - (1.0 - p) * (1.0 - p),
        "ease_in_cubic" => p * p * p,
        "ease_out_cubic" => 1.0 - (1.0 - p).powi(3),
        "ease_in_expo" => {
            if p <= 0.0 {
                0.0
            } else {
                2.0f32.powf(10.0 * (p - 1.0))
            }
        }
        "ease_out_expo" => {
            if p >= 1.0 {
                1.0
            } else {
                1.0 - 2.0f32.powf(-10.0 * p)
            }
        }
        "ease_in_sine" => 1.0 - (p * std::f32::consts::PI / 2.0).cos(),
        "ease_out_sine" => (p * std::f32::consts::PI / 2.0).sin(),
        _ => p,
    }
}

fn string_field(json: &serde_json::Value, name: &str) -> Option<String> {
    json.get(name)
        .and_then(serde_json::Value::as_str)
        .map(str::to_owned)
}

fn f32_field_opt(json: Option<&serde_json::Value>, name: &str) -> Option<f32> {
    json?
        .get(name)
        .and_then(serde_json::Value::as_f64)
        .map(|value| value as f32)
}

fn i32_field(json: &serde_json::Value, name: &str, fallback: i32) -> i32 {
    i32_field_opt(Some(json), name).unwrap_or(fallback)
}

fn i32_field_opt(json: Option<&serde_json::Value>, name: &str) -> Option<i32> {
    json?
        .get(name)
        .and_then(serde_json::Value::as_i64)
        .map(|value| value as i32)
}

fn vec3_json(value: Option<&serde_json::Value>, fallback: Vec3) -> Vec3 {
    let Some(values) = value.and_then(serde_json::Value::as_array) else {
        return fallback;
    };
    if values.len() < 3 {
        return fallback;
    }
    Vec3::new(
        values[0].as_f64().unwrap_or(fallback.x as f64) as f32,
        values[1].as_f64().unwrap_or(fallback.y as f64) as f32,
        values[2].as_f64().unwrap_or(fallback.z as f64) as f32,
    )
}

fn quat_json(value: Option<&serde_json::Value>, fallback: Quat) -> Quat {
    let Some(values) = value.and_then(serde_json::Value::as_array) else {
        return fallback;
    };
    if values.len() < 4 {
        return fallback;
    }
    Quat::from_xyzw(
        values[0].as_f64().unwrap_or(fallback.x as f64) as f32,
        values[1].as_f64().unwrap_or(fallback.y as f64) as f32,
        values[2].as_f64().unwrap_or(fallback.z as f64) as f32,
        values[3].as_f64().unwrap_or(fallback.w as f64) as f32,
    )
}

pub(super) fn update_fighting_camera(
    time: Res<Time>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    entities: Query<(&SceneMaxEntity, &Transform)>,
    mut cameras: Query<
        (
            &mut Transform,
            &mut SceneMaxCameraModifierState,
            Option<&mut Projection>,
        ),
        (With<Camera3d>, Without<SceneMaxEntity>),
    >,
) {
    if camera_system.active_cinematic.is_some() {
        return;
    }
    let selected = camera_system.selected.clone();
    let Some(camera_settings) = camera_system.fighting.as_mut() else {
        return;
    };
    if selected
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
    let Ok((mut camera, mut modifier_state, projection)) = cameras.single_mut() else {
        return;
    };
    let mut projection = projection;

    let midpoint = (target_a.translation + target_b.translation) * 0.5;
    let mut fighter_axis = target_b.translation - target_a.translation;
    fighter_axis.y = 0.0;
    if fighter_axis.length_squared() <= f32::EPSILON {
        fighter_axis = camera_settings.last_side_dir.cross(Vec3::Y);
    }
    fighter_axis = fighter_axis.normalize();

    let mut side_dir = fighter_axis.cross(Vec3::Y);
    if side_dir.length_squared() <= f32::EPSILON {
        side_dir = Vec3::Z;
    }
    side_dir = side_dir.normalize();
    if side_dir.dot(camera_settings.last_side_dir) < 0.0 {
        side_dir = -side_dir;
    }
    camera_settings.last_side_dir = side_dir;

    let planar_distance = (target_b.translation - target_a.translation)
        .with_y(0.0)
        .length()
        .max(1.0);
    let desired_depth = (planar_distance * camera_settings.zoom_factor)
        .clamp(camera_settings.min_distance, camera_settings.max_distance);
    let mut look_target = midpoint + Vec3::Y * camera_settings.height * 0.35;
    if camera_settings.look_ahead != 0.0 {
        look_target += fighter_axis * camera_settings.look_ahead;
    }

    let damping = camera_settings.damping.max(0.01);
    let blend = 1.0 - (-damping * time.delta_secs()).exp();
    let initialized_this_frame = !camera_settings.initialized;
    if initialized_this_frame {
        camera_settings.initialized = true;
        camera_settings.smoothed_look_at = look_target;
    } else {
        camera_settings.smoothed_look_at =
            camera_settings.smoothed_look_at.lerp(look_target, blend);
    }

    let desired_translation = camera_settings.smoothed_look_at
        + Vec3::Y * camera_settings.height
        + side_dir * desired_depth
        + fighter_axis * camera_settings.side;
    camera.translation = if initialized_this_frame {
        desired_translation
    } else {
        camera.translation.lerp(desired_translation, blend)
    };
    camera.look_at(camera_settings.smoothed_look_at, Vec3::Y);
    modifier_state.base_look_at = Some(camera_settings.smoothed_look_at);

    let distance_factor = ((planar_distance - camera_settings.min_distance)
        / (camera_settings.max_distance - camera_settings.min_distance).max(0.001))
    .clamp(0.0, 1.0);
    let target_fov = camera_settings
        .fov
        .lerp(camera_settings.max_fov, distance_factor);
    let current_fov = projection
        .as_deref_mut()
        .and_then(perspective_fov_radians)
        .map(f32::to_degrees)
        .unwrap_or(camera_settings.fov);
    let smoothed_fov = if initialized_this_frame {
        target_fov
    } else {
        current_fov.lerp(target_fov, blend)
    };
    set_perspective_fov_degrees(projection, smoothed_fov);
}

pub(super) fn update_third_person_camera(
    time: Res<Time>,
    camera_system: Res<SceneMaxCameraSystem>,
    entities: Query<(&SceneMaxEntity, &Transform)>,
    mut cameras: Query<
        (&mut Transform, &mut SceneMaxCameraModifierState),
        (With<Camera3d>, Without<SceneMaxEntity>),
    >,
) {
    if camera_system.active_cinematic.is_some() {
        return;
    }
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
    let Ok((mut camera, mut modifier_state)) = cameras.single_mut() else {
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
    modifier_state.base_look_at = Some(look_target);

    let _ = (camera_settings.fov, camera_settings.max_fov);
}

pub(super) fn update_attached_camera(
    camera_system: Res<SceneMaxCameraSystem>,
    entities: Query<(&SceneMaxEntity, &Transform)>,
    mut cameras: Query<
        (&mut Transform, &mut SceneMaxCameraModifierState),
        (With<Camera3d>, Without<SceneMaxEntity>),
    >,
) {
    if camera_system.active_cinematic.is_some() {
        return;
    }
    let Some(attachment) = camera_system.attached.as_ref() else {
        return;
    };
    let Some(target) = entities
        .iter()
        .find_map(|(entity, transform)| (entity.name == attachment.target).then_some(*transform))
    else {
        return;
    };
    let Ok((mut camera, mut modifier_state)) = cameras.single_mut() else {
        return;
    };

    let desired_translation = target.translation + target.rotation * attachment.offset;
    let look_target = target.translation + Vec3::Y * attachment.offset.y.max(1.0) * 0.35;
    camera.translation = desired_translation;
    camera.look_at(look_target, Vec3::Y);
    modifier_state.base_look_at = Some(look_target);
}

pub(super) fn restore_camera_modifier_base(
    mut cameras: Query<
        (
            &mut Transform,
            &mut SceneMaxCameraModifierState,
            Option<&mut Projection>,
        ),
        (With<Camera3d>, Without<SceneMaxEntity>),
    >,
) {
    let Ok((mut transform, mut modifier_state, projection)) = cameras.single_mut() else {
        return;
    };
    if let Some(base_transform) = modifier_state.base_transform.take() {
        *transform = base_transform;
    }
    if let Some(base_fov) = modifier_state.base_fov_radians.take()
        && let Some(mut projection) = projection
        && let Projection::Perspective(perspective) = projection.as_mut()
    {
        perspective.fov = base_fov;
    }
}

// Java modifier offsets are authored for the JME camera/world scale; convert them to Bevy's
// noticeably more sensitive camera response at the final application point.
const CAMERA_MODIFIER_POSITION_SCALE: f32 = 0.28;
const CAMERA_MODIFIER_LOOK_AT_SCALE: f32 = 0.18;
const CAMERA_MODIFIER_ROTATION_SCALE: f32 = 0.22;
const CAMERA_MODIFIER_FOV_SCALE: f32 = 0.35;

pub(super) fn update_camera_modifiers(
    time: Res<Time>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut cameras: Query<
        (
            &mut Transform,
            &mut SceneMaxCameraModifierState,
            Option<&mut Projection>,
        ),
        (With<Camera3d>, Without<SceneMaxEntity>),
    >,
) {
    let Ok((mut transform, mut modifier_state, projection)) = cameras.single_mut() else {
        return;
    };
    if camera_system.active_modifiers.is_empty() {
        return;
    }

    let base_transform = *transform;
    let mut projection = projection;
    let base_fov = projection.as_deref_mut().and_then(perspective_fov_radians);
    modifier_state.base_transform = Some(base_transform);
    modifier_state.base_fov_radians = base_fov;

    let frame = update_camera_modifier_frame(
        time.delta_secs(),
        &base_transform,
        &mut camera_system.active_modifiers,
    );
    let forward = base_transform.forward().as_vec3();
    let base_look_at = modifier_state
        .base_look_at
        .unwrap_or(base_transform.translation + forward * 16.0);
    let mut modified = base_transform;
    modified.translation =
        base_transform.translation + frame.position_offset * CAMERA_MODIFIER_POSITION_SCALE;
    let look_target = base_look_at + frame.look_at_offset * CAMERA_MODIFIER_LOOK_AT_SCALE;
    if modified.translation.distance_squared(look_target) > 0.000001 {
        modified.look_at(look_target, Vec3::Y);
    }
    modified.rotation *= Quat::from_euler(
        EulerRot::XYZ,
        (frame.rotation_degrees.x * CAMERA_MODIFIER_ROTATION_SCALE).to_radians(),
        (frame.rotation_degrees.y * CAMERA_MODIFIER_ROTATION_SCALE).to_radians(),
        (frame.rotation_degrees.z * CAMERA_MODIFIER_ROTATION_SCALE).to_radians(),
    );
    *transform = modified;

    if let (Some(base_fov), Some(mut projection)) = (base_fov, projection)
        && let Projection::Perspective(perspective) = projection.as_mut()
    {
        let fov = base_fov + (frame.fov_offset_degrees * CAMERA_MODIFIER_FOV_SCALE).to_radians();
        perspective.fov = fov.clamp(5.0_f32.to_radians(), 130.0_f32.to_radians());
    }
}

fn perspective_fov_radians(projection: &mut Projection) -> Option<f32> {
    match projection {
        Projection::Perspective(perspective) => Some(perspective.fov),
        _ => None,
    }
}

fn set_perspective_fov_degrees(projection: Option<Mut<Projection>>, fov_degrees: f32) {
    let Some(mut projection) = projection else {
        return;
    };
    let Projection::Perspective(perspective) = projection.as_mut() else {
        return;
    };
    perspective.fov = fov_degrees
        .to_radians()
        .clamp(5.0_f32.to_radians(), 130.0_f32.to_radians());
}

fn update_camera_modifier_frame(
    delta_seconds: f32,
    base_transform: &Transform,
    active_modifiers: &mut Vec<ActiveCameraModifier>,
) -> CameraModifierFrame {
    let mut frame = CameraModifierFrame::default();
    let forward = normalized_or(base_transform.forward().as_vec3(), Vec3::Z);
    let right = normalized_or(forward.cross(Vec3::Y), Vec3::X);
    let up = Vec3::Y;

    let mut index = 0;
    while index < active_modifiers.len() {
        let modifier = &mut active_modifiers[index];
        modifier.elapsed_seconds += delta_seconds;
        let duration = modifier.value.duration.max(0.01);
        let progress = (modifier.elapsed_seconds / duration).clamp(0.0, 1.0);
        let envelope = camera_modifier_envelope(&modifier.value.modifier_type, progress);
        if progress >= 1.0 && envelope <= 0.0001 {
            active_modifiers.remove(index);
            continue;
        }

        let amplitude = modifier.value.amplitude * envelope;
        let frequency = modifier.value.frequency.max(0.1);
        let time = modifier.elapsed_seconds;
        let nx = camera_modifier_signal(time, frequency, modifier.seed + 0.11);
        let ny = camera_modifier_signal(time, frequency * 1.09, modifier.seed + 1.33);
        let nz = camera_modifier_signal(time, frequency * 0.93, modifier.seed + 2.57);

        apply_camera_modifier_directional_bias(
            &modifier.value.modifier_type,
            progress,
            amplitude,
            &mut frame,
            forward,
            right,
            up,
        );

        frame.position_offset += right * nx * modifier.value.x * amplitude;
        frame.position_offset += up * ny * modifier.value.y * amplitude;
        frame.position_offset += forward * nz * modifier.value.z * amplitude;
        frame.look_at_offset += right * nx * modifier.value.x * amplitude * 0.35;
        frame.look_at_offset += up * ny * modifier.value.y * amplitude * 0.35;
        frame.rotation_degrees.x += ny * modifier.value.rx * amplitude;
        frame.rotation_degrees.y += nx * modifier.value.ry * amplitude;
        frame.rotation_degrees.z += nz * modifier.value.rz * amplitude;
        frame.fov_offset_degrees += envelope * modifier.value.fov * amplitude;
        index += 1;
    }

    frame
}

fn normalized_or(value: Vec3, fallback: Vec3) -> Vec3 {
    if value.length_squared() <= f32::EPSILON {
        fallback
    } else {
        value.normalize()
    }
}

fn camera_modifier_signal(time: f32, frequency: f32, seed: f32) -> f32 {
    (((time + seed) * frequency * std::f32::consts::TAU).sin()
        + 0.5 * ((time * 1.73 + seed * 1.91) * frequency * std::f32::consts::TAU).sin()
        + 0.25 * ((time * 0.63 + seed * 0.47) * frequency * std::f32::consts::TAU).cos())
        / 1.75
}

fn camera_modifier_envelope(modifier_type: &str, progress: f32) -> f32 {
    let p = progress.clamp(0.0, 1.0);
    match modifier_type {
        "earthquake_modifier" => {
            (0.7 + 0.3 * ((1.0 - p) * std::f32::consts::PI).sin()) * (1.0 - p).powf(0.6)
        }
        "fall_modifier" | "accelerating_modifier" | "decelerating_modifier" => {
            (p * std::f32::consts::PI).sin()
        }
        "shooting_modifier" => (1.0 - p).powf(1.8),
        _ => (1.0 - p).powf(1.25) * (0.55 + 0.45 * (p * std::f32::consts::PI).sin()),
    }
}

fn apply_camera_modifier_directional_bias(
    modifier_type: &str,
    progress: f32,
    amplitude: f32,
    frame: &mut CameraModifierFrame,
    forward: Vec3,
    right: Vec3,
    up: Vec3,
) {
    let impulse = amplitude * (1.0 - progress);
    match modifier_type {
        "hit_modifier" | "near_miss_modifier" => {
            frame.position_offset += right * 0.12 * impulse;
            frame.look_at_offset += right * 0.04 * impulse;
        }
        "fall_modifier" => {
            frame.position_offset += up * -0.08 * amplitude;
        }
        "shooting_modifier" => {
            frame.position_offset += forward * -0.05 * impulse;
        }
        "accelerating_modifier" => {
            frame.position_offset += forward * -0.08 * amplitude;
        }
        "decelerating_modifier" => {
            frame.position_offset += forward * 0.06 * amplitude;
        }
        "bump_modifier" | "landing_modifier" => {
            frame.position_offset += up * -0.1 * impulse;
        }
        "explosion_modifier" => {
            frame.position_offset += forward * -0.12 * impulse;
        }
        _ => {}
    }
}

pub(super) fn setup_camera_and_lights(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut runtime_assets: ResMut<SceneMaxRuntimeAssets>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
) {
    runtime_assets.asset_server = Some(asset_server.clone());
    runtime_assets.asset_root = context.asset_root.clone();
    runtime_assets.builtin_asset_root = context.builtin_asset_root.clone();
    runtime_assets.placeholder_mesh = Some(meshes.add(Cuboid::new(1.0, 1.0, 1.0)));
    runtime_assets.placeholder_material = Some(materials.add(Color::srgb_u8(185, 150, 65)));
    runtime_assets.audio_by_name = load_audio_index(
        &asset_server,
        context.asset_root.as_deref(),
        context.builtin_asset_root.as_deref(),
    );
    write_runtime_diagnostic_line(format!(
        "AUDIO:INDEX loaded={}",
        runtime_assets.audio_by_name.len()
    ));

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
        SceneMaxCameraModifierState::default(),
        startup_program
            .0
            .as_ref()
            .map(camera_transform_from_program)
            .unwrap_or_else(default_camera_transform),
    ));
    if let Some(program) = startup_program.0.as_ref() {
        let transform = camera_transform_from_program(program);
        let forward = transform.forward().as_vec3();
        write_runtime_diagnostic_line(format!(
            "CAMERA:START pos=({}, {}, {}) forward=({}, {}, {})",
            format_scenemax_number(transform.translation.x),
            format_scenemax_number(transform.translation.y),
            format_scenemax_number(transform.translation.z),
            format_scenemax_number(forward.x),
            format_scenemax_number(forward.y),
            format_scenemax_number(forward.z)
        ));
    }
}

pub(super) fn camera_transform_from_program(program: &Program) -> Transform {
    let mut camera_transform = default_camera_transform();
    for statement in &program.statements {
        match statement {
            Statement::CameraPosition(position) => {
                camera_transform.translation = vec3_from_scenemax(*position);
            }
            Statement::CameraRotation(rotation) => {
                camera_transform.rotation = camera_rotation_from_degrees(*rotation);
            }
            _ => {}
        }
    }
    camera_transform
}

pub(super) fn camera_rotation_from_degrees(value: SceneMaxVec3) -> Quat {
    rotation_from_degrees(value) * Quat::from_rotation_y(std::f32::consts::PI)
}

pub(super) fn default_camera_transform() -> Transform {
    Transform::from_xyz(0.0, 0.0, 10.0).looking_at(Vec3::ZERO, Vec3::Y)
}
