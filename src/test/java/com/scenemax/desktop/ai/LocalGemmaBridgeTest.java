package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.gemma.LocalGemmaBridgeConfig;
import com.scenemax.desktop.ai.gemma.LocalGemmaBridgeRequest;
import com.scenemax.desktop.ai.gemma.LocalGemmaBridgeResponse;
import com.scenemax.desktop.ai.gemma.LocalGemmaBridgeStatus;
import com.scenemax.desktop.ai.gemma.LocalGemmaMessage;
import com.scenemax.desktop.ai.gemma.OpenAiCompatibleGemmaBridge;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalGemmaBridgeTest {

    @Test
    public void openAiCompatibleBridgeCanPingAndParseToolCalls() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", new JsonHandler(new JSONObject().put("data", new JSONArray())));
        server.createContext("/v1/chat/completions", new JsonHandler(new JSONObject()
                .put("choices", new JSONArray().put(new JSONObject()
                        .put("message", new JSONObject()
                                .put("content", "I can help with that.")
                                .put("tool_calls", new JSONArray().put(new JSONObject()
                                        .put("id", "call_1")
                                        .put("function", new JSONObject()
                                                .put("name", "project.list_tree")
                                                .put("arguments", "{\"base\":\"project\"}")))))))));
        server.start();
        try {
            int port = server.getAddress().getPort();
            OpenAiCompatibleGemmaBridge bridge = new OpenAiCompatibleGemmaBridge(
                    new LocalGemmaBridgeConfig(true,
                            "http://127.0.0.1:" + port + "/v1/chat/completions",
                            "gemma-test",
                            "",
                            10));

            LocalGemmaBridgeStatus status = bridge.checkStatus();
            assertTrue(status.isReachable());

            LocalGemmaBridgeResponse response = bridge.generate(new LocalGemmaBridgeRequest(
                    "You are SceneMax AI.",
                    List.of(new LocalGemmaMessage("user", "List the project tree.")),
                    new JSONArray().put(new JSONObject()
                            .put("name", "project.list_tree")
                            .put("description", "List project tree.")
                            .put("inputSchema", new JSONObject().put("type", "object"))),
                    0.2
            ));

            assertEquals("I can help with that.", response.getText());
            assertEquals(1, response.getToolCalls().size());
            assertEquals("project.list_tree", response.getToolCalls().get(0).getName());
            assertEquals("project", response.getToolCalls().get(0).getArguments().getString("base"));
        } finally {
            server.stop(0);
        }
    }

    private static class JsonHandler implements HttpHandler {
        private final JSONObject response;

        private JsonHandler(JSONObject response) {
            this.response = response;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
