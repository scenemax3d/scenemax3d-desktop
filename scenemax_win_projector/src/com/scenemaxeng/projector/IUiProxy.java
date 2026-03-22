package com.scenemaxeng.projector;

//import AppModelAnimationController;
//import ISceneMaxController;

import java.util.List;

public interface IUiProxy {

    int loadSprite(SpriteInst spriteInst);

    void rotateSphere(String targetVar, int axisNum, float direction, float rotateVal);
    void moveSphere(String targetVar, int axisNum, float direction, float moveVal);
    void moveModel(String targetVar, int axisNum, float direction, float moveVal);
    void moveSprite(String targetVar, int axisNum, float direction, float moveVal);
    void rotateModel(String targetVar, int axisNum, float direction, float rotateVal);
    void animateModel(String targetVar, String animationName, String speed, AppModelAnimationController animEventListener);
    void spritePlayFrames(String varName, float frame, SceneMaxScope thread);
    void onEndCode();
    void onEndCode(List<String> errors);
    void onStartCode();
    int registerController(SceneMaxBaseController c);
    void print(String printChannel, String txt, String color, double x, double y, double z, String font, double fontSize, boolean append);

    void moveCamera(int axisNum, float direction, float val);
    void rotateCamera(int axisNum, float direction, float rotateVal);
}
