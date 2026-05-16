package com.scenemaxeng.common.motion;

import com.jme3.math.Vector3f;

public class ThrowMotionSample {
    private final float time;
    private final Vector3f position;
    private final Vector3f velocity;
    private final float spinDegrees;

    public ThrowMotionSample(float time, Vector3f position, Vector3f velocity, float spinDegrees) {
        this.time = time;
        this.position = position == null ? Vector3f.ZERO.clone() : position.clone();
        this.velocity = velocity == null ? Vector3f.ZERO.clone() : velocity.clone();
        this.spinDegrees = spinDegrees;
    }

    public float getTime() {
        return time;
    }

    public Vector3f getPosition() {
        return position.clone();
    }

    public Vector3f getVelocity() {
        return velocity.clone();
    }

    public float getSpinDegrees() {
        return spinDegrees;
    }
}
