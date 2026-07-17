package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.DirectionalMoveCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class DirectionalMoveController extends SceneMaxBaseController{

    private boolean targetCalculated=false;
    private float targetTime = -1;
    private float originalTargetTime = 0;
    private boolean paused = false;
    private Double dist;
    private DirectionalMoveCommand cmd;
    private ActionLogicalExpressionVm loopExprCached;
    private MotionEase.MotionEaseSpec motionEase;
    private boolean multiplayerCommandDispatched = false;
    private int multiplayerActionSequence = 0;

    public DirectionalMoveController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, DirectionalMoveCommand cmd) {
        super(app, prg, scope, cmd);
        this.adhereToPauseStatus=false;
        this.cmd = (DirectionalMoveCommand)cmd;
    }
    public boolean run(float tpf) {
        if (forceStop) return true;
        if (app.scenePaused && targetCalculated) {
            this.app.moveDirectional(this.targetVar, DirectionalMoveCommand.FORWARD, 0.0);
            this.paused = true;
            return false;
        }

        if (this.paused && !app.scenePaused) {
            this.paused = false;
        }

        if (!targetCalculated) {

            targetCalculated = true;
            if (findTargetVar() != 0) {
                return true;
            }

            if (cmd.distanceExpr != null) {
                dist = (Double) new ActionLogicalExpressionVm(cmd.distanceExpr, this.scope).evaluate();
            }

            if (cmd.timeExpr != null) {
                this.targetTime = ((Double) new ActionLogicalExpressionVm(cmd.timeExpr, this.scope).evaluate()).floatValue();
                this.originalTargetTime = this.targetTime;
                motionEase = MotionEase.fromCommand(cmd, scope);
                MultiplayerControllerResumeState resumeState = consumeMultiplayerResumeState(MULTIPLAYER_ACTION_SLOT_MOVE);
                if (resumeState != null && this.originalTargetTime > 0f) {
                    this.targetTime = Math.min(this.originalTargetTime, resumeState.remainingSeconds);
                }
                dispatchMultiplayerDirectionalMoveCommand();
            } else {
                dispatchMultiplayerDirectionalMoveCommand();
                this.app.moveDirectional(this.targetVar, cmd.direction, dist);
                return true;
            }

        }

        float previousTime = Math.max(0f, originalTargetTime - targetTime);
        if (targetTime > 0f) {
            this.targetTime -= tpf;
            if (this.targetTime < 0f) {
                this.targetTime = 0f;
            }
        } else {
            this.targetTime = 0f;
        }
        float currentTime = Math.max(0f, originalTargetTime - targetTime);
        boolean stop = (this.targetTime <= 0);

        this.app.moveDirectional(this.targetVar, cmd.direction, calculateDirectionalSpeed(previousTime, currentTime, tpf));

        if(stop && this.cmd.loopExpr!=null) {
            if(loopExprCached == null) {
                loopExprCached = new ActionLogicalExpressionVm(this.cmd.loopExpr,this.scope);
            }
            Object cond = loopExprCached.evaluate();
            if(cond instanceof Boolean && ((Boolean)cond)) {
                stop=false;
                this.targetTime=this.originalTargetTime;
            }
        }

        if (stop) {
            this.app.moveDirectional(this.targetVar, DirectionalMoveCommand.FORWARD, 0.0);
            endMultiplayerTimedAction(MULTIPLAYER_ACTION_SLOT_MOVE, multiplayerActionSequence);
            multiplayerActionSequence = 0;
        }

        return stop;

    }

    private double calculateDirectionalSpeed(float previousTime, float currentTime, float tpf) {
        if (dist == null) {
            return 0.0;
        }
        if (originalTargetTime <= 0f || tpf <= 0f) {
            return dist;
        }

        float previousProgress = previousTime / originalTargetTime;
        float currentProgress = currentTime / originalTargetTime;
        float progressDelta = MotionEase.delta(motionEase, previousProgress, currentProgress);
        return dist * originalTargetTime * progressDelta / tpf;
    }

    private void dispatchMultiplayerDirectionalMoveCommand() {
        if (multiplayerCommandDispatched) {
            return;
        }
        multiplayerCommandDispatched = true;
        String command = "{network_entity}.move " + directionName(cmd.direction)
                + " " + networkNumber(dist == null ? 0.0 : dist);
        if (cmd.timeExpr != null) {
            command += " for " + networkNumber(originalTargetTime) + " seconds";
        }
        dispatchMultiplayerCommand(command);
        if (cmd.timeExpr != null && originalTargetTime > 0f) {
            multiplayerActionSequence = startMultiplayerTimedAction(MULTIPLAYER_ACTION_SLOT_MOVE, originalTargetTime, command);
        }
    }

    private String directionName(int direction) {
        switch (direction) {
            case DirectionalMoveCommand.BACKWARD:
                return "backward";
            case DirectionalMoveCommand.LEFT:
                return "left";
            case DirectionalMoveCommand.RIGHT:
                return "right";
            case DirectionalMoveCommand.FORWARD:
            default:
                return "forward";
        }
    }
}
