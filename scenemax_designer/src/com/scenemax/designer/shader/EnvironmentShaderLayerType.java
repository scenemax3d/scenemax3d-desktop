package com.scenemax.designer.shader;

public enum EnvironmentShaderLayerType {
    FOG("Fog"),
    RAIN("Rain"),
    SNOW("Snow"),
    WIND("Wind"),
    SKY_TWEAKS("Sky Tweaks"),
    AMBIENT_COLOR("Ambient Color"),
    LIGHTING("Lighting");

    private final String displayName;

    EnvironmentShaderLayerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
