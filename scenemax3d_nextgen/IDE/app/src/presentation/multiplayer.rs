//! Compact retained Project Settings multiplayer form.
use crate::application::multiplayer::{Action, State};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_ui::{label, property, theme::*};
#[derive(Component)]
pub(crate) struct Field(String);
#[derive(Component)]
pub(crate) struct Status;
#[derive(Component)]
pub(crate) struct LocalStatus;
#[derive(Default, Resource)]
pub(crate) struct View {
    root: Option<Entity>,
    revision: u64,
}
pub(crate) fn smoke(
    mut state: ResMut<State>,
    services: Res<crate::application::EditorServices>,
    mut fired: Local<bool>,
    mut seeded: Local<bool>,
    mut launched: Local<bool>,
    mut queue: ResMut<crate::application::CommandQueue>,
) {
    if !*fired && !services.storage.is_pending() && !services.catalog_storage.is_pending() {
        state.actions.push_back(Action::Open);
        *fired = true;
    }
    if *fired && !*seeded && !state.busy && !state.fields.is_empty() {
        if let Ok(password) = std::env::var("SCENEMAX_SMOKE_MULTIPLAYER_PASSWORD") {
            state.password = Some(password);
            state.revision += 1;
            if std::env::var_os("SCENEMAX_SMOKE_MULTIPLAYER_SAVE_RUN").is_some() {
                state.actions.push_back(Action::Save);
            }
        }
        *seeded = true;
    }
    if *seeded
        && !*launched
        && !state.busy
        && state.status == "Multiplayer settings saved."
        && std::env::var_os("SCENEMAX_SMOKE_MULTIPLAYER_SAVE_RUN").is_some()
    {
        state.actions.push_back(Action::Close);
        queue.0.push_back(crate::application::Command::RunProject);
        *launched = true;
    }
}
pub(crate) fn menu_item(c: &mut Commands, parent: Entity, title: &str) -> Entity {
    let e = scenemax_ide_ui::button(c, parent, title, Name::new("Project Settings"));
    c.entity(e)
        .observe(|_: On<Pointer<Press>>, mut s: ResMut<State>| s.actions.push_back(Action::Open));
    e
}
fn node(c: &mut Commands, p: Entity, mut n: Node) -> Entity {
    n.flex_shrink = 0.;
    c.spawn((n, ChildOf(p))).id()
}
fn text(c: &mut Commands, p: Entity, value: &str) -> Entity {
    c.spawn((
        label(value, 12.),
        Node {
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(p),
    ))
    .id()
}
pub(crate) fn input(
    mut state: ResMut<State>,
    fields: Query<(&Field, Option<&EditableText>, Option<&property::Choice>)>,
    passwords: Query<&scenemax_ide_ui::password::Password>,
    keys: Res<ButtonInput<KeyCode>>,
) {
    if !state.open {
        return;
    }
    for (field, editable, choice) in &fields {
        let value = editable
            .map(|e| e.value().to_string())
            .or_else(|| choice.map(|c| c.0.clone()));
        if let Some(value) = value {
            state.fields.insert(field.0.clone(), value);
        }
    }
    for password in &passwords {
        if password.touched() {
            state.password = Some(password.value().to_owned());
        }
    }
    if keys.just_pressed(KeyCode::Escape) {
        state.actions.push_back(Action::Close);
    }
}
pub(crate) fn refresh(
    mut c: Commands,
    state: Res<State>,
    mut view: ResMut<View>,
    mut status: Query<&mut Text, (With<Status>, Without<LocalStatus>)>,
    mut local_status: Query<&mut Text, (With<LocalStatus>, Without<Status>)>,
) {
    if !state.open {
        if let Some(root) = view.root.take() {
            c.entity(root).despawn();
        }
        return;
    }
    if view.root.is_none() || view.revision != state.revision {
        if let Some(root) = view.root.take() {
            c.entity(root).despawn();
        }
        let root = c
            .spawn((
                Node {
                    position_type: PositionType::Absolute,
                    width: percent(100.),
                    height: percent(100.),
                    align_items: AlignItems::Center,
                    justify_content: JustifyContent::Center,
                    ..default()
                },
                BackgroundColor(Color::srgba(0., 0., 0., 0.6)),
                GlobalZIndex(190),
                bevy::input_focus::tab_navigation::TabGroup::modal(),
            ))
            .id();
        let panel = node(
            &mut c,
            root,
            Node {
                width: px(720.),
                max_height: percent(95.),
                overflow: Overflow::scroll_y(),
                max_width: percent(95.),
                padding: px(16.).all(),
                row_gap: px(10.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
        );
        c.entity(panel)
            .insert((BackgroundColor(PANEL), ScrollPosition::default()))
            .observe(
                |mut event: On<Pointer<Scroll>>,
                 mut nodes: Query<(&mut ScrollPosition, &ComputedNode)>| {
                    if let Ok((mut scroll, node)) = nodes.get_mut(event.entity) {
                        let scale = if event.unit == bevy::input::mouse::MouseScrollUnit::Line {
                            40.
                        } else {
                            1.
                        };
                        let max = (node.content_size().y - node.size().y).max(0.)
                            * node.inverse_scale_factor();
                        scroll.y = (scroll.y - event.y * scale).clamp(0., max);
                        event.propagate(false);
                    }
                },
            );
        text(&mut c, panel, "Project Settings — Multiplayer");
        text(
            &mut c,
            panel,
            "SceneMax UDP server • Session and project settings",
        );
        for (key, caption) in [
            ("server", "Server IP / hostname"),
            ("port", "UDP port"),
            ("project_guid", "Project GUID"),
            ("session_id", "Session ID"),
            ("session_name", "Session name"),
            ("platform", "Server platform"),
            ("gateway_enabled", "Browser gateway"),
            ("gateway_url", "Browser WebSocket URL"),
            ("gateway_listen", "Gateway listen IP:port"),
            ("gateway_origin", "Allowed game origin"),
        ] {
            let row = node(
                &mut c,
                panel,
                Node {
                    column_gap: px(8.),
                    align_items: AlignItems::Center,
                    height: px(28.),
                    min_height: px(28.),
                    ..default()
                },
            );
            let l = text(&mut c, row, caption);
            c.entity(l).insert(Node {
                width: px(180.),
                height: px(16.),
                flex_shrink: 0.,
                ..default()
            });
            let holder = node(
                &mut c,
                row,
                Node {
                    flex_grow: 1.,
                    min_width: px(0.),
                    ..default()
                },
            );
            let value = state.fields.get(key).map(String::as_str).unwrap_or("");
            if key == "gateway_enabled" {
                property::dropdown(
                    &mut c,
                    holder,
                    Field(key.into()),
                    value,
                    &["Disabled".into(), "Enabled".into()],
                );
            } else if key == "platform" {
                property::dropdown(
                    &mut c,
                    holder,
                    Field(key.into()),
                    value,
                    &["windows-x64".into(), "linux-x64".into(), "macos-x64".into()],
                );
            } else {
                property::input(&mut c, holder, Field(key.into()), value);
            }
        }
        text(
            &mut c,
            panel,
            "Enabled: Build Server includes the browser gateway; Run starts both. Web exports use the saved WebSocket URL. HTTPS games need a wss:// TLS reverse proxy. Origin is the game website (or hosting iframe), without a path.",
        );
        text(&mut c, panel, "Password (this IDE session only)");
        scenemax_ide_ui::password::input(
            &mut c,
            panel,
            Name::new("Multiplayer password"),
            state.password.as_deref().unwrap_or(""),
        );
        let password_actions = node(
            &mut c,
            panel,
            Node {
                column_gap: px(8.),
                ..default()
            },
        );
        let clear = scenemax_ide_ui::button(
            &mut c,
            password_actions,
            "No password",
            Name::new("No password"),
        );
        c.entity(clear)
            .observe(|_: On<Pointer<Click>>, mut state: ResMut<State>| {
                state.actions.push_back(Action::NoPassword)
            });
        let environment = scenemax_ide_ui::button(
            &mut c,
            password_actions,
            "Use environment password",
            Name::new("Use environment password"),
        );
        c.entity(environment)
            .observe(|_: On<Pointer<Click>>, mut state: ResMut<State>| {
                state.actions.push_back(Action::UseEnvironment)
            });
        text(
            &mut c,
            panel,
            "Save applies the password to future runs. Build Server uses the same password.\nPasswords are never saved to project files. Clear the field for no password.",
        );
        let local_row = node(
            &mut c,
            panel,
            Node {
                column_gap: px(8.),
                ..default()
            },
        );
        for (caption, action) in [
            ("Run Server Locally", Action::RunLocal),
            ("Stop Local Server", Action::StopLocal),
        ] {
            let e = scenemax_ide_ui::button(&mut c, local_row, caption, Name::new(caption));
            c.entity(e)
                .observe(move |_: On<Pointer<Click>>, mut s: ResMut<State>| {
                    s.actions.push_back(action)
                });
        }
        text(
            &mut c,
            panel,
            "Run saves settings and binds UDP and the enabled gateway to loopback. Both stop when the IDE closes or you change projects. Serve the browser game from the allowed origin; its URL must point to this local gateway.",
        );
        let local = text(&mut c, panel, &state.local_status);
        c.entity(local).insert(LocalStatus);
        let row = node(
            &mut c,
            panel,
            Node {
                column_gap: px(8.),
                ..default()
            },
        );
        for (caption, action) in [
            ("Save", Action::Save),
            ("Build Server", Action::Build),
            ("Cancel", Action::Cancel),
            ("Open Folder", Action::OpenFolder),
            ("Close", Action::Close),
        ] {
            let e = scenemax_ide_ui::button(&mut c, row, caption, Name::new(caption));
            c.entity(e)
                .observe(move |_: On<Pointer<Click>>, mut s: ResMut<State>| {
                    s.actions.push_back(action)
                });
        }
        let label = text(&mut c, panel, &state.status);
        c.entity(label).insert(Status);
        view.root = Some(root);
        view.revision = state.revision;
    }
    for mut text in &mut status {
        if text.0 != state.status {
            text.0 = state.status.clone();
        }
    }
    for mut text in &mut local_status {
        let value = if state.local_status.is_empty() {
            "UDP local server stopped"
        } else {
            &state.local_status
        };
        if text.0 != value {
            text.0 = value.to_owned();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn gateway_fields_retain_edits_without_recreating_the_dialog() {
        let mut app = App::new();
        app.init_resource::<State>()
            .init_resource::<View>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_systems(Update, (input, refresh).chain());
        app.world_mut().resource_mut::<State>().open = true;
        app.update();
        let world = app.world_mut();
        let field = world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, f)| f.0 == "gateway_url")
            .unwrap()
            .0;
        *world.get_mut::<EditableText>(field).unwrap() =
            EditableText::new("wss://network.example.com/play");
        app.update();
        assert_eq!(
            app.world().resource::<State>().fields["gateway_url"],
            "wss://network.example.com/play"
        );
        assert!(app.world().get::<Field>(field).is_some());
    }
    #[test]
    fn multiplayer_settings_retain_fields_and_block_editor_shortcuts() {
        let (mut app, _dir, _) = crate::tests::app();
        {
            let mut state = app.world_mut().resource_mut::<State>();
            state.open = true;
            state.revision = 1;
            state.fields.insert("server".into(), "127.0.0.1".into());
        }
        app.update();
        app.update();
        let world = app.world_mut();
        let field = world
            .query::<(Entity, &Field)>()
            .iter(world)
            .find(|(_, f)| f.0 == "server")
            .unwrap()
            .0;
        *world.get_mut::<EditableText>(field).unwrap() = EditableText::new("192.0.2.10");
        app.update();
        app.update();
        assert_eq!(
            app.world().resource::<State>().fields["server"],
            "192.0.2.10"
        );
        assert!(app.world().get::<Field>(field).is_some());
        let status = app
            .world()
            .resource::<crate::application::Session>()
            .status
            .clone();
        app.world_mut()
            .resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::F8);
        app.update();
        assert_eq!(
            app.world().resource::<crate::application::Session>().status,
            status
        );
        app.world_mut()
            .resource_mut::<State>()
            .actions
            .push_back(Action::Close);
        app.update();
        assert!(app.world().get_entity(field).is_err());
    }
}
