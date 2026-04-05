# SceneMax Effekseer JNI Bridge

This folder contains the native bridge that lets the SceneMax designer and runtime
call the official Effekseer OpenGL runtime from Java.

## Goal

- render Effekseer natively inside the same `SceneMaxApp` OpenGL canvas
- share the same runtime path between IDE preview and final game playback
- keep the Java API narrow and stable

## Current status

- Java-side loading and preview integration are scaffolded
- the native library is not built by default in this repo yet
- once built, place the output binary under:

`scenemax_effekseer_runtime/assets/native/windows-x86_64/scenemax_effekseer_jni.dll`

or point Java at it with:

`-Dscenemax.effekseer.nativeLib=C:\path\to\scenemax_effekseer_jni.dll`

## Planned runtime flow

1. `SceneMaxApp` owns the OpenGL context.
2. Java calls the JNI bridge from the render thread.
3. The native bridge creates an Effekseer OpenGL renderer against the current context.
4. Java passes camera/projection matrices each frame.
5. Native code calls Effekseer `Update`, `BeginRendering`, `Draw`, `EndRendering`.

## Android

The same bridge shape is intended to work on Android via the NDK, producing
`libscenemax_effekseer_jni.so` and using the same JNI entry points.
