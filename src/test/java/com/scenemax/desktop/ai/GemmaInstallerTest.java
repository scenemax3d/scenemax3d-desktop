package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.gemma.install.GemmaInstallManifest;
import com.scenemax.desktop.ai.gemma.install.GemmaInstaller;
import com.scenemax.desktop.ai.gemma.install.GemmaModelVariant;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GemmaInstallerTest {

    @Test
    public void manifestRoundTripWorks() throws Exception {
        Path dir = Files.createTempDirectory("gemma-install-manifest");
        try {
            Path modelPath = dir.resolve("AI/models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm");
            Path runtimePath = dir.resolve("AI/runtime/litert-lm/" + GemmaInstaller.RUNTIME_FILE_NAME);
            GemmaInstallManifest manifest = GemmaInstallManifest.now(
                    GemmaModelVariant.E2B,
                    modelPath,
                    runtimePath,
                    "https://example.com/runtime.exe"
            );
            Path manifestPath = dir.resolve("AI/models/installed.json");
            manifest.save(manifestPath);

            GemmaInstallManifest loaded = GemmaInstallManifest.load(manifestPath);
            assertNotNull(loaded);
            assertEquals(GemmaModelVariant.E2B.getModelId(), loaded.getModelId());
            assertEquals(modelPath.toAbsolutePath().normalize().toString(), loaded.getModelPath());
            assertEquals(runtimePath.toAbsolutePath().normalize().toString(), loaded.getRuntimePath());
        } finally {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    public void ensureSupportedRuntimeInstalledRepairsLegacyRuntimePath() throws Exception {
        Path dir = Files.createTempDirectory("gemma-install-upgrade");
        try {
            Path modelPath = dir.resolve("AI/models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm");
            Path legacyRuntimePath = dir.resolve("AI/runtime/litert-lm/" + GemmaInstaller.LEGACY_RUNTIME_FILE_NAME);
            Files.createDirectories(modelPath.getParent());
            Files.createDirectories(legacyRuntimePath.getParent());
            Files.writeString(modelPath, "fake-model");
            Files.writeString(legacyRuntimePath, "legacy-runtime");

            GemmaInstallManifest manifest = GemmaInstallManifest.now(
                    GemmaModelVariant.E2B,
                    modelPath,
                    legacyRuntimePath,
                    "https://example.com/legacy.exe"
            );
            manifest.save(dir.resolve("AI/models/installed.json"));

            GemmaInstaller installer = new GemmaInstaller(dir) {
                @Override
                public GemmaInstallManifest ensureSupportedRuntimeInstalled(GemmaInstallManifest manifest, com.scenemax.desktop.ai.gemma.install.GemmaInstallListener listener) throws java.io.IOException {
                    Path runtimePath = getRuntimePath();
                    Files.createDirectories(runtimePath.getParent());
                    Files.writeString(runtimePath, "new-runtime");
                    GemmaInstallManifest updated = manifest.withRuntime(runtimePath, GemmaInstaller.LIT_CLI_RUNTIME_URL);
                    updated.save(getManifestPath());
                    return updated;
                }
            };

            GemmaInstallManifest updated = installer.ensureSupportedRuntimeInstalled(manifest, null);
            assertTrue(updated.getRuntimePath().endsWith(GemmaInstaller.RUNTIME_FILE_NAME));
            assertEquals(dir.resolve("AI/runtime/litert-lm/" + GemmaInstaller.RUNTIME_FILE_NAME).toAbsolutePath().normalize().toString(),
                    updated.getRuntimePath());
        } finally {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }
}
