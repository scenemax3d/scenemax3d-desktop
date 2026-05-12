package com.scenemaxeng.projector;

import com.jme3.bounding.BoundingVolume;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.weapons.AttackProfile;
import com.scenemaxeng.common.weapons.DamageProfile;
import com.scenemaxeng.common.weapons.ProjectileDefinition;
import com.scenemaxeng.common.weapons.WeaponAttackInstance;
import com.scenemaxeng.common.weapons.WeaponProjectilePath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WeaponProjectileRuntime {
    private static final float GRAVITY = 9.81f;

    private final SceneMaxApp app;
    private final EquippedWeaponRuntime weapon;
    private final AttackProfile attackProfile;
    private final ProjectileDefinition projectileDefinition;
    private final Spatial spatial;
    private final Vector3f velocity;
    private final Vector3f origin;
    private final WeaponProjectilePath pathOverride;
    private final Set<String> hitTargets = new HashSet<>();
    private double age;
    private int remainingPierces;
    private boolean expired;
    private boolean expiredByImpact;

    public WeaponProjectileRuntime(SceneMaxApp app, EquippedWeaponRuntime weapon, AttackProfile attackProfile,
                                   ProjectileDefinition projectileDefinition, Spatial spatial,
                                   Vector3f origin, Vector3f direction) {
        this.app = app;
        this.weapon = weapon;
        this.attackProfile = attackProfile;
        this.projectileDefinition = projectileDefinition;
        this.spatial = spatial;
        this.remainingPierces = Math.max(0, projectileDefinition.getPierceCount());
        this.origin = origin == null ? Vector3f.ZERO.clone() : origin.clone();
        this.pathOverride = attackProfile instanceof WeaponAttackInstance
                ? ((WeaponAttackInstance) attackProfile).getProjectilePathOverride()
                : null;
        Vector3f normalizedDirection = direction == null || direction.lengthSquared() == 0
                ? Vector3f.UNIT_Z.clone()
                : direction.normalize();
        this.velocity = normalizedDirection.mult((float) projectileDefinition.getSpeed());
        if (spatial != null) {
            spatial.setLocalTranslation(this.origin);
            spatial.lookAt(this.origin.add(normalizedDirection), Vector3f.UNIT_Y);
        }
    }

    public List<WeaponDamageEvent> update(float tpf) {
        List<WeaponDamageEvent> damageEvents = new ArrayList<>();
        if (expired) {
            return damageEvents;
        }

        age += tpf;
        if (age >= projectileDefinition.getLifetime()) {
            expire();
            return damageEvents;
        }

        updateProjectileMotion(tpf);

        String directHit = findDirectHitTarget();
        if (directHit != null) {
            if (projectileDefinition.isExplodeOnImpact() && projectileDefinition.getExplosionRadius() > 0) {
                damageEvents.addAll(resolveExplosionHits(directHit));
            } else {
                WeaponDamageEvent damageEvent = resolveProjectileHit(directHit);
                if (damageEvent != null) {
                    damageEvents.add(damageEvent);
                }
            }

            if (remainingPierces > 0 && !projectileDefinition.isExplodeOnImpact()) {
                remainingPierces--;
            } else {
                expireByImpact();
            }
        }

        return damageEvents;
    }

    private void updateProjectileMotion(float tpf) {
        if (spatial == null) {
            return;
        }
        if (pathOverride != null && pathOverride.hasUsablePoints()) {
            Vector3f previous = spatial.getWorldTranslation().clone();
            Vector3f next = resolvePathPosition(age);
            spatial.setLocalTranslation(next);
            Vector3f delta = next.subtract(previous);
            if (delta.lengthSquared() > 0.0001f) {
                spatial.lookAt(next.add(delta.normalize()), Vector3f.UNIT_Y);
            }
            return;
        }
        if (projectileDefinition.getGravityScale() != 0) {
            velocity.y -= GRAVITY * projectileDefinition.getGravityScale() * tpf;
        }
        spatial.move(velocity.mult(tpf));
        if (velocity.lengthSquared() > 0.0001f) {
            spatial.lookAt(spatial.getWorldTranslation().add(velocity.normalize()), Vector3f.UNIT_Y);
        }
    }

    private Vector3f resolvePathPosition(double pathTime) {
        List<WeaponProjectilePath.Point> points = pathOverride.getReadOnlyPoints();
        WeaponProjectilePath.Point previous = points.get(0);
        WeaponProjectilePath.Point next = points.get(points.size() - 1);
        for (int i = 1; i < points.size(); i++) {
            next = points.get(i);
            if (pathTime <= next.getTime()) {
                break;
            }
            previous = next;
        }
        double segmentDuration = Math.max(0.0001, next.getTime() - previous.getTime());
        float blend = (float) Math.max(0, Math.min(1, (pathTime - previous.getTime()) / segmentDuration));
        Vector3f a = toVector(previous);
        Vector3f b = toVector(next);
        Vector3f result = a.interpolateLocal(b, blend);
        return pathOverride.isRelativeToOrigin() ? origin.add(result) : result;
    }

    private Vector3f toVector(WeaponProjectilePath.Point point) {
        return new Vector3f((float) point.getX(), (float) point.getY(), (float) point.getZ());
    }

    private String findDirectHitTarget() {
        Vector3f projectilePosition = getPosition();
        if (projectilePosition == null) {
            return null;
        }
        double radius = projectileDefinition.getCollisionRadius();
        for (EntityInstBase inst : app.getRuntimeEntityInstances()) {
            String targetVarName = runtimeName(inst);
            if (!isEligibleTarget(targetVarName)) {
                continue;
            }
            Spatial target = app.getEntitySpatial(targetVarName, inst.varDef.varType);
            if (isWithinRadius(target, projectilePosition, radius)) {
                return targetVarName;
            }
        }
        return null;
    }

    private List<WeaponDamageEvent> resolveExplosionHits(String directHit) {
        List<WeaponDamageEvent> damageEvents = new ArrayList<>();
        Vector3f explosionCenter = getPosition();
        if (explosionCenter == null) {
            return damageEvents;
        }
        WeaponDamageEvent directEvent = resolveProjectileHit(directHit);
        if (directEvent != null) {
            damageEvents.add(directEvent);
        }
        for (EntityInstBase inst : app.getRuntimeEntityInstances()) {
            String targetVarName = runtimeName(inst);
            if (!isEligibleTarget(targetVarName)) {
                continue;
            }
            Spatial target = app.getEntitySpatial(targetVarName, inst.varDef.varType);
            if (isWithinRadius(target, explosionCenter, projectileDefinition.getExplosionRadius())) {
                WeaponDamageEvent damageEvent = resolveProjectileHit(targetVarName);
                if (damageEvent != null) {
                    damageEvents.add(damageEvent);
                }
            }
        }
        return damageEvents;
    }

    private WeaponDamageEvent resolveProjectileHit(String targetVarName) {
        if (!isEligibleTarget(targetVarName)) {
            return null;
        }
        hitTargets.add(targetVarName);
        DamageProfile override = projectileDefinition.getDamageProfileOverride();
        return weapon.createDamageEvent(targetVarName, attackProfile, override);
    }

    private boolean isEligibleTarget(String targetVarName) {
        return targetVarName != null
                && !targetVarName.equals(weapon.getOwnerCharacterId())
                && !hitTargets.contains(targetVarName);
    }

    private boolean isWithinRadius(Spatial target, Vector3f center, double radius) {
        if (target == null || center == null) {
            return false;
        }
        BoundingVolume bound = target.getWorldBound();
        if (bound != null) {
            return bound.distanceToEdge(center) <= radius;
        }
        return target.getWorldTranslation().distance(center) <= radius;
    }

    private String runtimeName(EntityInstBase inst) {
        if (inst == null || inst.varDef == null || inst.scope == null) {
            return null;
        }
        return inst.getVarRunTimeName();
    }

    private Vector3f getPosition() {
        return spatial != null ? spatial.getWorldTranslation() : null;
    }

    public void expire() {
        expired = true;
        if (spatial != null) {
            spatial.removeFromParent();
        }
    }

    public void expireByImpact() {
        expiredByImpact = true;
        expire();
    }

    public boolean isExpired() {
        return expired;
    }

    public boolean isExpiredByImpact() {
        return expiredByImpact;
    }

    public EquippedWeaponRuntime getWeapon() {
        return weapon;
    }

    public ProjectileDefinition getProjectileDefinition() {
        return projectileDefinition;
    }
}
