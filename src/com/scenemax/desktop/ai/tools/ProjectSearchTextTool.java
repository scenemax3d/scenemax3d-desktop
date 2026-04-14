package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ProjectSearchTextTool extends AbstractSceneMaxTool {

    private static final long MAX_FILE_BYTES = 1_000_000;
    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>();
    private static final Set<String> SKIP_DIRECTORIES = new HashSet<>();

    static {
        String[] extensions = {
                ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".zip", ".jar", ".class",
                ".dll", ".exe", ".so", ".bin", ".dat", ".mp3", ".wav", ".ogg", ".glb", ".gltf", ".ttf",
                ".fbx", ".obj", ".tga", ".dds", ".pdf", ".7z", ".rar", ".gz", ".tar"
        };
        for (String extension : extensions) {
            BINARY_EXTENSIONS.add(extension);
        }
        String[] directories = {
                ".git", ".hg", ".svn", "node_modules", ".gradle", ".idea", "build", "out", "dist", "target"
        };
        for (String directory : directories) {
            SKIP_DIRECTORIES.add(directory);
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
        Files.walkFileTree(root, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (matches.length() >= maxResults) {
                    return FileVisitResult.TERMINATE;
                }
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (!dir.equals(root) && (name.startsWith(".") || SKIP_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT)))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (matches.length() >= maxResults) {
                    return FileVisitResult.TERMINATE;
                }
                if (!attrs.isRegularFile() || shouldSkip(path, attrs)) {
                    return FileVisitResult.CONTINUE;
                }
                scanFile(path, root, query, matches, maxResults);
                return matches.length() >= maxResults ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        JSONObject data = new JSONObject();
        data.put("root", root.toString());
        data.put("query", query);
        data.put("matches", matches);
        return SceneMaxToolResult.success("Found " + matches.length() + " text match(es).", data);
    }

    private void scanFile(Path path, Path root, String query, JSONArray matches, int maxResults) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (matches.length() >= maxResults) {
                    return;
                }
                if (line.contains(query)) {
                    JSONObject match = new JSONObject();
                    match.put("path", path.toString());
                    match.put("relativePath", safeRelative(root, path));
                    match.put("lineNumber", lineNumber);
                    match.put("lineText", truncate(line));
                    matches.put(match);
                }
            }
        } catch (MalformedInputException | UnmappableCharacterException e) {
            // File is not valid UTF-8 — skip it.
        } catch (IOException e) {
            // Unreadable for any other reason — skip it.
        }
    }

    private boolean shouldSkip(Path path, BasicFileAttributes attrs) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : BINARY_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return attrs.size() > MAX_FILE_BYTES;
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
