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
                app.handleRuntimeError("Line: "+cmd.varLineNum+".  variable '"+cmd.targetVar+"' is undefined");
                this.stopAnimationSequence();
                return true;
            }

            findTargetVar();
            AppModel m = app.getAppModel(this.targetVar);
            if(m==null) {
                app.handleRuntimeError("Line: "+cmd.varLineNum+".  run-time instance '"+cmd.targetVar+"' not found");
                this.stopAnimationSequence();
                return true;
            }
            // remove the old animate composite controller if it exists
            if(m.currentAction!=null) {
                if (m.currentAction.isProtected) {
                    return true;
                }
                SceneMaxBaseController hostController = m.currentAction.getHostController();
                ((AnimateCompositeController)hostController.parentController).stopAnimationSequence();
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
