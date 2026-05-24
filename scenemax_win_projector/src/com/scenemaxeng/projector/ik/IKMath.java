package com.scenemaxeng.projector.ik;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

final class IKMath {
    private IKMath() {
    }

    static Quaternion rotationBetween(Vector3f from, Vector3f to) {
        Vector3f a = safeNormalize(from);
        Vector3f b = safeNormalize(to);
        float dot = FastMath.clamp(a.dot(b), -1f, 1f);
        if (dot > 0.9999f) {
            return new Quaternion();
        }
        if (dot < -0.9999f) {
            Vector3f axis = a.cross(Vector3f.UNIT_X);
            if (axis.lengthSquared() < 0.0001f) {
                axis = a.cross(Vector3f.UNIT_Y);
            }
            axis.normalizeLocal();
            return new Quaternion().fromAngleAxis(FastMath.PI, axis);
        }
        Vector3f axis = a.cross(b).normalizeLocal();
        return new Quaternion().fromAngleAxis(FastMath.acos(dot), axis);
    }

    static Vector3f safeNormalize(Vector3f value) {
        if (value == null || value.lengthSquared() < 0.000001f) {
            return Vector3f.UNIT_Z.clone();
        }
        return value.normalize();
    }

    static Vector3f projectOnPlane(Vector3f vector, Vector3f normal) {
        Vector3f n = safeNormalize(normal);
        return vector.subtract(n.mult(vector.dot(n)));
    }
}
