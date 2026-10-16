//! Image material replacement and first-image restoration for native UI shaders.
use super::*;

#[derive(Component, Clone)]
struct OriginalImage {
    node: ImageNode,
    sheet: Option<SceneMaxUiSpriteSheet>,
    authored: Option<SceneMaxUiWidgetDef>,
    failed: Vec<Handle<Image>>,
}

#[derive(Component, Clone, Copy, Default)]
pub(super) struct DisplayState {
    pub original: bool,
    pub position: Option<usize>,
}

#[derive(Component)]
struct ShaderName(String);

#[derive(Debug, Clone)]
pub(crate) struct Snapshot {
    pub original: images::Snapshot,
    pub current: images::Snapshot,
    pub restored: bool,
    pub position: Option<usize>,
    pub shader: Option<String>,
}

impl Snapshot {
    pub(crate) fn valid(&self) -> bool {
        self.original.valid() && self.current.valid()
            && self.shader.as_ref().is_none_or(|name| !name.trim().is_empty() && !name.contains('\0'))
    }
}

pub(crate) fn snapshot(world: &World, entity: Entity) -> Option<Snapshot> {
    let original = world.get::<OriginalImage>(entity)?.authored.as_ref()?;
    let display = world.get::<DisplayState>(entity).copied().unwrap_or_default();
    Some(Snapshot {
        original: images::Snapshot { image: original.image_path.clone(), sprite: original.sprite_name.clone(), frame: original.sprite_frame },
        current: images::snapshot(world, entity)?,
        restored: display.original, position: display.position,
        shader: world.get::<ShaderName>(entity).map(|name| name.0.clone()),
    })
}

pub(crate) fn restore(world: &mut World, entity: Entity, state: Snapshot) {
    if !state.valid() || world.get::<ImageNode>(entity).is_none() { return; }
    if let Some(name) = &state.shader {
        if !world.contains_resource::<Assets<shaders::SurfaceShader>>() ||
            crate::shader::resolve_ui_surface_color(name, world.resource::<SceneMaxLaunchContext>()).is_none() { return; }
    }
    let Some(mut authored) = world.get::<images::ImageState>(entity).map(|s| s.0.clone()) else { return; };
    authored.image_path = state.original.image;
    authored.sprite_name = state.original.sprite;
    authored.sprite_frame = state.original.frame;
    // Repeated snapshots must keep the first resolved visual and failed handles.
    // Reconstruct only when the sender supplies a different reset source.
    let original = world.get::<OriginalImage>(entity).filter(|saved| {
        saved.authored.as_ref().is_some_and(|source|
            source.image_path == authored.image_path && source.sprite_name == authored.sprite_name
                && source.sprite_frame == authored.sprite_frame)
    }).cloned().unwrap_or_else(|| {
        let (node, sheet) = ui_image_node_for_widget(&authored, world.resource::<AssetServer>(),
            world.resource::<SceneMaxLaunchContext>(), world.resource::<SceneMaxUiRuntime>());
        OriginalImage { node, sheet, authored: Some(authored), failed: Vec::new() }
    });
    images::restore(world, entity, state.current);
    if state.restored {
        world.entity_mut(entity).insert(original.node.clone()).remove::<SceneMaxUiSpriteSheet>();
        if let Some(sheet) = original.sheet { world.entity_mut(entity).insert(sheet); }
    }
    if let Some(position) = state.position {
        if let Some(sheet) = world.get::<SceneMaxUiSpriteSheet>(entity) {
            let rect = ui_sprite_position_rect(position, sheet.cols, sheet.rows, sheet.image_width, sheet.image_height);
            world.get_mut::<ImageNode>(entity).unwrap().rect = Some(rect);
        }
    }
    world.entity_mut(entity).insert((original, DisplayState { original: state.restored, position: state.position }));
    if let Some(name) = state.shader { apply(world, entity, &name); }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn repeated_replay_keeps_resolved_first_visual_until_reset_source_changes() {
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, bevy::asset::AssetPlugin::default()));
        let world = app.world_mut();
        world.init_resource::<SceneMaxUiRuntime>();
        world.init_resource::<Assets<Image>>();
        world.insert_resource(SceneMaxLaunchContext {
            script_root: None, asset_root: None, builtin_asset_root: None,
            window_width: 640, window_height: 360,
        });
        let authored: SceneMaxUiWidgetDef = serde_json::from_str(
            r#"{"name":"image","type":"IMAGE","imagePath":"missing.png"}"#).unwrap();
        let retained = world.resource_mut::<Assets<Image>>().add(Image::default());
        let entity = world.spawn((images::ImageState(authored.clone()), ImageNode::default(),
            OriginalImage { node: ImageNode::solid_color(Color::srgb(0.,1.,0.)), sheet: None,
                authored: Some(authored), failed: vec![retained.clone()] })).id();
        let mut state = Snapshot {
            original: images::Snapshot { image: Some("missing.png".into()), sprite: None, frame: 0 },
            current: images::Snapshot { image: Some("new.png".into()), sprite: None, frame: 0 },
            restored: true, position: None, shader: None,
        };
        for frame in [1, 2] {
            state.current.frame = frame;
            restore(world, entity, state.clone());
            assert_eq!(world.get::<ImageNode>(entity).unwrap().color, Color::srgb(0.,1.,0.));
            assert_eq!(world.get::<OriginalImage>(entity).unwrap().failed, vec![retained.clone()]);
        }
        state.original.image = Some("different.png".into());
        restore(world, entity, state);
        assert_eq!(world.get::<ImageNode>(entity).unwrap().color, Color::srgba(1.,0.,1.,1.));
        assert!(world.get::<OriginalImage>(entity).unwrap().failed.is_empty());
    }
    #[test]
    fn image_shader_keeps_first_texture_and_frame_across_replacement() {
        let files = tempfile::tempdir().unwrap();
        fs::create_dir(files.path().join("scripts")).unwrap();
        fs::write(
            files.path().join("scripts/tint.bvshader"),
            r#"{"mainColor":[1,1,1,1],"blocks":["TINT"]}"#,
        )
        .unwrap();
        let mut world = World::new();
        world.init_resource::<Assets<Image>>();
        world.init_resource::<Assets<shaders::SurfaceShader>>();
        world.insert_resource(SceneMaxLaunchContext {
            script_root: None,
            asset_root: Some(files.path().join("resources")),
            builtin_asset_root: None,
            window_width: 640,
            window_height: 360,
        });
        let first = world.resource_mut::<Assets<Image>>().add(Image::default());
        let next = world.resource_mut::<Assets<Image>>().add(Image::default());
        let mut node = ImageNode::new(first.clone());
        let rect = ui_sprite_frame_rect(1, 4, 1, 128, 32);
        node.rect = Some(rect);
        let entity = world
            .spawn((
                node,
                SceneMaxUiSpriteSheet {
                    rows: 1,
                    cols: 4,
                    image_width: 128,
                    image_height: 32,
                },
            ))
            .id();
        apply(&mut world, entity, "tint");
        assert!(
            world
                .get::<MaterialNode<shaders::SurfaceShader>>(entity)
                .is_some()
        );
        assert_eq!(world.get::<ImageNode>(entity).unwrap().color, Color::NONE);
        discard(&mut world, entity);
        world.entity_mut(entity).insert(ImageNode::new(next));
        apply(&mut world, entity, "tint");
        assert_eq!(
            world.get::<OriginalImage>(entity).unwrap().node.image,
            first
        );
        apply(&mut world, entity, "");
        let restored = world.get::<ImageNode>(entity).unwrap();
        assert_eq!(restored.image, first);
        assert_eq!(restored.rect, Some(rect));
        assert_eq!(restored.color, Color::WHITE);
        assert!(world.get::<ActiveImageShader>(entity).is_none());
    }
}

#[derive(Component, Clone, Copy)]
pub(crate) struct ActiveImageShader {
    color: Color,
    inherit: bool,
}

/// Resolve the first visual independently of subsequent authored resource edits.
/// A shader may capture a handle before asynchronous image decoding completes.
pub(crate) fn update_sources(world: &mut World) {
    let pending = world.query::<(Entity, &OriginalImage)>().iter(world)
        .filter(|(_, original)| matches!(world.resource::<AssetServer>().get_load_state(original.node.image.id()),
            Some(bevy::asset::LoadState::Failed(_))))
        .map(|(entity, original)| (entity, original.clone())).collect::<Vec<_>>();
    for (entity, mut original) in pending {
        let Some(authored) = original.authored.as_ref() else { continue; };
        let failed = original.node.image.clone();
        let (node, sheet) = ui_image_node_for_widget(authored,
            world.resource::<AssetServer>(), world.resource::<SceneMaxLaunchContext>(),
            world.resource::<SceneMaxUiRuntime>());
        if original.failed.len() < 2 && !original.failed.contains(&failed) {
            original.failed.push(failed.clone());
        }
        original.node = node.clone();
        original.sheet = sheet;
        let active = world.get::<ActiveImageShader>(entity).copied();
        if world.get::<ImageNode>(entity).is_some_and(|current| current.image == failed) {
            let mut current = node.clone();
            if active.is_some() { current.color = Color::NONE; }
            world.entity_mut(entity).insert(current).remove::<SceneMaxUiSpriteSheet>();
            if let Some(sheet) = sheet { world.entity_mut(entity).insert(sheet); }
        }
        if let Some(active) = active {
            let texture = (active.inherit && node.image != Handle::<Image>::default()).then(|| node.image.clone());
            let definition=world.get::<ShaderName>(entity).and_then(|name|shaders::SurfaceShader::configured(&name.0,world.resource::<SceneMaxLaunchContext>(),texture.clone())).unwrap_or_else(||shaders::SurfaceShader::textured(active.color,texture));
            let material = world.resource_mut::<Assets<shaders::SurfaceShader>>().add(definition);
            world.entity_mut(entity).insert(MaterialNode(material));
        }
        world.entity_mut(entity).insert(original);
        write_runtime_diagnostic_line(format!("UI:IMAGE_SHADER source failure resolved target={entity:?}"));
    }
}

pub(super) fn discard(world: &mut World, entity: Entity) {
    if world.get::<ActiveImageShader>(entity).is_some() {
        world
            .entity_mut(entity)
            .remove::<(MaterialNode<shaders::SurfaceShader>, ActiveImageShader, ShaderName)>();
    }
}

pub(super) fn apply(world: &mut World, entity: Entity, name: &str) -> bool {
    let Some(current) = world.get::<ImageNode>(entity).cloned() else {
        return false;
    };
    if name.trim().is_empty() {
        if let Some(original) = world.get::<OriginalImage>(entity).cloned() {
            discard(world, entity);
            world
                .entity_mut(entity)
                .insert((original.node, DisplayState { original: true, position: None }))
                .remove::<SceneMaxUiSpriteSheet>();
            if let Some(sheet) = original.sheet {
                world.entity_mut(entity).insert(sheet);
            }
        }
        write_runtime_diagnostic_line(format!("UI:IMAGE_SHADER reset target={entity:?}"));
        return true;
    }
    let Some((color, inherit)) =
        crate::shader::resolve_ui_surface_color(name, world.resource::<SceneMaxLaunchContext>())
    else {
        write_runtime_diagnostic_line(format!(
            "UI:IMAGE_SHADER unavailable name={name} target={entity:?}"
        ));
        return false;
    };
    if !world.contains_resource::<Assets<shaders::SurfaceShader>>() {
        return false;
    }
    if world.get::<OriginalImage>(entity).is_none() {
        let sheet = world.get::<SceneMaxUiSpriteSheet>(entity).copied();
        let authored = world.get::<images::ImageState>(entity).map(|state| state.0.clone());
        world.entity_mut(entity).insert(OriginalImage {
            node: current,
            sheet,
            authored,
            failed: Vec::new(),
        });
    }
    let source = &world.get::<OriginalImage>(entity).unwrap().node;
    let texture =
        (inherit && source.image != Handle::<Image>::default()).then(|| source.image.clone());
    let Some(definition)=shaders::SurfaceShader::configured(name,world.resource::<SceneMaxLaunchContext>(),texture) else{return false;};
    let material=world.resource_mut::<Assets<shaders::SurfaceShader>>().add(definition);
    // Keep the authored ImageNode for subsequent resource/frame commands, but
    // suppress its separate draw while the material renders the whole source map.
    world.get_mut::<ImageNode>(entity).unwrap().color = Color::NONE;
    world
        .entity_mut(entity)
        .insert((MaterialNode(material), ActiveImageShader { color, inherit }, ShaderName(name.to_owned())));
    write_runtime_diagnostic_line(format!(
        "UI:IMAGE_SHADER applied name={name} target={entity:?}"
    ));
    true
}
