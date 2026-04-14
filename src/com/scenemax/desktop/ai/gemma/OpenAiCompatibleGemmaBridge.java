package com.scenemax.desktop.ai.gemma;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OpenAiCompatibleGemmaBridge implements LocalGemmaBridge {

    private final LocalGemmaBridgeConfig config;
    private final HttpClient httpClient;

    public OpenAiCompatibleGemmaBridge(LocalGemmaBridgeConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, config.getTimeoutSeconds())))
                .build();
    }

    @Override
    public LocalGemmaBridgeConfig getConfig() {
        return config;
    }

    @Override
    public LocalGemmaBridgeStatus checkStatus() {
        if (!config.isEnabled()) {
            return LocalGemmaBridgeStatus.disabled(config);
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getModelsUrl()))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .GET()
                    .header("Accept", "application/json");
            if (!config.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.getApiKey());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return LocalGemmaBridgeStatus.reachable(config, "Connected to local Gemma bridge.");
            }
            return LocalGemmaBridgeStatus.unreachable(config,
                    "HTTP " + response.statusCode() + " from " + config.getModelsUrl());
        } catch (Exception ex) {
            return LocalGemmaBridgeStatus.unreachable(config, ex.getMessage());
        }
    }

    @Override
    public LocalGemmaBridgeResponse generate(LocalGemmaBridgeRequest request) throws Exception {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Local Gemma bridge is disabled.");
        }

        JSONObject payload = new JSONObject();
        payload.put("model", config.getModel());
        payload.put("temperature", request.getTemperature());
        payload.put("messages", toMessages(request));
        if (request.getTools().length() > 0) {
            payload.put("tools", toOpenAiTools(request.getTools()));
            payload.put("tool_choice", "auto");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getEndpointUrl()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        if (!config.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        JSONObject json = new JSONObject(response.body());
        JSONObject message = json.getJSONArray("choices")
                .getJSONObject(0)
                .optJSONObject("message");
        String text = message == null ? "" : message.optString("content", "");

        List<LocalGemmaToolCall> toolCalls = new ArrayList<>();
        if (message != null) {
            JSONArray rawToolCalls = message.optJSONArray("tool_calls");
            if (rawToolCalls != null) {
                for (int i = 0; i < rawToolCalls.length(); i++) {
                    JSONObject rawCall = rawToolCalls.getJSONObject(i);
                    JSONObject function = rawCall.optJSONObject("function");
                    JSONObject arguments = parseArguments(function == null ? null : function.opt("arguments"));
                    toolCalls.add(new LocalGemmaToolCall(
                            rawCall.optString("id", ""),
                            function == null ? "" : function.optString("name", ""),
                            arguments
                    ));
                }
            }
        }

        return new LocalGemmaBridgeResponse(text, toolCalls, json);
    }

    private JSONArray toMessages(LocalGemmaBridgeRequest request) {
        JSONArray messages = new JSONArray();
        if (!request.getSystemPrompt().isBlank()) {
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", request.getSystemPrompt()));
        }
        for (LocalGemmaMessage message : request.getMessages()) {
            messages.put(new JSONObject()
                    .put("role", message.getRole())
                    .put("content", message.getContent()));
        }
        return messages;
    }

    private JSONArray toOpenAiTools(JSONArray sceneMaxTools) {
        JSONArray tools = new JSONArray();
        for (int i = 0; i < sceneMaxTools.length(); i++) {
            JSONObject tool = sceneMaxTools.getJSONObject(i);
            tools.put(new JSONObject()
                    .put("type", "function")
                    .put("function", new JSONObject()
                            .put("name", tool.optString("name"))
                            .put("description", tool.optString("description"))
                            .put("parameters", tool.optJSONObject("inputSchema") == null
                                    ? new JSONObject().put("type", "object")
                                    : tool.getJSONObject("inputSchema"))));
        }
        return tools;
    }

    private JSONObject parseArguments(Object rawArguments) {
        if (rawArguments == null) {
            return new JSONObject();
        }
        if (rawArguments instanceof JSONObject) {
            return (JSONObject) rawArguments;
        }
        String text = String.valueOf(rawArguments).trim();
        if (text.isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(text);
    }
}
