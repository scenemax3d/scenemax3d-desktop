# Package & Deploy

Open **Tools → Package & Deploy…**. The retained Bevy form runs inside the IDE. Web Start is intentionally absent.

## Platform capability

| Platform | Artifact | Requirements |
| --- | --- | --- |
| Windows | One self-extracting `.exe` | Release projector and independent Rust launcher |
| Linux | `.zip` containing one executable game | Matching Rust target/linker, or prebuilt projector; launcher still needs the target toolchain |
| macOS | `.zip` containing one executable game | Apple SDK, normally a Mac; editable architecture, ARM64 by default |
| Android | `.apk` | Explicit SDK builder recipe and Android runtime host |
| iOS | `.ipa` | Explicit SDK builder recipe, iOS runtime host and signing on a Mac |
| Web | `.zip` with `index.html` and Wasm | Explicit browser-runtime builder recipe |

**Android, iOS and Web are builder integrations, not completed projector ports.** The current projector has no Android activity host, iOS app host or browser filesystem/bootstrap. This change does not emit placeholder applications or relabel raw Wasm as a working browser export. Without a recipe, these selections report their missing requirements before building. Implementing/testing those runtime hosts remains necessary for built-in one-click exports on all six platforms.

Linux/macOS package assembly is implemented but not executed on those OSes in this Windows workspace. Cross compilation needs a compatible linker and SDK in addition to the Rust target. The existing automatic Effekseer build stages a Windows DLL. For other targets use a matching prebuilt projector and adjacent libraries, or disable native effects and test the resulting game behavior.

## Workflow

1. Set the filename, release version and output folder. Every build gets a unique child folder. Previous releases are never deleted or overwritten.
2. Review **Shared resources**. New forms include the installation resource directory by default, so development-time shared assets can travel with the game. Clear it only when all required resources are project-local.
3. Select platforms and use **Edit** for their details. Desktop builds accept a Rust triple and optional prebuilt projector. Its adjacent dynamic libraries are included. Native file/folder pickers are available beside paths.
4. **Check requirements** checks configuration, entry-point containment, tools and prebuilt executable format. Compilation, SDK setup, transitive libraries, signing and clean-machine execution still need their respective verification.
5. **Build release** saves dirty documents through the existing save/conflict pipeline. Failed saves, newer edits or a changed project cancel deferred packaging. Stop the running projector first.
6. Follow phase progress and live stdout/stderr. Logs wrap inside the panel; disable **Follow log** to inspect earlier output. **Cancel build** stops the owned tool tree and preserves finished artifacts and logs. Partial artifacts remain private and are removed.
7. A modal **Release complete** or **Build stopped** message clearly marks the end. It offers output/log actions and stays until dismissed. **Open output** opens the artifact directory; **Log folder** locates `build.log`.

Settings are saved at build time in `.scenemax-studio/deployment.json`. Closing/reopening the form also preserves its draft for the same project. Unknown top-level settings fields are preserved. Existing source documents, undo history, Java code and Java settings remain untouched. Relative form paths resolve against the project.

## Fast packaging and resource selection

Packaging always starts at the project's root `main` (the shallowest `scripts/**/main`, matching the project model), never the active editor document. Every reachable script is parsed with `scenemax_parser`; typed `AddCode`, `SwitchTo` and `UiLoad` statements drive traversal, including nested functions, branches and event handlers. Character-mode commands are not scene transitions. The closure retains referenced catalog records, model aliases, glTF/GLB external buffers and textures, font pages, authored definitions and effect sidecars. Catalogs are pruned only inside the isolated snapshot.

Resource-name selection is conservative: literal names in reachable code and UI documents remain eligible even in conditional branches. For resource names assembled dynamically, enable **Include all assets (dynamic resource names)**. Static selection cannot prove the values of arbitrary runtime expressions. `package-size.json` lists the selected resource files, byte counts and excluded bytes in the build folder. Unsupported legacy game content is not converted by packaging.

The release projector is reused by default. **Rebuild engine from source (slower)** explicitly updates it after engine/source changes; missing releases or a required native-effects feature invoke Cargo. The small launcher still gets Cargo's incremental freshness check. Native Effekseer compilation no longer gets forced on every package. A first engine build can still take minutes; ordinary script/asset releases do not rebuild Bevy.

Identical resource payloads are byte-verified, archived once and restored at their original paths by the launcher. Already compressed media is stored directly; other files use fast deflate. No lossy asset conversion is performed. The September 18 Windows benchmark on the user's project produced a 245.9 MiB executable in 17.5 seconds, down from approximately 1 GiB and a six-minute engine compilation. Timings depend on the machine, cache and asset contents.

## Asset sizing report

Every build writes `size-report-analysis.txt` and `size-report-analysis.json` next to `build.log`, using the Java report's title, category/top-contributor sections and JSON fields. The report is generated from the actual staged snapshot, after dependency selection and before platform compilation, and survives later build/upload failures. It is also generated when **Include all assets** is enabled.

Use **Size report** in Build Activity or the result dialog to locate the text report. Categories are sorted by total size; the 40 largest individual files are listed with snapshot-relative paths. Project/shared resources are separate, and categories account for every staged byte without overlapping. Counts explicitly represent files, including catalogs and sidecars, rather than Java's sometimes-partial logical resource counts. Machine-readable byte sizes remain exact; MiB figures are rounded to two decimals. Generation times use UTC.

This is staged content analysis, not compressed artifact size or a GPU/frame-time profiler. The runtime and platform libraries are added later; archive compression and deduplication can reduce the game content size. The report files stay outside the payload and upload directories. Existing `package-size.json` remains available for dependency-selection details.

## Single-file launcher

`Projector/launcher` is a safe-Rust executable with no IDE, Java, Zig or Bevy dependency. The service appends a ZIP containing the projector, scripts, resources, launch manifest and adjacent native libraries. On launch it validates paths and CRCs, extracts into a private temporary directory, passes explicit project/script paths, sets the bundled resource/library paths, waits for the game and cleans up. The caller's working directory is irrelevant. Linux/macOS ZIP entries preserve executable permissions.

Bootstrap errors are written to `scenemax-launch-error.txt` in the system temporary directory. Runtime writes inside the extracted project are temporary; durable game saves must use a user-data location. `SCENEMAX_DISTRIBUTION_EXE` identifies the original package. Forced termination or a machine crash can leave temporary files behind.

The bundle includes adjacent libraries, not an arbitrary SDK installation. Clean-machine testing must still verify graphics drivers, OS runtime prerequisites and transitive native dependencies. Code signing/notarization is not implemented by this form. Cargo is required even with a prebuilt projector because the launcher is compiled independently. `SCENEMAX_BUILD_WORKSPACE` overrides the source-workspace location.

## itch.io

Enter `https://username.itch.io/game` or `username/game` and review each platform's channel. **Sign in with Butler…** starts Butler's browser authentication. Alternatively it inherits `BUTLER_API_KEY`. Credentials are never entered into or serialized by this form, and the Java database's private keys are not migrated.

New forms import matching non-secret page/channel defaults from the Java project catalog. Butler detection checks shipped tools and the Windows itch application's selected Butler version, then falls back to PATH. Its path is also editable.

Automatic upload starts only after all selected artifacts succeed. Each channel uses `butler push` and `--userversion`. ZIPs are passed directly, so their contents become the upload root. Executable/APK/IPA files are pushed from their dedicated platform directory. Source folders, settings and build logs are not uploaded. A failed upload preserves local artifacts. **Upload / retry existing builds** publishes those outputs without compiling again; keep their platform selection. Retrying can re-push already successful channels.

The game page must already exist. Publishing updates its channels immediately. For Web, mark the page/upload playable in the browser through itch's UI after the first upload; see the official [Butler manual](https://itch.io/docs/butler/pushing.html).

## SDK builder contract

Android/iOS/Web accept an explicit JSON recipe naming an existing builder and structured arguments:

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
