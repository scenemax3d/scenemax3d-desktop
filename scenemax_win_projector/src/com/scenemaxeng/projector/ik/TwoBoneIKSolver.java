package com.scenemaxeng.projector.ik;

import com.jme3.anim.Joint;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.ik.IKLayerDefinition;

public class TwoBoneIKSolver implements IKSolver {
    @Override
    public void solve(IKContext context) {
        IKLayerDefinition layer = context.getLayer();
        IKChain chain = context.getTwoBoneChain();
        if (!chain.isComplete()) {
            return;
        }

        Vector3f rootPos = context.jointWorldPosition(chain.getRoot());
        Vector3f midPos = context.jointWorldPosition(chain.getMiddle());
        Vector3f endPos = context.jointWorldPosition(chain.getEnd());
        if (rootPos == null || midPos == null || endPos == null) {
            return;
        }

        Vector3f targetPos = context.getTarget(layer.getTarget()).getWorldPosition().addLocal(offset(layer.getPositionOffset()));
        float upperLength = midPos.distance(rootPos);
        float lowerLength = endPos.distance(midPos);
        if (upperLength < 0.0001f || lowerLength < 0.0001f) {
            return;
        }

        float maxReach = upperLength + lowerLength;
        if (layer.isAllowStretch()) {
            maxReach *= Math.max(1f, layer.getMaxStretch());
        }
        Vector3f rootToTarget = targetPos.subtract(rootPos);
        float distance = FastMath.clamp(rootToTarget.length(), 0.0001f, maxReach * 0.999f);
        Vector3f targetDir = rootToTarget.normalize();

        Vector3f poleDir = resolvePoleDirection(context, layer, rootPos, midPos, targetDir);
        float bendDistance = (distance * distance + upperLength * upperLength - lowerLength * lowerLength) / (2f * distance);
        float bendHeight = FastMath.sqrt(Math.max(0f, upperLength * upperLength - bendDistance * bendDistance));
        Vector3f desiredMid = rootPos.add(targetDir.mult(bendDistance)).addLocal(poleDir.mult(bendHeight));

        rotateJointToward(context, chain.getRoot(), midPos, desiredMid, rootPos);
        context.updateArmature();

        midPos = context.jointWorldPosition(chain.getMiddle());
        endPos = context.jointWorldPosition(chain.getEnd());
        if (midPos == null || endPos == null) {
            return;
        }
        rotateJointToward(context, chain.getMiddle(), endPos, targetPos, midPos);
        context.updateArmature();
    }

    protected static void rotateJointToward(IKContext context, Joint joint, Vector3f currentChildPos,
                                            Vector3f desiredChildPos, Vector3f pivot) {
        Vector3f current = currentChildPos.subtract(pivot);
        Vector3f desired = desiredChildPos.subtract(pivot);
        if (current.lengthSquared() < 0.000001f || desired.lengthSquared() < 0.000001f) {
            return;
        }
        Quaternion currentWorld = context.jointWorldRotation(joint);
        if (currentWorld == null) {
            return;
        }
        Quaternion delta = IKMath.rotationBetween(current, desired);
        Quaternion desiredWorld = delta.mult(currentWorld).normalizeLocal();
        context.setJointWorldRotation(joint, desiredWorld, context.getEffectiveWeight());
    }

    private Vector3f resolvePoleDirection(IKContext context, IKLayerDefinition layer, Vector3f rootPos,
                                          Vector3f midPos, Vector3f targetDir) {
        Spatial pole = context.findTargetSpatial(layer.getPoleTarget());
        Vector3f rawPole = pole == null ? midPos.subtract(rootPos) : pole.getWorldTranslation().subtract(rootPos);
        Vector3f projected = IKMath.projectOnPlane(rawPole, targetDir);
        if (projected.lengthSquared() < 0.000001f) {
            projected = targetDir.cross(Vector3f.UNIT_Y);
            if (projected.lengthSquared() < 0.000001f) {
                projected = targetDir.cross(Vector3f.UNIT_X);
            }
        }
        return projected.normalize();
    }

    private Vector3f offset(float[] values) {
        if (values == null || values.length < 3) {
            return new Vector3f();
        }
        return new Vector3f(values[0], values[1], values[2]);
    }
}
