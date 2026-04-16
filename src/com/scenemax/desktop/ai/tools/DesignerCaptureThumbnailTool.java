package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DesignerCaptureThumbnailTool extends AbstractSceneMaxTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public String getName() {
        return "designer.capture_thumbnail";
    }

    @Override
    public String getDescription() {
        return "Captures a PNG thumbnail of the currently visible editor/designer tab.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("width", new JSONObject().put("type", "integer"))
                        .put("height", new JSONObject().put("type", "integer")));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Thumbnail capture requires a running IDE host.");
        }

        Path outputPath;
        String rawPath = arguments.optString("path", "").trim();
        if (!rawPath.isEmpty()) {
            outputPath = ToolPaths.resolvePath(context, rawPath, optionalString(arguments, "base", "workspace"));
        } else {
            outputPath = context.getWorkspaceRoot()
                    .resolve("tmp")
                    .resolve("mcp-captures")
                    .resolve("designer_capture_" + FORMATTER.format(LocalDateTime.now()) + ".png")
                    .normalize()
                    .toAbsolutePath();
            ToolPaths.ensureAllowed(context, outputPath);
        }

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        int width = optionalInt(arguments, "width", 1280);
        int height = optionalInt(arguments, "height", 720);
        host.captureActiveDocumentThumbnailForAutomation(outputPath.toFile(), width, height);

        JSONObject data = new JSONObject();
        data.put("path", outputPath.toString());
        data.put("width", width);
        data.put("height", height);
        return SceneMaxToolResult.success("Captured a thumbnail of the active tab.", data);
    }
}
