# SceneMax3D NextGen

The independent Rust/Bevy products live in one Cargo workspace. The IDE is not a subproject of the projector. Each product can be built and shipped independently; workspace-level versions, policy, fixtures and build artifacts are shared.

Continuing the migration in a new conversation? Start with the [migration handoff](docs/MIGRATION_HANDOFF.md) for current status, decisions, known gaps and verification commands.

| Component | Responsibility |
|---|---|
| `IDE/app` | Native IDE executable, composition, application commands and feature views |
| `IDE/core` | Pure Rust buffers, selections, undo history, editing and workspace state |
| `IDE/services` | Background storage/search/recovery, syntax checks, owned projector process and logs |
| `IDE/ui` | Reusable retained Bevy widgets, input and theme |
| `Projector/app` | Standalone projector executable |
| `Engine` | Generic runtime, scripting, VM, runtime UI and multiplayer |
| `Language` | Parser and IR |
| `Common/assets` | Shared asset catalog and resolution |
| `Tools` | Verification and maintainer tools |
| `Tests/fixtures` | IDE sample project and existing local projector fixtures |
| `Tests/artifacts` | Ignored local captures/logs preserved from migration |

Read the [architecture and coding standards](docs/ARCHITECTURE.md), [IDE guide](IDE/README.md), [projector guide](Projector/README.md), and [migration roadmap](../docs/RUST_IDE_MIGRATION.md).

## Build and run

Rust **1.97.1** is pinned in `rust-toolchain.toml`. Rustup installs the pinned compiler, Rustfmt and Clippy when needed. Windows builds use the MSVC target and the Rust-provided LLD linker. Normal builds and both Rust executables require no Java. Explicit ANTLR regeneration is still a temporary maintainer dependency, tracked for removal.

From this directory:

```powershell
# Independent product builds (Bevy features remain product-specific).
cargo build --locked -p scenemax_ide
cargo build --locked -p scenemax_projector_nextgen

cargo run --locked -p scenemax_ide -- --project-root Tests/fixtures/ide/sample_project --script scripts/main
cargo run --locked -p scenemax_projector_nextgen -- --help

# Architecture, formatting, strict IDE Clippy, workspace checks and tests.
./Tools/verify.ps1
# Also link both products independently:
./Tools/verify.ps1 -Build
```

`cargo` invoked from a component folder discovers this workspace automatically. Do not create nested workspaces, duplicate lockfiles, or duplicate copies of runtime/parser code. Package and executable names remain unchanged for launcher/export compatibility. The old `scenemax_projector_nextgen` source directory has been replaced by this directory.

The Swing launchers discover the new workspace and adapt persisted paths pointing to the removed directory, while preserving valid custom locations. This compatibility adapter is Java-only and is not a dependency of either Rust executable.
