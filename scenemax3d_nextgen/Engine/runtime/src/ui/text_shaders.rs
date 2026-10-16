//! Glyph materials preserve atlas coverage; no rectangle is drawn behind text.
use super::*;
use bevy::text::TextLayoutInfo;
use std::hash::{Hash, Hasher};
#[derive(Component, Clone)]
struct TextShader {
    name: String,
    original: Color,
    tint: Color,
    signature: u64,
    visuals: Vec<Entity>,
}
#[derive(Component)]
struct BitmapMaterial {
    original: Color,
    name: String,
}
pub(super) fn is_text(world: &World, entity: Entity) -> bool {
    if let Some(target) = world.get_resource::<SceneMaxUiRuntime>().and_then(|runtime| {
        runtime.loaded.values().flat_map(|loaded| loaded.targets.values()).find(|target| target.entity == entity)
    }) {
        return target.widget_type == "TEXT_VIEW";
    }
    world.get::<Text>(entity).is_some() || world.get::<SceneMaxUiBitmapText>(entity).is_some()
}
pub(super) fn snapshot(world: &World, entity: Entity) -> Option<shaders::Snapshot> {
    let state = world.get::<TextShader>(entity)?;
    Some(shaders::Snapshot {
        name: state.name.clone(),
        captured: true,
        original: Some(state.original.to_srgba().to_f32_array()),
        current: Some(state.tint.to_srgba().to_f32_array()),
    })
}
pub(super) fn restore(world: &mut World, entity: Entity, state: shaders::Snapshot) {
    if !state.valid()
        || (!state.name.is_empty()
            && crate::shader::resolve_ui_effects(
                &state.name,
                world.resource::<SceneMaxLaunchContext>(),
            )
            .is_none())
    {
        return;
    }
    if apply(world, entity, &state.name) {
        if let Some(mut shader) = world.get_mut::<TextShader>(entity) {
            if let Some(c) = state.original {
                shader.original = Color::srgba(c[0], c[1], c[2], c[3]);
            }
            if let Some(c) = state.current {
                shader.tint = Color::srgba(c[0], c[1], c[2], c[3]);
            }
            shader.signature = 0;
        }
    }
}
pub(super) fn apply(world: &mut World, entity: Entity, name: &str) -> bool {
    if !is_text(world, entity) {
        return false;
    }
    if !name.trim().is_empty()
        && (crate::shader::resolve_ui_effects(name, world.resource::<SceneMaxLaunchContext>())
            .is_none()
            || !world.contains_resource::<Assets<shaders::SurfaceShader>>())
    {
        return false;
    }
    let mut state = world.get::<TextShader>(entity).cloned().unwrap_or_else(|| {
        let color = world
            .get::<SceneMaxUiBitmapText>(entity)
            .map(|b| b.color)
            .or_else(|| world.get::<TextColor>(entity).map(|c| c.0))
            .unwrap_or(Color::WHITE);
        TextShader {
            name: String::new(),
            original: color,
            tint: color,
            signature: 0,
            visuals: Vec::new(),
        }
    });
    state.name = name.trim().to_owned();
    state.signature = 0;
    if state.name.is_empty() {
        for child in state.visuals.drain(..) {
            let _ = world.despawn(child);
        }
        if let Some(mut color) = world.get_mut::<TextColor>(entity) {
            color.0 = state.original;
        }
        let children = world
            .get::<Children>(entity)
            .map(|c| c.to_vec())
            .unwrap_or_default();
        for child in children {
            if let Some(saved) = world.get::<BitmapMaterial>(child).map(|b| b.original) {
                if let Some(mut image) = world.get_mut::<ImageNode>(child) {
                    image.color = saved;
                }
                world
                    .entity_mut(child)
                    .remove::<(BitmapMaterial, MaterialNode<shaders::SurfaceShader>)>();
            }
        }
    }
    write_runtime_diagnostic_line(format!("UI:TEXT_SHADER name={name} target={entity:?}"));
    world.entity_mut(entity).insert(state);
    true
}
pub(crate) fn refresh(world: &mut World) {
    let covered: Vec<_> = world
        .query_filtered::<Entity, (
            With<MaterialNode<shaders::SurfaceShader>>,
            With<BackgroundColor>,
        )>()
        .iter(world)
        .collect();
    for entity in covered {
        world.entity_mut(entity).remove::<BackgroundColor>();
    }
    let entities: Vec<_> = world
        .query_filtered::<Entity, With<TextShader>>()
        .iter(world)
        .collect();
    for entity in entities {
        let Some(mut state) = world.get::<TextShader>(entity).cloned() else {
            continue;
        };
        if state.name.is_empty() {
            continue;
        }
        if world.get::<SceneMaxUiBitmapText>(entity).is_some() {
            let children = world
                .get::<Children>(entity)
                .map(|c| c.to_vec())
                .unwrap_or_default();
            for child in children {
                if world
                    .get::<BitmapMaterial>(child)
                    .is_some_and(|m| m.name == state.name)
                {
                    continue;
                }
                let Some(image) = world.get::<ImageNode>(child).cloned() else {
                    continue;
                };
                let Some(size) = world
                    .resource::<Assets<Image>>()
                    .get(&image.image)
                    .map(|i| i.size().as_vec2())
                else {
                    continue;
                };
                let rect = image.rect.unwrap_or(Rect::from_corners(Vec2::ZERO, size));
                let tint = world
                    .get::<BitmapMaterial>(child)
                    .map(|m| m.original)
                    .unwrap_or(image.color);
                let Some(material) = shaders::SurfaceShader::configured(
                    &state.name,
                    world.resource::<SceneMaxLaunchContext>(),
                    Some(image.image.clone()),
                ) else {
                    continue;
                };
                let uv = Vec4::new(
                    rect.min.x / size.x,
                    rect.min.y / size.y,
                    rect.max.x / size.x,
                    rect.max.y / size.y,
                );
                let handle = world
                    .resource_mut::<Assets<shaders::SurfaceShader>>()
                    .add(material.glyph(uv, tint, false));
                world.get_mut::<ImageNode>(child).unwrap().color = Color::NONE;
                world.entity_mut(child).insert((
                    MaterialNode(handle),
                    BitmapMaterial {
                        original: tint,
                        name: state.name.clone(),
                    },
                ));
            }
            continue;
        }
        let Some(info) = world.get::<TextLayoutInfo>(entity).cloned() else {
            continue;
        };
        if let Some(color) = world
            .get::<TextColor>(entity)
            .map(|c| c.0)
            .filter(|c| *c != Color::NONE)
        {
            state.tint = color;
        }
        let scale = world
            .get::<ComputedNode>(entity)
            .map_or(1., |n| n.inverse_scale_factor());
        let mut hash = std::collections::hash_map::DefaultHasher::new();
        state.name.hash(&mut hash);
        scale.to_bits().hash(&mut hash);
        for v in state.tint.to_srgba().to_f32_array() {
            v.to_bits().hash(&mut hash);
        }
        for g in &info.glyphs {
            g.atlas_info.texture.hash(&mut hash);
            for v in [
                g.position.x,
                g.position.y,
                g.atlas_info.rect.min.x,
                g.atlas_info.rect.min.y,
                g.atlas_info.rect.max.x,
                g.atlas_info.rect.max.y,
            ] {
                v.to_bits().hash(&mut hash);
            }
        }
        let signature = hash.finish();
        if signature == state.signature {
            continue;
        }
        // Wait for all atlas pages before hiding the ordinary glyph rendering.
        if info.glyphs.iter().any(|g| {
            !world
                .resource::<Assets<Image>>()
                .contains(g.atlas_info.texture)
        }) {
            continue;
        }
        for child in state.visuals.drain(..) {
            let _ = world.despawn(child);
        }
        for glyph in &info.glyphs {
            let atlas = &glyph.atlas_info;
            let size = world
                .resource::<Assets<Image>>()
                .get(atlas.texture)
                .unwrap()
                .size()
                .as_vec2();
            let texture = world
                .resource_mut::<Assets<Image>>()
                .get_strong_handle(atlas.texture);
            let Some(material) = shaders::SurfaceShader::configured(
                &state.name,
                world.resource::<SceneMaxLaunchContext>(),
                texture,
            ) else {
                continue;
            };
            let r = atlas.rect;
            let uv = Vec4::new(
                r.min.x / size.x,
                r.min.y / size.y,
                r.max.x / size.x,
                r.max.y / size.y,
            );
            let handle = world
                .resource_mut::<Assets<shaders::SurfaceShader>>()
                .add(material.glyph(uv, state.tint, atlas.is_alpha_mask));
            let pos = (glyph.position - r.size() * 0.5) * scale;
            let extent = r.size() * scale;
            let child = world
                .spawn((
                    Node {
                        position_type: PositionType::Absolute,
                        left: Val::Px(pos.x),
                        top: Val::Px(pos.y),
                        width: Val::Px(extent.x),
                        height: Val::Px(extent.y),
                        ..default()
                    },
                    MaterialNode(handle),
                    bevy::ui::FocusPolicy::Pass,
                    ChildOf(entity),
                ))
                .id();
            state.visuals.push(child);
        }
        if let Some(mut color) = world.get_mut::<TextColor>(entity) {
            color.0 = Color::NONE;
        }
        state.signature = signature;
        world.entity_mut(entity).insert(state);
    }
}
