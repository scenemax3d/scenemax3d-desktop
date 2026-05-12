package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class AttackProfile {
    private String id = "primary";
    private String name = "Primary Attack";
    private String inputAction = "primary";
    private String attackType = "meleeHitbox";
    private double damageMultiplier = 1.0;
    private double cooldown = 0.6;
    private double startupTime = 0.15;
    private double activeTime = 0.2;
    private double recoveryTime = 0.25;
    private double range = 1.5;
    private double staminaCost = 0.0;
    private int ammoCost = 0;
    private String projectileDefinitionId = "";
    private String attackAnimation = "";
    private String attackSound = "";
    private String impactSound = "";
    private String muzzleFlashEffect = "";
    private String meleeTrailEffect = "";
    private String impactEffect = "";
    private String animationEventBinding = "";
    private String soundEventBinding = "";
    private String effectEventBinding = "";
    private boolean autoFire = false;

    public JSONObject toJSON() {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("inputAction", inputAction)
                .put("attackType", attackType)
                .put("damageMultiplier", damageMultiplier)
                .put("cooldown", cooldown)
                .put("startupTime", startupTime)
                .put("activeTime", activeTime)
                .put("recoveryTime", recoveryTime)
                .put("range", range)
                .put("staminaCost", staminaCost)
                .put("ammoCost", ammoCost)
                .put("projectileDefinitionId", projectileDefinitionId)
                .put("attackAnimation", attackAnimation)
                .put("attackSound", attackSound)
                .put("impactSound", impactSound)
                .put("muzzleFlashEffect", muzzleFlashEffect)
                .put("meleeTrailEffect", meleeTrailEffect)
                .put("impactEffect", impactEffect)
                .put("animationEventBinding", animationEventBinding)
                .put("soundEventBinding", soundEventBinding)
                .put("effectEventBinding", effectEventBinding)
                .put("autoFire", autoFire);
    }

    public static AttackProfile fromJSON(JSONObject json) {
        AttackProfile profile = new AttackProfile();
        if (json == null) {
            return profile;
        }
        profile.id = json.optString("id", profile.id);
        profile.name = json.optString("name", profile.name);
        profile.inputAction = json.optString("inputAction", profile.inputAction);
        profile.attackType = json.optString("attackType", profile.attackType);
        profile.damageMultiplier = json.optDouble("damageMultiplier", profile.damageMultiplier);
        profile.cooldown = json.optDouble("cooldown", profile.cooldown);
        profile.startupTime = json.optDouble("startupTime", profile.startupTime);
        profile.activeTime = json.optDouble("activeTime", profile.activeTime);
        profile.recoveryTime = json.optDouble("recoveryTime", profile.recoveryTime);
        profile.range = json.optDouble("range", profile.range);
        profile.staminaCost = json.optDouble("staminaCost", profile.staminaCost);
        profile.ammoCost = json.optInt("ammoCost", profile.ammoCost);
        profile.projectileDefinitionId = json.optString("projectileDefinitionId", profile.projectileDefinitionId);
        profile.attackAnimation = json.optString("attackAnimation",
                json.optString("animationEventBinding", profile.attackAnimation));
        profile.attackSound = json.optString("attackSound",
                json.optString("soundEventBinding", profile.attackSound));
        profile.impactSound = json.optString("impactSound", profile.impactSound);
        profile.muzzleFlashEffect = json.optString("muzzleFlashEffect", profile.muzzleFlashEffect);
        profile.meleeTrailEffect = json.optString("meleeTrailEffect", profile.meleeTrailEffect);
        profile.impactEffect = json.optString("impactEffect",
                json.optString("effectEventBinding", profile.impactEffect));
        profile.animationEventBinding = json.optString("animationEventBinding", profile.animationEventBinding);
        profile.soundEventBinding = json.optString("soundEventBinding", profile.soundEventBinding);
        profile.effectEventBinding = json.optString("effectEventBinding", profile.effectEventBinding);
        profile.autoFire = json.optBoolean("autoFire", profile.autoFire);
        return profile;
    }

    public void validate(WeaponValidationResult result, WeaponDefinition definition) {
        if (id == null || id.trim().isEmpty()) {
            result.addError("attackProfiles.id", "Every attack profile needs an id.");
        }
        if (name == null || name.trim().isEmpty()) {
            result.addError("attackProfiles.name", "Every attack profile needs a name.");
        }
        if (attackType == null || attackType.trim().isEmpty()) {
            result.addError("attackProfiles.attackType", "Attack type is required.");
        }
        if (cooldown < 0 || startupTime < 0 || activeTime < 0 || recoveryTime < 0) {
            result.addError("attackProfiles.timing", "Attack timings must be non-negative.");
        }
        if ("meleeHitbox".equalsIgnoreCase(attackType) && activeTime <= 0) {
            result.addError("attackProfiles.activeTime", "Melee attacks need an active hit window greater than zero.");
        }
        if (damageMultiplier <= 0) {
            result.addError("attackProfiles.damageMultiplier", "Damage multiplier must be greater than zero.");
        }
        if (ammoCost < 0) {
            result.addError("attackProfiles.ammoCost", "Ammo cost cannot be negative.");
        }
        if (ammoCost > 0 && (definition.getAmmoDefinition() == null || !definition.getAmmoDefinition().isUsesAmmo())) {
            result.addError("attackProfiles.ammoCost", "Ammo cost requires ammo settings.");
        }
        if (requiresProjectile() && (projectileDefinitionId == null || projectileDefinitionId.trim().isEmpty())
                && definition.findProjectileDefinition(id) == null) {
            result.addError("attackProfiles.projectileDefinitionId", "Projectile and hitscan attacks require a projectile definition.");
        }
    }

    public boolean requiresProjectile() {
        return "projectile".equalsIgnoreCase(attackType);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInputAction() {
        return inputAction;
    }

    public void setInputAction(String inputAction) {
        this.inputAction = inputAction;
    }

    public String getAttackType() {
        return attackType;
    }

    public void setAttackType(String attackType) {
        this.attackType = attackType;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public double getCooldown() {
        return cooldown;
    }

    public void setCooldown(double cooldown) {
        this.cooldown = cooldown;
    }

    public double getStartupTime() {
        return startupTime;
    }

    public void setStartupTime(double startupTime) {
        this.startupTime = startupTime;
    }

    public double getActiveTime() {
        return activeTime;
    }

    public void setActiveTime(double activeTime) {
        this.activeTime = activeTime;
    }

    public double getRecoveryTime() {
        return recoveryTime;
    }

    public void setRecoveryTime(double recoveryTime) {
        this.recoveryTime = recoveryTime;
    }

    public double getRange() {
        return range;
    }

    public void setRange(double range) {
        this.range = range;
    }

    public double getStaminaCost() {
        return staminaCost;
    }

    public void setStaminaCost(double staminaCost) {
        this.staminaCost = staminaCost;
    }

    public int getAmmoCost() {
        return ammoCost;
    }

    public void setAmmoCost(int ammoCost) {
        this.ammoCost = ammoCost;
    }

    public String getProjectileDefinitionId() {
        return projectileDefinitionId;
    }

    public void setProjectileDefinitionId(String projectileDefinitionId) {
        this.projectileDefinitionId = projectileDefinitionId;
    }

    public String getAnimationEventBinding() {
        return animationEventBinding;
    }

    public void setAnimationEventBinding(String animationEventBinding) {
        this.animationEventBinding = animationEventBinding;
    }

    public String getAttackAnimation() {
        return attackAnimation;
    }

    public void setAttackAnimation(String attackAnimation) {
        this.attackAnimation = attackAnimation;
    }

    public String getAttackSound() {
        return attackSound;
    }

    public void setAttackSound(String attackSound) {
        this.attackSound = attackSound;
    }

    public String getImpactSound() {
        return impactSound;
    }

    public void setImpactSound(String impactSound) {
        this.impactSound = impactSound;
    }

    public String getMuzzleFlashEffect() {
        return muzzleFlashEffect;
    }

    public void setMuzzleFlashEffect(String muzzleFlashEffect) {
        this.muzzleFlashEffect = muzzleFlashEffect;
    }

    public String getMeleeTrailEffect() {
        return meleeTrailEffect;
    }

    public void setMeleeTrailEffect(String meleeTrailEffect) {
        this.meleeTrailEffect = meleeTrailEffect;
    }

    public String getImpactEffect() {
        return impactEffect;
    }

    public void setImpactEffect(String impactEffect) {
        this.impactEffect = impactEffect;
    }

    public String getSoundEventBinding() {
        return soundEventBinding;
    }

    public void setSoundEventBinding(String soundEventBinding) {
        this.soundEventBinding = soundEventBinding;
    }

    public String getEffectEventBinding() {
        return effectEventBinding;
    }

    public void setEffectEventBinding(String effectEventBinding) {
        this.effectEventBinding = effectEventBinding;
    }

    public boolean isAutoFire() {
        return autoFire;
    }

    public void setAutoFire(boolean autoFire) {
        this.autoFire = autoFire;
    }
}
