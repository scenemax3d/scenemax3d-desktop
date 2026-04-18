package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
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
                .put("description", "No input is required. The tool lists scene entities from the currently active scene designer tab.")
                .put("properties", new JSONObject());
    }

    @Override
    public JSONObject getOutputSchema() {
        JSONObject entitySchema = new JSONObject()
                .put("type", "object")
                .put("description", "Summarized scene designer entity metadata.")
                .put("properties", new JSONObject()
                        .put("id", new JSONObject().put("type", "string").put("description", "Entity id."))
                        .put("name", new JSONObject().put("type", "string").put("description", "Entity display name."))
                        .put("type", new JSONObject().put("type", "string").put("description", "Entity type in lower-case form."))
                        .put("material", new JSONObject().put("type", "string").put("description", "Applied material name when present."))
                        .put("shader", new JSONObject().put("type", "string").put("description", "Applied shader name when present."))
                        .put("resourcePath", new JSONObject().put("type", "string").put("description", "Referenced resource path for imported assets when present."))
                        .put("position", new JSONObject().put("type", "array").put("items", new JSONObject().put("type", "number")))
                        .put("rotation", new JSONObject().put("type", "array").put("items", new JSONObject().put("type", "number")))
                        .put("scale", new JSONObject().put("type", "array").put("items", new JSONObject().put("type", "number")))
                        .put("cinematicRuntimeId", new JSONObject().put("type", "string"))
                        .put("cinematicTargetEntityId", new JSONObject().put("type", "string"))
                        .put("cinematicTargetEntityName", new JSONObject().put("type", "string"))
                        .put("cinematicSegmentCount", new JSONObject().put("type", "integer"))
                        .put("children", new JSONObject()
                                .put("type", "array")
                                .put("description", "Nested child entities when this entity is a section or group.")
                                .put("items", new JSONObject().put("type", "object"))))
                .put("required", new JSONArray()
                        .put("id")
                        .put("name")
                        .put("type")
                        .put("material")
                        .put("shader")
                        .put("resourcePath")
                        .put("position")
                        .put("rotation")
                        .put("scale"));

        return new JSONObject()
                .put("type", "object")
                .put("description", "Scene entities from the active scene designer tab.")
                .put("properties", new JSONObject()
                        .put("entities", new JSONObject()
                                .put("type", "array")
                                .put("items", entitySchema)))
                .put("required", new JSONArray().put("entities"));
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
