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
        Statement::UiSetProperty(_) => None,
        _ => None,
    }
}

pub(super) fn clear_scenemax_ui_on_scene_change(
    mut commands: Commands,
    context: Res<SceneMaxLaunchContext>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut ui_runtime: ResMut<SceneMaxUiRuntime>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
) {
    let current_scene_root = startup_program
        .1
        .clone()
        .or_else(|| context.script_root.clone());
    if ui_runtime.scene_script_root == current_scene_root {
        return;
    }

    if ui_runtime.scene_script_root.is_none() && ui_runtime.loaded.is_empty() {
        ui_runtime.scene_script_root = current_scene_root;
        return;
    }

    let ui_count = ui_runtime.loaded.len();
    for loaded in ui_runtime.loaded.values() {
        for entity in &loaded.root_entities {
            commands.entity(*entity).despawn();
        }
    }

    ui_runtime.active_ui_name = None;
    ui_runtime.loaded.clear();
    ui_queue.actions.clear();
    ui_runtime.scene_script_root = current_scene_root;

    tracing::info!(ui_count, "cleared SceneMax UI after scene switch");
    write_runtime_diagnostic_line(format!(
        "cleared {ui_count} SceneMax UI document(s) after scene switch"
    ));
}

pub(super) fn apply_scenemax_ui_actions(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    context: Res<SceneMaxLaunchContext>,
    startup_program: Res<SceneMaxStartupProgram>,
    mut ui_runtime: ResMut<SceneMaxUiRuntime>,
    mut ui_queue: ResMut<SceneMaxUiActionQueue>,
    mut text_queries: ParamSet<(
        Query<(
            &mut Text,
            &mut TextColor,
            &mut UiTransform,
            Option<&SceneMaxUiTextVisualState>,
        )>,
        Query<(
            &mut SceneMaxUiBitmapText,
            &mut UiTransform,
            Option<&SceneMaxUiTextVisualState>,
        )>,
    )>,
    mut image_query: Query<(&mut ImageNode, Option<&SceneMaxUiSpriteSheet>)>,
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
                    startup_program.1.as_deref(),
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
                        text_queries.p0().get_mut(text_entity)
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
                } else if let Some(target) = resolve_ui_target(&ui_runtime, &target)
                    && let Some(text_entity) = target.text_entity
                    && let Ok((mut bitmap_text, mut transform, visual_state)) =
                        text_queries.p1().get_mut(text_entity)
                {
                    let visual_state = visual_state.copied().unwrap_or(SceneMaxUiTextVisualState {
                        base_color: bitmap_text.color,
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
                        apply_ui_bitmap_message_progress(
                            &mut commands,
                            text_entity,
                            &mut bitmap_text,
                            &mut transform,
                            &ui_runtime,
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
                        bitmap_text.text = text;
                        bitmap_text.color = visual_state.base_color;
                        *transform = visual_state.base_transform;
                        render_scenemax_bitmap_text(
                            &mut commands,
                            text_entity,
                            &mut bitmap_text,
                            &ui_runtime,
                        );
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
                    {
                        if let Ok((
                            mut text_component,
                            mut text_color,
                            mut transform,
                            visual_state,
                        )) = text_queries.p0().get_mut(text_entity)
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
                        } else if let Ok((mut bitmap_text, mut transform, visual_state)) =
                            text_queries.p1().get_mut(text_entity)
                        {
                            let visual_state =
                                visual_state.copied().unwrap_or(SceneMaxUiTextVisualState {
                                    base_color: bitmap_text.color,
                                    base_transform: *transform,
                                });
                            commands
                                .entity(text_entity)
                                .remove::<SceneMaxUiMessageAnimation>()
                                .insert(visual_state);
                            bitmap_text.text = value;
                            bitmap_text.color = visual_state.base_color;
                            *transform = visual_state.base_transform;
                            render_scenemax_bitmap_text(
                                &mut commands,
                                text_entity,
                                &mut bitmap_text,
                                &ui_runtime,
                            );
                        }
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
                } else if property.eq_ignore_ascii_case("frame")
                    && let Some(target) = resolve_ui_target(&ui_runtime, &target)
                    && let Ok((mut image_node, sprite_sheet)) = image_query.get_mut(target.entity)
                    && let Some(sprite_sheet) = sprite_sheet
                    && let Some(frame) = parse_ui_frame_value(&value)
                {
                    image_node.rect = Some(ui_sprite_frame_rect(
                        frame,
                        sprite_sheet.cols,
                        sprite_sheet.rows,
                        sprite_sheet.image_width,
                        sprite_sheet.image_height,
                    ));
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

pub(super) fn update_scenemax_ui_bitmap_message_animations(
    time: Res<Time>,
    mut commands: Commands,
    ui_runtime: Res<SceneMaxUiRuntime>,
    mut query: Query<(
        Entity,
        &mut SceneMaxUiBitmapText,
        &mut UiTransform,
        &mut SceneMaxUiMessageAnimation,
    )>,
) {
    for (entity, mut bitmap_text, mut transform, mut animation) in &mut query {
        animation.elapsed_seconds += time.delta_secs();
        let progress = (animation.elapsed_seconds / animation.duration_seconds).clamp(0.0, 1.0);
        apply_ui_bitmap_message_progress(
            &mut commands,
            entity,
            &mut bitmap_text,
            &mut transform,
            &ui_runtime,
            &animation.full_text,
            &animation.effect_names,
            progress,
            animation.base_color,
            animation.base_transform,
        );
        if progress >= 1.0 {
            bitmap_text.text = animation.full_text.clone();
            bitmap_text.color = animation.base_color;
            *transform = animation.base_transform;
            render_scenemax_bitmap_text(&mut commands, entity, &mut bitmap_text, &ui_runtime);
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

pub(super) fn apply_ui_bitmap_message_progress(
    commands: &mut Commands,
    entity: Entity,
    bitmap_text: &mut SceneMaxUiBitmapText,
    transform: &mut UiTransform,
    ui_runtime: &SceneMaxUiRuntime,
    full_text: &str,
    effect_names: &[String],
    progress: f32,
    base_color: Color,
    base_transform: UiTransform,
) {
    let frame = scenemax_runtime_ui_core::evaluate_message_frame(full_text, effect_names, progress);
    bitmap_text.text = frame.visible_text;
    bitmap_text.color = base_color.with_alpha(base_color.alpha() * frame.alpha);
    *transform = base_transform;
    transform.scale = base_transform.scale * frame.scale;
    render_scenemax_bitmap_text(commands, entity, bitmap_text, ui_runtime);
}

pub(super) fn load_scenemax_ui_document(
    name: &str,
    commands: &mut Commands,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    scene_script_root: Option<&Path>,
    ui_runtime: &mut SceneMaxUiRuntime,
) -> Result<()> {
    ui_runtime.scene_script_root = scene_script_root
        .map(Path::to_path_buf)
        .or_else(|| context.script_root.clone());
    if ui_runtime.loaded.contains_key(name) {
        ui_runtime.active_ui_name = Some(name.to_owned());
        return Ok(());
    }
    let path = resolve_scenemax_ui_path(name, context, scene_script_root)?;
    let source = fs::read_to_string(&path)?;
    let doc: SceneMaxUiDocument = serde_json::from_str(&source)?;
    let ui_name = doc.name.clone();
    let ui_scale = document_scale(&doc, context.window_width, context.window_height);
    refresh_sprite_index(ui_runtime, context);
    refresh_font_index(ui_runtime, context);

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
    scene_script_root: Option<&Path>,
) -> Result<PathBuf> {
    let file_name = if name.ends_with(".smui") {
        name.to_owned()
    } else {
        format!("{name}.smui")
    };
    let mut candidates = Vec::new();
    if let Some(scene_script_root) = scene_script_root {
        candidates.push(scene_script_root.join(&file_name));
    }
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
    ui_runtime: &mut SceneMaxUiRuntime,
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
            let entity = spawn_scenemax_ui_text_entity(
                commands,
                asset_server,
                context,
                ui_runtime,
                format!("UI.{ui_name}.{}", widget_path.join(".")),
                node,
                visibility,
                ZIndex(widget.z_order),
                widget.text.clone(),
                widget.font_name.as_deref(),
                widget.font_size,
                text_color,
                ui_text_justify(&widget.text_alignment),
                ui_scale,
                rect.width,
                rect.height,
                Some(base_marker),
            );
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
            let text = spawn_scenemax_ui_text_entity(
                commands,
                asset_server,
                context,
                ui_runtime,
                format!("UI.{ui_name}.{}.text", widget_path.join(".")),
                Node {
                    position_type: PositionType::Absolute,
                    left: Val::ZERO,
                    top: Val::ZERO,
                    width: Val::Percent(100.0),
                    height: Val::Percent(100.0),
                    ..default()
                },
                Visibility::Inherited,
                ZIndex(0),
                widget.button_text.clone(),
                widget.font_name.as_deref(),
                widget.font_size,
                parse_ui_color(&widget.button_text_color),
                Justify::Center,
                ui_scale,
                rect.width,
                rect.height,
                None,
            );
            commands.entity(entity).add_child(text);
            (entity, Some(text))
        }
        "IMAGE" => {
            let (image_node, sprite_sheet) =
                ui_image_node_for_widget(widget, asset_server, context, ui_runtime);
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
            if let Some(sprite_sheet) = sprite_sheet {
                commands.entity(entity).insert(sprite_sheet);
            }
            (entity, None)
        }
        "PANEL" => {
            let entity = commands
                .spawn((
                    Name::new(format!("UI.{ui_name}.{}", widget_path.join("."))),
                    node,
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
            let entity = spawn_scenemax_ui_text_entity(
                commands,
                asset_server,
                context,
                ui_runtime,
                format!("UI.{ui_name}.{}", widget_path.join(".")),
                node,
                visibility,
                ZIndex(widget.z_order),
                text,
                widget
                    .list_row_font_name
                    .as_deref()
                    .or(widget.font_name.as_deref()),
                widget.list_row_font_size,
                text_color,
                Justify::Left,
                ui_scale,
                rect.width,
                rect.height,
                Some(base_marker),
            );
            commands
                .entity(entity)
                .insert(BackgroundColor(parse_ui_color(&widget.background_color)));
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

pub(super) fn spawn_scenemax_ui_text_entity(
    commands: &mut Commands,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
    ui_runtime: &mut SceneMaxUiRuntime,
    name: String,
    node: Node,
    visibility: Visibility,
    z_index: ZIndex,
    text: String,
    font_name: Option<&str>,
    font_size: f32,
    color: Color,
    alignment: Justify,
    ui_scale: f32,
    widget_width: f32,
    widget_height: f32,
    marker: Option<SceneMaxUiWidget>,
) -> Entity {
    let transform = UiTransform::default();
    let entity = commands
        .spawn((
            Name::new(name),
            node,
            transform,
            visibility,
            z_index,
            SceneMaxUiTextVisualState {
                base_color: color,
                base_transform: transform,
            },
        ))
        .id();
    if let Some(marker) = marker {
        commands.entity(entity).insert(marker);
    }

    if let Some(font_name) =
        ensure_scenemax_bitmap_font(font_name, ui_runtime, asset_server, context)
    {
        let mut bitmap_text = SceneMaxUiBitmapText {
            text,
            font_name,
            font_size: scaled_font_size(font_size, ui_scale),
            color,
            alignment,
            widget_width,
            widget_height,
            glyph_entities: Vec::new(),
        };
        render_scenemax_bitmap_text(commands, entity, &mut bitmap_text, ui_runtime);
        commands.entity(entity).insert(bitmap_text);
    } else {
        commands.entity(entity).insert((
            Text::new(text),
            TextFont {
                font_size: FontSize::Px(scaled_font_size(font_size, ui_scale)),
                ..default()
            },
            TextColor(color),
            TextLayout::justify(alignment).with_no_wrap(),
        ));
    }

    entity
}

pub(super) fn ensure_scenemax_bitmap_font(
    font_name: Option<&str>,
    ui_runtime: &mut SceneMaxUiRuntime,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
) -> Option<String> {
    let font_name = font_name?.trim();
    if font_name.is_empty() {
        return None;
    }
    let key = font_name.to_ascii_lowercase();
    if ui_runtime.bitmap_fonts.contains_key(&key) {
        return Some(key);
    }
    let asset = ui_runtime.font_index.get(&key)?.clone();
    match load_scenemax_bitmap_font(&asset.path, asset_server, context) {
        Ok(font) => {
            ui_runtime.bitmap_fonts.insert(key.clone(), font);
            Some(key)
        }
        Err(error) => {
            tracing::warn!(font = font_name, path = asset.path, %error, "failed to load SceneMax bitmap font");
            write_runtime_diagnostic_line(format!(
                "failed to load font {font_name} from {}: {error}",
                asset.path
            ));
            None
        }
    }
}

pub(super) fn render_scenemax_bitmap_text(
    commands: &mut Commands,
    entity: Entity,
    bitmap_text: &mut SceneMaxUiBitmapText,
    ui_runtime: &SceneMaxUiRuntime,
) {
    for glyph_entity in bitmap_text.glyph_entities.drain(..) {
        commands.entity(glyph_entity).despawn();
    }

    let Some(font) = ui_runtime.bitmap_fonts.get(&bitmap_text.font_name) else {
        return;
    };
    let scale = bitmap_text.font_size / font.size.max(1.0);
    let line_height = font.line_height.max(font.size) * scale;
    let lines = bitmap_text.text.split('\n').collect::<Vec<_>>();
    let total_height = line_height * lines.len().max(1) as f32;
    let y_start = (bitmap_text.widget_height - total_height) * 0.5;

    for (line_index, line) in lines.iter().enumerate() {
        let line_width = scenemax_bitmap_line_width(font, line, scale);
        let mut cursor_x = match bitmap_text.alignment {
            Justify::Center => (bitmap_text.widget_width - line_width) * 0.5,
            Justify::Right => bitmap_text.widget_width - line_width,
            _ => 0.0,
        };
        let line_y = y_start + line_index as f32 * line_height;
        for ch in line.chars() {
            if ch == '\r' {
                continue;
            }
            let Some(glyph) = font.glyphs.get(&ch) else {
                cursor_x += line_height * 0.35;
                continue;
            };
            if glyph.width > 1.0 && glyph.height > 1.0 {
                let glyph_entity = commands
                    .spawn((
                        Node {
                            position_type: PositionType::Absolute,
                            left: Val::Px(cursor_x + glyph.x_offset * scale),
                            top: Val::Px(line_y + glyph.y_offset * scale),
                            width: Val::Px(glyph.width * scale),
                            height: Val::Px(glyph.height * scale),
                            ..default()
                        },
                        ImageNode {
                            image: font.image.clone(),
                            color: bitmap_text.color,
                            rect: Some(glyph.source),
                            ..default()
                        },
                    ))
                    .id();
                commands.entity(entity).add_child(glyph_entity);
                bitmap_text.glyph_entities.push(glyph_entity);
            }
            cursor_x += glyph.x_advance * scale;
        }
    }
}

pub(super) fn scenemax_bitmap_line_width(font: &SceneMaxBitmapFont, text: &str, scale: f32) -> f32 {
    text.chars()
        .filter(|ch| *ch != '\r')
        .map(|ch| {
            font.glyphs
                .get(&ch)
                .map(|glyph| glyph.x_advance)
                .unwrap_or(font.line_height * 0.35)
        })
        .sum::<f32>()
        * scale
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
) -> (ImageNode, Option<SceneMaxUiSpriteSheet>) {
    let candidates = ui_image_asset_candidates(widget, context, ui_runtime);
    for candidate in candidates {
        if ui_asset_path_exists(&candidate, context) {
            let sprite_sheet = ui_sprite_sheet_for_widget(widget, &candidate, context, ui_runtime);
            let mut image_node = ImageNode::new(asset_server.load(candidate));
            apply_ui_image_scale_mode(widget, &mut image_node);
            if let Some(sprite_sheet) = sprite_sheet {
                image_node.rect = Some(ui_sprite_frame_rect(
                    widget.sprite_frame,
                    sprite_sheet.cols,
                    sprite_sheet.rows,
                    sprite_sheet.image_width,
                    sprite_sheet.image_height,
                ));
                return (image_node, Some(sprite_sheet));
            }
            return (image_node, None);
        }
    }
    (
        ImageNode::solid_color(Color::srgba(0.9, 0.72, 0.38, 0.85)),
        None,
    )
}

pub(super) fn apply_ui_image_scale_mode(widget: &SceneMaxUiWidgetDef, image_node: &mut ImageNode) {
    if widget.image_scale_mode.eq_ignore_ascii_case("stretch") {
        image_node.image_mode = NodeImageMode::Stretch;
    }
}

pub(super) fn parse_ui_frame_value(value: &str) -> Option<usize> {
    let value = value.trim().parse::<f32>().ok()?;
    value.is_finite().then_some(value.round().max(0.0) as usize)
}

pub(super) fn ui_sprite_sheet_for_widget(
    widget: &SceneMaxUiWidgetDef,
    asset_path: &str,
    context: &SceneMaxLaunchContext,
    ui_runtime: &SceneMaxUiRuntime,
) -> Option<SceneMaxUiSpriteSheet> {
    let sprite_name = widget.sprite_name.as_deref()?;
    let sprite = ui_runtime.sprite_index.get(sprite_name).or_else(|| {
        ui_runtime
            .sprite_index
            .get(&sprite_name.to_ascii_lowercase())
    })?;
    let image_file = ui_asset_file_path(asset_path, context)?;
    let (image_width, image_height) = ui_png_dimensions(&image_file)?;
    Some(SceneMaxUiSpriteSheet {
        rows: sprite.rows.max(1),
        cols: sprite.cols.max(1),
        image_width,
        image_height,
    })
}

pub(super) fn ui_sprite_frame_rect(
    frame: usize,
    cols: usize,
    rows: usize,
    image_width: u32,
    image_height: u32,
) -> Rect {
    let cols = cols.max(1);
    let rows = rows.max(1);
    let frame = frame.min(cols * rows - 1);
    let col = frame % cols;
    let row = frame / cols;
    let frame_width = image_width as f32 / cols as f32;
    let frame_height = image_height as f32 / rows as f32;
    Rect {
        min: Vec2::new(col as f32 * frame_width, row as f32 * frame_height),
        max: Vec2::new(
            (col + 1) as f32 * frame_width,
            (row + 1) as f32 * frame_height,
        ),
    }
}

pub(super) fn ui_png_dimensions(path: &Path) -> Option<(u32, u32)> {
    let bytes = fs::read(path).ok()?;
    if bytes.len() < 24 || &bytes[..8] != b"\x89PNG\r\n\x1a\n" {
        return None;
    }
    Some((
        u32::from_be_bytes(bytes[16..20].try_into().ok()?),
        u32::from_be_bytes(bytes[20..24].try_into().ok()?),
    ))
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
    load_sprite_index_file(
        &asset_root.join("sprites").join("sprites-ext.json"),
        "",
        ui_runtime,
    );
    if let Some(builtin_root) = context.builtin_asset_root.as_ref() {
        load_sprite_index_file(
            &builtin_root.join("sprites").join("sprites.json"),
            "builtin://",
            ui_runtime,
        );
        load_sprite_index_file(
            &builtin_root.join("sprites").join("sprites-ext.json"),
            "builtin://",
            ui_runtime,
        );
    }
}

pub(super) fn refresh_font_index(
    ui_runtime: &mut SceneMaxUiRuntime,
    context: &SceneMaxLaunchContext,
) {
    if ui_runtime.font_index_root.as_ref() == context.asset_root.as_ref() {
        return;
    }
    ui_runtime.font_index.clear();
    ui_runtime.bitmap_fonts.clear();
    ui_runtime.font_index_root = context.asset_root.clone();

    if let Some(builtin_root) = context.builtin_asset_root.as_ref() {
        load_font_index_file(
            &builtin_root.join("fonts").join("fonts.json"),
            "builtin://",
            ui_runtime,
        );
    }
    if let Some(asset_root) = context.asset_root.as_ref() {
        load_font_index_file(&asset_root.join("fonts").join("fonts.json"), "", ui_runtime);
        load_font_index_file(
            &asset_root.join("fonts").join("fonts-ext.json"),
            "",
            ui_runtime,
        );
    }
}

pub(super) fn load_font_index_file(path: &Path, prefix: &str, ui_runtime: &mut SceneMaxUiRuntime) {
    let Ok(source) = fs::read_to_string(path) else {
        return;
    };
    let Ok(root) = serde_json::from_str::<serde_json::Value>(&source) else {
        return;
    };
    let Some(fonts) = root.get("fonts").and_then(|value| value.as_array()) else {
        return;
    };
    for font in fonts {
        let Some(name) = font.get("name").and_then(|value| value.as_str()) else {
            continue;
        };
        let Some(path) = font.get("path").and_then(|value| value.as_str()) else {
            continue;
        };
        ui_runtime.font_index.insert(
            name.to_ascii_lowercase(),
            SceneMaxBitmapFontAsset {
                path: format!("{prefix}{}", normalize_asset_path(path)),
            },
        );
    }
}

pub(super) fn load_scenemax_bitmap_font(
    asset_path: &str,
    asset_server: &AssetServer,
    context: &SceneMaxLaunchContext,
) -> Result<SceneMaxBitmapFont> {
    let font_file = ui_asset_file_path(asset_path, context)
        .ok_or_else(|| anyhow::anyhow!("font asset {asset_path} was not found"))?;
    let source = fs::read_to_string(&font_file)?;
    let mut size = 0.0;
    let mut line_height = 0.0;
    let mut page_file = None::<String>;
    let mut glyphs = HashMap::new();

    for line in source.lines() {
        if line.starts_with("info ") {
            size = parse_fnt_f32(line, "size").unwrap_or(size).abs();
        } else if line.starts_with("common ") {
            line_height = parse_fnt_f32(line, "lineHeight").unwrap_or(line_height);
        } else if line.starts_with("page ") {
            page_file = parse_fnt_string(line, "file");
        } else if line.starts_with("char ") {
            let Some(id) = parse_fnt_u32(line, "id") else {
                continue;
            };
            let Some(ch) = char::from_u32(id) else {
                continue;
            };
            let x = parse_fnt_f32(line, "x").unwrap_or(0.0);
            let y = parse_fnt_f32(line, "y").unwrap_or(0.0);
            let width = parse_fnt_f32(line, "width").unwrap_or(0.0);
            let height = parse_fnt_f32(line, "height").unwrap_or(0.0);
            glyphs.insert(
                ch,
                SceneMaxBitmapGlyph {
                    source: Rect {
                        min: Vec2::new(x, y),
                        max: Vec2::new(x + width, y + height),
                    },
                    width,
                    height,
                    x_offset: parse_fnt_f32(line, "xoffset").unwrap_or(0.0),
                    y_offset: parse_fnt_f32(line, "yoffset").unwrap_or(0.0),
                    x_advance: parse_fnt_f32(line, "xadvance").unwrap_or(width),
                },
            );
        }
    }

    if glyphs.is_empty() {
        anyhow::bail!("font {asset_path} has no glyphs");
    }
    let page_file =
        page_file.ok_or_else(|| anyhow::anyhow!("font {asset_path} has no page image"))?;
    let image_path = bitmap_font_page_asset_path(asset_path, &page_file);
    Ok(SceneMaxBitmapFont {
        image: asset_server.load(image_path),
        size: size.max(line_height).max(1.0),
        line_height: line_height.max(size).max(1.0),
        glyphs,
    })
}

pub(super) fn bitmap_font_page_asset_path(font_asset_path: &str, page_file: &str) -> String {
    let normalized_font = normalize_asset_path(font_asset_path);
    let (prefix, relative_font) = normalized_font
        .strip_prefix("builtin://")
        .map(|relative| ("builtin://", relative))
        .unwrap_or(("", normalized_font.as_str()));
    let parent = Path::new(relative_font)
        .parent()
        .map(|path| path.to_string_lossy().replace('\\', "/"))
        .unwrap_or_default();
    let page_file = page_file.replace('\\', "/");
    if parent.is_empty() {
        format!("{prefix}{page_file}")
    } else {
        format!("{prefix}{parent}/{page_file}")
    }
}

pub(super) fn ui_asset_file_path(
    asset_path: &str,
    context: &SceneMaxLaunchContext,
) -> Option<PathBuf> {
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

pub(super) fn parse_fnt_string(line: &str, key: &str) -> Option<String> {
    let prefix = format!("{key}=");
    line.split_whitespace()
        .find_map(|part| part.strip_prefix(&prefix))
        .map(|value| value.trim_matches('"').to_owned())
}

pub(super) fn parse_fnt_f32(line: &str, key: &str) -> Option<f32> {
    parse_fnt_string(line, key)?.parse().ok()
}

pub(super) fn parse_fnt_u32(line: &str, key: &str) -> Option<u32> {
    parse_fnt_string(line, key)?.parse().ok()
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
        let asset = SceneMaxSpriteAsset {
            path: format!("{prefix}{}", normalize_asset_path(path)),
            rows,
            cols,
        };
        ui_runtime
            .sprite_index
            .insert(name.to_owned(), asset.clone());
        ui_runtime
            .sprite_index
            .insert(name.to_ascii_lowercase(), asset);
    }
}
