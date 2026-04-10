package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

class GameplayCameraAppState {

    private final SceneMaxApp app;
    private final SceneMaxScope scope;
    private final RuntimeCameraSystemValue settings;
    private final Camera cam;

    private final Vector3f smoothedLookAt = new Vector3f();
    private final Vector3f lastTargetPos = new Vector3f();
    private final Vector3f focusPoint = new Vector3f();
    private boolean initialized;

    private float initialFrustumLeft;
    private float initialFrustumRight;
    private float initialFrustumTop;
    private float initialFrustumBottom;
    private float initialFrustumNear;
    private float initialFrustumFar;

    GameplayCameraAppState(SceneMaxApp app, SceneMaxScope scope, RuntimeCameraSystemValue settings) {
        this.app = app;
        this.scope = scope;
        this.settings = settings;
        this.cam = app.getCamera();
    }

    void update(float tpf) {
        if (RuntimeCameraSystemValue.TYPE_RTS.equalsIgnoreCase(settings.systemType)) {
            updateRts(tpf);
            return;
        }

        Spatial target = resolveSpatial(settings.primaryTargetVar);
        if (target == null) {
            return;
        }

        Vector3f targetPos = target.getWorldTranslation().clone();
        Vector3f targetForward = target.getWorldRotation().mult(Vector3f.UNIT_Z).normalizeLocal();
        Vector3f targetRight = target.getWorldRotation().mult(Vector3f.UNIT_X).normalizeLocal();

        if (!initialized) {
            initialized = true;
            storeFrustum();
            smoothedLookAt.set(targetPos);
            lastTargetPos.set(targetPos);
            focusPoint.set(targetPos);
        }

        Vector3f velocity = targetPos.subtract(lastTargetPos);
        lastTargetPos.set(targetPos);
        float motion = velocity.length();

        if (RuntimeCameraSystemValue.TYPE_FIRST_PERSON.equalsIgnoreCase(settings.systemType)) {
            updateFirstPerson(tpf, targetPos, targetForward, targetRight);
            return;
        }

        float alpha = smoothingAlpha(settings.damping, tpf);
        Vector3f desiredLookAt = targetPos.clone()
                .addLocal(Vector3f.UNIT_Y.mult(settings.verticalBias));

        if (settings.lookAhead != 0f) {
            Vector3f leadDir = motion > FastMath.ZERO_TOLERANCE ? velocity.normalize() : targetForward;
            desiredLookAt.addLocal(leadDir.mult(settings.lookAhead));
        }

        if (RuntimeCameraSystemValue.TYPE_PLATFORMER.equalsIgnoreCase(settings.systemType)) {
            Vector3f delta = desiredLookAt.subtract(focusPoint);
            if (Math.abs(delta.x) > settings.deadZone) {
                focusPoint.x += delta.x - FastMath.sign(delta.x) * settings.deadZone;
            }
            if (Math.abs(delta.y) > settings.deadZone) {
                focusPoint.y += delta.y - FastMath.sign(delta.y) * settings.deadZone;
            }
            if (Math.abs(delta.z) > settings.deadZone) {
                focusPoint.z += delta.z - FastMath.sign(delta.z) * settings.deadZone;
            }
            smoothedLookAt.interpolateLocal(focusPoint, alpha);
        } else {
            smoothedLookAt.interpolateLocal(desiredLookAt, alpha);
        }

        Vector3f desiredPos;
        float targetFov = settings.fov;
        if (RuntimeCameraSystemValue.TYPE_RACING.equalsIgnoreCase(settings.systemType)) {
            float chaseDistance = clamp(settings.distance + motion * settings.zoomFactor * 0.5f,
                    settings.minDistance, settings.maxDistance > 0 ? settings.maxDistance : settings.distance + 20f);
            desiredPos = targetPos.clone()
                    .addLocal(targetForward.negate().mult(chaseDistance))
                    .addLocal(Vector3f.UNIT_Y.mult(settings.height))
                    .addLocal(targetRight.mult(settings.side));
            smoothedLookAt.interpolateLocal(targetPos.clone().addLocal(targetForward.mult(settings.lookAhead + motion * 0.75f)), alpha);
            targetFov = clamp(settings.fov + motion * settings.zoomFactor * 4f, settings.fov, settings.maxFov);
        } else {
            Quaternion yawRotation = new Quaternion().fromAngleAxis(camYaw(targetForward), Vector3f.UNIT_Y);
            Vector3f offset = yawRotation.mult(new Vector3f(settings.side, settings.height, -Math.max(0.1f, settings.distance)));
            desiredPos = targetPos.clone().addLocal(offset);
            if (RuntimeCameraSystemValue.TYPE_THIRD_PERSON.equalsIgnoreCase(settings.systemType)) {
                targetFov = clamp(settings.fov + motion * settings.zoomFactor * 2f, settings.fov, settings.maxFov);
            } else if (RuntimeCameraSystemValue.TYPE_PLATFORMER.equalsIgnoreCase(settings.systemType)) {
                targetFov = clamp(settings.fov + motion * settings.zoomFactor, settings.fov, settings.maxFov);
            }
        }

        applyBounds(desiredPos);
        Vector3f camPos = cam.getLocation().clone().interpolateLocal(desiredPos, alpha);
        app.applySystemCameraFrame(camPos, smoothedLookAt, FastMath.interpolateLinear(alpha, currentFovDegrees(), targetFov), tpf);
    }

    void cleanup() {
        if (!initialized) {
            return;
        }
        cam.setFrustum(initialFrustumNear, initialFrustumFar, initialFrustumLeft, initialFrustumRight, initialFrustumTop, initialFrustumBottom);
    }

    private void updateFirstPerson(float tpf, Vector3f targetPos, Vector3f targetForward, Vector3f targetRight) {
        if (!initialized) {
            initialized = true;
            storeFrustum();
        }
        Vector3f desiredPos = targetPos.clone()
                .addLocal(Vector3f.UNIT_Y.mult(settings.height))
                .addLocal(targetRight.mult(settings.side))
                .addLocal(targetForward.mult(settings.depth));
        applyBounds(desiredPos);
        app.applySystemCameraFrame(desiredPos,
                desiredPos.clone().addLocal(targetForward.mult(Math.max(1f, settings.lookAhead + 3f))),
                settings.fov,
                tpf);
    }

    private void updateRts(float tpf) {
        if (!initialized) {
            initialized = true;
            storeFrustum();
        }

        float alpha = smoothingAlpha(settings.damping, tpf);
        Vector3f desiredLookAt;
        if (settings.primaryTargetVar != null) {
            Spatial target = resolveSpatial(settings.primaryTargetVar);
            desiredLookAt = target != null ? target.getWorldTranslation().clone() : cam.getLocation().clone();
        } else {
            desiredLookAt = smoothedLookAt.lengthSquared() == 0f ? cam.getLocation().clone() : smoothedLookAt.clone();
        }
        desiredLookAt.addLocal(Vector3f.UNIT_Y.mult(settings.verticalBias));
        smoothedLookAt.interpolateLocal(desiredLookAt, alpha);

        float angleRad = settings.angle * FastMath.DEG_TO_RAD;
        float horizontal = Math.max(1f, settings.distance);
        Vector3f desiredPos = smoothedLookAt.clone()
                .addLocal(new Vector3f(0f,
                        FastMath.sin(angleRad) * horizontal + settings.height,
                        FastMath.cos(angleRad) * horizontal + settings.depth));
        applyBounds(desiredPos);
        app.applySystemCameraFrame(cam.getLocation().clone().interpolateLocal(desiredPos, alpha), smoothedLookAt, settings.fov, tpf);
    }

    private Spatial resolveSpatial(String varName) {
        if (varName == null || varName.isBlank()) {
            return null;
        }
        RunTimeVarDef rt = app.findVarRuntime(null, scope, varName);
        if (rt == null) {
            return null;
        }
        return app.getEntitySpatial(rt.varName, rt.varDef.varType);
    }

    private void applyBounds(Vector3f pos) {
        if (settings.minX != null) pos.x = Math.max(settings.minX, pos.x);
        if (settings.maxX != null) pos.x = Math.min(settings.maxX, pos.x);
        if (settings.minY != null) pos.y = Math.max(settings.minY, pos.y);
        if (settings.maxY != null) pos.y = Math.min(settings.maxY, pos.y);
        if (settings.minZ != null) pos.z = Math.max(settings.minZ, pos.z);
        if (settings.maxZ != null) pos.z = Math.min(settings.maxZ, pos.z);
    }

    private float camYaw(Vector3f forward) {
        return FastMath.atan2(forward.x, forward.z);
    }

    private void storeFrustum() {
        initialFrustumLeft = cam.getFrustumLeft();
        initialFrustumRight = cam.getFrustumRight();
        initialFrustumTop = cam.getFrustumTop();
        initialFrustumBottom = cam.getFrustumBottom();
        initialFrustumNear = cam.getFrustumNear();
        initialFrustumFar = cam.getFrustumFar();
    }

    private void applyPerspectiveFov(float fovDegrees) {
        float aspect = cam.getHeight() == 0 ? 1f : (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(fovDegrees, aspect, initialFrustumNear, initialFrustumFar);
    }

    private float currentFovDegrees() {
        float near = cam.getFrustumNear();
        if (near == 0f) {
            return settings.fov;
        }
        return 2f * FastMath.atan(cam.getFrustumTop() / near) * FastMath.RAD_TO_DEG;
    }

    private float smoothingAlpha(float damping, float tpf) {
        return 1f - FastMath.exp(-Math.max(0.01f, damping) * Math.max(0f, tpf));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
