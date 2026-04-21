package com.scenemax.desktop;

import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

public class DesignerDocumentResourceCollectorTest {

    @Test
    public void collectsModelReferencesFromDesignerDocuments() throws Exception {
        Path tempDir = Files.createTempDirectory("designer-resource-collector");
        Path projectRoot = tempDir.resolve("Fighting_Game");
        Files.createDirectories(projectRoot.resolve("resources"));
        Files.writeString(
                projectRoot.resolve("intro.smdesign"),
                "{\n" +
                        "  \"entities\": [\n" +
                        "    {\n" +
                        "      \"type\": \"CODE\",\n" +
                        "      \"codeText\": \"tg => tiger3: hidden async\"\n" +
                        "    },\n" +
                        "    {\n" +
                        "      \"type\": \"SECTION\",\n" +
                        "      \"children\": [\n" +
                        "        {\n" +
                        "          \"type\": \"MODEL\",\n" +
                        "          \"resourcePath\": \"mountain_low_poly\"\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}\n"
        );

        SceneMaxLanguageParser.modelsUsed = new ArrayList<>();
        SceneMaxLanguageParser.effekseerUsed = new ArrayList<>();
        SceneMaxLanguageParser.spriteSheetUsed = new ArrayList<>();
        SceneMaxLanguageParser.audioUsed = new ArrayList<>();
        SceneMaxLanguageParser.fontsUsed = new ArrayList<>();
        SceneMaxLanguageParser.parseUsingResource = true;

        DesignerDocumentResourceCollector.collectResources(projectRoot.toFile(), null);

        assertTrue(SceneMaxLanguageParser.modelsUsed.contains("tiger3"));
        assertTrue(SceneMaxLanguageParser.modelsUsed.contains("mountain_low_poly"));

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
