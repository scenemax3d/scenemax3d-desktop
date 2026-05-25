package com.scenemaxeng.projector.ik;

import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.ik.IKLayerDefinition;
import com.scenemaxeng.projector.AppModel;
import com.scenemaxeng.projector.SceneMaxApp;

public class IKContext {
    private final SceneMaxApp app;
    private final AppModel model;
    private final IKLayerDefinition layer;
    private final float tpf;
    private final float effectiveWeight;
    private final IKTarget runtimeTarget;

    public IKContext(SceneMaxApp app, AppModel model, IKLayerDefinition layer, float tpf, float effectiveWeight) {
        this(app, model, layer, tpf, effectiveWeight, null);
    }

    public IKContext(SceneMaxApp app, AppModel model, IKLayerDefinition layer, float tpf, float effectiveWeight, IKTarget runtimeTarget) {
        this.app = app;
        this.model = model;
        this.layer = layer;
        this.tpf = tpf;
        this.effectiveWeight = FastMath.clamp(effectiveWeight, 0f, 1f);
        this.runtimeTarget = runtimeTarget;
    }

    public SceneMaxApp getApp() {
        return app;
    }

    public AppModel getModel() {
        return model;
    }

    public IKLayerDefinition getLayer() {
        return layer;
    }

    public float getTpf() {
        return tpf;
    }

    public float getEffectiveWeight() {
        return effectiveWeight;
    }

    public SkinningControl getSkinningControl() {
        return model == null ? null : model.getSkinningControl();
    }

    public Armature getArmature() {
        SkinningControl skinningControl = getSkinningControl();
        return skinningControl == null ? null : skinningControl.getArmature();
    }

    public Joint findJoint(String name) {
        Armature armature = getArmature();
        if (armature == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        Joint direct = armature.getJoint(name.trim());
        if (direct != null) {
            return direct;
        }
        String normalized = normalizeJointName(name);
        for (Joint joint : armature.getJointList()) {
            if (normalizeJointName(joint.getName()).equals(normalized)) {
                return joint;
            }
        }
        return null;
    }

    public Spatial findTargetSpatial(String name) {
        if (app == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        return app.getEntitySpatial(name.trim());
    }

    public IKTarget getTarget(String targetName) {
        if (runtimeTarget != null) {
            return runtimeTarget;
        }
        Spatial spatial = findTargetSpatial(targetName);
        return new IKTarget(spatial, null, null);
    }

    public IKChain getTwoBoneChain() {
        return new IKChain(
                findJoint(layer.getRootJoint()),
                findJoint(layer.getMiddleJoint()),
                findJoint(layer.getEndJoint()));
    }

    public IKChain getThreeBoneChain() {
        return new IKChain(
                findJoint(layer.getRootJoint()),
                findJoint(layer.getMiddleJoint()),
                findJoint(layer.getSecondMiddleJoint()),
                findJoint(layer.getEndJoint()));
    }

    public Vector3f jointWorldPosition(Joint joint) {
        Transform transform = jointWorldTransform(joint);
        return transform == null ? null : transform.getTranslation().clone();
    }

    public Quaternion jointWorldRotation(Joint joint) {
        Transform transform = jointWorldTransform(joint);
        return transform == null ? null : transform.getRotation().clone();
    }

    public Transform jointWorldTransform(Joint joint) {
        if (joint == null || getSkinningControl() == null || getSkinningControl().getSpatial() == null) {
            return null;
        }
        Transform skinTransform = getSkinningControl().getSpatial().getWorldTransform();
        return joint.getModelTransform().clone().combineWithParent(skinTransform);
    }

    public Quaternion parentWorldRotation(Joint joint) {
        if (joint == null || getSkinningControl() == null || getSkinningControl().getSpatial() == null) {
            return new Quaternion();
        }
        Joint parent = joint.getParent();
        if (parent == null) {
            return getSkinningControl().getSpatial().getWorldRotation().clone();
        }
        Quaternion rotation = jointWorldRotation(parent);
        return rotation == null ? new Quaternion() : rotation;
    }

    public void setJointWorldRotation(Joint joint, Quaternion desiredWorldRotation, float weight) {
        if (joint == null || desiredWorldRotation == null) {
            return;
        }
        Quaternion parentWorld = parentWorldRotation(joint);
        Quaternion desiredLocal = parentWorld.inverse().mult(desiredWorldRotation).normalizeLocal();
        Quaternion currentLocal = joint.getLocalRotation().clone();
        Quaternion blended = new Quaternion().slerp(currentLocal, desiredLocal, FastMath.clamp(weight, 0f, 1f));
        joint.setLocalRotation(blended);
        updateArmature();
    }

    public void updateArmature() {
        Armature armature = getArmature();
        if (armature != null) {
            armature.update();
        }
    }

    public static String normalizeJointName(String name) {
        if (name == null) {
            return "";
        }
        String stripped = name;
        int colon = stripped.lastIndexOf(':');
        if (colon >= 0 && colon < stripped.length() - 1) {
            stripped = stripped.substring(colon + 1);
        }
        return stripped.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
