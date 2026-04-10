package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class CameraModifierManager {

    static class CameraModifierFrame {
        final Vector3f positionOffset = new Vector3f();
        final Vector3f lookAtOffset = new Vector3f();
        float rxDegrees;
        float ryDegrees;
        float rzDegrees;
        float fovOffset;
    }

    private static class ActiveCameraModifier {
        final RuntimeCameraModifierValue value;
        final float seed;
        float elapsed;

        ActiveCameraModifier(RuntimeCameraModifierValue value, float seed) {
            this.value = value;
            this.seed = seed;
        }
    }

    private final List<ActiveCameraModifier> active = new ArrayList<>();
    private int seedCounter;

    void clear() {
        active.clear();
    }

    void add(RuntimeCameraModifierValue value) {
        active.add(new ActiveCameraModifier(value.copy(), ++seedCounter * 0.731f));
    }

    boolean hasActive() {
        return !active.isEmpty();
    }

    CameraModifierFrame update(float tpf, Vector3f basePos, Vector3f baseLookAt) {
        CameraModifierFrame frame = new CameraModifierFrame();
        if (active.isEmpty()) {
            return frame;
        }

        Vector3f forward = baseLookAt.subtract(basePos).normalize();
        if (forward.lengthSquared() < FastMath.ZERO_TOLERANCE) {
            forward.set(Vector3f.UNIT_Z);
        }
        Vector3f right = forward.cross(Vector3f.UNIT_Y).normalize();
        if (right.lengthSquared() < FastMath.ZERO_TOLERANCE) {
            right.set(Vector3f.UNIT_X);
        }
        Vector3f up = Vector3f.UNIT_Y;

        Iterator<ActiveCameraModifier> iterator = active.iterator();
        while (iterator.hasNext()) {
            ActiveCameraModifier modifier = iterator.next();
            modifier.elapsed += tpf;
            float duration = Math.max(0.01f, modifier.value.duration);
            float p = FastMath.clamp(modifier.elapsed / duration, 0f, 1f);
            float envelope = envelopeFor(modifier.value.modifierType, p);
            if (p >= 1f && envelope <= 1e-4f) {
                iterator.remove();
                continue;
            }

            float amp = modifier.value.amplitude * envelope;
            float time = modifier.elapsed;
            float freq = Math.max(0.1f, modifier.value.frequency);

            float nx = signal(time, freq, modifier.seed + 0.11f);
            float ny = signal(time, freq * 1.09f, modifier.seed + 1.33f);
            float nz = signal(time, freq * 0.93f, modifier.seed + 2.57f);

            applyDirectionalBias(modifier.value.modifierType, p, amp, frame, forward, right, up);

            frame.positionOffset.addLocal(right.mult(nx * modifier.value.x * amp));
            frame.positionOffset.addLocal(up.mult(ny * modifier.value.y * amp));
            frame.positionOffset.addLocal(forward.mult(nz * modifier.value.z * amp));

            frame.lookAtOffset.addLocal(right.mult(nx * modifier.value.x * amp * 0.35f));
            frame.lookAtOffset.addLocal(up.mult(ny * modifier.value.y * amp * 0.35f));

            frame.rxDegrees += ny * modifier.value.rx * amp;
            frame.ryDegrees += nx * modifier.value.ry * amp;
            frame.rzDegrees += nz * modifier.value.rz * amp;
            frame.fovOffset += envelope * modifier.value.fov * amp;
        }

        return frame;
    }

    private float signal(float time, float frequency, float seed) {
        return (float) (
                Math.sin((time + seed) * frequency * FastMath.TWO_PI)
                        + 0.5 * Math.sin((time * 1.73f + seed * 1.91f) * frequency * FastMath.TWO_PI)
                        + 0.25 * Math.cos((time * 0.63f + seed * 0.47f) * frequency * FastMath.TWO_PI)
        ) / 1.75f;
    }

    private float envelopeFor(String type, float progress) {
        float p = FastMath.clamp(progress, 0f, 1f);
        if (RuntimeCameraModifierValue.TYPE_EARTHQUAKE.equalsIgnoreCase(type)) {
            return (0.7f + 0.3f * FastMath.sin((1f - p) * FastMath.PI)) * FastMath.pow(1f - p, 0.6f);
        }
        if (RuntimeCameraModifierValue.TYPE_FALL.equalsIgnoreCase(type)
                || RuntimeCameraModifierValue.TYPE_ACCELERATING.equalsIgnoreCase(type)
                || RuntimeCameraModifierValue.TYPE_DECELERATING.equalsIgnoreCase(type)) {
            return FastMath.sin(p * FastMath.PI);
        }
        if (RuntimeCameraModifierValue.TYPE_SHOOTING.equalsIgnoreCase(type)) {
            return FastMath.pow(1f - p, 1.8f);
        }
        return FastMath.pow(1f - p, 1.25f) * (0.55f + 0.45f * FastMath.sin(p * FastMath.PI));
    }

    private void applyDirectionalBias(String type,
                                      float progress,
                                      float amplitude,
                                      CameraModifierFrame frame,
                                      Vector3f forward,
                                      Vector3f right,
                                      Vector3f up) {
        float impulse = amplitude * (1f - progress);
        if (RuntimeCameraModifierValue.TYPE_HIT.equalsIgnoreCase(type)
                || RuntimeCameraModifierValue.TYPE_NEAR_MISS.equalsIgnoreCase(type)) {
            frame.positionOffset.addLocal(right.mult(0.12f * impulse));
            frame.lookAtOffset.addLocal(right.mult(0.04f * impulse));
        } else if (RuntimeCameraModifierValue.TYPE_FALL.equalsIgnoreCase(type)) {
            frame.positionOffset.addLocal(up.mult(-0.08f * amplitude));
        } else if (RuntimeCameraModifierValue.TYPE_SHOOTING.equalsIgnoreCase(type)) {
            frame.positionOffset.addLocal(forward.mult(-0.05f * impulse));
        } else if (RuntimeCameraModifierValue.TYPE_ACCELERATING.equalsIgnoreCase(type)) {
            frame.positionOffset.addLocal(forward.mult(-0.08f * amplitude));
        } else if (RuntimeCameraModifierValue.TYPE_DECELERATING.equalsIgnoreCase(type)) {
            frame.positionOffset.addLocal(forward.mult(0.06f * amplitude));
        } else if (RuntimeCameraModifierValue.TYPE_BUMP.equalsIgnoreCase(type)
                || RuntimeCameraModifierValue.TYPE_LANDING.equalsIgnoreCase(type)) {
            frame.positionOffset.addLocal(up.mult(-0.1f * impulse));
        } else if (RuntimeCameraModifierValue.TYPE_EXPLOSION.equalsIgnoreCase(type)) {
            frame.positionOffset.addLocal(forward.mult(-0.12f * impulse));
        }
    }
}
