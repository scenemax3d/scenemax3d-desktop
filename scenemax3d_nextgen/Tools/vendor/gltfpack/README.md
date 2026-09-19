# Native model preparation

Pinned gltfpack v1.1 from the official meshoptimizer release:
https://github.com/zeux/meshoptimizer/releases/tag/v1.1

Uses meshoptimizer. Copyright (c) 2016–2026 Arseny Kapoulkine.
See LICENSE.md for the MIT license.

The IDE uses the native executable for OBJ conversion and optional glTF optimization. It preserves named nodes, materials, extras and animation tracks, and emits unquantized glTF without requiring GPU mesh compression extensions. `SCENEMAX_GLTFPACK` can select a compatible locally installed binary.
