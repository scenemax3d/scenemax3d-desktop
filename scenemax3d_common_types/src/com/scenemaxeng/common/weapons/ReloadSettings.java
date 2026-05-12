package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class ReloadSettings {
    private double reloadTime = 1.0;
    private String reloadMode = "fullMagazine";
    private String reloadAnimationMarker = "ReloadAmmoApply";

    public JSONObject toJSON() {
        return new JSONObject()
                .put("reloadTime", reloadTime)
                .put("reloadMode", reloadMode)
                .put("reloadAnimationMarker", reloadAnimationMarker);
    }

    public static ReloadSettings fromJSON(JSONObject json) {
        ReloadSettings settings = new ReloadSettings();
        if (json == null) {
            return settings;
        }
        settings.reloadTime = json.optDouble("reloadTime", settings.reloadTime);
        settings.reloadMode = json.optString("reloadMode", settings.reloadMode);
        settings.reloadAnimationMarker = json.optString("reloadAnimationMarker", settings.reloadAnimationMarker);
        return settings;
    }

    public void validate(WeaponValidationResult result, AmmoDefinition ammoDefinition) {
        if (ammoDefinition == null || !ammoDefinition.isUsesAmmo()) {
            return;
        }
        if (reloadTime <= 0 && (reloadAnimationMarker == null || reloadAnimationMarker.trim().isEmpty())) {
            result.addError("reloadSettings.reloadTime", "Reloading weapons require reload time or a reload animation marker.");
        }
    }

    public double getReloadTime() {
        return reloadTime;
    }

    public void setReloadTime(double reloadTime) {
        this.reloadTime = reloadTime;
    }

    public String getReloadMode() {
        return reloadMode;
    }

    public void setReloadMode(String reloadMode) {
        this.reloadMode = reloadMode;
    }

    public String getReloadAnimationMarker() {
        return reloadAnimationMarker;
    }

    public void setReloadAnimationMarker(String reloadAnimationMarker) {
        this.reloadAnimationMarker = reloadAnimationMarker;
    }
}
