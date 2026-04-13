package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class ProjectSearchTextTool extends AbstractSceneMaxTool {

    private static final long MAX_FILE_BYTES = 1_000_000;
    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>();

    static {
        String[] extensions = {
                ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".zip", ".jar", ".class",
                ".dll", ".exe", ".so", ".bin", ".dat", ".mp3", ".wav", ".ogg", ".glb", ".gltf", ".ttf"
        };
        for (String extension : extensions) {
            BINARY_EXTENSIONS.add(extension);
        }
    }

    @Override
    public String getName() {
        return "project.search_text";
    }

    @Override
    public String getDescription() {
        return "Searches text content across files in the workspace or active project.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("query", new JSONObject().put("type", "string").put("description", "Text to search for."));
        properties.put("root", new JSONObject().put("type", "string").put("description", "Optional path to search from."));
        properties.put("base", new JSONObject().put("type", "string").put("description", "workspace, project, scripts, or resources."));
        properties.put("maxResults", new JSONObject().put("type", "integer").put("description", "Maximum matches to return."));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("query"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        String query = requireString(arguments, "query");
        String base = optionalString(arguments, "base", "project");
        Path root = arguments.has("root")
                ? ToolPaths.resolvePath(context, requireString(arguments, "root"), base)
                : ToolPaths.resolveBase(context, base);
        int maxResults = Math.max(1, optionalInt(arguments, "maxResults", 30));

        JSONArray matches = new JSONArray();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                if (matches.length() >= maxResults || shouldSkip(path)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size() && matches.length() < maxResults; i++) {
                    String line = lines.get(i);
                    if (line.contains(query)) {
                        JSONObject match = new JSONObject();
                        match.put("path", path.toString());
                        match.put("relativePath", safeRelative(root, path));
                        match.put("lineNumber", i + 1);
                        match.put("lineText", truncate(line));
                        matches.put(match);
                    }
                }
            }
        }

        JSONObject data = new JSONObject();
        data.put("root", root.toString());
        data.put("query", query);
        data.put("matches", matches);
        return SceneMaxToolResult.success("Found " + matches.length() + " text match(es).", data);
    }

    private boolean shouldSkip(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : BINARY_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return Files.size(path) > MAX_FILE_BYTES;
    }

    private String safeRelative(Path root, Path path) {
        try {
            return root.relativize(path).toString();
        } catch (Exception ignored) {
            return path.toString();
        }
    }

    private String truncate(String line) {
        if (line.length() <= 240) {
            return line;
        }
        return line.substring(0, 237) + "...";
    }
}
