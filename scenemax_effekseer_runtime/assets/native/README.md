Effekseer native desktop libraries live under platform folders here and are packaged into the jar as classpath resources.

Expected layout:

- `native/windows-x86_64/scenemax_effekseer_jni.dll`
- `native/linux-x86_64/libscenemax_effekseer_jni.so`
- `native/macos-x86_64/libscenemax_effekseer_jni.dylib`
- `native/macos-aarch64/libscenemax_effekseer_jni.dylib`

At runtime, `EffekseerNativeBridge` will try:

1. `-Dscenemax.effekseer.nativeLib=...`
2. repo-local unpacked file path
3. bundled classpath resource extraction from the jar
4. `java.library.path`

Gradle verification tasks from the repo root:

- `:scenemax_effekseer_runtime:verifyEffekseerNativeResources`
- `:scenemax_effekseer_runtime:verifyEffekseerNativeResourcesForCurrentPlatform`
- `:scenemax_effekseer_runtime:verifyEffekseerNativeResourcesForRelease`

Expected packaged output names:

- Windows x64: `windows-x86_64/scenemax_effekseer_jni.dll`
- Linux x64: `linux-x86_64/libscenemax_effekseer_jni.so`
- macOS Intel: `macos-x86_64/libscenemax_effekseer_jni.dylib`
- macOS Apple Silicon: `macos-aarch64/libscenemax_effekseer_jni.dylib`

Linux and macOS binaries must be built on those platforms and then copied into the matching folders above.
