use super::*;
use std::{fs, sync::OnceLock};
fn catalog() -> &'static Catalog {
    static C: OnceLock<Catalog> = OnceLock::new();
    C.get_or_init(|| Catalog::discover().unwrap())
}
fn options() -> Options {
    let mut o = Options::default();
    let names = catalog().names();
    if !names.contains(&o.family) {
        o.family = names[0].clone();
    }
    o.characters = "ABC jg123אב A".into();
    o
}
#[test]
fn exports_java_descriptor_contract_and_runtime_can_parse_it() {
    let o = options();
    let atlas = build_atlas(catalog(), &o).unwrap();
    assert!(atlas.image.width().is_power_of_two());
    assert!(atlas.image.height().is_power_of_two());
    assert!(
        atlas
            .descriptor
            .contains("page id=0 file=\"headline_font.png\"")
    );
    assert!(atlas.descriptor.ends_with("kernings count=0\n"));
    let font = scenemax_runtime_ui_core::bitmap::parse(&atlas.descriptor).unwrap();
    assert_eq!(font.glyphs.len(), o.charset().len());
    assert!(font.glyphs.contains_key(&'א'));
    let space = &font.glyphs[&' '];
    assert_eq!((space.width, space.height), (1., 1.));
    assert!(space.x_advance > 0.);
    for glyph in font.glyphs.values() {
        assert!(
            glyph.source[2] <= atlas.image.width() as f32
                && glyph.source[3] <= atlas.image.height() as f32
        );
        assert!(glyph.x_offset <= 0.);
    }
    assert!(atlas.image.pixels().any(|p| p[3] == 0));
    assert!(atlas.image.pixels().any(|p| p[3] > 0));
}
#[test]
fn effects_styles_and_smoothing_change_real_pixels() {
    let mut o = options();
    o.characters = "Ag".into();
    let plain = build_atlas(catalog(), &o).unwrap().image;
    o.style = 3;
    let styled = build_atlas(catalog(), &o).unwrap().image;
    assert_ne!(plain, styled);
    o.style = 0;
    o.gradient = false;
    let solid = build_atlas(catalog(), &o).unwrap().image;
    assert_ne!(plain, solid);
    o.outline = 0;
    o.shadow = [0, 0];
    o.smooth = false;
    let hard = build_atlas(catalog(), &o).unwrap().image;
    assert!(hard.pixels().all(|p| p[3] == 0 || p[3] == 255));
    o.smooth = true;
    let smooth = build_atlas(catalog(), &o).unwrap().image;
    assert!(smooth.pixels().any(|p| p[3] > 0 && p[3] < 255));
    o.shadow = [-32, -32];
    o.outline = 24;
    o.padding = 32;
    assert!(build_atlas(catalog(), &o).is_ok());
}
#[test]
fn custom_repertoire_deduplicates_and_always_includes_space() {
    let mut o = options();
    o.characters = "AAAאא".into();
    assert_eq!(o.charset(), vec!['A', 'א', ' ']);
    o.characters.clear();
    let a = build_atlas(catalog(), &o).unwrap();
    assert_eq!(a.characters, 1);
    assert_eq!(a.image.dimensions(), (1, 1));
    o.size = 257;
    assert!(build_atlas(catalog(), &o).is_err());
}
#[test]
fn preview_keeps_java_canvas_size_and_blank_text_fallback() {
    let mut o = options();
    o.preview = "".into();
    let blank = preview(catalog(), &o).unwrap();
    o.preview = "SceneMax Font".into();
    assert_eq!(blank, preview(catalog(), &o).unwrap());
    assert_eq!(blank.dimensions(), (640, 320));
}
#[test]
fn export_registers_pair_preserves_metadata_and_rejects_duplicate_and_existing_files() {
    let dir = tempfile::tempdir().unwrap();
    fs::create_dir_all(dir.path().join("scripts")).unwrap();
    let fonts = dir.path().join("resources/fonts");
    fs::create_dir_all(&fonts).unwrap();
    fs::write(
        fonts.join("fonts-ext.json"),
        r#"{"custom":42,"fonts":[{"name":"prior","path":"fonts/prior.fnt","keep":true}]}"#,
    )
    .unwrap();
    let o = options();
    export(dir.path(), catalog(), &o).unwrap();
    let index: serde_json::Value =
        serde_json::from_slice(&fs::read(fonts.join("fonts-ext.json")).unwrap()).unwrap();
    assert_eq!(index["custom"], 42);
    assert_eq!(index["fonts"][0]["keep"], true);
    assert_eq!(index["fonts"][1]["path"], "fonts/headline_font.fnt");
    let fnt = fs::read_to_string(fonts.join("headline_font.fnt")).unwrap();
    let parsed = scenemax_runtime_ui_core::bitmap::parse(&fnt).unwrap();
    assert_eq!(parsed.page, "headline_font.png");
    let image = image::open(fonts.join(&parsed.page)).unwrap();
    assert!(image.width() > 1);
    let before = fs::read(fonts.join("fonts-ext.json")).unwrap();
    let mut duplicate = o.clone();
    duplicate.name = "HEADLINE_FONT".into();
    assert!(export(dir.path(), catalog(), &duplicate).is_err());
    assert_eq!(before, fs::read(fonts.join("fonts-ext.json")).unwrap());
    duplicate.name = "blocked".into();
    fs::write(fonts.join("blocked.png"), "keep").unwrap();
    assert!(export(dir.path(), catalog(), &duplicate).is_err());
    assert_eq!(fs::read(fonts.join("blocked.png")).unwrap(), b"keep");
    assert!(!fonts.join("blocked.fnt").exists());
    duplicate.name = "../escape".into();
    assert!(export(dir.path(), catalog(), &duplicate).is_err());
    assert!(!dir.path().join(".scenemax-studio/inventory.lock").exists());
}
#[test]
fn shared_duplicate_and_bad_index_never_publish_partial_font() {
    let dir = tempfile::tempdir().unwrap();
    let project = dir.path().join("project");
    fs::create_dir_all(project.join("scripts")).unwrap();
    fs::create_dir_all(dir.path().join("resources/fonts")).unwrap();
    fs::write(
        dir.path().join("resources/fonts/fonts.json"),
        "{fonts:[{name:\"headline_font\",path:\"fonts/shared.fnt\"},]}",
    )
    .unwrap();
    let mut o = options();
    assert!(export(&project, catalog(), &o).is_err());
    o.name = "new_font".into();
    fs::create_dir_all(project.join("resources/fonts")).unwrap();
    fs::write(project.join("resources/fonts/fonts-ext.json"), "{bad").unwrap();
    assert!(export(&project, catalog(), &o).is_err());
    assert!(!project.join("resources/fonts/new_font.png").exists());
}

#[test]
#[cfg(windows)]
fn failed_index_publish_rolls_back_both_new_files() {
    let dir = tempfile::tempdir().unwrap();
    fs::create_dir_all(dir.path().join("scripts")).unwrap();
    let fonts = dir.path().join("resources/fonts");
    fs::create_dir_all(&fonts).unwrap();
    let index = fonts.join("fonts-ext.json");
    let original = br#"{"fonts":[],"preserve":true}"#;
    fs::write(&index, original).unwrap();
    let permissions = fs::metadata(&index).unwrap().permissions();
    let mut readonly = permissions.clone();
    readonly.set_readonly(true);
    fs::set_permissions(&index, readonly).unwrap();
    let result = export(dir.path(), catalog(), &options());
    fs::set_permissions(&index, permissions).unwrap();
    assert!(result.is_err());
    assert_eq!(fs::read(&index).unwrap(), original);
    assert!(!fonts.join("headline_font.png").exists());
    assert!(!fonts.join("headline_font.fnt").exists());
}
