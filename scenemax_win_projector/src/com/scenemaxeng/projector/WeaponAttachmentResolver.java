package com.scenemaxeng.projector;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.GhostControl;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.common.weapons.WeaponAttachmentTransform;
import com.scenemaxeng.common.weapons.WeaponColliderDefinition;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.common.weapons.WeaponPostureDefinition;
import com.scenemaxeng.compiler.BoxVariableDef;
import com.scenemaxeng.compiler.SphereVariableDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WeaponAttachmentResolver {
    private final SceneMaxApp app;
    private List<String> lastRegisteredColliderNames = Collections.emptyList();

    public WeaponAttachmentResolver(SceneMaxApp app) {
        this.app = app;
    }

    public Spatial attachWeaponModel(String ownerVarName, WeaponDefinition definition) {
        return attachWeaponModel(ownerVarName, definition, null);
    }

    public Spatial attachWeaponModel(String ownerVarName, WeaponDefinition definition, String postureIdOrName) {
        lastRegisteredColliderNames = Collections.emptyList();
        if (definition.getModelAssetId() == null || definition.getModelAssetId().trim().isEmpty()) {
            return null;
        }

        Spatial model = loadWeaponModel(definition);
        if (model == null) {
            return null;
        }

        WeaponPostureDefinition posture = definition.findPosture(postureIdOrName);
        Node attachNode = resolveAttachmentNode(ownerVarName, posture.getAttachmentPoint());
        if (attachNode == null) {
            model.removeFromParent();
            return null;
        }

        Node compensatedRoot = new Node("weapon_" + definition.getId() + "_" + ownerVarName);
        Node transformRoot = new Node("weapon_transform_" + definition.getId() + "_" + ownerVarName);
        compensateAttachmentScale(attachNode, compensatedRoot);
        transformRoot.attachChild(model);
        compensatedRoot.attachChild(transformRoot);
        applyAttachmentTransform(transformRoot, posture.getTransform());
        registerColliders(ownerVarName, definition, transformRoot);
        attachNode.attachChild(compensatedRoot);
        return compensatedRoot;
    }

    public List<String> getLastRegisteredColliderNames() {
        return new ArrayList<>(lastRegisteredColliderNames);
    }

    private Spatial loadWeaponModel(WeaponDefinition definition) {
        AssetManager assetManager = app.getAssetManager();
        AssetsMapping assetsMapping = app.getAssetsMapping();
        String modelAssetId = definition.getModelAssetId().trim();
        String modelPath = modelAssetId;
        ResourceSetup resource = null;

        if (assetsMapping != null) {
            resource = assetsMapping.get3DModelsIndex().get(modelAssetId.toLowerCase());
            if (resource != null) {
                modelPath = resource.path;
            }
        }

        try {
            Spatial model = assetManager.loadModel(modelPath);
            app.isolateLoadedModelMaterials(model);
            Node attachmentRoot = new Node("weapon_visual_" + definition.getId());
            attachmentRoot.attachChild(model);
            applyResourceTransform(model, resource);
            return attachmentRoot;
        } catch (Exception ex) {
            app.handleRuntimeError("Weapon '" + definition.getId() + "' could not load model '" + modelAssetId + "'.");
            return null;
        }
    }

    private void applyResourceTransform(Spatial model, ResourceSetup resource) {
        if (model == null || resource == null) {
            return;
        }
        model.setLocalScale(resource.scaleX, resource.scaleY, resource.scaleZ);
        model.setLocalTranslation(resource.localTranslationX, resource.localTranslationY, resource.localTranslationZ);
        model.setLocalRotation(new Quaternion().fromAngles(0f, resource.rotateY * FastMath.DEG_TO_RAD, 0f));
    }

    private void compensateAttachmentScale(Node attachmentNode, Node compensatedNode) {
        if (attachmentNode == null || compensatedNode == null) {
            return;
        }
        Vector3f worldScale = attachmentNode.getWorldScale();
        compensatedNode.setLocalScale(
                inverseScaleComponent(worldScale.x),
                inverseScaleComponent(worldScale.y),
                inverseScaleComponent(worldScale.z));
    }

    private float inverseScaleComponent(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale) || Math.abs(scale) < 0.000001f) {
            return 1f;
        }
        return 1f / scale;
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
                ownerModel.model.updateLogicalState(0f);
                ownerModel.model.updateGeometricState();
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

    private void registerColliders(String ownerVarName, WeaponDefinition definition, Node transformRoot) {
        if (definition == null || transformRoot == null || definition.getColliders().isEmpty()) {
            return;
        }
        AppModel ownerModel = app.getAppModel(ownerVarName);
        SceneMaxScope ownerScope = ownerModel != null && ownerModel.entityInst != null
                ? ownerModel.entityInst.scope
                : null;
        if (ownerScope == null) {
            return;
        }
        List<String> registered = new ArrayList<>();
        for (WeaponColliderDefinition collider : definition.getColliders()) {
            if (collider == null || collider.getName() == null || collider.getName().trim().isEmpty()) {
                continue;
            }
            String colliderName = collider.getName().trim();
            String runtimeName = colliderName + "@" + ownerScope.scopeId;
            Node colliderNode = new Node(runtimeName);
            Geometry geometry = createColliderGeometry(collider, runtimeName);
            colliderNode.attachChild(geometry);
            applyAttachmentTransform(colliderNode, collider.getTransform());
            GhostControl ghostControl = createColliderGhost(collider);
            colliderNode.addControl(ghostControl);
            transformRoot.attachChild(colliderNode);
            EntityInstBase inst = createColliderInst(collider, ownerScope);
            app.registerWeaponCollider(runtimeName, colliderNode, inst, ghostControl);
            registered.add(runtimeName);
        }
        lastRegisteredColliderNames = registered;
    }

    private Geometry createColliderGeometry(WeaponColliderDefinition collider, String runtimeName) {
        Geometry geometry = collider.isSphere()
                ? new Geometry(runtimeName, new Sphere(16, 16, 0.5f))
                : new Geometry(runtimeName, new Box(0.5f, 0.5f, 0.5f));
        Material material = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", com.jme3.math.ColorRGBA.White.mult(0f));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        geometry.setMaterial(material);
        return geometry;
    }

    private GhostControl createColliderGhost(WeaponColliderDefinition collider) {
        WeaponAttachmentTransform transform = collider.getTransform();
        float sx = Math.max(0.001f, (float) transform.getScaleX());
        float sy = Math.max(0.001f, (float) transform.getScaleY());
        float sz = Math.max(0.001f, (float) transform.getScaleZ());
        if (collider.isSphere()) {
            return new GhostControl(new SphereCollisionShape(Math.max(sx, Math.max(sy, sz)) * 0.5f));
        }
        return new GhostControl(new BoxCollisionShape(new Vector3f(sx * 0.5f, sy * 0.5f, sz * 0.5f)));
    }

    private EntityInstBase createColliderInst(WeaponColliderDefinition collider, SceneMaxScope ownerScope) {
        if (collider.isSphere()) {
            SphereVariableDef varDef = new SphereVariableDef();
            varDef.varName = collider.getName().trim();
            varDef.resName = "sphere";
            varDef.isCollider = true;
            varDef.visible = false;
            return new SphereInst(varDef, ownerScope);
        }
        BoxVariableDef varDef = new BoxVariableDef();
        varDef.varName = collider.getName().trim();
        varDef.resName = "box";
        varDef.isCollider = true;
        varDef.visible = false;
        return new BoxInst(varDef, ownerScope);
    }
}
