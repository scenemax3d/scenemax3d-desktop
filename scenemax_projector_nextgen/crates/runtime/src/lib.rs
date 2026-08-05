use std::{
    collections::{HashMap, HashSet},
    fs,
    path::{Path, PathBuf},
};

use anyhow::Result;
use bevy::{
    asset::AssetPlugin,
    gltf::Gltf,
    log::LogPlugin,
    prelude::*,
    window::{PresentMode, WindowResolution},
    winit::WinitSettings,
};
use scenemax_parser::{
    AnimationStatement, EntityOptions, KeyTrigger, MoveDirection, Program, SceneMaxVec3, Statement,
};

#[derive(Debug, Clone)]
pub struct ProjectorLaunch {
    pub script: Option<PathBuf>,
    pub project_root: Option<PathBuf>,
    pub window: WindowSettings,
}

#[derive(Debug, Clone)]
pub struct WindowSettings {
    pub width: u32,
    pub height: u32,
}

pub fn run_bevy_projector(launch: ProjectorLaunch) {
    tracing::info!(?launch, "starting SceneMax NextGen projector");
    let project_root = launch.project_root.clone();
    let script_root = launch
        .script
        .as_deref()
        .and_then(Path::parent)
        .map(Path::to_path_buf);
    let scene_program = load_startup_program(&launch);
    let asset_root = project_root
        .as_ref()
        .map(|root| root.join("resources"))
        .filter(|path| path.is_dir());

    App::new()
        .insert_resource(WinitSettings::continuous())
        .insert_resource(SceneMaxLaunchContext {
            script_root,
            asset_root: asset_root.clone(),
        })
        .insert_resource(scene_program)
        .add_plugins(
            DefaultPlugins
                .build()
                .disable::<LogPlugin>()
                .set(AssetPlugin {
                    file_path: asset_root
                        .as_ref()
                        .map(|path| path.to_string_lossy().to_string())
                        .unwrap_or_else(|| "assets".to_owned()),
                    ..default()
                })
                .set(WindowPlugin {
                    primary_window: Some(Window {
                        title: "SceneMax3D NextGen".to_owned(),
                        present_mode: PresentMode::AutoVsync,
                        resolution: WindowResolution::new(
                            launch.window.width,
                            launch.window.height,
                        )
                        .with_scale_factor_override(1.0),
                        ..default()
                    }),
                    ..default()
                }),
        )
        .add_systems(
            Startup,
            (setup_camera_and_lights, setup_scenemax_program).chain(),
        )
        .add_systems(
            Update,
            (
                switch_scene_on_key,
                apply_key_events,
                update_timed_turns,
                update_timed_moves,
                play_pending_animations,
            )
                .chain(),
        )
        .run();
}

pub fn audit_assets(project: &Path) -> Result<()> {
    let report = scenemax_assets::audit_project(project)?;
    report.print();
    Ok(())
}

#[derive(Debug, Resource)]
struct SceneMaxLaunchContext {
    script_root: Option<PathBuf>,
    asset_root: Option<PathBuf>,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxStartupProgram(Option<Program>);

#[derive(Debug, Component)]
#[allow(dead_code)]
struct SceneMaxEntity {
    name: String,
    runtime_name: String,
}

#[derive(Debug, Component)]
struct AnimationToPlay {
    clip: String,
    looped: bool,
    speed: f32,
    gltf: Handle<Gltf>,
}

#[derive(Debug, Component)]
struct SceneMaxGltf {
    gltf: Handle<Gltf>,
}

#[derive(Debug, Component)]
struct TimedTurn {
    remaining_seconds: f32,
    radians_per_second: f32,
}

#[derive(Debug, Component)]
struct TimedMove {
    remaining_seconds: f32,
    velocity: Vec3,
}

fn load_startup_program(launch: &ProjectorLaunch) -> SceneMaxStartupProgram {
    let Some(script_path) = launch
        .script
        .clone()
        .or_else(|| default_script_path(launch.project_root.as_deref()))
    else {
        tracing::info!("no SceneMax script was supplied; using placeholder scene");
        return SceneMaxStartupProgram::default();
    };

    match load_scene_entry_program(&script_path) {
        Ok(program) => {
            tracing::info!(
                path = %script_path.display(),
                statements = program.statements.len(),
                "loaded SceneMax startup script graph"
            );
            SceneMaxStartupProgram(Some(program))
        }
        Err(error) => {
            tracing::error!(
                path = %script_path.display(),
                %error,
                "failed to load SceneMax startup script graph"
            );
            SceneMaxStartupProgram::default()
        }
    }
}

fn load_scene_entry_program(script_path: &Path) -> Result<Program> {
    let root_program = load_script_with_adds(script_path, &mut HashSet::new())?;
    if has_scene_content(&root_program) {
        return Ok(root_program);
    }

    if let Some(scene) = root_program
        .statements
        .iter()
        .find_map(|statement| match statement {
            Statement::SwitchTo { scene } => Some(scene),
            _ => None,
        })
    {
        let scene_main = script_path
            .parent()
            .unwrap_or_else(|| Path::new("."))
            .join(scene)
            .join("main");
        tracing::info!(
            scene,
            path = %scene_main.display(),
            "startup script switches to scene"
        );
        return load_script_with_adds(&scene_main, &mut HashSet::new());
    }

    Ok(root_program)
}

fn load_script_with_adds(script_path: &Path, visited: &mut HashSet<PathBuf>) -> Result<Program> {
    let script_path = normalize_script_path(script_path);
    if !visited.insert(script_path.clone()) {
        tracing::warn!(path = %script_path.display(), "skipping recursive Add Code include");
        return Ok(Program {
            statements: Vec::new(),
        });
    }

    let source = fs::read_to_string(&script_path)?;
    let parsed = scenemax_parser::parse_program(&source)?;
    let mut statements = Vec::new();
    let script_dir = script_path.parent().unwrap_or_else(|| Path::new("."));

    for statement in parsed.statements {
        match statement {
            Statement::AddCode { path } => {
                let include_path = resolve_code_path(script_dir, &path);
                tracing::info!(
                    path,
                    resolved = %include_path.display(),
                    "loading Add Code include"
                );
                match load_script_with_adds(&include_path, visited) {
                    Ok(program) => statements.extend(program.statements),
                    Err(error) => tracing::warn!(
                        path = %include_path.display(),
                        %error,
                        "failed to load Add Code include"
                    ),
                }
            }
            statement => statements.push(statement),
        }
    }

    Ok(Program { statements })
}

fn has_scene_content(program: &Program) -> bool {
    program.statements.iter().any(|statement| {
        matches!(
            statement,
            Statement::ModelDecl { .. }
                | Statement::CameraPosition(_)
                | Statement::CameraRotation(_)
        )
    })
}

fn normalize_script_path(path: &Path) -> PathBuf {
    if path.is_file() {
        return path.to_path_buf();
    }
    let code_path = path.with_extension("code");
    if code_path.is_file() {
        return code_path;
    }
    path.to_path_buf()
}

fn resolve_code_path(script_dir: &Path, path: &str) -> PathBuf {
    let relative = path
        .trim_start_matches('/')
        .replace('/', std::path::MAIN_SEPARATOR_STR);
    normalize_script_path(&script_dir.join(relative))
}

fn default_script_path(project_root: Option<&Path>) -> Option<PathBuf> {
    let root = project_root?;
    [
        root.join("running").join("main"),
        root.join("running").join("main.code"),
        root.join("main"),
        root.join("main.code"),
    ]
    .into_iter()
    .find(|path| path.is_file())
}

fn setup_scenemax_program(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        setup_placeholder_model(commands, meshes, materials);
        return;
    };

    let Some(asset_root) = context.asset_root.as_ref() else {
        tracing::error!("SceneMax NextGen requires a project root with a resources folder");
        setup_placeholder_model(commands, meshes, materials);
        return;
    };

    spawn_scenemax_program(
        &mut commands,
        &asset_server,
        asset_root,
        program,
        &mut meshes,
        &mut materials,
    );
}

fn spawn_scenemax_program(
    commands: &mut Commands,
    asset_server: &AssetServer,
    asset_root: &Path,
    program: &Program,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
) {
    let animations_by_target = collect_animations_by_target(program);
    let visibility_by_target = collect_visibility_by_target(program);
    let turn_by_target = collect_turn_by_target(program);
    let mut spawned_any = false;
    let mut entities_by_name = HashMap::new();
    let mut transforms_by_name = HashMap::new();

    for statement in &program.statements {
        let Statement::ModelDecl {
            name,
            resource,
            options,
        } = statement
        else {
            continue;
        };

        if let Some(primitive) = primitive_mesh(resource, meshes, materials) {
            let transform = primitive_transform_from_options(options);
            let entity = commands
                .spawn((
                    SceneMaxEntity {
                        name: name.clone(),
                        runtime_name: format!("{name}@1"),
                    },
                    primitive.0,
                    primitive.1,
                    transform,
                    initial_visibility(name, options, &visibility_by_target),
                ))
                .id();
            entities_by_name.insert(name.clone(), entity);
            transforms_by_name.insert(name.clone(), transform);
            spawned_any = true;
            tracing::info!(name, resource, "spawned SceneMax primitive");
            continue;
        }

        match scenemax_assets::resolve_model_resource(asset_root, resource) {
            Ok(model) => {
                let runtime_name = format!("{name}@1");
                let asset_path = model.asset_path;
                let gltf: Handle<Gltf> = asset_server.load(asset_path.clone());
                let scene = WorldAssetRoot(
                    asset_server.load(GltfAssetLabel::Scene(0).from_asset(asset_path.clone())),
                );
                let transform = transform_from_options(options, model.scale);
                let entity_id = commands
                    .spawn((
                        SceneMaxEntity {
                            name: name.clone(),
                            runtime_name,
                        },
                        SceneMaxGltf { gltf: gltf.clone() },
                        scene,
                        transform,
                        initial_visibility(name, options, &visibility_by_target),
                    ))
                    .id();

                if let Some(animation) = animations_by_target.get(name) {
                    commands.entity(entity_id).insert(AnimationToPlay {
                        clip: animation.clip.clone(),
                        looped: animation.looped,
                        speed: animation.speed,
                        gltf,
                    });
                }

                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                spawned_any = true;
                tracing::info!(
                    name,
                    resource,
                    path = %asset_path,
                    "spawned SceneMax GLTF model"
                );
            }
            Err(error) => {
                tracing::error!(name, resource, %error, "failed to resolve SceneMax model");
            }
        }
    }

    apply_look_at_commands(
        program,
        commands,
        &entities_by_name,
        &mut transforms_by_name,
    );
    for (target, turn) in turn_by_target {
        if let Some(entity) = entities_by_name.get(&target) {
            commands
                .entity(*entity)
                .insert(timed_turn_from_statement(&turn));
        }
    }

    if !spawned_any {
        spawn_placeholder_model(commands, meshes, materials);
    }
}

fn primitive_mesh(
    resource: &str,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
) -> Option<(Mesh3d, MeshMaterial3d<StandardMaterial>)> {
    let mesh = match resource.to_ascii_lowercase().as_str() {
        "box" | "quad" => meshes.add(Cuboid::new(1.0, 1.0, 1.0)),
        "sphere" => meshes.add(Sphere::new(1.0)),
        _ => return None,
    };
    let material = materials.add(Color::srgb_u8(120, 135, 150));
    Some((Mesh3d(mesh), MeshMaterial3d(material)))
}

fn initial_visibility(
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

fn primitive_transform_from_options(options: &EntityOptions) -> Transform {
    let mut transform = transform_from_options(options, None);
    if options.scale.is_none() {
        if let Some(size) = options.size {
            transform.scale = vec3_from_scenemax(size);
        }
    }
    transform
}

fn apply_look_at_commands(
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
            transforms_by_name.get(subject).copied(),
        ) else {
            continue;
        };
        let mut updated = target_transform;
        updated.look_at(subject_transform.translation, Vec3::Y);
        commands.entity(entity).insert(updated);
        transforms_by_name.insert(target.clone(), updated);
    }
}

fn switch_scene_on_key(
    keyboard: Res<ButtonInput<KeyCode>>,
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    mut startup_program: ResMut<SceneMaxStartupProgram>,
    scene_entities: Query<Entity, With<SceneMaxEntity>>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    mut cameras: Query<&mut Transform, With<Camera3d>>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        return;
    };
    let Some(scene) = pending_key_switch(program, &keyboard).map(str::to_owned) else {
        return;
    };
    let Some(script_root) = context.script_root.as_ref() else {
        tracing::warn!(scene, "cannot switch scene without a startup script root");
        return;
    };
    let Some(asset_root) = context.asset_root.as_ref() else {
        tracing::warn!(scene, "cannot switch scene without an asset root");
        return;
    };

    let scene_main = script_root.join(&scene).join("main");
    match load_script_with_adds(&scene_main, &mut HashSet::new()) {
        Ok(program) => {
            for entity in &scene_entities {
                commands.entity(entity).despawn();
            }

            if let Ok(mut camera) = cameras.single_mut() {
                *camera = camera_transform_from_program(&program);
            }

            spawn_scenemax_program(
                &mut commands,
                &asset_server,
                asset_root,
                &program,
                &mut meshes,
                &mut materials,
            );
            startup_program.0 = Some(program);
            tracing::info!(scene, path = %scene_main.display(), "switched SceneMax scene");
        }
        Err(error) => tracing::error!(
            scene,
            path = %scene_main.display(),
            %error,
            "failed to switch SceneMax scene"
        ),
    }
}

fn apply_key_events(
    keyboard: Res<ButtonInput<KeyCode>>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&mut Visibility>,
        )>,
    )>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        return;
    };
    if pending_key_switch(program, &keyboard).is_some() {
        return;
    }

    let transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();

    for statement in &program.statements {
        let Statement::KeyEvent(event) = statement else {
            continue;
        };
        if !key_event_matches(&event.key, event.trigger, &keyboard) {
            continue;
        }

        for action in &event.actions {
            apply_key_action(
                action,
                &transforms_by_name,
                &mut commands,
                &mut scene_entities,
            );
        }
    }
}

fn apply_key_action(
    action: &Statement,
    transforms_by_name: &HashMap<String, Transform>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&mut Visibility>,
        )>,
    )>,
) {
    for (entity, scene_entity, mut transform, gltf, visibility) in &mut scene_entities.p1() {
        match action {
            Statement::Animate(animation) if animation.target == scene_entity.name => {
                if let Some(gltf) = gltf {
                    commands.entity(entity).insert(AnimationToPlay {
                        clip: animation.clip.clone(),
                        looped: animation.looped,
                        speed: animation.speed,
                        gltf: gltf.gltf.clone(),
                    });
                }
            }
            Statement::LookAt { target, subject } if target == &scene_entity.name => {
                if let Some(subject_transform) = transforms_by_name.get(subject) {
                    transform.look_at(subject_transform.translation, Vec3::Y);
                }
            }
            Statement::Turn(turn) if turn.target == scene_entity.name => {
                commands
                    .entity(entity)
                    .insert(timed_turn_from_statement(turn));
            }
            Statement::Move(movement) if movement.target == scene_entity.name => {
                commands
                    .entity(entity)
                    .insert(timed_move_from_statement(movement, &transform));
            }
            Statement::Visibility { target, visible } if target == &scene_entity.name => {
                if let Some(mut visibility) = visibility {
                    *visibility = if *visible {
                        Visibility::Inherited
                    } else {
                        Visibility::Hidden
                    };
                } else {
                    commands.entity(entity).insert(if *visible {
                        Visibility::Inherited
                    } else {
                        Visibility::Hidden
                    });
                }
            }
            _ => {}
        }
    }
}

fn pending_key_switch<'a>(
    program: &'a Program,
    keyboard: &ButtonInput<KeyCode>,
) -> Option<&'a str> {
    let mut saw_pressed_key = false;
    for statement in &program.statements {
        match statement {
            Statement::WaitForKey { key } if is_pressed_key(key, keyboard) => {
                saw_pressed_key = true;
            }
            Statement::SwitchTo { scene } if saw_pressed_key => return Some(scene),
            _ => {}
        }
    }
    None
}

fn is_pressed_key(key: &str, keyboard: &ButtonInput<KeyCode>) -> bool {
    let Some(key_code) = key_code_from_scenemax(key) else {
        return false;
    };
    keyboard.just_pressed(key_code)
}

fn key_event_matches(key: &str, trigger: KeyTrigger, keyboard: &ButtonInput<KeyCode>) -> bool {
    let Some(key_code) = key_code_from_scenemax(key) else {
        return false;
    };
    match trigger {
        KeyTrigger::Pressed => keyboard.pressed(key_code),
        KeyTrigger::PressedOnce => keyboard.just_pressed(key_code),
        KeyTrigger::Released => keyboard.just_released(key_code),
    }
}

fn key_code_from_scenemax(key: &str) -> Option<KeyCode> {
    match key.to_ascii_lowercase().as_str() {
        "space" => Some(KeyCode::Space),
        "enter" | "return" => Some(KeyCode::Enter),
        "up" => Some(KeyCode::ArrowUp),
        "down" => Some(KeyCode::ArrowDown),
        "left" => Some(KeyCode::ArrowLeft),
        "right" => Some(KeyCode::ArrowRight),
        "a" => Some(KeyCode::KeyA),
        "b" => Some(KeyCode::KeyB),
        "c" => Some(KeyCode::KeyC),
        "d" => Some(KeyCode::KeyD),
        "q" => Some(KeyCode::KeyQ),
        "s" => Some(KeyCode::KeyS),
        "w" => Some(KeyCode::KeyW),
        "x" => Some(KeyCode::KeyX),
        "z" => Some(KeyCode::KeyZ),
        _ => None,
    }
}

fn transform_from_options(options: &EntityOptions, asset_scale: Option<[f32; 3]>) -> Transform {
    let translation = options
        .position
        .map(vec3_from_scenemax)
        .unwrap_or(Vec3::ZERO);
    let scale = options
        .scale
        .map(vec3_from_scenemax)
        .or_else(|| asset_scale.map(|scale| Vec3::new(scale[0], scale[1], scale[2])))
        .unwrap_or(Vec3::ONE);
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

fn timed_turn_from_statement(turn: &scenemax_parser::TurnStatement) -> TimedTurn {
    let duration = turn.duration_seconds.max(0.001);
    TimedTurn {
        remaining_seconds: duration,
        radians_per_second: turn.degrees.to_radians() / duration,
    }
}

fn timed_move_from_statement(
    movement: &scenemax_parser::MoveStatement,
    transform: &Transform,
) -> TimedMove {
    let duration = movement.duration_seconds.max(0.001);
    let mut direction = transform.rotation * Vec3::NEG_Z;
    direction.y = 0.0;
    if direction.length_squared() <= f32::EPSILON {
        direction = Vec3::NEG_Z;
    }
    direction = direction.normalize();
    if movement.direction == MoveDirection::Backward {
        direction = -direction;
    }

    TimedMove {
        remaining_seconds: duration,
        velocity: direction * (movement.distance / duration),
    }
}

fn update_timed_turns(
    time: Res<Time>,
    mut commands: Commands,
    mut turns: Query<(Entity, &mut Transform, &mut TimedTurn)>,
) {
    for (entity, mut transform, mut turn) in &mut turns {
        let delta = time.delta_secs().min(turn.remaining_seconds);
        transform.rotate_y(turn.radians_per_second * delta);
        turn.remaining_seconds -= delta;
        if turn.remaining_seconds <= 0.0 {
            commands.entity(entity).remove::<TimedTurn>();
        }
    }
}

fn update_timed_moves(
    time: Res<Time>,
    mut commands: Commands,
    mut moves: Query<(Entity, &mut Transform, &mut TimedMove)>,
) {
    for (entity, mut transform, mut movement) in &mut moves {
        let delta = time.delta_secs().min(movement.remaining_seconds);
        transform.translation += movement.velocity * delta;
        movement.remaining_seconds -= delta;
        if movement.remaining_seconds <= 0.0 {
            commands.entity(entity).remove::<TimedMove>();
        }
    }
}

fn vec3_from_scenemax(value: SceneMaxVec3) -> Vec3 {
    Vec3::new(value.x, value.y, value.z)
}

fn rotation_from_degrees(value: SceneMaxVec3) -> Quat {
    Quat::from_euler(
        EulerRot::XYZ,
        value.x.to_radians(),
        value.y.to_radians(),
        value.z.to_radians(),
    )
}

fn collect_animations_by_target(program: &Program) -> HashMap<String, AnimationStatement> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::Animate(animation) => Some((animation.target.clone(), animation.clone())),
            _ => None,
        })
        .collect()
}

fn collect_visibility_by_target(program: &Program) -> HashMap<String, bool> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::Visibility { target, visible } => Some((target.clone(), *visible)),
            _ => None,
        })
        .collect()
}

fn collect_turn_by_target(program: &Program) -> HashMap<String, scenemax_parser::TurnStatement> {
    program
        .statements
        .iter()
        .filter_map(|statement| match statement {
            Statement::Turn(turn) => Some((turn.target.clone(), turn.clone())),
            _ => None,
        })
        .collect()
}

fn play_pending_animations(
    mut commands: Commands,
    children: Query<&Children>,
    animations_to_play: Query<(Entity, &AnimationToPlay)>,
    gltfs: Res<Assets<Gltf>>,
    mut graphs: ResMut<Assets<AnimationGraph>>,
    mut players: Query<&mut AnimationPlayer>,
) {
    for (root, animation_to_play) in &animations_to_play {
        let Some(gltf) = gltfs.get(&animation_to_play.gltf) else {
            continue;
        };

        let Some(clip) = gltf
            .named_animations
            .get(animation_to_play.clip.as_str())
            .or_else(|| gltf.animations.first())
        else {
            tracing::warn!(clip = %animation_to_play.clip, "GLTF model has no animation clips");
            commands.entity(root).remove::<AnimationToPlay>();
            continue;
        };

        let mut animation_players = Vec::new();
        for child in children.iter_descendants(root) {
            if players.get(child).is_ok() {
                animation_players.push(child);
            }
        }

        if animation_players.is_empty() {
            continue;
        }

        let (graph, index) = AnimationGraph::from_clip(clip.clone());
        let graph_handle = graphs.add(graph);

        for child in animation_players {
            if let Ok(mut player) = players.get_mut(child) {
                let active = player.start(index).set_speed(animation_to_play.speed);
                if animation_to_play.looped {
                    active.repeat();
                }
            }
            commands
                .entity(child)
                .insert(AnimationGraphHandle(graph_handle.clone()));
        }

        commands.entity(root).remove::<AnimationToPlay>();
    }
}

fn setup_camera_and_lights(mut commands: Commands, startup_program: Res<SceneMaxStartupProgram>) {
    commands.insert_resource(GlobalAmbientLight {
        color: Color::WHITE,
        brightness: 800.0,
        ..default()
    });

    commands.spawn((
        PointLight {
            intensity: 4_000.0,
            shadow_maps_enabled: true,
            ..default()
        },
        Transform::from_xyz(4.0, 6.0, 4.0),
    ));

    commands.spawn((
        Camera3d::default(),
        startup_program
            .0
            .as_ref()
            .map(camera_transform_from_program)
            .unwrap_or_else(default_camera_transform),
    ));
}

fn camera_transform_from_program(program: &Program) -> Transform {
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

fn default_camera_transform() -> Transform {
    Transform::from_xyz(-3.0, 3.0, 7.0).looking_at(Vec3::ZERO, Vec3::Y)
}

fn setup_placeholder_model(
    mut commands: Commands,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
) {
    spawn_placeholder_model(&mut commands, &mut meshes, &mut materials);
}

fn spawn_placeholder_model(
    commands: &mut Commands,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
) {
    commands.spawn((
        Mesh3d(meshes.add(Cuboid::new(1.5, 1.5, 1.5))),
        MeshMaterial3d(materials.add(Color::srgb_u8(74, 144, 226))),
        Transform::from_xyz(0.0, 0.75, 0.0),
    ));

    tracing::info!("spawned placeholder cube");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn collects_last_animation_for_target() {
        let program = scenemax_parser::parse_program("d=>dragon\nd.fly\nd.idle loop").unwrap();
        let animations = collect_animations_by_target(&program);

        assert_eq!(
            animations.get("d"),
            Some(&AnimationStatement {
                target: "d".to_owned(),
                clip: "idle".to_owned(),
                speed: 1.0,
                looped: true,
            })
        );
    }

    #[test]
    fn detects_space_switch_after_wait_statement() {
        let program = scenemax_parser::parse_program(
            "run show_game_intro_ui async\nwait for key space to be pressed\nswitch to \"game_level1\"",
        )
        .unwrap();
        let mut keyboard = ButtonInput::<KeyCode>::default();
        keyboard.press(KeyCode::Space);

        assert_eq!(pending_key_switch(&program, &keyboard), Some("game_level1"));
    }
}
