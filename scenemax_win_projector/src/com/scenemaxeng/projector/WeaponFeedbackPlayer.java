package com.scenemaxeng.projector;

import com.jme3.math.Vector3f;
import com.scenemaxeng.common.weapons.AttackProfile;
import com.scenemaxeng.common.weapons.WeaponAnimationSet;
import com.scenemaxeng.common.weapons.WeaponEffectSet;

public class WeaponFeedbackPlayer {
    private final SceneMaxApp app;

    public WeaponFeedbackPlayer(SceneMaxApp app) {
        this.app = app;
    }

    public void playEquipFeedback(EquippedWeaponRuntime runtime) {
        if (runtime == null || runtime.getWeaponDefinition() == null) {
            return;
        }
        WeaponAnimationSet animations = runtime.getWeaponDefinition().getAnimationSet();
        WeaponEffectSet effects = runtime.getWeaponDefinition().getEffectSet();
        playOwnerAnimation(runtime, animations != null ? animations.drawAnimation : "", animations);
        playSound(effects != null ? effects.drawSound : "");
    }

    public void playUnequipFeedback(EquippedWeaponRuntime runtime) {
        if (runtime == null || runtime.getWeaponDefinition() == null) {
            return;
        }
        WeaponAnimationSet animations = runtime.getWeaponDefinition().getAnimationSet();
        playOwnerAnimation(runtime, animations != null ? animations.sheatheAnimation : "", animations);
    }

    public void playAttackFeedback(EquippedWeaponRuntime runtime) {
        if (runtime == null || runtime.getWeaponDefinition() == null) {
            return;
        }
        WeaponAnimationSet animations = runtime.getWeaponDefinition().getAnimationSet();
        WeaponEffectSet effects = runtime.getWeaponDefinition().getEffectSet();
        AttackProfile attack = runtime.getActiveAttackProfile();
        String animation = resolveAttackAnimation(attack, animations);
        playOwnerAnimation(runtime, animation, animations);
        playSound(resolveAttackSound(attack, effects));

        Vector3f origin = runtime.getSpawnedModel() != null
                ? runtime.getSpawnedModel().getWorldTranslation()
                : worldPosition(runtime.getOwnerCharacterId());
        if (attack != null && "meleeHitbox".equalsIgnoreCase(attack.getAttackType())) {
            playEffect(resolveMeleeTrailEffect(attack, effects), origin);
        } else {
            playEffect(resolveMuzzleFlashEffect(attack, effects), origin);
        }
    }

    public void playAttackRejectedFeedback(EquippedWeaponRuntime runtime, String reason) {
        if (runtime == null || runtime.getWeaponDefinition() == null) {
            return;
        }
        WeaponEffectSet effects = runtime.getWeaponDefinition().getEffectSet();
        if ("weapon_empty".equals(reason) || "weapon_auto_reloading".equals(reason)) {
            playSound(effects != null ? effects.emptyAmmoSound : "");
        }
    }

    public void playReloadFeedback(EquippedWeaponRuntime runtime) {
        if (runtime == null || runtime.getWeaponDefinition() == null) {
            return;
        }
        WeaponAnimationSet animations = runtime.getWeaponDefinition().getAnimationSet();
        WeaponEffectSet effects = runtime.getWeaponDefinition().getEffectSet();
        playOwnerAnimation(runtime, animations != null ? animations.reloadAnimation : "", animations);
        playSound(effects != null ? effects.reloadSound : "");
    }

    public void playHitFeedback(EquippedWeaponRuntime runtime, WeaponDamageEvent damageEvent) {
        if (runtime == null || runtime.getWeaponDefinition() == null || damageEvent == null) {
            return;
        }
        WeaponEffectSet effects = runtime.getWeaponDefinition().getEffectSet();
        AttackProfile attack = damageEvent.getAttackProfile();
        playSound(resolveImpactSound(attack, effects));
        playEffect(resolveImpactEffect(attack, effects), worldPosition(damageEvent.getTargetVarName()));
    }

    private String resolveAttackAnimation(AttackProfile attack, WeaponAnimationSet animations) {
        return firstNonEmpty(
                attack != null ? attack.getAttackAnimation() : "",
                attack != null ? attack.getAnimationEventBinding() : "");
    }

    private String resolveAttackSound(AttackProfile attack, WeaponEffectSet effects) {
        return firstNonEmpty(
                attack != null ? attack.getAttackSound() : "",
                attack != null ? attack.getSoundEventBinding() : "");
    }

    private String resolveImpactSound(AttackProfile attack, WeaponEffectSet effects) {
        return firstNonEmpty(attack != null ? attack.getImpactSound() : "");
    }

    private String resolveMuzzleFlashEffect(AttackProfile attack, WeaponEffectSet effects) {
        return firstNonEmpty(attack != null ? attack.getMuzzleFlashEffect() : "");
    }

    private String resolveMeleeTrailEffect(AttackProfile attack, WeaponEffectSet effects) {
        return firstNonEmpty(attack != null ? attack.getMeleeTrailEffect() : "");
    }

    private String resolveImpactEffect(AttackProfile attack, WeaponEffectSet effects) {
        return firstNonEmpty(
                attack != null ? attack.getImpactEffect() : "",
                attack != null ? attack.getEffectEventBinding() : "");
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private void playOwnerAnimation(EquippedWeaponRuntime runtime, String animationName, WeaponAnimationSet animations) {
        if (animationName == null || animationName.trim().isEmpty()) {
            return;
        }
        double speed = animations != null ? animations.animationSpeedMultiplier : 1.0;
        app.playRuntimeAnimation(runtime.getOwnerCharacterId(), animationName, speed);
    }

    private void playSound(String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) {
            return;
        }
        app.playRuntimeSound(soundName);
    }

    private void playEffect(String effectName, Vector3f position) {
        if (effectName == null || effectName.trim().isEmpty()) {
            return;
        }
        app.playRuntimeEffect(effectName, position);
    }

    private Vector3f worldPosition(String varName) {
        if (varName == null || varName.trim().isEmpty()) {
            return null;
        }
        return app.getEntityWorldPosition(varName);
    }
}
