package com.scenemax.designer.shader;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
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
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.scenemaxeng.projector.SceneMaxApp;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class EnvironmentShaderPreviewApp extends SceneMaxApp {

    private static final String ACTION_RIGHT_CLICK = "EnvironmentPreviewRightClick";
    private static final String ACTION_MIDDLE_CLICK = "EnvironmentPreviewMiddleClick";
    private static final String ACTION_SCROLL_UP = "EnvironmentPreviewScrollUp";
    private static final String ACTION_SCROLL_DOWN = "EnvironmentPreviewScrollDown";

    private final Node previewRoot = new Node("EnvironmentPreviewRoot");
    private Geometry fogCard;
    private Geometry ground;
    private Geometry subjectBox;
    private final AmbientLight previewAmbient = new AmbientLight();
    private final DirectionalLight previewSun = new DirectionalLight();

    private EnvironmentShaderDocument currentDocument;
    private File currentFile;
    private String resourcesFolder;
    private float elapsedTime = 0f;

    private float cameraDistance = 11f;
    private float cameraYaw = (float) Math.toRadians(32);
    private float cameraPitch = (float) Math.toRadians(20);
    private final Vector3f cameraTarget = new Vector3f(0f, 1.6f, 0f);
    private boolean orbiting = false;
    private boolean panning = false;
    private final Vector2f lastMousePos = new Vector2f();

    @Override
    public void simpleInitApp() {
        if (resourcesFolder != null && !resourcesFolder.isBlank()) {
            File resourcesRoot = new File(resourcesFolder);
            File projectRoot = resourcesRoot.getParentFile();
            if (projectRoot != null) {
                File previewWorkingFolder = new File(new File(projectRoot, "scripts"), "_environment_shader_preview");
                setWorkingFolder(previewWorkingFolder.getAbsolutePath());
            }
        }

        super.simpleInitApp();
        viewPort.setBackgroundColor(new ColorRGBA(0.36f, 0.52f, 0.70f, 1f));
        flyCam.setEnabled(false);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(previewRoot);
        buildScene();
        rootNode.addLight(previewAmbient);
        rootNode.addLight(previewSun);
        registerInputMappings();
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
                Vector3f right = cam.getLeft().negate().mult(dx * 0.015f);
                Vector3f up = cam.getUp().mult(dy * 0.015f);
                cameraTarget.addLocal(right).addLocal(up);
                updateOrbitCamera();
            }
        }

        if (fogCard != null && fogCard.getMaterial() != null) {
            fogCard.getMaterial().setFloat("Time", elapsedTime);
        }
        applySceneSettings();
    }

    public void setResourcesFolder(String resourcesFolder) {
        this.resourcesFolder = resourcesFolder;
    }

    public void updatePreview(File documentFile, EnvironmentShaderDocument document) {
        if (document == null || documentFile == null) {
            return;
        }
        currentFile = documentFile;
        currentDocument = document.copy();
        enqueue(() -> {
            applyDocument();
            return null;
        });
    }

    private void buildScene() {
        fogCard = new Geometry("EnvironmentFogCard", new Quad(16f, 9f));
        fogCard.setLocalTranslation(-8f, -1.5f, 3.2f);
        fogCard.setQueueBucket(RenderQueue.Bucket.Transparent);
        previewRoot.attachChild(fogCard);

        ground = new Geometry("EnvironmentGround", new Box(8f, 0.05f, 8f));
        ground.setLocalTranslation(0f, -0.05f, 0f);
        Material groundMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        groundMat.setBoolean("UseMaterialColors", true);
        groundMat.setColor("Diffuse", new ColorRGBA(0.26f, 0.32f, 0.28f, 1f));
        groundMat.setColor("Ambient", new ColorRGBA(0.18f, 0.22f, 0.20f, 1f));
        groundMat.setColor("Specular", new ColorRGBA(0.05f, 0.05f, 0.05f, 1f));
        ground.setMaterial(groundMat);
        ground.setShadowMode(RenderQueue.ShadowMode.Receive);
        previewRoot.attachChild(ground);

        subjectBox = new Geometry("EnvironmentSubject", new Box(0.8f, 1.4f, 0.8f));
        subjectBox.setLocalTranslation(0f, 1.4f, -0.8f);
        Material boxMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        boxMat.setBoolean("UseMaterialColors", true);
        boxMat.setColor("Diffuse", new ColorRGBA(0.78f, 0.58f, 0.30f, 1f));
        boxMat.setColor("Ambient", new ColorRGBA(0.40f, 0.28f, 0.14f, 1f));
        boxMat.setColor("Specular", new ColorRGBA(0.08f, 0.08f, 0.08f, 1f));
        subjectBox.setMaterial(boxMat);
        subjectBox.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        previewRoot.attachChild(subjectBox);

        applySceneSettings();
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
                cameraDistance = Math.max(2f, cameraDistance - value * 25f);
                updateOrbitCamera();
            } else if (ACTION_SCROLL_DOWN.equals(name)) {
                cameraDistance = Math.min(60f, cameraDistance + value * 25f);
                updateOrbitCamera();
            }
        }, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN);
    }

    private void applyDocument() {
        if (currentDocument == null || currentFile == null || fogCard == null) {
            return;
        }

        if (resourcesFolder == null || resourcesFolder.isBlank()) {
            applyFallbackMaterial();
            return;
        }

        try {
            currentDocument.exportRuntimeAssets(currentFile, resourcesFolder);
            assetManager.clearCache();

            String assetBase = EnvironmentShaderDocument.getRuntimeAssetBase(currentFile);
            Material material = new Material(assetManager, assetBase + ".j3md");
            applyMaterialDefaultsFromAsset(assetBase, material);
            material.setColor("FogColor", currentDocument.getFogColor());
            material.setFloat("FogDensity", currentDocument.getFogDensity());
            material.setFloat("FogNearDistance", currentDocument.getFogNearDistance());
            material.setFloat("FogFarDistance", currentDocument.getFogFarDistance());
            material.setColor("RainColor", currentDocument.getRainColor());
            material.setFloat("RainIntensity", currentDocument.getRainIntensity());
            material.setFloat("RainSpeed", currentDocument.getRainSpeed());
            material.setFloat("RainAngle", currentDocument.getRainAngle());
            material.setFloat("OverlayOpacity", currentDocument.getOverlayOpacity());
            material.setColor("SnowColor", currentDocument.getSnowColor());
            material.setFloat("SnowIntensity", currentDocument.getSnowIntensity());
            material.setFloat("SnowSpeed", currentDocument.getSnowSpeed());
            material.setFloat("SnowFlakeSize", currentDocument.getSnowFlakeSize());
            material.setFloat("WindDirection", currentDocument.getWindDirection());
            material.setFloat("WindStrength", currentDocument.getWindStrength());
            material.setFloat("WindGustiness", currentDocument.getWindGustiness());
            material.setColor("SkyTint", currentDocument.getSkyTint());
            material.setFloat("SkyBrightness", currentDocument.getSkyBrightness());
            material.setFloat("SkyHorizonBlend", currentDocument.getSkyHorizonBlend());
            material.setColor("AmbientColor", currentDocument.getAmbientColor());
            material.setFloat("AmbientIntensity", currentDocument.getAmbientIntensity());
            material.setColor("LightColor", currentDocument.getLightColor());
            material.setFloat("LightIntensity", currentDocument.getLightIntensity());
            material.setFloat("LightPitch", currentDocument.getLightPitch());
            material.setFloat("LightYaw", currentDocument.getLightYaw());
            material.setFloat("Time", elapsedTime);
            fogCard.setMaterial(material);
            applySceneSettings();
        } catch (Exception ex) {
            ex.printStackTrace();
            applyFallbackMaterial();
        }
    }

    private void applyFallbackMaterial() {
        Material fallback = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA color = currentDocument != null ? currentDocument.getFogColor() : new ColorRGBA(0.72f, 0.80f, 0.90f, 0.55f);
        fallback.setColor("Color", new ColorRGBA(color.r, color.g, color.b, 0.55f));
        fallback.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        fogCard.setMaterial(fallback);
        applySceneSettings();
    }

    private void applyMaterialDefaultsFromAsset(String assetBase, Material material) {
        if (assetBase == null || material == null) {
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
                    applyMaterialDefaultLine(material, line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void applyMaterialDefaultLine(Material material, String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("Material ") || trimmed.equals("{") || trimmed.equals("}")) {
            return;
        }

        int sep = trimmed.indexOf(':');
        if (sep <= 0) {
            return;
        }

        String paramName = trimmed.substring(0, sep).trim();
        String rawValue = trimmed.substring(sep + 1).trim();
        if (rawValue.isEmpty()) {
            return;
        }

        try {
            if ("FogColor".equals(paramName) || "RainColor".equals(paramName)
                    || "SnowColor".equals(paramName) || "SkyTint".equals(paramName)
                    || "AmbientColor".equals(paramName) || "LightColor".equals(paramName)) {
                String[] parts = rawValue.split("\\s+");
                if (parts.length >= 4) {
                    material.setColor(paramName, new ColorRGBA(
                            Float.parseFloat(parts[0]),
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                    ));
                }
            } else {
                material.setFloat(paramName, Float.parseFloat(rawValue));
            }
        } catch (Exception ignored) {
        }
    }

    private void updateOrbitCamera() {
        float x = cameraTarget.x + cameraDistance * FastMath.cos(cameraPitch) * FastMath.sin(cameraYaw);
        float y = cameraTarget.y + cameraDistance * FastMath.sin(cameraPitch);
        float z = cameraTarget.z + cameraDistance * FastMath.cos(cameraPitch) * FastMath.cos(cameraYaw);
        cam.setLocation(new Vector3f(x, y, z));
        cam.lookAt(cameraTarget, Vector3f.UNIT_Y);
    }

    private void applySceneSettings() {
        if (currentDocument == null) {
            previewAmbient.setColor(new ColorRGBA(0.45f, 0.45f, 0.48f, 1f));
            previewSun.setColor(new ColorRGBA(1f, 0.96f, 0.88f, 1f));
            previewSun.setDirection(new Vector3f(-0.4f, -0.8f, -0.3f).normalizeLocal());
            viewPort.setBackgroundColor(new ColorRGBA(0.36f, 0.52f, 0.70f, 1f));
            return;
        }

        ColorRGBA sky = currentDocument.getSkyTint();
        float skyBrightness = currentDocument.getSkyBrightness();
        viewPort.setBackgroundColor(new ColorRGBA(
                clamp01(sky.r * skyBrightness),
                clamp01(sky.g * skyBrightness),
                clamp01(sky.b * skyBrightness),
                1f
        ));

        ColorRGBA ambient = currentDocument.getAmbientColor().mult(currentDocument.getAmbientIntensity());
        previewAmbient.setColor(new ColorRGBA(clamp01(ambient.r), clamp01(ambient.g), clamp01(ambient.b), 1f));

        ColorRGBA light = currentDocument.getLightColor().mult(currentDocument.getLightIntensity());
        previewSun.setColor(new ColorRGBA(clamp01(light.r), clamp01(light.g), clamp01(light.b), 1f));
        previewSun.setDirection(buildLightDirection(currentDocument.getLightPitch(), currentDocument.getLightYaw()));

        if (ground != null && ground.getMaterial() != null) {
            ground.getMaterial().setColor("Ambient", new ColorRGBA(
                    clamp01(ambient.r * 0.7f), clamp01(ambient.g * 0.7f), clamp01(ambient.b * 0.7f), 1f));
        }
        if (subjectBox != null && subjectBox.getMaterial() != null) {
            float windBend = currentDocument.getLayers().contains(EnvironmentShaderLayerType.WIND)
                    ? FastMath.sin(elapsedTime * (1.5f + currentDocument.getWindGustiness() * 2.5f))
                    * currentDocument.getWindStrength() * 0.08f
                    : 0f;
            subjectBox.setLocalRotation(new Quaternion().fromAngles(0f, windBend, 0f));
            subjectBox.getMaterial().setColor("Ambient", new ColorRGBA(
                    clamp01(ambient.r * 0.8f), clamp01(ambient.g * 0.8f), clamp01(ambient.b * 0.8f), 1f));
        }
    }

    private Vector3f buildLightDirection(float pitchDeg, float yawDeg) {
        float pitch = pitchDeg * FastMath.DEG_TO_RAD;
        float yaw = yawDeg * FastMath.DEG_TO_RAD;
        float x = FastMath.cos(pitch) * FastMath.sin(yaw);
        float y = FastMath.sin(pitch);
        float z = FastMath.cos(pitch) * FastMath.cos(yaw);
        return new Vector3f(-x, -y, -z).normalizeLocal();
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
