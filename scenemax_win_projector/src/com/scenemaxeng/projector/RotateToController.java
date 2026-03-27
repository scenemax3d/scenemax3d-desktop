package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandRotateTo;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class RotateToController extends SceneMaxBaseController {

    ActionCommandRotateTo cmd = null;
    private int axisNum;
    private int direction;
    private float passedTime = 0;
    private float targetTime=0;
    private float targetVal=0;


    public RotateToController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ActionCommandRotateTo cmd) {

        super(app, prg, scope, cmd);
        this.cmd=cmd;

    }

    @Override
    public void init() {

        if(this.cmd.axis.equals("x")) {
            axisNum=1;
        } else if(this.cmd.axis.equals("y")) {
            axisNum=2;
        } else if(this.cmd.axis.equals("z")) {
            axisNum=3;
        }

        direction=1; // +

    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            findTargetVar();
            targetVal = this.cmd.rotateValExpr==null?1.0f:((Double)new ActionLogicalExpressionVm(this.cmd.rotateValExpr,this.scope).evaluate()).floatValue();
            targetTime = this.cmd.speedExpr==null?1.0f:((Double)new ActionLogicalExpressionVm(this.cmd.speedExpr,this.scope).evaluate()).floatValue();

            float curr=0;
            if(axisNum==1) {
                curr = (Float)app.getFieldValue(this.targetVar,"rx");
            } else if(axisNum==2) {
                curr = (Float)app.getFieldValue(this.targetVar,"ry");
            } else if(axisNum==3) {
                curr = (Float)app.getFieldValue(this.targetVar,"rz");
            }

            float delta1 = targetVal-curr;
            float delta2=0;
            if(curr>targetVal) {
                delta2 = 360-curr+targetVal;
            } else {
                delta2 = (360-targetVal+curr)*-1;
            }

            float delta = delta1;
            if(Math.abs(delta2)<Math.abs(delta1)) {
                delta=delta2;
            }

            targetVal=delta;

            targetCalculated = true;

        }


        if(StopModelController.forceStopCommands.get(targetVar)!=null) {
            return true;
        }

        boolean finished = false;
        passedTime+=tpf;
        if(passedTime>=targetTime) {
            tpf-=(passedTime-targetTime);
            finished=true;
        }

        float rotateVal = tpf*targetVal/targetTime;

        if (targetVarDef.varType == VariableDef.VAR_TYPE_CAMERA) {
            this.app.rotateCamera(axisNum, direction, rotateVal);
        } else if (targetVarDef.varType == VariableDef.VAR_TYPE_SPHERE) {
            this.app.rotateSphere(targetVar, axisNum, direction, rotateVal);
        } else if (targetVarDef.varType == VariableDef.VAR_TYPE_BOX) {
            this.app.rotateBox(targetVar, axisNum, direction, rotateVal);
        } else {
            this.app.rotateModel(targetVar, axisNum, direction, rotateVal);
        }

        return finished;


    }

}