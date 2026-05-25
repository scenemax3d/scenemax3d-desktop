package com.scenemaxeng.projector.ik;

import com.jme3.anim.Joint;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.ik.IKLayerDefinition;

public class ThreeBoneIKSolver implements IKSolver {
    @Override
    public void solve(IKContext context) {
        IKLayerDefinition layer = context.getLayer();
        IKChain chain = context.getThreeBoneChain();
        if (!chain.isThreeBoneComplete()) {
            return;
        }

        Vector3f targetPos = context.getTarget(layer.getTarget()).getWorldPosition().addLocal(offset(layer.getPositionOffset()));
        Joint[] joints = new Joint[]{chain.getRoot(), chain.getMiddle(), chain.getSecondMiddle()};
        int iterations = Math.max(1, layer.getIterations());
        float toleranceSquared = Math.max(0.000001f, layer.getTolerance() * layer.getTolerance());

        for (int iteration = 0; iteration < iterations; iteration++) {
            applyPoleHint(context, layer, chain, targetPos);
            Vector3f endPos = context.jointWorldPosition(chain.getEnd());
            if (endPos == null || endPos.distanceSquared(targetPos) <= toleranceSquared) {
                return;
            }

            for (int i = joints.length - 1; i >= 0; i--) {
                Joint joint = joints[i];
                Vector3f jointPos = context.jointWorldPosition(joint);
                endPos = context.jointWorldPosition(chain.getEnd());
                if (jointPos == null || endPos == null) {
                    return;
                }
                TwoBoneIKSolver.rotateJointToward(context, joint, endPos, targetPos, jointPos);
                context.updateArmature();
            }
        }
    }

    private void applyPoleHint(IKContext context, IKLayerDefinition layer, IKChain chain, Vector3f targetPos) {
        Spatial pole = context.findTargetSpatial(layer.getPoleTarget());
        if (pole == null) {
            return;
        }
        Vector3f rootPos = context.jointWorldPosition(chain.getRoot());
        Vector3f middlePos = context.jointWorldPosition(chain.getMiddle());
        Quaternion rootWorld = context.jointWorldRotation(chain.getRoot());
        if (rootPos == null || middlePos == null || rootWorld == null) {
            return;
        }
        Vector3f axis = targetPos.subtract(rootPos);
        Vector3f currentPole = IKMath.projectOnPlane(middlePos.subtract(rootPos), axis);
        Vector3f desiredPole = IKMath.projectOnPlane(pole.getWorldTranslation().subtract(rootPos), axis);
        if (currentPole.lengthSquared() < 0.000001f || desiredPole.lengthSquared() < 0.000001f) {
            return;
        }
        Quaternion delta = IKMath.rotationBetween(currentPole, desiredPole);
        Quaternion desiredWorld = delta.mult(rootWorld).normalizeLocal();
        context.setJointWorldRotation(chain.getRoot(), desiredWorld, FastMath.clamp(context.getEffectiveWeight() * 0.5f, 0f, 1f));
        context.updateArmature();
    }

    private Vector3f offset(float[] values) {
        if (values == null || values.length < 3) {
            return new Vector3f();
        }
        return new Vector3f(values[0], values[1], values[2]);
    }
}
