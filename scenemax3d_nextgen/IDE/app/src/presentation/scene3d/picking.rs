//! Viewport-only picking resolves rendered model descendants to scene entries.
use super::*;
#[derive(Resource, Default)]
pub(crate) struct Request(Option<(Entity, Vec2)>);
pub(super) fn observe(commands: &mut Commands, viewport: Entity) {
    commands.entity(viewport).observe(
        move |event: On<Pointer<Press>>, mut request: ResMut<Request>| {
            if event.button == PointerButton::Primary {
                request.0 = Some((viewport, event.pointer_location.position));
            }
        },
    );
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct View<'w, 's> {
    ports: Query<
        'w,
        's,
        (
            &'static gizmo::Viewport,
            &'static ComputedNode,
            &'static UiGlobalTransform,
        ),
    >,
    cameras: Query<'w, 's, (&'static GlobalTransform, &'static Projection)>,
    parents: Query<'w, 's, &'static ChildOf>,
    objects: Query<'w, 's, &'static gizmo::SceneObject>,
    drawing: Res<'w, path::Drawing>,
    ik_handles: Query<'w, 's, &'static super::ik_controls::Handle>,
    gizmo: Res<'w, gizmo::State>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut request: ResMut<Request>,
    mut raycast: MeshRayCast,
    view: View,
    mut state: ResMut<SceneState>,
    mut ik: Option<ResMut<super::ik_controls::State>>,
) {
    let Some((viewport, position)) = request.0.take() else {
        return;
    };
    if view.drawing.document.is_some() || view.gizmo.drag.is_some() {
        return;
    }
    let Ok((port, node, ui)) = view.ports.get(viewport) else {
        return;
    };
    let Ok((camera, projection)) = view.cameras.get(port.0) else {
        return;
    };
    let Some(inverse) = ui.try_inverse() else {
        return;
    };
    let uv = inverse.transform_point2(position / node.inverse_scale_factor()) / node.size()
        + Vec2::splat(0.5);
    if uv.min_element() < 0. || uv.max_element() > 1. {
        return;
    }
    let Projection::Perspective(p) = projection else {
        return;
    };
    let half = (p.fov * 0.5).tan();
    let Ok(direction) = Dir3::new(
        camera.rotation()
            * Vec3::new(
                (uv.x * 2. - 1.) * half * p.aspect_ratio,
                (1. - uv.y * 2.) * half,
                -1.,
            ),
    ) else {
        return;
    };
    if let Some(ik) = ik.as_mut() {
        let filter = |entity| view.ik_handles.contains(entity);
        if let Some(handle) = raycast
            .cast_ray(
                Ray3d::new(camera.translation(), direction),
                &MeshRayCastSettings::default().with_filter(&filter),
            )
            .first()
            .and_then(|(e, _)| view.ik_handles.get(*e).ok())
            .copied()
        {
            ik.requested = Some(handle);
            return;
        }
    }
    let owner = |mut entity: Entity| {
        for _ in 0..128 {
            if let Ok(object) = view.objects.get(entity) {
                return Some(object.0);
            }
            entity = view.parents.get(entity).ok()?.parent();
        }
        None
    };
    let filter = |entity| owner(entity).is_some();
    let hit = raycast
        .cast_ray(
            Ray3d::new(camera.translation(), direction),
            &MeshRayCastSettings::default().with_filter(&filter),
        )
        .first()
        .and_then(|(entity, _)| owner(*entity));
    if let Some(index) = hit {
        if let Some(ik) = ik.as_mut() {
            ik.exit = true;
        }
        select(&mut commands, &mut state, index);
    }
}
pub(super) fn select(commands: &mut Commands, state: &mut SceneState, index: usize) {
    let Some(scene) = state.scene.as_ref() else {
        return;
    };
    let Some(entry) = scene.entities.get(index) else {
        return;
    };
    let mut parent = entry.parent;
    while let Some(i) = parent {
        state.collapsed.remove(&i);
        parent = scene.entities[i].parent;
    }
    if state.selected == index {
        return;
    }
    state.selected = index;
    if let Some(parts) = state.parts {
        super::view::inspect(commands, parts, scene, index);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::{camera::primitives::Aabb, ecs::system::RunSystemOnce};
    #[test]
    fn nearest_mesh_resolves_to_model_owner_and_ignores_editor_geometry() {
        bevy::tasks::ComputeTaskPool::get_or_init(bevy::tasks::TaskPool::default);
        let dir = tempfile::tempdir().unwrap();
        let scene=scenemax_ide_services::scene3d::load(dir.path(),r#"{"entities":[{"type":"SECTION","children":[{"type":"MODEL","name":"Near"},{"type":"MODEL","name":"Far"}]}]}"#).unwrap();
        let mut world = World::new();
        world.init_resource::<Assets<Mesh>>();
        world.init_resource::<path::Drawing>();
        world.init_resource::<gizmo::State>();
        world.insert_resource(SceneState {
            scene: Some(scene),
            collapsed: [0].into_iter().collect(),
            ..default()
        });
        let camera = world
            .spawn((
                GlobalTransform::from_translation(Vec3::new(0., 0., 10.)),
                Projection::Perspective(PerspectiveProjection::default()),
            ))
            .id();
        let port = world
            .spawn((
                gizmo::Viewport(camera),
                ComputedNode {
                    size: Vec2::splat(200.),
                    inverse_scale_factor: 1.,
                    ..default()
                },
                UiGlobalTransform::default(),
            ))
            .id();
        world.insert_resource(Request(Some((port, Vec2::ZERO))));
        let mesh = world
            .resource_mut::<Assets<Mesh>>()
            .add(Cuboid::new(1., 1., 1.));
        for (owner, z) in [(Some(1), 0.), (Some(2), -3.), (None, 5.)] {
            let root = world.spawn_empty().id();
            if let Some(index) = owner {
                world
                    .entity_mut(root)
                    .insert(gizmo::SceneObject(index, Transform::default()));
            }
            let visible = ViewVisibility::VISIBLE;
            world.spawn((
                Mesh3d(mesh.clone()),
                GlobalTransform::from_translation(Vec3::new(0., 0., z)),
                Aabb::from_min_max(Vec3::splat(-0.5), Vec3::splat(0.5)),
                InheritedVisibility::VISIBLE,
                visible,
                ChildOf(root),
            ));
        }
        world.run_system_once(update).unwrap();
        let state = world.resource::<SceneState>();
        assert_eq!(state.selected, 1);
        assert!(state.collapsed.is_empty());
        // Always-on-top IK joints remain selectable even behind the model surface.
        world.init_resource::<super::super::ik_controls::State>();
        world.spawn((
            super::super::ik_controls::Handle {
                layer: 2,
                kind: super::super::ik_controls::Kind::Bend,
            },
            Mesh3d(mesh),
            GlobalTransform::from_translation(Vec3::new(0., 0., -5.)),
            Aabb::from_min_max(Vec3::splat(-0.5), Vec3::splat(0.5)),
            InheritedVisibility::VISIBLE,
            ViewVisibility::VISIBLE,
        ));
        world.insert_resource(Request(Some((port, Vec2::ZERO))));
        world.run_system_once(update).unwrap();
        assert_eq!(world.resource::<SceneState>().selected, 1);
        let requested = world
            .resource::<super::super::ik_controls::State>()
            .requested
            .unwrap();
        assert_eq!(requested.layer, 2);
        assert_eq!(requested.kind, super::super::ik_controls::Kind::Bend);
    }
}
