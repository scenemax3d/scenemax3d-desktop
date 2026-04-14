package com.scenemax.desktop.ai.gemma;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalGemmaBridgeResponse {

    private final String text;
    private final List<LocalGemmaToolCall> toolCalls;
    private final JSONObject rawResponse;

    public LocalGemmaBridgeResponse(String text, List<LocalGemmaToolCall> toolCalls, JSONObject rawResponse) {
        this.text = text == null ? "" : text;
        this.toolCalls = toolCalls == null ? Collections.emptyList() : new ArrayList<>(toolCalls);
        this.rawResponse = rawResponse == null ? new JSONObject() : rawResponse;
    }

    public String getText() {
        return text;
    }

    public List<LocalGemmaToolCall> getToolCalls() {
        return toolCalls;
    }

    public JSONObject getRawResponse() {
        return rawResponse;
    }
}
