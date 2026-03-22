package com.scenemaxeng.projector;

import com.jayfella.jme.vehicle.Sound;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SceneMax3DGenericEngineSound extends Sound {

    List<String> soundFiles = new ArrayList<>();

    public SceneMax3DGenericEngineSound(int soundType) {

        switch(soundType) {
            case 1:
                setEngineSound1();
                break;
            case 2:
                setEngineSound2();
                break;

            case 4:
                setEngineSound4();
                break;

            case 5:
                setEngineSound5();
                break;

        }
    }

    public void setEngineSound1() {
        addAssetPath("audio/engine-1d2.wav", 13.4f);
        addAssetPath("audio/engine-1.wav", 26.75f);
        addAssetPath("audio/engine-1x2.wav", 53.5f);
        addAssetPath("audio/engine-1x4.wav", 107f);
    }

    public void setEngineSound2() {
        addAssetPath("audio/engine-2d2.wav", 12.6f);
        addAssetPath("audio/engine-2.wav", 25.25f);
        addAssetPath("audio/engine-2x2.wav", 50.5f);
        addAssetPath("audio/engine-2x4.wav", 101f);
    }

    public void setEngineSound4() {
        addAssetPath("audio/engine-4d8.wav", 10.7f);
        addAssetPath("audio/engine-4d4.wav", 21.4f);
        addAssetPath("audio/engine-4d2.wav", 42.9f);
        addAssetPath("audio/engine-4.wav", 85.75f);
        addAssetPath("audio/engine-4x2.wav", 171.5f);
    }

    public void setEngineSound5() {
        addAssetPath("audio/engine-5d4.wav", 14.4f);
        addAssetPath("audio/engine-5d2.wav", 28.9f);
        addAssetPath("audio/engine-5.wav", 57.75f);
        addAssetPath("audio/engine-5x2.wav", 115.5f);
    }

    public void addAssetPath(String path, float pitch) {
        soundFiles.add(path);
        super.addAssetPath(path, pitch);
    }
    public List<String> getSoundFiles() {
        return soundFiles;
    }



}
