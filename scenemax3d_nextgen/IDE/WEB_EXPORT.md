# Create and publish a Web game

## What a new installation needs

Web export is built into the Rust/Bevy SceneMax IDE, but the IDE executable alone
is **not a complete build installation**. It compiles the browser runtime locally.
The SceneMax Studio preview installer (`INNO-SETUP/build-studio-installer.ps1`)
includes the matching source workspace and configures its paths through the
SceneMax Studio shortcut. Keep **Download and install build tools** selected during
installation to install the prerequisites automatically, or install them later
through Settings.
The Studio installer still needs clean-machine validation.

| Requirement | How to obtain it | When needed |
| --- | --- | --- |
| Matching SceneMax source workspace | Full matching source distribution/checkout, including vendored dependencies | Every built-in Web build |
| Rust and Cargo | Settings > Build Configuration > Install / repair Rust | Every built-in Web build |
| Microsoft C++ x64 tools and Windows SDK | Install C++ / SDK / CMake | Windows host compilation, including the Web binding generator |
| Rust browser target and matching wasm-bindgen | Install Web tools | Every built-in Web build |
| Internet and free disk space | Allow several GB for tools, downloaded dependencies and compilation cache | Setup and first build; later builds may need dependency downloads |
| WebGPU-capable browser and GPU/driver | Use a supported browser with hardware acceleration | Local testing and players |
| Butler | Optional download from [itch.io](https://itchio.itch.io/butler) | Automatic itch.io upload only |

No separate browser SDK, Emscripten, Node.js, Python, Java/JVM or Effekseer SDK is
required for the built-in exporter and local preview. The Windows SDK is installed
by the C++ setup button. CMake is included by that button for native desktop
effects; browser effects use bundled JavaScript/Wasm binaries. Creating/editing
effect assets in an external editor is separate from exporting existing effects.
Players do not need any authoring tools installed.

## 1. Keep the source workspace available

The Studio installer defaults to `%LOCALAPPDATA%\Programs\SceneMax Studio` and
checks that the selected folder is writable before installing. It rejects Windows
and Program Files locations. Run the installer normally, without administrator
elevation. A previous installation path is not reused automatically; select your
existing custom folder explicitly when upgrading it.

Keep the complete matching `scenemax3d_nextgen` directory, including `Cargo.toml`,
`Cargo.lock`, `rust-toolchain.toml`, `.cargo`, `Projector`, `Engine`, `Language`,
`Common`, `IDE`, and `third_party`. Do not copy just `Projector/app` or just the IDE
executable. Cargo must be able to resolve the full workspace and local dependencies.
Use a writable location: the exporter writes compilation results into `target`.
The [source repository](https://github.com/scenemax3d/scenemax3d-desktop) contains
the workspace; select the revision matching your IDE instead of mixing versions.

The Studio installer shortcut configures the bundled workspace automatically.
Source-built IDEs default to the workspace where they were compiled. If you move
the installation, or receive an IDE binary compiled elsewhere, set the Windows
user environment variable `SCENEMAX_BUILD_WORKSPACE` to your matching workspace
folder and restart the IDE. For example, in PowerShell:

```powershell
[Environment]::SetEnvironmentVariable('SCENEMAX_BUILD_WORKSPACE', 'C:\SceneMax\scenemax3d_nextgen', 'User')
```

Replace the example path with your actual folder. A new IDE process must inherit
the variable (reopen its launcher/terminal too). This is the engine source folder,
not your game's project folder. The prerequisite installer installs build tools;
it does not download SceneMax sources. **Check requirements** reports missing
workspace files before compilation.

## 2. Install build tools on Windows x64

In the Studio installer, **Download and install build tools** is selected by default.
It downloads missing components in order and saves their paths for the IDE.
Existing compatible installations are reused. Internet, additional disk space,
and administrator approval for Microsoft Setup may be required. A failed step
shows Retry/Cancel; a requested reboot stops the remaining steps and never
restarts Windows automatically. After restarting, use **Set up SceneMax build tools**
in the Start menu. The same shortcut can retry a failed installation. Review
`%LOCALAPPDATA%\SceneMax\setup\dependencies.log` if setup is incomplete.
The native Effekseer bridge is bundled and checked for missing load dependencies;
GPU driver support must still be verified on the actual machine. No full Vulkan
SDK is installed because it cannot substitute for a working GPU driver.

If you skipped automatic setup or want to repair individual tools, open
**File > Settings > Build Configuration**. Opening the dialog scans only;
downloads start when you choose an installation button.

1. Choose **Install / repair Rust** if Rust is missing. Wait for completion.
2. Choose **Install C++ / SDK / CMake** if the compiler or Windows SDK is missing.
   Approve Microsoft's administrator prompt. If setup requests a reboot, save
   your work and restart Windows before continuing.
3. Choose **Install Web tools**. It installs `wasm32-unknown-unknown` for the pinned
   Rust toolchain and compiles the matching `wasm-bindgen-cli`. This can take
   several minutes and needs the C++ tools even though the game runs in a browser.
4. Choose **Refresh detection**. Check that Rust, Microsoft C++ compiler, Windows
   SDK, Rust browser target and Web bindings are installed. A missing CMake alone
   concerns native effects, not the browser renderer.

Keep the IDE running until setup finishes; closing Settings leaves its worker
running. Failed installation details appear in the console area. The current
source pins Rust **1.97.1** and wasm-bindgen **0.2.108**; use the versions requested
by your IDE, not an arbitrary latest binding generator. Web tools keeps its
binding generator in `%LOCALAPPDATA%\SceneMax\web-tools\<version>\bin` and preserves
other installed versions. Detection saves paths in
`%LOCALAPPDATA%\SceneMax\build-tools.json`; refresh after moving/installing tools.
See [Rust's Windows prerequisites](https://rust-lang.github.io/rustup/installation/windows-msvc.html)
for manual C++ installation.

Automatic prerequisite setup supports **Windows x64 only**. On other hosts,
install Rust through rustup and your host's compiler/linker prerequisites manually.
From the matching workspace, run `rustup target add wasm32-unknown-unknown` and
`cargo install wasm-bindgen-cli --version 0.2.108 --locked`, using the version
required by that workspace. Ensure Cargo, rustc and wasm-bindgen are on PATH.
These hosts still need platform-specific validation; Windows testing does not
establish Linux/macOS export support on a clean machine.

## 3. Build and preview

1. Open your game project and stop its running preview.
2. Open **Tools > Package & Deploy**. Set the game filename, release version and
   output folder. Select **Web**; deselect other platforms if you only want Web.
3. Use **Edit** on Web and leave **Custom Web recipe** blank for the built-in
   exporter. **Open Web build setup** opens the prerequisite dialog directly.
4. Optionally choose a PNG/JPEG loading background, heading, loading text and
   footer. The default footer is `powered by SceneMax3D`; the progress bar remains.
5. Review shared resources. Keep **Include all assets** off for referenced-asset
   selection; enable it if scripts construct resource names dynamically and
   static selection cannot identify them. Identical file contents are downloaded
   once while all original logical paths remain available.
6. Leave automatic upload off for a local test. Click **Check requirements**, fix
   reported problems, then **Build release**. First compilation can take a long
   time; subsequent builds reuse unchanged compiler output. Checks do not replace
   an actual build or browser test.
7. After completion, choose **Run Web locally**. SceneMax runs its own localhost
   server and opens the browser; no extra server software is needed. Rebuild after
   source/asset changes: this previews the completed ZIP. **Stop local server**
   stops it; exiting the IDE also stops it.

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
| Missing browser source / Cargo workspace | Install the full matching source distribution in a writable folder and set `SCENEMAX_BUILD_WORKSPACE`; restart the IDE |
| Rust, linker, compiler or Windows SDK missing | Run the relevant setup buttons, complete any requested reboot, then refresh detection |
| Wrong wasm-bindgen version or missing Wasm target | Run Install Web tools for this IDE; do not substitute a different version |
| Setup/build download fails | Check internet/proxy access and the log; rerun the failed setup after correcting the connection |
| Access denied / disk full during compilation | Use a writable source workspace/output folder and free space for dependencies and `target` |
| No WebGPU adapter / unavailable WebGPU | Use HTTPS or localhost, check browser hardware acceleration and GPU drivers, and try a supported device |
| Silent automatic startup | Click or press a key inside the game to enable audio; check browser/site mute settings |
| Failed to fetch an asset | Check the build/browser log, preserve the ZIP layout and filename case, and confirm all files were uploaded |
| Runtime-created resource names missing | Enable Include all assets, rebuild, and test those code paths |
| Failed to find index.html | Upload the generated Web ZIP directly with index.html at its root and select the HTML/playable upload |
| Upload accepted but game looks old/unavailable | Check channel and build ID, allow itch.io extraction to finish, then reload; the version label alone is not a build identifier |
| Unsupported Effekseer feature | Adjust the effect to the supported browser features; installing a native SDK does not add browser support |

## Multiplayer and other hosting

Single-player games require only static hosting. Multiplayer additionally needs
the SceneMax WebSocket gateway and the UDP server on a server you operate; a
browser cannot connect directly to raw UDP. Configure the exported `network.json`
with a public `wss://` URL and allow the actual browser/iframe origin at the
gateway. Static itch.io hosting does not run server processes. Gateway build,
TLS and configuration instructions are in [DEPLOYMENT.md](DEPLOYMENT.md#browser-exports)
and in each exported ZIP's `README.txt`.

## Distribution maintainer checklist

Ship this guide and the matching full build workspace, including lockfile, toolchain
file, Cargo configuration, workspace members and vendored dependencies. Configure
`SCENEMAX_BUILD_WORKSPACE` for its actual installed, writable location; the binary's
compile-time developer path is not a portable installation path. Bundled exporter
templates/effect binaries are embedded in the IDE, so rebuild the IDE when they
change. Do not claim clean-install readiness based on a developer machine's caches.
Before releasing, test setup, Web compilation, local preview, first-interaction
audio and effect rendering on a clean Windows x64 machine without preinstalled
Rust/Visual Studio/Butler, then test the resulting ZIP on the hosting site.
