//! Script-to-Bevy IK binding. Scene names remain project data.
use super::*;
use scenemax_ik::{Rig, Target};
use scenemax_parser::ik::{IkAction, IkStatement};
use serde_json::{Value, json};
fn read(root: &Path, id: &str) -> Result<Value> {
    anyhow::ensure!(
        !id.is_empty() && !id.contains(['/', '\\']) && id != "..",
        "Invalid IK asset ID"
    );
    let mut matches = vec![];
    for folder in ["ik", "IK"] {
        let folder = root.join(folder);
        if !scenemax_assets::filesystem::is_dir(&folder) {
            continue;
        }
        for entry in fs::read_dir(&folder)? {
            let path = entry?.path();
            let file = path
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .to_ascii_lowercase();
            if !file.ends_with(".smik") && !file.ends_with(".ik.json") {
                continue;
            }
            if fs::metadata(&path)?.len() > 8 * 1024 * 1024 {
                continue;
            }
            let mut value: Value = serde_json::from_str(&fs::read_to_string(&path)?)?;
            if value.get("layers").is_none() && value.get("solverType").is_some() {
                value["layers"] = json!([value.clone()]);
            }
            for layer in value["layers"].as_array_mut().into_iter().flatten() {
                if layer.get("chain").is_none() {
                    layer["chain"] = json!({"root":layer["rootJoint"],"middle":layer["middleJoint"],"secondMiddle":layer.get("secondMiddleJoint").or(layer.get("middle2Joint")).cloned().unwrap_or(Value::Null),"end":layer["endJoint"],"start":layer["startJoint"]});
                }
            }

            if value["id"]
                .as_str()
                .is_some_and(|s| s.eq_ignore_ascii_case(id))
                || file == format!("{}.smik", id.to_ascii_lowercase())
                || file == format!("{}.ik.json", id.to_ascii_lowercase())
            {
                let path = scenemax_assets::filesystem::canonicalize(&path)?;
                if !matches.iter().any(|(p, _)| *p == path) {
                    matches.push((path, value));
                }
            }
        }
    }
    anyhow::ensure!(
        matches.len() == 1,
        "Expected one IK asset '{id}', found {}",
        matches.len()
    );
    Ok(matches.remove(0).1)
}
pub(super) fn enqueue(
    commands: &mut Commands,
    cmd: IkStatement,
    owner: Option<Entity>,
    root: Option<PathBuf>,
    asset: Option<String>,
    weight: Option<f32>,
    blend: Option<f32>,
) {
    commands.queue(move |world: &mut World| {
        let result = (|| -> Result<()> {
            let owner =
                owner.ok_or_else(|| anyhow::anyhow!("IK owner not found: {}", cmd.owner))?;
            anyhow::ensure!(
                world.get_entity(owner).is_ok(),
                "IK owner no longer exists: {}",
                cmd.owner
            );
            anyhow::ensure!(
                cmd.weight.is_none() || weight.is_some_and(f32::is_finite),
                "IK weight must resolve to a finite number"
            );
            anyhow::ensure!(
                cmd.blend.is_none() || blend.is_some_and(f32::is_finite),
                "IK blend must resolve to a finite number"
            );
            match cmd.action {
                IkAction::Apply(_) => {
                    let root =
                        root.ok_or_else(|| anyhow::anyhow!("Project resources unavailable"))?;
                    let mut value = read(&root, asset.as_deref().unwrap_or(""))?;
                    for layer in value["layers"].as_array_mut().into_iter().flatten() {
                        layer["enabled"] = json!(false);
                        layer["runtimeWeight"] = json!(1.);
                    }

                    scenemax_ik::detach(world, owner);
                    world.entity_mut(owner).insert(Rig::new(value));
                }
                IkAction::Remove => scenemax_ik::detach(world, owner),
                _ => {
                    let mut rig = world.get_mut::<Rig>(owner).ok_or_else(|| {
                        anyhow::anyhow!("Apply an IK asset before controlling its layers")
                    })?;
                    let layer_id = cmd.layer.as_deref().unwrap_or("");
                    let was_active = rig.runtime_weight(layer_id) > 0.;
                    let layer = rig.definition["layers"]
                        .as_array_mut()
                        .and_then(|layers| {
                            layers.iter_mut().find(|v| {
                                ["id", "name"].iter().any(|key| {
                                    v[key]
                                        .as_str()
                                        .is_some_and(|s| s.eq_ignore_ascii_case(layer_id))
                                })
                            })
                        })
                        .ok_or_else(|| anyhow::anyhow!("IK layer not found: {layer_id}"))?;
                    if let Some(target) = cmd.target {
                        layer["target"] = json!(target);
                    }
                    if let Some(value) = weight.filter(|v| v.is_finite()) {
                        layer["defaultPlayWeight"] = json!(value.clamp(0., 1.));
                        if !matches!(cmd.action, IkAction::Weight) || was_active {
                            layer["runtimeWeight"] = json!(value.clamp(0., 1.));
                        }
                        if matches!(cmd.action, IkAction::Weight) && was_active {
                            layer["enabled"] = json!(true);
                        }
                    }
                    if let Some(value) = blend.filter(|v| v.is_finite()) {
                        layer["blendTime"] = json!(value.max(0.));
                    }
                    match cmd.action {
                        IkAction::PositionOffset(v) => layer["positionOffset"] = json!(v),
                        IkAction::RotationOffset(v) => {
                            layer["rotationOffset"] = json!(v);
                            layer["matchTargetRotation"] = json!(true);
                        }
                        IkAction::PolePosition(v) => layer["polePosition"] = json!(v),
                        IkAction::Play => {
                            layer["runtimeWeight"] =
                                layer.get("defaultPlayWeight").cloned().unwrap_or(json!(1.));
                            layer["enabled"] = json!(true);
                        }
                        IkAction::Stop => layer["enabled"] = json!(false),
                        _ => {}
                    }
                }
            }
            Ok(())
        })();
        if let Err(e) = result {
            write_runtime_diagnostic_line(format!("IK: {e}"));
        }
    });
}
pub(super) fn split_target(text: &str) -> (&str, Option<String>) {
    if let Some((object, bone)) = text.split_once(".\"") {
        return (
            object,
            Some(
                serde_json::from_str::<String>(&format!("\"{bone}"))
                    .unwrap_or_else(|_| bone.to_owned()),
            ),
        );
    }
    if let Some((object, bone)) = text.split_once('.') {
        return (object, Some(bone.into()));
    }
    (text, None)
}
pub(super) fn sync_targets(
    mut rigs: Query<&mut Rig, With<SceneMaxEntity>>,
    entities: Query<(Entity, &SceneMaxEntity)>,
) {
    for mut rig in &mut rigs {
        let keys = rig.definition["layers"]
            .as_array()
            .into_iter()
            .flatten()
            .flat_map(|l| {
                [
                    l["target"].as_str().unwrap_or("").to_string(),
                    l["poleTarget"].as_str().unwrap_or("").to_string(),
                ]
            })
            .collect::<Vec<_>>();
        rig.targets.clear();
        for key in keys {
            let (object, joint) = split_target(&key);
            let mut candidates = entities
                .iter()
                .filter(|(_, n)| n.name == object || n.runtime_name == object)
                .map(|(e, _)| e);
            if let Some(entity) = candidates.next()
                && candidates.next().is_none()
            {
                rig.targets.insert(key, Target { entity, joint });
            }
        }
    }
}

pub(super) fn reference(object: &str, joint: Option<&str>) -> String {
    match joint {
        Some(joint) => format!(
            "{object}.{}",
            serde_json::to_string(joint).unwrap_or_default()
        ),
        None => object.into(),
    }
}

pub(super) fn diagnostics(
    rigs: Query<(Entity, &Rig)>,
    mut last: Local<HashMap<Entity, Vec<String>>>,
) {
    last.retain(|e, _| rigs.contains(*e));
    for (entity, rig) in &rigs {
        if last.get(&entity) != Some(&rig.status) {
            for error in &rig.status {
                write_runtime_diagnostic_line(format!("IK: {error}"));
            }
            last.insert(entity, rig.status.clone());
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn stopped_weight_is_a_default_until_play_and_zero_play_stays_stopped() {
        let mut world = World::new();
        let owner = world
            .spawn(Rig::new(
                json!({"layers":[{"id":"contact","enabled":false,"runtimeWeight":1.}]}),
            ))
            .id();
        for (action, weight, expected) in [
            (IkAction::Weight, Some(0.4), 0.),
            (IkAction::Play, None, 0.4),
            (IkAction::Play, Some(0.), 0.),
            (IkAction::Weight, Some(0.7), 0.),
            (IkAction::Play, None, 0.7),
            (IkAction::Stop, None, 0.),
            (IkAction::Weight, Some(0.2), 0.),
            (IkAction::Play, None, 0.2),
        ] {
            let cmd = IkStatement {
                owner: "fixture".into(),
                layer: Some("CONTACT".into()),
                action,
                target: None,
                weight: None,
                blend: None,
            };
            enqueue(
                &mut world.commands(),
                cmd,
                Some(owner),
                None,
                None,
                weight,
                None,
            );
            world.flush();
            assert!(
                (world.get::<Rig>(owner).unwrap().runtime_weight("contact") - expected).abs()
                    < 0.0001
            );
        }
    }
    #[test]
    fn weight_assignment_during_release_resumes_from_current_activation() {
        let mut app = App::new();
        app.add_plugins((MinimalPlugins, TransformPlugin, scenemax_ik::IkPlugin));
        app.insert_resource(bevy::time::TimeUpdateStrategy::ManualDuration(
            std::time::Duration::from_millis(100),
        ));
        let owner = app
            .world_mut()
            .spawn((
                Transform::default(),
                Rig::new(json!({
                    "layers":[{"id":"contact","enabled":true,"runtimeWeight":1.}]
                })),
            ))
            .id();
        app.update();
        let command = |action| IkStatement {
            owner: "fixture".into(),
            layer: Some("contact".into()),
            action,
            target: None,
            weight: None,
            blend: None,
        };
        enqueue(
            &mut app.world_mut().commands(),
            command(IkAction::Stop),
            Some(owner),
            None,
            None,
            None,
            Some(1.),
        );
        app.world_mut().flush();
        app.update();
        assert!(
            (app.world()
                .get::<Rig>(owner)
                .unwrap()
                .runtime_weight("contact")
                - 0.9)
                .abs()
                < 0.001
        );
        enqueue(
            &mut app.world_mut().commands(),
            command(IkAction::Weight),
            Some(owner),
            None,
            None,
            Some(1.),
            None,
        );
        app.world_mut().flush();
        app.update();
        assert!(
            (app.world()
                .get::<Rig>(owner)
                .unwrap()
                .runtime_weight("contact")
                - 1.)
                .abs()
                < 0.001
        );
    }
    #[test]
    fn quoted_target_bones_keep_dots_and_names() {
        assert_eq!(
            split_target(r#"vehicle."socket.01""#),
            ("vehicle", Some("socket.01".into()))
        );
        assert_eq!(
            reference("vehicle", Some("socket.01")),
            r#"vehicle."socket.01""#
        );
    }
    #[test]
    fn layer_commands_preserve_authored_weight_and_support_stop_and_remove() {
        let mut w = World::new();
        let mut rig = Rig::new(json!({"layers":[{"id":"contact","weight":0.5,"enabled":false}]}));
        rig.active = true;
        let owner = w.spawn(rig).id();
        let cmd = IkStatement {
            owner: "fixture".into(),
            layer: Some("contact".into()),
            action: IkAction::Play,
            target: Some(r#"vehicle."socket""#.into()),
            weight: Some(scenemax_parser::AssignmentValue::Symbol("amount".into())),
            blend: None,
        };
        enqueue(
            &mut w.commands(),
            cmd.clone(),
            Some(owner),
            None,
            None,
            Some(0.75),
            None,
        );
        w.flush();
        let l = &w.get::<Rig>(owner).unwrap().definition["layers"][0];
        assert_eq!(l["weight"], 0.5);
        assert_eq!(l["runtimeWeight"], 0.75);
        assert_eq!(l["enabled"], true);
        let mut stop = cmd.clone();
        stop.action = IkAction::Stop;
        stop.weight = None;
        enqueue(
            &mut w.commands(),
            stop,
            Some(owner),
            None,
            None,
            None,
            Some(0.2),
        );
        w.flush();
        assert_eq!(
            w.get::<Rig>(owner).unwrap().definition["layers"][0]["enabled"],
            false
        );
        let mut remove = cmd;
        remove.action = IkAction::Remove;
        remove.weight = None;
        enqueue(
            &mut w.commands(),
            remove,
            Some(owner),
            None,
            None,
            None,
            None,
        );
        w.flush();
        assert!(w.get::<Rig>(owner).is_none());
    }
    #[test]
    fn asset_id_lookup_and_bone_binding_work_after_apply() {
        use bevy::ecs::system::RunSystemOnce;
        let root = std::env::temp_dir().join(format!(
            "scenemax-ik-test-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        fs::create_dir_all(root.join("ik")).unwrap();
        let file = root.join("ik/custom.smik");
        fs::write(&file,json!({"id":"ik_fixture","layers":[{"id":"contact","target":"anchor.\"socket\"","enabled":true}]}).to_string()).unwrap();
        let mut w = World::new();
        let owner = w
            .spawn(SceneMaxEntity {
                name: "actor".into(),
                runtime_name: "actor".into(),
            })
            .id();
        let target = w
            .spawn(SceneMaxEntity {
                name: "anchor".into(),
                runtime_name: "anchor".into(),
            })
            .id();
        let cmd = IkStatement {
            owner: "actor".into(),
            layer: None,
            action: IkAction::Apply(scenemax_parser::AssignmentValue::Symbol(
                "ik_fixture".into(),
            )),
            target: None,
            weight: None,
            blend: None,
        };
        enqueue(
            &mut w.commands(),
            cmd,
            Some(owner),
            Some(root.clone()),
            Some("ik_fixture".into()),
            None,
            None,
        );
        w.flush();
        assert_eq!(
            w.get::<Rig>(owner).unwrap().definition["layers"][0]["enabled"],
            false
        );
        w.run_system_once(sync_targets).unwrap();
        let reference = &w.get::<Rig>(owner).unwrap().targets["anchor.\"socket\""];
        assert_eq!(reference.entity, target);
        assert_eq!(reference.joint.as_deref(), Some("socket"));
        fs::remove_file(file).unwrap();
        fs::remove_dir(root.join("ik")).unwrap();
        fs::remove_dir(root).unwrap();
    }
}

#[cfg(test)]
mod pose_tests {
    use super::*;
    #[test]
    fn queued_asset_application_ignores_despawned_owner() {
        let root = tempfile::tempdir().unwrap();
        fs::create_dir(root.path().join("ik")).unwrap();
        fs::write(root.path().join("ik/fixture.smik"), r#"{"layers":[]}"#).unwrap();
        let mut world = World::new();
        let owner = world.spawn_empty().id();
        let Statement::Ik(command) = scenemax_parser::parse_program(r#"subject.ik = "fixture""#)
            .unwrap()
            .statements
            .remove(0)
        else {
            panic!()
        };
        enqueue(
            &mut world.commands(),
            command,
            Some(owner),
            Some(root.path().into()),
            Some("fixture".into()),
            None,
            None,
        );
        world.despawn(owner);
        world.flush();
        assert_eq!(world.query::<&Rig>().iter(&world).count(), 0);
    }

    #[test]
    fn generated_pose_assignments_reach_runtime_definition_before_play() {
        let mut world = World::new();
        let owner = world
            .spawn(Rig::new(
                json!({"layers":[{"id":"contact","enabled":false}]}),
            ))
            .id();
        for source in [
            "subject.ik.contact.positionOffset = (1,-2,3)",
            "subject.ik.contact.rotationOffset = (0,45,0)",
            "subject.ik.contact.polePosition = (0,1,1)",
        ] {
            let program = scenemax_parser::parse_program(source).unwrap();
            let scenemax_parser::Statement::Ik(cmd) = program.statements[0].clone() else {
                panic!("Expected IK statement")
            };
            enqueue(
                &mut world.commands(),
                cmd,
                Some(owner),
                None,
                None,
                None,
                None,
            );
            world.flush();
        }
        let layer = &world.get::<Rig>(owner).unwrap().definition["layers"][0];
        assert_eq!(layer["positionOffset"], json!([1., -2., 3.]));
        assert_eq!(layer["polePosition"], json!([0., 1., 1.]));
        assert_eq!(layer["rotationOffset"], json!([0., 45., 0.]));
        assert_eq!(layer["matchTargetRotation"], true);
        assert_eq!(layer["enabled"], false);
    }
}
