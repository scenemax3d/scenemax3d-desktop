package com.scenemax.designer.material;

import com.jme3.math.ColorRGBA;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MaterialDocument {

    private static final int FORMAT_VERSION = 1;
    public static final String RUNTIME_FOLDER = "material";

    private final String filePath;
    private MaterialTemplatePreset template = MaterialTemplatePreset.MATTE_PAINT;
    private MaterialPreviewShape previewShape = MaterialPreviewShape.BOX;
    private float previewScale = 1f;
    private ColorRGBA baseColor = ColorRGBA.White.clone();
    private ColorRGBA glowColor = new ColorRGBA(0f, 0f, 0f, 1f);
    private float ambientStrength = 0.4f;
    private float specularStrength = 1f;
    private float shininess = 32f;
    private float opacity = 1f;
    private float glowStrength = 0f;
    private float alphaDiscardThreshold = 0f;
    private boolean transparent = false;
    private boolean doubleSided = false;
    private String diffuseTexture = "";
    private String normalTexture = "";
    private String glowTexture = "";

    public MaterialDocument(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public MaterialTemplatePreset getTemplate() {
        return template;
    }

    public void setTemplate(MaterialTemplatePreset template) {
        this.template = template != null ? template : MaterialTemplatePreset.MATTE_PAINT;
    }

    public MaterialPreviewShape getPreviewShape() {
        return previewShape;
    }

    public void setPreviewShape(MaterialPreviewShape previewShape) {
        this.previewShape = previewShape != null ? previewShape : MaterialPreviewShape.BOX;
    }

    public float getPreviewScale() {
        return previewScale;
    }

    public void setPreviewScale(float previewScale) {
        this.previewScale = clamp(previewScale, 0.2f, 4f);
    }

    public ColorRGBA getBaseColor() {
        return baseColor.clone();
    }

    public void setBaseColor(ColorRGBA baseColor) {
        this.baseColor = baseColor != null ? baseColor.clone() : ColorRGBA.White.clone();
    }

    public ColorRGBA getGlowColor() {
        return glowColor.clone();
    }

    public void setGlowColor(ColorRGBA glowColor) {
        this.glowColor = glowColor != null ? glowColor.clone() : new ColorRGBA(0f, 0f, 0f, 1f);
    }

    public float getAmbientStrength() {
        return ambientStrength;
    }

    public void setAmbientStrength(float ambientStrength) {
        this.ambientStrength = clamp(ambientStrength, 0f, 1f);
    }

    public float getSpecularStrength() {
        return specularStrength;
    }

    public void setSpecularStrength(float specularStrength) {
        this.specularStrength = clamp(specularStrength, 0f, 2f);
    }

    public float getShininess() {
        return shininess;
    }

    public void setShininess(float shininess) {
        this.shininess = clamp(shininess, 0f, 128f);
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = clamp(opacity, 0f, 1f);
    }

    public float getGlowStrength() {
        return glowStrength;
    }

    public void setGlowStrength(float glowStrength) {
        this.glowStrength = clamp(glowStrength, 0f, 2f);
    }

    public float getAlphaDiscardThreshold() {
        return alphaDiscardThreshold;
    }

    public void setAlphaDiscardThreshold(float alphaDiscardThreshold) {
        this.alphaDiscardThreshold = clamp(alphaDiscardThreshold, 0f, 1f);
    }

    public boolean isTransparent() {
        return transparent;
    }

    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    public boolean isDoubleSided() {
        return doubleSided;
    }

    public void setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
    }

    public String getDiffuseTexture() {
        return diffuseTexture;
    }

    public void setDiffuseTexture(String diffuseTexture) {
        this.diffuseTexture = sanitizePath(diffuseTexture);
    }

    public String getNormalTexture() {
        return normalTexture;
    }

    public void setNormalTexture(String normalTexture) {
        this.normalTexture = sanitizePath(normalTexture);
    }

    public String getGlowTexture() {
        return glowTexture;
    }

    public void setGlowTexture(String glowTexture) {
        this.glowTexture = sanitizePath(glowTexture);
    }

    public MaterialDocument copy() {
        MaterialDocument copy = new MaterialDocument(filePath);
        copy.template = template;
        copy.previewShape = previewShape;
        copy.previewScale = previewScale;
        copy.baseColor = baseColor.clone();
        copy.glowColor = glowColor.clone();
        copy.ambientStrength = ambientStrength;
        copy.specularStrength = specularStrength;
        copy.shininess = shininess;
        copy.opacity = opacity;
        copy.glowStrength = glowStrength;
        copy.alphaDiscardThreshold = alphaDiscardThreshold;
        copy.transparent = transparent;
        copy.doubleSided = doubleSided;
        copy.diffuseTexture = diffuseTexture;
        copy.normalTexture = normalTexture;
        copy.glowTexture = glowTexture;
        return copy;
    }

    public void save(File file) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("template", template.name());
        root.put("previewShape", previewShape.name());
        root.put("previewScale", previewScale);
        root.put("baseColor", toArray(baseColor));
        root.put("glowColor", toArray(glowColor));
        root.put("ambientStrength", ambientStrength);
        root.put("specularStrength", specularStrength);
        root.put("shininess", shininess);
        root.put("opacity", opacity);
        root.put("glowStrength", glowStrength);
        root.put("alphaDiscardThreshold", alphaDiscardThreshold);
        root.put("transparent", transparent);
        root.put("doubleSided", doubleSided);
        root.put("diffuseTexture", diffuseTexture);
        root.put("normalTexture", normalTexture);
        root.put("glowTexture", glowTexture);
        Files.writeString(file.toPath(), root.toString(2), StandardCharsets.UTF_8);
    }

    public void exportRuntimeAssets(File documentFile, String resourcesFolder) throws IOException {
        if (resourcesFolder == null || resourcesFolder.isBlank()) {
            return;
        }

        File outputDir = getRuntimeFolder(documentFile, resourcesFolder);
        FileUtils.forceMkdir(outputDir);

        String runtimeName = getRuntimeName(documentFile);
        TextureExport diffuse = exportTexture(outputDir, runtimeName, "diffuse", diffuseTexture, resourcesFolder);
        TextureExport normal = exportTexture(outputDir, runtimeName, "normal", normalTexture, resourcesFolder);
        TextureExport glow = exportTexture(outputDir, runtimeName, "glow", glowTexture, resourcesFolder);

        File runtimeMaterial = new File(outputDir, runtimeName + ".j3m");
        Files.writeString(runtimeMaterial.toPath(),
                buildMaterialInstance(runtimeName, diffuse.runtimePath, normal.runtimePath, glow.runtimePath),
                StandardCharsets.UTF_8);

        save(new File(outputDir, runtimeName + ".mat"));
        updateMaterialIndex(documentFile, resourcesFolder);
    }

    public static void removeRuntimeAssets(File documentFile, String resourcesFolder) throws IOException {
        if (resourcesFolder == null || resourcesFolder.isBlank() || documentFile == null) {
            return;
        }

        File outputDir = getRuntimeFolder(documentFile, resourcesFolder);
        if (outputDir.exists()) {
            FileUtils.deleteDirectory(outputDir);
        }
        removeIndexEntry(documentFile, resourcesFolder);
    }

    public static MaterialDocument load(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);

        MaterialDocument doc = new MaterialDocument(file.getAbsolutePath());
        doc.template = MaterialTemplatePreset.fromName(root.optString("template", MaterialTemplatePreset.MATTE_PAINT.name()));
        doc.previewShape = parsePreviewShape(root.optString("previewShape", MaterialPreviewShape.BOX.name()));
        doc.previewScale = clamp((float) root.optDouble("previewScale", 1.0), 0.2f, 4f);
        doc.baseColor = parseColor(root.optJSONArray("baseColor"), ColorRGBA.White.clone());
        doc.glowColor = parseColor(root.optJSONArray("glowColor"), new ColorRGBA(0f, 0f, 0f, 1f));
        doc.ambientStrength = clamp((float) root.optDouble("ambientStrength", 0.4), 0f, 1f);
        doc.specularStrength = clamp((float) root.optDouble("specularStrength", 1.0), 0f, 2f);
        doc.shininess = clamp((float) root.optDouble("shininess", 32.0), 0f, 128f);
        doc.opacity = clamp((float) root.optDouble("opacity", 1.0), 0f, 1f);
        doc.glowStrength = clamp((float) root.optDouble("glowStrength", 0.0), 0f, 2f);
        doc.alphaDiscardThreshold = clamp((float) root.optDouble("alphaDiscardThreshold", 0.0), 0f, 1f);
        doc.transparent = root.optBoolean("transparent", false);
        doc.doubleSided = root.optBoolean("doubleSided", false);
        doc.diffuseTexture = sanitizePath(root.optString("diffuseTexture", ""));
        doc.normalTexture = sanitizePath(root.optString("normalTexture", ""));
        doc.glowTexture = sanitizePath(root.optString("glowTexture", ""));
        return doc;
    }

    public static MaterialDocument createFromTemplate(String filePath, MaterialTemplatePreset preset) {
        MaterialDocument doc = new MaterialDocument(filePath);
        (preset != null ? preset : MaterialTemplatePreset.MATTE_PAINT).applyTo(doc);
        return doc;
    }

    public static void writeEmptyFile(File file, MaterialTemplatePreset preset) throws IOException {
        createFromTemplate(file.getAbsolutePath(), preset).save(file);
    }

    public static File getRuntimeFolder(File documentFile, String resourcesFolder) {
        return new File(resourcesFolder, RUNTIME_FOLDER + "/" + getRuntimeName(documentFile));
    }

    public static String getRuntimeAssetBase(File documentFile) {
        String runtimeName = getRuntimeName(documentFile);
        return RUNTIME_FOLDER + "/" + runtimeName + "/" + runtimeName;
    }

    public static String getRuntimeName(File documentFile) {
        String baseName = documentFile.getName();
        if (baseName.toLowerCase().endsWith(".mat")) {
            baseName = baseName.substring(0, baseName.length() - ".mat".length());
        }
        return baseName.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private void updateMaterialIndex(File documentFile, String resourcesFolder) throws IOException {
        File rootFolder = new File(resourcesFolder, RUNTIME_FOLDER);
        FileUtils.forceMkdir(rootFolder);
        File indexFile = new File(rootFolder, "materials-ext.json");
        JSONObject root;
        if (indexFile.exists()) {
            String content = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
            root = content == null || content.isBlank() ? new JSONObject() : new JSONObject(content);
        } else {
            root = new JSONObject();
        }

        JSONArray materials = root.optJSONArray("materials");
        if (materials == null) {
            materials = new JSONArray();
            root.put("materials", materials);
        }

        String runtimeName = getRuntimeName(documentFile);
        String runtimePath = getRuntimeAssetBase(documentFile) + ".j3m";
        boolean updated = false;
        for (int i = 0; i < materials.length(); i++) {
            JSONObject material = materials.optJSONObject(i);
            if (material == null) {
                continue;
            }
            String existingName = material.optString("name", "");
            String existingPath = material.optString("path", "");
            if (runtimeName.equalsIgnoreCase(existingName) || runtimePath.equalsIgnoreCase(existingPath)) {
                material.put("name", runtimeName);
                material.put("path", runtimePath);
                material.put("transparent", transparent || opacity < 0.999f);
                material.put("doubleSided", doubleSided);
                updated = true;
                break;
            }
        }

        if (!updated) {
            JSONObject material = new JSONObject();
            material.put("name", runtimeName);
            material.put("path", runtimePath);
            material.put("transparent", transparent || opacity < 0.999f);
            material.put("doubleSided", doubleSided);
            materials.put(material);
        }

        Files.writeString(indexFile.toPath(), root.toString(2), StandardCharsets.UTF_8);
    }

    private static void removeIndexEntry(File documentFile, String resourcesFolder) throws IOException {
        File indexFile = new File(new File(resourcesFolder, RUNTIME_FOLDER), "materials-ext.json");
        if (!indexFile.exists()) {
            return;
        }

        String content = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
        JSONObject root = content == null || content.isBlank() ? new JSONObject() : new JSONObject(content);
        JSONArray materials = root.optJSONArray("materials");
        if (materials == null) {
            return;
        }

        String runtimeName = getRuntimeName(documentFile);
        String runtimePath = getRuntimeAssetBase(documentFile) + ".j3m";
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < materials.length(); i++) {
            JSONObject material = materials.optJSONObject(i);
            if (material == null) {
                continue;
            }
            String existingName = material.optString("name", "");
            String existingPath = material.optString("path", "");
            if (!runtimeName.equalsIgnoreCase(existingName) && !runtimePath.equalsIgnoreCase(existingPath)) {
                filtered.put(material);
            }
        }
        root.put("materials", filtered);
        Files.writeString(indexFile.toPath(), root.toString(2), StandardCharsets.UTF_8);
    }

    private String buildMaterialInstance(String runtimeName, String diffusePath, String normalPath, String glowPath) {
        ColorRGBA diffuse = baseColor.clone();
        diffuse.a = opacity;
        ColorRGBA ambient = new ColorRGBA(
                clamp(diffuse.r * ambientStrength, 0f, 1f),
                clamp(diffuse.g * ambientStrength, 0f, 1f),
                clamp(diffuse.b * ambientStrength, 0f, 1f),
                opacity
        );
        float spec = clamp(specularStrength, 0f, 2f);
        ColorRGBA specular = new ColorRGBA(spec, spec, spec, 1f);
        ColorRGBA glow = new ColorRGBA(
                clamp(glowColor.r * glowStrength, 0f, 4f),
                clamp(glowColor.g * glowStrength, 0f, 4f),
                clamp(glowColor.b * glowStrength, 0f, 4f),
                opacity
        );

        StringBuilder sb = new StringBuilder();
        sb.append("Material ").append(runtimeName).append(" : Common/MatDefs/Light/Lighting.j3md {\n");
        sb.append("    MaterialParameters {\n");
        sb.append("        UseMaterialColors : true\n");
        sb.append("        Ambient : ").append(colorLine(ambient)).append("\n");
        sb.append("        Diffuse : ").append(colorLine(diffuse)).append("\n");
        sb.append("        Specular : ").append(colorLine(specular)).append("\n");
        sb.append("        Shininess : ").append(shininess).append("\n");
        sb.append("        GlowColor : ").append(colorLine(glow)).append("\n");
        if (alphaDiscardThreshold > 0f) {
            sb.append("        AlphaDiscardThreshold : ").append(alphaDiscardThreshold).append("\n");
        }
        if (!diffusePath.isBlank()) {
            sb.append("        DiffuseMap : ").append(diffusePath).append("\n");
        }
        if (!normalPath.isBlank()) {
            sb.append("        NormalMap : ").append(normalPath).append("\n");
        }
        if (!glowPath.isBlank()) {
            sb.append("        GlowMap : ").append(glowPath).append("\n");
        }
        sb.append("    }\n");
        if (doubleSided || transparent || opacity < 0.999f) {
            sb.append("\n");
            sb.append("    AdditionalRenderState {\n");
            if (doubleSided) {
                sb.append("        FaceCull Off\n");
            }
            if (transparent || opacity < 0.999f) {
                sb.append("        Blend Alpha\n");
            }
            sb.append("    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private TextureExport exportTexture(File outputDir, String runtimeName, String prefix, String originalPath,
                                        String resourcesFolder) throws IOException {
        if (originalPath == null || originalPath.isBlank()) {
            return TextureExport.EMPTY;
        }

        File source = resolveTextureSource(originalPath, resourcesFolder);
        if (source == null || !source.isFile()) {
            return new TextureExport(originalPath);
        }

        String fileName = prefix + "_" + source.getName();
        File dest = new File(outputDir, fileName);
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return new TextureExport(RUNTIME_FOLDER + "/" + runtimeName + "/" + fileName);
    }

    private File resolveTextureSource(String originalPath, String resourcesFolder) {
        if (originalPath == null || originalPath.isBlank()) {
            return null;
        }

        File direct = new File(originalPath);
        if (direct.isFile()) {
            return direct;
        }

        File project = resourcesFolder == null ? null : new File(resourcesFolder, originalPath);
        if (project != null && project.isFile()) {
            return project;
        }

        File defaults = new File("./resources", originalPath);
        if (defaults.isFile()) {
            return defaults;
        }

        File bundledDefaults = new File("./resources-basic/resources", originalPath);
        if (bundledDefaults.isFile()) {
            return bundledDefaults;
        }

        return null;
    }

    private static JSONArray toArray(ColorRGBA color) {
        return new JSONArray(new float[]{color.r, color.g, color.b, color.a});
    }

    private static ColorRGBA parseColor(JSONArray data, ColorRGBA fallback) {
        if (data == null || data.length() < 4) {
            return fallback.clone();
        }
        return new ColorRGBA(
                (float) data.optDouble(0, fallback.r),
                (float) data.optDouble(1, fallback.g),
                (float) data.optDouble(2, fallback.b),
                (float) data.optDouble(3, fallback.a)
        );
    }

    private static MaterialPreviewShape parsePreviewShape(String raw) {
        for (MaterialPreviewShape shape : MaterialPreviewShape.values()) {
            if (shape.name().equalsIgnoreCase(raw)) {
                return shape;
            }
        }
        return MaterialPreviewShape.BOX;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String sanitizePath(String raw) {
        return raw == null ? "" : raw.trim().replace("\\", "/");
    }

    private static String colorLine(ColorRGBA color) {
        return color.r + " " + color.g + " " + color.b + " " + color.a;
    }

    private static final class TextureExport {
        static final TextureExport EMPTY = new TextureExport("");

        final String runtimePath;

        private TextureExport(String runtimePath) {
            this.runtimePath = runtimePath == null ? "" : runtimePath;
        }
    }
}
