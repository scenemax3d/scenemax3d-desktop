//! Ordered image resource/frame changes; authored state survives resource swaps.
use super::*;

#[derive(Component, Clone)]
pub(crate) struct ImageState(pub SceneMaxUiWidgetDef);

// Hold failed candidates until an explicit resource change so a failed sprite
// and failed direct image cannot repeatedly restart each other's load attempts.
#[derive(Component, Default)]
pub(crate) struct FailedImages(Vec<Handle<Image>>);

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, PartialEq)]
#[serde(deny_unknown_fields)]
pub(crate) struct Snapshot {
    pub image: Option<String>,
    pub sprite: Option<String>,
    pub frame: usize,
}

impl Snapshot {
    pub(crate) fn valid(&self) -> bool {
        self.image
            .iter()
            .chain(self.sprite.iter())
            .all(|name| !name.contains('\0'))
    }
}

pub(crate) fn snapshot(world: &World, entity: Entity) -> Option<Snapshot> {
    let state = &world.get::<ImageState>(entity)?.0;
    Some(Snapshot {
        image: state.image_path.clone(),
        sprite: state.sprite_name.clone(),
        frame: state.sprite_frame,
    })
}

pub(crate) fn restore(world: &mut World, entity: Entity, snapshot: Snapshot) {
    if !snapshot.valid() || world.get::<ImageNode>(entity).is_none() {
        return;
    }
    let Some(mut state) = world.get::<ImageState>(entity).cloned() else {
        return;
    };
    state.0.image_path = snapshot.image;
    state.0.sprite_name = snapshot.sprite;
    state.0.sprite_frame = snapshot.frame;
    render(world, entity, state);
}

pub(super) fn queue(commands: &mut Commands, entity: Entity, property: String, value: String) {
    commands.queue(move |world: &mut World| apply(world, entity, &property, &value));
}

fn apply(world: &mut World, entity: Entity, property: &str, value: &str) {
    if world.get::<ImageNode>(entity).is_none() || value.contains('\0') {
        return;
    }
    if property == "frame" {
        let Some(frame) = parse_ui_frame_value(value) else {
            return;
        };
        if let Some(mut state) = world.get_mut::<ImageState>(entity) {
            state.0.sprite_frame = frame;
        }
        // Shader reset restores a material, not the authored resource. Clamp
        // against the current resource before addressing the restored atlas.
        let frame = if let Some(state) = world.get::<ImageState>(entity).cloned() {
            let runtime = world.resource::<SceneMaxUiRuntime>();
            let sprite = state.0.sprite_name.as_deref().and_then(|name| {
                runtime.sprite_index.get(name)
                    .or_else(|| runtime.sprite_index.get(&name.to_ascii_lowercase()))
            });
            if let Some(sprite) = sprite {
                frame.min(sprite.cols.max(1).saturating_mul(sprite.rows.max(1)).saturating_sub(1))
            } else {
                render(world, entity, state);
                return;
            }
        } else {
            frame
        };
        if let Some(sheet) = world.get::<SceneMaxUiSpriteSheet>(entity) {
            let rect = ui_sprite_position_rect(
                frame,
                sheet.cols,
                sheet.rows,
                sheet.image_width,
                sheet.image_height,
            );
            world.get_mut::<ImageNode>(entity).unwrap().rect = Some(rect);
            let mut display = world.get::<image_shaders::DisplayState>(entity).copied().unwrap_or_default();
            display.position = Some(frame);
            world.entity_mut(entity).insert(display);
        }
        return;
    }
    let Some(mut state) = world.get::<ImageState>(entity).cloned() else {
        return;
    };
    match property {
        "image" => {
            state.0.image_path = Some(value.into());
            state.0.sprite_name = None;
            state.0.sprite_frame = 0;
        }
        "sprite" => {
            state.0.sprite_name = Some(value.into());
            state.0.image_path = None;
        }
        _ => return,
    }
    render(world, entity, state);
}

fn render(world: &mut World, entity: Entity, state: ImageState) {
    image_shaders::discard(world, entity);
    let runtime = world.resource::<SceneMaxUiRuntime>();
    let context = world.resource::<SceneMaxLaunchContext>();
    let (image, sheet) =
        ui_image_node_for_widget(&state.0, world.resource::<AssetServer>(), context, runtime);
    world
        .entity_mut(entity)
        .insert((state, image, image_shaders::DisplayState::default()))
        .remove::<(SceneMaxUiSpriteSheet, FailedImages)>();
    if let Some(sheet) = sheet {
        world.entity_mut(entity).insert(sheet);
    }
}

/// Resolve asynchronous decode failures using the same authored fallback chain.
pub(crate) fn update_failed(
    mut commands: Commands,
    server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    runtime: Res<SceneMaxUiRuntime>,
    mut query: Query<(
        Entity,
        &ImageState,
        &mut ImageNode,
        Option<&mut FailedImages>,
        Option<&image_shaders::ActiveImageShader>,
    )>,
) {
    for (entity, state, mut image, failures, shader) in &mut query {
        if !matches!(
            server.get_load_state(image.image.id()),
            Some(bevy::asset::LoadState::Failed(_))
        ) {
            continue;
        }
        let failed = image.image.clone();
        let (replacement, sheet) = ui_image_node_for_widget(&state.0, &server, &context, &runtime);
        *image = replacement;
        if shader.is_some() { image.color = Color::NONE; }
        if let Some(mut failures) = failures {
            if failures.0.len() < 2 && !failures.0.contains(&failed) {
                failures.0.push(failed);
            }
        } else {
            commands.entity(entity).insert(FailedImages(vec![failed]));
        }
        commands.entity(entity).remove::<SceneMaxUiSpriteSheet>();
        if let Some(sheet) = sheet {
            commands.entity(entity).insert(sheet);
        }
        write_runtime_diagnostic_line(format!("UI:IMAGE load failure resolved target={entity:?}"));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn image_resets_frame_sprite_retains_it_and_missing_resources_replace_old_visuals() {
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, bevy::asset::AssetPlugin::default()));
        let world = app.world_mut();
        world.init_resource::<SceneMaxUiRuntime>();
        world.resource_mut::<SceneMaxUiRuntime>().sprite_index.insert(
            "fixture".into(), SceneMaxSpriteAsset {
                path: "sprites/fixture.png".into(), rows: 1, cols: 4,
            },
        );
        world.insert_resource(SceneMaxLaunchContext {
            script_root: None,
            asset_root: None,
            builtin_asset_root: None,
            window_width: 640,
            window_height: 360,
        });
        let widget = serde_json::from_str(
            r#"{"name":"image","type":"IMAGE","spriteName":"fixture","spriteFrame":3,"imageScaleMode":"stretch"}"#,
        )
        .unwrap();
        let entity = world
            .spawn((
                ImageState(widget),
                ImageNode::default(),
                SceneMaxUiSpriteSheet {
                    rows: 1,
                    cols: 4,
                    image_width: 128,
                    image_height: 32,
                },
            ))
            .id();
        apply(world, entity, "frame", "2.9");
        assert_eq!(
            world.get::<ImageNode>(entity).unwrap().rect,
            Some(ui_sprite_frame_rect(2, 4, 1, 128, 32))
        );
        // Current resource has two frames, but shader reset restored a four-
        // frame material. Position clamps to one using the current resource.
        world.resource_mut::<SceneMaxUiRuntime>().sprite_index.get_mut("fixture").unwrap().cols = 2;
        apply(world, entity, "frame", "3");
        assert_eq!(world.get::<ImageNode>(entity).unwrap().rect,
            Some(ui_sprite_frame_rect(1, 4, 1, 128, 32)));
        assert_eq!(world.get::<ImageState>(entity).unwrap().0.sprite_frame, 3);
        // A larger current resource can address outside the restored atlas.
        // The reference shader keeps column one and samples beyond its last row.
        world.resource_mut::<SceneMaxUiRuntime>().sprite_index.get_mut("fixture").unwrap().cols = 8;
        apply(world, entity, "frame", "5");
        assert_eq!(world.get::<ImageNode>(entity).unwrap().rect,
            Some(Rect { min: Vec2::new(32., 32.), max: Vec2::new(64., 64.) }));
        // A direct image has no active sprite: frame rebuilds its authored
        // image even if reset left an old sprite material on the widget.
        {
            let mut state = world.get_mut::<ImageState>(entity).unwrap();
            state.0.sprite_name = None;
            state.0.image_path = Some("missing.png".into());
        }
        apply(world, entity, "frame", "2.9");
        assert!(world.get::<SceneMaxUiSpriteSheet>(entity).is_none());
        assert_eq!(world.get::<ImageNode>(entity).unwrap().color, Color::srgba(1., 0., 1., 1.));
        apply(world, entity, "sprite", "missing");
        assert!(matches!(
            world.get::<ImageNode>(entity).unwrap().image_mode,
            NodeImageMode::Stretch
        ));
        assert_eq!(world.get::<ImageState>(entity).unwrap().0.sprite_frame, 2);
        assert!(world.get::<SceneMaxUiSpriteSheet>(entity).is_none());
        assert_eq!(
            world.get::<ImageNode>(entity).unwrap().color,
            Color::srgba(0.5, 0.5, 0.5, 0.3)
        );
        queue(&mut world.commands(), entity, "frame".into(), "2.9".into());
        queue(
            &mut world.commands(),
            entity,
            "image".into(),
            "missing.png".into(),
        );
        queue(
            &mut world.commands(),
            entity,
            "sprite".into(),
            "missing".into(),
        );
        world.flush();
        assert_eq!(world.get::<ImageState>(entity).unwrap().0.sprite_frame, 0);
        queue(&mut world.commands(), entity, "frame".into(), "3.9".into());
        queue(
            &mut world.commands(),
            entity,
            "sprite".into(),
            "missing".into(),
        );
        world.flush();
        assert_eq!(world.get::<ImageState>(entity).unwrap().0.sprite_frame, 3);
        apply(world, entity, "image", "missing.png");
        assert_eq!(world.get::<ImageState>(entity).unwrap().0.sprite_frame, 0);
        assert_eq!(
            world.get::<ImageNode>(entity).unwrap().color,
            Color::srgba(1., 0., 1., 1.)
        );
        assert!(
            world
                .get::<ImageState>(entity)
                .unwrap()
                .0
                .sprite_name
                .is_none()
        );
        apply(world, entity, "frame", "3");
        apply(world, entity, "sprite", "");
        assert_eq!(world.get::<ImageState>(entity).unwrap().0.sprite_frame, 3);
        assert!(
            world
                .get::<ImageState>(entity)
                .unwrap()
                .0
                .image_path
                .is_none()
        );
        apply(world, entity, "image", "");
        assert_eq!(world.get::<ImageState>(entity).unwrap().0.sprite_frame, 0);
        assert_eq!(
            world.get::<ImageNode>(entity).unwrap().color,
            Color::srgba(0.5, 0.5, 0.5, 0.3)
        );
    }
}
