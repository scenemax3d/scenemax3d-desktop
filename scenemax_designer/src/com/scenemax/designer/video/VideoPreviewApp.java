package com.scenemax.designer.video;

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
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;
import com.scenemax.designer.gizmo.GizmoManager;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.gizmo.RotateGizmo;
import com.scenemax.designer.gizmo.TranslateGizmo;
import com.scenemaxeng.projector.SceneMaxApp;
import org.lwjgl.input.Mouse;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;

class VideoPreviewApp extends SceneMaxApp {
    private static final String ACTION_LEFT = "VideoPreviewLeft";
    private static final String ACTION_SCROLL_UP = "VideoPreviewScrollUp";
    private static final String ACTION_SCROLL_DOWN = "VideoPreviewScrollDown";

    private final Node objectNode = new Node("VideoPreviewObject");
    private final DesignerEntity objectEntity = new DesignerEntity("Video Preview", DesignerEntityType.MODEL);
    private final AWTLoader awtLoader = new AWTLoader();

    private TranslateGizmo translateGizmo;
    private RotateGizmo rotateGizmo;
    private GizmoManager gizmoManager;
    private Material material;
    private Texture2D texture;
    private VideoFrameDecoder decoder;
    private File videoFile;
    private VideoPreviewShape shape = VideoPreviewShape.PANE;
    private Consumer<String> statusCallback;
    private float aspect = 16f / 9f;
    private float cameraDistance = 6f;
    private float yaw = (float) Math.toRadians(35);
    private float pitch = (float) Math.toRadians(18);
    private boolean orbiting;
    private final Vector2f lastMouse = new Vector2f();

    @Override
    public void simpleInitApp() {
        viewPort.setBackgroundColor(new ColorRGBA(0.045f, 0.05f, 0.06f, 1f));
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(objectNode);
        objectEntity.setSceneNode(objectNode);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(1.15f));
        rootNode.addLight(ambient);

        DirectionalLight key = new DirectionalLight();
        key.setDirection(new Vector3f(-0.55f, -0.8f, -0.35f).normalizeLocal());
        key.setColor(ColorRGBA.White.mult(2.6f));
        rootNode.addLight(key);

        material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", ColorRGBA.White);
        material.setColor("Ambient", ColorRGBA.White);
        material.setColor("Specular", ColorRGBA.White.mult(0.25f));
        material.setFloat("Shininess", 12f);
        material.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);

        translateGizmo = new TranslateGizmo(assetManager);
        rotateGizmo = new RotateGizmo(assetManager);
        rootNode.attachChild(translateGizmo);
        rootNode.attachChild(rotateGizmo);
        gizmoManager = new GizmoManager(rootNode, translateGizmo, rotateGizmo);
        gizmoManager.onSelectionChanged(objectEntity);

        registerInput();
        rebuildGeometry();
        updateCamera();
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (orbiting) {
            Vector2f current = inputManager.getCursorPosition();
            float dx = current.x - lastMouse.x;
            float dy = current.y - lastMouse.y;
            lastMouse.set(current);
            yaw -= dx * 0.006f;
            pitch = FastMath.clamp(pitch + dy * 0.006f, (float) Math.toRadians(-70), (float) Math.toRadians(70));
            updateCamera();
        }
        if (decoder != null) {
            BufferedImage frame = decoder.pollFrame();
            if (frame != null) {
                applyFrame(frame);
            }
        }
        if (gizmoManager != null) {
            if (gizmoManager.isDragging()) {
                gizmoManager.updateDrag(cam, inputManager.getCursorPosition());
            }
            gizmoManager.scaleGizmoToCamera(cam);
            gizmoManager.updateGizmoPosition();
        }
    }

    void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    void setVideoFile(File file, VideoMetadata metadata) {
        enqueue(() -> {
            this.videoFile = file;
            if (metadata != null && metadata.width > 0 && metadata.height > 0) {
                aspect = Math.max(0.2f, metadata.width / (float) metadata.height);
                rebuildGeometry();
            }
            restartDecoder();
            return null;
        });
    }

    void setShape(VideoPreviewShape shape) {
        enqueue(() -> {
            this.shape = shape == null ? VideoPreviewShape.PANE : shape;
            rebuildGeometry();
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

    void resetView() {
        enqueue(() -> {
            cameraDistance = 6f;
            yaw = (float) Math.toRadians(35);
            pitch = (float) Math.toRadians(18);
            objectNode.setLocalTranslation(Vector3f.ZERO);
            objectNode.setLocalRotation(com.jme3.math.Quaternion.IDENTITY);
            objectNode.setLocalScale(Vector3f.UNIT_XYZ);
            updateCamera();
            return null;
        });
    }

    @Override
    public void stop() {
        releaseMouseCapture();
        closeDecoder();
        super.stop();
    }

    private void rebuildGeometry() {
        objectNode.detachAllChildren();
        Geometry geometry;
        float height = 2.2f;
        float width = Math.max(0.4f, height * aspect);
        if (shape == VideoPreviewShape.BOX) {
            geometry = new Geometry("VideoBox", new Box(width * 0.5f, height * 0.5f, 0.15f));
        } else if (shape == VideoPreviewShape.SPHERE) {
            geometry = new Geometry("VideoSphere", new Sphere(64, 64, 1.25f));
        } else {
            geometry = new Geometry("VideoPane", new Quad(width, height));
            geometry.setLocalTranslation(-width * 0.5f, -height * 0.5f, 0f);
        }
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.setMaterial(material);
        objectNode.attachChild(geometry);
    }

    private void applyFrame(BufferedImage frame) {
        Image image = awtLoader.load(frame, true);
        if (texture == null || texture.getImage() == null
                || texture.getImage().getWidth() != image.getWidth()
                || texture.getImage().getHeight() != image.getHeight()) {
            texture = new Texture2D(image);
            texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
            texture.setMagFilter(Texture.MagFilter.Bilinear);
            material.setTexture("DiffuseMap", texture);
        } else {
            texture.setImage(image);
        }
    }

    private void restartDecoder() {
        closeDecoder();
        if (videoFile == null || !videoFile.isFile()) {
            publishStatus("Choose a video file to preview.");
            return;
        }
        decoder = new VideoFrameDecoder(videoFile, this::publishStatus);
        decoder.start();
    }

    private void closeDecoder() {
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
    }

    private void releaseMouseCapture() {
        orbiting = false;
        try {
            if (inputManager != null) {
                inputManager.setCursorVisible(true);
            }
            if (Mouse.isCreated() && Mouse.isGrabbed()) {
                Mouse.setGrabbed(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void publishStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }

    private void updateCamera() {
        Vector3f center = objectNode.getWorldTranslation();
        float cp = FastMath.cos(pitch);
        Vector3f offset = new Vector3f(
                FastMath.sin(yaw) * cp,
                FastMath.sin(pitch),
                FastMath.cos(yaw) * cp
        ).multLocal(cameraDistance);
        cam.setLocation(center.add(offset));
        cam.lookAt(center, Vector3f.UNIT_Y);
    }

    private void registerInput() {
        inputManager.addMapping(ACTION_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(ACTION_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACTION_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addListener((ActionListener) (name, pressed, tpf) -> {
            if (!ACTION_LEFT.equals(name)) {
                return;
            }
            if (pressed) {
                Vector2f click = inputManager.getCursorPosition().clone();
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
                cameraDistance = Math.max(1.5f, cameraDistance - 0.35f);
                updateCamera();
            } else if (ACTION_SCROLL_DOWN.equals(name)) {
                cameraDistance = Math.min(30f, cameraDistance + 0.35f);
                updateCamera();
            }
        }, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN);
    }
}
