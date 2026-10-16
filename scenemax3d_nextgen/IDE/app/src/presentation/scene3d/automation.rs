use super::*;
use crate::application::mcp;
use bevy::render::render_resource::Extent3d;
use serde_json::{Value, json};
#[derive(Component)]
pub(crate) struct ReferenceOverlay;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct RenderInputs<'w, 's> {
    images: ResMut<'w, Assets<Image>>,
    server: Res<'w, AssetServer>,
    models: Query<'w, 's, &'static WorldAssetRoot>,
}
/// Adapt agent view requests to the same retained scene camera and inspector.
pub(crate) fn update(
    mut commands: Commands,
    mut automation: ResMut<mcp::State>,
    mut state: ResMut<SceneState>,
    mut cameras: Query<(&mut Transform, &mut Orbit, &RenderTarget)>,
    viewports: Query<(Entity, &super::gizmo::Viewport)>,
    overlays: Query<Entity, With<ReferenceOverlay>>,
    render: RenderInputs,
) {
    let RenderInputs {
        mut images,
        server,
        models,
    } = render;
    let Some((name, args)) = automation.view_request.take() else {
        return;
    };
    let result = (|| -> Result<Option<Value>, String> {
        if state.current != state.completed {
            return Err("Scene preview is still loading; retry capture/view request".into());
        }
        let scene = state
            .scene
            .as_ref()
            .ok_or("Open a 3D scene designer first")?;
        let camera = state.camera.ok_or("Scene camera not ready")?;
        let (mut transform, mut orbit, target) =
            cameras.get_mut(camera).map_err(|e| e.to_string())?;
        if name == "designer.overlay_reference_image" {
            for overlay in &overlays {
                commands.entity(overlay).despawn();
            }
            if args["visible"] != false {
                let data = automation
                    .overlay_image
                    .take()
                    .ok_or("Load a reference image first")?;
                let parent = viewports
                    .iter()
                    .find(|(_, v)| v.0 == camera)
                    .map(|(e, _)| e)
                    .ok_or("Viewport unavailable")?;
                let image = Image::new(
                    Extent3d {
                        width: data.width,
                        height: data.height,
                        depth_or_array_layers: 1,
                    },
                    TextureDimension::D2,
                    data.pixels,
                    TextureFormat::Rgba8UnormSrgb,
                    RenderAssetUsages::default(),
                );
                let handle = images.add(image);
                commands.spawn((
                    ReferenceOverlay,
                    ImageNode::new(handle).with_color(Color::srgba(
                        1.,
                        1.,
                        1.,
                        args["opacity"].as_f64().unwrap_or(0.35).clamp(0., 1.) as f32,
                    )),
                    Node {
                        position_type: PositionType::Absolute,
                        width: percent(100.),
                        height: percent(100.),
                        ..default()
                    },
                    Pickable::IGNORE,
                    ZIndex(3),
                    ChildOf(parent),
                ));
            }
            return Ok(Some(
                json!({"visible":args["visible"]!=false,"previewOnly":true}),
            ));
        }
        if name == "designer.select_entity" {
            let id = args["entity_id"].as_str();
            let name = args["entity_name"].as_str();
            let matches: Vec<_> = scene
                .entities
                .iter()
                .enumerate()
                .filter(|(_, e)| {
                    id.is_some_and(|id| id == e.id) || name.is_some_and(|n| n == e.name)
                })
                .map(|(i, _)| i)
                .collect();
            if matches.len() != 1 {
                return Err("Selector must match exactly one entity".into());
            }
            let selected = matches[0];
            if let Some(parts) = state.parts {
                view::inspect(&mut commands, parts, scene, selected);
            }
            state.selected = selected;
        } else if name == "designer.adjust_view" {
            if let Some(v) = args["cameraTarget"].as_array() {
                orbit.target = Vec3::new(
                    v[0].as_f64().unwrap_or(0.) as f32,
                    v[1].as_f64().unwrap_or(0.) as f32,
                    v[2].as_f64().unwrap_or(0.) as f32,
                );
            }
            if let Some(v) = args["cameraDistance"].as_f64() {
                orbit.distance = (v as f32).clamp(0.05, 100_000.);
            }
            if let Some(v) = args["cameraYawDegrees"].as_f64() {
                orbit.yaw = (v as f32).to_radians();
            }
            if let Some(v) = args["cameraPitchDegrees"].as_f64() {
                orbit.pitch = (v as f32).to_radians().clamp(-1.569, 1.569);
            }
            *transform = orbit.transform();
        } else if name == "designer.use_view_gizmo" {
            let (y, p) = match args["preset"].as_str() {
                Some("front") => (0., 0.),
                Some("back") => (std::f32::consts::PI, 0.),
                Some("left") => (-std::f32::consts::FRAC_PI_2, 0.),
                Some("right") => (std::f32::consts::FRAC_PI_2, 0.),
                Some("top") => (0., 1.569),
                Some("bottom") => (0., -1.569),
                _ => return Err("Unknown view".into()),
            };
            orbit.yaw = y;
            orbit.pitch = p;
            *transform = orbit.transform();
        } else if name.starts_with("designer.capture_") {
            for model in &models {
                if !server.is_loaded_with_dependencies(model.0.id()) {
                    return Err("Model assets are loading or failed; inspect scene diagnostics and retry capture".into());
                }
            }
            let thumbnail = name == "designer.capture_thumbnail";
            let capture = bevy::render::view::screenshot::Screenshot(target.clone());
            commands.spawn(capture).observe(
                move |event: On<bevy::render::view::screenshot::ScreenshotCaptured>,
                      mut state: ResMut<mcp::State>| {
                    let result = event
                        .image
                        .clone()
                        .try_into_dynamic()
                        .map_err(|e| e.to_string())
                        .and_then(|image| {
                            let image = if thumbnail {
                                image.thumbnail(320, 240)
                            } else {
                                image
                            };
                            let rgba = image.to_rgba8();
                            scenemax_ide_services::mcp::encode_image(
                                rgba.width(),
                                rgba.height(),
                                rgba.into_raw(),
                            )
                        });
                    state.view_result = Some(result);
                },
            );
            return Ok(None);
        }
        let scene = state.scene.as_ref().ok_or("Scene unavailable")?;
        Ok(Some(
            json!({"cameraTarget":orbit.target.to_array(),"cameraDistance":orbit.distance,"cameraYawDegrees":orbit.yaw.to_degrees(),"cameraPitchDegrees":orbit.pitch.to_degrees(),"selected":scene.entities.get(state.selected).map(|e|json!({"id":e.id,"name":e.name})),"position":transform.translation.to_array()}),
        ))
    })();
    match result {
        Ok(Some(v)) => automation.view_result = Some(Ok(v)),
        Err(e) => automation.view_result = Some(Err(e)),
        Ok(None) => {}
    }
}
