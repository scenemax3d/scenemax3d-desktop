package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerAddEntitiesBatchTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.add_entities_batch";
    }

    @Override
    public String getDescription() {
        return "Appends multiple entities to a scene designer JSON file and optionally reloads the document.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("entities", new JSONObject().put("type", "array"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("entities"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSceneDesigner(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        JSONArray newEntities = arguments.optJSONArray("entities");
        if (newEntities == null || newEntities.length() == 0) {
            throw new IllegalArgumentException("entities must contain at least one entity object.");
        }

        JSONObject root = DesignerAutomationSupport.readJson(path);
        JSONArray entities = root.optJSONArray("entities");
        if (entities == null) {
            entities = new JSONArray();
            root.put("entities", entities);
        }

        JSONArray addedIds = new JSONArray();
        for (int i = 0; i < newEntities.length(); i++) {
            JSONObject entity = newEntities.optJSONObject(i);
            if (entity == null) {
                throw new IllegalArgumentException("entities[" + i + "] must be a JSON object.");
            }
            JSONObject copy = new JSONObject(entity.toString());
            DesignerAutomationSupport.ensureEntityIds(copy);
            addedIds.put(copy.optString("id"));
            entities.put(copy);
        }

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
        data.put("addedCount", addedIds.length());
        data.put("addedEntityIds", addedIds);
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Added " + addedIds.length() + " entities to " + path.getFileName() + ".", data);
    }
}
