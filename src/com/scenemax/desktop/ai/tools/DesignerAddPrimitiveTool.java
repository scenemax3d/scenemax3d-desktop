package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class DesignerAddPrimitiveTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.add_primitive";
    }

    @Override
    public String getDescription() {
        return "Adds a primitive 3D object to the active scene designer tab.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("primitive", new JSONObject()
                .put("type", "string")
                .put("description", "sphere, box, cylinder, hollow_cylinder, or quad"));
        properties.put("saveDocument", new JSONObject().put("type", "boolean"));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("primitive"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Primitive creation requires a running SceneMax IDE host.");
        }

        JSONObject entity = host.addPrimitiveToActiveSceneDesignerForAutomation(
                requireString(arguments, "primitive"),
                optionalBoolean(arguments, "saveDocument", true)
        );
        return SceneMaxToolResult.success("Added " + entity.optString("type", "primitive") + " entity.", new JSONObject().put("entity", entity));
    }
}
