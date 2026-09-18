//! Shared studio palette, independent of editor features.
use bevy::prelude::*;
/// Editor canvas background.
pub const BG: Color = Color::srgb_u8(58, 62, 64);
/// Tool window and menu surface.
pub const PANEL: Color = Color::srgb_u8(68, 73, 75);
/// Standard foreground text.
pub const INK: Color = Color::srgb_u8(195, 199, 206);
/// Muted selection background sampled from the Java IDE reference.
pub const TEXT_SELECTION: Color = Color::srgb_u8(63, 70, 82);
/// Consistent text selection and caret appearance without overriding syntax colors.
pub const TEXT_CURSOR_STYLE: bevy::text::TextCursorStyle = bevy::text::TextCursorStyle {
    color: Color::WHITE,
    selection_color: TEXT_SELECTION,
    unfocused_selection_color: TEXT_SELECTION,
    selected_text_color: None,
};
/// Quiet supporting text.
pub const MUTED: Color = Color::srgb_u8(158, 165, 172);
/// Selected navigator row and menu item.
pub const SELECTED: Color = Color::srgb_u8(77, 111, 169);
/// Active editor underline and focus accent.
pub const ACCENT: Color = Color::srgb(0.35, 0.57, 0.96);
/// Subtle separators between tool windows.
pub const EDGE: Color = Color::srgb_u8(85, 90, 92);

/// Dark title/menu band, distinct from the lighter tool panels.
pub const HEADER: Color = Color::srgb_u8(48, 51, 52);
