package com.scenemaxeng.common.weapons;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class WeaponDefinition {
    public static final String FILE_EXTENSION = ".smweapon";
    public static final String SCHEMA_VERSION = "1.0";

    private String id = "weapon_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private String modelAssetId = "";
    private String defaultPostureId = "default";
    private List<WeaponPostureDefinition> postures = new ArrayList<>();
    private List<WeaponColliderDefinition> colliders = new ArrayList<>();
    private JSONObject designerMetadata = new JSONObject();

    public WeaponDefinition() {
        postures.add(createDefaultPosture());
    }

    public static WeaponDefinition createTemplate(String displayName, String template) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.setId(slugId(displayName == null || displayName.trim().isEmpty() ? "weapon" : displayName.trim()));
        return definition;
    }

    public JSONObject toJSON() {
        JSONArray postureArray = new JSONArray();
        for (WeaponPostureDefinition posture : postures) {
            postureArray.put(posture.toJSON());
        }
        JSONArray colliderArray = new JSONArray();
        for (WeaponColliderDefinition collider : getColliders()) {
            colliderArray.put(collider.toJSON());
        }
        return new JSONObject()
                .put("type", "SceneMaxWeaponDefinition")
                .put("schemaVersion", SCHEMA_VERSION)
                .put("id", id)
                .put("modelAssetId", modelAssetId)
                .put("defaultPostureId", defaultPostureId)
                .put("postures", postureArray)
                .put("colliders", colliderArray)
                .put("designerMetadata", designerMetadata == null ? new JSONObject() : designerMetadata);
    }

    public static WeaponDefinition fromJSON(JSONObject json) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.postures.clear();
        if (json == null) {
            definition.ensurePostureExists();
            return definition;
        }
        definition.id = json.optString("id", definition.id);
        definition.modelAssetId = json.optString("modelAssetId", definition.modelAssetId);
        definition.defaultPostureId = json.optString("defaultPostureId", definition.defaultPostureId);

        JSONArray postureArray = json.optJSONArray("postures");
        if (postureArray != null) {
            for (int i = 0; i < postureArray.length(); i++) {
                definition.postures.add(WeaponPostureDefinition.fromJSON(postureArray.optJSONObject(i)));
            }
        }
        if (definition.postures.isEmpty()) {
            WeaponPostureDefinition legacy = createDefaultPosture();
            legacy.setAttachmentPoint(json.optString("defaultAttachmentPoint", legacy.getAttachmentPoint()));
            legacy.setTransform(WeaponAttachmentTransform.fromJSON(json.optJSONObject("attachmentTransform")));
            definition.postures.add(legacy);
            definition.defaultPostureId = legacy.getId();
        }

        JSONArray colliderArray = json.optJSONArray("colliders");
        if (colliderArray != null) {
            for (int i = 0; i < colliderArray.length(); i++) {
                definition.colliders.add(WeaponColliderDefinition.fromJSON(colliderArray.optJSONObject(i)));
            }
        }

        definition.designerMetadata = json.optJSONObject("designerMetadata");
        if (definition.designerMetadata == null) {
            definition.designerMetadata = new JSONObject();
        }
        definition.ensurePostureExists();
        return definition;
    }

    public static WeaponDefinition load(File file) throws IOException {
        return fromJSON(new JSONObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8)));
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtils.writeStringToFile(file, toJSON().toString(2), StandardCharsets.UTF_8);
    }

    public static void writeTemplateFile(File file, String displayName, String template) throws IOException {
        createTemplate(displayName, template).save(file);
    }

    public WeaponValidationResult validate() {
        WeaponValidationResult result = new WeaponValidationResult();
        if (id == null || id.trim().isEmpty()) {
            result.addError("id", "Weapon id is required.");
        }
        if (modelAssetId == null || modelAssetId.trim().isEmpty()) {
            result.addWarning("modelAssetId", "Weapon model is not assigned. Invisible weapons are allowed, but most weapons should have a model.");
        }
        if (postures == null || postures.isEmpty()) {
            result.addError("postures", "At least one weapon posture is required.");
        } else {
            for (WeaponPostureDefinition posture : postures) {
                posture.validate(result);
            }
        }
        for (WeaponColliderDefinition collider : getColliders()) {
            collider.validate(result);
        }
        return result;
    }

    public WeaponPostureDefinition findPosture(String postureIdOrName) {
        WeaponPostureDefinition posture = findPostureOrNull(postureIdOrName);
        if (posture != null) {
            return posture;
        }
        ensurePostureExists();
        return postures.get(0);
    }

    public WeaponPostureDefinition findPostureOrNull(String postureIdOrName) {
        ensurePostureExists();
        String requested = postureIdOrName == null || postureIdOrName.trim().isEmpty()
                ? defaultPostureId
                : postureIdOrName.trim();
        for (WeaponPostureDefinition posture : postures) {
            if (posture == null) {
                continue;
            }
            if (requested.equalsIgnoreCase(nullToEmpty(posture.getId()))
                    || requested.equalsIgnoreCase(nullToEmpty(posture.getName()))) {
                return posture;
            }
        }
        return null;
    }

    public WeaponPostureDefinition getDefaultPosture() {
        return findPosture(defaultPostureId);
    }

    public void ensurePostureExists() {
        if (postures == null) {
            postures = new ArrayList<>();
        }
        if (postures.isEmpty()) {
            postures.add(createDefaultPosture());
        }
        if (defaultPostureId == null || defaultPostureId.trim().isEmpty()) {
            defaultPostureId = postures.get(0).getId();
        }
    }

    private static WeaponPostureDefinition createDefaultPosture() {
        WeaponPostureDefinition posture = new WeaponPostureDefinition();
        posture.setId("default");
        posture.setName("Default");
        posture.setAttachmentPoint("RightHandSocket");
        return posture;
    }

    private static String slugId(String value) {
        String slug = value == null ? "weapon" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) {
            slug = "weapon";
        }
        return "weapon_" + slug;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModelAssetId() {
        return modelAssetId;
    }

    public void setModelAssetId(String modelAssetId) {
        this.modelAssetId = modelAssetId;
    }

    public String getDefaultPostureId() {
        return defaultPostureId;
    }

    public void setDefaultPostureId(String defaultPostureId) {
        this.defaultPostureId = defaultPostureId;
    }

    public List<WeaponPostureDefinition> getPostures() {
        ensurePostureExists();
        return postures;
    }

    public List<WeaponColliderDefinition> getColliders() {
        if (colliders == null) {
            colliders = new ArrayList<>();
        }
        return colliders;
    }

    public JSONObject getDesignerMetadata() {
        if (designerMetadata == null) {
            designerMetadata = new JSONObject();
        }
        return designerMetadata;
    }
}
