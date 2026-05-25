package com.scenemax.designer.ik;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.tween.action.Action;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.debug.Arrow;
import com.jme3.scene.shape.Box;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.shape.Sphere;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;
import com.scenemax.designer.gizmo.GizmoManager;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.gizmo.RotateGizmo;
import com.scenemax.designer.gizmo.TranslateGizmo;
import com.scenemaxeng.common.ik.IKLayerDefinition;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.projector.AppModel;
import com.scenemaxeng.projector.SceneMaxApp;
import com.scenemaxeng.projector.ik.FABRIKIKSolver;
import com.scenemaxeng.projector.ik.FootIKSolver;
import com.scenemaxeng.projector.ik.IKContext;
import com.scenemaxeng.projector.ik.IKSolver;
import com.scenemaxeng.projector.ik.LookAtIKSolver;
import com.scenemaxeng.projector.ik.ThreeBoneIKSolver;
import com.scenemaxeng.projector.ik.TwoBoneIKSolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class IKPreviewApp extends SceneMaxApp {
    static final String PREVIEW_TARGET_ID = "ik_preview_target";

    private static final String ACTION_LEFT = "IKPreviewLeft";
    private static final String ACTION_SCROLL_UP = "IKPreviewScrollUp";
    private static final String ACTION_SCROLL_DOWN = "IKPreviewScrollDown";
    private static final String ACTION_X_POS = "IKPreviewX+";
    private static final String ACTION_X_NEG = "IKPreviewX-";
    private static final String ACTION_Y_POS = "IKPreviewY+";
    private static final String ACTION_Y_NEG = "IKPreviewY-";

    private final File resourcesRoot;
    private final File projectRoot;
    private final Node previewRoot = new Node("IKPreviewRoot");
    private final Node modelWrapper = new Node("IKPreviewModelWrapper");
    private final Node skeletonDebugNode = new Node("IKPreviewSkeleton");
    private final Node targetNode = new Node(PREVIEW_TARGET_ID);
    private final Node targetVisualNode = new Node("IKPreviewTargetVisual");
    private final DesignerEntity previewEntity = new DesignerEntity("IK Preview Model", DesignerEntityType.MODEL);
    private final DesignerEntity targetEntity = new DesignerEntity(PREVIEW_TARGET_ID, DesignerEntityType.MODEL);

    private AssetsMapping previewAssets;
    private Spatial modelSpatial;
    private AnimComposer modelComposer;
    private TranslateGizmo translateGizmo;
    private RotateGizmo rotateGizmo;
    private GizmoManager gizmoManager;
    private String targetModelId = "";
    private String previewTargetVisual = "Sphere";
    private boolean editingPreviewTarget;
    private List<String> compatibleModels = Collections.emptyList();
    private Consumer<List<String>> compatibleModelsChangedCallback;
    private Consumer<List<String>> jointNamesChangedCallback;
    private Consumer<List<String>> targetVisualOptionsChangedCallback;
    private Consumer<List<String>> animationOptionsChangedCallback;
    private Consumer<String> statusChangedCallback;
    private BiConsumer<Boolean, String> busyChangedCallback;
    private final Map<String, String> highlightedJointLabels = new LinkedHashMap<>();
    private final Map<String, Transform> initialJointTransforms = new HashMap<>();
    private IKPreviewPlaybackControl playbackControl;
    private String currentAnimationName = "";
    private boolean skeletonRefreshRequested;
    private float skeletonRefreshElapsed;
    private BitmapFont labelFont;

    private float cameraDistance = 6f;
    private float yaw = (float) Math.toRadians(35);
    private float pitch = (float) Math.toRadians(18);
    private Vector3f cameraTarget = new Vector3f(0f, 1f, 0f);
    private final Vector2f lastMouse = new Vector2f();
    private boolean orbiting;

    IKPreviewApp(File resourcesRoot) {
        this.resourcesRoot = resourcesRoot;
        this.projectRoot = resourcesRoot != null ? resourcesRoot.getParentFile() : null;
    }

    @Override
    public void simpleInitApp() {
        if (projectRoot != null) {
            setWorkingFolder(new File(new File(projectRoot, "scripts"), "_ik_preview").getAbsolutePath());
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

        viewPort.setBackgroundColor(new ColorRGBA(0.045f, 0.05f, 0.06f, 1f));
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(previewRoot);
        previewRoot.attachChild(modelWrapper);
        previewRoot.attachChild(skeletonDebugNode);
        previewRoot.attachChild(targetNode);
        targetNode.setLocalTranslation(0f, 1.2f, 2f);
        targetNode.attachChild(targetVisualNode);
        initGizmos();
        setupLights();
        registerInput();
        updateCamera();
        updateBusy(true, "Scanning project models...");
        publishCompatibleModels();
        publishTargetVisualOptions();
        rebuildPreviewTargetVisual();
        updateBusy(true, "Preparing preview panel...");
        reloadSelectedModel();
        updateBusy(false, null);
    }

    @Override
    public void simpleUpdate(float tpf) {
        super.simpleUpdate(tpf);
        skeletonRefreshElapsed += tpf;
        if (skeletonRefreshRequested && skeletonRefreshElapsed >= 1f / 30f) {
            skeletonRefreshRequested = false;
            skeletonRefreshElapsed = 0f;
            rebuildSkeletonDebug();
        }
        if (gizmoManager != null) {
            gizmoManager.updateGizmoPosition();
            gizmoManager.scaleGizmoToCamera(cam);
            if (gizmoManager.isDragging()) {
                rebuildSkeletonDebug();
            }
        }
    }

    public void setCompatibleModelsChangedCallback(Consumer<List<String>> callback) {
        this.compatibleModelsChangedCallback = callback;
        if (callback != null && compatibleModels != null) {
            callback.accept(new ArrayList<>(compatibleModels));
        }
    }

    public void setJointNamesChangedCallback(Consumer<List<String>> callback) {
        this.jointNamesChangedCallback = callback;
    }

    public void setTargetVisualOptionsChangedCallback(Consumer<List<String>> callback) {
        this.targetVisualOptionsChangedCallback = callback;
        if (callback != null) {
            callback.accept(buildTargetVisualOptions());
        }
    }

    public void setAnimationOptionsChangedCallback(Consumer<List<String>> callback) {
        this.animationOptionsChangedCallback = callback;
        if (callback != null) {
            callback.accept(buildAnimationOptions());
        }
    }

    public void setStatusChangedCallback(Consumer<String> callback) {
        this.statusChangedCallback = callback;
    }

    public void setBusyChangedCallback(BiConsumer<Boolean, String> callback) {
        this.busyChangedCallback = callback;
    }

    public void setGizmoMode(GizmoMode mode) {
        enqueue(() -> {
            if (gizmoManager != null) {
                gizmoManager.setMode(mode);
            }
            return null;
        });
    }

    public void setEditingPreviewTarget(boolean editingPreviewTarget) {
        this.editingPreviewTarget = editingPreviewTarget;
        enqueue(() -> {
            if (gizmoManager != null) {
                gizmoManager.onSelectionChanged(editingPreviewTarget ? targetEntity : previewEntity);
            }
            return null;
        });
    }

    public void setPreviewTargetVisual(String visual) {
        previewTargetVisual = visual == null || visual.isBlank() ? "Sphere" : visual.trim();
        enqueue(() -> {
            rebuildPreviewTargetVisual();
            return null;
        });
    }

    public void setHighlightedJoints(List<String> jointNames) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (jointNames != null) {
            for (String jointName : jointNames) {
                if (jointName != null && !jointName.isBlank()) {
                    labels.put(jointName, jointName);
                }
            }
        }
        setHighlightedJoints(labels);
    }

    public void setHighlightedJoints(Map<String, String> jointLabels) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (jointLabels != null) {
            for (Map.Entry<String, String> entry : jointLabels.entrySet()) {
                String normalized = normalizeJoint(entry.getKey());
                if (!normalized.isBlank()) {
                    String label = entry.getValue() == null || entry.getValue().isBlank()
                            ? entry.getKey()
                            : entry.getValue().trim();
                    labels.put(normalized, label);
                }
            }
        }
        enqueue(() -> {
            highlightedJointLabels.clear();
            highlightedJointLabels.putAll(labels);
            rebuildSkeletonDebug();
            return null;
        });
    }

    public void runLayerPreview(IKLayerDefinition layer) {
        IKLayerDefinition previewLayer = layer == null ? null : IKLayerDefinition.fromJSON(layer.toJSON());
        enqueue(() -> {
            runLayerPreviewOnRenderThread(previewLayer);
            return null;
        });
    }

    public void playLayerPreview(IKLayerDefinition layer) {
        IKLayerDefinition previewLayer = layer == null ? null : IKLayerDefinition.fromJSON(layer.toJSON());
        enqueue(() -> {
            startLayerPlayback(previewLayer);
            return null;
        });
    }

    public void stopLayerPreview() {
        enqueue(() -> {
            stopLayerPlayback();
            return null;
        });
    }

    public void playAnimation(String animationName) {
        String name = animationName == null ? "" : animationName.trim();
        enqueue(() -> {
            playAnimationOnRenderThread(name);
            return null;
        });
    }

    public void stopAnimation() {
        enqueue(() -> {
            stopAnimationOnRenderThread();
            return null;
        });
    }

    public void resetModelPose() {
        enqueue(() -> {
            stopLayerPlayback();
            stopAnimationOnRenderThread();
            restoreInitialJointTransforms();
            rebuildSkeletonDebug();
            updateStatus("Preview pose reset.");
            return null;
        });
    }

    public void setTargetModelId(String modelId) {
        String next = modelId == null ? "" : modelId.trim();
        if (next.equals(targetModelId)) {
            return;
        }
        targetModelId = next;
        enqueue(() -> {
            updateBusy(true, "Preparing preview panel...");
            reloadSelectedModel();
            updateBusy(false, null);
            return null;
        });
    }

    public void resetCamera() {
        enqueue(() -> {
            fitCameraToPreview();
            return null;
        });
    }

    private void publishCompatibleModels() {
        Set<String> names = new LinkedHashSet<>();
        boolean compatibilityChanged = false;
        if (previewAssets != null && previewAssets.get3DModelsIndex() != null) {
            for (ResourceSetup resource : previewAssets.get3DModelsIndex().values()) {
                if (!resource.ikCompatibilityScanned) {
                    compatibilityChanged |= scanIKCompatibility(resource);
                }
                if (resource.ikReady) {
                    String name = resource.name == null || resource.name.isBlank()
                            ? resource.path
                            : resource.name;
                    if (name != null && !name.isBlank()) {
                        names.add(name);
                    }
                }
            }
        }
        if (compatibilityChanged) {
            persistIKCompatibilityFlags();
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        compatibleModels = sorted;
        if (compatibleModelsChangedCallback != null) {
            compatibleModelsChangedCallback.accept(new ArrayList<>(compatibleModels));
        }
        if (sorted.isEmpty()) {
            updateStatus("No project models with SkinningControl armatures were found.");
        }
    }

    private void publishTargetVisualOptions() {
        if (targetVisualOptionsChangedCallback != null) {
            targetVisualOptionsChangedCallback.accept(buildTargetVisualOptions());
        }
    }

    private void publishAnimationOptions() {
        if (animationOptionsChangedCallback != null) {
            animationOptionsChangedCallback.accept(buildAnimationOptions());
        }
    }

    private List<String> buildTargetVisualOptions() {
        List<String> options = new ArrayList<>();
        options.add("Sphere");
        options.add("Box");
        if (previewAssets != null && previewAssets.get3DModelsIndex() != null) {
            List<String> models = new ArrayList<>();
            for (ResourceSetup resource : previewAssets.get3DModelsIndex().values()) {
                if (resource != null && resource.name != null && !resource.name.isBlank()) {
                    models.add("Model: " + resource.name);
                }
            }
            models.sort(String.CASE_INSENSITIVE_ORDER);
            options.addAll(models);
        }
        return options;
    }

    private List<String> buildAnimationOptions() {
        List<String> options = new ArrayList<>();
        if (modelComposer != null) {
            for (AnimClip clip : modelComposer.getAnimClips()) {
                if (clip != null && clip.getName() != null && !clip.getName().isBlank()) {
                    options.add(clip.getName());
                }
            }
        }
        options.sort(String.CASE_INSENSITIVE_ORDER);
        return options;
    }

    private boolean scanIKCompatibility(ResourceSetup resource) {
        if (resource == null || resource.path == null || resource.path.isBlank()) {
            return false;
        }
        boolean hasArmature = false;
        boolean ikReady = false;
        try {
            Spatial spatial = assetManager.loadModel(resource.path);
            SkinningControl skinning = findSkinningControl(spatial);
            Armature armature = skinning == null ? null : skinning.getArmature();
            hasArmature = armature != null && armature.getJointCount() > 0;
            ikReady = hasArmature;
        } catch (Exception ignored) {
        }
        boolean changed = !resource.ikCompatibilityScanned
                || resource.hasSkeleton != hasArmature
                || resource.hasArmature != hasArmature
                || resource.ikReady != ikReady;
        resource.ikCompatibilityScanned = true;
        resource.hasSkeleton = hasArmature;
        resource.hasArmature = hasArmature;
        resource.ikReady = ikReady;
        return changed;
    }

    private void persistIKCompatibilityFlags() {
        File indexFile = findModelsIndexFile();
        if (indexFile == null || !indexFile.isFile() || previewAssets == null || previewAssets.get3DModelsIndex() == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject(Files.readString(indexFile.toPath(), StandardCharsets.UTF_8));
            JSONArray models = root.optJSONArray("models");
            if (models == null) {
                return;
            }
            for (int i = 0; i < models.length(); i++) {
                JSONObject entry = models.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                ResourceSetup resource = resolveModel(entry.optString("name", ""));
                if (resource == null) {
                    resource = resolveModelByPath(entry.optString("path", ""));
                }
                if (resource == null || !resource.ikCompatibilityScanned) {
                    continue;
                }
                entry.put("hasSkeleton", resource.hasSkeleton);
                entry.put("hasArmature", resource.hasArmature);
                entry.put("ikReady", resource.ikReady);
                entry.put("ikCompatibilityScanned", true);
            }
            Files.writeString(indexFile.toPath(), root.toString(2), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            ex.printStackTrace();
            updateStatus("Could not update model IK compatibility cache: " + ex.getMessage());
        }
    }

    private File findModelsIndexFile() {
        if (resourcesRoot == null) {
            return null;
        }
        File upper = new File(new File(resourcesRoot, "Models"), "models-ext.json");
        if (upper.isFile()) {
            return upper;
        }
        File lower = new File(new File(resourcesRoot, "models"), "models-ext.json");
        return lower.isFile() ? lower : upper;
    }

    private ResourceSetup resolveModelByPath(String path) {
        if (previewAssets == null || previewAssets.get3DModelsIndex() == null || path == null || path.isBlank()) {
            return null;
        }
        for (ResourceSetup resource : previewAssets.get3DModelsIndex().values()) {
            if (resource != null && resource.path != null && resource.path.equalsIgnoreCase(path.trim())) {
                return resource;
            }
        }
        return null;
    }

    private void reloadSelectedModel() {
        stopLayerPlayback();
        stopAnimationOnRenderThread();
        modelWrapper.detachAllChildren();
        skeletonDebugNode.detachAllChildren();
        modelSpatial = null;
        modelComposer = null;
        if (gizmoManager != null) {
            gizmoManager.onSelectionChanged(null);
        }
        publishJointNames(Collections.emptyList());
        publishAnimationOptions();

        if (targetModelId == null || targetModelId.isBlank()) {
            updateStatus("Choose a model with a new animation armature.");
            return;
        }
        ResourceSetup resource = resolveModel(targetModelId);
        if (resource == null) {
            updateStatus("Model not found: " + targetModelId);
            return;
        }
        try {
            modelSpatial = assetManager.loadModel(resource.path);
            modelWrapper.setLocalTranslation(resource.localTranslationX, resource.localTranslationY, resource.localTranslationZ);
            modelWrapper.setLocalScale(resource.scaleX, resource.scaleY, resource.scaleZ);
            modelWrapper.setLocalRotation(new Quaternion().fromAngles(0f, resource.rotateY * FastMath.DEG_TO_RAD, 0f));
            modelWrapper.attachChild(modelSpatial);
            applyWireframe(modelSpatial);
            modelComposer = findAnimComposer(modelSpatial);
            rootNode.updateLogicalState(0f);
            rootNode.updateGeometricState();

            SkinningControl skinning = findSkinningControl(modelSpatial);
            if (skinning == null || skinning.getArmature() == null || skinning.getArmature().getJointCount() == 0) {
                updateStatus(targetModelId + " does not expose a SkinningControl armature.");
                return;
            }
            List<String> joints = new ArrayList<>();
            for (Joint joint : skinning.getArmature().getJointList()) {
                if (joint != null && joint.getName() != null && !joint.getName().isBlank()) {
                    joints.add(joint.getName());
                }
            }
            joints.sort(String.CASE_INSENSITIVE_ORDER);
            publishJointNames(joints);
            publishAnimationOptions();
            captureInitialJointTransforms(skinning);
            rebuildSkeletonDebug(skinning);
            if (gizmoManager != null) {
                gizmoManager.onSelectionChanged(editingPreviewTarget ? targetEntity : previewEntity);
            }
            fitCameraToPreview();
            updateStatus("Previewing " + resource.name + " (" + skinning.getArmature().getJointCount() + " joints).");
        } catch (Exception ex) {
            ex.printStackTrace();
            updateStatus("Preview failed: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    private ResourceSetup resolveModel(String modelId) {
        if (previewAssets == null || previewAssets.get3DModelsIndex() == null || modelId == null) {
            return null;
        }
        ResourceSetup direct = previewAssets.get3DModelsIndex().get(modelId.trim().toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        for (ResourceSetup resource : previewAssets.get3DModelsIndex().values()) {
            if (resource != null && resource.name != null && resource.name.equalsIgnoreCase(modelId.trim())) {
                return resource;
            }
        }
        return null;
    }

    private void rebuildSkeletonDebug(SkinningControl skinning) {
        skeletonDebugNode.detachAllChildren();
        skinning.getArmature().update();
        Material jointMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        jointMaterial.setColor("Color", new ColorRGBA(0.32f, 0.83f, 1f, 1f));
        Material highlightedJointMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        highlightedJointMaterial.setColor("Color", new ColorRGBA(1f, 0.18f, 0.12f, 1f));
        Material boneMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        boneMaterial.setColor("Color", new ColorRGBA(1f, 0.82f, 0.24f, 1f));
        Material highlightedBoneMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        highlightedBoneMaterial.setColor("Color", new ColorRGBA(1f, 0.26f, 0.14f, 1f));

        float jointRadius = Math.max(0.015f, cameraDistance * 0.004f);
        for (Joint joint : skinning.getArmature().getJointList()) {
            if (joint == null) {
                continue;
            }
            Vector3f position = jointWorldPosition(skinning, joint);
            if (position == null) {
                continue;
            }
            boolean highlighted = isHighlightedJoint(joint);
            float radius = highlighted ? jointRadius * 2.2f : jointRadius;
            Geometry sphere = new Geometry("joint_" + joint.getName(), new Sphere(10, 10, radius));
            sphere.setMaterial(highlighted ? highlightedJointMaterial : jointMaterial);
            sphere.setQueueBucket(RenderQueue.Bucket.Transparent);
            sphere.setLocalTranslation(position);
            skeletonDebugNode.attachChild(sphere);
            if (highlighted) {
                attachJointLabel(highlightLabel(joint), position, radius * 2.5f);
            }

            Joint parent = joint.getParent();
            Vector3f parentPosition = parent == null ? null : jointWorldPosition(skinning, parent);
            if (parentPosition != null) {
                Vector3f direction = position.subtract(parentPosition);
                if (direction.lengthSquared() > 0.000001f) {
                    Arrow arrow = new Arrow(direction);
                    arrow.setLineWidth(highlighted ? 4f : 2f);
                    Geometry bone = new Geometry("bone_" + joint.getName(), arrow);
                    bone.setMaterial(highlighted ? highlightedBoneMaterial : boneMaterial);
                    bone.setLocalTranslation(parentPosition);
                    skeletonDebugNode.attachChild(bone);
                }
            }
        }
    }

    private void publishJointNames(List<String> joints) {
        if (jointNamesChangedCallback != null) {
            jointNamesChangedCallback.accept(new ArrayList<>(joints == null ? Collections.emptyList() : joints));
        }
    }

    private void attachJointLabel(String jointName, Vector3f position, float offset) {
        if (labelFont == null) {
            labelFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        }
        BitmapText text = new BitmapText(labelFont, false);
        text.setText(jointName);
        text.setSize(Math.max(0.14f, cameraDistance * 0.018f));
        text.setColor(new ColorRGBA(1f, 0.94f, 0.55f, 1f));
        text.setLocalTranslation(position.add(0f, offset, 0f));
        text.addControl(new BillboardControl());
        skeletonDebugNode.attachChild(text);
    }

    private boolean isHighlightedJoint(Joint joint) {
        return joint != null && highlightedJointLabels.containsKey(normalizeJoint(joint.getName()));
    }

    private String highlightLabel(Joint joint) {
        if (joint == null) {
            return "";
        }
        String label = highlightedJointLabels.get(normalizeJoint(joint.getName()));
        return label == null || label.isBlank() ? joint.getName() : label;
    }

    private static String normalizeJoint(String value) {
        if (value == null) {
            return "";
        }
        String stripped = value;
        int colon = stripped.lastIndexOf(':');
        if (colon >= 0 && colon < stripped.length() - 1) {
            stripped = stripped.substring(colon + 1);
        }
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void rebuildPreviewTargetVisual() {
        targetVisualNode.detachAllChildren();
        String visual = previewTargetVisual == null || previewTargetVisual.isBlank() ? "Sphere" : previewTargetVisual.trim();
        try {
            if (visual.startsWith("Model: ")) {
                String modelName = visual.substring("Model: ".length()).trim();
                ResourceSetup resource = resolveModel(modelName);
                if (resource != null) {
                    Spatial spatial = assetManager.loadModel(resource.path);
                    spatial.setLocalScale(resource.scaleX, resource.scaleY, resource.scaleZ);
                    spatial.setLocalRotation(new Quaternion().fromAngles(0f, resource.rotateY * FastMath.DEG_TO_RAD, 0f));
                    targetVisualNode.attachChild(spatial);
                    return;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Geometry geometry;
        if ("Box".equalsIgnoreCase(visual)) {
            geometry = new Geometry(PREVIEW_TARGET_ID + "_box", new Box(0.18f, 0.18f, 0.18f));
        } else {
            geometry = new Geometry(PREVIEW_TARGET_ID + "_sphere", new Sphere(16, 16, 0.22f));
        }
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(1f, 0.22f, 0.16f, 1f));
        geometry.setMaterial(material);
        targetVisualNode.attachChild(geometry);
    }

    private void rebuildSkeletonDebug() {
        SkinningControl skinning = findSkinningControl(modelSpatial);
        if (skinning != null && skinning.getArmature() != null) {
            rootNode.updateLogicalState(0f);
            rootNode.updateGeometricState();
            rebuildSkeletonDebug(skinning);
        }
    }

    private void runLayerPreviewOnRenderThread(IKLayerDefinition layer) {
        stopLayerPlayback();
        if (layer == null) {
            updateStatus("Choose an IK layer before running the preview.");
            return;
        }
        SkinningControl skinning = findSkinningControl(modelSpatial);
        if (skinning == null || skinning.getArmature() == null) {
            updateStatus("Choose an IK-ready model before running the preview.");
            return;
        }

        List<String> missing = missingPreviewFields(layer);
        if (!missing.isEmpty()) {
            updateStatus("Choose " + String.join(", ", missing) + " before running the IK preview.");
            return;
        }

        restoreInitialJointTransforms();
        layer.setTarget(PREVIEW_TARGET_ID);

        AppModel appModel = new AppModel(modelWrapper);
        appModel.skinningControlNode = skinning.getSpatial();
        IKSolver solver = createPreviewSolver(layer.getSolverType());
        solver.solve(new IKContext(this, appModel, layer, 1f / 60f, Math.max(0.01f, layer.getWeight())));

        skinning.getArmature().update();
        rootNode.updateLogicalState(0f);
        rootNode.updateGeometricState();
        rebuildSkeletonDebug(skinning);
        updateStatus("Ran " + layer.getSolverType() + " preview using ik_preview_target. Move the target and run again.");
    }

    private void startLayerPlayback(IKLayerDefinition layer) {
        stopLayerPlayback();
        if (layer == null) {
            updateStatus("Choose an IK layer before playing the preview.");
            return;
        }
        SkinningControl skinning = findSkinningControl(modelSpatial);
        if (skinning == null || skinning.getArmature() == null) {
            updateStatus("Choose an IK-ready model before playing the preview.");
            return;
        }
        List<String> missing = missingPreviewFields(layer);
        if (!missing.isEmpty()) {
            updateStatus("Choose " + String.join(", ", missing) + " before playing the IK preview.");
            return;
        }
        if (currentAnimationName == null || currentAnimationName.isBlank()) {
            restoreInitialJointTransforms();
        }
        layer.setTarget(PREVIEW_TARGET_ID);
        AppModel appModel = new AppModel(modelWrapper);
        appModel.skinningControlNode = skinning.getSpatial();
        playbackControl = new IKPreviewPlaybackControl(appModel, layer, createPreviewSolver(layer.getSolverType()));
        skeletonDebugNode.addControl(playbackControl);
        updateStatus("Playing " + layer.getSolverType() + " every frame. Move ik_preview_target while it runs.");
    }

    private void stopLayerPlayback() {
        if (playbackControl != null) {
            if (playbackControl.getSpatial() != null) {
                playbackControl.getSpatial().removeControl(playbackControl);
            }
            playbackControl = null;
            skeletonRefreshRequested = true;
            updateStatus("IK playback stopped.");
        }
    }

    private void playAnimationOnRenderThread(String animationName) {
        if (animationName == null || animationName.isBlank()) {
            stopAnimationOnRenderThread();
            return;
        }
        if (modelComposer == null) {
            updateStatus("The selected model has no embedded AnimComposer animations.");
            return;
        }
        if (!modelComposer.hasAction(animationName)) {
            if (!modelComposer.hasAnimClip(animationName)) {
                updateStatus("Animation not found: " + animationName);
                return;
            }
            Action action = modelComposer.action(animationName);
            modelComposer.addAction(animationName, action);
        }
        restoreInitialJointTransforms();
        modelComposer.setGlobalSpeed(1f);
        modelComposer.setCurrentAction(animationName);
        currentAnimationName = animationName;
        updateStatus("Playing animation " + animationName + ". Press Play to layer IK over it.");
    }

    private void stopAnimationOnRenderThread() {
        if (modelComposer != null) {
            try {
                modelComposer.removeCurrentAction();
                modelComposer.reset();
            } catch (RuntimeException ignored) {
            }
        }
        currentAnimationName = "";
        if (playbackControl == null) {
            restoreInitialJointTransforms();
            skeletonRefreshRequested = true;
        }
    }

    private List<String> missingPreviewFields(IKLayerDefinition layer) {
        List<String> missing = new ArrayList<>();
        String solverType = layer.getSolverType();
        if (IKLayerDefinition.SOLVER_TWO_BONE.equalsIgnoreCase(solverType)
                || IKLayerDefinition.SOLVER_FOOT.equalsIgnoreCase(solverType)) {
            requirePreviewField(missing, "Root", layer.getRootJoint());
            requirePreviewField(missing, "Middle", layer.getMiddleJoint());
            requirePreviewField(missing, "End", layer.getEndJoint());
        } else if (IKLayerDefinition.SOLVER_THREE_BONE.equalsIgnoreCase(solverType)) {
            requirePreviewField(missing, "Root", layer.getRootJoint());
            requirePreviewField(missing, "Middle", layer.getMiddleJoint());
            requirePreviewField(missing, "Middle 2", layer.getSecondMiddleJoint());
            requirePreviewField(missing, "End", layer.getEndJoint());
        } else if (IKLayerDefinition.SOLVER_FABRIK.equalsIgnoreCase(solverType)) {
            requirePreviewField(missing, "Start", layer.getStartJoint());
            requirePreviewField(missing, "End", layer.getEndJoint());
        } else if ((IKLayerDefinition.SOLVER_LOOK_AT.equalsIgnoreCase(solverType)
                || IKLayerDefinition.SOLVER_AIM.equalsIgnoreCase(solverType))
                && layer.getAffectedJoints().isEmpty()
                && (layer.getEndJoint() == null || layer.getEndJoint().isBlank())) {
            missing.add("Affected Joints");
        }
        return missing;
    }

    private void requirePreviewField(List<String> missing, String label, String value) {
        if (value == null || value.isBlank()) {
            missing.add(label);
        }
    }

    private IKSolver createPreviewSolver(String solverType) {
        if (IKLayerDefinition.SOLVER_LOOK_AT.equalsIgnoreCase(solverType)
                || IKLayerDefinition.SOLVER_AIM.equalsIgnoreCase(solverType)) {
            return new LookAtIKSolver();
        }
        if (IKLayerDefinition.SOLVER_FABRIK.equalsIgnoreCase(solverType)) {
            return new FABRIKIKSolver();
        }
        if (IKLayerDefinition.SOLVER_THREE_BONE.equalsIgnoreCase(solverType)) {
            return new ThreeBoneIKSolver();
        }
        if (IKLayerDefinition.SOLVER_FOOT.equalsIgnoreCase(solverType)) {
            return new FootIKSolver();
        }
        return new TwoBoneIKSolver();
    }

    private void captureInitialJointTransforms(SkinningControl skinning) {
        initialJointTransforms.clear();
        if (skinning == null || skinning.getArmature() == null) {
            return;
        }
        for (Joint joint : skinning.getArmature().getJointList()) {
            if (joint != null && joint.getName() != null) {
                initialJointTransforms.put(joint.getName(), joint.getLocalTransform().clone());
            }
        }
    }

    private void restoreInitialJointTransforms() {
        SkinningControl skinning = findSkinningControl(modelSpatial);
        if (skinning == null || skinning.getArmature() == null || initialJointTransforms.isEmpty()) {
            return;
        }
        for (Joint joint : skinning.getArmature().getJointList()) {
            Transform transform = joint == null || joint.getName() == null ? null : initialJointTransforms.get(joint.getName());
            if (transform != null) {
                joint.setLocalTransform(transform.clone());
            }
        }
        skinning.getArmature().update();
        rootNode.updateLogicalState(0f);
        rootNode.updateGeometricState();
    }

    @Override
    public Spatial getEntitySpatial(String varName) {
        if (PREVIEW_TARGET_ID.equals(varName)) {
            return targetNode;
        }
        return super.getEntitySpatial(varName);
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

    private final class IKPreviewPlaybackControl extends AbstractControl {
        private final AppModel appModel;
        private final IKLayerDefinition layer;
        private final IKSolver solver;
        private float elapsed;

        IKPreviewPlaybackControl(AppModel appModel, IKLayerDefinition layer, IKSolver solver) {
            this.appModel = appModel;
            this.layer = layer;
            this.solver = solver;
        }

        @Override
        protected void controlUpdate(float tpf) {
            if (appModel == null || layer == null || solver == null || appModel.getSkinningControl() == null) {
                return;
            }
            elapsed += Math.max(0f, tpf);
            float blend = FastMath.clamp(elapsed / 0.75f, 0f, 1f);
            float effectiveWeight = layer.getWeight() * blend;
            if (effectiveWeight <= 0f) {
                return;
            }
            solver.solve(new IKContext(IKPreviewApp.this, appModel, layer, tpf, effectiveWeight));
            SkinningControl skinning = appModel.getSkinningControl();
            if (skinning != null && skinning.getArmature() != null) {
                skinning.getArmature().update();
                skeletonRefreshRequested = true;
            }
        }

        @Override
        protected void controlRender(RenderManager rm, ViewPort vp) {
        }
    }

    private void applyWireframe(Spatial spatial) {
        if (spatial instanceof Geometry) {
            Geometry geometry = (Geometry) spatial;
            if (geometry.getMaterial() != null) {
                geometry.getMaterial().getAdditionalRenderState().setWireframe(true);
            }
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                applyWireframe(child);
            }
        }
    }

    private Vector3f jointWorldPosition(SkinningControl skinning, Joint joint) {
        if (skinning == null || skinning.getSpatial() == null || joint == null) {
            return null;
        }
        Transform skinTransform = skinning.getSpatial().getWorldTransform();
        return joint.getModelTransform().clone().combineWithParent(skinTransform).getTranslation();
    }

    private SkinningControl findSkinningControl(Spatial spatial) {
        if (spatial == null) {
            return null;
        }
        SkinningControl control = spatial.getControl(SkinningControl.class);
        if (control != null) {
            return control;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                SkinningControl found = findSkinningControl(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void fitCameraToPreview() {
        rootNode.updateGeometricState();
        BoundingVolume bound = modelWrapper.getWorldBound();
        if (bound instanceof BoundingBox) {
            BoundingBox box = (BoundingBox) bound;
            cameraTarget = box.getCenter().clone();
            float radius = Math.max(box.getXExtent(), Math.max(box.getYExtent(), box.getZExtent()));
            cameraDistance = FastMath.clamp(radius * 4.2f, 2.2f, 40f);
        } else if (bound instanceof BoundingSphere) {
            BoundingSphere sphere = (BoundingSphere) bound;
            cameraTarget = sphere.getCenter().clone();
            cameraDistance = FastMath.clamp(sphere.getRadius() * 3.5f, 2.2f, 40f);
        } else {
            cameraTarget = new Vector3f(0f, 1f, 0f);
            cameraDistance = 6f;
        }
        yaw = (float) Math.toRadians(35);
        pitch = (float) Math.toRadians(18);
        updateCamera();
    }

    private void setupLights() {
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(1.25f));
        rootNode.addLight(ambient);

        DirectionalLight key = new DirectionalLight();
        key.setDirection(new Vector3f(-0.45f, -0.8f, -0.35f).normalizeLocal());
        key.setColor(ColorRGBA.White.mult(2.4f));
        rootNode.addLight(key);

        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.55f, -0.25f, 0.6f).normalizeLocal());
        fill.setColor(new ColorRGBA(0.72f, 0.82f, 1f, 1f).mult(1.2f));
        rootNode.addLight(fill);
    }

    private void initGizmos() {
        previewEntity.setSceneNode(modelWrapper);
        targetEntity.setSceneNode(targetNode);
        translateGizmo = new TranslateGizmo(assetManager);
        rotateGizmo = new RotateGizmo(assetManager);
        rootNode.attachChild(translateGizmo);
        rootNode.attachChild(rotateGizmo);
        gizmoManager = new GizmoManager(rootNode, translateGizmo, rotateGizmo);
        gizmoManager.onSelectionChanged(null);
        gizmoManager.setDragEndCallback(entity -> rebuildSkeletonDebug());
    }

    private void registerInput() {
        inputManager.addMapping(ACTION_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(ACTION_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACTION_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addMapping(ACTION_X_POS, new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping(ACTION_X_NEG, new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping(ACTION_Y_POS, new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping(ACTION_Y_NEG, new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addListener((ActionListener) (name, pressed, tpf) -> {
            if (ACTION_LEFT.equals(name)) {
                if (pressed) {
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
            }
        }, ACTION_LEFT);
        inputManager.addListener((AnalogListener) (name, value, tpf) -> {
            if (ACTION_SCROLL_UP.equals(name)) {
                cameraDistance = Math.max(1.2f, cameraDistance - value * 8f);
                updateCamera();
            } else if (ACTION_SCROLL_DOWN.equals(name)) {
                cameraDistance = Math.min(60f, cameraDistance + value * 8f);
                updateCamera();
            } else if (orbiting) {
                Vector2f current = inputManager.getCursorPosition();
                float dx = current.x - lastMouse.x;
                float dy = current.y - lastMouse.y;
                lastMouse.set(current);
                yaw -= dx * 0.01f;
                pitch = FastMath.clamp(pitch + dy * 0.01f, -1.2f, 1.2f);
                updateCamera();
            } else if (gizmoManager != null && gizmoManager.isDragging()) {
                gizmoManager.updateDrag(cam, inputManager.getCursorPosition());
                rebuildSkeletonDebug();
            }
        }, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN, ACTION_X_POS, ACTION_X_NEG, ACTION_Y_POS, ACTION_Y_NEG);
    }

    private void updateCamera() {
        Vector3f location = new Vector3f(
                cameraTarget.x + cameraDistance * FastMath.cos(pitch) * FastMath.sin(yaw),
                cameraTarget.y + cameraDistance * FastMath.sin(pitch),
                cameraTarget.z + cameraDistance * FastMath.cos(pitch) * FastMath.cos(yaw));
        cam.setLocation(location);
        cam.lookAt(cameraTarget, Vector3f.UNIT_Y);
    }

    private void updateStatus(String message) {
        if (statusChangedCallback != null) {
            statusChangedCallback.accept(message);
        }
    }

    private void updateBusy(boolean busy, String message) {
        if (busyChangedCallback != null) {
            busyChangedCallback.accept(busy, message);
        }
        if (message != null && !message.isBlank()) {
            updateStatus(message);
        }
    }

    private void tryRegisterLocator(File folder) {
        if (folder != null && folder.isDirectory()) {
            assetManager.registerLocator(folder.getAbsolutePath(), FileLocator.class);
        }
    }
}
