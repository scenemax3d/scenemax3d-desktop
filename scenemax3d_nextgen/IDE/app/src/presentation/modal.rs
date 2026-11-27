//! Shared, non-scrolling modal heading. Callers attach their cancellation action.
use bevy::prelude::*;

pub(crate) fn header(c: &mut Commands, parent: Entity, title: impl Into<String>) -> Entity {
    let header = c
        .spawn((
            Node {
                width: percent(100.),
                min_height: px(30.),
                flex_shrink: 0.,
                align_items: AlignItems::Center,
                justify_content: JustifyContent::SpaceBetween,
                column_gap: px(12.),
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    c.spawn((
        scenemax_ide_ui::label(title, 18.),
        Node {
            min_width: px(0.),
            flex_grow: 1.,
            ..default()
        },
        ChildOf(header),
    ));
    header
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::application::{Session, deployment::Deployment, git};

    fn escape(app: &mut App) {
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .reset_all();
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::Escape);
        app.update();
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .reset_all();
        app.update();
    }

    #[test]
    fn escape_cancels_busy_git_without_confirming_an_operation() {
        let (mut app, _dir, _) = crate::tests::app();
        {
            let mut state = app.world_mut().resource_mut::<git::State>();
            state.open = true;
            state.busy = true;
        }
        escape(&mut app);
        assert!(!app.world().resource::<git::State>().open);
    }

    #[test]
    fn escape_dismisses_only_the_release_result_then_closes_busy_parent() {
        let (mut app, _dir, _) = crate::tests::app();
        {
            let mut state = app.world_mut().resource_mut::<Deployment>();
            state.open = true;
            state.notification = Some((false, "Test failure".into()));
        }
        escape(&mut app);
        let state = app.world().resource::<Deployment>();
        assert!(state.open);
        assert!(state.notification.is_none());
        app.world_mut().resource_mut::<Deployment>().busy = true;
        escape(&mut app);
        assert!(!app.world().resource::<Deployment>().open);
    }

    #[test]
    fn escape_cancels_exit_instead_of_discarding_documents() {
        let (mut app, _dir, _) = crate::tests::app();
        app.world_mut().resource_mut::<Session>().closing = true;
        escape(&mut app);
        assert!(!app.world().resource::<Session>().closing);
    }

    #[test]
    fn escape_closes_inventory_even_while_it_is_scanning() {
        let (mut app, _dir, _) = crate::tests::app();
        app.world_mut()
            .resource_mut::<super::super::inventory::State>()
            .actions
            .push(super::super::inventory::Action::Open);
        app.update();
        escape(&mut app);
        assert!(
            !app.world()
                .resource::<super::super::inventory::State>()
                .open
        );
    }
}
