package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ChaseCameraCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class ChaseCameraController extends SceneMaxBaseController{

    private boolean targetCalculated=false;

    public ChaseCameraController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ChaseCameraCommand cmd) {
        this.app=app;
        this.prg=prg;
        this.scope=scope;
        this.cmd=cmd;

        this.targetVarDef=cmd.varDef;
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        ChaseCameraCommand cmd = (ChaseCameraCommand)this.cmd;
        if(cmd.command==ChaseCameraCommand.STOP) {
            app.setChaseCameraOff();

            return true;
        }

        if(!targetCalculated) {

            findTargetVar();

            if(cmd.havingAttributesExists) {
                if(cmd.rotationSpeedExpr!=null) {
                    cmd.rotationSpeedVal = (Double) new ActionLogicalExpression(cmd.rotationSpeedExpr,scope).evaluate();
                }

                if(cmd.verticalRotationExpr!=null) {
                    cmd.verticalRotationVal = (Double) new ActionLogicalExpression(cmd.verticalRotationExpr,scope).evaluate();
                }

                if(cmd.horizontalRotationExpr!=null) {
                    cmd.horizontalRotationVal = (Double) new ActionLogicalExpression(cmd.horizontalRotationExpr,scope).evaluate();
                }

                if(cmd.minDistanceExpr!=null) {
                    cmd.minDistanceVal = (Double) new ActionLogicalExpression(cmd.minDistanceExpr,scope).evaluate();
                }

                if(cmd.maxDistanceExpr!=null) {
                    cmd.maxDistanceVal = (Double) new ActionLogicalExpression(cmd.maxDistanceExpr,scope).evaluate();
                }
            }

            targetCalculated=true;
        }

        app.setChaseCameraOn(targetVar,targetVarDef.varType, cmd);

        return true;

    }

}
