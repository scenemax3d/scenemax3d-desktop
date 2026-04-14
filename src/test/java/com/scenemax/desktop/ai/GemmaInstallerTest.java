package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.gemma.install.GemmaInstallManifest;
import com.scenemax.desktop.ai.gemma.install.GemmaModelVariant;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GemmaInstallerTest {

    @Test
    public void manifestRoundTripWorks() throws Exception {
        Path dir = Files.createTempDirectory("gemma-install-manifest");
        try {
            Path modelPath = dir.resolve("AI/models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm");
            Path runtimePath = dir.resolve("AI/runtime/litert-lm/litert_lm_main.windows_x86_64.exe");
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
}
