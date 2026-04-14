package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class ProjectListTreeTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "project.list_tree";
    }

    @Override
    public String getDescription() {
        return "Lists files and folders under the workspace or active project.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("root", new JSONObject().put("type", "string").put("description", "Optional path to list from."));
        properties.put("base", new JSONObject().put("type", "string").put("description", "workspace, project, scripts, or resources."));
        properties.put("maxDepth", new JSONObject().put("type", "integer").put("description", "Maximum depth to traverse."));
        properties.put("maxEntries", new JSONObject().put("type", "integer").put("description", "Maximum entries to return."));
        properties.put("includeFiles", new JSONObject().put("type", "boolean"));
        properties.put("includeDirectories", new JSONObject().put("type", "boolean"));
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        String base = optionalString(arguments, "base", "project");
        Path root = arguments.has("root")
                ? ToolPaths.resolvePath(context, requireString(arguments, "root"), base)
                : ToolPaths.resolveBase(context, base);
        int maxDepth = Math.max(0, optionalInt(arguments, "maxDepth", 4));
        int maxEntries = Math.max(1, optionalInt(arguments, "maxEntries", 200));
        boolean includeFiles = optionalBoolean(arguments, "includeFiles", true);
        boolean includeDirectories = optionalBoolean(arguments, "includeDirectories", true);

        JSONArray entries = new JSONArray();
        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            stream.filter(path -> !path.equals(root))
                    .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> {
                        if (entries.length() >= maxEntries) {
                            return;
                        }
                        boolean directory = Files.isDirectory(path);
                        if ((directory && !includeDirectories) || (!directory && !includeFiles)) {
                            return;
                        }
                        JSONObject item = new JSONObject();
                        item.put("path", path.toString());
                        item.put("relativePath", safeRelative(root, path));
                        item.put("kind", directory ? "directory" : "file");
                        item.put("depth", Math.max(0, root.relativize(path).getNameCount()));
                        entries.put(item);
                    });
        }

        JSONObject data = new JSONObject();
        data.put("root", root.toString());
        data.put("entries", entries);
        data.put("truncated", entries.length() >= maxEntries);
        return SceneMaxToolResult.success("Listed " + entries.length() + " project tree entrie(s).", data);
    }

    private String safeRelative(Path root, Path path) {
        try {
            return root.relativize(path).toString();
        } catch (Exception ignored) {
            return path.toString();
        }
    }
}
