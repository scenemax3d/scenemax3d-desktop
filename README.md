# SceneMax3D Developer Studio

A desktop 3D scene editor and game development environment built with Java, [JMonkeyEngine 3](https://jmonkeyengine.org/), Swing, native runtime integrations, and a growing toolchain for shipping real interactive 3D games.

SceneMax3D lets you visually design 3D scenes, write game logic using a built-in scripting language, extend projects with custom Java runtime code, run multiplayer sessions, and export projects as standalone PC executables or Android apps.

<img width="1600" height="846" alt="image" src="https://github.com/user-attachments/assets/8e83d0b0-950e-425d-94a0-d38db5fc4fd3" />

SceneMax3D was first created by Adi Barda in 2005 as a C++/DirectX game engine and scripting language for education purposes.
In 2017-2018 it was rewritten in Java from scratch using JMonkeyEngine3 as the target renderer and ANTLR4 for the language parsing. 
In Mar-22, 2026 the entire solution was uploaded to GitHub as an open source (MIT license) project.
On August 08 2026, the initial next generation projector, based on Rust/Bevy engine was committed looking ahead as the target renderer mainly for web browser games. SceneMax3D will continue to support both the "Classic" Java games and the "NextGen" Rust ones. The choreographic programming language will be similar for both platforms.

## Product Website
[SceneMax3D](https://scenemax3d.com/cook-book/)

## Documentation

SceneMax3D includes project documentation in the [`docs/`](docs/) folder, covering the scripting language, control flow, and other engine concepts.

For IDE extension development, see the enhanced plugin system guide in [`docs/plugin-system.md`](docs/plugin-system.md). It covers plugin discovery, lifecycle, toolbar/menu actions, views, asset providers, settings, model import/preview, packaging, and a minimal plugin example.

For a real-world enhanced plugin reference, see the Meshy AI plugin guide in [`docs/meshy-ai-plugin.md`](docs/meshy-ai-plugin.md), which explains the Meshy integration, community search, rig/animation filters, static model imports, preview/import flow, and source-code map.

For AI-assisted workflows, see the built-in MCP and Local Gemma guide in [`docs/built-in-mcp-server.md`](docs/built-in-mcp-server.md), which explains setup for Claude, Codex, Claude Desktop, and local Gemma-powered assistance.

For a tool-by-tool reference covering purpose, common inputs, outputs, and agent-facing usage patterns, see [`docs/mcp-tools-reference.md`](docs/mcp-tools-reference.md).

For project-level native Java runtime code, see [`docs/java-extensions.md`](docs/java-extensions.md).

For networked games, entity synchronization, packaged server setup, and diagnostics, see [`docs/multiplayer.md`](docs/multiplayer.md).

For weapon authoring and runtime equip/posture examples, see [`docs/weapons-designer.md`](docs/weapons-designer.md).

For throw motion authoring, projectile/return motion setup, and runtime examples, see [`docs/throw-motion.md`](docs/throw-motion.md).

For inverse-kinematics authoring, joint setup, preview simulation, and runtime IK commands, see [`docs/ik-designer.md`](docs/ik-designer.md).

For analyzing bundled 3D model animations and saving named frame ranges, see [`docs/model-animation-analyzer.md`](docs/model-animation-analyzer.md).

For Effekseer particle-effect scripting and runtime examples, see [`docs/effects.md`](docs/effects.md).

For hands-on examples, see the demo projects guide in [`projects/readme.md`](projects/readme.md), which walks through the sample games and explains how they are structured.

## Ready-to-Use Binaries

If you want to try SceneMax3D without building from source, download the Windows setup binaries from the [latest GitHub release](https://github.com/scenemax3d/scenemax3d-desktop/releases/latest).

## Features

- **Visual 3D Scene Designer** -- drag-and-drop scene composition with real-time preview
- **Custom Scripting Language** -- purpose-built DSL (ANTLR4-based parser) for game logic and interactivity
- **Custom Java Extensions** -- add native Java app states beside SceneMax scripts with `Java.attach`, scope-aware entity lookup, direct JME access, IDE compile diagnostics, and packaging support
- **Code Editor** -- syntax-highlighted editor with code folding 
- **Multiplayer Runtime** -- mark entities as `multiplayer` and synchronize creation, destruction, transforms, movement, rotation, animation, attachments, and IK through an ultra-efficient low-level UDP server written in [Zig](https://ziglang.org/)
- **Effekseer Particle Effects** -- create and play advanced real-time particle effects integrated into scenes and gameplay
- **Video Rendering** -- import FFmpeg-supported video files and render them onto scene objects at runtime
- **Physics Engine** -- integrated Minie / Bullet physics
- **3D Model Import** -- load models into your scenes
- **Cinematic Camera System** -- build dynamic camera moves, chase cameras, and dramatic gameplay cutaways
- **Weapons Designer** -- author `.smweapon` assets with equip postures, attachment points, preview models, runtime equip/unequip commands, and weapon collider support
- **Throw Motion Designer** -- author `.smmotion` assets for throws, projectiles, boomerang-style returns, homing behavior, and reusable motion runtime commands
- **IK Designer** -- author `.smik` inverse-kinematics assets with armature scanning, solver templates, skeleton preview, animation blending tests, and runtime layer commands
- **Multi-Project Support** -- manage multiple projects from a single workspace
- **Enhanced Plugin System** -- extend the IDE with Java plugins that add toolbar/menu actions, Swing views, asset providers, settings, and model import/preview workflows
- **Built-in MCP Server & AI Console** -- connect Claude Code, Codex, Claude Desktop, and Local Gemma to live SceneMax project tools through the IDE
- **Classroom Mode** -- collaborative features for educational settings
- **Export Targets** -- package desktop builds, include project assets and Java extensions, generate Web Start bundles, and prepare multiplayer metadata/server artifacts

## Requirements

- Java 11 or later
- Windows (primary platform)
- No separate `ffmpeg.exe` install is required for normal builds; SceneMax uses the JavaCV / Bytedeco FFmpeg runtime libraries declared in Gradle.
- Java extension authoring and packaging requires running SceneMax with a JDK so the Java compiler API is available.
- Multiplayer server builds require Zig when a prebuilt server executable is not already available for the selected target platform.

## Building from Source

SceneMax3D uses Gradle as its build system.

```bash
# Clone the repository
git clone https://github.com/scenemax3d/scenemax3d-desktop.git
cd scenemax_desktop

# OPTIONAL - Copy the example config and fill in your values
cp config.properties.example config.properties
# Edit config.properties with your credentials (FTP, API keys, etc.)

# Build the project
./gradlew build
```

Notes:

- The Gradle wrapper (`gradlew`, `gradlew.bat`, and `gradle/wrapper/*`) is the supported build entry point and is included in the repository.
- The build automatically generates the SceneMax parser jar from `scenemax3d_parser/SceneMax.g4` before compiling the compiler and projector modules.
- Parser generation is implemented in Gradle and works from the root wrapper on Windows, Linux, and macOS.
- The ANTLR tool is resolved automatically from Maven Central during the build; the local parser convenience script is not required for a clean clone build.
- `scenemax3d_parser/build.bat` is kept as a Windows convenience script and is non-interactive. It uses `JAVA_HOME` when available, otherwise it falls back to `java`, `javac`, and `jar` from `PATH`.

## Configuration

Application credentials and service endpoints are stored in `config.properties` (git-ignored).
Copy `config.properties.example` to `config.properties` and fill in your values before running.

See `config.properties.example` for all available settings.

## Custom Java Extensions

SceneMax scripts are still the main way to describe scenes, gameplay flow, animation, camera behavior, UI, and designer-friendly logic. For lower-level or performance-sensitive work, projects can now include custom Java extension folders under the project `scripts/` tree.

Create a Java extension in the IDE, then attach it from SceneMax script:

```scenemax
player => sinbad
camera => Camera.System.follow(player)

Java.attach "PlayerNativeLogic"
```

Java extension classes extend `SceneMaxBaseAppState`, receive the active `SceneMaxScope`, and can resolve SceneMax entities into native JME objects:

```java
import com.jme3.scene.Spatial;
import com.scenemaxeng.projector.SceneMaxBaseAppState;

public class PlayerNativeLogic extends SceneMaxBaseAppState {
    private Spatial player;

    @Override
    public void update(float tpf) {
        if (player == null) {
            player = getEntitySpatial("player");
        }
        // Per-frame native runtime logic can go here.
    }
}
```

The run and package flows compile extension source files, build runtime extension jars, write an extension index, and make those jars available to the projector. Compile failures are reported in the IDE with source paths, line/column details, source context, and classpath information.

Use Java extensions for direct JME access, reusable app states, custom algorithms, procedural animation, native library integration, advanced instrumentation, or code that needs tight per-frame control. See [`docs/java-extensions.md`](docs/java-extensions.md) for the complete lifecycle, scope model, examples, and troubleshooting guide.

## Multiplayer

SceneMax now has an entity-based multiplayer runtime. Add the `multiplayer` attribute to shared actors, and SceneMax registers them with a UDP session server:

```scenemax
player => fighter1_native: multiplayer, pos (0,0,0), scale 3, collision shape none async
camera.chase player

when key up is pressed do
  player.move forward 1 in 0.1 seconds
end do
```

Local input stays local, while commands against registered multiplayer entities are broadcast and replayed on the other clients. The runtime currently synchronizes networked entity creation/destruction for models, primitives, labels, and Effekseer effects, plus transform corrections, movement, rotation, model animation, character mode switch/clear commands, Effekseer playback, attach commands, IK apply/remove/layer commands, and `network var` state for scalar values and arrays. Timed and persistent structural actions plus network variables are included in late-join snapshots so new players can enter a running scene with the current shared state.

The visual designer can also emit multiplayer-ready code. Enable the multiplayer flag on an entity, save the design, and the generated `.code` file includes the correct `multiplayer` attribute for supported model and primitive entities.

SceneMax includes a dedicated multiplayer server toolchain under `tools/multiplayer-server/`. The server is written in the low-level [Zig](https://ziglang.org/) language and designed for ultra-efficient UDP networking: small native binaries, direct packet handling, low overhead, and platform-specific executable builds for shipping multiplayer games. Project Settings can copy or build a platform server, patch it with game name, project GUID, UDP port, project path, and password settings, and packaged games embed multiplayer metadata into the generated `main` script. The default local multiplayer port is UDP `9001`, and the client writes diagnostics to `scenemax-multiplayer-client.log`.

See [`docs/multiplayer.md`](docs/multiplayer.md) for architecture, session/scene behavior, synchronized command coverage, packaging details, runtime properties, and the production checklist.

## Video Rendering

SceneMax can import video files and render them directly inside a running scene. The feature is backed by JavaCV and FFmpeg: the designer uses FFmpeg to probe and preview video assets, and the runtime decodes frames on a background thread and applies them as a texture to a target object.

Use this for in-world screens, animated billboards, cutscene panels, portal surfaces, UI-like scene props, or other places where a movie should appear on a 3D object.

### Importing A Video

1. Open or create a SceneMax project.
2. Choose **Assets -> Import Video...**.
3. Select an MP4, MOV, MKV, AVI, WebM, MPEG, or another FFmpeg-supported video file.
4. Review the detected dimensions, duration, frame rate, format, and live preview.
5. Optionally edit the asset name and choose a preview shape: `Pane`, `Box`, or `Sphere`.
6. Click **Import Video**.

Imported videos are copied into the active project under:

```text
resources/videos/<assetId>/
```

SceneMax also updates:

```text
resources/videos/videos-ext.json
```

That index stores the asset id, video path, original import path, preview shape, dimensions, duration, frame rate, and format metadata.

### Rendering A Video In Code

Declare a video resource with `videos.<assetId>`, create a renderable target object, then play the video on that target:

```scenemax
screen => quad : size (16,9), pos (0,3,8)
intro_video => videos.intro_clip

intro_video.play : target screen
```

The target can be any renderable scene object. At playback time, SceneMax replaces the target object's material with an unshaded video texture and updates that texture as FFmpeg decodes frames.

You can also render only part of a clip, reverse it, or loop it:

```scenemax
screen => quad : size (16,9), pos (0,3,8)
intro_video => videos.intro_clip

intro_video.play : target screen, start "00:01:00", end "00:02:59", loop
```

```scenemax
screen => quad : size (16,9), pos (0,3,8)
countdown => videos.countdown_clip

countdown.play : target screen, start "00:00:05", end "00:00:15", reverse
```

Supported play options:

| Option | Meaning |
|--------|---------|
| `target <object>` | Required. The scene object that receives the video texture. |
| `start "<time>"` | Optional start timestamp. Colon-separated values are supported, such as `"00:10"` or `"00:01:30"`. |
| `end "<time>"` | Optional end timestamp. Playback stops when this point is reached. |
| `reverse` | Plays frames from the end timestamp back toward the start timestamp. |
| `loop` | Restarts playback when the selected range ends. |

When a script declares `videos.<assetId>`, SceneMax marks that video as used so desktop packaging/export can include the referenced video file and metadata automatically.

## Visual Effects With Effekseer

SceneMax now includes an Effekseer-based visual-effects stack for imported particle systems. Effekseer assets stay in their native format, are stored under `resources/effects/<assetId>/`, and can be declared in scripts with `effects.effekseer.<assetId>`.

Import effects from **Assets -> Import Effekseer Effect...**. SceneMax accepts `.efkefc`, `.efkproj`, and `.efk` files and copies related texture, model, sound, material, and curve assets into the project effect folder. If you work with `.efkproj` files, configure `effekseer_tool_path` in `config.properties` or choose the Effekseer executable from the effect designer so SceneMax can launch the external Effekseer tool and export runtime `.efkefc` files.

Basic effect usage:

```scenemax
fire_burst => effects.effekseer.fire_burst
fire_burst.play pos (0,0,0)
```

Effect objects support placement, show/hide/delete, attachment, look-at behavior, looping playback, and runtime attributes such as playback speed and Effekseer dynamic input channels. See [`docs/effects.md`](docs/effects.md) for complete examples.

## Project Structure

```
scenemax_desktop/            -- Main desktop application (Swing UI)
scenemax_designer/           -- 3D scene designer/editor module
scenemax_win_projector/      -- 3D runtime/playback engine
scenemax_effekseer_runtime/  -- Effekseer JNI/native runtime bridge
scenemax3d_compiler/         -- Script compilation engine
scenemax3d_parser/           -- ANTLR4 grammar & parser for SceneMax scripting language
scenemax3d_common_types/     -- Shared type definitions across modules
scenemax3d_plugins/          -- Plugin system with WebSocket support
scenemax3d_plugins_ide/      -- IDE for plugin development
tools/multiplayer-server/    -- Low-level Zig UDP multiplayer server source, binaries, and load-test tooling
assets/                      -- UI resources, images, code templates
third_party/Effekseer/       -- Local Effekseer source/sample corpus used by the native bridge workflow
```

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Desktop Language / Runtime | Java 11 |
| Graphics Engine | JMonkeyEngine 3 |
| Physics | Minie (Bullet) |
| Visual Effects | Effekseer native runtime integration |
| Video Decoding / Rendering | JavaCV with FFmpeg platform bindings |
| UI Framework | Swing + FlatLaf (dark theme) |
| Scripting | Custom DSL via ANTLR4 |
| Java Extensibility | JDK compiler API, runtime extension jars, `SceneMaxBaseAppState`, JME `AppState` lifecycle |
| Multiplayer | SceneMax UDP client/server protocol, entity snapshots, command replication, ultra-efficient low-level server written in [Zig](https://ziglang.org/) |
| AI / Tooling | Built-in MCP HTTP server, stdio MCP proxy jar, AI Console, Local Gemma bridge |
| Plugins | Java plugin API with lifecycle hooks, Swing views, actions, settings, asset providers, and WebSocket support |
| Build System | Gradle |
| Code Editor | RSyntaxTextArea |
| Packaging | Shadow JAR, native Zig launcher, jdeps/jlink runtime embedding, Inno Setup installer, Web Start bundle generation |

## Third-Party Libraries

- [JMonkeyEngine 3](https://github.com/jMonkeyEngine/jmonkeyengine) -- 3D engine
- [Minie](https://github.com/stephengold/Minie) -- physics library
- [JME-Vehicles](https://github.com/stephengold/jme-vehicles) -- vehicle physics
- [ANTLR4](https://www.antlr.org/) -- parser generator
- [Effekseer](https://effekseer.github.io/en/) -- native particle-effect authoring and runtime stack
- [JavaCV](https://github.com/bytedeco/javacv) / [FFmpeg](https://ffmpeg.org/) -- video probing, preview, decoding, and runtime frame rendering
- [FlatLaf](https://www.formdev.com/flatlaf/) -- modern Swing look-and-feel
- [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea) -- code editor component
- [Socket.IO](https://github.com/socketio/socket.io) -- real-time communication
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) -- embedded HTTP server
- [Zig](https://ziglang.org/) -- low-level native language used for the ultra-efficient UDP multiplayer server and native launcher build path

## Contributing

Contributions are welcome! Please open an issue to discuss your idea before submitting a pull request.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Recently Delivered

SceneMax3D has grown a lot beyond the original scene editor and scripting engine. Recently shipped capabilities include:

- **Custom Java Extensibility** -- project-local Java extensions, `Java.attach`, scope-aware access to SceneMax entities, runtime extension jars, IDE diagnostics, and packaged-game support
- **Multiplayer Support** -- entity-based networking, ultra-efficient low-level Zig UDP server tooling, command replication, late-join snapshots, designer code generation, packaged metadata, and client diagnostics
- **Weapons Designer and Runtime Equip System** -- `.smweapon` assets, attachment points, equip postures, preview models, runtime equip/unequip commands, and weapon colliders
- **Throw Motion Designer** -- reusable `.smmotion` assets for thrown objects, projectiles, homing motion, and boomerang-style returns
- **IK Designer** -- `.smik` assets, armature scanning, solver templates, preview simulation, animation blending tests, and runtime IK layer commands
- **Effekseer Visual Effects** -- native effect import, preview, packaging, and runtime playback with dynamic inputs
- **Video Rendering** -- FFmpeg-backed video import, metadata indexing, preview, and runtime playback on scene objects
- **Material, Lighting, Physics, Messaging, and UI Frame Improvements** -- broader built-in systems for building complete game scenes inside the IDE
- **Itch.io Integration** -- upload and maintain packaged games on Itch.io
- **Built-in AI Tooling** -- MCP server, MCP proxy, AI Console, and Local Gemma bridge for project-aware assistance

## Roadmap

The items below are planned future areas. They will not necessarily be implemented in the order listed:

- **Health Bar System** -- built-in support for health bar setup and management
- **Inventory System** -- item storage, pickup rules, and inventory UI workflows
- **Climbing System** -- easy definition of climbable objects and a climbing game state machine
- **Leaderboard System** -- support for game leaderboards
- **MiniMap System** -- customized minimap system
- **Gallery** -- a shared place for presenting SceneMax3D projects
- **Terrain Builder** -- tools for creating and editing terrain
- **Audio System** -- built-in audio workflow and tooling
- **Android Package & Deployment** -- streamlined Android build packaging and deployment
- **Move to Jolt Physics** -- transition from the current physics backend to the Jolt physics engine
- **Debugger** -- easier runtime breakpoints and debugging information
- **Web Browser Projector** -- run exported projects directly in the browser
- **Scene Sharing**

