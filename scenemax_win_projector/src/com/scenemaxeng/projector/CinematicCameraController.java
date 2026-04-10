package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.bounding.BoundingVolume;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.CinematicCameraPlayCommand;
import com.scenemaxeng.compiler.CinematicCameraVariableDef;
import com.scenemaxeng.compiler.PositionStatement;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class CinematicCameraController extends SceneMaxBaseController {

    private static final float DEFAULT_DURATION_SECONDS = 10f;
    private static final Logger LOGGER = Logger.getLogger(CinematicCameraController.class.getName());

    private final CinematicCameraPlayCommand cmd;
    private final List<RuntimePlaybackSegment> playbackSegments = new ArrayList<>();
    private float totalDuration = DEFAULT_DURATION_SECONDS;
    private float elapsed = 0f;
    private Vector3f runtimeRigPosition;
    private Quaternion runtimeRigRotation;
    private Vector3f runtimeRigScale;
    private RunTimeVarDef explicitTarget;
    private RunTimeVarDef rigTarget;
    private boolean playbackRegistered;

    private static class RuntimeTargetReference {
        Vector3f point;
        Quaternion rotation;
    }

    CinematicCameraController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, CinematicCameraPlayCommand cmd) {
        super(app, prg, scope, cmd);
        this.cmd = cmd;
    }

    @Override
    public void init() {
        findTargetVar();
        enableEntity(targetVar);

        EntityInstBase instBase = scope.getEntityInst(cmd.targetVar);
        if (!(instBase instanceof CinematicCameraInst) && cmd.varDef != null) {
            app.instantiateVariable(prg, cmd.varDef, scope);
            instBase = scope.getEntityInst(cmd.targetVar);
        }
        if (!(instBase instanceof CinematicCameraInst)) {
            app.handleRuntimeError(app.formatRuntimeLocation(cmd.varLineNum)
                    + "Cinematic camera instance '" + cmd.targetVar + "' is not available. "
                    + "Its declaration may have failed earlier, for example if the cinematic rig runtime ID could not be resolved.");
            forceStop = true;
            return;
        }

        CinematicCameraInst inst = (CinematicCameraInst) instBase;
        RuntimeCinematicRig rig = inst.rig;
        if (rig == null) {
            app.handleRuntimeError(app.formatRuntimeLocation(cmd.varLineNum)
                    + "Cinematic rig runtime ID '" + ((CinematicCameraVariableDef) inst.varDef).cinematicCameraId
                    + "' was not found for cinematic camera '" + cmd.targetVar + "'.");
            forceStop = true;
            return;
        }

        updateRuntimeRigTransform(rig);
        runtimeRigScale = rig.scale != null ? rig.scale.clone() : new Vector3f(1f, 1f, 1f);

        if (cmd.lookAtTargetVar != null && !cmd.lookAtTargetVar.isBlank()) {
            explicitTarget = app.findVarRuntime(prg, scope, cmd.lookAtTargetVar);
        }
        if (rig.targetEntityName != null && !rig.targetEntityName.isBlank()) {
            rigTarget = app.findVarRuntime(prg, scope, rig.targetEntityName);
        }

        buildPlaybackSegments(rig);
        if (playbackSegments.isEmpty()) {
            app.handleRuntimeError(app.formatRuntimeLocation(cmd.varLineNum)
                    + "Cinematic rig '" + rig.name + "' has no selected ranges to play.");
            forceStop = true;
            return;
        }

        totalDuration = cmd.speedExpr == null
                ? DEFAULT_DURATION_SECONDS
                : Math.max(0.1f, (float) ActionLogicalExpressionVm.toDouble(new ActionLogicalExpressionVm(cmd.speedExpr, scope).evaluate()));

        float totalAnchorDistance = 0f;
        for (RuntimePlaybackSegment segment : playbackSegments) {
            totalAnchorDistance += Math.max(0.0001f, segment.anchorDistance);
        }
        if (totalAnchorDistance <= 1e-6f) {
            float evenDuration = totalDuration / playbackSegments.size();
            for (RuntimePlaybackSegment segment : playbackSegments) {
                segment.duration = evenDuration;
            }
        } else {
            for (RuntimePlaybackSegment segment : playbackSegments) {
                segment.duration = totalDuration * (Math.max(0.0001f, segment.anchorDistance) / totalAnchorDistance);
            }
        }

        app.turnOffCameraStates();
        app.onCinematicCameraStarted();
        playbackRegistered = true;
        logPlaybackSummary();
        applyCameraAtElapsed(0f);
        targetCalculated = true;
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            finishPlayback();
            return true;
        }
        if (!targetCalculated) {
            init();
            if (forceStop) {
                finishPlayback();
                return true;
            }
        }
        if (StopModelController.forceStopCommands.get(targetVar) != null) {
            finishPlayback();
            return true;
        }

        elapsed = Math.min(totalDuration, elapsed + tpf);
        applyCameraAtElapsed(elapsed);
        boolean finished = elapsed >= totalDuration - 1e-6f;
        if (finished) {
            finishPlayback();
        }
        return finished;
    }

    private void finishPlayback() {
        if (playbackRegistered) {
            playbackRegistered = false;
            app.onCinematicCameraStopped();
        }
    }

    private void applyCameraAtElapsed(float timeSeconds) {
        RuntimePlaybackSegment active = null;
        float remaining = timeSeconds;
        for (RuntimePlaybackSegment segment : playbackSegments) {
            if (remaining <= segment.duration || segment == playbackSegments.get(playbackSegments.size() - 1)) {
                active = segment;
                break;
            }
            remaining -= segment.duration;
        }
        if (active == null) {
            active = playbackSegments.get(playbackSegments.size() - 1);
            remaining = active.duration;
        }

        updateRuntimeRigTransform(active.rig);

        float segmentProgress = active.duration <= 1e-6f ? 1f : FastMath.clamp(remaining / active.duration, 0f, 1f);
        float easedProgress = applyRigEasing(active.rig, segmentProgress, active.firstSegment, active.lastSegment);
        float anchorCursor = advanceAnchorCursor(active.startAnchor, active.anchorDistance, easedProgress, active.track.anchorCount);
        Vector3f cameraPos = computeTrackWorldPosition(active.track, anchorCursor);
        app.getCamera().setLocation(cameraPos);

        Vector3f lookAt = resolveLookAtPoint(active, cameraPos, anchorCursor + 1f);
        app.getCamera().lookAt(lookAt, Vector3f.UNIT_Y);
    }

    private Vector3f resolveLookAtPoint(RuntimePlaybackSegment active, Vector3f cameraPos, float lookAheadCursor) {
        if (explicitTarget == null && cmd.lookAtTargetVar != null && !cmd.lookAtTargetVar.isBlank()) {
            explicitTarget = app.findVarRuntime(prg, scope, cmd.lookAtTargetVar);
        }
        if (explicitTarget != null) {
            Spatial targetSpatial = app.getEntitySpatial(explicitTarget.varName, explicitTarget.varDef.varType);
            if (targetSpatial != null) {
                return resolveTargetPoint(targetSpatial, active.rig.targetOffset);
            }
        }
        if (rigTarget == null && active.rig.targetEntityName != null && !active.rig.targetEntityName.isBlank()) {
            rigTarget = app.findVarRuntime(prg, scope, active.rig.targetEntityName);
        }
        if (rigTarget != null) {
            Spatial targetSpatial = app.getEntitySpatial(rigTarget.varName, rigTarget.varDef.varType);
            if (targetSpatial != null) {
                return resolveTargetPoint(targetSpatial, active.rig.targetOffset);
            }
        }
        Vector3f explicitTargetPoint = resolveRuntimePoint(cmd.lookAtPosStatement, null);
        if (explicitTargetPoint != null) {
            return explicitTargetPoint;
        }
        return computeTrackWorldPosition(active.track, lookAheadCursor);
    }

    private Vector3f resolveTargetPoint(Spatial targetSpatial, Vector3f offset) {
        Vector3f base = targetSpatial.getWorldTranslation().clone();
        BoundingVolume worldBound = targetSpatial.getWorldBound();
        if (worldBound != null && worldBound.getCenter() != null) {
            base = worldBound.getCenter().clone();
        }
        if (offset != null) {
            base.addLocal(offset);
        }
        return base;
    }

    private void updateRuntimeRigTransform(RuntimeCinematicRig rig) {
        RuntimeTargetReference targetReference = resolveCurrentTargetReference(rig);
        if (targetReference != null && rig != null && rig.hasRelativeTargetPlacement) {
            runtimeRigPosition = targetReference.point.add(targetReference.rotation.mult(rig.relativeRigPositionToTarget));
            runtimeRigRotation = targetReference.rotation.mult(rig.relativeRigRotationToTarget.clone());
        } else {
            runtimeRigPosition = rig != null && rig.position != null ? rig.position.clone() : new Vector3f();
            runtimeRigRotation = rig != null && rig.rotation != null ? rig.rotation.clone() : new Quaternion();
        }
    }

    private RuntimeTargetReference resolveCurrentTargetReference(RuntimeCinematicRig rig) {
        RuntimeTargetReference ref = new RuntimeTargetReference();
        if (cmd.lookAtPosStatement != null && cmd.lookAtPosStatement.startEntity != null && !cmd.lookAtPosStatement.startEntity.isBlank()) {
            RunTimeVarDef runtimeVar = app.findVarRuntime(prg, scope, cmd.lookAtPosStatement.startEntity);
            if (runtimeVar != null) {
                Spatial spatial = app.getEntitySpatial(runtimeVar.varName, runtimeVar.varDef.varType);
                if (spatial != null) {
                    Vector3f point = spatial.getWorldTranslation().clone();
                    Quaternion rot = spatial.getWorldRotation() != null ? spatial.getWorldRotation().clone() : new Quaternion();
                    Util.calcPositionStatementVerbs(scope, cmd.lookAtPosStatement, rot, point);
                    ref.point = point;
                    ref.rotation = rot;
                    return ref;
                }
            }
        }
        if (cmd.lookAtTargetVar != null && !cmd.lookAtTargetVar.isBlank()) {
            RunTimeVarDef runtimeVar = app.findVarRuntime(prg, scope, cmd.lookAtTargetVar);
            if (runtimeVar != null) {
                Spatial spatial = app.getEntitySpatial(runtimeVar.varName, runtimeVar.varDef.varType);
                if (spatial != null) {
                    ref.point = spatial.getWorldTranslation().clone().addLocal(rig.targetOffset);
                    ref.rotation = spatial.getWorldRotation() != null ? spatial.getWorldRotation().clone() : new Quaternion();
                    return ref;
                }
            }
        }
        if (rig.targetEntityName != null && !rig.targetEntityName.isBlank()) {
            RunTimeVarDef runtimeVar = app.findVarRuntime(prg, scope, rig.targetEntityName);
            if (runtimeVar != null) {
                Spatial spatial = app.getEntitySpatial(runtimeVar.varName, runtimeVar.varDef.varType);
                if (spatial != null) {
                    ref.point = spatial.getWorldTranslation().clone().addLocal(rig.targetOffset);
                    ref.rotation = spatial.getWorldRotation() != null ? spatial.getWorldRotation().clone() : new Quaternion();
                    return ref;
                }
            }
        }
        return null;
    }

    private Vector3f resolveRuntimePoint(PositionStatement posStatement, Vector3f fallback) {
        if (posStatement != null && posStatement.startEntity != null && !posStatement.startEntity.isBlank()) {
            RunTimeVarDef runtimeVar = app.findVarRuntime(prg, scope, posStatement.startEntity);
            if (runtimeVar != null) {
                Spatial spatial = app.getEntitySpatial(runtimeVar.varName, runtimeVar.varDef.varType);
                if (spatial != null) {
                    Vector3f point = spatial.getWorldTranslation().clone();
                    Quaternion rot = spatial.getWorldRotation() != null ? spatial.getWorldRotation().clone() : new Quaternion();
                    Util.calcPositionStatementVerbs(scope, posStatement, rot, point);
                    return point;
                }
            }
        }
        return fallback != null ? fallback.clone() : null;
    }

    private void buildPlaybackSegments(RuntimeCinematicRig rig) {
        playbackSegments.clear();
        for (int i = 0; i < rig.segments.size(); i++) {
            RuntimeCinematicSegment segment = rig.segments.get(i);
            RuntimeCinematicTrack track = rig.tracksById.get(segment.trackId);
            if (track == null) {
                continue;
            }
            RuntimePlaybackSegment playbackSegment = new RuntimePlaybackSegment();
            playbackSegment.rig = rig;
            playbackSegment.track = track;
            playbackSegment.startAnchor = normalizeAnchor(segment.startAnchor, track.anchorCount);
            playbackSegment.anchorDistance = computeForwardAnchorDistance(playbackSegment.startAnchor, segment.endAnchor, track.anchorCount);
            playbackSegment.firstSegment = i == 0;
            playbackSegment.lastSegment = i == rig.segments.size() - 1;
            playbackSegments.add(playbackSegment);
        }
    }

    private void logPlaybackSummary() {
        if (playbackSegments.isEmpty()) {
            return;
        }
        RuntimePlaybackSegment first = playbackSegments.get(0);
        RuntimePlaybackSegment last = playbackSegments.get(playbackSegments.size() - 1);
        Vector3f startPos = computeTrackWorldPosition(first.track, first.startAnchor);
        float endCursor = advanceAnchorCursor(last.startAnchor, last.anchorDistance, 1f, last.track.anchorCount);
        Vector3f endPos = computeTrackWorldPosition(last.track, endCursor);
        LOGGER.log(Level.INFO,
                "Cinematic '{0}' playing. target={1}, duration={2}s, start={3}, end={4}",
                new Object[]{
                        ((CinematicCameraVariableDef) cmd.varDef).cinematicCameraId,
                        cmd.lookAtTargetVar != null ? cmd.lookAtTargetVar : last.rig.targetEntityName,
                        totalDuration,
                        startPos,
                        endPos
                });
    }

    private Vector3f computeTrackWorldPosition(RuntimeCinematicTrack track, float anchorCursor) {
        int count = Math.max(8, track.anchorCount);
        float wrapped = anchorCursor % count;
        if (wrapped < 0f) {
            wrapped += count;
        }
        int index0 = (int) FastMath.floor(wrapped);
        int index1 = (index0 + 1) % count;
        float alpha = wrapped - index0;

        Vector3f local0 = getAnchorLocalPoint(track, index0);
        Vector3f local1 = getAnchorLocalPoint(track, index1);
        Vector3f local = local0.clone().interpolateLocal(local1, alpha);

        Transform rigTransform = new Transform(runtimeRigPosition, runtimeRigRotation, runtimeRigScale);
        Transform worldTransform = new Transform(track.localPosition.clone(), track.localRotation.clone(), track.localScale.clone())
                .combineWithParent(rigTransform);
        return worldTransform.transformVector(local, new Vector3f());
    }

    private Vector3f getAnchorLocalPoint(RuntimeCinematicTrack track, int anchorIndex) {
        int count = Math.max(8, track.anchorCount);
        int normalized = normalizeAnchor(anchorIndex, count);
        float angle = FastMath.TWO_PI * normalized / count;
        return new Vector3f(FastMath.cos(angle) * track.radiusX, 0f, FastMath.sin(angle) * track.radiusZ);
    }

    private int normalizeAnchor(int anchorIndex, int anchorCount) {
        int normalized = anchorIndex % anchorCount;
        return normalized < 0 ? normalized + anchorCount : normalized;
    }

    private float computeForwardAnchorDistance(float currentCursor, int endAnchor, int anchorCount) {
        float current = currentCursor % anchorCount;
        if (current < 0) {
            current += anchorCount;
        }
        float end = endAnchor % anchorCount;
        if (end < 0) {
            end += anchorCount;
        }
        if (end >= current) {
            return end - current;
        }
        return (anchorCount - current) + end;
    }

    private float advanceAnchorCursor(int startAnchor, float distance, float progress, int anchorCount) {
        float cursor = startAnchor + distance * FastMath.clamp(progress, 0f, 1f);
        while (cursor >= anchorCount) {
            cursor -= anchorCount;
        }
        return cursor;
    }

    private float applyRigEasing(RuntimeCinematicRig rig, float progress, boolean firstSegment, boolean lastSegment) {
        float p = FastMath.clamp(progress, 0f, 1f);
        if (rig == null) {
            return p;
        }
        String easeIn = firstSegment ? rig.easeIn : "linear";
        String easeOut = lastSegment ? rig.easeOut : "linear";
        boolean useEaseIn = easeIn != null && !"linear".equals(easeIn);
        boolean useEaseOut = easeOut != null && !"linear".equals(easeOut);
        if (!useEaseIn && !useEaseOut) {
            return p;
        }
        if (useEaseIn && !useEaseOut) {
            return applySingleEase(easeIn, p);
        }
        if (!useEaseIn) {
            return applySingleEase(easeOut, p);
        }
        if (p < 0.5f) {
            return 0.5f * applySingleEase(easeIn, p * 2f);
        }
        return 0.5f + 0.5f * applySingleEase(easeOut, (p - 0.5f) * 2f);
    }

    private float applySingleEase(String easeId, float t) {
        float p = FastMath.clamp(t, 0f, 1f);
        if (easeId == null || easeId.isBlank() || "linear".equals(easeId)) {
            return p;
        }
        switch (easeId) {
            case "ease_in_quad":
                return p * p;
            case "ease_out_quad":
                return 1f - (1f - p) * (1f - p);
            case "ease_in_cubic":
                return p * p * p;
            case "ease_out_cubic":
                return 1f - FastMath.pow(1f - p, 3f);
            case "ease_in_expo":
                return p <= 0f ? 0f : FastMath.pow(2f, 10f * (p - 1f));
            case "ease_out_expo":
                return p >= 1f ? 1f : 1f - FastMath.pow(2f, -10f * p);
            case "ease_in_sine":
                return 1f - FastMath.cos((p * FastMath.PI) / 2f);
            case "ease_out_sine":
                return FastMath.sin((p * FastMath.PI) / 2f);
            default:
                return p;
        }
    }

    private static final class RuntimePlaybackSegment {
        RuntimeCinematicRig rig;
        RuntimeCinematicTrack track;
        int startAnchor;
        float anchorDistance;
        float duration;
        boolean firstSegment;
        boolean lastSegment;
    }
}
