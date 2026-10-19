# SceneMax3D NextGen Projector

Bevy/Rust runtime projector for SceneMax3D NextGen projects.

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

## Local Toolchain

Rust is installed at:

```powershell
C:\Users\adikt\.cargo\bin
```

MSVC build tools are available at:

```powershell
C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat
```

## Commands

From the `scenemax3d_nextgen` workspace root:

```powershell
$env:PATH = 'C:\Users\adikt\.cargo\bin;' + $env:PATH
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

First-stage asset policy:

- GLB/GLTF models are supported.
- JME `.j3o` models are reported as unsupported and must be exported or converted to GLB/GLTF.
- SceneMax source is parsed in Rust at runtime. There is no JSON intermediate file in the NextGen startup path.
