package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

public class ProjectSearchFilesTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "project.search_files";
    }

    @Override
    public String getDescription() {
        return "Searches for files by name under the workspace or active project.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("query", new JSONObject().put("type", "string").put("description", "Substring to match in file names."));
        properties.put("root", new JSONObject().put("type", "string").put("description", "Optional path to search from."));
        properties.put("base", new JSONObject().put("type", "string").put("description", "workspace, project, scripts, or resources."));
        properties.put("maxResults", new JSONObject().put("type", "integer").put("description", "Maximum matches to return."));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("query"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        String query = requireString(arguments, "query").toLowerCase(Locale.ROOT);
        String base = optionalString(arguments, "base", "project");
        Path root = arguments.has("root")
                ? ToolPaths.resolvePath(context, requireString(arguments, "root"), base)
                : ToolPaths.resolveBase(context, base);
        int maxResults = Math.max(1, optionalInt(arguments, "maxResults", 30));

        JSONArray matches = new JSONArray();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(query))
                    .limit(maxResults)
                    .forEach(path -> matches.put(toMatch(root, path)));
        }

        JSONObject data = new JSONObject();
        data.put("root", root.toString());
        data.put("matches", matches);
        return SceneMaxToolResult.success("Found " + matches.length() + " file match(es).", data);
    }

    private JSONObject toMatch(Path root, Path path) {
        JSONObject match = new JSONObject();
        match.put("path", path.toString());
        match.put("relativePath", safeRelative(root, path));
        return match;
    }

    private String safeRelative(Path root, Path path) {
        try {
            return root.relativize(path).toString();
        } catch (Exception ignored) {
            return path.toString();
        }
    }
}
