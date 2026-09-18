//! Retained orientation widget and editor camera modes.
use super::*;
#[derive(Resource, Default)]
pub(crate) struct Navigation {
    pub(super) pan: bool,
}
#[derive(Component)]
pub(crate) struct Axis {
    camera: Entity,
    direction: Vec3,
}
#[derive(Component)]
pub(crate) struct Arm {
    camera: Entity,
    direction: Vec3,
}
pub(crate) fn spawn(commands: &mut Commands, parent: Entity, camera: Entity) {
    let root = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                right: px(10.),
                top: px(10.),
                width: px(104.),
                height: px(104.),
                ..default()
            },
            BackgroundColor(Color::srgba(0.08, 0.09, 0.11, 0.8)),
            ZIndex(5),
            ChildOf(parent),
        ))
        .id();
    commands.entity(root).observe(
        move |mut event: On<Pointer<Drag>>, mut cameras: Query<(&mut Orbit, &mut Transform)>| {
            event.propagate(false);
            if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                orbit.yaw -= event.delta.x * 0.012;
                orbit.pitch = (orbit.pitch + event.delta.y * 0.012).clamp(-1.56, 1.56);
                *transform = orbit.transform();
            }
        },
    );
    for (name, direction, color) in [
        ("X", Vec3::X, Color::srgb(0.95, 0.2, 0.2)),
        ("Y", Vec3::Y, Color::srgb(0.2, 0.9, 0.3)),
        ("Z", Vec3::Z, Color::srgb(0.25, 0.5, 1.)),
    ] {
        for sign in [-1., 1.] {
            let direction = direction * sign;
            let arm = scenemax_ide_ui::icons::line(
                commands,
                root,
                Vec2::splat(52.),
                Vec2::splat(52.),
                color,
            );
            commands.entity(arm).insert(Arm { camera, direction });
            let dot = commands
                .spawn((
                    Button,
                    ZIndex(1),
                    Axis { camera, direction },
                    Name::new(format!("View {}{name}", if sign > 0. { "+" } else { "−" })),
                    Node {
                        position_type: PositionType::Absolute,
                        width: px(20.),
                        height: px(20.),
                        border_radius: BorderRadius::MAX,
                        align_items: AlignItems::Center,
                        justify_content: JustifyContent::Center,
                        ..default()
                    },
                    scenemax_ide_ui::ButtonSurface(if sign > 0. {
                        color
                    } else {
                        color.with_alpha(0.45)
                    }),
                    BackgroundColor(if sign > 0. {
                        color
                    } else {
                        color.with_alpha(0.45)
                    }),
                    ChildOf(root),
                ))
                .id();
            if sign > 0. {
                commands.spawn((label(name, 11.), Pickable::IGNORE, ChildOf(dot)));
            }
            commands.entity(dot).observe(
                move |mut event: On<Pointer<Click>>,
                      mut cameras: Query<(&mut Orbit, &mut Transform)>| {
                    event.propagate(false);
                    if let Ok((mut orbit, mut transform)) = cameras.get_mut(camera) {
                        orbit.yaw = direction.x.atan2(direction.z);
                        orbit.pitch = direction.y.asin().clamp(-1.569, 1.569);
                        *transform = orbit.transform();
                    }
                },
            );
        }
    }
}
pub(crate) fn update(
    cameras: Query<&Transform, With<Orbit>>,
    mut arms: Query<(&Arm, &mut Node, &mut UiTransform), Without<Axis>>,
    mut axes: Query<(&Axis, &mut Node, &mut ZIndex), Without<Arm>>,
) {
    for (arm, mut node, mut transform) in &mut arms {
        if let Ok(camera) = cameras.get(arm.camera) {
            let p = camera.rotation.inverse() * arm.direction;
            let delta = Vec2::new(p.x, -p.y) * 34.;
            let width = px(delta.length());
            let left = px(52. + delta.x / 2. - delta.length() / 2.);
            let top = px(52. + delta.y / 2. - 0.75);
            let rotation = UiTransform::from_rotation(Rot2::radians(delta.y.atan2(delta.x)));
            if node.width != width || node.left != left || node.top != top {
                node.width = width;
                node.left = left;
                node.top = top;
            }
            if *transform != rotation {
                *transform = rotation;
            }
        }
    }
    for (axis, mut node, mut z) in &mut axes {
        if let Ok(camera) = cameras.get(axis.camera) {
            let p = camera.rotation.inverse() * axis.direction;
            let left = px(42. + p.x * 34.);
            let top = px(42. - p.y * 34.);
            if node.left != left || node.top != top {
                node.left = left;
                node.top = top;
            }
            let depth = if p.z > 0. { 3 } else { 1 };
            if z.0 != depth {
                z.0 = depth;
            }
        }
    }
}
