package com.scenemax.desktop.ai;

import org.json.JSONArray;
import org.json.JSONObject;

public class SceneMaxToolResult {

    private final boolean error;
    private final String summary;
    private final JSONObject data;

    private SceneMaxToolResult(boolean error, String summary, JSONObject data) {
        this.error = error;
        this.summary = summary;
        this.data = data == null ? new JSONObject() : data;
    }

    public static SceneMaxToolResult success(String summary, JSONObject data) {
        return new SceneMaxToolResult(false, summary, data);
    }

    public static SceneMaxToolResult error(String summary, JSONObject data) {
        return new SceneMaxToolResult(true, summary, data);
    }

    public boolean isError() {
        return error;
    }

    public JSONObject getData() {
        return data;
    }

    public JSONObject toMcpResult() {
        JSONObject result = new JSONObject();
        JSONArray content = new JSONArray();
        JSONObject text = new JSONObject();
        text.put("type", "text");
        text.put("text", summary != null && !summary.isBlank() ? summary : data.toString(2));
        content.put(text);
        result.put("content", content);
        result.put("structuredData", data);
        if (error) {
            result.put("isError", true);
        }
        return result;
    }
}
