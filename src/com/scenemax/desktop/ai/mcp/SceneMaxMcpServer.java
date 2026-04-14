package com.scenemax.desktop.ai.mcp;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolRegistry;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class SceneMaxMcpServer {

    private final SceneMaxToolRegistry toolRegistry;
    private final SceneMaxToolContext toolContext;

    public SceneMaxMcpServer(SceneMaxToolRegistry toolRegistry, SceneMaxToolContext toolContext) {
        this.toolRegistry = toolRegistry;
        this.toolContext = toolContext;
    }

    public JSONObject handleRequest(JSONObject request) {
        String method = request.optString("method", "");
        Object id = request.has("id") ? request.get("id") : JSONObject.NULL;

        switch (method) {
            case "initialize":
                return response(id, new JSONObject()
                        .put("protocolVersion", "2025-03-26")
                        .put("serverInfo", new JSONObject()
                                .put("name", "SceneMax Automation")
                                .put("version", "0.1.0"))
                        .put("capabilities", new JSONObject()
                                .put("tools", new JSONObject().put("listChanged", false))));
            case "tools/list":
                return response(id, new JSONObject().put("tools", toolRegistry.describeTools()));
            case "tools/call":
                return handleToolCall(id, request.optJSONObject("params"));
            default:
                return error(id, -32601, "Method not found: " + method);
        }
    }

    private JSONObject handleToolCall(Object id, JSONObject params) {
        if (params == null) {
            return error(id, -32602, "Missing params");
        }

        String name = params.optString("name", "");
        JSONObject arguments = params.optJSONObject("arguments");
        SceneMaxToolResult result = toolRegistry.call(name, toolContext, arguments);
        return response(id, result.toMcpResult());
    }

    private JSONObject response(Object id, JSONObject result) {
        return new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("result", result);
    }

    private JSONObject error(Object id, int code, String message) {
        return new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("error", new JSONObject()
                        .put("code", code)
                        .put("message", message));
    }
}
