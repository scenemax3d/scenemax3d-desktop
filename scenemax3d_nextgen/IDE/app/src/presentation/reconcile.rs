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
            ViewChange::MaterialsChanged
            | ViewChange::CatalogChanged
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
    chrome: Res<super::chrome::ChromeState>,
) {
    let changed = changes
        .read()
        .any(|change| matches!(change, ViewChange::SearchResultsChanged));
    let (entity, node) = &mut *host;
    node.display = if chrome.search_open() && session.search_summary.is_some() {
        Display::Flex
    } else {
        Display::None
    };
    if !changed {
        return;
    }
    commands.entity(*entity).despawn_children();
    use scenemax_ide_ui::{label, theme::*};
    let header = commands
        .spawn((
            Node {
                padding: UiRect::axes(px(10.), px(4.)),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::SpaceBetween,
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(*entity),
        ))
        .id();
    commands.spawn((
        label(
            format!(
                "Project search · {}",
                session.search_summary.as_deref().unwrap_or("")
            ),
            12.,
        ),
        ChildOf(header),
    ));
    scenemax_ide_ui::button(
        &mut commands,
        header,
        "Close search",
        super::chrome::ChromeAction::Dismiss,
    );
    let list = scenemax_ide_ui::property::scroll_column(&mut commands, *entity);
    let mut previous = None;
    for hit in &session.search_hits {
        let relative = hit
            .path
            .strip_prefix(session.workspace.project().root())
            .unwrap_or(&hit.path);
        if previous != Some(&hit.path) {
            commands
                .spawn((
                    label(relative.display().to_string(), 12.),
                    Node {
                        padding: UiRect::axes(px(8.), px(4.)),
                        flex_shrink: 0.,
                        ..default()
                    },
                    ChildOf(list),
                ))
                .insert(TextColor(ACCENT));
            previous = Some(&hit.path);
        }
        let caption = format!("{}   {}", hit.line, hit.preview);
        let row = scenemax_ide_ui::button(
            &mut commands,
            list,
            &caption,
            super::input::Action::OpenAt(hit.path.clone(), hit.line),
        );
        commands.entity(row).insert(Node {
            padding: UiRect::axes(px(12.), px(3.)),
            min_height: px(24.),
            flex_shrink: 0.,
            ..default()
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::presentation::chrome::{ChromeAction, ChromeState, controls};

    #[test]
    fn closing_search_hides_results_even_when_worker_finishes_later() {
        let mut app = App::new();
        let mut session = Session::new(scenemax_ide_core::Project::new("project".into(), vec![]));
        session.search_summary = Some("1 matching line".into());
        session.search_hits.push(scenemax_ide_core::SearchHit {
            path: "project/example.code".into(),
            line: 7,
            preview: "needle".into(),
        });
        app.insert_resource(session)
            .init_resource::<ChromeState>()
            .init_resource::<ButtonInput<KeyCode>>()
            .init_resource::<ButtonInput<MouseButton>>()
            .add_message::<ViewChange>()
            .add_systems(Update, (controls, search_results).chain());
        let host = app.world_mut().spawn((SearchResults, Node::default())).id();
        app.world_mut()
            .write_message(ViewChange::SearchResultsChanged);
        {
            let mut keys = app.world_mut().resource_mut::<ButtonInput<KeyCode>>();
            keys.press(KeyCode::ControlLeft);
            keys.press(KeyCode::KeyF);
        }
        app.update();
        assert_eq!(
            app.world().get::<Node>(host).unwrap().display,
            Display::Flex
        );
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .reset_all();
        app.world_mut()
            .spawn((Button, Interaction::Pressed, ChromeAction::Dismiss));
        app.world_mut()
            .resource_mut::<ButtonInput<MouseButton>>()
            .press(MouseButton::Left);
        app.update();
        assert_eq!(
            app.world().get::<Node>(host).unwrap().display,
            Display::None
        );
        app.world_mut()
            .resource_mut::<ButtonInput<MouseButton>>()
            .reset_all();
        app.world_mut()
            .write_message(ViewChange::SearchResultsChanged);
        app.update();
        assert_eq!(
            app.world().get::<Node>(host).unwrap().display,
            Display::None
        );
    }
}
