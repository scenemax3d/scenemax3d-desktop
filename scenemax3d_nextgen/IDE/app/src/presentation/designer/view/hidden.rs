//! Editor-only runtime visibility indicator; never changes the saved widget definition.
use super::*;
pub(super) fn node(hidden: bool) -> Node {
    Node {
        position_type: PositionType::Absolute,
        width: percent(100.),
        height: percent(100.),
        left: px(0.),
        top: px(0.),
        display: if hidden { Display::Flex } else { Display::None },
        ..default()
    }
}
pub(super) fn frame(commands: &mut Commands, parent: Entity, hidden: bool) -> Entity {
    let frame = commands
        .spawn((
            node(hidden),
            Name::new("Hidden at runtime"),
            ZIndex(i32::MAX),
            Pickable::IGNORE,
            ChildOf(parent),
        ))
        .id();
    // Percentage spacing follows the widget while one-pixel strokes stay crisp at any zoom.
    for i in 0..8 {
        for far in [false, true] {
            let mut horizontal = Node {
                position_type: PositionType::Absolute,
                left: percent(i as f32 * 12.5),
                width: percent(7.5),
                height: px(1.),
                ..default()
            };
            let mut vertical = Node {
                position_type: PositionType::Absolute,
                top: percent(i as f32 * 12.5),
                height: percent(7.5),
                width: px(1.),
                ..default()
            };
            if far {
                horizontal.bottom = px(0.);
                vertical.right = px(0.);
            } else {
                horizontal.top = px(0.);
                vertical.left = px(0.);
            }
            for edge in [horizontal, vertical] {
                commands.spawn((
                    edge,
                    BackgroundColor(Color::srgb_u8(220, 175, 95)),
                    Pickable::IGNORE,
                    ChildOf(frame),
                ));
            }
        }
    }
    frame
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn hidden_layer_and_parent_children_keep_layout_and_pick_targets() {
        let scene = scenemax_ide_services::scene::preview(r#"{"name":"test","canvasWidth":800,"canvasHeight":600,"layers":[{"name":"hidden","visible":false,"widgets":[{"name":"panel","type":"PANEL","width":300,"height":200,"children":[{"name":"text","type":"TEXT_VIEW","text":"Hidden message","width":120,"height":30}]}]}]}"#).unwrap();
        assert_eq!(scene.widgets.len(), 2);
        let mut world = World::new();
        let canvas = world.spawn_empty().id();
        for w in &scene.widgets {
            assert!(!w.visible);
            let parts = draw_widget(
                &mut world.commands(),
                canvas,
                canvas,
                &scene,
                w,
                None,
                None,
                None,
            );
            world.flush();
            assert_eq!(
                world.get::<Node>(parts.entity).unwrap().display,
                Display::Flex
            );
            assert!(world.get::<Pick>(parts.entity).is_some());
            assert_eq!(
                world.get::<Node>(parts.hidden_frame).unwrap().display,
                Display::Flex
            );
            assert!(world.get::<Pickable>(parts.hidden_frame).is_some());
        }
    }
}
