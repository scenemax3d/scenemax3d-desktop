//! Reusable retained Bevy UI, with no SceneMax domain or runtime dependency.
#![forbid(unsafe_code)]
#![deny(missing_docs)]
pub mod canvas;
pub mod color;
pub mod completion;
mod editor;
mod feathers;
pub mod icons;
pub mod number;
mod syntax;
pub use syntax::{TextEmphasis, TextHighlights};
pub mod panels;
pub mod theme;
pub mod tree;
mod widgets;
use bevy::prelude::*;
pub use editor::{commit_pending_input, spawn_editor};
pub use widgets::{ButtonSurface, NoButtonFeedback, button, label};
/// Install reusable studio widget behavior.
pub struct StudioUiPlugin;
impl Plugin for StudioUiPlugin {
    fn build(&self, app: &mut App) {
        syntax::install(app);
        feathers::install(app);
        app.add_plugins(scenemax_ui_input::UiInputPlugin);
        app.add_systems(PreStartup, editor::share_font_cache);
        app.add_systems(
            PostUpdate,
            (
                tree::synchronize,
                icons::hover,
                icons::rasterize,
                number::update,
                color::update,
                property::synchronize_choices,
            )
                .before(bevy::ui::UiSystems::Prepare),
        );
        app.add_systems(
            Update,
            (
                widgets::button_feedback,
                panels::reset,
                panels::layout,
                password::render,
            )
                .chain(),
        )
        .add_systems(Last, panels::measure)
        .add_systems(PostUpdate, canvas::fit.before(bevy::ui::UiSystems::Prepare));
    }
}
pub mod password;
pub mod property;
