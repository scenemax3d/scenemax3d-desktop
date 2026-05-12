package com.scenemaxeng.projector;

import com.scenemaxeng.common.weapons.AttackProfile;
import com.scenemaxeng.common.weapons.DamageProfile;
import com.scenemaxeng.common.weapons.WeaponDefinition;

public class WeaponDamageEvent {
    private final String attackerVarName;
    private final String targetVarName;
    private final String weaponInstanceId;
    private final WeaponDefinition weaponDefinition;
    private final AttackProfile attackProfile;
    private final double damageAmount;
    private final String damageType;
    private final boolean criticalHit;
    private final double knockback;
    private final double stunDuration;
    private final double armorPenetration;

    public WeaponDamageEvent(String attackerVarName, String targetVarName, EquippedWeaponRuntime weapon,
                             double damageAmount, boolean criticalHit) {
        this(attackerVarName, targetVarName, weapon, weapon.getActiveAttackProfile(),
                weapon.getWeaponDefinition().getDamageProfile(), damageAmount, criticalHit);
    }

    public WeaponDamageEvent(String attackerVarName, String targetVarName, EquippedWeaponRuntime weapon,
                             AttackProfile attackProfile, DamageProfile damageProfile,
                             double damageAmount, boolean criticalHit) {
        this.attackerVarName = attackerVarName;
        this.targetVarName = targetVarName;
        this.weaponInstanceId = weapon.getWeaponInstanceId();
        this.weaponDefinition = weapon.getWeaponDefinition();
        this.attackProfile = attackProfile;
        this.damageAmount = damageAmount;
        this.damageType = damageProfile.getDamageType();
        this.criticalHit = criticalHit;
        this.knockback = damageProfile.getKnockback();
        this.stunDuration = damageProfile.getStunDuration();
        this.armorPenetration = damageProfile.getArmorPenetration();
    }

    public String getAttackerVarName() {
        return attackerVarName;
    }

    public String getTargetVarName() {
        return targetVarName;
    }

    public String getWeaponInstanceId() {
        return weaponInstanceId;
    }

    public WeaponDefinition getWeaponDefinition() {
        return weaponDefinition;
    }

    public AttackProfile getAttackProfile() {
        return attackProfile;
    }

    public double getDamageAmount() {
        return damageAmount;
    }

    public String getDamageType() {
        return damageType;
    }

    public boolean isCriticalHit() {
        return criticalHit;
    }

    public double getKnockback() {
        return knockback;
    }

    public double getStunDuration() {
        return stunDuration;
    }

    public double getArmorPenetration() {
        return armorPenetration;
    }
}
