package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

/**
 * Runtime copy of an attack profile that can be changed before an attack starts.
 */
public class WeaponAttackInstance extends AttackProfile {
    private String ownerCharacterId = "";
    private String weaponInstanceId = "";
    private String slotId = "";
    private String sourceAttackId = "";
    private boolean cancelled;
    private ProjectileDefinition projectileDefinitionOverride;
    private WeaponProjectilePath projectilePathOverride;

    public static WeaponAttackInstance from(AttackProfile profile, ProjectileDefinition projectileDefinition) {
        WeaponAttackInstance instance = new WeaponAttackInstance();
        if (profile != null) {
            AttackProfile copy = AttackProfile.fromJSON(profile.toJSON());
            instance.copyFrom(copy);
            instance.sourceAttackId = copy.getId();
        }
        if (projectileDefinition != null) {
            instance.projectileDefinitionOverride = ProjectileDefinition.fromJSON(projectileDefinition.toJSON());
        }
        return instance;
    }

    private void copyFrom(AttackProfile profile) {
        setId(profile.getId());
        setName(profile.getName());
        setInputAction(profile.getInputAction());
        setAttackType(profile.getAttackType());
        setDamageMultiplier(profile.getDamageMultiplier());
        setCooldown(profile.getCooldown());
        setStartupTime(profile.getStartupTime());
        setActiveTime(profile.getActiveTime());
        setRecoveryTime(profile.getRecoveryTime());
        setRange(profile.getRange());
        setStaminaCost(profile.getStaminaCost());
        setAmmoCost(profile.getAmmoCost());
        setProjectileDefinitionId(profile.getProjectileDefinitionId());
        setProjectileLaunchOffsetX(profile.getProjectileLaunchOffsetX());
        setProjectileLaunchOffsetY(profile.getProjectileLaunchOffsetY());
        setProjectileLaunchOffsetZ(profile.getProjectileLaunchOffsetZ());
        setAttackAnimation(profile.getAttackAnimation());
        setAttackSound(profile.getAttackSound());
        setImpactSound(profile.getImpactSound());
        setMuzzleFlashEffect(profile.getMuzzleFlashEffect());
        setMeleeTrailEffect(profile.getMeleeTrailEffect());
        setImpactEffect(profile.getImpactEffect());
        setAttackHandlerProcedure(profile.getAttackHandlerProcedure());
        setAnimationEventBinding(profile.getAnimationEventBinding());
        setSoundEventBinding(profile.getSoundEventBinding());
        setEffectEventBinding(profile.getEffectEventBinding());
        setAutoFire(profile.isAutoFire());
    }

    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON()
                .put("ownerCharacterId", ownerCharacterId)
                .put("weaponInstanceId", weaponInstanceId)
                .put("slotId", slotId)
                .put("sourceAttackId", sourceAttackId)
                .put("cancelled", cancelled);
        if (projectileDefinitionOverride != null) {
            json.put("projectileDefinitionOverride", projectileDefinitionOverride.toJSON());
        }
        if (projectilePathOverride != null) {
            json.put("projectilePathOverride", projectilePathOverride.toJSON());
        }
        return json;
    }

    public String getOwnerCharacterId() {
        return ownerCharacterId;
    }

    public void setOwnerCharacterId(String ownerCharacterId) {
        this.ownerCharacterId = ownerCharacterId;
    }

    public String getWeaponInstanceId() {
        return weaponInstanceId;
    }

    public void setWeaponInstanceId(String weaponInstanceId) {
        this.weaponInstanceId = weaponInstanceId;
    }

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public String getSourceAttackId() {
        return sourceAttackId;
    }

    public void setSourceAttackId(String sourceAttackId) {
        this.sourceAttackId = sourceAttackId;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public ProjectileDefinition getProjectileDefinitionOverride() {
        return projectileDefinitionOverride;
    }

    public void setProjectileDefinitionOverride(ProjectileDefinition projectileDefinitionOverride) {
        this.projectileDefinitionOverride = projectileDefinitionOverride;
    }

    public WeaponProjectilePath getProjectilePathOverride() {
        return projectilePathOverride;
    }

    public void setProjectilePathOverride(WeaponProjectilePath projectilePathOverride) {
        this.projectilePathOverride = projectilePathOverride;
    }
}
