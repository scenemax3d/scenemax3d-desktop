# SceneMax3D Developer Studio

**A native Rust/Bevy environment for creating and publishing desktop and browser games, including multiplayer.**

SceneMax3D brings scene editing, game scripting, asset previews, and release packaging into one desktop workspace. Both the IDE and the game runtime are built with Rust and Bevy. Create scenes visually, write gameplay in the SceneMax language, and run your project as a native game or publish it to the browser with WebAssembly and WebGPU. Browser and desktop players can join the same multiplayer sessions.
SceneMax is the authoring language. Rust and Bevy powers the editor, parser, engine, and deployment tools underneath.

<img width="1897" height="1012" alt="image" src="https://github.com/user-attachments/assets/19e510d9-e7bd-487a-8b29-872a9df1f5c4" />

[Product website](https://scenemax3d.com/cook-book/) · [IDE guide](scenemax3d_nextgen/IDE/README.md) · [Scripting reference](docs/README.md) · [Package & Deploy](scenemax3d_nextgen/IDE/DEPLOYMENT.md) · [Web publishing](scenemax3d_nextgen/IDE/WEB_EXPORT.md) · [Multiplayer](docs/multiplayer.md)

## History
SceneMax3D was first created by Adi Barda in 2005 as a C++/DirectX game engine and scripting language. In 2017-2018 it was rewritten in Java from scratch using JME3 as the target renderer and ANTLR4 for the language parsing. In Mar-22, 2026 the entire solution was uploaded to GitHub as an open source (MIT license) project. On August 08 2026, the initial next generation projector, based on Rust/Bevy engine was committed.
On October 29, 2026 - First official Rust/Bevy release was deployed as a GitHub artifact featuring full Bevy UI based IDE with SceneMax choreography programming language, multiplayer integrated into the language and Zig UDP server supporting both native and web (browser) games.

## Create, preview, and play

- **Integrated workspace:** manage projects and files, edit tabbed documents, search and replace, check script syntax, and use undo/redo and document recovery.
- **Visual 3D scene editing:** compose scenes with a hierarchy, property inspector, viewport picking, transform gizmos, and model previews. Work with game cameras and cinematic camera tracks inside the editor.
- **SceneMax scripting:** describe entities, movement, animation, events, scene transitions, and gameplay flow in a language designed for interactive scenes. Run a script or the project, inspect output, and stop the game from the IDE.
- **UI design:** arrange nested interface elements with live layout previews, sprite and bitmap-font support, property editing, zoom, and pan.
- **Asset workflows:** import models, animations, sprites, audio, video, and Effekseer effects. Inspect model materials and animation playback, slice sprite sheets, and preview effects before importing them.
- **Material and animation tools:** compose PBR materials with live previews and named-slot assignment, and create, preview, and scrub named animation frame ranges with the Model Animation Analyzer.
- **Physics Simulation:** experiment with forces, impulses, velocity, and torque in an isolated laboratory with pause, step, reset, and trajectory visualization, then copy SceneMax commands into your game.
- **Native game execution:** the independent Rust/Bevy runtime loads project scripts and resources, keeping game execution separate from the editor session.
- **Web deployment:** export a browser-ready ZIP with the WebAssembly runtime, scripts, assets, and supported Effekseer effects. Preview locally from the IDE and publish to itch.io or your own static host.
- **Browser and desktop multiplayer:** share sessions through the dedicated Zig server, with a WebSocket gateway for browser clients. Configure, build, test, and deploy server bundles from the IDE.
- **Git and AI integration:** manage staging, commits, branches, history, and remote operations with integrated Git tools. A built-in MCP server lets AI agents inspect and edit projects, author scenes and UI, validate scripts, and capture previews.
- **Package & Deploy:** configure releases, follow build progress and logs, inspect asset sizes, and upload completed builds to itch.io. The Studio installer bundles Windows and Web runtimes, so ordinary exports require no compiler.

The desktop workflow is actively developed and tested on Windows. Individual feature guides document current capabilities and remaining limitations.

## Get started

### Install SceneMax Studio

Download the Studio installer from [GitHub Releases](https://github.com/scenemax3d/scenemax3d-desktop/releases). The Windows installation includes the IDE and prebuilt Windows and Web export runtimes. You do not need Java, Rust, or C++ build tools to create games and export them using these bundles. Engine rebuild tools are optional and intended for developers changing the engine.

Browser games require a WebGPU-capable browser and graphics device. See the [Web publishing guide](scenemax3d_nextgen/IDE/WEB_EXPORT.md) for export, preview, and hosting instructions.

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

For resource names constructed dynamically at runtime, set `include_all_resources` to `true` in the project's `.scenemax-studio/deployment.json`. Use this advanced setting when static dependency selection cannot determine every required resource.

Detailed workflows:

- [Model, animation, audio, and video imports](scenemax3d_nextgen/IDE/ASSET_IMPORTS.md)
- [Sprite sheets and sprite animation](scenemax3d_nextgen/IDE/SPRITE_IMPORT.md)
- [Effekseer import and preview](scenemax3d_nextgen/IDE/EFFECT_IMPORT.md)
- [UI designer](scenemax3d_nextgen/IDE/UI_DESIGNER.md)
- [Material Studio](scenemax3d_nextgen/IDE/MATERIAL_EDITOR.md)
- [Model Animation Analyzer](scenemax3d_nextgen/IDE/ANIMATION_ANALYZER.md)
- [Physics Simulation](scenemax3d_nextgen/IDE/PHYSICS_SIMULATION.md)
- [Git workflows](scenemax3d_nextgen/IDE/GIT.md)
- [AI agent connection and authoring](scenemax3d_nextgen/IDE/MCP.md)

## Package and publish

Open **Tools → Package & Deploy…** to choose output targets, configure a release, and build it. The form provides phase progress, live build logs, cancellation, and a completion or failure dialog with access to the output and reports.

| Target | Output | Current availability |
| --- | --- | --- |
| Windows | A single executable containing the runtime and game content | Bundled runtime and launcher; no compiler required for ordinary exports |
| Linux | ZIP containing an executable game bundle | Package assembly implemented; matching target tools and platform verification required |
| macOS | ZIP containing an executable game bundle | Package assembly implemented; Apple SDK and platform verification required |
| Android | APK through a configured builder | Runtime host and SDK integration work remains |
| iOS | IPA through a configured builder | Runtime host, SDK integration, and signing work remains |
| Web | ZIP containing HTML, JavaScript, WebAssembly, and game content | Built-in WebGPU export, browser preview, and multiplayer gateway integration; bundled runtime requires no compiler |

The desktop launcher is written in Rust. It extracts the bundled content into a private temporary directory, starts the game, and cleans up after a normal exit. Signing and clean-machine distribution checks remain part of preparing a release.

Ordinary Windows and Web releases package the installer-supplied runtimes with your scripts and assets. They do not invoke Cargo, rustc, or wasm-bindgen, and exporting requires no internet connection. Rebuilding the engine is a separate developer workflow; other desktop targets require matching runtime bundles or the appropriate source-build toolchain.

### Publish a browser game

1. Open **Tools → Package & Deploy…**, select **Web**, and build the release. The exporter creates a ZIP with `index.html` at its root and the runtime and game files alongside it.
2. Choose **Open In Browser** to serve and preview the completed export locally. Multiplayer projects also offer **Test With Local Server** and **Test With Remote Server**. Export again after changing scripts or assets.
3. Upload the ZIP to itch.io as a browser-playable HTML game, or extract it onto your own HTTPS static host. Butler integration can upload completed builds directly from the IDE.

Games start after loading. Browsers may require a click or key press before audio can play. WebGPU is required; there is no WebGL fallback. Use HTTPS for public hosting or HTTP on localhost, rather than opening `index.html` directly from disk. Supported Effekseer effects are bundled automatically; see the [Web guide](scenemax3d_nextgen/IDE/WEB_EXPORT.md) for browser rendering limits.

### Deploy multiplayer for browsers and desktop

SceneMax supports browser-to-browser and browser-to-desktop play through the same project, session, and scene. Desktop clients connect directly to the Zig UDP server; browsers connect through the supplied WebSocket-to-UDP gateway. Multiplayer scripts can share registered entities, movement, animation, events, and state, with snapshots for joining players.

Open **File → Projects → Project Settings** and configure multiplayer:

- **Server:** set the server address, UDP port, project/session, and game password.
- **Browser:** enable the gateway and enter its public WebSocket URL, listener, and exact game or iframe origin. Web exports include the saved endpoint automatically.
- **Build:** create the server and gateway bundle or run both locally for testing.
- **Deployment:** connect to an existing Linux x64 host over SSH, then use **Deploy & start** to install the services and **Check status** to inspect them. Deployment supports OpenSSH and PuTTY, checksum verification, systemd startup, and rollback on startup failure.

Static game hosting, including itch.io, serves the browser files; run the multiplayer server and gateway on a separate server host. Public HTTPS games need a trusted `wss://` endpoint through a TLS reverse proxy. DNS, firewall rules, and TLS are configured on that host. See the [multiplayer guide](docs/multiplayer.md#browser-clients) for setup and deployment requirements.

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

The current source tree contains the Rust/Bevy implementation. The former Java
implementation is available in earlier release tags. Existing local reference
copies are ignored by Git and are not required to build or run SceneMax Studio.

Normal builds use the checked-in Rust parser. The optional
`SCENEMAX_REGENERATE_PARSER=1` maintainer workflow uses a locally supplied ANTLR
generator at `scenemax3d_nextgen/Tools/antlr/antlr4-4.8-2-SNAPSHOT-complete.jar`;
that tool is not distributed in this repository.

Contributions are welcome. Open an issue to discuss substantial changes, and read [AGENTS.md](AGENTS.md) and the architecture guide before changing engine or editor code. Keep reusable engine behavior independent of any particular game.

Run the relevant tests from `scenemax3d_nextgen`:

```powershell
cargo test --locked -p scenemax_ide -p scenemax_ide_ui -p scenemax_ide_core -p scenemax_ide_services --lib
python Tools/check_architecture.py
```

Useful contributions include editor usability, asset workflows, runtime behavior, deployment diagnostics, and verified platform support. Current platform priorities include mobile runtime hosts, broader browser/device testing, multiplayer resilience, and distribution testing across desktop systems.

## License

SceneMax3D is licensed under the [MIT License](LICENSE). Third-party components retain their respective licenses and attribution.
