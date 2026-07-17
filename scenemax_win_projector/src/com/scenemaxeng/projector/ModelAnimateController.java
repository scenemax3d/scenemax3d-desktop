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
    private boolean multiplayerCommandDispatched = false;
    private int multiplayerActionSequence = 0;

    public ModelAnimateController(SceneMaxApp app, ProgramDef prg, ActionCommandAnimate cmd, SceneMaxScope scope) {
        super(app, prg, scope, cmd);
        this.cmdAnim = (ActionCommandAnimate)this.cmd;
        speedExpr = cmd.speedExpr==null?null:new ActionLogicalExpressionVm(cmd.speedExpr,scope);
        this.adhereToPauseStatus=false;
    }


    @Override
    public boolean run(float tpf) {

        if (forceStop) {
            if (controller != null) {
                controller.stop();
            }
            return true;
        }

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
            controller=createAnimationController();
            speed=speedExpr==null?"1":speedExpr.evaluate().toString();

            if(cmd.varDef==null) {
                app.handleRuntimeError(app.formatUndefinedVariableError(
                        cmd.varLineNum,
                        cmd.targetVar,
                        null,
                        getClass().getSimpleName()));
                return true;
            }

            if (findTargetVar() != 0) {
                return true;
            }

            app.animateModel(this.targetVar, ((ActionCommandAnimate)this.cmd).animationName, speed, controller);
            applyMultiplayerResumeState();
            dispatchMultiplayerAnimationCommand();

        } else {
            if(reused) {
                this.reused=false;
                app.animateModel(this.targetVar, ((ActionCommandAnimate)this.cmd).animationName, speed, controller);
                applyMultiplayerResumeState();
                dispatchMultiplayerAnimationCommand();
            }
        }

        if (controller != null) {
            controller.updateFrameRangeState();
        }
        boolean finished = controller.animationFinished;
        if (finished) {
            endMultiplayerTimedAction(MULTIPLAYER_ACTION_SLOT_ANIMATE, multiplayerActionSequence);
            multiplayerActionSequence = 0;
        }
        return finished;
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
            this.multiplayerCommandDispatched = false;
            this.multiplayerActionSequence = 0;
        }

    }

    public AppModelAnimationController getAnimationController() {
        return controller;
    }

    private AppModelAnimationController createAnimationController() {
        AppModelAnimationController result = new AppModelAnimationController(this);
        result.isProtected = this.cmdAnim.isProtected;
        if (this.cmdAnim.hasFrameRange()) {
            result.setFrameRange(
                    this.cmdAnim.frameRangeStart,
                    this.cmdAnim.frameRangeStartPercent,
                    this.cmdAnim.frameRangeEnd,
                    this.cmdAnim.frameRangeEndPercent);
        }
        return result;
    }

    public String getAnimationName() {
        return cmdAnim.animationName;
    }

    private void applyMultiplayerResumeState() {
        MultiplayerControllerResumeState resumeState = consumeMultiplayerResumeState(MULTIPLAYER_ACTION_SLOT_ANIMATE);
        if (resumeState != null && controller != null) {
            controller.applyMultiplayerResumeElapsed(resumeState.elapsedSeconds);
        }
    }

    private void dispatchMultiplayerAnimationCommand() {
        if (multiplayerCommandDispatched || controller == null || controller.animationFinished) {
            return;
        }
        multiplayerCommandDispatched = true;
        String command = buildMultiplayerAnimationCommand();
        dispatchMultiplayerCommand(command);

        double durationSeconds = controller.getPlaybackDurationSeconds();
        if (durationSeconds > 0) {
            multiplayerActionSequence = startMultiplayerTimedAction(
                    MULTIPLAYER_ACTION_SLOT_ANIMATE,
                    (float) durationSeconds,
                    command);
        }
    }

    private String buildMultiplayerAnimationCommand() {
        StringBuilder result = new StringBuilder();
        result.append("{network_entity}.")
                .append(animationNameLiteral(cmdAnim.animationName));
        if (cmdAnim.hasFrameRange()) {
            result.append("[")
                    .append(cmdAnim.frameRangeStart)
                    .append(cmdAnim.frameRangeStartPercent ? "%" : "")
                    .append("-")
                    .append(cmdAnim.frameRangeEnd)
                    .append(cmdAnim.frameRangeEndPercent ? "%" : "")
                    .append("]");
        }
        result.append(" at speed of ")
                .append(networkNumber(parseSpeed(speed)));
        return result.toString();
    }

    private String animationNameLiteral(String name) {
        String safeName = name == null ? "" : name;
        return "\"" + safeName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private double parseSpeed(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ex) {
            return 1.0d;
        }
    }

}
