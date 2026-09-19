//! Isolated native preview camera and editor simulation clock.
use super::super::scene3d::Orbit;
use super::*;
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, visibility::RenderLayers},
    render::render_resource::{Extent3d, TextureDimension, TextureFormat, TextureUsages},
    ui::widget::ViewportNode,
};
use scenemax_effects::{Effect, Playback, PreviewClock};
#[derive(Component)]
struct Camera;
#[derive(Component)]
struct WorldOwner(Entity);
#[derive(Default, Reflect, bevy::gizmos::config::GizmoConfigGroup)]
pub(crate) struct Lines;
pub(super) fn observe(commands: &mut Commands, viewport: Entity, host: Entity) {
    commands.entity(viewport).observe(
        move |mut e: On<Pointer<Drag>>,
              states: Query<&Import>,
              mut cameras: Query<(&mut Orbit, &mut Transform), With<Camera>>| {
            if !matches!(e.button, PointerButton::Secondary | PointerButton::Middle) {
                return;
            }
            e.propagate(false);
            let Ok(state) = states.get(host) else {
                return;
            };
            let Some(camera) = state.camera else {
                return;
            };
            if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                if e.button == PointerButton::Middle {
                    let distance = orbit.distance;
                    orbit.target += transform.rotation
                        * Vec3::new(-e.delta.x, e.delta.y, 0.)
                        * distance
                        * 0.002;
                } else {
                    orbit.yaw -= e.delta.x * 0.007;
                    orbit.pitch = (orbit.pitch + e.delta.y * 0.007).clamp(-1.5, 1.5);
                }
                *transform = orbit.transform();
            }
        },
    );
    commands.entity(viewport).observe(
        move |mut e: On<Pointer<Scroll>>,
              states: Query<&Import>,
              mut cameras: Query<(&mut Orbit, &mut Transform), With<Camera>>| {
            e.propagate(false);
            let Ok(state) = states.get(host) else {
                return;
            };
            let Some(camera) = state.camera else {
                return;
            };
            if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                let factor = if e.unit == bevy::input::mouse::MouseScrollUnit::Line {
                    0.1
                } else {
                    0.003
                };
                orbit.distance = (orbit.distance * (-e.y * factor).exp()).clamp(0.01, 1e6);
                *transform = orbit.transform();
            }
        },
    );
}

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct View<'w, 's> {
    images: Option<ResMut<'w, Assets<Image>>>,
    cameras: Query<
        'w,
        's,
        (
            &'static mut Orbit,
            &'static mut Transform,
            &'static mut bevy::prelude::Camera,
        ),
        With<Camera>,
    >,
    worlds: Query<'w, 's, (Entity, &'static WorldOwner)>,
    texts: Query<'w, 's, &'static mut Text>,
    clock: Option<ResMut<'w, PreviewClock>>,
    diagnostics: Option<Res<'w, scenemax_effects::Diagnostics>>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
    lines: Gizmos<'w, 's, Lines>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut states: Query<(Entity, &EditorHost, &mut Import)>,
    session: Res<Session>,
    time: Res<Time>,
    mut view: View,
) {
    for (world, owner) in &view.worlds {
        if states.get(owner.0).is_err() {
            commands.entity(world).try_despawn();
        }
    }
    if let Some(clock) = view.clock.as_deref_mut() {
        clock.delta_seconds = None;
    }
    for (host, owner, mut state) in &mut states {
        if session.workspace.active_id() != Some(owner.0) {
            if let Some(root) = state.world.take() {
                commands.entity(root).try_despawn();
                state.camera = None;
                state.effect = None;
            }
            continue;
        }
        let Ok(doc) = session.workspace.document(owner.0) else {
            continue;
        };
        let Ok(draft) = serde_json::from_str::<Value>(doc.text()) else {
            continue;
        };
        let Some(parts) = state.parts else {
            continue;
        };
        let Some(images) = view.images.as_deref_mut() else {
            continue;
        };
        let n = |key: &str| draft[key].as_f64().unwrap_or(0.) as f32;
        if state.world.is_none() {
            let root = commands
                .spawn((
                    Transform::default(),
                    Visibility::default(),
                    WorldOwner(host),
                ))
                .id();
            let mut image = Image::new_uninit(
                Extent3d {
                    width: 900,
                    height: 640,
                    depth_or_array_layers: 1,
                },
                TextureDimension::D2,
                TextureFormat::Bgra8UnormSrgb,
                RenderAssetUsages::all(),
            );
            image.texture_descriptor.usage = TextureUsages::TEXTURE_BINDING
                | TextureUsages::COPY_DST
                | TextureUsages::COPY_SRC
                | TextureUsages::RENDER_ATTACHMENT;
            let target = images.add(image);
            let orbit = Orbit {
                target: Vec3::Y,
                distance: 15.,
                yaw: 0.35,
                pitch: 0.2,
            };
            let camera = commands
                .spawn((
                    Camera,
                    Camera3d::default(),
                    RenderLayers::layer(4),
                    bevy::prelude::Camera {
                        order: -3,
                        ..default()
                    },
                    RenderTarget::Image(target.clone().into()),
                    orbit,
                    orbit.transform(),
                    ChildOf(root),
                ))
                .id();
            commands
                .entity(parts.viewport)
                .insert(ViewportNode::new(camera));
            super::super::scene3d::navigation::spawn(&mut commands, parts.viewport, camera);
            state.world = Some(root);
            state.camera = Some(camera);
            state.target = Some(target);
            state.effect = None;
        }
        if let Some(camera) = state.camera
            && let Ok((mut orbit, mut transform, mut camera)) = view.cameras.get_mut(camera)
        {
            camera.clear_color =
                ClearColorConfig::Custom(match draft["background"].as_str().unwrap_or_default() {
                    "black" => Color::BLACK,
                    "white" => Color::WHITE,
                    "gray" => Color::srgb(0.4, 0.4, 0.4),
                    _ => Color::srgb_u8(27, 30, 36),
                });
            if state.home {
                orbit.target = Vec3::new(n("x"), n("y") + n("scale"), n("z"));
                orbit.distance = 15. * n("scale").max(0.1);
                *transform = orbit.transform();
                state.home = false;
            }
        }
        if draft["grid"].as_bool().unwrap_or(true) {
            let extent = 10. * n("scale").max(0.1);
            for i in -10..=10 {
                let p = i as f32 * extent / 10.;
                let color = if i == 0 {
                    Color::srgb(0.34, 0.38, 0.44)
                } else {
                    Color::srgb(0.18, 0.21, 0.26)
                };
                view.lines
                    .line(Vec3::new(p, 0., -extent), Vec3::new(p, 0., extent), color);
                view.lines
                    .line(Vec3::new(-extent, 0., p), Vec3::new(extent, 0., p), color);
            }
        }
        if let Some(p) = state.prepared.clone()
            && p.complete()
        {
            let Some(world) = state.world else {
                continue;
            };
            let entity = if let Some(e) = state.effect {
                e
            } else {
                let e = commands
                    .spawn((Transform::default(), RenderLayers::layer(4), ChildOf(world)))
                    .id();
                state.effect = Some(e);
                e
            };
            let delta = if state.seeking {
                state.seeking = false;
                0.
            } else if state.step {
                1. / 60.
            } else if state.playing {
                time.delta_secs().min(0.1) * n("speed")
            } else {
                0.
            };
            state.step = false;
            state.frame += delta * 60.;
            if state.frame >= n("duration") {
                if draft["loop"].as_bool().unwrap_or(true) {
                    state.frame = 0.;
                    state.generation += 1;
                    state.seek_frame = 0.;
                    state.seek_generation += 1;
                } else {
                    state.frame = n("duration");
                    state.playing = false;
                }
            }
            if let Some(clock) = view.clock.as_deref_mut() {
                clock.delta_seconds = Some(delta);
            }
            commands
                .entity(entity)
                .insert(scenemax_effects::PreviewOptions {
                    seed: n("seed") as i32,
                    seek_generation: state.seek_generation,
                    seek_frame: state.seek_frame,
                    target: [n("targetX"), n("targetY"), n("targetZ")],
                    color: [
                        n("red") as u8,
                        n("green") as u8,
                        n("blue") as u8,
                        n("alpha") as u8,
                    ],
                    triggers: state.triggers,
                });
            commands.entity(entity).insert((
                Effect {
                    instance_id: entity.to_bits(),
                    asset_id: state.source.clone(),
                    effect_path: Some(p.runtime.clone()),
                    one_shot_duration_seconds: n("duration") / 60.,
                },
                Playback {
                    looped: false,
                    play_generation: state.generation,
                    playback_speed: 1.,
                    dynamic_inputs: [n("input0"), n("input1"), n("input2"), n("input3")],
                    elapsed_seconds: state.frame / 60.,
                },
                Transform::from_translation(Vec3::new(n("x"), n("y"), n("z")))
                    .with_scale(Vec3::splat(n("scale")))
                    .with_rotation(Quat::from_euler(
                        EulerRot::XYZ,
                        n("pitch").to_radians(),
                        n("yaw").to_radians(),
                        n("roll").to_radians(),
                    )),
            ));
        } else if let Some(e) = state.effect.take() {
            commands.entity(e).try_despawn();
        }
        if let Some(winit) = view.winit.as_deref_mut() {
            winit.focused_mode = bevy::winit::UpdateMode::reactive(
                std::time::Duration::from_millis(if state.playing { 16 } else { 100 }),
            );
        }
        if let Ok(mut t) = view.texts.get_mut(parts.timeline) {
            t.0 = format!(
                "{}  {:04.0} / {:.0} frames  ·  {:.2}s",
                if state.playing { "PLAYING" } else { "PAUSED" },
                state.frame,
                n("duration"),
                state.frame / 60.
            );
        }
        if let Some(d) = view.diagnostics.as_deref()
            && let Ok(message) = d.0.lock()
            && let Ok(mut t) = view.texts.get_mut(parts.diagnostics)
        {
            t.0 = format!("{message}  ·  right-drag orbit · middle-drag pan · wheel zoom");
        }
        if state.snapshot {
            state.snapshot = false;
            if let Some(target) = state.target.clone() {
                let stamp = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis();
                let path = session
                    .workspace
                    .project()
                    .root()
                    .join(format!("effect-preview-{stamp}.png"));
                commands
                    .spawn(bevy::render::view::screenshot::Screenshot::image(target))
                    .observe(bevy::render::view::screenshot::save_to_disk(path.clone()));
                state.status = format!("Snapshot requested: {}", path.display());
            }
        }
    }
}
