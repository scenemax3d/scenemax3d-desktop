package com.scenemaxeng.projector;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Spatial;

public abstract class SceneMaxBaseAppState extends BaseAppState {
    private SceneMaxApp sceneMaxApp;
    private SceneMaxScope sceneMaxScope;

    final void setSceneMaxScope(SceneMaxScope sceneMaxScope) {
        this.sceneMaxScope = sceneMaxScope;
    }

    @Override
    protected final void initialize(Application app) {
        this.sceneMaxApp = (SceneMaxApp) app;
        onSceneMaxInitialize(sceneMaxApp);
    }

    protected void onSceneMaxInitialize(SceneMaxApp app) {
    }

    @Override
    protected void cleanup(Application app) {
        this.sceneMaxApp = null;
        this.sceneMaxScope = null;
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    protected final SceneMaxApp getSceneMaxApp() {
        return sceneMaxApp;
    }

    protected final SceneMaxScope getSceneMaxScope() {
        return sceneMaxScope;
    }

    protected final EntityInstBase getEntity(String name) {
        return sceneMaxScope == null ? null : sceneMaxScope.getEntityInst(name);
    }

    protected final Spatial getEntitySpatial(String name) {
        EntityInstBase entity = getEntity(name);
        if (entity == null || entity.varDef == null || sceneMaxApp == null) {
            return null;
        }
        return sceneMaxApp.getEntitySpatial(entity.getVarRunTimeName(), entity.varDef.varType);
    }
}
