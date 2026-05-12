package com.scenemaxeng.projector;

import org.json.JSONObject;

public class WeaponRuntimeEvent {
    public static final String EQUIPPED = "weapon_equipped";
    public static final String UNEQUIPPED = "weapon_unequipped";
    public static final String ATTACK_STARTED = "weapon_attack_started";
    public static final String ATTACK_REJECTED = "weapon_attack_rejected";
    public static final String RELOAD_STARTED = "weapon_reload_started";
    public static final String RELOAD_COMPLETED = "weapon_reload_completed";
    public static final String HIT_RESOLVED = "weapon_hit_resolved";
    public static final String PROJECTILE_SPAWNED = "weapon_projectile_spawned";
    public static final String PROJECTILE_EXPIRED = "weapon_projectile_expired";

    private final long sequence;
    private final String type;
    private final String ownerVarName;
    private final String targetVarName;
    private final String slotId;
    private final String weaponId;
    private final String weaponName;
    private final String weaponInstanceId;
    private final String attackId;
    private final String attackName;
    private final WeaponDamageEvent damageEvent;
    private final JSONObject data;

    public WeaponRuntimeEvent(long sequence, String type, EquippedWeaponRuntime weapon, String targetVarName,
                              WeaponDamageEvent damageEvent, JSONObject data) {
        this.sequence = sequence;
        this.type = type;
        this.ownerVarName = weapon != null ? weapon.getOwnerCharacterId() : "";
        this.targetVarName = targetVarName != null ? targetVarName : "";
        this.slotId = weapon != null && weapon.getEquipmentSlot() != null ? weapon.getEquipmentSlot().getId() : "";
        this.weaponId = weapon != null && weapon.getWeaponDefinition() != null ? weapon.getWeaponDefinition().getId() : "";
        this.weaponName = weapon != null && weapon.getWeaponDefinition() != null ? weapon.getWeaponDefinition().getName() : "";
        this.weaponInstanceId = weapon != null ? weapon.getWeaponInstanceId() : "";
        this.attackId = weapon != null && weapon.getActiveAttackProfile() != null ? weapon.getActiveAttackProfile().getId() : "";
        this.attackName = weapon != null && weapon.getActiveAttackProfile() != null ? weapon.getActiveAttackProfile().getName() : "";
        this.damageEvent = damageEvent;
        this.data = data != null ? data : new JSONObject();
    }

    public long getSequence() {
        return sequence;
    }

    public String getType() {
        return type;
    }

    public String getOwnerVarName() {
        return ownerVarName;
    }

    public String getTargetVarName() {
        return targetVarName;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getWeaponId() {
        return weaponId;
    }

    public String getWeaponName() {
        return weaponName;
    }

    public String getWeaponInstanceId() {
        return weaponInstanceId;
    }

    public String getAttackId() {
        return attackId;
    }

    public String getAttackName() {
        return attackName;
    }

    public WeaponDamageEvent getDamageEvent() {
        return damageEvent;
    }

    public JSONObject getData() {
        return data;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject()
                .put("sequence", sequence)
                .put("type", type)
                .put("owner", ownerVarName)
                .put("target", targetVarName)
                .put("slot", slotId)
                .put("weaponId", weaponId)
                .put("weaponName", weaponName)
                .put("weaponInstanceId", weaponInstanceId)
                .put("attackId", attackId)
                .put("attackName", attackName)
                .put("data", data);
        if (damageEvent != null) {
            json.put("damage", new JSONObject()
                    .put("amount", damageEvent.getDamageAmount())
                    .put("type", damageEvent.getDamageType())
                    .put("critical", damageEvent.isCriticalHit())
                    .put("knockback", damageEvent.getKnockback())
                    .put("stunDuration", damageEvent.getStunDuration())
                    .put("armorPenetration", damageEvent.getArmorPenetration()));
        }
        return json;
    }
}
