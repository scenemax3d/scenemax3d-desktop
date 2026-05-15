package com.scenemaxeng.common.motion;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class ThrowMotionSampler {
    private static final float GRAVITY = 9.81f;

    private ThrowMotionSampler() {
    }

    public static List<ThrowMotionSample> sample(ThrowMotionDefinition definition, PreviewScenario scenario, float fixedDelta) {
        List<ThrowMotionSample> samples = new ArrayList<>();
        if (definition == null) {
            return samples;
        }
        PreviewScenario ctx = scenario == null ? new PreviewScenario() : scenario;
        float dt = Math.max(0.01f, fixedDelta);
        String type = ThrowMotionDefinition.normalizeMotionType(definition.getMotionType());
        if (ThrowMotionDefinition.TYPE_TARGET_ARC.equals(type)) {
            sampleTargetArc(definition.getParameters(), ctx, dt, samples);
        } else if (ThrowMotionDefinition.TYPE_STRAIGHT.equals(type)) {
            sampleStraight(definition.getParameters(), ctx, dt, samples);
        } else if (ThrowMotionDefinition.TYPE_HOMING.equals(type)) {
            sampleHoming(definition.getParameters(), ctx, dt, samples);
        } else if (ThrowMotionDefinition.TYPE_RETURNING.equals(type)) {
            sampleReturning(definition.getParameters(), ctx, dt, samples);
        } else {
            sampleBallistic(definition.getParameters(), ctx, dt, samples);
        }
        return samples;
    }

    private static void sampleBallistic(ThrowMotionDefinition.MotionParameters p, PreviewScenario ctx,
                                        float dt, List<ThrowMotionSample> out) {
        float speed = (float) Math.max(0.1, p.initialSpeed);
        float angle = (float) p.launchAngle * FastMath.DEG_TO_RAD;
        Vector3f pos = ctx.start.clone();
        Vector3f velocity = new Vector3f(0f, FastMath.sin(angle) * speed, FastMath.cos(angle) * speed);
        float gravity = GRAVITY * (float) p.gravityScale;
        float maxTime = (float) Math.max(0.2, p.maxLifetime);
        for (float t = 0f; t <= maxTime; t += dt) {
            out.add(new ThrowMotionSample(t, pos, velocity, spin(p, t)));
            velocity.y -= gravity * dt;
            pos = pos.add(velocity.mult(dt));
            if (ctx.stopAtGround && t > 0.1f && pos.y <= ctx.groundY) {
                pos.y = ctx.groundY;
                out.add(new ThrowMotionSample(t + dt, pos, velocity, spin(p, t + dt)));
                break;
            }
        }
    }

    private static void sampleTargetArc(ThrowMotionDefinition.MotionParameters p, PreviewScenario ctx,
                                        float dt, List<ThrowMotionSample> out) {
        float duration = (float) Math.max(0.1, p.duration);
        Vector3f previous = ctx.start.clone();
        for (float t = 0f; t <= duration + 0.0001f; t += dt) {
            float raw = FastMath.clamp(t / duration, 0f, 1f);
            float u = ease(raw, p.easingFunction);
            Vector3f pos = lerp(ctx.start, ctx.target, u);
            pos.y += (float) p.arcHeight * 4f * raw * (1f - raw);
            Vector3f velocity = t <= 0f ? ctx.target.subtract(ctx.start).normalizeLocal() : pos.subtract(previous).divideLocal(dt);
            out.add(new ThrowMotionSample(t, pos, velocity, spin(p, t)));
            previous = pos;
        }
    }

    private static void sampleStraight(ThrowMotionDefinition.MotionParameters p, PreviewScenario ctx,
                                       float dt, List<ThrowMotionSample> out) {
        float speed = (float) Math.max(0.1, p.speed);
        float acceleration = (float) p.acceleration;
        float maxDistance = (float) Math.max(0.1, p.maxDistance);
        float maxTime = (float) Math.max(0.2, p.maxLifetime);
        Vector3f dir = directionToTarget(ctx);
        Vector3f pos = ctx.start.clone();
        float distance = 0f;
        for (float t = 0f; t <= maxTime && distance <= maxDistance; t += dt) {
            Vector3f velocity = dir.mult(speed);
            out.add(new ThrowMotionSample(t, pos, velocity, spin(p, t)));
            float step = Math.max(0f, speed * dt);
            pos = pos.add(dir.mult(step));
            distance += step;
            speed += acceleration * dt;
        }
    }

    private static void sampleHoming(ThrowMotionDefinition.MotionParameters p, PreviewScenario ctx,
                                     float dt, List<ThrowMotionSample> out) {
        float speed = (float) Math.max(0.1, p.speed);
        float maxTime = (float) Math.max(0.2, p.maxLifetime);
        float turnRate = (float) Math.max(1.0, p.turnRate) * FastMath.DEG_TO_RAD;
        Vector3f pos = ctx.start.clone();
        Vector3f dir = new Vector3f(0f, 0.08f, 1f).normalizeLocal();
        for (float t = 0f; t <= maxTime; t += dt) {
            Vector3f desired = ctx.target.subtract(pos);
            if (desired.lengthSquared() > 0.0001f && t >= p.homingDelay) {
                desired.normalizeLocal();
                float blend = FastMath.clamp(turnRate * dt * (float) Math.max(0.0, p.homingStrength), 0f, 1f);
                dir = dir.interpolateLocal(desired, blend).normalizeLocal();
            }
            Vector3f velocity = dir.mult(speed);
            out.add(new ThrowMotionSample(t, pos, velocity, spin(p, t)));
            pos = pos.add(velocity.mult(dt));
            if (pos.distance(ctx.target) <= Math.max(0.2f, (float) p.collisionRadius)) {
                out.add(new ThrowMotionSample(t + dt, ctx.target, velocity, spin(p, t + dt)));
                break;
            }
        }
    }

    private static void sampleReturning(ThrowMotionDefinition.MotionParameters p, PreviewScenario ctx,
                                        float dt, List<ThrowMotionSample> out) {
        float outboundDuration = (float) Math.max(0.1, p.outboundDuration);
        Vector3f outboundTarget = ctx.start.add(0f, 0f, (float) p.outboundDistance);
        Vector3f previous = ctx.start.clone();
        for (float t = 0f; t <= outboundDuration + 0.0001f; t += dt) {
            float u = FastMath.clamp(t / outboundDuration, 0f, 1f);
            Vector3f pos = lerp(ctx.start, outboundTarget, ease(u, p.easingFunction));
            pos.y += (float) p.outboundArcHeight * 4f * u * (1f - u);
            Vector3f velocity = t <= 0f ? outboundTarget.subtract(ctx.start).normalizeLocal() : pos.subtract(previous).divideLocal(dt);
            out.add(new ThrowMotionSample(t, pos, velocity, spin(p, t)));
            previous = pos;
        }
        float delay = (float) Math.max(0.0, p.returnDelay);
        if (delay > 0f) {
            out.add(new ThrowMotionSample(outboundDuration + delay, previous, Vector3f.ZERO, spin(p, outboundDuration + delay)));
        }
        float returnSpeed = (float) Math.max(0.1, p.returnSpeed);
        float returnDistance = previous.distance(ctx.start);
        float returnDuration = Math.max(0.1f, returnDistance / returnSpeed);
        for (float t = dt; t <= returnDuration + 0.0001f; t += dt) {
            float u = FastMath.clamp(t / returnDuration, 0f, 1f);
            Vector3f pos = lerp(previous, ctx.start, ease(u, p.easingFunction));
            Vector3f velocity = ctx.start.subtract(previous).normalizeLocal().mult(returnSpeed);
            out.add(new ThrowMotionSample(outboundDuration + delay + t, pos, velocity, spin(p, outboundDuration + delay + t)));
        }
    }

    private static Vector3f directionToTarget(PreviewScenario ctx) {
        Vector3f dir = ctx.target.subtract(ctx.start);
        dir.y = 0f;
        if (dir.lengthSquared() < 0.0001f) {
            return Vector3f.UNIT_Z.clone();
        }
        return dir.normalizeLocal();
    }

    private static Vector3f lerp(Vector3f start, Vector3f end, float t) {
        return start.mult(1f - t).add(end.mult(t));
    }

    private static float ease(float t, String easing) {
        String value = easing == null ? "" : easing.trim().toLowerCase();
        if ("ease_in".equals(value)) {
            return t * t;
        }
        if ("ease_out".equals(value)) {
            return 1f - (1f - t) * (1f - t);
        }
        if ("ease_in_out".equals(value)) {
            return t < 0.5f ? 2f * t * t : 1f - FastMath.pow(-2f * t + 2f, 2f) / 2f;
        }
        return t;
    }

    private static float spin(ThrowMotionDefinition.MotionParameters p, float time) {
        return (float) p.spinSpeed * time;
    }

    public static class PreviewScenario {
        public Vector3f start = new Vector3f(0f, 1.2f, 0f);
        public Vector3f target = new Vector3f(0f, 1.2f, 12f);
        public float groundY = 0f;
        public boolean stopAtGround = true;
    }
}
