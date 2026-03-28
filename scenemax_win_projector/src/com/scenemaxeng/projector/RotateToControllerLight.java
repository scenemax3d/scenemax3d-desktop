package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandRotateTo;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class RotateToControllerLight extends SceneMaxBaseController {

    private int axisNum;
    private int direction = 1;
    private float passedTime = 0;
    private float targetTime=0;
    private float targetVal=0;


    public RotateToControllerLight(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, ActionCommandRotateTo cmd) {
        super(app, prg, thread, cmd);
    }

    public void setTargetDetails(String targetVar, VariableDef targetVarDef, int axis, float targetVal, float targetTime) {
        this.targetVar = targetVar;
        this.targetVarDef=targetVarDef;
        this.targetVal=targetVal;
        this.targetTime=targetTime;
        this.axisNum=axis;
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            float curr=0;
            if(axisNum==1) {
                curr = ((Number)app.getFieldValue(this.targetVar,"rx")).floatValue();
            } else if(axisNum==2) {
                curr = ((Number)app.getFieldValue(this.targetVar,"ry")).floatValue();
            } else if(axisNum==3) {
                curr = ((Number)app.getFieldValue(this.targetVar,"rz")).floatValue();
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
