//! SceneMax Studio composition root and application plugin.
#![forbid(unsafe_code)]
#![deny(missing_docs)]
#![cfg_attr(not(test), deny(clippy::unwrap_used, clippy::expect_used))]

mod application;
mod presentation;
mod project_assets;
#[cfg(test)]
mod tests;

use anyhow::Result;
use application::{CommandQueue, EditorServices, Session, ViewChange};
use bevy::{input_focus::tab_navigation::TabNavigationPlugin, prelude::*};
use scenemax_ide_core::Project;
use scenemax_ide_services::StorageRequest;
use scenemax_ide_ui::{StudioUiPlugin, theme::BG};
use std::{path::PathBuf, time::Duration};

/// Launch configuration independent of command-line parsing.
pub struct LaunchOptions {
    /// Selected project directory.
    pub project_root: PathBuf,
    /// Optional existing Java-compatible project catalog.
    pub project_catalog: Option<PathBuf>,
    /// Restore the catalog selection when no explicit project was requested.
    pub select_catalog_project: bool,
    /// Optional initial script, relative to the project root or absolute.
    pub script: Option<PathBuf>,
    /// Explicit projector build; otherwise use the sibling executable.
    pub projector: Option<PathBuf>,
    /// Optional frame limit for controlled GPU smoke runs.
    pub smoke_frames: Option<u32>,
    /// Screenshot destination for a controlled smoke run of at least 30 frames.
    pub smoke_screenshot: Option<PathBuf>,
    /// Open the File menu during a controlled screenshot run.
    pub smoke_menu: bool,
    /// Show Project Explorer after initial loading in a controlled smoke run.
    pub smoke_projects: bool,
    /// Run the project after loading in a controlled smoke run.
    pub smoke_run_project: bool,
    /// Show code completion at the document end during a controlled GPU run.
    pub smoke_completion: bool,
    /// Select a scene hierarchy entry in a controlled GPU capture.
    pub smoke_scene_entry: Option<usize>,
    /// Open a project-tree popup for a project-relative path during a GPU capture.
    pub smoke_tree_menu: Option<PathBuf>,
}

/// Start the standalone IDE. Projector code is not linked into this executable.
pub fn run(options: LaunchOptions) -> Result<()> {
    // Show the shell immediately; canonicalization, scanning and initial loading
    // belong to the disk worker, including startup on slow/network storage.
    let restart_executable = std::env::current_exe()?;
    let restart_catalog = options.project_catalog.clone();
    let restart_projector = options.projector.clone();
    let mut session = Session::new(Project::new(options.project_root.clone(), vec![]));
    session.status = "Opening project…".into();
    let restart_project = session.restart_project.clone();
    let projector = match options.projector {
        Some(path) => path,
        None => std::env::current_exe()?.with_file_name(format!(
            "scenemax_projector_nextgen{}",
            std::env::consts::EXE_SUFFIX
        )),
    };
    let mut services = EditorServices::new(projector)?;
    services.catalog_root = options.project_root.clone();
    if options.smoke_frames.is_none() {
        services.last_project_file = scenemax_ide_services::workspace_state::last_project_file();
    }
    services.catalog_path = options.project_catalog.clone();
    services.catalog_storage.request(StorageRequest::Catalog {
        root: options.project_root.clone(),
        path: options.project_catalog,
        last_project: services.last_project_file.clone(),
    })?;
    if options.select_catalog_project {
        services.catalog_startup = Some((options.project_root, options.script));
    } else {
        services.storage.request(StorageRequest::Project {
            root: options.project_root,
            script: options.script,
        })?;
    }
    let mut app = App::new();
    let project_assets = project_assets::ProjectAssets::default();
    let reader = project_assets.clone();
    app.register_asset_source(
        "project",
        bevy::asset::io::AssetSourceBuilder::new(move || Box::new(reader.clone())),
    );
    app.insert_resource(project_assets);
    app.insert_resource(services)
        .insert_resource(session)
        .insert_resource(ClearColor(BG))
        .insert_resource(if options.smoke_frames.is_some() {
            bevy::winit::WinitSettings::continuous()
        } else {
            bevy::winit::WinitSettings {
                focused_mode: bevy::winit::UpdateMode::reactive(Duration::from_millis(100)),
                unfocused_mode: bevy::winit::UpdateMode::reactive_low_power(Duration::from_millis(
                    250,
                )),
            }
        })
        .add_plugins(
            DefaultPlugins
                .set(bevy::render::RenderPlugin {
                    render_creation: bevy::render::settings::RenderCreation::Automatic(Box::new(
                        bevy::render::settings::WgpuSettings {
                            backends: Some(bevy::render::settings::Backends::VULKAN),
                            ..default()
                        },
                    )),
                    ..default()
                })
                .set(bevy::asset::AssetPlugin {
                    unapproved_path_mode: bevy::asset::UnapprovedPathMode::Forbid,
                    ..default()
                })
                .set(WindowPlugin {
                    primary_window: Some(Window {
                        title: "SceneMax Studio".into(),
                        decorations: false,
                        resolution: (1400, 900).into(),
                        ..default()
                    }),
                    close_when_requested: false,
                    ..default()
                }),
        )
        .add_plugins(scenemax_effects::EffectsPlugin)
        .add_plugins(scenemax_materials::MaterialsPlugin)
        .init_resource::<scenemax_effects::PreviewClock>()
        .add_plugins((TabNavigationPlugin, StudioUiPlugin, StudioPlugin))
        .add_plugins((
            presentation::scene3d::gizmo::GizmoPlugin,
            presentation::scene3d::grid::GridPlugin,
        ));
    app.add_systems(
        PostUpdate,
        presentation::scene3d::ik_controls::refresh
            .after(scenemax_ik::Solve)
            .before(bevy::transform::TransformSystems::Propagate),
    );
    app.add_systems(
        Update,
        presentation::scene3d::ik_controls::commit.before(presentation::scene3d::live::update),
    );

    bevy::asset::embedded_asset!(app, "presentation/scenemax_icon.png");
    presentation::java_icons::register(&mut app);
    app.init_gizmo_group::<presentation::effect_import::preview::Lines>();
    app.world_mut()
        .resource_mut::<bevy::gizmos::config::GizmoConfigStore>()
        .config_mut::<presentation::effect_import::preview::Lines>()
        .0
        .render_layers = bevy::camera::visibility::RenderLayers::layer(4);
    app.init_gizmo_group::<presentation::model_import::render::ImportLines>();
    app.world_mut()
        .resource_mut::<bevy::gizmos::config::GizmoConfigStore>()
        .config_mut::<presentation::model_import::render::ImportLines>()
        .0
        .render_layers = bevy::camera::visibility::RenderLayers::layer(3);
    app.add_systems(Update, presentation::model_import::render::overlays);
    app.add_systems(
        Update,
        presentation::effect_import::preview::update
            .after(presentation::effect_import::update)
            .after(presentation::scene3d::playback::cadence),
    );

    if let Some(remaining) = options.smoke_frames {
        app.insert_resource(presentation::smoke::SmokeCapture {
            remaining,
            path: options.smoke_screenshot,
            show_projects: options.smoke_projects,
            run_project: options.smoke_run_project,
            show_completion: options.smoke_completion,
            scene_entry: options.smoke_scene_entry,
            tree_menu: options.smoke_tree_menu,
        });
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_INVENTORY").is_some() {
        app.add_systems(
            Update,
            presentation::inventory::smoke.before(presentation::inventory::update),
        );
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_ABOUT").is_some() {
        app.add_systems(
            Update,
            presentation::about::smoke.before(presentation::chrome::controls),
        );
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_IK").is_some() {
        app.add_systems(Update, presentation::ik::smoke);
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_MOTION").is_some() {
        app.add_systems(Update, presentation::motion::smoke);
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_ANALYZER").is_some() {
        app.add_systems(
            Update,
            presentation::animation_analyzer::smoke
                .before(presentation::animation_analyzer::update),
        );
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_WEAPON").is_some() {
        app.add_systems(Update, presentation::weapon::smoke);
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_FONT").is_some() {
        app.add_systems(Update, presentation::font_generator::smoke);
    }
    if options.smoke_menu && options.smoke_frames.is_some() {
        app.world_mut()
            .resource_mut::<presentation::chrome::ChromeState>()
            .preview_file_menu();
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_IMPORT").is_some() {
        app.world_mut()
            .resource_mut::<presentation::asset_import::State>()
            .preview(scenemax_ide_services::imports::Kind::Sprite);
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_MODEL").is_some() {
        app.add_systems(Update, presentation::model_import::smoke);
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_SPRITE").is_some() {
        app.add_systems(Update, presentation::sprite_import::smoke);
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_EFFECT").is_some() {
        app.add_systems(Update, presentation::effect_import::smoke);
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_DEPLOY").is_some() {
        app.add_systems(Update, presentation::deployment::smoke);
    }
    if options.smoke_frames.is_some() && std::env::var_os("SCENEMAX_SMOKE_DEPLOY_BUILD").is_some() {
        app.add_systems(Update, presentation::deployment::smoke_build);
    }
    app.run();
    let restart = restart_project
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .take();
    if let Some(root) = restart {
        scenemax_ide_services::workspace_state::restart(
            &restart_executable,
            &root,
            restart_catalog.as_deref(),
            restart_projector.as_deref(),
        )?;
    }
    Ok(())
}

struct StudioPlugin;
impl Plugin for StudioPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(
            Update,
            (
                presentation::titlebar::load_icon,
                presentation::java_icons::load,
            ),
        );
        app.init_resource::<CommandQueue>()
            .init_resource::<application::deployment::Deployment>()
            .init_resource::<presentation::deployment::View>()
            .init_resource::<presentation::tree_menu::State>()
            .init_resource::<presentation::asset_import::State>()
            .init_resource::<presentation::inventory::State>()
            .init_resource::<presentation::font_generator::State>()
            .init_resource::<presentation::model_import::State>()
            .init_resource::<presentation::sprite_import::State>()
            .init_resource::<presentation::effect_import::State>()
            .init_resource::<presentation::titlebar::Maximized>()
            .init_resource::<application::symbols::ProjectSymbols>()
            .init_resource::<presentation::scene3d::SceneState>()
            .init_resource::<presentation::scene3d::ik_controls::State>()
            .init_resource::<presentation::scene3d::rig::Selection>()
            .init_resource::<presentation::scene3d::picking::Request>()
            .init_resource::<presentation::scene3d::tools::Tools>()
            .init_resource::<presentation::scene3d::path::Drawing>()
            .init_resource::<presentation::scene3d::navigation::Navigation>()
            .init_resource::<presentation::scene3d::playback::Playback>()
            .init_resource::<presentation::completion::CompletionState>()
            .init_resource::<ButtonInput<MouseButton>>()
            .init_resource::<presentation::chrome::ChromeState>()
            .init_resource::<presentation::editing::EditorZoom>()
            .init_resource::<application::material::MaterialLibrary>()
            .init_resource::<presentation::material::State>()
            .init_resource::<presentation::weapon::State>()
            .init_resource::<presentation::animation_analyzer::State>()
            .init_resource::<presentation::about::State>()
            .init_resource::<presentation::motion::State>()
            .init_resource::<presentation::ik::State>()
            .add_plugins(scenemax_ik::IkPlugin)
            .add_systems(
                PostUpdate,
                presentation::weapon::attachment::follow
                    .after(bevy::app::AnimationSystems)
                    .before(bevy::transform::TransformSystems::Propagate),
            )
            .add_systems(
                PostUpdate,
                presentation::scene3d::constraints::update
                    .after(bevy::app::AnimationSystems)
                    .before(scenemax_ik::Solve),
            )
            .add_systems(
                Update,
                presentation::scene3d::constraints::diagnostics
                    .after(presentation::scene3d::asset_status),
            )
            .add_systems(
                Update,
                presentation::scene3d::constraints::smoke_rotation
                    .before(presentation::scene3d::live::update),
            )
            .init_resource::<presentation::browser::TreeState>()
            .add_message::<ViewChange>()
            .add_systems(
                Startup,
                (
                    presentation::shell::setup,
                    presentation::titlebar::maximize_on_startup,
                ),
            )
            .add_systems(
                Update,
                (
                    presentation::completion::prepare,
                    presentation::assistance::indent_newlines,
                    scenemax_ide_ui::commit_pending_input,
                )
                    .chain()
                    .before(presentation::input::sync_documents),
            )
            // Commit queued native input before commands, then materialize retained views
            // in Update. Bevy can prepare, lay out and render every change in the
            // same frame; no UI entities are rebuilt after the layout pass.
            .add_systems(
                Update,
                (
                    presentation::input::sync_documents,
                    presentation::input::collect_actions,
                    (presentation::chrome::controls, presentation::about::update).chain(),
                    (
                        presentation::browser::keyboard,
                        presentation::tree_menu::update,
                        presentation::asset_import::update,
                        presentation::inventory::update,
                        presentation::inventory::preview_update,
                        presentation::font_generator::update,
                        presentation::deployment::collect,
                        presentation::deployment::keyboard,
                        application::deployment::update,
                        presentation::deployment::refresh,
                    )
                        .chain(),
                    (
                        presentation::material::update,
                        (
                            presentation::weapon::update,
                            presentation::animation_analyzer::update,
                        )
                            .chain(),
                        presentation::motion::update,
                        presentation::ik::update,
                        presentation::model_import::update,
                        presentation::sprite_import::update,
                        presentation::effect_import::update,
                        presentation::designer::live::update,
                        presentation::designer::interactions,
                        presentation::scene3d::live::update,
                        presentation::scene3d::inspector::apply,
                        presentation::scene3d::tools::update,
                        presentation::scene3d::ambient::update,
                        presentation::scene3d::path::update,
                        presentation::scene3d::segments::update,
                        application::execute_commands,
                    )
                        .chain(),
                    (
                        application::poll_jobs,
                        application::symbols::update,
                        application::material::library,
                    )
                        .chain(),
                    application::checkpoint_buffers,
                    (
                        presentation::reconcile::reconcile,
                        presentation::designer::refresh,
                        presentation::model_import::render::update,
                        presentation::material::preview::update,
                        (
                            presentation::weapon::preview::update,
                            presentation::animation_analyzer::preview::update,
                        )
                            .chain(),
                        (
                            presentation::motion::preview::update,
                            presentation::ik::preview::update,
                        )
                            .chain(),
                        presentation::sprite_import::preview::update,
                        presentation::model_import::playback::update,
                        presentation::scene3d::refresh_materials,
                        presentation::scene3d::update,
                        presentation::scene3d::synchronize_tree,
                        presentation::scene3d::synchronize_names,
                        presentation::scene3d::rig::highlight,
                        presentation::scene3d::navigation::update,
                        presentation::scene3d::focus::update,
                        (
                            presentation::scene3d::game_camera::synchronize,
                            presentation::scene3d::game_camera::size_marker,
                        )
                            .chain(),
                        presentation::scene3d::tools::lighting,
                        presentation::scene3d::asset_status,
                        presentation::scene3d::playback::update,
                        presentation::scene3d::playback::cadence,
                    )
                        .chain(),
                    presentation::projects::refresh,
                    presentation::browser::update_tree,
                    presentation::browser::selection,
                    presentation::chrome::active_tabs,
                    presentation::reconcile::search_results,
                    presentation::editing::project_edits,
                    (
                        presentation::editing::apply_editor_zoom,
                        presentation::editing::update_gutters,
                        presentation::editing::highlight_documents,
                        presentation::assistance::bracket_emphasis,
                        presentation::completion::refresh,
                    )
                        .chain(),
                    presentation::labels::refresh_labels,
                    presentation::labels::close_prompt,
                    (
                        presentation::labels::refresh_console,
                        presentation::labels::output_visibility,
                    )
                        .chain(),
                    presentation::labels::tab_close_prompt,
                    presentation::smoke::smoke_capture,
                )
                    .chain(),
            );
    }
}
