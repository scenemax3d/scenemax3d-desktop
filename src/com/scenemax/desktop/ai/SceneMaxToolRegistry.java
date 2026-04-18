package com.scenemax.desktop.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SceneMaxToolRegistry {

    private final Map<String, SceneMaxTool> tools = new LinkedHashMap<>();

    public void register(SceneMaxTool tool) {
        tools.put(tool.getName(), tool);
    }

    public SceneMaxTool get(String name) {
        return tools.get(name);
    }

    public Collection<SceneMaxTool> getTools() {
        return tools.values();
    }

    public JSONArray describeTools() {
        JSONArray result = new JSONArray();
        for (SceneMaxTool tool : tools.values()) {
            JSONObject item = new JSONObject();
            item.put("name", tool.getName());
            item.put("description", tool.getDescription());
            item.put("inputSchema", tool.getInputSchema());
            item.put("outputSchema", tool.getOutputSchema());
            result.put(item);
        }
        return result;
    }

    public SceneMaxToolResult call(String name, SceneMaxToolContext context, JSONObject arguments) {
        SceneMaxTool tool = tools.get(name);
        if (tool == null) {
            JSONObject data = new JSONObject();
            data.put("toolName", name);
            return SceneMaxToolResult.error("Unknown tool: " + name, data);
        }
        try {
            return tool.execute(context, arguments == null ? new JSONObject() : arguments);
        } catch (Exception ex) {
            JSONObject data = new JSONObject();
            data.put("toolName", name);
            data.put("message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            return SceneMaxToolResult.error("Tool failed: " + data.getString("message"), data);
        }
    }
}
