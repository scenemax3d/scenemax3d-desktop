package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.motion.ThrowMotionDefinition;
import com.scenemaxeng.common.motion.ThrowMotionSample;
import com.scenemaxeng.common.motion.ThrowMotionSampler;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.ThrowMotionApplyCommand;

import java.util.Collections;
import java.util.List;

public class ThrowMotionApplyController extends SceneMaxBaseController {
    private static final float SAMPLE_DELTA = 1f / 60f;
    private static final float DEFAULT_TARGET_ARC_DISTANCE = 12f;

    private Spatial spatial;
    private ThrowMotionDefinition definition;
    private RuntimeThrowMotionValue motionValue;
    private List<ThrowMotionSample> samples = Collections.emptyList();
    private Vector3f startWorld;
    private Vector3f previousWorld;
    private Vector3f rightAxis;
    private Vector3f upAxis;
    private Vector3f forwardAxis;
    private float time;

    public ThrowMotionApplyController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ThrowMotionApplyCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            return true;
        }

        if (!targetCalculated) {
            targetCalculated = true;
            if (!initMotion()) {
                return true;
            }
        }

        if (samples.isEmpty()) {
            return true;
        }

        time += Math.max(0f, tpf);
        ThrowMotionSample sample = sampleAt(time);
        Vector3f nextWorld = toWorld(sample.getPosition());
        Vector3f delta = nextWorld.subtract(previousWorld);
        spatial.move(delta);
        previousWorld = nextWorld;

        return time >= samples.get(samples.size() - 1).getTime();
    }

    private boolean initMotion() {
        ThrowMotionApplyCommand cmd = (ThrowMotionApplyCommand) this.cmd;
        VarInst motionVar = scope.getVar(cmd.motionVarName);
        if (motionVar == null || !(motionVar.value instanceof RuntimeThrowMotionValue)) {
            app.handleRuntimeError("Motion '" + cmd.motionVarName + "' is not defined");
            return false;
        }

        motionValue = (RuntimeThrowMotionValue) motionVar.value;
        definition = app.getAssetsMapping() == null ? null : app.getAssetsMapping().getThrowMotionDefinition(motionValue.motionAssetId);
        if (definition == null) {
            app.handleRuntimeError("Throw motion asset '" + motionValue.motionAssetId + "' was not found under resources/throw_motions");
            return false;
        }

        spatial = cmd.appliedObjectIsEquippedWeapon
                ? resolveEquippedWeaponSpatial(cmd)
                : resolveObjectSpatial(cmd);
        if (spatial == null) {
            return false;
        }

        startWorld = spatial.getWorldTranslation().clone();
        Vector3f direction = resolveDirection(startWorld, spatial);
        buildBasis(direction);

        ThrowMotionSampler.PreviewScenario scenario = new ThrowMotionSampler.PreviewScenario();
        scenario.start = Vector3f.ZERO.clone();
        scenario.target = new Vector3f(0f, 0f, pathDistance(definition));
        scenario.groundY = -100000f;
        scenario.stopAtGround = false;
        samples = ThrowMotionSampler.sample(definition, scenario, SAMPLE_DELTA);
        previousWorld = startWorld.clone();
        time = 0f;

        if (!samples.isEmpty()) {
            spatial.move(toWorld(samples.get(0).getPosition()).subtract(previousWorld));
            previousWorld = spatial.getWorldTranslation().clone();
        }
        return true;
    }

    private Spatial resolveObjectSpatial(ThrowMotionApplyCommand cmd) {
        RunTimeVarDef applied = app.findVarRuntime(prg, scope, cmd.appliedObjectVarName);
        if (applied == null) {
            app.handleRuntimeError(app.formatUndefinedVariableError(
                    cmd.appliedObjectVarLine,
                    cmd.appliedObjectVarName,
                    cmd.appliedObjectVarDef,
                    getClass().getSimpleName()));
            return null;
        }

        Spatial resolved = app.getEntitySpatial(applied.varName, applied.varDef.varType);
        if (resolved == null) {
            app.handleRuntimeError("Cannot find object '" + cmd.appliedObjectVarName + "' for throw motion");
        }
        return resolved;
    }

    private Spatial resolveEquippedWeaponSpatial(ThrowMotionApplyCommand cmd) {
        RunTimeVarDef owner = app.findVarRuntime(prg, scope, cmd.appliedObjectVarName);
        if (owner == null) {
            app.handleRuntimeError(app.formatUndefinedVariableError(
                    cmd.appliedObjectVarLine,
                    cmd.appliedObjectVarName,
                    cmd.appliedObjectVarDef,
                    getClass().getSimpleName()));
            return null;
        }

        EquippedWeaponRuntime runtime = app.getEquippedWeapon(owner.varName, "rightHand");
        if (runtime == null) {
            app.handleRuntimeError("Cannot apply throw motion: '" + cmd.appliedObjectVarName
                    + "' has no equipped weapon.");
            return null;
        }

        Spatial resolved = runtime.getSpawnedModel();
        if (resolved == null) {
            app.handleRuntimeError("Cannot apply throw motion: equipped weapon on '"
                    + cmd.appliedObjectVarName + "' has no active model.");
        }
        return resolved;
    }

    private Vector3f resolveDirection(Vector3f start, Spatial appliedSpatial) {
        Vector3f target = resolveTargetPosition();
        Vector3f direction = target == null ? null : target.subtract(start);
        if (direction == null || direction.lengthSquared() < 0.0001f) {
            direction = appliedSpatial.getWorldRotation().mult(Vector3f.UNIT_Z);
        }
        if (direction == null || direction.lengthSquared() < 0.0001f) {
            direction = Vector3f.UNIT_Z.clone();
        }
        return direction.normalizeLocal();
    }

    private Vector3f resolveTargetPosition() {
        if (motionValue.targetKind == RuntimeThrowMotionValue.TargetKind.OBJECT) {
            RunTimeVarDef target = app.findVarRuntime(prg, scope, motionValue.targetVarName);
            if (target == null) {
                app.handleRuntimeError("Motion target '" + motionValue.targetVarName + "' is not defined");
                return null;
            }
            Spatial targetSpatial = app.getEntitySpatial(target.varName, target.varDef.varType);
            return targetSpatial == null ? null : targetSpatial.getWorldTranslation().clone();
        }

        if (motionValue.targetKind == RuntimeThrowMotionValue.TargetKind.POSITION) {
            float x = (float) ActionLogicalExpressionVm.toDouble(new ActionLogicalExpressionVm(motionValue.targetXExpr, scope).evaluate());
            float y = (float) ActionLogicalExpressionVm.toDouble(new ActionLogicalExpressionVm(motionValue.targetYExpr, scope).evaluate());
            float z = (float) ActionLogicalExpressionVm.toDouble(new ActionLogicalExpressionVm(motionValue.targetZExpr, scope).evaluate());
            return new Vector3f(x, y, z);
        }

        return null;
    }

    private void buildBasis(Vector3f forward) {
        forwardAxis = forward.clone().normalizeLocal();
        Vector3f worldUp = Math.abs(forwardAxis.dot(Vector3f.UNIT_Y)) > 0.96f
                ? Vector3f.UNIT_X.clone()
                : Vector3f.UNIT_Y.clone();
        rightAxis = worldUp.cross(forwardAxis).normalizeLocal();
        upAxis = forwardAxis.cross(rightAxis).normalizeLocal();
    }

    private Vector3f toWorld(Vector3f local) {
        return startWorld.add(toWorldDirection(local));
    }

    private Vector3f toWorldDirection(Vector3f local) {
        return rightAxis.mult(local.x).add(upAxis.mult(local.y)).add(forwardAxis.mult(local.z));
    }

    private ThrowMotionSample sampleAt(float t) {
        ThrowMotionSample last = samples.get(samples.size() - 1);
        if (t <= 0f) {
            return samples.get(0);
        }
        if (t >= last.getTime()) {
            return last;
        }
        for (int i = 1; i < samples.size(); i++) {
            ThrowMotionSample current = samples.get(i);
            if (current.getTime() >= t) {
                ThrowMotionSample previous = samples.get(i - 1);
                float span = Math.max(0.0001f, current.getTime() - previous.getTime());
                float u = FastMath.clamp((t - previous.getTime()) / span, 0f, 1f);
                Vector3f pos = previous.getPosition().interpolateLocal(current.getPosition(), u);
                Vector3f velocity = previous.getVelocity().interpolateLocal(current.getVelocity(), u);
                float spin = previous.getSpinDegrees() + (current.getSpinDegrees() - previous.getSpinDegrees()) * u;
                return new ThrowMotionSample(t, pos, velocity, spin);
            }
        }
        return last;
    }

    private float pathDistance(ThrowMotionDefinition definition) {
        ThrowMotionDefinition.MotionParameters p = definition.getParameters();
        String type = ThrowMotionDefinition.normalizeMotionType(definition.getMotionType());
        if (ThrowMotionDefinition.TYPE_STRAIGHT.equals(type) || ThrowMotionDefinition.TYPE_HOMING.equals(type)) {
            return (float) Math.max(0.1, p.maxDistance);
        }
        if (ThrowMotionDefinition.TYPE_RETURNING.equals(type)) {
            return (float) Math.max(0.1, p.outboundDistance);
        }
        if (ThrowMotionDefinition.TYPE_TARGET_ARC.equals(type)) {
            return DEFAULT_TARGET_ARC_DISTANCE;
        }
        return DEFAULT_TARGET_ARC_DISTANCE;
    }

}
