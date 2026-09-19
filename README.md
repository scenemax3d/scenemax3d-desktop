# SceneMax3D Developer Studio

**A native Rust/Bevy environment for building interactive 3D games.**

SceneMax3D brings scene editing, game scripting, asset previews, and release packaging into one desktop workspace. Both the IDE and the game runtime are built with Rust and Bevy. Create scenes visually, write gameplay in the SceneMax language, and run your project in a dedicated native game process.
<img width="1917" height="1013" alt="image" src="https://github.com/user-attachments/assets/0aa39dc2-f6e1-4ef8-942d-f1e570d3b9bd" />

SceneMax is the authoring language: you do not need to write Rust to create game scripts. Rust powers the editor, parser, engine, and deployment tools underneath.

[Product website](https://scenemax3d.com/cook-book/) · [IDE guide](scenemax3d_nextgen/IDE/README.md) · [Package & Deploy](scenemax3d_nextgen/IDE/DEPLOYMENT.md)

## Create, preview, and play

- **Integrated workspace:** manage projects and files, edit tabbed documents, search and replace, check script syntax, and use undo/redo and document recovery.
- **Visual 3D scene editing:** compose scenes with a hierarchy, property inspector, viewport picking, transform gizmos, and model previews. Work with game cameras and cinematic camera tracks inside the editor.
- **SceneMax scripting:** describe entities, movement, animation, events, scene transitions, and gameplay flow in a language designed for interactive scenes. Run a script or the project, inspect output, and stop the game from the IDE.
- **UI design:** arrange nested interface elements with live layout previews, sprite and bitmap-font support, property editing, zoom, and pan.
- **Asset workflows:** import models, animations, sprites, audio, video, and Effekseer effects. Inspect model materials and animation playback, slice sprite sheets, and preview effects before importing them.
- **Native game execution:** the independent Rust/Bevy runtime loads project scripts and resources, keeping game execution separate from the editor session.
- **Package & Deploy:** configure releases, follow build progress and logs, inspect asset sizes, and upload completed builds to itch.io.

The desktop workflow is actively developed and tested on Windows. Individual feature guides document current capabilities and remaining limitations.

## Get started

### Requirements

For the Windows source build, install:

- Rust through rustup. The workspace pins its compiler and tools in [`rust-toolchain.toml`](scenemax3d_nextgen/rust-toolchain.toml).
- Visual Studio C++ Build Tools with the Windows SDK.
- CMake for the native Effekseer integration, or a compatible prebuilt native library.
- A Vulkan-capable graphics device and current drivers for the IDE renderer.

See the [shared effects renderer guide](scenemax3d_nextgen/Engine/effects/README.md) for native dependencies and prebuilt-library configuration. Other target platforms require their own linkers, SDKs, and native libraries.

### Build and launch

Clone the repository into an explicitly named folder:

```powershell
git clone https://github.com/scenemax3d/scenemax3d-desktop.git scenemax_desktop
cd scenemax_desktop
```

Build the IDE and game runtime from the Rust workspace:

```powershell
cd scenemax3d_nextgen
cargo build --locked -p scenemax_ide -p scenemax_projector_nextgen
cd ..
```

Launch the IDE from the repository root:

```powershell
.\run-rust-ide.ps1
```

To open a particular project:

```powershell
.\run-rust-ide.ps1 -ProjectRoot 'C:\path\to\your-project'
```

The launcher uses the repository project catalog when available. The first build compiles the engine and its dependencies; subsequent builds use Cargo's incremental cache.

## Working with game assets

Use **glTF or GLB** for runtime models. The model importer also accepts source formats such as FBX and OBJ through its import workflow. Model previews provide camera navigation, transform controls, animation inspection, and optimization options.

Project resources live alongside the scripts that use them. Packaging starts from the project's root `main` script, parses reachable SceneMax code, and follows scene and resource dependencies. External glTF buffers and textures are included with their model. Source projects remain unchanged while packaging operates on an isolated snapshot.

For resource names constructed dynamically at runtime, the packaging form offers **Include all assets**. Use this when static dependency selection cannot determine every required resource.

Detailed workflows:

- [Model, animation, audio, and video imports](scenemax3d_nextgen/IDE/ASSET_IMPORTS.md)
- [Sprite sheets and sprite animation](scenemax3d_nextgen/IDE/SPRITE_IMPORT.md)
- [Effekseer import and preview](scenemax3d_nextgen/IDE/EFFECT_IMPORT.md)
- [UI designer](scenemax3d_nextgen/IDE/UI_DESIGNER.md)

## Package and publish

Open **Tools → Package & Deploy…** to choose output targets, configure a release, and build it. The form provides phase progress, live build logs, cancellation, and a completion or failure dialog with access to the output and reports.

| Target | Output | Current availability |
| --- | --- | --- |
| Windows | A single executable containing the runtime and game content | Implemented and exercised on Windows |
| Linux | ZIP containing an executable game bundle | Package assembly implemented; matching target tools and platform verification required |
| macOS | ZIP containing an executable game bundle | Package assembly implemented; Apple SDK and platform verification required |
| Android | APK through a configured builder | Runtime host and SDK integration work remains |
| iOS | IPA through a configured builder | Runtime host, SDK integration, and signing work remains |
| Web | ZIP containing a browser application | Browser runtime and builder integration work remains |

The desktop launcher is written in Rust. It extracts the bundled content into a private temporary directory, starts the game, and cleans up after a normal exit. Signing and clean-machine distribution checks remain part of preparing a release.

Ordinary game releases reuse the built release runtime. Rebuilding the engine is an explicit option, so script and asset changes do not require recompiling Bevy every time.

### Understand your game's size

Each build produces `size-report-analysis.txt` and a machine-readable JSON companion. They show category totals, the largest files, and only the models included in the package, with their runtime paths, dependencies, and script references. The figures describe staged content before compression and runtime embedding.

Use these reports to find large textures, models, audio, and video before publishing. Build logs and analysis reports stay outside the shipped game payload.

### Publish to itch.io

Configure your itch.io page and platform channels in the deployment form. Authenticate with Butler, then enable automatic upload after a successful build or upload existing artifacts separately. Failed uploads can be retried without rebuilding the game.

See the [deployment guide](scenemax3d_nextgen/IDE/DEPLOYMENT.md) for target requirements, runtime reuse, asset selection, authentication, and builder recipes.

## Rust workspace

The product's Rust components live under [`scenemax3d_nextgen/`](scenemax3d_nextgen/):

| Directory | Responsibility |
| --- | --- |
| `IDE/` | Native editor application, domain state, background services, and retained Bevy UI |
| `Projector/` | Independent game executable and distribution launcher |
| `Engine/` | Runtime systems, shared UI rendering, and native effects integration |
| `Language/` | SceneMax parsing and language support |
| `Common/` | Shared project and asset contracts |
| `Tools/` | Architecture checks and development utilities |

The IDE and runtime have separate application ownership. Shared functionality belongs in focused components, while game-specific behavior belongs in project scripts and assets.

For implementation details, see the [architecture guide](scenemax3d_nextgen/docs/ARCHITECTURE.md) and [designer hosting contract](scenemax3d_nextgen/docs/DESIGNER_HOSTS.md).

## Contributing

Contributions are welcome. Open an issue to discuss substantial changes, and read [AGENTS.md](AGENTS.md) and the architecture guide before changing engine or editor code. Keep reusable engine behavior independent of any particular game.

Run the relevant tests from `scenemax3d_nextgen`:

```powershell
cargo test --locked -p scenemax_ide -p scenemax_ide_ui -p scenemax_ide_core -p scenemax_ide_services --lib
python Tools/check_architecture.py
```

Useful contributions include editor usability, asset workflows, runtime behavior, deployment diagnostics, and verified platform support. Current platform priorities include mobile runtime hosts, browser execution, and distribution testing across desktop systems.

## License

SceneMax3D is licensed under the [MIT License](LICENSE). Third-party components retain their respective licenses and attribution.
