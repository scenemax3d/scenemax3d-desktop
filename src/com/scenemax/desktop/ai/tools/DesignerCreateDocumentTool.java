package com.scenemax.desktop.ai.tools;

import com.scenemax.designer.DesignerDocument;
import com.scenemax.designer.material.MaterialDocument;
import com.scenemax.designer.material.MaterialTemplatePreset;
import com.scenemax.designer.shader.EnvironmentShaderDocument;
import com.scenemax.designer.shader.EnvironmentShaderTemplatePreset;
import com.scenemax.designer.shader.ShaderDocument;
import com.scenemax.designer.shader.ShaderTemplatePreset;
import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import com.scenemaxeng.common.ui.model.UIDocument;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;

public class DesignerCreateDocumentTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.create_document";
    }

    @Override
    public String getDescription() {
        return "Creates a new scene, UI, material, shader, or environment-shader document.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("directoryPath", new JSONObject().put("type", "string"));
        properties.put("base", new JSONObject().put("type", "string"));
        properties.put("kind", new JSONObject().put("type", "string")
                .put("description", "scene, ui, shader, environment_shader, or material"));
        properties.put("fileName", new JSONObject().put("type", "string"));
        properties.put("preset", new JSONObject().put("type", "string").put("description", "Optional starter preset name."));
        properties.put("openInEditor", new JSONObject().put("type", "boolean"));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("directoryPath").put("kind").put("fileName"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path directory = ToolPaths.resolvePath(context, requireString(arguments, "directoryPath"), optionalString(arguments, "base", "scripts"));
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("directoryPath is not a directory: " + directory);
        }

        String kind = requireString(arguments, "kind").toLowerCase();
        String fileName = requireString(arguments, "fileName");
        String preset = optionalString(arguments, "preset", "");
        Path file = createDocument(directory, kind, fileName, preset);

        MainApp host = context.getHost();
        if (host != null) {
            host.refreshWorkspaceViews();
            if (optionalBoolean(arguments, "openInEditor", true)) {
                host.openFileFromAutomation(file.toFile());
            }
        }

        JSONObject data = new JSONObject();
        data.put("path", file.toString());
        data.put("kind", kind);
        return SceneMaxToolResult.success("Created " + kind + " document " + file.getFileName(), data);
    }

    private Path createDocument(Path directory, String kind, String fileName, String preset) throws Exception {
        switch (kind) {
            case "scene":
                Path sceneFile = directory.resolve(ensureExtension(fileName, ".smdesign"));
                DesignerDocument.writeEmptyFile(sceneFile.toFile());
                return sceneFile;
            case "ui":
                Path uiFile = directory.resolve(ensureExtension(fileName, ".smui"));
                UIDocument.writeEmptyFile(uiFile.toFile());
                return uiFile;
            case "shader":
                Path shaderFile = directory.resolve(ensureExtension(fileName, ".smshader"));
                ShaderDocument.writeEmptyFile(shaderFile.toFile(), ShaderTemplatePreset.fromName(preset));
                return shaderFile;
            case "environment_shader":
                Path envShaderFile = directory.resolve(ensureExtension(fileName, ".smenvshader"));
                EnvironmentShaderDocument.writeEmptyFile(envShaderFile.toFile(), EnvironmentShaderTemplatePreset.fromName(preset));
                return envShaderFile;
            case "material":
                Path materialFile = directory.resolve(ensureExtension(fileName, ".mat"));
                MaterialDocument.writeEmptyFile(materialFile.toFile(), parseMaterialPreset(preset));
                return materialFile;
            default:
                throw new IllegalArgumentException("Unsupported document kind: " + kind);
        }
    }

    private MaterialTemplatePreset parseMaterialPreset(String preset) {
        if (preset == null || preset.isBlank()) {
            return MaterialTemplatePreset.MATTE_PAINT;
        }
        for (MaterialTemplatePreset candidate : MaterialTemplatePreset.values()) {
            if (candidate.name().equalsIgnoreCase(preset) || candidate.toString().equalsIgnoreCase(preset)) {
                return candidate;
            }
        }
        return MaterialTemplatePreset.MATTE_PAINT;
    }

    private String ensureExtension(String fileName, String extension) {
        return fileName.toLowerCase().endsWith(extension) ? fileName : fileName + extension;
    }
}
