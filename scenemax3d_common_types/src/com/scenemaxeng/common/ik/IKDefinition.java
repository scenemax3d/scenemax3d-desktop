package com.scenemaxeng.common.ik;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class IKDefinition {
    public static final String FILE_EXTENSION = ".smik";
    public static final String LEGACY_FILE_EXTENSION = ".ik.json";
    public static final String SCHEMA_VERSION = "1.0";

    private String id = "ik_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private String name = "IK Definition";
    private String targetModelId = "";
    private final List<IKLayerDefinition> layers = new ArrayList<>();
    private JSONObject designerMetadata = new JSONObject();

    public IKDefinition() {
    }

    public static IKDefinition createTemplate(String displayName, String solverType) {
        IKDefinition definition = new IKDefinition();
        String title = displayName == null || displayName.trim().isEmpty() ? "IK Definition" : displayName.trim();
        definition.setName(title);
        definition.setId(slugId(title));
        IKLayerDefinition layer = new IKLayerDefinition();
        layer.setId("layer_" + slugBody(title));
        layer.setName(title);
        layer.setSolverType(solverType == null || solverType.trim().isEmpty()
                ? IKLayerDefinition.SOLVER_TWO_BONE
                : solverType.trim());
        definition.layers.add(layer);
        return definition;
    }

    public JSONObject toJSON() {
        JSONArray layerArray = new JSONArray();
        getLayers().stream()
                .sorted(Comparator.comparingInt(IKLayerDefinition::getPriority))
                .forEach(layer -> layerArray.put(layer.toJSON()));

        return new JSONObject()
                .put("type", "SceneMaxIKDefinition")
                .put("schemaVersion", SCHEMA_VERSION)
                .put("id", id)
                .put("name", name)
                .put("targetModelId", targetModelId)
                .put("layers", layerArray)
                .put("designerMetadata", designerMetadata == null ? new JSONObject() : designerMetadata);
    }

    public static IKDefinition fromJSON(JSONObject json) {
        IKDefinition definition = new IKDefinition();
        definition.layers.clear();
        if (json == null) {
            return definition;
        }
        definition.id = json.optString("id", definition.id);
        definition.name = json.optString("name", definition.name);
        definition.targetModelId = json.optString("targetModelId", definition.targetModelId);

        JSONArray layerArray = json.optJSONArray("layers");
        if (layerArray != null) {
            for (int i = 0; i < layerArray.length(); i++) {
                definition.layers.add(IKLayerDefinition.fromJSON(layerArray.optJSONObject(i)));
            }
        } else if (json.has("solverType")) {
            IKLayerDefinition legacyLayer = IKLayerDefinition.fromJSON(json);
            legacyLayer.setId(json.optString("id", "layer_0"));
            legacyLayer.setName(json.optString("name", "IK Layer"));
            definition.layers.add(legacyLayer);
        }

        definition.designerMetadata = json.optJSONObject("designerMetadata");
        if (definition.designerMetadata == null) {
            definition.designerMetadata = new JSONObject();
        }
        return definition;
    }

    public static IKDefinition load(File file) throws IOException {
        return fromJSON(new JSONObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8)));
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtils.writeStringToFile(file, toJSON().toString(2), StandardCharsets.UTF_8);
    }

    public static void writeTemplateFile(File file, String displayName, String solverType) throws IOException {
        createTemplate(displayName, solverType).save(file);
    }

    public IKValidationResult validate() {
        IKValidationResult result = new IKValidationResult();
        if (id == null || id.trim().isEmpty()) {
            result.addError("id", "IK id is required.");
        }
        if (name == null || name.trim().isEmpty()) {
            result.addError("name", "IK name is required.");
        }
        if (layers.isEmpty()) {
            result.addError("layers", "At least one IK layer is required.");
        }
        for (int i = 0; i < layers.size(); i++) {
            layers.get(i).validate(result, "layers[" + i + "]");
        }
        return result;
    }

    private static String slugId(String value) {
        return "ik_" + slugBody(value);
    }

    private static String slugBody(String value) {
        String slug = value == null ? "definition" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "definition" : slug;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetModelId() {
        return targetModelId;
    }

    public void setTargetModelId(String targetModelId) {
        this.targetModelId = targetModelId;
    }

    public List<IKLayerDefinition> getLayers() {
        return layers;
    }

    public JSONObject getDesignerMetadata() {
        if (designerMetadata == null) {
            designerMetadata = new JSONObject();
        }
        return designerMetadata;
    }
}
