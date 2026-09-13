//! Compact retained tree rows with independent disclosure and selection targets.
use crate::{ButtonSurface, label, theme::*};
use bevy::prelude::*;

/// Visible caption of a retained tree row.
#[derive(Component)]
pub struct Caption;

/// Mutable appearance of a retained row. Changing selection never replaces it.
#[derive(Component, PartialEq)]
pub struct TreeAppearance {
    /// Whether the row is selected.
    pub selected: bool,
    /// Branch expansion, or None for a leaf.
    pub expanded: Option<bool>,
}
#[derive(Component)]
pub(crate) struct Disclosure;

/// Presentation of one visible hierarchy entry; the caller owns the tree model.
pub struct TreeRow<'a> {
    /// Primary label, kept on one line.
    pub caption: &'a str,
    /// Short type icon or abbreviation.
    pub icon: &'a str,
    /// Indentation level.
    pub depth: usize,
    /// None for leaves; expansion state for branches.
    pub expanded: Option<bool>,
    /// Selected row styling.
    pub selected: bool,
    /// Dim entries hidden in the scene.
    pub muted: bool,
}

/// Spawn a 24-pixel tree row with a separate expander and full-width selection.
pub fn row<S: Component, T: Component>(
    commands: &mut Commands,
    parent: Entity,
    item: TreeRow<'_>,
    select: S,
    toggle: T,
) -> Entity {
    let surface = if item.selected { SELECTED } else { PANEL };
    let row = commands
        .spawn((
            TreeAppearance {
                selected: item.selected,
                expanded: item.expanded,
            },
            Node {
                width: percent(100.),
                height: px(24.),
                min_height: px(24.),
                flex_shrink: 0.,
                padding: UiRect::left(px(6. + item.depth as f32 * 16.)),
                align_items: AlignItems::Center,
                overflow: Overflow::clip(),
                ..default()
            },
            BackgroundColor(surface),
            ChildOf(parent),
        ))
        .id();
    let disclosure = commands
        .spawn((
            Disclosure,
            Node {
                width: px(18.),
                height: px(24.),
                flex_shrink: 0.,
                justify_content: JustifyContent::Center,
                align_items: AlignItems::Center,
                ..default()
            },
            ChildOf(row),
        ))
        .id();
    if let Some(expanded) = item.expanded {
        commands.entity(disclosure).insert((
            Button,
            toggle,
            ButtonSurface(surface),
            BackgroundColor(surface),
        ));
        commands.spawn((
            Node {
                width: px(5.),
                height: px(5.),
                border: UiRect {
                    right: px(1.),
                    bottom: px(1.),
                    ..default()
                },
                ..default()
            },
            BorderColor::all(INK),
            UiTransform::from_rotation(Rot2::degrees(if expanded { 45. } else { -45. })),
            ChildOf(disclosure),
        ));
    }
    let select = commands
        .spawn((
            Button,
            select,
            ButtonSurface(surface),
            BackgroundColor(surface),
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                height: percent(100.),
                column_gap: px(7.),
                align_items: AlignItems::Center,
                overflow: Overflow::clip(),
                ..default()
            },
            ChildOf(row),
        ))
        .id();
    commands.spawn((
        label(item.icon, 11.),
        Node {
            width: px(18.),
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(select),
    ));
    let text = commands
        .spawn((
            Caption,
            label(item.caption, 12.),
            TextLayout::no_wrap(),
            ChildOf(select),
        ))
        .id();
    if item.muted {
        commands
            .entity(text)
            .insert(TextColor(Color::srgb_u8(125, 131, 141)));
    }
    row
}

type RowChildren<'w, 's> = Query<
    'w,
    's,
    (
        Option<&'static Interaction>,
        Option<&'static Children>,
        Has<Disclosure>,
    ),
    Without<TreeAppearance>,
>;

pub(crate) fn synchronize(
    mut commands: Commands,
    mut rows: Query<(&TreeAppearance, &Children, &mut BackgroundColor), Changed<TreeAppearance>>,
    children: RowChildren,
) {
    for (appearance, descendants, mut color) in &mut rows {
        let surface = if appearance.selected { SELECTED } else { PANEL };
        if color.0 != surface {
            color.0 = surface;
        }
        for child in descendants.iter() {
            let Ok((interaction, descendants, disclosure)) = children.get(child) else {
                continue;
            };
            commands.entity(child).insert(ButtonSurface(surface));
            if interaction.is_none_or(|i| *i == Interaction::None) {
                commands.entity(child).insert(BackgroundColor(surface));
            }
            if disclosure
                && let (Some(expanded), Some(descendants)) = (appearance.expanded, descendants)
            {
                for arrow in descendants.iter() {
                    commands
                        .entity(arrow)
                        .insert(UiTransform::from_rotation(Rot2::degrees(if expanded {
                            45.
                        } else {
                            -45.
                        })));
                }
            }
        }
    }
}
