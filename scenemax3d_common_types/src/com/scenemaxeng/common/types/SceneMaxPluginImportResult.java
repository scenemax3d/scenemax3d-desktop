package com.scenemaxeng.common.types;

public class SceneMaxPluginImportResult {
    private final String assetName;
    private final String assetPath;
    private final String absolutePath;

    public SceneMaxPluginImportResult(String assetName, String assetPath, String absolutePath) {
        this.assetName = assetName;
        this.assetPath = assetPath;
        this.absolutePath = absolutePath;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getAssetPath() {
        return assetPath;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }
}
