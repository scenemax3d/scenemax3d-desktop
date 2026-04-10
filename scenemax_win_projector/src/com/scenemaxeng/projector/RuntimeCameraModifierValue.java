package com.scenemaxeng.projector;

class RuntimeCameraModifierValue {
    static final String TYPE_HIT = "hit_modifier";
    static final String TYPE_FALL = "fall_modifier";
    static final String TYPE_SHOOTING = "shooting_modifier";
    static final String TYPE_ACCELERATING = "accelerating_modifier";
    static final String TYPE_DECELERATING = "decelerating_modifier";
    static final String TYPE_BUMP = "bump_modifier";
    static final String TYPE_LANDING = "landing_modifier";
    static final String TYPE_EARTHQUAKE = "earthquake_modifier";
    static final String TYPE_EXPLOSION = "explosion_modifier";
    static final String TYPE_NEAR_MISS = "near_miss_modifier";

    String modifierType;
    float duration = 0.3f;
    float amplitude = 1f;
    float frequency = 12f;
    float x = 0.08f;
    float y = 0.08f;
    float z = 0.08f;
    float rx = 0.8f;
    float ry = 0.8f;
    float rz = 0.8f;
    float fov = 0.4f;

    RuntimeCameraModifierValue copy() {
        RuntimeCameraModifierValue copy = new RuntimeCameraModifierValue();
        copy.modifierType = modifierType;
        copy.duration = duration;
        copy.amplitude = amplitude;
        copy.frequency = frequency;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.rx = rx;
        copy.ry = ry;
        copy.rz = rz;
        copy.fov = fov;
        return copy;
    }
}
