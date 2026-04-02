package com.scenemax.designer;

import com.jme3.asset.ModelKey;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.scenemax.designer.gizmo.*;
import com.scenemax.designer.grid.GridPlane;
import com.scenemax.designer.path.*;
import com.scenemax.designer.selection.OutlineEffect;
import com.scenemax.designer.selection.SelectionManager;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.projector.SceneMaxApp;
import com.scenemaxeng.projector.SceneMaxScope;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Core designer 3D application. Extends SceneMaxApp to leverage
 * the SceneMax runtime for entity creation via runPartialCode().
 * Adds designer extras: grid, orbit camera, selection, gizmos.
 */
public class DesignerApp extends SceneMaxApp {

    /**
     * Callback interface for notifying the Swing DesignerPanel of changes.
     */
    public interface DesignerPanelCallback {
        void onSelectionChanged(DesignerEntity entity);
        void onSceneChanged();
        void onLoadingProgress(int loaded, int total);
    }

    /**
     * Holds deferred entity info. After runPartialCode() is called,
     * the entity node won't exist until simpleUpdate() processes the
     * SceneMax controllers. This struct queues the lookup for later.
     */
    private static class PendingEntity {
        String name;
        String code;
        DesignerEntityType type;
        float radius;
        float sizeX, sizeY, sizeZ;
        float radiusTop, radiusBottom, cylinderHeight;
        float innerRadiusTop, innerRadiusBottom;
        float quadWidth, quadHeight;
        String resourcePath;
        boolean staticModel;
        boolean dynamicModel;
        boolean vehicleModel;
        boolean staticEntity;
        boolean colliderEntity;
        String material;
        String shader;
        boolean hidden;
        String shadowMode;
        String jointMapping;
        String nodeName;
        int framesWaited;
        boolean selectAfterCreation;
        // For document loading: saved transform to apply
        String entityId;
        Quaternion savedRotation;
        Vector3f savedScale;
        // Loading progress gizmo shown while async model is loading
        LoadingProgressGizmo loadingGizmo;
        // Target list to add the resolved entity to (entities list or a section's children)
        List<DesignerEntity> targetList;
        // Index within the target list to insert at (-1 = append)
        int insertIndex = -1;
    }

    private String designerProjectPath;
    private File designerFile;
    private DesignerDocument document;
    private DesignerPanelCallback panelCallback;
    private Runnable scriptsTreeRefreshCallback;
    private java.util.function.Consumer<String> codeFileUpdatedCallback;

    private final List<DesignerEntity> entities = new ArrayList<>();
    private final List<PendingEntity> pendingEntities = new ArrayList<>();
    private SelectionManager selectionManager;
    private GizmoManager gizmoManager;
    private OutlineEffect outlineEffect;
    private GridPlane gridPlane;
    private TranslateGizmo translateGizmo;
    private RotateGizmo rotateGizmo;
    private ViewCubeGizmo viewCubeGizmo;
    private DesignerEntity cameraEntity;
    private CameraGizmoVisual cameraGizmoVisual;
    private CameraPreviewViewport cameraPreview;

    // Path drawing and editing
    private PathDrawingMode pathDrawingMode;
    private PathEditGizmo pathEditGizmo;
    private int pathCounter = 0;
    // Map from PATH entity ID to its PathVisual node
    private final java.util.Map<String, PathVisual> pathVisuals = new java.util.HashMap<>();

    // Camera animation state (for view preset transitions)
    private boolean animatingCamera = false;
    private float animStartYaw, animStartPitch;
    private float animTargetYaw, animTargetPitch;
    private float animProgress;
    private static final float ANIM_DURATION = 0.4f;

    // Camera target animation state (for focus-on-entity)
    private boolean animatingCameraTarget = false;
    private Vector3f animStartTarget;
    private Vector3f animEndTarget;
    private float animTargetProgress;
    private float animStartDistance;
    private float animEndDistance;
    private static final float FOCUS_ANIM_DURATION = 0.5f;

    // Camera mode
    public enum CameraMode { ORBIT, PAN }
    private CameraMode cameraMode = CameraMode.ORBIT;

    // Camera orbit state
    private float cameraDistance = 15f;
    private float cameraYaw = (float) Math.toRadians(45);
    private float cameraPitch = (float) Math.toRadians(30);
    private Vector3f cameraTarget = new Vector3f(0, 0, 0);
    private boolean orbiting = false;
    private boolean panning = false;
    private boolean ctrlHeld = false;
    private Vector2f lastMousePos = new Vector2f();

    private int sphereCounter = 0;
    private int boxCounter = 0;
    private int cylinderCounter = 0;
    private int hollowCylinderCounter = 0;
    private int quadCounter = 0;
    private int modelCounter = 0;

    private boolean loadingDocument = false;
    private int loadingTotalEntities = 0;
    private List<String> loadingEntityOrder;  // entity IDs in document order

    // Input action names
    private static final String ACTION_LEFT_CLICK = "DesignerLeftClick";
    private static final String ACTION_RIGHT_CLICK = "DesignerRightClick";
    private static final String ACTION_MIDDLE_CLICK = "DesignerMiddleClick";
    private static final String ACTION_DELETE = "DesignerDelete";

    /** Max frames to wait for a pending entity node to appear */
    private static final int MAX_PENDING_FRAMES = 30;

    /** Max frames to wait for an async model (much longer than primitives) */
    private static final int MAX_PENDING_FRAMES_ASYNC = 600;

    public DesignerApp() {
        super();
    }

    public void setProjectPath(String projectPath) {
        this.designerProjectPath = projectPath;
    }

    public void setDesignerFile(File file) {
        this.designerFile = file;
    }

    public void setDocument(DesignerDocument document) {
        this.document = document;
    }

    public DesignerDocument getDocument() {
        return document;
    }

    // --- Orbit camera state accessors (for save/restore across document switches) ---

    public float getCameraDistance() { return cameraDistance; }
    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }
    public Vector3f getCameraTarget() { return cameraTarget.clone(); }

    public void setOrbitCameraState(float distance, float yaw, float pitch, Vector3f target) {
        this.cameraDistance = distance;
        this.cameraYaw = yaw;
        this.cameraPitch = pitch;
        this.cameraTarget.set(target);
        updateOrbitCamera();
    }

    public void setDesignerPanelCallback(DesignerPanelCallback callback) {
        this.panelCallback = callback;
    }

    /**
     * Sets a callback that will be invoked (on the Swing EDT) when a .code
     * file is created for the first time and the scripts tree needs refreshing.
     */
    public void setScriptsTreeRefreshCallback(Runnable callback) {
        this.scriptsTreeRefreshCallback = callback;
    }

    /**
     * Sets a callback that will be invoked (on the Swing EDT) whenever the
     * companion .code file is updated on disk.  The callback receives the
     * absolute path of the .code file so the UI can refresh open tabs.
     */
    public void setCodeFileUpdatedCallback(java.util.function.Consumer<String> callback) {
        this.codeFileUpdatedCallback = callback;
    }

    public List<DesignerEntity> getEntities() {
        return entities;
    }

    /**
     * Moves an entity from one index to another in the entity list,
     * affecting its order in the generated code.
     */
    public void reorderEntity(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= entities.size() || toIndex < 0 || toIndex >= entities.size()) {
            return;
        }
        DesignerEntity entity = entities.remove(fromIndex);
        entities.add(toIndex, entity);
        markDocumentDirty();
        notifySceneChanged();
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public void setGizmoMode(GizmoMode mode) {
        if (gizmoManager != null) gizmoManager.setMode(mode);
    }

    public void setCameraMode(CameraMode mode) {
        this.cameraMode = mode;
    }

    // --- Override SceneMaxApp lifecycle to prevent clearing scene on parse errors ---

    @Override
    public void onEndCode() {
        // No-op in designer mode - do NOT clear the scene
    }

    @Override
    public void onEndCode(List<String> errors) {
        // No-op in designer mode - just log errors
        if (errors != null && !errors.isEmpty()) {
            System.err.println("Designer parse errors: " + errors);
        }
    }

    @Override
    public void onStartCode() {
        // No-op in designer mode
    }

    @Override
    public void simpleInitApp() {
        // Set workingFolder for SceneMaxApp base init (asset locators, etc.)
        if (designerFile != null) {
            setWorkingFolder(designerFile.getParentFile().getAbsolutePath());
        } else if (designerProjectPath != null) {
            setWorkingFolder(designerProjectPath);
        }

        // Call SceneMaxApp's init - sets up asset management, lighting, physics, etc.
        super.simpleInitApp();

        // Reload AssetsMapping with the project's resources folder so that
        // ext models (models-ext.json) are included, same as MainApp does.
        // Also register the resources folder as an asset locator so the
        // JME asset manager can find model files (e.g. .gltf/.glb).
        String resourcesFolder = getProjectResourcesFolder();
        if (resourcesFolder != null) {
            assetsMapping = new AssetsMapping(resourcesFolder);
            try {
                File resDir = new File(resourcesFolder);
                if (resDir.exists()) {
                    assetManager.registerLocator(resDir.getCanonicalPath(),
                            com.jme3.asset.plugins.FileLocator.class);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Disable default flyCam (SceneMaxApp already does this, but be explicit)
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);

        // Initialize the SceneMax runtime (mainScope, ProgramDef, threads)
        // so we can use runPartialCode() to create entities
        String runtimePath = designerFile != null
                ? designerFile.getParentFile().getAbsolutePath()
                : designerProjectPath;
        initDesignerRuntime(runtimePath);

        // --- Designer extras ---

        // Grid plane
        gridPlane = new GridPlane(assetManager);
        rootNode.attachChild(gridPlane);

        // Selection system
        selectionManager = new SelectionManager();
        outlineEffect = new OutlineEffect(assetManager);

        // Gizmo system
        translateGizmo = new TranslateGizmo(assetManager);
        rootNode.attachChild(translateGizmo);
        rotateGizmo = new RotateGizmo(assetManager);
        rootNode.attachChild(rotateGizmo);
        gizmoManager = new GizmoManager(rootNode, translateGizmo, rotateGizmo);
        selectionManager.addListener(gizmoManager);

        // Path drawing mode and edit gizmo
        pathDrawingMode = new PathDrawingMode(rootNode, assetManager);
        pathDrawingMode.setCallback(new PathDrawingMode.PathDrawingCallback() {
            @Override
            public void onPathFinished(BezierPath path) {
                addPathEntity(path);
                if (panelCallback != null) panelCallback.onSceneChanged();
            }
            @Override
            public void onPathCancelled() {
                if (panelCallback != null) panelCallback.onSceneChanged();
            }
        });

        pathEditGizmo = new PathEditGizmo();
        pathEditGizmo.setEditCallback(entity -> {
            if (panelCallback != null) {
                panelCallback.onSelectionChanged(entity);
            }
            markDocumentDirty();
        });

        // ViewCube navigation gizmo (separate viewport, top-right corner)
        viewCubeGizmo = new ViewCubeGizmo();
        viewCubeGizmo.init(assetManager, renderManager, cam);
        viewCubeGizmo.setCallback((targetYaw, targetPitch) -> {
            startCameraAnimation(targetYaw, targetPitch);
        });

        // When a drag ends, update the properties panel with new transform values
        gizmoManager.setDragEndCallback(entity -> {
            if (panelCallback != null) {
                panelCallback.onSelectionChanged(entity);
            }
            markDocumentDirty();
        });

        // Selection listeners -> notify Swing panel + path edit gizmo
        selectionManager.addListener(entity -> {
            outlineEffect.removeOutline();
            if (entity != null && entity.getSceneNode() != null) {
                outlineEffect.applyOutline(entity.getSceneNode());
            }
            // Handle PATH edit gizmo activation/deactivation
            if (entity != null && entity.getType() == DesignerEntityType.PATH) {
                PathVisual visual = pathVisuals.get(entity.getId());
                pathEditGizmo.attach(entity, visual);
                // Hide standard gizmos for PATH entities
                gizmoManager.setMode(gizmoManager.getMode()); // force hide
            } else {
                pathEditGizmo.detach();
            }
            if (panelCallback != null) {
                panelCallback.onSelectionChanged(entity);
            }
        });

        // Register designer input mappings
        registerDesignerInputMappings();

        // Camera gizmo entity (singleton - always exists, cannot be deleted)
        initCameraEntity();

        // Camera preview viewport (offscreen rendering for live preview, always visible)
        cameraPreview = new CameraPreviewViewport();
        cameraPreview.init(assetManager, renderManager, rootNode,
                           cam.getWidth(), cam.getHeight());
        cameraPreview.syncWithEntity(cameraEntity);
        cameraPreview.show(guiNode);

        // Load document entities if present (deferred - nodes appear after simpleUpdate)
        if (document != null) {
            loadDocumentEntities();
        }

        // Set initial camera
        updateOrbitCamera();
    }

    // --- Entity management via SceneMax language ---

    /** Creates a sphere using SceneMax language: name => sphere : pos (x,y,z), radius r */
    public void addDefaultSphere() {
        addDefaultSphere(null, -1);
    }

    /** Creates a sphere at a specific position in a target list. */
    public void addDefaultSphere(List<DesignerEntity> targetList, int insertIndex) {
        String name = "sphere_" + (++sphereCounter);
        String code = name + " => sphere : pos (0,0.5,0), radius 0.5";
        addEntityViaCode(name, code, DesignerEntityType.SPHERE, 0.5f, 0, 0, 0, null, false, false, false, false, false, targetList, insertIndex);
    }

    /** Creates a box using SceneMax language: name => box : size (x,y,z), pos (x,y,z) */
    public void addDefaultBox() {
        addDefaultBox(null, -1);
    }

    /** Creates a box at a specific position in a target list. */
    public void addDefaultBox(List<DesignerEntity> targetList, int insertIndex) {
        String name = "box_" + (++boxCounter);
        String code = name + " => box : size (1,1,1), pos (0,0.5,0)";
        addEntityViaCode(name, code, DesignerEntityType.BOX, 0, 0.5f, 0.5f, 0.5f, null, false, false, false, false, false, targetList, insertIndex);
    }

    /** Creates a cylinder using SceneMax language: name => cylinder : radius (top, bottom), height h, pos (x,y,z) */
    public void addDefaultCylinder() {
        addDefaultCylinder(null, -1);
    }

    /** Creates a cylinder at a specific position in a target list. */
    public void addDefaultCylinder(List<DesignerEntity> targetList, int insertIndex) {
        String name = "cylinder_" + (++cylinderCounter);
        String code = name + " => cylinder : radius (1,1), height 2, pos (0,1,0)";
        addEntityViaCode(name, code, DesignerEntityType.CYLINDER, 0, 0, 0, 0, null, false, false, false, false, false, targetList, insertIndex);
    }

    /** Creates a hollow cylinder using SceneMax language. */
    public void addDefaultHollowCylinder() {
        addDefaultHollowCylinder(null, -1);
    }

    /** Creates a hollow cylinder at a specific position in a target list. */
    public void addDefaultHollowCylinder(List<DesignerEntity> targetList, int insertIndex) {
        String name = "hcylinder_" + (++hollowCylinderCounter);
        String code = name + " => hollow cylinder : radius (1,1), inner radius (0.5,0.5), height 2, pos (0,1,0)";
        addEntityViaCode(name, code, DesignerEntityType.HOLLOW_CYLINDER, 0, 0, 0, 0, null, false, false, false, false, false, targetList, insertIndex);
    }

    /** Creates a quad using SceneMax language: name => quad : size (w, h), pos (x,y,z) */
    public void addDefaultQuad() {
        addDefaultQuad(null, -1);
    }

    /** Creates a quad at a specific position in a target list. */
    public void addDefaultQuad(List<DesignerEntity> targetList, int insertIndex) {
        String name = "quad_" + (++quadCounter);
        String code = name + " => quad : size (1,1), pos (0,0.5,0)";
        addEntityViaCode(name, code, DesignerEntityType.QUAD, 0, 0, 0, 0, null, false, false, false, false, false, targetList, insertIndex);
    }

    /** Creates a 3D model using SceneMax language: name => [static|dynamic] resourceName : pos (x,y,z) */
    public void addModel(String resourceName, boolean isStatic) {
        addModel(resourceName, isStatic, false, false);
    }

    public void addModel(String resourceName, boolean isStatic, boolean isDynamic, boolean isVehicle) {
        addModel(resourceName, isStatic, isDynamic, isVehicle, null, -1);
    }

    /** Creates a 3D model at a specific position in a target list. */
    public void addModel(String resourceName, boolean isStatic, boolean isDynamic, boolean isVehicle,
                         List<DesignerEntity> targetList, int insertIndex) {
        String name = "model_" + (++modelCounter);
        String prefix = isStatic ? "static " : isDynamic ? "dynamic " : "";
        String vehicleSuffix = isVehicle ? " vehicle" : "";
        float initialY = isVehicle ? 5f : 0f;
        String code = name + " => " + prefix + resourceName + vehicleSuffix + ": pos (0," + initialY + ",0) async";
        addEntityViaCode(name, code, DesignerEntityType.MODEL, 0, 0, 0, 0, resourceName, isStatic, isDynamic, isVehicle, false, false, targetList, insertIndex);

        // Apply the model's configured scale from ResourceSetup instead of defaulting to 1
        if (getAssetsMapping() != null && getAssetsMapping().get3DModelsIndex() != null) {
            ResourceSetup res = getAssetsMapping().get3DModelsIndex().get(resourceName.toLowerCase());
            if (res != null) {
                PendingEntity pe = pendingEntities.get(pendingEntities.size() - 1);
                pe.savedScale = new Vector3f(res.scaleX, res.scaleY, res.scaleZ);
            }
        }
    }

    /**
     * Adds a code node at the given position in the entity list.
     * Code nodes have no 3D representation; they inject user code
     * into the generated .code file at their position in the hierarchy.
     *
     * @param name        display name for the code node
     * @param insertIndex index in the entity list to insert at (-1 = end)
     * @return the created code node entity
     */
    public DesignerEntity addCodeNode(String name, int insertIndex) {
        return addCodeNode(name, null, insertIndex);
    }

    /**
     * Adds a code node at the given position in a target list.
     *
     * @param name        display name for the code node
     * @param targetList  the list to insert into (null = top-level entities)
     * @param insertIndex index in the target list to insert at (-1 = end)
     * @return the created code node entity
     */
    public DesignerEntity addCodeNode(String name, List<DesignerEntity> targetList, int insertIndex) {
        DesignerEntity codeEntity = new DesignerEntity(name, DesignerEntityType.CODE);
        codeEntity.setCodeText("// " + name + "\n");
        List<DesignerEntity> target = (targetList != null) ? targetList : entities;
        if (insertIndex >= 0 && insertIndex < target.size()) {
            target.add(insertIndex, codeEntity);
        } else {
            target.add(codeEntity);
        }
        markDocumentDirty();
        notifySceneChanged();
        return codeEntity;
    }

    /**
     * Adds a section node at the given position in the entity list.
     * Section nodes group other entities for organizational purposes
     * and control code generation order.
     *
     * @param name        display name for the section
     * @param insertIndex index in the entity list to insert at (-1 = end)
     * @return the created section entity
     */
    public DesignerEntity addSectionNode(String name, int insertIndex) {
        DesignerEntity section = new DesignerEntity(name, DesignerEntityType.SECTION);
        if (insertIndex >= 0 && insertIndex < entities.size()) {
            entities.add(insertIndex, section);
        } else {
            entities.add(section);
        }
        markDocumentDirty();
        notifySceneChanged();
        return section;
    }

    // --- Path entity management ---

    /** Enters path drawing mode. Called from toolbar button. */
    public void enterPathMode() {
        if (pathDrawingMode != null && !pathDrawingMode.isActive()) {
            selectionManager.deselect();
            pathDrawingMode.enter();
        }
    }

    /** Exits path drawing mode without creating a path. */
    public void exitPathMode() {
        if (pathDrawingMode != null && pathDrawingMode.isActive()) {
            pathDrawingMode.cancel();
        }
    }

    /** Returns whether path drawing mode is active. */
    public boolean isPathDrawingActive() {
        return pathDrawingMode != null && pathDrawingMode.isActive();
    }

    /**
     * Creates a PATH entity from a finished BezierPath and adds it to the scene.
     */
    private void addPathEntity(BezierPath path) {
        String name = "path_" + (++pathCounter);

        DesignerEntity entity = new DesignerEntity(name, DesignerEntityType.PATH);
        entity.setBezierPath(path);

        // Create the visual node
        PathVisual visual = new PathVisual(assetManager);
        visual.rebuild(path);
        rootNode.attachChild(visual);

        // Create a wrapper node for the entity (needed for getPosition() etc.)
        Node pathNode = new Node(name);
        // Set position to first control point
        if (path.getPointCount() > 0) {
            pathNode.setLocalTranslation(path.getPoint(0).getPosition());
        }
        entity.setSceneNode(pathNode);
        rootNode.attachChild(pathNode);

        // Track the visual
        pathVisuals.put(entity.getId(), visual);

        entities.add(entity);
        markDocumentDirty();
        selectionManager.select(entity);
        notifySceneChanged();
    }

    /**
     * Rebuilds the PathVisual for a given PATH entity after editing.
     */
    public void rebuildPathVisual(DesignerEntity entity) {
        if (entity == null || entity.getType() != DesignerEntityType.PATH) return;
        PathVisual visual = pathVisuals.get(entity.getId());
        if (visual != null && entity.getBezierPath() != null) {
            visual.rebuild(entity.getBezierPath());
        }
    }

    /** Returns the PathEditGizmo for external access (e.g. from DesignerPanel context menus). */
    public PathEditGizmo getPathEditGizmo() { return pathEditGizmo; }

    /**
     * Moves an entity into a section node as a child at the given index.
     * Removes the entity from its current location first.
     */
    public void moveEntityToSection(DesignerEntity entity, DesignerEntity section, int childIndex) {
        if (entity == null || section == null || section.getType() != DesignerEntityType.SECTION) return;
        // Remove from top-level list
        entities.remove(entity);
        // Remove from any other section
        removeEntityFromAllSections(entity, entities);
        // Add to target section
        if (childIndex >= 0 && childIndex <= section.getChildren().size()) {
            section.addChild(childIndex, entity);
        } else {
            section.addChild(entity);
        }
        markDocumentDirty();
        notifySceneChanged();
    }

    /**
     * Moves an entity out of a section back to the top-level entity list.
     */
    public void moveEntityToTopLevel(DesignerEntity entity, int targetIndex) {
        if (entity == null) return;
        // Remove from any section
        removeEntityFromAllSections(entity, entities);
        // Remove from top-level in case it's there
        entities.remove(entity);
        if (targetIndex >= 0 && targetIndex <= entities.size()) {
            entities.add(targetIndex, entity);
        } else {
            entities.add(entity);
        }
        markDocumentDirty();
        notifySceneChanged();
    }

    /**
     * Recursively removes an entity from all section children lists.
     */
    private void removeEntityFromAllSections(DesignerEntity target, List<DesignerEntity> list) {
        for (DesignerEntity e : list) {
            if (e.getType() == DesignerEntityType.SECTION) {
                e.removeChild(target);
                removeEntityFromAllSections(target, e.getChildren());
            }
        }
    }

    /**
     * Recursively removes 3D scene nodes for all entities within a list
     * (used when deleting a section and its children).
     */
    private void removeSceneNodesRecursive(List<DesignerEntity> list) {
        for (DesignerEntity child : list) {
            if (child.getType() == DesignerEntityType.SECTION) {
                removeSceneNodesRecursive(child.getChildren());
            }
            if (child.getType() == DesignerEntityType.PATH) {
                PathVisual visual = pathVisuals.remove(child.getId());
                if (visual != null) visual.removeFromParent();
            }
            if (child.getSceneNode() != null) {
                child.getSceneNode().removeFromParent();
            }
        }
    }

    /**
     * Creates the singleton camera gizmo entity. This is not created via
     * SceneMax code -- the visual node is built directly.
     */
    private void initCameraEntity() {
        // Remove the previous camera gizmo node to avoid duplicates
        if (cameraEntity != null && cameraEntity.getSceneNode() != null) {
            cameraEntity.getSceneNode().removeFromParent();
        }

        cameraGizmoVisual = new CameraGizmoVisual(assetManager);
        Node cameraNode = new Node("GameCameraEntity");
        cameraNode.attachChild(cameraGizmoVisual);

        // Set initial position from document (or default)
        Vector3f initialPos = new Vector3f(0, 2, 5);
        Quaternion initialRot = Quaternion.IDENTITY.clone();
        if (document != null) {
            initialPos = document.getGameCameraPos().clone();
            initialRot = document.getGameCameraRot().clone();
        }
        cameraNode.setLocalTranslation(initialPos);
        cameraNode.setLocalRotation(initialRot);

        cameraEntity = new DesignerEntity("__game_camera__", "Game Camera", DesignerEntityType.CAMERA);
        cameraEntity.setSceneNode(cameraNode);
        rootNode.attachChild(cameraNode);

        // Add to entities list so it participates in selection/picking
        entities.add(cameraEntity);
    }

    /** Returns the project's resources folder path. */
    private String getProjectResourcesFolder() {
        if (designerProjectPath != null) {
            return designerProjectPath + "/resources";
        }
        if (designerFile != null) {
            return designerFile.getParentFile().getParent() + "/resources";
        }
        return null;
    }

    /**
     * Creates an AssetsMapping that includes both default and project-specific
     * extended models, the same way MainApp does via Util.getResourcesFolder().
     */
    private AssetsMapping createFullAssetsMapping() {
        String resourcesFolder = getProjectResourcesFolder();
        if (resourcesFolder != null) {
            return new AssetsMapping(resourcesFolder);
        }
        return new AssetsMapping();
    }

    /** Returns sorted list of available 3D model names, including project ext models. */
    public List<String> getAvailableModelNames() {
        AssetsMapping mapping = createFullAssetsMapping();
        List<String> names = new ArrayList<>();
        if (mapping.get3DModelsIndex() != null) {
            TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            mapping.get3DModelsIndex().values()
                    .forEach(r -> sorted.add(r.name));
            names.addAll(sorted);
        }
        return names;
    }

    /** Checks whether a model is a vehicle based on the assets mapping. */
    public boolean isModelVehicle(String modelName) {
        AssetsMapping mapping = createFullAssetsMapping();
        if (mapping.get3DModelsIndex() == null) return false;
        ResourceSetup res = mapping.get3DModelsIndex().get(modelName.toLowerCase());
        return res != null && res.isVehicle;
    }

    /** Checks whether a model is marked as static based on the assets mapping. */
    public boolean isModelStatic(String modelName) {
        AssetsMapping mapping = createFullAssetsMapping();
        if (mapping.get3DModelsIndex() == null) return false;
        ResourceSetup res = mapping.get3DModelsIndex().get(modelName.toLowerCase());
        return res != null && res.isStatic;
    }

    /** Returns sorted list of project shader names from resources/shaders/shaders-ext.json. */
    public List<String> getAvailableProjectShaderNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String resourcesFolder = getProjectResourcesFolder();
        if (resourcesFolder == null || resourcesFolder.isBlank()) {
            return new ArrayList<>();
        }

        File indexFile = new File(resourcesFolder, "shaders/shaders-ext.json");
        if (!indexFile.exists()) {
            return new ArrayList<>();
        }

        try {
            String content = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                return new ArrayList<>();
            }

            JSONObject root = new JSONObject(content);
            JSONArray shaders = root.optJSONArray("shaders");
            if (shaders == null) {
                return new ArrayList<>();
            }

            for (int i = 0; i < shaders.length(); i++) {
                JSONObject shader = shaders.optJSONObject(i);
                if (shader == null) {
                    continue;
                }
                String name = shader.optString("name", "").trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        } catch (Exception ex) {
            System.err.println("Failed to load project shaders list");
            ex.printStackTrace();
        }

        return new ArrayList<>(names);
    }

    public List<String> getAvailableProjectEnvironmentShaderNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String resourcesFolder = getProjectResourcesFolder();
        if (resourcesFolder == null || resourcesFolder.isBlank()) {
            return new ArrayList<>();
        }

        File indexFile = new File(resourcesFolder, "environment_shaders/environment-shaders-ext.json");
        if (!indexFile.exists()) {
            return new ArrayList<>();
        }

        try {
            String content = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                return new ArrayList<>();
            }

            JSONObject root = new JSONObject(content);
            JSONArray shaders = root.optJSONArray("environmentShaders");
            if (shaders == null) {
                return new ArrayList<>();
            }

            for (int i = 0; i < shaders.length(); i++) {
                JSONObject shader = shaders.optJSONObject(i);
                if (shader == null) {
                    continue;
                }
                String name = shader.optString("name", "").trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        } catch (Exception ex) {
            System.err.println("Failed to load project environment shaders list");
            ex.printStackTrace();
        }

        return new ArrayList<>(names);
    }

    /**
     * Executes SceneMax code to create an entity and queues a deferred lookup.
     * The node won't exist until simpleUpdate() runs the SceneMax controllers,
     * so we queue the info and check for the node each frame in simpleUpdate().
     */
    private void addEntityViaCode(String name, String code, DesignerEntityType type,
                                   float radius, float sizeX, float sizeY, float sizeZ,
                                   String resourcePath, boolean isStatic, boolean isVehicle) {
        addEntityViaCode(name, code, type, radius, sizeX, sizeY, sizeZ, resourcePath, isStatic, false, isVehicle, false, false);
    }

    private void addEntityViaCode(String name, String code, DesignerEntityType type,
                                   float radius, float sizeX, float sizeY, float sizeZ,
                                   String resourcePath, boolean isStatic, boolean isDynamic, boolean isVehicle) {
        addEntityViaCode(name, code, type, radius, sizeX, sizeY, sizeZ, resourcePath, isStatic, isDynamic, isVehicle, false, false);
    }

    private void addEntityViaCode(String name, String code, DesignerEntityType type,
                                   float radius, float sizeX, float sizeY, float sizeZ,
                                   String resourcePath, boolean isStatic, boolean isDynamic, boolean isVehicle,
                                   boolean isStaticEntity, boolean isColliderEntity) {
        addEntityViaCode(name, code, type, radius, sizeX, sizeY, sizeZ, resourcePath, isStatic, isDynamic, isVehicle, isStaticEntity, isColliderEntity, null, -1);
    }

    private void addEntityViaCode(String name, String code, DesignerEntityType type,
                                   float radius, float sizeX, float sizeY, float sizeZ,
                                   String resourcePath, boolean isStatic, boolean isDynamic, boolean isVehicle,
                                   boolean isStaticEntity, boolean isColliderEntity,
                                   List<DesignerEntity> targetList, int insertIndex) {
        // Run the SceneMax code - this creates controllers that will be
        // processed in super.simpleUpdate() on the next frame(s)
        runPartialCode(code, null, false);

        // Build the expected node name
        SceneMaxScope scope = getMainScope();
        String nodeName = name + "@" + scope.scopeId;

        // Queue for deferred lookup
        PendingEntity pending = new PendingEntity();
        pending.name = name;
        pending.code = code;
        pending.type = type;
        pending.radius = radius;
        pending.sizeX = sizeX;
        pending.sizeY = sizeY;
        pending.sizeZ = sizeZ;
        pending.resourcePath = resourcePath;
        pending.staticModel = isStatic;
        pending.dynamicModel = isDynamic;
        pending.vehicleModel = isVehicle;
        pending.staticEntity = isStaticEntity;
        pending.colliderEntity = isColliderEntity;
        pending.nodeName = nodeName;
        pending.framesWaited = 0;

        // Set sensible defaults for type-specific fields so they don't stay at 0
        if (type == DesignerEntityType.CYLINDER) {
            pending.radiusTop = 1.0f;
            pending.radiusBottom = 1.0f;
            pending.cylinderHeight = 2.0f;
        } else if (type == DesignerEntityType.HOLLOW_CYLINDER) {
            pending.radiusTop = 1.0f;
            pending.radiusBottom = 1.0f;
            pending.innerRadiusTop = 0.5f;
            pending.innerRadiusBottom = 0.5f;
            pending.cylinderHeight = 2.0f;
        } else if (type == DesignerEntityType.QUAD) {
            pending.quadWidth = 1.0f;
            pending.quadHeight = 1.0f;
        }
        pending.selectAfterCreation = true;
        pending.targetList = targetList;
        pending.insertIndex = insertIndex;

        System.out.println("[TRACE] addEntityViaCode: name=" + name + ", type=" + type
                + ", targetList=" + (targetList != null ? "non-null (size=" + targetList.size() + ")" : "null")
                + ", insertIndex=" + insertIndex);

        // Show a loading progress gizmo for async model loading
        if (type == DesignerEntityType.MODEL) {
            attachLoadingGizmo(pending);
        }

        pendingEntities.add(pending);
    }

    /**
     * Creates and attaches a loading progress gizmo for a pending model entity.
     * The gizmo is placed slightly above the model's expected spawn position.
     */
    private void attachLoadingGizmo(PendingEntity pe) {
        LoadingProgressGizmo gizmo = new LoadingProgressGizmo(assetManager, pe.resourcePath);
        // Position slightly above the model's expected Y position
        float y = pe.vehicleModel ? 6f : 1f;
        gizmo.setLocalTranslation(0, y, 0);
        rootNode.attachChild(gizmo);
        pe.loadingGizmo = gizmo;
    }

    private static final String COLLIDER_WIREFRAME_KEY = "ColliderWireframe";

    /**
     * Attaches a wireframe geometry to a collider entity's node so it is
     * visible in the designer even though colliders have no runtime geometry.
     * Uses a semi-transparent green wireframe to distinguish colliders from
     * regular objects.
     */
    private void attachColliderWireframe(Node node, DesignerEntityType type,
                                          float sizeX, float sizeY, float sizeZ, float radius) {
        // Remove any existing collider wireframe first
        Spatial existing = node.getChild(COLLIDER_WIREFRAME_KEY);
        if (existing != null) existing.removeFromParent();

        Geometry wireGeo;
        switch (type) {
            case BOX:
                wireGeo = new Geometry(COLLIDER_WIREFRAME_KEY, new Box(sizeX * 2, sizeY * 2, sizeZ * 2));
                break;
            case SPHERE:
                wireGeo = new Geometry(COLLIDER_WIREFRAME_KEY, new Sphere(16, 16, radius));
                break;
            case CYLINDER:
                wireGeo = new Geometry(COLLIDER_WIREFRAME_KEY,
                        new com.jme3.scene.shape.Cylinder(16, 16, radius, radius, sizeY * 2, true, false));
                break;
            case HOLLOW_CYLINDER:
                wireGeo = new Geometry(COLLIDER_WIREFRAME_KEY,
                        new com.jme3.scene.shape.Cylinder(16, 16, radius, radius, sizeY * 2, true, false));
                break;
            default:
                return;
        }

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0f, 1f, 0.4f, 1f));
        mat.getAdditionalRenderState().setWireframe(true);
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        wireGeo.setMaterial(mat);
        node.attachChild(wireGeo);
    }

    /**
     * Called each frame from simpleUpdate() to check if pending entity nodes
     * have been created by the SceneMax controllers.
     */
    private void processPendingEntities() {
        if (pendingEntities.isEmpty()) return;

        boolean anyResolved = false;
        Iterator<PendingEntity> it = pendingEntities.iterator();
        while (it.hasNext()) {
            PendingEntity pe = it.next();
            pe.framesWaited++;

            Node node = (Node) rootNode.getChild(pe.nodeName);
            if (node != null) {
                // Entity node was created - register it
                DesignerEntity entity;
                if (pe.entityId != null) {
                    entity = new DesignerEntity(pe.entityId, pe.name, pe.type);
                } else {
                    entity = new DesignerEntity(pe.name, pe.type);
                }
                entity.setSceneMaxCode(pe.code);
                entity.setSceneNode(node);

                // Store type-specific properties
                switch (pe.type) {
                    case SPHERE:
                        entity.setRadius(pe.radius);
                        entity.setStaticEntity(pe.staticEntity);
                        entity.setColliderEntity(pe.colliderEntity);
                        entity.setMaterial(pe.material != null ? pe.material : "");
                        entity.setShader(pe.shader != null ? pe.shader : "");
                        entity.setHidden(pe.hidden);
                        entity.setShadowMode(pe.shadowMode);
                        break;
                    case BOX:
                        entity.setSizeX(pe.sizeX);
                        entity.setSizeY(pe.sizeY);
                        entity.setSizeZ(pe.sizeZ);
                        entity.setStaticEntity(pe.staticEntity);
                        entity.setColliderEntity(pe.colliderEntity);
                        entity.setMaterial(pe.material != null ? pe.material : "");
                        entity.setShader(pe.shader != null ? pe.shader : "");
                        entity.setHidden(pe.hidden);
                        entity.setShadowMode(pe.shadowMode);
                        break;
                    case CYLINDER:
                        entity.setRadiusTop(pe.radiusTop);
                        entity.setRadiusBottom(pe.radiusBottom);
                        entity.setHeight(pe.cylinderHeight);
                        entity.setStaticEntity(pe.staticEntity);
                        entity.setColliderEntity(pe.colliderEntity);
                        entity.setMaterial(pe.material != null ? pe.material : "");
                        entity.setShader(pe.shader != null ? pe.shader : "");
                        entity.setHidden(pe.hidden);
                        entity.setShadowMode(pe.shadowMode);
                        break;
                    case HOLLOW_CYLINDER:
                        entity.setRadiusTop(pe.radiusTop);
                        entity.setRadiusBottom(pe.radiusBottom);
                        entity.setInnerRadiusTop(pe.innerRadiusTop);
                        entity.setInnerRadiusBottom(pe.innerRadiusBottom);
                        entity.setHeight(pe.cylinderHeight);
                        entity.setStaticEntity(pe.staticEntity);
                        entity.setColliderEntity(pe.colliderEntity);
                        entity.setMaterial(pe.material != null ? pe.material : "");
                        entity.setShader(pe.shader != null ? pe.shader : "");
                        entity.setHidden(pe.hidden);
                        entity.setShadowMode(pe.shadowMode);
                        break;
                    case QUAD:
                        entity.setQuadWidth(pe.quadWidth);
                        entity.setQuadHeight(pe.quadHeight);
                        entity.setStaticEntity(pe.staticEntity);
                        entity.setColliderEntity(pe.colliderEntity);
                        entity.setMaterial(pe.material != null ? pe.material : "");
                        entity.setShader(pe.shader != null ? pe.shader : "");
                        entity.setHidden(pe.hidden);
                        entity.setShadowMode(pe.shadowMode);
                        break;
                    case MODEL:
                        entity.setResourcePath(pe.resourcePath);
                        entity.setStaticModel(pe.staticModel);
                        entity.setDynamicModel(pe.dynamicModel);
                        entity.setVehicleModel(pe.vehicleModel);
                        entity.setHidden(pe.hidden);
                        entity.setShader(pe.shader != null ? pe.shader : "");
                        entity.setShadowMode(pe.shadowMode);
                        entity.setJointMapping(pe.jointMapping != null ? pe.jointMapping : "");
                        break;
                }

                // Attach wireframe decorator for collider entities so they
                // are visible in the designer (colliders have no runtime geometry)
                if (pe.colliderEntity) {
                    attachColliderWireframe(node, pe.type, pe.sizeX, pe.sizeY, pe.sizeZ, pe.radius);
                }

                // Apply saved transform (for document loading)
                if (pe.savedRotation != null) {
                    node.setLocalRotation(pe.savedRotation);
                }
                if (pe.savedScale != null) {
                    node.setLocalScale(pe.savedScale);
                }

                // Add to the correct target list (top-level or section children)
                List<DesignerEntity> target = pe.targetList != null ? pe.targetList : entities;
                boolean isTopLevel = (target == entities);
                System.out.println("[TRACE] processPendingEntities resolved: name=" + pe.name
                        + ", targetList=" + (pe.targetList != null ? "non-null" : "null")
                        + ", isTopLevel=" + isTopLevel
                        + ", insertIndex=" + pe.insertIndex
                        + ", target.size()=" + target.size()
                        + ", entities.size()=" + entities.size());
                if (pe.insertIndex >= 0 && pe.insertIndex <= target.size()) {
                    target.add(pe.insertIndex, entity);
                } else {
                    target.add(entity);
                }
                System.out.println("[TRACE] After add: target.size()=" + target.size()
                        + ", entities.size()=" + entities.size());

                if (pe.selectAfterCreation) {
                    markDocumentDirty();
                    selectionManager.select(entity);
                }

                // Remove loading gizmo if present
                if (pe.loadingGizmo != null) {
                    pe.loadingGizmo.removeFromParent();
                    pe.loadingGizmo = null;
                }

                anyResolved = true;
                it.remove();

                // Notify loading progress during document loading
                if (loadingDocument) {
                    int loaded = loadingTotalEntities - pendingEntities.size();
                    notifyLoadingProgress(loaded, loadingTotalEntities);
                }
            } else {
                // Update loading gizmo progress for models still loading
                if (pe.loadingGizmo != null) {
                    int maxFrames = (pe.type == DesignerEntityType.MODEL) ? MAX_PENDING_FRAMES_ASYNC : MAX_PENDING_FRAMES;
                    // Use a fast-start curve so the bar fills quickly at first,
                    // then slows down, giving the user a sense of progress
                    float ratio = (float) pe.framesWaited / maxFrames;
                    float progress = 1.0f - (1.0f - ratio) * (1.0f - ratio);
                    pe.loadingGizmo.setProgress(Math.min(progress, 0.95f));
                    pe.loadingGizmo.faceCamera(cam);
                }
                // During document loading, use the longer async timeout for ALL
                // entity types because the CompositeController processes actions
                // sequentially — primitives queued after async models won't run
                // until all preceding models finish loading.
                int maxFrames = (pe.type == DesignerEntityType.MODEL || loadingDocument)
                        ? MAX_PENDING_FRAMES_ASYNC : MAX_PENDING_FRAMES;
                if (pe.framesWaited > maxFrames) {
                    System.err.println("Failed to create entity via code (timed out after "
                            + maxFrames + " frames): " + pe.code);
                    System.err.println("Expected node name: " + pe.nodeName);
                    if (pe.loadingGizmo != null) {
                        pe.loadingGizmo.removeFromParent();
                        pe.loadingGizmo = null;
                    }
                    it.remove();
                }
            }
        }

        // During document loading, skip intermediate tree refreshes — the
        // entities list is still being populated on this (JME) thread while
        // the Swing thread would try to read it.  A single definitive refresh
        // happens below once ALL entities have been resolved.
        if (anyResolved && !loadingDocument) {
            notifySceneChanged();
        }

        // If we were loading a document and all pending are resolved (or timed out), finalize
        if (loadingDocument && pendingEntities.isEmpty()) {
            loadingDocument = false;
            notifyLoadingProgress(loadingTotalEntities, loadingTotalEntities);

            // Restore original document order (CODE/SECTION nodes were added immediately,
            // while 3D entities were added asynchronously as their nodes appeared)
            if (loadingEntityOrder != null) {
                sortEntitiesByLoadOrder(entities, loadingEntityOrder);
                loadingEntityOrder = null;
            }

            // Now that all entities are in and sorted, refresh the tree once
            System.out.println("[JME] Loading complete. entities.size()=" + entities.size()
                    + " pendingEntities.size()=" + pendingEntities.size());
            for (DesignerEntity e : entities) {
                System.out.println("[JME]   " + e.getName() + " type=" + e.getType() + " collider=" + e.isColliderEntity());
            }
            notifySceneChanged();

            sphereCounter = (int) entities.stream()
                    .filter(e -> e.getType() == DesignerEntityType.SPHERE).count();
            boxCounter = (int) entities.stream()
                    .filter(e -> e.getType() == DesignerEntityType.BOX).count();
            modelCounter = (int) entities.stream()
                    .filter(e -> e.getType() == DesignerEntityType.MODEL).count();
            selectionManager.deselect();
        }
    }

    /** Updates the visual box mesh to match the entity's current size. */
    public void updateBoxMesh(DesignerEntity entity) {
        if (entity == null || entity.getType() != DesignerEntityType.BOX) return;
        Node node = entity.getSceneNode();
        if (node == null) return;
        if (entity.isColliderEntity()) {
            // Update the collider wireframe decorator
            attachColliderWireframe(node, DesignerEntityType.BOX,
                    entity.getSizeX(), entity.getSizeY(), entity.getSizeZ(), 0);
            return;
        }
        for (Spatial child : node.getChildren()) {
            if (child instanceof Geometry) {
                ((Geometry) child).setMesh(new Box(entity.getSizeX() * 2, entity.getSizeY() * 2, entity.getSizeZ() * 2));
                break;
            }
        }
    }

    /** Updates the visual sphere mesh to match the entity's current radius. */
    public void updateSphereMesh(DesignerEntity entity) {
        if (entity == null || entity.getType() != DesignerEntityType.SPHERE) return;
        Node node = entity.getSceneNode();
        if (node == null) return;
        if (entity.isColliderEntity()) {
            attachColliderWireframe(node, DesignerEntityType.SPHERE, 0, 0, 0, entity.getRadius());
            return;
        }
        for (Spatial child : node.getChildren()) {
            if (child instanceof Geometry) {
                ((Geometry) child).setMesh(new Sphere(16, 16, entity.getRadius()));
                break;
            }
        }
    }

    /** Updates the visual cylinder mesh to match the entity's current radii and height. */
    public void updateCylinderMesh(DesignerEntity entity) {
        if (entity == null || entity.getType() != DesignerEntityType.CYLINDER) return;
        Node node = entity.getSceneNode();
        if (node == null) return;
        if (entity.isColliderEntity()) {
            attachColliderWireframe(node, DesignerEntityType.CYLINDER,
                    0, 0, 0, Math.max(entity.getRadiusTop(), entity.getRadiusBottom()));
            return;
        }
        for (Spatial child : node.getChildren()) {
            if (child instanceof Geometry) {
                ((Geometry) child).setMesh(new com.jme3.scene.shape.Cylinder(
                        16, 32, entity.getRadiusTop(), entity.getRadiusBottom(), entity.getHeight(), true, false));
                break;
            }
        }
    }

    /** Updates the visual hollow cylinder mesh to match the entity's current radii and height. */
    public void updateHollowCylinderMesh(DesignerEntity entity) {
        if (entity == null || entity.getType() != DesignerEntityType.HOLLOW_CYLINDER) return;
        Node node = entity.getSceneNode();
        if (node == null) return;
        if (entity.isColliderEntity()) {
            attachColliderWireframe(node, DesignerEntityType.HOLLOW_CYLINDER,
                    0, 0, 0, Math.max(entity.getRadiusTop(), entity.getRadiusBottom()));
            return;
        }
        for (Spatial child : node.getChildren()) {
            if (child instanceof Geometry) {
                ((Geometry) child).setMesh(new com.scenemaxeng.projector.HollowCylinderMesh(
                        entity.getRadiusTop(), entity.getRadiusBottom(),
                        entity.getInnerRadiusTop(), entity.getInnerRadiusBottom(),
                        entity.getHeight()));
                break;
            }
        }
    }

    /** Applies a material to a BOX or SPHERE entity and updates the scene. */
    public void applyMaterial(DesignerEntity entity, String material) {
        if (entity == null) return;
        entity.setMaterial(material);
        if (material != null && !material.isEmpty()) {
            runPartialCode(entity.getName() + ".material = \"" + material + "\"", null, false);
        }
        markDocumentDirty();
    }

    /** Applies or clears a shader on a 3D entity and persists the change. */
    public void applyShader(DesignerEntity entity, String shader) {
        if (entity == null) return;
        String shaderName = shader != null ? shader : "";
        entity.setShader(shaderName);
        runPartialCode(entity.getName() + ".shader = \"" + shaderName + "\"", null, false);
        markDocumentDirty();
    }

    public void applySceneEnvironmentShader(String shader) {
        if (document == null) return;
        String shaderName = shader != null ? shader.trim() : "";
        document.setSceneEnvironmentShader(shaderName);
        runPartialCode("Scene.environment.shader = \"" + shaderName + "\"", null, false);
        markDocumentDirty();
    }

    public void removeEntity(DesignerEntity entity) {
        if (entity == null) return;
        if (entity.getType() == DesignerEntityType.CAMERA) return; // Camera cannot be deleted

        if (selectionManager.getSelected() == entity) {
            selectionManager.deselect();
        }

        outlineEffect.removeOutline();

        // For PATH entities, detach edit gizmo and remove visual
        if (entity.getType() == DesignerEntityType.PATH) {
            if (pathEditGizmo != null && pathEditGizmo.isAttached()) {
                pathEditGizmo.detach();
            }
            PathVisual visual = pathVisuals.remove(entity.getId());
            if (visual != null) {
                visual.removeFromParent();
            }
        }

        // For SECTION nodes, recursively remove 3D scene nodes of children
        if (entity.getType() == DesignerEntityType.SECTION) {
            removeSceneNodesRecursive(entity.getChildren());
        }

        if (entity.getSceneNode() != null) {
            entity.getSceneNode().removeFromParent();
        }

        entities.remove(entity);
        // Also remove from any parent section
        removeEntityFromAllSections(entity, entities);
        markDocumentDirty();
        notifySceneChanged();
    }

    /**
     * Removes all pending and loaded entities whose resource name matches
     * {@code resourceName}, then evicts the model asset from the JME cache.
     * Must be called on the JME render thread (e.g. via {@code enqueue()}).
     * Used by the import panel to release Windows file locks before deleting
     * a rolled-back preview model directory.
     */
    public void removePreviewEntities(String resourceName, String assetPath) {
        // Cancel any still-pending load for this resource
        pendingEntities.removeIf(pe -> resourceName.equalsIgnoreCase(pe.resourcePath));

        // Remove any already-loaded scene entities for this resource
        for (DesignerEntity entity : new ArrayList<>(entities)) {
            if (resourceName.equalsIgnoreCase(entity.getResourcePath())) {
                if (entity.getSceneNode() != null) {
                    entity.getSceneNode().removeFromParent();
                }
                entities.remove(entity);
                removeEntityFromAllSections(entity, entities);
            }
        }

        // Evict from the JME asset cache to release the OS file lock (Windows)
        assetManager.deleteFromCache(new ModelKey(assetPath));
    }

    /**
     * Recreates an entity with updated static/collider flags.
     * Preserves position, rotation, scale, and all other properties.
     */
    public void recreateEntity(DesignerEntity entity, boolean isStatic, boolean isCollider) {
        if (entity == null) return;

        // Capture current state
        String name = entity.getName();
        String entityId = entity.getId();
        DesignerEntityType type = entity.getType();
        Vector3f pos = entity.getPosition().clone();
        Quaternion rot = entity.getRotation().clone();
        Vector3f scale = entity.getScale().clone();
        float radius = entity.getRadius();
        float sizeX = entity.getSizeX();
        float sizeY = entity.getSizeY();
        float sizeZ = entity.getSizeZ();
        float radiusTop = entity.getRadiusTop();
        float radiusBottom = entity.getRadiusBottom();
        float innerRadiusTop = entity.getInnerRadiusTop();
        float innerRadiusBottom = entity.getInnerRadiusBottom();
        float cylHeight = entity.getHeight();
        float quadWidth = entity.getQuadWidth();
        float quadHeight = entity.getQuadHeight();
        String material = entity.getMaterial();

        // Find the entity's location (top-level or inside a section)
        List<DesignerEntity> ownerList = null;
        int ownerIndex = -1;
        int topIdx = entities.indexOf(entity);
        if (topIdx >= 0) {
            ownerList = null; // top-level
            ownerIndex = topIdx;
        } else {
            // Search in section children
            for (DesignerEntity e : entities) {
                if (e.getType() == DesignerEntityType.SECTION) {
                    int childIdx = e.getChildren().indexOf(entity);
                    if (childIdx >= 0) {
                        ownerList = e.getChildren();
                        ownerIndex = childIdx;
                        break;
                    }
                }
            }
        }

        // Remove old entity from scene
        if (selectionManager.getSelected() == entity) {
            selectionManager.deselect();
        }
        outlineEffect.removeOutline();
        if (entity.getSceneNode() != null) {
            entity.getSceneNode().removeFromParent();
        }
        if (ownerList != null) {
            ownerList.remove(entity);
        } else {
            entities.remove(entity);
        }

        // Build new code with updated flags.
        // NOTE: "collider" keyword is intentionally omitted — the runtime
        // would create an invisible GhostControl.  We create a normal visible
        // object in the designer; the collider keyword is only emitted in the
        // exported .code file by DesignerDocument.generateEntityCode().
        String staticPfx = isStatic ? "static " : "";
        String materialSuffix = (material != null && !material.isEmpty()) ? ", material \"" + material + "\"" : "";
        String code;
        switch (type) {
            case SPHERE:
                code = name + " => " + staticPfx + "sphere : pos (" + pos.x + "," + pos.y + "," + pos.z +
                       "), radius " + radius + materialSuffix;
                break;
            case BOX:
                code = name + " => " + staticPfx + "box : size (" +
                       (sizeX * 2) + "," + (sizeY * 2) + "," + (sizeZ * 2) +
                       "), pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix;
                break;
            case CYLINDER:
                code = name + " => " + staticPfx + "cylinder : radius (" +
                       radiusTop + "," + radiusBottom +
                       "), height " + cylHeight +
                       ", pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix;
                break;
            case HOLLOW_CYLINDER:
                code = name + " => " + staticPfx + "hollow cylinder : radius (" +
                       radiusTop + "," + radiusBottom +
                       "), inner radius (" + innerRadiusTop + "," + innerRadiusBottom +
                       "), height " + cylHeight +
                       ", pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix;
                break;
            case QUAD:
                code = name + " => " + staticPfx + "quad : size (" +
                       quadWidth + "," + quadHeight +
                       "), pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix;
                break;
            default:
                return;
        }

        // Run the code to recreate entity
        runPartialCode(code, null, false);

        SceneMaxScope scope = getMainScope();
        String nodeName = name + "@" + scope.scopeId;

        PendingEntity pending = new PendingEntity();
        pending.entityId = entityId;
        pending.name = name;
        pending.code = code;
        pending.type = type;
        pending.radius = radius;
        pending.sizeX = sizeX;
        pending.sizeY = sizeY;
        pending.sizeZ = sizeZ;
        pending.radiusTop = radiusTop;
        pending.radiusBottom = radiusBottom;
        pending.cylinderHeight = cylHeight;
        pending.innerRadiusTop = innerRadiusTop;
        pending.innerRadiusBottom = innerRadiusBottom;
        pending.quadWidth = quadWidth;
        pending.quadHeight = quadHeight;
        pending.staticEntity = isStatic;
        pending.colliderEntity = isCollider;
        pending.material = material;
        pending.nodeName = nodeName;
        pending.framesWaited = 0;
        pending.selectAfterCreation = true;
        pending.savedRotation = rot;
        pending.savedScale = scale;
        pending.targetList = ownerList;
        pending.insertIndex = ownerIndex;
        pendingEntities.add(pending);
    }

    private void notifySceneChanged() {
        if (panelCallback != null) {
            panelCallback.onSceneChanged();
        }
    }

    private void notifyLoadingProgress(int loaded, int total) {
        if (panelCallback != null) {
            panelCallback.onLoadingProgress(loaded, total);
        }
    }

    // --- Document persistence ---

    /**
     * Clears the current scene and reloads all entities from the .smdesign
     * file on disk.  This ensures the designer is 100% aligned with the
     * companion .code file.  Called when a cached DesignerPanel is reused
     * after its tab was closed.
     */
    public void reloadDocument() {
        // Save camera position – clearScene() resets it
        Vector3f savedCamPos = cam.getLocation().clone();
        Quaternion savedCamRot = cam.getRotation().clone();

        // Deselect current selection
        if (selectionManager != null) {
            selectionManager.deselect();
        }
        if (outlineEffect != null) {
            outlineEffect.removeOutline();
        }

        // Clean up path visuals and editing state
        if (pathEditGizmo != null) pathEditGizmo.detach();
        if (pathDrawingMode != null && pathDrawingMode.isActive()) pathDrawingMode.cancel();
        for (PathVisual pv : pathVisuals.values()) pv.removeFromParent();
        pathVisuals.clear();

        // Clear designer entity tracking
        entities.clear();
        for (PendingEntity pe : pendingEntities) {
            if (pe.loadingGizmo != null) {
                pe.loadingGizmo.removeFromParent();
            }
        }
        pendingEntities.clear();

        // Clear the SceneMax scene (removes all models, boxes, spheres,
        // controllers, audio, etc.)
        clearScene();

        // Re-initialize the SceneMax runtime (clearScene shuts down the
        // executor service and clears all controllers including the main
        // controller, so runPartialCode() won't work without this)
        String runtimePath = designerFile != null
                ? designerFile.getParentFile().getAbsolutePath()
                : designerProjectPath;
        initDesignerRuntime(runtimePath);

        // Restore camera (clearScene resets it to default)
        cam.setLocation(savedCamPos);
        cam.setRotation(savedCamRot);

        // Re-attach grid and gizmos if they were detached
        if (gridPlane != null && gridPlane.getParent() == null) {
            rootNode.attachChild(gridPlane);
        }
        if (translateGizmo != null && translateGizmo.getParent() == null) {
            rootNode.attachChild(translateGizmo);
        }
        if (rotateGizmo != null && rotateGizmo.getParent() == null) {
            rootNode.attachChild(rotateGizmo);
        }

        // Reload the document from disk (it may have been updated)
        if (designerFile != null && designerFile.exists() && designerFile.length() > 0) {
            try {
                document = DesignerDocument.load(designerFile);
            } catch (IOException e) {
                System.err.println("Failed to reload designer document: " + e.getMessage());
                document = new DesignerDocument(designerFile.getAbsolutePath());
            }
        }

        // Re-create camera entity
        initCameraEntity();

        // Recreate all entities via SceneMax3D commands
        if (document != null) {
            loadDocumentEntities();
        }

        // Re-show the camera preview (clearScene detaches all guiNode children)
        if (cameraPreview != null) {
            cameraPreview.hide();  // reset visible flag
            cameraPreview.show(guiNode);
            cameraPreview.syncWithEntity(cameraEntity);
        }

        // Don't refresh the tree here — loadDocumentEntities() only queues
        // pending entities.  The tree will refresh once all of them have been
        // resolved in processPendingEntities().
        if (pendingEntities.isEmpty()) {
            notifySceneChanged();
        }
    }

    /**
     * Switches from the current designer document to a different one.
     * Saves the current document, clears the scene, loads the new document
     * Clears the current document state without loading a new one.
     * Called when a designer tab is closed due to file deletion, so that
     * stale entities are not accidentally saved to a future file with
     * the same path.
     *
     * Must be called on the JME thread (via enqueue).
     */
    public void clearDocument() {
        if (selectionManager != null) {
            selectionManager.deselect();
        }
        if (outlineEffect != null) {
            outlineEffect.removeOutline();
        }
        // Clean up path state
        if (pathEditGizmo != null) pathEditGizmo.detach();
        if (pathDrawingMode != null && pathDrawingMode.isActive()) pathDrawingMode.cancel();
        for (PathVisual pv : pathVisuals.values()) pv.removeFromParent();
        pathVisuals.clear();

        entities.clear();
        for (PendingEntity pe : pendingEntities) {
            if (pe.loadingGizmo != null) {
                pe.loadingGizmo.removeFromParent();
            }
        }
        pendingEntities.clear();
        clearSceneAll();
        document = null;
        designerFile = null;
    }

    /**
     * from disk, and recreates all entities.  Unlike {@link #reloadDocument()},
     * this method does NOT preserve the current camera position — the orbit
     * camera should be restored by the caller after this method returns.
     *
     * Must be called on the JME thread (via enqueue).
     */
    public void switchDocument(File newDesignerFile, String newProjectPath) {
        // Save current document before switching
        saveCameraState();

        // Set new document info
        this.designerFile = newDesignerFile;
        this.designerProjectPath = newProjectPath;

        // Deselect current selection
        if (selectionManager != null) {
            selectionManager.deselect();
        }
        if (outlineEffect != null) {
            outlineEffect.removeOutline();
        }

        // Clean up path state
        if (pathEditGizmo != null) pathEditGizmo.detach();
        if (pathDrawingMode != null && pathDrawingMode.isActive()) pathDrawingMode.cancel();
        for (PathVisual pv : pathVisuals.values()) pv.removeFromParent();
        pathVisuals.clear();

        // Clear designer entity tracking
        entities.clear();
        for (PendingEntity pe : pendingEntities) {
            if (pe.loadingGizmo != null) {
                pe.loadingGizmo.removeFromParent();
            }
        }
        pendingEntities.clear();

        // Clear the SceneMax scene — use clearSceneAll() to also remove
        // shared/static entities that clearScene() would keep alive
        clearSceneAll();

        // Re-initialize the SceneMax runtime
        String runtimePath = designerFile != null
                ? designerFile.getParentFile().getAbsolutePath()
                : designerProjectPath;
        initDesignerRuntime(runtimePath);

        // Re-attach grid and gizmos if they were detached
        if (gridPlane != null && gridPlane.getParent() == null) {
            rootNode.attachChild(gridPlane);
        }
        if (translateGizmo != null && translateGizmo.getParent() == null) {
            rootNode.attachChild(translateGizmo);
        }
        if (rotateGizmo != null && rotateGizmo.getParent() == null) {
            rootNode.attachChild(rotateGizmo);
        }

        // Load the new document from disk
        if (designerFile != null && designerFile.exists() && designerFile.length() > 0) {
            try {
                document = DesignerDocument.load(designerFile);
            } catch (IOException e) {
                System.err.println("Failed to load designer document: " + e.getMessage());
                document = new DesignerDocument(designerFile.getAbsolutePath());
            }
        } else {
            document = new DesignerDocument(designerFile.getAbsolutePath());
        }

        // Re-create camera entity from new document
        initCameraEntity();

        // Reset entity counters before loading (loadDocumentEntities will
        // sync them to the highest index found in the document)
        sphereCounter = 0;
        boxCounter = 0;
        modelCounter = 0;

        // Recreate all entities from new document
        if (document != null) {
            loadDocumentEntities();
        }

        // Re-show the camera preview (clearSceneAll detaches all guiNode children)
        if (cameraPreview != null) {
            cameraPreview.hide();  // reset visible flag
            cameraPreview.show(guiNode);
            cameraPreview.syncWithEntity(cameraEntity);
        }

        // Don't refresh the tree here — loadDocumentEntities() only queues
        // pending entities.  The tree will refresh once all of them have been
        // resolved in processPendingEntities().
        if (pendingEntities.isEmpty()) {
            // No 3D entities to load (only CODE nodes) — refresh now
            notifySceneChanged();
        }
    }

    private void loadDocumentEntities() {
        loadingDocument = true;
        loadingTotalEntities = document.getEntityDefs().size();
        notifyLoadingProgress(0, loadingTotalEntities);
        applyDocumentSceneEnvironmentShader();

        // Record document entity order so we can restore it after async loading
        loadingEntityOrder = new ArrayList<>();
        collectEntityIdsRecursive(document.getEntityDefs(), loadingEntityOrder);

        loadEntityDefs(document.getEntityDefs(), entities);
    }

    private void applyDocumentSceneEnvironmentShader() {
        if (document == null) {
            return;
        }

        String shaderName = document.getSceneEnvironmentShader();
        if (shaderName != null && !shaderName.trim().isEmpty()) {
            runPartialCode("Scene.environment.shader = \"" + shaderName.trim() + "\"", null, false);
        }
    }

    /**
     * Recursively sorts entities (and section children) by the recorded load order.
     */
    private void sortEntitiesByLoadOrder(List<DesignerEntity> list, List<String> order) {
        list.sort((a, b) -> {
            int idxA = order.indexOf(a.getId());
            int idxB = order.indexOf(b.getId());
            if (idxA < 0) idxA = -1;
            if (idxB < 0) idxB = -1;
            return Integer.compare(idxA, idxB);
        });
        for (DesignerEntity e : list) {
            if (e.getType() == DesignerEntityType.SECTION && !e.getChildren().isEmpty()) {
                sortEntitiesByLoadOrder(e.getChildren(), order);
            }
        }
    }

    /**
     * Recursively collects entity IDs from entity defs including section children.
     */
    private void collectEntityIdsRecursive(List<JSONObject> entityDefs, List<String> ids) {
        for (JSONObject entityDef : entityDefs) {
            ids.add(entityDef.getString("id"));
            if ("SECTION".equals(entityDef.optString("type")) && entityDef.has("children")) {
                JSONArray children = entityDef.getJSONArray("children");
                List<JSONObject> childDefs = new ArrayList<>();
                for (int i = 0; i < children.length(); i++) {
                    childDefs.add(children.getJSONObject(i));
                }
                collectEntityIdsRecursive(childDefs, ids);
            }
        }
    }

    /**
     * Recursively loads entity definitions, handling CODE and SECTION types
     * specially. 3D entities (SPHERE, BOX, MODEL) are created via code execution.
     * SECTION entities are added directly with their children loaded recursively.
     */
    private void loadEntityDefs(List<JSONObject> entityDefs, List<DesignerEntity> targetList) {
        for (JSONObject entityDef : entityDefs) {
            DesignerEntity entityTemplate = DesignerEntity.fromJSON(entityDef);

            // Code nodes have no 3D representation — add directly to entities list
            if (entityTemplate.getType() == DesignerEntityType.CODE) {
                DesignerEntity codeEntity = new DesignerEntity(
                        entityTemplate.getId(), entityTemplate.getName(), DesignerEntityType.CODE);
                codeEntity.setCodeText(entityTemplate.getCodeText());
                targetList.add(codeEntity);
                continue;
            }

            // PATH entities are created directly (no SceneMax code)
            if (entityTemplate.getType() == DesignerEntityType.PATH) {
                DesignerEntity pathEntity = new DesignerEntity(
                        entityTemplate.getId(), entityTemplate.getName(), DesignerEntityType.PATH);
                pathEntity.setBezierPath(entityTemplate.getBezierPath());

                // Create visual
                PathVisual visual = new PathVisual(assetManager);
                if (pathEntity.getBezierPath() != null) {
                    visual.rebuild(pathEntity.getBezierPath());
                }
                rootNode.attachChild(visual);
                pathVisuals.put(pathEntity.getId(), visual);

                // Create wrapper node
                Node pathNode = new Node(pathEntity.getName());
                if (pathEntity.getBezierPath() != null && pathEntity.getBezierPath().getPointCount() > 0) {
                    pathNode.setLocalTranslation(pathEntity.getBezierPath().getPoint(0).getPosition());
                }
                pathEntity.setSceneNode(pathNode);
                rootNode.attachChild(pathNode);

                targetList.add(pathEntity);
                continue;
            }

            // Section nodes — add directly and load children recursively
            if (entityTemplate.getType() == DesignerEntityType.SECTION) {
                DesignerEntity sectionEntity = new DesignerEntity(
                        entityTemplate.getId(), entityTemplate.getName(), DesignerEntityType.SECTION);
                targetList.add(sectionEntity);
                // Recursively load children into the section's children list
                if (entityDef.has("children")) {
                    JSONArray childrenArr = entityDef.getJSONArray("children");
                    List<JSONObject> childDefs = new ArrayList<>();
                    for (int i = 0; i < childrenArr.length(); i++) {
                        childDefs.add(childrenArr.getJSONObject(i));
                    }
                    loadEntityDefs(childDefs, sectionEntity.getChildren());
                }
                continue;
            }

            Vector3f pos = DesignerEntity.positionFromJSON(entityDef);
            Quaternion rot = DesignerEntity.rotationFromJSON(entityDef);
            Vector3f scale = DesignerEntity.scaleFromJSON(entityDef);

            // Always regenerate code from entity properties so it reflects
            // the latest position, size, static/collider flags, etc.
            // (The saved sceneMaxCode can become stale after transform or
            // property changes that don't update it.)
            String code = generateCodeFromEntity(entityTemplate, pos);

            // Run the code to create the entity - will be processed by controllers
            runPartialCode(code, null, false);

            // Build the expected node name and queue for deferred lookup
            SceneMaxScope scope = getMainScope();
            String nodeName = entityTemplate.getName() + "@" + scope.scopeId;

            PendingEntity pending = new PendingEntity();
            pending.entityId = entityTemplate.getId();
            pending.name = entityTemplate.getName();
            pending.code = code;
            pending.type = entityTemplate.getType();
            pending.nodeName = nodeName;
            pending.framesWaited = 0;
            pending.selectAfterCreation = false;
            pending.savedRotation = rot;
            pending.savedScale = scale;
            pending.targetList = targetList;

            // Copy type-specific properties
            switch (entityTemplate.getType()) {
                case SPHERE:
                    pending.radius = entityTemplate.getRadius();
                    pending.staticEntity = entityTemplate.isStaticEntity();
                    pending.colliderEntity = entityTemplate.isColliderEntity();
                    pending.material = entityTemplate.getMaterial();
                    pending.shader = entityTemplate.getShader();
                    pending.hidden = entityTemplate.isHidden();
                    pending.shadowMode = entityTemplate.getShadowMode();
                    break;
                case BOX:
                    pending.sizeX = entityTemplate.getSizeX();
                    pending.sizeY = entityTemplate.getSizeY();
                    pending.sizeZ = entityTemplate.getSizeZ();
                    pending.staticEntity = entityTemplate.isStaticEntity();
                    pending.colliderEntity = entityTemplate.isColliderEntity();
                    pending.material = entityTemplate.getMaterial();
                    pending.shader = entityTemplate.getShader();
                    pending.hidden = entityTemplate.isHidden();
                    pending.shadowMode = entityTemplate.getShadowMode();
                    break;
                case CYLINDER:
                    pending.radiusTop = entityTemplate.getRadiusTop();
                    pending.radiusBottom = entityTemplate.getRadiusBottom();
                    pending.cylinderHeight = entityTemplate.getHeight();
                    pending.staticEntity = entityTemplate.isStaticEntity();
                    pending.colliderEntity = entityTemplate.isColliderEntity();
                    pending.material = entityTemplate.getMaterial();
                    pending.shader = entityTemplate.getShader();
                    pending.hidden = entityTemplate.isHidden();
                    pending.shadowMode = entityTemplate.getShadowMode();
                    break;
                case HOLLOW_CYLINDER:
                    pending.radiusTop = entityTemplate.getRadiusTop();
                    pending.radiusBottom = entityTemplate.getRadiusBottom();
                    pending.innerRadiusTop = entityTemplate.getInnerRadiusTop();
                    pending.innerRadiusBottom = entityTemplate.getInnerRadiusBottom();
                    pending.cylinderHeight = entityTemplate.getHeight();
                    pending.staticEntity = entityTemplate.isStaticEntity();
                    pending.colliderEntity = entityTemplate.isColliderEntity();
                    pending.material = entityTemplate.getMaterial();
                    pending.shader = entityTemplate.getShader();
                    pending.hidden = entityTemplate.isHidden();
                    pending.shadowMode = entityTemplate.getShadowMode();
                    break;
                case QUAD:
                    pending.quadWidth = entityTemplate.getQuadWidth();
                    pending.quadHeight = entityTemplate.getQuadHeight();
                    pending.staticEntity = entityTemplate.isStaticEntity();
                    pending.colliderEntity = entityTemplate.isColliderEntity();
                    pending.material = entityTemplate.getMaterial();
                    pending.shader = entityTemplate.getShader();
                    pending.hidden = entityTemplate.isHidden();
                    pending.shadowMode = entityTemplate.getShadowMode();
                    break;
                case MODEL:
                    pending.resourcePath = entityTemplate.getResourcePath();
                    pending.staticModel = entityTemplate.isStaticModel();
                    pending.dynamicModel = entityTemplate.isDynamicModel();
                    pending.vehicleModel = entityTemplate.isVehicleModel();
                    pending.hidden = entityTemplate.isHidden();
                    pending.shader = entityTemplate.getShader();
                    pending.shadowMode = entityTemplate.getShadowMode();
                    pending.jointMapping = entityTemplate.getJointMapping();
                    break;
            }

            // Show a loading progress gizmo for async model loading
            if (entityTemplate.getType() == DesignerEntityType.MODEL) {
                LoadingProgressGizmo gizmo = new LoadingProgressGizmo(assetManager, entityTemplate.getResourcePath());
                gizmo.setLocalTranslation(pos.x, pos.y + 1f, pos.z);
                rootNode.attachChild(gizmo);
                pending.loadingGizmo = gizmo;
            }

            // Apply material at runtime (the creation code includes the material
            // attribute, but also send the runtime command to guarantee the visual)
            String mat = entityTemplate.getMaterial();
            if (mat != null && !mat.isEmpty()) {
                runPartialCode(entityTemplate.getName() + ".material = \"" + mat + "\"", null, false);
            }

            String shader = entityTemplate.getShader();
            if (shader != null && !shader.isEmpty()) {
                runPartialCode(entityTemplate.getName() + ".shader = \"" + shader + "\"", null, false);
            }

            pendingEntities.add(pending);
        }

        // If no entities to load, finalize immediately
        if (pendingEntities.isEmpty()) {
            loadingDocument = false;
        }

        // Sync counters with loaded entity names so new entities get unique indices
        syncCountersFromEntities();
    }

    /**
     * Scans entity names loaded from the document and advances the per-type
     * counters past the highest index found.  This prevents name collisions
     * when the user adds new entities after opening a saved document.
     */
    private void syncCountersFromEntities() {
        if (document == null) return;
        for (JSONObject entityDef : document.getEntityDefs()) {
            DesignerEntity e = DesignerEntity.fromJSON(entityDef);
            String name = e.getName();
            if (name == null) continue;

            if (name.startsWith("sphere_")) {
                int idx = parseTrailingIndex(name, "sphere_");
                if (idx > sphereCounter) sphereCounter = idx;
            } else if (name.startsWith("box_")) {
                int idx = parseTrailingIndex(name, "box_");
                if (idx > boxCounter) boxCounter = idx;
            } else if (name.startsWith("model_")) {
                int idx = parseTrailingIndex(name, "model_");
                if (idx > modelCounter) modelCounter = idx;
            } else if (name.startsWith("path_")) {
                int idx = parseTrailingIndex(name, "path_");
                if (idx > pathCounter) pathCounter = idx;
            }
        }
    }

    private int parseTrailingIndex(String name, String prefix) {
        try {
            return Integer.parseInt(name.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Generates SceneMax code from entity properties for design-time use.
     * NOTE: the "collider" keyword is intentionally omitted here because the
     * runtime turns colliders into invisible GhostControls with no geometry.
     * In the designer we always create a visible object; the "collider"
     * keyword is only emitted in DesignerDocument.generateEntityCode() for
     * the exported .code file.
     */
    private String generateCodeFromEntity(DesignerEntity entity, Vector3f pos) {
        String name = entity.getName();
        String mat = entity.getMaterial();
        String materialSuffix = (mat != null && !mat.isEmpty()) ? ", material \"" + mat + "\"" : "";
        switch (entity.getType()) {
            case SPHERE:
                String spherePrefix = entity.isStaticEntity() ? "static " : "";
                return name + " => " + spherePrefix + "sphere : pos (" + pos.x + "," + pos.y + "," + pos.z +
                       "), radius " + entity.getRadius() + materialSuffix;
            case BOX:
                String boxPrefix = entity.isStaticEntity() ? "static " : "";
                return name + " => " + boxPrefix + "box : size (" +
                       (entity.getSizeX() * 2) + "," + (entity.getSizeY() * 2) + "," + (entity.getSizeZ() * 2) +
                       "), pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix;
            case CYLINDER:
                String cylPrefix = entity.isStaticEntity() ? "static " : "";
                return name + " => " + cylPrefix + "cylinder : radius (" +
                       entity.getRadiusTop() + "," + entity.getRadiusBottom() +
                       "), height " + entity.getHeight() +
                       ", pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix;
            case HOLLOW_CYLINDER:
                String hcPrefix = entity.isStaticEntity() ? "static " : "";
                return name + " => " + hcPrefix + "hollow cylinder : radius (" +
                       entity.getRadiusTop() + "," + entity.getRadiusBottom() +
                       "), inner radius (" + entity.getInnerRadiusTop() + "," + entity.getInnerRadiusBottom() +
                       "), height " + entity.getHeight() +
                       ", pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix;
            case QUAD:
                String quadPrefix = entity.isStaticEntity() ? "static " : "";
                return name + " => " + quadPrefix + "quad : size (" +
                       entity.getQuadWidth() + "," + entity.getQuadHeight() +
                       "), pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix;
            case MODEL:
                String staticPfx = entity.isStaticModel() ? "static " : "";
                String vehicleSfx = entity.isVehicleModel() ? " vehicle" : "";
                return name + " => " + staticPfx + entity.getResourcePath() + vehicleSfx +
                       ": pos (" + pos.x + "," + pos.y + "," + pos.z + ") async";
            default:
                return "";
        }
    }

    /**
     * Auto-saves both the .smdesign JSON and the companion .code file
     * immediately so the on-disk files always reflect the current scene state.
     */
    public void markDocumentDirty() {
        // Auto-save the .smdesign JSON
        if (document != null && designerFile != null && designerFile.exists()) {
            try {
                List<DesignerEntity> sceneEntities = entities.stream()
                        .filter(e -> e.getType() != DesignerEntityType.CAMERA)
                        .collect(Collectors.toList());
                Vector3f gameCamPos = cameraEntity != null ? cameraEntity.getPosition() : new Vector3f(0, 2, 5);
                Quaternion gameCamRot = cameraEntity != null ? cameraEntity.getRotation() : Quaternion.IDENTITY;
                document.save(new File(document.getFilePath()), sceneEntities,
                        cam.getLocation(), cam.getRotation(), gameCamPos, gameCamRot);
            } catch (IOException e) {
                System.err.println("Failed to auto-save designer document");
                e.printStackTrace();
            }
        }
        // Auto-save the .code companion and notify UI
        persistCodeFile();
    }

    /**
     * Writes the companion .code file with the current SceneMax3D script.
     * If the file is newly created, triggers a scripts tree refresh.
     */
    private void persistCodeFile() {
        if (designerFile == null) return;
        if (!designerFile.exists()) return; // file was deleted, don't recreate it
        try {
            List<DesignerEntity> sceneEntities = entities.stream()
                    .filter(e -> e.getType() != DesignerEntityType.CAMERA)
                    .collect(Collectors.toList());
            Vector3f gameCamPos = cameraEntity != null ? cameraEntity.getPosition() : new Vector3f(0, 2, 5);
            Quaternion gameCamRot = cameraEntity != null ? cameraEntity.getRotation() : Quaternion.IDENTITY;
            boolean wasNew = DesignerDocument.saveCodeFile(designerFile, sceneEntities, gameCamPos, gameCamRot,
                    document != null ? document.getSceneEnvironmentShader() : "");
            if (wasNew && scriptsTreeRefreshCallback != null) {
                javax.swing.SwingUtilities.invokeLater(scriptsTreeRefreshCallback);
            }
            // Notify the UI so an open .code tab refreshes its content
            if (codeFileUpdatedCallback != null) {
                String codeFilePath = DesignerDocument.getCodeFile(designerFile).getAbsolutePath();
                javax.swing.SwingUtilities.invokeLater(() -> codeFileUpdatedCallback.accept(codeFilePath));
            }
        } catch (IOException e) {
            System.err.println("Failed to persist .code file");
            e.printStackTrace();
        }
    }

    /**
     * Persists the final camera state on shutdown.  Entity changes are
     * already auto-saved by {@link #markDocumentDirty()}.
     */
    private void saveCameraState() {
        if (document == null || designerFile == null) return;
        if (!designerFile.exists()) return; // file was deleted, don't recreate it
        try {
            List<DesignerEntity> sceneEntities = entities.stream()
                    .filter(e -> e.getType() != DesignerEntityType.CAMERA)
                    .collect(Collectors.toList());
            Vector3f gameCamPos = cameraEntity != null ? cameraEntity.getPosition() : new Vector3f(0, 2, 5);
            Quaternion gameCamRot = cameraEntity != null ? cameraEntity.getRotation() : Quaternion.IDENTITY;
            document.save(new File(document.getFilePath()), sceneEntities,
                    cam.getLocation(), cam.getRotation(), gameCamPos, gameCamRot);
        } catch (IOException e) {
            System.err.println("Failed to save camera state");
            e.printStackTrace();
        }
    }

    // --- Input handling ---

    private void registerDesignerInputMappings() {
        inputManager.addMapping(ACTION_LEFT_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(ACTION_RIGHT_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping(ACTION_MIDDLE_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_MIDDLE));
        inputManager.addMapping(ACTION_DELETE, new KeyTrigger(KeyInput.KEY_DELETE));
        inputManager.addMapping("DesignerCtrl", new KeyTrigger(KeyInput.KEY_LCONTROL), new KeyTrigger(KeyInput.KEY_RCONTROL));
        inputManager.addMapping("DesignerAlt", new KeyTrigger(KeyInput.KEY_LMENU), new KeyTrigger(KeyInput.KEY_RMENU));
        inputManager.addMapping("DesignerShift", new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
        inputManager.addMapping("DesignerEnter", new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping("DesignerEscape", new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping("DesignerScrollUp", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping("DesignerScrollDown", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        inputManager.addListener((ActionListener) (name, isPressed, tpf) -> {
            if (name.equals(ACTION_LEFT_CLICK)) {
                if (isPressed) onLeftClickPressed();
                else onLeftClickReleased();
            } else if (name.equals(ACTION_RIGHT_CLICK)) {
                orbiting = isPressed;
                if (isPressed) {
                    lastMousePos.set(inputManager.getCursorPosition());
                    animatingCamera = false;
                }
            } else if (name.equals(ACTION_MIDDLE_CLICK)) {
                panning = isPressed;
                if (isPressed) {
                    lastMousePos.set(inputManager.getCursorPosition());
                    animatingCamera = false;
                }
            }
        }, ACTION_LEFT_CLICK, ACTION_RIGHT_CLICK, ACTION_MIDDLE_CLICK);

        inputManager.addListener((ActionListener) (name, isPressed, tpf) -> {
            if (name.equals(ACTION_DELETE) && isPressed) {
                DesignerEntity sel = selectionManager.getSelected();
                if (sel != null && sel.getType() != DesignerEntityType.CAMERA) removeEntity(sel);
            } else if (name.equals("DesignerCtrl")) {
                ctrlHeld = isPressed;
            } else if (name.equals("DesignerAlt")) {
                if (pathEditGizmo != null) pathEditGizmo.setAltHeld(isPressed);
            } else if (name.equals("DesignerShift")) {
                if (pathEditGizmo != null) pathEditGizmo.setShiftHeld(isPressed);
            } else if (name.equals("DesignerEnter") && isPressed) {
                if (pathDrawingMode != null && pathDrawingMode.isActive()) {
                    pathDrawingMode.finish();
                }
            } else if (name.equals("DesignerEscape") && isPressed) {
                if (pathDrawingMode != null && pathDrawingMode.isActive()) {
                    pathDrawingMode.cancel();
                }
            }
        }, ACTION_DELETE, "DesignerCtrl", "DesignerAlt", "DesignerShift", "DesignerEnter", "DesignerEscape");

        inputManager.addListener((AnalogListener) (name, value, tpf) -> {
            if (name.equals("DesignerScrollUp")) {
                cameraDistance = Math.max(1f, cameraDistance - value * 50f);
                updateOrbitCamera();
            } else if (name.equals("DesignerScrollDown")) {
                cameraDistance = Math.min(200f, cameraDistance + value * 50f);
                updateOrbitCamera();
            }
        }, "DesignerScrollUp", "DesignerScrollDown");
    }

    private void onLeftClickPressed() {
        Vector2f click = inputManager.getCursorPosition();

        // Consume clicks inside camera preview area
        if (cameraPreview != null && cameraPreview.isVisible() &&
                cameraPreview.containsPoint(click)) {
            return;
        }

        // Try viewcube click first (rendered on top)
        if (viewCubeGizmo != null &&
                viewCubeGizmo.tryClick(click, cam.getWidth(), cam.getHeight())) {
            return;
        }

        // If path drawing mode is active, delegate to it
        if (pathDrawingMode != null && pathDrawingMode.isActive()) {
            pathDrawingMode.onLeftClick(cam, click);
            return;
        }

        // Try path edit gizmo drag (control points / tangent handles)
        if (pathEditGizmo != null && pathEditGizmo.isAttached()) {
            if (pathEditGizmo.tryStartDrag(cam, click)) {
                return;
            }
        }

        // Try gizmo drag
        if (gizmoManager.tryStartDrag(cam, click)) {
            return;
        }

        // Otherwise, try picking an entity (including PATH entities)
        DesignerEntity picked = pickEntity(cam, click);
        selectionManager.select(picked);
    }

    /**
     * Picks an entity at the given screen position, including PATH entities
     * which use screen-space curve proximity picking.
     */
    private DesignerEntity pickEntity(Camera cam, Vector2f screenPos) {
        // First try standard ray-cast picking for non-PATH entities
        DesignerEntity picked = selectionManager.pick(cam, screenPos, entities);

        // Also check PATH entities using screen-space proximity to curve
        float bestDist = picked != null ? PICK_THRESHOLD_PX : Float.MAX_VALUE;
        DesignerEntity bestPath = picked;

        for (DesignerEntity entity : entities) {
            if (entity.getType() != DesignerEntityType.PATH) continue;
            BezierPath path = entity.getBezierPath();
            if (path == null || path.getPointCount() < 2) continue;

            // Check screen-space distance to control points
            for (int i = 0; i < path.getPointCount(); i++) {
                Vector3f cpScreen = cam.getScreenCoordinates(path.getPoint(i).getPosition());
                float dist = screenPos.distance(new Vector2f(cpScreen.x, cpScreen.y));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPath = entity;
                }
            }

            // Check screen-space distance to curve segments
            for (int seg = 0; seg < path.getSegmentCount(); seg++) {
                for (int s = 0; s <= 20; s++) {
                    float t = (float) s / 20;
                    Vector3f worldPt = path.evaluate(seg, t);
                    Vector3f screenPt = cam.getScreenCoordinates(worldPt);
                    float dist = screenPos.distance(new Vector2f(screenPt.x, screenPt.y));
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestPath = entity;
                    }
                }
            }
        }

        if (bestDist < PICK_THRESHOLD_PX) {
            return bestPath;
        }
        return picked;
    }

    private static final float PICK_THRESHOLD_PX = 18f;

    private void onLeftClickReleased() {
        if (pathEditGizmo != null && pathEditGizmo.isDragging()) {
            pathEditGizmo.endDrag();
        }
        if (gizmoManager.isDragging()) {
            gizmoManager.endDrag();
        }
    }

    // --- Orbit camera ---

    private void updateOrbitCamera() {
        float x = cameraDistance * (float) (Math.cos(cameraPitch) * Math.sin(cameraYaw));
        float y = cameraDistance * (float) Math.sin(cameraPitch);
        float z = cameraDistance * (float) (Math.cos(cameraPitch) * Math.cos(cameraYaw));

        Vector3f camPos = cameraTarget.add(x, y, z);
        cam.setLocation(camPos);
        cam.lookAt(cameraTarget, Vector3f.UNIT_Y);
    }

    /**
     * Starts a smooth camera animation toward the given yaw/pitch.
     * NaN yaw means keep the current yaw (used for top/bottom views).
     */
    private void startCameraAnimation(float targetYaw, float targetPitch) {
        animStartYaw = cameraYaw;
        animStartPitch = cameraPitch;
        animTargetPitch = targetPitch;

        if (Float.isNaN(targetYaw)) {
            animTargetYaw = cameraYaw;
        } else {
            // Normalize yaw difference so the camera takes the shortest path
            float yawDiff = targetYaw - cameraYaw;
            while (yawDiff > Math.PI) yawDiff -= 2 * Math.PI;
            while (yawDiff < -Math.PI) yawDiff += 2 * Math.PI;
            animTargetYaw = cameraYaw + yawDiff;
        }

        animProgress = 0f;
        animatingCamera = true;
    }

    /**
     * Smoothly moves the editor camera to focus on the given entity.
     * The camera eases its target to the entity's world position and adjusts
     * the distance so the object is comfortably visible.
     */
    public void focusCameraOnEntity(DesignerEntity entity) {
        if (entity == null || entity.getSceneNode() == null) return;

        Vector3f entityPos = entity.getSceneNode().getWorldTranslation();

        animStartTarget = cameraTarget.clone();
        animEndTarget = entityPos.clone();

        // Compute a comfortable distance based on the entity's bounding volume
        float radius = 5f;
        if (entity.getSceneNode().getWorldBound() != null) {
            radius = entity.getSceneNode().getWorldBound().getVolume();
            // Approximate a radius from bounding volume (cube root for rough size)
            radius = (float) Math.cbrt(radius);
            if (radius < 1f) radius = 1f;
        }
        float desiredDistance = radius * 3f;
        // Clamp to reasonable range
        desiredDistance = Math.max(3f, Math.min(desiredDistance, 100f));

        animStartDistance = cameraDistance;
        animEndDistance = desiredDistance;

        animTargetProgress = 0f;
        animatingCameraTarget = true;
    }

    /**
     * Called when the canvas is resized. Updates both main camera and viewcube.
     */
    public void onCanvasResized(int width, int height) {
        cam.resize(width, height, true);
        if (viewCubeGizmo != null) {
            viewCubeGizmo.updateViewportBounds(width, height);
        }
        if (cameraPreview != null) {
            cameraPreview.updatePosition(width, height);
        }
    }

    // --- Update loop ---

    @Override
    public void simpleUpdate(float tpf) {
        // IMPORTANT: call super so SceneMaxApp's controllers execute.
        // This is where runPartialCode() commands (entity creation, etc.)
        // actually get processed and nodes appear in the scene graph.
        super.simpleUpdate(tpf);

        // Check if any pending entity nodes have been created by the controllers
        processPendingEntities();

        // Animate camera toward a preset view (smooth transition)
        if (animatingCamera) {
            animProgress += tpf / ANIM_DURATION;
            if (animProgress >= 1.0f) {
                animProgress = 1.0f;
                animatingCamera = false;
            }
            // Quadratic ease-out: fast start, smooth end
            float t = 1.0f - (1.0f - animProgress) * (1.0f - animProgress);
            cameraYaw = animStartYaw + (animTargetYaw - animStartYaw) * t;
            cameraPitch = animStartPitch + (animTargetPitch - animStartPitch) * t;
            updateOrbitCamera();
        }

        // Animate camera target toward a focused entity (smooth transition)
        if (animatingCameraTarget) {
            animTargetProgress += tpf / FOCUS_ANIM_DURATION;
            if (animTargetProgress >= 1.0f) {
                animTargetProgress = 1.0f;
                animatingCameraTarget = false;
            }
            // Quadratic ease-out
            float t = 1.0f - (1.0f - animTargetProgress) * (1.0f - animTargetProgress);
            cameraTarget.interpolateLocal(animStartTarget, animEndTarget, t);
            cameraDistance = animStartDistance + (animEndDistance - animStartDistance) * t;
            updateOrbitCamera();
        }

        // Handle orbit/pan camera with mouse drag
        if ((orbiting || panning) && !animatingCamera && !animatingCameraTarget) {
            Vector2f currentMouse = inputManager.getCursorPosition();
            float dx = currentMouse.x - lastMousePos.x;
            float dy = currentMouse.y - lastMousePos.y;
            lastMousePos.set(currentMouse);

            if (orbiting && cameraMode == CameraMode.ORBIT) {
                // Right-click drag in Orbit mode: rotate camera around target
                cameraYaw -= dx * 0.005f;
                cameraPitch += dy * 0.005f;
                cameraPitch = FastMath.clamp(cameraPitch, (float) Math.toRadians(-89), (float) Math.toRadians(89));
                updateOrbitCamera();
            } else if (orbiting && cameraMode == CameraMode.PAN) {
                // Right-click drag in Pan mode: pan camera target
                if (ctrlHeld) {
                    // Ctrl held: vertical mouse movement pans forward/backward
                    // along camera look direction projected onto XZ plane
                    Vector3f lookDir = cam.getDirection().clone();
                    lookDir.y = 0;
                    lookDir.normalizeLocal();
                    cameraTarget.addLocal(lookDir.mult(dy * 0.02f));
                    // Horizontal mouse movement still pans left/right
                    Vector3f right = cam.getLeft().negate().mult(dx * 0.02f);
                    cameraTarget.addLocal(right);
                } else {
                    // No modifier: pan left/right and up/down
                    Vector3f right = cam.getLeft().negate().mult(dx * 0.02f);
                    Vector3f up = cam.getUp().mult(dy * 0.02f);
                    cameraTarget.addLocal(right).addLocal(up);
                }
                updateOrbitCamera();
            } else if (panning) {
                // Middle-click drag: always pan (in both modes)
                Vector3f right = cam.getLeft().negate().mult(dx * 0.02f);
                Vector3f up = cam.getUp().mult(dy * 0.02f);
                cameraTarget.addLocal(right).addLocal(up);
                updateOrbitCamera();
            }
        }

        // Handle path drawing mode mouse move (rubber-band preview)
        if (pathDrawingMode != null && pathDrawingMode.isActive()) {
            pathDrawingMode.onMouseMove(cam, inputManager.getCursorPosition());
        }

        // Handle path edit gizmo drag
        if (pathEditGizmo != null && pathEditGizmo.isDragging()) {
            pathEditGizmo.updateDrag(cam, inputManager.getCursorPosition());
        }

        // Handle gizmo drag
        if (gizmoManager.isDragging()) {
            gizmoManager.updateDrag(cam, inputManager.getCursorPosition());
        }

        // Scale gizmo based on camera distance
        gizmoManager.scaleGizmoToCamera(cam);
        gizmoManager.updateGizmoPosition();

        // Sync viewcube camera rotation with main camera
        if (viewCubeGizmo != null) {
            viewCubeGizmo.syncCamera(cameraYaw, cameraPitch);
        }

        // Update camera preview if visible
        if (cameraPreview != null && cameraPreview.isVisible()) {
            cameraPreview.syncWithEntity(cameraEntity);
        }
    }

    @Override
    public void destroy() {
        saveCameraState();
        if (cameraPreview != null) {
            cameraPreview.cleanup(renderManager);
        }
        if (viewCubeGizmo != null) {
            viewCubeGizmo.cleanup(renderManager);
        }
        // Ensure cursor is visible before JME3 context shutdown
        if (inputManager != null) {
            inputManager.setCursorVisible(true);
        }
        try {
            super.destroy();
        } catch (NullPointerException e) {
            // SceneMaxApp.destroy() references fields (pluginsCommunicationChannel,
            // _appObserver) that are not initialized in designer mode.
            // The JME3 base class cleanup (super.super.destroy()) has already
            // executed successfully by the time these NPEs are thrown.
        }
    }
}
