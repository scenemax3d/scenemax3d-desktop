package com.scenemax.designer.shader;

import com.jme3.math.ColorRGBA;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Locale;

public class EnvironmentShaderDocument {

    private static final int FORMAT_VERSION = 1;

    private final String filePath;
    private EnvironmentShaderTemplatePreset template = EnvironmentShaderTemplatePreset.FOG;
    private final EnumSet<EnvironmentShaderLayerType> layers = EnumSet.of(EnvironmentShaderLayerType.FOG);

    private ColorRGBA fogColor = new ColorRGBA(0.72f, 0.80f, 0.90f, 1f);
    private float fogDensity = 0.35f;
    private float fogNearDistance = 12f;
    private float fogFarDistance = 80f;

    private ColorRGBA rainColor = new ColorRGBA(0.82f, 0.90f, 1f, 1f);
    private float rainIntensity = 0.55f;
    private float rainSpeed = 1.2f;
    private float rainAngle = -18f;
    private float overlayOpacity = 0.32f;

    private ColorRGBA snowColor = new ColorRGBA(0.95f, 0.97f, 1f, 1f);
    private float snowIntensity = 0.45f;
    private float snowSpeed = 0.65f;
    private float snowFlakeSize = 0.55f;

    private float windDirection = 24f;
    private float windStrength = 0.45f;
    private float windGustiness = 0.35f;

    private ColorRGBA skyTint = new ColorRGBA(0.58f, 0.74f, 0.96f, 1f);
    private float skyBrightness = 1.0f;
    private float skyHorizonBlend = 0.45f;

    private ColorRGBA ambientColor = new ColorRGBA(0.58f, 0.62f, 0.68f, 1f);
    private float ambientIntensity = 0.65f;

    private ColorRGBA lightColor = new ColorRGBA(1f, 0.96f, 0.88f, 1f);
    private float lightIntensity = 1.1f;
    private float lightPitch = -38f;
    private float lightYaw = -32f;

    public EnvironmentShaderDocument(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public EnvironmentShaderTemplatePreset getTemplate() {
        return template;
    }

    public void setTemplate(EnvironmentShaderTemplatePreset template) {
        this.template = template != null ? template : EnvironmentShaderTemplatePreset.FOG;
    }

    public EnumSet<EnvironmentShaderLayerType> getLayers() {
        return layers;
    }

    public ColorRGBA getFogColor() {
        return fogColor.clone();
    }

    public void setFogColor(ColorRGBA fogColor) {
        this.fogColor = fogColor != null ? fogColor.clone() : new ColorRGBA(0.72f, 0.80f, 0.90f, 1f);
    }

    public float getFogDensity() {
        return fogDensity;
    }

    public void setFogDensity(float fogDensity) {
        this.fogDensity = fogDensity;
    }

    public float getFogNearDistance() {
        return fogNearDistance;
    }

    public void setFogNearDistance(float fogNearDistance) {
        this.fogNearDistance = fogNearDistance;
    }

    public float getFogFarDistance() {
        return fogFarDistance;
    }

    public void setFogFarDistance(float fogFarDistance) {
        this.fogFarDistance = fogFarDistance;
    }

    public ColorRGBA getRainColor() {
        return rainColor.clone();
    }

    public void setRainColor(ColorRGBA rainColor) {
        this.rainColor = rainColor != null ? rainColor.clone() : new ColorRGBA(0.82f, 0.90f, 1f, 1f);
    }

    public float getRainIntensity() {
        return rainIntensity;
    }

    public void setRainIntensity(float rainIntensity) {
        this.rainIntensity = rainIntensity;
    }

    public float getRainSpeed() {
        return rainSpeed;
    }

    public void setRainSpeed(float rainSpeed) {
        this.rainSpeed = rainSpeed;
    }

    public float getRainAngle() {
        return rainAngle;
    }

    public void setRainAngle(float rainAngle) {
        this.rainAngle = rainAngle;
    }

    public float getOverlayOpacity() {
        return overlayOpacity;
    }

    public void setOverlayOpacity(float overlayOpacity) {
        this.overlayOpacity = overlayOpacity;
    }

    public ColorRGBA getSnowColor() { return snowColor.clone(); }
    public void setSnowColor(ColorRGBA snowColor) {
        this.snowColor = snowColor != null ? snowColor.clone() : new ColorRGBA(0.95f, 0.97f, 1f, 1f);
    }
    public float getSnowIntensity() { return snowIntensity; }
    public void setSnowIntensity(float snowIntensity) { this.snowIntensity = snowIntensity; }
    public float getSnowSpeed() { return snowSpeed; }
    public void setSnowSpeed(float snowSpeed) { this.snowSpeed = snowSpeed; }
    public float getSnowFlakeSize() { return snowFlakeSize; }
    public void setSnowFlakeSize(float snowFlakeSize) { this.snowFlakeSize = snowFlakeSize; }

    public float getWindDirection() { return windDirection; }
    public void setWindDirection(float windDirection) { this.windDirection = windDirection; }
    public float getWindStrength() { return windStrength; }
    public void setWindStrength(float windStrength) { this.windStrength = windStrength; }
    public float getWindGustiness() { return windGustiness; }
    public void setWindGustiness(float windGustiness) { this.windGustiness = windGustiness; }

    public ColorRGBA getSkyTint() { return skyTint.clone(); }
    public void setSkyTint(ColorRGBA skyTint) {
        this.skyTint = skyTint != null ? skyTint.clone() : new ColorRGBA(0.58f, 0.74f, 0.96f, 1f);
    }
    public float getSkyBrightness() { return skyBrightness; }
    public void setSkyBrightness(float skyBrightness) { this.skyBrightness = skyBrightness; }
    public float getSkyHorizonBlend() { return skyHorizonBlend; }
    public void setSkyHorizonBlend(float skyHorizonBlend) { this.skyHorizonBlend = skyHorizonBlend; }

    public ColorRGBA getAmbientColor() { return ambientColor.clone(); }
    public void setAmbientColor(ColorRGBA ambientColor) {
        this.ambientColor = ambientColor != null ? ambientColor.clone() : new ColorRGBA(0.58f, 0.62f, 0.68f, 1f);
    }
    public float getAmbientIntensity() { return ambientIntensity; }
    public void setAmbientIntensity(float ambientIntensity) { this.ambientIntensity = ambientIntensity; }

    public ColorRGBA getLightColor() { return lightColor.clone(); }
    public void setLightColor(ColorRGBA lightColor) {
        this.lightColor = lightColor != null ? lightColor.clone() : new ColorRGBA(1f, 0.96f, 0.88f, 1f);
    }
    public float getLightIntensity() { return lightIntensity; }
    public void setLightIntensity(float lightIntensity) { this.lightIntensity = lightIntensity; }
    public float getLightPitch() { return lightPitch; }
    public void setLightPitch(float lightPitch) { this.lightPitch = lightPitch; }
    public float getLightYaw() { return lightYaw; }
    public void setLightYaw(float lightYaw) { this.lightYaw = lightYaw; }

    public EnvironmentShaderDocument copy() {
        EnvironmentShaderDocument copy = new EnvironmentShaderDocument(filePath);
        copy.template = template;
        copy.layers.addAll(layers);
        copy.fogColor = fogColor.clone();
        copy.fogDensity = fogDensity;
        copy.fogNearDistance = fogNearDistance;
        copy.fogFarDistance = fogFarDistance;
        copy.rainColor = rainColor.clone();
        copy.rainIntensity = rainIntensity;
        copy.rainSpeed = rainSpeed;
        copy.rainAngle = rainAngle;
        copy.overlayOpacity = overlayOpacity;
        copy.snowColor = snowColor.clone();
        copy.snowIntensity = snowIntensity;
        copy.snowSpeed = snowSpeed;
        copy.snowFlakeSize = snowFlakeSize;
        copy.windDirection = windDirection;
        copy.windStrength = windStrength;
        copy.windGustiness = windGustiness;
        copy.skyTint = skyTint.clone();
        copy.skyBrightness = skyBrightness;
        copy.skyHorizonBlend = skyHorizonBlend;
        copy.ambientColor = ambientColor.clone();
        copy.ambientIntensity = ambientIntensity;
        copy.lightColor = lightColor.clone();
        copy.lightIntensity = lightIntensity;
        copy.lightPitch = lightPitch;
        copy.lightYaw = lightYaw;
        return copy;
    }

    public void save(File file) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("template", template.name());
        root.put("fogColor", colorToJson(fogColor));
        root.put("fogDensity", fogDensity);
        root.put("fogNearDistance", fogNearDistance);
        root.put("fogFarDistance", fogFarDistance);
        root.put("rainColor", colorToJson(rainColor));
        root.put("rainIntensity", rainIntensity);
        root.put("rainSpeed", rainSpeed);
        root.put("rainAngle", rainAngle);
        root.put("overlayOpacity", overlayOpacity);
        root.put("snowColor", colorToJson(snowColor));
        root.put("snowIntensity", snowIntensity);
        root.put("snowSpeed", snowSpeed);
        root.put("snowFlakeSize", snowFlakeSize);
        root.put("windDirection", windDirection);
        root.put("windStrength", windStrength);
        root.put("windGustiness", windGustiness);
        root.put("skyTint", colorToJson(skyTint));
        root.put("skyBrightness", skyBrightness);
        root.put("skyHorizonBlend", skyHorizonBlend);
        root.put("ambientColor", colorToJson(ambientColor));
        root.put("ambientIntensity", ambientIntensity);
        root.put("lightColor", colorToJson(lightColor));
        root.put("lightIntensity", lightIntensity);
        root.put("lightPitch", lightPitch);
        root.put("lightYaw", lightYaw);

        JSONArray layersJson = new JSONArray();
        for (EnvironmentShaderLayerType layer : layers) {
            layersJson.put(layer.name());
        }
        root.put("layers", layersJson);

        Files.write(file.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    public void exportRuntimeAssets(File documentFile, String resourcesFolder) throws IOException {
        if (resourcesFolder == null || resourcesFolder.isBlank()) {
            return;
        }

        File outputDir = getRuntimeFolder(documentFile, resourcesFolder);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String assetBase = getRuntimeAssetBase(documentFile);
        String runtimeName = getRuntimeName(documentFile);

        Files.writeString(new File(outputDir, runtimeName + ".vert").toPath(),
                buildVertexShader(), StandardCharsets.UTF_8);
        Files.writeString(new File(outputDir, runtimeName + ".frag").toPath(),
                buildFragmentShader(), StandardCharsets.UTF_8);
        Files.writeString(new File(outputDir, runtimeName + ".j3md").toPath(),
                buildMaterialDefinition(assetBase, runtimeName), StandardCharsets.UTF_8);
        Files.writeString(new File(outputDir, runtimeName + ".j3m").toPath(),
                buildMaterialInstance(assetBase, runtimeName), StandardCharsets.UTF_8);
        Files.writeString(new File(outputDir, "environment.json").toPath(),
                buildEnvironmentMetadata(documentFile).toString(2), StandardCharsets.UTF_8);
        updateEnvironmentShaderIndex(documentFile, resourcesFolder);
    }

    public String buildPreviewSummary(File documentFile) {
        String runtimeName = getRuntimeName(documentFile);
        StringBuilder sb = new StringBuilder();
        sb.append("Runtime name: ").append(runtimeName).append("\n");
        sb.append("Layers: ");
        if (layers.isEmpty()) {
            sb.append("none");
        } else {
            boolean first = true;
            for (EnvironmentShaderLayerType layer : layers) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(layer.getDisplayName());
                first = false;
            }
        }
        sb.append("\n\n");

        if (layers.contains(EnvironmentShaderLayerType.FOG)) {
            sb.append("Fog\n");
            sb.append("  Color: ").append(colorToText(fogColor)).append("\n");
            sb.append("  Density: ").append(format(fogDensity)).append("\n");
            sb.append("  Range: ").append(format(fogNearDistance)).append(" -> ")
                    .append(format(fogFarDistance)).append("\n\n");
        }

        if (layers.contains(EnvironmentShaderLayerType.RAIN)) {
            sb.append("Rain\n");
            sb.append("  Color: ").append(colorToText(rainColor)).append("\n");
            sb.append("  Intensity: ").append(format(rainIntensity)).append("\n");
            sb.append("  Speed: ").append(format(rainSpeed)).append("\n");
            sb.append("  Angle: ").append(format(rainAngle)).append("\n");
            sb.append("  Overlay opacity: ").append(format(overlayOpacity)).append("\n\n");
        }

        if (layers.contains(EnvironmentShaderLayerType.SNOW)) {
            sb.append("Snow\n");
            sb.append("  Color: ").append(colorToText(snowColor)).append("\n");
            sb.append("  Intensity: ").append(format(snowIntensity)).append("\n");
            sb.append("  Speed: ").append(format(snowSpeed)).append("\n");
            sb.append("  Flake size: ").append(format(snowFlakeSize)).append("\n\n");
        }

        if (layers.contains(EnvironmentShaderLayerType.WIND)) {
            sb.append("Wind\n");
            sb.append("  Direction: ").append(format(windDirection)).append("\n");
            sb.append("  Strength: ").append(format(windStrength)).append("\n");
            sb.append("  Gustiness: ").append(format(windGustiness)).append("\n\n");
        }

        if (layers.contains(EnvironmentShaderLayerType.SKY_TWEAKS)) {
            sb.append("Sky Tweaks\n");
            sb.append("  Tint: ").append(colorToText(skyTint)).append("\n");
            sb.append("  Brightness: ").append(format(skyBrightness)).append("\n");
            sb.append("  Horizon blend: ").append(format(skyHorizonBlend)).append("\n\n");
        }

        if (layers.contains(EnvironmentShaderLayerType.AMBIENT_COLOR)) {
            sb.append("Ambient Color\n");
            sb.append("  Color: ").append(colorToText(ambientColor)).append("\n");
            sb.append("  Intensity: ").append(format(ambientIntensity)).append("\n\n");
        }

        if (layers.contains(EnvironmentShaderLayerType.LIGHTING)) {
            sb.append("Lighting\n");
            sb.append("  Color: ").append(colorToText(lightColor)).append("\n");
            sb.append("  Intensity: ").append(format(lightIntensity)).append("\n");
            sb.append("  Pitch/Yaw: ").append(format(lightPitch)).append(" / ")
                    .append(format(lightYaw)).append("\n");
        }

        return sb.toString();
    }

    public static EnvironmentShaderDocument load(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);

        EnvironmentShaderDocument doc = new EnvironmentShaderDocument(file.getAbsolutePath());
        doc.template = EnvironmentShaderTemplatePreset.fromName(
                root.optString("template", EnvironmentShaderTemplatePreset.FOG.name()));

        doc.layers.clear();
        JSONArray layersJson = root.optJSONArray("layers");
        if (layersJson != null) {
            for (int i = 0; i < layersJson.length(); i++) {
                try {
                    doc.layers.add(EnvironmentShaderLayerType.valueOf(layersJson.getString(i)));
                } catch (Exception ignored) {
                }
            }
        }
        if (doc.layers.isEmpty()) {
            doc.template.applyTo(doc);
        }

        doc.fogColor = colorFromJson(root.optJSONArray("fogColor"), new ColorRGBA(0.72f, 0.80f, 0.90f, 1f));
        doc.fogDensity = (float) root.optDouble("fogDensity", 0.35);
        doc.fogNearDistance = (float) root.optDouble("fogNearDistance", 12.0);
        doc.fogFarDistance = (float) root.optDouble("fogFarDistance", 80.0);
        doc.rainColor = colorFromJson(root.optJSONArray("rainColor"), new ColorRGBA(0.82f, 0.90f, 1f, 1f));
        doc.rainIntensity = (float) root.optDouble("rainIntensity", 0.55);
        doc.rainSpeed = (float) root.optDouble("rainSpeed", 1.2);
        doc.rainAngle = (float) root.optDouble("rainAngle", -18.0);
        doc.overlayOpacity = (float) root.optDouble("overlayOpacity", 0.32);
        doc.snowColor = colorFromJson(root.optJSONArray("snowColor"), new ColorRGBA(0.95f, 0.97f, 1f, 1f));
        doc.snowIntensity = (float) root.optDouble("snowIntensity", 0.45);
        doc.snowSpeed = (float) root.optDouble("snowSpeed", 0.65);
        doc.snowFlakeSize = (float) root.optDouble("snowFlakeSize", 0.55);
        doc.windDirection = (float) root.optDouble("windDirection", 24.0);
        doc.windStrength = (float) root.optDouble("windStrength", 0.45);
        doc.windGustiness = (float) root.optDouble("windGustiness", 0.35);
        doc.skyTint = colorFromJson(root.optJSONArray("skyTint"), new ColorRGBA(0.58f, 0.74f, 0.96f, 1f));
        doc.skyBrightness = (float) root.optDouble("skyBrightness", 1.0);
        doc.skyHorizonBlend = (float) root.optDouble("skyHorizonBlend", 0.45);
        doc.ambientColor = colorFromJson(root.optJSONArray("ambientColor"), new ColorRGBA(0.58f, 0.62f, 0.68f, 1f));
        doc.ambientIntensity = (float) root.optDouble("ambientIntensity", 0.65);
        doc.lightColor = colorFromJson(root.optJSONArray("lightColor"), new ColorRGBA(1f, 0.96f, 0.88f, 1f));
        doc.lightIntensity = (float) root.optDouble("lightIntensity", 1.1);
        doc.lightPitch = (float) root.optDouble("lightPitch", -38.0);
        doc.lightYaw = (float) root.optDouble("lightYaw", -32.0);
        return doc;
    }

    public static EnvironmentShaderDocument createFromTemplate(String filePath,
                                                               EnvironmentShaderTemplatePreset preset) {
        EnvironmentShaderDocument doc = new EnvironmentShaderDocument(filePath);
        (preset != null ? preset : EnvironmentShaderTemplatePreset.FOG).applyTo(doc);
        return doc;
    }

    public static void writeEmptyFile(File file, EnvironmentShaderTemplatePreset preset) throws IOException {
        EnvironmentShaderDocument.createFromTemplate(file.getAbsolutePath(), preset).save(file);
    }

    public static File getRuntimeFolder(File documentFile, String resourcesFolder) {
        return new File(resourcesFolder, "environment_shaders/" + getRuntimeName(documentFile));
    }

    public static String getRuntimeAssetBase(File documentFile) {
        String runtimeName = getRuntimeName(documentFile);
        return "environment_shaders/" + runtimeName + "/" + runtimeName;
    }

    public static String getRuntimeName(File documentFile) {
        String baseName = documentFile.getName();
        if (baseName.toLowerCase().endsWith(".smenvshader")) {
            baseName = baseName.substring(0, baseName.length() - ".smenvshader".length());
        }
        return baseName.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private void updateEnvironmentShaderIndex(File documentFile, String resourcesFolder) throws IOException {
        File rootFolder = new File(resourcesFolder, "environment_shaders");
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }

        File indexFile = new File(rootFolder, "environment-shaders-ext.json");
        JSONObject root;
        if (indexFile.exists()) {
            String content = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
            root = content == null || content.isBlank() ? new JSONObject() : new JSONObject(content);
        } else {
            root = new JSONObject();
        }

        JSONArray docs = root.optJSONArray("environmentShaders");
        if (docs == null) {
            docs = new JSONArray();
            root.put("environmentShaders", docs);
        }

        String runtimeName = getRuntimeName(documentFile);
        String runtimePath = getRuntimeAssetBase(documentFile) + ".j3md";
        boolean updated = false;
        for (int i = 0; i < docs.length(); i++) {
            JSONObject entry = docs.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            if (runtimeName.equalsIgnoreCase(entry.optString("name", ""))
                    || runtimePath.equalsIgnoreCase(entry.optString("path", ""))) {
                applyIndexEntry(entry, runtimeName, runtimePath);
                updated = true;
                break;
            }
        }

        if (!updated) {
            JSONObject entry = new JSONObject();
            applyIndexEntry(entry, runtimeName, runtimePath);
            docs.put(entry);
        }

        Files.writeString(indexFile.toPath(), root.toString(2), StandardCharsets.UTF_8);
    }

    private void applyIndexEntry(JSONObject entry, String runtimeName, String runtimePath) {
        entry.put("name", runtimeName);
        entry.put("path", runtimePath);
        JSONArray layersJson = new JSONArray();
        for (EnvironmentShaderLayerType layer : layers) {
            layersJson.put(layer.name());
        }
        entry.put("layers", layersJson);
    }

    private JSONObject buildEnvironmentMetadata(File documentFile) {
        JSONObject root = new JSONObject();
        root.put("name", getRuntimeName(documentFile));
        root.put("template", template.name());

        JSONArray layersJson = new JSONArray();
        for (EnvironmentShaderLayerType layer : layers) {
            layersJson.put(layer.name());
        }
        root.put("layers", layersJson);

        JSONObject fog = new JSONObject();
        fog.put("color", colorToJson(fogColor));
        fog.put("density", fogDensity);
        fog.put("nearDistance", fogNearDistance);
        fog.put("farDistance", fogFarDistance);
        root.put("fog", fog);

        JSONObject rain = new JSONObject();
        rain.put("color", colorToJson(rainColor));
        rain.put("intensity", rainIntensity);
        rain.put("speed", rainSpeed);
        rain.put("angle", rainAngle);
        rain.put("overlayOpacity", overlayOpacity);
        root.put("rain", rain);

        JSONObject snow = new JSONObject();
        snow.put("color", colorToJson(snowColor));
        snow.put("intensity", snowIntensity);
        snow.put("speed", snowSpeed);
        snow.put("flakeSize", snowFlakeSize);
        root.put("snow", snow);

        JSONObject wind = new JSONObject();
        wind.put("direction", windDirection);
        wind.put("strength", windStrength);
        wind.put("gustiness", windGustiness);
        root.put("wind", wind);

        JSONObject sky = new JSONObject();
        sky.put("tint", colorToJson(skyTint));
        sky.put("brightness", skyBrightness);
        sky.put("horizonBlend", skyHorizonBlend);
        root.put("skyTweaks", sky);

        JSONObject ambient = new JSONObject();
        ambient.put("color", colorToJson(ambientColor));
        ambient.put("intensity", ambientIntensity);
        root.put("ambientColor", ambient);

        JSONObject lighting = new JSONObject();
        lighting.put("color", colorToJson(lightColor));
        lighting.put("intensity", lightIntensity);
        lighting.put("pitch", lightPitch);
        lighting.put("yaw", lightYaw);
        root.put("lighting", lighting);
        return root;
    }

    private String buildMaterialDefinition(String assetBase, String runtimeName) {
        return "MaterialDef " + runtimeName + " {\n" +
                "\n" +
                "    MaterialParameters {\n" +
                "        Color FogColor\n" +
                "        Float FogDensity : " + fogDensity + "\n" +
                "        Float FogNearDistance : " + fogNearDistance + "\n" +
                "        Float FogFarDistance : " + fogFarDistance + "\n" +
                "        Color RainColor\n" +
                "        Float RainIntensity : " + rainIntensity + "\n" +
                "        Float RainSpeed : " + rainSpeed + "\n" +
                "        Float RainAngle : " + rainAngle + "\n" +
                "        Float OverlayOpacity : " + overlayOpacity + "\n" +
                "        Color SnowColor\n" +
                "        Float SnowIntensity : " + snowIntensity + "\n" +
                "        Float SnowSpeed : " + snowSpeed + "\n" +
                "        Float SnowFlakeSize : " + snowFlakeSize + "\n" +
                "        Float WindDirection : " + windDirection + "\n" +
                "        Float WindStrength : " + windStrength + "\n" +
                "        Float WindGustiness : " + windGustiness + "\n" +
                "        Color SkyTint\n" +
                "        Float SkyBrightness : " + skyBrightness + "\n" +
                "        Float SkyHorizonBlend : " + skyHorizonBlend + "\n" +
                "        Color AmbientColor\n" +
                "        Float AmbientIntensity : " + ambientIntensity + "\n" +
                "        Color LightColor\n" +
                "        Float LightIntensity : " + lightIntensity + "\n" +
                "        Float LightPitch : " + lightPitch + "\n" +
                "        Float LightYaw : " + lightYaw + "\n" +
                "        Float Time : 0\n" +
                "    }\n" +
                "\n" +
                "    Technique {\n" +
                "        VertexShader GLSL100:   " + assetBase + ".vert\n" +
                "        FragmentShader GLSL100: " + assetBase + ".frag\n" +
                "\n" +
                "        WorldParameters {\n" +
                "            WorldViewProjectionMatrix\n" +
                "        }\n" +
                "\n" +
                "        RenderState {\n" +
                "            Blend Alpha\n" +
                "            FaceCull Off\n" +
                "            DepthWrite Off\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
    }

    private String buildMaterialInstance(String assetBase, String runtimeName) {
        return "Material " + runtimeName + " : " + assetBase + ".j3md {\n" +
                "    FogColor : " + colorToMaterialLine(fogColor) + "\n" +
                "    FogDensity : " + fogDensity + "\n" +
                "    FogNearDistance : " + fogNearDistance + "\n" +
                "    FogFarDistance : " + fogFarDistance + "\n" +
                "    RainColor : " + colorToMaterialLine(rainColor) + "\n" +
                "    RainIntensity : " + rainIntensity + "\n" +
                "    RainSpeed : " + rainSpeed + "\n" +
                "    RainAngle : " + rainAngle + "\n" +
                "    OverlayOpacity : " + overlayOpacity + "\n" +
                "    SnowColor : " + colorToMaterialLine(snowColor) + "\n" +
                "    SnowIntensity : " + snowIntensity + "\n" +
                "    SnowSpeed : " + snowSpeed + "\n" +
                "    SnowFlakeSize : " + snowFlakeSize + "\n" +
                "    WindDirection : " + windDirection + "\n" +
                "    WindStrength : " + windStrength + "\n" +
                "    WindGustiness : " + windGustiness + "\n" +
                "    SkyTint : " + colorToMaterialLine(skyTint) + "\n" +
                "    SkyBrightness : " + skyBrightness + "\n" +
                "    SkyHorizonBlend : " + skyHorizonBlend + "\n" +
                "    AmbientColor : " + colorToMaterialLine(ambientColor) + "\n" +
                "    AmbientIntensity : " + ambientIntensity + "\n" +
                "    LightColor : " + colorToMaterialLine(lightColor) + "\n" +
                "    LightIntensity : " + lightIntensity + "\n" +
                "    LightPitch : " + lightPitch + "\n" +
                "    LightYaw : " + lightYaw + "\n" +
                "    Time : 0.0\n" +
                "}\n";
    }

    private String buildVertexShader() {
        return "uniform mat4 g_WorldViewProjectionMatrix;\n" +
                "attribute vec3 inPosition;\n" +
                "attribute vec2 inTexCoord;\n" +
                "\n" +
                "varying vec2 vUv;\n" +
                "\n" +
                "void main() {\n" +
                "    vUv = inTexCoord;\n" +
                "    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);\n" +
                "}\n";
    }

    private String buildFragmentShader() {
        return "#ifdef GL_ES\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "\n" +
                "uniform vec4 m_FogColor;\n" +
                "uniform float m_FogDensity;\n" +
                "uniform float m_FogNearDistance;\n" +
                "uniform float m_FogFarDistance;\n" +
                "uniform vec4 m_RainColor;\n" +
                "uniform float m_RainIntensity;\n" +
                "uniform float m_RainSpeed;\n" +
                "uniform float m_RainAngle;\n" +
                "uniform float m_OverlayOpacity;\n" +
                "uniform vec4 m_SnowColor;\n" +
                "uniform float m_SnowIntensity;\n" +
                "uniform float m_SnowSpeed;\n" +
                "uniform float m_SnowFlakeSize;\n" +
                "uniform float m_WindDirection;\n" +
                "uniform float m_WindStrength;\n" +
                "uniform float m_WindGustiness;\n" +
                "uniform vec4 m_SkyTint;\n" +
                "uniform float m_SkyBrightness;\n" +
                "uniform float m_SkyHorizonBlend;\n" +
                "uniform vec4 m_AmbientColor;\n" +
                "uniform float m_AmbientIntensity;\n" +
                "uniform vec4 m_LightColor;\n" +
                "uniform float m_LightIntensity;\n" +
                "uniform float m_LightPitch;\n" +
                "uniform float m_LightYaw;\n" +
                "uniform float m_Time;\n" +
                "\n" +
                "varying vec2 vUv;\n" +
                "\n" +
                "const bool ENABLE_FOG = " + layers.contains(EnvironmentShaderLayerType.FOG) + ";\n" +
                "const bool ENABLE_RAIN = " + layers.contains(EnvironmentShaderLayerType.RAIN) + ";\n" +
                "const bool ENABLE_SNOW = " + layers.contains(EnvironmentShaderLayerType.SNOW) + ";\n" +
                "const bool ENABLE_WIND = " + layers.contains(EnvironmentShaderLayerType.WIND) + ";\n" +
                "const bool ENABLE_SKY = " + layers.contains(EnvironmentShaderLayerType.SKY_TWEAKS) + ";\n" +
                "const bool ENABLE_AMBIENT = " + layers.contains(EnvironmentShaderLayerType.AMBIENT_COLOR) + ";\n" +
                "const bool ENABLE_LIGHT = " + layers.contains(EnvironmentShaderLayerType.LIGHTING) + ";\n" +
                "\n" +
                "float hash(vec2 p) {\n" +
                "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);\n" +
                "}\n" +
                "\n" +
                "float rainStripe(vec2 uv, float time) {\n" +
                "    float angle = radians(m_RainAngle + (ENABLE_WIND ? m_WindDirection * 0.35 : 0.0));\n" +
                "    mat2 rot = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));\n" +
                "    float windShift = ENABLE_WIND ? sin(time * (1.5 + m_WindGustiness * 2.5)) * m_WindStrength * 1.5 : 0.0;\n" +
                "    vec2 p = rot * ((uv * vec2(22.0, 14.0)) + vec2(windShift, time * m_RainSpeed * 7.5));\n" +
                "    vec2 cell = floor(p);\n" +
                "    float rnd = hash(cell);\n" +
                "    float streak = smoothstep(0.86, 0.98, fract(p.y + rnd));\n" +
                "    float lane = smoothstep(0.18, 0.02, abs(fract(p.x) - 0.5));\n" +
                "    return streak * lane;\n" +
                "}\n" +
                "\n" +
                "float snowFlake(vec2 uv, float time) {\n" +
                "    float windAngle = radians(ENABLE_WIND ? m_WindDirection : 0.0);\n" +
                "    vec2 windVec = vec2(cos(windAngle), sin(windAngle)) * (ENABLE_WIND ? m_WindStrength * 0.4 : 0.0);\n" +
                "    vec2 p = uv * vec2(12.0, 9.0);\n" +
                "    p += vec2(time * windVec.x, time * (m_SnowSpeed * 1.8 + windVec.y));\n" +
                "    vec2 cell = floor(p);\n" +
                "    vec2 local = fract(p) - 0.5;\n" +
                "    float rnd = hash(cell);\n" +
                "    float gust = ENABLE_WIND ? sin(time * (2.0 + m_WindGustiness * 3.0) + rnd * 6.28318) * m_WindStrength * 0.25 : 0.0;\n" +
                "    local.x += gust;\n" +
                "    float size = mix(0.07, 0.22, clamp(m_SnowFlakeSize, 0.0, 1.5));\n" +
                "    float d = length(local + vec2(rnd - 0.5, sin(time + rnd * 8.0) * 0.03));\n" +
                "    return smoothstep(size, size * 0.35, d);\n" +
                "}\n" +
                "\n" +
                "void main() {\n" +
                "    vec3 color = ENABLE_SKY ? (m_SkyTint.rgb * m_SkyBrightness * mix(0.65, 1.15, pow(1.0 - vUv.y, m_SkyHorizonBlend + 0.2))) : vec3(0.0);\n" +
                "    float alpha = 0.0;\n" +
                "\n" +
                "    if (ENABLE_FOG) {\n" +
                "        float depthLike = mix(m_FogNearDistance, m_FogFarDistance, vUv.y);\n" +
                "        float fogFactor = smoothstep(m_FogNearDistance, m_FogFarDistance, depthLike);\n" +
                "        fogFactor = clamp(fogFactor * max(m_FogDensity, 0.01), 0.0, 1.0);\n" +
                "        color += m_FogColor.rgb * fogFactor;\n" +
                "        alpha = max(alpha, fogFactor * 0.85 * m_FogColor.a);\n" +
                "    }\n" +
                "\n" +
                "    if (ENABLE_RAIN) {\n" +
                "        float streaks = rainStripe(vUv, m_Time);\n" +
                "        streaks += rainStripe(vUv + vec2(0.19, 0.11), m_Time * 1.1) * 0.7;\n" +
                "        streaks += rainStripe(vUv + vec2(-0.13, 0.27), m_Time * 0.92) * 0.5;\n" +
                "        streaks *= clamp(m_RainIntensity, 0.0, 1.5);\n" +
                "        color += m_RainColor.rgb * streaks;\n" +
                "        alpha = max(alpha, streaks * max(m_OverlayOpacity, 0.02) * m_RainColor.a);\n" +
                "    }\n" +
                "\n" +
                "    if (ENABLE_SNOW) {\n" +
                "        float flakes = snowFlake(vUv, m_Time);\n" +
                "        flakes += snowFlake(vUv + vec2(0.21, 0.17), m_Time * 0.82) * 0.7;\n" +
                "        flakes += snowFlake(vUv + vec2(-0.11, 0.31), m_Time * 1.14) * 0.55;\n" +
                "        flakes *= clamp(m_SnowIntensity, 0.0, 1.5);\n" +
                "        color += m_SnowColor.rgb * flakes;\n" +
                "        alpha = max(alpha, flakes * 0.7 * m_SnowColor.a);\n" +
                "    }\n" +
                "\n" +
                "    if (ENABLE_AMBIENT) {\n" +
                "        color = mix(color, color + m_AmbientColor.rgb * m_AmbientIntensity * 0.35, 0.6);\n" +
                "    }\n" +
                "\n" +
                "    if (ENABLE_LIGHT) {\n" +
                "        float sunArc = 0.5 + 0.5 * sin(radians(m_LightPitch) + vUv.x * 2.3 + vUv.y * 1.7);\n" +
                "        color += m_LightColor.rgb * m_LightIntensity * sunArc * 0.18;\n" +
                "    }\n" +
                "\n" +
                "    gl_FragColor = vec4(color, clamp(alpha, 0.0, 1.0));\n" +
                "}\n";
    }

    private static JSONArray colorToJson(ColorRGBA color) {
        return new JSONArray(new float[]{color.r, color.g, color.b, color.a});
    }

    private static ColorRGBA colorFromJson(JSONArray color, ColorRGBA fallback) {
        if (color == null || color.length() < 4) {
            return fallback.clone();
        }
        return new ColorRGBA(
                (float) color.optDouble(0, fallback.r),
                (float) color.optDouble(1, fallback.g),
                (float) color.optDouble(2, fallback.b),
                (float) color.optDouble(3, fallback.a)
        );
    }

    private String colorToMaterialLine(ColorRGBA color) {
        return color.r + " " + color.g + " " + color.b + " " + color.a;
    }

    private String colorToText(ColorRGBA color) {
        return format(color.r) + ", " + format(color.g) + ", " + format(color.b) + ", " + format(color.a);
    }

    private String format(float value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
