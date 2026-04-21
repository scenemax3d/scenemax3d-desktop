package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class UiDeleteLayerTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.delete_layer";
    }

    @Override
    public String getDescription() {
        return "Removes a layer and all its widgets from a .smui document.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("layerName", new JSONObject().put("type", "string"))
                        .put("layerId", new JSONObject().put("type", "string"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("path"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = UiAuthoringSupport.resolveUiDocPath(context, arguments);
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        String layerName = optionalString(arguments, "layerName", "");
        String layerId = optionalString(arguments, "layerId", "");
        if (layerName.isEmpty() && layerId.isEmpty()) {
            throw new IllegalArgumentException("Provide layerName or layerId.");
        }

        JSONObject root = UiAuthoringSupport.readUiDoc(path);
        int index = UiAuthoringSupport.findLayerIndex(root, !layerName.isEmpty() ? layerName : layerId);
        if (index < 0) {
            throw new IllegalArgumentException("No layer found matching the provided layerName/layerId.");
        }

        JSONObject removed = root.getJSONArray("layers").optJSONObject(index);
        root.getJSONArray("layers").remove(index);

        UiAuthoringSupport.writeUiDoc(path, root);
        boolean reloaded = UiAuthoringSupport.reloadInIdeIfOpen(context, path, arguments);

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("removedLayer", removed == null ? JSONObject.NULL : removed);
        data.put("reloaded", reloaded);
        return SceneMaxToolResult.success("Deleted layer " + (removed == null ? "" : removed.optString("name")) + ".", data);
    }
}
