//! Retained suggestion popup, independent of language semantics.
use crate::{
    ButtonSurface, label,
    theme::{EDGE, PANEL, SELECTED},
};
use bevy::prelude::*;
/// Build a bounded suggestion popup anchored in its editor's coordinate space.
pub fn suggestion_popup(
    commands: &mut Commands,
    parent: Entity,
    position: Vec2,
    size: Vec2,
) -> Entity {
    commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                left: px(position.x),
                top: px(position.y),
                width: px(size.x),
                max_height: px(size.y),
                flex_direction: FlexDirection::Column,
                border: px(1.).all(),
                overflow: Overflow::clip(),
                ..default()
            },
            BackgroundColor(PANEL),
            BorderColor::all(EDGE),
            GlobalZIndex(150),
            ChildOf(parent),
        ))
        .id()
}
/// Add a selectable suggestion with a category; marker routes application input.
pub fn suggestion_row<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    name: &str,
    category: &str,
    selected: bool,
    marker: T,
) -> Entity {
    let row = commands
        .spawn((
            marker,
            Button,
            ButtonSurface(if selected { SELECTED } else { PANEL }),
            Node {
                height: px(26.),
                min_height: px(26.),
                padding: UiRect::axes(px(10.), px(2.)),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::SpaceBetween,
                ..default()
            },
            BackgroundColor(if selected { SELECTED } else { PANEL }),
            ChildOf(parent),
        ))
        .id();
    commands.spawn((label(name, 13.), ChildOf(row)));
    commands.spawn((label(category, 11.), ChildOf(row)));
    row
}
