# Shared Effekseer renderer

`scenemax_effects` owns native Effekseer rendering and its C ABI. Both the IDE and projector use it; it depends on neither application. Runtime scheduling and script semantics remain in Engine/runtime. IDE/app is allowed to depend on this graphics component only, not the projector/runtime application. Render layers isolate preview cameras from game and model designer views.

The IDE enables the `effekseer_native` feature and Vulkan backend. The build script reuses an up-to-date native installation under `tmp/eb/<profile>/i`, otherwise builds it with CMake and Visual Studio. Official checkouts are `third_party/Effekseer` and `third_party/Vulkan-Headers`. Set `SCENEMAX_EFFEKSEER_NATIVE_PREBUILT_DIR` to a directory containing the matching DLL and import library for a prebuilt distribution. Package the DLL beside the executable. `SCENEMAX_EFFEKSEER_NATIVE_BUILD=1` forces rebuilding.

The base runtime instance ABI is preserved. PreviewOptions is a separate additive API for deterministic seeds, seeking, target location, color and triggers. The preview uses the same renderer as the projector, including its current limitations: no native sound output, no game depth/background distortion integration in the offscreen composite, and an additive final composite. This is a runtime rendering limitation, not a reimplementation of effect simulation. Test alpha-blended effects against the actual game; white backgrounds expose the additive composite limitation.

Simulation time now uses delta time, including a zero-delta paused preview, rather than counting rendered frames. Rendering and FFI ownership remain on the render thread.
