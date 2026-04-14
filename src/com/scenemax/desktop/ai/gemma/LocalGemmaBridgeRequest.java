package com.scenemax.desktop.ai.gemma;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalGemmaBridgeRequest {

    private final String systemPrompt;
    private final List<LocalGemmaMessage> messages;
    private final JSONArray tools;
    private final double temperature;

    public LocalGemmaBridgeRequest(String systemPrompt, List<LocalGemmaMessage> messages, JSONArray tools, double temperature) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.messages = messages == null ? Collections.emptyList() : new ArrayList<>(messages);
        this.tools = tools == null ? new JSONArray() : tools;
        this.temperature = temperature;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<LocalGemmaMessage> getMessages() {
        return messages;
    }

    public JSONArray getTools() {
        return tools;
    }

    public double getTemperature() {
        return temperature;
    }
}
