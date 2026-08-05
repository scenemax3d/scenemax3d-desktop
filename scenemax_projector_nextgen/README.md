# SceneMax3D NextGen Projector

Bevy/Rust runtime projector for SceneMax3D NextGen projects.

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

From this folder:

```powershell
$env:PATH = 'C:\Users\adikt\.cargo\bin;' + $env:PATH
cargo check --workspace --all-targets
cargo test -p scenemax_parser -p scenemax_assets -p scenemax_runtime --lib
cargo run -p scenemax_projector_nextgen -- --help
```

Run the first tiny SceneMax program through the NextGen parser/runtime:

```powershell
cargo run -p scenemax_projector_nextgen -- run --project-root C:\path\to\SceneMaxProject --script .\fixtures\scripts\dragon_fly.code
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
