package com.scenemaxeng.common.weapons;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WeaponInstance {
    private String instanceId = "weapon_instance_" + UUID.randomUUID().toString().replace("-", "");
    private String definitionId = "";
    private String ownerId = "";
    private int currentAmmo = 0;
    private double currentDurability = 1.0;
    private int upgradeLevel = 0;
    private String customName = "";
    private List<String> modifiers = new ArrayList<>();
    private boolean equipped = false;
    private String equippedSlot = "";

    public JSONObject toJSON() {
        return new JSONObject()
                .put("instanceId", instanceId)
                .put("definitionId", definitionId)
                .put("ownerId", ownerId)
                .put("currentAmmo", currentAmmo)
                .put("currentDurability", currentDurability)
                .put("upgradeLevel", upgradeLevel)
                .put("customName", customName)
                .put("modifiers", WeaponJsonUtil.toArray(modifiers))
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
        instance.currentAmmo = json.optInt("currentAmmo", instance.currentAmmo);
        instance.currentDurability = json.optDouble("currentDurability", instance.currentDurability);
        instance.upgradeLevel = json.optInt("upgradeLevel", instance.upgradeLevel);
        instance.customName = json.optString("customName", instance.customName);
        JSONArray modifiers = json.optJSONArray("modifiers");
        instance.modifiers = WeaponJsonUtil.stringList(modifiers);
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

    public int getCurrentAmmo() {
        return currentAmmo;
    }

    public void setCurrentAmmo(int currentAmmo) {
        this.currentAmmo = currentAmmo;
    }

    public double getCurrentDurability() {
        return currentDurability;
    }

    public void setCurrentDurability(double currentDurability) {
        this.currentDurability = currentDurability;
    }

    public int getUpgradeLevel() {
        return upgradeLevel;
    }

    public void setUpgradeLevel(int upgradeLevel) {
        this.upgradeLevel = upgradeLevel;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public List<String> getModifiers() {
        return modifiers;
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
