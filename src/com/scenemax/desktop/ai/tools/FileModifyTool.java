package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileModifyTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "file.modify";
    }

    @Override
    public String getDescription() {
        return "Modifies an existing file using an exact replacement or full overwrite.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("path", new JSONObject().put("type", "string"));
        properties.put("base", new JSONObject().put("type", "string"));
        properties.put("expectedText", new JSONObject().put("type", "string").put("description", "Exact text that must exist before replacement."));
        properties.put("replacementText", new JSONObject().put("type", "string").put("description", "Replacement text."));
        properties.put("replaceAll", new JSONObject().put("type", "boolean").put("description", "Replace all matching occurrences."));
        properties.put("newContent", new JSONObject().put("type", "string").put("description", "Full file content when overwrite=true."));
        properties.put("overwrite", new JSONObject().put("type", "boolean").put("description", "Allow replacing the full file content."));
        properties.put("openInEditor", new JSONObject().put("type", "boolean").put("description", "Open or refresh the file after changing it."));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("path"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = ToolPaths.resolvePath(context, requireString(arguments, "path"), optionalString(arguments, "base", "workspace"));
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }

        MainApp host = context.getHost();
        if (host != null && host.getEditorTabPanel() != null && host.getEditorTabPanel().isTabDirty(path.toString())) {
            throw new IllegalStateException("Refusing to modify a file with unsaved IDE changes: " + path);
        }

        String original = Files.readString(path, StandardCharsets.UTF_8);
        String updated = buildUpdatedContent(arguments, original);
        Files.writeString(path, updated, StandardCharsets.UTF_8);

        if (host != null) {
            host.refreshWorkspaceViews();
            if (optionalBoolean(arguments, "openInEditor", true)) {
                host.openOrRefreshTextFileFromAutomation(path.toFile(), updated);
            }
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("sizeBytes", Files.size(path));
        return SceneMaxToolResult.success("Updated file " + path.getFileName(), data);
    }

    private String buildUpdatedContent(JSONObject arguments, String original) {
        boolean overwrite = optionalBoolean(arguments, "overwrite", false);
        if (overwrite) {
            if (!arguments.has("newContent")) {
                throw new IllegalArgumentException("newContent is required when overwrite=true");
            }
            return arguments.optString("newContent", "");
        }

        String expected = requireString(arguments, "expectedText");
        String replacement = arguments.optString("replacementText", "");
        boolean replaceAll = optionalBoolean(arguments, "replaceAll", false);

        int occurrences = countOccurrences(original, expected);
        if (occurrences == 0) {
            throw new IllegalArgumentException("expectedText was not found in the file");
        }
        if (occurrences > 1 && !replaceAll) {
            throw new IllegalArgumentException("expectedText matched multiple locations; set replaceAll=true or use a more specific match");
        }

        if (replaceAll) {
            return original.replace(expected, replacement);
        }
        return original.replaceFirst(java.util.regex.Pattern.quote(expected), java.util.regex.Matcher.quoteReplacement(replacement));
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
