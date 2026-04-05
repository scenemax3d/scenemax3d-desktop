package com.scenemax.designer.effekseer;

import com.jme3.asset.plugins.FileLocator;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;
import com.jme3.ui.Picture;
import com.scenemaxeng.projector.SceneMaxApp;

import javax.swing.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class EffekseerPreviewApp extends SceneMaxApp {

    private static final String ACTION_RIGHT_CLICK = "EffekseerPreviewRightClick";
    private static final String ACTION_MIDDLE_CLICK = "EffekseerPreviewMiddleClick";
    private static final String ACTION_SCROLL_UP = "EffekseerPreviewScrollUp";
    private static final String ACTION_SCROLL_DOWN = "EffekseerPreviewScrollDown";
    private static final float FRAMES_PER_SECOND = 60f;
    private static final float MIN_SPAWN_INTERVAL_SECONDS = 1f / 600f;

    private final Node previewRoot = new Node("EffekseerPreviewRoot");
    private final Vector2f lastMousePos = new Vector2f();
    private final List<EmitterRuntime> emitters = new ArrayList<>();
    private final Quaternion billboardRotation = new Quaternion();
    private final Quaternion effectOrientation = new Quaternion();
    private final Vector3f projectTargetLocation = new Vector3f();
    private final EffekseerNativePreview nativePreview = new EffekseerNativePreview();

    private EffekseerEffectDocument document;
    private float cameraDistance = 12f;
    private float cameraYaw = FastMath.DEG_TO_RAD * 35f;
    private float cameraPitch = FastMath.DEG_TO_RAD * -15f;
    private Vector3f cameraTarget = new Vector3f(0f, 1.4f, 0f);
    private boolean orbiting = false;
    private boolean panning = false;
    private float effectTime = 0f;
    private Geometry ground;
    private Consumer<String> statusListener;
    private String registeredLocatorRoot;
    private String loadedEffectPath;
    private String loadedNativeEffectPath;
    private long nextParticleId = 1L;
    private boolean previewReady = false;
    private EffekseerEffectDocument pendingDocument;
    private File pendingImportedEffectFile;
    private boolean pendingReplay = false;
    private Picture nativePreviewPicture;
    private Texture2D nativePreviewTexture;
    private ByteBuffer nativePreviewPixels;
    private int nativePreviewWidth;
    private int nativePreviewHeight;
    private volatile String lastCameraInteractionStatus = "idle";

    public void setStatusListener(Consumer<String> statusListener) {
        this.statusListener = statusListener;
    }

    public void updateDocument(EffekseerEffectDocument document, File importedEffectFile) {
        this.document = document;
        if (!previewReady) {
            pendingDocument = document;
            pendingImportedEffectFile = importedEffectFile;
            pendingReplay = false;
            return;
        }
        enqueue(() -> {
            applyDocumentSettings(false);
            if (shouldRebuildFor(importedEffectFile)) {
                rebuildPreview(importedEffectFile);
            }
            return null;
        });
    }

    public void replayDocument(EffekseerEffectDocument document, File importedEffectFile) {
        this.document = document;
        if (!previewReady) {
            pendingDocument = document;
            pendingImportedEffectFile = importedEffectFile;
            pendingReplay = true;
            return;
        }
        enqueue(() -> {
            rebuildPreview(importedEffectFile);
            return null;
        });
    }

    @Override
    public void simpleInitApp() {
        super.simpleInitApp();
        nativePreview.setCompositeEnabled(false);
        setDisplayFps(false);
        setDisplayStatView(false);
        if (flyCam != null) {
            flyCam.setEnabled(false);
        }
        viewPort.setBackgroundColor(new ColorRGBA(0.04f, 0.05f, 0.08f, 1f));
        rootNode.attachChild(previewRoot);
        buildGround();
        if (inputManager != null) {
            registerInputMappings();
        }
        updateGroundVisibility();
        updateCameraFromDocument();
        updateOrbitCamera();
        previewReady = true;
        if (pendingDocument != null || pendingImportedEffectFile != null) {
            this.document = pendingDocument != null ? pendingDocument : this.document;
            File effectFile = pendingImportedEffectFile;
            boolean replay = pendingReplay;
            pendingDocument = null;
            pendingImportedEffectFile = null;
            pendingReplay = false;
            if (replay) {
                rebuildPreview(effectFile);
            } else {
                applyDocumentSettings(false);
                if (shouldRebuildFor(effectFile)) {
                    rebuildPreview(effectFile);
                }
            }
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        super.simpleUpdate(tpf);
        updateMouseCamera();
        float scaledTpf = (float) ((document != null ? document.getPlaybackSpeed() : 1.0) * tpf);
        nativePreview.update(tpf);
        effectTime += scaledTpf;
        for (EmitterRuntime emitter : emitters) {
            emitter.update(scaledTpf);
        }
    }

    @Override
    public void simpleRender(com.jme3.renderer.RenderManager rm) {
        nativePreview.updateCamera(cam.getViewMatrix(), cam.getProjectionMatrix(), cam.getLocation());
        nativePreview.render(cam.getWidth(), cam.getHeight());
        refreshNativeOverlay();
        super.simpleRender(rm);
    }

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        resetNativeOverlay();
    }

    public String getNativePreviewStatus() {
        return nativePreview.getStatus();
    }

    public String getPreviewMode() {
        if (loadedNativeEffectPath != null) {
            return "native";
        }
        if (!emitters.isEmpty()) {
            return "embedded";
        }
        return "none";
    }

    public String getLastCameraInteractionStatus() {
        return lastCameraInteractionStatus;
    }

    public void orbitByDegrees(float yawDeg, float pitchDeg) {
        enqueue(() -> {
            cameraYaw += yawDeg * FastMath.DEG_TO_RAD;
            cameraPitch = FastMath.clamp(
                    cameraPitch + pitchDeg * FastMath.DEG_TO_RAD,
                    FastMath.DEG_TO_RAD * -89f,
                    FastMath.DEG_TO_RAD * 89f);
            updateOrbitCamera();
            lastCameraInteractionStatus = String.format(Locale.ROOT, "orbit %.1f/%.1f", yawDeg, pitchDeg);
            publishStatus("Preview camera: " + lastCameraInteractionStatus);
            return null;
        });
    }

    public void panBy(float dx, float dy) {
        enqueue(() -> {
            Vector3f right = cam.getLeft().negate().mult(dx);
            Vector3f up = cam.getUp().mult(dy);
            cameraTarget.addLocal(right).addLocal(up);
            updateOrbitCamera();
            lastCameraInteractionStatus = String.format(Locale.ROOT, "pan %.2f/%.2f", dx, dy);
            publishStatus("Preview camera: " + lastCameraInteractionStatus);
            return null;
        });
    }

    public void resetCameraToDocument() {
        enqueue(() -> {
            updateCameraFromDocument();
            updateOrbitCamera();
            lastCameraInteractionStatus = "reset";
            publishStatus("Preview camera: reset");
            return null;
        });
    }

    public void beginOrbit(float x, float y) {
        enqueue(() -> {
            orbiting = true;
            panning = false;
            lastMousePos.set(x, y);
            lastCameraInteractionStatus = "orbit";
            publishStatus("Preview camera: orbit");
            return null;
        });
    }

    public void beginPan(float x, float y) {
        enqueue(() -> {
            panning = true;
            orbiting = false;
            lastMousePos.set(x, y);
            lastCameraInteractionStatus = "pan";
            publishStatus("Preview camera: pan");
            return null;
        });
    }

    public void updatePointer(float x, float y) {
        enqueue(() -> {
            if (!(orbiting || panning)) {
                lastMousePos.set(x, y);
                return null;
            }
            float dx = x - lastMousePos.x;
            float dy = y - lastMousePos.y;
            lastMousePos.set(x, y);

            if (orbiting) {
                cameraYaw -= dx * 0.005f;
                cameraPitch += dy * 0.005f;
                cameraPitch = FastMath.clamp(cameraPitch, FastMath.DEG_TO_RAD * -89f, FastMath.DEG_TO_RAD * 89f);
                updateOrbitCamera();
                lastCameraInteractionStatus = "orbit move";
            } else if (panning) {
                Vector3f right = cam.getLeft().negate().mult(dx * 0.015f);
                Vector3f up = cam.getUp().mult(dy * 0.015f);
                cameraTarget.addLocal(right).addLocal(up);
                updateOrbitCamera();
                lastCameraInteractionStatus = "pan move";
            }
            return null;
        });
    }

    public void endPointerInteraction() {
        enqueue(() -> {
            orbiting = false;
            panning = false;
            lastCameraInteractionStatus = "idle";
            publishStatus("Preview camera: idle");
            return null;
        });
    }

    public void zoomByWheel(float wheelRotation) {
        enqueue(() -> {
            cameraDistance = FastMath.clamp(cameraDistance + wheelRotation * 1.5f, 1.5f, 150f);
            updateOrbitCamera();
            lastCameraInteractionStatus = String.format(Locale.ROOT, "zoom %.2f", cameraDistance);
            publishStatus(String.format(Locale.ROOT, "Preview camera: zoom %.2f", cameraDistance));
            return null;
        });
    }

    private void registerInputMappings() {
        inputManager.addMapping(ACTION_RIGHT_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping(ACTION_MIDDLE_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_MIDDLE));
        inputManager.addMapping(ACTION_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACTION_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        inputManager.addListener((ActionListener) (name, isPressed, tpf) -> {
            if (ACTION_RIGHT_CLICK.equals(name)) {
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
        }, ACTION_RIGHT_CLICK, ACTION_MIDDLE_CLICK);

        inputManager.addListener((AnalogListener) (name, value, tpf) -> {
            if (ACTION_SCROLL_UP.equals(name)) {
                cameraDistance = Math.max(1.5f, cameraDistance - value * 25f);
                updateOrbitCamera();
            } else if (ACTION_SCROLL_DOWN.equals(name)) {
                cameraDistance = Math.min(150f, cameraDistance + value * 25f);
                updateOrbitCamera();
            }
        }, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN);
    }

    private void updateMouseCamera() {
        if (!(orbiting || panning) || inputManager == null) {
            return;
        }
        Vector2f currentMouse = inputManager.getCursorPosition();
        float dx = currentMouse.x - lastMousePos.x;
        float dy = currentMouse.y - lastMousePos.y;
        lastMousePos.set(currentMouse);

        if (orbiting) {
            cameraYaw -= dx * 0.005f;
            cameraPitch += dy * 0.005f;
            cameraPitch = FastMath.clamp(cameraPitch, FastMath.DEG_TO_RAD * -89f, FastMath.DEG_TO_RAD * 89f);
            updateOrbitCamera();
        } else if (panning) {
            Vector3f right = cam.getLeft().negate().mult(dx * 0.015f);
            Vector3f up = cam.getUp().mult(dy * 0.015f);
            cameraTarget.addLocal(right).addLocal(up);
            updateOrbitCamera();
        }
    }

    private void rebuildPreview(File importedEffectFile) {
        clearEmitters();
        nativePreview.unload();
        loadedNativeEffectPath = null;
        loadedEffectPath = importedEffectFile != null ? importedEffectFile.getAbsolutePath() : null;
        effectTime = 0f;
        applyDocumentSettings(true);

        if (importedEffectFile == null || !importedEffectFile.isFile()) {
            publishStatus("Imported effect file is missing.");
            return;
        }

        if (importedEffectFile.getName().toLowerCase(Locale.ROOT).endsWith(".efkproj")) {
            try {
                EffekseerProject project = EffekseerProjectParser.parse(importedEffectFile);
                projectTargetLocation.set(project.getTargetLocation());
                updateEffectOrientation();
            } catch (Exception ignored) {
                projectTargetLocation.set(Vector3f.ZERO);
                updateEffectOrientation();
            }
        } else {
            projectTargetLocation.set(Vector3f.ZERO);
            updateEffectOrientation();
        }

        File runtimeEffectFile = EffekseerNativeEffectResolver.resolveRuntimeEffect(importedEffectFile);
        if (runtimeEffectFile != null && nativePreview.isAvailable()) {
            if (nativePreview.loadEffect(runtimeEffectFile, document != null && document.isLoop(), document != null ? document.getPlaybackSpeed() : 1.0)) {
                loadedNativeEffectPath = runtimeEffectFile.getAbsolutePath();
                publishStatus("Native Effekseer preview active using " + runtimeEffectFile.getName() + ".");
                return;
            } else {
                publishStatus("Native Effekseer preview failed: " + nativePreview.getStatus() + ". Falling back to embedded preview.");
            }
        }

        if (!importedEffectFile.getName().toLowerCase(Locale.ROOT).endsWith(".efkproj")) {
            publishStatus("Embedded preview currently supports imported .efkproj files. Native runtime bridge was not available for this effect.");
            return;
        }

        try {
            registerAssetFolder(importedEffectFile.getParentFile());
            EffekseerProject project = EffekseerProjectParser.parse(importedEffectFile);
            projectTargetLocation.set(project.getTargetLocation());
            updateEffectOrientation();
            int usable = 0;
            for (EffekseerSpriteEmitter emitter : project.getEmitters()) {
                if (emitter.getTexturePath() == null || emitter.getTexturePath().isBlank()) {
                    continue;
                }
                emitters.add(new EmitterRuntime(emitter, null));
                usable++;
            }
            if (usable == 0) {
                publishStatus("This Effekseer project was parsed, but no sprite emitters with textures were found to preview.");
            } else {
                String mode = document != null && document.isLoop() ? "looping" : "one-shot";
                StringBuilder status = new StringBuilder();
                status.append("Embedded preview loaded ")
                        .append(usable)
                        .append(" previewable emitter")
                        .append(usable == 1 ? "" : "s")
                        .append(" in ")
                        .append(mode)
                        .append(" mode");
                if (project.getSkippedNodeCount() > 0) {
                    status.append("; skipped ")
                            .append(project.getSkippedNodeCount())
                            .append(" unsupported or non-renderable node")
                            .append(project.getSkippedNodeCount() == 1 ? "" : "s");
                }
                status.append(".");
                publishStatus(status.toString());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            publishStatus("Embedded preview could not parse this Effekseer project: " + ex.getMessage());
        }
    }

    private void clearEmitters() {
        for (EmitterRuntime emitter : emitters) {
            emitter.dispose();
        }
        emitters.clear();
        List<Spatial> children = new ArrayList<>(previewRoot.getChildren());
        for (Spatial child : children) {
            if (child != ground) {
                child.removeFromParent();
            }
        }
    }

    private void refreshNativeOverlay() {
        if (loadedNativeEffectPath == null) {
            hideNativeOverlay();
            return;
        }

        int renderWidth = Math.max(1, cam.getWidth());
        int renderHeight = Math.max(1, cam.getHeight());
        int guiWidth = guiViewPort != null && guiViewPort.getCamera() != null
                ? Math.max(1, guiViewPort.getCamera().getWidth())
                : renderWidth;
        int guiHeight = guiViewPort != null && guiViewPort.getCamera() != null
                ? Math.max(1, guiViewPort.getCamera().getHeight())
                : renderHeight;

        ensureNativeOverlay(renderWidth, renderHeight);
        if (nativePreviewPixels == null || nativePreviewTexture == null || nativePreviewPicture == null) {
            return;
        }
        nativePreviewPixels.clear();
        if (!nativePreview.readbackFrame(nativePreviewPixels, renderWidth, renderHeight)) {
            hideNativeOverlay();
            return;
        }
        nativePreviewPixels.position(0);
        nativePreviewTexture.getImage().setData(0, nativePreviewPixels);
        nativePreviewTexture.getImage().setUpdateNeeded();
        nativePreviewPicture.setWidth(guiWidth);
        nativePreviewPicture.setHeight(guiHeight);
        nativePreviewPicture.setPosition(0f, 0f);
        nativePreviewPicture.setLocalTranslation(0f, 0f, 10f);
        if (nativePreviewPicture.getParent() == null) {
            guiNode.attachChild(nativePreviewPicture);
        }
    }

    private void ensureNativeOverlay(int width, int height) {
        int requiredBytes = width * height * 4;
        if (nativePreviewPicture == null || nativePreviewTexture == null
                || nativePreviewWidth != width || nativePreviewHeight != height
                || nativePreviewPixels == null || nativePreviewPixels.capacity() != requiredBytes) {
            hideNativeOverlay();
            nativePreviewWidth = width;
            nativePreviewHeight = height;
            nativePreviewPixels = BufferUtils.createByteBuffer(requiredBytes);
            Image image = new Image(Image.Format.RGBA8, width, height, nativePreviewPixels);
            nativePreviewTexture = new Texture2D(image);
            nativePreviewTexture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
            nativePreviewTexture.setMagFilter(Texture.MagFilter.Bilinear);
            nativePreviewPicture = new Picture("EffekseerNativePreviewPicture");
            nativePreviewPicture.setTexture(assetManager, nativePreviewTexture, true);
            nativePreviewPicture.setWidth(width);
            nativePreviewPicture.setHeight(height);
            nativePreviewPicture.setLocalTranslation(0f, 0f, 10f);
        }
    }

    private void hideNativeOverlay() {
        if (nativePreviewPicture != null) {
            nativePreviewPicture.removeFromParent();
        }
    }

    private void resetNativeOverlay() {
        hideNativeOverlay();
        nativePreviewPicture = null;
        nativePreviewTexture = null;
        nativePreviewPixels = null;
        nativePreviewWidth = 0;
        nativePreviewHeight = 0;
    }

    private boolean shouldRebuildFor(File importedEffectFile) {
        String nextPath = importedEffectFile != null ? importedEffectFile.getAbsolutePath() : null;
        if (loadedEffectPath == null) {
            return true;
        }
        if (nextPath == null) {
            return true;
        }
        return !loadedEffectPath.equals(nextPath);
    }

    private void registerAssetFolder(File assetFolder) {
        if (assetFolder == null) {
            return;
        }
        String root = assetFolder.getAbsolutePath();
        if (root.equals(registeredLocatorRoot)) {
            return;
        }
        if (registeredLocatorRoot != null) {
            assetManager.unregisterLocator(registeredLocatorRoot, FileLocator.class);
        }
        assetManager.registerLocator(root, FileLocator.class);
        registeredLocatorRoot = root;
    }

    private void updateCameraFromDocument() {
        if (document == null) {
            return;
        }
        cameraTarget.set(0f, 1.4f, 0f);
        cameraDistance = Math.max(1.5f, (float) document.getCameraDistance());
        cameraYaw = FastMath.DEG_TO_RAD * (float) document.getCameraYawDeg();
        cameraPitch = FastMath.DEG_TO_RAD * (float) document.getCameraPitchDeg();
        switch (document.getBackgroundMode()) {
            case "light":
                viewPort.setBackgroundColor(new ColorRGBA(0.85f, 0.88f, 0.92f, 1f));
                break;
            case "transparent":
                viewPort.setBackgroundColor(new ColorRGBA(0f, 0f, 0f, 0f));
                break;
            case "dark":
            default:
                viewPort.setBackgroundColor(new ColorRGBA(0.04f, 0.05f, 0.08f, 1f));
                break;
        }
    }

    private void applyDocumentSettings(boolean includeCameraReset) {
        updateGroundVisibility();
        if (document == null) {
            return;
        }
        nativePreview.applyPreviewSettings(document.isLoop(), document.getPlaybackSpeed(), projectTargetLocation);
        switch (document.getBackgroundMode()) {
            case "light":
                viewPort.setBackgroundColor(new ColorRGBA(0.85f, 0.88f, 0.92f, 1f));
                break;
            case "transparent":
                viewPort.setBackgroundColor(new ColorRGBA(0f, 0f, 0f, 0f));
                break;
            case "dark":
            default:
                viewPort.setBackgroundColor(new ColorRGBA(0.04f, 0.05f, 0.08f, 1f));
                break;
        }
        // Camera defaults are part of the document, so inspector/header edits should apply immediately.
        updateCameraFromDocument();
        updateOrbitCamera();
    }

    private void updateGroundVisibility() {
        if (ground != null) {
            ground.setCullHint(document != null && document.isShowGround()
                    ? Spatial.CullHint.Inherit
                    : Spatial.CullHint.Always);
        }
    }

    private void updateOrbitCamera() {
        Vector3f offset = new Vector3f(
                cameraDistance * FastMath.cos(cameraPitch) * FastMath.cos(cameraYaw),
                cameraDistance * FastMath.sin(cameraPitch),
                cameraDistance * FastMath.cos(cameraPitch) * FastMath.sin(cameraYaw)
        );
        cam.setLocation(cameraTarget.add(offset));
        cam.lookAt(cameraTarget, Vector3f.UNIT_Y);
        billboardRotation.set(cam.getRotation());
    }

    private void updateEffectOrientation() {
        if (projectTargetLocation.lengthSquared() <= FastMath.ZERO_TOLERANCE) {
            effectOrientation.loadIdentity();
            return;
        }
        Vector3f direction = projectTargetLocation.normalize();
        effectOrientation.lookAt(direction, Vector3f.UNIT_Y);
    }

    private void buildGround() {
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(
                -8f, 0f, -8f,
                8f, 0f, -8f,
                8f, 0f, 8f,
                -8f, 0f, 8f
        ));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f
        ));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(0, 1, 2, 0, 2, 3));
        mesh.updateBound();

        ground = new Geometry("EffekseerPreviewGround", mesh);
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0.16f, 0.18f, 0.22f, 1f));
        ground.setMaterial(material);
        previewRoot.attachChild(ground);
    }

    private Material createParticleMaterial(EffekseerSpriteEmitter emitter) {
        Material material = new Material(
                assetManager,
                emitter.isAdditiveBlend()
                        ? "MatDefs/EffekseerAdditiveSprite.j3md"
                        : "Common/MatDefs/Misc/Unshaded.j3md"
        );
        Texture texture = assetManager.loadTexture(emitter.getTexturePath());
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        material.setTexture("ColorMap", texture);
        material.setColor("Color", emitter.getStartColor().clone());
        material.setFloat("AlphaDiscardThreshold", emitter.isAdditiveBlend() ? 0.03f : 0.01f);
        if (emitter.isAdditiveBlend()) {
            material.setFloat("BlackThreshold", 0.28f);
            material.setFloat("BrightnessPower", 1.15f);
            material.setFloat("EdgeSoftness", 0.18f);
        }
        material.getAdditionalRenderState().setBlendMode(
                emitter.isAdditiveBlend() ? RenderState.BlendMode.Additive : RenderState.BlendMode.Alpha
        );
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return material;
    }

    private Mesh createCenteredQuad() {
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(
                -0.5f, -0.5f, 0f,
                0.5f, -0.5f, 0f,
                0.5f, 0.5f, 0f,
                -0.5f, 0.5f, 0f
        ));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(
                0f, 1f,
                1f, 1f,
                1f, 0f,
                0f, 0f
        ));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(0, 1, 2, 0, 2, 3));
        mesh.updateBound();
        return mesh;
    }

    private void publishStatus(String status) {
        if (statusListener != null) {
            SwingUtilities.invokeLater(() -> statusListener.accept(status));
        }
    }

    private final class EmitterRuntime {
        private final EffekseerSpriteEmitter definition;
        private final EmitterRuntime parentRuntime;
        private final Material templateMaterial;
        private final Mesh quadMesh;
        private final List<ParticleInstance> particles = new ArrayList<>();
        private final List<EmitterRuntime> children = new ArrayList<>();
        private final Map<Long, RibbonTrail> ribbonTrails = new HashMap<>();
        private float emitterAge;
        private float spawnAccumulator;
        private int emittedCount;

        private EmitterRuntime(EffekseerSpriteEmitter definition, EmitterRuntime parentRuntime) {
            this.definition = definition;
            this.parentRuntime = parentRuntime;
            this.templateMaterial = createParticleMaterial(definition);
            this.quadMesh = definition.getRendererType() == EffekseerSpriteEmitter.RendererType.SPRITE ? createCenteredQuad() : null;
            for (EffekseerSpriteEmitter child : definition.getChildren()) {
                children.add(new EmitterRuntime(child, this));
            }
        }

        private void update(float tpf) {
            if (definition.getRendererType() == EffekseerSpriteEmitter.RendererType.SPRITE) {
                updateSpriteEmitter(tpf);
            } else {
                updateRibbonEmitter(tpf);
            }
            for (EmitterRuntime child : children) {
                child.update(tpf);
            }
        }

        private boolean canSpawn() {
            return definition.isInfinite() || emittedCount < definition.getMaxGeneration();
        }

        private void updateSpriteEmitter(float tpf) {
            emitterAge += tpf;
            float startDelaySeconds = definition.getStartDelayFrames() / FRAMES_PER_SECOND;
            float spawnIntervalSeconds = definition.hasExplicitGenerationTime()
                    ? Math.max(MIN_SPAWN_INTERVAL_SECONDS, definition.getGenerationFrames() / FRAMES_PER_SECOND)
                    : 1f / FRAMES_PER_SECOND;
            boolean loop = document != null && document.isLoop();
            boolean spawnFromParent = definition.isInheritParentPosition() && parentRuntime != null;

            if (spawnFromParent) {
                if (emitterAge >= startDelaySeconds) {
                    spawnAccumulator += tpf;
                    while (spawnAccumulator >= spawnIntervalSeconds && canSpawn()) {
                        spawnAccumulator -= spawnIntervalSeconds;
                        ParticleInstance parentParticle = parentRuntime.pickParentParticleForChildSpawn();
                        if (parentParticle == null) {
                            break;
                        }
                        spawnParticle(parentParticle);
                    }
                }
            } else if (emitterAge >= startDelaySeconds) {
                spawnAccumulator += tpf;
                while (spawnAccumulator >= spawnIntervalSeconds && canSpawn()) {
                    spawnAccumulator -= spawnIntervalSeconds;
                    spawnParticle(null);
                }
            }

            Iterator<ParticleInstance> iterator = particles.iterator();
            while (iterator.hasNext()) {
                ParticleInstance particle = iterator.next();
                particle.age += tpf;
                if (particle.age >= particle.lifeSeconds) {
                    particle.alive = false;
                    particle.geometry.removeFromParent();
                    iterator.remove();
                    continue;
                }

                updateParticleMotion(particle, tpf);
                updateParticleVisual(particle);
            }

            if (loop && !definition.isInfinite() && !Float.isInfinite(spawnIntervalSeconds)
                    && emittedCount >= definition.getMaxGeneration() && particles.isEmpty()) {
                emitterAge = 0f;
                spawnAccumulator = 0f;
                emittedCount = 0;
            }
        }

        private void updateRibbonEmitter(float tpf) {
            if (parentRuntime == null) {
                return;
            }

            for (ParticleInstance parentParticle : parentRuntime.particles) {
                RibbonTrail trail = ribbonTrails.computeIfAbsent(parentParticle.id, id -> new RibbonTrail(definition, parentParticle));
                trail.touch(parentParticle, tpf);
            }

            Iterator<Map.Entry<Long, RibbonTrail>> iterator = ribbonTrails.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, RibbonTrail> entry = iterator.next();
                RibbonTrail trail = entry.getValue();
                ParticleInstance parentParticle = parentRuntime.findParticleById(entry.getKey());
                trail.update(parentParticle, tpf);
                if (trail.isFinished()) {
                    trail.dispose();
                    iterator.remove();
                }
            }
        }

        private void spawnParticle(ParticleInstance parentParticle) {
            emittedCount++;
            ParticleInstance particle = new ParticleInstance();
            Vector3f basePosition = parentParticle != null ? parentParticle.position : Vector3f.ZERO;
            particle.id = nextParticleId++;
            particle.position.set(basePosition).addLocal(rotateEffectVector(randomBetween(definition.getPositionMin(), definition.getPositionMax())));
            particle.velocity.set(rotateEffectVector(randomBetween(definition.getVelocityMin(), definition.getVelocityMax())));
            particle.acceleration.set(rotateEffectVector(definition.getAcceleration().clone()));
            particle.sizeX = 1f;
            particle.sizeY = 1f;
            particle.scaleVelocity.set(definition.getScaleVelocity());
            particle.rotationVelocityDeg = definition.getRotationVelocityDeg();
            particle.lifeSeconds = definition.hasExplicitLife()
                    ? randomRange(definition.getLifeMinFrames(), definition.getLifeMaxFrames()) / FRAMES_PER_SECOND
                    : Math.max(2f, document != null && document.isLoop() ? 30f : 10f);
            particle.fadeOutSeconds = definition.getFadeOutFrames() / FRAMES_PER_SECOND;
            particle.startColor.set(definition.getStartColor());
            particle.endColor.set(definition.getEndColor());
            particle.alive = true;

            Geometry geometry = new Geometry(definition.getName() + "_particle", quadMesh);
            geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
            geometry.setMaterial(templateMaterial.clone());
            previewRoot.attachChild(geometry);
            particle.geometry = geometry;
            particles.add(particle);
            updateParticleVisual(particle);
        }

        private void updateParticleMotion(ParticleInstance particle, float tpf) {
            float forceScale = document != null ? (float) document.getMotionForceScale() : 1f;
            float orbitStrength = document != null ? (float) document.getMotionOrbitStrength() : 0f;
            float damping = document != null ? (float) document.getMotionDamping() : 0f;
            if (definition.getAttractiveForce() != 0f) {
                Vector3f toTarget = projectTargetLocation.subtract(particle.position);
                if (toTarget.lengthSquared() > FastMath.ZERO_TOLERANCE) {
                    float distance = toTarget.length();
                    Vector3f toward = toTarget.normalize();
                    Vector3f attraction = toward.mult(definition.getAttractiveForce() * distance * 6f * forceScale);
                    particle.velocity.addLocal(attraction.mult(tpf));
                    if (orbitStrength != 0f) {
                        Vector3f orbitAxis = Vector3f.UNIT_Y;
                        Vector3f tangent = orbitAxis.cross(toward);
                        if (tangent.lengthSquared() > FastMath.ZERO_TOLERANCE) {
                            tangent.normalizeLocal().multLocal(distance * orbitStrength * 3f);
                            particle.velocity.addLocal(tangent.mult(tpf));
                        }
                    }
                    float control = FastMath.clamp(definition.getAttractiveControl(), 0f, 1f) * tpf;
                    particle.velocity.interpolateLocal(toward.mult(Math.max(2f, particle.velocity.length())), control);
                }
            }
            if (damping > 0f) {
                particle.velocity.multLocal(Math.max(0f, 1f - damping * tpf));
            }
            particle.velocity.addLocal(particle.acceleration.mult(tpf));
            particle.position.addLocal(particle.velocity.mult(tpf));
            particle.sizeX = Math.max(0.01f, particle.sizeX + particle.scaleVelocity.x * tpf);
            particle.sizeY = Math.max(0.01f, particle.sizeY + particle.scaleVelocity.y * tpf);
            particle.rotationDeg += particle.rotationVelocityDeg * tpf;
        }

        private void updateParticleVisual(ParticleInstance particle) {
            float alpha = 1f;
            if (particle.fadeOutSeconds > 0f && particle.lifeSeconds - particle.age <= particle.fadeOutSeconds) {
                alpha = FastMath.clamp((particle.lifeSeconds - particle.age) / particle.fadeOutSeconds, 0f, 1f);
            }
            float progress = FastMath.clamp(particle.age / particle.lifeSeconds, 0f, 1f);
            ColorRGBA color = particle.startColor.clone().interpolateLocal(particle.endColor, progress);
            color.a *= alpha;
            particle.geometry.getMaterial().setColor("Color", color);
            particle.geometry.setLocalTranslation(particle.position);
            float baseWidth = FastMath.interpolateLinear(progress, definition.getWidth(), definition.getEndWidth());
            float baseHeight = FastMath.interpolateLinear(progress, definition.getHeight(), definition.getEndHeight());
            particle.geometry.setLocalScale(baseWidth * particle.sizeX, baseHeight * particle.sizeY, 1f);
            if (definition.isBillboard()) {
                particle.geometry.setLocalRotation(billboardRotation.mult(new Quaternion().fromAngles(0f, 0f, particle.rotationDeg * FastMath.DEG_TO_RAD)));
            } else {
                Quaternion localRotation = new Quaternion().fromAngles(
                        definition.getFixedRotationDeg().x * FastMath.DEG_TO_RAD,
                        definition.getFixedRotationDeg().y * FastMath.DEG_TO_RAD,
                        (definition.getFixedRotationDeg().z + particle.rotationDeg) * FastMath.DEG_TO_RAD
                );
                particle.geometry.setLocalRotation(effectOrientation.mult(localRotation));
            }
        }

        private ParticleInstance pickParentParticleForChildSpawn() {
            if (particles.isEmpty()) {
                return null;
            }
            int index = FastMath.nextRandomInt(0, particles.size() - 1);
            return particles.get(index);
        }

        private ParticleInstance findParticleById(long id) {
            for (ParticleInstance particle : particles) {
                if (particle.id == id) {
                    return particle;
                }
            }
            return null;
        }

        private Vector3f randomBetween(Vector3f min, Vector3f max) {
            return new Vector3f(randomRange(min.x, max.x), randomRange(min.y, max.y), randomRange(min.z, max.z));
        }

        private float randomRange(float min, float max) {
            if (max <= min) {
                return min;
            }
            return FastMath.nextRandomFloat() * (max - min) + min;
        }

        private void dispose() {
            for (ParticleInstance particle : particles) {
                if (particle.geometry != null) {
                    particle.geometry.removeFromParent();
                }
            }
            particles.clear();
            for (RibbonTrail trail : ribbonTrails.values()) {
                trail.dispose();
            }
            ribbonTrails.clear();
            for (EmitterRuntime child : children) {
                child.dispose();
            }
        }

        private Vector3f rotateEffectVector(Vector3f vector) {
            return effectOrientation.mult(vector);
        }
    }

    private final class RibbonTrail {
        private static final int MAX_POINTS = 24;
        private final long parentId;
        private final EffekseerSpriteEmitter definition;
        private final Geometry geometry;
        private final Mesh mesh = new Mesh();
        private final List<TrailPoint> points = new ArrayList<>();
        private float idleTime;
        private boolean parentAlive = true;
        private float spawnAccumulator;

        private RibbonTrail(EffekseerSpriteEmitter definition, ParticleInstance parentParticle) {
            this.parentId = parentParticle.id;
            this.definition = definition;
            initializeEmptyMesh();
            this.geometry = new Geometry(definitionSafeName(parentParticle) + "_trail", mesh);
            this.geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
            this.geometry.setMaterial(createParticleMaterial(definition));
            this.geometry.setCullHint(Spatial.CullHint.Always);
            previewRoot.attachChild(geometry);
        }

        private void touch(ParticleInstance parentParticle, float tpf) {
            parentAlive = true;
            idleTime = 0f;
            float spawnIntervalSeconds = definition.hasExplicitGenerationTime()
                    ? Math.max(MIN_SPAWN_INTERVAL_SECONDS, definition.getGenerationFrames() / FRAMES_PER_SECOND)
                    : 1f / FRAMES_PER_SECOND;
            spawnAccumulator += tpf;
            while (spawnAccumulator >= spawnIntervalSeconds) {
                spawnAccumulator -= spawnIntervalSeconds;
                addPoint(parentParticle.position.clone(), trailColor(parentParticle));
            }
            agePoints(tpf);
            rebuildMesh();
        }

        private void update(ParticleInstance parentParticle, float tpf) {
            if (parentParticle == null) {
                parentAlive = false;
                idleTime += tpf;
                agePoints(tpf);
            } else {
                touch(parentParticle, tpf);
            }
            if (!parentAlive && !points.isEmpty()) {
                rebuildMesh();
            }
        }

        private void addPoint(Vector3f position, ColorRGBA color) {
            if (!points.isEmpty() && points.get(points.size() - 1).position.distanceSquared(position) < 0.0025f) {
                return;
            }
            points.add(new TrailPoint(position, color));
            while (points.size() > MAX_POINTS) {
                points.remove(0);
            }
        }

        private void agePoints(float tpf) {
            float lifeSeconds = definition.hasExplicitLife()
                    ? Math.max(0.1f, definition.getLifeMaxFrames() / FRAMES_PER_SECOND)
                    : 0.75f;
            Iterator<TrailPoint> iterator = points.iterator();
            while (iterator.hasNext()) {
                TrailPoint point = iterator.next();
                point.age += tpf;
                float alpha = 1f - FastMath.clamp(point.age / lifeSeconds, 0f, 1f);
                point.color.a = point.baseAlpha * alpha;
                if (point.age >= lifeSeconds) {
                    iterator.remove();
                }
            }
        }

        private void rebuildMesh() {
            if (points.size() < 2) {
                initializeEmptyMesh();
                geometry.setCullHint(Spatial.CullHint.Always);
                return;
            }
            geometry.setCullHint(Spatial.CullHint.Inherit);
            int vertexCount = points.size() * 2;
            float[] positions = new float[vertexCount * 3];
            float[] texCoords = new float[vertexCount * 2];
            float[] colors = new float[vertexCount * 4];
            int[] indices = new int[(points.size() - 1) * 6];
            Vector3f camDir = cam.getDirection().normalize();

            for (int i = 0; i < points.size(); i++) {
                TrailPoint point = points.get(i);
                Vector3f prev = points.get(Math.max(0, i - 1)).position;
                Vector3f next = points.get(Math.min(points.size() - 1, i + 1)).position;
                Vector3f tangent = next.subtract(prev);
                if (tangent.lengthSquared() < FastMath.ZERO_TOLERANCE) {
                    tangent.set(Vector3f.UNIT_Y);
                } else {
                    tangent.normalizeLocal();
                }
                Vector3f side = definition.isBillboard()
                        ? camDir.cross(tangent).normalizeLocal()
                        : Vector3f.UNIT_X.clone();
                if (side.lengthSquared() < FastMath.ZERO_TOLERANCE) {
                    side.set(Vector3f.UNIT_X);
                }
                float width = Math.max(0.05f, definition.getWidth() * 0.5f);
                Vector3f left = point.position.add(side.mult(width));
                Vector3f right = point.position.add(side.negate().mult(width));

                writeVec3(positions, i * 6, left);
                writeVec3(positions, i * 6 + 3, right);

                float v = points.size() == 1 ? 0f : (float) i / (float) (points.size() - 1);
                texCoords[i * 4] = 0f;
                texCoords[i * 4 + 1] = v;
                texCoords[i * 4 + 2] = 1f;
                texCoords[i * 4 + 3] = v;

                writeColor(colors, i * 8, point.color);
                writeColor(colors, i * 8 + 4, point.color);
            }

            int idx = 0;
            for (int i = 0; i < points.size() - 1; i++) {
                int base = i * 2;
                indices[idx++] = base;
                indices[idx++] = base + 1;
                indices[idx++] = base + 2;
                indices[idx++] = base + 1;
                indices[idx++] = base + 3;
                indices[idx++] = base + 2;
            }

            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoords));
            mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors));
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
            mesh.updateBound();
        }

        private void initializeEmptyMesh() {
            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(
                    0f, 0f, 0f,
                    0f, 0f, 0f,
                    0f, 0f, 0f
            ));
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(
                    0f, 0f,
                    0f, 0f,
                    0f, 0f
            ));
            mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(
                    0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f
            ));
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(0, 1, 2));
            mesh.updateBound();
        }

        private boolean isFinished() {
            return !parentAlive && (idleTime > Math.max(0.25f, definition.getFadeOutFrames() / FRAMES_PER_SECOND) || points.isEmpty());
        }

        private ColorRGBA trailColor(ParticleInstance parentParticle) {
            float progress = FastMath.clamp(parentParticle.age / Math.max(parentParticle.lifeSeconds, 0.001f), 0f, 1f);
            return parentParticle.startColor.clone().interpolateLocal(parentParticle.endColor, progress);
        }

        private void dispose() {
            geometry.removeFromParent();
        }

        private String definitionSafeName(ParticleInstance parentParticle) {
            return definition.getName() + "_" + parentParticle.id;
        }

        private void writeVec3(float[] target, int offset, Vector3f value) {
            target[offset] = value.x;
            target[offset + 1] = value.y;
            target[offset + 2] = value.z;
        }

        private void writeColor(float[] target, int offset, ColorRGBA color) {
            target[offset] = color.r;
            target[offset + 1] = color.g;
            target[offset + 2] = color.b;
            target[offset + 3] = color.a;
        }
    }

    private static final class TrailPoint {
        private final Vector3f position;
        private final ColorRGBA color;
        private final float baseAlpha;
        private float age;

        private TrailPoint(Vector3f position, ColorRGBA color) {
            this.position = position;
            this.color = color;
            this.baseAlpha = color.a;
        }
    }

    private static final class ParticleInstance {
        private long id;
        private Geometry geometry;
        private final Vector3f position = new Vector3f();
        private final Vector3f velocity = new Vector3f();
        private final Vector3f acceleration = new Vector3f();
        private final Vector3f scaleVelocity = new Vector3f();
        private final ColorRGBA startColor = new ColorRGBA();
        private final ColorRGBA endColor = new ColorRGBA();
        private boolean alive;
        private float sizeX;
        private float sizeY;
        private float rotationDeg;
        private float rotationVelocityDeg;
        private float age;
        private float lifeSeconds;
        private float fadeOutSeconds;
    }
}
