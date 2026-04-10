package com.scenemax.designer.shader;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.MatParam;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.MaterialDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.shader.VarType;
import com.jme3.texture.Texture;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;
import com.scenemax.designer.gizmo.GizmoManager;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.gizmo.RotateGizmo;
import com.scenemax.designer.gizmo.TranslateGizmo;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.projector.SceneMaxApp;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ShaderPreviewApp extends SceneMaxApp {

    private final Node pivotNode = new Node("ShaderPreviewPivot");
    private final Node previewContentNode = new Node("ShaderPreviewContent");
    private Spatial previewSpatial;
    private ShaderDocument currentDocument;
    private File currentFile;
    private String resourcesFolder;
    private float elapsedTime = 0f;
    private DesignerEntity previewEntity;
    private TranslateGizmo translateGizmo;
    private RotateGizmo rotateGizmo;
    private GizmoManager gizmoManager;

    private float cameraDistance = 4.6f;
    private float cameraYaw = (float) Math.toRadians(25);
    private float cameraPitch = (float) Math.toRadians(18);
    private Vector3f cameraTarget = new Vector3f(0, 0, 0);
    private boolean orbiting = false;
    private boolean panning = false;
    private final Vector2f lastMousePos = new Vector2f();
    private final Map<Geometry, Material> originalPreviewMaterials = new IdentityHashMap<>();
    private final Set<Material> previewShaderMaterials = Collections.newSetFromMap(new WeakHashMap<>());
    private ShaderPreviewTarget loadedPreviewTarget = null;
    private String loadedPreviewModelName = null;

    private static final String ACTION_LEFT_CLICK = "ShaderPreviewLeftClick";
    private static final String ACTION_RIGHT_CLICK = "ShaderPreviewRightClick";
    private static final String ACTION_MIDDLE_CLICK = "ShaderPreviewMiddleClick";
    private static final String ACTION_SCROLL_UP = "ShaderPreviewScrollUp";
    private static final String ACTION_SCROLL_DOWN = "ShaderPreviewScrollDown";

    @Override
    public void simpleInitApp() {
        if (resourcesFolder != null && !resourcesFolder.isBlank()) {
            File resourcesRoot = new File(resourcesFolder);
            File projectRoot = resourcesRoot.getParentFile();
            if (projectRoot != null) {
                // SceneMaxApp derives the project root from workingFolder.parent.parent,
                // so we give it a path that lives one level below the project's scripts folder.
                File previewWorkingFolder = new File(new File(projectRoot, "scripts"), "_shader_preview");
                setWorkingFolder(previewWorkingFolder.getAbsolutePath());
            }
        }

        super.simpleInitApp();
        viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.08f, 0.12f, 1f));
        flyCam.setEnabled(false);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(pivotNode);
        pivotNode.attachChild(previewContentNode);
        initGizmos();
        registerInputMappings();
        rebuildGeometry(ShaderPreviewTarget.BOX);
        updateOrbitCamera();
    }

    @Override
    public void simpleUpdate(float tpf) {
        super.simpleUpdate(tpf);
        elapsedTime += tpf;
        if ((orbiting || panning) && inputManager != null) {
            Vector2f currentMouse = inputManager.getCursorPosition();
            float dx = currentMouse.x - lastMousePos.x;
            float dy = currentMouse.y - lastMousePos.y;
            lastMousePos.set(currentMouse);

            if (orbiting) {
                cameraYaw -= dx * 0.005f;
                cameraPitch += dy * 0.005f;
                cameraPitch = FastMath.clamp(cameraPitch,
                        (float) Math.toRadians(-89), (float) Math.toRadians(89));
                updateOrbitCamera();
            } else if (panning) {
                Vector3f right = cam.getLeft().negate().mult(dx * 0.01f);
                Vector3f up = cam.getUp().mult(dy * 0.01f);
                cameraTarget.addLocal(right).addLocal(up);
                updateOrbitCamera();
            }
        }
        for (Material material : previewShaderMaterials) {
            if (material != null) {
                material.setFloat("Time", elapsedTime);
            }
        }
        if (gizmoManager != null) {
            if (gizmoManager.isDragging() && inputManager != null) {
                gizmoManager.updateDrag(cam, inputManager.getCursorPosition());
            }
            gizmoManager.updateGizmoPosition();
            gizmoManager.scaleGizmoToCamera(cam);
        }
    }

    public void setResourcesFolder(String resourcesFolder) {
        this.resourcesFolder = resourcesFolder;
    }

    public void updatePreview(File shaderFile, ShaderDocument document) {
        if (document == null || shaderFile == null) {
            return;
        }
        currentFile = shaderFile;
        currentDocument = document.copy();
        enqueue(() -> {
            applyDocument();
            return null;
        });
    }

    private void initGizmos() {
        translateGizmo = new TranslateGizmo(assetManager);
        rotateGizmo = new RotateGizmo(assetManager);
        rootNode.attachChild(translateGizmo);
        rootNode.attachChild(rotateGizmo);

        gizmoManager = new GizmoManager(rootNode, translateGizmo, rotateGizmo);
        gizmoManager.setMode(GizmoMode.ROTATE);
        gizmoManager.setDragEndCallback(entity -> updateOrbitCamera());
    }

    private void registerInputMappings() {
        inputManager.addMapping(ACTION_LEFT_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(ACTION_RIGHT_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping(ACTION_MIDDLE_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_MIDDLE));
        inputManager.addMapping(ACTION_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACTION_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        inputManager.addListener((ActionListener) (name, isPressed, tpf) -> {
            if (ACTION_LEFT_CLICK.equals(name)) {
                if (isPressed) {
                    if (gizmoManager != null) {
                        gizmoManager.tryStartDrag(cam, inputManager.getCursorPosition());
                    }
                } else if (gizmoManager != null && gizmoManager.isDragging()) {
                    gizmoManager.endDrag();
                }
            } else if (ACTION_RIGHT_CLICK.equals(name)) {
                orbiting = isPressed;
                if (isPressed) {
                    lastMousePos.set(inputManager.getCursorPosition());
                }
            } else if (ACTION_MIDDLE_CLICK.equals(name)) {
                panning = isPressed;
                if (isPressed) {
                    lastMousePos.set(inputManager.getCursorPosition());
                }
            }
        }, ACTION_LEFT_CLICK, ACTION_RIGHT_CLICK, ACTION_MIDDLE_CLICK);

        inputManager.addListener((AnalogListener) (name, value, tpf) -> {
            if (ACTION_SCROLL_UP.equals(name)) {
                cameraDistance = Math.max(0.5f, cameraDistance - value * 25f);
                updateOrbitCamera();
            } else if (ACTION_SCROLL_DOWN.equals(name)) {
                cameraDistance = Math.min(200f, cameraDistance + value * 25f);
                updateOrbitCamera();
            }
        }, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN);
    }

    private void applyDocument() {
        ensurePreviewGeometry();

        if (resourcesFolder == null || resourcesFolder.isBlank()) {
            return;
        }

        try {
            currentDocument.exportRuntimeAssets(currentFile, resourcesFolder);
            assetManager.clearCache();

            String assetBase = ShaderDocument.getRuntimeAssetBase(currentFile);
            applyMaterialToPreview(assetBase, RenderQueue.Bucket.Transparent);
        } catch (Exception ex) {
            ex.printStackTrace();
            applyFallbackMaterial();
        }
    }

    private void ensurePreviewGeometry() {
        ShaderPreviewTarget target = currentDocument != null ? currentDocument.getPreviewTarget() : ShaderPreviewTarget.BOX;
        String modelName = currentDocument != null ? currentDocument.getPreviewModelName() : null;

        boolean needsRebuild = previewSpatial == null || loadedPreviewTarget != target;
        if (!needsRebuild && target == ShaderPreviewTarget.MODEL) {
            String currentName = loadedPreviewModelName != null ? loadedPreviewModelName : "";
            String newName = modelName != null ? modelName : "";
            needsRebuild = !currentName.equals(newName);
        }

        if (needsRebuild) {
            rebuildGeometry(target);
            loadedPreviewTarget = target;
            loadedPreviewModelName = target == ShaderPreviewTarget.MODEL ? modelName : null;
        }
    }

    private void applyFallbackMaterial() {
        if (previewSpatial == null) {
            return;
        }
        Material fallback = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA color = currentDocument != null ? currentDocument.getMainColor() : new ColorRGBA(0.9f, 0.6f, 0.2f, 1f);
        fallback.setColor("Color", color);
        applyFallbackMaterialRecursive(previewContentNode, fallback, RenderQueue.Bucket.Opaque);
    }

    private void rebuildGeometry(ShaderPreviewTarget target) {
        if (previewSpatial != null) {
            previewSpatial.removeFromParent();
        }
        previewContentNode.detachAllChildren();
        previewContentNode.setLocalRotation(Quaternion.IDENTITY);
        originalPreviewMaterials.clear();
        previewShaderMaterials.clear();

        ShaderPreviewTarget safeTarget = target != null ? target : ShaderPreviewTarget.BOX;
        switch (safeTarget) {
            case SPHERE:
                previewSpatial = new Geometry("ShaderPreviewSphere", new Sphere(48, 48, 1f));
                previewSpatial.setLocalTranslation(0f, 0f, 0f);
                break;
            case SPRITE:
                previewSpatial = new Geometry("ShaderPreviewSprite", new Quad(2.4f, 2.4f));
                previewSpatial.setLocalTranslation(-1.2f, -1.2f, 0f);
                previewSpatial.rotate(0f, FastMath.PI, 0f);
                break;
            case MODEL:
                previewSpatial = loadPreviewModel();
                break;
            case BOX:
            default:
                previewSpatial = new Geometry("ShaderPreviewBox", new Box(0.95f, 0.95f, 0.95f));
                previewSpatial.setLocalTranslation(0f, 0f, 0f);
                break;
        }

        if (previewSpatial == null) {
            previewSpatial = new Geometry("ShaderPreviewBoxFallback", new Box(0.95f, 0.95f, 0.95f));
        }

        applyPreviewScale();

        previewContentNode.attachChild(previewSpatial);
        captureOriginalMaterials(previewSpatial);
        centerPreviewContent();
        attachPreviewEntity(safeTarget);
        applyFallbackMaterial();
        fitCameraToPreview();

        if (safeTarget == ShaderPreviewTarget.SPRITE) {
            previewSpatial.setShadowMode(RenderQueue.ShadowMode.Off);
        } else {
            previewSpatial.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        }
    }

    private Spatial loadPreviewModel() {
        if (currentDocument == null || currentDocument.getPreviewModelName().isBlank() || assetsMapping == null) {
            return new Geometry("ShaderPreviewBox", new Box(0.95f, 0.95f, 0.95f));
        }
        try {
            ResourceSetup res = assetsMapping.get3DModelsIndex().get(currentDocument.getPreviewModelName().toLowerCase());
            if (res == null || res.path == null || res.path.isBlank()) {
                return new Geometry("ShaderPreviewBox", new Box(0.95f, 0.95f, 0.95f));
            }
            Spatial model = assetManager.loadModel(res.path);
            model.scale(res.scaleX, res.scaleY, res.scaleZ);
            model.setLocalTranslation(res.localTranslationX, res.localTranslationY, res.localTranslationZ);
            model.rotate(0f, res.rotateY * FastMath.DEG_TO_RAD, 0f);
            return model;
        } catch (Exception ex) {
            ex.printStackTrace();
            return new Geometry("ShaderPreviewBox", new Box(0.95f, 0.95f, 0.95f));
        }
    }

    private void applyMaterialToPreview(String assetBase, RenderQueue.Bucket bucket) {
        if (previewSpatial == null || assetBase == null || assetBase.isBlank()) {
            return;
        }
        previewContentNode.setQueueBucket(bucket);
        applyMaterialRecursive(previewContentNode, assetBase, bucket);
    }

    private void applyMaterialRecursive(Spatial spatial, String assetBase, RenderQueue.Bucket bucket) {
        spatial.setQueueBucket(bucket);
        if (spatial instanceof Geometry) {
            Geometry geometry = (Geometry) spatial;
            Material sourceMaterial = originalPreviewMaterials.get(geometry);
            Material material = buildPreviewMaterial(assetBase, sourceMaterial);
            if (material != null) {
                geometry.setMaterial(material);
            }
            return;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                applyMaterialRecursive(child, assetBase, bucket);
            }
        }
    }

    private void applyPreviewScale() {
        if (previewSpatial == null || currentDocument == null) {
            return;
        }
        float scale = Math.max(0.1f, currentDocument.getPreviewScale());
        Vector3f baseScale = previewSpatial.getLocalScale().clone();
        previewSpatial.setLocalScale(baseScale.mult(scale));
    }

    private Material buildPreviewMaterial(String assetBase, Material sourceMaterial) {
        Material shaderTemplate;
        try {
            shaderTemplate = assetManager.loadMaterial(assetBase + ".j3m");
        } catch (Exception ex) {
            shaderTemplate = null;
        }
        Material material = new Material(assetManager, assetBase + ".j3md");
        copyAllParams(shaderTemplate, material);
        applyShaderMaterialDefaultsFromAsset(assetBase, material);
        material.setColor("MainColor", currentDocument.getMainColor());
        material.setFloat("Time", elapsedTime);
        material.setFloat("GlowStrength", currentDocument.getGlowStrength());
        material.setFloat("PulseSpeed", currentDocument.getPulseSpeed());
        material.setFloat("Transparency", currentDocument.getTransparency());
        material.setFloat("EdgeWidth", currentDocument.getEdgeWidth());
        material.setFloat("ScrollSpeed", currentDocument.getScrollSpeed());
        material.setBoolean("UseOriginalTexture", currentDocument.isUseOriginalTexture());

        if (currentDocument.getTexturePath() != null && !currentDocument.getTexturePath().isBlank()) {
            Texture texture = assetManager.loadTexture(currentDocument.getTexturePath());
            texture.setWrap(Texture.WrapMode.Repeat);
            material.setTexture("ColorMap", texture);
        }

        if (sourceMaterial != null) {
            if (currentDocument.isUseOriginalTexture()) {
                copyTextureParamIfPresent(sourceMaterial, material, "ColorMap", "ColorMap");
                copyTextureParamIfPresent(sourceMaterial, material, "DiffuseMap", "ColorMap");
                copyTextureParamIfPresent(sourceMaterial, material, "Texture", "ColorMap");
                copyFirstTextureParamIfPresent(sourceMaterial, material, "ColorMap");
            }
            copyParamIfPresent(sourceMaterial, material, "VertexColor", "VertexColor");
        }

        ensureDefaultColorParam(material, "MainColor", ColorRGBA.White);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        previewShaderMaterials.add(material);
        return material;
    }

    private void applyShaderMaterialDefaultsFromAsset(String assetBase, Material shaderMaterial) {
        if (assetBase == null || shaderMaterial == null) {
            return;
        }

        String j3mPath = assetBase + ".j3m";
        try {
            AssetInfo assetInfo = assetManager.locateAsset(new AssetKey(j3mPath));
            if (assetInfo == null) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(assetInfo.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    applyShaderMaterialDefaultLine(shaderMaterial, line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void applyShaderMaterialDefaultLine(Material material, String line) {
        if (material == null || line == null) {
            return;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("Material ") || trimmed.equals("{") || trimmed.equals("}")) {
            return;
        }

        int sep = trimmed.indexOf(':');
        if (sep <= 0) {
            return;
        }

        String paramName = trimmed.substring(0, sep).trim();
        String rawValue = trimmed.substring(sep + 1).trim();
        if (paramName.isEmpty() || rawValue.isEmpty()) {
            return;
        }

        MaterialDef def = material.getMaterialDef();
        if (def == null || def.getMaterialParam(paramName) == null) {
            return;
        }

        VarType varType = def.getMaterialParam(paramName).getVarType();
        try {
            if (varType == VarType.Float) {
                material.setFloat(paramName, Float.parseFloat(rawValue));
            } else if (varType == VarType.Boolean) {
                material.setBoolean(paramName, Boolean.parseBoolean(rawValue));
            } else if (varType == VarType.Vector4 || varType == VarType.Vector4Array) {
                String[] parts = rawValue.split("\\s+");
                if (parts.length >= 4) {
                    material.setColor(paramName, new ColorRGBA(
                            Float.parseFloat(parts[0]),
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                    ));
                }
            } else if (varType == VarType.Texture2D && !rawValue.isEmpty()) {
                material.setTexture(paramName, assetManager.loadTexture(rawValue));
            }
        } catch (Exception ignored) {
        }
    }

    private void copyAllParams(Material sourceMaterial, Material targetMaterial) {
        if (sourceMaterial == null || targetMaterial == null) {
            return;
        }

        for (MatParam param : sourceMaterial.getParams()) {
            if (param == null || param.getValue() == null) {
                continue;
            }

            try {
                VarType varType = param.getVarType();
                if (varType != null) {
                    targetMaterial.setParam(param.getName(), varType, param.getValue());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void captureOriginalMaterials(Spatial spatial) {
        if (spatial instanceof Geometry) {
            Geometry geometry = (Geometry) spatial;
            if (geometry.getMaterial() != null) {
                originalPreviewMaterials.put(geometry, geometry.getMaterial().clone());
            }
            return;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                captureOriginalMaterials(child);
            }
        }
    }

    private void applyFallbackMaterialRecursive(Spatial spatial, Material material, RenderQueue.Bucket bucket) {
        spatial.setQueueBucket(bucket);
        if (spatial instanceof Geometry) {
            ((Geometry) spatial).setMaterial(material);
            return;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                applyFallbackMaterialRecursive(child, material, bucket);
            }
        }
    }

    private void copyTextureParamIfPresent(Material sourceMaterial, Material targetMaterial, String fromName, String toName) {
        MaterialDef targetDef = targetMaterial.getMaterialDef();
        if (targetDef == null || targetDef.getMaterialParam(toName) == null) {
            return;
        }

        MatParam param = sourceMaterial.getParam(fromName);
        if (param instanceof MatParamTexture) {
            targetMaterial.setTexture(toName, ((MatParamTexture) param).getTextureValue());
        }
    }

    private void copyFirstTextureParamIfPresent(Material sourceMaterial, Material targetMaterial, String toName) {
        MaterialDef targetDef = targetMaterial.getMaterialDef();
        if (targetDef == null || targetDef.getMaterialParam(toName) == null || targetMaterial.getParam(toName) != null) {
            return;
        }

        for (MatParam param : sourceMaterial.getParams()) {
            if (param instanceof MatParamTexture) {
                targetMaterial.setTexture(toName, ((MatParamTexture) param).getTextureValue());
                return;
            }
        }
    }

    private void ensureDefaultColorParam(Material material, String paramName, ColorRGBA defaultColor) {
        if (material == null || defaultColor == null) {
            return;
        }

        MaterialDef targetDef = material.getMaterialDef();
        if (targetDef == null || targetDef.getMaterialParam(paramName) == null) {
            return;
        }

        if (material.getParam(paramName) == null) {
            material.setColor(paramName, defaultColor.clone());
        }
    }

    private void copyParamIfPresent(Material sourceMaterial, Material targetMaterial, String fromName, String toName) {
        MaterialDef targetDef = targetMaterial.getMaterialDef();
        if (targetDef == null || targetDef.getMaterialParam(toName) == null) {
            return;
        }

        MatParam sourceParam = sourceMaterial.getParam(fromName);
        if (sourceParam == null || sourceParam.getValue() == null || sourceParam.getVarType() == null) {
            return;
        }

        try {
            targetMaterial.setParam(toName, sourceParam.getVarType(), sourceParam.getValue());
        } catch (Exception ignored) {
        }
    }

    private void centerPreviewContent() {
        previewContentNode.setLocalTranslation(Vector3f.ZERO);
        previewContentNode.updateGeometricState();
        BoundingVolume bound = previewContentNode.getWorldBound();
        if (bound == null) {
            return;
        }
        Vector3f center = bound.getCenter().clone();
        previewContentNode.setLocalTranslation(center.negate());
        previewContentNode.updateGeometricState();
    }

    private void attachPreviewEntity(ShaderPreviewTarget target) {
        DesignerEntityType entityType = target == ShaderPreviewTarget.MODEL
                ? DesignerEntityType.MODEL : DesignerEntityType.BOX;
        previewEntity = new DesignerEntity("preview_object", entityType);
        previewEntity.setSceneNode(previewContentNode);
        if (gizmoManager != null) {
            gizmoManager.onSelectionChanged(previewEntity);
            gizmoManager.setMode(GizmoMode.ROTATE);
        }
    }

    private void fitCameraToPreview() {
        previewContentNode.updateGeometricState();
        BoundingVolume bound = previewContentNode.getWorldBound();
        float radius = 1.5f;
        if (bound instanceof BoundingSphere) {
            radius = ((BoundingSphere) bound).getRadius();
        } else if (bound instanceof BoundingBox) {
            radius = ((BoundingBox) bound).getExtent(null).length();
        }
        if (radius < 0.1f || Float.isNaN(radius)) {
            radius = 1.5f;
        }
        cameraTarget.set(Vector3f.ZERO);
        cameraDistance = FastMath.clamp(radius * 2.8f, 2.5f, 80f);
        updateOrbitCamera();
    }

    private void updateOrbitCamera() {
        float x = cameraTarget.x + cameraDistance * FastMath.cos(cameraPitch) * FastMath.sin(cameraYaw);
        float y = cameraTarget.y + cameraDistance * FastMath.sin(cameraPitch);
        float z = cameraTarget.z + cameraDistance * FastMath.cos(cameraPitch) * FastMath.cos(cameraYaw);
        cam.setLocation(new Vector3f(x, y, z));
        cam.lookAt(cameraTarget, Vector3f.UNIT_Y);
    }
}
