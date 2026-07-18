package com.scenemax.desktop;

import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScriptTreeResourceCollectorTest {

    @Test
    public void mainParseClearsSkyboxAndTerrainResourceTrackers() {
        SceneMaxLanguageParser.skyboxUsed = new ArrayList<>();
        SceneMaxLanguageParser.terrainsUsed = new ArrayList<>();
        SceneMaxLanguageParser.skyboxUsed.add("stale_skybox");
        SceneMaxLanguageParser.terrainsUsed.add("stale_terrain");

        new SceneMaxLanguageParser(null, "").parse("skybox.show solar system");

        assertTrue(SceneMaxLanguageParser.skyboxUsed.isEmpty());
        assertTrue(SceneMaxLanguageParser.terrainsUsed.isEmpty());
    }

    @Test
    public void collectsResourcesFromReachableScenesOnly() throws Exception {
        Path tempDir = Files.createTempDirectory("script-tree-resource-collector");
        Path scriptRoot = tempDir.resolve("running");
        Files.createDirectories(scriptRoot.resolve("game_level1/game_input"));
        Files.createDirectories(scriptRoot.resolve("game_intro"));
        Files.createDirectories(scriptRoot.resolve("side_tests"));

        Files.writeString(scriptRoot.resolve("main"), "switch to \"game_intro\"");
        Files.writeString(scriptRoot.resolve("game_intro/main"),
                "intro.draw press_space_to_start\nswitch to \"game_level1\"");
        Files.writeString(scriptRoot.resolve("game_level1/main"),
                "add \"/game_input/input\", \"game_init.code\" code\nUI.load \"test_ui\"");
        Files.writeString(scriptRoot.resolve("game_level1/game_input/input"), "laser_effect => effects.effekseer.Homing_Laser01_3");
        Files.writeString(scriptRoot.resolve("game_level1/game_init.code"), "intro.draw intro_page");
        Files.writeString(scriptRoot.resolve("game_level1/game_init.smdesign"),
                "{ \"entities\": [{ \"type\":\"MODEL\", \"resourcePath\":\"reachable_model\" }] }");
        Files.writeString(scriptRoot.resolve("game_level1/test_ui.smui"),
                "{ \"layers\": [] }");
        Files.writeString(scriptRoot.resolve("side_tests/test_scene"), "unused.draw unused_sprite");
        Files.write(scriptRoot.resolve("ignore.dll"), new byte[]{0, 1, 2});

        SceneMaxLanguageParser.modelsUsed = new ArrayList<>();
        SceneMaxLanguageParser.effekseerUsed = new ArrayList<>();
        SceneMaxLanguageParser.spriteSheetUsed = new ArrayList<>();
        SceneMaxLanguageParser.audioUsed = new ArrayList<>();
        SceneMaxLanguageParser.fontsUsed = new ArrayList<>();
        SceneMaxLanguageParser.parseUsingResource = true;

        ScriptTreeResourceCollector.CollectionResult result =
                ScriptTreeResourceCollector.collectReachableResources(scriptRoot.toFile(), null);
        DesignerDocumentResourceCollector.collectResources(result.designerFiles, null);

        assertTrue(SceneMaxLanguageParser.spriteSheetUsed.contains("intro_page"));
        assertTrue(SceneMaxLanguageParser.spriteSheetUsed.contains("press_space_to_start"));
        assertTrue(SceneMaxLanguageParser.effekseerUsed.contains("effects.effekseer.Homing_Laser01_3"));
        assertFalse(SceneMaxLanguageParser.spriteSheetUsed.contains("unused_sprite"));
        List<String> scripts = result.scriptFiles;
        assertTrue(containsPathEndingWith(scripts, "main"));
        assertTrue(containsPathEndingWith(scripts, "game_intro" + File.separator + "main"));
        assertTrue(containsPathEndingWith(scripts, "game_level1" + File.separator + "main"));
        assertTrue(containsPathEndingWith(scripts, "game_level1" + File.separator + "game_init.code"));
        assertTrue(containsPathEndingWith(scripts, "game_level1" + File.separator + "game_input" + File.separator + "input"));
        assertFalse(containsPathEndingWith(scripts, "side_tests" + File.separator + "test_scene"));
        assertTrue(containsPathEndingWith(result.designerFiles, "game_level1" + File.separator + "game_init.smdesign"));
        assertTrue(containsPathEndingWith(result.uiFiles, "game_level1" + File.separator + "test_ui.smui"));

        deleteDirectory(tempDir.toFile());
    }

    private boolean containsPathEndingWith(List<String> paths, String suffix) {
        for (String path : paths) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
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
