package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class IdeGetRecentErrorsTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ide.get_recent_errors";
    }

    @Override
    public String getDescription() {
        return "Returns the most recent IDE MCP/server errors captured by the host.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("limit", new JSONObject().put("type", "integer")));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("The IDE host is not available.");
        }
        int limit = Math.max(1, optionalInt(arguments, "limit", 10));
        JSONObject data = host.getRecentAutomationErrors(limit);
        return SceneMaxToolResult.success("Fetched recent IDE errors.", data);
    }
}
