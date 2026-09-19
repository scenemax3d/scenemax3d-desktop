//! Project asset resolution for the retained UI preview (runs on a disk task).
use super::*;
use std::{
    collections::HashMap,
    fs,
    path::{Path, PathBuf},
};
/// An image or a bitmap text atlas, resolved inside its owning asset root.
#[derive(Clone)]
pub struct Visual {
    /// Shared font metrics for immediate text and layout edits without disk access.
    pub font: Option<std::sync::Arc<scenemax_runtime_ui_core::bitmap::Font>>,
    /// Canonical resource directory.
    pub root: PathBuf,
    /// Canonical image file.
    pub path: PathBuf,
    /// Atlas source rectangles paired with local destination rectangles.
    pub quads: Vec<([f32; 4], [f32; 4])>,
    /// True when quads represent text glyphs.
    pub text: bool,
}
fn normalized(s: &str) -> String {
    s.replace('\\', "/")
        .trim_start_matches('/')
        .trim_start_matches("resources/")
        .into()
}
fn locate(root: &Path, path: &str) -> Option<PathBuf> {
    let path = root.join(normalized(path)).canonicalize().ok()?;
    (path.starts_with(root) && path.is_file()).then_some(path)
}
fn registry(root: &Path, file: &str, key: &str) -> Vec<serde_json::Value> {
    locate(root, file)
        .and_then(|p| fs::read_to_string(p).ok())
        .and_then(|s| serde_json::from_str::<serde_json::Value>(&s).ok())
        .and_then(|v| v[key].as_array().cloned())
        .unwrap_or_default()
}
/// Load actual project sprites and bitmap fonts using runtime registry precedence.
pub fn load(project: &Path, source: &str) -> Result<ScenePreview, String> {
    let mut scene = preview(source)?;
    let root = project
        .join("resources")
        .canonicalize()
        .unwrap_or_else(|_| project.join("resources"));
    let mut candidates = Vec::new();
    if let Some(path) = std::env::var_os("SCENEMAX_BUILTIN_RESOURCES") {
        candidates.push(PathBuf::from(path));
    }
    if let Ok(cwd) = std::env::current_dir() {
        candidates.extend(cwd.ancestors().map(|a| a.join("resources")));
    }
    if let Ok(exe) = std::env::current_exe() {
        candidates.extend(exe.ancestors().skip(1).map(|a| a.join("resources")));
    }
    candidates.extend(project.ancestors().map(|a| a.join("resources")));
    let builtin = candidates
        .into_iter()
        .filter_map(|p| p.canonicalize().ok())
        .find(|p| *p != root && (p.join("Models").is_dir() || p.join("models").is_dir()));
    let mut sprites = HashMap::new();
    let mut fonts = HashMap::new();
    for asset_root in std::iter::once(&root).chain(builtin.iter()) {
        for file in ["sprites/sprites.json", "sprites/sprites-ext.json"] {
            for v in registry(asset_root, file, "sprites") {
                if let (Some(name), Some(path)) = (v["name"].as_str(), v["path"].as_str()) {
                    let entry = (
                        asset_root.clone(),
                        path.to_owned(),
                        v["cols"].as_u64().unwrap_or(1).max(1),
                        v["rows"].as_u64().unwrap_or(1).max(1),
                    );
                    sprites.insert(name.to_owned(), entry.clone());
                    sprites.insert(name.to_ascii_lowercase(), entry);
                }
            }
        }
    }
    for asset_root in builtin.iter().chain(std::iter::once(&root)) {
        for file in ["fonts/fonts.json", "fonts/fonts-ext.json"] {
            if Some(asset_root) == builtin.as_ref() && file.ends_with("-ext.json") {
                continue;
            }
            for v in registry(asset_root, file, "fonts") {
                if let (Some(name), Some(path)) = (v["name"].as_str(), v["path"].as_str()) {
                    fonts.insert(
                        name.to_ascii_lowercase(),
                        (asset_root.clone(), path.to_owned()),
                    );
                }
            }
        }
    }
    scene.fonts = fonts.keys().cloned().collect();
    scene.fonts.sort();
    scene.sprites = sprites.keys().cloned().collect();
    scene.sprites.sort();
    let mut font_cache = HashMap::new();
    for w in &mut scene.widgets {
        let def = &w.definition;
        if w.kind == "LIST_VIEW" {
            w.text = scenemax_runtime_ui_core::list_view_text(def);
            w.font_size = def.list_row_font_size;
            w.alignment = "left".into();
        }
        if w.kind == "IMAGE" {
            let mut paths = Vec::new();
            let sheet = def.sprite_name.as_ref().and_then(|n| {
                sprites
                    .get(n)
                    .or_else(|| sprites.get(&n.to_ascii_lowercase()))
            });
            if let Some((r, p, _, _)) = sheet {
                paths.push((r.clone(), p.clone()));
            }
            if let Some(name) = &def.sprite_name {
                for r in std::iter::once(&root).chain(builtin.iter()) {
                    paths.push((r.clone(), format!("sprites/{name}.png")));
                    paths.push((
                        r.clone(),
                        format!("sprites/{}.png", name.to_ascii_lowercase()),
                    ));
                }
            }
            if let Some(p) = &def.image_path {
                paths.push((root.clone(), p.clone()));
            }
            if let Some((r, p)) = paths
                .into_iter()
                .find_map(|(r, p)| locate(&r, &p).map(|p| (r, p)))
            {
                let mut quads = Vec::new();
                if let Some((_, _, cols, rows)) = sheet {
                    use std::io::Read;
                    let mut header = [0u8; 24];
                    if fs::File::open(&p)
                        .and_then(|mut f| f.read_exact(&mut header))
                        .is_ok()
                        && &header[..8] == b"\x89PNG\r\n\x1a\n"
                    {
                        let width =
                            u32::from_be_bytes([header[16], header[17], header[18], header[19]])
                                as f32
                                / *cols as f32;
                        let height =
                            u32::from_be_bytes([header[20], header[21], header[22], header[23]])
                                as f32
                                / *rows as f32;
                        let frame = (def.sprite_frame as u64).min(cols.saturating_mul(*rows) - 1);
                        let x = (frame % cols) as f32 * width;
                        let y = (frame / cols) as f32 * height;
                        quads.push((
                            [x, y, x + width, y + height],
                            [0., 0., w.rect[2], w.rect[3]],
                        ));
                    }
                }
                w.visual = Some(Visual {
                    root: r,
                    path: p,
                    quads,
                    text: false,
                    font: None,
                });
            } else {
                scene.warnings.push(format!(
                    "{}: image asset not found (runtime fallback)",
                    w.name
                ));
            }
        } else if ["TEXT_VIEW", "EDIT_TEXT", "BUTTON", "LIST_VIEW"].contains(&w.kind.as_str()) {
            let name = if w.kind == "LIST_VIEW" {
                def.list_row_font_name.as_ref().or(def.font_name.as_ref())
            } else {
                def.font_name.as_ref()
            };
            if let Some(name) = name.filter(|s| !s.is_empty()) {
                let resolved = fonts
                    .get(&name.to_ascii_lowercase())
                    .and_then(|(r, p)| locate(r, p).map(|p| (r, p)));
                if let Some((r, p)) = resolved {
                    let font = font_cache.entry(p.clone()).or_insert_with(|| {
                        fs::read_to_string(&p)
                            .map_err(|e| e.to_string())
                            .and_then(|s| scenemax_runtime_ui_core::bitmap::parse(&s))
                    });
                    if let Ok(font) = font
                        && let Some(page) = p
                            .parent()
                            .and_then(|parent| parent.join(&font.page).canonicalize().ok())
                            .filter(|p| p.starts_with(r) && p.is_file())
                    {
                        w.visual = Some(Visual {
                            root: r.clone(),
                            path: page,
                            quads: scenemax_runtime_ui_core::bitmap::layout(
                                font,
                                &w.text,
                                w.font_size,
                                w.rect[2],
                                w.rect[3],
                                &w.alignment,
                            ),
                            text: true,
                            font: Some(std::sync::Arc::new(font.clone())),
                        });
                    }
                }
                if w.visual.is_none() {
                    scene.warnings.push(format!(
                        "{}: font {name} unavailable (runtime system-font fallback)",
                        w.name
                    ));
                }
            }
        }
    }
    Ok(scene)
}

/// Re-layout with already resolved assets. New asset references need the disk worker.
pub fn refresh_cached(
    source: &str,
    previous: &ScenePreview,
) -> Result<Option<ScenePreview>, String> {
    let mut next = preview(source)?;
    if next.widgets.len() != previous.widgets.len() {
        return Ok(None);
    }
    next.fonts = previous.fonts.clone();
    next.sprites = previous.sprites.clone();
    next.warnings = previous.warnings.clone();
    for w in &mut next.widgets {
        let Some(old) = previous.widgets.iter().find(|old| old.pointer == w.pointer) else {
            return Ok(None);
        };
        let a = &w.definition;
        let b = &old.definition;
        if a.widget_type != b.widget_type
            || a.sprite_name != b.sprite_name
            || a.image_path != b.image_path
            || a.sprite_frame != b.sprite_frame
            || a.font_name != b.font_name
            || a.list_row_font_name != b.list_row_font_name
        {
            return Ok(None);
        }
        if w.kind == "LIST_VIEW" {
            w.text = scenemax_runtime_ui_core::list_view_text(a);
            w.font_size = a.list_row_font_size;
            w.alignment = "left".into();
        }
        w.visual = old.visual.clone();
        if let Some(visual) = &mut w.visual
            && let Some(font) = &visual.font
        {
            visual.quads = scenemax_runtime_ui_core::bitmap::layout(
                font,
                &w.text,
                w.font_size,
                w.rect[2],
                w.rect[3],
                &w.alignment,
            );
        }
    }
    Ok(Some(next))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn resolves_registered_sprite_frames_fonts_and_panel_semantics() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().join("resources");
        fs::create_dir_all(root.join("sprites")).unwrap();
        fs::create_dir_all(root.join("fonts")).unwrap();
        fs::write(
            root.join("sprites/sprites-ext.json"),
            r#"{"sprites":[{"name":"portrait","path":"sprites/sheet.png","cols":2,"rows":2}]}"#,
        )
        .unwrap();
        let mut png = vec![0u8; 24];
        png[..8].copy_from_slice(b"\x89PNG\r\n\x1a\n");
        png[16..20].copy_from_slice(&100u32.to_be_bytes());
        png[20..24].copy_from_slice(&80u32.to_be_bytes());
        fs::write(root.join("sprites/sheet.png"), &png).unwrap();
        fs::write(root.join("fonts/atlas.png"), &png).unwrap();
        fs::write(
            root.join("fonts/fonts-ext.json"),
            r#"{"fonts":[{"name":"caption","path":"fonts/caption.fnt"}]}"#,
        )
        .unwrap();
        fs::write(root.join("fonts/caption.fnt"),"info size=20\ncommon lineHeight=20\npage id=0 file=\"atlas.png\"\nchar id=65 x=0 y=0 width=10 height=12 xadvance=12").unwrap();
        let scene = load(dir.path(),r##"{"name":"hud","canvasWidth":800,"canvasHeight":600,"layers":[{"name":"ui","widgets":[{"name":"picture","type":"IMAGE","spriteName":"portrait","spriteFrame":3},{"name":"text","type":"TEXT_VIEW","text":"AA","fontName":"caption"},{"name":"panel","type":"PANEL","backgroundColor":"#FF0000"}]}]}"##).unwrap();
        assert!(scene.warnings.is_empty(), "{:?}", scene.warnings);
        assert_eq!(
            scene.widgets[0].visual.as_ref().unwrap().quads[0].0,
            [50., 40., 100., 80.]
        );
        assert_eq!(scene.widgets[1].visual.as_ref().unwrap().quads.len(), 2);
        assert_eq!(scene.widgets[2].kind, "PANEL");
        assert!(scene.widgets[2].visual.is_none());
        assert!(locate(&root, "../../outside.png").is_none());
    }
    #[test]
    fn cached_font_relayouts_text_without_reading_assets() {
        let source = r#"{"name":"ui","layers":[{"name":"hud","widgets":[{"name":"title","type":"TEXT_VIEW","text":"A","fontName":"caption"}]}]}"#;
        let mut previous = preview(source).unwrap();
        let font=scenemax_runtime_ui_core::bitmap::parse("info size=20\ncommon lineHeight=20\npage id=0 file=\"missing.png\"\nchar id=65 x=0 y=0 width=10 height=12 xadvance=12").unwrap();
        previous.widgets[0].visual = Some(Visual {
            font: Some(std::sync::Arc::new(font)),
            root: "unavailable".into(),
            path: "unavailable/missing.png".into(),
            quads: vec![],
            text: true,
        });
        let updated = refresh_cached(&source.replace("\"A\"", "\"AAA\""), &previous)
            .unwrap()
            .unwrap();
        assert_eq!(updated.widgets[0].visual.as_ref().unwrap().quads.len(), 3);
        assert!(
            refresh_cached(
                &source.replace("\"caption\"", "\"different_font\""),
                &previous
            )
            .unwrap()
            .is_none()
        );
    }
}
