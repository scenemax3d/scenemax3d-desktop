use super::{
    components::*,
    tabs::{editor, tab},
};
use crate::application::{Session, ViewChange};
use bevy::prelude::*;

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct ViewHosts<'w, 's> {
    editors: Single<'w, 's, Entity, With<Editors>>,
    tabs: Single<'w, 's, Entity, With<Tabs>>,
}
pub(crate) fn reconcile(
    mut commands: Commands,
    session: Res<Session>,
    mut changes: MessageReader<ViewChange>,
    mut editors: Query<(Entity, &EditorHost, &mut Node)>,
    tab_buttons: Query<(Entity, &TabButton)>,
    hosts: ViewHosts,
) {
    let mut selection_changed = false;
    for change in changes.read() {
        match change {
            ViewChange::CatalogChanged
            | ViewChange::BufferChanged(_)
            | ViewChange::SearchResultsChanged => {}
            ViewChange::DocumentClosed(id) => {
                for (entity, host, _) in &editors {
                    if host.0 == *id {
                        commands.entity(entity).despawn();
                    }
                }
                for (entity, tab) in &tab_buttons {
                    if tab.0 == *id {
                        commands.entity(entity).despawn();
                    }
                }
            }
            ViewChange::DocumentOpened(id) => {
                if let Ok(doc) = session.workspace.document(*id) {
                    editor(
                        &mut commands,
                        *hosts.editors,
                        *id,
                        doc,
                        session.workspace.active_id() == Some(*id),
                    );
                    tab(&mut commands, *hosts.tabs, *id, doc);
                }
            }
            ViewChange::ProjectOpened => {
                commands.entity(*hosts.editors).despawn_children();
                commands.entity(*hosts.tabs).despawn_children();
            }
            ViewChange::ProjectTreeChanged | ViewChange::ProjectIndexInvalidated => {}
            ViewChange::ActiveChanged => selection_changed = true,
        }
    }
    if selection_changed {
        for (_, editor, mut node) in &mut editors {
            let display = if session.workspace.active_id() == Some(editor.0) {
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

pub(crate) fn search_results(
    mut commands: Commands,
    session: Res<Session>,
    mut changes: MessageReader<ViewChange>,
    mut host: Single<(Entity, &mut Node), With<SearchResults>>,
) {
    if !changes
        .read()
        .any(|change| matches!(change, ViewChange::SearchResultsChanged))
    {
        return;
    }
    let (entity, node) = &mut *host;
    node.display = if session.search_hits.is_empty() {
        Display::None
    } else {
        Display::Flex
    };
    commands.entity(*entity).despawn_children();
    for hit in &session.search_hits {
        let relative = hit
            .path
            .strip_prefix(session.workspace.project().root())
            .unwrap_or(&hit.path);
        let caption = format!(
            "{}:{}\n{}",
            relative.display(),
            hit.line,
            hit.preview.chars().take(32).collect::<String>()
        );
        scenemax_ide_ui::button(
            &mut commands,
            *entity,
            &caption,
            super::input::Action::OpenAt(hit.path.clone(), hit.line),
        );
    }
}
