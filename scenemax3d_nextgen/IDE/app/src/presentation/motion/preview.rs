//! Isolated offscreen native 3D preview; no runtime systems involved.
use super::*;
use crate::presentation::scene3d::Orbit;
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, visibility::RenderLayers},
    render::render_resource::{
        Extent3d, PrimitiveTopology, TextureDimension, TextureFormat, TextureUsages,
    },
    ui::widget::ViewportNode,
};
use motion::sampling::{self, Sample};
const LAYER: usize = 11;
#[derive(Component)]
struct PreviewCamera;
#[derive(Component)]
struct Projectile;
#[derive(Component)]
struct Velocity;
#[derive(Default)]
pub(super) struct Preview {
    root: Option<Entity>,
    camera: Option<Entity>,
    geometry: Option<Entity>,
    key: String,
    samples: Vec<Sample>,
    projectile: Option<Entity>,
    velocity: Option<Entity>,
}
impl Preview {
    pub fn clear(&mut self, c: &mut Commands) {
        if let Some(root) = self.root.take() {
            c.entity(root).try_despawn();
        }
        *self = default();
    }
    pub fn duration(&self) -> f32 {
        self.samples.last().map_or(0., |s| s.time)
    }
    pub fn count(&self) -> usize {
        self.samples.len()
    }
}
type Transforms<'w, 's> = Query<
    'w,
    's,
    (&'static mut Transform, Option<&'static mut Orbit>),
    Or<(With<PreviewCamera>, With<Projectile>, With<Velocity>)>,
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Render<'w, 's> {
    images: Option<ResMut<'w, Assets<Image>>>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    transforms: Transforms<'w, 's>,
    winit: Option<ResMut<'w, bevy::winit::WinitSettings>>,
}
fn object(
    c: &mut Commands,
    parent: Entity,
    mesh: Handle<Mesh>,
    material: Handle<StandardMaterial>,
    transform: Transform,
) -> Entity {
    c.spawn((
        Mesh3d(mesh),
        MeshMaterial3d(material),
        transform,
        RenderLayers::layer(LAYER),
        ChildOf(parent),
    ))
    .id()
}
pub(crate) fn update(
    mut c: Commands,
    mut s: ResMut<State>,
    mut r: Render,
    time: Res<Time>,
    mut prior: Local<Option<bevy::winit::UpdateMode>>,
) {
    if let Some(winit) = r.winit.as_mut() {
        if s.host.is_some() && s.playing {
            if prior.is_none() {
                *prior = Some(winit.focused_mode);
            }
            winit.focused_mode = bevy::winit::UpdateMode::Continuous;
        } else if let Some(mode) = prior.take() {
            winit.focused_mode = mode;
        }
    }
    let (Some(images), Some(meshes), Some(materials), Some(parts)) = (
        r.images.as_mut(),
        r.meshes.as_mut(),
        r.materials.as_mut(),
        s.parts.as_ref(),
    ) else {
        return;
    };
    let viewport = parts.viewport;
    if s.render.root.is_none() {
        let root = c.spawn((Transform::default(), Visibility::default())).id();
        s.render.root = Some(root);
        let mut image = Image::new_uninit(
            Extent3d {
                width: 1000,
                height: 750,
                depth_or_array_layers: 1,
            },
            TextureDimension::D2,
            TextureFormat::Bgra8UnormSrgb,
            RenderAssetUsages::all(),
        );
        image.texture_descriptor.usage = TextureUsages::TEXTURE_BINDING
            | TextureUsages::RENDER_ATTACHMENT
            | TextureUsages::COPY_DST;
        let orbit = Orbit {
            target: Vec3::new(0., 2., 6.),
            distance: 24.,
            yaw: 0.61,
            pitch: 0.38,
        };
        let camera = c
            .spawn((
                PreviewCamera,
                Camera3d::default(),
                bevy::pbr::ScreenSpaceTransmission::default(),
                Camera {
                    order: -11,
                    clear_color: ClearColorConfig::Custom(Color::srgb(0.045, 0.05, 0.058)),
                    ..default()
                },
                RenderTarget::Image(images.add(image).into()),
                RenderLayers::layer(LAYER),
                orbit.transform(),
                orbit,
                ChildOf(root),
            ))
            .id();
        s.render.camera = Some(camera);
        c.entity(viewport).insert(ViewportNode::new(camera));
        c.entity(viewport).observe(
            |mut e: On<PointerDrag>,
             mut q: Query<(&mut Orbit, &mut Transform), With<PreviewCamera>>| {
                e.propagate(false);
                for (mut o, mut t) in &mut q {
                    o.yaw -= e.delta.x * 0.006;
                    o.pitch = (o.pitch + e.delta.y * 0.006).clamp(-1.22, 1.22);
                    *t = o.transform();
                }
            },
        );
        c.entity(viewport).observe(
            |mut e: On<PointerScroll>,
             mut q: Query<(&mut Orbit, &mut Transform), With<PreviewCamera>>| {
                e.propagate(false);
                let delta = if e.unit == bevy::input::mouse::MouseScrollUnit::Line {
                    e.y * 0.12
                } else {
                    e.y * 0.001
                };
                for (mut o, mut t) in &mut q {
                    o.distance = (o.distance * (1. + delta)).clamp(2., 10000.);
                    *t = o.transform();
                }
            },
        );
    }
    let key = format!(
        "{}|{}|{}|{}|{}",
        s.data, s.distance, s.height, s.trajectory, s.samples
    );
    if s.render.key != key {
        let changed_motion = s.render.key.split('|').take(3).collect::<Vec<_>>()
            != key.split('|').take(3).collect::<Vec<_>>();
        s.render.key = key;
        if changed_motion {
            s.time = 0.;
        }
        if let Some(g) = s.render.geometry.take() {
            c.entity(g).try_despawn();
        }
        let Some(root) = s.render.root else {
            return;
        };
        let g = c
            .spawn((Transform::default(), Visibility::default(), ChildOf(root)))
            .id();
        s.render.geometry = Some(g);
        s.render.samples = if motion::validate(&s.data).is_ok() {
            sampling::sample(&s.data, s.distance, s.height)
        } else {
            vec![]
        };
        let sphere = meshes.add(Sphere::new(1.).mesh().uv(16, 12));
        let cube = meshes.add(Cuboid::new(1., 1., 1.));
        let colors = [
            Color::srgb(0.18, 0.20, 0.22),
            Color::srgb(0.4, 0.8, 1.),
            Color::srgb(1., 0.25, 0.25),
            Color::srgb(0.92, 0.62, 0.22),
            Color::srgb(0.1, 0.85, 0.95),
            Color::srgb(1., 0.92, 0.25),
            Color::srgb(0.45, 1., 0.45),
        ];
        let mats = colors.map(|color| {
            materials.add(StandardMaterial {
                base_color: color,
                unlit: true,
                ..default()
            })
        });
        object(
            &mut c,
            g,
            cube.clone(),
            mats[0].clone(),
            Transform::from_xyz(0., -0.02, 10.).with_scale(Vec3::new(36., 0.04, 48.)),
        );
        object(
            &mut c,
            g,
            sphere.clone(),
            mats[1].clone(),
            Transform::from_xyz(0., 1.2, 0.).with_scale(Vec3::splat(0.16)),
        );
        object(
            &mut c,
            g,
            sphere.clone(),
            mats[2].clone(),
            Transform::from_xyz(0., 1.2 + s.height, s.distance).with_scale(Vec3::splat(0.22)),
        );
        if s.trajectory && s.render.samples.len() > 1 {
            let positions = s
                .render
                .samples
                .iter()
                .map(|s| s.position)
                .collect::<Vec<_>>();
            let mesh = meshes.add(
                Mesh::new(PrimitiveTopology::LineStrip, RenderAssetUsages::all())
                    .with_inserted_attribute(Mesh::ATTRIBUTE_POSITION, positions),
            );
            object(&mut c, g, mesh, mats[4].clone(), Transform::default());
        }
        if s.samples {
            for sample in s
                .render
                .samples
                .iter()
                .step_by((s.render.samples.len() / 28).max(1))
            {
                object(
                    &mut c,
                    g,
                    sphere.clone(),
                    mats[5].clone(),
                    Transform::from_translation(Vec3::from_array(sample.position))
                        .with_scale(Vec3::splat(0.055)),
                );
            }
        }
        let projectile = object(
            &mut c,
            g,
            sphere,
            mats[3].clone(),
            Transform::from_xyz(0., 1.2, 0.).with_scale(Vec3::splat(0.28)),
        );
        c.entity(projectile).insert(Projectile);
        s.render.projectile = Some(projectile);
        let velocity = object(
            &mut c,
            g,
            cube,
            mats[6].clone(),
            Transform::from_scale(Vec3::ZERO),
        );
        c.entity(velocity).insert(Velocity);
        s.render.velocity = Some(velocity);
    }
    if s.frame
        && let Some(camera) = s.render.camera
        && let Ok((mut t, Some(mut o))) = r.transforms.get_mut(camera)
    {
        let mut min = Vec3::ZERO;
        let mut max = Vec3::new(0., 1.2 + s.height, s.distance);
        for sample in &s.render.samples {
            let p = Vec3::from_array(sample.position);
            min = min.min(p);
            max = max.max(p);
        }
        o.target = (min + max) * 0.5;
        o.distance = ((max - min).length() * 1.4).max(6.);
        o.yaw = 0.61;
        o.pitch = 0.38;
        *t = o.transform();
        s.frame = false;
    }
    if s.playing {
        s.time += time.delta_secs();
    }
    if s.time > s.render.duration() {
        s.time = 0.;
    }
    if let Some(sample) = sampling::at(&s.render.samples, s.time) {
        let pos = Vec3::from_array(sample.position);
        if let Some(entity) = s.render.projectile
            && let Ok((mut t, _)) = r.transforms.get_mut(entity)
        {
            t.translation = pos;
        }
        if let Some(entity) = s.render.velocity
            && let Ok((mut t, _)) = r.transforms.get_mut(entity)
        {
            let dir = Vec3::from_array(sample.velocity).normalize_or_zero();
            let end = pos + dir * 1.3;
            *t = Transform::from_translation((pos + end) * 0.5)
                .looking_at(
                    end,
                    if dir.dot(Vec3::Y).abs() > 0.99 {
                        Vec3::X
                    } else {
                        Vec3::Y
                    },
                )
                .with_scale(Vec3::new(
                    0.035,
                    0.035,
                    if dir == Vec3::ZERO { 0. } else { 1.3 },
                ));
        }
    }
}
