package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

import java.util.Locale;

public class WeaponColliderDefinition {
    public static final String SHAPE_BOX = "box";
    public static final String SHAPE_SPHERE = "sphere";

    private String name = "weapon_collider";
    private String shape = SHAPE_BOX;
    private WeaponAttachmentTransform transform = new WeaponAttachmentTransform();

    public JSONObject toJSON() {
        return new JSONObject()
                .put("name", name)
                .put("shape", normalizedShape(shape))
                .put("transform", transform == null ? new WeaponAttachmentTransform().toJSON() : transform.toJSON());
    }

    public static WeaponColliderDefinition fromJSON(JSONObject json) {
        WeaponColliderDefinition collider = new WeaponColliderDefinition();
        if (json == null) {
            return collider;
        }
        collider.name = json.optString("name", collider.name);
        collider.shape = normalizedShape(json.optString("shape", collider.shape));
        collider.transform = WeaponAttachmentTransform.fromJSON(json.optJSONObject("transform"));
        return collider;
    }

    public void validate(WeaponValidationResult result) {
        if (name == null || name.trim().isEmpty()) {
            result.addError("colliders.name", "Every weapon collider needs a name.");
        }
        if (!SHAPE_BOX.equals(normalizedShape(shape)) && !SHAPE_SPHERE.equals(normalizedShape(shape))) {
            result.addError("colliders.shape", "Weapon collider shape must be box or sphere.");
        }
        if (transform == null) {
            transform = new WeaponAttachmentTransform();
        }
        transform.validate(result);
    }

    public boolean isSphere() {
        return SHAPE_SPHERE.equals(normalizedShape(shape));
    }

    public boolean isBox() {
        return SHAPE_BOX.equals(normalizedShape(shape));
    }

    public static String normalizedShape(String shape) {
        String normalized = shape == null ? "" : shape.trim().toLowerCase(Locale.ROOT);
        return SHAPE_SPHERE.equals(normalized) ? SHAPE_SPHERE : SHAPE_BOX;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShape() {
        return normalizedShape(shape);
    }

    public void setShape(String shape) {
        this.shape = normalizedShape(shape);
    }

    public WeaponAttachmentTransform getTransform() {
        if (transform == null) {
            transform = new WeaponAttachmentTransform();
        }
        return transform;
    }

    public void setTransform(WeaponAttachmentTransform transform) {
        this.transform = transform == null ? new WeaponAttachmentTransform() : transform;
    }
}
