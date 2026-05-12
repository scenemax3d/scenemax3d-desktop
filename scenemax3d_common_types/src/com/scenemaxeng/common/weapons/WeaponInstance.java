package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

import java.util.UUID;

public class WeaponInstance {
    private String instanceId = "weapon_instance_" + UUID.randomUUID().toString().replace("-", "");
    private String definitionId = "";
    private String ownerId = "";
    private boolean equipped = false;
    private String equippedSlot = "";

    public JSONObject toJSON() {
        return new JSONObject()
                .put("instanceId", instanceId)
                .put("definitionId", definitionId)
                .put("ownerId", ownerId)
                .put("equipped", equipped)
                .put("equippedSlot", equippedSlot);
    }

    public static WeaponInstance fromJSON(JSONObject json) {
        WeaponInstance instance = new WeaponInstance();
        if (json == null) {
            return instance;
        }
        instance.instanceId = json.optString("instanceId", instance.instanceId);
        instance.definitionId = json.optString("definitionId", instance.definitionId);
        instance.ownerId = json.optString("ownerId", instance.ownerId);
        instance.equipped = json.optBoolean("equipped", instance.equipped);
        instance.equippedSlot = json.optString("equippedSlot", instance.equippedSlot);
        return instance;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isEquipped() {
        return equipped;
    }

    public void setEquipped(boolean equipped) {
        this.equipped = equipped;
    }

    public String getEquippedSlot() {
        return equippedSlot;
    }

    public void setEquippedSlot(String equippedSlot) {
        this.equippedSlot = equippedSlot;
    }
}
