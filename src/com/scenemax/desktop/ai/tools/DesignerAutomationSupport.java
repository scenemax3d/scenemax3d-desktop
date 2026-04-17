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
import java.util.Comparator;
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

    static final class EntityRef {
        final JSONObject entity;
        final JSONArray parentArray;
        final int index;
        final int depth;

        EntityRef(JSONObject entity, JSONArray parentArray, int index, int depth) {
            this.entity = entity;
            this.parentArray = parentArray;
            this.index = index;
            this.depth = depth;
        }
    }

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

    static JSONObject deepCopyObject(JSONObject source) {
        return source == null ? null : new JSONObject(source.toString());
    }

    static JSONArray deepCopyArray(JSONArray source) {
        return source == null ? null : new JSONArray(source.toString());
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

    static List<String> getAvailableModelNames(SceneMaxToolContext context) throws IOException {
        return readNamedIndex(context, "Models/models-ext.json", "models");
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
        EntityRef ref = findEntityRef(entities, entityId, entityName);
        return ref != null ? ref.entity : null;
    }

    static EntityRef findEntityRef(JSONArray entities, String entityId, String entityName) {
        return findEntityRef(entities, entityId, entityName, 0);
    }

    private static EntityRef findEntityRef(JSONArray entities, String entityId, String entityName, int depth) {
        if (entities == null) {
            return null;
        }
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                continue;
            }
            if (!entityId.isBlank() && entityId.equals(entity.optString("id"))) {
                return new EntityRef(entity, entities, i, depth);
            }
            if (!entityName.isBlank() && entityName.equals(entity.optString("name"))) {
                return new EntityRef(entity, entities, i, depth);
            }
            EntityRef childMatch = findEntityRef(entity.optJSONArray("children"), entityId, entityName, depth + 1);
            if (childMatch != null) {
                return childMatch;
            }
        }
        return null;
    }

    static List<EntityRef> collectEntityRefs(JSONArray entities, Set<String> entityIds, Set<String> entityNames) {
        List<EntityRef> refs = new ArrayList<>();
        collectEntityRefs(entities, entityIds, entityNames, refs, 0, new LinkedHashSet<>());
        refs.sort(Comparator.comparingInt((EntityRef ref) -> ref.depth).reversed()
                .thenComparing((EntityRef ref) -> ref.index, Comparator.reverseOrder()));
        return refs;
    }

    private static void collectEntityRefs(JSONArray entities, Set<String> entityIds, Set<String> entityNames,
                                          List<EntityRef> refs, int depth, Set<String> seenIds) {
        if (entities == null) {
            return;
        }
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                continue;
            }
            String id = entity.optString("id", "").trim();
            String name = entity.optString("name", "").trim();
            boolean match = (!id.isEmpty() && entityIds.contains(id)) || (!name.isEmpty() && entityNames.contains(name));
            if (match) {
                String dedupeKey = !id.isEmpty() ? id : "__name__:" + name + "@" + depth + ":" + i;
                if (seenIds.add(dedupeKey)) {
                    refs.add(new EntityRef(entity, entities, i, depth));
                }
            }
            collectEntityRefs(entity.optJSONArray("children"), entityIds, entityNames, refs, depth + 1, seenIds);
        }
    }

    static JSONArray ensureEntitiesArray(JSONObject root) {
        JSONArray entities = root.optJSONArray("entities");
        if (entities == null) {
            entities = new JSONArray();
            root.put("entities", entities);
        }
        return entities;
    }

    static Set<String> collectStringSet(JSONObject arguments, String pluralKey, String singularKey) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JSONArray array = arguments.optJSONArray(pluralKey);
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        String single = arguments.optString(singularKey, "").trim();
        if (!single.isEmpty()) {
            values.add(single);
        }
        return values;
    }

    static String entityLabel(JSONObject entity) {
        if (entity == null) {
            return "(null entity)";
        }
        String name = entity.optString("name", "").trim();
        if (!name.isEmpty()) {
            return name;
        }
        String id = entity.optString("id", "").trim();
        return id.isEmpty() ? "(unnamed entity)" : id;
    }

    static void applyUpdates(JSONObject entity, JSONObject updates) {
        if (entity == null || updates == null) {
            return;
        }
        for (String key : updates.keySet()) {
            Object value = updates.get(key);
            if (value instanceof JSONObject) {
                entity.put(key, deepCopyObject((JSONObject) value));
            } else if (value instanceof JSONArray) {
                entity.put(key, deepCopyArray((JSONArray) value));
            } else {
                entity.put(key, value);
            }
        }
    }

    static JSONObject createSectionEntity(String name, String id) {
        JSONObject group = new JSONObject();
        group.put("id", id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim());
        group.put("name", name == null || name.isBlank() ? "group" : name.trim());
        group.put("type", "SECTION");
        group.put("position", new JSONArray().put(0).put(0).put(0));
        group.put("rotation", new JSONArray().put(0).put(0).put(0).put(1));
        group.put("scale", new JSONArray().put(1).put(1).put(1));
        group.put("children", new JSONArray());
        return group;
    }

    static void regenerateEntityIds(JSONObject entity) {
        if (entity == null) {
            return;
        }
        entity.put("id", UUID.randomUUID().toString());
        JSONArray children = entity.optJSONArray("children");
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child != null) {
                regenerateEntityIds(child);
            }
        }
    }

    static void applyNameSuffix(JSONObject entity, String suffix) {
        if (entity == null || suffix == null || suffix.isBlank()) {
            return;
        }
        String name = entity.optString("name", "").trim();
        if (!name.isEmpty()) {
            entity.put("name", name + suffix);
        }
        JSONArray children = entity.optJSONArray("children");
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child != null) {
                applyNameSuffix(child, suffix);
            }
        }
    }

    static void offsetEntity(JSONObject entity, double dx, double dy, double dz) {
        if (entity == null) {
            return;
        }
        JSONArray position = entity.optJSONArray("position");
        if (position != null && position.length() >= 3) {
            position.put(0, position.optDouble(0, 0d) + dx);
            position.put(1, position.optDouble(1, 0d) + dy);
            position.put(2, position.optDouble(2, 0d) + dz);
        }
        JSONArray children = entity.optJSONArray("children");
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child != null) {
                offsetEntity(child, dx, dy, dz);
            }
        }
    }

    static void mirrorEntity(JSONObject entity, String axis, double pivot) {
        if (entity == null) {
            return;
        }
        int axisIndex = axisIndex(axis);
        JSONArray position = entity.optJSONArray("position");
        if (position != null && position.length() >= 3) {
            double current = position.optDouble(axisIndex, 0d);
            position.put(axisIndex, (pivot * 2d) - current);
        }

        JSONArray rotation = entity.optJSONArray("rotation");
        if (rotation != null && rotation.length() >= 4) {
            entity.put("rotation", mirrorQuaternionArray(rotation, axis));
        }

        JSONArray children = entity.optJSONArray("children");
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child != null) {
                mirrorEntity(child, axis, pivot);
            }
        }
    }

    private static int axisIndex(String axis) {
        String normalized = axis == null ? "x" : axis.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "y":
                return 1;
            case "z":
                return 2;
            case "x":
            default:
                return 0;
        }
    }

    private static JSONArray mirrorQuaternionArray(JSONArray rotation, String axis) {
        double x = rotation.optDouble(0, 0d);
        double y = rotation.optDouble(1, 0d);
        double z = rotation.optDouble(2, 0d);
        double w = rotation.optDouble(3, 1d);
        String normalized = axis == null ? "x" : axis.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "y":
                x = -x;
                z = -z;
                break;
            case "z":
                x = -x;
                y = -y;
                break;
            case "x":
            default:
                y = -y;
                z = -z;
                break;
        }
        return new JSONArray().put(x).put(y).put(z).put(w);
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

    static JSONObject validateSceneResources(SceneMaxToolContext context, JSONObject root) throws IOException {
        JSONArray entities = root.optJSONArray("entities");
        LinkedHashSet<String> missingMaterials = new LinkedHashSet<>();
        LinkedHashSet<String> missingShaders = new LinkedHashSet<>();
        LinkedHashSet<String> missingEnvironmentShaders = new LinkedHashSet<>();
        LinkedHashSet<String> missingModels = new LinkedHashSet<>();

        Set<String> materialNames = new LinkedHashSet<>(getAvailableMaterialNames(context));
        Set<String> shaderNames = new LinkedHashSet<>(getAvailableShaderNames(context));
        Set<String> environmentShaderNames = new LinkedHashSet<>(getAvailableEnvironmentShaderNames(context));
        Set<String> modelNames = new LinkedHashSet<>(getAvailableModelNames(context));

        String envShader = root.optString("sceneEnvironmentShader", "").trim();
        if (!envShader.isEmpty() && !environmentShaderNames.contains(envShader)) {
            missingEnvironmentShaders.add(envShader);
        }

        collectMissingSceneResources(entities, materialNames, shaderNames, modelNames,
                missingMaterials, missingShaders, missingModels);

        JSONObject result = new JSONObject();
        result.put("missingMaterials", toJsonArray(missingMaterials));
        result.put("missingShaders", toJsonArray(missingShaders));
        result.put("missingEnvironmentShaders", toJsonArray(missingEnvironmentShaders));
        result.put("missingModels", toJsonArray(missingModels));
        result.put("hasMissingResources",
                !missingMaterials.isEmpty() || !missingShaders.isEmpty()
                        || !missingEnvironmentShaders.isEmpty() || !missingModels.isEmpty());
        return result;
    }

    private static void collectMissingSceneResources(JSONArray entities, Set<String> materialNames, Set<String> shaderNames,
                                                     Set<String> modelNames, Set<String> missingMaterials,
                                                     Set<String> missingShaders, Set<String> missingModels) {
        if (entities == null) {
            return;
        }
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                continue;
            }
            String material = entity.optString("material", "").trim();
            if (!material.isEmpty() && !materialNames.contains(material)) {
                missingMaterials.add(material);
            }
            String shader = entity.optString("shader", "").trim();
            if (!shader.isEmpty() && !shaderNames.contains(shader)) {
                missingShaders.add(shader);
            }
            String type = entity.optString("type", "").trim().toUpperCase(Locale.ROOT);
            String resourcePath = entity.optString("resourcePath", "").trim();
            if ("MODEL".equals(type) && !resourcePath.isEmpty() && !modelNames.contains(resourcePath)) {
                missingModels.add(resourcePath);
            }
            collectMissingSceneResources(entity.optJSONArray("children"), materialNames, shaderNames, modelNames,
                    missingMaterials, missingShaders, missingModels);
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
