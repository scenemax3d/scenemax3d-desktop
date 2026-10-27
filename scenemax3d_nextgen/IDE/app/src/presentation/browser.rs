//! Retained project navigator: directory expansion is view state, never domain state.
use super::components::Browser;
use crate::application::{Session, ViewChange};
use bevy::prelude::*;
use scenemax_ide_core::{Project, ProjectEntry};
use scenemax_ide_ui::{ButtonSurface, button, label, theme::*};
use scenemax_ui_input::{Button, Interaction};
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

#[derive(Component)]
pub(crate) struct Disclosure {
    path: PathBuf,
    expanded: bool,
}
#[derive(Component)]
pub(crate) struct TreeLimit;

type ExistingRows<'w, 's> = Query<'w, 's, (Entity, &'static TreeItem, &'static mut Node)>;

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
                format!("{stem}-init.code"),
                format!("{stem}-end.code"),
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
    let scripts = project.root().join("scripts");
    // Earlier Assets-menu creations live outside scripts. Keep those scenes
    // discoverable without moving users' files or changing their references.
    let scene_folders: Vec<_> = project
        .entries()
        .iter()
        .filter(|entry| {
            !entry.is_directory && entry.path.extension().is_some_and(|ext| ext == "smdesign")
        })
        .filter_map(|entry| entry.path.parent())
        .filter(|parent| *parent != project.root() && !parent.starts_with(&scripts))
        .collect();
    let owners = companions(project);
    let mut children: HashMap<&PathBuf, Vec<&ProjectEntry>> = HashMap::new();
    for entry in project.entries() {
        if let Some(owner) = owners.get(&entry.path) {
            children.entry(owner).or_default().push(entry);
        }
    }
    let mut rows = Vec::new();
    for entry in project.entries() {
        let in_scripts = entry.path.starts_with(&scripts);
        let external_scene = scene_folders
            .iter()
            .any(|folder| entry.path.starts_with(folder) || folder.starts_with(&entry.path));
        let navigation_root = if in_scripts {
            scripts.as_path()
        } else {
            project.root()
        };
        if entry.path == scripts
            || entry.path == project.root()
            || (!in_scripts && !external_scene)
            || owners.contains_key(&entry.path)
            || !entry
                .path
                .ancestors()
                .skip(1)
                .take_while(|p| *p != navigation_root)
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
    host: Single<(Entity, Option<&Children>), With<Browser>>,
    mut rows: ExistingRows,
    mut disclosures: Query<(&mut Disclosure, &mut UiTransform)>,
    limits: Query<Entity, With<TreeLimit>>,
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
                    if doc.path().extension().is_some_and(|ext| ext == "smdesign") {
                        rebuild |= state.expanded.insert(doc.path().to_owned());
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
    let (host, children) = *host;
    // Keep row entities (and their text, icons, interactions and observers) alive
    // while their paths remain visible. Expansion changes only the affected rows.
    let mut existing: HashMap<_, _> = rows
        .iter()
        .map(|(entity, item, _)| (item.path.clone(), entity))
        .collect();
    let mut order = Vec::new();
    let project = session.workspace.project();
    let owners = companions(project);
    let scripts = project.root().join("scripts");
    let mut focus_row = None;
    for entry in visible(project, &state) {
        let depth = entry
            .path
            .strip_prefix(if entry.path.starts_with(&scripts) {
                &scripts
            } else {
                project.root()
            })
            .map_or(0, |p| p.components().count().saturating_sub(1))
            + usize::from(owners.contains_key(&entry.path));
        let name = entry.path.file_name().unwrap_or_default().to_string_lossy();
        let expandable = entry.is_directory || owners.values().any(|p| p == &entry.path);
        let retained = existing.remove(&entry.path).filter(|entity| {
            let Ok((_, item, _)) = rows.get(*entity) else {
                return false;
            };
            if item.directory == entry.is_directory && item.expandable == expandable {
                true
            } else {
                commands.entity(*entity).despawn();
                false
            }
        });
        let row = if let Some(entity) = retained {
            if let Ok((_, _, mut node)) = rows.get_mut(entity) {
                let left = px(10. + depth as f32 * 16.);
                if node.padding.left != left {
                    node.padding.left = left;
                }
            }
            entity
        } else {
            tree_row(
                &mut commands,
                host,
                entry.path.clone(),
                &name,
                depth,
                entry.is_directory,
                expandable.then(|| state.expanded.contains(&entry.path)),
            )
        };
        order.push(row);
        if state.focus_path.as_ref() == Some(&entry.path) {
            focus_row = Some(row);
        }
    }
    for entity in existing.into_values() {
        commands.entity(entity).despawn();
    }
    for (mut disclosure, mut transform) in &mut disclosures {
        let expanded = state.expanded.contains(&disclosure.path);
        if disclosure.expanded != expanded {
            disclosure.expanded = expanded;
            *transform =
                UiTransform::from_rotation(Rot2::degrees(if expanded { 45. } else { -45. }));
        }
    }
    if restore_focus
        && let Some(focus) = focus.as_mut()
        && let Some(focus_row) = focus_row
    {
        focus.set(focus_row, bevy::input_focus::FocusCause::Navigated);
    }
    if project.tree_truncated() {
        let limit = limits.iter().next().unwrap_or_else(|| {
            commands
                .spawn((
                    TreeLimit,
                    label("Tree limited to 10,000 entries / 32 levels", 11.),
                    ChildOf(host),
                ))
                .id()
        });
        order.push(limit);
    } else {
        for entity in &limits {
            commands.entity(entity).despawn();
        }
    }
    // Reorder only when the visible paths change, never for selection/focus alone.
    if children.is_none_or(|children| children.iter().ne(order.iter().copied())) {
        commands.entity(host).replace_children(&order);
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
                    move |event: On<PointerClick>,
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
        move |mut event: On<PointerClick>,
              mut menu: Option<ResMut<super::tree_menu::State>>,
              mut focus: Option<ResMut<bevy::input_focus::InputFocus>>| {
            if event.button == PointerButton::Secondary {
                event.propagate(false);
                if let Some(menu) = menu.as_mut() {
                    menu.open(target.clone(), directory, event.pointer.position);
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
            commands.entity(chevron).insert((
                Button,
                Interaction::None,
                Folder(disclosure_path.clone()),
            ));
            commands
                .entity(chevron)
                .observe(|mut event: On<PointerClick>| {
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
            Disclosure {
                path: disclosure_path,
                expanded,
            },
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
    if menu.is_some_and(|m| m.is_open())
        || ![
            KeyCode::ArrowDown,
            KeyCode::ArrowUp,
            KeyCode::ArrowRight,
            KeyCode::ArrowLeft,
            KeyCode::Enter,
        ]
        .into_iter()
        .any(|key| keys.just_pressed(key))
    {
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
    let paths = visible(project, &tree)
        .iter()
        .map(|e| e.path.as_path())
        .map(|p| p.to_owned())
        .collect::<Vec<_>>();
    let Some(index) = paths.iter().position(|p| *p == item.path) else {
        return;
    };
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
                .filter(|p| paths.iter().any(|path| path == p))
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
    fn expansion_reuses_unaffected_rows_and_preserves_focus_and_scroll() {
        use bevy::input_focus::{FocusCause, InputFocus};
        let root = PathBuf::from("project");
        let folder = root.join("scripts/section");
        let designer = folder.join("scene.smdesign");
        let companion = folder.join("scene-init.code");
        let sibling = root.join("scripts/main");
        let entries = vec![
            ProjectEntry {
                path: folder.clone(),
                is_directory: true,
            },
            ProjectEntry {
                path: designer.clone(),
                is_directory: false,
            },
            ProjectEntry {
                path: companion.clone(),
                is_directory: false,
            },
            ProjectEntry {
                path: sibling.clone(),
                is_directory: false,
            },
        ];
        let mut app = App::new();
        app.insert_resource(Session::new(
            Project::new(root, vec![]).with_entries(entries, false),
        ))
        .init_resource::<TreeState>()
        .init_resource::<InputFocus>()
        .add_message::<ViewChange>()
        .add_systems(Update, update_tree);
        let host = app
            .world_mut()
            .spawn((Browser, Node::default(), ScrollPosition(Vec2::new(0., 40.))))
            .id();
        app.update();
        let snapshot = |world: &mut World| -> HashMap<PathBuf, (Entity, Vec<Entity>)> {
            world
                .query::<(Entity, &TreeItem, &Children)>()
                .iter(world)
                .map(|(e, row, children)| (row.path.clone(), (e, children.iter().collect())))
                .collect()
        };
        let initial = snapshot(app.world_mut());
        let folder_row = initial[&folder].0;
        app.world_mut()
            .resource_mut::<InputFocus>()
            .set(folder_row, FocusCause::Navigated);
        app.world_mut()
            .entity_mut(folder_row)
            .insert(Interaction::Pressed);
        app.update();
        let expanded = snapshot(app.world_mut());
        assert_eq!(expanded[&folder], initial[&folder]);
        assert_eq!(expanded[&sibling], initial[&sibling]);
        assert!(expanded.contains_key(&designer));
        assert!(!expanded.contains_key(&companion));
        assert_eq!(app.world().resource::<InputFocus>().get(), Some(folder_row));
        assert_eq!(app.world().get::<ScrollPosition>(host).unwrap().y, 40.);
        // A no-op filesystem refresh and keyboard focus move retain every child.
        let hierarchy_tick = app
            .world()
            .entity(host)
            .get_ref::<Children>()
            .unwrap()
            .last_changed();
        app.world_mut()
            .write_message(ViewChange::ProjectTreeChanged);
        app.update();
        app.world_mut().resource_mut::<TreeState>().focus_path = Some(sibling.clone());
        app.update();
        assert_eq!(snapshot(app.world_mut()), expanded);
        assert_eq!(
            app.world()
                .entity(host)
                .get_ref::<Children>()
                .unwrap()
                .last_changed(),
            hierarchy_tick
        );
        // Designer companions use the same incremental path as real directories.
        let disclosure = app
            .world_mut()
            .query::<(Entity, &Folder)>()
            .iter(app.world())
            .find(|(_, item)| item.0 == designer)
            .unwrap()
            .0;
        app.world_mut()
            .entity_mut(disclosure)
            .insert(Interaction::Pressed);
        app.update();
        let with_companion = snapshot(app.world_mut());
        assert!(with_companion.contains_key(&companion));
        assert_eq!(with_companion[&designer], expanded[&designer]);
        app.world_mut()
            .entity_mut(folder_row)
            .insert(Interaction::None);
        app.update();
        app.world_mut()
            .entity_mut(folder_row)
            .insert(Interaction::Pressed);
        app.update();
        assert_eq!(snapshot(app.world_mut()), initial);
        assert!(app.world().get_entity(expanded[&designer].0).is_err());
        assert!(
            app.world()
                .get_entity(with_companion[&companion].0)
                .is_err()
        );
        assert_eq!(app.world().resource::<InputFocus>().get(), Some(folder_row));
        assert_eq!(app.world().get::<ScrollPosition>(host).unwrap().y, 40.);
        let arrow = app
            .world_mut()
            .query::<(&Disclosure, &UiTransform)>()
            .iter(app.world())
            .find(|(d, _)| d.path == folder)
            .unwrap();
        assert!(!arrow.0.expanded);
        assert_eq!(*arrow.1, UiTransform::from_rotation(Rot2::degrees(-45.)));
    }

    #[test]
    fn added_scene_folder_and_companions_appear_after_worker_refresh() {
        use crate::application::{Command, CommandQueue, EditorServices};
        use scenemax_ide_services::TreeOperation;
        for relative_parent in ["scripts", ""] {
            let (mut app, _dir, _) = crate::tests::app();
            let root = app
                .world()
                .resource::<Session>()
                .workspace
                .project()
                .root()
                .to_owned();
            let parent = root.join(relative_parent);
            app.world_mut()
                .resource_mut::<CommandQueue>()
                .0
                .push_back(Command::Tree(TreeOperation::AddScene {
                    parent: parent.clone(),
                    name: "Opening".into(),
                }));
            app.update();
            let deadline = std::time::Instant::now() + std::time::Duration::from_secs(15);
            while app
                .world()
                .resource::<EditorServices>()
                .storage
                .is_pending()
            {
                assert!(
                    std::time::Instant::now() < deadline,
                    "Scene worker timed out"
                );
                std::thread::sleep(std::time::Duration::from_millis(1));
                app.update();
            }
            app.update();
            let scene = parent.join("Opening");
            let session = app.world().resource::<Session>();
            let active = session
                .workspace
                .document(session.workspace.active_id().unwrap())
                .unwrap();
            assert_eq!(active.path(), scene.join("Opening.smdesign"));
            let world = app.world_mut();
            let rows: HashSet<_> = world
                .query::<&TreeItem>()
                .iter(world)
                .map(|row| row.path.clone())
                .collect();
            assert!(rows.contains(&scene), "Missing scene folder: {scene:?}");
            for name in [
                "Opening.smdesign",
                "Opening-init.code",
                "Opening-end.code",
                "Opening.code",
                "main",
            ] {
                let path = scene.join(name);
                assert!(path.is_file(), "Missing file: {path:?}");
                assert!(rows.contains(&path), "Missing tree row: {path:?}");
            }
        }
    }
    #[test]
    fn companion_keyboard_navigation_uses_designer_parent_and_opens_files() {
        use bevy::ecs::system::RunSystemOnce;
        use bevy::input_focus::{FocusCause, InputFocus};
        let root = PathBuf::from("project");
        let designer = root.join("scripts/scene.smdesign");
        let child = root.join("scripts/scene.code");
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
                    path: root.join("scripts").join(n),
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
        tree.expanded.insert(root.join("scripts/level.smdesign"));
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
        tree.expanded.insert(root.join("scripts/hud.smui"));
        assert_eq!(rows(&tree).len(), names.len());
        assert_eq!(
            companions(&project)[&root.join("scripts/hud_ui.code")],
            root.join("scripts/hud.smui")
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
            world.trigger(PointerClick {
                pointer: Pointer::new(
                    PointerId::Mouse,
                    Location {
                        target: bevy::camera::NormalizedRenderTarget::None {
                            width: 100,
                            height: 100,
                        },
                        position: Vec2::ZERO,
                    },
                ),
                entity: row,
                button,
                count,
                duration: std::time::Duration::ZERO,
                hit: bevy::picking::backend::HitData::new(Entity::PLACEHOLDER, 0., None, None),
            });
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
                    path: root.join("scripts/level"),
                    is_directory: true,
                },
                ProjectEntry {
                    path: root.join("scripts/level/main"),
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
        let paths = |tree: &TreeState| {
            visible(&project, tree)
                .iter()
                .map(|e| e.path.clone())
                .collect::<Vec<_>>()
        };
        assert_eq!(
            paths(&tree),
            vec![root.join("scripts/main"), root.join("scripts/level")]
        );
        tree.expanded.insert(root.join("scripts/level"));
        assert_eq!(
            paths(&tree),
            vec![
                root.join("scripts/main"),
                root.join("scripts/level"),
                root.join("scripts/level/main")
            ]
        );
        tree.expanded.clear();
        assert_eq!(paths(&tree).len(), 2);
    }
}
