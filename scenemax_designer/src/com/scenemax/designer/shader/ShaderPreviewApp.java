package com.scenemax.designer.shader;

import com.jme3.app.SimpleApplication;
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
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.CameraNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;
import com.scenemax.designer.gizmo.GizmoManager;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.gizmo.RotateGizmo;
import com.scenemax.designer.gizmo.TranslateGizmo;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;

import java.io.File;

public class ShaderPreviewApp extends SimpleApplication {

    private final Node pivotNode = new Node("ShaderPreviewPivot");
    private final Node previewContentNode = new Node("ShaderPreviewContent");
    private Spatial previewSpatial;
    private ShaderDocument currentDocument;
    private File currentFile;
    private String resourcesFolder;
    private boolean locatorRegistered = false;
    private boolean defaultLocatorRegistered = false;
    private Material currentMaterial;
    private float elapsedTime = 0f;
    private AssetsMapping assetsMapping;
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
    private ShaderPreviewTarget loadedPreviewTarget = null;
    private String loadedPreviewModelName = null;

    private static final String ACTION_LEFT_CLICK = "ShaderPreviewLeftClick";
    private static final String ACTION_RIGHT_CLICK = "ShaderPreviewRightClick";
    private static final String ACTION_MIDDLE_CLICK = "ShaderPreviewMiddleClick";
    private static final String ACTION_SCROLL_UP = "ShaderPreviewScrollUp";
    private static final String ACTION_SCROLL_DOWN = "ShaderPreviewScrollDown";

    @Override
    public void simpleInitApp() {
        viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.08f, 0.12f, 1f));
        flyCam.setEnabled(false);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(pivotNode);
        pivotNode.attachChild(previewContentNode);
        addLights();
        initGizmos();
        registerInputMappings();
        rebuildGeometry(ShaderPreviewTarget.BOX);
        updateOrbitCamera();
    }

    @Override
    public void simpleUpdate(float tpf) {
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
        if (currentMaterial != null) {
            currentMaterial.setFloat("Time", elapsedTime);
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

    private void addLights() {
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.7f, 0.75f, 0.9f, 1f));
        rootNode.addLight(ambient);

        DirectionalLight keyLight = new DirectionalLight();
        keyLight.setDirection(new Vector3f(-0.4f, -0.8f, -0.6f).normalizeLocal());
        keyLight.setColor(new ColorRGBA(1f, 0.97f, 0.9f, 1f));
        rootNode.addLight(keyLight);
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
        registerLocatorIfNeeded();
        ensurePreviewGeometry();

        if (resourcesFolder == null || resourcesFolder.isBlank()) {
            return;
        }

        try {
            currentDocument.exportRuntimeAssets(currentFile, resourcesFolder);
            assetManager.clearCache();

            String assetBase = ShaderDocument.getRuntimeAssetBase(currentFile);
            Material material = new Material(assetManager, assetBase + ".j3md");
            material.setColor("MainColor", currentDocument.getMainColor());
            material.setFloat("Time", elapsedTime);
            material.setFloat("GlowStrength", currentDocument.getGlowStrength());
            material.setFloat("PulseSpeed", currentDocument.getPulseSpeed());
            material.setFloat("Transparency", currentDocument.getTransparency());
            material.setFloat("EdgeWidth", currentDocument.getEdgeWidth());
            material.setFloat("ScrollSpeed", currentDocument.getScrollSpeed());

            if (currentDocument.getTexturePath() != null && !currentDocument.getTexturePath().isBlank()) {
                Texture texture = assetManager.loadTexture(currentDocument.getTexturePath());
                texture.setWrap(Texture.WrapMode.Repeat);
                material.setTexture("ColorMap", texture);
            }

            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            applyMaterialToPreview(material, RenderQueue.Bucket.Transparent);
            currentMaterial = material;
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
        applyMaterialToPreview(fallback, RenderQueue.Bucket.Opaque);
        currentMaterial = fallback;
    }

    private void registerLocatorIfNeeded() {
        try {
            if (!defaultLocatorRegistered) {
                File defaultRoot = new File("./resources");
                if (defaultRoot.exists()) {
                    assetManager.registerLocator(defaultRoot.getCanonicalPath(), FileLocator.class);
                }
                defaultLocatorRegistered = true;
            }

            if (!locatorRegistered && resourcesFolder != null && !resourcesFolder.isBlank()) {
                File root = new File(resourcesFolder);
                if (root.exists()) {
                    assetManager.registerLocator(root.getCanonicalPath(), FileLocator.class);
                    locatorRegistered = true;
                }
            }

            if (assetsMapping == null) {
                if (resourcesFolder != null && !resourcesFolder.isBlank()) {
                    assetsMapping = new AssetsMapping(new File(resourcesFolder).getCanonicalPath());
                } else {
                    assetsMapping = new AssetsMapping();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void rebuildGeometry(ShaderPreviewTarget target) {
        if (previewSpatial != null) {
            previewSpatial.removeFromParent();
        }
        previewContentNode.detachAllChildren();
        previewContentNode.setLocalRotation(Quaternion.IDENTITY);

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

        previewContentNode.attachChild(previewSpatial);
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

    private void applyMaterialToPreview(Material material, RenderQueue.Bucket bucket) {
        if (previewSpatial == null || material == null) {
            return;
        }
        previewContentNode.setQueueBucket(bucket);
        applyMaterialRecursive(previewContentNode, material, bucket);
    }

    private void applyMaterialRecursive(Spatial spatial, Material material, RenderQueue.Bucket bucket) {
        spatial.setQueueBucket(bucket);
        if (spatial instanceof Geometry) {
            ((Geometry) spatial).setMaterial(material);
            return;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                applyMaterialRecursive(child, material, bucket);
            }
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
