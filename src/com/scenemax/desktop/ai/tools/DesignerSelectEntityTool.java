package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class DesignerSelectEntityTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.select_entity";
    }

    @Override
    public String getDescription() {
        return "Selects an entity in the active scene designer tab by id or name.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("entity_id", new JSONObject().put("type", "string"))
                        .put("entity_name", new JSONObject().put("type", "string")));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Entity selection requires a running IDE host.");
        }

        String entityId = optionalString(arguments, "entity_id", "");
        String entityName = optionalString(arguments, "entity_name", "");
        if (entityId.isEmpty() && entityName.isEmpty()) {
            throw new IllegalArgumentException("Provide entity_id or entity_name.");
        }

        JSONObject data = host.selectEntityInActiveSceneDesignerForAutomation(entityId, entityName);
        return SceneMaxToolResult.success("Selected scene entity.", new JSONObject().put("entity", data));
    }
}
