# Package & Deploy

Open **Tools → Package & Deploy…**. The retained Bevy form runs inside the IDE. Web Start is intentionally absent.

Players can override a desktop game's packaged UDP endpoint by placing
`network.json` beside the built executable, with `host` and numeric `port` fields.
Optional project/room overrides and validation rules are described in
[External multiplayer configuration](../Projector/README.md#external-multiplayer-configuration).
Existing distributions must be rebuilt with the updated projector; custom
prebuilt runtimes must also include this support.

## Platform capability

| Platform | Artifact | Requirements |
| --- | --- | --- |
| Windows | One self-extracting `.exe` | Bundled Windows projector and launcher; no compiler |
| Linux | `.zip` containing one executable game | Matching bundled projector and launcher, or explicit source rebuild with target/linker |
| macOS | `.zip` containing one executable game | Matching bundled projector and launcher, or explicit source rebuild with Apple SDK |
| Android | `.apk` | Explicit SDK builder recipe and Android runtime host |
| iOS | `.ipa` | Explicit SDK builder recipe, iOS runtime host and signing on a Mac |
| Web | `.zip` with `index.html`, JavaScript, Wasm and game files | Bundled WebGPU Wasm projector and JavaScript bindings; no compiler |

**Android and iOS remain external builder integrations.** They require a platform recipe and runtime host. Web uses the built-in browser projector when its optional custom recipe is blank.

## Browser exports

New installation? Start with the [Web setup and publishing guide](WEB_EXPORT.md).
The Studio installer supplies the matching runtime bundles. Source and build tools
are needed only when explicitly rebuilding the engine.

When updating exporter source, save and close the IDE, then run
`cargo build --locked -p scenemax_ide` from `scenemax3d_nextgen` and reopen it.
Export logic and bundled effect runtime files are compiled into the IDE;
rebuilding a projector or validation example does not update the running IDE.
The old message “This export contains native Effekseer effects. Their browser
renderer is not available” indicates an IDE built before browser effect support.

For explicit engine rebuilding, install **Web tools** in File → Settings → Build Configuration. This installs
`wasm32-unknown-unknown` for the IDE's pinned Rust toolchain and a matching
`wasm-bindgen-cli` in a versioned SceneMax tools folder, preserving other installed
versions. The installer also installs Rust if needed; C++ build tools are needed
on Windows to compile the binding generator.

Select Web in Package & Deploy and leave the custom recipe blank. The worker
copies the bundled Wasm runtime and generated bindings, stages the selected
scripts/assets and creates a static-site ZIP. Engine compilation is a developer workflow outside the export dialog.

After a Web artifact completes, **Open In Browser** serves the ZIP from a private
loopback HTTP server and opens the browser without requiring Zig. **Stop Test**
stops the preview. Multiplayer projects can additionally use **Test With Local
Server** or **Test With Remote Server** to prepare their server connection and
export the game for testing. Export again after changing scripts or assets.
Package & Deploy also discovers the newest completed Web ZIP for the saved game
filename, including exports from earlier IDE sessions. Multiplayer connections
still require the configured gateway and allowed browser origin.

For manual hosting, serve the extracted ZIP through HTTP on localhost or HTTPS in production.
Opening `index.html` directly from disk is unsupported. A WebGPU-capable browser
and graphics adapter are required. The loading page reports file failures and
starts automatically after loading. Browser project files are
mounted read-only before script execution; Bevy asset loaders use the same mounted
store, so content aliases do not trigger duplicate HTTP downloads. This first implementation preloads the complete
selected snapshot into memory, so large games have higher startup/memory costs.
Desktop authoring filesystem writes are not browser save-game persistence.

Supported Effekseer `.efkefc` effects use a bundled, locally served WebGPU runtime.
Existing play, loop, stop, transform, speed and dynamic-input commands use the
same shared playback state; completion is checked against each playback generation.
No additional Effekseer SDK is required. The first click or key press in the game
unlocks browser-restricted game and effect audio without blocking automatic startup. Missing dependencies and renderer/device failures are shown on the loading
screen. Effect dependencies referenced outside the effect directory are included
when they remain inside the project's resource root.

Web effects currently disable MSAA and draw after transparent geometry, before
tone mapping. They share Bevy's device, color target and reversed-depth buffer.
Distortion samples the current camera's scene color on the GPU and supports SDR
targets, camera viewports and resizing. Soft particles, custom materials and
GPU-simulated particles are not supported and fail export with an actionable error. Effects need editor metadata
for that validation; raw `.efk` files are unsupported. Transparent objects from
Bevy and Effekseer are not jointly sorted. Unsupported render-target formats and
device loss produce visible errors; device loss requires reloading the game.
The desktop native-effects setting remains independent of the browser backend.
Binary pin, checksums, licenses and maintainer update instructions are in
`Engine/effects/web/MAINTENANCE.md` (EffekseerForWeb 0.1.0 at commit
`da76b5023bd7aa10fe66ee05f6fecd4b84debce3`).
Custom Rust additions must compile for `wasm32-unknown-unknown`.

For multiplayer, enable Browser gateway in Project Settings — Multiplayer and save
its WebSocket URL, listener and allowed game origin. The exporter writes the saved
URL into `network.json` automatically.
Browsers use binary WebSocket messages through the supplied
`scenemax_web_gateway`, which forwards each connection through its own UDP socket
to the existing Zig server without changing the game protocol. Build Server packages
both executables and `gateway.json`; Run Server Locally starts and monitors both.
The standalone gateway reads its adjacent configuration when launched without
arguments. Local defaults are `ws://localhost:9002` and origin `http://localhost:8080`.
Use a TLS reverse proxy for public WSS, and specify the actual hosting/iframe
origin. Static hosting cannot run the gateway. The ZIP includes these deployment
instructions. Desktop clients continue to connect directly over UDP.

Linux/macOS package assembly is implemented but not executed on those OSes in this Windows workspace. Cross compilation needs a compatible linker and SDK in addition to the Rust target. The existing automatic Effekseer build stages a Windows DLL. For other targets use a matching prebuilt projector and adjacent libraries, or disable native effects and test the resulting game behavior.

## Workflow

1. Set the filename and release version. Builds use the project’s `build_games` folder by default, with a unique child folder for each build. Previous releases are never deleted or overwritten.
2. Shared resources are discovered automatically from the installation and included when referenced. Existing saved deployment paths remain supported.
3. Select platforms and use **Edit** for their details. Desktop builds accept a Rust triple and optional prebuilt projector. Its adjacent dynamic libraries are included. Native file/folder pickers are available beside paths.
4. **Check requirements** checks configuration, entry-point containment, tools and prebuilt executable format. Compilation, SDK setup, transitive libraries, signing and clean-machine execution still need their respective verification.
5. **Build release** saves dirty documents through the existing save/conflict pipeline. Failed saves, newer edits or a changed project cancel deferred packaging. Stop the running projector first.
6. Follow phase progress and live stdout/stderr. Logs wrap inside the panel; disable **Follow log** to inspect earlier output. **Cancel build** stops the owned tool tree and preserves finished artifacts and logs. Partial artifacts remain private and are removed.
7. A modal **Release complete** or **Build stopped** message clearly marks the end. It offers output/log actions and stays until dismissed. **Open output** opens the artifact directory; **Log folder** locates `build.log`.

Settings edits are saved in `.scenemax-studio/deployment.json` without requiring a build. Closing/reopening the form also preserves its draft for the same project. Unknown top-level settings fields are preserved. Existing source documents, undo history, Java code and Java settings remain untouched. Relative form paths resolve against the project.

## Fast packaging and resource selection

Packaging always starts at the project's root `main` (the shallowest `scripts/**/main`, matching the project model), never the active editor document. Every reachable script is parsed with `scenemax_parser`; typed `AddCode`, `SwitchTo` and `UiLoad` statements drive traversal, including nested functions, branches and event handlers. Character-mode commands are not scene transitions. The closure retains referenced catalog records, model aliases, glTF/GLB external buffers and textures, font pages, authored definitions and effect sidecars. Catalogs are pruned only inside the isolated snapshot.

Resource-name selection is conservative: literal names in reachable code and UI documents remain eligible even in conditional branches. Advanced projects using dynamically assembled resource names can retain `include_all_resources: true` in `.scenemax-studio/deployment.json`; this setting is not shown in the export dialog. Static selection cannot prove the values of arbitrary runtime expressions. `package-size.json` lists the selected resource files, byte counts and excluded bytes in the build folder. Unsupported legacy game content is not converted by packaging.

The installer supplies both projectors and the packaging launcher under
`scenemax3d_nextgen/prebuilt`. Normal exports never run Cargo, rustc or wasm-bindgen,
even when sources are present. Missing bundles fail with repair instructions.
Source builds are a developer workflow outside the export dialog.
Unsupported targets require matching runtime bundles or an explicit source build.

Identical resource payloads are byte-verified, archived once and restored at their original paths by the launcher. Already compressed media is stored directly; other files use fast deflate. No lossy asset conversion is performed. The September 18 Windows benchmark on the user's project produced a 245.9 MiB executable in 17.5 seconds, down from approximately 1 GiB and a six-minute engine compilation. Timings depend on the machine, cache and asset contents.

## Asset sizing report

Every build writes `size-report-analysis.txt` and `size-report-analysis.json` next to `build.log`, using the Java report's title, category/top-contributor sections and JSON fields. The report is generated from the actual staged snapshot, after dependency selection and before platform compilation, and survives later build/upload failures. It is also generated when `include_all_resources` is enabled in the advanced deployment configuration.

Use **Open output** to open the build folder containing the text report and platform output folders. Categories are sorted by total size; the 40 largest individual files are listed with snapshot-relative paths. Project/shared resources are separate, and categories account for every staged byte without overlapping. Counts explicitly represent files, including catalogs and sidecars, rather than Java's sometimes-partial logical resource counts. Machine-readable byte sizes remain exact; MiB figures are rounded to two decimals. Generation times use UTC.

This is staged content analysis, not compressed artifact size or a GPU/frame-time profiler. The runtime and platform libraries are added later; archive compression and deduplication can reduce the game content size. The report files stay outside the payload and upload directories. Existing `package-size.json` remains available for dependency-selection details.

## Single-file launcher

`Projector/launcher` is a safe-Rust executable with no IDE, Java, Zig or Bevy dependency. The service appends a ZIP containing the projector, scripts, resources, launch manifest and adjacent native libraries. On launch it validates paths and CRCs, extracts into a private temporary directory, passes explicit project/script paths, sets the bundled resource/library paths, waits for the game and cleans up. The caller's working directory is irrelevant. Linux/macOS ZIP entries preserve executable permissions.

Bootstrap errors are written to `scenemax-launch-error.txt` in the system temporary directory. Runtime writes inside the extracted project are temporary; durable game saves must use a user-data location. `SCENEMAX_DISTRIBUTION_EXE` identifies the original package. Forced termination or a machine crash can leave temporary files behind.

The bundle includes adjacent libraries, not an arbitrary SDK installation. Clean-machine testing must still verify graphics drivers, OS runtime prerequisites and transitive native dependencies. Code signing/notarization is not implemented by this form. The installer ships the launcher already compiled. `SCENEMAX_BUILD_WORKSPACE` selects the workspace containing the `prebuilt` bundles and optional rebuild sources.

## itch.io

Enter `https://username.itch.io/game` or `username/game` and review each platform's channel. **Sign in with Butler…** starts Butler's browser authentication. Alternatively it inherits `BUTLER_API_KEY`. Credentials are never entered into or serialized by this form, and the Java database's private keys are not migrated.

New forms import matching non-secret page/channel defaults from the Java project catalog. Butler detection checks shipped tools and the Windows itch application's selected Butler version, then falls back to PATH. Its path is also editable.

Automatic upload starts only after all selected artifacts succeed. Each channel uses `butler push` and `--userversion`. ZIPs are passed directly, so their contents become the upload root. Executable/APK/IPA files are pushed from their dedicated platform directory. Source folders, settings and build logs are not uploaded. A failed upload preserves local artifacts. **Upload / retry existing builds** publishes those outputs without compiling again; keep their platform selection. Retrying can re-push already successful channels.

The game page must already exist. Successful upload updates the selected channel,
but itch.io may still need to process and extract it before browser play is ready.
Do not immediately reupload while processing; wait and reload the game page.
The release version is entered manually and does not increment automatically. For Web, mark the page/upload playable in the browser through itch's UI after the first upload; see the official [Butler manual](https://itch.io/docs/butler/pushing.html).

## SDK builder contract

Android/iOS require an explicit JSON recipe; Web optionally accepts one to override the built-in builder:

```json
{
  "program": "C:/tools/my-platform-builder.exe",
  "args": ["--project", "{project}", "--entry", "{entry}", "--output", "{output}", "--version", "{version}"],
  "artifact": "application.apk"
}
```

This is a contract illustration; that builder is not shipped. Use an absolute executable or a command on PATH. Scripts must name their interpreter explicitly. The recipe directory is the working directory. Arguments are substituted separately without shell interpolation:

- `{project}`: isolated snapshot containing scripts, resources, optional `builtin/resources`, and `launch.json`.
- `{entry}`: entry script relative to that snapshot.
- `{output}`: new empty job-owned output directory.
- `{workspace}`, `{name}`, `{version}`: NextGen workspace and release settings.

The builder must consume the snapshot, implement the target runtime bootstrap, invoke its SDK, and fail nonzero on errors. Signing credentials belong in the SDK/OS store or environment. Its artifact path must be relative to `{output}`:

- Android: APK containing `AndroidManifest.xml` and native libraries.
- iOS: IPA containing `Payload/*.app/Info.plist`; the builder handles signing/provisioning.
- Web: directory containing `index.html`, a Wasm module, generated JavaScript and required assets; the service creates the final ZIP.

Structural output validation does not establish device/browser compatibility or verify signing. Inspect third-party recipes before configuring them: they execute build tools in the user's environment.

## Windows build tool setup

File → Settings → **Build Configuration** scans installed Rust/Cargo, Visual Studio
MSVC x64 tools, Windows SDK libraries/headers and CMake. Discovery runs off the UI
thread and checks PATH, Cargo home, user/machine environment settings and Visual
Studio's installation catalog. It does not download or install during a scan.

**Install / repair Rust** uses the official rustup bootstrapper (verified against
its published SHA-256) and installs the IDE's pinned MSVC toolchain, rustfmt and
Clippy without changing an existing default toolchain. **Install C++ / SDK /
CMake** uses the signed Microsoft Visual Studio 2022 Build Tools installer with
the C++ workload, recommended SDK and CMake components. Existing Build Tools
installations are modified; full Visual Studio editions are kept intact. Windows
may ask for administrator permission. Setup never requests an automatic reboot;
restart-required results are shown in the dialog.

The dialog shows installed/missing components and paths, download progress,
indeterminate installation activity, and bounded console output. Microsoft Setup
also provides its own progress window and `%TEMP%/dd_*` diagnostic logs. Closing
the dialog leaves the worker running; keep the IDE running until setup finishes.
Refresh detection after an external installation or reboot. Automatic setup is
currently for Windows x64; cross-compilation prerequisites remain separate.

Successful scans atomically save `%LOCALAPPDATA%/SceneMax/build-tools.json`.
Export Cargo processes use the discovered executable and compiler/SDK environment
without changing the IDE's global environment or requiring an IDE restart. A new
scan replaces stale installation paths. No Zig SDK is installed by this feature.
Read-only dialog smoke capture: `SCENEMAX_SMOKE_SETTINGS=1` with `--smoke-frames`
and `--smoke-screenshot`; the hook never starts an installation.

## Boundaries and verification

Domain validation lives in `IDE/core/deployment.rs`. The owned worker, filesystem, SDK adapters and Butler live in `IDE/services/deployment/`. `IDE/app/application/deployment.rs` owns orchestration and saving. `IDE/app/presentation/deployment.rs` owns retained widgets. `Projector/launcher` is an independent executable built through Cargo, never linked into the IDE.

UI logs retain 24 KiB; the persistent log is capped at 16 MiB. Tools producing over 64 MiB or running over two hours are stopped. Environment API keys are redacted across chunk boundaries before UI/persistent output. Traversal rejects links/junctions and special files, with limits of 100,000 files, 64 levels and 16 GiB. Source metadata and inventories are rechecked for concurrent changes. Cancellation is checked between files and chunks.

Tests cover validation, payload contents/permissions, source preservation, process logs/failure/cancellation, secret redaction, Butler arguments and retained fields. Executable round trip:

```powershell
cargo build --locked --release -p scenemax_game_launcher
cargo test --locked -p scenemax_ide_services actual_single_file -- --ignored --nocapture
```

An isolated headless acceptance entry point uses the same worker:

```powershell
cargo run --locked -p scenemax_ide_services --example package -- <project> <output> <prebuilt-projector>
```

Use disposable projects because building saves their deployment settings. For native form capture set `SCENEMAX_SMOKE_DEPLOY=1` with the IDE's `--smoke-frames` and `--smoke-screenshot` options. Test/smoke hooks never perform a real itch upload.

## Model selection and report traceability

The size report lists only models included in the artifact under Packaged Models, independently of the top-40 file ranking. Each entry identifies its inclusion status, registered and resolved runtime paths, parsed script declarations (including entity names), and the glTF descriptor, external buffers and textures with byte totals. Shared files can appear under multiple aliases; model subtotals must not be added together. Category totals remain the actual staged file totals.

Animation `bevyBakedRetargets` are optional target variants: only variants for selected models survive dependency expansion and staged catalog writing. An unused variant cannot pull its target model into the game. Source-model aliases still resolve to their supported glTF/GLB resource.

JME `.j3o` payloads are excluded in both dependency and all-assets modes. Unsupported catalog entries without a source-model alias are removed from the staged catalog; source files remain untouched. A referenced unsupported model produces a build-log warning and a dependency diagnostic in package-size.json; excluded models do not appear in the size analysis. This does not convert the model or repair the scene: authors must supply a supported glTF/GLB resource or remove its scene reference.

Browser test buttons in both forms use the Multiplayer Deployment progress view.
Local testing starts an IDE-owned loopback Zig server and WebSocket gateway using
temporary connection settings. Remote testing uses the saved deployed gateway.
Both rebuild the browser game without publishing. Stop Test stops the preview
and any owned local test processes; it never stops deployed remote services.
