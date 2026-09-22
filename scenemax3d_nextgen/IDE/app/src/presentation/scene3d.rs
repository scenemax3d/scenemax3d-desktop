//! Embedded, editable 3D scene documents, isolated from the projector runtime.
use super::components::EditorHost;
use crate::application::{EditorServices, Session};
use bevy::{
    asset::RenderAssetUsages,
    camera::RenderTarget,
    prelude::*,
    render::render_resource::{TextureDimension, TextureFormat, TextureUsages},
    ui::widget::ViewportNode,
};
use scenemax_ide_core::{DocumentId, DocumentRevision};
use scenemax_ide_services::{StorageRequest, StorageResult, scene3d::Scene3d};
use scenemax_ide_ui::{button, label, theme::*};

#[derive(Component)]
pub(crate) struct SceneHost;
#[derive(Component)]
pub(crate) enum Choose {
    Select(usize),
    Toggle(usize),
}
#[derive(Component, Clone, Copy)]
pub(crate) struct Orbit {
    pub(crate) target: Vec3,
    pub(crate) distance: f32,
    pub(crate) yaw: f32,
    pub(crate) pitch: f32,
}
impl Orbit {
    pub(crate) fn transform(&self) -> Transform {
        let direction = Vec3::new(
            self.yaw.sin() * self.pitch.cos(),
            self.pitch.sin(),
            self.yaw.cos() * self.pitch.cos(),
        );
        Transform::from_translation(self.target + direction * self.distance)
            .looking_at(self.target, Vec3::Y)
    }
}
#[derive(Resource, Default)]
pub(crate) struct SceneState {
    reload_assets: bool,
    pending: Option<(DocumentId, DocumentRevision)>,
    current: Option<(DocumentId, DocumentRevision)>,
    completed: Option<(DocumentId, DocumentRevision)>,
    world: Option<Entity>,
    camera: Option<Entity>,
    scene: Option<Scene3d>,
    selected: usize,
    pending_selection: Option<String>,
    collapsed: std::collections::HashSet<usize>,
    saved_view: Option<(Transform, Orbit)>,
    parts: Option<view::Parts>,
}
impl SceneState {
    pub(crate) fn reload_assets(&mut self) {
        self.reload_assets = true;
    }
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Renderer<'w, 's> {
    capture: Option<ResMut<'w, super::smoke::SmokeCapture>>,
    cameras: Query<'w, 's, (&'static Transform, &'static Orbit)>,
    images: Option<ResMut<'w, Assets<Image>>>,
    textures: Option<ResMut<'w, scenemax_materials::TextureCache>>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    server: Option<Res<'w, AssetServer>>,
    project_assets: Option<Res<'w, crate::project_assets::ProjectAssets>>,
}
pub(crate) fn refresh_materials(
    mut changes: MessageReader<crate::application::ViewChange>,
    buttons: Query<&Interaction, (With<inspector::RefreshMaterials>, Changed<Interaction>)>,
    mut state: ResMut<SceneState>,
) {
    let mut refresh = false;
    for event in changes.read() {
        refresh |= matches!(
            event,
            crate::application::ViewChange::MaterialsChanged
                | crate::application::ViewChange::ProjectTreeChanged
        );
    }
    if refresh || buttons.iter().any(|i| *i == Interaction::Pressed) {
        state.reload_assets();
        // A material scan already in flight describes the old asset inventory.
        state.pending = None;
    }
}
pub(crate) fn update(
    mut commands: Commands,
    session: Res<Session>,
    mut services: ResMut<EditorServices>,
    mut state: ResMut<SceneState>,
    hosts: Query<(Entity, &EditorHost), With<SceneHost>>,
    choices: Query<(&Interaction, &Choose), Changed<Interaction>>,
    mut renderer: Renderer,
) {
    let active = session
        .workspace
        .active_id()
        .and_then(|id| session.workspace.document(id).ok().map(|d| (id, d)));
    let target = active.and_then(|(id, d)| {
        hosts
            .iter()
            .find(|(_, h)| h.0 == id)
            .map(|(host, _)| (host, id, d))
    });
    let desired = target.map(|(_, id, d)| (id, d.revision()));
    if state.current != desired || state.reload_assets {
        state.reload_assets = false;
        // The world is owned by the active scene. Remove its old controls too,
        // so hidden tabs cannot contribute stale inspector values to live edits.
        if let Some((previous, _)) = state.current
            && Some(previous) != desired.map(|s| s.0)
        {
            for (host, owner) in &hosts {
                if owner.0 == previous {
                    commands.entity(host).despawn_children();
                }
            }
        }
        let same_document = state.current.map(|s| s.0) == desired.map(|s| s.0);
        if same_document {
            state.saved_view = state
                .camera
                .and_then(|c| renderer.cameras.get(c).ok())
                .map(|(t, o)| (*t, *o));
        } else {
            state.saved_view = None;
            state.selected = 0;
            state.collapsed.clear();
        }
        if let Some(world) = state.world.take() {
            commands.entity(world).despawn();
        }
        state.camera = None;
        state.parts = None;
        state.scene = None;
        state.current = desired;
        state.completed = None;
        if let Some((host, _, _)) = target {
            commands.entity(host).despawn_children();
            commands.spawn((
                label("Loading 3D scene and resolving project models…", 14.),
                ChildOf(host),
            ));
        }
    }
    if services.scene_storage.is_pending() {
        match services.scene_storage.poll() {
            Ok(Some(StorageResult::Scene3d(result))) => {
                let stamp = state.pending.take();
                state.completed = stamp;
                if stamp == desired
                    && let Some((host, _, _)) = target
                {
                    match result {
                        Ok(scene) => {
                            if let Some(pointer) = state.pending_selection.take()
                                && let Some(index) =
                                    scene.entities.iter().position(|e| e.pointer == pointer)
                            {
                                state.selected = index;
                            }
                            let (
                                Some(images),
                                Some(meshes),
                                Some(materials),
                                Some(server),
                                Some(project_assets),
                                Some(textures),
                            ) = (
                                renderer.images.as_mut(),
                                renderer.meshes.as_mut(),
                                renderer.materials.as_mut(),
                                renderer.server.as_ref(),
                                renderer.project_assets.as_ref(),
                                renderer.textures.as_mut(),
                            )
                            else {
                                return;
                            };
                            let (world, camera) = spawn_world(
                                &mut commands,
                                images,
                                meshes,
                                materials,
                                server,
                                &scene,
                                (project_assets, textures),
                            );
                            if renderer.capture.is_some()
                                && std::env::var_os("SCENEMAX_SMOKE_SCENE_SAVED_CAMERA").is_some()
                                && let Some(view) = &scene.camera
                            {
                                let rotation = Quat::from_array(view.rotation).normalize();
                                let translation = Vec3::from_array(view.position);
                                // Stored scene cameras look along +Z; Bevy cameras look along -Z.
                                let direction = -(rotation * Vec3::Z);
                                let orbit = Orbit {
                                    target: translation - direction * 20.,
                                    distance: 20.,
                                    yaw: direction.x.atan2(direction.z),
                                    pitch: direction.y.asin(),
                                };
                                commands.entity(camera).insert((orbit.transform(), orbit));
                            }
                            if let Some((transform, orbit)) = state.saved_view {
                                commands.entity(camera).insert((transform, orbit));
                            }
                            state.world = Some(world);
                            state.camera = Some(camera);
                            state.selected =
                                state.selected.min(scene.entities.len().saturating_sub(1));
                            if state.saved_view.is_none() {
                                state.collapsed = scene
                                    .entities
                                    .iter()
                                    .enumerate()
                                    .filter_map(|(i, e)| (e.kind == "SECTION").then_some(i))
                                    .collect();
                            }
                            state.parts = Some(build(
                                &mut commands,
                                host,
                                camera,
                                &scene,
                                state.selected,
                                &state.collapsed,
                            ));
                            state.scene = Some(scene);
                        }
                        Err(error) => {
                            commands.entity(host).despawn_children();
                            commands.spawn((
                                label(format!("Could not open this .smdesign scene\n{error}"), 14.),
                                ChildOf(host),
                            ));
                        }
                    }
                }
            }
            Err(error) => {
                state.pending = None;
                state.completed = desired;
                if let Some((host, _, _)) = target {
                    commands.entity(host).despawn_children();
                    commands.spawn((label(error.to_string(), 14.), ChildOf(host)));
                }
            }
            _ => {}
        }
    }
    if !services.scene_storage.is_pending()
        && state.completed != desired
        && state.scene.is_none()
        && let Some((_, id, doc)) = target
        && services
            .scene_storage
            .request(StorageRequest::Scene3d {
                root: session.workspace.project().root().into(),
                source: doc.text().into(),
            })
            .is_ok()
    {
        state.pending = Some((id, doc.revision()));
    }
    if state.scene.is_some()
        && let Some(capture) = renderer.capture.as_mut()
        && let Some(index) = capture.scene_entry.take()
    {
        commands.spawn((Interaction::Pressed, Choose::Select(index)));
    }
    for (interaction, choice) in &choices {
        if *interaction != Interaction::Pressed {
            continue;
        }
        match *choice {
            Choose::Select(index) => {
                if index == state.selected
                    || state
                        .scene
                        .as_ref()
                        .is_none_or(|s| index >= s.entities.len())
                {
                    continue;
                }
                state.selected = index;
                if let (Some(parts), Some(scene)) = (state.parts, state.scene.as_ref()) {
                    view::inspect(&mut commands, parts, scene, index);
                }
            }
            Choose::Toggle(index) => {
                if !state.collapsed.remove(&index) {
                    state.collapsed.insert(index);
                }
            }
        }
    }
}
#[derive(Component)]
pub(crate) struct AssetStatus;
pub(crate) fn asset_status(
    server: Option<Res<AssetServer>>,
    models: Query<&WorldAssetRoot>,
    mut labels: Query<(&mut Text, &mut Node), With<AssetStatus>>,
) {
    let Some(server) = server else {
        return;
    };
    let mut ready = 0;
    let mut failed = Vec::new();
    let mut count = 0;
    for model in &models {
        count += 1;
        if server.is_loaded_with_dependencies(model.0.id()) {
            ready += 1;
        }
        if let Some(bevy::asset::LoadState::Failed(error)) = server.get_load_state(model.0.id()) {
            failed.push(error.to_string());
        }
    }
    let value = if failed.is_empty() {
        format!("Model loading: {ready}/{count} ready")
    } else {
        format!(
            "Model loading: {ready}/{count} ready · {} failed: {}",
            failed.len(),
            failed[0]
        )
    };
    for (mut text, mut node) in &mut labels {
        let display = if failed.is_empty() && ready == count {
            Display::None
        } else {
            Display::Flex
        };
        if node.display != display {
            node.display = display;
        }
        if text.0 != value {
            text.0.clone_from(&value);
        }
    }
}

pub(crate) mod ambient;
mod cinematic;
pub(crate) mod constraints;
pub(crate) mod gizmo;
pub(crate) mod inspector;
pub(crate) mod live;
pub(crate) mod navigation;
pub(crate) mod path;
pub(crate) mod picking;
pub(crate) mod playback;
mod primitives;
mod properties;
mod render;
pub(crate) mod rig;
pub(crate) mod segments;
pub(crate) mod tools;
mod view;
use render::spawn_world;
use view::build;
pub(crate) use view::{synchronize_names, synchronize_tree};

#[cfg(test)]
mod inspector_tests;

#[cfg(test)]
mod tools_tests;

#[cfg(test)]
mod live_tests;

pub(crate) mod focus;
pub(crate) mod game_camera;

pub(crate) mod ik_controls;

pub(crate) mod grid;
