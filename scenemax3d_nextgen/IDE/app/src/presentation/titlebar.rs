//! Client title bar: native move/resize requests, with normal IDE close handling.
use bevy::{math::CompassOctant, prelude::*};
use scenemax_ide_ui::{button, label};
#[derive(Resource, Default)]
pub(crate) struct Maximized(bool);
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
    commands.entity(region).observe(
        |event: On<Pointer<Press>>, mut windows: Query<&mut Window>| {
            if event.button == PointerButton::Primary
                && let Ok(mut window) = windows.single_mut()
            {
                window.start_drag_move();
            }
        },
    );
    commands.entity(region).observe(
        |event: On<Pointer<Click>>,
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
    let minimize = button(commands, parent, "−", Name::new("Minimize window"));
    commands
        .entity(minimize)
        .observe(|_: On<Pointer<Click>>, mut windows: Query<&mut Window>| {
            if let Ok(mut window) = windows.single_mut() {
                window.set_minimized(true);
            }
        });
    let maximize = button(
        commands,
        parent,
        "□",
        Name::new("Maximize or restore window"),
    );
    commands.entity(maximize).observe(
        |_: On<Pointer<Click>>,
         mut windows: Query<&mut Window>,
         mut maximized: ResMut<Maximized>| {
            if let Ok(mut window) = windows.single_mut() {
                maximized.0 = !maximized.0;
                window.set_maximized(maximized.0);
            }
        },
    );
    let close = button(commands, parent, "×", super::input::Action::Exit);
    for control in [minimize, maximize, close] {
        commands.entity(control).insert((
            scenemax_ide_ui::ButtonSurface(scenemax_ide_ui::theme::HEADER),
            BackgroundColor(scenemax_ide_ui::theme::HEADER),
        ));
    }
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
                move |_: On<Pointer<Press>>, mut windows: Query<&mut Window>| {
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
