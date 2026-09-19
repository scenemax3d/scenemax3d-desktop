//! GPU images and retained preview updates; no runtime or project mutations.
use super::*;
use bevy::{
    asset::RenderAssetUsages,
    image::ImageSampler,
    render::render_resource::{Extent3d, TextureDimension, TextureFormat},
};
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct View<'w, 's> {
    images: Option<ResMut<'w, Assets<Image>>>,
    pictures: Query<'w, 's, &'static mut ImageNode>,
    nodes: Query<'w, 's, &'static mut Node>,
    canvases: Query<'w, 's, &'static mut scenemax_ide_ui::canvas::Canvas>,
    texts: Query<'w, 's, &'static mut Text>,
    children: Query<'w, 's, &'static Children>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
    time: Res<'w, Time>,
}
fn rect(layout: schema::Layout, index: u32) -> Rect {
    let [x, y, w, h] = layout.rect(index);
    Rect::new(x as f32, y as f32, (x + w) as f32, (y + h) as f32)
}
fn image(width: u32, height: u32, rgba: Vec<u8>) -> Image {
    let mut image = Image::new(
        Extent3d {
            width,
            height,
            depth_or_array_layers: 1,
        },
        TextureDimension::D2,
        rgba,
        TextureFormat::Rgba8UnormSrgb,
        RenderAssetUsages::all(),
    );
    image.sampler = ImageSampler::nearest();
    image
}
pub(crate) fn update(
    mut commands: Commands,
    mut states: Query<(Entity, &EditorHost, &mut Import)>,
    session: Res<Session>,
    mut view: View,
    mut previous: Local<Option<bevy::winit::UpdateMode>>,
    mut checker: Local<Option<Handle<Image>>>,
) {
    let mut playing = false;
    if checker.is_none()
        && let Some(images) = view.images.as_mut()
    {
        let mut pixels = Vec::new();
        for y in 0..16 {
            for x in 0..16 {
                let n = if (x / 8 + y / 8) % 2 == 0 { 49 } else { 62 };
                pixels.extend([n, n, n, 255]);
            }
        }
        *checker = Some(images.add(image(16, 16, pixels)));
    }
    for (host, owner, mut state) in &mut states {
        if session.workspace.active_id() != Some(owner.0) {
            continue;
        }
        let Some(parts) = state.parts else { continue };
        let Ok(doc) = session.workspace.document(owner.0) else {
            continue;
        };
        let Ok(draft) = serde_json::from_str::<Value>(doc.text()) else {
            continue;
        };
        if let Some(checker) = checker.as_ref() {
            for background in [parts.background, parts.frame_background] {
                if let Ok(picture) = view.pictures.get(background) {
                    if picture.image != *checker {
                        commands.entity(background).insert(
                            ImageNode::new(checker.clone()).with_mode(NodeImageMode::Tiled {
                                tile_x: true,
                                tile_y: true,
                                stretch_value: 1.,
                            }),
                        );
                    }
                } else {
                    commands
                        .entity(background)
                        .insert(
                            ImageNode::new(checker.clone()).with_mode(NodeImageMode::Tiled {
                                tile_x: true,
                                tile_y: true,
                                stretch_value: 1.,
                            }),
                        );
                }
            }
        }
        let Some(prepared) = state.prepared.clone() else {
            for e in [parts.cells, parts.film] {
                if view.children.get(e).is_ok_and(|c| !c.is_empty()) {
                    commands.entity(e).despawn_children();
                }
            }
            for e in [parts.sheet, parts.frame] {
                if let Ok(mut p) = view.pictures.get_mut(e) {
                    p.image = ImageNode::default().image;
                }
            }
            for e in [parts.cells, parts.selected] {
                if let Ok(mut node) = view.nodes.get_mut(e) {
                    node.display = Display::None;
                }
            }
            continue;
        };
        if state.image.is_none()
            && let Some(images) = view.images.as_mut()
        {
            state.image = Some(images.add(image(
                prepared.width,
                prepared.height,
                prepared.rgba.clone(),
            )));
            state.filter.clear();
        }
        let Some(handle) = state.image.clone() else {
            continue;
        };
        let filter = draft["filter"].as_str().unwrap_or("nearest");
        if filter != state.filter {
            if let Some(images) = view.images.as_mut()
                && let Some(mut image) = images.get_mut(&handle)
            {
                image.sampler = if filter == "nearest" {
                    ImageSampler::nearest()
                } else {
                    ImageSampler::linear()
                };
            }
            state.filter = filter.into();
        }
        if let Ok(mut p) = view.pictures.get_mut(parts.sheet)
            && p.image != handle
        {
            p.image = handle.clone();
            p.image_mode = NodeImageMode::Stretch;
        }
        if let Ok(mut canvas) = view.canvases.get_mut(parts.sheet) {
            canvas.resize(prepared.width as f32, prepared.height as f32);
        }
        let Some(layout) = state.layout else {
            for e in [parts.cells, parts.selected] {
                if let Ok(mut node) = view.nodes.get_mut(e) {
                    node.display = Display::None;
                }
            }
            if let Ok(mut p) = view.pictures.get_mut(parts.frame) {
                p.image = ImageNode::default().image;
            }
            continue;
        };
        let grid = draft["grid"].as_bool().unwrap_or(true);
        if state.drawn != Some((layout, grid)) {
            commands.entity(parts.cells).despawn_children();
            commands.entity(parts.film).despawn_children();
            if grid {
                for index in 0..layout.count() {
                    let [x, y, w, h] = layout.rect(index);
                    commands.spawn((
                        Node {
                            position_type: PositionType::Absolute,
                            left: percent(x as f32 / prepared.width as f32 * 100.),
                            top: percent(y as f32 / prepared.height as f32 * 100.),
                            width: percent(w as f32 / prepared.width as f32 * 100.),
                            height: percent(h as f32 / prepared.height as f32 * 100.),
                            border: px(1.).all(),
                            ..default()
                        },
                        BorderColor::all(Color::srgba(0.5, 0.72, 0.9, 0.6)),
                        Pickable::IGNORE,
                        ChildOf(parts.cells),
                    ));
                }
            }
            for index in 0..layout.count().min(128) {
                view::thumbnail(
                    &mut commands,
                    parts.film,
                    host,
                    index,
                    handle.clone(),
                    rect(layout, index),
                );
            }
            if layout.count() > 128 {
                commands.spawn((
                    label("More frames: select directly in the sheet", 11.),
                    ChildOf(parts.film),
                ));
            }
            state.drawn = Some((layout, grid));
        }
        if let Ok(mut node) = view.nodes.get_mut(parts.cells) {
            node.display = Display::Flex;
        }
        let first = schema::integer(&draft, "first").unwrap_or(0);
        let last = schema::integer(&draft, "last").unwrap_or(0);
        let range_valid = first <= last && last < layout.count();
        if !range_valid {
            state.playing = false;
            state.status = format!("Preview range must be within 0–{}", layout.count() - 1);
        }
        if state.playing && range_valid {
            state.elapsed += view.time.delta_secs_f64();
            let (frame, done) = schema::playback_frame(
                state.elapsed,
                draft["fps"].as_f64().unwrap_or(12.),
                first,
                last,
                draft["mode"].as_str().unwrap_or("loop"),
            );
            state.frame = frame;
            if done {
                state.playing = false;
            }
            playing |= state.playing;
        }
        state.frame = state.frame.min(layout.count() - 1);
        let region = rect(layout, state.frame);
        if let Ok(mut p) = view.pictures.get_mut(parts.frame) {
            if p.image != handle {
                p.image = handle;
            }
            if p.rect != Some(region) {
                p.rect = Some(region);
            }
            p.image_mode = NodeImageMode::Stretch;
        }
        if let Ok(mut canvas) = view.canvases.get_mut(parts.frame_canvas) {
            let width = draft["frameWidth"].as_f64().unwrap_or(1.);
            let height = draft["frameHeight"].as_f64().unwrap_or(0.);
            let preview_height = if height == 0. {
                layout.height as f32
            } else {
                layout.width as f32 * (height / width) as f32
            };
            canvas.resize(layout.width as f32, preview_height);
        }
        if let Ok(mut node) = view.nodes.get_mut(parts.selected) {
            node.display = Display::Flex;
            node.left = percent(region.min.x / prepared.width as f32 * 100.);
            node.top = percent(region.min.y / prepared.height as f32 * 100.);
            node.width = percent(layout.width as f32 / prepared.width as f32 * 100.);
            node.height = percent(layout.height as f32 / prepared.height as f32 * 100.);
        }
        if let Ok(mut text) = view.texts.get_mut(parts.info) {
            let width = draft["frameWidth"].as_f64().unwrap_or(1.);
            let height = draft["frameHeight"].as_f64().unwrap_or(0.);
            let height = if height == 0. {
                width * layout.height as f64 / layout.width as f64
            } else {
                height
            };
            let caption = format!(
                "Frame {} / {}
{} × {} pixels · {} × {} grid
Display {:.2} × {:.2} units",
                state.frame,
                layout.count() - 1,
                layout.width,
                layout.height,
                layout.cols,
                layout.rows,
                width,
                height
            );
            if text.0 != caption {
                text.0 = caption;
            }
        }
    }
    if let Some(winit) = view.winit.as_mut() {
        if playing {
            if previous.is_none() {
                *previous = Some(winit.focused_mode);
            }
            winit.focused_mode = bevy::winit::UpdateMode::Continuous;
        } else if let Some(mode) = previous.take() {
            winit.focused_mode = mode;
        }
    }
}
