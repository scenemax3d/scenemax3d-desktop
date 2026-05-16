package com.scenemaxeng.projector;

import com.jme3.scene.Spatial;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.common.weapons.WeaponInstance;
import com.scenemaxeng.common.weapons.WeaponPostureDefinition;
import com.scenemaxeng.common.weapons.WeaponValidationResult;
import com.scenemaxeng.compiler.VariableDef;

import java.util.LinkedHashMap;
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
            existing.detachModel(app);
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
        runtime.detachModel(app);
        return true;
    }

    public boolean detachWeapon(String ownerVarName, String slotId) {
        EquippedWeaponRuntime runtime = getEquippedWeapon(ownerVarName, slotId);
        if (runtime == null) {
            app.handleRuntimeError("Cannot detach weapon: no weapon is equipped on '" + ownerVarName + "'.");
            return false;
        }
        Spatial spawnedModel = runtime.getSpawnedModel();
        if (spawnedModel == null) {
            app.handleRuntimeError("Cannot detach weapon: weapon model is not available on '" + ownerVarName + "'.");
            return false;
        }
        if (runtime.isDetachedFromOwner()) {
            return true;
        }

        Vector3f worldTranslation = spawnedModel.getWorldTranslation().clone();
        Quaternion worldRotation = spawnedModel.getWorldRotation().clone();
        Vector3f worldScale = spawnedModel.getWorldScale().clone();

        spawnedModel.removeFromParent();
        app.getRootNode().attachChild(spawnedModel);
        spawnedModel.setLocalTranslation(app.getRootNode().worldToLocal(worldTranslation, null));
        spawnedModel.setLocalRotation(worldRotation);
        spawnedModel.setLocalScale(worldScale);
        spawnedModel.updateLogicalState(0f);
        spawnedModel.updateGeometricState();
        runtime.setDetachedFromOwner(true);
        return true;
    }

    public boolean attachWeapon(String ownerVarName, String slotId) {
        EquippedWeaponRuntime runtime = getEquippedWeapon(ownerVarName, slotId);
        if (runtime == null) {
            app.handleRuntimeError("Cannot attach weapon: no weapon is equipped on '" + ownerVarName + "'.");
            return false;
        }
        if (!runtime.isDetachedFromOwner()) {
            return true;
        }
        return applyPosture(runtime, runtime.getCurrentPostureId());
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
                runtime.detachModel(app);
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
        runtime.detachModel(app);
        Spatial spawnedModel = attachmentResolver.attachWeaponModel(runtime.getOwnerCharacterId(),
                runtime.getWeaponDefinition(), resolvedPostureId);
        runtime.setSpawnedModel(spawnedModel);
        runtime.setRegisteredColliderNames(attachmentResolver.getLastRegisteredColliderNames());
        runtime.setRegisteredModelName(registerWeaponModel(runtime, spawnedModel));
        runtime.setCurrentPostureId(resolvedPostureId);
        runtime.setDetachedFromOwner(false);
        return spawnedModel != null;
    }

    private String registerWeaponModel(EquippedWeaponRuntime runtime, Spatial spawnedModel) {
        if (!(spawnedModel instanceof Node) || runtime == null || runtime.getWeaponDefinition() == null) {
            return null;
        }
        AppModel ownerModel = app.getAppModel(runtime.getOwnerCharacterId());
        SceneMaxScope ownerScope = ownerModel != null && ownerModel.entityInst != null
                ? ownerModel.entityInst.scope
                : null;
        String weaponName = runtime.getWeaponDefinition().getId() == null
                ? ""
                : runtime.getWeaponDefinition().getId().trim();
        if (ownerScope == null || weaponName.isEmpty()) {
            return null;
        }

        VariableDef varDef = new VariableDef();
        varDef.varName = weaponName;
        varDef.varType = VariableDef.VAR_TYPE_3D;
        varDef.resName = runtime.getWeaponDefinition().getModelAssetId();
        ModelInst inst = new ModelInst(null, varDef, ownerScope);
        String runtimeName = weaponName + "@" + ownerScope.scopeId;
        app.registerWeaponModel(runtimeName, (Node) spawnedModel, inst);
        return runtimeName;
    }

    private WeaponDefinition resolveWeaponDefinition(String weaponNameOrId) {
        if (weaponNameOrId == null || weaponNameOrId.trim().isEmpty() || app.getAssetsMapping() == null) {
            return null;
        }
        return app.getAssetsMapping().getWeaponDefinition(weaponNameOrId);
    }

}
