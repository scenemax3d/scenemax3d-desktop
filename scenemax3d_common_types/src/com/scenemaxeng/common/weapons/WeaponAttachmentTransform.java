package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class WeaponAttachmentTransform {
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double offsetZ = 0.0;
    private double rotationX = 0.0;
    private double rotationY = 0.0;
    private double rotationZ = 0.0;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private double scaleZ = 1.0;

    public JSONObject toJSON() {
        return new JSONObject()
                .put("offsetX", offsetX)
                .put("offsetY", offsetY)
                .put("offsetZ", offsetZ)
                .put("rotationX", rotationX)
                .put("rotationY", rotationY)
                .put("rotationZ", rotationZ)
                .put("scaleX", scaleX)
                .put("scaleY", scaleY)
                .put("scaleZ", scaleZ);
    }

    public static WeaponAttachmentTransform fromJSON(JSONObject json) {
        WeaponAttachmentTransform transform = new WeaponAttachmentTransform();
        if (json == null) {
            return transform;
        }
        transform.offsetX = json.optDouble("offsetX", transform.offsetX);
        transform.offsetY = json.optDouble("offsetY", transform.offsetY);
        transform.offsetZ = json.optDouble("offsetZ", transform.offsetZ);
        transform.rotationX = json.optDouble("rotationX", transform.rotationX);
        transform.rotationY = json.optDouble("rotationY", transform.rotationY);
        transform.rotationZ = json.optDouble("rotationZ", transform.rotationZ);
        transform.scaleX = json.optDouble("scaleX", transform.scaleX);
        transform.scaleY = json.optDouble("scaleY", transform.scaleY);
        transform.scaleZ = json.optDouble("scaleZ", transform.scaleZ);
        return transform;
    }

    public void validate(WeaponValidationResult result) {
        if (scaleX <= 0 || scaleY <= 0 || scaleZ <= 0) {
            result.addError("attachmentTransform.scale", "Attachment scale must be greater than zero on every axis.");
        }
    }

    public double getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
    }

    public double getOffsetZ() {
        return offsetZ;
    }

    public void setOffsetZ(double offsetZ) {
        this.offsetZ = offsetZ;
    }

    public double getRotationX() {
        return rotationX;
    }

    public void setRotationX(double rotationX) {
        this.rotationX = rotationX;
    }

    public double getRotationY() {
        return rotationY;
    }

    public void setRotationY(double rotationY) {
        this.rotationY = rotationY;
    }

    public double getRotationZ() {
        return rotationZ;
    }

    public void setRotationZ(double rotationZ) {
        this.rotationZ = rotationZ;
    }

    public double getScaleX() {
        return scaleX;
    }

    public void setScaleX(double scaleX) {
        this.scaleX = scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public void setScaleY(double scaleY) {
        this.scaleY = scaleY;
    }

    public double getScaleZ() {
        return scaleZ;
    }

    public void setScaleZ(double scaleZ) {
        this.scaleZ = scaleZ;
    }
}
