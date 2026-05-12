package com.scenemaxeng.projector;

public class WeaponAttackResult {
    private final boolean success;
    private final String reason;
    private final EquippedWeaponRuntime weapon;

    private WeaponAttackResult(boolean success, String reason, EquippedWeaponRuntime weapon) {
        this.success = success;
        this.reason = reason;
        this.weapon = weapon;
    }

    public static WeaponAttackResult success(EquippedWeaponRuntime weapon) {
        return new WeaponAttackResult(true, "", weapon);
    }

    public static WeaponAttackResult failed(String reason, EquippedWeaponRuntime weapon) {
        return new WeaponAttackResult(false, reason, weapon);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public EquippedWeaponRuntime getWeapon() {
        return weapon;
    }
}
