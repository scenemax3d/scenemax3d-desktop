package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class WeaponAnimationSet {
    public String idleAnimation = "";
    public String drawAnimation = "";
    public String sheatheAnimation = "";
    public String primaryAttackAnimation = "";
    public String secondaryAttackAnimation = "";
    public String reloadAnimation = "";
    public String aimAnimation = "";
    public String blockAnimation = "";
    public String hitReactionAnimation = "";
    public double animationSpeedMultiplier = 1.0;

    public JSONObject toJSON() {
        return new JSONObject()
                .put("idleAnimation", idleAnimation)
                .put("drawAnimation", drawAnimation)
                .put("sheatheAnimation", sheatheAnimation)
                .put("primaryAttackAnimation", primaryAttackAnimation)
                .put("secondaryAttackAnimation", secondaryAttackAnimation)
                .put("reloadAnimation", reloadAnimation)
                .put("aimAnimation", aimAnimation)
                .put("blockAnimation", blockAnimation)
                .put("hitReactionAnimation", hitReactionAnimation)
                .put("animationSpeedMultiplier", animationSpeedMultiplier);
    }

    public static WeaponAnimationSet fromJSON(JSONObject json) {
        WeaponAnimationSet set = new WeaponAnimationSet();
        if (json == null) {
            return set;
        }
        set.idleAnimation = json.optString("idleAnimation", set.idleAnimation);
        set.drawAnimation = json.optString("drawAnimation", set.drawAnimation);
        set.sheatheAnimation = json.optString("sheatheAnimation", set.sheatheAnimation);
        set.primaryAttackAnimation = json.optString("primaryAttackAnimation", set.primaryAttackAnimation);
        set.secondaryAttackAnimation = json.optString("secondaryAttackAnimation", set.secondaryAttackAnimation);
        set.reloadAnimation = json.optString("reloadAnimation", set.reloadAnimation);
        set.aimAnimation = json.optString("aimAnimation", set.aimAnimation);
        set.blockAnimation = json.optString("blockAnimation", set.blockAnimation);
        set.hitReactionAnimation = json.optString("hitReactionAnimation", set.hitReactionAnimation);
        set.animationSpeedMultiplier = json.optDouble("animationSpeedMultiplier", set.animationSpeedMultiplier);
        return set;
    }

    public void validate(WeaponValidationResult result) {
        if (animationSpeedMultiplier <= 0) {
            result.addWarning("animationSet.animationSpeedMultiplier", "Animation speed multiplier should be greater than zero.");
        }
    }
}
