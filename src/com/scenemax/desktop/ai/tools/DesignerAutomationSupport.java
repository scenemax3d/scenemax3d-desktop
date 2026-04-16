package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.EditorTabPanel;
import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.ToolPaths;
import com.scenemax.designer.DesignerDocument;
import com.scenemax.designer.effekseer.EffekseerEffectDocument;
import com.scenemax.designer.material.MaterialDocument;
import com.scenemax.designer.shader.EnvironmentShaderDocument;
import com.scenemax.designer.shader.ShaderDocument;
import com.scenemaxeng.common.ui.model.UIDocument;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class DesignerAutomationSupport {

    private static final String[] BUILTIN_MATERIALS = {
            "pond", "rock", "rock2", "brickwall", "dirt", "grass", "road", "alpha", "alpha2"
    };

    private DesignerAutomationSupport() {
    }

    static Path resolvePathOrActive(SceneMaxToolContext context, JSONObject arguments, String defaultBase) throws IOException {
        String rawPath = arguments.optString("path", "").trim();
        if (!rawPath.isEmpty()) {
            return ToolPaths.resolvePath(context, rawPath, arguments.optString("base", defaultBase));
        }
        MainApp host = context.getHost();
        if (host == null) {
            throw new IOException("Path is required when the IDE host is not available.");
        }
        JSONObject snapshot = host.getAutomationActiveDocumentSnapshot();
        String activePath = snapshot.optString("path", "").trim();
        if (activePath.isEmpty()) {
            throw new IOException("Path is required because there is no active document.");
        }
        return ToolPaths.resolvePath(context, activePath, "workspace");
    }

    static boolean isSupportedJsonDesigner(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".smdesign")
                || lower.endsWith(".smui")
                || lower.endsWith(".mat")
                || lower.endsWith(".smshader")
                || lower.endsWith(".smenvshader")
                || lower.endsWith(".smeffectdesign");
    }

    static boolean isSceneDesigner(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".smdesign");
    }

    static String kindFor(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".smdesign")) {
            return "scene_designer";
        }
        if (lower.endsWith(".smui")) {
            return "ui_designer";
        }
        if (lower.endsWith(".mat")) {
            return "material_designer";
        }
        if (lower.endsWith(".smshader")) {
            return "shader_designer";
        }
        if (lower.endsWith(".smenvshader")) {
            return "environment_shader_designer";
        }
        if (lower.endsWith(".smeffectdesign")) {
            return "effect_designer";
        }
        return "unknown";
    }

    static void ensureSupportedJsonDesigner(Path path) throws IOException {
        if (!isSupportedJsonDesigner(path)) {
            throw new IOException("Unsupported designer JSON file: " + path.getFileName());
        }
    }

    static void ensureSceneDesigner(Path path) throws IOException {
        if (!isSceneDesigner(path)) {
            throw new IOException("This tool only supports .smdesign scene designer documents.");
        }
    }

    static JSONObject readJson(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return new JSONObject(content);
    }

    static void writeJson(Path path, JSONObject root) throws IOException {
        Files.writeString(path, root.toString(2), StandardCharsets.UTF_8);
    }

    static void ensureDiskFileExists(Path path) throws IOException {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IOException("File does not exist: " + path);
        }
    }

    static void ensureNotDirtyInIde(SceneMaxToolContext context, Path path, boolean force) throws IOException {
        if (force) {
            return;
        }
        MainApp host = context.getHost();
        if (host == null || host.getEditorTabPanel() == null) {
            return;
        }
        EditorTabPanel tabs = host.getEditorTabPanel();
        if (tabs.isTabDirty(path.toString())) {
            throw new IOException("The open IDE tab has unsaved changes. Save it first or pass force=true.");
        }
    }

    static List<String> getAvailableMaterialNames(SceneMaxToolContext context) throws IOException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String builtin : BUILTIN_MATERIALS) {
            names.add(builtin);
        }
        Path resourcesRoot = context.getResourcesRoot();
        if (resourcesRoot == null) {
            return new ArrayList<>(names);
        }
        Path index = resourcesRoot.resolve("material").resolve("materials-ext.json");
        if (!Files.exists(index)) {
            return new ArrayList<>(names);
        }
        JSONObject root = readJson(index);
        JSONArray materials = root.optJSONArray("materials");
        if (materials != null) {
            for (int i = 0; i < materials.length(); i++) {
                JSONObject material = materials.optJSONObject(i);
                if (material == null) {
                    continue;
                }
                String name = material.optString("name", "").trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    static List<String> getAvailableShaderNames(SceneMaxToolContext context) throws IOException {
        return readNamedIndex(context, "shaders/shaders-ext.json", "shaders");
    }

    static List<String> getAvailableEnvironmentShaderNames(SceneMaxToolContext context) throws IOException {
        return readNamedIndex(context, "environment_shaders/environment-shaders-ext.json", "environmentShaders");
    }

    private static List<String> readNamedIndex(SceneMaxToolContext context, String relativeIndexPath, String arrayName) throws IOException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Path resourcesRoot = context.getResourcesRoot();
        if (resourcesRoot == null) {
            return new ArrayList<>(names);
        }
        Path index = resourcesRoot.resolve(relativeIndexPath.replace("/", java.io.File.separator));
        if (!Files.exists(index)) {
            return new ArrayList<>(names);
        }
        JSONObject root = readJson(index);
        JSONArray items = root.optJSONArray(arrayName);
        if (items == null) {
            return new ArrayList<>(names);
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = item.optString("name", "").trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    static JSONArray toJsonArray(Collection<String> values) {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            array.put(value);
        }
        return array;
    }

    static void ensureEntityIds(JSONObject entity) {
        if (entity == null) {
            return;
        }
        if (entity.optString("id", "").trim().isEmpty()) {
            entity.put("id", UUID.randomUUID().toString());
        }
        JSONArray children = entity.optJSONArray("children");
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child != null) {
                ensureEntityIds(child);
            }
        }
    }

    static JSONObject findEntity(JSONArray entities, String entityId, String entityName) {
        if (entities == null) {
            return null;
        }
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                continue;
            }
            if (!entityId.isBlank() && entityId.equals(entity.optString("id"))) {
                return entity;
            }
            if (!entityName.isBlank() && entityName.equals(entity.optString("name"))) {
                return entity;
            }
            JSONObject childMatch = findEntity(entity.optJSONArray("children"), entityId, entityName);
            if (childMatch != null) {
                return childMatch;
            }
        }
        return null;
    }

    static JSONObject validateDocument(SceneMaxToolContext context, Path path) throws Exception {
        ensureDiskFileExists(path);
        ensureSupportedJsonDesigner(path);

        JSONArray errors = new JSONArray();
        JSONArray warnings = new JSONArray();
        String kind = kindFor(path);

        if ("scene_designer".equals(kind)) {
            DesignerDocument.load(path.toFile());
            JSONObject root = readJson(path);
            validateSceneDocument(context, root, errors, warnings);
        } else if ("ui_designer".equals(kind)) {
            UIDocument doc = UIDocument.load(path.toFile());
            List<String> duplicates = doc.validateUniqueNames();
            for (String duplicate : duplicates) {
                errors.put("Duplicate UI name: " + duplicate);
            }
        } else if ("material_designer".equals(kind)) {
            MaterialDocument.load(path.toFile());
        } else if ("shader_designer".equals(kind)) {
            ShaderDocument.load(path.toFile());
        } else if ("environment_shader_designer".equals(kind)) {
            EnvironmentShaderDocument.load(path.toFile());
        } else if ("effect_designer".equals(kind)) {
            EffekseerEffectDocument.load(path.toFile());
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("kind", kind);
        data.put("valid", errors.length() == 0);
        data.put("errors", errors);
        data.put("warnings", warnings);
        return data;
    }

    static void validateSceneDocument(SceneMaxToolContext context, JSONObject root, JSONArray errors, JSONArray warnings) throws IOException {
        JSONArray entities = root.optJSONArray("entities");
        if (entities == null) {
            errors.put("Scene document is missing an 'entities' array.");
            return;
        }

        Set<String> materialNames = new LinkedHashSet<>(getAvailableMaterialNames(context));
        Set<String> shaderNames = new LinkedHashSet<>(getAvailableShaderNames(context));
        Set<String> environmentShaderNames = new LinkedHashSet<>(getAvailableEnvironmentShaderNames(context));

        String envShader = root.optString("sceneEnvironmentShader", "").trim();
        if (!envShader.isEmpty() && !environmentShaderNames.contains(envShader)) {
            errors.put("Unknown environment shader '" + envShader + "'.");
        }

        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                errors.put("Entity at index " + i + " is not a JSON object.");
                continue;
            }
            validateSceneEntity(entity, materialNames, shaderNames, errors, warnings);
        }
    }

    private static void validateSceneEntity(JSONObject entity, Set<String> materialNames, Set<String> shaderNames,
                                            JSONArray errors, JSONArray warnings) {
        String label = entity.optString("name", entity.optString("id", "(unnamed entity)"));
        String material = entity.optString("material", "").trim();
        if (!material.isEmpty() && !materialNames.contains(material)) {
            errors.put("Unknown material '" + material + "' on entity '" + label + "'.");
        }
        String shader = entity.optString("shader", "").trim();
        if (!shader.isEmpty() && !shaderNames.contains(shader)) {
            errors.put("Unknown shader '" + shader + "' on entity '" + label + "'.");
        }
        if (entity.optString("id", "").trim().isEmpty()) {
            warnings.put("Entity '" + label + "' is missing an id.");
        }
        JSONArray children = entity.optJSONArray("children");
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child == null) {
                errors.put("Child entity at index " + i + " under '" + label + "' is not a JSON object.");
                continue;
            }
            validateSceneEntity(child, materialNames, shaderNames, errors, warnings);
        }
    }
}
