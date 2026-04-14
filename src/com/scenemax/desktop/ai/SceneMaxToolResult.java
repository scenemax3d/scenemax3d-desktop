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

        boolean hasSummary = summary != null && !summary.isBlank();
        boolean hasData = data != null && !data.isEmpty();

        if (hasSummary) {
            JSONObject summaryBlock = new JSONObject();
            summaryBlock.put("type", "text");
            summaryBlock.put("text", summary);
            content.put(summaryBlock);
        }

        if (hasData) {
            JSONObject dataBlock = new JSONObject();
            dataBlock.put("type", "text");
            dataBlock.put("text", data.toString(2));
            content.put(dataBlock);
        }

        if (!hasSummary && !hasData) {
            JSONObject empty = new JSONObject();
            empty.put("type", "text");
            empty.put("text", "");
            content.put(empty);
        }

        result.put("content", content);
        result.put("structuredContent", data);
        if (error) {
            result.put("isError", true);
        }
        return result;
    }
}
