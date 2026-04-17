package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerOverlayReferenceImageTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.overlay_reference_image";
    }

    @Override
    public String getDescription() {
        return "Shows or hides a reference image overlay over the active scene designer canvas. Accepts a workspace image path or a directly attached image file object.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject fileInputSchema = new JSONObject()
                .put("description", "Either a workspace image path string or an attached-file object. Attached files can provide local_path/path/url/download_url and will be staged into workspace/tmp/mcp-inputs when needed.")
                .put("oneOf", new JSONArray()
                        .put(new JSONObject()
                                .put("type", "string")
                                .put("description", "Path to an image file, resolved relative to the selected base when not absolute."))
                        .put(new JSONObject()
                                .put("type", "object")
                                .put("description", "Attached-file object passed through the tool layer.")
                                .put("properties", new JSONObject()
                                        .put("path", new JSONObject().put("type", "string").put("description", "Workspace or absolute image path."))
                                        .put("local_path", new JSONObject().put("type", "string").put("description", "Local filesystem path supplied by the caller."))
                                        .put("download_url", new JSONObject().put("type", "string").put("description", "Download URL for staged external files."))
                                        .put("url", new JSONObject().put("type", "string").put("description", "Generic file URL for staged external files."))
                                        .put("name", new JSONObject().put("type", "string").put("description", "Display name used when staging the file."))
                                        .put("filename", new JSONObject().put("type", "string").put("description", "Alternate filename field used by some clients."))
                                        .put("content_type", new JSONObject().put("type", "string").put("description", "Optional MIME type hint."))
                                        .put("mime_type", new JSONObject().put("type", "string").put("description", "Optional MIME type hint.")))));
        return new JSONObject()
                .put("type", "object")
                .put("description", "Show or hide a reference image overlay in the active scene designer. This is intended for manual visual alignment while modeling.")
                .put("properties", new JSONObject()
                        .put("image_path", new JSONObject(fileInputSchema.toString()).put("description", "Reference image to show. Alias of image."))
                        .put("image", new JSONObject(fileInputSchema.toString()).put("description", "Reference image to show. Alias of image_path."))
                        .put("base", new JSONObject()
                                .put("type", "string")
                                .put("description", "Path base used to resolve relative image_path or image.path values.")
                                .put("enum", new JSONArray().put("workspace").put("project").put("scripts").put("resources"))
                                .put("default", "workspace"))
                        .put("visible", new JSONObject()
                                .put("type", "boolean")
                                .put("description", "Set true to show/update the overlay, or false to clear it.")
                                .put("default", true))
                        .put("opacity", new JSONObject()
                                .put("type", "number")
                                .put("description", "Overlay opacity from 0.0 to 1.0.")
                                .put("default", 0.35))
                        .put("scale", new JSONObject()
                                .put("type", "number")
                                .put("description", "Extra scale multiplier applied after fit mode.")
                                .put("default", 1.0))
                        .put("fit_mode", new JSONObject()
                                .put("type", "string")
                                .put("description", "Overlay fitting strategy inside the designer viewport.")
                                .put("enum", new JSONArray().put("contain").put("cover").put("stretch"))
                                .put("default", "contain"))
                        .put("offset_x", new JSONObject()
                                .put("type", "integer")
                                .put("description", "Horizontal overlay offset in pixels.")
                                .put("default", 0))
                        .put("offset_y", new JSONObject()
                                .put("type", "integer")
                                .put("description", "Vertical overlay offset in pixels.")
                                .put("default", 0)));
    }

    @Override
    public JSONObject getOutputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("description", "Current state of the reference image overlay after the request.")
                .put("properties", new JSONObject()
                        .put("visible", new JSONObject().put("type", "boolean").put("description", "Whether the overlay is visible after the operation."))
                        .put("imagePath", new JSONObject().put("type", "string").put("description", "Resolved local path currently displayed by the overlay."))
                        .put("resolvedImagePath", new JSONObject().put("type", "string").put("description", "Resolved or staged local image path returned by the tool layer when visible=true."))
                        .put("opacity", new JSONObject().put("type", "number"))
                        .put("scale", new JSONObject().put("type", "number"))
                        .put("fitMode", new JSONObject().put("type", "string"))
                        .put("offsetX", new JSONObject().put("type", "integer"))
                        .put("offsetY", new JSONObject().put("type", "integer")))
                .put("required", new JSONArray().put("visible"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Reference overlays require a running IDE host.");
        }

        boolean visible = optionalBoolean(arguments, "visible", true);
        Path imagePath = null;
        if (visible) {
            Object rawInput = arguments.has("image") ? arguments.opt("image") : arguments.opt("image_path");
            imagePath = ToolPaths.resolveFlexibleFileInput(
                    context,
                    rawInput,
                    optionalString(arguments, "base", "workspace"),
                    "reference_overlay");
        }

        JSONObject data = host.showReferenceOverlayInActiveSceneDesignerForAutomation(
                imagePath != null ? imagePath.toFile() : null,
                (float) arguments.optDouble("opacity", 0.35d),
                arguments.has("scale") ? arguments.optDouble("scale", 1d) : 1d,
                optionalString(arguments, "fit_mode", "contain"),
                optionalInt(arguments, "offset_x", 0),
                optionalInt(arguments, "offset_y", 0),
                visible);
        if (imagePath != null) {
            data.put("resolvedImagePath", imagePath.toString());
        }
        return SceneMaxToolResult.success(visible ? "Displayed the reference image overlay." : "Cleared the reference image overlay.", data);
    }
}
