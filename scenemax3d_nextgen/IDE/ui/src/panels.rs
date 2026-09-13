//! Retained tool surfaces and constrained splitters shared by editor features.
use crate::{label, theme::*};
use bevy::prelude::*;

/// Stable attachment point for a tool plugin's native Bevy UI children.
/// The shell owns this entity; the tool owns its descendants and feature state.
#[derive(Component)]
#[require(Node, PanelViewport)]
pub struct PanelHost(pub &'static str);

/// Latest measured panel content extent. Cameras can observe `Changed<PanelViewport>`.
/// Hidden or collapsed hosts have zero extent; suspend their render work then.
#[derive(Component, Clone, Copy, Debug, Default, PartialEq)]
pub struct PanelViewport {
    /// Available content size in logical UI pixels.
    pub logical_size: Vec2,
    /// Available size in physical render-target pixels.
    pub physical_size: UVec2,
}

/// Entities of a retained tool panel. Populate the content with ordinary Bevy UI.
pub struct ToolPanel {
    /// Container, including header and content.
    pub root: Entity,
    /// Header row, ready for caller-owned toolbar actions.
    pub header: Entity,
    /// Clipped content attachment point carrying `PanelHost`.
    pub content: Entity,
}

/// Spawn a tool panel without coupling its contents to the shell or runtime.
pub fn tool_panel(
    commands: &mut Commands,
    parent: Entity,
    id: &'static str,
    title: &str,
) -> ToolPanel {
    let root = commands
        .spawn((
            Node {
                flex_direction: FlexDirection::Column,
                min_width: px(0.),
                min_height: px(0.),
                overflow: Overflow::clip(),
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(parent),
        ))
        .id();
    let header = commands
        .spawn((
            Node {
                height: px(30.),
                flex_shrink: 0.,
                align_items: AlignItems::Center,
                justify_content: JustifyContent::SpaceBetween,
                padding: UiRect::horizontal(px(12.)),
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    commands.spawn((label(title, 13.), ChildOf(header)));
    let content = commands
        .spawn((
            PanelHost(id),
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                min_height: px(0.),
                overflow: Overflow::clip(),
                ..default()
            },
            ChildOf(root),
        ))
        .id();
    ToolPanel {
        root,
        header,
        content,
    }
}

/// Which dimension a splitter changes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PanelAxis {
    /// A panel beside the document area.
    Width,
    /// A panel above or below the document area.
    Height,
}

/// Retained panel extent in logical pixels, independent of its child widgets.
#[derive(Component)]
pub struct ResizablePanel {
    axis: PanelAxis,
    preferred: f32,
    initial: f32,
}
impl ResizablePanel {
    /// Create an extent with a finite positive starting size.
    pub fn new(axis: PanelAxis, pixels: f32) -> Self {
        let initial = if pixels.is_finite() {
            pixels.max(0.)
        } else {
            280.
        };
        Self {
            axis,
            preferred: initial,
            initial,
        }
    }
}

/// Side of a panel on which the divider sits.
#[derive(Clone, Copy)]
pub enum SplitterEdge {
    /// Dragging right/down grows the panel (e.g. left project tree).
    After,
    /// Dragging left/up grows the panel (e.g. bottom output).
    Before,
}
#[derive(Component)]
struct Splitter {
    target: Entity,
    axis: PanelAxis,
    sign: f32,
}

/// A button marker that resets all resizable panels to their initial sizes.
#[derive(Component)]
pub struct ResetPanelLayout;

/// Spawn a native Bevy picking/drag handle. No window or OS embedding is involved.
pub fn splitter(
    commands: &mut Commands,
    parent: Entity,
    target: Entity,
    axis: PanelAxis,
    edge: SplitterEdge,
) -> Entity {
    commands
        .spawn((
            Splitter {
                target,
                axis,
                sign: if matches!(edge, SplitterEdge::After) {
                    1.
                } else {
                    -1.
                },
            },
            Node {
                width: if axis == PanelAxis::Width {
                    px(5.)
                } else {
                    percent(100.)
                },
                height: if axis == PanelAxis::Height {
                    px(5.)
                } else {
                    percent(100.)
                },
                flex_shrink: 0.,
                ..default()
            },
            BackgroundColor(EDGE),
            ChildOf(parent),
        ))
        .observe(
            |mut drag: On<Pointer<Drag>>,
             handles: Query<&Splitter>,
             mut panels: Query<(&mut ResizablePanel, &Node, &ChildOf)>,
             parents: Query<&ComputedNode>,
             ui_scale: Option<Res<UiScale>>| {
                let Ok(handle) = handles.get(drag.entity) else {
                    return;
                };
                let Ok((mut panel, node, parent)) = panels.get_mut(handle.target) else {
                    return;
                };
                let Ok(computed) = parents.get(parent.parent()) else {
                    return;
                };
                let size = computed.size() * computed.inverse_scale_factor();
                let (extent, delta, available) = if handle.axis == PanelAxis::Width {
                    (node.width, drag.delta.x, size.x)
                } else {
                    (node.height, drag.delta.y, size.y)
                };
                let scale = ui_scale.map_or(1., |s| s.0).max(f32::EPSILON);
                if let Val::Px(actual) = extent
                    && delta.is_finite()
                {
                    panel.preferred =
                        constrained(actual + delta * handle.sign / scale, available, panel.axis);
                }
                drag.propagate(false);
            },
        )
        .observe(
            |event: On<Pointer<Over>>, mut colors: Query<&mut BackgroundColor>| {
                if let Ok(mut color) = colors.get_mut(event.entity) {
                    color.0 = ACCENT;
                }
            },
        )
        .observe(
            |event: On<Pointer<Out>>, mut colors: Query<&mut BackgroundColor>| {
                if let Ok(mut color) = colors.get_mut(event.entity) {
                    color.0 = EDGE;
                }
            },
        )
        .id()
}

fn constrained(preferred: f32, available: f32, axis: PanelAxis) -> f32 {
    let minimum: f32 = if axis == PanelAxis::Width { 200. } else { 70. };
    // Preserve at least 40% for the document area, including at small window sizes.
    let maximum = (available * 0.6).max(0.);
    preferred.clamp(minimum.min(maximum), maximum)
}

pub(crate) fn layout(
    mut panels: Query<(&ResizablePanel, &ChildOf, &mut Node)>,
    parents: Query<&ComputedNode>,
) {
    for (panel, parent, mut node) in &mut panels {
        let Ok(computed) = parents.get(parent.parent()) else {
            continue;
        };
        let size = computed.size() * computed.inverse_scale_factor();
        let available = if panel.axis == PanelAxis::Width {
            size.x
        } else {
            size.y
        };
        if available <= 0. {
            continue;
        }
        let extent = px(constrained(panel.preferred, available, panel.axis));
        match panel.axis {
            PanelAxis::Width if node.width != extent => node.width = extent,
            PanelAxis::Height if node.height != extent => node.height = extent,
            _ => {}
        }
    }
}

pub(crate) fn measure(mut hosts: Query<(&ComputedNode, &mut PanelViewport), With<PanelHost>>) {
    for (node, mut viewport) in &mut hosts {
        let physical = node.content_box().size().max(Vec2::ZERO);
        viewport.set_if_neq(PanelViewport {
            logical_size: physical * node.inverse_scale_factor(),
            physical_size: physical.round().as_uvec2(),
        });
    }
}

pub(crate) fn reset(
    buttons: Query<&Interaction, (Changed<Interaction>, With<ResetPanelLayout>)>,
    mut panels: Query<&mut ResizablePanel>,
) {
    if buttons
        .iter()
        .any(|interaction| *interaction == Interaction::Pressed)
    {
        for mut panel in &mut panels {
            panel.preferred = panel.initial;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn resizing_preserves_document_space_at_small_and_large_sizes() {
        assert_eq!(constrained(900., 1000., PanelAxis::Width), 600.);
        assert_eq!(constrained(0., 1000., PanelAxis::Width), 200.);
        assert!((constrained(280., 100., PanelAxis::Width) - 60.).abs() < 0.001);
        assert_eq!(constrained(0., 900., PanelAxis::Height), 70.);
    }
    #[test]
    fn reset_restores_preferred_extent_without_replacing_content() {
        let mut app = App::new();
        app.add_systems(Update, reset);
        let root = app
            .world_mut()
            .spawn(ResizablePanel::new(PanelAxis::Width, 280.))
            .id();
        let child = app.world_mut().spawn(ChildOf(root)).id();
        app.world_mut()
            .get_mut::<ResizablePanel>(root)
            .unwrap()
            .preferred = 500.;
        app.world_mut()
            .spawn((ResetPanelLayout, Interaction::Pressed));
        app.update();
        assert_eq!(
            app.world().get::<ResizablePanel>(root).unwrap().preferred,
            280.
        );
        assert_eq!(app.world().get::<ChildOf>(child).unwrap().parent(), root);
    }
    #[test]
    fn viewport_reports_dpi_scaled_content_and_keeps_child_state() {
        let mut app = App::new();
        app.add_systems(Update, measure);
        let host = app
            .world_mut()
            .spawn((
                PanelHost("designer-test"),
                ComputedNode {
                    size: Vec2::new(800., 600.),
                    inverse_scale_factor: 0.5,
                    ..default()
                },
            ))
            .id();
        let child = app
            .world_mut()
            .spawn((Text::new("retained designer state"), ChildOf(host)))
            .id();
        app.update();
        let viewport = *app.world().get::<PanelViewport>(host).unwrap();
        assert_eq!(viewport.logical_size, Vec2::new(400., 300.));
        assert_eq!(viewport.physical_size, UVec2::new(800, 600));
        app.world_mut().get_mut::<ComputedNode>(host).unwrap().size = Vec2::ZERO;
        app.update();
        assert_eq!(
            app.world()
                .get::<PanelViewport>(host)
                .unwrap()
                .physical_size,
            UVec2::ZERO
        );
        assert_eq!(
            app.world().get::<Text>(child).unwrap().0,
            "retained designer state"
        );
    }
    #[test]
    fn stable_extents_do_not_dirty_layout_every_frame() {
        #[derive(Resource, Default)]
        struct Changes(usize);
        let mut app = App::new();
        app.init_resource::<Changes>().add_systems(
            Update,
            (
                layout,
                |nodes: Query<&Node, Changed<Node>>, mut changes: ResMut<Changes>| {
                    changes.0 += nodes.iter().count();
                },
            )
                .chain(),
        );
        let parent = app
            .world_mut()
            .spawn(ComputedNode {
                size: Vec2::new(1000., 800.),
                inverse_scale_factor: 1.,
                ..default()
            })
            .id();
        app.world_mut().spawn((
            ResizablePanel::new(PanelAxis::Width, 280.),
            Node::default(),
            ChildOf(parent),
        ));
        app.update();
        let count = app.world().resource::<Changes>().0;
        app.update();
        assert_eq!(app.world().resource::<Changes>().0, count);
    }
    #[test]
    fn native_drag_resizes_in_both_directions_without_overshoot_debt() {
        use bevy::picking::pointer::{Location, PointerButton, PointerId};
        for (axis, edge, first_delta, expected) in [
            (
                PanelAxis::Width,
                SplitterEdge::After,
                Vec2::new(100., 0.),
                380.,
            ),
            (
                PanelAxis::Height,
                SplitterEdge::Before,
                Vec2::new(0., -100.),
                380.,
            ),
        ] {
            let mut app = App::new();
            app.add_systems(Update, layout);
            let parent = app
                .world_mut()
                .spawn(ComputedNode {
                    size: Vec2::new(1000., 1000.),
                    inverse_scale_factor: 1.,
                    ..default()
                })
                .id();
            let panel = app
                .world_mut()
                .spawn((
                    ResizablePanel::new(axis, 280.),
                    Node {
                        width: px(280.),
                        height: px(280.),
                        ..default()
                    },
                    ChildOf(parent),
                ))
                .id();
            let handle = splitter(&mut app.world_mut().commands(), parent, panel, axis, edge);
            app.world_mut().flush();
            let send_drag = |app: &mut App, delta| {
                app.world_mut().trigger(Pointer::new(
                    PointerId::Mouse,
                    Location {
                        target: bevy::camera::NormalizedRenderTarget::None {
                            width: 1000,
                            height: 1000,
                        },
                        position: Vec2::ZERO,
                    },
                    Drag {
                        button: PointerButton::Primary,
                        distance: delta,
                        delta,
                    },
                    handle,
                ));
                app.update();
            };
            send_drag(&mut app, first_delta);
            assert_eq!(
                app.world().get::<ResizablePanel>(panel).unwrap().preferred,
                expected
            );
            send_drag(&mut app, first_delta * 100.);
            send_drag(&mut app, first_delta * -0.1);
            assert!(
                (app.world().get::<ResizablePanel>(panel).unwrap().preferred - 590.).abs() < 0.001
            );
        }
    }
}
