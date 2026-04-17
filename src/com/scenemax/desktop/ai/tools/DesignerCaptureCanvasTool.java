package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DesignerCaptureCanvasTool extends AbstractSceneMaxTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public String getName() {
        return "designer.capture_canvas";
    }

    @Override
    public String getDescription() {
        return "Captures a PNG of just the active scene/UI designer canvas, excluding toolbars and side panels. Scene captures can optionally hide editor-only aids.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("description", "Capture the visible designer canvas to a PNG file. This uses the active designer view and can optionally hide editor-only aids in scene designers.")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject()
                                .put("type", "string")
                                .put("description", "Optional output PNG path. If omitted, a timestamped file is created under workspace/tmp/mcp-captures."))
                        .put("base", new JSONObject()
                                .put("type", "string")
                                .put("description", "Path base used to resolve a relative path.")
                                .put("enum", new JSONArray().put("workspace").put("project").put("scripts").put("resources"))
                                .put("default", "workspace"))
                        .put("width", new JSONObject()
                                .put("type", "integer")
                                .put("description", "Requested capture width in pixels.")
                                .put("default", 1280))
                        .put("height", new JSONObject()
                                .put("type", "integer")
                                .put("description", "Requested capture height in pixels.")
                                .put("default", 720))
                        .put("clean", new JSONObject()
                                .put("type", "boolean")
                                .put("description", "When true in a scene designer, hides editor-only aids such as grid, gizmos, selection outlines, and scene camera markers during capture.")
                                .put("default", false)));
    }

    @Override
    public JSONObject getOutputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("description", "Information about the saved designer canvas capture.")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "Absolute path of the saved PNG capture."))
                        .put("width", new JSONObject().put("type", "integer").put("description", "Requested output width in pixels."))
                        .put("height", new JSONObject().put("type", "integer").put("description", "Requested output height in pixels."))
                        .put("clean", new JSONObject().put("type", "boolean").put("description", "Whether editor-only scene aids were hidden during capture.")))
                .put("required", new JSONArray().put("path").put("width").put("height").put("clean"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Canvas capture requires a running IDE host.");
        }

        Path outputPath;
        String rawPath = arguments.optString("path", "").trim();
        if (!rawPath.isEmpty()) {
            String lowerPath = rawPath.toLowerCase(Locale.ROOT);
            if (!lowerPath.endsWith(".png")) {
                throw new IllegalArgumentException("path must point to a .png output file.");
            }
            outputPath = ToolPaths.resolvePath(context, rawPath, optionalString(arguments, "base", "workspace"));
        } else {
            outputPath = context.getWorkspaceRoot()
                    .resolve("tmp")
                    .resolve("mcp-captures")
                    .resolve("designer_canvas_" + FORMATTER.format(LocalDateTime.now()) + ".png")
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
        boolean clean = optionalBoolean(arguments, "clean", false);
        host.captureActiveDesignerCanvasForAutomation(outputPath.toFile(), width, height, clean);

        JSONObject data = new JSONObject();
        data.put("path", outputPath.toString());
        data.put("width", width);
        data.put("height", height);
        data.put("clean", clean);
        return SceneMaxToolResult.success("Captured the active designer canvas.", data);
    }
}
