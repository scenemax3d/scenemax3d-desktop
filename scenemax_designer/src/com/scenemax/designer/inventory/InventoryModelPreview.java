package com.scenemax.designer.inventory;

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
import com.jme3.light.Light;
import com.jme3.light.LightProbe;
import com.jme3.light.PointLight;
import com.jme3.material.MatParam;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.jme3.texture.Texture;
import com.scenemaxeng.projector.SceneMaxApp;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.Map;

public class InventoryModelPreview {

    private final PreviewApp app;
    private final Canvas canvas;

    public InventoryModelPreview(String projectPath, String resourcesFolder) {
        app = new PreviewApp(projectPath, resourcesFolder);

        AppSettings settings = new AppSettings(true);
        settings.setWidth(640);
        settings.setHeight(420);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setGammaCorrection(false);

        app.setSettings(settings);
        app.setPauseOnLostFocus(false);
        app.setShowSettings(false);
        app.createCanvas();

        JmeCanvasContext ctx = (JmeCanvasContext) app.getContext();
        ctx.setSystemListener(app);
        canvas = ctx.getCanvas();
        canvas.setMinimumSize(new Dimension(120, 120));
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (canvas.getWidth() > 0 && canvas.getHeight() > 0) {
                    app.enqueue(() -> {
                        app.reshape(canvas.getWidth(), canvas.getHeight());
                        return null;
                    });
                }
            }
        });
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public void start() {
        app.startCanvas();
    }

    public void stop() {
        app.stop();
    }

    public void previewModel(String path, Map<String, String> properties) {
        app.previewModel(path, properties);
    }

    private static class PreviewApp extends SceneMaxApp {
        private static final String ACTION_ORBIT = "InventoryPreviewOrbit";
        private static final String ACTION_SCROLL_UP = "InventoryPreviewScrollUp";
        private static final String ACTION_SCROLL_DOWN = "InventoryPreviewScrollDown";

        private final Node previewRoot = new Node("InventoryPreviewRoot");
        private final String projectPath;
        private final String resourcesFolder;
        private Spatial previewSpatial;
        private PointLight cameraLight;
        private float cameraDistance = 5f;
        private float yaw = (float) Math.toRadians(35);
        private float pitch = (float) Math.toRadians(18);
        private boolean orbiting;
        private final Vector2f lastMouse = new Vector2f();

        PreviewApp(String projectPath, String resourcesFolder) {
            this.projectPath = projectPath;
            this.resourcesFolder = resourcesFolder;
        }

        @Override
        public void simpleInitApp() {
            if (projectPath != null && !projectPath.isBlank()) {
                setWorkingFolder(new File(new File(projectPath, "scripts"), "_inventory_preview").getAbsolutePath());
            } else if (resourcesFolder != null && !resourcesFolder.isBlank()) {
                File projectRoot = new File(resourcesFolder).getParentFile();
                if (projectRoot != null) {
                    setWorkingFolder(new File(new File(projectRoot, "scripts"), "_inventory_preview").getAbsolutePath());
                }
            }
            super.simpleInitApp();
            tryRegisterLocator(new File("./resources-basic/resources"));
            tryRegisterLocator(new File("./resources"));
            if (resourcesFolder != null) {
                tryRegisterLocator(new File(resourcesFolder));
            }

            viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.065f, 0.08f, 1f));
            flyCam.setEnabled(false);
            setDisplayFps(false);
            setDisplayStatView(false);

            rootNode.attachChild(previewRoot);
            AmbientLight ambient = new AmbientLight();
            ambient.setColor(ColorRGBA.White.mult(1.1f));
            rootNode.addLight(ambient);

            DirectionalLight key = new DirectionalLight();
            key.setDirection(new Vector3f(-0.45f, -0.75f, -0.35f).normalizeLocal());
            key.setColor(ColorRGBA.White.mult(2.25f));
            rootNode.addLight(key);

            DirectionalLight fill = new DirectionalLight();
            fill.setDirection(new Vector3f(0.65f, -0.25f, 0.55f).normalizeLocal());
            fill.setColor(new ColorRGBA(0.75f, 0.85f, 1f, 1f).mult(1.45f));
            rootNode.addLight(fill);

            DirectionalLight rim = new DirectionalLight();
            rim.setDirection(new Vector3f(0.2f, -0.2f, 1f).normalizeLocal());
            rim.setColor(new ColorRGBA(0.9f, 0.95f, 1f, 1f).mult(1.15f));
            rootNode.addLight(rim);

            cameraLight = new PointLight();
            cameraLight.setColor(ColorRGBA.White.mult(1.8f));
            cameraLight.setRadius(120f);
            rootNode.addLight(cameraLight);

            installEnvironmentProbe();
            registerInput();
            showFallbackBox();
            updateCamera(Vector3f.ZERO);
        }

        @Override
        public void simpleUpdate(float tpf) {
            super.simpleUpdate(tpf);
            if (!orbiting) {
                previewRoot.rotate(0f, tpf * 0.25f, 0f);
            }
            if (orbiting && inputManager != null) {
                Vector2f current = inputManager.getCursorPosition();
                float dx = current.x - lastMouse.x;
                float dy = current.y - lastMouse.y;
                lastMouse.set(current);
                yaw -= dx * 0.006f;
                pitch = FastMath.clamp(pitch + dy * 0.006f, (float) Math.toRadians(-70), (float) Math.toRadians(70));
                updateCamera(currentCenter());
            }
        }

        void previewModel(String path, Map<String, String> properties) {
            if (path == null || path.isBlank()) {
                return;
            }
            enqueue(() -> {
                loadModel(path, properties);
                return null;
            });
        }

        private void loadModel(String path, Map<String, String> properties) {
            previewRoot.detachAllChildren();
            previewRoot.setLocalRotation(Quaternion.IDENTITY);
            try {
                Spatial model = assetManager.loadModel(path);
                float scaleX = parseFloat(properties.get("scaleX"), 1f);
                float scaleY = parseFloat(properties.get("scaleY"), scaleX);
                float scaleZ = parseFloat(properties.get("scaleZ"), scaleX);
                model.setLocalScale(scaleX, scaleY, scaleZ);
                model.setLocalTranslation(
                        parseFloat(properties.get("transX"), 0f),
                        parseFloat(properties.get("transY"), 0f),
                        parseFloat(properties.get("transZ"), 0f));
                model.rotate(0f, parseFloat(properties.get("rotateY"), 0f) * FastMath.DEG_TO_RAD, 0f);
                model.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                applyReadablePreviewMaterials(model);
                previewSpatial = model;
                previewRoot.attachChild(previewSpatial);
                fitCamera();
            } catch (Exception ex) {
                ex.printStackTrace();
                showFallbackBox();
            }
        }

        private void showFallbackBox() {
            previewRoot.detachAllChildren();
            previewSpatial = new Geometry("InventoryPreviewFallback", new Box(1f, 1f, 1f));
            previewRoot.attachChild(previewSpatial);
            fitCamera();
        }

        private void applyReadablePreviewMaterials(Spatial spatial) {
            if (spatial == null) {
                return;
            }
            spatial.depthFirstTraversal((SceneGraphVisitor) child -> {
                if (child instanceof Geometry) {
                    Geometry geometry = (Geometry) child;
                    Material material = createReadableMaterial(geometry.getMaterial());
                    if (material != null) {
                        geometry.setMaterial(material);
                    }
                }
            });
        }

        private Material createReadableMaterial(Material source) {
            if (source == null) {
                return null;
            }

            Material preview = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            Texture texture = firstTexture(source,
                    "BaseColorMap", "DiffuseMap", "ColorMap", "AlbedoMap", "Texture", "DiffuseTexture");
            if (texture != null) {
                preview.setTexture("ColorMap", texture);
                preview.setColor("Color", ColorRGBA.White);
            } else {
                preview.setColor("Color", firstColor(source,
                        new ColorRGBA(0.85f, 0.85f, 0.85f, 1f),
                        "BaseColor", "Diffuse", "Color", "Ambient"));
            }

            if (isTransparent(source)) {
                preview.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                preview.getAdditionalRenderState().setDepthWrite(false);
            }
            return preview;
        }

        private Texture firstTexture(Material material, String... names) {
            for (String name : names) {
                MatParam param = material.getParam(name);
                if (param instanceof MatParamTexture) {
                    return ((MatParamTexture) param).getTextureValue();
                }
            }
            for (MatParam param : material.getParams()) {
                if (param instanceof MatParamTexture) {
                    return ((MatParamTexture) param).getTextureValue();
                }
            }
            return null;
        }

        private ColorRGBA firstColor(Material material, ColorRGBA fallback, String... names) {
            for (String name : names) {
                MatParam param = material.getParam(name);
                if (param != null && param.getValue() instanceof ColorRGBA) {
                    ColorRGBA color = ((ColorRGBA) param.getValue()).clone();
                    if (color.a <= 0f) {
                        color.a = 1f;
                    }
                    return color;
                }
            }
            return fallback.clone();
        }

        private boolean isTransparent(Material material) {
            if (material == null) {
                return false;
            }
            RenderState state = material.getAdditionalRenderState();
            return state != null && state.getBlendMode() != RenderState.BlendMode.Off;
        }

        private void registerInput() {
            inputManager.addMapping(ACTION_ORBIT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
            inputManager.addMapping(ACTION_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
            inputManager.addMapping(ACTION_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
            inputManager.addListener((ActionListener) (name, isPressed, tpf) -> {
                if (ACTION_ORBIT.equals(name)) {
                    orbiting = isPressed;
                    if (isPressed) {
                        lastMouse.set(inputManager.getCursorPosition());
                    }
                }
            }, ACTION_ORBIT);
            inputManager.addListener((AnalogListener) (name, value, tpf) -> {
                if (ACTION_SCROLL_UP.equals(name)) {
                    cameraDistance = Math.max(0.6f, cameraDistance - value * 16f);
                    updateCamera(currentCenter());
                } else if (ACTION_SCROLL_DOWN.equals(name)) {
                    cameraDistance = Math.min(160f, cameraDistance + value * 16f);
                    updateCamera(currentCenter());
                }
            }, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN);
        }

        private void fitCamera() {
            previewRoot.updateGeometricState();
            BoundingVolume bound = previewRoot.getWorldBound();
            Vector3f center = Vector3f.ZERO.clone();
            float radius = 2f;
            if (bound instanceof BoundingBox) {
                BoundingBox box = (BoundingBox) bound;
                center = box.getCenter().clone();
                radius = Math.max(0.5f, box.getExtent(null).length());
            } else if (bound instanceof BoundingSphere) {
                BoundingSphere sphere = (BoundingSphere) bound;
                center = sphere.getCenter().clone();
                radius = Math.max(0.5f, sphere.getRadius());
            }
            cameraDistance = Math.max(2.2f, radius * 2.6f);
            updateCamera(center);
        }

        private Vector3f currentCenter() {
            previewRoot.updateGeometricState();
            BoundingVolume bound = previewRoot.getWorldBound();
            return bound == null ? Vector3f.ZERO : bound.getCenter();
        }

        private void updateCamera(Vector3f center) {
            float x = center.x + cameraDistance * FastMath.cos(pitch) * FastMath.sin(yaw);
            float y = center.y + cameraDistance * FastMath.sin(pitch);
            float z = center.z + cameraDistance * FastMath.cos(pitch) * FastMath.cos(yaw);
            Vector3f cameraLocation = new Vector3f(x, y, z);
            cam.setLocation(cameraLocation);
            cam.lookAt(center, Vector3f.UNIT_Y);
            if (cameraLight != null) {
                cameraLight.setPosition(cameraLocation);
                cameraLight.setRadius(Math.max(16f, cameraDistance * 6f));
            }
        }

        private void tryRegisterLocator(File folder) {
            if (folder == null || !folder.isDirectory() || assetManager == null) {
                return;
            }
            try {
                assetManager.registerLocator(folder.getCanonicalPath(), FileLocator.class);
            } catch (Exception ignored) {
            }
        }

        private void installEnvironmentProbe() {
            String[] probeAssets = {
                    "probes/Sky_Cloudy.j3o",
                    "probes/corsica_beach.j3o",
                    "probes/River_Road.j3o"
            };
            for (String probeAsset : probeAssets) {
                try {
                    Spatial spatial = assetManager.loadModel(probeAsset);
                    if (spatial instanceof Node) {
                        LightProbe probe = findLightProbe((Node) spatial);
                        if (probe != null) {
                            probe.getArea().setRadius(1000f);
                            probe.setPosition(Vector3f.ZERO);
                            rootNode.addLight(probe);
                            return;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        private LightProbe findLightProbe(Node node) {
            for (Light light : node.getLocalLightList()) {
                if (light instanceof LightProbe) {
                    node.removeLight(light);
                    return (LightProbe) light;
                }
            }
            for (Spatial child : node.getChildren()) {
                if (child instanceof Node) {
                    LightProbe probe = findLightProbe((Node) child);
                    if (probe != null) {
                        return probe;
                    }
                }
            }
            return null;
        }

        private float parseFloat(String value, float fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
    }
}
