//! Retained UI hierarchy: disclosure changes visibility, never document entities.
use super::*;
use scenemax_ide_ui::tree::{self, TreeAppearance, TreeRow};
use std::collections::HashSet;
#[derive(Component)]
pub(crate) struct Row {
    pub host: Entity,
    pub key: String,
    ancestors: Vec<String>,
}
#[derive(Component)]
pub(super) struct WidgetCaption {
    pub host: Entity,
    pub pointer: String,
}
#[derive(Component)]
struct Toggle;
pub(crate) type Rows<'w, 's> =
    Query<'w, 's, (&'static Row, &'static mut Node, &'static mut TreeAppearance)>;
pub(crate) fn reveal(collapsed: &mut HashSet<String>, pointer: &str) {
    for p in ancestors(pointer) {
        collapsed.remove(&p);
    }
}
fn ancestors(pointer: &str) -> Vec<String> {
    let mut result = Vec::new();
    if let Some((layer, _)) = pointer.split_once("/widgets/") {
        result.push(layer.into());
    }
    let mut p = pointer;
    while let Some((parent, _)) = p.rsplit_once("/children/") {
        result.push(parent.into());
        p = parent;
    }
    result
}
pub(crate) fn synchronize(host: Entity, state: &Designer, rows: &mut Rows) {
    for (row, mut node, mut appearance) in rows.iter_mut().filter(|(r, _, _)| r.host == host) {
        let display = if row.ancestors.iter().any(|p| state.collapsed.contains(p)) {
            Display::None
        } else {
            Display::Flex
        };
        if node.display != display {
            node.display = display;
        }
        let next = TreeAppearance {
            selected: state.selected.as_deref() == Some(&row.key),
            expanded: appearance
                .expanded
                .map(|_| !state.collapsed.contains(&row.key)),
        };
        if *appearance != next {
            *appearance = next;
        }
    }
}
pub(super) fn build(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    scene: &ScenePreview,
    selected: Option<&str>,
    collapsed: &HashSet<String>,
) {
    let frame = area(
        commands,
        parent,
        Node {
            width: px(230.),
            min_width: px(160.),
            height: percent(100.),
            min_height: px(0.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            ..default()
        },
        PANEL,
    );
    commands.spawn((
        label("UI HIERARCHY", 11.),
        Node {
            padding: px(8.).all(),
            ..default()
        },
        ChildOf(frame),
    ));
    let content = scenemax_ide_ui::property::scroll_column(commands, frame);
    let mut layers = HashSet::new();
    for w in &scene.widgets {
        let layer = w.pointer.split("/widgets/").next().unwrap_or("");
        if layers.insert(layer.to_owned()) {
            spawn(
                commands, content, host, layer, &w.layer, "▱", 0, true, false, None, collapsed,
            );
        }
        spawn(
            commands,
            content,
            host,
            &w.pointer,
            &w.name,
            match w.kind.as_str() {
                "PANEL" => "▱",
                "TEXT_VIEW" => "T",
                "EDIT_TEXT" => "I",
                "IMAGE" => "I",
                "BUTTON" => "B",
                "LIST_VIEW" => "≡",
                _ => "□",
            },
            w.depth + 1,
            !w.definition.children.is_empty(),
            !w.visible,
            selected,
            collapsed,
        );
    }
}
#[allow(clippy::too_many_arguments)]
fn spawn(
    commands: &mut Commands,
    parent: Entity,
    host: Entity,
    key: &str,
    caption: &str,
    icon: &str,
    depth: usize,
    branch: bool,
    muted: bool,
    selected: Option<&str>,
    collapsed: &HashSet<String>,
) {
    // Layer captions toggle their branch; widgets retain an independent selection target.
    let row = tree::row(
        commands,
        parent,
        TreeRow {
            caption,
            icon,
            depth,
            expanded: branch.then(|| !collapsed.contains(key)),
            selected: selected == Some(key),
            muted,
        },
        Pick {
            host,
            pointer: key.into(),
        },
        Toggle,
    );
    let lineage = ancestors(key);
    if lineage.iter().any(|p| collapsed.contains(p)) {
        commands.entity(row).insert(Node {
            display: Display::None,
            width: percent(100.),
            height: px(24.),
            min_height: px(24.),
            flex_shrink: 0.,
            padding: UiRect::left(px(6. + depth as f32 * 16.)),
            align_items: AlignItems::Center,
            overflow: Overflow::clip(),
            ..default()
        });
    }
    commands.entity(row).insert(Row {
        host,
        key: key.into(),
        ancestors: lineage,
    });
    let key = key.to_owned();
    commands.queue(move |world: &mut World| {
        let captions: Vec<_> = world
            .get::<Children>(row)
            .into_iter()
            .flat_map(|c| c.iter())
            .flat_map(|child| {
                world
                    .get::<Children>(child)
                    .into_iter()
                    .flat_map(|c| c.iter())
            })
            .filter(|child| {
                world
                    .get::<scenemax_ide_ui::tree::Caption>(*child)
                    .is_some()
            })
            .collect();
        for caption in captions {
            world.entity_mut(caption).insert(WidgetCaption {
                host,
                pointer: key.clone(),
            });
        }
        let targets: Vec<_> = world
            .get::<Children>(row)
            .into_iter()
            .flat_map(|c| c.iter())
            .filter(|child| {
                world.get::<Toggle>(*child).is_some()
                    || (!key.contains("/widgets/") && world.get::<Pick>(*child).is_some())
            })
            .collect();
        for target in targets {
            let key = key.clone();
            world.entity_mut(target).observe(
                move |mut event: On<Pointer<Click>>, mut designers: Query<&mut Designer>| {
                    if event.button != PointerButton::Primary {
                        return;
                    }
                    event.propagate(false);
                    if let Ok(mut state) = designers.get_mut(host)
                        && !state.collapsed.remove(&key)
                    {
                        state.collapsed.insert(key.clone());
                    }
                },
            );
        }
    });
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn revealing_a_leaf_opens_only_its_ancestors() {
        let mut collapsed = HashSet::from([
            "/layers/0".into(),
            "/layers/0/widgets/0".into(),
            "/layers/0/widgets/1".into(),
        ]);
        reveal(&mut collapsed, "/layers/0/widgets/0/children/2");
        assert_eq!(collapsed, HashSet::from(["/layers/0/widgets/1".into()]));
    }
    fn click(world: &mut World, target: Entity) {
        use bevy::picking::{
            backend::HitData,
            pointer::{Location, PointerId},
        };
        world.trigger(Pointer::new(
            PointerId::Mouse,
            Location {
                target: bevy::camera::NormalizedRenderTarget::None {
                    width: 800,
                    height: 600,
                },
                position: Vec2::ZERO,
            },
            Click {
                count: 1,
                button: PointerButton::Primary,
                hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
                duration: std::time::Duration::from_millis(10),
            },
            target,
        ));
        world.flush();
    }
    #[test]
    fn disclosure_and_inspector_toggle_preserve_retained_entities_and_drafts() {
        use bevy::ecs::system::RunSystemOnce;
        let mut world = World::new();
        let host = world.spawn((Node::default(), Designer::default())).id();
        let scene=scenemax_ide_services::scene::preview(r#"{"name":"ui","layers":[{"name":"HUD","widgets":[{"name":"panel","type":"PANEL","children":[{"name":"caption","type":"TEXT_VIEW"}]}]}]}"#).unwrap();
        build(
            &mut world.commands(),
            host,
            host,
            &scene,
            None,
            &HashSet::new(),
        );
        world.flush();
        let branch = world
            .query::<(Entity, &Row)>()
            .iter(&world)
            .find(|(_, r)| r.key == "/layers/0/widgets/0")
            .unwrap()
            .0;
        let leaf = world
            .query::<(Entity, &Row)>()
            .iter(&world)
            .find(|(_, r)| r.key.ends_with("/children/0"))
            .unwrap()
            .0;
        let disclosure = world.get::<Children>(branch).unwrap()[0];
        click(&mut world, disclosure);
        world
            .run_system_once(move |states: Query<&Designer>, mut rows: Rows| {
                synchronize(host, states.get(host).unwrap(), &mut rows);
            })
            .unwrap();
        assert_eq!(world.get::<Node>(leaf).unwrap().display, Display::None);
        click(&mut world, disclosure);
        world
            .run_system_once(move |states: Query<&Designer>, mut rows: Rows| {
                synchronize(host, states.get(host).unwrap(), &mut rows);
            })
            .unwrap();
        assert_eq!(world.get::<Node>(leaf).unwrap().display, Display::Flex);
        let panel = world.spawn(Node::default()).id();
        let input = world
            .spawn((EditableText::new("unsaved value"), ChildOf(panel)))
            .id();
        let toggle = button(&mut world.commands(), host, "›", Name::new("Toggle"));
        super::super::chrome::collapse(&mut world.commands(), toggle, panel, host);
        world.flush();
        click(&mut world, toggle);
        assert_eq!(world.get::<Node>(panel).unwrap().display, Display::None);
        assert!(world.get::<Designer>(host).unwrap().properties_collapsed);
        click(&mut world, toggle);
        assert_eq!(world.get::<Node>(panel).unwrap().display, Display::Flex);
        assert_eq!(
            world
                .get::<EditableText>(input)
                .unwrap()
                .value()
                .to_string(),
            "unsaved value"
        );
        assert!(world.get::<Row>(leaf).is_some());
    }
}
