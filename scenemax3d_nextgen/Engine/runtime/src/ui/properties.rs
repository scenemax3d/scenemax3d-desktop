//! Shared normalization and typed application for native widget styling.
use super::*;

pub(crate) fn canonical(widget: &SceneMaxUiTarget, property: &str) -> Option<&'static str> {
    if widget.widget_type == "EDIT_TEXT" && let Some(property) = edits::canonical(property) { return Some(property); }
    if widget.widget_type == "LIST_VIEW" && let Some(property) = lists::canonical(property) { return Some(property); }
    match property.to_ascii_lowercase().as_str() {
        "text" => Some("text"),
        "value" if widget.widget_type == "EDIT_TEXT" => Some("text"),
        "visible" => Some("visible"),
        "frame" | "spriteframe" => Some("frame"),
        "image" | "imagepath" if widget.widget_type == "IMAGE" => Some("image"),
        "image" | "backgroundimage" if widget.widget_type == "PANEL" => Some("image"),
        "sprite" | "spritename" if widget.widget_type == "IMAGE" => Some("sprite"),
        "shader" if matches!(widget.widget_type.as_str(), "PANEL" | "BUTTON" | "IMAGE" | "TEXT_VIEW" | "LIST_VIEW" | "EDIT_TEXT") => Some("shader"),
        "font" | "fontname" if widget.text_entity.is_some() => Some("font"),
        "fontsize" if widget.text_entity.is_some() => Some("fontsize"),
        "textcolor" | "buttontextcolor" if widget.text_entity.is_some() => Some("textcolor"),
        "backgroundcolor" | "buttoncolor" | "edittextbackgroundcolor"
            if matches!(
                widget.widget_type.as_str(),
                "PANEL" | "BUTTON" | "EDIT_TEXT"
            ) =>
        {
            Some("backgroundcolor")
        }
        "color" => match widget.widget_type.as_str() {
            "TEXT_VIEW" | "EDIT_TEXT" => Some("textcolor"),
            "PANEL" | "BUTTON" => Some("backgroundcolor"),
            _ => None,
        },
        _ => None,
    }
}

pub(crate) fn color(value: &str) -> Option<Color> {
    let hex = value.trim().strip_prefix('#').unwrap_or(value.trim());
    if !matches!(hex.len(), 6 | 8) || !hex.bytes().all(|b| b.is_ascii_hexdigit()) {
        return None;
    }
    Srgba::hex(hex).ok().map(Color::Srgba)
}

pub(crate) fn is_style(property: &str) -> bool {
    matches!(
        property,
        "textcolor" | "backgroundcolor" | "fontsize" | "font"
    )
}

pub(crate) fn valid(property: &str, value: &str) -> bool {
    match property {
        "font" => !value.contains('\0'),
        "textcolor" | "backgroundcolor" => color(value).is_some(),
        "fontsize" => value
            .trim()
            .parse::<f32>()
            .is_ok_and(|n| n.is_finite() && n > 0. && n <= 4096.),
        _ => false,
    }
}

pub(super) fn queue(
    commands: &mut Commands,
    target: SceneMaxUiTarget,
    property: String,
    value: String,
    publish_shader: Option<String>,
) {
    commands.queue(move |world: &mut World| {
        if world.get_entity(target.entity).is_err() {
            return;
        }
        if property == "shader" {
            if target.widget_type == "IMAGE" {
                image_shaders::apply(world, target.entity, &value);
                return;
            }
            if shaders::apply(world, target.entity, &value) && let Some(path) = publish_shader {
                multiplayer::widgets::capture_shader(world, target.entity, &path);
            }
            return;
        }
        if property == "backgroundcolor" {
            if shaders::active(world, target.entity) { return; }
            if let Some(color) = color(&value) {
                world
                    .entity_mut(target.entity)
                    .insert(BackgroundColor(color));
                if let Some(path) = publish_shader {
                    multiplayer::widgets::capture_shader(world, target.entity, &path);
                }
            }
            return;
        }
        let Some(entity) = target.text_entity.filter(|e| world.get_entity(*e).is_ok()) else {
            return;
        };
        if property == "font" {
            switch_font(world, entity, &value);
            return;
        }
        let mut bitmap = world.get::<SceneMaxUiBitmapText>(entity).cloned();
        if property == "textcolor" {
            let Some(color) = color(&value) else {
                return;
            };
            if let Some(mut text) = world.get_mut::<TextColor>(entity) {
                text.0 = color;
            }
            if let Some(mut state) = world.get_mut::<SceneMaxUiTextVisualState>(entity) {
                state.base_color = color;
            }
            if let Some(mut animation) = world.get_mut::<SceneMaxUiMessageAnimation>(entity) {
                animation.base_color = color;
            }
            if let Some(bitmap) = bitmap.as_mut() {
                bitmap.color = color;
            }
        } else if property == "fontsize" {
            let Ok(size) = value.trim().parse::<f32>() else {
                return;
            };
            let size = scaled_font_size(size, target.ui_scale);
            if let Some(mut font) = world.get_mut::<TextFont>(entity) {
                font.font_size = FontSize::Px(size);
            }
            if let Some(bitmap) = bitmap.as_mut() {
                bitmap.font_size = size;
            }
        }
        if let Some(mut bitmap) = bitmap {
            world.resource_scope(|world, runtime: Mut<SceneMaxUiRuntime>| {
                render_scenemax_bitmap_text(&mut world.commands(), entity, &mut bitmap, &runtime);
            });
            world.entity_mut(entity).insert(bitmap);
        }
    });
}

#[derive(Component, Clone, Copy)]
pub(super) struct FontLayout {
    pub width: f32,
    pub height: f32,
    pub alignment: Justify,
}

fn switch_font(world: &mut World, entity: Entity, name: &str) {
    let Some(layout) = world.get::<FontLayout>(entity).copied() else {
        return;
    };
    let previous = world.get::<SceneMaxUiBitmapText>(entity).cloned();
    let (text, size, color) = if let Some(bitmap) = &previous {
        (bitmap.text.clone(), bitmap.font_size, bitmap.color)
    } else {
        let Some(text) = world.get::<Text>(entity) else {
            return;
        };
        let Some(font) = world.get::<TextFont>(entity) else {
            return;
        };
        let FontSize::Px(size) = font.font_size else {
            return;
        };
        (
            text.0.clone(),
            size,
            world
                .get::<TextColor>(entity)
                .map(|color| color.0)
                .unwrap_or(Color::WHITE),
        )
    };
    let color = world
        .get::<SceneMaxUiTextVisualState>(entity)
        .map(|state| state.base_color)
        .unwrap_or(color);
    let font_name = world.resource_scope(|world, mut runtime: Mut<SceneMaxUiRuntime>| {
        ensure_scenemax_bitmap_font(
            Some(name),
            &mut runtime,
            world.resource::<AssetServer>(),
            world.resource::<SceneMaxLaunchContext>(),
        )
    });
    if let Some(previous) = previous {
        for glyph in previous.glyph_entities {
            let _ = world.despawn(glyph);
        }
    }
    if let Some(font_name) = font_name {
        let mut bitmap = SceneMaxUiBitmapText {
            text,
            font_name,
            font_size: size,
            color,
            alignment: layout.alignment,
            widget_width: layout.width,
            widget_height: layout.height,
            glyph_entities: Vec::new(),
        };
        world
            .entity_mut(entity)
            .remove::<(Text, TextFont, TextColor, TextLayout)>();
        world.resource_scope(|world, runtime: Mut<SceneMaxUiRuntime>| {
            render_scenemax_bitmap_text(&mut world.commands(), entity, &mut bitmap, &runtime);
        });
        world.entity_mut(entity).insert(bitmap);
    } else {
        world
            .entity_mut(entity)
            .remove::<SceneMaxUiBitmapText>()
            .insert((
                Text::new(text),
                TextFont {
                    font_size: FontSize::Px(size),
                    ..default()
                },
                TextColor(color),
                TextLayout::justify(layout.alignment).with_no_wrap(),
            ));
    }
    write_runtime_diagnostic_line(format!("UI:FONT name={name} target={entity:?}"));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn font_switch_preserves_style_and_removes_old_glyphs_on_fallback() {
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, bevy::asset::AssetPlugin::default()));
        app.init_resource::<SceneMaxUiRuntime>();
        app.insert_resource(SceneMaxLaunchContext {
            script_root: None,
            asset_root: None,
            builtin_asset_root: None,
            window_width: 640,
            window_height: 360,
        });
        let metrics = scenemax_runtime_ui_core::bitmap::parse(
            "info size=16\ncommon lineHeight=16\npage id=0 file=fixture.png\nchar id=65 x=0 y=0 width=8 height=16 xadvance=8").unwrap();
        let world = app.world_mut();
        for key in ["custom", "second"] {
            world
                .resource_mut::<SceneMaxUiRuntime>()
                .bitmap_fonts
                .insert(
                    key.into(),
                    SceneMaxBitmapFont {
                                    images: HashMap::from([(0,Handle::default())]),
                        metrics: metrics.clone(),
                    },
                );
        }
        let color = Color::srgb(0.2, 0.6, 0.9);
        let entity = world
            .spawn((
                Text::new("A"),
                TextFont {
                    font_size: FontSize::Px(26.),
                    ..default()
                },
                TextColor(color),
                FontLayout {
                    width: 200.,
                    height: 40.,
                    alignment: Justify::Right,
                },
            ))
            .id();
        for name in ["CUSTOM", "second"] {
            switch_font(world, entity, name);
            world.flush();
            let bitmap = world.get::<SceneMaxUiBitmapText>(entity).unwrap();
            assert_eq!(bitmap.text, "A");
            assert_eq!(bitmap.font_size, 26.);
            assert_eq!(bitmap.color, color);
            assert_eq!(bitmap.alignment, Justify::Right);
            assert_eq!(bitmap.font_name, name.to_lowercase());
            assert!(world.get::<Text>(entity).is_none());
            assert_eq!(world.get::<Children>(entity).unwrap().len(), 1);
        }
        let glyph = world
            .get::<SceneMaxUiBitmapText>(entity)
            .unwrap()
            .glyph_entities[0];
        switch_font(world, entity, "missing");
        world.flush();
        assert!(world.get_entity(glyph).is_err());
        assert!(world.get::<SceneMaxUiBitmapText>(entity).is_none());
        assert_eq!(world.get::<Text>(entity).unwrap().0, "A");
        assert_eq!(
            world.get::<TextFont>(entity).unwrap().font_size,
            FontSize::Px(26.)
        );
        assert_eq!(world.get::<TextColor>(entity).unwrap().0, color);
        switch_font(world, entity, "custom");
        world.flush();
        switch_font(world, entity, "");
        world.flush();
        assert!(world.get::<SceneMaxUiBitmapText>(entity).is_none());
        assert!(valid("font", ""));
        assert!(!valid("font", "bad\0name"));
    }
}
