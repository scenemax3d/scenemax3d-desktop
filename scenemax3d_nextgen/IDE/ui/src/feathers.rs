//! SceneMax styling for Bevy's editor controls.
use crate::theme::*;
use bevy::{
    feathers::{
        FeathersCorePlugin, dark_theme::create_dark_theme, theme::UiTheme, tokens::semantic as s,
    },
    prelude::*,
};

pub(crate) fn install(app: &mut App) {
    app.add_plugins(FeathersCorePlugin);
    app.insert_resource(theme());
    app.add_systems(
        PostUpdate,
        popup_layers.before(bevy::ui::UiSystems::Prepare),
    );
    app.add_systems(Update, focus_dropdown);
    app.add_systems(
        PreUpdate,
        popup_escape
            .after(bevy::input::InputSystems)
            .before(bevy::input_focus::InputFocusSystems::Dispatch),
    );
    app.add_systems(
        PostUpdate,
        typography
            .after(bevy::app::PropagateSet::<TextFont>::default())
            .before(bevy::ui::UiSystems::Content),
    );
}

fn typography(
    mut fonts: Query<
        (&mut TextFont, Has<bevy::text::EditableText>),
        (With<bevy::feathers::theme::ThemedText>, Changed<TextFont>),
    >,
) {
    for (mut font, editable) in &mut fonts {
        // Preserve Feathers' dedicated symbol face for the color-wheel icon.
        if let bevy::text::FontSource::Handle(handle) = &font.font
            && handle.path().is_some_and(|path| {
                path.path()
                    .file_name()
                    .is_some_and(|name| name.to_string_lossy().starts_with("NotoSansSymbols"))
            })
        {
            continue;
        }
        let source = if editable {
            bevy::text::FontSource::monospace()
        } else {
            bevy::text::FontSource::sans_serif()
        };
        let size = FontSize::Px(if editable { 12. } else { 13. });
        if font.font != source {
            font.font = source;
        }
        if font.font_size != size {
            font.font_size = size;
        }
    }
}

fn focus_dropdown(
    popups: Query<
        (Entity, &Visibility),
        (
            With<bevy::feathers::controls::FeathersMenuPopup>,
            Changed<Visibility>,
        ),
    >,
    parents: Query<&ChildOf>,
    selects: Query<(), With<bevy::feathers::controls::FeathersSelect>>,
    children: Query<&Children>,
    lists: Query<(), With<bevy::ui_widgets::ListBox>>,
    mut focus: ResMut<bevy::input_focus::InputFocus>,
) {
    for (popup, visibility) in &popups {
        if *visibility == Visibility::Visible
            && parents
                .iter_ancestors(popup)
                .any(|parent| selects.contains(parent))
            && let Some(list) = children
                .iter_descendants(popup)
                .find(|entity| lists.contains(*entity))
        {
            focus.set(list, bevy::input_focus::FocusCause::Navigated);
        }
    }
}

// Keep Escape local to an open picker/menu instead of also closing its designer.
// The focused KeyboardInput event still reaches Bevy's menu handler.
fn popup_escape(
    mut keys: ResMut<ButtonInput<KeyCode>>,
    popups: Query<&Visibility, With<bevy::feathers::controls::FeathersMenuPopup>>,
) {
    if keys.just_pressed(KeyCode::Escape)
        && popups
            .iter()
            .any(|visibility| *visibility == Visibility::Visible)
    {
        keys.clear_just_pressed(KeyCode::Escape);
    }
}

// Feathers' default popup layer is below several SceneMax modal designers.
fn popup_layers(
    mut commands: Commands,
    popups: Query<Entity, Added<bevy::feathers::controls::FeathersMenuPopup>>,
    parents: Query<&ChildOf>,
    layers: Query<&GlobalZIndex>,
) {
    for popup in &popups {
        let layer = parents
            .iter_ancestors(popup)
            .filter_map(|parent| layers.get(parent).ok().map(|z| z.0))
            .max()
            .unwrap_or(0);
        commands
            .entity(popup)
            .insert(GlobalZIndex(layer.max(100) + 1));
    }
}

pub(crate) fn theme() -> UiTheme {
    let mut theme = create_dark_theme();
    theme.semantic_overrides.clear();
    for (token, color) in [
        (s::SURFACE_WINDOW, BG),
        (s::SURFACE_PANE_BODY, PANEL),
        (s::SURFACE_PANE_HEADER, HEADER),
        (s::SURFACE_SUBPANE_BODY, PANEL),
        (s::SURFACE_SUBPANE_HEADER, HEADER),
        (s::SURFACE_GROUP, PANEL),
        (s::SURFACE_DIALOG, PANEL),
        (s::SURFACE_MENU, PANEL),
        (s::BORDER_DEFAULT, EDGE),
        (s::TEXT_DEFAULT, INK),
        (s::TEXT_DIM, MUTED),
        (s::TEXT_DISABLED, MUTED.with_alpha(0.5)),
        (s::FILL_ACCENT_DEFAULT, SELECTED),
        (s::FILL_ACCENT_HOVER, ACCENT),
        (s::FILL_ACCENT_PRESSED, SELECTED),
        (s::FILL_SOLID_DEFAULT, BG),
        (s::FILL_SOLID_HOVER, EDGE),
        (s::FILL_SOLID_PRESSED, HEADER),
        (s::FILL_FIELD_DEFAULT, BG),
        (s::FILL_FIELD_HOVER, HEADER),
        (s::FILL_FIELD_PRESSED, HEADER),
        (s::FILL_ITEM_HOVER, EDGE),
        (s::FILL_ITEM_PRESSED, SELECTED),
        (s::FOCUS_RING, ACCENT),
    ] {
        theme.semantic_base.insert(token, color);
    }
    UiTheme(theme)
}

#[cfg(test)]
#[path = "feathers_tests.rs"]
mod tests;
