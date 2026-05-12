package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class AmmoDefinition {
    private boolean usesAmmo = false;
    private String ammoType = "";
    private int magazineSize = 0;
    private int defaultMagazineAmmo = 0;
    private int defaultReserveAmmo = 0;
    private int ammoConsumedPerShot = 1;
    private boolean autoReload = true;
    private String emptyBehavior = "dryFire";

    public JSONObject toJSON() {
        return new JSONObject()
                .put("usesAmmo", usesAmmo)
                .put("ammoType", ammoType)
                .put("magazineSize", magazineSize)
                .put("defaultMagazineAmmo", defaultMagazineAmmo)
                .put("defaultReserveAmmo", defaultReserveAmmo)
                .put("ammoConsumedPerShot", ammoConsumedPerShot)
                .put("autoReload", autoReload)
                .put("emptyBehavior", emptyBehavior);
    }

    public static AmmoDefinition fromJSON(JSONObject json) {
        AmmoDefinition ammo = new AmmoDefinition();
        if (json == null) {
            return ammo;
        }
        ammo.usesAmmo = json.optBoolean("usesAmmo", ammo.usesAmmo);
        ammo.ammoType = json.optString("ammoType", ammo.ammoType);
        ammo.magazineSize = json.optInt("magazineSize", ammo.magazineSize);
        ammo.defaultMagazineAmmo = json.optInt("defaultMagazineAmmo", ammo.defaultMagazineAmmo);
        ammo.defaultReserveAmmo = json.optInt("defaultReserveAmmo", ammo.defaultReserveAmmo);
        ammo.ammoConsumedPerShot = json.optInt("ammoConsumedPerShot", ammo.ammoConsumedPerShot);
        ammo.autoReload = json.optBoolean("autoReload", ammo.autoReload);
        ammo.emptyBehavior = json.optString("emptyBehavior", ammo.emptyBehavior);
        return ammo;
    }

    public void validate(WeaponValidationResult result) {
        if (!usesAmmo) {
            return;
        }
        if (magazineSize <= 0) {
            result.addError("ammoDefinition.magazineSize", "Ammo-using weapons require a magazine size greater than zero.");
        }
        if (ammoConsumedPerShot <= 0) {
            result.addError("ammoDefinition.ammoConsumedPerShot", "Ammo consumed per shot must be greater than zero.");
        }
        if (defaultMagazineAmmo < 0 || defaultReserveAmmo < 0) {
            result.addError("ammoDefinition.defaultAmmo", "Default ammo values cannot be negative.");
        }
    }

    public boolean isUsesAmmo() {
        return usesAmmo;
    }

    public void setUsesAmmo(boolean usesAmmo) {
        this.usesAmmo = usesAmmo;
    }

    public int getMagazineSize() {
        return magazineSize;
    }

    public void setMagazineSize(int magazineSize) {
        this.magazineSize = magazineSize;
    }

    public int getDefaultMagazineAmmo() {
        return defaultMagazineAmmo;
    }

    public void setDefaultMagazineAmmo(int defaultMagazineAmmo) {
        this.defaultMagazineAmmo = defaultMagazineAmmo;
    }

    public int getDefaultReserveAmmo() {
        return defaultReserveAmmo;
    }

    public void setDefaultReserveAmmo(int defaultReserveAmmo) {
        this.defaultReserveAmmo = defaultReserveAmmo;
    }

    public int getAmmoConsumedPerShot() {
        return ammoConsumedPerShot;
    }

    public void setAmmoConsumedPerShot(int ammoConsumedPerShot) {
        this.ammoConsumedPerShot = ammoConsumedPerShot;
    }

    public boolean isAutoReload() {
        return autoReload;
    }

    public void setAutoReload(boolean autoReload) {
        this.autoReload = autoReload;
    }

    public String getEmptyBehavior() {
        return emptyBehavior;
    }

    public void setEmptyBehavior(String emptyBehavior) {
        this.emptyBehavior = emptyBehavior;
    }
}
