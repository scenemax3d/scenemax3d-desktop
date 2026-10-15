//! Adapt bundled duplicate-rig clips to the skeleton used by the preview mesh.
use bevy::{animation::AnimationTargetId, prelude::*};
use std::collections::{HashMap, HashSet};

pub(crate) fn bone_names(candidates: &[(Entity, String, bool)]) -> Vec<String> {
    let mut names: Vec<_> = candidates
        .iter()
        .filter(|(_, _, joint)| *joint)
        .map(|(_, name, _)| name.clone())
        .collect();
    names.sort();
    names.dedup();
    names
}

pub(crate) fn adapt(
    source: &AnimationClip,
    targets: &[(AnimationTargetId, String, bool)],
) -> AnimationClip {
    let mut destinations: HashMap<String, Vec<AnimationTargetId>> = HashMap::new();
    for (id, name, used) in targets {
        if *used {
            let list = destinations.entry(name.to_lowercase()).or_default();
            if !list.contains(id) {
                list.push(*id);
            }
        }
    }
    let live: HashSet<_> = targets
        .iter()
        .filter(|(_, _, used)| *used)
        .map(|(id, _, _)| *id)
        .collect();
    let mut result = source.clone();
    // Keep original curves and event timing. Add aliases only to uniquely named live bones;
    // an authored curve already targeting that bone always takes precedence.
    for (id, name, _) in targets {
        if live.contains(id) {
            continue;
        }
        let Some(destination) = destinations
            .get(&name.to_lowercase())
            .filter(|ids| ids.len() == 1)
            .map(|ids| ids[0])
        else {
            continue;
        };
        if source.curves().contains_key(&destination) {
            continue;
        }
        if let Some(curves) = source.curves().get(id) {
            result
                .curves_mut()
                .entry(destination)
                .or_insert_with(|| curves.clone());
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn dropdown_excludes_named_scene_and_animation_roots() {
        let mut w = World::new();
        let root = w.spawn_empty().id();
        let bone = w.spawn_empty().id();
        let duplicate = w.spawn_empty().id();
        assert_eq!(
            bone_names(&[
                (root, "Walk animation".into(), false),
                (bone, "Grip".into(), true),
                (duplicate, "Grip".into(), false)
            ]),
            vec!["Grip"]
        );
    }
    #[test]
    fn clip_for_unused_rig_animates_live_bone_without_changing_source() {
        let source_id = AnimationTargetId::from_iter(["UnusedRig", "Grip"]);
        let live_id = AnimationTargetId::from_iter(["VisibleRig", "Grip"]);
        let mut source = AnimationClip::default();
        source.add_curve_to_target(
            source_id,
            AnimatableCurve::new(
                bevy::animation::animated_field!(Transform::translation),
                UnevenSampleAutoCurve::new([(0., Vec3::ZERO), (1., Vec3::X)]).unwrap(),
            ),
        );
        let adapted = adapt(
            &source,
            &[
                (source_id, "Grip".into(), false),
                (live_id, "Grip".into(), true),
            ],
        );
        assert!(adapted.curves().contains_key(&live_id));
        assert!(!source.curves().contains_key(&live_id));
        assert_eq!(adapted.duration(), source.duration());
        let mut app = App::new();
        app.add_plugins((
            MinimalPlugins,
            AssetPlugin::default(),
            bevy::animation::AnimationPlugin,
        ));
        let handle = app
            .world_mut()
            .resource_mut::<Assets<AnimationClip>>()
            .add(adapted);
        let (graph, nodes) = AnimationGraph::from_clips([handle]);
        let graph = app
            .world_mut()
            .resource_mut::<Assets<AnimationGraph>>()
            .add(graph);
        let mut player = AnimationPlayer::default();
        player.play(nodes[0]).seek_to(0.5).pause();
        let player = app
            .world_mut()
            .spawn((player, AnimationGraphHandle(graph)))
            .id();
        let bone = app
            .world_mut()
            .spawn((
                Transform::default(),
                live_id,
                bevy::animation::AnimatedBy(player),
            ))
            .id();
        app.update();
        app.update();
        assert!((app.world().get::<Transform>(bone).unwrap().translation.x - 0.5).abs() < 0.001);
    }
}
