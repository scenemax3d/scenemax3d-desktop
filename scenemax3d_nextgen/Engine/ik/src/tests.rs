use super::*;
use serde_json::json;
fn fixture(scale: f32) -> (App, Entity, Entity, Entity, Entity, Entity) {
    let mut app = App::new();
    app.add_plugins((MinimalPlugins, TransformPlugin, IkPlugin));
    let w = app.world_mut();
    let owner = w
        .spawn(
            Transform::from_xyz(3., 2., 1.)
                .with_rotation(Quat::from_rotation_y(0.4))
                .with_scale(Vec3::splat(scale)),
        )
        .id();
    let a = w
        .spawn((Name::new("upper"), Transform::default(), ChildOf(owner)))
        .id();
    let b = w
        .spawn((
            Name::new("middle"),
            Transform::from_xyz(0., 1., 0.),
            ChildOf(a),
        ))
        .id();
    let end = w
        .spawn((
            Name::new("tip"),
            Transform::from_xyz(0., 1., 0.),
            ChildOf(b),
        ))
        .id();
    let goal = w
        .get::<Transform>(owner)
        .unwrap()
        .transform_point(Vec3::new(1., 1., 0.));
    let target = w.spawn(Transform::from_translation(goal)).id();
    let mut rig = Rig::new(
        json!({"layers":[{"id":"contact","solverType":"TwoBoneIK","chain":{"root":"upper","middle":"middle","end":"tip"},"target":"anchor","iterations":64}]}),
    );
    rig.targets.insert(
        "anchor".into(),
        Target {
            entity: target,
            joint: None,
        },
    );
    w.entity_mut(owner).insert(rig);
    (app, owner, a, b, end, target)
}
#[test]
fn positional_contacts_respect_root_transform_and_restore_without_drift() {
    for scale in [0.01, 1., 3.] {
        let (mut app, owner, a, b, end, target) = fixture(scale);
        app.update();
        assert!(
            app.world().get::<Rig>(owner).unwrap().status.is_empty(),
            "{:?}",
            app.world().get::<Rig>(owner).unwrap().status
        );
        let pos = app
            .world()
            .get::<GlobalTransform>(end)
            .unwrap()
            .translation();
        let goal = app.world().get::<Transform>(target).unwrap().translation;
        assert!(pos.distance(goal) < 0.05 * scale, "{pos:?} vs {goal:?}");
        let first = *app.world().get::<Transform>(a).unwrap();
        for _ in 0..30 {
            app.update();
        }
        assert_eq!(first, *app.world().get::<Transform>(a).unwrap());
        assert!(
            (app.world()
                .get::<Transform>(b)
                .unwrap()
                .translation
                .length()
                - 1.)
                .abs()
                < 1e-5
        );
        detach(app.world_mut(), owner);
        app.update();
        assert_eq!(
            app.world().get::<Transform>(a).unwrap().rotation,
            Quat::IDENTITY
        );
    }
}
#[test]
fn moving_bone_target_uses_current_local_hierarchy() {
    let (mut app, owner, _, _, end, target) = fixture(1.);
    let socket = app
        .world_mut()
        .spawn((Name::new("socket"), Transform::default(), ChildOf(target)))
        .id();
    app.world_mut()
        .get_mut::<Rig>(owner)
        .unwrap()
        .targets
        .get_mut("anchor")
        .unwrap()
        .joint = Some("socket".into());
    app.update();
    app.world_mut()
        .get_mut::<Transform>(socket)
        .unwrap()
        .translation = Vec3::new(-0.2, 0.15, 0.1);
    app.update();
    let w = app.world();
    assert!(
        w.get::<GlobalTransform>(end)
            .unwrap()
            .translation()
            .distance(w.get::<GlobalTransform>(socket).unwrap().translation())
            < 0.05
    );
}
#[test]
fn missing_and_degenerate_targets_never_corrupt_pose() {
    let (mut app, owner, a, _, _, target) = fixture(1.);
    app.world_mut()
        .get_mut::<Transform>(target)
        .unwrap()
        .translation = Vec3::new(3., 2., 1.);
    app.update();
    assert!(
        app.world()
            .get::<Transform>(a)
            .unwrap()
            .rotation
            .is_finite()
    );
    app.world_mut()
        .get_mut::<Rig>(owner)
        .unwrap()
        .targets
        .clear();
    app.update();
    assert!(!app.world().get::<Rig>(owner).unwrap().status.is_empty());
    assert_eq!(
        app.world().get::<Transform>(a).unwrap().rotation,
        Quat::IDENTITY
    );
}

#[test]
fn independent_layers_follow_two_contacts_and_stop_restores_only_one() {
    let (mut app, owner, a, _, end, target) = fixture(1.);
    let w = app.world_mut();
    let other = w
        .spawn((
            Name::new("other_upper"),
            Transform::from_xyz(0., 0., 0.5),
            ChildOf(owner),
        ))
        .id();
    let middle = w
        .spawn((
            Name::new("other_middle"),
            Transform::from_xyz(0., 1., 0.),
            ChildOf(other),
        ))
        .id();
    let tip = w
        .spawn((
            Name::new("other_tip"),
            Transform::from_xyz(0., 1., 0.),
            ChildOf(middle),
        ))
        .id();
    let anchor = w
        .spawn(Transform::from_translation(
            w.get::<Transform>(owner)
                .unwrap()
                .transform_point(Vec3::new(-1., 1., 0.5)),
        ))
        .id();
    let mut rig = w.get_mut::<Rig>(owner).unwrap();
    let mut layer = rig.definition["layers"][0].clone();
    layer["id"] = json!("second");
    layer["target"] = json!("other_anchor");
    layer["chain"] = json!({"root":"other_upper","middle":"other_middle","end":"other_tip"});
    rig.definition["layers"].as_array_mut().unwrap().push(layer);
    rig.targets.insert(
        "other_anchor".into(),
        Target {
            entity: anchor,
            joint: None,
        },
    );
    app.update();
    let w = app.world();
    for (tip, anchor) in [(end, target), (tip, anchor)] {
        assert!(
            w.get::<GlobalTransform>(tip)
                .unwrap()
                .translation()
                .distance(w.get::<GlobalTransform>(anchor).unwrap().translation())
                < 0.02
        );
    }
    app.world_mut().get_mut::<Rig>(owner).unwrap().definition["layers"][0]["enabled"] =
        json!(false);
    app.update();
    assert_eq!(
        app.world().get::<Transform>(a).unwrap().rotation,
        Quat::IDENTITY
    );
    assert_ne!(
        app.world().get::<Transform>(middle).unwrap().rotation,
        Quat::IDENTITY
    );
}
#[test]
fn pole_and_target_orientation_are_applied() {
    let (mut app, owner, _, _, end, target) = fixture(1.);
    let w = app.world_mut();
    let pole = w.spawn(Transform::from_xyz(3., 2., 4.)).id();
    w.get_mut::<Transform>(target).unwrap().rotation = Quat::from_rotation_x(0.7);
    let mut rig = w.get_mut::<Rig>(owner).unwrap();
    rig.definition["layers"][0]["poleTarget"] = json!("pole");
    rig.definition["layers"][0]["matchTargetRotation"] = json!(true);
    rig.targets.insert(
        "pole".into(),
        Target {
            entity: pole,
            joint: None,
        },
    );
    app.update();
    let w = app.world();
    assert!(w.get::<Rig>(owner).unwrap().status.is_empty());
    assert!(
        w.get::<GlobalTransform>(end)
            .unwrap()
            .rotation()
            .angle_between(w.get::<Transform>(target).unwrap().rotation)
            < 0.001
    );
}

#[test]
fn only_live_skin_joints_are_resolved() {
    let (mut app, owner, a, b, end, _) = fixture(1.);
    let w = app.world_mut();
    w.spawn((Name::new("upper"), Transform::default(), ChildOf(owner)));
    w.spawn((
        SkinnedMesh {
            inverse_bindposes: default(),
            joints: vec![a, b, end],
        },
        Transform::default(),
        ChildOf(owner),
    ));
    app.update();
    assert!(app.world().get::<Rig>(owner).unwrap().status.is_empty());
}
#[test]
fn blending_releases_a_contact_gradually() {
    let (mut app, owner, _, a, _, _) = fixture(1.);
    app.insert_resource(bevy::time::TimeUpdateStrategy::ManualDuration(
        std::time::Duration::from_millis(100),
    ));
    app.update();
    let full = app.world().get::<Transform>(a).unwrap().rotation;
    {
        let mut r = app.world_mut().get_mut::<Rig>(owner).unwrap();
        r.definition["layers"][0]["enabled"] = json!(false);
        r.definition["layers"][0]["blendTime"] = json!(1.);
    }
    app.update();
    let partial = app.world().get::<Transform>(a).unwrap().rotation;
    assert!(partial.angle_between(Quat::IDENTITY) < full.angle_between(Quat::IDENTITY));
    assert!(partial.angle_between(Quat::IDENTITY) > 0.01);
    for _ in 0..12 {
        app.update();
    }
    assert_eq!(
        app.world().get::<Transform>(a).unwrap().rotation,
        Quat::IDENTITY
    );
}

#[test]
fn authored_influence_does_not_shorten_activation_or_release_blends() {
    for authored in [0.25, 0.5, 1.] {
        let (mut app, owner, _, _, _, _) = fixture(1.);
        app.insert_resource(bevy::time::TimeUpdateStrategy::ManualDuration(
            std::time::Duration::from_millis(100),
        ));
        {
            let mut rig = app.world_mut().get_mut::<Rig>(owner).unwrap();
            rig.definition["layers"][0]["weight"] = json!(authored);
            rig.definition["layers"][0]["blendTime"] = json!(1.);
        }
        app.update();
        for _ in 0..5 {
            app.update();
        }
        assert!(
            (app.world()
                .get::<Rig>(owner)
                .unwrap()
                .runtime_weight("contact")
                - 0.5)
                .abs()
                < 0.001
        );
        app.world_mut().get_mut::<Rig>(owner).unwrap().definition["layers"][0]["enabled"] =
            json!(false);
        app.update();
        assert!(
            (app.world()
                .get::<Rig>(owner)
                .unwrap()
                .runtime_weight("contact")
                - 0.4)
                .abs()
                < 0.001
        );
        for _ in 0..5 {
            app.update();
        }
        assert_eq!(
            app.world()
                .get::<Rig>(owner)
                .unwrap()
                .runtime_weight("contact"),
            0.
        );
    }
}

#[test]
fn straight_limb_can_bend_toward_an_axial_contact() {
    let (mut app, owner, _, _, end, target) = fixture(1.);
    let goal = app
        .world()
        .get::<Transform>(owner)
        .unwrap()
        .transform_point(Vec3::Y);
    app.world_mut()
        .get_mut::<Transform>(target)
        .unwrap()
        .translation = goal;
    app.update();
    let w = app.world();
    assert!(
        w.get::<Rig>(owner).unwrap().status.is_empty(),
        "{:?}",
        w.get::<Rig>(owner).unwrap().status
    );
    assert!(
        w.get::<GlobalTransform>(end)
            .unwrap()
            .translation()
            .distance(goal)
            < 0.02
    );
}

#[test]
fn legacy_solver_kinds_solve_their_declared_chains() {
    for kind in ["ThreeBoneIK", "FABRIK", "FootIK", "LookAtIK", "AimIK"] {
        let (mut app, owner, a, _, end, target) = fixture(1.);
        if kind == "ThreeBoneIK" {
            let w = app.world_mut();
            let base = w
                .spawn((Name::new("base"), Transform::default(), ChildOf(owner)))
                .id();
            w.entity_mut(a).insert(ChildOf(base));
            w.get_mut::<Transform>(a).unwrap().translation = Vec3::Y;
            let mut r = w.get_mut::<Rig>(owner).unwrap();
            r.definition["layers"][0]["chain"] =
                json!({"root":"base","middle":"upper","secondMiddle":"middle","end":"tip"});
        }
        {
            let mut r = app.world_mut().get_mut::<Rig>(owner).unwrap();
            let l = &mut r.definition["layers"][0];
            l["solverType"] = json!(kind);
            l["chain"]["start"] = json!("upper");
            l["affectedJoints"] = json!(["upper"]);
            l["maxAngle"] = json!(180.);
        }
        app.update();
        let w = app.world();
        assert!(
            w.get::<Rig>(owner).unwrap().status.is_empty(),
            "{kind}: {:?}",
            w.get::<Rig>(owner).unwrap().status
        );
        if matches!(kind, "LookAtIK" | "AimIK") {
            let g = w.get::<GlobalTransform>(a).unwrap();
            let direction = (w.get::<GlobalTransform>(target).unwrap().translation()
                - g.translation())
            .normalize();
            assert!((g.rotation() * Vec3::Z).dot(direction) > 0.999, "{kind}");
        } else {
            assert!(
                w.get::<GlobalTransform>(end)
                    .unwrap()
                    .translation()
                    .distance(w.get::<GlobalTransform>(target).unwrap().translation())
                    < 0.02,
                "{kind}"
            );
        }
    }
}

#[test]
fn one_shot_preview_solves_fully_even_when_asset_blends() {
    let (mut app, owner, _, _, end, target) = fixture(1.);
    {
        let mut r = app.world_mut().get_mut::<Rig>(owner).unwrap();
        r.hold = true;
        r.definition["layers"][0]["blendTime"] = json!(1.);
    }
    app.update();
    let w = app.world();
    assert!(!w.get::<Rig>(owner).unwrap().active);
    assert!(
        w.get::<GlobalTransform>(end)
            .unwrap()
            .translation()
            .distance(w.get::<GlobalTransform>(target).unwrap().translation())
            < 0.02
    );
    let pose = *w.get::<Transform>(end).unwrap();
    app.update();
    assert_eq!(*app.world().get::<Transform>(end).unwrap(), pose);
}

#[test]
fn scene_local_bend_position_controls_knee_and_follows_scaled_rotated_owner() {
    let (mut app, owner, _, middle, end, target) = fixture(3.);
    let mut positions = vec![];
    for side in [-1., 1.] {
        app.world_mut().get_mut::<Rig>(owner).unwrap().definition["layers"][0]["polePosition"] =
            json!([0., 1., side]);
        app.update();
        let world = app.world();
        let rig = world.get::<Rig>(owner).unwrap();
        assert!(rig.status.is_empty(), "{:?}", rig.status);
        assert!(
            world
                .get::<GlobalTransform>(end)
                .unwrap()
                .translation()
                .distance(world.get::<GlobalTransform>(target).unwrap().translation())
                < 0.05
        );
        let model = world.get::<GlobalTransform>(owner).unwrap();
        positions.push(
            model
                .affine()
                .inverse()
                .transform_point3(world.get::<GlobalTransform>(middle).unwrap().translation()),
        );
    }
    assert!(
        positions[0].z < -0.2 && positions[1].z > 0.2,
        "{positions:?}"
    );
    let scratch = app.world().get::<Rig>(owner).unwrap().pole_handle.unwrap();
    for _ in 0..30 {
        app.update();
    }
    assert_eq!(
        app.world().get::<Rig>(owner).unwrap().pole_handle,
        Some(scratch)
    );
    detach(app.world_mut(), owner);
    assert!(app.world().get_entity(scratch).is_err());
}
