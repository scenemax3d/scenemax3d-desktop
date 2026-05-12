package com.scenemaxeng.projector;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.common.weapons.AttackProfile;
import com.scenemaxeng.common.weapons.DamageProfile;
import com.scenemaxeng.common.weapons.ProjectileDefinition;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.common.weapons.WeaponInstance;
import com.scenemaxeng.common.weapons.WeaponValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class WeaponSystem {
    private static final int MAX_EVENT_QUEUE_SIZE = 128;

    private final SceneMaxApp app;
    private final WeaponAttachmentResolver attachmentResolver;
    private final WeaponFeedbackPlayer feedbackPlayer;
    private final Map<String, EquipmentComponent> equipmentByOwner = new LinkedHashMap<>();
    private final Map<String, WeaponDamageEvent> lastDamageEventByOwner = new LinkedHashMap<>();
    private final List<WeaponProjectileRuntime> activeProjectiles = new ArrayList<>();
    private final LinkedList<WeaponRuntimeEvent> eventQueue = new LinkedList<>();
    private WeaponRuntimeEvent lastEvent;
    private long eventSequence;

    public WeaponSystem(SceneMaxApp app) {
        this.app = app;
        this.attachmentResolver = new WeaponAttachmentResolver(app);
        this.feedbackPlayer = new WeaponFeedbackPlayer(app);
    }

    public EquippedWeaponRuntime equipWeapon(String ownerVarName, String weaponNameOrId, String slotId) {
        if (ownerVarName == null || ownerVarName.trim().isEmpty()) {
            app.handleRuntimeError("Cannot equip weapon: owner is empty.");
            return null;
        }
        WeaponDefinition definition = resolveWeaponDefinition(weaponNameOrId);
        if (definition == null) {
            app.handleRuntimeError("Cannot equip weapon: weapon '" + weaponNameOrId + "' was not found.");
            return null;
        }

        WeaponValidationResult validation = definition.validate();
        if (!validation.isValid()) {
            app.handleRuntimeError("Cannot equip weapon '" + definition.getName() + "': weapon definition has validation errors.");
            return null;
        }

        EquipmentSlot slot = EquipmentSlot.fromId(slotId);
        if (!isSlotAllowed(definition, slot)) {
            app.handleRuntimeError("Cannot equip weapon '" + definition.getName() + "' to slot '" + slot.getId() + "'.");
            return null;
        }

        if (app.getAppModel(ownerVarName) == null) {
            app.handleRuntimeError("Cannot equip weapon: character '" + ownerVarName + "' was not found.");
            return null;
        }

        EquipmentComponent equipment = equipmentByOwner.computeIfAbsent(ownerVarName, EquipmentComponent::new);
        EquippedWeaponRuntime existing = equipment.unequip(slot);
        if (existing != null) {
            existing.detachModel();
        }

        WeaponInstance instance = new WeaponInstance();
        instance.setDefinitionId(definition.getId());
        instance.setOwnerId(ownerVarName);
        instance.setEquipped(true);
        instance.setEquippedSlot(slot.getId());

        EquippedWeaponRuntime runtime = new EquippedWeaponRuntime(ownerVarName, instance.getInstanceId(), definition, slot);
        Spatial spawnedModel = attachmentResolver.attachWeaponModel(ownerVarName, definition);
        runtime.setSpawnedModel(spawnedModel);
        equipment.equip(slot, runtime);
        feedbackPlayer.playEquipFeedback(runtime);
        emitEvent(WeaponRuntimeEvent.EQUIPPED, runtime, null, null, ammoData(runtime));
        return runtime;
    }

    public boolean unequipWeapon(String ownerVarName, String slotId) {
        EquipmentComponent equipment = equipmentByOwner.get(ownerVarName);
        if (equipment == null) {
            return false;
        }
        EquipmentSlot slot = EquipmentSlot.fromId(slotId);
        EquippedWeaponRuntime runtime = equipment.unequip(slot);
        if (runtime == null) {
            return false;
        }
        runtime.detachModel();
        feedbackPlayer.playUnequipFeedback(runtime);
        emitEvent(WeaponRuntimeEvent.UNEQUIPPED, runtime, null, null, ammoData(runtime));
        return true;
    }

    public void update(float tpf) {
        for (EquipmentComponent equipment : equipmentByOwner.values()) {
            for (EquippedWeaponRuntime runtime : equipment.getEquippedWeapons()) {
                if (runtime.update(tpf)) {
                    emitEvent(WeaponRuntimeEvent.RELOAD_COMPLETED, runtime, null, null, ammoData(runtime));
                }
            }
        }
        Iterator<WeaponProjectileRuntime> iterator = activeProjectiles.iterator();
        while (iterator.hasNext()) {
            WeaponProjectileRuntime projectile = iterator.next();
            List<WeaponDamageEvent> damageEvents = projectile.update(tpf);
            for (WeaponDamageEvent event : damageEvents) {
                lastDamageEventByOwner.put(event.getAttackerVarName(), event);
                feedbackPlayer.playHitFeedback(projectile.getWeapon(), event);
                emitEvent(WeaponRuntimeEvent.HIT_RESOLVED, projectile.getWeapon(), event.getTargetVarName(), event,
                        damageData(event).put("source", "projectile"));
            }
            if (projectile.isExpired()) {
                emitEvent(WeaponRuntimeEvent.PROJECTILE_EXPIRED, projectile.getWeapon(), null, null,
                        projectileData(projectile).put("reason", projectile.isExpiredByImpact() ? "impact" : "lifetime"));
                iterator.remove();
            }
        }
    }

    public WeaponAttackResult beginAttack(String ownerVarName, String inputActionOrAttackId) {
        return beginAttack(ownerVarName, "rightHand", inputActionOrAttackId);
    }

    public WeaponAttackResult beginAttack(String ownerVarName, String slotId, String inputActionOrAttackId) {
        EquippedWeaponRuntime runtime = getEquippedWeapon(ownerVarName, slotId);
        if (runtime == null) {
            return WeaponAttackResult.failed("weapon_not_equipped", null);
        }
        WeaponAttackResult result = runtime.beginAttack(inputActionOrAttackId);
        if (result.isSuccess()) {
            feedbackPlayer.playAttackFeedback(runtime);
            emitEvent(WeaponRuntimeEvent.ATTACK_STARTED, runtime, null, null, ammoData(runtime));
        } else {
            feedbackPlayer.playAttackRejectedFeedback(runtime, result.getReason());
            emitEvent(WeaponRuntimeEvent.ATTACK_REJECTED, runtime, null, null,
                    ammoData(runtime).put("reason", result.getReason()));
        }
        return result;
    }

    public boolean beginReload(String ownerVarName) {
        return beginReload(ownerVarName, "rightHand");
    }

    public boolean beginReload(String ownerVarName, String slotId) {
        EquippedWeaponRuntime runtime = getEquippedWeapon(ownerVarName, slotId);
        if (runtime == null) {
            return false;
        }
        boolean started = runtime.beginReload();
        if (started) {
            feedbackPlayer.playReloadFeedback(runtime);
            emitEvent(WeaponRuntimeEvent.RELOAD_STARTED, runtime, null, null, ammoData(runtime));
        }
        return started;
    }

    public WeaponDamageEvent resolveHit(String ownerVarName, String targetVarName) {
        return resolveHit(ownerVarName, "rightHand", targetVarName);
    }

    public WeaponDamageEvent resolveHit(String ownerVarName, String slotId, String targetVarName) {
        EquippedWeaponRuntime runtime = getEquippedWeapon(ownerVarName, slotId);
        if (runtime == null || !runtime.canHitTarget(targetVarName) || !isTargetInRange(runtime, targetVarName)) {
            return null;
        }
        WeaponDamageEvent event = runtime.registerHit(targetVarName);
        if (event != null) {
            lastDamageEventByOwner.put(ownerVarName, event);
            feedbackPlayer.playHitFeedback(runtime, event);
            emitEvent(WeaponRuntimeEvent.HIT_RESOLVED, runtime, targetVarName, event,
                    damageData(event).put("source", "melee"));
        }
        return event;
    }

    public WeaponDamageEvent getLastDamageEvent(String ownerVarName) {
        return lastDamageEventByOwner.get(ownerVarName);
    }

    public WeaponRuntimeEvent getLastEvent() {
        return lastEvent;
    }

    public WeaponRuntimeEvent getLastEvent(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            return lastEvent;
        }
        String normalizedType = eventType.trim();
        for (int i = eventQueue.size() - 1; i >= 0; i--) {
            WeaponRuntimeEvent event = eventQueue.get(i);
            if (event.getType().equalsIgnoreCase(normalizedType)) {
                return event;
            }
        }
        return null;
    }

    public boolean hasEvent(String eventType) {
        return getLastEvent(eventType) != null;
    }

    public List<WeaponRuntimeEvent> getQueuedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(eventQueue));
    }

    public List<WeaponRuntimeEvent> drainEvents() {
        List<WeaponRuntimeEvent> events = new ArrayList<>(eventQueue);
        eventQueue.clear();
        return events;
    }

    public JSONArray drainEventsAsJSON() {
        JSONArray arr = new JSONArray();
        for (WeaponRuntimeEvent event : drainEvents()) {
            arr.put(event.toJSON());
        }
        return arr;
    }

    public WeaponAttackResult fireWeapon(String ownerVarName, String inputActionOrAttackId) {
        return fireWeapon(ownerVarName, "rightHand", inputActionOrAttackId, null, null);
    }

    public WeaponAttackResult fireWeapon(String ownerVarName, String slotId, String inputActionOrAttackId,
                                         Vector3f origin, Vector3f direction) {
        WeaponAttackResult attackResult = beginAttack(ownerVarName, slotId, inputActionOrAttackId);
        if (!attackResult.isSuccess()) {
            return attackResult;
        }

        EquippedWeaponRuntime runtime = attackResult.getWeapon();
        AttackProfile attackProfile = runtime.getActiveAttackProfile();
        Vector3f resolvedOrigin = origin != null ? origin.clone() : resolveMuzzleOrigin(runtime);
        Vector3f resolvedDirection = direction != null && direction.lengthSquared() > 0
                ? direction.normalize()
                : resolveForwardDirection(runtime);

        if ("projectile".equalsIgnoreCase(attackProfile.getAttackType())) {
            spawnProjectile(runtime, attackProfile, resolvedOrigin, resolvedDirection);
        } else if ("hitscan".equalsIgnoreCase(attackProfile.getAttackType())) {
            WeaponDamageEvent event = resolveHitscan(runtime, attackProfile, resolvedOrigin, resolvedDirection);
            if (event != null) {
                lastDamageEventByOwner.put(ownerVarName, event);
                feedbackPlayer.playHitFeedback(runtime, event);
                emitEvent(WeaponRuntimeEvent.HIT_RESOLVED, runtime, event.getTargetVarName(), event,
                        damageData(event).put("source", "hitscan"));
            }
        }

        return attackResult;
    }

    public WeaponAttackResult fireWeaponAt(String ownerVarName, String targetVarName, String inputActionOrAttackId) {
        return fireWeaponAt(ownerVarName, "rightHand", targetVarName, inputActionOrAttackId);
    }

    public WeaponAttackResult fireWeaponAt(String ownerVarName, String slotId, String targetVarName, String inputActionOrAttackId) {
        EquippedWeaponRuntime runtime = getEquippedWeapon(ownerVarName, slotId);
        Vector3f origin = runtime != null ? resolveMuzzleOrigin(runtime) : null;
        Vector3f direction = resolveDirectionToTarget(origin, targetVarName);
        return fireWeapon(ownerVarName, slotId, inputActionOrAttackId, origin, direction);
    }

    public EquippedWeaponRuntime getEquippedWeapon(String ownerVarName, String slotId) {
        EquipmentComponent equipment = equipmentByOwner.get(ownerVarName);
        return equipment == null ? null : equipment.getWeapon(slotId);
    }

    public EquipmentComponent getEquipment(String ownerVarName) {
        return equipmentByOwner.get(ownerVarName);
    }

    public void clear() {
        for (EquipmentComponent equipment : equipmentByOwner.values()) {
            for (EquippedWeaponRuntime runtime : equipment.getEquippedWeapons()) {
                runtime.detachModel();
            }
        }
        equipmentByOwner.clear();
        for (WeaponProjectileRuntime projectile : activeProjectiles) {
            projectile.expire();
        }
        activeProjectiles.clear();
        lastDamageEventByOwner.clear();
        eventQueue.clear();
        lastEvent = null;
    }

    private WeaponDefinition resolveWeaponDefinition(String weaponNameOrId) {
        if (weaponNameOrId == null || weaponNameOrId.trim().isEmpty() || app.getAssetsMapping() == null) {
            return null;
        }
        return app.getAssetsMapping().getWeaponsIndex().get(weaponNameOrId.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isSlotAllowed(WeaponDefinition definition, EquipmentSlot slot) {
        if (definition.getAllowedEquipmentSlots() == null || definition.getAllowedEquipmentSlots().isEmpty()) {
            return true;
        }
        for (String allowed : definition.getAllowedEquipmentSlots()) {
            if (allowed != null && allowed.equalsIgnoreCase(slot.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTargetInRange(EquippedWeaponRuntime runtime, String targetVarName) {
        double range = runtime.getActiveAttackProfile() != null ? runtime.getActiveAttackProfile().getRange() : 0;
        if (range <= 0) {
            return true;
        }
        Spatial owner = app.getEntitySpatial(runtime.getOwnerCharacterId());
        Spatial target = app.getEntitySpatial(targetVarName);
        if (owner == null || target == null) {
            return false;
        }
        return owner.getWorldTranslation().distance(target.getWorldTranslation()) <= range;
    }

    private void spawnProjectile(EquippedWeaponRuntime runtime, AttackProfile attackProfile, Vector3f origin, Vector3f direction) {
        ProjectileDefinition projectileDefinition = runtime.getWeaponDefinition()
                .findProjectileDefinition(resolveProjectileId(attackProfile));
        if (projectileDefinition == null) {
            app.handleRuntimeError("Cannot fire weapon '" + runtime.getWeaponDefinition().getName() + "': projectile definition was not found.");
            return;
        }
        Spatial projectileModel = loadProjectileModel(projectileDefinition);
        projectileModel.setName("weapon_projectile_" + runtime.getWeaponInstanceId() + "_" + activeProjectiles.size());
        app.getRootNode().attachChild(projectileModel);
        WeaponProjectileRuntime projectile = new WeaponProjectileRuntime(app, runtime, attackProfile, projectileDefinition,
                projectileModel, origin, direction);
        activeProjectiles.add(projectile);
        emitEvent(WeaponRuntimeEvent.PROJECTILE_SPAWNED, runtime, null, null, projectileData(projectile));
    }

    private WeaponDamageEvent resolveHitscan(EquippedWeaponRuntime runtime, AttackProfile attackProfile,
                                             Vector3f origin, Vector3f direction) {
        if (origin == null || direction == null || direction.lengthSquared() == 0) {
            return null;
        }
        Vector3f normalizedDirection = direction.normalize();
        double maxRange = attackProfile.getRange() > 0 ? attackProfile.getRange() : 1000;
        String closestTarget = null;
        double closestDistance = Double.MAX_VALUE;
        for (EntityInstBase inst : app.getRuntimeEntityInstances()) {
            if (inst == null || inst.varDef == null || inst.scope == null) {
                continue;
            }
            String targetVarName = inst.getVarRunTimeName();
            if (targetVarName.equals(runtime.getOwnerCharacterId())) {
                continue;
            }
            Spatial target = app.getEntitySpatial(targetVarName, inst.varDef.varType);
            if (target == null) {
                continue;
            }
            Vector3f toTarget = target.getWorldTranslation().subtract(origin);
            float projectedDistance = toTarget.dot(normalizedDirection);
            if (projectedDistance < 0 || projectedDistance > maxRange) {
                continue;
            }
            Vector3f closestPoint = origin.add(normalizedDirection.mult(projectedDistance));
            double missDistance = target.getWorldTranslation().distance(closestPoint);
            if (missDistance <= 0.35 && projectedDistance < closestDistance) {
                closestDistance = projectedDistance;
                closestTarget = targetVarName;
            }
        }
        if (closestTarget == null) {
            return null;
        }
        ProjectileDefinition projectileDefinition = runtime.getWeaponDefinition()
                .findProjectileDefinition(resolveProjectileId(attackProfile));
        DamageProfile damageProfile = projectileDefinition != null && projectileDefinition.getDamageProfileOverride() != null
                ? projectileDefinition.getDamageProfileOverride()
                : runtime.getWeaponDefinition().getDamageProfile();
        return runtime.createDamageEvent(closestTarget, attackProfile, damageProfile);
    }

    private String resolveProjectileId(AttackProfile attackProfile) {
        if (attackProfile.getProjectileDefinitionId() != null && !attackProfile.getProjectileDefinitionId().trim().isEmpty()) {
            return attackProfile.getProjectileDefinitionId();
        }
        return attackProfile.getId();
    }

    private Spatial loadProjectileModel(ProjectileDefinition projectileDefinition) {
        String modelAssetId = projectileDefinition.getModelAssetId();
        if (modelAssetId == null || modelAssetId.trim().isEmpty()) {
            return new Node();
        }

        String modelPath = modelAssetId.trim();
        AssetsMapping assetsMapping = app.getAssetsMapping();
        if (assetsMapping != null) {
            ResourceSetup resource = assetsMapping.get3DModelsIndex().get(modelPath.toLowerCase(Locale.ROOT));
            if (resource != null) {
                modelPath = resource.path;
            }
        }

        try {
            return app.getAssetManager().loadModel(modelPath);
        } catch (Exception ex) {
            app.handleRuntimeError("Projectile '" + projectileDefinition.getName() + "' could not load model '" + modelAssetId + "'.");
            return new Node();
        }
    }

    private Vector3f resolveMuzzleOrigin(EquippedWeaponRuntime runtime) {
        if (runtime.getSpawnedModel() != null) {
            return runtime.getSpawnedModel().getWorldTranslation().clone();
        }
        Spatial owner = app.getEntitySpatial(runtime.getOwnerCharacterId());
        return owner != null ? owner.getWorldTranslation().clone() : Vector3f.ZERO.clone();
    }

    private Vector3f resolveForwardDirection(EquippedWeaponRuntime runtime) {
        Spatial source = runtime.getSpawnedModel() != null
                ? runtime.getSpawnedModel()
                : app.getEntitySpatial(runtime.getOwnerCharacterId());
        if (source == null) {
            return Vector3f.UNIT_Z.clone();
        }
        return source.getWorldRotation().mult(Vector3f.UNIT_Z).normalizeLocal();
    }

    private Vector3f resolveDirectionToTarget(Vector3f origin, String targetVarName) {
        Spatial target = app.getEntitySpatial(targetVarName);
        if (origin == null || target == null) {
            return null;
        }
        Vector3f direction = target.getWorldTranslation().subtract(origin);
        return direction.lengthSquared() > 0 ? direction.normalizeLocal() : null;
    }

    private void emitEvent(String eventType, EquippedWeaponRuntime runtime, String targetVarName,
                           WeaponDamageEvent damageEvent, JSONObject data) {
        WeaponRuntimeEvent event = new WeaponRuntimeEvent(++eventSequence, eventType, runtime, targetVarName, damageEvent, data);
        eventQueue.add(event);
        lastEvent = event;
        while (eventQueue.size() > MAX_EVENT_QUEUE_SIZE) {
            eventQueue.removeFirst();
        }
    }

    private JSONObject ammoData(EquippedWeaponRuntime runtime) {
        JSONObject data = new JSONObject();
        if (runtime != null) {
            data.put("currentAmmo", runtime.getCurrentAmmo());
            data.put("reserveAmmo", runtime.getReserveAmmo());
            data.put("state", runtime.getCurrentState().name());
            data.put("cooldownTimer", runtime.getCooldownTimer());
            data.put("reloadTimer", runtime.getReloadTimer());
        }
        return data;
    }

    private JSONObject damageData(WeaponDamageEvent event) {
        JSONObject data = new JSONObject();
        if (event != null) {
            data.put("damageAmount", event.getDamageAmount());
            data.put("damageType", event.getDamageType());
            data.put("criticalHit", event.isCriticalHit());
            data.put("knockback", event.getKnockback());
            data.put("stunDuration", event.getStunDuration());
            data.put("armorPenetration", event.getArmorPenetration());
        }
        return data;
    }

    private JSONObject projectileData(WeaponProjectileRuntime projectile) {
        JSONObject data = new JSONObject();
        if (projectile != null && projectile.getProjectileDefinition() != null) {
            data.put("projectileId", projectile.getProjectileDefinition().getId());
            data.put("projectileName", projectile.getProjectileDefinition().getName());
            data.put("speed", projectile.getProjectileDefinition().getSpeed());
            data.put("lifetime", projectile.getProjectileDefinition().getLifetime());
            data.put("pierceCount", projectile.getProjectileDefinition().getPierceCount());
            data.put("explodes", projectile.getProjectileDefinition().isExplodeOnImpact());
        }
        return data;
    }
}
