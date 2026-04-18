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

public class DesignerCaptureCanvasReliableTool extends AbstractSceneMaxTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public String getName() {
        return "designer.capture_canvas_reliable";
    }

    @Override
    public String getDescription() {
        return "Captures the active scene/UI designer canvas using the renderer-backed snapshot path for reliable visual verification, defaulting to a clean scene snapshot.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("description", "Capture the active designer canvas through the renderer-backed reliable path. Use this for design-compare-improve loops instead of screen capture.")
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
                                .put("default", true)));
    }

    @Override
    public JSONObject getOutputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("description", "Information about the reliable designer capture that was saved.")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "Absolute path of the saved PNG capture."))
                        .put("kind", new JSONObject()
                                .put("type", "string")
                                .put("description", "Kind of designer that was captured.")
                                .put("enum", new JSONArray().put("scene_designer").put("ui_designer")))
                        .put("width", new JSONObject().put("type", "integer").put("description", "Actual saved image width in pixels."))
                        .put("height", new JSONObject().put("type", "integer").put("description", "Actual saved image height in pixels."))
                        .put("captureMode", new JSONObject()
                                .put("type", "string")
                                .put("description", "Renderer-backed capture implementation used by the host, such as scene_view_renderer or ui_snapshot."))
                        .put("clean", new JSONObject().put("type", "boolean").put("description", "Whether editor-only scene aids were hidden during capture.")))
                .put("required", new JSONArray().put("path").put("kind").put("width").put("height").put("captureMode").put("clean"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Reliable canvas capture requires a running IDE host.");
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
                    .resolve("designer_canvas_reliable_" + FORMATTER.format(LocalDateTime.now()) + ".png")
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
        boolean clean = optionalBoolean(arguments, "clean", true);
        JSONObject data = host.captureActiveDesignerCanvasForAutomationReliable(outputPath.toFile(), width, height, clean);
        data.put("clean", clean);
        return SceneMaxToolResult.success("Captured the active designer canvas with the reliable capture path.", data);
    }
}
