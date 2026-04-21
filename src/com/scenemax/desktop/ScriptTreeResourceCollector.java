package com.scenemax.desktop;

import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class ScriptTreeResourceCollector {

    private ScriptTreeResourceCollector() {
    }

    static List<String> collectResources(File scriptRoot, MacroFilter macroFilter) {
        List<String> scannedFiles = new ArrayList<>();
        if (scriptRoot == null || !scriptRoot.exists()) {
            return scannedFiles;
        }

        Iterator<File> files = FileUtils.iterateFiles(scriptRoot, null, true);
        while (files.hasNext()) {
            File file = files.next();
            if (!isSceneMaxScriptFile(file)) {
                continue;
            }

            scannedFiles.add(file.getAbsolutePath());
            parseScriptFile(file, macroFilter);
        }

        return scannedFiles;
    }

    private static boolean isSceneMaxScriptFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        String name = file.getName();
        if (name.endsWith(".code")) {
            return true;
        }

        return !name.contains(".");
    }

    private static void parseScriptFile(File file, MacroFilter macroFilter) {
        try {
            String code = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            if (code == null || code.isBlank()) {
                return;
            }

            SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, file.getAbsolutePath());
            parser.enableChildParserMode(true);
            if (macroFilter != null) {
                SceneMaxLanguageParser.setMacroFilter(macroFilter);
            }
            parser.parse(code);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception ignored) {
        }
    }
}
