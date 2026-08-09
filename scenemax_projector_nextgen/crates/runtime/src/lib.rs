use std::{
    collections::{BTreeMap, HashMap, HashSet},
    env, fs,
    io::Write,
    path::{Path, PathBuf},
    sync::Mutex,
    sync::atomic::{AtomicU64, Ordering},
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
    asset::{AssetApp, AssetPlugin, RenderAssetUsages, io::AssetSourceBuilder},
    ecs::system::SystemParam,
    gltf::Gltf,
    log::LogPlugin,
    mesh::{Indices, PrimitiveTopology},
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
    MoveToDestination, ObjectPoolStatement, PoolReleaseStatement, PositionExpr, PositionStatement,
    PositionValue, Program, SceneMaxAxis, SceneMaxBodyKind, SceneMaxCollisionShape, SceneMaxVec3,
    SpritePlayStatement, Statement, UiEaseDirection, UiTargetPath,
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

mod actions;
mod animation;
mod camera;
mod physics;
mod sprites;
mod startup;
mod ui;

use actions::*;
use animation::*;
use camera::*;
use physics::*;
use sprites::*;
use startup::*;
use ui::*;

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
    let effective_script_root = scene_program.1.clone().or_else(|| script_root.clone());
    let asset_root = project_root
        .as_ref()
        .map(|root| root.join("resources"))
        .filter(|path| path.is_dir());
    let builtin_asset_root = find_builtin_resources_root(
        project_root.as_deref(),
        effective_script_root.as_deref(),
        asset_root.as_deref(),
    );
    initialize_runtime_logger(project_root.as_deref(), effective_script_root.as_deref());

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
            script_root: effective_script_root,
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
        .init_resource::<SceneMaxPerfDebug>()
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
                update_sprite_animations,
                restore_default_idle_animations,
                play_pending_animations,
                apply_animation_speed_overrides,
            )
                .chain(),
        )
        .add_systems(
            Update,
            (
                clear_scenemax_ui_on_scene_change,
                apply_scenemax_ui_actions,
                update_scenemax_ui_eases,
                update_scenemax_ui_message_animations,
                update_scenemax_ui_bitmap_message_animations,
            )
                .chain(),
        )
        .add_systems(Update, update_scenemax_perf_debug)
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

#[derive(Debug, Resource)]
struct SceneMaxLaunchContext {
    script_root: Option<PathBuf>,
    asset_root: Option<PathBuf>,
    builtin_asset_root: Option<PathBuf>,
    window_width: u32,
    window_height: u32,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxStartupProgram(Option<Program>, Option<PathBuf>);

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
struct SceneMaxBoneAliasTarget {
    alias: String,
    owner: String,
    bone: String,
}

#[derive(Debug, Resource, Default)]
struct SceneMaxPerfDebug {
    elapsed_seconds: f32,
    frames: u32,
}

static PERF_BONE_ALIAS_NS: AtomicU64 = AtomicU64::new(0);
static PERF_TRANSFORM_BUILDS: AtomicU64 = AtomicU64::new(0);
static PERF_BONE_TARGET_RESOLVES: AtomicU64 = AtomicU64::new(0);
static PERF_BONE_ALIASES_INSERTED: AtomicU64 = AtomicU64::new(0);

fn update_scenemax_perf_debug(time: Res<Time>, mut perf: ResMut<SceneMaxPerfDebug>) {
    perf.elapsed_seconds += time.delta_secs();
    perf.frames += 1;
    if perf.elapsed_seconds < 1.0 {
        return;
    }
    let elapsed = perf.elapsed_seconds.max(f32::EPSILON);
    let fps = perf.frames as f32 / elapsed;
    let builds = PERF_TRANSFORM_BUILDS.swap(0, Ordering::Relaxed).max(1);
    let bone_ns = PERF_BONE_ALIAS_NS.swap(0, Ordering::Relaxed);
    let targets = PERF_BONE_TARGET_RESOLVES.swap(0, Ordering::Relaxed);
    let aliases = PERF_BONE_ALIASES_INSERTED.swap(0, Ordering::Relaxed);
    write_runtime_diagnostic_line(format!(
        "PERF fps={fps:.1} bone_ms/build={:.3} bone_targets/build={:.1} bone_aliases/build={:.1} transform_builds={builds}",
        bone_ns as f64 / 1_000_000.0 / builds as f64,
        targets as f64 / builds as f64,
        aliases as f64 / builds as f64,
    ));
    *perf = SceneMaxPerfDebug::default();
}

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
    scene_script_root: Option<PathBuf>,
    active_ui_name: Option<String>,
    loaded: HashMap<String, LoadedSceneMaxUi>,
    sprite_index: HashMap<String, SceneMaxSpriteAsset>,
    sprite_index_root: Option<PathBuf>,
    font_index: HashMap<String, SceneMaxBitmapFontAsset>,
    font_index_root: Option<PathBuf>,
    bitmap_fonts: HashMap<String, SceneMaxBitmapFont>,
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

#[derive(Debug, Clone, Copy, Component)]
struct SceneMaxUiSpriteSheet {
    rows: usize,
    cols: usize,
    image_width: u32,
    image_height: u32,
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

#[derive(Debug, Clone)]
struct SceneMaxBitmapFontAsset {
    path: String,
}

#[derive(Debug, Clone)]
struct SceneMaxBitmapFont {
    image: Handle<Image>,
    size: f32,
    line_height: f32,
    glyphs: HashMap<char, SceneMaxBitmapGlyph>,
}

#[derive(Debug, Clone, Copy)]
struct SceneMaxBitmapGlyph {
    source: Rect,
    width: f32,
    height: f32,
    x_offset: f32,
    y_offset: f32,
    x_advance: f32,
}

#[derive(Debug, Clone, Component)]
struct SceneMaxUiBitmapText {
    text: String,
    font_name: String,
    font_size: f32,
    color: Color,
    alignment: Justify,
    widget_width: f32,
    widget_height: f32,
    glyph_entities: Vec<Entity>,
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

#[derive(SystemParam)]
struct SceneMaxBoneQueries<'w, 's> {
    children: Query<'w, 's, &'static Children>,
    named_nodes: Query<'w, 's, (&'static Name, &'static GlobalTransform)>,
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
struct SceneMaxSprite {
    rows: usize,
    cols: usize,
    frame_count: usize,
    mesh: Handle<Mesh>,
}

#[derive(Debug, Clone, Component)]
struct SceneMaxSpriteAnimation {
    from_frame: usize,
    to_frame: usize,
    duration_seconds: f32,
    elapsed_seconds: f32,
    looped: bool,
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
    fn ui_text_property_resolves_variable_expressions() {
        let vars = SceneMaxVars(HashMap::from([("timer".to_owned(), 59.0)]));
        let guards = HashMap::new();

        assert_eq!(
            resolve_ui_property_value(
                &scenemax_parser::UiPropertyValue::Expression(AssignmentValue::Symbol(
                    "timer".to_owned(),
                )),
                &vars,
                None,
                &guards,
                None,
                None,
            ),
            "59"
        );
        assert_eq!(
            resolve_ui_property_value(
                &scenemax_parser::UiPropertyValue::Literal("timer".to_owned()),
                &vars,
                None,
                &guards,
                None,
                None,
            ),
            "timer"
        );
        assert_eq!(
            resolve_ui_property_value(
                &scenemax_parser::UiPropertyValue::Concatenation(vec![
                    scenemax_parser::UiPropertyValuePart::Literal("timer = ".to_owned()),
                    scenemax_parser::UiPropertyValuePart::Expression(AssignmentValue::Symbol(
                        "timer".to_owned(),
                    )),
                ]),
                &vars,
                None,
                &guards,
                None,
                None,
            ),
            "timer = 59"
        );
    }

    #[test]
    fn ui_sprite_frame_rect_selects_health_bar_rows() {
        assert_eq!(parse_ui_frame_value("5"), Some(5));
        assert_eq!(parse_ui_frame_value("5.4"), Some(5));

        let rect = ui_sprite_frame_rect(5, 1, 15, 384, 360);
        assert_eq!(rect.min, Vec2::new(0.0, 120.0));
        assert_eq!(rect.max, Vec2::new(384.0, 144.0));

        let clamped = ui_sprite_frame_rect(99, 1, 15, 384, 360);
        assert_eq!(clamped.min, Vec2::new(0.0, 336.0));
        assert_eq!(clamped.max, Vec2::new(384.0, 360.0));
    }

    #[test]
    fn ui_image_scale_mode_stretch_sets_bevy_stretch() {
        let widget: SceneMaxUiWidgetDef = serde_json::from_str(
            r#"{"name":"playerHealthFill","type":"IMAGE","imageScaleMode":"stretch"}"#,
        )
        .unwrap();
        let mut image_node = ImageNode::default();

        apply_ui_image_scale_mode(&widget, &mut image_node);

        assert!(matches!(image_node.image_mode, NodeImageMode::Stretch));
    }

    #[test]
    fn quoted_bone_alias_matches_position_subject_syntax() {
        assert_eq!(
            quoted_bone_alias("player2", "mixamorig:Head"),
            "player2.\"mixamorig:Head\""
        );
    }

    #[test]
    fn ui_sprite_index_loads_extension_file() {
        let root =
            std::env::temp_dir().join(format!("scenemax_ui_sprite_ext_{}", std::process::id()));
        let sprite_dir = root.join("sprites");
        let _ = fs::remove_dir_all(&root);
        fs::create_dir_all(&sprite_dir).unwrap();
        fs::write(
            sprite_dir.join("sprites-ext.json"),
            r#"{"sprites":[{"path":"sprites/bar1.png","name":"health_bar1","rows":15,"cols":1}]}"#,
        )
        .unwrap();

        let mut ui_runtime = SceneMaxUiRuntime::default();
        let context = SceneMaxLaunchContext {
            script_root: None,
            asset_root: Some(root.clone()),
            builtin_asset_root: None,
            window_width: 1600,
            window_height: 900,
        };

        refresh_sprite_index(&mut ui_runtime, &context);

        let sprite = ui_runtime.sprite_index.get("health_bar1").unwrap();
        assert_eq!(sprite.rows, 15);
        assert_eq!(sprite.cols, 1);
        assert_eq!(sprite.path, "sprites/bar1.png");
        let _ = fs::remove_dir_all(root);
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
    fn sprite_play_statement_builds_runtime_animation() {
        let animation = sprite_animation_from_statement(&SpritePlayStatement {
            target: "b".to_owned(),
            from_frame: 0,
            to_frame: 13,
            duration_seconds: 1.0,
            looped: true,
        });

        assert_eq!(animation.from_frame, 0);
        assert_eq!(animation.to_frame, 13);
        assert_eq!(animation.duration_seconds, 1.0);
        assert!(animation.looped);
    }

    #[test]
    fn sprite_display_size_defaults_to_world_unit_quad() {
        assert_eq!(
            sprite_display_size(&EntityOptions::default(), 184, 169),
            Vec2::ONE
        );
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
            ..Default::default()
        };
        let sphere_options = EntityOptions {
            collider: true,
            radius: Some(0.4),
            collision_shape: Some(SceneMaxCollisionShape::Sphere),
            ..Default::default()
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

    #[test]
    fn resolves_bitmap_font_page_next_to_fnt_asset() {
        assert_eq!(
            bitmap_font_page_asset_path("fonts/message_bold1.fnt", "message_bold1.png"),
            "fonts/message_bold1.png"
        );
        assert_eq!(
            bitmap_font_page_asset_path("builtin://fonts/arial_64.fnt", "arial_64.png"),
            "builtin://fonts/arial_64.png"
        );
        assert_eq!(parse_fnt_u32("char id=44 x=62", "id"), Some(44));
        assert_eq!(
            parse_fnt_string("page id=0 file=\"message_bold1.png\"", "file").as_deref(),
            Some("message_bold1.png")
        );
    }

    #[test]
    fn detects_ui_only_programs_as_runtime_content() {
        assert!(has_ui_runtime_content(&Program {
            statements: vec![Statement::UiLoad {
                name: "game_intro_ui".to_owned(),
            }],
        }));
        assert!(!has_ui_runtime_content(&Program {
            statements: vec![Statement::Wait { seconds: 0.1 }],
        }));
    }

    #[test]
    fn scene_entry_program_reports_effective_scene_script_root() {
        let root = std::env::temp_dir().join(format!(
            "scenemax_nextgen_scene_root_{}",
            std::process::id()
        ));
        let scene_dir = root.join("game_intro");
        let _ = fs::remove_dir_all(&root);
        fs::create_dir_all(&scene_dir).unwrap();
        fs::write(root.join("main"), "switch to \"game_intro\"\n").unwrap();
        fs::write(scene_dir.join("main"), "UI.load \"game_intro_ui\"\n").unwrap();

        let (program, script_root) = load_scene_entry_program(&root.join("main")).unwrap();

        assert_eq!(script_root, scene_dir);
        assert!(matches!(
            program.statements.first(),
            Some(Statement::UiLoad { name }) if name == "game_intro_ui"
        ));
        let _ = fs::remove_dir_all(&root);
    }

    #[test]
    fn scene_switch_resolves_sibling_scene_main_from_current_scene_root() {
        let current_scene = Path::new("C:/game/scripts/Fighting Game/game_intro");
        assert_eq!(
            scene_main_path(current_scene, "game_level1"),
            PathBuf::from("C:/game/scripts/Fighting Game/game_level1/main")
        );
    }
}
