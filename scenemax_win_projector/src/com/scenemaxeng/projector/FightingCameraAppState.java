package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

class FightingCameraAppState {

    private final SceneMaxApp app;
    private final SceneMaxScope scope;
    private final RuntimeCameraSystemValue settings;
    private final Camera cam;

    private final Vector3f smoothedLookAt = new Vector3f();
    private final Vector3f lastSideDir = new Vector3f(0f, 0f, 1f);
    private boolean initialized;

    private float initialFrustumLeft;
    private float initialFrustumRight;
    private float initialFrustumTop;
    private float initialFrustumBottom;
    private float initialFrustumNear;
    private float initialFrustumFar;

    FightingCameraAppState(SceneMaxApp app, SceneMaxScope scope, RuntimeCameraSystemValue settings) {
        this.app = app;
        this.scope = scope;
        this.settings = settings;
        this.cam = app.getCamera();
    }

    void update(float tpf) {
        Spatial player = resolveSpatial(settings.playerVar);
        Spatial opponent = resolveSpatial(settings.opponentVar);
        if (player == null || opponent == null) {
            return;
        }

        if (!initialized) {
            initialized = true;
            storeFrustum();
            smoothedLookAt.set(player.getWorldTranslation()).interpolateLocal(opponent.getWorldTranslation(), 0.5f);
        }

        Vector3f p1 = player.getWorldTranslation().clone();
        Vector3f p2 = opponent.getWorldTranslation().clone();
        Vector3f separation = p2.subtract(p1);
        Vector3f planarSeparation = new Vector3f(separation.x, 0f, separation.z);

        if (planarSeparation.lengthSquared() < FastMath.ZERO_TOLERANCE) {
            planarSeparation.set(lastSideDir).crossLocal(Vector3f.UNIT_Y);
        }

        Vector3f fighterAxis = planarSeparation.normalize();
        Vector3f sideDir = fighterAxis.cross(Vector3f.UNIT_Y).normalizeLocal();
        if (sideDir.dot(lastSideDir) < 0f) {
            sideDir.negateLocal();
        }
        lastSideDir.set(sideDir);

        float fighterDistance = Math.max(1f, planarSeparation.length());
        float desiredDepth = clamp(fighterDistance * settings.zoomFactor, settings.minDistance, settings.maxDistance);

        Vector3f midpoint = p1.interpolateLocal(p2, 0.5f);
        Vector3f lookAt = midpoint.add(Vector3f.UNIT_Y.mult(settings.height * 0.35f));
        if (settings.lookAhead != 0f) {
            lookAt.addLocal(fighterAxis.mult(settings.lookAhead));
        }

        float alpha = smoothingAlpha(settings.damping, tpf);
        smoothedLookAt.interpolateLocal(lookAt, alpha);

        Vector3f desiredPos = smoothedLookAt.clone()
                .addLocal(Vector3f.UNIT_Y.mult(settings.height))
                .addLocal(sideDir.mult(desiredDepth))
                .addLocal(fighterAxis.mult(settings.side));

        if (settings.arenaMinX != null) {
            desiredPos.x = Math.max(settings.arenaMinX, desiredPos.x);
        }
        if (settings.arenaMaxX != null) {
            desiredPos.x = Math.min(settings.arenaMaxX, desiredPos.x);
        }
        if (settings.arenaMinZ != null) {
            desiredPos.z = Math.max(settings.arenaMinZ, desiredPos.z);
        }
        if (settings.arenaMaxZ != null) {
            desiredPos.z = Math.min(settings.arenaMaxZ, desiredPos.z);
        }

        Vector3f smoothedPos = cam.getLocation().clone().interpolateLocal(desiredPos, alpha);
        cam.setLocation(smoothedPos);
        cam.lookAt(smoothedLookAt, Vector3f.UNIT_Y);

        float distanceFactor = clamp01((fighterDistance - settings.minDistance) / Math.max(0.001f, settings.maxDistance - settings.minDistance));
        float targetFov = FastMath.interpolateLinear(distanceFactor, settings.fov, settings.maxFov);
        applyPerspectiveFov(FastMath.interpolateLinear(alpha, currentFovDegrees(), targetFov));
    }

    void cleanup() {
        if (!initialized) {
            return;
        }
        cam.setFrustum(initialFrustumNear, initialFrustumFar, initialFrustumLeft, initialFrustumRight, initialFrustumTop, initialFrustumBottom);
    }

    private Spatial resolveSpatial(String varName) {
        RunTimeVarDef rt = app.findVarRuntime(null, scope, varName);
        if (rt == null) {
            return null;
        }
        return app.getEntitySpatial(rt.varName, rt.varDef.varType);
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

    private float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }
}
