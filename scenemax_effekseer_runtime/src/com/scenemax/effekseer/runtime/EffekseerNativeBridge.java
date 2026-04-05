package com.scenemax.effekseer.runtime;

import com.jme3.math.Matrix4f;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EffekseerNativeBridge {

    private static final String PROPERTY_NATIVE_LIB = "scenemax.effekseer.nativeLib";
    private static final String LIBRARY_BASENAME = "scenemax_effekseer_jni";
    private static final Object LOAD_LOCK = new Object();

    private static volatile boolean loadAttempted;
    private static volatile boolean libraryLoaded;
    private static volatile String loadMessage = "Native bridge has not been loaded yet.";
    private static volatile File extractedLibraryFile;

    private EffekseerNativeBridge() {
    }

    public static boolean isAvailable() {
        ensureLoaded();
        return libraryLoaded;
    }

    public static String getLoadMessage() {
        ensureLoaded();
        return loadMessage;
    }

    public static void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        synchronized (LOAD_LOCK) {
            if (loadAttempted) {
                return;
            }
            loadAttempted = true;
            try {
                File configured = resolveConfiguredLibrary();
                if (configured != null) {
                    System.load(configured.getAbsolutePath());
                    libraryLoaded = true;
                    loadMessage = "Loaded native bridge from " + configured.getAbsolutePath();
                    return;
                }

                File classpathLibrary = extractBundledLibrary();
                if (classpathLibrary != null) {
                    System.load(classpathLibrary.getAbsolutePath());
                    extractedLibraryFile = classpathLibrary;
                    libraryLoaded = true;
                    loadMessage = "Loaded native bridge from bundled resource " + classpathLibrary.getAbsolutePath();
                    return;
                }

                System.loadLibrary(LIBRARY_BASENAME);
                libraryLoaded = true;
                loadMessage = "Loaded native bridge via java.library.path.";
            } catch (Throwable ex) {
                libraryLoaded = false;
                loadMessage = "Native bridge unavailable: " + ex.getMessage();
            }
        }
    }

    public static List<String> describeSearchPaths() {
        List<String> lines = new ArrayList<>();
        String configured = System.getProperty(PROPERTY_NATIVE_LIB, "").trim();
        if (!configured.isEmpty()) {
            lines.add("Configured native library path: " + configured);
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        File repoDefault = new File("scenemax_effekseer_runtime/assets/native/" + platformFolder(os, arch) + "/" + mappedLibraryName(os));
        lines.add("Default packaged native path: " + repoDefault.getPath());
        lines.add("Bundled classpath native resource: " + bundledResourcePath(os, arch));
        if (extractedLibraryFile != null) {
            lines.add("Extracted bundled native library: " + extractedLibraryFile.getAbsolutePath());
        }
        lines.add("java.library.path fallback is also supported.");
        return lines;
    }

    public static long createPreviewContext(int squareMaxCount) {
        ensureLoaded();
        return libraryLoaded ? nativeCreatePreviewContext(squareMaxCount) : 0L;
    }

    public static void destroyPreviewContext(long contextHandle) {
        if (libraryLoaded && contextHandle != 0L) {
            nativeDestroyPreviewContext(contextHandle);
        }
    }

    public static boolean loadEffect(long contextHandle, String effectPath, String assetRootPath) {
        ensureLoaded();
        return libraryLoaded && contextHandle != 0L && nativeLoadEffect(contextHandle, effectPath, assetRootPath);
    }

    public static void unloadEffect(long contextHandle) {
        if (libraryLoaded && contextHandle != 0L) {
            nativeUnloadEffect(contextHandle);
        }
    }

    public static void setLooping(long contextHandle, boolean loop) {
        if (libraryLoaded && contextHandle != 0L) {
            nativeSetLooping(contextHandle, loop);
        }
    }

    public static void setPlaybackSpeed(long contextHandle, float speed) {
        if (libraryLoaded && contextHandle != 0L) {
            nativeSetPlaybackSpeed(contextHandle, speed);
        }
    }

    public static void setTargetLocation(long contextHandle, float x, float y, float z) {
        if (libraryLoaded && contextHandle != 0L) {
            nativeSetTargetLocation(contextHandle, x, y, z);
        }
    }

    public static void setCompositeEnabled(long contextHandle, boolean enabled) {
        if (libraryLoaded && contextHandle != 0L) {
            nativeSetCompositeEnabled(contextHandle, enabled);
        }
    }

    public static void setCamera(long contextHandle, Matrix4f view, Matrix4f projection) {
        if (libraryLoaded && contextHandle != 0L && view != null && projection != null) {
            nativeSetCamera(contextHandle, toArray(view), toArray(projection), null);
        }
    }

    public static void setCamera(long contextHandle, Matrix4f view, Matrix4f projection, float[] cameraPosition) {
        if (libraryLoaded && contextHandle != 0L && view != null && projection != null) {
            nativeSetCamera(contextHandle, toArray(view), toArray(projection), cameraPosition);
        }
    }

    public static void update(long contextHandle, float deltaSeconds) {
        if (libraryLoaded && contextHandle != 0L) {
            nativeUpdate(contextHandle, deltaSeconds);
        }
    }

    public static void render(long contextHandle, int viewportWidth, int viewportHeight) {
        if (libraryLoaded && contextHandle != 0L) {
            nativeRender(contextHandle, viewportWidth, viewportHeight);
        }
    }

    public static String getStatus(long contextHandle) {
        if (libraryLoaded && contextHandle != 0L) {
            return nativeGetStatus(contextHandle);
        }
        return "native bridge unavailable";
    }

    public static boolean readbackFrame(long contextHandle, ByteBuffer targetBuffer, int width, int height) {
        return libraryLoaded && contextHandle != 0L && targetBuffer != null
                && nativeReadbackFrame(contextHandle, targetBuffer, width, height);
    }

    private static float[] toArray(Matrix4f matrix) {
        return new float[]{
                matrix.m00, matrix.m01, matrix.m02, matrix.m03,
                matrix.m10, matrix.m11, matrix.m12, matrix.m13,
                matrix.m20, matrix.m21, matrix.m22, matrix.m23,
                matrix.m30, matrix.m31, matrix.m32, matrix.m33
        };
    }

    private static File resolveConfiguredLibrary() {
        String configured = System.getProperty(PROPERTY_NATIVE_LIB, "").trim();
        if (!configured.isEmpty()) {
            File configuredFile = new File(configured);
            if (configuredFile.isFile()) {
                return configuredFile;
            }
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        File repoDefault = new File("scenemax_effekseer_runtime/assets/native/" + platformFolder(os, arch) + "/" + mappedLibraryName(os));
        return repoDefault.isFile() ? repoDefault : null;
    }

    private static File extractBundledLibrary() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String resourcePath = bundledResourcePath(os, arch);
        try (InputStream in = EffekseerNativeBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            String suffix = mappedLibraryName(os);
            int dot = suffix.lastIndexOf('.');
            String extension = dot >= 0 ? suffix.substring(dot) : "";
            Path tempDir = Files.createTempDirectory("scenemax-effekseer-native-");
            tempDir.toFile().deleteOnExit();
            Path extracted = Files.createTempFile(tempDir, LIBRARY_BASENAME + "-", extension);
            Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            return extracted.toFile();
        }
    }

    private static String bundledResourcePath(String os, String arch) {
        return "/native/" + platformFolder(os, arch) + "/" + mappedLibraryName(os);
    }

    private static String platformFolder(String os, String arch) {
        String normalizedArch = normalizeArch(arch);
        if (os.contains("win")) {
            return "windows-" + normalizedArch;
        }
        if (os.contains("mac")) {
            return "macos-" + normalizedArch;
        }
        return "linux-" + normalizedArch;
    }

    private static String normalizeArch(String arch) {
        String normalized = arch == null ? "" : arch.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "amd64":
            case "x86_64":
                return "x86_64";
            case "arm64":
            case "aarch64":
                return "aarch64";
            default:
                return normalized;
        }
    }

    private static String mappedLibraryName(String os) {
        if (os.contains("win")) {
            return LIBRARY_BASENAME + ".dll";
        }
        if (os.contains("mac")) {
            return "lib" + LIBRARY_BASENAME + ".dylib";
        }
        return "lib" + LIBRARY_BASENAME + ".so";
    }

    private static native long nativeCreatePreviewContext(int squareMaxCount);
    private static native void nativeDestroyPreviewContext(long contextHandle);
    private static native boolean nativeLoadEffect(long contextHandle, String effectPath, String assetRootPath);
    private static native void nativeUnloadEffect(long contextHandle);
    private static native void nativeSetLooping(long contextHandle, boolean loop);
    private static native void nativeSetPlaybackSpeed(long contextHandle, float speed);
    private static native void nativeSetTargetLocation(long contextHandle, float x, float y, float z);
    private static native void nativeSetCompositeEnabled(long contextHandle, boolean enabled);
    private static native void nativeSetCamera(long contextHandle, float[] viewMatrix, float[] projectionMatrix, float[] cameraPosition);
    private static native void nativeUpdate(long contextHandle, float deltaSeconds);
    private static native void nativeRender(long contextHandle, int viewportWidth, int viewportHeight);
    private static native boolean nativeReadbackFrame(long contextHandle, ByteBuffer targetBuffer, int width, int height);
    private static native String nativeGetStatus(long contextHandle);
}
