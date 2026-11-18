//! Authored HUD button procedures use the active scene's ordinary call machinery.
#![allow(deprecated)]
use super::*;
#[derive(Component)]
pub(crate) struct ClickProcedure(pub String);
#[derive(Component)]
pub(crate) struct LabelAlignment(pub String);
pub(super) fn vertical_justify(value: &str) -> JustifyContent {
    match value.to_ascii_lowercase().as_str() {
        "top" => JustifyContent::FlexStart,
        "bottom" => JustifyContent::FlexEnd,
        _ => JustifyContent::Center,
    }
}
pub(crate) fn click(
    buttons: Query<
        (
            &Interaction,
            &ClickProcedure,
            &SceneMaxUiWidget,
            &InheritedVisibility,
        ),
        Changed<Interaction>,
    >,
    program: Res<SceneMaxStartupProgram>,
    runtime: Res<SceneMaxUiRuntime>,
    mut queue: ResMut<DelayedActionQueue>,
) {
    let Some(program) = &program.0 else {
        return;
    };
    let functions = collect_functions_by_name(program);
    for (interaction, procedure, widget, visible) in &buttons {
        if *interaction != Interaction::Pressed || !visible.get() || procedure.0.is_empty() {
            continue;
        }
        let Some(function) = functions.get(&procedure.0) else {
            continue;
        };
        if !function.params.is_empty() {
            continue;
        }
        let Some(loaded) = runtime.loaded.get(&widget.ui_name) else {
            continue;
        };
        enqueue_delayed_actions(
            Some(&mut queue),
            0.,
            vec![Statement::RunFunction {
                name: procedure.0.clone(),
                args: vec![],
            }],
            None,
            loaded.scope.clone(),
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn clicks_resolve_scene_procedures_once_and_preserve_loaded_scope() {
        let mut app = App::new();
        app.init_resource::<SceneMaxUiRuntime>()
            .init_resource::<DelayedActionQueue>()
            .insert_resource(SceneMaxStartupProgram(
                Some(
                    scenemax_parser::parse_program(
                        "launch() = {\n var result = 1\n}\nneeds_arg(x) = {\n var result = x\n}",
                    )
                    .unwrap(),
                ),
                None,
            ))
            .add_systems(Update, click);
        let mut scope = SceneMaxScopeFrame::default();
        scope
            .typed
            .insert("selection".into(), serde_json::json!("chosen"));
        app.world_mut()
            .resource_mut::<SceneMaxUiRuntime>()
            .loaded
            .insert(
                "fixture".into(),
                LoadedSceneMaxUi {
                    scope: Some(scope.clone()),
                    ..default()
                },
            );
        let marker = || SceneMaxUiWidget {
            ui_name: "fixture".into(),
            layer: "overlay".into(),
            widget_path: vec!["start".into()],
        };
        let button = app
            .world_mut()
            .spawn((
                Interaction::Pressed,
                ClickProcedure("launch".into()),
                marker(),
                InheritedVisibility::VISIBLE,
            ))
            .id();
        for name in ["missing", "", "needs_arg"] {
            app.world_mut().spawn((
                Interaction::Pressed,
                ClickProcedure(name.into()),
                marker(),
                InheritedVisibility::VISIBLE,
            ));
        }
        app.world_mut().spawn((
            Interaction::Pressed,
            ClickProcedure("launch".into()),
            marker(),
            InheritedVisibility::HIDDEN,
        ));
        app.update();
        let queue = app.world().resource::<DelayedActionQueue>();
        assert_eq!(queue.actions.len(), 1);
        assert_eq!(queue.actions[0].scope, Some(scope));
        assert!(
            matches!(&queue.actions[0].actions[0], Statement::RunFunction { name, args } if name == "launch" && args.is_empty())
        );
        app.update();
        assert_eq!(
            app.world().resource::<DelayedActionQueue>().actions.len(),
            1
        );
        app.world_mut().entity_mut(button).insert(Interaction::None);
        app.update();
        app.world_mut()
            .entity_mut(button)
            .insert(Interaction::Pressed);
        app.update();
        assert_eq!(
            app.world().resource::<DelayedActionQueue>().actions.len(),
            2
        );
        app.world_mut()
            .resource_mut::<SceneMaxUiRuntime>()
            .loaded
            .clear();
        app.world_mut()
            .entity_mut(button)
            .insert(Interaction::Pressed);
        app.update();
        assert_eq!(
            app.world().resource::<DelayedActionQueue>().actions.len(),
            2
        );
    }
    #[test]
    fn authored_button_loads_alignment_and_callback_in_runtime() {
        use bevy::ecs::system::RunSystemOnce;
        let root = tempfile::tempdir().unwrap();
        std::fs::write(root.path().join("fixture.smui"), r#"{"name":"hud","layers":[{"name":"overlay","widgets":[{"name":"go","type":"BUTTON","buttonText":"Go","textAlignment":"right","textVerticalAlignment":"bottom","onClickProcedure":" launch "}]}]}"#).unwrap();
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, bevy::asset::AssetPlugin::default()))
            .insert_resource(SceneMaxLaunchContext {
                script_root: Some(root.path().into()),
                asset_root: None,
                builtin_asset_root: None,
                window_width: 800,
                window_height: 600,
            })
            .init_resource::<SceneMaxStartupProgram>()
            .init_resource::<SceneMaxUiRuntime>()
            .init_resource::<SceneMaxUiActionQueue>();
        let mut scope = SceneMaxScopeFrame::default();
        scope.vars.insert("choice".into(), 7.);
        app.world_mut()
            .resource_mut::<SceneMaxUiActionQueue>()
            .actions
            .push(SceneMaxUiAction::Load {
                name: "fixture".into(),
                scope: Some(scope.clone()),
            });
        app.world_mut()
            .run_system_once(apply_scenemax_ui_actions)
            .unwrap();
        app.world_mut().flush();
        let loaded = &app.world().resource::<SceneMaxUiRuntime>().loaded["hud"];
        assert_eq!(loaded.scope, Some(scope));
        let target = &loaded.targets["hud.overlay.go"];
        assert_eq!(
            app.world()
                .get::<Node>(target.entity)
                .unwrap()
                .justify_content,
            JustifyContent::FlexEnd
        );
        assert_eq!(
            app.world().get::<ClickProcedure>(target.entity).unwrap().0,
            "launch"
        );
        assert!(app.world().get::<Interaction>(target.entity).is_some());
        let text = target.text_entity.unwrap();
        assert_eq!(
            app.world().get::<TextLayout>(text).unwrap().justify,
            Justify::Right
        );
        assert_eq!(app.world().get::<Node>(text).unwrap().height, Val::Auto);
    }
}

#[cfg(test)]
mod rendered_alignment_tests {
    use super::*;
    include!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../Tests/support/ui_layout.rs"
    ));
    #[test]
    fn rendered_button_alignment_uses_measured_text_box() {
        let mut app = layout_app();
        app.init_resource::<SceneMaxUiRuntime>();
        let root = app
            .world_mut()
            .spawn(Node {
                width: px(1280.),
                height: px(720.),
                ..default()
            })
            .id();
        let server = app.world().resource::<AssetServer>().clone();
        let context = SceneMaxLaunchContext {
            script_root: None,
            asset_root: None,
            builtin_asset_root: None,
            window_width: 1280,
            window_height: 720,
        };
        for h in ["left", "center", "right"] {
            for v in ["top", "center", "bottom"] {
                let mut widget:SceneMaxUiWidgetDef=serde_json::from_str(r#"{"name":"start","type":"BUTTON","buttonText":"Start","fontSize":24,"width":240,"height":56,"widthMode":"FIXED","heightMode":"FIXED"}"#).unwrap();
                widget.text_alignment = h.into();
                widget.text_vertical_alignment = v.into();
                let rects = solve_widget_layout(&[widget.clone()], 1280., 720.);
                let mut loaded = LoadedSceneMaxUi::default();
                app.world_mut()
                    .resource_scope(|world, runtime: Mut<SceneMaxUiRuntime>| {
                        spawn_scenemax_ui_widget(
                            &mut world.commands(),
                            &server,
                            &context,
                            runtime.into_inner(),
                            "fixture",
                            "main",
                            vec![],
                            &widget,
                            root,
                            UiLayoutRect {
                                x: 0.,
                                y: 0.,
                                width: 1280.,
                                height: 720.,
                            },
                            1.,
                            &rects,
                            &mut loaded,
                        );
                    });
                app.world_mut().flush();
                settle_layout(&mut app);
                let target = &loaded.targets["fixture.main.start"];
                assert_label_position(
                    app.world(),
                    target.entity,
                    target.text_entity.unwrap(),
                    h,
                    v,
                );
                app.world_mut().entity_mut(target.entity).despawn();
            }
        }
    }
}
