package com.scenemax.desktop.ai.gemma;

import org.json.JSONObject;

public class LocalGemmaToolCall {

    private final String id;
    private final String name;
    private final JSONObject arguments;

    public LocalGemmaToolCall(String id, String name, JSONObject arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments == null ? new JSONObject() : arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JSONObject getArguments() {
        return arguments;
    }
}
