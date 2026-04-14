package com.scenemax.desktop.ai.gemma;

public class LocalGemmaMessage {

    private final String role;
    private final String content;

    public LocalGemmaMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
