package com.scenemaxeng.projector;

import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.LightProbe;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;

public class LightTransformControl extends AbstractControl {

    private final Light light;
    private final Vector3f baseDirection;

    public LightTransformControl(Light light, Vector3f baseDirection) {
        this.light = light;
        this.baseDirection = baseDirection != null && baseDirection.lengthSquared() > 0.0001f
                ? baseDirection.clone().normalizeLocal()
                : new Vector3f(0f, -1f, 0f);
    }

    @Override
    public void setSpatial(com.jme3.scene.Spatial spatial) {
        super.setSpatial(spatial);
        syncLight();
    }

    @Override
    protected void controlUpdate(float tpf) {
        syncLight();
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
    }

    private void syncLight() {
        if (spatial == null || light == null) {
            return;
        }
        if (light instanceof PointLight) {
            ((PointLight) light).setPosition(spatial.getWorldTranslation());
        } else if (light instanceof SpotLight) {
            SpotLight spot = (SpotLight) light;
            spot.setPosition(spatial.getWorldTranslation());
            spot.setDirection(spatial.getWorldRotation().mult(baseDirection).normalizeLocal());
        } else if (light instanceof DirectionalLight) {
            ((DirectionalLight) light).setDirection(spatial.getWorldRotation().mult(baseDirection).normalizeLocal());
        } else if (light instanceof LightProbe) {
            ((LightProbe) light).setPosition(spatial.getWorldTranslation());
        }
    }
}
