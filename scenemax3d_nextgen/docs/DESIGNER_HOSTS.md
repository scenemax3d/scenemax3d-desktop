# Retained Bevy UI designer hosting

## Architectural commitment

SceneMax Studio's shell, menus, text input, project navigator, toolbars and panel infrastructure use native retained Bevy UI entities. Future UI, scene, material and other designers must integrate into that same infrastructure. The IDE remains an independent product under `IDE`, alongside `Projector`; designers must not depend on the projector executable or Java/JME embedding bridges.

Renzora's [Ember entry point](https://github.com/renzora/engine/blob/main/crates/renzora_ember/src/lib.rs) separates native UI widgets, panel infrastructure, docking, styles and reactive behavior. It is an architectural reference. This milestone implements SceneMax-owned panel primitives; it imports no Renzora code or engine dependency. Any later source extraction must pin the upstream revision and preserve its license and attribution.

## Implemented hosting contract

`IDE/ui::panels` provides:

- `tool_panel`: a retained root, header toolbar and clipped content surface. Feature plugins own the children of their own content surface.
- `PanelHost`: a stable tool identifier attached to that content surface. Existing project-tree and script-editor hosts remain owned by their respective features; another tool must create its own panel rather than adding children that their refresh lifecycle would remove.
- `PanelViewport`: logical content extent and physical pixel extent, measured after Bevy layout. Consumers observe changes and suspend render-target work when the extent is zero. Inactive designer tabs must hide their host with `Display::None` so layout reports zero size.
- `ResizablePanel` and `splitter`: bounded layout changes through native pointer picking. Resizing and resetting panel dimensions retain the existing content entities and their state.
- `ResetPanelLayout`: a toolbar/menu button marker to restore initial extents. The extra prototype View menu entry has been removed to preserve the Java menu structure.

The project divider changes sidebar width. The divider above Run / Output changes output height. Sizes are currently session-local. The splitter preserves room for the document area as the window shrinks. Stable sizes do not dirty layout on every frame.

## Attaching native designer UI

A designer plugin should create its tool panel during shell composition, then add ordinary Bevy `Node`, `Text`, `ImageNode`, input and picking components under `ToolPanel.content`. Keep its document/model state separate from those entities. Update changed properties in place; never recreate a designer hierarchy on every keystroke or resize. Header actions dispatch the designer's undoable application commands.

A UI designer's canvas can directly host the authored Bevy UI hierarchy under the panel. Selection bounds, handles and editor controls belong to editor-owned entities and must not enter the saved/exported UI model. Input focus and text composition must be scoped so script shortcuts do not affect a focused designer field.

For a later 2D/3D scene viewport, the designer will own a camera and render-target image displayed by an `ImageNode` in its panel. Use `PanelViewport.physical_size` to size the image and logical geometry to map panel-local input. Isolate preview entities/cameras with explicit render layers and ownership markers; hide/pause inactive views; release camera/image/entity resources on tool closure. This camera adapter is a subsequent implementation, not a completed renderer in this milestone.

## Next increments

1. A designer document/tab lifecycle alongside script documents, including dirty-state, undo and close guards.
2. A native UI authoring canvas with hierarchy, selection and property inspector using the same panel widgets.
3. An isolated render-target camera adapter with resize, focus and picking tests.
4. Reorderable/dockable tool panels and persisted layout, followed by keyboard/accessibility acceptance.

Tests cover native pointer-drag dispatch, layout constraints and reversal at bounds, reset without child recreation, DPI-aware host measurements, and avoiding redundant layout dirtiness. GPU startup capture remains complementary to interaction tests.


## Confirmed migration order

First complete Java IDE parity: project selection/file editing and individual/project runs, then editing capabilities including syntax highlighting. The next designer is the UI scene designer. It must open as a designer document within the IDE's Bevy app, sharing the document tab/dirty-state/undo lifecycle. All subsequent designers follow that in-process document model. The separate projector remains a game execution service; it is not the host for designer windows. New menu concepts and features beyond Java parity wait until that phase is complete.
