package com.scenemax.designer.shader;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;

import java.io.File;

public class ShaderPreviewApp extends SimpleApplication {

    private final Node pivotNode = new Node("ShaderPreviewPivot");
    private Geometry previewGeometry;
    private ShaderDocument currentDocument;
    private File currentFile;
    private String resourcesFolder;
    private boolean locatorRegistered = false;
    private Material currentMaterial;
    private float elapsedTime = 0f;

    @Override
    public void simpleInitApp() {
        viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.08f, 0.12f, 1f));
        flyCam.setEnabled(false);
        setDisplayFps(false);
        setDisplayStatView(false);

        cam.setLocation(new Vector3f(0, 1.35f, 4.6f));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);

        rootNode.attachChild(pivotNode);
        addLights();
        rebuildGeometry(ShaderPreviewTarget.BOX);
    }

    @Override
    public void simpleUpdate(float tpf) {
        elapsedTime += tpf;
        if (previewGeometry != null) {
            float speed = currentDocument != null && currentDocument.getPreviewTarget() == ShaderPreviewTarget.SPRITE ? 0.2f : 0.45f;
            pivotNode.rotate(0f, tpf * speed, 0f);
        }
        if (currentMaterial != null) {
            currentMaterial.setFloat("Time", elapsedTime);
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

    private void applyDocument() {
        registerLocatorIfNeeded();
        rebuildGeometry(currentDocument.getPreviewTarget());

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
            previewGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);
            previewGeometry.setMaterial(material);
            currentMaterial = material;
        } catch (Exception ex) {
            ex.printStackTrace();
            applyFallbackMaterial();
        }
    }

    private void applyFallbackMaterial() {
        if (previewGeometry == null) {
            return;
        }
        Material fallback = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA color = currentDocument != null ? currentDocument.getMainColor() : new ColorRGBA(0.9f, 0.6f, 0.2f, 1f);
        fallback.setColor("Color", color);
        previewGeometry.setQueueBucket(RenderQueue.Bucket.Opaque);
        previewGeometry.setMaterial(fallback);
        currentMaterial = fallback;
    }

    private void registerLocatorIfNeeded() {
        if (locatorRegistered || resourcesFolder == null || resourcesFolder.isBlank()) {
            return;
        }
        try {
            File root = new File(resourcesFolder);
            if (root.exists()) {
                assetManager.registerLocator(root.getCanonicalPath(), FileLocator.class);
                locatorRegistered = true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void rebuildGeometry(ShaderPreviewTarget target) {
        if (previewGeometry != null) {
            previewGeometry.removeFromParent();
        }

        ShaderPreviewTarget safeTarget = target != null ? target : ShaderPreviewTarget.BOX;
        switch (safeTarget) {
            case SPHERE:
                previewGeometry = new Geometry("ShaderPreviewSphere", new Sphere(48, 48, 1f));
                previewGeometry.setLocalTranslation(0f, 0f, 0f);
                break;
            case SPRITE:
                previewGeometry = new Geometry("ShaderPreviewSprite", new Quad(2.4f, 2.4f));
                previewGeometry.setLocalTranslation(-1.2f, -1.2f, 0f);
                previewGeometry.rotate(0f, FastMath.PI, 0f);
                break;
            case BOX:
            default:
                previewGeometry = new Geometry("ShaderPreviewBox", new Box(0.95f, 0.95f, 0.95f));
                previewGeometry.setLocalTranslation(0f, 0f, 0f);
                break;
        }

        pivotNode.detachAllChildren();
        pivotNode.attachChild(previewGeometry);
        pivotNode.setLocalRotation(Quaternion.IDENTITY);
        applyFallbackMaterial();

        if (safeTarget == ShaderPreviewTarget.SPRITE) {
            previewGeometry.setShadowMode(RenderQueue.ShadowMode.Off);
        } else {
            previewGeometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        }
    }
}
