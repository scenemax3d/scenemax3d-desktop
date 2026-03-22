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
            this.app.moveDirectional(this.targetVar, DirectionalMoveCommand.FORWARD, dist);
            return false;
        }

        if (!targetCalculated) {

            targetCalculated = true;
            findTargetVar();

            if (cmd.distanceExpr != null) {
                dist = (Double) new ActionLogicalExpression(cmd.distanceExpr, this.scope).evaluate();
            }
            this.app.moveDirectional(this.targetVar, cmd.direction, dist);

            if (cmd.timeExpr != null) {
                this.targetTime = ((Double) new ActionLogicalExpression(cmd.timeExpr, this.scope).evaluate()).floatValue();
                this.originalTargetTime = this.targetTime;
                return false;
            } else {
                return true;
            }

        }

        this.targetTime -= tpf;
        boolean stop = (this.targetTime <= 0);

        if(stop && this.cmd.loopExpr!=null) {
            Object cond = new ActionLogicalExpression(this.cmd.loopExpr,this.scope).evaluate();
            if(cond instanceof Boolean && ((Boolean)cond)) {
                stop=false;
                this.targetTime=this.originalTargetTime;
            }
        }

        if (stop) {
            this.app.moveDirectional(this.targetVar, DirectionalMoveCommand.FORWARD, 0.0);
        }

        return stop;

    }
}
