package com.scenemaxeng.projector.ik;

import com.scenemaxeng.common.ik.IKLayerDefinition;

public class IKLayer {
    private final IKLayerDefinition definition;
    private final IKSolver solver;
    private IKTarget runtimeTarget;
    private float runtimeWeight = 0.0f;
    private float targetRuntimeWeight = 0.0f;
    private float defaultPlayWeight = 1.0f;
    private float blendTime;

    public IKLayer(IKLayerDefinition definition, IKSolver solver) {
        this.definition = definition;
        this.solver = solver;
        this.blendTime = definition == null ? 0f : Math.max(0f, definition.getBlendTime());
    }

    public IKLayerDefinition getDefinition() {
        return definition;
    }

    public float getRuntimeWeight() {
        return runtimeWeight;
    }

    public void setRuntimeWeight(float runtimeWeight) {
        this.runtimeWeight = clamp01(runtimeWeight);
        this.targetRuntimeWeight = this.runtimeWeight;
        this.defaultPlayWeight = this.runtimeWeight;
        if (definition != null && this.runtimeWeight > 0f) {
            definition.setEnabled(true);
        }
    }

    public float getDefaultPlayWeight() {
        return defaultPlayWeight;
    }

    public void setDefaultPlayWeight(float defaultPlayWeight) {
        this.defaultPlayWeight = clamp01(defaultPlayWeight);
    }

    public float getBlendTime() {
        return blendTime;
    }

    public void setBlendTime(float blendTime) {
        this.blendTime = Math.max(0f, blendTime);
        if (definition != null) {
            definition.setBlendTime(this.blendTime);
        }
    }

    public IKTarget getRuntimeTarget() {
        return runtimeTarget;
    }

    public void setRuntimeTarget(IKTarget runtimeTarget) {
        this.runtimeTarget = runtimeTarget;
    }

    public void play(Float weight, Float blend) {
        if (blend != null) {
            setBlendTime(blend);
        }
        float nextWeight = weight == null ? defaultPlayWeight : clamp01(weight);
        defaultPlayWeight = nextWeight;
        targetRuntimeWeight = nextWeight;
        if (definition != null) {
            definition.setEnabled(true);
        }
        if (blendTime <= 0f) {
            runtimeWeight = targetRuntimeWeight;
        }
    }

    public void stop(Float blend) {
        if (blend != null) {
            setBlendTime(blend);
        }
        targetRuntimeWeight = 0f;
        if (blendTime <= 0f) {
            runtimeWeight = 0f;
            if (definition != null) {
                definition.setEnabled(false);
            }
        }
    }

    public void update(float tpf) {
        if (runtimeWeight == targetRuntimeWeight) {
            return;
        }
        if (blendTime <= 0f || tpf <= 0f) {
            runtimeWeight = targetRuntimeWeight;
        } else {
            float step = tpf / blendTime;
            if (runtimeWeight < targetRuntimeWeight) {
                runtimeWeight = Math.min(targetRuntimeWeight, runtimeWeight + step);
            } else {
                runtimeWeight = Math.max(targetRuntimeWeight, runtimeWeight - step);
            }
        }
        if (definition != null && runtimeWeight <= 0.0001f && targetRuntimeWeight <= 0f) {
            definition.setEnabled(false);
        }
    }

    public void solve(IKContext context) {
        if (solver != null) {
            solver.solve(context);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
