package com.scenemax.designer.shader;

public enum ShaderBlockType {
    TINT("Tint"),
    GLOW("Glow"),
    PULSE("Pulse"),
    DISSOLVE("Dissolve"),
    RIM_LIGHT("Rim Light"),
    SCROLL_UV("Scroll UV"),
    FLICKER("Flicker"),
    WATER_WAVES("Water Waves"),
    HOLOGRAM_LINES("Hologram Lines"),
    TOON_RAMP("Toon Ramp");

    private final String displayName;

    ShaderBlockType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
