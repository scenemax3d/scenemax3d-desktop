# SceneMax3D NextGen Projector

Bevy/Rust runtime projector for SceneMax3D NextGen projects.

## Send commands to a running game

Start the desktop projector with `--live-stdin`. A controller can then write one UTF-8 JSON object per line to the projector's standard input and flush after each line. Each object contains a SceneMax source string. Use JSON newline escapes for multiline snippets.

```powershell
.\scenemax_projector_nextgen.exe run --project-root C:\Games\Demo --script C:\Games\Demo\scripts\main --live-stdin
```

Enter these two lines in the same session, or send them through the process's input pipe.

```json
{"code":"marker => box: pos (0,0,0)"}
{"code":"wait 1 second\nmarker.move(x+3) in 1 second"}
```

The projector replies on standard output with one JSON line per request. `{"accepted":true}` means the snippet was parsed and scheduled in the current scene. Waits, motion, and event handlers continue on subsequent updates. A rejected snippet returns `{"accepted":false,"error":"..."}`. Set `"close_on_error":true` to end the game on a SceneMax source error. Closing the input stream lets the game continue.

Snippets share the scene's variables, entities, functions, and asset roots. A main-scope function defined in one request can be called by later requests. Rust hosts can create a `partial_code_channel()`, pass its input to `run_bevy_projector_with_input`, and call `sender.run_partial_code(code, scope, close_on_error)` from a worker thread. Pass `None` for the main scope or pass an owned `SceneMaxScopeFrame` for scoped execution.

Rebuilt single-file desktop games also accept `--live-stdin` on the game executable.

## External multiplayer configuration

Desktop games built by the Rust IDE support an optional UTF-8 `network.json`
beside the game executable. It is read on each launch, including for single-file
packages that extract their runtime into a temporary folder. It is not read from
the working directory or the extraction folder. When running the standalone
projector directly, place it beside the projector executable.

```json
{
  "host": "127.0.0.1",
  "port": 9001
}
```

`host` and `port` are required when the file exists. Use a hostname or IP address
(bare IPv6 is supported), without a URL scheme or appended port. `port` must be a
JSON integer from 1 to 65535. The following fields are optional:

| Field | Purpose |
| --- | --- |
| `project_guid` | Override the game identity when the private Zig server expects a different GUID. Normally keep the packaged value. |
| `session_id` | Override the initial room ID (unsigned 32-bit integer; zero requests allocation according to the server protocol). |
| `session_name` | Override the initial room name. |

GUID and room name must contain 1–63 UTF-8 bytes without line breaks or NUL.
Explicit room selections made by the game's scripts still apply after login.
Server platform is not a connection setting: it only chooses a server binary
when building the dedicated server. It is not a supported field in this file.

Values supplied in `network.json` take precedence over packaged settings and
`SCENEMAX_MULTIPLAYER_*` environment overrides. Omitted optional fields retain
their normal defaults. Without the file, existing configuration behavior is
unchanged. Other settings, including password handling, remain unchanged.
Unknown fields, invalid values, unreadable files, and files larger than 16 KiB
disable the multiplayer connection instead of silently connecting to the default
server. Launch with `--runtime-log` to see `NETWORK:CONFIG_ERROR` diagnostics in
the game's runtime log.

Rebuild existing game distributions to include this support. No IDE setting or
game-script change is needed; replacing or removing the sidecar and restarting
the game changes the selected server.

## Build prerequisites

Follow the [source-build requirements](../../README.md#requirements) for Rust, native build tools, and graphics support. Installed Studio distributions already provide Windows and Web export runtimes.

## Commands

From the `scenemax3d_nextgen` workspace root:

```powershell
cargo check --workspace --all-targets
cargo test -p scenemax_parser -p scenemax_assets -p scenemax_runtime --lib
cargo run -p scenemax_projector_nextgen -- --help
```

Run the first tiny SceneMax program through the NextGen parser/runtime:

```powershell
cargo run -p scenemax_projector_nextgen -- run --project-root C:\path\to\SceneMaxProject --script .\Tests\fixtures\projector\scripts\dragon_fly.code
```

The sample script is:

```scenemax
d=>dragon
d.fly loop
```

For that script to spawn a model, the selected project's `resources\Models\models-ext.json` must contain a `dragon` entry whose path points to a `.glb` or `.gltf` file.

Runtime asset formats:

- GLB/GLTF models are supported.
- JME `.j3o` models are reported as unsupported and must be exported or converted to GLB/GLTF.
- SceneMax source is parsed in Rust at runtime. There is no JSON intermediate file in the NextGen startup path.
