//! Named project BMFonts share native UI catalog lookup and metrics.
use super::*;

pub(super) struct Glyph {
    pub texture: bevy::asset::AssetId<Image>,
    pub rect: Rect,
    pub position: Vec2,
    pub size: Vec2,
    pub is_alpha_mask: bool,
}

pub(crate) fn prepare(
    mut labels: Query<(&SceneMaxEntity, &mut Label)>,
    mut ui: ResMut<SceneMaxUiRuntime>,
    assets: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
) {
    if labels.is_empty() {
        return;
    }
    refresh_font_index(&mut ui, &context);
    for (scene, mut label) in &mut labels {
        if !label.font_ready {
            label.font_ready = true;
            label.bitmap_font =
                ensure_scenemax_bitmap_font(label.font.as_deref(), &mut ui, &assets, &context)
                    .and_then(|key| ui.bitmap_fonts.get(&key).cloned());
            if label
                .font
                .as_deref()
                .is_some_and(|name| !name.trim().is_empty())
                && label.bitmap_font.is_none()
            {
                tracing::warn!(entity=scene.name, font=?label.font, "Label font unavailable; using default font");
            }
            label.dirty = true;
        }
        if label.bitmap_font.as_ref().is_some_and(|font| {
            font.images.values().any(|image| {
                matches!(
                    assets.load_state(image.id()),
                    bevy::asset::LoadState::Failed(_)
                )
            })
        }) {
            tracing::warn!(entity=scene.name, font=?label.font, "Label font atlas failed to load; using default font");
            label.bitmap_font = None;
            label.dirty = true;
        }
    }
}

pub(super) fn bitmap_layout(font: &SceneMaxBitmapFont, text: &str) -> (Vec2, Vec<Glyph>) {
    let metrics = &font.metrics;
    let scale = 64. / metrics.size.max(1.);
    let lines: Vec<_> = text.split('\n').collect();
    let width = lines
        .iter()
        .map(|line| scenemax_runtime_ui_core::bitmap::line_width(metrics, line) * scale)
        .fold(0., f32::max);
    let height = metrics.line_height.max(metrics.size) * scale * lines.len() as f32;
    let glyphs =
        scenemax_runtime_ui_core::bitmap::layout_paged(metrics, text, 64., width, height, "center")
            .into_iter()
            .map(|glyph| {
                let rect = glyph.source;
                let [x, y, w, h] = glyph.rect;
                Glyph {
                    texture: font.images[&glyph.page].id(),
                    rect: Rect::new(rect[0], rect[1], rect[2], rect[3]),
                    position: Vec2::new(x + w / 2., y + h / 2.),
                    size: Vec2::new(w, h),
                    is_alpha_mask: true,
                }
            })
            .collect();
    (Vec2::new(width, height), glyphs)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn catalogs_preserve_strings_and_project_overrides() {
        let raw = r#"{fonts:[{"name":"Font", path:"fonts/name: value.fnt"}]}"#;
        let parsed = crate::ui::parse_font_catalog(raw).unwrap();
        assert_eq!(parsed["fonts"][0]["path"], "fonts/name: value.fnt");
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("fonts.json");
        fs::write(&file, raw).unwrap();
        let mut ui = SceneMaxUiRuntime::default();
        load_font_index_file(&file, "builtin://", &mut ui);
        assert_eq!(
            ui.font_index["font"].path,
            "builtin://fonts/name: value.fnt"
        );
        fs::write(
            &file,
            r#"{"fonts":[{name:"FONT",path:"fonts/override.fnt"}]}"#,
        )
        .unwrap();
        load_font_index_file(&file, "", &mut ui);
        assert_eq!(ui.font_index["font"].path, "fonts/override.fnt");
        let fonts = dir.path().join("fonts");
        fs::create_dir(&fonts).unwrap();
        fs::copy(&file, fonts.join("fonts.json")).unwrap();
        let mut ui = SceneMaxUiRuntime::default();
        let context = SceneMaxLaunchContext {
            script_root: None,
            asset_root: None,
            builtin_asset_root: Some(dir.path().to_owned()),
            window_width: 0,
            window_height: 0,
        };
        refresh_font_index(&mut ui, &context);
        assert_eq!(ui.font_index["font"].path, "builtin://fonts/override.fnt");
    }

    #[test]
    fn multi_page_label_glyphs_keep_distinct_images_and_kerning() {
        let metrics=scenemax_runtime_ui_core::bitmap::parse("info size=16\ncommon lineHeight=16\npage id=0 file=\"a.png\"\npage id=2 file=\"v.png\"\nchar id=65 width=8 height=12 xadvance=10 page=0\nchar id=86 width=8 height=12 xadvance=10 page=2\nkerning first=65 second=86 amount=-3").unwrap();
        let mut images = Assets::<Image>::default();
        let a = images.add(Image::default());
        let v = images.add(Image::default());
        let font = SceneMaxBitmapFont {
            images: HashMap::from([(0, a.clone()), (2, v.clone())]),
            metrics,
        };
        let (size, glyphs) = bitmap_layout(&font, "AV");
        assert_eq!(size.x, 68.);
        assert_eq!(glyphs[0].texture, a.id());
        assert_eq!(glyphs[1].texture, v.id());
        assert_eq!(glyphs[1].position.x - glyphs[0].position.x, 28.);
    }
    #[test]
    fn bitmap_geometry_scales_metrics_without_scaling_atlas_uvs() {
        let metrics = scenemax_runtime_ui_core::bitmap::parse("info size=16\ncommon lineHeight=20\npage id=0 file=\"atlas.png\"\nchar id=65 x=8 y=4 width=10 height=12 xoffset=1 yoffset=2 xadvance=12").unwrap();
        let font = SceneMaxBitmapFont {
            images: HashMap::from([(0, Handle::default())]),
            metrics,
        };
        let (size, glyphs) = bitmap_layout(&font, "AA\nA");
        assert_eq!(size, Vec2::new(76.8, 128.));
        assert_eq!(glyphs.len(), 3);
        assert_eq!(glyphs[0].rect, Rect::new(8., 4., 18., 16.));
        assert_eq!(glyphs[0].size, Vec2::new(32., 38.4));
        assert!((glyphs[2].position.x - size.x / 2.).abs() < 0.001);
    }
}
