package com.scenemax.designer;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Wraps a JME3 Node with metadata for serialization and designer operations.
 */
public class DesignerEntity {

    private String id;
    private String name;
    private DesignerEntityType type;
    private Node sceneNode;

    // Sphere-specific
    private float radius = 0.5f;

    // Box-specific
    private float sizeX = 0.5f;
    private float sizeY = 0.5f;
    private float sizeZ = 0.5f;

    // Model-specific
    private String resourcePath;
    private boolean staticModel;
    private boolean dynamicModel;
    private boolean vehicleModel;

    // Shared flags for BOX, SPHERE (and MODEL already has staticModel)
    private boolean staticEntity;
    private boolean colliderEntity;

    // Material for BOX, SPHERE (empty = no material / default)
    private String material = "";

    // Code node text (for CODE type)
    private String codeText = "";

    // Hidden flag – only affects generated code output, not designer visibility
    private boolean hidden;

    // Shadow mode: "none", "cast", "receive", "both"
    private String shadowMode = "none";

    // Joint mapping for 3D models (comma-separated joint names, empty = disabled)
    private String jointMapping = "";

    // The SceneMax language code used to create this entity
    private String sceneMaxCode;

    public DesignerEntity(String name, DesignerEntityType type) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
    }

    public DesignerEntity(String id, String name, DesignerEntityType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    // --- Getters/Setters ---

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DesignerEntityType getType() { return type; }
    public Node getSceneNode() { return sceneNode; }
    public void setSceneNode(Node sceneNode) { this.sceneNode = sceneNode; }

    public float getRadius() { return radius; }
    public void setRadius(float radius) { this.radius = radius; }

    public float getSizeX() { return sizeX; }
    public void setSizeX(float sizeX) { this.sizeX = sizeX; }
    public float getSizeY() { return sizeY; }
    public void setSizeY(float sizeY) { this.sizeY = sizeY; }
    public float getSizeZ() { return sizeZ; }
    public void setSizeZ(float sizeZ) { this.sizeZ = sizeZ; }

    public String getResourcePath() { return resourcePath; }
    public void setResourcePath(String resourcePath) { this.resourcePath = resourcePath; }

    public boolean isStaticModel() { return staticModel; }
    public void setStaticModel(boolean staticModel) { this.staticModel = staticModel; }

    public boolean isDynamicModel() { return dynamicModel; }
    public void setDynamicModel(boolean dynamicModel) { this.dynamicModel = dynamicModel; }

    public boolean isVehicleModel() { return vehicleModel; }
    public void setVehicleModel(boolean vehicleModel) { this.vehicleModel = vehicleModel; }

    public boolean isStaticEntity() { return staticEntity; }
    public void setStaticEntity(boolean staticEntity) { this.staticEntity = staticEntity; }
    public boolean isColliderEntity() { return colliderEntity; }
    public void setColliderEntity(boolean colliderEntity) { this.colliderEntity = colliderEntity; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material != null ? material : ""; }

    public String getCodeText() { return codeText; }
    public void setCodeText(String codeText) { this.codeText = codeText != null ? codeText : ""; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public String getShadowMode() { return shadowMode; }
    public void setShadowMode(String shadowMode) { this.shadowMode = shadowMode != null ? shadowMode : "none"; }

    public String getJointMapping() { return jointMapping; }
    public void setJointMapping(String jointMapping) { this.jointMapping = jointMapping != null ? jointMapping : ""; }

    public String getSceneMaxCode() { return sceneMaxCode; }
    public void setSceneMaxCode(String sceneMaxCode) { this.sceneMaxCode = sceneMaxCode; }

    // --- Transform helpers ---

    public Vector3f getPosition() {
        return sceneNode != null ? sceneNode.getLocalTranslation() : Vector3f.ZERO;
    }

    public void setPosition(Vector3f pos) {
        if (sceneNode != null) sceneNode.setLocalTranslation(pos);
    }

    public Quaternion getRotation() {
        return sceneNode != null ? sceneNode.getLocalRotation() : Quaternion.IDENTITY;
    }

    public void setRotation(Quaternion rot) {
        if (sceneNode != null) sceneNode.setLocalRotation(rot);
    }

    public Vector3f getScale() {
        return sceneNode != null ? sceneNode.getLocalScale() : Vector3f.UNIT_XYZ;
    }

    public void setScale(Vector3f scale) {
        if (sceneNode != null) sceneNode.setLocalScale(scale);
    }

    // --- Serialization ---

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("type", type.name());

        Vector3f pos = getPosition();
        json.put("position", new float[]{pos.x, pos.y, pos.z});

        Quaternion rot = getRotation();
        json.put("rotation", new float[]{rot.getX(), rot.getY(), rot.getZ(), rot.getW()});

        Vector3f scale = getScale();
        json.put("scale", new float[]{scale.x, scale.y, scale.z});

        if (sceneMaxCode != null) {
            json.put("sceneMaxCode", sceneMaxCode);
        }

        switch (type) {
            case SPHERE:
                json.put("radius", radius);
                json.put("staticEntity", staticEntity);
                json.put("colliderEntity", colliderEntity);
                json.put("material", material);
                json.put("hidden", hidden);
                json.put("shadowMode", shadowMode);
                break;
            case BOX:
                json.put("sizeX", sizeX);
                json.put("sizeY", sizeY);
                json.put("sizeZ", sizeZ);
                json.put("staticEntity", staticEntity);
                json.put("colliderEntity", colliderEntity);
                json.put("material", material);
                json.put("hidden", hidden);
                json.put("shadowMode", shadowMode);
                break;
            case MODEL:
                json.put("resourcePath", resourcePath != null ? resourcePath : "");
                json.put("staticModel", staticModel);
                json.put("dynamicModel", dynamicModel);
                json.put("vehicleModel", vehicleModel);
                json.put("hidden", hidden);
                json.put("shadowMode", shadowMode);
                json.put("jointMapping", jointMapping);
                break;
            case CAMERA:
                break;
            case CODE:
                json.put("codeText", codeText);
                break;
        }

        return json;
    }

    public static DesignerEntity fromJSON(JSONObject json) {
        String id = json.getString("id");
        String name = json.getString("name");
        DesignerEntityType type = DesignerEntityType.valueOf(json.getString("type"));

        DesignerEntity entity = new DesignerEntity(id, name, type);

        switch (type) {
            case SPHERE:
                entity.radius = (float) json.optDouble("radius", 0.5);
                entity.staticEntity = json.optBoolean("staticEntity", false);
                entity.colliderEntity = json.optBoolean("colliderEntity", false);
                entity.material = json.optString("material", "");
                entity.hidden = json.optBoolean("hidden", false);
                entity.shadowMode = json.optString("shadowMode", "none");
                break;
            case BOX:
                entity.sizeX = (float) json.optDouble("sizeX", 0.5);
                entity.sizeY = (float) json.optDouble("sizeY", 0.5);
                entity.sizeZ = (float) json.optDouble("sizeZ", 0.5);
                entity.staticEntity = json.optBoolean("staticEntity", false);
                entity.colliderEntity = json.optBoolean("colliderEntity", false);
                entity.material = json.optString("material", "");
                entity.hidden = json.optBoolean("hidden", false);
                entity.shadowMode = json.optString("shadowMode", "none");
                break;
            case MODEL:
                entity.resourcePath = json.optString("resourcePath", "");
                entity.staticModel = json.optBoolean("staticModel", false);
                entity.dynamicModel = json.optBoolean("dynamicModel", false);
                entity.vehicleModel = json.optBoolean("vehicleModel", false);
                entity.hidden = json.optBoolean("hidden", false);
                entity.shadowMode = json.optString("shadowMode", "none");
                entity.jointMapping = json.optString("jointMapping", "");
                break;
            case CAMERA:
                break;
            case CODE:
                entity.codeText = json.optString("codeText", "");
                break;
        }

        return entity;
    }

    /**
     * Returns the saved position from JSON (before sceneNode is attached).
     */
    public static Vector3f positionFromJSON(JSONObject json) {
        if (!json.has("position")) return new Vector3f(0, 0, 0);
        org.json.JSONArray arr = json.getJSONArray("position");
        return new Vector3f((float) arr.getDouble(0), (float) arr.getDouble(1), (float) arr.getDouble(2));
    }

    public static Quaternion rotationFromJSON(JSONObject json) {
        if (!json.has("rotation")) return Quaternion.IDENTITY.clone();
        org.json.JSONArray arr = json.getJSONArray("rotation");
        return new Quaternion((float) arr.getDouble(0), (float) arr.getDouble(1),
                (float) arr.getDouble(2), (float) arr.getDouble(3));
    }

    public static Vector3f scaleFromJSON(JSONObject json) {
        if (!json.has("scale")) return new Vector3f(1, 1, 1);
        org.json.JSONArray arr = json.getJSONArray("scale");
        return new Vector3f((float) arr.getDouble(0), (float) arr.getDouble(1), (float) arr.getDouble(2));
    }
}
