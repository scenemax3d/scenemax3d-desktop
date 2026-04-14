package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class RunPreviewSceneTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "run.preview_scene";
    }

    @Override
    public String getDescription() {
        return "Saves the active editor tab if needed and launches a SceneMax preview run.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray());
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Preview execution requires a running SceneMax IDE host.");
        }

        boolean launched = host.runPreviewFromAutomation();
        JSONObject data = new JSONObject();
        data.put("launched", launched);
        if (!launched) {
            return SceneMaxToolResult.error("Scene preview could not be launched.", data);
        }
        return SceneMaxToolResult.success("Scene preview launched.", data);
    }
}
