//! Offscreen rendering of measured solver poses; no approximate animation path.
use super::*;
use crate::presentation::scene3d::Orbit;
use bevy::{
    asset::RenderAssetUsages,
    camera::{RenderTarget, visibility::RenderLayers},
    render::render_resource::{Extent3d, TextureDimension, TextureFormat, TextureUsages},
    ui::widget::ViewportNode,
};
const LAYER: usize = 13;
#[derive(Component)]
struct CameraTag;
#[derive(Component)]
struct BodyTag;
#[derive(Default)]
pub(super) struct Preview {
    root: Option<Entity>,
    body: Option<Entity>,
    camera: Option<Entity>,
    geometry: Option<Entity>,
    trail: Option<Entity>,
    revision: Option<u64>,
    sphere: Handle<Mesh>,
    cube: Handle<Mesh>,
    blue: Handle<StandardMaterial>,
    red: Handle<StandardMaterial>,
    green: Handle<StandardMaterial>,
    grey: Handle<StandardMaterial>,
    grid: Handle<StandardMaterial>,
    last: Vec3,
    samples: usize,
}
impl Preview {
    pub fn clear(&mut self, c: &mut Commands) {
        if let Some(root) = self.root.take() {
            c.entity(root).try_despawn();
        }
        *self = default();
    }
}
fn object(
    c: &mut Commands,
    p: Entity,
    mesh: Handle<Mesh>,
    material: Handle<StandardMaterial>,
    transform: Transform,
) -> Entity {
    c.spawn((
        Mesh3d(mesh),
        MeshMaterial3d(material),
        transform,
        RenderLayers::layer(LAYER),
        ChildOf(p),
    ))
    .id()
}
type Transforms<'w, 's> = Query<
    'w,
    's,
    (&'static mut Transform, Option<&'static mut Orbit>),
    Or<(With<CameraTag>, With<BodyTag>)>,
>;
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Render<'w, 's> {
    images: Option<ResMut<'w, Assets<Image>>>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    transforms: Transforms<'w, 's>,
}
pub(crate) fn update(mut c: Commands, mut s: ResMut<State>, mut r: Render) {
    let (Some(images), Some(meshes), Some(materials)) =
        (r.images.as_mut(), r.meshes.as_mut(), r.materials.as_mut())
    else {
        return;
    };
    let Some(parts) = &s.parts else { return };
    let viewport = parts.viewport;
    if s.render.root.is_none() {
        let root = c.spawn((Transform::default(), Visibility::default())).id();
        s.render.root = Some(root);
        s.render.sphere = meshes.add(Sphere::new(0.5).mesh().uv(20, 16));
        s.render.cube = meshes.add(Cuboid::new(1., 1., 1.));
        let colors = [
            Color::srgb(0.18, 0.6, 0.95),
            Color::srgb(0.95, 0.25, 0.18),
            Color::srgb(0.2, 0.9, 0.5),
            Color::srgb(0.22, 0.24, 0.27),
        ];
        let mats = colors.map(|base_color| {
            materials.add(StandardMaterial {
                base_color,
                perceptual_roughness: 0.6,
                ..default()
            })
        });
        s.render.blue = mats[0].clone();
        s.render.red = mats[1].clone();
        s.render.green = materials.add(StandardMaterial {
            base_color: Color::srgb(0.25, 1., 0.55),
            unlit: true,
            ..default()
        });
        s.render.grid = materials.add(StandardMaterial {
            base_color: Color::srgb(0.33, 0.36, 0.4),
            unlit: true,
            ..default()
        });
        s.render.grey = mats[3].clone();
        let mut image = Image::new_uninit(
            Extent3d {
                width: 1000,
                height: 700,
                depth_or_array_layers: 1,
            },
            TextureDimension::D2,
            TextureFormat::Bgra8UnormSrgb,
            RenderAssetUsages::all(),
        );
        image.texture_descriptor.usage = TextureUsages::TEXTURE_BINDING
            | TextureUsages::RENDER_ATTACHMENT
            | TextureUsages::COPY_DST
            | TextureUsages::COPY_SRC;
        let orbit = Orbit {
            target: Vec3::new(0., 3., 6.),
            distance: 32.,
            yaw: -2.5,
            pitch: 0.5,
        };
        let camera = c
            .spawn((
                CameraTag,
                Camera3d::default(),
                Camera {
                    order: -13,
                    clear_color: ClearColorConfig::Custom(Color::srgb(0.075, 0.085, 0.10)),
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
        c.spawn((
            PointLight {
                intensity: 12_000_000.,
                range: 150.,
                shadow_maps_enabled: true,
                ..default()
            },
            Transform::from_xyz(8., 20., -5.),
            RenderLayers::layer(LAYER),
            ChildOf(root),
        ));
        c.spawn((
            PointLight {
                intensity: 3_000_000.,
                range: 100.,
                ..default()
            },
            Transform::from_xyz(-10., 12., 18.),
            RenderLayers::layer(LAYER),
            ChildOf(root),
        ));
        c.entity(viewport).observe(
            |mut e: On<Pointer<Drag>>,
             mut q: Query<(&mut Orbit, &mut Transform), With<CameraTag>>| {
                e.propagate(false);
                for (mut o, mut t) in &mut q {
                    o.yaw -= e.delta.x * 0.006;
                    o.pitch = (o.pitch + e.delta.y * 0.006).clamp(0.05, 1.5);
                    *t = o.transform();
                }
            },
        );
        c.entity(viewport).observe(
            |mut e: On<Pointer<Scroll>>,
             mut q: Query<(&mut Orbit, &mut Transform), With<CameraTag>>| {
                e.propagate(false);
                let d = if e.unit == bevy::input::mouse::MouseScrollUnit::Line {
                    e.y * 0.12
                } else {
                    e.y * 0.001
                };
                for (mut o, mut t) in &mut q {
                    o.distance = (o.distance * (1. + d)).clamp(2., 2000.);
                    *t = o.transform();
                }
            },
        );
    }
    if s.render.revision != Some(s.revision) {
        if let Some(g) = s.render.geometry.take() {
            c.entity(g).try_despawn();
        }
        let Some(root) = s.render.root else { return };
        let g = c
            .spawn((Transform::default(), Visibility::default(), ChildOf(root)))
            .id();
        s.render.geometry = Some(g);
        object(
            &mut c,
            g,
            s.render.cube.clone(),
            s.render.grey.clone(),
            Transform::from_xyz(0., -0.5, 0.).with_scale(Vec3::new(80., 1., 80.)),
        );
        for i in -20..=20 {
            for axis in 0..2 {
                let mut position = Vec3::new(i as f32 * 2., 0.012, 0.);
                let mut scale = Vec3::new(0.015, 0.012, 80.);
                if axis == 1 {
                    position = Vec3::new(0., 0.012, i as f32 * 2.);
                    scale = Vec3::new(80., 0.012, 0.015);
                }
                object(
                    &mut c,
                    g,
                    s.render.cube.clone(),
                    s.render.grid.clone(),
                    Transform::from_translation(position).with_scale(scale),
                );
            }
        }
        let mesh = if s.settings.shape == "Sphere" {
            s.render.sphere.clone()
        } else {
            s.render.cube.clone()
        };
        let body = object(
            &mut c,
            g,
            mesh,
            s.render.blue.clone(),
            Transform::from_translation(Vec3::from_array(s.settings.position)),
        );
        c.entity(body).insert(BodyTag);
        // Asymmetric accent makes angular motion visible even on the sphere.
        object(
            &mut c,
            body,
            s.render.cube.clone(),
            s.render.red.clone(),
            Transform::from_xyz(0., 0.51, 0.).with_scale(Vec3::new(0.12, 0.025, 0.8)),
        );
        object(
            &mut c,
            g,
            s.render.sphere.clone(),
            s.render.red.clone(),
            Transform::from_translation(Vec3::from_array(s.settings.destination))
                .with_scale(Vec3::splat(0.4)),
        );
        s.render.body = Some(body);
        s.render.trail = Some(g);
        s.render.revision = Some(s.revision);
        s.render.last = s.snapshot.position;
        s.render.samples = 0;
    }
    if let Some(body) = s.render.body
        && let Ok((mut t, _)) = r.transforms.get_mut(body)
    {
        t.translation = s.snapshot.position;
        t.rotation = s.snapshot.rotation;
    }
    if s.trail
        && s.render.samples < 1200
        && s.snapshot.position.distance(s.render.last) > 0.12
        && let Some(parent) = s.render.trail
    {
        let from = s.render.last;
        let to = s.snapshot.position;
        object(
            &mut c,
            parent,
            s.render.cube.clone(),
            s.render.green.clone(),
            Transform::from_translation((from + to) * 0.5)
                .with_rotation(Quat::from_rotation_arc(Vec3::Z, (to - from).normalize()))
                .with_scale(Vec3::new(0.055, 0.055, from.distance(to))),
        );
        s.render.last = s.snapshot.position;
        s.render.samples += 1;
    }
    if s.frame
        && let Some(camera) = s.render.camera
        && let Ok((mut t, Some(mut o))) = r.transforms.get_mut(camera)
    {
        let a = Vec3::from_array(s.settings.position);
        let b = Vec3::from_array(s.settings.destination);
        o.target = (a + b) * 0.5 + Vec3::Y * 2.;
        o.distance = (a.distance(b) * 1.8 + 12.).max(20.);
        *t = o.transform();
        s.frame = false;
    }
}
