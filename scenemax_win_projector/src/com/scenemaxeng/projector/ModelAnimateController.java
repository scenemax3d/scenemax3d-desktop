package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandAnimate;
import com.scenemaxeng.compiler.ProgramDef;

public class ModelAnimateController extends SceneMaxBaseController {

    public String speed;
    private boolean animationStarted = false;
    private AppModelAnimationController controller;
    private ActionLogicalExpressionVm speedExpr;
    private boolean reused;
    private ActionCommandAnimate cmdAnim = null;

    public ModelAnimateController(SceneMaxApp app, ProgramDef prg, ActionCommandAnimate cmd, SceneMaxScope scope) {
        super(app, prg, scope, cmd);
        this.cmdAnim = (ActionCommandAnimate)this.cmd;
        speedExpr = cmd.speedExpr==null?null:new ActionLogicalExpressionVm(cmd.speedExpr,scope);
        this.adhereToPauseStatus=false;
    }


    @Override
    public boolean run(float tpf) {

        if(controller!=null && controller.isPaused()) {
            if (!app.scenePaused) {
                controller.resume();
                return false;
            } else {
                return false;
            }
        }

        if(animationStarted && app.scenePaused) {
            controller.pause();
            return false;
        }


        if(!animationStarted ) {

            if(!checkGoExpr()) {
                return true;
            }

            animationStarted=true;
            controller=new AppModelAnimationController(this);
            controller.isProtected = this.cmdAnim.isProtected;
            speed=speedExpr==null?"1":speedExpr.evaluate().toString();

            if(cmd.varDef==null) {
                app.handleRuntimeError("Line: "+cmd.varLineNum+".  variable '"+cmd.targetVar+"' is undefined");
                return true;
            }

            findTargetVar();

            app.animateModel(this.targetVar, ((ActionCommandAnimate)this.cmd).animationName, speed, controller);

        } else {
            if(reused) {
                this.reused=false;
                app.animateModel(this.targetVar, ((ActionCommandAnimate)this.cmd).animationName, speed, controller);
            }
        }

        return controller.animationFinished;
    }

    public boolean checkGoExpr() {

        if(cmdAnim.goExpr!=null) {
            Object cond = new ActionLogicalExpressionVm(cmdAnim.goExpr,this.scope).evaluate();
            if(cond instanceof Boolean) {
                return (Boolean)cond;

            }
        }

        return true;

    }

    public void reuse() {
        if(controller!=null) {
            controller.animationFinished = false;
            this.reused = true;
        }

    }

}
