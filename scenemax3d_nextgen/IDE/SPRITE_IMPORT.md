# Sprite import designer

Open **Assets → Import Sprite**. Browse a PNG/JPEG or paste a path and choose **Load / Reload**. Nothing is registered while previewing; **Import sprite** publishes the resource and saves the import draft.

The document offers a checkerboard sheet view with live frame outlines, highlighted selection, clickable frames, thumbnails, independent sheet/animation zoom and pan, and a collapsible property panel. Use the mouse wheel to zoom, middle/right drag to pan, and Fit/100% to reset inspection. The thumbnail strip supports horizontal wheel scrolling; sheets over 128 frames remain fully selectable in the main view.

## Layout and runtime parity

Rows and columns define row-major frame order, starting at the top left. Cell width/height are source pixels; 0 calculates the cell size from the image, grid, symmetric margins and gutters. Invalid or clipped rectangles are rejected. Margin and spacing edits immediately update the preview. Import removes margins/gutters and writes a uniform RGBA PNG sheet so runtime UVs match the selected source rectangles. Alpha is preserved and the original image stays unchanged.

Game display width/height retain the Java importer's resource semantics. A zero display height preserves the cell aspect ratio. An explicit height updates the animation preview's display aspect. Source pixel dimensions are separate from game display units.

## Animation inspection

Use Play/Pause/Stop, previous/next frame, first/last frame (zero based), preview FPS, and Loop/Play once/Ping-pong. Use all frames resets the preview range. Preview FPS, range and nearest/smooth filtering do not alter script-controlled game animation. All sheet frames are imported.

Drafts live under `.scenemax-studio/imports/Import Sprite.smspriteimport`; ordinary Save/Undo/Redo apply. Images are decoded on workers, and import packs/registers them on a worker. Duplicate names and failed validation do not overwrite existing resources. Closing the document releases its decoded pixels and image handles.

## Implementation

- `IDE/core/src/sprite_import.rs`: validated grid geometry, pixel hit testing and playback timing.
- `IDE/services/src/imports/sprite.rs`: bounded decoding, immutable source snapshots, alpha-preserving packing and atomic import registration.
- `IDE/app/src/presentation/sprite_import/`: retained form, document transactions, Bevy UI previews and transport.
- `IDE/ui/src/canvas.rs`: shared zoom/pan surface that can resize without losing navigation state.

Java reference: `src/com/scenemax/desktop/ImportSpriteSheetDialog.java`. Its source/name/rows/columns/frame-size controls are supported. The separate game-launch Test workflow is replaced with embedded previews that do not temporarily register assets or write test scripts.
