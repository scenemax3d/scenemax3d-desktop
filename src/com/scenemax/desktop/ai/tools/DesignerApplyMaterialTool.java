package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.List;

public class DesignerApplyMaterialTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.apply_material";
    }

    @Override
    public String getDescription() {
        return "Applies a material to a scene entity by id or name in a .smdesign document and optionally reloads it.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("entityId", new JSONObject().put("type", "string"))
                        .put("entityName", new JSONObject().put("type", "string"))
                        .put("material", new JSONObject().put("type", "string"))
                        .put("allowUnknown", new JSONObject().put("type", "boolean"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("material"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSceneDesigner(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        String entityId = optionalString(arguments, "entityId", "");
        String entityName = optionalString(arguments, "entityName", "");
        if (entityId.isBlank() && entityName.isBlank()) {
            throw new IllegalArgumentException("Provide entityId or entityName.");
        }

        String material = requireString(arguments, "material");
        if (!optionalBoolean(arguments, "allowUnknown", false)) {
            List<String> available = DesignerAutomationSupport.getAvailableMaterialNames(context);
            if (!available.contains(material)) {
                throw new IllegalArgumentException("Unknown material: " + material);
            }
        }

        JSONObject root = DesignerAutomationSupport.readJson(path);
        JSONObject entity = DesignerAutomationSupport.findEntity(root.optJSONArray("entities"), entityId, entityName);
        if (entity == null) {
            throw new IllegalArgumentException("Could not find the target entity.");
        }
        entity.put("material", material);
        DesignerAutomationSupport.writeJson(path, root);
        JSONObject validation = DesignerAutomationSupport.validateDocument(context, path);

        boolean reloaded = false;
        if (optionalBoolean(arguments, "reload", true)) {
            MainApp host = context.getHost();
            if (host != null) {
                reloaded = host.reloadFileFromDiskForAutomation(path.toFile());
            }
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("entityId", entity.optString("id"));
        data.put("entityName", entity.optString("name"));
        data.put("material", material);
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Applied material '" + material + "' in " + path.getFileName() + ".", data);
    }
}
