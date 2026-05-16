package com.scenemax.designer.motion;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
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
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;
import com.scenemaxeng.common.motion.ThrowMotionDefinition;
import com.scenemaxeng.common.motion.ThrowMotionSample;
import com.scenemaxeng.common.motion.ThrowMotionSampler;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class ThrowMotionPreviewApp extends SimpleApplication {
    private static final String ACTION_LEFT = "ThrowMotionPreviewLeft";
    private static final String ACTION_SCROLL_UP = "ThrowMotionPreviewScrollUp";
    private static final String ACTION_SCROLL_DOWN = "ThrowMotionPreviewScrollDown";

    private final File resourcesRoot;
    private final Node previewRoot = new Node("ThrowMotionPreviewRoot");
    private final Node projectileRoot = new Node("ThrowMotionProjectileRoot");
    private final Node trajectoryRoot = new Node("ThrowMotionTrajectoryRoot");
    private final Node samplesRoot = new Node("ThrowMotionSamplesRoot");
    private final Node velocityRoot = new Node("ThrowMotionVelocityRoot");
    private final Node targetRoot = new Node("ThrowMotionTargetRoot");
    private ThrowMotionDefinition definition;
    private String definitionSnapshot = "";
    private List<ThrowMotionSample> samples = new ArrayList<>();
    private boolean playing;
    private boolean showTrajectory = true;
    private boolean showSamples = true;
    private float simulationTime;
    private float targetDistance = 12f;
    private float targetHeight = 0f;
    private float cameraDistance = 18f;
    private float yaw = (float) Math.toRadians(35);
    private float pitch = (float) Math.toRadians(22);
    private boolean orbiting;
    private final Vector2f lastMouse = new Vector2f();
    private PointLight cameraLight;
    private Consumer<String> statusChangedCallback;

    ThrowMotionPreviewApp(File resourcesRoot) {
        this.resourcesRoot = resourcesRoot;
    }

    @Override
    public void simpleInitApp() {
        tryRegisterLocator(new File("./resources-basic/resources"));
        tryRegisterLocator(new File("./resources"));
        tryRegisterLocator(resourcesRoot);

        viewPort.setBackgroundColor(new ColorRGBA(0.045f, 0.05f, 0.058f, 1f));
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(previewRoot);
        previewRoot.attachChild(projectileRoot);
        previewRoot.attachChild(trajectoryRoot);
        previewRoot.attachChild(samplesRoot);
        previewRoot.attachChild(velocityRoot);
        previewRoot.attachChild(targetRoot);
        buildEnvironment();
        registerInput();
        rebuildPreview(true);
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (orbiting && inputManager != null) {
            Vector2f current = inputManager.getCursorPosition();
            float dx = current.x - lastMouse.x;
            float dy = current.y - lastMouse.y;
            lastMouse.set(current);
            yaw -= dx * 0.006f;
            pitch = FastMath.clamp(pitch + dy * 0.006f, (float) Math.toRadians(-70), (float) Math.toRadians(70));
            updateCamera(currentCenter());
        }
        if (playing) {
            simulationTime += tpf;
            if (!samples.isEmpty() && simulationTime > samples.get(samples.size() - 1).getTime()) {
                simulationTime = 0f;
            }
            updateProjectileAtTime(simulationTime);
        }
    }

    void setStatusChangedCallback(Consumer<String> callback) {
        this.statusChangedCallback = callback;
    }

    void setThrowMotionDefinition(ThrowMotionDefinition next) {
        enqueue(() -> {
            String snapshot = next == null ? "" : next.toJSON().toString();
            if (snapshot.equals(definitionSnapshot)) {
                return null;
            }
            definitionSnapshot = snapshot;
            definition = next == null ? null : ThrowMotionDefinition.fromJSON(next.toJSON());
            rebuildPreview(false);
            return null;
        });
    }

    void setPlaying(boolean playing) {
        enqueue(() -> {
            this.playing = playing;
            return null;
        });
    }

    void stepFrame() {
        enqueue(() -> {
            playing = false;
            simulationTime += 1f / 30f;
            if (!samples.isEmpty() && simulationTime > samples.get(samples.size() - 1).getTime()) {
                simulationTime = 0f;
            }
            updateProjectileAtTime(simulationTime);
            return null;
        });
    }

    void resetSimulation() {
        enqueue(() -> {
            simulationTime = 0f;
            updateProjectileAtTime(simulationTime);
            return null;
        });
    }

    void resetCamera() {
        enqueue(() -> {
            fitCamera();
            return null;
        });
    }

    void setTargetDistance(float value) {
        enqueue(() -> {
            targetDistance = Math.max(1f, value);
            rebuildPreview(false);
            return null;
        });
    }

    void setTargetHeight(float value) {
        enqueue(() -> {
            targetHeight = value;
            rebuildPreview(false);
            return null;
        });
    }

    void setShowTrajectory(boolean show) {
        enqueue(() -> {
            showTrajectory = show;
            trajectoryRoot.setCullHint(show ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            return null;
        });
    }

    void setShowSamples(boolean show) {
        enqueue(() -> {
            showSamples = show;
            samplesRoot.setCullHint(show ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            return null;
        });
    }

    private void buildEnvironment() {
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(1.2f));
        rootNode.addLight(ambient);

        DirectionalLight key = new DirectionalLight();
        key.setDirection(new Vector3f(-0.45f, -0.75f, -0.35f).normalizeLocal());
        key.setColor(ColorRGBA.White.mult(2.6f));
        rootNode.addLight(key);

        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.65f, -0.35f, 0.5f).normalizeLocal());
        fill.setColor(new ColorRGBA(0.75f, 0.86f, 1f, 1f).mult(1.35f));
        rootNode.addLight(fill);

        cameraLight = new PointLight();
        cameraLight.setColor(ColorRGBA.White.mult(1.9f));
        cameraLight.setRadius(120f);
        rootNode.addLight(cameraLight);

        Geometry ground = new Geometry("PreviewGround", new Box(18f, 0.02f, 24f));
        ground.setMaterial(colorMaterial(new ColorRGBA(0.18f, 0.2f, 0.22f, 1f)));
        ground.setLocalTranslation(0f, -0.02f, 10f);
        previewRoot.attachChild(ground);

        Geometry start = new Geometry("ThrowStart", new Sphere(16, 16, 0.16f));
        start.setMaterial(colorMaterial(new ColorRGBA(0.4f, 0.8f, 1f, 1f)));
        start.setLocalTranslation(0f, 1.2f, 0f);
        previewRoot.attachChild(start);
    }

    private void rebuildPreview(boolean fitCamera) {
        ThrowMotionSampler.PreviewScenario scenario = scenario();
        samples = ThrowMotionSampler.sample(definition, scenario, 1f / 60f);
        rebuildProjectile();
        rebuildTrajectory();
        rebuildTarget();
        simulationTime = 0f;
        updateProjectileAtTime(0f);
        if (fitCamera) {
            fitCamera();
        }
        updateStatus();
    }

    private ThrowMotionSampler.PreviewScenario scenario() {
        ThrowMotionSampler.PreviewScenario scenario = new ThrowMotionSampler.PreviewScenario();
        scenario.start = new Vector3f(0f, 1.2f, 0f);
        scenario.target = new Vector3f(0f, 1.2f + targetHeight, targetDistance);
        scenario.groundY = 0f;
        return scenario;
    }

    private void rebuildProjectile() {
        projectileRoot.detachAllChildren();
        Geometry sphere = new Geometry("ThrowMotionPreviewSphere", new Sphere(24, 24, 0.28f));
        sphere.setMaterial(colorMaterial(new ColorRGBA(0.92f, 0.62f, 0.22f, 1f)));
        sphere.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        projectileRoot.attachChild(sphere);
        updateProjectileAtTime(simulationTime);
    }

    private void rebuildTrajectory() {
        trajectoryRoot.detachAllChildren();
        samplesRoot.detachAllChildren();
        if (samples.size() < 2) {
            return;
        }
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.LineStrip);
        FloatBuffer positions = BufferUtils.createFloatBuffer(samples.size() * 3);
        for (ThrowMotionSample sample : samples) {
            Vector3f pos = sample.getPosition();
            positions.put(pos.x).put(pos.y).put(pos.z);
        }
        positions.flip();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.updateBound();
        Geometry line = new Geometry("ThrowTrajectoryLine", mesh);
        Material mat = colorMaterial(new ColorRGBA(0.1f, 0.85f, 0.95f, 1f));
        mat.getAdditionalRenderState().setLineWidth(2.2f);
        line.setMaterial(mat);
        trajectoryRoot.attachChild(line);

        int stride = Math.max(1, samples.size() / 28);
        for (int i = 0; i < samples.size(); i += stride) {
            Geometry dot = new Geometry("ThrowSample_" + i, new Sphere(8, 8, 0.055f));
            dot.setMaterial(colorMaterial(new ColorRGBA(1f, 0.92f, 0.25f, 1f)));
            dot.setLocalTranslation(samples.get(i).getPosition());
            samplesRoot.attachChild(dot);
        }
        trajectoryRoot.setCullHint(showTrajectory ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        samplesRoot.setCullHint(showSamples ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    private void rebuildTarget() {
        targetRoot.detachAllChildren();
        Geometry target = new Geometry("ThrowTarget", new Sphere(16, 16, 0.22f));
        target.setMaterial(colorMaterial(new ColorRGBA(1f, 0.25f, 0.25f, 0.92f)));
        target.setLocalTranslation(scenario().target);
        targetRoot.attachChild(target);
    }

    private void updateProjectileAtTime(float time) {
        if (projectileRoot == null || samples.isEmpty()) {
            return;
        }
        ThrowMotionSample sample = sampleAt(time);
        Vector3f pos = sample.getPosition();
        projectileRoot.setLocalTranslation(pos);
        projectileRoot.setLocalRotation(Quaternion.IDENTITY);
        rebuildVelocityVector(sample);
    }

    private ThrowMotionSample sampleAt(float time) {
        if (samples.isEmpty()) {
            return new ThrowMotionSample(0f, Vector3f.ZERO, Vector3f.UNIT_Z, 0f);
        }
        ThrowMotionSample previous = samples.get(0);
        for (ThrowMotionSample sample : samples) {
            if (sample.getTime() >= time) {
                return sample;
            }
            previous = sample;
        }
        return previous;
    }

    private void rebuildVelocityVector(ThrowMotionSample sample) {
        velocityRoot.detachAllChildren();
        Vector3f velocity = sample.getVelocity();
        if (velocity.lengthSquared() < 0.0001f) {
            return;
        }
        Vector3f start = sample.getPosition();
        Vector3f end = start.add(velocity.normalize().mult(1.3f));
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        FloatBuffer positions = BufferUtils.createFloatBuffer(6);
        positions.put(start.x).put(start.y).put(start.z);
        positions.put(end.x).put(end.y).put(end.z);
        positions.flip();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.updateBound();
        Geometry line = new Geometry("VelocityVector", mesh);
        Material mat = colorMaterial(new ColorRGBA(0.45f, 1f, 0.45f, 1f));
        mat.getAdditionalRenderState().setLineWidth(3f);
        line.setMaterial(mat);
        velocityRoot.attachChild(line);
    }

    private void registerInput() {
        inputManager.addMapping(ACTION_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(ACTION_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACTION_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addListener((ActionListener) (name, isPressed, tpf) -> {
            if (!ACTION_LEFT.equals(name)) {
                return;
            }
            if (isPressed) {
                Vector2f click = inputManager.getCursorPosition();
                orbiting = true;
                lastMouse.set(click);
            } else {
                orbiting = false;
            }
        }, ACTION_LEFT);
        inputManager.addListener((AnalogListener) (name, value, tpf) -> {
            if (ACTION_SCROLL_UP.equals(name)) {
                cameraDistance = Math.max(2f, cameraDistance - value * 20f);
                updateCamera(currentCenter());
            } else if (ACTION_SCROLL_DOWN.equals(name)) {
                cameraDistance = Math.min(160f, cameraDistance + value * 20f);
                updateCamera(currentCenter());
            }
        }, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN);
    }

    private void fitCamera() {
        previewRoot.updateGeometricState();
        Vector3f center = currentCenter();
        float radius = 10f;
        BoundingVolume bound = previewRoot.getWorldBound();
        if (bound instanceof BoundingBox) {
            radius = Math.max(3f, ((BoundingBox) bound).getExtent(null).length());
        }
        float aspect = canvasAspect();
        float verticalFov = 45f * FastMath.DEG_TO_RAD;
        float horizontalFov = 2f * FastMath.atan(FastMath.tan(verticalFov * 0.5f) * Math.max(0.1f, aspect));
        float fitFov = Math.max(0.1f, Math.min(verticalFov, horizontalFov));
        cameraDistance = Math.max(4f, (radius / FastMath.tan(fitFov * 0.5f)) * 0.9f);
        cam.setFrustumPerspective(45f, aspect, 0.01f, Math.max(1000f, cameraDistance * 8f));
        updateCamera(center);
    }

    private Vector3f currentCenter() {
        previewRoot.updateGeometricState();
        BoundingVolume bound = previewRoot.getWorldBound();
        return bound == null ? new Vector3f(0f, 1f, targetDistance * 0.5f) : bound.getCenter();
    }

    private float canvasAspect() {
        int width = cam != null && cam.getWidth() > 0 ? cam.getWidth() : 720;
        int height = cam != null && cam.getHeight() > 0 ? cam.getHeight() : 520;
        return height <= 0 ? 1f : Math.max(0.1f, width / (float) height);
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

    private Material colorMaterial(ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        if (color.a < 1f) {
            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            material.getAdditionalRenderState().setDepthWrite(false);
        }
        return material;
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

    private void updateStatus() {
        if (statusChangedCallback == null) {
            return;
        }
        String id = definition == null ? "Preview" : definition.getId();
        String type = definition == null ? "" : ThrowMotionDefinition.displayNameForType(definition.getMotionType());
        statusChangedCallback.accept(id + (type.isBlank() ? "" : " - " + type) + " (" + samples.size() + " samples)");
    }
}
