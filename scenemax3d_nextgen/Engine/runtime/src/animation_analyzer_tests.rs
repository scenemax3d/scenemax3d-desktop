use super::*;
use bevy::ecs::system::RunSystemOnce;

#[test]
fn authored_alias_record_uses_source_index_interval_speed_and_repeat() {
    for mode in ["record", "numeric", "named", "missing", "numeric_source"] {
    let ranged = mode == "numeric";
    let named = mode == "named" || mode == "missing";
    for (looped, expired) in [(false, false), (true, false), (false, true)] {
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, AssetPlugin::default()));
        app.init_asset::<Gltf>()
            .init_asset::<GltfNode>()
            .init_asset::<AnimationClip>()
            .init_asset::<AnimationGraph>()
            .init_asset::<Mesh>();
        app.init_resource::<SceneMaxAnimationDurations>();
        let target = AnimationTargetId::from_iter(["fixture_joint"]);
        let mut source = AnimationClip::default();
        source.add_curve_to_target(
            target,
            AnimatableCurve::new(
                animated_field!(Transform::translation),
                UnevenSampleAutoCurve::new([(0., Vec3::ZERO), (2., Vec3::X * 4.)]).unwrap(),
            ),
        );
        let clip = app
            .world_mut()
            .resource_mut::<Assets<AnimationClip>>()
            .add(source);
        let gltf = app.world_mut().resource_mut::<Assets<Gltf>>().add(Gltf {
            scenes: vec![],
            named_scenes: default(),
            meshes: vec![],
            named_meshes: default(),
            materials: vec![],
            named_materials: default(),
            nodes: vec![],
            named_nodes: default(),
            skins: vec![],
            named_skins: default(),
            default_scene: None,
            animations: vec![clip.clone()],
            named_animations: [("Source take".into(), clip.clone())].into_iter().collect(),
            source: None,
        });
        let mut assets = SceneMaxRuntimeAssets {
            asset_root: Some(PathBuf::from(".")),
            ..default()
        };
        assets.analyzer_records.insert(
            "fixture_alias".into(),
            Ok(vec![(
                scenemax_assets::animation_ranges::Record {
                    name: "segment".into(),
                    source: "Animation 1".into(),
                    start: 1,
                    end: 4,
                },
                scenemax_assets::animation_ranges::Timeline {
                    name: "Animation 1".into(),
                    index: 0,
                    times: vec![0., 0.1, 0.35, 0.8, 1.2, 1.6, 2.],
                },
            )]),
        );
        assets.frame_timelines.insert(gltf.id(), Ok(vec![scenemax_assets::animation_ranges::Timeline {
            name: "Animation 1".into(), index: 0, times: vec![0., 0.1, 0.35, 0.8, 1.2, 1.6, 2.],
        }]));
        let mut frame_range = ranged.then_some(scenemax_parser::animation_range::AnimationFrameRange {
            start: scenemax_parser::animation_range::FrameEndpoint { value: 0., percent: true },
            end: scenemax_parser::animation_range::FrameEndpoint { value: 50., percent: true },
        });
        assets.named_frame_bounds.insert(("fixture_alias".into(), "section-a".into()), Ok((1., 4.)));
        assets.named_frame_bounds.insert(("fixture_alias".into(), "missing".into()), Err("Range missing".into()));
        let frame_range_name = named.then(|| if mode == "missing" { "missing".to_owned() } else { "SECTION-A".to_owned() });
        if mode == "numeric_source" {
            assets.analyzer_records.insert("fixture_alias".into(), Err("Fractional named metadata is not an integer record".into()));
            frame_range = Some(scenemax_parser::animation_range::AnimationFrameRange {
                start: scenemax_parser::animation_range::FrameEndpoint { value: 1., percent: false },
                end: scenemax_parser::animation_range::FrameEndpoint { value: 4., percent: false },
            });
        }
        let requested_clip = if named || mode == "numeric_source" { "Source take" } else { "segment" };
        app.insert_resource(assets);
        let lease = multiplayer::ActionLease::new(());
        let lifetime = std::sync::Arc::downgrade(&lease);
        let owner = SceneMaxControllerKey::NetworkAction { entity: 101, slot: 3, sequence: 1 };
        let root = app
            .world_mut()
            .spawn((
                SceneMaxEntity {
                    name: "subject".into(),
                    runtime_name: "subject@1".into(),
                },
                AnimationRecordModel("fixture_alias".into()),
                Transform::default(),
                AnimationToPlay { rewind: None,
                    frame_range,
                    frame_range_name: frame_range_name.clone(),
                    controller_owner: Some(owner.clone()),
                    network_lifetime: Some(lease),
                    clip: requested_clip.into(),
                    runtime_clip: requested_clip.into(),
                    looped,
                    speed: 0.5,
                    gltf,
                    animation_names: vec![String::new()],
                    target_model_resource: Some("fixture_source".into()),
                    baked_external: None,
                    bake_request: None,
                    external_retarget: default(),
                    external_source: false,
                    tried_external_source: false,
                    visual_transform_preapplied: false,
                    retarget_wait_logged: false,
                },
            ))
            .id();
        let player = app
            .world_mut()
            .spawn((
                AnimationPlayer::default(),
                Transform::default(),
                ChildOf(root),
            ))
            .id();
        {
            app.world_mut().entity_mut(root).insert(NetworkAnimationResume {
                owner: owner.clone(), remaining_seconds: if looped { f64::from(u32::MAX - 1234) / 1000. } else if expired { 0. } else { 0.8 },
                received_at: web_time::Instant::now(),
            });
        }
        if expired {
            // Deadline cleanup must not depend on the model ever loading.
            app.world_mut().get_mut::<AnimationToPlay>(root).unwrap().gltf = Handle::default();
        }
        app.world_mut()
            .run_system_once(play_pending_animations)
            .unwrap();
        if expired || mode == "missing" {
            assert!(app.world().get::<AnimationToPlay>(root).is_none());
            assert!(app.world().get::<CurrentAnimation>(root).is_none());
            assert!(app.world().get::<AnimationPlayer>(player).unwrap().playing_animations().next().is_none());
            assert_eq!(lifetime.strong_count(), 0);
            continue;
        }
        let current = app.world().get::<CurrentAnimation>(root).unwrap();
        assert_eq!(current.clip, format!("{requested_clip}{}", scenemax_parser::animation_range::selector_key(frame_range, frame_range_name.as_deref())));
        assert!((current.duration_seconds - if ranged { 0.475 } else { 1.1 }).abs() < 0.00001);
        assert_eq!(current.speed, 0.5);
        assert_eq!(current.looped, looped);
        assert_eq!(current.controller_owner, Some(owner));
        assert_eq!(current.playback_graphs.len(), 1);
        assert_eq!(current.playback_graphs[0].0, player);
        assert_eq!(lifetime.strong_count(), 1);
        assert!(app.world().get::<AnimationToPlay>(root).is_none());
        let animation_player = app.world().get::<AnimationPlayer>(player).unwrap();
        let (node, active) = animation_player.playing_animations().next().unwrap();
        if !looped {
            assert!(active.seek_time() >= if ranged { 0.075 - 0.0001 } else { 0.7 } && active.seek_time() < current.duration_seconds);
            assert_eq!(active.seek_time(), current.elapsed_seconds);
        }
        if looped {
            let expected = (1.234_f32 * 0.5).rem_euclid(current.duration_seconds);
            assert!((active.seek_time() - expected).abs() < 0.02, "loop seek={} expected={expected}", active.seek_time());
            assert_eq!(active.seek_time(), current.elapsed_seconds);
        }
        assert_eq!(active.speed(), 0.5);
        assert_eq!(
            active.repeat_mode(),
            if looped {
                RepeatAnimation::Forever
            } else {
                RepeatAnimation::Never
            }
        );
        let graph = app
            .world()
            .resource::<Assets<AnimationGraph>>()
            .get(&app.world().get::<AnimationGraphHandle>(player).unwrap().0)
            .unwrap();
        let AnimationNodeType::Clip(handle) = &graph.graph.node_weight(*node).unwrap().node_type
        else {
            panic!("Expected interval clip")
        };
        let clips = app.world().resource::<Assets<AnimationClip>>();
        let interval = clips.get(handle).unwrap();
        let curve = &interval.curves()[&target][0];
        let first = *curve.0.sample_clamped(0.).downcast::<Vec3>().unwrap();
        let last = *curve
            .0
            .sample_clamped(interval.duration())
            .downcast::<Vec3>()
            .unwrap();
        assert!((first.x - 0.2).abs() < 0.00001);
        assert!((last.x - if ranged { 1.15 } else { 2.4 }).abs() < 0.00001);
        assert_eq!(clips.get(&clip).unwrap().duration(), 2.);
        app.init_resource::<SceneMaxVars>();
        app.world_mut().resource_mut::<Time>().advance_by(Duration::from_secs(3));
        app.world_mut().run_system_once(update_current_animation_vars).unwrap();
        assert_eq!(lifetime.strong_count(), usize::from(looped));
        app.world_mut().entity_mut(root).remove::<CurrentAnimation>();
        assert_eq!(lifetime.strong_count(), 0);
    }
}

}
