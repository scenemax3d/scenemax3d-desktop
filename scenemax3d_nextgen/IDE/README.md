# SceneMax Studio — native IDE

Material authoring: **Assets → Create New Material…** opens the retained [Material Studio](MATERIAL_EDITOR.md): a visual PBR composer with texture channels, presets, live primitive/model previews, named-slot assignment, undo, and shared runtime rendering.

Latest distribution slice: **Tools → Package & Deploy…** provides retained release settings, desktop packaging, phase progress, live logs, cancellation and Butler publishing/retry. Windows is a single Java-free executable; Linux/macOS use ZIPs. Android/iOS/Web expose explicit SDK-builder integration, with runtime-port limitations described in [DEPLOYMENT.md](DEPLOYMENT.md). This supersedes older statements below that all packaging is deferred.

SceneMax Studio is an independent Rust/Bevy IDE. The current milestone supports everyday SceneMax script editing and run/check workflows; visual designer and distribution parity with Swing are still separate migration work.

## Launch

From the repository root:

```powershell
.\run-rust-ide.ps1 -ProjectRoot "C:\path\to\project"
# Included sample:
.\run-rust-ide.ps1 -ProjectRoot .\scenemax3d_nextgen\Tests\fixtures\ide\sample_project -Script scripts/main
```

From `scenemax3d_nextgen`, use `cargo run --locked -p scenemax_ide -- --project-root C:\path\to\project`. Build `scenemax_projector_nextgen` separately to use Run; `--projector` selects a different executable. Normal builds and both executables require no Java.

## Java IDE parity workflow

The menu bar follows the Java IDE's `assets/menu/main_menu`: **File, Assets, Tools, View, Git, Help**, including **File → Projects**. The menu captions, nesting and command IDs are recorded in `presentation/menu_catalog.rs`; `Tools/check_java_menu_parity.py` audits names/order/IDs against Swing. Commands whose features are not yet ported are dimmed and inactive. Matching the menu structure does not mean full behavioral parity is complete.

Launch `./run-rust-ide.ps1` without `-ProjectRoot` to load the project selected in the existing `projects/projects.json` catalog. The catalog is read on a background worker and is never rewritten by this adapter. Bevy project selections are currently session-local; a new launch restores the catalog's selection. Explicit `-ProjectRoot` takes precedence. Direct binary launches can pass `--project-catalog <path>`; otherwise discovery searches ancestors of the requested/current folder for `projects/projects.json`.

Choose a project by name under **File → Projects**, or use **File → Projects → Project Explorer...**. The explorer filters projects by name and also accepts a project directory typed into its path field. On successful selection, the tree and title update, and the project's main script opens with its ancestor folders expanded. Save dirty buffers and stop a running projector before switching; the same checks run again after asynchronous loading completes.

Click files in the project tree to edit them. The tree includes scripts, resources, empty folders and other project files. Native text editing currently supports UTF-8 text; binary files and visual designer documents do not yet have specialized editors. Script tabs retain their edits/caret while inactive. Each tab's × button uses the existing save/discard/cancel guard.

## Editing and running

- **Run file / F8** runs the active `.code` or extensionless script. **Ctrl+F12** is the Java Bevy equivalent. Save other dirty scripts first. Non-script files cannot be run accidentally.
- **Run project / F10** runs the shallowest extensionless `main` beneath `scripts/`, as Java does. **F12** is the Java Bevy equivalent. Equal-depth candidates use stable path order. Dirty buffers are saved first, so included files are current; edits arriving during the save cancel the deferred launch. An active tab is not required.
- **Stop** cancels an already-running projector or a run waiting for saves. F5/Shift+F5 remain compatibility shortcuts for run-file/stop.
- **Save / Ctrl+S**, **Save all / Ctrl+Shift+S**, Undo/Redo, **Check syntax / Ctrl+Enter**, and Find are available in the toolbar or through keyboard shortcuts. The extra Edit/Navigate/Run menus from the prototype have been removed to match Java.
- **Ctrl+F** opens literal find/replace; F3/Shift+F3 move between matches. Ctrl+G opens go-to-line. Ctrl+N or the tree's + button opens script creation/save-copy. Tab/Shift+Tab indent/unindent; Ctrl+/ toggles line comments.

Project-wide search uses unsaved buffers and scans at most 64 MiB, returning up to 100 matching lines. It rejects stale results after a buffer changes. Undo history is session-local and stores compact differences (up to 1,000 transactions with an 8 MiB target). Automatic typing coalescing remains future work.

Drag the divider beside Project or above Run / Output to resize panels. Sizes are session-local. Reset-size infrastructure remains available to future settings UI, but its extra prototype View menu entry was removed for Java menu parity. See the [retained designer hosting contract](../docs/DESIGNER_HOSTS.md).

The immediate next milestones are the remaining editing parity work (including project-wide semantic completion and large-document performance), then the UI scene designer as an embedded IDE document. All designers will share the IDE's Bevy app and retained UI; standalone designer processes are not the migration target.


SceneMax syntax highlighting uses the Java editor's 252 vocabulary entries and token palette, plus strings, comments, numbers, booleans, operators and punctuation. It tolerates incomplete code and colors unterminated quotes separately. `Tools/check_java_syntax_parity.py` checks vocabulary drift. This is lexical coloring, separate from **Check syntax** validation; Java-specific syntax mode is not ported.

Coloring extends Bevy's native glyph extraction using Parley's visual clusters. It does not duplicate text, change its layout or replace `EditableText`. Highlighting only reclassifies changed text; glyph color mappings are cached until source/layout changes. During IME composition the editor temporarily uses its normal foreground color, preserving Bevy's preedit rendering. Large documents still require full-buffer scanning and shaping; incremental lexing and viewport virtualization remain performance work.

To preview the palette from the repository root:

```powershell
.\run-rust-ide.ps1 -ProjectRoot .\scenemax3d_nextgen\Tests\fixtures\ide\syntax_project
```

The syntax fixture exercises display categories and Unicode; it is an editor preview, not a runnable scene. GPU inspection displayed Latin, Hebrew and Japanese text. The current dependency stack emitted an ICU4X missing Japanese segmentation-model diagnostic; Japanese word-navigation/IME acceptance still requires follow-up.

## Enter indentation and bracket matching

Enter preserves the leading tabs/spaces at the selection start and adds four spaces after `do`, `then`, `else`, `(`, `[` or `{`. Quoted words and comments do not open scopes; a trailing comment after a real opener is allowed. The native editor replaces selections, enforces character limits and places the caret. Newline plus indentation reaches document history as one undo transaction. Closing delimiters are not inserted automatically, and closing lines are not automatically dedented yet.

Pairs of `()`, `[]` and `{}` receive subtle outlines when the caret is beside either bracket. Strings, comments and malformed nesting are excluded. Selections and active IME composition suppress outlines. Matching uses a cached lexical index and byte-safe positions, while the reusable UI draws outlines with native glyph transforms and clipping.

Paste and IME batches remain entirely with Bevy's normal input handling. If Enter arrives in the same batch as paste or an IME event, it keeps native newline behavior for that batch. This avoids modifying pasted content or composition. Code inputs with a custom character filter likewise retain native handling.

Preview bracket outlines with `-Script scripts/assistance.code` in the syntax fixture. Its initial caret is beside a matching pair. It is a display fixture, not a runnable scene.

## Code completion

Press **Ctrl+Space** in an editor, or type two prefix characters. A native retained popup lists matching Java-compatible keywords, built-in functions, colors, effects and input keys. Built-ins insert their `()` suffix, matching Java. The current unsaved document also supplies lexical `var`, `shared` and `function` declarations, including Unicode names. `@` prefixes suggest local and indexed project declarations. **Up/Down** changes the selection, **Enter/Tab** inserts, **Escape** dismisses, and clicking a row accepts it.

Accepting verifies the source and caret snapshot and edits through Bevy's native text queue; Undo restores the typed prefix. Completion closes on caret movement, focus loss and IME activity. Strings and comments suppress suggestions. Paste stays on the normal input path. Lists are capped at 100 candidates with eight visible rows; arrows reveal later rows. No new top-level menus were added.

A dedicated Rust parser worker indexes top-level functions, assignments, models and lights across the project script inventory. Completion exposes the project `main` and its transitive `Add … Code` includes, plus the active document’s include closure; unrelated scripts are excluded. Relative paths, leading `/` and `.code` fallback follow the projector’s conventions. Cycles are visited once, and include resolution only uses the validated inventory. Unsaved open buffers override disk contents, and obsolete scan results are discarded. Changes debounce for 350 ms; unchanged source avoids reparsing. Use the existing Refresh action after external disk edits. The popup shows indexed/skipped files, unresolved include targets and scan limits (2,000 files, 1 MiB per source, 32 MiB total, 20,000 symbols). Parser failures or oversized sources contribute no stale declarations. The current runtime parser tolerates some unsupported syntax, so an indexed file is not a syntax-validation guarantee.

This is not full Java semantic parity. The Java editor also resolves included project files, scene resources, parameters and expression-pointer types. Resource resolution and scope/type analysis remain to be implemented; Java reflection completion is not ported. Local declarations currently use lexical recognition and are not scope/type checked. Completion still scans the current buffer, so incremental indexing remains performance work.

For a focused preview, launch the syntax fixture with `-Script scripts/completion.code`, move to the end and press Ctrl+Space. Controlled GPU runs support `--smoke-completion`. The catalog audit is `Tools/check_java_completion_parity.py`.

## Saves and recovery

Project scans, loads, searches, saves and checkpoints run on one bounded disk worker. Typing continues during a save: the submitted snapshot is written, while newer edits remain unsaved. A second disk action reports busy and can be retried after completion. Saves preserve UTF-8 BOM/newline conventions and detect changes to the original disk bytes before replacing a file.

Dirty buffers are checkpointed approximately every two seconds when the disk worker is free, under `.scenemax-studio/recovery/` in the project. These immutable checkpoint files are Git-ignored and do not change scripts. An interrupted session can lose edits made since the last successful checkpoint; failures appear in the status area.

Reopening the same project offers **Restore buffers** or **Discard recovery copies**. Restoration preserves the original save baseline, so external edits still cause a conflict. Review restored text before saving; use Save copy if the original has changed. Restoration replaces the current clean tabs; save current dirty buffers first. Accepted checkpoints are retired only after their replacements are durable. Checkpoints are bounded to 64 MiB; damaged journals, unsupported versions, or moved/deleted source paths report an error and leave recovery files intact for inspection.

## Current scope and validation

Files load up to 8 MiB; native input is capped at roughly two million characters. Source inventories remain capped at 2,000 scripts. The project tree is bounded at 10,000 entries / 32 levels and reports truncation; it excludes `.git`, `target`, `node_modules`, recovery journals, symlinks and Windows junctions. Filesystem traversal stays on the storage worker. Large text still uses native full-buffer layout rather than a virtualized code renderer. Resource/type-aware semantic completion, regex search, file rename/delete, file watching/merge, persistent clean tabs/recent-project lists, designer panels and packaging remain upcoming. Keyboard/IME/DPI/accessibility acceptance and measured Swing performance comparisons remain necessary before full replacement.

Run `./Tools/verify.ps1 -Build` from the workspace for architecture, formatting, strict IDE Clippy, whole-workspace checks, library tests and independent builds. Controlled GPU capture is available through `--smoke-frames 150 --smoke-screenshot target/ide-smoke.png`. Add `--smoke-menu` to capture the open File menu, or `--smoke-projects` for Project Explorer. On isolated fixtures, `--smoke-run-project` verifies project execution after loading. Source/domain/application tests and startup screenshots are complementary; a screenshot does not establish complete interaction parity.

Read [architecture and engineering standards](../docs/ARCHITECTURE.md) before extending the editor.


## Embedded UI scene designer — first slice

Open an existing `.smui` file in the project tree. It opens as a native retained Bevy UI designer tab inside the IDE, with a layer/widget hierarchy, a canvas fitted to its viewport and an inspector. Select widgets in the hierarchy or canvas. Add Panel, Text or Button to the selected panel (otherwise the selected layer); Delete selected removes its subtree. Valid property edits update the preview immediately while preserving field focus and the canvas view. Incomplete numeric input keeps the last valid preview. Continuous field edits form one undo transaction; **Save**, **Save All**, undo/redo, dirty-tab prompts and recovery use the existing document lifecycle. The JSON source retains unsupported fields and constraints when edited. The Check syntax action validates the UI schema and layout for designer documents.

Try from the repository root:

```powershell
.\run-rust-ide.ps1 -ProjectRoot .\scenemax3d_nextgen\Tests\fixtures\ide\ui_project -Script ui/welcome.smui
```

This first slice previews panels, text and buttons using the runtime's pure constraint solver. Other widget types are labeled placeholders, and custom fonts/assets, interactive runtime behavior, drag/resize handles, constraint editing, layer management, file creation and generated `.code` sidecars remain to be implemented. This is not full Java designer parity. Width/height edits preserve size modes and constraints, which may override the requested size. Preview input is bounded to 1 MiB, 256 widgets and a limited JSON nesting depth. Source formatting may change on the first edit; unchanged documents are not rewritten. Invalid documents display a preview error without modifying their source.


## 3D scene documents (.smdesign)

`.smui` is a UI layout; `.smdesign` is the Java 3D scene designer format. Existing `.smdesign` files now open in an embedded Bevy `ViewportNode` with a 3D camera, hierarchy and transform inspector. A dedicated filesystem worker parses the document and resolves project glTF/GLB model resources through Common/assets. The view loads those assets asynchronously and reports model loading/failure counts. Boxes, spheres and quads have neutral preview materials. The initial view uses a neutral overview. The Saved camera button restores the Java editor camera with its forward axis converted for Bevy; Overview returns to the initial view. Drag the viewport to orbit and scroll to zoom; selecting a hierarchy entry focuses its position.

This import is **read-only**. Embedded code, cinematic rigs, animation, environment shaders, skyboxes, original primitive materials, physics and companion script generation are not applied. Unsupported entries remain visible in the hierarchy. Source JSON is not rewritten, and script editing commands are rejected for these documents. The viewport and its owned world are removed when switching away or closing the document. A scoped project asset source resolves model files and their relative textures/buffers within registered resource roots. Project identities stay distinct across switches, paths escaping a resource root are rejected, and global unapproved asset loading remains forbidden.

Example from the repository root:

```powershell
.\run-rust-ide.ps1 -ProjectRoot .\projects\fighting_game_project_-_bevy -Script "scripts/Fighting Game/game_level1/game_init.smdesign"
```

### Scene hierarchy and transform fidelity

Scene sections and cinematic rigs now retain their nested children in document order. The scene tree uses a reusable retained UI row component with separate disclosure and selection targets, 24 px rows, indentation, single-line labels and dimmed hidden entries. Branches start collapsed and keep expansion state while selecting entries. Organizational sections do not multiply child world transforms.

Saved model scale overrides resource defaults, matching Java's `setLocalScale(savedScale)` during document restoration; resource scale is used only when the saved scale is absent. The previous preview multiplied both and could make models much too small or large. Saved scene position/rotation are likewise the restored node transform, without adding resource transforms a second time. Embedded scripts, attachment behavior, animation, materials and environment parity remain outstanding.

### Designer visibility and project scrolling

The 3D designer displays runtime-hidden objects as well as visible objects. Hidden flags remain unchanged in the source and are shown as runtime metadata in the inspector/tree. The runtime/projector behavior is unchanged.

The project navigator now has a native vertical scrollbar with a draggable thumb and track paging. Wheel and touchpad events over child rows scroll the containing viewport, with line/pixel units and bounds respected. The viewport is constrained to the remaining sidebar height, so long file trees scroll rather than extending below the window.

### Compact designer and transform gizmo preview

The oversized scene heading, camera toolbar and permanent status footer have been removed. Asset progress/errors appear in the inspector only while loading or on failure. Run / Output and its splitter are hidden while no projector is running; captured logs remain in the session.

The compact Move / Rotate / Scale toolbar controls Renzora-derived, always-on-top mesh gizmos. Select a scene entry in the hierarchy, then left-drag an axis handle. Right-drag orbits and scrolling zooms. Handles maintain a consistent apparent size, highlight on hover, and update transform readouts during dragging. Escape cancels an active drag; Reset restores the selected entry's imported transform. The adapter reuses licensed Renzora geometry proportions, axis palette, shader/material and analytical picking helpers; attribution and MIT license are in IDE/third_party/renzora_gizmo.

Transform manipulation now edits the `.smdesign` document live. Java-compatible companion script generation remains a separate required step. This adapter currently supports world-axis move/rotate and object-axis scale with perspective viewport picking; multi-selection, planar handles, snapping and local-space modes remain outstanding.

### Main toolbar removal and mouse focus

The toolbar above document tabs has been removed. File now includes Save, Save all, Undo, Redo and Find; Tools includes Check syntax, Run project, Run file and Stop. Project Explorer remains under File / Projects. These explicitly requested action additions supplement the preserved Java menu catalog. Existing keyboard shortcuts are unchanged.

Code editor widgets now declare a tab index, so Bevy's click-to-focus traversal retains keyboard focus after native pointer caret/selection handling. The regression test exercises native pointer presses and focused keyboard dispatch together, including double-click selection.


### Editable 3D inspector and cinematic rigs

The retained inspector edits valid `.smdesign` properties live. Save, Save all, Undo and Redo use the normal document/storage pipeline, including conflict detection. Unknown JSON properties, embedded source, IK data, and nested hierarchy are retained. An unchanged form is a no-op; stale drafts are rejected. Changing one scale axis with **Proportional scale** enabled preserves the original ratios. Rotation uses JME-compatible YZX composition and displays X/Y/Z degrees. Viewport move/rotate/scale updates the property fields and document automatically; the reset-arrow control invokes Undo.

Property controls include name, attachment target, position, rotation, scale, hidden and multiplayer flags, project model/shader/material/IK choices, shadow and model collision modes, static/dynamic/vehicle flags, joint mapping text, and existing IK layer play/target/blend/weight settings. These authoring settings are serialized using the Java keys. Hidden objects remain visible in the designer. The inspector scrolls independently of the hierarchy and viewport.

Cinematic rigs render their child tracks using the Java local transforms; SECTION entries remain organizational. Tracks include their XZ ellipse, anchor markers, and selected arc. The inspector edits radii, anchor count, start/end, speed, rig target/offset, easing, duration, and segment values. A track's saved range can be appended to its owning rig; segments can be reordered and removed. **Play** previews within the existing document viewport. Segment duration weights, forward wrapping, interpolation, easing, and look-at behavior follow the Java implementation; Stop/completion restores the editor camera. Property reloads preserve the editor viewpoint and selected entry.

This is not yet full Java scene-designer parity. `.smdesign` changes persist, but Java's companion `.code` generation has not been ported: running the existing generated script will still use its previous contents. The Bevy viewport does not yet execute Java shader/material definitions, joint attachments, IK solving/animation playback, or collision previews. The specialized joint-mapping dialog, IK layer creation, rig/track creation, direct anchor picking, and the remaining Java primitive/light/path designers still need ports. Existing data for these features is preserved. The Play checkbox in an IK layer is its serialized runtime setting, not an implemented IK simulation preview.

Regression coverage includes lossless nested edits, proportional scaling/rotation validation, native editable-field transactions and stale rejection, scene Save/Undo/Redo through application commands, Java ellipse anchors/easing, and gizmo drag behavior with parented scene objects. GPU capture can select a flattened hierarchy entry using `--smoke-scene-entry INDEX` together with `--smoke-frames` and `--smoke-screenshot`.

### Stable interaction rendering

UI changes are committed before Bevy's layout pass, including native text edits before Save commands. Menu, panel, tab and selection systems avoid marking unchanged layout values dirty. Selecting a scene object preserves the existing viewport, camera and hierarchy rows; only the inspector changes. Expanding a scene branch toggles retained row visibility. Clicking the selected object preserves unfinished property edits. Single-click selects; double-click a 3D hierarchy row to frame the object. UI scene selection likewise retains the canvas widgets.

Regression tests cover layout timing, idle-click stability, retained scene entities/property drafts, and saving native input queued in the same frame.

### Designer toolbar and window polish

The scene toolbar follows the Java Add order: sphere, box, wedge, cylinder, cone, hollow cylinder, quad, stairs, arch, model, point light, path and cinematic rig. Copy, paste and delete use document history. New identities are generated and internal subtree references are remapped on paste. Select a section to add into it; select a cinematic rig and use the cinematic tool to add a rail. Models come from the project's resource catalog. New content is placed at the editor work-plane target. Save persists `.smdesign`; companion script generation remains outstanding as described above.

Path drawing uses the horizontal plane through the camera target: click to place points, double-click to finish, Escape to cancel. Imported Bezier tangents are rendered. The camera controls select right-drag orbit or pan. The corner orientation widget supports drag-to-orbit and signed axis clicks. Ambient settings are edited in-document and previewed on its camera. Design lighting can be toggled independently of authored point lights.

Toolbar strokes are rasterized at four times their logical size with smooth coverage and linear filtering. Numeric scene properties have pointer sliders alongside precise text input; valid slider and field edits update the scene document immediately. The menu now occupies the client title bar with native window move/resize requests, minimize/maximize controls, and the IDE's existing protected close action.


### Live 3D properties and viewport selection

Valid property changes update the current scene object and document without rebuilding the viewport or losing input focus. Incomplete/invalid numeric input leaves the last valid scene state intact and reports validation in the status bar. Gizmo changes update local position/rotation/scale fields in the same frame. Consecutive edits within a gesture share one undo transaction; save snapshots and unrelated edits break continuation. Ctrl+S persists the live document. Asset replacement uses the asynchronous loader; authoring metadata unsupported by the renderer retains the limitations listed above.

Click a model or primitive in the viewport to select its scene owner. Mesh descendants of imported models resolve to their owning entry; editor gizmo meshes are excluded. The corresponding hierarchy row is selected and collapsed ancestors are expanded. Orientation controls and path drawing keep their separate interactions. Ordinary selection and property edits retain the viewport and camera.

### Cinematic rig table and document activation

The cinematic rig inspector provides a Rail / Start / End / Weight grid. Start, End and Weight cells edit the live document; Rail names are read-only, matching Java. Select a row before using the reorder/remove controls. Add Rail creates a child track. Ease In/Out show Java's descriptive choices while preserving the serialized easing IDs. Weight retains Java's `speed` field in the scene format. The inspector is wider to accommodate the grid.

Project files open on a primary-button double-click or Enter; a single click focuses/selects the row. Folder expansion remains available on a single click. The exact Sinbad menu icon is embedded in the Rust executable from the IDE's own asset copy.

### Game camera preview and designer navigation

Every scene exposes its Java `gameCamera` as a permanent Game Camera hierarchy entry, with Java defaults for older documents. Its selectable body and frustum appear in the editor, and a bottom-right retained viewport renders the scene using its position and rotation. JME +Z camera orientation is converted to Bevy -Z for rendering. Property and gizmo edits retain Java's separate `gameCamera` object, unknown metadata, and normal Save/Undo behavior. The camera is not duplicated into the entities array and cannot be deleted/copied as an ordinary object.

Single-click a spatial scene hierarchy entry to animate the editor camera toward it over 450 ms. Orbit/pan/zoom interrupts that movement. Sections and code entries do not move the camera. Use the chevron beside the properties panel to collapse or expand it; controls and in-progress edits remain retained while hidden.

Game Camera is pinned immediately below the scene hierarchy heading, outside the scrollable object list. It remains available in long scenes and while folders are collapsed. Selecting it uses the same selection path, Move/Rotate handles, and live position/rotation fields as other spatial objects. The object list now has a visible draggable scrollbar.

### Java artwork and two-tone chrome

Project-tree file types reuse the Java IDE images and exported Java2D artwork, including yellow folders, blue main-script braces, amber scene cubes, cyan UI layouts, and the specialized designer icons. Scene toolbar actions use the original Java drawing assets at 16 logical pixels within compact 24-pixel controls. The assets are embedded PNGs; their source provenance is recorded in `app/src/presentation/java_icons/README.md`. No Java runtime or build step is required.

Document tabs use 10-pixel text and a 28-pixel strip. A dark neutral gray header sits above lighter gray content/tool panels, with the Java-style blue selection color.

### Run-output responsiveness

The run console renders at most the latest 200 lines / 24 KiB, independently of retained diagnostic history. Output uses unwrapped text so long runtime messages cannot expand into thousands of visual lines. On projector exit, the hidden console text is cleared before layout and the status bar reports the exit result. This bounds rendering work during noisy runs and avoids re-laying out a large log when its panel collapses. The projector's project-local runtime log is unchanged.

### Project tree context menu

Right-click a project file or folder to select it and open its native retained Bevy UI context menu. Single-click selection and double-click document opening remain unchanged. Escape or a click outside dismisses the menu; long folder menus support wheel scrolling.

The menu uses the Java IDE's contextual labels and ordering. Working actions include Run (for runnable scripts), Save, Reload from disk, Refresh Project Files, Copy absolute path, Open in explorer, Rename, Move To, Delete, Add Scene, Create New Script, Create Designer Document, Create UI Document, and Create Sub Folder. Actions target the clicked path, independently of the active tab. Add Scene creates a scene directory, designer document, companion scripts, and a main script. Java extension creation is omitted from the Rust-only product. Asset-specific creation, backup cleanup, and publishing/import integrations that have not been ported are visible but disabled.

Naming and confirmation dialogs are Bevy UI. Disk operations execute on the existing bounded worker. Existing destinations and paths outside the project are rejected. Renames preserve open buffers and their undo history; reload rejects newer edits that arrive while disk is being read. Delete requires confirmation, protects the root and main entry point, and moves files to `.scenemax-studio/deleted-*` for recovery, including existing scene/UI code companions. File mutations require the game to be stopped and affected buffers saved.

GPU check: `--smoke-frames 120 --smoke-tree-menu scripts --smoke-screenshot <absolute-output.png>` opens a folder context menu after project loading; use a file path instead to inspect the file menu.

### Code editor font size

With focus in the code editor, press Ctrl+plus (Ctrl+= also works) to enlarge text or Ctrl+minus to reduce it. Numeric keypad plus/minus are supported. Font size ranges from 8 to 48 pixels; line spacing and line numbers scale together. The setting applies across code tabs for the current IDE session.


### Model Animation Analyzer

Tools → Model Animation Analyzer opens the project model catalog as a retained designer. Create named frame ranges, preview and scrub them, then save the records into the model JSON. The projector plays them through the ordinary animation syntax without modifying GLTF. See [the analyzer guide](ANIMATION_ANALYZER.md).
