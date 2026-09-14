//! Project selection views backed by the read-only compatibility catalog.
use super::{chrome::ChromeState, components::Field, input::Action};
use crate::application::{Session, ViewChange};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_ui::{button, label, theme::*};
#[derive(Component)]
pub(crate) struct ProjectChoices {
    compact: bool,
}
#[derive(Component)]
pub(crate) struct ProjectCaption;

pub(crate) fn choices(commands: &mut Commands, parent: Entity, compact: bool) {
    commands.spawn((ProjectChoices {compact},Node { flex_direction:FlexDirection::Column, min_height:px(0.), max_height:px(if compact {360.} else {500.}), flex_grow:if compact {0.} else {1.}, overflow:Overflow::scroll_y(), ..default() },ChildOf(parent)))
        .observe(|event: On<Pointer<Scroll>>, mut nodes: Query<(&mut ScrollPosition,&ComputedNode),With<ProjectChoices>>| {
            if let Ok((mut scroll,node)) = nodes.get_mut(event.entity) {
                let scale = if event.unit == bevy::input::mouse::MouseScrollUnit::Line {40.} else {1.};
                scroll.y = (scroll.y - event.y * scale).clamp(0.,((node.content_size().y-node.size().y)*node.inverse_scale_factor()).max(0.));
            }
        });
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct ProjectViews<'w, 's> {
    hosts: Query<'w, 's, (Entity, &'static ProjectChoices)>,
    paths: Query<'w, 's, (&'static Field, &'static mut EditableText)>,
    captions: Query<'w, 's, &'static mut Text, With<ProjectCaption>>,
}
pub(crate) fn refresh(
    mut commands: Commands,
    session: Res<Session>,
    mut changes: MessageReader<ViewChange>,
    mut views: ProjectViews,
    mut filter: Local<String>,
    mut chrome: ResMut<ChromeState>,
) {
    let query = views
        .paths
        .iter()
        .find(|(field, _)| **field == Field::ProjectFilter)
        .map(|(_, input)| input.value().to_string().to_lowercase())
        .unwrap_or_default();
    let mut rebuild = *filter != query;
    if rebuild {
        *filter = query;
    }

    for event in changes.read() {
        match event {
            ViewChange::ProjectOpened => {
                rebuild = true;
                chrome.close_project_picker();
                for (field, mut input) in &mut views.paths {
                    match field {
                        Field::Project => input
                            .editor_mut()
                            .set_text(&display_path(session.workspace.project().root())),
                        Field::Filter => input.editor_mut().set_text(""),
                        _ => {}
                    }
                }
            }
            ViewChange::CatalogChanged => rebuild = true,
            _ => {}
        }
    }
    if !rebuild {
        return;
    }
    let project = session.workspace.project();
    let active = session
        .catalog
        .projects
        .iter()
        .find(|p| p.root == project.root());
    let name = active.map_or_else(
        || {
            project
                .root()
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .into_owned()
        },
        |p| p.name.clone(),
    );
    for mut caption in &mut views.captions {
        caption.0 = format!("SceneMax Studio · {name}");
    }
    for (host, view) in &views.hosts {
        commands.entity(host).despawn_children();
        if session.catalog.projects.is_empty() {
            commands.spawn((
                label("No catalog projects. Enter a project folder below.", 12.),
                ChildOf(host),
            ));
        }
        for project in &session.catalog.projects {
            if !view.compact && !project.name.to_lowercase().contains(filter.as_str()) {
                continue;
            }
            let caption = if view.compact {
                project.name.clone()
            } else {
                format!(
                    "{}
{}",
                    project.name,
                    display_path(&project.root)
                )
            };
            let row = button(
                &mut commands,
                host,
                &caption,
                Action::ChooseProject(project.root.clone()),
            );
            commands.entity(row).insert(Node {
                padding: UiRect::axes(px(10.), px(8.)),
                flex_shrink: 0.,
                ..default()
            });
            if project.root == session.workspace.project().root() {
                commands.entity(row).insert((
                    scenemax_ide_ui::ButtonSurface(SELECTED),
                    BackgroundColor(SELECTED),
                ));
            }
        }
    }
}
pub(crate) fn display_path(path: &std::path::Path) -> String {
    let path = path.to_string_lossy();
    path.strip_prefix(r"\\?\").unwrap_or(&path).to_owned()
}
