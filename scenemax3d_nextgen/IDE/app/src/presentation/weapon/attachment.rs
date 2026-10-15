//! Resolve real skin joints before same-named nodes from unused imported rigs.
use bevy::{prelude::*, transform::helper::TransformHelper};

pub(crate) fn resolve(
    wanted: &str,
    candidates: &[(Entity, String, bool)],
) -> Result<Option<Entity>, String> {
    if wanted.is_empty() {
        return Ok(None);
    }
    let mut matches: Vec<_> = candidates
        .iter()
        .filter_map(|(entity, name, joint)| {
            let exact = name.eq_ignore_ascii_case(wanted);
            let alias = name
                .rsplit(['/', '.'])
                .next()
                .is_some_and(|tail| tail.eq_ignore_ascii_case(wanted));
            (exact || alias).then_some((*entity, (*joint, exact)))
        })
        .collect();
    matches.sort_by_key(|(_, rank)| std::cmp::Reverse(*rank));
    let Some((entity, rank)) = matches.first() else {
        return Err(format!("Attachment '{wanted}' not found on player"));
    };
    if matches.get(1).is_some_and(|(_, other)| other == rank) {
        return Err(format!(
            "Attachment '{wanted}' is ambiguous; use a unique rig node name"
        ));
    }
    Ok(Some(*entity))
}

#[derive(Component)]
pub(crate) struct BoneAttachment {
    pub(super) joint: Entity,
    pub(super) offset: Transform,
}

/// Run after animation and before propagation so the weapon follows this frame's bone pose.
pub(crate) fn follow(
    attachments: Query<(Entity, &BoneAttachment, &ChildOf)>,
    mut transforms: ParamSet<(TransformHelper, Query<&mut Transform>)>,
) {
    let poses: Vec<_> = attachments
        .iter()
        .filter_map(|(entity, attachment, parent)| {
            let helper = transforms.p0();
            let bone = helper.compute_global_transform(attachment.joint).ok()?;
            let parent = helper.compute_global_transform(parent.parent()).ok()?;
            let (_, rotation, translation) = bone.to_scale_rotation_translation();
            let pose = GlobalTransform::from(
                Transform {
                    translation,
                    rotation,
                    scale: Vec3::ONE,
                }
                .mul_transform(attachment.offset),
            );
            Some((entity, pose.reparented_to(&parent)))
        })
        .collect();
    for (entity, pose) in poses {
        if let Ok(mut transform) = transforms.p1().get_mut(entity) {
            *transform = pose;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn actual_skin_joint_wins_independently_of_iteration_order() {
        let mut world = World::new();
        let live = world.spawn_empty().id();
        let unused = world.spawn_empty().id();
        let mut candidates = vec![
            (live, "rig/Grip".into(), true),
            (unused, "Grip".into(), false),
        ];
        assert_eq!(resolve("Grip", &candidates).unwrap(), Some(live));
        candidates[0].1 = "Grip".into();
        candidates.reverse();
        assert_eq!(resolve("Grip", &candidates).unwrap(), Some(live));
        candidates.push((world.spawn_empty().id(), "Grip".into(), true));
        assert!(resolve("Grip", &candidates).is_err());
        assert!(resolve("missing", &candidates).is_err());
    }
    #[test]
    fn weapon_follows_current_bone_not_previous_global_transform() {
        let mut app = App::new();
        app.add_plugins(bevy::transform::TransformPlugin)
            .add_systems(
                PostUpdate,
                follow.before(bevy::transform::TransformSystems::Propagate),
            );
        let root = app.world_mut().spawn(Transform::default()).id();
        let model = app
            .world_mut()
            .spawn((Transform::from_scale(Vec3::splat(3.)), ChildOf(root)))
            .id();
        let joint = app
            .world_mut()
            .spawn((Transform::from_xyz(1., 2., 0.), ChildOf(model)))
            .id();
        let weapon = app
            .world_mut()
            .spawn((
                Transform::default(),
                ChildOf(root),
                BoneAttachment {
                    joint,
                    offset: Transform::from_xyz(0.5, 0., 0.),
                },
            ))
            .id();
        app.update();
        app.world_mut()
            .get_mut::<Transform>(joint)
            .unwrap()
            .translation = Vec3::new(4., 2., 0.);
        app.world_mut()
            .get_mut::<Transform>(joint)
            .unwrap()
            .rotation = Quat::from_rotation_z(std::f32::consts::FRAC_PI_2);
        app.update();
        let pose = app.world().get::<GlobalTransform>(weapon).unwrap();
        assert!(
            pose.translation()
                .abs_diff_eq(Vec3::new(12., 6.5, 0.), 0.0001)
        );
        let (scale, rotation, _) = pose.to_scale_rotation_translation();
        assert!(scale.abs_diff_eq(Vec3::ONE, 0.0001));
        assert!(rotation.abs_diff_eq(Quat::from_rotation_z(std::f32::consts::FRAC_PI_2), 0.0001));
    }
}
