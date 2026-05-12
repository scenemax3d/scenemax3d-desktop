package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class ProjectileDefinition {
    private String id = "projectile";
    private String name = "Projectile";
    private String modelAssetId = "";
    private double speed = 30.0;
    private double gravityScale = 0.0;
    private double lifetime = 5.0;
    private double collisionRadius = 0.2;
    private int pierceCount = 0;
    private boolean explodeOnImpact = false;
    private double explosionRadius = 0.0;
    private String trailEffectId = "";
    private String impactEffectId = "";
    private DamageProfile damageProfileOverride;

    public JSONObject toJSON() {
        JSONObject json = new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("modelAssetId", modelAssetId)
                .put("speed", speed)
                .put("gravityScale", gravityScale)
                .put("lifetime", lifetime)
                .put("collisionRadius", collisionRadius)
                .put("pierceCount", pierceCount)
                .put("explodeOnImpact", explodeOnImpact)
                .put("explosionRadius", explosionRadius)
                .put("trailEffectId", trailEffectId)
                .put("impactEffectId", impactEffectId);
        if (damageProfileOverride != null) {
            json.put("damageProfileOverride", damageProfileOverride.toJSON());
        }
        return json;
    }

    public static ProjectileDefinition fromJSON(JSONObject json) {
        ProjectileDefinition projectile = new ProjectileDefinition();
        if (json == null) {
            return projectile;
        }
        projectile.id = json.optString("id", projectile.id);
        projectile.name = json.optString("name", projectile.name);
        projectile.modelAssetId = json.optString("modelAssetId", projectile.modelAssetId);
        projectile.speed = json.optDouble("speed", projectile.speed);
        projectile.gravityScale = json.optDouble("gravityScale", projectile.gravityScale);
        projectile.lifetime = json.optDouble("lifetime", projectile.lifetime);
        projectile.collisionRadius = json.optDouble("collisionRadius", projectile.collisionRadius);
        projectile.pierceCount = json.optInt("pierceCount", projectile.pierceCount);
        projectile.explodeOnImpact = json.optBoolean("explodeOnImpact", projectile.explodeOnImpact);
        projectile.explosionRadius = json.optDouble("explosionRadius", projectile.explosionRadius);
        projectile.trailEffectId = json.optString("trailEffectId", projectile.trailEffectId);
        projectile.impactEffectId = json.optString("impactEffectId", projectile.impactEffectId);
        if (json.optJSONObject("damageProfileOverride") != null) {
            projectile.damageProfileOverride = DamageProfile.fromJSON(json.optJSONObject("damageProfileOverride"));
        }
        return projectile;
    }

    public void validate(WeaponValidationResult result) {
        if (id == null || id.trim().isEmpty()) {
            result.addError("projectileDefinitions.id", "Projectile definition id is required.");
        }
        if (speed <= 0) {
            result.addError("projectileDefinitions.speed", "Projectile speed must be greater than zero.");
        }
        if (lifetime <= 0) {
            result.addError("projectileDefinitions.lifetime", "Projectile lifetime must be greater than zero.");
        }
        if (collisionRadius <= 0) {
            result.addError("projectileDefinitions.collisionRadius", "Projectile collision radius must be greater than zero.");
        }
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

    public String getModelAssetId() {
        return modelAssetId;
    }

    public void setModelAssetId(String modelAssetId) {
        this.modelAssetId = modelAssetId;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getGravityScale() {
        return gravityScale;
    }

    public void setGravityScale(double gravityScale) {
        this.gravityScale = gravityScale;
    }

    public double getLifetime() {
        return lifetime;
    }

    public void setLifetime(double lifetime) {
        this.lifetime = lifetime;
    }

    public double getCollisionRadius() {
        return collisionRadius;
    }

    public void setCollisionRadius(double collisionRadius) {
        this.collisionRadius = collisionRadius;
    }

    public int getPierceCount() {
        return pierceCount;
    }

    public void setPierceCount(int pierceCount) {
        this.pierceCount = pierceCount;
    }

    public boolean isExplodeOnImpact() {
        return explodeOnImpact;
    }

    public void setExplodeOnImpact(boolean explodeOnImpact) {
        this.explodeOnImpact = explodeOnImpact;
    }

    public double getExplosionRadius() {
        return explosionRadius;
    }

    public void setExplosionRadius(double explosionRadius) {
        this.explosionRadius = explosionRadius;
    }

    public String getTrailEffectId() {
        return trailEffectId;
    }

    public void setTrailEffectId(String trailEffectId) {
        this.trailEffectId = trailEffectId;
    }

    public String getImpactEffectId() {
        return impactEffectId;
    }

    public void setImpactEffectId(String impactEffectId) {
        this.impactEffectId = impactEffectId;
    }

    public DamageProfile getDamageProfileOverride() {
        return damageProfileOverride;
    }

    public void setDamageProfileOverride(DamageProfile damageProfileOverride) {
        this.damageProfileOverride = damageProfileOverride;
    }
}
