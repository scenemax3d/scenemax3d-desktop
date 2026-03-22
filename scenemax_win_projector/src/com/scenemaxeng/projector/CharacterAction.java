package com.scenemaxeng.projector;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.tween.Tween;
import com.jme3.anim.tween.action.BaseAction;

public class CharacterAction extends BaseAction {

    public AppModelAnimationController controller;
    public boolean isProtected = false;
    AnimComposer ac;

    public CharacterAction(AppModelAnimationController controller, Tween delegate, AnimComposer ac) {
        super(delegate);
        this.ac = ac;
        this.controller=controller;
        this.isProtected = this.controller.isProtected;
    }

    @Override
    public boolean interpolate(double t) {
        boolean running = super.interpolate(t);
        if (!running) {
            this.setSpeed(0);
            this.controller.animationFinished = true;
            this.isProtected = false;
        }
        return running;
    }

    public void finishAnimation() {
        this.controller.animationFinished = true;
    }

    public void setController(AppModelAnimationController ctl) {
        this.controller=ctl;
        this.isProtected = ctl.isProtected;
    }

    public SceneMaxBaseController getHostController() {
        return this.controller.hostController;
    }

}