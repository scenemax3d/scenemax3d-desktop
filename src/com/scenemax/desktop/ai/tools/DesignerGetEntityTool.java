package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerGetEntityTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.get_entity";
    }

    @Override
    public String getDescription() {
        return "Returns a single scene entity from a .smdesign document by id or name.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("entity_id", new JSONObject().put("type", "string"))
                        .put("entity_name", new JSONObject().put("type", "string")));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSceneDesigner(path);

        String entityId = optionalString(arguments, "entity_id", "");
        String entityName = optionalString(arguments, "entity_name", "");
        if (entityId.isEmpty() && entityName.isEmpty()) {
            throw new IllegalArgumentException("Provide entity_id or entity_name.");
        }

        JSONObject root = DesignerAutomationSupport.readJson(path);
        JSONArray entities = root.optJSONArray("entities");
        JSONObject entity = DesignerAutomationSupport.findEntity(entities, entityId, entityName);
        if (entity == null) {
            throw new IllegalArgumentException("Could not find an entity matching the provided id/name.");
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("entity", DesignerAutomationSupport.deepCopyObject(entity));
        return SceneMaxToolResult.success("Loaded entity " + DesignerAutomationSupport.entityLabel(entity) + ".", data);
    }
}
