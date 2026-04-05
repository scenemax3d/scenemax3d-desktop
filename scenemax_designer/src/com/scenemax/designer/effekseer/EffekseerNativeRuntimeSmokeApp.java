package com.scenemax.designer.effekseer;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;
import com.scenemaxeng.projector.SceneMaxApp;

import java.io.File;
import java.util.Locale;

public final class EffekseerNativeRuntimeSmokeApp extends SceneMaxApp {

    private static final String EFFECT_PROPERTY = "scenemax.effekseer.testEffect";
    private static final String DEFAULT_EFFECT_PATH =
            "C:\\dev\\scenemax_desktop\\projects\\fighting_game_project\\resources\\effects\\Homing_Laser01_3\\Homing_Laser01.efkefc";

    private final EffekseerNativePreview nativePreview = new EffekseerNativePreview();
    private final File runtimeEffectFile;
    private float statusTimer;

    public EffekseerNativeRuntimeSmokeApp(File runtimeEffectFile) {
        super(SceneMaxApp.HOST_APP_WINDOWS);
        this.runtimeEffectFile = runtimeEffectFile;
        setPauseOnLostFocus(false);
        setShowSettings(false);
    }

    @Override
    public void simpleInitApp() {
        super.simpleInitApp();
        nativePreview.setCompositeEnabled(true);
        setDisplayFps(true);
        setDisplayStatView(false);
        viewPort.setBackgroundColor(new ColorRGBA(0.04f, 0.05f, 0.08f, 1f));

        if (flyCam != null) {
            flyCam.setEnabled(true);
            flyCam.setMoveSpeed(25f);
            flyCam.setDragToRotate(false);
        }

        cam.setLocation(new Vector3f(9.5f, -1.7f, 6.6f));
        cam.lookAt(new Vector3f(0f, 1.4f, 0f), Vector3f.UNIT_Y);

        attachReferenceGeometry();

        if (!runtimeEffectFile.isFile()) {
            throw new IllegalStateException("Effekseer runtime effect not found: " + runtimeEffectFile.getAbsolutePath());
        }
        if (!nativePreview.isAvailable()) {
            throw new IllegalStateException("Effekseer native bridge unavailable: " + nativePreview.getAvailabilityMessage());
        }

        boolean queued = nativePreview.loadEffect(runtimeEffectFile, true, 1.0);
        if (!queued) {
            throw new IllegalStateException("Failed to queue native effect load for " + runtimeEffectFile.getAbsolutePath());
        }
        System.out.println("Effekseer runtime smoke test loading: " + runtimeEffectFile.getAbsolutePath());
    }

    @Override
    public void simpleUpdate(float tpf) {
        super.simpleUpdate(tpf);
        nativePreview.update(tpf);
        statusTimer += tpf;
        if (statusTimer >= 2.0f) {
            statusTimer = 0f;
            System.out.println("Effekseer smoke status: " + nativePreview.getStatus());
        }
    }

    @Override
    public void simpleRender(com.jme3.renderer.RenderManager rm) {
        nativePreview.updateCamera(cam.getViewMatrix(), cam.getProjectionMatrix(), cam.getLocation());
        nativePreview.render(cam.getWidth(), cam.getHeight());
        super.simpleRender(rm);
    }

    @Override
    public void destroy() {
        nativePreview.dispose();
        super.destroy();
    }

    private void attachReferenceGeometry() {
        Geometry marker = new Geometry("EffekseerOriginMarker", new Box(0.25f, 0.25f, 0.25f));
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0.15f, 0.75f, 0.95f, 1f));
        marker.setMaterial(material);
        marker.setLocalTranslation(0f, 1.4f, 0f);
        rootNode.attachChild(marker);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.5f));
        rootNode.addLight(ambient);

        DirectionalLight sun = new DirectionalLight();
        sun.setColor(ColorRGBA.White.mult(0.9f));
        sun.setDirection(new Vector3f(-0.4f, -1f, -0.3f).normalizeLocal());
        rootNode.addLight(sun);
    }

    private static File resolveEffectFile(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return new File(args[0]);
        }

        String configured = System.getProperty(EFFECT_PROPERTY, "").trim();
        if (!configured.isEmpty()) {
            return new File(configured);
        }

        return new File(DEFAULT_EFFECT_PATH);
    }

    public static void main(String[] args) {
        File effectFile = resolveEffectFile(args);
        AppSettings settings = new AppSettings(true);
        settings.setWidth(1600);
        settings.setHeight(900);
        settings.setVSync(true);
        settings.setSamples(4);
        settings.setTitle(String.format(Locale.ROOT, "Effekseer Native Smoke Test - %s", effectFile.getName()));

        EffekseerNativeRuntimeSmokeApp app = new EffekseerNativeRuntimeSmokeApp(effectFile);
        app.setSettings(settings);
        app.start();
    }
}
