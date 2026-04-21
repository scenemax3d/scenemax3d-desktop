package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemaxeng.common.ui.model.UIRenderMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public class UiUpdateLayerTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.update_layer";
    }

    @Override
    public String getDescription() {
        return "Updates a layer's name / visibility / zOrder / renderMode. Rename is safe — no widget constraint "
                + "references a layer name (layers are resolved positionally), but layer names must remain unique.";
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
                        .put("newName", new JSONObject().put("type", "string"))
                        .put("visible", new JSONObject().put("type", "boolean"))
                        .put("zOrder", new JSONObject().put("type", "integer"))
                        .put("renderMode", new JSONObject().put("type", "string"))
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
        JSONObject layer = UiAuthoringSupport.findLayer(root, !layerName.isEmpty() ? layerName : layerId);
        if (layer == null) {
            throw new IllegalArgumentException("No layer found matching the provided layerName/layerId.");
        }

        if (arguments.has("newName")) {
            String newName = optionalString(arguments, "newName", "").trim();
            if (newName.isEmpty()) {
                throw new IllegalArgumentException("newName cannot be blank.");
            }
            if (newName.contains(".")) {
                throw new IllegalArgumentException("newName must not contain '.'.");
            }
            String oldName = layer.optString("name");
            if (!newName.equals(oldName)) {
                Set<String> taken = UiAuthoringSupport.collectAllNames(root);
                taken.remove(oldName);
                if (taken.contains(newName)) {
                    throw new IllegalArgumentException("newName already in use: " + newName);
                }
                layer.put("name", newName);
            }
        }
        if (arguments.has("visible")) {
            layer.put("visible", arguments.optBoolean("visible", true));
        }
        if (arguments.has("zOrder")) {
            layer.put("zOrder", arguments.optInt("zOrder", 0));
        }
        if (arguments.has("renderMode")) {
            String rm = arguments.optString("renderMode", "").toUpperCase(Locale.ROOT);
            try {
                UIRenderMode.valueOf(rm);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid renderMode: " + rm);
            }
            layer.put("renderMode", rm);
        }

        UiAuthoringSupport.writeUiDoc(path, root);
        boolean reloaded = UiAuthoringSupport.reloadInIdeIfOpen(context, path, arguments);

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("layer", layer);
        data.put("reloaded", reloaded);
        return SceneMaxToolResult.success("Updated layer " + layer.optString("name") + ".", data);
    }
}
