# Create and publish a Web game

## What a new installation needs

The Studio installer includes a prebuilt WebAssembly projector and its matching
JavaScript bindings, plus the Windows projector and packaging launcher. Normal
Windows and Web exports package these with your scripts and assets without
running Cargo, rustc or wasm-bindgen. No compiler or internet is needed to export.
A WebGPU-capable browser/GPU is required to play; Butler is optional for itch.io.

Export runtimes live in `scenemax3d_nextgen/prebuilt`: `web/` contains the generated
bindings and Wasm (including any binding snippets), and `x86_64-pc-windows-msvc/`
contains the desktop projector, launcher and native effects DLL. Keep these from
the same installer release as the IDE. Missing bundles produce a repair message;
they never trigger automatic compilation.

Engine rebuilding is a developer workflow outside the export dialog. It needs
the matching source workspace, Rust/Cargo, platform tools and, for Web, the
`wasm32-unknown-unknown` target and matching wasm-bindgen. Other desktop targets
need a supplied projector and bundled launcher for their target triple, or an
explicit source rebuild with the appropriate toolchain.

For an installed Studio, skip directly to [Build and preview](#3-build-and-preview). The following source setup applies only to explicit engine rebuilds.

## 1. Obtain sources for engine development

The Studio installer contains prebuilt runtimes and tools, not the Rust source
workspace. To change or rebuild the engine, obtain the matching release from the
[source repository](https://github.com/scenemax3d/scenemax3d-desktop). Keep the full
`scenemax3d_nextgen` workspace, lockfile, toolchain configuration, local dependencies,
and vendored sources together in a writable checkout.

Normal installed exports do not need `SCENEMAX_BUILD_WORKSPACE`. For a source-build
workflow that uses an IDE compiled elsewhere, this variable can point to the
matching developer checkout. Restart the IDE after changing its environment.

## 2. Prepare an engine-build toolchain

Follow the repository's source-build requirements. On Windows, install Rust through
rustup and the Microsoft C++ build tools/Windows SDK, plus the native effects build
dependencies. Install the workspace's Wasm target and the exact wasm-bindgen CLI
version required by `IDE/services/src/build_configuration.rs`. Use the pinned
toolchain and lockfile; a mismatched binding generator cannot process the runtime.

The developer IDE's **File → Settings → Build Configuration** provides tool
detection and setup actions. These are for source development, not ordinary
installed exports. Tool installation can require downloads, administrator approval,
and a manual restart. On other hosts, install the appropriate host linker and SDK.

Do not mix an updated Wasm binary with older JavaScript bindings. Generate and
ship them together when preparing a new runtime bundle. The installer maintainer
workflow is documented in the source repository under `INNO-SETUP/STUDIO.md`.

## 3. Build and preview

1. Open your game project and stop its running preview.
2. Open **Tools > Package & Deploy**. Set the game filename and
   release version. Builds use a new folder under the project’s `build_games` directory. Select **Web**; deselect other platforms if you only want Web.
3. Use **Edit** on Web and leave **Custom Web recipe** blank for the built-in
   exporter. **Open Web build setup** is for developer tool setup; it is not
   required when using the installed runtime bundle.
4. Optionally choose a PNG/JPEG loading background, heading, loading text and
   footer. The default footer is `powered by SceneMax3D`; the progress bar remains.
5. Shared resources are located automatically. Exports select referenced assets. Advanced projects
   using dynamic resource names can retain `include_all_resources` in their
   deployment configuration. Identical file contents are downloaded
   once while all original logical paths remain available.
6. Leave automatic upload off for a local test. Click **Check requirements**, fix
   reported problems, then **Build release**. Normal exports package the bundled
   runtime without compilation.
7. After completion, choose **Open In Browser**. SceneMax serves the completed ZIP
   on localhost and opens the browser without requiring a multiplayer server.
   Multiplayer projects can also use **Test With Local Server** or **Test With
   Remote Server**. **Stop Test** stops the preview; exiting the IDE also stops it.
   Export again after changing scripts or assets.

Each build gets a separate output directory and a `<name>-web.zip`. The ZIP has
`index.html` at its root. Settings edits are saved per project in
`.scenemax-studio/deployment.json`, including the itch.io URL and Butler path.
Build logs and size reports stay beside the artifacts and are not game payloads.

## 4. Browser behavior and effects

The game starts automatically after loading; there is no SceneMax Start button.
Browser policy can mute sound until the player clicks or presses a key in the
game. That interaction resumes game and Effekseer audio; automatic startup cannot
guarantee audible sound before interaction. itch.io may separately present its
own launch UI.

Serve the game over HTTPS in production or HTTP on localhost. Double-clicking
`index.html` (`file://`) is unsupported. WebGPU and a supported GPU/driver are
required; there is no WebGL fallback. Test on the actual hosting page as well as
locally. Large games preload assets into memory and can take longer to start.

Supported Effekseer `.efkefc` effects, their selected dependencies, and the browser
runtime are bundled automatically. **Include native Effekseer support** affects
desktop builds only. Browser distortion is supported. Custom materials, soft
particles and GPU-simulated particles are currently unsupported; export checks
report unsupported content. Raw `.efk` files lack the required validation metadata.
See [browser export details](DEPLOYMENT.md#browser-exports) for rendering limits.

## 5. Publish to itch.io

Create your game page on itch.io first and choose **HTML Game**. For automatic
upload, download Butler from its [official page](https://itchio.itch.io/butler),
extract the archive, select `butler.exe` in Package & Deploy, and choose
**Sign in with Butler**. An absolute path avoids needing to edit PATH. If Butler
is already available through the installation, itch app or PATH, SceneMax can
detect it. Do not assume every SceneMax distribution includes Butler.

Enter `https://username.itch.io/game` or `username/game`, keep a stable Web channel
such as `web`, and enable automatic upload. All selected builds must succeed
before uploading. Alternatively, upload the generated Web ZIP manually through
itch.io; that requires no Butler. Upload the ZIP itself, without placing it inside
another enclosing folder or archive. Mark the upload **playable in the browser**
in the itch.io edit page, then save the page settings. See the official
[HTML game instructions](https://itch.io/docs/creators/html5) and
[Butler upload instructions](https://itch.io/docs/butler/pushing.html).

Upload completion is not proof that browser extraction is complete. itch.io can
still be processing/unpacking the upload. Wait and reload the game page; avoid
immediately pushing again if it reports that the latest build is still processing.
`butler status username/game:web` reports the channel's build status, but the HTML
game can still be extracting afterward. If extraction stays stuck, inspect the
upload's status on itch.io and contact itch.io support with its build/upload ID.
**Upload / retry existing builds** retries finished local artifacts without
compiling again. The release version is entered manually; repeatedly uploading
`1.0.0` creates new builds with the same displayed version label.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Missing source workspace during an explicit engine rebuild | Install the full matching source distribution in a writable folder and set `SCENEMAX_BUILD_WORKSPACE`; restart the IDE |
| Rust, linker, compiler or Windows SDK missing during an engine rebuild | Run the relevant setup buttons, complete any requested reboot, then refresh detection |
| Wrong wasm-bindgen version or missing Wasm target during an engine rebuild | Run Install Web tools for this IDE; do not substitute a different version |
| Setup/build download fails | Check internet/proxy access and the log; rerun the failed setup after correcting the connection |
| Access denied / disk full during compilation | Use a writable source workspace/output folder and free space for dependencies and `target` |
| Missing prebuilt export runtime | Repair or reinstall the matching Studio runtime bundle; ordinary export does not compile a replacement |
| No WebGPU adapter / unavailable WebGPU | Use HTTPS or localhost, check browser hardware acceleration and GPU drivers, and try a supported device |
| Silent automatic startup | Click or press a key inside the game to enable audio; check browser/site mute settings |
| Failed to fetch an asset | Check the build/browser log, preserve the ZIP layout and filename case, and confirm all files were uploaded |
| Runtime-created resource names missing | Set `include_all_resources` in the advanced deployment configuration, rebuild, and test those code paths |
| Failed to find index.html | Upload the generated Web ZIP directly with index.html at its root and select the HTML/playable upload |
| Upload accepted but game looks old/unavailable | Check channel and build ID, allow itch.io extraction to finish, then reload; the version label alone is not a build identifier |
| Unsupported Effekseer feature | Adjust the effect to the supported browser features; installing a native SDK does not add browser support |

## Multiplayer and other hosting

Single-player games require only static hosting. Multiplayer additionally needs
the SceneMax WebSocket gateway and the UDP server on a server you operate; a
browser cannot connect directly to raw UDP. In **Project Settings — Multiplayer**,
enable **Browser gateway**, enter its public `wss://` URL, listener and allowed
browser/iframe origin, then Save. Web exports write this URL into `network.json`
automatically. **Build Server** packages the gateway alongside the Zig server;
**Run Server Locally** starts both for local `ws://localhost` testing.
Static itch.io hosting does not run server processes. Public WSS still needs a
TLS reverse proxy. See [multiplayer settings](../../docs/multiplayer.md#browser-clients)
for local defaults and deployment details.

## Distribution maintainer checklist

Ship this guide with matching prebuilt Windows and Web runtime bundles, exporter templates, effect binaries, and their licenses. Rebuild the IDE when embedded exporter templates change. Source distributions intended for engine rebuilding must include the complete matching workspace and toolchain configuration.

Verify installation, compiler-free Windows/Web export, local browser preview, first-interaction audio, supported effects, and multiplayer on a clean Windows x64 machine. Test the resulting Web ZIP on its intended hosting site. Verify optional engine-rebuild setup separately; developer-machine caches do not establish clean-install readiness.

Browser test buttons in both forms use the Multiplayer Deployment progress view.
Local testing starts an IDE-owned loopback Zig server and WebSocket gateway using
temporary connection settings. Remote testing uses the saved deployed gateway.
Both rebuild the browser game without publishing. Stop Test stops the preview
and any owned local test processes; it never stops deployed remote services.
