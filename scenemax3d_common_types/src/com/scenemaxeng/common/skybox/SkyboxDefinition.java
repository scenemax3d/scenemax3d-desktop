package com.scenemaxeng.common.skybox;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class SkyboxDefinition {
    public static final String FILE_EXTENSION = ".smskybox";
    public static final String SCHEMA_VERSION = "1.0";
    public static final String MODE_HDRI_CUBEMAP = "hdri_cubemap";
    public static final String MODE_PROCEDURAL_ATMOSPHERE = "procedural_atmosphere";

    private String id = "skybox";
    private String displayName = "Skybox";
    private String mode = MODE_PROCEDURAL_ATMOSPHERE;
    private String cubemap = "";
    private String diffuseMap = "";
    private String specularMap = "";
    private double skyboxBrightness = 5000.0;
    private double environmentIntensity = 2000.0;
    private double rotationYDegrees = 0.0;
    private boolean generatedEnvironmentMap = true;
    private boolean affectsLightmappedMeshes = true;
    private String atmospherePreset = "EARTH";
    private String atmosphereQuality = "LOOKUP_TEXTURE";
    private double exposureEv100 = 13.0;
    private double sunAzimuthDegrees = 35.0;
    private double sunElevationDegrees = 42.0;
    private double sunIlluminanceScale = 1.0;
    private String sunColor = "#fff4d6";
    private String groundAlbedo = "#4d4d4d";
    private boolean atmosphereEnvironmentMap = true;
    private int atmosphereEnvironmentMapSize = 512;
    private boolean volumetricFog = false;
    private double fogAmbientIntensity = 0.0;
    private String notes = "";

    public static SkyboxDefinition createTemplate(String displayName, String mode) {
        SkyboxDefinition definition = new SkyboxDefinition();
        definition.setDisplayName(displayName);
        definition.id = slugId(definition.displayName);
        definition.setMode(mode);
        return definition;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("type", "SceneMaxBevySkyboxDefinition")
                .put("schemaVersion", SCHEMA_VERSION)
                .put("id", getId())
                .put("displayName", getDisplayName())
                .put("mode", getMode())
                .put("cubemap", getCubemap())
                .put("diffuseMap", getDiffuseMap())
                .put("specularMap", getSpecularMap())
                .put("skyboxBrightness", skyboxBrightness)
                .put("environmentIntensity", environmentIntensity)
                .put("rotationYDegrees", rotationYDegrees)
                .put("generatedEnvironmentMap", generatedEnvironmentMap)
                .put("affectsLightmappedMeshes", affectsLightmappedMeshes)
                .put("atmospherePreset", atmospherePreset)
                .put("atmosphereQuality", atmosphereQuality)
                .put("exposureEv100", exposureEv100)
                .put("sunAzimuthDegrees", sunAzimuthDegrees)
                .put("sunElevationDegrees", sunElevationDegrees)
                .put("sunIlluminanceScale", sunIlluminanceScale)
                .put("sunColor", normalizeHexColor(sunColor, "#fff4d6"))
                .put("groundAlbedo", normalizeHexColor(groundAlbedo, "#4d4d4d"))
                .put("atmosphereEnvironmentMap", atmosphereEnvironmentMap)
                .put("atmosphereEnvironmentMapSize", atmosphereEnvironmentMapSize)
                .put("volumetricFog", volumetricFog)
                .put("fogAmbientIntensity", fogAmbientIntensity)
                .put("notes", notes == null ? "" : notes);
    }

    public static SkyboxDefinition fromJSON(JSONObject json) {
        SkyboxDefinition definition = new SkyboxDefinition();
        if (json == null) {
            return definition;
        }
        definition.setId(json.optString("id", definition.id));
        definition.setDisplayName(json.optString("displayName", definition.displayName));
        definition.setMode(json.optString("mode", definition.mode));
        definition.setCubemap(json.optString("cubemap", ""));
        definition.setDiffuseMap(json.optString("diffuseMap", ""));
        definition.setSpecularMap(json.optString("specularMap", ""));
        definition.skyboxBrightness = Math.max(0.0, json.optDouble("skyboxBrightness", definition.skyboxBrightness));
        definition.environmentIntensity = Math.max(0.0, json.optDouble("environmentIntensity", definition.environmentIntensity));
        definition.rotationYDegrees = json.optDouble("rotationYDegrees", definition.rotationYDegrees);
        definition.generatedEnvironmentMap = json.optBoolean("generatedEnvironmentMap", definition.generatedEnvironmentMap);
        definition.affectsLightmappedMeshes = json.optBoolean("affectsLightmappedMeshes", definition.affectsLightmappedMeshes);
        definition.atmospherePreset = json.optString("atmospherePreset", definition.atmospherePreset);
        definition.atmosphereQuality = json.optString("atmosphereQuality", definition.atmosphereQuality);
        definition.exposureEv100 = json.optDouble("exposureEv100", definition.exposureEv100);
        definition.sunAzimuthDegrees = json.optDouble("sunAzimuthDegrees", definition.sunAzimuthDegrees);
        definition.sunElevationDegrees = json.optDouble("sunElevationDegrees", definition.sunElevationDegrees);
        definition.sunIlluminanceScale = Math.max(0.0, json.optDouble("sunIlluminanceScale", definition.sunIlluminanceScale));
        definition.sunColor = normalizeHexColor(json.optString("sunColor", definition.sunColor), "#fff4d6");
        definition.groundAlbedo = normalizeHexColor(json.optString("groundAlbedo", definition.groundAlbedo), "#4d4d4d");
        definition.atmosphereEnvironmentMap = json.optBoolean("atmosphereEnvironmentMap", definition.atmosphereEnvironmentMap);
        definition.atmosphereEnvironmentMapSize = Math.max(32, json.optInt("atmosphereEnvironmentMapSize", definition.atmosphereEnvironmentMapSize));
        definition.volumetricFog = json.optBoolean("volumetricFog", definition.volumetricFog);
        definition.fogAmbientIntensity = Math.max(0.0, json.optDouble("fogAmbientIntensity", definition.fogAmbientIntensity));
        definition.notes = json.optString("notes", "");
        return definition;
    }

    public static SkyboxDefinition load(File file) throws IOException {
        return fromJSON(new JSONObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8)));
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtils.writeStringToFile(file, toJSON().toString(2), StandardCharsets.UTF_8);
    }

    public String toSummaryText(File file) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bevy Skybox\n\n");
        sb.append("Name: ").append(getDisplayName()).append('\n');
        sb.append("ID: ").append(getId()).append('\n');
        sb.append("File: ").append(file == null ? "" : file.getAbsolutePath()).append("\n\n");
        sb.append("Mode: ").append(MODE_HDRI_CUBEMAP.equals(getMode()) ? "HDRI / Cubemap Sky" : "Procedural Sky (Bevy Atmosphere)").append('\n');
        if (MODE_HDRI_CUBEMAP.equals(getMode())) {
            sb.append("Cubemap: ").append(emptyLabel(cubemap)).append('\n');
            sb.append("Diffuse IBL: ").append(emptyLabel(diffuseMap)).append('\n');
            sb.append("Specular IBL: ").append(emptyLabel(specularMap)).append('\n');
            sb.append("Skybox brightness: ").append(skyboxBrightness).append('\n');
            sb.append("Environment intensity: ").append(environmentIntensity).append('\n');
            sb.append("Generated environment map: ").append(generatedEnvironmentMap).append('\n');
        } else {
            sb.append("Atmosphere preset: ").append(atmospherePreset).append('\n');
            sb.append("Quality: ").append(atmosphereQuality).append('\n');
            sb.append("Exposure EV100: ").append(exposureEv100).append('\n');
            sb.append("Sun: azimuth ").append(sunAzimuthDegrees)
                    .append(" deg, elevation ").append(sunElevationDegrees)
                    .append(" deg, color ").append(normalizeHexColor(sunColor, "#fff4d6"))
                    .append(", illuminance x").append(sunIlluminanceScale).append('\n');
            sb.append("Ground albedo: ").append(normalizeHexColor(groundAlbedo, "#4d4d4d")).append('\n');
            sb.append("Atmosphere IBL: ").append(atmosphereEnvironmentMap)
                    .append(" (").append(atmosphereEnvironmentMapSize).append(" px)\n");
            sb.append("Volumetric fog: ").append(volumetricFog).append('\n');
        }
        sb.append("Rotation Y: ").append(rotationYDegrees).append(" deg\n");
        sb.append("Affects lightmapped meshes: ").append(affectsLightmappedMeshes).append('\n');
        if (notes != null && !notes.isBlank()) {
            sb.append("\nNotes:\n").append(notes.trim()).append('\n');
        }
        return sb.toString();
    }

    private static String emptyLabel(String value) {
        return value == null || value.trim().isEmpty() ? "(not set)" : value.trim();
    }

    private static String normalizeHexColor(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        return normalized.matches("#[0-9a-f]{6}") ? normalized : fallback;
    }

    private static String slugId(String value) {
        String slug = value == null ? "skybox" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "skybox" : "skybox_" + slug.replaceFirst("^skybox_+", "");
    }

    public String getId() {
        return id == null || id.trim().isEmpty() ? "skybox" : id.trim();
    }

    public void setId(String id) {
        this.id = id == null || id.trim().isEmpty() ? "skybox" : id.trim();
    }

    public String getDisplayName() {
        return displayName == null || displayName.trim().isEmpty() ? "Skybox" : displayName.trim();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.trim().isEmpty() ? "Skybox" : displayName.trim();
    }

    public String getMode() {
        return MODE_HDRI_CUBEMAP.equals(mode) ? MODE_HDRI_CUBEMAP : MODE_PROCEDURAL_ATMOSPHERE;
    }

    public void setMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        this.mode = MODE_HDRI_CUBEMAP.equals(normalized) ? MODE_HDRI_CUBEMAP : MODE_PROCEDURAL_ATMOSPHERE;
    }

    public String getCubemap() {
        return cubemap == null ? "" : cubemap.trim();
    }

    public void setCubemap(String cubemap) {
        this.cubemap = cubemap == null ? "" : cubemap.trim();
    }

    public String getDiffuseMap() {
        return diffuseMap == null ? "" : diffuseMap.trim();
    }

    public void setDiffuseMap(String diffuseMap) {
        this.diffuseMap = diffuseMap == null ? "" : diffuseMap.trim();
    }

    public String getSpecularMap() {
        return specularMap == null ? "" : specularMap.trim();
    }

    public void setSpecularMap(String specularMap) {
        this.specularMap = specularMap == null ? "" : specularMap.trim();
    }
}
