package com.scenemaxeng.projector;

import com.jme3.scene.Spatial;
import com.scenemaxeng.common.weapons.AmmoDefinition;
import com.scenemaxeng.common.weapons.AttackProfile;
import com.scenemaxeng.common.weapons.DamageProfile;
import com.scenemaxeng.common.weapons.ReloadSettings;
import com.scenemaxeng.common.weapons.WeaponDefinition;

import java.util.HashSet;
import java.util.Set;

public class EquippedWeaponRuntime {
    private final String ownerCharacterId;
    private final String weaponInstanceId;
    private final WeaponDefinition weaponDefinition;
    private final EquipmentSlot equipmentSlot;
    private AttackProfile activeAttackProfile;
    private WeaponRuntimeState currentState = WeaponRuntimeState.IDLE;
    private double cooldownTimer;
    private double reloadTimer;
    private double attackTimer;
    private int currentAmmo;
    private int reserveAmmo;
    private Spatial spawnedModel;
    private final Set<String> activeHitTargets = new HashSet<>();

    public EquippedWeaponRuntime(String ownerCharacterId, String weaponInstanceId,
                                 WeaponDefinition weaponDefinition, EquipmentSlot equipmentSlot) {
        this.ownerCharacterId = ownerCharacterId;
        this.weaponInstanceId = weaponInstanceId;
        this.weaponDefinition = weaponDefinition;
        this.equipmentSlot = equipmentSlot;
        if (!weaponDefinition.getAttackProfiles().isEmpty()) {
            activeAttackProfile = weaponDefinition.getAttackProfiles().get(0);
        }
        AmmoDefinition ammo = weaponDefinition.getAmmoDefinition();
        if (ammo != null && ammo.isUsesAmmo()) {
            currentAmmo = Math.min(ammo.getDefaultMagazineAmmo(), ammo.getMagazineSize());
            reserveAmmo = ammo.getDefaultReserveAmmo();
        }
    }

    public boolean update(float tpf) {
        boolean reloadCompleted = false;
        if (cooldownTimer > 0) {
            cooldownTimer = Math.max(0, cooldownTimer - tpf);
        }

        if (currentState == WeaponRuntimeState.RELOADING) {
            reloadTimer = Math.max(0, reloadTimer - tpf);
            if (reloadTimer <= 0) {
                applyReload();
                currentState = cooldownTimer > 0 ? WeaponRuntimeState.COOLDOWN : WeaponRuntimeState.IDLE;
                reloadCompleted = true;
            }
            return reloadCompleted;
        }

        if (isAttackInProgress()) {
            attackTimer += tpf;
            updateAttackStateFromTimer();
            return false;
        }

        if (currentState == WeaponRuntimeState.COOLDOWN && cooldownTimer <= 0) {
            currentState = WeaponRuntimeState.IDLE;
        }
        return false;
    }

    public WeaponAttackResult beginAttack(String inputActionOrAttackId) {
        if (currentState == WeaponRuntimeState.RELOADING) {
            return WeaponAttackResult.failed("weapon_is_reloading", this);
        }
        if (isAttackInProgress() || currentState == WeaponRuntimeState.DRAWING || currentState == WeaponRuntimeState.SHEATHING) {
            return WeaponAttackResult.failed("weapon_is_busy", this);
        }
        if (cooldownTimer > 0) {
            currentState = WeaponRuntimeState.COOLDOWN;
            return WeaponAttackResult.failed("weapon_is_cooling_down", this);
        }

        AttackProfile profile = findAttackProfile(inputActionOrAttackId);
        if (profile == null) {
            return WeaponAttackResult.failed("attack_profile_not_found", this);
        }
        if (!hasAmmoFor(profile)) {
            AmmoDefinition ammo = weaponDefinition.getAmmoDefinition();
            if (ammo != null && ammo.isAutoReload() && beginReload()) {
                return WeaponAttackResult.failed("weapon_auto_reloading", this);
            }
            return WeaponAttackResult.failed("weapon_empty", this);
        }

        activeAttackProfile = profile;
        consumeAmmo(profile);
        attackTimer = 0;
        activeHitTargets.clear();
        currentState = profile.getStartupTime() > 0
                ? WeaponRuntimeState.ATTACK_STARTUP
                : WeaponRuntimeState.ATTACK_ACTIVE;
        return WeaponAttackResult.success(this);
    }

    public boolean beginReload() {
        AmmoDefinition ammo = weaponDefinition.getAmmoDefinition();
        if (ammo == null || !ammo.isUsesAmmo() || reserveAmmo <= 0 || currentAmmo >= ammo.getMagazineSize()) {
            return false;
        }
        ReloadSettings reload = weaponDefinition.getReloadSettings();
        reloadTimer = reload != null ? Math.max(0, reload.getReloadTime()) : 0;
        currentState = WeaponRuntimeState.RELOADING;
        if (reloadTimer <= 0) {
            applyReload();
            currentState = cooldownTimer > 0 ? WeaponRuntimeState.COOLDOWN : WeaponRuntimeState.IDLE;
        }
        return true;
    }

    public boolean isAttackWindowActive() {
        return currentState == WeaponRuntimeState.ATTACK_ACTIVE;
    }

    public boolean canHitTarget(String targetVarName) {
        return targetVarName != null
                && !targetVarName.equals(ownerCharacterId)
                && isAttackWindowActive()
                && !activeHitTargets.contains(targetVarName);
    }

    public WeaponDamageEvent registerHit(String targetVarName) {
        if (!canHitTarget(targetVarName)) {
            return null;
        }
        activeHitTargets.add(targetVarName);
        return createDamageEvent(targetVarName, activeAttackProfile, weaponDefinition.getDamageProfile());
    }

    public WeaponDamageEvent createDamageEvent(String targetVarName, AttackProfile attackProfile, DamageProfile damageProfile) {
        DamageProfile resolvedDamageProfile = damageProfile != null ? damageProfile : weaponDefinition.getDamageProfile();
        AttackProfile resolvedAttackProfile = attackProfile != null ? attackProfile : activeAttackProfile;
        double damage = resolvedDamageProfile.getBaseDamage() * resolvedAttackProfile.getDamageMultiplier();
        boolean critical = Math.random() < resolvedDamageProfile.getCriticalChance();
        if (critical) {
            damage *= resolvedDamageProfile.getCriticalMultiplier();
        }
        return new WeaponDamageEvent(ownerCharacterId, targetVarName, this, resolvedAttackProfile,
                resolvedDamageProfile, damage, critical);
    }

    private AttackProfile findAttackProfile(String inputActionOrAttackId) {
        if (weaponDefinition.getAttackProfiles().isEmpty()) {
            return null;
        }
        if (inputActionOrAttackId == null || inputActionOrAttackId.trim().isEmpty()) {
            return weaponDefinition.getAttackProfiles().get(0);
        }
        String requested = inputActionOrAttackId.trim();
        for (AttackProfile profile : weaponDefinition.getAttackProfiles()) {
            if (requested.equalsIgnoreCase(profile.getId()) || requested.equalsIgnoreCase(profile.getInputAction())) {
                return profile;
            }
        }
        return null;
    }

    private void updateAttackStateFromTimer() {
        double startupEnd = activeAttackProfile.getStartupTime();
        double activeEnd = startupEnd + activeAttackProfile.getActiveTime();
        double attackEnd = activeEnd + activeAttackProfile.getRecoveryTime();

        if (attackTimer < startupEnd) {
            currentState = WeaponRuntimeState.ATTACK_STARTUP;
        } else if (attackTimer < activeEnd) {
            currentState = WeaponRuntimeState.ATTACK_ACTIVE;
        } else if (attackTimer < attackEnd) {
            currentState = WeaponRuntimeState.ATTACK_RECOVERY;
        } else {
            currentState = WeaponRuntimeState.COOLDOWN;
            cooldownTimer = Math.max(cooldownTimer, activeAttackProfile.getCooldown());
            attackTimer = 0;
            activeHitTargets.clear();
            if (cooldownTimer <= 0) {
                currentState = WeaponRuntimeState.IDLE;
            }
        }
    }

    private boolean isAttackInProgress() {
        return currentState == WeaponRuntimeState.ATTACK_STARTUP
                || currentState == WeaponRuntimeState.ATTACK_ACTIVE
                || currentState == WeaponRuntimeState.ATTACK_RECOVERY;
    }

    private boolean hasAmmoFor(AttackProfile profile) {
        AmmoDefinition ammo = weaponDefinition.getAmmoDefinition();
        if (ammo == null || !ammo.isUsesAmmo()) {
            return true;
        }
        return currentAmmo >= ammoCostFor(profile);
    }

    private void consumeAmmo(AttackProfile profile) {
        AmmoDefinition ammo = weaponDefinition.getAmmoDefinition();
        if (ammo == null || !ammo.isUsesAmmo()) {
            return;
        }
        currentAmmo = Math.max(0, currentAmmo - ammoCostFor(profile));
    }

    private int ammoCostFor(AttackProfile profile) {
        AmmoDefinition ammo = weaponDefinition.getAmmoDefinition();
        if (profile.getAmmoCost() > 0) {
            return profile.getAmmoCost();
        }
        return ammo != null ? Math.max(1, ammo.getAmmoConsumedPerShot()) : 0;
    }

    private void applyReload() {
        AmmoDefinition ammo = weaponDefinition.getAmmoDefinition();
        if (ammo == null || !ammo.isUsesAmmo()) {
            return;
        }
        int missing = Math.max(0, ammo.getMagazineSize() - currentAmmo);
        int loaded = Math.min(missing, reserveAmmo);
        currentAmmo += loaded;
        reserveAmmo -= loaded;
    }

    public void detachModel() {
        if (spawnedModel != null) {
            spawnedModel.removeFromParent();
            spawnedModel = null;
        }
    }

    public String getOwnerCharacterId() {
        return ownerCharacterId;
    }

    public String getWeaponInstanceId() {
        return weaponInstanceId;
    }

    public WeaponDefinition getWeaponDefinition() {
        return weaponDefinition;
    }

    public EquipmentSlot getEquipmentSlot() {
        return equipmentSlot;
    }

    public AttackProfile getActiveAttackProfile() {
        return activeAttackProfile;
    }

    public WeaponRuntimeState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(WeaponRuntimeState currentState) {
        this.currentState = currentState;
    }

    public double getCooldownTimer() {
        return cooldownTimer;
    }

    public void setCooldownTimer(double cooldownTimer) {
        this.cooldownTimer = cooldownTimer;
    }

    public double getReloadTimer() {
        return reloadTimer;
    }

    public void setReloadTimer(double reloadTimer) {
        this.reloadTimer = reloadTimer;
    }

    public double getAttackTimer() {
        return attackTimer;
    }

    public void setAttackTimer(double attackTimer) {
        this.attackTimer = attackTimer;
    }

    public Spatial getSpawnedModel() {
        return spawnedModel;
    }

    public void setSpawnedModel(Spatial spawnedModel) {
        this.spawnedModel = spawnedModel;
    }

    public Set<String> getActiveHitTargets() {
        return activeHitTargets;
    }

    public int getCurrentAmmo() {
        return currentAmmo;
    }

    public void setCurrentAmmo(int currentAmmo) {
        this.currentAmmo = currentAmmo;
    }

    public int getReserveAmmo() {
        return reserveAmmo;
    }

    public void setReserveAmmo(int reserveAmmo) {
        this.reserveAmmo = reserveAmmo;
    }
}
