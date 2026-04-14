package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.gemma.install.VcRuntimeInstaller;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class VcRuntimeInstallerTest {

    @Test
    public void installerPathUsesAiRuntimeFolder() throws Exception {
        Path dir = Files.createTempDirectory("vc-runtime-installer");
        try {
            VcRuntimeInstaller installer = new VcRuntimeInstaller(dir);
            assertEquals(dir.resolve("AI/runtime/vcredist/vc_redist.x64.exe").toAbsolutePath().normalize(),
                    installer.getInstallerPath());
            assertFalse(installer.isDownloaded());
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
