//! Client title bar: native move/resize requests, with normal IDE close handling.
use bevy::{math::CompassOctant, prelude::*};
use scenemax_ide_ui::{label, theme::*};
use scenemax_ui_input::{Button, Interaction};

#[derive(Component)]
pub(crate) struct WindowControl {
    close: bool,
}

fn control(
    commands: &mut Commands,
    parent: Entity,
    title: &str,
    close: bool,
    strokes: &[(Vec2, Vec2)],
) -> Entity {
    let entity = commands
        .spawn((
            Button,
            WindowControl { close },
            Name::new(title.to_owned()),
            scenemax_ide_ui::NoButtonFeedback,
            Node {
                width: px(40.),
                height: px(28.),
                margin: UiRect::left(px(2.)),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                flex_shrink: 0.,
                border_radius: BorderRadius::all(px(3.)),
                ..default()
            },
            BackgroundColor(HEADER),
            ChildOf(parent),
        ))
        .id();
    commands.spawn((
        Node {
            width: px(12.),
            height: px(12.),
            flex_shrink: 0.,
            ..default()
        },
        Pickable::IGNORE,
        scenemax_ide_ui::icons::drawing(
            strokes
                .iter()
                .map(|&(a, b)| vec![a * 1.25, b * 1.25])
                .collect(),
        ),
        ChildOf(entity),
    ));
    entity
}

pub(crate) fn control_feedback(
    mut controls: Query<(&Interaction, &WindowControl, &mut BackgroundColor), Changed<Interaction>>,
) {
    for (interaction, control, mut background) in &mut controls {
        background.0 = match (interaction, control.close) {
            (Interaction::Hovered, true) => Color::srgb_u8(210, 48, 58),
            (Interaction::Pressed, true) => Color::srgb_u8(166, 32, 42),
            (Interaction::Hovered, false) => Color::srgb_u8(83, 90, 99),
            (Interaction::Pressed, false) => SELECTED,
            (Interaction::None, _) => HEADER,
        };
    }
}
#[derive(Resource, Default)]
pub(crate) struct Maximized(bool);

pub(crate) fn maximize_on_startup(
    mut window: Single<&mut Window, With<bevy::window::PrimaryWindow>>,
    mut maximized: ResMut<Maximized>,
) {
    window.set_maximized(true);
    maximized.0 = true;
}
pub(crate) fn drag(commands: &mut Commands, parent: Entity) {
    let region = commands
        .spawn((
            Node {
                flex_grow: 1.,
                height: percent(100.),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    commands.spawn((
        super::projects::ProjectCaption,
        label("SceneMax Studio", 12.),
        Pickable::IGNORE,
        ChildOf(region),
    ));
    commands
        .entity(region)
        .observe(|event: On<PointerPress>, mut windows: Query<&mut Window>| {
            if event.button == PointerButton::Primary
                && let Ok(mut window) = windows.single_mut()
            {
                window.start_drag_move();
            }
        });
    commands.entity(region).observe(
        |event: On<PointerClick>,
         mut windows: Query<&mut Window>,
         mut maximized: ResMut<Maximized>| {
            if event.count == 2
                && let Ok(mut window) = windows.single_mut()
            {
                maximized.0 = !maximized.0;
                window.set_maximized(maximized.0);
            }
        },
    );
    let minimize = control(
        commands,
        parent,
        "Minimize window",
        false,
        &[(Vec2::new(2., 10.), Vec2::new(14., 10.))],
    );
    commands
        .entity(minimize)
        .observe(|_: On<PointerClick>, mut windows: Query<&mut Window>| {
            if let Ok(mut window) = windows.single_mut() {
                window.set_minimized(true);
            }
        });
    let maximize = control(
        commands,
        parent,
        "Maximize or restore window",
        false,
        &[
            (Vec2::new(2., 2.), Vec2::new(14., 2.)),
            (Vec2::new(14., 2.), Vec2::new(14., 14.)),
            (Vec2::new(14., 14.), Vec2::new(2., 14.)),
            (Vec2::new(2., 14.), Vec2::new(2., 2.)),
        ],
    );
    commands.entity(maximize).observe(
        |_: On<PointerClick>, mut windows: Query<&mut Window>, mut maximized: ResMut<Maximized>| {
            if let Ok(mut window) = windows.single_mut() {
                maximized.0 = !maximized.0;
                window.set_maximized(maximized.0);
            }
        },
    );
    let close = control(
        commands,
        parent,
        "Close window",
        true,
        &[
            (Vec2::new(3., 3.), Vec2::new(13., 13.)),
            (Vec2::new(13., 3.), Vec2::new(3., 13.)),
        ],
    );
    commands.entity(close).insert(super::input::Action::Exit);
}
pub(crate) fn resize_edges(commands: &mut Commands, root: Entity) {
    for (direction, left, top, width, height) in [
        (CompassOctant::North, px(6.), px(0.), percent(99.), px(4.)),
        (
            CompassOctant::South,
            px(6.),
            Val::Auto,
            percent(99.),
            px(4.),
        ),
        (CompassOctant::West, px(0.), px(6.), px(4.), percent(99.)),
        (CompassOctant::East, Val::Auto, px(6.), px(4.), percent(99.)),
        (CompassOctant::NorthWest, px(0.), px(0.), px(6.), px(6.)),
        (CompassOctant::NorthEast, Val::Auto, px(0.), px(6.), px(6.)),
        (CompassOctant::SouthWest, px(0.), Val::Auto, px(6.), px(6.)),
        (
            CompassOctant::SouthEast,
            Val::Auto,
            Val::Auto,
            px(6.),
            px(6.),
        ),
    ] {
        commands
            .spawn((
                Node {
                    position_type: PositionType::Absolute,
                    left,
                    top,
                    right: if left == Val::Auto { px(0.) } else { Val::Auto },
                    bottom: if top == Val::Auto { px(0.) } else { Val::Auto },
                    width,
                    height,
                    ..default()
                },
                GlobalZIndex(150),
                ChildOf(root),
            ))
            .observe(
                move |_: On<PointerPress>, mut windows: Query<&mut Window>| {
                    if let Ok(mut window) = windows.single_mut() {
                        window.start_drag_resize(direction);
                    }
                },
            );
    }
}

#[derive(Component)]
pub(crate) struct AppIcon;
pub(crate) fn load_icon(
    mut commands: Commands,
    icons: Query<Entity, Added<AppIcon>>,
    server: Option<Res<AssetServer>>,
) {
    let Some(server) = server else {
        return;
    };
    for entity in &icons {
        commands.entity(entity).insert(ImageNode::new(
            server.load("embedded://scenemax_ide/presentation/scenemax_icon.png"),
        ));
    }
}
