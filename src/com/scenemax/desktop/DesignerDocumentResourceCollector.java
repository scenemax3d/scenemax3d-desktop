package com.scenemax.desktop;

import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class DesignerDocumentResourceCollector {

    private DesignerDocumentResourceCollector() {
    }

    static List<String> collectResources(File projectRoot, MacroFilter macroFilter) {
        List<String> scannedFiles = new ArrayList<>();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return scannedFiles;
        }

        Collection<File> designerFiles = FileUtils.listFiles(projectRoot, new String[]{"smdesign"}, true);
        for (File designerFile : designerFiles) {
            scannedFiles.add(designerFile.getAbsolutePath());
            try {
                String raw = FileUtils.readFileToString(designerFile, StandardCharsets.UTF_8);
                if (raw == null || raw.isBlank()) {
                    continue;
                }

                JSONObject root = new JSONObject(raw);
                collectEntityResources(designerFile, root.optJSONArray("entities"), macroFilter);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (Exception ignored) {
            }
        }

        return scannedFiles;
    }

    private static void collectEntityResources(File designerFile, JSONArray entities, MacroFilter macroFilter) {
        if (entities == null) {
            return;
        }

        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                continue;
            }

            if ("MODEL".equals(entity.optString("type", ""))) {
                addModelUsage(entity.optString("resourcePath", ""));
            }

            parseEmbeddedCode(designerFile, entity.optString("codeText", ""), macroFilter);
            parseEmbeddedCode(designerFile, entity.optString("sceneMaxCode", ""), macroFilter);
            collectEntityResources(designerFile, entity.optJSONArray("children"), macroFilter);
        }
    }

    private static void addModelUsage(String resourcePath) {
        if (resourcePath == null) {
            return;
        }

        String normalized = resourcePath.trim();
        if (normalized.isEmpty()) {
            return;
        }

        if (!SceneMaxLanguageParser.modelsUsed.contains(normalized)) {
            SceneMaxLanguageParser.modelsUsed.add(normalized);
        }
    }

    private static void parseEmbeddedCode(File designerFile, String code, MacroFilter macroFilter) {
        if (code == null || code.isBlank()) {
            return;
        }

        SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, designerFile.getAbsolutePath());
        parser.enableChildParserMode(true);
        if (macroFilter != null) {
            SceneMaxLanguageParser.setMacroFilter(macroFilter);
        }
        parser.parse(code);
    }
}
