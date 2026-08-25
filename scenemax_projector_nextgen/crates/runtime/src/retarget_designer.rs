use super::*;
use bevy::{
    gizmos::transform_gizmo::{
        TransformGizmoCamera, TransformGizmoFocus, TransformGizmoMode, TransformGizmoPlugin,
        TransformGizmoSettings, TransformGizmoSpace,
    },
    gltf::GltfAssetLabel,
    input::mouse::{AccumulatedMouseMotion, MouseScrollUnit, MouseWheel},
    ui::RelativeCursorPosition,
    window::PrimaryWindow,
};
use serde_json::{Value, json};

#[derive(Debug, Clone)]
pub struct BevyRetargetDesignerLaunch {
    pub project_root: PathBuf,
    pub animation: String,
    pub model: Option<String>,
}

const PANEL_BG: Color = Color::srgba(0.045, 0.055, 0.072, 0.94);
const BUTTON_BG: Color = Color::srgb(0.105, 0.125, 0.150);
const BUTTON_HOVER: Color = Color::srgb(0.145, 0.175, 0.210);
const BUTTON_ACTIVE: Color = Color::srgb(0.140, 0.400, 0.480);
const TEXT_MAIN: Color = Color::srgb(0.935, 0.955, 0.975);
const TEXT_MUTED: Color = Color::srgb(0.610, 0.690, 0.770);
const TEXT_ACCENT: Color = Color::srgb(0.560, 0.900, 0.940);
const MODEL_PAGE_SIZE: usize = 8;
const PANEL_WIDTH: f32 = 390.0;
const PANEL_WITH_PADDING: f32 = PANEL_WIDTH + 36.0;
const SCROLL_LINE_HEIGHT: f32 = 28.0;

#[derive(Debug, Resource)]
struct BevyRetargetDesignerState {
    animation_name: String,
    animation_asset_path: String,
    source_clip: String,
    animation_index_path: PathBuf,
    source_gltf: Handle<Gltf>,
    selected_model_index: usize,
    model_page: usize,
    profile: String,
    skip_top_animated_targets: usize,
    exclude_bones: Vec<String>,
    root_bone: String,
    scale_base_bone: String,
    remove_unimportant_translation_tracks: bool,
    remove_motion_translation_tracks: bool,
    remove_motion_rotation_tracks: bool,
    normalize_motion_scale: bool,
    visual_rotation_degrees: [f32; 3],
    visual_translation: Vec3,
    locked_translation_axes: [bool; 3],
    camera_distance: f32,
    preview_target: Vec3,
    speed: f32,
    dirty: bool,
    status: String,
    pending_replay: bool,
    pending_bake: bool,
    current_root: Option<Entity>,
}

#[derive(Debug, Clone)]
struct RetargetBakedIndexEntry {
    model: String,
    path: String,
    clip_name: String,
    visual_rotation_baked: bool,
}

#[derive(Debug, Clone)]
struct RetargetDesignerModel {
    label: String,
    name: Option<String>,
    asset_path: String,
    scale: Vec3,
    rotation_y_degrees: f32,
}

#[derive(Debug, Resource)]
struct BevyRetargetDesignerCatalog {
    asset_root: PathBuf,
    models: Vec<RetargetDesignerModel>,
}

#[derive(Debug, Clone, Copy, Component)]
enum RetargetDesignerAction {
    GizmoMode(RetargetGizmoMode),
    ResetVisualTransform,
    Profile(usize),
    Skip(usize),
    ToggleTranslationAxis(usize),
    ResetTranslationAxes,
    Rotate(usize, f32),
    ResetRotation,
    Zoom(f32),
    ResetZoom,
    Speed(f32),
    Replay,
    Save,
    ModelPage(i32),
    ModelSlot(usize),
    Close,
}

#[derive(Debug, Component)]
struct RetargetDesignerButton;

#[derive(Debug, Component)]
struct RetargetDesignerButtonLabel;

#[derive(Debug, Component)]
struct RetargetDesignerStatusText;

#[derive(Debug, Component)]
struct RetargetDesignerValueText(RetargetDesignerValue);

#[derive(Debug, Component)]
struct RetargetDesignerModelSlotLabel(usize);

#[derive(Debug, Clone, Copy, PartialEq, Eq, Component)]
enum RetargetDesignerValue {
    Gizmo,
    Model,
    Profile,
    Skip,
    TranslationAxes,
    Rotation,
    Zoom,
    Speed,
}

#[derive(Debug, Component)]
struct RetargetDesignerModelRoot;

#[derive(Debug, Component)]
struct RetargetDesignerCamera;

#[derive(Debug, Component)]
struct RetargetDesignerScrollPanel;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Component)]
enum RetargetGizmoMode {
    Move,
    Rotate,
}

pub fn run_bevy_retarget_designer(launch: BevyRetargetDesignerLaunch) {
    let catalog = BevyRetargetDesignerCatalog::discover(&launch.project_root);
    let asset_file_path = catalog.asset_root.to_string_lossy().to_string();
    let state = BevyRetargetDesignerState::load(launch, &catalog);
    App::new()
        .insert_resource(ClearColor(Color::srgb(0.025, 0.035, 0.05)))
        .insert_resource(SceneMaxRuntimeAssets::default())
        .insert_resource(SceneMaxAnimationDurations::default())
        .insert_resource(state)
        .insert_resource(catalog)
        .insert_resource(WinitSettings::continuous())
        .insert_resource(TransformGizmoSettings {
            mode: TransformGizmoMode::Translate,
            space: TransformGizmoSpace::World,
            snap_rotate: Some(5.0_f32.to_radians()),
            ..default()
        })
        .add_plugins(
            DefaultPlugins
                .build()
                .disable::<LogPlugin>()
                .set(AssetPlugin {
                    file_path: asset_file_path,
                    ..default()
                })
                .set(WindowPlugin {
                    primary_window: Some(Window {
                        title: "SceneMax Bevy Retarget Designer".to_owned(),
                        present_mode: PresentMode::AutoVsync,
                        resolution: WindowResolution::new(1440, 900)
                            .with_scale_factor_override(1.0),
                        ..default()
                    }),
                    ..default()
                }),
        )
        .add_plugins(TransformGizmoPlugin)
        .add_systems(Startup, setup_bevy_retarget_designer)
        .add_systems(
            Update,
            (
                handle_retarget_buttons,
                update_retarget_button_colors,
                handle_retarget_panel_scroll,
                handle_retarget_viewport_mouse,
                update_retarget_keyboard,
                sync_retarget_gizmo_to_state,
                apply_retarget_state_to_root_transform,
                replay_retarget_preview,
                play_pending_animations,
                update_retarget_camera,
                update_retarget_ui_state,
                exit_retarget_on_escape,
            )
                .chain(),
        )
        .run();
}

impl BevyRetargetDesignerState {
    fn load(launch: BevyRetargetDesignerLaunch, catalog: &BevyRetargetDesignerCatalog) -> Self {
        let animation =
            scenemax_assets::resolve_animation_resource(&catalog.asset_root, &launch.animation)
                .ok();
        let retarget = animation
            .as_ref()
            .map(|animation| animation.bevy_retarget.clone())
            .unwrap_or_default();
        let animation_asset_path = animation
            .as_ref()
            .map(|animation| animation.asset_path.clone())
            .unwrap_or_default();
        let source_clip = animation
            .as_ref()
            .map(|animation| animation.clip_name.clone())
            .unwrap_or_else(|| launch.animation.clone());
        let selected_model_index = launch
            .model
            .as_deref()
            .and_then(|model| catalog.model_index(model))
            .unwrap_or(0);
        let source_gltf = Handle::default();
        Self {
            animation_name: launch.animation,
            animation_asset_path,
            source_clip,
            animation_index_path: animation_index_path(&catalog.asset_root),
            source_gltf,
            selected_model_index,
            model_page: selected_model_index / MODEL_PAGE_SIZE,
            profile: retarget.profile,
            skip_top_animated_targets: retarget.skip_top_animated_targets,
            exclude_bones: retarget.exclude_bones,
            root_bone: retarget.root_bone,
            scale_base_bone: retarget.scale_base_bone,
            remove_unimportant_translation_tracks: retarget.remove_unimportant_translation_tracks,
            remove_motion_translation_tracks: retarget.remove_motion_translation_tracks,
            remove_motion_rotation_tracks: retarget.remove_motion_rotation_tracks,
            normalize_motion_scale: retarget.normalize_motion_scale,
            visual_rotation_degrees: retarget.visual_rotation_degrees,
            visual_translation: Vec3::from_array(retarget.visual_translation),
            locked_translation_axes: retarget.locked_translation_axes,
            camera_distance: 6.0,
            preview_target: Vec3::new(0.0, 1.1, 0.0),
            speed: 1.0,
            dirty: false,
            status: "Adjust values, replay, then save when the preview looks right.".to_owned(),
            pending_replay: true,
            pending_bake: false,
            current_root: None,
        }
    }

    fn retarget_options(&self) -> scenemax_assets::AnimationRetargetOptions {
        scenemax_assets::AnimationRetargetOptions {
            profile: self.profile.clone(),
            skip_top_animated_targets: self.skip_top_animated_targets,
            exclude_bones: self.exclude_bones.clone(),
            root_bone: self.root_bone.clone(),
            scale_base_bone: self.scale_base_bone.clone(),
            remove_unimportant_translation_tracks: self.remove_unimportant_translation_tracks,
            remove_motion_translation_tracks: self.remove_motion_translation_tracks,
            remove_motion_rotation_tracks: self.remove_motion_rotation_tracks,
            normalize_motion_scale: self.normalize_motion_scale,
            visual_translation: self.visual_translation.to_array(),
            visual_rotation_degrees: self.visual_rotation_degrees,
            locked_translation_axes: self.locked_translation_axes,
        }
    }

    fn preview_retarget_options(&self) -> scenemax_assets::AnimationRetargetOptions {
        let mut options = self.retarget_options();
        options.visual_translation = [0.0, 0.0, 0.0];
        options.visual_rotation_degrees = [0.0, 0.0, 0.0];
        options
    }

    fn mark_dirty(&mut self) {
        self.dirty = true;
        self.pending_replay = true;
    }

    fn save(&mut self, catalog: &BevyRetargetDesignerCatalog) {
        let baked = self.baked_retarget_entry(catalog);
        match save_retarget_options(
            &self.animation_index_path,
            &self.animation_name,
            &self.retarget_options(),
            baked.as_ref(),
        ) {
            Ok(()) => {
                self.dirty = false;
                self.pending_bake = baked.is_some();
                self.pending_replay = true;
                self.status = format!("Saved Bevy retarget settings for {}", self.animation_name);
            }
            Err(error) => {
                self.status = format!("Save failed: {error}");
            }
        }
    }

    fn baked_retarget_entry(
        &self,
        catalog: &BevyRetargetDesignerCatalog,
    ) -> Option<RetargetBakedIndexEntry> {
        let model = catalog.models.get(self.selected_model_index)?;
        let model_name = model.name.clone().unwrap_or_else(|| model.label.clone());
        if model_name.trim().is_empty() {
            return None;
        }
        let model_key = normalized_animation_name(&model_name);
        Some(RetargetBakedIndexEntry {
            model: model_name,
            path: format!(
                "animations/{}/baked/{}.json",
                normalized_animation_name(&self.animation_name),
                model_key
            ),
            clip_name: format!("{}_{}", self.animation_name, model_key),
            visual_rotation_baked: true,
        })
    }
}

impl BevyRetargetDesignerCatalog {
    fn discover(project_root: &Path) -> Self {
        let asset_root = project_root.join("resources");
        let mut models = Vec::new();
        collect_indexed_models(&asset_root, &mut models);
        if models.is_empty() {
            collect_gltf_models(&asset_root, &asset_root, &mut models);
        }
        models.sort_by(|left, right| {
            left.label
                .to_ascii_lowercase()
                .cmp(&right.label.to_ascii_lowercase())
        });
        Self { asset_root, models }
    }

    fn page_count(&self) -> usize {
        self.models.len().div_ceil(MODEL_PAGE_SIZE).max(1)
    }

    fn model_index_for_slot(&self, page: usize, slot: usize) -> Option<usize> {
        let index = page.saturating_mul(MODEL_PAGE_SIZE).saturating_add(slot);
        (index < self.models.len()).then_some(index)
    }

    fn model_index(&self, model_name: &str) -> Option<usize> {
        self.models.iter().position(|model| {
            model
                .name
                .as_deref()
                .is_some_and(|name| name.eq_ignore_ascii_case(model_name))
                || model.label.eq_ignore_ascii_case(model_name)
                || model.asset_path.eq_ignore_ascii_case(model_name)
        })
    }
}

fn setup_bevy_retarget_designer(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    mut state: ResMut<BevyRetargetDesignerState>,
    catalog: Res<BevyRetargetDesignerCatalog>,
) {
    commands.spawn((
        Camera3d::default(),
        retarget_camera_transform(state.camera_distance, state.preview_target),
        RetargetDesignerCamera,
        TransformGizmoCamera,
    ));
    commands.spawn((
        DirectionalLight {
            illuminance: 20_000.0,
            shadow_maps_enabled: true,
            ..default()
        },
        Transform::from_xyz(-4.5, 7.0, 4.0).looking_at(Vec3::ZERO, Vec3::Y),
    ));
    commands.insert_resource(GlobalAmbientLight {
        color: Color::srgb(0.78, 0.84, 1.0),
        brightness: 260.0,
        ..default()
    });

    state.source_gltf = asset_server.load(state.animation_asset_path.clone());
    spawn_retarget_model(&mut commands, &asset_server, &mut state, catalog.as_ref());
    spawn_retarget_designer_ui(&mut commands, &state, catalog.as_ref());
}

fn retarget_camera_transform(distance: f32, target: Vec3) -> Transform {
    Transform::from_translation(target + Vec3::new(-distance * 0.45, distance * 0.35, distance))
        .looking_at(target, Vec3::Y)
}

fn spawn_retarget_model(
    commands: &mut Commands,
    asset_server: &AssetServer,
    state: &mut BevyRetargetDesignerState,
    catalog: &BevyRetargetDesignerCatalog,
) {
    let Some(model) = catalog.models.get(state.selected_model_index) else {
        state.status = "No GLTF model was found in project resources.".to_owned();
        return;
    };
    let gltf: Handle<Gltf> = asset_server.load(model.asset_path.clone());
    let scene = asset_server.load(GltfAssetLabel::Scene(0).from_asset(model.asset_path.clone()));
    let transform = retarget_model_transform(state, model);
    let entity = commands
        .spawn((
            SceneMaxEntity {
                name: "retarget_preview".to_owned(),
                runtime_name: "retarget_preview@1".to_owned(),
            },
            SceneMaxGltf { gltf },
            WorldAssetRoot(scene),
            transform,
            Visibility::Inherited,
            RetargetDesignerModelRoot,
            TransformGizmoFocus,
        ))
        .id();
    state.current_root = Some(entity);
    state.pending_replay = true;
}

fn retarget_model_transform(
    state: &BevyRetargetDesignerState,
    model: &RetargetDesignerModel,
) -> Transform {
    let rotation = Quat::from_rotation_y(model.rotation_y_degrees.to_radians())
        * Quat::from_euler(
            EulerRot::XYZ,
            state.visual_rotation_degrees[0].to_radians(),
            state.visual_rotation_degrees[1].to_radians(),
            state.visual_rotation_degrees[2].to_radians(),
        );
    Transform {
        translation: state.visual_translation,
        rotation,
        scale: model.scale,
    }
}

fn spawn_retarget_designer_ui(
    commands: &mut Commands,
    state: &BevyRetargetDesignerState,
    catalog: &BevyRetargetDesignerCatalog,
) {
    commands.spawn((
        Camera2d,
        Camera {
            order: 1,
            ..default()
        },
        IsDefaultUiCamera,
    ));
    commands
        .spawn(Node {
            position_type: PositionType::Absolute,
            left: px(0.0),
            top: px(0.0),
            width: Val::Percent(100.0),
            height: Val::Percent(100.0),
            flex_direction: FlexDirection::Row,
            justify_content: JustifyContent::SpaceBetween,
            padding: UiRect::all(px(18.0)),
            ..default()
        })
        .with_children(|root| {
            root.spawn(panel_frame(PANEL_WIDTH)).with_children(|panel| {
                panel.spawn(text_bundle("Bevy Retarget Designer", 24.0, TEXT_MAIN));
                panel.spawn(text_bundle(
                    format!("Animation: {}", state.animation_name),
                    12.0,
                    TEXT_MUTED,
                ));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Save",
                        RetargetDesignerAction::Save,
                        false,
                        92.0,
                    ));
                    row.spawn(action_button(
                        "Close",
                        RetargetDesignerAction::Close,
                        false,
                        92.0,
                    ));
                });
                panel.spawn(section_label("Gizmo"));
                panel.spawn(value_text(RetargetDesignerValue::Gizmo));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Move",
                        RetargetDesignerAction::GizmoMode(RetargetGizmoMode::Move),
                        true,
                        92.0,
                    ));
                    row.spawn(action_button(
                        "Rotate",
                        RetargetDesignerAction::GizmoMode(RetargetGizmoMode::Rotate),
                        false,
                        92.0,
                    ));
                    row.spawn(action_button(
                        "Reset",
                        RetargetDesignerAction::ResetVisualTransform,
                        false,
                        92.0,
                    ));
                });

                panel.spawn(section_label("Preview Model"));
                panel.spawn(value_text(RetargetDesignerValue::Model));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Prev",
                        RetargetDesignerAction::ModelPage(-1),
                        false,
                        80.0,
                    ));
                    row.spawn(action_button(
                        "Next",
                        RetargetDesignerAction::ModelPage(1),
                        false,
                        80.0,
                    ));
                });
                panel.spawn(button_grid()).with_children(|grid| {
                    for slot in 0..MODEL_PAGE_SIZE {
                        let label = catalog
                            .model_index_for_slot(state.model_page, slot)
                            .and_then(|index| catalog.models.get(index))
                            .map(|model| model.label.as_str())
                            .unwrap_or("-");
                        grid.spawn(model_slot_button(label, slot));
                    }
                });

                panel.spawn(section_label("Retarget Profile"));
                panel.spawn(value_text(RetargetDesignerValue::Profile));
                panel.spawn(toolbar_row()).with_children(|row| {
                    for (index, profile) in RETARGET_PROFILE_CHOICES.iter().enumerate() {
                        row.spawn(action_button(
                            profile.label,
                            RetargetDesignerAction::Profile(index),
                            state.profile == profile.value,
                            92.0,
                        ));
                    }
                });

                panel.spawn(section_label("Root Levels"));
                panel.spawn(value_text(RetargetDesignerValue::Skip));
                panel.spawn(button_grid()).with_children(|grid| {
                    for skip in 0..=5 {
                        grid.spawn(action_button(
                            match skip {
                                0 => "0",
                                1 => "1",
                                2 => "2",
                                3 => "3",
                                4 => "4",
                                _ => "5",
                            },
                            RetargetDesignerAction::Skip(skip),
                            state.skip_top_animated_targets == skip,
                            74.0,
                        ));
                    }
                });

                panel.spawn(section_label("Movement Axes"));
                panel.spawn(value_text(RetargetDesignerValue::TranslationAxes));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Lock X",
                        RetargetDesignerAction::ToggleTranslationAxis(0),
                        state.locked_translation_axes[0],
                        82.0,
                    ));
                    row.spawn(action_button(
                        "Lock Y",
                        RetargetDesignerAction::ToggleTranslationAxis(1),
                        state.locked_translation_axes[1],
                        82.0,
                    ));
                    row.spawn(action_button(
                        "Lock Z",
                        RetargetDesignerAction::ToggleTranslationAxis(2),
                        state.locked_translation_axes[2],
                        82.0,
                    ));
                    row.spawn(action_button(
                        "Reset",
                        RetargetDesignerAction::ResetTranslationAxes,
                        false,
                        82.0,
                    ));
                });

                panel.spawn(section_label("Rotation Gizmo"));
                panel.spawn(value_text(RetargetDesignerValue::Rotation));
                panel.spawn(button_grid()).with_children(|grid| {
                    grid.spawn(action_button(
                        "Pitch -",
                        RetargetDesignerAction::Rotate(0, -5.0),
                        false,
                        82.0,
                    ));
                    grid.spawn(action_button(
                        "Pitch +",
                        RetargetDesignerAction::Rotate(0, 5.0),
                        false,
                        82.0,
                    ));
                    grid.spawn(action_button(
                        "Yaw -",
                        RetargetDesignerAction::Rotate(1, -5.0),
                        false,
                        82.0,
                    ));
                    grid.spawn(action_button(
                        "Yaw +",
                        RetargetDesignerAction::Rotate(1, 5.0),
                        false,
                        82.0,
                    ));
                    grid.spawn(action_button(
                        "Roll -",
                        RetargetDesignerAction::Rotate(2, -5.0),
                        false,
                        82.0,
                    ));
                    grid.spawn(action_button(
                        "Roll +",
                        RetargetDesignerAction::Rotate(2, 5.0),
                        false,
                        82.0,
                    ));
                    grid.spawn(action_button(
                        "Reset",
                        RetargetDesignerAction::ResetRotation,
                        false,
                        82.0,
                    ));
                });

                panel.spawn(section_label("View"));
                panel.spawn(value_text(RetargetDesignerValue::Zoom));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Zoom In",
                        RetargetDesignerAction::Zoom(-0.5),
                        false,
                        92.0,
                    ));
                    row.spawn(action_button(
                        "Zoom Out",
                        RetargetDesignerAction::Zoom(0.5),
                        false,
                        92.0,
                    ));
                    row.spawn(action_button(
                        "Reset",
                        RetargetDesignerAction::ResetZoom,
                        false,
                        92.0,
                    ));
                });

                panel.spawn(section_label("Playback"));
                panel.spawn(value_text(RetargetDesignerValue::Speed));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button(
                        "Slower",
                        RetargetDesignerAction::Speed(-0.1),
                        false,
                        92.0,
                    ));
                    row.spawn(action_button(
                        "Faster",
                        RetargetDesignerAction::Speed(0.1),
                        false,
                        92.0,
                    ));
                    row.spawn(action_button(
                        "Replay",
                        RetargetDesignerAction::Replay,
                        false,
                        92.0,
                    ));
                });

                panel.spawn((
                    Text::new(state.status.clone()),
                    TextFont::from_font_size(12.0),
                    TextColor(TEXT_ACCENT),
                    RetargetDesignerStatusText,
                ));
            });
        });
}

fn replay_retarget_preview(
    mut commands: Commands,
    catalog: Res<BevyRetargetDesignerCatalog>,
    mut state: ResMut<BevyRetargetDesignerState>,
) {
    if !state.pending_replay {
        return;
    }
    state.pending_replay = false;
    let Some(root) = state.current_root else {
        return;
    };
    let bake_request = if state.pending_bake {
        state.pending_bake = false;
        state
            .baked_retarget_entry(catalog.as_ref())
            .map(|baked| RuntimeAnimationBakeRequest {
                output_path: catalog.asset_root.join(&baked.path),
                model: baked.model,
                clip: baked.clip_name,
            })
    } else {
        None
    };
    commands.entity(root).insert(AnimationToPlay {
        clip: state.source_clip.clone(),
        runtime_clip: state.animation_name.clone(),
        looped: true,
        speed: state.speed,
        gltf: state.source_gltf.clone(),
        target_model_resource: None,
        baked_external: None,
        bake_request,
        external_retarget: state.preview_retarget_options(),
        external_source: true,
        tried_external_source: true,
        visual_transform_preapplied: false,
        retarget_wait_logged: false,
    });
}

fn handle_retarget_buttons(
    mut commands: Commands,
    interactions: Query<
        (&Interaction, &RetargetDesignerAction),
        (Changed<Interaction>, With<RetargetDesignerButton>),
    >,
    mut state: ResMut<BevyRetargetDesignerState>,
    mut gizmo_settings: ResMut<TransformGizmoSettings>,
    catalog: Res<BevyRetargetDesignerCatalog>,
    asset_server: Res<AssetServer>,
    model_roots: Query<Entity, With<RetargetDesignerModelRoot>>,
    mut app_exit: MessageWriter<AppExit>,
) {
    for (interaction, action) in &interactions {
        if *interaction != Interaction::Pressed {
            continue;
        }
        match *action {
            RetargetDesignerAction::GizmoMode(mode) => {
                gizmo_settings.mode = match mode {
                    RetargetGizmoMode::Move => TransformGizmoMode::Translate,
                    RetargetGizmoMode::Rotate => TransformGizmoMode::Rotate,
                };
            }
            RetargetDesignerAction::ResetVisualTransform => {
                state.visual_translation = Vec3::ZERO;
                state.visual_rotation_degrees = [0.0, 0.0, 0.0];
                state.mark_dirty();
            }
            RetargetDesignerAction::Profile(index) => {
                if let Some(profile) = RETARGET_PROFILE_CHOICES.get(index)
                    && state.profile != profile.value
                {
                    state.profile = profile.value.to_owned();
                    state.mark_dirty();
                }
            }
            RetargetDesignerAction::Skip(skip) => {
                state.skip_top_animated_targets = skip;
                state.mark_dirty();
            }
            RetargetDesignerAction::ToggleTranslationAxis(axis) => {
                if let Some(value) = state.locked_translation_axes.get_mut(axis) {
                    *value = !*value;
                    state.mark_dirty();
                }
            }
            RetargetDesignerAction::ResetTranslationAxes => {
                state.locked_translation_axes = [false, false, false];
                state.mark_dirty();
            }
            RetargetDesignerAction::Rotate(axis, delta) => {
                if let Some(value) = state.visual_rotation_degrees.get_mut(axis) {
                    *value = wrap_degrees(*value + delta);
                    state.mark_dirty();
                }
            }
            RetargetDesignerAction::ResetRotation => {
                state.visual_rotation_degrees = [0.0, 0.0, 0.0];
                state.mark_dirty();
            }
            RetargetDesignerAction::Zoom(delta) => {
                state.camera_distance = (state.camera_distance + delta).clamp(2.0, 14.0);
            }
            RetargetDesignerAction::ResetZoom => {
                state.camera_distance = 6.0;
                state.preview_target = Vec3::new(0.0, 1.1, 0.0);
            }
            RetargetDesignerAction::Speed(delta) => {
                state.speed = (state.speed + delta).clamp(0.1, 3.0);
                state.pending_replay = true;
            }
            RetargetDesignerAction::Replay => {
                state.pending_replay = true;
            }
            RetargetDesignerAction::Save => state.save(catalog.as_ref()),
            RetargetDesignerAction::ModelPage(direction) => {
                let page_count = catalog.page_count();
                if direction < 0 {
                    state.model_page = state.model_page.saturating_sub(1);
                } else {
                    state.model_page = (state.model_page + 1).min(page_count.saturating_sub(1));
                }
            }
            RetargetDesignerAction::ModelSlot(slot) => {
                if let Some(index) = catalog.model_index_for_slot(state.model_page, slot) {
                    state.selected_model_index = index;
                    despawn_retarget_model_roots(&model_roots, &mut commands);
                    spawn_retarget_model(
                        &mut commands,
                        &asset_server,
                        &mut state,
                        catalog.as_ref(),
                    );
                }
            }
            RetargetDesignerAction::Close => {
                if state.dirty {
                    state.save(catalog.as_ref());
                }
                app_exit.write(AppExit::Success);
            }
        }
    }
}

fn update_retarget_button_colors(
    mut buttons: Query<
        (&Interaction, &RetargetDesignerAction, &mut BackgroundColor),
        With<RetargetDesignerButton>,
    >,
    state: Res<BevyRetargetDesignerState>,
    gizmo_settings: Res<TransformGizmoSettings>,
) {
    for (interaction, action, mut color) in &mut buttons {
        let active = match *action {
            RetargetDesignerAction::GizmoMode(RetargetGizmoMode::Move) => {
                gizmo_settings.mode == TransformGizmoMode::Translate
            }
            RetargetDesignerAction::GizmoMode(RetargetGizmoMode::Rotate) => {
                gizmo_settings.mode == TransformGizmoMode::Rotate
            }
            RetargetDesignerAction::Profile(index) => RETARGET_PROFILE_CHOICES
                .get(index)
                .is_some_and(|profile| state.profile == profile.value),
            RetargetDesignerAction::Skip(skip) => skip == state.skip_top_animated_targets,
            RetargetDesignerAction::ToggleTranslationAxis(axis) => state
                .locked_translation_axes
                .get(axis)
                .copied()
                .unwrap_or(false),
            _ => false,
        };
        *color = BackgroundColor(match (*interaction, active) {
            (Interaction::Hovered, true) | (Interaction::Pressed, true) => BUTTON_ACTIVE,
            (Interaction::Hovered, false) | (Interaction::Pressed, false) => BUTTON_HOVER,
            (_, true) => BUTTON_ACTIVE,
            _ => BUTTON_BG,
        });
    }
}

fn update_retarget_keyboard(
    input: Res<ButtonInput<KeyCode>>,
    catalog: Res<BevyRetargetDesignerCatalog>,
    mut state: ResMut<BevyRetargetDesignerState>,
) {
    if input.just_pressed(KeyCode::KeyS) {
        state.save(catalog.as_ref());
    }
    if input.just_pressed(KeyCode::KeyR) {
        state.pending_replay = true;
    }
}

fn handle_retarget_panel_scroll(
    mut wheel_events: MessageReader<MouseWheel>,
    mut panels: Query<
        (
            &mut ScrollPosition,
            &Node,
            &ComputedNode,
            &RelativeCursorPosition,
        ),
        With<RetargetDesignerScrollPanel>,
    >,
) {
    let Ok((mut scroll_position, node, computed, cursor)) = panels.single_mut() else {
        return;
    };
    if cursor.normalized.is_none() || node.overflow.y != OverflowAxis::Scroll {
        return;
    }

    let max_offset = ((computed.content_size() - computed.size())
        * computed.inverse_scale_factor())
    .max(Vec2::ZERO);
    for event in wheel_events.read() {
        let unit = match event.unit {
            MouseScrollUnit::Line => SCROLL_LINE_HEIGHT,
            MouseScrollUnit::Pixel => 1.0,
        };
        scroll_position.y = (scroll_position.y - event.y * unit).clamp(0.0, max_offset.y);
    }
}

fn handle_retarget_viewport_mouse(
    windows: Query<&Window, With<PrimaryWindow>>,
    buttons: Res<ButtonInput<MouseButton>>,
    motion: Res<AccumulatedMouseMotion>,
    mut wheel_events: MessageReader<MouseWheel>,
    mut state: ResMut<BevyRetargetDesignerState>,
) {
    let Ok(window) = windows.single() else {
        return;
    };
    if !cursor_in_retarget_preview(window) {
        return;
    }

    let delta = motion.delta;
    if delta != Vec2::ZERO {
        if buttons.pressed(MouseButton::Right) || buttons.pressed(MouseButton::Middle) {
            let pan_scale = state.camera_distance * 0.0035;
            state.preview_target.x -= delta.x * pan_scale;
            state.preview_target.y += delta.y * pan_scale;
        }
    }

    for event in wheel_events.read() {
        let unit = match event.unit {
            MouseScrollUnit::Line => 0.45,
            MouseScrollUnit::Pixel => 0.018,
        };
        state.camera_distance = (state.camera_distance - event.y * unit).clamp(2.0, 14.0);
    }
}

fn sync_retarget_gizmo_to_state(
    mut state: ResMut<BevyRetargetDesignerState>,
    catalog: Res<BevyRetargetDesignerCatalog>,
    model_roots: Query<&Transform, (With<RetargetDesignerModelRoot>, Changed<Transform>)>,
) {
    let Some(model) = catalog.models.get(state.selected_model_index) else {
        return;
    };
    let Ok(transform) = model_roots.single() else {
        return;
    };
    if transform.scale != model.scale {
        return;
    }

    let visual_rotation =
        Quat::from_rotation_y(-model.rotation_y_degrees.to_radians()) * transform.rotation;
    let (pitch, yaw, roll) = visual_rotation.to_euler(EulerRot::XYZ);
    let next_rotation = [
        wrap_degrees(pitch.to_degrees()),
        wrap_degrees(yaw.to_degrees()),
        wrap_degrees(roll.to_degrees()),
    ];

    if !vec3_nearly_equal(state.visual_translation, transform.translation)
        || !rotation_degrees_nearly_equal(state.visual_rotation_degrees, next_rotation)
    {
        state.visual_translation = transform.translation;
        state.visual_rotation_degrees = next_rotation;
        state.dirty = true;
        state.pending_replay = true;
    }
}

fn apply_retarget_state_to_root_transform(
    state: Res<BevyRetargetDesignerState>,
    catalog: Res<BevyRetargetDesignerCatalog>,
    mut model_roots: Query<&mut Transform, With<RetargetDesignerModelRoot>>,
) {
    if !state.is_changed() {
        return;
    }
    let Some(model) = catalog.models.get(state.selected_model_index) else {
        return;
    };
    let Ok(mut transform) = model_roots.single_mut() else {
        return;
    };
    let expected = retarget_model_transform(&state, model);
    if !transform_nearly_equal(&transform, &expected) {
        *transform = expected;
    }
}

fn vec3_nearly_equal(left: Vec3, right: Vec3) -> bool {
    left.distance_squared(right) <= 0.000001
}

fn rotation_degrees_nearly_equal(left: [f32; 3], right: [f32; 3]) -> bool {
    left.iter()
        .zip(right.iter())
        .all(|(left, right)| (*left - *right).abs() <= 0.01)
}

fn transform_nearly_equal(left: &Transform, right: &Transform) -> bool {
    vec3_nearly_equal(left.translation, right.translation)
        && left.rotation.dot(right.rotation).abs() > 0.99999
        && vec3_nearly_equal(left.scale, right.scale)
}

fn cursor_in_retarget_preview(window: &Window) -> bool {
    window
        .cursor_position()
        .is_some_and(|position| position.x >= PANEL_WITH_PADDING)
}

fn update_retarget_ui_state(
    state: Res<BevyRetargetDesignerState>,
    catalog: Res<BevyRetargetDesignerCatalog>,
    mut text_queries: ParamSet<(
        Query<(&RetargetDesignerValueText, &mut Text)>,
        Query<&mut Text, With<RetargetDesignerStatusText>>,
        Query<(&RetargetDesignerModelSlotLabel, &mut Text)>,
    )>,
) {
    if !state.is_changed() {
        return;
    }
    for (value, mut text) in &mut text_queries.p0() {
        *text = Text::new(match value.0 {
            RetargetDesignerValue::Gizmo => format!(
                "Move: x {:.2}, y {:.2}, z {:.2}",
                state.visual_translation.x, state.visual_translation.y, state.visual_translation.z
            ),
            RetargetDesignerValue::Model => model_caption(&state, &catalog),
            RetargetDesignerValue::Profile => {
                format!("Profile: {}", retarget_profile_label(&state.profile))
            }
            RetargetDesignerValue::Skip => format!(
                "Skip top animated targets: {}",
                state.skip_top_animated_targets
            ),
            RetargetDesignerValue::TranslationAxes => format!(
                "Locked movement: X {}, Y {}, Z {}",
                on_off(state.locked_translation_axes[0]),
                on_off(state.locked_translation_axes[1]),
                on_off(state.locked_translation_axes[2])
            ),
            RetargetDesignerValue::Rotation => format!(
                "Rotation offset: pitch {:.0} deg, yaw {:.0} deg, roll {:.0} deg",
                state.visual_rotation_degrees[0],
                state.visual_rotation_degrees[1],
                state.visual_rotation_degrees[2]
            ),
            RetargetDesignerValue::Zoom => format!("Camera distance: {:.1}", state.camera_distance),
            RetargetDesignerValue::Speed => format!("Speed: {:.1}x", state.speed),
        });
    }
    for mut text in &mut text_queries.p1() {
        *text = Text::new(state.status.clone());
    }
    for (slot, mut text) in &mut text_queries.p2() {
        *text = Text::new(model_slot_label(&state, &catalog, slot.0));
    }
}

#[derive(Debug, Clone, Copy)]
struct RetargetProfileChoice {
    label: &'static str,
    value: &'static str,
}

const RETARGET_PROFILE_CHOICES: &[RetargetProfileChoice] = &[
    RetargetProfileChoice {
        label: "Auto",
        value: "auto",
    },
    RetargetProfileChoice {
        label: "Humanoid",
        value: "humanoid",
    },
    RetargetProfileChoice {
        label: "Exact",
        value: "exact",
    },
];

fn retarget_profile_label(profile: &str) -> &'static str {
    RETARGET_PROFILE_CHOICES
        .iter()
        .find(|choice| choice.value == profile)
        .map(|choice| choice.label)
        .unwrap_or("Auto")
}

fn on_off(value: bool) -> &'static str {
    if value { "on" } else { "off" }
}

fn update_retarget_camera(
    state: Res<BevyRetargetDesignerState>,
    mut cameras: Query<&mut Transform, With<RetargetDesignerCamera>>,
) {
    if !state.is_changed() {
        return;
    }
    for mut transform in &mut cameras {
        *transform = retarget_camera_transform(state.camera_distance, state.preview_target);
    }
}

fn exit_retarget_on_escape(
    keyboard: Res<ButtonInput<KeyCode>>,
    catalog: Res<BevyRetargetDesignerCatalog>,
    mut app_exit: MessageWriter<AppExit>,
    mut state: ResMut<BevyRetargetDesignerState>,
) {
    if keyboard.just_pressed(KeyCode::Escape) {
        if state.dirty {
            state.save(catalog.as_ref());
        }
        app_exit.write(AppExit::Success);
    }
}

fn despawn_retarget_model_roots(
    model_roots: &Query<Entity, With<RetargetDesignerModelRoot>>,
    commands: &mut Commands,
) {
    for root in model_roots {
        let mut entity = commands.entity(root);
        entity.despawn_children();
        entity.despawn();
    }
}

fn model_caption(
    state: &BevyRetargetDesignerState,
    catalog: &BevyRetargetDesignerCatalog,
) -> String {
    let model = catalog
        .models
        .get(state.selected_model_index)
        .map(|model| model.label.as_str())
        .unwrap_or("<none>");
    format!(
        "Model: {}  |  page {}/{}",
        model,
        state.model_page + 1,
        catalog.page_count()
    )
}

fn model_slot_label(
    state: &BevyRetargetDesignerState,
    catalog: &BevyRetargetDesignerCatalog,
    slot: usize,
) -> String {
    catalog
        .model_index_for_slot(state.model_page, slot)
        .and_then(|index| catalog.models.get(index))
        .map(|model| model.label.clone())
        .unwrap_or_else(|| "-".to_owned())
}

fn panel_frame(width: f32) -> impl Bundle {
    (
        Node {
            width: px(width),
            max_height: Val::Percent(100.0),
            flex_direction: FlexDirection::Column,
            row_gap: px(10.0),
            padding: UiRect::all(px(16.0)),
            border: UiRect::all(px(1.0)),
            overflow: Overflow::scroll_y(),
            scrollbar_width: 10.0,
            ..default()
        },
        BackgroundColor(PANEL_BG),
        BorderColor::all(Color::srgba(0.32, 0.42, 0.5, 0.55)),
        ScrollPosition(Vec2::ZERO),
        RelativeCursorPosition::default(),
        RetargetDesignerScrollPanel,
    )
}

fn section_label(label: &'static str) -> impl Bundle {
    (
        Text::new(label),
        TextFont::from_font_size(14.0),
        TextColor(TEXT_ACCENT),
        Node {
            margin: UiRect::top(px(8.0)),
            ..default()
        },
    )
}

fn value_text(value: RetargetDesignerValue) -> impl Bundle {
    (
        Text::new(""),
        TextFont::from_font_size(12.0),
        TextColor(TEXT_MUTED),
        value,
        RetargetDesignerValueText(value),
    )
}

fn text_bundle(text: impl Into<String>, size: f32, color: Color) -> impl Bundle {
    (
        Text::new(text.into()),
        TextFont::from_font_size(size),
        TextColor(color),
    )
}

fn toolbar_row() -> impl Bundle {
    Node {
        width: Val::Percent(100.0),
        flex_direction: FlexDirection::Row,
        column_gap: px(8.0),
        row_gap: px(8.0),
        flex_wrap: FlexWrap::Wrap,
        ..default()
    }
}

fn button_grid() -> impl Bundle {
    Node {
        width: Val::Percent(100.0),
        flex_direction: FlexDirection::Row,
        column_gap: px(8.0),
        row_gap: px(8.0),
        flex_wrap: FlexWrap::Wrap,
        ..default()
    }
}

fn action_button(
    label: impl Into<String>,
    action: RetargetDesignerAction,
    active: bool,
    width: f32,
) -> impl Bundle {
    (
        Node {
            width: px(width),
            height: px(34.0),
            border: UiRect::all(px(1.0)),
            box_sizing: BoxSizing::BorderBox,
            justify_content: JustifyContent::Center,
            align_items: AlignItems::Center,
            padding: UiRect::horizontal(px(8.0)),
            ..default()
        },
        Button,
        RetargetDesignerButton,
        action,
        BackgroundColor(if active { BUTTON_ACTIVE } else { BUTTON_BG }),
        BorderColor::all(Color::srgba(0.4, 0.5, 0.58, 0.5)),
        RelativeCursorPosition::default(),
        children![(
            Text::new(label.into()),
            TextFont::from_font_size(12.0),
            TextColor(TEXT_MAIN),
            RetargetDesignerButtonLabel,
        )],
    )
}

fn model_slot_button(label: &str, slot: usize) -> impl Bundle {
    (
        Node {
            width: px(170.0),
            height: px(34.0),
            border: UiRect::all(px(1.0)),
            box_sizing: BoxSizing::BorderBox,
            justify_content: JustifyContent::Center,
            align_items: AlignItems::Center,
            padding: UiRect::horizontal(px(8.0)),
            ..default()
        },
        Button,
        RetargetDesignerButton,
        RetargetDesignerAction::ModelSlot(slot),
        BackgroundColor(BUTTON_BG),
        BorderColor::all(Color::srgba(0.4, 0.5, 0.58, 0.5)),
        RelativeCursorPosition::default(),
        children![(
            Text::new(label.to_owned()),
            TextFont::from_font_size(11.0),
            TextColor(TEXT_MAIN),
            RetargetDesignerButtonLabel,
            RetargetDesignerModelSlotLabel(slot),
        )],
    )
}

fn collect_indexed_models(asset_root: &Path, models: &mut Vec<RetargetDesignerModel>) {
    for index_path in [
        asset_root.join("Models").join("models-ext.json"),
        asset_root.join("models").join("models-ext.json"),
        asset_root.join("Models").join("models.json"),
        asset_root.join("models").join("models.json"),
    ] {
        let Ok(raw) = fs::read_to_string(index_path) else {
            continue;
        };
        let Ok(root) = serde_json::from_str::<Value>(&raw) else {
            continue;
        };
        let Some(entries) = root.get("models").and_then(Value::as_array) else {
            continue;
        };
        for entry in entries {
            let Some(name) = entry.get("name").and_then(Value::as_str) else {
                continue;
            };
            let Some(path) = entry.get("path").and_then(Value::as_str) else {
                continue;
            };
            let lower = path.to_ascii_lowercase();
            if !lower.ends_with(".glb") && !lower.ends_with(".gltf") {
                continue;
            }
            let scale = Vec3::new(
                json_f32(entry, "scaleX").unwrap_or(1.0),
                json_f32(entry, "scaleY").unwrap_or(1.0),
                json_f32(entry, "scaleZ").unwrap_or(1.0),
            );
            models.push(RetargetDesignerModel {
                label: name.to_owned(),
                name: Some(name.to_owned()),
                asset_path: path.replace('\\', "/"),
                scale,
                rotation_y_degrees: json_f32(entry, "rotateY").unwrap_or(0.0),
            });
        }
    }
}

fn collect_gltf_models(root: &Path, dir: &Path, models: &mut Vec<RetargetDesignerModel>) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            collect_gltf_models(root, &path, models);
            continue;
        }
        let Some(extension) = path.extension().and_then(|value| value.to_str()) else {
            continue;
        };
        if !extension.eq_ignore_ascii_case("gltf") && !extension.eq_ignore_ascii_case("glb") {
            continue;
        }
        let Ok(relative) = path.strip_prefix(root) else {
            continue;
        };
        let asset_path = relative
            .components()
            .map(|component| component.as_os_str().to_string_lossy())
            .collect::<Vec<_>>()
            .join("/");
        models.push(RetargetDesignerModel {
            label: model_asset_label(relative),
            name: None,
            asset_path,
            scale: Vec3::ONE,
            rotation_y_degrees: 0.0,
        });
    }
}

fn model_asset_label(relative: &Path) -> String {
    let file_stem = relative
        .file_stem()
        .and_then(|value| value.to_str())
        .unwrap_or("model");
    let parent = relative
        .parent()
        .and_then(|path| path.file_name())
        .and_then(|value| value.to_str());
    match parent {
        Some(parent) if !parent.eq_ignore_ascii_case(file_stem) => {
            format!("{parent}/{file_stem}")
        }
        _ => file_stem.to_owned(),
    }
}

fn json_f32(value: &Value, key: &str) -> Option<f32> {
    value
        .get(key)
        .and_then(Value::as_f64)
        .map(|value| value as f32)
}

fn wrap_degrees(value: f32) -> f32 {
    if !value.is_finite() {
        return 0.0;
    }
    let mut wrapped = value % 360.0;
    if wrapped > 180.0 {
        wrapped -= 360.0;
    } else if wrapped < -180.0 {
        wrapped += 360.0;
    }
    wrapped
}

fn animation_index_path(asset_root: &Path) -> PathBuf {
    [
        asset_root.join("animations").join("animations-ext.json"),
        asset_root.join("Animations").join("animations-ext.json"),
    ]
    .into_iter()
    .find(|path| path.is_file())
    .unwrap_or_else(|| asset_root.join("animations").join("animations-ext.json"))
}

fn save_retarget_options(
    index_path: &Path,
    animation_name: &str,
    options: &scenemax_assets::AnimationRetargetOptions,
    baked: Option<&RetargetBakedIndexEntry>,
) -> anyhow::Result<()> {
    let raw = fs::read_to_string(index_path)?;
    let mut root: Value = serde_json::from_str(&raw)?;
    let animations = root
        .get_mut("animations")
        .and_then(Value::as_array_mut)
        .ok_or_else(|| anyhow::anyhow!("animation index has no animations array"))?;
    let entry = animations
        .iter_mut()
        .find(|entry| {
            entry
                .get("name")
                .and_then(Value::as_str)
                .is_some_and(|name| name.eq_ignore_ascii_case(animation_name))
        })
        .ok_or_else(|| anyhow::anyhow!("animation '{animation_name}' was not found"))?;
    entry["bevyRetarget"] = json!({
        "profile": options.profile,
        "skipTopAnimatedTargets": options.skip_top_animated_targets,
        "excludeBones": options.exclude_bones,
        "rootBone": options.root_bone,
        "scaleBaseBone": options.scale_base_bone,
        "removeUnimportantTranslationTracks": options.remove_unimportant_translation_tracks,
        "removeMotionTranslationTracks": options.remove_motion_translation_tracks,
        "removeMotionRotationTracks": options.remove_motion_rotation_tracks,
        "normalizeMotionScale": options.normalize_motion_scale,
        "visualTranslation": {
            "x": options.visual_translation[0],
            "y": options.visual_translation[1],
            "z": options.visual_translation[2],
        },
        "visualRotationDegrees": {
            "x": options.visual_rotation_degrees[0],
            "y": options.visual_rotation_degrees[1],
            "z": options.visual_rotation_degrees[2],
        },
        "lockedTranslationAxes": {
            "x": options.locked_translation_axes[0],
            "y": options.locked_translation_axes[1],
            "z": options.locked_translation_axes[2],
        },
    });
    if let Some(baked) = baked {
        upsert_baked_retarget_entry(entry, baked);
    }
    fs::write(index_path, serde_json::to_string_pretty(&root)?)?;
    Ok(())
}

fn upsert_baked_retarget_entry(entry: &mut Value, baked: &RetargetBakedIndexEntry) {
    if !entry.get("bevyBakedRetargets").is_some_and(Value::is_array) {
        entry["bevyBakedRetargets"] = json!([]);
    }
    let Some(retargets) = entry
        .get_mut("bevyBakedRetargets")
        .and_then(Value::as_array_mut)
    else {
        return;
    };
    let value = json!({
        "model": baked.model,
        "path": baked.path,
        "clipName": baked.clip_name,
        "visualRotationBaked": baked.visual_rotation_baked,
    });
    if let Some(existing) = retargets.iter_mut().find(|existing| {
        existing
            .get("model")
            .and_then(Value::as_str)
            .is_some_and(|model| model.eq_ignore_ascii_case(&baked.model))
    }) {
        *existing = value;
    } else {
        retargets.push(value);
    }
}
