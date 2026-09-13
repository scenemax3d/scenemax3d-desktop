//! Resolution-independent toolbar strokes with retained hover labels.
use crate::{ButtonSurface, label, theme::*};
use bevy::prelude::*;

#[derive(Component)]
pub(crate) struct Tip(Entity);
/// Draw a line in a retained UI coordinate system.
pub fn line(commands: &mut Commands, parent: Entity, a: Vec2, b: Vec2, color: Color) -> Entity {
    let delta = b - a;
    commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                left: px((a.x + b.x) / 2. - delta.length() / 2.),
                top: px((a.y + b.y) / 2. - 0.75),
                width: px(delta.length()),
                height: px(1.5),
                ..default()
            },
            UiTransform::from_rotation(Rot2::radians(delta.y.atan2(delta.x))),
            BackgroundColor(color),
            Pickable::IGNORE,
            ChildOf(parent),
        ))
        .id()
}
/// Spawn a compact icon control. Points use a 20 by 20 coordinate system.
pub fn button<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    title: &str,
    paths: &[Vec<Vec2>],
    action: T,
) -> Entity {
    let root = commands
        .spawn((
            Button,
            action,
            Name::new(title.to_owned()),
            Node {
                width: px(24.),
                height: px(24.),
                flex_shrink: 0.,
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                ..default()
            },
            BackgroundColor(PANEL),
            ButtonSurface(PANEL),
            ChildOf(parent),
        ))
        .id();
    let drawing = commands
        .spawn((
            Node {
                width: px(16.),
                height: px(16.),
                ..default()
            },
            Pickable::IGNORE,
            ChildOf(root),
        ))
        .id();
    commands.entity(drawing).insert(Icon(paths.to_vec()));
    let tip = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                top: px(32.),
                left: px(0.),
                padding: px(6.).all(),
                display: Display::None,
                ..default()
            },
            BackgroundColor(BG),
            GlobalZIndex(100),
            Pickable::IGNORE,
            ChildOf(root),
        ))
        .id();
    commands.spawn((
        label(title, 12.),
        TextLayout::no_wrap(),
        Pickable::IGNORE,
        ChildOf(tip),
    ));
    commands.entity(root).insert(Tip(tip));
    root
}
pub(crate) fn hover(
    buttons: Query<(&Interaction, &Tip), Changed<Interaction>>,
    mut nodes: Query<&mut Node>,
) {
    for (interaction, tip) in &buttons {
        if let Ok(mut node) = nodes.get_mut(tip.0) {
            let display = if *interaction == Interaction::Hovered {
                Display::Flex
            } else {
                Display::None
            };
            if node.display != display {
                node.display = display;
            }
        }
    }
}

#[derive(Component)]
pub(crate) struct Icon(Vec<Vec<Vec2>>);
// Coverage is evaluated at four times the logical size; round caps and bilinear
// sampling keep thin strokes smooth at fractional Windows display scales.
pub(crate) fn rasterize(
    mut commands: Commands,
    icons: Query<(Entity, &Icon), Added<Icon>>,
    images: Option<ResMut<Assets<Image>>>,
) {
    let Some(mut images) = images else {
        return;
    };
    for (entity, icon) in &icons {
        let mut data = vec![0u8; 80 * 80 * 4];
        for y in 0..80 {
            for x in 0..80 {
                let p = Vec2::new((x as f32 + 0.5) / 4., (y as f32 + 0.5) / 4.);
                let distance = icon
                    .0
                    .iter()
                    .flat_map(|path| path.windows(2))
                    .map(|v| {
                        let d = v[1] - v[0];
                        let t = ((p - v[0]).dot(d) / d.length_squared().max(0.0001)).clamp(0., 1.);
                        p.distance(v[0] + d * t)
                    })
                    .fold(f32::INFINITY, f32::min);
                let i = (y * 80 + x) * 4;
                data[i] = 207;
                data[i + 1] = 211;
                data[i + 2] = 219;
                data[i + 3] = ((0.78 - distance) * 4. + 0.5)
                    .clamp(0., 1.)
                    .mul_add(255., 0.) as u8;
            }
        }
        let mut image = Image::new(
            bevy::render::render_resource::Extent3d {
                width: 80,
                height: 80,
                depth_or_array_layers: 1,
            },
            bevy::render::render_resource::TextureDimension::D2,
            data,
            bevy::render::render_resource::TextureFormat::Rgba8UnormSrgb,
            bevy::asset::RenderAssetUsages::all(),
        );
        image.sampler = bevy::image::ImageSampler::linear();
        commands
            .entity(entity)
            .insert(ImageNode::new(images.add(image)));
    }
}

/// Use bundled artwork with the same retained button behavior and tooltip.
pub fn image_button<T: Component, I: Component>(
    commands: &mut Commands,
    parent: Entity,
    title: &str,
    icon: I,
    action: T,
) -> Entity {
    let root = button(commands, parent, title, &[], action);
    commands.queue(move |world: &mut World| {
        if let Some(drawing) = world
            .get::<Children>(root)
            .and_then(|children| children.first().copied())
        {
            world.entity_mut(drawing).remove::<Icon>().insert(icon);
        }
    });
    root
}
