package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class WeaponRuntimeRules {
    public boolean canAttackWhileMoving = true;
    public boolean canAttackWhileJumping = false;
    public boolean autoAimEnabled = false;
    public double aimAssistStrength = 0.0;
    public boolean interruptibleAttack = false;
    public boolean canCancelIntoNextAttack = false;
    public double comboWindow = 0.0;
    public boolean usesStamina = false;
    public boolean requiresLineOfSight = false;
    public double equippedMoveSpeedMultiplier = 1.0;

    public JSONObject toJSON() {
        return new JSONObject()
                .put("canAttackWhileMoving", canAttackWhileMoving)
                .put("canAttackWhileJumping", canAttackWhileJumping)
                .put("autoAimEnabled", autoAimEnabled)
                .put("aimAssistStrength", aimAssistStrength)
                .put("interruptibleAttack", interruptibleAttack)
                .put("canCancelIntoNextAttack", canCancelIntoNextAttack)
                .put("comboWindow", comboWindow)
                .put("usesStamina", usesStamina)
                .put("requiresLineOfSight", requiresLineOfSight)
                .put("equippedMoveSpeedMultiplier", equippedMoveSpeedMultiplier);
    }

    public static WeaponRuntimeRules fromJSON(JSONObject json) {
        WeaponRuntimeRules rules = new WeaponRuntimeRules();
        if (json == null) {
            return rules;
        }
        rules.canAttackWhileMoving = json.optBoolean("canAttackWhileMoving", rules.canAttackWhileMoving);
        rules.canAttackWhileJumping = json.optBoolean("canAttackWhileJumping", rules.canAttackWhileJumping);
        rules.autoAimEnabled = json.optBoolean("autoAimEnabled", rules.autoAimEnabled);
        rules.aimAssistStrength = json.optDouble("aimAssistStrength", rules.aimAssistStrength);
        rules.interruptibleAttack = json.optBoolean("interruptibleAttack", rules.interruptibleAttack);
        rules.canCancelIntoNextAttack = json.optBoolean("canCancelIntoNextAttack", rules.canCancelIntoNextAttack);
        rules.comboWindow = json.optDouble("comboWindow", rules.comboWindow);
        rules.usesStamina = json.optBoolean("usesStamina", rules.usesStamina);
        rules.requiresLineOfSight = json.optBoolean("requiresLineOfSight", rules.requiresLineOfSight);
        rules.equippedMoveSpeedMultiplier = json.optDouble("equippedMoveSpeedMultiplier", rules.equippedMoveSpeedMultiplier);
        return rules;
    }
}
