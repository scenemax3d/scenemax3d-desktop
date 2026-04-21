package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class UiListLayersTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.list_layers";
    }

    @Override
    public String getDescription() {
        return "Lists the layers in a .smui document with their id, name, visibility, z-order, render mode, and widget count.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "Path to the .smui file."))
                        .put("base", new JSONObject().put("type", "string").put("description", "workspace, project, scripts, or resources. Defaults to workspace.")))
                .put("required", new JSONArray().put("path"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = UiAuthoringSupport.resolveUiDocPath(context, arguments);
        DesignerAutomationSupport.ensureDiskFileExists(path);

        JSONObject root = UiAuthoringSupport.readUiDoc(path);
        JSONArray layersIn = root.optJSONArray("layers");
        JSONArray layersOut = new JSONArray();
        if (layersIn != null) {
            for (int i = 0; i < layersIn.length(); i++) {
                JSONObject layer = layersIn.optJSONObject(i);
                if (layer == null) {
                    continue;
                }
                JSONArray widgets = layer.optJSONArray("widgets");
                layersOut.put(new JSONObject()
                        .put("id", layer.optString("id"))
                        .put("name", layer.optString("name"))
                        .put("visible", layer.optBoolean("visible", true))
                        .put("zOrder", layer.optInt("zOrder", 0))
                        .put("renderMode", layer.optString("renderMode", "SCREEN_SPACE"))
                        .put("widgetCount", widgets == null ? 0 : widgets.length()));
            }
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("canvasWidth", root.optDouble("canvasWidth", 1920));
        data.put("canvasHeight", root.optDouble("canvasHeight", 1080));
        data.put("name", root.optString("name"));
        data.put("layers", layersOut);
        data.put("count", layersOut.length());
        return SceneMaxToolResult.success("Listed " + layersOut.length() + " layers.", data);
    }
}
