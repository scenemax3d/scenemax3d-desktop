package com.scenemax.desktop;

import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScriptTreeResourceCollector {

    private static final Pattern ADD_CODE_PATTERN = Pattern.compile(
            "(?is)\\badd\\b\\s+(.*?)(?<!\\.)\\bcode\\b"
    );
    private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern SWITCH_TO_PATTERN = Pattern.compile(
            "(?im)(?<![.A-Za-z0-9_])\\bswitch\\s+to\\s+(?:\"([^\"]+)\"|([A-Za-z_][A-Za-z0-9_\\-/]*))"
    );
    private static final Pattern UI_LOAD_PATTERN = Pattern.compile(
            "(?im)\\bUI\\s*\\.\\s*load\\s+\"([^\"]+)\""
    );

    private ScriptTreeResourceCollector() {
    }

    static List<String> collectResources(File scriptRoot, MacroFilter macroFilter) {
        return collectReachableResources(scriptRoot, macroFilter).scriptFiles;
    }

    static CollectionResult collectReachableResources(File scriptRoot, MacroFilter macroFilter) {
        CollectionResult result = new CollectionResult();
        if (scriptRoot == null || !scriptRoot.exists()) {
            return result;
        }

        Set<String> queuedScripts = new LinkedHashSet<>();
        File mainFile = new File(scriptRoot, "main");
        enqueueScript(mainFile, scriptRoot, result, queuedScripts);

        int index = 0;
        while (index < result.scriptFiles.size()) {
            File scriptFile = new File(result.scriptFiles.get(index++));
            String code = readFile(scriptFile);
            parseScriptText(scriptFile, code, macroFilter);
            collectSiblingDesignerFile(scriptFile, result);
            collectDependencies(scriptRoot, scriptFile, code, result, queuedScripts);
        }

        return result;
    }

    static boolean isSceneMaxScriptFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        String name = file.getName();
        if (name.endsWith(".code")) {
            return true;
        }

        return !name.contains(".");
    }

    private static void collectDependencies(File scriptRoot, File scriptFile, String code,
                                            CollectionResult result, Set<String> queuedScripts) {
        String dependencyText = stripLineComments(code);
        File baseDir = scriptFile.getParentFile();

        Matcher addMatcher = ADD_CODE_PATTERN.matcher(dependencyText);
        while (addMatcher.find()) {
            Matcher fileMatcher = QUOTED_STRING_PATTERN.matcher(addMatcher.group(1));
            while (fileMatcher.find()) {
                File referenced = resolveScriptReference(baseDir, fileMatcher.group(1));
                enqueueScript(referenced, scriptRoot, result, queuedScripts);
            }
        }

        Matcher switchMatcher = SWITCH_TO_PATTERN.matcher(dependencyText);
        while (switchMatcher.find()) {
            String sceneName = switchMatcher.group(1) != null ? switchMatcher.group(1) : switchMatcher.group(2);
            enqueueSceneMain(scriptRoot, sceneName, result, queuedScripts);
        }

        Matcher uiMatcher = UI_LOAD_PATTERN.matcher(dependencyText);
        while (uiMatcher.find()) {
            File uiFile = new File(baseDir, uiMatcher.group(1) + ".smui");
            enqueueFile(uiFile, scriptRoot, result.uiFiles);
        }
    }

    private static File resolveScriptReference(File baseDir, String reference) {
        String normalized = reference == null ? "" : reference.replace("\\", "/").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return new File(baseDir, normalized);
    }

    private static void enqueueSceneMain(File scriptRoot, String sceneName, CollectionResult result, Set<String> queuedScripts) {
        if (sceneName == null || sceneName.isBlank()) {
            return;
        }
        String normalized = sceneName.replace("\\", "/").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        File sceneMain = "main".equalsIgnoreCase(normalized)
                ? new File(scriptRoot, "main")
                : new File(new File(scriptRoot, normalized), "main");
        enqueueScript(sceneMain, scriptRoot, result, queuedScripts);
    }

    private static void enqueueScript(File file, File scriptRoot, CollectionResult result, Set<String> queuedScripts) {
        if (!isSceneMaxScriptFile(file)) {
            return;
        }
        if (!isInsideRoot(file, scriptRoot)) {
            return;
        }
        try {
            String path = file.getCanonicalPath();
            if (queuedScripts.add(path)) {
                result.scriptFiles.add(path);
            }
        } catch (IOException ignored) {
        }
    }

    private static void collectSiblingDesignerFile(File scriptFile, CollectionResult result) {
        String name = scriptFile.getName();
        if (!name.endsWith(".code")) {
            return;
        }
        String stem = name.substring(0, name.length() - ".code".length());
        enqueueDesignerFile(new File(scriptFile.getParentFile(), stem + ".smdesign"), result);
    }

    private static void enqueueDesignerFile(File file, CollectionResult result) {
        enqueueFile(file, null, result.designerFiles);
    }

    private static void enqueueFile(File file, File root, List<String> target) {
        if (file == null || !file.isFile()) {
            return;
        }
        if (root != null && !isInsideRoot(file, root)) {
            return;
        }
        try {
            String path = file.getCanonicalPath();
            if (!target.contains(path)) {
                target.add(path);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isInsideRoot(File file, File root) {
        if (file == null || root == null) {
            return true;
        }
        try {
            String rootPath = root.getCanonicalFile().toPath().toString();
            String filePath = file.getCanonicalFile().toPath().toString();
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private static String readFile(File file) {
        try {
            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void parseScriptText(File file, String code, MacroFilter macroFilter) {
        try {
            if (code == null || code.isBlank()) {
                return;
            }
            SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, file.getAbsolutePath());
            parser.enableChildParserMode(true);
            if (macroFilter != null) {
                SceneMaxLanguageParser.setMacroFilter(macroFilter);
            }
            parser.parse(code);
        } catch (Exception ignored) {
        }
    }

    private static String stripLineComments(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        StringBuilder stripped = new StringBuilder(code.length());
        String[] lines = code.split("\\R", -1);
        for (String line : lines) {
            int comment = line.indexOf("//");
            stripped.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return stripped.toString();
    }

    static final class CollectionResult {
        final List<String> scriptFiles = new ArrayList<>();
        final List<String> designerFiles = new ArrayList<>();
        final List<String> uiFiles = new ArrayList<>();
    }
}
