package com.scenemax.designer.weapon;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.Bone;
import com.jme3.animation.LoopMode;
import com.jme3.animation.SkeletonControl;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.LightProbe;
import com.jme3.light.PointLight;
import com.jme3.material.MatParam;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Texture;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;
import com.scenemax.designer.gizmo.GizmoManager;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.gizmo.RotateGizmo;
import com.scenemax.designer.gizmo.TranslateGizmo;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.common.weapons.WeaponAttachmentTransform;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.common.weapons.WeaponPostureDefinition;
import com.scenemaxeng.projector.AppModel;
import com.scenemaxeng.projector.SceneMaxApp;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

class WeaponPreviewApp extends SceneMaxApp {
    private static final String ACTION_LEFT = "WeaponPreviewLeft";
    private static final String ACTION_SCROLL_UP = "WeaponPreviewScrollUp";
    private static final String ACTION_SCROLL_DOWN = "WeaponPreviewScrollDown";

    private final File resourcesRoot;
    private final File projectRoot;
    private final Node previewRoot = new Node("WeaponPreviewRoot");
    private final Node holderWrapper = new Node("WeaponPreviewHolder");
    private final Node fallbackAttachmentNode = new Node("WeaponPreviewFallbackAttachment");
    private final Node weaponTransformNode = new Node("WeaponPreviewWeaponTransform");
    private final Node weaponVisualNode = new Node("WeaponPreviewWeaponVisual");
    private final DesignerEntity weaponEntity = new DesignerEntity("Preview Weapon", DesignerEntityType.MODEL);

    private TranslateGizmo translateGizmo;
    private RotateGizmo rotateGizmo;
    private GizmoManager gizmoManager;
    private AssetsMapping previewAssets;
    private WeaponDefinition weaponDefinition;
    private String weaponDefinitionSnapshot = "";
    private String holderModelId;
    private int selectedPostureIndex;
    private Spatial holderSpatial;
    private Spatial weaponSpatial;
    private AppModel holderAppModel;
    private AnimChannel legacyAnimationChannel;
    private String previewAnimationName = "";
    private PointLight cameraLight;
    private Consumer<WeaponAttachmentTransform> transformChangedCallback;
    private Consumer<List<String>> attachmentPointsChangedCallback;
    private Consumer<List<String>> animationNamesChangedCallback;
    private Consumer<String> statusChangedCallback;

    private float cameraDistance = 6f;
    private float yaw = (float) Math.toRadians(35);
    private float pitch = (float) Math.toRadians(18);
    private boolean orbiting;
    private final Vector2f lastMouse = new Vector2f();

    WeaponPreviewApp(File resourcesRoot) {
        this.resourcesRoot = resourcesRoot;
        this.projectRoot = resourcesRoot != null ? resourcesRoot.getParentFile() : null;
    }

    @Override
    public void simpleInitApp() {
        if (projectRoot != null) {
            setWorkingFolder(new File(new File(projectRoot, "scripts"), "_weapon_preview").getAbsolutePath());
        }
        if (resourcesRoot != null && resourcesRoot.isDirectory()) {
            previewAssets = new AssetsMapping(resourcesRoot.getAbsolutePath());
        } else {
            previewAssets = new AssetsMapping();
        }
        assetsMapping = previewAssets;

        tryRegisterLocator(new File("./resources-basic/resources"));
        tryRegisterLocator(new File("./resources"));
        tryRegisterLocator(resourcesRoot);

        viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.055f, 0.065f, 1f));
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(previewRoot);
        previewRoot.attachChild(holderWrapper);
        holderWrapper.attachChild(fallbackAttachmentNode);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(1.35f));
        rootNode.addLight(ambient);

        DirectionalLight key = new DirectionalLight();
        key.setDirection(new Vector3f(-0.45f, -0.75f, -0.35f).normalizeLocal());
        key.setColor(ColorRGBA.White.mult(3.2f));
        rootNode.addLight(key);

        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.6f, -0.3f, 0.55f).normalizeLocal());
        fill.setColor(new ColorRGBA(0.75f, 0.86f, 1f, 1f).mult(1.75f));
        rootNode.addLight(fill);

        DirectionalLight rim = new DirectionalLight();
        rim.setDirection(new Vector3f(0.2f, -0.2f, 1f).normalizeLocal());
        rim.setColor(new ColorRGBA(0.9f, 0.95f, 1f, 1f).mult(1.3f));
        rootNode.addLight(rim);

        cameraLight = new PointLight();
        cameraLight.setColor(ColorRGBA.White.mult(2.3f));
        cameraLight.setRadius(100f);
        rootNode.addLight(cameraLight);
        installEnvironmentProbe();

        translateGizmo = new TranslateGizmo(assetManager);
        rotateGizmo = new RotateGizmo(assetManager);
        rootNode.attachChild(translateGizmo);
        rootNode.attachChild(rotateGizmo);
        gizmoManager = new GizmoManager(rootNode, translateGizmo, rotateGizmo);
        gizmoManager.onSelectionChanged(weaponEntity);
        gizmoManager.setDragEndCallback(entity -> publishCurrentTransform());

        weaponEntity.setSceneNode(weaponTransformNode);
        registerInput();
        reloadPreview(true);
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (orbiting && inputManager != null) {
            Vector2f current = inputManager.getCursorPosition();
            float dx = current.x - lastMouse.x;
            float dy = current.y - lastMouse.y;
            lastMouse.set(current);
            yaw -= dx * 0.006f;
            pitch = FastMath.clamp(pitch + dy * 0.006f, (float) Math.toRadians(-70), (float) Math.toRadians(70));
            updateCamera(currentCenter());
        }
        if (gizmoManager != null) {
            if (gizmoManager.isDragging()) {
                gizmoManager.updateDrag(cam, inputManager.getCursorPosition());
                publishCurrentTransform();
            }
            gizmoManager.scaleGizmoToCamera(cam);
            gizmoManager.updateGizmoPosition();
        }
    }

    void setTransformChangedCallback(Consumer<WeaponAttachmentTransform> callback) {
        this.transformChangedCallback = callback;
    }

    void setAttachmentPointsChangedCallback(Consumer<List<String>> callback) {
        this.attachmentPointsChangedCallback = callback;
    }

    void setAnimationNamesChangedCallback(Consumer<List<String>> callback) {
        this.animationNamesChangedCallback = callback;
    }

    void setStatusChangedCallback(Consumer<String> callback) {
        this.statusChangedCallback = callback;
    }

    void setWeaponDefinition(WeaponDefinition definition) {
        enqueue(() -> {
            String snapshot = definition == null ? "" : definition.toJSON().toString();
            if (snapshot.equals(weaponDefinitionSnapshot)) {
                return null;
            }
            WeaponDefinition nextDefinition = cloneWeapon(definition);
            boolean canUpdateInPlace = canApplyPostureUpdateInPlace(nextDefinition);
            boolean shouldFitCamera = weaponDefinition == null
                    || !Objects.equals(safeString(weaponDefinition.getModelAssetId()), safeString(modelAssetId(nextDefinition)));
            weaponDefinitionSnapshot = snapshot;
            this.weaponDefinition = nextDefinition;
            clampSelectedPostureIndex();
            if (canUpdateInPlace) {
                applySelectedPostureInPlace();
            } else {
                reloadPreview(shouldFitCamera);
            }
            return null;
        });
    }

    void setSelectedPostureIndex(int selectedPostureIndex) {
        enqueue(() -> {
            int requestedIndex = Math.max(0, selectedPostureIndex);
            if (this.selectedPostureIndex == requestedIndex) {
                return null;
            }
            this.selectedPostureIndex = requestedIndex;
            if (weaponDefinition != null && requestedIndex < weaponDefinition.getPostures().size()) {
                reloadPreview(false);
            }
            return null;
        });
    }

    void setAttachmentPoint(String attachmentPoint) {
        enqueue(() -> {
            if (weaponDefinition != null) {
                WeaponPostureDefinition posture = selectedPosture();
                if (posture == null) {
                    return null;
                }
                String normalized = attachmentPoint == null ? "" : attachmentPoint.trim();
                if (normalized.equals(safeString(posture.getAttachmentPoint()))) {
                    return null;
                }
                posture.setAttachmentPoint(normalized);
                weaponDefinitionSnapshot = weaponDefinition.toJSON().toString();
                reloadPreview(false);
            }
            return null;
        });
    }

    void setHolderModelId(String holderModelId) {
        enqueue(() -> {
            String normalized = holderModelId == null || holderModelId.trim().isEmpty() ? null : holderModelId.trim();
            if (Objects.equals(this.holderModelId, normalized)) {
                return null;
            }
            this.holderModelId = normalized;
            reloadPreview(true);
            return null;
        });
    }

    void setPreviewAnimation(String animationName) {
        enqueue(() -> {
            this.previewAnimationName = animationName == null ? "" : animationName.trim();
            playPreviewAnimation();
            return null;
        });
    }

    void setGizmoMode(GizmoMode mode) {
        enqueue(() -> {
            if (gizmoManager != null) {
                gizmoManager.setMode(mode);
            }
            return null;
        });
    }

    void resetCamera() {
        enqueue(() -> {
            fitCamera();
            return null;
        });
    }

    void nudgeOffset(float x, float y, float z) {
        enqueue(() -> {
            weaponTransformNode.move(x, y, z);
            publishCurrentTransform();
            return null;
        });
    }

    void nudgeRotation(float xDeg, float yDeg, float zDeg) {
        enqueue(() -> {
            Quaternion delta = new Quaternion().fromAngles(
                    xDeg * FastMath.DEG_TO_RAD,
                    yDeg * FastMath.DEG_TO_RAD,
                    zDeg * FastMath.DEG_TO_RAD);
            weaponTransformNode.setLocalRotation(delta.mult(weaponTransformNode.getLocalRotation()));
            publishCurrentTransform();
            return null;
        });
    }

    void scaleWeapon(float factor) {
        enqueue(() -> {
            Vector3f scale = weaponTransformNode.getLocalScale();
            float safeFactor = Math.max(0.001f, factor);
            weaponTransformNode.setLocalScale(
                    Math.max(0.001f, scale.x * safeFactor),
                    Math.max(0.001f, scale.y * safeFactor),
                    Math.max(0.001f, scale.z * safeFactor));
            publishCurrentTransform();
            return null;
        });
    }

    void nudgeScale(float x, float y, float z) {
        enqueue(() -> {
            Vector3f scale = weaponTransformNode.getLocalScale();
            weaponTransformNode.setLocalScale(
                    Math.max(0.001f, scale.x + x),
                    Math.max(0.001f, scale.y + y),
                    Math.max(0.001f, scale.z + z));
            publishCurrentTransform();
            return null;
        });
    }

    private void reloadPreview(boolean fitCamera) {
        holderWrapper.detachAllChildren();
        holderWrapper.setLocalTranslation(Vector3f.ZERO);
        holderWrapper.setLocalRotation(Quaternion.IDENTITY);
        holderWrapper.setLocalScale(Vector3f.UNIT_XYZ);
        holderWrapper.attachChild(fallbackAttachmentNode);
        fallbackAttachmentNode.detachAllChildren();
        weaponTransformNode.detachAllChildren();
        weaponVisualNode.detachAllChildren();
        weaponVisualNode.setLocalTranslation(Vector3f.ZERO);
        weaponVisualNode.setLocalRotation(Quaternion.IDENTITY);
        weaponVisualNode.setLocalScale(Vector3f.UNIT_XYZ);
        weaponTransformNode.attachChild(weaponVisualNode);
        holderSpatial = null;
        weaponSpatial = null;
        holderAppModel = null;
        legacyAnimationChannel = null;

        loadHolderModel();
        Node attachmentNode = resolveAttachmentNode();
        loadWeaponModel(attachmentNode);
        WeaponPostureDefinition posture = selectedPosture();
        applyAttachmentTransform(posture == null ? null : posture.getTransform());

        weaponEntity.setSceneNode(weaponTransformNode);
        publishAttachmentPoints();
        if (gizmoManager != null) {
            gizmoManager.onSelectionChanged(weaponEntity);
            gizmoManager.updateGizmoPosition();
        }
        publishAnimationNames();
        playPreviewAnimation();
        if (fitCamera) {
            fitCamera();
        } else {
            updateCamera(currentCenter());
        }
    }

    private boolean canApplyPostureUpdateInPlace(WeaponDefinition nextDefinition) {
        if (weaponDefinition == null || nextDefinition == null) {
            return false;
        }
        if (!Objects.equals(safeString(weaponDefinition.getModelAssetId()), safeString(nextDefinition.getModelAssetId()))) {
            return false;
        }
        WeaponPostureDefinition currentPosture = postureAt(weaponDefinition, selectedPostureIndex);
        WeaponPostureDefinition nextPosture = postureAt(nextDefinition, selectedPostureIndex);
        return currentPosture != null
                && nextPosture != null
                && Objects.equals(safeString(currentPosture.getAttachmentPoint()), safeString(nextPosture.getAttachmentPoint()));
    }

    private void applySelectedPostureInPlace() {
        WeaponPostureDefinition posture = selectedPosture();
        applyAttachmentTransform(posture == null ? null : posture.getTransform());
        if (gizmoManager != null) {
            gizmoManager.updateGizmoPosition();
        }
    }

    private void loadHolderModel() {
        ResourceSetup resource = resolveModel(holderModelId);
        if (resource == null) {
            Geometry fallback = new Geometry("WeaponPreviewHolderFallback", new Box(0.45f, 0.9f, 0.25f));
            fallback.setMaterial(colorMaterial(new ColorRGBA(0.28f, 0.32f, 0.36f, 1f)));
            fallback.setLocalTranslation(0f, 0.9f, 0f);
            holderSpatial = fallback;
            holderWrapper.attachChild(holderSpatial);
            fallbackAttachmentNode.setLocalTranslation(0.35f, 1.15f, 0.05f);
            return;
        }

        try {
            holderSpatial = assetManager.loadModel(resource.path);
            holderSpatial.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            applyReadablePreviewMaterials(holderSpatial);
            fallbackAttachmentNode.removeFromParent();
            holderWrapper.attachChild(holderSpatial);
            holderWrapper.attachChild(fallbackAttachmentNode);
            holderAppModel = new AppModel(holderWrapper);
            holderAppModel.resource = resource;
            holderAppModel.skinningControlNode = findSkinningControlNode(holderSpatial);
            holderWrapper.setLocalScale(resource.scaleX, resource.scaleY, resource.scaleZ);
            holderWrapper.setLocalTranslation(resource.localTranslationX, resource.localTranslationY, resource.localTranslationZ);
            holderWrapper.setLocalRotation(new Quaternion().fromAngles(0f, resource.rotateY * FastMath.DEG_TO_RAD, 0f));
            fallbackAttachmentNode.setLocalTranslation(0.35f, 1.15f, 0.05f);
        } catch (Exception ex) {
            ex.printStackTrace();
            holderSpatial = null;
        }
    }

    private Node resolveAttachmentNode() {
        if (weaponDefinition == null || holderSpatial == null) {
            return fallbackAttachmentNode;
        }
        WeaponPostureDefinition posture = selectedPosture();
        String attachmentPoint = posture == null ? "" : posture.getAttachmentPoint();
        if (attachmentPoint == null || attachmentPoint.trim().isEmpty()) {
            return fallbackAttachmentNode;
        }
        Node jointNode = findAttachmentNode(holderSpatial, attachmentPoint.trim());
        return jointNode != null ? jointNode : fallbackAttachmentNode;
    }

    private Node findAttachmentNode(Spatial spatial, String jointName) {
        if (spatial == null || jointName == null || jointName.isBlank()) {
            return null;
        }
        SkinningControl skinning = spatial.getControl(SkinningControl.class);
        if (skinning != null) {
            try {
                Node node = skinning.getAttachmentsNode(jointName);
                if (node != null) {
                    return node;
                }
            } catch (Exception ignored) {
            }
        }
        SkeletonControl skeleton = spatial.getControl(SkeletonControl.class);
        if (skeleton != null) {
            try {
                Node node = skeleton.getAttachmentsNode(jointName);
                if (node != null) {
                    return node;
                }
            } catch (Exception ignored) {
            }
        }
        if (spatial instanceof Node) {
            Node node = (Node) spatial;
            for (Spatial child : node.getChildren()) {
                Node found = findAttachmentNode(child, jointName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void loadWeaponModel(Node attachmentNode) {
        if (attachmentNode == null) {
            attachmentNode = fallbackAttachmentNode;
        }
        attachmentNode.attachChild(weaponTransformNode);

        String modelAssetId = weaponDefinition != null ? weaponDefinition.getModelAssetId() : null;
        ResourceSetup resource = resolveModel(modelAssetId);
        if (resource != null) {
            try {
                weaponSpatial = assetManager.loadModel(resource.path);
                weaponSpatial.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                applyReadablePreviewMaterials(weaponSpatial);
                weaponVisualNode.attachChild(weaponSpatial);
                return;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        Geometry fallback = new Geometry("WeaponPreviewWeaponFallback", new Box(0.08f, 0.08f, 0.65f));
        fallback.setMaterial(colorMaterial(new ColorRGBA(0.66f, 0.56f, 0.36f, 1f)));
        fallback.setLocalTranslation(0f, 0f, 0.55f);
        weaponSpatial = fallback;
        weaponVisualNode.attachChild(weaponSpatial);
    }

    private void applyResourceTransform(Spatial spatial, ResourceSetup resource) {
        if (spatial == null || resource == null) {
            return;
        }
        spatial.setLocalScale(resource.scaleX, resource.scaleY, resource.scaleZ);
        spatial.setLocalTranslation(resource.localTranslationX, resource.localTranslationY, resource.localTranslationZ);
        spatial.setLocalRotation(new Quaternion().fromAngles(0f, resource.rotateY * FastMath.DEG_TO_RAD, 0f));
    }

    private ResourceSetup resolveModel(String modelAssetId) {
        if (modelAssetId == null || modelAssetId.trim().isEmpty()) {
            return null;
        }
        AssetsMapping assets = previewAssets != null ? previewAssets : getAssetsMapping();
        if (assets == null) {
            return null;
        }
        return assets.get3DModelsIndex().get(modelAssetId.trim().toLowerCase(Locale.ROOT));
    }

    private void applyAttachmentTransform(WeaponAttachmentTransform transform) {
        if (transform == null) {
            weaponTransformNode.setLocalTranslation(Vector3f.ZERO);
            weaponTransformNode.setLocalRotation(Quaternion.IDENTITY);
            weaponTransformNode.setLocalScale(Vector3f.UNIT_XYZ);
            return;
        }
        weaponTransformNode.setLocalTranslation(
                (float) transform.getOffsetX(),
                (float) transform.getOffsetY(),
                (float) transform.getOffsetZ());
        weaponTransformNode.setLocalRotation(new Quaternion().fromAngles(
                (float) transform.getRotationX() * FastMath.DEG_TO_RAD,
                (float) transform.getRotationY() * FastMath.DEG_TO_RAD,
                (float) transform.getRotationZ() * FastMath.DEG_TO_RAD));
        weaponTransformNode.setLocalScale(
                (float) transform.getScaleX(),
                (float) transform.getScaleY(),
                (float) transform.getScaleZ());
    }

    private void publishCurrentTransform() {
        if (transformChangedCallback == null) {
            return;
        }
        transformChangedCallback.accept(toAttachmentTransform());
    }

    private void publishAttachmentPoints() {
        if (attachmentPointsChangedCallback == null) {
            return;
        }
        attachmentPointsChangedCallback.accept(listAttachmentPoints());
    }

    private void publishAnimationNames() {
        if (animationNamesChangedCallback == null) {
            return;
        }
        animationNamesChangedCallback.accept(listPreviewAnimations());
    }

    private List<String> listAttachmentPoints() {
        Set<String> names = new LinkedHashSet<>();
        collectAttachmentPoints(holderSpatial, names);
        return new ArrayList<>(names);
    }

    private List<String> listPreviewAnimations() {
        Set<String> names = new LinkedHashSet<>();
        AnimComposer composer = findAnimComposer(holderSpatial);
        if (composer != null) {
            for (AnimClip clip : composer.getAnimClips()) {
                if (clip != null && clip.getName() != null && !clip.getName().isBlank()) {
                    names.add(clip.getName());
                }
            }
        }
        AnimControl control = findAnimControl(holderSpatial);
        if (control != null) {
            names.addAll(control.getAnimationNames());
        }
        if (previewAssets != null) {
            previewAssets.getAnimationsIndex().values().forEach(animation -> {
                if (animation != null && animation.name != null && !animation.name.isBlank()) {
                    names.add(animation.name);
                }
            });
        }
        return new ArrayList<>(names);
    }

    private void clampSelectedPostureIndex() {
        if (weaponDefinition == null || weaponDefinition.getPostures().isEmpty()) {
            selectedPostureIndex = 0;
            return;
        }
        selectedPostureIndex = Math.max(0, Math.min(selectedPostureIndex, weaponDefinition.getPostures().size() - 1));
    }

    private WeaponPostureDefinition selectedPosture() {
        if (weaponDefinition == null || weaponDefinition.getPostures().isEmpty()) {
            return null;
        }
        clampSelectedPostureIndex();
        return weaponDefinition.getPostures().get(selectedPostureIndex);
    }

    private WeaponPostureDefinition postureAt(WeaponDefinition definition, int index) {
        if (definition == null || index < 0 || index >= definition.getPostures().size()) {
            return null;
        }
        return definition.getPostures().get(index);
    }

    private String modelAssetId(WeaponDefinition definition) {
        return definition == null ? "" : definition.getModelAssetId();
    }

    private void updateStatus(String status) {
        if (statusChangedCallback != null) {
            statusChangedCallback.accept(status);
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private void playPreviewAnimation() {
        String name = safeString(previewAnimationName);
        stopPreviewAnimation();
        if (name.isEmpty() || holderSpatial == null) {
            return;
        }

        AnimComposer composer = findAnimComposer(holderSpatial);
        if (composer != null && (composer.hasAction(name) || composer.hasAnimClip(name))) {
            composer.setGlobalSpeed(1f);
            composer.setCurrentAction(name);
            updateStatus("Previewing animation: " + name);
            return;
        }

        AnimControl control = findAnimControl(holderSpatial);
        if (control != null && control.getAnimationNames().contains(name)) {
            legacyAnimationChannel = control.createChannel();
            legacyAnimationChannel.setAnim(name);
            legacyAnimationChannel.setLoopMode(LoopMode.Loop);
            legacyAnimationChannel.setSpeed(1f);
            updateStatus("Previewing animation: " + name);
            return;
        }

        if (holderAppModel != null && previewAssets != null && previewAssets.getAnimationsIndex().containsKey(name.toLowerCase(Locale.ROOT))) {
            boolean attached = holderAppModel.attachExternalAnimation(assetManager, previewAssets, name);
            AnimComposer attachedComposer = holderAppModel.getAnimComposer();
            if (attached && attachedComposer != null && attachedComposer.hasAction(name)) {
                attachedComposer.setGlobalSpeed(1f);
                attachedComposer.setCurrentAction(name);
                updateStatus("Previewing animation: " + name);
                return;
            }
        }

        updateStatus("Animation not found: " + name);
    }

    private void stopPreviewAnimation() {
        AnimComposer composer = findAnimComposer(holderSpatial);
        if (composer != null) {
            try {
                composer.removeCurrentAction();
                composer.reset();
            } catch (Exception ignored) {
            }
        }
        if (holderAppModel != null && holderAppModel.getAnimComposer() != null
                && holderAppModel.getAnimComposer() != composer) {
            try {
                holderAppModel.getAnimComposer().removeCurrentAction();
                holderAppModel.getAnimComposer().reset();
            } catch (Exception ignored) {
            }
        }
        if (legacyAnimationChannel != null) {
            try {
                legacyAnimationChannel.reset(true);
            } catch (Exception ignored) {
            }
            legacyAnimationChannel = null;
        }
    }

    private AnimComposer findAnimComposer(Spatial spatial) {
        if (spatial == null) {
            return null;
        }
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null) {
            return composer;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimComposer found = findAnimComposer(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private AnimControl findAnimControl(Spatial spatial) {
        if (spatial == null) {
            return null;
        }
        AnimControl control = spatial.getControl(AnimControl.class);
        if (control != null) {
            return control;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimControl found = findAnimControl(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Spatial findSkinningControlNode(Spatial spatial) {
        if (spatial == null) {
            return null;
        }
        if (spatial.getControl(SkinningControl.class) != null) {
            return spatial;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                Spatial found = findSkinningControlNode(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void collectAttachmentPoints(Spatial spatial, Set<String> names) {
        if (spatial == null) {
            return;
        }
        SkinningControl skinning = spatial.getControl(SkinningControl.class);
        if (skinning != null && skinning.getArmature() != null) {
            for (Joint joint : skinning.getArmature().getJointList()) {
                if (joint != null && joint.getName() != null && !joint.getName().isBlank()) {
                    names.add(joint.getName());
                }
            }
        }
        SkeletonControl skeleton = spatial.getControl(SkeletonControl.class);
        if (skeleton != null && skeleton.getSkeleton() != null) {
            for (int i = 0; i < skeleton.getSkeleton().getBoneCount(); i++) {
                Bone bone = skeleton.getSkeleton().getBone(i);
                if (bone != null && bone.getName() != null && !bone.getName().isBlank()) {
                    names.add(bone.getName());
                }
            }
        }
        if (spatial instanceof Node) {
            Node node = (Node) spatial;
            for (Spatial child : node.getChildren()) {
                collectAttachmentPoints(child, names);
            }
        }
    }

    private WeaponAttachmentTransform toAttachmentTransform() {
        WeaponAttachmentTransform transform = new WeaponAttachmentTransform();
        Vector3f pos = weaponTransformNode.getLocalTranslation();
        transform.setOffsetX(pos.x);
        transform.setOffsetY(pos.y);
        transform.setOffsetZ(pos.z);
        float[] angles = weaponTransformNode.getLocalRotation().toAngles(null);
        transform.setRotationX(angles[0] * FastMath.RAD_TO_DEG);
        transform.setRotationY(angles[1] * FastMath.RAD_TO_DEG);
        transform.setRotationZ(angles[2] * FastMath.RAD_TO_DEG);
        Vector3f scale = weaponTransformNode.getLocalScale();
        transform.setScaleX(scale.x);
        transform.setScaleY(scale.y);
        transform.setScaleZ(scale.z);
        return transform;
    }

    private void registerInput() {
        inputManager.addMapping(ACTION_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(ACTION_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACTION_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addListener((ActionListener) (name, isPressed, tpf) -> {
            if (!ACTION_LEFT.equals(name)) {
                return;
            }
            if (isPressed) {
                Vector2f click = inputManager.getCursorPosition();
                if (gizmoManager != null && gizmoManager.tryStartDrag(cam, click)) {
                    orbiting = false;
                    return;
                }
                orbiting = true;
                lastMouse.set(click);
            } else {
                orbiting = false;
                if (gizmoManager != null && gizmoManager.isDragging()) {
                    gizmoManager.endDrag();
                }
            }
        }, ACTION_LEFT);
        inputManager.addListener((AnalogListener) (name, value, tpf) -> {
            if (ACTION_SCROLL_UP.equals(name)) {
                cameraDistance = Math.max(0.6f, cameraDistance - value * 16f);
                updateCamera(currentCenter());
            } else if (ACTION_SCROLL_DOWN.equals(name)) {
                cameraDistance = Math.min(160f, cameraDistance + value * 16f);
                updateCamera(currentCenter());
            }
        }, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN);
    }

    private void fitCamera() {
        previewRoot.updateGeometricState();
        BoundingVolume bound = previewRoot.getWorldBound();
        Vector3f center = Vector3f.ZERO.clone();
        float radius = 2f;
        if (bound instanceof BoundingBox) {
            BoundingBox box = (BoundingBox) bound;
            center = box.getCenter().clone();
            radius = Math.max(0.5f, box.getExtent(null).length());
        } else if (bound instanceof BoundingSphere) {
            BoundingSphere sphere = (BoundingSphere) bound;
            center = sphere.getCenter().clone();
            radius = Math.max(0.5f, sphere.getRadius());
        }
        float aspect = canvasAspect();
        float verticalFov = 45f * FastMath.DEG_TO_RAD;
        float horizontalFov = 2f * FastMath.atan(FastMath.tan(verticalFov * 0.5f) * Math.max(0.1f, aspect));
        float fitFov = Math.max(0.1f, Math.min(verticalFov, horizontalFov));
        cameraDistance = Math.max(0.25f, (radius / FastMath.tan(fitFov * 0.5f)) * 1.25f);
        cam.setFrustumPerspective(45f, aspect, 0.01f, Math.max(1000f, cameraDistance * 8f));
        updateCamera(center);
    }

    private float canvasAspect() {
        int width = cam != null && cam.getWidth() > 0 ? cam.getWidth() : 640;
        int height = cam != null && cam.getHeight() > 0 ? cam.getHeight() : 520;
        return height <= 0 ? 1f : Math.max(0.1f, width / (float) height);
    }

    private Vector3f currentCenter() {
        previewRoot.updateGeometricState();
        BoundingVolume bound = previewRoot.getWorldBound();
        return bound == null ? Vector3f.ZERO : bound.getCenter();
    }

    private void updateCamera(Vector3f center) {
        float x = center.x + cameraDistance * FastMath.cos(pitch) * FastMath.sin(yaw);
        float y = center.y + cameraDistance * FastMath.sin(pitch);
        float z = center.z + cameraDistance * FastMath.cos(pitch) * FastMath.cos(yaw);
        Vector3f cameraLocation = new Vector3f(x, y, z);
        cam.setLocation(cameraLocation);
        cam.lookAt(center, Vector3f.UNIT_Y);
        if (cameraLight != null) {
            cameraLight.setPosition(cameraLocation);
            cameraLight.setRadius(Math.max(16f, cameraDistance * 6f));
        }
    }

    private void tryRegisterLocator(File folder) {
        if (folder == null || !folder.isDirectory() || assetManager == null) {
            return;
        }
        try {
            assetManager.registerLocator(folder.getCanonicalPath(), FileLocator.class);
        } catch (Exception ignored) {
        }
    }

    private Material colorMaterial(ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        return material;
    }

    private void applyReadablePreviewMaterials(Spatial spatial) {
        if (spatial == null) {
            return;
        }
        spatial.depthFirstTraversal((SceneGraphVisitor) child -> {
            if (child instanceof Geometry) {
                Geometry geometry = (Geometry) child;
                Material material = createReadableMaterial(geometry.getMaterial());
                if (material != null) {
                    geometry.setMaterial(material);
                }
            }
        });
    }

    private Material createReadableMaterial(Material source) {
        if (source == null) {
            return null;
        }
        Material preview = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        Texture texture = firstTexture(source,
                "BaseColorMap", "DiffuseMap", "ColorMap", "AlbedoMap", "Texture", "DiffuseTexture");
        if (texture != null) {
            preview.setTexture("ColorMap", texture);
            preview.setColor("Color", ColorRGBA.White);
        } else {
            preview.setColor("Color", firstColor(source,
                    new ColorRGBA(0.82f, 0.82f, 0.82f, 1f),
                    "BaseColor", "Diffuse", "Color", "Ambient"));
        }
        if (isTransparent(source)) {
            preview.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            preview.getAdditionalRenderState().setDepthWrite(false);
        }
        return preview;
    }

    private Texture firstTexture(Material material, String... names) {
        for (String name : names) {
            MatParam param = material.getParam(name);
            if (param instanceof MatParamTexture) {
                return ((MatParamTexture) param).getTextureValue();
            }
        }
        for (MatParam param : material.getParams()) {
            if (param instanceof MatParamTexture) {
                return ((MatParamTexture) param).getTextureValue();
            }
        }
        return null;
    }

    private ColorRGBA firstColor(Material material, ColorRGBA fallback, String... names) {
        for (String name : names) {
            MatParam param = material.getParam(name);
            if (param != null && param.getValue() instanceof ColorRGBA) {
                ColorRGBA color = ((ColorRGBA) param.getValue()).clone();
                if (color.a <= 0f) {
                    color.a = 1f;
                }
                return color;
            }
        }
        return fallback.clone();
    }

    private boolean isTransparent(Material material) {
        if (material == null) {
            return false;
        }
        RenderState state = material.getAdditionalRenderState();
        return state != null && state.getBlendMode() != RenderState.BlendMode.Off;
    }

    private void installEnvironmentProbe() {
        String[] probeAssets = {
                "probes/Sky_Cloudy.j3o",
                "probes/corsica_beach.j3o",
                "probes/River_Road.j3o"
        };
        for (String probeAsset : probeAssets) {
            try {
                Spatial spatial = assetManager.loadModel(probeAsset);
                if (spatial instanceof Node) {
                    LightProbe probe = findLightProbe((Node) spatial);
                    if (probe != null) {
                        probe.getArea().setRadius(1000f);
                        probe.setPosition(Vector3f.ZERO);
                        rootNode.addLight(probe);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private LightProbe findLightProbe(Node node) {
        for (Light light : node.getLocalLightList()) {
            if (light instanceof LightProbe) {
                node.removeLight(light);
                return (LightProbe) light;
            }
        }
        for (Spatial child : node.getChildren()) {
            if (child instanceof Node) {
                LightProbe probe = findLightProbe((Node) child);
                if (probe != null) {
                    return probe;
                }
            }
        }
        return null;
    }

    private WeaponDefinition cloneWeapon(WeaponDefinition definition) {
        if (definition == null) {
            return null;
        }
        return WeaponDefinition.fromJSON(definition.toJSON());
    }
}
