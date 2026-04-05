package com.scenemaxeng.projector;

import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;

public class EffekseerRenderProcessor implements SceneProcessor {

    private final SceneMaxApp app;
    private RenderManager renderManager;
    private ViewPort viewPort;
    private boolean initialized;
    private float tpf;

    public EffekseerRenderProcessor(SceneMaxApp app) {
        this.app = app;
    }

    @Override
    public void initialize(RenderManager rm, ViewPort vp) {
        this.renderManager = rm;
        this.viewPort = vp;
        this.initialized = true;
    }

    @Override
    public void reshape(ViewPort vp, int w, int h) {
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void preFrame(float tpf) {
        this.tpf = tpf;
    }

    @Override
    public void postQueue(RenderQueue rq) {
    }

    @Override
    public void postFrame(FrameBuffer out) {
        if (initialized && renderManager != null && viewPort != null) {
            app.renderEffekseerEffects(tpf, viewPort);
        }
    }

    @Override
    public void cleanup() {
        initialized = false;
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
    }
}
