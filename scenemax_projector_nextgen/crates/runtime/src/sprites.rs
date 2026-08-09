use super::*;

pub(super) fn load_sprite_index(
    asset_root: &Path,
    builtin_asset_root: Option<&Path>,
) -> HashMap<String, SceneMaxSpriteAsset> {
    let mut sprites = HashMap::new();
    load_sprite_index_map(
        &asset_root.join("sprites").join("sprites.json"),
        "",
        &mut sprites,
    );
    load_sprite_index_map(
        &asset_root.join("sprites").join("sprites-ext.json"),
        "",
        &mut sprites,
    );
    if let Some(builtin_root) = builtin_asset_root {
        load_sprite_index_map(
            &builtin_root.join("sprites").join("sprites.json"),
            "builtin://",
            &mut sprites,
        );
        load_sprite_index_map(
            &builtin_root.join("sprites").join("sprites-ext.json"),
            "builtin://",
            &mut sprites,
        );
    }
    sprites
}

fn load_sprite_index_map(
    path: &Path,
    prefix: &str,
    sprites_by_name: &mut HashMap<String, SceneMaxSpriteAsset>,
) {
    let Ok(source) = fs::read_to_string(path) else {
        return;
    };
    let Ok(root) = serde_json::from_str::<serde_json::Value>(&source) else {
        return;
    };
    let Some(sprites) = root.get("sprites").and_then(|value| value.as_array()) else {
        return;
    };
    for sprite in sprites {
        let Some(name) = sprite.get("name").and_then(|value| value.as_str()) else {
            continue;
        };
        let Some(path) = sprite.get("path").and_then(|value| value.as_str()) else {
            continue;
        };
        let rows = sprite
            .get("rows")
            .and_then(|value| value.as_u64())
            .unwrap_or(1) as usize;
        let cols = sprite
            .get("cols")
            .and_then(|value| value.as_u64())
            .unwrap_or(1) as usize;
        sprites_by_name.insert(
            name.to_ascii_lowercase(),
            SceneMaxSpriteAsset {
                path: format!("{prefix}{}", normalize_asset_path(path)),
                rows: rows.max(1),
                cols: cols.max(1),
            },
        );
    }
}

pub(super) fn spawn_scenemax_sprite_decl(
    commands: &mut Commands,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    meshes: &mut Assets<Mesh>,
    materials: &mut Assets<StandardMaterial>,
    name: &str,
    resource: &str,
    options: &EntityOptions,
    sprite_index: &HashMap<String, SceneMaxSpriteAsset>,
    visibility_by_target: &HashMap<String, bool>,
) -> Option<(Entity, Transform)> {
    let sprite_asset = sprite_index.get(&resource.to_ascii_lowercase())?;
    let asset_path = normalize_asset_path(&sprite_asset.path);
    let image_file = sprite_asset_file_path(&asset_path, context)?;
    let (image_width, image_height) =
        png_dimensions(&image_file).unwrap_or((sprite_asset.cols as u32, sprite_asset.rows as u32));
    let cols = sprite_asset.cols.max(1);
    let rows = sprite_asset.rows.max(1);
    let frame_width = (image_width / cols as u32).max(1);
    let frame_height = (image_height / rows as u32).max(1);
    let display_size = sprite_display_size(options, frame_width, frame_height);
    let mesh = meshes.add(sprite_quad_mesh(
        display_size,
        sprite_frame_uvs(0, cols, rows),
    ));
    let material = materials.add(StandardMaterial {
        base_color_texture: Some(asset_server.load(asset_path.clone())),
        alpha_mode: AlphaMode::Blend,
        unlit: true,
        cull_mode: None,
        ..default()
    });
    let transform = sprite_transform_from_options(options);
    let entity = commands
        .spawn((
            SceneMaxEntity {
                name: name.to_owned(),
                runtime_name: format!("{name}@1"),
            },
            SceneMaxSprite {
                rows,
                cols,
                frame_count: rows * cols,
                mesh: mesh.clone(),
            },
            Mesh3d(mesh),
            MeshMaterial3d(material),
            transform,
            initial_visibility(name, options, visibility_by_target),
        ))
        .id();
    tracing::info!(
        name,
        resource,
        path = %asset_path,
        rows,
        cols,
        image_width,
        image_height,
        frame_width,
        frame_height,
        display_width = display_size.x,
        display_height = display_size.y,
        "spawned SceneMax sprite"
    );
    write_runtime_diagnostic_line(format!(
        "spawned 3D sprite quad {name}=>{resource} at {asset_path} sheet={}x{} frame={}x{} display={:.3}x{:.3} pos=({:.3},{:.3},{:.3})",
        image_width,
        image_height,
        frame_width,
        frame_height,
        display_size.x,
        display_size.y,
        transform.translation.x,
        transform.translation.y,
        transform.translation.z
    ));
    Some((entity, transform))
}

fn sprite_asset_file_path(asset_path: &str, context: &SceneMaxLaunchContext) -> Option<PathBuf> {
    if let Some(relative) = asset_path.strip_prefix("builtin://") {
        return context
            .builtin_asset_root
            .as_ref()
            .map(|root| root.join(relative))
            .filter(|path| path.is_file());
    }
    context
        .asset_root
        .as_ref()
        .map(|root| root.join(asset_path))
        .filter(|path| path.is_file())
}

fn png_dimensions(path: &Path) -> Option<(u32, u32)> {
    let bytes = fs::read(path).ok()?;
    if bytes.len() < 24 || &bytes[..8] != b"\x89PNG\r\n\x1a\n" {
        return None;
    }
    Some((
        u32::from_be_bytes(bytes[16..20].try_into().ok()?),
        u32::from_be_bytes(bytes[20..24].try_into().ok()?),
    ))
}

pub(super) fn sprite_transform_from_options(options: &EntityOptions) -> Transform {
    let mut transform = Transform::from_translation(
        options
            .position
            .map(vec3_from_scenemax)
            .unwrap_or(Vec3::ZERO),
    );
    if let Some(rotation) = options.rotation_degrees {
        transform.rotation = rotation_from_degrees(rotation);
    }
    if let Some(scale) = options.scale {
        transform.scale = vec3_from_scenemax(scale);
    }
    transform
}

pub(super) fn sprite_display_size(
    options: &EntityOptions,
    _frame_width: u32,
    _frame_height: u32,
) -> Vec2 {
    options
        .size
        .map(|size| Vec2::new(size.x.abs().max(1.0), size.y.abs().max(1.0)))
        .unwrap_or(Vec2::ONE)
}

pub(super) fn sprite_quad_mesh(display_size: Vec2, uvs: Vec<[f32; 2]>) -> Mesh {
    let half_width = display_size.x.max(0.001) * 0.5;
    let half_height = display_size.y.max(0.001) * 0.5;
    Mesh::new(
        PrimitiveTopology::TriangleList,
        RenderAssetUsages::default(),
    )
    .with_inserted_attribute(
        Mesh::ATTRIBUTE_POSITION,
        vec![
            [-half_width, -half_height, 0.0],
            [half_width, -half_height, 0.0],
            [half_width, half_height, 0.0],
            [-half_width, half_height, 0.0],
        ],
    )
    .with_inserted_attribute(Mesh::ATTRIBUTE_NORMAL, vec![[0.0, 0.0, 1.0]; 4])
    .with_inserted_attribute(Mesh::ATTRIBUTE_UV_0, uvs)
    .with_inserted_indices(Indices::U32(vec![0, 1, 2, 2, 3, 0]))
}

pub(super) fn sprite_frame_uvs(frame: usize, cols: usize, rows: usize) -> Vec<[f32; 2]> {
    let cols = cols.max(1);
    let rows = rows.max(1);
    let frame = frame.min(cols * rows - 1);
    let col = frame % cols;
    let row = frame / cols;
    let u0 = col as f32 / cols as f32;
    let u1 = (col + 1) as f32 / cols as f32;
    let v0 = row as f32 / rows as f32;
    let v1 = (row + 1) as f32 / rows as f32;
    vec![[u0, v1], [u1, v1], [u1, v0], [u0, v0]]
}

pub(super) fn sprite_animation_from_statement(
    sprite_play: &SpritePlayStatement,
) -> SceneMaxSpriteAnimation {
    SceneMaxSpriteAnimation {
        from_frame: sprite_play.from_frame,
        to_frame: sprite_play.to_frame,
        duration_seconds: sprite_play.duration_seconds.max(0.001),
        elapsed_seconds: 0.0,
        looped: sprite_play.looped,
    }
}

pub(super) fn update_sprite_animations(
    time: Res<Time>,
    mut commands: Commands,
    mut meshes: ResMut<Assets<Mesh>>,
    mut sprites: Query<(
        Entity,
        &SceneMaxEntity,
        &SceneMaxSprite,
        &mut SceneMaxSpriteAnimation,
    )>,
) {
    for (entity, scene_entity, sheet, mut animation) in &mut sprites {
        let min_frame = animation.from_frame.min(animation.to_frame);
        let max_frame = animation.from_frame.max(animation.to_frame);
        let span = max_frame.saturating_sub(min_frame) + 1;
        if span == 0 || sheet.frame_count == 0 {
            commands.entity(entity).remove::<SceneMaxSpriteAnimation>();
            continue;
        }

        animation.elapsed_seconds += time.delta_secs();
        let mut progress = animation.elapsed_seconds / animation.duration_seconds.max(0.001);
        if animation.looped {
            progress = progress.fract();
        } else if progress >= 1.0 {
            progress = 1.0;
        }
        let offset = ((progress * span as f32).floor() as usize).min(span - 1);
        let frame = if animation.from_frame <= animation.to_frame {
            min_frame + offset
        } else {
            max_frame.saturating_sub(offset)
        }
        .min(sheet.frame_count - 1);

        if let Some(mut mesh) = meshes.get_mut(&sheet.mesh) {
            mesh.insert_attribute(
                Mesh::ATTRIBUTE_UV_0,
                sprite_frame_uvs(frame, sheet.cols, sheet.rows),
            );
        }

        if !animation.looped && animation.elapsed_seconds >= animation.duration_seconds {
            commands.entity(entity).remove::<SceneMaxSpriteAnimation>();
        }
        tracing::trace!(
            target = %scene_entity.name,
            frame,
            rows = sheet.rows,
            cols = sheet.cols,
            "advanced SceneMax sprite animation"
        );
    }
}
