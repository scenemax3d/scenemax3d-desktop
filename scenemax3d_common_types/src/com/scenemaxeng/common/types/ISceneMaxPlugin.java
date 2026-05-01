package com.scenemaxeng.common.types;

public interface ISceneMaxPlugin {
    default SceneMaxPluginManifest getManifest() {
        return new SceneMaxPluginManifest("legacy.plugin", getClass().getSimpleName(), "1.0", "");
    }

    default int initialize(SceneMaxPluginContext context) {
        return 0;
    }

    int start(Object... args) ;
    int stop(Object... args) ;
    int run(Object... args) ;
    int progress(Object... args) ;
    int registerObserver(ISceneMaxPlugin observer) ;
}
