# SceneMax Multiplayer Server

SceneMax expects one prebuilt multiplayer server executable per game platform in:

- `tools/multiplayer-server/bin/windows-x64/scenemax-mp-server.exe`
- `tools/multiplayer-server/bin/linux-x64/scenemax-mp-server`
- `tools/multiplayer-server/bin/macos-x64/scenemax-mp-server`

The Project Settings `Multiplayer` tab copies the selected executable and patches the embedded
`SCENEMAX_MP_CONFIG` block with the game name, project path, port, and password hash.

The server hosts multiple game sessions. Clients create or join a session, then join a scene
within that session. The scene id is the scene folder path (`main` for the root scene,
`game_level1` for a switched scene folder, and so on).

If a prebuilt executable is missing, SceneMax attempts to build `zig/scenemax_multiplayer_server.zig`
with `zig build-exe -O ReleaseFast`.
