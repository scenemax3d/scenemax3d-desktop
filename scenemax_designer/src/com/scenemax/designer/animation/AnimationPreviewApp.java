package com.scenemax.designer.animation;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceAnimation;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.projector.AppModel;
import com.scenemaxeng.projector.SceneMaxApp;

import java.io.File;
import java.util.function.Consumer;

public class AnimationPreviewApp extends SceneMaxApp {

    private final Node previewRoot = new Node("AnimationPreviewRoot");
    private Spatial modelSpatial;
    private AppModel appModel;
    private String resourcesFolder;
    private ResourceSetup modelResource;
    private File animationFile;
    private String animationName = "candidate";
    private String clipName = "mixamo.com";
    private float cameraDistance = 6f;
    private float cameraYaw = FastMath.DEG_TO_RAD * 25f;
    private float cameraPitch = FastMath.DEG_TO_RAD * 15f;
    private Vector3f cameraTarget = new Vector3f(0f, 1f, 0f);
    private boolean orbiting;
    private final Vector2f lastMouse = new Vector2f();
    private Consumer<String> statusListener;

    @Override
    public void simpleInitApp() {
        if (resourcesFolder != null && !resourcesFolder.isBlank()) {
            File resourcesRoot = new File(resourcesFolder);
            File projectRoot = resourcesRoot.getParentFile();
            if (projectRoot != null) {
                setWorkingFolder(new File(new File(projectRoot, "scripts"), "_animation_preview").getAbsolutePath());
            }
        }
        super.simpleInitApp();
        setDisplayFps(false);
        setDisplayStatView(false);
        flyCam.setEnabled(false);
        viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.06f, 0.07f, 1f));
        rootNode.attachChild(previewRoot);
        setupLight();
        registerInput();
        updateCamera();
    }

    public void setResourcesFolder(String resourcesFolder) {
        this.resourcesFolder = resourcesFolder;
    }

    public void setStatusListener(Consumer<String> statusListener) {
        this.statusListener = statusListener;
    }

    public void orbit(float dx, float dy) {
        enqueue(() -> {
            cameraYaw -= dx * 0.01f;
            cameraPitch = FastMath.clamp(cameraPitch + dy * 0.01f, -1.2f, 1.2f);
            updateCamera();
            return null;
        });
    }

    public void zoom(float amount) {
        enqueue(() -> {
            cameraDistance = FastMath.clamp(cameraDistance + amount, 1.2f, 40f);
            updateCamera();
            return null;
        });
    }

    public void preview(ResourceSetup modelResource, File animationFile, String animationName, String clipName) {
        this.modelResource = modelResource;
        this.animationFile = animationFile;
        this.animationName = animationName == null || animationName.isBlank() ? "candidate" : animationName;
        this.clipName = clipName == null || clipName.isBlank() ? "mixamo.com" : clipName;
        enqueue(() -> {
            loadPreview();
            return null;
        });
    }

    private void loadPreview() {
        try {
            previewRoot.detachAllChildren();
            appModel = null;
            if (modelResource == null || modelResource.path == null || modelResource.path.isBlank()
                    || animationFile == null || !animationFile.isFile()) {
                updateStatus("Choose a project model and animation file for preview.");
                return;
            }

            if (resourcesFolder != null && !resourcesFolder.isBlank()) {
                assetManager.registerLocator(resourcesFolder, FileLocator.class);
            }
            registerParent(animationFile);
            modelSpatial = assetManager.loadModel(modelResource.path);
            modelSpatial.scale(modelResource.scaleX, modelResource.scaleY, modelResource.scaleZ);
            modelSpatial.setLocalTranslation(modelResource.localTranslationX, modelResource.localTranslationY, modelResource.localTranslationZ);
            modelSpatial.rotate(0f, modelResource.rotateY * FastMath.DEG_TO_RAD, 0f);
            previewRoot.attachChild(modelSpatial);

            Node holder = new Node("PreviewModelHolder");
            previewRoot.detachChild(modelSpatial);
            holder.attachChild(modelSpatial);
            previewRoot.attachChild(holder);

            appModel = new AppModel(holder);
            appModel.skinningControlNode = findSkinningControlNode(modelSpatial);
            AssetsMapping mapping = new AssetsMapping();
            mapping.getAnimationsIndex().put(animationName.toLowerCase(),
                    new ResourceAnimation(animationName, animationFile.getName(), clipName));
            boolean attached = appModel.attachExternalAnimation(assetManager, mapping, animationName);

            AnimComposer composer = appModel.getAnimComposer();
            if (attached && composer != null && composer.hasAction(animationName)) {
                composer.setCurrentAction(animationName);
                updateStatus("Previewing " + animationName + " on " + modelResource.name + ".");
            } else {
                updateStatus("Animation loaded, but it could not be attached to " + modelResource.name + ".");
            }
            updateCamera();
        } catch (Exception ex) {
            ex.printStackTrace();
            updateStatus("Preview failed: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    private void registerParent(File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            assetManager.registerLocator(parent.getAbsolutePath(), FileLocator.class);
        }
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

    private void setupLight() {
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.45f));
        rootNode.addLight(ambient);
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.4f, -1f, -0.5f).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(1.1f));
        rootNode.addLight(sun);
    }

    private void registerInput() {
        inputManager.addMapping("AnimPreviewOrbit", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("AnimPreviewZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping("AnimPreviewZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addMapping("AnimPreviewX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("AnimPreviewX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("AnimPreviewY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("AnimPreviewY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addListener((ActionListener) (name, pressed, tpf) -> {
            if ("AnimPreviewOrbit".equals(name)) {
                orbiting = pressed;
                if (pressed) {
                    lastMouse.set(inputManager.getCursorPosition());
                }
            }
        }, "AnimPreviewOrbit");
        inputManager.addListener((AnalogListener) (name, value, tpf) -> {
            if ("AnimPreviewZoomIn".equals(name)) {
                cameraDistance = Math.max(1.2f, cameraDistance - value * 8f);
                updateCamera();
            } else if ("AnimPreviewZoomOut".equals(name)) {
                cameraDistance = Math.min(40f, cameraDistance + value * 8f);
                updateCamera();
            } else if (orbiting) {
                Vector2f current = inputManager.getCursorPosition();
                float dx = current.x - lastMouse.x;
                float dy = current.y - lastMouse.y;
                lastMouse.set(current);
                cameraYaw -= dx * 0.01f;
                cameraPitch = FastMath.clamp(cameraPitch + dy * 0.01f, -1.2f, 1.2f);
                updateCamera();
            }
        }, "AnimPreviewZoomIn", "AnimPreviewZoomOut", "AnimPreviewX+", "AnimPreviewX-", "AnimPreviewY+", "AnimPreviewY-");
    }

    private void updateCamera() {
        Vector3f location = new Vector3f(
                cameraTarget.x + cameraDistance * FastMath.cos(cameraPitch) * FastMath.sin(cameraYaw),
                cameraTarget.y + cameraDistance * FastMath.sin(cameraPitch),
                cameraTarget.z + cameraDistance * FastMath.cos(cameraPitch) * FastMath.cos(cameraYaw));
        cam.setLocation(location);
        cam.lookAt(cameraTarget, Vector3f.UNIT_Y);
    }

    private void updateStatus(String message) {
        if (statusListener != null) {
            statusListener.accept(message);
        }
    }
}
