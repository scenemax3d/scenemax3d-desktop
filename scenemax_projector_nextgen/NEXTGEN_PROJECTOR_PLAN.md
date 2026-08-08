# SceneMax3D NextGen Projector Plan

Target folder: `C:\dev\scenemax_desktop\scenemax_projector_nextgen`

Legacy runtime reference: `C:\dev\scenemax_desktop\scenemax_win_projector`

Date prepared: 2026-08-04

## Goal

Build a Bevy-based SceneMax3D runtime projector that can run projects created by the existing Java Swing IDE. The IDE remains Java Swing. Project creation gains a runtime choice:

- `Classic`: unchanged; runs `C:\dev\scenemax_desktop\scenemax_win_projector`
- `NextGen`: runs the new Bevy/Rust projector in `C:\dev\scenemax_desktop\scenemax_projector_nextgen`

The first usable NextGen milestone should support:

- project metadata and startup script loading
- scene/entity creation for the common 3D GLB/GLTF path
- movement and rotation commands
- model animation playback
- lights
- keyboard input blocks
- multiplayer compatibility with the current SceneMax UDP server/protocol

Deferred by design:

- Minie physics replacement
- Effekseer replacement
- full Swing canvas embedding
- full UI/video/weapons/IK/vehicle feature parity

## Current Findings

The existing projector is centered around:

- `MainWinApp`: loads `running/main` or an entry script, extracts metadata comments, parses startup settings, configures the window, starts `SceneMaxApp`, then calls `run(script)`.
- `SceneMaxApp`: JME `SimpleApplication`; owns runtime registries, assets, controllers, scopes, input, scene graph, lighting, networking, and per-frame controller updates.
- `SceneMaxLanguageParser` and `ProgramDef`: Java compiler layer used by both IDE/projector code.
- controller classes: per-command runtime behavior such as `MoveController`, `MoveToController`, `RotateController`, `ModelAnimateController`, and network command dispatch.
- `MultiplayerNetworkComponent`: a largely self-contained UDP client protocol using fixed message IDs and packet layouts.
- `AssetsMapping`: reads `resources/models/models*.json`, `sprites`, `audio`, `skyboxes`, `materials`, `animations`, `videos`, etc.

The current project assets are mixed. Some models are already `.glb`, but many are `.j3o`. For the first NextGen projector, only GLB/GLTF model assets are supported. `.j3o` is JME-specific and should be treated as a migration/export problem rather than a Bevy runtime feature.

## SDKs And Tools Needed

Required:

- Rust toolchain via rustup, latest stable channel.
- Cargo, installed with Rust.
- Visual Studio 2022 Build Tools on Windows with `Desktop development with C++`, or at minimum latest MSVC, Windows SDK, and C++ CMake tools.
- Git.

Strongly recommended:

- `rust-analyzer` for IDE support.
- `cargo-binutils` plus `llvm-tools-preview` for faster Windows linking with `rust-lld.exe`.
- Blender or an automated conversion/export path for old `.j3o` assets to `.glb`.

Current machine check:

- Rust is installed at `C:\Users\adikt\.cargo\bin`.
- `rustc 1.97.1` and `cargo 1.97.1` were verified from that path.
- MSVC build environment was found at `C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat`.
- An older Visual Studio 2019 Community toolchain also exists at `C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat`.

The Rust path is not guaranteed to be on every shell PATH, so local commands should prefix PATH with `C:\Users\adikt\.cargo\bin` or invoke Cargo by absolute path.

Current Bevy baseline:

- Use Bevy `0.19` initially. Bevy 0.19 was announced on June 19, 2026, and GitHub marks `v0.19.0` as the latest release at the time of this plan.

## Core Architecture

Use a Rust workspace with these crates:

- `scenemax_projector_nextgen`: executable entry point.
- `scenemax_runtime`: Bevy app setup, runtime state, entity registry, scheduling, command execution.
- `scenemax_ir`: neutral serialized runtime data structures.
- `scenemax_assets`: asset index loading, path resolution, model/material/audio lookup.
- `scenemax_multiplayer`: Rust port of the current UDP client protocol.
- `scenemax_test_support`: compatibility fixtures and scripted runtime tests.

Recommended folder layout:

```text
scenemax_projector_nextgen/
  Cargo.toml
  rust-toolchain.toml
  crates/
    projector/
    runtime/
    ir/
    assets/
    multiplayer/
    test_support/
  docs/
    compatibility-matrix.md
    multiplayer-protocol.md
    asset-migration.md
  fixtures/
    scripts/
    projects/
```

## Rust Parser Runtime Contract

The NextGen projector parses SceneMax source directly in Rust at runtime. There is no JSON intermediate file in the critical path because SceneMax supports dynamic `Add "..." Code` statements that require just-in-time parsing while the game is running.

Preferred path:

1. Use `rrevenantt/antlr4rust` to generate a Rust parser from the NextGen SceneMax grammar.
2. Keep the parser crate responsible for turning source text into owned in-memory runtime structures.
3. Keep the runtime crate responsible for translating those structures into Bevy entities, components, events, and controller state.
4. Add grammar coverage incrementally, starting with model declarations, animation commands, movement, rotation, lights, and `Add Code`.
5. Add compatibility fixtures that compare Rust parser output with expected SceneMax behavior for representative scripts.

Avoid:

- using the existing Java parser as the NextGen runtime parser.
- writing JSON runtime IR files for startup or `Add Code`.
- hand-parsing broad language behavior without ANTLR grammar coverage and tests.

The local ANTLR tool is stored at `tools/antlr/antlr4-4.8-2-SNAPSHOT-complete.jar`; grammar generation is handled by `crates/parser/build.rs`.

## Runtime Structure Shape

The parser output should be explicit, stable, and lower-level than raw ANTLR parse contexts.

Minimum v1 sections:

- `schemaVersion`
- `project`: name, guid, resource root, runtime mode, multiplayer metadata
- `window`: width, height, screen mode, disable audio
- `assets`: resolved resource entries from `AssetsMapping`
- `entities`: runtime declarations with type, name, resource, transform, scale, visibility, multiplayer flags
- `actions`: normalized command list
- `functions`: function blocks for later event/input dispatch
- `events`: input handlers, network handlers, collision placeholders

Important rule: Bevy runtime systems should not evaluate raw parser contexts. Expressions used by movement speed, distances, conditions, etc. should be lowered into owned Rust structures such as:

- literal values when compile-time constant, or
- a small expression bytecode/AST that Rust can evaluate.

For milestone 1, support numeric/string/bool literals and simple variable references, then expand.

## Bevy Runtime Mapping

SceneMax runtime state becomes Bevy ECS resources/components:

- `SceneMaxRuntime`: project metadata, pause state, current script/session state.
- `EntityRegistry`: maps SceneMax runtime names like `player@1` to Bevy `Entity`.
- `SceneMaxEntity`: source name, runtime name, var type, scope id, multiplayer flags.
- `TransformAction`: timed movement/rotation state.
- `AnimationAction`: animation request, speed, frame range, loop/protection state.
- `LightRuntime`: light kind and source settings.
- `NetworkIdentity`: network entity id, owner, archetype/source object name.
- `InputBindings`: SceneMax key mapping and active input handlers.

Command execution should be system driven:

- startup systems load assets and spawn declared entities
- update systems advance active actions using `Time`
- network systems receive/send packets and enqueue remote commands
- command systems translate IR commands into Bevy components/events
- animation systems control Bevy animation players/graphs

## Feature Milestones

### Milestone 0: Toolchain And Skeleton

Deliverables:

- Install Rust stable and Windows build tools.
- Create the Rust workspace and Bevy 0.19 executable.
- Add `.cargo/config.toml` and dev profile optimization.
- Launch an empty Bevy window with SceneMax title/window dimensions from CLI flags or a minimal config file.
- Add CI/local command docs: `cargo check`, `cargo test`, `cargo run`.

Exit criteria:

- `cargo run -p scenemax_projector_nextgen -- --help` works.
- Empty Bevy app opens and closes cleanly.

### Milestone 1: Rust Parser And Loader

Deliverables:

- Rust ANTLR parser for a limited SceneMax command set.
- Owned parser output structs for declarations, actions, and dynamic code loading.
- Load `running/main` equivalent metadata:
  - `//$[project]`
  - `//$[project_guid]`
  - `//$[disable_audio]`
  - multiplayer metadata
  - `canvas.size`
  - screen mode
- Asset index loading from project `resources`.

Exit criteria:

- A sample SceneMax script can be parsed and loaded directly by Rust.
- Runtime prints a deterministic summary of entities/actions/assets.

### Milestone 2: Entities And Assets

Deliverables:

- Spawn Bevy camera.
- Spawn 3D models from `.glb`/`.gltf` only.
- Apply resource scale, translation, rotation, initial visibility.
- Spawn primitives used by basic projects: sphere, box, cylinder, quad, cone if cheap.
- Maintain SceneMax runtime names and lookup registry.

Exit criteria:

- A project containing `.glb` assets renders in Bevy.
- Any `.j3o` asset produces a clear migration error with source model name/path.

### Milestone 3: Movement And Rotation

Deliverables:

- Port `move`, `move to`, verbal movement, `rotate`, and `rotate to` basics.
- Port `MotionEase` behavior for common easing modes.
- Support sync/async command scheduling close to `SceneMaxScope.mainController`.
- Support `stop` enough to interrupt active transform/animation actions.

Exit criteria:

- Java projector and Bevy projector produce matching final transforms for fixture scripts.
- Tolerance tests compare position/rotation after scripted durations.

### Milestone 4: Animation

Deliverables:

- Load Bevy animation clips from glTF assets.
- Play named clips with speed.
- Support frame ranges where source metadata provides start/end frames.
- Support loop/protected basics.
- Map animation commands to multiplayer action slot 3.

Exit criteria:

- A character `.glb` plays idle/walk/attack animations by SceneMax command name.
- At least one existing GMTK-style animated model runs after conversion to GLB if needed.

### Milestone 5: Lights

Deliverables:

- Support directional, point, spot, ambient/sky-style fallback lights.
- Support color, intensity, range, angle, shadow flags where Bevy has equivalents.
- Support moving/rotating light entities.

Exit criteria:

- Existing light syntax fixtures render visibly and can be moved/rotated.

### Milestone 6: Input And Basic Control Flow

Deliverables:

- Port key name mapping.
- Support press/release input handlers.
- Support `wait`, `wait for`, and simple `if` where needed by input samples.
- Support variables enough for basic gameplay scripts.

Exit criteria:

- User can move/rotate/animate an entity with keyboard input.

### Milestone 7: Multiplayer

Deliverables:

- Port `MultiplayerNetworkComponent` protocol to Rust.
- Preserve packet constants, endian layout, packet sizes, message IDs, login/session flow, entity create/destroy, command dispatch, transform correction, active action start/end, network variables, and events.
- Keep compatibility with `tools/multiplayer-server` and current Java clients.
- Implement remote entity spawning for supported archetypes.

Exit criteria:

- One Java Classic client and one Bevy NextGen client can join the same session.
- Move/rotate/animate commands replicate both directions for supported entities.
- Transform correction behaves within the same cadence/tolerances as Classic.

### Milestone 8: IDE Integration

Deliverables:

- Add project runtime choice: `Classic` or `NextGen`.
- Persist choice in project metadata.
- When running Classic, use existing launcher unchanged.
- When running NextGen:
  - launch Bevy executable with project path, script path, resource path, window options, and multiplayer metadata
  - capture stdout/stderr into the existing IDE console/error flow
- Package NextGen binary into installer artifacts.

Exit criteria:

- Creating a new project offers both modes.
- Existing projects default to Classic.
- NextGen projects run via Bevy from the IDE.

### Milestone 9: Compatibility Expansion

Candidate order after basics:

- audio playback
- skyboxes and environment shaders
- sprites and labels
- UI `.smui`
- object pools
- attach/detach/grouping
- camera systems
- terrain
- ray checks
- collision/physics replacement
- vehicles
- IK/weapons
- Effekseer replacement
- video playback

## Asset Migration Plan

Policy:

- Native NextGen asset format is GLB/glTF.
- Existing `.glb` assets run directly.
- Existing `.j3o` assets do not load in Bevy.

Migration lanes:

1. Prefer re-export from original source assets using existing IDE/model import pipeline.
2. If original sources are unavailable, investigate a one-time JME-based converter tool that loads `.j3o` and exports GLB/GLTF. This should be treated as tooling, not runtime code.
3. Update `models-ext.json` to point NextGen-compatible resources at `.glb`, while preserving Classic references if needed.

Add a compatibility report command:

```powershell
scenemax_projector_nextgen.exe audit-assets --project C:\dev\scenemax_desktop\projects\MyProject
```

The report should list:

- supported GLB/glTF assets
- unsupported J3O assets
- missing files
- animation metadata issues
- likely material/shader gaps

## Multiplayer Compatibility Notes

The Rust protocol implementation must match the Java constants:

- magic: `0x504d5853`
- version: `1`
- packet byte order: little endian
- max packet size: `1200`
- correction interval: `0.25s`
- character correction interval: `5.0s`
- command dispatch and active action slots:
  - move: `1`
  - rotate: `2`
  - animate: `3`
  - structural base: `64`
  - pos: `252`

Rust should add golden binary packet tests generated from Java fixtures before connecting to the real server.

## Testing Strategy

Use two classes of tests:

1. Headless/domain tests:
   - Rust parser output
   - `Add Code` just-in-time parsing
   - expression evaluator
   - command scheduling
   - transform interpolation
   - multiplayer packet encode/decode
   - asset index loading

2. Bevy runtime tests:
   - spawn model fixture
   - run transform fixture for N seconds
   - run animation fixture
   - connect to local multiplayer server
   - screenshot/smoke tests where practical

Add Classic-vs-NextGen compatibility fixtures:

- `basic_move_rotate.code`
- `move_to_entity.code`
- `animation_named_clip.code`
- `lights_basic.code`
- `input_move_character.code`
- `multiplayer_two_clients.code`

## Main Risks

- `.j3o` assets are not usable directly in Bevy.
- The SceneMax language is broad, so the Rust grammar must grow with fixture coverage to avoid compatibility drift.
- Animation clip naming and frame-range behavior may differ between JME and Bevy/glTF.
- Bevy is still pre-1.0, so lock the Bevy version per milestone and upgrade deliberately.
- Full physics parity cannot happen until a Minie replacement is chosen.
- Java Swing embedding of a Bevy canvas is possible but should not be milestone 1; launch as a separate runtime process first.

## Recommended First Implementation Sprint

1. Install Rust + Windows C++ build prerequisites.
2. Create Rust workspace and minimal Bevy app.
3. Add CLI:
   - `run --script <path> --project-root <path>`
   - `audit-assets --project <path>`
4. Add Rust ANTLR parser coverage for:
   - model declarations
   - light declarations
   - move/rotate/animate actions
   - `Add "..." Code`
5. Spawn a simple GLB scene in Bevy.
6. Port timed move/rotate.
7. Port animation playback for GLB clips.
8. Port light creation.
9. Port enough multiplayer to login, create entity, dispatch command, and receive transform correction.
10. Add IDE project runtime selection after the standalone executable proves the flow.

## Definition Of Done For "Basics Work"

NextGen can run a project that:

- opens a Bevy window with the requested SceneMax canvas size
- loads project resources from the same project folder layout
- spawns GLB 3D entities and primitives
- moves and rotates entities over time
- plays named model animations
- creates directional/point/spot/ambient lights
- responds to keyboard input
- joins the current SceneMax multiplayer server
- creates/syncs supported multiplayer entities
- exchanges move/rotate/animate commands with Classic clients
- reports unsupported J3O/physics/Effekseer features clearly instead of failing silently
