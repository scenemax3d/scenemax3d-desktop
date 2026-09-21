//! Renzora-derived gizmo adapter, bound to the live scene document.
//! Attribution: IDE/third_party/renzora_gizmo/NOTICE.md.
mod material;
pub(crate) mod math;
use super::*;
pub(crate) use material::GizmoMaterial;

pub(crate) struct GizmoPlugin;
impl Plugin for GizmoPlugin {
    fn build(&self, app: &mut App) {
        bevy::asset::embedded_asset!(app, "gizmo_material.wgsl");
        app.add_plugins(MaterialPlugin::<GizmoMaterial>::default())
            .init_resource::<State>()
            .add_systems(
                Update,
                update
                    .before(super::ik_controls::commit)
                    .before(super::live::update),
            )
            .add_systems(
                Update,
                super::picking::update
                    .after(super::live::update)
                    .before(super::update),
            );
    }
}
#[derive(Component)]
pub(crate) struct Viewport(pub Entity);
#[derive(Component)]
pub(crate) struct SceneObject(pub usize, pub Transform);
#[derive(Component)]
pub(super) struct TransformReadout(pub usize);
#[derive(Component)]
struct HandleAxis(usize);
#[derive(Component)]
struct Root;
#[derive(Component)]
struct ModeButton(Mode);
#[derive(Clone, Copy, Default, PartialEq, Eq)]
enum Mode {
    #[default]
    Move,
    Rotate,
    Scale,
}
#[derive(Resource, Default)]
pub(crate) struct State {
    mode: Mode,
    key: Option<(Entity, Mode)>,
    root: Option<Entity>,
    pub(super) drag: Option<DragState>,
}
pub(super) struct DragState {
    entity: Entity,
    axis: usize,
    start: Transform,
    param: f32,
    direction: Vec3,
    size: f32,
}
pub(crate) fn toolbar(commands: &mut Commands, parent: Entity) {
    let bar = parent;
    commands.spawn((label("Gizmo:", 11.), ChildOf(bar)));
    for (caption, mode) in [
        ("Move", Mode::Move),
        ("Rotate", Mode::Rotate),
        ("Scale", Mode::Scale),
    ] {
        let control = super::tools::control(
            commands,
            bar,
            &caption.to_lowercase(),
            caption,
            ModeButton(mode),
        );
        commands
            .entity(control)
            .observe(move |_: On<Pointer<Click>>, mut state: ResMut<State>| {
                if state.drag.is_none() {
                    state.mode = mode;
                }
            });
    }
    let reset = super::tools::control(
        commands,
        bar,
        "reset",
        "Undo last scene edit",
        ModeButton(Mode::Move),
    );
    commands.entity(reset).remove::<ModeButton>();
    commands.entity(reset).observe(
        |_: On<Pointer<Click>>, mut queue: ResMut<crate::application::CommandQueue>| {
            queue.0.push_back(crate::application::Command::Edit(
                crate::application::EditCommand::Undo,
            ));
        },
    );
}

type Objects<'w, 's> = Query<
    'w,
    's,
    (
        Entity,
        Option<&'static SceneObject>,
        &'static mut Transform,
        &'static ChildOf,
        Option<&'static crate::presentation::animation_analyzer::preview::ModelMarker>,
    ),
    (
        Without<Root>,
        Or<(
            With<SceneObject>,
            With<super::ik_controls::Proxy>,
            With<crate::presentation::animation_analyzer::preview::ModelMarker>,
        )>,
    ),
>;
#[derive(bevy::ecs::system::SystemParam)]
struct View<'w, 's> {
    analyzer: Option<Res<'w, crate::presentation::animation_analyzer::State>>,
    windows: Query<'w, 's, &'static Window>,
    ports: Query<
        'w,
        's,
        (
            &'static Viewport,
            &'static ComputedNode,
            &'static UiGlobalTransform,
        ),
    >,
    cameras: Query<'w, 's, (&'static GlobalTransform, &'static Projection), With<Camera3d>>,
    objects: Objects<'w, 's>,
    parents: Query<'w, 's, &'static GlobalTransform>,
    roots: Query<'w, 's, &'static mut Transform, With<Root>>,
    handles: Query<'w, 's, (&'static HandleAxis, &'static MeshMaterial3d<GizmoMaterial>)>,
    buttons: Query<'w, 's, (&'static ModeButton, &'static mut BackgroundColor)>,
    readouts: Query<'w, 's, (&'static TransformReadout, &'static mut Text)>,
    meshes: ResMut<'w, Assets<Mesh>>,
    materials: ResMut<'w, Assets<GizmoMaterial>>,
    mouse: Res<'w, ButtonInput<MouseButton>>,
    keys: Res<'w, ButtonInput<KeyCode>>,
}
fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    scene: Res<SceneState>,
    importer: Option<Res<crate::presentation::model_import::State>>,
    drawing: Option<Res<super::path::Drawing>>,
    mut view: View,
    ik: Option<Res<super::ik_controls::State>>,
) {
    if drawing.is_some_and(|d| d.document.is_some()) {
        return;
    }
    let analyzer_target = view.analyzer.as_ref().and_then(|s| s.gizmo_target());
    if let Some((_, _, _, mode)) = analyzer_target {
        state.mode = match mode {
            0 => Mode::Move,
            1 => Mode::Rotate,
            _ => Mode::Scale,
        };
    }
    let ik_proxy =
        if analyzer_target.is_some() || importer.as_ref().is_some_and(|i| i.target.is_some()) {
            None
        } else {
            ik.as_ref().and_then(|s| s.proxy)
        };
    if ik_proxy.is_some() && (state.mode == Mode::Scale || ik.as_ref().is_some_and(|s| s.is_bend()))
    {
        state.mode = Mode::Move;
    }
    for (button, mut color) in &mut view.buttons {
        color.0 = if button.0 == state.mode {
            SELECTED
        } else {
            PANEL
        };
    }
    let target = analyzer_target
        .map(|(root, camera, _, _)| (root, camera, 0))
        .or_else(|| importer.as_ref().and_then(|i| i.target))
        .or_else(|| {
            scene
                .world
                .zip(scene.camera)
                .map(|(w, c)| (w, c, scene.selected))
        });
    let Some((owner, camera, selected)) = target else {
        state.key = None;
        state.root = None;
        state.drag = None;
        return;
    };
    if state.key != Some((owner, state.mode)) {
        if let Some(root) = state.root.take() {
            commands.entity(root).try_despawn();
        }
        state.drag = None;
        state.root = Some(spawn_handles(
            &mut commands,
            owner,
            state.mode,
            if analyzer_target.is_some() { 13 } else { 1 },
            &mut view.meshes,
            &mut view.materials,
        ));
        state.key = Some((owner, state.mode));
    }
    let Some((entity, _object, mut local, parent, analyzer_model)) =
        view.objects.iter_mut().find(|(e, o, _, _, _)| {
            analyzer_target
                .map(|(_, _, entity, _)| entity)
                .or(ik_proxy)
                .map_or_else(|| o.is_some_and(|o| o.0 == selected), |proxy| *e == proxy)
        })
    else {
        return;
    };
    let parent = view
        .parents
        .get(parent.parent())
        .copied()
        .unwrap_or_default();
    let pivot = analyzer_model.map_or(Vec3::ZERO, |m| m.pivot);
    let mut transform = parent.mul_transform(*local).compute_transform();
    transform.translation = transform.transform_point(pivot);
    let Ok((camera_gt, projection)) = view.cameras.get(camera) else {
        return;
    };
    let Some((_, node, ui)) = view.ports.iter().find(|(port, _, _)| port.0 == camera) else {
        return;
    };
    if node.size().min_element() <= 0. {
        return;
    }
    let height = node.size().y * node.inverse_scale_factor();
    let distance = camera_gt.translation().distance(transform.translation);
    let size = match projection {
        Projection::Perspective(p) => (distance * (p.fov * 0.5).tan() * 100. / height).max(0.001),
        Projection::Orthographic(p) => p.area.height() * 50. / height,
        _ => 1.,
    };
    if let Some(root) = state.root
        && let Ok(mut root) = view.roots.get_mut(root)
    {
        root.translation = transform.translation;
        root.rotation = if state.mode == Mode::Scale {
            transform.rotation
        } else {
            Quat::IDENTITY
        };
        root.scale = Vec3::splat(size);
    }
    let ray = view
        .windows
        .single()
        .ok()
        .and_then(Window::cursor_position)
        .and_then(|cursor| {
            let local = ui
                .try_inverse()?
                .transform_point2(cursor / node.inverse_scale_factor());
            let uv = local / node.size() + Vec2::splat(0.5);
            if uv.min_element() < 0. || uv.max_element() > 1. {
                return None;
            }
            let Projection::Perspective(p) = projection else {
                return None;
            };
            let half = (p.fov * 0.5).tan();
            let dir = camera_gt.rotation()
                * Vec3::new(
                    (uv.x * 2. - 1.) * half * p.aspect_ratio,
                    (1. - uv.y * 2.) * half,
                    -1.,
                );
            Some(Ray3d::new(camera_gt.translation(), Dir3::new(dir).ok()?))
        });
    let axes = [Vec3::X, Vec3::Y, Vec3::Z].map(|axis| {
        if state.mode == Mode::Scale {
            transform.rotation * axis
        } else {
            axis
        }
    });
    let hover = ray.as_ref().and_then(|ray| {
        axes.iter()
            .enumerate()
            .filter_map(|(i, axis)| {
                let d = if state.mode == Mode::Rotate {
                    math::ray_circle_distance(ray, transform.translation, *axis, size * 1.6)
                } else {
                    math::closest_distance_ray_segment(
                        ray,
                        transform.translation + *axis * size * 0.2,
                        transform.translation + *axis * size * 2.,
                    )
                }?;
                (d < size * 0.16).then_some((
                    i,
                    d + if state.mode == Mode::Rotate {
                        size * 0.001 * (1. - ray.direction.dot(*axis).abs())
                    } else {
                        0.
                    },
                ))
            })
            .min_by(|a, b| a.1.total_cmp(&b.1))
            .map(|(i, _)| i)
    });
    if view.mouse.just_pressed(MouseButton::Left)
        && let (Some(axis), Some(ray)) = (hover, ray.as_ref())
    {
        let direction = math::ray_plane_point(ray, transform.translation, axes[axis])
            .map(|p| (p - transform.translation).normalize_or_zero())
            .unwrap_or(Vec3::ZERO);
        state.drag = Some(DragState {
            entity,
            axis,
            start: transform,
            param: math::ray_line_param(ray, transform.translation, axes[axis]).unwrap_or(0.),
            direction,
            size,
        });
    }
    let mut manipulated = false;
    if let Some(drag) = &state.drag {
        if drag.entity == entity {
            manipulated = true;
            if view.keys.just_pressed(KeyCode::Escape) {
                transform = drag.start;
            } else if let Some(ray) = &ray {
                let axis = axes[drag.axis];
                match state.mode {
                    Mode::Move | Mode::Scale => {
                        if let Some(param) = math::ray_line_param(ray, drag.start.translation, axis)
                        {
                            let delta = param - drag.param;
                            if state.mode == Mode::Move {
                                transform.translation = drag.start.translation + axis * delta;
                            } else {
                                transform.scale[drag.axis] = drag.start.scale[drag.axis]
                                    * (1. + delta / (drag.size * 2.)).max(0.01);
                            }
                        }
                    }
                    Mode::Rotate => {
                        if let Some(p) = math::ray_plane_point(ray, drag.start.translation, axis) {
                            let v = (p - drag.start.translation).normalize_or_zero();
                            let angle = axis
                                .dot(drag.direction.cross(v))
                                .atan2(drag.direction.dot(v));
                            transform.rotation =
                                Quat::from_axis_angle(axis, angle) * drag.start.rotation;
                        }
                    }
                }
            }
        }
        if drag.entity != entity
            || view.mouse.just_released(MouseButton::Left)
            || view.keys.just_pressed(KeyCode::Escape)
        {
            state.drag = None;
        }
    }
    let selected = state.drag.as_ref().map(|d| d.axis).or(hover);
    for (axis, handle) in &view.handles {
        if let Some(mut material) = view.materials.get_mut(&handle.0) {
            material.base_color = if selected == Some(axis.0) {
                LinearRgba::new(1., 1., 0.2, 1.)
            } else {
                axis_color(axis.0)
            };
        }
    }
    for (field, mut text) in &mut view.readouts {
        let value = match field.0 {
            3 => format!("Position\n{:.2?}", transform.translation.to_array()),
            4 => format!("Scale\n{:.3?}", transform.scale.to_array()),
            _ => format!("Rotation (XYZW)\n{:.3?}", transform.rotation.to_array()),
        };
        if text.0 != value {
            text.0 = value;
        }
    }
    if manipulated {
        transform.translation -= transform.rotation * (transform.scale * pivot);
        *local =
            Transform::from_matrix(Mat4::from(parent.affine().inverse()) * transform.to_matrix());
    }
}
fn axis_color(axis: usize) -> LinearRgba {
    [
        LinearRgba::new(1., 0.15, 0.15, 1.),
        LinearRgba::new(0.15, 1., 0.15, 1.),
        LinearRgba::new(0.2, 0.3, 1., 1.),
    ][axis]
}
fn spawn_handles(
    commands: &mut Commands,
    owner: Entity,
    mode: Mode,
    layer: usize,
    meshes: &mut Assets<Mesh>,
    materials: &mut Assets<GizmoMaterial>,
) -> Entity {
    let root = commands
        .spawn((
            Root,
            Transform::default(),
            Visibility::default(),
            ChildOf(owner),
        ))
        .id();
    for (axis, direction) in [Vec3::X, Vec3::Y, Vec3::Z].into_iter().enumerate() {
        let color = axis_color(axis);
        let material = materials.add(GizmoMaterial {
            base_color: color,
            emissive: color,
        });
        let rotation = Quat::from_rotation_arc(Vec3::Y, direction);
        let parts = if mode == Mode::Rotate {
            vec![(meshes.add(Torus::new(1.56, 1.64)), Vec3::ZERO)]
        } else {
            let tip = if mode == Mode::Move {
                meshes.add(Cone {
                    radius: 0.15,
                    height: 0.4,
                })
            } else {
                meshes.add(Cuboid::new(0.25, 0.25, 0.25))
            };
            vec![
                (meshes.add(Cylinder::new(0.05, 1.6)), direction * 0.8),
                (tip, direction * 1.8),
            ]
        };
        for (mesh, translation) in parts {
            commands.spawn((
                HandleAxis(axis),
                bevy::camera::visibility::RenderLayers::layer(layer),
                Mesh3d(mesh),
                MeshMaterial3d(material.clone()),
                Transform {
                    translation,
                    rotation,
                    ..default()
                },
                ChildOf(root),
            ));
        }
    }
    root
}

#[cfg(test)]
mod tests;
