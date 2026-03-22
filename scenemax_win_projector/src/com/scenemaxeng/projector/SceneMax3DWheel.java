package com.scenemaxeng.projector;


import com.jayfella.jme.vehicle.WheelModel;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;

public class SceneMax3DWheel extends WheelModel {

    /**
     * Instantiate a model with the specified diameter.
     *
     * @param path
     * @param diameter the desired diameter (in local units, &gt;0)
     */
    protected SceneMax3DWheel(AssetManager assetManager, String path, float diameter, float scale) {
        super(diameter);
        //String assetPath = "/Models/hcr2_buggy/front-wheel.j3o";
        Spatial cgmRoot = assetManager.loadModel(path);
        //cgmRoot.setLocalScale(scale);
        super.setSpatial(cgmRoot);
    }

    @Override
    public void load(AssetManager assetManager) {

    }

}
