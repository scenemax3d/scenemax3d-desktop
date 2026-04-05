# SceneMax3D Developer Studio

A desktop 3D scene editor and game development environment built with Java, [JMonkeyEngine 3](https://jmonkeyengine.org/), and Swing.

SceneMax3D lets you visually design 3D scenes, write game logic using a built-in scripting language, and export your projects as standalone PC executables or Android apps.

<img width="1600" height="846" alt="image" src="https://github.com/user-attachments/assets/8e83d0b0-950e-425d-94a0-d38db5fc4fd3" />

SceneMax3D was first created by Adi Barda in 2005 as a C++/DirectX game engine and scripting language for education purposes.
In 2017-2018 it was rewritten in Java from scratch using JMonkeyEngine3 as the target renderer and ANTLR4 for the language parsing. 
In Mar-22, 2026 the entire solution was uploaded to GitHub as an open source (MIT license) project.
## Product Website
[SceneMax3D](https://scenemax3d.com/cook-book/)

## Features

- **Visual 3D Scene Designer** -- drag-and-drop scene composition with real-time preview
- **Custom Scripting Language** -- purpose-built DSL (ANTLR4-based parser) for game logic and interactivity
- **Code Editor** -- syntax-highlighted editor with code folding 
- **Physics Engine** -- integrated Minie / Bullet physics
- **3D Model Import** -- load models into your scenes
- **Multi-Project Support** -- manage multiple projects from a single workspace
- **Plugin System** -- extend functionality via plugins (WebSocket-based communication)
- **Classroom Mode** -- collaborative features for educational settings
- **Export Targets** -- package as desktop builds or generate a Web Start bundle (JNLP + landing page)

## Requirements

- Java 11 or later
- Windows (primary platform)

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

## Project Structure

```
scenemax_desktop/            -- Main desktop application (Swing UI)
scenemax_designer/           -- 3D scene designer/editor module
scenemax_win_projector/      -- 3D runtime/playback engine
scenemax3d_compiler/         -- Script compilation engine
scenemax3d_parser/           -- ANTLR4 grammar & parser for SceneMax scripting language
scenemax3d_common_types/     -- Shared type definitions across modules
scenemax3d_plugins/          -- Plugin system with WebSocket support
scenemax3d_plugins_ide/      -- IDE for plugin development
assets/                      -- UI resources, images, code templates
```

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Graphics Engine | JMonkeyEngine 3 |
| Physics | Minie (Bullet) |
| UI Framework | Swing + FlatLaf (dark theme) |
| Scripting | Custom DSL via ANTLR4 |
| Build System | Gradle |
| Code Editor | RSyntaxTextArea |
| Packaging | Shadow JAR, Launch4J, Web Start bundle generation |

## Third-Party Libraries

- [JMonkeyEngine 3](https://github.com/jMonkeyEngine/jmonkeyengine) -- 3D engine
- [Minie](https://github.com/stephengold/Minie) -- physics library
- [JME-Vehicles](https://github.com/stephengold/jme-vehicles) -- vehicle physics
- [ANTLR4](https://www.antlr.org/) -- parser generator
- [FlatLaf](https://www.formdev.com/flatlaf/) -- modern Swing look-and-feel
- [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea) -- code editor component
- [Socket.IO](https://github.com/socketio/socket.io) -- real-time communication
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) -- embedded HTTP server

## Contributing

Contributions are welcome! Please open an issue to discuss your idea before submitting a pull request.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
