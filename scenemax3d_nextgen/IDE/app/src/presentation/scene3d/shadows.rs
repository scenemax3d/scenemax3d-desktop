//! Apply model shadow choices to meshes, including asynchronously loaded model children.
use super::*;
use bevy::light::{NotShadowCaster, NotShadowReceiver};

pub(crate) fn update(
    mut commands: Commands,
    state: Res<SceneState>,
    meshes: Query<
        (Entity, Has<NotShadowCaster>, Has<NotShadowReceiver>),
        (With<Mesh3d>, Without<super::outline::MaskMesh>),
    >,
    added: Query<(), Added<Mesh3d>>,
    parents: Query<&ChildOf>,
    objects: Query<&gizmo::SceneObject>,
) {
    if !state.is_changed() && added.is_empty() {
        return;
    }
    let Some(scene) = state.scene.as_ref() else {
        return;
    };
    for (entity, no_cast, no_receive) in &meshes {
        let Some(object) = std::iter::once(entity)
            .chain(parents.iter_ancestors(entity))
            .find_map(|e| objects.get(e).ok())
        else {
            continue;
        };
        let Some(entry) = scene.entities.get(object.0).filter(|e| e.kind == "MODEL") else {
            continue;
        };
        let mode = entry.properties["shadowMode"].as_str().unwrap_or("none");
        let cast = matches!(mode, "cast" | "both");
        let receive = matches!(mode, "receive" | "both");
        if no_cast == cast {
            if cast {
                commands.entity(entity).remove::<NotShadowCaster>();
            } else {
                commands.entity(entity).insert(NotShadowCaster);
            }
        }
        if no_receive == receive {
            if receive {
                commands.entity(entity).remove::<NotShadowReceiver>();
            } else {
                commands.entity(entity).insert(NotShadowReceiver);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn shadow_modes_reach_loaded_model_meshes_and_late_children() {
        let dir = tempfile::tempdir().unwrap();
        let mut app = App::new();
        app.insert_resource(SceneState {
            scene: Some(
                scenemax_ide_services::scene3d::load(
                    dir.path(),
                    r#"{"entities":[{"type":"MODEL"}]}"#,
                )
                .unwrap(),
            ),
            ..default()
        })
        .add_systems(Update, update);
        let root = app
            .world_mut()
            .spawn(gizmo::SceneObject(0, Transform::default()))
            .id();
        let mesh = app
            .world_mut()
            .spawn((Mesh3d::default(), ChildOf(root)))
            .id();
        for (mode, cast, receive) in [
            ("none", false, false),
            ("cast", true, false),
            ("receive", false, true),
            ("both", true, true),
        ] {
            app.world_mut()
                .resource_mut::<SceneState>()
                .scene
                .as_mut()
                .unwrap()
                .entities[0]
                .properties["shadowMode"] = serde_json::json!(mode);
            app.update();
            assert_eq!(app.world().get::<NotShadowCaster>(mesh).is_none(), cast);
            assert_eq!(
                app.world().get::<NotShadowReceiver>(mesh).is_none(),
                receive
            );
        }
        app.world_mut()
            .resource_mut::<SceneState>()
            .scene
            .as_mut()
            .unwrap()
            .entities[0]
            .properties["shadowMode"] = serde_json::json!("none");
        app.update();
        let late = app
            .world_mut()
            .spawn((Mesh3d::default(), ChildOf(root)))
            .id();
        app.update();
        assert!(app.world().get::<NotShadowCaster>(late).is_some());
        assert!(app.world().get::<NotShadowReceiver>(late).is_some());
    }
}
