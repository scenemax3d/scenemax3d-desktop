//! On-demand bindings for skinned glTF scenes that contain no embedded clips.
use super::*;
use bevy::{animation::AnimatedBy, mesh::skinning::SkinnedMesh};

pub(super) fn bind_unanimated_skin(world: &mut World, root: Entity) {
    if world.get::<AnimationToPlay>(root).is_none() {
        return;
    }
    let Some(children) = world.get::<Children>(root) else {
        return;
    };
    let scene_roots: Vec<_> = children.iter().collect();
    for scene in scene_roots {
        let mut stack = vec![(scene, Vec::<String>::new())];
        let mut bindings = Vec::new();
        let mut has_skin = false;
        let mut already_bound = false;
        while let Some((entity, mut path)) = stack.pop() {
            // Attached SceneMax objects own their animation state independently.
            if world.get::<SceneMaxEntity>(entity).is_some() {
                continue;
            }
            if world.get::<AnimationPlayer>(entity).is_some()
                || world.get::<AnimatedBy>(entity).is_some()
                || world.get::<AnimationTargetId>(entity).is_some()
            {
                already_bound = true;
                break;
            }
            has_skin |= world.get::<SkinnedMesh>(entity).is_some();
            if let Some(name) = world.get::<Name>(entity) {
                path.push(name.as_str().to_owned());
                bindings.push((entity, AnimationTargetId::from_iter(path.iter())));
            }
            if let Some(children) = world.get::<Children>(entity) {
                stack.extend(children.iter().map(|child| (child, path.clone())));
            }
        }
        if already_bound || !has_skin || bindings.is_empty() {
            continue;
        }
        let count = bindings.len();
        world.entity_mut(scene).insert(AnimationPlayer::default());
        for (entity, target) in bindings {
            world.entity_mut(entity).insert((target, AnimatedBy(scene)));
        }
        tracing::debug!(
            ?root,
            ?scene,
            targets = count,
            "bound skinned scene for external animation"
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;

    fn gltf() -> Gltf {
        Gltf {
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
            animations: vec![],
            named_animations: default(),
            source: None,
        }
    }

    fn pending(model: Handle<Gltf>) -> AnimationToPlay {
        AnimationToPlay {
            rewind: None,
            frame_range: None,
            frame_range_name: None,
            controller_owner: None,
            network_lifetime: None,
            clip: "shared_motion".into(),
            runtime_clip: "shared_motion".into(),
            looped: true,
            speed: 0.5,
            gltf: model,
            animation_names: vec![],
            target_model_resource: None,
            baked_external: None,
            bake_request: None,
            external_retarget: default(),
            external_source: false,
            tried_external_source: false,
            visual_transform_preapplied: false,
            retarget_wait_logged: false,
        }
    }

    #[test]
    fn external_clip_animates_unbundled_skin_and_reuses_bindings() {
        let mut app = App::new();
        app.add_plugins((
            MinimalPlugins,
            AssetPlugin::default(),
            bevy::animation::AnimationPlugin,
        ));
        app.init_asset::<Gltf>()
            .init_asset::<GltfNode>()
            .init_asset::<Mesh>();
        app.init_resource::<SceneMaxAnimationDurations>();
        app.insert_resource(bevy::time::TimeUpdateStrategy::ManualDuration(
            Duration::from_millis(100),
        ));
        let model = app.world_mut().resource_mut::<Assets<Gltf>>().add(gltf());
        let mut clip = AnimationClip::default();
        clip.add_curve_to_target(
            AnimationTargetId::from_iter(["joint"]),
            AnimatableCurve::new(
                animated_field!(Transform::rotation),
                UnevenSampleAutoCurve::new([(0., Quat::IDENTITY), (2., Quat::from_rotation_z(1.))])
                    .unwrap(),
            ),
        );
        let clip = app
            .world_mut()
            .resource_mut::<Assets<AnimationClip>>()
            .add(clip);
        let node = app
            .world_mut()
            .resource_mut::<Assets<GltfNode>>()
            .add(GltfNode {
                index: 0,
                name: "joint".into(),
                children: vec![],
                mesh: None,
                skin: None,
                transform: default(),
                is_animation_root: true,
                extras: None,
            });
        let mut source = gltf();
        source.nodes.push(node);
        source.animations.push(clip.clone());
        source.named_animations.insert("take".into(), clip);
        let source = app.world_mut().resource_mut::<Assets<Gltf>>().add(source);
        let mut assets = SceneMaxRuntimeAssets::default();
        assets.external_animations_by_name.insert(
            normalized_animation_name("shared_motion"),
            RuntimeExternalAnimation {
                gltf: source,
                animation_names: vec!["take".into()],
                clip: "take".into(),
                asset_path: "fixture.glb".into(),
                retarget: default(),
                baked_retargets: vec![],
            },
        );
        app.insert_resource(assets);
        let root = app
            .world_mut()
            .spawn((
                Transform::default(),
                pending(model.clone()),
                SceneMaxEntity {
                    name: "subject".into(),
                    runtime_name: "subject@1".into(),
                },
            ))
            .id();
        // Unnamed scene wrapper, as emitted by the glTF loader. No player or targets.
        let scene = app
            .world_mut()
            .spawn((Transform::default(), ChildOf(root)))
            .id();
        let joint = app
            .world_mut()
            .spawn((Name::new("joint"), Transform::default(), ChildOf(scene)))
            .id();
        app.world_mut().spawn((
            ChildOf(scene),
            SkinnedMesh {
                inverse_bindposes: default(),
                joints: vec![joint],
            },
        ));
        for _ in 0..4 {
            app.world_mut()
                .run_system_once(play_pending_animations)
                .unwrap();
        }
        assert!(app.world().get::<AnimationToPlay>(root).is_none());
        let current = app.world().get::<CurrentAnimation>(root).unwrap();
        assert!(current.looped);
        assert_eq!(current.speed, 0.5);
        assert_eq!(app.world().get::<AnimatedBy>(joint).unwrap().0, scene);
        let target = *app.world().get::<AnimationTargetId>(joint).unwrap();
        for _ in 0..6 {
            app.update();
        }
        assert!(
            app.world()
                .get::<Transform>(joint)
                .unwrap()
                .rotation
                .angle_between(Quat::IDENTITY)
                > 0.05
        );
        // A subsequent request must not replace the player, target IDs, or playing state.
        app.world_mut().entity_mut(root).insert(pending(model));
        bind_unanimated_skin(app.world_mut(), root);
        assert_eq!(
            *app.world().get::<AnimationTargetId>(joint).unwrap(),
            target
        );
        assert!(
            app.world()
                .get::<AnimationPlayer>(scene)
                .unwrap()
                .playing_animations()
                .next()
                .is_some()
        );
    }

    #[test]
    fn waits_for_skin_and_leaves_existing_animation_and_attached_objects_untouched() {
        let mut world = World::new();
        let root = world.spawn(pending(default())).id();
        bind_unanimated_skin(&mut world, root);
        let scene = world.spawn(ChildOf(root)).id();
        let joint = world.spawn((Name::new("joint"), ChildOf(scene))).id();
        bind_unanimated_skin(&mut world, root);
        assert!(world.get::<AnimationPlayer>(scene).is_none());
        let attached = world
            .spawn((
                ChildOf(scene),
                SceneMaxEntity {
                    name: "attached".into(),
                    runtime_name: "attached@1".into(),
                },
            ))
            .id();
        world.spawn((
            ChildOf(attached),
            SkinnedMesh {
                inverse_bindposes: default(),
                joints: vec![joint],
            },
        ));
        bind_unanimated_skin(&mut world, root);
        assert!(world.get::<AnimationPlayer>(scene).is_none());
        world.spawn((
            ChildOf(scene),
            SkinnedMesh {
                inverse_bindposes: default(),
                joints: vec![joint],
            },
        ));
        let original = AnimationTargetId::from_iter(["original", "joint"]);
        world
            .entity_mut(joint)
            .insert((original, AnimatedBy(scene)));
        world.entity_mut(scene).insert(AnimationPlayer::default());
        bind_unanimated_skin(&mut world, root);
        assert_eq!(*world.get::<AnimationTargetId>(joint).unwrap(), original);
        assert!(world.get::<AnimationTargetId>(attached).is_none());
        assert!(world.get::<AnimationPlayer>(joint).is_none());
    }

    /// Opt-in integration check against local project assets, using the real glTF loader
    /// and Bevy animation evaluator. No proprietary model is required by normal CI.
    #[test]
    #[ignore = "requires SCENEMAX_ANIMATION_TEST_PROJECT, _MODEL and _CLIP plus a GPU"]
    fn project_external_animation_moves_skin_joints() {
        let project = PathBuf::from(std::env::var("SCENEMAX_ANIMATION_TEST_PROJECT").unwrap());
        let resource = std::env::var("SCENEMAX_ANIMATION_TEST_MODEL").unwrap();
        let clip = std::env::var("SCENEMAX_ANIMATION_TEST_CLIP").unwrap();
        let assets = project.join("resources");
        let model =
            scenemax_assets::resolve_model_resource_with_builtin_fallback(&assets, None, &resource)
                .unwrap();
        let mut app = App::new();
        app.add_plugins(
            DefaultPlugins
                .build()
                .set(AssetPlugin {
                    file_path: assets.to_string_lossy().into_owned(),
                    ..default()
                })
                .set(WindowPlugin {
                    primary_window: None,
                    ..default()
                })
                .disable::<bevy::winit::WinitPlugin>(),
        );
        app.init_resource::<SceneMaxAnimationDurations>();
        app.insert_resource(SceneMaxRuntimeAssets {
            asset_root: Some(assets),
            ..default()
        });
        app.insert_resource(bevy::time::TimeUpdateStrategy::ManualDuration(
            Duration::from_millis(16),
        ));
        app.add_systems(Update, play_pending_animations);
        app.finish();
        app.cleanup();
        let server = app.world().resource::<AssetServer>();
        let gltf = server.load(model.asset_path.clone());
        let scene = server.load(bevy::gltf::GltfAssetLabel::Scene(0).from_asset(model.asset_path));
        let mut request = pending(gltf);
        request.clip = clip.clone();
        request.runtime_clip = clip;
        request.target_model_resource = Some(resource);
        let root = app
            .world_mut()
            .spawn((
                WorldAssetRoot(scene),
                request,
                SceneMaxEntity {
                    name: "subject".into(),
                    runtime_name: "subject@1".into(),
                },
            ))
            .id();
        let deadline = std::time::Instant::now() + Duration::from_secs(45);
        while app.world().get::<CurrentAnimation>(root).is_none() {
            assert!(
                std::time::Instant::now() < deadline,
                "animation did not start"
            );
            app.update();
            std::thread::sleep(Duration::from_millis(10));
        }
        let joints: Vec<_> = app
            .world_mut()
            .query::<&SkinnedMesh>()
            .iter(app.world())
            .flat_map(|skin| skin.joints.iter().copied())
            .collect();
        assert!(!joints.is_empty());
        let before: Vec<_> = joints
            .iter()
            .map(|e| *app.world().get::<Transform>(*e).unwrap())
            .collect();
        let clips = app.world().resource::<Assets<AnimationClip>>().len();
        let graphs = app.world().resource::<Assets<AnimationGraph>>().len();
        for _ in 0..30 {
            app.update();
        }
        let moved = joints
            .iter()
            .zip(before)
            .filter(|(e, t)| {
                app.world().get::<Transform>(**e).is_some_and(|now| {
                    now.rotation.angle_between(t.rotation) > 0.001
                        || now.translation.distance(t.translation) > 0.001
                })
            })
            .count();
        println!(
            "Animated {moved}/{} skin joints; clip assets={clips}, graphs={graphs}",
            joints.len()
        );
        assert!(moved > 2, "shared clip did not move the skeleton");
        assert_eq!(app.world().resource::<Assets<AnimationClip>>().len(), clips);
        assert_eq!(
            app.world().resource::<Assets<AnimationGraph>>().len(),
            graphs
        );
        assert!(app.world().get::<AnimationToPlay>(root).is_none());
    }
}
