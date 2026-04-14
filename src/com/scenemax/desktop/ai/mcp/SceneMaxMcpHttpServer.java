package com.scenemax.desktop.ai.mcp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SceneMaxMcpHttpServer {

    private static final int DEFAULT_PORT = 8765;
    private static final int MAX_PORT_ATTEMPTS = 10;

    private final SceneMaxMcpServer mcpServer;
    private final Consumer<SceneMaxMcpLogEntry> logConsumer;
    private HttpServer server;
    private ExecutorService executor;
    private int port = -1;
    private String lastError;

    public SceneMaxMcpHttpServer(SceneMaxMcpServer mcpServer, Consumer<SceneMaxMcpLogEntry> logConsumer) {
        this.mcpServer = mcpServer;
        this.logConsumer = logConsumer;
    }

    public synchronized boolean start() {
        return start(0);
    }

    public synchronized boolean start(int preferredPort) {
        if (server != null) {
            return true;
        }

        if (preferredPort > 0) {
            return tryStartOnPort(preferredPort);
        }

        for (int candidate = DEFAULT_PORT; candidate < DEFAULT_PORT + MAX_PORT_ATTEMPTS; candidate++) {
            if (tryStartOnPort(candidate)) {
                return true;
            }
        }

        return false;
    }

    private boolean tryStartOnPort(int candidate) {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), candidate), 0);
            httpServer.createContext("/mcp", new MpcHandler());
            httpServer.createContext("/mcp/status", new StatusHandler());
            executor = Executors.newCachedThreadPool();
            httpServer.setExecutor(executor);
            httpServer.start();
            server = httpServer;
            port = candidate;
            lastError = null;
            return true;
        } catch (IOException ex) {
            lastError = ex.getMessage();
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            return false;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        port = -1;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized int getPort() {
        return port;
    }

    public synchronized String getEndpointUrl() {
        return isRunning() ? "http://127.0.0.1:" + port + "/mcp" : null;
    }

    public synchronized String getStatusUrl() {
        return isRunning() ? "http://127.0.0.1:" + port + "/mcp/status" : null;
    }

    public synchronized String getLastError() {
        return lastError;
    }

    private class MpcHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange.getResponseHeaders());

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JSONObject body = new JSONObject();
                body.put("name", "SceneMax Automation");
                body.put("status", "ready");
                body.put("endpoint", getEndpointUrl());
                body.put("statusEndpoint", getStatusUrl());
                log(exchange, 200, "", "", "", "endpoint info");
                writeJson(exchange, 200, body);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                log(exchange, 405, "", "", "", "unsupported method");
                writeJson(exchange, 405, new JSONObject().put("error", "Only GET, POST, and OPTIONS are supported."));
                return;
            }

            try (InputStream in = exchange.getRequestBody()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject request = body == null || body.isBlank() ? new JSONObject() : new JSONObject(body);
                JSONObject response = mcpServer.handleRequest(request);
                if (response == null) {
                    log(exchange, 202,
                            request.optString("method", ""),
                            request.optJSONObject("params") != null ? request.optJSONObject("params").optString("name", "") : "",
                            request.has("id") ? String.valueOf(request.get("id")) : "",
                            "notification");
                    exchange.sendResponseHeaders(202, -1);
                    return;
                }
                log(exchange, 200,
                        request.optString("method", ""),
                        request.optJSONObject("params") != null ? request.optJSONObject("params").optString("name", "") : "",
                        request.has("id") ? String.valueOf(request.get("id")) : "",
                        summarizeResponse(response));
                writeJson(exchange, 200, response);
            } catch (Exception ex) {
                log(exchange, 500, "", "", "", ex.getMessage());
                writeJson(exchange, 500, new JSONObject()
                        .put("jsonrpc", "2.0")
                        .put("error", new JSONObject()
                                .put("code", -32603)
                                .put("message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
            }
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange.getResponseHeaders());
            JSONObject body = new JSONObject();
            body.put("running", isRunning());
            body.put("endpoint", getEndpointUrl());
            body.put("statusEndpoint", getStatusUrl());
            body.put("protocol", "MCP JSON-RPC over local HTTP");
            log(exchange, 200, "status", "", "", "status probe");
            writeJson(exchange, 200, body);
        }
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void writeJson(HttpExchange exchange, int statusCode, JSONObject body) throws IOException {
        byte[] bytes = body.toString(2).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String summarizeResponse(JSONObject response) {
        if (response == null) {
            return "";
        }
        if (response.has("error")) {
            return response.getJSONObject("error").optString("message", "error");
        }
        JSONObject result = response.optJSONObject("result");
        if (result != null && result.has("structuredData")) {
            JSONObject data = result.optJSONObject("structuredData");
            if (data != null) {
                if (data.has("message")) {
                    return data.optString("message", "");
                }
                if (data.has("path")) {
                    return data.optString("path", "");
                }
            }
        }
        return "ok";
    }

    private void log(HttpExchange exchange, int statusCode, String rpcMethod, String toolName,
                     String requestId, String summary) {
        if (logConsumer == null) {
            return;
        }
        String remoteAddress = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().toString() : "";
        logConsumer.accept(new SceneMaxMcpLogEntry(
                LocalDateTime.now(),
                exchange.getRequestMethod(),
                exchange.getRequestURI() != null ? exchange.getRequestURI().getPath() : "",
                statusCode,
                rpcMethod,
                toolName,
                requestId,
                summary,
                remoteAddress
        ));
    }
}
