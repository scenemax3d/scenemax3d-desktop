package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.mcp.SceneMaxMcpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SceneMaxMcpServerTest {

    @Test
    public void toolsListAndCallWorkOverJsonRpcShape() throws Exception {
        Path workspace = Files.createTempDirectory("scenemax-mcp");
        try {
            SceneMaxToolRegistry registry = SceneMaxAutomationBootstrap.createDefaultRegistry();
            SceneMaxToolContext context = new SceneMaxToolContext(null, workspace);
            SceneMaxMcpServer server = new SceneMaxMcpServer(registry, context);

            JSONObject init = server.handleRequest(new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "initialize"));
            assertEquals("2025-03-26", init.getJSONObject("result").getString("protocolVersion"));

            JSONObject toolsList = server.handleRequest(new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 2)
                    .put("method", "tools/list"));
            JSONArray tools = toolsList.getJSONObject("result").getJSONArray("tools");
            assertTrue(tools.length() >= 5);

            JSONObject createCall = server.handleRequest(new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 3)
                    .put("method", "tools/call")
                    .put("params", new JSONObject()
                            .put("name", "file.create")
                            .put("arguments", new JSONObject()
                                    .put("path", "demo/main.code")
                                    .put("base", "workspace")
                                    .put("content", "print \"ok\"")
                                    .put("openInEditor", false))));

            JSONObject result = createCall.getJSONObject("result");
            assertEquals(false, result.optBoolean("isError", false));
            assertTrue(Files.exists(workspace.resolve("demo/main.code")));
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    public void toolsListIncludesDesignerInputAndOutputSchemas() throws Exception {
        Path workspace = Files.createTempDirectory("scenemax-mcp");
        try {
            SceneMaxToolRegistry registry = SceneMaxAutomationBootstrap.createDefaultRegistry();
            SceneMaxToolContext context = new SceneMaxToolContext(null, workspace);
            SceneMaxMcpServer server = new SceneMaxMcpServer(registry, context);

            JSONObject toolsList = server.handleRequest(new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 2)
                    .put("method", "tools/list"));
            JSONArray tools = toolsList.getJSONObject("result").getJSONArray("tools");

            JSONObject adjustView = findTool(tools, "designer.adjust_view");
            JSONObject adjustInput = adjustView.getJSONObject("inputSchema");
            JSONObject adjustOutput = adjustView.getJSONObject("outputSchema");
            assertTrue(adjustInput.optString("description").contains("viewport"));
            assertTrue(adjustInput.getJSONObject("properties")
                    .getJSONObject("action")
                    .getJSONArray("enum")
                    .toList()
                    .contains("frame_all"));
            assertTrue(adjustOutput.has("oneOf"));

            JSONObject compare = findTool(tools, "designer.compare_to_reference");
            JSONObject compareInput = compare.getJSONObject("inputSchema");
            JSONObject compareOutput = compare.getJSONObject("outputSchema");
            assertTrue(compareInput.getJSONObject("properties").has("reference_image"));
            assertTrue(compareInput.getJSONObject("properties")
                    .getJSONObject("reference_image")
                    .optString("description")
                    .contains("Alias"));
            assertTrue(compareOutput.getJSONObject("properties").has("metrics"));

            JSONObject reliableCapture = findTool(tools, "designer.capture_canvas_reliable");
            JSONObject captureOutput = reliableCapture.getJSONObject("outputSchema");
            assertTrue(captureOutput.getJSONObject("properties").has("captureMode"));
            assertTrue(captureOutput.getJSONObject("properties").has("clean"));

            JSONObject overlay = findTool(tools, "designer.overlay_reference_image");
            JSONObject overlayOutput = overlay.getJSONObject("outputSchema");
            assertTrue(overlayOutput.getJSONObject("properties").has("resolvedImagePath"));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private JSONObject findTool(JSONArray tools, String name) {
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            if (name.equals(tool.optString("name"))) {
                assertNotNull(tool.optJSONObject("inputSchema"));
                assertNotNull(tool.optJSONObject("outputSchema"));
                assertFalse(tool.optJSONObject("inputSchema").isEmpty());
                return tool;
            }
        }
        throw new AssertionError("Tool not found: " + name);
    }

    private void deleteRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(it -> {
                    try {
                        Files.deleteIfExists(it);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }
}
