package com.scenemax.desktop;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class BevyShaderDocument {
    public static final String FILE_EXTENSION = ".bvshader";

    public enum Preset {
        TEXTURE_TINT("Texture + Tint", "TEXTURE_TINT"),
        UI_SOFT_GLOW("UI Soft Glow", "UI_SOFT_GLOW"),
        UI_NEON_SCAN("UI Neon Scan", "UI_NEON_SCAN"),
        GLOW_PULSE("Glow Pulse", "GLOW_PULSE"),
        DISSOLVE("Dissolve", "DISSOLVE"),
        HOLOGRAM_LITE("Hologram Lite", "HOLOGRAM_LITE"),
        WATER_LITE("Water Lite", "WATER_LITE");

        private final String label;
        private final String key;

        Preset(String label, String key) {
            this.label = label;
            this.key = key;
        }

        String key() {
            return key;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private BevyShaderDocument() {
    }

    public static void writeEmptyFile(File file, Preset preset) throws IOException {
        Preset selectedPreset = preset == null ? Preset.TEXTURE_TINT : preset;
        JSONObject root = defaultDocument(selectedPreset);
        FileUtils.writeStringToFile(file, root.toString(2), StandardCharsets.UTF_8);
    }

    private static JSONObject defaultDocument(Preset preset) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("documentType", "bevy_shader");
        root.put("template", preset.key());
        root.put("previewTarget", defaultPreviewTarget(preset));
        root.put("mainColor", array(defaultColor(preset)));
        root.put("glowStrength", defaultGlow(preset));
        root.put("pulseSpeed", 0.55);
        root.put("transparency", defaultTransparency(preset));
        root.put("alphaMode", defaultAlphaMode(preset));
        root.put("alphaCutoff", preset == Preset.DISSOLVE ? 0.35 : 0.5);
        root.put("roughness", defaultRoughness(preset));
        root.put("metallic", preset == Preset.HOLOGRAM_LITE ? 0.15 : 0.0);
        root.put("reflectance", preset == Preset.WATER_LITE ? 0.8 : 0.5);
        root.put("emissiveExposureWeight", 0.0);
        root.put("diffuseTransmission", 0.0);
        root.put("specularTransmission", 0.0);
        root.put("thickness", 0.0);
        root.put("ior", 1.5);
        root.put("attenuationDistance", 20.0);
        root.put("clearcoat", preset == Preset.GLOW_PULSE ? 0.35 : 0.0);
        root.put("clearcoatRoughness", preset == Preset.GLOW_PULSE ? 0.18 : 0.5);
        root.put("anisotropyStrength", 0.0);
        root.put("anisotropyRotation", 0.0);
        root.put("cullMode", preset == Preset.HOLOGRAM_LITE ? "NONE" : "BACK");
        root.put("doubleSided", preset == Preset.HOLOGRAM_LITE);
        root.put("unlit", preset == Preset.UI_NEON_SCAN || preset == Preset.HOLOGRAM_LITE);
        root.put("fogEnabled", true);
        root.put("flipNormalMapY", false);
        root.put("depthBias", 0.0);
        root.put("parallaxDepthScale", 0.1);
        root.put("parallaxLayers", 16.0);
        root.put("parallaxMethod", "OCCLUSION");
        root.put("lightmapExposure", 1.0);
        root.put("uvScale", array(preset == Preset.WATER_LITE ? new double[]{2.0, 2.0} : new double[]{1.0, 1.0}));
        root.put("uvOffset", array(new double[]{0.0, 0.0}));
        root.put("edgeWidth", 0.15);
        root.put("scrollSpeed", 0.35);
        root.put("previewScale", 1.0);
        root.put("texture", "");
        root.put("useOriginalTexture", true);
        root.put("previewModelName", "");
        root.put("blocks", blocks(preset));
        return root;
    }

    private static String defaultPreviewTarget(Preset preset) {
        switch (preset) {
            case UI_SOFT_GLOW:
            case UI_NEON_SCAN:
            case WATER_LITE:
                return "SPRITE";
            case GLOW_PULSE:
            case HOLOGRAM_LITE:
                return "SPHERE";
            default:
                return "BOX";
        }
    }

    private static double[] defaultColor(Preset preset) {
        switch (preset) {
            case UI_SOFT_GLOW:
                return new double[]{0.92, 0.97, 1.0, 1.0};
            case UI_NEON_SCAN:
                return new double[]{0.20, 1.0, 0.86, 1.0};
            case GLOW_PULSE:
                return new double[]{0.36, 0.92, 1.0, 1.0};
            case DISSOLVE:
                return new double[]{1.0, 0.54, 0.20, 1.0};
            case HOLOGRAM_LITE:
                return new double[]{0.32, 0.92, 1.0, 0.95};
            case WATER_LITE:
                return new double[]{0.22, 0.62, 1.0, 0.92};
            default:
                return new double[]{1.0, 0.85, 0.72, 1.0};
        }
    }

    private static double defaultGlow(Preset preset) {
        switch (preset) {
            case UI_SOFT_GLOW:
                return 0.55;
            case UI_NEON_SCAN:
                return 1.15;
            case GLOW_PULSE:
                return 1.25;
            case HOLOGRAM_LITE:
                return 1.4;
            case WATER_LITE:
                return 0.45;
            default:
                return 0.15;
        }
    }

    private static double defaultTransparency(Preset preset) {
        switch (preset) {
            case DISSOLVE:
                return 0.35;
            case HOLOGRAM_LITE:
                return 0.22;
            case WATER_LITE:
                return 0.18;
            default:
                return 0.05;
        }
    }

    private static double defaultRoughness(Preset preset) {
        return preset == Preset.WATER_LITE ? 0.18 : 0.52;
    }

    private static String defaultAlphaMode(Preset preset) {
        switch (preset) {
            case UI_SOFT_GLOW:
                return "PREMULTIPLIED";
            case UI_NEON_SCAN:
            case HOLOGRAM_LITE:
                return "ADD";
            case DISSOLVE:
            case WATER_LITE:
                return "BLEND";
            default:
                return "AUTO";
        }
    }

    private static JSONArray blocks(Preset preset) {
        JSONArray blocks = new JSONArray();
        blocks.put("TINT");
        switch (preset) {
            case UI_SOFT_GLOW:
                blocks.put("GLOW");
                break;
            case UI_NEON_SCAN:
                blocks.put("GLOW");
                blocks.put("PULSE");
                blocks.put("HOLOGRAM_LINES");
                blocks.put("FLICKER");
                break;
            case GLOW_PULSE:
                blocks.put("GLOW");
                blocks.put("PULSE");
                blocks.put("RIM_LIGHT");
                break;
            case DISSOLVE:
                blocks.put("DISSOLVE");
                blocks.put("GLOW");
                break;
            case HOLOGRAM_LITE:
                blocks.put("GLOW");
                blocks.put("PULSE");
                blocks.put("RIM_LIGHT");
                blocks.put("HOLOGRAM_LINES");
                blocks.put("FLICKER");
                break;
            case WATER_LITE:
                blocks.put("SCROLL_UV");
                blocks.put("WATER_WAVES");
                blocks.put("GLOW");
                break;
            default:
                break;
        }
        return blocks;
    }

    private static JSONArray array(double[] values) {
        JSONArray array = new JSONArray();
        for (double value : values) {
            array.put(value);
        }
        return array;
    }
}
