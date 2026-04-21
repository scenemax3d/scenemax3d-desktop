package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemaxeng.common.ui.model.UIRenderMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class UiAddLayerTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.add_layer";
    }

    @Override
    public String getDescription() {
        return "Adds a new empty layer to a .smui document. Layer name must be unique across the entire document.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("name", new JSONObject().put("type", "string"))
                        .put("visible", new JSONObject().put("type", "boolean"))
                        .put("zOrder", new JSONObject().put("type", "integer"))
                        .put("renderMode", new JSONObject().put("type", "string").put("description", "SCREEN_SPACE or WORLD_SPACE"))
                        .put("index", new JSONObject().put("type", "integer").put("description", "Insert position (default: append)."))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("path").put("name"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = UiAuthoringSupport.resolveUiDocPath(context, arguments);
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        String name = requireString(arguments, "name");
        if (name.contains(".")) {
            throw new IllegalArgumentException("Layer name must not contain '.' (dot is used as a path separator).");
        }

        JSONObject root = UiAuthoringSupport.readUiDoc(path);
        Set<String> taken = UiAuthoringSupport.collectAllNames(root);
        if (taken.contains(name)) {
            throw new IllegalArgumentException("Name is already in use in this document: " + name);
        }

        String renderMode = optionalString(arguments, "renderMode", "SCREEN_SPACE").toUpperCase(Locale.ROOT);
        try {
            UIRenderMode.valueOf(renderMode);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid renderMode: " + renderMode + " (use SCREEN_SPACE or WORLD_SPACE).");
        }

        JSONObject layer = new JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("name", name)
                .put("visible", optionalBoolean(arguments, "visible", true))
                .put("zOrder", optionalInt(arguments, "zOrder", 0))
                .put("renderMode", renderMode)
                .put("widgets", new JSONArray());

        JSONArray layers = root.getJSONArray("layers");
        int insertIndex = optionalInt(arguments, "index", -1);
        if (insertIndex < 0 || insertIndex >= layers.length()) {
            layers.put(layer);
            insertIndex = layers.length() - 1;
        } else {
            // JSONArray has no insert at index; rebuild
            JSONArray rebuilt = new JSONArray();
            for (int i = 0; i < layers.length(); i++) {
                if (i == insertIndex) {
                    rebuilt.put(layer);
                }
                rebuilt.put(layers.get(i));
            }
            root.put("layers", rebuilt);
        }

        UiAuthoringSupport.writeUiDoc(path, root);
        boolean reloaded = UiAuthoringSupport.reloadInIdeIfOpen(context, path, arguments);

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("layer", layer);
        data.put("index", insertIndex);
        data.put("reloaded", reloaded);
        return SceneMaxToolResult.success("Added layer " + name + ".", data);
    }
}
