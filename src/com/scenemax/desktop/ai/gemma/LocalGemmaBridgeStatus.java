package com.scenemax.desktop.ai.gemma;

public class LocalGemmaBridgeStatus {

    private final boolean enabled;
    private final boolean reachable;
    private final String endpointUrl;
    private final String model;
    private final String message;

    public LocalGemmaBridgeStatus(boolean enabled, boolean reachable, String endpointUrl, String model, String message) {
        this.enabled = enabled;
        this.reachable = reachable;
        this.endpointUrl = endpointUrl;
        this.model = model;
        this.message = message == null ? "" : message;
    }

    public static LocalGemmaBridgeStatus disabled(LocalGemmaBridgeConfig config) {
        return new LocalGemmaBridgeStatus(false, false, config.getEndpointUrl(), config.getModel(), "Local Gemma bridge is disabled.");
    }

    public static LocalGemmaBridgeStatus reachable(LocalGemmaBridgeConfig config, String message) {
        return new LocalGemmaBridgeStatus(true, true, config.getEndpointUrl(), config.getModel(), message);
    }

    public static LocalGemmaBridgeStatus unreachable(LocalGemmaBridgeConfig config, String message) {
        return new LocalGemmaBridgeStatus(true, false, config.getEndpointUrl(), config.getModel(), message);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isReachable() {
        return reachable;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getModel() {
        return model;
    }

    public String getMessage() {
        return message;
    }
}
