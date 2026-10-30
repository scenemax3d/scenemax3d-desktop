# Retained Bevy UI designer hosting

## Architectural commitment

SceneMax Studio's shell, menus, text input, project navigator, toolbars and panel infrastructure use native retained Bevy UI entities. UI, scene, material and other designers integrate into that same infrastructure. The IDE remains an independent product under `IDE`, alongside `Projector`; designers must not depend on the projector executable or Java/JME embedding bridges.

Renzora's [Ember entry point](https://github.com/renzora/engine/blob/main/crates/renzora_ember/src/lib.rs) separates native UI widgets, panel infrastructure, docking, styles and reactive behavior. It is an architectural reference. SceneMax owns its panel primitives. Reused third-party assets or code must preserve their license and attribution; see the [IDE credits](../IDE/THIRD_PARTY_CREDITS.md).

## Implemented hosting contract

`IDE/ui::panels` provides:

- `tool_panel`: a retained root, header toolbar and clipped content surface. Feature plugins own the children of their own content surface.
- `PanelHost`: a stable tool identifier attached to that content surface. Existing project-tree and script-editor hosts remain owned by their respective features; another tool must create its own panel rather than adding children that their refresh lifecycle would remove.
- `PanelViewport`: logical content extent and physical pixel extent, measured after Bevy layout. Consumers observe changes and suspend render-target work when the extent is zero. Inactive designer tabs must hide their host with `Display::None` so layout reports zero size.
- `ResizablePanel` and `splitter`: bounded layout changes through native pointer picking. Resizing and resetting panel dimensions retain the existing content entities and their state.
- `ResetPanelLayout`: a toolbar/menu button marker to restore initial extents.

The project divider changes sidebar width. The divider above Run / Output changes output height. Sizes are currently session-local. The splitter preserves room for the document area as the window shrinks. Stable sizes do not dirty layout on every frame.

## Attaching native designer UI

A designer plugin should create its tool panel during shell composition, then add ordinary Bevy `Node`, `Text`, `ImageNode`, input and picking components under `ToolPanel.content`. Keep its document/model state separate from those entities. Update changed properties in place; never recreate a designer hierarchy on every keystroke or resize. Header actions dispatch the designer's undoable application commands.

A UI designer's canvas can directly host the authored Bevy UI hierarchy under the panel. Selection bounds, handles and editor controls belong to editor-owned entities and must not enter the saved/exported UI model. Input focus and text composition must be scoped so script shortcuts do not affect a focused designer field.

For a 2D/3D scene viewport, the designer owns a camera and render-target image displayed by an `ImageNode` in its panel. Use `PanelViewport.physical_size` to size the image and logical geometry to map panel-local input. Isolate preview entities/cameras with explicit render layers and ownership markers; hide/pause inactive views; release camera/image/entity resources on tool closure.

## Document lifecycle and validation

Designers share the IDE document tabs, dirty state, undo history, Save, recovery, and close guards. UI and 3D previews stay inside the IDE's Bevy app. The separate projector is a game execution service, not a designer host.

Verify native pointer input, layout constraints, reset without child recreation, DPI-aware viewport measurements, and inactive-view cleanup. GPU captures complement interaction checks; they do not replace them. See the [IDE guide](../IDE/README.md) for the available designers.
