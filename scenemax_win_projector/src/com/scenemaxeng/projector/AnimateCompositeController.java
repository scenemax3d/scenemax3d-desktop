package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandAnimate;

public class AnimateCompositeController extends CompositeController{

    private final boolean loopAnimations;
    private boolean started = false;

    public AnimateCompositeController(ActionCommandAnimate cmd, SceneMaxScope scope) {
        this.scope = scope;
        this.cmd = cmd;
        loopAnimations = cmd.loop;
        this.adhereToPauseStatus=false;
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public boolean run(float tpf) {

        if(!this.started) {
            if(cmd.varDef==null) {
                app.handleRuntimeError(app.formatUndefinedVariableError(
                        cmd.varLineNum,
                        cmd.targetVar,
                        null,
                        getClass().getSimpleName()));
                this.stopAnimationSequence();
                return true;
            }

            if (findTargetVar() != 0) {
                this.stopAnimationSequence();
                return true;
            }
            AppModel m = app.getAppModel(this.targetVar);
            if(m==null) {
                app.handleRuntimeError(app.formatRuntimeLocation(cmd.varLineNum)
                        + "Animation target '" + cmd.targetVar + "' resolved to runtime key '"
                        + this.targetVar + "', but no runtime instance was found. "
                        + "The entity may not have been created yet in this level.");
                this.stopAnimationSequence();
                return true;
            }
            // Remove the old animation sequence if this action came from one.
            // Runtime preview animations may have a plain host controller with no parent.
            if(m.currentAction!=null) {
                if (m.currentAction.isProtected) {
                    return true;
                }
                SceneMaxBaseController hostController = m.currentAction.controller != null
                        ? m.currentAction.getHostController()
                        : null;
                if (hostController != null && hostController.parentController instanceof AnimateCompositeController) {
                    ((AnimateCompositeController) hostController.parentController).stopAnimationSequence();
                } else if (m.currentAction.controller != null) {
                    m.currentAction.controller.stop();
                } else {
                    m.currentAction.finishAnimation();
                    m.currentAction.isProtected = false;
                }
            }

            this.started = true;
        }

        if(_controllers.size()==0) return true;

        SceneMaxBaseController ctl = _controllers.get(runningControllerIndex);
        boolean finished = false;
        boolean async = ctl.async;
        if(async) {
            this.app.registerController(ctl);
        } else {
            finished = ctl.run(tpf);
        }

        if(finished || async) {

            runningControllerIndex++;
            if(runningControllerIndex < _controllers.size()) {
                return false;
            } else {
                if(loopAnimations) {

                    for(SceneMaxBaseController c: _controllers) {
                        ((ModelAnimateController)c).reuse();
                    }
                    runningControllerIndex=0;
                    return false;
                }
                return true; // no more controllers to run
            }

        }

        return false; // current controller not finished

    }

    public void stopAnimationSequence() {
        _controllers.clear();
    }

}
