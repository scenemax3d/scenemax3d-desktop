package com.scenemax.designer.weapon;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.Bone;
import com.jme3.animation.LoopMode;
import com.jme3.animation.SkeletonControl;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioContext;
import com.jme3.audio.AudioNode;
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
import com.jme3.scene.shape.Line;
import com.jme3.texture.Texture;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;
import com.scenemax.designer.gizmo.GizmoManager;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.gizmo.RotateGizmo;
import com.scenemax.designer.gizmo.TranslateGizmo;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceAudio;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.common.weapons.AttackProfile;
import com.scenemaxeng.common.weapons.ProjectileDefinition;
import com.scenemaxeng.common.weapons.WeaponEffectSet;
import com.scenemaxeng.common.weapons.WeaponAttachmentTransform;
import com.scenemaxeng.common.weapons.WeaponAnimationSet;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.projector.AppModel;
import com.scenemaxeng.projector.SceneMaxApp;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final Node attackPreviewRoot = new Node("WeaponPreviewAttackDebug");
    private final DesignerEntity weaponEntity = new DesignerEntity("Preview Weapon", DesignerEntityType.MODEL);

    private TranslateGizmo translateGizmo;
    private RotateGizmo rotateGizmo;
    private GizmoManager gizmoManager;
    private AssetsMapping previewAssets;
    private WeaponDefinition weaponDefinition;
    private String holderModelId;
    private int selectedAttackIndex;
    private Spatial holderSpatial;
    private Spatial weaponSpatial;
    private AppModel holderAppModel;
    private AnimChannel legacyAnimationChannel;
    private Spatial attackRangeLine;
    private Geometry targetDummy;
    private Spatial projectilePreview;
    private Geometry muzzleMarker;
    private Geometry impactMarker;
    private PointLight cameraLight;
    private Consumer<WeaponAttachmentTransform> transformChangedCallback;
    private Consumer<List<String>> attachmentPointsChangedCallback;
    private Consumer<String> statusChangedCallback;

    private float cameraDistance = 6f;
    private float yaw = (float) Math.toRadians(35);
    private float pitch = (float) Math.toRadians(18);
    private boolean orbiting;
    private boolean attackPreviewActive;
    private float attackPreviewTime;
    private AttackProfile attackPreviewProfile;
    private float attackPreviewDuration;
    private boolean attackSoundPlayed;
    private boolean impactFeedbackPlayed;
    private float feedbackMarkerTime;
    private final List<AudioNode> previewAudioNodes = new ArrayList<>();
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
        rootNode.attachChild(attackPreviewRoot);

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
        reloadPreview();
        updateCamera(currentCenter());
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
        updateAttackPreview(tpf);
        updateFeedbackMarkers(tpf);
    }

    void setTransformChangedCallback(Consumer<WeaponAttachmentTransform> callback) {
        this.transformChangedCallback = callback;
    }

    void setAttachmentPointsChangedCallback(Consumer<List<String>> callback) {
        this.attachmentPointsChangedCallback = callback;
    }

    void setStatusChangedCallback(Consumer<String> callback) {
        this.statusChangedCallback = callback;
    }

    void setWeaponDefinition(WeaponDefinition definition) {
        enqueue(() -> {
            this.weaponDefinition = cloneWeapon(definition);
            clampSelectedAttackIndex();
            reloadPreview();
            return null;
        });
    }

    void setSelectedAttackIndex(int selectedAttackIndex) {
        enqueue(() -> {
            this.selectedAttackIndex = Math.max(0, selectedAttackIndex);
            clampSelectedAttackIndex();
            return null;
        });
    }

    void setAttachmentPoint(String attachmentPoint) {
        enqueue(() -> {
            if (weaponDefinition != null) {
                weaponDefinition.setDefaultAttachmentPoint(attachmentPoint == null ? "" : attachmentPoint);
                reloadPreview();
            }
            return null;
        });
    }

    void setHolderModelId(String holderModelId) {
        enqueue(() -> {
            this.holderModelId = holderModelId;
            reloadPreview();
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

    void previewSelectedAttack() {
        enqueue(() -> {
            attackPreviewProfile = selectedAttack();
            attackPreviewTime = 0f;
            attackPreviewDuration = previewDurationForAttack(attackPreviewProfile);
            attackPreviewActive = attackPreviewProfile != null;
            attackSoundPlayed = false;
            impactFeedbackPlayed = false;
            playAnimation(attackAnimationName(attackPreviewProfile), false);
            playPrimaryStartFeedback();
            rebuildAttackDebug(false);
            return null;
        });
    }

    void previewReload() {
        enqueue(() -> {
            stopAttackPreviewNow();
            playAnimation(reloadAnimationName(), false);
            playPreviewSound(effectSet().reloadSound, "reload sound");
            showMarker(weaponTransformNode.getWorldTranslation(), new ColorRGBA(0.25f, 0.7f, 1f, 1f), true);
            return null;
        });
    }

    void stopAttackPreview() {
        enqueue(() -> {
            stopAttackPreviewNow();
            updateStatus("Preview stopped.");
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

    private void reloadPreview() {
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
        clearPreviewAudio();
        stopAttackPreviewNow();

        loadHolderModel();
        Node attachmentNode = resolveAttachmentNode();
        loadWeaponModel(attachmentNode);
        applyAttachmentTransform(weaponDefinition != null ? weaponDefinition.getAttachmentTransform() : null);

        weaponEntity.setSceneNode(weaponTransformNode);
        publishAttachmentPoints();
        if (gizmoManager != null) {
            gizmoManager.onSelectionChanged(weaponEntity);
            gizmoManager.updateGizmoPosition();
        }
        fitCamera();
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
            holderWrapper.attachChild(holderSpatial);
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
        String attachmentPoint = weaponDefinition.getDefaultAttachmentPoint();
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

    private List<String> listAttachmentPoints() {
        Set<String> names = new LinkedHashSet<>();
        collectAttachmentPoints(holderSpatial, names);
        return new ArrayList<>(names);
    }

    private void clampSelectedAttackIndex() {
        if (weaponDefinition == null || weaponDefinition.getAttackProfiles().isEmpty()) {
            selectedAttackIndex = 0;
            return;
        }
        selectedAttackIndex = Math.max(0, Math.min(selectedAttackIndex, weaponDefinition.getAttackProfiles().size() - 1));
    }

    private AttackProfile selectedAttack() {
        if (weaponDefinition == null || weaponDefinition.getAttackProfiles().isEmpty()) {
            return null;
        }
        clampSelectedAttackIndex();
        return weaponDefinition.getAttackProfiles().get(selectedAttackIndex);
    }

    private String attackAnimationName(AttackProfile attack) {
        return firstNonEmpty(
                attack == null ? "" : attack.getAttackAnimation(),
                attack == null ? "" : attack.getAnimationEventBinding());
    }

    private String reloadAnimationName() {
        WeaponAnimationSet animations = weaponDefinition == null ? null : weaponDefinition.getAnimationSet();
        return animations == null ? "" : safeString(animations.reloadAnimation);
    }

    private WeaponEffectSet effectSet() {
        return weaponDefinition == null || weaponDefinition.getEffectSet() == null
                ? new WeaponEffectSet()
                : weaponDefinition.getEffectSet();
    }

    private String attackSoundName(AttackProfile attack) {
        return firstNonEmpty(
                attack == null ? "" : attack.getAttackSound(),
                attack == null ? "" : attack.getSoundEventBinding());
    }

    private String impactSoundName(AttackProfile attack) {
        return firstNonEmpty(attack == null ? "" : attack.getImpactSound());
    }

    private String muzzleFlashName(AttackProfile attack) {
        return firstNonEmpty(attack == null ? "" : attack.getMuzzleFlashEffect());
    }

    private String meleeTrailName(AttackProfile attack) {
        return firstNonEmpty(attack == null ? "" : attack.getMeleeTrailEffect());
    }

    private String impactEffectName(AttackProfile attack) {
        return firstNonEmpty(
                attack == null ? "" : attack.getImpactEffect(),
                attack == null ? "" : attack.getEffectEventBinding());
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String safe = safeString(value);
            if (!safe.isEmpty()) {
                return safe;
            }
        }
        return "";
    }

    private void playAnimation(String animationName, boolean loop) {
        String name = safeString(animationName);
        if (name.isEmpty()) {
            updateStatus("No animation assigned for this preview action.");
            return;
        }
        if (holderSpatial == null) {
            updateStatus("No preview character model is loaded.");
            return;
        }

        double speed = 1.0;
        WeaponAnimationSet animations = weaponDefinition == null ? null : weaponDefinition.getAnimationSet();
        if (animations != null && animations.animationSpeedMultiplier > 0) {
            speed = animations.animationSpeedMultiplier;
        }

        AnimComposer composer = findAnimComposer(holderSpatial);
        if (composer != null) {
            if (composer.hasAction(name) || composer.hasAnimClip(name)) {
                composer.setGlobalSpeed((float) speed);
                composer.setCurrentAction(name);
                updateStatus("Previewing animation: " + name);
                return;
            }
        }

        AnimControl control = findAnimControl(holderSpatial);
        if (control != null && control.getAnimationNames().contains(name)) {
            if (legacyAnimationChannel == null) {
                legacyAnimationChannel = control.createChannel();
            }
            legacyAnimationChannel.setAnim(name);
            legacyAnimationChannel.setLoopMode(loop ? LoopMode.Loop : LoopMode.DontLoop);
            legacyAnimationChannel.setSpeed((float) speed);
            updateStatus("Previewing animation: " + name);
            return;
        }

        if (holderAppModel != null && previewAssets != null && previewAssets.getAnimationsIndex().containsKey(name.toLowerCase(Locale.ROOT))) {
            boolean attached = holderAppModel.attachExternalAnimation(assetManager, previewAssets, name);
            AnimComposer attachedComposer = holderAppModel.getAnimComposer();
            if (attached && attachedComposer != null && attachedComposer.hasAction(name)) {
                attachedComposer.setGlobalSpeed((float) speed);
                attachedComposer.setCurrentAction(name);
                updateStatus("Previewing animation: " + name);
                return;
            }
        }

        updateStatus("Animation not found on selected model: " + name);
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

    private void updateStatus(String status) {
        if (statusChangedCallback != null) {
            statusChangedCallback.accept(status);
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private void stopAttackPreviewNow() {
        attackPreviewActive = false;
        attackPreviewTime = 0f;
        attackPreviewProfile = null;
        attackPreviewDuration = 0f;
        attackSoundPlayed = false;
        impactFeedbackPlayed = false;
        feedbackMarkerTime = 0f;
        clearPreviewAudio();
        stopPreviewAnimation();
        weaponVisualNode.setLocalTranslation(Vector3f.ZERO);
        weaponVisualNode.setLocalRotation(Quaternion.IDENTITY);
        weaponVisualNode.setLocalScale(Vector3f.UNIT_XYZ);
        attackPreviewRoot.detachAllChildren();
        attackRangeLine = null;
        targetDummy = null;
        projectilePreview = null;
        muzzleMarker = null;
        impactMarker = null;
    }

    private void updateAttackPreview(float tpf) {
        if (!attackPreviewActive || attackPreviewProfile == null) {
            return;
        }
        attackPreviewTime += tpf;
        float startup = Math.max(0.01f, (float) attackPreviewProfile.getStartupTime());
        float active = Math.max(0.01f, (float) attackPreviewProfile.getActiveTime());
        float recovery = Math.max(0.01f, (float) attackPreviewProfile.getRecoveryTime());
        float total = Math.max(startup + active + recovery, attackPreviewDuration);
        float normalized = FastMath.clamp(attackPreviewTime / total, 0f, 1f);
        boolean hitWindow = attackPreviewTime >= startup && attackPreviewTime <= startup + active;
        if (!attackSoundPlayed) {
            playPrimaryStartFeedback();
        }
        if (hitWindow && !impactFeedbackPlayed) {
            impactFeedbackPlayed = true;
            playPreviewSound(impactSoundName(attackPreviewProfile), "impact sound");
            showMarker(attackTargetPosition(), new ColorRGBA(1f, 0.18f, 0.08f, 1f), false);
            String impact = impactEffectName(attackPreviewProfile);
            if (!impact.isEmpty()) {
                updateStatus("Impact effect marker: " + impact);
            }
        }
        if (isProjectilePreviewAttack(attackPreviewProfile)) {
            updateProjectilePreview(normalized);
        } else {
            float swing = (-35f + 85f * normalized) * FastMath.DEG_TO_RAD;
            float lift = FastMath.sin(normalized * FastMath.PI) * 0.08f;
            weaponVisualNode.setLocalRotation(new Quaternion().fromAngles(0f, swing * 0.25f, swing));
            weaponVisualNode.setLocalTranslation(0f, lift, 0f);
        }
        rebuildAttackDebug(hitWindow);
        if (attackPreviewTime >= total) {
            stopAttackPreviewNow();
        }
    }

    private float previewDurationForAttack(AttackProfile attack) {
        float startup = attack == null ? 0.01f : Math.max(0.01f, (float) attack.getStartupTime());
        float active = attack == null ? 0.01f : Math.max(0.01f, (float) attack.getActiveTime());
        float recovery = attack == null ? 0.01f : Math.max(0.01f, (float) attack.getRecoveryTime());
        if (isProjectilePreviewAttack(attack)) {
            ProjectileDefinition projectile = projectileDefinitionForAttack(attack);
            float range = attack == null ? 1.5f : Math.max(0.25f, (float) attack.getRange());
            float speed = projectile == null ? 0f : Math.max(0f, (float) projectile.getSpeed());
            float travelTime = speed > 0f ? range / speed : 0f;
            return Math.max(1.75f, Math.max(startup + active + recovery, travelTime));
        }
        return startup + active + recovery;
    }

    private void updateFeedbackMarkers(float tpf) {
        if (feedbackMarkerTime <= 0f) {
            return;
        }
        feedbackMarkerTime -= tpf;
        if (feedbackMarkerTime <= 0f) {
            if (muzzleMarker != null) {
                muzzleMarker.removeFromParent();
                muzzleMarker = null;
            }
            if (impactMarker != null) {
                impactMarker.removeFromParent();
                impactMarker = null;
            }
        }
    }

    private void playPrimaryStartFeedback() {
        if (attackSoundPlayed) {
            return;
        }
        attackSoundPlayed = true;
        playPreviewSound(attackSoundName(attackPreviewProfile), "attack sound");
        showMarker(weaponTransformNode.getWorldTranslation(), new ColorRGBA(1f, 0.82f, 0.16f, 1f), true);
        String flash = muzzleFlashName(attackPreviewProfile);
        String trail = meleeTrailName(attackPreviewProfile);
        if (!flash.isEmpty()) {
            updateStatus("Muzzle flash marker: " + flash);
        } else if (!trail.isEmpty()) {
            updateStatus("Melee trail marker: " + trail);
        }
    }

    private void updateProjectilePreview(float normalized) {
        ensureAttackDebugGeometry(false);
        Vector3f target = projectileTargetPosition();
        Vector3f pos = projectilePositionAt(FastMath.clamp(normalized, 0f, 1f));
        if (projectilePreview != null) {
            projectilePreview.setLocalTranslation(pos);
            if (pos.distanceSquared(target) > 0.0001f) {
                projectilePreview.lookAt(target, Vector3f.UNIT_Y);
            }
        }
    }

    private void rebuildAttackDebug(boolean hitWindow) {
        ensureAttackDebugGeometry(hitWindow);
        Vector3f source = isProjectilePreviewAttack(attackPreviewProfile)
                ? projectileStartPosition()
                : weaponTransformNode.getWorldTranslation();
        Vector3f target = isProjectilePreviewAttack(attackPreviewProfile)
                ? projectileTargetPosition()
                : attackTargetPosition();
        if (attackRangeLine != null) {
            attackRangeLine.removeFromParent();
        }
        ColorRGBA lineColor = hitWindow ? new ColorRGBA(1f, 0.62f, 0.15f, 1f) : new ColorRGBA(0.55f, 0.7f, 1f, 1f);
        attackRangeLine = isProjectilePreviewAttack(attackPreviewProfile)
                ? createProjectilePath("WeaponPreviewProjectilePath", lineColor)
                : createLine("WeaponPreviewAttackRange", source, target, lineColor);
        attackPreviewRoot.attachChild(attackRangeLine);
        if (targetDummy != null) {
            targetDummy.setLocalTranslation(target);
            targetDummy.setMaterial(colorMaterial(hitWindow
                    ? new ColorRGBA(1f, 0.25f, 0.12f, 1f)
                    : new ColorRGBA(0.38f, 0.42f, 0.48f, 1f)));
        }
    }

    private void ensureAttackDebugGeometry(boolean hitWindow) {
        if (targetDummy == null) {
            targetDummy = new Geometry("WeaponPreviewTargetDummy", new Box(0.25f, 0.55f, 0.25f));
            targetDummy.setMaterial(colorMaterial(new ColorRGBA(0.38f, 0.42f, 0.48f, 1f)));
            attackPreviewRoot.attachChild(targetDummy);
        }
        String type = attackPreviewProfile == null || attackPreviewProfile.getAttackType() == null
                ? ""
                : attackPreviewProfile.getAttackType();
        if (isProjectilePreviewAttack(attackPreviewProfile) && !"hitscan".equalsIgnoreCase(type) && projectilePreview == null) {
            projectilePreview = createProjectilePreviewSpatial();
            attackPreviewRoot.attachChild(projectilePreview);
        } else if ("hitscan".equalsIgnoreCase(type) && projectilePreview == null) {
            projectilePreview = createProjectileFallbackSpatial("WeaponPreviewHitscanMarker",
                    new ColorRGBA(1f, 0.82f, 0.22f, 1f));
            attackPreviewRoot.attachChild(projectilePreview);
        }
    }

    private Spatial createProjectilePreviewSpatial() {
        ProjectileDefinition projectileDefinition = projectileDefinitionForAttack(attackPreviewProfile);
        if (projectileDefinition != null) {
            ResourceSetup resource = resolveModel(projectileDefinition.getModelAssetId());
            if (resource != null) {
                try {
                    Spatial projectile = assetManager.loadModel(resource.path);
                    projectile.setName("WeaponPreviewProjectileModel");
                    projectile.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                    applyReadablePreviewMaterials(projectile);
                    projectile = wrapProjectileForTravel(projectile, projectileDefinition);
                    updateStatus("Previewing projectile model: " + projectileDefinition.getName());
                    return projectile;
                } catch (Exception ex) {
                    updateStatus("Projectile model failed to load: " + projectileDefinition.getModelAssetId());
                }
            } else if (!safeString(projectileDefinition.getModelAssetId()).isEmpty()) {
                updateStatus("Projectile model asset not found: " + projectileDefinition.getModelAssetId());
            }
        } else {
            updateStatus("No projectile definition bound to this attack.");
        }
        return createProjectileFallbackSpatial("WeaponPreviewProjectileFallback",
                new ColorRGBA(1f, 0.82f, 0.22f, 1f));
    }

    private Spatial createProjectileFallbackSpatial(String name, ColorRGBA color) {
        Geometry fallback = new Geometry(name, new Box(0.06f, 0.06f, 0.16f));
        fallback.setMaterial(colorMaterial(color));
        return fallback;
    }

    private Spatial wrapProjectileForTravel(Spatial projectile, ProjectileDefinition projectileDefinition) {
        Node root = new Node("WeaponPreviewProjectileRoot");
        root.attachChild(projectile);
        root.setLocalScale(
                Math.max(0.001f, (float) projectileDefinition.getScaleX()),
                Math.max(0.001f, (float) projectileDefinition.getScaleY()),
                Math.max(0.001f, (float) projectileDefinition.getScaleZ()));
        return root;
    }

    private Vector3f projectileStartPosition() {
        Vector3f start = weaponTransformNode.getWorldTranslation().clone();
        AttackProfile attack = attackPreviewProfile;
        Vector3f localOffset = new Vector3f(
                attack == null ? 0f : (float) attack.getProjectileLaunchOffsetX(),
                attack == null ? 0f : (float) attack.getProjectileLaunchOffsetY(),
                attack == null ? 0.35f : (float) attack.getProjectileLaunchOffsetZ());
        return start.add(weaponTransformNode.getWorldRotation().mult(localOffset));
    }

    private Vector3f projectileTargetPosition() {
        float range = attackPreviewProfile == null ? 1.5f : Math.max(0.25f, (float) attackPreviewProfile.getRange());
        return projectileStartPosition().add(holderForward().mult(range));
    }

    private Vector3f projectilePositionAt(float normalized) {
        Vector3f start = projectileStartPosition();
        Vector3f forward = holderForward();
        float range = attackPreviewProfile == null ? 1.5f : Math.max(0.25f, (float) attackPreviewProfile.getRange());
        Vector3f position = start.add(forward.mult(range * normalized));
        ProjectileDefinition projectile = projectileDefinitionForAttack(attackPreviewProfile);
        if (projectile != null && projectile.getGravityScale() != 0) {
            float duration = Math.max(0.01f, projectileTravelDuration());
            float time = duration * normalized;
            position.y -= 0.5f * 9.81f * (float) projectile.getGravityScale() * time * time;
        }
        return position;
    }

    private float projectileTravelDuration() {
        ProjectileDefinition projectile = projectileDefinitionForAttack(attackPreviewProfile);
        float range = attackPreviewProfile == null ? 1.5f : Math.max(0.25f, (float) attackPreviewProfile.getRange());
        float speed = projectile == null ? 0f : Math.max(0f, (float) projectile.getSpeed());
        return speed > 0f ? range / speed : Math.max(0.01f, attackPreviewDuration);
    }

    private Vector3f holderForward() {
        Vector3f forward = holderWrapper.getWorldRotation().mult(Vector3f.UNIT_Z).normalizeLocal();
        if (forward.lengthSquared() < 0.0001f) {
            return Vector3f.UNIT_Z.clone();
        }
        return forward;
    }

    private boolean isProjectilePreviewAttack(AttackProfile attack) {
        if (attack == null) {
            return false;
        }
        String type = safeString(attack.getAttackType());
        if ("projectile".equalsIgnoreCase(type) || "hitscan".equalsIgnoreCase(type)) {
            return true;
        }
        return !safeString(attack.getProjectileDefinitionId()).isEmpty() || projectileDefinitionForAttack(attack) != null;
    }

    private ProjectileDefinition projectileDefinitionForAttack(AttackProfile attack) {
        if (weaponDefinition == null || attack == null) {
            return null;
        }
        String projectileId = safeString(attack.getProjectileDefinitionId());
        if (projectileId.isEmpty()) {
            projectileId = safeString(attack.getId());
        }
        if (projectileId.isEmpty()) {
            return null;
        }
        for (ProjectileDefinition projectile : weaponDefinition.getProjectileDefinitions()) {
            if (projectile != null && projectile.getId() != null
                    && projectile.getId().trim().equalsIgnoreCase(projectileId)) {
                return projectile;
            }
        }
        return null;
    }

    private Vector3f attackTargetPosition() {
        float range = attackPreviewProfile == null ? 1.5f : Math.max(0.25f, (float) attackPreviewProfile.getRange());
        Vector3f origin = holderWrapper.getWorldTranslation().clone();
        if (origin.lengthSquared() < 0.0001f) {
            origin = weaponTransformNode.getWorldTranslation().clone();
        }
        Vector3f forward = holderForward();
        return origin.add(forward.mult(range)).add(0f, 0.55f, 0f);
    }

    private Spatial createProjectilePath(String name, ColorRGBA color) {
        Node path = new Node(name);
        Vector3f previous = projectilePositionAt(0f);
        int segments = 16;
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            Vector3f next = projectilePositionAt(t);
            path.attachChild(createLine(name + "_" + i, previous, next, color));
            previous = next;
        }
        return path;
    }

    private Geometry createLine(String name, Vector3f start, Vector3f end, ColorRGBA color) {
        Line line = new Line(start, end);
        line.setLineWidth(3f);
        Geometry geometry = new Geometry(name, line);
        Material material = colorMaterial(color);
        material.getAdditionalRenderState().setLineWidth(3f);
        geometry.setMaterial(material);
        return geometry;
    }

    private void showMarker(Vector3f position, ColorRGBA color, boolean muzzle) {
        Geometry marker = new Geometry(muzzle ? "WeaponPreviewMuzzleMarker" : "WeaponPreviewImpactMarker",
                new Box(0.11f, 0.11f, 0.11f));
        marker.setMaterial(colorMaterial(color));
        marker.setLocalTranslation(position == null ? Vector3f.ZERO : position);
        attackPreviewRoot.attachChild(marker);
        if (muzzle) {
            if (muzzleMarker != null) {
                muzzleMarker.removeFromParent();
            }
            muzzleMarker = marker;
        } else {
            if (impactMarker != null) {
                impactMarker.removeFromParent();
            }
            impactMarker = marker;
        }
        feedbackMarkerTime = 0.35f;
    }

    private void playPreviewSound(String soundName, String label) {
        String key = safeString(soundName);
        if (key.isEmpty() || previewAssets == null) {
            return;
        }
        ResourceAudio audio = previewAssets.getAudioIndex().get(key.toLowerCase(Locale.ROOT));
        if (audio == null) {
            updateStatus("Missing " + label + ": " + key);
            return;
        }
        try {
            if (AudioContext.getAudioRenderer() == null && audioRenderer != null) {
                AudioContext.setAudioRenderer(audioRenderer);
            }
            AudioNode node = new AudioNode(assetManager, audio.path, audio.dataType);
            node.setPositional(false);
            node.setLooping(false);
            node.setVolume(1f);
            rootNode.attachChild(node);
            node.playInstance();
            previewAudioNodes.add(node);
        } catch (Exception ex) {
            updateStatus("Sound preview unavailable: " + key);
        }
    }

    private void clearPreviewAudio() {
        for (AudioNode node : previewAudioNodes) {
            if (node == null) {
                continue;
            }
            try {
                node.stop();
                node.removeFromParent();
            } catch (Exception ignored) {
            }
        }
        previewAudioNodes.clear();
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
        }
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
        cameraDistance = Math.max(2.3f, radius * 2.7f);
        updateCamera(center);
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
