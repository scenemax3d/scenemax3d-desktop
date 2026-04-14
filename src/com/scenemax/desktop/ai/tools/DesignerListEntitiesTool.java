package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class DesignerListEntitiesTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.list_entities";
    }

    @Override
    public String getDescription() {
        return "Lists the entities in the active scene designer tab.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject());
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Entity inspection requires a running SceneMax IDE host.");
        }

        JSONObject data = new JSONObject();
        data.put("entities", host.getActiveSceneDesignerEntitiesForAutomation());
        return SceneMaxToolResult.success("Listed active scene designer entities.", data);
    }
}
