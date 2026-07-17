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
    private MotionEase.MotionEaseSpec motionEase;
    private boolean multiplayerCommandDispatched = false;
    private int multiplayerActionSequence = 0;

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
            if (this.findTargetVar() != 0) {
                return true;
            }
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
            motionEase = MotionEase.fromCommand(cmd, scope);
            MultiplayerControllerResumeState resumeState = consumeMultiplayerResumeState(MULTIPLAYER_ACTION_SLOT_MOVE);
            if (resumeState != null && targetTime > 0f) {
                passedTime = Math.min(targetTime, resumeState.elapsedSeconds);
            }
            targetCalculated=true;
            dispatchMultiplayerMoveCommand();
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

        float progressDelta = MotionEase.delta(motionEase, previousProgress, currentProgress);
        float val = targetVal * progressDelta;

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
            } else if(targetVarDef.varType== VariableDef.VAR_TYPE_LIGHT){
                app.moveLightToDirection(targetVar,cmd.verbalCommand,val);
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
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_LIGHT) {
                app.moveLight(targetVar, axisNum, direction, val);
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

        if (finished) {
            endMultiplayerTimedAction(MULTIPLAYER_ACTION_SLOT_MOVE, multiplayerActionSequence);
            multiplayerActionSequence = 0;
        }
        return finished;
    }

    private float calcProgress(float time, float duration) {
        if(duration<=0f) {
            return 1f;
        }
        return time/duration;
    }

    private void dispatchMultiplayerMoveCommand() {
        if (multiplayerCommandDispatched) {
            return;
        }
        multiplayerCommandDispatched = true;
        String command;
        if (cmd.verbalCommand > 0) {
            command = "{network_entity}.move " + verbalDirectionName(cmd.verbalCommand)
                    + " " + networkNumber(targetVal)
                    + " in " + networkNumber(targetTime) + " seconds";
        } else {
            String sign = direction < 0 ? "-" : "+";
            command = "{network_entity}.move (" + axis + " " + sign + " " + networkNumber(targetVal)
                    + ") in " + networkNumber(targetTime) + " seconds";
        }
        dispatchMultiplayerCommand(command);
        if (targetTime > 0f) {
            multiplayerActionSequence = startMultiplayerTimedAction(MULTIPLAYER_ACTION_SLOT_MOVE, targetTime, command);
        }
    }

    private String verbalDirectionName(int verbalCommand) {
        switch (verbalCommand) {
            case ActionCommandMove.VERBAL_MOVE_LEFT:
                return "left";
            case ActionCommandMove.VERBAL_MOVE_RIGHT:
                return "right";
            case ActionCommandMove.VERBAL_MOVE_UP:
                return "up";
            case ActionCommandMove.VERBAL_MOVE_DOWN:
                return "down";
            case ActionCommandMove.VERBAL_MOVE_FORWARD:
                return "forward";
            case ActionCommandMove.VERBAL_MOVE_BACKWARD:
                return "backward";
            default:
                return "forward";
        }
    }
}
