package com.scenemax.designer.modelanalyzer;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.TransformTrack;
import com.jme3.anim.tween.action.Action;
import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.Animation;
import com.jme3.animation.LoopMode;
import com.jme3.animation.Track;
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
import com.jme3.texture.Texture;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;
import com.scenemax.designer.gizmo.GizmoManager;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.gizmo.RotateGizmo;
import com.scenemax.designer.gizmo.TranslateGizmo;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.projector.AppModel;
import com.scenemaxeng.projector.SceneMaxApp;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

class ModelAnalyzerPreviewApp extends SceneMaxApp {
    private static final String ACTION_LEFT = "ModelAnalyzerLeft";
    private static final String ACTION_SCROLL_UP = "ModelAnalyzerScrollUp";
    private static final String ACTION_SCROLL_DOWN = "ModelAnalyzerScrollDown";
    private static final double FALLBACK_ANIMATION_FPS = 24.0;

    private final File resourcesRoot;
    private final File projectRoot;
    private final Node previewRoot = new Node("ModelAnalyzerRoot");
    private final Node modelTransformNode = new Node("ModelAnalyzerModelTransform");
    private final DesignerEntity modelEntity = new DesignerEntity("Analyzed Model", DesignerEntityType.MODEL);

    private AssetsMapping previewAssets;
    private Spatial modelSpatial;
    private AppModel appModel;
    private AnimChannel legacyAnimationChannel;
    private TranslateGizmo translateGizmo;
    private RotateGizmo rotateGizmo;
    private GizmoManager gizmoManager;
    private PointLight cameraLight;

    private String modelId = "";
    private String animationName = "";
    private int speedPercent = 100;
    private boolean running;
    private boolean paused;
    private boolean rangeMode;
    private double rangeStartTime;
    private double rangeEndTime;
    private AnimationTimeline activeTimeline;
    private PlaybackInfo lastPlaybackInfo = PlaybackInfo.empty();
    private int lastPublishedPercent = Integer.MIN_VALUE;
    private int lastPublishedFrame = Integer.MIN_VALUE;
    private int lastPublishedMaxFrame = Integer.MIN_VALUE;
    private boolean lastPublishedRunning;
    private boolean lastPublishedPaused;

    private float cameraDistance = 6f;
    private float yaw = (float) Math.toRadians(35);
    private float pitch = (float) Math.toRadians(18);
    private boolean orbiting;
    private boolean pendingCameraFit;
    private final Vector2f lastMouse = new Vector2f();

    private Consumer<List<String>> animationNamesChangedCallback;
    private Consumer<PlaybackInfo> playbackInfoChangedCallback;
    private Consumer<String> statusChangedCallback;

    ModelAnalyzerPreviewApp(File resourcesRoot) {
        this.resourcesRoot = resourcesRoot;
        this.projectRoot = resourcesRoot != null ? resourcesRoot.getParentFile() : null;
    }

    @Override
    public void simpleInitApp() {
        if (projectRoot != null) {
            setWorkingFolder(new File(new File(projectRoot, "scripts"), "_model_analyzer").getAbsolutePath());
        }
        if (resourcesRoot != null && resourcesRoot.isDirectory()) {
            previewAssets = new AssetsMapping(resourcesRoot.getAbsolutePath());
        } else {
            previewAssets = new AssetsMapping();
        }
        assetsMapping = previewAssets;

        tryRegisterLocator(new File("./resources-basic/resources"));
        tryRegisterLocator(new File("./resources"));
        tryRegisterLocator(resourcesRoot);

        viewPort.setBackgroundColor(new ColorRGBA(0.045f, 0.05f, 0.06f, 1f));
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(previewRoot);
        previewRoot.attachChild(modelTransformNode);
        modelEntity.setSceneNode(modelTransformNode);

        installLighting();
        installEnvironmentProbe();

        translateGizmo = new TranslateGizmo(assetManager);
        rotateGizmo = new RotateGizmo(assetManager);
        rootNode.attachChild(translateGizmo);
        rootNode.attachChild(rotateGizmo);
        gizmoManager = new GizmoManager(rootNode, translateGizmo, rotateGizmo);
        gizmoManager.onSelectionChanged(modelEntity);

        registerInput();
        publishPlaybackInfo(true);
    }

    void setAnimationNamesChangedCallback(Consumer<List<String>> callback) {
        this.animationNamesChangedCallback = callback;
    }

    void setPlaybackInfoChangedCallback(Consumer<PlaybackInfo> callback) {
        this.playbackInfoChangedCallback = callback;
    }

    void setStatusChangedCallback(Consumer<String> callback) {
        this.statusChangedCallback = callback;
    }

    void loadModel(String modelId) {
        enqueue(() -> {
            this.modelId = safeString(modelId);
            loadSelectedModel();
            return null;
        });
    }

    void selectAnimation(String animationName) {
        enqueue(() -> {
            this.animationName = safeString(animationName);
            stopAnimationInternal();
            activeTimeline = resolveTimeline(this.animationName);
            publishPlaybackInfo(true);
            if (this.animationName.isEmpty()) {
                updateStatus("Choose an animation to inspect.");
            } else {
                updateStatus("Selected animation: " + this.animationName);
            }
            return null;
        });
    }

    void playAnimation(boolean rangeMode, int startFrame, int endFrame) {
        enqueue(() -> {
            this.rangeMode = rangeMode;
            startPlayback(rangeMode, startFrame, endFrame);
            return null;
        });
    }

    void stopAnimation() {
        enqueue(() -> {
            stopAnimationInternal();
            updateStatus("Animation stopped.");
            publishPlaybackInfo(true);
            return null;
        });
    }

    void setAnimationPaused(boolean paused) {
        enqueue(() -> {
            if (!running) {
                return null;
            }
            this.paused = paused;
            applyPlaybackSpeed();
            updateStatus((paused ? "Paused" : "Resumed") + " animation: " + animationName);
            publishPlaybackInfo(true);
            return null;
        });
    }

    void seekToFrame(int frame, int startFrame, int endFrame) {
        enqueue(() -> {
            String name = safeString(animationName);
            if (name.isEmpty() || modelSpatial == null) {
                return null;
            }
            boolean wasRunning = running;
            boolean wasPaused = paused;
            if (!hasActivePlayback()) {
                startPlayback(true, startFrame, endFrame);
                wasRunning = false;
            } else {
                activeTimeline = activeTimeline != null ? activeTimeline : resolveTimeline(name);
                updateActiveRange(startFrame, endFrame);
            }
            if (activeTimeline == null || activeTimeline.length <= 0) {
                return null;
            }
            int start = Math.max(0, Math.min(startFrame, activeTimeline.maxFrame()));
            int end = Math.max(start, Math.min(endFrame, activeTimeline.maxFrame()));
            int clampedFrame = Math.max(start, Math.min(frame, end));
            setAnimationTime(activeTimeline.timeAtFrame(clampedFrame));
            running = true;
            paused = wasRunning ? wasPaused : true;
            applyPlaybackSpeed();
            updateStatus("Animation cursor: frame " + clampedFrame + ".");
            publishPlaybackInfo(true);
            return null;
        });
    }

    void updateRunningRange(int startFrame, int endFrame) {
        enqueue(() -> {
            if (!running || activeTimeline == null || activeTimeline.length <= 0) {
                return null;
            }
            updateActiveRange(startFrame, endFrame);
            double currentTime = currentAnimationTime();
            if (currentTime < rangeStartTime || currentTime > rangeEndTime) {
                setAnimationTime(rangeStartTime);
            }
            publishPlaybackInfo(true);
            return null;
        });
    }

    void setAnimationSpeedPercent(int speedPercent) {
        enqueue(() -> {
            this.speedPercent = Math.max(1, Math.min(200, speedPercent));
            applyPlaybackSpeed();
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

    void resetCamera() {
        enqueue(() -> {
            fitCamera();
            return null;
        });
    }

    void resetModelTransform() {
        enqueue(() -> {
            modelTransformNode.setLocalTranslation(Vector3f.ZERO);
            modelTransformNode.setLocalRotation(Quaternion.IDENTITY);
            modelTransformNode.setLocalScale(Vector3f.UNIT_XYZ);
            if (gizmoManager != null) {
                gizmoManager.updateGizmoPosition();
            }
            updateStatus("Model transform reset.");
            return null;
        });
    }

    PlaybackInfo getLastPlaybackInfo() {
        return lastPlaybackInfo;
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

        if (running && !paused && rangeMode) {
            double currentTime = currentAnimationTime();
            if (currentTime >= rangeEndTime) {
                setAnimationTime(rangeStartTime);
            }
        }

        if (pendingCameraFit) {
            pendingCameraFit = false;
            fitCamera();
        }
        if (gizmoManager != null) {
            if (gizmoManager.isDragging()) {
                gizmoManager.updateDrag(cam, inputManager.getCursorPosition());
            }
            gizmoManager.scaleGizmoToCamera(cam);
            gizmoManager.updateGizmoPosition();
        }
        publishPlaybackInfo(false);
    }

    private void loadSelectedModel() {
        stopAnimationInternal();
        previewRoot.detachAllChildren();
        previewRoot.attachChild(modelTransformNode);
        modelTransformNode.detachAllChildren();
        modelTransformNode.setLocalTranslation(Vector3f.ZERO);
        modelTransformNode.setLocalRotation(Quaternion.IDENTITY);
        modelTransformNode.setLocalScale(Vector3f.UNIT_XYZ);
        modelSpatial = null;
        appModel = null;
        legacyAnimationChannel = null;
        activeTimeline = null;

        ResourceSetup resource = resolveModel(modelId);
        if (resource == null) {
            attachFallbackModel();
            updateStatus(modelId.isEmpty() ? "Choose a model to analyze." : "Model not found: " + modelId);
        } else {
            try {
                modelSpatial = assetManager.loadModel(resource.path);
                modelSpatial.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                applyReadablePreviewMaterials(modelSpatial);
                applyResourceTransform(modelSpatial, resource);
                modelTransformNode.attachChild(modelSpatial);
                appModel = new AppModel(modelTransformNode);
                appModel.resource = resource;
                appModel.skinningControlNode = findSkinningControlNode(modelSpatial);
                updateStatus("Loaded model: " + resource.name);
            } catch (Exception ex) {
                ex.printStackTrace();
                attachFallbackModel();
                updateStatus("Model load failed: " + rootMessage(ex));
            }
        }

        if (gizmoManager != null) {
            gizmoManager.onSelectionChanged(modelEntity);
            gizmoManager.updateGizmoPosition();
        }
        publishAnimationNames();
        publishPlaybackInfo(true);
        pendingCameraFit = true;
    }

    private void attachFallbackModel() {
        Geometry fallback = new Geometry("ModelAnalyzerFallback", new Box(0.5f, 0.5f, 0.5f));
        fallback.setMaterial(colorMaterial(new ColorRGBA(0.38f, 0.46f, 0.56f, 1f)));
        fallback.setLocalTranslation(0f, 0.5f, 0f);
        modelSpatial = fallback;
        modelTransformNode.attachChild(fallback);
    }

    private void publishAnimationNames() {
        if (animationNamesChangedCallback != null) {
            animationNamesChangedCallback.accept(listAnimations());
        }
    }

    private List<String> listAnimations() {
        Set<String> names = new LinkedHashSet<>();
        AnimComposer composer = findAnimComposer(modelSpatial);
        if (composer != null) {
            for (AnimClip clip : composer.getAnimClips()) {
                if (clip != null && clip.getName() != null && !clip.getName().isBlank()) {
                    names.add(clip.getName());
                }
            }
        }
        AnimControl control = findAnimControl(modelSpatial);
        if (control != null) {
            names.addAll(control.getAnimationNames());
        }
        return new ArrayList<>(names);
    }

    private void startPlayback(boolean rangeMode, int startFrame, int endFrame) {
        String name = safeString(animationName);
        stopAnimationInternal();
        activeTimeline = resolveTimeline(name);
        if (name.isEmpty() || modelSpatial == null) {
            updateStatus("Choose an animation to play.");
            publishPlaybackInfo(true);
            return;
        }

        AnimComposer composer = findAnimComposer(modelSpatial);
        if (composer != null && (composer.hasAction(name) || composer.hasAnimClip(name))) {
            composer.setCurrentAction(name);
            activeTimeline = resolveTimeline(name);
            applyRangeIfNeeded(rangeMode, startFrame, endFrame);
            applyPlaybackSpeed();
            running = true;
            paused = false;
            updateStatus(playbackStatus(name, rangeMode));
            publishPlaybackInfo(true);
            return;
        }

        AnimControl control = findAnimControl(modelSpatial);
        if (control != null && control.getAnimationNames().contains(name)) {
            legacyAnimationChannel = control.createChannel();
            legacyAnimationChannel.setAnim(name);
            legacyAnimationChannel.setLoopMode(LoopMode.Loop);
            activeTimeline = resolveTimeline(name);
            applyRangeIfNeeded(rangeMode, startFrame, endFrame);
            applyPlaybackSpeed();
            running = true;
            paused = false;
            updateStatus(playbackStatus(name, rangeMode));
            publishPlaybackInfo(true);
            return;
        }

        if (appModel != null && previewAssets != null && previewAssets.getAnimationsIndex().containsKey(name.toLowerCase(Locale.ROOT))) {
            boolean attached = appModel.attachExternalAnimation(assetManager, previewAssets, name);
            AnimComposer attachedComposer = appModel.getAnimComposer();
            if (attached && attachedComposer != null && attachedComposer.hasAction(name)) {
                attachedComposer.setCurrentAction(name);
                activeTimeline = resolveTimeline(name);
                applyRangeIfNeeded(rangeMode, startFrame, endFrame);
                applyPlaybackSpeed();
                running = true;
                paused = false;
                updateStatus(playbackStatus(name, rangeMode));
                publishPlaybackInfo(true);
                return;
            }
        }

        updateStatus("Animation not found: " + name);
        publishPlaybackInfo(true);
    }

    private String playbackStatus(String name, boolean rangeMode) {
        if (rangeMode) {
            return "Playing frame range for " + name + " (" + speedPercent + "% speed).";
        }
        return "Playing animation: " + name + " (" + speedPercent + "% speed).";
    }

    private void applyRangeIfNeeded(boolean rangeMode, int startFrame, int endFrame) {
        if (!rangeMode || activeTimeline == null || activeTimeline.length <= 0) {
            this.rangeMode = false;
            rangeStartTime = 0;
            rangeEndTime = 0;
            return;
        }
        int maxFrame = activeTimeline.maxFrame();
        int start = Math.max(0, Math.min(startFrame, maxFrame));
        int end = Math.max(0, Math.min(endFrame, maxFrame));
        if (end < start) {
            end = start;
        }
        rangeStartTime = activeTimeline.timeAtFrame(start);
        rangeEndTime = activeTimeline.timeAtFrame(end);
        if (rangeEndTime <= rangeStartTime) {
            rangeEndTime = Math.min(activeTimeline.length, rangeStartTime + (1.0 / FALLBACK_ANIMATION_FPS));
        }
        setAnimationTime(rangeStartTime);
        this.rangeMode = true;
    }

    private void updateActiveRange(int startFrame, int endFrame) {
        if (activeTimeline == null || activeTimeline.length <= 0) {
            return;
        }
        int maxFrame = activeTimeline.maxFrame();
        int start = Math.max(0, Math.min(startFrame, maxFrame));
        int end = Math.max(start, Math.min(endFrame, maxFrame));
        rangeStartTime = activeTimeline.timeAtFrame(start);
        rangeEndTime = activeTimeline.timeAtFrame(end);
        if (rangeEndTime <= rangeStartTime) {
            rangeEndTime = Math.min(activeTimeline.length, rangeStartTime + (1.0 / FALLBACK_ANIMATION_FPS));
        }
        rangeMode = true;
    }

    private void stopAnimationInternal() {
        AnimComposer composer = findAnimComposer(modelSpatial);
        if (composer != null) {
            try {
                composer.removeCurrentAction();
                composer.reset();
            } catch (Exception ignored) {
            }
        }
        if (appModel != null && appModel.getAnimComposer() != null && appModel.getAnimComposer() != composer) {
            try {
                appModel.getAnimComposer().removeCurrentAction();
                appModel.getAnimComposer().reset();
            } catch (Exception ignored) {
            }
        }
        if (legacyAnimationChannel != null) {
            try {
                legacyAnimationChannel.reset(true);
            } catch (Exception ignored) {
            }
            legacyAnimationChannel = null;
        }
        running = false;
        paused = false;
        rangeMode = false;
        rangeStartTime = 0;
        rangeEndTime = 0;
    }

    private void applyPlaybackSpeed() {
        float speed = paused ? 0f : speedPercent / 100f;
        AnimComposer composer = findAnimComposer(modelSpatial);
        if (composer != null) {
            composer.setGlobalSpeed(speed);
        }
        if (appModel != null && appModel.getAnimComposer() != null && appModel.getAnimComposer() != composer) {
            appModel.getAnimComposer().setGlobalSpeed(speed);
        }
        if (legacyAnimationChannel != null) {
            legacyAnimationChannel.setSpeed(speed);
        }
    }

    private double currentAnimationTime() {
        AnimComposer composer = activeComposer();
        if (composer != null) {
            return composer.getTime("Default");
        }
        if (legacyAnimationChannel != null) {
            return legacyAnimationChannel.getTime();
        }
        return 0;
    }

    private void setAnimationTime(double time) {
        AnimComposer composer = activeComposer();
        if (composer != null) {
            composer.setTime("Default", time);
        }
        if (legacyAnimationChannel != null) {
            legacyAnimationChannel.setTime((float) time);
        }
    }

    private AnimComposer activeComposer() {
        AnimComposer composer = findAnimComposer(modelSpatial);
        if (composer != null && composer.getCurrentAction() != null) {
            return composer;
        }
        if (appModel != null && appModel.getAnimComposer() != null
                && appModel.getAnimComposer().getCurrentAction() != null) {
            return appModel.getAnimComposer();
        }
        return null;
    }

    private boolean hasActivePlayback() {
        return activeComposer() != null || legacyAnimationChannel != null;
    }

    private AnimationTimeline resolveTimeline(String name) {
        String safeName = safeString(name);
        if (safeName.isEmpty()) {
            return null;
        }
        AnimComposer composer = findAnimComposer(modelSpatial);
        AnimationTimeline timeline = timelineForComposer(composer, safeName);
        if (timeline != null) {
            return timeline;
        }
        if (appModel != null && appModel.getAnimComposer() != composer) {
            timeline = timelineForComposer(appModel.getAnimComposer(), safeName);
            if (timeline != null) {
                return timeline;
            }
        }
        AnimControl control = findAnimControl(modelSpatial);
        if (control != null && control.getAnim(safeName) != null) {
            Animation animation = control.getAnim(safeName);
            return new AnimationTimeline(animation.getLength(), timesFromAnimation(animation));
        }
        return null;
    }

    private AnimationTimeline timelineForComposer(AnimComposer composer, String name) {
        if (composer == null) {
            return null;
        }
        AnimClip clip = composer.getAnimClip(name);
        if (clip != null) {
            return new AnimationTimeline(clip.getLength(), timesFromClip(clip));
        }
        Action action = composer.getAction(name);
        if (action != null && action.getLength() > 0) {
            return new AnimationTimeline(action.getLength(), null);
        }
        return null;
    }

    private float[] timesFromClip(AnimClip clip) {
        if (clip == null || clip.getTracks() == null) {
            return null;
        }
        float[] bestTimes = null;
        for (AnimTrack track : clip.getTracks()) {
            if (track instanceof TransformTrack) {
                float[] times = ((TransformTrack) track).getTimes();
                if (times != null && (bestTimes == null || times.length > bestTimes.length)) {
                    bestTimes = times;
                }
            }
        }
        return bestTimes;
    }

    private float[] timesFromAnimation(Animation animation) {
        if (animation == null || animation.getTracks() == null) {
            return null;
        }
        float[] bestTimes = null;
        for (Track track : animation.getTracks()) {
            float[] times = track == null ? null : track.getKeyFrameTimes();
            if (times != null && (bestTimes == null || times.length > bestTimes.length)) {
                bestTimes = times;
            }
        }
        return bestTimes;
    }

    private void publishPlaybackInfo(boolean force) {
        PlaybackInfo info = currentPlaybackInfo();
        lastPlaybackInfo = info;
        if (!force
                && info.percent == lastPublishedPercent
                && info.currentFrame == lastPublishedFrame
                && info.maxFrame == lastPublishedMaxFrame
                && info.running == lastPublishedRunning
                && info.paused == lastPublishedPaused) {
            return;
        }
        lastPublishedPercent = info.percent;
        lastPublishedFrame = info.currentFrame;
        lastPublishedMaxFrame = info.maxFrame;
        lastPublishedRunning = info.running;
        lastPublishedPaused = info.paused;
        if (playbackInfoChangedCallback != null) {
            playbackInfoChangedCallback.accept(info);
        }
    }

    private PlaybackInfo currentPlaybackInfo() {
        AnimationTimeline timeline = activeTimeline != null ? activeTimeline : resolveTimeline(animationName);
        if (timeline == null || timeline.length <= 0) {
            return PlaybackInfo.empty();
        }
        double time = currentAnimationTime();
        if (!running) {
            time = 0;
        }
        int maxFrame = timeline.maxFrame();
        int frame = (int) Math.round(timeline.frameAtTime(time));
        int percent = maxFrame <= 0 ? 0 : (int) Math.round(frame / (double) maxFrame * 100.0);
        return new PlaybackInfo(
                Math.max(0, Math.min(100, percent)),
                Math.max(0, Math.min(maxFrame, frame)),
                maxFrame,
                running,
                paused
        );
    }

    private void installLighting() {
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(1.25f));
        rootNode.addLight(ambient);

        DirectionalLight key = new DirectionalLight();
        key.setDirection(new Vector3f(-0.45f, -0.75f, -0.35f).normalizeLocal());
        key.setColor(ColorRGBA.White.mult(3.0f));
        rootNode.addLight(key);

        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.6f, -0.3f, 0.55f).normalizeLocal());
        fill.setColor(new ColorRGBA(0.75f, 0.86f, 1f, 1f).mult(1.55f));
        rootNode.addLight(fill);

        cameraLight = new PointLight();
        cameraLight.setColor(ColorRGBA.White.mult(2.0f));
        cameraLight.setRadius(100f);
        rootNode.addLight(cameraLight);
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
        float aspect = canvasAspect();
        float verticalFov = 45f * FastMath.DEG_TO_RAD;
        float horizontalFov = 2f * FastMath.atan(FastMath.tan(verticalFov * 0.5f) * Math.max(0.1f, aspect));
        float fitFov = Math.max(0.1f, Math.min(verticalFov, horizontalFov));
        cameraDistance = Math.max(0.25f, (radius / FastMath.tan(fitFov * 0.5f)) * 1.25f);
        cam.setFrustumPerspective(45f, aspect, 0.01f, Math.max(1000f, cameraDistance * 8f));
        updateCamera(center);
    }

    private float canvasAspect() {
        int width = cam != null && cam.getWidth() > 0 ? cam.getWidth() : 900;
        int height = cam != null && cam.getHeight() > 0 ? cam.getHeight() : 620;
        return height <= 0 ? 1f : Math.max(0.1f, width / (float) height);
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

    private ResourceSetup resolveModel(String modelAssetId) {
        if (modelAssetId == null || modelAssetId.trim().isEmpty()
                || previewAssets == null || previewAssets.get3DModelsIndex() == null) {
            return null;
        }
        return previewAssets.get3DModelsIndex().get(modelAssetId.trim().toLowerCase(Locale.ROOT));
    }

    private void applyResourceTransform(Spatial spatial, ResourceSetup resource) {
        if (spatial == null || resource == null) {
            return;
        }
        spatial.setLocalScale(resource.scaleX, resource.scaleY, resource.scaleZ);
        spatial.setLocalTranslation(resource.localTranslationX, resource.localTranslationY, resource.localTranslationZ);
        spatial.setLocalRotation(new Quaternion().fromAngles(0f, resource.rotateY * FastMath.DEG_TO_RAD, 0f));
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

    private AnimComposer findAnimComposer(Spatial spatial) {
        if (spatial == null) {
            return null;
        }
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null) {
            return composer;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimComposer found = findAnimComposer(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private AnimControl findAnimControl(Spatial spatial) {
        if (spatial == null) {
            return null;
        }
        AnimControl control = spatial.getControl(AnimControl.class);
        if (control != null) {
            return control;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimControl found = findAnimControl(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    private Material colorMaterial(ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        return material;
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
                    new ColorRGBA(0.82f, 0.82f, 0.82f, 1f),
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

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private void updateStatus(String status) {
        if (statusChangedCallback != null) {
            statusChangedCallback.accept(status);
        }
    }

    static class PlaybackInfo {
        final int percent;
        final int currentFrame;
        final int maxFrame;
        final boolean running;
        final boolean paused;

        PlaybackInfo(int percent, int currentFrame, int maxFrame, boolean running, boolean paused) {
            this.percent = percent;
            this.currentFrame = currentFrame;
            this.maxFrame = maxFrame;
            this.running = running;
            this.paused = paused;
        }

        static PlaybackInfo empty() {
            return new PlaybackInfo(-1, -1, -1, false, false);
        }
    }

    private static class AnimationTimeline {
        final double length;
        final float[] times;

        AnimationTimeline(double length, float[] times) {
            this.length = length;
            this.times = times == null || times.length == 0 ? null : times;
        }

        int maxFrame() {
            if (times != null) {
                return Math.max(0, times.length - 1);
            }
            return Math.max(0, (int) Math.round(length * FALLBACK_ANIMATION_FPS));
        }

        double timeAtFrame(double frame) {
            if (times == null) {
                return length <= 0 ? 0 : frame / FALLBACK_ANIMATION_FPS;
            }
            if (times.length == 1) {
                return times[0];
            }
            double clampedFrame = Math.max(0, Math.min(maxFrame(), frame));
            int left = (int) Math.floor(clampedFrame);
            int right = Math.min(times.length - 1, (int) Math.ceil(clampedFrame));
            if (left == right) {
                return times[left];
            }
            double amount = clampedFrame - left;
            return times[left] + (times[right] - times[left]) * amount;
        }

        double frameAtTime(double time) {
            if (times == null) {
                return Math.max(0, Math.min(maxFrame(), time * FALLBACK_ANIMATION_FPS));
            }
            if (times.length == 1) {
                return 0;
            }
            double clampedTime = Math.max(0, Math.min(length, time));
            for (int i = 1; i < times.length; i++) {
                if (clampedTime <= times[i]) {
                    float previous = times[i - 1];
                    float next = times[i];
                    if (next <= previous) {
                        return i;
                    }
                    double amount = (clampedTime - previous) / (next - previous);
                    return (i - 1) + amount;
                }
            }
            return maxFrame();
        }
    }
}
