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
    private AnimControl control;
    private AnimChannel channel;

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
            this.animationFinished = false;

            AnimComposer composer = m.getAnimComposer();
            if (composer == null && m.resource != null && m.resource.isJ3O()) {
                composer = m.getOrCreateAnimComposerForSkinningControl();
            }
            if (composer != null) {
                boolean hasLocalAnimation = composer.hasAction(animationName) || composer.hasAnimClip(animationName);
                boolean attachedExternal = hasLocalAnimation
                        || m.attachExternalAnimation(hostController.app.getAssetManager(), hostController.app.getAssetsMapping(), animationName);
                Action ac = composer.getAction(animationName);
                if (ac == null && !attachedExternal && !composer.hasAnimClip(animationName)) {
                    System.out.println("Animation not found on model or external animation resources: " + animationName);
                    animationFinished = true;
                    return;
                }

                if (!(ac instanceof CharacterAction)) {
                    Action originalAction = ac != null ? ac : composer.action(animationName);
                    ac = new CharacterAction(this, originalAction, composer);
                    composer.addAction(animationName, ac);

                } else {
                    CharacterAction characterAction = (CharacterAction) ac;
                    if (characterAction.controller != this) {
                        characterAction.finishAnimation(); // free previous controller's animation
                    }
                    characterAction.setController(this); // set new controller
                }

                Double animSpeed = Double.parseDouble(speed);
                ac.setSpeed(animSpeed);

                if (m.currentAction == null) {
                    m.currentAction = (CharacterAction) ac;
                    composer.setCurrentAction(animationName);
                } else {
                    if(ac!=m.currentAction) {
                        if (m.currentAction.controller != this) {
                            m.currentAction.finishAnimation();
                        }
                        m.currentAction = (CharacterAction)ac;
                        composer.setCurrentAction(animationName);
                    } else {
                        composer.setCurrentAction(animationName);
                    }

                }
                this.animationFinished = false;

            } else if(m.resource.isJ3O()) {
                AnimControl control = m.getAnimControl();
                if (control == null) {
                    if (m.getSkinningControl() == null) {
                        System.out.println("Animation target has no AnimComposer, SkinningControl, or AnimControl: "
                                + animationName + ". Imported animations require a rigged/skinned model.");
                    } else {
                        System.out.println("Animation control not found for J3O model: " + animationName);
                    }
                    animationFinished = true;
                    return;
                }
                this.control = control;
                control.addListener(this);
                AnimChannel channel = m.getChannel();
                if (channel == null) {
                    System.out.println("Animation channel could not be created for J3O model: " + animationName);
                    animationFinished = true;
                    return;
                }
                this.channel = channel;
                channel.reset(false);
                channel.setAnim(animationName);
                channel.setLoopMode(LoopMode.DontLoop);
                Float animSpeed = Float.parseFloat(speed);
                channel.setSpeed(animSpeed);

            } else {
                animationFinished = true;
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

    public void stop() {
        animationFinished = true;
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            appModel.currentAction.setSpeed(0);
            appModel.currentAction.finishAnimation();
            appModel.currentAction.isProtected = false;
        }
        if (control != null) {
            control.removeListener(this);
        }
        if (channel != null) {
            channel.setSpeed(0);
        }
    }

    public double getCurrentPercent() {
        double length = getLength();
        if (length <= 0) {
            return -1;
        }
        return getCurrentTime() / length * 100.0;
    }

    public double getCurrentTime() {
        if (appModel == null) {
            return -1;
        }

        if (appModel.currentAction != null && appModel.currentAction.controller == this) {
            AnimComposer composer = appModel.getAnimComposer();
            if (composer == null) {
                return -1;
            }
            return composer.getTime("Default");
        }

        if (channel != null) {
            return channel.getTime();
        }

        return -1;
    }

    public double getLength() {
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            return appModel.currentAction.getLength();
        }

        if (channel != null) {
            return channel.getAnimMaxTime();
        }

        return -1;
    }

    public double getPlaybackSpeed() {
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            return appModel.currentAction.getSpeed();
        }

        if (channel != null) {
            return channel.getSpeed();
        }

        return 0;
    }

    public void setPlaybackSpeed(double speed) {
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            appModel.currentAction.setSpeed(speed);
        }

        if (channel != null) {
            channel.setSpeed((float) speed);
        }
    }

    public void setCurrentTime(double time) {
        double length = getLength();
        if (length <= 0) {
            return;
        }

        double clampedTime = Math.max(0, Math.min(length, time));
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            AnimComposer composer = appModel.getAnimComposer();
            if (composer != null) {
                composer.setTime("Default", clampedTime);
            }
        }

        if (channel != null) {
            channel.setTime((float) clampedTime);
        }
    }
}
