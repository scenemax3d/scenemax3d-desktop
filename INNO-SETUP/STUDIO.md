# SceneMax Studio installer (Rust IDE)

Run `./INNO-SETUP/build-studio-installer.ps1` from the repository root. Requires
the pinned Rust toolchain, Windows build prerequisites and Inno Setup 6. The script
builds release IDE/projector binaries, compiles a small native launch helper,
stages a separate distribution and runs Inno Setup. `-SkipBuild` reuses existing
release binaries; only use it after a successful current release build.

The installer uses its own application ID and the current user's writable
Programs directory. The final installer is copied to
`INNO-SETUP/Output/scenemax-studio-<version>.exe` after compilation succeeds.
Rebuilding the same version replaces that file. The isolated build remains in
`scenemax3d_nextgen/target/studio-installer/<id>/output`; adjacent
`size-report.json` records the final installer path, exact installer/payload bytes,
file count and SHA-256.

The default is `%LOCALAPPDATA%\Programs\SceneMax Studio`. Setup does not reuse a
previous installation folder automatically; users upgrading a custom installation
must select that folder again. The folder page stays visible. Windows and both
Program Files directories are rejected because the IDE needs ongoing write access
for builds. Other destinations must pass a temporary directory/file write-and-delete
probe, in the destination or its nearest existing ancestor. Validation preserves
existing files and does not create the chosen destination. It runs on leaving the
folder page and again immediately before installation, including `/DIR` and silent
installs. Permissions or locks can still change after validation.
Run `./INNO-SETUP/test-studio-location.ps1` for executable Inno validation tests.

Included: IDE, desktop projector, native effect bridge and import library,
standard resources, import tools, matching Rust workspace sources (including
vendored Web renderer), setup guide, and licenses. Game projects, build caches,
debug symbols and Git data are excluded. The bundled native bridge supports
desktop rebuilding without shipping the upstream effect editor source checkout.

The installer offers **Download and install build tools**, selected by default.
It scans existing tools, installs missing Rust, Microsoft C++/Windows SDK/CMake,
then the pinned Wasm target and wasm-bindgen, and saves the detected paths for
IDE exports. Downloads use the same official-package worker as Settings.
Internet and several GB are required; Microsoft Setup may request administrator
access. The installer itself runs as the current user. Native Effekseer uses the
bundled bridge/import library, so a separate Vulkan or Effekseer SDK is unnecessary.
A native-library load check detects missing runtime dependencies; a working GPU
and Vulkan-capable graphics driver are still required for rendering.

Failures show Retry/Cancel and keep the IDE installed, but do not claim build
readiness. The finish page offers "Launch SceneMax Studio" even when build-tool
setup is incomplete; it starts the launcher in the selected installation folder.
Silent installations do not launch the IDE. If a reboot is required, setup stops before
Web tool installation, reports exit code 3010 and asks the user to restart manually.
Setup never restarts Windows automatically. After a failure or reboot, use
**Set up SceneMax build tools** in the Start menu, or the individual buttons in
**File > Settings > Build Configuration**. Installed components are skipped on retry.
The Start-menu helper writes its outcome to the same log and can also be run in
PowerShell for its exit code. Logs append to
`%LOCALAPPDATA%/SceneMax/setup/dependencies.log`; detected paths are atomically saved
to `%LOCALAPPDATA%/SceneMax/build-tools.json`.

For unattended installs, `/TASKS="buildtools"` selects prerequisite setup;
`/TASKS=""` skips it. A cancelled/failed dependency step returns 1; required restart
returns 3010; success returns 0. Tool installers may require interactive UAC approval,
so silent Studio setup does not guarantee unattended dependency installation.
The `-CheckOnly` option on `setup-studio-dependencies.ps1` detects/saves installed
paths and validates the native bridge without downloading or installing anything.

Rust, Microsoft build tools and Web tools are downloaded at install time rather
than embedded; their sizes and future Cargo caches are not part of the installer
size. The first build can still download Cargo dependencies. Optional Butler and
its account sign-in remain separate, since they are not needed for game builds. The native
`SceneMaxStudio.exe` launcher sets child-process paths for the bundled source,
resources and import/effect tools without modifying the user's global environment.
Launch via its shortcut rather than directly running `scenemax_ide.exe`.

This is an unsigned preview package, not a certified clean-machine release.
Validate installation/uninstallation and fresh tool setup in a clean Windows VM
before public distribution. Do not present a staged startup check on a developer
machine as proof of a fresh installation. Code signing is not configured here.

Dependency orchestration regression tests (no downloads/installations):
`powershell -NoProfile -ExecutionPolicy Bypass -File INNO-SETUP/test-studio-dependencies.ps1`.

## Installation history and packaged assets

Installed copies use `user-state/last-project.json` inside their writable installation
folder. A new installation opens the bundled `projects/Hello World` project. Its
`scripts/main` prints a greeting, creates a dragon, loops its flight animation,
and turns it through 360 degrees over 30 seconds. Its `resources` folder contains
the glTF model, textures, and a named flight animation record. Opening a project
explicitly records it for that installation; upgrading in place preserves that history.
Development launches retain their existing `%LOCALAPPDATA%/SceneMax/Studio` history.
The `.scenemax-studio-installation` marker also prevents automatic discovery of a
parent development catalog. Personal development projects are never bundled or deleted. Starter project files
and the installation catalog are installed only when missing, so upgrades and
uninstallation preserve user edits.

Packaging excludes test fixtures, the development `Tests` tree, and all `.j3o` and
`.j3odata` files. The shipped model catalog uses existing glTF/GLB alternatives when
available and omits models with no supported alternative. These are not automatic
J3O conversions: recover original model sources and export to glTF/GLB to restore
legacy-only models. Source resources in the development checkout remain intact.
Upgrades remove only exact previously bundled fixture/model files listed in a
build-generated cleanup manifest; other user files and history are preserved.
The included source workspace supports game builds; running its development test
suite requires the full repository and its fixtures.

Package regression checks (run against a staged payload):

```powershell
.\INNO-SETUP\test-studio-payload.ps1 -Payload <staging-folder>\payload
.\INNO-SETUP\test-studio-cleanup.ps1 -Manifest <staging-folder>\obsolete-files.iss
```

The cleanup test uses an isolated temporary installation and checks that a
user-created model survives removal of the exact obsolete bundled files.

The default installer version is `3.0`, producing `scenemax-studio-3.0.exe`.
The Setup display name and Windows installed-app version use the same version.

The default model library includes the animated `sinbad` model as a self-contained
GLB with embedded textures, 69 joints and 15 source clips. Its original license
and conversion notes are shipped in `resources/Models/Sinbad`. Conversion is an
offline preparation step; installation and game builds do not require a converter.

The green low-poly `ninja` is also bundled as `Models/Ninja/Ninja.glb`, with an
embedded texture, 28 joints and all 20 source animation clips. Its existing catalog
scale, orientation and character settings are preserved. Source provenance and
conversion details are included beside the model.
