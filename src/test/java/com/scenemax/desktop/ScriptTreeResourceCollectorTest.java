package com.scenemax.desktop;

import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

public class ScriptTreeResourceCollectorTest {

    @Test
    public void collectsResourcesFromNestedScriptTree() throws Exception {
        Path tempDir = Files.createTempDirectory("script-tree-resource-collector");
        Path scriptRoot = tempDir.resolve("running");
        Files.createDirectories(scriptRoot.resolve("game_level1/game_input"));
        Files.createDirectories(scriptRoot.resolve("game_intro"));

        Files.writeString(scriptRoot.resolve("game_level1/game_input/input"), "laser_effect => effects.effekseer.Homing_Laser01_3");
        Files.writeString(scriptRoot.resolve("game_level1/game_init.code"), "intro.draw intro_page");
        Files.writeString(scriptRoot.resolve("game_intro/main"), "intro.draw press_space_to_start");
        Files.write(scriptRoot.resolve("ignore.dll"), new byte[]{0, 1, 2});

        SceneMaxLanguageParser.modelsUsed = new ArrayList<>();
        SceneMaxLanguageParser.effekseerUsed = new ArrayList<>();
        SceneMaxLanguageParser.spriteSheetUsed = new ArrayList<>();
        SceneMaxLanguageParser.audioUsed = new ArrayList<>();
        SceneMaxLanguageParser.fontsUsed = new ArrayList<>();
        SceneMaxLanguageParser.parseUsingResource = true;

        ScriptTreeResourceCollector.collectResources(scriptRoot.toFile(), null);

        assertTrue(SceneMaxLanguageParser.spriteSheetUsed.contains("intro_page"));
        assertTrue(SceneMaxLanguageParser.spriteSheetUsed.contains("press_space_to_start"));
        assertTrue(SceneMaxLanguageParser.effekseerUsed.contains("effects.effekseer.Homing_Laser01_3"));

        deleteDirectory(tempDir.toFile());
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        file.delete();
    }
}
