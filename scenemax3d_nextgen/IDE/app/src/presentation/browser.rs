//! Retained project navigator: directory expansion is view state, never domain state.
use super::components::Browser;
use crate::application::{Session, ViewChange};
use bevy::prelude::*;
use scenemax_ide_core::{Project, ProjectEntry};
use scenemax_ide_ui::{ButtonSurface, button, label, theme::*};
use std::{
    collections::{HashMap, HashSet},
    path::PathBuf,
};

#[derive(Resource, Default)]
pub(crate) struct TreeState {
    expanded: HashSet<PathBuf>,
    focus_path: Option<PathBuf>,
}
#[derive(Component)]
pub(crate) struct TreeItem {
    path: PathBuf,
    directory: bool,
    expandable: bool,
}
#[derive(Component)]
pub(crate) struct Folder(PathBuf);

// Presentation-only ownership: files retain their real paths and file actions.
fn companions(project: &Project) -> HashMap<PathBuf, PathBuf> {
    let files: HashSet<_> = project
        .entries()
        .iter()
        .filter(|e| !e.is_directory)
        .map(|e| e.path.clone())
        .collect();
    let mut owners = HashMap::new();
    for entry in project.entries().iter().filter(|e| !e.is_directory) {
        let path = &entry.path;
        let Some(stem) = path.file_stem().and_then(|s| s.to_str()) else {
            continue;
        };
        let names = match path.extension().and_then(|s| s.to_str()) {
            Some("smdesign") => vec![
                format!("{stem}.code"),
                format!("{stem}_init.code"),
                format!("{stem}_end.code"),
            ],
            Some("smui") => vec![format!("{stem}_ui.code")],
            _ => continue,
        };
        for name in names {
            let child = path.with_file_name(name);
            if files.contains(&child) {
                owners.entry(child).or_insert_with(|| path.clone());
            }
        }
    }
    owners
}

fn visible<'a>(project: &'a Project, tree: &TreeState) -> Vec<&'a ProjectEntry> {
    let owners = companions(project);
    let mut children: HashMap<&PathBuf, Vec<&ProjectEntry>> = HashMap::new();
    for entry in project.entries() {
        if let Some(owner) = owners.get(&entry.path) {
            children.entry(owner).or_default().push(entry);
        }
    }
    let mut rows = Vec::new();
    for entry in project.entries() {
        if owners.contains_key(&entry.path)
            || !tree.expanded.contains(project.root())
            || !entry
                .path
                .ancestors()
                .skip(1)
                .take_while(|p| *p != project.root())
                .all(|p| tree.expanded.contains(p))
        {
            continue;
        }
        rows.push(entry);
        if tree.expanded.contains(&entry.path)
            && let Some(nested) = children.get(&entry.path)
        {
            rows.extend(nested.iter().copied());
        }
    }
    rows
}

pub(crate) fn update_tree(
    mut commands: Commands,
    session: Res<Session>,
    mut state: ResMut<TreeState>,
    mut changes: MessageReader<ViewChange>,
    folders: Query<(&Interaction, &Folder), Changed<Interaction>>,
    host: Single<Entity, With<Browser>>,
    mut focus: Option<ResMut<bevy::input_focus::InputFocus>>,
) {
    let mut rebuild = state.is_changed();
    let mut restore_focus = rebuild && state.focus_path.is_some();
    if state.is_added() {
        state
            .expanded
            .insert(session.workspace.project().root().to_owned());
        state
            .expanded
            .insert(session.workspace.project().root().join("scripts"));
    }
    for event in changes.read() {
        match event {
            ViewChange::ProjectOpened => {
                state.expanded.clear();
                state.focus_path = None;
                state
                    .expanded
                    .insert(session.workspace.project().root().to_owned());
                state
                    .expanded
                    .insert(session.workspace.project().root().join("scripts"));
                rebuild = true;
            }
            ViewChange::ActiveChanged => {
                if let Some(id) = session.workspace.active_id()
                    && let Ok(doc) = session.workspace.document(id)
                {
                    // Tool catalogs must not expand the user's project navigator.
                    if scenemax_ide_core::animation_analyzer::is_file(doc.path()) {
                        continue;
                    }
                    if let Some(owner) = companions(session.workspace.project()).get(doc.path()) {
                        rebuild |= state.expanded.insert(owner.clone());
                    }
                    for parent in doc
                        .path()
                        .ancestors()
                        .skip(1)
                        .take_while(|p| p.starts_with(session.workspace.project().root()))
                    {
                        if !state.expanded.contains(parent) {
                            state.expanded.insert(parent.to_owned());
                            rebuild = true;
                        }
                    }
                    if state.focus_path.is_some() {
                        state.focus_path = None;
                    }
                }
            }
            ViewChange::ProjectTreeChanged => rebuild = true,
            _ => {}
        }
    }
    for (interaction, folder) in &folders {
        if *interaction == Interaction::Pressed {
            state.focus_path = Some(folder.0.clone());
            restore_focus = true;
            if !state.expanded.remove(&folder.0) {
                state.expanded.insert(folder.0.clone());
            }
            rebuild = true;
        }
    }
    if !rebuild {
        return;
    }
    commands.entity(*host).despawn_children();
    let project = session.workspace.project();
    let owners = companions(project);
    let root_name = project
        .root()
        .file_name()
        .unwrap_or_default()
        .to_string_lossy();
    let root_row = tree_row(
        &mut commands,
        *host,
        project.root().to_owned(),
        &root_name,
        0,
        true,
        Some(state.expanded.contains(project.root())),
    );
    let mut focus_row = root_row;
    for entry in visible(project, &state) {
        let depth = entry
            .path
            .strip_prefix(project.root())
            .map_or(1, |p| p.components().count())
            + usize::from(owners.contains_key(&entry.path));
        let name = entry.path.file_name().unwrap_or_default().to_string_lossy();
        let row = tree_row(
            &mut commands,
            *host,
            entry.path.clone(),
            &name,
            depth,
            entry.is_directory,
            (entry.is_directory || owners.values().any(|p| p == &entry.path))
                .then(|| state.expanded.contains(&entry.path)),
        );
        if state.focus_path.as_ref() == Some(&entry.path) {
            focus_row = row;
        }
    }
    if restore_focus && let Some(focus) = focus.as_mut() {
        focus.set(focus_row, bevy::input_focus::FocusCause::Navigated);
    }
    if project.tree_truncated() {
        commands.spawn((
            label("Tree limited to 10,000 entries / 32 levels", 11.),
            ChildOf(*host),
        ));
    }
}

fn tree_row(
    commands: &mut Commands,
    parent: Entity,
    path: PathBuf,
    name: &str,
    depth: usize,
    directory: bool,
    expansion: Option<bool>,
) -> Entity {
    let expandable = expansion.is_some();
    let expanded = expansion.unwrap_or(false);
    let tree_item = TreeItem {
        path: path.clone(),
        directory,
        expandable,
    };
    let row = if directory {
        button(commands, parent, "", Folder(path))
    } else {
        {
            let target = path.clone();
            let row = button(commands, parent, "", Name::new("Project file"));
            commands
                .entity(row)
                .insert(bevy::input_focus::tab_navigation::TabIndex(0))
                .observe(
                    move |event: On<Pointer<Click>>,
                          mut queue: ResMut<crate::application::CommandQueue>| {
                        if event.button == PointerButton::Primary && event.count == 2 {
                            queue
                                .0
                                .push_back(crate::application::Command::Open(target.clone()));
                        }
                    },
                );
            row
        }
    };
    let disclosure_path = tree_item.path.clone();
    let target = tree_item.path.clone();
    commands.entity(row).insert(tree_item).observe(
        move |mut event: On<Pointer<Click>>,
              mut menu: Option<ResMut<super::tree_menu::State>>,
              mut focus: Option<ResMut<bevy::input_focus::InputFocus>>| {
            if event.button == PointerButton::Secondary {
                event.propagate(false);
                if let Some(menu) = menu.as_mut() {
                    menu.open(target.clone(), directory, event.pointer_location.position);
                }
                if let Some(focus) = focus.as_mut() {
                    focus.set(row, bevy::input_focus::FocusCause::Navigated);
                }
            }
        },
    );
    commands.entity(row).despawn_children();
    commands.entity(row).insert((
        Node {
            width: percent(100.),
            height: px(27.),
            flex_shrink: 0.,
            align_items: AlignItems::Center,
            padding: UiRect {
                left: px(10. + depth as f32 * 16.),
                right: px(8.),
                ..default()
            },
            column_gap: px(7.),
            overflow: Overflow::clip(),
            ..default()
        },
        ButtonSurface(PANEL),
    ));
    let chevron = commands
        .spawn((
            Node {
                width: px(10.),
                height: px(12.),
                align_items: AlignItems::Center,
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(row),
        ))
        .id();
    if expandable {
        if !directory {
            commands
                .entity(chevron)
                .insert((Button, Interaction::None, Folder(disclosure_path)));
            commands
                .entity(chevron)
                .observe(|mut event: On<Pointer<Click>>| {
                    event.propagate(false);
                });
        }
        commands.spawn((
            Node {
                width: px(5.),
                height: px(5.),
                border: UiRect {
                    right: px(1.),
                    bottom: px(1.),
                    ..default()
                },
                ..default()
            },
            BorderColor::all(INK),
            UiTransform::from_rotation(Rot2::degrees(if expanded { 45. } else { -45. })),
            ChildOf(chevron),
        ));
    }
    commands.spawn((
        super::java_icons::JavaIcon(super::java_icons::file(name, directory)),
        Node {
            width: px(18.),
            height: px(18.),
            flex_shrink: 0.,
            ..default()
        },
        Pickable::IGNORE,
        ChildOf(row),
    ));
    commands.spawn((label(name, 13.), ChildOf(row)));
    row
}

type TreeRows<'w, 's> = Query<
    'w,
    's,
    (
        Entity,
        &'static TreeItem,
        &'static Interaction,
        &'static mut BackgroundColor,
        &'static mut ButtonSurface,
    ),
>;
pub(crate) fn selection(
    session: Res<Session>,
    focus: Option<Res<bevy::input_focus::InputFocus>>,
    mut rows: TreeRows,
) {
    let active = session
        .workspace
        .active_id()
        .and_then(|id| session.workspace.document(id).ok())
        .map(|d| d.path());
    let focused = focus
        .as_ref()
        .and_then(|f| f.get())
        .filter(|e| rows.get(*e).is_ok());
    for (entity, row, interaction, mut color, mut surface) in &mut rows {
        let selected = if let Some(focused) = focused {
            entity == focused
        } else {
            active == Some(row.path.as_path())
        };
        let background = if selected { SELECTED } else { PANEL };
        if surface.0 != background {
            surface.0 = background;
        }
        if *interaction == Interaction::None && color.0 != background {
            color.0 = background;
        }
    }
}

pub(crate) fn keyboard(
    keys: Res<ButtonInput<KeyCode>>,
    focus: Option<Res<bevy::input_focus::InputFocus>>,
    rows: Query<&TreeItem>,
    session: Res<Session>,
    mut tree: ResMut<TreeState>,
    mut queue: ResMut<crate::application::CommandQueue>,
    menu: Option<Res<super::tree_menu::State>>,
) {
    if menu.is_some_and(|m| m.is_open()) {
        return;
    }
    let Some(item) = focus
        .as_ref()
        .and_then(|f| f.get())
        .and_then(|e| rows.get(e).ok())
    else {
        return;
    };
    let project = session.workspace.project();
    let owners = companions(project);
    let paths = std::iter::once(project.root())
        .chain(visible(project, &tree).iter().map(|e| e.path.as_path()))
        .map(|p| p.to_owned())
        .collect::<Vec<_>>();
    let index = paths.iter().position(|p| *p == item.path).unwrap_or(0);
    let mut next = None;
    if keys.just_pressed(KeyCode::ArrowDown) {
        next = paths.get((index + 1).min(paths.len() - 1)).cloned();
    }
    if keys.just_pressed(KeyCode::ArrowUp) {
        next = paths.get(index.saturating_sub(1)).cloned();
    }
    if keys.just_pressed(KeyCode::ArrowRight) && item.expandable {
        if tree.expanded.insert(item.path.clone()) {
            next = Some(item.path.clone());
        } else {
            next = paths
                .get(index + 1)
                .filter(|p| p.starts_with(&item.path) || owners.get(*p) == Some(&item.path))
                .cloned();
        }
    }
    if keys.just_pressed(KeyCode::ArrowLeft) {
        if tree.expanded.remove(&item.path) {
            next = Some(item.path.clone());
        } else {
            next = owners
                .get(&item.path)
                .map(|p| p.as_path())
                .or_else(|| item.path.parent())
                .filter(|p| p.starts_with(project.root()))
                .map(|p| p.to_owned());
        }
    }
    if keys.just_pressed(KeyCode::Enter) {
        if item.directory {
            if !tree.expanded.remove(&item.path) {
                tree.expanded.insert(item.path.clone());
            }
            next = Some(item.path.clone());
        } else {
            queue
                .0
                .push_back(crate::application::Command::Open(item.path.clone()));
        }
    }
    if let Some(path) = next {
        tree.focus_path = Some(path);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn companion_keyboard_navigation_uses_designer_parent_and_opens_files() {
        use bevy::ecs::system::RunSystemOnce;
        use bevy::input_focus::{FocusCause, InputFocus};
        let root = PathBuf::from("project");
        let designer = root.join("scene.smdesign");
        let child = root.join("scene.code");
        let project = Project::new(root.clone(), vec![]).with_entries(
            vec![
                ProjectEntry {
                    path: designer.clone(),
                    is_directory: false,
                },
                ProjectEntry {
                    path: child.clone(),
                    is_directory: false,
                },
            ],
            false,
        );
        let mut world = World::new();
        world.insert_resource(Session::new(project));
        world.insert_resource(TreeState {
            expanded: HashSet::from([root, designer.clone()]),
            focus_path: None,
        });
        world.init_resource::<crate::application::CommandQueue>();
        world.init_resource::<ButtonInput<KeyCode>>();
        world.init_resource::<InputFocus>();
        let row = world
            .spawn(TreeItem {
                path: child.clone(),
                directory: false,
                expandable: false,
            })
            .id();
        world
            .resource_mut::<InputFocus>()
            .set(row, FocusCause::Navigated);
        world
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::ArrowLeft);
        world.run_system_once(keyboard).unwrap();
        assert_eq!(
            world.resource::<TreeState>().focus_path,
            Some(designer.clone())
        );
        world.resource_mut::<ButtonInput<KeyCode>>().reset_all();
        world.entity_mut(row).insert(TreeItem {
            path: designer.clone(),
            directory: false,
            expandable: true,
        });
        world
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::ArrowRight);
        world.run_system_once(keyboard).unwrap();
        assert_eq!(world.resource::<TreeState>().focus_path, Some(child));
        world.resource_mut::<ButtonInput<KeyCode>>().reset_all();
        world
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::Enter);
        world.run_system_once(keyboard).unwrap();
        assert!(
            matches!(world.resource::<crate::application::CommandQueue>().0.front(), Some(crate::application::Command::Open(p)) if *p == designer)
        );
    }
    #[test]
    fn designer_companions_are_nested_and_unrelated_files_stay_visible() {
        let root = PathBuf::from("project");
        let names = [
            "level.code",
            "level.smdesign",
            "level_end.code",
            "level_init.code",
            "other.code",
            "hud.smui",
            "hud_ui.code",
            "hud_init.code",
            "hud_end.code",
            "orphan_init.code",
        ];
        let project = Project::new(root.clone(), vec![]).with_entries(
            names
                .iter()
                .map(|n| ProjectEntry {
                    path: root.join(n),
                    is_directory: false,
                })
                .collect(),
            false,
        );
        let mut tree = TreeState::default();
        tree.expanded.insert(root.clone());
        let rows = |tree: &TreeState| {
            visible(&project, tree)
                .iter()
                .map(|e| e.path.file_name().unwrap().to_str().unwrap().to_owned())
                .collect::<Vec<_>>()
        };
        assert_eq!(
            rows(&tree),
            [
                "level.smdesign",
                "other.code",
                "hud.smui",
                "hud_init.code",
                "hud_end.code",
                "orphan_init.code"
            ]
        );
        tree.expanded.insert(root.join("level.smdesign"));
        assert_eq!(
            rows(&tree),
            [
                "level.smdesign",
                "level.code",
                "level_end.code",
                "level_init.code",
                "other.code",
                "hud.smui",
                "hud_init.code",
                "hud_end.code",
                "orphan_init.code"
            ]
        );
        tree.expanded.insert(root.join("hud.smui"));
        assert_eq!(rows(&tree).len(), names.len());
        assert_eq!(
            companions(&project)[&root.join("hud_ui.code")],
            root.join("hud.smui")
        );
    }
    #[test]
    fn file_requires_primary_double_click_to_open() {
        use bevy::ecs::system::RunSystemOnce;
        use bevy::picking::pointer::{Location, PointerId};
        let mut world = World::new();
        world.register_component::<Window>();
        world.init_resource::<crate::application::CommandQueue>();
        world.init_resource::<super::super::tree_menu::State>();
        let row = world
            .run_system_once(|mut commands: Commands| {
                let parent = commands.spawn(Node::default()).id();
                tree_row(
                    &mut commands,
                    parent,
                    PathBuf::from("example.smdesign"),
                    "example",
                    1,
                    false,
                    None,
                )
            })
            .unwrap();
        world.flush();
        for (button, count, expected) in [
            (PointerButton::Primary, 1, 0),
            (PointerButton::Secondary, 2, 0),
            (PointerButton::Primary, 2, 1),
        ] {
            world.trigger(Pointer::new(
                PointerId::Mouse,
                Location {
                    target: bevy::camera::NormalizedRenderTarget::None {
                        width: 100,
                        height: 100,
                    },
                    position: Vec2::ZERO,
                },
                Click {
                    button,
                    count,
                    duration: std::time::Duration::ZERO,
                    hit: bevy::picking::backend::HitData::new(Entity::PLACEHOLDER, 0., None, None),
                },
                row,
            ));
            assert_eq!(
                world.resource::<crate::application::CommandQueue>().0.len(),
                expected
            );
            if button == PointerButton::Secondary {
                assert!(world.resource::<super::super::tree_menu::State>().is_open());
            }
        }
    }
    #[test]
    fn analyzer_tab_preserves_project_tree_expansion() {
        let (mut app, dir, _) = crate::tests::app();
        app.update();
        let folder = dir.path().join("resources/Models");
        std::fs::create_dir_all(&folder).unwrap();
        let path = folder.join("models-ext.json");
        std::fs::write(&path, r#"{"models":[]}"#).unwrap();
        let before = app.world().resource::<TreeState>().expanded.clone();
        let document = {
            let session = app.world().resource::<Session>();
            scenemax_ide_services::Filesystem::open_document(session.workspace.project(), &path)
                .unwrap()
        };
        app.world_mut()
            .resource_mut::<Session>()
            .workspace
            .open_document(document)
            .unwrap();
        app.world_mut().write_message(ViewChange::ActiveChanged);
        app.update();
        assert_eq!(app.world().resource::<TreeState>().expanded, before);
    }

    #[test]
    fn expansion_keeps_the_hierarchy() {
        let root = PathBuf::from("project");
        let project = Project::new(root.clone(), vec![]).with_entries(
            vec![
                ProjectEntry {
                    path: root.join("scripts"),
                    is_directory: true,
                },
                ProjectEntry {
                    path: root.join("scripts/main"),
                    is_directory: false,
                },
                ProjectEntry {
                    path: root.join("resources"),
                    is_directory: true,
                },
            ],
            false,
        );
        let mut tree = TreeState::default();
        tree.expanded.insert(root.clone());
        assert_eq!(visible(&project, &tree).len(), 2);
        tree.expanded.insert(root.join("scripts"));
        assert_eq!(visible(&project, &tree).len(), 3);
        tree.expanded.clear();
        assert!(visible(&project, &tree).is_empty());
    }
}
