package com.scenemaxeng.projector;

import com.jme3.scene.Spatial;
import com.scenemaxeng.common.weapons.WeaponDefinition;

public class EquippedWeaponRuntime {
    private final String ownerCharacterId;
    private final String weaponInstanceId;
    private final WeaponDefinition weaponDefinition;
    private final EquipmentSlot equipmentSlot;
    private String currentPostureId;
    private Spatial spawnedModel;

    public EquippedWeaponRuntime(String ownerCharacterId, String weaponInstanceId,
                                 WeaponDefinition weaponDefinition, EquipmentSlot equipmentSlot) {
        this.ownerCharacterId = ownerCharacterId;
        this.weaponInstanceId = weaponInstanceId;
        this.weaponDefinition = weaponDefinition;
        this.equipmentSlot = equipmentSlot;
        this.currentPostureId = weaponDefinition == null ? "" : weaponDefinition.getDefaultPostureId();
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

    public String getCurrentPostureId() {
        return currentPostureId;
    }

    public void setCurrentPostureId(String currentPostureId) {
        this.currentPostureId = currentPostureId;
    }

    public Spatial getSpawnedModel() {
        return spawnedModel;
    }

    public void setSpawnedModel(Spatial spawnedModel) {
        this.spawnedModel = spawnedModel;
    }
}
