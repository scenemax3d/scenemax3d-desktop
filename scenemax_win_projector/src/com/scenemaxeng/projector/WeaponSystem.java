package com.scenemaxeng.projector;

import com.jme3.scene.Spatial;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.common.weapons.WeaponInstance;
import com.scenemaxeng.common.weapons.WeaponPostureDefinition;
import com.scenemaxeng.common.weapons.WeaponValidationResult;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class WeaponSystem {
    private final SceneMaxApp app;
    private final WeaponAttachmentResolver attachmentResolver;
    private final Map<String, EquipmentComponent> equipmentByOwner = new LinkedHashMap<>();

    public WeaponSystem(SceneMaxApp app) {
        this.app = app;
        this.attachmentResolver = new WeaponAttachmentResolver(app);
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
            app.handleRuntimeError("Cannot equip weapon '" + definition.getId() + "': weapon definition has validation errors.");
            return null;
        }

        EquipmentSlot slot = EquipmentSlot.fromId(slotId);

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
        applyPosture(runtime, definition.getDefaultPostureId());
        equipment.equip(slot, runtime);
        return runtime;
    }

    public boolean setWeaponPosture(String ownerVarName, String postureIdOrName) {
        return setWeaponPosture(ownerVarName, "rightHand", postureIdOrName);
    }

    public boolean setWeaponPosture(String ownerVarName, String slotId, String postureIdOrName) {
        EquippedWeaponRuntime runtime = getEquippedWeapon(ownerVarName, slotId);
        if (runtime == null) {
            app.handleRuntimeError("Cannot set weapon posture: no weapon is equipped on '" + ownerVarName + "'.");
            return false;
        }
        return applyPosture(runtime, postureIdOrName);
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
        return true;
    }

    public void update(float tpf) {
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
    }

    private boolean applyPosture(EquippedWeaponRuntime runtime, String postureIdOrName) {
        if (runtime == null || runtime.getWeaponDefinition() == null) {
            return false;
        }
        WeaponPostureDefinition posture = runtime.getWeaponDefinition().findPostureOrNull(postureIdOrName);
        if (posture == null) {
            app.handleRuntimeError("Cannot set weapon posture: posture '" + postureIdOrName + "' was not found on weapon '"
                    + runtime.getWeaponDefinition().getId() + "'.");
            return false;
        }
        String resolvedPostureId = posture.getId();
        runtime.detachModel();
        Spatial spawnedModel = attachmentResolver.attachWeaponModel(runtime.getOwnerCharacterId(),
                runtime.getWeaponDefinition(), resolvedPostureId);
        runtime.setSpawnedModel(spawnedModel);
        runtime.setCurrentPostureId(resolvedPostureId);
        return spawnedModel != null;
    }

    private WeaponDefinition resolveWeaponDefinition(String weaponNameOrId) {
        if (weaponNameOrId == null || weaponNameOrId.trim().isEmpty() || app.getAssetsMapping() == null) {
            return null;
        }
        return app.getAssetsMapping().getWeaponsIndex().get(weaponNameOrId.trim().toLowerCase(Locale.ROOT));
    }

}
