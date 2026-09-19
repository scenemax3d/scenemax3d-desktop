# Asset imports

Assets now provides Import 3D Model, Import Animation, Import Sprite, Import Audio, Import Video, Import Effekseer Effect, and Open Assets Folder. Import Skybox is hidden; Create commands remain deferred.

Choose Browse (native file picker) or enter a source path, enter a resource name, adjust the category settings, and choose Import. File selection and import/conversion run off the UI thread. An error stays in the dialog so settings can be corrected. Existing resources are not overwritten.

Supported sources:
- Models: GLB, glTF 2.0, FBX, OBJ and ZIP packages containing these formats. Animations: GLB, glTF 2.0, FBX. External glTF images and buffers are copied with their directory structure. FBX uses the native FBX2glTF tool under Tools/vendor/fbx2gltf, or SCENEMAX_FBX2GLTF. Model scale and animation clip name are configurable.
- Sprites: PNG/JPEG in the dedicated sprite import designer, with live sheet/animation preview, grid slicing, margins/gutters, and frame display dimensions. See [SPRITE_IMPORT.md](SPRITE_IMPORT.md).
- Audio: WAV/OGG.
- Video: MP4, WebM, MOV, AVI, MKV, OGV. Playback codec support depends on the projector.
- Effects: a dedicated native Effekseer preview/import designer with dependency inspection, live transforms/inputs, frame stepping/seeking, deterministic replay, triggers, tint and snapshots. Compiled .efkefc/.efk files are staged with their referenced resources; legacy .efkproj files are converted automatically using the installed official Effekseer tool. See [EFFECT_IMPORT.md](EFFECT_IMPORT.md) for discovery and runtime-companion fallback details.

Imports preserve resource index extensions and unknown fields. Private staging and per-category locks prevent partial registrations and concurrent IDE imports from overwriting each other. Missing/escaping dependencies and duplicate names fail before publication. No Java process is used.

## 3D model import designer

Assets → Import 3D Model opens a retained Bevy document with an embedded PBR preview. Browse a file, or paste a path and choose Load / Reload. The source stays untouched; conversion, extraction and optimization run on background workers in a private temporary directory. Import publishes the staged artifact and calibrated resource metadata only after a successful preview. Close the document to release the preview and temporary assets.

| Java importer capability | Bevy implementation |
| --- | --- |
| Local model or ZIP selection | GLB/glTF 2.0, native FBX conversion, native OBJ conversion; ZIP model selector |
| Preview camera | Orbit, pan, wheel zoom, frame model and orientation widget |
| Transform controls | Move/rotate/scale gizmos, immediate XYZ fields and sliders, proportional scale |
| Preview pose | XYZ position and rotation, reset pose; separate from saved import offsets |
| Asset properties | Name, scale XYZ, import translation XYZ, Y rotation, static flag |
| Character calibration | XYZ calibration, capsule radius/height, step height; optional capsule overlay |
| Animation testing | Bundled clip selector, play/pause/stop, bind pose, loop, playback speed and percentage scrubbing |
| Texture optimization | Maximum size (0 keeps dimensions), JPEG quality, opaque color/metallic-roughness conversion; alpha retained as PNG |
| Mesh optimization | Optional triangle ratio for static, non-skinned models |
| Document editing | Undo/redo through existing commands, persistent draft, collapsible properties |

Optimization settings require Load / Reload before Import so the preview uses the exact optimized file that will be published. Geometry and image conversion use native tools, not Java. Unsupported files or missing dependencies report errors in the document. Model formats beyond the listed formats, Sketchfab, J3O conversion and animation retargeting are not included.

Model import code is separated between `IDE/core/src/model_import.rs` (draft validation/metadata), `IDE/services/src/imports/model.rs` and `optimization.rs` (staging/conversion), and `IDE/app/src/presentation/model_import/` (retained controls, rendering, playback). Preview-only data never enters runtime resource entries.

Native optimization uses [meshoptimizer/gltfpack v1.1](https://github.com/zeux/meshoptimizer/releases/tag/v1.1), bundled under `Tools/vendor/gltfpack`; `SCENEMAX_GLTFPACK` overrides the executable path. Uses meshoptimizer. Copyright (c) 2016–2026 Arseny Kapoulkine. The accompanying MIT license is retained. FBX2glTF licensing is retained separately beside its executable.

