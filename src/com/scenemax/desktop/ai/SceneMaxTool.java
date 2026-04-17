package com.scenemax.desktop.ai;

import org.json.JSONObject;

public interface SceneMaxTool {
    String getName();
    String getDescription();
    JSONObject getInputSchema();
    default JSONObject getOutputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("description", "Structured content returned by the tool.");
    }
    SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception;
}
