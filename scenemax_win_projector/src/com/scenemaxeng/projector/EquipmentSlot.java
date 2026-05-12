package com.scenemaxeng.projector;

import java.util.Locale;

public enum EquipmentSlot {
    RIGHT_HAND("rightHand"),
    LEFT_HAND("leftHand"),
    BOTH_HANDS("bothHands"),
    BACK("back"),
    BELT("belt"),
    SHOULDER("shoulder"),
    WEAPON_PRIMARY("weaponPrimary"),
    WEAPON_SECONDARY("weaponSecondary");

    private final String id;

    EquipmentSlot(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static EquipmentSlot fromId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return RIGHT_HAND;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (EquipmentSlot slot : values()) {
            if (slot.id.toLowerCase(Locale.ROOT).equals(normalized) || slot.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return slot;
            }
        }
        return RIGHT_HAND;
    }
}
