package com.scenemax.designer.shader;

import com.jme3.math.ColorRGBA;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;

public class ShaderDocument {

    private static final int FORMAT_VERSION = 1;

    private final String filePath;
    private ShaderTemplatePreset template = ShaderTemplatePreset.TEXTURE_TINT;
    private final EnumSet<ShaderBlockType> blocks = EnumSet.of(ShaderBlockType.TINT);
    private ShaderPreviewTarget previewTarget = ShaderPreviewTarget.BOX;
    private ColorRGBA mainColor = new ColorRGBA(1f, 1f, 1f, 1f);
    private float glowStrength = 0.15f;
    private float pulseSpeed = 0.55f;
    private float transparency = 0.05f;
    private float edgeWidth = 0.15f;
    private float scrollSpeed = 0.35f;
    private String texturePath = "";
    private String previewModelName = "";

    public ShaderDocument(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public ShaderTemplatePreset getTemplate() {
        return template;
    }

    public void setTemplate(ShaderTemplatePreset template) {
        this.template = template != null ? template : ShaderTemplatePreset.TEXTURE_TINT;
    }

    public EnumSet<ShaderBlockType> getBlocks() {
        return blocks;
    }

    public ShaderPreviewTarget getPreviewTarget() {
        return previewTarget;
    }

    public void setPreviewTarget(ShaderPreviewTarget previewTarget) {
        this.previewTarget = previewTarget != null ? previewTarget : ShaderPreviewTarget.BOX;
    }

    public ColorRGBA getMainColor() {
        return mainColor.clone();
    }

    public void setMainColor(ColorRGBA mainColor) {
        this.mainColor = mainColor != null ? mainColor.clone() : new ColorRGBA(1f, 1f, 1f, 1f);
    }

    public float getGlowStrength() {
        return glowStrength;
    }

    public void setGlowStrength(float glowStrength) {
        this.glowStrength = glowStrength;
    }

    public float getPulseSpeed() {
        return pulseSpeed;
    }

    public void setPulseSpeed(float pulseSpeed) {
        this.pulseSpeed = pulseSpeed;
    }

    public float getTransparency() {
        return transparency;
    }

    public void setTransparency(float transparency) {
        this.transparency = transparency;
    }

    public float getEdgeWidth() {
        return edgeWidth;
    }

    public void setEdgeWidth(float edgeWidth) {
        this.edgeWidth = edgeWidth;
    }

    public float getScrollSpeed() {
        return scrollSpeed;
    }

    public void setScrollSpeed(float scrollSpeed) {
        this.scrollSpeed = scrollSpeed;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath != null ? texturePath.trim().replace("\\", "/") : "";
    }

    public String getPreviewModelName() {
        return previewModelName;
    }

    public void setPreviewModelName(String previewModelName) {
        this.previewModelName = previewModelName != null ? previewModelName.trim() : "";
    }

    public ShaderDocument copy() {
        ShaderDocument copy = new ShaderDocument(filePath);
        copy.template = template;
        copy.blocks.addAll(blocks);
        copy.previewTarget = previewTarget;
        copy.mainColor = mainColor.clone();
        copy.glowStrength = glowStrength;
        copy.pulseSpeed = pulseSpeed;
        copy.transparency = transparency;
        copy.edgeWidth = edgeWidth;
        copy.scrollSpeed = scrollSpeed;
        copy.texturePath = texturePath;
        copy.previewModelName = previewModelName;
        return copy;
    }

    public void save(File file) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("template", template.name());
        root.put("previewTarget", previewTarget.name());
        root.put("mainColor", new JSONArray(new float[]{
                mainColor.r, mainColor.g, mainColor.b, mainColor.a
        }));
        root.put("glowStrength", glowStrength);
        root.put("pulseSpeed", pulseSpeed);
        root.put("transparency", transparency);
        root.put("edgeWidth", edgeWidth);
        root.put("scrollSpeed", scrollSpeed);
        root.put("texture", texturePath);
        root.put("previewModelName", previewModelName);

        JSONArray blocksJson = new JSONArray();
        for (ShaderBlockType block : blocks) {
            blocksJson.put(block.name());
        }
        root.put("blocks", blocksJson);

        Files.write(file.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    public void exportRuntimeAssets(File shaderFile, String resourcesFolder) throws IOException {
        if (resourcesFolder == null || resourcesFolder.trim().isEmpty()) {
            return;
        }

        File outputDir = getRuntimeFolder(shaderFile, resourcesFolder);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String assetBase = getRuntimeAssetBase(shaderFile);
        String runtimeName = getRuntimeName(shaderFile);

        Files.writeString(new File(outputDir, runtimeName + ".vert").toPath(),
                buildVertexShader(), StandardCharsets.UTF_8);
        Files.writeString(new File(outputDir, runtimeName + ".frag").toPath(),
                buildFragmentShader(), StandardCharsets.UTF_8);
        Files.writeString(new File(outputDir, runtimeName + ".j3md").toPath(),
                buildMaterialDefinition(assetBase, runtimeName), StandardCharsets.UTF_8);
        Files.writeString(new File(outputDir, runtimeName + ".j3m").toPath(),
                buildMaterialInstance(assetBase, runtimeName), StandardCharsets.UTF_8);
        updateShaderIndex(shaderFile, resourcesFolder);
    }

    public static ShaderDocument load(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);

        ShaderDocument doc = new ShaderDocument(file.getAbsolutePath());
        doc.template = ShaderTemplatePreset.fromName(root.optString("template", ShaderTemplatePreset.TEXTURE_TINT.name()));
        doc.previewTarget = ShaderPreviewTarget.valueOf(root.optString("previewTarget", ShaderPreviewTarget.BOX.name()));

        JSONArray color = root.optJSONArray("mainColor");
        if (color != null && color.length() >= 4) {
            doc.mainColor = new ColorRGBA(
                    (float) color.optDouble(0, 1),
                    (float) color.optDouble(1, 1),
                    (float) color.optDouble(2, 1),
                    (float) color.optDouble(3, 1)
            );
        }

        doc.glowStrength = (float) root.optDouble("glowStrength", 0.15);
        doc.pulseSpeed = (float) root.optDouble("pulseSpeed", 0.55);
        doc.transparency = (float) root.optDouble("transparency", 0.05);
        doc.edgeWidth = (float) root.optDouble("edgeWidth", 0.15);
        doc.scrollSpeed = (float) root.optDouble("scrollSpeed", 0.35);
        doc.texturePath = root.optString("texture", "");
        doc.previewModelName = root.optString("previewModelName", "");

        doc.blocks.clear();
        JSONArray blocksJson = root.optJSONArray("blocks");
        if (blocksJson != null) {
            for (int i = 0; i < blocksJson.length(); i++) {
                try {
                    doc.blocks.add(ShaderBlockType.valueOf(blocksJson.getString(i)));
                } catch (Exception ignored) {
                }
            }
        }
        if (doc.blocks.isEmpty()) {
            doc.template.applyTo(doc);
        }

        return doc;
    }

    public static ShaderDocument createFromTemplate(String filePath, ShaderTemplatePreset preset) {
        ShaderDocument doc = new ShaderDocument(filePath);
        preset.applyTo(doc);
        return doc;
    }

    public static void writeEmptyFile(File file, ShaderTemplatePreset preset) throws IOException {
        ShaderDocument.createFromTemplate(file.getAbsolutePath(), preset).save(file);
    }

    public static File getRuntimeFolder(File shaderFile, String resourcesFolder) {
        return new File(resourcesFolder, "shaders/" + getRuntimeName(shaderFile));
    }

    public static String getRuntimeAssetBase(File shaderFile) {
        String runtimeName = getRuntimeName(shaderFile);
        return "shaders/" + runtimeName + "/" + runtimeName;
    }

    public static String getRuntimeName(File shaderFile) {
        String baseName = shaderFile.getName();
        if (baseName.toLowerCase().endsWith(".smshader")) {
            baseName = baseName.substring(0, baseName.length() - ".smshader".length());
        }
        return baseName.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private void updateShaderIndex(File shaderFile, String resourcesFolder) throws IOException {
        File shadersRoot = new File(resourcesFolder, "shaders");
        if (!shadersRoot.exists()) {
            shadersRoot.mkdirs();
        }

        File indexFile = new File(shadersRoot, "shaders-ext.json");
        JSONObject root;
        if (indexFile.exists()) {
            String content = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
            root = content == null || content.isBlank() ? new JSONObject() : new JSONObject(content);
        } else {
            root = new JSONObject();
        }

        JSONArray shaders = root.optJSONArray("shaders");
        if (shaders == null) {
            shaders = new JSONArray();
            root.put("shaders", shaders);
        }

        String runtimeName = getRuntimeName(shaderFile);
        String runtimePath = getRuntimeAssetBase(shaderFile) + ".j3md";
        boolean updated = false;
        for (int i = 0; i < shaders.length(); i++) {
            JSONObject shader = shaders.optJSONObject(i);
            if (shader == null) {
                continue;
            }

            String existingName = shader.optString("name", "");
            String existingPath = shader.optString("path", "");
            if (runtimeName.equalsIgnoreCase(existingName) || runtimePath.equalsIgnoreCase(existingPath)) {
                shader.put("name", runtimeName);
                shader.put("path", runtimePath);
                updated = true;
                break;
            }
        }

        if (!updated) {
            JSONObject shader = new JSONObject();
            shader.put("name", runtimeName);
            shader.put("path", runtimePath);
            shaders.put(shader);
        }

        Files.writeString(indexFile.toPath(), root.toString(2), StandardCharsets.UTF_8);
    }

    private String buildMaterialDefinition(String assetBase, String runtimeName) {
        return "MaterialDef " + runtimeName + " {\n" +
                "\n" +
                "    MaterialParameters {\n" +
                "        Color MainColor\n" +
                "        Float Time : 0\n" +
                "        Float GlowStrength : " + glowStrength + "\n" +
                "        Float PulseSpeed : " + pulseSpeed + "\n" +
                "        Float Transparency : " + transparency + "\n" +
                "        Float EdgeWidth : " + edgeWidth + "\n" +
                "        Float ScrollSpeed : " + scrollSpeed + "\n" +
                "        Boolean VertexColor : false\n" +
                "        Texture2D ColorMap\n" +
                "    }\n" +
                "\n" +
                "    Technique {\n" +
                "        VertexShader GLSL100:   " + assetBase + ".vert\n" +
                "        FragmentShader GLSL100: " + assetBase + ".frag\n" +
                "\n" +
                "        WorldParameters {\n" +
                "            WorldViewProjectionMatrix\n" +
                "            WorldMatrix\n" +
                "        }\n" +
                "\n" +
                "        Defines {\n" +
                "            USE_TEXTURE : ColorMap\n" +
                "            USE_VERTEX_COLOR : VertexColor\n" +
                "        }\n" +
                "\n" +
                "        RenderState {\n" +
                "            Blend Alpha\n" +
                "            FaceCull Off\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
    }

    private String buildMaterialInstance(String assetBase, String runtimeName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Material ").append(runtimeName).append(" : ").append(assetBase).append(".j3md {\n");
        sb.append("    MainColor : ").append(mainColor.r).append(' ').append(mainColor.g).append(' ')
                .append(mainColor.b).append(' ').append(mainColor.a).append("\n");
        sb.append("    Time : 0.0\n");
        sb.append("    GlowStrength : ").append(glowStrength).append("\n");
        sb.append("    PulseSpeed : ").append(pulseSpeed).append("\n");
        sb.append("    Transparency : ").append(transparency).append("\n");
        sb.append("    EdgeWidth : ").append(edgeWidth).append("\n");
        sb.append("    ScrollSpeed : ").append(scrollSpeed).append("\n");
        if (texturePath != null && !texturePath.isBlank()) {
            sb.append("    ColorMap : ").append(texturePath).append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String buildVertexShader() {
        return "uniform mat4 g_WorldViewProjectionMatrix;\n" +
                "attribute vec3 inPosition;\n" +
                "attribute vec2 inTexCoord;\n" +
                "attribute vec3 inNormal;\n" +
                "attribute vec4 inColor;\n" +
                "\n" +
                "varying vec2 vUv;\n" +
                "varying vec3 vWorldNormal;\n" +
                "varying vec3 vLocalPos;\n" +
                "varying vec4 vColor;\n" +
                "\n" +
                "void main() {\n" +
                "    vUv = inTexCoord;\n" +
                "    float normalLen = dot(inNormal, inNormal);\n" +
                "    vWorldNormal = normalLen > 0.0001 ? normalize(inNormal) : vec3(0.0, 0.0, 1.0);\n" +
                "    vLocalPos = inPosition;\n" +
                "    vColor = inColor;\n" +
                "    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);\n" +
                "}\n";
    }

    private String buildFragmentShader() {
        return "#ifdef GL_ES\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "\n" +
                "uniform vec4 m_MainColor;\n" +
                "uniform float m_GlowStrength;\n" +
                "uniform float m_PulseSpeed;\n" +
                "uniform float m_Transparency;\n" +
                "uniform float m_EdgeWidth;\n" +
                "uniform float m_ScrollSpeed;\n" +
                "uniform float m_Time;\n" +
                "#ifdef USE_TEXTURE\n" +
                "uniform sampler2D m_ColorMap;\n" +
                "#endif\n" +
                "\n" +
                "varying vec2 vUv;\n" +
                "varying vec3 vWorldNormal;\n" +
                "varying vec3 vLocalPos;\n" +
                "varying vec4 vColor;\n" +
                "\n" +
                "#ifdef USE_VERTEX_COLOR\n" +
                "const bool ENABLE_VERTEX_COLOR = true;\n" +
                "#else\n" +
                "const bool ENABLE_VERTEX_COLOR = false;\n" +
                "#endif\n" +
                "\n" +
                "const bool ENABLE_TINT = " + blocks.contains(ShaderBlockType.TINT) + ";\n" +
                "const bool ENABLE_GLOW = " + blocks.contains(ShaderBlockType.GLOW) + ";\n" +
                "const bool ENABLE_PULSE = " + blocks.contains(ShaderBlockType.PULSE) + ";\n" +
                "const bool ENABLE_DISSOLVE = " + blocks.contains(ShaderBlockType.DISSOLVE) + ";\n" +
                "const bool ENABLE_RIM = " + blocks.contains(ShaderBlockType.RIM_LIGHT) + ";\n" +
                "const bool ENABLE_SCROLL_UV = " + blocks.contains(ShaderBlockType.SCROLL_UV) + ";\n" +
                "const bool ENABLE_FLICKER = " + blocks.contains(ShaderBlockType.FLICKER) + ";\n" +
                "const bool ENABLE_WATER = " + blocks.contains(ShaderBlockType.WATER_WAVES) + ";\n" +
                "const bool ENABLE_HOLOGRAM = " + blocks.contains(ShaderBlockType.HOLOGRAM_LINES) + ";\n" +
                "const bool ENABLE_TOON = " + blocks.contains(ShaderBlockType.TOON_RAMP) + ";\n" +
                "\n" +
                "float hash(vec2 p) {\n" +
                "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);\n" +
                "}\n" +
                "\n" +
                "void main() {\n" +
                "    vec2 uv = vUv;\n" +
                "    if (ENABLE_SCROLL_UV) {\n" +
                "        uv.x += m_Time * m_ScrollSpeed * 0.08;\n" +
                "        uv.y += m_Time * m_ScrollSpeed * 0.03;\n" +
                "    }\n" +
                "    if (ENABLE_WATER) {\n" +
                "        uv.y += sin((uv.x * 12.0) + (m_Time * 1.8 * max(m_ScrollSpeed, 0.15))) * 0.03;\n" +
                "        uv.x += cos((uv.y * 10.0) + (m_Time * 1.4 * max(m_ScrollSpeed, 0.15))) * 0.02;\n" +
                "    }\n" +
                "\n" +
                "    vec4 base = vec4(1.0);\n" +
                "#ifdef USE_TEXTURE\n" +
                "    base *= texture2D(m_ColorMap, uv);\n" +
                "#endif\n" +
                "    if (ENABLE_VERTEX_COLOR) {\n" +
                "        base *= vColor;\n" +
                "    }\n" +
                "    if (ENABLE_TINT) {\n" +
                "        base.rgb *= m_MainColor.rgb;\n" +
                "    }\n" +
                "    base.a *= m_MainColor.a;\n" +
                "\n" +
                "    float pulse = 1.0;\n" +
                "    if (ENABLE_PULSE) {\n" +
                "        pulse = 0.72 + 0.28 * (0.5 + 0.5 * sin(m_Time * max(m_PulseSpeed, 0.05) * 6.28318));\n" +
                "    }\n" +
                "\n" +
                "    float flicker = 1.0;\n" +
                "    if (ENABLE_FLICKER) {\n" +
                "        flicker = 0.82 + 0.18 * hash(vec2(floor(m_Time * 12.0), floor(uv.y * 32.0)));\n" +
                "    }\n" +
                "\n" +
                "    if (ENABLE_HOLOGRAM) {\n" +
                "        float scan = 0.55 + 0.45 * sin((uv.y * 90.0) - (m_Time * 7.0));\n" +
                "        base.rgb += m_MainColor.rgb * scan * 0.18;\n" +
                "    }\n" +
                "\n" +
                "    if (ENABLE_TOON) {\n" +
                "        float lit = dot(normalize(vWorldNormal), normalize(vec3(0.25, 0.75, 0.6))) * 0.5 + 0.5;\n" +
                "        lit = floor(lit * 4.0) / 4.0;\n" +
                "        base.rgb *= mix(0.55, 1.0, lit);\n" +
                "    }\n" +
                "\n" +
                "    float rim = 0.0;\n" +
                "    if (ENABLE_RIM) {\n" +
                "        rim = pow(1.0 - abs(normalize(vWorldNormal).z), 2.2);\n" +
                "    }\n" +
                "\n" +
                "    vec3 color = base.rgb * pulse * flicker;\n" +
                "    if (ENABLE_GLOW) {\n" +
                "        color += m_MainColor.rgb * (m_GlowStrength * (0.35 + pulse * 0.65));\n" +
                "    }\n" +
                "    if (ENABLE_RIM) {\n" +
                "        color += m_MainColor.rgb * rim * (0.15 + m_GlowStrength * 0.25);\n" +
                "    }\n" +
                "\n" +
                "    float alpha = clamp(base.a * (1.0 - m_Transparency), 0.0, 1.0);\n" +
                "    if (ENABLE_DISSOLVE) {\n" +
                "        float dissolveNoise = hash(floor((uv + vLocalPos.xy * 0.3) * 24.0) + floor(m_Time * 1.5));\n" +
                "        float cut = 1.0 - clamp(m_Transparency, 0.0, 1.0);\n" +
                "        float edge = smoothstep(cut - max(m_EdgeWidth, 0.01), cut, dissolveNoise) -\n" +
                "                smoothstep(cut, cut + max(m_EdgeWidth, 0.01), dissolveNoise);\n" +
                "        if (dissolveNoise > cut) {\n" +
                "            discard;\n" +
                "        }\n" +
                "        color += vec3(1.0, 0.85, 0.45) * edge * (0.35 + m_GlowStrength * 0.5);\n" +
                "        alpha = base.a;\n" +
                "    }\n" +
                "\n" +
                "    gl_FragColor = vec4(color, alpha);\n" +
                "}\n";
    }
}
