//! Tabbed Settings dialog with asynchronous build prerequisite setup.
use crate::application::build_configuration::{Action, State};
use bevy::{
    input_focus::{InputFocus, tab_navigation::TabGroup},
    prelude::*,
};
use scenemax_ide_services::build_configuration::Operation;
use scenemax_ide_ui::{button, label, theme::*};
#[derive(Resource, Default)]
pub(crate) struct View {
    host: Option<Entity>,
}
#[derive(Component)]
pub(crate) struct Summary;
#[derive(Component)]
pub(crate) struct Logs;
#[derive(Component)]
pub(crate) struct Status;
#[derive(Component)]
pub(crate) struct Progress;
#[derive(Component)]
pub(crate) struct SetupButton;
#[derive(Component)]
pub(crate) struct LogViewport;
fn text(c: &mut Commands, parent: Entity, value: &str, size: f32) -> Entity {
    c.spawn((label(value, size), ChildOf(parent))).id()
}
fn area(c: &mut Commands, parent: Entity, node: Node, color: Color) -> Entity {
    let scrolls = node.overflow.y == OverflowAxis::Scroll;
    let entity = c
        .spawn((node, BackgroundColor(color), ChildOf(parent)))
        .id();
    if scrolls {
        c.entity(entity).observe(
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
    }
    entity
}
fn action(c: &mut Commands, parent: Entity, title: &str, action: Action) -> Entity {
    let e = button(c, parent, title, Name::new(title.to_owned()));
    c.entity(e).observe(
        move |mut event: On<Pointer<Click>>, mut state: ResMut<State>| {
            if event.button == PointerButton::Primary {
                event.propagate(false);
                if !state.report.busy || matches!(action, Action::Close) {
                    state.actions.push_back(action);
                }
            }
        },
    );
    e
}
pub(crate) fn menu_item(c: &mut Commands, parent: Entity, title: &str) -> Entity {
    let e = button(c, parent, title, Name::new("Settings"));
    c.entity(e)
        .observe(|mut event: On<Pointer<Press>>, mut state: ResMut<State>| {
            if event.button == PointerButton::Primary {
                event.propagate(false);
                state.actions.push_back(Action::Open);
            }
        });
    e
}
#[allow(clippy::too_many_arguments, clippy::type_complexity)]
pub(crate) fn refresh(
    mut c: Commands,
    mut view: ResMut<View>,
    mut state: ResMut<State>,
    keys: Res<ButtonInput<KeyCode>>,
    mut focus: ResMut<InputFocus>,
    time: Res<Time>,
    mut texts: Query<(&mut Text, Option<&Summary>, Option<&Status>, Option<&Logs>)>,
    mut bars: Query<&mut Node, With<Progress>>,
    mut buttons: Query<&mut BackgroundColor, With<SetupButton>>,
    mut scroll: Query<(&mut ScrollPosition, &ComputedNode), With<LogViewport>>,
) {
    if state.open && keys.just_pressed(KeyCode::Escape) {
        state.actions.push_back(Action::Close);
    }
    if !state.open {
        if let Some(host) = view.host.take() {
            c.entity(host).despawn();
            focus.clear();
        }
        return;
    }
    if view.host.is_none() {
        focus.clear();
        let host = c
            .spawn((
                Node {
                    position_type: PositionType::Absolute,
                    width: percent(100.),
                    height: percent(100.),
                    align_items: AlignItems::Center,
                    justify_content: JustifyContent::Center,
                    ..default()
                },
                BackgroundColor(Color::srgba(0., 0., 0., 0.65)),
                GlobalZIndex(400),
                TabGroup::modal(),
            ))
            .id();
        view.host = Some(host);
        let panel = area(
            &mut c,
            host,
            Node {
                width: px(1000.),
                height: px(760.),
                max_width: percent(95.),
                max_height: percent(94.),
                padding: UiRect::all(px(20.)),
                row_gap: px(12.),
                flex_direction: FlexDirection::Column,
                overflow: Overflow::scroll_y(),
                ..default()
            },
            PANEL,
        );
        text(&mut c, panel, "Settings", 23.);
        let tabs = area(
            &mut c,
            panel,
            Node {
                height: px(36.),
                ..default()
            },
            BG,
        );
        let tab = button(
            &mut c,
            tabs,
            "Build Configuration",
            Name::new("Build Configuration tab"),
        );
        c.entity(tab)
            .insert((BackgroundColor(SELECTED), scenemax_ide_ui::NoButtonFeedback));
        text(&mut c, panel, "Desktop and Web build tools", 18.);
        text(
            &mut c,
            panel,
            "First Web build? Install the missing tools below in order. Automatic setup supports Windows x64. Detected paths are saved for exports.",
            13.,
        );
        let summary = text(&mut c, panel, "Searching this computer…", 13.);
        c.entity(summary).insert(Summary);
        let actions = area(
            &mut c,
            panel,
            Node {
                column_gap: px(10.),
                flex_wrap: FlexWrap::Wrap,
                ..default()
            },
            PANEL,
        );
        for (title, op) in [
            ("Refresh detection", Operation::Scan),
            ("Install / repair Rust", Operation::Rust),
            ("Install C++ / SDK / CMake", Operation::Cpp),
            ("Install Web tools", Operation::Web),
        ] {
            let e = action(&mut c, actions, title, Action::Run(op));
            c.entity(e).insert(SetupButton);
        }
        text(
            &mut c,
            panel,
            "1. Install / repair Rust.  2. Install C++ / SDK / CMake.  3. Install Web tools.\nWeb tools adds wasm32-unknown-unknown and the exact wasm-bindgen version required by this IDE.\nThe Windows SDK and compiler build host tools; no separate browser or Effekseer SDK is needed.\nInternet and several GB of free disk space are needed for setup and the first build. Microsoft Setup may request administrator access.\nKeep the IDE running until setup finishes. After a requested reboot, reopen Settings and Refresh detection.",
            12.,
        );
        text(
            &mut c,
            panel,
            "Web export also requires the matching SceneMax source workspace in a writable folder. A standalone IDE executable is insufficient.\nIf sources are missing, obtain the matching full source distribution and set SCENEMAX_BUILD_WORKSPACE to its scenemax3d_nextgen folder before launching the IDE.\nThen use Tools > Package & Deploy > Web > Check requirements. See IDE/WEB_EXPORT.md for setup, preview, publishing and troubleshooting.",
            12.,
        );
        let status = text(&mut c, panel, "", 13.);
        c.entity(status).insert(Status);
        let track = area(
            &mut c,
            panel,
            Node {
                height: px(8.),
                min_height: px(8.),
                width: percent(100.),
                overflow: Overflow::clip(),
                ..default()
            },
            BG,
        );
        let bar = area(
            &mut c,
            track,
            Node {
                height: percent(100.),
                width: percent(0.),
                ..default()
            },
            Color::srgb(0.25, 0.6, 0.9),
        );
        c.entity(bar).insert(Progress);
        let logbox = area(
            &mut c,
            panel,
            Node {
                flex_grow: 1.,
                min_height: px(60.),
                width: percent(100.),
                overflow: Overflow::scroll_y(),
                padding: UiRect::all(px(10.)),
                ..default()
            },
            BG,
        );
        let logs = text(&mut c, logbox, "", 12.);
        c.entity(logbox).insert(LogViewport);
        c.entity(logs).insert(Logs);
        action(&mut c, panel, "Close", Action::Close);
    }
    let r = &state.report;
    for (mut value, summary, status, logs) in &mut texts {
        let replacement = if summary.is_some() {
            Some(if r.packages.is_empty() {
                "Searching this computer…".into()
            } else {
                r.packages
                    .iter()
                    .map(|p| {
                        format!(
                            "{}  {}\n{}",
                            if p.installed { "Installed" } else { "Missing" },
                            p.name,
                            p.detail
                        )
                    })
                    .collect::<Vec<_>>()
                    .join("\n\n")
            })
        } else if status.is_some() {
            Some(r.status.clone())
        } else if logs.is_some() {
            Some(
                r.log
                    .lines()
                    .rev()
                    .take(70)
                    .collect::<Vec<_>>()
                    .into_iter()
                    .rev()
                    .collect::<Vec<_>>()
                    .join("\n"),
            )
        } else {
            None
        };
        if let Some(replacement) = replacement
            && value.0 != replacement
        {
            value.0 = replacement;
        }
    }
    for mut bar in &mut bars {
        bar.width = percent(if r.busy && (r.progress == 0. || r.progress >= 100.) {
            20. + (time.elapsed_secs() * 30.) % 70.
        } else {
            r.progress
        });
    }
    for mut color in &mut buttons {
        color.0 = if r.busy { BG } else { HEADER };
    }
    if r.busy {
        for (mut position, node) in &mut scroll {
            position.0.y =
                (node.content_size().y - node.size().y).max(0.) * node.inverse_scale_factor();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::ecs::system::RunSystemOnce;
    #[test]
    fn dialog_retains_entities_and_closes_without_clearing_worker_report() {
        let mut app = App::new();
        app.init_resource::<State>()
            .init_resource::<View>()
            .init_resource::<InputFocus>()
            .init_resource::<ButtonInput<KeyCode>>()
            .init_resource::<Time>();
        app.world_mut().resource_mut::<State>().open = true;
        app.world_mut().run_system_once(refresh).unwrap();
        let host = app.world().resource::<View>().host.unwrap();
        app.world_mut().resource_mut::<State>().report.status = "Downloading Rust".into();
        app.world_mut().run_system_once(refresh).unwrap();
        assert_eq!(app.world().resource::<View>().host, Some(host));
        assert!(
            app.world_mut()
                .query::<&Text>()
                .iter(app.world())
                .any(|t| t.0 == "Downloading Rust")
        );
        app.world_mut().resource_mut::<State>().open = false;
        app.world_mut().run_system_once(refresh).unwrap();
        assert!(app.world().get_entity(host).is_err());
        assert_eq!(
            app.world().resource::<State>().report.status,
            "Downloading Rust"
        );
    }
}
