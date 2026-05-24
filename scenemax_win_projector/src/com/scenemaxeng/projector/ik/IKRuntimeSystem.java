package com.scenemaxeng.projector.ik;

import com.jme3.scene.Spatial;
import com.scenemaxeng.common.ik.IKDefinition;
import com.scenemaxeng.projector.AppModel;
import com.scenemaxeng.projector.SceneMaxApp;

import java.util.HashMap;
import java.util.Map;

public class IKRuntimeSystem {
    private final SceneMaxApp app;
    private final Map<String, IKControlComponent> controls = new HashMap<>();

    public IKRuntimeSystem(SceneMaxApp app) {
        this.app = app;
    }

    public IKControlComponent apply(String modelVarName, AppModel model, IKDefinition definition) {
        if (modelVarName == null || model == null || definition == null || model.getSkinningControl() == null) {
            return null;
        }
        remove(modelVarName);
        IKControlComponent component = IKControlComponent.fromDefinition(app, model, definition);
        Spatial host = model.skinningControlNode != null ? model.skinningControlNode : model.model;
        host.addControl(component);
        controls.put(modelVarName, component);
        return component;
    }

    public IKControlComponent get(String modelVarName) {
        return controls.get(modelVarName);
    }

    public void remove(String modelVarName) {
        IKControlComponent existing = controls.remove(modelVarName);
        if (existing != null && existing.getSpatial() != null) {
            existing.getSpatial().removeControl(existing);
        }
    }

    public void clear() {
        for (String key : new java.util.ArrayList<>(controls.keySet())) {
            remove(key);
        }
    }
}
