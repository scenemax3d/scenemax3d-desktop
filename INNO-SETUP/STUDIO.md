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

## Distribution contents

The installer includes the IDE, Windows projector and packaging launcher, WebAssembly
runtime and matching JavaScript bindings, native effects library, standard assets,
model import tools, prebuilt multiplayer servers and browser gateways, guides,
and license notices. It excludes Rust sources, Cargo/CMake build files, compiler
setup scripts, development fixtures, caches, and J3O models.

Ordinary Windows and Web exports need no compiler or internet connection. Launch
Studio through its shortcut so the native launcher sets the bundled resource and
tool paths. A Vulkan-capable GPU and current drivers are required for native
rendering; browser games require WebGPU. Butler and its account sign-in are
optional for itch.io publishing and are acquired separately.

Engine development requires a separate matching source checkout and toolchain.
The installer does not offer a build-tools setup task. On the maintainer machine,
the installer build requires the pinned Rust toolchain, Windows native build
prerequisites, `wasm32-unknown-unknown`, `x86_64-unknown-linux-musl`, the matching
wasm-bindgen CLI, and Inno Setup 6. `-WasmBindgen` can select the required binding
generator. Prebuilt Zig server templates must be available for the distributed
platforms. `-SkipBuild` skips compilation, but still needs matching compiled
artifacts and binding generation.

This installer is unsigned. Validate installation/uninstallation, compiler-free
Windows/Web export, browser preview, and multiplayer on a clean Windows x64
machine before distribution. A staged startup check on a developer machine is
not proof of a fresh installation.

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
Running the development test suite requires a separate full repository checkout and its fixtures.

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
