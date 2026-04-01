package com.scenemax.designer.shader;

import com.jme3.math.ColorRGBA;

import java.util.EnumSet;

public enum ShaderTemplatePreset {
    TEXTURE_TINT("Texture + Tint"),
    GLOW_PULSE("Glow Pulse"),
    DISSOLVE("Dissolve"),
    HOLOGRAM_LITE("Hologram Lite"),
    WATER_LITE("Water Lite");

    private final String displayName;

    ShaderTemplatePreset(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static ShaderTemplatePreset fromName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return TEXTURE_TINT;
        }
        for (ShaderTemplatePreset preset : values()) {
            if (preset.name().equalsIgnoreCase(raw) || preset.displayName.equalsIgnoreCase(raw)) {
                return preset;
            }
        }
        return TEXTURE_TINT;
    }

    public void applyTo(ShaderDocument doc) {
        doc.setTemplate(this);
        doc.getBlocks().clear();

        switch (this) {
            case TEXTURE_TINT:
                doc.getBlocks().addAll(EnumSet.of(ShaderBlockType.TINT));
                doc.setMainColor(new ColorRGBA(1f, 0.85f, 0.72f, 1f));
                doc.setGlowStrength(0.15f);
                doc.setPulseSpeed(0.55f);
                doc.setTransparency(0.05f);
                doc.setEdgeWidth(0.15f);
                doc.setScrollSpeed(0.35f);
                doc.setPreviewTarget(ShaderPreviewTarget.BOX);
                break;
            case GLOW_PULSE:
                doc.getBlocks().addAll(EnumSet.of(
                        ShaderBlockType.TINT,
                        ShaderBlockType.GLOW,
                        ShaderBlockType.PULSE,
                        ShaderBlockType.RIM_LIGHT
                ));
                doc.setMainColor(new ColorRGBA(0.36f, 0.92f, 1f, 1f));
                doc.setGlowStrength(1.25f);
                doc.setPulseSpeed(1.05f);
                doc.setTransparency(0.08f);
                doc.setEdgeWidth(0.12f);
                doc.setScrollSpeed(0.25f);
                doc.setPreviewTarget(ShaderPreviewTarget.SPHERE);
                break;
            case DISSOLVE:
                doc.getBlocks().addAll(EnumSet.of(
                        ShaderBlockType.TINT,
                        ShaderBlockType.DISSOLVE,
                        ShaderBlockType.GLOW
                ));
                doc.setMainColor(new ColorRGBA(1f, 0.54f, 0.20f, 1f));
                doc.setGlowStrength(0.75f);
                doc.setPulseSpeed(0.60f);
                doc.setTransparency(0.35f);
                doc.setEdgeWidth(0.22f);
                doc.setScrollSpeed(0.40f);
                doc.setPreviewTarget(ShaderPreviewTarget.BOX);
                break;
            case HOLOGRAM_LITE:
                doc.getBlocks().addAll(EnumSet.of(
                        ShaderBlockType.TINT,
                        ShaderBlockType.GLOW,
                        ShaderBlockType.PULSE,
                        ShaderBlockType.RIM_LIGHT,
                        ShaderBlockType.HOLOGRAM_LINES,
                        ShaderBlockType.FLICKER
                ));
                doc.setMainColor(new ColorRGBA(0.32f, 0.92f, 1f, 0.95f));
                doc.setGlowStrength(1.4f);
                doc.setPulseSpeed(1.15f);
                doc.setTransparency(0.22f);
                doc.setEdgeWidth(0.16f);
                doc.setScrollSpeed(0.75f);
                doc.setPreviewTarget(ShaderPreviewTarget.SPHERE);
                break;
            case WATER_LITE:
                doc.getBlocks().addAll(EnumSet.of(
                        ShaderBlockType.TINT,
                        ShaderBlockType.SCROLL_UV,
                        ShaderBlockType.WATER_WAVES,
                        ShaderBlockType.GLOW
                ));
                doc.setMainColor(new ColorRGBA(0.22f, 0.62f, 1f, 0.92f));
                doc.setGlowStrength(0.45f);
                doc.setPulseSpeed(0.35f);
                doc.setTransparency(0.18f);
                doc.setEdgeWidth(0.08f);
                doc.setScrollSpeed(0.95f);
                doc.setPreviewTarget(ShaderPreviewTarget.SPRITE);
                break;
        }
    }
}
