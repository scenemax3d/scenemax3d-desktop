package com.scenemaxeng.projector;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.tween.action.Action;
import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.AnimEventListener;
import com.jme3.animation.LoopMode;

public class AppModelAnimationController implements AnimEventListener {

    public boolean animationFinished = false;
    public SceneMaxBaseController hostController = null;
    private String animationName = null;
    public AppModel appModel;
    private double globalSpeed;
    private boolean paused;
    public boolean isProtected = false;

    public AppModelAnimationController(SceneMaxBaseController hostController) {
        this.hostController=hostController;
    }

    @Override
    public void onAnimCycleDone(AnimControl animControl, AnimChannel animChannel, String animName) {

        if (animName.equals(animationName)) {
            animControl.removeListener(this);
            animationFinished = true;
        }
    }

    @Override
    public void onAnimChange(AnimControl animControl, AnimChannel animChannel, String animName) {
        if (!animName.equals(animationName)) {
            animControl.removeListener(this);
            animationFinished = true;
        }
    }

    public void animate(AppModel m, String animationName, String speed) {

        try {
            this.animationName = animationName;
            this.appModel = m;

            if(m.resource.isJ3O()) {
                AnimControl control = m.getAnimControl();
                control.addListener(this);
                AnimChannel channel = m.getChannel();
                channel.reset(false);
                channel.setAnim(animationName);
                channel.setLoopMode(LoopMode.DontLoop);
                Float animSpeed = Float.parseFloat(speed);
                channel.setSpeed(animSpeed);

            } else {
                AnimComposer composer = m.getAnimComposer();
                if (composer == null) {
                    return;
                }

                Action ac = composer.getAction(animationName);

                if (ac == null) {
                    Action originalAction = composer.action(animationName);
                    ac = new CharacterAction(this, originalAction, composer);
                    composer.addAction(animationName, ac);

                } else {
                    this.animationFinished = false;
                    ((CharacterAction) ac).finishAnimation(); // free current controller's animation
                    ((CharacterAction) ac).setController(this); // set new controller
                }

                Double animSpeed = Double.parseDouble(speed);
                ac.setSpeed(animSpeed);

                if (m.currentAction == null) {
                    m.currentAction = (CharacterAction) ac;
                    composer.setCurrentAction(animationName);
                } else {
                    if(ac!=m.currentAction) {
                        m.currentAction.finishAnimation();
                        m.currentAction = (CharacterAction)ac;
                        composer.setCurrentAction(animationName);
                    }

                }
            }

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Problem running animation " + animationName);
            animationFinished = true;
        }
    }

    public void pause() {
        if(!this.paused && this.appModel.currentAction!=null) {
            double speed = this.appModel.currentAction.getSpeed();//getAnimComposer().getGlobalSpeed();
            if(speed==0) {
                return;
            }
            this.globalSpeed = speed;
            this.appModel.currentAction.setSpeed(0);//this.appModel.getAnimComposer().setGlobalSpeed(0);
        }
        this.paused=true;
    }

    public void resume() {
        if(this.paused && this.appModel.currentAction!=null) {
            this.appModel.currentAction.setSpeed(this.globalSpeed);
        }

        this.paused = false;

    }

    public boolean isPaused() {
        return this.paused;
    }
}
