package com.scenemaxeng.projector.ik;

import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.scenemaxeng.common.ik.IKDefinition;
import com.scenemaxeng.common.ik.IKLayerDefinition;
import com.scenemaxeng.projector.AppModel;
import com.scenemaxeng.projector.SceneMaxApp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IKControlComponent extends AbstractControl {
    private final SceneMaxApp app;
    private final AppModel model;
    private final String name;
    private final List<IKLayer> layers = new ArrayList<>();
    private boolean enabled = true;
    private float weight = 1.0f;
    private boolean debugMode = false;

    public IKControlComponent(SceneMaxApp app, AppModel model, String name) {
        this.app = app;
        this.model = model;
        this.name = name;
    }

    public static IKControlComponent fromDefinition(SceneMaxApp app, AppModel model, IKDefinition definition) {
        IKControlComponent component = new IKControlComponent(app, model, definition == null ? "" : definition.getId());
        if (definition != null) {
            for (IKLayerDefinition layer : definition.getLayers()) {
                component.addLayer(layer);
            }
        }
        return component;
    }

    public void addLayer(IKLayerDefinition definition) {
        if (definition == null) {
            return;
        }
        layers.add(new IKLayer(definition, createSolver(definition.getSolverType())));
        layers.sort(Comparator.comparingInt(layer -> layer.getDefinition().getPriority()));
    }

    public List<IKLayer> getLayers() {
        return layers;
    }

    public IKLayer findLayer(String layerNameOrId) {
        if (layerNameOrId == null || layerNameOrId.trim().isEmpty()) {
            return null;
        }
        String requested = layerNameOrId.trim();
        for (IKLayer layer : layers) {
            IKLayerDefinition definition = layer.getDefinition();
            if (requested.equalsIgnoreCase(definition.getId()) || requested.equalsIgnoreCase(definition.getName())) {
                return layer;
            }
        }
        return null;
    }

    public void setLayerEnabled(String layerNameOrId, boolean enabled) {
        IKLayer layer = findLayer(layerNameOrId);
        if (layer != null) {
            layer.getDefinition().setEnabled(enabled);
        }
    }

    public void setLayerWeight(String layerNameOrId, float layerWeight) {
        IKLayer layer = findLayer(layerNameOrId);
        if (layer != null) {
            layer.setDefaultPlayWeight(layerWeight);
            if (layer.getRuntimeWeight() > 0f) {
                layer.play(layerWeight, null);
            }
        }
    }

    public void setLayerTarget(String layerNameOrId, String targetEntity) {
        IKLayer layer = findLayer(layerNameOrId);
        if (layer != null) {
            layer.getDefinition().setTarget(targetEntity);
            layer.setRuntimeTarget(null);
        }
    }

    public void setLayerTarget(String layerNameOrId, String targetEntity, Spatial targetSpatial) {
        IKLayer layer = findLayer(layerNameOrId);
        if (layer != null) {
            layer.getDefinition().setTarget(targetEntity);
            layer.setRuntimeTarget(targetSpatial == null ? null : new IKTarget(targetSpatial, null, null));
        }
    }

    public void setLayerBlend(String layerNameOrId, float blendTime) {
        IKLayer layer = findLayer(layerNameOrId);
        if (layer != null) {
            layer.setBlendTime(blendTime);
        }
    }

    public void playLayer(String layerNameOrId, Float weight, Float blendTime) {
        IKLayer layer = findLayer(layerNameOrId);
        if (layer != null) {
            layer.play(weight, blendTime);
        }
    }

    public void stopLayer(String layerNameOrId, Float blendTime) {
        IKLayer layer = findLayer(layerNameOrId);
        if (layer != null) {
            layer.stop(blendTime);
        }
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (!enabled || weight <= 0f || model == null || model.getSkinningControl() == null) {
            return;
        }
        for (IKLayer layer : layers) {
            layer.update(tpf);
            IKLayerDefinition definition = layer.getDefinition();
            if (definition == null || !definition.isEnabled() || definition.getWeight() <= 0f) {
                continue;
            }
            float effectiveWeight = weight * definition.getWeight() * layer.getRuntimeWeight();
            if (effectiveWeight <= 0f) {
                continue;
            }
            layer.solve(new IKContext(app, model, definition, tpf, effectiveWeight, layer.getRuntimeTarget()));
        }
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
    }

    private static IKSolver createSolver(String solverType) {
        if (IKLayerDefinition.SOLVER_LOOK_AT.equalsIgnoreCase(solverType)
                || IKLayerDefinition.SOLVER_AIM.equalsIgnoreCase(solverType)) {
            return new LookAtIKSolver();
        }
        if (IKLayerDefinition.SOLVER_FABRIK.equalsIgnoreCase(solverType)) {
            return new FABRIKIKSolver();
        }
        if (IKLayerDefinition.SOLVER_THREE_BONE.equalsIgnoreCase(solverType)) {
            return new ThreeBoneIKSolver();
        }
        if (IKLayerDefinition.SOLVER_FOOT.equalsIgnoreCase(solverType)) {
            return new FootIKSolver();
        }
        return new TwoBoneIKSolver();
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = Math.max(0f, Math.min(1f, weight));
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
}
