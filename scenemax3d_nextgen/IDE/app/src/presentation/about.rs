//! Retained About dialog and searchable, embedded third-party acknowledgements.
use super::chrome::{ChromeAction, Panel};
use crate::application::{Command, CommandQueue};
use bevy::{input_focus::tab_navigation::TabGroup, prelude::*, text::EditableText};
use scenemax_ide_ui::{button, label, property, theme::*};
use scenemax_ui_input::Interaction;
use serde_json::Value;
use std::sync::OnceLock;

fn credits() -> &'static Vec<Value> {
    static DATA: OnceLock<Vec<Value>> = OnceLock::new();
    DATA.get_or_init(|| {
        serde_json::from_str(include_str!("../../assets/credits.json")).unwrap_or_default()
    })
}
#[derive(Component)]
pub(crate) struct Search;
#[derive(Component)]
struct Link(String);
#[derive(Component)]
pub(crate) struct List;
#[derive(Component)]
pub(crate) struct Count;
#[derive(Component)]
pub(crate) enum Action {
    Page(i32),
    Notice(usize),
}
#[derive(Resource, Default)]
pub(crate) struct State {
    query: String,
    page: usize,
    notice: Option<usize>,
    built: bool,
    open: bool,
}
fn text(c: &mut Commands, p: Entity, value: impl Into<String>, size: f32) {
    c.spawn((
        label(value, size),
        Node {
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(p),
    ));
}
fn link(c: &mut Commands, p: Entity, title: &str, url: &str) {
    let url = url.replacen("http://", "https://", 1);
    let entity = button(c, p, title, Link(url.clone()));
    c.entity(entity).observe(
        |event: On<PointerClick>, links: Query<&Link>, mut queue: ResMut<CommandQueue>| {
            if let Ok(link) = links.get(event.entity) {
                queue.0.push_back(Command::OpenWeb(link.0.clone()));
            }
        },
    );
}
pub(crate) fn build(c: &mut Commands, root: Entity) {
    let overlay = c
        .spawn((
            Panel::Help,
            Node {
                display: Display::None,
                position_type: PositionType::Absolute,
                width: percent(100.),
                height: percent(100.),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                ..default()
            },
            GlobalZIndex(350),
            BackgroundColor(Color::srgba(0., 0., 0., 0.65)),
            TabGroup::modal(),
            ChildOf(root),
        ))
        .id();
    let card = c
        .spawn((
            Node {
                width: px(860.),
                max_width: percent(94.),
                height: px(760.),
                max_height: percent(92.),
                flex_direction: FlexDirection::Column,
                padding: px(26.).all(),
                row_gap: px(14.),
                border: px(1.).all(),
                border_radius: BorderRadius::all(px(14.)),
                ..default()
            },
            BorderColor::all(EDGE),
            BackgroundColor(BG),
            ChildOf(overlay),
        ))
        .id();
    let hero = c
        .spawn((
            Node {
                column_gap: px(18.),
                align_items: AlignItems::Center,
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(card),
        ))
        .id();
    c.spawn((
        super::titlebar::AppIcon,
        Node {
            width: px(68.),
            height: px(68.),
            ..default()
        },
        ChildOf(hero),
    ));
    let title = c
        .spawn((
            Node {
                flex_direction: FlexDirection::Column,
                row_gap: px(4.),
                ..default()
            },
            ChildOf(hero),
        ))
        .id();
    text(c, title, "SceneMax Studio", 30.);
    text(c, title, "The choreographic programming language", 15.);
    text(
        c,
        title,
        format!("Version {}  •  Rust / Bevy", env!("CARGO_PKG_VERSION")),
        12.,
    );
    text(
        c,
        card,
        "Choreograph 3D worlds, motion and interaction with code.",
        14.,
    );
    let links = c
        .spawn((
            Node {
                column_gap: px(10.),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(card),
        ))
        .id();
    link(
        c,
        links,
        "www.scenemax3d.com ↗",
        "https://www.scenemax3d.com",
    );
    link(
        c,
        links,
        "GitHub repository ↗",
        "https://github.com/scenemax3d/scenemax3d-desktop",
    );
    text(c, card, "Technology behind the language", 18.);
    text(
        c,
        card,
        "Bevy · wgpu · Parley · Vello · glTF · Effekseer",
        14.,
    );
    text(c, card, "Third-party credits", 17.);
    text(
        c,
        card,
        "Libraries, native tools and adapted code. Select a license to read its notices.",
        12.,
    );
    let search = property::input(c, card, Search, "");
    c.entity(search).insert((
        BackgroundColor(Color::srgb(0.12, 0.14, 0.15)),
        BorderColor::all(EDGE),
    ));
    c.entity(search).entry::<Node>().and_modify(|mut n| {
        n.border = px(1.).all();
        n.border_radius = BorderRadius::all(px(5.));
        n.height = px(32.);
    });
    c.spawn((Count, label("", 11.), ChildOf(card)));
    let list = property::scroll_column(c, card);
    c.entity(list).insert(List);
    let footer = c
        .spawn((
            Node {
                column_gap: px(8.),
                align_items: AlignItems::Center,
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(card),
        ))
        .id();
    button(c, footer, "Previous", Action::Page(-1));
    button(c, footer, "Next", Action::Page(1));
    c.spawn((
        Node {
            flex_grow: 1.,
            ..default()
        },
        ChildOf(footer),
    ));
    button(c, footer, "Close", ChromeAction::Dismiss);
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Ui<'w, 's> {
    search: Query<'w, 's, (Entity, &'static EditableText), With<Search>>,
    host: Query<'w, 's, (&'static Panel, &'static Node)>,
    list: Query<'w, 's, Entity, With<List>>,
    count: Query<'w, 's, &'static mut Text, With<Count>>,
    actions: Query<'w, 's, (&'static Interaction, &'static Action), Changed<Interaction>>,
    focus: Option<ResMut<'w, bevy::input_focus::InputFocus>>,
}
pub(crate) fn update(mut c: Commands, mut state: ResMut<State>, mut ui: Ui) {
    if !ui
        .host
        .iter()
        .any(|(p, n)| *p == Panel::Help && n.display != Display::None)
    {
        state.open = false;
        return;
    }
    let Ok(list) = ui.list.single() else {
        return;
    };
    if !state.open {
        state.open = true;
        if let Ok((entity, _)) = ui.search.single()
            && let Some(focus) = ui.focus.as_mut()
        {
            focus.set(entity, bevy::input_focus::FocusCause::Navigated);
        }
    }
    let query = ui
        .search
        .single()
        .map(|(_, s)| s.value().to_string().to_lowercase())
        .unwrap_or_default();
    let mut changed = !state.built;
    if query != state.query {
        state.query = query;
        state.page = 0;
        state.notice = None;
        changed = true;
    }
    for (i, a) in &ui.actions {
        if *i != Interaction::Pressed {
            continue;
        }
        changed = true;
        match a {
            Action::Page(d) => {
                state.page = state.page.saturating_add_signed(*d as isize);
                state.notice = None;
            }
            Action::Notice(i) => {
                state.notice = if state.notice == Some(*i) {
                    None
                } else {
                    Some(*i)
                }
            }
        }
    }
    if !changed {
        return;
    }
    state.built = true;
    let mut rows: Vec<_> = credits()
        .iter()
        .enumerate()
        .filter(|(_, e)| {
            ["name", "authors", "license", "group"].iter().any(|k| {
                e[k].as_str()
                    .unwrap_or("")
                    .to_lowercase()
                    .contains(&state.query)
            })
        })
        .collect();
    let featured = [
        "Bevy",
        "wgpu",
        "parley",
        "Effekseer / Vulkan runtime",
        "Renzora gizmo",
    ];
    rows.sort_by_key(|(index, e)| {
        (
            featured
                .iter()
                .position(|name| e["name"] == *name)
                .unwrap_or(featured.len()),
            *index,
        )
    });
    let pages = rows.len().div_ceil(24).max(1);
    state.page = state.page.min(pages - 1);
    for mut t in &mut ui.count {
        t.0 = format!(
            "{} projects  ·  Page {} of {}  ·  Search by name, contributor or license",
            rows.len(),
            state.page + 1,
            pages
        );
    }
    c.entity(list).despawn_children();
    c.entity(list).insert(ScrollPosition::default());
    for (i, e) in rows.into_iter().skip(state.page * 24).take(24) {
        let row = c
            .spawn((
                Node {
                    flex_direction: FlexDirection::Column,
                    flex_shrink: 0.,
                    padding: px(12.).all(),
                    row_gap: px(5.),
                    margin: UiRect::bottom(px(6.)),
                    border_radius: BorderRadius::all(px(6.)),
                    ..default()
                },
                BackgroundColor(PANEL),
                ChildOf(list),
            ))
            .id();
        let name = e["name"].as_str().unwrap_or("");
        let title = format!("{}  {}", name, e["version"].as_str().unwrap_or(""));
        link(&mut c, row, &title, e["url"].as_str().unwrap_or(""));
        text(&mut c, row, e["authors"].as_str().unwrap_or(""), 11.);
        button(
            &mut c,
            row,
            e["license"].as_str().unwrap_or("License"),
            Action::Notice(i),
        );
        if state.notice == Some(i) {
            let notices = e["notices"]
                .as_str()
                .filter(|s| !s.is_empty())
                .unwrap_or("License text is available from the project's upstream website.");
            let notices = if notices.starts_with("FBX-SDK-License.rtf") {
                "Autodesk FBX SDK license. The full license accompanies the bundled FBX2glTF converter; use the project link above for Autodesk SDK terms."
            } else {
                notices
            };
            // Split long notices into paragraphs to keep text layout bounded.
            for paragraph in notices.split("\n\n") {
                text(&mut c, row, paragraph, 11.);
            }
        }
    }
}

/// Installed only for explicit bounded screenshot launches.
pub(crate) fn smoke(mut chrome: ResMut<super::chrome::ChromeState>, mut once: Local<bool>) {
    if !*once {
        chrome.show_about();
        *once = true;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn about_has_links_searchable_credits_and_closes_with_escape() {
        let (mut app, _dir, _) = crate::tests::app();
        app.init_resource::<bevy::input_focus::InputFocus>();
        app.update();
        app.world_mut()
            .resource_mut::<super::super::chrome::ChromeState>()
            .show_about();
        app.update();
        let w = app.world_mut();
        let links: Vec<_> = w.query::<&Link>().iter(w).map(|v| v.0.clone()).collect();
        assert!(links.contains(&"https://www.scenemax3d.com".into()));
        assert!(links.contains(&"https://github.com/scenemax3d/scenemax3d-desktop".into()));
        assert!(credits().len() > 100);
        assert_eq!(
            credits()
                .iter()
                .filter(|e| e["url"]
                    .as_str()
                    .is_some_and(|u| u.trim_end_matches('/').trim_end_matches(".git")
                        == "https://github.com/bevyengine/bevy"))
                .count(),
            1
        );
        let search = w
            .query_filtered::<Entity, With<Search>>()
            .single(w)
            .unwrap();
        assert_eq!(
            w.resource::<bevy::input_focus::InputFocus>().get(),
            Some(search)
        );
        w.get_mut::<EditableText>(search)
            .unwrap()
            .editor_mut()
            .set_text("Renzora");
        app.update();
        let w = app.world_mut();
        let count = w.query_filtered::<&Text, With<Count>>().single(w).unwrap();
        assert!(count.0.starts_with("1 projects"));
        w.resource_mut::<ButtonInput<KeyCode>>()
            .press(KeyCode::Escape);
        app.update();
        assert!(
            !app.world()
                .resource::<super::super::chrome::ChromeState>()
                .about_open()
        );
    }
}
