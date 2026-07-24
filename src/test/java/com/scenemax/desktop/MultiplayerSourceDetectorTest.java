package com.scenemax.desktop;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiplayerSourceDetectorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void detectsNetworkRuntimeUsageBeforeMultiplayerEntityCreation() {
        assertTrue(MultiplayerSourceDetector.usesMultiplayer(
                "sys.print \"connecting...\"\n"
                        + "wait for network.ready\n"
                        + "sessions = network.state.sessions\n"));
        assertTrue(MultiplayerSourceDetector.usesMultiplayer("network.join session selected_session"));
    }

    @Test
    public void keepsExistingMultiplayerEntityDetection() {
        assertTrue(MultiplayerSourceDetector.usesMultiplayer(
                "player => fighter1_native: multiplayer, pos (0,0,0)"));
    }

    @Test
    public void ignoresPlainNonNetworkScripts() {
        assertFalse(MultiplayerSourceDetector.usesMultiplayer("sys.print \"hello\""));
    }

    @Test
    public void detectsNetworkUsageInReachableSwitchedScene() throws Exception {
        File scriptRoot = temporaryFolder.newFolder("scripts");
        Files.write(new File(scriptRoot, "main").toPath(),
                "switch to \"welcome\"".getBytes(StandardCharsets.UTF_8));
        File welcomeFolder = new File(scriptRoot, "welcome");
        assertTrue(welcomeFolder.mkdirs());
        Files.write(new File(welcomeFolder, "main").toPath(),
                "wait for network.ready".getBytes(StandardCharsets.UTF_8));

        assertTrue(MultiplayerSourceDetector.usesMultiplayerInReachableScripts(
                scriptRoot,
                "switch to \"welcome\""));
    }
}
