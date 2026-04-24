package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandRotate;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class RotateController extends SceneMaxBaseController{

    public String axis;
    public String numSign;
    public String num;
    //public String targetVar;

    private float passedTime = 0;
    private float targetTime=0;
    private float targetVal=0;
    private int axisNum = -1;
    private float direction = 1;
    public ActionLogicalExpressionVm numExpr;
    public ActionLogicalExpressionVm speedExpr;
    private boolean targetCalculated=false;
    private ActionCommandRotate rotateCmd;

    // Cached to avoid per-frame allocation
    private ActionLogicalExpressionVm loopExprCached;
    //private VariableDef targetVarDef;


    public RotateController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ActionCommandRotate cmd) {
        super(app,prg,scope,cmd);
        this.rotateCmd=cmd;
        //this.targetVarDef=cmd.varDef;
    }

    @Override
    public void init() {

        if(axis.equals("x")) {
            axisNum=1;
        } else if(axis.equals("y")) {
            axisNum=2;
        } else if(axis.equals("z")) {
            axisNum=3;
        }

        if(numSign.equals("-")) {
            direction=-1;
        }

    }

    @Override
    public boolean run(float tpf) {

        if(!targetCalculated) {

            findTargetVar();

            targetVal = numExpr==null?1.0f:(float) ActionLogicalExpressionVm.toDouble(numExpr.evaluate());
            targetTime = speedExpr==null?1.0f:(float) ActionLogicalExpressionVm.toDouble(speedExpr.evaluate());

            this.enableEntity(targetVar);// enable this entity
            targetCalculated=true;
        }

        if(StopModelController.forceStopCommands.get(targetVar)!=null) {
            return true;
        }

        float previousProgress;
        float currentProgress;
        boolean finished = targetTime <= 0f;
        if (!finished) {
            float previousTime = passedTime;
            passedTime += tpf;
            if (passedTime >= targetTime) {
                passedTime = targetTime;
                finished = true;
            }
            previousProgress = calcProgress(previousTime, targetTime);
            currentProgress = calcProgress(passedTime, targetTime);
        } else {
            previousProgress = 0f;
            currentProgress = 1f;
        }

        float rotateVal = targetVal * MotionEase.delta(rotateCmd.motionEaseType, previousProgress, currentProgress);

        if (targetVarDef.varType == VariableDef.VAR_TYPE_CAMERA) {
            this.app.rotateCamera(axisNum, direction, rotateVal);
        } else if (targetVarDef.varType == VariableDef.VAR_TYPE_SPHERE) {
            this.app.rotateSphere(targetVar, axisNum, direction, rotateVal);
        } else if (targetVarDef.varType == VariableDef.VAR_TYPE_BOX) {
            this.app.rotateBox(targetVar, axisNum, direction, rotateVal);
        } else {
            this.app.rotateModel(targetVar, axisNum, direction, rotateVal);
        }

        if(finished && this.rotateCmd.loopExpr!=null) {
            if(loopExprCached == null) {
                loopExprCached = new ActionLogicalExpressionVm(this.rotateCmd.loopExpr, this.scope);
            }
            Object cond = loopExprCached.evaluate();
            if(cond instanceof Boolean && ((Boolean)cond)) {
                finished=false;
                passedTime=0;
            }
        }

        return finished;
    }

    private float calcProgress(float time, float duration) {
        if(duration<=0f) {
            return 1f;
        }
        return time/duration;
    }
}
