package com.scenemaxeng.projector;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public interface ISceneMax3dObjectWrapper {
    void attachTo(Node parent);
    Spatial getSpatial();
}
