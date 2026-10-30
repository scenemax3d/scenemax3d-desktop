# SceneMax Studio IDE

SceneMax Studio is a native Rust/Bevy workspace for SceneMax scripts, visual scenes, UI, assets, and game publishing. The editor and standalone game runtime share focused rendering and resource components. Game authoring uses SceneMax; Rust programming is not required.

## Launch and projects

Launch the installed Studio from its shortcut. For a source build, follow the [repository setup](../../README.md#get-started), then run from the repository root:

```powershell
.\run-rust-ide.ps1 -ProjectRoot 'C:\path\to\project'
```

Use **File → Projects** to select a project or open Project Explorer. Double-click a project file, or select it and press Enter, to open it. Right-click files and folders for creation, rename, move, and other project actions. Save affected buffers and stop a running game before project changes that require them.

## Write and run SceneMax

Edit scripts in tabs with syntax highlighting, completion, find/replace, indentation, bracket matching, undo/redo, and recovery. See the [language reference](../../docs/README.md) for commands and examples.

| Action | Shortcut |
| --- | --- |
| Save / Save all | Ctrl+S / Ctrl+Shift+S |
| Find / next / previous | Ctrl+F / F3 / Shift+F3 |
| Go to line | Ctrl+G |
| Check syntax | Ctrl+Enter |
| Run active script | F8 |
| Run project | F10 |
| Stop | Shift+F5 |
| Code font size | Ctrl+plus / Ctrl+minus |

File commands are in **File**; syntax checking and run controls are in **Tools**. Run project starts the project's main script and saves dirty scripts first. Running a `.smdesign` saves and regenerates its companion script before launching. Output and exit status appear in the IDE; the game runs in a separate process.

## Design scenes and interfaces

The 3D designer combines a hierarchy, viewport selection, transform gizmos, and live properties. Add primitives, project models, lights, paths, and cinematic rigs; edit the game camera and preview its view. Property and gizmo changes participate in document Save and Undo. Unknown project metadata is preserved.

Designers open as document tabs inside the IDE. Use their individual guides for controls and supported preview behavior:

- [UI designer](UI_DESIGNER.md)
- [Material Studio](MATERIAL_EDITOR.md)
- [Model Animation Analyzer](ANIMATION_ANALYZER.md)
- [Weapon Designer](WEAPON_DESIGNER.md)
- [IK Designer](IK_DESIGNER.md)
- [Throw Motion Designer](THROW_MOTION_DESIGNER.md)
- [Physics Simulation](PHYSICS_SIMULATION.md)
- [Bitmap Font Generator](FONT_GENERATOR.md)

## Import assets

Import models, animation resources, images, audio, video, and effects through **Assets**. Runtime models use glTF/GLB; model import also supports native conversion of FBX and OBJ. Preview assets before publication and inspect any unsupported-content diagnostics.

- [Model, animation, audio, and video import](ASSET_IMPORTS.md)
- [Sprite sheets and animation](SPRITE_IMPORT.md)
- [Effekseer import and preview](EFFECT_IMPORT.md)
- [Project inventory](PROJECT_INVENTORY.md)

## Publish and connect players

**Tools → Package & Deploy…** builds desktop packages and WebAssembly/WebGPU browser games, previews Web exports, reports asset sizes, and uploads completed builds to itch.io. Installed Windows and Web runtime bundles support ordinary exports without a compiler.

Configure multiplayer under **File → Projects → Project Settings**. Browser clients use the WebSocket gateway; desktop clients use the dedicated Zig UDP server. The IDE provides local/remote browser tests, server bundle creation, and SSH deployment to Linux x64 hosts. Public browser multiplayer requires a WSS endpoint and the correct game origin.

- [Package & Deploy](DEPLOYMENT.md)
- [Web export and publishing](WEB_EXPORT.md)
- [Multiplayer configuration and deployment](../../docs/multiplayer.md)

## Git and AI automation

The **Git** menu provides repository operations, staging, commits, branches, history/diffs, and remote workflows. See [Git tools](GIT.md).

The built-in **MCP** server exposes editing, scene/UI authoring, validation, and previews to AI agents. Click MCP in the status area for connection status and request monitoring. See [MCP setup](MCP.md) and the [generated tool reference](MCP_TOOLS.md).

## Development

See the [component architecture](../docs/ARCHITECTURE.md) and [designer hosting contract](../docs/DESIGNER_HOSTS.md). Historical migration reports record earlier implementation stages; they are not current editor instructions. Platform and feature limitations are documented in the relevant guides above.
