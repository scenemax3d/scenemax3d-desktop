package com.scenemaxeng.projector;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EquipmentComponent {
    private final String ownerVarName;
    private final Map<String, EquippedWeaponRuntime> equippedWeapons = new LinkedHashMap<>();

    public EquipmentComponent(String ownerVarName) {
        this.ownerVarName = ownerVarName;
    }

    public String getOwnerVarName() {
        return ownerVarName;
    }

    public EquippedWeaponRuntime getWeapon(String slot) {
        return equippedWeapons.get(EquipmentSlot.fromId(slot).getId());
    }

    public void equip(EquipmentSlot slot, EquippedWeaponRuntime runtime) {
        equippedWeapons.put(slot.getId(), runtime);
    }

    public EquippedWeaponRuntime unequip(EquipmentSlot slot) {
        return equippedWeapons.remove(slot.getId());
    }

    public Collection<EquippedWeaponRuntime> getEquippedWeapons() {
        return Collections.unmodifiableCollection(equippedWeapons.values());
    }
}
