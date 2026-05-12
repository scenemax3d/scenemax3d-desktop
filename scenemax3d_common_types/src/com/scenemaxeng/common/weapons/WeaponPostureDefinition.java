package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class WeaponPostureDefinition {
    private String id = "default";
    private String name = "Default";
    private String attachmentPoint = "RightHandSocket";
    private WeaponAttachmentTransform transform = new WeaponAttachmentTransform();

    public JSONObject toJSON() {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("attachmentPoint", attachmentPoint)
                .put("transform", transform == null ? new JSONObject() : transform.toJSON());
    }

    public static WeaponPostureDefinition fromJSON(JSONObject json) {
        WeaponPostureDefinition posture = new WeaponPostureDefinition();
        if (json == null) {
            return posture;
        }
        posture.id = json.optString("id", posture.id);
        posture.name = json.optString("name", posture.name);
        posture.attachmentPoint = json.optString("attachmentPoint",
                json.optString("attachTo", posture.attachmentPoint));
        posture.transform = WeaponAttachmentTransform.fromJSON(json.optJSONObject("transform"));
        return posture;
    }

    public void validate(WeaponValidationResult result) {
        if (id == null || id.trim().isEmpty()) {
            result.addError("postures.id", "Every weapon posture needs an id.");
        }
        if (name == null || name.trim().isEmpty()) {
            result.addError("postures.name", "Every weapon posture needs a name.");
        }
        if (transform != null) {
            transform.validate(result);
        }
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

    public String getAttachmentPoint() {
        return attachmentPoint;
    }

    public void setAttachmentPoint(String attachmentPoint) {
        this.attachmentPoint = attachmentPoint;
    }

    public WeaponAttachmentTransform getTransform() {
        if (transform == null) {
            transform = new WeaponAttachmentTransform();
        }
        return transform;
    }

    public void setTransform(WeaponAttachmentTransform transform) {
        this.transform = transform;
    }
}
