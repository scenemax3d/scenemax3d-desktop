# UI designer preview

Open a `.smui` document in the project navigator. The canvas fits the document's saved design resolution inside the editor. Use the hierarchy arrows to expand or collapse layers and nested widgets. Canvas selection reveals the selected widget in its tree branch. The arrow beside Properties collapses the panel without losing the current field focus. The Add toolbar uses compact icons with hover labels. Select a widget in the canvas or hierarchy, edit its inspector fields, then save. Valid property edits update the preview immediately. A continuous typing or slider gesture is grouped into one undo operation; saving starts a new undo group. Incomplete or invalid values retain the last valid preview without stealing focus.

The preview uses the runtime UI schema and constraint solver. Images resolve through the project's sprite registries, including sprite-sheet frame cropping and built-in resources. BMFont parsing and glyph placement are shared with the game in `Engine/runtime_ui/src/bitmap.rs`. The inspector offers registered font and sprite choices, numeric sliders, visibility, size modes, margins, padding, biases, constraints, text, button, image, edit-text, and list fields. List headers and rows use Java's `|`-separated cell format.

Text, layout, and style edits reuse the cached assets and retained canvas widgets in the same frame. New font and sprite references resolve off the UI thread. Imported properties not understood by the renderer survive edits and saves. Renaming a widget updates constraint references; invalid numeric values are rejected before changing the document.

## Canvas navigation

- Mouse wheel: zoom around the pointer (5%–800%).
- Right- or middle-button drag: pan.
- Shift+wheel: scroll; horizontal wheel/trackpad motion pans horizontally.
- **− / +**: zoom around the center; **100%**: actual design size; **Fit**: recenter and fit the viewport.

The displayed percentage follows the actual canvas scale. Navigation stays with the open document across property changes and does not modify or dirty its source. Left-click continues to select widgets.

## Preview scope

This is the saved UI state at its design resolution, without running game scripts or rendering a game scene behind the transparent overlay. Script-controlled timers, scores, visibility, and animation require a game run. The preview deliberately follows the current Bevy runtime: panels are transparent layout containers, edit-text widgets render as text, and list views use the runtime's textual representation. Java-specific list styling, edit-text interaction colors, and multiplayer behavior are not newly implemented in the runtime by this change; compatible inspector data is retained in the document.

## Verification

Run from `scenemax3d_nextgen`:

```powershell
cargo test --locked -p scenemax_ide -p scenemax_ide_services -p scenemax_ide_core -p scenemax_runtime_ui_core --lib
python Tools/check_architecture.py
cargo run --locked -p scenemax_ide_services --example ui_preview_report -- <project-directory> <smui-file>
```

The report resolves assets without opening a window and lists missing-asset diagnostics. Asset files are restricted to their owning resource roots.

Runtime-hidden widgets remain visible and selectable in the designer, with an amber dashed frame. This also indicates visibility inherited from hidden parents or layers; saved visibility and game rendering are unchanged.
