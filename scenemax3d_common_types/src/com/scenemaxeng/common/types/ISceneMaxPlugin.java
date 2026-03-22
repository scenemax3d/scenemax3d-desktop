package com.scenemaxeng.common.types;

public interface ISceneMaxPlugin {
    int start(Object... args) ;
    int stop(Object... args) ;
    int run(Object... args) ;
    int progress(Object... args) ;
    int registerObserver(ISceneMaxPlugin observer) ;
}
