package com.scenemax.designer.shader;

import java.util.EnumSet;

public enum EnvironmentShaderTemplatePreset {
    FOG("Fog"),
    RAIN("Rain"),
    FOG_AND_RAIN("Fog + Rain"),
    SNOWY_BREEZE("Snowy Breeze"),
    CINEMATIC_DAYLIGHT("Cinematic Daylight");

    private final String displayName;

    EnvironmentShaderTemplatePreset(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static EnvironmentShaderTemplatePreset fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return FOG;
        }
        for (EnvironmentShaderTemplatePreset preset : values()) {
            if (preset.name().equalsIgnoreCase(raw) || preset.displayName.equalsIgnoreCase(raw)) {
                return preset;
            }
        }
        return FOG;
    }

    public void applyTo(EnvironmentShaderDocument doc) {
        if (doc == null) {
            return;
        }
        doc.setTemplate(this);
        doc.getLayers().clear();
        switch (this) {
            case FOG:
                doc.getLayers().add(EnvironmentShaderLayerType.FOG);
                break;
            case RAIN:
                doc.getLayers().add(EnvironmentShaderLayerType.RAIN);
                break;
            case FOG_AND_RAIN:
                doc.getLayers().addAll(EnumSet.of(
                        EnvironmentShaderLayerType.FOG,
                        EnvironmentShaderLayerType.RAIN
                ));
                break;
            case SNOWY_BREEZE:
                doc.getLayers().addAll(EnumSet.of(
                        EnvironmentShaderLayerType.FOG,
                        EnvironmentShaderLayerType.SNOW,
                        EnvironmentShaderLayerType.WIND,
                        EnvironmentShaderLayerType.SKY_TWEAKS,
                        EnvironmentShaderLayerType.AMBIENT_COLOR
                ));
                break;
            case CINEMATIC_DAYLIGHT:
                doc.getLayers().addAll(EnumSet.of(
                        EnvironmentShaderLayerType.SKY_TWEAKS,
                        EnvironmentShaderLayerType.AMBIENT_COLOR,
                        EnvironmentShaderLayerType.LIGHTING,
                        EnvironmentShaderLayerType.WIND
                ));
                break;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
