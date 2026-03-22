package com.scenemaxeng.projector;

import com.jayfella.jme.vehicle.Sound;

public class SceneMax3DGenericSound extends Sound {
    public SceneMax3DGenericSound(String baseFile, float v) {
        addAssetPath(baseFile,v);
    }
}
