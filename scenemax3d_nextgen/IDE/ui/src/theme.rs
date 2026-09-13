//! Shared studio palette, independent of editor features.
use bevy::prelude::*;
/// Editor canvas background.
pub const BG: Color = Color::srgb_u8(58, 62, 64);
/// Tool window and menu surface.
pub const PANEL: Color = Color::srgb_u8(68, 73, 75);
/// Standard foreground text.
pub const INK: Color = Color::srgb_u8(195, 199, 206);
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
