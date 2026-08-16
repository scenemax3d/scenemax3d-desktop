use super::*;
use bevy::{
    gltf::GltfAssetLabel, ui::RelativeCursorPosition, world_serialization::WorldInstanceReady,
};

#[derive(Debug, Clone)]
pub struct BevyShaderDesignerLaunch {
    pub shader: PathBuf,
    pub project_root: Option<PathBuf>,
}

const PANEL_BG: Color = Color::srgba(0.045, 0.055, 0.072, 0.94);
const BUTTON_BG: Color = Color::srgb(0.105, 0.125, 0.150);
const BUTTON_HOVER: Color = Color::srgb(0.145, 0.175, 0.210);
const BUTTON_ACTIVE: Color = Color::srgb(0.140, 0.400, 0.480);
const BUTTON_ACTIVE_HOVER: Color = Color::srgb(0.170, 0.500, 0.590);
const BUTTON_DANGER: Color = Color::srgb(0.410, 0.145, 0.135);
const TEXT_MAIN: Color = Color::srgb(0.935, 0.955, 0.975);
const TEXT_MUTED: Color = Color::srgb(0.610, 0.690, 0.770);
const TEXT_ACCENT: Color = Color::srgb(0.560, 0.900, 0.940);
const MODEL_PAGE_SIZE: usize = 8;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
enum DesignerBlock {
    Tint,
    Glow,
    Pulse,
    Dissolve,
    RimLight,
    ScrollUv,
    Flicker,
    WaterWaves,
    HologramLines,
    ToonRamp,
}

impl DesignerBlock {
    const ALL: [DesignerBlock; 10] = [
        DesignerBlock::Tint,
        DesignerBlock::Glow,
        DesignerBlock::Pulse,
        DesignerBlock::Dissolve,
        DesignerBlock::RimLight,
        DesignerBlock::ScrollUv,
        DesignerBlock::Flicker,
        DesignerBlock::WaterWaves,
        DesignerBlock::HologramLines,
        DesignerBlock::ToonRamp,
    ];

    fn key(self) -> &'static str {
        match self {
            DesignerBlock::Tint => "TINT",
            DesignerBlock::Glow => "GLOW",
            DesignerBlock::Pulse => "PULSE",
            DesignerBlock::Dissolve => "DISSOLVE",
            DesignerBlock::RimLight => "RIM_LIGHT",
            DesignerBlock::ScrollUv => "SCROLL_UV",
            DesignerBlock::Flicker => "FLICKER",
            DesignerBlock::WaterWaves => "WATER_WAVES",
            DesignerBlock::HologramLines => "HOLOGRAM_LINES",
            DesignerBlock::ToonRamp => "TOON_RAMP",
        }
    }

    fn label(self) -> &'static str {
        match self {
            DesignerBlock::Tint => "Tint",
            DesignerBlock::Glow => "Glow",
            DesignerBlock::Pulse => "Pulse",
            DesignerBlock::Dissolve => "Dissolve",
            DesignerBlock::RimLight => "Rim Light",
            DesignerBlock::ScrollUv => "Scroll UV",
            DesignerBlock::Flicker => "Flicker",
            DesignerBlock::WaterWaves => "Water Waves",
            DesignerBlock::HologramLines => "Hologram Lines",
            DesignerBlock::ToonRamp => "Toon Ramp",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DesignerTemplate {
    TextureTint,
    UiSoftGlow,
    UiNeonScan,
    GlowPulse,
    Dissolve,
    HologramLite,
    WaterLite,
}

impl DesignerTemplate {
    const ALL: [DesignerTemplate; 7] = [
        DesignerTemplate::TextureTint,
        DesignerTemplate::UiSoftGlow,
        DesignerTemplate::UiNeonScan,
        DesignerTemplate::GlowPulse,
        DesignerTemplate::Dissolve,
        DesignerTemplate::HologramLite,
        DesignerTemplate::WaterLite,
    ];

    fn key(self) -> &'static str {
        match self {
            DesignerTemplate::TextureTint => "TEXTURE_TINT",
            DesignerTemplate::UiSoftGlow => "UI_SOFT_GLOW",
            DesignerTemplate::UiNeonScan => "UI_NEON_SCAN",
            DesignerTemplate::GlowPulse => "GLOW_PULSE",
            DesignerTemplate::Dissolve => "DISSOLVE",
            DesignerTemplate::HologramLite => "HOLOGRAM_LITE",
            DesignerTemplate::WaterLite => "WATER_LITE",
        }
    }

    fn label(self) -> &'static str {
        match self {
            DesignerTemplate::TextureTint => "Texture + Tint",
            DesignerTemplate::UiSoftGlow => "UI Soft Glow",
            DesignerTemplate::UiNeonScan => "UI Neon Scan",
            DesignerTemplate::GlowPulse => "Glow Pulse",
            DesignerTemplate::Dissolve => "Dissolve",
            DesignerTemplate::HologramLite => "Hologram Lite",
            DesignerTemplate::WaterLite => "Water Lite",
        }
    }

    fn from_key(value: &str) -> Self {
        Self::ALL
            .into_iter()
            .find(|template| template.key().eq_ignore_ascii_case(value))
            .unwrap_or(DesignerTemplate::TextureTint)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DesignerTarget {
    Box,
    Sphere,
    Sprite,
}

impl DesignerTarget {
    const ALL: [DesignerTarget; 3] = [
        DesignerTarget::Box,
        DesignerTarget::Sphere,
        DesignerTarget::Sprite,
    ];

    fn from_key(value: &str) -> Self {
        match value {
            value if value.eq_ignore_ascii_case("SPHERE") => DesignerTarget::Sphere,
            value if value.eq_ignore_ascii_case("SPRITE") => DesignerTarget::Sprite,
            _ => DesignerTarget::Box,
        }
    }

    fn key(self) -> &'static str {
        match self {
            DesignerTarget::Box => "BOX",
            DesignerTarget::Sphere => "SPHERE",
            DesignerTarget::Sprite => "SPRITE",
        }
    }

    fn label(self) -> &'static str {
        match self {
            DesignerTarget::Box => "Box",
            DesignerTarget::Sphere => "Sphere",
            DesignerTarget::Sprite => "Sprite Quad",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DesignerParam {
    Red,
    Green,
    Blue,
    Glow,
    Transparency,
    Scroll,
    Pulse,
    Edge,
    Scale,
}

impl DesignerParam {
    const ALL: [DesignerParam; 9] = [
        DesignerParam::Red,
        DesignerParam::Green,
        DesignerParam::Blue,
        DesignerParam::Glow,
        DesignerParam::Transparency,
        DesignerParam::Scroll,
        DesignerParam::Pulse,
        DesignerParam::Edge,
        DesignerParam::Scale,
    ];

    fn label(self) -> &'static str {
        match self {
            DesignerParam::Red => "Red",
            DesignerParam::Green => "Green",
            DesignerParam::Blue => "Blue",
            DesignerParam::Glow => "Glow",
            DesignerParam::Transparency => "Transparency",
            DesignerParam::Scroll => "UV Scroll",
            DesignerParam::Pulse => "Pulse",
            DesignerParam::Edge => "Edge",
            DesignerParam::Scale => "Preview Scale",
        }
    }

    fn range(self) -> (f32, f32, f32) {
        match self {
            DesignerParam::Red | DesignerParam::Green | DesignerParam::Blue => (0.0, 1.0, 0.04),
            DesignerParam::Glow => (0.0, 5.0, 0.10),
            DesignerParam::Transparency => (0.0, 1.0, 0.04),
            DesignerParam::Scroll | DesignerParam::Pulse => (0.0, 5.0, 0.10),
            DesignerParam::Edge => (0.01, 1.0, 0.04),
            DesignerParam::Scale => (0.1, 10.0, 0.10),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DesignerPalette {
    Warm,
    Aqua,
    Violet,
    Ember,
    Leaf,
}

impl DesignerPalette {
    const ALL: [DesignerPalette; 5] = [
        DesignerPalette::Warm,
        DesignerPalette::Aqua,
        DesignerPalette::Violet,
        DesignerPalette::Ember,
        DesignerPalette::Leaf,
    ];

    fn label(self) -> &'static str {
        match self {
            DesignerPalette::Warm => "Warm",
            DesignerPalette::Aqua => "Aqua",
            DesignerPalette::Violet => "Violet",
            DesignerPalette::Ember => "Ember",
            DesignerPalette::Leaf => "Leaf",
        }
    }

    fn color(self) -> [f32; 4] {
        match self {
            DesignerPalette::Warm => [1.0, 0.84, 0.62, 1.0],
            DesignerPalette::Aqua => [0.25, 0.93, 1.0, 1.0],
            DesignerPalette::Violet => [0.72, 0.45, 1.0, 1.0],
            DesignerPalette::Ember => [1.0, 0.36, 0.12, 1.0],
            DesignerPalette::Leaf => [0.48, 0.92, 0.48, 1.0],
        }
    }

    fn bevy_color(self) -> Color {
        let [r, g, b, a] = self.color();
        Color::srgba(r, g, b, a)
    }
}

#[derive(Debug, Clone, Copy, Component)]
enum DesignerAction {
    Template(DesignerTemplate),
    Target(DesignerTarget),
    ModelSlot(usize),
    ModelPage(i32),
    ClearModel,
    ToggleBlock(DesignerBlock),
    Adjust(DesignerParam, f32),
    Palette(DesignerPalette),
    ToggleOriginalTexture,
    Save,
    Reset,
    Close,
}

#[derive(Debug, Resource)]
struct BevyShaderDesignerState {
    shader_path: PathBuf,
    project_root: Option<PathBuf>,
    template: DesignerTemplate,
    target: DesignerTarget,
    preview_model_index: Option<usize>,
    preview_model_asset_path: Option<String>,
    model_page: usize,
    selected_block: usize,
    blocks: HashSet<DesignerBlock>,
    main_color: [f32; 4],
    glow_strength: f32,
    pulse_speed: f32,
    transparency: f32,
    edge_width: f32,
    scroll_speed: f32,
    preview_scale: f32,
    use_original_texture: bool,
    dirty: bool,
    status: String,
    elapsed: f32,
}

#[derive(Debug, Clone)]
struct DesignerModelAsset {
    label: String,
    asset_path: String,
}

#[derive(Debug, Resource)]
struct BevyShaderDesignerAssetCatalog {
    asset_root: Option<PathBuf>,
    models: Vec<DesignerModelAsset>,
}

impl BevyShaderDesignerAssetCatalog {
    fn discover(project_root: Option<&Path>) -> Self {
        let asset_root = project_root
            .map(|root| root.join("resources"))
            .filter(|path| path.is_dir());
        let mut models = Vec::new();
        if let Some(root) = asset_root.as_ref() {
            collect_gltf_models(root, root, &mut models);
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
}

fn collect_gltf_models(root: &Path, dir: &Path, models: &mut Vec<DesignerModelAsset>) {
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
        models.push(DesignerModelAsset {
            label: model_asset_label(relative),
            asset_path,
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

#[derive(Debug, Component)]
struct ShaderPreviewMesh;

#[derive(Debug, Component)]
struct ShaderPreviewModelRoot;

#[derive(Debug, Component)]
struct ShaderPreviewModelMaterial;

#[derive(Debug, Component)]
struct ShaderPreviewModelOriginalMaterial(Handle<StandardMaterial>);

#[derive(Debug, Component)]
struct ShaderDesignerUiRoot;

#[derive(Debug, Component)]
struct ShaderDesignerStatusText;

#[derive(Debug, Component)]
struct ShaderDesignerActionButton;

#[derive(Debug, Component)]
struct ShaderDesignerButtonLabel;

#[derive(Debug, Component)]
struct ShaderDesignerModelSlotLabel(usize);

#[derive(Debug, Component)]
struct ShaderDesignerModelPageText;

#[derive(Debug, Component)]
struct ShaderDesignerValueText(DesignerParam);

#[derive(Debug, Component)]
struct ShaderDesignerValueFill(DesignerParam);

#[derive(Debug, Component)]
struct ShaderDesignerSliderTrack(DesignerParam);

#[derive(Debug, Component)]
struct ShaderDesignerSliderThumb(DesignerParam);

pub fn run_bevy_shader_designer(launch: BevyShaderDesignerLaunch) {
    let catalog = BevyShaderDesignerAssetCatalog::discover(launch.project_root.as_deref());
    let asset_file_path = catalog
        .asset_root
        .as_ref()
        .map(|path| path.to_string_lossy().to_string())
        .unwrap_or_else(|| "assets".to_owned());
    let state = BevyShaderDesignerState::load(launch, &catalog);
    App::new()
        .insert_resource(ClearColor(Color::srgb(0.025, 0.035, 0.05)))
        .insert_resource(state)
        .insert_resource(catalog)
        .insert_resource(WinitSettings::continuous())
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
                        title: "SceneMax Bevy Shader Designer".to_owned(),
                        present_mode: PresentMode::AutoVsync,
                        resolution: WindowResolution::new(1440, 900)
                            .with_scale_factor_override(1.0),
                        ..default()
                    }),
                    ..default()
                }),
        )
        .add_systems(Startup, setup_bevy_shader_designer)
        .add_systems(
            Update,
            (
                update_designer_keyboard,
                handle_designer_buttons,
                handle_designer_sliders,
                animate_preview_mesh,
                draw_designer_gizmos,
                apply_designer_material,
                update_designer_ui_state,
                exit_designer_on_escape,
            )
                .chain(),
        )
        .add_observer(apply_material_to_loaded_preview_model)
        .run();
}

impl BevyShaderDesignerState {
    fn load(launch: BevyShaderDesignerLaunch, catalog: &BevyShaderDesignerAssetCatalog) -> Self {
        let source = fs::read_to_string(&launch.shader).ok();
        let value = source
            .as_deref()
            .and_then(|source| serde_json::from_str::<serde_json::Value>(source).ok())
            .unwrap_or_else(default_shader_json);
        let template = value
            .get("template")
            .and_then(serde_json::Value::as_str)
            .map(DesignerTemplate::from_key)
            .unwrap_or(DesignerTemplate::TextureTint);
        let target = value
            .get("previewTarget")
            .and_then(serde_json::Value::as_str)
            .map(DesignerTarget::from_key)
            .unwrap_or(DesignerTarget::Box);
        let blocks = value
            .get("blocks")
            .and_then(serde_json::Value::as_array)
            .map(|blocks| {
                blocks
                    .iter()
                    .filter_map(serde_json::Value::as_str)
                    .filter_map(block_from_key)
                    .collect::<HashSet<_>>()
            })
            .filter(|blocks| !blocks.is_empty())
            .unwrap_or_else(|| HashSet::from([DesignerBlock::Tint]));
        let preview_model_name = value
            .get("previewModelName")
            .and_then(serde_json::Value::as_str)
            .filter(|value| !value.trim().is_empty());
        let preview_model_index = preview_model_name.and_then(|name| {
            catalog
                .models
                .iter()
                .position(|model| model.asset_path.eq_ignore_ascii_case(name))
        });
        Self {
            shader_path: launch.shader,
            project_root: launch.project_root,
            template,
            target,
            preview_model_index,
            preview_model_asset_path: preview_model_index
                .and_then(|index| catalog.models.get(index))
                .map(|model| model.asset_path.clone()),
            model_page: preview_model_index
                .map(|index| index / MODEL_PAGE_SIZE)
                .unwrap_or(0),
            selected_block: 0,
            blocks,
            main_color: rgba_array(value.get("mainColor"), [1.0, 0.85, 0.72, 1.0]),
            glow_strength: f32_json(&value, "glowStrength", 0.15),
            pulse_speed: f32_json(&value, "pulseSpeed", 0.55),
            transparency: f32_json(&value, "transparency", 0.05),
            edge_width: f32_json(&value, "edgeWidth", 0.15),
            scroll_speed: f32_json(&value, "scrollSpeed", 0.35),
            preview_scale: f32_json(&value, "previewScale", 1.0).max(0.1),
            use_original_texture: bool_json(&value, "useOriginalTexture", true),
            dirty: false,
            status: "Ready".to_owned(),
            elapsed: 0.0,
        }
    }

    fn save(&mut self) {
        let mut root = serde_json::Map::new();
        root.insert("version".to_owned(), serde_json::json!(1));
        root.insert(
            "template".to_owned(),
            serde_json::json!(self.template.key()),
        );
        root.insert(
            "previewTarget".to_owned(),
            serde_json::json!(self.target.key()),
        );
        root.insert("mainColor".to_owned(), serde_json::json!(self.main_color));
        root.insert(
            "glowStrength".to_owned(),
            serde_json::json!(self.glow_strength),
        );
        root.insert("pulseSpeed".to_owned(), serde_json::json!(self.pulse_speed));
        root.insert(
            "transparency".to_owned(),
            serde_json::json!(self.transparency),
        );
        root.insert("edgeWidth".to_owned(), serde_json::json!(self.edge_width));
        root.insert(
            "scrollSpeed".to_owned(),
            serde_json::json!(self.scroll_speed),
        );
        root.insert(
            "previewScale".to_owned(),
            serde_json::json!(self.preview_scale),
        );
        root.insert("texture".to_owned(), serde_json::json!(""));
        root.insert(
            "useOriginalTexture".to_owned(),
            serde_json::json!(self.use_original_texture),
        );
        root.insert(
            "previewModelName".to_owned(),
            serde_json::json!(self.preview_model_asset_path.as_deref().unwrap_or("")),
        );
        let mut blocks = self
            .blocks
            .iter()
            .map(|block| block.key())
            .collect::<Vec<_>>();
        blocks.sort();
        root.insert("blocks".to_owned(), serde_json::json!(blocks));
        let value = serde_json::Value::Object(root);
        match serde_json::to_string_pretty(&value)
            .ok()
            .and_then(|source| fs::write(&self.shader_path, source).ok())
        {
            Some(()) => {
                self.dirty = false;
                self.status = format!("Saved {}", self.shader_path.display());
            }
            None => {
                self.status = format!("Save failed for {}", self.shader_path.display());
            }
        }
    }

    fn mark_dirty(&mut self) {
        self.dirty = true;
        self.status = "Unsaved changes".to_owned();
    }
}

fn setup_bevy_shader_designer(
    mut commands: Commands,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    asset_server: Res<AssetServer>,
    state: Res<BevyShaderDesignerState>,
    catalog: Res<BevyShaderDesignerAssetCatalog>,
) {
    commands.spawn((
        Camera3d::default(),
        Transform::from_xyz(-3.2, 2.6, 6.2).looking_at(Vec3::ZERO, Vec3::Y),
    ));
    commands.spawn((
        DirectionalLight {
            illuminance: 18_000.0,
            shadow_maps_enabled: true,
            ..default()
        },
        Transform::from_xyz(-4.5, 7.0, 4.0).looking_at(Vec3::ZERO, Vec3::Y),
    ));
    commands.insert_resource(GlobalAmbientLight {
        color: Color::srgb(0.75, 0.82, 1.0),
        brightness: 260.0,
        ..default()
    });

    let mesh = preview_mesh(&mut meshes, state.target);
    let material = materials.add(state.standard_material(None));
    commands.spawn((
        Mesh3d(mesh),
        MeshMaterial3d(material),
        Transform::from_scale(Vec3::splat(state.preview_scale)),
        if state.preview_model_index.is_some() {
            Visibility::Hidden
        } else {
            Visibility::Inherited
        },
        ShaderPreviewMesh,
    ));
    if let Some(model_index) = state.preview_model_index {
        spawn_preview_model(
            &mut commands,
            &asset_server,
            catalog.as_ref(),
            model_index,
            state.preview_scale,
        );
    }
    spawn_shader_designer_ui(&mut commands, &state, &catalog);
}

fn preview_mesh(meshes: &mut Assets<Mesh>, target: DesignerTarget) -> Handle<Mesh> {
    match target {
        DesignerTarget::Box => meshes.add(Cuboid::from_size(Vec3::splat(1.75))),
        DesignerTarget::Sphere => meshes.add(Sphere::new(1.05).mesh().uv(48, 24)),
        DesignerTarget::Sprite => meshes.add(Rectangle::new(2.3, 1.35)),
    }
}

fn spawn_shader_designer_ui(
    commands: &mut Commands,
    state: &BevyShaderDesignerState,
    catalog: &BevyShaderDesignerAssetCatalog,
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
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                left: px(0.0),
                top: px(0.0),
                width: Val::Percent(100.0),
                height: Val::Percent(100.0),
                flex_direction: FlexDirection::Row,
                justify_content: JustifyContent::SpaceBetween,
                padding: UiRect::all(px(18.0)),
                ..default()
            },
            ShaderDesignerUiRoot,
        ))
        .with_children(|root| {
            root.spawn(panel_node(340.0)).with_children(|panel| {
                panel.spawn(text_bundle("Bevy Shader Designer", 24.0, TEXT_MAIN));
                panel.spawn(text_bundle(
                    document_caption(state),
                    12.0,
                    TEXT_MUTED,
                ));
                panel.spawn(section_label("Templates"));
                panel.spawn(button_grid()).with_children(|grid| {
                    for template in DesignerTemplate::ALL {
                        grid.spawn(action_button(
                            template.label(),
                            DesignerAction::Template(template),
                            state.template == template,
                            144.0,
                        ));
                    }
                });
                panel.spawn(section_label("Preview Target"));
                panel.spawn(button_grid()).with_children(|grid| {
                    for target in DesignerTarget::ALL {
                        grid.spawn(action_button(
                            target.label(),
                            DesignerAction::Target(target),
                            state.target == target,
                            144.0,
                        ));
                    }
                });
                panel.spawn(section_label("GLTF Preview"));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button("Clear", DesignerAction::ClearModel, false, 72.0));
                    row.spawn(action_button("<", DesignerAction::ModelPage(-1), false, 36.0));
                    row.spawn((
                        Text::new(model_page_text(state, catalog)),
                        TextFont::from_font_size(12.0),
                        TextColor(TEXT_MUTED),
                        Node {
                            width: px(108.0),
                            height: px(34.0),
                            justify_content: JustifyContent::Center,
                            align_items: AlignItems::Center,
                            ..default()
                        },
                        ShaderDesignerModelPageText,
                    ));
                    row.spawn(action_button(">", DesignerAction::ModelPage(1), false, 36.0));
                });
                panel.spawn(button_grid()).with_children(|grid| {
                    for slot in 0..MODEL_PAGE_SIZE {
                        let label = model_slot_label(state, catalog, slot);
                        grid.spawn(model_slot_button(label, slot));
                    }
                });
                panel.spawn(section_label("Shader Blocks"));
                panel.spawn(button_grid()).with_children(|grid| {
                    for block in DesignerBlock::ALL {
                        grid.spawn(action_button(
                            block.label(),
                            DesignerAction::ToggleBlock(block),
                            state.blocks.contains(&block),
                            144.0,
                        ));
                    }
                });
            });
            root.spawn(panel_node(390.0)).with_children(|panel| {
                panel.spawn(section_label("Color Palette"));
                panel.spawn(button_grid()).with_children(|grid| {
                    for palette in DesignerPalette::ALL {
                        grid.spawn(color_button(
                            palette.label(),
                            DesignerAction::Palette(palette),
                            palette.bevy_color(),
                            state.main_color == palette.color(),
                        ));
                    }
                });
                panel.spawn(section_label("Parameters"));
                for param in DesignerParam::ALL {
                    panel.spawn(param_control(param, state));
                }
                panel.spawn(section_label("Options"));
                panel.spawn(action_button(
                    "Original Texture",
                    DesignerAction::ToggleOriginalTexture,
                    state.use_original_texture,
                    174.0,
                ));
                panel.spawn(toolbar_row()).with_children(|row| {
                    row.spawn(action_button("Save", DesignerAction::Save, false, 102.0));
                    row.spawn(action_button("Reset", DesignerAction::Reset, false, 102.0));
                    row.spawn(danger_button("Close", DesignerAction::Close, 102.0));
                });
                panel.spawn((
                    Text::new(status_text(state)),
                    TextFont::from_font_size(12.0),
                    TextColor(Color::srgb(0.72, 0.90, 0.68)),
                    ShaderDesignerStatusText,
                ));
            });
        });
}

fn panel_node(width: f32) -> impl Bundle {
    (
        Node {
            width: px(width),
            flex_direction: FlexDirection::Column,
            row_gap: px(11.0),
            padding: UiRect::all(px(16.0)),
            align_self: AlignSelf::Stretch,
            ..default()
        },
        BackgroundColor(PANEL_BG),
    )
}

fn text_bundle(value: impl Into<String>, size: f32, color: Color) -> impl Bundle {
    (
        Text::new(value.into()),
        TextFont::from_font_size(size),
        TextColor(color),
    )
}

fn section_label(value: &'static str) -> impl Bundle {
    (
        Text::new(value),
        TextFont::from_font_size(12.0),
        TextColor(TEXT_ACCENT),
        Node {
            margin: UiRect::top(px(8.0)),
            ..default()
        },
    )
}

fn button_grid() -> impl Bundle {
    Node {
        flex_direction: FlexDirection::Row,
        flex_wrap: FlexWrap::Wrap,
        column_gap: px(8.0),
        row_gap: px(8.0),
        ..default()
    }
}

fn toolbar_row() -> impl Bundle {
    Node {
        flex_direction: FlexDirection::Row,
        column_gap: px(8.0),
        margin: UiRect::top(px(4.0)),
        ..default()
    }
}

fn action_button(
    label: &'static str,
    action: DesignerAction,
    active: bool,
    width: f32,
) -> impl Bundle {
    (
        button_node(width, 34.0),
        Button,
        ShaderDesignerActionButton,
        action,
        BackgroundColor(if active { BUTTON_ACTIVE } else { BUTTON_BG }),
        children![(
            Text::new(label),
            TextFont::from_font_size(13.0),
            TextColor(TEXT_MAIN),
            ShaderDesignerButtonLabel,
        )],
    )
}

fn danger_button(label: &'static str, action: DesignerAction, width: f32) -> impl Bundle {
    (
        button_node(width, 34.0),
        Button,
        ShaderDesignerActionButton,
        action,
        BackgroundColor(BUTTON_DANGER),
        children![(
            Text::new(label),
            TextFont::from_font_size(13.0),
            TextColor(TEXT_MAIN),
            ShaderDesignerButtonLabel,
        )],
    )
}

fn color_button(
    label: &'static str,
    action: DesignerAction,
    swatch: Color,
    active: bool,
) -> impl Bundle {
    (
        button_node(108.0, 34.0),
        Button,
        ShaderDesignerActionButton,
        action,
        BackgroundColor(if active { BUTTON_ACTIVE } else { BUTTON_BG }),
        children![
            (
                Node {
                    width: px(16.0),
                    height: px(16.0),
                    margin: UiRect::right(px(8.0)),
                    ..default()
                },
                BackgroundColor(swatch),
            ),
            (
                Text::new(label),
                TextFont::from_font_size(12.0),
                TextColor(TEXT_MAIN),
                ShaderDesignerButtonLabel,
            )
        ],
    )
}

fn model_slot_button(label: String, slot: usize) -> impl Bundle {
    (
        button_node(144.0, 34.0),
        Button,
        ShaderDesignerActionButton,
        DesignerAction::ModelSlot(slot),
        BackgroundColor(BUTTON_BG),
        children![(
            Text::new(label),
            TextFont::from_font_size(11.0),
            TextColor(TEXT_MAIN),
            ShaderDesignerModelSlotLabel(slot),
        )],
    )
}

fn button_node(width: f32, height: f32) -> Node {
    Node {
        width: px(width),
        height: px(height),
        border: UiRect::all(px(1.0)),
        box_sizing: BoxSizing::BorderBox,
        justify_content: JustifyContent::Center,
        align_items: AlignItems::Center,
        flex_direction: FlexDirection::Row,
        padding: UiRect::axes(px(10.0), px(0.0)),
        ..default()
    }
}

fn model_slot_label(
    state: &BevyShaderDesignerState,
    catalog: &BevyShaderDesignerAssetCatalog,
    slot: usize,
) -> String {
    catalog
        .model_index_for_slot(state.model_page, slot)
        .and_then(|index| catalog.models.get(index))
        .map(|model| model.label.clone())
        .unwrap_or_else(|| "-".to_owned())
}

fn model_page_text(
    state: &BevyShaderDesignerState,
    catalog: &BevyShaderDesignerAssetCatalog,
) -> String {
    if catalog.models.is_empty() {
        "No models".to_owned()
    } else {
        format!(
            "{}/{}  {}",
            state.model_page + 1,
            catalog.page_count(),
            catalog.models.len()
        )
    }
}

fn param_control(param: DesignerParam, state: &BevyShaderDesignerState) -> impl Bundle {
    let (min, max, step) = param.range();
    let value = state.param_value(param);
    let pct = normalized_percent(value, min, max);
    (
        Node {
            flex_direction: FlexDirection::Column,
            row_gap: px(5.0),
            ..default()
        },
        children![
            (
                Node {
                    flex_direction: FlexDirection::Row,
                    justify_content: JustifyContent::SpaceBetween,
                    align_items: AlignItems::Center,
                    ..default()
                },
                children![
                    text_bundle(param.label(), 12.0, TEXT_MAIN),
                    (
                        Text::new(format!("{value:.2}")),
                        TextFont::from_font_size(12.0),
                        TextColor(TEXT_MUTED),
                        ShaderDesignerValueText(param),
                    )
                ]
            ),
            (
                Node {
                    flex_direction: FlexDirection::Row,
                    align_items: AlignItems::Center,
                    column_gap: px(7.0),
                    ..default()
                },
                children![
                    action_button("-", DesignerAction::Adjust(param, -step), false, 34.0),
                    (
                        Node {
                            width: px(268.0),
                            height: px(18.0),
                            border: UiRect::all(px(1.0)),
                            box_sizing: BoxSizing::BorderBox,
                            overflow: Overflow::clip(),
                            ..default()
                        },
                        Button,
                        ShaderDesignerSliderTrack(param),
                        RelativeCursorPosition::default(),
                        BackgroundColor(Color::srgb(0.090, 0.105, 0.125)),
                        children![
                            (
                                Node {
                                    position_type: PositionType::Absolute,
                                    left: px(0.0),
                                    top: px(0.0),
                                    width: Val::Percent(pct),
                                    height: Val::Percent(100.0),
                                    ..default()
                                },
                                BackgroundColor(Color::srgb(0.260, 0.780, 0.840)),
                                ShaderDesignerValueFill(param),
                            ),
                            (
                                Node {
                                    position_type: PositionType::Absolute,
                                    left: Val::Percent(pct),
                                    top: px(0.0),
                                    width: px(8.0),
                                    height: Val::Percent(100.0),
                                    ..default()
                                },
                                BackgroundColor(Color::srgb(0.930, 0.985, 1.0)),
                                ShaderDesignerSliderThumb(param),
                            )
                        ]
                    ),
                    action_button("+", DesignerAction::Adjust(param, step), false, 34.0),
                ]
            )
        ],
    )
}

fn document_caption(state: &BevyShaderDesignerState) -> String {
    let document = state
        .shader_path
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("<shader>");
    let project = state
        .project_root
        .as_ref()
        .and_then(|path| path.file_name())
        .and_then(|value| value.to_str())
        .unwrap_or("<no project>");
    format!("{document}\n{project}")
}

fn update_designer_keyboard(
    mut commands: Commands,
    input: Res<ButtonInput<KeyCode>>,
    mut state: ResMut<BevyShaderDesignerState>,
    mut mesh_query: Query<&mut Mesh3d, With<ShaderPreviewMesh>>,
    mut primitive_visibility: Query<&mut Visibility, With<ShaderPreviewMesh>>,
    model_roots: Query<Entity, With<ShaderPreviewModelRoot>>,
    mut meshes: ResMut<Assets<Mesh>>,
) {
    if input.just_pressed(KeyCode::KeyS)
        && (input.pressed(KeyCode::ControlLeft) || input.pressed(KeyCode::ControlRight))
    {
        state.save();
    }
    if input.just_pressed(KeyCode::KeyT) {
        let index = DesignerTemplate::ALL
            .iter()
            .position(|template| *template == state.template)
            .unwrap_or(0);
        let next = DesignerTemplate::ALL[(index + 1) % DesignerTemplate::ALL.len()];
        apply_template(&mut state, next);
        let target = state.target;
        update_preview_mesh(target, &mut mesh_query, &mut meshes);
    }
    if input.just_pressed(KeyCode::Tab) {
        state.selected_block = (state.selected_block + 1) % DesignerBlock::ALL.len();
    }
    if input.just_pressed(KeyCode::Space) {
        let block = DesignerBlock::ALL[state.selected_block];
        if state.blocks.contains(&block) {
            state.blocks.remove(&block);
        } else {
            state.blocks.insert(block);
        }
        state.mark_dirty();
    }
    if input.just_pressed(KeyCode::KeyP) {
        let target = match state.target {
            DesignerTarget::Box => DesignerTarget::Sphere,
            DesignerTarget::Sphere => DesignerTarget::Sprite,
            DesignerTarget::Sprite => DesignerTarget::Box,
        };
        set_preview_target(
            &mut state,
            target,
            &mut mesh_query,
            &mut primitive_visibility,
            &model_roots,
            &mut commands,
            &mut meshes,
        );
    }

    let color_step = if input.pressed(KeyCode::ShiftLeft) || input.pressed(KeyCode::ShiftRight) {
        0.05
    } else {
        0.015
    };
    adjust_if_pressed(&input, KeyCode::Digit1, &mut state, DesignerParam::Red, color_step);
    adjust_if_pressed(
        &input,
        KeyCode::Digit2,
        &mut state,
        DesignerParam::Green,
        color_step,
    );
    adjust_if_pressed(&input, KeyCode::Digit3, &mut state, DesignerParam::Blue, color_step);
    adjust_if_pressed(&input, KeyCode::Digit4, &mut state, DesignerParam::Glow, 0.04);
    adjust_if_pressed(
        &input,
        KeyCode::Digit5,
        &mut state,
        DesignerParam::Transparency,
        0.015,
    );
    adjust_if_pressed(&input, KeyCode::Digit6, &mut state, DesignerParam::Scroll, 0.035);
    adjust_if_pressed(&input, KeyCode::Digit7, &mut state, DesignerParam::Pulse, 0.035);
    adjust_if_pressed(&input, KeyCode::Digit8, &mut state, DesignerParam::Edge, 0.015);
    adjust_if_pressed(&input, KeyCode::Minus, &mut state, DesignerParam::Scale, -0.03);
    adjust_if_pressed(&input, KeyCode::Equal, &mut state, DesignerParam::Scale, 0.03);
}

fn adjust_if_pressed(
    input: &ButtonInput<KeyCode>,
    key: KeyCode,
    state: &mut BevyShaderDesignerState,
    param: DesignerParam,
    delta: f32,
) {
    if input.just_pressed(key) || input.pressed(key) {
        state.adjust_param(param, delta);
    }
}

fn handle_designer_buttons(
    mut commands: Commands,
    interactions: Query<
        (&Interaction, &DesignerAction),
        (Changed<Interaction>, With<ShaderDesignerActionButton>),
    >,
    mut state: ResMut<BevyShaderDesignerState>,
    mut mesh_query: Query<&mut Mesh3d, With<ShaderPreviewMesh>>,
    mut primitive_visibility: Query<&mut Visibility, With<ShaderPreviewMesh>>,
    model_roots: Query<Entity, With<ShaderPreviewModelRoot>>,
    mut meshes: ResMut<Assets<Mesh>>,
    asset_server: Res<AssetServer>,
    catalog: Res<BevyShaderDesignerAssetCatalog>,
    mut app_exit: MessageWriter<AppExit>,
) {
    for (interaction, action) in &interactions {
        if *interaction != Interaction::Pressed {
            continue;
        }
        match *action {
            DesignerAction::Template(template) => {
                apply_template(&mut state, template);
                let target = state.target;
                update_preview_mesh(target, &mut mesh_query, &mut meshes);
            }
            DesignerAction::Target(target) => {
                set_preview_target(
                    &mut state,
                    target,
                    &mut mesh_query,
                    &mut primitive_visibility,
                    &model_roots,
                    &mut commands,
                    &mut meshes,
                );
            }
            DesignerAction::ModelSlot(slot) => {
                select_model_slot(
                    &mut state,
                    slot,
                    catalog.as_ref(),
                    &asset_server,
                    &model_roots,
                    &mut primitive_visibility,
                    &mut commands,
                );
            }
            DesignerAction::ModelPage(direction) => {
                state.step_model_page(direction, catalog.as_ref());
            }
            DesignerAction::ClearModel => {
                clear_preview_model(
                    &mut state,
                    &model_roots,
                    &mut primitive_visibility,
                    &mut commands,
                );
            }
            DesignerAction::ToggleBlock(block) => {
                if state.blocks.contains(&block) {
                    state.blocks.remove(&block);
                } else {
                    state.blocks.insert(block);
                }
                state.selected_block = DesignerBlock::ALL
                    .iter()
                    .position(|value| *value == block)
                    .unwrap_or(state.selected_block);
                state.mark_dirty();
            }
            DesignerAction::Adjust(param, delta) => state.adjust_param(param, delta),
            DesignerAction::Palette(palette) => {
                state.main_color = palette.color();
                state.mark_dirty();
            }
            DesignerAction::ToggleOriginalTexture => {
                state.use_original_texture = !state.use_original_texture;
                state.mark_dirty();
            }
            DesignerAction::Save => state.save(),
            DesignerAction::Reset => {
                state.reset_to_defaults();
                let target = state.target;
                update_preview_mesh(target, &mut mesh_query, &mut meshes);
                clear_preview_model(
                    &mut state,
                    &model_roots,
                    &mut primitive_visibility,
                    &mut commands,
                );
            }
            DesignerAction::Close => {
                if state.dirty {
                    state.save();
                }
                app_exit.write(AppExit::Success);
            }
        }
    }
}

fn handle_designer_sliders(
    sliders: Query<(
        &Interaction,
        &RelativeCursorPosition,
        &ShaderDesignerSliderTrack,
    )>,
    mut state: ResMut<BevyShaderDesignerState>,
) {
    for (interaction, cursor, slider) in &sliders {
        if *interaction != Interaction::Pressed {
            continue;
        }
        let Some(position) = cursor.normalized else {
            continue;
        };
        let (min, max, _) = slider.0.range();
        let amount = (position.x + 0.5).clamp(0.0, 1.0);
        state.set_param(slider.0, min + (max - min) * amount);
    }
}

fn set_preview_target(
    state: &mut BevyShaderDesignerState,
    target: DesignerTarget,
    mesh_query: &mut Query<&mut Mesh3d, With<ShaderPreviewMesh>>,
    primitive_visibility: &mut Query<&mut Visibility, With<ShaderPreviewMesh>>,
    model_roots: &Query<Entity, With<ShaderPreviewModelRoot>>,
    commands: &mut Commands,
    meshes: &mut Assets<Mesh>,
) {
    if state.target == target && state.preview_model_index.is_none() {
        return;
    }
    state.target = target;
    clear_preview_model(state, model_roots, primitive_visibility, commands);
    update_preview_mesh(target, mesh_query, meshes);
    state.mark_dirty();
}

fn update_preview_mesh(
    target: DesignerTarget,
    mesh_query: &mut Query<&mut Mesh3d, With<ShaderPreviewMesh>>,
    meshes: &mut Assets<Mesh>,
) {
    for mut mesh in mesh_query {
        mesh.0 = preview_mesh(meshes, target);
    }
}

fn select_model_slot(
    state: &mut BevyShaderDesignerState,
    slot: usize,
    catalog: &BevyShaderDesignerAssetCatalog,
    asset_server: &AssetServer,
    model_roots: &Query<Entity, With<ShaderPreviewModelRoot>>,
    primitive_visibility: &mut Query<&mut Visibility, With<ShaderPreviewMesh>>,
    commands: &mut Commands,
) {
    let Some(model_index) = catalog.model_index_for_slot(state.model_page, slot) else {
        return;
    };
    let Some(model) = catalog.models.get(model_index) else {
        return;
    };
    despawn_preview_model_roots(model_roots, commands);
    for mut visibility in primitive_visibility {
        *visibility = Visibility::Hidden;
    }
    state.preview_model_index = Some(model_index);
    state.preview_model_asset_path = Some(model.asset_path.clone());
    spawn_preview_model(
        commands,
        asset_server,
        catalog,
        model_index,
        state.preview_scale,
    );
    state.mark_dirty();
}

fn clear_preview_model(
    state: &mut BevyShaderDesignerState,
    model_roots: &Query<Entity, With<ShaderPreviewModelRoot>>,
    primitive_visibility: &mut Query<&mut Visibility, With<ShaderPreviewMesh>>,
    commands: &mut Commands,
) {
    let had_model = state.preview_model_index.take().is_some()
        || state.preview_model_asset_path.take().is_some();
    despawn_preview_model_roots(model_roots, commands);
    for mut visibility in primitive_visibility {
        *visibility = Visibility::Inherited;
    }
    if had_model {
        state.mark_dirty();
    }
}

fn despawn_preview_model_roots(
    model_roots: &Query<Entity, With<ShaderPreviewModelRoot>>,
    commands: &mut Commands,
) {
    for root in model_roots {
        let mut entity = commands.entity(root);
        entity.despawn_children();
        entity.despawn();
    }
}

fn spawn_preview_model(
    commands: &mut Commands,
    asset_server: &AssetServer,
    catalog: &BevyShaderDesignerAssetCatalog,
    model_index: usize,
    preview_scale: f32,
) {
    let Some(model) = catalog.models.get(model_index) else {
        return;
    };
    let scene = asset_server.load(GltfAssetLabel::Scene(0).from_asset(model.asset_path.clone()));
    commands.spawn((
        WorldAssetRoot(scene),
        Transform::from_scale(Vec3::splat(preview_scale)),
        Visibility::Inherited,
        ShaderPreviewModelRoot,
    ));
}

fn animate_preview_mesh(
    time: Res<Time>,
    mut state: ResMut<BevyShaderDesignerState>,
    mut transforms: ParamSet<(
        Query<&mut Transform, With<ShaderPreviewMesh>>,
        Query<&mut Transform, With<ShaderPreviewModelRoot>>,
    )>,
) {
    state.elapsed += time.delta_secs();
    for mut transform in &mut transforms.p0() {
        transform.rotation = Quat::from_euler(EulerRot::YXZ, state.elapsed * 0.42, 0.22, 0.0);
        transform.scale = Vec3::splat(state.preview_scale);
    }
    for mut transform in &mut transforms.p1() {
        transform.rotation = Quat::from_euler(EulerRot::YXZ, state.elapsed * 0.34, 0.0, 0.0);
        transform.scale = Vec3::splat(state.preview_scale);
    }
}

fn apply_designer_material(
    state: Res<BevyShaderDesignerState>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    mut material_handles: ParamSet<(
        Query<&MeshMaterial3d<StandardMaterial>, With<ShaderPreviewMesh>>,
        Query<
            (
                &MeshMaterial3d<StandardMaterial>,
                &ShaderPreviewModelOriginalMaterial,
            ),
            With<ShaderPreviewModelMaterial>,
        >,
    )>,
) {
    if !state.is_changed() {
        return;
    }
    for handle in &material_handles.p0() {
        if let Some(mut material) = materials.get_mut(&handle.0) {
            *material = state.standard_material(None);
        }
    }
    for (handle, original) in &material_handles.p1() {
        let source = materials.get(&original.0).cloned();
        if let Some(mut material) = materials.get_mut(&handle.0) {
            *material = state.standard_material(source.as_ref());
        }
    }
}

fn apply_material_to_loaded_preview_model(
    scene_ready: On<WorldInstanceReady>,
    mut commands: Commands,
    children: Query<&Children>,
    model_roots: Query<(), With<ShaderPreviewModelRoot>>,
    mesh_materials: Query<&MeshMaterial3d<StandardMaterial>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
    state: Res<BevyShaderDesignerState>,
) {
    if model_roots.get(scene_ready.entity).is_err() {
        return;
    }
    for descendant in children.iter_descendants(scene_ready.entity) {
        let Ok(material_handle) = mesh_materials.get(descendant) else {
            continue;
        };
        let source = materials.get(&material_handle.0).cloned();
        let material = materials.add(state.standard_material(source.as_ref()));
        commands.entity(descendant).insert((
            MeshMaterial3d(material),
            ShaderPreviewModelMaterial,
            ShaderPreviewModelOriginalMaterial(material_handle.0.clone()),
        ));
    }
}

fn draw_designer_gizmos(mut gizmos: Gizmos, state: Res<BevyShaderDesignerState>) {
    let grid_color = LinearRgba::new(0.18, 0.22, 0.26, 1.0);
    gizmos.grid(
        Quat::from_rotation_x(std::f32::consts::FRAC_PI_2),
        UVec2::splat(18),
        Vec2::splat(0.35),
        grid_color,
    );
    gizmos.arrow(Vec3::ZERO, Vec3::X * 1.75, Color::srgb(0.95, 0.20, 0.18));
    gizmos.arrow(Vec3::ZERO, Vec3::Y * 1.75, Color::srgb(0.30, 0.88, 0.34));
    gizmos.arrow(Vec3::ZERO, Vec3::Z * 1.75, Color::srgb(0.25, 0.50, 1.0));
    let accent = Color::srgba(
        state.main_color[0],
        state.main_color[1],
        state.main_color[2],
        0.82,
    );
    match state.target {
        DesignerTarget::Box => {
            gizmos.cube(
                Transform::from_scale(Vec3::splat(1.95 * state.preview_scale)),
                accent,
            );
        }
        DesignerTarget::Sphere => {
            gizmos
                .sphere(Vec3::ZERO, 1.20 * state.preview_scale, accent)
                .resolution(48);
        }
        DesignerTarget::Sprite => {
            gizmos.rect(
                Isometry3d::new(Vec3::ZERO, Quat::from_rotation_x(0.0)),
                Vec2::new(2.55, 1.50) * state.preview_scale,
                accent,
            );
        }
    }
}

fn update_designer_ui_state(
    state: Res<BevyShaderDesignerState>,
    catalog: Res<BevyShaderDesignerAssetCatalog>,
    mut buttons: Query<
        (&Interaction, &DesignerAction, &mut BackgroundColor),
        With<ShaderDesignerActionButton>,
    >,
    mut texts: ParamSet<(
        Query<(&ShaderDesignerValueText, &mut Text)>,
        Query<&mut Text, With<ShaderDesignerStatusText>>,
        Query<(&ShaderDesignerModelSlotLabel, &mut Text)>,
        Query<&mut Text, With<ShaderDesignerModelPageText>>,
    )>,
    mut slider_nodes: ParamSet<(
        Query<(&ShaderDesignerValueFill, &mut Node)>,
        Query<(&ShaderDesignerSliderThumb, &mut Node)>,
    )>,
) {
    for (interaction, action, mut background) in &mut buttons {
        *background = BackgroundColor(button_color(&state, &catalog, *action, *interaction));
    }
    for (param, mut text) in &mut texts.p0() {
        *text = Text::new(format!("{:.2}", state.param_value(param.0)));
    }
    for (param, mut node) in &mut slider_nodes.p0() {
        let (min, max, _) = param.0.range();
        node.width = Val::Percent(normalized_percent(state.param_value(param.0), min, max));
    }
    for (param, mut node) in &mut slider_nodes.p1() {
        let (min, max, _) = param.0.range();
        node.left = Val::Percent(normalized_percent(state.param_value(param.0), min, max));
    }
    for mut text in &mut texts.p1() {
        *text = Text::new(status_text(&state));
    }
    for (slot, mut text) in &mut texts.p2() {
        *text = Text::new(model_slot_label(&state, &catalog, slot.0));
    }
    for mut text in &mut texts.p3() {
        *text = Text::new(model_page_text(&state, &catalog));
    }
}

fn button_color(
    state: &BevyShaderDesignerState,
    catalog: &BevyShaderDesignerAssetCatalog,
    action: DesignerAction,
    interaction: Interaction,
) -> Color {
    let active = action_is_active(state, catalog, action);
    let mut color = if matches!(action, DesignerAction::Close) {
        BUTTON_DANGER
    } else if active {
        BUTTON_ACTIVE
    } else {
        BUTTON_BG
    };
    if interaction == Interaction::Hovered {
        color = if active {
            BUTTON_ACTIVE_HOVER
        } else if matches!(action, DesignerAction::Close) {
            Color::srgb(0.520, 0.170, 0.155)
        } else {
            BUTTON_HOVER
        };
    }
    if interaction == Interaction::Pressed {
        color = if active {
            BUTTON_ACTIVE_HOVER
        } else {
            Color::srgb(0.210, 0.250, 0.300)
        };
    }
    color
}

fn action_is_active(
    state: &BevyShaderDesignerState,
    catalog: &BevyShaderDesignerAssetCatalog,
    action: DesignerAction,
) -> bool {
    match action {
        DesignerAction::Template(template) => state.template == template,
        DesignerAction::Target(target) => state.target == target && state.preview_model_index.is_none(),
        DesignerAction::ModelSlot(slot) => catalog
            .model_index_for_slot(state.model_page, slot)
            .is_some_and(|index| state.preview_model_index == Some(index)),
        DesignerAction::ToggleBlock(block) => state.blocks.contains(&block),
        DesignerAction::Palette(palette) => state.main_color == palette.color(),
        DesignerAction::ToggleOriginalTexture => state.use_original_texture,
        DesignerAction::ModelPage(_)
        | DesignerAction::ClearModel
        | DesignerAction::Adjust(_, _)
        | DesignerAction::Save
        | DesignerAction::Reset
        | DesignerAction::Close => false,
    }
}

fn normalized_percent(value: f32, min: f32, max: f32) -> f32 {
    (((value - min) / (max - min)).clamp(0.0, 1.0) * 100.0).round()
}

fn exit_designer_on_escape(
    input: Res<ButtonInput<KeyCode>>,
    mut app_exit: MessageWriter<AppExit>,
    mut state: ResMut<BevyShaderDesignerState>,
) {
    if input.just_pressed(KeyCode::Escape) {
        if state.dirty {
            state.save();
        }
        app_exit.write(AppExit::Success);
    }
}

impl BevyShaderDesignerState {
    fn param_value(&self, param: DesignerParam) -> f32 {
        match param {
            DesignerParam::Red => self.main_color[0],
            DesignerParam::Green => self.main_color[1],
            DesignerParam::Blue => self.main_color[2],
            DesignerParam::Glow => self.glow_strength,
            DesignerParam::Transparency => self.transparency,
            DesignerParam::Scroll => self.scroll_speed,
            DesignerParam::Pulse => self.pulse_speed,
            DesignerParam::Edge => self.edge_width,
            DesignerParam::Scale => self.preview_scale,
        }
    }

    fn set_param(&mut self, param: DesignerParam, value: f32) {
        let (min, max, _) = param.range();
        let value = value.clamp(min, max);
        let target = match param {
            DesignerParam::Red => &mut self.main_color[0],
            DesignerParam::Green => &mut self.main_color[1],
            DesignerParam::Blue => &mut self.main_color[2],
            DesignerParam::Glow => &mut self.glow_strength,
            DesignerParam::Transparency => &mut self.transparency,
            DesignerParam::Scroll => &mut self.scroll_speed,
            DesignerParam::Pulse => &mut self.pulse_speed,
            DesignerParam::Edge => &mut self.edge_width,
            DesignerParam::Scale => &mut self.preview_scale,
        };
        if (*target - value).abs() > f32::EPSILON {
            *target = value;
            self.mark_dirty();
        }
    }

    fn adjust_param(&mut self, param: DesignerParam, delta: f32) {
        self.set_param(param, self.param_value(param) + delta);
    }

    fn step_model_page(&mut self, direction: i32, catalog: &BevyShaderDesignerAssetCatalog) {
        let page_count = catalog.page_count();
        if page_count <= 1 {
            return;
        }
        self.model_page = if direction < 0 {
            self.model_page.saturating_sub(1)
        } else {
            (self.model_page + 1).min(page_count - 1)
        };
    }

    fn reset_to_defaults(&mut self) {
        self.template = DesignerTemplate::TextureTint;
        self.target = DesignerTarget::Box;
        self.preview_model_index = None;
        self.preview_model_asset_path = None;
        self.model_page = 0;
        self.selected_block = 0;
        self.blocks = HashSet::from([DesignerBlock::Tint]);
        self.main_color = [1.0, 0.85, 0.72, 1.0];
        self.glow_strength = 0.15;
        self.pulse_speed = 0.55;
        self.transparency = 0.05;
        self.edge_width = 0.15;
        self.scroll_speed = 0.35;
        self.preview_scale = 1.0;
        self.use_original_texture = true;
        self.mark_dirty();
    }

    fn standard_material(&self, source: Option<&StandardMaterial>) -> StandardMaterial {
        let alpha = (self.main_color[3] * (1.0 - self.transparency)).clamp(0.0, 1.0);
        let pulse = if self.blocks.contains(&DesignerBlock::Pulse) {
            0.72 + 0.28
                * (0.5 + 0.5 * (self.elapsed * self.pulse_speed * std::f32::consts::TAU).sin())
        } else {
            1.0
        };
        let mut color = Color::srgba(
            self.main_color[0] * pulse,
            self.main_color[1] * pulse,
            self.main_color[2] * pulse,
            alpha,
        );
        if self.blocks.contains(&DesignerBlock::ToonRamp) {
            color = Color::srgba(
                (self.main_color[0] * 0.85).clamp(0.0, 1.0),
                (self.main_color[1] * 0.85).clamp(0.0, 1.0),
                (self.main_color[2] * 0.85).clamp(0.0, 1.0),
                alpha,
            );
        }
        let glow = if self.blocks.contains(&DesignerBlock::Glow)
            || self.blocks.contains(&DesignerBlock::RimLight)
            || self.blocks.contains(&DesignerBlock::HologramLines)
        {
            self.glow_strength.max(0.0)
        } else {
            0.0
        };
        let mut material = source.cloned().unwrap_or_default();
        material.base_color = color;
        material.emissive = LinearRgba::new(
            self.main_color[0] * glow,
            self.main_color[1] * glow,
            self.main_color[2] * glow,
            1.0,
        );
        material.perceptual_roughness = if self.blocks.contains(&DesignerBlock::WaterWaves) {
            0.18
        } else {
            0.52
        };
        material.metallic = if self.blocks.contains(&DesignerBlock::HologramLines) {
            0.15
        } else {
            0.0
        };
        material.alpha_mode = if alpha < 0.999 || self.blocks.contains(&DesignerBlock::Dissolve) {
            AlphaMode::Blend
        } else {
            AlphaMode::Opaque
        };
        if !self.use_original_texture {
            material.base_color_texture = None;
            material.normal_map_texture = None;
            material.emissive_texture = None;
        }
        material
    }
}

fn apply_template(state: &mut BevyShaderDesignerState, template: DesignerTemplate) {
    state.template = template;
    state.blocks.clear();
    match template {
        DesignerTemplate::TextureTint => {
            state.blocks.insert(DesignerBlock::Tint);
            state.main_color = [1.0, 0.85, 0.72, 1.0];
            state.target = DesignerTarget::Box;
        }
        DesignerTemplate::UiSoftGlow => {
            state
                .blocks
                .extend([DesignerBlock::Tint, DesignerBlock::Glow]);
            state.main_color = [0.92, 0.97, 1.0, 1.0];
            state.glow_strength = 0.55;
            state.target = DesignerTarget::Sprite;
        }
        DesignerTemplate::UiNeonScan => {
            state.blocks.extend([
                DesignerBlock::Tint,
                DesignerBlock::Glow,
                DesignerBlock::Pulse,
                DesignerBlock::HologramLines,
                DesignerBlock::Flicker,
            ]);
            state.main_color = [0.20, 1.0, 0.86, 1.0];
            state.glow_strength = 1.15;
            state.target = DesignerTarget::Sprite;
        }
        DesignerTemplate::GlowPulse => {
            state.blocks.extend([
                DesignerBlock::Tint,
                DesignerBlock::Glow,
                DesignerBlock::Pulse,
                DesignerBlock::RimLight,
            ]);
            state.main_color = [0.36, 0.92, 1.0, 1.0];
            state.glow_strength = 1.25;
            state.target = DesignerTarget::Sphere;
        }
        DesignerTemplate::Dissolve => {
            state.blocks.extend([
                DesignerBlock::Tint,
                DesignerBlock::Dissolve,
                DesignerBlock::Glow,
            ]);
            state.main_color = [1.0, 0.54, 0.20, 1.0];
            state.transparency = 0.35;
            state.target = DesignerTarget::Box;
        }
        DesignerTemplate::HologramLite => {
            state.blocks.extend([
                DesignerBlock::Tint,
                DesignerBlock::Glow,
                DesignerBlock::Pulse,
                DesignerBlock::RimLight,
                DesignerBlock::HologramLines,
                DesignerBlock::Flicker,
            ]);
            state.main_color = [0.32, 0.92, 1.0, 0.95];
            state.glow_strength = 1.4;
            state.transparency = 0.22;
            state.target = DesignerTarget::Sphere;
        }
        DesignerTemplate::WaterLite => {
            state.blocks.extend([
                DesignerBlock::Tint,
                DesignerBlock::ScrollUv,
                DesignerBlock::WaterWaves,
                DesignerBlock::Glow,
            ]);
            state.main_color = [0.22, 0.62, 1.0, 0.92];
            state.glow_strength = 0.45;
            state.transparency = 0.18;
            state.target = DesignerTarget::Sprite;
        }
    }
    state.mark_dirty();
}

fn block_from_key(value: &str) -> Option<DesignerBlock> {
    DesignerBlock::ALL
        .into_iter()
        .find(|block| block.key().eq_ignore_ascii_case(value))
}

fn default_shader_json() -> serde_json::Value {
    serde_json::json!({
        "version": 1,
        "template": "TEXTURE_TINT",
        "previewTarget": "BOX",
        "mainColor": [1.0, 0.85, 0.72, 1.0],
        "glowStrength": 0.15,
        "pulseSpeed": 0.55,
        "transparency": 0.05,
        "edgeWidth": 0.15,
        "scrollSpeed": 0.35,
        "previewScale": 1.0,
        "useOriginalTexture": true,
        "blocks": ["TINT"]
    })
}

fn rgba_array(value: Option<&serde_json::Value>, fallback: [f32; 4]) -> [f32; 4] {
    let Some(values) = value.and_then(serde_json::Value::as_array) else {
        return fallback;
    };
    let mut out = fallback;
    for (index, value) in values.iter().take(4).enumerate() {
        if let Some(value) = value.as_f64() {
            out[index] = value as f32;
        }
    }
    out
}

fn f32_json(value: &serde_json::Value, field: &str, fallback: f32) -> f32 {
    value
        .get(field)
        .and_then(serde_json::Value::as_f64)
        .map(|value| value as f32)
        .unwrap_or(fallback)
}

fn bool_json(value: &serde_json::Value, field: &str, fallback: bool) -> bool {
    value
        .get(field)
        .and_then(serde_json::Value::as_bool)
        .unwrap_or(fallback)
}

fn status_text(state: &BevyShaderDesignerState) -> String {
    format!("{}{}", if state.dirty { "* " } else { "" }, state.status)
}
