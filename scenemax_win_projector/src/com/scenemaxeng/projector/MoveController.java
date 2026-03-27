package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandMove;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class MoveController extends SceneMaxBaseController{


    public String axis;
    public String numSign;
    public String num;

    private float passedTime = 0;
    private float targetTime=0;
    private float targetVal=0;
    private int axisNum = -1;
    private float direction = 1;

    public ActionLogicalExpressionVm numExpr;
    private boolean targetCalculated=false;
    public ActionLogicalExpressionVm speedExpr;

    // Cached to avoid per-frame allocation
    private ActionLogicalExpressionVm loopExprCached;

    private ActionCommandMove cmd;

    public MoveController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ActionCommandMove cmd) {
        super(app, prg, scope, cmd);
        this.cmd=cmd;
    }

    @Override
    public void init() {

        if(cmd.verbalCommand>0) {
            return;// verbal direction (left, right, forward, backward etc.) doesn't need axis and direction
        }

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
            targetVal = numExpr==null?1.0f:(float) ActionLogicalExpressionVm.toDouble(numExpr.evaluate());
            targetTime = speedExpr==null?1.0f:(float) ActionLogicalExpressionVm.toDouble(speedExpr.evaluate());
            this.findTargetVar();
//            if(cmd.varDef.varType==VariableDef.VAR_TYPE_SPHERE || cmd.varDef.varType==VariableDef.VAR_TYPE_BOX) {
//                int threadId = app.getEntityThreadId(thread, cmd.targetVar,cmd.varDef.varType);
//                this.targetVar = cmd.varDef.varName + "@" + threadId;//cmd.targetVar;
//            } else if(cmd.varDef.varType== VariableDef.VAR_TYPE_OBJECT) {
//                EntityInstBase obj = (EntityInstBase) thread.getFuncScopeParam(cmd.varDef.varName);
//
//                if(obj==null) {
//                    app.handleRuntimeError("Function argument '"+cmd.varDef.varName+"' is undefined");
//                    return true;
//                }
//
//                this.targetVar = obj.varDef.varName + "@" + obj.thread.threadId;
//                targetVarDef=new VariableDef();// in order to avoid overriding varType
//                targetVarDef.varType = obj.varDef.varType;
//            } else if(cmd.varDef.varType!= VariableDef.VAR_TYPE_CAMERA) {
//                int threadId = app.getEntityThreadId(thread, cmd.targetVar);
//                this.targetVar = cmd.varDef.varName + "@" + threadId;//cmd.targetVar;
//            }

            this.enableEntity(targetVar);// enable this entity
            targetCalculated=true;
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

        float val = tpf*targetVal/targetTime;

        if(cmd.verbalCommand>0) {
            if(targetVarDef.varType== ProgramDef.VAR_TYPE_3D){
                app.moveModelToDirection(targetVar,cmd.verbalCommand,val);
            } else if(targetVarDef.varType== ProgramDef.VAR_TYPE_2D){
                app.moveSpriteToDirection(targetVar,cmd.verbalCommand,val);
            } else if(targetVarDef.varType== ProgramDef.VAR_TYPE_CAMERA){
                app.moveCameraToDirection(targetVar,cmd.verbalCommand,val);
            } else if(targetVarDef.varType== ProgramDef.VAR_TYPE_SPHERE){
                app.moveSphereToDirection(targetVar,cmd.verbalCommand,val);
            } else if(targetVarDef.varType== VariableDef.VAR_TYPE_BOX){
                app.moveBoxToDirection(targetVar,cmd.verbalCommand,val);
            }

        } else {

            if (targetVarDef.varType == ProgramDef.VAR_TYPE_3D) {
                app.moveModel(targetVar, axisNum, direction, val);
            } else if (targetVarDef.varType == ProgramDef.VAR_TYPE_2D) {
                app.moveSprite(targetVar, axisNum, direction, val);
            } else if (targetVarDef.varType == ProgramDef.VAR_TYPE_CAMERA) {
                app.moveCamera(axisNum, direction, val);
            } else if (targetVarDef.varType == ProgramDef.VAR_TYPE_SPHERE) {
                app.moveSphere(targetVar, axisNum, direction, val);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_BOX) {
                app.moveBox(targetVar, axisNum, direction, val);
            }

        }

        if(finished && this.cmd.loopExpr!=null) {
            if(loopExprCached == null) {
                loopExprCached = new ActionLogicalExpressionVm(this.cmd.loopExpr, this.scope);
            }
            Object cond = loopExprCached.evaluate();
            if(cond instanceof Boolean && ((Boolean)cond)) {
                finished=false;
                passedTime=0;
            }
        }

        return finished;
    }
}
