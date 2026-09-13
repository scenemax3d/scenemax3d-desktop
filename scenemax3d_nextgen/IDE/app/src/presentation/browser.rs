//! Retained project navigator: directory expansion is view state, never domain state.
use super::components::Browser;
use crate::application::{Session, ViewChange};
use bevy::prelude::*;
use scenemax_ide_core::{Project, ProjectEntry};
use scenemax_ide_ui::{ButtonSurface, button, label, theme::*};
use std::{collections::HashSet, path::PathBuf};

#[derive(Resource, Default)]
pub(crate) struct TreeState {
    expanded: HashSet<PathBuf>,
    focus_path: Option<PathBuf>,
}
#[derive(Component)]
pub(crate) struct TreeItem {
    path: PathBuf,
    directory: bool,
}
#[derive(Component)]
pub(crate) struct Folder(PathBuf);

fn visible<'a>(project: &'a Project, tree: &TreeState, filter: &str) -> Vec<&'a ProjectEntry> {
    let needle = filter.to_lowercase();
    let mut matches = HashSet::new();
    if !needle.is_empty() {
        for entry in project.entries() {
            if entry
                .path
                .strip_prefix(project.root())
                .unwrap_or(&entry.path)
                .to_string_lossy()
                .to_lowercase()
                .contains(&needle)
            {
                matches.extend(
                    entry
                        .path
                        .ancestors()
                        .take_while(|p| *p != project.root())
                        .map(|p| p.to_owned()),
                );
            }
        }
    }
    project
        .entries()
        .iter()
        .filter(|entry| {
            if !needle.is_empty() {
                return matches.contains(&entry.path);
            }
            entry
                .path
                .ancestors()
                .skip(1)
                .take_while(|p| *p != project.root())
                .all(|p| tree.expanded.contains(p))
                && tree.expanded.contains(project.root())
        })
        .collect()
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
        state.expanded.contains(project.root()),
    );
    let mut focus_row = root_row;
    for entry in visible(project, &state, &session.filter) {
        let depth = entry
            .path
            .strip_prefix(project.root())
            .map_or(1, |p| p.components().count());
        let name = entry.path.file_name().unwrap_or_default().to_string_lossy();
        let row = tree_row(
            &mut commands,
            *host,
            entry.path.clone(),
            &name,
            depth,
            entry.is_directory,
            state.expanded.contains(&entry.path),
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
    expanded: bool,
) -> Entity {
    let tree_item = TreeItem {
        path: path.clone(),
        directory,
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
    commands.entity(row).insert(tree_item);
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
    if directory {
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
) {
    let Some(item) = focus
        .as_ref()
        .and_then(|f| f.get())
        .and_then(|e| rows.get(e).ok())
    else {
        return;
    };
    let project = session.workspace.project();
    let paths = std::iter::once(project.root())
        .chain(
            visible(project, &tree, &session.filter)
                .iter()
                .map(|e| e.path.as_path()),
        )
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
    if keys.just_pressed(KeyCode::ArrowRight) && item.directory {
        if tree.expanded.insert(item.path.clone()) {
            next = Some(item.path.clone());
        } else {
            next = paths
                .get(index + 1)
                .filter(|p| p.starts_with(&item.path))
                .cloned();
        }
    }
    if keys.just_pressed(KeyCode::ArrowLeft) {
        if tree.expanded.remove(&item.path) {
            next = Some(item.path.clone());
        } else {
            next = item
                .path
                .parent()
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
    fn file_requires_primary_double_click_to_open() {
        use bevy::ecs::system::RunSystemOnce;
        use bevy::picking::pointer::{Location, PointerId};
        let mut world = World::new();
        world.register_component::<Window>();
        world.init_resource::<crate::application::CommandQueue>();
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
                    false,
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
        }
    }
    #[test]
    fn expansion_and_filter_keep_the_hierarchy() {
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
        assert_eq!(visible(&project, &tree, "").len(), 2);
        tree.expanded.insert(root.join("scripts"));
        assert_eq!(visible(&project, &tree, "").len(), 3);
        tree.expanded.clear();
        let matches = visible(&project, &tree, "main");
        assert_eq!(matches.len(), 2);
        assert!(matches[0].is_directory);
    }
}
