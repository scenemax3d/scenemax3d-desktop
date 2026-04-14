package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class DesignerCreateCinematicRigTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.create_cinematic_rig";
    }

    @Override
    public String getDescription() {
        return "Creates a cinematic rig in the active scene designer tab.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("preset", new JSONObject().put("type", "string")
                .put("description", "empty, football_flyover, orbit_push_in, or sideline_sweep"));
        properties.put("targetEntityId", new JSONObject().put("type", "string"));
        properties.put("targetEntityName", new JSONObject().put("type", "string"));
        properties.put("saveDocument", new JSONObject().put("type", "boolean"));
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Cinematic rig creation requires a running SceneMax IDE host.");
        }

        JSONObject rig = host.createCinematicRigInActiveSceneDesignerForAutomation(
                optionalString(arguments, "preset", "empty"),
                optionalString(arguments, "targetEntityId", ""),
                optionalString(arguments, "targetEntityName", ""),
                optionalBoolean(arguments, "saveDocument", true)
        );
        return SceneMaxToolResult.success("Created cinematic rig.", new JSONObject().put("rig", rig));
    }
}
