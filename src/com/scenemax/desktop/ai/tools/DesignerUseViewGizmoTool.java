package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class DesignerUseViewGizmoTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.use_view_gizmo";
    }

    @Override
    public String getDescription() {
        return "Uses the scene designer's top-right view gizmo presets to switch to front/back/left/right/top/bottom/default views.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("preset", new JSONObject().put("type", "string"))
                        .put("animated", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("preset"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("View gizmo control requires a running IDE host.");
        }

        String preset = requireString(arguments, "preset");
        boolean animated = optionalBoolean(arguments, "animated", true);
        JSONObject data = host.applyViewGizmoPresetForAutomation(preset, animated);
        return SceneMaxToolResult.success("Switched the scene designer view gizmo preset.", data);
    }
}
