package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerUpdateEntityTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.update_entity";
    }

    @Override
    public String getDescription() {
        return "Updates an existing scene entity in a .smdesign document by id or name.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("entity_id", new JSONObject().put("type", "string"))
                        .put("entity_name", new JSONObject().put("type", "string"))
                        .put("updates", new JSONObject().put("type", "object"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("updates"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSceneDesigner(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        String entityId = optionalString(arguments, "entity_id", "");
        String entityName = optionalString(arguments, "entity_name", "");
        if (entityId.isEmpty() && entityName.isEmpty()) {
            throw new IllegalArgumentException("Provide entity_id or entity_name.");
        }

        JSONObject updates = arguments.optJSONObject("updates");
        if (updates == null) {
            throw new IllegalArgumentException("updates must be an object.");
        }

        JSONObject root = DesignerAutomationSupport.readJson(path);
        DesignerAutomationSupport.EntityRef ref =
                DesignerAutomationSupport.findEntityRef(root.optJSONArray("entities"), entityId, entityName);
        if (ref == null) {
            throw new IllegalArgumentException("Could not find an entity matching the provided id/name.");
        }

        DesignerAutomationSupport.applyUpdates(ref.entity, updates);
        DesignerAutomationSupport.ensureEntityIds(ref.entity);
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
        data.put("entity", DesignerAutomationSupport.deepCopyObject(ref.entity));
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Updated entity " + DesignerAutomationSupport.entityLabel(ref.entity) + ".", data);
    }
}
