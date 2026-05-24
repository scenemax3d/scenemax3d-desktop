package com.scenemaxeng.projector.ik;

import com.jme3.anim.Joint;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.scenemaxeng.common.ik.IKLayerDefinition;

import java.util.ArrayList;
import java.util.List;

public class LookAtIKSolver implements IKSolver {
    @Override
    public void solve(IKContext context) {
        IKLayerDefinition layer = context.getLayer();
        Vector3f target = context.getTarget(layer.getTarget()).getWorldPosition();
        List<Joint> joints = affectedJoints(context, layer);
        if (target == null || joints.isEmpty()) {
            return;
        }

        float remainingWeight = context.getEffectiveWeight();
        float maxRadians = layer.getMaxAngle() <= 0f ? FastMath.PI : layer.getMaxAngle() * FastMath.DEG_TO_RAD;
        for (int i = 0; i < joints.size(); i++) {
            Joint joint = joints.get(i);
            Vector3f jointPos = context.jointWorldPosition(joint);
            Quaternion jointWorld = context.jointWorldRotation(joint);
            if (jointPos == null || jointWorld == null) {
                continue;
            }
            Vector3f currentForward = jointWorld.mult(Vector3f.UNIT_Z);
            Vector3f desiredForward = target.subtract(jointPos);
            if (desiredForward.lengthSquared() < 0.000001f) {
                continue;
            }
            Quaternion delta = IKMath.rotationBetween(currentForward, desiredForward);
            Vector3f axis = new Vector3f();
            float angle = delta.toAngleAxis(axis);
            if (angle > maxRadians) {
                delta = new Quaternion().fromAngleAxis(maxRadians, axis);
            }
            float jointWeight = remainingWeight / (joints.size() - i);
            Quaternion desiredWorld = delta.mult(jointWorld).normalizeLocal();
            context.setJointWorldRotation(joint, desiredWorld, jointWeight);
            remainingWeight = Math.max(0f, remainingWeight - jointWeight);
        }
    }

    private List<Joint> affectedJoints(IKContext context, IKLayerDefinition layer) {
        List<Joint> joints = new ArrayList<>();
        for (String jointName : layer.getAffectedJoints()) {
            Joint joint = context.findJoint(jointName);
            if (joint != null) {
                joints.add(joint);
            }
        }
        if (joints.isEmpty()) {
            Joint end = context.findJoint(layer.getEndJoint());
            if (end != null) {
                joints.add(end);
            }
        }
        return joints;
    }
}
