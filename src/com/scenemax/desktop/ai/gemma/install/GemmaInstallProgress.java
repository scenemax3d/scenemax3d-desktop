package com.scenemax.desktop.ai.gemma.install;

public class GemmaInstallProgress {

    private final int percent;
    private final String message;

    public GemmaInstallProgress(int percent, String message) {
        this.percent = percent;
        this.message = message == null ? "" : message;
    }

    public int getPercent() {
        return percent;
    }

    public String getMessage() {
        return message;
    }
}
