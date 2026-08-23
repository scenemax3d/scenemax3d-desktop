package com.scenemax.designer.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnimationImportOptions {
    private String bevyRetargetProfile = "auto";
    private int bevySkipTopAnimatedTargets = 1;
    private final List<String> bevyExcludedBones = new ArrayList<>();
    private String bevyRootBone = "Root";
    private String bevyScaleBaseBone = "Hips";
    private boolean bevyRemoveUnimportantTranslationTracks = true;
    private boolean bevyRemoveMotionTranslationTracks = true;
    private boolean bevyRemoveMotionRotationTracks;
    private boolean bevyNormalizeMotionScale = true;
    private float bevyVisualTranslationX;
    private float bevyVisualTranslationY;
    private float bevyVisualTranslationZ;
    private float bevyVisualRotationXDegrees;
    private float bevyVisualRotationYDegrees;
    private float bevyVisualRotationZDegrees;
    private boolean bevyLockTranslationX;
    private boolean bevyLockTranslationY;
    private boolean bevyLockTranslationZ;

    public String getBevyRetargetProfile() {
        return bevyRetargetProfile;
    }

    public void setBevyRetargetProfile(String value) {
        if (value == null) {
            bevyRetargetProfile = "auto";
            return;
        }
        String normalized = value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        if ("humanoid".equals(normalized) || "human".equals(normalized)) {
            bevyRetargetProfile = "humanoid";
        } else if ("exact".equals(normalized) || "none".equals(normalized) || "off".equals(normalized)) {
            bevyRetargetProfile = "exact";
        } else {
            bevyRetargetProfile = "auto";
        }
    }

    public int getBevySkipTopAnimatedTargets() {
        return Math.max(0, bevySkipTopAnimatedTargets);
    }

    public void setBevySkipTopAnimatedTargets(int bevySkipTopAnimatedTargets) {
        this.bevySkipTopAnimatedTargets = Math.max(0, Math.min(16, bevySkipTopAnimatedTargets));
    }

    public List<String> getBevyExcludedBones() {
        return Collections.unmodifiableList(bevyExcludedBones);
    }

    public void setBevyExcludedBones(List<String> bones) {
        bevyExcludedBones.clear();
        if (bones == null) {
            return;
        }
        for (String bone : bones) {
            if (bone == null || bone.trim().isEmpty()) {
                continue;
            }
            bevyExcludedBones.add(bone.trim());
        }
    }

    public String getBevyExcludedBonesCsv() {
        return String.join(", ", bevyExcludedBones);
    }

    public void setBevyExcludedBonesCsv(String csv) {
        List<String> bones = new ArrayList<>();
        if (csv != null) {
            for (String bone : csv.split(",")) {
                bones.add(bone);
            }
        }
        setBevyExcludedBones(bones);
    }

    public String getBevyRootBone() {
        return bevyRootBone;
    }

    public void setBevyRootBone(String value) {
        bevyRootBone = sanitizeBoneName(value, "Root");
    }

    public String getBevyScaleBaseBone() {
        return bevyScaleBaseBone;
    }

    public void setBevyScaleBaseBone(String value) {
        bevyScaleBaseBone = sanitizeBoneName(value, "Hips");
    }

    public boolean isBevyRemoveUnimportantTranslationTracks() {
        return bevyRemoveUnimportantTranslationTracks;
    }

    public void setBevyRemoveUnimportantTranslationTracks(boolean value) {
        bevyRemoveUnimportantTranslationTracks = value;
    }

    public boolean isBevyRemoveMotionTranslationTracks() {
        return bevyRemoveMotionTranslationTracks;
    }

    public void setBevyRemoveMotionTranslationTracks(boolean value) {
        bevyRemoveMotionTranslationTracks = value;
    }

    public boolean isBevyRemoveMotionRotationTracks() {
        return bevyRemoveMotionRotationTracks;
    }

    public void setBevyRemoveMotionRotationTracks(boolean value) {
        bevyRemoveMotionRotationTracks = value;
    }

    public boolean isBevyNormalizeMotionScale() {
        return bevyNormalizeMotionScale;
    }

    public void setBevyNormalizeMotionScale(boolean value) {
        bevyNormalizeMotionScale = value;
    }

    public float getBevyVisualTranslationX() {
        return bevyVisualTranslationX;
    }

    public void setBevyVisualTranslationX(float value) {
        bevyVisualTranslationX = sanitizeFinite(value);
    }

    public float getBevyVisualTranslationY() {
        return bevyVisualTranslationY;
    }

    public void setBevyVisualTranslationY(float value) {
        bevyVisualTranslationY = sanitizeFinite(value);
    }

    public float getBevyVisualTranslationZ() {
        return bevyVisualTranslationZ;
    }

    public void setBevyVisualTranslationZ(float value) {
        bevyVisualTranslationZ = sanitizeFinite(value);
    }

    public float getBevyVisualRotationXDegrees() {
        return bevyVisualRotationXDegrees;
    }

    public void setBevyVisualRotationXDegrees(float value) {
        bevyVisualRotationXDegrees = sanitizeDegrees(value);
    }

    public float getBevyVisualRotationYDegrees() {
        return bevyVisualRotationYDegrees;
    }

    public void setBevyVisualRotationYDegrees(float value) {
        bevyVisualRotationYDegrees = sanitizeDegrees(value);
    }

    public float getBevyVisualRotationZDegrees() {
        return bevyVisualRotationZDegrees;
    }

    public void setBevyVisualRotationZDegrees(float value) {
        bevyVisualRotationZDegrees = sanitizeDegrees(value);
    }

    public boolean isBevyLockTranslationX() {
        return bevyLockTranslationX;
    }

    public void setBevyLockTranslationX(boolean value) {
        bevyLockTranslationX = value;
    }

    public boolean isBevyLockTranslationY() {
        return bevyLockTranslationY;
    }

    public void setBevyLockTranslationY(boolean value) {
        bevyLockTranslationY = value;
    }

    public boolean isBevyLockTranslationZ() {
        return bevyLockTranslationZ;
    }

    public void setBevyLockTranslationZ(boolean value) {
        bevyLockTranslationZ = value;
    }

    private float sanitizeDegrees(float value) {
        value = sanitizeFinite(value);
        float wrapped = value % 360f;
        if (wrapped > 180f) {
            wrapped -= 360f;
        } else if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }

    private float sanitizeFinite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }

    private String sanitizeBoneName(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
