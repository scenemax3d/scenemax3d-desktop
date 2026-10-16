# Project Inventory

Open **Assets > Project Inventory** in the Bevy IDE. The active project and shared installation resources appear in one browser. Categories include models, animations, sprites, textures, audio, video, fonts, materials, shaders, weapons, throw motions, effects, scenes, UI, skyboxes, terrain, IK and cinematics.

Search matches names, categories, origins, paths and metadata. Category counts and paged cards keep large libraries manageable. Click to inspect; Ctrl+click selects multiple assets. Arrow keys select nearby results, Home/End jump to the first/last result, and Escape closes the panel. Select page and Clear selection operate on the current selection. Sort switches between names and primary-file sizes. Transfer reviews show full dependency size separately.

## Preview and inspection

- Native glTF/GLB preview: right-drag to orbit, wheel to zoom, Frame, turntable, animation play/pause and next clip. Models gain gallery thumbnails when previewed.
- Native `.smmat` PBR preview uses the same surface adapter as the game and Material Studio.
- PNG/JPEG image and sprite-sheet previews use bounded thumbnails decoded on background workers.
- WAV/OGG audio has Play and Stop; changing selection or closing the inventory stops its preview.
- Videos use the operating system's player through Open. Text/JSON source and registration metadata are available in the inspector. Designer documents open through the normal IDE document workflow.
- Folder opens the containing directory; Copy name copies the runtime resource identity. Registered project assets can be renamed, with duplicate detection. Rename does not rewrite scripts.
- Missing files, legacy JME formats and shadowed registrations remain visible with diagnostic labels. Shared installation assets are read-only but can be copied into a project.

## Copy, move and remove

Select one or more assets and choose a destination from the project catalog (or enter a destination path if the catalog is empty). Copy and Move prepare a review containing the selected resources, discovered registered dependencies, files, size and conflicts. Nothing changes until **Confirm operation**. Save unsaved project documents first so disk content is the reviewed source of truth.

Transfers preserve registration metadata, publish entries into destination extension indexes, and retain imported bundles, glTF/GLB external files, font pages, designer companions and native material textures. Different existing files and duplicate destination registrations are conflicts; identical existing dependency files can be reused. The service rechecks source files and registration snapshots before applying a reviewed plan and rolls back created files/changed indexes if an operation fails.

Move copies first, then removes selected source registrations and archives unshared source files. Remove archives the selection without a destination copy. Shared payloads still used by other registrations are kept. Source backups and a manifest are retained below `.scenemax-studio/trash/inventory-*`; the completion dialog reports the exact path. Script references are not rewritten by move, remove or rename. This is an asset-bundle tool, not a game packaging operation or an automatic script refactoring tool.

The UI stays open during operations, shows progress and a completion dialog, and prevents project changes or orderly IDE shutdown while a mutation is committing. Refresh rescans the active project and shared resource indexes. Unreadable indexes are reported rather than silently dropped. File transfers reject paths outside their resource roots and linked/junction dependencies.

Java IDE implementation is unchanged.
