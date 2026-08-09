use std::{
    collections::{BTreeMap, HashMap, HashSet},
    env, fs,
    io::Write,
    path::{Path, PathBuf},
    sync::Mutex,
};

use anyhow::Result;
use avian3d::{
    prelude::{
        AngularVelocity, Collider as AvianCollider, CollisionEnd, CollisionEventsEnabled,
        CollisionLayers, CollisionStart, LinearVelocity, LockedAxes, PhysicsPlugins,
        RigidBody as AvianRigidBody, Sensor,
    },
    schedule::PhysicsSchedule,
};
use bevy::{
    animation::AnimationTargetId,
    asset::{AssetApp, AssetPlugin, io::AssetSourceBuilder},
    gltf::Gltf,
    log::LogPlugin,
    prelude::*,
    ui::IsDefaultUiCamera,
    window::{PresentMode, WindowResolution},
    winit::WinitSettings,
};
use bevy_tnua::{
    TnuaUserControlsSystems,
    builtins::{TnuaBuiltinJump, TnuaBuiltinJumpConfig, TnuaBuiltinWalk, TnuaBuiltinWalkConfig},
    prelude::{TnuaConfig, TnuaController, TnuaControllerPlugin, TnuaScheme},
};
use bevy_tnua_avian3d::prelude::{TnuaAvian3dPlugin, TnuaAvian3dSensorShape};
use scenemax_parser::{
    AnimationSpeedStatement, AnimationStatement, AssignmentValue, AttachStatement,
    CameraAttachStatement, CharacterJumpStatement, CharacterModeStatement, Condition,
    EntityOptions, KeyTrigger, LoggerLevel, LoggerMessage, LoggerStatement, MoveDirection,
    ObjectPoolStatement, PoolReleaseStatement, PositionExpr, PositionStatement, PositionValue,
    Program, SceneMaxAxis, SceneMaxBodyKind, SceneMaxCollisionShape, SceneMaxVec3, Statement,
    UiEaseDirection, UiTargetPath,
};
use scenemax_runtime_script_core::{
    FunctionRuntime, actions_with_parent_continuation, animation_candidate_score,
    animation_name_matches, collect_animations_by_target, collect_attaches_by_target,
    collect_functions_by_name, collect_guards_by_name, collect_turn_by_target,
    collect_visibility_by_target, instantiate_function_actions, normalized_animation_name,
    repeat_actions, requested_animation_names_match, substitute_function_condition,
};
use scenemax_runtime_ui_core::{
    SceneMaxSpriteAsset, SceneMaxUiDocument, SceneMaxUiWidgetDef, UiLayoutRect, document_scale,
    list_view_text, percent, scaled_font_size, solve_widget_layout, sorted_widgets, target_key,
};
use scenemax_runtime_vm_core::{SceneMaxScopeFrame, SceneMaxVars, SceneMaxVmSpatial};

#[derive(TnuaScheme)]
#[scheme(basis = TnuaBuiltinWalk)]
enum SceneMaxControlScheme {
    Jump(TnuaBuiltinJump),
}

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
    let builtin_asset_root = find_builtin_resources_root(
        project_root.as_deref(),
        script_root.as_deref(),
        asset_root.as_deref(),
    );
    initialize_runtime_logger(project_root.as_deref(), script_root.as_deref());

    let mut app = App::new();
    if let Some(builtin_asset_root) = builtin_asset_root.as_ref() {
        let source_path = builtin_asset_root.to_string_lossy().to_string();
        app.register_asset_source(
            "builtin",
            AssetSourceBuilder::platform_default(&source_path, None),
        );
        tracing::info!(
            path = %builtin_asset_root.display(),
            "registered SceneMax built-in asset source"
        );
        write_runtime_diagnostic_line(format!(
            "registered built-in resources at {}",
            builtin_asset_root.display()
        ));
    } else {
        write_runtime_diagnostic_line("no separate built-in resources folder was discovered");
    }

    app.insert_resource(WinitSettings::continuous())
        .insert_resource(SceneMaxLaunchContext {
            script_root,
            asset_root: asset_root.clone(),
            builtin_asset_root,
            window_width: launch.window.width,
            window_height: launch.window.height,
        })
        .insert_resource(scene_program)
        .init_resource::<SceneMaxVars>()
        .init_resource::<SceneMaxObjectPools>()
        .init_resource::<SceneMaxCameraSystem>()
        .init_resource::<SceneMaxRuntimeAssets>()
        .init_resource::<SceneMaxAnimationDurations>()
        .init_resource::<DelayedActionQueue>()
        .init_resource::<RecurringRunTimers>()
        .init_resource::<ActiveCollisionEvents>()
        .init_resource::<ActiveActionControllers>()
        .init_resource::<SceneMaxPhysicsContacts>()
        .init_resource::<SceneMaxColliderBounds>()
        .init_resource::<SceneMaxUiRuntime>()
        .init_resource::<SceneMaxUiActionQueue>()
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
        .add_plugins((
            PhysicsPlugins::default(),
            TnuaControllerPlugin::<SceneMaxControlScheme>::new(PhysicsSchedule),
            TnuaAvian3dPlugin::new(PhysicsSchedule),
        ))
        .add_systems(
            Startup,
            (setup_camera_and_lights, setup_scenemax_program).chain(),
        )
        .add_systems(
            PhysicsSchedule,
            feed_tnua_character_controllers.in_set(TnuaUserControlsSystems),
        )
        .add_systems(
            Update,
            (
                switch_scene_on_key,
                update_recurring_runs,
                update_delayed_actions,
                apply_pending_character_modes,
                cleanup_character_supports,
                update_avian_collision_contacts,
                apply_key_events,
                update_virtual_colliders,
                update_current_animation_vars,
                apply_when_events,
            )
                .chain(),
        )
        .add_systems(
            Update,
            (
                apply_builtin_navigation_controls,
                update_timed_turns,
                update_timed_moves,
                update_timed_jumps,
                update_fighting_camera,
                update_third_person_camera,
                update_attached_camera,
                restore_default_idle_animations,
                play_pending_animations,
                apply_animation_speed_overrides,
            )
                .chain(),
        )
        .add_systems(
            Update,
            (
                apply_scenemax_ui_actions,
                update_scenemax_ui_eases,
                update_scenemax_ui_message_animations,
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

pub fn resolve_model_asset_for_project(project: &Path, model_name: &str) -> Result<String> {
    let asset_root = project.join("resources");
    let builtin_asset_root = find_builtin_resources_root(Some(project), None, Some(&asset_root));
    let model = scenemax_assets::resolve_model_resource_with_builtin_fallback(
        &asset_root,
        builtin_asset_root.as_deref(),
        model_name,
    )
    .map_err(anyhow::Error::from)?;
    Ok(model.asset_path)
}

fn find_builtin_resources_root(
    project_root: Option<&Path>,
    script_root: Option<&Path>,
    active_asset_root: Option<&Path>,
) -> Option<PathBuf> {
    let active_asset_root = active_asset_root.and_then(canonicalize_existing);
    let mut candidates = Vec::new();

    if let Ok(path) = env::var("SCENEMAX_BUILTIN_RESOURCES") {
        candidates.push(PathBuf::from(path));
    }
    if let Ok(current_dir) = env::current_dir() {
        add_resources_ancestor_candidates(&current_dir, &mut candidates);
    }
    if let Ok(current_exe) = env::current_exe()
        && let Some(parent) = current_exe.parent()
    {
        add_resources_ancestor_candidates(parent, &mut candidates);
    }
    if let Some(project_root) = project_root {
        add_resources_ancestor_candidates(project_root, &mut candidates);
    }
    if let Some(script_root) = script_root {
        add_resources_ancestor_candidates(script_root, &mut candidates);
    }

    let mut seen = HashSet::new();
    candidates.into_iter().find(|candidate| {
        let Some(canonical) = canonicalize_existing(candidate) else {
            return false;
        };
        if !seen.insert(canonical.clone()) {
            return false;
        }
        if active_asset_root.as_ref() == Some(&canonical) {
            return false;
        }
        canonical.join("Models").is_dir() || canonical.join("models").is_dir()
    })
}

fn add_resources_ancestor_candidates(base: &Path, candidates: &mut Vec<PathBuf>) {
    for ancestor in base.ancestors() {
        candidates.push(ancestor.join("resources"));
    }
}

fn canonicalize_existing(path: impl AsRef<Path>) -> Option<PathBuf> {
    path.as_ref().canonicalize().ok()
}

#[derive(Debug, Resource)]
struct SceneMaxLaunchContext {
    script_root: Option<PathBuf>,
    asset_root: Option<PathBuf>,
    builtin_asset_root: Option<PathBuf>,
    window_width: u32,
    window_height: u32,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxStartupProgram(Option<Program>);

#[derive(Debug, Resource, Default)]
struct SceneMaxObjectPools {
    aliases: HashMap<String, String>,
    pools: HashMap<String, ObjectPoolRuntime>,
}

#[derive(Debug, Default)]
struct ObjectPoolRuntime {
    available: Vec<String>,
    in_use: HashSet<String>,
    members: HashSet<String>,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxCameraSystem {
    fighting: Option<FightingCameraRuntime>,
    third_person: HashMap<String, ThirdPersonCameraRuntime>,
    selected: Option<String>,
    attached: Option<CameraAttachmentRuntime>,
}

#[derive(Debug, Resource, Default)]
struct DelayedActionQueue {
    actions: Vec<DelayedActions>,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
enum SceneMaxControllerKey {
    When(usize),
    Recurring(usize),
}

#[derive(Debug, Resource, Default)]
struct ActiveActionControllers {
    running: HashSet<SceneMaxControllerKey>,
}

#[derive(Debug, Resource, Default)]
struct RecurringRunTimers {
    remaining_by_statement: HashMap<usize, f32>,
}

#[derive(Debug, Resource, Default)]
struct ActiveCollisionEvents {
    active_by_statement: HashSet<usize>,
    transition_armed_by_statement: HashSet<usize>,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxPhysicsContacts {
    active_pairs: HashSet<(String, String)>,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxColliderBounds {
    radius_by_name: HashMap<String, f32>,
    shape_by_name: HashMap<String, ColliderBoundShape>,
}

#[derive(Debug, Clone, Copy)]
enum ColliderBoundShape {
    Box { half_extents: Vec3 },
    Sphere { radius: f32 },
    Capsule { radius: f32, half_height: f32 },
}

impl ColliderBoundShape {
    fn bounding_radius(self) -> f32 {
        match self {
            ColliderBoundShape::Box { half_extents } => half_extents.length(),
            ColliderBoundShape::Sphere { radius } => radius,
            ColliderBoundShape::Capsule {
                radius,
                half_height,
            } => radius + half_height,
        }
    }
}

impl SceneMaxColliderBounds {
    fn clear(&mut self) {
        self.radius_by_name.clear();
        self.shape_by_name.clear();
    }
}

#[derive(Debug, Resource, Default)]
struct SceneMaxRuntimeAssets {
    placeholder_mesh: Option<Handle<Mesh>>,
    placeholder_material: Option<Handle<StandardMaterial>>,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxAnimationDurations {
    by_target_clip: HashMap<(String, String), f32>,
    by_clip: HashMap<String, f32>,
}

impl SceneMaxAnimationDurations {
    fn insert(&mut self, target: &str, requested_clip: &str, resolved_clip: &str, duration: f32) {
        let duration = duration.max(0.001);
        self.by_target_clip.insert(
            (target.to_owned(), normalized_animation_name(requested_clip)),
            duration,
        );
        self.by_target_clip.insert(
            (target.to_owned(), normalized_animation_name(resolved_clip)),
            duration,
        );
        self.by_clip
            .insert(normalized_animation_name(requested_clip), duration);
        self.by_clip
            .insert(normalized_animation_name(resolved_clip), duration);
    }

    fn lookup(&self, target: &str, requested_clip: &str) -> Option<f32> {
        let clip_key = normalized_animation_name(requested_clip);
        self.by_target_clip
            .get(&(target.to_owned(), clip_key.clone()))
            .copied()
            .or_else(|| self.by_clip.get(&clip_key).copied())
    }
}

#[derive(Debug, Resource, Default)]
struct SceneMaxUiRuntime {
    active_ui_name: Option<String>,
    loaded: HashMap<String, LoadedSceneMaxUi>,
    sprite_index: HashMap<String, SceneMaxSpriteAsset>,
    sprite_index_root: Option<PathBuf>,
}

#[derive(Debug, Default)]
struct LoadedSceneMaxUi {
    root_entities: Vec<Entity>,
    layer_entities: HashMap<String, Entity>,
    targets: HashMap<String, SceneMaxUiTarget>,
}

#[derive(Debug, Clone)]
struct SceneMaxUiTarget {
    entity: Entity,
    text_entity: Option<Entity>,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxUiActionQueue {
    actions: Vec<SceneMaxUiAction>,
}

#[derive(Debug, Clone)]
enum SceneMaxUiAction {
    Load {
        name: String,
    },
    ShowHide {
        target: UiTargetPath,
        visible: bool,
    },
    Message {
        target: UiTargetPath,
        text: String,
        effects: String,
        duration_seconds: f32,
    },
    Ease {
        target: UiTargetPath,
        easing: String,
        direction: UiEaseDirection,
        duration_seconds: f32,
    },
    SetProperty {
        target: UiTargetPath,
        property: String,
        value: String,
    },
}

#[allow(dead_code)]
#[derive(Debug, Clone, Component)]
struct SceneMaxUiWidget {
    ui_name: String,
    layer: String,
    widget_path: Vec<String>,
}

#[derive(Debug, Clone, Component)]
struct SceneMaxUiEase {
    start: Vec2,
    elapsed_seconds: f32,
    duration_seconds: f32,
    easing: String,
}

#[derive(Debug, Clone, Component)]
struct SceneMaxUiMessageAnimation {
    full_text: String,
    effect_names: Vec<String>,
    elapsed_seconds: f32,
    duration_seconds: f32,
    base_color: Color,
    base_transform: UiTransform,
}

#[derive(Debug, Clone, Copy, Component)]
struct SceneMaxUiTextVisualState {
    base_color: Color,
    base_transform: UiTransform,
}

#[derive(Debug)]
struct DelayedActions {
    remaining_seconds: f32,
    actions: Vec<Statement>,
    owner: Option<SceneMaxControllerKey>,
    scope: Option<SceneMaxScopeFrame>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ActionSequenceResult {
    Completed,
    Returned,
    Suspended,
}

impl ActionSequenceResult {
    fn is_suspended(self) -> bool {
        matches!(self, Self::Suspended)
    }

    fn should_stop_parent(self) -> bool {
        matches!(self, Self::Returned | Self::Suspended)
    }
}

#[derive(Debug, Clone)]
struct FightingCameraRuntime {
    name: String,
    target_a: String,
    target_b: String,
    depth: f32,
    height: f32,
    side: f32,
    min_distance: f32,
    max_distance: f32,
    damping: f32,
}

#[derive(Debug, Clone)]
struct ThirdPersonCameraRuntime {
    name: String,
    target: String,
    distance: f32,
    height: f32,
    side: f32,
    look_ahead: f32,
    damping: f32,
    fov: f32,
    max_fov: f32,
}

#[derive(Debug, Clone)]
struct CameraAttachmentRuntime {
    target: String,
    offset: Vec3,
}

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
struct AnimationSpeedOverride {
    speed: f32,
    remaining_seconds: Option<f32>,
    applied: bool,
}

#[derive(Debug, Component)]
struct SceneMaxGltf {
    gltf: Handle<Gltf>,
}

#[derive(Debug, Component)]
struct CurrentAnimation {
    clip: String,
    looped: bool,
    speed: f32,
    elapsed_seconds: f32,
    duration_seconds: f32,
}

#[derive(Debug, Component)]
struct TimedTurn {
    remaining_seconds: f32,
    duration_seconds: f32,
    radians_per_second: f32,
    loop_condition: Option<Condition>,
}

#[derive(Debug, Component)]
struct TimedMove {
    remaining_seconds: f32,
    duration_seconds: f32,
    velocity: Vec3,
    final_translation: Option<Vec3>,
    loop_condition: Option<Condition>,
}

#[derive(Debug, Component)]
struct TimedMoves {
    moves: Vec<TimedMove>,
}

#[derive(Debug, Component)]
struct TimedJump {
    elapsed_seconds: f32,
    duration_seconds: f32,
    start_y: f32,
    height: f32,
}

#[derive(Debug, Component)]
struct SceneMaxCharacterController {
    move_speed: f32,
    gravity: f32,
}

#[derive(Debug, Component, Default)]
struct SceneMaxCharacterMotor {
    desired_motion: Vec3,
    motion_ttl_seconds: f32,
    timed_motion: Vec3,
    timed_motion_remaining_seconds: f32,
    jump_hold_seconds: f32,
    pending_jump_speed: Option<f32>,
}

#[derive(Debug, Component)]
struct PendingCharacterMode(CharacterModeStatement);

#[derive(Debug, Component)]
struct SceneMaxStageSupport;

#[derive(Debug, Component)]
struct SceneMaxVirtualCollider {
    owner: String,
    bone: Option<String>,
    local_offset: Vec3,
    fallback_offset: Vec3,
}

#[derive(Debug, Clone)]
struct ModelRuntimeDecl {
    name: String,
    resource: String,
    options: EntityOptions,
}

#[derive(Debug, Clone, Copy)]
enum VirtualColliderShape {
    Box { half_extents: Vec3 },
    Sphere { radius: f32 },
}

const BUILTIN_PLAYER_MOVE_SPEED: f32 = 4.0;
const BUILTIN_PLAYER_TURN_SPEED_RADIANS: f32 = std::f32::consts::FRAC_PI_2;
const DEFAULT_CHARACTER_GRAVITY: f32 = 60.0;
const DEFAULT_CHARACTER_MOVE_SPEED: f32 = 7.0;
const DEFAULT_CHARACTER_CAPSULE_RADIUS: f32 = 0.35;
const DEFAULT_CHARACTER_CAPSULE_HEIGHT: f32 = 0.9;
const DEFAULT_CHARACTER_FLOAT_HEIGHT: f32 = 0.95;
const DEFAULT_CHARACTER_SENSOR_HEIGHT: f32 = 0.08;
const DEFAULT_CHARACTER_VISUAL_DROP: f32 = 1.25;
const DEFAULT_STAGE_SUPPORT_HALF_SIZE: f32 = 160.0;
const CHARACTER_INPUT_TTL_SECONDS: f32 = 0.12;
const CHARACTER_JUMP_FEED_SECONDS: f32 = 0.2;
const DEFAULT_ANIMATION_CLIP_SECONDS: f32 = 1.5;
const MAX_PLAYER_HITBOX_OWNER_DISTANCE: f32 = 3.75;
const LOOP_CONTINUE_DELAY_SECONDS: f32 = 0.001;
const PHYSICS_LAYER_WORLD: u32 = 1 << 0;
const PHYSICS_LAYER_CHARACTER: u32 = 1 << 1;
const PHYSICS_LAYER_HITBOX: u32 = 1 << 2;
static SCENEMAX_RUNTIME_LOG_FILE: Mutex<Option<PathBuf>> = Mutex::new(None);

fn initialize_runtime_logger(project_root: Option<&Path>, script_root: Option<&Path>) {
    let base = project_root
        .or(script_root)
        .map(Path::to_path_buf)
        .unwrap_or_else(|| std::env::current_dir().unwrap_or_else(|_| PathBuf::from(".")));
    let path = base.join("scenemax-nextgen-runtime.log");
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let _ = fs::remove_file(&path);
    if let Ok(mut log_file) = SCENEMAX_RUNTIME_LOG_FILE.lock() {
        *log_file = Some(path);
    }
    write_runtime_diagnostic_line("initialized SceneMax NextGen runtime log");
}

fn write_runtime_diagnostic_line(message: impl AsRef<str>) {
    let Some(path) = SCENEMAX_RUNTIME_LOG_FILE
        .lock()
        .ok()
        .and_then(|path| path.clone())
    else {
        return;
    };
    let line = format!("[RUNTIME] {}\n", message.as_ref());
    if let Ok(mut file) = fs::OpenOptions::new().create(true).append(true).open(path) {
        let _ = file.write_all(line.as_bytes());
    }
}

fn write_runtime_log_line(level: LoggerLevel, message: &str) {
    let level_text = match level {
        LoggerLevel::Info => "INFO",
        LoggerLevel::Debug => "DEBUG",
        LoggerLevel::Error => "ERROR",
    };
    tracing::info!(level = level_text, message, "SceneMax logger");
    let Some(path) = SCENEMAX_RUNTIME_LOG_FILE
        .lock()
        .ok()
        .and_then(|path| path.clone())
    else {
        return;
    };
    let line = format!("[{level_text}] {message}\n");
    if let Ok(mut file) = fs::OpenOptions::new().create(true).append(true).open(path) {
        let _ = file.write_all(line.as_bytes());
    }
}

fn write_key_event_probe(
    prefix: &str,
    key: &str,
    trigger: KeyTrigger,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
) {
    let message = format!(
        "{prefix} key={} trigger={} action={} move_forward={} player_hit={} player1_ko={} \
         game_status={} p1_jump={} p2_down={} p1_attack_legs={} allow_move={} asd_go={}",
        key,
        key_trigger_label(trigger),
        key_probe_var(vars, "action"),
        key_probe_var(vars, "move_forward"),
        key_probe_var(vars, "player_hit"),
        key_probe_var(vars, "player1_ko"),
        key_probe_var(vars, "game_status"),
        key_probe_var(vars, "player1.data.is_jumping"),
        key_probe_var(vars, "player2.data.is_down"),
        key_probe_var(vars, "player1.data.attack_legs"),
        key_probe_guard(
            "allow_move",
            vars,
            guards_by_name,
            transforms_by_name,
            collider_bounds
        ),
        key_probe_guard(
            "asd_go_condition",
            vars,
            guards_by_name,
            transforms_by_name,
            collider_bounds
        ),
    );
    write_runtime_log_line(LoggerLevel::Info, &message);
}

fn key_probe_var(vars: &SceneMaxVars, name: &str) -> String {
    vars.0
        .get(name)
        .copied()
        .map(format_scenemax_number)
        .unwrap_or_else(|| "null".to_owned())
}

fn key_probe_guard(
    name: &str,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
) -> &'static str {
    let Some(guard) = guards_by_name.get(name) else {
        return "missing";
    };
    if condition_matches(
        guard,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        Some(collider_bounds),
    ) {
        "true"
    } else {
        "false"
    }
}

fn key_trigger_label(trigger: KeyTrigger) -> &'static str {
    match trigger {
        KeyTrigger::Pressed => "pressed",
        KeyTrigger::PressedOnce => "pressed_once",
        KeyTrigger::Released => "released",
    }
}

fn write_state_assignment_probe(name: &str, previous: Option<f32>, value: f32, force_local: bool) {
    const WATCHED_ASSIGNMENTS: &[&str] = &[
        "action",
        "op_action",
        "player_hit",
        "op_hit",
        "player1_ko",
        "enemy_ko",
        "slow_motion",
        "game_status",
        "player1.data.is_jumping",
        "player2.data.is_jumping",
        "player2.data.is_down",
        "player1.data.attack_legs",
        "player1.data.hand_attack_hit",
        "player2.data.trapped",
    ];
    if !WATCHED_ASSIGNMENTS.contains(&name) {
        return;
    }
    let previous = previous
        .map(format_scenemax_number)
        .unwrap_or_else(|| "null".to_owned());
    write_runtime_log_line(
        LoggerLevel::Info,
        &format!(
            "STATE:SET name={} from={} to={} local={}",
            name,
            previous,
            format_scenemax_number(value),
            force_local as u8
        ),
    );
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
    log_unsupported_summary(&script_path, &parsed);
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

fn log_unsupported_summary(script_path: &Path, program: &Program) {
    let mut unsupported = BTreeMap::<String, usize>::new();
    collect_unsupported_statements(&program.statements, &mut unsupported);
    if unsupported.is_empty() {
        return;
    }
    let total = unsupported.values().copied().sum::<usize>();
    let examples = unsupported
        .iter()
        .take(8)
        .map(|(text, count)| format!("{count}x {text}"))
        .collect::<Vec<_>>()
        .join(" | ");
    tracing::info!(
        path = %script_path.display(),
        unsupported = total,
        examples,
        "SceneMax parser compatibility gaps in script"
    );
}

fn collect_unsupported_statements(
    statements: &[Statement],
    unsupported: &mut BTreeMap<String, usize>,
) {
    for statement in statements {
        match statement {
            Statement::Unsupported { text } => {
                *unsupported.entry(text.clone()).or_default() += 1;
            }
            Statement::KeyEvent(event) => {
                collect_unsupported_statements(&event.actions, unsupported)
            }
            Statement::WhenEvent(event) => {
                collect_unsupported_statements(&event.actions, unsupported)
            }
            Statement::FunctionDef(function) => {
                collect_unsupported_statements(&function.actions, unsupported);
            }
            Statement::If(statement) => {
                collect_unsupported_statements(&statement.actions, unsupported);
                collect_unsupported_statements(&statement.else_actions, unsupported);
            }
            Statement::Guarded { actions, .. }
            | Statement::Repeat { actions, .. }
            | Statement::DoWhile { actions, .. }
            | Statement::LoopContinue { actions, .. }
            | Statement::Async { actions } => collect_unsupported_statements(actions, unsupported),
            _ => {}
        }
    }
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

fn scenemax_ui_action_from_statement(action: &Statement) -> Option<SceneMaxUiAction> {
    match action {
        Statement::UiLoad { name } => Some(SceneMaxUiAction::Load { name: name.clone() }),
        Statement::UiShowHide(show_hide) => Some(SceneMaxUiAction::ShowHide {
            target: show_hide.target.clone(),
            visible: show_hide.visible,
        }),
        Statement::UiMessage(message) => Some(SceneMaxUiAction::Message {
            target: message.target.clone(),
            text: message.text.clone(),
            effects: message.effects.clone(),
            duration_seconds: message.duration_seconds,
        }),
        Statement::UiEase(ease) => Some(SceneMaxUiAction::Ease {
            target: ease.target.clone(),
            easing: ease.easing.clone(),
            direction: ease.direction,
            duration_seconds: ease.duration_seconds,
        }),
        Statement::UiSetProperty(property) => Some(SceneMaxUiAction::SetProperty {
            target: property.target.clone(),
            property: property.property.clone(),
            value: property.value.clone(),
        }),
        _ => None,
    }
}

fn apply_scenemax_ui_actions(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    mut ui_runtime: ResMut<SceneMaxUiRuntime>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut text_query: Query<(
        &mut Text,
        &mut TextColor,
        &mut UiTransform,
        Option<&SceneMaxUiTextVisualState>,
    )>,
    mut visibility_query: Query<&mut Visibility>,
) {
    let actions = ui_queue.actions.drain(..).collect::<Vec<_>>();
    if actions.is_empty() {
        return;
    }
    for action in actions {
        match action {
            SceneMaxUiAction::Load { name } => {
                if let Err(error) = load_scenemax_ui_document(
                    &name,
                    &mut commands,
                    &asset_server,
                    &context,
                    &mut ui_runtime,
                ) {
                    tracing::warn!(name, %error, "failed to load SceneMax UI document");
                    write_runtime_diagnostic_line(format!("failed to load UI {name}: {error}"));
                }
            }
            SceneMaxUiAction::ShowHide { target, visible } => {
                if let Some(target) = resolve_ui_target(&ui_runtime, &target) {
                    if let Ok(mut visibility_component) = visibility_query.get_mut(target.entity) {
                        *visibility_component = if visible {
                            Visibility::Inherited
                        } else {
                            Visibility::Hidden
                        };
                    } else {
                        commands.entity(target.entity).insert(if visible {
                            Visibility::Inherited
                        } else {
                            Visibility::Hidden
                        });
                    }
                }
            }
            SceneMaxUiAction::Message {
                target,
                text,
                effects,
                duration_seconds,
            } => {
                if let Some(target) = resolve_ui_target(&ui_runtime, &target)
                    && let Some(text_entity) = target.text_entity
                    && let Ok((mut text_component, mut text_color, mut transform, visual_state)) =
                        text_query.get_mut(text_entity)
                {
                    let visual_state = visual_state.copied().unwrap_or(SceneMaxUiTextVisualState {
                        base_color: text_color.0,
                        base_transform: *transform,
                    });
                    commands
                        .entity(text_entity)
                        .remove::<SceneMaxUiMessageAnimation>()
                        .insert(visual_state);
                    let effect_names = scenemax_runtime_ui_core::parse_effects(&effects);
                    if scenemax_runtime_ui_core::should_animate(&effect_names)
                        && duration_seconds > f32::EPSILON
                    {
                        apply_ui_message_progress(
                            &mut text_component,
                            &mut text_color,
                            &mut transform,
                            &text,
                            &effect_names,
                            0.0,
                            visual_state.base_color,
                            visual_state.base_transform,
                        );
                        commands
                            .entity(text_entity)
                            .insert(SceneMaxUiMessageAnimation {
                                full_text: text,
                                effect_names,
                                elapsed_seconds: 0.0,
                                duration_seconds: duration_seconds.max(0.001),
                                base_color: visual_state.base_color,
                                base_transform: visual_state.base_transform,
                            });
                    } else {
                        text_component.0 = text;
                        text_color.0 = visual_state.base_color;
                        *transform = visual_state.base_transform;
                    }
                }
            }
            SceneMaxUiAction::Ease {
                target,
                easing,
                direction,
                duration_seconds,
            } => {
                if let Some(target_path) = resolve_ui_target_path(&ui_runtime, &target)
                    && let Some(target) = resolve_ui_target(&ui_runtime, &target)
                {
                    let start = ui_ease_start_offset(direction);
                    commands.entity(target.entity).insert((
                        Visibility::Inherited,
                        UiTransform::from_translation(Val2::percent(start.x, start.y)),
                        SceneMaxUiEase {
                            start,
                            elapsed_seconds: 0.0,
                            duration_seconds: duration_seconds.max(0.001),
                            easing,
                        },
                    ));
                    tracing::debug!(target = target_path, "started SceneMax UI ease");
                }
            }
            SceneMaxUiAction::SetProperty {
                target,
                property,
                value,
            } => {
                if property.eq_ignore_ascii_case("text") {
                    if let Some(target) = resolve_ui_target(&ui_runtime, &target)
                        && let Some(text_entity) = target.text_entity
                        && let Ok((mut text_component, mut text_color, mut transform, visual_state)) =
                            text_query.get_mut(text_entity)
                    {
                        let visual_state =
                            visual_state.copied().unwrap_or(SceneMaxUiTextVisualState {
                                base_color: text_color.0,
                                base_transform: *transform,
                            });
                        commands
                            .entity(text_entity)
                            .remove::<SceneMaxUiMessageAnimation>()
                            .insert(visual_state);
                        text_component.0 = value;
                        text_color.0 = visual_state.base_color;
                        *transform = visual_state.base_transform;
                    }
                } else if property.eq_ignore_ascii_case("visible") {
                    let visible = !matches!(value.to_ascii_lowercase().as_str(), "0" | "false");
                    if let Some(target) = resolve_ui_target(&ui_runtime, &target) {
                        commands.entity(target.entity).insert(if visible {
                            Visibility::Inherited
                        } else {
                            Visibility::Hidden
                        });
                    }
                }
            }
        }
    }
}

fn update_scenemax_ui_eases(
    time: Res<Time>,
    mut commands: Commands,
    mut query: Query<(Entity, &mut UiTransform, &mut SceneMaxUiEase)>,
) {
    for (entity, mut transform, mut ease) in &mut query {
        ease.elapsed_seconds += time.delta_secs();
        let t = (ease.elapsed_seconds / ease.duration_seconds).clamp(0.0, 1.0);
        let eased = ui_ease_progress(t, &ease.easing);
        let offset = ease.start * (1.0 - eased);
        transform.translation = Val2::percent(offset.x, offset.y);
        if t >= 1.0 {
            transform.translation = Val2::ZERO;
            commands.entity(entity).remove::<SceneMaxUiEase>();
        }
    }
}

fn update_scenemax_ui_message_animations(
    time: Res<Time>,
    mut commands: Commands,
    mut query: Query<(
        Entity,
        &mut Text,
        &mut TextColor,
        &mut UiTransform,
        &mut SceneMaxUiMessageAnimation,
    )>,
) {
    for (entity, mut text, mut text_color, mut transform, mut animation) in &mut query {
        animation.elapsed_seconds += time.delta_secs();
        let progress = (animation.elapsed_seconds / animation.duration_seconds).clamp(0.0, 1.0);
        apply_ui_message_progress(
            &mut text,
            &mut text_color,
            &mut transform,
            &animation.full_text,
            &animation.effect_names,
            progress,
            animation.base_color,
            animation.base_transform,
        );
        if progress >= 1.0 {
            text.0 = animation.full_text.clone();
            text_color.0 = animation.base_color;
            *transform = animation.base_transform;
            commands
                .entity(entity)
                .remove::<SceneMaxUiMessageAnimation>();
        }
    }
}

fn apply_ui_message_progress(
    text: &mut Text,
    text_color: &mut TextColor,
    transform: &mut UiTransform,
    full_text: &str,
    effect_names: &[String],
    progress: f32,
    base_color: Color,
    base_transform: UiTransform,
) {
    let frame = scenemax_runtime_ui_core::evaluate_message_frame(full_text, effect_names, progress);
    text.0 = frame.visible_text;
    text_color.0 = base_color.with_alpha(base_color.alpha() * frame.alpha);
    *transform = base_transform;
    transform.scale = base_transform.scale * frame.scale;
}

fn load_scenemax_ui_document(
    name: &str,
    commands: &mut Commands,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    ui_runtime: &mut SceneMaxUiRuntime,
) -> Result<()> {
    if ui_runtime.loaded.contains_key(name) {
        ui_runtime.active_ui_name = Some(name.to_owned());
        return Ok(());
    }
    let path = resolve_scenemax_ui_path(name, context)?;
    let source = fs::read_to_string(&path)?;
    let doc: SceneMaxUiDocument = serde_json::from_str(&source)?;
    let ui_name = doc.name.clone();
    let ui_scale = document_scale(&doc, context.window_width, context.window_height);
    refresh_sprite_index(ui_runtime, context);

    let mut loaded = LoadedSceneMaxUi::default();
    for layer in &doc.layers {
        let root = commands
            .spawn((
                Name::new(format!("UI.{}.{}", ui_name, layer.name)),
                Node {
                    position_type: PositionType::Absolute,
                    left: Val::ZERO,
                    top: Val::ZERO,
                    width: Val::Percent(100.0),
                    height: Val::Percent(100.0),
                    ..default()
                },
                UiTransform::default(),
                if layer.visible {
                    Visibility::Inherited
                } else {
                    Visibility::Hidden
                },
                GlobalZIndex(layer.z_order),
            ))
            .id();
        loaded.root_entities.push(root);
        loaded.layer_entities.insert(layer.name.clone(), root);
        loaded.targets.insert(
            target_key(&ui_name, &layer.name, &[]),
            SceneMaxUiTarget {
                entity: root,
                text_entity: None,
            },
        );

        let local_rects = solve_widget_layout(&layer.widgets, doc.canvas_width, doc.canvas_height);
        for widget in sorted_widgets(&layer.widgets) {
            spawn_scenemax_ui_widget(
                commands,
                asset_server,
                context,
                ui_runtime,
                &ui_name,
                &layer.name,
                Vec::new(),
                widget,
                root,
                UiLayoutRect {
                    x: 0.0,
                    y: 0.0,
                    width: doc.canvas_width,
                    height: doc.canvas_height,
                },
                ui_scale,
                &local_rects,
                &mut loaded,
            );
        }
    }

    ui_runtime.active_ui_name = Some(ui_name.clone());
    ui_runtime.loaded.insert(ui_name.clone(), loaded);
    tracing::info!(name = ui_name, path = %path.display(), "loaded SceneMax UI document");
    write_runtime_diagnostic_line(format!("loaded UI {ui_name} from {}", path.display()));
    Ok(())
}

fn resolve_scenemax_ui_path(name: &str, context: &SceneMaxLaunchContext) -> Result<PathBuf> {
    let file_name = if name.ends_with(".smui") {
        name.to_owned()
    } else {
        format!("{name}.smui")
    };
    let mut candidates = Vec::new();
    if let Some(script_root) = context.script_root.as_ref() {
        candidates.push(script_root.join(&file_name));
    }
    if let Some(asset_root) = context.asset_root.as_ref()
        && let Some(project_root) = asset_root.parent()
    {
        candidates.push(project_root.join("scripts").join(&file_name));
    }
    candidates
        .into_iter()
        .find(|candidate| candidate.is_file())
        .ok_or_else(|| anyhow::anyhow!("UI document {file_name} was not found"))
}

fn spawn_scenemax_ui_widget(
    commands: &mut Commands,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    ui_runtime: &SceneMaxUiRuntime,
    ui_name: &str,
    layer_name: &str,
    parent_path: Vec<String>,
    widget: &SceneMaxUiWidgetDef,
    parent_entity: Entity,
    parent_rect: UiLayoutRect,
    ui_scale: f32,
    sibling_rects: &HashMap<String, UiLayoutRect>,
    loaded: &mut LoadedSceneMaxUi,
) {
    let Some(rect) = sibling_rects.get(&widget.name).copied() else {
        return;
    };
    let mut widget_path = parent_path.clone();
    widget_path.push(widget.name.clone());
    let local_rect = UiLayoutRect {
        x: rect.x - parent_rect.x,
        y: rect.y - parent_rect.y,
        width: rect.width,
        height: rect.height,
    };
    let node = node_from_rect(local_rect, parent_rect);
    let key = target_key(ui_name, layer_name, &widget_path);
    let visibility = if widget.visible {
        Visibility::Inherited
    } else {
        Visibility::Hidden
    };
    let base_marker = SceneMaxUiWidget {
        ui_name: ui_name.to_owned(),
        layer: layer_name.to_owned(),
        widget_path: widget_path.clone(),
    };

    let (entity, text_entity) = match widget.widget_type.as_str() {
        "TEXT_VIEW" | "EDIT_TEXT" => {
            let text_color = parse_ui_color(&widget.text_color);
            let text_transform = UiTransform::default();
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    text_transform,
                    visibility,
                    ZIndex(widget.z_order),
                    Text::new(widget.text.clone()),
                    TextFont {
                        font_size: FontSize::Px(scaled_font_size(widget.font_size, ui_scale)),
                        ..default()
                    },
                    TextColor(text_color),
                    TextLayout::justify(ui_text_justify(&widget.text_alignment)).with_no_wrap(),
                    SceneMaxUiTextVisualState {
                        base_color: text_color,
                        base_transform: text_transform,
                    },
                    base_marker,
                ))
                .id();
            (entity, Some(entity))
        }
        "BUTTON" => {
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    BackgroundColor(parse_ui_color(&widget.button_color)),
                    UiTransform::default(),
                    visibility,
                    ZIndex(widget.z_order),
                    base_marker,
                ))
                .id();
            let text = commands
                .spawn((
                    Node {
                        position_type: PositionType::Absolute,
                        left: Val::ZERO,
                        top: Val::ZERO,
                        width: Val::Percent(100.0),
                        height: Val::Percent(100.0),
                        ..default()
                    },
                    Text::new(widget.button_text.clone()),
                    TextFont {
                        font_size: FontSize::Px(scaled_font_size(widget.font_size, ui_scale)),
                        ..default()
                    },
                    TextColor(parse_ui_color(&widget.button_text_color)),
                    TextLayout::justify(Justify::Center).with_no_wrap(),
                    SceneMaxUiTextVisualState {
                        base_color: parse_ui_color(&widget.button_text_color),
                        base_transform: UiTransform::default(),
                    },
                ))
                .id();
            commands.entity(entity).add_child(text);
            (entity, Some(text))
        }
        "IMAGE" => {
            let image_node = ui_image_node_for_widget(widget, asset_server, context, ui_runtime);
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    image_node,
                    UiTransform::default(),
                    visibility,
                    ZIndex(widget.z_order),
                    base_marker,
                ))
                .id();
            (entity, None)
        }
        "LIST_VIEW" => {
            let text = list_view_text(widget);
            let text_color = parse_ui_color(&widget.text_color);
            let text_transform = UiTransform::default();
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    BackgroundColor(parse_ui_color(&widget.background_color)),
                    text_transform,
                    visibility,
                    ZIndex(widget.z_order),
                    Text::new(text),
                    TextFont {
                        font_size: FontSize::Px(scaled_font_size(
                            widget.list_row_font_size,
                            ui_scale,
                        )),
                        ..default()
                    },
                    TextColor(text_color),
                    TextLayout::justify(Justify::Left).with_no_wrap(),
                    SceneMaxUiTextVisualState {
                        base_color: text_color,
                        base_transform: text_transform,
                    },
                    base_marker,
                ))
                .id();
            (entity, Some(entity))
        }
        _ => {
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    BackgroundColor(parse_ui_color(&widget.background_color)),
                    UiTransform::default(),
                    visibility,
                    ZIndex(widget.z_order),
                    base_marker,
                ))
                .id();
            (entity, None)
        }
    };
    commands.entity(parent_entity).add_child(entity);
    loaded.targets.insert(
        key,
        SceneMaxUiTarget {
            entity,
            text_entity,
        },
    );

    if !widget.children.is_empty() {
        let child_parent_rect = UiLayoutRect {
            x: rect.x + widget.padding_left,
            y: rect.y + widget.padding_top,
            width: (rect.width - widget.padding_left - widget.padding_right).max(1.0),
            height: (rect.height - widget.padding_top - widget.padding_bottom).max(1.0),
        };
        let child_rects = solve_widget_layout(
            &widget.children,
            child_parent_rect.width,
            child_parent_rect.height,
        )
        .into_iter()
        .map(|(name, mut child_rect)| {
            child_rect.x += child_parent_rect.x;
            child_rect.y += child_parent_rect.y;
            (name, child_rect)
        })
        .collect::<HashMap<_, _>>();
        for child in sorted_widgets(&widget.children) {
            spawn_scenemax_ui_widget(
                commands,
                asset_server,
                context,
                ui_runtime,
                ui_name,
                layer_name,
                widget_path.clone(),
                child,
                entity,
                child_parent_rect,
                ui_scale,
                &child_rects,
                loaded,
            );
        }
    }
}

fn node_from_rect(rect: UiLayoutRect, parent_rect: UiLayoutRect) -> Node {
    Node {
        position_type: PositionType::Absolute,
        left: Val::Percent(percent(rect.x, parent_rect.width)),
        top: Val::Percent(percent(rect.y, parent_rect.height)),
        width: Val::Percent(percent(rect.width, parent_rect.width)),
        height: Val::Percent(percent(rect.height, parent_rect.height)),
        ..default()
    }
}

fn parse_ui_color(hex: &str) -> Color {
    let trimmed = hex.trim().trim_start_matches('#');
    if trimmed.len() < 6 {
        return Color::WHITE;
    }
    let parse =
        |range: std::ops::Range<usize>| u8::from_str_radix(&trimmed[range], 16).unwrap_or(255);
    let alpha = if trimmed.len() >= 8 { parse(6..8) } else { 255 };
    Color::srgba_u8(parse(0..2), parse(2..4), parse(4..6), alpha)
}

fn ui_text_justify(alignment: &str) -> Justify {
    match alignment.to_ascii_lowercase().as_str() {
        "center" => Justify::Center,
        "right" => Justify::Right,
        _ => Justify::Left,
    }
}

fn resolve_ui_target<'a>(
    ui_runtime: &'a SceneMaxUiRuntime,
    path: &UiTargetPath,
) -> Option<&'a SceneMaxUiTarget> {
    let target_path = resolve_ui_target_path(ui_runtime, path)?;
    let ui_name = path
        .ui_name
        .as_deref()
        .or(ui_runtime.active_ui_name.as_deref())?;
    ui_runtime
        .loaded
        .get(ui_name)
        .and_then(|loaded| loaded.targets.get(&target_path))
}

fn resolve_ui_target_path(ui_runtime: &SceneMaxUiRuntime, path: &UiTargetPath) -> Option<String> {
    let ui_name = path
        .ui_name
        .as_deref()
        .or(ui_runtime.active_ui_name.as_deref())?;
    if path.widget_path.is_empty() {
        return Some(target_key(ui_name, &path.layer, &[]));
    }
    Some(target_key(ui_name, &path.layer, &path.widget_path))
}

fn ui_ease_start_offset(direction: UiEaseDirection) -> Vec2 {
    match direction {
        UiEaseDirection::Up => Vec2::new(0.0, 115.0),
        UiEaseDirection::Down => Vec2::new(0.0, -115.0),
        UiEaseDirection::Left => Vec2::new(-115.0, 0.0),
        UiEaseDirection::Right => Vec2::new(115.0, 0.0),
    }
}

fn ui_ease_progress(t: f32, easing: &str) -> f32 {
    let t = t.clamp(0.0, 1.0);
    let lower = easing.to_ascii_lowercase();
    if lower.contains("bounce") {
        return ease_out_bounce(t);
    }
    if lower.contains("back") {
        let c1 = 1.70158;
        let c3 = c1 + 1.0;
        return c3 * t * t * t - c1 * t * t;
    }
    if lower.contains("cubic") {
        return t * t * t;
    }
    if lower.contains("quad") {
        return t * t;
    }
    t
}

fn ease_out_bounce(t: f32) -> f32 {
    let n1 = 7.5625;
    let d1 = 2.75;
    if t < 1.0 / d1 {
        n1 * t * t
    } else if t < 2.0 / d1 {
        let t = t - 1.5 / d1;
        n1 * t * t + 0.75
    } else if t < 2.5 / d1 {
        let t = t - 2.25 / d1;
        n1 * t * t + 0.9375
    } else {
        let t = t - 2.625 / d1;
        n1 * t * t + 0.984375
    }
}

fn ui_image_node_for_widget(
    widget: &SceneMaxUiWidgetDef,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    ui_runtime: &SceneMaxUiRuntime,
) -> ImageNode {
    let candidates = ui_image_asset_candidates(widget, context, ui_runtime);
    for candidate in candidates {
        if ui_asset_path_exists(&candidate, context) {
            return ImageNode::new(asset_server.load(candidate));
        }
    }
    ImageNode::solid_color(Color::srgba(0.9, 0.72, 0.38, 0.85))
}

fn ui_image_asset_candidates(
    widget: &SceneMaxUiWidgetDef,
    context: &SceneMaxLaunchContext,
    ui_runtime: &SceneMaxUiRuntime,
) -> Vec<String> {
    let mut candidates = Vec::new();
    if let Some(sprite_name) = widget.sprite_name.as_deref() {
        if let Some(sprite) = ui_runtime.sprite_index.get(sprite_name) {
            candidates.push(normalize_asset_path(&sprite.path));
        }
        candidates.push(format!("sprites/{sprite_name}.png"));
        candidates.push(format!("sprites/{}.png", sprite_name.to_ascii_lowercase()));
        if context.builtin_asset_root.is_some() {
            candidates.push(format!("builtin://sprites/{sprite_name}.png"));
            candidates.push(format!(
                "builtin://sprites/{}.png",
                sprite_name.to_ascii_lowercase()
            ));
        }
    }
    if let Some(image_path) = widget.image_path.as_deref() {
        candidates.push(normalize_asset_path(image_path));
    }
    candidates
}

fn normalize_asset_path(path: &str) -> String {
    path.replace('\\', "/")
        .trim_start_matches('/')
        .trim_start_matches("resources/")
        .to_owned()
}

fn ui_asset_path_exists(asset_path: &str, context: &SceneMaxLaunchContext) -> bool {
    if let Some(relative) = asset_path.strip_prefix("builtin://") {
        return context
            .builtin_asset_root
            .as_ref()
            .is_some_and(|root| root.join(relative).is_file());
    }
    context
        .asset_root
        .as_ref()
        .is_some_and(|root| root.join(asset_path).is_file())
}

fn refresh_sprite_index(ui_runtime: &mut SceneMaxUiRuntime, context: &SceneMaxLaunchContext) {
    let Some(asset_root) = context.asset_root.as_ref() else {
        return;
    };
    if ui_runtime.sprite_index_root.as_ref() == Some(asset_root) {
        return;
    }
    ui_runtime.sprite_index.clear();
    ui_runtime.sprite_index_root = Some(asset_root.clone());
    load_sprite_index_file(
        &asset_root.join("sprites").join("sprites.json"),
        "",
        ui_runtime,
    );
    if let Some(builtin_root) = context.builtin_asset_root.as_ref() {
        load_sprite_index_file(
            &builtin_root.join("sprites").join("sprites.json"),
            "builtin://",
            ui_runtime,
        );
    }
}

fn load_sprite_index_file(path: &Path, prefix: &str, ui_runtime: &mut SceneMaxUiRuntime) {
    let Ok(source) = fs::read_to_string(path) else {
        return;
    };
    let Ok(root) = serde_json::from_str::<serde_json::Value>(&source) else {
        return;
    };
    let Some(sprites) = root.get("sprites").and_then(|value| value.as_array()) else {
        return;
    };
    for sprite in sprites {
        let Some(name) = sprite.get("name").and_then(|value| value.as_str()) else {
            continue;
        };
        let Some(path) = sprite.get("path").and_then(|value| value.as_str()) else {
            continue;
        };
        let rows = sprite
            .get("rows")
            .and_then(|value| value.as_u64())
            .unwrap_or(1) as usize;
        let cols = sprite
            .get("cols")
            .and_then(|value| value.as_u64())
            .unwrap_or(1) as usize;
        ui_runtime.sprite_index.insert(
            name.to_owned(),
            SceneMaxSpriteAsset {
                path: format!("{prefix}{}", normalize_asset_path(path)),
                rows,
                cols,
            },
        );
    }
}

fn setup_scenemax_program(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    mut character_configs: ResMut<Assets<SceneMaxControlSchemeConfig>>,
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

    object_pools.aliases.clear();
    object_pools.pools.clear();
    delayed_actions.actions.clear();
    collider_bounds.clear();
    apply_initial_assignments(program, &mut vars);
    apply_camera_systems(program, &mut camera_system);
    spawn_scenemax_program(
        &mut commands,
        &asset_server,
        asset_root,
        context.builtin_asset_root.as_deref(),
        program,
        &mut vars,
        &mut object_pools,
        &mut camera_system,
        &mut delayed_actions,
        &mut ui_queue,
        &mut collider_bounds,
        &mut meshes,
        &mut materials,
        &mut character_configs,
    );
}

fn spawn_scenemax_program(
    commands: &mut Commands,
    asset_server: &AssetServer,
    asset_root: &Path,
    builtin_asset_root: Option<&Path>,
    program: &Program,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    delayed_actions: &mut DelayedActionQueue,
    ui_queue: &mut SceneMaxUiActionQueue,
    collider_bounds: &mut SceneMaxColliderBounds,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
    character_configs: &mut ResMut<Assets<SceneMaxControlSchemeConfig>>,
) {
    let animations_by_target = collect_animations_by_target(program);
    let visibility_by_target = collect_visibility_by_target(program);
    let turn_by_target = collect_turn_by_target(program);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);
    let attaches_by_target = collect_attaches_by_target(program);
    let mut model_declarations = collect_model_declarations(program);
    model_declarations.extend(instantiate_object_pool_declarations(
        program,
        &functions_by_name,
        object_pools,
    ));
    let mut spawned_any = false;
    let mut entities_by_name = HashMap::new();
    let mut transforms_by_name = HashMap::new();
    let mut gltfs_by_name = HashMap::new();

    for ModelRuntimeDecl {
        name,
        resource,
        options,
    } in &model_declarations
    {
        if options.collider {
            let transform =
                collider_decl_transform(name, options, &attaches_by_target, &transforms_by_name);
            let entity = spawn_scenemax_collider_decl(
                commands,
                name,
                resource,
                options,
                transform,
                attaches_by_target.get(name),
            );
            register_collider_bounds(collider_bounds, name, options, transform);
            entities_by_name.insert(name.clone(), entity);
            transforms_by_name.insert(name.clone(), transform);
            spawned_any = true;
            tracing::info!(name, resource, "spawned SceneMax collider");
            continue;
        }

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
            insert_physics_components(commands, entity, name, resource, options, &transform);
            entities_by_name.insert(name.clone(), entity);
            transforms_by_name.insert(name.clone(), transform);
            spawned_any = true;
            tracing::info!(name, resource, "spawned SceneMax primitive");
            continue;
        }

        match scenemax_assets::resolve_model_resource_with_builtin_fallback(
            asset_root,
            builtin_asset_root,
            resource,
        ) {
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
                        gltf: gltf.clone(),
                    });
                }
                insert_physics_components(commands, entity_id, name, resource, options, &transform);

                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                gltfs_by_name.insert(name.clone(), gltf);
                spawned_any = true;
                tracing::info!(
                    name,
                    resource,
                    path = %asset_path,
                    "spawned SceneMax GLTF model"
                );
                write_runtime_diagnostic_line(format!(
                    "resolved GLTF model {name}=>{resource} at {asset_path}"
                ));
            }
            Err(scenemax_assets::AssetLookupError::UnsupportedModelFormat {
                asset_path, ..
            }) => {
                let transform = transform_from_options(options, None);
                let entity_id = spawn_unsupported_model_placeholder(
                    commands,
                    meshes,
                    materials,
                    name,
                    resource,
                    options,
                    transform,
                    &visibility_by_target,
                );
                insert_physics_components(commands, entity_id, name, resource, options, &transform);
                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                spawned_any = true;
                tracing::warn!(
                    name,
                    resource,
                    path = %asset_path,
                    "spawned placeholder for unsupported SceneMax model format"
                );
                write_runtime_diagnostic_line(format!(
                    "placeholder for unsupported model {name}=>{resource}; path={asset_path}"
                ));
            }
            Err(scenemax_assets::AssetLookupError::ModelNotFound(_)) => {
                let transform = transform_from_options(options, None);
                let entity_id = spawn_unsupported_model_placeholder(
                    commands,
                    meshes,
                    materials,
                    name,
                    resource,
                    options,
                    transform,
                    &visibility_by_target,
                );
                insert_physics_components(commands, entity_id, name, resource, options, &transform);
                entities_by_name.insert(name.clone(), entity_id);
                transforms_by_name.insert(name.clone(), transform);
                spawned_any = true;
                tracing::warn!(
                    name,
                    resource,
                    "spawned placeholder for unresolved SceneMax model resource"
                );
                write_runtime_diagnostic_line(format!(
                    "placeholder for unresolved model {name}=>{resource}; project assets={}; builtin assets={}",
                    asset_root.display(),
                    builtin_asset_root
                        .map(|path| path.display().to_string())
                        .unwrap_or_else(|| "<none>".to_owned())
                ));
            }
        }
    }

    apply_look_at_commands(
        program,
        commands,
        &entities_by_name,
        &mut transforms_by_name,
    );
    apply_character_modes(
        program,
        commands,
        &entities_by_name,
        &transforms_by_name,
        character_configs,
    );
    spawn_default_virtual_colliders(
        commands,
        &mut entities_by_name,
        &mut transforms_by_name,
        collider_bounds,
    );
    for (target, turn) in turn_by_target {
        if let Some(entity) = entities_by_name.get(&target) {
            commands
                .entity(*entity)
                .insert(timed_turn_from_statement(&turn));
        }
    }

    apply_startup_runs(
        program,
        commands,
        vars,
        object_pools,
        camera_system,
        delayed_actions,
        ui_queue,
        &functions_by_name,
        &entities_by_name,
        &mut transforms_by_name,
        &gltfs_by_name,
        &guards_by_name,
    );

    if !spawned_any {
        write_runtime_diagnostic_line(
            "spawned default placeholder because the program produced no renderable entities",
        );
        spawn_placeholder_model(commands, meshes, materials);
    }
}

fn collider_decl_transform(
    name: &str,
    options: &EntityOptions,
    attaches_by_target: &HashMap<String, AttachStatement>,
    transforms_by_name: &HashMap<String, Transform>,
) -> Transform {
    if let Some(attach) = attaches_by_target.get(name) {
        let owner = attach_owner(&attach.subject);
        if let Some(owner_transform) = transforms_by_name.get(&owner).copied() {
            return virtual_collider_transform(owner_transform, attach_fallback_offset(attach));
        }
    }
    primitive_transform_from_options(options)
}

fn spawn_scenemax_collider_decl(
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

fn collect_model_declarations(program: &Program) -> Vec<ModelRuntimeDecl> {
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
            Some(ModelRuntimeDecl {
                name: name.clone(),
                resource: resource.clone(),
                options: *options,
            })
        })
        .collect()
}

fn instantiate_object_pool_declarations(
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

        let mut runtime = ObjectPoolRuntime::default();
        for index in 0..pool.size.min(256) {
            let member_name = format!("__pool_{}_{}", pool.name, index);
            runtime.available.push(member_name.clone());
            runtime.members.insert(member_name.clone());
            let mut options = prototype.options;
            options.hidden = true;
            declarations.push(ModelRuntimeDecl {
                name: member_name,
                resource: prototype.resource.clone(),
                options,
            });
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

fn object_pool_prototype(
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
            options: *options,
        })
    })
}

fn spawn_unsupported_model_placeholder(
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

fn unsupported_model_placeholder_mesh(
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

fn insert_physics_components(
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

fn physics_body_kind(options: &EntityOptions) -> Option<SceneMaxBodyKind> {
    options.body_kind.or_else(|| {
        options
            .collision_shape
            .is_some_and(|shape| shape != SceneMaxCollisionShape::None)
            .then_some(SceneMaxBodyKind::Static)
    })
}

fn physics_collision_shape(
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

fn default_collision_shape(
    name: &str,
    resource: &str,
    body_kind: SceneMaxBodyKind,
) -> SceneMaxCollisionShape {
    let lower = format!("{} {}", name, resource).to_ascii_lowercase();
    if body_kind == SceneMaxBodyKind::Kinematic
        && (lower.contains("player") || lower.contains("fighter") || lower.contains("boss"))
    {
        SceneMaxCollisionShape::Capsule
    } else if resource.eq_ignore_ascii_case("sphere") {
        SceneMaxCollisionShape::Sphere
    } else {
        SceneMaxCollisionShape::Box
    }
}

fn avian_collider(
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

fn collider_dimensions(options: &EntityOptions, transform: &Transform) -> Vec3 {
    if let Some(radius) = options.radius {
        return Vec3::splat((radius * 2.0).max(0.1));
    }
    options
        .size
        .map(vec3_from_scenemax)
        .unwrap_or_else(|| transform.scale.abs())
        .max(Vec3::splat(0.1))
}

fn collider_bound_shape(options: &EntityOptions, transform: Transform) -> ColliderBoundShape {
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

fn register_collider_bounds(
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

fn spawn_default_virtual_colliders(
    commands: &mut Commands,
    entities_by_name: &mut HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    collider_bounds: &mut SceneMaxColliderBounds,
) {
    let owners = ["player1", "player2"];
    for owner in owners {
        let Some(owner_transform) = transforms_by_name.get(owner).copied() else {
            continue;
        };
        for spec in default_fighter_virtual_colliders(owner) {
            if entities_by_name.contains_key(&spec.name) {
                continue;
            }
            let transform = virtual_collider_transform(owner_transform, spec.local_offset);
            let shape = virtual_collider_bound_shape(spec.shape);
            let radius = shape.bounding_radius();
            let entity = commands
                .spawn((
                    SceneMaxEntity {
                        name: spec.name.clone(),
                        runtime_name: format!("{}@virtual", spec.name),
                    },
                    SceneMaxVirtualCollider {
                        owner: owner.to_owned(),
                        bone: spec.bone.map(str::to_owned),
                        local_offset: Vec3::ZERO,
                        fallback_offset: spec.local_offset,
                    },
                    transform,
                    Visibility::Hidden,
                    AvianRigidBody::Kinematic,
                    virtual_collider_shape(spec.shape),
                    hitbox_collision_layers(),
                    Sensor,
                    CollisionEventsEnabled,
                ))
                .id();
            entities_by_name.insert(spec.name.clone(), entity);
            transforms_by_name.insert(spec.name.clone(), transform);
            collider_bounds
                .radius_by_name
                .insert(spec.name.clone(), radius);
            collider_bounds
                .shape_by_name
                .insert(spec.name.clone(), shape);
        }
    }
}

#[derive(Debug)]
struct VirtualColliderSpec {
    name: String,
    bone: Option<&'static str>,
    local_offset: Vec3,
    shape: VirtualColliderShape,
}

fn default_fighter_virtual_colliders(owner: &str) -> Vec<VirtualColliderSpec> {
    vec![
        VirtualColliderSpec {
            name: format!("{owner}_body_collider"),
            bone: None,
            local_offset: Vec3::new(0.0, 1.0, 0.0),
            shape: VirtualColliderShape::Box {
                half_extents: Vec3::new(0.55, 0.9, 0.35),
            },
        },
        VirtualColliderSpec {
            name: format!("{owner}_head_collider"),
            bone: Some("mixamorig:Head"),
            local_offset: Vec3::new(0.0, 1.65, 0.0),
            shape: VirtualColliderShape::Sphere { radius: 0.28 },
        },
        VirtualColliderSpec {
            name: format!("{owner}_left_hand_collider"),
            bone: Some("mixamorig:LeftHand"),
            local_offset: Vec3::new(-0.45, 1.15, 0.2),
            shape: VirtualColliderShape::Sphere { radius: 0.24 },
        },
        VirtualColliderSpec {
            name: format!("{owner}_right_hand_collider"),
            bone: Some("mixamorig:RightHand"),
            local_offset: Vec3::new(0.45, 1.15, 0.2),
            shape: VirtualColliderShape::Sphere { radius: 0.24 },
        },
        VirtualColliderSpec {
            name: format!("{owner}_left_foot_collider"),
            bone: Some("mixamorig:LeftFoot"),
            local_offset: Vec3::new(-0.22, 0.18, 0.18),
            shape: VirtualColliderShape::Sphere { radius: 0.26 },
        },
        VirtualColliderSpec {
            name: format!("{owner}_right_foot_collider"),
            bone: Some("mixamorig:RightFoot"),
            local_offset: Vec3::new(0.22, 0.18, 0.18),
            shape: VirtualColliderShape::Sphere { radius: 0.26 },
        },
    ]
}

fn virtual_collider_shape(shape: VirtualColliderShape) -> AvianCollider {
    match shape {
        VirtualColliderShape::Box { half_extents } => {
            AvianCollider::cuboid(half_extents.x, half_extents.y, half_extents.z)
        }
        VirtualColliderShape::Sphere { radius } => AvianCollider::sphere(radius),
    }
}

fn virtual_collider_bound_shape(shape: VirtualColliderShape) -> ColliderBoundShape {
    match shape {
        VirtualColliderShape::Box { half_extents } => ColliderBoundShape::Box { half_extents },
        VirtualColliderShape::Sphere { radius } => ColliderBoundShape::Sphere { radius },
    }
}

fn solid_collision_layers(body_kind: SceneMaxBodyKind) -> CollisionLayers {
    match body_kind {
        SceneMaxBodyKind::Static => world_collision_layers(),
        SceneMaxBodyKind::Kinematic | SceneMaxBodyKind::Dynamic => {
            CollisionLayers::from_bits(PHYSICS_LAYER_WORLD, PHYSICS_LAYER_CHARACTER)
        }
    }
}

fn world_collision_layers() -> CollisionLayers {
    CollisionLayers::from_bits(
        PHYSICS_LAYER_WORLD,
        PHYSICS_LAYER_WORLD | PHYSICS_LAYER_CHARACTER,
    )
}

fn character_collision_layers() -> CollisionLayers {
    CollisionLayers::from_bits(PHYSICS_LAYER_CHARACTER, PHYSICS_LAYER_WORLD)
}

fn hitbox_collision_layers() -> CollisionLayers {
    CollisionLayers::from_bits(PHYSICS_LAYER_HITBOX, PHYSICS_LAYER_HITBOX)
}

fn virtual_collider_transform(owner_transform: Transform, local_offset: Vec3) -> Transform {
    Transform {
        translation: owner_transform.translation
            + owner_transform.rotation * (local_offset * owner_transform.scale.abs()),
        rotation: owner_transform.rotation,
        scale: Vec3::ONE,
    }
}

fn attach_owner(subject: &str) -> String {
    collision_owner(subject)
}

fn attach_fallback_offset(attach: &AttachStatement) -> Vec3 {
    attachment_bone_offset(&attach.subject) + vec3_from_scenemax(attach.offset)
}

fn attach_bone_name(subject: &str) -> Option<String> {
    let start = subject.find('"')?;
    let after_start = &subject[start + 1..];
    let end = after_start.find('"')?;
    let bone = after_start[..end].trim();
    (!bone.is_empty()).then(|| bone.to_owned())
}

fn attachment_bone_offset(subject: &str) -> Vec3 {
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

fn update_virtual_colliders(
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

fn find_descendant_transform_by_name(
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

fn names_match(actual: &str, wanted: &str) -> bool {
    actual.eq_ignore_ascii_case(wanted)
        || actual
            .rsplit(['/', '.'])
            .next()
            .is_some_and(|tail| tail.eq_ignore_ascii_case(wanted))
}

fn attachment_node_transform(node_transform: Transform, local_offset: Vec3) -> Transform {
    Transform {
        translation: node_transform.translation
            + node_transform.rotation * (local_offset * node_transform.scale.abs()),
        rotation: node_transform.rotation,
        scale: Vec3::ONE,
    }
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

fn apply_character_modes(
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
        support_samples.push((character_mode.target.clone(), transform));
        insert_tnua_character_controller(commands, entity, character_mode, character_configs);
    }
    spawn_character_stage_support(commands, &support_samples);
}

fn insert_tnua_character_controller(
    commands: &mut Commands,
    entity: Entity,
    character_mode: &CharacterModeStatement,
    character_configs: &mut ResMut<Assets<SceneMaxControlSchemeConfig>>,
) {
    let gravity = character_mode.gravity.unwrap_or(DEFAULT_CHARACTER_GRAVITY);
    let radius = DEFAULT_CHARACTER_CAPSULE_RADIUS;
    let height = DEFAULT_CHARACTER_CAPSULE_HEIGHT;
    let float_height = DEFAULT_CHARACTER_FLOAT_HEIGHT;
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
        },
        SceneMaxCharacterMotor::default(),
        AvianRigidBody::Dynamic,
        AvianCollider::capsule(radius, height),
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
        "enabled Tnua SceneMax character mode"
    );
}

fn apply_pending_character_modes(
    mut commands: Commands,
    mut character_configs: ResMut<Assets<SceneMaxControlSchemeConfig>>,
    pending: Query<(Entity, &Transform, &PendingCharacterMode)>,
    active_characters: Query<
        (&SceneMaxEntity, &Transform),
        (
            With<SceneMaxCharacterController>,
            Without<PendingCharacterMode>,
        ),
    >,
    supports: Query<Entity, With<SceneMaxStageSupport>>,
) {
    let pending_modes = pending.iter().collect::<Vec<_>>();
    if pending_modes.is_empty() {
        return;
    }

    for support_entity in &supports {
        commands.entity(support_entity).despawn();
    }
    let support_samples = pending_modes
        .iter()
        .map(|(_, transform, pending_mode)| (pending_mode.0.target.clone(), **transform))
        .chain(
            active_characters
                .iter()
                .map(|(scene_entity, transform)| (scene_entity.name.clone(), *transform)),
        )
        .collect::<Vec<_>>();
    if !support_samples.is_empty() {
        spawn_character_stage_support(&mut commands, &support_samples);
    }

    for (entity, _transform, pending_mode) in pending_modes {
        insert_tnua_character_controller(
            &mut commands,
            entity,
            &pending_mode.0,
            &mut character_configs,
        );
        commands.entity(entity).remove::<PendingCharacterMode>();
    }
}

fn cleanup_character_supports(
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

fn clear_character_mode(commands: &mut Commands, entity: Entity) {
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

fn spawn_character_stage_support(commands: &mut Commands, samples: &[(String, Transform)]) {
    let support_samples = preferred_stage_support_samples(samples);
    if support_samples.is_empty() {
        return;
    }

    let sample_count = support_samples.len() as f32;
    let center = support_samples
        .iter()
        .map(|(_, transform)| transform.translation)
        .fold(Vec3::ZERO, |sum, translation| sum + translation)
        / sample_count;
    let support_y = character_stage_support_y(center.y);
    let spread = support_samples
        .iter()
        .map(|(_, transform)| {
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
        SceneMaxStageSupport,
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

fn character_stage_support_y(center_y: f32) -> f32 {
    center_y - DEFAULT_CHARACTER_FLOAT_HEIGHT - DEFAULT_CHARACTER_VISUAL_DROP
}

fn preferred_stage_support_samples(samples: &[(String, Transform)]) -> Vec<(String, Transform)> {
    let player_samples = samples
        .iter()
        .filter(|(name, _)| name.to_ascii_lowercase().starts_with("player"))
        .cloned()
        .collect::<Vec<_>>();
    if player_samples.is_empty() {
        samples.to_vec()
    } else {
        player_samples
    }
}

fn apply_startup_runs(
    program: &Program,
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    delayed_actions: &mut DelayedActionQueue,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
) {
    let actions = program
        .statements
        .iter()
        .filter(|statement| is_startup_action(statement))
        .cloned()
        .collect::<Vec<_>>();
    let _ = apply_startup_action_sequence(
        &actions,
        commands,
        vars,
        object_pools,
        camera_system,
        delayed_actions,
        ui_queue,
        functions_by_name,
        entities_by_name,
        transforms_by_name,
        gltfs_by_name,
        guards_by_name,
        0,
    );
}

fn is_startup_action(statement: &Statement) -> bool {
    !matches!(
        statement,
        Statement::ModelDecl { .. }
            | Statement::ObjectPool(_)
            | Statement::KeyEvent(_)
            | Statement::WhenEvent(_)
            | Statement::GuardDef { .. }
            | Statement::FightingCamera(_)
            | Statement::ThirdPersonCamera(_)
            | Statement::FunctionDef(_)
            | Statement::RunEvery { .. }
            | Statement::CameraPosition(_)
            | Statement::CameraRotation(_)
            | Statement::WaitForKey { .. }
            | Statement::SwitchTo { .. }
            | Statement::AddCode { .. }
            | Statement::NoOp { .. }
            | Statement::Unsupported { .. }
    )
}

fn apply_startup_action_sequence(
    actions: &[Statement],
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    delayed_actions: &mut DelayedActionQueue,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
    depth: usize,
) -> ActionSequenceResult {
    if depth > 8 {
        tracing::warn!("skipping deeply recursive startup SceneMax action sequence");
        return ActionSequenceResult::Completed;
    }

    for (index, action) in actions.iter().enumerate() {
        match action {
            Statement::NoOp { .. } | Statement::Unsupported { .. } => {}
            Statement::Return | Statement::ReturnValue { .. } => {
                return ActionSequenceResult::Returned;
            }
            Statement::Wait { seconds } => {
                if enqueue_delayed_actions(
                    Some(delayed_actions),
                    *seconds,
                    actions[index + 1..].to_vec(),
                    None,
                    None,
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::WaitValue { value } => {
                let seconds = resolve_assignment_value_scoped_with_guards(
                    value,
                    vars,
                    None,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                )
                .unwrap_or_default();
                if enqueue_delayed_actions(
                    Some(delayed_actions),
                    seconds,
                    actions[index + 1..].to_vec(),
                    None,
                    None,
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::RunFunction { name, args } => {
                let Some(function) = functions_by_name.get(name) else {
                    tracing::debug!(name, "startup SceneMax function was not parsed");
                    continue;
                };
                if !function_guard_matches(
                    function,
                    args,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    tracing::debug!(name, "startup SceneMax function guard is false");
                    continue;
                }
                let function_actions = actions_with_parent_continuation(
                    instantiate_function_actions(function, args),
                    parent_action_tail(actions, index),
                );
                let result = apply_startup_action_sequence(
                    &function_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    delayed_actions,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth + 1,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Async {
                actions: async_actions,
            } => {
                let _ = apply_startup_action_sequence(
                    async_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    delayed_actions,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth + 1,
                );
            }
            Statement::Repeat {
                times,
                actions: block_actions,
            } => {
                let repeated_actions = actions_with_parent_continuation(
                    repeat_actions(block_actions, *times),
                    parent_action_tail(actions, index),
                );
                let result = apply_startup_action_sequence(
                    &repeated_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    delayed_actions,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth + 1,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::If(statement) => {
                let selected_actions = if condition_matches(
                    &statement.condition,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    &statement.actions
                } else {
                    &statement.else_actions
                };
                let selected_actions = actions_with_parent_continuation(
                    selected_actions.clone(),
                    parent_action_tail(actions, index),
                );
                let result = apply_startup_action_sequence(
                    &selected_actions,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    delayed_actions,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth + 1,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Guarded {
                condition,
                actions: block_actions,
            } => {
                if condition_matches(
                    condition,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    let guarded_actions = actions_with_parent_continuation(
                        block_actions.clone(),
                        parent_action_tail(actions, index),
                    );
                    let result = apply_startup_action_sequence(
                        &guarded_actions,
                        commands,
                        vars,
                        object_pools,
                        camera_system,
                        delayed_actions,
                        ui_queue,
                        functions_by_name,
                        entities_by_name,
                        transforms_by_name,
                        gltfs_by_name,
                        guards_by_name,
                        depth + 1,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                    return ActionSequenceResult::Completed;
                }
            }
            action if blocking_timed_action_seconds(action).is_some() => {
                let seconds = blocking_timed_action_seconds(action).unwrap_or_default();
                let result = apply_startup_action(
                    action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth,
                );
                if result.should_stop_parent() {
                    return result;
                }
                if enqueue_delayed_actions(
                    Some(delayed_actions),
                    seconds,
                    actions[index + 1..].to_vec(),
                    None,
                    None,
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            action => {
                let result = apply_startup_action(
                    action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
        }
    }

    ActionSequenceResult::Completed
}

fn apply_startup_function_by_name(
    name: &str,
    args: &[String],
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
    depth: usize,
) -> ActionSequenceResult {
    if depth > 8 {
        tracing::warn!(name, "skipping deeply recursive startup SceneMax run");
        return ActionSequenceResult::Completed;
    }
    let Some(function) = functions_by_name.get(name) else {
        tracing::debug!(name, "startup SceneMax function was not parsed");
        return ActionSequenceResult::Completed;
    };
    if !function_guard_matches(
        function,
        args,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        None,
    ) {
        tracing::debug!(name, "startup SceneMax function guard is false");
        return ActionSequenceResult::Completed;
    }

    tracing::info!(name, "running SceneMax startup function");
    let actions = instantiate_function_actions(function, args);
    for action in &actions {
        let result = apply_startup_action(
            action,
            commands,
            vars,
            object_pools,
            camera_system,
            ui_queue,
            functions_by_name,
            entities_by_name,
            transforms_by_name,
            gltfs_by_name,
            guards_by_name,
            depth,
        );
        if result.should_stop_parent() {
            return result;
        }
    }
    ActionSequenceResult::Completed
}

fn apply_startup_action(
    action: &Statement,
    commands: &mut Commands,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    camera_system: &mut SceneMaxCameraSystem,
    ui_queue: &mut SceneMaxUiActionQueue,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    entities_by_name: &HashMap<String, Entity>,
    transforms_by_name: &mut HashMap<String, Transform>,
    gltfs_by_name: &HashMap<String, Handle<Gltf>>,
    guards_by_name: &HashMap<String, Condition>,
    depth: usize,
) -> ActionSequenceResult {
    match action {
        Statement::Assignment(assignment) | Statement::LocalAssignment(assignment) => {
            apply_assignment(
                assignment,
                vars,
                Some(transforms_by_name),
                guards_by_name,
                None,
            );
            ActionSequenceResult::Completed
        }
        Statement::CameraSystemSelect { name } => {
            select_camera_system(name, camera_system);
            ActionSequenceResult::Completed
        }
        Statement::CameraAttach(attach) => {
            attach_camera(attach, object_pools, None, camera_system);
            ActionSequenceResult::Completed
        }
        Statement::CameraChase { target } => {
            chase_camera(target, object_pools, None, camera_system);
            ActionSequenceResult::Completed
        }
        Statement::CameraAttachStop => {
            stop_camera_attachment(camera_system);
            ActionSequenceResult::Completed
        }
        Statement::Logger(logger) => {
            apply_logger_statement(
                logger,
                vars,
                None,
                guards_by_name,
                Some(transforms_by_name),
                None,
            );
            ActionSequenceResult::Completed
        }
        Statement::UiLoad { name } => {
            ui_queue
                .actions
                .push(SceneMaxUiAction::Load { name: name.clone() });
            ActionSequenceResult::Completed
        }
        Statement::UiShowHide(show_hide) => {
            ui_queue.actions.push(SceneMaxUiAction::ShowHide {
                target: show_hide.target.clone(),
                visible: show_hide.visible,
            });
            ActionSequenceResult::Completed
        }
        Statement::UiMessage(message) => {
            ui_queue.actions.push(SceneMaxUiAction::Message {
                target: message.target.clone(),
                text: message.text.clone(),
                effects: message.effects.clone(),
                duration_seconds: message.duration_seconds,
            });
            ActionSequenceResult::Completed
        }
        Statement::UiEase(ease) => {
            ui_queue.actions.push(SceneMaxUiAction::Ease {
                target: ease.target.clone(),
                easing: ease.easing.clone(),
                direction: ease.direction,
                duration_seconds: ease.duration_seconds,
            });
            ActionSequenceResult::Completed
        }
        Statement::UiSetProperty(property) => {
            ui_queue.actions.push(SceneMaxUiAction::SetProperty {
                target: property.target.clone(),
                property: property.property.clone(),
                value: property.value.clone(),
            });
            ActionSequenceResult::Completed
        }
        Statement::RunFunction { name, args } => {
            match apply_startup_function_by_name(
                name,
                args,
                commands,
                vars,
                object_pools,
                camera_system,
                ui_queue,
                functions_by_name,
                entities_by_name,
                transforms_by_name,
                gltfs_by_name,
                guards_by_name,
                depth + 1,
            ) {
                ActionSequenceResult::Suspended => ActionSequenceResult::Suspended,
                ActionSequenceResult::Completed | ActionSequenceResult::Returned => {
                    ActionSequenceResult::Completed
                }
            }
        }
        Statement::Async { actions } => {
            for nested_action in actions {
                let result = apply_startup_action(
                    nested_action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::If(statement) => {
            let selected_actions = if condition_matches(
                &statement.condition,
                vars,
                guards_by_name,
                Some(transforms_by_name),
                None,
            ) {
                &statement.actions
            } else {
                &statement.else_actions
            };
            for nested_action in selected_actions {
                let result = apply_startup_action(
                    nested_action,
                    commands,
                    vars,
                    object_pools,
                    camera_system,
                    ui_queue,
                    functions_by_name,
                    entities_by_name,
                    transforms_by_name,
                    gltfs_by_name,
                    guards_by_name,
                    depth,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::Guarded { condition, actions } => {
            if condition_matches(
                condition,
                vars,
                guards_by_name,
                Some(transforms_by_name),
                None,
            ) {
                for nested_action in actions {
                    let result = apply_startup_action(
                        nested_action,
                        commands,
                        vars,
                        object_pools,
                        camera_system,
                        ui_queue,
                        functions_by_name,
                        entities_by_name,
                        transforms_by_name,
                        gltfs_by_name,
                        guards_by_name,
                        depth,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::Repeat { times, actions } => {
            for _ in 0..*times {
                for nested_action in actions {
                    let result = apply_startup_action(
                        nested_action,
                        commands,
                        vars,
                        object_pools,
                        camera_system,
                        ui_queue,
                        functions_by_name,
                        entities_by_name,
                        transforms_by_name,
                        gltfs_by_name,
                        guards_by_name,
                        depth,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::DoWhile { condition, actions } => {
            for _ in 0..128 {
                for nested_action in actions {
                    let result = apply_startup_action(
                        nested_action,
                        commands,
                        vars,
                        object_pools,
                        camera_system,
                        ui_queue,
                        functions_by_name,
                        entities_by_name,
                        transforms_by_name,
                        gltfs_by_name,
                        guards_by_name,
                        depth,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                }
                if !condition_matches(
                    condition,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    None,
                ) {
                    break;
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::Visibility { target, visible } => {
            if let Some(entity) = entities_by_name.get(target) {
                commands.entity(*entity).insert(if *visible {
                    Visibility::Inherited
                } else {
                    Visibility::Hidden
                });
            }
            ActionSequenceResult::Completed
        }
        Statement::Animate(animation) => {
            if let (Some(entity), Some(gltf)) = (
                entities_by_name.get(&animation.target),
                gltfs_by_name.get(&animation.target),
            ) {
                commands.entity(*entity).insert(AnimationToPlay {
                    clip: animation.clip.clone(),
                    looped: animation.looped,
                    speed: animation.speed,
                    gltf: gltf.clone(),
                });
            }
            ActionSequenceResult::Completed
        }
        Statement::AnimationSpeed(animation_speed) => {
            if let Some(entity) = entities_by_name.get(&animation_speed.target) {
                commands
                    .entity(*entity)
                    .insert(animation_speed_override(animation_speed));
            }
            ActionSequenceResult::Completed
        }
        Statement::CharacterMode(character_mode) => {
            if let Some(entity) = entities_by_name.get(&character_mode.target) {
                commands
                    .entity(*entity)
                    .insert(PendingCharacterMode(character_mode.clone()));
            }
            ActionSequenceResult::Completed
        }
        Statement::ClearCharacterMode { target } => {
            if let Some(entity) = entities_by_name.get(target) {
                clear_character_mode(commands, *entity);
            }
            ActionSequenceResult::Completed
        }
        Statement::CharacterIgnore(ignore) => {
            tracing::debug!(
                target = ignore.target,
                ignored = ignore.ignored,
                "SceneMax character.ignore is handled by collision layers"
            );
            ActionSequenceResult::Completed
        }
        Statement::LookAt { target, subject } => {
            let (Some(entity), Some(target_transform), Some(subject_transform)) = (
                entities_by_name.get(target),
                transforms_by_name.get(target).copied(),
                lookup_subject_transform(subject, transforms_by_name),
            ) else {
                return ActionSequenceResult::Completed;
            };
            let mut updated = target_transform;
            look_at_scenemax_forward(&mut updated, subject_transform.translation);
            commands.entity(*entity).insert(updated);
            transforms_by_name.insert(target.clone(), updated);
            ActionSequenceResult::Completed
        }
        Statement::Position(position) => {
            let Some(entity) = entities_by_name.get(&position.target) else {
                return ActionSequenceResult::Completed;
            };
            let Some(translation) = evaluate_position_statement(position, transforms_by_name)
            else {
                return ActionSequenceResult::Completed;
            };
            let mut transform = transforms_by_name
                .get(&position.target)
                .copied()
                .unwrap_or_default();
            transform.translation = translation;
            commands.entity(*entity).insert(transform);
            transforms_by_name.insert(position.target.clone(), transform);
            ActionSequenceResult::Completed
        }
        Statement::Turn(turn) => {
            if let Some(entity) = entities_by_name.get(&turn.target) {
                commands
                    .entity(*entity)
                    .insert(timed_turn_from_statement(turn));
            }
            ActionSequenceResult::Completed
        }
        Statement::Move(movement) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&movement.target),
                transforms_by_name.get(&movement.target),
            ) {
                append_timed_move(
                    commands,
                    *entity,
                    timed_move_from_statement(movement, transform),
                );
            }
            ActionSequenceResult::Completed
        }
        Statement::MoveTo(move_to) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&move_to.target),
                transforms_by_name.get(&move_to.target),
            ) {
                if let Some(timed_move) =
                    timed_move_to_from_statement(move_to, transform, transforms_by_name)
                {
                    append_timed_move(commands, *entity, timed_move);
                }
            }
            ActionSequenceResult::Completed
        }
        Statement::CharacterJump(jump) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&jump.target),
                transforms_by_name.get(&jump.target),
            ) {
                commands
                    .entity(*entity)
                    .insert(timed_jump_from_statement(jump, transform));
            }
            ActionSequenceResult::Completed
        }
        Statement::PhysicsImpulse(impulse) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&impulse.target),
                transforms_by_name.get(&impulse.target),
            ) {
                apply_physics_impulse(commands, *entity, transform, impulse);
            }
            ActionSequenceResult::Completed
        }
        Statement::PhysicsStop { target } => {
            if let Some(entity) = entities_by_name.get(target) {
                apply_physics_stop(commands, *entity);
            }
            ActionSequenceResult::Completed
        }
        Statement::PhysicsThrowAt(throw_at) => {
            if let (Some(entity), Some(transform)) = (
                entities_by_name.get(&throw_at.target),
                transforms_by_name.get(&throw_at.target),
            ) {
                apply_physics_throw_at(
                    commands,
                    *entity,
                    transform,
                    throw_at,
                    vars,
                    transforms_by_name,
                );
            }
            ActionSequenceResult::Completed
        }
        Statement::Return | Statement::ReturnValue { .. } => ActionSequenceResult::Returned,
        _ => ActionSequenceResult::Completed,
    }
}

fn switch_scene_on_key(
    keyboard: Res<ButtonInput<KeyCode>>,
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    mut startup_program: ResMut<SceneMaxStartupProgram>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut recurring_timers: ResMut<RecurringRunTimers>,
    mut physics_contacts: ResMut<SceneMaxPhysicsContacts>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut scene_queries: ParamSet<(
        Query<Entity, With<SceneMaxEntity>>,
        Query<&mut Transform, With<Camera3d>>,
    )>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    mut character_configs: ResMut<Assets<SceneMaxControlSchemeConfig>>,
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
            for entity in &scene_queries.p0() {
                commands.entity(entity).despawn();
            }

            if let Ok(mut camera) = scene_queries.p1().single_mut() {
                *camera = camera_transform_from_program(&program);
            }

            vars.0.clear();
            object_pools.aliases.clear();
            object_pools.pools.clear();
            delayed_actions.actions.clear();
            recurring_timers.remaining_by_statement.clear();
            physics_contacts.active_pairs.clear();
            collider_bounds.clear();
            apply_initial_assignments(&program, &mut vars);
            apply_camera_systems(&program, &mut camera_system);
            let mut scene_ui_queue = SceneMaxUiActionQueue::default();
            spawn_scenemax_program(
                &mut commands,
                &asset_server,
                asset_root,
                context.builtin_asset_root.as_deref(),
                &program,
                &mut vars,
                &mut object_pools,
                &mut camera_system,
                &mut delayed_actions,
                &mut scene_ui_queue,
                &mut collider_bounds,
                &mut meshes,
                &mut materials,
                &mut character_configs,
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
    time: Res<Time>,
    keyboard: Res<ButtonInput<KeyCode>>,
    startup_program: Res<SceneMaxStartupProgram>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    animation_durations: Res<SceneMaxAnimationDurations>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        return;
    };
    if pending_key_switch(program, &keyboard).is_some() {
        return;
    }

    let mut transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    apply_transform_aliases(&mut transforms_by_name, &object_pools);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for statement in &program.statements {
        let Statement::KeyEvent(event) = statement else {
            continue;
        };
        if !key_event_matches(&event.key, event.trigger, &keyboard) {
            continue;
        }
        let guard_matches = event.guard.as_ref().is_none_or(|guard| {
            condition_matches(
                guard,
                &vars,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(&collider_bounds),
            )
        });
        if !guard_matches {
            write_key_event_probe(
                "KEY:SKIP",
                &event.key,
                event.trigger,
                &vars,
                &guards_by_name,
                &transforms_by_name,
                &collider_bounds,
            );
            continue;
        }
        write_key_event_probe(
            "KEY:FIRE",
            &event.key,
            event.trigger,
            &vars,
            &guards_by_name,
            &transforms_by_name,
            &collider_bounds,
        );

        let mut queued_animations = HashMap::new();
        let continuous_delta_seconds =
            (event.trigger == KeyTrigger::Pressed).then_some(time.delta_secs());
        apply_action_sequence(
            &event.actions,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            None,
            None,
            continuous_delta_seconds,
            &mut commands,
            &mut scene_entities,
        );
    }
}

fn update_avian_collision_contacts(
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

fn collision_event_pair(
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

fn collision_event_entity_name(
    collider: Entity,
    body: Option<Entity>,
    scene_entities: &Query<&SceneMaxEntity>,
) -> Option<String> {
    body.and_then(|body| scene_entities.get(body).ok())
        .or_else(|| scene_entities.get(collider).ok())
        .map(|entity| entity.name.clone())
}

fn apply_when_events(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    animation_durations: Res<SceneMaxAnimationDurations>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut active_collisions: ResMut<ActiveCollisionEvents>,
    mut active_controllers: ResMut<ActiveActionControllers>,
    physics_contacts: Res<SceneMaxPhysicsContacts>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        active_collisions.active_by_statement.clear();
        active_controllers.running.clear();
        return;
    };

    let mut transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    apply_transform_aliases(&mut transforms_by_name, &object_pools);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for (statement_index, statement) in program.statements.iter().enumerate() {
        let Statement::WhenEvent(event) = statement else {
            continue;
        };
        let guard_matches = event.guard.as_ref().is_none_or(|guard| {
            condition_matches(
                guard,
                &vars,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(&collider_bounds),
            )
        });
        let condition_matches_now = when_condition_matches(
            &event.condition,
            &vars,
            &guards_by_name,
            Some(&transforms_by_name),
            Some(&collider_bounds),
            &physics_contacts,
            &object_pools,
        );
        if let Some(after_condition) = &event.after_condition {
            let after_matches = when_condition_matches(
                after_condition,
                &vars,
                &guards_by_name,
                Some(&transforms_by_name),
                Some(&collider_bounds),
                &physics_contacts,
                &object_pools,
            );
            if !guard_matches {
                active_collisions
                    .transition_armed_by_statement
                    .remove(&statement_index);
                continue;
            }
            if after_matches {
                active_collisions
                    .transition_armed_by_statement
                    .insert(statement_index);
                active_controllers
                    .running
                    .remove(&SceneMaxControllerKey::When(statement_index));
                continue;
            }
            if !condition_matches_now {
                active_controllers
                    .running
                    .remove(&SceneMaxControllerKey::When(statement_index));
                continue;
            }
            if !active_collisions
                .transition_armed_by_statement
                .remove(&statement_index)
            {
                continue;
            }
        }
        let is_collision_event = condition_contains_collision(&event.condition);
        if !guard_matches || !condition_matches_now {
            if is_collision_event {
                active_collisions
                    .active_by_statement
                    .remove(&statement_index);
            }
            active_controllers
                .running
                .remove(&SceneMaxControllerKey::When(statement_index));
            continue;
        }
        if is_collision_event
            && !active_collisions
                .active_by_statement
                .insert(statement_index)
        {
            continue;
        }
        if is_collision_event {
            write_collision_event_probe(
                statement_index,
                &event.condition,
                &transforms_by_name,
                &collider_bounds,
                &physics_contacts,
                &object_pools,
            );
        }
        let owner = SceneMaxControllerKey::When(statement_index);
        if active_controllers.running.contains(&owner) {
            continue;
        }

        let mut queued_animations = HashMap::new();
        active_controllers.running.insert(owner.clone());
        let result = apply_action_sequence(
            &event.actions,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            Some(owner.clone()),
            None,
            Some(time.delta_secs()),
            &mut commands,
            &mut scene_entities,
        );
        if !result.is_suspended() {
            active_controllers.running.remove(&owner);
        }
    }
    clear_transient_hit_flags(&mut vars);
}

fn clear_transient_hit_flags(vars: &mut SceneMaxVars) {
    scenemax_runtime_vm_core::clear_transient_hit_flags(vars);
}

fn write_collision_event_probe(
    statement_index: usize,
    condition: &Condition,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) {
    let Some(report) = collision_condition_report(
        condition,
        transforms_by_name,
        collider_bounds,
        physics_contacts,
        object_pools,
    ) else {
        return;
    };
    write_runtime_log_line(
        LoggerLevel::Info,
        &format!(
            "COLL:FIRE stmt={} source={} target={} avian={} fallback={} distance={} threshold={} owner_distance={}",
            statement_index,
            report.source,
            report.target,
            report.avian_contact as u8,
            report.fallback_match as u8,
            format_scenemax_number(report.distance),
            format_scenemax_number(report.threshold),
            format_scenemax_number(report.owner_distance),
        ),
    );
}

#[derive(Debug)]
struct CollisionConditionReport {
    source: String,
    target: String,
    avian_contact: bool,
    fallback_match: bool,
    distance: f32,
    threshold: f32,
    owner_distance: f32,
}

fn collision_condition_report(
    condition: &Condition,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) -> Option<CollisionConditionReport> {
    match condition {
        Condition::Collision { sources, target } => collision_pair_report(
            sources,
            target,
            transforms_by_name,
            collider_bounds,
            physics_contacts,
            object_pools,
        ),
        Condition::And(conditions) | Condition::Or(conditions) => {
            conditions.iter().find_map(|condition| {
                collision_condition_report(
                    condition,
                    transforms_by_name,
                    collider_bounds,
                    physics_contacts,
                    object_pools,
                )
            })
        }
        Condition::Not(condition) => collision_condition_report(
            condition,
            transforms_by_name,
            collider_bounds,
            physics_contacts,
            object_pools,
        ),
        _ => None,
    }
}

fn collision_pair_report(
    sources: &[String],
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) -> Option<CollisionConditionReport> {
    let target_candidates = collision_reference_candidates_with_alias(target, object_pools);
    let mut best: Option<CollisionConditionReport> = None;
    for source in sources {
        let source_candidates = collision_reference_candidates_with_alias(source, object_pools);
        for source_name in &source_candidates {
            for target_name in &target_candidates {
                let avian_contact =
                    active_physics_contact_matches(source_name, target_name, physics_contacts);
                let (distance, threshold, fallback_match, owner_distance) =
                    collision_pair_distance_report(
                        source_name,
                        target_name,
                        transforms_by_name,
                        collider_bounds,
                    );
                let report = CollisionConditionReport {
                    source: source_name.clone(),
                    target: target_name.clone(),
                    avian_contact,
                    fallback_match,
                    distance,
                    threshold,
                    owner_distance,
                };
                if report.avian_contact || report.fallback_match {
                    return Some(report);
                }
                if best
                    .as_ref()
                    .is_none_or(|best| report.distance < best.distance)
                {
                    best = Some(report);
                }
            }
        }
    }
    best
}

fn collision_pair_distance_report(
    source: &str,
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
    collider_bounds: &SceneMaxColliderBounds,
) -> (f32, f32, bool, f32) {
    let source_exact = transforms_by_name.get(source).copied();
    let target_exact = transforms_by_name.get(target).copied();
    let owner_distance = collision_owner_distance(source, target, transforms_by_name);
    let Some(source_transform) =
        source_exact.or_else(|| collision_owner_transform(source, transforms_by_name))
    else {
        return (f32::INFINITY, 0.0, false, owner_distance);
    };
    let Some(target_transform) =
        target_exact.or_else(|| collision_owner_transform(target, transforms_by_name))
    else {
        return (f32::INFINITY, 0.0, false, owner_distance);
    };
    let threshold = if source_exact.is_some() && target_exact.is_some() {
        exact_collision_threshold(source, target, Some(collider_bounds))
    } else {
        collision_threshold(source, target)
    };
    let distance = source_transform
        .translation
        .distance(target_transform.translation);
    let matches =
        if let (Some(source_transform), Some(target_transform)) = (source_exact, target_exact) {
            if !player_hitbox_owner_distance_allows(source, target, transforms_by_name) {
                false
            } else {
                exact_collider_shapes_overlap(
                    source,
                    source_transform,
                    target,
                    target_transform,
                    Some(collider_bounds),
                )
                .unwrap_or(distance <= threshold)
            }
        } else {
            distance <= threshold
        };
    (distance, threshold, matches, owner_distance)
}

fn update_recurring_runs(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    animation_durations: Res<SceneMaxAnimationDurations>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut recurring_timers: ResMut<RecurringRunTimers>,
    mut active_controllers: ResMut<ActiveActionControllers>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        recurring_timers.remaining_by_statement.clear();
        active_controllers.running.clear();
        return;
    };

    let delta = time.delta_secs();
    let mut due_runs = Vec::new();
    for (index, statement) in program.statements.iter().enumerate() {
        let Statement::RunEvery {
            name,
            args,
            interval_seconds,
        } = statement
        else {
            continue;
        };
        let interval = interval_seconds.max(0.001);
        let remaining = recurring_timers
            .remaining_by_statement
            .entry(index)
            .or_insert(interval);
        *remaining -= delta;
        if *remaining <= 0.0 {
            let owner = SceneMaxControllerKey::Recurring(index);
            if active_controllers.running.contains(&owner) {
                *remaining = 0.0;
            } else {
                due_runs.push((index, name.clone(), args.clone()));
                while *remaining <= 0.0 {
                    *remaining += interval;
                }
            }
        }
    }

    if due_runs.is_empty() {
        return;
    }

    let mut transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    apply_transform_aliases(&mut transforms_by_name, &object_pools);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for (index, name, args) in due_runs {
        let mut queued_animations = HashMap::new();
        let owner = SceneMaxControllerKey::Recurring(index);
        active_controllers.running.insert(owner.clone());
        let result = apply_function_by_name(
            &name,
            &args,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            Some(owner.clone()),
            None,
            None,
            &mut commands,
            &mut scene_entities,
            0,
        );
        if !result.is_suspended() {
            active_controllers.running.remove(&owner);
        }
    }
}

fn update_delayed_actions(
    time: Res<Time>,
    startup_program: Res<SceneMaxStartupProgram>,
    runtime_assets: Res<SceneMaxRuntimeAssets>,
    animation_durations: Res<SceneMaxAnimationDurations>,
    mut collider_bounds: ResMut<SceneMaxColliderBounds>,
    mut vars: ResMut<SceneMaxVars>,
    mut object_pools: ResMut<SceneMaxObjectPools>,
    mut camera_system: ResMut<SceneMaxCameraSystem>,
    mut delayed_actions: ResMut<DelayedActionQueue>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut active_controllers: ResMut<ActiveActionControllers>,
    mut commands: Commands,
    mut scene_entities: ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) {
    let Some(program) = startup_program.0.as_ref() else {
        delayed_actions.actions.clear();
        active_controllers.running.clear();
        return;
    };

    let delta = time.delta_secs();
    let mut ready_actions = Vec::new();
    let mut pending_actions = Vec::new();
    for mut delayed in delayed_actions.actions.drain(..) {
        delayed.remaining_seconds -= delta;
        if delayed.remaining_seconds <= 0.0 {
            ready_actions.push(delayed);
        } else {
            pending_actions.push(delayed);
        }
    }
    delayed_actions.actions = pending_actions;

    if ready_actions.is_empty() {
        return;
    }

    let mut transforms_by_name = scene_entities
        .p0()
        .iter()
        .map(|(entity, transform)| (entity.name.clone(), *transform))
        .collect::<HashMap<_, _>>();
    apply_transform_aliases(&mut transforms_by_name, &object_pools);
    let functions_by_name = collect_functions_by_name(program);
    let guards_by_name = collect_guards_by_name(program);

    for mut delayed in ready_actions {
        if delayed.actions.is_empty() {
            if let Some(owner) = delayed.owner {
                active_controllers.running.remove(&owner);
            }
            continue;
        }
        let mut queued_animations = HashMap::new();
        let result = apply_action_sequence(
            &delayed.actions,
            &mut transforms_by_name,
            &mut vars,
            &mut object_pools,
            Some(&mut camera_system),
            &functions_by_name,
            &guards_by_name,
            &mut queued_animations,
            &runtime_assets,
            &animation_durations,
            &mut collider_bounds,
            Some(&mut delayed_actions),
            Some(&mut ui_queue),
            delayed.owner.clone(),
            delayed.scope.as_mut(),
            None,
            &mut commands,
            &mut scene_entities,
        );
        if !result.is_suspended() {
            if let Some(owner) = delayed.owner {
                active_controllers.running.remove(&owner);
            }
        }
    }
}

fn enqueue_delayed_actions(
    delayed_actions: Option<&mut DelayedActionQueue>,
    seconds: f32,
    actions: Vec<Statement>,
    owner: Option<SceneMaxControllerKey>,
    scope: Option<SceneMaxScopeFrame>,
) -> bool {
    let Some(delayed_actions) = delayed_actions else {
        return false;
    };
    if actions.is_empty() && owner.is_none() {
        return false;
    }
    delayed_actions.actions.push(DelayedActions {
        remaining_seconds: seconds.max(0.0),
        actions,
        owner,
        scope,
    });
    true
}

fn apply_action_sequence(
    actions: &[Statement],
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    mut camera_system: Option<&mut SceneMaxCameraSystem>,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    queued_animations: &mut HashMap<Entity, (String, bool)>,
    runtime_assets: &SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    mut delayed_actions: Option<&mut DelayedActionQueue>,
    mut ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) -> ActionSequenceResult {
    apply_scoped_transform_aliases(transforms_by_name, scope.as_deref());

    for (index, action) in actions.iter().enumerate() {
        match action {
            Statement::NoOp { .. } => {}
            Statement::Unsupported { text } => {
                tracing::debug!(text, "skipping unsupported SceneMax runtime action");
            }
            Statement::Return | Statement::ReturnValue { .. } => {
                return ActionSequenceResult::Returned;
            }
            Statement::Wait { seconds } => {
                let remaining = actions[index + 1..].to_vec();
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    *seconds,
                    remaining,
                    owner.clone(),
                    scope.as_deref().cloned(),
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::WaitValue { value } => {
                let seconds = resolve_assignment_value_scoped_with_guards(
                    value,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                )
                .unwrap_or_default();
                let remaining = actions[index + 1..].to_vec();
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    seconds,
                    remaining,
                    owner.clone(),
                    scope.as_deref().cloned(),
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::WaitUntil { condition } => {
                if !condition_matches_scoped(
                    condition,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    if enqueue_delayed_actions(
                        delayed_actions.as_deref_mut(),
                        LOOP_CONTINUE_DELAY_SECONDS,
                        actions[index..].to_vec(),
                        owner.clone(),
                        scope.as_deref().cloned(),
                    ) {
                        return ActionSequenceResult::Suspended;
                    }
                    return ActionSequenceResult::Completed;
                }
            }
            Statement::AnimationSpeed(animation_speed)
                if animation_speed.condition.is_some()
                    && !animation_speed_condition_matches(
                        animation_speed,
                        vars,
                        object_pools,
                        guards_by_name,
                        Some(transforms_by_name),
                        Some(collider_bounds),
                        scene_entities,
                    ) =>
            {
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    LOOP_CONTINUE_DELAY_SECONDS,
                    actions[index..].to_vec(),
                    owner.clone(),
                    scope.as_deref().cloned(),
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::RunFunction { name, args } => {
                let Some(function) = functions_by_name.get(name) else {
                    tracing::debug!(
                        name,
                        "SceneMax function is not implemented or was not parsed"
                    );
                    continue;
                };
                if !function_guard_matches(
                    function,
                    args,
                    vars,
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    tracing::debug!(name, "SceneMax function guard is false");
                    continue;
                }

                let function_actions = actions_with_parent_continuation(
                    instantiate_function_actions(function, args),
                    parent_action_tail(actions, index),
                );
                let mut function_scope = scope.as_deref().cloned().unwrap_or_default();
                let result = apply_action_sequence(
                    &function_actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    Some(&mut function_scope),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Async { actions } => {
                let _ = apply_action_sequence(
                    actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    None,
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
            }
            Statement::Repeat {
                times,
                actions: block_actions,
            } => {
                let repeated_actions = actions_with_parent_continuation(
                    repeat_actions(block_actions, *times),
                    parent_action_tail(actions, index),
                );
                let result = apply_action_sequence(
                    &repeated_actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::DoWhile {
                condition,
                actions: block_actions,
            } => {
                let mut loop_actions = block_actions.clone();
                loop_actions.push(Statement::LoopContinue {
                    condition: condition.clone(),
                    actions: block_actions.clone(),
                });
                loop_actions = actions_with_parent_continuation(
                    loop_actions,
                    parent_action_tail(actions, index),
                );
                let result = apply_action_sequence(
                    &loop_actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::LoopContinue {
                condition,
                actions: block_actions,
            } => {
                if condition_matches_scoped(
                    condition,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    let mut loop_and_tail = vec![Statement::DoWhile {
                        condition: condition.clone(),
                        actions: block_actions.clone(),
                    }];
                    loop_and_tail.extend_from_slice(parent_action_tail(actions, index));
                    if enqueue_delayed_actions(
                        delayed_actions.as_deref_mut(),
                        LOOP_CONTINUE_DELAY_SECONDS,
                        loop_and_tail,
                        owner.clone(),
                        scope.as_deref().cloned(),
                    ) {
                        return ActionSequenceResult::Suspended;
                    }
                }
            }
            Statement::If(statement) => {
                let selected_actions = if condition_matches_scoped(
                    &statement.condition,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    &statement.actions
                } else {
                    &statement.else_actions
                };
                let selected_actions = actions_with_parent_continuation(
                    selected_actions.clone(),
                    parent_action_tail(actions, index),
                );
                let result = apply_action_sequence(
                    &selected_actions,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Guarded {
                condition,
                actions: block_actions,
            } => {
                if condition_matches_scoped(
                    condition,
                    vars,
                    scope.as_deref(),
                    guards_by_name,
                    Some(transforms_by_name),
                    Some(collider_bounds),
                ) {
                    let guarded_actions = actions_with_parent_continuation(
                        block_actions.clone(),
                        parent_action_tail(actions, index),
                    );
                    let result = apply_action_sequence(
                        &guarded_actions,
                        transforms_by_name,
                        vars,
                        object_pools,
                        camera_system.as_deref_mut(),
                        functions_by_name,
                        guards_by_name,
                        queued_animations,
                        runtime_assets,
                        animation_durations,
                        collider_bounds,
                        delayed_actions.as_deref_mut(),
                        ui_queue.as_deref_mut(),
                        owner.clone(),
                        scope.as_deref_mut(),
                        continuous_delta_seconds,
                        commands,
                        scene_entities,
                    );
                    if result.should_stop_parent() {
                        return result;
                    }
                    return ActionSequenceResult::Completed;
                }
            }
            action if blocking_timed_action_seconds(action).is_some() => {
                let seconds = blocking_timed_action_seconds(action).unwrap_or_default();
                apply_key_action(
                    action,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    None,
                    commands,
                    scene_entities,
                );
                let remaining = actions[index + 1..].to_vec();
                if enqueue_delayed_actions(
                    delayed_actions.as_deref_mut(),
                    seconds,
                    remaining,
                    owner.clone(),
                    scope.as_deref().cloned(),
                ) {
                    return ActionSequenceResult::Suspended;
                }
                return ActionSequenceResult::Completed;
            }
            Statement::Animate(animation) => {
                apply_key_action(
                    action,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if animation.blocking {
                    let remaining = actions[index + 1..].to_vec();
                    if enqueue_delayed_actions(
                        delayed_actions.as_deref_mut(),
                        estimated_animation_seconds(animation, animation_durations),
                        remaining,
                        owner.clone(),
                        scope.as_deref().cloned(),
                    ) {
                        return ActionSequenceResult::Suspended;
                    }
                    return ActionSequenceResult::Completed;
                }
            }
            action => {
                let result = apply_key_action(
                    action,
                    transforms_by_name,
                    vars,
                    object_pools,
                    camera_system.as_deref_mut(),
                    functions_by_name,
                    guards_by_name,
                    queued_animations,
                    runtime_assets,
                    animation_durations,
                    collider_bounds,
                    delayed_actions.as_deref_mut(),
                    ui_queue.as_deref_mut(),
                    owner.clone(),
                    scope.as_deref_mut(),
                    continuous_delta_seconds,
                    commands,
                    scene_entities,
                );
                if result.should_stop_parent() {
                    return result;
                }
            }
        }
    }
    ActionSequenceResult::Completed
}

fn blocking_timed_action_seconds(action: &Statement) -> Option<f32> {
    match action {
        Statement::Turn(turn)
            if !turn.async_run
                && turn.loop_condition.is_none()
                && turn.duration_seconds > f32::EPSILON =>
        {
            Some(turn.duration_seconds.max(0.001))
        }
        Statement::Move(movement)
            if !movement.async_run
                && movement.loop_condition.is_none()
                && movement.duration_seconds > f32::EPSILON =>
        {
            Some(movement.duration_seconds.max(0.001))
        }
        Statement::MoveTo(move_to)
            if !move_to.async_run && move_to.duration_seconds > f32::EPSILON =>
        {
            Some(move_to.duration_seconds.max(0.001))
        }
        Statement::CharacterJump(jump) if !jump.async_run => {
            Some(jump_duration_seconds(jump.speed))
        }
        _ => None,
    }
}

fn estimated_animation_seconds(
    animation: &AnimationStatement,
    animation_durations: &SceneMaxAnimationDurations,
) -> f32 {
    if let Some(duration) = animation_durations.lookup(&animation.target, &animation.clip) {
        return (duration / animation.speed.max(0.1)).clamp(0.08, 3.5);
    }
    if is_jump_animation_clip(&animation.clip) {
        return (jump_duration_seconds(35.0) / animation.speed.max(0.1)).clamp(0.65, 1.4);
    }
    estimated_animation_seconds_from_speed(animation.speed)
}

fn is_jump_animation_clip(clip: &str) -> bool {
    let lower = clip.to_ascii_lowercase();
    lower.contains("jump") || lower.contains("fly_kick") || lower.contains("flying_kick")
}

fn estimated_animation_seconds_from_speed(speed: f32) -> f32 {
    (DEFAULT_ANIMATION_CLIP_SECONDS / speed.max(0.1)).clamp(0.25, 2.0)
}

fn animation_clip_duration_seconds(
    animation_clips: &Assets<AnimationClip>,
    clip: &Handle<AnimationClip>,
) -> f32 {
    animation_clips
        .get(clip)
        .map(AnimationClip::duration)
        .filter(|duration| *duration > 0.0)
        .unwrap_or(DEFAULT_ANIMATION_CLIP_SECONDS)
}

fn apply_runtime_model_decl(
    name: &str,
    resource: &str,
    options: &EntityOptions,
    transforms_by_name: &mut HashMap<String, Transform>,
    runtime_assets: &SceneMaxRuntimeAssets,
    collider_bounds: &mut SceneMaxColliderBounds,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) {
    let transform = primitive_transform_from_options(options);
    for (entity, scene_entity, mut existing_transform, _, _, visibility, _, _) in
        &mut scene_entities.p1()
    {
        if scene_entity.name != name {
            continue;
        }
        *existing_transform = transform;
        if let Some(mut visibility) = visibility {
            *visibility = if options.hidden {
                Visibility::Hidden
            } else {
                Visibility::Inherited
            };
        } else {
            commands.entity(entity).insert(if options.hidden {
                Visibility::Hidden
            } else {
                Visibility::Inherited
            });
        }
        transforms_by_name.insert(name.to_owned(), transform);
        if options.collider {
            register_collider_bounds(collider_bounds, name, options, transform);
        }
        tracing::debug!(
            name,
            resource,
            "updated runtime SceneMax placeholder entity"
        );
        return;
    }

    let visibility = if options.hidden {
        Visibility::Hidden
    } else {
        Visibility::Inherited
    };
    if options.collider {
        spawn_scenemax_collider_decl(commands, name, resource, options, transform, None);
        register_collider_bounds(collider_bounds, name, options, transform);
        transforms_by_name.insert(name.to_owned(), transform);
        tracing::info!(name, resource, "spawned runtime SceneMax collider");
        return;
    }
    let mut entity = commands.spawn((
        SceneMaxEntity {
            name: name.to_owned(),
            runtime_name: format!("{name}@runtime"),
        },
        transform,
        visibility,
    ));
    if let (Some(mesh), Some(material)) = (
        runtime_assets.placeholder_mesh.as_ref(),
        runtime_assets.placeholder_material.as_ref(),
    ) {
        entity.insert((Mesh3d(mesh.clone()), MeshMaterial3d(material.clone())));
    }
    let entity_id = entity.id();
    insert_physics_components(commands, entity_id, name, resource, options, &transform);
    transforms_by_name.insert(name.to_owned(), transform);
    tracing::info!(
        name,
        resource,
        "spawned runtime SceneMax placeholder entity"
    );
}

fn apply_key_action(
    action: &Statement,
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    mut camera_system: Option<&mut SceneMaxCameraSystem>,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    queued_animations: &mut HashMap<Entity, (String, bool)>,
    runtime_assets: &SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    delayed_actions: Option<&mut DelayedActionQueue>,
    mut ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) -> ActionSequenceResult {
    if matches!(action, Statement::NoOp { .. }) {
        return ActionSequenceResult::Completed;
    }
    if let Statement::Unsupported { text } = action {
        tracing::debug!(text, "skipping unsupported SceneMax runtime action");
        return ActionSequenceResult::Completed;
    }
    if let Statement::ModelDecl {
        name,
        resource,
        options,
    } = action
    {
        apply_runtime_model_decl(
            name,
            resource,
            options,
            transforms_by_name,
            runtime_assets,
            collider_bounds,
            commands,
            scene_entities,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::Assignment(assignment) | Statement::LocalAssignment(assignment) = action {
        if let AssignmentValue::PoolAcquire { pool } = &assignment.value {
            let Some(member) = acquire_pool_member(pool, object_pools) else {
                tracing::debug!(pool, "SceneMax object pool has no available members");
                return ActionSequenceResult::Completed;
            };
            if let Some(scope) = scope.as_deref_mut() {
                scope
                    .aliases
                    .insert(assignment.name.clone(), member.clone());
            } else {
                object_pools
                    .aliases
                    .insert(assignment.name.clone(), member.clone());
            }
            for (entity, scene_entity, transform, _, _, visibility, _, _) in
                &mut scene_entities.p1()
            {
                if scene_entity.name == member {
                    if let Some(mut visibility) = visibility {
                        *visibility = Visibility::Inherited;
                    } else {
                        commands.entity(entity).insert(Visibility::Inherited);
                    }
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                    commands
                        .entity(entity)
                        .insert((LinearVelocity::ZERO, AngularVelocity::ZERO));
                    break;
                }
            }
            return ActionSequenceResult::Completed;
        }
        let assigned_value = apply_assignment_scoped(
            assignment,
            vars,
            scope.as_deref_mut(),
            Some(transforms_by_name),
            guards_by_name,
            Some(collider_bounds),
            matches!(action, Statement::LocalAssignment(_)),
        );
        if assignment.name == "action"
            && assigned_value.is_some_and(|value| value.abs() <= f32::EPSILON)
        {
            for (entity, scene_entity, _, gltf, current_animation, _, _, _) in
                &mut scene_entities.p1()
            {
                if scene_entity.name != "player1" {
                    continue;
                }
                let already_queued =
                    queued_animations
                        .get(&entity)
                        .is_some_and(|(clip, looped)| {
                            *looped && requested_animation_names_match(clip, "idle2")
                        });
                let already_current = queued_animations.get(&entity).is_none()
                    && current_animation
                        .is_some_and(|current| current_animation_matches(current, "idle2", true));
                if already_queued || already_current {
                    continue;
                }
                if let Some(gltf) = gltf {
                    commands.entity(entity).insert(AnimationToPlay {
                        clip: "idle2".to_owned(),
                        looped: true,
                        speed: 1.0,
                        gltf: gltf.gltf.clone(),
                    });
                    queued_animations.insert(entity, ("idle2".to_owned(), true));
                }
            }
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::CameraSystemSelect { name } = action {
        if let Some(camera_system) = camera_system {
            select_camera_system(name, camera_system);
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::CameraAttach(attach) = action {
        if let Some(camera_system) = camera_system {
            attach_camera(attach, object_pools, scope.as_deref(), camera_system);
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::CameraChase { target } = action {
        if let Some(camera_system) = camera_system {
            chase_camera(target, object_pools, scope.as_deref(), camera_system);
        }
        return ActionSequenceResult::Completed;
    }
    if matches!(action, Statement::CameraAttachStop) {
        if let Some(camera_system) = camera_system {
            stop_camera_attachment(camera_system);
        }
        return ActionSequenceResult::Completed;
    }
    if let Statement::Logger(logger) = action {
        apply_logger_statement(
            logger,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        );
        return ActionSequenceResult::Completed;
    }
    if let Some(ui_action) = scenemax_ui_action_from_statement(action)
        && let Some(ui_queue) = ui_queue.as_deref_mut()
    {
        ui_queue.actions.push(ui_action);
        return ActionSequenceResult::Completed;
    }
    if let Statement::RunFunction { name, args } = action {
        return match apply_function_by_name(
            name,
            args,
            transforms_by_name,
            vars,
            object_pools,
            camera_system,
            functions_by_name,
            guards_by_name,
            queued_animations,
            runtime_assets,
            animation_durations,
            collider_bounds,
            delayed_actions,
            ui_queue,
            owner,
            scope,
            continuous_delta_seconds,
            commands,
            scene_entities,
            0,
        ) {
            ActionSequenceResult::Suspended => ActionSequenceResult::Suspended,
            ActionSequenceResult::Completed | ActionSequenceResult::Returned => {
                ActionSequenceResult::Completed
            }
        };
    }
    if let Statement::PoolRelease(release) = action {
        release_pool_action(
            release,
            object_pools,
            scope.as_deref_mut(),
            commands,
            scene_entities,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::Delete { target } = action {
        delete_scene_object(
            target,
            object_pools,
            scope.as_deref_mut(),
            commands,
            scene_entities,
        );
        return ActionSequenceResult::Completed;
    }
    if let Statement::If(statement) = action {
        let selected_actions = if condition_matches_scoped(
            &statement.condition,
            vars,
            scope.as_deref(),
            guards_by_name,
            Some(transforms_by_name),
            Some(collider_bounds),
        ) {
            &statement.actions
        } else {
            &statement.else_actions
        };
        for nested_action in selected_actions {
            let result = apply_key_action(
                nested_action,
                transforms_by_name,
                vars,
                object_pools,
                camera_system.as_deref_mut(),
                functions_by_name,
                guards_by_name,
                queued_animations,
                runtime_assets,
                animation_durations,
                collider_bounds,
                None,
                ui_queue.as_deref_mut(),
                owner.clone(),
                scope.as_deref_mut(),
                continuous_delta_seconds,
                commands,
                scene_entities,
            );
            if result.should_stop_parent() {
                return result;
            }
        }
        return ActionSequenceResult::Completed;
    }

    for (
        entity,
        scene_entity,
        mut transform,
        gltf,
        current_animation,
        visibility,
        character_controller,
        mut character_motor,
    ) in &mut scene_entities.p1()
    {
        match action {
            Statement::Animate(animation)
                if target_matches_alias(
                    &animation.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                let already_queued =
                    queued_animations
                        .get(&entity)
                        .is_some_and(|(clip, looped)| {
                            *looped == animation.looped
                                && requested_animation_names_match(clip, &animation.clip)
                        });
                let already_current = animation.looped
                    && queued_animations.get(&entity).is_none()
                    && current_animation.is_some_and(|current| {
                        current_animation_matches(current, &animation.clip, animation.looped)
                    });
                if already_queued || already_current {
                    continue;
                }
                if let Some(gltf) = gltf {
                    commands.entity(entity).insert(AnimationToPlay {
                        clip: animation.clip.clone(),
                        looped: animation.looped,
                        speed: animation.speed,
                        gltf: gltf.gltf.clone(),
                    });
                    queued_animations.insert(entity, (animation.clip.clone(), animation.looped));
                }
            }
            Statement::AnimationSpeed(animation_speed)
                if target_matches_alias(
                    &animation_speed.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                commands
                    .entity(entity)
                    .insert(animation_speed_override(animation_speed));
            }
            Statement::LookAt { target, subject }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(subject_transform) =
                    lookup_subject_transform(subject, transforms_by_name)
                {
                    look_at_scenemax_forward(&mut transform, subject_transform.translation);
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                }
            }
            Statement::Position(position)
                if target_matches_alias(
                    &position.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(translation) = evaluate_position_statement(position, transforms_by_name)
                {
                    transform.translation = translation;
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                }
            }
            Statement::Turn(turn)
                if target_matches_alias(
                    &turn.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(delta_seconds) = continuous_delta_seconds {
                    let timed_turn = timed_turn_from_statement(turn);
                    transform.rotate_y(timed_turn.radians_per_second * delta_seconds);
                    sync_live_transform(
                        transforms_by_name,
                        object_pools,
                        scope.as_deref(),
                        &scene_entity.name,
                        *transform,
                    );
                } else {
                    commands
                        .entity(entity)
                        .insert(timed_turn_from_statement(turn));
                }
            }
            Statement::Move(movement)
                if target_matches_alias(
                    &movement.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                let timed_move = timed_move_from_statement(movement, &transform);
                if let (Some(character_controller), Some(character_motor)) =
                    (character_controller, character_motor.as_deref_mut())
                {
                    set_character_move_intent(
                        character_motor,
                        character_controller,
                        movement,
                        &transform,
                        continuous_delta_seconds,
                    );
                    if let Some(delta_seconds) = continuous_delta_seconds {
                        transform.translation += timed_move.velocity * delta_seconds;
                        sync_live_transform(
                            transforms_by_name,
                            object_pools,
                            scope.as_deref(),
                            &scene_entity.name,
                            *transform,
                        );
                    } else {
                        append_timed_move(commands, entity, timed_move);
                    }
                } else {
                    if let Some(delta_seconds) = continuous_delta_seconds {
                        transform.translation += timed_move.velocity * delta_seconds;
                        sync_live_transform(
                            transforms_by_name,
                            object_pools,
                            scope.as_deref(),
                            &scene_entity.name,
                            *transform,
                        );
                    } else {
                        append_timed_move(commands, entity, timed_move);
                    }
                }
            }
            Statement::MoveTo(move_to)
                if target_matches_alias(
                    &move_to.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(timed_move) =
                    timed_move_to_from_statement(move_to, &transform, transforms_by_name)
                {
                    if let Some(delta_seconds) = continuous_delta_seconds {
                        let delta = delta_seconds.min(timed_move.remaining_seconds);
                        transform.translation += timed_move.velocity * delta;
                        sync_live_transform(
                            transforms_by_name,
                            object_pools,
                            scope.as_deref(),
                            &scene_entity.name,
                            *transform,
                        );
                    } else {
                        append_timed_move(commands, entity, timed_move);
                    }
                }
            }
            Statement::CharacterJump(jump)
                if target_matches_alias(
                    &jump.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                if let Some(character_motor) = character_motor.as_deref_mut() {
                    set_character_jump_intent(character_motor, jump);
                    commands
                        .entity(entity)
                        .insert(timed_jump_from_statement(jump, &transform));
                } else {
                    commands
                        .entity(entity)
                        .insert(timed_jump_from_statement(jump, &transform));
                }
            }
            Statement::CharacterMode(character_mode)
                if target_matches_alias(
                    &character_mode.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                commands
                    .entity(entity)
                    .insert(PendingCharacterMode(character_mode.clone()));
            }
            Statement::ClearCharacterMode { target }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                clear_character_mode(commands, entity);
            }
            Statement::CharacterIgnore(ignore)
                if target_matches_alias(
                    &ignore.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                tracing::debug!(
                    target = ignore.target,
                    ignored = ignore.ignored,
                    "SceneMax character.ignore is handled by collision layers"
                );
            }
            Statement::PhysicsImpulse(impulse)
                if target_matches_alias(
                    &impulse.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                apply_physics_impulse(commands, entity, &transform, impulse);
            }
            Statement::PhysicsStop { target }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                apply_physics_stop(commands, entity);
            }
            Statement::PhysicsThrowAt(throw_at)
                if target_matches_alias(
                    &throw_at.target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
                apply_physics_throw_at(
                    commands,
                    entity,
                    &transform,
                    throw_at,
                    vars,
                    transforms_by_name,
                );
            }
            Statement::Visibility { target, visible }
                if target_matches_alias(
                    target,
                    &scene_entity.name,
                    object_pools,
                    scope.as_deref(),
                ) =>
            {
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
    ActionSequenceResult::Completed
}

fn animation_speed_condition_matches(
    animation_speed: &AnimationSpeedStatement,
    vars: &SceneMaxVars,
    object_pools: &SceneMaxObjectPools,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) -> bool {
    let Some(condition) = animation_speed.condition.as_ref() else {
        return true;
    };
    let mut scoped_vars = SceneMaxVars(vars.0.clone());
    let frames = scene_entities
        .p1()
        .iter()
        .find_map(|(_, scene_entity, _, _, current_animation, _, _, _)| {
            target_matches_alias(
                &animation_speed.target,
                &scene_entity.name,
                object_pools,
                None,
            )
            .then(|| {
                current_animation
                    .map(current_animation_percent)
                    .unwrap_or_default()
            })
        })
        .unwrap_or_default();
    scoped_vars.0.insert("frames".to_owned(), frames);
    scoped_vars
        .0
        .insert(format!("{}.frames", animation_speed.target), frames);
    condition_matches(
        condition,
        &scoped_vars,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
}

fn apply_function_by_name(
    name: &str,
    args: &[String],
    transforms_by_name: &mut HashMap<String, Transform>,
    vars: &mut SceneMaxVars,
    object_pools: &mut SceneMaxObjectPools,
    mut camera_system: Option<&mut SceneMaxCameraSystem>,
    functions_by_name: &HashMap<String, FunctionRuntime>,
    guards_by_name: &HashMap<String, Condition>,
    queued_animations: &mut HashMap<Entity, (String, bool)>,
    runtime_assets: &SceneMaxRuntimeAssets,
    animation_durations: &SceneMaxAnimationDurations,
    collider_bounds: &mut SceneMaxColliderBounds,
    delayed_actions: Option<&mut DelayedActionQueue>,
    ui_queue: Option<&mut SceneMaxUiActionQueue>,
    owner: Option<SceneMaxControllerKey>,
    scope: Option<&mut SceneMaxScopeFrame>,
    continuous_delta_seconds: Option<f32>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
    depth: usize,
) -> ActionSequenceResult {
    if depth > 8 {
        tracing::warn!(name, "skipping deeply recursive SceneMax run");
        return ActionSequenceResult::Completed;
    }
    let Some(function) = functions_by_name.get(name) else {
        tracing::debug!(
            name,
            "SceneMax function is not implemented or was not parsed"
        );
        return ActionSequenceResult::Completed;
    };
    if !function_guard_matches(
        function,
        args,
        vars,
        guards_by_name,
        Some(transforms_by_name),
        Some(collider_bounds),
    ) {
        tracing::debug!(name, "SceneMax function guard is false");
        return ActionSequenceResult::Completed;
    }

    let actions = instantiate_function_actions(function, args);
    let mut function_scope = scope.cloned().unwrap_or_default();
    apply_action_sequence(
        &actions,
        transforms_by_name,
        vars,
        object_pools,
        camera_system.as_deref_mut(),
        functions_by_name,
        guards_by_name,
        queued_animations,
        runtime_assets,
        animation_durations,
        collider_bounds,
        delayed_actions,
        ui_queue,
        owner,
        Some(&mut function_scope),
        continuous_delta_seconds,
        commands,
        scene_entities,
    )
}

fn function_guard_matches(
    function: &FunctionRuntime,
    args: &[String],
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let Some(guard) = &function.guard else {
        return true;
    };
    let guard = substitute_function_condition(function, args, guard);
    condition_matches(
        &guard,
        vars,
        guards_by_name,
        transforms_by_name,
        collider_bounds,
    )
}

fn apply_transform_aliases(
    transforms_by_name: &mut HashMap<String, Transform>,
    object_pools: &SceneMaxObjectPools,
) {
    for (alias, target) in &object_pools.aliases {
        if let Some(transform) = transforms_by_name.get(target).copied() {
            transforms_by_name.insert(alias.clone(), transform);
        }
    }
}

fn apply_scoped_transform_aliases(
    transforms_by_name: &mut HashMap<String, Transform>,
    scope: Option<&SceneMaxScopeFrame>,
) {
    let Some(scope) = scope else {
        return;
    };
    for (alias, target) in &scope.aliases {
        if let Some(transform) = transforms_by_name.get(target).copied() {
            transforms_by_name.insert(alias.clone(), transform);
        }
    }
}

fn resolve_object_alias(
    name: &str,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
) -> String {
    scope
        .and_then(|scope| scope.aliases.get(name).cloned())
        .or_else(|| object_pools.aliases.get(name).cloned())
        .unwrap_or_else(|| name.to_owned())
}

fn target_matches_alias(
    target: &str,
    scene_name: &str,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
) -> bool {
    resolve_object_alias(target, object_pools, scope) == scene_name
}

fn sync_live_transform(
    transforms_by_name: &mut HashMap<String, Transform>,
    object_pools: &SceneMaxObjectPools,
    scope: Option<&SceneMaxScopeFrame>,
    scene_name: &str,
    transform: Transform,
) {
    transforms_by_name.insert(scene_name.to_owned(), transform);
    for (alias, target) in &object_pools.aliases {
        if target == scene_name {
            transforms_by_name.insert(alias.clone(), transform);
        }
    }
    if let Some(scope) = scope {
        for (alias, target) in &scope.aliases {
            if target == scene_name {
                transforms_by_name.insert(alias.clone(), transform);
            }
        }
    }
}

fn acquire_pool_member(pool: &str, object_pools: &mut SceneMaxObjectPools) -> Option<String> {
    let runtime = object_pools.pools.get_mut(pool)?;
    let member = runtime.available.pop()?;
    runtime.in_use.insert(member.clone());
    Some(member)
}

fn release_pool_action(
    release: &PoolReleaseStatement,
    object_pools: &mut SceneMaxObjectPools,
    scope: Option<&mut SceneMaxScopeFrame>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) {
    let mut scope = scope;
    let target = resolve_object_alias(&release.target, object_pools, scope.as_deref());
    if release_pool_member(&release.pool, &target, object_pools) {
        if let Some(scope) = scope.as_deref_mut() {
            scope.aliases.retain(|_, value| value != &target);
        }
        hide_and_stop_scene_entity(&target, commands, scene_entities);
    }
}

fn delete_scene_object(
    target: &str,
    object_pools: &mut SceneMaxObjectPools,
    scope: Option<&mut SceneMaxScopeFrame>,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) {
    let mut scope = scope;
    let target = resolve_object_alias(target, object_pools, scope.as_deref());
    if release_pooled_member_by_name(&target, object_pools) {
        if let Some(scope) = scope.as_deref_mut() {
            scope.aliases.retain(|_, value| value != &target);
        }
        hide_and_stop_scene_entity(&target, commands, scene_entities);
        return;
    }
    object_pools.aliases.retain(|_, value| value != &target);
    if let Some(scope) = scope.as_deref_mut() {
        scope.aliases.retain(|_, value| value != &target);
    }
    for (entity, scene_entity, _, _, _, _, _, _) in &mut scene_entities.p1() {
        if scene_entity.name == target {
            commands.entity(entity).despawn();
            break;
        }
    }
}

fn release_pool_member(pool: &str, target: &str, object_pools: &mut SceneMaxObjectPools) -> bool {
    let Some(runtime) = object_pools.pools.get_mut(pool) else {
        return false;
    };
    if !runtime.members.contains(target) {
        return false;
    }
    runtime.in_use.remove(target);
    if !runtime.available.iter().any(|member| member == target) {
        runtime.available.push(target.to_owned());
    }
    object_pools.aliases.retain(|_, value| value != target);
    true
}

fn release_pooled_member_by_name(target: &str, object_pools: &mut SceneMaxObjectPools) -> bool {
    let Some(pool_name) = object_pools
        .pools
        .iter()
        .find_map(|(name, runtime)| runtime.members.contains(target).then(|| name.clone()))
    else {
        return false;
    };
    release_pool_member(&pool_name, target, object_pools)
}

fn hide_and_stop_scene_entity(
    target: &str,
    commands: &mut Commands,
    scene_entities: &mut ParamSet<(
        Query<(&SceneMaxEntity, &Transform)>,
        Query<(
            Entity,
            &SceneMaxEntity,
            &mut Transform,
            Option<&SceneMaxGltf>,
            Option<&CurrentAnimation>,
            Option<&mut Visibility>,
            Option<&SceneMaxCharacterController>,
            Option<&mut SceneMaxCharacterMotor>,
        )>,
    )>,
) {
    for (entity, scene_entity, _, _, _, visibility, _, _) in &mut scene_entities.p1() {
        if scene_entity.name != target {
            continue;
        }
        if let Some(mut visibility) = visibility {
            *visibility = Visibility::Hidden;
        } else {
            commands.entity(entity).insert(Visibility::Hidden);
        }
        commands
            .entity(entity)
            .insert((LinearVelocity::ZERO, AngularVelocity::ZERO));
        break;
    }
}

fn parent_action_tail(actions: &[Statement], index: usize) -> &[Statement] {
    actions.get(index + 1..).unwrap_or_default()
}

fn apply_builtin_navigation_controls(
    time: Res<Time>,
    keyboard: Res<ButtonInput<KeyCode>>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut commands: Commands,
    mut players: Query<(
        Entity,
        &SceneMaxEntity,
        &mut Transform,
        Option<&SceneMaxGltf>,
        Option<&CurrentAnimation>,
        Option<&SceneMaxCharacterController>,
        Option<&mut SceneMaxCharacterMotor>,
    )>,
) {
    if startup_program.0.as_ref().is_some_and(|program| {
        program
            .statements
            .iter()
            .any(|statement| matches!(statement, Statement::KeyEvent(_)))
    }) {
        return;
    }

    let delta_seconds = time.delta_secs();
    let turn_delta = if keyboard.pressed(KeyCode::ArrowLeft) {
        BUILTIN_PLAYER_TURN_SPEED_RADIANS * delta_seconds
    } else if keyboard.pressed(KeyCode::ArrowRight) {
        -BUILTIN_PLAYER_TURN_SPEED_RADIANS * delta_seconds
    } else {
        0.0
    };

    let move_direction = if keyboard.pressed(KeyCode::ArrowUp) {
        1.0
    } else if keyboard.pressed(KeyCode::ArrowDown) {
        -1.0
    } else {
        0.0
    };

    let should_restore_idle = keyboard.just_released(KeyCode::ArrowUp);
    if turn_delta == 0.0 && move_direction == 0.0 && !should_restore_idle {
        return;
    }

    for (
        entity,
        scene_entity,
        mut transform,
        gltf,
        current_animation,
        character_controller,
        character_motor,
    ) in &mut players
    {
        if scene_entity.name != "player1" {
            continue;
        }

        if turn_delta != 0.0 {
            transform.rotate_y(turn_delta);
        }

        if let (Some(character_controller), Some(mut character_motor)) =
            (character_controller, character_motor)
        {
            if move_direction != 0.0 {
                let direction = horizontal_forward(&transform) * move_direction;
                set_character_motion(
                    &mut character_motor,
                    direction,
                    BUILTIN_PLAYER_MOVE_SPEED / character_controller.move_speed.max(0.001),
                    CHARACTER_INPUT_TTL_SECONDS,
                );
            }
        } else if move_direction != 0.0 {
            let direction = horizontal_forward(&transform) * move_direction;
            transform.translation += direction * BUILTIN_PLAYER_MOVE_SPEED * delta_seconds;
        }

        if move_direction > 0.0 {
            queue_builtin_player_animation(
                &mut commands,
                entity,
                gltf,
                current_animation,
                "run_sword",
                true,
            );
        } else if should_restore_idle {
            queue_builtin_player_animation(
                &mut commands,
                entity,
                gltf,
                current_animation,
                "idle2",
                true,
            );
        }
    }
}

fn queue_builtin_player_animation(
    commands: &mut Commands,
    entity: Entity,
    gltf: Option<&SceneMaxGltf>,
    current_animation: Option<&CurrentAnimation>,
    clip: &str,
    looped: bool,
) {
    if current_animation.is_some_and(|current| current_animation_matches(current, clip, looped)) {
        return;
    }
    let Some(gltf) = gltf else {
        return;
    };
    commands.entity(entity).insert(AnimationToPlay {
        clip: clip.to_owned(),
        looped,
        speed: 1.0,
        gltf: gltf.gltf.clone(),
    });
}

fn current_animation_matches(
    current: &CurrentAnimation,
    requested_clip: &str,
    looped: bool,
) -> bool {
    current.looped == looped
        && animation_name_matches(&current.clip, &normalized_animation_name(requested_clip))
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

fn apply_initial_assignments(program: &Program, vars: &mut SceneMaxVars) {
    let guards_by_name = collect_guards_by_name(program);
    for statement in &program.statements {
        if let Statement::Assignment(assignment) = statement {
            apply_assignment(assignment, vars, None, &guards_by_name, None);
        }
    }
}

fn apply_camera_systems(program: &Program, camera_system: &mut SceneMaxCameraSystem) {
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

fn select_camera_system(name: &str, camera_system: &mut SceneMaxCameraSystem) {
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

fn attach_camera(
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

fn chase_camera(
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

fn stop_camera_attachment(camera_system: &mut SceneMaxCameraSystem) {
    if camera_system.attached.take().is_some() {
        tracing::info!("stopped SceneMax camera attachment");
    }
}

fn apply_logger_statement(
    logger: &LoggerStatement,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) {
    let message = match &logger.message {
        LoggerMessage::Text(text) => text.clone(),
        LoggerMessage::Value(value) => resolve_assignment_value_scoped_with_guards(
            value,
            vars,
            scope,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        )
        .map(format_scenemax_number)
        .unwrap_or_else(|| "null".to_owned()),
    };
    write_runtime_log_line(logger.level, &message);
}

struct RuntimeVmSpatial<'a> {
    transforms_by_name: Option<&'a HashMap<String, Transform>>,
    collider_bounds: Option<&'a SceneMaxColliderBounds>,
}

impl SceneMaxVmSpatial for RuntimeVmSpatial<'_> {
    fn symbol_value(&self, name: &str) -> Option<f32> {
        coordinate_value_from_name(name, self.transforms_by_name?)
    }

    fn distance(&self, left: &str, right: &str) -> Option<f32> {
        let transforms_by_name = self.transforms_by_name?;
        let left = transforms_by_name.get(left)?;
        let right = transforms_by_name.get(right)?;
        Some(left.translation.distance(right.translation))
    }

    fn collision_matches(&self, sources: &[String], target: &str) -> bool {
        collision_condition_matches(
            sources,
            target,
            self.transforms_by_name,
            self.collider_bounds,
        )
    }
}

fn format_scenemax_number(value: f32) -> String {
    scenemax_runtime_vm_core::format_scenemax_number(value)
}

fn apply_assignment(
    assignment: &scenemax_parser::AssignmentStatement,
    vars: &mut SceneMaxVars,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    guards_by_name: &HashMap<String, Condition>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    apply_assignment_scoped(
        assignment,
        vars,
        None,
        transforms_by_name,
        guards_by_name,
        collider_bounds,
        false,
    )
}

fn apply_assignment_scoped(
    assignment: &scenemax_parser::AssignmentStatement,
    vars: &mut SceneMaxVars,
    mut scope: Option<&mut SceneMaxScopeFrame>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    guards_by_name: &HashMap<String, Condition>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    force_local: bool,
) -> Option<f32> {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    let Some(result) = scenemax_runtime_vm_core::apply_assignment_with_spatial(
        assignment,
        vars,
        scope.as_deref_mut(),
        guards_by_name,
        &spatial,
        force_local,
    ) else {
        tracing::debug!(
            name = %assignment.name,
            value = ?assignment.value,
            "SceneMax assignment value is not known yet"
        );
        return None;
    };
    write_state_assignment_probe(&assignment.name, result.previous, result.value, force_local);
    Some(result.value)
}

fn resolve_assignment_value(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    transforms_by_name: Option<&HashMap<String, Transform>>,
) -> Option<f32> {
    resolve_assignment_value_with_guards(value, vars, &HashMap::new(), transforms_by_name, None)
}

fn resolve_assignment_value_with_guards(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    scenemax_runtime_vm_core::resolve_assignment_value_with_guards(
        value,
        vars,
        guards_by_name,
        &spatial,
    )
}

fn resolve_assignment_value_scoped_with_guards(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> Option<f32> {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    scenemax_runtime_vm_core::resolve_assignment_value_scoped_with_guards(
        value,
        vars,
        scope,
        guards_by_name,
        &spatial,
    )
}

fn condition_matches(
    condition: &Condition,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    scenemax_runtime_vm_core::condition_matches(condition, vars, guards_by_name, &spatial)
}

fn condition_matches_scoped(
    condition: &Condition,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let spatial = RuntimeVmSpatial {
        transforms_by_name,
        collider_bounds,
    };
    scenemax_runtime_vm_core::condition_matches_scoped(
        condition,
        vars,
        scope,
        guards_by_name,
        &spatial,
    )
}

fn when_condition_matches(
    condition: &Condition,
    vars: &SceneMaxVars,
    guards_by_name: &HashMap<String, Condition>,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) -> bool {
    match condition {
        Condition::Collision { sources, target } => {
            physics_contact_condition_matches(
                sources,
                target,
                physics_contacts,
                object_pools,
                transforms_by_name,
            ) || collision_condition_matches(sources, target, transforms_by_name, collider_bounds)
        }
        Condition::Boolean(value) => *value,
        Condition::Not(condition) => !when_condition_matches(
            condition,
            vars,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
            physics_contacts,
            object_pools,
        ),
        Condition::And(conditions) => conditions.iter().all(|condition| {
            when_condition_matches(
                condition,
                vars,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
                physics_contacts,
                object_pools,
            )
        }),
        Condition::Or(conditions) => conditions.iter().any(|condition| {
            when_condition_matches(
                condition,
                vars,
                guards_by_name,
                transforms_by_name,
                collider_bounds,
                physics_contacts,
                object_pools,
            )
        }),
        condition => condition_matches(
            condition,
            vars,
            guards_by_name,
            transforms_by_name,
            collider_bounds,
        ),
    }
}

fn condition_contains_collision(condition: &Condition) -> bool {
    match condition {
        Condition::Collision { .. } => true,
        Condition::Not(condition) => condition_contains_collision(condition),
        Condition::And(conditions) | Condition::Or(conditions) => {
            conditions.iter().any(condition_contains_collision)
        }
        _ => false,
    }
}

#[cfg(test)]
fn physics_contact_matches(
    sources: &[String],
    target: &str,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
) -> bool {
    let target_candidates = collision_reference_candidates_with_alias(target, object_pools);
    sources.iter().any(|source| {
        let source_candidates = collision_reference_candidates_with_alias(source, object_pools);
        source_candidates.iter().any(|source_name| {
            target_candidates.iter().any(|target_name| {
                active_physics_contact_matches(source_name, target_name, physics_contacts)
            })
        })
    })
}

fn physics_contact_condition_matches(
    sources: &[String],
    target: &str,
    physics_contacts: &SceneMaxPhysicsContacts,
    object_pools: &SceneMaxObjectPools,
    transforms_by_name: Option<&HashMap<String, Transform>>,
) -> bool {
    let target_candidates = collision_reference_candidates_with_alias(target, object_pools);
    sources.iter().any(|source| {
        let source_candidates = collision_reference_candidates_with_alias(source, object_pools);
        source_candidates.iter().any(|source_name| {
            target_candidates.iter().any(|target_name| {
                active_physics_contact_matches(source_name, target_name, physics_contacts)
                    && transforms_by_name.is_none_or(|transforms_by_name| {
                        player_hitbox_owner_distance_allows(
                            source_name,
                            target_name,
                            transforms_by_name,
                        )
                    })
            })
        })
    })
}

fn collision_reference_candidates_with_alias(
    reference: &str,
    object_pools: &SceneMaxObjectPools,
) -> Vec<String> {
    let mut candidates = collision_reference_candidates(reference);
    let resolved = resolve_object_alias(reference, object_pools, None);
    if resolved != reference {
        candidates.extend(collision_reference_candidates(&resolved));
    }
    candidates.sort();
    candidates.dedup();
    candidates
}

fn active_physics_contact_matches(
    source: &str,
    target: &str,
    physics_contacts: &SceneMaxPhysicsContacts,
) -> bool {
    if physics_contacts
        .active_pairs
        .contains(&normalized_collision_pair(source, target))
    {
        return true;
    }

    if !is_owner_level_collision_reference(source) && !is_owner_level_collision_reference(target) {
        return false;
    }

    let expected_owner_pair =
        normalized_collision_pair(&collision_owner(source), &collision_owner(target));
    physics_contacts.active_pairs.iter().any(|(left, right)| {
        normalized_collision_pair(&collision_owner(left), &collision_owner(right))
            == expected_owner_pair
    })
}

fn is_owner_level_collision_reference(reference: &str) -> bool {
    let normalized = reference.trim().trim_matches('"');
    collision_owner(normalized) == normalized
}

fn normalized_collision_pair(left: &str, right: &str) -> (String, String) {
    if left <= right {
        (left.to_owned(), right.to_owned())
    } else {
        (right.to_owned(), left.to_owned())
    }
}

fn collision_condition_matches(
    sources: &[String],
    target: &str,
    transforms_by_name: Option<&HashMap<String, Transform>>,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> bool {
    let Some(transforms_by_name) = transforms_by_name else {
        return false;
    };
    let target_exact = transforms_by_name.get(target).copied();
    let Some(target_transform) =
        target_exact.or_else(|| collision_owner_transform(target, transforms_by_name))
    else {
        return false;
    };
    sources.iter().any(|source| {
        let source_exact = transforms_by_name.get(source).copied();
        if let (Some(source_transform), Some(target_transform)) = (source_exact, target_exact) {
            if !player_hitbox_owner_distance_allows(source, target, transforms_by_name) {
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
        collision_owner_transform(source, transforms_by_name).is_some_and(|source_transform| {
            source_transform
                .translation
                .distance(target_transform.translation)
                <= collision_threshold(source, target)
        })
    })
}

fn collision_owner_transform(
    reference: &str,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Transform> {
    let owner = collision_owner(reference);
    transforms_by_name
        .get(reference)
        .copied()
        .or_else(|| transforms_by_name.get(&owner).copied())
}

fn collision_owner_distance(
    source: &str,
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
) -> f32 {
    let source_owner = collision_owner(source);
    let target_owner = collision_owner(target);
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

fn player_hitbox_owner_distance_allows(
    source: &str,
    target: &str,
    transforms_by_name: &HashMap<String, Transform>,
) -> bool {
    let source_owner = collision_owner(source);
    let target_owner = collision_owner(target);
    if source_owner == target_owner {
        return true;
    }
    if !source_owner.starts_with("player") || !target_owner.starts_with("player") {
        return true;
    }
    if !source.contains("_collider") || !target.contains("_collider") {
        return true;
    }
    let owner_distance = collision_owner_distance(source, target, transforms_by_name);
    !owner_distance.is_finite() || owner_distance <= MAX_PLAYER_HITBOX_OWNER_DISTANCE
}

fn collision_reference_candidates(reference: &str) -> Vec<String> {
    vec![reference.trim().trim_matches('"').to_owned()]
}

fn collision_owner(reference: &str) -> String {
    let normalized = reference.trim().trim_matches('"');
    for owner in ["player1", "player2"] {
        if normalized == owner
            || normalized
                .strip_prefix(owner)
                .is_some_and(|rest| rest.starts_with('_') || rest.starts_with('.'))
        {
            return owner.to_owned();
        }
    }
    normalized
        .split(['.', '[', '"'])
        .next()
        .unwrap_or(normalized)
        .to_owned()
}

fn collision_threshold(source: &str, target: &str) -> f32 {
    let source_owner = collision_owner(source);
    let target_owner = collision_owner(target);
    if source_owner.starts_with("player") && target_owner.starts_with("player") {
        4.25
    } else {
        2.5
    }
}

fn exact_collider_shapes_overlap(
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

fn collider_shapes_overlap(
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

fn sphere_overlaps_box(
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

fn exact_collision_threshold(
    source: &str,
    target: &str,
    collider_bounds: Option<&SceneMaxColliderBounds>,
) -> f32 {
    collider_radius(source, collider_bounds) + collider_radius(target, collider_bounds)
}

fn collider_radius(reference: &str, collider_bounds: Option<&SceneMaxColliderBounds>) -> f32 {
    collider_bounds
        .and_then(|bounds| bounds.radius_by_name.get(reference).copied())
        .unwrap_or_else(|| collision_part_radius(reference))
}

fn collision_part_radius(reference: &str) -> f32 {
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

fn coordinate_value_from_name(
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
        duration_seconds: duration,
        radians_per_second: turn.degrees.to_radians() / duration,
        loop_condition: turn.loop_condition.clone(),
    }
}

fn timed_move_from_statement(
    movement: &scenemax_parser::MoveStatement,
    transform: &Transform,
) -> TimedMove {
    let duration = movement.duration_seconds.max(0.001);
    let direction = movement_direction_vector(movement.direction, transform);

    TimedMove {
        remaining_seconds: duration,
        duration_seconds: duration,
        velocity: direction * directional_move_speed(movement),
        final_translation: None,
        loop_condition: movement.loop_condition.clone(),
    }
}

fn directional_move_speed(movement: &scenemax_parser::MoveStatement) -> f32 {
    if movement.duration_seconds > 0.0 {
        movement.distance / movement.duration_seconds.max(0.001)
    } else {
        movement.distance / 0.001
    }
}

fn timed_move_to_from_statement(
    movement: &scenemax_parser::MoveToStatement,
    transform: &Transform,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<TimedMove> {
    let destination = evaluate_move_to_destination(&movement.destination, transforms_by_name)?;
    let duration = movement.duration_seconds.max(0.001);
    Some(TimedMove {
        remaining_seconds: duration,
        duration_seconds: duration,
        velocity: (destination - transform.translation) / duration,
        final_translation: Some(destination),
        loop_condition: None,
    })
}

fn append_timed_move(commands: &mut Commands, entity: Entity, timed_move: TimedMove) {
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

fn timed_jump_from_statement(jump: &CharacterJumpStatement, transform: &Transform) -> TimedJump {
    TimedJump {
        elapsed_seconds: 0.0,
        duration_seconds: jump_duration_seconds(jump.speed),
        start_y: transform.translation.y,
        height: jump_height(jump.speed),
    }
}

fn evaluate_move_to_destination(
    destination: &scenemax_parser::MoveToDestination,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Vec3> {
    match destination {
        scenemax_parser::MoveToDestination::Position(position) => {
            evaluate_position_value(position, transforms_by_name)
        }
        scenemax_parser::MoveToDestination::EntityForward { entity, distance } => {
            let transform = transforms_by_name.get(entity)?;
            Some(transform.translation + horizontal_forward(transform) * *distance)
        }
    }
}

fn apply_physics_impulse(
    commands: &mut Commands,
    entity: Entity,
    transform: &Transform,
    impulse: &scenemax_parser::PhysicsImpulseStatement,
) {
    let direction = physics_direction_vector(impulse.direction, transform);
    commands
        .entity(entity)
        .insert(LinearVelocity(direction * impulse.strength));
}

fn apply_physics_stop(commands: &mut Commands, entity: Entity) {
    commands
        .entity(entity)
        .insert((LinearVelocity::ZERO, AngularVelocity::ZERO));
}

fn apply_physics_throw_at(
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

fn physics_direction_vector(
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

fn set_character_move_intent(
    motor: &mut SceneMaxCharacterMotor,
    controller: &SceneMaxCharacterController,
    movement: &scenemax_parser::MoveStatement,
    transform: &Transform,
    continuous_delta_seconds: Option<f32>,
) {
    let duration = movement.duration_seconds.max(0.001);
    let speed = directional_move_speed(movement);
    let direction = movement_direction_vector(movement.direction, transform);
    let speed_ratio = speed / controller.move_speed.max(0.001);

    if continuous_delta_seconds.is_some() {
        set_character_motion(motor, direction, speed_ratio, CHARACTER_INPUT_TTL_SECONDS);
    } else {
        motor.timed_motion = direction.normalize_or_zero() * speed_ratio;
        motor.timed_motion_remaining_seconds = duration;
    }
}

fn movement_direction_vector(direction: MoveDirection, transform: &Transform) -> Vec3 {
    match direction {
        MoveDirection::Forward => horizontal_forward(transform),
        MoveDirection::Backward => -horizontal_forward(transform),
        MoveDirection::Left => horizontal_right(transform),
        MoveDirection::Right => -horizontal_right(transform),
        MoveDirection::Up => Vec3::Y,
        MoveDirection::Down => -Vec3::Y,
    }
}

fn set_character_motion(
    motor: &mut SceneMaxCharacterMotor,
    direction: Vec3,
    speed_ratio: f32,
    ttl_seconds: f32,
) {
    motor.desired_motion = direction.normalize_or_zero() * speed_ratio;
    motor.motion_ttl_seconds = ttl_seconds;
}

fn set_character_jump_intent(motor: &mut SceneMaxCharacterMotor, jump: &CharacterJumpStatement) {
    motor.pending_jump_speed = Some(jump.speed);
    motor.jump_hold_seconds = motor
        .jump_hold_seconds
        .max(character_jump_feed_seconds(jump.speed));
}

fn jump_height(speed: f32) -> f32 {
    (speed * 0.16).clamp(1.2, 8.0)
}

fn jump_duration_seconds(speed: f32) -> f32 {
    (speed * 0.025).clamp(0.45, 1.15)
}

fn character_jump_feed_seconds(speed: f32) -> f32 {
    jump_duration_seconds(speed).max(CHARACTER_JUMP_FEED_SECONDS)
}

fn jump_y_offset(progress: f32, height: f32) -> f32 {
    let progress = progress.clamp(0.0, 1.0);
    4.0 * height * progress * (1.0 - progress)
}

fn horizontal_forward(transform: &Transform) -> Vec3 {
    let mut direction = transform.rotation * Vec3::Z;
    direction.y = 0.0;
    if direction.length_squared() <= f32::EPSILON {
        return Vec3::Z;
    }
    direction.normalize()
}

fn horizontal_right(transform: &Transform) -> Vec3 {
    let mut direction = transform.rotation * Vec3::X;
    direction.y = 0.0;
    if direction.length_squared() <= f32::EPSILON {
        return Vec3::X;
    }
    direction.normalize()
}

fn evaluate_position_statement(
    position: &PositionStatement,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Vec3> {
    evaluate_position_value(&position.position, transforms_by_name)
}

fn evaluate_position_value(
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

fn evaluate_position_expr(
    value: &PositionExpr,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<f32> {
    match value {
        PositionExpr::Number(value) => Some(*value),
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

fn lookup_subject_transform(
    subject: &str,
    transforms_by_name: &HashMap<String, Transform>,
) -> Option<Transform> {
    if let Some(transform) = transforms_by_name.get(subject).copied() {
        return Some(transform);
    }
    let name = subject.split_whitespace().next()?;
    transforms_by_name.get(name).copied()
}

fn look_at_scenemax_forward(transform: &mut Transform, target_translation: Vec3) {
    let mut direction = target_translation - transform.translation;
    direction.y = 0.0;
    if direction.length_squared() <= f32::EPSILON {
        return;
    }
    let direction = direction.normalize();
    transform.rotation = Quat::from_rotation_y(direction.x.atan2(direction.z));
}

fn update_timed_turns(
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

fn update_timed_moves(
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

fn update_timed_jumps(
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

fn feed_tnua_character_controllers(
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

fn update_fighting_camera(
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

fn update_third_person_camera(
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

fn update_attached_camera(
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

fn restore_default_idle_animations(
    mut commands: Commands,
    vars: Res<SceneMaxVars>,
    characters: Query<(
        Entity,
        &SceneMaxEntity,
        Option<&SceneMaxGltf>,
        Option<&CurrentAnimation>,
        Option<&AnimationToPlay>,
    )>,
) {
    if !scene_is_ready_for_player_idle(&vars) {
        return;
    }
    for (entity, scene_entity, gltf, current_animation, pending_animation) in &characters {
        if scene_entity.name != "player1" || pending_animation.is_some() {
            continue;
        }
        if current_animation.is_some_and(|current| {
            current_animation_matches(current, "idle2", true)
                || (!current.looped && current.elapsed_seconds < current.duration_seconds)
        }) {
            continue;
        }
        let Some(gltf) = gltf else {
            continue;
        };
        commands.entity(entity).insert(AnimationToPlay {
            clip: "idle2".to_owned(),
            looped: true,
            speed: 1.0,
            gltf: gltf.gltf.clone(),
        });
    }
}

fn scene_is_ready_for_player_idle(vars: &SceneMaxVars) -> bool {
    variable_is_zero(vars, "action")
        && variable_is_zero(vars, "move_forward")
        && variable_is_zero(vars, "player_hit")
        && variable_is_zero(vars, "player1_ko")
        && variable_is_zero(vars, "player1.data.is_jumping")
}

fn variable_is_zero(vars: &SceneMaxVars, name: &str) -> bool {
    scenemax_runtime_vm_core::variable_is_zero(vars, name)
}

fn play_pending_animations(
    mut commands: Commands,
    children: Query<&Children>,
    root_entities: Query<&SceneMaxEntity>,
    animations_to_play: Query<(Entity, &AnimationToPlay)>,
    gltfs: Res<Assets<Gltf>>,
    mut animation_clips: ResMut<Assets<AnimationClip>>,
    mut animation_durations: ResMut<SceneMaxAnimationDurations>,
    mut graphs: ResMut<Assets<AnimationGraph>>,
    animation_targets: Query<(&AnimationTargetId, Option<&Name>)>,
    mut players: Query<(
        &mut AnimationPlayer,
        Option<&mut AnimationGraphHandle>,
        Option<&Name>,
    )>,
) {
    for (root, animation_to_play) in &animations_to_play {
        let target_name = root_entities
            .get(root)
            .map(|entity| entity.name.as_str())
            .unwrap_or("<unknown>");
        let Some(gltf) = gltfs.get(&animation_to_play.gltf) else {
            continue;
        };

        let Some((resolved_clip_name, clip)) =
            find_named_animation_clip(gltf.named_animations.iter(), &animation_to_play.clip)
        else {
            write_runtime_log_line(
                LoggerLevel::Info,
                &format!(
                    "ANIM:MISS target={} requested={} available={}",
                    target_name,
                    animation_to_play.clip,
                    animation_names_summary(gltf)
                ),
            );
            if gltf.animations.is_empty() {
                tracing::warn!(clip = %animation_to_play.clip, "GLTF model has no animation clips");
            } else {
                tracing::warn!(
                    clip = %animation_to_play.clip,
                    available = gltf.named_animations.len(),
                    "GLTF animation clip was not found; skipping fallback"
                );
            }
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
            tracing::debug!(
                target = target_name,
                clip = %animation_to_play.clip,
                "waiting for GLTF AnimationPlayer entity"
            );
            continue;
        }

        let retargeted_clip = animation_clips.get(clip).and_then(|source_clip| {
            retarget_clip_to_visible_animation_player(
                source_clip,
                resolved_clip_name,
                &animation_players,
                &children,
                &animation_targets,
                &players,
            )
        });
        let (clip_to_play, playback_players, retargeted_curve_count) =
            if let Some((retargeted_clip, destination_player, curve_count)) = retargeted_clip {
                let handle = animation_clips.add(retargeted_clip);
                (handle, vec![destination_player], curve_count)
            } else {
                (clip.clone(), animation_players.clone(), 0)
            };

        let (graph, index) = AnimationGraph::from_clip(clip_to_play.clone());
        let graph_handle = graphs.add(graph);
        let duration_seconds = animation_clip_duration_seconds(&animation_clips, &clip_to_play);
        animation_durations.insert(
            target_name,
            &animation_to_play.clip,
            resolved_clip_name,
            duration_seconds,
        );

        let animation_player_count = playback_players.len();
        let mut animation_player_names = Vec::new();
        for child in playback_players {
            if let Ok((mut player, graph_component, name)) = players.get_mut(child) {
                animation_player_names.push(
                    name.map(|name| name.as_str().to_owned())
                        .unwrap_or_else(|| format!("{child:?}")),
                );
                if let Some(mut graph_component) = graph_component {
                    graph_component.0 = graph_handle.clone();
                } else {
                    commands
                        .entity(child)
                        .insert(AnimationGraphHandle(graph_handle.clone()));
                }
                let active = player
                    .stop_all()
                    .start(index)
                    .set_speed(animation_to_play.speed)
                    .set_weight(1.0);
                if animation_to_play.looped {
                    active.repeat();
                }
            }
        }

        write_runtime_log_line(
            LoggerLevel::Info,
            &format!(
                "ANIM:PLAY target={} requested={} resolved={} looped={} speed={} duration={} players={}",
                target_name,
                animation_to_play.clip,
                resolved_clip_name,
                animation_to_play.looped as u8,
                format_scenemax_number(animation_to_play.speed),
                format_scenemax_number(duration_seconds),
                animation_player_count,
            ),
        );
        write_runtime_log_line(
            LoggerLevel::Info,
            &format!(
                "ANIM:PLAYERS target={} names={} retargeted_curves={}",
                target_name,
                animation_player_names.join("|"),
                retargeted_curve_count
            ),
        );
        commands.entity(root).remove::<AnimationToPlay>();
        commands.entity(root).insert(CurrentAnimation {
            clip: resolved_clip_name.to_owned(),
            looped: animation_to_play.looped,
            speed: animation_to_play.speed.max(0.001),
            elapsed_seconds: 0.0,
            duration_seconds,
        });
    }
}

fn retarget_clip_to_visible_animation_player(
    source_clip: &AnimationClip,
    resolved_clip_name: &str,
    animation_players: &[Entity],
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    players: &Query<(
        &mut AnimationPlayer,
        Option<&mut AnimationGraphHandle>,
        Option<&Name>,
    )>,
) -> Option<(AnimationClip, Entity, usize)> {
    let destination_player = animation_players.first().copied()?;
    let source_player = animation_players.iter().copied().find(|entity| {
        players
            .get(*entity)
            .ok()
            .and_then(|(_, _, name)| name)
            .is_some_and(|name| {
                animation_name_matches(
                    name.as_str(),
                    &normalized_animation_name(resolved_clip_name),
                )
            })
    })?;
    if source_player == destination_player {
        return None;
    }

    let source_targets =
        collect_animation_targets_by_name(source_player, children, animation_targets);
    let destination_targets =
        collect_animation_targets_by_name(destination_player, children, animation_targets);
    if source_targets.is_empty() || destination_targets.is_empty() {
        return None;
    }

    let mut source_to_destination = HashMap::new();
    for (bone_name, source_target) in source_targets {
        if let Some(destination_target) = destination_targets.get(&bone_name).copied() {
            source_to_destination.insert(source_target, destination_target);
        }
    }
    if source_to_destination.is_empty() {
        return None;
    }

    let mut retargeted = AnimationClip::default();
    retargeted.set_duration(source_clip.duration());
    let mut curve_count = 0usize;
    for (source_target, curves) in source_clip.curves() {
        let Some(destination_target) = source_to_destination.get(source_target).copied() else {
            continue;
        };
        for curve in curves {
            retargeted.add_variable_curve_to_target(destination_target, curve.clone());
            curve_count += 1;
        }
    }

    (curve_count > 0).then_some((retargeted, destination_player, curve_count))
}

fn collect_animation_targets_by_name(
    root: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
) -> HashMap<String, AnimationTargetId> {
    let mut targets = HashMap::new();
    collect_animation_targets_by_name_recursive(root, children, animation_targets, &mut targets);
    targets
}

fn collect_animation_targets_by_name_recursive(
    entity: Entity,
    children: &Query<&Children>,
    animation_targets: &Query<(&AnimationTargetId, Option<&Name>)>,
    targets: &mut HashMap<String, AnimationTargetId>,
) {
    if let Ok((target_id, Some(name))) = animation_targets.get(entity) {
        targets.insert(normalized_animation_name(name.as_str()), *target_id);
    }
    if let Ok(child_list) = children.get(entity) {
        for child in child_list {
            collect_animation_targets_by_name_recursive(
                *child,
                children,
                animation_targets,
                targets,
            );
        }
    }
}

fn animation_names_summary(gltf: &Gltf) -> String {
    let mut names = gltf
        .named_animations
        .keys()
        .take(16)
        .map(|name| name.as_ref())
        .collect::<Vec<_>>()
        .join(",");
    if gltf.named_animations.len() > 16 {
        names.push_str(",...");
    }
    names
}

fn find_named_animation_clip<'a>(
    named_animations: impl IntoIterator<Item = (&'a Box<str>, &'a Handle<AnimationClip>)>,
    requested: &str,
) -> Option<(&'a str, &'a Handle<AnimationClip>)> {
    let requested_key = normalized_animation_name(requested);
    let mut case_match = None;
    let mut normalized_match = None;
    for (name, clip) in named_animations {
        let name = name.as_ref();
        if name == requested {
            case_match = preferred_animation_match(case_match, (name, clip), &requested_key);
            continue;
        }
        if name.eq_ignore_ascii_case(requested) {
            case_match = preferred_animation_match(case_match, (name, clip), &requested_key);
        }
        if animation_name_matches(name, &requested_key) {
            normalized_match =
                preferred_animation_match(normalized_match, (name, clip), &requested_key);
        }
    }
    case_match.or(normalized_match)
}

fn preferred_animation_match<'a>(
    current: Option<(&'a str, &'a Handle<AnimationClip>)>,
    candidate: (&'a str, &'a Handle<AnimationClip>),
    requested_key: &str,
) -> Option<(&'a str, &'a Handle<AnimationClip>)> {
    let Some(current) = current else {
        return Some(candidate);
    };
    (animation_candidate_score(candidate.0, requested_key)
        >= animation_candidate_score(current.0, requested_key))
    .then_some(candidate)
    .or(Some(current))
}

fn update_current_animation_vars(
    time: Res<Time>,
    mut vars: ResMut<SceneMaxVars>,
    mut animations: Query<(&SceneMaxEntity, &mut CurrentAnimation)>,
) {
    let delta = time.delta_secs();
    for (entity, mut animation) in &mut animations {
        let duration = animation.duration_seconds.max(0.001);
        animation.elapsed_seconds += delta * animation.speed.max(0.001);
        let elapsed = if animation.looped {
            animation.elapsed_seconds % duration
        } else {
            animation.elapsed_seconds.min(duration)
        };
        let percent = animation_percent_from_elapsed(elapsed, duration);
        vars.0
            .insert(format!("{}.anim_percent", entity.name), percent);
        vars.0.insert(
            format!("{}.anim_finished", entity.name),
            (!animation.looped && animation.elapsed_seconds >= duration) as u8 as f32,
        );
    }
}

fn current_animation_percent(animation: &CurrentAnimation) -> f32 {
    let duration = animation.duration_seconds.max(0.001);
    let elapsed = if animation.looped {
        animation.elapsed_seconds % duration
    } else {
        animation.elapsed_seconds.min(duration)
    };
    animation_percent_from_elapsed(elapsed, duration)
}

fn animation_percent_from_elapsed(elapsed: f32, duration: f32) -> f32 {
    ((elapsed / duration.max(0.001)) * 100.0).clamp(0.0, 100.0)
}

fn animation_speed_override(animation_speed: &AnimationSpeedStatement) -> AnimationSpeedOverride {
    AnimationSpeedOverride {
        speed: animation_speed.speed.max(0.001),
        remaining_seconds: animation_speed.duration_seconds,
        applied: false,
    }
}

fn apply_animation_speed_overrides(
    time: Res<Time>,
    mut commands: Commands,
    children: Query<&Children>,
    mut roots: Query<(Entity, &mut AnimationSpeedOverride), With<SceneMaxEntity>>,
    mut players: Query<&mut AnimationPlayer>,
) {
    for (root, mut speed_override) in &mut roots {
        let player_entities = children.iter_descendants(root).collect::<Vec<_>>();
        if !speed_override.applied {
            for player_entity in &player_entities {
                if let Ok(mut player) = players.get_mut(*player_entity) {
                    set_active_animation_speeds(&mut player, speed_override.speed);
                }
            }
            speed_override.applied = true;
        }

        let Some(remaining_seconds) = speed_override.remaining_seconds.as_mut() else {
            commands.entity(root).remove::<AnimationSpeedOverride>();
            continue;
        };
        *remaining_seconds -= time.delta_secs();
        if *remaining_seconds <= 0.0 {
            for player_entity in &player_entities {
                if let Ok(mut player) = players.get_mut(*player_entity) {
                    set_active_animation_speeds(&mut player, 1.0);
                }
            }
            commands.entity(root).remove::<AnimationSpeedOverride>();
        }
    }
}

fn set_active_animation_speeds(player: &mut AnimationPlayer, speed: f32) {
    for (_, active_animation) in player.playing_animations_mut() {
        active_animation.set_speed(speed);
    }
}

fn setup_camera_and_lights(
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
    Transform::from_xyz(0.0, 0.0, 10.0).looking_at(Vec3::ZERO, Vec3::Y)
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
    fn default_camera_matches_classic_projector_start_view() {
        let transform = default_camera_transform();

        assert_eq!(transform.translation, Vec3::new(0.0, 0.0, 10.0));
        assert!(
            transform
                .forward()
                .as_vec3()
                .abs_diff_eq(Vec3::new(0.0, 0.0, -1.0), 0.0001)
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

    #[test]
    fn evaluates_alias_and_constant_backed_guard() {
        let program = scenemax_parser::parse_program(
            "var GAME_STATE_OVER = 2\nvar @allow_move = player1.data.is_jumping==0 && game_status!=GAME_STATE_OVER",
        )
        .unwrap();
        let guards = collect_guards_by_name(&program);
        let mut vars = SceneMaxVars::default();
        apply_initial_assignments(&program, &mut vars);
        vars.0.insert("player1.data.is_jumping".to_owned(), 0.0);
        vars.0.insert("game_status".to_owned(), 1.0);

        assert!(condition_matches(
            &Condition::Alias("allow_move".to_owned()),
            &vars,
            &guards,
            None,
            None,
        ));

        vars.0.insert("game_status".to_owned(), 2.0);
        assert!(!condition_matches(
            &Condition::Alias("allow_move".to_owned()),
            &vars,
            &guards,
            None,
            None,
        ));
    }

    #[test]
    fn evaluates_parameterized_function_guard() {
        let program = scenemax_parser::parse_program(
            "[p2.data.is_jumping == 0 && enemy_ko == 0]\nopponent_ai(p1, p2) = {\n  p2.look at (p1)\n}",
        )
        .unwrap();
        let functions = collect_functions_by_name(&program);
        let guards = collect_guards_by_name(&program);
        let function = functions.get("opponent_ai").unwrap();
        let mut vars = SceneMaxVars::default();
        vars.0.insert("player2.data.is_jumping".to_owned(), 1.0);
        vars.0.insert("enemy_ko".to_owned(), 0.0);

        assert!(!function_guard_matches(
            function,
            &["player1".to_owned(), "player2".to_owned()],
            &vars,
            &guards,
            None,
            None,
        ));

        vars.0.insert("player2.data.is_jumping".to_owned(), 0.0);
        assert!(function_guard_matches(
            function,
            &["player1".to_owned(), "player2".to_owned()],
            &vars,
            &guards,
            None,
            None,
        ));
    }

    #[test]
    fn evaluates_reference_enemy_ai_guard_with_constants_and_params() {
        let program = scenemax_parser::parse_program(
            "var GAME_STATE_START = 1, GAME_STATE_OVER = 2\nvar @enemy_ai_allowed = enemy_ko==0 && op_hit==0 && player1_ko==0 && game_status!=GAME_STATE_OVER && slow_motion==0 && player2.data.trapped == 0\n[op_hit!=1 && player1_ko==0 && enemy_ko==0 && op_action==0 && game_status==GAME_STATE_START && slow_motion==0 && player2.data.trapped == 0 && p2.data.is_jumping == 0]\nopponent_ai(p1, p2) = {\n  p2.look at (p1)\n}",
        )
        .unwrap();
        let functions = collect_functions_by_name(&program);
        let guards = collect_guards_by_name(&program);
        let function = functions.get("opponent_ai").unwrap();
        let mut vars = SceneMaxVars::default();
        apply_initial_assignments(&program, &mut vars);
        vars.0.insert("enemy_ko".to_owned(), 0.0);
        vars.0.insert("op_hit".to_owned(), 0.0);
        vars.0.insert("player1_ko".to_owned(), 0.0);
        vars.0.insert("op_action".to_owned(), 0.0);
        vars.0.insert("game_status".to_owned(), 1.0);
        vars.0.insert("slow_motion".to_owned(), 0.0);
        vars.0.insert("player2.data.trapped".to_owned(), 0.0);
        vars.0.insert("player2.data.is_jumping".to_owned(), 0.0);

        assert!(condition_matches(
            &Condition::Alias("enemy_ai_allowed".to_owned()),
            &vars,
            &guards,
            None,
            None,
        ));
        assert!(function_guard_matches(
            function,
            &["player1".to_owned(), "player2".to_owned()],
            &vars,
            &guards,
            None,
            None,
        ));

        vars.0.insert("player2.data.is_jumping".to_owned(), 1.0);
        assert!(!function_guard_matches(
            function,
            &["player1".to_owned(), "player2".to_owned()],
            &vars,
            &guards,
            None,
            None,
        ));
    }

    #[test]
    fn scoped_object_aliases_override_global_pool_aliases() {
        let mut object_pools = SceneMaxObjectPools::default();
        object_pools
            .aliases
            .insert("rock".to_owned(), "__global_rock".to_owned());
        let mut scope = SceneMaxScopeFrame::default();
        scope
            .aliases
            .insert("rock".to_owned(), "__local_rock".to_owned());

        assert_eq!(
            resolve_object_alias("rock", &object_pools, Some(&scope)),
            "__local_rock"
        );
        assert!(target_matches_alias(
            "rock",
            "__local_rock",
            &object_pools,
            Some(&scope)
        ));
        assert!(!target_matches_alias(
            "rock",
            "__global_rock",
            &object_pools,
            Some(&scope)
        ));
    }

    #[test]
    fn logger_writes_literal_and_expression_messages_to_runtime_file() {
        let log_dir =
            std::env::temp_dir().join(format!("scenemax_logger_test_{}", std::process::id()));
        let _ = fs::remove_dir_all(&log_dir);
        fs::create_dir_all(&log_dir).unwrap();
        initialize_runtime_logger(Some(&log_dir), None);
        let vars = SceneMaxVars(HashMap::from([("counter".to_owned(), 7.0)]));
        let guards = HashMap::new();

        apply_logger_statement(
            &LoggerStatement {
                level: LoggerLevel::Info,
                message: LoggerMessage::Text("flow-start".to_owned()),
            },
            &vars,
            None,
            &guards,
            None,
            None,
        );
        apply_logger_statement(
            &LoggerStatement {
                level: LoggerLevel::Debug,
                message: LoggerMessage::Value(AssignmentValue::Symbol("counter".to_owned())),
            },
            &vars,
            None,
            &guards,
            None,
            None,
        );

        let log_path = log_dir.join("scenemax-nextgen-runtime.log");
        let log = fs::read_to_string(log_path).unwrap();
        assert!(log.contains("[INFO] flow-start"));
        assert!(log.contains("[DEBUG] 7"));
        let _ = fs::remove_dir_all(log_dir);
    }

    #[test]
    fn object_pool_prototype_uses_returned_factory_symbol() {
        let functions = HashMap::from([(
            "create_rock".to_owned(),
            FunctionRuntime {
                params: Vec::new(),
                guard: None,
                actions: vec![
                    Statement::ModelDecl {
                        name: "ignored".to_owned(),
                        resource: "wrong_resource".to_owned(),
                        options: EntityOptions::default(),
                    },
                    Statement::ModelDecl {
                        name: "rock1".to_owned(),
                        resource: "meshy_rock1_native".to_owned(),
                        options: EntityOptions {
                            hidden: true,
                            ..Default::default()
                        },
                    },
                    Statement::ReturnValue {
                        value: AssignmentValue::Symbol("rock1".to_owned()),
                    },
                ],
            },
        )]);
        let pool = ObjectPoolStatement {
            name: "rocks".to_owned(),
            factory: "create_rock".to_owned(),
            size: 5,
        };

        let prototype = object_pool_prototype(&pool, &functions).unwrap();

        assert_eq!(prototype.resource, "meshy_rock1_native");
        assert!(prototype.options.hidden);
    }

    #[test]
    fn scoped_transform_aliases_follow_live_transform_sync() {
        let object_pools = SceneMaxObjectPools::default();
        let mut scope = SceneMaxScopeFrame::default();
        scope
            .aliases
            .insert("spawned".to_owned(), "__pool_enemy_0".to_owned());
        let mut transforms = HashMap::new();
        let transform = Transform::from_translation(Vec3::new(1.0, 2.0, 3.0));

        sync_live_transform(
            &mut transforms,
            &object_pools,
            Some(&scope),
            "__pool_enemy_0",
            transform,
        );

        assert_eq!(transforms.get("__pool_enemy_0").copied(), Some(transform));
        assert_eq!(transforms.get("spawned").copied(), Some(transform));
    }

    #[test]
    fn character_support_y_tracks_current_character_height() {
        assert_eq!(
            character_stage_support_y(-88.03695),
            -88.03695 - DEFAULT_CHARACTER_FLOAT_HEIGHT - DEFAULT_CHARACTER_VISUAL_DROP
        );
    }

    #[test]
    fn evaluates_fighter_collision_by_owner_distance() {
        let transforms = HashMap::from([
            (
                "player1".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 0.0)),
            ),
            (
                "player2".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 3.5)),
            ),
        ]);

        assert!(collision_condition_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_head_collider",
            Some(&transforms),
            None,
        ));
    }

    #[test]
    fn evaluates_fighter_collision_by_exact_collider_distance_first() {
        let transforms = HashMap::from([
            (
                "player1".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 0.0)),
            ),
            (
                "player2".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 2.0)),
            ),
            (
                "player1_head_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 1.6, 0.0)),
            ),
            (
                "player2_left_hand_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 1.5, 0.4)),
            ),
        ]);

        assert!(collision_condition_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_head_collider",
            Some(&transforms),
            None,
        ));
    }

    #[test]
    fn declared_collider_bounds_drive_exact_collision_fallback() {
        let transforms = HashMap::from([
            (
                "wide_box".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 0.0)),
            ),
            (
                "small_sphere".to_owned(),
                Transform::from_translation(Vec3::new(1.4, 0.0, 0.0)),
            ),
        ]);
        let collider_bounds = SceneMaxColliderBounds {
            radius_by_name: HashMap::from([
                ("wide_box".to_owned(), 1.0),
                ("small_sphere".to_owned(), 0.5),
            ]),
            shape_by_name: HashMap::new(),
        };

        assert!(collision_condition_matches(
            &["wide_box".to_owned()],
            "small_sphere",
            Some(&transforms),
            Some(&collider_bounds),
        ));
    }

    #[test]
    fn registers_declared_box_and_sphere_collider_bounds() {
        let mut collider_bounds = SceneMaxColliderBounds::default();
        let box_options = EntityOptions {
            position: None,
            rotation_degrees: None,
            scale: None,
            size: Some(SceneMaxVec3 {
                x: 2.0,
                y: 2.0,
                z: 2.0,
            }),
            hidden: false,
            collider: true,
            radius: None,
            body_kind: None,
            collision_shape: Some(SceneMaxCollisionShape::Box),
        };
        let sphere_options = EntityOptions {
            position: None,
            rotation_degrees: None,
            scale: None,
            size: None,
            hidden: false,
            collider: true,
            radius: Some(0.4),
            body_kind: None,
            collision_shape: Some(SceneMaxCollisionShape::Sphere),
        };

        register_collider_bounds(
            &mut collider_bounds,
            "box_hit",
            &box_options,
            Transform::IDENTITY,
        );
        register_collider_bounds(
            &mut collider_bounds,
            "sphere_hit",
            &sphere_options,
            Transform::IDENTITY,
        );

        assert!(
            (collider_bounds.radius_by_name["box_hit"] - Vec3::splat(1.0).length()).abs() < 0.001
        );
        assert!((collider_bounds.radius_by_name["sphere_hit"] - 0.4).abs() < 0.001);
    }

    #[test]
    fn exact_collider_distance_takes_precedence_over_owner_fallback() {
        let transforms = HashMap::from([
            (
                "player1".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 0.0)),
            ),
            (
                "player2".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 1.0)),
            ),
            (
                "player1_head_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 1.6, 0.0)),
            ),
            (
                "player2_left_hand_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 1.5, 3.0)),
            ),
        ]);

        assert!(!collision_condition_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_head_collider",
            Some(&transforms),
            None,
        ));
    }

    #[test]
    fn player_hitbox_collision_is_gated_by_owner_distance() {
        let transforms = HashMap::from([
            (
                "player1".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 0.0)),
            ),
            (
                "player2".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 4.5)),
            ),
            (
                "player1_head_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 1.6, 0.0)),
            ),
            (
                "player2_left_hand_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 1.5, 0.4)),
            ),
        ]);

        assert!(!collision_condition_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_head_collider",
            Some(&transforms),
            None,
        ));
    }

    #[test]
    fn exact_box_sphere_collision_uses_box_shape_not_bounding_sphere() {
        let transforms = HashMap::from([
            (
                "player1_body_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 1.0, 0.0)),
            ),
            (
                "player2_left_hand_collider".to_owned(),
                Transform::from_translation(Vec3::new(1.0, 1.0, 0.75)),
            ),
        ]);
        let collider_bounds = SceneMaxColliderBounds {
            radius_by_name: HashMap::from([
                (
                    "player1_body_collider".to_owned(),
                    Vec3::new(0.55, 0.9, 0.35).length(),
                ),
                ("player2_left_hand_collider".to_owned(), 0.24),
            ]),
            shape_by_name: HashMap::from([
                (
                    "player1_body_collider".to_owned(),
                    ColliderBoundShape::Box {
                        half_extents: Vec3::new(0.55, 0.9, 0.35),
                    },
                ),
                (
                    "player2_left_hand_collider".to_owned(),
                    ColliderBoundShape::Sphere { radius: 0.24 },
                ),
            ]),
        };

        assert!(!collision_condition_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_body_collider",
            Some(&transforms),
            Some(&collider_bounds),
        ));
    }

    #[test]
    fn exact_box_sphere_collision_matches_when_sphere_reaches_box() {
        let transforms = HashMap::from([
            (
                "player1_body_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 1.0, 0.0)),
            ),
            (
                "player2_left_hand_collider".to_owned(),
                Transform::from_translation(Vec3::new(0.7, 1.0, 0.2)),
            ),
        ]);
        let collider_bounds = SceneMaxColliderBounds {
            radius_by_name: HashMap::from([
                (
                    "player1_body_collider".to_owned(),
                    Vec3::new(0.55, 0.9, 0.35).length(),
                ),
                ("player2_left_hand_collider".to_owned(), 0.24),
            ]),
            shape_by_name: HashMap::from([
                (
                    "player1_body_collider".to_owned(),
                    ColliderBoundShape::Box {
                        half_extents: Vec3::new(0.55, 0.9, 0.35),
                    },
                ),
                (
                    "player2_left_hand_collider".to_owned(),
                    ColliderBoundShape::Sphere { radius: 0.24 },
                ),
            ]),
        };

        assert!(collision_condition_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_body_collider",
            Some(&transforms),
            Some(&collider_bounds),
        ));
    }

    #[test]
    fn matches_collision_condition_from_avian_contact_pair() {
        let contacts = SceneMaxPhysicsContacts {
            active_pairs: HashSet::from([normalized_collision_pair("player1", "player2")]),
        };
        let object_pools = SceneMaxObjectPools::default();

        assert!(physics_contact_matches(
            &["player2".to_owned()],
            "player1",
            &contacts,
            &object_pools,
        ));
        assert!(!physics_contact_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_head_collider",
            &contacts,
            &object_pools,
        ));
        assert!(!physics_contact_matches(
            &["player2_left_hand_collider".to_owned()],
            "crystal_box",
            &contacts,
            &object_pools,
        ));
    }

    #[test]
    fn matches_collision_condition_from_exact_avian_collider_pair() {
        let contacts = SceneMaxPhysicsContacts {
            active_pairs: HashSet::from([normalized_collision_pair(
                "player2_left_hand_collider",
                "player1_head_collider",
            )]),
        };
        let object_pools = SceneMaxObjectPools::default();

        assert!(physics_contact_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_head_collider",
            &contacts,
            &object_pools,
        ));
        assert!(physics_contact_matches(
            &["player2".to_owned()],
            "player1_head_collider",
            &contacts,
            &object_pools,
        ));
    }

    #[test]
    fn exact_avian_player_hitbox_contact_is_gated_by_owner_distance() {
        let contacts = SceneMaxPhysicsContacts {
            active_pairs: HashSet::from([normalized_collision_pair(
                "player2_left_hand_collider",
                "player1_head_collider",
            )]),
        };
        let object_pools = SceneMaxObjectPools::default();
        let transforms = HashMap::from([
            (
                "player1".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 0.0)),
            ),
            (
                "player2".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 4.5)),
            ),
        ]);

        assert!(!physics_contact_condition_matches(
            &["player2_left_hand_collider".to_owned()],
            "player1_head_collider",
            &contacts,
            &object_pools,
            Some(&transforms),
        ));
    }

    #[test]
    fn matches_physics_contacts_through_object_pool_alias() {
        let contacts = SceneMaxPhysicsContacts {
            active_pairs: HashSet::from([normalized_collision_pair(
                "player1_body_collider",
                "__pool_rocks_0",
            )]),
        };
        let mut object_pools = SceneMaxObjectPools::default();
        object_pools
            .aliases
            .insert("rock".to_owned(), "__pool_rocks_0".to_owned());

        assert!(physics_contact_matches(
            &["player1_body_collider".to_owned()],
            "rock",
            &contacts,
            &object_pools,
        ));
    }

    #[test]
    fn extracts_bone_name_from_attach_subject() {
        assert_eq!(
            attach_bone_name("player1.\"mixamorig:Head\""),
            Some("mixamorig:Head".to_owned())
        );
        assert_eq!(attach_bone_name("player1"), None);
    }

    #[test]
    fn computes_attachment_node_transform_with_local_offset() {
        let node_transform = Transform {
            translation: Vec3::new(1.0, 2.0, 3.0),
            rotation: Quat::IDENTITY,
            scale: Vec3::splat(2.0),
        };

        let attached = attachment_node_transform(node_transform, Vec3::new(0.0, 0.5, 0.0));

        assert_eq!(attached.translation, Vec3::new(1.0, 3.0, 3.0));
        assert_eq!(attached.rotation, Quat::IDENTITY);
        assert_eq!(attached.scale, Vec3::ONE);
    }

    #[test]
    fn computes_character_jump_arc_from_speed() {
        let jump = timed_jump_from_statement(
            &CharacterJumpStatement {
                target: "player1".to_owned(),
                speed: 35.0,
                async_run: false,
            },
            &Transform::from_translation(Vec3::new(0.0, 10.0, 0.0)),
        );

        assert_eq!(jump.start_y, 10.0);
        assert!((jump.height - 5.6).abs() < 0.001);
        assert!((jump.duration_seconds - 0.875).abs() < 0.001);
        assert!((jump_y_offset(0.5, jump.height) - jump.height).abs() < 0.001);
        assert_eq!(jump_y_offset(1.0, jump.height), 0.0);
        assert!((character_jump_feed_seconds(35.0) - 0.875).abs() < 0.001);
    }

    #[test]
    fn jump_animation_blocks_long_enough_for_character_jump() {
        let jump_animation = AnimationStatement {
            target: "player1".to_owned(),
            clip: "big_jump".to_owned(),
            speed: 1.0,
            looped: false,
            blocking: true,
        };
        let punch_animation = AnimationStatement {
            clip: "CrossPunch".to_owned(),
            speed: 2.5,
            ..jump_animation.clone()
        };

        let durations = SceneMaxAnimationDurations::default();
        assert!(estimated_animation_seconds(&jump_animation, &durations) >= 0.65);
        assert!(estimated_animation_seconds(&punch_animation, &durations) < 1.0);
    }

    #[test]
    fn cached_animation_duration_drives_blocking_estimate() {
        let mut durations = SceneMaxAnimationDurations::default();
        durations.insert("player2", "HighKick", "Armature|High-Kick", 2.4);
        let animation = AnimationStatement {
            target: "player2".to_owned(),
            clip: "HighKick".to_owned(),
            speed: 3.0,
            looped: false,
            blocking: true,
        };

        assert!((estimated_animation_seconds(&animation, &durations) - 0.8).abs() < 0.001);
    }

    #[test]
    fn timed_turn_preserves_loop_condition() {
        let turn = timed_turn_from_statement(&scenemax_parser::TurnStatement {
            target: "axe".to_owned(),
            degrees: 360.0,
            duration_seconds: 1.0,
            loop_condition: Some(Condition::EqualsValue {
                left: AssignmentValue::Number(1.0),
                right: AssignmentValue::Number(1.0),
            }),
            async_run: false,
        });

        assert_eq!(turn.duration_seconds, 1.0);
        assert!(turn.loop_condition.is_some());
    }

    #[test]
    fn looped_timed_turns_do_not_block_following_startup_actions() {
        let blocking_turn = Statement::Turn(scenemax_parser::TurnStatement {
            target: "gemini".to_owned(),
            degrees: 360.0,
            duration_seconds: 3.0,
            loop_condition: None,
            async_run: false,
        });
        let looped_turn = Statement::Turn(scenemax_parser::TurnStatement {
            target: "gemini".to_owned(),
            degrees: 360.0,
            duration_seconds: 3.0,
            loop_condition: Some(Condition::EqualsValue {
                left: AssignmentValue::Number(1.0),
                right: AssignmentValue::Number(1.0),
            }),
            async_run: false,
        });

        assert_eq!(blocking_timed_action_seconds(&blocking_turn), Some(3.0));
        assert_eq!(blocking_timed_action_seconds(&looped_turn), None);
    }

    #[test]
    fn timed_move_preserves_loop_condition() {
        let movement = timed_move_from_statement(
            &scenemax_parser::MoveStatement {
                target: "player1".to_owned(),
                direction: MoveDirection::Forward,
                distance: 0.2,
                duration_seconds: 0.5,
                loop_condition: Some(Condition::EqualsNumber {
                    name: "move_forward".to_owned(),
                    value: 1.0,
                }),
                async_run: false,
            },
            &Transform::default(),
        );

        assert_eq!(movement.duration_seconds, 0.5);
        assert!((movement.velocity.length() - 0.4).abs() < 0.001);
        assert!(movement.loop_condition.is_some());
    }

    #[test]
    fn timed_move_uses_lateral_direction_and_duration() {
        let left = timed_move_from_statement(
            &scenemax_parser::MoveStatement {
                target: "gemini".to_owned(),
                direction: MoveDirection::Left,
                distance: 4.0,
                duration_seconds: 2.0,
                loop_condition: None,
                async_run: false,
            },
            &Transform::default(),
        );
        let right = timed_move_from_statement(
            &scenemax_parser::MoveStatement {
                target: "gemini".to_owned(),
                direction: MoveDirection::Right,
                distance: 4.0,
                duration_seconds: 2.0,
                loop_condition: None,
                async_run: false,
            },
            &Transform::default(),
        );

        assert_eq!(left.duration_seconds, 2.0);
        assert!(left.velocity.abs_diff_eq(Vec3::new(2.0, 0.0, 0.0), 0.001));
        assert!(right.velocity.abs_diff_eq(Vec3::new(-2.0, 0.0, 0.0), 0.001));
    }

    #[test]
    fn timed_move_uses_vertical_direction_and_duration() {
        let movement = timed_move_from_statement(
            &scenemax_parser::MoveStatement {
                target: "gemini".to_owned(),
                direction: MoveDirection::Up,
                distance: 3.0,
                duration_seconds: 1.5,
                loop_condition: None,
                async_run: false,
            },
            &Transform::default(),
        );

        assert_eq!(movement.duration_seconds, 1.5);
        assert!(
            movement
                .velocity
                .abs_diff_eq(Vec3::new(0.0, 2.0, 0.0), 0.001)
        );
    }

    #[test]
    fn chooses_kinematic_capsule_for_dynamic_fighter_physics() {
        let options = EntityOptions {
            body_kind: Some(SceneMaxBodyKind::Kinematic),
            collision_shape: None,
            ..Default::default()
        };

        assert_eq!(
            physics_body_kind(&options),
            Some(SceneMaxBodyKind::Kinematic)
        );
        assert_eq!(
            physics_collision_shape(
                "player2",
                "old_fighter2_native",
                &options,
                SceneMaxBodyKind::Kinematic
            ),
            Some(SceneMaxCollisionShape::Capsule)
        );
    }

    #[test]
    fn evaluates_coordinate_symbols_and_position_statements() {
        let vars = SceneMaxVars::default();
        let transforms = HashMap::from([
            (
                "player1".to_owned(),
                Transform::from_translation(Vec3::new(10.0, 20.0, 30.0)),
            ),
            (
                "fx".to_owned(),
                Transform::from_translation(Vec3::new(0.0, 0.0, 0.0)),
            ),
        ]);

        assert_eq!(
            resolve_assignment_value(
                &AssignmentValue::Symbol("player1.y".to_owned()),
                &vars,
                Some(&transforms),
            ),
            Some(20.0)
        );
        assert_eq!(
            evaluate_position_statement(
                &PositionStatement {
                    target: "fx".to_owned(),
                    position: PositionValue::Coordinates(vec![
                        PositionExpr::EntityAxis {
                            entity: "player1".to_owned(),
                            axis: SceneMaxAxis::X,
                            offset: 0.0,
                        },
                        PositionExpr::EntityAxis {
                            entity: "player1".to_owned(),
                            axis: SceneMaxAxis::Y,
                            offset: 3.0,
                        },
                        PositionExpr::EntityAxis {
                            entity: "player1".to_owned(),
                            axis: SceneMaxAxis::Z,
                            offset: 0.0,
                        },
                    ]),
                },
                &transforms,
            ),
            Some(Vec3::new(10.0, 23.0, 30.0))
        );
    }

    #[test]
    fn scenemax_look_at_points_positive_z_forward() {
        let mut transform = Transform::from_translation(Vec3::ZERO);

        look_at_scenemax_forward(&mut transform, Vec3::Z);
        assert!(horizontal_forward(&transform).distance(Vec3::Z) < 0.001);

        look_at_scenemax_forward(&mut transform, Vec3::X);
        assert!(horizontal_forward(&transform).distance(Vec3::X) < 0.001);
    }

    #[test]
    fn detects_matching_current_animation_like_gltf_lookup() {
        let current = CurrentAnimation {
            clip: "Armature|Run_Sword".to_owned(),
            looped: true,
            speed: 1.0,
            elapsed_seconds: 0.0,
            duration_seconds: 0.65,
        };

        assert!(current_animation_matches(&current, "run_sword", true));
        assert!(!current_animation_matches(&current, "idle2", true));
    }
}
