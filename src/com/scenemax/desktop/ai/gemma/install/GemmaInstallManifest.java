package com.scenemax.desktop.ai.gemma.install;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class GemmaInstallManifest {

    private final String installedAtUtc;
    private final String modelId;
    private final String modelDisplayName;
    private final String modelPath;
    private final String runtimePath;
    private final String runtimeDownloadUrl;
    private final String modelDownloadUrl;

    public GemmaInstallManifest(String installedAtUtc, String modelId, String modelDisplayName, String modelPath,
                                String runtimePath, String runtimeDownloadUrl, String modelDownloadUrl) {
        this.installedAtUtc = installedAtUtc;
        this.modelId = modelId;
        this.modelDisplayName = modelDisplayName;
        this.modelPath = modelPath;
        this.runtimePath = runtimePath;
        this.runtimeDownloadUrl = runtimeDownloadUrl;
        this.modelDownloadUrl = modelDownloadUrl;
    }

    public static GemmaInstallManifest now(GemmaModelVariant variant, Path modelPath, Path runtimePath, String runtimeDownloadUrl) {
        return new GemmaInstallManifest(
                Instant.now().toString(),
                variant.getModelId(),
                variant.getDisplayName(),
                modelPath.toAbsolutePath().normalize().toString(),
                runtimePath.toAbsolutePath().normalize().toString(),
                runtimeDownloadUrl,
                variant.getDownloadUrl()
        );
    }

    public static GemmaInstallManifest load(Path manifestFile) throws IOException {
        if (manifestFile == null || !Files.exists(manifestFile)) {
            return null;
        }
        JSONObject json = new JSONObject(Files.readString(manifestFile, StandardCharsets.UTF_8));
        return new GemmaInstallManifest(
                json.optString("installedAtUtc"),
                json.optString("modelId"),
                json.optString("modelDisplayName"),
                json.optString("modelPath"),
                json.optString("runtimePath"),
                json.optString("runtimeDownloadUrl"),
                json.optString("modelDownloadUrl")
        );
    }

    public void save(Path manifestFile) throws IOException {
        Files.createDirectories(manifestFile.getParent());
        Files.writeString(manifestFile, toJson().toString(2), StandardCharsets.UTF_8);
    }

    public JSONObject toJson() {
        return new JSONObject()
                .put("installedAtUtc", installedAtUtc)
                .put("modelId", modelId)
                .put("modelDisplayName", modelDisplayName)
                .put("modelPath", modelPath)
                .put("runtimePath", runtimePath)
                .put("runtimeDownloadUrl", runtimeDownloadUrl)
                .put("modelDownloadUrl", modelDownloadUrl);
    }

    public String getInstalledAtUtc() {
        return installedAtUtc;
    }

    public String getModelId() {
        return modelId;
    }

    public String getModelDisplayName() {
        return modelDisplayName;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getRuntimePath() {
        return runtimePath;
    }
}
