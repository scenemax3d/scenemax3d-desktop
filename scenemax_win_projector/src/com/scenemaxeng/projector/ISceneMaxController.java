package com.scenemaxeng.projector;

public interface ISceneMaxController {

    void setUIProxy(SceneMaxApp p);
    boolean run(float tpf);
    void init();
    void init(int startIndex);
    void dispose();
}
