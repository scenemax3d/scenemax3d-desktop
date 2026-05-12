package com.scenemaxeng.projector;

import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.common.weapons.WeaponAttachmentTransform;
import com.scenemaxeng.common.weapons.WeaponDefinition;

public class WeaponAttachmentResolver {
    private final SceneMaxApp app;

    public WeaponAttachmentResolver(SceneMaxApp app) {
        this.app = app;
    }

    public Spatial attachWeaponModel(String ownerVarName, WeaponDefinition definition) {
        if (definition.getModelAssetId() == null || definition.getModelAssetId().trim().isEmpty()) {
            return null;
        }

        Spatial model = loadWeaponModel(definition);
        if (model == null) {
            return null;
        }

        Node attachNode = resolveAttachmentNode(ownerVarName, definition.getDefaultAttachmentPoint());
        if (attachNode == null) {
            model.removeFromParent();
            return null;
        }

        model.setName("weapon_" + definition.getId() + "_" + ownerVarName);
        applyAttachmentTransform(model, definition.getAttachmentTransform());
        attachNode.attachChild(model);
        model.updateGeometricState();
        return model;
    }

    private Spatial loadWeaponModel(WeaponDefinition definition) {
        AssetManager assetManager = app.getAssetManager();
        AssetsMapping assetsMapping = app.getAssetsMapping();
        String modelAssetId = definition.getModelAssetId().trim();
        String modelPath = modelAssetId;

        if (assetsMapping != null) {
            ResourceSetup resource = assetsMapping.get3DModelsIndex().get(modelAssetId.toLowerCase());
            if (resource != null) {
                modelPath = resource.path;
            }
        }

        try {
            return assetManager.loadModel(modelPath);
        } catch (Exception ex) {
            app.handleRuntimeError("Weapon '" + definition.getName() + "' could not load model '" + modelAssetId + "'.");
            return null;
        }
    }

    private Node resolveAttachmentNode(String ownerVarName, String attachmentPoint) {
        AppModel ownerModel = app.getAppModel(ownerVarName);
        if (ownerModel == null || ownerModel.model == null) {
            app.handleRuntimeError("Cannot equip weapon: character '" + ownerVarName + "' was not found.");
            return null;
        }

        if (attachmentPoint != null && !attachmentPoint.trim().isEmpty()) {
            Node jointNode = ownerModel.getJointAttachementNode(attachmentPoint.trim());
            if (jointNode != null) {
                return jointNode;
            }
            app.handleRuntimeError("Weapon attachment point '" + attachmentPoint + "' was not found on '" + ownerVarName + "'.");
            return null;
        }

        return ownerModel.model;
    }

    private void applyAttachmentTransform(Spatial model, WeaponAttachmentTransform transform) {
        if (transform == null) {
            return;
        }
        model.setLocalTranslation(new Vector3f(
                (float) transform.getOffsetX(),
                (float) transform.getOffsetY(),
                (float) transform.getOffsetZ()));
        Quaternion rotation = new Quaternion().fromAngles(
                (float) transform.getRotationX() * FastMath.DEG_TO_RAD,
                (float) transform.getRotationY() * FastMath.DEG_TO_RAD,
                (float) transform.getRotationZ() * FastMath.DEG_TO_RAD);
        model.setLocalRotation(rotation);
        model.setLocalScale(
                (float) transform.getScaleX(),
                (float) transform.getScaleY(),
                (float) transform.getScaleZ());
    }
}
