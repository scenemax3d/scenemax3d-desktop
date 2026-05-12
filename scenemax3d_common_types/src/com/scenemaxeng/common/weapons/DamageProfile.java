package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class DamageProfile {
    private double baseDamage = 10.0;
    private String damageType = "physical";
    private double criticalChance = 0.0;
    private double criticalMultiplier = 2.0;
    private double knockback = 0.0;
    private double stunDuration = 0.0;
    private double armorPenetration = 0.0;
    private boolean friendlyFireAllowed = false;

    public JSONObject toJSON() {
        return new JSONObject()
                .put("baseDamage", baseDamage)
                .put("damageType", damageType)
                .put("criticalChance", criticalChance)
                .put("criticalMultiplier", criticalMultiplier)
                .put("knockback", knockback)
                .put("stunDuration", stunDuration)
                .put("armorPenetration", armorPenetration)
                .put("friendlyFireAllowed", friendlyFireAllowed);
    }

    public static DamageProfile fromJSON(JSONObject json) {
        DamageProfile profile = new DamageProfile();
        if (json == null) {
            return profile;
        }
        profile.baseDamage = json.optDouble("baseDamage", profile.baseDamage);
        profile.damageType = json.optString("damageType", profile.damageType);
        profile.criticalChance = json.optDouble("criticalChance", profile.criticalChance);
        profile.criticalMultiplier = json.optDouble("criticalMultiplier", profile.criticalMultiplier);
        profile.knockback = json.optDouble("knockback", profile.knockback);
        profile.stunDuration = json.optDouble("stunDuration", profile.stunDuration);
        profile.armorPenetration = json.optDouble("armorPenetration", profile.armorPenetration);
        profile.friendlyFireAllowed = json.optBoolean("friendlyFireAllowed", profile.friendlyFireAllowed);
        return profile;
    }

    public void validate(WeaponValidationResult result) {
        if (baseDamage <= 0) {
            result.addError("damageProfile.baseDamage", "Base damage must be greater than zero.");
        }
        if (damageType == null || damageType.trim().isEmpty()) {
            result.addError("damageProfile.damageType", "Damage type is required.");
        }
        if (criticalChance < 0 || criticalChance > 1) {
            result.addError("damageProfile.criticalChance", "Critical chance must be between 0 and 1.");
        }
        if (criticalMultiplier < 1) {
            result.addError("damageProfile.criticalMultiplier", "Critical multiplier must be at least 1.");
        }
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public void setBaseDamage(double baseDamage) {
        this.baseDamage = baseDamage;
    }

    public String getDamageType() {
        return damageType;
    }

    public void setDamageType(String damageType) {
        this.damageType = damageType;
    }

    public double getCriticalChance() {
        return criticalChance;
    }

    public void setCriticalChance(double criticalChance) {
        this.criticalChance = criticalChance;
    }

    public double getCriticalMultiplier() {
        return criticalMultiplier;
    }

    public void setCriticalMultiplier(double criticalMultiplier) {
        this.criticalMultiplier = criticalMultiplier;
    }

    public double getKnockback() {
        return knockback;
    }

    public void setKnockback(double knockback) {
        this.knockback = knockback;
    }

    public double getStunDuration() {
        return stunDuration;
    }

    public void setStunDuration(double stunDuration) {
        this.stunDuration = stunDuration;
    }

    public double getArmorPenetration() {
        return armorPenetration;
    }

    public void setArmorPenetration(double armorPenetration) {
        this.armorPenetration = armorPenetration;
    }

    public boolean isFriendlyFireAllowed() {
        return friendlyFireAllowed;
    }

    public void setFriendlyFireAllowed(boolean friendlyFireAllowed) {
        this.friendlyFireAllowed = friendlyFireAllowed;
    }
}
