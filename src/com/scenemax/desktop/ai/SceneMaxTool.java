package com.scenemax.desktop.ai;

import org.json.JSONObject;

public interface SceneMaxTool {
    String getName();
    String getDescription();
    JSONObject getInputSchema();
    SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception;
}
