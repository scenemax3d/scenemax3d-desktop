//! Scene labels use Bevy's font shaping and world-space, depth-tested meshes.
use super::*;
mod fonts;
use bevy::sprite::{Text2d, Text2dReader};
use bevy::text::{
    ComputedTextBlock, FontAtlasSet, FontCx, LayoutCx, ScaleCx, TextBounds, TextLayoutInfo,
    TextPipeline,
};
pub(super) use fonts::prepare;

#[derive(Component)]
pub(super) struct Label {
    pub text: String,
    font: Option<String>,
    font_ready: bool,
    bitmap_font: Option<SceneMaxBitmapFont>,
    size: Option<Vec2>,
    style: &'static str,
    transparency: f32,
    visual: Option<Entity>,
    dirty: bool,
}

#[derive(Component)]
pub(super) struct LabelVisual;

fn canonical_style(style: Option<&str>) -> &'static str {
    match style
        .unwrap_or_default()
        .trim()
        .to_ascii_lowercase()
        .as_str()
    {
        "holo_glass" | "style1" | "futuristic" | "glass" => "holo_glass",
        "neon_cyan" | "neon" | "style2" => "neon_cyan",
        "tactical_dark" | "minimal" | "style3" => "tactical_dark",
        "warning_amber" => "warning_amber",
        "minimal_light" => "minimal_light",
        _ => "default",
    }
}

fn transparency(
    options: &EntityOptions,
    vars: &SceneMaxVars,
    guards: &HashMap<String, Condition>,
) -> f32 {
    let value = options
        .label
        .as_ref()
        .and_then(|label| label.transparency.as_ref())
        .and_then(|value| {
            resolve_assignment_value_scoped_with_guards(value, vars, None, guards, None, None)
        })
        .unwrap_or(0.);
    if value.is_finite() {
        value.clamp(0., 100.)
    } else {
        0.
    }
}

fn palette(style: &str, transparency: f32) -> [Color; 3] {
    let colors = match style {
        "holo_glass" => [
            [0.04, 0.12, 0.16, 0.52],
            [0.82, 0.98, 1., 0.96],
            [0.2, 0.9, 1., 0.8],
        ],
        "neon_cyan" => [
            [0.02, 0.05, 0.08, 0.48],
            [0.78, 1., 1., 0.97],
            [0., 0.95, 1., 0.9],
        ],
        "tactical_dark" => [
            [0.02, 0.03, 0.04, 0.6],
            [0.92, 0.98, 1., 0.96],
            [0.54, 0.72, 0.92, 0.75],
        ],
        "warning_amber" => [
            [0.16, 0.09, 0.02, 0.58],
            [1., 0.93, 0.72, 0.97],
            [1., 0.62, 0.18, 0.86],
        ],
        "minimal_light" => [
            [0.88, 0.96, 1., 0.5],
            [0.04, 0.08, 0.11, 0.95],
            [0.22, 0.62, 0.8, 0.7],
        ],
        _ => [
            [0.03, 0.1, 0.13, 0.48],
            [0.86, 0.98, 1., 0.95],
            [0.45, 0.95, 1., 0.72],
        ],
    };
    colors.map(|[r, g, b, a]| Color::srgba(r, g, b, a * (1. - transparency / 100.)))
}

pub(super) fn text(
    value: &AssignmentValue,
    vars: &SceneMaxVars,
    scope: Option<&SceneMaxScopeFrame>,
    guards: &HashMap<String, Condition>,
    transforms: &HashMap<String, Transform>,
) -> String {
    resolve_script_value(value, vars, scope, guards, Some(transforms), None)
        .map(|value| scenemax_runtime_vm_core::typed::text(&value))
        .unwrap_or_default()
}

pub(super) fn spawn(
    commands: &mut Commands,
    name: &str,
    options: &EntityOptions,
    transform: Transform,
    vars: &SceneMaxVars,
    guards: &HashMap<String, Condition>,
) -> Entity {
    let text = options
        .label
        .as_ref()
        .and_then(|label| label.text.as_ref())
        .map(|value| text(value, vars, None, guards, &HashMap::new()))
        .unwrap_or_else(|| name.to_owned());
    commands
        .spawn((
            SceneMaxEntity {
                name: name.to_owned(),
                runtime_name: format!("{name}@label"),
            },
            Label {
                text: text.clone(),
                font: options.label.as_ref().and_then(|label| label.font.clone()),
                font_ready: false,
                bitmap_font: None,
                size: options.size.map(|v| Vec2::new(v.x.max(0.1), v.y.max(0.1))),
                style: canonical_style(
                    options
                        .label
                        .as_ref()
                        .and_then(|label| label.style.as_deref()),
                ),
                transparency: transparency(options, vars, guards),
                visual: None,
                dirty: true,
            },
            Text2d::new(text),
            TextFont::from_font_size(64.),
            // Text is drawn by the 3D glyph meshes, never by the sprite renderer.
            bevy::camera::visibility::RenderLayers::none(),
            transform,
            if options.hidden {
                Visibility::Hidden
            } else {
                Visibility::Inherited
            },
            SceneMaxRuntimeVisibility {
                visible: !options.hidden,
            },
        ))
        .id()
}

// Keep placeholder expansion from rewriting braces inside user-authored text.
pub(super) fn wire_text(value: &str) -> String {
    serde_json::to_string(value)
        .expect("string serialization")
        .replace('{', "\\u007b")
        .replace('}', "\\u007d")
}

pub(super) fn spawn_source(label: &Label, options: &EntityOptions, scale: Vec3) -> String {
    let mut attrs = vec![format!("text {}", wire_text(&label.text))];
    if let Some(font) = label.font.as_deref() {
        attrs.push(format!("font {}", wire_text(font)));
    }
    if let Some(size) = label.size {
        attrs.push(format!("size ({},{})", size.x, size.y));
    }
    attrs.push(format!("style {}", wire_text(label.style)));
    attrs.push(format!("transparency {}", label.transparency));
    if options.hidden {
        attrs.push("hidden".into());
    }
    // Pose packets carry position/rotation only; scale belongs in the spawn.
    if scale != Vec3::ONE {
        attrs.push(format!("scale ({},{},{})", scale.x, scale.y, scale.z));
    }
    format!("{{network_entity}} => label: {}", attrs.join(", "))
}

pub(super) fn set_text(commands: &mut Commands, target: String, value: String) {
    commands.queue(move |world: &mut World| {
        let mut query = world.query::<(&SceneMaxEntity, &mut Label, &mut Text2d)>();
        for (entity, mut label, mut text) in query.iter_mut(world) {
            if entity.name == target && label.text != value {
                label.text.clone_from(&value);
                text.0.clone_from(&value);
                label.dirty = true;
            }
        }
    });
}

pub(super) fn reset(
    commands: &mut Commands,
    entity: Entity,
    name: &str,
    options: &EntityOptions,
    vars: &SceneMaxVars,
    guards: &HashMap<String, Condition>,
) {
    let value = options
        .label
        .as_ref()
        .and_then(|label| label.text.as_ref())
        .map(|value| text(value, vars, None, guards, &HashMap::new()))
        .unwrap_or_else(|| name.to_owned());
    let size = options.size.map(|v| Vec2::new(v.x.max(0.1), v.y.max(0.1)));
    let visible = !options.hidden;
    let font = options.label.as_ref().and_then(|label| label.font.clone());
    let style = canonical_style(
        options
            .label
            .as_ref()
            .and_then(|label| label.style.as_deref()),
    );
    let transparency = transparency(options, vars, guards);
    commands.queue(move |world: &mut World| {
        let Ok(mut entity) = world.get_entity_mut(entity) else {
            return;
        };
        let Some(mut label) = entity.get_mut::<Label>() else {
            return;
        };
        label.text.clone_from(&value);
        label.font = font;
        label.font_ready = false;
        label.bitmap_font = None;
        label.size = size;
        label.style = style;
        label.transparency = transparency;
        label.dirty = true;
        entity.insert((Text2d::new(value), SceneMaxRuntimeVisibility { visible }));
    });
}

// A fixed shaping scale keeps world geometry independent of monitor DPI/camera.
pub(super) fn layout(
    mut commands: Commands,
    fonts: Res<Assets<Font>>,
    mut images: ResMut<Assets<Image>>,
    mut atlases: ResMut<FontAtlasSet>,
    mut pipeline: ResMut<TextPipeline>,
    mut font_cx: ResMut<FontCx>,
    mut layout_cx: ResMut<LayoutCx>,
    mut scale_cx: ResMut<ScaleCx>,
    mut reader: Text2dReader,
    mut labels: Query<(
        Entity,
        &SceneMaxEntity,
        &mut Label,
        &mut ComputedTextBlock,
        &mut TextLayoutInfo,
    )>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
) {
    for (entity, scene, mut label, mut computed, mut info) in &mut labels {
        if !label.dirty || !label.font_ready {
            continue;
        }
        let (text_size, glyphs) = if let Some(font) = label.bitmap_font.as_ref() {
            if font.images.values().any(|image|!images.contains(image.id())) {
                continue;
            }
            fonts::bitmap_layout(font, &label.text)
        } else {
            let bounds = TextBounds::default();
            if pipeline
                .update_buffer(
                    &fonts,
                    reader.iter(entity),
                    bevy::text::LineBreak::NoWrap,
                    Justify::Center,
                    bounds,
                    1.,
                    &mut computed,
                    &mut font_cx,
                    &mut layout_cx,
                    Vec2::splat(1000.),
                    16.,
                )
                .is_err()
            {
                continue;
            }
            if pipeline
                .update_text_layout_info(
                    &mut info,
                    &mut atlases,
                    &mut images,
                    &mut computed,
                    &mut scale_cx,
                    bounds,
                    Justify::Center,
                    FontHinting::Disabled,
                )
                .is_err()
            {
                continue;
            }
            (
                info.size,
                info.glyphs
                    .iter()
                    .map(|glyph| fonts::Glyph {
                        texture: glyph.atlas_info.texture,
                        rect: glyph.atlas_info.rect,
                        position: glyph.position,
                        size: glyph.atlas_info.rect.size(),
                        is_alpha_mask: glyph.atlas_info.is_alpha_mask,
                    })
                    .collect(),
            )
        };
        if let Some(old) = label.visual.take() {
            commands.entity(old).despawn();
        }
        let visual = commands
            .spawn((
                LabelVisual,
                Transform::default(),
                Visibility::Inherited,
                ChildOf(entity),
            ))
            .id();
        let scale = label
            .size
            .map(|size| {
                ((size.x - 0.32).max(0.05) / text_size.x.max(1.))
                    .min(size.y * 0.55 / text_size.y.max(1.))
            })
            .unwrap_or(0.35 / 64.);
        let size = label.size.unwrap_or(Vec2::new(
            (text_size.x * scale).max(0.65) + 0.5,
            (text_size.y * scale).max(0.35) + 0.28,
        ));
        let colors = palette(label.style, label.transparency);
        let panel = materials.add(StandardMaterial {
            base_color: colors[0],
            unlit: true,
            alpha_mode: AlphaMode::Blend,
            cull_mode: None,
            ..default()
        });
        commands.spawn((
            Mesh3d(meshes.add(Rectangle::new(size.x, size.y))),
            MeshMaterial3d(panel),
            Transform::from_xyz(0., 0., -0.02),
            ChildOf(visual),
        ));
        let accent_height = (size.y * 0.055).max(0.025);
        let accent = materials.add(StandardMaterial {
            base_color: colors[2],
            unlit: true,
            alpha_mode: AlphaMode::Blend,
            cull_mode: None,
            ..default()
        });
        commands.spawn((
            Mesh3d(meshes.add(Rectangle::new(size.x, accent_height))),
            MeshMaterial3d(accent),
            Transform::from_xyz(0., -size.y / 2. + accent_height / 2., -0.01),
            ChildOf(visual),
        ));
        let mut page_materials = HashMap::new();
        for glyph in &glyphs {
            let atlas = glyph;
            let Some(image) = images.get(atlas.texture) else {
                continue;
            };
            let dimensions = image.size().as_vec2();
            let Some(texture) = images.get_strong_handle(atlas.texture) else {
                continue;
            };
            let material = page_materials
                .entry((atlas.texture, atlas.is_alpha_mask))
                .or_insert_with(|| {
                    materials.add(StandardMaterial {
                        base_color: if atlas.is_alpha_mask {
                            colors[1]
                        } else {
                            Color::WHITE.with_alpha(colors[1].alpha())
                        },
                        base_color_texture: Some(texture),
                        unlit: true,
                        alpha_mode: AlphaMode::Blend,
                        cull_mode: None,
                        ..default()
                    })
                })
                .clone();
            let min = atlas.rect.min / dimensions;
            let max = atlas.rect.max / dimensions;
            let mut mesh = Mesh::from(Rectangle::from_size(glyph.size * scale));
            // Rectangle vertices: top-right, top-left, bottom-left, bottom-right.
            mesh.insert_attribute(
                Mesh::ATTRIBUTE_UV_0,
                vec![
                    [max.x, min.y],
                    [min.x, min.y],
                    [min.x, max.y],
                    [max.x, max.y],
                ],
            );
            let position = (glyph.position - text_size / 2.) * scale;
            commands.spawn((
                Mesh3d(meshes.add(mesh)),
                MeshMaterial3d(material),
                Transform::from_xyz(position.x, -position.y, 0.),
                ChildOf(visual),
            ));
        }
        label.visual = Some(visual);
        label.dirty = false;
        if std::env::var_os("SCENEMAX_ATTACHMENT_AUDIT").is_some() {
            write_runtime_diagnostic_line(format!(
                "LABEL:LAYOUT {} text={} glyphs={} font={}",
                scene.name,
                serde_json::Value::String(label.text.clone()),
                glyphs.len(),
                if label.bitmap_font.is_some() {
                    label.font.as_deref().unwrap_or("default")
                } else {
                    "default"
                }
            ));
        }
    }
}

pub(super) fn billboard(
    cameras: Query<(&Camera, &GlobalTransform), With<Camera3d>>,
    roots: Query<&GlobalTransform, With<Label>>,
    mut visuals: Query<
        (&ChildOf, &mut Transform),
        (With<LabelVisual>, Without<Label>, Without<Camera3d>),
    >,
) {
    let Some((_, camera)) = cameras.iter().find(|(camera, _)| camera.is_active) else {
        return;
    };
    for (parent, mut local) in &mut visuals {
        let Ok(root) = roots.get(parent.parent()) else {
            continue;
        };
        let (_, rotation, _) = root.to_scale_rotation_translation();
        local.rotation = rotation.inverse() * camera.rotation();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn styles_and_evaluated_transparency_survive_snapshot_and_text_updates() {
        let program = scenemax_parser::parse_program("caption => label: multiplayer, text \"styled\", style \"Glass\", transparency fade + 5").unwrap();
        let statement = scenemax_runtime_script_core::substitute_statement(
            &program.statements[0],
            &HashMap::from([("fade".into(), "20".into())]),
        );
        let Statement::ModelDecl { name, options, .. } = statement else {
            panic!()
        };
        let mut world = World::new();
        let entity = spawn(
            &mut world.commands(),
            &name,
            &options,
            Transform::default(),
            &SceneMaxVars::default(),
            &HashMap::new(),
        );
        world.flush();
        let label = world.get::<Label>(entity).unwrap();
        assert_eq!(label.style, "holo_glass");
        assert_eq!(label.transparency, 25.);
        let colors = palette(label.style, label.transparency);
        for (color, alpha) in colors.into_iter().zip([0.52, 0.96, 0.8]) {
            assert!((color.alpha() - alpha * 0.75).abs() < 0.00001);
        }
        let source = spawn_source(label, &options, Vec3::ONE).replace("{network_entity}", "remote");
        let p = scenemax_parser::parse_program(&source).unwrap();
        let Statement::ModelDecl {
            options: remote, ..
        } = &p.statements[0]
        else {
            panic!()
        };
        assert_eq!(
            remote.label.as_ref().unwrap().style.as_deref(),
            Some("holo_glass")
        );
        assert_eq!(
            transparency(remote, &SceneMaxVars::default(), &HashMap::new()),
            25.
        );
        set_text(&mut world.commands(), name, "changed".into());
        world.flush();
        let label = world.get::<Label>(entity).unwrap();
        assert_eq!((label.style, label.transparency), ("holo_glass", 25.));
        for (value, expected) in [(-5., 0.), (150., 100.), (f32::NAN, 0.), (f32::INFINITY, 0.)] {
            let mut options = options.clone();
            options.label.as_mut().unwrap().transparency = Some(AssignmentValue::Number(value));
            assert_eq!(
                transparency(&options, &SceneMaxVars::default(), &HashMap::new()),
                expected
            );
        }
        for (alias, canonical) in [
            ("STYLE1", "holo_glass"),
            ("neon", "neon_cyan"),
            ("minimal", "tactical_dark"),
            ("warning_amber", "warning_amber"),
            ("minimal_light", "minimal_light"),
            ("unknown", "default"),
        ] {
            assert_eq!(canonical_style(Some(alias)), canonical);
            assert!(
                palette(canonical, 100.)
                    .iter()
                    .all(|color| color.alpha() == 0.)
            );
        }
    }

    #[test]
    fn label_snapshot_evaluates_text_and_changes_without_replacing_entity() {
        let program = scenemax_parser::parse_program(
            "caption => label: multiplayer, text message, font \"project_font\", size (3,1)",
        )
        .unwrap();
        let Statement::ModelDecl { name, options, .. } = &program.statements[0] else {
            panic!()
        };
        let mut vars = SceneMaxVars::default();
        let message = "Quoted \"text\", slash \\ and Unicode Ω {network_entity}";
        scenemax_runtime_vm_core::typed::store(&mut vars, "message", serde_json::json!(message));
        let mut world = World::new();
        let entity = spawn(
            &mut world.commands(),
            name,
            options,
            Transform::default(),
            &vars,
            &HashMap::new(),
        );
        world.flush();
        let label = world.get::<Label>(entity).unwrap();
        assert_eq!(label.text, message);
        let source = spawn_source(label, options, Vec3::ONE).replace("{network_entity}", "remote");
        let remote = scenemax_parser::parse_program(&source).unwrap();
        let Statement::ModelDecl { options, .. } = &remote.statements[0] else {
            panic!()
        };
        assert!(!options.multiplayer);
        assert_eq!(
            options.label.as_ref().unwrap().font.as_deref(),
            Some("project_font")
        );
        assert_eq!(
            text(
                options.label.as_ref().unwrap().text.as_ref().unwrap(),
                &SceneMaxVars::default(),
                None,
                &HashMap::new(),
                &HashMap::new()
            ),
            message
        );
        set_text(&mut world.commands(), name.clone(), "new text".into());
        world.flush();
        assert_eq!(world.get::<Text2d>(entity).unwrap().0, "new text");
        assert!(world.get::<Label>(entity).unwrap().dirty);
        let visual = world.spawn(ChildOf(entity)).id();
        world.entity_mut(entity).despawn();
        assert!(world.get_entity(visual).is_err());
    }
}
