package com.scenemax.desktop.ai.gemma;

import com.scenemax.desktop.AppDB;

public class LocalGemmaBridgeConfig {

    public static final String DEFAULT_ENDPOINT = "http://127.0.0.1:8787/v1/chat/completions";
    public static final String DEFAULT_MODEL = "gemma-4-e4b-it";
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final boolean enabled;
    private final String endpointUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutSeconds;

    public LocalGemmaBridgeConfig(boolean enabled, String endpointUrl, String model, String apiKey, int timeoutSeconds) {
        this.enabled = enabled;
        this.endpointUrl = normalize(endpointUrl, DEFAULT_ENDPOINT);
        this.model = normalize(model, DEFAULT_MODEL);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
    }

    public static LocalGemmaBridgeConfig fromAppSettings() {
        AppDB db = AppDB.getInstance();
        boolean enabled = Boolean.parseBoolean(defaultString(db.getParam("local_gemma_enabled"), "false"));
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        try {
            timeout = Integer.parseInt(defaultString(db.getParam("local_gemma_timeout_seconds"), String.valueOf(DEFAULT_TIMEOUT_SECONDS)).trim());
        } catch (NumberFormatException ignored) {
        }
        return new LocalGemmaBridgeConfig(
                enabled,
                defaultString(db.getParam("local_gemma_endpoint"), DEFAULT_ENDPOINT),
                defaultString(db.getParam("local_gemma_model"), DEFAULT_MODEL),
                defaultString(db.getParam("local_gemma_api_key"), ""),
                timeout
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getModelsUrl() {
        if (endpointUrl.endsWith("/v1/chat/completions")) {
            return endpointUrl.substring(0, endpointUrl.length() - "/chat/completions".length()) + "/models";
        }
        if (endpointUrl.endsWith("/chat/completions")) {
            return endpointUrl.substring(0, endpointUrl.length() - "/chat/completions".length()) + "/models";
        }
        if (endpointUrl.endsWith("/")) {
            return endpointUrl + "v1/models";
        }
        return endpointUrl + "/v1/models";
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
