package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class WeaponEffectSet {
    public String drawSound = "";
    public String attackSound = "";
    public String reloadSound = "";
    public String emptyAmmoSound = "";
    public String impactSound = "";
    public String muzzleFlashEffect = "";
    public String meleeTrailEffect = "";
    public String impactEffect = "";
    public String cameraShakeProfile = "";

    public JSONObject toJSON() {
        return new JSONObject()
                .put("drawSound", drawSound)
                .put("attackSound", attackSound)
                .put("reloadSound", reloadSound)
                .put("emptyAmmoSound", emptyAmmoSound)
                .put("impactSound", impactSound)
                .put("muzzleFlashEffect", muzzleFlashEffect)
                .put("meleeTrailEffect", meleeTrailEffect)
                .put("impactEffect", impactEffect)
                .put("cameraShakeProfile", cameraShakeProfile);
    }

    public static WeaponEffectSet fromJSON(JSONObject json) {
        WeaponEffectSet set = new WeaponEffectSet();
        if (json == null) {
            return set;
        }
        set.drawSound = json.optString("drawSound", set.drawSound);
        set.attackSound = json.optString("attackSound", set.attackSound);
        set.reloadSound = json.optString("reloadSound", set.reloadSound);
        set.emptyAmmoSound = json.optString("emptyAmmoSound", set.emptyAmmoSound);
        set.impactSound = json.optString("impactSound", set.impactSound);
        set.muzzleFlashEffect = json.optString("muzzleFlashEffect", set.muzzleFlashEffect);
        set.meleeTrailEffect = json.optString("meleeTrailEffect", set.meleeTrailEffect);
        set.impactEffect = json.optString("impactEffect", set.impactEffect);
        set.cameraShakeProfile = json.optString("cameraShakeProfile", set.cameraShakeProfile);
        return set;
    }
}
