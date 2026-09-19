//! Compact widget tools and a retained collapsible inspector.
use super::*;
#[derive(Component)]
pub(crate) struct PropertiesPanel;
pub(super) fn toolbar(commands: &mut Commands, parent: Entity, host: Entity) {
    commands.spawn((label("Add:", 12.), ChildOf(parent)));
    for (title, kind, paths) in [
        (
            "Add panel",
            "PANEL",
            vec![
                vec![(2., 3.), (18., 3.), (18., 17.), (2., 17.), (2., 3.)],
                vec![(2., 7.), (18., 7.)],
            ],
        ),
        (
            "Add text",
            "TEXT_VIEW",
            vec![
                vec![(3., 4.), (17., 4.)],
                vec![(10., 4.), (10., 17.)],
                vec![(7., 17.), (13., 17.)],
            ],
        ),
        (
            "Add button",
            "BUTTON",
            vec![
                vec![
                    (4., 5.),
                    (16., 5.),
                    (18., 7.),
                    (18., 13.),
                    (16., 15.),
                    (4., 15.),
                    (2., 13.),
                    (2., 7.),
                    (4., 5.),
                ],
                vec![(7., 10.), (13., 10.)],
            ],
        ),
        (
            "Add image",
            "IMAGE",
            vec![
                vec![(2., 3.), (18., 3.), (18., 17.), (2., 17.), (2., 3.)],
                vec![(3., 15.), (8., 9.), (12., 13.), (15., 10.), (18., 14.)],
                vec![(13., 6.), (14., 6.)],
            ],
        ),
        (
            "Add editable text",
            "EDIT_TEXT",
            vec![
                vec![(2., 5.), (18., 5.), (18., 15.), (2., 15.), (2., 5.)],
                vec![(6., 8.), (11., 8.)],
                vec![(13., 3.), (15., 3.)],
                vec![(14., 3.), (14., 17.)],
                vec![(13., 17.), (15., 17.)],
            ],
        ),
        (
            "Add list view",
            "LIST_VIEW",
            vec![
                vec![(2., 3.), (18., 3.), (18., 17.), (2., 17.), (2., 3.)],
                vec![(2., 7.), (18., 7.)],
                vec![(2., 12.), (18., 12.)],
                vec![(7., 3.), (7., 17.)],
            ],
        ),
    ] {
        let paths: Vec<Vec<Vec2>> = paths
            .into_iter()
            .map(|p| p.into_iter().map(|(x, y)| Vec2::new(x, y)).collect())
            .collect();
        scenemax_ide_ui::icons::button(
            commands,
            parent,
            title,
            &paths,
            Structure {
                host,
                kind: Some(kind),
            },
        );
    }
    scenemax_ide_ui::icons::image_button(
        commands,
        parent,
        "Delete selected widget",
        crate::presentation::java_icons::JavaIcon("toolbar_delete"),
        Structure { host, kind: None },
    );
}
/// Document actions share the canvas navigation row, aligned to its right edge.
pub(super) fn document_actions(commands: &mut Commands, parent: Entity) {
    commands.spawn((
        Node {
            flex_grow: 1.,
            ..default()
        },
        ChildOf(parent),
    ));
    let save = vec![
        vec![
            (3., 2.),
            (15., 2.),
            (18., 5.),
            (18., 18.),
            (3., 18.),
            (3., 2.),
        ],
        vec![(6., 2.), (6., 8.), (14., 8.), (14., 2.)],
        vec![(6., 18.), (6., 12.), (15., 12.), (15., 18.)],
    ];
    let undo = vec![
        vec![(7., 3.), (2., 8.), (7., 13.)],
        vec![(2., 8.), (12., 8.), (15., 9.), (17., 12.), (17., 16.)],
    ];
    let redo = undo
        .iter()
        .map(|path| path.iter().map(|&(x, y)| (20. - x, y)).collect())
        .collect();
    for (title, action, paths) in [
        ("Save (Ctrl+S)", Action::Save, save),
        ("Undo (Ctrl+Z)", Action::Undo, undo),
        ("Redo (Ctrl+Y)", Action::Redo, redo),
    ] {
        let paths: Vec<Vec<Vec2>> = paths
            .into_iter()
            .map(|path| path.into_iter().map(|(x, y)| Vec2::new(x, y)).collect())
            .collect();
        scenemax_ide_ui::icons::button(commands, parent, title, &paths, action);
    }
}
pub(super) fn collapse(commands: &mut Commands, toggle: Entity, panel: Entity, host: Entity) {
    commands.entity(toggle).observe(
        move |event: On<Pointer<Click>>,
              mut states: Query<&mut Designer>,
              mut nodes: Query<&mut Node>,
              children: Query<&Children>,
              mut texts: Query<&mut Text>| {
            if event.button != PointerButton::Primary {
                return;
            }
            if let (Ok(mut state), Ok(mut node)) = (states.get_mut(host), nodes.get_mut(panel)) {
                state.properties_collapsed = !state.properties_collapsed;
                node.display = if state.properties_collapsed {
                    Display::None
                } else {
                    Display::Flex
                };
                if let Ok(children) = children.get(toggle) {
                    for child in children.iter() {
                        if let Ok(mut text) = texts.get_mut(child) {
                            text.0 = if state.properties_collapsed {
                                "‹"
                            } else {
                                "›"
                            }
                            .into();
                        }
                    }
                }
            }
        },
    );
}
