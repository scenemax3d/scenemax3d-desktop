package com.scenemaxeng.projector;

import com.jme3.input.ChaseCamera;
import com.jme3.input.InputManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

public class SceneMaxChaseCamera extends ChaseCamera {
    public SceneMaxChaseCamera(Camera cam, Spatial target) {
        super(cam, target);
    }

    public SceneMaxChaseCamera(Camera cam) {
        super(cam);
    }

    public SceneMaxChaseCamera(Camera cam, InputManager inputManager) {
        super(cam, inputManager);
    }

    public SceneMaxChaseCamera(Camera cam, Spatial target, InputManager inputManager) {
        super(cam, target, inputManager);
    }

    public void setPosition(Vector3f pos) {
        //this.zoomCamera(1.0f);
    }

}
