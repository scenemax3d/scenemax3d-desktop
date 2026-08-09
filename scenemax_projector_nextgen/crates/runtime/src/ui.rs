use super::*;

pub(super) fn scenemax_ui_action_from_statement(action: &Statement) -> Option<SceneMaxUiAction> {
    match action {
        Statement::UiLoad { name } => Some(SceneMaxUiAction::Load { name: name.clone() }),
        Statement::UiShowHide(show_hide) => Some(SceneMaxUiAction::ShowHide {
            target: show_hide.target.clone(),
            visible: show_hide.visible,
        }),
        Statement::UiMessage(message) => Some(SceneMaxUiAction::Message {
            target: message.target.clone(),
            text: message.text.clone(),
            effects: message.effects.clone(),
            duration_seconds: message.duration_seconds,
        }),
        Statement::UiEase(ease) => Some(SceneMaxUiAction::Ease {
            target: ease.target.clone(),
            easing: ease.easing.clone(),
            direction: ease.direction,
            duration_seconds: ease.duration_seconds,
        }),
        Statement::UiSetProperty(property) => Some(SceneMaxUiAction::SetProperty {
            target: property.target.clone(),
            property: property.property.clone(),
            value: property.value.clone(),
        }),
        _ => None,
    }
}

pub(super) fn apply_scenemax_ui_actions(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    mut ui_runtime: ResMut<SceneMaxUiRuntime>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut text_query: Query<(
        &mut Text,
        &mut TextColor,
        &mut UiTransform,
        Option<&SceneMaxUiTextVisualState>,
    )>,
    mut visibility_query: Query<&mut Visibility>,
) {
    let actions = ui_queue.actions.drain(..).collect::<Vec<_>>();
    if actions.is_empty() {
        return;
    }
    for action in actions {
        match action {
            SceneMaxUiAction::Load { name } => {
                if let Err(error) = load_scenemax_ui_document(
                    &name,
                    &mut commands,
                    &asset_server,
                    &context,
                    &mut ui_runtime,
                ) {
                    tracing::warn!(name, %error, "failed to load SceneMax UI document");
                    write_runtime_diagnostic_line(format!("failed to load UI {name}: {error}"));
                }
            }
            SceneMaxUiAction::ShowHide { target, visible } => {
                if let Some(target) = resolve_ui_target(&ui_runtime, &target) {
                    if let Ok(mut visibility_component) = visibility_query.get_mut(target.entity) {
                        *visibility_component = if visible {
                            Visibility::Inherited
                        } else {
                            Visibility::Hidden
                        };
                    } else {
                        commands.entity(target.entity).insert(if visible {
                            Visibility::Inherited
                        } else {
                            Visibility::Hidden
                        });
                    }
                }
            }
            SceneMaxUiAction::Message {
                target,
                text,
                effects,
                duration_seconds,
            } => {
                if let Some(target) = resolve_ui_target(&ui_runtime, &target)
                    && let Some(text_entity) = target.text_entity
                    && let Ok((mut text_component, mut text_color, mut transform, visual_state)) =
                        text_query.get_mut(text_entity)
                {
                    let visual_state = visual_state.copied().unwrap_or(SceneMaxUiTextVisualState {
                        base_color: text_color.0,
                        base_transform: *transform,
                    });
                    commands
                        .entity(text_entity)
                        .remove::<SceneMaxUiMessageAnimation>()
                        .insert(visual_state);
                    let effect_names = scenemax_runtime_ui_core::parse_effects(&effects);
                    if scenemax_runtime_ui_core::should_animate(&effect_names)
                        && duration_seconds > f32::EPSILON
                    {
                        apply_ui_message_progress(
                            &mut text_component,
                            &mut text_color,
                            &mut transform,
                            &text,
                            &effect_names,
                            0.0,
                            visual_state.base_color,
                            visual_state.base_transform,
                        );
                        commands
                            .entity(text_entity)
                            .insert(SceneMaxUiMessageAnimation {
                                full_text: text,
                                effect_names,
                                elapsed_seconds: 0.0,
                                duration_seconds: duration_seconds.max(0.001),
                                base_color: visual_state.base_color,
                                base_transform: visual_state.base_transform,
                            });
                    } else {
                        text_component.0 = text;
                        text_color.0 = visual_state.base_color;
                        *transform = visual_state.base_transform;
                    }
                }
            }
            SceneMaxUiAction::Ease {
                target,
                easing,
                direction,
                duration_seconds,
            } => {
                if let Some(target_path) = resolve_ui_target_path(&ui_runtime, &target)
                    && let Some(target) = resolve_ui_target(&ui_runtime, &target)
                {
                    let start = ui_ease_start_offset(direction);
                    commands.entity(target.entity).insert((
                        Visibility::Inherited,
                        UiTransform::from_translation(Val2::percent(start.x, start.y)),
                        SceneMaxUiEase {
                            start,
                            elapsed_seconds: 0.0,
                            duration_seconds: duration_seconds.max(0.001),
                            easing,
                        },
                    ));
                    tracing::debug!(target = target_path, "started SceneMax UI ease");
                }
            }
            SceneMaxUiAction::SetProperty {
                target,
                property,
                value,
            } => {
                if property.eq_ignore_ascii_case("text") {
                    if let Some(target) = resolve_ui_target(&ui_runtime, &target)
                        && let Some(text_entity) = target.text_entity
                        && let Ok((mut text_component, mut text_color, mut transform, visual_state)) =
                            text_query.get_mut(text_entity)
                    {
                        let visual_state =
                            visual_state.copied().unwrap_or(SceneMaxUiTextVisualState {
                                base_color: text_color.0,
                                base_transform: *transform,
                            });
                        commands
                            .entity(text_entity)
                            .remove::<SceneMaxUiMessageAnimation>()
                            .insert(visual_state);
                        text_component.0 = value;
                        text_color.0 = visual_state.base_color;
                        *transform = visual_state.base_transform;
                    }
                } else if property.eq_ignore_ascii_case("visible") {
                    let visible = !matches!(value.to_ascii_lowercase().as_str(), "0" | "false");
                    if let Some(target) = resolve_ui_target(&ui_runtime, &target) {
                        commands.entity(target.entity).insert(if visible {
                            Visibility::Inherited
                        } else {
                            Visibility::Hidden
                        });
                    }
                }
            }
        }
    }
}

pub(super) fn update_scenemax_ui_eases(
    time: Res<Time>,
    mut commands: Commands,
    mut query: Query<(Entity, &mut UiTransform, &mut SceneMaxUiEase)>,
) {
    for (entity, mut transform, mut ease) in &mut query {
        ease.elapsed_seconds += time.delta_secs();
        let t = (ease.elapsed_seconds / ease.duration_seconds).clamp(0.0, 1.0);
        let eased = ui_ease_progress(t, &ease.easing);
        let offset = ease.start * (1.0 - eased);
        transform.translation = Val2::percent(offset.x, offset.y);
        if t >= 1.0 {
            transform.translation = Val2::ZERO;
            commands.entity(entity).remove::<SceneMaxUiEase>();
        }
    }
}

pub(super) fn update_scenemax_ui_message_animations(
    time: Res<Time>,
    mut commands: Commands,
    mut query: Query<(
        Entity,
        &mut Text,
        &mut TextColor,
        &mut UiTransform,
        &mut SceneMaxUiMessageAnimation,
    )>,
) {
    for (entity, mut text, mut text_color, mut transform, mut animation) in &mut query {
        animation.elapsed_seconds += time.delta_secs();
        let progress = (animation.elapsed_seconds / animation.duration_seconds).clamp(0.0, 1.0);
        apply_ui_message_progress(
            &mut text,
            &mut text_color,
            &mut transform,
            &animation.full_text,
            &animation.effect_names,
            progress,
            animation.base_color,
            animation.base_transform,
        );
        if progress >= 1.0 {
            text.0 = animation.full_text.clone();
            text_color.0 = animation.base_color;
            *transform = animation.base_transform;
            commands
                .entity(entity)
                .remove::<SceneMaxUiMessageAnimation>();
        }
    }
}

pub(super) fn apply_ui_message_progress(
    text: &mut Text,
    text_color: &mut TextColor,
    transform: &mut UiTransform,
    full_text: &str,
    effect_names: &[String],
    progress: f32,
    base_color: Color,
    base_transform: UiTransform,
) {
    let frame = scenemax_runtime_ui_core::evaluate_message_frame(full_text, effect_names, progress);
    text.0 = frame.visible_text;
    text_color.0 = base_color.with_alpha(base_color.alpha() * frame.alpha);
    *transform = base_transform;
    transform.scale = base_transform.scale * frame.scale;
}

pub(super) fn load_scenemax_ui_document(
    name: &str,
    commands: &mut Commands,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    ui_runtime: &mut SceneMaxUiRuntime,
) -> Result<()> {
    if ui_runtime.loaded.contains_key(name) {
        ui_runtime.active_ui_name = Some(name.to_owned());
        return Ok(());
    }
    let path = resolve_scenemax_ui_path(name, context)?;
    let source = fs::read_to_string(&path)?;
    let doc: SceneMaxUiDocument = serde_json::from_str(&source)?;
    let ui_name = doc.name.clone();
    let ui_scale = document_scale(&doc, context.window_width, context.window_height);
    refresh_sprite_index(ui_runtime, context);

    let mut loaded = LoadedSceneMaxUi::default();
    for layer in &doc.layers {
        let root = commands
            .spawn((
                Name::new(format!("UI.{}.{}", ui_name, layer.name)),
                Node {
                    position_type: PositionType::Absolute,
                    left: Val::ZERO,
                    top: Val::ZERO,
                    width: Val::Percent(100.0),
                    height: Val::Percent(100.0),
                    ..default()
                },
                UiTransform::default(),
                if layer.visible {
                    Visibility::Inherited
                } else {
                    Visibility::Hidden
                },
                GlobalZIndex(layer.z_order),
            ))
            .id();
        loaded.root_entities.push(root);
        loaded.layer_entities.insert(layer.name.clone(), root);
        loaded.targets.insert(
            target_key(&ui_name, &layer.name, &[]),
            SceneMaxUiTarget {
                entity: root,
                text_entity: None,
            },
        );

        let local_rects = solve_widget_layout(&layer.widgets, doc.canvas_width, doc.canvas_height);
        for widget in sorted_widgets(&layer.widgets) {
            spawn_scenemax_ui_widget(
                commands,
                asset_server,
                context,
                ui_runtime,
                &ui_name,
                &layer.name,
                Vec::new(),
                widget,
                root,
                UiLayoutRect {
                    x: 0.0,
                    y: 0.0,
                    width: doc.canvas_width,
                    height: doc.canvas_height,
                },
                ui_scale,
                &local_rects,
                &mut loaded,
            );
        }
    }

    ui_runtime.active_ui_name = Some(ui_name.clone());
    ui_runtime.loaded.insert(ui_name.clone(), loaded);
    tracing::info!(name = ui_name, path = %path.display(), "loaded SceneMax UI document");
    write_runtime_diagnostic_line(format!("loaded UI {ui_name} from {}", path.display()));
    Ok(())
}

pub(super) fn resolve_scenemax_ui_path(
    name: &str,
    context: &SceneMaxLaunchContext,
) -> Result<PathBuf> {
    let file_name = if name.ends_with(".smui") {
        name.to_owned()
    } else {
        format!("{name}.smui")
    };
    let mut candidates = Vec::new();
    if let Some(script_root) = context.script_root.as_ref() {
        candidates.push(script_root.join(&file_name));
    }
    if let Some(asset_root) = context.asset_root.as_ref()
        && let Some(project_root) = asset_root.parent()
    {
        candidates.push(project_root.join("scripts").join(&file_name));
    }
    candidates
        .into_iter()
        .find(|candidate| candidate.is_file())
        .ok_or_else(|| anyhow::anyhow!("UI document {file_name} was not found"))
}

pub(super) fn spawn_scenemax_ui_widget(
    commands: &mut Commands,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    ui_runtime: &SceneMaxUiRuntime,
    ui_name: &str,
    layer_name: &str,
    parent_path: Vec<String>,
    widget: &SceneMaxUiWidgetDef,
    parent_entity: Entity,
    parent_rect: UiLayoutRect,
    ui_scale: f32,
    sibling_rects: &HashMap<String, UiLayoutRect>,
    loaded: &mut LoadedSceneMaxUi,
) {
    let Some(rect) = sibling_rects.get(&widget.name).copied() else {
        return;
    };
    let mut widget_path = parent_path.clone();
    widget_path.push(widget.name.clone());
    let local_rect = UiLayoutRect {
        x: rect.x - parent_rect.x,
        y: rect.y - parent_rect.y,
        width: rect.width,
        height: rect.height,
    };
    let node = node_from_rect(local_rect, parent_rect);
    let key = target_key(ui_name, layer_name, &widget_path);
    let visibility = if widget.visible {
        Visibility::Inherited
    } else {
        Visibility::Hidden
    };
    let base_marker = SceneMaxUiWidget {
        ui_name: ui_name.to_owned(),
        layer: layer_name.to_owned(),
        widget_path: widget_path.clone(),
    };

    let (entity, text_entity) = match widget.widget_type.as_str() {
        "TEXT_VIEW" | "EDIT_TEXT" => {
            let text_color = parse_ui_color(&widget.text_color);
            let text_transform = UiTransform::default();
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    text_transform,
                    visibility,
                    ZIndex(widget.z_order),
                    Text::new(widget.text.clone()),
                    TextFont {
                        font_size: FontSize::Px(scaled_font_size(widget.font_size, ui_scale)),
                        ..default()
                    },
                    TextColor(text_color),
                    TextLayout::justify(ui_text_justify(&widget.text_alignment)).with_no_wrap(),
                    SceneMaxUiTextVisualState {
                        base_color: text_color,
                        base_transform: text_transform,
                    },
                    base_marker,
                ))
                .id();
            (entity, Some(entity))
        }
        "BUTTON" => {
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    BackgroundColor(parse_ui_color(&widget.button_color)),
                    UiTransform::default(),
                    visibility,
                    ZIndex(widget.z_order),
                    base_marker,
                ))
                .id();
            let text = commands
                .spawn((
                    Node {
                        position_type: PositionType::Absolute,
                        left: Val::ZERO,
                        top: Val::ZERO,
                        width: Val::Percent(100.0),
                        height: Val::Percent(100.0),
                        ..default()
                    },
                    Text::new(widget.button_text.clone()),
                    TextFont {
                        font_size: FontSize::Px(scaled_font_size(widget.font_size, ui_scale)),
                        ..default()
                    },
                    TextColor(parse_ui_color(&widget.button_text_color)),
                    TextLayout::justify(Justify::Center).with_no_wrap(),
                    SceneMaxUiTextVisualState {
                        base_color: parse_ui_color(&widget.button_text_color),
                        base_transform: UiTransform::default(),
                    },
                ))
                .id();
            commands.entity(entity).add_child(text);
            (entity, Some(text))
        }
        "IMAGE" => {
            let image_node = ui_image_node_for_widget(widget, asset_server, context, ui_runtime);
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    image_node,
                    UiTransform::default(),
                    visibility,
                    ZIndex(widget.z_order),
                    base_marker,
                ))
                .id();
            (entity, None)
        }
        "LIST_VIEW" => {
            let text = list_view_text(widget);
            let text_color = parse_ui_color(&widget.text_color);
            let text_transform = UiTransform::default();
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    BackgroundColor(parse_ui_color(&widget.background_color)),
                    text_transform,
                    visibility,
                    ZIndex(widget.z_order),
                    Text::new(text),
                    TextFont {
                        font_size: FontSize::Px(scaled_font_size(
                            widget.list_row_font_size,
                            ui_scale,
                        )),
                        ..default()
                    },
                    TextColor(text_color),
                    TextLayout::justify(Justify::Left).with_no_wrap(),
                    SceneMaxUiTextVisualState {
                        base_color: text_color,
                        base_transform: text_transform,
                    },
                    base_marker,
                ))
                .id();
            (entity, Some(entity))
        }
        _ => {
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
                    BackgroundColor(parse_ui_color(&widget.background_color)),
                    UiTransform::default(),
                    visibility,
                    ZIndex(widget.z_order),
                    base_marker,
                ))
                .id();
            (entity, None)
        }
    };
    commands.entity(parent_entity).add_child(entity);
    loaded.targets.insert(
        key,
        SceneMaxUiTarget {
            entity,
            text_entity,
        },
    );

    if !widget.children.is_empty() {
        let child_parent_rect = UiLayoutRect {
            x: rect.x + widget.padding_left,
            y: rect.y + widget.padding_top,
            width: (rect.width - widget.padding_left - widget.padding_right).max(1.0),
            height: (rect.height - widget.padding_top - widget.padding_bottom).max(1.0),
        };
        let child_rects = solve_widget_layout(
            &widget.children,
            child_parent_rect.width,
            child_parent_rect.height,
        )
        .into_iter()
        .map(|(name, mut child_rect)| {
            child_rect.x += child_parent_rect.x;
            child_rect.y += child_parent_rect.y;
            (name, child_rect)
        })
        .collect::<HashMap<_, _>>();
        for child in sorted_widgets(&widget.children) {
            spawn_scenemax_ui_widget(
                commands,
                asset_server,
                context,
                ui_runtime,
                ui_name,
                layer_name,
                widget_path.clone(),
                child,
                entity,
                child_parent_rect,
                ui_scale,
                &child_rects,
                loaded,
            );
        }
    }
}

pub(super) fn node_from_rect(rect: UiLayoutRect, parent_rect: UiLayoutRect) -> Node {
    Node {
        position_type: PositionType::Absolute,
        left: Val::Percent(percent(rect.x, parent_rect.width)),
        top: Val::Percent(percent(rect.y, parent_rect.height)),
        width: Val::Percent(percent(rect.width, parent_rect.width)),
        height: Val::Percent(percent(rect.height, parent_rect.height)),
        ..default()
    }
}

pub(super) fn parse_ui_color(hex: &str) -> Color {
    let trimmed = hex.trim().trim_start_matches('#');
    if trimmed.len() < 6 {
        return Color::WHITE;
    }
    let parse =
        |range: std::ops::Range<usize>| u8::from_str_radix(&trimmed[range], 16).unwrap_or(255);
    let alpha = if trimmed.len() >= 8 { parse(6..8) } else { 255 };
    Color::srgba_u8(parse(0..2), parse(2..4), parse(4..6), alpha)
}

pub(super) fn ui_text_justify(alignment: &str) -> Justify {
    match alignment.to_ascii_lowercase().as_str() {
        "center" => Justify::Center,
        "right" => Justify::Right,
        _ => Justify::Left,
    }
}

pub(super) fn resolve_ui_target<'a>(
    ui_runtime: &'a SceneMaxUiRuntime,
    path: &UiTargetPath,
) -> Option<&'a SceneMaxUiTarget> {
    let target_path = resolve_ui_target_path(ui_runtime, path)?;
    let ui_name = path
        .ui_name
        .as_deref()
        .or(ui_runtime.active_ui_name.as_deref())?;
    ui_runtime
        .loaded
        .get(ui_name)
        .and_then(|loaded| loaded.targets.get(&target_path))
}

pub(super) fn resolve_ui_target_path(
    ui_runtime: &SceneMaxUiRuntime,
    path: &UiTargetPath,
) -> Option<String> {
    let ui_name = path
        .ui_name
        .as_deref()
        .or(ui_runtime.active_ui_name.as_deref())?;
    if path.widget_path.is_empty() {
        return Some(target_key(ui_name, &path.layer, &[]));
    }
    Some(target_key(ui_name, &path.layer, &path.widget_path))
}

pub(super) fn ui_ease_start_offset(direction: UiEaseDirection) -> Vec2 {
    match direction {
        UiEaseDirection::Up => Vec2::new(0.0, 115.0),
        UiEaseDirection::Down => Vec2::new(0.0, -115.0),
        UiEaseDirection::Left => Vec2::new(-115.0, 0.0),
        UiEaseDirection::Right => Vec2::new(115.0, 0.0),
    }
}

pub(super) fn ui_ease_progress(t: f32, easing: &str) -> f32 {
    let t = t.clamp(0.0, 1.0);
    let lower = easing.to_ascii_lowercase();
    if lower.contains("bounce") {
        return ease_out_bounce(t);
    }
    if lower.contains("back") {
        let c1 = 1.70158;
        let c3 = c1 + 1.0;
        return c3 * t * t * t - c1 * t * t;
    }
    if lower.contains("cubic") {
        return t * t * t;
    }
    if lower.contains("quad") {
        return t * t;
    }
    t
}

pub(super) fn ease_out_bounce(t: f32) -> f32 {
    let n1 = 7.5625;
    let d1 = 2.75;
    if t < 1.0 / d1 {
        n1 * t * t
    } else if t < 2.0 / d1 {
        let t = t - 1.5 / d1;
        n1 * t * t + 0.75
    } else if t < 2.5 / d1 {
        let t = t - 2.25 / d1;
        n1 * t * t + 0.9375
    } else {
        let t = t - 2.625 / d1;
        n1 * t * t + 0.984375
    }
}

pub(super) fn ui_image_node_for_widget(
    widget: &SceneMaxUiWidgetDef,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    ui_runtime: &SceneMaxUiRuntime,
) -> ImageNode {
    let candidates = ui_image_asset_candidates(widget, context, ui_runtime);
    for candidate in candidates {
        if ui_asset_path_exists(&candidate, context) {
            return ImageNode::new(asset_server.load(candidate));
        }
    }
    ImageNode::solid_color(Color::srgba(0.9, 0.72, 0.38, 0.85))
}

pub(super) fn ui_image_asset_candidates(
    widget: &SceneMaxUiWidgetDef,
    context: &SceneMaxLaunchContext,
    ui_runtime: &SceneMaxUiRuntime,
) -> Vec<String> {
    let mut candidates = Vec::new();
    if let Some(sprite_name) = widget.sprite_name.as_deref() {
        if let Some(sprite) = ui_runtime.sprite_index.get(sprite_name) {
            candidates.push(normalize_asset_path(&sprite.path));
        }
        candidates.push(format!("sprites/{sprite_name}.png"));
        candidates.push(format!("sprites/{}.png", sprite_name.to_ascii_lowercase()));
        if context.builtin_asset_root.is_some() {
            candidates.push(format!("builtin://sprites/{sprite_name}.png"));
            candidates.push(format!(
                "builtin://sprites/{}.png",
                sprite_name.to_ascii_lowercase()
            ));
        }
    }
    if let Some(image_path) = widget.image_path.as_deref() {
        candidates.push(normalize_asset_path(image_path));
    }
    candidates
}

pub(super) fn normalize_asset_path(path: &str) -> String {
    path.replace('\\', "/")
        .trim_start_matches('/')
        .trim_start_matches("resources/")
        .to_owned()
}

pub(super) fn ui_asset_path_exists(asset_path: &str, context: &SceneMaxLaunchContext) -> bool {
    if let Some(relative) = asset_path.strip_prefix("builtin://") {
        return context
            .builtin_asset_root
            .as_ref()
            .is_some_and(|root| root.join(relative).is_file());
    }
    context
        .asset_root
        .as_ref()
        .is_some_and(|root| root.join(asset_path).is_file())
}

pub(super) fn refresh_sprite_index(
    ui_runtime: &mut SceneMaxUiRuntime,
    context: &SceneMaxLaunchContext,
) {
    let Some(asset_root) = context.asset_root.as_ref() else {
        return;
    };
    if ui_runtime.sprite_index_root.as_ref() == Some(asset_root) {
        return;
    }
    ui_runtime.sprite_index.clear();
    ui_runtime.sprite_index_root = Some(asset_root.clone());
    load_sprite_index_file(
        &asset_root.join("sprites").join("sprites.json"),
        "",
        ui_runtime,
    );
    if let Some(builtin_root) = context.builtin_asset_root.as_ref() {
        load_sprite_index_file(
            &builtin_root.join("sprites").join("sprites.json"),
            "builtin://",
            ui_runtime,
        );
    }
}

pub(super) fn load_sprite_index_file(
    path: &Path,
    prefix: &str,
    ui_runtime: &mut SceneMaxUiRuntime,
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
        ui_runtime.sprite_index.insert(
            name.to_owned(),
            SceneMaxSpriteAsset {
                path: format!("{prefix}{}", normalize_asset_path(path)),
                rows,
                cols,
            },
        );
    }
}
