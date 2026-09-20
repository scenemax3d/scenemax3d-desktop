//! Menus and transient command panels. Application actions remain shared with shortcuts.
use super::{
    components::{Editor, Field, TabButton},
    input::Action,
};
use crate::application::Session;
use bevy::{
    input_focus::{FocusCause, InputFocus},
    prelude::*,
};
use scenemax_ide_ui::{ButtonSurface, button, label, theme::*};

#[derive(Component, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Panel {
    Project,
    Search,
    NewFile,
    Help,
}
#[derive(Component)]
pub(crate) enum ChromeAction {
    Menu(usize),
    Submenu(usize),
    Panel(Panel),
    Dismiss,
}
#[derive(Component)]
pub(crate) struct MenuPopup(usize);
#[derive(Component)]
struct MenuItem;
#[derive(Resource, Default)]
pub(crate) struct ChromeState {
    menu: Option<usize>,
    submenu: Option<usize>,
    panel: Option<Panel>,
}

pub(crate) fn menu_bar(commands: &mut Commands, root: Entity) {
    let bar = commands
        .spawn((
            Node {
                height: px(34.),
                flex_shrink: 0.,
                align_items: AlignItems::Center,
                padding: UiRect::horizontal(px(8.)),
                ..default()
            },
            BackgroundColor(HEADER),
            ChildOf(root),
        ))
        .id();
    commands.spawn((
        super::titlebar::AppIcon,
        Node {
            width: px(20.),
            height: px(20.),
            margin: UiRect::right(px(6.)),
            ..default()
        },
        ChildOf(bar),
    ));
    let mut next_popup = super::menu_catalog::MENUS.len();
    for (index, menu) in super::menu_catalog::MENUS.iter().enumerate() {
        let entry = button(commands, bar, menu.name, ChromeAction::Menu(index));
        commands
            .entity(entry)
            .insert((ButtonSurface(HEADER), BackgroundColor(HEADER)));
        let popup = popup(commands, root, index, 32. + index as f32 * 62., 33.);
        // Actions relocated from the removed document toolbar, as requested.
        if menu.name == "File" {
            for (name, shortcut, action) in [
                ("Save", "Ctrl+S", Action::Save),
                ("Save all", "Ctrl+Shift+S", Action::SaveAll),
                ("Undo", "Ctrl+Z", Action::Undo),
                ("Redo", "Ctrl+Y", Action::Redo),
            ] {
                item(commands, popup, name, shortcut, action);
            }
            panel_item(commands, popup, "Find…", "Ctrl+F", Panel::Search);
            separator(commands, popup);
        } else if menu.name == "Tools" {
            super::deployment::menu_item(commands, popup);
            for (name, shortcut, action) in [
                ("Check syntax", "", Action::Check),
                ("Run project", "F10", Action::RunProject),
                ("Run file", "F8", Action::Run),
                ("Stop", "", Action::Stop),
            ] {
                item(commands, popup, name, shortcut, action);
            }
            separator(commands, popup);
        }
        populate_menu(commands, root, popup, menu.children, &mut next_popup);
    }
    super::titlebar::drag(commands, bar);
}
fn popup(commands: &mut Commands, root: Entity, index: usize, left: f32, top: f32) -> Entity {
    commands
        .spawn((
            MenuPopup(index),
            GlobalZIndex(100),
            Node {
                display: Display::None,
                position_type: PositionType::Absolute,
                top: px(top),
                left: px(left),
                width: px(320.),
                flex_direction: FlexDirection::Column,
                padding: px(5.).all(),
                border: px(1.).all(),
                ..default()
            },
            BorderColor::all(EDGE),
            BackgroundColor(PANEL),
            ChildOf(root),
        ))
        .id()
}
fn populate_menu(
    commands: &mut Commands,
    root: Entity,
    host: Entity,
    entries: &[super::menu_catalog::MenuEntry],
    next_popup: &mut usize,
) {
    for entry in entries {
        if entry.name == "-" {
            separator(commands, host);
            continue;
        }
        if !entry.children.is_empty() {
            let id = *next_popup;
            *next_popup += 1;
            let row = button(commands, host, entry.name, ChromeAction::Submenu(id));
            finish_item(commands, row, ">");
            let nested = popup(commands, root, id, 476., 33.);
            populate_menu(commands, root, nested, entry.children, next_popup);
            if entry.name == "Projects" {
                separator(commands, nested);
                super::projects::choices(commands, nested, true);
            }
            continue;
        }
        if entry.command == "add_skybox" {
            continue;
        }
        if let Some(row) = super::asset_import::menu_item(commands, host, entry.name, entry.command)
        {
            finish_item(commands, row, "");
            continue;
        }
        if entry.command == "project_inventory" {
            let row = super::inventory::menu_item(commands, host, entry.name);
            finish_item(commands, row, "");
            continue;
        }
        if entry.command == "font_generator" {
            let row = super::font_generator::menu_item(commands, host, entry.name);
            finish_item(commands, row, "");
            continue;
        }
        if entry.command == "create_weapon_document" {
            let row = button(commands, host, entry.name, Name::new("Create weapon"));
            commands.entity(row).observe(
                |_: On<Pointer<Press>>,
                 session: Res<crate::application::Session>,
                 mut menu: ResMut<super::tree_menu::State>| {
                    menu.create_weapon(session.workspace.project().root().to_owned());
                },
            );
            finish_item(commands, row, "");
            continue;
        }
        if entry.command == "create_material_document" {
            let row = button(commands, host, entry.name, Name::new("Create material"));
            commands.entity(row).observe(
                |_: On<Pointer<Click>>,
                 session: Res<crate::application::Session>,
                 mut menu: ResMut<super::tree_menu::State>| {
                    menu.create_material(session.workspace.project().root().join("scripts"));
                },
            );
            finish_item(commands, row, "");
            continue;
        }
        match entry.command {
            "project_explorer" | "new_project_scripts_folder" => {
                panel_item(commands, host, entry.name, "", Panel::Project)
            }
            "refresh_project_tree" => item(commands, host, entry.name, "", Action::Refresh),
            "restart_app" => item(commands, host, entry.name, "Ctrl+Alt+R", Action::Restart),
            "exit" => item(commands, host, entry.name, "", Action::Exit),
            "about" => panel_item(commands, host, entry.name, "", Panel::Help),
            _ => {
                let row = commands
                    .spawn((
                        Node {
                            height: px(30.),
                            padding: UiRect::horizontal(px(9.)),
                            align_items: AlignItems::Center,
                            ..default()
                        },
                        ChildOf(host),
                    ))
                    .id();
                let text = commands.spawn((label(entry.name, 13.), ChildOf(row))).id();
                commands.entity(text).insert(TextColor(MUTED));
            }
        }
    }
}

fn item(commands: &mut Commands, parent: Entity, name: &str, shortcut: &str, action: Action) {
    let row = button(commands, parent, name, action);
    finish_item(commands, row, shortcut);
}
fn panel_item(commands: &mut Commands, parent: Entity, name: &str, shortcut: &str, panel: Panel) {
    let row = button(commands, parent, name, ChromeAction::Panel(panel));
    finish_item(commands, row, shortcut);
}
fn finish_item(commands: &mut Commands, row: Entity, shortcut: &str) {
    commands.entity(row).insert((
        MenuItem,
        Node {
            height: px(30.),
            padding: UiRect::horizontal(px(9.)),
            justify_content: JustifyContent::SpaceBetween,
            align_items: AlignItems::Center,
            ..default()
        },
    ));
    let text = commands.spawn((label(shortcut, 11.), ChildOf(row))).id();
    commands.entity(text).insert(TextColor(MUTED));
}
fn separator(commands: &mut Commands, parent: Entity) {
    commands.spawn((
        Node {
            height: px(1.),
            margin: UiRect::vertical(px(5.)),
            ..default()
        },
        BackgroundColor(EDGE),
        ChildOf(parent),
    ));
}

type ChromeButtons<'w, 's> = Query<
    'w,
    's,
    (
        &'static Interaction,
        Option<&'static ChromeAction>,
        Option<&'static Action>,
        Option<&'static MenuItem>,
    ),
    With<Button>,
>;

#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct ChromeViews<'w, 's> {
    panels: Query<'w, 's, (&'static Panel, &'static mut Node), Without<MenuPopup>>,
    menus: Query<'w, 's, (&'static MenuPopup, &'static mut Node), Without<Panel>>,
    buttons: ChromeButtons<'w, 's>,
    fields: Query<'w, 's, (Entity, &'static Field)>,
    editors: Query<'w, 's, (Entity, &'static Editor)>,
}
pub(crate) fn controls(
    mut state: ResMut<ChromeState>,
    mut views: ChromeViews,
    keys: Res<ButtonInput<KeyCode>>,
    mouse: Res<ButtonInput<MouseButton>>,
    mut focus: Option<ResMut<InputFocus>>,
    session: Res<Session>,
    context: Option<Res<super::tree_menu::State>>,
) {
    if context.as_ref().is_some_and(|m| m.is_open()) {
        state.menu = None;
        state.submenu = None;
    }
    let mut target = None;
    let mut restore_editor = false;
    if mouse.just_pressed(MouseButton::Left) {
        let mut inside_menu = false;
        for (interaction, chrome, action, menu_item) in &views.buttons {
            if *interaction != Interaction::Pressed {
                continue;
            }
            inside_menu |= chrome.is_some() || menu_item.is_some();
            if action.is_some() || (menu_item.is_some() && chrome.is_none()) {
                state.menu = None;
                state.submenu = None;
            }
            if let Some(chrome) = chrome {
                match chrome {
                    ChromeAction::Menu(index) => {
                        state.submenu = None;
                        state.menu = if state.menu == Some(*index) {
                            None
                        } else {
                            Some(*index)
                        }
                    }
                    ChromeAction::Submenu(index) => {
                        state.submenu = if state.submenu == Some(*index) {
                            None
                        } else {
                            Some(*index)
                        };
                    }
                    ChromeAction::Panel(panel) => {
                        state.panel = Some(*panel);
                        state.menu = None;
                        state.submenu = None;
                        target = panel_field(*panel);
                    }
                    ChromeAction::Dismiss => {
                        state.panel = None;
                        state.menu = None;
                        state.submenu = None;
                        restore_editor = true;
                    }
                }
            }
        }
        if !inside_menu {
            state.menu = None;
            state.submenu = None;
        }
    }
    let ctrl = keys.any_pressed([KeyCode::ControlLeft, KeyCode::ControlRight]);
    for (key, panel, field) in [
        (KeyCode::KeyF, Panel::Search, Field::Find),
        (KeyCode::KeyG, Panel::Search, Field::Line),
        (KeyCode::KeyN, Panel::NewFile, Field::NewScript),
        (KeyCode::KeyO, Panel::Project, Field::ProjectFilter),
    ] {
        if ctrl && keys.just_pressed(key) && !context.as_ref().is_some_and(|m| m.is_open()) {
            state.panel = Some(panel);
            state.menu = None;
            state.submenu = None;
            target = Some(field);
        }
    }
    if keys.just_pressed(KeyCode::Escape) {
        state.menu = None;
        state.submenu = None;
        state.panel = None;
        restore_editor = true;
    }
    for (panel, mut node) in &mut views.panels {
        let display = if state.panel == Some(*panel) {
            Display::Flex
        } else {
            Display::None
        };
        if node.display != display {
            node.display = display;
        }
    }
    for (menu, mut node) in &mut views.menus {
        let display = if state.menu == Some(menu.0) || state.submenu == Some(menu.0) {
            Display::Flex
        } else {
            Display::None
        };
        if node.display != display {
            node.display = display;
        }
    }
    if let Some(focus) = focus.as_mut() {
        if let Some(field) = target
            && let Some((entity, _)) = views.fields.iter().find(|(_, f)| **f == field)
        {
            focus.set(entity, FocusCause::Navigated);
        }
        if restore_editor
            && let Some((entity, _)) = views
                .editors
                .iter()
                .find(|(_, e)| Some(e.0) == session.workspace.active_id())
        {
            focus.set(entity, FocusCause::Navigated);
        }
    }
}
fn panel_field(panel: Panel) -> Option<Field> {
    match panel {
        Panel::Project => Some(Field::ProjectFilter),
        Panel::Search => Some(Field::Find),
        Panel::NewFile => Some(Field::NewScript),
        Panel::Help => None,
    }
}

pub(crate) fn active_tabs(
    session: Res<Session>,
    mut tabs: Query<(
        &TabButton,
        &Interaction,
        &mut BackgroundColor,
        &mut BorderColor,
        &mut ButtonSurface,
    )>,
) {
    for (tab, interaction, mut color, mut border, mut surface) in &mut tabs {
        let active = Some(tab.0) == session.workspace.active_id();
        let background = if active { BG } else { PANEL };
        if surface.0 != background {
            surface.0 = background;
        }
        let desired_border = BorderColor::all(if active { ACCENT } else { PANEL });
        if *border != desired_border {
            *border = desired_border;
        }
        if *interaction == Interaction::None && color.0 != background {
            color.0 = background;
        }
    }
}

impl ChromeState {
    pub(crate) fn preview_projects(&mut self) {
        self.panel = Some(Panel::Project);
    }
    pub(crate) fn preview_file_menu(&mut self) {
        self.menu = Some(0);
    }
}

impl ChromeState {
    pub(crate) fn close_project_picker(&mut self) {
        if self.panel == Some(Panel::Project) {
            self.panel = None;
        }
    }
}
