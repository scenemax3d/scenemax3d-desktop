package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.scenemaxeng.compiler.MotionEaseType;

public final class MotionEase {

    private MotionEase() {
    }

    public static float delta(int easeType, float previousProgress, float currentProgress) {
        float from = apply(easeType, previousProgress);
        float to = apply(easeType, currentProgress);
        return to - from;
    }

    public static float apply(int easeType, float progress) {
        float p = FastMath.clamp(progress, 0f, 1f);
        switch (easeType) {
            case MotionEaseType.EASE_IN:
                return p * p;
            case MotionEaseType.EASE_OUT:
                return 1f - (1f - p) * (1f - p);
            case MotionEaseType.EASE_IN_OUT:
                if (p < 0.5f) {
                    return 2f * p * p;
                }
                float inv = 1f - p;
                return 1f - 2f * inv * inv;
            default:
                return p;
        }
    }
}
