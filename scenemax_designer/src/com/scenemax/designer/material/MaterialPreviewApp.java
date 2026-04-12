package com.scenemax.designer.material;

import com.jme3.asset.plugins.FileLocator;
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
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;
import com.scenemaxeng.projector.SceneMaxApp;

import java.io.File;

public class MaterialPreviewApp extends SceneMaxApp {

    private static final String ACTION_ROTATE = "MaterialPreviewRotate";
    private static final String ACTION_SCALE_UP = "MaterialPreviewScaleUp";
    private static final String ACTION_SCALE_DOWN = "MaterialPreviewScaleDown";

    private final Node previewRoot = new Node("MaterialPreviewRoot");
    private Geometry previewGeometry;
    private MaterialDocument currentDocument;
    private String resourcesFolder;
    private MaterialPreviewShape loadedShape = null;
    private boolean rotating = false;
    private final Vector2f lastMouse = new Vector2f();
    private float rotationX = -0.25f;
    private float rotationY = 0.45f;
    private float interactionScale = 1f;

    @Override
    public void simpleInitApp() {
        if (resourcesFolder != null && !resourcesFolder.isBlank()) {
            File resourcesRoot = new File(resourcesFolder);
            File projectRoot = resourcesRoot.getParentFile();
            if (projectRoot != null) {
                File previewWorkingFolder = new File(new File(projectRoot, "scripts"), "_material_preview");
                setWorkingFolder(previewWorkingFolder.getAbsolutePath());
            }
        }

        super.simpleInitApp();
        registerStarterTextureFallbacks();
        viewPort.setBackgroundColor(new ColorRGBA(0.07f, 0.08f, 0.1f, 1f));
        flyCam.setEnabled(false);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(previewRoot);

        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setColor(ColorRGBA.White.mult(0.5f));
        rootNode.addLight(ambientLight);

        DirectionalLight keyLight = new DirectionalLight();
        keyLight.setDirection(new Vector3f(-0.5f, -1f, -0.3f).normalizeLocal());
        keyLight.setColor(ColorRGBA.White.mult(1.4f));
        rootNode.addLight(keyLight);

        DirectionalLight fillLight = new DirectionalLight();
        fillLight.setDirection(new Vector3f(0.6f, -0.35f, 0.45f).normalizeLocal());
        fillLight.setColor(new ColorRGBA(0.55f, 0.65f, 0.85f, 1f));
        rootNode.addLight(fillLight);

        cam.setLocation(new Vector3f(0f, 0f, 4.8f));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        registerInput();
        rebuildShape(MaterialPreviewShape.BOX);
    }

    @Override
    public void simpleUpdate(float tpf) {
        super.simpleUpdate(tpf);
        if (rotating && inputManager != null) {
            Vector2f current = inputManager.getCursorPosition();
            float dx = current.x - lastMouse.x;
            float dy = current.y - lastMouse.y;
            lastMouse.set(current);
            rotationY += dx * 0.01f;
            rotationX = FastMath.clamp(rotationX + dy * 0.01f, -1.2f, 1.2f);
            applyPreviewTransform();
        }
    }

    public void setResourcesFolder(String resourcesFolder) {
        this.resourcesFolder = resourcesFolder;
    }

    public void updatePreview(MaterialDocument document) {
        if (document == null) {
            return;
        }
        currentDocument = document.copy();
        enqueue(() -> {
            applyDocument();
            return null;
        });
    }

    private void registerStarterTextureFallbacks() {
        registerLocatorIfDirectory(new File("./resources-basic/resources"));
        registerLocatorIfDirectory(new File("./resources"));
    }

    private void registerLocatorIfDirectory(File root) {
        if (root == null || !root.isDirectory()) {
            return;
        }
        try {
            assetManager.registerLocator(root.getCanonicalPath(), FileLocator.class);
        } catch (Exception ignored) {
        }
    }

    private void registerInput() {
        inputManager.addMapping(ACTION_ROTATE, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(ACTION_SCALE_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACTION_SCALE_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        inputManager.addListener((ActionListener) (name, isPressed, tpf) -> {
            if (ACTION_ROTATE.equals(name)) {
                rotating = isPressed;
                if (isPressed) {
                    lastMouse.set(inputManager.getCursorPosition());
                }
            }
        }, ACTION_ROTATE);

        inputManager.addListener((AnalogListener) (name, value, tpf) -> {
            if (ACTION_SCALE_UP.equals(name)) {
                interactionScale = Math.min(3.5f, interactionScale + value * 2.2f);
                applyPreviewTransform();
            } else if (ACTION_SCALE_DOWN.equals(name)) {
                interactionScale = Math.max(0.25f, interactionScale - value * 2.2f);
                applyPreviewTransform();
            }
        }, ACTION_SCALE_UP, ACTION_SCALE_DOWN);
    }

    private void applyDocument() {
        if (currentDocument == null) {
            return;
        }

        if (loadedShape != currentDocument.getPreviewShape()) {
            rebuildShape(currentDocument.getPreviewShape());
        }

        if (previewGeometry == null) {
            return;
        }

        Material material = buildMaterial(currentDocument);
        previewGeometry.setMaterial(material);
        boolean transparent = currentDocument.isTransparent() || currentDocument.getOpacity() < 0.999f;
        previewGeometry.setQueueBucket(transparent ? RenderQueue.Bucket.Transparent : RenderQueue.Bucket.Opaque);
        interactionScale = 1f;
        applyPreviewTransform();
    }

    private void rebuildShape(MaterialPreviewShape shape) {
        previewRoot.detachAllChildren();
        loadedShape = shape;
        if (shape == MaterialPreviewShape.SPHERE) {
            previewGeometry = new Geometry("MaterialPreviewSphere", new Sphere(48, 48, 1f));
        } else {
            previewGeometry = new Geometry("MaterialPreviewBox", new Box(0.95f, 0.95f, 0.95f));
        }
        previewRoot.attachChild(previewGeometry);
        applyPreviewTransform();
    }

    private void applyPreviewTransform() {
        float baseScale = currentDocument != null ? currentDocument.getPreviewScale() : 1f;
        float actualScale = baseScale * interactionScale;
        previewRoot.setLocalScale(actualScale);
        previewRoot.setLocalRotation(new com.jme3.math.Quaternion().fromAngles(rotationX, rotationY, 0f));
    }

    private Material buildMaterial(MaterialDocument doc) {
        Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        ColorRGBA diffuse = doc.getBaseColor();
        diffuse.a = doc.getOpacity();
        ColorRGBA ambient = new ColorRGBA(
                diffuse.r * doc.getAmbientStrength(),
                diffuse.g * doc.getAmbientStrength(),
                diffuse.b * doc.getAmbientStrength(),
                diffuse.a
        );
        float specular = doc.getSpecularStrength();
        ColorRGBA specularColor = new ColorRGBA(specular, specular, specular, 1f);
        ColorRGBA glowTint = doc.getGlowColor();
        ColorRGBA glow = new ColorRGBA(
                glowTint.r * doc.getGlowStrength(),
                glowTint.g * doc.getGlowStrength(),
                glowTint.b * doc.getGlowStrength(),
                diffuse.a
        );

        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", diffuse);
        material.setColor("Ambient", ambient);
        material.setColor("Specular", specularColor);
        material.setColor("GlowColor", glow);
        material.setFloat("Shininess", doc.getShininess());
        if (doc.getAlphaDiscardThreshold() > 0f) {
            material.setFloat("AlphaDiscardThreshold", doc.getAlphaDiscardThreshold());
        }

        loadTextureIfPresent(material, "DiffuseMap", doc.getDiffuseTexture());
        loadTextureIfPresent(material, "NormalMap", doc.getNormalTexture());
        loadTextureIfPresent(material, "GlowMap", doc.getGlowTexture());

        if (doc.isDoubleSided()) {
            material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        }
        if (doc.isTransparent() || doc.getOpacity() < 0.999f) {
            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        }

        return material;
    }

    private void loadTextureIfPresent(Material material, String paramName, String texturePath) {
        if (material == null || texturePath == null || texturePath.isBlank()) {
            return;
        }
        try {
            Texture texture = assetManager.loadTexture(texturePath);
            texture.setWrap(Texture.WrapMode.Repeat);
            material.setTexture(paramName, texture);
        } catch (Exception ignored) {
        }
    }
}
