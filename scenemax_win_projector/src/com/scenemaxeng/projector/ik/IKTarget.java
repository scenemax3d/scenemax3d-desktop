package com.scenemaxeng.projector.ik;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public class IKTarget {
    private final Spatial targetSpatial;
    private final Vector3f position;
    private final Quaternion rotation;

    public IKTarget(Spatial targetSpatial, Vector3f fallbackPosition, Quaternion fallbackRotation) {
        this.targetSpatial = targetSpatial;
        this.position = fallbackPosition == null ? new Vector3f() : fallbackPosition.clone();
        this.rotation = fallbackRotation == null ? new Quaternion() : fallbackRotation.clone();
    }

    public Vector3f getWorldPosition() {
        return targetSpatial != null ? targetSpatial.getWorldTranslation().clone() : position.clone();
    }

    public Quaternion getWorldRotation() {
        return targetSpatial != null ? targetSpatial.getWorldRotation().clone() : rotation.clone();
    }
}
