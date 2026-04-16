package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class AppRestartTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "app.restart";
    }

    @Override
    public String getDescription() {
        return "Restarts the SceneMax IDE.";
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
            throw new IllegalStateException("The IDE host is not available.");
        }
        host.restartApplicationFromAutomation();
        return SceneMaxToolResult.success("Scheduled a SceneMax restart.", new JSONObject().put("scheduled", true));
    }
}
