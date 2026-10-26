//! Screen-space, depth-tested infinite work plane for the editor camera only.
use super::SceneState;
use bevy::{
    camera::visibility::{NoFrustumCulling, RenderLayers},
    mesh::MeshVertexBufferLayoutRef,
    pbr::{MaterialPipeline, MaterialPipelineKey},
    prelude::*,
    render::render_resource::{
        AsBindGroup, RenderPipelineDescriptor, SpecializedMeshPipelineError,
    },
    shader::ShaderRef,
};

pub(crate) struct GridPlugin;
impl Plugin for GridPlugin {
    fn build(&self, app: &mut App) {
        bevy::asset::embedded_asset!(app, "grid.wesl");
        app.add_plugins(MaterialPlugin::<GridMaterial>::default())
            .init_resource::<GridState>()
            .add_systems(Update, update.after(super::update));
    }
}
#[derive(Asset, TypePath, AsBindGroup, Debug, Clone)]
struct GridMaterial {
    #[uniform(0)]
    color: LinearRgba,
}
impl Material for GridMaterial {
    fn vertex_shader() -> ShaderRef {
        "embedded://scenemax_ide/presentation/scene3d/grid.wesl".into()
    }
    fn fragment_shader() -> ShaderRef {
        Self::vertex_shader()
    }
    fn alpha_mode(&self) -> AlphaMode {
        AlphaMode::Blend
    }
    fn specialize(
        _: &MaterialPipeline,
        descriptor: &mut RenderPipelineDescriptor,
        _: &MeshVertexBufferLayoutRef,
        _: MaterialPipelineKey<Self>,
    ) -> Result<(), SpecializedMeshPipelineError> {
        descriptor.primitive.cull_mode = None;
        // The fragment shader supplies the true ground-plane depth. The grid does
        // not write depth, cast shadows or prevent picking the scene beneath it.
        if let Some(depth) = &mut descriptor.depth_stencil {
            depth.depth_write_enabled = Some(false);
        }
        Ok(())
    }
}
#[derive(Component)]
struct Grid;
#[derive(Resource, Default)]
struct GridState {
    owner: Option<Entity>,
    entity: Option<Entity>,
}
fn update(
    mut c: Commands,
    scene: Res<SceneState>,
    mut state: ResMut<GridState>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<GridMaterial>>,
) {
    if state.owner == scene.world {
        return;
    }
    if let Some(entity) = state.entity.take() {
        c.entity(entity).try_despawn();
    }
    state.owner = scene.world;
    if let Some(owner) = scene.world {
        state.entity = Some(
            c.spawn((
                Grid,
                Mesh3d(meshes.add(Rectangle::new(2., 2.))),
                MeshMaterial3d(materials.add(GridMaterial {
                    color: LinearRgba::new(0.32, 0.36, 0.42, 1.),
                })),
                Transform::default(),
                Visibility::default(),
                RenderLayers::layer(1),
                NoFrustumCulling,
                Pickable::IGNORE,
                bevy::light::NotShadowCaster,
                bevy::light::NotShadowReceiver,
                ChildOf(owner),
            ))
            .id(),
        );
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[test]
    fn grid_is_owned_by_scene_world_and_never_duplicated() {
        let mut w = World::new();
        w.init_resource::<SceneState>();
        w.init_resource::<GridState>();
        w.init_resource::<Assets<Mesh>>();
        w.init_resource::<Assets<GridMaterial>>();
        let owner = w.spawn_empty().id();
        w.resource_mut::<SceneState>().world = Some(owner);
        for _ in 0..3 {
            w.run_system_once(update).unwrap();
        }
        let (e, layers, parent) = w
            .query_filtered::<(Entity, &RenderLayers, &ChildOf), With<Grid>>()
            .single(&w)
            .unwrap();
        assert_eq!(parent.parent(), owner);
        assert!(!layers.intersects(&RenderLayers::layer(0)));
        assert!(layers.intersects(&RenderLayers::layer(1)));
        assert!(w.get::<NoFrustumCulling>(e).is_some());
        assert!(w.get::<super::super::gizmo::SceneObject>(e).is_none());
        w.despawn(owner);
        w.resource_mut::<SceneState>().world = None;
        w.run_system_once(update).unwrap();
        assert_eq!(w.query_filtered::<Entity, With<Grid>>().iter(&w).count(), 0);
    }
}
