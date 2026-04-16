package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

import java.util.List;

public class DesignerListAvailableMaterialsTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.list_available_materials";
    }

    @Override
    public String getDescription() {
        return "Lists the materials currently available to scene designer documents.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject());
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        List<String> names = DesignerAutomationSupport.getAvailableMaterialNames(context);
        JSONObject data = new JSONObject();
        data.put("materials", DesignerAutomationSupport.toJsonArray(names));
        data.put("count", names.size());
        data.put("resourcesRoot", context.getResourcesRoot() != null ? context.getResourcesRoot().toString() : JSONObject.NULL);
        return SceneMaxToolResult.success("Listed available designer materials.", data);
    }
}
